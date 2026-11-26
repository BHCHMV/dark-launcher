package com.bhchmv.darklauncher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The user's own named tabs, each holding up to 10 apps. */
final class TabStore {
    static final int MAX_APPS = 10;
    static final int MAX_TABS = 6;
    static final int MAX_NAME = 12;

    private static final String PREFS = "darkos";
    private static final String KEY = "custom_tabs";

    static final class Tab {
        String name;
        final List<String> apps = new ArrayList<>();

        Tab(String name) {
            this.name = name;
        }
    }

    private final Context ctx;
    final List<Tab> tabs = new ArrayList<>();

    TabStore(Context ctx) {
        this.ctx = ctx;
        try {
            JSONArray arr = new JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Tab t = new Tab(o.optString("name", "tab"));
                JSONArray apps = o.optJSONArray("apps");
                if (apps != null) for (int j = 0; j < apps.length() && j < MAX_APPS; j++) t.apps.add(apps.getString(j));
                tabs.add(t);
            }
        } catch (Exception ignored) {
        }
    }

    void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Tab t : tabs) {
                JSONObject o = new JSONObject();
                o.put("name", t.name);
                JSONArray apps = new JSONArray();
                for (String k : t.apps) apps.put(k);
                o.put("apps", apps);
                arr.put(o);
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static String cleanName(String raw) {
        String s = raw.trim().replaceAll("\\s+", "-").toLowerCase();
        if (s.length() > MAX_NAME) s = s.substring(0, MAX_NAME);
        return s;
    }
}
