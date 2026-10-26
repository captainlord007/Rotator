package com.example.rotateall;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupMenu;

final class ThemeTools {
    static final String KEY_THEME = "theme_choice";
    static final String THEME_AUTO = "auto";
    static final String THEME_LIGHT = "light";
    static final String THEME_DARK = "dark";

    private ThemeTools() {
    }

    static void apply(Activity activity) {
        String choice = activity.getSharedPreferences(RotationRules.PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_THEME, THEME_AUTO);
        if (THEME_LIGHT.equals(choice)) {
            activity.setTheme(R.style.AppTheme_Light);
        } else if (THEME_DARK.equals(choice)) {
            activity.setTheme(R.style.AppTheme_Dark);
        } else if ((activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            activity.setTheme(R.style.AppTheme_Dark);
        } else {
            activity.setTheme(R.style.AppTheme_Light);
        }
    }

    static int color(Context context, int attribute) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attribute, value, true);
        if (value.resourceId != 0) {
            return context.getColor(value.resourceId);
        }
        return value.data;
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable roundedBackground(Context context, int fill, int stroke, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, 18));
        if (strokeWidthDp > 0) {
            drawable.setStroke(dp(context, strokeWidthDp), stroke);
        }
        return drawable;
    }

    static void showMenu(Activity activity, View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor, Gravity.END);
        menu.getMenu().add(0, 1, 0, R.string.theme_light);
        menu.getMenu().add(0, 2, 1, R.string.theme_dark);
        menu.getMenu().add(0, 3, 2, R.string.theme_auto);
        SharedPreferences preferences = activity.getSharedPreferences(
                RotationRules.PREFERENCES,
                Context.MODE_PRIVATE
        );
        String current = preferences.getString(KEY_THEME, THEME_AUTO);
        menu.getMenu().findItem(THEME_LIGHT.equals(current) ? 1 : THEME_DARK.equals(current) ? 2 : 3)
                .setChecked(true);
        menu.getMenu().setGroupCheckable(0, true, true);
        menu.setOnMenuItemClickListener(item -> {
            String choice = item.getItemId() == 1
                    ? THEME_LIGHT
                    : item.getItemId() == 2 ? THEME_DARK : THEME_AUTO;
            preferences.edit().putString(KEY_THEME, choice).apply();
            activity.recreate();
            return true;
        });
        menu.show();
    }
}
