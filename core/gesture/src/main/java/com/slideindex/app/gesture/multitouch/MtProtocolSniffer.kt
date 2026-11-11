package com.slideindex.app.gesture.multitouch

/**
 * 根据**真实事件流**判断触摸设备用的是 Type A 还是 Type B。
 *
 * 为什么不能只看 `getevent -p`：能力表只能说明设备**声明**了哪些轴，不能保证它的上报风格。
 * 驱动/ROM 的混合实现会出现「声明像 B、实际按 A 发」的情况；静态判断一旦出错，
 * 解析器会一个触点都拼不出来，用户看到的就是「状态正常但怎么划都没反应」。
 *
 * 判断规则（按证据强度排序）：
 * - 出现 `SYN_MT_REPORT` → Type A（这是 A 独有的分隔符）；
 * - 出现 `ABS_MT_SLOT` / `ABS_MT_TRACKING_ID` → Type B；
 * - 只有坐标、没有分隔符/tracking → 按 Type A 处理（单点/老式风格）。
 *
 * 看到 slot 但始终没有 tracking id 时，[trackingIdOptional] 为 true：
 * 让解码器走「靠 BTN_TOUCH 整体抬起」的 B 变体。
 */
class MtProtocolSniffer {

    var sawSynMtReport: Boolean = false
        private set

    var sawSlot: Boolean = false
        private set

    var sawTrackingId: Boolean = false
        private set

    /** 是否已经看到「真的在摸」的证据（坐标或按下），用于决定何时定案。 */
    var sawTouch: Boolean = false
        private set

    fun onEvent(type: Int, code: Int, value: Int) {
        when {
            type == MtTouchFrameDecoder.EV_SYN && code == MtTouchFrameDecoder.SYN_MT_REPORT -> {
                sawSynMtReport = true
            }

            type == MtTouchFrameDecoder.EV_ABS && code == MtTouchFrameDecoder.ABS_MT_SLOT -> {
                sawSlot = true
            }

            type == MtTouchFrameDecoder.EV_ABS &&
                code == MtTouchFrameDecoder.ABS_MT_TRACKING_ID -> {
                sawTrackingId = true
                sawTouch = true
            }

            type == MtTouchFrameDecoder.EV_ABS &&
                (code == MtTouchFrameDecoder.ABS_MT_POSITION_X ||
                    code == MtTouchFrameDecoder.ABS_MT_POSITION_Y) -> {
                sawTouch = true
            }

            type == MtTouchFrameDecoder.EV_KEY &&
                code == MtTouchFrameDecoder.BTN_TOUCH && value != 0 -> {
                sawTouch = true
            }
        }
    }

    val protocol: MtTouchFrameDecoder.Protocol
        get() = when {
            sawSynMtReport -> MtTouchFrameDecoder.Protocol.TYPE_A
            sawSlot || sawTrackingId -> MtTouchFrameDecoder.Protocol.TYPE_B
            else -> MtTouchFrameDecoder.Protocol.TYPE_A
        }

    /** 有 slot 但没有 tracking id：Type B 的混合变体。 */
    val trackingIdOptional: Boolean
        get() = sawSlot && !sawTrackingId

    fun reset() {
        sawSynMtReport = false
        sawSlot = false
        sawTrackingId = false
        sawTouch = false
    }
}
