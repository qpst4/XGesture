package com.slideindex.app.overlay.quickmenu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.slideindex.app.overlay.panels.PanelRuntimeHooks
import com.slideindex.app.settings.FloatBallTranslateEngine
import com.slideindex.app.translate.TranslateDependencyAccess
import com.slideindex.app.util.ImageDecodeGuard
import com.slideindex.app.translate.TranslateEngine
import com.slideindex.app.translate.TranslateLanguageCatalog
import com.slideindex.app.translate.TranslateResult
import com.slideindex.app.translate.TranslateTargetResolver
import com.slideindex.app.translate.translateTargetAutonym
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.barcode.QrCodeGenerator
import com.slideindex.app.barcode.mergeScanResults
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.barcode.ScanOutcome
import com.slideindex.app.barcode.ZxingBarcodeScanner
import com.slideindex.app.barcode.boundingBoxOrNull
import com.slideindex.app.barcode.scanOutcome
import com.slideindex.app.overlay.screenshot.ScreenshotStorage
import com.slideindex.app.overlay.OverlayToastWindow
import com.slideindex.app.overlay.searchpanel.SearchPanelImagePickerActivity
import com.slideindex.app.quickmenu.CountdownLogic
import com.slideindex.app.quickmenu.CurrencyRateEngine
import com.slideindex.app.quickmenu.PasswordGenerator
import com.slideindex.app.quickmenu.StopwatchLogic
import com.slideindex.app.quickmenu.TextToolkit
import com.slideindex.app.quickmenu.UnitConversionEngine
import com.slideindex.app.service.ScreenshotCaptureOutcome
import com.slideindex.app.ui.ThinActionIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.security.SecureRandom
import kotlin.random.asKotlinRandom
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.MiuixIcons

/**
 * 「快捷菜单」的 6 个工具页。
 *
 * 这些页面只做「状态 + 渲染」，所有可验证的逻辑都在纯 JVM 件里
 * （`UnitConversionEngine` / `CurrencyRateEngine` / `PasswordGenerator` / `StopwatchLogic` /
 * `QrCodeGenerator` / `QrPayloadParser`）。因此本文件**没有单测**，逻辑正确性由那些件的单测覆盖，
 * 这里出错只可能是布局问题（需要真机看）。
 */


/** 交换按钮的尺寸（对齐 IconButton 默认值），用于标签行的占位。 */
private val SWAP_ACTION_SIZE = 48.dp

/**
 * 图上标记的最小边长（归一化）。
 *
 * **必须有**：一维条码只给两个端点，外接矩形高度为 0，不兜底就画不出可点的标记。
 */
private const val MARKER_MIN_SIZE = 0.05f

/** 标记区的压暗强度：只压暗不"挖亮"的话，码本身也一起变暗，反而更难看清。 */
private const val MARKER_DIM_ALPHA = 0.45f

/** 四角括号的臂长占标记短边的比例。 */
private const val MARKER_CORNER_FRACTION = 0.28f

/**
 * 强度进度条的"满格"参考值（bit）。
 *
 * 取 128：大致对应 AES-128 那一档的安全强度，是业界常用的"足够"参照线。
 * 进度条与那行小字说明共用它，避免两处各写一个魔法数。
 */
private const val ENTROPY_FULL_SCALE_BITS = 128

/**
 * 收起浮层后到"截屏"之间的等待。
 *
 * 用 120ms 而不是 MediaProjection 那条路用的 50ms：这里对齐 `performSmartScreenshot` 的既有节奏——
 * 它也是"收起浮层 → `Choreographer` 等一帧 → 再等 120ms → 截"，无障碍截图对合成时机更敏感。
 */
private const val SCREENSHOT_HIDE_DELAY_MS = 120L


@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 结果行：等宽字体 + 一键复制。 */
@Composable
private fun ResultRow(text: String, copyLabel: String) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { copyToClipboard(context, copyLabel, text) }) {
                Icon(imageVector = MiuixIcons.Medium.Copy, contentDescription = null)
            }
        }
    }
}

/**
 * 结果行 + 两个动作（重新生成、复制）。
 *
 * 摆位对齐 quickdash 的密码页：**两个动作都贴在它们作用的那个值旁边、放在同一张卡片里，
 * 且"重新生成"在前**（对生成器来说重算比复制更常用）。早先的版本把复制放在卡片里、把刷新
 * 孤零零挂在卡片外面，视觉上不在一组，也看不出它作用于谁。
 */
@Composable
private fun ResultRowWithActions(
    text: String,
    copyLabel: String,
    onRegenerate: () -> Unit,
    regenerateLabel: String,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRegenerate) {
                Icon(
                    imageVector = MiuixIcons.Medium.Refresh,
                    contentDescription = regenerateLabel,
                )
            }
            IconButton(onClick = { copyToClipboard(context, copyLabel, text) }) {
                Icon(imageVector = MiuixIcons.Medium.Copy, contentDescription = null)
            }
        }
    }
}

/**
 * 居中的字段标签。
 *
 * 标签与输入框分成两行，是为了让中间的交换按钮**只与输入框那一行垂直居中**：早先标签在
 * `DropdownBox` 内部（Column），Row 居中的对象变成"标签 + 输入框"整列，箭头相对输入框就偏上。
 */
@Composable
private fun RowScope.CenteredFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f),
    )
}

/** 标签行里与交换按钮等宽的占位，保证两行左右对齐（IconButton 默认 48dp）。 */
@Composable
private fun SwapActionSpacer() {
    Spacer(modifier = Modifier.width(SWAP_ACTION_SIZE))
}

@Composable
private fun RowScope.DropdownBox(
    current: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.weight(1f)) {
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = current,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            expanded = false
                            onSelect(id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun unitCategoryLabel(category: UnitConversionEngine.Category): String = stringResource(
    when (category) {
        UnitConversionEngine.Category.LENGTH -> R.string.quick_menu_unit_cat_length
        UnitConversionEngine.Category.WEIGHT -> R.string.quick_menu_unit_cat_weight
        UnitConversionEngine.Category.TEMPERATURE -> R.string.quick_menu_unit_cat_temperature
        UnitConversionEngine.Category.AREA -> R.string.quick_menu_unit_cat_area
        UnitConversionEngine.Category.VOLUME -> R.string.quick_menu_unit_cat_volume
        UnitConversionEngine.Category.SPEED -> R.string.quick_menu_unit_cat_speed
        UnitConversionEngine.Category.DATA -> R.string.quick_menu_unit_cat_data
    },
)

/**
 * 下拉里显示的单位标签：`名称 (符号)`。
 *
 * 只给符号（`acre` / `knot` / `fl oz`）中文用户读不懂；只给译名又丢掉国际通用写法，所以两者都给。
 * 未知 id 退回符号本身，保证以后引擎里加单位时 UI 不会崩。
 */
@Composable
private fun unitOptionLabel(unit: UnitConversionEngine.UnitDef): String {
    val nameRes = unitNameRes(unit.id) ?: return unit.symbol
    return "${stringResource(nameRes)} (${unit.symbol})"
}

/** 单位 id → 名称资源；id 是 [UnitConversionEngine] 里的稳定标识，与符号解耦。 */
private fun unitNameRes(id: String): Int? = when (id) {
    "m" -> R.string.quick_menu_unit_m
    "km" -> R.string.quick_menu_unit_km
    "cm" -> R.string.quick_menu_unit_cm
    "mm" -> R.string.quick_menu_unit_mm
    "ft" -> R.string.quick_menu_unit_ft
    "in" -> R.string.quick_menu_unit_in
    "mi" -> R.string.quick_menu_unit_mi
    "kg" -> R.string.quick_menu_unit_kg
    "g" -> R.string.quick_menu_unit_g
    "mg" -> R.string.quick_menu_unit_mg
    "t" -> R.string.quick_menu_unit_t
    "lb" -> R.string.quick_menu_unit_lb
    "oz" -> R.string.quick_menu_unit_oz
    "celsius" -> R.string.quick_menu_unit_celsius
    "fahrenheit" -> R.string.quick_menu_unit_fahrenheit
    "kelvin" -> R.string.quick_menu_unit_kelvin
    "sqm" -> R.string.quick_menu_unit_sqm
    "sqft" -> R.string.quick_menu_unit_sqft
    "acre" -> R.string.quick_menu_unit_acre
    "hectare" -> R.string.quick_menu_unit_hectare
    "sqkm" -> R.string.quick_menu_unit_sqkm
    "l" -> R.string.quick_menu_unit_l
    "ml" -> R.string.quick_menu_unit_ml
    "gal_us" -> R.string.quick_menu_unit_gal_us
    "cup_us" -> R.string.quick_menu_unit_cup_us
    "pint_us" -> R.string.quick_menu_unit_pint_us
    "floz_us" -> R.string.quick_menu_unit_floz_us
    "mps" -> R.string.quick_menu_unit_mps
    "kph" -> R.string.quick_menu_unit_kph
    "mph" -> R.string.quick_menu_unit_mph
    "knot" -> R.string.quick_menu_unit_knot
    "fps" -> R.string.quick_menu_unit_fps
    "byte" -> R.string.quick_menu_unit_byte
    "kib" -> R.string.quick_menu_unit_kib
    "mib" -> R.string.quick_menu_unit_mib
    "gib" -> R.string.quick_menu_unit_gib
    "tib" -> R.string.quick_menu_unit_tib
    else -> null
}

// ---------------------------------------------------------------- 单位换算

@Composable
internal fun QuickMenuUnitConverterScreen() {
    // 类别与两端单位都直接由持久化偏好推导：既做到"记住上次选择"，也天然保证选中的 id 一定
    // 落在当前类别里（存的是别的类别的 id 时退回该类别的默认两端），因此不再需要"切类别时纠正"。
    val prefs = QuickMenuPreferences.current
    val category = prefs.unitCategory
        ?.let { name -> UnitConversionEngine.Category.entries.firstOrNull { it.name == name } }
        ?: UnitConversionEngine.Category.LENGTH
    val units = UnitConversionEngine.unitsOf(category)
    // 单位名必须在 composable 作用域取（`stringResource` 不能出现在非内联 lambda 里），
    // 所以先用普通 for 循环把 id → "名称 (符号)" 预算出来，后面只做 Map 查表。
    val unitLabels = mutableMapOf<String, String>()
    for (unit in units) {
        unitLabels[unit.id] = unitOptionLabel(unit)
    }
    val fromId = prefs.unitFromId?.takeIf { id -> units.any { it.id == id } } ?: units.first().id
    val toId = prefs.unitToId?.takeIf { id -> units.any { it.id == id } }
        ?: (units.getOrNull(1)?.id ?: units.first().id)
    var input by remember { mutableStateOf("1") }

    val from = UnitConversionEngine.unitOf(fromId)
    val to = UnitConversionEngine.unitOf(toId)
    val value = input.toDoubleOrNull()
    val result = if (value != null && from != null && to != null) {
        UnitConversionEngine.convert(value, from, to)
    } else {
        null
    }

    ToolScaffold {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UnitConversionEngine.Category.entries.forEach { entry ->
                FilterChip(
                    selected = entry == category,
                    onClick = {
                        QuickMenuPreferences.update { it.copy(unitCategory = entry.name) }
                    },
                    label = { Text(unitCategoryLabel(entry)) },
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            CenteredFieldLabel(stringResource(R.string.quick_menu_convert_from))
            SwapActionSpacer()
            CenteredFieldLabel(stringResource(R.string.quick_menu_convert_to))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DropdownBox(
                current = from?.let { unitLabels[it.id] }.orEmpty(),
                options = units.map { it.id to (unitLabels[it.id] ?: it.symbol) },
                onSelect = { id -> QuickMenuPreferences.update { it.copy(unitFromId = id) } },
            )
            IconButton(onClick = {
                QuickMenuPreferences.update { it.copy(unitFromId = toId, unitToId = fromId) }
            }) {
                Icon(imageVector = Icons.Filled.SwapHoriz, contentDescription = null)
            }
            DropdownBox(
                current = to?.let { unitLabels[it.id] }.orEmpty(),
                options = units.map { it.id to (unitLabels[it.id] ?: it.symbol) },
                onSelect = { id -> QuickMenuPreferences.update { it.copy(unitToId = id) } },
            )
        }

        if (result != null && to != null) {
            ResultRow(
                text = "${UnitConversionEngine.format(result)} ${to.symbol}",
                copyLabel = "unit",
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "1 ${from?.symbol.orEmpty()} = ${
                UnitConversionEngine.format(
                    if (from != null && to != null) UnitConversionEngine.convert(1.0, from, to) else 1.0,
                )
            } ${to?.symbol.orEmpty()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- 汇率换算

@Composable
internal fun QuickMenuCurrencyConverterScreen() {
    val scope = rememberCoroutineScope()
    var table by remember { mutableStateOf(CurrencyRateEngine.offlineTable) }
    var loading by remember { mutableStateOf(false) }
    // 两端货币同样由偏好推导（记住上次选择；不存在的代码由 QuickMenuPrefsCodec 兜回默认值）。
    val prefs = QuickMenuPreferences.current
    val fromCode = prefs.currencyFrom ?: CurrencyRateEngine.currencies.first()
    val toCode = prefs.currencyTo ?: CurrencyRateEngine.currencies.first()
    var input by remember { mutableStateOf("100") }

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            val live = CurrencyRateEngine.fetchLiveRates()
            if (live != null) table = live
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val amount = input.toDoubleOrNull()
    val converted = if (amount != null) CurrencyRateEngine.convert(amount, fromCode, toCode, table) else null

    ToolScaffold {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (table.live) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Text(
                    text = table.source ?: "offline",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { refresh() }, enabled = !loading) {
                Icon(
                    imageVector = MiuixIcons.Medium.Refresh,
                    contentDescription = null,
                    modifier = if (loading) Modifier.size(18.dp) else Modifier.size(24.dp),
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            CenteredFieldLabel(stringResource(R.string.quick_menu_convert_from))
            SwapActionSpacer()
            CenteredFieldLabel(stringResource(R.string.quick_menu_convert_to))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DropdownBox(
                current = fromCode,
                options = CurrencyRateEngine.currencies.map { it to it },
                onSelect = { code -> QuickMenuPreferences.update { it.copy(currencyFrom = code) } },
            )
            IconButton(onClick = {
                QuickMenuPreferences.update { it.copy(currencyFrom = toCode, currencyTo = fromCode) }
            }) {
                Icon(imageVector = Icons.Filled.SwapHoriz, contentDescription = null)
            }
            DropdownBox(
                current = toCode,
                options = CurrencyRateEngine.currencies.map { it to it },
                onSelect = { code -> QuickMenuPreferences.update { it.copy(currencyTo = code) } },
            )
        }

        if (converted != null) {
            ResultRow(
                text = "${UnitConversionEngine.format(converted, maxDecimals = 4)} $toCode",
                copyLabel = "currency",
            )
        }
        CurrencyRateEngine.rateHint(fromCode, toCode, table)?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- 二维码生成

@Composable
internal fun QuickMenuQrGeneratorScreen() {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }
    val bitmap = remember(content) {
        if (content.isBlank()) null else QrCodeGenerator.generate(content)
    }

    ToolScaffold {
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("https://") },
        )
        if (bitmap != null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(240.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                // 保存到相册与分享都复用本仓库既有的 ScreenshotStorage（MediaStore + FileProvider），
                // 不另造 FileProvider 路径，因此无需新权限。
                IconButton(onClick = {
                    val saved = ScreenshotStorage.saveToGallery(context, bitmap)
                    val message = if (saved != null) {
                        R.string.quick_menu_qr_saved
                    } else {
                        R.string.quick_menu_qr_save_failed
                    }
                    showQuickMenuToast(
                        context,
                        if (saved != null) {
                            R.string.quick_menu_qr_saved
                        } else {
                            R.string.quick_menu_qr_save_failed
                        },
                    )
                }) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.quick_menu_qr_save),
                    )
                }
                IconButton(onClick = {
                    // 系统分享面板会盖在我们上面，且没有可靠的回调 → 先收起浮层，不自动恢复。
                    val uri = ScreenshotStorage.createShareUri(context, bitmap)
                    if (uri != null) {
                        QuickMenuOverlayWindow.hideForExternalShare()
                        ScreenshotStorage.share(context, uri)
                    }
                }) {
                    Icon(
                        imageVector = MiuixIcons.Medium.Share,
                        contentDescription = stringResource(R.string.quick_menu_qr_share),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 识码解析

/**
 * 拉起相机扫码并把结果落到当前页面。
 *
 * 抽成函数是因为**两个地方**都要用它：识码页的「相机扫码」按钮，以及菜单列表搜索框旁的扫码按钮
 * （用户要"从顶部一键扫码"）。以前这段内联在按钮里，抽出后两处走同一条路，行为不会分叉。
 */
internal fun launchCameraScan(
    context: Context,
    onContentChange: (String) -> Unit,
    onResultsChange: (List<BarcodeScanResult>) -> Unit,
    onImageChange: (Bitmap?) -> Unit,
) {
    // 浮层必须先收起：无障碍覆盖层永远在最上层，不收起来相机预览会被它整个盖住。
    QuickMenuOverlayWindow.suspendForExternalActivity()
    QuickMenuScanActivity.launch(context) { scanned, scannedImage ->
        QuickMenuOverlayWindow.resumeFromExternalActivity()
        // ① 先把"锁定那一帧"的结果与定格图立刻显示出来：标记与图**同源**，天然对齐。
        applyScanResults(context, scanned, scannedImage, onContentChange, onResultsChange, onImageChange)
        // ② 再在这张**同一张图**上做一次全码制 + TRY_HARDER 的重扫，把同画面里其余的码补齐。
        //    与选图/截屏走完全同一个函数，所以三条路的召回率一致；因为扫的是同一张图，标记也不会偏移。
        if (scannedImage == null) return@launch
        QuickMenuWork.launch {
            val rescanned = withContext(Dispatchers.Default) { scanBarcodes(scannedImage) }
            if (rescanned.isNotEmpty()) {
                // **合并**而不是替换：重扫走的是另一套策略，它可能先命中别的码；直接替换会表现为
                // "刚标出来的码又消失、改成标别的码"（真机反馈）。
                applyScanResults(
                    context = context,
                    results = mergeScanResults(scanned, rescanned),
                    image = scannedImage,
                    onContentChange = onContentChange,
                    onResultsChange = onResultsChange,
                    onImageChange = onImageChange,
                )
            }
        }
    }
}

@Composable
internal fun QuickMenuScanDecoderScreen(
    content: String,
    results: List<BarcodeScanResult>,
    image: Bitmap?,
    onContentChange: (String) -> Unit,
    onResultsChange: (List<BarcodeScanResult>) -> Unit,
    onImageChange: (Bitmap?) -> Unit,
) {
    val context = LocalContext.current

    ToolScaffold {
        // 相机扫码是主操作。quickdash 的扫码页也是"打开就是取景器"，没有常驻输入框。
        Button(
            onClick = { launchCameraScan(context, onContentChange, onResultsChange, onImageChange) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.quick_menu_scan_camera))
        }

        // 次级来源：选图 / 截屏。两者同样要先挂起浮层（截屏还必须挂起，否则会把面板自己截进去）。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    QuickMenuOverlayWindow.suspendForExternalActivity()
                    // 复用已有的选图 Activity：面板跑在 overlay 的 ComposeView 里，
                    // 拿不到 ActivityResultRegistry，用不了 rememberLauncherForActivityResult。
                    SearchPanelImagePickerActivity.launch(context) picked@{ uri ->
                        QuickMenuOverlayWindow.resumeFromExternalActivity()
                        if (uri == null) return@picked
                        // ⚠️ 必须用 QuickMenuWork（与组合无关的作用域）：外部 Activity 期间
                        // 面板窗口可能被系统摘掉、组合随之销毁，rememberCoroutineScope 会被取消，
                        // 后续解码就永远不会执行 —— 这正是"选图识别没反应"的根因。
                        QuickMenuWork.launch {
                            val bitmap = withContext(Dispatchers.Default) { decodeBitmap(context, uri) }
                                ?: return@launch
                            val scanned = withContext(Dispatchers.Default) { scanBarcodes(bitmap) }
                            applyScanResults(
                                context = context,
                                results = scanned,
                                image = bitmap,
                                onContentChange = onContentChange,
                                onResultsChange = onResultsChange,
                                onImageChange = onImageChange,
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.quick_menu_scan_pick_image))
            }
            OutlinedButton(
                onClick = {
                    // 同上：收尾工作不能在组合作用域里做。
                    QuickMenuWork.launch {
                        // 无障碍截图截的是合成后的屏幕，浮层不收会被一起截进去。
                        QuickMenuOverlayWindow.suspendForExternalActivity()
                        // 对齐 performSmartScreenshot 的既有节奏（等一帧 + 120ms）再截。
                        delay(SCREENSHOT_HIDE_DELAY_MS)
                        val outcome = com.slideindex.app.overlay.quickmenu.QuickMenuHostAccess.host?.captureScreenshot()
                        QuickMenuOverlayWindow.resumeFromExternalActivity()
                        if (outcome == null) {
                            showQuickMenuToast(context, R.string.quick_menu_scan_capture_unavailable)
                            return@launch
                        }
                        when (outcome) {
                            is ScreenshotCaptureOutcome.Success -> {
                                val scanned = withContext(Dispatchers.Default) {
                                    scanBarcodes(outcome.bitmap)
                                }
                                applyScanResults(
                                    context = context,
                                    results = scanned,
                                    image = outcome.bitmap,
                                    onContentChange = onContentChange,
                                    onResultsChange = onResultsChange,
                                    onImageChange = onImageChange,
                                )
                            }
                            ScreenshotCaptureOutcome.Throttled ->
                                showQuickMenuToast(context, R.string.quick_menu_scan_capture_throttled)
                            ScreenshotCaptureOutcome.ServiceUnavailable ->
                                showQuickMenuToast(context, R.string.quick_menu_scan_capture_unavailable)
                            is ScreenshotCaptureOutcome.Failed ->
                                showQuickMenuToast(
                                    context,
                                    R.string.quick_menu_scan_capture_failed,
                                    outcome.errorCode,
                                )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = ThinActionIcons.Screenshot, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.quick_menu_scan_capture))
            }
        }

        // 有图就显示（认出一个也显示），认出几个标几个；列表只在多个码时才出现。
        if (results.isNotEmpty() && image != null) {
            ScanImageWithMarkers(
                image = image,
                results = results,
                selectedText = content,
                onSelect = onContentChange,
            )
        }

        // 多个码时才列出候选：只有一个码时下面直接就是它的解析结果，不需要再选一次。
        if (results.size > 1) {
            Text(
                text = stringResource(R.string.quick_menu_scan_candidates, results.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            results.forEach { result ->
                val selected = result.text == content
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onContentChange(result.text) },
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = result.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        }
                        // 复制 / 转到挂在**每一条**上：以前这些操作在下面单独一块"解析详情"里，
                        // 而那块和这里的候选列表看起来是"两块结果"，用户反馈看不出什么关系。
                        ScanItemActions(text = result.text)
                    }
                }
            }
            Text(
                text = stringResource(R.string.quick_menu_scan_candidates_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 只有一个码（或选图/截屏只认出一个）时没有候选列表，这里给一行"内容 + 复制/转到"，
        // 保证单码也能复制、也能一键打开链接。
        if (results.size <= 1 && content.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ScanItemActions(text = content)
                }
            }
        } else if (results.isEmpty()) {
            Text(
                text = stringResource(R.string.quick_menu_scan_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 一条识别结果上的操作：复制，以及（内容是 http/https 时）转到。
 *
 * 这两件事以前挂在屏幕底部那块"解析详情"里，而那块与上面的候选列表看起来是**两块结果**、
 * 关系说不清（用户反馈："上面不是已经有找到 x 个码显示结果了吗"）。现在操作直接挂在每一条结果上，
 * 界面只剩"找到的码 + 每项的操作"。转到按钮的文案复用既有的 `pick_result_open_link`，
 * 不新增字符串。
 */
@Composable
private fun ScanItemActions(text: String) {
    val context = LocalContext.current
    val url = remember(text) { webUrlOfText(text) }
    IconButton(onClick = { copyToClipboard(context, "scan", text) }) {
        Icon(imageVector = MiuixIcons.Medium.Copy, contentDescription = null)
    }
    if (url != null) {
        IconButton(onClick = {
            // 浏览器会盖在我们上面，且用户可能不回来 → **只收起、不自动恢复**（与分享同一策略）
            QuickMenuOverlayWindow.hideForExternalShare()
            // 失败静默：只会出现在"没有任何应用能处理 http(s)"的极端机型上
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }) {
            Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = stringResource(R.string.pick_result_open_link))
        }
    }
}

/** 内容是可直接打开的链接时返回它（`www.` 开头会补成 https）；否则 null。 */
private fun webUrlOfText(text: String): String? {
    val trimmed = text.trim()
    return when {
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("www.", ignoreCase = true) -> "https://$trimmed"
        else -> null
    }
}

// ---------------------------------------------------------------- 翻译

/**
 * 翻译小工具：输入 → 翻译 → 结果（可复制）。
 *
 * 直接复用 App 既有的翻译链路（`TranslateService`：Google / ML Kit / 云端 LLM），**目标语言读的就是
 * 「翻译」设置里那一项** —— 用户在悬浮球那边配好，这里不用再配一遍。
 *
 * 一个取舍：引擎设成「本地 App」时**不**照搬。那条路是"把文字交给另一个 App 去显示"，需要一个前台
 * Activity 跟它交互，跟面板里的输入框不兼容；这里退化成离线可用的 ML Kit（本地、免费、无需配额）。
 */
/**
 * 翻译页的**跨"收起/展开"状态**。
 *
 * 为什么不用 `remember`：收起面板会销毁窗口 → 组合被销毁 → `remember` 里的东西全丢 ✗
 * （真机反馈："输入的文字收起再展开就没了"）。这里和 `QuickMenuTimerRuntime` / `QuickMenuPreferences`
 * 同一个套路：**把状态放在组合之外**，窗口来去都不影响 ✓
 */
internal object QuickMenuTranslateState {
    var input by mutableStateOf("")
    var output by mutableStateOf("")
    var detected by mutableStateOf<String?>(null)
    var target by mutableStateOf("")
}

@Composable
internal fun QuickMenuTranslateScreen() {
    val context = LocalContext.current
    val settings = remember(context) {
        PanelRuntimeHooks.settingsSnapshot?.invoke(context)
    }
    val languages = remember { TranslateLanguageCatalog.options }
    // 目标语言标签用 App 既有的 `translateTargetAutonym`（**各语言用自己的语言写自己**：
    // 日本語 / العربية / English…），与「取词面板翻译」和「ML Kit 语言包」两页完全一致。
    // 不用 catalog 里的 `displayName` —— 那是硬编码英文 ✗
    // ⚠️ 它是 @Composable，所以这里**不能**包在 remember{} 里（remember 的 lambda 不是组合作用域），
    // 直接每次重组算一遍即可（13 条，极便宜）。
    val targetOptions = languages.map { it.code to translateTargetAutonym(it.code) }
    // 首帧用设置里那项回填（之后尊重用户在本页的手选，且能跨收起/展开保留）
    if (QuickMenuTranslateState.target.isBlank()) {
        QuickMenuTranslateState.target = settings?.let {
            TranslateTargetResolver.resolve(it.floatBallTranslateTargetLang, it.appUiLanguageTag)
        } ?: "en"
    }

    // 本次实际使用的引擎（**面板内**把「本地 App」退化成 ML Kit，所以显示的是真在用的那个）
    val engine = when (settings?.floatBallTranslateEngine) {
        FloatBallTranslateEngine.GOOGLE -> TranslateEngine.GOOGLE
        FloatBallTranslateEngine.CLOUD_LLM -> TranslateEngine.CLOUD_LLM
        FloatBallTranslateEngine.LOCAL_APP,
        FloatBallTranslateEngine.ML_KIT,
        null,
        -> TranslateEngine.ML_KIT
    }
    val engineLabel = stringResource(
        when (engine) {
            TranslateEngine.GOOGLE -> R.string.float_ball_translate_engine_google
            TranslateEngine.CLOUD_LLM -> R.string.float_ball_translate_engine_cloud
            TranslateEngine.ML_KIT -> R.string.float_ball_translate_engine_mlkit
        },
    )
    // 这两条是"这一次操作"的瞬时状态，跟着组合走即可（收起面板丢掉也不影响体验）
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    ToolScaffold {
        // 只留**目标语言**：源语言由引擎自己检测（三个引擎都支持，ML Kit 用 LanguageIdentification）。
        // 有意不做"手动指定源语言"的下拉——现有引擎接口没有 source 参数，放了也是白选 ✗
        // （要真做就得给 core 那层加参数，属于另一件事）。检测结果仍然会显示在译文上方。
        // 注意 `DropdownBox` 是 `RowScope` 扩展（自己带权重），所以必须直接放在 Row 里。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FieldLabel(stringResource(R.string.float_ball_translate_target_lang))
            DropdownBox(
                current = translateTargetAutonym(QuickMenuTranslateState.target),
                options = targetOptions,
                onSelect = { QuickMenuTranslateState.target = it },
            )
        }

        OutlinedTextField(
            value = QuickMenuTranslateState.input,
            onValueChange = { QuickMenuTranslateState.input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.quick_menu_translate_title)) },
            minLines = 2,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val pasted = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (pasted.isNotBlank()) QuickMenuTranslateState.input = pasted
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.quick_menu_translate_paste))
            }
            Button(
                onClick = {
                    val text = QuickMenuTranslateState.input.trim()
                    if (text.isEmpty() || busy) return@Button
                    busy = true
                    error = null
                    // 与组合无关的作用域：翻译是挂起调用，期间面板可能被系统摘掉。
                    QuickMenuWork.launch {
                        val service = TranslateDependencyAccess.translateService(context)
                        if (service == null) {
                            busy = false
                            return@launch
                        }
                        // 引擎内部自动检测源语言（Google 用 sl=auto、ML Kit 用 LanguageIdentification），
                        // 检测结果由 TranslateResult.Success.detectedSourceLanguage 带回来显示。
                        when (val result = service.translate(text, QuickMenuTranslateState.target, engine)) {
                            is TranslateResult.Success -> {
                                QuickMenuTranslateState.output = result.translatedText
                                QuickMenuTranslateState.detected = result.detectedSourceLanguage
                                error = null
                            }
                            is TranslateResult.Failure -> error = result.message
                        }
                        busy = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = QuickMenuTranslateState.input.isNotBlank() && !busy,
            ) {
                Text(stringResource(R.string.quick_menu_translate_action))
            }
        }
        if (QuickMenuTranslateState.output.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            // 一行元信息，**不新增视觉元素**（就是原来那行的位置与小字灰）：
            // `谁译的 · 源语言 → 目标语言`。源语言是引擎真实检测的结果（可能为空：云端 LLM 不回报），
            // 为空时只显示 `谁译的 · → 目标语言`。
            val sourceLabel = detectedLabel(QuickMenuTranslateState.detected)
            val targetLabel = translateTargetAutonym(QuickMenuTranslateState.target)
            Text(
                text = if (sourceLabel != null) {
                    "$engineLabel · $sourceLabel → $targetLabel"
                } else {
                    "$engineLabel · → $targetLabel"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ResultRow(text = QuickMenuTranslateState.output, copyLabel = "translate")
        }
        error?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}



/**
 * 把引擎回报的源语言码转成"能看懂的自称"。
 *
 * 引擎给的码口径不一：Google 可能给 `zh-CN`，ML Kit 给它自己的 `zh`。所以按三步匹配目录：
 * 完整码 → ML Kit 码 → 语言前缀；都匹配不到就原样显示（宁可显示 `xx`，也不要空白）。
 */
@Composable
private fun detectedLabel(detected: String?): String? {
    val code = detected?.takeIf { it.isNotBlank() } ?: return null
    val catalogCode = TranslateLanguageCatalog.options.firstOrNull { it.code.equals(code, ignoreCase = true) }?.code
        ?: TranslateLanguageCatalog.options.firstOrNull { it.mlKitLanguage.equals(code, ignoreCase = true) }?.code
        ?: TranslateLanguageCatalog.options.firstOrNull {
            it.code.substringBefore('-').equals(code, ignoreCase = true)
        }?.code
    return if (catalogCode != null) translateTargetAutonym(catalogCode) else code
}

// ---------------------------------------------------------------- 密码生成

@Composable
internal fun QuickMenuPasswordScreen() {
    // 选项直接读持久化偏好：面板关掉再打开也记得上次的设置（用户反馈"3 个开关没有记忆"）。
    val prefs = QuickMenuPreferences.current
    val length = prefs.passwordLength
    val digits = prefs.passwordDigits
    val symbols = prefs.passwordSymbols
    val avoidAmbiguous = prefs.passwordAvoidAmbiguous
    // 「重新生成」按钮的计数器：只靠 options 做 key 的话，选项没变就按不动（生成结果不变）。
    var regenerateNonce by remember { mutableStateOf(0) }

    val options = PasswordGenerator.Options(
        length = length,
        digits = digits,
        symbols = symbols,
        avoidAmbiguous = avoidAmbiguous,
    )
    // 选项或计数器一变就重算；随机源用密码学安全的 SecureRandom。
    val password = remember(options, regenerateNonce) {
        PasswordGenerator.generate(options, SecureRandom().asKotlinRandom())
    }
    val entropy = PasswordGenerator.entropyBits(options)

    ToolScaffold {
        ResultRowWithActions(
            text = password,
            copyLabel = "password",
            onRegenerate = { regenerateNonce++ },
            regenerateLabel = stringResource(R.string.quick_menu_pwd_regenerate),
        )

        FieldLabel("$length")
        Slider(
            value = length.toFloat(),
            onValueChange = { value ->
                // 拖动中只改内存：每像素写一次盘既浪费又可能排队卡顿；松手时 onValueChangeFinished 写一次。
                QuickMenuPreferences.preview { it.copy(passwordLength = value.roundToInt()) }
            },
            onValueChangeFinished = { QuickMenuPreferences.persist() },
            valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
        )

        ToggleRow(stringResource(R.string.quick_menu_pwd_digits), digits) { checked ->
            QuickMenuPreferences.update { it.copy(passwordDigits = checked) }
        }
        ToggleRow(stringResource(R.string.quick_menu_pwd_symbols), symbols) { checked ->
            QuickMenuPreferences.update { it.copy(passwordSymbols = checked) }
        }
        ToggleRow(stringResource(R.string.quick_menu_pwd_avoid_ambiguous), avoidAmbiguous) { checked ->
            QuickMenuPreferences.update { it.copy(passwordAvoidAmbiguous = checked) }
        }

        // 强度：文字等级 + 进度条 + 数字三条都给（用户选的口径）。
        // 早先只有一条没有刻度的进度条，用户不知道满格是多少、74% 意味着什么。
        val strength = PasswordGenerator.strengthOf(options)
        val strengthColor = strengthColor(strength)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.quick_menu_pwd_strength),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(strengthLabelRes(strength)),
                style = MaterialTheme.typography.labelLarge,
                color = strengthColor,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((entropy / ENTROPY_FULL_SCALE_BITS).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(strengthColor),
            )
        }
        Text(
            text = stringResource(
                R.string.quick_menu_pwd_entropy_scale,
                entropy.toInt(),
                ENTROPY_FULL_SCALE_BITS,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 摘要/文本对比已独立成「文本工具」，与"生成密码"无关，不再挂在这一页。
    }
}

/** 强度等级 → 文案。 */
private fun strengthLabelRes(strength: PasswordGenerator.Strength): Int = when (strength) {
    PasswordGenerator.Strength.WEAK -> R.string.quick_menu_pwd_strength_weak
    PasswordGenerator.Strength.FAIR -> R.string.quick_menu_pwd_strength_fair
    PasswordGenerator.Strength.STRONG -> R.string.quick_menu_pwd_strength_strong
    PasswordGenerator.Strength.VERY_STRONG -> R.string.quick_menu_pwd_strength_very_strong
}

/** 强度等级 → 颜色；文字本身已经说明结论，颜色只做辅助，所以只区分"弱"和其余。 */
@Composable
private fun strengthColor(strength: PasswordGenerator.Strength): Color = when (strength) {
    PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
    PasswordGenerator.Strength.FAIR -> MaterialTheme.colorScheme.tertiary
    PasswordGenerator.Strength.STRONG,
    PasswordGenerator.Strength.VERY_STRONG,
    -> MaterialTheme.colorScheme.primary
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------- 秒表

@Composable
internal fun QuickMenuStopwatchScreen() {
    // 状态放在 QuickMenuTimerRuntime（与组合无关）：离开本页去别的工具、再回来读数不会归零，
    // 也不会因为页面被销毁而丢掉正在跑的计时。
    val state = QuickMenuTimerRuntime.stopwatchState
    val nowMs = QuickMenuTimerRuntime.nowMs

    val elapsed = StopwatchLogic.elapsedAt(state, nowMs)
    val bestIndex = StopwatchLogic.bestLapIndex(state)
    val worstIndex = StopwatchLogic.worstLapIndex(state)

    ToolScaffold {
        Text(
            text = StopwatchLogic.format(elapsed),
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = {
                if (state.running) {
                    QuickMenuTimerRuntime.pauseStopwatch()
                } else {
                    QuickMenuTimerRuntime.startStopwatch()
                }
            }) {
                Icon(
                    imageVector = if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
            }
            IconButton(onClick = {
                if (state.running) {
                    QuickMenuTimerRuntime.lapStopwatch()
                } else {
                    // 写历史与复位都交给运行时，保证"即使此刻页面正在销毁"也能落盘。
                    QuickMenuTimerRuntime.finishStopwatch()
                }
            }) {
                Icon(
                    imageVector = if (state.running) Icons.Filled.Flag else MiuixIcons.Medium.Refresh,
                    contentDescription = null,
                )
            }
        }

        // 运行中那颗按钮的语义是"计次"，历史是在暂停后按 ⟳ 时写入的 —— 明确写出来，
        // 否则用户会以为"我记过一段却没进历史"（本轮 review 的第 6 条正是这个现象）。
        Text(
            text = stringResource(R.string.quick_menu_stopwatch_finish_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.laps.forEachIndexed { index, lapMs ->
            val highlight = when (index) {
                bestIndex -> Color(0xFF4CAF50)
                worstIndex -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "#${index + 1}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = StopwatchLogic.format(lapMs),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = highlight,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 倒计时

@Composable
internal fun QuickMenuCountdownScreen() {
    // 计时状态、到点闹钟、历史写入都在 QuickMenuTimerRuntime 里：本页被销毁（返回列表、切到别的
    // 工具）也不会让倒计时停下，而且暂停会真的撤掉闹钟。
    val state = QuickMenuTimerRuntime.countdownState
    val nowMs = QuickMenuTimerRuntime.nowMs
    // 从别的页面回来时，滑杆要对齐运行时里那个正在计时的时长。
    var customMinutes by remember {
        mutableFloatStateOf((state.durationMs / 60_000L).toFloat().coerceAtLeast(1f))
    }

    val remaining = CountdownLogic.remainingAt(state, nowMs)
    val progress = CountdownLogic.progress(state, nowMs)

    ToolScaffold {
        Text(
            text = StopwatchLogic.format(remaining),
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CountdownLogic.PRESET_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = state.durationMs == minutes * 60_000L,
                    onClick = {
                        customMinutes = minutes.toFloat()
                        QuickMenuTimerRuntime.setCountdownMinutes(minutes)
                    },
                    label = { Text(stringResource(R.string.quick_menu_timer_minutes, minutes)) },
                )
            }
        }

        FieldLabel(stringResource(R.string.quick_menu_timer_minutes, customMinutes.roundToInt()))
        Slider(
            value = customMinutes,
            onValueChange = {
                customMinutes = it
                QuickMenuTimerRuntime.setCountdownMinutes(it.roundToInt())
            },
            valueRange = CountdownLogic.MIN_MINUTES.toFloat()..CountdownLogic.MAX_MINUTES.toFloat(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = {
                if (state.running) {
                    QuickMenuTimerRuntime.pauseCountdown()
                } else {
                    // 到点闹钟由 QuickMenuTimerRuntime 自己按剩余时长排/撤（它持有 appContext），
                    // 不在这里处理 —— 这里以前借用了「N 分钟后提醒」，暂停撤不掉，到点照样响。
                    QuickMenuTimerRuntime.startCountdown()
                }
            }) {
                Icon(
                    imageVector = if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
            }
            IconButton(onClick = { QuickMenuTimerRuntime.resetCountdown() }) {
                Icon(imageVector = MiuixIcons.Medium.Refresh, contentDescription = null)
            }
        }
    }
}

// ---------------------------------------------------------------- 计时历史

@Composable
internal fun QuickMenuHistoryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<QuickMenuHistoryEntry>>(emptyList()) }

    // 每次进入页面重新读一次：历史是在秒表/倒计时页里写的，这里不必做实时订阅。
    LaunchedEffect(Unit) {
        entries = QuickMenuHistoryStore.read(context.applicationContext)
    }

    ToolScaffold {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.quick_menu_history_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    scope.launch {
                        QuickMenuHistoryStore.clear(context.applicationContext)
                        entries = emptyList()
                    }
                },
                enabled = entries.isNotEmpty(),
            ) {
                Text(stringResource(R.string.quick_menu_history_clear))
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.quick_menu_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entries.forEach { entry ->
            // 先在 composable 作用域把文案取好，避免在非内联 lambda 里调 stringResource。
            val lapsLabel = if (entry.lapsMs.isNotEmpty()) {
                stringResource(R.string.quick_menu_history_laps, entry.lapsMs.size)
            } else {
                null
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = historyKindLabel(entry.kind),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    // 相对时间用系统能力（随语言本地化，不需要新增文案）。
                    val secondary = listOfNotNull(
                        lapsLabel,
                        DateUtils.getRelativeTimeSpanString(entry.atEpochMs).toString(),
                    ).joinToString(" · ")
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = StopwatchLogic.format(entry.totalMs),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

@Composable
private fun historyKindLabel(kind: QuickMenuHistoryEntry.Kind): String = stringResource(
    when (kind) {
        QuickMenuHistoryEntry.Kind.STOPWATCH -> R.string.quick_menu_history_kind_stopwatch
        QuickMenuHistoryEntry.Kind.TIMER -> R.string.quick_menu_history_kind_timer
    },
)

// ---------------------------------------------------------------- 剪贴板小工具

/** 统一的短提示：截屏这类动作失败时必须让用户看见，不能静默什么都不做。 */
private fun showQuickMenuToast(context: Context, resId: Int, vararg args: Any) {
    // ⚠️ 必须走 OverlayToastWindow，不能用系统 Toast：系统 Toast 在**系统层**，而面板是覆盖层窗口，
    // 系统 Toast 会被面板整个盖住（用户反馈"提示看不见"）。OverlayToastWindow 挂在无障碍层，
    // 稳定叠在面板之上；拿不到悬浮窗时它自己会退回系统 Toast。
    OverlayToastWindow.show(context, context.getString(resId, *args))
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(label, text)) }
}

// ---------------------------------------------------------------- 文本工具

private enum class TextToolMode(val labelRes: Int) {
    REGEX(R.string.quick_menu_text_mode_regex),
    URL(R.string.quick_menu_text_mode_url),
    BASE64(R.string.quick_menu_text_mode_base64),

    /** 「文本对比」：两段文本直接比，**不走哈希**——哈希只能说同/不同，说不出差在哪。 */
    COMPARE(R.string.quick_menu_text_mode_compare),
    AES(R.string.quick_menu_text_mode_aes),
}

/**
 * 「文本工具」：URL / Base64 编解码、SHA-256 摘要（可与另一份摘要对比）、正则匹配、AES 加解密。
 *
 * - SHA-256 原本挂在密码页，已挪到这里；并且**加了第二个框**用于粘贴另一份摘要做对比 ——
 *   早先只有一个框，却写着"核对两段文本是否一致"，那是做不到的。
 * - AES 是唯一**不实时计算**的模式：PBKDF2 迭代 12 万次在手机上要几百毫秒，随输入实时算会卡住界面。
 */
@Composable
internal fun QuickMenuTextToolScreen() {
    // 模式记住上次选择；输入内容属于会话数据，不跨次留存。
    val mode = TextToolMode.entries
        .firstOrNull { it.name == QuickMenuPreferences.current.textToolMode }
        ?: TextToolMode.COMPARE
    var input by remember { mutableStateOf("") }

    /** 「文本对比」的第二段文本；输入内容属于会话数据，不持久化。 */
    var inputB by remember { mutableStateOf("") }
    var ignoreWhitespace by remember { mutableStateOf(false) }
    var regexPattern by remember { mutableStateOf("") }
    var ignoreCase by remember { mutableStateOf(false) }
    var aesPassword by remember { mutableStateOf("") }
    var aesDecryptMode by remember { mutableStateOf(false) }
    var aesOutput by remember { mutableStateOf("") }
    var aesErrorRes by remember { mutableStateOf<Int?>(null) }

    ToolScaffold {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextToolMode.entries.forEach { candidate ->
                FilterChip(
                    selected = mode == candidate,
                    onClick = {
                        QuickMenuPreferences.update { it.copy(textToolMode = candidate.name) }
                    },
                    label = { Text(stringResource(candidate.labelRes)) },
                )
            }
        }

        if (mode == TextToolMode.REGEX) {
            TextToolField(
                value = regexPattern,
                onValueChange = { regexPattern = it },
                label = stringResource(R.string.quick_menu_text_regex_pattern),
                mono = true,
                heightDp = 72,
            )
        }

        TextToolField(
            value = input,
            onValueChange = {
                input = it
                // AES 结果与输入绑死：输入一变就作废，避免"看着旧结果以为算的是新输入"。
                aesOutput = ""
                aesErrorRes = null
            },
            label = stringResource(
                // 对比模式下第一个框叫「文本 A」，另一个框在下面单独渲染。
                if (mode == TextToolMode.COMPARE) {
                    R.string.quick_menu_text_input_a
                } else {
                    R.string.quick_menu_text_input
                },
            ),
            mono = mode != TextToolMode.REGEX,
            heightDp = 120,
        )

        when (mode) {
            TextToolMode.REGEX -> {
                ToggleRow(
                    stringResource(R.string.quick_menu_text_regex_ignore_case),
                    ignoreCase,
                ) { ignoreCase = it }
                RegexSection(pattern = regexPattern, input = input, ignoreCase = ignoreCase)
            }

            TextToolMode.URL -> CodecSection(
                encoded = TextToolkit.urlEncode(input),
                decoded = TextToolkit.urlDecode(input),
                inputIsBlank = input.isBlank(),
            )

            TextToolMode.BASE64 -> CodecSection(
                encoded = TextToolkit.base64Encode(input),
                decoded = TextToolkit.base64Decode(input),
                inputIsBlank = input.isBlank(),
            )

            TextToolMode.COMPARE -> {
                TextToolField(
                    value = inputB,
                    onValueChange = { inputB = it },
                    label = stringResource(R.string.quick_menu_text_input_b),
                    mono = false,
                    heightDp = 120,
                )
                ToggleRow(
                    stringResource(R.string.quick_menu_text_compare_ignore_whitespace),
                    ignoreWhitespace,
                ) { ignoreWhitespace = it }
                CompareSection(a = input, b = inputB, ignoreWhitespace = ignoreWhitespace)
            }

            TextToolMode.AES -> AesSection(
                password = aesPassword,
                onPasswordChange = {
                    aesPassword = it
                    aesOutput = ""
                    aesErrorRes = null
                },
                decryptMode = aesDecryptMode,
                onDecryptModeChange = {
                    aesDecryptMode = it
                    aesOutput = ""
                    aesErrorRes = null
                },
                input = input,
                output = aesOutput,
                errorRes = aesErrorRes,
                onResult = { text, errorRes ->
                    aesOutput = text
                    aesErrorRes = errorRes
                },
            )
        }
    }
}

@Composable
private fun TextToolField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    mono: Boolean,
    heightDp: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        shape = RoundedCornerShape(16.dp),
        textStyle = if (mono) {
            MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        } else {
            MaterialTheme.typography.bodyMedium
        },
    )
}

@Composable
private fun TextToolError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

/** URL / Base64 共用：同时给出"编码结果"与"解码结果"，两个方向都能用。 */
@Composable
private fun CodecSection(
    encoded: String,
    decoded: TextToolkit.DecodeResult,
    inputIsBlank: Boolean,
) {
    // 没输入时不要把"编码结果/解码结果"两个标题先摆出来 —— 用户会以为这个工具只能解码。
    if (inputIsBlank) {
        Text(
            text = stringResource(R.string.quick_menu_text_codec_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FieldLabel(stringResource(R.string.quick_menu_text_result_encoded))
    ResultRow(text = encoded, copyLabel = "text-encoded")
    FieldLabel(stringResource(R.string.quick_menu_text_result_decoded))
    when (decoded) {
        is TextToolkit.DecodeResult.Ok -> if (decoded.text.isNotBlank()) {
            ResultRow(text = decoded.text, copyLabel = "text-decoded")
        }
        TextToolkit.DecodeResult.Malformed ->
            TextToolError(stringResource(R.string.quick_menu_text_error_malformed))
        TextToolkit.DecodeResult.NotText ->
            TextToolError(stringResource(R.string.quick_menu_text_error_not_text))
    }
}

/**
 * 「文本对比」的结论。
 *
 * 用**人话**说结论，并且给出哈希给不出的信息：差在第几个字符、长度差多少、是否只是空白或大小写不同。
 * （这正是把"比两段文本"从哈希里拿出来的原因——哈希只能回一句"不同"。）
 */
@Composable
private fun CompareSection(a: String, b: String, ignoreWhitespace: Boolean) {
    val result = remember(a, b, ignoreWhitespace) {
        TextToolkit.compareText(a, b, ignoreWhitespace)
    }
    when (result) {
        TextToolkit.TextCompareResult.EmptySide -> TextToolHint(
            stringResource(R.string.quick_menu_text_compare_empty),
        )

        TextToolkit.TextCompareResult.Identical -> Text(
            text = stringResource(R.string.quick_menu_text_compare_same),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        is TextToolkit.TextCompareResult.Different -> {
            TextToolError(
                stringResource(R.string.quick_menu_text_compare_different, result.index + 1),
            )
            // 长度相同时不显示这条：那时"相差 0"纯属噪音。
            if (result.lengthDelta != 0) {
                TextToolHint(
                    stringResource(
                        R.string.quick_menu_text_compare_length,
                        kotlin.math.abs(result.lengthDelta),
                    ),
                )
            }
            if (result.identicalIgnoringWhitespace) {
                TextToolHint(stringResource(R.string.quick_menu_text_compare_only_whitespace))
            }
            if (result.identicalIgnoringCase) {
                TextToolHint(stringResource(R.string.quick_menu_text_compare_only_case))
            }
        }
    }
}

/** 中性的补充说明（区别于红色的错误）。 */
@Composable
private fun TextToolHint(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RegexSection(pattern: String, input: String, ignoreCase: Boolean) {
    val result = remember(pattern, input, ignoreCase) {
        TextToolkit.regexFind(pattern, input, ignoreCase)
    }
    when (result) {
        is TextToolkit.RegexResult.InvalidPattern ->
            TextToolError(stringResource(R.string.quick_menu_text_regex_invalid, result.message))

        TextToolkit.RegexResult.NoMatch ->
            TextToolError(stringResource(R.string.quick_menu_text_regex_no_match))

        is TextToolkit.RegexResult.Matches -> {
            FieldLabel(stringResource(R.string.quick_menu_text_regex_count, result.matches.size))
            result.matches.forEach { match ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = match.value.ifBlank { "∅" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    // 位置用中性的区间写法，省掉一条需要翻译的文案。
                    Text(
                        text = "[${match.range.first}..${match.range.last}]",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                match.groups.forEachIndexed { groupIndex, groupValue ->
                    Text(
                        text = "  $${groupIndex + 1} = $groupValue",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (result.truncated) {
                TextToolError(
                    stringResource(
                        R.string.quick_menu_text_regex_truncated,
                        TextToolkit.MAX_REGEX_MATCHES,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AesSection(
    password: String,
    onPasswordChange: (String) -> Unit,
    decryptMode: Boolean,
    onDecryptModeChange: (Boolean) -> Unit,
    input: String,
    output: String,
    errorRes: Int?,
    onResult: (String, Int?) -> Unit,
) {
    TextToolField(
        value = password,
        onValueChange = onPasswordChange,
        label = stringResource(R.string.quick_menu_text_aes_password),
        mono = true,
        heightDp = 72,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = !decryptMode,
            onClick = { onDecryptModeChange(false) },
            label = { Text(stringResource(R.string.quick_menu_text_aes_encrypt)) },
        )
        FilterChip(
            selected = decryptMode,
            onClick = { onDecryptModeChange(true) },
            label = { Text(stringResource(R.string.quick_menu_text_aes_decrypt)) },
        )
    }
    Button(
        onClick = {
            if (decryptMode) {
                when (val result = TextToolkit.aesDecrypt(password, input)) {
                    is TextToolkit.AesResult.Ok -> onResult(result.text, null)
                    TextToolkit.AesResult.Malformed ->
                        onResult("", R.string.quick_menu_text_error_malformed)
                    TextToolkit.AesResult.WrongPasswordOrTampered ->
                        onResult("", R.string.quick_menu_text_aes_failed)
                }
            } else {
                onResult(TextToolkit.aesEncrypt(password, input), null)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = password.isNotEmpty(),
    ) {
        Text(stringResource(R.string.quick_menu_text_aes_run))
    }
    errorRes?.let { TextToolError(stringResource(it)) }
    if (output.isNotBlank()) {
        FieldLabel(stringResource(R.string.quick_menu_text_result))
        ResultRow(text = output, copyLabel = "aes")
    }
    Text(
        text = stringResource(R.string.quick_menu_text_aes_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------- 图像来源小工具

/** 从选中的图片读成位图；解码失败返回 null（例如用户选了非图片或文件已失效）。 */
private fun decodeBitmap(context: Context, uri: Uri): Bitmap? =
    ImageDecodeGuard.decodeUri(
        context = context,
        uri = uri,
        maxSidePx = ImageDecodeGuard.MAX_DISPLAY_SIDE_PX,
    )

/**
 * 把被扫描的图和码的位置一起显示，点标记即选中（微信那种）。
 *
 * 用 `aspectRatio` 让容器形状**正好等于图片形状**：容器边界就是图片边界，因此不需要另算
 * `ContentScale.Fit` 的 letterbox——手算留白是这类标记最容易画偏的地方。点位是归一化的，
 * 直接乘容器尺寸即可。
 */
@Composable
private fun ScanImageWithMarkers(
    image: Bitmap,
    results: List<BarcodeScanResult>,
    selectedText: String,
    onSelect: (String) -> Unit,
) {
    val imageBitmap = remember(image) { image.asImageBitmap() }
    val accent = MaterialTheme.colorScheme.primary
    val markerBoxes = remember(results) {
        results.map { result ->
            result to result.points.boundingBoxOrNull(MARKER_MIN_SIZE)
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(image.width.toFloat() / image.height.toFloat())
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
    ) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        // 压暗 + "挖亮"标记区 + 四角括号。
        // 只压暗不挖亮的话，码本身会一起变暗、反而更难看清；这里把标记区按原亮度重画一遍。
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxPx = markerBoxes.mapNotNull { (_, box) ->
                box?.let {
                    Rect(
                        left = size.width * it.left,
                        top = size.height * it.top,
                        right = size.width * it.right,
                        bottom = size.height * it.bottom,
                    )
                }
            }
            drawRect(color = Color.Black.copy(alpha = MARKER_DIM_ALPHA))
            boxPx.forEach { rect ->
                clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                    drawImage(
                        image = imageBitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(imageBitmap.width, imageBitmap.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    )
                }
            }
            boxPx.forEach { rect ->
                drawMarkerCorners(rect = rect, color = Color.White)
            }
        }
        markerBoxes.forEachIndexed { index, (result, box) ->
            if (box == null) return@forEachIndexed
            val selected = result.text == selectedText
            Box(
                modifier = Modifier
                    .offset(x = boxWidth * box.left, y = boxHeight * box.top)
                    .size(
                        width = boxWidth * (box.right - box.left),
                        height = boxHeight * (box.bottom - box.top),
                    )
                    .clickable { onSelect(result.text) },
            ) {
                Text(
                    text = if (selected) "✓${index + 1}" else "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .background(if (selected) accent else Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(width = 2.dp, color = accent, shape = RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }
}

/** 画一个"四角括号"的选中框：只画八个短笔，不挡住码本身。 */
private fun DrawScope.drawMarkerCorners(rect: Rect, color: Color) {
    val side = minOf(rect.width, rect.height)
    val arm = side * MARKER_CORNER_FRACTION
    val stroke = maxOf(2f, side * 0.03f)
    val left = rect.left
    val top = rect.top
    val right = rect.right
    val bottom = rect.bottom
    // 左上
    drawLine(color, Offset(left, top + arm), Offset(left, top), strokeWidth = stroke)
    drawLine(color, Offset(left, top), Offset(left + arm, top), strokeWidth = stroke)
    // 右上
    drawLine(color, Offset(right - arm, top), Offset(right, top), strokeWidth = stroke)
    drawLine(color, Offset(right, top), Offset(right, top + arm), strokeWidth = stroke)
    // 左下
    drawLine(color, Offset(left, bottom - arm), Offset(left, bottom), strokeWidth = stroke)
    drawLine(color, Offset(left, bottom), Offset(left + arm, bottom), strokeWidth = stroke)
    // 右下
    drawLine(color, Offset(right - arm, bottom), Offset(right, bottom), strokeWidth = stroke)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - arm), strokeWidth = stroke)
}

/**
 * 取图里所有可识别的条码/二维码（**带归一化定位点**，供图上标记；按文本去重）。
 *
 * 先按原方向扫；扫不到再按 90° 逐次旋转重试。理由（取自 quickdash 的相册路径）：**一维条码有方向性**
 * ——照片可能是横着拍的，或 EXIF 方向没被正确处理，此时二维码仍能认出（ZXing 的二维码检测对旋转不像
 * 一维码那么敏感），一维码却会整张扫不出来。
 *
 * 重试拿到的是"旋转后那张图"的坐标，所以必须按**反方向**转回原图坐标系，否则标记会整体歪掉。
 * 旋转是逆变换：`rotatePoints(points, -degrees)`（`rotatePoint` 对负角度做了归一化）。
 */

private fun scanBarcodes(bitmap: Bitmap): List<BarcodeScanResult> {
    val direct = runCatching { ZxingBarcodeScanner.scanBitmap(bitmap) }.getOrDefault(emptyList())
    if (direct.isNotEmpty()) return direct.cleaned()

    for (degrees in listOf(90, 180, 270)) {
        val rotated = rotateBitmap(bitmap, degrees) ?: continue
        val found = runCatching { ZxingBarcodeScanner.scanBitmap(rotated) }.getOrDefault(emptyList())
        if (rotated !== bitmap) rotated.recycle()
        if (found.isNotEmpty()) {
            return found.map { result ->
                result.copy(points = QuickMenuScanFrame.rotatePoints(result.points, -degrees))
            }.cleaned()
        }
    }
    return emptyList()
}

private fun List<BarcodeScanResult>.cleaned(): List<BarcodeScanResult> =
    filter { it.text.isNotBlank() }.distinctBy { it.text }

/** 按角度旋转位图；失败返回 null（调用方会跳过该方向）。 */
private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap? = runCatching {
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}.getOrNull()

/**
 * 一次扫描的结果落地。
 *
 * **只有一个码时与以前完全一样**：直接回填、不出现列表、也不显示图——最常见的场景一点没变复杂。
 * 只有扫到多个码时才把候选（含定位点）与被扫描的图交给界面。
 */
private fun applyScanResults(
    context: Context,
    results: List<BarcodeScanResult>,
    image: Bitmap?,
    onContentChange: (String) -> Unit,
    onResultsChange: (List<BarcodeScanResult>) -> Unit,
    onImageChange: (Bitmap?) -> Unit,
) {
    when (val outcome = scanOutcome(results.map { it.text })) {
        ScanOutcome.NoCode -> {
            onResultsChange(emptyList())
            onImageChange(null)
            showQuickMenuToast(context, R.string.quick_menu_scan_no_code_found)
        }

        is ScanOutcome.Single -> {
            // 单码也把结果与图给界面：用户要能看见"它认的是哪一个"。
            // （以前这里把两者都丢掉，相机路径因此表现为"没有定格图"——用户反馈的正是这个。）
            onResultsChange(results.filter { it.text.isNotBlank() }.distinctBy { it.text })
            onImageChange(image)
            onContentChange(outcome.text)
        }

        is ScanOutcome.Multiple -> {
            onResultsChange(results.filter { it.text.isNotBlank() }.distinctBy { it.text })
            onImageChange(image)
            onContentChange(outcome.texts.first())
        }
    }
}