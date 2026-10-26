package com.example.rotateall;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import java.util.HashSet;
import java.util.Set;

public class RotationServiceV2 extends Service {
    static final String ACTION_APPLY = "com.example.rotateall.APPLY_ROTATION";
    private static final String ACTION_STOP = "com.example.rotateall.STOP_ROTATION";
    private static final String CHANNEL_ID = "rotator_rotation";
    private static final int NOTIFICATION_ID = 6104;
    private static final long POLL_INTERVAL_MS = 800L;
    private static final int EVENT_WINDOW_MS = 8000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> handledEvents = new HashSet<>();
    private SharedPreferences preferences;
    private WindowManager windowManager;
    private View orientationOverlay;
    private OrientationEventListener orientationListener;
    private UsageStatsManager usageStatsManager;
    private String foregroundPackage;
    private long lastEventQueryTime;
    private int activeMode = RotationRules.AUTO;
    private int activeBlockedMask;
    private int lastAllowedMode = RotationRules.PORTRAIT;
    private int lastRequestedOrientation = Integer.MIN_VALUE;
    private String activePackageRule;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            readForegroundApp();
            applyCurrentProfile();
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(RotationRules.PREFERENCES, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        usageStatsManager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        lastEventQueryTime = System.currentTimeMillis() - 60_000L;
        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (activeMode != RotationRules.AUTO || activeBlockedMask == 0) {
                    return;
                }
                int sensedMode = RotationRules.modeForSensorAngle(orientation);
                if (sensedMode != RotationRules.INHERIT
                        && !RotationRules.isBlocked(activeBlockedMask, sensedMode)) {
                    lastAllowedMode = sensedMode;
                    handler.post(RotationServiceV2.this::applyCurrentProfile);
                }
            }
        };
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            removeOrientationOverlay();
            setInactive(false);
            preferences.edit().putBoolean(RotationRules.KEY_GLOBAL_ENABLED, false).apply();
            stopForegroundNotification();
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, createNotification());
        if (!Settings.canDrawOverlays(this)) {
            setInactive(true);
            stopForegroundNotification();
            stopSelf();
            return START_NOT_STICKY;
        }

        preferences.edit()
                .putBoolean(RotationRules.KEY_ACTIVE, true)
                .putBoolean(RotationRules.KEY_START_ERROR, false)
                .apply();
        try {
            applyCurrentProfile();
        } catch (RuntimeException exception) {
            removeOrientationOverlay();
            setInactive(true);
            stopForegroundNotification();
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(pollRunnable);
        if (orientationListener != null) {
            orientationListener.disable();
        }
        removeOrientationOverlay();
        if (preferences != null && preferences.getBoolean(RotationRules.KEY_ACTIVE, false)) {
            setInactive(false);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void readForegroundApp() {
        if (!hasUsageAccess() || usageStatsManager == null) {
            foregroundPackage = null;
            return;
        }
        long now = System.currentTimeMillis();
        long begin = Math.max(lastEventQueryTime - 1500L, now - EVENT_WINDOW_MS);
        UsageEvents events = usageStatsManager.queryEvents(begin, now);
        if (events != null) {
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                String packageName = event.getPackageName();
                if (packageName == null) {
                    continue;
                }
                String key = event.getTimeStamp() + ":" + event.getEventType() + ":" + packageName;
                if (!handledEvents.add(key)) {
                    continue;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                        foregroundPackage = packageName;
                    } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED
                            && packageName.equals(foregroundPackage)) {
                        foregroundPackage = null;
                    }
                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    foregroundPackage = packageName;
                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND
                        && packageName.equals(foregroundPackage)) {
                    foregroundPackage = null;
                }
            }
        }
        lastEventQueryTime = now;
        if (handledEvents.size() > 512) {
            handledEvents.clear();
        }
    }

    private void applyCurrentProfile() {
        if (preferences == null || !Settings.canDrawOverlays(this)) {
            return;
        }
        boolean globalEnabled = preferences.getBoolean(RotationRules.KEY_GLOBAL_ENABLED, false);
        int mode = preferences.getInt(RotationRules.KEY_GLOBAL_MODE, RotationRules.AUTO);
        int blockedMask = preferences.getInt(RotationRules.KEY_GLOBAL_BLOCKED, 0);
        String matchedPackage = null;
        if (foregroundPackage != null) {
            int appMode = RotationRules.appMode(preferences, foregroundPackage);
            if (appMode != RotationRules.INHERIT) {
                mode = appMode;
                blockedMask = RotationRules.appBlocked(preferences, foregroundPackage);
                matchedPackage = foregroundPackage;
            }
        }

        if (!globalEnabled && matchedPackage == null) {
            activeMode = RotationRules.INHERIT;
            activeBlockedMask = 0;
            activePackageRule = null;
            removeOrientationOverlay();
            return;
        }

        if (mode != activeMode || blockedMask != activeBlockedMask
                || !same(activePackageRule, matchedPackage)) {
            activeMode = mode;
            activeBlockedMask = blockedMask;
            activePackageRule = matchedPackage;
            if (mode == RotationRules.AUTO && blockedMask != 0) {
                int displayMode = RotationRules.modeForSurfaceRotation(
                        windowManager.getDefaultDisplay().getRotation());
                lastAllowedMode = RotationRules.isBlocked(blockedMask, displayMode)
                        ? RotationRules.firstAllowedMode(blockedMask)
                        : displayMode;
            }
        }

        int requested;
        if (mode == RotationRules.AUTO && blockedMask == 0xF) {
            requested = ActivityInfo.SCREEN_ORIENTATION_LOCKED;
        } else if (mode == RotationRules.AUTO && blockedMask != 0) {
            if (RotationRules.isBlocked(blockedMask, lastAllowedMode)) {
                lastAllowedMode = RotationRules.firstAllowedMode(blockedMask);
            }
            requested = RotationRules.orientationForMode(lastAllowedMode);
        } else {
            requested = RotationRules.orientationForMode(mode);
        }
        if (requested != lastRequestedOrientation) {
            try {
                applyOrientationOverlay(requested);
                lastRequestedOrientation = requested;
            } catch (RuntimeException exception) {
                removeOrientationOverlay();
                setInactive(true);
                stopForegroundNotification();
                stopSelf();
            }
        }
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private void applyOrientationOverlay(int requestedOrientation) {
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
        params.screenOrientation = requestedOrientation;
        params.setTitle("Rotator orientation control");
        if (orientationOverlay == null) {
            orientationOverlay = new View(this);
            orientationOverlay.setAlpha(0f);
            orientationOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            windowManager.addView(orientationOverlay, params);
        } else {
            windowManager.updateViewLayout(orientationOverlay, params);
        }
    }

    private Notification createNotification() {
        ensureNotificationChannel();
        Intent stopIntent = new Intent(this, RotationServiceV2.class).setAction(ACTION_STOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent stopAction = PendingIntent.getService(this, NOTIFICATION_ID, stopIntent, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        int statusText = preferences.getBoolean(RotationRules.KEY_GLOBAL_ENABLED, false)
                ? R.string.rotation_active : R.string.app_rules_active;
        return builder.setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(statusText))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.stop_rotation), stopAction)
                .build();
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_description));
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
    }

    private void removeOrientationOverlay() {
        if (orientationOverlay != null && windowManager != null) {
            try {
                windowManager.removeView(orientationOverlay);
            } catch (IllegalArgumentException ignored) {
                // The system may already have removed the overlay.
            }
            orientationOverlay = null;
            lastRequestedOrientation = Integer.MIN_VALUE;
        }
    }

    private void setInactive(boolean startError) {
        if (preferences != null) {
            preferences.edit()
                    .putBoolean(RotationRules.KEY_ACTIVE, false)
                    .putBoolean(RotationRules.KEY_START_ERROR, startError)
                    .apply();
        }
    }

    private void stopForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    private boolean same(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }
}
