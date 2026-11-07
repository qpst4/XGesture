package com.slideindex.app.overlay.carousel

import android.graphics.Bitmap
import android.graphics.Color
import androidx.collection.LruCache
import androidx.palette.graphics.Palette
import androidx.core.graphics.ColorUtils

data class AppCarouselCardColors(
    val baseCardColor: Int,
    val highlightCardColor: Int,
    val strokeColor: Int,
    val titleTextColor: Int,
)

/**
 * 调色板算法解析器：提取应用图标主色调，生成 Squircle Tile 背景与高亮发光描边。
 */
object AppCarouselPaletteResolver {
    private val colorCache = LruCache<String, AppCarouselCardColors>(128)

    fun resolveCardColors(key: String, bitmap: Bitmap): AppCarouselCardColors {
        colorCache.get(key)?.let { return it }

        val palette = try {
            Palette.from(bitmap).maximumColorCount(16).generate()
        } catch (_: Exception) {
            null
        }

        val dominantSwatch = palette?.dominantSwatch
        val vibrantSwatch = palette?.vibrantSwatch
            ?: palette?.lightVibrantSwatch
            ?: palette?.darkVibrantSwatch
            ?: dominantSwatch

        val dominantColor = dominantSwatch?.rgb ?: 0xFF2A2D32.toInt()
        val vibrantColor = vibrantSwatch?.rgb ?: dominantColor

        // Base card tile color: dominant color with dark blending
        val baseCard = ColorUtils.blendARGB(dominantColor, 0xFF181A1E.toInt(), 0.65f)
        // Highlighted card tile color: slightly brighter dominant blend
        val highlightCard = ColorUtils.blendARGB(dominantColor, 0xFF282C34.toInt(), 0.35f)
        // Glow stroke: vibrant color
        val stroke = if (vibrantColor != 0) ColorUtils.setAlphaComponent(vibrantColor, 230) else 0xCCFFFFFF.toInt()
        val titleText = vibrantSwatch?.titleTextColor ?: Color.WHITE

        val result = AppCarouselCardColors(
            baseCardColor = baseCard,
            highlightCardColor = highlightCard,
            strokeColor = stroke,
            titleTextColor = titleText,
        )
        colorCache.put(key, result)
        return result
    }

    fun clear() {
        colorCache.evictAll()
    }
}
