package com.slideindex.app.gesture.multitouch

/**
 * evdev 多点触摸帧解码器（纯逻辑，无 Android 依赖）。
 *
 * 触摸屏驱动有两种上报协议：
 *
 * - **Type B**（现代 Android 主流）：用 `ABS_MT_SLOT` 给每根手指分配槽位，再用
 *   `ABS_MT_TRACKING_ID` 表示这根手指按下 / 抬起；
 * - **Type A**（老设备、部分 ROM）：没有槽位，一帧里把每个接触点的坐标连续上报，
 *   用 `SYN_MT_REPORT` 分隔相邻的接触点。
 *
 * 本类只做「原始事件 → 触点列表」的翻译：调用方把每条 evdev 事件的
 * `(type, code, value)` 交给 [onEvent]，返回 `true` 表示收到 `SYN_REPORT`（一帧结束），
 * 此时读 [pointers]。
 *
 * 坐标归一化沿用识别器约定：x、y 都除以**屏高**（y 轴量程），横竖方向同一尺度
 * （见 [TouchPointer]）。
 */
class MtTouchFrameDecoder(
    val protocol: Protocol,
    private val panelHeight: Float,
    /**
     * Type B 的变体：设备有 slot，但不发 tracking id（少数混合驱动）。
     *
     * 这种情况下没有「单指抬起」的事件，只能靠 `BTN_TOUCH` 判断整体抬起；
     * 触点数变化也只会在全部抬起后重新按下时体现。
     */
    val trackingIdOptional: Boolean = false,
) {
    enum class Protocol {
        /** 老式协议：无 slot / tracking id，靠 `SYN_MT_REPORT` 分隔接触点。 */
        TYPE_A,

        /** 现代协议：`ABS_MT_SLOT` + `ABS_MT_TRACKING_ID` 给每根手指编号。 */
        TYPE_B,
    }

    private class Contact(
        var trackingId: Int = -1,
        var x: Float = Float.NaN,
        var y: Float = Float.NaN,
    ) {
        val hasPosition: Boolean get() = x.isFinite() && y.isFinite()
    }

    /** Type B：slot 下标 → 当前接触点。 */
    private val slots = HashMap<Int, Contact>()
    private var currentSlot = 0

    /** Type A：正在拼接的接触点与已经收完的接触点。 */
    private var currentContact: Contact? = null
    private val frameContacts = ArrayList<Contact>()

    /**
     * Type A 的 `BTN_TOUCH` 状态。
     *
     * 初始为 `true`：部分设备根本不发这个键，不能因为「没收到按下事件」就永远判成没触摸。
     * 只有明确收到抬起（0）时才清空本帧接触点。
     */
    private var touchDown = true

    /** 最近一帧的触点（[onEvent] 返回 `true` 时有效）。 */
    var pointers: List<TouchPointer> = emptyList()
        private set

    /**
     * 喂一条 evdev 事件。
     *
     * @return 收到 `SYN_REPORT`（一帧结束）时返回 `true`，此时 [pointers] 是这一帧的触点。
     */
    fun onEvent(type: Int, code: Int, value: Int): Boolean {
        when (type) {
            EV_ABS -> handleAbs(code, value)

            EV_KEY -> if (code == BTN_TOUCH) {
                touchDown = value != 0
                if (!touchDown) {
                    if (protocol == Protocol.TYPE_A) {
                        frameContacts.clear()
                        currentContact = null
                    } else if (trackingIdOptional) {
                        // B 的变体：没有 tracking id，BTN_TOUCH=0 就是「所有手指都抬起了」。
                        slots.clear()
                    }
                }
            }

            EV_SYN -> when (code) {
                // Type A：一个接触点结束。空的 SYN_MT_REPORT 是合法的（表示本帧没有更多点）。
                SYN_MT_REPORT -> if (protocol == Protocol.TYPE_A) finishCurrentContact()
                SYN_REPORT -> {
                    commitFrame()
                    return true
                }
                SYN_DROPPED -> reset()
            }
        }
        return false
    }

    /** 清空所有中间状态（`SYN_DROPPED` 或重新打开设备时调用）。 */
    fun reset() {
        slots.clear()
        currentSlot = 0
        currentContact = null
        frameContacts.clear()
        touchDown = true
        pointers = emptyList()
    }

    private fun handleAbs(code: Int, value: Int) {
        when (protocol) {
            Protocol.TYPE_B -> when (code) {
                ABS_MT_SLOT -> currentSlot = value

                ABS_MT_TRACKING_ID -> {
                    val contact = slots.getOrPut(currentSlot) { Contact() }
                    contact.trackingId = value
                    if (value < 0) {
                        // 抬起：位置作废，本帧结束时从槽位表里清掉。
                        contact.x = Float.NaN
                        contact.y = Float.NaN
                    }
                }

                ABS_MT_POSITION_X -> slots.getOrPut(currentSlot) { Contact() }.x = value.toFloat()
                ABS_MT_POSITION_Y -> slots.getOrPut(currentSlot) { Contact() }.y = value.toFloat()
            }

            Protocol.TYPE_A -> when (code) {
                ABS_MT_POSITION_X -> contactForTypeA().x = value.toFloat()
                ABS_MT_POSITION_Y -> contactForTypeA().y = value.toFloat()
                // 少数 Type A 设备也会带 tracking id：有就拿来当稳定 id，没有就用序号。
                ABS_MT_TRACKING_ID -> contactForTypeA().trackingId = value
            }
        }
    }

    private fun contactForTypeA(): Contact =
        currentContact ?: Contact().also { currentContact = it }

    private fun finishCurrentContact() {
        val contact = currentContact
        currentContact = null
        if (contact != null && contact.hasPosition) {
            frameContacts += contact
        }
    }

    private fun commitFrame() {
        val frame = ArrayList<TouchPointer>(4)
        when (protocol) {
            Protocol.TYPE_B -> {
                if (trackingIdOptional && !touchDown) {
                    // 已经在 BTN_TOUCH 抬起时清过槽位；这里再兜一层，避免残留。
                    slots.clear()
                } else {
                    slots.entries
                        .filter {
                            it.value.hasPosition &&
                                (trackingIdOptional || it.value.trackingId >= 0)
                        }
                        .sortedBy { it.key }
                        .forEach { (slot, contact) -> frame += toPointer(slot, contact) }
                    // 抬起的槽位留到本帧之后再清，避免同一帧里「先抬起后又按下」被误删。
                    // 变体模式下 trackingId 恒为 -1，不能按它清理。
                    if (!trackingIdOptional) {
                        slots.entries.removeAll { it.value.trackingId < 0 }
                    }
                }
            }

            Protocol.TYPE_A -> {
                if (touchDown) {
                    frameContacts.forEachIndexed { index, contact ->
                        val id = contact.trackingId.takeIf { it >= 0 } ?: index
                        frame += toPointer(id, contact)
                    }
                }
                frameContacts.clear()
                currentContact = null
            }
        }
        pointers = frame
    }

    private fun toPointer(id: Int, contact: Contact): TouchPointer {
        val height = panelHeight.coerceAtLeast(1f)
        return TouchPointer(
            id = id,
            x = contact.x / height,
            y = contact.y / height,
        )
    }

    companion object {
        const val EV_SYN = 0
        const val EV_KEY = 1
        const val EV_ABS = 3

        const val SYN_REPORT = 0

        /** `SYN_MT_REPORT`：Type A 里分隔相邻接触点。 */
        const val SYN_MT_REPORT = 2
        const val SYN_DROPPED = 3

        const val ABS_MT_SLOT = 47
        const val ABS_MT_POSITION_X = 53
        const val ABS_MT_POSITION_Y = 54
        const val ABS_MT_TRACKING_ID = 57
        const val BTN_TOUCH = 330
    }
}
