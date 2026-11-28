package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 当前是否处在**玻璃浮层**内部（`GlassBottomSheet` / `GlassDialog` 的内容区）。
 *
 * 用途：让卡片容器在玻璃面上自动换成 miuix 的玻璃观感 —— 半透明填充、能透出下层材质。
 * 依据 `miuix docs/components/glass/glasscard.md`：`GlassCard` 只用于玻璃面，
 * "on an opaque page it looks like a faint tinted card"，所以这个标记**只能**由玻璃浮层下发，
 * 普通页面必须保持 false，否则卡片会变成一层发灰的淡色。
 *
 * 与 [LocalMiuixPageBackdrop] 的区别：那个是"采样源"，这个是"我正站在玻璃上"，
 * 两者都由宿主/浮层提供，但一个给模糊用、一个给配色用，不要合并。
 */
val LocalMiuixGlassSurface = staticCompositionLocalOf { false }

/**
 * 当前是否在**卡片内部**（`CardSegment` 等容器）。
 *
 * 用途：玻璃浮层里的输入框只有在"直接躺在玻璃面上"时才该换玻璃样式；已经包在
 * 玻璃卡片里的输入框若再套一层玻璃，就是三层半透明叠加，对比度会发灰。
 * 示例 `GlassHalfBottomSheetDemo` 也是把 `GlassTextField` 直接放在抽屉 body、
 * 卡片里则用普通行。
 */
val LocalMiuixInCard = staticCompositionLocalOf { false }

/**
 * 把 [content] 标记为玻璃表面。由 `MiuixBottomSheet` / `MiuixConfirmDialog` /
 * `MiuixFormDialog` 的玻璃分支调用，业务层不需要手写。
 */
@Composable
fun MiuixGlassSurfaceScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMiuixGlassSurface provides true) {
        content()
    }
}

/**
 * 玻璃浮层内容的统一作用域：标记"站在玻璃上"，并**重新下发页面背板**。
 *
 * 为什么必须重新下发：`GlassDialog` 的内容渲染在根 scaffold 的浮层里，那里位于
 * `MiuixPageBackdropBox` 的 provider 之外 —— 若在弹窗内容里再开一个弹窗，它会读到
 * `LocalMiuixPageBackdrop == null`，从而退化成 `WindowDialog`（完全不玻璃）。
 * 重新 provide 之后，嵌套弹窗继承同一个背板，与 miuix 示例
 * （`GlassPage.kt` 内外两层都显式 `backdrop = pageBackdrop`）等价。
 */
@Composable
fun MiuixGlassDialogScope(
    backdrop: Backdrop?,
    content: @Composable () -> Unit,
) {
    // 只有 LayerBackdrop 能塞回 local（它就是页面背板的类型）；其它实现按 null 处理，
    // 嵌套弹窗退化成窗口弹窗，不影响正确性。
    val layerBackdrop = backdrop as? LayerBackdrop
    CompositionLocalProvider(
        LocalMiuixPageBackdrop provides layerBackdrop,
        LocalMiuixGlassSurface provides true,
    ) {
        content()
    }
}
