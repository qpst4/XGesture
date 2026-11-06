package com.slideindex.app.shizuku

import android.content.ComponentName
import android.content.Intent
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal reflection helpers for system TaskInfo / ActivityManager stubs.
 *
 * 任务切换器每次打开都会对每条任务做十几次「按名字找方法/字段」的反射；不做缓存时，
 * 每次 `getMethod` 都要遍历方法表，找不到时还要构造 `NoSuchMethodException`
 * （调用方用 `runCatching` 吞掉，但异常构造的成本照付）。这里按「类 + 名字 + 参数类型」
 * 缓存命中和未命中两种结果，把重复的开销压掉。
 */
internal object SystemReflect {

    private val NO_METHOD = Any()
    private val NO_FIELD = Any()
    private val EMPTY_TYPES = emptyArray<Class<*>>()

    private val methodCache = ConcurrentHashMap<String, Any>()
    private val fieldCache = ConcurrentHashMap<String, Any>()

    fun invoke(target: Any, method: String): Any? =
        runCatching { findMethod(target.javaClass, method, EMPTY_TYPES)?.invoke(target) }.getOrNull()

    fun invoke(target: Any, method: String, vararg args: Any?): Any? =
        runCatching {
            val types = args.map { arg ->
                when (arg) {
                    is Int -> Int::class.javaPrimitiveType!!
                    is Boolean -> Boolean::class.javaPrimitiveType!!
                    is Long -> Long::class.javaPrimitiveType!!
                    null -> Any::class.java
                    else -> arg.javaClass
                }
            }.toTypedArray()
            findMethod(target.javaClass, method, types)?.invoke(target, *args)
        }.getOrNull()

    fun readField(target: Any, name: String): Any? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            val field = findField(clazz, name)
            if (field != null) {
                val value = runCatching { field.get(target) }.getOrNull()
                if (value != null) return value
            }
            clazz = clazz.superclass
        }
        return null
    }

    fun readInt(target: Any, vararg names: String): Int? {
        for (name in names) {
            when {
                name.startsWith("get") -> {
                    when (val value = invoke(target, name)) {
                        is Int -> return value
                        is Number -> return value.toInt()
                    }
                }
                else -> {
                    when (val value = readField(target, name)) {
                        is Int -> return value
                        is Number -> return value.toInt()
                        else -> value?.toString()?.toIntOrNull()?.let { return it }
                    }
                }
            }
        }
        return null
    }

    fun readString(target: Any, vararg names: String): String? {
        for (name in names) {
            val value = if (name.startsWith("get")) invoke(target, name) else readField(target, name)
            value?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    fun readCharSequence(target: Any, vararg names: String): CharSequence? {
        for (name in names) {
            val value = if (name.startsWith("get")) invoke(target, name) else readField(target, name)
            (value as? CharSequence)?.let { return it }
        }
        return null
    }

    fun readComponent(target: Any, vararg names: String): ComponentName? {
        for (name in names) {
            val value = if (name.startsWith("get")) invoke(target, name) else readField(target, name)
            when (value) {
                is ComponentName -> return value
                is String -> parseComponent(value)?.let { return it }
                null -> continue
                else -> {
                    readString(value, "flattenToShortString")?.let { parseComponent(it) }?.let { return it }
                    val pkg = readString(value, "packageName")
                    val cls = readString(value, "className", "name")
                    if (!pkg.isNullOrBlank() && !cls.isNullOrBlank()) {
                        return ComponentName(pkg, cls)
                    }
                }
            }
        }
        return null
    }

    fun readComponentFromInfo(task: Any, vararg infoFields: String): ComponentName? {
        for (field in infoFields) {
            val info = readField(task, field) ?: continue
            val pkg = readString(info, "packageName") ?: continue
            val cls = readString(info, "name") ?: continue
            return ComponentName(pkg, cls)
        }
        return null
    }

    fun unwrapList(result: Any?): List<Any>? {
        when (result) {
            null -> return null
            is List<*> -> return result.filterNotNull()
            is Array<*> -> return result.filterNotNull()
        }
        return runCatching {
            invoke(result, "getList") as? List<*>
        }.getOrNull()?.filterNotNull()
    }

    private fun findMethod(clazz: Class<*>, name: String, types: Array<Class<*>>): Method? {
        val key = methodKey(clazz, name, types)
        val cached = methodCache[key]
        if (cached != null) return if (cached === NO_METHOD) null else cached as Method
        val found = runCatching { clazz.getMethod(name, *types) }.getOrNull()
        methodCache[key] = found ?: NO_METHOD
        return found
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        val key = clazz.name + '#' + name
        val cached = fieldCache[key]
        if (cached != null) return if (cached === NO_FIELD) null else cached as Field
        val found = runCatching {
            clazz.getDeclaredField(name).also { it.isAccessible = true }
        }.getOrNull()
        fieldCache[key] = found ?: NO_FIELD
        return found
    }

    private fun methodKey(clazz: Class<*>, name: String, types: Array<Class<*>>): String =
        buildString(48) {
            append(clazz.name)
            append('#')
            append(name)
            for (type in types) {
                append('|')
                append(type.name)
            }
        }

    private fun parseComponent(raw: String): ComponentName? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || !trimmed.contains('/')) return null
        val parts = trimmed.split('/', limit = 2)
        if (parts.size == 2 && parts[0].contains('.')) {
            return ComponentName(parts[0], parts[1].removePrefix("."))
        }
        return null
    }
}
