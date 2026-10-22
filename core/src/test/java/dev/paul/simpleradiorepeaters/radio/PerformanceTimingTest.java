package dev.paul.simpleradiorepeaters.radio;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceTimingTest {
    @Test void emptyAndZeroLengthMeasurementsProduceFiniteReports() {
        var timing = new PerformanceTiming();
        assertEquals(0, timing.upper(.95)); assertEquals(0, ((Number) timing.report().get("mean_us")).doubleValue());
        timing.add(0); timing.add(-1);
        assertEquals(2, timing.count()); assertEquals(0, timing.total()); assertEquals(0, timing.upper(.99));
    }
    @Test void histogramBoundsContainRealPercentilesAndPreserveExactTotals() {
        var timing = new PerformanceTiming();
        for (int i = 1; i <= 100; i++) timing.add(i * 1000L);
        assertEquals(5_050_000, timing.total());
        assertTrue(timing.upper(.95) >= 95_000 && timing.upper(.95) < 190_000);
        assertTrue(timing.upper(.5) >= 50_000 && timing.upper(.5) < 100_000);
        assertEquals(100L, timing.report().get("samples"));
        assertEquals(100d, timing.report().get("max_us"));
        assertEquals(50.5, timing.report().get("mean_us"));
    }
    @Test void veryLargeSamplesUseTheFinalBucketWithoutIndexOverflow() {
        var timing = new PerformanceTiming(); timing.add(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, timing.upper(.99)); assertEquals(Long.MAX_VALUE, timing.total());
    }
}
