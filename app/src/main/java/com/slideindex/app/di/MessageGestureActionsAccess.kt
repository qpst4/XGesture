package com.slideindex.app.di

import android.content.Context
import com.slideindex.app.message.MessageGestureActions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MessageGestureActionsEntryPoint {
    fun messageGestureActions(): MessageGestureActions
}

/**
 * 手势侧取 [MessageGestureActions] 的窄入口。
 *
 * `ActionExecutor` 是手工 new 出来的（不走 Hilt），这里用 EntryPoint 补依赖；
 * 实例在进程内缓存一次，避免每次手势都解析一遍 EntryPoint。
 */
object MessageGestureActionsAccess {
    @Volatile
    private var cached: MessageGestureActions? = null

    fun get(context: Context): MessageGestureActions? {
        cached?.let { return it }
        return runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                MessageGestureActionsEntryPoint::class.java,
            ).messageGestureActions()
        }.getOrNull()?.also { cached = it }
    }
}
