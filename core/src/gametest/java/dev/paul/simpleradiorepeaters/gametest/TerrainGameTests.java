package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.RepeatersConfig;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
import dev.paul.simpleradiorepeaters.terrain.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class TerrainGameTests implements FabricGameTest {
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    private static long counter(String key) { return ((Number) TerrainManager.statistics().get(key)).longValue(); }
    private static Frequency frequency(String value) {
        return ServerSimpleRadioApi.getInstance().frequencies().getOrCreate(value, Frequency.Modulation.AMPLITUDE);
    }
    private static RadioSource packet(RadioTransmitter sender) {
        var source = new RadioSource(UUID.randomUUID(), sender.getLocation(), new byte[]{1}, 1);
        source.frequencingType = sender.frequencingType.id; source.frequencyMedium = sender.frequency;
        source.transmissionCap = source.transmissionPower = 4000; return source;
    }
    private static WorldlyPosition position(ServerLevel world, double x, double y, double z) {
        return new WorldlyPosition((float) x, (float) y, (float) z, world);
    }
    private static RepeatersConfig.Terrain configure(ServerLevel world, boolean relays, boolean devices) {
        var old = RepeatersConfig.INSTANCE.terrain;
        var cfg = new RepeatersConfig.Terrain(); cfg.mode = devices ? TerrainMode.FULL : relays ? TerrainMode.RELAY_MAPPING : TerrainMode.OFF;
        cfg.maxElevationAntennaBonus = 0; RepeatersConfig.INSTANCE.terrain = cfg;
        TerrainManager.reconfigure(world.getServer()); return old;
    }
    private static void restore(ServerLevel world, RepeatersConfig.Terrain old) {
        RepeatersConfig.INSTANCE.terrain = old; TerrainManager.reconfigure(world.getServer());
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_mast_tip")
    public void verifiedMastTipTracksGrowthAndTrimmingOfThePhysicalColumn(GameTestHelper helper) {
        var world = helper.getLevel(); var core = helper.absolutePos(new BlockPos(2, 2, 2));
        world.setBlock(core, net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(), 3);
        for (int i = 1; i <= 4; i++) world.setBlock(core.above(i), com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        var first = AntennaScanner.scan(world, core.north(), core);
        check(first.complete() && first.antennaBase().equals(core) && core.above(4).equals(first.antennaTip()), "A verified scan must retain the physical column's tip separately from its base");
        world.setBlock(core.above(4), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        var shorter = AntennaScanner.scan(world, core.north(), core);
        check(shorter.complete() && core.above(3).equals(shorter.antennaTip()), "Removing the top must lower the verified tip");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_engine")
    public void engineHeightQueriesAreDeterministicWithoutLoadingOrGeneratingChunks(GameTestHelper helper) {
        var world = helper.getLevel();
        var settings = world.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var generator = new NoiseBasedChunkGenerator(world.getChunkSource().getGenerator().getBiomeSource(), settings);
        var noise = RandomState.create(settings.value(), world.registryAccess().lookupOrThrow(Registries.NOISE), world.getSeed());
        for (int x : new int[]{1_300_000, -1_300_000, 1_450_000}) {
            var point = new BlockPos(x, 80, -1_300_000);
            check(!world.hasChunkAt(point), "Probe must begin outside generated/active test chunks");
            int a = generator.getBaseHeight(x, point.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, world, noise);
            int b = generator.getBaseHeight(x, point.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, world, noise);
            check(a == b && a >= world.getMinBuildHeight() && a <= world.getMaxBuildHeight(), "Noise height must be deterministic and bounded");
            TerrainManager.engineHeight(world, x, point.getZ());
            check(!world.hasChunkAt(point), "Generator height sampling must not activate or create a world chunk");
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_device_free")
    public void disabledAndNearbyDeviceLinksNeverSampleTerrain(GameTestHelper helper) {
        var world = helper.getLevel(); var old = configure(world, false, false);
        try {
            var frequency = frequency("097.90");
            var sender = new RadioTransmitter(frequency, position(world, 1_500_000.5, -64, -1_500_000.5)).frequencingType(SimpleRadioFrequencing.TRANSMITTER);
            var receiver = new RadioReceiver(frequency, position(world, 1_501_000.5, -64, -1_500_000.5)).frequencingType(SimpleRadioFrequencing.RECEIVER);
            long before = counter("device_columns");
            check(sender.shouldRouteTo(packet(sender), receiver), "Disabled terrain must preserve ordinary reach");
            RepeatersConfig.INSTANCE.terrain.mode = TerrainMode.FULL; TerrainManager.reconfigure(world.getServer());
            receiver.position = position(world, 1_500_256.5, -64, -1_500_000.5);
            for (int i = 0; i < 50; i++) {
                check(sender.shouldRouteTo(packet(sender), receiver), "The 256-block boundary must remain free");
                check(TerrainManager.nativeLoss(sender, receiver) == 0, "Nearby links must have no terrain penalty");
            }
            check(counter("device_columns") == before && counter("pending_device_checks") == 0 && counter("device_results") == 0,
                    "Disabled/nearby paths must create no state, no sample jobs and no height queries");
            var nether = world.getServer().getLevel(Level.NETHER);
            sender.position = position(nether, 1_500_000.5, 60, -1_500_000.5);
            receiver.position = position(nether, 1_501_000.5, 60, -1_500_000.5);
            check(TerrainManager.nativeLoss(sender, receiver) == 0 && counter("pending_device_checks") == 0, "Nether propagation must remain outside terrain simulation");
            helper.succeed();
        } finally { restore(world, old); }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80, batch = "terrain_mode_off")
    public void offModeCancelsPendingWorkAndRestoresOrdinaryPropagation(GameTestHelper helper) {
        var world = helper.getLevel(); var old = configure(world, false, false);
        var frequency = frequency("097.93");
        var sender = new RadioTransmitter(frequency, position(world, 1_800_000.5, -64, -1_800_000.5)).frequencingType(SimpleRadioFrequencing.TRANSMITTER);
        var receiver = new RadioReceiver(frequency, position(world, 1_800_600.5, -64, -1_800_000.5)).frequencingType(SimpleRadioFrequencing.RECEIVER);
        float ordinary = sender.prepareSource(packet(sender), receiver).transmissionPower;
        RepeatersConfig.INSTANCE.terrain.mode = TerrainMode.FULL; TerrainManager.reconfigure(world.getServer());
        check(!sender.shouldRouteTo(packet(sender), receiver) && counter("pending_device_checks") == 1, "Full mode must schedule a distant device check");
        long before = counter("sampled_columns");
        RepeatersConfig.INSTANCE.terrain.mode = TerrainMode.OFF; TerrainManager.reconfigure(world.getServer());
        check(sender.shouldRouteTo(packet(sender), receiver)
                && sender.prepareSource(packet(sender), receiver).transmissionPower == ordinary,
                "Off must immediately restore the native gate and travel result");
        var a = tower(world, new BlockPos(1_800_000, 64, -1_800_000), new BlockPos(1_800_000, 300, -1_800_000), UUID.randomUUID(), 32);
        var b = tower(world, a.antennaBase().east(600), a.antennaTip().east(600), UUID.randomUUID(), 32);
        var leg = RadioSpace.between(RadioSpace.sites(world.getServer(), a, false).get(0), RadioSpace.sites(world.getServer(), b, false).get(0), false);
        check(TerrainManager.relayLoss(leg, a, b) == 0 && TerrainManager.elevationMultiplier(a) == 1,
                "Off must disable both relay terrain loss and the elevation bonus");
        helper.runAfterDelay(20, () -> {
            try {
                check(counter("sampled_columns") == before && counter("pending_device_checks") == 0,
                        "Off must cancel queued work and perform no engine height queries"); helper.succeed();
            } finally { restore(world, old); }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 160, batch = "terrain_device_movement")
    public void deviceChecksFollowMovementAndNativeReachAgreesWithTravel(GameTestHelper helper) {
        var world = helper.getLevel(); var old = configure(world, false, true);
        var frequency = frequency("097.91");
        var a = position(world, 1_600_000.5, -64, -1_600_000.5);
        var sender = new RadioTransmitter(frequency, a).frequencingType(SimpleRadioFrequencing.TRANSMITTER);
        var receiver = new RadioReceiver(frequency, position(world, a.x + 600, -64, a.z)).frequencingType(SimpleRadioFrequencing.RECEIVER);
        long before = counter("device_columns");
        check(!sender.shouldRouteTo(packet(sender), receiver), "A first distant path must wait for its bounded terrain check");
        check(counter("device_columns") == before, "The audio routing call must never run generator queries");
        helper.runAfterDelay(30, () -> {
            try {
                double extra = TerrainManager.nativeLoss(sender, receiver);
                check(Double.isFinite(extra) && extra > 0 && counter("device_columns") > before, "A below-surface device path must gain a finite terrain penalty");
                var source = packet(sender);
                check(sender.shouldRouteTo(source, receiver), "Strong carrier must survive the terrain-aware reach gate");
                var traveled = sender.prepareSource(source, receiver);
                var leg = RadioSpace.between(RadioSpace.physical(world, new Vec3(sender.getLocation())), RadioSpace.physical(world, new Vec3(receiver.getLocation())), false);
                var predicted = NativeRadioAdapter.receiveAtReceiver(leg, 4000, 4000, sender.frequencingType, receiver, ChannelKey.from(frequency), extra);
                check(predicted != null && Math.abs(predicted.power() - traveled.transmissionPower) < .01,
                        "Native travel and relay-side numeric math must apply the same single terrain penalty");
                long sampled = counter("device_columns");
                for (int i = 0; i < 100; i++) TerrainManager.nativeLoss(sender, receiver);
                receiver.position = position(world, a.x + 616, -64, a.z);
                TerrainManager.nativeLoss(sender, receiver);
                check(counter("pending_device_checks") == 0 && counter("device_columns") == sampled, "Stationary packets and exactly 16 blocks must reuse only the scalar result");
                receiver.position = position(world, a.x + 616.25, -64, a.z);
                TerrainManager.nativeLoss(sender, receiver);
                check(counter("pending_device_checks") == 1, "More than 16 blocks must queue a new check");
                helper.runAfterDelay(30, () -> {
                    try {
                        var data = world.getDataStorage().computeIfAbsent(TerrainProfiles::load, TerrainProfiles::new, TerrainProfiles.NAME);
                        var initial = TerrainProfiles.Key.between(net.minecraft.util.Mth.floor(a.x), net.minecraft.util.Mth.floor(a.z),
                                net.minecraft.util.Mth.floor(a.x + 600), net.minecraft.util.Mth.floor(a.z), 64, 1024);
                        var moved = TerrainProfiles.Key.between(net.minecraft.util.Mth.floor(a.x), net.minecraft.util.Mth.floor(a.z),
                                net.minecraft.util.Mth.floor(a.x + 616.25), net.minecraft.util.Mth.floor(a.z), 64, 1024);
                        check(counter("device_columns") > sampled && data.get(initial) == null && data.get(moved) == null,
                                "Device movement must resample without creating any saved terrain profile");
                        check(!world.hasChunkAt(a.blockPos()) && !world.hasChunkAt(receiver.getLocation().blockPos()), "Moving device checks must not load terrain chunks");
                        helper.succeed();
                    } finally { restore(world, old); }
                });
            } catch (RuntimeException | Error failure) { restore(world, old); throw failure; }
        });
    }

    private static TowerRecord tower(ServerLevel world, BlockPos base, BlockPos tip, UUID id, int strength) {
        var channel = ChannelKey.from(frequency("097.92"));
        return new TowerRecord(id, world.dimension().location().toString(), base.below(), Set.of(channel), true,
                RelayCatalysts.GILDED, true, strength, Set.of(), null, RelayAttachment.NONE, null, base, tip);
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 720, batch = "terrain_relay_cache")
    public void relayProfilesPersistAndHeightChangesReuseSamplesAtThe200TickBoundary(GameTestHelper helper) {
        var world = helper.getLevel(); var old = configure(world, true, false); var manager = TowerManager.get(world.getServer());
        var a = tower(world, new BlockPos(1_700_000, 64, -1_700_000), new BlockPos(1_700_000, 180, -1_700_000), UUID.randomUUID(), 32);
        var b = tower(world, a.antennaBase().east(800), a.antennaTip().east(800), UUID.randomUUID(), 32);
        var channel = a.channels().iterator().next(); manager.updateSnapshot(a); manager.updateSnapshot(b);
        var leg = RadioSpace.between(RadioSpace.sites(world.getServer(), a, false).get(0), RadioSpace.sites(world.getServer(), b, false).get(0), false);
        check(TowerRecord.load(a.save()).antennaTip().equals(a.antennaTip()) && a.invalidate().antennaTip().equals(a.antennaTip()), "Verified mast tips must survive saves and temporary invalidation");
        helper.runAfterDelay(210, () -> {
            manager.network(world, channel);
            helper.runAfterDelay(210, () -> {
                try {
                    double loss = TerrainManager.relayLoss(leg, a, b);
                    check(loss == 0, "High masts must clear the flat world's surface");
                    long columns = counter("sampled_columns");
                    var data = world.getDataStorage().computeIfAbsent(TerrainProfiles::load, TerrainProfiles::new, TerrainProfiles.NAME);
                    var restored = TerrainProfiles.load(data.save(new CompoundTag())); restored.initialize(world.getSeed());
                    var key = TerrainProfiles.Key.between(a.antennaTip().getX(), a.antennaTip().getZ(), b.antennaTip().getX(), b.antennaTip().getZ(), 64, 1024);
                    check(Arrays.equals(data.get(key), restored.get(key)), "Relay terrain samples must survive a saved-data roundtrip");
                    var stronger = tower(world, a.antennaBase(), a.antennaTip(), a.id(), 64);
                    manager.updateSnapshot(stronger); manager.network(world, channel);
                    TowerManager.resourcesReloaded(world.getServer(), "terrain_fixture_dish_change"); manager.network(world, channel);
                    check(counter("sampled_columns") == columns && counter("pending_relay_profiles") == 0,
                            "Power and directional graph changes must reuse the saved physical profile");
                    var lowA = tower(world, a.antennaBase(), new BlockPos(a.antennaTip().getX(), -63, a.antennaTip().getZ()), a.id(), 64);
                    var lowB = tower(world, b.antennaBase(), new BlockPos(b.antennaTip().getX(), -63, b.antennaTip().getZ()), b.id(), 32);
                    manager.updateSnapshot(lowA); manager.updateSnapshot(lowB);
                    check(TerrainManager.relayLoss(leg, lowA, lowB) == 0, "Mast edits must wait for the shared 200-tick height boundary");
                    helper.runAfterDelay(210, () -> {
                        try {
                            check(TerrainManager.relayLoss(leg, lowA, lowB) > 0 && counter("sampled_columns") == columns,
                                    "Shortening the masts must update clearance without resampling the ground");
                            check(!world.hasChunkAt(a.antennaBase()) && !world.hasChunkAt(b.antennaBase()), "Saved relay terrain checks must not activate endpoint or intervening chunks");
                            helper.succeed();
                        } finally { manager.removeAt(a.dimension(), a.position()); manager.removeAt(b.dimension(), b.position()); restore(world, old); }
                    });
                } catch (RuntimeException | Error failure) {
                    manager.removeAt(a.dimension(), a.position()); manager.removeAt(b.dimension(), b.position()); restore(world, old); throw failure;
                }
            });
        });
    }
}
