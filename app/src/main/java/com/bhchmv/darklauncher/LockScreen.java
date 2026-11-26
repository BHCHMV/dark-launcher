package com.bhchmv.darklauncher;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.util.Random;

/**
 * Draws a terminal-style picture and sets it as the lock screen wallpaper.
 * (Android doesn't let apps replace the lock screen itself.)
 */
final class LockScreen {
    static final int STYLE_MATRIX = 0;
    static final int STYLE_PLAIN = 1;

    private static final String[] LOGO = {
            "     _            _     ___  ____  ",
            "  __| | __ _ _ __| | __/ _ \\/ ___| ",
            " / _` |/ _` | '__| |/ / | | \\___ \\ ",
            "| (_| | (_| | |  |   <| |_| |___) |",
            " \\__,_|\\__,_|_|  |_|\\_\\\\___/|____/ ",
    };

    private LockScreen() {}

    interface Done {
        void done(boolean ok);
    }

    /** Builds and sets the wallpaper on a background thread; calls back on the main thread. */
    static void apply(MainActivity a, int style, Done done) {
        int[] size = screenSize(a);
        new Thread(() -> {
            boolean ok;
            try {
                Bitmap bmp = draw(size[0], size[1], style);
                WallpaperManager wm = WallpaperManager.getInstance(a);
                wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK);
                ok = true;
            } catch (Exception e) {
                ok = false;
            }
            boolean result = ok;
            a.runOnUiThread(() -> done.done(result));
        }).start();
    }

    @SuppressWarnings("deprecation")
    private static int[] screenSize(Context c) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 30) {
            Rect r = wm.getMaximumWindowMetrics().getBounds();
            return new int[]{r.width(), r.height()};
        }
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return new int[]{dm.widthPixels, dm.heightPixels};
    }

    static Bitmap draw(int w, int h, int style) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.BLACK);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.MONOSPACE);

        if (style == STYLE_MATRIX) {
            Random rnd = new Random(55);
            float cs = w / 30f;
            p.setTextSize(cs);
            String chars = "01アイウエオカキクケコサシスセソ<>/\\$#{}[]=+*";
            int cols = (int) (w / (cs * 0.75f));
            for (int col = 0; col < cols; col++) {
                float x = col * cs * 0.75f;
                int len = 6 + rnd.nextInt(26);
                float start = rnd.nextInt((int) (h / cs)) * cs - cs * 10;
                for (int i = 0; i < len; i++) {
                    float y = start + i * cs;
                    if (y < 0 || y > h) continue;
                    int alpha = (int) (20 + 110f * i / len);
                    p.setColor(Color.argb(i == len - 1 ? 200 : alpha, 51, 255, 102));
                    c.drawText(String.valueOf(chars.charAt(rnd.nextInt(chars.length()))), x, y, p);
                }
            }
            // darken the middle so the logo stands out
            Paint fade = new Paint();
            fade.setColor(Color.argb(190, 0, 0, 0));
            c.drawRect(0, h * 0.47f, w, h * 0.72f, fade);
        } else {
            // faint scanlines
            Paint line = new Paint();
            line.setColor(Color.argb(28, 51, 255, 102));
            for (int y = 0; y < h; y += Math.max(3, h / 600)) c.drawLine(0, y, w, y, line);
        }

        // ASCII logo, about 82% of the screen width
        float logoSize = (w * 0.82f) / (LOGO[0].length() * 0.6f);
        p.setTextSize(logoSize);
        p.setColor(0xFF33FF66);
        p.setShadowLayer(logoSize * 0.6f, 0, 0, 0x8833FF66);
        float lw = p.measureText(LOGO[0]);
        float x0 = (w - lw) / 2f;
        float y = h * 0.52f;
        for (String l : LOGO) {
            c.drawText(l, x0, y, p);
            y += logoSize * 1.15f;
        }
        p.clearShadowLayer();

        float ts = w / 26f;
        p.setTextSize(ts);
        y += ts * 1.6f;
        p.setColor(0xFF33FF66);
        String prompt = "a55@darkos:~$ ./unlock █";
        c.drawText(prompt, (w - p.measureText(prompt)) / 2f, y, p);
        y += ts * 1.7f;
        p.setColor(0xFF7A7A7A);
        p.setTextSize(ts * 0.8f);
        String hint = "[ swipe to unlock ]";
        c.drawText(hint, (w - p.measureText(hint)) / 2f, y, p);
        return b;
    }
}
