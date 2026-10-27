package dev.paul.simpleradiorepeaters.radio;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.core.registry.items.*;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.*;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.network.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Operator-requested snapshot only: no terrain loads, audio emission, device repair or packet-path sampling. */
public final class RelayDiagnostics {
    private RelayDiagnostics() {}
    public static List<String> describe(ServerPlayer player) {
        return describe(player.serverLevel(), player.position(), player.getMainHandItem());
    }
    public static List<String> describe(ServerLevel level, Vec3 position, ItemStack held) {
        return RelayPerformance.diagnostic(level.getServer(), () -> describeObserved(level, position, held));
    }
    private static List<String> describeObserved(ServerLevel level, Vec3 position, ItemStack held) {
        TowerManager manager = TowerManager.get(level.getServer());
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN tick=" + level.getServer().getTickCount() + " position=" + level.dimension().location() + " " + position);
        FabricLoader.getInstance().getAllMods().stream().filter(mod -> mod.getMetadata().getId().startsWith("simpleradio")
                || mod.getMetadata().getId().equals("voicechat")).sorted(Comparator.comparing(mod -> mod.getMetadata().getId()))
                .forEach(mod -> lines.add("MOD " + mod.getMetadata().getId() + " " + mod.getMetadata().getVersion().getFriendlyString()));
        lines.add("CONFIG persistent=" + RepeatersConfig.INSTANCE.persistentUnloadedTowers + " maxHops=" + RepeatersConfig.INSTANCE.maxRelayHops
                + " nativeDimensions=" + SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional
                + " relayDimensions=" + RadioSpace.nativeCrossDimensional());
        lines.add("ANTENNA BALANCE enabled=" + RepeatersConfig.INSTANCE.antennaDependentBasePower
                + " fullBaseAt=" + TowerPower.FULL_POWER_ANTENNA);
        var tx = SimpleRadioFrequencing.TRANSMITTER; var rx = SimpleRadioFrequencing.RECEIVER;
        lines.add("RF txAM=" + tx.transmissionPowerAM + " txFM=" + tx.transmissionPowerFM + " txAptitude=" + tx.antennaAptitude
                + " loss=" + tx.transmissionDiminishment + " method=" + tx.diminishmentMethod
                + " rxBase=" + rx.receptionPower + " rxAptitude=" + rx.antennaAptitude);
        lines.add("TERRAIN mode=" + RepeatersConfig.INSTANCE.terrain.mode.id() + " " + dev.paul.simpleradiorepeaters.terrain.TerrainManager.statistics());
        if (manager == null) { lines.add("Manager unavailable"); return List.copyOf(lines); }
        lines.add(manager.summary());
        Set<ChannelKey> channels = new TreeSet<>();
        for (TowerRecord tower : manager.records().stream().sorted(Comparator.comparing(t -> t.id().toString())).toList()) {
            channels.addAll(tower.channels());
            lines.add("SAVED " + tower.id() + " " + tower.dimension() + " " + tower.position().toShortString()
                    + " eligible=" + tower.eligible() + " enabled=" + tower.enabled() + " verified=" + tower.verified()
                    + " antennaBase=" + tower.antennaBase().toShortString()
                    + " antennaTip=" + tower.antennaTip()
                    + " elevationMultiplier=" + dev.paul.simpleradiorepeaters.terrain.TerrainManager.elevationMultiplier(tower)
                    + " antennaRaw=" + tower.antennaScore() + " baseMultiplier="
                    + TowerPower.baseMultiplier(tower.antennaScore(), RepeatersConfig.INSTANCE.antennaDependentBasePower)
                    + " antennaEffective="
                    + RelayPower.antennaScore(tower.antennaScore(), tower.attachment().antennaMultiplier())
                    + " catalyst=" + tower.catalystItem() + " efficiency=" + tower.efficiency()
                    + " upgrade=" + tower.attachment() + " reset=" + tower.attachment().resetsDecay()
                    + " controller=" + tower.cardBank() + " ports=" + tower.ports());
            var dimension = net.minecraft.resources.ResourceLocation.tryParse(tower.dimension());
            var world = dimension == null ? null : level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimension));
            if (world == null) { lines.add("LIVE " + tower.id() + " dimension missing"); continue; }
            var chunk = BankConnection.chunk(world, tower.position());
            if (chunk == null) { lines.add("LIVE " + tower.id() + " chunk unloaded"); continue; }
            var entity = BankConnection.entity(world, tower.position());
            lines.add("LIVE " + tower.id() + " block=" + chunk.getBlockState(tower.position()) + " entity="
                    + (entity == null ? "pending/missing" : entity.getClass().getSimpleName() + " nbt=" + entity.saveWithoutMetadata()));
            if (tower.cardBank() != null) {
                var module = BankConnection.entity(world, tower.cardBank().position());
                lines.add("MODULE " + tower.id() + " " + (module == null ? "pending/missing" : module.getClass().getSimpleName()
                        + " nbt=" + module.saveWithoutMetadata()));
            }
        }
        var api = ServerSimpleRadioApi.getInstance();
        List<RadioRouter> endpoints = api.getRouters().stream().filter(r -> r instanceof RadioReceiver || r instanceof RadioTransmitter)
                .map(r -> (RadioRouter) r).toList();
        for (RadioRouter router : endpoints) {
            Frequency frequency = router instanceof RadioReceiver receiver ? receiver.frequency : ((RadioTransmitter) router).frequency;
            if (frequency == null) continue;
            ChannelKey channel = ChannelKey.from(frequency); channels.add(channel);
            Frequency live = api.frequencies().get(channel.frequency(), channel.mode());
            boolean subscribed = live != null && (router instanceof RadioReceiver ? live.getReceivers().contains(router) : live.getTransmitters().contains(router));
            lines.add("ENDPOINT " + router.getClass().getSimpleName() + " " + router.reference + " " + channel
                    + " location=" + router.getLocation() + " active=" + router.active + " valid=" + router.valid
                    + " canonical=" + (frequency == live) + " subscribed=" + subscribed
                    + " children=" + router.routers.size());
        }
        for (ChannelKey channel : channels) {
            Frequency live = api.frequencies().get(channel.frequency(), channel.mode());
            lines.add("FREQUENCY " + channel + " registered=" + (live != null) + " receivers="
                    + (live == null ? 0 : live.getReceivers().size()) + " transmitters=" + (live == null ? 0 : live.getTransmitters().size()));
        }
        ChannelKey input = ChannelKey.from(held.getTag());
        FrequencingType sourceType = held.getItem() instanceof WalkieTalkieItem ? SimpleRadioFrequencing.WALKIE_TALKIE
                : held.getItem() instanceof TransceiverItem ? SimpleRadioFrequencing.TRANSCEIVER : null;
        if (input != null && sourceType != null) {
            var inspection = manager.inspectNetwork(level, input);
            lines.add("CACHE " + input + " present=" + (inspection.cached() != null) + " samePorts="
                    + (inspection.cached() != null && inspection.cached().ports().equals(inspection.fresh().ports()))
                    + " sameFirstLegs=" + (inspection.cached() != null && inspection.cached().edges().equals(inspection.fresh().edges())));
            if (inspection.cached() != null) probe("cached", lines, inspection.cached(), level, position, input, sourceType, endpoints);
            probe("fresh", lines, inspection.fresh(), level, position, input, sourceType, endpoints);
        } else lines.add("PROBE omitted: hold the tuned transmitting walkie-talkie or transceiver in the main hand");
        lines.add("END (read-only; fresh graph was not installed)");
        return List.copyOf(lines);
    }
    private static void probe(String label, List<String> lines, TowerManager.Network network, ServerLevel level, Vec3 position,
                              ChannelKey channel, FrequencingType type, List<RadioRouter> endpoints) {
        double power = type.getTransmissionPower(channel.mode()); Map<Integer, Double> entrances = new LinkedHashMap<>();
        var emission = RadioSpace.physical(level, position);
        UUID emitter = endpoints.stream().filter(r -> r instanceof RadioTransmitter t && t.frequency != null
                && ChannelKey.from(t.frequency).equals(channel) && t.frequencingType == type && t.getLocation() != null
                && t.getLocation().level == level && new Vec3(t.getLocation()).distanceToSqr(position) <= 256)
                .sorted(Comparator.comparingDouble(r -> new Vec3(r.getLocation()).distanceToSqr(position)))
                .map(r -> r.reference).findFirst().orElse(new UUID(0, 0));
        for (int i = 0; i < network.ports().size(); i++) {
            RelayPort port = network.ports().get(i); if (!port.input().equals(channel)) continue;
            double severity = Double.POSITIVE_INFINITY;
            for (var site : port.sites()) {
                var leg = RadioSpace.between(emission, site, network.nativeCrossDimensional());
                if (leg == null) continue;
                var arrival = NativeRadioAdapter.receiveAtTower(leg, power, power, type, port.tower(), channel,
                        dev.paul.simpleradiorepeaters.terrain.TerrainManager.observedIngressLoss(leg, emitter, port.tower()));
                if (arrival != null) severity = Math.min(severity, arrival.severity());
            }
            if (Double.isFinite(severity)) entrances.put(i, severity);
        }
        lines.add("PROBE " + label + " channel=" + channel + " power=" + power + " entrances=" + entrances);
        var reachable = network.graph().alternatives(entrances, 0, 0, true);
        for (int i = 0; i < network.ports().size(); i++) {
            RelayPort port = network.ports().get(i);
            lines.add("ROUTE " + label + " " + port.tower().position().toShortString() + " " + port.input() + " -> "
                    + port.output() + " alternatives=" + reachable.get(i));
        }
        for (RadioRouter router : endpoints) if (router instanceof RadioReceiver receiver && receiver.frequency != null
                && receiver.frequencingType != null && receiver.getLocation() != null
                && receiver.getLocation().level instanceof ServerLevel destinationLevel) {
            ChannelKey output = ChannelKey.from(receiver.frequency); double strongest = -1; double cleanest = Double.POSITIVE_INFINITY;
            for (int i = 0; i < network.ports().size(); i++) {
                RelayPort port = network.ports().get(i); if (!port.output().equals(output)) continue;
                for (var path : reachable.get(i)) for (var site : port.sites()) {
                    var leg = RadioSpace.between(site, RadioSpace.physical(destinationLevel, new Vec3(receiver.getLocation())), network.nativeCrossDimensional());
                    if (leg == null) continue;
                    double departure = NativeRadioAdapter.departurePower(port.tower(), output, path.decay());
                    var arrival = NativeRadioAdapter.receiveAtReceiver(leg, departure, departure, SimpleRadioFrequencing.TRANSMITTER, receiver, output,
                            dev.paul.simpleradiorepeaters.terrain.TerrainManager.observedEgressLoss(leg, port.tower(), receiver));
                    if (arrival == null) continue;
                    strongest = Math.max(strongest, arrival.power()); cleanest = Math.min(cleanest, Math.max(path.severity(), arrival.severity()));
                }
            }
            lines.add("RF-CANDIDATE " + label + " " + receiver.reference + " " + output + " reachable=" + (strongest >= 0)
                    + " strongestCarrier=" + strongest + " cleanestNoise=" + cleanest
                    + " (RF-only; registration, active state and deduplication are separate)");
        }
    }
}
