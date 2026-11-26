package com.bhchmv.darklauncher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The whole launcher: a Linux-terminal home screen with three tabs
 * (apps / settings / system) and a swipe-down quick panel.
 */
final class TerminalHome {
    private static final int GREEN = 0xFF33FF66;
    private static final int DIM = 0xFF1F9E48;
    private static final int WHITE = 0xFFD8D8D8;
    private static final int CYAN = 0xFF4FC3F7;
    private static final int YELLOW = 0xFFFFD54F;
    private static final int RED = 0xFFFF5C5C;
    private static final int GRAY = 0xFF7A7A7A;
    private static final String PROMPT = "a55@darkos:~$ ";

    private static final String[] TAB_NAMES = {"apps", "settings", "system"};
    private static final String[] TAB_CMDS = {"ls ~/apps", "settings", "neofetch"};

    private static final String[] LOGO = {
            "     _            _     ___  ____  ",
            "  __| | __ _ _ __| | __/ _ \\/ ___| ",
            " / _` |/ _` | '__| |/ / | | \\___ \\ ",
            "| (_| | (_| | |  |   <| |_| |___) |",
            " \\__,_|\\__,_|_|  |_|\\_\\\\___/|____/ ",
    };

    private final MainActivity a;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final SwipeLayout root;
    private final LinearLayout out;
    private final LinearLayout panel;
    private final View scrim;
    private final ScrollView scroll;
    private final EditText input;
    private final TextView[] tabViews = new TextView[3];
    private final TextView status;
    private final Map<String, Runnable> menu = new HashMap<>();

    /** Where line() writes: the terminal output or the quick panel. */
    private LinearLayout target;
    private int tab = 0;
    private boolean inAppMenu;
    private boolean busy;
    private boolean panelOpen;
    private int clearGen;
    private View scrollTarget;
    private boolean scrollPosted;

    TerminalHome(MainActivity a) {
        this.a = a;
        root = new SwipeLayout(a);
        root.setBackgroundColor(Color.BLACK);
        root.setFocusableInTouchMode(true);

        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // tmux-style tab bar
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF101010);
        for (int i = 0; i < 3; i++) {
            TextView t = mono(" " + (i + 1) + ":" + TAB_NAMES[i] + " ", DIM);
            t.setPadding(dp(4), dp(9), dp(4), dp(9));
            final int idx = i;
            t.setOnClickListener(v -> {
                if (!busy) showTab(idx, true);
            });
            tabViews[i] = t;
            bar.addView(t);
        }
        status = mono("", GRAY);
        status.setGravity(Gravity.END);
        status.setPadding(0, 0, dp(10), 0);
        status.setOnClickListener(v -> openPanel());
        bar.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        column.addView(bar);
        View rule = new View(a);
        rule.setBackgroundColor(0xFF1F5F33);
        column.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // output
        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        out = new LinearLayout(a);
        out.setOrientation(LinearLayout.VERTICAL);
        out.setPadding(dp(10), dp(8), dp(10), dp(16));
        scroll.addView(out);
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        target = out;

        // prompt
        LinearLayout inputBar = new LinearLayout(a);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setBackgroundColor(0xFF0B0B0B);
        inputBar.setPadding(dp(10), dp(4), dp(10), dp(4));
        inputBar.addView(mono(PROMPT, GREEN));
        input = new EditText(a);
        input.setBackground(null);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(13);
        input.setTextColor(GREEN);
        input.setHint("type here");
        input.setHintTextColor(0xFF2E5E3A);
        input.setSingleLine(true);
        input.setPadding(0, dp(8), 0, dp(8));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable cur = new GradientDrawable();
            cur.setColor(GREEN);
            cur.setSize(dp(8), dp(16));
            input.setTextCursorDrawable(cur);
        }
        input.setOnEditorActionListener((v, actionId, ev) -> {
            if (ev != null && ev.getAction() != KeyEvent.ACTION_DOWN) return true;
            String cmd = input.getText().toString();
            input.setText("");
            if (!cmd.trim().isEmpty()) runTyped(cmd);
            return true;
        });
        inputBar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        column.addView(inputBar);

        // quick panel overlay
        scrim = new View(a);
        scrim.setBackgroundColor(0xB3000000);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> closePanel());
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xFF070907);
        pbg.setStroke(dp(1), 0xFF1F9E48);
        panel.setBackground(pbg);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));
        panel.setVisibility(View.GONE);
        panel.setClickable(true);
        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        root.setListener(new SwipeLayout.Listener() {
            @Override
            public boolean canSwipe(int dir, float startY) {
                if (panelOpen) return dir == SwipeLayout.UP;
                if (busy) return false;
                if (dir == SwipeLayout.DOWN) return startY < dp(140) || !scroll.canScrollVertically(-1);
                return dir == SwipeLayout.LEFT || dir == SwipeLayout.RIGHT;
            }

            @Override
            public void onSwipe(int dir) {
                if (dir == SwipeLayout.DOWN) openPanel();
                else if (dir == SwipeLayout.UP) closePanel();
                else if (dir == SwipeLayout.LEFT) showTab((tab + 1) % 3, true);
                else showTab((tab + 2) % 3, true);
            }
        });

        updateTabBar();
        tick();
        boot();
    }

    View getView() {
        return root;
    }

    // ===================== output helpers =====================

    private TextView mono(CharSequence s, int color) {
        TextView t = new TextView(a);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(13);
        t.setTextColor(color);
        t.setText(s);
        return t;
    }

    private TextView line(CharSequence s, int color) {
        TextView t = mono(s, color);
        target.addView(t);
        return t;
    }

    private void blank() {
        line("", WHITE);
    }

    private void section(String title) {
        blank();
        StringBuilder rule = new StringBuilder("── ").append(title).append(' ');
        for (int i = Math.max(2, 30 - title.length()); i > 0; i--) rule.append('─');
        line(rule, CYAN);
    }

    private TextView clickable(TextView t, Runnable onClick) {
        t.setBackground(IconUtil.selectable(a));
        t.setOnClickListener(v -> {
            if (!busy) onClick.run();
        });
        return t;
    }

    /** A numbered option. Tapping it "types" the command, then runs it. */
    private TextView option(String key, String name, String desc, Runnable action) {
        String text = String.format(Locale.US, "  [%s] %-10s %s", key, name, desc);
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        int split = Math.min(text.length(), 7 + Math.max(10, name.length()));
        sb.setSpan(new ForegroundColorSpan(GRAY), split, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView t = line(sb, GREEN);
        t.setPadding(0, dp(6), 0, dp(6));
        boolean inPanel = target == panel;
        clickable(t, () -> {
            if (inPanel) action.run();
            else typeAndRun(name, action);
        });
        if (!inPanel) {
            menu.put(key.toLowerCase(Locale.ROOT), action);
            menu.put(name.toLowerCase(Locale.ROOT), action);
        }
        return t;
    }

    /** name  [-] [#####-----]  50% [+] */
    private void slider(String name, Supplier<String> value, Runnable minus, Runnable plus) {
        LinearLayout r = row(name);
        TextView v = mono(value.get(), GREEN);
        r.addView(button("[-]", minus, v, value));
        r.addView(v);
        r.addView(button("[+]", plus, v, value));
    }

    /** name  value  [button] */
    private void control(String name, Supplier<String> value, String label, Runnable action) {
        LinearLayout r = row(name);
        TextView v = mono(value.get(), GREEN);
        v.setMinWidth(dp(96));
        r.addView(v);
        r.addView(button(label, action, v, value));
    }

    private LinearLayout row(String name) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(mono(String.format(Locale.US, "  %-11s", name), WHITE));
        target.addView(r);
        return r;
    }

    private TextView button(String label, Runnable action, TextView value, Supplier<String> supplier) {
        TextView b = mono(label, CYAN);
        b.setPadding(dp(6), dp(8), dp(6), dp(8));
        clickable(b, () -> {
            action.run();
            value.setText(supplier.get());
            value.postDelayed(() -> value.setText(supplier.get()), 250);
        });
        return b;
    }

    private void echo(String cmd) {
        line(PROMPT + cmd, GREEN);
    }

    private void typeAndRun(String cmd, Runnable action) {
        busy = true;
        TextView t = line(PROMPT, GREEN);
        final int gen = clearGen;
        final int[] i = {0};
        h.post(new Runnable() {
            @Override
            public void run() {
                if (gen != clearGen) return;
                if (i[0] <= cmd.length()) {
                    t.setText(PROMPT + cmd.substring(0, i[0]) + (i[0] < cmd.length() ? "█" : ""));
                    i[0]++;
                    h.postDelayed(this, 22);
                } else {
                    busy = false;
                    runAndReveal(action);
                }
            }
        });
    }

    /** Runs an action and scrolls so its first new line is visible. */
    private void runAndReveal(Runnable action) {
        int gen = clearGen;
        int before = out.getChildCount();
        action.run();
        if (gen == clearGen && out.getChildCount() > before) requestScroll(out.getChildAt(before));
    }

    private void requestScroll(View t) {
        scrollTarget = t;
        if (scrollPosted) return;
        scrollPosted = true;
        scroll.postDelayed(() -> {
            scrollPosted = false;
            View v = scrollTarget;
            scrollTarget = null;
            if (v == null || v.getParent() != out) return;
            int y = Math.max(0, v.getTop() - dp(8));
            if (y > scroll.getScrollY() + scroll.getHeight() - dp(60) || y < scroll.getScrollY()) {
                scroll.smoothScrollTo(0, y);
            }
        }, 60);
    }

    private void clearOut() {
        h.removeCallbacksAndMessages(null);
        clearGen++;
        busy = false;
        out.removeAllViews();
        menu.clear();
        target = out;
        scroll.scrollTo(0, 0);
    }

    // ===================== tabs =====================

    private void updateTabBar() {
        for (int i = 0; i < 3; i++) {
            TextView t = tabViews[i];
            if (i == tab) {
                t.setBackgroundColor(GREEN);
                t.setTextColor(Color.BLACK);
            } else {
                t.setBackgroundColor(Color.TRANSPARENT);
                t.setTextColor(DIM);
            }
        }
    }

    private void showTab(int t, boolean animate) {
        tab = t;
        inAppMenu = false;
        updateTabBar();
        clearOut();
        Runnable render = t == 0 ? () -> renderApps(null) : t == 1 ? this::renderSettings : this::renderSystem;
        if (animate) {
            typeAndRun(TAB_CMDS[t], render);
        } else {
            echo(TAB_CMDS[t]);
            render.run();
        }
    }

    private void showAppsFocused(AppEntry focus) {
        tab = 0;
        inAppMenu = false;
        updateTabBar();
        clearOut();
        echo(TAB_CMDS[0]);
        renderApps(focus);
    }

    private void boot() {
        clearOut();
        busy = true;
        List<Runnable> steps = new ArrayList<>();
        steps.add(() -> line("darkOS 1.1 (tty1) on " + Build.MANUFACTURER + " " + Build.MODEL, GRAY));
        steps.add(() -> line("[  OK  ] mounted /home/a55", DIM));
        steps.add(() -> line("[  OK  ] indexed " + a.getApps().size() + " apps", DIM));
        steps.add(() -> line("[  OK  ] started dark shell", DIM));
        steps.add(this::blank);
        for (String l : LOGO) {
            steps.add(() -> line(l, GREEN).setTextSize(11));
        }
        steps.add(this::blank);
        steps.add(() -> line("  swipe ↓ quick panel   swipe ← → tabs", GRAY));
        steps.add(() -> line("  hold an app to move it or uninstall it", GRAY));
        steps.add(() -> {
            busy = false;
            if (!LauncherSettings.isDefaultHome(a)) {
                blank();
                option("h", "sethome", "make darkOS your home screen", () -> LauncherSettings.requestHome(a));
            }
            typeAndRun(TAB_CMDS[0], () -> renderApps(null));
        });
        for (int i = 0; i < steps.size(); i++) h.postDelayed(steps.get(i), i * 50L);
    }

    // ----- tab 1: apps -----

    private void renderApps(AppEntry focus) {
        List<AppEntry> apps = a.getApps();
        section("APPS (" + apps.size() + ")");
        View focusView = null;
        for (int i = 0; i < apps.size(); i++) {
            AppEntry e = apps.get(i);
            TextView t = line(String.format(Locale.US, "  %3d  %s", i + 1, e.label), WHITE);
            t.setPadding(0, dp(5), 0, dp(5));
            clickable(t, () -> launch(e));
            t.setOnLongClickListener(v -> {
                if (!busy) appMenu(e, null);
                return true;
            });
            menu.put(String.valueOf(i + 1), () -> launch(e));
            if (e == focus) {
                focusView = t;
                t.setTextColor(GREEN);
            }
        }
        line("  tap to open · hold for options · or type a name", GRAY);
        if (focusView != null) {
            View fv = focusView;
            scroll.postDelayed(() -> scroll.scrollTo(0, Math.max(0, fv.getTop() - scroll.getHeight() / 3)), 80);
        }
    }

    private void launch(AppEntry e) {
        line("[  OK  ] launching " + e.label + " ...", DIM);
        AppActions.launch(a, e);
    }

    private void appMenu(AppEntry e, String message) {
        int idx = a.getApps().indexOf(e);
        if (idx < 0) {
            showTab(0, false);
            return;
        }
        clearOut();
        inAppMenu = true;
        echo("app --menu \"" + e.label + "\"");
        section(e.label);
        line("  position " + (idx + 1) + " of " + a.getApps().size(), GRAY);
        if (message != null) line(message, DIM);
        option("1", "open", "launch the app", () -> launch(e));
        option("2", "up", "move up one place", () -> move(e, idx - 1));
        option("3", "down", "move down one place", () -> move(e, idx + 1));
        option("4", "top", "move to the top", () -> move(e, 0));
        option("5", "bottom", "move to the bottom", () -> move(e, a.getApps().size() - 1));
        option("6", "info", "app info (Samsung page)", () -> AppActions.appInfo(a, e));
        option("7", "uninstall", "remove this app", () -> AppActions.uninstall(a, e));
        option("0", "back", "back to the app list", () -> showAppsFocused(e));
    }

    private void move(AppEntry e, int to) {
        int n = a.getApps().size();
        to = Math.max(0, Math.min(n - 1, to));
        a.moveApp(e, to);
        appMenu(e, "[  OK  ] moved to position " + (to + 1));
    }

    // ----- tab 2: settings -----

    private String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    private void renderSettings() {
        boolean write = Controls.canWrite(a);
        section("DISPLAY");
        slider("brightness", () -> Controls.bar(Controls.brightnessPct(a)),
                () -> Controls.changeBrightness(a, -10), () -> Controls.changeBrightness(a, 10));
        control("auto-bright", () -> onOff(Controls.autoBrightness(a)), "[toggle]", () -> Controls.toggleAutoBrightness(a));
        control("auto-rotate", () -> onOff(Controls.autoRotate(a)), "[toggle]", () -> Controls.toggleAutoRotate(a));
        control("screen off", () -> Controls.timeout(a), "[next]", () -> Controls.cycleTimeout(a));
        if (!write) {
            TextView p = line("  [!] tap here to let darkOS change display settings", YELLOW);
            p.setPadding(0, dp(6), 0, dp(6));
            clickable(p, () -> Controls.requestWrite(a));
        }

        section("SOUND");
        slider("media vol", () -> Controls.bar(Controls.volumePct(a, AudioManager.STREAM_MUSIC)),
                () -> Controls.changeVolume(a, AudioManager.STREAM_MUSIC, false),
                () -> Controls.changeVolume(a, AudioManager.STREAM_MUSIC, true));
        slider("ring vol", () -> Controls.bar(Controls.volumePct(a, AudioManager.STREAM_RING)),
                () -> Controls.changeVolume(a, AudioManager.STREAM_RING, false),
                () -> Controls.changeVolume(a, AudioManager.STREAM_RING, true));
        control("sound mode", () -> Controls.soundMode(a), "[switch]", () -> Controls.toggleSoundMode(a));
        if (Controls.hasTorch()) {
            control("flashlight", () -> onOff(Controls.torchOn()), "[toggle]", () -> Controls.toggleTorch(a));
        }

        section("CONNECTIONS");
        control("network", () -> Controls.network(a), "[wifi]",
                () -> Controls.openPanel(a, Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS));
        control("bluetooth", () -> Controls.bluetooth(a), "[open]",
                () -> AppActions.openSettings(a, Settings.ACTION_BLUETOOTH_SETTINGS));
        control("internet", () -> "data & wifi", "[open]",
                () -> Controls.openPanel(a, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIRELESS_SETTINGS));
        control("airplane", () -> "mode", "[open]",
                () -> AppActions.openSettings(a, Settings.ACTION_AIRPLANE_MODE_SETTINGS));

        section("MORE (opens Samsung settings)");
        setting("d", "display", "dark mode, font size", Settings.ACTION_DISPLAY_SETTINGS);
        setting("b", "battery", "usage & power saving", Intent.ACTION_POWER_USAGE_SUMMARY);
        setting("m", "manage", "installed apps", Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        setting("s", "storage", "free up space", Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        setting("l", "lock", "lock screen & security", Settings.ACTION_SECURITY_SETTINGS);
        setting("i", "about", "about phone", Settings.ACTION_DEVICE_INFO_SETTINGS);
        setting("a", "all", "all settings", Settings.ACTION_SETTINGS);

        section("LAUNCHER");
        option("h", "sethome", LauncherSettings.isDefaultHome(a) ? "already your home screen"
                : "make darkOS your home screen", () -> LauncherSettings.requestHome(a));
        option("z", "sort", "sort apps A-Z (resets your order)", () -> {
            a.sortApps();
            line("[  OK  ] apps sorted A-Z", DIM);
        });
        blank();
        line("  note: Samsung's own Settings app and its pull-down", GRAY);
        line("  panel can't be restyled by other apps. Use these", GRAY);
        line("  controls and the swipe-down panel here instead.", GRAY);
    }

    private void setting(String key, String name, String desc, String action) {
        option(key, name, desc, () -> {
            line("[  OK  ] opening " + desc + " ...", DIM);
            AppActions.openSettings(a, action);
        });
    }

    // ----- tab 3: system -----

    private void renderSystem() {
        section("SYSTEM");
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        double gb = 1024.0 * 1024 * 1024;
        long up = SystemClock.elapsedRealtime() / 60000;
        String cpu = Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : Build.HARDWARE;

        kv("user", "a55@darkos");
        kv("os", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        kv("host", cap(Build.MANUFACTURER) + " " + Build.MODEL);
        kv("kernel", String.valueOf(System.getProperty("os.version")));
        kv("uptime", (up / 1440) + "d " + ((up / 60) % 24) + "h " + (up % 60) + "m");
        kv("shell", "darkOS sh 1.1");
        kv("display", dm.widthPixels + "x" + dm.heightPixels);
        kv("cpu", cpu);
        kv("memory", String.format(Locale.US, "%.1f / %.1f GB",
                (mi.totalMem - mi.availMem) / gb, mi.totalMem / gb));
        kv("storage", String.format(Locale.US, "%.0f GB free of %.0f GB",
                fs.getAvailableBytes() / gb, fs.getTotalBytes() / gb));
        kv("battery", Controls.battery(a).replace("+", " (charging)"));
        kv("network", Controls.network(a));
        kv("apps", a.getApps().size() + " installed");
        SpannableStringBuilder colors = new SpannableStringBuilder("  ");
        int[] cs = {0xFF000000, RED, GREEN, YELLOW, 0xFF4F7CFF, 0xFFD050FF, CYAN, WHITE};
        for (int c : cs) {
            int s = colors.length();
            colors.append("███");
            colors.setSpan(new ForegroundColorSpan(c), s, colors.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        blank();
        line(colors, WHITE);
        section("TOOLS");
        option("n", "notify", "open notifications", this::notifications);
        option("q", "panel", "open the quick panel", this::openPanel);
        option("?", "help", "commands you can type", this::help);
    }

    private void kv(String k, String v) {
        String text = String.format(Locale.US, "  %-9s %s", k + ":", v);
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.setSpan(new ForegroundColorSpan(CYAN), 0, Math.min(text.length(), 12), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        line(sb, WHITE);
    }

    private void notifications() {
        if (!Controls.openNotifications(a)) {
            line("  can't open notifications here — swipe down from the very top edge", YELLOW);
        }
    }

    private void help() {
        section("HELP");
        String[][] cmds = {
                {"apps, ls", "app list (tab 1)"},
                {"settings", "settings (tab 2)"},
                {"neofetch", "system info (tab 3)"},
                {"<number>", "open app / pick option"},
                {"<app name>", "open app, e.g. 'chrome'"},
                {"open <name>", "same as above"},
                {"torch", "flashlight on/off"},
                {"wifi, bt", "Wi-Fi / Bluetooth pages"},
                {"panel", "quick panel"},
                {"notify", "notifications"},
                {"battery, date", "quick info"},
                {"sethome", "make darkOS your home"},
                {"clear", "redraw this tab"},
        };
        for (String[] c : cmds) line(String.format(Locale.US, "  %-14s %s", c[0], c[1]), WHITE);
    }

    // ===================== quick panel =====================

    private void openPanel() {
        if (panelOpen) return;
        panelOpen = true;
        hideKeyboard();
        buildPanel();
        scrim.setAlpha(0f);
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(180).start();
        panel.setVisibility(View.INVISIBLE);
        panel.post(() -> {
            panel.setTranslationY(-panel.getHeight());
            panel.setVisibility(View.VISIBLE);
            panel.animate().translationY(0).setDuration(200).start();
        });
    }

    private void closePanel() {
        if (!panelOpen) return;
        panelOpen = false;
        scrim.animate().alpha(0f).setDuration(160).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        panel.animate().translationY(-panel.getHeight()).setDuration(180)
                .withEndAction(() -> panel.setVisibility(View.GONE)).start();
    }

    private void buildPanel() {
        panel.removeAllViews();
        LinearLayout prev = target;
        target = panel;
        line("┌─ quick panel ───────────────", CYAN);
        line("  " + new SimpleDateFormat("EEE d MMM  HH:mm", Locale.getDefault()).format(new Date())
                + "   bat " + Controls.battery(a), WHITE);
        line("  net " + Controls.network(a) + "   bt " + Controls.bluetooth(a)
                + "   " + Controls.soundMode(a), GRAY);
        blank();
        slider("brightness", () -> Controls.bar(Controls.brightnessPct(a)),
                () -> Controls.changeBrightness(a, -10), () -> Controls.changeBrightness(a, 10));
        slider("media vol", () -> Controls.bar(Controls.volumePct(a, AudioManager.STREAM_MUSIC)),
                () -> Controls.changeVolume(a, AudioManager.STREAM_MUSIC, false),
                () -> Controls.changeVolume(a, AudioManager.STREAM_MUSIC, true));
        if (Controls.hasTorch()) {
            control("flashlight", () -> onOff(Controls.torchOn()), "[toggle]", () -> Controls.toggleTorch(a));
        }
        control("sound mode", () -> Controls.soundMode(a), "[switch]", () -> Controls.toggleSoundMode(a));
        control("auto-rotate", () -> onOff(Controls.autoRotate(a)), "[toggle]", () -> Controls.toggleAutoRotate(a));
        control("wifi", () -> Controls.network(a), "[open]",
                () -> Controls.openPanel(a, Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS));
        control("bluetooth", () -> Controls.bluetooth(a), "[open]",
                () -> AppActions.openSettings(a, Settings.ACTION_BLUETOOTH_SETTINGS));
        blank();
        option("n", "notify", "notifications", () -> {
            closePanel();
            notifications();
        });
        option("s", "settings", "all settings (tab 2)", () -> {
            closePanel();
            showTab(1, true);
        });
        option("x", "close", "close this panel", this::closePanel);
        line("└─ swipe up to close ─────────", GRAY);
        target = prev;
    }

    // ===================== typed commands =====================

    private void runTyped(String raw) {
        echo(raw.trim());
        runAndReveal(() -> execute(raw));
    }

    private void execute(String raw) {
        String c = raw.trim();
        String lc = c.toLowerCase(Locale.ROOT);
        Runnable m = menu.get(lc);
        if (m != null) {
            m.run();
            return;
        }
        int sp = c.indexOf(' ');
        String cmd = sp < 0 ? lc : lc.substring(0, sp);
        String arg = sp < 0 ? "" : c.substring(sp + 1).trim();
        switch (cmd) {
            case "help": case "?": help(); return;
            case "apps": case "ls": showTab(0, false); return;
            case "settings": showTab(1, false); return;
            case "system": case "sysinfo": case "neofetch": case "uname": showTab(2, false); return;
            case "clear": case "cls": showTab(tab, false); return;
            case "open": case "run": case "launch": open(arg); return;
            case "panel": case "quick": openPanel(); return;
            case "notify": case "notifications": notifications(); return;
            case "sethome": LauncherSettings.requestHome(a); return;
            case "torch": case "flashlight": case "flash":
                Controls.toggleTorch(a);
                line(Controls.hasTorch() ? "  flashlight " + onOff(Controls.torchOn()) : "  no flashlight found", WHITE);
                return;
            case "date": line("  " + new SimpleDateFormat("EEEE d MMMM yyyy, HH:mm:ss", Locale.getDefault())
                    .format(new Date()), WHITE); return;
            case "battery": line("  " + Controls.battery(a).replace("+", " (charging)"), WHITE); return;
            case "whoami": line("  a55", WHITE); return;
            case "echo": line("  " + arg, WHITE); return;
            case "wifi":
                line("[  OK  ] opening Wi-Fi ...", DIM);
                Controls.openPanel(a, Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS);
                return;
            case "bluetooth": case "bt":
                line("[  OK  ] opening Bluetooth ...", DIM);
                AppActions.openSettings(a, Settings.ACTION_BLUETOOTH_SETTINGS);
                return;
            case "sudo": line("  [sudo] nice try.", YELLOW); return;
            case "exit": case "logout": line("  this is your home screen — nowhere to exit to :)", YELLOW); return;
            case "reboot": case "shutdown": line("  permission denied: hold the power button for that", RED); return;
            default:
                List<AppEntry> found = AppRepository.find(a.getApps(), c);
                if (found.size() == 1) launch(found.get(0));
                else if (found.size() > 1) matches(found);
                else line("  bash: " + cmd + ": command not found  (type 'help')", RED);
        }
    }

    private void open(String arg) {
        if (arg.isEmpty()) {
            line("  usage: open <app name>", YELLOW);
            return;
        }
        List<AppEntry> found = AppRepository.find(a.getApps(), arg);
        if (found.isEmpty()) line("  open: no app matching '" + arg + "'", RED);
        else if (found.size() == 1) launch(found.get(0));
        else matches(found);
    }

    private void matches(List<AppEntry> found) {
        line("  " + found.size() + " matches — tap one or type its number:", CYAN);
        menu.clear();
        int n = 0;
        for (AppEntry e : found) {
            n++;
            TextView t = line(String.format(Locale.US, "  %3d  %s", n, e.label), WHITE);
            t.setPadding(0, dp(5), 0, dp(5));
            clickable(t, () -> launch(e));
            menu.put(String.valueOf(n), () -> launch(e));
        }
    }

    // ===================== lifecycle hooks =====================

    void tick() {
        status.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date())
                + "  " + Controls.battery(a) + " ▾");
    }

    /** Back to the launcher (e.g. after granting a permission): refresh live values. */
    void onResume() {
        tick();
        if (busy) return;
        if (panelOpen) buildPanel();
        if (tab == 1 && !inAppMenu) {
            int y = scroll.getScrollY();
            showTab(1, false);
            scroll.post(() -> scroll.scrollTo(0, y));
        }
    }

    void onAppsChanged() {
        if (tab == 0 && !inAppMenu && !busy) {
            int y = scroll.getScrollY();
            showTab(0, false);
            scroll.post(() -> scroll.scrollTo(0, y));
        }
    }

    void onBack() {
        hideKeyboard();
        if (panelOpen) {
            closePanel();
        } else if (busy) {
            // ignore
        } else if (inAppMenu) {
            showTab(0, false);
        } else if (tab != 0) {
            showTab(0, true);
        }
    }

    /** Home pressed while already on the home screen. */
    void onHomePressed() {
        hideKeyboard();
        closePanel();
        if (busy) return;
        if (tab != 0 || inAppMenu) showTab(0, false);
        else scroll.smoothScrollTo(0, 0);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
        root.requestFocus();
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private int dp(float v) {
        return IconUtil.dp(a, v);
    }

    @SuppressWarnings("unused")
    private void toast(String s) {
        Toast.makeText(a, s, Toast.LENGTH_SHORT).show();
    }
}
