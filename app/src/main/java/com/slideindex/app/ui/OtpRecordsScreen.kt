package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.otp.OtpAutoFillUiLabels
import com.slideindex.app.otp.OtpClipboardHelper
import com.slideindex.app.otp.OtpRecord
import com.slideindex.app.otp.OtpRecordCategory
import com.slideindex.app.otp.OtpRecordFillStatus
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.viewmodel.OtpRecordsViewModel
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.DateFormat
import java.util.Date

enum class OtpRecordSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
}

class OtpRecordsUi(
    val appendListItems: LazyListScope.() -> Unit,
    val overlays: @Composable () -> Unit,
    val scaffoldActions: @Composable RowScope.() -> Unit,
)

@Composable
fun rememberOtpRecordsUi(
    embeddedInHub: Boolean,
    onOpenTestFlow: (() -> Unit)?,
    viewModel: OtpRecordsViewModel = hiltViewModel(),
): OtpRecordsUi {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val records by viewModel.records.collectAsStateWithLifecycle()
    var sortOrder by remember { mutableStateOf(OtpRecordSortOrder.NEWEST_FIRST) }
    var filterPackage by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<OtpRecord?>(null) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    // 回到记录页就强制读一次盘：跨进程的写通知可能丢，内存快照会停在旧的"填充中"。
    LifecycleResumeEffect(Unit) {
        viewModel.refreshRecords()
        onPauseOrDispose { }
    }

    val packageOptions = remember(records) {
        records.map { it.packageName }.distinct().sorted()
    }
    val appFilterLabels = remember(packageOptions) {
        packageOptions.associateWith { pkg -> viewModel.ensureAppInfo(pkg)?.label ?: pkg }
    }
    val allFilterLabel = stringResource(R.string.otp_records_filter_all)
    // 筛选也是"小列表里选一项"，和排序一样用下拉菜单，不再开 bottom sheet。
    val filterEntry = remember(packageOptions, filterPackage, appFilterLabels, allFilterLabel) {
        DropdownEntry(
            items = buildList {
                add(
                    DropdownItem(
                        text = allFilterLabel,
                        selected = filterPackage == null,
                        onClick = { filterPackage = null },
                    ),
                )
                packageOptions.forEach { pkg ->
                    add(
                        DropdownItem(
                            text = appFilterLabels[pkg] ?: pkg,
                            selected = filterPackage == pkg,
                            onClick = { filterPackage = pkg },
                        ),
                    )
                }
            },
        )
    }
    val filteredRecords = remember(records, filterPackage, sortOrder) {
        val filtered = if (filterPackage == null) {
            records
        } else {
            records.filter { it.packageName == filterPackage }
        }
        when (sortOrder) {
            OtpRecordSortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.timestampMs }
            OtpRecordSortOrder.OLDEST_FIRST -> filtered.sortedBy { it.timestampMs }
        }
    }

    val filterSortActions: @Composable RowScope.() -> Unit = {
        OtpRecordsFilterSortActions(
            sortOrder = sortOrder,
            filterEntry = filterEntry,
            onSortNewest = { sortOrder = OtpRecordSortOrder.NEWEST_FIRST },
            onSortOldest = { sortOrder = OtpRecordSortOrder.OLDEST_FIRST },
        )
    }

    val senderHint = stringResource(R.string.otp_records_sender_hint)

    val lazyItems: LazyListScope.() -> Unit = {
        if (embeddedInHub) {
            item(key = "records_toolbar") {
                OtpRecordsEmbeddedToolbar(
                    embeddedInHub = true,
                    sortOrder = sortOrder,
                    filterEntry = filterEntry,
                    onSortNewest = { sortOrder = OtpRecordSortOrder.NEWEST_FIRST },
                    onSortOldest = { sortOrder = OtpRecordSortOrder.OLDEST_FIRST },
                )
            }
        }
        otpRecordsListItems(
            embeddedInHub = embeddedInHub,
            records = records,
            filteredRecords = filteredRecords,
            viewModel = viewModel,
            dateFormat = dateFormat,
            senderHint = senderHint,
            onOpenTestFlow = onOpenTestFlow,
            onCopy = { record ->
                if (record.code.isBlank()) return@otpRecordsListItems
                OtpClipboardHelper.copyCode(context, record.code)
                Toast.makeText(
                    context,
                    appContext.getString(R.string.otp_copied_to_clipboard, record.code),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDelete = { pendingDelete = it },
        )
    }

    val overlays: @Composable () -> Unit = {
        val deleteRecord = pendingDelete
        MiuixConfirmDialog(
            show = deleteRecord != null,
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.otp_records_delete_title),
            message = deleteRecord?.let {
                stringResource(R.string.otp_records_delete_message, it.code)
            },
            confirmText = stringResource(R.string.otp_rules_delete),
            dismissText = stringResource(R.string.shell_panel_close),
            onConfirm = {
                deleteRecord?.let { record ->
                    viewModel.deleteRecord(record.id)
                    pendingDelete = null
                }
            },
        )
    }

    return OtpRecordsUi(
        appendListItems = lazyItems,
        overlays = overlays,
        scaffoldActions = filterSortActions,
    )
}

fun LazyListScope.otpRecordsListItems(
    embeddedInHub: Boolean,
    records: List<OtpRecord>,
    filteredRecords: List<OtpRecord>,
    viewModel: OtpRecordsViewModel,
    dateFormat: DateFormat,
    senderHint: String,
    onOpenTestFlow: (() -> Unit)?,
    onCopy: (OtpRecord) -> Unit,
    onDelete: (OtpRecord) -> Unit,
) {
    if (records.isEmpty()) {
        item(key = "records_empty") {
            OtpRecordsEmptyState(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                onOpenTestFlow = onOpenTestFlow,
                embeddedInHub = embeddedInHub,
            )
        }
        return
    }

    if (embeddedInHub) {
        settingsLazyHint(key = "sender_hint", text = senderHint)
    }

    if (filteredRecords.isEmpty()) {
        item(key = "filter_empty") {
            Text(
                text = stringResource(R.string.otp_records_filter_empty),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            )
        }
    } else {
        groupedCardItems(
            keyPrefix = "otp-records",
            items = filteredRecords.map { record ->
                val appInfo = viewModel.getCachedAppInfo(record.packageName)
                CardItem(key = record.id) {
                    OtpRecordRow(
                        record = record,
                        appInfo = appInfo,
                        timeLabel = dateFormat.format(Date(record.timestampMs)),
                        onCopy = { onCopy(record) },
                        onDelete = { onDelete(record) },
                    )
                }
            },
        )
    }
}

@Composable
private fun OtpRecordsFilterSortActions(
    sortOrder: OtpRecordSortOrder,
    filterEntry: DropdownEntry,
    onSortNewest: () -> Unit,
    onSortOldest: () -> Unit,
) {
    val newestLabel = stringResource(R.string.otp_records_sort_newest)
    val oldestLabel = stringResource(R.string.otp_records_sort_oldest)
    val sortEntry = remember(sortOrder, newestLabel, oldestLabel) {
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = newestLabel,
                    selected = sortOrder == OtpRecordSortOrder.NEWEST_FIRST,
                    onClick = onSortNewest,
                ),
                DropdownItem(
                    text = oldestLabel,
                    selected = sortOrder == OtpRecordSortOrder.OLDEST_FIRST,
                    onClick = onSortOldest,
                ),
            ),
        )
    }
    OverlayIconDropdownMenu(entry = filterEntry) {
        Icon(
            Icons.Default.FilterList,
            contentDescription = stringResource(R.string.otp_records_filter),
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
    OverlayIconDropdownMenu(entry = sortEntry) {
        Icon(
            Icons.Default.SwapVert,
            contentDescription = stringResource(R.string.otp_records_sort),
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun OtpRecordsEmbeddedToolbar(
    embeddedInHub: Boolean,
    sortOrder: OtpRecordSortOrder,
    filterEntry: DropdownEntry,
    onSortNewest: () -> Unit,
    onSortOldest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 28.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.otp_records_title),
            style = MiuixTheme.textStyles.title4,
            modifier = Modifier.weight(1f),
        )
        OtpRecordsFilterSortActions(
            sortOrder = sortOrder,
            filterEntry = filterEntry,
            onSortNewest = onSortNewest,
            onSortOldest = onSortOldest,
        )
    }
}

@Composable
private fun OtpRecordsEmptyState(
    modifier: Modifier = Modifier,
    onOpenTestFlow: (() -> Unit)? = null,
    embeddedInHub: Boolean = false,
) {
    Column(
        modifier = modifier.padding(horizontal = if (embeddedInHub) 0.dp else 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MailOutline,
            contentDescription = stringResource(R.string.otp_records_empty_title),
            modifier = Modifier.size(72.dp),
            tint = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.5f),
        )
        Text(
            text = stringResource(R.string.otp_records_empty_title),
            style = MiuixTheme.textStyles.title2,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 20.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.otp_records_empty_subtitle),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
        if (onOpenTestFlow != null) {
            TextButton(
                text = stringResource(R.string.otp_records_open_test_flow),
                onClick = onOpenTestFlow,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OtpRecordRow(
    record: OtpRecord,
    appInfo: AppInfo?,
    timeLabel: String,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val categoryLabel = when (record.category) {
        OtpRecordCategory.PLAIN_SMS -> stringResource(R.string.otp_records_category_plain_sms)
        OtpRecordCategory.APP_NOTIFY -> stringResource(R.string.otp_records_category_app_notify)
        OtpRecordCategory.TEST -> stringResource(R.string.otp_records_test_source)
        OtpRecordCategory.CODE -> stringResource(R.string.otp_records_category_code)
    }
    val slotLabel = if (record.simSlot >= 0) {
        stringResource(R.string.otp_sim_slot_label, record.simSlot + 1)
    } else {
        null
    }
    val sourceLabel = when {
        record.category == OtpRecordCategory.TEST -> categoryLabel
        appInfo != null -> "$categoryLabel · ${appInfo.label}"
        else -> "$categoryLabel · ${record.packageName}"
    }.let { label -> if (slotLabel == null) label else "$slotLabel · $label" }
    val snippet = record.text.ifBlank { record.title }.ifBlank { record.packageName }
    val context = LocalContext.current
    val fillLabel = OtpAutoFillUiLabels.formatRecordFillStatus(context, record.autoFillStatus)
    val fillDetail = OtpAutoFillUiLabels.formatRecordFillDetail(
        context,
        record.autoFillStatus,
        record.autoFillReason,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onCopy,
                onLongClick = onDelete,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.code.ifBlank {
                        stringResource(R.string.otp_records_plain_sms_no_code)
                    },
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                OtpRecordFillBadge(
                    label = fillLabel,
                    status = record.autoFillStatus,
                )
            }
            fillDetail?.let { detail ->
                Text(
                    text = detail,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = sourceLabel,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeLabel,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            if (snippet.isNotBlank()) {
                Text(
                    text = snippet,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            record.ruleName?.let { ruleName ->
                Text(
                    text = stringResource(R.string.otp_records_rule_label, ruleName),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
    }
}

@Composable
private fun OtpRecordFillBadge(
    label: String,
    status: OtpRecordFillStatus,
) {
    val containerColor = when (status) {
        OtpRecordFillStatus.LSPOSED -> MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
        OtpRecordFillStatus.ACCESSIBILITY -> MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
        OtpRecordFillStatus.FAILED -> MiuixTheme.colorScheme.error.copy(alpha = 0.12f)
        OtpRecordFillStatus.PENDING -> MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.12f)
        OtpRecordFillStatus.NONE -> MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.08f)
    }
    val contentColor = when (status) {
        OtpRecordFillStatus.LSPOSED -> MiuixTheme.colorScheme.primary
        OtpRecordFillStatus.ACCESSIBILITY -> MiuixTheme.colorScheme.primary
        OtpRecordFillStatus.FAILED -> MiuixTheme.colorScheme.error
        OtpRecordFillStatus.PENDING -> MiuixTheme.colorScheme.onSurface
        OtpRecordFillStatus.NONE -> MiuixTheme.colorScheme.onSurfaceSecondary
    }
    Box(
        modifier = Modifier
            .squircleSurface(
                color = containerColor,
                cornerRadius = 6.dp,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
            maxLines = 1,
        )
    }
}
