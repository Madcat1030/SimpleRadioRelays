package dev.paul.simpleradiorepeaters.radio;

/** Shared numeric policy for native wireless transmissions and virtual relay legs. */
public final class RadioPropagation {
    public record Profile(double distanceMultiplier, double dimensionalMultiplier, double noiseFloorAddition) {}
    public static final Profile NORMAL = new Profile(1, 1, 0);
    private static final Profile NETHER = new Profile(.5, 1, 10);
    private static final Profile FOCUSED_NETHER = new Profile(.25, 1, 0);
    private static final Profile NETHER_CROSSING = new Profile(1, 2, 0);
    private RadioPropagation() {}

    public static Profile profile(boolean fromNether, boolean toNether, boolean dimensional, boolean focused) {
        // Native Nether coordinates already multiply all axes by eight.
        if (!dimensional && fromNether && toNether) return focused ? FOCUSED_NETHER : NETHER;
        return dimensional && (fromNether || toNether) ? NETHER_CROSSING : NORMAL;
    }
}
