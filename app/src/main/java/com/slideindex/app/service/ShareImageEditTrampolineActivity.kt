package com.slideindex.app.service

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatBallTextPick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 系统分享菜单 / 打开方式里的「XGesture 图片编辑」入口。
 *
 * 只做一件事：把交进来的图片解成位图，交给内置图片编辑器。
 * 支持 [Intent.ACTION_SEND] / [Intent.ACTION_SEND_MULTIPLE]（分享）与
 * [Intent.ACTION_VIEW]（打开方式；部分 ROM 的「编辑」入口是 [Intent.ACTION_EDIT]）。
 * 走 ImageEditorLaunchCache，不依赖无障碍浮层，所以没有连接状态的前置检查。
 */
class ShareImageEditTrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleHandleImageIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scheduleHandleImageIntent(intent)
    }

    private fun scheduleHandleImageIntent(imageIntent: Intent?) {
        val intent = imageIntent ?: run {
            finish()
            return
        }
        if (!ShareImageOcrCoordinator.isImageIntent(intent)) {
            finish()
            return
        }
        window.decorView.post {
            lifecycleScope.launch {
                handleImageIntent(intent)
                finishTransparent()
            }
        }
    }

    private suspend fun handleImageIntent(imageIntent: Intent) {
        val uri = ShareImageOcrCoordinator.resolveImageUri(imageIntent)
        if (uri == null) {
            Toast.makeText(this, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return
        }
        imageIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { ShareImageOcrCoordinator.decodeShareImage(this@ShareImageEditTrampolineActivity, uri) }
                .getOrNull()
        }
        if (bitmap == null) {
            Toast.makeText(this, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return
        }
        if (!FloatBallTextPick.openBuiltinImageEditor(this@ShareImageEditTrampolineActivity, bitmap)) {
            Toast.makeText(this, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishTransparent() {
        if (isFinishing) return
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
