package com.slideindex.app.overlay.searchpanel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.finishWithoutTransition

/**
 * 手电筒（[android.hardware.camera2.CameraManager.setTorchMode]）所需相机权限的瞬发跳板。
 *
 * 已授权时什么都不做，避免把用户甩到「应用信息」页；未授权时才拉起系统运行时权限对话框。
 * 权限缺失时的提示由动作选择器自身的权限提示行负责，这里不再重复打扰。
 */
class CameraPermissionTrampolineActivity : ComponentActivity() {

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean ->
        finishWithoutTransition()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PermissionHelper.hasCameraPermission(this)) {
            finishWithoutTransition()
            return
        }
        requestPermission.launch(Manifest.permission.CAMERA)
    }

    companion object {
        /**
         * 仅在相机权限确实缺失时请求；已授权时直接返回 true，不产生任何跳转。
         */
        fun ensureGranted(context: Context): Boolean {
            if (PermissionHelper.hasCameraPermission(context)) return true
            val intent = Intent(context, CameraPermissionTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching { context.startActivity(intent) }.isSuccess
        }
    }
}
