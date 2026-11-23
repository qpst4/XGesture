package com.slideindex.app.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationCodeExtractorTest {

    @Test
    fun extract_keywordFallback_findsCodeAfterChineseKeyword() {
        val result = VerificationCodeExtractor.extract(
            packageName = "com.sms.app",
            title = "登录验证",
            text = "您的验证码是：884812，请勿泄露。",
            config = OtpExtractionConfig(),
        )

        assertEquals("884812", result.code)
        assertTrue(result.attempted)
    }

    @Test
    fun extract_withoutKeyword_returnsAttemptedWithoutCode() {
        val result = VerificationCodeExtractor.extract(
            packageName = "com.sms.app",
            title = "天气",
            text = "今日晴",
            config = OtpExtractionConfig(),
        )

        assertNull(result.code)
        assertTrue(result.attempted)
    }

    @Test
    fun extract_matchRule_usesRegexCaptureGroup() {
        val rule = OtpMatchRule(
            id = "bank",
            name = "银行",
            keyword = "验证码",
            regex = "验证码[：:\\s为]?(\\d{4,8})",
            packageName = "com.bank.app",
            isOfficial = true,
            enabled = true,
        )
        val result = VerificationCodeExtractor.extract(
            packageName = "com.bank.app",
            title = "",
            text = "验证码：556677",
            config = OtpExtractionConfig(matchRules = listOf(rule)),
        )

        assertEquals("556677", result.code)
        assertEquals("银行", result.ruleName)
    }

    @Test
    fun extract_matchRule_wrongPackage_skipsRule() {
        val rule = OtpMatchRule(
            id = "bank",
            name = "银行",
            keyword = "验证码",
            regex = "(\\d{4,8})",
            packageName = "com.bank.app",
            isOfficial = true,
            enabled = true,
        )
        val result = VerificationCodeExtractor.extract(
            packageName = "com.other.app",
            title = "",
            text = "验证码 123456",
            config = OtpExtractionConfig(matchRules = listOf(rule)),
        )

        assertEquals("123456", result.code)
        assertNull(result.ruleName)
    }

    @Test
    fun extract_blankInput_notAttempted() {
        val result = VerificationCodeExtractor.extract(
            packageName = "com.app",
            title = "",
            text = "",
            config = OtpExtractionConfig(),
        )

        assertNull(result.code)
        assertFalse(result.attempted)
    }

    @Test
    fun extract_truncatedKeywordInNotification_findsCode() {
        val result = VerificationCodeExtractor.extract(
            packageName = "com.android.mms",
            title = "深圳市税务局",
            text = "…证码是：177712（有效期为5分钟）…",
            config = OtpExtractionConfig(),
        )

        assertEquals("177712", result.code)
        assertTrue(result.attempted)
    }

    @Test
    fun extract_aggregatedNotificationWithValidityHint_findsCode() {
        val result = VerificationCodeExtractor.extract(
            packageName = "com.android.mms",
            title = "深圳市税务局",
            text = "[2条]…177712（有效期为5分钟）…",
            config = OtpExtractionConfig(),
        )

        assertEquals("177712", result.code)
        assertTrue(result.attempted)
    }

    @Test
    fun extract_truncatedKeyword_rejectsPasswordAndIdNumber() {
        val password = VerificationCodeExtractor.extract(
            packageName = "com.app",
            title = "",
            text = "您的密码是 1234 请妥善保管",
            config = OtpExtractionConfig(),
        )
        val idNumber = VerificationCodeExtractor.extract(
            packageName = "com.app",
            title = "",
            text = "您的身份证码 1234 已更新",
            config = OtpExtractionConfig(),
        )

        assertNull(password.code)
        assertNull(idNumber.code)
    }

    @Test
    fun extract_multipleCandidatesWithoutContext_doesNotGuess() {
        val result = VerificationCodeExtractor.extract(
            packageName = "com.app",
            title = "",
            text = "订单金额 1777 元，单号 12345678",
            config = OtpExtractionConfig(),
        )

        assertNull(result.code)
        assertTrue(result.attempted)
    }

    @Test
    fun extract_dateTimeCandidate_isExcluded() {
        val result = VerificationCodeExtractor.extract(
            packageName = "com.app",
            title = "",
            text = "验证码 2026-11-23 12:34 已发送，请注意查收",
            config = OtpExtractionConfig(),
        )

        assertNull(result.code)
        assertTrue(result.attempted)
    }

    @Test
    fun extract_spaceSeparatedCode_isNormalized() {
        val result = VerificationCodeExtractor.extract(
            packageName = "com.app",
            title = "",
            text = "您的验证码是 1 2 3 4 5 6，请勿泄露",
            config = OtpExtractionConfig(),
        )

        assertEquals("123456", result.code)
    }

    @Test
    fun extract_unicodeDigits_areNormalized() {
        val result = VerificationCodeExtractor.extract(
            packageName = "com.app",
            title = "",
            text = "您的验证码是：١٢٣٤٥٦，请勿泄露",
            config = OtpExtractionConfig(),
        )

        assertEquals("123456", result.code)
    }
}
