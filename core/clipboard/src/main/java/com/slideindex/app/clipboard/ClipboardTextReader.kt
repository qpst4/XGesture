package com.slideindex.app.clipboard

import android.content.Context

object ClipboardTextReader {
    fun read(context: Context): String? = ClipboardReader.read(context)?.text
}
