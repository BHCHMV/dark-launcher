package com.bhchmv.darklauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import java.util.List;

final class IconUtil {
    private static final String PREFS = "darkos";
    private static final String KEY_GREEN = "green_icons";

    private IconUtil() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Drawable selectable(Context c) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return c.getDrawable(tv.resourceId);
    }

    static boolean greenIcons(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_GREEN, false);
    }

    static void setGreenIcons(Context c, boolean green, List<AppEntry> apps) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_GREEN, green).apply();
        for (AppEntry e : apps) {
            e.smallIcon = null;
            e.bigIcon = null;
        }
    }

    static Bitmap small(Context c, AppEntry e) {
        int size = dp(c, 24);
        if (e.smallIcon == null || e.smallIcon.getWidth() != size) e.smallIcon = render(e.icon, size, greenIcons(c));
        return e.smallIcon;
    }

    static Bitmap big(Context c, AppEntry e) {
        int size = dp(c, 64);
        if (e.bigIcon == null || e.bigIcon.getWidth() != size) e.bigIcon = render(e.icon, size, greenIcons(c));
        return e.bigIcon;
    }

    static Bitmap render(Drawable d, int size, boolean green) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        if (d == null) return b;
        Canvas cv = new Canvas(b);
        synchronized (d) {
            d.setBounds(0, 0, size, size);
            d.draw(cv);
        }
        if (!green) return b;
        // Turn the icon into green "phosphor" shades.
        Bitmap g = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                0.06f, 0.12f, 0.02f, 0, 0,
                0.33f, 0.65f, 0.12f, 0, 40,
                0.10f, 0.20f, 0.04f, 0, 8,
                0, 0, 0, 1, 0,
        })));
        new Canvas(g).drawBitmap(b, 0, 0, p);
        return g;
    }
}
