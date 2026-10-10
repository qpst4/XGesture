package com.slideindex.app.cloudstorage

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 云端备份文件的命名与识别。
 *
 * 与 ClipShare 的关键差异：它的文件名是 `backup-{设备名}-{yyyyMMdd}.zip`，
 * **同一天备份两次会写到同一个对象名上互相覆盖**，远端也没有可排序的版本信息。
 * 这里改成到秒的时间戳（`cebian-backup-20260213-153012.zip`），
 * 既天然不覆盖，又能让远端列表按名字排序就等于按时间排序——保留策略依赖这一点。
 */
object CloudBackupNaming {
    /** 备份统一放在 `<baseDir>/backup/` 下，与 ClipShare 的目录约定一致。 */
    const val BACKUP_DIR = "backup"

    private const val PREFIX = "cebian-backup-"
    private const val SUFFIX = ".zip"

    private val NAME_PATTERN = Regex("""^cebian-backup-(\d{8})-(\d{6})(?:-(.+))?\.zip$""", RegexOption.IGNORE_CASE)
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    private val NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /**
     * @param appVersionName 会作为后缀进文件名（可选），方便用户分辨"这份备份是哪个版本做的"。
     */
    fun buildFileName(timestamp: Instant, appVersionName: String?): String {
        val local = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())
        val version = appVersionName?.let(::sanitizeVersion)?.takeIf { it.isNotEmpty() }
        return buildString {
            append(PREFIX)
            append(NAME_FORMAT.format(local))
            if (version != null) append('-').append(version)
            append(SUFFIX)
        }
    }

    /** 从文件名解析备份时刻；不匹配命名规则时返回 null（调用方退化成用远端修改时间）。 */
    fun parseCreatedAt(fileName: String): Long? {
        val match = NAME_PATTERN.matchEntire(fileName) ?: return null
        val stamp = match.groupValues[1] + match.groupValues[2]
        return runCatching {
            LocalDateTime.parse(stamp, TIMESTAMP_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    fun parseAppVersion(fileName: String): String? =
        NAME_PATTERN.matchEntire(fileName)?.groupValues?.getOrNull(3)?.takeIf { it.isNotBlank() }

    fun isBackupFile(fileName: String): Boolean = fileName.endsWith(SUFFIX, ignoreCase = true)

    /** 版本号进文件名前先洗一遍：去掉路径分隔符与不适合做文件名的字符。 */
    private fun sanitizeVersion(version: String): String =
        version.trim().replace(Regex("""[^A-Za-z0-9._-]"""), "_").take(24)
}
