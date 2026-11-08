package com.slideindex.app.overlay.pickresult

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.slideindex.app.settings.SearchEngineConfig

@Composable
fun ImageShareEngineVerticalQuickSwitchColumn(
    engines: List<SearchEngineConfig>,
    hoveredIndex: Int,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(24.dp)
    val scrim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val surfaceTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    Column(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = pillShape,
                spotColor = Color.Black.copy(alpha = 0.22f),
            )
            .clip(pillShape)
            .background(surfaceTint)
            .background(scrim)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        engines.forEachIndexed { index, engine ->
            val isHovered = index == hoveredIndex
            val scale by animateFloatAsState(
                targetValue = if (isHovered) 1.18f else 1f,
                label = "editorShareEngineScale",
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isHovered) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                SearchEngineIcon(
                    engine = engine,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
