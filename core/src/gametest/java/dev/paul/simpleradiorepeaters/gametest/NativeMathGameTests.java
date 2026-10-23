package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.network.ChannelKey;
import dev.paul.simpleradiorepeaters.network.TowerRecord;
import dev.paul.simpleradiorepeaters.radio.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Native objects are an independent oracle here, never allocated for speculative gameplay links. */
public class NativeMathGameTests implements FabricGameTest {
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    public record Observation(boolean reached, boolean delivered, double power, double severity) {}

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_tower_departure_power_matches_the_adapter_before_catalyst_decay(GameTestHelper helper) {
        var world = helper.getLevel(); var pos = new net.minecraft.core.BlockPos(1000000, 64, -1000000);
        for (var mode : Frequency.Modulation.values()) for (var attachment : new RelayAttachment[]{RelayAttachment.NONE,
                RelayAttachment.COPPER_AMPLIFIER, RelayAttachment.REDSTONE_AMPLIFIER, RelayAttachment.AMPLIFIER}) {
            var frequency = ServerSimpleRadioApi.getInstance().frequencies().getOrCreate("097.85", mode);
            var channel = ChannelKey.from(frequency);
            var tower = new TowerRecord(UUID.randomUUID(), world.dimension().location().toString(), pos, java.util.Set.of(channel),
                    true, RelayCatalysts.GILDED, true, 32, java.util.Set.of(), null, attachment, null);
            var location = pos.getCenter();
            var nativeTower = new RadioTransmitter(frequency, new WorldlyPosition((float) location.x, (float) location.y, (float) location.z, world))
                    .frequencingType(SimpleRadioFrequencing.TRANSMITTER);
            nativeTower.antennaPower = (int) (32 * attachment.antennaMultiplier());
            check(nativeTower.getPower(mode) == NativeRadioAdapter.towerPower(tower, channel),
                    "Native departure must agree for " + mode + "/" + attachment);
        }
        check(!world.hasChunkAt(pos), "Native power probes must not load the antenna chunk");
        helper.succeed();
    }

    public static Observation compare(ServerLevel from, Vec3 origin, ServerLevel to, Vec3 destination,
                                      Frequency.Modulation mode, FrequencingType type, float power, float cap, String label) {
        return compare(from, origin, to, destination, mode, type, power, cap, label, 0);
    }
    private static Observation compare(ServerLevel from, Vec3 origin, ServerLevel to, Vec3 destination,
                                       Frequency.Modulation mode, FrequencingType type, float power, float cap, String label, int antenna) {
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        var frequency = frequencies.getOrCreate("097.81", mode);
        var a = new WorldlyPosition((float) origin.x, (float) origin.y, (float) origin.z, from);
        var b = new WorldlyPosition((float) destination.x, (float) destination.y, (float) destination.z, to);
        var sender = new RadioTransmitter(frequency, a).frequencingType(type);
        var receiver = new RadioReceiver(frequency, b).frequencingType(SimpleRadioFrequencing.RECEIVER);
        receiver.antennaPower = antenna;
        var source = new RadioSource(UUID.randomUUID(), a, new byte[]{1}, 1);
        source.frequencingType = type.id; source.frequencyMedium = frequency;
        source.transmissionPower = power; source.transmissionCap = cap;
        // Compare the whole native gate, then travel and severity. travel alone is not an acceptance oracle.
        boolean reached = sender.shouldRouteTo(source, receiver);
        if (reached) source = sender.prepareSource(source, receiver);
        boolean delivered = reached && source.transmissionPower > 0 && source.computeSeverity() < 100;
        var leg = RadioSpace.between(RadioSpace.physical(from, new Vec3(a)), RadioSpace.physical(to, new Vec3(b)),
                SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional);
        var channel = ChannelKey.from(frequency);
        var tower = new TowerRecord(UUID.randomUUID(), to.dimension().location().toString(), b.blockPos(), java.util.Set.of(channel),
                true, RelayCatalysts.GILDED, true, antenna, java.util.Set.of());
        var actual = leg == null ? null : antenna == 0
                ? NativeRadioAdapter.receiveAtReceiver(leg, power, cap, type, receiver, channel)
                : NativeRadioAdapter.receiveAtTower(leg, power, cap, type, tower, channel);
        check(delivered == (actual != null), label + ": native delivery=" + delivered + ", adapter=" + actual);
        if (delivered) {
            // Native positions/distances and the final power store use float. Budget their ULPs, not a broad percentage.
            double largest = Math.max(new Vec3(a).scale(from.dimensionType().coordinateScale()).length(),
                    new Vec3(b).scale(to.dimensionType().coordinateScale()).length());
            double loss = type.transmissionDiminishment + (leg.dimensional()
                    ? SimpleRadioLibrary.SERVER_CONFIG.frequency.dimensionalInterference * leg.propagation().dimensionalMultiplier()
                    / (mode == Frequency.Modulation.AMPLITUDE ? 2 : 1) : 0);
            double tolerance = Math.max(.001, 4 * Math.ulp((float) largest) * leg.propagation().distanceMultiplier() * Math.abs(loss)
                    + 4 * Math.ulp(power));
            check(Math.abs(actual.power() - source.transmissionPower) <= tolerance,
                    label + ": native power=" + source.transmissionPower + ", adapter=" + actual.power() + ", tolerance=" + tolerance);
            double severityTolerance = .001 + tolerance * 100 / type.getDiminishThreshold(mode);
            check(Math.abs(actual.severity() - source.computeSeverity()) <= severityTolerance,
                    label + ": native severity=" + source.computeSeverity() + ", adapter=" + actual.severity());
        }
        check(!from.hasChunkAt(a.blockPos()) && !to.hasChunkAt(b.blockPos()), label + ": numeric/native probes must not load terrain");
        return new Observation(reached, delivered, source.transmissionPower, source.computeSeverity());
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_radio_math_matches_across_modes_dimensions_and_distant_coordinates(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (var dimension : java.util.List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            ServerLevel world = server.getLevel(dimension);
            for (var mode : Frequency.Modulation.values()) {
                for (var type : new FrequencingType[]{SimpleRadioFrequencing.TRANSMITTER, SimpleRadioFrequencing.TRANSCEIVER,
                        SimpleRadioFrequencing.WALKIE_TALKIE}) {
                    for (double coordinate : new double[]{16000.5, 1000000.5, -1000000.5}) {
                        Vec3 origin = new Vec3(coordinate, 64.5, coordinate);
                        double scale = world.dimensionType().coordinateScale();
                        for (double distance : new double[]{1, 80, 300, 1200, 3000}) {
                            String label = dimension.location() + "/" + mode + "/" + type.id + "/" + coordinate + "/" + distance;
                            compare(world, origin, world, origin.add(distance / scale, 0, 0), mode, type, 2000, 2000, label);
                            compare(world, origin, world, origin.add(distance / scale, 0, 0), mode, type, 250, 2000, label + "/weak");
                            compare(world, origin, world, origin.add(distance * .6 / scale, distance * .2 / scale, distance * .75 / scale),
                                    mode, type, 2000, 2000, label + "/tower-diagonal", 32);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_reach_edges_use_safe_margins_including_far_Nether_coordinates(GameTestHelper helper) {
        for (var world : new ServerLevel[]{helper.getLevel(), helper.getLevel().getServer().getLevel(Level.NETHER)}) {
            var type = SimpleRadioFrequencing.TRANSMITTER;
            for (var mode : Frequency.Modulation.values()) for (double coordinate : new double[]{16000.5, 1000000.5, -1000000.5}) {
                Vec3 origin = new Vec3(coordinate, 64.5, coordinate);
                var a = new WorldlyPosition((float) origin.x, (float) origin.y, (float) origin.z, world);
                double multiplier = RadioSpace.nativePropagation(a, a).distanceMultiplier();
                double physicalScale = world.dimensionType().coordinateScale() * multiplier;
                double edge = (2000 + SimpleRadioFrequencing.RECEIVER.receptionPower) / type.transmissionDiminishment / physicalScale;
                // At least half a physical block, and comfortably outside the coordinate float rounding band.
                double margin = Math.max(.5, 8 * Math.ulp((float) (Math.abs(coordinate) * world.dimensionType().coordinateScale())) / physicalScale);
                var inside = compare(world, origin, world, origin.add(edge - margin, 0, 0), mode, type, 2000, 2000, "inside reach");
                var outside = compare(world, origin, world, origin.add(edge + margin, 0, 0), mode, type, 2000, 2000, "outside reach");
                check(inside.reached() && !outside.reached(), "Native reach gate must separate the margin probes");
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_reception_floor_and_both_loss_methods_match(GameTestHelper helper) {
        var world = helper.getLevel(); var type = SimpleRadioFrequencing.TRANSMITTER;
        var receiver = SimpleRadioFrequencing.RECEIVER;
        var oldMethod = type.diminishmentMethod; var oldLoss = type.transmissionDiminishment;
        var oldFloor = receiver.receptionFloor; var oldReception = receiver.receptionPower;
        try {
            receiver.receptionFloor = 32; receiver.receptionPower = 100;
            Vec3 origin = new Vec3(16000.5, 64.5, -16000.5);
            for (var method : FrequencingType.DiminishmentMethod.values()) {
                type.diminishmentMethod = method;
                type.transmissionDiminishment = method == FrequencingType.DiminishmentMethod.ADDITIVE ? 1 : .1;
                for (var mode : Frequency.Modulation.values()) for (double distance : new double[]{.5, 31.5, 32, 32.5, 100, 200})
                    compare(world, origin, world, origin.add(distance, 0, 0), mode, type, 1000, 2000,
                            "floor/" + method + "/" + distance);
            }
        } finally {
            type.diminishmentMethod = oldMethod; type.transmissionDiminishment = oldLoss;
            receiver.receptionFloor = oldFloor; receiver.receptionPower = oldReception;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_dimensional_travel_can_exhaust_a_signal_that_passes_reach(GameTestHelper helper) {
        // Foci intentionally replaces native cross-world travel; it has its own hook-active parity checks.
        if (FabricLoader.getInstance().isModLoaded("simpleradio_repeaters_foci")) { helper.succeed(); return; }
        var from = helper.getLevel();
        var config = SimpleRadioLibrary.SERVER_CONFIG.frequency; boolean oldEnabled = config.crossDimensional;
        var oldInterference = config.dimensionalInterference;
        try {
            config.crossDimensional = true; config.dimensionalInterference = 4d;
            for (var dimension : java.util.List.of(Level.NETHER, Level.END))
            for (var mode : Frequency.Modulation.values()) for (double coordinate : new double[]{160000.5, 1000000.5, -1000000.5}) {
                var to = from.getServer().getLevel(dimension);
                Vec3 origin = new Vec3(coordinate, 64, coordinate);
                double scale = to.dimensionType().coordinateScale();
                Vec3 mapped = origin.scale(1d / scale);
                var alive = compare(from, origin, to, mapped.add(200 / scale, 0, 0), mode,
                        SimpleRadioFrequencing.TRANSMITTER, 2000, 2000, "crossing survives");
                var exhausted = compare(from, origin, to, mapped.add(1600 / scale, 0, 0), mode,
                        SimpleRadioFrequencing.TRANSMITTER, 2000, 2000, "crossing exhausted");
                check(alive.delivered() && exhausted.reached() && !exhausted.delivered(),
                        "Reach must pass while dimensional travel rejects the distant probe");
                config.crossDimensional = false;
                var disabled = compare(from, origin, to, mapped.add(200 / scale, 0, 0), mode,
                        SimpleRadioFrequencing.TRANSMITTER, 2000, 2000, "crossing disabled");
                check(!disabled.delivered(), "Native dimensional OFF must reject an otherwise reachable crossing");
                config.crossDimensional = true;
            }
        } finally { config.crossDimensional = oldEnabled; config.dimensionalInterference = oldInterference; }
        helper.succeed();
    }
}
