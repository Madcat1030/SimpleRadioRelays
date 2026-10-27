package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.RepeatersConfig;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
import dev.paul.simpleradiorepeaters.terrain.TerrainMode;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Real native power/reach/travel hooks and unloaded relay graphs, without voice decoding. */
public class TowerPowerGameTests implements FabricGameTest {
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    private static WorldlyPosition at(ServerLevel world, double x) { return new WorldlyPosition((float) x, 64.5f, -2100000.5f, world); }
    private static Frequency frequency(Frequency.Modulation mode) {
        return ServerSimpleRadioApi.getInstance().frequencies().getOrCreate("097.83", mode);
    }
    private static double multiplier(int score, boolean enabled) { return enabled ? .5 + Math.min(16, score) / 32d : 1; }
    private static RadioSource packet(WorldlyPosition location, Frequency channel) {
        RadioSource source = new RadioSource(UUID.randomUUID(), location, new byte[]{1}, 1);
        source.frequencyMedium = channel; return source;
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_blocks_and_virtual_towers_share_the_linear_base_curve(GameTestHelper helper) {
        var world = helper.getLevel(); var cfg = RepeatersConfig.INSTANCE;
        boolean old = cfg.antennaDependentBasePower; TerrainMode terrain = cfg.terrain.mode;
        var tx = SimpleRadioFrequencing.TRANSMITTER; var rx = SimpleRadioFrequencing.RECEIVER;
        int oldAM = tx.transmissionPowerAM, oldFM = tx.transmissionPowerFM, oldReception = rx.receptionPower;
        try {
            cfg.terrain.mode = TerrainMode.OFF;
            // Odd custom values catch unwanted integer rounding, and must remain configured values.
            tx.transmissionPowerAM = 4401; tx.transmissionPowerFM = 3301; rx.receptionPower = 301;
            for (boolean enabled : new boolean[]{true, false}) {
                cfg.antennaDependentBasePower = enabled;
                for (var mode : Frequency.Modulation.values()) for (int score : new int[]{0, 2, 8, 15, 16, 32}) {
                    Frequency frequency = frequency(mode); var location = at(world, 2100000.5);
                    var sender = new RadioTransmitter(frequency, location).frequencingType(tx); sender.antennaPower = score;
                    var receiver = new RadioReceiver(frequency, location).frequencingType(rx); receiver.antennaPower = score;
                    double expectedTx = tx.getTransmissionPower(mode) * multiplier(score, enabled) + score * tx.antennaAptitude;
                    double expectedRx = rx.receptionPower * multiplier(score, enabled) + score * rx.antennaAptitude;
                    check(sender.getPower(mode) == (float) expectedTx && receiver.getPower() == (float) expectedRx,
                            "Native block power must match the curve for " + mode + "/ANT " + score + "/enabled=" + enabled);
                    var tower = new TowerRecord(UUID.randomUUID(), world.dimension().location().toString(), location.blockPos(),
                            Set.of(ChannelKey.from(frequency)), true, RelayCatalysts.GILDED, true, score, Set.of());
                    check(NativeRadioAdapter.towerPower(tower, ChannelKey.from(frequency)) == sender.getPower(mode)
                                    && NativeRadioAdapter.towerReception(tower) == expectedRx,
                            "Unloaded tower math must match the native block hooks");
                    check(tower.eligible() == (score > 0), "A relay still requires a positive antenna score");
                }
            }
            check(tx.transmissionPowerAM == 4401 && tx.transmissionPowerFM == 3301 && rx.receptionPower == 301,
                    "Balancing must not rewrite the native shared configuration");
            check(!world.hasChunkAt(at(world, 2100000.5).blockPos()), "Numeric probes must not load the mast chunk");
        } finally {
            tx.transmissionPowerAM = oldAM; tx.transmissionPowerFM = oldFM; rx.receptionPower = oldReception;
            cfg.antennaDependentBasePower = old; cfg.terrain.mode = terrain;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_reach_and_travel_use_the_balanced_power_once(GameTestHelper helper) {
        var world = helper.getLevel(); var cfg = RepeatersConfig.INSTANCE;
        boolean old = cfg.antennaDependentBasePower; TerrainMode terrain = cfg.terrain.mode;
        try {
            cfg.antennaDependentBasePower = true; cfg.terrain.mode = TerrainMode.OFF;
            for (var mode : Frequency.Modulation.values()) for (int score : new int[]{0, 8, 16, 32}) {
                Frequency f = frequency(mode); var location = at(world, 2100000.5);
                var txType = SimpleRadioFrequencing.TRANSMITTER; var rxType = SimpleRadioFrequencing.RECEIVER;
                var sender = new RadioTransmitter(f, location).frequencingType(txType); sender.antennaPower = score;
                double power = txType.getTransmissionPower(mode) * multiplier(score, true) + score * txType.antennaAptitude;
                double reception = rxType.receptionPower * multiplier(score, true) + score * rxType.antennaAptitude;
                double edge = (power + reception) / txType.transmissionDiminishment;
                for (double offset : new double[]{-2, 2}) {
                    var receiver = new RadioReceiver(f, at(world, location.x + edge + offset)).frequencingType(rxType);
                    receiver.antennaPower = score;
                    check(sender.shouldRouteTo(packet(location, f), receiver) == (offset < 0),
                            "The native reach gate must use the balanced base at ANT " + score);
                }
                var receiver = new RadioReceiver(f, at(world, location.x + 500)).frequencingType(rxType); receiver.antennaPower = score;
                RadioSource source = sender.prepareSource(packet(location, f), receiver);
                double expected = power - Math.max(rxType.receptionFloor, 500 - reception) * txType.transmissionDiminishment;
                check(source.transmissionCap == (float) power && Math.abs(source.transmissionPower - expected) < .01,
                        "Source initialization and native travel must apply the balance exactly once");
                var alreadyPowered = packet(location, f); alreadyPowered.frequencingType = txType.id;
                alreadyPowered.transmissionCap = alreadyPowered.transmissionPower = (float) power;
                check(sender.prepareSource(alreadyPowered, receiver).transmissionPower == source.transmissionPower,
                        "An already powered wired packet must not be scaled again");
            }
        } finally { cfg.antennaDependentBasePower = old; cfg.terrain.mode = terrain; }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void handhelds_radios_and_other_frequencing_types_are_unchanged(GameTestHelper helper) {
        var cfg = RepeatersConfig.INSTANCE; boolean old = cfg.antennaDependentBasePower;
        try {
            cfg.antennaDependentBasePower = true;
            var custom = new FrequencingType(); custom.transmissionPowerAM = 1234; custom.transmissionPowerFM = 987;
            custom.receptionPower = 231; custom.antennaAptitude = 7;
            for (var type : new FrequencingType[]{SimpleRadioFrequencing.TRANSCEIVER, SimpleRadioFrequencing.WALKIE_TALKIE,
                    SimpleRadioFrequencing.RADIO, custom}) for (var mode : Frequency.Modulation.values()) {
                var f = frequency(mode); var location = at(helper.getLevel(), 2100000.5);
                var sender = new RadioTransmitter(f, location).frequencingType(type);
                var receiver = new RadioReceiver(f, location).frequencingType(type);
                for (int score : new int[]{0, 8, 32}) {
                    sender.antennaPower = receiver.antennaPower = score;
                    check(sender.getPower(mode) == type.getTransmissionPower(mode) + score * type.antennaAptitude
                                    && receiver.getPower() == type.receptionPower + score * type.antennaAptitude,
                            "Other radio types must retain their original power, including custom addon types");
                }
            }
        } finally { cfg.antennaDependentBasePower = old; }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void relay_upgrades_keep_their_bonus_and_config_changes_rebuild_saved_links(GameTestHelper helper) {
        var world = helper.getLevel(); var cfg = RepeatersConfig.INSTANCE; var manager = TowerManager.get(world.getServer());
        boolean old = cfg.antennaDependentBasePower; TerrainMode terrain = cfg.terrain.mode;
        BlockPos first = new BlockPos(2100000, 64, -2100000), second = first.east(3000);
        Frequency f = frequency(Frequency.Modulation.AMPLITUDE); ChannelKey channel = ChannelKey.from(f);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        try {
            cfg.terrain.mode = TerrainMode.OFF; cfg.antennaDependentBasePower = true;
            for (var upgrade : new RelayAttachment[]{RelayAttachment.NONE, RelayAttachment.COPPER_AMPLIFIER,
                    RelayAttachment.REDSTONE_AMPLIFIER, RelayAttachment.AMPLIFIER}) {
                var tower = new TowerRecord(a, world.dimension().location().toString(), first, Set.of(channel), true,
                        RelayCatalysts.GILDED, true, 8, Set.of(), null, upgrade, null);
                double expected = SimpleRadioFrequencing.TRANSMITTER.getTransmissionPower(f.getModulation()) * .75
                        + 8 * upgrade.antennaMultiplier() * SimpleRadioFrequencing.TRANSMITTER.antennaAptitude;
                check(NativeRadioAdapter.towerPower(tower, channel) == (float) expected,
                        "An amplifier keeps its full antenna bonus without advancing physical base recovery");
                check(Math.abs(NativeRadioAdapter.departurePower(tower, channel, 3)
                        - expected * tower.efficiency() * (upgrade.resetsDecay() ? 1 : .81)) < .001,
                        "Catalyst efficiency and hop reset must act on the new tower ceiling");
            }
            TowerRecord left = new TowerRecord(a, world.dimension().location().toString(), first, Set.of(channel), true,
                    RelayCatalysts.GILDED, true, 2, Set.of());
            TowerRecord right = new TowerRecord(b, left.dimension(), second, left.channels(), true,
                    RelayCatalysts.GILDED, true, 2, Set.of());
            manager.updateSnapshot(left); manager.updateSnapshot(right);
            TowerManager.resourcesReloaded(world.getServer());
            int stamp = NativeRadioAdapter.configurationStamp(); var balanced = manager.network(world, channel);
            check(balanced.edges().stream().allMatch(List::isEmpty), "Two ANT 2 relays should no longer bridge 3000 blocks");
            cfg.antennaDependentBasePower = false;
            check(NativeRadioAdapter.configurationStamp() != stamp, "The switch must participate in graph invalidation");
            TowerManager.resourcesReloaded(world.getServer()); var original = manager.network(world, channel);
            check(original != balanced && original.edges().stream().anyMatch(edges -> !edges.isEmpty())
                            && manager.snapshot(a).equals(left) && manager.snapshot(b).equals(right),
                    "Disabling must restore the original links without rebuilding antennas or rewriting saved hardware");
            check(!world.hasChunkAt(first) && !world.hasChunkAt(second), "Saved-only links must not load either tower");
        } finally {
            manager.removeAt(world.dimension().location().toString(), first); manager.removeAt(world.dimension().location().toString(), second);
            cfg.antennaDependentBasePower = old; cfg.terrain.mode = terrain; TowerManager.resourcesReloaded(world.getServer());
        }
        helper.succeed();
    }
}
