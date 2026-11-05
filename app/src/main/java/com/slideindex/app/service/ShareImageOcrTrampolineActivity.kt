package com.slideindex.app.service

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.di.AppDependencies
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 接收别的 App 交过来的图片，弹出取词面板并在本地跑 OCR。
 *
 * 两个系统入口共用同一个 trampoline：
 * - 系统分享菜单：[Intent.ACTION_SEND] / [Intent.ACTION_SEND_MULTIPLE]；
 * - 系统「打开方式」：[Intent.ACTION_VIEW]（部分相册/文件管理器的「编辑」入口是 [Intent.ACTION_EDIT]）。
 *
 * 取词面板是 `TYPE_APPLICATION_OVERLAY`：需要无障碍服务与悬浮窗权限；OCR 还需要已安装模型。
 * 未就绪时给出提示并跳对应设置页。
 */
@AndroidEntryPoint
class ShareImageOcrTrampolineActivity : ComponentActivity() {

    @Inject
    lateinit var deps: AppDependencies

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
                if (!isFinishing) {
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
            }
        }
    }

    private suspend fun handleImageIntent(imageIntent: Intent) {
        if (!SlideIndexAccessibilityService.isConnected()) {
            Toast.makeText(this, R.string.share_image_ocr_service_required, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = ShareImageOcrCoordinator.resolveImageUri(imageIntent)
        if (uri == null) {
            Toast.makeText(this, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return
        }
        imageIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val modelId = deps.settingsRepository.readSnapshot().floatBallOcrModelId
        ShareImageOcrCoordinator.handleSharedImage(
            context = this@ShareImageOcrTrampolineActivity,
            uri = uri,
            modelId = modelId
        )
    }
}
