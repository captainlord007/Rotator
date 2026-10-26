package com.example.rotateall;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class HomeActivity extends Activity {
    private static final String STATE_SELECTED_MODE = "draft_selected_mode";
    private static final String STATE_BLOCKED_MASK = "draft_blocked_mask";
    private SharedPreferences preferences;
    private TextView status;
    private Button stopButton;
    private LinearLayout autoBlockedSection;
    private final View[] modeCards = new View[5];
    private final CheckBox[] blockedChecks = new CheckBox[5];
    private int selectedMode = RotationRules.AUTO;
    private int blockedMask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeTools.apply(this);
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(RotationRules.PREFERENCES, MODE_PRIVATE);
        if (savedInstanceState == null) {
            selectedMode = preferences.getInt(RotationRules.KEY_GLOBAL_MODE, RotationRules.AUTO);
            blockedMask = preferences.getInt(RotationRules.KEY_GLOBAL_BLOCKED, 0);
        } else {
            selectedMode = savedInstanceState.getInt(STATE_SELECTED_MODE,
                    preferences.getInt(RotationRules.KEY_GLOBAL_MODE, RotationRules.AUTO));
            blockedMask = savedInstanceState.getInt(STATE_BLOCKED_MASK,
                    preferences.getInt(RotationRules.KEY_GLOBAL_BLOCKED, 0));
        }
        setContentView(buildScreen());
        updateSelection();
        if (preferences.getBoolean(RotationRules.KEY_ACTIVE, false)
                && Settings.canDrawOverlays(this)) {
            startRotationService();
        }
        updateStatus();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_SELECTED_MODE, selectedMode);
        outState.putInt(STATE_BLOCKED_MASK, blockedMask);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preferences.getBoolean(RotationRules.KEY_PENDING_APPLY, false)) {
            preferences.edit().putBoolean(RotationRules.KEY_PENDING_APPLY, false).apply();
            if (Settings.canDrawOverlays(this)) {
                startRotationService();
            } else {
                preferences.edit().putBoolean(RotationRules.KEY_GLOBAL_ENABLED, false).apply();
            }
        }
        if (status != null) {
            updateStatus();
        }
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ThemeTools.color(this, R.attr.screenBackground));
        WindowInsetsHelper.apply(scroll);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ThemeTools.dp(this, 20), ThemeTools.dp(this, 10),
                ThemeTools.dp(this, 20), ThemeTools.dp(this, 28));
        scroll.addView(content);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(ThemeTools.color(this, R.attr.inkColor));
        back.setContentDescription(getString(R.string.back));
        back.setBackground(ThemeTools.roundedBackground(this,
                ThemeTools.color(this, R.attr.panelColor), ThemeTools.color(this, R.attr.panelColor), 0));
        back.setOnClickListener(view -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(
                ThemeTools.dp(this, 44), ThemeTools.dp(this, 44)));
        TextView title = text(getString(R.string.whole_phone), 20, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = ThemeTools.dp(this, 10);
        toolbar.addView(title, titleParams);
        content.addView(toolbar);

        status = text(getString(R.string.rotation_inactive), 14, false);
        status.setTextColor(ThemeTools.color(this, R.attr.mutedInk));
        addTop(content, status, 5);

        TextView rotationTitle = text(getString(R.string.rotation_heading), 16, true);
        addTop(content, rotationTitle, 22);

        LinearLayout autoCard = createOptionCard(RotationRules.AUTO, true);
        LinearLayout.LayoutParams autoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ThemeTools.dp(this, 76));
        autoParams.topMargin = ThemeTools.dp(this, 10);
        content.addView(autoCard, autoParams);
        modeCards[RotationRules.AUTO] = autoCard;

        GridLayout orientationGrid = new GridLayout(this);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int columns = widthDp < 360 ? 1 : widthDp >= 700 ? 4 : 2;
        orientationGrid.setColumnCount(columns);
        int[] fixedModes = {RotationRules.PORTRAIT, RotationRules.REVERSE_PORTRAIT,
                RotationRules.LANDSCAPE, RotationRules.REVERSE_LANDSCAPE};
        for (int i = 0; i < fixedModes.length; i++) {
            int mode = fixedModes[i];
            LinearLayout card = createOptionCard(mode, false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / columns, 1f), GridLayout.spec(i % columns, 1f));
            params.width = 0;
            params.height = ThemeTools.dp(this, 112);
            params.setMargins(ThemeTools.dp(this, 4), ThemeTools.dp(this, 8),
                    ThemeTools.dp(this, 4), ThemeTools.dp(this, 0));
            orientationGrid.addView(card, params);
            modeCards[mode] = card;
        }
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = ThemeTools.dp(this, 4);
        content.addView(orientationGrid, gridParams);

        autoBlockedSection = new LinearLayout(this);
        autoBlockedSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout blockHeading = new LinearLayout(this);
        blockHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView blockTitle = text(getString(R.string.block_heading), 16, true);
        blockHeading.addView(blockTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton blockInfo = new ImageButton(this);
        blockInfo.setImageResource(R.drawable.ic_info);
        blockInfo.setContentDescription(getString(R.string.block_info));
        blockInfo.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        blockInfo.setPadding(ThemeTools.dp(this, 6), ThemeTools.dp(this, 6),
                ThemeTools.dp(this, 6), ThemeTools.dp(this, 6));
        blockInfo.setOnClickListener(view -> BlockInfo.show(this));
        blockHeading.addView(blockInfo, new LinearLayout.LayoutParams(
                ThemeTools.dp(this, 36), ThemeTools.dp(this, 36)));
        addTop(autoBlockedSection, blockHeading, 20);
        addBlockedRow(autoBlockedSection, RotationRules.PORTRAIT);
        addBlockedRow(autoBlockedSection, RotationRules.REVERSE_PORTRAIT);
        addBlockedRow(autoBlockedSection, RotationRules.LANDSCAPE);
        addBlockedRow(autoBlockedSection, RotationRules.REVERSE_LANDSCAPE);
        content.addView(autoBlockedSection);

        Button apply = primaryButton(getString(R.string.apply_phone));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ThemeTools.dp(this, 54));
        applyParams.topMargin = ThemeTools.dp(this, 22);
        content.addView(apply, applyParams);
        apply.setOnClickListener(view -> applyWholePhoneMode());

        stopButton = smallButton(getString(R.string.stop_rotation));
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ThemeTools.dp(this, 48));
        stopParams.topMargin = ThemeTools.dp(this, 8);
        content.addView(stopButton, stopParams);
        stopButton.setOnClickListener(view -> stopRotation());

        return scroll;
    }

    private LinearLayout createOptionCard(int mode, boolean horizontal) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        card.setPadding(ThemeTools.dp(this, 10), ThemeTools.dp(this, 8),
                ThemeTools.dp(this, 10), ThemeTools.dp(this, 8));
        OrientationIconView icon = new OrientationIconView(this);
        icon.setMode(mode);
        LinearLayout.LayoutParams iconParams = horizontal
                ? new LinearLayout.LayoutParams(ThemeTools.dp(this, 54), ThemeTools.dp(this, 54))
                : new LinearLayout.LayoutParams(ThemeTools.dp(this, 44), ThemeTools.dp(this, 44));
        if (horizontal) {
            iconParams.rightMargin = ThemeTools.dp(this, 12);
        }
        card.addView(icon, iconParams);

        TextView label = text(getString(RotationRules.labelResource(mode)), horizontal ? 15 : 13, true);
        label.setGravity(horizontal ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        if (horizontal) {
            card.addView(label, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = ThemeTools.dp(this, 4);
            card.addView(label, labelParams);
        }
        card.setTag(mode);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> {
            selectedMode = (int) view.getTag();
            updateSelection();
        });
        return card;
    }

    private void addBlockedRow(LinearLayout container, int mode) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        OrientationIconView icon = new OrientationIconView(this);
        icon.setMode(mode);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                ThemeTools.dp(this, 38), ThemeTools.dp(this, 38));
        iconParams.rightMargin = ThemeTools.dp(this, 8);
        row.addView(icon, iconParams);
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(getString(RotationRules.labelResource(mode)));
        checkBox.setTextColor(ThemeTools.color(this, R.attr.inkColor));
        checkBox.setTextSize(14);
        checkBox.setChecked(RotationRules.isBlocked(blockedMask, mode));
        checkBox.setOnCheckedChangeListener((button, checked) -> {
            int bit = RotationRules.blockedBit(mode);
            blockedMask = checked ? blockedMask | bit : blockedMask & ~bit;
        });
        blockedChecks[mode] = checkBox;
        row.addView(checkBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ThemeTools.dp(this, 48)));
        row.setOnClickListener(view -> checkBox.setChecked(!checkBox.isChecked()));
        container.addView(row);
    }

    private void updateSelection() {
        int accent = ThemeTools.color(this, R.attr.accentColor);
        int panel = ThemeTools.color(this, R.attr.panelColor);
        int muted = ThemeTools.color(this, R.attr.mutedInk);
        for (int mode = RotationRules.AUTO; mode <= RotationRules.REVERSE_LANDSCAPE; mode++) {
            View card = modeCards[mode];
            if (card != null) {
                boolean selected = mode == selectedMode;
                GradientDrawable background = ThemeTools.roundedBackground(
                        this, panel, selected ? accent : muted, selected ? 2 : 1);
                card.setBackground(background);
            }
        }
        autoBlockedSection.setVisibility(selectedMode == RotationRules.AUTO ? View.VISIBLE : View.GONE);
    }

    private void applyWholePhoneMode() {
        preferences.edit()
                .putInt(RotationRules.KEY_GLOBAL_MODE, selectedMode)
                .putInt(RotationRules.KEY_GLOBAL_BLOCKED, blockedMask)
                .putBoolean(RotationRules.KEY_GLOBAL_ENABLED, true)
                .putBoolean(RotationRules.KEY_START_ERROR, false)
                .apply();
        if (!Settings.canDrawOverlays(this)) {
            preferences.edit().putBoolean(RotationRules.KEY_PENDING_APPLY, true).apply();
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    .setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(permission);
            } catch (ActivityNotFoundException exception) {
                preferences.edit().putBoolean(RotationRules.KEY_PENDING_APPLY, false).apply();
                Toast.makeText(this, R.string.overlay_access_needed, Toast.LENGTH_LONG).show();
            }
            return;
        }
        startRotationService();
    }

    private void startRotationService() {
        Intent service = new Intent(this, RotationServiceV2.class)
                .setAction(RotationServiceV2.ACTION_APPLY);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            status.postDelayed(this::updateStatus, 500);
        } catch (RuntimeException exception) {
            preferences.edit()
                    .putBoolean(RotationRules.KEY_ACTIVE, false)
                    .putBoolean(RotationRules.KEY_START_ERROR, true)
                    .apply();
            updateStatus();
        }
    }

    private void updateStatus() {
        boolean active = preferences.getBoolean(RotationRules.KEY_ACTIVE, false);
        boolean globalEnabled = preferences.getBoolean(RotationRules.KEY_GLOBAL_ENABLED, false);
        status.setText(globalEnabled ? R.string.rotation_active
                : active ? R.string.app_rules_active : R.string.rotation_inactive);
        if (stopButton != null) {
            stopButton.setVisibility(active ? View.VISIBLE : View.GONE);
            stopButton.setText(globalEnabled ? R.string.disable_whole_phone : R.string.stop_app_rules);
        }
    }

    private void stopRotation() {
        boolean globalEnabled = preferences.getBoolean(RotationRules.KEY_GLOBAL_ENABLED, false);
        preferences.edit().putBoolean(RotationRules.KEY_GLOBAL_ENABLED, false).apply();
        if (globalEnabled && hasAppSpecificRules()
                && hasUsageAccess() && Settings.canDrawOverlays(this)) {
            startRotationService();
        } else {
            stopService(new Intent(this, RotationServiceV2.class));
            preferences.edit().putBoolean(RotationRules.KEY_ACTIVE, false).apply();
        }
        updateStatus();
    }

    private boolean hasAppSpecificRules() {
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith("app_mode:")
                    && preferences.getInt(key, RotationRules.INHERIT) != RotationRules.INHERIT) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ThemeTools.color(this, R.attr.inkColor));
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(15);
        button.setBackground(ThemeTools.roundedBackground(this,
                ThemeTools.color(this, R.attr.accentColor), ThemeTools.color(this, R.attr.accentColor), 0));
        return button;
    }

    private Button smallButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(ThemeTools.color(this, R.attr.accentColor));
        button.setBackground(ThemeTools.roundedBackground(this,
                ThemeTools.color(this, R.attr.panelColor), ThemeTools.color(this, R.attr.accentColor), 1));
        button.setPadding(ThemeTools.dp(this, 10), 0, ThemeTools.dp(this, 10), 0);
        return button;
    }

    private void addTop(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = ThemeTools.dp(this, topDp);
        parent.addView(child, params);
    }
}
