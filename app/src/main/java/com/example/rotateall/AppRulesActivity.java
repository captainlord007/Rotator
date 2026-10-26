package com.example.rotateall;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppRulesActivity extends Activity {
    private static final int PENDING_NONE = 0;
    private static final int PENDING_USAGE = 1;
    private static final int PENDING_OVERLAY = 2;
    private static final int PENDING_APP_INFO = 3;

    private SharedPreferences preferences;
    private TextView permissionStatus;
    private LinearLayout appList;
    private ProgressBar loading;
    private int pendingPermission = PENDING_NONE;
    private int resumeAfterAppInfo = PENDING_NONE;
    private final Map<String, PendingAppRule> pendingRules = new LinkedHashMap<>();
    private final Map<String, TextView> summaryViews = new LinkedHashMap<>();
    private final ExecutorService appLoader = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeTools.apply(this);
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pendingPermission = savedInstanceState.getInt("pending_permission", PENDING_NONE);
            resumeAfterAppInfo = savedInstanceState.getInt("resume_after_app_info", PENDING_NONE);
        }
        preferences = getSharedPreferences(RotationRules.PREFERENCES, MODE_PRIVATE);
        setContentView(buildScreen());
        loadApps();
        updatePermissionStatus();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("pending_permission", pendingPermission);
        outState.putInt("resume_after_app_info", resumeAfterAppInfo);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        appLoader.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingPermission == PENDING_APP_INFO) {
            int next = resumeAfterAppInfo;
            pendingPermission = PENDING_NONE;
            resumeAfterAppInfo = PENDING_NONE;
            if (next == PENDING_OVERLAY && hasUsageAccess() && !Settings.canDrawOverlays(this)) {
                openOverlaySettings();
            } else {
                continuePermissionFlow();
            }
        } else if (pendingPermission == PENDING_USAGE) {
            pendingPermission = PENDING_NONE;
            if (hasUsageAccess()) {
                continuePermissionFlow();
            } else {
                showRestrictedAccessHelp(PENDING_USAGE);
            }
        } else if (pendingPermission == PENDING_OVERLAY) {
            pendingPermission = PENDING_NONE;
            if (hasUsageAccess() && Settings.canDrawOverlays(this)) {
                startRotationService();
            } else if (!Settings.canDrawOverlays(this)) {
                showRestrictedAccessHelp(PENDING_OVERLAY);
            } else {
                showRestrictedAccessHelp(PENDING_USAGE);
            }
        }
        if (permissionStatus != null) {
            updatePermissionStatus();
        }
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeTools.color(this, R.attr.screenBackground));
        WindowInsetsHelper.apply(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(ThemeTools.dp(this, 18), ThemeTools.dp(this, 8),
                ThemeTools.dp(this, 18), ThemeTools.dp(this, 8));
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(ThemeTools.color(this, R.attr.inkColor));
        back.setContentDescription(getString(R.string.back));
        back.setBackground(ThemeTools.roundedBackground(this,
                ThemeTools.color(this, R.attr.panelColor), ThemeTools.color(this, R.attr.panelColor), 0));
        back.setOnClickListener(view -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(
                ThemeTools.dp(this, 44), ThemeTools.dp(this, 44)));
        TextView title = text(getString(R.string.applications), 20, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = ThemeTools.dp(this, 10);
        toolbar.addView(title, titleParams);
        Button apply = new Button(this);
        apply.setText(R.string.apply_short);
        apply.setContentDescription(getString(R.string.apply_app_rules));
        apply.setAllCaps(false);
        apply.setMinWidth(ThemeTools.dp(this, 72));
        apply.setTextColor(0xFFFFFFFF);
        apply.setTextSize(14);
        apply.setPadding(ThemeTools.dp(this, 12), 0, ThemeTools.dp(this, 12), 0);
        apply.setBackground(ThemeTools.roundedBackground(this,
                ThemeTools.color(this, R.attr.accentColor), ThemeTools.color(this, R.attr.accentColor), 0));
        apply.setOnClickListener(view -> applyPendingRules());
        toolbar.addView(apply, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ThemeTools.dp(this, 44)));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ThemeTools.dp(this, 60)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ThemeTools.dp(this, 18), ThemeTools.dp(this, 10),
                ThemeTools.dp(this, 18), ThemeTools.dp(this, 24));
        scroll.addView(content);

        permissionStatus = text(getString(R.string.app_rules_access_message), 13, false);
        permissionStatus.setTextColor(ThemeTools.color(this, R.attr.mutedInk));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = ThemeTools.dp(this, 14);
        content.addView(permissionStatus, statusParams);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                ThemeTools.dp(this, 36), ThemeTools.dp(this, 36));
        loadingParams.gravity = Gravity.CENTER_HORIZONTAL;
        loadingParams.topMargin = ThemeTools.dp(this, 24);
        content.addView(loading, loadingParams);

        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        listParams.topMargin = ThemeTools.dp(this, 12);
        content.addView(appList, listParams);

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void loadApps() {
        appList.removeAllViews();
        summaryViews.clear();
        loading.setVisibility(View.VISIBLE);
        appLoader.execute(() -> {
            List<AppEntry> entries = queryLauncherApps();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                loading.setVisibility(View.GONE);
                showApps(entries);
            });
        });
    }

    private List<AppEntry> queryLauncherApps() {
        ArrayList<AppEntry> entries = new ArrayList<>();
        PackageManager packageManager = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherActivities = packageManager.queryIntentActivities(launcherIntent, 0);
        Set<String> seenPackages = new HashSet<>();
        for (ResolveInfo resolved : launcherActivities) {
            if (resolved.activityInfo == null) {
                continue;
            }
            ApplicationInfo info = resolved.activityInfo.applicationInfo;
            if (info == null || !seenPackages.add(info.packageName)) {
                continue;
            }
            try {
                entries.add(new AppEntry(info.packageName,
                        packageManager.getApplicationLabel(info).toString(),
                        packageManager.getApplicationIcon(info)));
            } catch (RuntimeException ignored) {
                // Skip packages that disappear while the list is read.
            }
        }
        Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(entries, (left, right) -> collator.compare(left.name, right.name));
        return entries;
    }

    private void showApps(List<AppEntry> entries) {
        appList.removeAllViews();
        if (entries.isEmpty()) {
            appList.addView(text(getString(R.string.no_apps_found), 15, false));
            return;
        }
        int columns = getResources().getConfiguration().screenWidthDp >= 700 ? 2 : 1;
        if (columns == 1) {
            for (AppEntry entry : entries) {
                appList.addView(createAppRow(entry));
            }
        } else {
            for (int i = 0; i < entries.size(); i += 2) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.TOP);
                row.addView(createAppRow(entries.get(i)), new LinearLayout.LayoutParams(
                        0, ThemeTools.dp(this, 68), 1f));
                if (i + 1 < entries.size()) {
                    LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(
                            0, ThemeTools.dp(this, 68), 1f);
                    secondParams.leftMargin = ThemeTools.dp(this, 10);
                    row.addView(createAppRow(entries.get(i + 1)), secondParams);
                } else {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                }
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, ThemeTools.dp(this, 76));
                appList.addView(row, rowParams);
            }
        }
    }

    private View createAppRow(AppEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ThemeTools.dp(this, 12), ThemeTools.dp(this, 8),
                ThemeTools.dp(this, 12), ThemeTools.dp(this, 8));
        row.setBackground(ThemeTools.roundedBackground(this,
                ThemeTools.color(this, R.attr.panelColor), ThemeTools.color(this, R.attr.mutedInk), 1));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(entry.icon);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                ThemeTools.dp(this, 46), ThemeTools.dp(this, 46));
        iconParams.rightMargin = ThemeTools.dp(this, 12);
        row.addView(icon, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(entry.name, 15, true);
        TextView summary = text(currentRuleLabel(entry.packageName), 12, false);
        summary.setTextColor(ThemeTools.color(this, R.attr.mutedInk));
        summaryViews.put(entry.packageName, summary);
        labels.addView(name);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = ThemeTools.dp(this, 2);
        labels.addView(summary, summaryParams);
        row.addView(labels, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> RotationPickerDialog.show(
                this,
                entry.name,
                effectiveAppMode(entry.packageName),
                effectiveAppBlocked(entry.packageName),
                (mode, blocked) -> {
                    pendingRules.put(entry.packageName, new PendingAppRule(mode, blocked));
                    summary.setText(ruleLabel(mode));
                }
        ));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ThemeTools.dp(this, 68));
        rowParams.bottomMargin = ThemeTools.dp(this, 8);
        row.setLayoutParams(rowParams);
        return row;
    }

    private String currentRuleLabel(String packageName) {
        return ruleLabel(effectiveAppMode(packageName));
    }

    private String ruleLabel(int mode) {
        if (mode == RotationRules.INHERIT) {
            return getString(R.string.use_whole_phone);
        }
        return getString(RotationRules.labelResource(mode));
    }

    private int effectiveAppMode(String packageName) {
        PendingAppRule pending = pendingRules.get(packageName);
        return pending == null ? RotationRules.appMode(preferences, packageName) : pending.mode;
    }

    private int effectiveAppBlocked(String packageName) {
        PendingAppRule pending = pendingRules.get(packageName);
        return pending == null ? RotationRules.appBlocked(preferences, packageName) : pending.blockedMask;
    }

    private void applyPendingRules() {
        if (pendingRules.isEmpty()) {
            Toast.makeText(this, R.string.no_app_rule_changes, Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(RotationRules.KEY_GLOBAL_ENABLED, false)
                .putBoolean(RotationRules.KEY_START_ERROR, false);
        for (Map.Entry<String, PendingAppRule> entry : pendingRules.entrySet()) {
            PendingAppRule rule = entry.getValue();
            editor.putInt(RotationRules.appModeKey(entry.getKey()), rule.mode)
                    .putInt(RotationRules.appBlockedKey(entry.getKey()), rule.blockedMask);
        }
        editor.apply();

        pendingRules.clear();
        for (Map.Entry<String, TextView> entry : summaryViews.entrySet()) {
            entry.getValue().setText(currentRuleLabel(entry.getKey()));
        }

        if (hasAppSpecificRules()) {
            if (!hasUsageAccess()) {
                stopService(new Intent(this, RotationServiceV2.class));
                preferences.edit().putBoolean(RotationRules.KEY_ACTIVE, false).apply();
            }
            continuePermissionFlow();
        } else {
            stopService(new Intent(this, RotationServiceV2.class));
            preferences.edit().putBoolean(RotationRules.KEY_ACTIVE, false).apply();
            Toast.makeText(this, R.string.app_rules_applied, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasAppSpecificRules() {
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().startsWith("app_mode:")
                    && entry.getValue() instanceof Integer
                    && (Integer) entry.getValue() != RotationRules.INHERIT) {
                return true;
            }
        }
        return false;
    }

    private void continuePermissionFlow() {
        if (!hasUsageAccess()) {
            openUsageAccessSettings();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            return;
        }
        startRotationService();
    }

    private void openUsageAccessSettings() {
        pendingPermission = PENDING_USAGE;
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException exception) {
            pendingPermission = PENDING_NONE;
            Toast.makeText(this, R.string.usage_access_needed, Toast.LENGTH_LONG).show();
        }
    }

    private void openOverlaySettings() {
        pendingPermission = PENDING_OVERLAY;
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            pendingPermission = PENDING_NONE;
            Toast.makeText(this, R.string.overlay_access_needed, Toast.LENGTH_LONG).show();
        }
    }

    private void showRestrictedAccessHelp(int nextPermission) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.restricted_access_title)
                .setMessage(R.string.restricted_access_message)
                .setPositiveButton(R.string.open_app_info,
                        (dialog, which) -> openAppInfoAndResume(nextPermission))
                .setNeutralButton(R.string.try_again, (dialog, which) -> {
                    if (nextPermission == PENDING_OVERLAY) {
                        openOverlaySettings();
                    } else {
                        openUsageAccessSettings();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openAppInfoAndResume(int nextPermission) {
        resumeAfterAppInfo = nextPermission;
        pendingPermission = PENDING_APP_INFO;
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            pendingPermission = PENDING_NONE;
            resumeAfterAppInfo = PENDING_NONE;
            Toast.makeText(this, R.string.restricted_access_message, Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void startRotationService() {
        preferences.edit().putBoolean(RotationRules.KEY_START_ERROR, false).apply();
        Intent service = new Intent(this, RotationServiceV2.class)
                .setAction(RotationServiceV2.ACTION_APPLY);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            Toast.makeText(this, R.string.app_rules_applied, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException exception) {
            preferences.edit()
                    .putBoolean(RotationRules.KEY_ACTIVE, false)
                    .putBoolean(RotationRules.KEY_START_ERROR, true)
                    .apply();
        }
    }

    private void updatePermissionStatus() {
        if (!hasUsageAccess()) {
            permissionStatus.setText(R.string.usage_access_needed);
        } else if (!Settings.canDrawOverlays(this)) {
            permissionStatus.setText(R.string.overlay_access_needed);
        } else {
            permissionStatus.setText("");
        }
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

    private static final class AppEntry {
        final String packageName;
        final String name;
        final android.graphics.drawable.Drawable icon;

        AppEntry(String packageName, String name, android.graphics.drawable.Drawable icon) {
            this.packageName = packageName;
            this.name = name;
            this.icon = icon;
        }
    }

    private static final class PendingAppRule {
        final int mode;
        final int blockedMask;

        PendingAppRule(int mode, int blockedMask) {
            this.mode = mode;
            this.blockedMask = blockedMask;
        }
    }
}
