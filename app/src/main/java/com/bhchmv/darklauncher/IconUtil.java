package com.bhchmv.darklauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

final class IconUtil {
    private IconUtil() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Drawable selectable(Context c) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return c.getDrawable(tv.resourceId);
    }

    /** iPhone-style rounded-square icon. */
    static Bitmap rounded(AppEntry e, int size) {
        if (e.roundIcon != null && e.roundIcon.getWidth() == size) return e.roundIcon;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Path clip = new Path();
        float r = size * 0.225f;
        clip.addRoundRect(new RectF(0, 0, size, size), r, r, Path.Direction.CW);
        cv.clipPath(clip);

        Drawable d = e.icon;
        if (e.special || d == null) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setShader(new LinearGradient(0, 0, 0, size, 0xFF8E8E93, 0xFF48484A, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, size, size, bg);
            drawGear(cv, size / 2f, size / 2f, size * 0.32f, Color.WHITE, 0xFF5E5E62);
        } else if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            int ext = Math.round(size * 0.25f);
            Drawable back = a.getBackground();
            Drawable fore = a.getForeground();
            if (back != null) {
                back.setBounds(-ext, -ext, size + ext, size + ext);
                back.draw(cv);
            } else {
                cv.drawColor(0xFF2C2C2E);
            }
            if (fore != null) {
                fore.setBounds(-ext, -ext, size + ext, size + ext);
                fore.draw(cv);
            }
        } else {
            cv.drawColor(0xFF2C2C2E);
            int in = Math.round(size * 0.14f);
            d.setBounds(in, in, size - in, size - in);
            d.draw(cv);
        }
        e.roundIcon = b;
        return b;
    }

    /** Windows-style icon: just the app's symbol, no background, for drawing on a colored tile. */
    static Bitmap glyph(AppEntry e, int size) {
        if (e.glyphIcon != null && e.glyphIcon.getWidth() == size) return e.glyphIcon;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Drawable d = e.icon;
        if (e.special || d == null) {
            drawGear(cv, size / 2f, size / 2f, size * 0.4f, Color.WHITE, Color.TRANSPARENT);
        } else if (d instanceof AdaptiveIconDrawable && ((AdaptiveIconDrawable) d).getForeground() != null) {
            Drawable fore = ((AdaptiveIconDrawable) d).getForeground();
            int ext = Math.round(size * 0.25f);
            fore.setBounds(-ext, -ext, size + ext, size + ext);
            fore.draw(cv);
            if (coverage(b) < 0.04f) {
                // Symbol is in the background layer; use the full icon instead.
                e.glyphIcon = rounded(e, size);
                return e.glyphIcon;
            }
        } else {
            d.setBounds(0, 0, size, size);
            d.draw(cv);
        }
        e.glyphIcon = b;
        return b;
    }

    private static float coverage(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int seen = 0, solid = 0;
        for (int i = 0; i < px.length; i += 3) {
            seen++;
            if ((px[i] >>> 24) > 60) solid++;
        }
        return seen == 0 ? 0 : solid / (float) seen;
    }

    private static void drawGear(Canvas cv, float cx, float cy, float r, int color, int holeColor) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        for (int k = 0; k < 8; k++) {
            cv.save();
            cv.rotate(k * 45f, cx, cy);
            cv.drawRoundRect(new RectF(cx - r * 0.2f, cy - r, cx + r * 0.2f, cy - r * 0.5f), r * 0.06f, r * 0.06f, p);
            cv.restore();
        }
        cv.drawCircle(cx, cy, r * 0.72f, p);
        if (holeColor == Color.TRANSPARENT) {
            p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        } else {
            p.setColor(holeColor);
        }
        cv.drawCircle(cx, cy, r * 0.3f, p);
    }
}
