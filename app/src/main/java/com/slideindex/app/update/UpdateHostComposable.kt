package com.slideindex.app.update

import android.Manifest
import android.os.Build
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R

@Composable
fun UpdateHost(
    viewModel: UpdateViewModel,
    entryIntentAction: String? = null,
    showUpdateFromIntent: Boolean = false,
    onShowUpdateConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            val activity = context as? android.app.Activity
            if (activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            ) {
                runCatching {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkOnLaunch()
        viewModel.onEntry()
        viewModel.evaluateNotificationPrompt()
    }

    LaunchedEffect(entryIntentAction) {
        viewModel.onEntry()
    }

    LaunchedEffect(showUpdateFromIntent) {
        if (showUpdateFromIntent) {
            viewModel.checkFromNotification()
            onShowUpdateConsumed()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val rationaleShowing = uiState.showNotificationRationale
    UpdateDialog(
        show = uiState.showDialog && !rationaleShowing,
        state = uiState,
        onDismissRequest = viewModel::dismiss,
        onIgnore = viewModel::onIgnoreVersion,
        onConfirm = viewModel::onConfirmUpdate,
        onInstall = viewModel::onInstall,
        onMoveToBackground = viewModel::onMoveToBackground,
        onOpenRelease = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, UpdateChecker.RELEASES_PAGE_URL.toUri()),
            )
        },
    )

    if (uiState.showCheckFailedDialog && !rationaleShowing) {
        val titleRes = when (uiState.checkFailedReason) {
            UpdateViewModel.CheckFailedReason.RateLimited -> R.string.update_check_rate_limited_title
            UpdateViewModel.CheckFailedReason.NoApk -> R.string.update_check_no_apk_title
            UpdateViewModel.CheckFailedReason.NetworkUnavailable -> R.string.update_check_network_failed_title
            UpdateViewModel.CheckFailedReason.Generic -> R.string.update_check_failed_title
        }
        val messageRes = when (uiState.checkFailedReason) {
            UpdateViewModel.CheckFailedReason.RateLimited -> R.string.update_check_rate_limited_message
            UpdateViewModel.CheckFailedReason.NoApk -> R.string.update_check_no_apk_message
            UpdateViewModel.CheckFailedReason.NetworkUnavailable -> R.string.update_check_network_failed_message
            UpdateViewModel.CheckFailedReason.Generic -> R.string.update_check_failed_message
        }
        MiuixConfirmDialog(
            show = true,
            onDismissRequest = viewModel::dismissCheckFailedDialog,
            title = stringResource(titleRes),
            message = stringResource(messageRes),
            confirmText = stringResource(R.string.update_view_on_github),
            onConfirm = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, UpdateChecker.RELEASES_PAGE_URL.toUri()),
                )
            },
        )
    }

    if (rationaleShowing) {
        MiuixConfirmDialog(
            show = true,
            onDismissRequest = viewModel::dismissNotificationRationale,
            title = stringResource(R.string.update_notification_permission_title),
            message = stringResource(R.string.update_notification_permission_rationale),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
}
