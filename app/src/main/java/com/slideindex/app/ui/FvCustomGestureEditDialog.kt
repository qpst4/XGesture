/*
 * FV「自定义手势」的录入弹窗（Miuix 样式，Compose）
 *
 * 与 FV 的对应关系（见 docs/reverse/fv-custom-gesture/05-ui-spec.md）：
 *   - 标题「手势」，画板高度 = 屏幕可用高 / 3（FV `q1.m`：`j2.d(ctx)/3`，单位 px）
 *   - 「执行」行       ← FVPrefItem(action_item)，点击去选动作
 *   - 「同时添加对称手势」← FVPrefItem(mirror_item)，仅图标库显示
 *   - 取消 / 确定     ← 弹窗底部按钮
 *
 * 为什么是 Compose 而不是 FV 那种 View Dialog：
 *   本项目设置页全部是 Compose + Miuix，用 `WindowDialog` 能直接得到与其它设置弹窗一致的外观
 *   （之前的 View Dialog 只把窗口背景设成透明、又没画底卡片，所以整块是"透明浮字"）。
 *   画板仍然用 View（`FvGesturePanel` 继承平台 `GestureOverlayView`），通过 `AndroidView` 嵌进来，
 *   这样轨迹绘制/淡出行为与 FV 逐像素一致。
 */
package com.slideindex.app.ui

import android.gesture.Gesture
import android.gesture.GestureOverlayView
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.slideindex.app.ui.miuix.MiuixAdaptiveDialog
import com.slideindex.app.R
import com.slideindex.app.fvgesture.FvGesturePanel
import com.slideindex.app.fvgesture.mirroredGesture
import com.slideindex.app.ui.miuix.MiuixArrowRow
import com.slideindex.app.ui.miuix.MiuixSwitchRow
import com.slideindex.app.ui.miuix.MiuixDialogActionButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * @param initialGesture 已有轨迹（编辑时回显；从动作选择页返回时也靠它把轨迹恢复出来）
 * @param initialMirror  「同时添加对称手势」的初值（由调用方持有，跨"去选动作"保留）
 * @param actionLabel    「执行」行显示的当前动作名（调用方用 gestureActionLabelText 取好）
 * @param onGestureDrawn 抬手回调：把刚画的轨迹交给调用方做冲突预检（FV `q1.m$c.onGestureEnded`）
 * @param onActionRowClick 点「执行」：调用方先把 (轨迹, 对称开关) 收进草稿，再去打开动作选择页
 * @param onMirrorChanged 对称开关变化：调用方记住它
 * @param onConfirm      点「确定」：调用方做校验与落库（是否关闭弹窗由调用方决定）
 */
@Composable
internal fun FvCustomGestureEditDialog(
    show: Boolean = true,
    initialGesture: Gesture?,
    initialMirror: Boolean,
    actionLabel: String,
    onGestureDrawn: (Gesture?) -> Unit,
    onActionRowClick: (gesture: Gesture?, mirror: Boolean) -> Unit,
    onMirrorChanged: (Boolean) -> Unit,
    onConfirm: (gesture: Gesture?, mirrored: Gesture?, mirror: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var mirror by remember { mutableStateOf(initialMirror) }
    val panelState = remember { mutableStateOf<FvGesturePanel?>(null) }

    // FV: 画板高 = 屏幕可用高 / 3（px → dp）。用 containerSize 而非 displayMetrics，
    // 与 `MiuixConfirmDialog` 里算最大高度用的是同一来源。
    val panelHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height / 3).toDp()
    }

    MiuixAdaptiveDialog(
        show = show,
        title = stringResource(R.string.fv_custom_gesture_dialog_title),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                factory = { ctx ->
                    FvGesturePanel(ctx).apply {
                        configureForEditDialog(showMarkers = true)
                        // 回显已有轨迹（编辑时 / 从动作选择页返回时）。
                        // 必须走 setGestureAfterLayout：平台 setGesture() 用**调用当时的宽高**做居中，
                        // 布局前调用会把轨迹挪到左上角（用户实测现象）。画板内部会等自己量出尺寸再 set。
                        setGestureAfterLayout(initialGesture)
                        addOnGestureListener(object : GestureOverlayView.OnGestureListener {
                            override fun onGestureStarted(v: GestureOverlayView, e: MotionEvent) = Unit
                            override fun onGesture(v: GestureOverlayView, e: MotionEvent) = Unit
                            override fun onGestureCancelled(v: GestureOverlayView, e: MotionEvent) = Unit
                            override fun onGestureEnded(v: GestureOverlayView, e: MotionEvent) {
                                onGestureDrawn((v as? FvGesturePanel)?.gesture)
                            }
                        })
                    }
                },
                update = { panel -> panelState.value = panel },
            )

            Spacer(Modifier.height(12.dp))

            // FV: action_item（「执行」+ 当前动作名）
            MiuixArrowRow(
                title = stringResource(R.string.fv_custom_gesture_action_row),
                summary = actionLabel,
                onClick = { onActionRowClick(panelState.value?.gesture, mirror) },
            )
            // FV: mirror_item（「同时添加对称手势」）。FV 只在图标手势库（type==1）显示，
            // 本页就是图标库，所以恒显示。
            MiuixSwitchRow(
                title = stringResource(R.string.fv_custom_gesture_mirror_row),
                checked = mirror,
                onCheckedChange = { checked ->
                    mirror = checked
                    onMirrorChanged(checked)
                },
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixDialogActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                )
                MiuixDialogActionButton(
                role = GlassDialogButtonRole.Primary,
                    text = stringResource(R.string.confirm),
                    onClick = {
                        val gesture = panelState.value?.gesture
                        // FV `q1.m.w()`：**开关关闭时必须返回 null**（否则会强制生成对称手势）。
                        val mirrored = if (mirror) {
                            mirroredGesture(gesture, panelState.value?.width ?: 0)
                        } else {
                            null
                        }
                        onConfirm(gesture, mirrored, mirror)
                    },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                )
            }
        }
    }
}
