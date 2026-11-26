package com.bhchmv.darklauncher;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

final class AppActions {
    private AppActions() {}

    static void launch(MainActivity a, AppEntry e) {
        if (e.special) {
            LauncherSettings.show(a);
            return;
        }
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

    /** Opens a system settings screen, falling back to the main settings page. */
    static boolean openSettings(MainActivity a, String action) {
        try {
            a.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception ex) {
            try {
                a.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    static void showAppMenu(MainActivity a, AppEntry e) {
        if (e.special) {
            LauncherSettings.show(a);
            return;
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(e.label)
                .setItems(new String[]{"Open", "App info", "Uninstall"}, (d, which) -> {
                    Uri uri = Uri.fromParts("package", e.packageName, null);
                    try {
                        if (which == 0) {
                            launch(a, e);
                        } else if (which == 1) {
                            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } else {
                            a.startActivity(new Intent(Intent.ACTION_DELETE, uri)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        }
                    } catch (Exception ex) {
                        Toast.makeText(a, "Not available", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
}
