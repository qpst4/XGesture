package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 整页采样源：由宿主（`MainActivity` 的 `MiuixPageBackdropBox`）录制**整屏**（含顶栏/底栏）后下发，
 * 专供 `GlassDialog`、`GlassBottomSheet`、`GlassIconDropdownMenu` 这类全屏浮层采样。
 *
 * 与 `LocalMiuixScreenBackdrop`（页面内列表级采样，供顶栏/底栏自身使用）是两层：
 * 浮层用整页那层，栏用列表那层；null 表示模糊不可用，浮层应退化为纯色/窗口实现。
 *
 * 见 Mishka `docs/miuix-glass-migration.md` §2.2。
 */
val LocalMiuixPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
