package com.slideindex.app.activity

import android.content.Context
import com.slideindex.app.iconpack.IconPackProvenanceStore
import java.io.File

/**
 * 「应用内直达」的图标来源桥：把「图标包选择页」的结果并入快捷方式自己的图标目录。
 *
 * 为什么不能直接用选择页回传的路径：那个页面把 PNG 落在全局图标目录
 * （`files/action_icons/`，见 `ActionIconStorage.saveBitmapForGlobalKey`），
 * 而 `ActivityShortcut.iconPath` 的约定是 `files/shortcut_icons/` 下的**相对路径**
 * （见 [ShortcutIconStorage.loadBitmap]，前缀对不上直接返回 null）。
 *
 * 这里做一次"读字节 → 复制进 shortcut_icons → 删中间文件"，保持快捷方式的
 * 图标模型不变（仍然是一张 PNG），老数据、恢复默认、导出都不受影响。
 */
object ShortcutIconPackBridge {

    /** 选择页用来记账 / 落盘的存储键；同一条快捷方式始终一致，重选会覆盖。 */
    fun storageKey(shortcutId: String): String = "activity_shortcut:$shortcutId"

    /**
     * @param pickedAbsolutePath 选择页回传的 PNG 绝对路径
     * @return `shortcut_icons/xxx.png` 相对路径；失败返回 null（调用方保持原图标）
     */
    fun importPickedIcon(
        context: Context,
        shortcutId: String,
        pickedAbsolutePath: String,
    ): String? {
        if (pickedAbsolutePath.isBlank()) return null
        val picked = File(pickedAbsolutePath)
        val bytes = runCatching { picked.takeIf { it.isFile }?.readBytes() }.getOrNull()
            ?: return null
        val saved = ShortcutIconStorage.saveIconFromBytes(context, bytes) ?: return null
        // 图片已经复制进 shortcut_icons：中间文件与"来自哪个图标包"的记录都不再需要
        runCatching { picked.delete() }
        IconPackProvenanceStore.clear(context, storageKey(shortcutId))
        return saved
    }
}
