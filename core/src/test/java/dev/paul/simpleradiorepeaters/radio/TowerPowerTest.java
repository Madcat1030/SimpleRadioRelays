package dev.paul.simpleradiorepeaters.radio;

import dev.paul.simpleradiorepeaters.RepeatersConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TowerPowerTest {
    @Test void theFirstSixteenPointsRestoreHalfTheBaseLinearly() {
        for (int score = 0; score <= 16; score++)
            assertEquals(.5 + score / 32d, TowerPower.baseMultiplier(score, true));
        assertEquals(.5, TowerPower.baseMultiplier(-10, true));
        assertEquals(1, TowerPower.baseMultiplier(10000, true));
    }
    @Test void disablingTheBalanceRestoresEveryScore() {
        for (int score : new int[]{-1, 0, 2, 8, 15, 16, 32, 10000})
            assertEquals(1, TowerPower.baseMultiplier(score, false));
    }
    @Test void onlyTheBaseIsReducedAndFractionalPowerIsRetained() {
        var previous = RepeatersConfig.INSTANCE;
        try {
            RepeatersConfig.INSTANCE = new RepeatersConfig();
            assertEquals(2337.5 + 10, TowerPower.apply(4410, 4400, 1));
            assertEquals(3300 + 80, TowerPower.apply(4480, 4400, 8));
            assertEquals(4400 + 160, TowerPower.apply(4560, 4400, 16));
            assertEquals(4400 + 320, TowerPower.apply(4720, 4400, 32));
            // An amplified antenna bonus must not also advance recovery of the base.
            assertEquals(3300 + 140, TowerPower.apply(4540, 4400, 8));
            assertEquals(225 + 80, TowerPower.apply(380, 300, 8));
            RepeatersConfig.INSTANCE.antennaDependentBasePower = false;
            assertEquals(4540, TowerPower.apply(4540, 4400, 8));
        } finally { RepeatersConfig.INSTANCE = previous; }
    }
}
