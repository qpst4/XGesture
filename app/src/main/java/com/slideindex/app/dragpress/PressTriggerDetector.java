/*
 * 移植自 GestureEVO（https://github.com/LumiaGG/GestureEVO，GPL-3.0-only）
 *   源文件：drop/PressTriggerDetector.java
 * 上游 GestureEVO 另标注该文件转写自 MMGG（com.leaf.hyperdragshare，GPL-3.0）；
 * 已按本项目的包结构、设置体系与流转栏集成层改写（部分逻辑为本项目新增）；
 * 版权与许可声明见 THIRD_PARTY_NOTICES.md，文件级映射见 docs/ATTRIBUTIONS.md。
 */

package com.slideindex.app.dragpress;

/**
 * 单指长按与移动判定器：在指定超时时间内位移限制在 36px 以内，超时后 armed；
 * armed 之后微移超过 dragThreshold 即标记 fired。
 */
public final class PressTriggerDetector {
    private static final float MOVE_LIMIT = 36f;
    private final float dragThreshold;
    private boolean active;
    private boolean armed;
    private boolean fired;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private float anchorX;
    private float anchorY;
    private long downTime = -1L;
    private long deadline = -1L;

    public PressTriggerDetector(float dragThreshold) {
        this.dragThreshold = dragThreshold;
    }

    public void start(float x, float y, long time, long confirmMillis) {
        active = true;
        armed = false;
        fired = false;
        lastX = downX = x;
        lastY = downY = y;
        downTime = time;
        deadline = time + confirmMillis;
    }

    public void onPointerEvent(int action, float x, float y, int pointerCount) {
        if (!active) return;
        if (action != 2 || pointerCount != 1) {
            cancel();
            return;
        }
        lastX = x;
        lastY = y;
        if (fired) return;
        if (armed) {
            float dx = x - anchorX;
            float dy = y - anchorY;
            if (dx * dx + dy * dy > dragThreshold * dragThreshold) {
                fired = true;
            }
        } else if (Math.abs(x - downX) > MOVE_LIMIT || Math.abs(y - downY) > MOVE_LIMIT) {
            cancel();
        }
    }

    public void arm(long now) {
        if (!active || armed || now < deadline) return;
        armed = true;
        anchorX = lastX;
        anchorY = lastY;
    }

    public void cancel() {
        active = false;
        armed = false;
        deadline = -1L;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isReady() {
        return active && armed;
    }

    public boolean isFired() {
        return fired;
    }

    public long downTime() {
        return downTime;
    }

    public long deadline() {
        return deadline;
    }
}
