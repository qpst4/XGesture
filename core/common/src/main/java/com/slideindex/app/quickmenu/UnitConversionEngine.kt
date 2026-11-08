package com.slideindex.app.quickmenu

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 单位换算引擎（「快捷菜单 → 单位换算」的计算核心）。
 *
 * 设计：每个单位记录「到该类基准单位的系数 + 偏移」，换算统一走「先转基准、再转目标」两步。
 * 温度是唯一的仿射换算（需要偏移），其余都是线性系数，因此不必为目标类别各写一套分支。
 *
 * 参考：QuickDash（https://github.com/balajitechlabs/quickdash，GPL-3.0）的
 * UnitConversionEngine 提供过同类能力划分（7 个类别 + 逐类别换算分支）。本实现按其行为规格
 * 独立重写，并从「基准单位 + 系数表」出发，顺带修正它两处问题：
 * 1. 其数据类单位用 1024 进制却标注为 KB / MB / GB / TB（实际是 KiB / MiB / GiB / TiB），
 *    本实现改用正确的二进制符号，避免用户误读容量；
 * 2. 结果格式化时 `stripTrailingZeros()` 会把 100 变成 `1E+2`（scale 变为负数），本实现的
 *    [format] 对负 scale 补一次 `setScale(0)`（该坑在参考实现的计算器结果格式化里同样存在）。
 *
 * 换算系数均为公开定义（英制、美制液量、1024 进制数据单位），属事实数据。
 * 本类为纯 JVM 逻辑，可脱离 Android 直接单测。
 */
object UnitConversionEngine {

    enum class Category {
        LENGTH,
        WEIGHT,
        TEMPERATURE,
        AREA,
        VOLUME,
        SPEED,
        DATA,
    }

    /**
     * 一个单位定义。
     *
     * @param id 稳定标识（用于持久化「上次选择」与本地化查名），一旦发布不可改名。
     * @param symbol 展示用符号；多数与语言无关（`km` / `°C` / `KiB`）。
     * @param factor 到该类基准单位的换算系数：`基准值 = 原值 * factor + offset`。
     * @param offset 到基准单位的偏移，仅温度非 0。
     */
    data class UnitDef(
        val id: String,
        val symbol: String,
        val factor: Double,
        val offset: Double = 0.0,
    )

    /** 磅（国际磅，精确定义）。 */
    private const val POUND = 0.45359237

    /** 盎司 = 1/16 磅。 */
    private const val OUNCE = POUND / 16.0

    /** 美制液量加仑。 */
    private const val GALLON_US = 3.785411784

    /** 节 = 1 海里/小时 = 1852 m / 3600 s。 */
    private const val KNOT = 1852.0 / 3600.0

    /** km/h → m/s。 */
    private const val KILOMETRE_PER_HOUR = 1000.0 / 3600.0

    /** °F → °C 的斜率与截距：`°C = (°F - 32) * 5/9`。 */
    private const val FAHRENHEIT_FACTOR = 5.0 / 9.0
    private const val FAHRENHEIT_OFFSET = -32.0 * 5.0 / 9.0

    /** 各类别的单位表；每个类别的第一个单位即该类基准单位。 */
    val units: Map<Category, List<UnitDef>> = mapOf(
        Category.LENGTH to listOf(
            UnitDef("m", "m", 1.0),
            UnitDef("km", "km", 1000.0),
            UnitDef("cm", "cm", 0.01),
            UnitDef("mm", "mm", 0.001),
            UnitDef("ft", "ft", 0.3048),
            UnitDef("in", "in", 0.0254),
            UnitDef("mi", "mi", 1609.344),
        ),
        Category.WEIGHT to listOf(
            UnitDef("kg", "kg", 1.0),
            UnitDef("g", "g", 0.001),
            UnitDef("mg", "mg", 1e-6),
            UnitDef("t", "t", 1000.0),
            UnitDef("lb", "lb", POUND),
            UnitDef("oz", "oz", OUNCE),
        ),
        Category.TEMPERATURE to listOf(
            UnitDef("celsius", "°C", 1.0),
            UnitDef("fahrenheit", "°F", FAHRENHEIT_FACTOR, FAHRENHEIT_OFFSET),
            UnitDef("kelvin", "K", 1.0, -273.15),
        ),
        Category.AREA to listOf(
            UnitDef("sqm", "m²", 1.0),
            UnitDef("sqft", "ft²", 0.09290304),
            UnitDef("acre", "acre", 4046.8564224),
            UnitDef("hectare", "ha", 10000.0),
            UnitDef("sqkm", "km²", 1e6),
        ),
        Category.VOLUME to listOf(
            UnitDef("l", "L", 1.0),
            UnitDef("ml", "mL", 0.001),
            UnitDef("gal_us", "gal", GALLON_US),
            UnitDef("cup_us", "cup", GALLON_US / 16.0),
            UnitDef("pint_us", "pt", GALLON_US / 8.0),
            UnitDef("floz_us", "fl oz", GALLON_US / 128.0),
        ),
        Category.SPEED to listOf(
            UnitDef("mps", "m/s", 1.0),
            UnitDef("kph", "km/h", KILOMETRE_PER_HOUR),
            UnitDef("mph", "mph", 0.44704),
            UnitDef("knot", "kn", KNOT),
            UnitDef("fps", "ft/s", 0.3048),
        ),
        Category.DATA to listOf(
            UnitDef("byte", "B", 1.0),
            UnitDef("kib", "KiB", 1024.0),
            UnitDef("mib", "MiB", 1024.0 * 1024.0),
            UnitDef("gib", "GiB", 1024.0 * 1024.0 * 1024.0),
            UnitDef("tib", "TiB", 1024.0 * 1024.0 * 1024.0 * 1024.0),
        ),
    )

    private val unitIndex: Map<String, UnitDef> by lazy {
        units.values.flatten().associateBy { it.id }
    }

    private val categoryByUnitId: Map<String, Category> by lazy {
        units.entries.flatMap { (category, list) -> list.map { it.id to category } }.toMap()
    }

    fun unitsOf(category: Category): List<UnitDef> = units.getValue(category)

    /** 按稳定 id 取单位；用于恢复「上次选择」。未知 id 返回 null。 */
    fun unitOf(unitId: String): UnitDef? = unitIndex[unitId]

    /** 单位所属类别；未知 id 返回 null。 */
    fun categoryOf(unitId: String): Category? = categoryByUnitId[unitId]

    /**
     * 把 [value] 从 [from] 换算为 [to]。
     *
     * 两者必须属于同一 [Category]，否则抛 [IllegalArgumentException]——调用方是从同一类别的
     * 单位列表里取的，跨类别属于编程错误，不是用户输入错误。
     */
    fun convert(value: Double, from: UnitDef, to: UnitDef): Double {
        require(categoryOf(from.id) == categoryOf(to.id)) {
            "unit category mismatch: ${from.id} -> ${to.id}"
        }
        if (from.id == to.id) return value
        val base = value * from.factor + from.offset
        return (base - to.offset) / to.factor
    }

    /**
     * 结果格式化：最多保留 [maxDecimals] 位小数、去掉尾随零，且**不使用科学计数法**。
     *
     * `stripTrailingZeros()` 会把 `100.000000` 压成 `1E+2`（scale 变为负），所以这里对负 scale
     * 补一次 `setScale(0)`。非有限值（NaN / ±Infinity）返回空串，交由 UI 决定如何提示。
     */
    fun format(value: Double, maxDecimals: Int = 6): String {
        if (!value.isFinite()) return ""
        val scaled = BigDecimal(value)
            .setScale(maxDecimals, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return (if (scaled.scale() < 0) scaled.setScale(0) else scaled).toPlainString()
    }
}
