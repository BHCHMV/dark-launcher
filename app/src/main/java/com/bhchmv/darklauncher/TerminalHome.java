package com.bhchmv.darklauncher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
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
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Linux terminal look. Everything is a numbered option you can tap (it "types"
 * the command for you), or you can type commands yourself.
 */
final class TerminalHome implements HomeMode {
    private static final int GREEN = 0xFF33FF66;
    private static final int DIM = 0xFF1F9E48;
    private static final int WHITE = 0xFFD8D8D8;
    private static final int CYAN = 0xFF4FC3F7;
    private static final int YELLOW = 0xFFFFD54F;
    private static final int RED = 0xFFFF5C5C;
    private static final int GRAY = 0xFF7A7A7A;
    private static final String PROMPT = "a55@darkos:~$ ";

    private static final String[] LOGO = {
            "     _            _     ___  ____  ",
            "  __| | __ _ _ __| | __/ _ \\/ ___| ",
            " / _` |/ _` | '__| |/ / | | \\___ \\ ",
            "| (_| | (_| | |  |   <| |_| |___) |",
            " \\__,_|\\__,_|_|  |_|\\_\\\\___/|____/ ",
    };

    private final MainActivity a;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final LinearLayout root, out;
    private final ScrollView scroll;
    private final EditText input;
    private final Map<String, Runnable> menu = new HashMap<>();
    private String menuName = "main";
    private boolean busy;
    private View scrollTarget;
    private boolean scrollPosted;

    TerminalHome(MainActivity a) {
        this.a = a;
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setFocusableInTouchMode(true);

        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        out = new LinearLayout(a);
        out.setOrientation(LinearLayout.VERTICAL);
        out.setPadding(dp(10), dp(8), dp(10), dp(12));
        scroll.addView(out);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF0B0B0B);
        bar.setPadding(dp(10), dp(6), dp(10), dp(6));
        TextView prompt = mono(PROMPT, GREEN);
        bar.addView(prompt);
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
            if (!cmd.trim().isEmpty()) {
                echo(cmd);
                execute(cmd);
            }
            return true;
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        boot();
    }

    // ---------- output helpers ----------

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
        out.addView(t);
        requestScroll(null);
        return t;
    }

    private void blank() {
        line("", WHITE);
    }

    private TextView option(String key, String name, String desc, Runnable action) {
        String text = String.format(Locale.US, "  [%s] %-10s %s", key, name, desc);
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        int split = Math.min(text.length(), 7 + Math.max(10, name.length()));
        sb.setSpan(new ForegroundColorSpan(GRAY), split, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView t = line(sb, GREEN);
        t.setPadding(0, dp(6), 0, dp(6));
        t.setBackground(IconUtil.selectable(a));
        t.setOnClickListener(v -> {
            if (!busy) typeAndRun(name, action);
        });
        menu.put(key.toLowerCase(Locale.ROOT), action);
        menu.put(name.toLowerCase(Locale.ROOT), action);
        return t;
    }

    private void echo(String cmd) {
        line(PROMPT + cmd, GREEN);
    }

    /** Shows the command being typed out, then runs it. */
    private void typeAndRun(String cmd, Runnable action) {
        busy = true;
        TextView t = line(PROMPT, GREEN);
        final int[] i = {0};
        h.post(new Runnable() {
            @Override
            public void run() {
                if (i[0] <= cmd.length()) {
                    t.setText(PROMPT + cmd.substring(0, i[0]) + (i[0] < cmd.length() ? "█" : ""));
                    i[0]++;
                    h.postDelayed(this, 30);
                } else {
                    busy = false;
                    action.run();
                }
            }
        });
    }

    private void requestScroll(View target) {
        scrollTarget = target;
        if (scrollPosted) return;
        scrollPosted = true;
        scroll.postDelayed(() -> {
            scrollPosted = false;
            int y = scrollTarget == null ? out.getHeight() : Math.max(0, scrollTarget.getTop() - dp(8));
            scrollTarget = null;
            scroll.smoothScrollTo(0, y);
        }, 60);
    }

    private void clear() {
        h.removeCallbacksAndMessages(null);
        busy = false;
        out.removeAllViews();
    }

    // ---------- screens ----------

    private void boot() {
        clear();
        busy = true;
        List<Runnable> steps = new ArrayList<>();
        steps.add(() -> line("darkOS 1.0 (tty1) on " + Build.MANUFACTURER + " " + Build.MODEL, GRAY));
        steps.add(() -> line("[  OK  ] mounted /home/a55", DIM));
        steps.add(() -> line("[  OK  ] indexed " + appCount() + " apps", DIM));
        steps.add(() -> line("[  OK  ] started dark shell", DIM));
        steps.add(this::blank);
        for (String l : LOGO) {
            steps.add(() -> {
                TextView t = line(l, GREEN);
                t.setTextSize(11);
            });
        }
        steps.add(this::blank);
        steps.add(() -> line("welcome back. " + new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
                .format(new Date()), WHITE));
        steps.add(() -> {
            busy = false;
            showMainMenu();
        });
        for (int i = 0; i < steps.size(); i++) h.postDelayed(steps.get(i), i * 55L);
    }

    private void startMenu(String name, String title) {
        menu.clear();
        menuName = name;
        blank();
        StringBuilder rule = new StringBuilder("── ").append(title).append(' ');
        for (int i = Math.max(2, 30 - title.length()); i > 0; i--) rule.append('─');
        line(rule, CYAN);
    }

    private void showMainMenu() {
        startMenu("main", "MAIN MENU");
        option("1", "apps", "open an application", this::listApps);
        option("2", "settings", "phone settings", this::settingsMenu);
        option("3", "sysinfo", "device information", this::sysinfo);
        option("4", "theme", "change the look", this::themeMenu);
        option("5", "clear", "clear the screen", this::boot);
        option("6", "help", "commands you can type", this::help);
        line("  tap an option or type a command", GRAY);
    }

    private void listApps() {
        startMenu("apps", "APPLICATIONS");
        TextView header = (TextView) out.getChildAt(out.getChildCount() - 1);
        int n = 0;
        for (AppEntry e : a.getApps()) {
            if (e.special) continue;
            n++;
            TextView t = line(String.format(Locale.US, "  %3d  %s", n, e.label), WHITE);
            t.setPadding(0, dp(5), 0, dp(5));
            t.setBackground(IconUtil.selectable(a));
            t.setOnClickListener(v -> launch(e));
            t.setOnLongClickListener(v -> {
                AppActions.showAppMenu(a, e);
                return true;
            });
            menu.put(String.valueOf(n), () -> launch(e));
        }
        line("  " + n + " apps — tap one, type its number, or type its name", GRAY);
        option("0", "back", "main menu", this::showMainMenu);
        requestScroll(header);
    }

    private void launch(AppEntry e) {
        line("[  OK  ] launching " + e.label + " ...", DIM);
        AppActions.launch(a, e);
    }

    private void settingsMenu() {
        startMenu("settings", "SETTINGS");
        setting("1", "wifi", "Wi-Fi networks", Settings.ACTION_WIFI_SETTINGS);
        setting("2", "bluetooth", "devices & pairing", Settings.ACTION_BLUETOOTH_SETTINGS);
        setting("3", "display", "brightness, dark mode", Settings.ACTION_DISPLAY_SETTINGS);
        setting("4", "sound", "volume & ringtones", Settings.ACTION_SOUND_SETTINGS);
        setting("5", "battery", "usage & power saving", Intent.ACTION_POWER_USAGE_SUMMARY);
        setting("6", "apps", "manage installed apps", Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        setting("7", "storage", "free up space", Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        setting("8", "security", "lock screen & security", Settings.ACTION_SECURITY_SETTINGS);
        setting("9", "about", "about phone", Settings.ACTION_DEVICE_INFO_SETTINGS);
        setting("a", "all", "all settings", Settings.ACTION_SETTINGS);
        option("l", "launcher", "darkOS look & home screen", () -> LauncherSettings.show(a));
        option("0", "back", "main menu", this::showMainMenu);
    }

    private void setting(String key, String name, String desc, String action) {
        option(key, name, desc, () -> {
            line("[  OK  ] opening " + desc + " ...", DIM);
            AppActions.openSettings(a, action);
        });
    }

    private void themeMenu() {
        startMenu("theme", "THEME");
        option("1", "iphone", "rounded icons + dock", () -> switchTo(MainActivity.MODE_IOS, "iphone"));
        option("2", "windows", "live tiles", () -> switchTo(MainActivity.MODE_WIN, "windows"));
        option("3", "terminal", "you are here", () -> line("  already running terminal", YELLOW));
        option("0", "back", "main menu", this::showMainMenu);
    }

    private void switchTo(String mode, String name) {
        line("[  OK  ] switching to " + name + " ...", DIM);
        busy = true;
        h.postDelayed(() -> a.setMode(mode), 450);
    }

    private void help() {
        startMenu("help", "HELP");
        String[][] cmds = {
                {"apps, ls", "list apps"},
                {"open <name>", "open an app"},
                {"<app name>", "same as open, e.g. 'chrome'"},
                {"settings", "phone settings menu"},
                {"wifi, bluetooth", "jump straight to a setting"},
                {"sysinfo", "device info (also: neofetch)"},
                {"battery", "battery level"},
                {"date", "current date & time"},
                {"theme", "change the look"},
                {"iphone, windows", "switch look directly"},
                {"clear", "clear the screen"},
                {"menu", "back to main menu"},
        };
        for (String[] c : cmds) line(String.format(Locale.US, "  %-16s %s", c[0], c[1]), WHITE);
        option("0", "back", "main menu", this::showMainMenu);
    }

    private void sysinfo() {
        startMenu("sysinfo", "SYSTEM");
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
        kv("shell", "darkOS sh 1.0");
        kv("display", dm.widthPixels + "x" + dm.heightPixels);
        kv("cpu", cpu);
        kv("memory", String.format(Locale.US, "%.1f / %.1f GB",
                (mi.totalMem - mi.availMem) / gb, mi.totalMem / gb));
        kv("storage", String.format(Locale.US, "%.0f GB free of %.0f GB",
                fs.getAvailableBytes() / gb, fs.getTotalBytes() / gb));
        kv("battery", batteryString());
        kv("apps", appCount() + " installed");
        SpannableStringBuilder bar = new SpannableStringBuilder("  ");
        int[] colors = {0xFF000000, RED, GREEN, YELLOW, 0xFF4F7CFF, 0xFFD050FF, CYAN, WHITE};
        for (int c : colors) {
            int s = bar.length();
            bar.append("███");
            bar.setSpan(new ForegroundColorSpan(c), s, bar.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        blank();
        line(bar, WHITE);
        option("0", "back", "main menu", this::showMainMenu);
    }

    private void kv(String k, String v) {
        String text = String.format(Locale.US, "  %-9s %s", k + ":", v);
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.setSpan(new ForegroundColorSpan(CYAN), 0, Math.min(text.length(), 12), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        line(sb, WHITE);
    }

    // ---------- typed commands ----------

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
            case "apps": case "ls": listApps(); return;
            case "open": case "run": case "launch": open(arg); return;
            case "settings": settingsMenu(); return;
            case "sysinfo": case "neofetch": case "uname": sysinfo(); return;
            case "theme": themeMenu(); return;
            case "iphone": case "ios": switchTo(MainActivity.MODE_IOS, "iphone"); return;
            case "windows": case "win": switchTo(MainActivity.MODE_WIN, "windows"); return;
            case "clear": case "cls": boot(); return;
            case "menu": case "home": case "back": showMainMenu(); return;
            case "date": line("  " + new SimpleDateFormat("EEEE d MMMM yyyy, HH:mm:ss", Locale.getDefault())
                    .format(new Date()), WHITE); return;
            case "battery": line("  " + batteryString(), WHITE); return;
            case "whoami": line("  a55", WHITE); return;
            case "echo": line("  " + arg, WHITE); return;
            case "wifi": openSetting("Wi-Fi", Settings.ACTION_WIFI_SETTINGS); return;
            case "bluetooth": case "bt": openSetting("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS); return;
            case "sudo": line("  [sudo] nice try.", YELLOW); return;
            case "exit": case "logout": line("  this is your home screen — there's nowhere to exit to :)", YELLOW); return;
            case "reboot": case "shutdown": line("  permission denied: hold the power button for that", RED); return;
            default:
                List<AppEntry> found = AppRepository.find(a.getApps(), c);
                if (found.size() == 1) {
                    launch(found.get(0));
                } else if (found.size() > 1) {
                    matches(found);
                } else {
                    line("  bash: " + cmd + ": command not found  (type 'help')", RED);
                }
        }
    }

    private void openSetting(String name, String action) {
        line("[  OK  ] opening " + name + " ...", DIM);
        AppActions.openSettings(a, action);
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
        line("  " + found.size() + " matches:", CYAN);
        int n = 0;
        menu.clear();
        menuName = "matches";
        for (AppEntry e : found) {
            n++;
            TextView t = line(String.format(Locale.US, "  %3d  %s", n, e.label), WHITE);
            t.setPadding(0, dp(5), 0, dp(5));
            t.setBackground(IconUtil.selectable(a));
            t.setOnClickListener(v -> launch(e));
            menu.put(String.valueOf(n), () -> launch(e));
        }
        option("0", "back", "main menu", this::showMainMenu);
    }

    // ---------- misc ----------

    private int appCount() {
        int n = 0;
        for (AppEntry e : a.getApps()) if (!e.special) n++;
        return n;
    }

    private String batteryString() {
        BatteryManager bm = (BatteryManager) a.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return "unknown";
        int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return pct + "%" + (bm.isCharging() ? " (charging)" : "");
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
        root.requestFocus();
    }

    private int dp(float v) {
        return IconUtil.dp(a, v);
    }

    @Override public View getView() { return root; }

    @Override public void tick() {}

    @Override
    public void onBack() {
        hideKeyboard();
        if (!busy && !"main".equals(menuName)) showMainMenu();
    }

    @Override
    public void onHomePressed() {
        hideKeyboard();
        boot();
    }

    @Override public int statusBarColor() { return Color.BLACK; }
}
