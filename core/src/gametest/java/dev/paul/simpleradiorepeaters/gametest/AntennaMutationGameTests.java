package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.network.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

public class AntennaMutationGameTests implements FabricGameTest {
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    private static RelayBlockEntity fixture(GameTestHelper helper, String number) {
        var world = helper.getLevel(); var core = helper.absolutePos(new BlockPos(3, 2, 3));
        world.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        world.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        var box = core.east();
        world.setBlock(box, SimpleRadioRepeaters.RELAY.defaultBlockState().setValue(RelayBlock.FACING, Direction.EAST), 3);
        world.setBlock(box.above(), SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState(), 3);
        var relay = (RelayBlockEntity) world.getBlockEntity(box);
        relay.installCatalyst(new ItemStack(Items.GILDED_BLACKSTONE), true);
        var card = new ItemStack(SimpleRadioRepeaters.CARD);
        SimpleRadioRepeaters.CARD.setFrequency(card, number, Frequency.Modulation.AMPLITUDE);
        ((ChannelBankBlockEntity) world.getBlockEntity(box.above())).insert(0, card, true);
        return relay;
    }
    private static void ready(GameTestHelper helper, RelayBlockEntity relay, Runnable action) {
        var manager = TowerManager.get(helper.getLevel().getServer());
        helper.startSequence().thenWaitUntil(() -> check(manager.isEligible(relay.relayId()), "Fixture must be verified"))
                .thenWaitUntil(() -> {
                    long phase = helper.getLevel().getGameTime() % 200;
                    check(phase >= 40 && phase <= 150, "Exercise edits away from the periodic scan boundary");
                }).thenExecute(action);
    }
    private static void cleanup(GameTestHelper helper, RelayBlockEntity relay) {
        var world = helper.getLevel();
        world.removeBlock(relay.getBlockPos().above(), false); world.removeBlock(relay.getBlockPos(), false);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 450, batch = "antenna_real_edits")
    public void breakingRebuildingAndTrimmingAMastUpdateWithoutThePeriodicBoundary(GameTestHelper helper) {
        var world = helper.getLevel(); var relay = fixture(helper, "098.41"); var manager = TowerManager.get(world.getServer());
        ready(helper, relay, () -> {
            check(relay.getAntennaPower() == 2, "A single mast must have native strength two");
            world.removeBlock(relay.corePos().above(), false);
            check(!manager.isEligible(relay.relayId()), "A confirmed mast break must invalidate routing in the same call");
            check(!TowerRecord.load(manager.snapshot(relay.relayId()).save()).eligible(), "The broken state must persist before the next scan");
            helper.runAfterDelay(3, () -> {
                check(relay.getAntennaPower() == 0 && relay.displayStatus() == 1, "A complete empty scan must publish NO ANT without a second ten-second vote");
                world.setBlock(relay.corePos().above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
                helper.runAfterDelay(3, () -> {
                    check(manager.isEligible(relay.relayId()) && relay.getAntennaPower() == 2, "Rebuilding the mast must restore it within a few ticks");
                    world.setBlock(relay.corePos().above(2), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
                    helper.runAfterDelay(3, () -> {
                        int taller = AntennaScanner.scan(world, relay.getBlockPos(), relay.corePos()).score();
                        check(taller > 2 && relay.getAntennaPower() == taller, "A mast addition must settle through native block ticks before publishing its real score");
                        world.removeBlock(relay.corePos().above(2), false);
                        check(!manager.isEligible(relay.relayId()), "A structural trim must invalidate the old higher-power routes immediately");
                        helper.runAfterDelay(3, () -> {
                            check(manager.isEligible(relay.relayId()) && relay.getAntennaPower() == 2, "Trimming must promptly publish the smaller surviving antenna");
                            world.removeBlock(relay.corePos(), false);
                            check(!manager.isEligible(relay.relayId()), "Removing the antenna's support core must also invalidate immediately");
                            cleanup(helper, relay); helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "antenna_chunk_unload")
    public void unloadingWithoutBlockReplacementKeepsTheVerifiedAntennaAndGraph(GameTestHelper helper) {
        var world = helper.getLevel(); var relay = fixture(helper, "098.42"); var manager = TowerManager.get(world.getServer());
        ready(helper, relay, () -> {
            var saved = manager.snapshot(relay.relayId()); var channel = relay.channels().iterator().next();
            var network = manager.network(world, channel);
            relay.setRemoved();
            ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(world, world.getChunkAt(relay.getBlockPos()));
            helper.runAfterDelay(3, () -> {
                check(manager.snapshot(relay.relayId()).equals(saved) && manager.isEligible(relay.relayId()),
                        "Entity/chunk unloading without a block mutation must retain the verified saved antenna");
                check(manager.network(world, channel) == network, "An ordinary unload must preserve the cached graph");
                cleanup(helper, relay); helper.succeed();
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "antenna_saved_break")
    public void breakingTheMastWithItsRelayUnloadedInvalidatesTheSavedRecord(GameTestHelper helper) {
        var world = helper.getLevel(); var relay = fixture(helper, "098.43"); var manager = TowerManager.get(world.getServer());
        ready(helper, relay, () -> {
            relay.setRemoved();
            check(manager.isEligible(relay.relayId()), "An unload alone must preserve the tower");
            world.removeBlock(relay.corePos().above(), false);
            check(!manager.isEligible(relay.relayId()), "The loaded mast's actual removal must also invalidate an unloaded relay box");
            helper.runAfterDelay(3, () -> {
                check(!TowerRecord.load(manager.snapshot(relay.relayId()).save()).eligible(), "Saving after leaving must not resurrect the removed mast");
                cleanup(helper, relay); helper.succeed();
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "antenna_missing_dependencies")
    public void missingDependenciesKeepGraceUntilAnActualMastIsBroken(GameTestHelper helper) {
        var world = helper.getLevel(); var relay = fixture(helper, "098.44"); var manager = TowerManager.get(world.getServer());
        ready(helper, relay, () -> {
            var saved = manager.snapshot(relay.relayId()); var unavailable = relay.corePos().east(20_000);
            check(!world.hasChunkAt(unavailable), "Historical dependency must begin unloaded");
            var footprint = new HashSet<>(saved.footprint()); footprint.add(unavailable.asLong());
            manager.updateSnapshot(new TowerRecord(saved.id(), saved.dimension(), saved.position(), saved.channels(), saved.enabled(),
                    saved.catalystItem(), saved.verified(), saved.antennaScore(), footprint, saved.cardBank(), saved.attachment(),
                    saved.transponder(), saved.antennaBase(), saved.antennaTip()));
            world.setBlock(relay.corePos().north(), Blocks.STONE.defaultBlockState(), 3);
            helper.runAfterDelay(3, () -> {
                check(manager.isEligible(relay.relayId()) && relay.getAntennaPower() == 2 && !world.hasChunkAt(unavailable),
                        "A nearby edit cannot turn unavailable historical dependencies into a false disconnection or load them");
                world.removeBlock(relay.corePos().above(), false);
                check(!manager.isEligible(relay.relayId()), "Known destruction must override grace even if another historical chunk is unavailable");
                helper.runAfterDelay(3, () -> {
                    check(!manager.isEligible(relay.relayId()) && !world.hasChunkAt(unavailable), "The destroyed installation must stay inactive without loading remote chunks");
                    cleanup(helper, relay); helper.succeed();
                });
            });
        });
    }
}
