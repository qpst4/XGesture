package com.slideindex.app.message

import android.app.Notification
import android.content.Context
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MessageNotificationFilterTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    @After
    fun resetState() {
        MessageNotificationFilter.resetForTesting()
    }

    @Test
    fun dedup_rejectsDuplicatePostKey() {
        val data = sampleData(key = "k1", title = "A", content = "1")
        assertTrue(MessageNotificationFilter.dedup(data))
        assertFalse(MessageNotificationFilter.dedup(data))
    }

    @Test
    fun dedup_allowsSameConversationWithNewPostTime() {
        val first = sampleData(key = "k1", title = "Hello", content = "World", postTime = 1L)
        val second = sampleData(key = "k1", title = "Hello", content = "World", postTime = 2L)
        assertTrue(MessageNotificationFilter.dedup(first))
        assertTrue(MessageNotificationFilter.dedup(second))
    }

    @Test
    fun acceptDanmaku_rejectsUnchangedConversationContent() {
        val first = sampleData(
            key = "k1",
            title = "TG 群",
            content = "旧消息",
            postTime = 1L,
            conversationSourceKey = "tg|group",
            messages = listOf(NotificationMessage(text = "旧消息", timestamp = 1L)),
        )
        val refreshed = sampleData(
            key = "k1",
            title = "TG 群",
            content = "旧消息",
            postTime = 2L,
            conversationSourceKey = "tg|group",
            messages = listOf(NotificationMessage(text = "旧消息", timestamp = 1L)),
        )
        val updated = sampleData(
            key = "k1",
            title = "TG 群",
            content = "新消息",
            postTime = 3L,
            conversationSourceKey = "tg|group",
            messages = listOf(
                NotificationMessage(text = "旧消息", timestamp = 1L),
                NotificationMessage(text = "新消息", timestamp = 3L),
            ),
        )

        assertTrue(MessageNotificationFilter.acceptDanmaku(first))
        assertFalse(MessageNotificationFilter.acceptDanmaku(refreshed))
        assertTrue(MessageNotificationFilter.acceptDanmaku(updated))
    }

    @Test
    fun shouldShowNotification_blocksOwnPackage() {
        val sbn = statusBarNotification(context.packageName, "self")
        val data = sampleData(packageName = context.packageName)
        assertFalse(
            MessageNotificationFilter.shouldShowNotification(
                context = context,
                // 允许列表必须非空，否则会被 isPackageAllowed 先拦掉，测不到「自己的包」这条。
                settings = MessageSettings(enabledPackages = setOf(context.packageName)),
                sbn = sbn,
                data = data,
                environmentPort = object : MessageEnvironmentPort {
                    override fun isSystemDndEnabled(context: Context) = false
                },
                foregroundPort = object : MessageForegroundPort {
                    override fun foregroundPackage() = null
                },
            ),
        )
    }

    @Test
    fun shouldShowNotification_blocksDisabledPackage() {
        val sbn = statusBarNotification("com.example.chat", "chat")
        val data = sampleData(packageName = "com.example.chat")
        val settings = MessageSettings(
            enabledPackages = setOf("com.example.chat"),
            disabledPackages = setOf("com.example.chat"),
        )
        assertFalse(
            MessageNotificationFilter.shouldShowNotification(
                context = context,
                settings = settings,
                sbn = sbn,
                data = data,
                environmentPort = object : MessageEnvironmentPort {
                    override fun isSystemDndEnabled(context: Context) = false
                },
                foregroundPort = object : MessageForegroundPort {
                    override fun foregroundPackage() = null
                },
            ),
        )
    }

    @Test
    fun shouldShowNotification_blocksSystemDndWhenConfigured() {
        val sbn = statusBarNotification("com.example.chat", "chat")
        val data = sampleData(packageName = "com.example.chat")
        val settings = MessageSettings(
            enabledPackages = setOf("com.example.chat"),
            suppressWhenSystemDnd = true,
        )
        assertFalse(
            MessageNotificationFilter.shouldShowNotification(
                context = context,
                settings = settings,
                sbn = sbn,
                data = data,
                environmentPort = object : MessageEnvironmentPort {
                    override fun isSystemDndEnabled(context: Context) = true
                },
                foregroundPort = object : MessageForegroundPort {
                    override fun foregroundPackage() = null
                },
            ),
        )
    }

    @Test
    fun shouldShowNotification_allowsNormalNotification() {
        val sbn = statusBarNotification("com.example.chat", "chat")
        val data = sampleData(packageName = "com.example.chat")
        assertTrue(
            MessageNotificationFilter.shouldShowNotification(
                context = context,
                settings = MessageSettings(enabledPackages = setOf("com.example.chat")),
                sbn = sbn,
                data = data,
                environmentPort = object : MessageEnvironmentPort {
                    override fun isSystemDndEnabled(context: Context) = false
                },
                foregroundPort = object : MessageForegroundPort {
                    override fun foregroundPackage() = null
                },
            ),
        )
    }

    @Test
    fun shouldShowNotification_blocksWhenAllowlistEmpty() {
        // 允许列表空 = 不允许，与「允许的应用」界面一致（见 MessageSettings.isPackageAllowed）。
        // 这条锁住语义：改动时不能悄悄退回「空 = 全允许」。
        val sbn = statusBarNotification("com.example.chat", "chat")
        val data = sampleData(packageName = "com.example.chat")
        assertFalse(
            MessageNotificationFilter.shouldShowNotification(
                context = context,
                settings = MessageSettings(),
                sbn = sbn,
                data = data,
                environmentPort = object : MessageEnvironmentPort {
                    override fun isSystemDndEnabled(context: Context) = false
                },
                foregroundPort = object : MessageForegroundPort {
                    override fun foregroundPackage() = null
                },
            ),
        )
    }

    private fun sampleData(
        packageName: String = "com.example.chat",
        key: String = "key",
        title: String = "Title",
        content: String = "Body",
        postTime: Long = 1_700_000_000_000L,
        conversationSourceKey: String = "",
        messages: List<NotificationMessage> = emptyList(),
    ) = NotificationData(
        packageName = packageName,
        key = key,
        title = title,
        content = content,
        largeIcon = null,
        appIcon = null,
        contentIntent = null,
        postTime = postTime,
        conversationSourceKey = conversationSourceKey,
        messages = messages,
    )

    private fun statusBarNotification(packageName: String, tag: String): StatusBarNotification {
        val notification = Notification.Builder(context, "test")
            .setContentTitle("title")
            .setContentText("text")
            .build()
        return StatusBarNotification(
            packageName,
            packageName,
            1,
            tag,
            1000,
            1,
            0,
            notification,
            UserHandle.getUserHandleForUid(0),
            System.currentTimeMillis(),
        )
    }
}
