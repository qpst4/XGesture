package com.slideindex.app.overlay.pickresult

import android.content.Context
import com.slideindex.app.R

/**
 * 面板内提示条的内容。
 *
 * [success] 决定图标：成功画勾，失败画感叹号。以前这里只有字符串，于是
 * 「操作失败」也顶着一个绿色对勾（用户实测反馈过）。
 */
internal data class PickResultPanelNotification(
    val text: String,
    val success: Boolean = true,
)

internal fun translateErrorMessage(context: Context, code: String): String = when (code) {
    "mlkit_model_not_installed" -> context.getString(R.string.float_ball_translate_error_model_missing)
    "translate_engine_not_installed" -> context.getString(R.string.float_ball_translate_error_engine_missing)
    "wifi_required" -> context.getString(R.string.float_ball_translate_error_wifi_required)
    "unsupported_language" -> context.getString(R.string.float_ball_translate_error_unsupported_language)
    "translate_unavailable" -> context.getString(R.string.float_ball_translate_error_unavailable)
    "cloud_api_key_not_configured" -> context.getString(R.string.float_ball_translate_error_cloud_not_configured)
    "cloud_translate_empty" -> context.getString(R.string.float_ball_translate_error_cloud_empty)
    "network_error", "http_403", "http_429", "http_500" ->
        context.getString(R.string.float_ball_translate_error_network)
    else -> code
}
