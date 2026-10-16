package dev.paul.simpleradiorepeaters.radio;

import com.codinglitch.simpleradio.central.FrequencingType;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.network.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Selects one direct or repeated wireless delivery, then uses the original receiving pipeline. */
public final class RelayRouter {
    private RelayRouter() {}
    /** A numeric candidate needs a mutable native source only after it wins or a custom predicate inspects it. */
    private record Delivery(RadioSource source, float cap, float power, double severity, int hops) {
        RadioSource materialize(RadioSource prepared, RadioTransmitter transmitter, RadioReceiver receiver) {
            if (source != null) return source;
            RadioSource repeated = copy(prepared);
            repeated.frequencingType = SimpleRadioFrequencing.TRANSMITTER.id;
            repeated.transmissionCap = cap; repeated.transmissionPower = power;
            repeated.frequencyMedium = transmitter.frequency;
            ((RelaySourceState) repeated).repeaters$setInheritedSeverity(severity);
            ((RelaySourceState) repeated).repeaters$setRelayCount(hops);
            repeated.visit(receiver);
            return repeated;
        }
    }
    public static boolean tryRoute(RadioTransmitter transmitter, RadioSource original, Predicate<RadioRouter> criteria) {
        if (!transmitter.active || !original.isValid() || transmitter.frequency == null || transmitter.frequencingType == null
                || !(transmitter.getLocation().level instanceof ServerLevel level) || !level.getServer().isSameThread()) return false;
        TowerManager manager = TowerManager.get(level.getServer()); if (manager == null) return false;
        int previousRelays = ((RelaySourceState) original).repeaters$getRelayCount();
        if (previousRelays >= dev.paul.simpleradiorepeaters.RepeatersConfig.INSTANCE.maxRelayHops) return false;
        ChannelKey channel = ChannelKey.from(transmitter.frequency);
        TowerManager.Network network = manager.network(level, channel);
        if (network.towers().isEmpty()) return false;

        RadioSource prepared = copy(original);
        if (prepared.frequencingType == -1) {
            prepared.frequencingType = transmitter.frequencingType.id;
            prepared.transmissionCap = transmitter.getPower(channel.mode());
            prepared.transmissionPower = prepared.transmissionCap;
        }
        if (prepared.transmissionPower <= 0) return false;
        FrequencingType sourceType = prepared.getFrequencingType(); if (sourceType == null) return false;
        double inherited = noise(prepared);
        Vec3 sourcePosition = new Vec3(transmitter.getLocation());
        double scale = level.dimensionType().coordinateScale();
        Map<Integer, Double> entrances = new LinkedHashMap<>();
        for (int i = 0; i < network.towers().size(); i++) {
            TowerRecord tower = network.towers().get(i);
            RadioLinkMath.Result received = NativeRadioAdapter.receiveAtTower(sourcePosition.distanceTo(tower.position().getCenter()) * scale,
                    prepared.transmissionPower, prepared.transmissionCap, sourceType, tower, channel);
            if (received != null) entrances.put(i, Math.max(inherited, received.severity()));
        }
        if (entrances.isEmpty()) return false;

        RelayFrame frame = ((RelaySourceState) original).repeaters$getFrame();
        if (frame == null) { frame = new RelayFrame(); ((RelaySourceState) original).repeaters$setFrame(frame); }
        ((RelaySourceState) prepared).repeaters$setFrame(frame);
        frame.managed = true;
        if (!frame.emitters.add(channel + "/" + transmitter.reference)) return true;
        manager.forwardedFrames++;

        // Preserve native custom acceptors and wired fan-out, but isolate mutable branch power.
        RadioSource working = copy(original);
        if (transmitter.routerAcceptor != null) transmitter.routerAcceptor.apply(working);
        if (transmitter.distributes) transmitter.distribute(copy(working));
        prepared = copy(working);
        if (prepared.frequencingType == -1) {
            prepared.frequencingType = transmitter.frequencingType.id;
            prepared.transmissionCap = transmitter.getPower(channel.mode());
            prepared.transmissionPower = prepared.transmissionCap;
        }
        sourceType = prepared.getFrequencingType();
        inherited = noise(prepared);
        entrances.clear();
        for (int i = 0; i < network.towers().size(); i++) {
            TowerRecord tower = network.towers().get(i);
            RadioLinkMath.Result received = NativeRadioAdapter.receiveAtTower(sourcePosition.distanceTo(tower.position().getCenter()) * scale,
                    prepared.transmissionPower, prepared.transmissionCap, sourceType, tower, channel);
            if (received != null) entrances.put(i, Math.max(inherited, received.severity()));
        }
        // Custom predicates may deliberately prefer a weaker/higher-count copy.
        List<List<RelayGraph.Reach>> reachable = network.graph().alternatives(entrances, previousRelays, transmitter.routeCriteria == null);
        for (var recipient : List.copyOf(transmitter.frequency.getReceivers())) {
            if (!(recipient instanceof RadioReceiver receiver) || !receiver.active || !receiver.valid || receiver.frequencingType == null
                    || receiver.getLocation() == null || original.willShort(receiver)) continue;
            if (criteria != null && !criteria.test(receiver)) continue;
            if (frame.receivers.contains(channel + "/" + receiver.reference)) continue;
            if (receiver.owner == null && receiver.position != null && receiver.position.level instanceof ServerLevel receiverLevel
                    && !receiverLevel.hasChunkAt(receiver.position.realLocation())) continue;

            List<RelayDeliverySelection.Candidate<Delivery>> candidates = new ArrayList<>();
            RadioSource direct = copy(prepared);
            if (transmitter.shouldRouteTo(direct, receiver)) {
                direct = transmitter.prepareSource(direct, receiver);
                if (direct.transmissionPower > 0 && noise(direct) < 100
                        && (transmitter.routeCriteria == null || transmitter.routeCriteria.test(direct, receiver)))
                    candidates.add(new RelayDeliverySelection.Candidate<>(new Delivery(direct, direct.transmissionCap,
                            direct.transmissionPower, noise(direct), previousRelays), noise(direct), direct.transmissionPower, previousRelays));
            }

            if (receiver.getLocation().level == level) {
                Vec3 destination = new Vec3(receiver.getLocation());
                for (int i = 0; i < reachable.size(); i++) for (RelayGraph.Reach path : reachable.get(i)) {
                    TowerRecord tower = network.towers().get(i); float power = NativeRadioAdapter.departurePower(tower, channel, path.hops());
                    RadioLinkMath.Result received = NativeRadioAdapter.receiveAtReceiver(tower.position().getCenter().distanceTo(destination) * scale,
                            power, power, SimpleRadioFrequencing.TRANSMITTER, receiver, channel);
                    if (received == null) continue;
                    double severity = Math.max(path.severity(), received.severity());
                    float remaining = (float) received.power();
                    Delivery delivery = new Delivery(null, power, remaining, severity, path.hops());
                    if (transmitter.routeCriteria != null) {
                        RadioSource repeated = delivery.materialize(prepared, transmitter, receiver);
                        if (!transmitter.routeCriteria.test(repeated, receiver)) continue;
                        remaining = repeated.transmissionPower;
                        delivery = new Delivery(repeated, power, remaining, severity, path.hops());
                    }
                    candidates.add(new RelayDeliverySelection.Candidate<>(delivery, severity, remaining, path.hops()));
                }
            }
            var best = RelayDeliverySelection.select(candidates);
            if (best != null) receiver.accept(best.source().materialize(prepared, transmitter, receiver));
        }
        return true;
    }
    private static RadioSource copy(RadioSource original) {
        RadioSource next = original.copy(); next.activity = original.activity; return next;
    }
    private static double noise(RadioSource source) { return source.computeSeverity(); }
}
