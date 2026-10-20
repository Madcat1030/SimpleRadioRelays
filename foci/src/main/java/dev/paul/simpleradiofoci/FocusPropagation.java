package dev.paul.simpleradiofoci;
import dev.paul.simpleradiorepeaters.radio.RadioPropagation;
/** Optional Nether adjustments; native coordinates already have an eightfold Nether scale. */
public final class FocusPropagation {
    private static final RadioPropagation.Profile NETHER = new RadioPropagation.Profile(.5, 1, 10);
    private static final RadioPropagation.Profile FOCUSED = new RadioPropagation.Profile(.25, 1, 0);
    private static final RadioPropagation.Profile CROSSING = new RadioPropagation.Profile(1, 2, 0);
    private FocusPropagation() {}
    public static RadioPropagation.Profile profile(boolean fromNether, boolean toNether, boolean dimensional, boolean focused) {
        if (!dimensional && fromNether && toNether) return focused ? FOCUSED : NETHER;
        return dimensional && (fromNether || toNether) ? CROSSING : RadioPropagation.NORMAL;
    }
}
