package dev.paul.simpleradiorepeaters.radio;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.paul.simpleradiorepeaters.radio.RadioLinkMath.Loss.ADDITIVE;

public class RelayGraphTest {
    private static RelayGraph.Edge edge(int to, double severity) { return new RelayGraph.Edge(to, severity); }
    @Test void oppositeSidesNeedTheTower() {
        assertNull(RadioLinkMath.receive(3600, 1800, 1800, 200, 10, 1, ADDITIVE, 300, 15));
        assertNotNull(RadioLinkMath.receive(1800, 1800, 1800, 400, 20, 1, ADDITIVE, 300, 15));
        assertNotNull(RadioLinkMath.receive(1800, 4600, 4600, 200, 10, 1, ADDITIVE, 500, 15));
    }
    @Test void aChainReachesBeyondAnyIndividualTower() {
        RelayGraph graph = new RelayGraph(List.of(List.of(edge(1, 15)), List.of(edge(0, 15), edge(2, 15)), List.of(edge(1, 15))), 64);
        RelayGraph.Reach[] reachable = graph.reachable(Map.of(0, 20d));
        assertEquals(3, reachable[2].hops()); assertEquals(20, reachable[2].severity());
        assertEquals(3, graph.reachable(Map.of(2, 20d))[0].hops());
    }
    @Test void aDiamondSelectsTheBestPathOnce() {
        RelayGraph graph = new RelayGraph(List.of(List.of(edge(1, 40), edge(2, 20)), List.of(edge(3, 40)), List.of(edge(3, 20)), List.of()), 64);
        RelayGraph.Reach[] result = graph.reachable(Map.of(0, 15d));
        assertEquals(4, Arrays.stream(result).filter(Objects::nonNull).count());
        assertEquals(20, result[3].severity()); assertEquals(3, result[3].hops());
    }
    @Test void ringsTerminateAndUseTheShortestEqualQualityRoute() {
        RelayGraph graph = new RelayGraph(List.of(List.of(edge(1, 15), edge(2, 15)), List.of(edge(0, 15), edge(2, 15)), List.of(edge(0, 15), edge(1, 15))), 64);
        RelayGraph.Reach[] result = graph.reachable(Map.of(0, 15d));
        assertEquals(1, result[0].hops()); assertEquals(2, result[1].hops()); assertEquals(2, result[2].hops());
    }
    @Test void hopBudgetKeepsUsefulShorterAlternatives() {
        RelayGraph graph = new RelayGraph(List.of(List.of(edge(1, 5), edge(2, 30)), List.of(edge(2, 5)), List.of(edge(3, 5)), List.of()), 3);
        RelayGraph.Reach[] result = graph.reachable(Map.of(0, 0d));
        assertEquals(30, result[3].severity()); assertEquals(3, result[3].hops());
    }
    @Test void ingressNoiseCannotBeRepairedAndPrefersFewerHops() {
        RelayGraph graph = new RelayGraph(List.of(List.of(edge(1, 5), edge(2, 20)), List.of(edge(2, 5)), List.of()), 64);
        RelayGraph.Reach[] result = graph.reachable(Map.of(0, 60d));
        assertEquals(60, result[2].severity()); assertEquals(2, result[2].hops());
    }
    @Test void separateEntrancesMergeWithoutDuplicatingRecipients() {
        RelayGraph graph = new RelayGraph(List.of(List.of(edge(1, 20)), List.of()), 64);
        RelayGraph.Reach[] result = graph.reachable(Map.of(0, 40d, 1, 15d));
        assertEquals(2, Arrays.stream(result).filter(Objects::nonNull).count());
        assertEquals(15, result[1].severity()); assertEquals(1, result[1].hops());
    }
    @Test void aDirectedLinkDoesNotInventAReturnPath() {
        RelayGraph graph = new RelayGraph(List.of(List.of(edge(1, 15)), List.of()), 64);
        assertNotNull(graph.reachable(Map.of(0, 15d))[1]); assertNull(graph.reachable(Map.of(1, 15d))[0]);
    }
    @Test void cutoffAndReceptionFloorMatchNativeRules() {
        assertNull(RadioLinkMath.receive(2000, 1800, 1800, 200, 10, 1, ADDITIVE, 300, 15));
        assertNotNull(RadioLinkMath.receive(1999, 1800, 1800, 200, 10, 1, ADDITIVE, 300, 15));
        assertNull(RadioLinkMath.receive(100, 20, 20, 300, 20, 1, ADDITIVE, 300, 15));
    }
    @Test void identicalPayloadFramesRemainDistinct() {
        RelayFrame first = new RelayFrame(), second = new RelayFrame(); UUID receiver = UUID.randomUUID();
        assertTrue(first.delivered("123.4 AM", receiver)); assertFalse(first.delivered("123.4 AM", receiver));
        assertTrue(second.delivered("123.4 AM", receiver)); assertTrue(first.delivered("123.4 FM", receiver));
    }
}
