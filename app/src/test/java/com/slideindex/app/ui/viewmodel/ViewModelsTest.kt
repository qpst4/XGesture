package com.slideindex.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Looper
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.clipboard.monitor.ClipboardMonitorController
import com.slideindex.app.data.AppLaunchPort
import com.slideindex.app.stash.StashMetaRepository
import com.slideindex.app.stash.StashRepository
import com.slideindex.app.data.AppLaunchIconCache
import com.slideindex.app.data.AppRepository
import com.slideindex.app.notification.NotificationFilterPreferences
import com.slideindex.app.notification.NotificationFilterRepository
import com.slideindex.app.notification.NotificationHistoryItem
import com.slideindex.app.notification.NotificationHistoryLaunchPort
import com.slideindex.app.notification.NotificationHistoryRepository
import com.slideindex.app.notification.NotificationListenerPort
import com.slideindex.app.notification.NotificationShadeActions
import com.slideindex.app.otp.OtpOfficialRulesLoader
import com.slideindex.app.otp.OtpRecordsRepository
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.slideindex.app.data.AppIconOverrideProvider
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageDisplayPlan
import com.slideindex.app.message.MessageOverlayPort
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.NotificationData
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.clearTestSettings
import com.slideindex.app.settings.testSettingsRepository
import com.slideindex.app.ui.feedback.UserMessage
import com.slideindex.app.ui.feedback.UserMessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class HomeViewModelTest : ViewModelCoroutineTest() {
    private lateinit var context: Context
    private lateinit var repository: com.slideindex.app.settings.SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        repository = testSettingsRepository(context)
    }

    @Test
    fun settings_initialValue_matchesDefaults() {
        val viewModel = HomeViewModel(
            repository,
            UserMessageBus(),
            context,
            object : HomeScreenEffects {
                override fun refreshServiceState() = Unit
                override fun requestNotificationPermission() = Unit
                override fun requestShizuku() = Unit
                override fun openAccessibilitySettings() = Unit
                override fun previewHaptic(enabled: Boolean, strengthLevel: Int?) = Unit
            },
        )

        assertFalse(viewModel.settings.value.serviceEnabled)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class KeepAliveSettingsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = KeepAliveSettingsViewModel(
            settingsRepository = repository,
            userMessageBus = UserMessageBus(),
            context,
            object : KeepAliveScreenEffects {
                override fun applyHideFromRecents(enabled: Boolean) = Unit
                override fun onAccessibilityKeepAliveEnabled() = Unit
            },
        )

        assertFalse(viewModel.settings.value.hideFromRecents)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ExtensionHubViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val stashRepository = StashRepository(context, StashMetaRepository(context))
        val clipboardHistoryRepository = ClipboardHistoryRepository(
            context,
            repository,
            ClipboardMonitorController(context),
        )
        val viewModel = ExtensionHubViewModel(
            settingsRepository = repository,
            userMessageBus = UserMessageBus(),
            context = context,
            stashRepository = stashRepository,
            clipboardHistoryRepository = clipboardHistoryRepository,
            appRepository = testAppRepository(context),
        )

        assertFalse(viewModel.settings.value.serviceEnabled)
        assertEquals(AppSettings().quickLauncherColumnsPerPage, viewModel.settings.value.quickLauncherColumnsPerPage)
    }

    @Test
    fun settings_reflectRepositoryUpdates() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val stashRepository = StashRepository(context, StashMetaRepository(context))
        val clipboardHistoryRepository = ClipboardHistoryRepository(
            context,
            repository,
            ClipboardMonitorController(context),
        )
        val viewModel = ExtensionHubViewModel(
            repository,
            UserMessageBus(),
            context,
            stashRepository,
            clipboardHistoryRepository,
            testAppRepository(context),
        )
        val settingsCollector = launch { viewModel.settings.collect { } }
        primeSettingsFlow(repository)

        repository.setServiceEnabled(true)

        assertTrue(awaitSettings(repository) { it.serviceEnabled }.serviceEnabled)
        assertTrue(viewModel.settings.first { it.serviceEnabled }.serviceEnabled)
        settingsCollector.cancel()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ExtensionSettingsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = ExtensionSettingsViewModel(repository, UserMessageBus(), context)

        assertEquals(AppSettings().quickLauncherColumnsPerPage, viewModel.settings.value.quickLauncherColumnsPerPage)
    }

    @Test
    fun setQuickLauncherColumnsPerPage_persistsToSettingsFlow() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = ExtensionSettingsViewModel(repository, UserMessageBus(), context)
        primeSettingsFlow(repository)

        viewModel.setQuickLauncherColumnsPerPage(5)

        assertEquals(5, awaitSettings(repository) { it.quickLauncherColumnsPerPage == 5 }.quickLauncherColumnsPerPage)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class HomeDetailSettingsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = HomeDetailSettingsViewModel(repository, UserMessageBus(), context)

        assertEquals(AppSettings().panelOpacity, viewModel.settings.value.panelOpacity)
    }

    @Test
    fun setPanelOpacity_persistsToSettingsFlow() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = HomeDetailSettingsViewModel(repository, UserMessageBus(), context)
        primeSettingsFlow(repository)

        viewModel.setPanelOpacity(0.72f)

        assertEquals(
            0.72f,
            awaitSettings(repository) { kotlin.math.abs(it.panelOpacity - 0.72f) < 0.001f }.panelOpacity,
            0.001f,
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MessageSettingsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = MessageSettingsViewModel(
            repository,
            UserMessageBus(),
            context,
            NoOpMessageOverlayPort,
        )

        // stateIn 的初始值是 codec 默认，必须先等到 DataStore 首帧快照再断言；
        // 总开关只认自己的键，全新存储下应为关（不再被默认开启的提醒样式带成开）。
        val settings = awaitSettings(repository) { !it.messageReminderSettings.enabled }
        assertFalse(settings.messageReminderSettings.enabled)
    }

    @Test
    fun setMessageReminderEnabled_persistsToSettingsFlow() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = MessageSettingsViewModel(
            repository,
            UserMessageBus(),
            context,
            NoOpMessageOverlayPort,
        )
        primeSettingsFlow(repository)

        viewModel.setMessageReminderEnabled(true)

        assertTrue(awaitSettings(repository) { it.messageReminderSettings.enabled }.messageReminderSettings.enabled)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OtpSettingsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun refreshOfficialRules_reloadsBundledRules() {
        val context = RuntimeEnvironment.getApplication()
        val loader = OtpOfficialRulesLoader(context)
        val viewModel = OtpSettingsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            otpOfficialRulesLoader = loader,
            otpRecordsRepository = OtpRecordsRepository(context),
        )

        viewModel.refreshOfficialRules()

        assertTrue(viewModel.officialRules.value.isNotEmpty())
    }

    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = OtpSettingsViewModel(
            settingsRepository = repository,
            userMessageBus = UserMessageBus(),
            context,
            otpOfficialRulesLoader = OtpOfficialRulesLoader(context),
            otpRecordsRepository = OtpRecordsRepository(context),
        )

        assertFalse(viewModel.settings.value.otpAutoInputEnabled)
    }

    @Test
    fun setOtpAutoInputEnabled_persistsToSettingsFlow() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = OtpSettingsViewModel(
            settingsRepository = repository,
            userMessageBus = UserMessageBus(),
            context,
            otpOfficialRulesLoader = OtpOfficialRulesLoader(context),
            otpRecordsRepository = OtpRecordsRepository(context),
        )
        primeSettingsFlow(repository)

        viewModel.setOtpAutoInputEnabled(true)

        assertTrue(awaitSettings(repository) { it.otpAutoInputEnabled }.otpAutoInputEnabled)
    }

    @Test
    fun recordTestOtp_persistsRecord() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val recordsRepository = OtpRecordsRepository(context)
        val viewModel = OtpSettingsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            otpOfficialRulesLoader = OtpOfficialRulesLoader(context),
            otpRecordsRepository = recordsRepository,
        )
        delay(200)

        viewModel.recordTestOtp(code = "654321", sampleText = "code 654321", ruleName = "test-rule")
        delay(200)

        assertEquals(1, recordsRepository.records.value.size)
        assertEquals("654321", recordsRepository.records.value.first().code)
        assertTrue(recordsRepository.records.value.first().isTest)
    }

    @Test
    fun recordTestOtp_onFailure_emitsError() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val bus = UserMessageBus()
        val viewModel = OtpSettingsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = bus,
            context,
            otpOfficialRulesLoader = OtpOfficialRulesLoader(context),
            otpRecordsRepository = OtpRecordsRepository(context),
        )
        val message = async(Dispatchers.Unconfined) { awaitUserError(bus) }
        // Windows 下目录的只读属性并不阻止写入：先让仓库落一次盘，再把记录文件本身设为只读。
        val recordsFile = File(context.filesDir, "otp_records.json")
        assertTrue(OtpRecordsRepository(context).clearAll().isSuccess)
        assertTrue("record file should exist before write", recordsFile.exists())
        recordsFile.setWritable(false)

        try {
            viewModel.recordTestOtp(code = "000000", sampleText = "code", ruleName = null)
            delay(200)
            assertTrue(withTimeout(5_000) { message.await() }.text.isNotEmpty())
        } finally {
            recordsFile.setWritable(true)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OtpRecordsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun records_initialValue_isEmpty() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = OtpRecordsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            otpRecordsRepository = OtpRecordsRepository(context),
            appRepository = testAppRepository(context),
        )

        assertTrue(viewModel.records.value.isEmpty())
    }

    @Test
    fun deleteRecord_removesFromRepository() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val repository = OtpRecordsRepository(context)
        repository.recordSuspend(
            code = "654321",
            packageName = "com.test.app",
            title = "Test",
            text = "code 654321",
            isTest = true,
        )
        val recordId = repository.records.first { it.isNotEmpty() }.first().id

        val viewModel = OtpRecordsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            otpRecordsRepository = repository,
            appRepository = testAppRepository(context),
        )

        viewModel.deleteRecord(recordId)
        delay(200)

        assertTrue(repository.records.value.none { it.id == recordId })
    }

    @Test
    fun deleteRecord_onFailure_emitsError() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val repository = OtpRecordsRepository(context)
        repository.recordSuspend(
            code = "111111",
            packageName = "com.test.app",
            title = "Test",
            text = "code 111111",
            isTest = true,
        )
        val recordId = repository.records.first { it.isNotEmpty() }.first().id
        val bus = UserMessageBus()
        val viewModel = OtpRecordsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = bus,
            context,
            otpRecordsRepository = repository,
            appRepository = testAppRepository(context),
        )
        val message = async(Dispatchers.Unconfined) { awaitUserError(bus) }
        // Windows 下目录的只读属性并不阻止写入，必须把记录文件本身设为只读，写盘才会确定失败。
        val recordsFile = File(context.filesDir, "otp_records.json")
        assertTrue("record file should exist before delete", recordsFile.exists())
        recordsFile.setWritable(false)

        try {
            viewModel.deleteRecord(recordId)
            delay(200)
            assertTrue(withTimeout(5_000) { message.await() }.text.isNotEmpty())
        } finally {
            recordsFile.setWritable(true)
        }
    }

    @Test
    fun loadApps_onFailure_emitsError() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val bus = UserMessageBus()
        val viewModel = OtpRecordsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = bus,
            context,
            otpRecordsRepository = OtpRecordsRepository(context),
            appRepository = AppRepository(
                ThrowingPackageManagerContext(context),
                NoOpAppLaunchPort,
                AppLaunchIconCache(context),
                NoOpAppIconOverrideProvider,
                testApplicationScope,
            ),
        )
        val message = async(Dispatchers.Unconfined) { awaitUserError(bus) }

        viewModel.loadApps()
        delay(100)

        assertTrue(message.await().text.isNotEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NotificationHubViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun visibleHistoryCount_startsAtZero() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = NotificationHubViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            notificationHistoryRepository = notificationHistoryRepository(context),
            notificationFilterRepository = notificationFilterRepository(context),
        )

        assertEquals(0, viewModel.visibleHistoryCount.value)
    }

    @Test
    fun visibleHistoryCount_reflectsRecordedItems() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val historyRepository = notificationHistoryRepository(context)
        val viewModel = NotificationHubViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            notificationHistoryRepository = historyRepository,
            notificationFilterRepository = notificationFilterRepository(context),
        )
        val countCollector = launch { viewModel.visibleHistoryCount.collect { } }
        delay(100)

        historyRepository.record(
            NotificationHistoryItem(
                id = "hub-item-1",
                packageName = "com.test",
                title = "Title",
                text = "Body",
                postedAtMs = 1L,
                intentUri = null,
                notificationKey = "key-1",
            ),
        )

        // visibleHistoryCount 走 combine(...).debounce(300)：debounce 用的是虚拟时间，而写入到 combine
        // 发射之间隔着仓库的 IO 路径，timing 不定，所以交替「推进虚拟时间 + 让出真实时间片」直到更新，
        // 超时即失败（否则主线程 runBlocking 会永久挂住整个测试 JVM）。
        val visibleCount = withTimeout(5_000) {
            var count = viewModel.visibleHistoryCount.value
            while (count != 1) {
                advanceMainVirtualTime(100)
                delay(10)
                count = viewModel.visibleHistoryCount.value
            }
            count
        }
        assertEquals(1, visibleCount)
        countCollector.cancel()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SettingsViewModelWriteTest : ViewModelCoroutineTest() {
    @Test
    fun launchSettingsWrite_onFailure_emitsError() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val bus = UserMessageBus()
        val viewModel = TestSettingsViewModel(testSettingsRepository(context), bus, context)
        val message = async(Dispatchers.Unconfined) { awaitUserError(bus) }

        viewModel.triggerFailingWrite()

        assertTrue(message.await().text.isNotEmpty())
    }

    @Test
    fun launchRepositoryWrite_onFailure_emitsError() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val bus = UserMessageBus()
        val viewModel = TestSettingsViewModel(testSettingsRepository(context), bus, context)
        val message = async(Dispatchers.Unconfined) { awaitUserError(bus) }

        viewModel.triggerFailingRepositoryWrite()

        assertTrue(message.await().text.isNotEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NotificationHistoryViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun hideNotification_whenListenerDisabled_emitsError() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val bus = UserMessageBus()
        val viewModel = notificationHistoryViewModel(context, bus)
        val item = NotificationHistoryItem(
            id = "item-1",
            packageName = "com.test",
            title = "Title",
            text = "Body",
            postedAtMs = 1L,
            intentUri = null,
            notificationKey = null,
        )

        val message = async(Dispatchers.Unconfined) { bus.messages.first() }
        viewModel.hideNotification(item, listenerEnabled = false)
        assertTrue(message.await() is UserMessage.Error)
    }

    @Test
    fun restoreAllSnoozed_whenListenerDisabled_returnsNegativeOne() {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = notificationHistoryViewModel(context, UserMessageBus())

        val result = viewModel.restoreAllSnoozed(listenerEnabled = false)

        assertEquals(-1, result)
    }

    @Test
    fun refreshActive_incrementsGeneration() {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = notificationHistoryViewModel(context, UserMessageBus())
        val before = viewModel.refreshGeneration.value

        viewModel.refreshActive()

        // 主动刷新自增一次；重新发布的快照到达时 init 里的收集器还会再自增一次，
        // 因此这里只断言「确实推进了」，不对次数做约束。
        assertTrue(viewModel.refreshGeneration.value > before)
    }
}

private fun notificationHistoryViewModel(context: Context, bus: UserMessageBus): NotificationHistoryViewModel =
    NotificationHistoryViewModel(
        settingsRepository = testSettingsRepository(context),
        userMessageBus = bus,
        context = context,
        notificationHistoryRepository = notificationHistoryRepository(context),
        notificationFilterRepository = notificationFilterRepository(context),
        notificationFilterPreferences = NotificationFilterPreferences(context),
        appRepository = testAppRepository(context),
    )

/** 与生产 `AppModule.provideApplicationScope()` 等价，测试中不启动后台工作线程。 */
private val testApplicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

private fun testAppRepository(context: Context): AppRepository =
    AppRepository(
        context,
        NoOpAppLaunchPort,
        AppLaunchIconCache(context),
        NoOpAppIconOverrideProvider,
        testApplicationScope,
    )

private fun notificationHistoryRepository(context: Context): NotificationHistoryRepository =
    NotificationHistoryRepository(
        context = context,
        filterPreferences = NotificationFilterPreferences(context),
        listenerPort = NoOpNotificationListenerPort,
        shadeActions = NoOpNotificationShadeActions,
        launchPort = NoOpNotificationHistoryLaunchPort,
    )

private fun notificationFilterRepository(context: Context): NotificationFilterRepository =
    NotificationFilterRepository(
        context = context,
        listenerPort = NoOpNotificationListenerPort,
        shadeActions = NoOpNotificationShadeActions,
    )

private object NoOpAppIconOverrideProvider : AppIconOverrideProvider {
    override fun bitmap(context: Context, packageName: String, sizePx: Int): Bitmap? = null

    override fun peekBitmap(context: Context, packageName: String, sizePx: Int): Bitmap? = null

    override fun drawable(context: Context, packageName: String, sizePx: Int): Drawable? = null

    override fun peekDrawable(context: Context, packageName: String, sizePx: Int): Drawable? = null

    override fun preload(context: Context, packageNames: Collection<String>, sizePx: Int) = Unit
}

private object NoOpAppLaunchPort : AppLaunchPort {
    override fun launch(intent: Intent, settings: AppSettings, fullscreen: Boolean) = Unit
}

private object NoOpNotificationListenerPort : NotificationListenerPort {
    override fun listenerOrNull() = null
}

private object NoOpNotificationShadeActions : NotificationShadeActions {
    override fun hideFromShade(
        listener: android.service.notification.NotificationListenerService,
        sbn: android.service.notification.StatusBarNotification,
    ) = false

    override fun hideFromShadeOnMain(
        listener: android.service.notification.NotificationListenerService,
        sbn: android.service.notification.StatusBarNotification,
    ) = Unit

    override fun cancelDismissibleFromShade(
        listener: android.service.notification.NotificationListenerService,
        sbn: android.service.notification.StatusBarNotification,
    ) = false

    override fun cancelDismissibleFromShadeOnMain(
        listener: android.service.notification.NotificationListenerService,
        sbn: android.service.notification.StatusBarNotification,
    ) = Unit

    override fun hideFromShade(
        listener: android.service.notification.NotificationListenerService,
        key: String,
        sbn: android.service.notification.StatusBarNotification?,
    ) = false

    override fun snoozeMatchingActive(
        context: Context,
        listener: android.service.notification.NotificationListenerService,
        shouldHide: (android.service.notification.StatusBarNotification) -> Boolean,
    ) = Unit

    override fun executeRules(
        context: Context,
        listener: android.service.notification.NotificationListenerService,
        sbn: android.service.notification.StatusBarNotification,
        rules: List<com.slideindex.app.notification.NotificationFilterRule>,
    ) = Unit

    override fun hideNotificationByKey(key: String) = false

    override fun restoreAllSnoozed(selfPackage: String) = emptyList<String>()

    override fun unsnoozeNotification(key: String) = false
}

private object NoOpNotificationHistoryLaunchPort : NotificationHistoryLaunchPort {
    override fun startPendingIntentTrampoline(pendingIntentBase64: String, fallbackIntent: Intent?) = false

    override fun launchReplayIntent(intent: Intent, packageName: String, extrasBase64: String?) = false
}

private object NoOpMessageOverlayPort : MessageOverlayPort {
    override fun containsNotification(style: MessageStyle, data: NotificationData) = false

    override fun containsCNoticeConversation(conversationSourceKey: String) = false

    override fun refreshCNoticeConversationIcon(conversationSourceKey: String, icon: Bitmap) = Unit

    override fun dismissEntry(style: MessageStyle, key: String, postTime: Long) = Unit

    override fun dismissEntriesForKey(style: MessageStyle, key: String) = Unit

    override fun reconcileCNoticeAfterRemoval(
        removedNotificationKey: String,
        removedConversationKey: String?,
        activeConversationKeys: Set<String>,
    ) = Unit

    override fun resumeAutoDismiss(style: MessageStyle, key: String, postTime: Long) = Unit

    override fun pauseAutoDismiss(style: MessageStyle, key: String, postTime: Long) = Unit

    override fun dismissImmediate(style: MessageStyle?) = Unit

    override fun snapshotDisplayedKeys(): Set<String> = emptySet()

    override fun dismissAllReminders() = Unit

    override fun replayFloatIconAfterUnlock() = Unit

    override fun dismissSameSourceReminders(sourceKey: String) = Unit

    override fun snapshotDisplayedKeysForSource(sourceKey: String): Set<String> = emptySet()

    override fun showPlan(
        context: Context,
        plan: MessageDisplayPlan,
        onAction: (MessageAction) -> Unit,
        onDismiss: () -> Unit,
        showDanmaku: Boolean,
    ) = Unit

    override fun showUnlockConfirmation(
        context: Context,
        data: NotificationData,
        autoDismissSeconds: Int,
        onConfirm: (Boolean) -> Unit,
        onDismiss: () -> Unit,
    ) = Unit

    override fun detachDanmaku() = Unit
}

private class TestSettingsViewModel(
    settingsRepository: com.slideindex.app.settings.SettingsRepository,
    userMessageBus: UserMessageBus,
    context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun triggerFailingWrite() = launchSettingsWrite { Result.failure(IOException("settings failed")) }

    fun triggerFailingRepositoryWrite() = launchRepositoryWrite { Result.failure(IOException("repo failed")) }
}

private class ThrowingPackageManagerContext(
    base: Context,
) : android.content.ContextWrapper(base) {
    override fun getPackageManager(): android.content.pm.PackageManager {
        throw IOException("package manager failed")
    }
}
