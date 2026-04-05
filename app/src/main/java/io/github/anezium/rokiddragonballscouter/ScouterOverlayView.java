package io.github.anezium.rokiddragonballscouter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class ScouterOverlayView extends View {
    private final float density = getResources().getDisplayMetrics().density;

    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint amberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private HudState hudState = HudState.idle("PRESS TO SCAN", "STANDBY", "Tap glasses or screen");

    public ScouterOverlayView(Context context) {
        super(context);
        init();
    }

    public ScouterOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScouterOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        tintPaint.setColor(Color.argb(26, 86, 255, 124));
        tintPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(Color.argb(42, 120, 255, 150));
        gridPaint.setStrokeWidth(density);
        gridPaint.setStyle(Paint.Style.STROKE);

        hudPaint.setColor(Color.parseColor("#A6FF78"));
        hudPaint.setStrokeWidth(2f * density);
        hudPaint.setStyle(Paint.Style.STROKE);
        hudPaint.setPathEffect(new CornerPathEffect(6f * density));

        amberPaint.setColor(Color.parseColor("#FF9D27"));
        amberPaint.setStrokeWidth(2f * density);
        amberPaint.setStyle(Paint.Style.STROKE);

        sweepPaint.setColor(Color.argb(105, 190, 255, 120));
        sweepPaint.setStrokeWidth(3f * density);
        sweepPaint.setStyle(Paint.Style.STROKE);

        textPaint.setColor(Color.parseColor("#DAFF9C"));
        textPaint.setTextSize(14f * density);
        textPaint.setLetterSpacing(0.12f);

        headlinePaint.setColor(Color.parseColor("#FFB648"));
        headlinePaint.setTextSize(20f * density);
        headlinePaint.setFakeBoldText(true);
        headlinePaint.setLetterSpacing(0.10f);

        smallPaint.setColor(Color.parseColor("#92F56A"));
        smallPaint.setTextSize(10f * density);
        smallPaint.setLetterSpacing(0.16f);

        solidPaint.setColor(Color.parseColor("#B4FF7E"));
        solidPaint.setStyle(Paint.Style.FILL);
    }

    public void render(HudState state) {
        hudState = state;
        if (state.scanActive) {
            postInvalidateOnAnimation();
        } else {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        long now = SystemClock.elapsedRealtime();
        drawTint(canvas);
        drawGrid(canvas);
        drawFrame(canvas);

        if (hudState.scanActive) {
            drawSweep(canvas, now);
            drawStatusPanel(canvas);
            drawTargetLock(canvas, now);
            postInvalidateOnAnimation();
        } else {
            drawIdlePrompt(canvas);
        }
    }

    private void drawTint(Canvas canvas) {
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), tintPaint);
    }

    private void drawGrid(Canvas canvas) {
        int columns = 6;
        int rows = 10;
        float cellWidth = getWidth() / (float) columns;
        float cellHeight = getHeight() / (float) rows;

        for (int column = 1; column < columns; column++) {
            float x = column * cellWidth;
            canvas.drawLine(x, 0f, x, getHeight(), gridPaint);
        }

        for (int row = 1; row < rows; row++) {
            float y = row * cellHeight;
            canvas.drawLine(0f, y, getWidth(), y, gridPaint);
        }
    }

    private void drawFrame(Canvas canvas) {
        float pad = 18f * density;
        float arm = 26f * density;

        canvas.drawLine(pad, pad, pad + arm, pad, hudPaint);
        canvas.drawLine(pad, pad, pad, pad + arm, hudPaint);

        canvas.drawLine(getWidth() - pad, pad, getWidth() - pad - arm, pad, hudPaint);
        canvas.drawLine(getWidth() - pad, pad, getWidth() - pad, pad + arm, hudPaint);

        canvas.drawLine(pad, getHeight() - pad, pad + arm, getHeight() - pad, hudPaint);
        canvas.drawLine(pad, getHeight() - pad, pad, getHeight() - pad - arm, hudPaint);

        canvas.drawLine(getWidth() - pad, getHeight() - pad, getWidth() - pad - arm, getHeight() - pad, hudPaint);
        canvas.drawLine(getWidth() - pad, getHeight() - pad, getWidth() - pad, getHeight() - pad - arm, hudPaint);
    }

    private void drawSweep(Canvas canvas, long now) {
        float progress = (now % 2200L) / 2200f;
        float y = progress * getHeight();

        canvas.drawLine(0f, y, getWidth(), y, sweepPaint);
    }

    private void drawIdlePrompt(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        float lensWidth = smallPaint.measureText(hudState.lensLabel);
        canvas.drawText(hudState.lensLabel, centerX - (lensWidth / 2f), centerY - (28f * density), smallPaint);

        float titleWidth = headlinePaint.measureText(hudState.statusLabel);
        canvas.drawText(hudState.statusLabel, centerX - (titleWidth / 2f), centerY, headlinePaint);

        if (hudState.promptLabel != null) {
            float promptWidth = textPaint.measureText(hudState.promptLabel);
            canvas.drawText(
                    hudState.promptLabel,
                    centerX - (promptWidth / 2f),
                    centerY + (28f * density),
                    textPaint
            );
        }

        float lineWidth = 28f * density;
        float lineY = centerY + (48f * density);
        canvas.drawLine(centerX - lineWidth, lineY, centerX - (8f * density), lineY, amberPaint);
        canvas.drawLine(centerX + (8f * density), lineY, centerX + lineWidth, lineY, amberPaint);
    }

    private void drawStatusPanel(Canvas canvas) {
        float left = 24f * density;
        float top = 38f * density;
        float rightPanelLeft = getWidth() - 148f * density;
        float bottom = getHeight() - 30f * density;

        canvas.drawText(hudState.statusLabel, left, top, headlinePaint);
        canvas.drawText(hudState.lensLabel, left, top + 22f * density, smallPaint);
        canvas.drawText("MODE: LOCAL LOCK", left, top + 40f * density, smallPaint);

        float panelTop = 54f * density;
        float panelBottom = 148f * density;
        float panelRight = getWidth() - 22f * density;
        RectF panelRect = new RectF(rightPanelLeft, panelTop, panelRight, panelBottom);
        canvas.drawRoundRect(panelRect, 14f * density, 14f * density, amberPaint);

        canvas.drawText("BATTLE POWER", panelRect.left + 14f * density, panelTop + 22f * density, smallPaint);
        String powerLabel = hudState.powerLevel != null
                ? String.format(Locale.getDefault(), "%,d", hudState.powerLevel)
                : "----";
        canvas.drawText(powerLabel, panelRect.left + 14f * density, panelTop + 52f * density, headlinePaint);

        String targetLabel = hudState.targetId != null
                ? "TARGET-" + Math.max(1, hudState.targetId)
                : "NO TARGET";
        canvas.drawText(targetLabel, panelRect.left + 14f * density, panelTop + 74f * density, textPaint);

        if (hudState.overNineThousand) {
            canvas.drawText("IT'S OVER 9000", left, bottom, headlinePaint);
        } else {
            canvas.drawText("SCOUTER LINK STABLE", left, bottom, smallPaint);
        }
    }

    private void drawTargetLock(Canvas canvas, long now) {
        if (hudState.targetRect == null) {
            return;
        }

        RectF mapped = mapToView(hudState.targetRect, hudState.imageWidth, hudState.imageHeight);
        if (mapped == null) {
            return;
        }

        float centerX = mapped.centerX();
        float centerY = mapped.centerY() - mapped.height() * 0.10f;
        float radius = Math.max(mapped.width(), mapped.height()) * 0.42f;
        float pulse = 1f + (((now % 900L) / 900f) * 0.08f);

        canvas.drawCircle(centerX, centerY, radius * pulse, amberPaint);
        canvas.drawCircle(centerX, centerY, radius * 0.72f, hudPaint);
        canvas.drawCircle(centerX, centerY, 4f * density, solidPaint);

        float bracket = radius * 1.25f;
        float arm = 12f * density;

        canvas.drawLine(centerX - bracket, centerY - bracket, centerX - bracket + arm, centerY - bracket, hudPaint);
        canvas.drawLine(centerX - bracket, centerY - bracket, centerX - bracket, centerY - bracket + arm, hudPaint);

        canvas.drawLine(centerX + bracket, centerY - bracket, centerX + bracket - arm, centerY - bracket, hudPaint);
        canvas.drawLine(centerX + bracket, centerY - bracket, centerX + bracket, centerY - bracket + arm, hudPaint);

        canvas.drawLine(centerX - bracket, centerY + bracket, centerX - bracket + arm, centerY + bracket, hudPaint);
        canvas.drawLine(centerX - bracket, centerY + bracket, centerX - bracket, centerY + bracket - arm, hudPaint);

        canvas.drawLine(centerX + bracket, centerY + bracket, centerX + bracket - arm, centerY + bracket, hudPaint);
        canvas.drawLine(centerX + bracket, centerY + bracket, centerX + bracket, centerY + bracket - arm, hudPaint);

        float labelY = centerY - radius - 12f * density;
        canvas.drawText("LOCK", centerX - 18f * density, labelY, smallPaint);
    }

    private RectF mapToView(RectF rect, int imageWidth, int imageHeight) {
        if (getWidth() == 0 || getHeight() == 0 || imageWidth <= 0 || imageHeight <= 0) {
            return null;
        }

        float scale = Math.max(getWidth() / (float) imageWidth, getHeight() / (float) imageHeight);
        float scaledWidth = imageWidth * scale;
        float scaledHeight = imageHeight * scale;
        float dx = (getWidth() - scaledWidth) / 2f;
        float dy = (getHeight() - scaledHeight) / 2f;

        return new RectF(
                rect.left * scale + dx,
                rect.top * scale + dy,
                rect.right * scale + dx,
                rect.bottom * scale + dy
        );
    }
}
