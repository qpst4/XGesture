/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

package com.slideindex.app.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import com.slideindex.app.gesture.SelectedHintMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.accessibilityservice.AccessibilityService;

public final class HoneycombOverlayView extends View {
    private static final String TAG = "HoneycombOverlayView";
    private static final float HOLD_GRID_ACCELERATION = 0.35f;
    interface Listener {
        /**
         * @param selectionPressDurationMs overlay 点按时长，或连续滑选时停在当前图标上的停留时长（ms）
         */
        void onLaunch(HoneycombRuntimeTarget target, long selectionPressDurationMs);
        void onClosed();
    }

    private final float density;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LocalFrostedGlassDrawable frostedGlassDrawable = new LocalFrostedGlassDrawable(this);
    private final Paint iconPlatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint placeholderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint namePillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallpaperPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF wallpaperBounds = new RectF();
    private final RectF blurBounds = new RectF();
    private final Rect iconOldBounds = new Rect();
    private final Path clipPath = new Path();
    private final ScaleGestureDetector scaleDetector;
    private final int touchSlop;
    private float[] visualX = new float[0];
    private float[] visualY = new float[0];
    private float[] drawnX = new float[0];
    private float[] drawnY = new float[0];
    private float[] drawnRadius = new float[0];
    private float[] drawnScale = new float[0];
    private float[] stableHitX = new float[0];
    private float[] stableHitY = new float[0];
    private float[] stableHitRadius = new float[0];

    private List<HoneycombRuntimeTarget> targets = Collections.emptyList();
    private List<HoneycombGeometry.Point> basePoints = Collections.emptyList();
    private HoneycombCorner corner;
    private Listener listener;
    private boolean browseMode;
    private boolean hapticEnabled;
    private boolean emptyTapClose;
    private boolean showSelectedName;
    private int hintIconSizeDp = SelectedHintMetrics.DEFAULT_ICON_SIZE_DP;
    private boolean forceCircularIcons = true;
    private boolean interactionPaused;
    private boolean closing;
    private boolean released;
    private int speedIndex;
    private int inertiaIndex;
    private float iconSize;
    private float pitch;
    private float centerScale;
    private float edgeScale;
    private float selectionScale;
    private float anchorX = Float.NaN;
    private float anchorY = Float.NaN;
    private boolean followFingerPosition;
    private int fixedXPercent;
    private int fixedYPercent;
    private Bitmap wallpaper;
    private boolean usesNativeWindowBlur;
    private int backgroundStyle;
    private int dimPercent;
    private int blurDp;
    private int discSizePercent;
    private int statusBarHeight;
    private float panX;
    private float panY;
    private float zoom = 1f;
    private float entryProgress;
    private float dismissProgress;
    private float confirmProgress;
    private float confirmStartX = Float.NaN;
    private float confirmStartY = Float.NaN;
    private float confirmStartScale = Float.NaN;
    private int selected = -1;
    private HoneycombRuntimeTarget nameTarget;
    private float selectionProgress;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private boolean dragging;
    private boolean scaledDuringGesture;
    private VelocityTracker velocityTracker;
    private final ValueAnimator selectionAnimator = new ValueAnimator();
    private float baseMaximumX;
    private float baseMaximumY;
    private float panMinimumX;
    private float panMaximumX;
    private float panMinimumY;
    private float panMaximumY;
    private HoneycombRuntimeTarget fittedNameTarget;
    private CharSequence fittedName = "";
    private float fittedNameMaxWidth = -1f;
    private float fittedNameWidth;
    private int fittedHintIconSizeDp = -1;
    private boolean physicsRunning;
    private float physicsVelocityX;
    private float physicsVelocityY;
    private long lastPhysicsFrameNanos;
    private final Choreographer.FrameCallback physicsFrame = this::stepPhysics;
    private long lastHapticAt;
    private int lastHapticIndex = -1;
    private boolean pointerValid;
    private float pointerX;
    private float pointerY;
    private float lensX = Float.NaN;
    private float lensY = Float.NaN;
    private boolean externalTracking;
    private long selectionTouchDownUptimeMs;
    private long selectionSettledUptimeMs;
    private int launchLongPressDurationMs;
    private boolean launchLongPressTrackingEnabled;
    private float externalLastX;
    private float externalLastY;
    private float externalVelocityX;
    private float externalVelocityY;
    private long externalLastTime;
    private long lastHoldPanFrameMs;
    private final BlurredWallpaperCache.Callback wallpaperCallback = bitmap -> post(() -> {
        if (released || backgroundStyle != HoneycombDisplayConfig.BACKGROUND_BLUR || usesNativeWindowBlur) return;
        if (bitmap != null && bitmap.isRecycled()) return;
        wallpaper = bitmap;
        invalidate();
    });
    private final Runnable systemWallpaperLoad = () -> {
        if (released || backgroundStyle != HoneycombDisplayConfig.BACKGROUND_WALLPAPER_BLUR) return;
        final int blur = blurDp;
        final Context app = getContext().getApplicationContext();
        new Thread(() -> {
            Bitmap blurred = SystemWallpaperBlurHelper.loadBlurredSync(app, blur);
            post(() -> {
                if (released || backgroundStyle != HoneycombDisplayConfig.BACKGROUND_WALLPAPER_BLUR) {
                    return;
                }
                wallpaper = blurred;
                invalidate();
            });
        }, "honeycomb-wallpaper").start();
    };
    private final Runnable holdSelectionUpdate = () -> {
        if (!browseMode && pointerValid && !interactionPaused && !closing) {
            selectAt(pointerX, pointerY, true);
        }
    };
    private final Runnable selectionLongPressRunnable = () -> {
        if (selected < 0 || closing || interactionPaused || !launchLongPressTrackingEnabled) return;
        if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    };

    HoneycombOverlayView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        density = context.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                stopPhysics();
                scaledDuringGesture = true;
                return browseMode && !interactionPaused && !closing;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                float oldZoom = zoom;
                float next = HoneycombGeometry.clamp(oldZoom * detector.getScaleFactor(), 0.78f, 1.35f);
                float centerX = resolvedCenterX();
                float centerY = resolvedCenterY();
                float contentX = (detector.getFocusX() - centerX) / oldZoom - panX;
                float contentY = (detector.getFocusY() - centerY) / oldZoom - panY;
                zoom = next;
                panX = (detector.getFocusX() - centerX) / next - contentX;
                panY = (detector.getFocusY() - centerY) / next - contentY;
                clampPan(false);
                invalidate();
                return true;
            }
        });
        setFocusableInTouchMode(true);
        backgroundPaint.setColor(Color.BLACK);
        iconPlatePaint.setColor(0xff17181d);
        namePaint.setColor(Color.WHITE);
        namePaint.setTextSize(SelectedHintMetrics.textSizePx(
                SelectedHintMetrics.DEFAULT_ICON_SIZE_DP, density));
        namePaint.setTextAlign(Paint.Align.LEFT);
        namePillPaint.setColor(0xdd20263f);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        statusBarHeight = 0;
        selectionAnimator.addUpdateListener(value -> {
            selectionProgress = (Float) value.getAnimatedValue();
            invalidate();
        });
    }

    void configure(List<HoneycombRuntimeTarget> targets, HoneycombCorner corner,
                   float triggerX, float triggerY,
                   HoneycombDisplayConfig config, boolean usesNativeWindowBlur,
                   Listener listener) {
        this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
        this.corner = corner;
        this.listener = listener;
        this.usesNativeWindowBlur = usesNativeWindowBlur;
        browseMode = config.getHoneycombMode() == HoneycombDisplayConfig.MODE_BROWSE;
        hapticEnabled = config.getHapticEnabled();
        emptyTapClose = config.getHoneycombEmptyTapClose();
        showSelectedName = config.getHoneycombShowSelectedName();
        hintIconSizeDp = SelectedHintMetrics.clampIconSizeDp(config.getSelectedHintIconSizeDp());
        namePaint.setTextSize(SelectedHintMetrics.textSizePx(hintIconSizeDp, density));
        fittedNameTarget = null;
        fittedNameMaxWidth = -1f;
        fittedHintIconSizeDp = -1;
        forceCircularIcons = config.getForceCircularIcons();
        nameTarget = null;
        speedIndex = config.getHoneycombAnimationSpeed();
        inertiaIndex = config.getHoneycombInertia();
        iconSize = config.getHoneycombIconSizeDp() * density;
        pitch = Math.max(iconSize + 4f * density, config.getHoneycombSpacingDp() * density);
        centerScale = config.getHoneycombCenterScale() / 100f;
        edgeScale = config.getHoneycombEdgeScale() / 100f;
        selectionScale = config.getHoneycombSelectionScale() / 100f;
        anchorX = Float.NaN;
        anchorY = Float.NaN;
        fixedXPercent = config.getHoneycombFixedXPercent();
        fixedYPercent = config.getHoneycombFixedYPercent();
        followFingerPosition = config.getHoneycombFollowFinger();
        if (followFingerPosition) {
            // The old full-disc clamp collapsed most bottom/edge trigger points onto
            // the same coordinate, which made this option look identical to fixed mode.
            // Keep the real finger coordinate and only reserve enough room for one icon.
            anchorX = triggerX;
            anchorY = triggerY;
        }
        backgroundStyle = config.getHoneycombBackgroundStyle();
        dimPercent = config.getHoneycombDimPercent();
        blurDp = config.getHoneycombBlurDp();
        discSizePercent = config.getHoneycombDiscSizePercent();
        launchLongPressDurationMs = config.getLaunchLongPressDurationMs();
        launchLongPressTrackingEnabled = config.getLaunchLongPressTrackingEnabled();
        selectionSettledUptimeMs = 0L;
        selectionTouchDownUptimeMs = 0L;
        cancelSelectionLongPressCheck();
        if (backgroundStyle == HoneycombDisplayConfig.BACKGROUND_BLUR && !usesNativeWindowBlur) {
            loadWallpaper();
        } else if (backgroundStyle == HoneycombDisplayConfig.BACKGROUND_WALLPAPER_BLUR) {
            // 主线程只读缓存；解码放后台。失败时仅靠 dim，不漆纯黑、不回退截屏缓存。
            removeCallbacks(systemWallpaperLoad);
            wallpaper = SystemWallpaperBlurHelper.peekCachedBlurred(
                    getContext().getApplicationContext(), blurDp);
            if (wallpaper == null || wallpaper.isRecycled()) {
                wallpaper = null;
                post(systemWallpaperLoad);
            }
        } else {
            wallpaper = null;
        }
        basePoints = HoneycombGeometry.compactPoints(targets.size(), pitch);
        baseMaximumX = 0f;
        baseMaximumY = 0f;
        for (HoneycombGeometry.Point point : basePoints) {
            baseMaximumX = Math.max(baseMaximumX, Math.abs(point.x));
            baseMaximumY = Math.max(baseMaximumY, Math.abs(point.y));
        }
        visualX = new float[targets.size()];
        visualY = new float[targets.size()];
        drawnX = new float[targets.size()];
        drawnY = new float[targets.size()];
        drawnRadius = new float[targets.size()];
        drawnScale = new float[targets.size()];
        stableHitX = new float[targets.size()];
        stableHitY = new float[targets.size()];
        stableHitRadius = new float[targets.size()];
        for (int index = 0; index < targets.size(); index++) {
            drawnX[index] = Float.NaN;
            drawnY[index] = Float.NaN;
            stableHitX[index] = Float.NaN;
            stableHitY[index] = Float.NaN;
        }
        externalTracking = true;
        externalLastX = triggerX;
        externalLastY = triggerY;
        externalLastTime = SystemClock.uptimeMillis();
        pointerX = triggerX;
        pointerY = triggerY;
        lensX = triggerX;
        lensY = triggerY;
        pointerValid = !browseMode;
        android.util.Log.i(TAG, "Honeycomb position mode=" + (followFingerPosition ? "finger" : "fixed")
                + " requested=" + Math.round(triggerX) + "," + Math.round(triggerY)
                + " fixed=" + fixedXPercent + "," + fixedYPercent);
    }

    private void loadWallpaper() {
        Context hostContext = getContext();
        if (!(hostContext instanceof AccessibilityService service)) {
            android.util.Log.w(TAG, "Cannot capture honeycomb background: accessibility unavailable");
            return;
        }
        Context appContext = hostContext.getApplicationContext();
        if (appContext == null) appContext = hostContext;
        wallpaper = BlurredWallpaperCache.captureFromDisplay(
                service,
                appContext,
                blurDp,
                wallpaperCallback);
        if (wallpaper != null && wallpaper.isRecycled()) wallpaper = null;
    }

    void releaseResources() {
        if (released) return;
        released = true;
        closing = true;
        listener = null;
        stopPhysics();
        selectionAnimator.cancel();
        removeCallbacks(holdSelectionUpdate);
        cancelSelectionLongPressCheck();
        removeCallbacks(systemWallpaperLoad);
        recycleVelocityTracker();
        targets = Collections.emptyList();
        basePoints = Collections.emptyList();
        wallpaper = null;
    }

    void playEntry() {
        requestFocus();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(scaledDuration(280));
        animator.setInterpolator(new DecelerateInterpolator(1.35f));
        animator.addUpdateListener(value -> {
            entryProgress = (Float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    void onExternalMove(float x, float y) {
        if (interactionPaused || closing) return;
        long now = SystemClock.uptimeMillis();
        if (!externalTracking) {
            externalTracking = true;
            externalLastX = x;
            externalLastY = y;
            externalLastTime = now;
        }
        float deltaX = x - externalLastX;
        float deltaY = y - externalLastY;
        long elapsed = Math.max(1L, now - externalLastTime);
        float oldPanX = panX;
        float oldPanY = panY;
        float panDeltaX = browseMode ? deltaX
                : HoneycombGeometry.counterPanDelta(deltaX, HOLD_GRID_ACCELERATION);
        float panDeltaY = browseMode ? deltaY
                : HoneycombGeometry.counterPanDelta(deltaY, HOLD_GRID_ACCELERATION);
        panX += panDeltaX / zoom;
        panY += panDeltaY / zoom;
        clampPan(true);
        float screenShiftX = (panX - oldPanX) * zoom;
        float screenShiftY = (panY - oldPanY) * zoom;
        shiftDrawnCenters(screenShiftX, screenShiftY);
        float instantVelocityX = screenShiftX * 1000f / elapsed / zoom;
        float instantVelocityY = screenShiftY * 1000f / elapsed / zoom;
        externalVelocityX = externalVelocityX * 0.68f + instantVelocityX * 0.32f;
        externalVelocityY = externalVelocityY * 0.68f + instantVelocityY * 0.32f;
        externalLastX = x;
        externalLastY = y;
        externalLastTime = now;
        pointerX = x;
        pointerY = y;
        pointerValid = !browseMode;
        if (browseMode) {
            select(-1, false);
            invalidate();
        } else {
            selectAt(x, y, true);
            invalidate();
        }
    }

    void onExternalUp(float x, float y, boolean cancelled) {
        externalTracking = false;
        pointerValid = false;
        lastHoldPanFrameMs = 0L;
        removeCallbacks(holdSelectionUpdate);
        if (browseMode) {
            select(-1, false);
            if (!cancelled && Math.hypot(externalVelocityX, externalVelocityY)
                    >= 220f * density) {
                float factor = inertiaIndex == 0 ? 0.65f : inertiaIndex == 2 ? 1f : 0.82f;
                fling(externalVelocityX * factor, externalVelocityY * factor);
            } else {
                settlePan();
            }
            externalVelocityX = 0f;
            externalVelocityY = 0f;
            return;
        }
        if (!cancelled) selectAt(x, y, true);
        if (!cancelled && selected >= 0) playConfirmation(selected);
        else playDismissal();
    }

    void onExternalCancel() {
        externalTracking = false;
        pointerValid = false;
        selectionTouchDownUptimeMs = 0L;
        selectionSettledUptimeMs = 0L;
        cancelSelectionLongPressCheck();
        lastHoldPanFrameMs = 0L;
        removeCallbacks(holdSelectionUpdate);
        externalVelocityX = 0f;
        externalVelocityY = 0f;
        select(-1, false);
        settlePan();
        invalidate();
    }

    void setInteractionPaused(boolean paused) {
        interactionPaused = paused;
        if (paused) {
            stopPhysics();
            pointerValid = false;
            lastHoldPanFrameMs = 0L;
            removeCallbacks(holdSelectionUpdate);
            select(-1, false);
        }
    }

    void playDismissal() {
        if (closing) return;
        closing = true;
        stopPhysics();
        ValueAnimator animator = ValueAnimator.ofFloat(dismissProgress, 1f);
        animator.setDuration(scaledDuration(220));
        animator.setInterpolator(new DecelerateInterpolator(1.3f));
        animator.addUpdateListener(value -> {
            dismissProgress = (Float) value.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new SimpleAnimatorListener() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (listener != null) listener.onClosed();
            }
        });
        animator.start();
    }

    private void playConfirmation(int index) {
        if (closing || index < 0 || index >= targets.size()) return;
        HoneycombRuntimeTarget launchTarget = targets.get(index);
        closing = true;
        selected = index;
        nameTarget = launchTarget;
        if (index < drawnX.length && Float.isFinite(drawnX[index])
                && Float.isFinite(drawnY[index])) {
            confirmStartX = drawnX[index];
            confirmStartY = drawnY[index];
            confirmStartScale = drawnScale[index] > 0f ? drawnScale[index] : Float.NaN;
        } else {
            confirmStartX = Float.NaN;
            confirmStartY = Float.NaN;
            confirmStartScale = Float.NaN;
        }
        if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(scaledDuration(230));
        animator.setInterpolator(new DecelerateInterpolator(1.45f));
        animator.addUpdateListener(value -> {
            confirmProgress = (Float) value.getAnimatedValue();
            invalidate();
        });
        dispatchLaunch(launchTarget);
        animator.start();
    }

    private void dispatchLaunch(HoneycombRuntimeTarget launchTarget) {
        long durationMs;
        if (selectionTouchDownUptimeMs > 0L) {
            durationMs = SystemClock.uptimeMillis() - selectionTouchDownUptimeMs;
        } else if (selectionSettledUptimeMs > 0L) {
            durationMs = SystemClock.uptimeMillis() - selectionSettledUptimeMs;
        } else {
            durationMs = 0L;
        }
        selectionTouchDownUptimeMs = 0L;
        selectionSettledUptimeMs = 0L;
        cancelSelectionLongPressCheck();
        Listener callback = listener;
        if (callback != null) callback.onLaunch(launchTarget, durationMs);
    }

    private void scheduleSelectionLongPressCheck() {
        cancelSelectionLongPressCheck();
        if (!launchLongPressTrackingEnabled || launchLongPressDurationMs <= 0 || selected < 0) return;
        postDelayed(selectionLongPressRunnable, launchLongPressDurationMs);
    }

    private void cancelSelectionLongPressCheck() {
        removeCallbacks(selectionLongPressRunnable);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float visible = (1f - dismissProgress) * (1f - confirmProgress * 0.18f);
        if (backgroundStyle == HoneycombDisplayConfig.BACKGROUND_BLUR) {
            int blurPx = Math.round(blurDp * density);
            int clampedBlurPx = Math.min(120, Math.max(1, blurPx));
            blurBounds.set(0, statusBarHeight, getWidth(), getHeight());
            frostedGlassDrawable.draw(canvas, blurBounds, 0f, clampedBlurPx, Color.TRANSPARENT, visible);
        }
        boolean drawWallpaperBitmap = wallpaper != null && !wallpaper.isRecycled()
                && (backgroundStyle == HoneycombDisplayConfig.BACKGROUND_WALLPAPER_BLUR
                || (backgroundStyle == HoneycombDisplayConfig.BACKGROUND_BLUR && !usesNativeWindowBlur));
        if (drawWallpaperBitmap) {
            wallpaperPaint.setAlpha(Math.round(255f * visible));
            wallpaperBounds.set(0, statusBarHeight, getWidth(), getHeight());
            canvas.drawBitmap(wallpaper, null, wallpaperBounds, wallpaperPaint);
        }
        int maskAlpha = Math.round(resolvedMaskAlpha(visible));
        if (maskAlpha > 0) {
            backgroundPaint.setColor(Color.BLACK);
            backgroundPaint.setAlpha(maskAlpha);
            canvas.drawRect(0, statusBarHeight, getWidth(), getHeight(), backgroundPaint);
        }
        updateLensFocus();
        updateVisualCenters();
        float centerX = resolvedCenterX();
        float centerY = resolvedCenterY();
        float effectRadius = viewportRadius();
        float alphaStart = effectRadius * 0.70f;
        float originX = corner == HoneycombCorner.RIGHT ? getWidth() : 0f;
        float originY = getHeight();
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 0; index < targets.size(); index++) {
                if (pass == 0 && index == selected) continue;
                if (pass == 1 && index != selected) continue;
            float finalCenterX = visualX[index];
            float finalCenterY = visualY[index];
            float delay = entryDelay(index);
            float localEntry = HoneycombGeometry.clamp((entryProgress - delay)
                    / Math.max(0.01f, 1f - delay), 0f, 1f);
            float spring = spring(localEntry);
            float x = originX + (finalCenterX - originX) * spring;
            float y = originY + (finalCenterY - originY) * spring;
            float distance = (float) Math.hypot(x - centerX, y - centerY);
            float scale = HoneycombGeometry.smoothScale(distance, effectRadius,
                    centerScale, edgeScale);
            float alpha = localEntry * visible;
            float untrimmedRadius = iconSize * scale * 0.5f;
            float edgeVisibility = HoneycombGeometry.edgeVisibility(distance,
                    effectRadius, untrimmedRadius);
            float inset = HoneycombGeometry.edgeInset(distance, effectRadius,
                    untrimmedRadius);
            if (distance > 1f && inset > 0f) {
                x -= (x - centerX) / distance * inset;
                y -= (y - centerY) / distance * inset;
            }
            scale *= HoneycombGeometry.edgeScale(distance, effectRadius, untrimmedRadius);
            if (edgeVisibility <= 0f) {
                alpha = 0f;
            }
            if (alpha > 0.08f && scale > 0.05f) {
                stableHitX[index] = x;
                stableHitY[index] = y;
                stableHitRadius[index] = Math.max(8f * density,
                        iconSize * scale * 0.54f);
            } else {
                stableHitX[index] = Float.NaN;
                stableHitY[index] = Float.NaN;
                stableHitRadius[index] = 0f;
            }
            if (!browseMode && (pointerValid || Float.isFinite(confirmStartX))
                    && Float.isFinite(lensX)
                    && Float.isFinite(lensY)) {
                float fromLensX = x - lensX;
                float fromLensY = y - lensY;
                float lensDistance = (float) Math.hypot(fromLensX, fromLensY);
                scale *= HoneycombGeometry.fisheyeScale(lensDistance,
                        effectRadius * 1.25f, 0.66f, 1.24f);
                if (lensDistance > 1f) {
                    float bulgeOffset = HoneycombGeometry.surfaceBulgeOffset(
                            lensDistance, effectRadius * 1.60f, 10f * density);
                    x += fromLensX / lensDistance * bulgeOffset;
                    y += fromLensY / lensDistance * bulgeOffset;
                }
            }
            if (index == selected) scale *= 1f + (selectionScale - 1f) * selectionProgress;
            if (confirmProgress > 0f) {
                if (index == selected) {
                    float startX = Float.isFinite(confirmStartX) ? confirmStartX : x;
                    float startY = Float.isFinite(confirmStartY) ? confirmStartY : y;
                    float startScale = Float.isFinite(confirmStartScale)
                            ? confirmStartScale : scale;
                    x = startX + (centerX - startX) * confirmProgress;
                    y = startY + (centerY - startY) * confirmProgress;
                    scale = startScale * (1f + 0.65f * confirmProgress);
                } else {
                    alpha *= 1f - confirmProgress;
                    scale *= 1f - 0.14f * confirmProgress;
                }
            }
            if (dismissProgress > 0f) {
                float collapse = dismissProgress * dismissProgress * (3f - 2f * dismissProgress);
                x += (originX - x) * collapse;
                y += (originY - y) * collapse;
                scale *= 1f - 0.75f * collapse;
                alpha *= 1f - collapse;
            }
            if (alpha > 0.08f && scale > 0.05f) {
                drawnX[index] = x;
                drawnY[index] = y;
                drawnRadius[index] = Math.max(8f * density, iconSize * scale * 0.58f);
                drawnScale[index] = scale;
            } else {
                drawnX[index] = Float.NaN;
                drawnY[index] = Float.NaN;
                drawnRadius[index] = 0f;
                drawnScale[index] = 0f;
            }
            drawIcon(canvas, targets.get(index), x, y, iconSize * scale, alpha,
                    index == selected);
            }
        }
        drawSelectedName(canvas, visible);
    }

    private void updateVisualCenters() {
        float centerX = resolvedCenterX();
        float centerY = resolvedCenterY();
        int count = Math.min(basePoints.size(), visualX.length);
        for (int index = 0; index < count; index++) {
            HoneycombGeometry.Point point = basePoints.get(index);
            visualX[index] = centerX + (point.x + panX) * zoom;
            visualY[index] = centerY + (point.y + panY) * zoom;
        }
    }

    private void updateLensFocus() {
        if (browseMode || !pointerValid) {
            lastHoldPanFrameMs = 0L;
            return;
        }
        if (!Float.isFinite(lensX) || !Float.isFinite(lensY)) {
            lensX = pointerX;
            lensY = pointerY;
            return;
        }
        float deltaX = pointerX - lensX;
        float deltaY = pointerY - lensY;
        lensX += deltaX * 0.58f;
        lensY += deltaY * 0.58f;
        boolean continueAnimation = Math.hypot(deltaX, deltaY) > 0.45f;
        if (stepHoldEdgePan()) {
            removeCallbacks(holdSelectionUpdate);
            post(holdSelectionUpdate);
            continueAnimation = true;
        }
        if (continueAnimation) postInvalidateOnAnimation();
    }

    private boolean stepHoldEdgePan() {
        float centerX = resolvedCenterX();
        float centerY = resolvedCenterY();
        float directionX = pointerX - centerX;
        float directionY = pointerY - centerY;
        float distance = (float) Math.hypot(directionX, directionY);
        float strength = HoneycombGeometry.edgePanStrength(distance, viewportRadius(), 0.78f);
        if (strength <= 0f || distance <= 1f) {
            lastHoldPanFrameMs = 0L;
            return false;
        }
        long now = SystemClock.uptimeMillis();
        if (lastHoldPanFrameMs == 0L) {
            lastHoldPanFrameMs = now;
            return true;
        }
        float elapsedSeconds = Math.min(0.032f,
                Math.max(0.001f, (now - lastHoldPanFrameMs) / 1000f));
        lastHoldPanFrameMs = now;
        float oldPanX = panX;
        float oldPanY = panY;
        float speed = 520f * density * strength;
        panX -= directionX / distance * speed * elapsedSeconds / zoom;
        panY -= directionY / distance * speed * elapsedSeconds / zoom;
        clampPan(true);
        float shiftX = (panX - oldPanX) * zoom;
        float shiftY = (panY - oldPanY) * zoom;
        shiftDrawnCenters(shiftX, shiftY);
        return Math.hypot(shiftX, shiftY) > 0.05f;
    }

    private float resolvedCenterX() {
        float fallback = getWidth() * fixedXPercent / 100f;
        float radius = viewportRadius();
        if (Float.isNaN(anchorX)) {
            return HoneycombGeometry.clamp(fallback, radius,
                    Math.max(radius, getWidth() - radius));
        }
        float margin = Math.min(radius, Math.max(iconSize * 0.75f, 24f * density));
        return HoneycombGeometry.clamp(anchorX, margin,
                Math.max(margin, getWidth() - margin));
    }

    private float resolvedCenterY() {
        float radius = viewportRadius();
        float fallback = getHeight() * fixedYPercent / 100f;
        if (Float.isNaN(anchorY)) {
            return HoneycombGeometry.clamp(fallback, statusBarHeight + radius,
                    Math.max(statusBarHeight + radius, getHeight() - radius));
        }
        float margin = Math.min(radius, Math.max(iconSize * 0.75f, 24f * density));
        return HoneycombGeometry.clamp(anchorY, statusBarHeight + margin,
                Math.max(statusBarHeight + margin, getHeight() - margin));
    }

    private void drawIcon(Canvas canvas, HoneycombRuntimeTarget target, float x, float y, float diameter,
                          float alpha, boolean active) {
        if (diameter <= 1f || alpha <= 0f) return;
        Drawable icon = target == null ? null : target.icon;
        float radius = diameter * 0.5f;
        if (icon == null) {
            iconPlatePaint.setColor(Color.argb(140, 48, 48, 52));
            iconPlatePaint.setAlpha(Math.round(255 * alpha));
            canvas.drawCircle(x, y, radius, iconPlatePaint);

            namePillPaint.setColor(Color.argb(90, 255, 255, 255));
            namePillPaint.setStyle(Paint.Style.STROKE);
            namePillPaint.setStrokeWidth(Math.max(1f, radius * 0.08f));
            namePillPaint.setAlpha(Math.round(90 * alpha));
            canvas.drawCircle(x, y, radius - namePillPaint.getStrokeWidth() / 2f, namePillPaint);
            namePillPaint.setStyle(Paint.Style.FILL);

            String label = target != null && target.label != null ? target.label.trim() : "";
            String initial = label.isEmpty() ? "•" : label.substring(0, Math.min(label.length(), 1)).toUpperCase(java.util.Locale.ROOT);
            placeholderPaint.setColor(Color.WHITE);
            placeholderPaint.setTextSize(radius * 0.88f);
            placeholderPaint.setTextAlign(Paint.Align.CENTER);
            placeholderPaint.setFakeBoldText(true);
            placeholderPaint.setAlpha(Math.round(220 * alpha));
            Paint.FontMetrics metrics = placeholderPaint.getFontMetrics();
            float baseline = y - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(initial, x, baseline, placeholderPaint);
            return;
        }
        int save = canvas.save();
        if (forceCircularIcons) {
            iconPlatePaint.setColor(Color.WHITE);
            iconPlatePaint.setAlpha(Math.round(255 * alpha));
            canvas.drawCircle(x, y, radius, iconPlatePaint);
            clipPath.reset();
            clipPath.addCircle(x, y, radius, Path.Direction.CW);
            canvas.clipPath(clipPath);
        }
        icon.copyBounds(iconOldBounds);
        int intrinsicWidth = Math.max(1, icon.getIntrinsicWidth());
        int intrinsicHeight = Math.max(1, icon.getIntrinsicHeight());
        float targetSize = forceCircularIcons ? diameter : diameter * 0.94f;
        float scale = forceCircularIcons
                ? Math.max(targetSize / intrinsicWidth, targetSize / intrinsicHeight)
                : Math.min(targetSize / intrinsicWidth, targetSize / intrinsicHeight);
        int drawWidth = Math.round(intrinsicWidth * scale);
        int drawHeight = Math.round(intrinsicHeight * scale);
        icon.setBounds(Math.round(x - drawWidth / 2f), Math.round(y - drawHeight / 2f),
                Math.round(x + drawWidth / 2f), Math.round(y + drawHeight / 2f));
        icon.setAlpha(Math.round(255 * alpha));
        icon.draw(canvas);
        icon.setAlpha(255);
        icon.setBounds(iconOldBounds);
        canvas.restoreToCount(save);
        if (target.isShortcut()) {
            ShortcutBadgeRenderer.draw(canvas, x, y, diameter, alpha, density);
        } else if (target.isShellCommandBadge()) {
            ShellCommandBadgeRenderer.draw(canvas, x, y, diameter, alpha, density);
        } else if (target.isMacroBadge()) {
            MacroBadgeRenderer.draw(canvas, x, y, diameter, alpha, density);
        }
        // 分身黄点：画在圆形裁剪之外，否则方角上的点会被裁掉
        if (target.isClone) {
            com.slideindex.app.util.CloneBadge.INSTANCE.draw(
                    canvas, x, y, diameter, alpha, forceCircularIcons);
        }
    }

    private void drawHintIcon(Canvas canvas, HoneycombRuntimeTarget target, float left, float centerY,
            float size, float alpha) {
        if (target == null || size <= 1f || alpha <= 0f) return;
        Drawable icon = target.icon;
        float top = centerY - size / 2f;
        if (icon == null) {
            float cornerRadius = 6f * density;
            iconPlatePaint.setColor(Color.argb(140, 48, 48, 52));
            iconPlatePaint.setAlpha(Math.round(255 * alpha));
            canvas.drawRoundRect(left, top, left + size, top + size, cornerRadius, cornerRadius,
                    iconPlatePaint);

            String label = target != null && target.label != null ? target.label.trim() : "";
            String initial = label.isEmpty() ? "•" : label.substring(0, Math.min(label.length(), 1)).toUpperCase(java.util.Locale.ROOT);
            placeholderPaint.setColor(Color.WHITE);
            placeholderPaint.setTextSize(size * 0.5f);
            placeholderPaint.setTextAlign(Paint.Align.CENTER);
            placeholderPaint.setFakeBoldText(true);
            placeholderPaint.setAlpha(Math.round(220 * alpha));
            Paint.FontMetrics metrics = placeholderPaint.getFontMetrics();
            float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(initial, left + size / 2f, baseline, placeholderPaint);
            return;
        }
        int save = canvas.save();
        icon.copyBounds(iconOldBounds);
        int intrinsicWidth = Math.max(1, icon.getIntrinsicWidth());
        int intrinsicHeight = Math.max(1, icon.getIntrinsicHeight());
        float scale = Math.min(size / intrinsicWidth, size / intrinsicHeight);
        int drawWidth = Math.round(intrinsicWidth * scale);
        int drawHeight = Math.round(intrinsicHeight * scale);
        float drawLeft = left + (size - drawWidth) / 2f;
        float drawTop = centerY - drawHeight / 2f;
        icon.setBounds(Math.round(drawLeft), Math.round(drawTop),
                Math.round(drawLeft + drawWidth), Math.round(drawTop + drawHeight));
        icon.setAlpha(Math.round(255 * alpha));
        icon.draw(canvas);
        icon.setAlpha(255);
        icon.setBounds(iconOldBounds);
        canvas.restoreToCount(save);
        if (target.isShortcut()) {
            ShortcutBadgeRenderer.draw(canvas, left + size / 2f, centerY, size, alpha, density);
        } else if (target.isShellCommandBadge()) {
            ShellCommandBadgeRenderer.draw(canvas, left + size / 2f, centerY, size, alpha, density);
        } else if (target.isMacroBadge()) {
            MacroBadgeRenderer.draw(canvas, left + size / 2f, centerY, size, alpha, density);
        }
        if (target.isClone) {
            com.slideindex.app.util.CloneBadge.INSTANCE.draw(
                    canvas, left + size / 2f, centerY, size, alpha, forceCircularIcons);
        }
    }

    private void drawSelectedName(Canvas canvas, float visible) {
        if (browseMode || !showSelectedName || nameTarget == null
                || selectionProgress <= 0.01f) return;
        float paddingX = SelectedHintMetrics.paddingXPx(density);
        float gap = SelectedHintMetrics.gapPx(density);
        float iconSize = hintIconSizeDp * density;
        float boxHeight = SelectedHintMetrics.boxHeightPx(hintIconSizeDp, density);
        float maxTextWidth = Math.min(180f * density, getWidth() * 0.48f);
        if (fittedNameTarget != nameTarget || fittedNameMaxWidth != maxTextWidth
                || fittedHintIconSizeDp != hintIconSizeDp) {
            fittedNameTarget = nameTarget;
            fittedNameMaxWidth = maxTextWidth;
            fittedHintIconSizeDp = hintIconSizeDp;
            namePaint.setTextSize(SelectedHintMetrics.textSizePx(hintIconSizeDp, density));
            fittedName = TextUtils.ellipsize(nameTarget.label, namePaint,
                    maxTextWidth, TextUtils.TruncateAt.END);
            fittedNameWidth = namePaint.measureText(fittedName, 0, fittedName.length());
        }
        float boxWidth = paddingX * 2f + iconSize + gap + fittedNameWidth;
        float margin = 8f * density;
        float centerX = (getWidth() * 0.5f);
        centerX = Math.max(boxWidth * 0.5f + margin,
                Math.min(getWidth() - boxWidth * 0.5f - margin, centerX));
        float centerY = Math.max(statusBarHeight + boxHeight * 0.5f + 8f * density,
                resolvedCenterY() - viewportRadius() - SelectedHintMetrics.honeycombHintAboveDiscPx(density));
        float alpha = HoneycombGeometry.clamp(selectionProgress * visible, 0f, 1f);
        namePillPaint.setColor(Color.WHITE);
        namePillPaint.setAlpha(Math.round(221f * alpha));
        namePaint.setColor(0xDE000000);
        namePaint.setAlpha(Math.round(255f * alpha));
        float left = centerX - boxWidth / 2f;
        canvas.drawRoundRect(left, centerY - boxHeight / 2f, left + boxWidth,
                centerY + boxHeight / 2f, boxHeight / 2f, boxHeight / 2f, namePillPaint);
        float iconLeft = left + paddingX;
        drawHintIcon(canvas, nameTarget, iconLeft, centerY, iconSize, alpha);
        Paint.FontMetrics metrics = namePaint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(fittedName, 0, fittedName.length(), iconLeft + iconSize + gap, baseline,
                namePaint);
    }

    @Override public boolean performClick() {
        return super.performClick();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (interactionPaused || closing) return true;
        if (!browseMode) {
            int holdAction = event.getActionMasked();
            if (holdAction == MotionEvent.ACTION_DOWN) {
                selectionTouchDownUptimeMs = SystemClock.uptimeMillis();
                onExternalMove(event.getX(), event.getY());
            } else if (holdAction == MotionEvent.ACTION_MOVE) {
            } else if (holdAction == MotionEvent.ACTION_UP) {
                onExternalUp(event.getX(), event.getY(), false);
                performClick();
            } else if (holdAction == MotionEvent.ACTION_CANCEL) {
                onExternalCancel();
            }
            return true;
        }
        scaleDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            stopPhysics();
            selectionTouchDownUptimeMs = SystemClock.uptimeMillis();
            downX = lastX = event.getX();
            downY = lastY = event.getY();
            pointerX = downX;
            pointerY = downY;
            pointerValid = true;
            dragging = false;
            scaledDuringGesture = false;
            recycleVelocityTracker();
            velocityTracker = VelocityTracker.obtain();
            velocityTracker.addMovement(event);
            selectAt(downX, downY, false);
            return true;
        }
        if (velocityTracker != null) velocityTracker.addMovement(event);
        if (action == MotionEvent.ACTION_MOVE) {
            if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                float x = event.getX();
                float y = event.getY();
                if (!dragging && Math.hypot(x - downX, y - downY) > touchSlop) {
                    dragging = true;
                    pointerValid = false;
                    select(-1, false);
                }
                if (dragging) {
                    panX += (x - lastX) / zoom;
                    panY += (y - lastY) / zoom;
                    clampPan(true);
                    invalidate();
                }
                lastX = x;
                lastY = y;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (!dragging && !scaledDuringGesture && !scaleDetector.isInProgress()) {
                pointerX = event.getX();
                pointerY = event.getY();
                selectAt(event.getX(), event.getY(), false);
                if (selected >= 0) playConfirmation(selected);
                else if (emptyTapClose) playDismissal();
            } else if (dragging && !scaledDuringGesture && velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000, 5000f * density);
                float factor = inertiaIndex == 0 ? 0.65f : inertiaIndex == 2 ? 1f : 0.82f;
                float vx = velocityTracker.getXVelocity() * factor / zoom;
                float vy = velocityTracker.getYVelocity() * factor / zoom;
                if (Math.hypot(vx, vy) >= 220f * density) fling(vx, vy);
                else settlePan();
            } else settlePan();
            pointerValid = false;
            recycleVelocityTracker();
            performClick();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            pointerValid = false;
            recycleVelocityTracker();
            select(-1, false);
            settlePan();
            return true;
        }
        return true;
    }

    private void fling(float velocityX, float velocityY) {
        startPhysics(velocityX, velocityY);
    }

    private void startPhysics(float velocityX, float velocityY) {
        physicsVelocityX = velocityX;
        physicsVelocityY = velocityY;
        if (physicsRunning) return;
        physicsRunning = true;
        lastPhysicsFrameNanos = 0L;
        Choreographer.getInstance().postFrameCallback(physicsFrame);
    }

    private void settlePan() {
        updatePanBounds();
        float targetX = HoneycombGeometry.clamp(panX, panMinimumX, panMaximumX);
        float targetY = HoneycombGeometry.clamp(panY, panMinimumY, panMaximumY);
        if (Math.abs(targetX - panX) < 0.5f && Math.abs(targetY - panY) < 0.5f) return;
        startPhysics(0f, 0f);
    }

    private void stepPhysics(long frameTimeNanos) {
        if (!physicsRunning || closing || interactionPaused) return;
        if (lastPhysicsFrameNanos == 0L) {
            lastPhysicsFrameNanos = frameTimeNanos;
            Choreographer.getInstance().postFrameCallback(physicsFrame);
            return;
        }
        float dt = Math.min(0.032f, Math.max(0.001f,
                (frameTimeNanos - lastPhysicsFrameNanos) / 1_000_000_000f));
        lastPhysicsFrameNanos = frameTimeNanos;
        updatePanBounds();
        float targetX = HoneycombGeometry.clamp(panX, panMinimumX, panMaximumX);
        float targetY = HoneycombGeometry.clamp(panY, panMinimumY, panMaximumY);
        boolean springing = targetX != panX || targetY != panY;
        float spring = inertiaIndex == 0 ? 105f : inertiaIndex == 2 ? 72f : 86f;
        float damping = inertiaIndex == 0 ? 17f : inertiaIndex == 2 ? 11f : 14f;
        if (springing) {
            physicsVelocityX += ((targetX - panX) * spring
                    - physicsVelocityX * damping) * dt;
            physicsVelocityY += ((targetY - panY) * spring
                    - physicsVelocityY * damping) * dt;
        } else {
            float friction = inertiaIndex == 0 ? 8.8f : inertiaIndex == 2 ? 4.6f : 6.4f;
            float decay = (float) Math.exp(-friction * dt);
            physicsVelocityX *= decay;
            physicsVelocityY *= decay;
        }
        panX += physicsVelocityX * dt;
        panY += physicsVelocityY * dt;
        invalidate();
        targetX = HoneycombGeometry.clamp(panX, panMinimumX, panMaximumX);
        targetY = HoneycombGeometry.clamp(panY, panMinimumY, panMaximumY);
        if (Math.hypot(physicsVelocityX, physicsVelocityY) < 6f
                && Math.hypot(targetX - panX, targetY - panY) < 0.7f) {
            panX = targetX;
            panY = targetY;
            stopPhysics();
            invalidate();
        } else {
            Choreographer.getInstance().postFrameCallback(physicsFrame);
        }
    }

    private void stopPhysics() {
        if (physicsRunning) Choreographer.getInstance().removeFrameCallback(physicsFrame);
        physicsRunning = false;
        physicsVelocityX = 0f;
        physicsVelocityY = 0f;
        lastPhysicsFrameNanos = 0L;
    }

    private void clampPan(boolean resisted) {
        updatePanBounds();
        if (resisted) {
            panX = HoneycombGeometry.resisted(panX, panMinimumX, panMaximumX, 0.30f);
            panY = HoneycombGeometry.resisted(panY, panMinimumY, panMaximumY, 0.30f);
        } else {
            panX = HoneycombGeometry.clamp(panX, panMinimumX, panMaximumX);
            panY = HoneycombGeometry.clamp(panY, panMinimumY, panMaximumY);
        }
    }

    private void updatePanBounds() {
        float radius = viewportRadius();
        float allowanceX = Math.max(radius * 0.55f,
                Math.max(0f, baseMaximumX - radius * 0.55f / zoom) + pitch * 0.70f);
        float allowanceY = Math.max(radius * 0.55f,
                Math.max(0f, baseMaximumY - radius * 0.55f / zoom) + pitch * 0.70f);
        panMinimumX = -allowanceX;
        panMaximumX = allowanceX;
        panMinimumY = -allowanceY;
        panMaximumY = allowanceY;
    }

    private void selectAt(float x, float y, boolean haptic) {
        updateVisualCenters();
        int next = HoneycombGeometry.hitVisible(stableHitX, stableHitY, stableHitRadius,
                targets.size(), x, y);
        if (next < 0 && entryProgress < 0.12f) {
            next = hitVisualCenters(x, y);
        }
        if (next < 0 && selected >= 0 && selected < stableHitX.length
                && Float.isFinite(stableHitX[selected])
                && Float.isFinite(stableHitY[selected])) {
            float distance = (float) Math.hypot(x - stableHitX[selected],
                    y - stableHitY[selected]);
            if (distance <= stableHitRadius[selected] + 8f * density) next = selected;
        }
        select(next, haptic);
    }

    private int hitVisualCenters(float x, float y) {
        int best = -1;
        float bestNormalized = Float.MAX_VALUE;
        float centerX = resolvedCenterX();
        float centerY = resolvedCenterY();
        float radius = viewportRadius();
        for (int index = 0; index < targets.size(); index++) {
            float distance = (float) Math.hypot(visualX[index] - centerX,
                    visualY[index] - centerY);
            float scale = HoneycombGeometry.smoothScale(distance, radius,
                    centerScale, edgeScale);
            float hitRadius = Math.max(8f * density, iconSize * scale * 0.58f);
            float visibility = HoneycombGeometry.edgeVisibility(distance, radius,
                    iconSize * scale * 0.5f);
            if (visibility <= 0.08f) continue;
            float dx = x - visualX[index];
            float dy = y - visualY[index];
            float normalized = (dx * dx + dy * dy) / (hitRadius * hitRadius);
            if (normalized <= 1f && normalized < bestNormalized) {
                bestNormalized = normalized;
                best = index;
            }
        }
        return best;
    }

    private float resolvedMaskAlpha(float visible) {
        int clampedDim = Math.max(HoneycombDisplayConfig.MIN_DIM_PERCENT,
                Math.min(HoneycombDisplayConfig.MAX_DIM_PERCENT, dimPercent));
        return 255f * clampedDim / 100f * visible;
    }

    private float viewportRadius() {
        float screenMinimum = Math.min(getWidth(), getHeight());
        if (screenMinimum <= 0f) {
            screenMinimum = Math.min(getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
        }
        return screenMinimum * HoneycombGeometry.clamp(discSizePercent,
                HoneycombDisplayConfig.MIN_DISC_SIZE_PERCENT,
                HoneycombDisplayConfig.MAX_DISC_SIZE_PERCENT)
                / 200f;
    }

    private void shiftDrawnCenters(float deltaX, float deltaY) {
        for (int index = 0; index < drawnX.length; index++) {
            if (Float.isFinite(drawnX[index]) && Float.isFinite(drawnY[index])) {
                drawnX[index] += deltaX;
                drawnY[index] += deltaY;
            }
            if (Float.isFinite(stableHitX[index]) && Float.isFinite(stableHitY[index])) {
                stableHitX[index] += deltaX;
                stableHitY[index] += deltaY;
            }
        }
    }

    private void select(int next, boolean haptic) {
        if (selected == next) return;
        boolean changed = selected != next;
        if (next >= 0 && next < targets.size()) {
            nameTarget = targets.get(next);
        } else if (selected >= 0 && selected < targets.size()) {
            nameTarget = targets.get(selected);
        }
        selected = next;
        if (changed) {
            selectionSettledUptimeMs = next >= 0 ? SystemClock.uptimeMillis() : 0L;
            if (next >= 0) {
                scheduleSelectionLongPressCheck();
            } else {
                cancelSelectionLongPressCheck();
            }
        }
        selectionAnimator.cancel();
        float start = next >= 0 && changed ? 0.62f : selectionProgress;
        float end = next < 0 ? 0f : 1f;
        selectionAnimator.setFloatValues(start, end);
        selectionAnimator.setDuration(next < 0 ? 110L : 90L);
        selectionAnimator.start();
        long now = android.os.SystemClock.uptimeMillis();
        if (haptic && hapticEnabled && changed && next >= 0 && next != lastHapticIndex
                && now - lastHapticAt >= 55L) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            lastHapticIndex = next;
            lastHapticAt = now;
        }
    }

    @Override public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP) {
            playDismissal();
            return true;
        }
        return super.dispatchKeyEventPreIme(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP) {
            playDismissal();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private float entryDelay(int index) {
        if (basePoints.isEmpty()) return 0f;
        HoneycombGeometry.Point point = basePoints.get(index);
        float distance = (float) Math.hypot(point.x, point.y);
        float maximum = pitch * 4.5f;
        return HoneycombGeometry.clamp(distance / maximum, 0f, 1f) * 0.20f;
    }

    private static float spring(float progress) {
        if (progress <= 0f) return 0f;
        if (progress >= 1f) return 1f;
        return 1f - (float) (Math.cos(progress * Math.PI * 2.15)
                * Math.exp(-6.2 * progress));
    }

    private long scaledDuration(long base) {
        float[] multipliers = {0.72f, 0.86f, 1f, 1.16f, 1.32f};
        return Math.max(1L, Math.round(base * multipliers[Math.max(0,
                Math.min(multipliers.length - 1, speedIndex))]));
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) velocityTracker.recycle();
        velocityTracker = null;
    }

    private abstract static class SimpleAnimatorListener
            implements android.animation.Animator.AnimatorListener {
        @Override public void onAnimationStart(android.animation.Animator animation) { }
        @Override public void onAnimationCancel(android.animation.Animator animation) { }
        @Override public void onAnimationRepeat(android.animation.Animator animation) { }
    }
}
