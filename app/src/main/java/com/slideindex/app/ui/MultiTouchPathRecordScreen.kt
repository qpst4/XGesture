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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.multitouch.PathPoint
import com.slideindex.app.gesture.multitouch.PathStroke
import kotlin.math.hypot
import top.yukonga.miuix.kmp.basic.TextButton

private val RecordStrokeColors = listOf(
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
)

/**
 * 路径手势录制页（全屏、非滚动）。
 *
 * 画板占满页面宽度，并同时在窗口层和画板层排除系统边缘手势，
 * 避免单指从屏幕左右边缘起笔时被系统返回手势抢走。
 */
@Composable
fun MultiTouchPathRecordScreen(
    onBack: () -> Unit,
    onRecordingStateChange: (Boolean) -> Unit,
    initialStrokes: List<PathStroke> = emptyList(),
    onFinish: (List<PathStroke>) -> Unit,
) {
    DisposableEffect(Unit) {
        onRecordingStateChange(true)
        onDispose { onRecordingStateChange(false) }
    }

    val editing = initialStrokes.isNotEmpty()
    val strokes = remember {
        mutableListOf<MutableList<PathPoint>>().apply {
            initialStrokes.forEach { stroke -> add(stroke.points.toMutableList()) }
        }
    }
    val strokeIndex = remember { mutableMapOf<Int, Int>() }
    var version by remember { mutableIntStateOf(0) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.multitouch_back),
                    )
                }
                Text(
                    text = if (editing) {
                        stringResource(R.string.multitouch_record_rerecord_title)
                    } else {
                        stringResource(R.string.multitouch_record_title)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (editing) {
                    stringResource(R.string.multitouch_record_rerecord_hint)
                } else {
                    stringResource(R.string.multitouch_record_hint)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.multitouch_record_guide),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                    },
            ) {
                RecordCanvas(strokes = strokes, version = version)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.multitouch_record_drawn_count, strokes.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(R.string.multitouch_record_clear),
                    onClick = {
                        strokes.clear()
                        strokeIndex.clear()
                        version++
                    },
                    minHeight = 44.dp,
                )
                TextButton(
                    text = stringResource(R.string.multitouch_done),
                    onClick = {
                        if (strokes.size >= 1) {
                            onFinish(strokes.map { points -> PathStroke(points.toList()) })
                        }
                    },
                    minHeight = 44.dp,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
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
