package com.slideindex.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log

/**
 * 应用级"预测性返回"（`android:enableOnBackInvokedCallback`）的读写。
 *
 * ⚠️ §0.16.25：这个 flag 是浮窗返回键的**命门**，也是历史上两次真机事故的现场：
 *
 * ① **写入太晚** —— 它原来在 `SlideIndexApp.onCreate` 里写，而 `Application.onCreate` 虽然早于
 *    Activity，但系统可以在 `attach` 之后、`onCreate` 返回之前就把首批窗口（前台服务 / 悬浮窗）
 *    挂上；`ViewRootImpl` 在视图挂载时就按当时的 flag 决定了返回键走哪条路，之后再改**对已有窗口无效**。
 *    现在统一在 [com.slideindex.app.SlideIndexApp.attachBaseContext] 的最早时点写入（见
 *    [applyFromStartupSetting]）。
 * ② **写入的值不对** —— 原来读 `settingsRepository.readSnapshot()`，那是**异步填充的缓存**，
 *    进程刚起时还是默认值 `false`。现在读 [PredictiveBackStartupStore]（同步镜像，
 *    由 `SettingsRepository` 跟着设置变化 commit 落盘）。
 * ③ **读回来的口径和系统不一致** —— [resolveAppBackDispatch] 直接读 `ApplicationInfo.flags`，
 *    以**系统实际会怎么派发**为准，而不是按用户设置猜。
 *
 * 写失败（隐藏 API 被拦 / ROM 无此方法）时两边仍可能不一致，所以
 * [com.slideindex.app.overlay.OverlayViewBackHandler] **按系统真实口径**决定自己装哪条路
 * （见 [resolveAppBackDispatch]），而不是按用户设置猜。
 */
object PredictiveBackHelper {

    /**
     * 系统会把返回键怎么派发给本应用。
     *
     * `OverlayViewBackHandler` 必须按这个（**而不是用户设置**）决定自己装哪条拦截路：
     * 装错就等于"系统按 A 派发、我们只等 B"，在魅族上会形成
     * `registerCompatOnBackInvokedCallback` ↔ `injectBackKeyEvents` 的死循环。
     */
    enum class AppBackDispatch {
        /** 应用级预测性返回开着：系统走 `OnBackInvokedCallback`，注入 `KEYCODE_BACK` 的兼容路会死循环。 */
        ON_BACK_INVOKED,

        /** 应用级预测性返回关着：系统走"兼容注入 `KEYCODE_BACK`"，按键监听/未处理键监听才收得到。 */
        INJECTED_KEY,

        /** API 33 以下：没有 OnBackInvoked，只有按键。 */
        NONE,
        ;

        /** 供真机排查用（Gradle 里把 `PredictiveBack` 打到 logcat 即可读）。 */
        val label: String
            get() = when (this) {
                ON_BACK_INVOKED -> "OnBackInvoked"
                INJECTED_KEY -> "注入 KEYCODE_BACK"
                NONE -> "无（API < 33）"
            }
    }

    /**
     * 解析**系统真实口径**。
     *
     * 判据优先级：
     * ① `ApplicationInfo.flags` 上的 `FLAG_ENABLE_ON_BACK_INVOKED_CALLBACK`（运行时写入后读回来就是真值）；
     * ② 反射拿不到该常量时的 AOSP 位值兜底；
     * ③ 都拿不到（API 33 上被拦截）才退回用户设置 —— 这时两者的差异会留一行日志，别再默默吞掉。
     */
    fun resolveAppBackDispatch(context: Context): AppBackDispatch {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return AppBackDispatch.NONE
        val appInfo = context.applicationContext.applicationInfo
        // 判据①：系统自己的开关读取方法（与 setEnableOnBackInvokedCallback 成对，最权威）。
        // 判据②：公开的 ApplicationInfo.flags 上的对应位。
        // 两条都拿不到才退回用户设置 —— 不再拿位值猜"系统会怎么派发"。
        readSystemEnabled(appInfo)?.let { enabled ->
            Log.i(TAG, "resolveAppBackDispatch: 系统口径 enabled=$enabled（isOnBackInvokedCallbackEnabled）")
            return if (enabled) AppBackDispatch.ON_BACK_INVOKED else AppBackDispatch.INJECTED_KEY
        }
        val flag = ENABLE_ON_BACK_INVOKED_FLAG
        if (flag != null) {
            val bitSet = appInfo.flags and flag != 0
            Log.i(
                TAG,
                "resolveAppBackDispatch: 退回 flags 判据 sdkInt=${Build.VERSION.SDK_INT} flag=0x${
                    flag.toString(16)
                } appFlags=0x${appInfo.flags.toString(16)} bitSet=$bitSet",
            )
            return if (bitSet) AppBackDispatch.ON_BACK_INVOKED else AppBackDispatch.INJECTED_KEY
        }
        Log.w(
            TAG,
            "拿不到系统口径也无法读 ApplicationInfo.flags，退回用户设置判断" +
                "（若与系统实际派发不一致，浮窗返回键会失效甚至形成注入死循环）",
        )
        return if (userSettingEnabled(context)) AppBackDispatch.ON_BACK_INVOKED else AppBackDispatch.INJECTED_KEY
    }

    /**
     * 反射调 `ApplicationInfo.isOnBackInvokedCallbackEnabled()`（API 33 起，与
     * [setEnableOnBackInvokedCallback] 成对）。拿不到（反射被拦 / 无此方法）→ `null`。
     */
    @Suppress("SwallowedException")
    private fun readSystemEnabled(appInfo: ApplicationInfo): Boolean? = runCatching {
        val declared = ApplicationInfo::class.java.getDeclaredMethod("isOnBackInvokedCallbackEnabled")
        declared.isAccessible = true
        declared.invoke(appInfo) as? Boolean
    }.getOrNull()

    /**
     * 把"应用级预测性返回"这个开关**运行时**刷成 [enabled]（manifest 里声明的是 true）。
     *
     * 只在**运行时**调用（`MainActivity.applyPredictiveBackEnabled`，值来自真实的设置快照）。
     * ⚠️ **不要**在启动早期（`Application.attachBaseContext` / `onCreate`）用异步缓存的值调它 ——
     * 那会把"用户其实开着"写成关，而窗口若在那一瞬间创建就制造出
     * "系统走 OnBackInvoked、浮窗只等注入按键"的不一致（§0.16.25 真机闪退事故的成因）。
     * 启动期一律**不写**：flag 由 manifest 的 `true` 决定，读方按系统真实口径走。
     *
     * ⚠️ §0.16.22：这里原来是**静默** `runCatching`，但它改失败时两边就对不上：
     * 系统按 manifest 的 true 走 OnBackInvoked 派发，而 `OverlayViewBackHandler` 若按用户设置
     * 只装了 legacy 的"注入 `KEYCODE_BACK`"监听 → 浮窗返回键静默失效；反过来（系统走注入、
     * 我们装 OnBackInvoked）在魅族上会直接形成注入死循环。所以失败**必须留痕**
     * （`adb logcat -s PredictiveBack`），别再默默吞掉。
     * 真机实测（魅族 21 / Android 16）：该隐藏方法在 `attach` 阶段偶发 `NoSuchMethodException`，
     * 这也是"启动期不要写"的另一条理由。
     */
    fun applyEnabled(appInfo: ApplicationInfo, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            setEnableOnBackInvokedCallback(appInfo, enabled)
            Log.i(
                TAG,
                "applyEnabled($enabled): 已写入 ApplicationInfo（返回键走 ${
                    if (enabled) "OnBackInvoked" else "注入 KEYCODE_BACK"
                }）",
            )
        }.onFailure { error ->
            Log.w(
                TAG,
                "applyEnabled($enabled) 失败：ApplicationInfo 的预测性返回 flag 保持 manifest 的 true，" +
                    "而 OverlayViewBackHandler 会按用户设置(false)只装 legacy 按键监听 —— " +
                    "两边对不上时所有浮窗的返回键都会失效（真机事故 §0.16.22），" +
                    "系统走兼容注入时还可能形成 registerCompatOnBackInvokedCallback ↔ injectBackKeyEvents 死循环",
                error,
            )
        }
    }

    /** 用户设置里的口径（只在读不到系统 flag 时作为兜底）。 */
    private fun userSettingEnabled(context: Context): Boolean =
        PredictiveBackStartupStore.readOrNull(context) ?: false

    /**
     * 调隐藏 API `ApplicationInfo.setEnableOnBackInvokedCallback(boolean)`。
     *
     * 参数类型按 `Boolean.TYPE` 查（不按 `Boolean::class.java`，那是装箱类型，会 `NoSuchMethodException`）。
     */
    @Suppress("SwallowedException")
    private fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enabled: Boolean) {
        val declared = ApplicationInfo::class.java.getDeclaredMethod(
            "setEnableOnBackInvokedCallback",
            Boolean::class.javaPrimitiveType,
        )
        declared.isAccessible = true
        declared.invoke(appInfo, enabled)
    }

    /**
     * `ApplicationInfo.FLAG_ENABLE_ON_BACK_INVOKED_CALLBACK` 的值。
     *
     * 它不是公开常量（`android.jar` 里没有），所以先按名字从**运行时**的 ApplicationInfo 上取
     * （不同 ROM 改名/改位都能跟上）；取不到再用 AOSP 的位值 `1 shl 20` 兜底。
     */
    private val ENABLE_ON_BACK_INVOKED_FLAG: Int? by lazy {
        runCatching {
            ApplicationInfo::class.java.getField("FLAG_ENABLE_ON_BACK_INVOKED_CALLBACK").getInt(null)
        }.getOrNull() ?: AOSP_FLAG_ENABLE_ON_BACK_INVOKED_CALLBACK
    }

    private const val TAG = "PredictiveBack"

    /** AOSP `ApplicationInfo.FLAG_ENABLE_ON_BACK_INVOKED_CALLBACK = 1 shl 20`（API 33 起）。 */
    private const val AOSP_FLAG_ENABLE_ON_BACK_INVOKED_CALLBACK = 1 shl 20
}
