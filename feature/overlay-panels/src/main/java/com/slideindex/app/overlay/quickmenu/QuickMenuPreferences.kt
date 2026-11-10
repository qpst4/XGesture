package com.slideindex.app.overlay.quickmenu

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.slideindex.app.quickmenu.CurrencyRateEngine
import com.slideindex.app.quickmenu.PasswordGenerator
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.quickMenuPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "slide_index_quick_menu_prefs",
)

private const val DEFAULT_PASSWORD_LENGTH = 16

/** 汇率换算的默认货币对（与界面上原来写死的默认一致）。 */
private const val DEFAULT_CURRENCY_FROM = "USD"
private const val DEFAULT_CURRENCY_TO = "CNY"

/**
 * 「快捷菜单」里需要**记住**的选择。
 *
 * 起因是用户反馈"密码生成中的 3 个开关没有记忆"：这些选择原先只存在 Composable 的 `remember`
 * 里，面板一关就回到默认值（连返回工具列表再进来都会重置，因为那一页的组合被销毁了）。
 *
 * 只存**选择**，不存**内容**：扫到的二维码原文、输入框里的文本属于会话数据，不该跨次留存。
 */
@Serializable
data class QuickMenuPrefs(
    val passwordLength: Int = DEFAULT_PASSWORD_LENGTH,
    val passwordDigits: Boolean = true,
    val passwordSymbols: Boolean = false,
    val passwordAvoidAmbiguous: Boolean = false,
    /** 单位换算：记住类别与两端单位（id 由 [com.slideindex.app.quickmenu.UnitConversionEngine] 定义）。 */
    val unitCategory: String? = null,
    val unitFromId: String? = null,
    val unitToId: String? = null,
    /** 汇率换算：记住两端货币。 */
    val currencyFrom: String? = DEFAULT_CURRENCY_FROM,
    val currencyTo: String? = DEFAULT_CURRENCY_TO,
    /** 文本工具：记住上次的模式（枚举名）。 */
    val textToolMode: String? = null,
    /** 首页模块顺序（[QuickMenuHomeModule] 枚举名）；空 = 默认 最近使用 → 文件 → 工具。 */
    val homeModuleOrder: List<String> = emptyList(),
)

/**
 * 读取时的收敛规则（纯函数，可单测）。
 *
 * 包名/枚举/引擎都可能变：密码长度夹到合法区间，货币代码必须真的存在，否则宁可退回默认值，
 * 也不要让界面拿着一个查不到的代码去算汇率。
 */
object QuickMenuPrefsCodec {

    fun sanitize(prefs: QuickMenuPrefs): QuickMenuPrefs = prefs.copy(
        passwordLength = prefs.passwordLength.coerceIn(
            PasswordGenerator.MIN_LENGTH,
            PasswordGenerator.MAX_LENGTH,
        ),
        currencyFrom = prefs.currencyFrom?.takeIf { it in CurrencyRateEngine.currencies }
            ?: DEFAULT_CURRENCY_FROM,
        currencyTo = prefs.currencyTo?.takeIf { it in CurrencyRateEngine.currencies }
            ?: DEFAULT_CURRENCY_TO,
        homeModuleOrder = QuickMenuHomeModule.sanitize(prefs.homeModuleOrder)
            .map { it.id },
        // 单位与类别的合法性依赖当前引擎的分类，由界面按类别兜底，这里不做判断。
    )
}

/**
 * 偏好落盘（功能级 DataStore，遵循本仓库 `UpdatePreferencesStore` 的既有约定）。
 *
 * 读取一律兜底：DataStore 抛错或数据损坏时返回默认值，面板照常可用。
 */
object QuickMenuPreferencesStore {

    private val KEY = stringPreferencesKey("quick_menu_prefs")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(context: Context): QuickMenuPrefs {
        val raw = runCatching { context.quickMenuPrefsDataStore.data.first()[KEY] }.getOrNull()
            ?: return QuickMenuPrefs()
        val decoded = runCatching { json.decodeFromString<QuickMenuPrefs>(raw) }
            .getOrDefault(QuickMenuPrefs())
        return QuickMenuPrefsCodec.sanitize(decoded)
    }

    suspend fun write(context: Context, prefs: QuickMenuPrefs) {
        runCatching {
            context.quickMenuPrefsDataStore.edit { mutable ->
                mutable[KEY] = json.encodeToString(QuickMenuPrefsCodec.sanitize(prefs))
            }
        }
    }

    /** 备份用：DataStore 里的原始 JSON。 */
    suspend fun exportRawJson(context: Context): String =
        runCatching { context.quickMenuPrefsDataStore.data.first()[KEY] }
            .getOrNull()
            .orEmpty()

    /** 恢复用：整份写回；返回收敛后的内存态，解析失败返回 null。 */
    suspend fun importRawJson(context: Context, raw: String): QuickMenuPrefs? {
        if (raw.isBlank()) return null
        val decoded = runCatching { json.decodeFromString<QuickMenuPrefs>(raw) }
            .getOrNull() ?: return null
        val sanitized = QuickMenuPrefsCodec.sanitize(decoded)
        runCatching {
            context.quickMenuPrefsDataStore.edit { mutable ->
                mutable[KEY] = json.encodeToString(sanitized)
            }
        }
        return sanitized
    }
}

/**
 * 偏好的内存态（Compose 可观察）+ 异步落盘。
 *
 * 界面**直接**读写这里，不再各自留一份 `remember` —— 否则异步读回来时本地副本不会更新，
 * 又会出现"明明记住了却没生效"。
 *
 * - [preview]：只改内存（拖滑杆过程中），不写盘；
 * - [update]：改内存并写盘（拖拽结束、拨开关、切选项）。
 */
object QuickMenuPreferences {

    var current by mutableStateOf(QuickMenuPrefs())
        private set

    private var appContext: Context? = null
    private var loaded = false

    /** 面板每次打开时调用；只在首次真正读一次盘（内存态此后就是权威）。 */
    fun attach(context: Context) {
        appContext = context.applicationContext
        if (loaded) return
        loaded = true
        QuickMenuWork.launch {
            current = QuickMenuPreferencesStore.read(context.applicationContext)
        }
    }

    fun preview(transform: (QuickMenuPrefs) -> QuickMenuPrefs) {
        current = QuickMenuPrefsCodec.sanitize(transform(current))
    }
    fun update(transform: (QuickMenuPrefs) -> QuickMenuPrefs) {
        preview(transform)
        persist()
    }

    /** 把当前内存态写盘（拖拽结束后调用一次，避免拖动过程中疯狂写）。 */
    fun persist() {
        val context = appContext ?: return
        val snapshot = current
        QuickMenuWork.launch { QuickMenuPreferencesStore.write(context, snapshot) }
    }

    /** 备份用：DataStore 里的原始 JSON。 */
    suspend fun exportRawJson(context: Context): String =
        QuickMenuPreferencesStore.exportRawJson(context)

    /** 恢复备份：写盘并同步内存态，面板下次打开直接生效。 */
    suspend fun importRawJson(context: Context, raw: String) {
        val decoded = QuickMenuPreferencesStore.importRawJson(context, raw) ?: return
        appContext = context.applicationContext
        loaded = true
        current = decoded
    }

    fun homeModules(): List<QuickMenuHomeModule> =
        QuickMenuHomeModule.sanitize(current.homeModuleOrder)

    /** 按首页可见模块的索引移动；越界/相同位置直接忽略。 */
    fun moveHomeModule(from: Int, to: Int) {
        val order = homeModules().toMutableList()
        if (from !in order.indices || to !in order.indices || from == to) return
        val item = order.removeAt(from)
        order.add(to, item)
        update { it.copy(homeModuleOrder = order.map { module -> module.id }) }
    }
}
