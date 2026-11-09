package com.slideindex.app.overlay.history

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.stash.StashMetaRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun formatHistoryRelativeTime(epochMs: Long): String {
    val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    return when {
        diffMs < 60_000L -> stringResource(R.string.stash_time_just_now)
        diffMs < 3_600_000L -> stringResource(R.string.stash_time_minutes_ago, (diffMs / 60_000L).toInt())
        diffMs < 86_400_000L -> stringResource(R.string.stash_time_hours_ago, (diffMs / 3_600_000L).toInt())
        else -> {
            val locale = LocalLocale.current.platformLocale
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = epochMs }
            val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
                DateFormat.getBestDateTimePattern(locale, "MMMd")
            } else {
                "yyyy/M/d"
            }
            SimpleDateFormat(pattern, locale).format(Date(epochMs))
        }
    }
}

/**
 * 提醒时间的展示：`今天 21:30` / `明天 09:00` / `昨天 21:30` / `3月5日 09:00`。
 */
@Composable
fun formatReminderTime(epochMs: Long): String {
    val locale = LocalLocale.current.platformLocale
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = epochMs }
    val time = SimpleDateFormat("HH:mm", locale).format(Date(epochMs))
    val dayLabel = when {
        sameDay(now, target) -> stringResource(R.string.stash_group_today)
        sameDay(target, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }) -> {
            stringResource(R.string.stash_group_yesterday)
        }
        sameDay(target, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }) -> {
            stringResource(R.string.stash_remind_tomorrow)
        }
        else -> {
            val pattern = DateFormat.getBestDateTimePattern(locale, "MMMd")
            SimpleDateFormat(pattern, locale).format(Date(epochMs))
        }
    }
    return "$dayLabel $time"
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/** 默认提醒时间：明天 09:00。 */
fun historyDefaultReminderAt(): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

@StringRes
fun stashSourceLabelRes(sourceKey: String?): Int? = when (sourceKey) {
    StashMetaRepository.SOURCE_CLIPBOARD -> R.string.stash_source_clipboard
    StashMetaRepository.SOURCE_PICK -> R.string.stash_source_pick
    StashMetaRepository.SOURCE_IMAGE -> R.string.stash_source_image
    else -> null
}

@StringRes
fun defaultTagLabelRes(name: String): Int? = when (name) {
    StashMetaRepository.WORK_TAG_NAME -> R.string.stash_tag_default_work
    StashMetaRepository.IDEA_TAG_NAME -> R.string.stash_tag_default_idea
    StashMetaRepository.TODO_TAG_NAME -> R.string.stash_tag_default_todo
    StashMetaRepository.INSPIRATION_TAG_NAME -> R.string.stash_tag_default_inspiration
    StashMetaRepository.DESIGN_TAG_NAME -> R.string.stash_tag_default_design
    else -> null
}

@Composable
fun stashTagLabel(name: String): String =
    defaultTagLabelRes(name)?.let { stringResource(it) } ?: name
