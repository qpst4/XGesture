package com.slideindex.app.ui

import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.otp.LsposedInjectorProbe
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.StatusRow
import com.slideindex.app.ui.settings.components.StatusTone
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.xposed.bridge.ModuleBridgeStatusProbe
import com.slideindex.app.xposed.bridge.ModuleBridgeStatusStore
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.bridge.ModuleStatusFields

/**
 * 验证码一级页：顶部两行状态（无障碍 / LSPosed）+ 四个入口。
 *
 * 入口分别进入：验证码提取、验证码自动填充、验证码提取规则、验证码记录。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpHubScreen(
    settings: AppSettings,
    onExit: () -> Unit,
    accessibilityGranted: Boolean,
    onRequestAccessibility: () -> Unit,
    onOpenExtraction: () -> Unit,
    onOpenAutoFill: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenRecords: () -> Unit,
    statusRefreshKey: Int = 0,
    onRestartPhoneProcess: () -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var checking by remember { mutableStateOf(false) }
    var moduleState by remember { mutableStateOf<ModuleBridgeStatusProbe.Status?>(null) }
    var moduleCodeState by remember { mutableStateOf<ModuleStatusFields.CodeState?>(null) }
    var phoneSnapshot by remember {
        mutableStateOf(ModuleBridgeStatusStore.read(appContext, ModuleHookBridgeContract.CHANNEL_PHONE))
    }
    var injectReady by remember { mutableStateOf<Boolean?>(null) }
    val probe = remember(appContext) {
        {
            if (!checking) {
                checking = true
                ModuleBridgeStatusProbe.probe(appContext) { status, detail ->
                    checking = false
                    moduleState = status
                    moduleCodeState = ModuleStatusFields.codeStateOfDetail(appContext, detail)
                    phoneSnapshot = ModuleBridgeStatusStore.read(
                        appContext,
                        ModuleHookBridgeContract.CHANNEL_PHONE,
                    )
                }
                LsposedInjectorProbe.probe(appContext) { status, _ ->
                    injectReady = status == LsposedInjectorProbe.Status.Ready
                }
            }
        }
    }
    LaunchedEffect(statusRefreshKey) {
        probe()
        // 进程重启后状态通道需要一点时间重新注册，稍后再自动重测一次。
        kotlinx.coroutines.delay(2500)
        probe()
    }

    val statusItems = listOf(
        settingsCardScopeItem("otp-a11y-status") {
            StatusRow(
                title = stringResource(R.string.otp_auto_input_service_setup_title),
                pill = if (accessibilityGranted) {
                    stringResource(R.string.otp_a11y_pill_on)
                } else {
                    stringResource(R.string.otp_a11y_pill_off)
                },
                tone = if (accessibilityGranted) StatusTone.Good else StatusTone.Bad,
                detail = stringResource(R.string.otp_auto_input_service_setup_desc),
                segmentKey = "otp-a11y-status",
                onClick = onRequestAccessibility,
            )
        },
        settingsCardScopeItem("otp-lsposed-status") {
            val codeState = moduleCodeState
            // 「模块」只看两件事：system_server 里有没有回应、跑的是不是当前版本的代码。
            // 不能拿 ModuleBridgeStatusProbe.Status.Ready 当依据——那是"手势接管真的生效"，
            // 手势开关全关时它是 Armed，会把健康的模块显示成未就绪。
            val moduleReady = moduleState != null &&
                moduleState != ModuleBridgeStatusProbe.Status.NotReady &&
                codeState == ModuleStatusFields.CodeState.Current
            val phoneStale = ModuleStatusFields.codeStateOf(appContext, phoneSnapshot) ==
                ModuleStatusFields.CodeState.Stale
            val phoneReady = phoneSnapshot.state == ModuleHookBridgeContract.STATUS_STATE_READY
            val injectReadyNow = injectReady == true
            val pill: String
            val tone: StatusTone
            when {
                checking -> {
                    pill = stringResource(R.string.system_gesture_takeover_module_pill_checking)
                    tone = StatusTone.Neutral
                }
                codeState == ModuleStatusFields.CodeState.Stale || phoneStale -> {
                    pill = stringResource(R.string.system_gesture_takeover_module_pill_restart_needed)
                    tone = StatusTone.Bad
                }
                !moduleReady -> {
                    pill = stringResource(R.string.system_gesture_takeover_module_pill_not_ready)
                    tone = StatusTone.Bad
                }
                phoneReady && injectReadyNow -> {
                    pill = stringResource(R.string.system_gesture_takeover_module_pill_ready)
                    tone = StatusTone.Good
                }
                else -> {
                    // 模块已就绪，但短信通道 / 系统注入还有一项没就绪：这三件事本来互相独立，
                    // 不该因为一项没到位就把整行说成「未生效」。
                    pill = stringResource(R.string.otp_lsposed_pill_partial)
                    tone = StatusTone.Neutral
                }
            }
            val moduleLabel = stringResource(R.string.otp_lsposed_row_module_label)
            val smsLabel = stringResource(R.string.otp_lsposed_row_sms_label)
            val injectLabel = stringResource(R.string.otp_lsposed_inject_title)
            val okText = stringResource(R.string.system_gesture_takeover_module_pill_ready)
            val badText = stringResource(R.string.system_gesture_takeover_module_pill_not_ready)
            val injectSummary = when (injectReady) {
                true -> stringResource(R.string.otp_status_inject_pill_ready)
                false -> badText
                null -> stringResource(R.string.system_gesture_takeover_module_pill_unknown)
            }
            StatusRow(
                title = stringResource(R.string.otp_lsposed_status_section),
                pill = pill,
                tone = tone,
                detail = "$moduleLabel：${if (moduleReady) okText else badText}" +
                    " ｜ $smsLabel：${if (phoneReady) okText else badText}" +
                    " ｜ $injectLabel：$injectSummary",
                segmentKey = "otp-lsposed-status",
                onClick = probe,
            )
        },
    )

    val entryItems = listOf(
        settingsCardScopeItem("entry-extraction") {
            SettingNavigationRow(
                icon = { label -> SymbolsIcon("Sms", contentDescription = label) },
                title = stringResource(R.string.otp_extraction_entry_title),
                subtitle = stringResource(R.string.otp_extraction_entry_desc),
                onClick = onOpenExtraction,
            )
        },
        settingsCardScopeItem("entry-auto-fill") {
            SettingNavigationRow(
                icon = { label -> SymbolsIcon("Keyboard", contentDescription = label) },
                title = stringResource(R.string.otp_auto_input_entry_title),
                subtitle = stringResource(R.string.otp_auto_input_entry_desc),
                onClick = onOpenAutoFill,
            )
        },
        settingsCardScopeItem("entry-rules") {
            SettingNavigationRow(
                icon = { label -> SymbolsIcon("Rule", contentDescription = label, mirrored = true) },
                title = stringResource(R.string.otp_match_rules_entry_title),
                subtitle = stringResource(R.string.otp_match_rules_entry_desc),
                onClick = onOpenRules,
            )
        },
        settingsCardScopeItem("entry-records") {
            SettingNavigationRow(
                icon = { label -> SymbolsIcon("History", contentDescription = label) },
                title = stringResource(R.string.otp_records_title),
                subtitle = stringResource(R.string.otp_records_entry_desc),
                onClick = onOpenRecords,
            )
        },
    )

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.otp_hub_entry_title),
        subtitle = stringResource(R.string.otp_hub_entry_desc),
        onBack = onExit,
    ) {
        groupedCardItems(keyPrefix = "otp-status", items = statusItems)
        groupedCardItems(keyPrefix = "otp-entries", items = entryItems)
    }
}
