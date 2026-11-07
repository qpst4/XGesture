package com.slideindex.app.search

/*
 * Portions derived from Circle To Search (https://github.com/AKS-Labs/CircleToSearch)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.webkit.WebSettings
import java.net.URLEncoder

object ImageSearchUrlBuilder {
    fun build(engine: ImageSearchEngine, hostedImageUrl: String): String {
        require(engine.usesHostedUrl) { "Engine $engine does not use hosted image URLs" }
        val encoded = URLEncoder.encode(hostedImageUrl, Charsets.UTF_8.name())
        return when (engine) {
            ImageSearchEngine.Google ->
                "https://lens.google.com/uploadbyurl?url=$encoded"
            ImageSearchEngine.TinEye -> "https://tineye.com/search?url=$encoded"
            ImageSearchEngine.Yandex ->
                "https://yandex.com/images/search?rpt=imageview&url=$encoded"
            ImageSearchEngine.TraceMoe -> "https://trace.moe/?url=$encoded"
            ImageSearchEngine.AnimeTrace -> "https://www.animetrace.com/?url=$encoded"
            ImageSearchEngine.Copyseeker -> "https://copyseeker.net/discovery?imageUrl=$encoded"
            ImageSearchEngine.Iqdb,
            ImageSearchEngine.SauceNao,
            ImageSearchEngine.Iqdb3D,
            ImageSearchEngine.Ascii2d,
            ->
                error("Direct POST engines do not use hosted URLs")
        }
    }

    /** System default mobile Chrome UA for in-panel WebView engines. */
    fun userAgent(context: Context, engine: ImageSearchEngine): String {
        val defaultUa = WebSettings.getDefaultUserAgent(context)
        return if (engine == ImageSearchEngine.Google) {
            // Dynamically strip out the WebView specific tags from the system's default UA.
            // By NOT having '; wv' and 'Version/X.X', we bypass Catbox's WAF block so the image thumbnail loads,
            // while remaining aligned with the actual device Chrome version.
            defaultUa
                .replace("; wv", "")
                .replace(Regex("Version/[0-9.]+\\s"), "")
        } else {
            defaultUa
        }
    }
}
