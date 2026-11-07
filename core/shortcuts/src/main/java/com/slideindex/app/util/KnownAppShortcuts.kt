package com.slideindex.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.slideindex.app.shortcuts.R
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.overlay.TaskSwitcherMenuItemType

/**
 * Hard-coded desktop-equivalent shortcuts for WeChat / QQ / Alipay when ShortcutManager
 * and Shizuku queries return nothing on many ROMs.
 */
object KnownAppShortcuts {
    private const val WECHAT = "com.tencent.mm"
    private const val QQ = "com.tencent.mobileqq"
    private const val TIM = "com.tencent.tim"
    private const val ALIPAY = "com.eg.android.AlipayGphone"

    private val supportedPackages = setOf(WECHAT, QQ, TIM, ALIPAY)

    private data class ShortcutDef(
        val id: String,
        @StringRes val labelRes: Int,
        val intent: Intent,
    )

    fun supports(packageName: String): Boolean = packageName in supportedPackages

    fun packageForIntentUri(intentUri: String): String? {
        val target = runCatching {
            Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return null
        supportedPackages.forEach { packageName ->
            definitionsForPackage(packageName).forEach { def ->
                if (intentsMatchForIcon(target, def.intent)) return packageName
            }
        }
        return null
    }

    private fun intentsMatchForIcon(left: Intent, right: Intent): Boolean {
        if (left.action != right.action) return false
        if (!dataMatches(left.data, right.data)) return false
        val leftComponent = left.component
        val rightComponent = right.component
        if (leftComponent != null || rightComponent != null) {
            return leftComponent == rightComponent
        }
        return true
    }

    private fun dataMatches(left: Uri?, right: Uri?): Boolean {
        if (left == null && right == null) return true
        if (left == null || right == null) return false
        return left.toString() == right.toString()
    }

    fun load(context: Context, packageName: String): List<TaskSwitcherMenuItem> =
        definitionsForPackage(packageName).map { def ->
            TaskSwitcherMenuItem(
                label = context.getString(def.labelRes),
                type = TaskSwitcherMenuItemType.SHORTCUT,
                shortcutId = def.id,
                shortcutIntent = def.intent,
            )
        }

    private fun definitionsForPackage(packageName: String): List<ShortcutDef> = when (packageName) {
        WECHAT -> weChatDefinitions()
        QQ, TIM -> qqDefinitions(packageName)
        ALIPAY -> alipayDefinitions()
        else -> emptyList()
    }

    private fun weChatDefinitions(): List<ShortcutDef> {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return listOf(
            ShortcutDef(
                id = "wechat_scan",
                labelRes = R.string.shortcut_scan,
                intent = Intent(Intent.ACTION_VIEW).apply {
                    component = ComponentName(WECHAT, "com.tencent.mm.ui.LauncherUI")
                    putExtra("LauncherUI.From.Scaner.Shortcut", true)
                    this.flags = flags
                },
            ),
            ShortcutDef(
                id = "wechat_my_qrcode",
                labelRes = R.string.shortcut_my_qr,
                intent = Intent(Intent.ACTION_VIEW, "weixin://dl/myQRcode".toUri()).apply {
                    setPackage(WECHAT)
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            ),
            ShortcutDef(
                id = "wechat_pay",
                labelRes = R.string.shortcut_receive_pay,
                intent = Intent(Intent.ACTION_VIEW).apply {
                    component = ComponentName(WECHAT, "com.tencent.mm.plugin.offline.ui.WalletOfflineCoinPurseUI")
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            ),
        )
    }

    private fun qqDefinitions(packageName: String): List<ShortcutDef> {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return listOf(
            ShortcutDef(
                id = "qq_scan",
                labelRes = R.string.shortcut_scan,
                intent = Intent(
                    Intent.ACTION_VIEW,
                    "mqqapi://qrcode/scan_qrcode?version=1&src_type=app".toUri(),
                ).apply {
                    setPackage(packageName)
                    this.flags = flags
                },
            ),
            ShortcutDef(
                id = "qq_my_qrcode",
                labelRes = R.string.shortcut_my_qr,
                intent = Intent(
                    Intent.ACTION_VIEW,
                    "mqqapi://qrcode/showcard?version=1&src_type=internal".toUri(),
                ).apply {
                    setPackage(packageName)
                    this.flags = flags
                },
            ),
            ShortcutDef(
                id = "qq_pay",
                labelRes = R.string.shortcut_receive_pay,
                intent = Intent(
                    Intent.ACTION_VIEW,
                    "mqqapi://wallet/pay?version=1&src_type=app".toUri(),
                ).apply {
                    setPackage(packageName)
                    this.flags = flags
                },
            ),
        )
    }

    private fun alipayDefinitions(): List<ShortcutDef> {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return listOf(
            ShortcutDef("alipay_cainiao", R.string.shortcut_cainiao, viewUri("alipays://platformapi/startapp?saId=2021001141626787", flags)),
            ShortcutDef("alipay_forest", R.string.shortcut_ant_forest, viewUri("alipays://platformapi/startapp?appId=60000002", flags)),
            ShortcutDef("alipay_receive", R.string.shortcut_receive_code, viewUri("alipayqr://platformapi/startapp?saId=20000123", flags)),
            ShortcutDef("alipay_pay", R.string.shortcut_pay_code, viewUri("alipayqr://platformapi/startapp?saId=20000056", flags)),
            ShortcutDef("alipay_bus", R.string.shortcut_bus_code, viewUri("alipayqr://platformapi/startapp?saId=200011235", flags)),
            ShortcutDef("alipay_scan", R.string.shortcut_scan, viewUri("alipayqr://platformapi/startapp?saId=10000007", flags)),
        )
    }

    private fun viewUri(uri: String, flags: Int): Intent {
        return Intent(Intent.ACTION_VIEW, uri.toUri()).apply { this.flags = flags }
    }
}
