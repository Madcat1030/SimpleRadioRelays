package dev.paul.simpleradiorepeaters.monitor;

import dev.paul.simpleradiorepeaters.radio.RadioLinkMath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MonitorNetworkReachTest {
    private static final RadioLinkMath.Result SIGNAL = new RadioLinkMath.Result(100, 12);

    @Test void mapsBothDirectionsPastTwoHopsButStopsAtTheFirstUnheardTower() {
        var links = new RadioLinkMath.Result[9][9];
        for (int i = 0; i < 4; i++) links[i][i + 1] = links[i + 1][i] = SIGNAL;
        links[5][3] = SIGNAL; // Heard through three relays, but unreachable on the way back.
        links[3][6] = links[6][7] = SIGNAL; // Tower 6 is the first unheard tower.
        MonitorReach.Links receive = (from, to, decay) -> links[from][to];
        var survey = MonitorNetworkReach.solve(links.length, 64, receive, ignored -> false);
        assertEquals(List.of(
                new MonitorReach.Node(1, 0, true, true), new MonitorReach.Node(2, 1, true, true),
                new MonitorReach.Node(3, 2, true, true), new MonitorReach.Node(4, 3, true, true),
                new MonitorReach.Node(5, -1, false, true), new MonitorReach.Node(6, 3, true, false)),
                survey.nodes());
        assertTrue(survey.connections().contains(new MonitorNetworkReach.Connection(5, 3)));
        assertTrue(survey.connections().contains(new MonitorNetworkReach.Connection(3, 6)));
        assertFalse(survey.connections().stream().anyMatch(c -> c.from() == 6 || c.to() == 7));
        assertEquals(3, MonitorNetworkReach.solve(links.length, 3, receive, ignored -> false).nodes().size());
    }

    @Test void decayCanBreakTheReturnPathAndAnEnderResetCanRestoreIt() {
        MonitorReach.Links plain = (from, to, decay) -> Math.abs(from - to) == 1 && decay <= 2 ? SIGNAL : null;
        var noReset = MonitorNetworkReach.solve(6, 64, plain, ignored -> false).nodes();
        assertEquals(3, noReset.size());
        assertEquals(new MonitorReach.Node(3, 2, true, false), noReset.get(2));

        MonitorReach.Links boosted = (from, to, decay) -> plain.receive(from, to, from == 2 ? 1 : decay);
        var reset = MonitorNetworkReach.solve(6, 64, boosted, from -> from == 2).nodes();
        assertEquals(4, reset.size());
        assertTrue(reset.stream().allMatch(node -> node.tx() && node.rx()));
    }

    @Test void limitsAConnectedChainEvenWhenTheServerAllowsMoreHops() {
        var nodes = MonitorNetworkReach.solve(18, 64,
                (from, to, decay) -> Math.abs(from - to) == 1 ? SIGNAL : null, ignored -> false).nodes();
        assertEquals(12, nodes.size());
        assertEquals(new MonitorReach.Node(12, 11, true, true), nodes.get(11));
    }
}
