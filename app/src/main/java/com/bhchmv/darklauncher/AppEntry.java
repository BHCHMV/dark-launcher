package com.bhchmv.darklauncher;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

/** An installed, launchable app. */
final class AppEntry {
    final String label;
    final String packageName;
    final ComponentName component;
    final Drawable icon;

    Bitmap smallIcon;
    Bitmap bigIcon;

    AppEntry(String label, String packageName, ComponentName component, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.component = component;
        this.icon = icon;
    }

    String key() {
        return component.flattenToShortString();
    }
}
