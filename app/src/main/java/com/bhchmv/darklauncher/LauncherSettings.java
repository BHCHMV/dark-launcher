package com.bhchmv.darklauncher;

import android.app.role.RoleManager;
import android.os.Build;
import android.provider.Settings;

final class LauncherSettings {
    static final int REQUEST_HOME_ROLE = 42;

    private LauncherSettings() {}

    static boolean isDefaultHome(MainActivity a) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = a.getSystemService(RoleManager.class);
            return rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME);
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    static void requestHome(MainActivity a) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = a.getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
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
