package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks;
import dev.paul.simpleradiorepeaters.*;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.monitor.*;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
import dev.paul.simpleradiorepeaters.terrain.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;

public class MonitorGameTests implements FabricGameTest {
    private static final ChannelKey CHANNEL = new ChannelKey("098.21", "AM"), OTHER = new ChannelKey("098.22", "AM");
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    private static ItemStack card(ChannelKey channel) {
        var card = new ItemStack(SimpleRadioRepeaters.CARD);
        SimpleRadioRepeaters.CARD.setFrequency(card, channel.frequency(), channel.mode()); return card;
    }
    private static TowerRecord node(ServerLevel world, BlockPos base, Set<ChannelKey> channels, TransponderChannels ports) {
        return new TowerRecord(UUID.randomUUID(), world.dimension().location().toString(), base.south(17), channels, true,
                RelayCatalysts.GILDED, true, 64, Set.of(), null, RelayAttachment.NONE, ports, base);
    }
    private static List<TowerRecord> targets(ServerLevel world, TowerRecord home) {
        var base = home.antennaBase();
        return List.of(node(world, base.east(1000), Set.of(CHANNEL), new TransponderChannels(CHANNEL, OTHER, CHANNEL, TransponderChannels.Mode.MULTICAST)),
                node(world, base.north(1500), Set.of(), new TransponderChannels(OTHER, CHANNEL, null, false)),
                node(world, base.offset(1400, 0, 3400), Set.of(), new TransponderChannels(CHANNEL, OTHER, null, false)),
                node(world, base.west(200_000), Set.of(CHANNEL), null),
                node(world, base.south(1000), Set.of(new ChannelKey(CHANNEL.frequency(), "FM")), null));
    }
    private static void remove(TowerManager manager, Collection<TowerRecord> records) {
        records.forEach(record -> manager.removeAt(record.dimension(), record.position()));
    }
    private static RelayMonitorBlockEntity place(ServerLevel world, BlockPos core, Direction facing) {
        world.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        for (int i = 1; i <= 3; i++) world.setBlock(core.above(i), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        BlockPos relayPos = core.relative(facing), pos = relayPos.above();
        world.setBlock(relayPos, SimpleRadioRepeaters.RELAY.defaultBlockState().setValue(RelayBlock.FACING, facing), 3);
        world.setBlock(pos, SimpleRadioRepeaters.RELAY_MONITOR.defaultBlockState().setValue(ChannelBankBlock.FACING, facing), 3);
        ((RelayBlockEntity) world.getBlockEntity(relayPos)).installCatalyst(new ItemStack(Items.GILDED_BLACKSTONE), true);
        return (RelayMonitorBlockEntity) world.getBlockEntity(pos);
    }
    private static BlockHitResult hit(BlockPos pos, Direction facing, double x, double y) {
        Vec3 local = switch (facing) {
            case EAST -> new Vec3(1, y, x); case SOUTH -> new Vec3(1 - x, y, 1);
            case WEST -> new Vec3(0, y, 1 - x); default -> new Vec3(x, y, 0);
        };
        return new BlockHitResult(local.add(pos.getX(), pos.getY(), pos.getZ()), facing, pos, false);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "monitor_survey")
    public void surveyCountsUniqueNodesAndUsesTransponderInputAndOutputRoles(GameTestHelper helper) {
        var world = helper.getLevel(); var manager = TowerManager.get(world.getServer());
        var home = node(world, new BlockPos(2_400_000, 64, -2_400_000), Set.of(), null);
        var targets = targets(world, home); manager.updateSnapshot(home); targets.forEach(manager::updateSnapshot);
        try {
            var result = RelaySurvey.scan(world.getServer(), manager, home, CHANNEL);
            check(result.tx() == 2 && result.rx() == 2 && result.other() == 2 && result.entries().size() == 3,
                    "Counts must include one node per tower, distinguish simplex roles and exclude FM from an AM card");
            check(result.origin().equals(home.antennaBase()) && !result.origin().equals(home.position()), "Overview coordinates must identify the antenna base");
            check(result.entries().get(0).directionLabel().equals("TX/RX") && result.entries().get(1).directionLabel().equals("RX only")
                            && result.entries().get(2).directionLabel().equals("TX only"), "Heard entries must precede approximate TX-only entries");
            check(result.entries().get(2).position() == null && result.entries().get(2).bearing().equals("SSE"),
                    "A TX-only result must omit coordinates and use the sixteen-point bearing");
            for (var target : targets) check(!world.hasChunkAt(target.position()), "Surveying saved targets must not load their chunks");
            helper.succeed();
        } finally { remove(manager, targets); remove(manager, List.of(home)); }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 140, batch = "monitor_physical")
    public void monitorIsPassiveAndItsRealPagesAndCompassBindingSurviveNativeTracking(GameTestHelper helper) {
        var world = helper.getLevel(); var manager = TowerManager.get(world.getServer());
        var monitor = place(world, helper.absolutePos(new BlockPos(3, 2, 3)), Direction.NORTH);
        var pages = new LinkedHashMap<String, String>(); pages.put("empty", monitor.getUpdateTag().toString());
        helper.runAfterDelay(40, () -> {
            var relay = monitor.relay(); var before = manager.snapshot(relay.relayId());
            check(before != null && before.hardwareReady(), "The monitor fixture needs a verified mast and catalyst");
            check(String.join(" ", monitor.lines()).equals("Insert tuned card for Relay diagnostics") && monitor.reading() == null,
                    "An empty monitor must show only the requested prompt and perform no survey");
            var home = new TowerRecord(before.id(), before.dimension(), before.position(), Set.of(), true, before.catalystItem(), true,
                    64, before.footprint(), before.cardBank(), before.attachment(), null, before.antennaBase());
            manager.updateSnapshot(home); var targets = targets(world, home); targets.forEach(manager::updateSnapshot);
            var player = helper.makeMockPlayer(); player.setPos(monitor.getBlockPos().getCenter());
            try {
                var incoming = card(CHANNEL); player.setItemInHand(InteractionHand.MAIN_HAND, incoming);
                var screen = hit(monitor.getBlockPos(), Direction.NORTH, .5, .625);
                SimpleRadioRepeaters.RELAY_MONITOR.use(monitor.getBlockState(), world, monitor.getBlockPos(), player, InteractionHand.MAIN_HAND, screen);
                check(incoming.isEmpty() && monitor.reading() != null && monitor.reading().tx() == 2 && monitor.reading().rx() == 2,
                        "A tuned card on the front must fill the sole slot and open the overview");
                check(relay.channels().isEmpty() && !manager.isEligible(relay.relayId())
                                && manager.network(world, CHANNEL).towers().stream().noneMatch(t -> t.id().equals(home.id())),
                        "A monitor card must never become a forwarding port");
                var cached = monitor.reading(); monitor.refresh(); check(monitor.reading() == cached, "An unchanged revision/card must reuse its survey");
                pages.put("overview", monitor.getUpdateTag().toString());
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                SimpleRadioRepeaters.RELAY_MONITOR.use(monitor.getBlockState(), world, monitor.getBlockPos(), player, InteractionHand.MAIN_HAND, screen);
                var map = monitor.displayedMap();
                check(map != null && map.markers().size() == 1 && map.markers().get(0).kind() == MonitorMap.Kind.TX_RX
                                && map.markers().get(0).label().equals("O-A"),
                        "The map must show the one reachable same-channel tower once, without frequency translations or FM");
                var mapTag = monitor.getUpdateTag();
                check(mapTag.getIntArray("MonitorMap").length == 1 && !mapTag.contains("MonitorLines")
                                && !monitor.saveWithoutMetadata().contains("MonitorMap"),
                        "Only the displayed map cells belong in the client update, never in saved inventory data");
                var clientMonitor = new RelayMonitorBlockEntity(monitor.getBlockPos(), monitor.getBlockState());
                clientMonitor.load(mapTag);
                check(map.equals(clientMonitor.displayedMap()), "A tracking client must reconstruct the displayed map");
                monitor.refresh();
                check(monitor.displayedMap() == map, "An unchanged map must not be resurveyed");
                var reachable = targets.get(0);
                manager.updateSnapshot(reachable.settings(reachable.channels(), false, reachable.catalystItem(),
                        null, reachable.attachment(), reachable.transponder()));
                monitor.refresh();
                check(monitor.displayedMap() != null && monitor.displayedMap().markers().isEmpty(),
                        "Disabling a relay must remove its marker while the map stays open");
                manager.updateSnapshot(reachable);
                monitor.refresh();
                check(map.equals(monitor.displayedMap()), "Restoring the relay must restore its marker");
                pages.put("map", mapTag.toString());
                for (String name : List.of("both", "rx", "tx")) {
                    SimpleRadioRepeaters.RELAY_MONITOR.use(monitor.getBlockState(), world, monitor.getBlockPos(), player, InteractionHand.MAIN_HAND, screen);
                    pages.put(name, monitor.getUpdateTag().toString());
                }
                check(monitor.lines().get(0).contains("TX only") && monitor.lines().contains("SSE"), "The final target page must be the SSE TX-only entry");
                String payload = monitor.getUpdateTag().toString(); var destination = targets.get(2);
                check(!payload.contains(String.valueOf(destination.antennaBase().getX())) && !payload.contains(String.valueOf(destination.antennaBase().getZ())),
                        "Exact TX-only coordinates must not be included anywhere in the client update tag");
                var compass = new ItemStack(Items.COMPASS); compass.getOrCreateTag().putString("CustomTest", "keep");
                player.setItemInHand(InteractionHand.MAIN_HAND, compass); var page = monitor.lines();
                check(SimpleRadioRepeaters.RELAY_MONITOR.use(monitor.getBlockState(), world, monitor.getBlockPos(), player, InteractionHand.MAIN_HAND, screen).consumesAction()
                                && monitor.lines().equals(page), "Using a compass must bind without cycling");
                BlockPos estimate = monitor.reading().entries().stream().filter(e -> e.id().equals(destination.id())).findFirst().orElseThrow().compassTarget();
                check(!estimate.equals(destination.antennaBase())
                                && NbtUtils.readBlockPos(compass.getTag().getCompound(CompassItem.TAG_LODESTONE_POS)).equals(estimate)
                                && compass.getTag().getString(CompassItem.TAG_LODESTONE_DIMENSION).equals(destination.dimension())
                                && !compass.getTag().getBoolean(CompassItem.TAG_LODESTONE_TRACKED)
                                && compass.getTag().getString("CustomTest").equals("keep"), "TX-only compass binding must use only the coarse estimate and preserve unrelated item data");
                var restored = ItemStack.of(compass.save(new CompoundTag()));
                Items.COMPASS.inventoryTick(restored, world, player, 0, false);
                check(restored.getTag().contains(CompassItem.TAG_LODESTONE_POS) && !world.hasChunkAt(destination.antennaBase()),
                        "A saved compass must retain its target without a lodestone or target-chunk load");
                manager.updateSnapshot(destination.settings(Set.of(CHANNEL), true, destination.catalystItem(), null,
                        destination.attachment(), null));
                monitor.refresh();
                check(monitor.lines().get(0).contains("TX/RX")
                                && NbtUtils.readBlockPos(compass.getTag().getCompound(CompassItem.TAG_LODESTONE_POS)).equals(estimate),
                        "An existing compass must stay approximate when the tower becomes hearable");
                check(monitor.bindCompass(player, InteractionHand.MAIN_HAND)
                                && NbtUtils.readBlockPos(compass.getTag().getCompound(CompassItem.TAG_LODESTONE_POS)).equals(destination.antennaBase()),
                        "Rebinding a now-heard tower must replace the estimate with its exact antenna base");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); monitor.nextPage();
                check(monitor.lines().get(0).startsWith("TX OVERVIEW"), "Cycling past the last target must return to the overview");
                var stack = new ItemStack(Items.COMPASS, 3); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                check(monitor.bindCompass(player, InteractionHand.MAIN_HAND) && stack.getCount() == 2 && !stack.hasTag(), "Only one compass in a stack may be bound");
                boolean found = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    var item = player.getInventory().getItem(i);
                    if (item.is(Items.COMPASS) && item.hasTag() && item.getTag().contains(CompassItem.TAG_LODESTONE_POS))
                        found |= NbtUtils.readBlockPos(item.getTag().getCompound(CompassItem.TAG_LODESTONE_POS)).equals(home.antennaBase());
                }
                check(found, "The overview must bind the single new compass to this tower");
                var offhand = new ItemStack(Items.COMPASS); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, offhand); page = monitor.lines();
                SimpleRadioRepeaters.RELAY_MONITOR.use(monitor.getBlockState(), world, monitor.getBlockPos(), player, InteractionHand.MAIN_HAND, screen);
                check(offhand.hasTag() && monitor.lines().equals(page), "An offhand compass with an empty main hand must bind instead of cycling");
                var saved = monitor.saveWithoutMetadata(); check(!saved.contains("MonitorLines"), "Derived survey pages must not be persisted as stale world data");
                monitor.load(saved); monitor.refresh(); check(!manager.snapshot(home.id()).eligible() && monitor.reading() != null,
                        "Reloading a monitor inventory must retain passivity and rebuild its derived display");
                java.nio.file.Files.writeString(Path.of("monitor-preview-pages.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(pages));
                helper.succeed();
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { remove(manager, targets); world.removeBlock(monitor.getBlockPos(), false); world.removeBlock(relay.getBlockPos(), false); }
        });
    }

    private static void assertEstimate(ServerLevel world, ServerLevel destination, TowerRecord home,
                                       TowerRecord target, ChannelKey channel, double unitsPerBlock) {
        var manager = TowerManager.get(world.getServer()); manager.updateSnapshot(home); manager.updateSnapshot(target);
        try {
            var entries = RelaySurvey.scan(world.getServer(), manager, home, channel).entries();
            check(entries.size() == 1 && entries.get(0).tx() && !entries.get(0).rx(), "Fixture must produce one TX-only estimate");
            var entry = entries.get(0); var point = entry.compassTarget();
            double scale = world.dimensionType().coordinateScale() / destination.dimensionType().coordinateScale();
            double x = home.antennaBase().getCenter().x * scale, z = home.antennaBase().getCenter().z * scale;
            check(Math.abs(Math.hypot(point.getX() + .5 - x, point.getZ() + .5 - z)
                            - MonitorMath.approximateDistance(entry.distance()) / unitsPerBlock) < 2,
                    "A compass estimate must convert the displayed radio distance into destination-world blocks");
            check(entry.position() == null && !point.equals(target.antennaBase())
                            && MonitorMath.bearing(point.getX() + .5 - x, point.getZ() + .5 - z).equals(entry.bearing()),
                    "TX-only binding must use the rounded bearing without exposing a physical destination coordinate");
            check(point.getY() == home.antennaBase().getY() && point.getY() != target.antennaBase().getY(),
                    "An estimated compass must not leak the destination's height");
            check(!world.hasChunkAt(home.antennaBase()) && !destination.hasChunkAt(target.antennaBase()),
                    "Estimated and projected targets must not load chunks");
        } finally { remove(manager, List.of(home, target)); }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "monitor_survey")
    public void netherCompassEstimatesUseWorldBlocksRatherThanScaledRadioBlocks(GameTestHelper helper) {
        var world = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        var channel = new ChannelKey("098.27", "AM");
        var home = node(world, new BlockPos(2_600_000, 64, -2_600_000), Set.of(), null);
        var target = node(world, home.antennaBase().offset(137, 16, 337), Set.of(), new TransponderChannels(channel, OTHER, null, false));
        var a = com.codinglitch.simpleradio.central.WorldlyPosition.of(home.antennaBase(), world);
        var b = com.codinglitch.simpleradio.central.WorldlyPosition.of(target.antennaBase(), world);
        double units = world.dimensionType().coordinateScale() * RadioSpace.nativePropagation(a, b).distanceMultiplier();
        assertEstimate(world, world, home, target, channel, units); helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "monitor_survey")
    public void projectedCompassEstimatesResolveInThePhysicalDestinationsDimension(GameTestHelper helper) {
        var focus = RelayAttachment.stored("NETHER_FOCUS");
        if (focus == RelayAttachment.NONE) { helper.succeed(); return; }
        var overworld = helper.getLevel().getServer().overworld();
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        var channel = new ChannelKey("098.28", "AM"); var base = new BlockPos(2_800_000, 64, -2_800_000);
        var plain = node(overworld, base, Set.of(), null);
        var focused = plain.settings(Set.of(), true, plain.catalystItem(), null, focus, null);
        var below = node(nether, new BlockPos(base.getX() / 8 + 30, 80, base.getZ() / 8 + 70), Set.of(),
                new TransponderChannels(channel, OTHER, null, false));
        assertEstimate(overworld, nether, focused, below, channel, 8);
        var talking = node(nether, new BlockPos(base.getX() / 8, 64, base.getZ() / 8), Set.of(), null);
        var above = node(overworld, base.offset(240, 16, 560), Set.of(), new TransponderChannels(channel, OTHER, null, false));
        above = above.settings(above.channels(), true, above.catalystItem(), null, focus, above.transponder());
        assertEstimate(nether, overworld, talking, above, channel, 1); helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "monitor_clicks")
    public void cardAndScreenHitRegionsRespectAllFourFacingsAndDoNotExposeHopperSlots(GameTestHelper helper) {
        var world = helper.getLevel(); var player = helper.makeMockPlayer();
        for (var facing : Direction.Plane.HORIZONTAL) {
            var pos = helper.absolutePos(new BlockPos(3, 2, 3));
            world.setBlock(pos, SimpleRadioRepeaters.RELAY_MONITOR.defaultBlockState().setValue(ChannelBankBlock.FACING, facing), 3);
            var monitor = (RelayMonitorBlockEntity) world.getBlockEntity(pos);
            var held = card(CHANNEL); player.setItemInHand(InteractionHand.MAIN_HAND, held);
            var back = new BlockHitResult(pos.getCenter(), facing.getOpposite(), pos, false);
            SimpleRadioRepeaters.RELAY_MONITOR.use(monitor.getBlockState(), world, pos, player, InteractionHand.MAIN_HAND, back);
            check(!held.isEmpty() && monitor.isEmpty(), "A card on any non-front face must do nothing");
            SimpleRadioRepeaters.RELAY_MONITOR.use(monitor.getBlockState(), world, pos, player, InteractionHand.MAIN_HAND, hit(pos, facing, .5, .15625));
            check(held.isEmpty() && !monitor.isEmpty(), "The front slot must accept exactly one card in every orientation");
            for (var side : Direction.values()) check(monitor.getSlotsForFace(side).length == 0, "Hoppers must have no exposed monitor slots");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            SimpleRadioRepeaters.RELAY_MONITOR.use(monitor.getBlockState(), world, pos, player, InteractionHand.MAIN_HAND, hit(pos, facing, .1, .3));
            check(!monitor.isEmpty(), "An empty-hand background click must not eject the card");
            SimpleRadioRepeaters.RELAY_MONITOR.use(monitor.getBlockState(), world, pos, player, InteractionHand.MAIN_HAND, hit(pos, facing, .5, .15625));
            check(monitor.isEmpty(), "Clicking the card must eject it in every orientation");
            world.removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "monitor_loot")
    public void monitorHasPickaxeTagsLootAndAnAvailableRecipe(GameTestHelper helper) {
        var state = SimpleRadioRepeaters.RELAY_MONITOR.defaultBlockState();
        check(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state), "A normal pickaxe must be able to harvest the monitor");
        var world = helper.getLevel(); var pos = helper.absolutePos(BlockPos.ZERO);
        var drops = Block.getDrops(state, world, pos, null, helper.makeMockPlayer(), new ItemStack(Items.IRON_PICKAXE));
        check(drops.stream().anyMatch(s -> s.is(SimpleRadioRepeaters.RELAY_MONITOR.asItem())), "Breaking the monitor must drop its block");
        check(world.getRecipeManager().byKey(SimpleRadioRepeaters.id("relay_monitor")).isPresent(), "The monitor must have a synced crafting recipe");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300, batch = "monitor_terrain")
    public void monitorUsesTheSharedTerrainProfileAndReportsTheActualExtraDistancePercentage(GameTestHelper helper) {
        var world = helper.getLevel(); var manager = TowerManager.get(world.getServer());
        var cfg = RepeatersConfig.INSTANCE.terrain; var replacement = new RepeatersConfig.Terrain(); replacement.maxElevationAntennaBonus = 0;
        RepeatersConfig.INSTANCE.terrain = replacement; TerrainManager.reconfigure(world.getServer());
        var channel = new ChannelKey("098.26", "AM"); BlockPos base = new BlockPos(2_500_000, 64, -2_500_000);
        var home = new TowerRecord(UUID.randomUUID(), world.dimension().location().toString(), base.north(), Set.of(), true,
                RelayCatalysts.GILDED, true, 64, Set.of(), null, RelayAttachment.NONE, null, base, new BlockPos(base.getX(), 200, base.getZ()));
        var other = new TowerRecord(UUID.randomUUID(), home.dimension(), base.east(1000).north(), Set.of(channel), true,
                RelayCatalysts.GILDED, true, 64, Set.of(), null, RelayAttachment.NONE, null, base.east(1000), home.antennaTip().east(1000));
        manager.updateSnapshot(home); manager.updateSnapshot(other);
        check(RelaySurvey.scan(world.getServer(), manager, home, channel).pending() == 1, "Initial unknown terrain must be identified as pending mapping");
        var pendingMap = RelayMapSurvey.scan(world, manager, home, channel);
        check(pendingMap.pending() && pendingMap.markers().isEmpty(), "A map must not guess reach while terrain is unknown");
        helper.runAfterDelay(210, () -> {
            try {
                var profiles = world.getDataStorage().computeIfAbsent(TerrainProfiles::load, TerrainProfiles::new, TerrainProfiles.NAME);
                var key = TerrainProfiles.Key.between(base.getX(), base.getZ(), base.getX() + 1000, base.getZ(), 64, 1024);
                check(RelaySurvey.scan(world.getServer(), manager, home, channel).pending() == 1,
                        "Once tips publish, an unmeasured survey path must queue its own measurement");
                check(TerrainManager.mappingState(home.id()) == 0 && TerrainManager.mappingState(other.id()) == 0,
                        "A passive survey must not mark either relay SCAN or CHECK");
                check(profiles.get(key) != null || ((Number) TerrainManager.statistics().get("pending_relay_profiles")).longValue() > 0,
                        "A passive survey must still request its own bounded terrain measurement");
                profiles.put(key, new int[]{217, 217});
                var result = RelaySurvey.scan(world.getServer(), manager, home, channel);
                check(result.entries().size() == 1 && Math.abs(result.entries().get(0).terrainPercent() - 25) < .001,
                        "Sixteen blocks below the sampled ground must display +25%, using the shared saved profile");
                var mapped = RelayMapSurvey.scan(world, manager, home, channel);
                check(!mapped.pending() && mapped.markers().size() == 1 && mapped.markers().get(0).kind() == MonitorMap.Kind.TX_RX,
                        "A completed terrain profile must make the real two-way link visible on the map");
                long sampled = ((Number) TerrainManager.statistics().get("sampled_columns")).longValue();
                replacement.clearanceCurve[4][1] = .4;
                TerrainManager.reconfigure(world.getServer());
                var updated = RelaySurvey.scan(world.getServer(), manager, home, channel);
                check(updated.entries().size() == 1 && Math.abs(updated.entries().get(0).terrainPercent() - 40) < .001,
                        "Changing a curve point must invalidate derived penalties and update the monitor's real survey");
                check(((Number) TerrainManager.statistics().get("sampled_columns")).longValue() == sampled,
                        "A changed attenuation curve must reuse saved ground heights without generator queries");
                check(!world.hasChunkAt(base) && !world.hasChunkAt(other.antennaBase()), "Diagnostic terrain inspection must not load endpoints");
                helper.succeed();
            } finally {
                remove(manager, List.of(home, other)); RepeatersConfig.INSTANCE.terrain = cfg; TerrainManager.reconfigure(world.getServer());
            }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "monitor_raw_antenna")
    public void surveyDistinguishesRawMastScoreFromAmplifierAndElevationBonuses(GameTestHelper helper) {
        var world = helper.getLevel(); var manager = TowerManager.get(world.getServer());
        var base = new BlockPos(2_900_000, 64, -2_900_000);
        var home = new TowerRecord(UUID.randomUUID(), world.dimension().location().toString(), base.north(), Set.of(), true,
                RelayCatalysts.GILDED, true, 10, Set.of(), null, RelayAttachment.AMPLIFIER, null, base);
        manager.updateSnapshot(home);
        try {
            var result = RelaySurvey.scan(world.getServer(), manager, home, CHANNEL);
            check(result.antenna() == 10 && result.effectiveAntenna() == 17.5,
                    "ANT must show raw mast progress; the effective value must include the Ender bonus separately");
            helper.succeed();
        } finally { remove(manager, List.of(home)); }
    }
}
