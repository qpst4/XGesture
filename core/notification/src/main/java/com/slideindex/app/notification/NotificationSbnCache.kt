package com.slideindex.app.notification

import android.service.notification.StatusBarNotification
import android.service.notification.NotificationListenerService

/**
 * In-memory LRU cache of [StatusBarNotification] objects for replay after dismiss.
 * Mirrors the competitor approach: keep live SBN (with contentIntent) on removal
 * instead of relying solely on serialized PendingIntent on disk.
 */
object NotificationSbnCache {
    private const val ACTIVE_CAPACITY = 20
    private const val DISMISSED_CAPACITY = 10

    private val lock = Any()

    private val activeCache = object : LinkedHashMap<String, StatusBarNotification>(ACTIVE_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StatusBarNotification>?): Boolean {
            return size > ACTIVE_CAPACITY
        }
    }

    private val dismissedCache = object : LinkedHashMap<String, StatusBarNotification>(DISMISSED_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StatusBarNotification>?): Boolean {
            return size > DISMISSED_CAPACITY
        }
    }

    fun cacheActive(sbn: StatusBarNotification) {
        synchronized(lock) {
            activeCache[sbn.key] = sbn
        }
    }

    fun refreshActive(notifications: Collection<StatusBarNotification>) {
        synchronized(lock) {
            activeCache.clear()
            notifications.forEach { activeCache[it.key] = it }
        }
    }

    /** 活动缓存里 postTime 最新的一条；缓存为空时返回 null。 */
    fun latestActive(): StatusBarNotification? {
        synchronized(lock) {
            return activeCache.values.maxByOrNull { it.postTime }
        }
    }

    fun onRemoved(sbn: StatusBarNotification, reason: Int) {
        synchronized(lock) {
            if (reason != NotificationListenerService.REASON_SNOOZED) {
                val existing = dismissedCache[sbn.key]
                if (existing == null || existing.postTime != sbn.postTime) {
                    val toCache = activeCache[sbn.key] ?: sbn
                    dismissedCache[sbn.key] = toCache
                }
            }
            activeCache.remove(sbn.key)
        }
    }

    fun find(key: String, postTimeMs: Long?): StatusBarNotification? {
        synchronized(lock) {
            findExactMatch(key, postTimeMs)?.let { return it }
            return activeCache[key] ?: dismissedCache[key]
        }
    }

    private fun findExactMatch(key: String, postTimeMs: Long?): StatusBarNotification? {
        activeCache[key]?.let { if (matchesPostTime(it, postTimeMs)) return it }
        dismissedCache[key]?.let { if (matchesPostTime(it, postTimeMs)) return it }
        return null
    }

    private fun matchesPostTime(sbn: StatusBarNotification, postTimeMs: Long?): Boolean =
        matchesPostTimeForTest(sbn.postTime, postTimeMs)

    internal fun matchesPostTimeForTest(cachedPostTime: Long, lookupPostTime: Long?): Boolean {
        if (lookupPostTime == null || lookupPostTime <= 0L) return true
        if (cachedPostTime <= 0L) return true
        return cachedPostTime == lookupPostTime
    }
}
