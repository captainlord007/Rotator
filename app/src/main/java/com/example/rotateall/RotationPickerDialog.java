package com.example.rotateall;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class RotationPickerDialog {
    interface OnSave {
        void onSave(int mode, int blockedMask);
    }

    private static final int[] OPTIONS = {
            RotationRules.INHERIT,
            RotationRules.AUTO,
            RotationRules.PORTRAIT,
            RotationRules.REVERSE_PORTRAIT,
            RotationRules.LANDSCAPE,
            RotationRules.REVERSE_LANDSCAPE
    };

    private RotationPickerDialog() {
    }

    static void show(Activity activity, String title, int initialMode, int initialBlockedMask, OnSave onSave) {
        int[] selectedMode = {initialMode};
        int[] blockedMask = {initialBlockedMask};
        CheckBox[] blockedChecks = new CheckBox[5];

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ThemeTools.dp(activity, 18), ThemeTools.dp(activity, 8),
                ThemeTools.dp(activity, 18), ThemeTools.dp(activity, 8));

        GridLayout grid = new GridLayout(activity);
        int columns = activity.getResources().getConfiguration().screenWidthDp >= 700 ? 3 : 2;
        grid.setColumnCount(columns);
        grid.setUseDefaultMargins(false);
        View[] blockedGroup = new View[2];
        int position = 0;
        for (int option : OPTIONS) {
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(ThemeTools.dp(activity, 4), ThemeTools.dp(activity, 6),
                    ThemeTools.dp(activity, 4), ThemeTools.dp(activity, 6));
            OrientationIconView icon = new OrientationIconView(activity);
            icon.setMode(option == RotationRules.INHERIT
                    ? OrientationIconView.ICON_WHOLE_PHONE : option);
            card.addView(icon, new LinearLayout.LayoutParams(
                    ThemeTools.dp(activity, 44), ThemeTools.dp(activity, 44)));
            TextView label = new TextView(activity);
            label.setText(label(activity, option));
            label.setTextColor(ThemeTools.color(activity, R.attr.inkColor));
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            card.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            card.setTag(option);
            card.setOnClickListener(view -> {
                selectedMode[0] = (int) view.getTag();
                updateCards(activity, grid, selectedMode[0]);
                if (blockedGroup[0] != null) {
                    updateBlockedVisibility(blockedChecks, blockedGroup[0], blockedGroup[1],
                            selectedMode[0] == RotationRules.AUTO);
                }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(position / columns, 1f), GridLayout.spec(position % columns, 1f));
            params.width = 0;
            params.height = ThemeTools.dp(activity, 92);
            params.setMargins(ThemeTools.dp(activity, 4), ThemeTools.dp(activity, 4),
                    ThemeTools.dp(activity, 4), ThemeTools.dp(activity, 4));
            grid.addView(card, params);
            position++;
        }
        content.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout blockHeading = new LinearLayout(activity);
        blockHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView blockTitle = new TextView(activity);
        blockTitle.setText(R.string.block_in_auto);
        blockTitle.setTextColor(ThemeTools.color(activity, R.attr.inkColor));
        blockTitle.setTextSize(15);
        blockTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        blockHeading.addView(blockTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.ImageButton blockInfo = new android.widget.ImageButton(activity);
        blockInfo.setImageResource(R.drawable.ic_info);
        blockInfo.setContentDescription(activity.getString(R.string.block_info));
        blockInfo.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        blockInfo.setPadding(ThemeTools.dp(activity, 6), ThemeTools.dp(activity, 6),
                ThemeTools.dp(activity, 6), ThemeTools.dp(activity, 6));
        blockInfo.setOnClickListener(view -> BlockInfo.show(activity));
        blockHeading.addView(blockInfo, new LinearLayout.LayoutParams(
                ThemeTools.dp(activity, 36), ThemeTools.dp(activity, 36)));
        blockedGroup[0] = blockHeading;
        LinearLayout.LayoutParams blockTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blockTitleParams.topMargin = ThemeTools.dp(activity, 14);
        content.addView(blockHeading, blockTitleParams);

        LinearLayout blockedList = new LinearLayout(activity);
        blockedGroup[1] = blockedList;
        blockedList.setOrientation(LinearLayout.VERTICAL);
        int[] blockModes = {RotationRules.PORTRAIT, RotationRules.REVERSE_PORTRAIT,
                RotationRules.LANDSCAPE, RotationRules.REVERSE_LANDSCAPE};
        for (int mode : blockModes) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            OrientationIconView icon = new OrientationIconView(activity);
            icon.setMode(mode);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    ThemeTools.dp(activity, 38), ThemeTools.dp(activity, 38));
            iconParams.setMargins(0, 0, ThemeTools.dp(activity, 8), 0);
            row.addView(icon, iconParams);

            CheckBox checkBox = new CheckBox(activity);
            checkBox.setText(label(activity, mode));
            checkBox.setTextColor(ThemeTools.color(activity, R.attr.inkColor));
            checkBox.setTextSize(14);
            checkBox.setChecked((blockedMask[0] & RotationRules.blockedBit(mode)) != 0);
            checkBox.setOnCheckedChangeListener((button, checked) -> {
                int bit = RotationRules.blockedBit(mode);
                blockedMask[0] = checked ? blockedMask[0] | bit : blockedMask[0] & ~bit;
            });
            blockedChecks[mode] = checkBox;
            row.addView(checkBox, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ThemeTools.dp(activity, 48)));
            row.setOnClickListener(view -> checkBox.setChecked(!checkBox.isChecked()));
            blockedList.addView(row);
        }
        content.addView(blockedList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        updateBlockedVisibility(blockedChecks, blockTitle, blockedList,
                initialMode == RotationRules.AUTO);
        updateCards(activity, grid, initialMode);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);
        scrollView.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(scrollView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.app_rule_done, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            onSave.onSave(selectedMode[0], blockedMask[0]);
            dialog.dismiss();
        }));
        dialog.show();
    }

    static String label(Activity activity, int mode) {
        if (mode == RotationRules.INHERIT) {
            return activity.getString(R.string.use_whole_phone);
        }
        return activity.getString(RotationRules.labelResource(mode));
    }

    private static void updateBlockedVisibility(CheckBox[] checks, View title, View list, boolean visible) {
        title.setVisibility(visible ? View.VISIBLE : View.GONE);
        list.setVisibility(visible ? View.VISIBLE : View.GONE);
        for (CheckBox checkBox : checks) {
            if (checkBox != null) {
                checkBox.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (checkBox.getParent() instanceof View) {
                    ((View) checkBox.getParent()).setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }
        }
    }

    private static void updateCards(Activity activity, GridLayout grid, int selectedMode) {
        int accent = ThemeTools.color(activity, R.attr.accentColor);
        int panel = ThemeTools.color(activity, R.attr.panelColor);
        int muted = ThemeTools.color(activity, R.attr.mutedInk);
        for (int index = 0; index < grid.getChildCount(); index++) {
            View child = grid.getChildAt(index);
            int mode = (int) child.getTag();
            boolean selected = mode == selectedMode;
            GradientDrawable background = ThemeTools.roundedBackground(
                    activity, panel, selected ? accent : muted, selected ? 2 : 1);
            child.setBackground(background);
        }
    }
}
