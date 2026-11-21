package com.slideindex.app.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.slideindex.app.MainActivity

/**
 * 临时桌面桩 Activity。
 *
 * 仅用于通过系统 [android.content.pm.LauncherApps.hasShortcutHostPermission] 权限检查，
 * 绝不作为真实桌面常驻。被系统 HOME 意图拉起时，立即跳转回主界面并销毁自己。
 */
class ShortcutImportHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }
}
