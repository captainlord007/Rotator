package com.example.rotateall;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Surface;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int MODE_NONE = -1;
    private static final String KEY_SELECTED_MODE = "selected_rotation_mode";
    private static final String KEY_PENDING_MODE = "pending_rotation_mode";

    private TextView statusTitle;
    private TextView statusMessage;
    private TextView rotationValue;
    private RadioGroup modeOptions;
    private Button applyButton;
    private Button stopButton;
    private SharedPreferences preferences;
    private int selectedMode = RotationOverlayService.MODE_AUTO;
    private int pendingMode = MODE_NONE;
    private boolean updatingSelection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTitle = findViewById(R.id.statusTitle);
        statusMessage = findViewById(R.id.statusMessage);
        rotationValue = findViewById(R.id.rotationValue);
        modeOptions = findViewById(R.id.modeOptions);
        applyButton = findViewById(R.id.applyButton);
        stopButton = findViewById(R.id.stopButton);
        preferences = getSharedPreferences(RotationOverlayService.PREFERENCES, MODE_PRIVATE);

        if (savedInstanceState != null) {
            selectedMode = savedInstanceState.getInt(
                    KEY_SELECTED_MODE,
                    preferences.getInt(RotationOverlayService.KEY_ACTIVE_MODE, RotationOverlayService.MODE_AUTO)
            );
            pendingMode = savedInstanceState.getInt(KEY_PENDING_MODE, MODE_NONE);
        } else {
            selectedMode = preferences.getInt(
                    RotationOverlayService.KEY_ACTIVE_MODE,
                    RotationOverlayService.MODE_AUTO
            );
        }
        setSelectedMode(selectedMode);

        if (preferences.getBoolean(RotationOverlayService.KEY_ACTIVE, false)) {
            setRequestedOrientation(activityOrientationForMode(selectedMode));
        }

        modeOptions.setOnCheckedChangeListener((group, checkedId) -> {
            if (updatingSelection) {
                return;
            }
            int mode = modeForRadioId(checkedId);
            if (mode != MODE_NONE) {
                selectedMode = mode;
                preferences.edit().putInt(KEY_SELECTED_MODE, mode).apply();
                refreshScreen();
            }
        });
        applyButton.setOnClickListener(view -> requestOrApplySelectedMode());
        stopButton.setOnClickListener(view -> stopForcedRotation());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingMode != MODE_NONE) {
            int mode = pendingMode;
            pendingMode = MODE_NONE;
            if (Settings.canDrawOverlays(this)) {
                startRotationService(mode);
            } else {
                refreshScreen();
            }
            return;
        }

        if (preferences.getBoolean(RotationOverlayService.KEY_ACTIVE, false)) {
            selectedMode = preferences.getInt(RotationOverlayService.KEY_ACTIVE_MODE, selectedMode);
            setRequestedOrientation(activityOrientationForMode(selectedMode));
        } else {
            selectedMode = preferences.getInt(KEY_SELECTED_MODE, selectedMode);
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
        setSelectedMode(selectedMode);
        refreshScreen();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_SELECTED_MODE, selectedMode);
        outState.putInt(KEY_PENDING_MODE, pendingMode);
        super.onSaveInstanceState(outState);
    }

    private void requestOrApplySelectedMode() {
        if (Settings.canDrawOverlays(this)) {
            startRotationService(selectedMode);
            return;
        }

        pendingMode = selectedMode;
        Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        permissionIntent.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(permissionIntent);
        } catch (ActivityNotFoundException exception) {
            pendingMode = MODE_NONE;
            statusTitle.setText(R.string.permission_title);
            statusMessage.setText(R.string.permission_fallback);
        }
    }

    private void startRotationService(int mode) {
        preferences.edit()
                .putInt(KEY_SELECTED_MODE, mode)
                .putInt(RotationOverlayService.KEY_ACTIVE_MODE, mode)
                .putBoolean(RotationOverlayService.KEY_ACTIVE, true)
                .putBoolean(RotationOverlayService.KEY_START_ERROR, false)
                .apply();
        selectedMode = mode;
        setSelectedMode(mode);
        setRequestedOrientation(activityOrientationForMode(mode));

        Intent serviceIntent = new Intent(this, RotationOverlayService.class)
                .setAction(RotationOverlayService.ACTION_APPLY)
                .putExtra(RotationOverlayService.EXTRA_MODE, mode);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            refreshScreen();
        } catch (RuntimeException exception) {
            preferences.edit()
                    .putBoolean(RotationOverlayService.KEY_ACTIVE, false)
                    .putBoolean(RotationOverlayService.KEY_START_ERROR, true)
                    .apply();
            refreshScreen();
        }
    }

    private void stopForcedRotation() {
        stopService(new Intent(this, RotationOverlayService.class));
        preferences.edit().putBoolean(RotationOverlayService.KEY_ACTIVE, false).apply();
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        refreshScreen();
    }

    private void refreshScreen() {
        boolean hasOverlayAccess = Settings.canDrawOverlays(this);
        boolean active = preferences.getBoolean(RotationOverlayService.KEY_ACTIVE, false);
        boolean startError = preferences.getBoolean(RotationOverlayService.KEY_START_ERROR, false);
        int activeMode = preferences.getInt(RotationOverlayService.KEY_ACTIVE_MODE, selectedMode);
        String label = getString(labelForMode(selectedMode));

        if (!hasOverlayAccess) {
            statusTitle.setText(R.string.permission_title);
            statusMessage.setText(R.string.permission_message);
        } else if (startError) {
            statusTitle.setText(R.string.could_not_start_title);
            statusMessage.setText(R.string.could_not_start_message);
        } else if (active && activeMode == selectedMode) {
            statusTitle.setText(getString(R.string.active_mode_title, label));
            statusMessage.setText(descriptionForMode(selectedMode));
        } else if (active) {
            statusTitle.setText(getString(R.string.selected_mode_title, label));
            statusMessage.setText(R.string.update_mode_message);
        } else {
            statusTitle.setText(getString(R.string.selected_mode_title, label));
            statusMessage.setText(descriptionForMode(selectedMode));
        }

        applyButton.setText(hasOverlayAccess
                ? R.string.apply_button
                : R.string.grant_and_apply_button);
        stopButton.setVisibility(active ? Button.VISIBLE : Button.GONE);
        refreshRotationValue();
    }

    private void setSelectedMode(int mode) {
        selectedMode = mode;
        updatingSelection = true;
        modeOptions.check(radioIdForMode(mode));
        updatingSelection = false;
    }

    private int activityOrientationForMode(int mode) {
        switch (mode) {
            case RotationOverlayService.MODE_PORTRAIT_UP:
                return android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case RotationOverlayService.MODE_PORTRAIT_DOWN:
                return android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            case RotationOverlayService.MODE_LANDSCAPE_LEFT:
                return android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case RotationOverlayService.MODE_LANDSCAPE_RIGHT:
                return android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            case RotationOverlayService.MODE_AUTO:
            default:
                return android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        }
    }

    private int radioIdForMode(int mode) {
        switch (mode) {
            case RotationOverlayService.MODE_PORTRAIT_UP:
                return R.id.optionPortraitUp;
            case RotationOverlayService.MODE_PORTRAIT_DOWN:
                return R.id.optionPortraitDown;
            case RotationOverlayService.MODE_LANDSCAPE_LEFT:
                return R.id.optionLandscapeLeft;
            case RotationOverlayService.MODE_LANDSCAPE_RIGHT:
                return R.id.optionLandscapeRight;
            case RotationOverlayService.MODE_AUTO:
            default:
                return R.id.optionAuto;
        }
    }

    private int modeForRadioId(int radioId) {
        if (radioId == R.id.optionPortraitUp) {
            return RotationOverlayService.MODE_PORTRAIT_UP;
        } else if (radioId == R.id.optionPortraitDown) {
            return RotationOverlayService.MODE_PORTRAIT_DOWN;
        } else if (radioId == R.id.optionLandscapeLeft) {
            return RotationOverlayService.MODE_LANDSCAPE_LEFT;
        } else if (radioId == R.id.optionLandscapeRight) {
            return RotationOverlayService.MODE_LANDSCAPE_RIGHT;
        } else if (radioId == R.id.optionAuto) {
            return RotationOverlayService.MODE_AUTO;
        }
        return MODE_NONE;
    }

    private int labelForMode(int mode) {
        switch (mode) {
            case RotationOverlayService.MODE_PORTRAIT_UP:
                return R.string.mode_portrait_up;
            case RotationOverlayService.MODE_PORTRAIT_DOWN:
                return R.string.mode_portrait_down;
            case RotationOverlayService.MODE_LANDSCAPE_LEFT:
                return R.string.mode_landscape_left;
            case RotationOverlayService.MODE_LANDSCAPE_RIGHT:
                return R.string.mode_landscape_right;
            case RotationOverlayService.MODE_AUTO:
            default:
                return R.string.mode_auto;
        }
    }

    private int descriptionForMode(int mode) {
        switch (mode) {
            case RotationOverlayService.MODE_PORTRAIT_UP:
            case RotationOverlayService.MODE_PORTRAIT_DOWN:
            case RotationOverlayService.MODE_LANDSCAPE_LEFT:
            case RotationOverlayService.MODE_LANDSCAPE_RIGHT:
                return R.string.fixed_mode_description;
            case RotationOverlayService.MODE_AUTO:
            default:
                return R.string.auto_mode_description;
        }
    }

    private void refreshRotationValue() {
        int degrees;
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                degrees = 0;
        }
        rotationValue.setText(getString(R.string.current_rotation, degrees));
    }
}
