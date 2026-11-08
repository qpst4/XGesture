package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardImageLabel
import com.slideindex.app.clipboard.ClipboardImageStore
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.clipboard.isPureImageEntry
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.settings.ClipboardFloatEntryClickAction

enum class PasteFailureReason {
    NO_ACTIVE_WINDOW,
    NO_EDITABLE_FOCUS,
    PASTE_AND_INSERT_FAILED,
}

sealed class PasteResult {
    data object Success : PasteResult()
    data class Failure(val reason: PasteFailureReason) : PasteResult()
}

object ClipboardPasteHelper {

    private const val PASTE_SETTLE_MS = 80L

    fun isPasteTarget(node: AccessibilityNodeInfo): Boolean {
        if (canPaste(node)) return true
        if (isKnownInputContainer(node)) return true
        if (isLikelyInputField(node)) return true
        return isLikelyInputFieldRelaxed(node) && !node.isVisibleToUser
    }

    /** FV E3: paste clipboard entry into the editable under screen point (no per-frame tree walk). */
    fun pasteAtScreenPoint(
        service: AccessibilityService,
        context: Context,
        entry: ClipboardEntry,
        x: Float,
        y: Float,
    ): PasteResult {
        val px = x.toInt()
        val py = y.toInt()
        focusAtScreenPoint(service, px.toFloat(), py.toFloat())
        val target = findPasteTargetAtPointAcrossWindows(service, px, py)
            ?: return PasteResult.Failure(PasteFailureReason.NO_EDITABLE_FOCUS)
        return try {
            pasteIntoResolvedTarget(
                context = context,
                node = target,
                entry = entry,
            )
        } finally {
            recycleNode(target)
        }
    }

    /** FV e1: paste into the best editable intersecting [rect] (QQ zaq container + child EditText). */
    fun pasteAtScreenRect(
        service: AccessibilityService,
        context: Context,
        entry: ClipboardEntry,
        rect: Rect,
    ): PasteResult {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return PasteResult.Failure(PasteFailureReason.NO_EDITABLE_FOCUS)
        }
        focusAtRectCenter(service, rect)
        val target = findPasteTargetInRectAcrossWindows(service, rect)
            ?: return PasteResult.Failure(PasteFailureReason.NO_EDITABLE_FOCUS)
        return try {
            pasteIntoResolvedTarget(
                context = context,
                node = target,
                entry = entry,
            )
        } finally {
            recycleNode(target)
        }
    }

    private fun pasteIntoResolvedTarget(
        context: Context,
        node: AccessibilityNodeInfo,
        entry: ClipboardEntry,
    ): PasteResult {
        val resolved = refinePasteTarget(node)
        val owned = resolved !== node
        return try {
            pasteIntoNode(
                context = context,
                node = resolved,
                entry = entry,
                clipboardAlreadyPrepared = false
            )
        } finally {
            if (owned) recycleNode(resolved)
        }
    }

    private fun focusAtRectCenter(service: AccessibilityService, rect: Rect) {
        focusAtScreenPoint(service, rect.exactCenterX(), rect.exactCenterY())
    }

    private fun focusAtScreenPoint(service: AccessibilityService, x: Float, y: Float) {
        val px = x.toInt()
        val py = y.toInt()
        if (Looper.myLooper() != Looper.getMainLooper()) {
            com.slideindex.app.clipboardfloat.ClipboardFloatHostAccess.host?.dispatchTap(service, x, y)
            SystemClock.sleep(PASTE_SETTLE_MS)
        }
        val node = findPasteTargetAtPointAcrossWindows(service, px, py)
            ?: findFocusableClickableAtPointAcrossWindows(service, px, py)
        if (node == null) return
        try {
            prepareNodeForPaste(node)
        } finally {
            recycleNode(node)
        }
    }

    fun performEntryAction(
        service: AccessibilityService,
        context: Context,
        entry: ClipboardEntry,
        action: ClipboardFloatEntryClickAction
    ): PasteResult {
        return when (action) {
            ClipboardFloatEntryClickAction.COPY -> {
                com.slideindex.app.overlay.panels.PanelStashWriteHooks.writeClipboardEntry?.invoke(context, entry)
                PasteResult.Success
            }
            ClipboardFloatEntryClickAction.PASTE -> {
                pasteIntoFocusedField(
                    service = service,
                    context = context,
                    entry = entry,
                    clipboardAlreadyPrepared = false
                )
            }
            ClipboardFloatEntryClickAction.COPY_AND_PASTE -> {
                com.slideindex.app.overlay.panels.PanelStashWriteHooks.writeClipboardEntry?.invoke(context, entry)
                pasteIntoFocusedField(
                    service = service,
                    context = context,
                    entry = entry,
                    clipboardAlreadyPrepared = true
                )
            }
        }
    }

    fun pasteEntryToFocusedField(
        service: AccessibilityService,
        context: Context,
        entry: ClipboardEntry,
    ): PasteResult = pasteIntoFocusedField(
        service = service,
        context = context,
        entry = entry,
        clipboardAlreadyPrepared = false
    )

    private fun pasteIntoFocusedField(
        service: AccessibilityService,
        context: Context,
        entry: ClipboardEntry,
        clipboardAlreadyPrepared: Boolean
    ): PasteResult {
        val focused = findFocusedEditableAcrossWindows(service)
            ?: return PasteResult.Failure(PasteFailureReason.NO_EDITABLE_FOCUS)
        return try {
            pasteIntoNode(
                context = context,
                node = focused,
                entry = entry,
                clipboardAlreadyPrepared = clipboardAlreadyPrepared
            )
        } finally {
            recycleNode(focused)
        }
    }

    private fun pasteIntoNode(
        context: Context,
        node: AccessibilityNodeInfo,
        entry: ClipboardEntry,
        clipboardAlreadyPrepared: Boolean
    ): PasteResult {
        prepareNodeForPaste(node)
        val entryText = resolveEntryPasteText(entry)
        val beforeText = readEditableContent(node, readHintText(node))

        if (!entry.hasImageContent()) {
            // Try ACTION_PASTE first for text entries to avoid hint text issues
            // (e.g. Telegram puts hint in node.text instead of hintText).
            if (supportsPaste(node)) {
                if (!clipboardAlreadyPrepared) {
                    com.slideindex.app.overlay.panels.PanelStashWriteHooks.writeClipboardForPaste?.invoke(context, entry)
                }
                if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE) &&
                    verifyTextPaste(node, entryText, beforeText)
                ) {
                    return PasteResult.Success
                }
            }
            // Fall back to SET_TEXT with enhanced hint detection.
            if (entryText != null && canTrySetText(node)) {
                return insertViaSetText(node, entryText)
            }
            return PasteResult.Failure(PasteFailureReason.PASTE_AND_INSERT_FAILED)
        }

        if (!clipboardAlreadyPrepared) {
            try {
                com.slideindex.app.overlay.panels.PanelStashWriteHooks.writeClipboardForPaste?.invoke(context, entry)
            } catch (_: RuntimeException) {
                // TransactionTooLargeException fallback: try text-only paste.
                if (entryText != null && canTrySetText(node)) {
                    return insertViaSetText(node, entryText)
                }
                return PasteResult.Failure(PasteFailureReason.PASTE_AND_INSERT_FAILED)
            }
        }

        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            if (entry.hasImageContent()) {
                return PasteResult.Success
            }
            if (verifyTextPaste(node, entryText, beforeText)) {
                return PasteResult.Success
            }
        }

        if (entryText != null && canTrySetText(node)) {
            return insertViaSetText(node, entryText)
        }
        return PasteResult.Failure(PasteFailureReason.PASTE_AND_INSERT_FAILED)
    }

    private fun canTrySetText(node: AccessibilityNodeInfo): Boolean =
        supportsSetText(node) || isLikelyInputFieldRelaxed(node)

    private fun insertViaSetText(
        node: AccessibilityNodeInfo,
        clipText: String
    ): PasteResult {
        prepareNodeForPaste(node)
        val hint = readHintText(node)
        val snapshot = ClipboardPasteTextLogic.snapshotEditableText(node.text, hint)
        val merged = if (snapshot.content.isEmpty() || isLikelyHintText(node, hint)) {
            clipText
        } else {
            val rawStart = readSelectionStart(node)
            val rawEnd = readSelectionEnd(node)
            val start = (rawStart - snapshot.leadingPlaceholderLength).coerceIn(0, snapshot.content.length)
            val end = (rawEnd - snapshot.leadingPlaceholderLength).coerceIn(start, snapshot.content.length)
            ClipboardPasteTextLogic.mergeClipAtSelection(
                currentText = snapshot.content,
                clipText = clipText,
                selectionStart = start,
                selectionEnd = end
            )
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return PasteResult.Failure(PasteFailureReason.PASTE_AND_INSERT_FAILED)
        }
        return if (verifyTextPaste(node, clipText, snapshot.content, expectedAfterSetText = merged)) {
            PasteResult.Success
        } else {
            PasteResult.Failure(PasteFailureReason.PASTE_AND_INSERT_FAILED)
        }
    }

    private fun resolveEntryPasteText(entry: ClipboardEntry): String? {
        if (entry.isPureImageEntry()) return null
        val imageSources = ClipboardImageStore.collectImageSourcesForEntry(entry)
        val blocks = ClipboardImageLabel.blocksForClipboardWrite(
            blocks = entry.resolvedContentBlocks(),
            imageSources = imageSources,
            uri = entry.uri
        )
        val textBlocks = blocks.filter { it.kind == ClipboardBlockKind.TEXT }
        if (textBlocks.isNotEmpty()) {
            return textBlocks.joinToString("\n") { it.text.trim() }
                .trim()
                .takeIf { it.isNotEmpty() }
        }
        return entry.text.trim().takeIf { it.isNotEmpty() }
    }

    private fun readHintText(node: AccessibilityNodeInfo): CharSequence? {
        return node.hintText
    }

    /** FV-style: click to attach IME, then focus before paste/set-text. */
    private fun prepareNodeForPaste(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        node.refresh()
        SystemClock.sleep(PASTE_SETTLE_MS)
    }

    private fun readEditableContent(node: AccessibilityNodeInfo, hint: CharSequence?): String {
        if (isLikelyHintText(node, hint)) return ""
        return ClipboardPasteTextLogic.snapshotEditableText(node.text, hint).content
    }

    /**
     * Reject accessibility "false success" (performAction true but no visible text change).
     */
    private fun verifyTextPaste(
        node: AccessibilityNodeInfo,
        entryText: String?,
        beforeText: String,
        expectedAfterSetText: String? = null
    ): Boolean {
        node.refresh()
        settleAfterPaste()
        node.refresh()
        val after = readEditableContent(node, readHintText(node))
        if (expectedAfterSetText != null) {
            return after == expectedAfterSetText ||
                after.contains(expectedAfterSetText) ||
                expectedAfterSetText.contains(after)
        }
        val clip = entryText?.trim().orEmpty()
        if (clip.isEmpty()) {
            return after != beforeText
        }
        if (after.contains(clip)) return true
        if (beforeText.isNotEmpty() && after.length > beforeText.length && after.startsWith(beforeText)) {
            return after.substring(beforeText.length).contains(clip)
        }
        return after == beforeText + clip || after.endsWith(clip)
    }

    private fun settleAfterPaste() {
        SystemClock.sleep(PASTE_SETTLE_MS)
    }

    private fun readSelectionStart(node: AccessibilityNodeInfo): Int {
        val textLength = node.text?.length ?: 0
        val start = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.textSelectionStart
        } else {
            AccessibilityNodeInfoCompat.wrap(node).textSelectionStart
        }
        return if (start >= 0) start else textLength
    }

    private fun readSelectionEnd(node: AccessibilityNodeInfo): Int {
        val textLength = node.text?.length ?: 0
        val end = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.textSelectionEnd
        } else {
            AccessibilityNodeInfoCompat.wrap(node).textSelectionEnd
        }
        return if (end >= 0) end else textLength
    }

    private fun findPasteTargetAtPointAcrossWindows(
        service: AccessibilityService,
        px: Int,
        py: Int,
    ): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        fun considerRoot(root: AccessibilityNodeInfo) {
            val hit = findPasteTargetAtPoint(root, px, py)
            if (hit == null) return
            val bounds = Rect()
            hit.getBoundsInScreen(bounds)
            val area = bounds.width().coerceAtLeast(1) * bounds.height().coerceAtLeast(1)
            if (area < bestArea) {
                recycleNode(best)
                best = hit
                bestArea = area
            } else {
                recycleNode(hit)
            }
        }
        for (window in service.windows) {
            if (shouldSkipPasteWindow(window)) continue
            val root = window.root ?: continue
            try {
                considerRoot(root)
            } finally {
                recycleNode(root)
            }
        }
        val active = service.rootInActiveWindow
        if (active != null) {
            try {
                considerRoot(active)
            } finally {
                recycleNode(active)
            }
        }
        return best
    }

    private fun findFocusableClickableAtPointAcrossWindows(
        service: AccessibilityService,
        px: Int,
        py: Int,
    ): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        val bounds = Rect()
        fun considerRoot(root: AccessibilityNodeInfo) {
            val stack = ArrayDeque<AccessibilityNodeInfo>()
            stack.addLast(root)
            while (stack.isNotEmpty()) {
                val current = stack.removeLast()
                val owned = current !== root
                try {
                    if (shouldSkipNodeForPointHit(current)) continue
                    current.getBoundsInScreen(bounds)
                    if (!bounds.contains(px, py)) continue
                    if (current.isFocusable || current.isClickable || isKnownInputContainer(current)) {
                        val area = bounds.width().coerceAtLeast(1) * bounds.height().coerceAtLeast(1)
                        if (area < bestArea) {
                            recycleNode(best)
                            best = copyNode(current)
                            bestArea = area
                        }
                    }
                    for (i in current.childCount - 1 downTo 0) {
                        current.getChild(i)?.let { stack.addLast(it) }
                    }
                } finally {
                    if (owned) recycleNode(current)
                }
            }
        }
        for (window in service.windows) {
            if (shouldSkipPasteWindow(window)) continue
            val root = window.root ?: continue
            try {
                considerRoot(root)
            } finally {
                recycleNode(root)
            }
        }
        return best
    }

    private fun refinePasteTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        if (canPaste(node) || isLikelyInputField(node) || isLikelyInputFieldRelaxed(node)) {
            return node
        }
        val inner = findSmallestPasteDescendant(node)
        return inner ?: node
    }

    private fun findSmallestPasteDescendant(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        val bounds = Rect()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val owned = current !== root
            try {
                if (canPaste(current) || isLikelyInputField(current) || isLikelyInputFieldRelaxed(current)) {
                    current.getBoundsInScreen(bounds)
                    val area = bounds.width().coerceAtLeast(1) * bounds.height().coerceAtLeast(1)
                    if (area < bestArea) {
                        recycleNode(best)
                        best = copyNode(current)
                        bestArea = area
                    }
                }
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { stack.addLast(it) }
                }
            } finally {
                if (owned) recycleNode(current)
            }
        }
        return best
    }

    private fun findPasteTargetInRectAcrossWindows(
        service: AccessibilityService,
        rect: Rect,
    ): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        fun considerRoot(root: AccessibilityNodeInfo) {
            val hit = findPasteTargetInRect(root, rect, preferFocus = false)
            if (hit == null) return
            val bounds = Rect()
            hit.getBoundsInScreen(bounds)
            val area = bounds.width().coerceAtLeast(1) * bounds.height().coerceAtLeast(1)
            if (area < bestArea) {
                recycleNode(best)
                best = hit
                bestArea = area
            } else {
                recycleNode(hit)
            }
        }
        for (window in service.windows) {
            if (shouldSkipPasteWindow(window)) continue
            val root = window.root ?: continue
            try {
                considerRoot(root)
            } finally {
                recycleNode(root)
            }
        }
        val active = service.rootInActiveWindow
        if (active != null) {
            try {
                considerRoot(active)
            } finally {
                recycleNode(active)
            }
        }
        return best
    }

    private fun shouldSkipPasteWindow(window: AccessibilityWindowInfo): Boolean {
        return when (window.type) {
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
            AccessibilityWindowInfo.TYPE_INPUT_METHOD,
            -> true
            else -> false
        }
    }

    private fun findFocusedEditableAcrossWindows(service: AccessibilityService): AccessibilityNodeInfo? {
        for (window in service.windows) {
            if (shouldSkipPasteWindow(window)) continue
            val root = window.root ?: continue
            try {
                val focused = findFocusedEditableNode(root)
                if (focused != null) return focused
            } finally {
                recycleNode(root)
            }
        }
        val active = service.rootInActiveWindow
        if (active != null) {
            try {
                return findFocusedEditableNode(active)
            } finally {
                recycleNode(active)
            }
        }
        return null
    }

    private fun findPasteTargetInRect(
        root: AccessibilityNodeInfo,
        rect: Rect,
        preferFocus: Boolean = true,
    ): AccessibilityNodeInfo? {
        if (preferFocus) {
            val focused = findFocusedEditableNode(root)
            if (focused != null) {
                val bounds = Rect()
                focused.getBoundsInScreen(bounds)
                if (Rect.intersects(bounds, rect)) {
                    return focused
                }
                recycleNode(focused)
            }
        }
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        val nodeBounds = Rect()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val owned = current !== root
            try {
                if (shouldSkipNodeForPointHit(current)) continue
                current.getBoundsInScreen(nodeBounds)
                if (!Rect.intersects(nodeBounds, rect)) continue
                if (isPasteTarget(current)) {
                    val area = nodeBounds.width().coerceAtLeast(1) * nodeBounds.height().coerceAtLeast(1)
                    if (area < bestArea) {
                        recycleNode(best)
                        best = copyNode(current)
                        bestArea = area
                    }
                }
                for (i in current.childCount - 1 downTo 0) {
                    val child = current.getChild(i) ?: continue
                    stack.addLast(child)
                }
            } finally {
                if (owned) recycleNode(current)
            }
        }
        return best
    }

    private fun findPasteTargetAtPoint(root: AccessibilityNodeInfo, px: Int, py: Int): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        val bounds = Rect()
        val childBounds = Rect()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val owned = current !== root
            try {
                if (shouldSkipNodeForPointHit(current)) continue
                current.getBoundsInScreen(bounds)
                if (!bounds.contains(px, py)) continue
                if (isPasteTarget(current)) {
                    val area = bounds.width().coerceAtLeast(1) * bounds.height().coerceAtLeast(1)
                    if (area < bestArea) {
                        recycleNode(best)
                        best = copyNode(current)
                        bestArea = area
                    }
                }
                for (i in current.childCount - 1 downTo 0) {
                    val child = current.getChild(i) ?: continue
                    stack.addLast(child)
                }
            } finally {
                if (owned) recycleNode(current)
            }
        }
        return best
    }

    private fun shouldSkipNodeForPointHit(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) {
            return !isLikelyInputFieldRelaxed(node) && !isKnownInputContainer(node)
        }
        return false
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocus != null && isPasteTarget(inputFocus)) return inputFocus
        recycleNode(inputFocus)
        val accessibilityFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (accessibilityFocus != null && isPasteTarget(accessibilityFocus)) return accessibilityFocus
        recycleNode(accessibilityFocus)
        return findBestEditableNode(root)
    }

    private fun findBestEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val owned = node !== root
            try {
                if (isPasteTarget(node)) {
                    candidates.add(copyNode(node))
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let { stack.add(it) }
                }
            } finally {
                if (owned) recycleNode(node)
            }
        }
        return candidates.maxWithOrNull(
            compareByDescending<AccessibilityNodeInfo> { it.isFocused }
                .thenByDescending { supportsSetText(it) }
                .thenByDescending { it.className?.toString().orEmpty().contains("EditText", ignoreCase = true) }
        )
    }

    private fun canPaste(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || !node.isEnabled) return false
        return node.isEditable || supportsSetText(node) || supportsPaste(node)
    }

    /** QQ search etc.: EditText before IME exposes PASTE / editable flags. */
    private fun isLikelyInputField(node: AccessibilityNodeInfo): Boolean {
        if (!node.isEnabled || !node.isVisibleToUser) return false
        return isLikelyInputFieldRelaxed(node)
    }

    private fun isLikelyInputFieldRelaxed(node: AccessibilityNodeInfo): Boolean {
        if (!node.isEnabled) return false
        val cls = node.className?.toString().orEmpty()
        return cls.contains("EditText", ignoreCase = true) ||
            cls.contains("AutoCompleteTextView", ignoreCase = true)
    }

    /** QQ chat/search outer input host (e.g. zaq). */
    private fun isKnownInputContainer(node: AccessibilityNodeInfo): Boolean {
        if (!node.isEnabled || !node.isVisibleToUser) return false
        val id = node.viewIdResourceName ?: return false
        if (id.endsWith(":id/zaq")) return true
        if (id.endsWith(":id/input")) return true
        if (id.endsWith(":id/et_search")) return true
        if (id.contains("mobileqq") && id.contains("search", ignoreCase = true)) return true
        if (id.contains("search", ignoreCase = true) &&
            (id.contains("edit", ignoreCase = true) || id.contains("input", ignoreCase = true))
        ) {
            return true
        }
        return false
    }

    private fun supportsSetText(node: AccessibilityNodeInfo): Boolean {
        return node.actionList.any { action ->
            action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.id
        }
    }

    private fun supportsPaste(node: AccessibilityNodeInfo): Boolean {
        return node.actionList.any { action ->
            action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE.id
        }
    }

    private fun copyNode(source: AccessibilityNodeInfo): AccessibilityNodeInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityNodeInfo(source)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.obtain(source)
        }

    private fun recycleNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        node.recycle()
    }

    /**
     * Detect placeholder/hint text that some apps (e.g. Telegram) put in
     * [AccessibilityNodeInfo.getText] instead of [AccessibilityNodeInfo.getHintText].
     */
    private fun isLikelyHintText(node: AccessibilityNodeInfo, hintText: CharSequence?): Boolean {
        // If standard hintText was present, snapshotEditableText already handled it.
        if (!hintText.isNullOrEmpty()) return false
        val text = node.text?.toString() ?: return false
        if (text.isEmpty()) return false
        // Read raw selection positions without fallback.
        val rawStart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.textSelectionStart
        } else {
            AccessibilityNodeInfoCompat.wrap(node).textSelectionStart
        }
        val rawEnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.textSelectionEnd
        } else {
            AccessibilityNodeInfoCompat.wrap(node).textSelectionEnd
        }
        // No selection set (both -1): the "text" is likely a placeholder/hint.
        if (rawStart < 0 && rawEnd < 0) return true
        // Text matches contentDescription — another common hint pattern.
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrEmpty() && desc == text) return true
        return false
    }
}
