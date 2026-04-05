package io.github.anezium.rokiddragonballscouter;

import java.util.Random;

public final class PowerLevelGenerator {
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    private PowerLevelGenerator() {
    }

    public static int next() {
        int roll = RANDOM.nextInt(100);
        if (roll < 10) {
            return 9001 + RANDOM.nextInt(76000);
        }
        if (roll < 45) {
            return 2800 + RANDOM.nextInt(6200);
        }
        return 120 + RANDOM.nextInt(2680);
    }
}
