package com.slideindex.app.cloudstorage

/**
 * 远端路径归一化。
 *
 * 移植自 ClipShare `StorageClient` 的 `normalizeStoragePath` / `buildObjectStorageKey`
 * 一组工具方法：统一 Windows/Unix 分隔符、折叠重复 `/`、去掉首尾 `/`，
 * 避免 baseDir 与业务路径拼出 `//`（对象存储里 `a//b` 与 `a/b` 是两个不同的 key）。
 */
object CloudStoragePath {
    /** 归一化为不带首尾 `/` 的相对路径；`\` 视为 `/`；丢弃空段与 `.`。 */
    fun normalize(path: String): String =
        path.replace('\\', '/')
            .split('/')
            .filter { it.isNotEmpty() && it != "." }
            .joinToString("/")

    /** 归一化 baseDir；非空时补齐尾部 `/`，便于直接做前缀拼接。 */
    fun normalizeBaseDir(baseDir: String): String {
        val normalized = normalize(baseDir)
        return if (normalized.isEmpty()) "" else "$normalized/"
    }

    /** baseDir 与相对路径拼接，结果不带首尾 `/`。 */
    fun join(baseDir: String, path: String): String {
        val base = normalize(baseDir)
        val rest = normalize(path)
        return when {
            base.isEmpty() -> rest
            rest.isEmpty() -> base
            else -> "$base/$rest"
        }
    }

    /**
     * 取最后一段作为文件名。
     *
     * 注意不能用 `substringAfterLast('/', "")`：路径里没有 `/` 时它会返回缺省值空串，
     * 把 "a.zip" 变成 ""。
     */
    fun fileName(path: String): String = normalize(path).substringAfterLast('/')

    fun parent(path: String): String = normalize(path).substringBeforeLast('/', "")

    /** 路径是否落在 baseDir 内（含 baseDir 自身）。 */
    fun isUnderBaseDir(baseDir: String, path: String): Boolean {
        val base = normalize(baseDir)
        val target = normalize(path)
        if (base.isEmpty()) return true
        return target == base || target.startsWith("$base/")
    }

    /**
     * 拒绝把 `..` 带进远端路径：备份文件名由本机生成、目录由常量拼接，
     * 一旦出现 `..` 说明调用方传错了，宁可抛错也不要发出越界请求。
     */
    fun requireSafe(path: String): String {
        val normalized = normalize(path)
        require(normalized.split('/').none { it == ".." }) { "Illegal remote path: $path" }
        return normalized
    }
}
