package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.WidgetPickerScreen
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import com.slideindex.app.widget.WidgetProviderEntry

/**
 * Fallback picker when [com.slideindex.app.overlay.WidgetPickerOverlayWindow] cannot attach
 * (e.g. settings screen while accessibility host is unavailable).
 */
@dagger.hilt.android.AndroidEntryPoint
class WidgetPickerTrampolineActivity : ComponentActivity() {

  @javax.inject.Inject lateinit var deps: AppDependencies

  /** 是否已经把结果（成功/取消/去绑定）交给 [WidgetPickerTrampoline]，避免 onDestroy 再补一次取消。 */
  private var delivered = false

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
    )
    super.onCreate(savedInstanceState)

    var appSettings by mutableStateOf(deps.settingsRepository.readSnapshot())

    setContent {
      // WidgetPickerScreen 内部通过 rememberSettingsRepository/rememberAppRepository 取依赖，
      // 缺这层 provider 会在组合期直接抛 IllegalStateException（选器 Activity 一进就闪退）。
      CompositionLocalProvider(LocalAppDependencies provides deps) {
        ModuleTheme(settings = appSettings) {
          WidgetPickerScreen(
            onBack = {
              delivered = true
              WidgetPickerTrampoline.deliverCancel()
              finish()
            },
            onWidgetSelected = { entry -> onWidgetPicked(entry) },
            onAppSelected = { app ->
              delivered = true
              WidgetPickerTrampoline.deliverAppSuccess(app.packageName, app.className, app.appLabel, app.cloneUserId)
              finish()
            },
            onShortcutSelected = { sc ->
              delivered = true
              WidgetPickerTrampoline.deliverShortcutSuccess(sc.packageName, sc.shortcutId, sc.label, sc.intentUri)
              finish()
            },
            enableBackHandler = true
          )
        }
      }
    }
  }

  private fun onWidgetPicked(entry: WidgetProviderEntry) {
    // 小组件要先去绑定页（WidgetBindTrampolineActivity），成功/取消都在那边回传；
    // 这里只标记“已交接”，别让 onDestroy 把整条添加流程取消掉。
    delivered = true
    WidgetPickerTrampoline.startBindFlow(this, entry.provider.provider)
    finish()
  }

  override fun onDestroy() {
    // 没有正常回传就被销毁（返回键被系统吞掉、任务被划掉等）：补一次取消，
    // 否则面板会一直停在“添加流程中”的隐藏态。
    if (isFinishing && !delivered) {
      delivered = true
      WidgetPickerTrampoline.deliverCancel()
    }
    super.onDestroy()
  }

  companion object {
    fun createIntent(context: android.content.Context) =
      android.content.Intent(context, WidgetPickerTrampolineActivity::class.java)
  }
}
