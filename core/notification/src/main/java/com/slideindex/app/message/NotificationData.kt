package com.slideindex.app.message

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import com.slideindex.app.util.BundleParcelCompat
import com.slideindex.app.notification.NotificationRemoteReply

data class NotificationData(
    val packageName: String,
    val key: String,
    val title: String,
    /** 群聊/频道名称，来自 [Notification.EXTRA_CONVERSATION_TITLE]。 */
    val conversationTitle: String = "",
    val content: String,
    val largeIcon: Bitmap?,
    val appIcon: Bitmap?,
    val contentIntent: PendingIntent?,
    val postTime: Long,
    val hasDirectReply: Boolean = false,
    val hasMarkAsRead: Boolean = false,
    val conversationSourceKey: String = "",
    val messages: List<NotificationMessage> = emptyList(),
    /** 列表悬浮球使用的会话/群头像。 */
    val conversationIcon: Bitmap? = null,
    val badgeCount: Int = 0,
    val textLineCount: Int = 0,
    /** 群聊发言人（来自 MessagingStyle，单聊为空）。 */
    val senderName: String = "",
) {
    /** 弹幕仅展示会话最新一条消息，避免 TG 等多条历史被重复滚动。 */
    fun latestMessageForDanmaku(): NotificationData {
        val latest = messages.lastOrNull() ?: return this
        return copy(
            content = latest.text,
            largeIcon = latest.senderIcon ?: largeIcon,
        )
    }

    companion object {
        private const val ICON_SIZE_PX = 144
        private const val BADGE_SIZE_PX = 48

        fun fromSbn(context: Context, sbn: StatusBarNotification): NotificationData? {
            val notification = sbn.notification ?: return null
            val extras = notification.extras ?: return null
            val text = NotificationTextExtractor.extract(extras)
            if (text.title.isBlank() && text.text.isBlank()) return null

            val appContext = context.applicationContext
            val appIcon = loadAppIcon(appContext, sbn.packageName)
            val isGroup = isGroupConversation(extras, text.title)
            val messagingUserIcon = extractMessagingUserIcon(appContext, extras, sbn.packageName)
            val senderIcon = extractMessagingStyleIcon(appContext, notification, extras, sbn.packageName)
            val sessionIcon = if (isGroup) {
                extractGroupConversationIcon(
                    context = appContext,
                    notification = notification,
                    extras = extras,
                    packageName = sbn.packageName,
                    shortcutIcon = null,
                    messagingUserIcon = messagingUserIcon,
                )
            } else {
                extractDirectConversationIcon(
                    context = appContext,
                    notification = notification,
                    extras = extras,
                    packageName = sbn.packageName,
                    shortcutIcon = null,
                    messagingUserIcon = messagingUserIcon,
                )
            }
            val largeIcon = senderIcon ?: sessionIcon ?: appIcon
            val listIcon = sessionIcon ?: messagingUserIcon
            val textLineCount = extras.getCharSequenceArray("android.textLines")
                ?.count { !it.isNullOrBlank() }
                ?: 0

            val conversationTitle = extras
                .getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?.toString()
                ?.trim()
                ?: extras
                    .getCharSequence("android.hiddenConversationTitle")
                    ?.toString()
                    ?.trim()
                    ?: ""

            return NotificationData(
                packageName = sbn.packageName,
                key = sbn.key,
                title = text.title,
                conversationTitle = conversationTitle,
                content = text.text,
                largeIcon = largeIcon,
                appIcon = when {
                    listIcon == null -> appIcon
                    listIcon != appIcon -> appIcon
                    else -> null
                },
                contentIntent = notification.contentIntent,
                postTime = sbn.postTime.coerceAtLeast(0L),
                hasDirectReply = NotificationRemoteReply.hasReplyAction(notification),
                hasMarkAsRead = NotificationRemoteReply.hasMarkAsReadAction(notification),
                conversationSourceKey = conversationSourceKey(sbn.packageName, text.title, extras),
                messages = NotificationMessagingHistory.extractMessages(appContext, extras),
                conversationIcon = listIcon,
                badgeCount = notification.number.coerceAtLeast(0),
                textLineCount = textLineCount,
                senderName = NotificationMessagingHistory.extractMessages(appContext, extras)
                    .lastOrNull()?.sender?.trim().orEmpty(),
            )
        }

        internal fun extractMessageSenderIcon(
            context: Context,
            message: Bundle,
            packageName: String = "",
        ): Bitmap? = extractPersonIcon(context, message, packageName)

        fun normalizeConversationTitle(title: String): String =
            title.trim()
                .replace(Regex("\\(\\d+条新消息\\)$"), "")
                .trim()

        /** C Notice 详情顶栏：优先会话名，避免 TG 等把发言人写进 [Notification.EXTRA_TITLE]。 */
        fun overlayHeaderTitle(data: NotificationData): String {
            val fromConversation = normalizeConversationTitle(data.conversationTitle)
            if (fromConversation.isNotBlank()) return fromConversation
            val normalizedTitle = normalizeConversationTitle(data.title)
            return stripGroupSenderSuffix(normalizedTitle).ifBlank { data.packageName }
        }

        /** 群聊发言人；与标题相同（单聊、或标题已含发送者）时返回空，避免重复显示。 */
        fun overlaySenderName(data: NotificationData): String {
            val sender = data.senderName.trim()
            if (sender.isEmpty()) return ""
            val header = overlayHeaderTitle(data)
            return if (sender.equals(header, ignoreCase = true)) "" else sender
        }

        private fun stripGroupSenderSuffix(title: String): String {
            val colonIndex = title.indexOf(':')
            if (colonIndex <= 0 || colonIndex >= title.lastIndex) return title
            val groupPart = title.substring(0, colonIndex).trim()
            val senderPart = title.substring(colonIndex + 1).trim()
            if (groupPart.isBlank() || senderPart.isBlank() || senderPart.contains(':')) return title
            return groupPart
        }

        /**
         * 贴边列表聚合用稳定会话键：忽略 group/dm 前缀抖动（QQ 同群会在两种前缀间切换）。
         */
        fun conversationIdentityKey(data: NotificationData): String =
            conversationIdentityKey(data.packageName, data.title, data.conversationTitle)

        fun conversationIdentityKey(
            packageName: String,
            title: String,
            conversationTitle: String = "",
        ): String {
            val normalizedConversation = normalizeConversationTitle(conversationTitle)
            val normalizedTitle = normalizeConversationTitle(title)
            val displayName = normalizedConversation.ifBlank { normalizedTitle }.ifBlank { packageName }
            return "$packageName:$displayName"
        }

        fun isGroupConversation(data: NotificationData): Boolean =
            data.conversationSourceKey.startsWith("group:") ||
                data.conversationTitle.isNotBlank() ||
                data.textLineCount > 1 ||
                data.messages.size > 1

        /** 群聊历史合并时勿把会话级头像写入每条消息的 senderIcon。 */
        fun storedMessageSenderIcon(data: NotificationData): Bitmap? =
            if (isGroupConversation(data)) null else data.largeIcon

        fun isSessionLevelIcon(icon: Bitmap, data: NotificationData): Boolean {
            val conversation = data.conversationIcon
            if (conversation != null) {
                if (conversation === icon) return true
                if (
                    conversation.width == icon.width &&
                    conversation.height == icon.height &&
                    conversation.sameAs(icon)
                ) {
                    return true
                }
            }
            // TG 等群聊 largeIcon 是最新发言人头像，与会话图标不同；仅当 largeIcon 本身就是会话图时才过滤。
            val large = data.largeIcon ?: return false
            val largeMatchesConversation = conversation == null ||
                large === conversation ||
                (
                    large.width == conversation.width &&
                        large.height == conversation.height &&
                        large.sameAs(conversation)
                    )
            if (!largeMatchesConversation) return false
            if (large === icon) return true
            return large.width == icon.width && large.height == icon.height && large.sameAs(icon)
        }

        /** 群聊里 QQ 等常把群头像塞进 senderIcon，需与会话级图标区分后才可当每人头像。 */
        fun isDistinctMessageSenderIcon(senderIcon: Bitmap?, data: NotificationData): Boolean {
            if (senderIcon == null) return false
            if (!isGroupConversation(data)) return true
            return !isSessionLevelIcon(senderIcon, data)
        }

        fun resolveConversationSourceKey(
            packageName: String,
            title: String,
            extras: Bundle,
        ): String = conversationSourceKey(packageName, title, extras)

        internal fun conversationSourceKey(
            packageName: String,
            title: String,
            extras: Bundle,
        ): String {
            val normalizedTitle = normalizeConversationTitle(title)
            val conversationTitle = extras
                .getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?.toString()
                ?.trim()
                .orEmpty()
            val hiddenConversationTitle = extras
                .getCharSequence("android.hiddenConversationTitle")
                ?.toString()
                ?.trim()
                .orEmpty()
            val resolvedConversationTitle = conversationTitle.ifBlank { hiddenConversationTitle }
            return if (isGroupConversation(extras, title)) {
                val groupName = resolvedConversationTitle.ifBlank { normalizedTitle }
                "group:$packageName:$groupName"
            } else if (hasMultipleMessagingEntries(extras) && resolvedConversationTitle.isNotBlank()) {
                "group:$packageName:$resolvedConversationTitle"
            } else {
                val contactName = resolvedConversationTitle.ifBlank { normalizedTitle }
                "dm:$packageName:$contactName"
            }
        }

        private fun extractGroupConversationIcon(
            context: Context,
            notification: Notification,
            extras: Bundle,
            packageName: String,
            shortcutIcon: Bitmap?,
            messagingUserIcon: Bitmap?,
        ): Bitmap? {
            extractConversationIconExtra(context, extras, packageName)?.let { return it }
            if (packageName == "com.tencent.mobileqq") {
                loadLargeIcon(context, notification, extras, packageName)?.let { return it }
            }
            messagingUserIcon?.let { return it }
            shortcutIcon?.let { return it }
            return null
        }

        private fun extractDirectConversationIcon(
            context: Context,
            notification: Notification,
            extras: Bundle,
            packageName: String,
            shortcutIcon: Bitmap?,
            messagingUserIcon: Bitmap?,
        ): Bitmap? {
            loadLargeIcon(context, notification, extras, packageName)?.let { return it }
            messagingUserIcon?.let { return it }
            shortcutIcon?.let { return it }
            extractMessagingStyleIcon(context, notification, extras, packageName)?.let { return it }
            extractConversationIconExtra(context, extras, packageName)?.let { return it }
            extractAnyIconFromExtras(context, extras, packageName)?.let { return it }
            return null
        }

        private fun extractMessagingUserIcon(
            context: Context,
            extras: Bundle,
            packageName: String,
        ): Bitmap? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                readPersonExtra(extras, "android.messagingUser")
                    ?.let { person ->
                        loadNotificationIcon(context, person.icon, packageName)?.let { return it }
                    }
            }
            val styleUser = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getBundle("android.messagingStyleUser")
            } else {
                @Suppress("DEPRECATION")
                extras.getBundle("android.messagingStyleUser")
            }
            if (styleUser != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    readPersonExtra(styleUser, "person")
                        ?.let { person ->
                            loadNotificationIcon(context, person.icon, packageName)?.let { return it }
                        }
                    readPersonExtra(styleUser, "user")
                        ?.let { person ->
                            loadNotificationIcon(context, person.icon, packageName)?.let { return it }
                        }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    loadNotificationIcon(context, readIconExtra(styleUser, "icon"), packageName)
                        ?.let { return it }
                }
            }
            return null
        }

        fun shortcutUserId(user: UserHandle): Int = userIdFrom(user)

        private fun userIdFrom(user: UserHandle): Int {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return 0
            return runCatching {
                UserHandle::class.java.getMethod("getIdentifier").invoke(user) as Int
            }.getOrDefault(0)
        }

        internal fun isGroupConversation(extras: Bundle, title: String = ""): Boolean {
            if (extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)) {
                return extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
            }
            val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            if (!conversationTitle.isNullOrBlank()) return true
            return title.trim().matches(Regex(".*\\(\\d+条新消息\\)$"))
        }

        private fun hasMultipleMessagingEntries(extras: Bundle): Boolean {
            val bundleCount = BundleParcelCompat
                .getParcelableArrayOfBundles(extras, Notification.EXTRA_MESSAGES)
                ?.size
                ?: BundleParcelCompat
                    .getParcelableArrayOfBundles(extras, "android.messages")
                    ?.size
                ?: 0
            if (bundleCount > 1) return true
            val textLineCount = extras.getCharSequenceArray("android.textLines")
                ?.count { !it.isNullOrBlank() }
                ?: 0
            return textLineCount > 1
        }

        private fun loadLargeIcon(
            context: Context,
            notification: Notification,
            extras: Bundle,
            packageName: String,
        ): Bitmap? {
            loadNotificationIcon(context, notification.getLargeIcon(), packageName)?.let { return it }
            extractBitmapFromExtras(context, extras, packageName)?.let { return it }
            legacyLargeIcon(notification)?.let { bitmap ->
                if (!bitmap.isBlank()) return scaleIcon(bitmap)
            }
            return null
        }

        private fun extractAnyIconFromExtras(
            context: Context,
            extras: Bundle,
            packageName: String,
        ): Bitmap? {
            for (key in extras.keySet()) {
                if (key.contains("picture", ignoreCase = true)) continue
                when (val value = BundleParcelCompat.getValue(extras, key)) {
                    is Bitmap -> if (!value.isBlank()) return scaleIcon(value)
                    is Icon -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        loadNotificationIcon(context, value, packageName)?.let { return it }
                    }
                }
            }
            return null
        }

        private fun extractBitmapFromExtras(
            context: Context,
            extras: Bundle,
            packageName: String,
        ): Bitmap? {
            readBitmapExtra(extras, "android.largeIcon")?.let { return scaleIcon(it) }
            readBitmapExtra(extras, "android.largeIcon.big")?.let { return scaleIcon(it) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                loadNotificationIcon(context, readIconExtra(extras, "android.largeIcon"), packageName)
                    ?.let { return it }
                loadNotificationIcon(context, readIconExtra(extras, "android.largeIcon.big"), packageName)
                    ?.let { return it }
            }
            return null
        }

        private fun extractMessagingStyleIcon(
            context: Context,
            notification: Notification,
            extras: Bundle,
            packageName: String,
        ): Bitmap? {
            val messages = BundleParcelCompat.getParcelableArrayOfBundles(extras, Notification.EXTRA_MESSAGES)
                ?: return null
            for (index in messages.indices.reversed()) {
                val message = messages[index]
                extractPersonIcon(context, message, packageName)?.let { return it }
            }
            return null
        }

        private fun extractPersonIcon(
            context: Context,
            message: Bundle,
            packageName: String,
        ): Bitmap? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                readPersonExtra(message, "sender_person")
                    ?.let { person ->
                        loadNotificationIcon(context, person.icon, packageName)?.let { return it }
                    }
                readPersonExtra(message, Notification.EXTRA_MESSAGING_PERSON)
                    ?.let { person ->
                        loadNotificationIcon(context, person.icon, packageName)?.let { return it }
                    }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                loadNotificationIcon(context, readIconExtra(message, "sender_icon"), packageName)
                    ?.let { return it }
            }
            readBitmapExtra(message, "sender_avatar")?.let { return scaleIcon(it) }
            return null
        }

        private fun extractConversationIconExtra(
            context: Context,
            extras: Bundle,
            packageName: String,
        ): Bitmap? {
            return loadNotificationIcon(context, readIconExtra(extras, "android.conversationIcon"), packageName)
        }

        private fun readBitmapExtra(bundle: Bundle, key: String): Bitmap? {
            val value = BundleParcelCompat.getValue(bundle, key) ?: return null
            return when (value) {
                is Bitmap -> value
                else -> null
            }
        }

        private fun readIconExtra(bundle: Bundle, key: String): Icon? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable(key, Icon::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getParcelable(key)
            }
        }

        private fun readPersonExtra(bundle: Bundle, key: String): Person? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable(key, Person::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getParcelable(key)
            }
        }

        private fun loadNotificationIcon(
            context: Context,
            icon: Icon?,
            packageName: String? = null,
        ): Bitmap? {
            if (icon == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
            val drawable = runCatching { icon.loadDrawable(context) }.getOrNull()
                ?: packageName?.let { pkg ->
                    runCatching {
                        icon.loadDrawable(context.createPackageContext(pkg, 0))
                    }.getOrNull()
                }
                ?: return null
            val bitmap = drawableToBitmap(drawable)
            return if (bitmap.isBlank()) null else scaleIcon(bitmap)
        }

        @Suppress("DEPRECATION")
        private fun legacyLargeIcon(notification: Notification): Bitmap? =
            notification.largeIcon

        private fun loadAppIcon(context: Context, packageName: String): Bitmap? {
            return runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                scaleIcon(drawableToBitmap(drawable), BADGE_SIZE_PX)
            }.getOrNull()
        }

        private fun scaleIcon(source: Bitmap, sizePx: Int = ICON_SIZE_PX): Bitmap {
            if (source.width == sizePx && source.height == sizePx) return source
            return Bitmap.createScaledBitmap(source, sizePx, sizePx, true)
        }

        private fun drawableToBitmap(drawable: Drawable): Bitmap {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap
            }
            val width = drawable.intrinsicWidth.coerceIn(1, ICON_SIZE_PX)
            val height = drawable.intrinsicHeight.coerceIn(1, ICON_SIZE_PX)
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
            }
        }

        private fun Bitmap.isBlank(): Boolean {
            if (width <= 1 || height <= 1) return true
            val sample = getPixel(width / 2, height / 2)
            val alpha = sample ushr 24
            return alpha < 16
        }
    }
}
