package com.slideindex.app.settings.macro.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.macro.MacroValidation
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsLabeledTextFieldRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.util.PickerAppIconBitmap
import com.slideindex.app.ui.SymbolsIcon

/**
 * 宏的新建 / 编辑页。
 *
 * 表单是"即时生效"的：名字与目标 App 一改就写回调用方（落盘），这样中途去挑应用、
 * 或去录制器里录一段再回来，都不会丢输入；底部按钮只负责收尾（保存回列表 / 取消）。
 */
/**
 * 「目标应用」这一行的图标：选好之后直接显示那个 App 自己的图标；没选（或图标读不出来）时用通用图标。
 */
@Composable
private fun MacroTargetAppIcon(target: String, contentDescription: String) {
    val parsed = com.slideindex.app.launcher.AppTarget.parse(target)
    val packageName = parsed.packageName
    if (packageName.isBlank()) {
        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
        return
    }
    val context = LocalContext.current
    var bitmap by remember(target) { mutableStateOf(PickerAppIconBitmap.peek(packageName)) }
    var failed by remember(packageName) { mutableStateOf(false) }
    LaunchedEffect(target) {
        if (bitmap == null && !failed) {
            val loaded = PickerAppIconBitmap.load(context, packageName)
            if (loaded == null) failed = true else bitmap = loaded
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            // 分身目标在图标右上角补黄点（主实例原样）。
            bitmap = if (parsed.isClone) {
                com.slideindex.app.util.CloneBadge
                    .decorate(current.asAndroidBitmap(), true)
                    ?.asImageBitmap() ?: current
            } else {
                current
            },
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            contentScale = ContentScale.Fit,
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MacroEditScreen(
    context: Context,
    titleResId: Int,
    name: String,
    targetPackage: String,
    recording: Boolean,
    /** 有没有真的改动过；没改动时「保存」是灰的（避免凭空存出一条空宏）。 */
    saveEnabled: Boolean = true,
    onNameChange: (String) -> Unit,
    onPickTargetApp: () -> Unit,
    onStartRecording: () -> Unit,
    onTestRun: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var errorResId by rememberSaveable { mutableStateOf<Int?>(null) }
    val appLabel = remember(context, targetPackage) { resolveAppLabel(context, targetPackage) }

    val nameSectionTitle = stringResource(R.string.macro_section_basic)
    val recordSectionTitle = stringResource(R.string.macro_section_record)
    val helpText = stringResource(
        if (recording) R.string.macro_help_recording else R.string.macro_help_idle,
    )
    val targetSummary = if (targetPackage.isBlank()) {
        stringResource(R.string.macro_target_none)
    } else {
        stringResource(R.string.macro_target_value, appLabel.ifBlank { targetPackage }, targetPackage)
    }

    SettingsLazyScreenScaffold(
        title = stringResource(titleResId),
        onBack = onBack,
    ) {
        // 说明卡片放最顶部（与仓库其它页面一致：先看说明，再操作）。
        settingsLazyTipCard(key = "macro-help", text = helpText)

        // 名字为空时的提示：只用一句红字，不占用卡片高度。
        errorResId?.let { resId ->
            item(key = "macro-error") {
                Text(
                    text = stringResource(resId),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        settingsLazySmallTitle(key = "macro-section-basic", title = nameSectionTitle)
        groupedCardItems(
            keyPrefix = "macro-basic",
            items = listOf(
                settingsCardScopeItem("macro-name") {
                    SettingsLabeledTextFieldRow(
                        key = "macro-name-field",
                        label = stringResource(R.string.macro_name_label),
                        value = name,
                        onValueChange = { text ->
                            errorResId = null
                            onNameChange(text)
                        },
                    )
                },
                settingsCardScopeItem("macro-target") {
                    SettingNavigationRow(
                        icon = { label -> MacroTargetAppIcon(targetPackage, label) },
                        title = stringResource(R.string.macro_target_label),
                        subtitle = targetSummary,
                        onClick = onPickTargetApp,
                    )
                },
            ),
        )

        settingsLazySmallTitle(key = "macro-section-record", title = recordSectionTitle)
        groupedCardItems(
            keyPrefix = "macro-record",
            items = listOf(
                settingsCardScopeItem("macro-start-recording") {
                    SettingNavigationRow(
                        icon = { label -> SymbolsIcon("Videocam", contentDescription = label) },
                        title = stringResource(
                            if (recording) R.string.macro_recording else R.string.macro_start_recording,
                        ),
                        subtitle = stringResource(R.string.macro_start_recording_desc),
                        enabled = !recording,
                        onClick = {
                            if (MacroValidation.isNameValid(name)) {
                                errorResId = null
                                onStartRecording()
                            } else {
                                errorResId = R.string.macro_error_name_required
                            }
                        },
                    )
                },
                // 「试运行」：不绑手势也能跑一次，方便判断"回放本身"到底有没有生效。
                settingsCardScopeItem("macro-test-run") {
                    SettingNavigationRow(
                        icon = { label -> SymbolsIcon("PlayArrow", contentDescription = label) },
                        title = stringResource(R.string.macro_test_run),
                        subtitle = stringResource(R.string.macro_test_run_desc),
                        enabled = !recording && targetPackage.isNotBlank(),
                        onClick = onTestRun,
                    )
                },
            ),
        )

        // 收尾按钮：放在内容最后（这个 scaffold 没有 bottomBar 槽位）。
        item(key = "macro-actions") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            if (MacroValidation.isNameValid(name)) {
                                errorResId = null
                                onSave()
                            } else {
                                errorResId = R.string.macro_error_name_required
                            }
                        },
                        enabled = saveEnabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.macro_save))
                    }
                }
            }
        }
    }
}
