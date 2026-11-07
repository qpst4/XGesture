package com.slideindex.app.overlay.history

import android.content.Context
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.overlay.OverlayComposeDialogHost
import com.slideindex.app.overlay.StashPanelExternalUi
import com.slideindex.app.overlay.rememberOverlayImeBottomHeight
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashTag
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlinx.coroutines.launch
import java.io.File

/**
 * 长按贴边指示条弹出的**完整「记一条」编辑窗**（§0.16.25）。
 *
 * ## 它和面板里的加号弹窗是**同一个东西**
 *
 * 内容与面板里的 [HistoryComposerModal] 一模一样：块编辑器（文字 / 图片 / 语音交错）、
 * 标签、⏰ 提醒、多行正文。本类只负责把它挂到一扇**独立的 overlay 窗**里，
 * 让用户不必先打开面板就能记一条。
 *
 * ⚠️ 因此**能力必须与面板那扇保持同步**：给弹窗加了什么能力（新的块类型、新的元数据），
 * 两边要一起有 —— 共用同一个 composable（[HistoryComposerModal]）与同一份落库实现
 * （[StashComposerSubmit]），不要在任何一边另写一套。
 *
 * ## 为什么用 [OverlayComposeDialogHost]，而不是像旧的输入槽那样手搓 ComposeView
 *
 * 旧的长按输入槽（`HistoryNoteSlotWindow`，已随本次改动删除）是裸 `ComposeView` +
 * 自己拼 `LayoutParams`，代价很具体：**没有 IME 避让**（那份代码自己也标了"输入法弹起时
 * 这个窗不会跟着上移"，一直没修），抢焦点 / 返回键 / 点外部收起也都要自己再来一遍。
 *
 * [OverlayComposeDialogHost] 已经带了这几样（九个浮层在用）：可抢焦点的窗、
 * `SOFT_INPUT_ADJUST_RESIZE`、`FLAG_NOT_TOUCH_MODAL` + 全屏透明宿主、`onBackPressed` 回调。
 *
 * ## 两条外挂路径
 *
 * - **选图**：overlay 里不能直接拉系统相册，走 [StashComposerImageTrampolineActivity]。
 *   拉起前必须把**本窗**挂起（它是覆盖层，不挂起会压在相册上面），回来第一件事恢复 ——
 *   这两个动作注册到 [StashPanelExternalUi]（它就是为这件事存在的：挂起 + 看门狗兜底）。
 *   面板与本窗**不会同时开着**（本窗收起才回桌面 / 面板），所以共用这一份状态是安全的。
 * - **录音**：走 `StashAudioRecorderController` 的前台服务，窗是谁不影响。
 */
class HistoryComposerWindow(
    private val context: Context,
    @Suppress("unused") private val windowManager: WindowManager,
) {
    private var host: OverlayComposeDialogHost? = null

    /**
     * 每次打开 +1：弹窗内部那条"延时抢焦点"的 `LaunchedEffect` 认它，
     * 于是第二次打开时焦点 / 输入法照样会被请出来（`open` 一直为 true 就不会重跑）。
     */
    private val openEpoch = mutableIntStateOf(0)

    fun show() {
        val dialogHost = host ?: OverlayComposeDialogHost(
            context = context,
            fullScreen = true,
        ).also { created ->
            created.show(
                // 返回键：交给内容层决定（有未保存内容时先问一句），并且**消费掉**这次返回
                // —— 不消费的话会继续落到系统返回（把桌面 App 也返回一层）。
                onBackPressed = {
                    requestClose()
                    true
                },
                onDismiss = { StashPanelExternalUi.clear() },
            ) { ComposerWindowContent() }
            if (created.isShowing) {
                host = created
                // 只有窗真的挂上之后才注册挂起 / 恢复钩子：窗没了还留着钩子 = 叫不动任何东西。
                registerExternalUiHooks(created)
            }
        }
        if (!dialogHost.isShowing) {
            // 挂不上（拿不到宿主 WindowManager / 悬浮窗权限被收回）：把 host 丢掉，下次长按重建，
            // 别留一个永远弹不出来的空壳。
            if (host === dialogHost) host = null
            Log.w(TAG, "编辑窗没挂上：宿主 WindowManager 拿不到（悬浮窗权限？），本次长按忽略")
            return
        }
        openEpoch.intValue++
        dialogHost.requestFocus()
    }

    /**
     * 关窗。**不清草稿** —— 清不清由调用方决定：
     * - 存下 / 用户明确放弃 → 内容层先 `clearDraft()` 再调它；
     * - 把手被藏起来（全屏 / 横屏 / 息屏 / 截图）→ 直接调它，正文留在进程级草稿单例里，
     *   用户切回来再长按还能接着写。
     */
    fun dismiss() {
        host?.dismiss()
    }

    fun destroy() {
        host?.dismiss()
        host = null
    }

    /** 返回键 / 点空白：交给内容层决定（有未保存内容时要先问一句）。 */
    private fun requestClose() {
        pendingClose.value = true
    }

    /**
     * 把"拉起外部 UI 期间挂起本窗"的两个动作注册给 overlay 里的 Compose 代码。
     *
     * 与 `FloatBallStashPanel.registerExternalUiHooks` 同一套。⚠️ 那两个钩子是**单槽**的：
     * 面板打开时会把自己那份注册上去（覆盖本窗这份），这也正是"两扇窗不会同时开着"的语义 ——
     * 谁在台上，谁负责挂起。
     */
    private fun registerExternalUiHooks(dialogHost: OverlayComposeDialogHost) {
        StashPanelExternalUi.suspend = { suppressed.value = true }
        StashPanelExternalUi.resume = {
            suppressed.value = false
            runCatching { dialogHost.requestFocus() }
        }
    }

    /**
     * 是否处于"为外部 UI 让位"的状态。
     *
     * 做法是**内容整体不画**（窗留着），不是 `dismiss()`：`dismiss` 会把组合整个销毁，
     * 用户从相册回来时焦点、滚动位置、正在录的语音块都会重置。而 `suspendFromWindow()`
     * 那条路会把 View 从 WindowManager 摘掉再挂回来，动静比"不画"大得多。
     */
    private val suppressed = mutableStateOf(false)

    /** 内容层看到它 → 弹"要丢弃吗"的确认。 */
    private val pendingClose = mutableStateOf(false)

    // -----------------------------------------------------------------------------------------
    // 内容
    // -----------------------------------------------------------------------------------------

    @Composable
    private fun ComposerWindowContent() {
        val appContext = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        // 草稿走**进程级单例**（与面板共用同一份）：窗被系统摘掉再回来，正文 / 标签 / 图都还在。
        val blocks = StashComposerDraft.blocks
        var tags by StashComposerDraft.tags
        var reminderAtMs by StashComposerDraft.reminderAtMs
        var submitting by remember { mutableStateOf(false) }
        var reminderPickerOpen by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        // overlay 窗里 `WindowInsets.ime` 常常是 0，必须用这个（与面板同一条理由）。
        val imeBottom = rememberOverlayImeBottomHeight()
        val metaRepo = StashAccess.metaRepository
        val metaStore = metaRepo?.store?.collectAsState()?.value
        val availableTags: List<StashTag> = remember(metaStore) {
            metaStore?.tags?.sortedBy { it.order } ?: emptyList()
        }

        /** 有内容可存？判据与面板提交时完全一致（非空文字块 / 有媒体块）。 */
        fun hasContent(): Boolean =
            blocks.any { it is DraftBlock.Image || it is DraftBlock.Audio } ||
                blocks.any { it is DraftBlock.Text && it.value.isNotBlank() }

        /**
         * 清草稿：块回到一个空文字块、标签 / 提醒归零，**并删掉 cache 里的临时图与录音**。
         *
         * ⚠️ 删文件只在"用户明确放弃 / 已经存下"两条路上做（存下时 `StashComposerSubmit`
         * 那边也会删一次，幂等）：这不只是清理垃圾 —— cache 里那份是**还没落盘**的副本，
         * 留着它下次打开弹窗就会看到一个指向已失效内容的图块。
         */
        fun clearDraft() {
            blocks.filterIsInstance<DraftBlock.Image>()
                .forEach { image -> runCatching { File(image.path).delete() } }
            blocks.filterIsInstance<DraftBlock.Audio>()
                .forEach { audio -> runCatching { File(audio.path).delete() } }
            StashComposerDraft.updateBlocks { listOf(StashComposerDraft.newEmptyTextBlock()) }
            StashComposerDraft.tags.value = emptySet()
            StashComposerDraft.reminderAtMs.value = null
        }

        val submit: () -> Unit = {
            if (!submitting) {
                submitting = true
                StashComposerSubmit.submit(
                    context = appContext,
                    blocks = blocks.toList(),
                    tags = tags,
                    reminderAtMs = reminderAtMs,
                    onEmpty = {
                        submitting = false
                        Toast.makeText(appContext, R.string.stash_composer_empty, Toast.LENGTH_SHORT)
                            .show()
                    },
                    onFailed = {
                        submitting = false
                        Toast.makeText(appContext, R.string.stash_save_failed, Toast.LENGTH_SHORT)
                            .show()
                    },
                    onSaved = { _, _ ->
                        // 存下了：清草稿 → 关窗。提示交给**把手脉冲 + peek 预览**
                        // （`StashCoordinator.addRich` 内部会 `notifySaved`），这里不再多弹一层。
                        clearDraft()
                        dismiss()
                    },
                )
            }
        }

        /** 关窗（可能先问一句）。 */
        val tryClose: () -> Unit = {
            if (hasContent()) pendingClose.value = true else dismiss()
        }

        if (pendingClose.value) {
            DiscardConfirm(
                onCancel = { pendingClose.value = false },
                onDiscard = {
                    pendingClose.value = false
                    clearDraft()
                    dismiss()
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (suppressed.value) {
                        // 让位给外部系统 UI（相册）：窗留着但不画、也不该吃触摸。
                        Modifier
                    } else {
                        Modifier
                            .background(Color.Black.copy(alpha = 0.32f))
                            // 点空白收起：与系统对话框一致（有内容时先问一句）。
                            // 提醒选择器开着时不响应：它自己有遮罩，但它只是一块画在本窗里的内容，
                            // 点空白该收的是选择器（由它自己的遮罩吃掉），不是整个编辑窗。
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !reminderPickerOpen,
                                onClick = tryClose,
                            )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (suppressed.value) return@Box
            OverlayAwareModuleTheme {
                // `key(openEpoch)`：第二次打开时重建这一块，弹窗内部的"延时抢焦点"会重跑。
                key(openEpoch.intValue) {
                    HistoryComposerModal(
                        open = true,
                        blocks = blocks,
                        onBlocksChange = { transform -> StashComposerDraft.updateBlocks(transform) },
                        onSubmit = submit,
                        onVoiceError = { messageResId ->
                            Toast.makeText(appContext, messageResId, Toast.LENGTH_SHORT).show()
                        },
                        availableTags = availableTags,
                        selectedTags = tags,
                        onToggleTag = { name ->
                            tags = if (name in tags) tags - name else tags + name
                        },
                        reminderAtMs = reminderAtMs,
                        onReminderClick = { reminderPickerOpen = true },
                        onAddImage = { onPicked ->
                            // 拉起系统相册前挂起本窗（它是覆盖层，不挂起会盖在相册上面）；
                            // 回调里**第一件事**恢复 —— 用户取消（picked 为空）也要恢复。
                            StashPanelExternalUi.suspendForExternalUi(
                                caller = "composer-window-add-image",
                            )
                            com.slideindex.app.overlay.panels.PanelRuntimeHooks.pickStashComposerImages?.invoke(appContext) { picked ->
                                StashPanelExternalUi.resumeAfterExternalUi(
                                    caller = "选图回调（编辑窗）",
                                )
                                if (picked.isNotEmpty()) onPicked(picked.distinct())
                            }
                        },
                        // 删块时把 cache 里那份临时文件也删掉（与面板同一条）。
                        onRemoveImage = { path ->
                            scope.launch { runCatching { File(path).delete() } }
                        },
                        onRemoveAudio = { path ->
                            scope.launch { runCatching { File(path).delete() } }
                        },
                        imeBottom = imeBottom,
                        focusRequester = focusRequester,
                        // 每次打开都要把焦点/输入法重新请出来（`open` 恒为 true，只有它能触发）。
                        focusEpoch = openEpoch.intValue,
                    )
                }
            }
            // 提醒时间选择器（§0.16.9）：与面板**同一个组件**、同一个草稿字段。
            //
            // ⚠️ 它是一块"画在窗里的居中模态"（不是独立窗），所以必须画在本窗内、且画在
            // 编辑弹窗**之后** —— 它的遮罩要靠后画才能吃掉落在自己身上的点击。
            if (reminderPickerOpen) {
                HistoryReminderPickerModal(
                    open = true,
                    currentAtMs = reminderAtMs,
                    imeBottom = imeBottom,
                    onPick = { at ->
                        reminderAtMs = at
                        reminderPickerOpen = false
                    },
                    onDismiss = { reminderPickerOpen = false },
                )
            }
        }
    }

    /**
     * 「有未保存内容时收起」的确认。
     *
     * 为什么必须有：收起会**连 cache 里的临时图与录音一起删掉**（那是"用户明确放弃"的语义），
     * 不能因为一次误触返回就把编辑到一半的东西清光。
     *
     * 用现成的 [MiuixConfirmDialog]（自带 `WindowDialog` 壳 + 取消/确认两个按钮，与全 App 一致）：
     * **不新造一套 UI、也不新增文案资源** —— 标题借用加号弹窗自己的名字（"记一条"），
     * 确认按钮是"已删除"（丢掉这条草稿），取消按钮是通用的「取消」。
     */
    @Composable
    private fun DiscardConfirm(
        onCancel: () -> Unit,
        onDiscard: () -> Unit,
    ) {
        MiuixConfirmDialog(
            show = true,
            onDismissRequest = onCancel,
            title = stringResource(R.string.stash_composer_fab),
            confirmText = stringResource(R.string.stash_deleted),
            onConfirm = onDiscard,
            dismissText = stringResource(R.string.cancel),
        )
    }

    private companion object {
        const val TAG = "HistoryComposerWindow"
    }
}
