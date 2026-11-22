package com.slideindex.app.xposed

import android.os.Bundle
import android.os.Binder
import android.content.pm.ApplicationInfo
import android.os.Looper
import com.android.server.clipboard.ClipboardService
import com.slideindex.app.xposed.bridge.ModuleHookSnapshot
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.hook.ClipboardWhitelistHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 复现覆盖安装：框架切换到新模块实例后，后台剪贴板权限仍应生效。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ClipboardHotReloadAcceptanceTest {
    private val targetLoader = ClipboardService::class.java.classLoader!!

    @Test
    fun update_keepsBackgroundClipboardAccess_withANewModuleInstance() {
        val framework = Framework()
        var module = framework.newModule()
        module.onSystemServerStarting { targetLoader }
        applyWhitelist()
        assertTrue(framework.canReadClipboard("com.slideindex.app"))
        assertFalse(framework.canReadClipboard("unlisted.app"))

        repeat(2) {
            val outgoing = SavingParam()
            assertTrue(module.onHotReloading(outgoing))
            val oldHandles = framework.activeHandles()
            module = framework.newModule()
            module.onHotReloaded(ReloadedParam(outgoing.saved, oldHandles))
            applyWhitelist()

            assertTrue("更新后白名单应用仍应能在后台读取", framework.canReadClipboard("com.slideindex.app"))
            assertFalse("不应向其他应用放开权限", framework.canReadClipboard("unlisted.app"))
            assertEquals("每轮更新仅保留一个剪贴板 Hook", 1, framework.clipboardHookCount())
            assertEquals("旧代状态接收器应随热重载注销", 1,
                shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.count {
                    it.intentFilter.hasAction(ModuleHookBridgeContract.ACTION_MODULE_STATUS_REQUEST)
                })
        }
    }

    @Test
    fun updateFromLegacyModule_withoutSavedState_recoversFromExistingHookTargets() {
        val framework = Framework()
        framework.newModule().onSystemServerStarting { targetLoader }
        val oldHandles = framework.activeHandles()

        framework.newModule().onHotReloaded(ReloadedParam(null, oldHandles))
        applyWhitelist()

        assertTrue(framework.canReadClipboard("com.slideindex.app"))
        assertEquals(1, framework.clipboardHookCount())
    }

    @Test
    fun updateFromAlreadyBrokenGeneration_recoversWithoutOldHooksOrSavedState() {
        val mainThread = Looper.getMainLooper().thread
        val previousLoader = mainThread.contextClassLoader
        try {
            mainThread.contextClassLoader = targetLoader
            val framework = Framework()
            framework.newModule().onHotReloaded(ReloadedParam(null, emptyList()))
            applyWhitelist()

            assertTrue("已失效的旧热重载实例也应可恢复", framework.canReadClipboard("com.slideindex.app"))
        } finally {
            mainThread.contextClassLoader = previousLoader
        }
    }

    @Test
    fun brokenLegacyGeneration_canRecoverFromRunningService_whenThreadLoadersCannotLoadSystemCode() {
        val mainThread = Looper.getMainLooper().thread
        val currentThread = Thread.currentThread()
        val previousMainLoader = mainThread.contextClassLoader
        val previousCurrentLoader = currentThread.contextClassLoader
        try {
            mainThread.contextClassLoader = object : ClassLoader(null) {}
            currentThread.contextClassLoader = object : ClassLoader(null) {}
            LibXposedReflect.callStaticMethod(Class.forName("android.os.ServiceManager"),
                "addService", "clipboard", object : Binder() {})
            val framework = Framework()

            framework.newModule().onHotReloaded(ReloadedParam(null, emptyList()))
            applyWhitelist()

            assertTrue("无法依靠线程加载器时，从现有系统服务恢复", framework.canReadClipboard("com.slideindex.app"))
        } finally {
            mainThread.contextClassLoader = previousMainLoader
            currentThread.contextClassLoader = previousCurrentLoader
        }
    }

    private fun applyWhitelist() {
        ClipboardWhitelistHook.onConfigBroadcast(ModuleHookSnapshot(
            takeoverGroups = 0,
            clipboardWhitelist = listOf("com.slideindex.app"),
        ).toJson())
    }

    private class SavingParam : XposedModuleInterface.HotReloadingParam {
        var saved: Any? = null
        override fun getExtras(): Bundle = Bundle.EMPTY
        override fun setSavedInstanceState(state: Any?) {
            require(state == null || state.javaClass.classLoader != SlideIndexLibXposedModule::class.java.classLoader)
            saved = state
        }
    }

    private class ReloadedParam(
        private val saved: Any?,
        private val handles: List<XposedInterface.HookHandle>,
    ) : XposedModuleInterface.HotReloadedParam {
        override fun getExtras(): Bundle = Bundle.EMPTY
        override fun getSavedInstanceState(): Any? = saved
        override fun getOldHookHandles(): List<XposedInterface.HookHandle> = handles
        override fun isSystemServer(): Boolean = true
        override fun getProcessName(): String = "system"
    }

    /** 只替代框架的 Hook 调度；模块入口、安装和权限判断均使用生产代码。 */
    private class Framework {
        private val handles = mutableListOf<Handle>()
        private val api = proxy<XposedInterface> { name, args ->
            when (name) {
                "hook" -> builder(args!![0] as Executable)
                "log" -> null
                "getApiVersion" -> 102
                "getFrameworkName", "getFrameworkVersion" -> "test"
                "getFrameworkVersionCode", "getFrameworkProperties" -> 0L
                "getModuleApplicationInfo" -> ApplicationInfo().apply { sourceDir = "/current/base.apk" }
                else -> error("Unexpected framework call: $name")
            }
        }

        fun newModule() = SlideIndexLibXposedModule().apply { attachFramework(api) {} }

        fun activeHandles(): List<XposedInterface.HookHandle> = handles.filter { it.active }

        fun clipboardHookCount(): Int = handles.count {
            it.active && it.target.declaringClass == ClipboardService::class.java
        }

        fun canReadClipboard(packageName: String): Boolean {
            val handle = handles.lastOrNull {
                it.active && it.target.declaringClass == ClipboardService::class.java
            } ?: return false
            val chain = proxy<XposedInterface.Chain> { name, args ->
                when (name) {
                    "getArg" -> listOf(10525, packageName)[args!![0] as Int]
                    "getArgs" -> listOf(10525, packageName)
                    "getThisObject" -> null
                    "getExecutable" -> handle.target
                    "proceed" -> false // 系统原始判定：普通后台应用不具备输入法权限。
                    else -> error("Unexpected chain call: $name")
                }
            }
            return handle.hooker.intercept(chain) as Boolean
        }

        private fun builder(target: Executable): XposedInterface.HookBuilder {
            var id: String? = null
            lateinit var builder: XposedInterface.HookBuilder
            builder = proxy { name, args ->
                when (name) {
                    "setId" -> { id = args!![0] as String; builder }
                    "setPriority", "setExceptionMode" -> builder
                    "intercept" -> Handle(target, id, args!![0] as XposedInterface.Hooker)
                        .also { handles += it }
                    else -> error("Unexpected builder call: $name")
                }
            }
            return builder
        }
    }

    private class Handle(
        val target: Executable,
        private val hookId: String?,
        var hooker: XposedInterface.Hooker,
    ) : XposedInterface.HookHandle {
        var active = true
        override fun getExecutable(): Executable = target
        override fun getId(): String? = hookId
        override fun unhook() { active = false }
        override fun replaceHook(replacement: XposedInterface.Hooker): XposedInterface.HookHandle {
            hooker = replacement
            return this
        }
    }

    companion object {
        private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                call(method.name, args)
            } as T
    }
}
