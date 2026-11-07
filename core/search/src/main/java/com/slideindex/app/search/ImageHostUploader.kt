package com.slideindex.app.search

/*
 * Portions derived from Circle To Search (https://github.com/AKS-Labs/CircleToSearch)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object ImageHostUploader {
    private const val TAG = "ImageHostUploader"
    private const val USER_AGENT = "SlideIndex-ImageSearch/1.0"
    private const val TIMEOUT_MS = 30_000
    private const val MAX_UPLOAD_LENGTH = 2048

    suspend fun upload(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val uploadBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        try {
            val imageBytes = compressToJpeg(uploadBitmap)
            uploadBytesToLitterbox(imageBytes)
                ?: uploadBytesToCatbox(imageBytes)
        } finally {
            uploadBitmap.recycle()
        }
    }

    private fun compressToJpeg(bitmap: Bitmap): ByteArray {
        val resized = ImageSearchBitmapUtils.resizeForUpload(bitmap, maxLength = MAX_UPLOAD_LENGTH)
        val shouldRecycleResized = resized !== bitmap
        return try {
            ByteArrayOutputStream().apply {
                resized.compress(Bitmap.CompressFormat.JPEG, 90, this)
            }.toByteArray()
        } finally {
            if (shouldRecycleResized) {
                resized.recycle()
            }
        }
    }

    private fun uploadBytesToLitterbox(imageBytes: ByteArray): String? {
        val startedAt = System.currentTimeMillis()
        return try {
            val boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "")
            val connection = (
                URL("https://litterbox.catbox.moe/resources/internals/api.php")
                    .openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            DataOutputStream(connection.outputStream).use { dos ->
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n")
                dos.writeBytes("fileupload\r\n")
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"time\"\r\n\r\n")
                dos.writeBytes("1h\r\n")
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"image.jpg\"\r\n")
                dos.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                dos.write(imageBytes)
                dos.writeBytes("\r\n")
                dos.writeBytes("--$boundary--\r\n")
                dos.flush()
            }
            if (connection.responseCode == 200) {
                val url = connection.inputStream.bufferedReader().use { it.readText().trim() }
                Log.d(TAG, "Litterbox upload ok in ${System.currentTimeMillis() - startedAt}ms: $url")
                url.takeIf { it.isNotBlank() }
            } else {
                Log.w(TAG, "Litterbox upload failed: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Litterbox upload error", e)
            null
        }
    }

    private fun uploadBytesToCatbox(imageBytes: ByteArray): String? {
        val startedAt = System.currentTimeMillis()
        return try {
            val boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "")
            val connection = (URL("https://catbox.moe/user/api.php").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            DataOutputStream(connection.outputStream).use { dos ->
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n")
                dos.writeBytes("fileupload\r\n")
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"image.jpg\"\r\n")
                dos.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                dos.write(imageBytes)
                dos.writeBytes("\r\n")
                dos.writeBytes("--$boundary--\r\n")
                dos.flush()
            }
            if (connection.responseCode == 200) {
                val url = connection.inputStream.bufferedReader().use { it.readText().trim() }
                Log.d(TAG, "Catbox upload ok in ${System.currentTimeMillis() - startedAt}ms: $url")
                url.takeIf { it.isNotBlank() }
            } else {
                Log.w(TAG, "Catbox upload failed: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Catbox upload error", e)
            null
        }
    }
}
