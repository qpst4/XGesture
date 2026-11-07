package com.slideindex.app.overlay.filebrowser

/** 面包屑的一段：path 是该级完整绝对路径，label 是显示用段名（特殊路径由 UI 换文案）。 */
data class PathCrumb(
    val path: String,
    val label: String,
)

/**
 * 文件浏览器的路径计算（纯逻辑）。
 *
 * 所有对外路径都经过 [normalize]：只保留绝对路径、去掉 `.`/空段、处理 `..`，
 * 并把 `/sdcard`、`/storage/self/primary` 归一到当前主用户的内部存储路径。
 * 这里**不做** canonicalize（不解析符号链接），避免 shell 路径与显示路径分叉。
 */
object FileBrowserPaths {
    const val DEFAULT_INTERNAL_ROOT = "/storage/emulated/0"

    fun normalize(path: String, internalRoot: String = DEFAULT_INTERNAL_ROOT): String {
        val root = internalRoot.trimEnd('/').ifEmpty { DEFAULT_INTERNAL_ROOT }
        val raw = path.trimEnd('/').ifEmpty { "/" }
        val absolute = if (raw.startsWith("/")) raw else "/$raw"
        val aliased = when {
            absolute == "/sdcard" -> root
            absolute.startsWith("/sdcard/") -> root + absolute.removePrefix("/sdcard")
            absolute == "/storage/self/primary" -> root
            absolute.startsWith("/storage/self/primary/") ->
                root + absolute.removePrefix("/storage/self/primary")
            else -> absolute
        }

        val stack = ArrayDeque<String>()
        for (segment in aliased.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(segment)
            }
        }
        return if (stack.isEmpty()) "/" else stack.joinToString(separator = "/", prefix = "/")
    }

    fun parent(path: String, internalRoot: String = DEFAULT_INTERNAL_ROOT): String? {
        val normalized = normalize(path, internalRoot)
        if (normalized == "/") return null
        // 内部存储是用户心智里的“一级”，返回键直接回根目录，不经过 /storage/emulated 这类系统层。
        if (normalized == normalize(internalRoot, internalRoot)) return "/"
        val parent = normalized.substringBeforeLast('/').ifEmpty { "/" }
        return normalize(parent, internalRoot)
    }

    fun child(
        parent: String,
        name: String,
        internalRoot: String = DEFAULT_INTERNAL_ROOT,
    ): String {
        val base = normalize(parent, internalRoot)
        val raw = if (base == "/") "/$name" else "$base/$name"
        return normalize(raw, internalRoot)
    }

    /**
     * 面包屑：根目录 → 内部存储（折叠 `/storage`、`/emulated` 中间层）→ 其余各级。
     *
     * 例：`/storage/emulated/0/Android/data/com.foo`
     * → `/`、`/storage/emulated/0`、`.../Android`、`.../data`、`.../com.foo`
     */
    fun crumbs(
        path: String,
        internalRoot: String = DEFAULT_INTERNAL_ROOT,
    ): List<PathCrumb> {
        val normalized = normalize(path, internalRoot)
        val internal = normalize(internalRoot, internalRoot).trimEnd('/').ifEmpty { "/" }
        val result = mutableListOf(PathCrumb(path = "/", label = "/"))

        if (internal != "/" && (normalized == internal || normalized.startsWith("$internal/"))) {
            result += PathCrumb(
                path = internal,
                label = internal.substringAfterLast('/').ifEmpty { internal },
            )
            var acc = internal
            normalized.removePrefix(internal).split('/')
                .filter { it.isNotEmpty() }
                .forEach { segment ->
                    acc = "$acc/$segment"
                    result += PathCrumb(path = acc, label = segment)
                }
            return result
        }

        if (normalized != "/") {
            var acc = ""
            normalized.split('/').filter { it.isNotEmpty() }.forEach { segment ->
                acc = "$acc/$segment"
                result += PathCrumb(path = acc, label = segment)
            }
        }
        return result
    }

    fun isExternal(path: String, internalRoot: String = DEFAULT_INTERNAL_ROOT): Boolean {
        val normalized = normalize(path, internalRoot)
        val internal = normalize(internalRoot, internalRoot)
        return normalized == internal ||
            normalized.startsWith("$internal/") ||
            normalized == "/sdcard" ||
            normalized.startsWith("/sdcard/") ||
            normalized.startsWith("/storage/") ||
            normalized.startsWith("/mnt/")
    }

    fun isAndroidDataOrObb(
        path: String,
        internalRoot: String = DEFAULT_INTERNAL_ROOT,
    ): Boolean {
        val normalized = normalize(path, internalRoot)
        return normalized.endsWith("/Android/data") ||
            normalized.contains("/Android/data/") ||
            normalized.endsWith("/Android/obb") ||
            normalized.contains("/Android/obb/")
    }

    /** shell(uid 2000) 读不到、需要 Root 的路径。 */
    fun isRootOnly(path: String): Boolean {
        val normalized = normalize(path)
        return normalized == "/data" ||
            normalized.startsWith("/data/") ||
            normalized == "/root" ||
            normalized.startsWith("/root/")
    }
}
