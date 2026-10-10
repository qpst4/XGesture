package com.slideindex.app.cloudstorage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudStorageConfigCodecTest {

    @Test
    fun codec_roundTripsSealedConfigHierarchy() {
        val settings = CloudStorageSettings(
            configs = listOf(
                WebDavStorageConfig(
                    id = "w1",
                    displayName = "坚果云",
                    baseDir = "/cebian",
                    server = "https://dav.jianguoyun.com/dav",
                    username = "alice",
                    password = "secret",
                    userAgent = "cebian",
                ),
                S3StorageConfig(
                    id = "s1",
                    displayName = "阿里云 OSS",
                    baseDir = "cebian",
                    endpoint = "oss-cn-hangzhou.aliyuncs.com",
                    accessKey = "ak",
                    secretKey = "sk",
                    bucket = "bucket",
                    region = "cn-hangzhou",
                    pathStyle = false,
                ),
            ),
            activeConfigId = "s1",
            retentionCount = 3,
        )

        val decoded = CloudStorageConfigCodec.decode(CloudStorageConfigCodec.encode(settings))

        assertEquals(settings, decoded)
        assertTrue(decoded.configs[0] is WebDavStorageConfig)
        assertTrue(decoded.configs[1] is S3StorageConfig)
        assertEquals("s1", decoded.active?.id)
        assertEquals(3, decoded.retentionCount)
    }

    @Test
    fun decode_survivesBrokenInput() {
        // 坏配置不能把设置页打死：退化成空配置
        assertEquals(CloudStorageSettings.EMPTY, CloudStorageConfigCodec.decode("not json"))
        assertEquals(CloudStorageSettings.EMPTY, CloudStorageConfigCodec.decode(null))
        assertEquals(CloudStorageSettings.EMPTY, CloudStorageConfigCodec.decode(""))
    }

    @Test
    fun decode_ignoresUnknownFieldsForForwardCompatibility() {
        val raw = """
            {"configs":[],"activeConfigId":null,"retentionCount":7,"futureField":"x"}
        """.trimIndent()
        assertEquals(7, CloudStorageConfigCodec.decode(raw).retentionCount)
    }

    @Test
    fun active_isNullWhenIdDoesNotMatchAnyConfig() {
        assertNull(CloudStorageSettings(configs = emptyList(), activeConfigId = "ghost").active)
    }

    @Test
    fun retentionSemantics_zeroOrNegativeMeansUnlimited() {
        assertTrue(CloudStorageSettings(retentionCount = 0).hasRetentionLimit.not())
        assertTrue(CloudStorageSettings(retentionCount = -1).hasRetentionLimit.not())
        assertTrue(CloudStorageSettings(retentionCount = 1).hasRetentionLimit)
    }

    @Test
    fun toString_neverLeaksCredentials() {
        val webDav = WebDavStorageConfig(
            id = "w1",
            displayName = "nc",
            baseDir = "/cebian",
            server = "https://example.com/dav",
            username = "alice",
            password = "p@ssw0rd",
        )
        val s3 = S3StorageConfig(
            id = "s1",
            displayName = "s3",
            baseDir = "/cebian",
            endpoint = "oss-cn-hangzhou.aliyuncs.com",
            accessKey = "AKIAACCESSKEY",
            secretKey = "SuperSecretKey",
            bucket = "b",
        )
        // 日志里出现口令 = 事故，这里当硬约束测
        assertTrue("webdav 口令泄漏：$webDav", !webDav.toString().contains("p@ssw0rd"))
        assertTrue("webdav 用户名泄漏：$webDav", !webDav.toString().contains("alice"))
        assertTrue("s3 secretKey 泄漏：$s3", !s3.toString().contains("SuperSecretKey"))
        assertTrue("s3 accessKey 泄漏：$s3", !s3.toString().contains("AKIAACCESSKEY"))
    }
}
