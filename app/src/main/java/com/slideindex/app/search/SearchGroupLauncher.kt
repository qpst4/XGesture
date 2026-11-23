package com.slideindex.app.search

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchGroupDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 搜索组的串行启动器。
 *
 * 组本身只是一个 [SearchEngineType.SEARCH_GROUP] 的 [SearchEngineConfig]；
 * 真正的成员是 [SearchEngineConfig.groupMemberIds] 指向的普通引擎。
 * 第一个成员立即启动，之后每隔 [SearchEngineConfig.groupIntervalMillis] 启动一个；
 * 新的组启动会取消上一轮，避免连点叠加。
 */
object SearchGroupLauncher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sequenceJob: Job? = null

    fun launchText(
        context: Context,
        group: SearchEngineConfig,
        text: String,
        settings: AppSettings,
        longPressTriggered: Boolean = false,
    ): Boolean {
        val members = resolveMembers(settings.searchEngines, group, image = false)
        if (members.isEmpty()) return noMembers(context)
        return launchSequence(context, group, members) { launchContext, member ->
            SearchEngineLauncher.launchText(
                launchContext,
                member,
                text,
                settings,
                longPressTriggered,
            )
        }
    }

    fun launchImage(
        context: Context,
        group: SearchEngineConfig,
        bitmap: Bitmap,
        engines: List<SearchEngineConfig>,
    ): Boolean {
        if (bitmap.isRecycled) return false
        val members = resolveMembers(engines, group, image = true)
        if (members.isEmpty()) return noMembers(context)
        // 调用方（取词面板等）可能在启动后立即 dismiss 并 recycle 原图；
        // 串行启动会持续几秒，必须用一份独立副本。
        val stableBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            ?: return false
        val launched = launchSequence(
            context = context,
            group = group,
            members = members,
            onFinished = { stableBitmap.recycle() },
        ) { launchContext, member ->
            SearchEngineLauncher.launchImageShare(launchContext, member, stableBitmap)
        }
        return launched
    }

    fun cancel() {
        sequenceJob?.cancel()
        sequenceJob = null
    }

    private fun launchSequence(
        context: Context,
        group: SearchEngineConfig,
        members: List<SearchEngineConfig>,
        onFinished: (() -> Unit)? = null,
        launchMember: (Context, SearchEngineConfig) -> Boolean,
    ): Boolean {
        cancel()
        val first = members.first()
        if (!launchMember(context, first)) {
            onFinished?.invoke()
            return false
        }
        if (members.size == 1) {
            onFinished?.invoke()
            return true
        }

        val appContext = context.applicationContext
        val interval = group.groupIntervalMillis.coerceIn(
            SearchGroupDefaults.MIN_INTERVAL_MILLIS,
            SearchGroupDefaults.MAX_INTERVAL_MILLIS,
        ).toLong()
        sequenceJob = scope.launch {
            try {
                members.drop(1).forEach { member ->
                    delay(interval)
                    launchMember(appContext, member)
                }
            } finally {
                onFinished?.invoke()
            }
        }
        return true
    }

    private fun resolveMembers(
        engines: List<SearchEngineConfig>,
        group: SearchEngineConfig,
        image: Boolean,
    ): List<SearchEngineConfig> {
        val byId = engines.associateBy { it.id }
        return group.groupMemberIds.mapNotNull { id ->
            byId[id]?.takeIf { member ->
                member.id != group.id &&
                    if (image) {
                        member.engineType == SearchEngineType.SHARE_IMAGE_TO_APP
                    } else {
                        member.engineType != SearchEngineType.SEARCH_GROUP &&
                            textLaunchKind(member.engineType) != TextLaunchKind.UNSUPPORTED
                    }
            }
        }.distinctBy { it.id }
    }

    private fun noMembers(context: Context): Boolean {
        Toast.makeText(context, R.string.search_group_no_members, Toast.LENGTH_SHORT).show()
        return false
    }
}
