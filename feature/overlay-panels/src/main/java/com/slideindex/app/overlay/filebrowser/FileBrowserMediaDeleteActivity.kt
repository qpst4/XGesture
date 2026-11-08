package com.slideindex.app.overlay.filebrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.util.finishWithoutTransition

/**
 * 媒体库删除需要系统确认；浮层无法直接弹 Activity Result，走透明 Activity。
 */
class FileBrowserMediaDeleteActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        deliver(result.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        if (uri == null) {
            deliver(false)
            return
        }
        runCatching {
            val request = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure {
            deliver(false)
        }
    }

    private fun deliver(success: Boolean) {
        onResult?.invoke(success)
        onResult = null
        finishWithoutTransition()
    }

    companion object {
        private const val EXTRA_URI = "extra_uri"
        private var onResult: ((Boolean) -> Unit)? = null

        fun launch(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
            this.onResult = onResult
            val intent = Intent(context, FileBrowserMediaDeleteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_URI, uri.toString())
            }
            context.startActivity(intent)
        }
    }
}
