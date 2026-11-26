package com.bhchmv.darklauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class AppRepository {
    private AppRepository() {}

    /** Loads every launchable app, sorted by name. Run off the main thread. */
    static List<AppEntry> load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
        String self = ctx.getPackageName();
        List<AppEntry> out = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || self.equals(ai.packageName)) continue;
            CharSequence label = ri.loadLabel(pm);
            Drawable icon;
            try {
                icon = ri.loadIcon(pm);
            } catch (Exception e) {
                icon = null;
            }
            out.add(new AppEntry(label == null ? ai.packageName : label.toString(),
                    ai.packageName, new ComponentName(ai.packageName, ai.name), icon, false));
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        out.add(AppEntry.settingsEntry());
        return out;
    }

    static String signature(List<AppEntry> apps) {
        StringBuilder sb = new StringBuilder();
        for (AppEntry e : apps) {
            if (e.component != null) sb.append(e.component.flattenToShortString());
            sb.append('|').append(e.label).append(';');
        }
        return sb.toString();
    }

    private static final String[][] FAVORITES = {
            {"com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer"},
            {"com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms"},
            {"com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox"},
            {"com.sec.android.app.camera", "com.google.android.GoogleCamera", "com.android.camera2"},
    };

    /** Phone, messages, browser, camera — for the dock / big tiles. */
    static List<AppEntry> favorites(List<AppEntry> apps) {
        List<AppEntry> favs = new ArrayList<>();
        for (String[] group : FAVORITES) {
            AppEntry found = null;
            for (String pkg : group) {
                for (AppEntry e : apps) {
                    if (!e.special && pkg.equals(e.packageName) && !favs.contains(e)) {
                        found = e;
                        break;
                    }
                }
                if (found != null) break;
            }
            if (found != null) favs.add(found);
        }
        for (AppEntry e : apps) {
            if (favs.size() >= 4) break;
            if (!e.special && !favs.contains(e)) favs.add(e);
        }
        return favs;
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
