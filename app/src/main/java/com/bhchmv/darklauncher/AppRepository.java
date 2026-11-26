package com.bhchmv.darklauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AppRepository {
    private static final String PREFS = "darkos";
    private static final String KEY_ORDER = "app_order";

    private AppRepository() {}

    /**
     * Loads every launchable app. Apps keep the position they had last time;
     * newly installed apps are added at the end. Run off the main thread.
     */
    static List<AppEntry> load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
        String self = ctx.getPackageName();
        List<AppEntry> found = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || self.equals(ai.packageName)) continue;
            CharSequence label = ri.loadLabel(pm);
            android.graphics.drawable.Drawable icon;
            try {
                icon = ri.loadIcon(pm);
            } catch (Exception ex) {
                icon = null;
            }
            AppEntry e = new AppEntry(label == null ? ai.packageName : label.toString(),
                    ai.packageName, new ComponentName(ai.packageName, ai.name), icon);
            IconUtil.small(ctx, e); // render off the main thread
            found.add(e);
        }
        Collections.sort(found, (a, b) -> a.label.compareToIgnoreCase(b.label));

        Map<String, AppEntry> byKey = new LinkedHashMap<>();
        for (AppEntry e : found) byKey.put(e.key(), e);
        List<AppEntry> ordered = new ArrayList<>();
        String saved = prefs(ctx).getString(KEY_ORDER, "");
        if (!saved.isEmpty()) {
            for (String k : Arrays.asList(saved.split("\n"))) {
                AppEntry e = byKey.remove(k);
                if (e != null) ordered.add(e);
            }
        }
        ordered.addAll(byKey.values());
        saveOrder(ctx, ordered);
        return ordered;
    }

    static void saveOrder(Context ctx, List<AppEntry> apps) {
        StringBuilder sb = new StringBuilder();
        for (AppEntry e : apps) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.key());
        }
        prefs(ctx).edit().putString(KEY_ORDER, sb.toString()).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String signature(List<AppEntry> apps) {
        StringBuilder sb = new StringBuilder();
        for (AppEntry e : apps) sb.append(e.key()).append('|').append(e.label).append(';');
        return sb.toString();
    }

    /** Exact name match first, then "starts with", then "contains". */
    static List<AppEntry> find(List<AppEntry> apps, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<AppEntry> exact = new ArrayList<>(), starts = new ArrayList<>(), contains = new ArrayList<>();
        if (q.isEmpty()) return contains;
        for (AppEntry e : apps) {
            String l = e.label.toLowerCase(Locale.ROOT);
            if (l.equals(q)) exact.add(e);
            else if (l.startsWith(q)) starts.add(e);
            else if (l.contains(q)) contains.add(e);
        }
        if (!exact.isEmpty()) return exact;
        if (!starts.isEmpty()) return starts;
        return contains;
    }
}
