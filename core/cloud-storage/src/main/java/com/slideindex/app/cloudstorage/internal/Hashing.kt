package com.slideindex.app.cloudstorage.internal

import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 空请求体的 SHA256（AWS 文档常量），GET/HEAD/DELETE 都复用它。 */
internal const val EMPTY_PAYLOAD_SHA256 =
    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

private val HEX_CHARS = "0123456789abcdef".toCharArray()

private val UNRESERVED = buildSet {
    ('A'..'Z').forEach { add(it) }
    ('a'..'z').forEach { add(it) }
    ('0'..'9').forEach { add(it) }
    addAll(listOf('-', '_', '.', '~'))
}

internal fun ByteArray.toHex(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val value = this[i].toInt() and 0xFF
        out[i * 2] = HEX_CHARS[value ushr 4]
        out[i * 2 + 1] = HEX_CHARS[value and 0x0F]
    }
    return String(out)
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

/**
 * 流式计算 SHA256。
 *
 * 上传备份包时需要先算出 body 哈希再签 SigV4。这里刻意选择"多读一遍本地文件"
 * 而不是 `UNSIGNED-PAYLOAD`：后者虽然省一次读盘，但并非所有 S3 兼容服务都接受，
 * 而兼容性比这点磁盘开销重要得多（备份包通常几十 MB，多读一遍是毫秒级成本）。
 */
internal fun sha256Hex(stream: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    stream.use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHex()
}

internal fun hmacSha256(key: ByteArray, data: String): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data.toByteArray(Charsets.UTF_8))
}

internal fun hmacSha256Hex(key: ByteArray, data: String): String = hmacSha256(key, data).toHex()

/**
 * AWS SigV4 要求的 URI 编码（RFC 3986，空格编码为 `%20`，十六进制大写）。
 *
 * 不能直接用 `URLEncoder`：它按 `application/x-www-form-urlencoded` 把空格写成 `+`、
 * 且不编码 `*`，两者都会让签名与服务端算出的规范请求不一致。
 */
internal fun uriEncode(value: String, encodeSlash: Boolean): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val builder = StringBuilder(bytes.size)
    for (byte in bytes) {
        val char = (byte.toInt() and 0xFF).toChar()
        when {
            char in UNRESERVED -> builder.append(char)
            char == '/' && !encodeSlash -> builder.append('/')
            else -> {
                val value8 = byte.toInt() and 0xFF
                builder.append('%')
                builder.append(HEX_CHARS[value8 ushr 4].uppercaseChar())
                builder.append(HEX_CHARS[value8 and 0x0F].uppercaseChar())
            }
        }
    }
    return builder.toString()
}
