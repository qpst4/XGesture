package com.slideindex.app.xposed

import android.app.Application
import android.os.Looper
import android.os.IBinder
import io.github.libxposed.api.XposedInterface

/** 跨代码代只传递目标进程的 ClassLoader，不把旧模块对象留给新一代。 */
internal class ModuleReloadTargets {
    private val loaders = linkedMapOf<String, ClassLoader>()

    fun remember(target: String, loader: ClassLoader) { loaders[target] = loader }

    // Kotlin 的空 Map 属于模块 ClassLoader，会被框架拒绝；空状态也必须使用 Java 集合。
    fun save(): Map<String, ClassLoader> = java.util.LinkedHashMap(loaders)

    fun restore(saved: Any?) {
        (saved as? Map<*, *>)?.forEach { (target, loader) ->
            if (target is String && target in TARGETS && loader is ClassLoader) {
                loaders[target] = loader
            }
        }
    }

    fun systemLoader(oldHooks: List<XposedInterface.HookHandle>): ClassLoader? =
        loaders[SYSTEM] ?: oldHooks.firstNotNullOfOrNull {
            it.executable.declaringClass.takeIf { type -> type.name.startsWith("com.android.server.") }
                ?.classLoader
        } ?: (listOfNotNull(
            Looper.getMainLooper().thread.contextClassLoader,
            Thread.currentThread().contextClassLoader,
        ) + runningServiceLoaders()).firstOrNull { loader ->
            LibXposedReflect.findClassIfExists("com.android.server.clipboard.ClipboardService", loader) != null
        }

    private fun runningServiceLoaders(): List<ClassLoader> {
        val manager = Class.forName("android.os.ServiceManager")
        return listOf("clipboard", "input").flatMap { name ->
            runCatching {
                val binder = LibXposedReflect.callStaticMethod(manager, "getService", name) as? IBinder
                listOfNotNull(binder?.javaClass?.classLoader,
                    binder?.interfaceDescriptor?.let { binder.queryLocalInterface(it)?.javaClass?.classLoader })
            }.getOrDefault(emptyList())
        }
    }

    fun packageLoaders(processName: String): Map<String, ClassLoader> {
        val restored = loaders.filterKeys { it != SYSTEM }
        if (restored.isNotEmpty() || processName !in TARGETS) return restored
        // 兼容旧模块没有保存状态的第一次升级；应用进程的原生命周期回调不会被框架重放。
        val app = runCatching {
            Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication")
                .invoke(null) as? Application
        }.getOrNull()
        return app?.classLoader?.let { mapOf(processName to it) }.orEmpty()
    }

    companion object {
        const val SYSTEM = "system"
        const val PHONE = "com.android.phone"
        const val TELEPHONY = "com.android.providers.telephony"
        private val TARGETS = setOf(SYSTEM, PHONE, TELEPHONY)
    }
}
