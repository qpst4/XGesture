package com.slideindex.app.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object MlKitTextRecognizer {
    private var recognizer: TextRecognizer? = null

    suspend fun recognize(bitmap: Bitmap): String? {
        val client = recognizer ?: TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build(),
        ).also { recognizer = it }
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = client.process(image).await()
        return result.text
            .lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .joinToString("\n")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }

    private suspend fun <T> Task<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { value ->
                if (continuation.isActive) continuation.resume(value)
            }
            addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            addOnCanceledListener {
                continuation.cancel()
            }
        }
}
