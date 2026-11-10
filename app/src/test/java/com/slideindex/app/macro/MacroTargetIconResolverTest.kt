package com.slideindex.app.macro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `MacroTargetIconResolver` 的目标包解析（纯函数部分）。
 *
 * 回归点：快速启动器只拿到 macroId，图标要靠宏表反查目标 App；
 * 分身目标存成 `包名\u0002userId`（见 `AppTarget`），图标必须按裸包名取。
 */
class MacroTargetIconResolverTest {

    private fun macro(target: String) = Macro(
        id = "m-1",
        name = "签到",
        targetPackage = target,
    )

    @Test
    fun plainPackage_isReturnedAsIs() {
        assertEquals("com.tencent.mm", MacroTargetIconResolver.targetPackageOf(macro("com.tencent.mm")))
    }

    @Test
    fun cloneTarget_dropsUserIdSuffix() {
        assertEquals(
            "com.tencent.mm",
            MacroTargetIconResolver.targetPackageOf(macro("com.tencent.mm\u000210")),
        )
    }

    @Test
    fun placeholderWithoutTarget_hasNoIconTarget() {
        assertNull(MacroTargetIconResolver.targetPackageOf(macro("")))
        assertNull(MacroTargetIconResolver.targetPackageOf(macro("   ")))
        assertNull(MacroTargetIconResolver.targetPackageOf(null))
    }
}
