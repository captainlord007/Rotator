package com.example.rotateall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class RotationOverlayService extends Service {
    static final int MODE_AUTO = 0;
    static final int MODE_PORTRAIT_UP = 1;
    static final int MODE_PORTRAIT_DOWN = 2;
    static final int MODE_LANDSCAPE_LEFT = 3;
    static final int MODE_LANDSCAPE_RIGHT = 4;

    static final String PREFERENCES = "rotate_all_preferences";
    static final String KEY_ACTIVE = "rotation_service_active";
    static final String KEY_ACTIVE_MODE = "active_rotation_mode";
    static final String KEY_START_ERROR = "rotation_service_start_error";
    static final String ACTION_APPLY = "com.example.rotateall.APPLY_ROTATION";
    static final String ACTION_STOP = "com.example.rotateall.STOP_ROTATION";
    static final String EXTRA_MODE = "rotation_mode";

    private static final String CHANNEL_ID = "rotate_all_rotation";
    private static final int NOTIFICATION_ID = 6104;

    private WindowManager windowManager;
    private View orientationOverlay;
    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            removeOrientationOverlay();
            setInactive(false);
            stopForegroundServiceNotification();
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        int mode = intent == null
                ? preferences.getInt(KEY_ACTIVE_MODE, MODE_AUTO)
                : intent.getIntExtra(EXTRA_MODE, MODE_AUTO);

        startForeground(NOTIFICATION_ID, createNotification(mode));
        if (!Settings.canDrawOverlays(this)) {
            setInactive(true);
            stopForegroundServiceNotification();
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            applyOrientationOverlay(mode);
            preferences.edit()
                    .putBoolean(KEY_ACTIVE, true)
                    .putInt(KEY_ACTIVE_MODE, mode)
                    .putBoolean(KEY_START_ERROR, false)
                    .apply();
        } catch (RuntimeException exception) {
            removeOrientationOverlay();
            setInactive(true);
            stopForegroundServiceNotification();
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        removeOrientationOverlay();
        if (preferences != null && preferences.getBoolean(KEY_ACTIVE, false)) {
            setInactive(false);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void applyOrientationOverlay(int mode) {
        int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1,
                1,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.alpha = 0f;
        params.screenOrientation = orientationForMode(mode);
        params.setTitle("Rotate All orientation control");

        if (orientationOverlay == null) {
            orientationOverlay = new View(this);
            orientationOverlay.setAlpha(0f);
            orientationOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            windowManager.addView(orientationOverlay, params);
        } else {
            windowManager.updateViewLayout(orientationOverlay, params);
        }
    }

    private int orientationForMode(int mode) {
        switch (mode) {
            case MODE_PORTRAIT_UP:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case MODE_PORTRAIT_DOWN:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            case MODE_LANDSCAPE_LEFT:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case MODE_LANDSCAPE_RIGHT:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            case MODE_AUTO:
            default:
                return ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        }
    }

    private Notification createNotification(int mode) {
        ensureNotificationChannel();
        Intent stopIntent = new Intent(this, RotationOverlayService.class).setAction(ACTION_STOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                NOTIFICATION_ID,
                stopIntent,
                pendingFlags
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(labelForMode(mode)))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.stop_button), stopPendingIntent);
        return builder.build();
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private int labelForMode(int mode) {
        switch (mode) {
            case MODE_PORTRAIT_UP:
                return R.string.mode_portrait_up;
            case MODE_PORTRAIT_DOWN:
                return R.string.mode_portrait_down;
            case MODE_LANDSCAPE_LEFT:
                return R.string.mode_landscape_left;
            case MODE_LANDSCAPE_RIGHT:
                return R.string.mode_landscape_right;
            case MODE_AUTO:
            default:
                return R.string.mode_auto;
        }
    }

    private void removeOrientationOverlay() {
        if (orientationOverlay != null && windowManager != null) {
            try {
                windowManager.removeView(orientationOverlay);
            } catch (IllegalArgumentException ignored) {
                // The window may already have been removed by the system.
            }
            orientationOverlay = null;
        }
    }

    private void setInactive(boolean startError) {
        if (preferences != null) {
            preferences.edit()
                    .putBoolean(KEY_ACTIVE, false)
                    .putBoolean(KEY_START_ERROR, startError)
                    .apply();
        }
    }

    private void stopForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }
}
