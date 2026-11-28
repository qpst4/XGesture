package com.slideindex.app.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.view.accessibility.AccessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityFeedbackRegistrationTest {
    @Test
    fun androidAdvertisesPickServiceAsProvidingSpokenFeedback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val component = ComponentName(context, SlideIndexAccessibilityService::class.java)
        val info = manager.installedAccessibilityServiceList.single { service ->
            ComponentName.unflattenFromString(service.id) == component
        }

        assertTrue(
            "Apps looking for spoken feedback services can recognize XGesture's Read aloud action",
            (info.feedbackType and AccessibilityServiceInfo.FEEDBACK_SPOKEN) != 0,
        )
        assertTrue((info.feedbackType and AccessibilityServiceInfo.FEEDBACK_GENERIC) != 0)
    }
}
