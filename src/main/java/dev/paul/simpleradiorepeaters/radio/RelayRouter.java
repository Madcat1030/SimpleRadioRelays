package dev.paul.simpleradiorepeaters.radio;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.network.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Selects one direct or repeated delivery per receiver, including directed channel conversions and focus exits. */
public final class RelayRouter {
    private RelayRouter() {}
    /** Native sources are allocated only for winning ordinary deliveries or a custom callback. */
    private record Delivery(RadioSource source, float cap, float power, double severity, int hops, int decay,
                            Frequency frequency, RadioSpace.Site departure) {
        RadioSource materialize(RadioSource prepared, RadioReceiver receiver, boolean nativeCrossDimensional) {
            if (source != null) return source;
            RadioSource repeated = copy(prepared);
            repeated.frequencingType = SimpleRadioFrequencing.TRANSMITTER.id;
            repeated.transmissionCap = cap; repeated.transmissionPower = power; repeated.frequencyMedium = frequency;
            ((RelaySourceState) repeated).repeaters$setInheritedSeverity(severity);
            ((RelaySourceState) repeated).repeaters$setRelayCount(hops);
            ((RelaySourceState) repeated).repeaters$setDecayCount(decay);
            // A focus is a local antenna in its target dimension. Localize its carrier for native speakers,
            // whose ordinary cross-dimension gate remains disabled. Stream owners and encoded audio are unchanged.
            if (!nativeCrossDimensional && repeated.origin.level != departure.level()) {
                Vec3 p = departure.position();
                repeated.origin = new WorldlyPosition((float) p.x, (float) p.y, (float) p.z, departure.level());
            }
            repeated.visit(receiver); return repeated;
        }
    }
    public static boolean tryRoute(RadioTransmitter transmitter, RadioSource original, Predicate<RadioRouter> criteria) {
        if (!transmitter.active || !original.isValid() || transmitter.frequency == null || transmitter.frequencingType == null
                || !(transmitter.getLocation().level instanceof ServerLevel level) || !level.getServer().isSameThread()) return false;
        TowerManager manager = TowerManager.get(level.getServer()); if (manager == null) return false;
        RelaySourceState state = (RelaySourceState) original;
        int previousRelays = state.repeaters$getRelayCount(), previousDecay = state.repeaters$getDecayCount();
        if (previousRelays >= dev.paul.simpleradiorepeaters.RepeatersConfig.INSTANCE.maxRelayHops) return false;
        ChannelKey input = ChannelKey.from(transmitter.frequency); TowerManager.Network network = manager.network(level, input);
        if (network.ports().isEmpty()) return false;
        RadioSpace.Site emission = RadioSpace.physical(level, new Vec3(transmitter.getLocation()));
        RadioSource prepared = prepare(original, transmitter, input);
        Map<Integer, Double> entrances = entrances(prepared, emission, input, network);
        if (entrances.isEmpty()) return false;

        RelayFrame frame = state.repeaters$getFrame();
        if (frame == null) { frame = new RelayFrame(); state.repeaters$setFrame(frame); }
        ((RelaySourceState) prepared).repeaters$setFrame(frame); frame.managed = true;
        if (!frame.emitters.add(input + "/" + transmitter.reference)) return true;
        manager.forwardedFrames++;
        // Preserve native custom acceptors and wired fan-out, with independent mutable branch power.
        RadioSource working = copy(original);
        if (transmitter.routerAcceptor != null) transmitter.routerAcceptor.apply(working);
        if (transmitter.distributes) transmitter.distribute(copy(working));
        prepared = prepare(working, transmitter, input);
        entrances = entrances(prepared, emission, input, network);
        List<List<RelayGraph.Reach>> reachable = network.graph().alternatives(entrances, previousRelays, previousDecay,
                transmitter.routeCriteria == null);
        Set<ChannelKey> outputs = new TreeSet<>(); outputs.add(input);
        for (int port = 0; port < reachable.size(); port++) if (!reachable.get(port).isEmpty()) outputs.add(network.ports().get(port).output());
        for (ChannelKey output : outputs) {
            Frequency frequency = output.equals(input) ? transmitter.frequency : ServerSimpleRadioApi.getInstance().frequencies()
                    .getOrCreate(output.frequency(), output.mode());
            for (var recipient : List.copyOf(frequency.getReceivers())) {
                if (!(recipient instanceof RadioReceiver receiver) || !receiver.active || !receiver.valid || receiver.frequencingType == null
                        || receiver.getLocation() == null || original.willShort(receiver)) continue;
                if (criteria != null && !criteria.test(receiver)) continue;
                if (frame.receivers.contains(output + "/" + receiver.reference)) continue;
                if (receiver.owner == null && receiver.position != null && receiver.position.level instanceof ServerLevel receiverLevel
                        && !receiverLevel.hasChunkAt(receiver.position.realLocation())) continue;
                List<RelayDeliverySelection.Candidate<Delivery>> candidates = new ArrayList<>();
                if (output.equals(input)) {
                    RadioSource direct = copy(prepared);
                    if (transmitter.shouldRouteTo(direct, receiver)) {
                        direct = transmitter.prepareSource(direct, receiver);
                        if (direct.transmissionPower > 0 && direct.computeSeverity() < 100
                                && (transmitter.routeCriteria == null || transmitter.routeCriteria.test(direct, receiver))) {
                            double noise = direct.computeSeverity();
                            candidates.add(new RelayDeliverySelection.Candidate<>(new Delivery(direct, direct.transmissionCap,
                                    direct.transmissionPower, noise, previousRelays, previousDecay, frequency, emission),
                                    noise, direct.transmissionPower, previousRelays));
                        }
                    }
                }
                if (receiver.getLocation().level instanceof ServerLevel destinationLevel) {
                    RadioSpace.Site destination = RadioSpace.physical(destinationLevel, new Vec3(receiver.getLocation()));
                    for (int i = 0; i < reachable.size(); i++) {
                        RelayPort port = network.ports().get(i); if (!port.output().equals(output) || reachable.get(i).isEmpty()) continue;
                        for (RadioSpace.Site site : port.sites()) {
                            RadioSpace.Leg leg = RadioSpace.between(site, destination, network.nativeCrossDimensional());
                            if (leg == null) continue;
                            for (RelayGraph.Reach path : reachable.get(i)) {
                                float power = NativeRadioAdapter.departurePower(port.tower(), output, path.decay());
                                var received = NativeRadioAdapter.receiveAtReceiver(leg, power, power,
                                        SimpleRadioFrequencing.TRANSMITTER, receiver, output);
                                if (received == null) continue;
                                double noise = Math.max(path.severity(), received.severity()); float remaining = (float) received.power();
                                Delivery delivery = new Delivery(null, power, remaining, noise, path.hops(), path.decay(), frequency, site);
                                if (transmitter.routeCriteria != null) {
                                    RadioSource repeated = delivery.materialize(prepared, receiver, network.nativeCrossDimensional());
                                    if (!transmitter.routeCriteria.test(repeated, receiver)) continue;
                                    remaining = repeated.transmissionPower;
                                    delivery = new Delivery(repeated, power, remaining, noise, path.hops(), path.decay(), frequency, site);
                                }
                                candidates.add(new RelayDeliverySelection.Candidate<>(delivery, noise, remaining, path.hops()));
                            }
                        }
                    }
                }
                var best = RelayDeliverySelection.select(candidates);
                if (best != null) receiver.accept(best.source().materialize(prepared, receiver, network.nativeCrossDimensional()));
            }
        }
        return true;
    }
    private static Map<Integer, Double> entrances(RadioSource source, RadioSpace.Site emission, ChannelKey channel, TowerManager.Network network) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        FrequencingType type = source.getFrequencingType(); if (source.transmissionPower <= 0 || type == null) return result;
        double inherited = source.computeSeverity();
        for (int i = 0; i < network.ports().size(); i++) {
            RelayPort port = network.ports().get(i); if (!port.input().equals(channel)) continue;
            double best = Double.POSITIVE_INFINITY;
            for (RadioSpace.Site site : port.sites()) {
                RadioSpace.Leg leg = RadioSpace.between(emission, site, network.nativeCrossDimensional());
                if (leg == null) continue;
                var received = NativeRadioAdapter.receiveAtTower(leg, source.transmissionPower, source.transmissionCap,
                        type, port.tower(), channel);
                if (received != null) best = Math.min(best, received.severity());
            }
            if (Double.isFinite(best)) result.put(i, Math.max(inherited, best));
        }
        return result;
    }
    private static RadioSource prepare(RadioSource source, RadioTransmitter transmitter, ChannelKey channel) {
        RadioSource prepared = copy(source);
        if (prepared.frequencingType == -1) {
            prepared.frequencingType = transmitter.frequencingType.id;
            prepared.transmissionCap = transmitter.getPower(channel.mode()); prepared.transmissionPower = prepared.transmissionCap;
        }
        return prepared;
    }
    private static RadioSource copy(RadioSource original) {
        RadioSource next = original.copy(); next.activity = original.activity; return next;
    }
}
