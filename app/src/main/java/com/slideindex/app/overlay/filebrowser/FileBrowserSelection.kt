package com.slideindex.app.overlay.filebrowser

/**
 * 滑动勾选的**纯逻辑**：一个 anchor + 当前手指所在行，不持有任何 Android / Compose 状态。
 *
 * 交互（对齐 MT 管理器）：
 * 1. 在任意一行左/右滑 → 勾选该行，anchor = 该行；
 * 2. 继续滑到（或另一次滑到）另一行 → 勾选 anchor 到该行之间的**所有**行（含两端）；
 * 3. 只做并集：滑动只加勾，取消勾选仍靠点击。
 */
internal object FileBrowserSelection {
    /**
     * 计算 anchor 到 [targetPath] 之间需要勾选的行（按 [orderedPaths] 的显示顺序，含两端）。
     *
     * @return 需要并入选择的行；[anchorPath] 为空、与目标相同、或任一端已不在当前列表里时返回
     *   null —— 调用方应只勾选 [targetPath]，并把 anchor 重设为它。
     */
    fun rangeBetween(
        orderedPaths: List<String>,
        anchorPath: String?,
        targetPath: String,
    ): List<String>? {
        if (anchorPath == null || anchorPath == targetPath) return null
        val anchorIndex = orderedPaths.indexOf(anchorPath)
        val targetIndex = orderedPaths.indexOf(targetPath)
        if (anchorIndex < 0 || targetIndex < 0) return null
        val from = minOf(anchorIndex, targetIndex)
        val to = maxOf(anchorIndex, targetIndex)
        return orderedPaths.subList(from, to + 1).toList()
    }
}
