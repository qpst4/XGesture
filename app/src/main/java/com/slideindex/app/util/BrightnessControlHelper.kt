package com.slideindex.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import com.slideindex.app.gesture.ToggleMode
import com.slideindex.app.gesture.resolve
import com.slideindex.app.privilege.PrivilegeGateway
import kotlin.math.roundToInt



object BrightnessControlHelper {

    const val UI_NIGHT_MODE_KEY = "ui_night_mode"



    private const val UI_NIGHT_MODE_AUTO = 0

    private const val UI_NIGHT_MODE_NO = 1

    private const val UI_NIGHT_MODE_YES = 2



    fun hasAccess(context: Context): Boolean =
        PermissionHelper.canWriteSettings(context) ||
        TaskManagerUtil.hasPermission() ||
        SecureSettingsHelper.hasWriteSecureSettings(context)

    fun hasDarkModeAccess(context: Context): Boolean =
        TaskManagerUtil.hasPermission() ||
        PermissionHelper.canWriteSettings(context) ||
        SecureSettingsHelper.hasWriteSecureSettings(context)



    fun readAutoBrightnessEnabled(context: Context): Boolean {
        return Settings.System.getInt(
            context.applicationContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }

    /** 自动亮度开启时，滑条位置 0–1 与系统 adj（-1–1）互转。 */
    fun adjToFraction(adj: Float): Float = ((adj + 1f) / 2f).coerceIn(0f, 1f)

    fun fractionToAdj(fraction: Float): Float = (fraction.coerceIn(0f, 1f) * 2f - 1f)

    fun readAutoBrightnessAdj(context: Context): Float {
        val appContext = context.applicationContext
        val adj = runCatching {
            Settings.System.getFloat(appContext.contentResolver, SCREEN_AUTO_BRIGHTNESS_ADJ)
        }.getOrNull()
        return if (adj != null && adj in -1f..1f) adj else 0f
    }

    /** 当前亮度比例（0–1），仅读 Settings（主线程安全，勿走 shell）。 */
    fun readBrightnessFraction(context: Context): Float =
        readManualBrightnessFraction(context.applicationContext)

    /**
     * 尽量读到系统当前亮度；有 Shizuku 时走 shell。仅限后台线程调用（勿在主线程阻塞）。
     */
    fun readBrightnessFractionAccurate(context: Context): Float {
        val appContext = context.applicationContext
        if (TaskManagerUtil.hasPermission()) {
            val intResult = TaskManagerUtil.runShellCommandOutput(
                "settings",
                "get",
                "system",
                "screen_brightness",
            )
            if (intResult.success) {
                intResult.output.trim().toIntOrNull()?.let { level ->
                    rememberObservedLevel(level)
                    return levelToFraction(appContext, level)
                }
            }
        }
        return readManualBrightnessFraction(appContext)
    }

    /**
     * 当前亮度比例，读 `screen_brightness` 这一个 int。
     *
     * 写的时候两个通道都写（见 writeManualBrightnessFraction），读只认 int：系统侧改亮度
     * （设置页、快捷开关、自动亮度）一定落 int，float 在部分 ROM 上会残留旧版本写进去的值
     * 或同步器写一半的中间态；拿它当读数会让面板停在错误的位置。
     */
    private fun readManualBrightnessFraction(appContext: Context): Float {
        val level = Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            255,
        )
        rememberObservedLevel(level)
        return levelToFraction(appContext, level)
    }

    private fun levelToFraction(context: Context, level: Int): Float {
        val max = brightnessMax(context).coerceAtLeast(level)
        val min = writableMin(context, max)
        if (max <= min) return 0f
        return ((level - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    }

    /**
     * 量程上限：三个来源**取最大**，不再"取第一个能读到的"。
     *
     * 1. framework 配置 `config_screenBrightnessSettingMaximum`；
     * 2. `PowerManager.BRIGHTNESS_ON`（@hide，反射）——AOSP 里是常量 255，小米等厂商会改写成
     *    真实量程（1023/2047/4095/8192），所以两边都不能单独信；
     * 3. 运行期观察到的最大读数。
     *
     * 为什么取最大：量程**估小**是致命的——写入值被压到真实量程的一小截（8191 的机器上只发
     * 1..255，等于把屏幕按黑）；估大的代价只是滑条顶部一段用不满。旧实现"优先 BRIGHTNESS_ON，
     * 读不到才看 config"，在 BRIGHTNESS_ON 没被厂商改写的机器上会把 config 变成死代码。
     *
     * 不再按当前亮度倒猜：那段逻辑四档里只有一档碰巧成立，会把量程算错一个数量级。
     */
    @SuppressLint("DiscouragedApi")
    private fun brightnessMax(context: Context): Int {
        var resolved = BRIGHTNESS_ON_VALUE
        configuredMax(context).takeIf { it > 0 }?.let { resolved = maxOf(resolved, it) }
        brightnessOnValue().takeIf { it > 0 }?.let { resolved = maxOf(resolved, it) }
        if (observedMaxLevel > resolved) resolved = observedMaxLevel
        return resolved
    }

    @SuppressLint("DiscouragedApi")
    private fun configuredMax(context: Context): Int = runCatching {
        val res = context.resources
        val id = res.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        if (id != 0) res.getInteger(id) else 0
    }.getOrDefault(0)

    /** `PowerManager.BRIGHTNESS_ON`（@hide）：反射一次并缓存，读不到就返回 0。 */
    private fun brightnessOnValue(): Int {
        if (brightnessOnCached >= 0) return brightnessOnCached
        val value = runCatching {
            val field = android.os.PowerManager::class.java.getDeclaredField("BRIGHTNESS_ON")
            field.isAccessible = true
            field.getInt(null)
        }.getOrDefault(0)
        brightnessOnCached = value
        return value
    }

    private fun rememberObservedLevel(level: Int) {
        if (level > observedMaxLevel) observedMaxLevel = level
    }

    @SuppressLint("DiscouragedApi")
    private fun brightnessMin(context: Context): Int {
        val res = context.resources
        val id = res.getIdentifier("config_screenBrightnessSettingMinimum", "integer", "android")
        return if (id != 0) res.getInteger(id) else BRIGHTNESS_FLOOR
    }

    /**
     * 滑条到底时写到的下限。
     *
     * 小米官方适配口径：量程 ≤255 的机型最小值固定为 1；多级调光（>255）的机型才用
     * `config_screenBrightnessSettingMinimum`（[亮度适配说明](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1633)）。
     *
     * 两头都不能省：
     * - 一律用 config：那是有机型会写成 12 的保守值，滑条到底会比系统条到底亮一截；
     * - 一律用 1：8191 量程上 1 只是 0.01%，等于把屏幕按黑。
     *
     * 也不写 0：AOSP 里 `PowerManager.BRIGHTNESS_OFF = 0`，BrightnessSynchronizer 会把 0
     * 当成「关屏/无效」而不是「最暗」。
     */
    private fun writableMin(context: Context, max: Int): Int {
        if (max <= BRIGHTNESS_ON_VALUE) return BRIGHTNESS_FLOOR
        return brightnessMin(context).coerceAtLeast(BRIGHTNESS_FLOOR)
    }

    /**
     * 写入亮度。**双写**：Settings（快，1~3ms）每次必写；shell（慢，5~20ms）按调用方的节流旁路写。
     *
     * 为什么要双写：哪条通道真的"进屏幕"是 ROM 决定的，而且**没有任何反馈可以判断**——
     * - Meizu/Flyme：应用身份写 Settings 就生效，快路足够（拖动可以到 30~60Hz）；
     * - ColorOS 16：应用身份写 Settings 返回成功、值也进库，但屏幕不动，只有 shell 身份写才生效。
     * 写入永远返回成功，所以"失败再切"在代码里做不到，只能两条都发：谁能进屏幕谁进。
     *
     * **三个 key 必须一起写**：真机实测（Meizu 21 / Flyme）自动亮度下单独写
     * `screen_auto_brightness_adj` 屏幕纹丝不动，只有带上 `screen_brightness` 才跟手；
     * 而只带 int、不带 `screen_brightness_float` 时，Android 12+ 的 DisplayManager 可能
     * 根本吃不到这个值（见 writeManualBrightnessFraction）。
     *
     * @param duringGesture 滑动过程中为 true：只降日志噪。
     * @param commitAutoAdj 手势结束时为 true：用 caller 指定的通道提交，保证持久。
     * @param includeShell  本次是否**额外**走一次 shell。调用方按 100ms 节奏置 true。
     */
    fun writeBrightnessFraction(
        context: Context,
        fraction: Float,
        duringGesture: Boolean = false,
        commitAutoAdj: Boolean = false,
        includeShell: Boolean = true,
    ): Boolean {
        if (!hasAccess(context)) return false
        val appContext = context.applicationContext
        val clamped = fraction.coerceIn(0f, 1f)
        val adj = if (readAutoBrightnessEnabled(appContext)) fractionToAdj(clamped) else null
        return writeManualBrightnessFraction(
            context = appContext,
            clamped = clamped,
            adj = adj,
            includeShell = includeShell,
            logSuccess = !duringGesture,
        )
    }

    fun applyBrightnessFraction(context: Context, fraction: Float): Boolean =
        writeBrightnessFraction(context, fraction, commitAutoAdj = readAutoBrightnessEnabled(context))

    private fun writeManualBrightnessFraction(
        context: Context,
        clamped: Float,
        adj: Float? = null,
        includeShell: Boolean = true,
        logSuccess: Boolean = true,
    ): Boolean {
        val appContext = context.applicationContext
        val max = brightnessMax(appContext)
        val min = writableMin(appContext, max)
        val targetIntLevel = if (max <= min) {
            min
        } else {
            (min + (max - min) * clamped).roundToInt().coerceIn(min, max)
        }
        val targetFloat = clamped.coerceIn(0f, 1f)
        val targetAdj = adj?.coerceIn(-1f, 1f)

        var intOk = false
        var floatOk = false
        var shellOk = false
        val canWriteSettings = PermissionHelper.canWriteSettings(appContext) ||
            SecureSettingsHelper.hasWriteSecureSettings(appContext)

        // ① 快路：Settings。每次调用都写（1~3ms），Settings 有效的 ROM 靠它跟手。
        //
        // **int 与 float 一起写，顺序固定 int → float**：
        // - `screen_brightness`(int)：系统设置页、部分 ROM 的自动亮度只认它；
        // - `screen_brightness_float`(0–1)：Android 12+ DisplayManager 直接吃的那个值。
        //   只写 int 时屏幕亮不亮，取决于 ROM 让不让 BrightnessSynchronizer 换算 ——
        //   ColorOS / HyperOS 上经常不让，表现就是「调一下、闪一下、立刻弹回原亮度」。
        // 两个 key 的量纲必须一致（float = (int - min) / (max - min)）：量程取对之后，
        // 同步器换算出来的和我们写的是同一个值，不会自己覆盖自己。
        if (canWriteSettings) {
            intOk = runCatching {
                Settings.System.putInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    targetIntLevel,
                )
            }.getOrDefault(false)
            floatOk = runCatching {
                Settings.System.putFloat(
                    appContext.contentResolver,
                    SCREEN_BRIGHTNESS_FLOAT,
                    targetFloat,
                )
            }.getOrDefault(false)
            if (targetAdj != null) {
                runCatching {
                    Settings.System.putFloat(
                        appContext.contentResolver,
                        SCREEN_AUTO_BRIGHTNESS_ADJ,
                        targetAdj,
                    )
                }
            }
        }
        val settingsOk = intOk || floatOk

        // ② 兼容路：shell（身份不同，ColorOS 只认它）。按调用方节流下发，或用 Settings 写不动时兜底。
        //    写同一组 key：少写一个就少一条能进屏幕的路。
        if (includeShell || !settingsOk) {
            if (TaskManagerUtil.hasPermission()) {
                val command = buildString {
                    append("settings put system screen_brightness ").append(targetIntLevel)
                    append(" && settings put system ").append(SCREEN_BRIGHTNESS_FLOAT)
                        .append(' ').append(targetFloat)
                    if (targetAdj != null) {
                        append(" && settings put system ")
                        append(SCREEN_AUTO_BRIGHTNESS_ADJ).append(' ').append(targetAdj)
                    }
                }
                shellOk = TaskManagerUtil.runShellCommandLine(
                    command = command,
                    useRoot = PrivilegeGateway.isRootMode(),
                ).success
            }
        }

        val synced = settingsOk || shellOk
        val path = when {
            settingsOk && shellOk -> "settings+shell"
            settingsOk -> "settings"
            shellOk -> "shell"
            else -> "none"
        }
        lastWritePath = path
        if (synced && logSuccess) {
            Log.d(
                TAG,
                "brightness int=$targetIntLevel float=$targetFloat (fraction=$clamped, " +
                    "range=$min..$max, adj=$targetAdj, path=$path, intOk=$intOk, " +
                    "floatOk=$floatOk, shellOk=$shellOk, includeShell=$includeShell)",
            )
        }
        return synced
    }

    /** 诊断用：最近一次实际走通的通道。 */
    @Volatile
    var lastWritePath: String = "-"
        private set

    /** 诊断用：量程 / 当前值（int 与 float 两条）/ 亮度模式 / 写权限，一次说清"这条机器上能怎么写"。 */
    fun describeScale(context: Context): String {
        val appContext = context.applicationContext
        val max = brightnessMax(appContext)
        val min = writableMin(appContext, max)
        val level = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        }.getOrDefault(-1)
        // 两个通道是否同步，是"写入到底进没进屏幕"最直接的证据：差一个量级，
        // 说明这台机器上只有一条通道在动。
        val floatLevel = runCatching {
            Settings.System.getFloat(appContext.contentResolver, SCREEN_BRIGHTNESS_FLOAT)
        }.getOrNull()
        return "range=$min..$max level=$level float=$floatLevel " +
            "auto=${readAutoBrightnessEnabled(appContext)} " +
            "canWriteSettings=${PermissionHelper.canWriteSettings(appContext)} " +
            "shizuku=${TaskManagerUtil.hasPermission()}"
    }



    fun toggleAutoBrightness(context: Context): Boolean? {

        if (!hasAccess(context)) return null

        val appContext = context.applicationContext

        val enable = !readAutoBrightnessEnabled(appContext)

        val mode = if (enable) {

            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

        } else {

            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL

        }

        return if (setAutoBrightnessMode(appContext, mode)) enable else null

    }



    /** 把自动亮度设成 [mode] 要求的状态（幂等）；null = 没权限 / 写入未生效。 */
    fun applyAutoBrightness(context: Context, mode: ToggleMode): Boolean? {

        if (!hasAccess(context)) return null

        val appContext = context.applicationContext

        val current = readAutoBrightnessEnabled(appContext)

        val target = mode.resolve(current)

        if (current == target) return true

        val modeValue = if (target) {

            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

        } else {

            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL

        }

        return if (setAutoBrightnessMode(appContext, modeValue)) target else null

    }

    fun readDarkModeEnabled(context: Context): Boolean {

        val appContext = context.applicationContext

        return when (

            Settings.Secure.getInt(

                appContext.contentResolver,

                UI_NIGHT_MODE_KEY,

                UI_NIGHT_MODE_AUTO,

            )

        ) {

            UI_NIGHT_MODE_YES -> true

            UI_NIGHT_MODE_NO -> false

            else -> {

                val nightMask = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

                nightMask == Configuration.UI_MODE_NIGHT_YES

            }

        }

    }



    fun toggleDarkMode(context: Context): Boolean? {

        if (!hasDarkModeAccess(context)) return null

        val appContext = context.applicationContext

        val enable = !readDarkModeEnabled(appContext)

        return if (setUiNightMode(appContext, enable)) enable else null

    }

    /** 把夜间模式设成 [mode] 要求的状态（幂等）；null = 没通道 / 写入失败。 */
    fun applyDarkMode(context: Context, mode: ToggleMode): Boolean? {

        if (!hasDarkModeAccess(context)) return null

        val appContext = context.applicationContext

        val current = readDarkModeEnabled(appContext)

        val target = mode.resolve(current)

        if (current == target) return true

        return if (setUiNightMode(appContext, target)) target else null

    }



    private fun setAutoBrightnessMode(context: Context, mode: Int): Boolean {

        var synced = false

        if (TaskManagerUtil.hasPermission()) {

            synced = TaskManagerUtil.runShellCommand(

                "settings",

                "put",

                "system",

                "screen_brightness_mode",

                mode.toString(),

            )

        }

        if (PermissionHelper.canWriteSettings(context) || SecureSettingsHelper.hasWriteSecureSettings(context)) {
            runCatching {
                synced = Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    mode,
                ) || synced
            }.onFailure { error ->
                Log.w(TAG, "auto brightness write failed", error)
            }
        }

        return synced

    }



    private fun setUiNightMode(context: Context, enableDark: Boolean): Boolean {

        val mode = if (enableDark) UI_NIGHT_MODE_YES else UI_NIGHT_MODE_NO

        val nightArg = if (enableDark) "yes" else "no"



        if (TaskManagerUtil.hasPermission()) {

            val shellCommands = listOf(

                arrayOf("cmd", "uimode", "night", nightArg),

                arrayOf("settings", "put", "secure", UI_NIGHT_MODE_KEY, mode.toString()),

                arrayOf("settings", "put", "system", UI_NIGHT_MODE_KEY, mode.toString()),

            )

            for (command in shellCommands) {

                if (TaskManagerUtil.runShellCommand(*command)) {

                    Log.d(TAG, "dark mode set via shell: ${command.joinToString(" ")}")

                    return true

                }

            }

        }



        runCatching {

            if (Settings.Secure.putInt(context.contentResolver, UI_NIGHT_MODE_KEY, mode)) {

                Log.d(TAG, "dark mode set via Settings.Secure.putInt mode=$mode")

                return true

            }

        }.onFailure { error ->

            Log.w(TAG, "ui night mode write failed", error)

        }

        return false

    }



    private const val TAG = "BrightnessControlHelper"

    /** 与 AOSP BrightnessController 一致，自动亮度下用户偏移量。 */
    private const val SCREEN_AUTO_BRIGHTNESS_ADJ = "screen_auto_brightness_adj"

    /**
     * 0–1 的亮度通道，Android 12+ 屏亮由它直接驱动。
     *
     * 用字面量而不是 `Settings.System.SCREEN_BRIGHTNESS_FLOAT`：那个常量是 @hide / API 31+，
     * 编译期拿不到，字符串与框架里的完全一致。
     */
    private const val SCREEN_BRIGHTNESS_FLOAT = "screen_brightness_float"

    /**
     * 滑条到底时写到的下限。
     *
     * 不用 `config_screenBrightnessSettingMinimum`：那是 ROM 的保守声明（实测见过 12），
     * 拿它当 0 会让滑条到底比系统条到底亮一截（小米文档：量程 ≤255 的机型最小值是 1）。
     * 也不用 0：AOSP 里 `PowerManager.BRIGHTNESS_OFF = 0`，BrightnessSynchronizer 会把 0
     * 当成「关屏/无效」而不是「最暗」，写进去可能完全不生效。
     */
    private const val BRIGHTNESS_FLOOR = 1

    /** `PowerManager.BRIGHTNESS_ON`（隐藏常量，值恒为 255）：int 量程不超过它时属于单级调光。 */
    private const val BRIGHTNESS_ON_VALUE = 255

    /** 反射读到的 `PowerManager.BRIGHTNESS_ON`；-1 表示还没读过。 */
    @Volatile
    private var brightnessOnCached = -1

    /** 运行期观察到的最大亮度读数：只用来抬高上限，不压低。 */
    @Volatile
    private var observedMaxLevel = 0

}


