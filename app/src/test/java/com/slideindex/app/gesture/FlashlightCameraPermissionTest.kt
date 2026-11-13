package com.slideindex.app.gesture

import android.Manifest
import android.app.Application
import android.content.Context
import com.slideindex.app.overlay.searchpanel.CameraPermissionTrampolineActivity
import com.slideindex.app.ui.gesturepicker.gestureActionPermissionHintText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 手电筒动作的相机权限处理：已授权（含「仅在使用时允许」）时不得产生任何跳转，
 * 只有真正缺失时才拉起权限请求。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FlashlightCameraPermissionTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private fun grantCamera() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.CAMERA)
    }

    private fun nextStartedComponent(): String? =
        shadowOf(RuntimeEnvironment.getApplication())
            .nextStartedActivity
            ?.component
            ?.className

    @Test
    fun permissionHintHiddenWhileCameraGranted() {
        grantCamera()

        assertNull(gestureActionPermissionHintText(context, GestureAction.Flashlight()))
    }

    @Test
    fun ensureGrantedDoesNotNavigateWhileCameraGranted() {
        grantCamera()

        assertTrue(CameraPermissionTrampolineActivity.ensureGranted(context))

        assertNull(nextStartedComponent())
    }

    @Test
    fun ensureGrantedLaunchesTrampolineWhileCameraMissing() {
        assertTrue(CameraPermissionTrampolineActivity.ensureGranted(context))

        assertEquals(
            CameraPermissionTrampolineActivity::class.java.name,
            nextStartedComponent(),
        )
    }
}
