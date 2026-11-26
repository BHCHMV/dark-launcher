package com.bhchmv.darklauncher;

import android.content.ComponentName;

/** An installed, launchable app. */
final class AppEntry {
    final String label;
    final String packageName;
    final ComponentName component;

    AppEntry(String label, String packageName, ComponentName component) {
        this.label = label;
        this.packageName = packageName;
        this.component = component;
    }

    String key() {
        return component.flattenToShortString();
    }
}
