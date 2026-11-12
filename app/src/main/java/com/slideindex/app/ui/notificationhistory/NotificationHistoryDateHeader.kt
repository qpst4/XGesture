package com.slideindex.app.ui.notificationhistory

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * 时间线里的日期分组头。
 *
 * 列表只在这里显示一次日期；每行左侧只保留时分，避免每条通知都重复完整日期。
 */
@Composable
internal fun NotificationHistoryDateHeader(dayStartMs: Long) {
    val today = startOfLocalDay(System.currentTimeMillis())
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = today
        add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis
    val label = when (dayStartMs) {
        today -> stringResource(R.string.notification_history_day_today)
        yesterday -> stringResource(R.string.notification_history_day_yesterday)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dayStartMs))
    }
    Text(
        text = label,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 4.dp),
    )
}

/** 本地时区当天 00:00 的时间戳，供列表按天分组。 */
internal fun startOfLocalDay(ms: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
