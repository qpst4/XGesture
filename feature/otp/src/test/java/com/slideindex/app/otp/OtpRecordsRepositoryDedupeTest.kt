package com.slideindex.app.otp

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OtpRecordsRepositoryDedupeTest {

    @Test
    fun record_dedupesSameCodeAcrossPackagesWithinWindow() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = OtpRecordsRepository(context)
        delay(200)

        repository.recordSuspend(
            code = "123456",
            packageName = "+8613800138000",
            title = "SMS",
            text = "code 123456",
            timestampMs = 1_700_000_000_000L,
        )
        repository.recordSuspend(
            code = "123456",
            packageName = "com.android.mms",
            title = "SMS",
            text = "code 123456",
            timestampMs = 1_700_000_000_500L,
        )

        assertEquals(1, repository.records.value.size)
        assertEquals("+8613800138000", repository.records.value.first().packageName)
    }

    @Test
    fun record_dedupesSameCodeAcrossNotificationAndSmsCategories() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = OtpRecordsRepository(context)
        delay(200)

        repository.recordSuspend(
            code = "556677",
            packageName = "com.android.mms",
            title = "SMS",
            text = "code 556677",
            timestampMs = 1_700_000_100_000L,
            category = OtpRecordCategory.APP_NOTIFY,
        )
        repository.recordSuspend(
            code = "556677",
            packageName = "com.android.phone",
            title = "SMS",
            text = "code 556677",
            timestampMs = 1_700_000_100_400L,
            category = OtpRecordCategory.CODE,
        )

        assertEquals(1, repository.records.value.size)
        assertEquals(OtpRecordCategory.APP_NOTIFY, repository.records.value.first().category)
    }

    @Test
    fun record_allowsSameCodeAfterWindowExpires() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = OtpRecordsRepository(context)
        delay(200)

        val firstResult = repository.recordSuspend(
            code = "888888",
            packageName = "com.test.app",
            title = "Test",
            text = "code 888888",
            timestampMs = 1_000L,
        )
        val secondResult = repository.recordSuspend(
            code = "888888",
            packageName = "com.test.app",
            title = "Test",
            text = "code 888888",
            timestampMs = 1_000L + OtpRecordsRepository.DEDUPE_WINDOW_MS + 1,
        )

        assertTrue(firstResult.isSuccess)
        assertTrue(secondResult.isSuccess)
        assertEquals(2, repository.records.value.size)
    }
}
