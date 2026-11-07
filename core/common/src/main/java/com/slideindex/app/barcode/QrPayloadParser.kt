package com.slideindex.app.barcode

import java.net.URLDecoder
import java.util.Locale

/**
 * 二维码 / 条码内容的语义解析（扫描结果的「翻译层」）。
 *
 * 背景：二维码本身不携带类型标记，扫描得到的永远只是一串原始文本；「这是 Wi-Fi 还是名片、
 * 密码在哪一段」必须靠解析得到。本类把原始文本翻译成结构化的 [QrPayloadResult]，供悬浮球
 * 取词面板、智能截图、分享图片 OCR 与「快捷菜单」共用。
 *
 * 与 [BarcodeScanResult] 的分工：[BarcodeScanResult] 只承载「扫到了什么原文与什么码制」，
 * 本类进一步承载「这段原文意味着什么」。
 *
 * 参考：QuickDash（https://github.com/balajitechlabs/quickdash，GPL-3.0）的 QrPayloadParser
 * 提供过同类能力划分（把结果分为网址 / Wi-Fi / 名片 / 电话 / 邮箱 / 商品条码 / 纯文本等语义
 * 类型，并附「标签 → 值」属性表）。本实现按其行为规格独立重写，并做三处调整：
 * 1. 不采用其硬编码英文标签，改为输出稳定的 [QrFieldId] 字段标识，标签文案交由 UI 本地化
 *    （本项目有 zh / ja / ar 多语言，逻辑层不应内嵌展示文案）；
 * 2. 补上其缺失的 Wi-Fi / MECARD 字段转义处理（`\;` `\:` `\\` `\,` `\"`）——原实现直接按
 *    `;` 切分，SSID 或密码里含分号时会解析错；
 * 3. 不采纳其 UPI（印度支付）分支。
 * 格式依据均为通行约定与标准：Wi-Fi 二维码为行业约定、vCard 为 RFC 6350、MECARD 为常见
 * 变体、`tel:` 为 RFC 3966、`mailto:` 为 RFC 6068、商品条码位数（EAN-8 / UPC-A / EAN-13 /
 * ITF-14）为 GS1 规范。
 *
 * 本类为纯 JVM 逻辑（只依赖 [URLDecoder]），可脱离 Android 直接单测。
 */
object QrPayloadParser {

    /** 结构化结果的语义类型；UI 据此选择标题文案（本地化由 UI 负责）。 */
    enum class Kind {
        WEB_URL,
        WIFI,
        CONTACT,
        PHONE,
        EMAIL,
        PRODUCT_BARCODE,
        PLAIN_TEXT,
    }

    /** 字段标识；UI 据此把字段名本地化成各语言文案。 */
    enum class FieldId {
        URL,
        HOST,
        SSID,
        SECURITY,
        PASSWORD,
        NAME,
        PHONE,
        EMAIL,
        SUBJECT,
        BARCODE,
        BARCODE_TYPE,
    }

    /** Wi-Fi 加密类型（解析 `WIFI:` 的 `T:` 字段）。 */
    enum class WifiSecurity {
        OPEN,
        WEP,
        WPA,
        WPA2_EAP,
        WPA3,
        UNKNOWN,
    }

    /** 商品/物流码制（按位数推断）。 */
    enum class BarcodeSymbology {
        EAN_8,
        UPC_A,
        EAN_13,
        ITF_14,
        UNKNOWN,
    }

    /** 一个「标签 → 值」字段；[id] 供 UI 查本地化文案，[value] 为原文数据。 */
    data class Field(val id: FieldId, val value: String)

    /** 结构化解析结果。 */
    sealed interface Result {
        /** 原始文本，始终原样保留。 */
        val raw: String

        val kind: Kind

        /** 建议展示顺序的字段列表；为空表示没有可展示的键值对（如纯文本）。 */
        val fields: List<Field>

        /** 适合做副标题的一行摘要；只含数据，不含需要本地化的文案。 */
        val summary: String
    }

    data class WebUrl(
        override val raw: String,
        /** 补全协议后的完整地址（`www.` 开头的会补成 `https://`）。 */
        val url: String,
        val host: String,
    ) : Result {
        override val kind: Kind get() = Kind.WEB_URL
        override val fields: List<Field> get() = listOf(Field(FieldId.URL, url), Field(FieldId.HOST, host))
        override val summary: String get() = host
    }

    data class Wifi(
        override val raw: String,
        val ssid: String,
        val security: WifiSecurity,
        /** `T:` 字段原文（如 `WPA` / `nopass`），供 UI 直接展示；无则为 null。 */
        val securityToken: String?,
        val password: String?,
        /** `H:true` 表示隐藏网络。 */
        val hidden: Boolean,
    ) : Result {
        override val kind: Kind get() = Kind.WIFI
        override val summary: String get() = ssid
        override val fields: List<Field>
            get() = buildList {
                add(Field(FieldId.SSID, ssid))
                securityToken?.takeIf { it.isNotBlank() }?.let { add(Field(FieldId.SECURITY, it)) }
                password?.takeIf { it.isNotEmpty() }?.let { add(Field(FieldId.PASSWORD, it)) }
            }
    }

    data class Contact(
        override val raw: String,
        val name: String?,
        val phone: String?,
        val email: String?,
    ) : Result {
        override val kind: Kind get() = Kind.CONTACT
        override val summary: String get() = name ?: phone ?: email ?: ""
        override val fields: List<Field>
            get() = buildList {
                name?.takeIf { it.isNotBlank() }?.let { add(Field(FieldId.NAME, it)) }
                phone?.takeIf { it.isNotBlank() }?.let { add(Field(FieldId.PHONE, it)) }
                email?.takeIf { it.isNotBlank() }?.let { add(Field(FieldId.EMAIL, it)) }
            }
    }

    data class PhoneNumber(
        override val raw: String,
        val number: String,
    ) : Result {
        override val kind: Kind get() = Kind.PHONE
        override val summary: String get() = number
        override val fields: List<Field> get() = listOf(Field(FieldId.PHONE, number))
    }

    data class EmailAddress(
        override val raw: String,
        val address: String,
        val subject: String?,
    ) : Result {
        override val kind: Kind get() = Kind.EMAIL
        override val summary: String get() = address
        override val fields: List<Field>
            get() = buildList {
                add(Field(FieldId.EMAIL, address))
                subject?.takeIf { it.isNotBlank() }?.let { add(Field(FieldId.SUBJECT, it)) }
            }
    }

    data class ProductBarcode(
        override val raw: String,
        val code: String,
        val symbology: BarcodeSymbology,
    ) : Result {
        override val kind: Kind get() = Kind.PRODUCT_BARCODE
        override val summary: String get() = code
        override val fields: List<Field>
            get() = listOf(Field(FieldId.BARCODE, code), Field(FieldId.BARCODE_TYPE, symbology.name))
    }

    data class PlainText(
        override val raw: String,
    ) : Result {
        override val kind: Kind get() = Kind.PLAIN_TEXT

        /** 纯文本没有键值结构。 */
        override val fields: List<Field> get() = emptyList()

        /** 摘要取首行并截断，避免长文本撑破副标题。 */
        override val summary: String
            get() = raw.lineSequence().firstOrNull()?.take(SUMMARY_MAX_CHARS)?.trim().orEmpty()
    }

    private const val SUMMARY_MAX_CHARS = 80
    private val GTIN_REGEX = Regex("^[0-9]{8,14}$")

    /**
     * 解析扫描得到的原始文本。
     *
     * 判定顺序（先具体后宽泛，避免误判）：Wi-Fi → 名片 → 电话 → 邮箱 → 网址 →
     * 商品条码 → 纯文本。
     */
    fun parse(raw: String): Result {
        val text = raw.trim()
        if (text.isEmpty()) return PlainText(raw)

        return when {
            text.startsWith(PREFIX_WIFI, ignoreCase = true) -> parseWifi(raw, text)
            isContactCard(text) -> parseContact(raw, text)
            text.startsWith(PREFIX_TEL, ignoreCase = true) -> {
                val number = text.substring(PREFIX_TEL.length).trim()
                PhoneNumber(raw = raw, number = number)
            }
            text.startsWith(PREFIX_MAILTO, ignoreCase = true) -> parseMailto(raw, text)
            isWebUrl(text) -> parseWebUrl(raw, text)
            GTIN_REGEX.matches(text) -> {
                ProductBarcode(raw = raw, code = text, symbology = symbologyOf(text.length))
            }
            else -> PlainText(raw = raw)
        }
    }

    private const val PREFIX_WIFI = "WIFI:"
    private const val PREFIX_TEL = "tel:"
    private const val PREFIX_MAILTO = "mailto:"
    private const val PREFIX_VCARD = "BEGIN:VCARD"
    private const val PREFIX_MECARD = "MECARD:"

    private fun isContactCard(text: String): Boolean =
        text.startsWith(PREFIX_VCARD, ignoreCase = true) ||
            text.startsWith(PREFIX_MECARD, ignoreCase = true)

    private fun isWebUrl(text: String): Boolean =
        text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true) ||
            text.startsWith("www.", ignoreCase = true)

    private fun symbologyOf(digits: Int): BarcodeSymbology = when (digits) {
        8 -> BarcodeSymbology.EAN_8
        12 -> BarcodeSymbology.UPC_A
        13 -> BarcodeSymbology.EAN_13
        14 -> BarcodeSymbology.ITF_14
        else -> BarcodeSymbology.UNKNOWN
    }

    private fun parseWebUrl(originalRaw: String, text: String): WebUrl {
        val url = if (text.startsWith("www.", ignoreCase = true)) "https://$text" else text
        val withoutScheme = url.substringAfter("://", url)
        val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return WebUrl(raw = originalRaw, url = url, host = host)
    }

    /**
     * 解析 `WIFI:S:<ssid>;T:<type>;P:<password>;H:<hidden>;;`。
     *
     * 与参考实现的关键差异：字段值可能含转义的分号/冒号（`\;` `\:`），因此先按**未转义的**
     * `;` 切分字段、再按**未转义的** `:` 切分键值，最后才反转义值本身。
     */
    private fun parseWifi(originalRaw: String, text: String): Wifi {
        val body = text.substring(PREFIX_WIFI.length)
        var ssid: String? = null
        var securityToken: String? = null
        var password: String? = null
        var hidden = false

        for (part in splitUnescaped(body, ';')) {
            if (part.isEmpty()) continue
            val separator = indexOfUnescaped(part, ':')
            if (separator <= 0) continue
            val key = part.substring(0, separator).trim().uppercase(Locale.US)
            val value = unescape(part.substring(separator + 1))
            when (key) {
                "S" -> ssid = value
                "T" -> securityToken = value
                "P" -> password = value
                "H" -> hidden = value.equals("true", ignoreCase = true)
                else -> Unit
            }
        }

        return Wifi(
            raw = originalRaw,
            ssid = ssid.orEmpty(),
            security = securityOf(securityToken),
            securityToken = securityToken,
            password = password,
            hidden = hidden,
        )
    }

    private fun securityOf(token: String?): WifiSecurity {
        val normalized = token?.trim()?.lowercase(Locale.US).orEmpty()
        return when {
            normalized.isEmpty() -> WifiSecurity.UNKNOWN
            normalized == "nopass" -> WifiSecurity.OPEN
            normalized.contains("wpa3") -> WifiSecurity.WPA3
            normalized.contains("wpa2-eap") || normalized.contains("wpa-eap") -> WifiSecurity.WPA2_EAP
            normalized.contains("wpa") -> WifiSecurity.WPA
            normalized.contains("wep") -> WifiSecurity.WEP
            else -> WifiSecurity.UNKNOWN
        }
    }

    /**
     * 解析 vCard（`BEGIN:VCARD`，按行）与 MECARD（`MECARD:`，按分号）。
     *
     * vCard 行可能带分组前缀（如 `item1.TEL:`），这里统一剥掉第一个 `.` 之前的分组名。
     * 只抽取姓名 / 电话 / 邮箱三样——与参考实现的覆盖范围一致，其余字段按需再扩。
     */
    private fun parseContact(originalRaw: String, text: String): Contact {
        val pairs: List<Pair<String, String>> = if (text.startsWith(PREFIX_MECARD, ignoreCase = true)) {
            splitUnescaped(text.substring(PREFIX_MECARD.length), ';')
                .mapNotNull { part -> toKeyValue(part) }
        } else {
            text.lineSequence()
                .mapNotNull { line -> toKeyValue(line.trim()) }
                .toList()
        }

        var name: String? = null
        var altName: String? = null
        var phone: String? = null
        var email: String? = null

        for ((rawKey, rawValue) in pairs) {
            // 剥掉 vCard 分组前缀：item1.TEL → TEL
            val key = rawKey.substringAfterLast('.').uppercase(Locale.US)
            val value = unescape(rawValue).trim()
            if (value.isEmpty()) continue
            when (key) {
                "FN" -> name = value
                "N" -> altName = value.replace(';', ' ').trim().replace(Regex("\\s+"), " ")
                "TEL" -> if (phone == null) phone = value
                "EMAIL" -> if (email == null) email = value
                else -> Unit
            }
        }

        return Contact(raw = originalRaw, name = name ?: altName, phone = phone, email = email)
    }

    /** 把 `KEY:VALUE` 或 `KEY;TYPE=x:VALUE` 拆成键值；无法识别返回 null。 */
    private fun toKeyValue(part: String): Pair<String, String>? {
        val separator = indexOfUnescaped(part, ':')
        if (separator <= 0) return null
        val keyPart = part.substring(0, separator)
        // vCard 允许 `TEL;TYPE=CELL:123`，取第一个 `;` 前的主键
        val key = keyPart.substringBefore(';').trim()
        if (key.isEmpty()) return null
        return key to part.substring(separator + 1)
    }

    private fun parseMailto(originalRaw: String, text: String): EmailAddress {
        val body = text.substring(PREFIX_MAILTO.length)
        val address = body.substringBefore('?').trim()
        val subject = body.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.startsWith("subject=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.let { decodeSafely(it) }
        return EmailAddress(raw = originalRaw, address = address, subject = subject)
    }

    /**
     * 按**未转义**的 [delimiter] 切分，转义序列（`\x`）原样保留给 [unescape] 处理。
     */
    private fun splitUnescaped(text: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '\\' && index + 1 < text.length -> {
                    current.append(char).append(text[index + 1])
                    index += 2
                }
                char == delimiter -> {
                    parts.add(current.toString())
                    current.setLength(0)
                    index++
                }
                else -> {
                    current.append(char)
                    index++
                }
            }
        }
        parts.add(current.toString())
        return parts
    }

    /** 返回第一个**未转义**的 [target] 下标；找不到返回 -1。 */
    private fun indexOfUnescaped(text: String, target: Char): Int {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\\' && index + 1 < text.length) {
                index += 2
                continue
            }
            if (char == target) return index
            index++
        }
        return -1
    }

    /** 反转义 Wi-Fi / MECARD 约定的 `\` `;` `:` `,` `"`。 */
    private fun unescape(value: String): String {
        if (!value.contains('\\')) return value
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '\\' && index + 1 < value.length) {
                out.append(value[index + 1])
                index += 2
            } else {
                out.append(char)
                index++
            }
        }
        return out.toString()
    }

    private fun decodeSafely(value: String): String = try {
        // ⚠️ 必须用**两参 String** 这个重载：`decode(String, Charset)` 是 API 33 才有的，
        // 而本项目 minSdk 31、也没开 core library desugaring —— 用它会直接被 lint 判 error
        // （NewApi），把 CI 的 Lint Check 任务整条卡红。两参 String 版从 API 1 就有。
        @Suppress("DEPRECATION")
        URLDecoder.decode(value, "UTF-8")
    } catch (_: IllegalArgumentException) {
        value
    }
}
