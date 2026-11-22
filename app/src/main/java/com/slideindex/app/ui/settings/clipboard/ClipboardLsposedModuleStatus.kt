package com.slideindex.app.ui.settings.clipboard

import android.content.Context
import com.slideindex.app.xposed.bridge.ModuleBridgeStatusProbe
import com.slideindex.app.xposed.bridge.ModuleBridgeStatusStore
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.bridge.ModuleStatusFields

/**
 * LSPosed 通道「模块此刻能不能真的干活」的判定。
 *
 * 看三件事：模块回传的代码版本与 APK 安装代是否和当前应用一致、
 * 剪贴板白名单 hook 是否装上、以及**模块手里那份名单和本地设置是否一致**。
 * 不看 [ModuleBridgeStatusStore.Snapshot.active]——那个是手势接管是否生效，和剪贴板无关。
 */
object ClipboardLsposedModuleStatus {
    enum class Readiness {
        /** 模块代码与当前 APK 同版，白名单 hook 已装上，且名单和本地设置一致。 */
        Ready,

        /** 系统里跑的还是覆盖安装前的旧模块代码：重启手机后新代码才生效。 */
        StaleModuleCode,

        /** 模块在跑，但白名单 hook 没装上（系统内部实现改了等）。 */
        ClipboardHookMissing,

        /**
         * 模块在跑、hook 也装上了，但它手里的名单和本地设置对不上。
         *
         * 实测成因：磁盘快照在部分 ROM 上 system_server 读不到，配置只能靠广播下发；
         * 广播没到（或到之前）模块就会一直用旧名单（现象：白名单里只有自己，
         * 用户后加的包拿不到后台放行）。设置页据此直接提示，不用翻 LSPosed 日志。
         */
        WhitelistStale,

        /** 模块没回应：没启用 / 作用域没勾系统框架 / 装完没重启。 */
        NotReady,
    }

    /** 模块回传的名单大小 vs 本地设置。 */
    internal enum class WhitelistState { Unknown, Synced, Stale }

    /** 判定结果：就绪程度 + 模块手里的名单大小（旧模块不带 `wl=` 时为 null）。 */
    data class Status(
        val readiness: Readiness,
        val moduleWhitelistSize: Int? = null,
    )

    private const val PROBE_THROTTLE_MS = 30_000L

    @Volatile
    private var lastProbeAtMs = 0L

    /** 先按本地缓存给一次结果，再按节流主动探一次模块。 */
    fun refresh(context: Context, localWhitelistSize: Int, onResult: (Status) -> Unit) {
        val appContext = context.applicationContext
        onResult(classify(appContext, ModuleBridgeStatusStore.read(appContext), localWhitelistSize))
        val now = System.currentTimeMillis()
        if (now - lastProbeAtMs < PROBE_THROTTLE_MS) return
        lastProbeAtMs = now
        ModuleBridgeStatusProbe.probe(appContext) { _, _ ->
            onResult(classify(appContext, ModuleBridgeStatusStore.read(appContext), localWhitelistSize))
        }
    }

    fun classify(
        context: Context,
        snapshot: ModuleBridgeStatusStore.Snapshot,
        localWhitelistSize: Int,
    ): Status {
        val moduleWhitelistSize = ModuleStatusFields.intField(
            snapshot.detail,
            ModuleHookBridgeContract.STATUS_DETAIL_WHITELIST_PREFIX,
        )
        // 旧模块不带 code 字段：能回应就说明模块活着，但那一定是覆盖安装前的代码。
        when (ModuleStatusFields.codeStateOf(context, snapshot)) {
            ModuleStatusFields.CodeState.Unknown -> return Status(Readiness.NotReady)
            ModuleStatusFields.CodeState.Stale -> return Status(Readiness.StaleModuleCode)
            ModuleStatusFields.CodeState.Current -> Unit
        }
        val clipboardHook = ModuleStatusFields.field(
            snapshot.detail,
            ModuleHookBridgeContract.STATUS_DETAIL_CLIPBOARD_PREFIX,
        )
        when (clipboardHook) {
            "ok" -> Unit
            null -> return Status(Readiness.StaleModuleCode)
            else -> return Status(Readiness.ClipboardHookMissing)
        }
        val readiness = when (whitelistStateOf(moduleWhitelistSize, localWhitelistSize)) {
            WhitelistState.Unknown -> Readiness.Ready // 旧模块没这个字段：不误报，交给 code= 判定
            WhitelistState.Synced -> Readiness.Ready
            WhitelistState.Stale -> Readiness.WhitelistStale
        }
        return Status(readiness, moduleWhitelistSize)
    }

    /**
     * 模块回传的名单大小 vs 本地设置。
     *
     * 只比大小：模块只回报数量（不回报包名），而 app 每次都是把整份名单原样下发，
     * 所以"数量对不上"就是"这份名单不是本地设置那份"。
     */
    internal fun whitelistStateOf(moduleSize: Int?, localSize: Int): WhitelistState = when {
        moduleSize == null -> WhitelistState.Unknown
        moduleSize == localSize -> WhitelistState.Synced
        else -> WhitelistState.Stale
    }
}
