package com.slideindex.app.xposed

import android.util.Log
import com.slideindex.app.xposed.hook.ClipboardWhitelistHook
import com.slideindex.app.xposed.hook.PermissionGranterHook
import com.slideindex.app.xposed.hook.SmsHandlerHook
import com.slideindex.app.xposed.hook.SmsProviderHook
import com.slideindex.app.xposed.hook.SystemInputFilterHook
import com.slideindex.app.xposed.hook.SystemInputInjectorHook
import com.slideindex.app.xposed.hook.otp.SmsPolicyRuntime
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class SlideIndexLibXposedModule : XposedModule() {
  private val reloadTargets = ModuleReloadTargets()
  private val smsHandlerHook = SmsHandlerHook()
  private val smsProviderHook = SmsProviderHook()
  private val systemInputInjectorHook = SystemInputInjectorHook()
  private val systemInputFilterHook = SystemInputFilterHook { moduleApplicationInfo.sourceDir }
  private val permissionGranterHook = PermissionGranterHook()

  override fun onModuleLoaded(param: ModuleLoadedParam) {
    XposedLog.bind(this)
    log(
      Log.INFO,
      TAG,
      "onModuleLoaded process=${param.processName} systemServer=${param.isSystemServer} " +
        "framework=$frameworkName($frameworkVersionCode) api=$apiVersion",
    )
  }

  override fun onSystemServerStarting(param: SystemServerStartingParam) {
    reloadTargets.remember(ModuleReloadTargets.SYSTEM, param.classLoader)
    log(Log.INFO, TAG, "onSystemServerStarting")
    installSystemServerHooks(param.classLoader)
  }

  override fun onPackageLoaded(param: PackageLoadedParam) {
    if (param.packageName == PHONE_PACKAGE) {
      log(Log.INFO, TAG, "onPackageLoaded phone first=${param.isFirstPackage}")
    }
  }

  override fun onPackageReady(param: PackageReadyParam) {
    when (param.packageName) {
      PHONE_PACKAGE -> {
        reloadTargets.remember(PHONE_PACKAGE, param.classLoader)
        log(Log.INFO, TAG, "onPackageReady phone")
        installPhoneHooks(param.classLoader)
      }
      TELEPHONY_PROVIDER_PACKAGE -> {
        reloadTargets.remember(TELEPHONY_PROVIDER_PACKAGE, param.classLoader)
        log(Log.INFO, TAG, "onPackageReady telephony provider")
        installTelephonyProviderHooks(param.classLoader)
      }
    }
  }

  override fun onHotReloading(param: HotReloadingParam): Boolean {
    log(Log.INFO, TAG, "onHotReloading")
    param.setSavedInstanceState(reloadTargets.save())
    systemInputFilterHook.detach()
    systemInputInjectorHook.detach()
    SmsPolicyRuntime.detach()
    return true
  }

  override fun onHotReloaded(param: HotReloadedParam) {
    // 这是新模块实例；onModuleLoaded / onSystemServerStarting / onPackageReady 均不会重放。
    XposedLog.bind(this)
    reloadTargets.restore(param.savedInstanceState)
    val systemLoader = if (param.isSystemServer) reloadTargets.systemLoader(param.oldHookHandles) else null
    super.onHotReloaded(param)
    log(Log.INFO, TAG, "onHotReloaded process=${param.processName}")
    if (param.isSystemServer) {
      if (systemLoader == null) {
        log(Log.ERROR, TAG, "hot reload: system class loader unavailable")
      } else {
        reloadTargets.remember(ModuleReloadTargets.SYSTEM, systemLoader)
        installSystemServerHooks(systemLoader)
      }
    } else {
      reloadTargets.packageLoaders(param.processName).forEach { (target, loader) ->
        reloadTargets.remember(target, loader)
        when (target) {
          PHONE_PACKAGE -> installPhoneHooks(loader)
          TELEPHONY_PROVIDER_PACKAGE -> installTelephonyProviderHooks(loader)
        }
      }
    }
  }

  private fun installSystemServerHooks(classLoader: ClassLoader) {
    // 状态通道最先建立：后续任何一步失败都能通过状态响应回传到 app，避免静默失败。
    runCatching { systemInputFilterHook.registerStatusChannel(classLoader) }
      .onFailure { log(Log.ERROR, TAG, "status channel failed: $it") }
    runCatching { systemInputInjectorHook.install(this, classLoader) }
      .onFailure { reportStepFailure("inputInjector", it) }
    runCatching { systemInputFilterHook.install(this, classLoader) }
      .onFailure { reportStepFailure("inputFilter", it) }
    runCatching { permissionGranterHook.install(this, classLoader) }
      .onFailure { reportStepFailure("permissionGranter", it) }
    runCatching { ClipboardWhitelistHook.install(this, classLoader) }
      .onFailure { reportStepFailure("clipboardWhitelist", it) }
  }

  private fun reportStepFailure(step: String, throwable: Throwable) {
    val message = "$step: ${throwable.javaClass.simpleName}: ${throwable.message}"
    systemInputFilterHook.recordModuleError(message)
    log(Log.ERROR, TAG, "system hook failed -> $message")
  }

  private fun installPhoneHooks(classLoader: ClassLoader) {
    SmsPolicyRuntime.register(ModuleHookBridgeContract.CHANNEL_PHONE, moduleApplicationInfo.sourceDir)
    smsHandlerHook.install(this, classLoader)
  }

  private fun installTelephonyProviderHooks(classLoader: ClassLoader) {
    SmsPolicyRuntime.register(ModuleHookBridgeContract.CHANNEL_TELEPHONY, moduleApplicationInfo.sourceDir)
    smsProviderHook.install(this, classLoader)
  }

  companion object {
    private const val TAG = "SlideIndexXposed"
    private const val PHONE_PACKAGE = "com.android.phone"
    private const val TELEPHONY_PROVIDER_PACKAGE = "com.android.providers.telephony"
    // 剪贴板白名单是单例：安装点和配置广播入口必须共用同一个 reader（见 ClipboardWhitelistHook）。
  }
}
