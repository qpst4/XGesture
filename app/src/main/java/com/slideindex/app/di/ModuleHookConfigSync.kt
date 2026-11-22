package com.slideindex.app.di

import android.content.Context
import com.slideindex.app.overlay.OverlayDisplayRotationMonitor
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.xposed.bridge.ModuleHookConfigWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 把接管开关与触钮几何同步给 system_server 模块（EdgeX 的快照文件 + 广播模式）。
 *
 * 仅在与接管相关的配置真正变化时才下发，避免无谓的跨进程广播。
 */
@Singleton
class ModuleHookConfigSync @Inject constructor(
  @ApplicationContext private val context: Context,
  private val settingsRepository: SettingsRepository,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val rotationMonitor = OverlayDisplayRotationMonitor(context) { publish() }

  private var started = false

  @OptIn(FlowPreview::class)
  fun start() {
    if (started) return
    started = true
    rotationMonitor.start()
    scope.launch {
      settingsRepository.settings
        .map { settings -> settings to takeoverSignature(settings) }
        .distinctUntilChangedBy { it.second }
        .debounce(SYNC_DEBOUNCE_MS)
        .collect { (settings, _) -> publish(settings) }
    }
  }

  fun publish() {
    scope.launch { publish(settingsRepository.readFreshSnapshot()) }
  }

  private fun publish(settings: AppSettings) {
    runCatching {
      ModuleHookConfigWriter.publish(context, settings)
    }
  }

  /** 与实际下发内容共用口径，避免漏掉横屏几何；时间戳不参与变化判定。 */
  private fun takeoverSignature(settings: AppSettings): String =
    ModuleHookConfigWriter.buildSnapshot(context, settings).copy(updatedAtMs = 0L).toJson()

  private companion object {
    const val SYNC_DEBOUNCE_MS = 300L
  }
}
