package com.slideindex.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 自由小窗 ROM 判定与厂商几何缩放补偿辅助类（对齐 GestureEVO 的 Rom.kt 实现）。
 */
object FreeWindowRomHelper {
    private const val TAG = "FreeWindowRomHelper"
    private const val KEY_VERSION_MIUI = "ro.miui.ui.version.name"

    private val propCache = ConcurrentHashMap<String, String>()

    fun isXiaomiDevice(): Boolean {
        val m = Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT)
        val b = Build.BRAND.orEmpty().lowercase(Locale.ROOT)
        return m.contains("xiaomi") ||
            b.contains("xiaomi") ||
            b.contains("redmi") ||
            b.contains("poco")
    }

    @get:SuppressLint("PrivateApi")
    private val systemPropertiesGetMethod by lazy {
        try {
            val clz = Class.forName("android.os.SystemProperties")
            clz.getMethod("get", String::class.java, String::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    fun isPad(context: Context): Boolean {
        return ((context.resources.configuration.screenLayout and
            Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE)
    }

    /**
     * 判断当前设备是否为 MIUI 13+ 或 HyperOS。
     */
    fun isMiui13Plus(context: Context): Boolean {
        if (!isXiaomiDevice()) return false
        val miuiVersion = getProp(KEY_VERSION_MIUI)
        if (miuiVersion.isBlank()) {
            // 没有 miui 属性但厂商是小米（如部分海外版或新 HyperOS），默认按现代系统策略处理
            return true
        }
        val ver = parseVersion(miuiVersion)
        return ver >= 13 || ver >= 130 || ver == -1
    }

    /**
     * 适配小米/红米/POCO 手机系统的自由小窗几何缩放补偿系数（直接对齐 GestureEVO）：
     *
     * - 竖屏：系统底层强制缩放为 0.70x，因此补偿系数固定为 1.42f (1 / 0.70)。
     * - 横屏：
     *   1. 平板 / 折叠屏展开态（长宽比 < 1.6、最小边长 >= 1600 或 isPad）：垂直空间充裕，系统维持 1.42f；
     *   2. 普通狭长手机横屏：纵向空间被极限挤压，系统强制进一步缩小至 0.55x，因此补偿系数为 1.80f (1 / 0.55)。
     */
    fun calculateMiuiCompensation(
        context: Context,
        screenWidth: Int,
        screenHeight: Int,
        isPortrait: Boolean,
    ): Float {
        if (isPortrait) return 1.42f
        val minD = minOf(screenWidth, screenHeight)
        val maxD = maxOf(screenWidth, screenHeight)
        if (minD <= 0) return 1.42f
        val aspectRatio = maxD.toFloat() / minD.toFloat()
        val isLargeScreen = isPad(context) || aspectRatio < 1.6f || minD >= 1600
        return if (isLargeScreen) 1.42f else 1.8f
    }

    private fun parseVersion(prop: String): Int {
        return try {
            val matcher = Pattern.compile("\\d+").matcher(prop)
            if (matcher.find()) {
                matcher.group().toInt()
            } else {
                -1
            }
        } catch (_: Exception) {
            -1
        }
    }

    fun getProp(name: String): String {
        return propCache.getOrPut(name) {
            val reflected = try {
                systemPropertiesGetMethod?.invoke(null, name, "") as? String
            } catch (_: Throwable) {
                null
            }
            if (!reflected.isNullOrBlank()) {
                reflected
            } else {
                getPropByExec(name)
            }
        }
    }

    private fun getPropByExec(name: String): String {
        var line = ""
        var input: BufferedReader? = null
        try {
            val p = Runtime.getRuntime().exec("getprop $name")
            input = BufferedReader(InputStreamReader(p.inputStream), 1024)
            line = input.readLine().orEmpty()
        } catch (ex: Exception) {
            Log.e(TAG, "Unable to read prop $name", ex)
        } finally {
            try {
                input?.close()
            } catch (_: Exception) {
            }
        }
        return line
    }
}
