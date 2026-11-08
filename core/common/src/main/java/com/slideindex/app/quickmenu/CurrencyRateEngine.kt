package com.slideindex.app.quickmenu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * 汇率换算引擎（「快捷菜单 → 汇率换算」的计算核心）。
 *
 * 参考：QuickDash（https://github.com/balajitechlabs/quickdash，GPL-3.0）的 CurrencyRateEngine
 * 提供过同类能力（20 币种 + 内置离线汇率 + 在线刷新，以 USD 为基准）。本实现按其行为规格独立
 * 重写，并做两处必要调整：
 *
 * 1. **汇率源换成多源兜底**。参考实现只请求 `open.er-api.com`；该域名在中国大陆通常不可达，
 *    于是"在线刷新"在国内等于永远失败、只剩硬编码离线值。本实现按顺序尝试三个公开源
 *    （见 [SOURCES]），任一成功即返回，全部失败才回落到 [offlineTable]。
 * 2. **把汇率与展示文案解耦**。参考实现把币种显示名（`" USD"`、`" Euro"`）与汇率表写在同一处；
 *    本实现只保留币种代码与数值，显示名交由 UI 本地化。
 *
 * 另：汇率是**会变的公开数据**，离线表只用于网络不可用时的兜底估算，UI 必须把 [Table.live]
 * 透出给用户（参考实现用 Live/Offline 徽章做这件事，这个交互值得保留）。
 *
 * 除 [fetchLiveRates]（需要网络）外均为纯 JVM 逻辑，可脱离 Android 直接单测。
 */
object CurrencyRateEngine {

    /**
     * 一份汇率表。[rates] 为「1 单位基准货币 = rates[X] 单位 X」，基准恒为 USD。
     *
     * @param live 是否来自在线源；false 表示用的是 [offlineTable] 兜底值。
     * @param source 命中的在线源名称；兜底时为 null。
     */
    data class Table(
        val rates: Map<String, Double>,
        val live: Boolean,
        val source: String? = null,
    )

    /** 支持的币种（代码 + 中文场景常用顺序）。数值来自汇率表，显示名由 UI 本地化。 */
    val currencies: List<String> = listOf(
        "CNY", "USD", "EUR", "JPY", "HKD", "GBP", "KRW", "TWD", "SGD", "AUD",
        "CAD", "CHF", "THB", "MYR", "AED", "INR", "RUB", "NZD", "SEK", "BRL",
    )

    /**
     * 内置离线汇率（基准 USD，粗粒度估算值）。
     *
     * 只在三个在线源全部失败时使用，因此精度要求不高；UI 必须显示"离线"状态。
     */
    val offlineRates: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "CNY" to 7.20,
        "EUR" to 0.92,
        "JPY" to 152.0,
        "HKD" to 7.80,
        "GBP" to 0.79,
        "KRW" to 1340.0,
        "TWD" to 32.0,
        "SGD" to 1.34,
        "AUD" to 1.52,
        "CAD" to 1.37,
        "CHF" to 0.88,
        "THB" to 35.0,
        "MYR" to 4.45,
        "AED" to 3.67,
        "INR" to 83.5,
        "RUB" to 92.0,
        "NZD" to 1.64,
        "SEK" to 10.5,
        "BRL" to 5.10,
    )

    val offlineTable: Table = Table(rates = offlineRates, live = false)

    /**
     * 在线汇率源（按顺序尝试）。
     *
     * 选择依据：均无需 API key、无需注册，且解析结构互不相同，避免"同一家挂了就全挂"。
     * - [OPEN_ER_API]：参考实现用的源，境外可达性一般，放第一位是为了与参考行为对齐；
     * - [FRANKFURTER]：欧洲央行数据，稳定但只有约 30 个主流币种；
     * - [FAWAZ_CDN]：jsDelivr CDN 上的开源汇率数据，国内 CDN 可达性相对好，币种最全。
     */
    private enum class Source(val label: String, val url: String) {
        OPEN_ER_API("open.er-api.com", "https://open.er-api.com/v6/latest/USD"),
        FRANKFURTER("frankfurter.app", "https://api.frankfurter.app/latest?from=USD"),
        FAWAZ_CDN(
            "currency-api",
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json",
        ),
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 6000

    /**
     * 把 [amount] 从 [fromCode] 换算为 [toCode]。
     *
     * 公式与参考实现一致（都建立在"`rates[X]` = 1 USD 兑 X"的约定上）：
     * 先折算成基准货币，再折算到目标货币。
     *
     * 未知币种或汇率缺失时返回 null，交由 UI 提示，而不是静默按 1:1 计算。
     */
    fun convert(amount: Double, fromCode: String, toCode: String, table: Table = offlineTable): Double? {
        if (fromCode == toCode) return amount
        val fromRate = table.rates[fromCode] ?: return null
        val toRate = table.rates[toCode] ?: return null
        // 两个汇率都要校验：只看源货币的话，目标货币是 0 或负数时会把无效数据算成一个"看着正常"的结果。
        if (fromRate <= 0.0 || toRate <= 0.0) return null
        return amount / fromRate * toRate
    }

    /** 披露给用户的一行汇率提示，如 `1 USD = 7.2 CNY`（尾随零已去掉，最多 4 位小数）。 */
    fun rateHint(fromCode: String, toCode: String, table: Table = offlineTable): String? {
        val unit = convert(1.0, fromCode, toCode, table) ?: return null
        return "1 $fromCode = ${UnitConversionEngine.format(unit, maxDecimals = 4)} $toCode"
    }

    /**
     * 请求在线汇率；按 [Source] 顺序尝试，全部失败返回 null（调用方应回落到 [offlineTable]）。
     */
    suspend fun fetchLiveRates(): Table? = withContext(Dispatchers.IO) {
        for (source in Source.entries) {
            val body = runCatching { readBody(source.url) }.getOrNull() ?: continue
            val rates = runCatching { parseRates(body) }.getOrNull() ?: continue
            if (rates.isEmpty()) continue
            return@withContext Table(rates = rates + ("USD" to 1.0), live = true, source = source.label)
        }
        null
    }

    private fun readBody(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /**
     * 解析汇率 JSON。三种在线源的字段布局不同，逐一试探；命中即返回。
     *
     * 抽成公开函数是为了能在不联网的条件下用真实报文片段做单测。
     */
    fun parseRates(body: String): Map<String, Double> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyMap()

        // open.er-api.com / frankfurter.app：{"rates":{"CNY":7.2,...}}
        root.objectOrNull("rates")?.let { rates ->
            return rates.toRateMap { it.uppercase() }
        }

        // currency-api：{"usd":{"cny":7.2,...}}（键为小写，可能带 date 等同级字段）
        root.objectOrNull("usd")?.let { rates ->
            return rates.toRateMap { it.uppercase() }
        }

        return emptyMap()
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        runCatching { this[key]?.jsonObject }.getOrNull()

    private fun JsonObject.toRateMap(normalizeKey: (String) -> String): Map<String, Double> =
        buildMap {
            for ((key, value) in this@toRateMap) {
                val rate = runCatching { value.jsonPrimitive.content.toDouble() }.getOrNull() ?: continue
                if (rate <= 0.0 || rate.isNaN() || rate.isInfinite()) continue
                put(normalizeKey(key), rate)
            }
        }
}
