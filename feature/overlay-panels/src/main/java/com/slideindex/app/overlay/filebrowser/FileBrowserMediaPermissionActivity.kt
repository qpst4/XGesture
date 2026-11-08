package com.slideindex.app.overlay.filebrowser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.util.finishWithoutTransition

/**
 * 浮层不能直接弹运行时权限，走一个透明 Activity 申请 MediaStore 所需权限。
 * 只负责“申请完回调”，是否授权由调用方重新查询时决定。
 */
class FileBrowserMediaPermissionActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        deliver()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissions = intent.getStringArrayExtra(EXTRA_PERMISSIONS)
            ?.toList()
            .orEmpty()
        if (permissions.isEmpty()) {
            deliver()
            return
        }
        launcher.launch(permissions.toTypedArray())
    }

    private fun deliver() {
        onResult?.invoke()
        onResult = null
        finishWithoutTransition()
    }

    companion object {
        private const val EXTRA_PERMISSIONS = "extra_permissions"
        private var onResult: (() -> Unit)? = null

        fun launch(context: Context, permissions: List<String>, onResult: () -> Unit) {
            this.onResult = onResult
            val intent = Intent(context, FileBrowserMediaPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_PERMISSIONS, permissions.toTypedArray())
            }
            context.startActivity(intent)
        }
    }
}
