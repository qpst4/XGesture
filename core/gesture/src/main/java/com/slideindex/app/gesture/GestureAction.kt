package com.slideindex.app.gesture

/**
 * 单个「启动应用」绑定自身的启动形态。
 *
 * - [FOLLOW_GLOBAL]：跟随小窗设置里的全局启动策略（含长按判定），默认值；
 * - [ALWAYS_FULLSCREEN] / [ALWAYS_FREE_WINDOW]：该绑定固定形态，不受全局策略影响。
 */
enum class LaunchWindowMode(val id: Int) {
    FOLLOW_GLOBAL(0),
    ALWAYS_FULLSCREEN(1),
    ALWAYS_FREE_WINDOW(2),
    ;

    companion object {
        fun fromId(id: Int): LaunchWindowMode =
            entries.firstOrNull { it.id == id } ?: FOLLOW_GLOBAL
    }

    /** 固定形态的绑定不再受全局启动策略（含长按判定）影响。 */
    val followsGlobalPolicy: Boolean get() = this == FOLLOW_GLOBAL
}

enum class GestureActionType(val id: Int) {
    OPEN_INDEX(0),
    LAUNCH_APP(1),
    QUICK_LAUNCHER(2),
    TASK_SWITCHER(3),
    BACK(4),
    HOME(5),
    RECENTS(6),
    NONE(7),
    CLOSE_CURRENT_APP(8),
    FREE_WINDOW_CURRENT_APP(9),
    CLICK_PASSTHROUGH(10),
    FLASHLIGHT(11),
    ADJUST_VOLUME(12),
    ADJUST_BRIGHTNESS(13),
    LAUNCH_ASSISTANT(14),
    LAUNCH_SHORTCUT(15),
    TOGGLE_MUTE(16),
    MEDIA_PLAY_PAUSE(17),
    MEDIA_PREVIOUS(18),
    MEDIA_NEXT(19),
    PREVIOUS_APP(20),
    OPEN_NOTIFICATIONS(21),
    OPEN_QUICK_SETTINGS(22),
    LOCK_SCREEN(23),
    SCREENSHOT(24),
    POWER_MENU(25),
    KEEP_SCREEN_ON(26),
    SCROLL_TO_TOP(27),
    SCROLL_TO_BOTTOM(28),
    SHELL_COMMAND_PANEL(29),
    QUICK_TOOLS_OVERLAY(31),
    TOGGLE_DND(32),
    SCREEN_RECORD(33),
    TOGGLE_WIFI(34),
    TOGGLE_MOBILE_DATA(35),
    SWITCH_INPUT_METHOD(36),
    WIDGET_POPUP_OVERLAY(37),
    FLOATING_POINTER(38),
    SIMULATE_POINTER_SWIPE(39),
    POINTER_GESTURE_RECORDER(40),
    POINTER_REALTIME_GESTURE(41),
    OPEN_FLOATING_POINTER_RADIAL_MENU(42),
    OPEN_STASH_PANEL(43),
    EXECUTE_SHELL_COMMAND(44),
    FULLSCREEN_SCREENSHOT_PICK(45),
    SEARCH_PANEL(46),
    LOCK_SCREEN_AND_SILENCE_RING(47),
    LOCK_SCREEN_AND_MUTE_ALL(48),
    OPEN_CLIPBOARD_PANEL(49),
    CORNER_INNER_CANCEL(50),
    CORNER_INNER_PIN_WHEEL(51),
    SNOOZE_OVERLAYS(52),
    HONEYCOMB_LAUNCHER(53),
    REGIONAL_SCREENSHOT_PICK(54),
    CLIPBOARD_PICK(55),
    APP_RING_LAUNCHER(56),
    OPEN_CLIPBOARD_FLOAT(57),
    HOLOGRAPHIC_LAUNCHER(58),
    VOLUME_PANEL(59),
    SCREEN_TRANSLATE(60),
    REMIND_1M(61),
    REMIND_3M(62),
    REMIND_5M(63),
    REMIND_10M(64),
    REMIND_15M(65),
    UNIVERSAL_COPY(66),
    FREEZER_PANEL(67),
    REFREEZE(68),
    TOGGLE_AUTO_BRIGHTNESS(69),
    REMIND(70),
    VOICE_SEARCH(71),
    VOICE_ASSISTANT(72),
    TOGGLE_AUTO_ROTATE(73),
    FORCE_PORTRAIT(74),
    FORCE_LANDSCAPE(75),
    OPEN_INTERNET_PANEL(76),
    OPEN_VOLUME_PANEL(77),
    OPEN_LINK(78),
    CURRENT_APP_INFO(79),
    SIMULATE_KEY_EVENT(80),
    SCREEN_OFF_KEEP_AWAKE(81),
    PIN_TO_SCREEN(82),
    APP_CAROUSEL_SWITCHER(83),
    FOREGROUND_ACTIVITY_INSPECTOR(84),
    FINGERTIP_RING(85),
    /** Paste latest clipboard history entry into the focused input field (FV gesture action 12). */
    CLIPBOARD_PASTE(86),
    /** 弹出时长选择，定时开启勿扰并在到期后恢复先前状态。 */
    TIMED_DND(87),
    /** 在当前界面查找关键字并高亮，支持自动滚动。 */
    SCREEN_SEARCH(88),
    /** 智能截图 (全屏/选区编辑裁剪与贴图) */
    SMART_SCREENSHOT(90),
    /** 音量增加一级（媒体流），同时弹出系统音量面板。 */
    VOLUME_UP(91),
    /** 音量减小一级（媒体流），同时弹出系统音量面板。 */
    VOLUME_DOWN(92),
    /** 强行停止当前前台应用：真杀进程（force-stop 语义），需 Shizuku / root。 */
    FORCE_STOP_CURRENT_APP(93),
    /** 快速启动轮盘：在触发点原地展开自定义同心环快捷轮盘。 */
    QUICK_WHEEL(94),

    /**
     * 「快捷菜单」：出厂即满的常用小工具聚合面板（聚合、零配置）。
     *
     * 与 [QUICK_TOOLS_OVERLAY]（「快速工具（悬浮窗）」，12 个系统开关磁贴）的分工：
     * 本动作打开的是**效率小工具**面板（单位换算 / 汇率 / 二维码 / 识码解析 / 密码 / 秒表），
     * 打开即有全部工具，用户不需要预先挑选、绑定或配置任何一项。
     */
    QUICK_MENU(95),

    /** 「运行宏」：把一条录制好的宏重放到它自己的目标 App 里；payload 是宏 id。 */
    RUN_MACRO(96),

    /**
     * 「页面滚动」：打开一个竖排控制条，并用**被持续续接的划动手势**让页面平滑滚动。
     *
     * 松手后继续滚（用控制条改方向 / 调速 / 暂停 / 关闭）；速度 7 档，默认第 3 档。
     * 具体行为见 `com.slideindex.app.pagescroll`。
     */
    PAGE_SCROLL(97),

    /**
     * 「禁止触摸」：全屏吞掉所有触摸，屏幕对下面的应用完全无响应。
     *
     * 解除方式：在屏幕上画一个闭合的圈（见 `TouchBlockLoopDetector`）；双击黑屏后再双击亮屏可以继续用；
     * 最长 5 分钟后自动解除兜底。
     *
     * 另外还注册了 `ACTION_SCREEN_OFF` 做**静默兜底**（收到就撤窗），但它只是 best-effort：Flyme + Android 14+
     * 上会因进程冻结、AOD/doze、快速解锁而漏掉，所以**文案里不承诺"锁屏恢复"**，别把它当可靠路径。
     */
    TOUCH_BLOCK(98),

    /** 「屏幕控制」：悬浮触控板 + 自绘指针 + 4 方向键 + 4 导航键。 */
    SCREEN_CONTROL(99),

    /** 「屏幕绘制」：全屏实时标注层 + 浮动工具栏，可撤销/清空并导出。 */
    SCREEN_DRAW(100),

    /** 「微信扫一扫」：直接唤起微信扫一扫取景界面（`LauncherUI` + `LauncherUI.From.Scaner.Shortcut`）。 */
    WECHAT_SCAN(101),

    /** 「微信付款码」：直接唤起微信付款码（`BIZSHORTCUT` + `launch_type_offline_wallet`）。 */
    WECHAT_PAY_CODE(102),

    /** 「支付宝付款码」：直接唤起支付宝当面付/付款码（`FastStartActivity`）。 */
    ALIPAY_PAY_CODE(103),

    /** 「支付宝扫一扫」：直接唤起支付宝扫码界面（`alipays://…saId=10000007`）。 */
    ALIPAY_SCAN(104),

    /**
     * 「相册图片编辑」：弹系统图片选择器选一张图，选完直接进内置图片编辑器。
     *
     * 与「智能截图」「区域截图&取词」的区别：那两条是**先截屏再编辑**，本动作编辑的是**相册里已有的图**，
     * 所以动作名带「相册」前缀（编辑器自身的标题仍是「图片编辑」，见 `inspire_image_edit_title`）。
     * ⚠️ 以后若给本动作加上"剪贴板 / 分享 / 拖入"等相册以外的来源，这个名字就会误导，届时应改名。
     *
     * 取图用 `PickVisualMedia`（系统照片选择器），不需要任何读图权限；
     * 流程见 `com.slideindex.app.service.GalleryImageEditTrampolineActivity`。
     */
    GALLERY_IMAGE_EDIT(105),

    /** 「切换晃动手势」：把晃动页的总开关取反，可作为任意手势/槽位的动作。 */
    TOGGLE_SHAKE_GESTURE(106),

    /** 「文件浏览器」：打开独立浮窗，支持系统根目录 / Android/data / Root 数据目录。 */
    FILE_BROWSER(107),

    /**
     * 「切换悬浮球」：把悬浮球总开关取反，只影响悬浮球。
     *
     * 状态落在 `FLOAT_BALL_ENABLED` 里，所以跨重启保持、设置页同步；
     * 与「临时暂停悬浮层」（内存态 5 秒）不是一回事。
     */
    TOGGLE_FLOAT_BALL(108),

    /** 「阅读消息」：打开通知栏里最新一条可点开的通知。 */
    READ_LATEST_MESSAGE(109),

    /** 「小窗阅读消息」：在小窗里打开最新一条通知；小窗不可用时回落全屏。 */
    READ_LATEST_MESSAGE_SMALL_WINDOW(110),

    /** 「清空通知」：移除通知栏里所有可清除通知，并收起全部消息提醒。 */
    CLEAR_NOTIFICATIONS(111),
    ;

    companion object {
        fun fromId(id: Int): GestureActionType =
            entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * 某一级轮盘的**形态覆盖方式**（呼出轮盘时用）。
 *
 * 单列成枚举是因为 `:core:gesture` 不依赖 `:core:overlay-layout`（避免模块循环），
 * 不能直接用那边的 `QuickWheelShape`；由调用方（浮层）做一次映射。
 */
enum class QuickWheelLaunchLevelShape {
    /** 跟随轮盘自身配置的该级形态。 */
    FOLLOW,

    /** 强制该级为圆形。 */
    CIRCLE,

    /** 强制该级为矩形。 */
    RECT,
}

/**
 * 绑定「快捷轮盘」动作时选择的**呼出形态**。
 *
 * - 四个组合值（[CIRCLE_CIRCLE] / [CIRCLE_RECT] / [RECT_CIRCLE] / [RECT_RECT]）读法固定为
 *   「**一级 + 二级**」：前一个词是一级形态，后一个是二级形态。例如 [CIRCLE_RECT] = 一级圆形、二级矩形。
 *   它们都**忽略**轮盘自身配置的形态，但仍沿用对应形态各自保存的那套外观参数
 *   （每个轮盘都同时存着圆形 / 矩形两套参数，因此两种形态的调参都不会丢）；
 * - [CIRCLE] / [RECT] 是**旧值**：只覆盖一级形态，二级继续跟随轮盘自身设置。保留原语义是为了
 *   让已保存的绑定行为不变（新绑定一般直接用上面的组合值）；
 * - [DEFAULT]：两级都跟随轮盘自身配置，载荷与旧版逐字节一致（仅 wheelId）。
 *
 * 之所以在 `:core:gesture` 内自定义而不直接用 `:core:overlay-layout` 的 `QuickWheelShape`，
 * 是因为 `:core:gesture` 不依赖 `:core:overlay-layout`（避免模块循环）。
 */
enum class QuickWheelLaunchShape {
    /** 两级都跟随轮盘设置（旧版默认）。 */
    DEFAULT,

    /** 一级圆形、二级圆形。 */
    CIRCLE_CIRCLE,

    /** 一级圆形、二级矩形。 */
    CIRCLE_RECT,

    /** 一级矩形、二级圆形。 */
    RECT_CIRCLE,

    /** 一级矩形、二级矩形。 */
    RECT_RECT,

    /** 旧值：只覆盖一级为圆形，二级跟随轮盘设置。 */
    CIRCLE,

    /** 旧值：只覆盖一级为矩形，二级跟随轮盘设置。 */
    RECT,
    ;

    /** 一级形态覆盖；[QuickWheelLaunchLevelShape.FOLLOW] 表示用轮盘自身的一级形态。 */
    val primaryLevel: QuickWheelLaunchLevelShape
        get() = when (this) {
            DEFAULT -> QuickWheelLaunchLevelShape.FOLLOW
            CIRCLE, CIRCLE_CIRCLE, CIRCLE_RECT -> QuickWheelLaunchLevelShape.CIRCLE
            RECT, RECT_CIRCLE, RECT_RECT -> QuickWheelLaunchLevelShape.RECT
        }

    /** 二级形态覆盖；[QuickWheelLaunchLevelShape.FOLLOW] 表示用轮盘自身的二级形态。 */
    val secondaryLevel: QuickWheelLaunchLevelShape
        get() = when (this) {
            DEFAULT, CIRCLE, RECT -> QuickWheelLaunchLevelShape.FOLLOW
            CIRCLE_CIRCLE, RECT_CIRCLE -> QuickWheelLaunchLevelShape.CIRCLE
            CIRCLE_RECT, RECT_RECT -> QuickWheelLaunchLevelShape.RECT
        }

    companion object {
        fun fromName(value: String?): QuickWheelLaunchShape =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * 轮盘呼出时**圆心的锚定方式**（动作绑定处可选，默认 [FOLLOW_FINGER]）。
 *
 * - [FOLLOW_FINGER]：圆心 = 动作被触发那一帧的手指位置（出现后不再跟随手指，只是高亮跟随）；
 * - [EDGE]：圆心 = 呼出点投影到**最近的一条屏幕边**（保留沿边坐标），轮盘像"从边缘长出来"。
 *   注意：这样整圆必然越界 → 运行时的自适应求解会自动把扇区收窄成半圆 / 90°。
 *
 * 之所以放在动作级而不是轮盘级：滑动距离阈值（短滑 60dp / 长滑 120dp）决定了两种方式的差别大小，
 * 同一个轮盘在不同手势下的最优解不同（短滑 + 贴边时手指正好落在中心大圆内）。
 */
enum class QuickWheelAnchorMode {
    /** 跟手（默认）：圆心落在触发点。 */
    FOLLOW_FINGER,

    /** 贴边：圆心落在最近的屏幕边线上。 */
    EDGE,
    ;

    companion object {
        fun fromName(value: String?): QuickWheelAnchorMode =
            entries.firstOrNull { it.name == value } ?: FOLLOW_FINGER
    }
}

sealed class GestureAction {
    abstract val type: GestureActionType
    abstract val payload: String

    data object OpenIndex : GestureAction() {
        override val type = GestureActionType.OPEN_INDEX
        override val payload = ""
    }

    data class LaunchApp(
        val packageName: String,
        /** 该绑定自己的启动形态；落库编码见 [com.slideindex.app.launcher.QuickLauncherItemCodec]。 */
        val windowMode: LaunchWindowMode = LaunchWindowMode.FOLLOW_GLOBAL,
        /** 0 = 主实例；>0 = 应用分身/工作资料所在的 user。 */
        val cloneUserId: Int = 0,
    ) : GestureAction() {
        override val type = GestureActionType.LAUNCH_APP
        override val payload = packageName
    }

    data class LaunchShortcut(
        val payloadKey: String,
        val label: String = "",
    ) : GestureAction() {
        override val type = GestureActionType.LAUNCH_SHORTCUT
        override val payload = payloadKey

        companion object {
            fun dynamic(packageName: String, shortcutId: String, label: String = "") =
                LaunchShortcut(
                    payloadKey = GestureShortcutPayload.encodeDynamic(packageName, shortcutId, label),
                    label = label,
                )

            fun component(componentFlat: String, label: String = "") =
                LaunchShortcut(
                    payloadKey = GestureShortcutPayload.encodeComponent(componentFlat, label),
                    label = label,
                )

            fun intent(intentUri: String, label: String = "", hostPackage: String? = null) =
                LaunchShortcut(
                    payloadKey = GestureShortcutPayload.encodeIntent(intentUri, label, hostPackage),
                    label = label,
                )

            fun intents(intentUris: List<String>, label: String = "", hostPackage: String? = null) =
                LaunchShortcut(
                    payloadKey = GestureShortcutPayload.encodeIntents(intentUris, label, hostPackage),
                    label = label,
                )

            fun fromPayload(payload: String): LaunchShortcut {
                val decoded = GestureShortcutPayload.decode(payload)
                return LaunchShortcut(
                    payloadKey = payload,
                    label = decoded?.label.orEmpty(),
                )
            }
        }
    }

    /**
     * 快速启动器面板。
     *
     * @param panelId 面板 id；空串表示取第一个面板。
     * @param pinned 钉住：呼出后面板常驻。点面板里的动作不再关闭面板，点面板外部也不关闭
     *   （外部触摸直接落到下面的应用），只有再划一次同一手势、面板上的关闭按钮或锁屏才收起。
     * @param pinCurrentApp 只钉在钉住那一刻的前台应用里：切走自动隐藏，切回原样恢复。
     *   仅在 [pinned] 为 true 时有意义。
     */
    data class QuickLauncher(
        val panelId: String = "",
        val pinned: Boolean = false,
        val pinCurrentApp: Boolean = false,
    ) : GestureAction() {
        override val type = GestureActionType.QUICK_LAUNCHER

        // 不钉住时载荷与旧版**完全一致**（仅 panelId），不影响已保存的记录；
        // 只有真的钉住时才追加第二段（1 = 钉住，2 = 钉住且只在本应用）。
        override val payload: String = buildString {
            append(panelId)
            if (!pinned) return@buildString
            append(PIN_SEP)
            append(if (pinCurrentApp) "2" else "1")
        }

        companion object {
            /** 载荷内各段的分隔符（SOH；面板 id 是 UUID，不会出现该控制字符）。 */
            private const val PIN_SEP = '\u0001'

            /** 从裸载荷解析；段缺失 / 非法时按「仅 panelId、不钉住」处理（向后兼容）。 */
            fun parse(raw: String): QuickLauncher {
                val parts = raw.split(PIN_SEP)
                val panelId = parts[0]
                return when (parts.getOrNull(1)?.trim()) {
                    "2" -> QuickLauncher(panelId = panelId, pinned = true, pinCurrentApp = true)
                    "1" -> QuickLauncher(panelId = panelId, pinned = true)
                    else -> QuickLauncher(panelId = panelId)
                }
            }
        }
    }

    data object TaskSwitcher : GestureAction() {
        override val type = GestureActionType.TASK_SWITCHER
        override val payload = ""
    }

    data object Back : GestureAction() {
        override val type = GestureActionType.BACK
        override val payload = ""
    }

    data object Home : GestureAction() {
        override val type = GestureActionType.HOME
        override val payload = ""
    }

    data object Recents : GestureAction() {
        override val type = GestureActionType.RECENTS
        override val payload = ""
    }

    data object CloseCurrentApp : GestureAction() {
        override val type = GestureActionType.CLOSE_CURRENT_APP
        override val payload = ""
    }

    /**
     * 强行停止当前前台应用。
     *
     * 与 [CloseCurrentApp] 的区别：后者只把任务从最近任务列表里移除（等同 OHO+ 划卡片），
     * 持有前台服务 / 正在播放媒体的应用仍会继续运行；本动作直接强杀进程。
     */
    data object ForceStopCurrentApp : GestureAction() {
        override val type = GestureActionType.FORCE_STOP_CURRENT_APP
        override val payload = ""
    }

    data object FreeWindowCurrentApp : GestureAction() {
        override val type = GestureActionType.FREE_WINDOW_CURRENT_APP
        override val payload = ""
    }

    data object ClickPassthrough : GestureAction() {
        override val type = GestureActionType.CLICK_PASSTHROUGH
        override val payload = ""
    }

    data object Flashlight : GestureAction() {
        override val type = GestureActionType.FLASHLIGHT
        override val payload = ""
    }

    data object AdjustVolume : GestureAction() {
        override val type = GestureActionType.ADJUST_VOLUME
        override val payload = ""
    }

    data object AdjustBrightness : GestureAction() {
        override val type = GestureActionType.ADJUST_BRIGHTNESS
        override val payload = ""
    }

    data object LaunchAssistant : GestureAction() {
        override val type = GestureActionType.LAUNCH_ASSISTANT
        override val payload = ""
    }

    data object VoiceSearch : GestureAction() {
        override val type = GestureActionType.VOICE_SEARCH
        override val payload = ""
    }

    data object VoiceAssistant : GestureAction() {
        override val type = GestureActionType.VOICE_ASSISTANT
        override val payload = ""
    }

    data object ToggleMute : GestureAction() {
        override val type = GestureActionType.TOGGLE_MUTE
        override val payload = ""
    }

    data object MediaPlayPause : GestureAction() {
        override val type = GestureActionType.MEDIA_PLAY_PAUSE
        override val payload = ""
    }

    data object MediaPrevious : GestureAction() {
        override val type = GestureActionType.MEDIA_PREVIOUS
        override val payload = ""
    }

    data object MediaNext : GestureAction() {
        override val type = GestureActionType.MEDIA_NEXT
        override val payload = ""
    }

    data object PreviousApp : GestureAction() {
        override val type = GestureActionType.PREVIOUS_APP
        override val payload = ""
    }

    data object OpenNotifications : GestureAction() {
        override val type = GestureActionType.OPEN_NOTIFICATIONS
        override val payload = ""
    }

    data object OpenQuickSettings : GestureAction() {
        override val type = GestureActionType.OPEN_QUICK_SETTINGS
        override val payload = ""
    }

    data object LockScreen : GestureAction() {
        override val type = GestureActionType.LOCK_SCREEN
        override val payload = ""
    }

    data object LockScreenAndSilenceRing : GestureAction() {
        override val type = GestureActionType.LOCK_SCREEN_AND_SILENCE_RING
        override val payload = ""
    }

    data object LockScreenAndMuteAll : GestureAction() {
        override val type = GestureActionType.LOCK_SCREEN_AND_MUTE_ALL
        override val payload = ""
    }

    data object Screenshot : GestureAction() {
        override val type = GestureActionType.SCREENSHOT
        override val payload = ""
    }

    /** Captures the full screen via accessibility screenshot and opens the text pick panel. */
    data object FullscreenScreenshotPick : GestureAction() {
        override val type = GestureActionType.FULLSCREEN_SCREENSHOT_PICK
        override val payload = ""
    }

    /** Opens the search panel overlay with text/image search. */
    data object SearchPanel : GestureAction() {
        override val type = GestureActionType.SEARCH_PANEL
        override val payload = ""
    }

    /** Opens the standalone file browser overlay. */
    data object FileBrowser : GestureAction() {
        override val type = GestureActionType.FILE_BROWSER
        override val payload = ""
    }

    data object PowerMenu : GestureAction() {
        override val type = GestureActionType.POWER_MENU
        override val payload = ""
    }

    data object KeepScreenOn : GestureAction() {
        override val type = GestureActionType.KEEP_SCREEN_ON
        override val payload = ""
    }

    data object ScrollToTop : GestureAction() {
        override val type = GestureActionType.SCROLL_TO_TOP
        override val payload = ""
    }

    data object ScrollToBottom : GestureAction() {
        override val type = GestureActionType.SCROLL_TO_BOTTOM
        override val payload = ""
    }

    data object ShellCommandPanel : GestureAction() {
        override val type = GestureActionType.SHELL_COMMAND_PANEL
        override val payload = ""
    }

    /** Opens an external URL, deeplink, or intent URI. */
    data class OpenLink(
        val url: String,
        val label: String = "",
    ) : GestureAction() {
        override val type = GestureActionType.OPEN_LINK
        override val payload = encodePayload(url, label)

        companion object {
            /** 勿用 \\u001F：与 [com.slideindex.app.launcher.QuickLauncherItemCodec] 多项拼接分隔符冲突。 */
            private const val SEP = '\u001D'
            private const val LEGACY_LABEL_SEP = '\u001F'

            fun encodePayload(url: String, label: String): String {
                val trimmedUrl = url.trim()
                val trimmedLabel = label.trim()
                return if (trimmedLabel.isBlank()) {
                    trimmedUrl
                } else {
                    "$trimmedUrl$SEP$trimmedLabel"
                }
            }

            fun fromPayload(payload: String): OpenLink {
                val separatorIndex = indexOfLabelSeparator(payload)
                if (separatorIndex < 0) {
                    return OpenLink(url = payload.trim())
                }
                return OpenLink(
                    url = payload.substring(0, separatorIndex).trim(),
                    label = payload.substring(separatorIndex + 1).trim(),
                )
            }

            private fun indexOfLabelSeparator(payload: String): Int {
                var best = -1
                for (sep in charArrayOf(SEP, LEGACY_LABEL_SEP)) {
                    val index = payload.indexOf(sep)
                    if (index >= 0 && (best < 0 || index < best)) {
                        best = index
                    }
                }
                return best
            }
        }
    }

    /** Runs a saved shell command when the gesture fires. */
    data class ExecuteShellCommand(
        val command: String = "",
    ) : GestureAction() {
        override val type = GestureActionType.EXECUTE_SHELL_COMMAND
        override val payload = command
    }

    /** Samsung OHO+ style quick-tools popup, rendered top-level via [com.slideindex.app.overlay.OhoQuickToolsOverlayWindow]. */
    data object QuickToolsOverlay : GestureAction() {
        override val type = GestureActionType.QUICK_TOOLS_OVERLAY
        override val payload = ""
    }

    /** 「快捷菜单」面板，由 `com.slideindex.app.overlay.quickmenu.QuickMenuOverlayWindow` 顶层渲染。 */
    data object QuickMenu : GestureAction() {
        override val type = GestureActionType.QUICK_MENU
        override val payload = ""
    }

    /**
     * 「运行宏」：把一条宏重放到它自己的目标 App 里。
     *
     * 只保存宏 id —— 宏本体在独立存储里（见 `com.slideindex.app.macro.MacroStore`），
     * 所以宏改名/改步骤不需要动任何绑定；宏被删掉后绑定会退化成无效动作。
     */
    data class RunMacro(
        val macroId: String = "",
    ) : GestureAction() {
        override val type = GestureActionType.RUN_MACRO
        override val payload = macroId
    }

    /**
     * 「页面滚动」：打开一个竖排控制条，并用**被持续续接的划动手势**让页面平滑滚动。
     *
     * 松手后继续滚（用控制条改方向 / 调速 / 暂停 / 关闭）；速度 7 档，默认第 3 档。
     * 运行时实现见 `com.slideindex.app.pagescroll`。
     */
    data object PageScroll : GestureAction() {
        override val type = GestureActionType.PAGE_SCROLL
        override val payload = ""
    }

    /** Samsung OHO+ style widget popup hosting system App Widgets via [com.slideindex.app.overlay.WidgetPopupOverlayWindow]. */
    data object WidgetPopupOverlay : GestureAction() {
        override val type = GestureActionType.WIDGET_POPUP_OVERLAY
        override val payload = ""
    }
    /** Edge-gesture regional screenshot & text pick (no persistent float ball). */
    data object RegionalScreenshotPick : GestureAction() {
        override val type = GestureActionType.REGIONAL_SCREENSHOT_PICK
        override val payload = ""
    }

    /** Opens the float-ball stash panel via [com.slideindex.app.overlay.FloatBallStashPanel]. */
    data object StashPanel : GestureAction() {
        override val type = GestureActionType.OPEN_STASH_PANEL
        override val payload = ""
    }

    /** Opens the float-ball clipboard tab via [com.slideindex.app.overlay.FloatBallStashPanel]. */
    data object ClipboardPanel : GestureAction() {
        override val type = GestureActionType.OPEN_CLIPBOARD_PANEL
        override val payload = ""
    }

    /** Opens the floating clipboard window via [ClipboardFloatService]. */
    data object ClipboardFloat : GestureAction() {
        override val type = GestureActionType.OPEN_CLIPBOARD_FLOAT
        override val payload = ""
    }

    /** Reads the current system clipboard and opens the text pick panel. */
    data object ClipboardPick : GestureAction() {
        override val type = GestureActionType.CLIPBOARD_PICK
        override val payload = ""
    }

    /** Pastes the latest clipboard history entry; multiple fields open a tap-to-pick overlay. */
    data object ClipboardPaste : GestureAction() {
        override val type = GestureActionType.CLIPBOARD_PASTE
        override val payload = ""
    }

    /** Virtual joystick + on-screen pointer; tap joystick to click at pointer via accessibility. */
    data object FloatingPointer : GestureAction() {
        override val type = GestureActionType.FLOATING_POINTER
        override val payload = ""
    }

    /** Simulates a swipe starting at the floating pointer position. */
    data class SimulatePointerSwipe(
        val config: PointerSwipeConfig = PointerSwipeConfig.DEFAULT,
    ) : GestureAction() {
        override val type = GestureActionType.SIMULATE_POINTER_SWIPE
        override val payload = PointerSwipeConfigCodec.encode(config)

        companion object {
            fun fromPayload(payload: String) =
                SimulatePointerSwipe(PointerSwipeConfigCodec.decode(payload))
        }
    }

    /** Starts the pointer gesture recorder: samples the pointer path and replays it on release. */
    data object PointerGestureRecorder : GestureAction() {
        override val type = GestureActionType.POINTER_GESTURE_RECORDER
        override val payload = ""
    }

    /** Starts the real-time gesture: the pointer follows the finger via continueStroke. */
    data object PointerRealtimeGesture : GestureAction() {
        override val type = GestureActionType.POINTER_REALTIME_GESTURE
        override val payload = ""
    }

    /** Opens the floating pointer radial action ring. */
    data object OpenFloatingPointerRadialMenu : GestureAction() {
        override val type = GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU
        override val payload = ""
    }

    data object ToggleDnd : GestureAction() {
        override val type = GestureActionType.TOGGLE_DND
        override val payload = ""
    }

    data object TimedDnd : GestureAction() {
        override val type = GestureActionType.TIMED_DND
        override val payload = ""
    }

    data object ScreenSearch : GestureAction() {
        override val type = GestureActionType.SCREEN_SEARCH
        override val payload = ""
    }


    data object SmartScreenshot : GestureAction() {
        override val type = GestureActionType.SMART_SCREENSHOT
        override val payload = ""
    }

    /**
     * 快速启动轮盘。
     *
     * @param wheelId 轮盘 id；空串表示取第一个轮盘。
     * @param shape 呼出形态：[QuickWheelLaunchShape.DEFAULT] 两级都跟随轮盘自身配置；
     *   [QuickWheelLaunchShape.CIRCLE_CIRCLE] 等四个组合值按「一级 + 二级」强制形态；
     *   [QuickWheelLaunchShape.CIRCLE] / [QuickWheelLaunchShape.RECT] 是只覆盖一级的旧值。
     * @param manualSectorMask 一级**圆形**轮盘的手动扇区掩码（1..15）；`null`（默认）= 不指定，
     *   由运行时按触发位置自适应求解（= "我什么都不想配，程序自动"）。动作页只在绑定了非默认
     *   [shape] 时提供该选择；跟随轮盘自身配置时一律为 `null`。二级与矩形基准角始终自适应。
     * @param anchorMode 圆心锚定方式（[QuickWheelAnchorMode]）；默认 [QuickWheelAnchorMode.FOLLOW_FINGER]。
     */
    data class QuickWheel(
        val wheelId: String = "",
        val shape: QuickWheelLaunchShape = QuickWheelLaunchShape.DEFAULT,
        val manualSectorMask: Int? = null,
        val anchorMode: QuickWheelAnchorMode = QuickWheelAnchorMode.FOLLOW_FINGER,
    ) : GestureAction() {
        override val type = GestureActionType.QUICK_WHEEL

        // 形态、扇区、锚点都为默认时载荷与旧版**完全一致**（仅 wheelId），不影响已保存的记录；
        // 手动扇区只在用户真的选了扇区时才追加第三段（因此不需要数据迁移）。
        override val payload: String = buildString {
            append(wheelId)
            val isAllDefault = shape == QuickWheelLaunchShape.DEFAULT &&
                manualSectorMask == null &&
                anchorMode == QuickWheelAnchorMode.FOLLOW_FINGER
            if (isAllDefault) return@buildString
            append(SHAPE_SEP)
            append(shape.name)
            if (anchorMode == QuickWheelAnchorMode.FOLLOW_FINGER) {
                manualSectorMask?.let {
                    append(SHAPE_SEP)
                    append(it)
                }
            } else {
                // 非默认锚点需要写第 4 段 → 扇区段必须占位（`0` 解析回"自动"），否则段位有歧义。
                append(SHAPE_SEP)
                append(manualSectorMask ?: 0)
                append(SHAPE_SEP)
                append(anchorMode.name)
            }
        }

        companion object {
            /** 载荷内各段的分隔符（SOH；UUID 中不会出现该控制字符）。 */
            private const val SHAPE_SEP = '\u0001'

            /** 扇区掩码的合法范围（4 个扇区；0 = 一个都没选 → 按"自动"处理）。 */
            private val SECTOR_MASK_RANGE = 1..0xF

            /** 从裸载荷解析；段缺失 / 非法时按"仅 wheelId、默认形态、自动扇区、跟手"处理（向后兼容）。 */
            fun parse(raw: String): QuickWheel {
                val parts = raw.split(SHAPE_SEP)
                if (parts.size < 2) return QuickWheel(raw)
                return QuickWheel(
                    wheelId = parts[0],
                    shape = QuickWheelLaunchShape.fromName(parts[1]),
                    manualSectorMask = parts.getOrNull(2)
                        ?.trim()
                        ?.toIntOrNull()
                        ?.takeIf { it in SECTOR_MASK_RANGE },
                    anchorMode = QuickWheelAnchorMode.fromName(parts.getOrNull(3)),
                )
            }
        }
    }

    data object ScreenRecord : GestureAction() {
        override val type = GestureActionType.SCREEN_RECORD
        override val payload = ""
    }

    data object ToggleWifi : GestureAction() {
        override val type = GestureActionType.TOGGLE_WIFI
        override val payload = ""
    }

    data object ToggleMobileData : GestureAction() {
        override val type = GestureActionType.TOGGLE_MOBILE_DATA
        override val payload = ""
    }

    /** 把「晃动」总开关取反（等价于晃动页顶部那个开关）。 */
    data object ToggleShakeGesture : GestureAction() {
        override val type = GestureActionType.TOGGLE_SHAKE_GESTURE
        override val payload = ""
    }

    /** 把「悬浮球」总开关取反（等价于悬浮球页顶部那个开关，但不影响边缘手势）。 */
    data object ToggleFloatBall : GestureAction() {
        override val type = GestureActionType.TOGGLE_FLOAT_BALL
        override val payload = ""
    }

    /**
     * 「阅读消息」：打开通知栏里**最新一条**可点开的通知。
     *
     * 目标通知在运行时现找，动作本身不带参数；真正打开走的是消息提醒那套执行器，
     * 所以「小窗回落全屏」「阅读后返回键」等行为与卡片上的「阅读」完全一致。
     */
    data object ReadLatestMessage : GestureAction() {
        override val type = GestureActionType.READ_LATEST_MESSAGE
        override val payload = ""
    }

    /** 「小窗阅读消息」：同 [ReadLatestMessage]，但优先在小窗里打开。 */
    data object ReadLatestMessageInSmallWindow : GestureAction() {
        override val type = GestureActionType.READ_LATEST_MESSAGE_SMALL_WINDOW
        override val payload = ""
    }

    /** 「清空通知」：清空通知栏（跳过常驻/不可清除的），并收起所有消息提醒浮层。 */
    data object ClearNotifications : GestureAction() {
        override val type = GestureActionType.CLEAR_NOTIFICATIONS
        override val payload = ""
    }

    data object SwitchInputMethod : GestureAction() {
        override val type = GestureActionType.SWITCH_INPUT_METHOD
        override val payload = ""
    }

    data object None : GestureAction() {
        override val type = GestureActionType.NONE
        override val payload = ""
    }

    /** 底角轮盘内环空白：松手取消轮盘。 */
    data object CornerInnerCancel : GestureAction() {
        override val type = GestureActionType.CORNER_INNER_CANCEL
        override val payload = ""
    }

    /** 底角轮盘内环空白：松手后轮盘驻留，直至点槽位/轮盘外/再次点内环。 */
    data object CornerInnerPinWheel : GestureAction() {
        override val type = GestureActionType.CORNER_INNER_PIN_WHEEL
        override val payload = ""
    }

    /** 临时隐藏触钮、边角轮盘与悬浮球。 */
    data object SnoozeOverlays : GestureAction() {
        override val type = GestureActionType.SNOOZE_OVERLAYS
        override val payload = ""
    }

    /** 蜂窝布局应用启动器，按住滑选后松手启动。 */
    data object HoneycombLauncher : GestureAction() {
        override val type = GestureActionType.HONEYCOMB_LAUNCHER
        override val payload = ""
    }

    /** FV 风格贴边半圆圆环启动器，按住滑选后松手启动。 */
    data object RingLauncher : GestureAction() {
        override val type = GestureActionType.APP_RING_LAUNCHER
        override val payload = ""
    }

    /** 独立应用切换器：卡片轮播与自适应 Squircle 大图标，支持持续/松手/即时触发。 */
    data object AppCarouselSwitcher : GestureAction() {
        override val type = GestureActionType.APP_CAROUSEL_SWITCHER
        override val payload = ""
    }

    /** 触钮手势指尖轮盘：在手指位置弹出轮盘槽位菜单。 */
    data object FingertipRing : GestureAction() {
        override val type = GestureActionType.FINGERTIP_RING
        override val payload = ""
    }

    /** 全屏 3D 球应用启动器，弹出后拖拽旋转、点击图标启动。 */
    data object HolographicLauncher : GestureAction() {
        override val type = GestureActionType.HOLOGRAPHIC_LAUNCHER
        override val payload = ""
    }

    /** 弹出音量面板，同时调节闹钟/铃声/媒体音量与亮度。 */
    data object VolumePanel : GestureAction() {
        override val type = GestureActionType.VOLUME_PANEL
        override val payload = ""
    }

    /** 媒体音量增加一级，同时弹出系统音量面板。 */
    data object VolumeUp : GestureAction() {
        override val type = GestureActionType.VOLUME_UP
        override val payload = ""
    }

    /** 媒体音量减小一级，同时弹出系统音量面板。 */
    data object VolumeDown : GestureAction() {
        override val type = GestureActionType.VOLUME_DOWN
        override val payload = ""
    }

    /** 屏幕翻译：在原位覆盖译文（开关式）。 */
    data object ScreenTranslate : GestureAction() {
        override val type = GestureActionType.SCREEN_TRANSLATE
        override val payload = ""
    }

    /** 触发后弹出时长选择，设置 N 分钟后闹钟。 */
    data object Remind : GestureAction() {
        override val type = GestureActionType.REMIND
        override val payload = ""
    }

    data object Remind1m : GestureAction() {
        override val type = GestureActionType.REMIND_1M
        override val payload = ""
    }

    data object Remind3m : GestureAction() {
        override val type = GestureActionType.REMIND_3M
        override val payload = ""
    }

    data object Remind5m : GestureAction() {
        override val type = GestureActionType.REMIND_5M
        override val payload = ""
    }

    data object Remind10m : GestureAction() {
        override val type = GestureActionType.REMIND_10M
        override val payload = ""
    }

    data object Remind15m : GestureAction() {
        override val type = GestureActionType.REMIND_15M
        override val payload = ""
    }

    /** Google Lens 风格全局复制：高亮框选屏幕文本后复制。 */
    data object UniversalCopy : GestureAction() {
        override val type = GestureActionType.UNIVERSAL_COPY
        override val payload = ""
    }

    /** 打开冰箱应用管理页。 */
    data object FreezerPanel : GestureAction() {
        override val type = GestureActionType.FREEZER_PANEL
        override val payload = ""
    }

    /** 一键重冻冰箱列表中已启用的应用。 */
    data object Refreeze : GestureAction() {
        override val type = GestureActionType.REFREEZE
        override val payload = ""
    }

    /** 打开或关闭系统自动亮度。 */
    data object ToggleAutoBrightness : GestureAction() {
        override val type = GestureActionType.TOGGLE_AUTO_BRIGHTNESS
        override val payload = ""
    }

    /** 开启或关闭屏幕自动旋转开关。 */
    data object ToggleAutoRotate : GestureAction() {
        override val type = GestureActionType.TOGGLE_AUTO_ROTATE
        override val payload = ""
    }

    /** 锁定屏幕为竖屏方向。 */
    data object ForcePortrait : GestureAction() {
        override val type = GestureActionType.FORCE_PORTRAIT
        override val payload = ""
    }

    /** 锁定屏幕为横屏方向。 */
    data object ForceLandscape : GestureAction() {
        override val type = GestureActionType.FORCE_LANDSCAPE
        override val payload = ""
    }

    /** 打开原生网络连接面板（Wi-Fi/移动网络）。 */
    data object OpenInternetPanel : GestureAction() {
        override val type = GestureActionType.OPEN_INTERNET_PANEL
        override val payload = ""
    }

    /** 调出系统原生声音调节面板。 */
    data object OpenVolumePanel : GestureAction() {
        override val type = GestureActionType.OPEN_VOLUME_PANEL
        override val payload = ""
    }

    /** 打开当前前台应用信息页面。 */
    data object CurrentAppInfo : GestureAction() {
        override val type = GestureActionType.CURRENT_APP_INFO
        override val payload = ""
    }

    /** 息屏挂机 / 伪息屏（全屏黑屏+最低亮度+保持唤醒+双击/音量键解除）。 */
    data object ScreenOffKeepAwake : GestureAction() {
        override val type = GestureActionType.SCREEN_OFF_KEEP_AWAKE
        override val payload = ""
    }

    /** 禁止触摸：全屏吞掉所有触摸，画一个闭合的圈解除。 */
    data object TouchBlock : GestureAction() {
        override val type = GestureActionType.TOUCH_BLOCK
        override val payload = ""
    }

    /** 屏幕控制：悬浮触控板 + 指针 + 方向键/导航键。 */
    data object ScreenControl : GestureAction() {
        override val type = GestureActionType.SCREEN_CONTROL
        override val payload = ""
    }

    /** 屏幕绘制：全屏实时标注 + 浮动工具栏。 */
    data object ScreenDraw : GestureAction() {
        override val type = GestureActionType.SCREEN_DRAW
        override val payload = ""
    }

    /** 微信扫一扫：直接唤起微信扫一扫取景界面。 */
    data object WechatScan : GestureAction() {
        override val type = GestureActionType.WECHAT_SCAN
        override val payload = ""
    }

    /** 微信付款码：直接唤起微信付款码（离线钱包）。 */
    data object WechatPayCode : GestureAction() {
        override val type = GestureActionType.WECHAT_PAY_CODE
        override val payload = ""
    }

    /** 支付宝付款码：直接唤起支付宝当面付/付款码。 */
    data object AlipayPayCode : GestureAction() {
        override val type = GestureActionType.ALIPAY_PAY_CODE
        override val payload = ""
    }

    /** 支付宝扫一扫：直接唤起支付宝扫码界面。 */
    data object AlipayScan : GestureAction() {
        override val type = GestureActionType.ALIPAY_SCAN
        override val payload = ""
    }

    /** 相册图片编辑：系统选一张图 → 内置图片编辑器。 */
    data object GalleryImageEdit : GestureAction() {
        override val type = GestureActionType.GALLERY_IMAGE_EDIT
        override val payload = ""
    }

    /** 钉到屏幕（轻量弹窗选择文本或图片，生成悬浮便签或图片置顶）。 */
    data object PinToScreen : GestureAction() {
        override val type = GestureActionType.PIN_TO_SCREEN
        override val payload = ""
    }

    /** 实时前台活动探测悬浮窗（显示当前前台应用包名与 Activity 类名）。 */
    data object ForegroundActivityInspector : GestureAction() {
        override val type = GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR
        override val payload = ""
    }

    /** 模拟按键事件（支持自定义 KeyCode 与长按）。 */
    data class SimulateKeyEvent(
        val keyCode: Int = 82,
        val keyName: String = "",
        val isLongPress: Boolean = false,
    ) : GestureAction() {
        override val type = GestureActionType.SIMULATE_KEY_EVENT
        override val payload: String
            get() = "$keyCode:$isLongPress:$keyName"

        companion object {
            fun fromPayload(payload: String): SimulateKeyEvent {
                if (payload.isBlank()) return SimulateKeyEvent(82, "KEYCODE_MENU", false)
                val parts = payload.split(":", limit = 3)
                val code = parts.getOrNull(0)?.toIntOrNull() ?: 82
                val longPress = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: false
                val name = parts.getOrNull(2).orEmpty()
                return SimulateKeyEvent(code, name, longPress)
            }
        }
    }

    companion object {
        /** Actions that support [GestureTriggerMode.CONTINUOUS] on compatible triggers. */
        val continuousTrackingActions: List<GestureAction> = listOf(
            OpenIndex,
            QuickLauncher(),
            TaskSwitcher,
            ShellCommandPanel,
            HoneycombLauncher,
            RingLauncher,
            AppCarouselSwitcher,
            FingertipRing,
            AdjustVolume,
            AdjustBrightness,
            FloatingPointer,
            RegionalScreenshotPick,
            QuickWheel(),
            // 「页面滚动」在持续触发下 = 按住并拖动（逐页滚动），所以它也算支持持续跟踪的动作。
            PageScroll,
        )

        fun from(type: GestureActionType, payload: String): GestureAction =
            when (type) {
                GestureActionType.OPEN_INDEX -> OpenIndex
                GestureActionType.LAUNCH_APP -> {
                    // 兼容带 clone 后缀的正文（部分存储路径只保留 body）；裸包名解析为 user 0。
                    val target = com.slideindex.app.launcher.AppTarget.parse(payload)
                    LaunchApp(target.packageName, cloneUserId = target.cloneUserId)
                }
                GestureActionType.LAUNCH_SHORTCUT -> LaunchShortcut.fromPayload(payload)
                GestureActionType.QUICK_LAUNCHER -> QuickLauncher.parse(payload)
                GestureActionType.TASK_SWITCHER -> TaskSwitcher
                GestureActionType.BACK -> Back
                GestureActionType.HOME -> Home
                GestureActionType.RECENTS -> Recents
                GestureActionType.CLOSE_CURRENT_APP -> CloseCurrentApp
                GestureActionType.FORCE_STOP_CURRENT_APP -> ForceStopCurrentApp
                GestureActionType.FREE_WINDOW_CURRENT_APP -> FreeWindowCurrentApp
                GestureActionType.CLICK_PASSTHROUGH -> ClickPassthrough
                GestureActionType.FLASHLIGHT -> Flashlight
                GestureActionType.ADJUST_VOLUME -> AdjustVolume
                GestureActionType.ADJUST_BRIGHTNESS -> AdjustBrightness
                GestureActionType.LAUNCH_ASSISTANT -> LaunchAssistant
                GestureActionType.VOICE_SEARCH -> VoiceSearch
                GestureActionType.VOICE_ASSISTANT -> VoiceAssistant
                GestureActionType.TOGGLE_AUTO_ROTATE -> ToggleAutoRotate
                GestureActionType.FORCE_PORTRAIT -> ForcePortrait
                GestureActionType.FORCE_LANDSCAPE -> ForceLandscape
                GestureActionType.TOGGLE_MUTE -> ToggleMute
                GestureActionType.MEDIA_PLAY_PAUSE -> MediaPlayPause
                GestureActionType.MEDIA_PREVIOUS -> MediaPrevious
                GestureActionType.MEDIA_NEXT -> MediaNext
                GestureActionType.PREVIOUS_APP -> PreviousApp
                GestureActionType.OPEN_NOTIFICATIONS -> OpenNotifications
                GestureActionType.READ_LATEST_MESSAGE -> ReadLatestMessage
                GestureActionType.READ_LATEST_MESSAGE_SMALL_WINDOW -> ReadLatestMessageInSmallWindow
                GestureActionType.CLEAR_NOTIFICATIONS -> ClearNotifications
                GestureActionType.OPEN_QUICK_SETTINGS -> OpenQuickSettings
                GestureActionType.LOCK_SCREEN -> LockScreen
                GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> LockScreenAndSilenceRing
                GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> LockScreenAndMuteAll
                GestureActionType.SCREENSHOT -> Screenshot
                GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> FullscreenScreenshotPick
                GestureActionType.REGIONAL_SCREENSHOT_PICK -> RegionalScreenshotPick
                GestureActionType.SEARCH_PANEL -> SearchPanel
                GestureActionType.FILE_BROWSER -> FileBrowser
                GestureActionType.POWER_MENU -> PowerMenu
                GestureActionType.KEEP_SCREEN_ON -> KeepScreenOn
                GestureActionType.SCROLL_TO_TOP -> ScrollToTop
                GestureActionType.SCROLL_TO_BOTTOM -> ScrollToBottom
                GestureActionType.SHELL_COMMAND_PANEL -> ShellCommandPanel
                GestureActionType.EXECUTE_SHELL_COMMAND -> ExecuteShellCommand(payload)
                GestureActionType.RUN_MACRO -> RunMacro(payload)
                GestureActionType.PAGE_SCROLL -> PageScroll
                GestureActionType.OPEN_LINK -> OpenLink.fromPayload(payload)
                GestureActionType.QUICK_TOOLS_OVERLAY -> QuickToolsOverlay
                GestureActionType.QUICK_MENU -> QuickMenu
                GestureActionType.WIDGET_POPUP_OVERLAY -> WidgetPopupOverlay
                GestureActionType.OPEN_STASH_PANEL -> StashPanel
                GestureActionType.OPEN_CLIPBOARD_PANEL -> ClipboardPanel
                GestureActionType.OPEN_CLIPBOARD_FLOAT -> ClipboardFloat
                GestureActionType.CLIPBOARD_PICK -> ClipboardPick
                GestureActionType.CLIPBOARD_PASTE -> ClipboardPaste
                GestureActionType.FLOATING_POINTER -> FloatingPointer
                GestureActionType.SIMULATE_POINTER_SWIPE -> SimulatePointerSwipe.fromPayload(payload)
                GestureActionType.POINTER_GESTURE_RECORDER -> PointerGestureRecorder
                GestureActionType.POINTER_REALTIME_GESTURE -> PointerRealtimeGesture
                GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> OpenFloatingPointerRadialMenu
                GestureActionType.TOGGLE_DND -> ToggleDnd
                GestureActionType.TIMED_DND -> TimedDnd
                GestureActionType.SCREEN_SEARCH -> ScreenSearch
                GestureActionType.SMART_SCREENSHOT -> SmartScreenshot
                GestureActionType.QUICK_WHEEL -> QuickWheel.parse(payload)
                GestureActionType.SCREEN_RECORD -> ScreenRecord
                GestureActionType.TOGGLE_WIFI -> ToggleWifi
                GestureActionType.TOGGLE_SHAKE_GESTURE -> ToggleShakeGesture
                GestureActionType.TOGGLE_FLOAT_BALL -> ToggleFloatBall
                GestureActionType.TOGGLE_MOBILE_DATA -> ToggleMobileData
                GestureActionType.SWITCH_INPUT_METHOD -> SwitchInputMethod
                GestureActionType.CORNER_INNER_CANCEL -> CornerInnerCancel
                GestureActionType.CORNER_INNER_PIN_WHEEL -> CornerInnerPinWheel
                GestureActionType.SNOOZE_OVERLAYS -> SnoozeOverlays
                GestureActionType.HONEYCOMB_LAUNCHER -> HoneycombLauncher
                GestureActionType.APP_RING_LAUNCHER -> RingLauncher
                GestureActionType.APP_CAROUSEL_SWITCHER -> AppCarouselSwitcher
                GestureActionType.FINGERTIP_RING -> FingertipRing
                GestureActionType.HOLOGRAPHIC_LAUNCHER -> HolographicLauncher
                GestureActionType.VOLUME_PANEL -> VolumePanel
                GestureActionType.VOLUME_UP -> VolumeUp
                GestureActionType.VOLUME_DOWN -> VolumeDown
                GestureActionType.SCREEN_TRANSLATE -> ScreenTranslate
                GestureActionType.REMIND -> Remind
                GestureActionType.REMIND_1M,
                GestureActionType.REMIND_3M,
                GestureActionType.REMIND_5M,
                GestureActionType.REMIND_10M,
                GestureActionType.REMIND_15M,
                -> Remind
                GestureActionType.UNIVERSAL_COPY -> UniversalCopy
                GestureActionType.FREEZER_PANEL -> FreezerPanel
                GestureActionType.REFREEZE -> Refreeze
                GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> ToggleAutoBrightness
                GestureActionType.OPEN_INTERNET_PANEL -> OpenInternetPanel
                GestureActionType.OPEN_VOLUME_PANEL -> OpenVolumePanel
                GestureActionType.CURRENT_APP_INFO -> CurrentAppInfo
                GestureActionType.SIMULATE_KEY_EVENT -> SimulateKeyEvent.fromPayload(payload)
                GestureActionType.SCREEN_OFF_KEEP_AWAKE -> ScreenOffKeepAwake
                GestureActionType.TOUCH_BLOCK -> TouchBlock
                GestureActionType.SCREEN_CONTROL -> ScreenControl
                GestureActionType.SCREEN_DRAW -> ScreenDraw
                GestureActionType.WECHAT_SCAN -> WechatScan
                GestureActionType.WECHAT_PAY_CODE -> WechatPayCode
                GestureActionType.ALIPAY_PAY_CODE -> AlipayPayCode
                GestureActionType.ALIPAY_SCAN -> AlipayScan
                GestureActionType.GALLERY_IMAGE_EDIT -> GalleryImageEdit
                GestureActionType.PIN_TO_SCREEN -> PinToScreen
                GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR -> ForegroundActivityInspector
                GestureActionType.NONE -> None
            }.normalized()

        fun remindMinutes(type: GestureActionType): Int? = when (type) {
            GestureActionType.REMIND_1M -> 1
            GestureActionType.REMIND_3M -> 3
            GestureActionType.REMIND_5M -> 5
            GestureActionType.REMIND_10M -> 10
            GestureActionType.REMIND_15M -> 15
            else -> null
        }

        val legacyRemindTypes: Set<GestureActionType> = setOf(
            GestureActionType.REMIND_1M,
            GestureActionType.REMIND_3M,
            GestureActionType.REMIND_5M,
            GestureActionType.REMIND_10M,
            GestureActionType.REMIND_15M,
        )
    }
}

fun GestureAction.isEffective(): Boolean = type != GestureActionType.NONE

/** 将旧版固定档位延时提醒迁移为统一的 [GestureAction.Remind]。 */
fun GestureAction.normalized(): GestureAction =
    if (type in GestureAction.legacyRemindTypes) GestureAction.Remind else this

fun GestureAction.isRemindAction(): Boolean = when (this) {
    GestureAction.Remind,
    GestureAction.Remind1m,
    GestureAction.Remind3m,
    GestureAction.Remind5m,
    GestureAction.Remind10m,
    GestureAction.Remind15m,
    -> true
    else -> false
}

fun GestureAction.isCornerInnerZoneOnly(): Boolean =
    this is GestureAction.CornerInnerCancel || this is GestureAction.CornerInnerPinWheel

/** Actions that only work with [GestureTriggerMode.CONTINUOUS] (not on-release / immediate). */
fun GestureAction.requiresContinuousTriggerOnly(): Boolean =
    this is GestureAction.RegionalScreenshotPick

/** [GestureAction.continuousTrackingActions] membership by action kind (not payload). */
fun GestureAction.isContinuousTrackingKind(): Boolean =
    GestureAction.continuousTrackingActions.any { ref ->
        when (ref) {
            is GestureAction.QuickLauncher -> this is GestureAction.QuickLauncher
            is GestureAction.QuickWheel -> this is GestureAction.QuickWheel
            else -> this == ref
        }
    }

fun GestureAction.supportsContinuousTracking(trigger: GestureTriggerType): Boolean {
    if (!isContinuousTrackingKind()) return false
    return when (this) {
        GestureAction.RingLauncher,
        GestureAction.AppCarouselSwitcher,
        GestureAction.FingertipRing,
        GestureAction.HoneycombLauncher,
        is GestureAction.QuickLauncher,
        is GestureAction.QuickWheel,
        GestureAction.ShellCommandPanel,
        -> trigger.isLongPress || !trigger.isPressOrTap
        else -> !trigger.isPressOrTap
    }
}

fun GestureAction.preferredTriggerMode(trigger: GestureTriggerType): GestureTriggerMode? =
    when (this) {
        GestureAction.OpenIndex ->
            if (!trigger.isPressOrTap) GestureTriggerMode.CONTINUOUS else null
        is GestureAction.QuickLauncher, GestureAction.ShellCommandPanel, GestureAction.HoneycombLauncher,
        GestureAction.RingLauncher, GestureAction.FingertipRing, is GestureAction.QuickWheel,
        ->
            when {
                trigger.isLongPress -> GestureTriggerMode.CONTINUOUS
                trigger.supportsIndex -> GestureTriggerMode.CONTINUOUS
                else -> null
            }
        GestureAction.AdjustVolume, GestureAction.AdjustBrightness ->
            if (!trigger.isPressOrTap) GestureTriggerMode.ON_RELEASE else null
        GestureAction.AppCarouselSwitcher,
        GestureAction.FingertipRing,
        GestureAction.RegionalScreenshotPick,
        GestureAction.FloatingPointer,
        ->
            if (!trigger.isPressOrTap) GestureTriggerMode.CONTINUOUS else null
        else -> null
    }
