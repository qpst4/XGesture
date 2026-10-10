package com.slideindex.app.cloudstorage

import com.slideindex.app.cloudstorage.internal.S3Client
import com.slideindex.app.cloudstorage.internal.WebDavClient
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * 按配置创建远端存储客户端。
 *
 * 三种后端共用同一个 OkHttpClient：连接池与线程池复用，且模块内不再各自 new 一个
 * （ClipShare 的备份流程会独立 new 客户端，与中转流程的连接互不复用）。
 */
object CloudStorageClientFactory {

    /**
     * 传输超时按"大文件备份"设定：不设 `callTimeout`，否则几十 MB 的备份包会被整体掐断；
     * 读写超时是**单次 IO 操作**的超时（不是整个请求），60s 足够覆盖移动网络的抖动。
     */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun create(config: CloudStorageConfig): CloudStorageClient = when (config) {
        is WebDavStorageConfig -> WebDavClient(config, httpClient)
        is S3StorageConfig -> S3Client(config, httpClient)
    }
}
