package com.slideindex.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

public final class ShortcutBadgeRenderer {
    private static final Paint BORDER = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BACKGROUND = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint GLYPH = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path LIGHTNING = new Path();

    static {
        BORDER.setColor(0xffffffff);
        BACKGROUND.setColor(0xff4976f2);
        GLYPH.setColor(0xffffffff);
        GLYPH.setStyle(Paint.Style.FILL);
    }

    private ShortcutBadgeRenderer() { }

    public static void draw(Canvas canvas, float iconX, float iconY, float iconDiameter,
                     float alpha, float density) {
        if (alpha <= 0f || iconDiameter <= 1f) return;
        float badgeDiameter = Math.max(9f * density, iconDiameter * 0.27f);
        float radius = badgeDiameter / 2f;
        float centerX = iconX + iconDiameter * 0.34f;
        float centerY = iconY + iconDiameter * 0.34f;
        int resolvedAlpha = Math.max(0, Math.min(255, Math.round(255f * alpha)));
        BORDER.setAlpha(resolvedAlpha);
        BACKGROUND.setAlpha(resolvedAlpha);
        GLYPH.setAlpha(resolvedAlpha);
        canvas.drawCircle(centerX, centerY, radius + 1.5f * density, BORDER);
        canvas.drawCircle(centerX, centerY, radius, BACKGROUND);

        LIGHTNING.reset();
        LIGHTNING.moveTo(centerX + radius * 0.02f, centerY - radius * 0.62f);
        LIGHTNING.lineTo(centerX - radius * 0.45f, centerY + radius * 0.05f);
        LIGHTNING.lineTo(centerX - radius * 0.10f, centerY + radius * 0.05f);
        LIGHTNING.lineTo(centerX - radius * 0.27f, centerY + radius * 0.62f);
        LIGHTNING.lineTo(centerX + radius * 0.47f, centerY - radius * 0.16f);
        LIGHTNING.lineTo(centerX + radius * 0.12f, centerY - radius * 0.16f);
        LIGHTNING.close();
        canvas.drawPath(LIGHTNING, GLYPH);
    }
}
