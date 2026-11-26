package com.bhchmv.darklauncher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private FrameLayout root;
    private List<AppEntry> apps = new ArrayList<>();
    private String appsSignature = "";
    private TerminalHome term;
    private long startedAt;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (term != null) term.tick();
            handler.postDelayed(this, 10_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        hideStatusBar();
        Controls.init(this);
        reloadApps();
    }

    private void reloadApps() {
        executor.execute(() -> {
            List<AppEntry> list = AppRepository.load(getApplicationContext());
            String sig = AppRepository.signature(list);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (term == null) {
                    apps = list;
                    appsSignature = sig;
                    term = new TerminalHome(this);
                    root.addView(term.getView(), new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                } else if (!sig.equals(appsSignature)) {
                    apps = list;
                    appsSignature = sig;
                    term.onAppsChanged();
                }
            });
        });
    }

    List<AppEntry> getApps() {
        return apps;
    }

    void moveApp(AppEntry e, int to) {
        List<AppEntry> list = new ArrayList<>(apps);
        int from = list.indexOf(e);
        if (from < 0) return;
        list.remove(from);
        list.add(Math.max(0, Math.min(list.size(), to)), e);
        setOrder(list);
    }

    void sortApps() {
        List<AppEntry> list = new ArrayList<>(apps);
        Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
        setOrder(list);
    }

    private void setOrder(List<AppEntry> list) {
        apps = list;
        appsSignature = AppRepository.signature(list);
        AppRepository.saveOrder(this, list);
    }

    @Override
    protected void onStart() {
        super.onStart();
        startedAt = SystemClock.uptimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideStatusBar();
        if (term != null) {
            reloadApps();
            term.onResume();
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    /** Hides the phone's own time/battery bar while the launcher is showing. */
    @SuppressWarnings("deprecation")
    private void hideStatusBar() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideStatusBar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Coming back from another app keeps everything where it was.
        // Pressing Home while already here goes back to the app list.
        boolean returningFromApp = SystemClock.uptimeMillis() - startedAt < 1500;
        if (term != null && !returningFromApp) term.onHomePressed();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (term != null) term.onBack();
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
