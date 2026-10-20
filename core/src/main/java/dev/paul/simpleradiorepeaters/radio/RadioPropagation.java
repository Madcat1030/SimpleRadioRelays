package dev.paul.simpleradiorepeaters.radio;

/** Numeric modifiers supplied by an optional propagation policy. */
public final class RadioPropagation {
    public record Profile(double distanceMultiplier, double dimensionalMultiplier, double noiseFloorAddition) {}
    public static final Profile NORMAL = new Profile(1, 1, 0);
    private RadioPropagation() {}
}
