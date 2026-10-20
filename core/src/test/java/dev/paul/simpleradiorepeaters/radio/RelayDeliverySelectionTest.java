package dev.paul.simpleradiorepeaters.radio;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class RelayDeliverySelectionTest {
    private static RelayDeliverySelection.Candidate<String> route(String source, double severity, double power, int hops) {
        return new RelayDeliverySelection.Candidate<>(source, severity, power, hops);
    }

    @Test void slightlyNoisierLocalRelayBeatsAnExhaustedDirectCarrier() {
        var direct = route("near-side receiver", 99.60, 2.37, 2);
        var local = route("far-side relay", 99.94, 4418, 3);
        var selected = RelayDeliverySelection.select(List.of(direct, local));
        assertSame(local, selected);
        assertEquals(99.94, selected.severity(), "Selection must retain the actual noisier route, not flatten its quality to the minimum");
    }

    @Test void onePointIsInclusiveButStrongerRoutesBeyondItAreExcluded() {
        var clean = route("clean", 20, 2, 1);
        var boundary = route("within tolerance", 21, 4400, 3);
        var outside = route("outside tolerance", 21.0001, 5500, 2);
        assertSame(boundary, RelayDeliverySelection.select(List.of(outside, clean, boundary)));
    }

    @Test void nearTiesCannotChainBeyondTheCleanestRouteAndOrderDoesNotChangeTheWinner() {
        var clean = route("clean", 10, 2, 1);
        var middle = route("middle", 10.8, 4400, 2);
        var outside = route("outside", 11.6, 5500, 3);
        for (var order : List.of(List.of(clean, middle, outside), List.of(clean, outside, middle),
                List.of(middle, clean, outside), List.of(middle, outside, clean),
                List.of(outside, clean, middle), List.of(outside, middle, clean)))
            assertSame(middle, RelayDeliverySelection.select(order), "The best quality band must use all candidates, independent of iteration order");
    }

    @Test void EqualCarrierPowerPrefersCleanerAudioThenFewerHops() {
        var noisy = route("noisier", 20.8, 4400, 1);
        var cleanLong = route("clean long", 20.2, 4400, 3);
        var cleanShort = route("clean short", 20.2, 4400, 2);
        assertSame(cleanShort, RelayDeliverySelection.select(List.of(noisy, cleanLong, cleanShort)));
    }

    @Test void emptyCandidateListDoesNotInventADelivery() {
        assertNull(RelayDeliverySelection.select(List.of()));
    }
}
