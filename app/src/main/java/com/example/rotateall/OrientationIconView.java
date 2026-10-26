package com.example.rotateall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class OrientationIconView extends View {
    static final int ICON_WHOLE_PHONE = -2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mode = RotationRules.AUTO;
    private int accent;
    private int surface;
    private int muted;

    public OrientationIconView(Context context) {
        super(context);
        init();
    }

    public OrientationIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setMode(int mode) {
        this.mode = mode;
        invalidate();
    }

    private void init() {
        accent = ThemeTools.color(getContext(), R.attr.accentColor);
        surface = ThemeTools.color(getContext(), R.attr.panelColor);
        muted = ThemeTools.color(getContext(), R.attr.mutedInk);
        setContentDescription(getContext().getString(R.string.orientation_graphic));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.save();
        if (mode == RotationRules.LANDSCAPE) {
            canvas.rotate(90, cx, cy);
        } else if (mode == RotationRules.REVERSE_LANDSCAPE) {
            canvas.rotate(-90, cx, cy);
        } else if (mode == RotationRules.REVERSE_PORTRAIT) {
            canvas.rotate(180, cx, cy);
        }

        boolean auto = mode == RotationRules.AUTO;
        float width = auto ? 18 * density : mode == ICON_WHOLE_PHONE || mode == RotationRules.INHERIT
                ? 25 * density : 23 * density;
        float height = auto ? 30 * density : mode == ICON_WHOLE_PHONE || mode == RotationRules.INHERIT
                ? 39 * density : 37 * density;
        RectF phone = new RectF(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(accent);
        canvas.drawRoundRect(phone, 4 * density, 4 * density, paint);

        float inner = 3 * density;
        RectF screenRect = new RectF(phone.left + inner, phone.top + 6 * density,
                phone.right - inner, phone.bottom - 5 * density);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(surface);
        canvas.drawRoundRect(screenRect, 2 * density, 2 * density, paint);
        paint.setColor(accent);
        RectF speaker = new RectF(cx - 3.2f * density, phone.top + 2.2f * density,
                cx + 2.1f * density, phone.top + 3.4f * density);
        canvas.drawRoundRect(speaker, 0.7f * density, 0.7f * density, paint);
        canvas.drawCircle(cx + 4.2f * density, phone.top + 2.8f * density,
                0.75f * density, paint);
        paint.setColor(muted);
        canvas.drawRoundRect(new RectF(cx - 2.2f * density, phone.bottom - 3.2f * density,
                cx + 2.2f * density, phone.bottom - 2.4f * density),
                0.4f * density, 0.4f * density, paint);

        if (mode == ICON_WHOLE_PHONE || mode == RotationRules.INHERIT) {
            float tile = 4.1f * density;
            float gap = 2 * density;
            float startX = cx - tile - gap / 2;
            float startY = cy - 2.5f * density;
            paint.setColor(accent);
            for (int row = 0; row < 2; row++) {
                for (int column = 0; column < 2; column++) {
                    float x = startX + column * (tile + gap);
                    float y = startY + row * (tile + gap);
                    canvas.drawRoundRect(new RectF(x, y, x + tile, y + tile),
                            0.8f * density, 0.8f * density, paint);
                }
            }
        }
        canvas.restore();

        if (mode == RotationRules.AUTO) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f * density);
            paint.setColor(accent);
            RectF arc = new RectF(cx - 21 * density, cy - 21 * density,
                    cx + 21 * density, cy + 21 * density);
            canvas.drawArc(arc, 215, 125, false, paint);
            canvas.drawArc(arc, 35, 125, false, paint);
            drawArrowHead(canvas, cx + 16 * density, cy - 13 * density,
                    70, density);
            drawArrowHead(canvas, cx - 16 * density, cy + 13 * density,
                    250, density);
        }
    }

    private void drawArrowHead(Canvas canvas, float x, float y, float angle, float density) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(angle);
        Path arrow = new Path();
        arrow.moveTo(0, 0);
        arrow.lineTo(-5 * density, -3 * density);
        arrow.lineTo(-5 * density, 3 * density);
        arrow.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        canvas.drawPath(arrow, paint);
        canvas.restore();
    }
}
