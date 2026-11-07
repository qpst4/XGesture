package com.slideindex.app.search

sealed class ImageSearchPostResult {
    data class Html(val baseUrl: String, val html: String) : ImageSearchPostResult()

    data class RedirectUrl(val url: String) : ImageSearchPostResult()
}
