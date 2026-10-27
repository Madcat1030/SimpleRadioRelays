package dev.paul.simpleradiorepeaters.radio;

import dev.paul.simpleradiorepeaters.RepeatersConfig;

/** Physical antenna strength restores the station's base power; antenna bonuses stay additive. */
public final class TowerPower {
    public static final int FULL_POWER_ANTENNA = 16;
    private TowerPower() {}

    public static double baseMultiplier(int antenna, boolean enabled) {
        return enabled ? .5 + .5 * Math.max(0, Math.min(FULL_POWER_ANTENNA, antenna)) / FULL_POWER_ANTENNA : 1;
    }

    /** Subtract only the missing base contribution, preserving bonuses supplied by other hooks. */
    public static double apply(double original, double base, int antenna) {
        return original - base * (1 - baseMultiplier(antenna, RepeatersConfig.INSTANCE.antennaDependentBasePower));
    }
}
