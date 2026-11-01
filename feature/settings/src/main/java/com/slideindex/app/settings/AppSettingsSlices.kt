package com.slideindex.app.settings

import com.slideindex.app.floatball.FloatBallGestureCodec
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.SideGestureDefaults
import com.slideindex.app.translate.TranslateTargetLanguages

/**
 * [AppSettings] 按域拆出的分片。
 *
 * 拆分原因是硬性约束而非风格偏好：Kotlin 为带默认值的构造器/`copy()` 生成的合成方法，
 * 其调用点走 DEX 的 range 形式 invoke 指令，而该指令的寄存器计数字段只有 8 位（上限 255）。
 * 单个 data class 的参数数 P 必须满足 `1(this) + P + ceil(P/32) + 1(marker) <= 255`，
 * 即 P <= 245，超出后 D8 不报错但会生成非法指令，运行期由 ART 抛 VerifyError。
 *
 * 每个分片的字段名与拆分前 [AppSettings] 的扁平字段名保持一致，
 * [AppSettings] 侧以派生属性原样暴露，因此读取方无需改动。
 */

/** 边缘触钮几何、横竖屏触钮、手势规则与手势提示。 */
data class EdgeTriggerSettings(
    val leftEdgeEnabled: Boolean = true,
    val rightEdgeEnabled: Boolean = true,
    val leftEdgeTriggerWidthDp: Float = 20f,
    val rightEdgeTriggerWidthDp: Float = 20f,
    val bottomEdgeTriggerWidthDp: Float = 20f,
    val topEdgeTriggerWidthDp: Float = 20f,
    val leftTriggerTopFraction: Float = 0.30f,
    val rightTriggerTopFraction: Float = 0.30f,
    val leftTriggerHeightFraction: Float = 0.38f,
    val rightTriggerHeightFraction: Float = 0.38f,
    val leftTriggerHandles: List<com.slideindex.app.gesture.TriggerHandle> =
        listOf(com.slideindex.app.gesture.TriggerHandle.default(0.30f, 0.38f)),
    val rightTriggerHandles: List<com.slideindex.app.gesture.TriggerHandle> =
        listOf(com.slideindex.app.gesture.TriggerHandle.default(0.30f, 0.38f)),
    val bottomTriggerHandles: List<com.slideindex.app.gesture.TriggerHandle> =
        listOf(com.slideindex.app.gesture.TriggerHandle.bottomDefault()),
    val topTriggerHandles: List<com.slideindex.app.gesture.TriggerHandle> =
        listOf(com.slideindex.app.gesture.TriggerHandle.topDefault()),
    val leftTriggerHandlesLandscape: List<com.slideindex.app.gesture.TriggerHandle> = emptyList(),
    val rightTriggerHandlesLandscape: List<com.slideindex.app.gesture.TriggerHandle> = emptyList(),
    val bottomTriggerHandlesLandscape: List<com.slideindex.app.gesture.TriggerHandle> = emptyList(),
    val topTriggerHandlesLandscape: List<com.slideindex.app.gesture.TriggerHandle> = emptyList(),
    /** 横屏触钮已从竖屏完成一次性复制；为 true 后横屏布局/手势与竖屏完全独立。 */
    val landscapeTriggersInitialized: Boolean = false,
    val gestureRulesLandscape: List<GestureRule> = SideGestureDefaults.defaultRules(),
    val leftDefaultTriggerModeLandscape: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val rightDefaultTriggerModeLandscape: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val bottomDefaultTriggerModeLandscape: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val topDefaultTriggerModeLandscape: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val interceptSystemBackGesture: Boolean = false,
    /** 由 LSPosed 模块在输入层接管顶部触钮区域，覆盖状态栏下拉等系统手势。 */
    val systemGestureTakeoverTop: Boolean = false,
    /** 由 LSPosed 模块在输入层接管左右触钮区域，覆盖系统返回手势。 */
    val systemGestureTakeoverSides: Boolean = false,
    /** 由 LSPosed 模块在输入层接管底部触钮区域，覆盖上滑回桌面等系统手势。 */
    val systemGestureTakeoverBottom: Boolean = false,
    val limitMaxInterceptLength: Boolean = false,
    val leftDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val rightDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val bottomDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val topDefaultTriggerMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    val shortSwipeDistanceDp: Float = 60f,
    val longSwipeDistanceDp: Float = 120f,
    val gestureHintEnabled: Boolean = true,
    val gestureHintStyleId: Int = GestureHintStyle.BUBBLE.id,
    /** 手势动画相对手指的垂直偏移（dp）；0 为贴在手指高度，增大则远离指腹（侧/底向上，顶向下）。 */
    val gestureHintFingerOffsetDp: Float = 0f,
    val androidBackColorSourceId: Int = AndroidBackColorSource.APP_THEME.id,
    val swipeHoverDurationMs: Int = SwipeHoverDurationLimits.DEFAULT_MS,
    /** 触钮双击判定间隔；0 表示无法构成双击。 */
    val triggerDoubleTapIntervalMs: Int = 300,
    val animationStyles: AnimationStyles = AnimationStyles(),
    val gestureAngles: GestureAngles = GestureAngles(),
    /** 竖屏下软键盘弹出时如何影响触发器（悬浮球 + 侧边触钮）。 */
    val keyboardTriggerBehaviorPortrait: KeyboardTriggerBehavior = KeyboardTriggerBehavior.OVERLAY,
    /** 横屏下软键盘弹出时如何影响触发器（悬浮球 + 侧边触钮）。 */
    val keyboardTriggerBehaviorLandscape: KeyboardTriggerBehavior = KeyboardTriggerBehavior.OVERLAY,
    /** 竖屏下键盘「收窄」时触发器缩至初始宽度的百分比（1–99）。 */
    val keyboardTriggerNarrowPercentPortrait: Int = KeyboardTriggerNarrowPercents.DEFAULT,
    /** 横屏下键盘「收窄」时触发器缩至初始宽度的百分比（1–99）。 */
    val keyboardTriggerNarrowPercentLandscape: Int = KeyboardTriggerNarrowPercents.DEFAULT,
)

/** 启动策略、应用过滤、快捷启动 / 蜂窝 / 圆环启动器槽位与命令。 */
data class LauncherSettings(
    val appLaunchPolicyId: Int = AppLaunchPolicy.ALWAYS_FULLSCREEN.id,
    val longPressLaunchDurationMs: Int = 450,
    val hiddenAppPackages: Set<String> = emptySet(),
    val freezerAppPackages: Set<String> = emptySet(),
    val freezerBootstrapExcludedPackages: Set<String> = emptySet(),
    val freezerShowInLauncher: Boolean = false,
    /** 「管理应用」的「全部」是否也列出系统应用；原先是页面内的临时状态，挪到设置页后必须持久化。 */
    val freezerShowSystemApps: Boolean = false,
    /** 冰箱工作模式：面板底部按钮默认执行冻结还是暂停，见 [FreezerWorkMode]。 */
    val freezerWorkModeId: Int = FreezerWorkMode.DEFAULT.id,
    /** 每个成员上次被设成的目标态；批量动作优先按它还原，见 [FreezerAppIntent]。 */
    val freezerAppIntents: Map<String, FreezerAppIntent> = emptyMap(),
    val expandPanelSlotActions: List<com.slideindex.app.gesture.GestureAction?> = List(8) { null },
    /** “切换上一应用”动作忽略的包名黑名单。 */
    val previousAppExcludedPackages: Set<String> = emptySet(),
    val excludedAppScopes: Map<String, ExcludedAppScopes> = emptyMap(),
    val excludedAppDefaultScopes: ExcludedAppScopes = ExcludedAppScopes.ALL,
    val gestureRules: List<GestureRule> = SideGestureDefaults.defaultRules(),
    val quickLauncherPanels: List<com.slideindex.app.launcher.QuickLauncherPanel> = emptyList(),
    val quickLauncherDisplay: QuickLauncherDisplaySettings = QuickLauncherDisplaySettings(),
    val honeycombLauncher: List<com.slideindex.app.launcher.QuickLauncherItem> = emptyList(),
    val honeycombDisplay: HoneycombDisplaySettings = HoneycombDisplaySettings(),
    val appCarouselSwitcher: AppCarouselSwitcherSettings = AppCarouselSwitcherSettings(),
    val fvRingLauncherVertical: FvRingLauncherSettings = FvRingLauncherSettings(),
    val fvRingLauncherHorizontal: FvRingLauncherSettings = FvRingLauncherSettings(),
    /** 为 true 时顶/底与左/右共用同一套外观（圈数、尺寸、半径等）。 */
    val fvRingLauncherLinkAppearanceAxes: Boolean = FvRingLauncherLinkFlags.DEFAULT_LINK_APPEARANCE_AXES,
    /** 为 true 时顶/底与左/右共用同一套槽位。 */
    val fvRingLauncherLinkSlotAxes: Boolean = FvRingLauncherLinkFlags.DEFAULT_LINK_SLOT_AXES,
    val holographicLauncher: HolographicLauncherSettings = HolographicLauncherSettings(),
    /** 「快速启动轮盘」：可创建多个自定义轮盘。 */
    val quickWheels: List<QuickWheel> = emptyList(),
    val shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
    val activityShortcuts: List<com.slideindex.app.activity.ActivityShortcut> = emptyList(),
    /** 桌面图标长按菜单顺序；空表示用默认顺序。 */
    val launcherShortcutMenuOrder: List<String> = emptyList(),
    /** 桌面图标长按菜单里被关闭的条目。 */
    val launcherShortcutMenuDisabled: Set<String> = emptySet(),
)

/** 边缘手势接力指针：指针、摇杆、轮盘与边缘动作。 */
data class FloatingPointerSettings(
    /**
     * Pointer speed shown in settings (0.2–0.75 ≈ 20%–75%). Higher = faster pointer.
     */
    val floatingPointerSensitivityFraction: Float = 0.52f,
    /** Virtual joystick diameter in screen pixels (QC default 275). */
    val floatingPointerJoystickDiameterPx: Float = 275f,
    /** Ring pointer outer diameter in screen pixels. */
    val floatingPointerPointerDiameterPx: Float = 100f,
    /** Pointer design id; ring style by default for backward compatibility. */
    val floatingPointerDesignId: String = FloatingPointerDesignIds.RING,
    /** Ring pointer band thickness in screen pixels. */
    val floatingPointerRingThicknessPx: Float = 12f,
    /** Ring pointer center dot diameter in screen pixels. */
    val floatingPointerDotDiameterPx: Float = 15f,
    val floatingPointerRingColorArgb: Int = 0xFFFFFFFF.toInt(),
    val floatingPointerFillColorArgb: Int = 0x19000000,
    val floatingPointerDotColorArgb: Int = 0xFFFFFFFF.toInt(),
    val floatingPointerClickVisualFeedbackEnabled: Boolean = true,
    val floatingPointerClickHapticEnabled: Boolean = true,
    val floatingPointerRippleColorArgb: Int = 0xFFFD746C.toInt(),
    /** Click ripple diameter in dp (QC default 80dp). */
    val floatingPointerRippleSizeDp: Float = 80f,
    /** Click ripple animation duration in ms (QC default 500). */
    val floatingPointerRippleDurationMs: Int = 500,
    val floatingPointerTrailTypeId: Int = FloatingPointerTrailType.HIGH_DETAIL.id,
    val floatingPointerTrailDurationMs: Int = 150,
    val floatingPointerTrailColorArgb: Int = 0x66FF5252,
    val floatingPointerHideWhenJoystickReleased: Boolean = false,
    /** QC `clickDistanceThreshold`: max tracker travel to still count as click/long-press (dp). */
    val floatingPointerClickDistanceThresholdDp: Float = 6f,
    val floatingPointerJoystickInnerColorArgb: Int = 0x80FFFFFF.toInt(),
    val floatingPointerJoystickOuterColorArgb: Int = 0x80C0C0C0.toInt(),
    val floatingPointerJoystickGradientRadiusFraction: Float = 1f,
    val floatingPointerHideOnOutsideClick: Boolean = true,
    val floatingPointerHideOnQuickSwipe: Boolean = true,
    val floatingPointerHideWhenIdle: Boolean = true,
    val floatingPointerIdleHideDelayMs: Int = 3000,
    /**
     * Continued edge handoff: on finger-up, click at pointer then dismiss the overlay.
     * Does not affect resident joystick tap-to-click.
     *
     * 默认关：新手拖出指针只是想"把指针拿出来用"，松手立刻在指针处点一下并关掉，
     * 看起来就像"什么都没发生"，反而让人以为功能没生效。需要这个手势的人在设置里自己开。
     */
    val floatingPointerReleaseClickAndDismiss: Boolean = false,
    /**
     * Continued edge handoff: dwell ~280ms on the pointer to enter element/region pick mode.
     */
    val floatingPointerHoverEnterSelect: Boolean = false,
    /** Action executed when the joystick is long-pressed. Defaults to opening the radial action ring. */
    val floatingPointerJoystickLongPressAction: com.slideindex.app.gesture.GestureAction = GestureAction.OpenFloatingPointerRadialMenu,
    /** Keep the radial action ring visible around the joystick without long-press. */
    val floatingPointerRadialAlwaysVisible: Boolean = false,
    val floatingPointerRadialLongPressMs: Int = 500,
    val floatingPointerRadialOuterDiameterPx: Float = 440f,
    val floatingPointerRadialInnerDiameterPx: Float = 192f,
    val floatingPointerRadialOuterColorArgb: Int = 0xE62B3D4F.toInt(),
    val floatingPointerRadialInnerColorArgb: Int = 0xE61A1A28.toInt(),
    val floatingPointerRadialDividerThicknessPx: Float = 4f,
    val floatingPointerRadialDividerColorArgb: Int = 0x22FFFFFF,
    val floatingPointerRadialIconSizeFraction: Float = 0.85f,
    val floatingPointerRadialIconColorArgb: Int = 0xFFFFFFFF.toInt(),
    val floatingPointerRadialSlotActions: List<com.slideindex.app.gesture.GestureAction> =
        FloatingPointerRadialMenuCodec.defaultSlots(),
    /** QC edge actions: pointer pushed past screen edge triggers configured shortcuts. */
    val floatingPointerEdgeThresholdDp: Float = 30f,
    val floatingPointerEdgePreviewSensitivity: Int = 3,
    val floatingPointerEdgePreviewGlowSize: Int = 4,
    val floatingPointerEdgePreviewShowIcon: Boolean = true,
    val floatingPointerEdgeVisualSizeDp: Float = 0f,
    val floatingPointerEdgeVisualOpacity: Int = 75,
    val floatingPointerEdgeVisualColorArgb: Int = 0xFFFD746C.toInt(),
    val floatingPointerEdgeActionsConfig: FloatingPointerEdgeActionsConfig =
        FloatingPointerEdgeActionsCodec.defaultConfig(),
)

/** 触钮手势「指尖轮盘」：围绕手指圆形分布的快捷图标。 */
data class FingertipRingSettings(
    val slotCount: Int = FingertipRingCodec.DEFAULT_SLOT_COUNT,
    val slotActions: List<com.slideindex.app.gesture.GestureAction> = FingertipRingCodec.defaultSlots(),
    val orbitRadiusPx: Float = FingertipRingCodec.DEFAULT_ORBIT_RADIUS_PX,
    val iconSizePx: Float = FingertipRingCodec.DEFAULT_ICON_SIZE_PX,
)

/** FV 风格常驻悬浮球：外观、取词、滑动阈值与翻译。 */
data class FloatBallSettings(
    /** FV-style persistent float ball; independent from edge-gesture floating pointer. */
    val floatBallEnabled: Boolean = false,
    val floatBallSizeDp: Float = 48f,
    /** Pick cross arm length from center to tip in dp. */
    val floatBallPickCrossArmDp: Float = 7.5f,
    val floatBallOpacity: Float = 0.88f,
    /**
     * 闲置隐身：超过 [floatBallIdleDelayMs] 没有触摸球区后，球改按 [floatBallIdleOpacity] 绘制。
     *
     * 只说"看不见"，不影响命中：隐身期间球窗照常接收单击/滑动/长按。
     */
    val floatBallHideWhenIdle: Boolean = true,
    /** 闲置时停止 GIF 出帧（保留当前帧，触摸后恢复播放）。 */
    val floatBallIdleFreezeGif: Boolean = true,
    /** 闲置隐身生效时球的不透明度（0–1，0 = 完全看不见）。 */
    val floatBallIdleOpacity: Float = 0.38f,
    /** 距最后一次触摸多久算闲置（ms，0 = 松手后立即隐身）。 */
    val floatBallIdleDelayMs: Int = 3000,
    /** Fraction of ball width visible on screen when docked to an edge (0.5–1). */
    val floatBallVisibleFraction: Float = 1f,
    /** CUSTOM mode only: ball center X as fraction of screen width. */
    val floatBallCustomCenterXFraction: Float = 0.92f,
    /** Ball center Y as fraction of screen height (0–1). */
    val floatBallPositionYFraction: Float = 0.55f,
    /** When a11y text pick fails, capture screen region and run on-device OCR. */
    val floatBallOcrFallbackEnabled: Boolean = true,
    /** Selected downloadable OCR model id; empty means OCR fallback is unavailable until a model is installed. */
    val floatBallOcrModelId: String = "",
    /** Download OCR models on Wi-Fi only. */
    val ocrDownloadWifiOnly: Boolean = true,
    /**
     * Float-ball pick pointer horizontal speed (0–0.75, higher = faster).
     * Independent from [FloatingPointerSettings.floatingPointerSensitivityFraction].
     */
    val floatBallPointerSpeedFraction: Float = 0.35f,
    /**
     * Float-ball pick pointer vertical speed (0–0.75, higher = faster).
     * Independent from [floatBallPointerSpeedFraction].
     */
    val floatBallPointerSpeedVerticalFraction: Float = 0.35f,
    /**
     * 取词光标「起步加速区」宽度（0–1，占屏幕该轴尺寸的比例；0 = 关闭）。
     *
     * 区内光标至少按 2 倍速移动（见 `FloatingPointerBounds.PICK_ACCEL_GAIN`）：从边缘起手时先把
     * 十字甩到前面，低速档也能在一屏行程内够到对侧；区外回到用户自己的倍率做精调。
     */
    val floatBallPickAccelZoneFraction: Float = 0.5f,
    val floatBallPositionMode: FloatBallPositionMode = FloatBallPositionMode.RIGHT,
    /** Which side shows the ball when [floatBallPositionMode] is [FloatBallPositionMode.BOTH_EDGES]. */
    val floatBallActiveSide: FloatBallSide = FloatBallSide.RIGHT,
    /** Edge line / capture strip height as fraction of screen height. */
    val floatBallLineHeightFraction: Float = 0.08f,
    /**
     * 边缘线触发带宽度（dp）。
     *
     * 以前是「屏宽百分比」（1%–50%），横屏 / 折叠展开时窗口一宽，触发带就成倍变宽，
     * 与球体的 dp 尺寸对不上。改成 dp 后同一设置在竖屏、横屏、展开态下保持同一物理宽度。
     */
    val floatBallLineWidthDp: Float = DEFAULT_LINE_WIDTH_DP,
    val floatBallLineOpacity: Float = 0.9f,
    /** 悬浮球手势 → 动作绑定。 */
    val floatBallGestureActions: Map<FloatBallGestureType, GestureAction> =
        FloatBallGestureCodec.defaultActions(),
    /** 悬浮球外观样式。 */
    val floatBallStyleType: FloatBallStyleType = FloatBallStyleType.DEFAULT,
    /** 自定义图片 URI（[floatBallStyleType] 为 CUSTOM_IMAGE 时使用）。 */
    val floatBallCustomImageUri: String = "",
    /**
     * 自定义图片「不处理」开关：开启后球窗口按**原图宽高比**生成，既不居中裁剪、也不套圆形蒙版。
     * 仅 [floatBallStyleType] 为 CUSTOM_IMAGE 时生效；其余样式仍按圆球显示。
     */
    val floatBallCustomImageNoCrop: Boolean = false,
    /**
     * 自定义图片原图宽高比（width / height）。
     *
     * 在「导入图片」时与 [floatBallCustomImageUri] 一起落盘，布局期只需同步读一个 Float，
     * 不必在窗口布局前解码整张图。`0f` 表示未知（旧数据或读取失败）：此时
     * [floatBallCustomImageNoCrop] 退化为正方形窗口 + 完整显示（会留透明边）。
     */
    val floatBallCustomImageAspect: Float = 0f,
    /** 幻灯片图片 URI 列表（[floatBallStyleType] 为 SLIDESHOW 时使用）。 */
    val floatBallSlideshowUris: List<String> = emptyList(),
    /** GIF 图片 URI（[floatBallStyleType] 为 GIF 时使用）。 */
    val floatBallGifUri: String = "",
    /** Gap between ball edge and pick crosshair in dp (above / below). */
    val floatBallPickOffsetDp: Float = 48f,
    /** Body text size for pick-result panel in sp. */
    val floatBallPickTextSizeSp: Float = 15f,
    /** Screen-height fraction for smooth above→below pick transition near bottom. */
    val floatBallPickBottomTransitionFraction: Float = 0f,
    /** Pick panel: show text + search by default; image section stays collapsed until the image row is tapped. */
    val floatBallPickTextFirstPanel: Boolean = false,
    /** Pick panel presentation style: INTEGRATED_SCROLL vs TAB_PAGED. */
    val floatBallPickPanelStyle: PickResultPanelStyle = PickResultPanelStyle.TAB_PAGED,
    /** 取词面板在屏幕上的落位方式：贴底（默认）或屏幕中间。 */
    val floatBallPickPanelPlacement: PickResultPanelPlacement = PickResultPanelPlacement.BOTTOM_DOCKED,
    /** Pick panel: default search grid display state (REMEMBER_LAST, ALWAYS_EXPANDED, ALWAYS_COLLAPSED). */
    val floatBallPickSearchGridDefaultState: PickResultSearchGridDefaultState = PickResultSearchGridDefaultState.REMEMBER_LAST,
    /** Pick panel: last search grid expanded state for REMEMBER_LAST mode. */
    val floatBallPickSearchGridLastExpanded: Boolean = false,
    /** Pick panel: 文本区「点词」进入面板时的默认状态（REMEMBER_LAST / ALWAYS_ON / ALWAYS_OFF）。 */
    val floatBallPickTextModeDefault: PickResultTextModeDefault = PickResultTextModeDefault.REMEMBER_LAST,
    /**
     * Pick panel: 上次退出面板时「点词」的状态，仅 REMEMBER_LAST 生效。
     *
     * 存 overlay 的 `PickResultTextMode` 存储键（见 PickResultTextModeStore）；
     * 空串表示还没有记录过，读取方需回落默认值。
     */
    val floatBallPickTextModeLastMode: String = "",
    /** Pick panel: engine used by search-button long-press quick search (null = long press idle). */
    val floatBallPickDefaultSearchEngineId: String? = null,
    /** Pick panel: auto-select all text when the panel opens after word pick or screenshot. */
    val floatBallPickAutoSelectAll: Boolean = false,
    /** Pick panel: dismiss after tapping copy; long-press copy keeps the panel open. */
    val floatBallPickCopyDismissPanel: Boolean = false,
    /** Pick panel: primary copy chip on the left or right of the bottom action bar. */
    val floatBallPickCopyButtonPosition: PickResultCopyButtonPosition = PickResultCopyButtonPosition.LEFT,
    val floatBallPickImageToolbarPosition: PickResultImageToolbarPosition = PickResultImageToolbarPosition.LEFT,
    /** Pick panel: enable subtle haptic feedback for copy and word tap drag selection. */
    val floatBallPickHapticEnabled: Boolean = true,
    /**
     * 拖动悬浮球时是否进入取词（红十字光标 + 悬停预览 + 松手弹取词面板）。
     *
     * 关闭后这条拖动只剩"球跟手"，不显示光标/选区、不弹面板；滑动手势与线条换边照旧，
     * 挪球仍走圆环工具栏「移动」。默认开 = 保持历史行为。
     */
    val floatBallDragPickEnabled: Boolean = true,
    /** FV enable_main_icon_paste: drag float ball over an input field and release to paste latest history. */
    val floatBallDragPasteEnabled: Boolean = true,
    /** Bottom pick panel slide-in duration in ms (0 = no animation). */
    val floatBallPickPanelEnterAnimationMs: Int = 64,
    /** Bottom pick panel slide-out duration in ms (0 = no animation). */
    val floatBallPickPanelExitAnimationMs: Int = 64,
    /** Finger travel before full-screen pointer mode activates. */
    val floatBallPointerSlopDp: Float = 3f,
    /** Hover dwell time before cursor locks start position for text pick or screenshot (ms). */
    val floatBallHoverPauseDelayMs: Int = 400,
    /** 悬浮球/侧边条长按手势的触发时长（ms）。 */
    val floatBallLongPressMs: Int = 500,
    /** Deadzone distance from pause origin to cancel screenshot / enter drag (dp). */
    val floatBallRegionalCancelSlopDp: Float = 16f,
    /** FV down_swipe_short_distance_2：短滑阈值 = percent × 40dp / 100。 */
    val floatBallDownSwipeShortPercent: Float = 200f,
    /** FV side_swipe_short_distance_2：短滑阈值 = percent × 40dp / 100。 */
    val floatBallSideSwipeShortPercent: Float = 320f,
    /** 上滑短滑阈值 = percent × 40dp / 100；超过即为长滑。 */
    val floatBallUpSwipeShortPercent: Float = 256f,
    /**
     * 悬浮球手势打开的浮层（快速启动器 / 圆环启动器）对齐手指**按下**位置。
     *
     * false = 沿用历史行为：对齐手势判定成立时（滑动为松手时）手指所在位置。
     * 只影响悬浮球触发的这两个浮层，边滑手势与其它浮层不受影响。
     */
    val floatBallOverlayAnchorAtTouchDown: Boolean = false,
    /** 悬浮球专属手势振动：判定成立与松手执行时振动（仍受首页「震动」总开关约束）。 */
    val floatBallGestureHapticEnabled: Boolean = true,
    /**
     * 悬浮球手势绑定「返回」时，是否**额外**由我们震一下。
     *
     * 部分机型（如 Flyme）在返回真正生效时系统自己会震一次，这时再震就是"两下"；
     * 关掉只保留系统那一下。系统不给返回震动的机型保持开启即可（默认开）。
     */
    val floatBallBackGestureHapticEnabled: Boolean = true,
    /**
     * 长按悬浮球呼出圆环启动器时，自动选中球正下方的第一个槽位；
     * 松手即启动该槽位，不必先滑过去（默认关，保持「不动松手 = 取消」的旧行为）。
     */
    val floatBallLongPressAutoSelectRingSlot: Boolean = false,
    /** When false, translate opens Google Translate in browser; when true, shows in-app overlay. */
    val floatBallInstantTranslate: Boolean = true,
    val floatBallTranslateEngine: FloatBallTranslateEngine = FloatBallTranslateEngine.GOOGLE,
    /** BCP-47 style target language code, or [TranslateTargetLanguages.FOLLOW_APP] to follow app UI language. */
    val floatBallTranslateTargetLang: String = TranslateTargetLanguages.FOLLOW_APP,
    /** Engine [FloatBallTranslateEngine.LOCAL_APP]: 用户指定的翻译 App 包名；空 = 未选择。 */
    val floatBallTranslateAppPackage: String = "",
    /** Pick-result card transparency while the in-app translate overlay is open (0=opaque, 1=transparent). */
    val floatBallImageSearchPickPanelTransparency: Float = 0.65f,
    /** Save shared long-image OCR results for later re-open from pick settings. */
    val shareImageOcrHistoryEnabled: Boolean = true,
    /**
     * 浮窗里的「分享」是否改用内置分享面板（网格 + 长按钉住 + 直发目标）。
     *
     * 默认关：老行为是弹系统分享面板。开启后从浮窗发起分享不再离开浮层，
     * 也顺带解决"系统面板被自家浮层盖住"的问题（见 `ShareSheetOverlayWindow`）。
     */
    val shareSheetEnabled: Boolean = false,
) {
    companion object {
        /**
         * 自定义图片宽高比的可信区间。
         *
         * 只防"退化的歪图"（1px 宽/高的坏文件、除零与溢出），**不是**限宽策略：
         * 用户明确要求不处理模式下不限制球宽，所以 20:1 的图仍然按 20:1 呈现。
         */
        const val IMAGE_ASPECT_MIN = 0.05f
        const val IMAGE_ASPECT_MAX = 20f

        /** 触发带宽度的默认值：与旧默认 4%（在 1080px / density 3 的竖屏上被 24dp 下限顶住）一致。 */
        const val DEFAULT_LINE_WIDTH_DP = 24f
        /** 下限 0 = 该侧线条只显示、不接收触摸。 */
        const val MIN_LINE_WIDTH_DP = 0f
        const val MAX_LINE_WIDTH_DP = 96f

        /**
         * 旧「屏宽百分比」→ dp 的迁移基准（dp）。
         *
         * 用固定常量而不是当前窗口宽度：迁移结果必须与横竖屏 / 折叠展开无关，
         * 否则同一个旧值在展开态会被放大。取普通手机竖屏的典型宽度，保留用户原来的观感。
         */
        const val LEGACY_LINE_WIDTH_REFERENCE_WIDTH_DP = 400f

        /**
         * 老数据迁移下限。
         *
         * 旧实现里 4% 屏宽在普通竖屏上会被 24dp 下限顶住，迁移时沿用这条下限，
         * 避免默认用户升级后触发带反而变窄；新设置允许用户自己调到 0。
         */
        private const val LEGACY_MIN_LINE_WIDTH_DP = DEFAULT_LINE_WIDTH_DP

        /** 把旧的屏宽百分比换算成 dp（带范围夹取），只用于迁移老数据。 */
        fun legacyLineWidthDp(fraction: Float): Float =
            (fraction * LEGACY_LINE_WIDTH_REFERENCE_WIDTH_DP)
                .coerceIn(LEGACY_MIN_LINE_WIDTH_DP, MAX_LINE_WIDTH_DP)
    }
}

/** 剪贴板监听、历史浮标、输入浮窗与暂存面板。 */
data class ClipboardSettings(
    /** Background clipboard monitoring via Shizuku/Root privileged listener. */
    val clipboardBackgroundMonitoring: Boolean = true,
    val clipboardBackgroundMonitoringMode: ClipboardMonitoringMode = ClipboardMonitoringMode.FOLLOW_PRIVILEGE,
    /** 提权通道（UI 主选项）：跟随全局特权 / Shizuku / Root / LSPosed / 标准。 */
    val clipboardMonitoringChannel: ClipboardMonitoringChannel = ClipboardMonitoringChannel.FOLLOW_PRIVILEGE,
    /** 采集方式（UI 次选项）：隐藏 API / 系统日志。 */
    val clipboardMonitoringCapture: ClipboardMonitoringCapture = ClipboardMonitoringCapture.LOGCAT,
    /**
     * LSPosed 模式的白名单：这些包会被系统当作默认输入法放行，可在后台直接读剪贴板。
     * 默认含本应用自身，用户可在设置里移除或添加其它应用。
     */
    val clipboardLsposedWhitelist: Set<String> = setOf(CLIPBOARD_LSPOSED_SELF_PACKAGE),
    /** Show AOSP-style clipboard preview overlay when a copy is captured. */
    val clipboardOverlayEnabled: Boolean = false,
    /** Uniform overlay scale, in percent (50–100). */
    val clipboardOverlayScalePercent: Int = ClipboardOverlayScale.DEFAULT_PERCENT,
    /** Monitor MediaStore for system/third-party screenshots and add to clipboard history. */
    val clipboardScreenshotMonitoring: Boolean = false,
    /** Max clipboard history entries; [ClipboardHistoryCapacity.UNLIMITED] means no limit. */
    val clipboardHistoryMaxEntries: Int = 100,
    /** Persistent edge handle showing clipboard history overlay. */
    val clipboardHistoryFloatEnabled: Boolean = false,
    /** When true, the history float handle is shown in landscape orientation. */
    val clipboardHistoryFloatEnabledLandscape: Boolean = false,
    /** When true, the history float handle cannot be dragged vertically. */
    val clipboardHistoryFloatLockPosition: Boolean = true,
    /** Which screen edge the history float handle sticks to (dragging it across the middle switches sides). */
    val clipboardHistoryFloatHandleSide: HistoryFloatHandleSide = HistoryFloatHandleSide.DEFAULT,
    /** Saved handle top offset in px for portrait; [HistoryFloatHandlePosition.UNSET_POSITION] = default. */
    val clipboardHistoryFloatHandleYPortrait: Int = HistoryFloatHandlePosition.UNSET_POSITION,
    /** Saved handle top offset in px for landscape; [HistoryFloatHandlePosition.UNSET_POSITION] = default. */
    val clipboardHistoryFloatHandleYLandscape: Int = HistoryFloatHandlePosition.UNSET_POSITION,
    /** Floating clipboard window for input scenarios (grid, resize, paste on tap). */
    val clipboardFloatEnabled: Boolean = false,
    /** When true, show a chip above the keyboard before expanding the full window. */
    val clipboardFloatShowChip: Boolean = true,
    /** When true, the chip follows the keyboard top edge instead of a saved position. */
    val clipboardFloatChipFollowIme: Boolean = true,
    val clipboardFloatChipX: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val clipboardFloatChipY: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    /** When true, the expanded panel uses saved x/y instead of IME-adaptive placement. */
    val clipboardFloatPanelPinPosition: Boolean = false,
    val clipboardFloatSingleLineEntryClickAction: ClipboardFloatEntryClickAction = ClipboardFloatEntryClickAction.PASTE,
    val clipboardFloatSingleLineEntryLongPressAction: ClipboardFloatEntryLongPressAction =
        ClipboardFloatEntryLongPressAction.WORD_TAP,
    val clipboardFloatCardEntryClickAction: ClipboardFloatEntryClickAction = ClipboardFloatEntryClickAction.PASTE,
    val clipboardFloatCardEntryLongPressAction: ClipboardFloatEntryLongPressAction =
        ClipboardFloatEntryLongPressAction.DRAG_DROP,
    val clipboardFloatListStyleId: Int = ClipboardFloatListStyle.SINGLE_LINE.id,
    val clipboardFloatPortraitGeometry: ClipboardFloatOrientationGeometry = ClipboardFloatOrientationGeometry(),
    val clipboardFloatLandscapeGeometry: ClipboardFloatOrientationGeometry = ClipboardFloatOrientationGeometry(),
    /** @deprecated Use [clipboardFloatPortraitGeometry]; kept for legacy readers. */
    val clipboardFloatPanelWidthDp: Int = ClipboardFloatWindowMetrics.DEFAULT_WIDTH_DP,
    /** @deprecated Use [clipboardFloatPortraitGeometry]. */
    val clipboardFloatPanelHeightDp: Int = ClipboardFloatWindowMetrics.DEFAULT_HEIGHT_DP,
    /** @deprecated Use [clipboardFloatPortraitGeometry]. */
    val clipboardFloatPanelX: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    /** @deprecated Use [clipboardFloatPortraitGeometry]. */
    val clipboardFloatPanelY: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val clipboardFloatBlockedPackages: Set<String> = emptySet(),
    val clipboardFloatPasteHapticEnabled: Boolean = false,
    /** When true: scan targets / multi-field picker / rect paste; when false: paste into focused field only. */
    val clipboardPasteFvStyleEnabled: Boolean = false,
    val clipboardFloatPasteSuccessCount: Int = 0,
    val clipboardFloatPasteFailCount: Int = 0,
    val clipboardFloatAlpha: Float = 1.0f,
    val clipboardFloatAutoDimWhenUnfocused: Boolean = false,
    val clipboardFloatAutoCloseSeconds: Int = 0,
    /**
     * 剪贴板浮窗（chip 与大窗）背后的跨窗模糊（API 31+）。
     *
     * 默认关闭：模糊会让 chip 在纯色桌面上看起来只是一块 tint，且老用户升级后观感突变。
     * 真正能不能糊还要过 [com.slideindex.app.overlay.OverlayBlurGate]（总开关 × 系统能力）。
     */
    val clipboardFloatBlurEnabled: Boolean = false,
    val clipboardFloatBlurRadiusDp: Int = AppSettings.CLIPBOARD_FLOAT_BLUR_RADIUS_DEFAULT_DP,
    /** Cross-window blur behind stash/history side panel (API 31+). */
    val stashPanelBackgroundBlurEnabled: Boolean = false,
    val stashPanelBackgroundBlurRadiusDp: Int = AppSettings.STASH_PANEL_BLUR_RADIUS_DEFAULT_DP,
    /**
     * 从贴边指示条打开收纳面板时先显示哪个页签。
     * 默认 [StashPanelHandleOpenTab.CLIPBOARD] = 加这条设置之前的历史行为（点指示条一直开剪贴板）。
     */
    val stashPanelHandleOpenTab: StashPanelHandleOpenTab = StashPanelHandleOpenTab.CLIPBOARD,
    /** 侧拉面板宽度档位，见 [StashPanelWidthMode]。默认保持设计稿规格。 */
    val stashPanelWidthMode: StashPanelWidthMode = StashPanelWidthMode.STANDARD,
    /** 侧拉面板用户直接拖拽的自定义宽度比例。 */
    val stashPanelCustomWidthFraction: Float? = null,
)

/** 搜索面板与搜索引擎。 */
data class SearchPanelSettings(
    /** Configured text/image search engines for pick panel. */
    val searchEngines: List<SearchEngineConfig> = emptyList(),
    val searchEngineGridColumns: Int = 5,
    val searchEngineGridRows: Int = 2,
    val searchEngineShowLabels: Boolean = true,
    val searchPanelDefaultEngineId: String? = null,
    val searchPanelInputBehavior: SearchPanelInputBehavior = SearchPanelInputBehavior.KEEP,
    val searchPanelEnterAction: SearchPanelEnterAction = SearchPanelEnterAction.SEARCH_ENGINE,
    val searchPanelContactSearchEnabled: Boolean = true,
    val searchPanelFileSearchEnabled: Boolean = true,
    val searchPanelAppSearchEnabled: Boolean = true,
    val searchPanelSettingsSearchEnabled: Boolean = true,
    /** 应用快捷方式（清单 android.app.shortcuts）搜索。 */
    val searchPanelShortcutSearchEnabled: Boolean = true,
    /** 剪贴板历史内容搜索。 */
    val searchPanelClipboardSearchEnabled: Boolean = true,
    /** Enum names of enabled file types; empty means all. */
    val searchPanelFileTypesEnabled: Set<String> = emptySet(),
    val searchPanelFileShowFolders: Boolean = false,
    val searchPanelFileShowSystemFiles: Boolean = false,
    val searchPanelFilePreviewsEnabled: Boolean = true,
    val searchPanelFileFolderWhitelist: Set<String> = emptySet(),
    val searchPanelFileFolderBlacklist: Set<String> = emptySet(),
    val searchPanelBarPosition: SearchPanelBarPosition = SearchPanelBarPosition.TOP,
    val searchPanelListOrder: SearchPanelListOrder = SearchPanelListOrder.TOP_DOWN,
    val searchPanelAppDisplayStyle: SearchPanelAppDisplayStyle = SearchPanelAppDisplayStyle.ICONS,
    val searchPanelCalculatorEnabled: Boolean = true,
    val searchPanelBackgroundStyle: Int = SearchPanelBackgroundStyle.DEFAULT,
    val searchPanelBlurRadiusDp: Int = AppSettings.SEARCH_PANEL_BLUR_RADIUS_DEFAULT_DP,
    val searchPanelDimPercent: Int = AppSettings.SEARCH_PANEL_DIM_DEFAULT_PERCENT,
    val searchPanelWebSuggestionsEnabled: Boolean = true,
    val searchPanelWebSuggestionsCount: Int = 5,
    val searchPanelHistoryMaxEntries: Int = SearchPanelHistoryCapacity.DEFAULT,
    val searchPanelSectionAliases: SearchPanelSectionAliasSettings = SearchPanelSectionAliasSettings(),
    val aggregatedImageSearchEngines: List<AggregatedImageSearchEngineConfig> =
        AppSettings.defaultAggregatedImageSearchEngines(),
)

/** 自由小窗模式与尺寸。 */
data class FreeWindowSettings(
    val freeWindowEnabled: Boolean = false,
    val freeWindowModeId: Int = FreeWindowMode.detectDefault().id,
    // 竖屏预置
    val freeWindowWidthFraction: Float = 0.8f,
    val freeWindowHeightFraction: Float = 0.55f,
    val freeWindowLeftFraction: Float = 0.1f,
    val freeWindowTopFraction: Float = 0.15f,
    /**
     * 跟随触发位置：小窗水平贴到本次手势/轮盘触发的那一侧、竖直以触发高度居中。
     * 拿不到触发信息（或已过期）时仍用上面的固定百分比。
     */
    val freeWindowFollowTriggerSide: Boolean = false,
    // 横屏预置（与竖屏完全独立，运行时按当前显示方向二选一）
    val freeWindowLandWidthFraction: Float = 0.68f,
    val freeWindowLandHeightFraction: Float = 0.4f,
    val freeWindowLandLeftFraction: Float = 0.16f,
    val freeWindowLandTopFraction: Float = 0.05f,
)

/** 小部件面板尺寸、模糊与页面。 */
data class WidgetPanelSettings(
    val widgetPanelPages: List<com.slideindex.app.widget.WidgetPanelPage> = emptyList(),
    val widgetPanelWidthFraction: Float = 0.8f,
    val widgetPanelHeightFraction: Float = 0.55f,
    val widgetPanelTopFraction: Float = 0.15f,
    val widgetPanelBlurEnabled: Boolean = true,
    val widgetPanelBlurRadiusDp: Int = AppSettings.WIDGET_PANEL_BLUR_RADIUS_DEFAULT_DP,
)

/** 验证码提取与自动填充规则。 */
data class OtpSettings(
    val otpCopyToClipboard: Boolean = false,
    val otpKeywordsRegex: String = com.slideindex.app.otp.OtpKeywords.DEFAULT_KEYWORDS_REGEX,
    val otpUserMatchRules: List<com.slideindex.app.otp.OtpMatchRule> = emptyList(),
    val otpDisabledOfficialRuleIds: Set<String> = emptySet(),
    val otpAutoInputEnabled: Boolean = false,
    val otpAutoConfirmEnabled: Boolean = false,
    val otpAutoInputDelayMs: Int = 0,
    val otpAutoInputIntervalMs: Int = 0,
    val otpLsposedSmsCaptureEnabled: Boolean = false,
    val otpLsposedSystemInjectEnabled: Boolean = true,
    val otpCodeNotificationEnabled: Boolean = false,
    val otpCodeNotificationRetentionSeconds: Int = com.slideindex.app.otp.OtpCodeAlertPolicy.DEFAULT_RETENTION_SECONDS,
    val otpShowCodeToast: Boolean = false,
    val otpSmsBlacklist: com.slideindex.app.otp.SmsBlacklistRuleSet =
        com.slideindex.app.otp.SmsBlacklistRuleSet.DISABLED,
    val otpBlockCodeSmsEnabled: Boolean = false,
    val otpMarkSmsReadEnabled: Boolean = false,
    val otpDeleteSmsAfterExtractEnabled: Boolean = false,
    val otpRecordCodeEnabled: Boolean = true,
    val otpRecordPlainSmsEnabled: Boolean = false,
    val otpRecordAppNotifyEnabled: Boolean = true,
    val otpRecordCodeLimit: Int = com.slideindex.app.otp.OtpRecordLimits.DEFAULT,
    val otpRecordPlainSmsLimit: Int = com.slideindex.app.otp.OtpRecordLimits.DEFAULT,
    val otpRecordAppNotifyLimit: Int = com.slideindex.app.otp.OtpRecordLimits.DEFAULT,
    /** 这些应用的验证码不处理（既不提取也不自动填充）。 */
    val otpBlockedPackages: Set<String> = emptySet(),
)

/**
 * 全局悬浮窗外观：所有浮层窗口与面板的模糊总开关。
 *
 * 只管"窗口跨窗模糊"与"面板内 backdrop"两条路，**不含**壁纸/截图模糊
 * （`BlurredWallpaperCache` / `SystemWallpaperBlurHelper`；蜂窝与全息启动器背景保持现状）。
 * 关闭后各处一律降级为实色底，不允许只剩半透明 tint。
 */
data class OverlayBlurSettings(
    /** 悬浮窗模糊总开关；关闭时全部降级为实色底。 */
    val overlayBlurEnabled: Boolean = true,
)

/**
 * 全局拖拽流转栏（Drag & Drop Flow Bar）设置。
 */
data class DragDropFlowSettings(
    /** 全局拖拽流转总开关。 */
    val dragDropFlowEnabled: Boolean = false,
    /** 流转栏出现位置（底边横向 / 左侧纵向 / 右侧纵向）。 */
    val dragDropFlowPositionId: Int = DragDropFlowPosition.BOTTOM.id,
    /** 拖拽至引擎或取消时是否震动。 */
    val dragDropFlowHapticEnabled: Boolean = true,
    /**
     * 拖拽流转栏里「文字」这一类目标。
     *
     * 存的是从搜索引擎卡片拖进来的**独立副本**：改原引擎不联动、删原引擎也不影响这里。
     * 空列表 = 拖文字时只剩翻译 / 复制 / 分享三个内置动作。
     */
    val dragFlowTextEngines: List<SearchEngineConfig> = emptyList(),
)

