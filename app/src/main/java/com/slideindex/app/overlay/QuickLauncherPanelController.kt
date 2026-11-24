package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherGridLogic.moveIndex
import com.slideindex.app.launcher.QuickLauncherDefaults
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.mergeIntoFolder
import com.slideindex.app.settings.AppSettings

internal class QuickLauncherPanelController(
    private val host: Host
) {
    interface Host {
        val context: Context
        fun settings(): AppSettings
        fun activeQuickLauncherPanel(): QuickLauncherPanel
        fun side(): PanelSide
        fun apps(): List<AppInfo>
        fun isPanelReady(): Boolean
        fun isAddDialogShowing(): Boolean
        fun dp(value: Float): Float
        fun sp(value: Float): Float
        fun invalidate()
        fun hapticTick()
        fun showAddDialog(
            configuredAppPackages: Set<String>,
            configuredShortcutKeys: Set<String>,
            configuredActionKeys: Set<String>,
            onAdd: (QuickLauncherItem) -> Unit,
            onRemove: (QuickLauncherItem) -> Unit
        )
        fun onPersist(items: List<QuickLauncherItem>)
        fun isQuickLauncherVisible(): Boolean
        fun quickLauncherPageSize(): Int
        fun onEditDragMove(touchX: Float, localY: Float, panelRect: RectF)
        fun onEditDragBegan()
        fun resolveEditDragTargetGlobal(touchX: Float, localY: Float, panelRect: RectF): Int
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
        fun switchToNextPanel()
        fun switchToPanel(panelId: String)
        fun allPanels(): List<QuickLauncherPanel>
        fun hasMultiplePanels(): Boolean
        fun currentPanelName(): String
        fun movePanelAnchor(deltaX: Float, deltaY: Float)
        fun togglePinMode()
        fun isPinned(): Boolean
        fun onFolderOpen(globalIndex: Int)
        fun onFolderItemsUpdated(globalIndex: Int, newChildren: List<QuickLauncherItem>)
    }

    typealias ToolbarAction = QuickLauncherPanelToolbar.ToolbarAction
    typealias ToolbarLayoutMetrics = QuickLauncherPanelToolbar.ToolbarLayoutMetrics

    fun allPanels(): List<QuickLauncherPanel> = host.allPanels()
    fun switchToPanel(panelId: String) = host.switchToPanel(panelId)
    fun movePanelAnchor(deltaX: Float, deltaY: Float) = host.movePanelAnchor(deltaX, deltaY)
    fun togglePinMode() = host.togglePinMode()

    private val toolbar = QuickLauncherPanelToolbar(this, host)
    private val management = QuickLauncherPanelManagementHandler(this, host, toolbar)

    var editMode: Boolean = false
        private set

    /**
     * 当前面板是否处于「钉住」态（由 [QuickLauncherOverlayController.draw] 每帧同步）。
     *
     * 工具栏据此多画一个「×」：钉住态不再抢焦点，返回键收不到派发，必须有一个看得见的出口。
     */
    var pinned: Boolean = false

    /** 工具栏「×」的回调（由 [QuickLauncherOverlayController] 注入）。 */
    var onCloseRequested: (() -> Unit)? = null

    var itemPageOffset: Int = 0
        internal set

    private var localItems: List<QuickLauncherItem> = emptyList()
    private var defaultsPersisted = false

    fun reset() {
        editMode = false
        toolbar.reset()
        management.reset()
        localItems = emptyList()
        defaultsPersisted = false
    }

    fun onActivePanelChanged() {
        localItems = emptyList()
        defaultsPersisted = false
    }

    fun setEditMode(enabled: Boolean) {
        if (editMode == enabled) return
        editMode = enabled
        if (!enabled) {
            management.onEditModeDisabled()
            toolbar.onEditModeDisabled()
            if (localItems.isNotEmpty()) {
                persistLocalItems()
            }
        }
        host.invalidate()
    }

    fun setItemPageOffset(offset: Int) {
        itemPageOffset = offset.coerceAtLeast(0)
    }

    fun syncSettings(@Suppress("UNUSED_PARAMETER") settings: AppSettings) {
        if (!host.isQuickLauncherVisible()) return
        if (editMode || management.isDragging()) return
        localItems = emptyList()
        defaultsPersisted = false
    }

    fun displayItems(settings: AppSettings, panel: QuickLauncherPanel): List<QuickLauncherItem> {
        if (shouldUseLocalItems(panel)) return localItems
        return QuickLauncherDefaults.effectiveItems(configuredItems(settings, panel), host.apps())
    }

    private fun shouldUseLocalItems(panel: QuickLauncherPanel): Boolean {
        if (localItems.isEmpty()) return false
        if (editMode || management.isDragging()) return true
        return defaultsPersisted && configuredItems(host.settings(), panel).isEmpty()
    }

    internal fun displayItems(): List<QuickLauncherItem> =
        displayItems(host.settings(), host.activeQuickLauncherPanel())

    fun ensureDefaultsPersisted(settings: AppSettings) {
        if (defaultsPersisted || configuredItems(settings, host.activeQuickLauncherPanel()).isNotEmpty()) return
        val defaults = QuickLauncherDefaults.fromApps(host.apps())
        if (defaults.isEmpty()) return
        defaultsPersisted = true
        localItems = defaults
        host.onPersist(defaults)
    }

    fun shouldShowToolbar(settings: AppSettings): Boolean = toolbar.shouldShowToolbar()
    fun toolbarLayoutMetrics(): ToolbarLayoutMetrics = toolbar.toolbarLayoutMetrics()
    fun contentReserveWidth(settings: AppSettings): Float = toolbar.contentReserveWidth()
    fun toolbarBounds(): RectF = toolbar.toolbarBounds()
    fun toolbarContains(localX: Float, localY: Float): Boolean = toolbar.toolbarContains(localX, localY)
    fun combinedContentRect(panelRect: RectF): RectF = toolbar.combinedContentRect(panelRect)
    fun layoutToolbar(panelRect: RectF) = toolbar.layoutToolbar(panelRect)
    fun drawToolbar(canvas: Canvas, panelRect: RectF) = toolbar.drawToolbar(canvas, panelRect)
    fun layoutDeleteBadges(cells: List<RectF>) =
        toolbar.layoutDeleteBadges(cells, management.dragSourceIndex())
    fun drawDeleteBadges(canvas: Canvas) = toolbar.drawDeleteBadges(canvas)
    fun cancelPendingDrag() = management.cancelPendingDrag()

    fun resolveToolbarAction(localX: Float, localY: Float, panelRect: RectF): ToolbarAction? =
        toolbar.resolveToolbarAction(localX, localY, panelRect)

    fun commitToolbarAtRelease(
        localX: Float,
        localY: Float,
        panelRect: RectF,
        tapGesture: Boolean,
        toolbarCommitAllowed: Boolean,
        allowSlideRelease: Boolean = false
    ): Boolean {
        val handled = toolbar.commitToolbarAtRelease(
            localX = localX,
            localY = localY,
            panelRect = panelRect,
            tapGesture = tapGesture,
            toolbarCommitAllowed = toolbarCommitAllowed,
            allowSlideRelease = allowSlideRelease
        )
        if (handled) {
            management.reset()
            host.invalidate()
        }
        return handled
    }

    fun handleManagementTouch(
        event: MotionEvent,
        localX: Float,
        localY: Float,
        panelRect: RectF,
        cellBounds: List<Pair<Any, RectF>>,
        tapGesture: Boolean = false,
        toolbarCommitAllowed: Boolean = true
    ): Boolean = management.handleManagementTouch(
        event = event,
        localX = localX,
        localY = localY,
        panelRect = panelRect,
        cellBounds = cellBounds,
        tapGesture = tapGesture,
        toolbarCommitAllowed = toolbarCommitAllowed
    )

    fun isDragging(): Boolean = management.isDragging()
    fun dragSourceIndex(): Int = management.dragSourceIndex()
    fun dragDestinationIndex(): Int = management.dragDestinationIndex()
    fun dragSourceGlobal(): Int = management.dragSourceGlobal()
    fun dragDestinationGlobal(): Int = management.dragDestinationGlobal()
    fun dragMergeTargetGlobal(): Int = management.dragMergeTargetGlobal()
    fun dragSourceOnPage(pageStart: Int, pageSize: Int): Boolean =
        management.dragSourceOnPage(pageStart, pageSize)
    fun dragSourceOnCurrentPage(): Boolean = management.dragSourceOnCurrentPage()
    fun dragPointerX(): Float = management.dragPointerX()
    fun dragPointerY(): Float = management.dragPointerY()
    fun dragVisualOffsetForPage(pageStart: Int, pageSize: Int): Pair<Float, Float> =
        management.dragVisualOffsetForPage(pageStart, pageSize)
    fun dragVisualOffset(index: Int): Pair<Float, Float> = management.dragVisualOffset(index)
    fun syncPageLocalDragTarget() = management.syncPageLocalDragTarget()

    internal fun openAddDialog(folderGlobalIndex: Int = -1) {
        if (host.isAddDialogShowing()) return
        val isTargetingFolder = folderGlobalIndex >= 0
        val currentItems = workingItems()
        val targetFolder = if (isTargetingFolder && folderGlobalIndex in currentItems.indices) {
            currentItems[folderGlobalIndex]
        } else {
            null
        }
        val items = if (targetFolder != null && targetFolder.type == QuickLauncherItemType.FOLDER) {
            targetFolder.folderItems()
        } else {
            currentItems
        }
        val configuredAppPackages = items
            .filter { it.type == QuickLauncherItemType.APP }
            .map { it.payload }
            .toSet()
        val configuredShortcutKeys = items
            .filter { it.type == QuickLauncherItemType.SHORTCUT }
            .mapNotNull { item -> QuickLauncherItemCodec.shortcutItemKey(item) }
            .toSet()
        val configuredActionKeys = items
            .filter { it.type == QuickLauncherItemType.ACTION }
            .mapNotNull { QuickLauncherItemCodec.parseActionPayload(it.payload)?.let(QuickLauncherItemCodec::actionKey) }
            .toSet()
        host.showAddDialog(
            configuredAppPackages,
            configuredShortcutKeys,
            configuredActionKeys,
            onAdd = { added ->
                if (isTargetingFolder && folderGlobalIndex in workingItems().indices) {
                    val rootItems = workingItems().toMutableList()
                    val folder = rootItems[folderGlobalIndex]
                    if (folder.type == QuickLauncherItemType.FOLDER) {
                        val newChildren = folder.folderItems() + added
                        val updatedFolder = folder.withFolderItems(newChildren)
                        rootItems[folderGlobalIndex] = updatedFolder
                        localItems = rootItems
                        persistLocalItems()
                        host.onFolderItemsUpdated(folderGlobalIndex, newChildren)
                    }
                } else {
                    localItems = workingItems() + added
                    persistLocalItems()
                }
                host.invalidate()
            },
            onRemove = { removed ->
                if (isTargetingFolder && folderGlobalIndex in workingItems().indices) {
                    val rootItems = workingItems().toMutableList()
                    val folder = rootItems[folderGlobalIndex]
                    if (folder.type == QuickLauncherItemType.FOLDER) {
                        val folderChildren = folder.folderItems()
                        val removeIdx = folderChildren.indexOfFirst { it.type == removed.type && it.payload == removed.payload }
                        if (removeIdx >= 0) {
                            val newChildren = folderChildren.filterIndexed { i, _ -> i != removeIdx }
                            val updatedFolder = folder.withFolderItems(newChildren)
                            rootItems[folderGlobalIndex] = updatedFolder
                            localItems = rootItems
                            persistLocalItems()
                            host.onFolderItemsUpdated(folderGlobalIndex, newChildren)
                        }
                    }
                } else {
                    val current = workingItems()
                    val removeIndex = current.indexOfFirst { item ->
                        item.type == removed.type && item.payload == removed.payload
                    }
                    if (removeIndex >= 0) {
                        localItems = current.filterIndexed { index, _ -> index != removeIndex }
                        persistLocalItems()
                    }
                }
                host.invalidate()
            }
        )
    }

    fun switchToNextPanel() {
        host.switchToNextPanel()
    }

    internal fun updateFolderItems(folderGlobalIndex: Int, newChildren: List<QuickLauncherItem>) {
        val current = workingItems().toMutableList()
        val folder = current.getOrNull(folderGlobalIndex) ?: return
        if (folder.type != QuickLauncherItemType.FOLDER) return
        current[folderGlobalIndex] = folder.withFolderItems(newChildren)
        localItems = current
        persistLocalItems()
        host.invalidate()
    }

    internal fun removeFolderChildItem(folderGlobalIndex: Int, childIndex: Int) {
        val current = workingItems().toMutableList()
        val folder = current.getOrNull(folderGlobalIndex) ?: return
        if (folder.type != QuickLauncherItemType.FOLDER) return
        val children = folder.folderItems()
        if (childIndex !in children.indices) return
        val updatedChildren = children.filterIndexed { i, _ -> i != childIndex }
        current[folderGlobalIndex] = folder.withFolderItems(updatedChildren)
        localItems = current
        persistLocalItems()
        host.onFolderItemsUpdated(folderGlobalIndex, updatedChildren)
        host.hapticTick()
        host.invalidate()
    }

    internal fun moveFolderChildItem(folderGlobalIndex: Int, from: Int, to: Int) {
        val current = workingItems().toMutableList()
        val folder = current.getOrNull(folderGlobalIndex) ?: return
        if (folder.type != QuickLauncherItemType.FOLDER) return
        val children = folder.folderItems()
        if (from !in children.indices || to !in 0..children.size || from == to) return
        val updatedChildren = children.moveIndex(from, to)
        current[folderGlobalIndex] = folder.withFolderItems(updatedChildren)
        localItems = current
        persistLocalItems()
        host.onFolderItemsUpdated(folderGlobalIndex, updatedChildren)
    }

    internal fun workingItems(): List<QuickLauncherItem> {
        val panel = host.activeQuickLauncherPanel()
        if (shouldUseLocalItems(panel)) return localItems
        return QuickLauncherDefaults.effectiveItems(
            configuredItems(host.settings(), panel),
            host.apps()
        )
    }

    internal fun removeItemAt(pageLocalIndex: Int) {
        val index = itemPageOffset + pageLocalIndex
        val current = workingItems()
        if (index !in current.indices) return
        localItems = current.filterIndexed { i, _ -> i != index }
        host.hapticTick()
        persistLocalItems()
        host.invalidate()
    }

    internal fun moveItemGlobal(from: Int, to: Int) {
        val current = workingItems().toMutableList()
        if (from !in current.indices || to !in 0..current.size) return
        if (from == to) return
        val item = current.removeAt(from)
        current.add(to.coerceIn(0, current.size), item)
        localItems = current
        persistLocalItems()
    }

    internal fun mergeItemsGlobal(from: Int, target: Int) {
        val current = workingItems()
        val next = current.mergeIntoFolder(from, target)
        if (next != current) {
            localItems = next
            persistLocalItems()
            host.hapticTick()
            host.invalidate()
        }
    }

    private fun persistLocalItems() {
        val items = localItems.ifEmpty { return }
        host.onPersist(items)
    }

    private fun configuredItems(settings: AppSettings, panel: QuickLauncherPanel): List<QuickLauncherItem> =
        panel.items

    fun collectAccessibilityNodes(context: Context, panelRect: RectF): List<OverlayVirtualNode> =
        toolbar.collectAccessibilityNodes(context, panelRect)
}
