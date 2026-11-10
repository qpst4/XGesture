package com.slideindex.app.launcher

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.macro.MacroStore

/**
 * 「这个条目是不是分身」的统一判定，供各启动器在形状裁剪之外画黄点。
 *
 * - 打开应用 → [GestureAction.LaunchApp.cloneUserId]
 * - 运行宏 → 宏目标包名里的分身后缀（`包名\u0002userId`，见 [AppTarget]）
 *
 * 宏表是独立 prefs，逐格逐帧解析太贵：按 [MacroStore.revision] 缓存一份 id → 是否分身，
 * 宏一改（目标 App / 增删）代次变化，下次读取自动重建。
 */
fun QuickLauncherItem.isCloneTarget(context: Context): Boolean {
    if (appTarget?.isClone == true) return true
    if (type != QuickLauncherItemType.ACTION) return false
    val action = QuickLauncherItemCodec.parseActionPayload(payload) ?: return false
    return action.isCloneTarget(context)
}

fun GestureAction.isCloneTarget(context: Context): Boolean = when (this) {
    is GestureAction.LaunchApp -> cloneUserId > 0
    is GestureAction.RunMacro -> macroTargetIsClone(context, macroId)
    else -> false
}

private var cachedRevision = -1
private var cachedCloneByMacroId: Map<String, Boolean> = emptyMap()

@Synchronized
private fun macroTargetIsClone(context: Context, macroId: String): Boolean {
    if (macroId.isBlank()) return false
    val revision = MacroStore.revision
    if (revision != cachedRevision) {
        cachedCloneByMacroId = MacroStore.all(context).associate { macro ->
            macro.id to AppTarget.parse(macro.targetPackage).isClone
        }
        cachedRevision = revision
    }
    return cachedCloneByMacroId[macroId] == true
}
