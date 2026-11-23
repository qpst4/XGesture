package com.slideindex.app.activity

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.slideindex.app.R

/**
 * 显式入口：快速启动器/圆环等序列化快捷方式无法携带 ClipData，
 * 统一由本 Activity 解码 FileShortcutConfig 后再启动目标。
 */
class ShortcutFileLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = intent.getStringExtra(EXTRA_FILE_CONFIG).orEmpty()
        val config = ActivityShortcutFileSupport.decodeConfig(raw)
        if (config == null) {
            Toast.makeText(this, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val uri = runCatching { Uri.parse(config.fileUri) }.getOrNull()
        if (uri == null) {
            Toast.makeText(this, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val target = if (config.isShareMode) {
            Intent(Intent.ACTION_SEND).apply {
                type = config.mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(contentResolver, "file", uri)
                if (config.isTextMode) putExtra(Intent.EXTRA_TEXT, config.fileUri)
            }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, config.mimeType.ifBlank { "*/*" })
            }
        }
        target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (config.targetPackage.isNotBlank() && config.targetActivity.isNotBlank()) {
            target.component = ComponentName(config.targetPackage, config.targetActivity)
        } else if (config.targetPackage.isNotBlank()) {
            target.setPackage(config.targetPackage)
        }
        runCatching { startActivity(target) }.onFailure {
            Toast.makeText(this, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        private const val EXTRA_FILE_CONFIG = "extra_file_config"

        fun createIntent(context: Context, config: FileShortcutConfig): Intent =
            Intent(context, ShortcutFileLaunchActivity::class.java).apply {
                putExtra(EXTRA_FILE_CONFIG, ActivityShortcutFileSupport.encodeUri(config))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
