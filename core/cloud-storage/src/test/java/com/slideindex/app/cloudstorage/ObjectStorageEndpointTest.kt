package com.slideindex.app.cloudstorage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectStorageEndpointTest {

    @Test
    fun toS3Compatible_rewritesAliyunNativeEndpoints() {
        assertEquals(
            "https://s3.oss-cn-hangzhou.aliyuncs.com/",
            ObjectStorageEndpoint.toS3Compatible("oss-cn-hangzhou.aliyuncs.com", "mybucket"),
        )
        assertEquals(
            "https://s3.oss-cn-hangzhou-internal.aliyuncs.com/",
            ObjectStorageEndpoint.toS3Compatible("https://oss-cn-hangzhou-internal.aliyuncs.com", "mybucket"),
        )
        assertEquals(
            "https://s3.oss-accelerate.aliyuncs.com/",
            ObjectStorageEndpoint.toS3Compatible("https://oss-accelerate.aliyuncs.com", "mybucket"),
        )
    }

    @Test
    fun toS3Compatible_stripsVirtualHostedBucketLabel() {
        // 阿里云控制台给的是虚拟主机风格端点时会带 bucket 前缀；
        // 不剥掉就会拼成 mybucket.mybucket.s3...
        assertEquals(
            "https://s3.oss-cn-hangzhou.aliyuncs.com/",
            ObjectStorageEndpoint.toS3Compatible("https://mybucket.oss-cn-hangzhou.aliyuncs.com", "mybucket"),
        )
    }

    @Test
    fun toS3Compatible_keepsAlreadyCompatibleAndForeignEndpoints() {
        assertEquals(
            "https://s3.oss-cn-hangzhou.aliyuncs.com/",
            ObjectStorageEndpoint.toS3Compatible("https://s3.oss-cn-hangzhou.aliyuncs.com", "mybucket"),
        )
        assertEquals(
            "https://s3.us-east-1.amazonaws.com/",
            ObjectStorageEndpoint.toS3Compatible("https://s3.us-east-1.amazonaws.com", "mybucket"),
        )
        assertEquals(
            "https://minio.example.com:9000/",
            ObjectStorageEndpoint.toS3Compatible("minio.example.com:9000", "mybucket"),
        )
        assertEquals(
            "https://account.r2.cloudflarestorage.com/",
            ObjectStorageEndpoint.toS3Compatible("https://account.r2.cloudflarestorage.com", "mybucket"),
        )
    }

    @Test
    fun inferRegion_readsRegionFromAliyunHost() {
        assertEquals("cn-hangzhou", ObjectStorageEndpoint.inferRegion("oss-cn-hangzhou.aliyuncs.com"))
        assertEquals("cn-hangzhou", ObjectStorageEndpoint.inferRegion("https://s3.oss-cn-hangzhou.aliyuncs.com"))
        assertEquals(
            "cn-hangzhou",
            ObjectStorageEndpoint.inferRegion("https://oss-cn-hangzhou-internal.aliyuncs.com"),
        )
        assertEquals("us-west-1", ObjectStorageEndpoint.inferRegion("https://s3.oss-us-west-1.aliyuncs.com"))
        // 加速端点没有地域信息
        assertNull(ObjectStorageEndpoint.inferRegion("https://s3.oss-accelerate.aliyuncs.com"))
        assertNull(ObjectStorageEndpoint.inferRegion("https://s3.us-east-1.amazonaws.com"))
        assertNull(ObjectStorageEndpoint.inferRegion("http://192.168.1.10:9000"))
    }

    @Test
    fun forcePathStyle_detectsHostsThatCannotUseVirtualHostedStyle() {
        assertTrue(ObjectStorageEndpoint.forcePathStyle("http://192.168.1.10:9000"))
        assertTrue(ObjectStorageEndpoint.forcePathStyle("localhost:9000"))
        assertTrue(ObjectStorageEndpoint.forcePathStyle("http://[::1]:9000"))
        assertTrue(ObjectStorageEndpoint.forcePathStyle("nas.local"))
        assertFalse(ObjectStorageEndpoint.forcePathStyle("https://s3.oss-cn-hangzhou.aliyuncs.com"))
        assertFalse(ObjectStorageEndpoint.forcePathStyle("https://account.r2.cloudflarestorage.com"))
    }

    @Test
    fun isUsable_rejectsGarbage() {
        assertTrue(ObjectStorageEndpoint.isUsable("s3.us-east-1.amazonaws.com"))
        assertFalse(ObjectStorageEndpoint.isUsable(""))
        assertFalse(ObjectStorageEndpoint.isUsable("   "))
    }
}
