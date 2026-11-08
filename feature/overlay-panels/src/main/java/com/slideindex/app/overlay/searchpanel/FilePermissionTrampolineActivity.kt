package com.slideindex.app.overlay.searchpanel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.search.files.FileSearchIndex
import com.slideindex.app.util.finishWithoutTransition

class FilePermissionTrampolineActivity : ComponentActivity() {

    private val manageAllFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        deliverResult(FileSearchIndex.hasPermission(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FileSearchIndex.hasPermission(this)) {
            deliverResult(true)
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:$packageName".toUri(),
        )
        runCatching {
            manageAllFilesLauncher.launch(intent)
        }.onFailure {
            manageAllFilesLauncher.launch(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            )
        }
    }

    private fun deliverResult(granted: Boolean) {
        onPermissionResult?.invoke(granted)
        onPermissionResult = null
        finishWithoutTransition()
    }

    companion object {
        var onPermissionResult: ((Boolean) -> Unit)? = null

        fun launch(context: Context, onResult: (Boolean) -> Unit) {
            onPermissionResult = onResult
            val intent = Intent(context, FilePermissionTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
