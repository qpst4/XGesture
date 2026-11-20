package com.slideindex.app.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * 守住 [AppSettings] 及其分片的参数规模。
 *
 * Kotlin 为「带默认值的构造器」和 `copy()` 生成合成方法，调用点编译成 DEX 的 range 形式
 * invoke 指令，其寄存器计数字段只有 8 位。一旦所需寄存器超过 255，D8/R8 不会报错，
 * 计数会静默截断（如 257 → 1），直到运行期 ART 校验器抛 VerifyError 才暴露，
 * 且崩溃点通常在毫不相关的注入类上，极难定位。
 *
 * 因此新增设置项必须加进对应分片，而不是直接挂到 [AppSettings] 主构造上。
 */
class AppSettingsDexRegisterLimitTest {

    /** 留出余量，不贴着 255 的硬上限，便于在触顶前发现。 */
    private val warnThreshold = 200

    private val settingsClasses = listOf(
        AppSettings::class.java,
        EdgeTriggerSettings::class.java,
        LauncherSettings::class.java,
        FloatingPointerSettings::class.java,
        FloatBallSettings::class.java,
        ClipboardSettings::class.java,
        SearchPanelSettings::class.java,
        OverlaySettings::class.java,
    )

    @Test
    fun `synthetic default constructors stay within dex register budget`() {
        settingsClasses.forEach { clazz ->
            val ctor = clazz.declaredConstructors.maxByOrNull { it.parameterCount }!!
            val registers = registerCount(ctor)
            assertTrue(
                "${clazz.simpleName} 的默认构造器需要 $registers 个寄存器（阈值 $warnThreshold，DEX 硬上限 255）。" +
                    "请把新增字段移入分片，而不是加在主构造上。",
                registers <= warnThreshold,
            )
        }
    }

    @Test
    fun `synthetic copy default methods stay within dex register budget`() {
        settingsClasses.forEach { clazz ->
            val copyDefault = clazz.declaredMethods.first { it.name == "copy\$default" }
            val registers = registerCount(copyDefault)
            assertTrue(
                "${clazz.simpleName}.copy() 需要 $registers 个寄存器（阈值 $warnThreshold，DEX 硬上限 255）。" +
                    "请把新增字段移入分片，而不是加在主构造上。",
                registers <= warnThreshold,
            )
        }
    }

    /** 实例方法额外占用一个 `this` 寄存器；long/double 各占两个。 */
    private fun registerCount(ctor: Constructor<*>): Int =
        1 + ctor.parameterTypes.sumOf { it.registerWidth() }

    private fun registerCount(method: Method): Int =
        method.parameterTypes.sumOf { it.registerWidth() }

    private fun Class<*>.registerWidth(): Int =
        if (this == Long::class.javaPrimitiveType || this == Double::class.javaPrimitiveType) 2 else 1
}
