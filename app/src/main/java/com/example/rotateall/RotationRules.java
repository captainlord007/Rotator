package com.example.rotateall;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.view.Surface;

final class RotationRules {
    static final int INHERIT = -1;
    static final int AUTO = 0;
    static final int PORTRAIT = 1;
    static final int REVERSE_PORTRAIT = 2;
    static final int LANDSCAPE = 3;
    static final int REVERSE_LANDSCAPE = 4;

    static final String PREFERENCES = "rotate_all_preferences";
    static final String KEY_GLOBAL_MODE = "active_rotation_mode";
    static final String KEY_GLOBAL_BLOCKED = "global_blocked_rotations";
    static final String KEY_GLOBAL_ENABLED = "global_rotation_enabled";
    static final String KEY_ACTIVE = "rotation_service_active";
    static final String KEY_START_ERROR = "rotation_service_start_error";
    static final String KEY_PENDING_APPLY = "pending_rotation_apply";
    static final String KEY_APP_RULES_ACTIVE = "app_rules_active";

    private RotationRules() {
    }

    static int appMode(SharedPreferences preferences, String packageName) {
        return preferences.getInt(appModeKey(packageName), INHERIT);
    }

    static int appBlocked(SharedPreferences preferences, String packageName) {
        return preferences.getInt(appBlockedKey(packageName), 0);
    }

    static void saveAppRule(SharedPreferences preferences, String packageName, int mode, int blockedMask) {
        preferences.edit()
                .putInt(appModeKey(packageName), mode)
                .putInt(appBlockedKey(packageName), blockedMask)
                .apply();
    }

    static String appModeKey(String packageName) {
        return "app_mode:" + packageName;
    }

    static String appBlockedKey(String packageName) {
        return "app_blocked:" + packageName;
    }

    static int orientationForMode(int mode) {
        switch (mode) {
            case PORTRAIT:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case REVERSE_PORTRAIT:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            case LANDSCAPE:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case REVERSE_LANDSCAPE:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            case AUTO:
            default:
                return ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        }
    }

    static int modeForSurfaceRotation(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_90:
                return LANDSCAPE;
            case Surface.ROTATION_180:
                return REVERSE_PORTRAIT;
            case Surface.ROTATION_270:
                return REVERSE_LANDSCAPE;
            case Surface.ROTATION_0:
            default:
                return PORTRAIT;
        }
    }

    static int modeForSensorAngle(int degrees) {
        if (degrees < 0 || degrees >= 360) {
            return INHERIT;
        }
        if (degrees >= 45 && degrees < 135) {
            return REVERSE_LANDSCAPE;
        }
        if (degrees >= 135 && degrees < 225) {
            return REVERSE_PORTRAIT;
        }
        if (degrees >= 225 && degrees < 315) {
            return LANDSCAPE;
        }
        return PORTRAIT;
    }

    static int blockedBit(int mode) {
        if (mode < PORTRAIT || mode > REVERSE_LANDSCAPE) {
            return 0;
        }
        return 1 << (mode - 1);
    }

    static boolean isBlocked(int blockedMask, int mode) {
        return (blockedMask & blockedBit(mode)) != 0;
    }

    static int firstAllowedMode(int blockedMask) {
        int[] order = {PORTRAIT, LANDSCAPE, REVERSE_PORTRAIT, REVERSE_LANDSCAPE};
        for (int mode : order) {
            if (!isBlocked(blockedMask, mode)) {
                return mode;
            }
        }
        return PORTRAIT;
    }

    static int labelResource(int mode) {
        switch (mode) {
            case PORTRAIT:
                return R.string.mode_portrait;
            case REVERSE_PORTRAIT:
                return R.string.mode_reverse_portrait;
            case LANDSCAPE:
                return R.string.mode_landscape;
            case REVERSE_LANDSCAPE:
                return R.string.mode_reverse_landscape;
            case AUTO:
            default:
                return R.string.mode_auto;
        }
    }
}
