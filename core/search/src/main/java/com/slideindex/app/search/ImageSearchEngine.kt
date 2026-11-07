package com.slideindex.app.search

/*
 * Portions derived from Circle To Search (https://github.com/AKS-Labs/CircleToSearch)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

enum class ImageSearchEngine(
    val displayName: String,
    val usesHostedUrl: Boolean = false,
    val usesDirectPost: Boolean = false,
    val postUploadUrl: String? = null,
    val webViewBaseUrl: String? = null,
    val externalPageUrl: String? = null
) {
    Google(
        displayName = "Google",
        usesHostedUrl = true,
        externalPageUrl = "https://lens.google.com/"
    ),
    Yandex(
        displayName = "Yandex",
        usesHostedUrl = true,
        externalPageUrl = "https://yandex.com/images/"
    ),
    TinEye(
        displayName = "TinEye",
        usesHostedUrl = true,
        externalPageUrl = "https://tineye.com/"
    ),
    Iqdb(
        displayName = "IQDB",
        usesDirectPost = true,
        postUploadUrl = "https://iqdb.org/",
        webViewBaseUrl = "https://iqdb.org/",
        externalPageUrl = "https://iqdb.org/"
    ),
    SauceNao(
        displayName = "SauceNAO",
        usesDirectPost = true,
        postUploadUrl = "https://saucenao.com/search.php",
        webViewBaseUrl = "https://saucenao.com/",
        externalPageUrl = "https://saucenao.com/"
    ),
    Iqdb3D(
        displayName = "3D-IQDB",
        usesDirectPost = true,
        postUploadUrl = "https://3d.iqdb.org/",
        webViewBaseUrl = "https://3d.iqdb.org/",
        externalPageUrl = "https://3d.iqdb.org/"
    ),
    Ascii2d(
        displayName = "ASCII2D",
        usesDirectPost = true,
        postUploadUrl = "https://ascii2d.net/search/file",
        webViewBaseUrl = "https://ascii2d.net/",
        externalPageUrl = "https://ascii2d.net/"
    ),
    TraceMoe(
        displayName = "trace.moe",
        usesHostedUrl = true,
        externalPageUrl = "https://trace.moe/"
    ),
    AnimeTrace(
        displayName = "AnimeTrace",
        usesHostedUrl = true,
        externalPageUrl = "https://www.animetrace.com/"
    ),
    Copyseeker(
        displayName = "Copyseeker",
        usesHostedUrl = true,
        externalPageUrl = "https://copyseeker.net/"
    ),
    ;

    val faviconSourceUrl: String?
        get() = externalPageUrl ?: webViewBaseUrl ?: postUploadUrl

    companion object {
        val hostedUrlEngines: List<ImageSearchEngine> = entries.filter { it.usesHostedUrl }
        val directPostEngines: List<ImageSearchEngine> = entries.filter { it.usesDirectPost }
    }
}
