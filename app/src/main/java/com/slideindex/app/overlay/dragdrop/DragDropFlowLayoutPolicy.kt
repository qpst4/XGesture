package com.slideindex.app.overlay.dragdrop

/**
 * 拖拽流转栏「收起态」的构成规则。
 *
 * 抽成纯函数是为了能单测：「开关开着却还留一个空枢纽」「文件拖拽冒出一个点开没内容的枢纽」
 * 这两类回归只有真机拖一次才看得见，放在浮层里靠肉眼守不住。
 */
internal object DragDropFlowLayoutPolicy {

    /**
     * 收起态是否保留「操作」枢纽。
     *
     * - 只有文字和图片有动作菜单，文件拖拽永远不需要枢纽；
     * - 内置动作被要求直接平铺时，动作已经是一级入口，枢纽没有可展开的东西。
     */
    fun shouldKeepHub(hasImage: Boolean, hasText: Boolean, inlineActions: Boolean): Boolean =
        (hasImage || hasText) && !inlineActions
}
