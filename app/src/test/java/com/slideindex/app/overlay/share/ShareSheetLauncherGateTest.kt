package com.slideindex.app.overlay.share

import android.content.Context
import com.slideindex.app.overlay.panels.PanelRuntimeHooks
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 内置分享面板的总开关必须真的能决定行为。
 *
 * 回归点：share 整组迁入 `:feature:overlay-panels`（498a3b1c）时，[ShareSheetLauncher.isEnabled]
 * 从"直读设置仓库"换成了 `PanelRuntimeHooks.isShareSheetEnabled`，但 `:app` 从未给那个槽位赋值。
 * 结果 `isEnabled` 恒为 false：开关打开后所有浮窗分享仍然弹系统 chooser（v1.64.0 起）。
 *
 * 放在 `:app` 的测试里，是因为要验的正是"`:app` 绑定的设置快照"这条链路本身
 * （与 `CornerSlotIconOverrideTest` 同一类做法）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ShareSheetLauncherGateTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PanelRuntimeHooks.settingsSnapshot = null
    }

    @Test
    fun enabledWhenSettingsSnapshotSaysOn() {
        PanelRuntimeHooks.settingsSnapshot = { _ ->
            AppSettings(floatBall = FloatBallSettings(shareSheetEnabled = true))
        }

        assertTrue(ShareSheetLauncher.isEnabled(context))
    }

    @Test
    fun disabledWhenSettingsSnapshotSaysOff() {
        PanelRuntimeHooks.settingsSnapshot = { _ ->
            AppSettings(floatBall = FloatBallSettings(shareSheetEnabled = false))
        }

        assertFalse(ShareSheetLauncher.isEnabled(context))
    }

    /** 钩子没接上（启动流程没跑到 / 未来再漏绑）时必须按"关"处理并退回系统面板，不能崩。 */
    @Test
    fun disabledWhenSettingsSnapshotMissing() {
        PanelRuntimeHooks.settingsSnapshot = null

        assertFalse(ShareSheetLauncher.isEnabled(context))
    }

    /** 仓库还没就绪时快照可能是 null：同样按"关"处理。 */
    @Test
    fun disabledWhenSettingsSnapshotReturnsNull() {
        PanelRuntimeHooks.settingsSnapshot = { _ -> null }

        assertFalse(ShareSheetLauncher.isEnabled(context))
    }
}
