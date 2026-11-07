package com.slideindex.app.overlay



import android.content.BroadcastReceiver

import android.content.Context

import android.content.Intent

import android.content.IntentFilter

import android.graphics.PixelFormat

import android.os.Handler

import android.os.Looper

import android.util.Log

import android.view.Gravity
import android.view.View
import android.view.WindowManager

import android.widget.Toast

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.widthIn

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.MutableState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.draw.shadow

import androidx.compose.ui.platform.ComposeView

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

import com.slideindex.app.R

import com.slideindex.app.di.OverlayDependencyAccess

import com.slideindex.app.settings.AppSettings

import com.slideindex.app.overlay.pickresult.PickResultInteractiveTextSection

import com.slideindex.app.overlay.pickresult.PickResultPanelMaxWidth
import com.slideindex.app.overlay.pickresult.PickResultPanelCardCorner
import com.slideindex.app.overlay.pickresult.PickResultTextSectionHeaderReservedHeight
import com.slideindex.app.overlay.pickresult.pickResultInteractiveTextChromeReservedHeight
import com.slideindex.app.overlay.pickresult.pickResultPanelCard
import com.slideindex.app.overlay.pickresult.pickResultWindowHeightDp
import com.slideindex.app.overlay.pickresult.PickResultSectionHeader
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import androidx.compose.ui.platform.LocalDensity

import com.slideindex.app.overlay.pickresult.PickResultTextMode

import com.slideindex.app.ui.theme.OverlayAwareModuleTheme



private val PANEL_HORIZONTAL_PADDING = 12.dp

private val PANEL_MAX_HEIGHT_FRACTION = 0.55f

private val TRANSLATE_PANEL_VERTICAL_PADDING = 12.dp

private val TRANSLATE_TEXT_BODY_MIN_HEIGHT = 48.dp



enum class FloatBallTranslatePanelPhase {

    LOADING,

    SUCCESS,

    ERROR,

}



/**

 * Independent translation overlay above the pick-result panel; reuses pick-result text interaction UI.

 */

object FloatBallTranslatePanel {

    private const val TAG = "FloatBallTranslatePanel"

    private val mainHandler = Handler(Looper.getMainLooper())

    private val panelHost = OverlayFullScreenPanelHost(
        tag = TAG,
        onScreenOff = { dismiss() }
    )

    private var phaseState: MutableState<FloatBallTranslatePanelPhase>? = null

    private var translatedTextState: MutableState<String?>? = null

    private var errorMessageState: MutableState<String?>? = null

    private var textModeState: MutableState<PickResultTextMode>? = null

    internal val panelVisible = mutableStateOf(false)

    val isShowing: Boolean
        get() = panelVisible.value && panelHost.isAttached && panelHost.isViewVisible()

    private var fileChooserSuppressed = false

    fun suppressForFileChooser() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suppressForFileChooser() }
            return
        }
        if (!panelHost.isAttached || fileChooserSuppressed) return
        fileChooserSuppressed = true
        panelHost.composeView?.visibility = View.GONE
    }

    fun restoreAfterFileChooser() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAfterFileChooser() }
            return
        }
        if (!fileChooserSuppressed) return
        fileChooserSuppressed = false
        panelHost.composeView?.visibility = View.VISIBLE
    }



    fun showLoading(context: Context) {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { showLoading(context) }

            return

        }

        val hostContext = overlayHostContext() ?: context.applicationContext

        if (FloatBallImageSearchPanel.isShowing) {
            FloatBallImageSearchPanel.dismiss()
        }

        ensureWindow(hostContext)

        translatedTextState?.value = null

        errorMessageState?.value = null

        textModeState?.value = PickResultTextMode.WORD_TAP

        phaseState?.value = FloatBallTranslatePanelPhase.LOADING
        updateWindowFocusable(focusable = false)

    }



    fun showResult(context: Context, translatedText: String) {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { showResult(context, translatedText) }

            return

        }

        val hostContext = overlayHostContext() ?: context.applicationContext

        ensureWindow(hostContext)

        translatedTextState?.value = translatedText

        errorMessageState?.value = null

        textModeState?.value = PickResultTextMode.WORD_TAP

        phaseState?.value = FloatBallTranslatePanelPhase.SUCCESS
        updateWindowFocusableForMode(PickResultTextMode.WORD_TAP)

    }



    fun showError(context: Context, message: String) {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { showError(context, message) }

            return

        }

        val hostContext = overlayHostContext() ?: context.applicationContext

        ensureWindow(hostContext)

        translatedTextState?.value = null

        errorMessageState?.value = message

        phaseState?.value = FloatBallTranslatePanelPhase.ERROR
        updateWindowFocusable(focusable = false)

    }



    fun dismiss() {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { dismiss() }

            return

        }

        panelHost.destroy()
        phaseState = null
        translatedTextState = null
        errorMessageState = null
        textModeState = null
        panelVisible.value = false
    }



    private fun updateWindowFocusableForMode(mode: PickResultTextMode) {

        updateWindowFocusable(

            focusable = mode == PickResultTextMode.SELECT || mode == PickResultTextMode.EDIT

        )

    }



    private fun updateWindowFocusable(focusable: Boolean) {
        panelHost.setInputActive(focusable)
    }



    private fun ensureWindow(context: Context) {

        if (panelHost.isAttached) return



        val phaseHolder = mutableStateOf(FloatBallTranslatePanelPhase.LOADING)

        val translatedHolder = mutableStateOf<String?>(null)

        val errorHolder = mutableStateOf<String?>(null)

        val textModeHolder = mutableStateOf(PickResultTextMode.WORD_TAP)

        phaseState = phaseHolder

        translatedTextState = translatedHolder

        errorMessageState = errorHolder

        textModeState = textModeHolder



        val overlayContext = OverlayCompose.themedContext(context)

        val attached = panelHost.ensureWindow(context, focusable = false) {
            val phase by phaseHolder
            val translatedText by translatedHolder
            val errorMessage by errorHolder
            val textMode by textModeHolder
            val settingsHolder = remember { mutableStateOf(AppSettings()) }
            LaunchedEffect(overlayContext) {
                val flow = OverlayDependencyAccess.overlayDependencies(overlayContext)
                    ?.settingsRepository
                    ?.settings
                    ?: return@LaunchedEffect
                flow.collect { settingsHolder.value = it }
            }
            val settings by settingsHolder
            FloatBallTranslatePanelContent(
                phase = phase,
                translatedText = translatedText,
                errorMessage = errorMessage,
                textMode = textMode,
                textSizeSp = settings.floatBallPickTextSizeSp,
                onTextModeChange = { mode ->
                    textModeHolder.value = mode
                    updateWindowFocusableForMode(mode)
                },
                onDismiss = { dismiss() },
                onTextChange = { translatedHolder.value = it },
                onCopy = { text, _ ->
                    FloatBallTextPick.copyText(context, text)
                    Toast.makeText(context, R.string.float_ball_text_copied, Toast.LENGTH_SHORT).show()
                },
                onSearch = { FloatBallTextPick.searchText(context, it) },
                onShare = {
                    // 内置分享面板点中目标前先收起翻译浮窗，避免目标 App 被它盖住。
                    FloatBallTextPick.shareText(context, it) { dismiss() }
                },
                onRemoveSpaces = { value, removeAll ->
                    translatedHolder.value = if (removeAll) {
                        value.replace(Regex("\\s+"), "")
                    } else {
                        value.trim()
                    }
                }
            )
        }
        if (attached == null) {
            Log.e(TAG, "failed to add translate panel")
            return
        }
        panelVisible.value = true
    }

}



@Composable

private fun FloatBallTranslatePanelContent(

    phase: FloatBallTranslatePanelPhase,

    translatedText: String?,

    errorMessage: String?,

    textMode: PickResultTextMode,

    textSizeSp: Float,

    onTextModeChange: (PickResultTextMode) -> Unit,

    onDismiss: () -> Unit,

    onTextChange: (String) -> Unit,

    onCopy: (String, keepPanelOpen: Boolean) -> Unit,

    onSearch: (String) -> Unit,

    onShare: (String) -> Unit,

    onRemoveSpaces: (String, removeAll: Boolean) -> Unit

) {

    val maxPanelHeight = pickResultWindowHeightDp(PANEL_MAX_HEIGHT_FRACTION)
    val translateTextBodyMaxHeight = (
        maxPanelHeight -
            PickResultTextSectionHeaderReservedHeight -
            pickResultInteractiveTextChromeReservedHeight() -
            TRANSLATE_PANEL_VERTICAL_PADDING
        ).coerceAtLeast(TRANSLATE_TEXT_BODY_MIN_HEIGHT)
    val dismissInteraction = remember { MutableInteractionSource() }

    OverlayAwareModuleTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val cornerPx = with(density) { PickResultPanelCardCorner.toPx() }
            val blurRadiusPx = (57f * density.density).roundToInt()
            val isDark = LocalAppDarkTheme.current
            val frostedTint = if (isDark) 0x661C1C1E.toInt() else 0x66F5F5F7.toInt()

            Box(
                modifier = Modifier
                    .padding(horizontal = PANEL_HORIZONTAL_PADDING)
                    .widthIn(max = PickResultPanelMaxWidth)
                    .heightIn(max = maxPanelHeight)
                    .pickResultPanelCard()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                LocalFrostedGlassBackdrop(
                    modifier = Modifier.matchParentSize(),
                    cornerRadiusPx = cornerPx,
                    blurRadiusPx = blurRadiusPx,
                    tintColor = frostedTint,
                    enabled = true
                )
                Column(
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    PickResultSectionHeader(
                        title = stringResource(R.string.float_ball_translate_panel_title),

                    expanded = true,

                    onToggle = {},

                    collapsible = false

                )

                when (phase) {

                    FloatBallTranslatePanelPhase.LOADING -> {

                        Row(

                            modifier = Modifier

                                .fillMaxWidth()

                                .padding(horizontal = 20.dp, vertical = 8.dp),

                            verticalAlignment = Alignment.CenterVertically,

                            horizontalArrangement = Arrangement.spacedBy(10.dp)

                        ) {

                            CircularProgressIndicator(

                                modifier = Modifier.size(20.dp),

                                strokeWidth = 2.dp

                            )

                            Text(

                                text = stringResource(R.string.float_ball_translating),

                                style = MaterialTheme.typography.bodyMedium

                            )

                        }

                    }

                    FloatBallTranslatePanelPhase.SUCCESS -> {

                        val result = translatedText.orEmpty()

                        if (result.isNotBlank()) {

                            PickResultInteractiveTextSection(

                                modifier = Modifier
                                    .padding(horizontal = 12.dp),

                                text = result,

                                textMode = textMode,

                                textSizeSp = textSizeSp,

                                onTextModeChange = onTextModeChange,

                                onTextChange = onTextChange,

                                showSourceChips = false,

                                translateEnabled = false,

                                pinActionBarOutside = true,

                                bodyMaxHeight = translateTextBodyMaxHeight,

                                showSearch = true,

                                onSearch = onSearch,

                                onShare = onShare,

                                onCopy = onCopy,

                                onTranslate = {},

                                onRemoveSpaces = onRemoveSpaces

                            )

                        }

                    }

                    FloatBallTranslatePanelPhase.ERROR -> {

                        Column(

                            modifier = Modifier

                                .fillMaxWidth()

                                .padding(horizontal = 20.dp, vertical = 8.dp),

                            verticalArrangement = Arrangement.spacedBy(6.dp)

                        ) {

                            Text(

                                text = stringResource(R.string.float_ball_translate_failed),

                                style = MaterialTheme.typography.bodyMedium,

                                color = MaterialTheme.colorScheme.error

                            )

                            if (!errorMessage.isNullOrBlank()) {

                                Text(

                                    text = translateErrorLabel(errorMessage),

                                    style = MaterialTheme.typography.bodySmall,

                                    color = MaterialTheme.colorScheme.onSurfaceVariant

                                )

                            }

                        }

                    }

                }

            }
        }
    }
}
}

@Composable

private fun translateErrorLabel(code: String): String = when (code) {

    "mlkit_model_not_installed" -> stringResource(R.string.float_ball_translate_error_model_missing)
    "translate_engine_not_installed" -> stringResource(R.string.float_ball_translate_error_engine_missing)

    "wifi_required" -> stringResource(R.string.float_ball_translate_error_wifi_required)

    "unsupported_language" -> stringResource(R.string.float_ball_translate_error_unsupported_language)

    "translate_unavailable" -> stringResource(R.string.float_ball_translate_error_unavailable)

    "network_error", "http_403", "http_429", "http_500" -> stringResource(R.string.float_ball_translate_error_network)

    else -> code

}


