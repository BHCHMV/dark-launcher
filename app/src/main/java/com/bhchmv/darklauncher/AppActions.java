package com.bhchmv.darklauncher;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

final class AppActions {
    private AppActions() {}

    static void launch(MainActivity a, AppEntry e) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(e.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            a.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(a, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    static void appInfo(MainActivity a, AppEntry e) {
        start(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", e.packageName, null)));
    }

    static void uninstall(MainActivity a, AppEntry e) {
        start(a, new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", e.packageName, null)));
    }

    /** Opens a system settings screen, falling back to the main settings page. */
    static boolean openSettings(MainActivity a, String action) {
        if (start(a, new Intent(action))) return true;
        start(a, new Intent(Settings.ACTION_SETTINGS));
        return false;
    }

    static boolean start(MainActivity a, Intent i) {
        try {
            a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
