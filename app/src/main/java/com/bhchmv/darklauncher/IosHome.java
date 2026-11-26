package com.bhchmv.darklauncher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** iPhone-style look: clock, search, rounded icon grid and a frosted dock. */
final class IosHome implements HomeMode {
    private static final int TOP_COLOR = 0xFF1B2340;

    private final MainActivity a;
    private final LinearLayout root;
    private final TextView time, date;
    private final EditText search;
    private final GridView grid;
    private final Adapter adapter;
    private final List<AppEntry> gridApps = new ArrayList<>();
    private final int iconPx;

    IosHome(MainActivity a) {
        this.a = a;
        iconPx = dp(58);

        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{TOP_COLOR, 0xFF0C0E18, 0xFF000000}));
        root.setFocusableInTouchMode(true);

        // Clock
        LinearLayout clock = new LinearLayout(a);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        clock.setPadding(0, dp(22), 0, dp(6));
        date = new TextView(a);
        date.setTextColor(0xFFC8C8D8);
        date.setTextSize(17);
        date.setGravity(Gravity.CENTER);
        time = new TextView(a);
        time.setTextColor(Color.WHITE);
        time.setTextSize(72);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setGravity(Gravity.CENTER);
        time.setIncludeFontPadding(false);
        clock.addView(date);
        clock.addView(time);
        clock.setOnLongClickListener(v -> {
            LauncherSettings.show(a);
            return true;
        });
        root.addView(clock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Search
        search = new EditText(a);
        search.setHint("🔍  Search");
        search.setHintTextColor(0xFF8E8E93);
        search.setTextColor(Color.WHITE);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        GradientDrawable sb = new GradientDrawable();
        sb.setCornerRadius(dp(14));
        sb.setColor(0x26FFFFFF);
        search.setBackground(sb);
        search.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(dp(18), dp(10), dp(18), dp(10));
        root.addView(search, slp);

        // Grid
        grid = new GridView(a);
        grid.setNumColumns(4);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(20));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setPadding(dp(8), dp(10), dp(8), dp(16));
        grid.setClipToPadding(false);
        grid.setVerticalScrollBarEnabled(false);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Dock
        List<AppEntry> all = a.getApps();
        List<AppEntry> favs = AppRepository.favorites(all);
        LinearLayout dock = new LinearLayout(a);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable db = new GradientDrawable();
        db.setCornerRadius(dp(30));
        db.setColor(0x30FFFFFF);
        dock.setBackground(db);
        dock.setPadding(dp(10), dp(12), dp(10), dp(12));
        for (AppEntry e : favs) {
            FrameLayout cell = new FrameLayout(a);
            ImageView iv = new ImageView(a);
            iv.setImageBitmap(IconUtil.rounded(e, iconPx));
            cell.addView(iv, new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
            cell.setOnClickListener(v -> AppActions.launch(a, e));
            cell.setOnLongClickListener(v -> {
                AppActions.showAppMenu(a, e);
                return true;
            });
            dock.addView(cell, new LinearLayout.LayoutParams(0, iconPx, 1f));
        }
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(dp(12), dp(4), dp(12), dp(14));
        root.addView(dock, dlp);

        for (AppEntry e : all) if (!favs.contains(e)) gridApps.add(e);
        adapter = new Adapter(new ArrayList<>(gridApps));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> AppActions.launch(a, adapter.getItem(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            AppActions.showAppMenu(a, adapter.getItem(pos));
            return true;
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });
        search.setOnEditorActionListener((v, actionId, ev) -> {
            if (adapter.getCount() > 0) {
                AppEntry first = adapter.getItem(0);
                reset();
                AppActions.launch(a, first);
            }
            return true;
        });

        tick();
    }

    private void filter(String q) {
        if (q.trim().isEmpty()) adapter.setItems(new ArrayList<>(gridApps));
        else adapter.setItems(AppRepository.find(a.getApps(), q));
    }

    private void reset() {
        search.setText("");
        search.clearFocus();
        InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        root.requestFocus();
        grid.smoothScrollToPosition(0);
    }

    private int dp(float v) {
        return IconUtil.dp(a, v);
    }

    @Override public View getView() { return root; }

    @Override
    public void tick() {
        boolean h24 = android.text.format.DateFormat.is24HourFormat(a);
        Date now = new Date();
        time.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now));
        date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now));
    }

    @Override public void onBack() { reset(); }

    @Override public void onHomePressed() { reset(); }

    @Override public int statusBarColor() { return TOP_COLOR; }

    private final class Adapter extends BaseAdapter {
        private List<AppEntry> items;

        Adapter(List<AppEntry> items) { this.items = items; }

        void setItems(List<AppEntry> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }

        @Override public AppEntry getItem(int position) { return items.get(position); }

        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView iv;
            TextView tv;
            if (convertView == null) {
                cell = new LinearLayout(a);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                iv = new ImageView(a);
                cell.addView(iv, new LinearLayout.LayoutParams(iconPx, iconPx));
                tv = new TextView(a);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(12);
                tv.setMaxLines(1);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(dp(2), dp(6), dp(2), 0);
                tv.setShadowLayer(4, 0, 1, 0x99000000);
                cell.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                cell = (LinearLayout) convertView;
                iv = (ImageView) cell.getChildAt(0);
                tv = (TextView) cell.getChildAt(1);
            }
            AppEntry e = items.get(position);
            iv.setImageBitmap(IconUtil.rounded(e, iconPx));
            tv.setText(e.label);
            return cell;
        }
    }
}
