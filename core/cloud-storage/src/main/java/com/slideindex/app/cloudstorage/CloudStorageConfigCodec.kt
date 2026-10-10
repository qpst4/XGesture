package com.slideindex.app.cloudstorage

import kotlinx.serialization.json.Json

/**
 * 云存储配置的 JSON 编解码。
 *
 * 配置整体存成一个字符串偏好项，因此会随 `settings.json` 一起进本地备份包、
 * 也能被恢复回来（新机恢复后不需要重新填网盘口令）。
 *
 * 解码刻意不做异常传播：配置损坏时退化成 [CloudStorageSettings.EMPTY]，
 * 否则一个坏字符串就会让设置页直接崩溃。
 */
object CloudStorageConfigCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(settings: CloudStorageSettings): String = json.encodeToString(settings)

    fun decode(raw: String?): CloudStorageSettings {
        if (raw.isNullOrBlank()) return CloudStorageSettings.EMPTY
        return runCatching { json.decodeFromString<CloudStorageSettings>(raw) }
            .getOrDefault(CloudStorageSettings.EMPTY)
    }
}
