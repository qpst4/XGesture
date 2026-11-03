package com.slideindex.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.slideindex.app.gesture.multitouch.PathPoint
import com.slideindex.app.gesture.multitouch.PathStroke
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsLazyHint
import kotlin.math.hypot

private val RecordStrokeColors = listOf(
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
)

/**
 * 路径手势录制页（独立页面，不是弹窗）。
 *
 * 一条轨迹 = 一根手指：画完一条松手，可以继续画下一条，最多 5 条；
 * 进入页面时暂停识别，离开时恢复，避免录制动作本身触发已有手势。
 */
@Composable
fun MultiTouchPathRecordScreen(
    onBack: () -> Unit,
    onRecordingStateChange: (Boolean) -> Unit,
    onFinish: (List<PathStroke>) -> Unit,
) {
    DisposableEffect(Unit) {
        onRecordingStateChange(true)
        onDispose { onRecordingStateChange(false) }
    }

    val strokes = remember { mutableListOf<MutableList<PathPoint>>() }
    val strokeIndex = remember { mutableMapOf<Int, Int>() }
    var version by remember { mutableIntStateOf(0) }

    SettingsScreenScaffold(title = "录制路径手势", onBack = onBack) {
        item(key = "multitouch-record-canvas") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "一条轨迹 = 一根手指",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "画完一条松手，可继续画下一条（2–5 条），画完点「完成」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        // 画到屏幕左右边缘时不触发系统返回手势。
                        .systemGestureExclusion()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            val boardHeight = size.height.toFloat().coerceAtLeast(1f)
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val active = event.changes.filter { it.pressed }
                                    if (active.isEmpty()) {
                                        strokeIndex.clear()
                                        continue
                                    }
                                    for (change in active) {
                                        val id = change.id.value.toInt()
                                        var index = strokeIndex[id]
                                        if (index == null) {
                                            if (strokes.size >= 5) continue
                                            strokes.add(mutableListOf())
                                            index = strokes.lastIndex
                                            strokeIndex[id] = index
                                        }
                                        val x = change.position.x / boardHeight
                                        val y = change.position.y / boardHeight
                                        val points = strokes[index]
                                        val last = points.lastOrNull()
                                        if (last == null ||
                                            hypot(x - last.x, y - last.y) >= 0.006f
                                        ) {
                                            points += PathPoint(x, y)
                                        }
                                    }
                                    version++
                                }
                            }
                        }
                ) {
                    RecordCanvas(strokes = strokes, version = version)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "已画 ${strokes.size}/5 条",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            strokes.clear()
                            strokeIndex.clear()
                            version++
                        },
                    ) { Text("重画") }
                    TextButton(
                        onClick = {
                            onFinish(strokes.map { points -> PathStroke(points.toList()) })
                        },
                        enabled = strokes.size >= 2,
                    ) { Text("完成") }
                }
            }
        }
        settingsLazyHint(
            key = "multitouch-record-hint",
            text = "录制期间多指识别已暂停；完成后会进入动作选择页，选好动作即可保存。",
        )
    }
}

@Composable
private fun RecordCanvas(strokes: List<List<PathPoint>>, version: Int) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (version < 0) return@Canvas
        val height = size.height
        strokes.forEachIndexed { index, points ->
            if (points.size < 2) return@forEachIndexed
            val path = Path()
            points.forEachIndexed { pointIndex, point ->
                val x = point.x * height
                val y = point.y * height
                if (pointIndex == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(
                path = path,
                color = RecordStrokeColors[index % RecordStrokeColors.size],
                style = Stroke(width = 6.dp.toPx()),
            )
        }
    }
}
