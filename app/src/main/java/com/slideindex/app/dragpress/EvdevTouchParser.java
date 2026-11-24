/*
 * 移植自 GestureEVO（https://github.com/LumiaGG/GestureEVO，GPL-3.0-only）
 *   源文件：drop/EvdevTouchParser.java
 * 上游 GestureEVO 另标注该文件转写自 MMGG（com.leaf.hyperdragshare，GPL-3.0）；
 * 已按本项目的包结构、设置体系与流转栏集成层改写（部分逻辑为本项目新增）；
 * 版权与许可声明见 THIRD_PARTY_NOTICES.md，文件级映射见 docs/ATTRIBUTIONS.md。
 */

package com.slideindex.app.dragpress;

import android.view.MotionEvent;

import java.util.Arrays;

/**
 * 将底层的 multi-touch evdev 事件帧转成稳定的单指追踪事件流。
 */
final class EvdevTouchParser {
    interface Listener {
        void onFrame(
                int action,
                float rawX,
                float rawY,
                int rawTouchMajor,
                int rawTouchMinor,
                int rawWidthMajor);
    }

    static final int EV_SYN = 0;
    static final int EV_KEY = 1;
    static final int EV_ABS = 3;
    static final int SYN_REPORT = 0;
    static final int BTN_TOUCH = 330;
    static final int ABS_MT_SLOT = 47;
    static final int ABS_MT_TOUCH_MAJOR = 48;
    static final int ABS_MT_TOUCH_MINOR = 49;
    static final int ABS_MT_WIDTH_MAJOR = 50;
    static final int ABS_MT_POSITION_X = 53;
    static final int ABS_MT_POSITION_Y = 54;
    static final int ABS_MT_TRACKING_ID = 57;

    private static final int MAX_SLOTS = 16;
    private static final int UPDATED_X = 1;
    private static final int UPDATED_Y = 2;
    private static final int UPDATED_TOUCH_MAJOR = 4;
    private static final int UPDATED_TOUCH_MINOR = 8;
    private static final int UPDATED_WIDTH_MAJOR = 16;

    private final Listener listener;
    private final float[] slotX = new float[MAX_SLOTS];
    private final float[] slotY = new float[MAX_SLOTS];
    private final int[] slotTouchMajor = new int[MAX_SLOTS];
    private final int[] slotTouchMinor = new int[MAX_SLOTS];
    private final int[] slotWidthMajor = new int[MAX_SLOTS];
    private final int[] trackingId = new int[MAX_SLOTS];
    private final int[] updatedAxes = new int[MAX_SLOTS];

    private int currentSlot;
    private int primarySlot = -1;
    private boolean gestureActive;
    private boolean buttonTouchSeen;
    private boolean buttonTouchDown;
    private boolean buttonDownEdge;
    private boolean sawTrackingStart;
    private boolean primaryLifted;
    private boolean frameChanged;
    private boolean ignoreUntilAllPointersUp;
    private float lastX = Float.NaN;
    private float lastY = Float.NaN;
    private int lastTouchMajor;
    private int lastTouchMinor;
    private int lastWidthMajor;

    EvdevTouchParser(Listener listener) {
        this.listener = listener;
        resetSlots();
    }

    void consume(int type, int code, int value) {
        if (type == EV_ABS) {
            consumeAbsolute(code, value);
        } else if (type == EV_KEY && code == BTN_TOUCH) {
            buttonTouchSeen = true;
            if (value != 0 && !buttonTouchDown) {
                buttonDownEdge = true;
                clearStaleCoordinates();
            }
            buttonTouchDown = value != 0;
            frameChanged = true;
        } else if (type == EV_SYN && code == SYN_REPORT) {
            emitFrame();
        }
    }

    void cancel() {
        if (gestureActive && Float.isFinite(lastX) && Float.isFinite(lastY)) {
            listener.onFrame(
                    MotionEvent.ACTION_CANCEL,
                    lastX,
                    lastY,
                    lastTouchMajor,
                    lastTouchMinor,
                    lastWidthMajor);
        }
        resetGesture();
        ignoreUntilAllPointersUp = false;
        resetSlots();
    }

    private void consumeAbsolute(int code, int value) {
        switch (code) {
            case ABS_MT_SLOT:
                if (value >= 0 && value < MAX_SLOTS) {
                    currentSlot = value;
                }
                break;
            case ABS_MT_TRACKING_ID:
                if (currentSlot >= 0 && currentSlot < MAX_SLOTS) {
                    trackingId[currentSlot] = value;
                    if (value >= 0) {
                        sawTrackingStart = true;
                    } else if (currentSlot == primarySlot) {
                        primaryLifted = true;
                    }
                    frameChanged = true;
                }
                break;
            case ABS_MT_POSITION_X:
                if (currentSlot >= 0 && currentSlot < MAX_SLOTS) {
                    slotX[currentSlot] = value;
                    updatedAxes[currentSlot] |= UPDATED_X;
                    frameChanged = true;
                }
                break;
            case ABS_MT_POSITION_Y:
                if (currentSlot >= 0 && currentSlot < MAX_SLOTS) {
                    slotY[currentSlot] = value;
                    updatedAxes[currentSlot] |= UPDATED_Y;
                    frameChanged = true;
                }
                break;
            case ABS_MT_TOUCH_MAJOR:
                if (currentSlot >= 0 && currentSlot < MAX_SLOTS) {
                    slotTouchMajor[currentSlot] = value;
                    updatedAxes[currentSlot] |= UPDATED_TOUCH_MAJOR;
                    frameChanged = true;
                }
                break;
            case ABS_MT_TOUCH_MINOR:
                if (currentSlot >= 0 && currentSlot < MAX_SLOTS) {
                    slotTouchMinor[currentSlot] = value;
                    updatedAxes[currentSlot] |= UPDATED_TOUCH_MINOR;
                    frameChanged = true;
                }
                break;
            case ABS_MT_WIDTH_MAJOR:
                if (currentSlot >= 0 && currentSlot < MAX_SLOTS) {
                    slotWidthMajor[currentSlot] = value;
                    updatedAxes[currentSlot] |= UPDATED_WIDTH_MAJOR;
                    frameChanged = true;
                }
                break;
        }
    }

    private void emitFrame() {
        if (!frameChanged) {
            buttonDownEdge = false;
            return;
        }
        frameChanged = false;

        int activeCount = countActiveSlots();
        if (ignoreUntilAllPointersUp) {
            if (activeCount == 0 && (!buttonTouchSeen || !buttonTouchDown)) {
                ignoreUntilAllPointersUp = false;
                resetSlots();
            }
            buttonDownEdge = false;
            sawTrackingStart = false;
            primaryLifted = false;
            return;
        }

        if (primarySlot >= 0 && trackingId[primarySlot] < 0) {
            primaryLifted = true;
        }

        if (gestureActive) {
            if (activeCount == 0 || primaryLifted || (buttonTouchSeen && !buttonTouchDown)) {
                if (Float.isFinite(lastX) && Float.isFinite(lastY)) {
                    listener.onFrame(
                            MotionEvent.ACTION_UP,
                            lastX,
                            lastY,
                            lastTouchMajor,
                            lastTouchMinor,
                            lastWidthMajor);
                }
                resetGesture();
                if (activeCount > 0 || (buttonTouchSeen && buttonTouchDown)) {
                    ignoreUntilAllPointersUp = true;
                }
            } else {
                int slot = primarySlot;
                float x = slotX[slot];
                float y = slotY[slot];
                if (Float.isFinite(x) && Float.isFinite(y)) {
                    int flags = updatedAxes[slot];
                    boolean moved = (flags & (UPDATED_X | UPDATED_Y)) != 0
                            && (x != lastX || y != lastY);
                    lastX = x;
                    lastY = y;
                    lastTouchMajor = slotTouchMajor[slot];
                    lastTouchMinor = slotTouchMinor[slot];
                    lastWidthMajor = slotWidthMajor[slot];
                    if (moved) {
                        listener.onFrame(
                                MotionEvent.ACTION_MOVE,
                                x,
                                y,
                                lastTouchMajor,
                                lastTouchMinor,
                                lastWidthMajor);
                    }
                }
            }
        } else {
            boolean shouldStart = (buttonTouchSeen && buttonDownEdge) || (!buttonTouchSeen && sawTrackingStart);
            if (shouldStart && activeCount == 1) {
                int slot = findFirstActiveSlot();
                if (slot >= 0) {
                    float x = slotX[slot];
                    float y = slotY[slot];
                    if (Float.isFinite(x) && Float.isFinite(y)) {
                        primarySlot = slot;
                        gestureActive = true;
                        lastX = x;
                        lastY = y;
                        lastTouchMajor = slotTouchMajor[slot];
                        lastTouchMinor = slotTouchMinor[slot];
                        lastWidthMajor = slotWidthMajor[slot];
                        listener.onFrame(
                                MotionEvent.ACTION_DOWN,
                                x,
                                y,
                                lastTouchMajor,
                                lastTouchMinor,
                                lastWidthMajor);
                    }
                }
            } else if (activeCount > 1) {
                ignoreUntilAllPointersUp = true;
            }
        }

        buttonDownEdge = false;
        sawTrackingStart = false;
        primaryLifted = false;
        Arrays.fill(updatedAxes, 0);
    }

    private int countActiveSlots() {
        int count = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (trackingId[i] >= 0) count++;
        }
        return count;
    }

    private int findFirstActiveSlot() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (trackingId[i] >= 0) return i;
        }
        return -1;
    }

    private void clearStaleCoordinates() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (trackingId[i] < 0) {
                slotX[i] = Float.NaN;
                slotY[i] = Float.NaN;
                slotTouchMajor[i] = 0;
                slotTouchMinor[i] = 0;
                slotWidthMajor[i] = 0;
            }
        }
    }

    private void resetGesture() {
        gestureActive = false;
        primarySlot = -1;
        lastX = Float.NaN;
        lastY = Float.NaN;
        lastTouchMajor = 0;
        lastTouchMinor = 0;
        lastWidthMajor = 0;
    }

    private void resetSlots() {
        currentSlot = 0;
        Arrays.fill(slotX, Float.NaN);
        Arrays.fill(slotY, Float.NaN);
        Arrays.fill(slotTouchMajor, 0);
        Arrays.fill(slotTouchMinor, 0);
        Arrays.fill(slotWidthMajor, 0);
        Arrays.fill(trackingId, -1);
        Arrays.fill(updatedAxes, 0);
    }
}
