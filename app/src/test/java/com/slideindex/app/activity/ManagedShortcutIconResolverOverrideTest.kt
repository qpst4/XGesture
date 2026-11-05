package com.slideindex.app.activity

import android.content.Context
import com.slideindex.app.ui.ActionIconKeyCodec
import com.slideindex.app.ui.GlobalIconStore
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 快捷方式图标的优先级：图标库里给宿主应用设的全局覆盖，必须盖住快捷方式
 * 从 activity / 系统快捷方式继承来的原始图标。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ManagedShortcutIconResolverOverrideTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val hostPackage = "com.example.host"

    @After
    fun tearDown() {
        GlobalIconStore.setAppIcon(context, hostPackage, null)
    }

    @Test
    fun appIconOverrideWinsOverShortcutOwnIcon() {
        GlobalIconStore.setAppIcon(context, hostPackage, ActionIconKeyCodec.text("替"))

        val drawable = ManagedShortcutIconResolver.drawableForManaged(
            context,
            ActivityShortcut.component(
                label = "宿主页面",
                packageName = hostPackage,
                activityClassName = "$hostPackage.MainActivity",
            ),
        )

        assertNotNull("设置了全局图标覆盖后，快捷方式必须显示覆盖图", drawable)
    }
}
