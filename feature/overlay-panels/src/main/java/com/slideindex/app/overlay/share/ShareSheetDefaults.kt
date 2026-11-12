package com.slideindex.app.overlay.share

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 默认分享目标（`包名/Activity 类名`），只在取词面板的文字 / 图片分享键上使用。
 *
 * 复用面板里 `ShareImageTarget` 的键格式：这样默认目标、钉住列表、分享目标缓存
 * 三者说的是同一套标识，换实现时不需要迁移。
 */
data class ShareDefaultTarget(
    val packageName: String,
    val activityClassName: String,
) {
    val key: String get() = "$packageName/$activityClassName"

    companion object {
        /** 解析 `包名/Activity 类名`；格式不对返回 null（坏数据不能把面板打不开）。 */
        fun fromKey(key: String?): ShareDefaultTarget? {
            val raw = key?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val separator = raw.lastIndexOf('/')
            if (separator <= 0 || separator == raw.lastIndex) return null
            val packageName = raw.substring(0, separator)
            val activityClassName = raw.substring(separator + 1)
            if (packageName.isBlank() || activityClassName.isBlank()) return null
            return ShareDefaultTarget(packageName, activityClassName)
        }
    }
}

/**
 * 分享面板的「设为默认」目标：按内容形态（文字 / 图片）各存一个。
 *
 * 为什么按类型分开：文本和图片的系统分享目标集合本来就不一样（见 [ShareSheetKind]），
 * 一个全局默认很容易在另一类内容上发不出去。
 *
 * 为什么不放进主设置：它和 [ShareSheetPins] 是同一份面板本地数据，放同一个 DataStore
 * 才能一起备份；面板首帧也要能同步取到（[current]），主设置的快照链路到不了浮层。
 */
object ShareSheetDefaults {

    private const val TAG = "ShareSheetDefaults"

    private val KEY = stringPreferencesKey("default_targets")
    private val json = Json { ignoreUnknownKeys = true }

    /** 串行写盘：连续「设为默认 / 清除」时保证后一次不会先落盘。 */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _targets = MutableStateFlow<Map<String, String>>(emptyMap())

    /** 当前默认目标（kind 名 → `包名/类名`）；Compose 直接 collect 它就能拿到角标。 */
    val targets: StateFlow<Map<String, String>> = _targets.asStateFlow()

    @Volatile
    private var loaded = false

    /** 这一版只有取词面板的文字 / 图片分享键显示角标并直发；文件分享不参与。 */
    fun supports(kind: ShareSheetKind): Boolean =
        kind == ShareSheetKind.TEXT || kind == ShareSheetKind.IMAGE

    /** 面板打开时读一次；重复调用是空操作。 */
    suspend fun load(context: Context) {
        if (loaded) return
        val appContext = context.applicationContext
        val raw = runCatching { appContext.shareSheetDataStore.data.first()[KEY] }.getOrNull()
        decode(raw)?.let { _targets.value = it }
        loaded = true
    }

    /** 同步取当前默认（首帧画角标用）；还没读过就是 null。 */
    fun current(kind: ShareSheetKind): ShareDefaultTarget? =
        ShareDefaultTarget.fromKey(_targets.value[kind.name])

    /**
     * 解析并校验目标仍然存在且可启动：应用被卸载 / Activity 被禁用时当作"没设默认"，
     * 下次点分享键会正常弹面板，而不是点了没反应。
     */
    fun resolve(context: Context, kind: ShareSheetKind): ShareDefaultTarget? {
        val target = current(kind) ?: return null
        val info = runCatching {
            context.packageManager.getActivityInfo(
                ComponentName(target.packageName, target.activityClassName),
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        }.getOrNull() ?: return null
        return target.takeIf { info.exported }
    }

    /** 「设为默认」：先更新内存（角标立刻变），再串行落盘。 */
    fun set(context: Context, kind: ShareSheetKind, target: ShareDefaultTarget) {
        if (!supports(kind)) return
        val updated = _targets.value + (kind.name to target.key)
        _targets.value = updated
        persist(context, updated)
    }

    fun clear(context: Context, kind: ShareSheetKind) {
        val updated = _targets.value - kind.name
        _targets.value = updated
        persist(context, updated)
    }

    /** 备份用：整份 JSON（`{"TEXT":"pkg/cls","IMAGE":"pkg/cls"}`）；没有默认目标时返回空串。 */
    suspend fun exportRawJson(context: Context): String {
        load(context)
        val current = _targets.value
        return if (current.isEmpty()) "" else json.encodeToString(current)
    }

    /** 恢复用：整份写回并替换内存状态。 */
    suspend fun importRawJson(context: Context, raw: String) {
        val parsed = decode(raw) ?: return
        _targets.value = parsed
        loaded = true
        runCatching {
            context.applicationContext.shareSheetDataStore.edit { prefs ->
                prefs[KEY] = json.encodeToString(parsed)
            }
        }
    }

    /** 纯解析（可单测）：坏数据一律返回 null，调用方保持原状态。 */
    internal fun decode(raw: String?): Map<String, String>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString<Map<String, String>>(raw)
        }.getOrNull()
    }

    private fun persist(context: Context, value: Map<String, String>) {
        val appContext = context.applicationContext
        val encoded = json.encodeToString(value)
        writeScope.launch {
            runCatching {
                appContext.shareSheetDataStore.edit { prefs -> prefs[KEY] = encoded }
            }.onFailure { Log.w(TAG, "persist defaults failed", it) }
        }
    }
}
