package com.example.rotateall;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class LandingActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeTools.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ThemeTools.color(this, R.attr.screenBackground));
        WindowInsetsHelper.apply(scroll);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(ThemeTools.dp(this, 22), ThemeTools.dp(this, 12),
                ThemeTools.dp(this, 22), ThemeTools.dp(this, 28));
        scroll.addView(content);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_rotator_logo);
        toolbar.addView(logo, new LinearLayout.LayoutParams(
                ThemeTools.dp(this, 44), ThemeTools.dp(this, 44)));
        TextView title = text(getString(R.string.app_name), 26, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = ThemeTools.dp(this, 10);
        toolbar.addView(title, titleParams);
        ImageButton theme = new ImageButton(this);
        theme.setImageResource(R.drawable.ic_theme);
        theme.setContentDescription(getString(R.string.theme_button));
        theme.setBackground(ThemeTools.roundedBackground(this,
                ThemeTools.color(this, R.attr.panelColor), ThemeTools.color(this, R.attr.mutedInk), 1));
        theme.setPadding(ThemeTools.dp(this, 10), ThemeTools.dp(this, 10),
                ThemeTools.dp(this, 10), ThemeTools.dp(this, 10));
        theme.setOnClickListener(view -> ThemeTools.showMenu(this, view));
        toolbar.addView(theme, new LinearLayout.LayoutParams(
                ThemeTools.dp(this, 48), ThemeTools.dp(this, 48)));
        content.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView chooseTitle = text(getString(R.string.choose_mode), 21, true);
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chooseParams.topMargin = ThemeTools.dp(this, 40);
        content.addView(chooseTitle, chooseParams);

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams choicesParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        choicesParams.topMargin = ThemeTools.dp(this, 18);
        content.addView(choices, choicesParams);
        choices.addView(createChoiceCard(
                false,
                getString(R.string.whole_phone),
                getString(R.string.whole_phone_summary),
                view -> startActivity(new Intent(this, HomeActivity.class))));
        choices.addView(createChoiceCard(
                true,
                getString(R.string.applications),
                getString(R.string.applications_summary),
                view -> startActivity(new Intent(this, AppRulesActivity.class))));

        int columns = getResources().getConfiguration().screenWidthDp >= 600 ? 2 : 1;
        if (columns == 2) {
            choices.setOrientation(LinearLayout.HORIZONTAL);
            for (int index = 0; index < choices.getChildCount(); index++) {
                View card = choices.getChildAt(index);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, ThemeTools.dp(this, 150), 1f);
                params.rightMargin = index == 0 ? ThemeTools.dp(this, 10) : 0;
                params.bottomMargin = 0;
                card.setLayoutParams(params);
            }
        }
        return scroll;
    }

    private View createChoiceCard(boolean apps, String title, String summary,
                                  View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(ThemeTools.dp(this, 18), ThemeTools.dp(this, 16),
                ThemeTools.dp(this, 16), ThemeTools.dp(this, 16));
        card.setBackground(ThemeTools.roundedBackground(this,
                ThemeTools.color(this, R.attr.panelColor), ThemeTools.color(this, R.attr.mutedInk), 1));
        card.setClickable(true);
        card.setFocusable(true);

        View icon;
        if (apps) {
            ImageView image = new ImageView(this);
            image.setImageResource(R.drawable.ic_apps);
            icon = image;
        } else {
            OrientationIconView orientationIcon = new OrientationIconView(this);
            orientationIcon.setMode(OrientationIconView.ICON_WHOLE_PHONE);
            icon = orientationIcon;
        }
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                ThemeTools.dp(this, 52), ThemeTools.dp(this, 52));
        iconParams.rightMargin = ThemeTools.dp(this, 16);
        card.addView(icon, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 17, true));
        TextView description = text(summary, 13, false);
        description.setTextColor(ThemeTools.color(this, R.attr.mutedInk));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = ThemeTools.dp(this, 4);
        labels.addView(description, descriptionParams);
        card.addView(labels, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = text("›", 28, false);
        arrow.setTextColor(ThemeTools.color(this, R.attr.mutedInk));
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(
                ThemeTools.dp(this, 22), LinearLayout.LayoutParams.WRAP_CONTENT);
        arrowParams.leftMargin = ThemeTools.dp(this, 8);
        card.addView(arrow, arrowParams);
        card.setOnClickListener(listener);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ThemeTools.dp(this, 116));
        cardParams.bottomMargin = ThemeTools.dp(this, 12);
        card.setLayoutParams(cardParams);
        return card;
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
}
