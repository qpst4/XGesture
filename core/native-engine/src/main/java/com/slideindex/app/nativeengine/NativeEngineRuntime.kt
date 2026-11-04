package com.slideindex.app.nativeengine

/**
 * 供非 Hilt 入口访问引擎协调器。
 */
object NativeEngineRuntime {
    @Volatile
    var coordinator: NativeEnginePackCoordinator? = null

    /** OCR 引擎包被删除/重装时通知上层丢弃 PaddleOCR 会话。 */
    @Volatile
    var onOcrEnginePackInvalidated: (() -> Unit)? = null
}
