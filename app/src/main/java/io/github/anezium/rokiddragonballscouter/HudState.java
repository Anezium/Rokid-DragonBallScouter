package io.github.anezium.rokiddragonballscouter;

import android.graphics.RectF;

public final class HudState {
    public final String statusLabel;
    public final String lensLabel;
    public final String promptLabel;
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

    public static HudState idle(String statusLabel, String lensLabel, String promptLabel) {
        return new HudState(
                statusLabel,
                lensLabel,
                promptLabel,
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
                false
        );
    }

    public static HudState active(String statusLabel, String lensLabel) {
        return active(statusLabel, lensLabel, null);
    }

    public static HudState active(String statusLabel, String lensLabel, String promptLabel) {
        return new HudState(
                statusLabel,
                lensLabel,
                promptLabel,
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
                false
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
            String promptLabel
    ) {
        return new HudState(
                statusLabel,
                lensLabel,
                promptLabel,
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
                false
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
            String promptLabel
    ) {
        return new HudState(
                statusLabel,
                lensLabel,
                promptLabel,
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
                predictiveLock
        );
    }

    private HudState(
            String statusLabel,
            String lensLabel,
            String promptLabel,
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
            boolean predictiveLock
    ) {
        this.statusLabel = statusLabel;
        this.lensLabel = lensLabel;
        this.promptLabel = promptLabel;
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
    }
}
