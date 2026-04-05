package io.github.anezium.rokiddragonballscouter;

import android.graphics.RectF;

public final class HudState {
    public final String statusLabel;
    public final String lensLabel;
    public final RectF targetRect;
    public final int imageWidth;
    public final int imageHeight;
    public final Integer powerLevel;
    public final Integer targetId;
    public final boolean overNineThousand;
    public final boolean scanActive;
    public final String promptLabel;

    public static HudState idle(String statusLabel, String lensLabel, String promptLabel) {
        return new HudState(statusLabel, lensLabel, null, 0, 0, null, null, false, false, promptLabel);
    }

    public static HudState active(String statusLabel, String lensLabel) {
        return new HudState(statusLabel, lensLabel, null, 0, 0, null, null, false, true, null);
    }

    public static HudState active(
            String statusLabel,
            String lensLabel,
            RectF targetRect,
            int imageWidth,
            int imageHeight,
            Integer powerLevel,
            Integer targetId,
            boolean overNineThousand
    ) {
        return new HudState(
                statusLabel,
                lensLabel,
                targetRect,
                imageWidth,
                imageHeight,
                powerLevel,
                targetId,
                overNineThousand,
                true,
                null
        );
    }

    private HudState(
            String statusLabel,
            String lensLabel,
            RectF targetRect,
            int imageWidth,
            int imageHeight,
            Integer powerLevel,
            Integer targetId,
            boolean overNineThousand,
            boolean scanActive,
            String promptLabel
    ) {
        this.statusLabel = statusLabel;
        this.lensLabel = lensLabel;
        this.targetRect = targetRect;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.powerLevel = powerLevel;
        this.targetId = targetId;
        this.overNineThousand = overNineThousand;
        this.scanActive = scanActive;
        this.promptLabel = promptLabel;
    }
}
