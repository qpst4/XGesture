package com.slideindex.app.overlay.dragdrop

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.ocr.OcrRecognizeResult
import com.slideindex.app.overlay.FloatBallImageSearchPanel
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.FloatBallTranslateCoordinator
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.PickResultContentOrigin
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.overlay.share.ShareSheetLauncher
import com.slideindex.app.overlay.share.ShareSheetPayload
import com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow
import com.slideindex.app.overlay.searchpanel.SearchPanelQueryBridge
import com.slideindex.app.overlay.searchpanel.SearchPanelSessionState
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.service.RegionalScreenshotOcr
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.DragDropFlowPosition
import com.slideindex.app.settings.SearchEngineCatalog
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 全局拖拽流转（Drag & Drop Flow）悬浮窗控制器：
 * - 待机时在屏幕边缘挂载一个 1px × 1px 无感透明锚点，用于监听系统全局拖放广播（DRAG_FLAG_GLOBAL）；
 * - 捕获到用户在任意 App 拖拽图片或文字时（ACTION_DRAG_STARTED），瞬时扩展为全屏并呈现毛玻璃流转栏；
 * - 支持将内容释放至各目标引擎或【恢复】/【取消】。
 */
class DragDropFlowController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val TAG = "DragDropFlow"

        /** 诊断埋点 tag：随「分享诊断报告」一起导出，release 正式包默认不记录。 */
        private const val DIAG_TAG = "DragFlow"

        /** 转发缓存目录：拖进来的外部文件先落一份在这里，再经 FileProvider 转交给目标应用。 */
        private const val FORWARD_CACHE_DIR = "drag_forward"

        /** 转发缓存的保留时长。目标应用读完后没人会删，落新文件时顺手清掉过期的。 */
        private const val FORWARD_CACHE_TTL_MS = 10 * 60 * 1000L

        /** 盘旋在「操作」项上多久才展开动作菜单。太短会误触，太长会让人以为没反应。 */
        private const val HUB_DWELL_MS = 400L

        /** 认领源后等 ACTION_DRAG_ENDED 的上限；超时就把源放回来，别让它永久藏着。 */
        private const val SOURCE_WATCHDOG_MS = 15_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var composeView: ComposeView? = null
    private var composeOwner: OverlayComposeOwner? = null
    private var anchorParams: WindowManager.LayoutParams? = null
    private var fullParams: WindowManager.LayoutParams? = null

    private var settingsJob: Job? = null
    private var isExpanded = false

    private val visibleState = mutableStateOf(false)
    private val positionState = mutableStateOf(DragDropFlowPosition.BOTTOM)
    private val hapticEnabledState = mutableStateOf(true)
    private val itemsState = mutableStateOf<List<DragDropFlowItem>>(emptyList())
    private val dragPositionState = mutableStateOf<Offset?>(null)
    private var currentHoveredId: String? = null

    /** 当前这次拖拽的「收起态」列表（操作枢纽 + 用户配置的目标）。 */
    private var dockItems: List<DragDropFlowItem> = emptyList()

    /** 枢纽展开后整列替换成的动作列表。 */
    private var actionItems: List<DragDropFlowItem> = emptyList()
    private val hubExpandedState = mutableStateOf(false)
    private var dwellJob: Job? = null

    /** 本次拖拽是否登记了可恢复的源（侧边面板 / 剪贴板浮窗 / 文件浏览器 / 屏幕绘制 / 全局复制）。 */
    private val restoreAvailableState = mutableStateOf(false)
    private var claimedSource: DragFlowSource? = null

    /** 认领源后的兜底：个别 OEM 会漏发 ACTION_DRAG_ENDED，别把源永久藏住。 */
    private var sourceWatchdog: Runnable? = null

    private var latestSettings: AppSettings = AppSettings()
    private var isHandlingDrop = false

    fun start() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            settingsRepository.settings.collectLatest { s ->
                latestSettings = s
                positionState.value = s.dragDropFlowPosition
                hapticEnabledState.value = s.dragDropFlowHapticEnabled
                withContext(Dispatchers.Main) {
                    if (s.dragDropFlowEnabled) {
                        ensureAnchorAttached()
                    } else {
                        destroyWindow()
                    }
                }
            }
        }
    }

    fun stop() {
        settingsJob?.cancel()
        settingsJob = null
        mainHandler.post {
            destroyWindow()
        }
    }

    private fun ensureAnchorAttached(): Boolean {
        if (composeView != null) return true

        val overlayContext = OverlayCompose.themedContext(context)
        val owner = OverlayComposeOwner()

        val view = OverlayCompose.createComposeView(overlayContext, owner).apply {
            visibility = View.VISIBLE
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

            setContent {
                OverlayAwareModuleTheme {
                    DragDropFlowBarLayout(
                        visible = visibleState.value,
                        position = positionState.value,
                        items = itemsState.value,
                        dragPosition = dragPositionState.value,
                        hapticEnabled = hapticEnabledState.value,
                        hubExpanded = hubExpandedState.value,
                        canRestore = restoreAvailableState.value,
                        onHoverChanged = { currentHoveredId = it },
                        onHubHoverChanged = { active -> handleHubHover(active) },
                        onLeavePanel = { collapseHub() },
                        onSelectTarget = { /* handled on drop */ },
                    )
                }
            }

            // 核心全局拖拽监听
            setOnDragListener { _, event ->
                handleDragEvent(event)
            }
        }

        val anchor = WindowManager.LayoutParams(
            1,
            1,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "XG:drag-anchor"
            gravity = Gravity.BOTTOM or Gravity.END
        }

        val added = runCatching { windowManager.addView(view, anchor) }
            .onFailure {
                Log.e(TAG, "addView failed", it)
                EdgeDiag.log(DIAG_TAG, "anchor addView failed: ${it.message}")
            }
            .isSuccess

        if (!added) {
            OverlayCompose.clearViewTreeOwners(view)
            owner.destroy()
            return false
        }

        this.composeView = view
        this.composeOwner = owner
        this.anchorParams = anchor
        this.isExpanded = false

        Log.d(TAG, "DragDropFlow anchor window attached (1px idle)")
        EdgeDiag.log(DIAG_TAG, "anchor attached")
        return true
    }

    private fun getActiveLayoutParams(position: DragDropFlowPosition): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        return when (position) {
            DragDropFlowPosition.BOTTOM -> {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    (175 * density).toInt(),
                    OverlayWindowTypes.overlayWindowType(context),
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
            }
            DragDropFlowPosition.LEFT -> {
                WindowManager.LayoutParams(
                    (120 * density).toInt(),
                    WindowManager.LayoutParams.MATCH_PARENT,
                    OverlayWindowTypes.overlayWindowType(context),
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
            }
            DragDropFlowPosition.RIGHT -> {
                WindowManager.LayoutParams(
                    (120 * density).toInt(),
                    WindowManager.LayoutParams.MATCH_PARENT,
                    OverlayWindowTypes.overlayWindowType(context),
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }
            }
            DragDropFlowPosition.RING -> {
                // 圆盘只占屏幕中央的一块正方形：盘外不归我们，手指划到盘外松手时
                // drop 仍然落到下面的应用，不会被这块浮层吃掉。
                val metrics = context.resources.displayMetrics
                val side = (minOf(metrics.widthPixels, metrics.heightPixels) * 0.92f).toInt()
                WindowManager.LayoutParams(
                    side,
                    side,
                    OverlayWindowTypes.overlayWindowType(context),
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.CENTER
                }
            }
        }
    }

    private fun handleDragEvent(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                val desc = event.clipDescription ?: return false
                val mimes = (0 until desc.mimeTypeCount).mapNotNull { index ->
                    runCatching { desc.getMimeType(index) }.getOrNull()
                }
                val hasImage = desc.hasMimeType("image/*")
                val hasAudio = desc.hasMimeType("audio/*") ||
                    desc.hasMimeType("audio/mp4")
                val hasFile = hasAudio || desc.hasMimeType("application/*") ||
                    desc.hasMimeType("video/*")
                val hasText = desc.hasMimeType("text/*") ||
                    desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                    desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)

                if (!hasImage && !hasText && !hasFile) {
                    EdgeDiag.log(DIAG_TAG, "ignored mimes=$mimes")
                    return false
                }

                // 准备候选项目列表：收起态默认是「操作枢纽 + 用户目标」，展开态是动作菜单。
                // 动作菜单在两处都要用（枢纽展开后 / 开关打开时直接平铺），所以先构建它。
                actionItems = if (hasImage || hasText) {
                    buildActionItems(hasImage = hasImage)
                } else {
                    emptyList()
                }
                dockItems = buildDockItems(
                    hasImage = hasImage,
                    hasText = hasText,
                    hasFile = hasFile,
                    actions = actionItems,
                )
                cancelDwell()
                hubExpandedState.value = false
                itemsState.value = dockItems
                currentHoveredId = null
                isHandlingDrop = false
                EdgeDiag.log(
                    DIAG_TAG,
                    "started mimes=$mimes items=${dockItems.joinToString(",") { it.id }}",
                )

                // 本应用注册过拖拽源就认领这次拖拽：之后源只负责藏好自己，
                // "结束后恢复还是关掉"由下面 DROP / DRAG_ENDED 统一决定。
                cancelSourceWatchdog()
                claimedSource = DragFlowSession.claim()
                restoreAvailableState.value = claimedSource != null
                claimedSource?.let { source ->
                    val runnable = Runnable {
                        if (claimedSource === source) {
                            claimedSource = null
                            restoreAvailableState.value = false
                            source.restore()
                            DragFlowSession.end(source)
                        }
                    }
                    sourceWatchdog = runnable
                    mainHandler.postDelayed(runnable, SOURCE_WATCHDOG_MS)
                }

                // 瞬时展开为边缘带状窗并展示流转栏
                expandToActiveRegion()
                visibleState.value = true
                return true
            }

            DragEvent.ACTION_DRAG_LOCATION -> {
                dragPositionState.value = Offset(event.x, event.y)
                return true
            }

            DragEvent.ACTION_DROP -> {
                val targetId = currentHoveredId
                val clipData = event.clipData
                val droppedTextLength = runCatching {
                    clipData?.getItemAt(0)?.text?.length ?: -1
                }.getOrDefault(-1)
                EdgeDiag.log(
                    DIAG_TAG,
                    "drop hovered=$targetId clipItems=${clipData?.itemCount ?: -1} " +
                        "textLen=$droppedTextLength",
                )

                // 只松在「操作」枢纽上（还没展开到动作项）不算选中任何目标：
                // 不消费这次 drop，也别把「已处理」的标记打上。
                if (targetId == DRAG_DROP_FLOW_HUB_ID) {
                    collapseHub()
                    return false
                }

                // 松在栏内空白处：不消费，交给 ACTION_DRAG_ENDED 按 result 决定源去留。
                // （以前这里会先把 isHandlingDrop 置 true，导致空白处松手后源永远藏着。）
                if (targetId == null) return false

                isHandlingDrop = true

                when (targetId) {
                    "__cancel__" -> {
                        Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_cancelled), Toast.LENGTH_SHORT).show()
                        // 取消 = 不再需要这个源：面板 / 浮窗 / 画布 / 选区直接关掉。
                        claimedSource?.discard()
                    }
                    "__restore__" -> {
                        Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_restored), Toast.LENGTH_SHORT).show()
                        // 恢复 = 把源原样放回来（侧边面板回到原位、画布回来、选区回来）。
                        claimedSource?.restore()
                    }
                    else -> {
                        if (clipData != null) {
                            processDrop(targetId, clipData)
                        }
                        // 内容已经交给目标了：源留在原地只会挡住目标应用，直接关掉。
                        claimedSource?.discard()
                    }
                }
                // 决策已经落定，兜底计时器不用再等（ENDED 也可能被 OEM 吞掉）。
                cancelSourceWatchdog()
                // 仅当命中流转栏项目时消费 Drop，避免拦截正常页面拖拽
                return true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                dragPositionState.value = null
                cancelDwell()
                cancelSourceWatchdog()
                hubExpandedState.value = false
                dockItems = emptyList()
                actionItems = emptyList()
                val droppedTarget = currentHoveredId
                currentHoveredId = null
                visibleState.value = false
                EdgeDiag.log(
                    DIAG_TAG,
                    "ended result=${event.result} handled=$isHandlingDrop target=$droppedTarget",
                )

                // 没落在本栏的项上（或落在空白处）时才由这里定案：
                // result=true = 内容已被某个目标接收（在其它应用里松的手），关掉源；
                // result=false = 这次拖拽没有接收方（取消），把源放回来。
                val source = claimedSource
                claimedSource = null
                restoreAvailableState.value = false
                if (!isHandlingDrop && source != null) {
                    if (event.result) source.discard() else source.restore()
                }
                DragFlowSession.end(source)

                // 等待淡出动画完成后还原为 1px 待机
                mainHandler.postDelayed({
                    if (!visibleState.value) {
                        collapseToAnchor()
                    }
                }, 180)
                return true
            }

            DragEvent.ACTION_DRAG_ENTERED -> return true
            DragEvent.ACTION_DRAG_EXITED -> {
                // 手指离开我们的窗口后系统就不再派位置事件了，不清掉的话高亮会冻在
                // 最后一格，看起来像“拖到圆外还选着某个应用”。
                dragPositionState.value = null
                collapseHub()
                return true
            }
            else -> return false
        }
    }

    private fun expandToActiveRegion() {
        val view = composeView ?: return
        val activeParams = getActiveLayoutParams(positionState.value)
        runCatching { windowManager.updateViewLayout(view, activeParams) }
        isExpanded = true
    }

    private fun collapseToAnchor() {
        val view = composeView ?: return
        val anchor = anchorParams ?: return
        if (isExpanded) {
            runCatching { windowManager.updateViewLayout(view, anchor) }
            isExpanded = false
        }
    }

    /**
     * 手指是否停在「操作」枢纽上。
     *
     * 停在上面满 [HUB_DWELL_MS] 就把整列换成动作菜单；离开或本次拖拽结束都要把计时取消，
     * 否则下一次拖拽会带着上一次的展开状态进来。
     */
    private fun handleHubHover(active: Boolean) {
        if (!visibleState.value || hubExpandedState.value) return
        if (!active) {
            cancelDwell()
            return
        }
        if (dwellJob?.isActive == true) return
        dwellJob = scope.launch {
            delay(HUB_DWELL_MS)
            if (visibleState.value && !hubExpandedState.value && actionItems.isNotEmpty()) {
                hubExpandedState.value = true
                currentHoveredId = null
                itemsState.value = actionItems
            }
        }
    }

    private fun cancelDwell() {
        dwellJob?.cancel()
        dwellJob = null
    }

    private fun cancelSourceWatchdog() {
        sourceWatchdog?.let(mainHandler::removeCallbacks)
        sourceWatchdog = null
    }

    /** 收起动作菜单，整列换回「枢纽 + 用户目标」。 */
    private fun collapseHub() {
        cancelDwell()
        if (!hubExpandedState.value) return
        hubExpandedState.value = false
        currentHoveredId = null
        if (visibleState.value) {
            itemsState.value = dockItems
        }
    }

    private fun actionItem(
        id: String,
        titleRes: Int,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        tint: Color,
    ) = DragDropFlowItem(
        id = id,
        title = context.getString(titleRes),
        iconVector = icon,
        iconTintColor = tint,
    )

    /**
     * 收起态列表。
     *
     * - 开关关着：`[操作枢纽] + 用户目标`，内置动作留给枢纽的展开态；
     * - 开关打开：`内置动作 + 用户目标`，动作本身就是一级入口，不再需要枢纽；
     * - 文件拖拽没有动作菜单（[actions] 为空），两种开关下都只有用户目标。
     */
    private fun buildDockItems(
        hasImage: Boolean,
        hasText: Boolean,
        hasFile: Boolean,
        actions: List<DragDropFlowItem>,
    ): List<DragDropFlowItem> {
        val targets = buildTargetItems(hasImage = hasImage, hasText = hasText, hasFile = hasFile)
        if (!DragDropFlowLayoutPolicy.shouldKeepHub(hasImage, hasText, latestSettings.dragDropFlowActionsInline)) {
            return actions + targets
        }
        return listOf(
            DragDropFlowItem(
                id = DRAG_DROP_FLOW_HUB_ID,
                title = context.getString(R.string.drag_drop_flow_hub),
                iconVector = Icons.Default.MoreVert,
                iconTintColor = Color(0xFF5F6368),
            )
        ) + targets
    }

    /**
     * 动作菜单。顺序与设置里的目标无关，保证两次拖拽之间不会跳位。
     *
     * 开关打开时整列会直接平铺到流转栏上：底栏一屏大约只放得下 5 格，
     * 所以把「分词 / 钉住」这两个别处没有入口的动作排在最前，别让它们落进滚动区。
     */
    private fun buildActionItems(hasImage: Boolean): List<DragDropFlowItem> =
        if (hasImage) {
            listOf(
                actionItem("segment_image", R.string.drag_drop_flow_action_segment, Icons.Outlined.TextFields, Color(0xFF3F51B5)),
                actionItem("copy_image", R.string.drag_drop_flow_target_copy_image, Icons.Outlined.ContentCopy, Color(0xFF4CAF50)),
                actionItem("search_image", R.string.drag_drop_flow_action_search, Icons.Outlined.ImageSearch, Color(0xFF2196F3)),
                actionItem("system_share_image", R.string.drag_drop_flow_target_share_image, Icons.Outlined.Share, Color(0xFFFF9800)),
                actionItem("pin_image", R.string.drag_drop_flow_action_pin, Icons.Outlined.PushPin, Color(0xFFE91E63)),
            )
        } else {
            listOf(
                actionItem("segment_text", R.string.drag_drop_flow_action_segment, Icons.Outlined.TextFields, Color(0xFF3F51B5)),
                actionItem("pin_text", R.string.drag_drop_flow_action_pin, Icons.Outlined.PushPin, Color(0xFFE91E63)),
                actionItem("search_text", R.string.drag_drop_flow_action_search, Icons.Outlined.Search, Color(0xFF2196F3)),
                actionItem("copy_text", R.string.drag_drop_flow_target_copy_text, Icons.Outlined.ContentCopy, Color(0xFF4CAF50)),
                actionItem("translate_text", R.string.drag_drop_flow_target_translate, Icons.Outlined.Translate, Color(0xFF9C27B0)),
                actionItem("system_share_text", R.string.drag_drop_flow_target_share_text, Icons.Outlined.Share, Color(0xFFFF9800)),
            )
        }

    private fun buildTargetItems(hasImage: Boolean, hasText: Boolean, hasFile: Boolean): List<DragDropFlowItem> {
        val pm = context.packageManager
        val list = mutableListOf<DragDropFlowItem>()

        if (hasImage) {
            // 图片：这里只放用户配置的图搜/分享引擎。复制、分享、以图搜图是内置动作，
            // 开关关着时收在「操作」菜单里，开着时由 buildDockItems 平铺到前面。
            for (engine in SearchEngineStore.imageFlowEngines(latestSettings.searchEngines)) {
                val pkg = engine.targetPackage?.takeIf { it.isNotBlank() } ?: continue
                val icon = (!engine.iconPath.isNullOrBlank()).let { hasPath ->
                    if (hasPath) runCatching { android.graphics.BitmapFactory.decodeFile(engine.iconPath) }.getOrNull() else null
                } ?: com.slideindex.app.ui.AppIconOverride.bitmap(context, pkg, 96)
                ?: runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()
                if (icon != null) {
                    list.add(
                        DragDropFlowItem(
                            id = "image_engine_${engine.id}",
                            title = engine.name,
                            iconBitmap = icon,
                        )
                    )
                }
            }
        } else if (hasFile) {
            // 通用文件/音频流转项：动态读取用户配置的文件分享引擎
            val engines = SearchEngineStore.fileSharePanelEngines(latestSettings.searchEngines)
            for (engine in engines) {
                val pkg = engine.targetPackage ?: continue
                val icon = (!engine.iconPath.isNullOrBlank()).let { hasPath ->
                    if (hasPath) runCatching { android.graphics.BitmapFactory.decodeFile(engine.iconPath) }.getOrNull() else null
                } ?: com.slideindex.app.ui.AppIconOverride.bitmap(context, pkg, 96)
                ?: runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()
                if (icon != null) {
                    list.add(
                        DragDropFlowItem(
                            id = "file_engine_${engine.id}",
                            title = engine.name,
                            iconBitmap = icon,
                        )
                    )
                }
            }

            // 系统分享文件
            list.add(
                DragDropFlowItem(
                    id = "system_share_file",
                    title = context.getString(R.string.drag_drop_flow_target_share_file),
                    iconVector = Icons.Outlined.Share,
                    iconTintColor = Color(0xFFFF9800),
                )
            )
        } else {
            // 文本：只放用户在「文字搜索引擎」页配置的命令（独立副本，按卡内顺序）。
            // 复制、翻译、分享、分词同样是内置动作，开关关着时收在「操作」菜单里。
            for (engine in latestSettings.dragFlowTextEngines.sortedBy { it.sortOrder }) {
                val pkgIcon = engine.targetPackage?.let { pkg ->
                    com.slideindex.app.ui.AppIconOverride.bitmap(context, pkg, 96)
                        ?: runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()
                }
                list.add(
                    DragDropFlowItem(
                        id = "engine_${engine.id}",
                        title = engine.name,
                        iconBitmap = pkgIcon,
                        iconVector = if (pkgIcon == null) Icons.Outlined.Search else null,
                        iconTintColor = Color(0xFF2196F3),
                    )
                )
            }
        }

        return list
    }

    private fun processDrop(targetId: String, clipData: ClipData) {
        scope.launch(Dispatchers.IO) {
            val item = clipData.getItemAt(0) ?: return@launch
            val uri = item.uri
            // 纯文本优先，其次 HTML（网页 / 部分编辑器只给 htmlText）。两者都没有时 text 为 null，
            // 下面引擎分支会明确提示，而不是静默什么都不做。
            val text = item.text?.toString()?.takeIf { it.isNotBlank() }
                ?: item.htmlText?.takeIf { it.isNotBlank() }

            withContext(Dispatchers.Main) {
                when {
                    targetId == "builtin_image_search" && uri != null -> {
                        val bitmap = decodeBitmapFromUri(uri)
                        if (bitmap != null) {
                            FloatBallImageSearchPanel.show(context, bitmap)
                        } else {
                            Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_image_unreadable), Toast.LENGTH_SHORT).show()
                        }
                    }
                    targetId.startsWith("image_engine_") && uri != null -> {
                        val engineId = targetId.removePrefix("image_engine_")
                        val engine = SearchEngineStore.imageFlowEngines(latestSettings.searchEngines)
                            .find { it.id == engineId }
                        val bitmap = decodeBitmapFromUri(uri)
                        if (engine != null && bitmap != null) {
                            val launched = SearchEngineLauncher.launchImageShare(context, engine, bitmap)
                            if (!launched) {
                                Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_launch_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    targetId.startsWith("file_engine_") && uri != null -> {
                        val engineId = targetId.removePrefix("file_engine_")
                        val engines = SearchEngineStore.fileSharePanelEngines(latestSettings.searchEngines)
                        val engine = engines.find { it.id == engineId }
                        val pkg = engine?.targetPackage
                        if (pkg != null) {
                            val forwarded = stageForForward(uri)
                            if (forwarded == null) {
                                Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_file_unreadable), Toast.LENGTH_SHORT).show()
                            } else {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = forwarded.intentType
                                    putExtra(Intent.EXTRA_STREAM, forwarded.uri)
                                    this.clipData = forwarded.clipData
                                    val act = engine.targetActivity
                                    if (!act.isNullOrBlank()) {
                                        component = android.content.ComponentName(pkg, act)
                                    } else {
                                        setPackage(pkg)
                                    }
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startShare(sendIntent, context.getString(R.string.drag_drop_flow_target_share_file))
                            }
                        }
                    }
                    targetId == "system_share_file" && uri != null -> {
                        val forwarded = stageForForward(uri)
                        if (forwarded == null) {
                            Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_file_unreadable), Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = forwarded.intentType
                                putExtra(Intent.EXTRA_STREAM, forwarded.uri)
                                this.clipData = forwarded.clipData
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startChooser(intent, context.getString(R.string.drag_drop_flow_target_share_file))
                        }
                    }
                    targetId == "copy_image" && uri != null -> {
                        val bitmap = decodeBitmapFromUri(uri)
                        if (bitmap != null) {
                            FloatBallTextPick.copyImage(context, bitmap)
                        }
                    }
                    targetId == "system_share_image" && uri != null -> {
                        val bitmap = decodeBitmapFromUri(uri)
                        if (bitmap != null) {
                            val shareUri = FloatBallTextPick.createShareImageUri(context, bitmap)
                            if (shareUri != null) {
                                ShareSheetLauncher.share(
                                    context = context,
                                    payload = ShareSheetPayload.Image(shareUri, "image/*"),
                                    // 流转载具是全屏浮窗：不收起的话目标 App 会被它盖住。
                                    dismissSource = { collapseToAnchor() },
                                )
                            }
                        }
                    }
                    targetId.startsWith("engine_") && !text.isNullOrBlank() -> {
                        val engineId = targetId.removePrefix("engine_")
                        val engine = latestSettings.dragFlowTextEngines.find { it.id == engineId }
                        if (engine != null) {
                            // 统一走 launchText：「分享到 App」的引擎由它转去 launchTextShare。
                            // 这里再自己判断 engineType 就会再次漏掉分享类（历史 bug：拖到 QQ 松手没反应）。
                            val launched = SearchEngineLauncher.launchText(context, engine, text, latestSettings)
                            EdgeDiag.log(
                                DIAG_TAG,
                                "engine id=${engine.id} type=${engine.engineType} " +
                                    "pkg=${engine.targetPackage} launched=$launched",
                            )
                        } else {
                            EdgeDiag.log(DIAG_TAG, "engine missing id=$engineId")
                            Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_target_missing), Toast.LENGTH_SHORT).show()
                        }
                    }
                    targetId.startsWith("engine_") -> {
                        // 拖过来的只有 URI 或纯 HTML，没有可交给引擎的文字：明确提示，别静默。
                        EdgeDiag.log(DIAG_TAG, "engine dropped without text id=$targetId")
                        Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_text_unreadable), Toast.LENGTH_SHORT).show()
                    }
                    targetId == "copy_text" && !text.isNullOrBlank() -> {
                        FloatBallTextPick.copyText(context, text)
                        Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_text_copied), Toast.LENGTH_SHORT).show()
                    }
                    targetId == "segment_text" && !text.isNullOrBlank() -> {
                        openWordSegmentation(text)
                    }
                    targetId == "search_text" && !text.isNullOrBlank() -> {
                        // 进我们自己的搜索面板，不要 ACTION_WEB_SEARCH 弹系统窗口。
                        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context
                        SearchPanelQueryBridge.rememberQuery(context, text)
                        if (!SearchPanelOverlayWindow.show(hostContext)) {
                            Toast.makeText(context, context.getString(R.string.shortcut_panel_open_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                    targetId == "pin_text" && !text.isNullOrBlank() -> {
                        StashCoordinator.pinTextToScreen(context, text)
                        Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_pinned), Toast.LENGTH_SHORT).show()
                    }
                    targetId == "pin_image" && uri != null -> {
                        val bitmap = decodeBitmapFromUri(uri)
                        if (bitmap != null) {
                            StashCoordinator.pinImageToScreen(context, bitmap)
                            Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_pinned), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_image_unreadable), Toast.LENGTH_SHORT).show()
                        }
                    }
                    targetId == "search_image" && uri != null -> {
                        // 不直接弹以图搜图：先把图送进搜索面板，由面板决定用哪个图搜引擎。
                        val bitmap = decodeBitmapFromUri(uri)
                        if (bitmap == null) {
                            Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_image_unreadable), Toast.LENGTH_SHORT).show()
                        } else {
                            val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context
                            SearchPanelSessionState.pushImage(bitmap)
                            if (!SearchPanelOverlayWindow.show(hostContext)) {
                                SearchPanelSessionState.pushImage(null)
                                FloatBallImageSearchPanel.show(context, bitmap)
                            }
                        }
                    }
                    targetId == "segment_image" && uri != null -> {
                        val bitmap = decodeBitmapFromUri(uri)
                        if (bitmap == null) {
                            Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_image_unreadable), Toast.LENGTH_SHORT).show()
                        } else {
                            segmentImage(bitmap)
                        }
                    }
                    targetId == "translate_text" && !text.isNullOrBlank() -> {
                        // 与取词面板 / 剪贴板智能翻译同一条链路：引擎与「即时翻译」开关都生效；
                        // 选「本地 App」或关掉即时翻译时不会打开取词面板。
                        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context
                        FloatBallTranslateCoordinator.translateFromExternalEntry(
                            context = hostContext,
                            text = text,
                            openTextPanel = { openTextPickPanel(hostContext, it) },
                        )
                    }
                    targetId == "system_share_text" && !text.isNullOrBlank() -> {
                        ShareSheetLauncher.share(
                            context = context,
                            payload = ShareSheetPayload.Text(text),
                            dismissSource = { collapseToAnchor() },
                        )
                    }
                }
            }
        }
    }

    /**
     * 分词：把拖进来的文字直接送进取词面板的分词模式，复用那边现成的词块点选与复制。
     * 走的是和全局取词相同的入口，锚点交给面板自己算（没有手指坐标可用）。
     */
    private fun openWordSegmentation(text: String) {
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context
        openTextPickPanel(hostContext, text)
    }

    /**
     * 把一段文字显示进取词结果面板：分词与翻译共用。
     *
     * 翻译会在面板显示后再调 `FloatBallTranslateCoordinator` 往面板里灌译文
     * （`showTranslateLoading` 要求面板已 show），所以这里只负责「先把原文放上面板」。
     */
    private fun openTextPickPanel(hostContext: Context, text: String) {
        FloatBallPickResultPanel.showResult(
            context = hostContext,
            result = FloatBallPickResult(
                a11yText = text,
                ocrText = null,
                screenshot = null,
                screenRect = null,
                activeSource = PickResultTextSource.A11Y,
                contentOrigin = PickResultContentOrigin.SCREEN_PICK,
            ),
            initialTextMode = PickResultTextMode.WORD_TAP,
        )
    }

    /**
     * 图片分词：先 OCR 出文字，再按文字分词处理。
     * 没有配置/没装 OCR 引擎时直接提示，不去跑一次注定失败的识别。
     */
    private fun segmentImage(bitmap: Bitmap) {
        val appContext = context.applicationContext
        scope.launch {
            val settings = runCatching {
                OverlayDependencyAccess.overlayDependencies(appContext)?.settingsRepository?.readSnapshot()
            }.getOrNull()
            val modelId = settings?.floatBallOcrModelId.orEmpty()
            val installed = modelId.isNotBlank() &&
                runCatching {
                    OcrDependencyAccess.modelRepository(appContext)?.isInstalled(modelId) == true
                }.getOrDefault(false)
            if (!installed) {
                Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_ocr_unavailable), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val recognized = withContext(Dispatchers.IO) {
                runCatching { RegionalScreenshotOcr.recognizeBitmapPublic(appContext, modelId, bitmap) }.getOrNull()
            }
            val text = (recognized as? OcrRecognizeResult.Success)?.text?.trim()
            withContext(Dispatchers.Main) {
                if (text.isNullOrEmpty()) {
                    Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_no_text), Toast.LENGTH_SHORT).show()
                } else {
                    openWordSegmentation(text)
                }
            }
        }
    }

    /** 已落盘、可以安全转交的分享目标。 */
    private data class ForwardFile(
        val uri: Uri,
        val mime: String,
        val clipData: ClipData,
    ) {
        /** Intent 的 type：认不出类型时退回通配符 MIME，跟旧实现一致，别把选择器收窄到一个都匹配不到。 */
        val intentType: String get() = if (mime == "application/octet-stream") "*/*" else mime
    }

    /**
     * 把拖进来的外部 URI 落成本应用缓存文件，再换成自己的 FileProvider URI。
     *
     * 外部 URI（相册 / 文件管理器的 `content://media/...`）的读权限只在来源应用手里，
     * 直接塞进 `EXTRA_STREAM` 转授给第三方时，AMS 会判定“越权转授”并当场抛
     * SecurityException（crash_20261022_213208：`UID 10486 does not have permission
     * to content://media/external_primary/file/1000048190`）。先复制成自己的缓存文件，
     * 再用 FileProvider + ClipData 授权，权限就全在自己手里，接收方也读得到。
     *
     * 返回 null 表示源文件读不出来：调用方应提示失败，而不是硬发一个谁都读不了的 URI。
     */
    private suspend fun stageForForward(uri: Uri): ForwardFile? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolveForwardMime(uri)
            val dir = forwardCacheDir()
            purgeForwardCache(dir)
            val target = newForwardFile(dir, forwardDisplayName(uri, mime))
            val copied = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (!copied || target.length() <= 0L) {
                // 拷一半失败也要清掉，别在缓存里留个半截文件。
                target.delete()
                return@runCatching null
            }
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target,
            )
            ForwardFile(
                uri = shareUri,
                mime = mime,
                clipData = ClipData.newUri(resolver, target.name, shareUri),
            )
        }.onFailure {
            Log.w(TAG, "stage forward failed, uri=$uri", it)
        }.getOrNull()
    }

    /**
     * MIME 必须自己定：目标应用只认 intent 的 `type`。
     *
     * 外部 URI 的 getType 会拿不到结果——没授权时抛 SecurityException，纯数字 ID 的
     * 相册 URI 还会返回 application/octet-stream——所以再按 URI 后缀、显示名后缀兜底。
     */
    private fun resolveForwardMime(uri: Uri): String {
        val declared = runCatching { context.contentResolver.getType(uri) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "*/*" && it != "application/octet-stream" }
        if (declared != null) return declared
        mimeFromExtension(extensionOf(uri.lastPathSegment))?.let { return it }
        mimeFromExtension(extensionOf(queryDisplayName(uri)))?.let { return it }
        return "application/octet-stream"
    }

    private fun mimeFromExtension(extension: String?): String? =
        extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }

    private fun extensionOf(raw: String?): String? =
        raw?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() && it.length in 1..8 }

    /**
     * 缓存文件必须带后缀：相册在 Android 14+ 给出的 URI 常常是纯数字 ID
     * （`.../file/1000048190`），没后缀时接收方会把它当成未知文件。
     */
    private fun forwardDisplayName(uri: Uri, mime: String): String {
        val raw = queryDisplayName(uri) ?: uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "drag"
        // 只取末段文件名，别让外部给的名字带出目录分隔符跑到缓存目录外面。
        val base = raw.replace('\\', '/').substringAfterLast('/').trim().ifBlank { "drag" }
        if (extensionOf(base) != null) return base
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        return "$base.$extension"
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun newForwardFile(dir: File, displayName: String): File {
        val candidate = File(dir, displayName)
        if (!candidate.exists()) return candidate
        // 同名再拖一次时不要覆盖上一份：那份可能正被目标应用读着。
        return File(dir, "${System.currentTimeMillis()}_$displayName")
    }

    private fun forwardCacheDir(): File =
        File(context.cacheDir, FORWARD_CACHE_DIR).apply { mkdirs() }

    /** 目标应用读完不会主动删，落新文件时顺手清掉过期的，缓存目录就不会越攒越大。 */
    private fun purgeForwardCache(dir: File) {
        val deadline = System.currentTimeMillis() - FORWARD_CACHE_TTL_MS
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < deadline) file.delete()
            }
        }
    }

    /** 先按目标应用直接拉起，失败再退系统选择器。异常不能带走浮层进程。 */
    private fun startShare(intent: Intent, chooserTitle: String) {
        val failure = runCatching { context.startActivity(intent) }.exceptionOrNull() ?: return
        Log.w(TAG, "direct share failed, fallback to chooser", failure)
        startChooser(intent, chooserTitle)
    }

    private fun startChooser(intent: Intent, chooserTitle: String) {
        runCatching {
            context.startActivity(
                Intent.createChooser(intent, chooserTitle)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Log.w(TAG, "chooser failed", it)
            Toast.makeText(context, context.getString(R.string.drag_drop_flow_toast_share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun decodeBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }

    private fun destroyWindow() {
        val view = composeView
        val owner = composeOwner
        composeView = null
        composeOwner = null
        anchorParams = null
        fullParams = null
        isExpanded = false

        if (view != null) {
            runCatching { windowManager.removeView(view) }
            OverlayCompose.teardownOverlayCompose(view, owner)
        }
    }
}
