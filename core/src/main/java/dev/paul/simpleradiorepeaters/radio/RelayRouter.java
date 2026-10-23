package dev.paul.simpleradiorepeaters.radio;

import static dev.paul.simpleradiorepeaters.radio.RelayPerformance.Counter.*;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.*;

import dev.paul.simpleradiorepeaters.network.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Predicate;

/**
 * Selects one direct or repeated delivery per receiver, including directed channel conversions and
 * focus exits.
 */
public final class RelayRouter {
    private RelayRouter() {}

    /** Native sources are allocated only for winning ordinary deliveries or a custom callback. */
    private record Delivery(
            RadioSource source,
            float cap,
            float power,
            double severity,
            int hops,
            int decay,
            Frequency frequency,
            RadioSpace.Site departure) {
        RadioSource materialize(
                RadioSource prepared, RadioReceiver receiver, boolean nativeCrossDimensional) {
            if (source != null) return source;
            RadioSource repeated = copy(prepared);
            repeated.frequencingType = SimpleRadioFrequencing.TRANSMITTER.id;
            repeated.transmissionCap = cap;
            repeated.transmissionPower = power;
            repeated.frequencyMedium = frequency;
            ((RelaySourceState) repeated).repeaters$setInheritedSeverity(severity);
            ((RelaySourceState) repeated).repeaters$setRelayCount(hops);
            ((RelaySourceState) repeated).repeaters$setDecayCount(decay);
            // A focus is a local antenna in its target dimension. Localize its carrier for native
            // speakers,
            // whose ordinary cross-dimension gate remains disabled. Stream owners and encoded audio
            // are unchanged.
            if (!nativeCrossDimensional && repeated.origin.level != departure.level()) {
                Vec3 p = departure.position();
                repeated.origin =
                        new WorldlyPosition(
                                (float) p.x, (float) p.y, (float) p.z, departure.level());
            }
            repeated.visit(receiver);
            return repeated;
        }
    }

    public static boolean tryRoute(
            RadioTransmitter transmitter, RadioSource original, Predicate<RadioRouter> criteria) {
        if (!transmitter.active
                || !original.isValid()
                || transmitter.frequency == null
                || transmitter.frequencingType == null
                || !(transmitter.getLocation().level instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return false;
        RelayPerformance.Sample sample = RelayPerformance.begin(level.getServer());
        try {
            boolean handled = routeObserved(transmitter, original, criteria, level, sample);
            if (sample != null && handled) sample.add(TAKEN_OVER);
            return handled;
        } catch (RuntimeException | Error failure) {
            if (sample != null) sample.add(ROUTE_EXCEPTIONS);
            throw failure;
        } finally {
            if (sample != null) sample.close();
        }
    }

    private static boolean routeObserved(
            RadioTransmitter transmitter,
            RadioSource original,
            Predicate<RadioRouter> criteria,
            ServerLevel level,
            RelayPerformance.Sample sample) {
        TowerManager manager = TowerManager.get(level.getServer());
        if (manager == null) return false;
        RelaySourceState state = (RelaySourceState) original;
        int previousRelays = state.repeaters$getRelayCount(),
                previousDecay = state.repeaters$getDecayCount();
        if (previousRelays >= dev.paul.simpleradiorepeaters.RepeatersConfig.INSTANCE.maxRelayHops) {
            if (sample != null) sample.add(HOP_LIMIT);
            return false;
        }
        ChannelKey input = ChannelKey.from(transmitter.frequency);
        if (sample != null) {
            sample.flow(
                    transmitter.reference,
                    level.dimension().location().toString(),
                    input,
                    previousRelays,
                    previousDecay);
            sample.phase(RelayPerformance.Phase.NETWORK_LOOKUP);
        }
        TowerManager.Network network = manager.network(level, input);
        if (network.ports().isEmpty()) {
            if (sample != null) sample.add(NO_PORTS);
            return false;
        }
        if (sample != null) sample.phase(RelayPerformance.Phase.INITIAL_ENTRANCES);
        RadioSpace.Site emission = RadioSpace.physical(level, new Vec3(transmitter.getLocation()));
        RadioSource prepared = prepare(original, transmitter, input);
        if (sample != null) sample.emission(emission.position());
        Map<Integer, Double> entrances =
                entrances(prepared, emission, input, network, sample, true);
        if (entrances.isEmpty()) {
            if (sample != null) sample.add(NO_ENTRANCE);
            return false;
        }

        RelayFrame frame = state.repeaters$getFrame();
        if (frame == null) {
            frame = new RelayFrame();
            state.repeaters$setFrame(frame);
        }
        ((RelaySourceState) prepared).repeaters$setFrame(frame);
        frame.managed = true;
        if (!frame.emitters.add(input + "/" + transmitter.reference)) {
            if (sample != null) sample.add(DUPLICATE_EMITTERS);
            return true;
        }
        manager.forwardedFrames++;
        if (sample != null) {
            sample.add(FORWARDED_FRAMES);
            sample.phase(RelayPerformance.Phase.CALLBACKS_AND_WIRES);
        }
        // Preserve native custom acceptors and wired fan-out, with independent mutable branch
        // power.
        RadioSource working = copy(original);
        if (transmitter.routerAcceptor != null) {
            if (sample != null) sample.add(ROUTER_ACCEPTORS);
            transmitter.routerAcceptor.apply(working);
        }
        if (transmitter.distributes) {
            if (sample != null) sample.add(WIRE_DISTRIBUTIONS);
            transmitter.distribute(copy(working));
        }
        prepared = prepare(working, transmitter, input);
        if (sample != null) sample.phase(RelayPerformance.Phase.FINAL_ENTRANCES);
        entrances = entrances(prepared, emission, input, network, sample, false);
        if (sample != null) sample.phase(RelayPerformance.Phase.PATHS);
        List<List<RelayGraph.Reach>> reachable =
                network.graph()
                        .alternatives(
                                entrances,
                                previousRelays,
                                previousDecay,
                                transmitter.routeCriteria == null);
        if (sample != null) {
            sample.phase(RelayPerformance.Phase.OUTPUTS);
            sample.add(REACHABLE_OPTIONS, reachable.stream().mapToLong(List::size).sum());
        }
        Set<ChannelKey> outputs = new TreeSet<>();
        outputs.add(input);
        for (int port = 0; port < reachable.size(); port++)
            if (!reachable.get(port).isEmpty()) outputs.add(network.ports().get(port).output());
        for (ChannelKey output : outputs) {
            if (sample != null) {
                sample.phase(RelayPerformance.Phase.OUTPUTS);
                sample.add(OUTPUT_CHANNELS);
            }
            Frequency frequency =
                    output.equals(input)
                            ? transmitter.frequency
                            : ServerSimpleRadioApi.getInstance()
                                    .frequencies()
                                    .get(output.frequency(), output.mode());
            // Traversal above is independent of native listener registration, including
            // intermediate translations.
            if (frequency == null) {
                if (sample != null) sample.add(MISSING_FREQUENCIES);
                continue;
            }
            for (var recipient : List.copyOf(frequency.getReceivers())) {
                if (sample != null) {
                    sample.phase(RelayPerformance.Phase.RECEIVER_FILTER);
                    sample.add(RECEIVER_VISITS);
                }
                if (!(recipient instanceof RadioReceiver receiver)
                        || !receiver.active
                        || !receiver.valid
                        || receiver.frequencingType == null
                        || receiver.getLocation() == null
                        || original.willShort(receiver)) {
                    if (sample != null) sample.add(SKIPPED_STATE);
                    continue;
                }
                if (criteria != null && !criteria.test(receiver)) {
                    if (sample != null) sample.add(SKIPPED_CRITERIA);
                    continue;
                }
                if (frame.receivers.contains(output + "/" + receiver.reference)) {
                    if (sample != null) sample.add(SKIPPED_DUPLICATE);
                    continue;
                }
                if (receiver.owner == null
                        && receiver.position != null
                        && receiver.position.level instanceof ServerLevel receiverLevel
                        && !receiverLevel.hasChunkAt(receiver.position.realLocation())) {
                    if (sample != null) sample.add(SKIPPED_UNLOADED);
                    continue;
                }
                if (sample != null) {
                    sample.add(RECEIVERS_ELIGIBLE);
                    sample.phase(RelayPerformance.Phase.DIRECT_CANDIDATES);
                }
                List<RelayDeliverySelection.Candidate<Delivery>> candidates = new ArrayList<>();
                if (output.equals(input)) {
                    if (sample != null) sample.add(DIRECT_RF_CHECKS);
                    RadioSource direct = copy(prepared);
                    if (transmitter.shouldRouteTo(direct, receiver)) {
                        direct = transmitter.prepareSource(direct, receiver);
                        if (direct.transmissionPower > 0
                                && direct.computeSeverity() < 100
                                && (transmitter.routeCriteria == null
                                        || checkCriteria(transmitter, direct, receiver, sample))) {
                            double noise = direct.computeSeverity();
                            if (sample != null) sample.add(DIRECT_CANDIDATES);
                            candidates.add(
                                    new RelayDeliverySelection.Candidate<>(
                                            new Delivery(
                                                    direct,
                                                    direct.transmissionCap,
                                                    direct.transmissionPower,
                                                    noise,
                                                    previousRelays,
                                                    previousDecay,
                                                    frequency,
                                                    emission),
                                            noise,
                                            direct.transmissionPower,
                                            previousRelays));
                        }
                    }
                }
                if (sample != null) sample.phase(RelayPerformance.Phase.RELAY_CANDIDATES);
                if (receiver.getLocation().level instanceof ServerLevel destinationLevel) {
                    RadioSpace.Site destination =
                            RadioSpace.physical(destinationLevel, new Vec3(receiver.getLocation()));
                    if (sample != null) sample.receiver(receiver, destination);
                    for (int i = 0; i < reachable.size(); i++) {
                        if (sample != null) sample.add(PORT_VISITS);
                        RelayPort port = network.ports().get(i);
                        if (!port.output().equals(output) || reachable.get(i).isEmpty()) continue;
                        if (sample != null) sample.add(MATCHING_PORTS);
                        for (RadioSpace.Site site : port.sites()) {
                            if (sample != null) sample.add(EGRESS_SITE_PAIRS);
                            RadioSpace.Leg leg =
                                    RadioSpace.between(
                                            site, destination, network.nativeCrossDimensional());
                            if (leg == null) continue;
                            for (RelayGraph.Reach path : reachable.get(i)) {
                                if (sample != null) sample.add(EGRESS_RF_CHECKS);
                                float power =
                                        NativeRadioAdapter.departurePower(
                                                port.tower(), output, path.decay());
                                var received =
                                        NativeRadioAdapter.receiveAtReceiver(
                                                leg,
                                                power,
                                                power,
                                                SimpleRadioFrequencing.TRANSMITTER,
                                                receiver,
                                                output);
                                if (received == null) continue;
                                double noise = Math.max(path.severity(), received.severity());
                                float remaining = (float) received.power();
                                Delivery delivery =
                                        new Delivery(
                                                null,
                                                power,
                                                remaining,
                                                noise,
                                                path.hops(),
                                                path.decay(),
                                                frequency,
                                                site);
                                if (transmitter.routeCriteria != null) {
                                    RadioSource repeated =
                                            delivery.materialize(
                                                    prepared,
                                                    receiver,
                                                    network.nativeCrossDimensional());
                                    if (!checkCriteria(transmitter, repeated, receiver, sample))
                                        continue;
                                    remaining = repeated.transmissionPower;
                                    delivery =
                                            new Delivery(
                                                    repeated,
                                                    power,
                                                    remaining,
                                                    noise,
                                                    path.hops(),
                                                    path.decay(),
                                                    frequency,
                                                    site);
                                }
                                if (sample != null) sample.add(RELAY_CANDIDATES);
                                candidates.add(
                                        new RelayDeliverySelection.Candidate<>(
                                                delivery, noise, remaining, path.hops()));
                            }
                        }
                    }
                }
                if (sample != null) {
                    sample.phase(RelayPerformance.Phase.SELECT_WINNER);
                    sample.add(RECEIVER_SELECTIONS);
                }
                var best = RelayDeliverySelection.select(candidates);
                if (best != null) {
                    if (sample != null) {
                        sample.phase(RelayPerformance.Phase.DELIVERY);
                        sample.add(DELIVERY_ATTEMPTS);
                    }
                    receiver.accept(
                            best.source()
                                    .materialize(
                                            prepared, receiver, network.nativeCrossDimensional()));
                }
            }
        }
        return true;
    }

    private static boolean checkCriteria(
            RadioTransmitter transmitter,
            RadioSource source,
            RadioReceiver receiver,
            RelayPerformance.Sample sample) {
        if (sample != null) sample.add(CUSTOM_ROUTE_CRITERIA);
        return transmitter.routeCriteria.test(source, receiver);
    }

    private static Map<Integer, Double> entrances(
            RadioSource source,
            RadioSpace.Site emission,
            ChannelKey channel,
            TowerManager.Network network,
            RelayPerformance.Sample sample,
            boolean initial) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        if (sample != null) sample.add(INGRESS_PASSES);
        FrequencingType type = source.getFrequencingType();
        if (source.transmissionPower <= 0 || type == null) {
            if (sample != null) sample.input(source, type, Double.NaN);
            return result;
        }
        double inherited = source.computeSeverity();
        if (sample != null) sample.input(source, type, inherited);
        for (int i = 0; i < network.ports().size(); i++) {
            RelayPort port = network.ports().get(i);
            if (!port.input().equals(channel)) continue;
            double best = Double.POSITIVE_INFINITY;
            for (RadioSpace.Site site : port.sites()) {
                if (sample != null) sample.add(INGRESS_SITE_PAIRS);
                RadioSpace.Leg leg =
                        RadioSpace.between(emission, site, network.nativeCrossDimensional());
                if (leg == null) continue;
                if (sample != null) {
                    sample.add(INGRESS_RF_CHECKS);
                    sample.add(initial ? INITIAL_INGRESS_RF_CHECKS : FINAL_INGRESS_RF_CHECKS);
                }
                var received =
                        NativeRadioAdapter.receiveAtTower(
                                leg,
                                source.transmissionPower,
                                source.transmissionCap,
                                type,
                                port.tower(),
                                channel);
                if (received != null) {
                    if (sample != null) sample.add(INGRESS_RF_SUCCESSES);
                    best = Math.min(best, received.severity());
                }
            }
            if (Double.isFinite(best)) result.put(i, Math.max(inherited, best));
        }
        return result;
    }

    private static RadioSource prepare(
            RadioSource source, RadioTransmitter transmitter, ChannelKey channel) {
        RadioSource prepared = copy(source);
        if (prepared.frequencingType == -1) {
            prepared.frequencingType = transmitter.frequencingType.id;
            prepared.transmissionCap = transmitter.getPower(channel.mode());
            prepared.transmissionPower = prepared.transmissionCap;
        }
        return prepared;
    }

    private static RadioSource copy(RadioSource original) {
        RelayPerformance.count(ROUTER_SOURCE_COPIES);
        RadioSource next = original.copy();
        next.activity = original.activity;
        return next;
    }
}
