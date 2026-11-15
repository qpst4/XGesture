package com.slideindex.app.notification

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.slideindex.app.message.NotificationTextExtractor
import com.slideindex.app.otp.OtpCaptureDeduplicator
import com.slideindex.app.otp.OtpExtractionConfig
import com.slideindex.app.otp.OtpExtractionResult
import com.slideindex.app.otp.OtpOfficialRulesLoader
import com.slideindex.app.otp.OtpRecordFillStatus
import com.slideindex.app.otp.OtpRecordCategory
import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.otp.OtpSmsSourcePackages
import com.slideindex.app.otp.VerificationCodeExtractor
import com.slideindex.app.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class NotificationHistoryRecorder @Inject constructor(
    private val filterRepository: NotificationFilterRepository,
    private val filterPreferences: NotificationFilterPreferences,
    private val historyRepository: NotificationHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val otpOfficialRulesLoader: OtpOfficialRulesLoader,
    private val otpRecordsRepository: OtpRecordsRepository,
    private val shadeActions: NotificationShadeActions,
    private val otpSideEffects: NotificationOtpSideEffects,
) {
    private val ongoingFingerprints = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun onListenerConnected(
        listener: NotificationListenerService,
        notifications: Array<StatusBarNotification>,
    ) {
        NotificationSbnCache.refreshActive(notifications.toList())
    }

    fun onPosted(
        context: Context,
        listener: NotificationListenerService,
        sbn: StatusBarNotification,
    ): Boolean {
        val notification = sbn.notification ?: return false
        val extras = notification.extras ?: return false
        val content = NotificationTextExtractor.extract(extras)
        val title = content.title
        val text = content.text
        if (title.isBlank() && text.isBlank()) return false

        NotificationSbnCache.cacheActive(sbn)

        // 媒体播放器、进度条或持续型前台通知高频刷新时，若核心文本（歌名/歌手/内容）未变，
        // 则纯属时间戳或播放进度微跳，跳过昂贵的 Intent 抓取、序列化与数据库写入。
        val isOngoing = sbn.isOngoing || (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
        val isMediaOrProgress = notification.category == android.app.Notification.CATEGORY_TRANSPORT ||
            extras.containsKey("android.mediaSession") ||
            extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX, 0) > 0
        if (isOngoing && isMediaOrProgress) {
            val fingerprint = "$title\u0000$text"
            val last = ongoingFingerprints[sbn.key]
            if (last == fingerprint) {
                return false
            }
            ongoingFingerprints[sbn.key] = fingerprint
        }

        val matchingRules = filterRepository.findMatchingRules(sbn)
        val shouldHide = shouldHideNotification(context, sbn)

        val capturedIntent = NotificationHistoryIntentCapture.capture(sbn, context)
        Log.i(
            TAG,
            "Record ${sbn.packageName} key=${sbn.key} " +
                "pi=${!capturedIntent.pendingIntentBase64.isNullOrBlank()} " +
                "uri=${!capturedIntent.intentUri.isNullOrBlank()} " +
                "parcel=${!capturedIntent.intentParcelBase64.isNullOrBlank()} " +
                "notificationExtras=${!capturedIntent.extrasBase64.isNullOrBlank()}",
        )

        val settings = settingsRepository.readSnapshot()
        val officialRules = otpOfficialRulesLoader.getRules()
        val blockedApp = sbn.packageName in settings.otpBlockedPackages
        val skipOtpFromSmsNotification = settings.otpLsposedSmsCaptureEnabled &&
            OtpSmsSourcePackages.isSystemSmsPackage(sbn.packageName)
        val extraction = if (blockedApp || skipOtpFromSmsNotification) {
            OtpExtractionResult(code = null, attempted = false)
        } else {
            VerificationCodeExtractor.extract(
                packageName = sbn.packageName,
                title = title,
                text = text,
                config = OtpExtractionConfig.build(
                    keywordsRegex = settings.otpKeywordsRegex,
                    officialRules = officialRules,
                    userRules = settings.otpUserMatchRules,
                    disabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds,
                ),
            )
        }
        val extractedCode = extraction.code
        val postedAtMs = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        if (!extractedCode.isNullOrBlank()) {
            if (!OtpCaptureDeduplicator.tryConsumeExtractedCode(extractedCode)) {
                Log.d(TAG, "Skipping duplicate OTP from ${sbn.packageName}")
            } else {
                Log.i(TAG, "Extracted OTP from ${sbn.packageName}")
                val fillStatus = if (settings.otpAutoInputEnabled) {
                    OtpRecordFillStatus.PENDING
                } else {
                    OtpRecordFillStatus.NONE
                }
                val recordId = if (settings.otpRecordAppNotifyEnabled) {
                    runBlocking {
                        otpRecordsRepository.recordSuspend(
                            code = extractedCode,
                            packageName = sbn.packageName,
                            title = title,
                            text = text,
                            timestampMs = postedAtMs,
                            ruleName = extraction.ruleName,
                            autoFillStatus = fillStatus,
                            category = OtpRecordCategory.APP_NOTIFY,
                        ).getOrNull()
                    }
                } else {
                    null
                }
                otpSideEffects.onVerificationCodeExtracted(
                    context = context,
                    code = extractedCode,
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    postedAtMs = postedAtMs,
                    ruleName = extraction.ruleName,
                    copyToClipboard = settings.otpCopyToClipboard,
                    autoInputEnabled = settings.otpAutoInputEnabled,
                    recordId = recordId,
                )
            }
        }

        val item = NotificationHistoryItem(
            packageName = sbn.packageName,
            title = title,
            text = text,
            postedAtMs = postedAtMs,
            intentUri = capturedIntent.intentUri,
            intentParcelBase64 = capturedIntent.intentParcelBase64,
            intentExtrasBase64 = capturedIntent.intentExtrasBase64,
            pendingIntentBase64 = capturedIntent.pendingIntentBase64,
            extrasBase64 = capturedIntent.extrasBase64,
            notificationKey = sbn.key,
            channelId = NotificationChannelSupport.channelIdFrom(notification),
            hidden = shouldHide,
            extractedCode = extractedCode,
            extractionAttempted = extraction.attempted,
        )
        // 「停止记录」只关这一处：过滤规则与验证码提取必须继续工作，
        // 否则用户会以为"关了滤盒，连拦截和提码都坏了"。
        if (filterPreferences.readSnapshot().notificationHistoryEnabled) {
            historyRepository.record(item)
        }

        if (matchingRules.isNotEmpty()) {
            shadeActions.executeRules(context, listener, sbn, matchingRules)
        }
        if (!shouldHide) return false
        if (matchingRules.any { it.hidesNotification() }) return true
        shadeActions.hideFromShadeOnMain(listener, sbn)
        return true
    }

    fun onRemoved(context: Context, sbn: StatusBarNotification, reason: Int) {
        ongoingFingerprints.remove(sbn.key)
        NotificationSbnCache.onRemoved(sbn, reason)
        if (!filterPreferences.readSnapshot().notificationHistoryEnabled) return

        val captured = NotificationHistoryIntentCapture.capture(sbn, context)
        val hasCapture = !captured.pendingIntentBase64.isNullOrBlank() ||
            !captured.intentUri.isNullOrBlank() ||
            !captured.intentParcelBase64.isNullOrBlank() ||
            !captured.extrasBase64.isNullOrBlank()
        if (!hasCapture) return
        Log.i(
            TAG,
            "Refresh capture on remove ${sbn.packageName} key=${sbn.key} " +
                "pi=${!captured.pendingIntentBase64.isNullOrBlank()} " +
                "uri=${!captured.intentUri.isNullOrBlank()} " +
                "parcel=${!captured.intentParcelBase64.isNullOrBlank()}",
        )
        historyRepository.updateCapture(sbn.key, captured)
    }

    private fun shouldHideNotification(context: Context, sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == context.packageName) return false
        return filterRepository.matches(sbn)
    }

    private companion object {
        const val TAG = "NotifFilterRecorder"
    }
}
