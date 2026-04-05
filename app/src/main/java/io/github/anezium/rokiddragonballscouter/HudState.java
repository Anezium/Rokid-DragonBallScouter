package io.github.anezium.rokiddragonballscouter;

import android.graphics.RectF;

public final class HudState {
    public final String statusLabel;
    public final String lensLabel;
    public final String promptLabel;
    public final String modeLabel;
    public final RectF targetRect;
    public final int imageWidth;
    public final int imageHeight;
    public final Integer powerLevel;
    public final Integer targetId;
    public final boolean overNineThousand;
    public final boolean scanActive;
    public final Float lockCenterX;
    public final Float lockCenterY;
    public final Float lockScale;
    public final boolean predictiveLock;
    public final boolean opaqueBackground;

    public static HudState idle(
            String statusLabel,
            String lensLabel,
            String promptLabel,
            boolean opaqueBackground
    ) {
        return new HudState(
                statusLabel,
                lensLabel,
                promptLabel,
                null,
                null,
                0,
                0,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                false,
                opaqueBackground
        );
    }

    public static HudState active(
            String statusLabel,
            String lensLabel,
            String promptLabel,
            String modeLabel,
            boolean opaqueBackground
    ) {
        return new HudState(
                statusLabel,
                lensLabel,
                promptLabel,
                modeLabel,
                null,
                0,
                0,
                null,
                null,
                false,
                true,
                null,
                null,
                null,
                false,
                opaqueBackground
        );
    }

    public static HudState active(
            String statusLabel,
            String lensLabel,
            RectF targetRect,
            int imageWidth,
            int imageHeight,
            Integer powerLevel,
            Integer targetId,
            boolean overNineThousand,
            String promptLabel,
            String modeLabel,
            boolean predictiveLock,
            boolean opaqueBackground
    ) {
        return new HudState(
                statusLabel,
                lensLabel,
                promptLabel,
                modeLabel,
                targetRect,
                imageWidth,
                imageHeight,
                powerLevel,
                targetId,
                overNineThousand,
                true,
                null,
                null,
                null,
                predictiveLock,
                opaqueBackground
        );
    }

    public static HudState activeAngular(
            String statusLabel,
            String lensLabel,
            Integer powerLevel,
            Integer targetId,
            boolean overNineThousand,
            float lockCenterX,
            float lockCenterY,
            float lockScale,
            boolean predictiveLock,
            String promptLabel,
            String modeLabel,
            boolean opaqueBackground
    ) {
        return new HudState(
                statusLabel,
                lensLabel,
                promptLabel,
                modeLabel,
                null,
                0,
                0,
                powerLevel,
                targetId,
                overNineThousand,
                true,
                lockCenterX,
                lockCenterY,
                lockScale,
                predictiveLock,
                opaqueBackground
        );
    }

    private HudState(
            String statusLabel,
            String lensLabel,
            String promptLabel,
            String modeLabel,
            RectF targetRect,
            int imageWidth,
            int imageHeight,
            Integer powerLevel,
            Integer targetId,
            boolean overNineThousand,
            boolean scanActive,
            Float lockCenterX,
            Float lockCenterY,
            Float lockScale,
            boolean predictiveLock,
            boolean opaqueBackground
    ) {
        this.statusLabel = statusLabel;
        this.lensLabel = lensLabel;
        this.promptLabel = promptLabel;
        this.modeLabel = modeLabel;
        this.targetRect = targetRect;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.powerLevel = powerLevel;
        this.targetId = targetId;
        this.overNineThousand = overNineThousand;
        this.scanActive = scanActive;
        this.lockCenterX = lockCenterX;
        this.lockCenterY = lockCenterY;
        this.lockScale = lockScale;
        this.predictiveLock = predictiveLock;
        this.opaqueBackground = opaqueBackground;
    }
}
