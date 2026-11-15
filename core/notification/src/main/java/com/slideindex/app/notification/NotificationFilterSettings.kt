package com.slideindex.app.notification

data class NotificationFilterSettings(
    /** 是否记录通知到滤盒；关闭后**只停止记录**，过滤规则与验证码提取仍然生效。 */
    val notificationHistoryEnabled: Boolean = true,
    val notificationHistoryMaxCount: Int = DEFAULT_NOTIFICATION_HISTORY_MAX_COUNT,
    val groupHistoryByApp: Boolean = false,
) {
    companion object {
        const val DEFAULT_NOTIFICATION_HISTORY_MAX_COUNT = 500
        const val MIN_NOTIFICATION_HISTORY_MAX_COUNT = 50
        const val MAX_NOTIFICATION_HISTORY_MAX_COUNT = 2000
    }
}
