package com.slideindex.app.cloudstorage

/**
 * 远端存储操作失败。
 *
 * [httpStatus] 与服务端错误码会被带进 [message]，让界面可以直接展示
 * `403 SignatureDoesNotMatch` 这类可操作的原因，而不是笼统的"备份失败"。
 */
class CloudStorageException(
    message: String,
    cause: Throwable? = null,
    val httpStatus: Int? = null,
    val serverCode: String? = null,
) : Exception(message, cause) {

    /** 配置类问题（凭据、桶名、目录不存在）通常无法靠重试解决，界面据此给出不同文案。 */
    val isConfigurationProblem: Boolean
        get() = httpStatus == 401 || httpStatus == 403 || httpStatus == 404
}
