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

    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint amberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private HudState hudState = HudState.idle("PRESS TO SCAN", "STANDBY", "Tap glasses or screen", true);

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
        if (hudState.opaqueBackground) {
            canvas.drawColor(Color.BLACK);
        }
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
        String modeLabel = hudState.modeLabel != null ? hudState.modeLabel : "MODE: SCOUTER";
        canvas.drawText(modeLabel, left, top + 40f * density, smallPaint);

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
            String footerLabel = hudState.promptLabel != null ? hudState.promptLabel : "SCOUTER LINK STABLE";
            canvas.drawText(footerLabel, left, bottom, smallPaint);
        }
    }

    private void drawTargetLock(Canvas canvas, long now) {
        float centerX;
        float centerY;
        float radius;

        if (hudState.lockCenterX != null && hudState.lockCenterY != null) {
            centerX = hudState.lockCenterX;
            centerY = hudState.lockCenterY;
            float base = Math.min(getWidth(), getHeight());
            float lockScale = hudState.lockScale != null ? hudState.lockScale : 0.18f;
            radius = Math.max(42f * density, base * lockScale);
        } else {
            if (hudState.targetRect == null) {
                return;
            }

            RectF mapped = mapToView(hudState.targetRect, hudState.imageWidth, hudState.imageHeight);
            if (mapped == null) {
                return;
            }

            centerX = mapped.centerX();
            centerY = mapped.centerY() - mapped.height() * 0.10f;
            radius = Math.max(mapped.width(), mapped.height()) * 0.42f;
        }

        if (hudState.predictiveLock) {
            radius *= 1.08f;
        }

        float pulseRange = hudState.predictiveLock ? 0.03f : 0.08f;
        float pulse = 1f + (((now % 900L) / 900f) * pulseRange);

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
        String lockLabel = hudState.predictiveLock ? "HOLD" : "LOCK";
        canvas.drawText(lockLabel, centerX - 18f * density, labelY, smallPaint);
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
