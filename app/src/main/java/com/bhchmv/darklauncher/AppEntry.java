package com.bhchmv.darklauncher;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

/** An installed app (or the built-in "darkOS Settings" entry). */
final class AppEntry {
    final String label;
    final String packageName;
    final ComponentName component;
    final Drawable icon;
    final boolean special;

    Bitmap roundIcon;
    Bitmap glyphIcon;

    AppEntry(String label, String packageName, ComponentName component, Drawable icon, boolean special) {
        this.label = label;
        this.packageName = packageName;
        this.component = component;
        this.icon = icon;
        this.special = special;
    }

    static AppEntry settingsEntry() {
        return new AppEntry("darkOS Settings", "", null, null, true);
    }
}
