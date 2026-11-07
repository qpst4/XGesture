package com.slideindex.app.overlay.history

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.voice.StashAudioRecordSession
import com.slideindex.app.voice.StashVoiceController
import com.slideindex.app.voice.StashVoiceSession
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 麦克风按钮 —— 三个输入面（输入槽 / 面板输入条 / 块编辑器）**共用同一个**。
 *
 * 两种模式（由 [onTapRecord] 是否有值决定，§0.16.21）：
 * - **null（默认，老行为）**：点一下 = [StashVoiceController.toggle]（语音识别）。
 *   输入槽 / 面板底部输入条用它 —— 那两处**没有正文块**，录下来的声音没有地方放。
 * - **非 null（块编辑器用）**：**点一下 = 录音开始/停止**，**长按 = 语音识别**（一字未改的老行为）。
 *   块编辑器有正文块，录音能作为语音块插到光标处。
 *
 * 状态来自两条会话（互相独立）：
 * - [StashVoiceSession]（识别）：正在听 → 实心图标 + 呼吸缩放；
 * - [StashAudioRecordSession]（录音）：正在录 → 实心图标 + 危险色。
 * - 出结果 → **只有点它的那一面**把文字取走（`ownsSession`），避免多处重复插入；
 * - 出错 → 交给调用方提示（面板用 snackbar、输入槽用 toast）。
 */
@Composable
fun HistoryVoiceMicButton(
    onResult: (String) -> Unit,
    onError: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 20.dp,
    /**
     * 点按的**替代**动作（§0.16.21 的录音）。
     *
     * null = 老行为（点按 = 语音识别开关）；非 null = 点按走它，语音识别挪到**长按**。
     */
    onTapRecord: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val listening = StashVoiceSession.state == StashVoiceSession.State.Listening
    val recording = StashAudioRecordSession.state == StashAudioRecordSession.State.Recording
    val active = listening || recording
    val finalText = StashVoiceSession.finalText
    val errorResId = StashVoiceSession.errorResId

    // 哪一面发起的会话，就由哪一面取结果：记下"点击时的 sessionId"，
    // 只有**之后真的开了新会话**（sessionId 变大）才认领 —— 否则用户拒绝权限、
    // 或点了停止，这一面会一直等着，把别人那轮的识别结果抢走。
    var claimedSessionId by remember { mutableIntStateOf(-1) }
    LaunchedEffect(claimedSessionId, finalText, errorResId) {
        if (claimedSessionId < 0) return@LaunchedEffect
        if (StashVoiceSession.sessionId <= claimedSessionId) return@LaunchedEffect
        if (finalText.isNotBlank()) {
            claimedSessionId = -1
            onResult(finalText)
            StashVoiceSession.consumeResult()
        } else if (errorResId != 0) {
            claimedSessionId = -1
            onError(errorResId)
            StashVoiceSession.consumeResult()
        }
    }

    /** 识别开关（点按的**老**语义，也是长按的新语义）：这一面把结果认领下来。 */
    val toggleRecognition: () -> Unit = {
        if (listening) {
            claimedSessionId = -1
        } else {
            claimedSessionId = StashVoiceSession.sessionId
        }
        StashVoiceController.toggle(context)
    }

    val scheme = MiuixTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "voiceMic")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voiceMicPulse",
    )
    val tint = when {
        recording -> scheme.error
        listening -> scheme.primary
        else -> scheme.onSurface
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (active) {
                    Modifier.background(tint.copy(alpha = 0.14f))
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // ⚠️ `onLongClick` 的类型是 `(() -> Unit)?`：老模式（onTapRecord == null）传 null
                // 就是"完全不挂长按"，行为与改造前**一字不差**。
                onLongClick = if (onTapRecord != null) toggleRecognition else null,
                onClick = {
                    if (onTapRecord != null) onTapRecord() else toggleRecognition()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (active) Icons.Default.Mic else Icons.Outlined.MicNone,
            contentDescription = stringResource(R.string.stash_voice_mic),
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    val scale = if (active) pulse else 1f
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}
