package com.bhchmv.darklauncher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Windows Phone-style look: big clock and a grid of colored live tiles. */
final class WindowsHome implements HomeMode {
    // Muted versions of the classic Windows Phone accent colors.
    private static final int[] ACCENTS = {
            0xFF0047D6, 0xFF8E0021, 0xFF007A00, 0xFF5C00DB, 0xFFB80062,
            0xFF008F8D, 0xFF6E4C26, 0xFF55657A, 0xFF1689C2, 0xFF8F00D6,
            0xFF4E8A13, 0xFFC25A00,
    };

    private final MainActivity a;
    private final ScrollView scroll;
    private final TextView time, date;
    private TextView batteryText, batterySub;
    private final int cell, gap;

    WindowsHome(MainActivity a) {
        this.a = a;
        scroll = new ScrollView(a);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(18), dp(14), dp(28));
        scroll.addView(col);

        time = new TextView(a);
        time.setTextColor(Color.WHITE);
        time.setTextSize(58);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setPadding(dp(4), 0, 0, 0);
        col.addView(time);

        date = new TextView(a);
        date.setTextColor(0xFFAAAAAA);
        date.setTextSize(17);
        date.setPadding(dp(6), dp(2), 0, 0);
        col.addView(date);

        TextView start = new TextView(a);
        start.setText("Start");
        start.setTextColor(Color.WHITE);
        start.setTextSize(36);
        start.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        start.setPadding(dp(4), dp(22), 0, dp(10));
        start.setOnLongClickListener(v -> {
            LauncherSettings.show(a);
            return true;
        });
        time.setOnLongClickListener(v -> {
            LauncherSettings.show(a);
            return true;
        });
        col.addView(start);

        int screenW = a.getResources().getDisplayMetrics().widthPixels;
        cell = (screenW - dp(28)) / 4;
        gap = dp(3);

        GridLayout gl = new GridLayout(a);
        gl.setColumnCount(4);
        gl.setUseDefaultMargins(false);
        gl.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        col.addView(gl);

        List<AppEntry> all = a.getApps();
        List<AppEntry> ordered = new ArrayList<>(AppRepository.favorites(all));
        for (AppEntry e : all) if (!ordered.contains(e)) ordered.add(e);

        List<int[]> slots = makeSlots(ordered.size() + 1);
        for (int i = 0; i < ordered.size() + 1; i++) {
            int[] s = slots.get(i); // row, col, rowSpan, colSpan
            View tile = (i == 0) ? batteryTile(s) : appTile(ordered.get(i - 1), s, i);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(s[0], s[2]), GridLayout.spec(s[1], s[3]));
            lp.width = cell * s[3] - 2 * gap;
            lp.height = cell * s[2] - 2 * gap;
            lp.setMargins(gap, gap, gap, gap);
            gl.addView(tile, lp);
        }

        TextView hint = new TextView(a);
        hint.setText("long-press Start for darkOS settings");
        hint.setTextColor(0xFF555555);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(18), 0, 0);
        col.addView(hint);

        tick();
    }

    /** Repeating pattern: big+4 small, 4 small+big, two wide tiles. */
    private static List<int[]> makeSlots(int need) {
        List<int[]> slots = new ArrayList<>();
        int r = 0, block = 0;
        while (slots.size() < need) {
            int t = block % 3;
            if (t == 0) {
                slots.add(new int[]{r, 0, 2, 2});
                slots.add(new int[]{r, 2, 1, 1});
                slots.add(new int[]{r, 3, 1, 1});
                slots.add(new int[]{r + 1, 2, 1, 1});
                slots.add(new int[]{r + 1, 3, 1, 1});
                r += 2;
            } else if (t == 1) {
                slots.add(new int[]{r, 0, 1, 1});
                slots.add(new int[]{r, 1, 1, 1});
                slots.add(new int[]{r + 1, 0, 1, 1});
                slots.add(new int[]{r + 1, 1, 1, 1});
                slots.add(new int[]{r, 2, 2, 2});
                r += 2;
            } else {
                slots.add(new int[]{r, 0, 1, 2});
                slots.add(new int[]{r, 2, 1, 2});
                r += 1;
            }
            block++;
        }
        return slots;
    }

    private View appTile(AppEntry e, int[] s, int index) {
        FrameLayout f = new FrameLayout(a);
        f.setBackgroundColor(ACCENTS[(index * 5) % ACCENTS.length]);
        f.setForeground(IconUtil.selectable(a));
        int w = cell * s[3] - 2 * gap, h = cell * s[2] - 2 * gap;
        boolean small = s[2] == 1 && s[3] == 1;
        int isz = small ? Math.round(h * 0.5f) : Math.round(Math.min(w, h) * 0.42f);
        ImageView iv = new ImageView(a);
        iv.setImageBitmap(IconUtil.glyph(e, isz));
        f.addView(iv, new FrameLayout.LayoutParams(isz, isz, Gravity.CENTER));
        if (!small) {
            TextView label = new TextView(a);
            label.setText(e.label);
            label.setTextColor(Color.WHITE);
            label.setTextSize(13);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(8), 0, dp(8), dp(6));
            f.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
        }
        f.setOnClickListener(v -> AppActions.launch(a, e));
        f.setOnLongClickListener(v -> {
            AppActions.showAppMenu(a, e);
            return true;
        });
        return f;
    }

    private View batteryTile(int[] s) {
        FrameLayout f = new FrameLayout(a);
        f.setBackgroundColor(0xFF1F1F1F);
        f.setForeground(IconUtil.selectable(a));
        LinearLayout inner = new LinearLayout(a);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(12), dp(10), dp(12), dp(10));
        batteryText = new TextView(a);
        batteryText.setTextColor(Color.WHITE);
        batteryText.setTextSize(46);
        batteryText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        batterySub = new TextView(a);
        batterySub.setTextColor(0xFFBBBBBB);
        batterySub.setTextSize(14);
        inner.addView(batteryText);
        inner.addView(batterySub);
        f.addView(inner);
        TextView label = new TextView(a);
        label.setText("Battery");
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setPadding(dp(8), 0, dp(8), dp(6));
        f.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
        f.setOnClickListener(v -> AppActions.openSettings(a, Intent.ACTION_POWER_USAGE_SUMMARY));
        f.setOnLongClickListener(v -> {
            LauncherSettings.show(a);
            return true;
        });
        return f;
    }

    private int dp(float v) {
        return IconUtil.dp(a, v);
    }

    @Override public View getView() { return scroll; }

    @Override
    public void tick() {
        boolean h24 = android.text.format.DateFormat.is24HourFormat(a);
        Date now = new Date();
        time.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now));
        date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now));
        if (batteryText != null) {
            BatteryManager bm = (BatteryManager) a.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                batteryText.setText(pct + "%");
                batterySub.setText(bm.isCharging() ? "charging" : "remaining");
            }
        }
    }

    @Override public void onBack() { scroll.smoothScrollTo(0, 0); }

    @Override public void onHomePressed() { scroll.smoothScrollTo(0, 0); }

    @Override public int statusBarColor() { return Color.BLACK; }
}
