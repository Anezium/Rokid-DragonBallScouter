package io.github.anezium.rokiddragonballscouter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * Full-screen HUD overlay styled after the Dragon Ball scouter.
 * <p>
 * Designed for monochrome-green AR displays: all visual hierarchy
 * is achieved through brightness (alpha), thickness and size —
 * not hue.  Every paint uses the same green base colour.
 */
public class ScouterOverlayView extends View {

    private final float dp = getResources().getDisplayMetrics().density;

    /* ── single-hue green palette ─────────────────────────────────── */
    private static final int G = 0xFF88FF44;

    private final Paint framePaint    = stroke(G, 1.5f);
    private final Paint arcPaint      = stroke(alpha(G, 155), 1f);
    private final Paint scanLinePaint = stroke(alpha(G, 30), 0.5f);
    private final Paint reticlePaint  = stroke(G, 1.5f);
    private final Paint sweepPaint    = stroke(alpha(G, 75), 2f);
    private final Paint dotPaint      = fill(G);

    private final Paint bigPaint   = text(G,             28f, true,  0.04f);
    private final Paint labelPaint = text(G,             13f, false, 0.10f);
    private final Paint smallPaint = text(alpha(G, 175), 9.5f, false, 0.16f);

    /* ── state ────────────────────────────────────────────────────── */
    private HudState hud = HudState.idle(
            "PRESS TO SCAN", "STANDBY", "Tap glasses or screen", true);

    /* ── constructors ─────────────────────────────────────────────── */
    public ScouterOverlayView(Context c)                        { super(c); }
    public ScouterOverlayView(Context c, AttributeSet a)        { super(c, a); }
    public ScouterOverlayView(Context c, AttributeSet a, int d) { super(c, a, d); }

    /* ── public API ───────────────────────────────────────────────── */
    public void render(HudState state) {
        hud = state;
        if (state.scanActive) postInvalidateOnAnimation(); else invalidate();
    }

    /* ══════════════════════════════════════════════════════════════ *
     *  DRAW                                                         *
     * ══════════════════════════════════════════════════════════════ */
    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now = SystemClock.elapsedRealtime();
        if (hud.opaqueBackground) c.drawColor(Color.BLACK);

        drawScanLines(c);
        drawScouterFrame(c);

        if (hud.scanActive) {
            drawSweep(c, now);
            drawStatus(c);
            drawPower(c);
            drawReticle(c, now);
            drawFooter(c);
            postInvalidateOnAnimation();
        } else {
            drawIdle(c);
        }
    }

    /* ── background CRT scan lines ────────────────────────────────── */
    private void drawScanLines(Canvas c) {
        float step = 6f * dp, w = getWidth();
        for (float y = 0; y < getHeight(); y += step)
            c.drawLine(0, y, w, y, scanLinePaint);
    }

    /* ── scouter lens frame ───────────────────────────────────────── */
    private void drawScouterFrame(Canvas c) {
        float w = getWidth(), h = getHeight();

        /* corner brackets */
        float p = 12f * dp, a = 18f * dp;
        corner(c, p,     p,     a,  1,  1);
        corner(c, w - p, p,     a, -1,  1);
        corner(c, p,     h - p, a,  1, -1);
        corner(c, w - p, h - p, a, -1, -1);
    }

    /* ── sweep animation ──────────────────────────────────────────── */
    private void drawSweep(Canvas c, long now) {
        float y = ((now % 2400L) / 2400f) * getHeight();
        c.drawLine(0, y, getWidth(), y, sweepPaint);
    }

    /* ── top-left status block ────────────────────────────────────── */
    private void drawStatus(Canvas c) {
        float x = 18f * dp, y = 32f * dp;
        c.drawText(hud.statusLabel, x, y, labelPaint);
        c.drawText(hud.lensLabel,   x, y + 17f * dp, smallPaint);
        if (hud.modeLabel != null)
            c.drawText(hud.modeLabel, x, y + 30f * dp, smallPaint);
    }

    /* ── top-right power level ────────────────────────────────────── */
    private void drawPower(Canvas c) {
        float rx = getWidth() - 18f * dp, ty = 30f * dp;

        String bp  = "BATTLE POWER";
        String num = hud.powerLevel != null
                ? String.format(Locale.getDefault(), "%,d", hud.powerLevel)
                : "----";
        String tgt = hud.targetId != null
                ? "TGT-" + Math.max(1, hud.targetId)
                : "NO TARGET";

        c.drawText(bp,  rx - smallPaint.measureText(bp),  ty,             smallPaint);
        c.drawText(num, rx - bigPaint.measureText(num),    ty + 32f * dp, bigPaint);
        c.drawText(tgt, rx - smallPaint.measureText(tgt),  ty + 48f * dp, smallPaint);

        float lw = Math.max(bigPaint.measureText(num), smallPaint.measureText(bp));
        c.drawLine(rx - lw, ty + 54f * dp, rx, ty + 54f * dp, arcPaint);
    }

    /* ── bottom footer ────────────────────────────────────────────── */
    private void drawFooter(Canvas c) {
        float x = 18f * dp, y = getHeight() - 18f * dp;
        if (hud.overNineThousand) {
            c.drawText("IT'S OVER 9000!", x, y, labelPaint);
        } else if (hud.promptLabel != null) {
            c.drawText(hud.promptLabel, x, y, smallPaint);
        }
    }

    /* ── target reticle (anime crosshair) ─────────────────────────── */
    private void drawReticle(Canvas c, long now) {
        float cx, cy, r;

        if (hud.lockCenterX != null && hud.lockCenterY != null) {
            cx = hud.lockCenterX;
            cy = hud.lockCenterY;
            float base = Math.min(getWidth(), getHeight());
            float ls = hud.lockScale != null ? hud.lockScale : 0.18f;
            r = Math.max(40f * dp, base * ls);
        } else {
            if (hud.targetRect == null) return;
            RectF m = mapToView(hud.targetRect, hud.imageWidth, hud.imageHeight);
            if (m == null) return;
            cx = m.centerX();
            cy = m.centerY() - m.height() * 0.1f;
            r = Math.max(m.width(), m.height()) * 0.42f;
        }

        if (hud.predictiveLock) r *= 1.06f;
        float pulse = 1f + ((now % 1100L) / 1100f)
                * (hud.predictiveLock ? 0.02f : 0.05f);
        float pr = r * pulse;

        /* circles */
        c.drawCircle(cx, cy, pr,         reticlePaint);
        c.drawCircle(cx, cy, pr * 0.45f, arcPaint);
        c.drawCircle(cx, cy, 3f * dp,    dotPaint);

        /* crosshair lines — gap near centre, extending past circle */
        float gap = 8f * dp, ext = pr + 16f * dp;
        c.drawLine(cx,       cy - gap, cx,       cy - ext, reticlePaint);
        c.drawLine(cx,       cy + gap, cx,       cy + ext, reticlePaint);
        c.drawLine(cx - gap, cy,       cx - ext, cy,       reticlePaint);
        c.drawLine(cx + gap, cy,       cx + ext, cy,       reticlePaint);

        /* diagonal ticks on main circle */
        float ti = pr - 4f * dp, to = pr + 4f * dp;
        for (int d = 45; d < 360; d += 90) {
            float rad = (float) Math.toRadians(d);
            float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
            c.drawLine(cx + ti * cos, cy + ti * sin,
                    cx + to * cos, cy + to * sin, arcPaint);
        }

        /* square brackets */
        float bk = pr * 1.25f, arm = 10f * dp;
        corner(c, cx - bk, cy - bk, arm,  1,  1);
        corner(c, cx + bk, cy - bk, arm, -1,  1);
        corner(c, cx - bk, cy + bk, arm,  1, -1);
        corner(c, cx + bk, cy + bk, arm, -1, -1);

        /* data-readout ticks beside the reticle */
        float dtX = cx + pr * 1.4f;
        float dtY = cy - 12f * dp;
        for (int i = 0; i < 4; i++) {
            float tw = (i == 1) ? 14f * dp : 8f * dp;
            c.drawLine(dtX, dtY + i * 7f * dp,
                    dtX + tw, dtY + i * 7f * dp, arcPaint);
        }

        /* label */
        String lbl = hud.predictiveLock ? "HOLD" : "LOCK";
        centred(c, lbl, cx, cy - pr - 12f * dp, smallPaint);
    }

    /* ── idle / standby screen ────────────────────────────────────── */
    private void drawIdle(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() * 0.42f;

        /* dormant crosshair */
        c.drawCircle(cx, cy, 50f * dp, arcPaint);
        float ch = 12f * dp;
        c.drawLine(cx - ch, cy, cx + ch, cy, framePaint);
        c.drawLine(cx, cy - ch, cx, cy + ch, framePaint);
        c.drawCircle(cx, cy, 2.5f * dp, dotPaint);

        /* labels */
        centred(c, hud.lensLabel,   cx, cy - 62f * dp, smallPaint);
        centred(c, hud.statusLabel, cx, cy + 76f * dp, bigPaint);
        if (hud.promptLabel != null)
            centred(c, hud.promptLabel, cx, cy + 96f * dp, smallPaint);
    }

    /* ═══════════ drawing helpers ═══════════ */

    private void corner(Canvas c, float x, float y, float a, int dx, int dy) {
        c.drawLine(x, y, x + a * dx, y, framePaint);
        c.drawLine(x, y, x, y + a * dy, framePaint);
    }

    private void tick(Canvas c, float cx, float cy, float r,
                      int deg, float half, Paint p) {
        float rad = (float) Math.toRadians(deg);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        c.drawLine(cx + (r - half) * cos, cy + (r - half) * sin,
                cx + (r + half) * cos, cy + (r + half) * sin, p);
    }

    private void centred(Canvas c, String t, float cx, float y, Paint p) {
        c.drawText(t, cx - p.measureText(t) / 2f, y, p);
    }

    private static RectF oval(float cx, float cy, float r) {
        return new RectF(cx - r, cy - r, cx + r, cy + r);
    }

    private RectF mapToView(RectF rect, int iw, int ih) {
        if (getWidth() == 0 || getHeight() == 0 || iw <= 0 || ih <= 0) return null;
        float s = Math.max(getWidth() / (float) iw, getHeight() / (float) ih);
        float dx = (getWidth() - iw * s) / 2f, dy = (getHeight() - ih * s) / 2f;
        return new RectF(rect.left * s + dx, rect.top * s + dy,
                rect.right * s + dx, rect.bottom * s + dy);
    }

    /* ═══════════ paint factories ═══════════ */

    private Paint stroke(int color, float wDp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStrokeWidth(wDp * dp);
        p.setStyle(Paint.Style.STROKE);
        return p;
    }

    private Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    private Paint text(int color, float sp, boolean bold, float spacing) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(sp * dp);
        p.setFakeBoldText(bold);
        p.setLetterSpacing(spacing);
        return p;
    }

    private static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
