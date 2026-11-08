package com.slideindex.app.overlay.quickmenu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.service.ScreenshotCaptureOutcome
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy

/**
 * 「屏幕取色」。
 *
 * ## 两次界面：取色页是"结果页"，真正的取色在全屏层
 *
 * 早先只有一页：点按钮截一张图，把图塞进 94%×86% 的面板卡片里按宽度铺满，再在**缩小后的图**
 * 上拖。三个硬伤：
 *
 * 1. 1080×2400 的截图在卡片里高约 787dp，而卡片可用高度只有 ~610dp——图必然超出一屏，偏偏
 *    图上的拖动又被取色手势吃掉，页面滚不动，图底部（含取色浮条）根本够不着。
 * 2. 截图前没把浮层收起来。`AccessibilityService.takeScreenshot` 拍的是**合成后的屏幕**，
 *    于是截进去的是"压暗 45% 的面板自己 + 悬浮球"——对着菜单取色，取到的自然不是目标。
 * 3. "取一次色"要开菜单 → 进工具页 → 再点一次按钮 → 拖 → 复制，结果还散在三处。
 *
 * 现在拆成两层，各管一件事：
 *
 * - **[QuickMenuColorPickerScreen]**（本页，仍留在面板卡片里）：结果页。一个「全屏取色」按钮，
 *   下面是最近一次取到的五种格式与取色历史；历史**常显**，不依赖"这一轮取过色"。
 * - **[QuickMenuColorPickFullscreen]**（画在面板窗口根部，整块屏幕）：真正取色的地方。冻结帧
 *   1:1 铺满屏幕（不压暗，看到的颜色就是屏幕的颜色），准星常显、手指只按位移推它，**松手**落定
 *   （记一条历史，不自动复制），返回退出。
 *
 * ## 为什么冻结帧而不是"实时取色"
 *
 * 无障碍截图一次一张、被系统限流，做不了连续取帧；业界取色器（Snipaste 一类）也都是
 * "先定格、再在定格画面上取样"。屏幕上的动态内容在定格那一刻停下，取样本身仍是**原始像素**。
 *
 * ## 截图的三个前置动作
 *
 * 面板、悬浮球、边缘触钮、贴边把手是**四个不同的窗口**，随便漏一个都会被拍进冻结帧
 * （`captureDisplayBitmap` 的注释、`ScreenSearchCapture.withOverlaysHiddenForCapture` 都写着）。
 * 所以 [startFullScreenPick] 里先让它们全部让开，再等一帧 + 120ms 让"让位"真的合成过。
 */
@Composable
internal fun QuickMenuColorPickerScreen(state: QuickMenuColorPickerState) {
    val context = LocalContext.current
    // 进页面读一次历史与取色速度：它和全屏层共用同一份 state，全屏里取到的色回来就能看见。
    LaunchedEffect(Unit) {
        state.history = QuickMenuColorHistoryStore.read(context)
        state.gain = QuickMenuColorGainStore.read(context)
    }

    // 三个失败文案各取一次；`captureFailed` 是带 %1$d 的模板，用 format 补错误码。
    val captureUnavailable = stringResource(R.string.quick_menu_scan_capture_unavailable)
    val captureThrottled = stringResource(R.string.quick_menu_scan_capture_throttled)
    val captureFailedTemplate = stringResource(R.string.quick_menu_scan_capture_failed)

    ToolScaffold {
        Text(
            text = stringResource(R.string.quick_menu_color_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = {
                startFullScreenPick(
                    context = context,
                    state = state,
                    unavailable = captureUnavailable,
                    throttled = captureThrottled,
                    failedTemplate = captureFailedTemplate,
                )
            },
            // 截图请求已发出、还没回来：连点会撞上无障碍截图的限流，直接挡住。
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Outlined.Colorize, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.quick_menu_color_start))
        }

        state.errorText?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        state.sample?.let { argb -> ColorFormatRows(argb) }

        // 历史条常显：用户点进这个工具，很多时候就是想把上次那个色再取一次。
        ColorHistoryRow(state.history)
    }
}

/**
 * 「屏幕取色」的界面状态：工具页与全屏取色层共用同一份。
 *
 * 挂在 [QuickMenuOverlayWindow] 的 Session 上而不是页面里的 `remember`：截图期间窗口会被隐藏，
 * 部分机型（Flyme 等）还会把覆盖层窗口直接摘掉；状态留在组合里会跟着一起丢，表现为"点完
 * 全屏取色什么都没发生"。Session 本来就是为这类"窗口没了但页面要留着"的场景准备的。
 */
internal class QuickMenuColorPickerState {

    /** 非空 = 正在全屏取色，值就是那帧冻结截图。 */
    var picking: Bitmap? by mutableStateOf(null)

    /** 最近一次落定的颜色（ARGB）。结果页靠它显示五种格式。 */
    var sample: Int? by mutableStateOf(null)

    /** 取色历史（最近的在前）。与全屏层、结果页共享。 */
    var history: List<Int> by mutableStateOf(emptyList())

    /** 截图失败的文案。发起新一次截图时清空。 */
    var errorText: String? by mutableStateOf(null)

    /** 截图请求已发出、还没回来。 */
    var busy: Boolean by mutableStateOf(false)

    /**
     * 准星位移 / 手指位移的倍率（档位见 [QuickMenuColorGainStore.STEPS]）。
     *
     * 默认 0.5；全屏取色层底部那个速度胶囊改的就是它，改动会落盘、下次沿用。
     */
    var gain: Float by mutableStateOf(QuickMenuColorGainStore.DEFAULT)
}

/** 收起浮层后到截屏之间的等待：对齐识码页那条路（等一帧 + 120ms）的既有节奏。 */
private const val PICK_HIDE_DELAY_MS = 120L

/** 「已复制」提示的停留时间。 */
private const val COPIED_HINT_MS = 1200L

/**
 * 收浮层 → 截一屏 → 把冻结帧挂给全屏层。
 *
 * 顺序有三处讲究，改动前先看：
 *
 * 1. **必须在 `QuickMenuWork` 里跑**，不能用 `rememberCoroutineScope`：截图期间面板窗口可能被
 *    系统摘掉、组合随之销毁，组合作用域的协程会被取消，于是"点了没反应"。
 * 2. **先隐藏再截**：`slideIndexAccessibilityService.captureDisplayBitmap` 的注释写着调用方
 *    必须自行收起浮层，识码页也是这么做的（`QuickMenuToolScreens` 的"截屏"按钮）。
 * 3. **先挂上 `picking` 再恢复窗口**：恢复的第一帧就应该是冻结帧，否则会闪一下工具页。
 *
 * 失败一律回结果页内联显示（不 toast：面板本身就是覆盖层，系统 toast 会被它盖住）。
 */
private fun startFullScreenPick(
    context: Context,
    state: QuickMenuColorPickerState,
    unavailable: String,
    throttled: String,
    failedTemplate: String,
) {
    if (state.busy) return
    state.busy = true
    state.errorText = null

    QuickMenuWork.launch {
        try {
            // 1) 面板自己让位（窗口还在，只是不可见/不可触摸，Session 保留）。
            QuickMenuOverlayWindow.suspendForExternalActivity()
            // 2) 其余常驻浮层各自是独立窗口，不会因为面板收起而消失，必须逐个藏绘制。
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.suppressChromeForCapture?.invoke()

            // 3) 等"让位"真的合成过：主线程一卡顿，不等就会把浮层拍进冻结帧。
            delay(PICK_HIDE_DELAY_MS)

            when (val outcome = com.slideindex.app.overlay.quickmenu.QuickMenuHostAccess.host
                ?.captureScreenshot()) {
                is ScreenshotCaptureOutcome.Success -> {
                    // 先挂冻结帧，再恢复窗口：恢复的第一帧就是全屏取色层，不会闪一下工具页。
                    state.picking = outcome.bitmap
                }
                null, ScreenshotCaptureOutcome.ServiceUnavailable -> state.errorText = unavailable
                ScreenshotCaptureOutcome.Throttled -> state.errorText = throttled
                is ScreenshotCaptureOutcome.Failed ->
                    state.errorText = failedTemplate.format(outcome.errorCode)
            }
        } finally {
            // 无论成败都必须恢复：漏一条，球/触钮/把手就会永久消失（比多拍一条浮层严重得多）。
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.restoreChromeAfterCapture?.invoke()
            QuickMenuOverlayWindow.resumeFromExternalActivity()
            state.busy = false
        }
    }
}

// ---------------------------------------------------------------- 全屏取色

/**
 * 整屏冻结帧上的取色层。
 *
 * 画在面板窗口的**根部**（`QuickMenuPanel` 里 Surface 之后），所以能铺满整屏；退出只是把
 * `picking` 置空，下面的结果页一直在组合里，历史与格式不会因为进出全屏而丢。
 *
 * ## 交互：准星是"虚拟光标"，手指只提供位移
 *
 * 第一版是"手指按哪取哪、放大镜悬在手指上方"。真机上试出来的问题很明确：**取样点被指尖压住
 * 看不见**，放大镜又没把"它对着哪颗像素"表达清楚，于是像两个各自为政的东西；手指直接在屏幕上
 * 滑也不够精细。
 *
 * 所以改成：屏幕上有一个**常显的准星**（十字 + 放大镜），它有自己的位置；手指在屏幕任意处按下，
 * 之后只按**位移**推准星（1:1），准星到边就抵住，反向拖立即回来。取样点永远不在指尖下，眼睛
 * 盯着准星微调即可。
 *
 * 为什么不把准星固定成"永远在手指上方 48dp"：那样屏幕最下面一条永远取不到（准星到不了），
 * 贴顶还得翻转。位移制没有这个死角，也不用翻转。
 *
 * 落定只在**松手**时发生（更新结果 + 记一条历史），**不自动复制**：剪贴板由底部浮条上的按钮
 * 显式触发（Android 13+ 每次写剪贴板都会弹系统提示，连取几个色就被弹几次）。
 *
 * ## 为什么不用 `aspectRatio` 撑容器
 *
 * 全屏的容器是整块屏幕，宽高比和截图不一定相等（状态栏/导航栏/刘海）。这里按
 * [QuickMenuColorPickerGeometry.fitRect] 把"图实际画在哪"算出来，采样走
 * [QuickMenuColorPickerGeometry.fitOffsetToPixel]，黑边里的点夹到图边上。
 */
@Composable
internal fun QuickMenuColorPickFullscreen(
    bitmap: Bitmap,
    state: QuickMenuColorPickerState,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val bitmapSize = remember(bitmap) {
        QuickMenuColorPickerGeometry.BitmapSize(bitmap.width, bitmap.height)
    }
    // `takeScreenshot` 回来的是硬件位图，`getPixel` 不能保证可用：先拷一份软件位图再读。
    val sampler = remember(bitmap) { ScreenSampler(bitmap) }
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    // "已复制"提示到点自动消失；期间再复制一次会重置倒计时。
    var copiedAt by remember(bitmap) { mutableStateOf(0L) }
    LaunchedEffect(copiedAt) {
        if (copiedAt == 0L) return@LaunchedEffect
        delay(COPIED_HINT_MS)
        copiedAt = 0L
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val viewport = QuickMenuColorPickerGeometry.Viewport(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        )
        // 图按 Fit 画，实际矩形与屏幕不一定重合（黑边）；准星与采样都以它为准。
        val fitted = remember(viewport, bitmapSize) {
            QuickMenuColorPickerGeometry.fitRect(viewport, bitmapSize)
        }

        /** 准星只能停在图上：到边就抵住，绝不画到黑边里。 */
        fun clampToImage(position: Offset): Offset = Offset(
            x = position.x.coerceIn(fitted.left, fitted.left + fitted.width),
            y = position.y.coerceIn(fitted.top, fitted.top + fitted.height),
        )

        fun argbAt(position: Offset): Int? {
            val point = QuickMenuColorPickerGeometry.fitOffsetToPixel(
                viewX = position.x,
                viewY = position.y,
                viewport = viewport,
                bitmap = bitmapSize,
            )
            return sampler.argbAt(point.x, point.y)
        }

        // 准星位置。**它不跟手指**：进全屏先放在图的中央，之后只按手指的**位移**移动。
        // 这样取样点永远不会被指尖压住，也不存在"屏幕最下面一条取不到"的死角。
        var cursor by remember(bitmap) {
            mutableStateOf(clampToImage(Offset(fitted.left + fitted.width / 2f, fitted.top + fitted.height / 2f)))
        }
        var liveArgb by remember(bitmap) { mutableStateOf<Int?>(null) }

        fun moveCursorTo(target: Offset) {
            val clamped = clampToImage(target)
            cursor = clamped
            argbAt(clamped)?.let { liveArgb = it }
        }

        // 进全屏先取中央那颗：还没碰屏幕就知道准星在哪、色值是多少。
        LaunchedEffect(bitmap, fitted) {
            liveArgb = argbAt(clampToImage(cursor))
        }

        fun commit() {
            // 抬手只"落定"：更新结果、记一条历史。**不自动复制**——
            // Android 13+ 每次写剪贴板都会弹系统提示，连取几个色会被弹好几次。
            val argb = liveArgb ?: return
            state.sample = argb
            // 历史落盘可能与页面销毁/重建并行，所以走与组合无关的作用域。
            QuickMenuWork.launch {
                state.history = QuickMenuColorHistoryStore.record(context, argb)
            }
        }

        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // 手势层铺满整屏（含黑边）：**一个** detector 同时管按下、拖动、抬手。
        //
        // 手指的绝对位置没有意义，只有**位移**有意义：按下时记下准星在哪，之后
        // `准星 = 按下时的准星 + (手指现在 - 手指按下) × 倍率`。到边由 `clampToImage` 抵住，
        // 反向拖立刻回来（每帧按总位移重算，不累积，所以不会出现"先吃掉一截空行程"）。
        // 倍率读 `state.gain`（底部速度胶囊可改），所以每帧取一次、不缓存。
        //
        // `consume()` 同时挡住下面那层"点罩层关面板"。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap, viewport) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val startCursor = cursor
                        val fingerX = down.position.x
                        val fingerY = down.position.y
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            moveCursorTo(
                                Offset(
                                    x = startCursor.x + (change.position.x - fingerX) * state.gain,
                                    y = startCursor.y + (change.position.y - fingerY) * state.gain,
                                ),
                            )
                        }
                        commit()
                    }
                },
        )

        // 准星 + 放大镜：与位图共用同一套坐标。准星就是 `cursor`，放大镜悬在它上方
        //（贴顶时翻到下方——那是"提示框"的位置问题，不影响准星本身）。
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (liveArgb == null) return@Canvas
            val center = clampToImage(cursor)
            val loupeSide = LOUPE_SIZE.toPx()
            // 跨度与镜子边长必须**一起**算（`alignedSpanAndLens`）：跨度定手感（镜子里看到多大一块
            // 屏幕），镜子边长再上取整到跨度的整数倍，于是放大倍数是整数——像素网格等宽、位图与
            // 镜子等大，准星中线也只差最后那不到半颗像素的量化误差。
            val (span, lensSide) = QuickMenuColorPickerGeometry.alignedSpanAndLens(
                bitmap = bitmapSize,
                lensSidePx = loupeSide.roundToInt(),
                span = LOUPE_SOURCE_SPAN,
                minSpan = MIN_LOUPE_SOURCE_SPAN,
                maxSpan = MAX_LOUPE_SOURCE_SPAN,
            )
            val point = QuickMenuColorPickerGeometry.fitOffsetToPixel(
                viewX = center.x,
                viewY = center.y,
                viewport = viewport,
                bitmap = bitmapSize,
            )
            val content = QuickMenuColorPickerGeometry.loupeContent(
                x = point.x,
                y = point.y,
                span = span,
                lensSide = lensSide,
                bitmap = bitmapSize,
            )
            drawPickerOverlay(
                image = imageBitmap,
                content = content,
                center = center,
                loupeSide = lensSide.toFloat(),
                accent = accent,
                density = density,
            )
        }

        // 顶部：退出 + 一句操作提示。压在冻结帧上，给一层半透明底保证在任意底色上都能看见。
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            ) {
                IconButton(onClick = onExit) {
                    Icon(
                        imageVector = MiuixIcons.Medium.Close,
                        contentDescription = stringResource(R.string.quick_menu_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            ) {
                Text(
                    text = stringResource(R.string.quick_menu_color_pick_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // 底部：速度胶囊 + 色值/复制。**不自动复制**，点浮条上的按钮才进剪贴板。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SpeedChip(
                    gain = state.gain,
                    onChange = { newGain ->
                        state.gain = newGain
                        // 落盘与组合无关：改完立刻退出全屏也不该丢。
                        QuickMenuWork.launch { QuickMenuColorGainStore.write(context, newGain) }
                    },
                )
                liveArgb?.let { current ->
                    PickedValueBar(
                        argb = current,
                        copied = copiedAt != 0L,
                        onCopy = {
                            copyColor(context, "color", QuickMenuColorMath.toHex(current))
                            copiedAt = SystemClock.uptimeMillis()
                        },
                    )
                }
            }
        }
    }
}

/**
 * 一次性把手里的位图读成软件位图，随后的 `getPixel` 都在这张上读。
 *
 * 硬件位图（`Bitmap.Config.HARDWARE`）不能保证 `getPixel` 可用，也不该在拖动的每一帧里
 * 反复读它；截图只有一张，转一次的成本可以忽略。
 */
private class ScreenSampler(bitmap: Bitmap) {

    private val software: Bitmap? =
        if (bitmap.config == Bitmap.Config.HARDWARE) {
            runCatching {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }.getOrNull()
        } else {
            bitmap
        }

    fun argbAt(x: Int, y: Int): Int? {
        val source = software ?: return null
        if (x < 0 || y < 0 || x >= source.width || y >= source.height) return null
        return runCatching { source.getPixel(x, y) }.getOrNull()
    }
}

// ---------------------------------------------------------------- 绘制

/** 放大镜的物理边长。96dp 是"够大能看清像素块、又不至于盖掉半个屏幕"的一档。 */
private val LOUPE_SIZE = 96.dp

/** 放大镜离开取样点的距离，避免被手指本身压住。 */
private val LOUPE_MARGIN = 24.dp

/**
 * 跨度的允许区间（像素），**必须都是奇数**。
 *
 * **手感由跨度决定**：跨度就是"镜子里能看到多大一块屏幕"。17 像素在 1080p 上约是屏宽的 1.6%，
 * 正好够"看清一颗像素 + 保留一点周围方位"；太小只剩两三颗像素、失去方位，太大则一颗像素只占
 * 几个屏幕像素，"像素块"就糊了。
 *
 * ⚠️ 为什么必须是奇数：[QuickMenuColorPickerGeometry.loupeSource] 用 `left = x - span/2` 把采样点
 * 放在窗口中间。奇数跨度下采样点正好是正中间那颗（左右邻域一样多），配合 `alignedSpanAndLens`
 * 的整数倍对齐，准星就能**精确**落在镜子正中；偶数跨度下左右邻域差一颗，准星会固定偏
 * `scale/2`（半个源像素 × 倍数）。
 */
private const val LOUPE_SOURCE_SPAN = 17
private const val MIN_LOUPE_SOURCE_SPAN = 15
private const val MAX_LOUPE_SOURCE_SPAN = 21

/**
 * 放大镜的空腔底色。
 *
 * 全屏层**不压暗**整幅截图（压暗会让整屏比真实屏幕灰一档，取色的人会怀疑取错了），所以放大镜
 * 与图之间只靠外圈区分。这层底色只在"位图没铺满镜子"的退化情况（极小位图）下露出来，作用同
 * 早先的压暗层：兜住取整误差，不让镜边露白。
 */
private const val LOUPE_BACKDROP_ALPHA = 0.35f

/** 放大镜外圈的不透明底：让镜子的边界在花哨的截图上也能一眼看出。 */
private const val LOUPE_OUTLINE_ALPHA = 0.85f

/**
 * 画准星与放大镜。
 *
 * 放大镜里的像素用 `drawImage` 的 `srcOffset / srcSize / dstOffset / dstSize` 重画，并且显式传
 * `FilterQuality.None`（最近邻）：方块就是地道的一颗颗像素，不会糊成渐变。这和
 * `ScanImageWithMarkers` 里"按原亮度重画标记区"是同一套手法，区别只是这里的源矩形比目标框小
 * 很多（所以是放大），以及必须关掉默认的双线性插值。
 */
private fun DrawScope.drawPickerOverlay(
    image: ImageBitmap,
    content: QuickMenuColorPickerGeometry.LoupeContent,
    center: Offset,
    loupeSide: Float,
    accent: Color,
    density: Density,
) {
    // 1) 准星：黑色垫底 + 白色细线**叠画**。两层一起用是为了在任意底色上都能看见——
    //    纯白十字在浅色截图上会消失，纯黑十字在深色截图上会消失。
    //    尺寸刻意细：准星是"指到哪"的标记，不是遮挡物；1dp 白线 + 2.5dp 黑垫底在
    //    1080p 上仍然清楚，又不会把取样点周围盖掉。
    val armLength = density.run { 12.dp.toPx() }
    val thin = density.run { 1.dp.toPx() }
    val thick = thin * 2.5f
    for (stroke in listOf(thick to Color.Black, thin to Color.White)) {
        val (width, color) = stroke
        drawLine(
            color = color,
            start = Offset(center.x - armLength, center.y),
            end = Offset(center.x + armLength, center.y),
            strokeWidth = width,
        )
        drawLine(
            color = color,
            start = Offset(center.x, center.y - armLength),
            end = Offset(center.x, center.y + armLength),
            strokeWidth = width,
        )
        drawCircle(
            color = color,
            radius = armLength * 0.5f,
            center = center,
            style = Stroke(width = width),
        )
    }

    // 2) 放大镜位置：默认在手指**上方**（手指压着下面，看得到上面）；贴顶时翻到下方；
    //    最后整体夹在屏幕内，绝不画到屏幕外面去。
    val half = loupeSide / 2f
    val margin = density.run { LOUPE_MARGIN.toPx() }
    val above = center.y - margin - loupeSide
    val top = if (above >= 0f) above else center.y + margin
    val left = center.x - half
    val safeLeft = left.coerceIn(0f, (size.width - loupeSide).coerceAtLeast(0f))
    val safeTop = top.coerceIn(0f, (size.height - loupeSide).coerceAtLeast(0f))
    val loupeTopLeft = Offset(safeLeft, safeTop)
    val loupeCenter = Offset(safeLeft + half, safeTop + half)

    if (content.src.width > 0 && content.src.height > 0) {
        // 圆形裁剪：像素块必须留在镜子里，不能溢到图上（溢出去就成了"图上莫名其妙多一块"）。
        val lens = Path().apply {
            addOval(
                Rect(
                    left = loupeTopLeft.x,
                    top = loupeTopLeft.y,
                    right = loupeTopLeft.x + loupeSide,
                    bottom = loupeTopLeft.y + loupeSide,
                ),
            )
        }
        clipPath(lens) {
            // 先铺一层底色，再画放大后的像素块。几何那边已经保证位图严丝合缝铺满镜子，正常情况下
            // 这一层完全被盖住、看不见；留着它是两道保险（`drawImage` 的 dst 参数是整数、取整后
            // 可能露一条极细的边；源矩形退化时几何会退回"保证准星居中"）。
            drawRect(
                color = Color.Black.copy(alpha = LOUPE_BACKDROP_ALPHA),
                topLeft = loupeTopLeft,
                size = Size(loupeSide, loupeSide),
            )
            drawImage(
                image = image,
                srcOffset = IntOffset(content.src.x, content.src.y),
                srcSize = IntSize(content.src.width, content.src.height),
                dstOffset = IntOffset(
                    (loupeTopLeft.x + content.dstOffsetX).roundToInt(),
                    (loupeTopLeft.y + content.dstOffsetY).roundToInt(),
                ),
                dstSize = IntSize(loupeSide.roundToInt(), loupeSide.roundToInt()),
                // ⚠️ 必须显式关掉插值：Compose 的 drawImage 默认用 FilterQuality.Low（双线性），
                // 那样放大出来是糊的渐变，"看清是哪颗像素"这件事就白做了。这里要的是最近邻。
                filterQuality = FilterQuality.None,
            )
            drawPixelGrid(content = content, topLeft = loupeTopLeft)

            // 镜心十字 + 当前像素的高亮框：镜子里到底对着哪颗像素，靠这两样一眼看出。
            // 位置取 `crosshairX/Y`（几何保证它就是采样点那颗像素的中心），不用 `loupeCenter`——
            // 贴边时两者会差最后不到半颗像素，而这里要的是"精确到那一颗"。
            val pixelCenter = Offset(
                x = loupeTopLeft.x + content.crosshairX,
                y = loupeTopLeft.y + content.crosshairY,
            )
            val reticleColor = Color.White.copy(alpha = 0.55f)
            val reticleStroke = density.run { 1.dp.toPx() }
            drawLine(
                color = reticleColor,
                start = Offset(loupeTopLeft.x, pixelCenter.y),
                end = Offset(loupeTopLeft.x + loupeSide, pixelCenter.y),
                strokeWidth = reticleStroke,
            )
            drawLine(
                color = reticleColor,
                start = Offset(pixelCenter.x, loupeTopLeft.y),
                end = Offset(pixelCenter.x, loupeTopLeft.y + loupeSide),
                strokeWidth = reticleStroke,
            )

            // 高亮框正好是一颗源像素的大小：黑底加白线，任何底色上都看得清。
            val pixelHalf = content.scale / 2f
            val pixelBox = Rect(
                left = pixelCenter.x - pixelHalf,
                top = pixelCenter.y - pixelHalf,
                right = pixelCenter.x + pixelHalf,
                bottom = pixelCenter.y + pixelHalf,
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.7f),
                topLeft = pixelBox.topLeft,
                size = pixelBox.size,
                style = Stroke(width = density.run { 3.dp.toPx() }),
            )
            drawRect(
                color = Color.White,
                topLeft = pixelBox.topLeft,
                size = pixelBox.size,
                style = Stroke(width = density.run { 1.5.dp.toPx() }),
            )
        }
    }

    // 3) 镜身外框：不透明底 + accent 圈，把"放大镜"从截图里拎出来。
    drawCircle(
        color = Color.Black.copy(alpha = LOUPE_OUTLINE_ALPHA),
        radius = half,
        center = loupeCenter,
        style = Stroke(width = density.run { 3.dp.toPx() }),
    )
    drawCircle(
        color = accent,
        radius = half,
        center = loupeCenter,
        style = Stroke(width = density.run { 1.5.dp.toPx() }),
    )
}

/** 放大镜里的像素网格：1 源像素一条线。用它确认"准星真的只差一颗像素"。 */
private fun DrawScope.drawPixelGrid(
    content: QuickMenuColorPickerGeometry.LoupeContent,
    topLeft: Offset,
) {
    if (content.scale <= 2f) return
    val gridColor = Color.White.copy(alpha = 0.25f)
    val lineWidth = 1f
    for (column in 1 until content.src.width) {
        val x = topLeft.x + content.dstOffsetX + column * content.scale
        drawLine(
            color = gridColor,
            start = Offset(x, topLeft.y),
            end = Offset(x, topLeft.y + content.src.height * content.scale),
            strokeWidth = lineWidth,
        )
    }
    for (row in 1 until content.src.height) {
        val y = topLeft.y + content.dstOffsetY + row * content.scale
        drawLine(
            color = gridColor,
            start = Offset(topLeft.x, y),
            end = Offset(topLeft.x + content.src.width * content.scale, y),
            strokeWidth = lineWidth,
        )
    }
}

// ---------------------------------------------------------------- 取色结果与历史

/** 全屏层底部那条浮条：色块 + HEX + 「已复制」+ 一键复制。 */
@Composable
private fun PickedValueBar(argb: Int, copied: Boolean, onCopy: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        // 用 surfaceContainerHigh 而不是纯 surface：浮条压在**截图**上，必须和截图有明确区分。
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = CircleShape),
            )
            Text(
                text = QuickMenuColorMath.toHex(argb),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
            )
            if (copied) {
                Text(
                    text = stringResource(R.string.quick_menu_color_copied),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(MiuixIcons.Medium.Copy, contentDescription = null)
            }
        }
    }
}

/**
 * 取色速度胶囊：点一下切下一档，长按回默认。
 *
 * 不做滑杆：全屏取色时"拖动"是推准星的手势，滑杆会和它抢同一根手指，而且更占地方。
 * 档位与默认值都在 [QuickMenuColorGainStore] 里，这里只负责显示与切换。
 */
@Composable
private fun SpeedChip(gain: Float, onChange: (Float) -> Unit) {
    val steps = QuickMenuColorGainStore.STEPS
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .combinedClickable(
                onClick = {
                    val index = steps.indexOfFirst { abs(it - gain) < 0.001f }
                    onChange(steps[(index + 1).mod(steps.size)])
                },
                onLongClick = { onChange(QuickMenuColorGainStore.DEFAULT) },
            ),
    ) {
        Text(
            text = gainLabel(gain),
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** 倍率标签：0.5 → `0.5×`。小数位不补零，1.0 也照原样显示成 `1.0×`。 */
private fun gainLabel(gain: Float): String = "${gain}×"

/** 结果页：最近一次取到的颜色的五种格式，各一个复制按钮。 */
@Composable
private fun ColorFormatRows(current: Int) {
    val context = LocalContext.current

    QuickMenuColorFormats.all(current).forEachIndexed { index, (label, value) ->
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index == 0) {
                    // 第一行（HEX）的左侧留给**色块本身**：它是"当前取到的颜色"，
                    // 比再写一遍"HEX"更能说明这一列是什么。
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 28.dp)
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(current))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(6.dp),
                            ),
                    )
                } else {
                    // 格式名是通用缩写（HEX/RGB/HSL/HSV），不是需要翻译的文案，所以不占字符串资源。
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.width(52.dp),
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                IconButton(onClick = { copyColor(context, label, value) }) {
                    Icon(MiuixIcons.Medium.Copy, contentDescription = null)
                }
            }
        }
    }
}

/**
 * 结果页：取色历史。
 *
 * 一行可横向滚动的色块，点一条即复制它的 HEX（历史的价值就是"上次那个色再给我一次"）。
 * 不写标题、也不要求这一轮取过色才显示——那两条早先都让人以为历史没了。
 */
@Composable
private fun ColorHistoryRow(history: List<Int>) {
    if (history.isEmpty()) return
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        history.forEach { color ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(color))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { copyColor(context, "color", QuickMenuColorMath.toHex(color)) },
            )
        }
    }
}

/** 复制到剪贴板。`contentDescription = null` 的图标按钮点完没有反馈，所以失败要静默兜住、不崩。 */
private fun copyColor(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(label, text)) }
}
