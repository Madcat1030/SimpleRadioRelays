package dev.paul.simpleradiorepeaters.monitor;

import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
import dev.paul.simpleradiorepeaters.terrain.TerrainManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** One-hop, first-departure predictions. Never joins the forwarding graph or emits audio. */
public final class RelaySurvey {
    public record Entry(UUID id, boolean tx, boolean rx, String dimension, @Nullable BlockPos position,
                        double distance, double terrainPercent, String bearing, boolean mapped, BlockPos compassTarget) {
        public String directionLabel() { return tx && rx ? "TX/RX" : rx ? "RX only" : "TX only"; }
    }
    public record Result(ChannelKey channel, BlockPos origin, String dimension, double antenna,
                         int tx, int rx, int other, int pending, List<Entry> entries) {}
    private record Arrival(RadioSpace.Leg leg, double extra, RadioLinkMath.Result signal) {}
    private record Probe(@Nullable Arrival arrival, boolean pending) {}
    private RelaySurvey() {}

    public static Result scan(MinecraftServer server, TowerManager manager, TowerRecord home, ChannelKey channel) {
        boolean dimensional = RadioSpace.nativeCrossDimensional();
        var localSites = RadioSpace.sites(server, home, dimensional);
        int active = 0, txCount = 0, rxCount = 0, pending = 0;
        List<Entry> entries = new ArrayList<>();
        for (TowerRecord target : manager.activeTowers()) {
            if (target.id().equals(home.id())) continue;
            boolean listens = target.ports().stream().anyMatch(p -> p.input().equals(channel));
            boolean broadcasts = target.ports().stream().anyMatch(p -> p.output().equals(channel));
            if (!listens && !broadcasts) continue;
            active++;
            var sites = RadioSpace.sites(server, target, dimensional);
            Probe outbound = listens ? probe(localSites, sites, home, target, channel, dimensional) : new Probe(null, false);
            Probe inbound = broadcasts ? probe(sites, localSites, target, home, channel, dimensional) : new Probe(null, false);
            if (outbound.pending() || inbound.pending()) pending++;
            boolean tx = outbound.arrival() != null, rx = inbound.arrival() != null;
            if (tx) txCount++;
            if (rx) rxCount++;
            if (!tx && !rx) continue;
            Arrival link = rx ? inbound.arrival() : outbound.arrival();
            var leg = link.leg();
            var a = leg.from().position().scale(leg.from().level().dimensionType().coordinateScale());
            var b = leg.to().position().scale(leg.to().level().dimensionType().coordinateScale());
            String bearing = MonitorMath.bearing(rx ? a.x - b.x : b.x - a.x, rx ? a.z - b.z : b.z - a.z);
            BlockPos compass = target.antennaBase();
            if (!rx) {
                // The physical site comes first in native/Foci policies; projections are extra sites.
                var destination = sites.stream().filter(site -> !site.projected()).findFirst().orElse(sites.get(0)).level();
                double scale = destination.dimensionType().coordinateScale();
                var estimate = MonitorMath.compassEstimate(a.x / scale, a.z / scale, bearing,
                        leg.distance(), scale * leg.propagation().distanceMultiplier());
                int y = Math.max(destination.getMinBuildHeight(), Math.min(destination.getMaxBuildHeight() - 1, home.antennaBase().getY()));
                compass = BlockPos.containing(estimate.x(), y, estimate.z());
            }
            entries.add(new Entry(target.id(), tx, rx, target.dimension(), rx ? target.antennaBase() : null,
                    leg.distance(), leg.distance() == 0 ? 0 : link.extra() / leg.distance() * 100,
                    bearing, leg.dimensional(), compass));
        }
        entries.sort(Comparator.comparing((Entry e) -> !e.rx()).thenComparingDouble(Entry::distance).thenComparing(Entry::id));
        return new Result(channel, home.antennaBase(), home.dimension(), NativeRadioAdapter.antennaScore(home),
                txCount, rxCount, active - rxCount, pending, List.copyOf(entries));
    }
    private static Probe probe(List<RadioSpace.Site> from, List<RadioSpace.Site> to, TowerRecord sender,
                               TowerRecord receiver, ChannelKey channel, boolean dimensional) {
        double power = NativeRadioAdapter.departurePower(sender, channel, 1);
        Arrival best = null; boolean pending = false;
        for (var leg : RadioSpace.between(from, to, dimensional)) {
            var signal = NativeRadioAdapter.receiveAtTower(leg, power, power, SimpleRadioFrequencing.TRANSMITTER, receiver, channel);
            if (signal == null) continue;
            double extra = TerrainManager.relayLoss(leg, sender, receiver);
            if (!Double.isFinite(extra)) { pending = true; continue; }
            if (extra > 0) signal = NativeRadioAdapter.receiveAtTower(leg, power, power, SimpleRadioFrequencing.TRANSMITTER, receiver, channel, extra);
            if (signal != null && (best == null || signal.severity() < best.signal().severity()
                    || signal.severity() == best.signal().severity() && signal.power() > best.signal().power()))
                best = new Arrival(leg, extra, signal);
        }
        return new Probe(best, pending);
    }
}
