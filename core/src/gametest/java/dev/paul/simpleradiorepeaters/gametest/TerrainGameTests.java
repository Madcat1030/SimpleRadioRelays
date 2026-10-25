package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.RepeatersConfig;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
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
                            double committed = TerrainManager.relayLoss(leg, lowA, lowB);
                            TerrainManager.clear(world.getServer());
                            check(TerrainManager.relayLoss(leg, lowA, lowB) == committed
                                            && TerrainManager.mappingState(a.id()) == 2,
                                    "Remapping must report CHECK and retain the last penalty instead of silencing an existing link");
                            helper.succeed();
                        } finally { manager.removeAt(a.dimension(), a.position()); manager.removeAt(b.dimension(), b.position()); restore(world, old); }
                    });
                } catch (RuntimeException | Error failure) {
                    manager.removeAt(a.dimension(), a.position()); manager.removeAt(b.dimension(), b.position()); restore(world, old); throw failure;
                }
            });
        });
    }

    private static RelayBlockEntity physicalRelay(ServerLevel world, BlockPos core, Frequency frequency) {
        world.setBlock(core, net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(), 3);
        for (int i = 1; i <= 2; i++) world.setBlock(core.above(i), com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        BlockPos box = core.east();
        world.setBlock(box, SimpleRadioRepeaters.RELAY.defaultBlockState().setValue(RelayBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        world.setBlock(box.above(), SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState(), 3);
        var relay = (RelayBlockEntity) world.getBlockEntity(box);
        relay.installCatalyst(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GILDED_BLACKSTONE), true);
        var card = new net.minecraft.world.item.ItemStack(SimpleRadioRepeaters.CARD);
        SimpleRadioRepeaters.CARD.setFrequency(card, frequency.getFrequency(), frequency.getModulation());
        ((ChannelBankBlockEntity) world.getBlockEntity(box.above())).insert(0, card, true);
        return relay;
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 720, batch = "terrain_mapping_display")
    public void physicalRelaysShowScanThenReadyAndKeepTheirLinksDuringCheck(GameTestHelper helper) {
        var world = helper.getLevel(); var old = configure(world, true, false); var manager = TowerManager.get(world.getServer());
        var frequency = frequency("097.97"); var channel = ChannelKey.from(frequency);
        var a = physicalRelay(world, helper.absolutePos(new BlockPos(2, 2, 2)), frequency);
        var b = physicalRelay(world, helper.absolutePos(new BlockPos(2, 2, 12)), frequency);
        helper.startSequence().thenWaitUntil(() -> check(manager.isEligible(a.relayId()) && manager.isEligible(b.relayId()), "Physical masts must finish their guarded antenna scan"))
                .thenWaitUntil(() -> check(a.displayStatus() == 5 && TerrainManager.mappingState(a.relayId()) == 1,
                        "A new path waiting for its first profile must be visible as SCAN"))
                .thenWaitUntil(() -> check(a.displayStatus() == RelayBlockEntity.READY && a.displayPeers() == 1,
                        "Completing terrain mapping must restore READY and publish the real connection"))
                .thenExecute(() -> { TerrainManager.clear(world.getServer()); manager.network(world, channel); })
                .thenWaitUntil(() -> check(a.displayStatus() == 6 && TerrainManager.mappingState(a.relayId()) == 2,
                        "An existing path must show CHECK while replacement terrain is measured"))
                .thenExecute(() -> {
                    check(a.displayPeers() == 1 && manager.isEligible(a.relayId()), "CHECK must retain the cached connection and eligibility");
                    for (var relay : List.of(a, b)) {
                        world.removeBlock(relay.getBlockPos().above(), false); world.removeBlock(relay.getBlockPos(), false);
                    }
                    restore(world, old);
                }).thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_device_preflight")
    public void unreachableNativeReceiversCreateNoTerrainStateOrHeightJobs(GameTestHelper helper) {
        var world = helper.getLevel(); var old = configure(world, false, true);
        var frequency = frequency("097.94");
        var sender = new RadioTransmitter(frequency, position(world, 2_000_000, 1000, -2_000_000)).frequencingType(SimpleRadioFrequencing.TRANSMITTER);
        try {
            long columns = counter("sampled_columns"), filtered = counter("filtered_device_checks");
            for (int i = 0; i < 625; i++) {
                var receiver = new RadioReceiver(frequency, position(world, 2_050_000 + i, 1000, -2_000_000)).frequencingType(SimpleRadioFrequencing.RECEIVER);
                check(!sender.shouldRouteTo(packet(sender), receiver), "An unreachable native receiver must still fail ordinary reach");
            }
            check(counter("device_results") == 0 && counter("pending_device_checks") == 0
                            && counter("sampled_columns") == columns && counter("filtered_device_checks") >= filtered + 625,
                    "Impossible native links must be rejected before creating any terrain state or generator work");
            helper.succeed();
        } finally { restore(world, old); }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 800, batch = "terrain_device_capacity")
    public void sixHundredTwentyFiveActiveNativePairsAllFinishWithoutEvictingPendingJobs(GameTestHelper helper) {
        var world = helper.getLevel(); var old = configure(world, false, true);
        var frequency = frequency("097.95");
        var senders = new ArrayList<RadioTransmitter>(); var receivers = new ArrayList<RadioReceiver>();
        var packets = new ArrayList<RadioSource>();
        for (int i = 0; i < 25; i++) {
            var sender = new RadioTransmitter(frequency, position(world, 2_100_000 + 2 * i, 1000, -2_100_000)).frequencingType(SimpleRadioFrequencing.TRANSMITTER);
            senders.add(sender); packets.add(packet(sender));
            receivers.add(new RadioReceiver(frequency, position(world, 2_100_700 + 2 * i, 1000, -2_100_000)).frequencingType(SimpleRadioFrequencing.RECEIVER));
        }
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (!finished[0])
                for (int i = 0; i < senders.size(); i++) for (var receiver : receivers) senders.get(i).shouldRouteTo(packets.get(i), receiver);
        });
        helper.startSequence().thenWaitUntil(() -> {
            int audible = 0;
            for (int i = 0; i < senders.size(); i++) for (var receiver : receivers)
                if (senders.get(i).shouldRouteTo(packets.get(i), receiver)) audible++;
            check(audible == 625 && counter("device_measured_results") == 625, "Every active pair, including those beyond 512, must complete its first terrain check");
        }).thenExecute(() -> {
            check(counter("device_results") == 625 && counter("pending_device_checks") == 0,
                    "A repeated packet traversal must retain all live scalar results and eventually drain the queue");
            SimpleRadioRepeaters.LOGGER.info("[terrain-regression] All 625 active device pairs completed: {}", TerrainManager.statistics());
            finished[0] = true; restore(world, old);
        }).thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_relay_device_preflight")
    public void unreachableRelayIngressAndEgressSkipDeviceTerrainWork(GameTestHelper helper) {
        var world = helper.getLevel(); var old = configure(world, false, true); var manager = TowerManager.get(world.getServer());
        var frequency = frequency("097.96"); var channel = ChannelKey.from(frequency);
        var records = new ArrayList<TowerRecord>(); var receivers = new ArrayList<RadioReceiver>();
        BlockPos base = new BlockPos(2_200_000, 1000, -2_200_000);
        try {
            for (int i = 0; i < 25; i++) {
                var record = new TowerRecord(UUID.randomUUID(), world.dimension().location().toString(), base.east(i),
                        Set.of(channel), true, RelayCatalysts.GILDED, true, 32, Set.of());
                records.add(record); manager.updateSnapshot(record);
                var receiver = new RadioReceiver(frequency, position(world, base.getX() + 50_000 + i, 1000, base.getZ())).frequencingType(SimpleRadioFrequencing.RECEIVER);
                receiver.identifier = (short) (31000 + i);
                receiver.owner = new net.minecraft.world.entity.decoration.ArmorStand(world, base.getX() + 50_000 + i, 1000, base.getZ());
                receivers.add(receiver); frequency.getReceivers().add(receiver);
            }
            var sender = new RadioTransmitter(frequency, position(world, base.getX(), 1000, base.getZ())).frequencingType(SimpleRadioFrequencing.TRANSMITTER);
            sender.identifier = 30990;
            check(RelayRouter.tryRoute(sender, packet(sender), null), "The nearby relay network must take over the packet so egress is exercised");
            check(counter("device_results") == 0 && counter("pending_device_checks") == 0,
                    "The 625 out-of-range relay-to-receiver combinations must not enqueue terrain work");
            sender.position = position(world, base.getX() - 50_000, 1000, base.getZ());
            check(!RelayRouter.tryRoute(sender, packet(sender), null), "A far transmitter must fail all relay entrances");
            check(counter("device_results") == 0 && counter("pending_device_checks") == 0,
                    "Unreachable relay entrances must not create device state either");
            helper.succeed();
        } finally {
            receivers.forEach(receiver -> frequency.getReceivers().remove(receiver));
            records.forEach(record -> manager.removeAt(record.dimension(), record.position()));
            restore(world, old);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_device_overflow_diagnostics")
    public void readOnlyDeviceDiagnosticsMatchLivePropagationAtTheSafetyCeiling(GameTestHelper helper) {
        var world = helper.getLevel(); var old = configure(world, false, true);
        TerrainManager.clear(world.getServer());
        var frequency = frequency("097.98");
        var sender = new RadioTransmitter(frequency, position(world, 2_400_000, 1000, -2_400_000))
                .frequencingType(SimpleRadioFrequencing.TRANSMITTER);
        var source = packet(sender);
        var home = tower(world, sender.getLocation().blockPos(), sender.getLocation().blockPos().above(2), sender.reference, 32);
        var overflow = new RadioReceiver(frequency, position(world, 2_400_700, 1000, -2_400_000))
                .frequencingType(SimpleRadioFrequencing.RECEIVER);
        var target = tower(world, overflow.getLocation().blockPos(), overflow.getLocation().blockPos().above(2), overflow.reference, 32);
        var leg = RadioSpace.between(RadioSpace.physical(world, new Vec3(sender.getLocation())),
                RadioSpace.physical(world, new Vec3(overflow.getLocation())), false);
        try {
            check(Double.isInfinite(TerrainManager.observedEgressLoss(leg, home, overflow)),
                    "A genuinely unmeasured device path must still be shown as pending below the ceiling");
            for (int i = 0; i < 16_384; i++) {
                var receiver = new RadioReceiver(frequency, position(world, 2_400_700 + i % 16, 1000, -2_400_000))
                        .frequencingType(SimpleRadioFrequencing.RECEIVER);
                TerrainManager.nativeLoss(sender, receiver, source, -1);
            }
            check(counter("device_results") == 16_384, "The fixture must fill the real retained-device limit");
            long jobs = counter("pending_device_checks"), columns = counter("sampled_columns"), fallbacks = counter("device_capacity_fallbacks");
            check(TerrainManager.observedIngressLoss(leg, sender.reference, target) == 0
                            && TerrainManager.observedEgressLoss(leg, home, overflow) == 0,
                    "Read-only ingress and egress diagnostics must show the live no-terrain capacity fallback");
            check(counter("device_capacity_fallbacks") == fallbacks, "Observing the fallback must not count it as a live attempt");
            check(TerrainManager.nativeLoss(sender, overflow, source, -1) == 0
                            && TerrainManager.ingressLoss(leg, sender.reference, target) == 0
                            && TerrainManager.egressLoss(leg, home, overflow) == 0,
                    "Native and relay device paths must use the same capacity fallback as diagnostics");
            check(counter("device_results") == 16_384 && counter("pending_device_checks") == jobs
                            && counter("sampled_columns") == columns,
                    "Overflow and observation must create no extra state, jobs or height queries");
            helper.succeed();
        } finally { restore(world, old); TerrainManager.clear(world.getServer()); }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 260, batch = "terrain_mapping_marker")
    public void fullQueueMarkersClearWhenAPathLeavesTheGraphWithoutDroppingOtherWork(GameTestHelper helper) {
        var world = helper.getLevel(); var old = configure(world, true, false); var manager = TowerManager.get(world.getServer());
        var records = new ArrayList<TowerRecord>();
        var channel = ChannelKey.from(frequency("097.99"));
        var background = ChannelKey.from(frequency("097.89"));
        var base = new BlockPos(2_500_000, 1000, -2_500_000);
        var a = tower(world, base, base.above(2), UUID.randomUUID(), 32)
                .settings(Set.of(channel, background), true, RelayCatalysts.GILDED, null);
        var b = tower(world, base.east(800), base.east(800).above(2), UUID.randomUUID(), 32)
                .settings(Set.of(channel), true, RelayCatalysts.GILDED, null);
        records.add(a); records.add(b);
        for (int i = 0; i < 128; i++) {
            BlockPos other = base.north(800 + i * 16);
            records.add(tower(world, other, other.above(2), UUID.randomUUID(), 32)
                    .settings(Set.of(background), true, RelayCatalysts.GILDED, null));
        }
        records.forEach(manager::updateSnapshot);
        helper.runAfterDelay(210, () -> {
            try {
                TerrainManager.clear(world.getServer());
                var origin = RadioSpace.sites(world.getServer(), a, false).get(0);
                for (var other : records.subList(2, records.size())) {
                    var leg = RadioSpace.between(origin, RadioSpace.sites(world.getServer(), other, false).get(0), false);
                    TerrainManager.relayLoss(leg, a, other);
                }
                check(counter("pending_relay_profiles") == 128, "The fixture must fill the real relay sampling queue");
                var initial = manager.network(world, channel);
                check(TerrainManager.mappingState(b.id()) == 1,
                        "An initial viable path must be marked pending even when its job cannot enter the full queue");
                manager.network(world, background);
                check(manager.network(world, channel) == initial && TerrainManager.mappingState(b.id()) == 1,
                        "Building another channel must not erase the first cached graph's mapping marker");
                BlockPos distant = base.east(50_000);
                manager.updateSnapshot(tower(world, distant, distant.above(2), b.id(), 32)
                        .settings(Set.of(channel), true, RelayCatalysts.GILDED, null));
                var rebuilt = manager.network(world, channel);
                check(rebuilt.edges().stream().allMatch(List::isEmpty), "The moved antenna must be beyond ordinary radio reach");
                check(TerrainManager.mappingState(b.id()) == 0,
                        "A path no longer considered by the rebuilt graph must not leave a permanent SCAN or CHECK marker");
                manager.inspectNetwork(world, channel);
                check(TerrainManager.mappingState(b.id()) == 0, "Read-only diagnostics must not restore obsolete pending markers");
                check(counter("pending_relay_profiles") == 128,
                        "Clearing obsolete display markers must preserve other queued terrain work");
                helper.succeed();
            } finally {
                records.forEach(record -> {
                    var live = manager.snapshot(record.id());
                    if (live != null) manager.removeAt(live.dimension(), live.position());
                });
                restore(world, old); TerrainManager.clear(world.getServer());
            }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_profile_capacity")
    public void activeProfilesBeyondTheOldLimitPersistAndOnlyOrphanedMastPathsArePruned(GameTestHelper helper) {
        var data = new TerrainProfiles(); data.initialize(helper.getLevel().getSeed());
        Set<Long> columns = new HashSet<>(); List<TerrainProfiles.Key> paths = new ArrayList<>();
        for (int i = 0; i < 46; i++) columns.add(TerrainProfiles.column(i * 64, -1000));
        for (int a = 0; a < 46; a++) for (int b = a + 1; b < 46; b++) {
            var key = TerrainProfiles.Key.between(a * 64, -1000, b * 64, -1000, 64, 1024);
            paths.add(key); data.put(key, new int[]{64, 64});
        }
        check(data.size() == 1035, "Every registered path in a 46-tower mesh must survive the old 1024-entry limit");
        for (int pass = 0; pass < 3; pass++) for (var key : paths) check(data.get(key) != null, "Graph traversal must not evict another active path");
        var restored = TerrainProfiles.load(data.save(new CompoundTag())); restored.initialize(helper.getLevel().getSeed());
        check(restored.size() == 1035, "Saving and loading must preserve the complete active mesh");
        columns.remove(TerrainProfiles.column(0, -1000)); restored.retainColumns(columns);
        check(restored.size() == 990, "Removing one mast must prune exactly its 45 paths and retain the others");
        var old = paths.get(paths.size() - 1);
        var replacement = TerrainProfiles.Key.between(old.ax(), old.az(), old.bx(), old.bz(), 128, 1024);
        check(data.previous(replacement) != null, "Changing resolution must leave a usable previous profile");
        data.put(replacement, new int[]{65, 65});
        check(data.size() == 1035 && data.get(old) == null && data.get(replacement)[0] == 65,
                "A replacement must retire only the previous resolution of its own physical path");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "terrain_datapack_reload")
    public void realDatapackReloadPreservesMeasuredTerrainProfiles(GameTestHelper helper) {
        var world = helper.getLevel(); var old = configure(world, true, false); var manager = TowerManager.get(world.getServer());
        var a = tower(world, new BlockPos(2_300_000, 64, -2_300_000), new BlockPos(2_300_000, 100, -2_300_000), UUID.randomUUID(), 32);
        var b = tower(world, a.antennaBase().east(800), a.antennaTip().east(800), UUID.randomUUID(), 32);
        manager.updateSnapshot(a); manager.updateSnapshot(b);
        var data = world.getDataStorage().computeIfAbsent(TerrainProfiles::load, TerrainProfiles::new, TerrainProfiles.NAME);
        var key = TerrainProfiles.Key.between(a.antennaTip().getX(), a.antennaTip().getZ(), b.antennaTip().getX(), b.antennaTip().getZ(), 64, 1024);
        int[] heights = {64, 64}; data.put(key, heights);
        var reload = world.getServer().reloadResources(world.getServer().getPackRepository().getSelectedIds());
        helper.startSequence().thenWaitUntil(() -> check(reload.isDone(), "Datapack reload must finish")).thenExecute(() -> {
            try {
                reload.join();
                check(Arrays.equals(heights, data.get(key)), "A successful real datapack reload must not discard generator-height profiles");
            } finally { manager.removeAt(a.dimension(), a.position()); manager.removeAt(b.dimension(), b.position()); restore(world, old); }
        }).thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_config_error")
    public void malformedConfigReloadReturnsFailureAndKeepsTheLiveSettings(GameTestHelper helper) throws Exception {
        var path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("simpleradio-repeaters.json");
        String previous = java.nio.file.Files.readString(path); var live = RepeatersConfig.INSTANCE;
        try {
            java.nio.file.Files.writeString(path, "{invalid");
            int result = helper.getLevel().getServer().getCommands().performPrefixedCommand(
                    helper.getLevel().getServer().createCommandSourceStack(), "radiorepeaters terrain reload");
            check(result == 0 && RepeatersConfig.INSTANCE == live && java.nio.file.Files.readString(path).equals("{invalid"),
                    "A broken config must return command failure, retain live settings and leave the invalid file available for correction");
        } finally { java.nio.file.Files.writeString(path, previous); }
        helper.succeed();
    }
}
