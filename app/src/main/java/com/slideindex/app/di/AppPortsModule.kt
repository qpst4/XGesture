package com.slideindex.app.di

import android.content.Context
import android.content.Intent
import com.slideindex.app.data.AppLaunchPort
import com.slideindex.app.message.AppMessageEnvironmentPort
import com.slideindex.app.message.AppMessageForegroundPort
import com.slideindex.app.message.AppMessageOverlayPort
import com.slideindex.app.message.AppMessageReplyPort
import com.slideindex.app.message.AppMessageReturnChip
import com.slideindex.app.message.AppNotificationShortcutIconPort
import com.slideindex.app.message.MessageReplyPort
import com.slideindex.app.message.MessageReturnPort
import com.slideindex.app.message.NotificationShortcutIconPort
import com.slideindex.app.message.AppMessageThemePort
import com.slideindex.app.message.MessageEnvironmentPort
import com.slideindex.app.message.MessageForegroundPort
import com.slideindex.app.message.MessageOverlayPort
import com.slideindex.app.message.MessageThemePort
import com.slideindex.app.notification.ActiveNotificationSnapshot
import com.slideindex.app.notification.NotificationListenerPort
import com.slideindex.app.notification.NotificationShadeActions
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.notification.NotificationShadeHider
import com.slideindex.app.notification.AppNotificationIntentLaunchPort
import com.slideindex.app.notification.AppNotificationOtpSideEffects
import com.slideindex.app.notification.AppNotificationRuleUiStrings
import com.slideindex.app.notification.NotificationHistoryLaunchPort
import com.slideindex.app.notification.NotificationIntentLaunchPort
import com.slideindex.app.notification.NotificationOtpSideEffects
import com.slideindex.app.notification.NotificationRuleExecutor
import com.slideindex.app.notification.NotificationRuleUiStrings
import com.slideindex.app.service.LaunchTrampolineActivity
import com.slideindex.app.service.MediaNotificationListener
import com.slideindex.app.overlay.OverlayStatePort
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsBackupCloudConfigPort
import com.slideindex.app.shake.AppShakeActionPort
import com.slideindex.app.shake.AppShakeFeedbackPort
import com.slideindex.app.shake.AppShakeRuntimePort
import com.slideindex.app.shake.ShakeActionPort
import com.slideindex.app.shake.ShakeFeedbackPort
import com.slideindex.app.shake.ShakeRuntimePort
import com.slideindex.app.util.FreeWindowLauncher
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaNotificationListenerPort @Inject constructor() : NotificationListenerPort {
    override fun listenerOrNull(): NotificationListenerService? = MediaNotificationListener.instance

    /**
     * 监听服务与 UI 同进程：直接读本进程发布的快照（还没有快照时返回 null，让调用方回退）。
     */
    override fun activeNotificationSnapshotsOrNull(): List<ActiveNotificationSnapshot>? =
        OverlayStatePort.mirroredActiveNotificationSnapshots()
}

@Singleton
class AppNotificationShadeActions @Inject constructor(
    private val ruleExecutor: NotificationRuleExecutor,
    private val shadeHider: NotificationShadeHider
) : NotificationShadeActions {
    override fun hideFromShade(listener: NotificationListenerService, sbn: StatusBarNotification): Boolean =
        shadeHider.hideFromShade(listener, sbn)

    override fun hideFromShadeOnMain(listener: NotificationListenerService, sbn: StatusBarNotification) {
        shadeHider.hideFromShadeOnMain(listener, sbn)
    }

    override fun cancelDismissibleFromShade(
        listener: NotificationListenerService,
        sbn: StatusBarNotification
    ): Boolean = shadeHider.cancelDismissibleFromShade(listener, sbn)

    override fun cancelDismissibleFromShadeOnMain(
        listener: NotificationListenerService,
        sbn: StatusBarNotification
    ) {
        shadeHider.cancelDismissibleFromShadeOnMain(listener, sbn)
    }

    override fun hideFromShade(
        listener: NotificationListenerService,
        key: String,
        sbn: StatusBarNotification?
    ): Boolean = shadeHider.hideFromShade(listener, key, sbn)

    override fun snoozeMatchingActive(
        context: Context,
        listener: NotificationListenerService,
        shouldHide: (StatusBarNotification) -> Boolean
    ) {
        listener.activeNotifications?.forEach { sbn ->
            if (sbn.packageName == context.packageName) return@forEach
            if (shouldHide(sbn)) {
                shadeHider.hideFromShade(listener, sbn)
            }
        }
    }

    override fun executeRules(
        context: Context,
        listener: NotificationListenerService,
        sbn: StatusBarNotification,
        rules: List<NotificationFilterRule>
    ) {
        ruleExecutor.execute(context, listener, sbn, rules)
    }

    override fun hideNotificationByKey(key: String): Boolean = shadeHider.hideNotification(key)

    override fun restoreAllSnoozed(selfPackage: String): List<String> =
        shadeHider.restoreAllSnoozed(selfPackage)

    override fun unsnoozeNotification(key: String): Boolean = shadeHider.unsnoozeNotification(key)
}

@Singleton
class AppNotificationHistoryLaunchPort @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationHistoryLaunchPort {
    override fun startPendingIntentTrampoline(pendingIntentBase64: String, fallbackIntent: Intent?): Boolean =
        runCatching {
            context.startActivity(
                LaunchTrampolineActivity.createPendingIntentIntent(
                    context = context,
                    pendingIntentBase64 = pendingIntentBase64,
                    fallbackIntent = fallbackIntent
                )
            )
        }.isSuccess

    override fun launchReplayIntent(intent: Intent, packageName: String, extrasBase64: String?): Boolean {
        val trampoline = LaunchTrampolineActivity.createIntent(context, intent)
        if (runCatching { context.startActivity(trampoline) }.isSuccess) return true
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}

@Singleton
class FreeWindowAppLaunchPort @Inject constructor(
    @ApplicationContext private val context: Context
) : AppLaunchPort {
    override fun launch(intent: Intent, settings: AppSettings, fullscreen: Boolean) {
        FreeWindowLauncher.launch(context, intent, settings, fullscreen)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppPortsModule {
    @Binds
    @Singleton
    abstract fun bindAppIconOverrideProvider(
        impl: AppIconOverrideProviderImpl,
    ): com.slideindex.app.data.AppIconOverrideProvider

    @Binds
    @Singleton
    abstract fun bindNotificationListenerPort(impl: MediaNotificationListenerPort): NotificationListenerPort

    @Binds
    @Singleton
    abstract fun bindNotificationShadeActions(impl: AppNotificationShadeActions): NotificationShadeActions

    @Binds
    @Singleton
    abstract fun bindAppLaunchPort(impl: FreeWindowAppLaunchPort): AppLaunchPort

    @Binds
    @Singleton
    abstract fun bindNotificationHistoryLaunchPort(
        impl: AppNotificationHistoryLaunchPort
    ): NotificationHistoryLaunchPort

    @Binds
    @Singleton
    abstract fun bindNotificationOtpSideEffects(
        impl: AppNotificationOtpSideEffects
    ): NotificationOtpSideEffects

    @Binds
    @Singleton
    abstract fun bindNotificationIntentLaunchPort(
        impl: AppNotificationIntentLaunchPort
    ): NotificationIntentLaunchPort

    @Binds
    @Singleton
    abstract fun bindNotificationRuleUiStrings(
        impl: AppNotificationRuleUiStrings
    ): NotificationRuleUiStrings

    @Binds
    @Singleton
    abstract fun bindMessageReplyPort(impl: AppMessageReplyPort): MessageReplyPort

    @Binds
    @Singleton
    abstract fun bindMessageOverlayPort(impl: AppMessageOverlayPort): MessageOverlayPort

    @Binds
    @Singleton
    abstract fun bindMessageReturnPort(impl: AppMessageReturnChip): MessageReturnPort

    @Binds
    @Singleton
    abstract fun bindNotificationShortcutIconPort(
        impl: AppNotificationShortcutIconPort,
    ): NotificationShortcutIconPort

    @Binds
    @Singleton
    abstract fun bindMessageThemePort(impl: AppMessageThemePort): MessageThemePort

    @Binds
    @Singleton
    abstract fun bindMessageForegroundPort(impl: AppMessageForegroundPort): MessageForegroundPort

    @Binds
    @Singleton
    abstract fun bindMessageEnvironmentPort(impl: AppMessageEnvironmentPort): MessageEnvironmentPort

    @Binds
    @Singleton
    abstract fun bindShakeRuntimePort(impl: AppShakeRuntimePort): ShakeRuntimePort

    @Binds
    @Singleton
    abstract fun bindShakeActionPort(impl: AppShakeActionPort): ShakeActionPort

    @Binds
    @Singleton
    abstract fun bindShakeFeedbackPort(impl: AppShakeFeedbackPort): ShakeFeedbackPort

    @Binds
    @Singleton
    abstract fun bindSettingsBackupCloudConfigPort(
        impl: VlmOcrSettingsBackupCloudConfigPort,
    ): SettingsBackupCloudConfigPort
}
