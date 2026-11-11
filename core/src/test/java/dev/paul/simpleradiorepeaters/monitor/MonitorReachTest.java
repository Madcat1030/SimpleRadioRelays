package dev.paul.simpleradiorepeaters.monitor;

import dev.paul.simpleradiorepeaters.radio.RadioLinkMath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MonitorReachTest {
    private static final RadioLinkMath.Result GOOD = new RadioLinkMath.Result(100, 12);

    @Test void stopsAfterTwoLayersAndDoesNotExtendThroughUnheardTowers() {
        var links = new RadioLinkMath.Result[6][6];
        links[0][1] = links[1][0] = GOOD; // O ⇄ a
        links[1][2] = links[2][1] = GOOD; // a ⇄ a-1
        links[2][3] = links[3][2] = GOOD; // This third layer stays off the map.
        links[0][4] = links[4][5] = GOOD; // O → ? → hidden
        assertEquals(List.of(new MonitorReach.Node(1, 0, true, true), new MonitorReach.Node(4, 0, true, false),
                new MonitorReach.Node(2, 1, true, true)), MonitorReach.solve(links.length, 64, (a, b, d) -> links[a][b]));
    }

    @Test void choosesOneParentUsingTheRadioDeliveryQualityAndPowerRule() {
        var links = new RadioLinkMath.Result[5][5];
        links[0][1] = links[1][0] = links[0][2] = links[2][0] = GOOD;
        links[1][3] = new RadioLinkMath.Result(80, 12);
        links[2][3] = new RadioLinkMath.Result(150, 12.8); // Within the quality tolerance; higher power wins.
        links[3][1] = GOOD;
        links[4][1] = GOOD; // A heard-only child.
        var nodes = MonitorReach.solve(links.length, 64, (a, b, d) -> links[a][b]);
        assertEquals(List.of(new MonitorReach.Node(1, 0, true, true), new MonitorReach.Node(2, 0, true, true),
                new MonitorReach.Node(3, 2, true, true), new MonitorReach.Node(4, 1, false, true)), nodes);
        links[2][3] = new RadioLinkMath.Result(150, 14);
        assertEquals(1, MonitorReach.solve(links.length, 64, (a, b, d) -> links[a][b]).get(2).parent());
    }

    @Test void evaluatesTheReturnLegWithDecayAndHonorsTheVisitBudget() {
        var links = new RadioLinkMath.Result[3][3];
        links[0][1] = links[1][0] = links[1][2] = links[2][1] = GOOD;
        MonitorReach.Links plain = (a, b, decay) -> decay == 2 ? null : links[a][b];
        var weak = MonitorReach.solve(links.length, 64, plain);
        assertEquals(new MonitorReach.Node(2, 1, true, false), weak.get(1));
        var reset = MonitorReach.solve(links.length, 64, (a, b, decay) -> links[a][b]);
        assertEquals(new MonitorReach.Node(2, 1, true, true), reset.get(1));
        assertEquals(1, MonitorReach.solve(links.length, 1, plain).size());
    }

    @Test void capsTheDirectLayerWithoutRelabelingDirectTowersAsChildren() {
        var nodes = MonitorReach.solve(18, 64, (a, b, d) -> a == b ? null : GOOD);
        assertEquals(14, nodes.size());
        assertEquals(new MonitorReach.Node(14, 0, true, true), nodes.get(13));
    }
}
