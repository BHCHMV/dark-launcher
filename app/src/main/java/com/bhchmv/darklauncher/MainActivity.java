package com.bhchmv.darklauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final String MODE_IOS = "ios";
    static final String MODE_WIN = "win";
    static final String MODE_TERM = "term";

    private static final String PREFS = "darkos";
    private static final String KEY_MODE = "mode";
    private static final String KEY_WELCOMED = "welcomed";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private FrameLayout root;
    private List<AppEntry> apps = new ArrayList<>();
    private String appsSignature = "";
    private HomeMode current;
    private String mode;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (current != null) current.tick();
            handler.postDelayed(this, 10_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        mode = prefs().getString(KEY_MODE, MODE_IOS);
        reloadApps(true);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void reloadApps(boolean force) {
        executor.execute(() -> {
            List<AppEntry> list = AppRepository.load(getApplicationContext());
            String sig = AppRepository.signature(list);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (force || !sig.equals(appsSignature)) {
                    apps = list;
                    appsSignature = sig;
                    showMode();
                    maybeWelcome();
                }
            });
        });
    }

    private void showMode() {
        root.removeAllViews();
        if (MODE_WIN.equals(mode)) current = new WindowsHome(this);
        else if (MODE_TERM.equals(mode)) current = new TerminalHome(this);
        else current = new IosHome(this);
        root.addView(current.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Window w = getWindow();
        w.setStatusBarColor(current.statusBarColor());
        w.setNavigationBarColor(Color.BLACK);
    }

    private void maybeWelcome() {
        if (prefs().getBoolean(KEY_WELCOMED, false)) return;
        prefs().edit().putBoolean(KEY_WELCOMED, true).apply();
        if (LauncherSettings.isDefaultHome(this)) return;
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Welcome to darkOS")
                .setMessage("Make darkOS your home screen so it shows when you press Home?\n\n"
                        + "You can switch between iPhone, Windows and terminal looks any time from "
                        + "the \"darkOS Settings\" app or by long-pressing the clock.")
                .setPositiveButton("Set as home", (d, w) -> LauncherSettings.requestHome(this))
                .setNegativeButton("Later", null)
                .show();
    }

    void setMode(String m) {
        mode = m;
        prefs().edit().putString(KEY_MODE, m).apply();
        showMode();
    }

    String getMode() {
        return mode;
    }

    List<AppEntry> getApps() {
        return apps;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!appsSignature.isEmpty()) reloadApps(false);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (current != null) current.onHomePressed();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // This is the home screen, so Back never closes it.
        if (current != null) current.onBack();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LauncherSettings.REQUEST_HOME_ROLE && resultCode != RESULT_OK
                && !LauncherSettings.isDefaultHome(this)) {
            AppActions.openSettings(this, Settings.ACTION_HOME_SETTINGS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}
