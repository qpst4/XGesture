package com.slideindex.app.overlay

import android.content.Context
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallTranslateEngine
import com.slideindex.app.translate.TranslateDependencyAccess
import com.slideindex.app.translate.TranslateEngine
import com.slideindex.app.translate.TranslateResult
import com.slideindex.app.translate.TranslateTargetLanguages
import com.slideindex.app.translate.TranslateTargetResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FloatBallTranslateCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun translate(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val settings = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.readSnapshot()
            ?: return

        // 网页回落那条路也要用同一个目标语言，所以提前解析（原来只服务即时浮窗）。
        val targetLang = resolveTargetLang(settings)

        when (resolveTranslateLanding(settings.floatBallTranslateEngine, settings.floatBallInstantTranslate)) {
            TranslateLanding.EXTERNAL_APP -> {
                FloatBallTextPick.translateToApp(
                    context = context,
                    text = trimmed,
                    targetPackage = settings.floatBallTranslateAppPackage,
                    targetLang = targetLang,
                )
                return
            }

            TranslateLanding.JUMP_TO_WEB -> {
                FloatBallTextPick.translateText(context, trimmed, targetLang)
                return
            }

            TranslateLanding.IN_PANEL -> Unit
        }

        if (!FloatBallPickResultPanel.isShowing) return

        if (FloatBallPickResultPanel.isShowingTranslation()) {
            FloatBallPickResultPanel.restoreFromTranslation()
            return
        }

        val engine = engineFor(settings) ?: return
        translateInPanel(context, trimmed, targetLang, engine)
    }

    /**
     * 给「取词面板以外」的入口用（全局复制、拖拽流转栏…）。
     *
     * 与 [translate] 只差一件事：这些入口没有现成的取词面板，结果面板由调用方通过
     * [openTextPanel] 准备。落地顺序与取词面板完全一致，且 [openTextPanel] 只在
     * 「确实要在面板里翻」时才会被调用 —— 不会出现「面板开着、人已经跳到别的 App」的空面板。
     */
    fun translateFromExternalEntry(
        context: Context,
        text: String,
        openTextPanel: (String) -> Unit,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val settings = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.readSnapshot()
        if (settings == null) {
            // 设置读不到也不能让点击没反应：退回「跳转翻译」的老路，目标语言按 App/系统语言推断。
            FloatBallTextPick.translateText(
                context = context,
                text = trimmed,
                targetLang = TranslateTargetResolver.resolve(TranslateTargetLanguages.FOLLOW_APP, null),
            )
            return
        }

        val targetLang = resolveTargetLang(settings)
        when (resolveTranslateLanding(settings.floatBallTranslateEngine, settings.floatBallInstantTranslate)) {
            TranslateLanding.EXTERNAL_APP -> {
                FloatBallTextPick.translateToApp(
                    context = context,
                    text = trimmed,
                    targetPackage = settings.floatBallTranslateAppPackage,
                    targetLang = targetLang,
                )
                return
            }

            TranslateLanding.JUMP_TO_WEB -> {
                FloatBallTextPick.translateText(context, trimmed, targetLang)
                return
            }

            TranslateLanding.IN_PANEL -> Unit
        }

        val engine = engineFor(settings) ?: return
        openTextPanel(trimmed)
        translateInPanel(context, trimmed, targetLang, engine)
    }

    private fun resolveTargetLang(settings: AppSettings): String =
        TranslateTargetResolver.resolve(
            settings.floatBallTranslateTargetLang,
            settings.appUiLanguageTag,
        )

    private fun engineFor(settings: AppSettings): TranslateEngine? = when (settings.floatBallTranslateEngine) {
        FloatBallTranslateEngine.GOOGLE -> TranslateEngine.GOOGLE
        FloatBallTranslateEngine.ML_KIT -> TranslateEngine.ML_KIT
        FloatBallTranslateEngine.CLOUD_LLM -> TranslateEngine.CLOUD_LLM
        // 上面已按落地方式提前分流，这里只是让 when 穷尽。
        FloatBallTranslateEngine.LOCAL_APP -> null
    }

    private fun translateInPanel(
        context: Context,
        text: String,
        targetLang: String,
        engine: TranslateEngine,
    ) {
        FloatBallPickResultPanel.showTranslateLoading()
        scope.launch {
            val service = TranslateDependencyAccess.translateService(context)
            if (service == null) {
                FloatBallPickResultPanel.showTranslateError(context, "translate_unavailable")
                return@launch
            }
            when (val result = service.translate(text, targetLang, engine)) {
                is TranslateResult.Success -> {
                    FloatBallPickResultPanel.showTranslateResult(result.translatedText)
                }
                is TranslateResult.Failure -> {
                    FloatBallPickResultPanel.showTranslateError(
                        context,
                        mapErrorMessage(result.message)
                    )
                }
            }
        }
    }

    private fun mapErrorMessage(code: String): String = when (code) {
        "target_model_not_installed", "source_model_not_installed", "model_download_required" ->
            "mlkit_model_not_installed"
        "translate_engine_not_installed" -> "translate_engine_not_installed"
        "wifi_required" -> "wifi_required"
        "unsupported_target_language" -> "unsupported_language"
        "api_key_not_configured" -> "cloud_api_key_not_configured"
        "cloud_empty" -> "cloud_translate_empty"
        else -> code
    }
}
