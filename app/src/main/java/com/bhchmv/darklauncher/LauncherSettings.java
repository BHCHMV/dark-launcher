package com.bhchmv.darklauncher;

import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

final class LauncherSettings {
    static final int REQUEST_HOME_ROLE = 42;

    private LauncherSettings() {}

    static void show(MainActivity a) {
        String mode = a.getMode();
        String[] items = {
                mark(mode, MainActivity.MODE_IOS) + "Look: iPhone style",
                mark(mode, MainActivity.MODE_WIN) + "Look: Windows style",
                mark(mode, MainActivity.MODE_TERM) + "Look: Linux terminal",
                "Set as default home screen",
                "Open phone settings",
        };
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("darkOS Settings")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: a.setMode(MainActivity.MODE_IOS); break;
                        case 1: a.setMode(MainActivity.MODE_WIN); break;
                        case 2: a.setMode(MainActivity.MODE_TERM); break;
                        case 3: requestHome(a); break;
                        default: AppActions.openSettings(a, Settings.ACTION_SETTINGS); break;
                    }
                })
                .show();
    }

    private static String mark(String current, String mode) {
        return current.equals(mode) ? "✓  " : "     ";
    }

    static boolean isDefaultHome(MainActivity a) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = a.getSystemService(RoleManager.class);
            return rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME);
        }
        return false;
    }

    static void requestHome(MainActivity a) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = a.getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Toast.makeText(a, "darkOS is already your home screen", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        AppActions.openSettings(a, Settings.ACTION_HOME_SETTINGS);
    }
}
