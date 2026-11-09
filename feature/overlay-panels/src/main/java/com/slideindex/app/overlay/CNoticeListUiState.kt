package com.slideindex.app.overlay

import android.graphics.Bitmap
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.NotificationData
import com.slideindex.app.message.NotificationMessage

enum class CNoticeListPhase {
    Collapsed,
    CollapsedDimmed,
    CollapsedGhost,
    ExpandedActive,
    ExpandedDimmed,
    ExpandedGhost,
}

object CNoticeListUiState {
    fun isCollapsedFamily(phase: CNoticeListPhase): Boolean =
        when (phase) {
            CNoticeListPhase.Collapsed,
            CNoticeListPhase.CollapsedDimmed,
            CNoticeListPhase.CollapsedGhost,
            -> true
            else -> false
        }

    fun toggleCollapse(phase: CNoticeListPhase): CNoticeListPhase =
        if (isCollapsedFamily(phase)) {
            CNoticeListPhase.ExpandedActive
        } else {
            CNoticeListPhase.Collapsed
        }

    fun onOutsideTouch(phase: CNoticeListPhase): CNoticeListPhase =
        when (phase) {
            CNoticeListPhase.ExpandedActive -> CNoticeListPhase.ExpandedDimmed
            CNoticeListPhase.Collapsed -> CNoticeListPhase.CollapsedDimmed
            else -> phase
        }

    fun onListInteraction(phase: CNoticeListPhase): CNoticeListPhase =
        when (phase) {
            CNoticeListPhase.ExpandedDimmed,
            CNoticeListPhase.ExpandedGhost,
            -> CNoticeListPhase.ExpandedActive
            CNoticeListPhase.CollapsedDimmed,
            CNoticeListPhase.CollapsedGhost,
            -> CNoticeListPhase.Collapsed
            else -> phase
        }

    fun onGhostTimeout(phase: CNoticeListPhase): CNoticeListPhase =
        when (phase) {
            CNoticeListPhase.ExpandedDimmed -> CNoticeListPhase.ExpandedGhost
            CNoticeListPhase.CollapsedDimmed -> CNoticeListPhase.CollapsedGhost
            else -> phase
        }

    fun listAlpha(phase: CNoticeListPhase, settings: MessageSettings): Float {
        val base = settings.cNoticeOpacity.coerceIn(0f, 1f)
        return when (phase) {
            CNoticeListPhase.Collapsed,
            CNoticeListPhase.ExpandedActive,
            -> base
            CNoticeListPhase.CollapsedDimmed,
            CNoticeListPhase.ExpandedDimmed,
            -> settings.cNoticeDimmedOpacity.coerceIn(0f, 1f)
            CNoticeListPhase.CollapsedGhost,
            CNoticeListPhase.ExpandedGhost,
            -> settings.cNoticeGhostOpacity.coerceIn(0f, 1f)
        }
    }

    fun handleVisible(phase: CNoticeListPhase): Boolean =
        phase != CNoticeListPhase.ExpandedGhost && phase != CNoticeListPhase.CollapsedGhost

    fun handleAlpha(phase: CNoticeListPhase): Float =
        if (handleVisible(phase)) 1f else 0f

    fun formatPeekBannerText(
        data: NotificationData,
        message: NotificationMessage?,
    ): String {
        val fallback = data.content.trim()
        val latest = message
        val text = latest?.text?.trim()?.takeIf { it.isNotBlank() } ?: fallback
        if (text.isBlank()) return ""
        val isGroup = NotificationData.isGroupConversation(data)
        val title = NotificationData.overlayHeaderTitle(data)
        val sender = latest?.sender?.trim()?.takeIf { it.isNotEmpty() }
        val raw = when {
            isGroup && sender != null && !textHasSenderPrefix(text) -> "$sender: $text"
            else -> text
        }
        return resolveSideBubbleContent(title, raw).ifBlank { raw }
    }

    private fun textHasSenderPrefix(text: String): Boolean {
        val colonIndex = text.indexOf(':')
        return colonIndex in 1 until text.lastIndex
    }

    /** 群聊消息行已单独展示 sender 时，正文去掉重复的「发送者:」前缀。 */
    fun formatMessageRowBody(
        message: NotificationMessage,
        data: NotificationData,
    ): String {
        val text = message.text.trim()
        if (text.isBlank()) return text
        val sender = message.sender?.trim()?.takeIf { it.isNotEmpty() }
            ?: return text
        if (!NotificationData.isGroupConversation(data)) return text
        return stripRedundantSenderPrefix(text, sender)
    }

    internal fun stripRedundantSenderPrefix(text: String, sender: String): String {
        val normalizedSender = sender.trim()
        if (normalizedSender.isEmpty()) return text
        for (colon in SENDER_PREFIX_COLONS) {
            val prefix = "$normalizedSender$colon"
            if (text.startsWith(prefix)) {
                return text.substring(prefix.length).trimStart()
            }
        }
        val colonIndex = text.indexOfAny(SENDER_PREFIX_COLONS)
        if (colonIndex in 1 until text.lastIndex) {
            val prefix = text.substring(0, colonIndex).trim()
            if (prefix.equals(normalizedSender, ignoreCase = true)) {
                return text.substring(colonIndex + 1).trimStart()
            }
        }
        return text
    }

    private val SENDER_PREFIX_COLONS = charArrayOf(':', '：')

    /** 群聊消息行：不用会话级图标冒充每人头像；单聊仍可用 largeIcon。 */
    fun resolveMessageRowAvatarBitmap(
        message: NotificationMessage,
        data: NotificationData,
    ): Bitmap? {
        if (NotificationData.isDistinctMessageSenderIcon(message.senderIcon, data)) {
            return message.senderIcon
        }
        if (!NotificationData.isGroupConversation(data)) return data.largeIcon
        val sender = message.sender?.trim()?.takeIf { it.isNotEmpty() }
            ?: senderFromMessageText(message.text.trim())
        if (sender != null && sender == resolveLatestMessagingSender(data)) {
            return data.largeIcon?.takeIf { NotificationData.isDistinctMessageSenderIcon(it, data) }
        }
        return null
    }

    fun resolveMessageRowAvatarLabel(
        message: NotificationMessage,
        data: NotificationData,
    ): String? {
        if (NotificationData.isDistinctMessageSenderIcon(message.senderIcon, data)) return null
        val sender = message.sender?.trim()?.takeIf { it.isNotEmpty() }
            ?: senderFromMessageText(message.text.trim())
        if (sender != null) return sender
        if (!NotificationData.isGroupConversation(data)) {
            return NotificationData.overlayHeaderTitle(data)
        }
        return null
    }

    fun shouldShowMessageRowAvatar(
        message: NotificationMessage,
        data: NotificationData,
    ): Boolean =
        resolveMessageRowAvatarBitmap(message, data) != null ||
            !resolveMessageRowAvatarLabel(message, data).isNullOrBlank()

    internal fun resolveLatestMessagingSender(data: NotificationData): String? {
        val latestInBundle = data.messages.lastOrNull()
        return latestInBundle?.sender?.trim()?.takeIf { it.isNotEmpty() }
            ?: senderFromMessageText(latestInBundle?.text?.trim().orEmpty())
            ?: senderFromMessageText(data.content.trim())
    }

    internal fun senderFromMessageText(text: String): String? {
        val colonIndex = text.indexOf(':')
        if (colonIndex in 1 until text.lastIndex) {
            return text.substring(0, colonIndex).trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    /**
     * 展示前补齐：历史里缺 sender 的群消息向前继承；每人头像仅按发送者名绑定，禁止跨人复用。
     */
    fun enrichMessageRowsForDisplay(
        messages: List<NotificationMessage>,
        data: NotificationData,
    ): List<NotificationMessage> {
        if (messages.isEmpty() || !NotificationData.isGroupConversation(data)) return messages
        val iconBySender = LinkedHashMap<String, Bitmap>()
        val latestSender = resolveLatestMessagingSender(data)
        val latestSenderIcon = data.largeIcon?.takeIf {
            NotificationData.isDistinctMessageSenderIcon(it, data)
        }
        if (!latestSender.isNullOrBlank() && latestSenderIcon != null) {
            iconBySender[latestSender] = latestSenderIcon
        }
        messages.forEach { message ->
            val sender = message.sender?.trim()?.takeIf { it.isNotEmpty() }
                ?: senderFromMessageText(message.text.trim())
            val icon = message.senderIcon?.takeIf {
                NotificationData.isDistinctMessageSenderIcon(it, data)
            }
            if (!sender.isNullOrBlank() && icon != null) {
                iconBySender[sender] = icon
            }
        }
        var lastSender: String? = null
        return messages.map { message ->
            val parsedSender = message.sender?.trim()?.takeIf { it.isNotEmpty() }
                ?: senderFromMessageText(message.text.trim())
            val sender = parsedSender ?: lastSender
            val ownDistinctIcon = message.senderIcon?.takeIf {
                NotificationData.isDistinctMessageSenderIcon(it, data)
            }
            val senderIcon = ownDistinctIcon ?: sender?.let { iconBySender[it] }
            if (parsedSender != null) lastSender = parsedSender
            message.copy(
                sender = sender,
                senderIcon = senderIcon,
            )
        }
    }

    /** 贴边列表为常驻会话，不因系统通知移除而删球。 */
    fun shouldRemoveEntryAfterReconcile(
        conversationSourceKey: String,
        removedConversationKey: String?,
        activeConversationKeys: Set<String>,
        displayedPostKeysEmpty: Boolean,
    ): Boolean = false
}
