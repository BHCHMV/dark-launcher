package com.bhchmv.darklauncher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.Locale;

/** Phone controls the terminal can change directly, without opening Samsung's settings. */
final class Controls {
    private Controls() {}

    private static boolean torchOn;
    private static String torchId;
    private static boolean torchRegistered;

    static void init(Context ctx) {
        if (torchRegistered) return;
        torchRegistered = true;
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return;
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) {
                    torchId = id;
                    break;
                }
            }
            cm.registerTorchCallback(new CameraManager.TorchCallback() {
                @Override
                public void onTorchModeChanged(String cameraId, boolean enabled) {
                    if (cameraId.equals(torchId)) torchOn = enabled;
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (Exception ignored) {
        }
    }

    static String bar(int pct) {
        pct = Math.max(0, Math.min(100, pct));
        int n = Math.round(pct / 10f);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(i < n ? '#' : '-');
        return sb.append("] ").append(String.format(Locale.US, "%3d%%", pct)).toString();
    }

    // ----- permission for system settings (brightness, rotation, timeout) -----

    static boolean canWrite(Context ctx) {
        return Settings.System.canWrite(ctx);
    }

    static void requestWrite(MainActivity a) {
        AppActions.start(a, new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + a.getPackageName())));
    }

    // ----- brightness -----

    static int brightnessPct(Context ctx) {
        int v = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        return Math.round(v * 100f / 255f);
    }

    /** @return false if permission is still needed */
    static boolean changeBrightness(MainActivity a, int deltaPct) {
        if (!canWrite(a)) {
            requestWrite(a);
            return false;
        }
        ContentResolver cr = a.getContentResolver();
        int pct = Math.max(1, Math.min(100, brightnessPct(a) + deltaPct));
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, Math.round(pct * 255f / 100f));
        return true;
    }

    static boolean autoBrightness(Context ctx) {
        return Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
    }

    static boolean toggleAutoBrightness(MainActivity a) {
        if (!canWrite(a)) {
            requestWrite(a);
            return false;
        }
        Settings.System.putInt(a.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                autoBrightness(a) ? Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                        : Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        return true;
    }

    // ----- rotation & screen timeout -----

    static boolean autoRotate(Context ctx) {
        return Settings.System.getInt(ctx.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    static boolean toggleAutoRotate(MainActivity a) {
        if (!canWrite(a)) {
            requestWrite(a);
            return false;
        }
        Settings.System.putInt(a.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, autoRotate(a) ? 0 : 1);
        return true;
    }

    private static final int[] TIMEOUTS = {15_000, 30_000, 60_000, 120_000, 300_000, 600_000};

    static String timeout(Context ctx) {
        int ms = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 30_000);
        return ms < 60_000 ? (ms / 1000) + " sec" : (ms / 60_000) + " min";
    }

    static boolean cycleTimeout(MainActivity a) {
        if (!canWrite(a)) {
            requestWrite(a);
            return false;
        }
        int ms = Settings.System.getInt(a.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 30_000);
        int next = TIMEOUTS[0];
        for (int t : TIMEOUTS) {
            if (t > ms) {
                next = t;
                break;
            }
        }
        Settings.System.putInt(a.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, next);
        return true;
    }

    // ----- sound -----

    static int volumePct(Context ctx, int stream) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return 0;
        int max = am.getStreamMaxVolume(stream);
        return max == 0 ? 0 : Math.round(am.getStreamVolume(stream) * 100f / max);
    }

    static void changeVolume(Context ctx, int stream, boolean up) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            am.adjustStreamVolume(stream, up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
        } catch (SecurityException ignored) {
            // Lowering the ringer into Do Not Disturb needs extra permission.
        }
    }

    static String soundMode(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return "unknown";
        switch (am.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT: return "mute";
            case AudioManager.RINGER_MODE_VIBRATE: return "vibrate";
            default: return "sound";
        }
    }

    static void toggleSoundMode(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            am.setRingerMode(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL
                    ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_NORMAL);
        } catch (SecurityException ignored) {
        }
    }

    // ----- flashlight -----

    static boolean hasTorch() {
        return torchId != null;
    }

    static boolean torchOn() {
        return torchOn;
    }

    static void toggleTorch(Context ctx) {
        if (torchId == null) return;
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            cm.setTorchMode(torchId, !torchOn);
            torchOn = !torchOn;
        } catch (Exception ignored) {
        }
    }

    // ----- status -----

    static String network(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            if (caps == null) return "offline";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "mobile data";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "vpn";
            return "online";
        } catch (Exception e) {
            return "unknown";
        }
    }

    static String bluetooth(Context ctx) {
        try {
            BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
            if (ad == null) return "none";
            return ad.isEnabled() ? "on" : "off";
        } catch (Exception e) {
            return "unknown";
        }
    }

    static String battery(Context ctx) {
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return "?";
        int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return pct + "%" + (bm.isCharging() ? "+" : "");
    }

    /** Small system pop-up for Wi-Fi / internet (falls back to the full page). */
    static void openPanel(MainActivity a, String panelAction, String fallback) {
        if (Build.VERSION.SDK_INT >= 29 && AppActions.start(a, new Intent(panelAction))) return;
        AppActions.openSettings(a, fallback);
    }

    /** Opens Samsung's real notification shade. */
    @SuppressWarnings({"WrongConstant", "JavaReflectionMemberAccess"})
    static boolean openNotifications(Context ctx) {
        try {
            Object sbm = ctx.getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
