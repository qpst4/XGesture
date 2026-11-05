package com.slideindex.app.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.search.NonExportedActivityLauncher

/**
 * 所有快捷方式启动的唯一出口。
 *
 * - 已导出 Activity：交给调用方的正常启动闭包（Trampoline / FreeWindow）。
 * - 未导出 Activity：统一走 [NonExportedActivityLauncher]，Root/Shizuku 策略由其内部选择。
 * - 不再让普通 startActivity / am start 处理未导出组件。
 */
object ShortcutLaunchDispatcher {

    enum class Failure {
        NO_PRIVILEGE,
        NOT_EXPORTED,
        START_FAILED,
    }

    fun launch(
        context: Context,
        packageName: String,
        intent: Intent,
        exportedLaunch: (Intent) -> Boolean,
        onResult: ((Boolean) -> Unit)? = null,
        onFailure: ((Failure) -> Unit)? = null,
    ): Boolean {
        val component = intent.component
        val exported = component == null ||
            PackageActivityResolver.isActivityExported(
                context,
                component.packageName,
                component.className,
            )
        if (exported) {
            val ok = runCatching { exportedLaunch(intent) }.getOrDefault(false)
            onResult?.invoke(ok)
            if (!ok) onFailure?.invoke(Failure.START_FAILED)
            return ok
        }

        if (!TaskManagerUtil.hasPermission()) {
            onResult?.invoke(false)
            onFailure?.invoke(Failure.NO_PRIVILEGE)
            return false
        }

        val accepted = NonExportedActivityLauncher.launch(context, intent) { success ->
            onResult?.invoke(success)
            if (!success) onFailure?.invoke(Failure.NOT_EXPORTED)
        }
        if (!accepted) {
            onResult?.invoke(false)
            onFailure?.invoke(Failure.NO_PRIVILEGE)
        }
        return accepted
    }

    fun showFailure(context: Context, failure: Failure) {
        val messageRes = when (failure) {
            Failure.NO_PRIVILEGE -> R.string.shortcut_port_requires_root
            Failure.NOT_EXPORTED -> if (PrivilegeGateway.isRootMode()) {
                R.string.float_ball_action_failed
            } else {
                R.string.shortcut_port_requires_root
            }
            Failure.START_FAILED -> R.string.float_ball_action_failed
        }
        Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
    }
}
