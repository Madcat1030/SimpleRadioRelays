package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.*;
import com.codinglitch.simpleradio.core.registry.blocks.InsulatorBlock;
import com.codinglitch.simpleradio.core.registry.blocks.InsulatorBlockEntity;
import com.codinglitch.simpleradio.core.registry.entities.Wire;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.*;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.item.FrequencyCardItem;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.RelayCatalysts;
import dev.paul.simpleradiorepeaters.radio.RelaySourceState;
import dev.paul.simpleradiorepeaters.radio.RelayFrame;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Runs only in Fabric's dedicated game-test environment; it does not capture microphones. */
public class RepeaterGameTests implements FabricGameTest {
    private static void check(boolean value, String description) { if (!value) throw new IllegalStateException(description); }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 90)
    public void chunk_load_removes_relay_deleted_without_callbacks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity bank = configuredRelay(level, core, 81);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); UUID id = bank.relayId();
            check(manager.isEligible(id), "Fixture bank must have a verified saved record");
            ChannelKey channel = bank.channels().iterator().next();
            var before = manager.network(level, channel);
            BlockPos pos = bank.getBlockPos(); var chunk = level.getChunkAt(pos);
            chunk.removeBlockEntity(pos);
            // Model an editor restoring section data without Block.onRemove.
            chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15,
                    pos.getZ() & 15, Blocks.AIR.defaultBlockState());
            chunk.setUnsaved(true);
            check(manager.isEligible(id), "Section replacement must bypass ordinary removal, reproducing the stale record");
            ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, chunk);
            helper.runAfterDelay(3, () -> {
                check(manager.snapshot(id) == null, "Chunk-load reconciliation must delete an absent bank");
                check(manager.network(level, channel) != before && manager.network(level, channel).towers().stream().noneMatch(n -> n.id().equals(id)),
                        "Deleting a stale record must invalidate cached relay routes");
                TowerSavedData data = level.getServer().overworld().getDataStorage().computeIfAbsent(TowerSavedData::load, TowerSavedData::new, TowerSavedData.NAME);
                check(data.isDirty() && !TowerSavedData.load(data.save(new CompoundTag())).towers.containsKey(id),
                        "The deletion must be saved so restarting cannot restore the ghost relay");
                helper.succeed();
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 90)
    public void chunk_load_rejects_old_identity_and_preserves_current_relay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity bank = configuredRelay(level, core, 82);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); TowerRecord current = manager.snapshot(bank.relayId());
            UUID oldId = UUID.randomUUID();
            manager.updateSnapshot(new TowerRecord(oldId, current.dimension(), current.position(), current.channels(), true, RelayCatalysts.GILDED, true,
                    100, current.footprint()));
            ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, level.getChunkAt(bank.getBlockPos()));
            helper.runAfterDelay(3, () -> {
                check(manager.snapshot(oldId) == null, "A restored bank with a different UUID must retire the old record");
                check(manager.isEligible(bank.relayId()) && manager.snapshot(bank.relayId()).equals(current),
                        "Identity reconciliation must retain the matching bank and its antenna cache");
                level.setBlock(bank.getBlockPos(), Blocks.AIR.defaultBlockState(), 3); helper.succeed();
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void chunk_load_waits_for_relay_entity_data(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity bank = configuredRelay(level, core, 83);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); TowerRecord verified = manager.snapshot(bank.relayId());
            CompoundTag nbt = bank.saveWithoutMetadata(); BlockPos pos = bank.getBlockPos(); var chunk = level.getChunkAt(pos);
            chunk.removeBlockEntity(pos);
            ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, chunk);
            helper.runAfterDelay(8, () -> {
                check(manager.snapshot(bank.relayId()).equals(verified), "A bank block awaiting its entity must keep its verified record");
                RelayBlockEntity restored = new RelayBlockEntity(pos, bank.getBlockState()); restored.load(nbt);
                level.setBlockEntity(restored);
                helper.runAfterDelay(8, () -> {
                    check(manager.isEligible(restored.relayId()) && manager.snapshot(restored.relayId()).antennaScore() == verified.antennaScore(),
                            "Restored matching identity must survive reconciliation and antenna settling");
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); helper.succeed();
                });
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 90)
    public void startup_refresh_checks_loaded_records_without_loading_remote_chunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity bank = configuredRelay(level, core, 84);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); UUID id = bank.relayId();
            BlockPos remote = core.offset(20000, 0, 0); UUID remoteId = UUID.randomUUID();
            check(!level.hasChunkAt(remote), "Remote fixture must be unloaded");
            TowerRecord remoteRecord = new TowerRecord(remoteId, level.dimension().location().toString(), remote,
                    bank.channels(), true, RelayCatalysts.GILDED, true, 32, Set.of(remote.asLong()));
            manager.updateSnapshot(remoteRecord);
            var chunk = level.getChunkAt(bank.getBlockPos()); BlockPos pos = bank.getBlockPos();
            chunk.removeBlockEntity(pos);
            chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15,
                    pos.getZ() & 15, Blocks.AIR.defaultBlockState());
            manager.reconcileLoadedChunks(); // Shared startup/refresh queue; do not reset other fixtures' antennas.
            helper.runAfterDelay(3, () -> {
                check(manager.snapshot(id) == null, "Already-loaded chunks must be reconciled without a new load event");
                check(manager.snapshot(remoteId).equals(remoteRecord) && !level.hasChunkAt(remote),
                        "Reconciliation must retain unloaded records without loading their chunks");
                manager.removeAt(remoteRecord.dimension(), remote); helper.succeed();
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 150)
    public void in_world_noops_do_not_restart_antenna_verification(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity relay = configuredRelay(level, core, 96);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); ChannelBankBlockEntity bank = relay.findAssignedBank();
            var player = helper.makeMockPlayer(); player.setPos(bank.getBlockPos().getCenter());
            check(relay.displayStatus() == 0 && manager.isEligible(relay.relayId()), "Fixture must finish antenna verification");
            TowerRecord snapshot = manager.snapshot(relay.relayId());
            bank.setChanged(); bank.setItem(0, bank.getItem(0).copy()); relay.setEnabled(true);
            var blankHit = new net.minecraft.world.phys.BlockHitResult(bank.getBlockPos().getCenter().add(-.25, .3, -.5),
                    Direction.NORTH, bank.getBlockPos(), false);
            SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, bank.getBlockPos(), player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, blankHit);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
            SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, bank.getBlockPos(), player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, blankHit);
            check(player.getMainHandItem().is(Items.DIRT) && bank.getItem(1).isEmpty(), "Wrong items must stay outside card slots");
            RelayBlockEntity.tick(level, relay.getBlockPos(), relay.getBlockState(), relay);
            check(relay.displayStatus() == 0 && manager.snapshot(relay.relayId()).equals(snapshot),
                    "Rejected items, empty slots and equivalent writes must not start a relay check");
            ItemStack catalyst = relay.removeCatalyst();
            check(relay.displayStatus() == 2 && !manager.isEligible(relay.relayId()), "Catalyst removal must immediately stop relaying");
            relay.installCatalyst(catalyst, false);
            check(relay.displayStatus() == 6, "Catalyst insertion must verify while retaining the antenna cache");
            helper.runAfterDelay(30, () -> {
                check(relay.displayStatus() == 0, "Catalyst verification must finish");
                relay.setEnabled(false);
                check(relay.displayStatus() == 4 && !manager.isEligible(relay.relayId()), "Disabling must immediately stop relaying");
                relay.setEnabled(true);
                check(relay.displayStatus() == 6 && manager.isEligible(relay.relayId()), "Turning on must trigger verification");
                helper.runAfterDelay(30, () -> {
                    check(relay.displayStatus() == 0, "Enable verification must finish");
                    ItemStack card = bank.removeItem(0, 1);
                    check(relay.displayStatus() == 3 && !manager.isEligible(relay.relayId()), "Last-card removal must immediately remove its channel");
                    bank.setItem(0, card);
                    check(relay.displayStatus() == 6 && manager.isEligible(relay.relayId()), "Adding a card must verify");
                    helper.runAfterDelay(30, () -> {
                        check(relay.displayStatus() == 0, "Card verification must finish");
                        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
                        String number = frequencies.incrementFrequency(frequencies.defaultFrequency(), 97);
                        bank.getItem(0).getOrCreateTag().putString("frequency", number); bank.setChanged();
                        check(relay.displayStatus() == 6 && manager.snapshot(relay.relayId()).channels().contains(new ChannelKey(number, "AM")),
                                "In-place card tuning must publish and verify the new channel");
                        level.setBlock(relay.getBlockPos(), Blocks.AIR.defaultBlockState(), 3); helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void blank_card_and_native_tuning(GameTestHelper helper) {
        ItemStack blank = new ItemStack(SimpleRadioRepeaters.CARD);
        check(SimpleRadioRepeaters.CARD.getFrequency(blank) != null, "Untuned card must give the native bench a non-null display frequency");
        check(!FrequencyCardItem.isTuned(blank), "Opening the bench must not tune a blank card");
        var api = ServerSimpleRadioApi.getInstance();
        SimpleRadioRepeaters.CARD.setFrequency(blank, api.frequencies().defaultFrequency(), Frequency.Modulation.AMPLITUDE);
        check(FrequencyCardItem.isTuned(blank), "Native tuning must store channel fields");
        check(blank.getTag().getString("modulation").equals("AM"), "Card must store native AM shorthand"); helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void split_hardware_scans_antenna_and_saves_components(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity relay = configuredRelay(level, core, 98); ChannelBankBlockEntity bank = relay.findAssignedBank();
        helper.runAfterDelay(30, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); relay.refresh(manager);
            check(relay.getAntennaPower() > 0 && manager.isEligible(relay.relayId()), "Split hardware must scan the native antenna");
            CompoundTag saved = relay.saveWithoutMetadata();
            RelayBlockEntity restored = new RelayBlockEntity(relay.getBlockPos(), relay.getBlockState()); restored.load(saved);
            check(restored.relayId().equals(relay.relayId()) && restored.hasCatalyst(), "Relay identity and direct catalyst must survive reload");
            check(!saved.contains("Items") && !((Object) restored instanceof net.minecraft.world.Container), "Relay must have no inventory");
            ChannelBankBlockEntity restoredBank = new ChannelBankBlockEntity(bank.getBlockPos(), bank.getBlockState());
            restoredBank.load(bank.saveWithoutMetadata());
            check(restoredBank.bankId().equals(bank.bankId()) && restoredBank.getItem(0).is(SimpleRadioRepeaters.CARD),
                    "The separate bank must retain its six-card inventory and identity");
            check(restoredBank.getContainerSize() == 6 && restored.cardBank().id().equals(bank.bankId()), "Relay must refer to the separate six-slot bank");
            relay.setEnabled(false); check(!manager.isEligible(relay.relayId()), "Disabling must immediately remove the tower from routes");
            level.setBlock(relay.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
            check(manager.snapshot(relay.relayId()) == null, "Destroying a Relay must remove its network record");
            check(bank.getItem(0).is(SimpleRadioRepeaters.CARD), "Removing the Relay must leave the separate Bank and cards intact");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 500)
    public void wired_core_reload_preserves_cache_and_recovers_without_toggle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2)).offset(500, 0, 0);
        BlockPos core = new BlockPos((origin.getX() & ~15) + 14, origin.getY(), (origin.getZ() & ~15) + 8);
        BlockPos remoteCore = core.east(4);
        BlockPos nearPos = core.east(), farPos = remoteCore.west();
        check((nearPos.getX() >> 4) != (farPos.getX() >> 4), "The extension must cross a chunk boundary");
        // Fixture tickets, never production verification, load the chunks for a real two-chunk extension.
        List<ChunkPos> fixtureChunks = new ArrayList<>();
        for (int x = (core.getX() >> 4) - 1; x <= (core.getX() >> 4) + 2; x++)
            for (int z = (core.getZ() >> 4) - 1; z <= (core.getZ() >> 4) + 1; z++) {
                ChunkPos chunk = new ChunkPos(x, z); fixtureChunks.add(chunk);
                level.setChunkForced(x, z, true); level.getChunk(x, z);
            }
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(remoteCore, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(remoteCore.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        level.setBlock(nearPos, SimpleRadioBlocks.INSULATOR.defaultBlockState().setValue(InsulatorBlock.FACING, Direction.EAST), 3);
        level.setBlock(farPos, SimpleRadioBlocks.INSULATOR.defaultBlockState().setValue(InsulatorBlock.FACING, Direction.WEST), 3);
        RelayBlockEntity bank = configuredRelay(level, core, 90);
        helper.runAfterDelay(10, () -> {
            InsulatorBlockEntity near = (InsulatorBlockEntity) level.getBlockEntity(nearPos);
            InsulatorBlockEntity far = (InsulatorBlockEntity) level.getBlockEntity(farPos);
            check(near.getRouter() != null && far.getRouter() != null, "Native insulator routers must initialize");
            Wire wire = Wire.connect(near, far, level);
            check(wire != null, "Fixture must use a real native copper wire");
            bank.markDirty();
            helper.runAfterDelay(30, () -> {
                TowerManager manager = TowerManager.get(level.getServer());
                check(bank.getAntennaPower() > 0 && manager.isEligible(bank.relayId()), "Wired antenna must first be verified");
                int score = bank.getAntennaPower();
                CompoundTag tag = bank.saveWithoutMetadata();
                tag.putInt("AntennaScore", 0); // A block-entity cache can lag the authoritative saved network record.
                near.getRouter().wires.clear(); far.getRouter().wires.clear();
                AntennaScanner.Result early = AntennaScanner.scan(level, bank.getBlockPos(), bank.corePos());
                check(early.complete() && early.score() == 0, "Reproduce native lookup's transient zero before the wire reattaches");
                bank.setRemoved();
                RelayBlockEntity restored = new RelayBlockEntity(bank.getBlockPos(), bank.getBlockState());
                restored.load(tag); level.setBlockEntity(restored);
                RelayBlockEntity.tick(level, restored.getBlockPos(), restored.getBlockState(), restored);
                check(restored.getAntennaPower() == score, "Reload must recover the authoritative cached score");
                check(manager.isEligible(restored.relayId()), "The first reload tick must retain relay eligibility");
                helper.runAfterDelay(30, () -> {
                    check(near.hasWire(wire) && far.hasWire(wire), "Native wire tick must reconnect both endpoints");
                    check(restored.getAntennaPower() == score && manager.isEligible(restored.relayId()), "Bank must recover without an enable toggle");
                    check(restored.saveWithoutMetadata().getInt("AntennaScore") == score, "Refreshed score must be saved in the block entity");
                    wire.burnOut(); restored.markDirty(); // Explicit refresh; do not patch upstream cut-wire notifications.
                    helper.runAfterDelay(30, () -> {
                        check(manager.isEligible(restored.relayId()), "One settled empty scan must not discard the cache");
                    });
                    helper.runAfterDelay(250, () -> {
                        check(!manager.isEligible(restored.relayId()), "A genuinely removed wire must eventually stop relaying");
                        check(manager.snapshot(restored.relayId()).antennaScore() == 0, "Confirmed disconnection must publish a zero score");
                        level.setBlock(restored.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
                        for (ChunkPos chunk : fixtureChunks) level.setChunkForced(chunk.x, chunk.z, false);
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 150)
    public void cached_antenna_waits_for_dependencies_without_loading_them(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity bank = configuredRelay(level, core, 92);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer());
            TowerRecord verified = manager.snapshot(bank.relayId());
            check(verified != null && verified.eligible(), "Fixture antenna must have a verified record");
            BlockPos unavailable = core.offset(20000, 0, 0);
            check(!level.hasChunkAt(unavailable), "The historical antenna dependency must be unloaded");
            Set<Long> footprint = new HashSet<>(verified.footprint()); footprint.add(unavailable.asLong());
            TowerRecord snapshot = new TowerRecord(verified.id(), verified.dimension(), verified.position(), verified.channels(),
                    verified.enabled(), verified.catalystItem(), verified.verified(), verified.antennaScore(), footprint, verified.cardBank());
            manager.updateSnapshot(snapshot); bank.markDirty();
            level.setBlock(core.east(), Blocks.STONE.defaultBlockState(), 3);
            check(manager.isEligible(bank.relayId()), "An unrelated neighbouring block change must not invalidate the snapshot");
            helper.runAfterDelay(30, () -> {
                check(manager.snapshot(bank.relayId()).equals(snapshot), "Partial loading must preserve the entire saved snapshot");
                check(manager.isEligible(bank.relayId()), "The saved antenna must continue relaying while verification waits");
                check(!level.hasChunkAt(unavailable), "Verification must not load the unavailable chunk");
                level.setBlock(bank.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
                helper.succeed();
            });
        });
    }

    private static RelayBlockEntity configuredRelay(ServerLevel level, BlockPos core, int channelOffset) {
        BlockPos pos = core.north(); level.setBlock(pos, SimpleRadioRepeaters.RELAY.defaultBlockState(), 3);
        level.setBlock(pos.above(), SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState(), 3);
        RelayBlockEntity relay = (RelayBlockEntity) level.getBlockEntity(pos);
        ChannelBankBlockEntity bank = (ChannelBankBlockEntity) level.getBlockEntity(pos.above());
        bank.setItem(0, tunedCard(channelOffset)); relay.installCatalyst(new ItemStack(Items.GILDED_BLACKSTONE), false);
        relay.bankChanged(); return relay;
    }
    private static ItemStack tunedCard(int offset) {
        ItemStack card = new ItemStack(SimpleRadioRepeaters.CARD);
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        SimpleRadioRepeaters.CARD.setFrequency(card, frequencies.incrementFrequency(frequencies.defaultFrequency(), offset), Frequency.Modulation.AMPLITUDE);
        return card;
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 50)
    public void missing_card_bank_invalidates_saved_relay_while_entity_is_pending(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState(), 3);
        ChannelBankBlockEntity bank = (ChannelBankBlockEntity) level.getBlockEntity(pos); bank.setItem(0, tunedCard(101));
        TowerManager manager = TowerManager.get(level.getServer());
        BlockPos remote = pos.below(); level.setBlock(remote, SimpleRadioRepeaters.RELAY.defaultBlockState(), 3);
        UUID id = ((RelayBlockEntity) level.getBlockEntity(remote)).relayId();
        level.getChunkAt(remote).removeBlockEntity(remote);
        TowerRecord record = new TowerRecord(id, level.dimension().location().toString(), remote,
                Set.of(bank.cardStates().get(0).channel()), true, RelayCatalysts.GILDED, true, 32, Set.of(remote.asLong()), new CardBankRef(pos, bank.bankId()));
        manager.updateSnapshot(record);
        helper.runAfterDelay(3, () -> {
            check(manager.isEligible(id), "Matching Bank identity must preserve the saved Relay");
            var chunk = level.getChunkAt(pos); chunk.removeBlockEntity(pos);
            chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, Blocks.AIR.defaultBlockState());
            ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, chunk);
            helper.runAfterDelay(3, () -> {
                TowerRecord cleared = manager.snapshot(id);
                check(cleared != null && !cleared.eligible() && cleared.cardBank() == null && cleared.channels().isEmpty(),
                        "Absent Bank must disable a saved Relay even while its entity is pending");
                check(cleared.antennaScore() == 32 && BankConnection.entity(level, remote) == null,
                        "Controller checks must retain antenna metadata without manufacturing a Relay entity");
                check(TowerRecord.load(cleared.save()).equals(cleared), "Controller removal must survive saving");
                manager.removeAt(record.dimension(), remote); helper.succeed();
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 90)
    public void stale_relay_nbt_cannot_restore_a_removed_bank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity relay = configuredRelay(level, core, 102);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); CompoundTag old = relay.saveWithoutMetadata();
            ChannelBankBlockEntity bank = relay.findAssignedBank();
            level.getChunkAt(relay.getBlockPos()).removeBlockEntity(relay.getBlockPos());
            level.setBlock(bank.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
            check(!manager.isEligible(relay.relayId()) && manager.snapshot(relay.relayId()).cardBank() == null,
                    "Removing a Bank must immediately clear its saved Relay configuration");
            RelayBlockEntity restored = new RelayBlockEntity(relay.getBlockPos(), relay.getBlockState()); restored.load(old); level.setBlockEntity(restored);
            helper.runAfterDelay(3, () -> {
                check(restored.cardBank() == null && restored.channels().isEmpty() && !manager.isEligible(restored.relayId()),
                        "Older Relay NBT must not revive a removed controller");
                level.setBlock(restored.getBlockPos(), Blocks.AIR.defaultBlockState(), 3); helper.succeed();
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 90)
    public void card_bank_entity_loading_preserves_configuration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity relay = configuredRelay(level, core, 103);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); TowerRecord saved = manager.snapshot(relay.relayId());
            ChannelBankBlockEntity bank = relay.findAssignedBank(); CompoundTag tag = bank.saveWithoutMetadata();
            var chunk = level.getChunkAt(bank.getBlockPos()); chunk.removeBlockEntity(bank.getBlockPos());
            ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, chunk);
            helper.runAfterDelay(8, () -> {
                check(saved.equals(manager.snapshot(relay.relayId())) && manager.isEligible(relay.relayId()),
                        "A Bank block awaiting entity data must retain the verified channel cache");
                ChannelBankBlockEntity restored = new ChannelBankBlockEntity(bank.getBlockPos(), bank.getBlockState()); restored.load(tag); level.setBlockEntity(restored);
                helper.runAfterDelay(8, () -> {
                    check(saved.equals(manager.snapshot(relay.relayId())), "Matching Bank identity must recover without a toggle");
                    ChannelBankBlockEntity copy = new ChannelBankBlockEntity(bank.getBlockPos(), bank.getBlockState()); copy.load(restored.getUpdateTag());
                    RelayBlockEntity relayCopy = new RelayBlockEntity(relay.getBlockPos(), relay.getBlockState()); relayCopy.load(relay.getUpdateTag());
                    check(copy.cardStates().equals(restored.cardStates()) && copy.getUpdatePacket() != null,
                            "Client update data must include card presence, frequency and modulation");
                    check(relayCopy.hasCatalyst() && relayCopy.getAntennaPower() == relay.getAntennaPower()
                            && relayCopy.displayStatus() == relay.displayStatus() && relayCopy.displayPeers() == relay.displayPeers(),
                            "Client update data must include the catalyst and live screen values");
                    level.setBlock(relay.getBlockPos(), Blocks.AIR.defaultBlockState(), 3); helper.succeed();
                });
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void card_display_cache_tracks_inventory_edits_and_client_update_tags(GameTestHelper helper) {
        var state = SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState();
        ChannelBankBlockEntity bank = new ChannelBankBlockEntity(BlockPos.ZERO, state);
        var empty = bank.displayCards();
        check(empty.size() == 6 && empty.stream().noneMatch(ChannelBankBlockEntity.CardDisplay::present),
                "An empty Bank must have six hidden card displays");
        for (int slot = 0; slot < 6; slot++) check(bank.insert(slot, tunedCard(140 + slot), false), "Fixture insertion must succeed");
        var full = bank.displayCards();
        check(full != empty && empty.stream().noneMatch(ChannelBankBlockEntity.CardDisplay::present),
                "Insertion must replace the display snapshot without mutating the previous one");
        for (int slot = 0; slot < 6; slot++) {
            var card = full.get(slot); var tag = bank.getItem(slot).getTag();
            check(card.present() && card.frequencyLabel().equals(tag.getString("frequency"))
                            && card.modulationLabel().equals(tag.getString("modulation"))
                            && card.label().equals(card.frequencyLabel() + " " + card.modulationLabel()),
                    "Each visible slot must retain its own stored tuning labels");
        }
        for (int frame = 0; frame < 120; frame++) check(bank.displayCards() == full, "Unchanged frames must reuse the same snapshot");
        bank.setChanged();
        check(bank.displayCards() == full && !bank.insert(0, tunedCard(150), false) && bank.displayCards() == full,
                "No-op notifications and refused insertions must preserve the cache");
        bank.removeItem(3, 1);
        check(!bank.displayCards().get(3).present() && full.get(3).present(), "Removing a card must hide only its current display");
        bank.insert(3, new ItemStack(SimpleRadioRepeaters.CARD), false);
        var blank = bank.displayCards().get(3);
        check(blank.present() && blank.frequencyLabel().equals("BLANK") && blank.modulationLabel().equals("--") && blank.label().equals("BLANK --"),
                "An inserted untuned card must keep the existing blank labels");
        bank.getItem(0).getOrCreateTag().putString("frequency", "000.77");
        bank.getItem(0).getOrCreateTag().putString("modulation", "FM"); bank.setChanged();
        var retuned = bank.displayCards();
        check(retuned != full && retuned.get(0).frequencyLabel().equals("000.77") && retuned.get(0).modulationLabel().equals("FM")
                        && retuned.get(0).label().equals("000.77 FM"),
                "An in-place tuning edit with setChanged must refresh both labels");
        bank.getItem(2).getOrCreateTag().putString("modulation", "invalid"); bank.setChanged();
        check(bank.displayCards().get(2).frequencyLabel().equals("BLANK"), "Invalid tuning must retain the existing blank-card appearance");
        ChannelBankBlockEntity clientCopy = new ChannelBankBlockEntity(BlockPos.ZERO, state);
        clientCopy.displayCards(); clientCopy.load(bank.getUpdateTag());
        var received = clientCopy.displayCards();
        check(received.equals(bank.displayCards()) && clientCopy.displayCards() == received,
                "Initial chunk/update data must replace an existing display cache and then reuse it");
        bank.removeItem(0, 1); clientCopy.load(bank.getUpdateTag());
        check(!clientCopy.displayCards().get(0).present() && received.get(0).present(),
                "A later update packet must remove stale labels without mutating an earlier snapshot");
        ItemStack extracted = clientCopy.removeItemNoUpdate(1);
        check(!clientCopy.displayCards().get(1).present(), "No-update extraction must invalidate local display data too");
        clientCopy.setItem(1, extracted);
        check(clientCopy.displayCards().get(1).present(),
                "Restoring a no-update extraction must refresh the display even when the saved configuration is unchanged");
        clientCopy.clearContent();
        check(clientCopy.displayCards().stream().noneMatch(ChannelBankBlockEntity.CardDisplay::present), "Clearing the Bank must hide every card");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void card_hit_bands_accept_the_card_and_one_row_above_in_every_facing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(); player.getAbilities().instabuild = false;
        int[] order = {5, 3, 1, 4, 2, 0};
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            level.setBlock(pos, SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState().setValue(ChannelBankBlock.FACING, facing), 3);
            ChannelBankBlockEntity bank = (ChannelBankBlockEntity) level.getBlockEntity(pos);
            for (double offset : new double[]{.25, 1.5, 3.5}) {
                bank.clearContent();
                for (int slot : order) {
                    double x = slot % 2 == 0 ? 12.0 / 16 : 4.0 / 16, bottom = 11 - (slot / 2) * 5;
                    ItemStack held = tunedCard(160 + slot), expected = held.copy();
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
                    var hit = bankFrontHit(pos, facing, x, (bottom + offset) / 16);
                    SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, pos, player,
                            net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    check(held.isEmpty() && ItemStack.isSameItemSameTags(bank.getItem(slot), expected),
                            "Clicks on or above a card must insert into that exact slot in every facing");
                }
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                CompoundTag before = bank.saveWithoutMetadata();
                for (int slot : order) for (double outside : new double[]{-.25, 4.25}) {
                    double x = slot % 2 == 0 ? 12.0 / 16 : 4.0 / 16, bottom = 11 - (slot / 2) * 5;
                    SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, pos, player,
                            net.minecraft.world.InteractionHand.MAIN_HAND, bankFrontHit(pos, facing, x, (bottom + outside) / 16));
                    check(before.equals(bank.saveWithoutMetadata()), "Clicks beyond the expanded band must not eject a neighbouring row");
                }
                for (int slot : order) {
                    double x = slot % 2 == 0 ? 12.0 / 16 : 4.0 / 16, bottom = 11 - (slot / 2) * 5;
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, pos, player,
                            net.minecraft.world.InteractionHand.MAIN_HAND, bankFrontHit(pos, facing, x, (bottom + offset) / 16));
                    check(bank.getItem(slot).isEmpty(), "Clicks on or above a card must remove that exact slot in every facing");
                }
            }
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void six_card_interactions_target_the_visible_card_in_every_direction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(); player.getAbilities().instabuild = false;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            level.setBlock(pos, SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState().setValue(ChannelBankBlock.FACING, facing), 3);
            ChannelBankBlockEntity bank = (ChannelBankBlockEntity) level.getBlockEntity(pos); bank.clearContent();
            for (int slot : new int[] {5, 3, 1, 4, 2, 0}) {
                var hit = bankFrontHit(pos, facing, slot % 2 == 0 ? 12.0 / 16 : 4.0 / 16, (13.0 - slot / 2 * 5) / 16);
                ItemStack held = tunedCard(110 + slot); held.setCount(2);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
                var result = SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, pos, player,
                        net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                check(result == net.minecraft.world.InteractionResult.CONSUME && held.getCount() == 1
                                && bank.getItem(slot).getCount() == 1 && ItemStack.isSameItemSameTags(bank.getItem(slot), held),
                        "A front-slot click must insert one matching card into that slot in every facing");
                check(bank.getItem(0).isEmpty() == (slot != 0), "Targeted insertion must leave earlier empty slots alone");
            }
            ItemStack extra = tunedCard(120); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, extra);
            var blankHit = bankFrontHit(pos, facing, .5, .5);
            check(SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, pos, player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, blankHit) == net.minecraft.world.InteractionResult.FAIL
                    && extra.getCount() == 1, "An off-slot click on a full Bank must preserve the held card");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var hit = bankFrontHit(pos, facing, 12.0 / 16, 3.0 / 16);
            SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            check(bank.getItem(4).isEmpty() && !bank.getItem(0).isEmpty() && !bank.getItem(5).isEmpty(),
                    "Clicking the bottom-left card must eject that card regardless of facing");
            check(player.getInventory().contains(tunedCard(114)), "The removed card must retain its tuning in the player's inventory");

            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, extra);
            check(SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, pos, player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, bankFrontHit(pos, facing, 4.0 / 16, 3.0 / 16))
                    == net.minecraft.world.InteractionResult.FAIL && extra.getCount() == 1 && bank.getItem(4).isEmpty(),
                    "An occupied target must not replace its card or spill into another empty slot");
            check(SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, pos, player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, blankHit) == net.minecraft.world.InteractionResult.CONSUME
                    && extra.isEmpty() && ItemStack.isSameItemSameTags(bank.getItem(4), tunedCard(120)),
                    "A front click outside the slots must fill the first available slot");

            bank.removeItem(2, 1); bank.removeItem(5, 1);
            player.getAbilities().instabuild = true; ItemStack creative = tunedCard(121);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, creative);
            SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, pos, player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, bankFrontHit(pos, facing, 4.0 / 16, 3.0 / 16));
            check(creative.getCount() == 1 && bank.getItem(2).isEmpty()
                            && ItemStack.isSameItemSameTags(bank.getItem(5), creative),
                    "Creative insertion must target the clicked slot without consuming the held card");
            player.getAbilities().instabuild = false;
        }
        helper.succeed();
    }

    private static net.minecraft.world.phys.BlockHitResult bankFrontHit(BlockPos pos, Direction facing, double x, double y) {
        net.minecraft.world.phys.Vec3 local = switch (facing) {
            case EAST -> new net.minecraft.world.phys.Vec3(1, y, x);
            case SOUTH -> new net.minecraft.world.phys.Vec3(1 - x, y, 1);
            case WEST -> new net.minecraft.world.phys.Vec3(0, y, 1 - x);
            default -> new net.minecraft.world.phys.Vec3(x, y, 0);
        };
        return new net.minecraft.world.phys.BlockHitResult(local.add(pos.getX(), pos.getY(), pos.getZ()), facing, pos, false);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void card_bank_front_background_and_other_faces_leave_inventory_unchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(); player.getAbilities().instabuild = false;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            level.setBlock(pos, SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState().setValue(ChannelBankBlock.FACING, facing), 3);
            ChannelBankBlockEntity bank = (ChannelBankBlockEntity) level.getBlockEntity(pos); bank.clearContent();
            for (int slot = 0; slot < 5; slot++) bank.setItem(slot, tunedCard(130 + slot));
            CompoundTag before = bank.saveWithoutMetadata();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            for (var hit : List.of(bankFrontHit(pos, facing, .5, .5), bankFrontHit(pos, facing, 4.0 / 16, 3.0 / 16))) {
                check(SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, pos, player,
                        net.minecraft.world.InteractionHand.MAIN_HAND, hit) == net.minecraft.world.InteractionResult.PASS
                        && before.equals(bank.saveWithoutMetadata()), "Empty-hand background and empty-slot clicks must do nothing");
            }
            var bounds = bank.getBlockState().getShape(level, pos).bounds();
            for (Direction side : Direction.values()) {
                if (side == facing) continue;
                double x = side == Direction.WEST ? bounds.minX : side == Direction.EAST ? bounds.maxX : (bounds.minX + bounds.maxX) / 2;
                double y = side == Direction.DOWN ? bounds.minY : side == Direction.UP ? bounds.maxY : (bounds.minY + bounds.maxY) / 2;
                double z = side == Direction.NORTH ? bounds.minZ : side == Direction.SOUTH ? bounds.maxZ : (bounds.minZ + bounds.maxZ) / 2;
                var hit = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(x, y, z)
                        .add(pos.getX(), pos.getY(), pos.getZ()), side, pos, false);
                for (ItemStack held : List.of(ItemStack.EMPTY, tunedCard(140))) {
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
                    check(SimpleRadioRepeaters.CHANNEL_BANK.use(bank.getBlockState(), level, pos, player,
                            net.minecraft.world.InteractionHand.MAIN_HAND, hit) == net.minecraft.world.InteractionResult.PASS
                            && before.equals(bank.saveWithoutMetadata()) && (held.isEmpty() || held.getCount() == 1),
                            "Non-front faces must leave both the Bank and the player's held item unchanged");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void saved_record_round_trip(GameTestHelper helper) {
        TowerRecord record = new TowerRecord(UUID.randomUUID(), helper.getLevel().dimension().location().toString(),
                new BlockPos(9000, 80, 9000), Set.of(new ChannelKey("123.4", "AM"), new ChannelKey("123.4", "FM")),
                true, RelayCatalysts.GILDED, true, 12, Set.of(new BlockPos(9000, 80, 9000).asLong()), new CardBankRef(new BlockPos(9000, 81, 9000), UUID.randomUUID()));
        check(record.equals(TowerRecord.load(record.save())), "Saved tower metadata must round-trip without merging AM/FM");
        TowerSavedData data = new TowerSavedData(); data.towers.put(record.id(), record);
        TowerSavedData restored = TowerSavedData.load(data.save(new CompoundTag()));
        check(restored.towers.get(record.id()).equals(record), "Whole network store must restore unchanged"); helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 50)
    public void changed_card_bank_identity_retires_the_saved_binding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState(), 3);
        ChannelBankBlockEntity bank = (ChannelBankBlockEntity) level.getBlockEntity(pos);
        bank.setItem(0, tunedCard(99));
        BlockPos remote = pos.below(); level.setBlock(remote, SimpleRadioRepeaters.RELAY.defaultBlockState(), 3);
        UUID id = ((RelayBlockEntity) level.getBlockEntity(remote)).relayId();
        level.getChunkAt(remote).removeBlockEntity(remote);
        TowerManager manager = TowerManager.get(level.getServer());
        TowerRecord record = new TowerRecord(id, level.dimension().location().toString(), remote,
                Set.of(bank.cardStates().get(0).channel()), true, RelayCatalysts.GILDED, true, 32, Set.of(remote.asLong()), new CardBankRef(pos, bank.bankId()));
        manager.updateSnapshot(record);
        helper.runAfterDelay(3, () -> {
            check(manager.isEligible(id), "Matching Bank identity must remain eligible");
            var chunk = level.getChunkAt(pos); chunk.removeBlockEntity(pos);
            ChannelBankBlockEntity replacement = new ChannelBankBlockEntity(pos, bank.getBlockState());
            check(!replacement.bankId().equals(bank.bankId()), "Replacement must have a different identity"); level.setBlockEntity(replacement);
            ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, chunk);
            helper.runAfterDelay(3, () -> {
                check(!manager.isEligible(id) && manager.snapshot(id).cardBank() == null,
                        "A different Bank at the same position must retire the old saved binding");
                check(BankConnection.entity(level, remote) == null, "Identity reconciliation must not manufacture a Relay entity");
                manager.removeAt(record.dimension(), remote); helper.succeed();
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bank_connection_requires_the_relay_directly_below(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        level.setBlock(pos.below(), SimpleRadioRepeaters.RELAY.defaultBlockState(), 3);
        level.setBlock(pos.north(), SimpleRadioRepeaters.RELAY.defaultBlockState(), 3);
        level.setBlock(pos, SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState(), 3);
        RelayBlockEntity below = (RelayBlockEntity) level.getBlockEntity(pos.below());
        RelayBlockEntity side = (RelayBlockEntity) level.getBlockEntity(pos.north());
        ChannelBankBlockEntity bank = (ChannelBankBlockEntity) level.getBlockEntity(pos); bank.setItem(0, tunedCard(125));
        below.bankChanged(); side.bankChanged();
        check(bank.relay() == below && below.findAssignedBank() == bank && side.findAssignedBank() == null,
                "A Bank must bind only to the Relay below");
        check(below.channels().size() == 1 && side.channels().isEmpty(), "Unassigned Relays must not use the Bank's cards");
        level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3); side.bankChanged();
        check(bank.relay() == null && side.findAssignedBank() == null && side.channels().isEmpty() && !bank.getItem(0).isEmpty(),
                "Removing the Relay below must preserve cards without falling back to a side Relay"); helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bank_placement_requires_a_relay_below_and_copies_its_facing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(); var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            level.setBlock(pos, SimpleRadioRepeaters.RELAY.defaultBlockState().setValue(RelayBlock.FACING, facing), 3);
            for (Direction face : Direction.values()) {
                var hit = new net.minecraft.world.phys.BlockHitResult(pos.getCenter().relative(face, .5), face, pos, false);
                var context = new net.minecraft.world.item.context.BlockPlaceContext(level, player, hand,
                        new ItemStack(SimpleRadioRepeaters.CHANNEL_BANK), hit);
                var placed = SimpleRadioRepeaters.CHANNEL_BANK.getStateForPlacement(context);
                check(face == Direction.UP ? placed != null && placed.getValue(ChannelBankBlock.FACING) == facing : placed == null,
                        "A Bank may be placed only above a Relay and must inherit its facing");
            }
        }
        level.setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        var hit = new net.minecraft.world.phys.BlockHitResult(pos.getCenter().add(0, .5, 0), Direction.UP, pos, false);
        var context = new net.minecraft.world.item.context.BlockPlaceContext(level, player, hand,
                new ItemStack(SimpleRadioRepeaters.CHANNEL_BANK), hit);
        check(SimpleRadioRepeaters.CHANNEL_BANK.getStateForPlacement(context) == null, "Other blocks must not accept the Bank plug");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void copied_relay_uses_only_its_own_top_bank_and_preserves_the_original(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity original = configuredRelay(level, core, 190);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); TowerRecord saved = manager.snapshot(original.relayId());
            check(saved != null && saved.eligible(), "Original tower must be verified before copying");
            // Use the original's loaded terrain chunk; this fixture tests copied NBT, not chunk activation.
            BlockPos copyCore = core.above(64), copyPos = copyCore.north();
            level.setBlock(copyPos.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(copyCore, Blocks.IRON_BLOCK.defaultBlockState(), 3);
            level.setBlock(copyCore.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
            level.setBlock(copyPos, original.getBlockState(), 3);
            RelayBlockEntity copy = (RelayBlockEntity) level.getBlockEntity(copyPos); copy.load(original.saveWithoutMetadata());
            check(copy.cardBank() == null && copy.channels().isEmpty() && copy.findAssignedBank() == null,
                    "Copied NBT must immediately discard the distant binding and cached channels");
            level.setBlock(copyPos.above(), SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState(), 3);
            ChannelBankBlockEntity bank = (ChannelBankBlockEntity) level.getBlockEntity(copyPos.above()); bank.setItem(0, tunedCard(191));
            copy.bankChanged();
            check(manager.snapshot(original.relayId()).equals(saved), "Pre-tick placement notifications must not alter the original's saved record");
            RelayBlockEntity.tick(level, copyPos, copy.getBlockState(), copy);
            check(!copy.relayId().equals(original.relayId()) && copy.cardBank() != null && copy.cardBank().position().equals(copyPos.above())
                            && copy.channels().equals(Set.of(bank.cardStates().get(0).channel())),
                    "The copy must get its own identity and use its local top-mounted Bank");
            helper.runAfterDelay(30, () -> {
                check(manager.isEligible(copy.relayId()), "The copy must work once its own antenna and Bank are verified");
                original.findAssignedBank().removeItem(0, 1);
                check(!manager.isEligible(original.relayId()) && manager.isEligible(copy.relayId())
                                && manager.snapshot(copy.relayId()).channels().equals(copy.channels()),
                        "Editing the original Bank must not change the copied tower");
                level.setBlock(copyPos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(copyPos.above(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(original.getBlockPos(), Blocks.AIR.defaultBlockState(), 3); helper.succeed();
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void invalid_saved_bank_bindings_are_cleared_without_loading_either_chunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); TowerManager manager = TowerManager.get(level.getServer());
        BlockPos relay = helper.absolutePos(new BlockPos(20000, 80, 20000)), oldBank = relay.offset(4000, 1, 0);
        UUID id = UUID.randomUUID(), bankId = UUID.randomUUID(); Set<ChannelKey> channels = Set.of(new ChannelKey("000.01", "AM"));
        check(!level.hasChunkAt(relay) && !level.hasChunkAt(oldBank), "Both copied-reference fixture chunks must be unloaded");
        TowerRecord valid = new TowerRecord(id, level.dimension().location().toString(), relay, channels, true, RelayCatalysts.GILDED, true, 32,
                Set.of(relay.asLong()), new CardBankRef(relay.above(), bankId));
        CompoundTag raw = valid.save(); raw.putLong("CardBankPosition", oldBank.asLong());
        CompoundTag store = new CompoundTag(); var records = new net.minecraft.nbt.ListTag(); records.add(raw); store.put("Towers", records);
        TowerSavedData restored = TowerSavedData.load(store); TowerRecord cleared = restored.towers.get(id);
        check(cleared.cardBank() == null && cleared.channels().isEmpty() && !cleared.eligible() && restored.isDirty(),
                "Restoring an invalid binding must clear channels and schedule the corrected record for saving");
        check(cleared.antennaScore() == 32 && !level.hasChunkAt(relay) && !level.hasChunkAt(oldBank),
                "Geometry validation must retain antenna metadata and read neither chunk");
        for (BlockPos misplaced : List.of(relay.north(), relay.below())) {
            TowerRecord rejected = new TowerRecord(id, valid.dimension(), relay, channels, true, RelayCatalysts.GILDED, true, 32,
                    valid.footprint(), new CardBankRef(misplaced, bankId));
            check(rejected.cardBank() == null && rejected.channels().isEmpty(), "Side and bottom bindings must also be rejected");
        }
        manager.updateSnapshot(cleared);
        level.setBlock(oldBank, SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState(), 3);
        ChannelBankBlockEntity bank = (ChannelBankBlockEntity) level.getBlockEntity(oldBank);
        CompoundTag bankTag = bank.saveWithoutMetadata(); bankTag.putUUID("CardBankId", bankId); bankTag.putLong("RelayPosition", relay.asLong()); bank.load(bankTag);
        bank.setItem(0, tunedCard(192)); manager.controllerChanged(level, bank);
        check(!manager.isEligible(id) && manager.snapshot(id).cardBank() == null && bank.primaryRelayPosition() == null
                        && !level.hasChunkAt(relay), "Loading or editing the old Bank must not revive a distant binding or its owner hint");
        manager.removeAt(valid.dimension(), relay); level.setBlock(oldBank, Blocks.AIR.defaultBlockState(), 3); helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void rejected_receiver_copy_does_not_reserve_delivery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var api = ServerSimpleRadioApi.getInstance();
        Frequency channel = api.frequencies().getOrCreate(api.frequencies().incrementFrequency(api.frequencies().defaultFrequency(), 193), Frequency.Modulation.AMPLITUDE);
        RadioReceiver receiver = new RadioReceiver(channel, WorldlyPosition.of(pos, level)); receiver.frequencingType(SimpleRadioFrequencing.RECEIVER);
        receiver.identifier = (short) 25000; CountingRouter terminal = new CountingRouter(receiver.position); terminal.identifier = (short) 25001;
        receiver.addRouter(terminal); int[] checks = {0}; receiver.setAcceptingCriteria(source -> { checks[0]++; return source.getPower() > 50; });
        RadioSource rejected = new RadioSource(UUID.randomUUID(), receiver.position, new byte[]{1, 2, 3}, 1);
        rejected.frequencingType = SimpleRadioFrequencing.TRANSMITTER.id; rejected.transmissionCap = 100; rejected.transmissionPower = 10;
        RelayFrame frame = new RelayFrame(); frame.managed = true; ((RelaySourceState) rejected).repeaters$setFrame(frame);
        RadioSource accepted = rejected.copy(); accepted.transmissionPower = 100;
        TowerManager manager = TowerManager.get(level.getServer()); long deliveries = manager.receiverDeliveries;
        receiver.accept(rejected);
        check(terminal.count == 0 && frame.receivers.isEmpty() && manager.receiverDeliveries == deliveries && checks[0] == 1,
                "A rejected candidate must not mark, count or deliver the frame");
        receiver.accept(accepted);
        check(terminal.count == 1 && frame.receivers.size() == 1 && manager.receiverDeliveries == deliveries + 1 && checks[0] == 2,
                "A later accepted copy must deliver once, with one predicate evaluation per attempt");
        receiver.accept(accepted.copy());
        check(terminal.count == 1 && manager.receiverDeliveries == deliveries + 1 && checks[0] == 3,
                "An accepted duplicate must still be suppressed before downstream routing"); helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void relay_direct_catalyst_clicks_have_no_inventory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, SimpleRadioRepeaters.RELAY.defaultBlockState(), 3);
        RelayBlockEntity relay = (RelayBlockEntity) level.getBlockEntity(pos); var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var hit = new net.minecraft.world.phys.BlockHitResult(pos.getCenter(), Direction.NORTH, pos, false);
        ItemStack held = new ItemStack(Items.GILDED_BLACKSTONE, 2); player.setItemInHand(hand, held);
        SimpleRadioRepeaters.RELAY.use(relay.getBlockState(), level, pos, player, hand, hit);
        check(held.getCount() == 1 && relay.hasCatalyst(), "Right-click must insert exactly one catalyst");
        SimpleRadioRepeaters.RELAY.use(relay.getBlockState(), level, pos, player, hand, hit);
        check(held.getCount() == 1, "An occupied socket must leave the held catalyst alone");
        player.setItemInHand(hand, ItemStack.EMPTY); player.setShiftKeyDown(true);
        SimpleRadioRepeaters.RELAY.use(relay.getBlockState(), level, pos, player, hand, hit);
        check(!relay.enabled() && relay.hasCatalyst(), "Sneaking must toggle power without ejecting the catalyst");
        player.setShiftKeyDown(false);
        SimpleRadioRepeaters.RELAY.use(relay.getBlockState(), level, pos, player, hand, hit);
        check(!relay.hasCatalyst() && player.getInventory().contains(new ItemStack(Items.GILDED_BLACKSTONE)), "An empty-hand click must return the catalyst");
        check(!relay.saveWithoutMetadata().contains("Items") && !((Object) relay instanceof net.minecraft.world.Container),
                "Catalyst interaction must not require a Relay inventory"); helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void bank_edits_while_relay_unloaded_override_old_relay_nbt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity relay = configuredRelay(level, core, 126);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); ChannelBankBlockEntity bank = relay.findAssignedBank();
            CompoundTag oldRelay = relay.saveWithoutMetadata();
            level.getChunkAt(relay.getBlockPos()).removeBlockEntity(relay.getBlockPos());
            bank.removeItem(0, 1);
            check(!manager.isEligible(relay.relayId()) && manager.snapshot(relay.relayId()).channels().isEmpty(),
                    "Editing a loaded Bank must immediately update its unloaded Relay's saved channels");
            CompoundTag emptyBank = bank.saveWithoutMetadata();
            level.getChunkAt(bank.getBlockPos()).removeBlockEntity(bank.getBlockPos());
            RelayBlockEntity restored = new RelayBlockEntity(relay.getBlockPos(), relay.getBlockState()); restored.load(oldRelay); level.setBlockEntity(restored);
            helper.runAfterDelay(5, () -> {
                check(restored.channels().isEmpty() && !manager.isEligible(restored.relayId()),
                        "Old Relay NBT must not restore removed cards while the Bank's entity is still loading");
                ChannelBankBlockEntity restoredBank = new ChannelBankBlockEntity(bank.getBlockPos(), bank.getBlockState());
                restoredBank.load(emptyBank); level.setBlockEntity(restoredBank);
                helper.runAfterDelay(5, () -> {
                    check(restored.channels().isEmpty() && !manager.isEligible(restored.relayId()), "Restoring the empty Bank must keep the channel inactive");
                    level.setBlock(relay.getBlockPos(), Blocks.AIR.defaultBlockState(), 3); helper.succeed();
                });
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void saved_chain_routes_without_loading_tower_chunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); TowerManager manager = TowerManager.get(level.getServer());
        BlockPos sourcePos = helper.absolutePos(new BlockPos(2, 2, 2));
        var api = ServerSimpleRadioApi.getInstance();
        String number = api.frequencies().incrementFrequency(api.frequencies().defaultFrequency(), 73);
        Frequency channel = api.frequencies().getOrCreate(number, Frequency.Modulation.AMPLITUDE);
        ChannelKey key = ChannelKey.from(channel);
        List<BlockPos> towerPositions = List.of(sourcePos.offset(1000, 0, 0), sourcePos.offset(4500, 0, 0), sourcePos.offset(8000, 0, 0));
        List<UUID> ids = new ArrayList<>();
        for (BlockPos pos : towerPositions) {
            check(!level.hasChunkAt(pos), "Fixture tower chunk must start unloaded");
            UUID id = UUID.randomUUID(); ids.add(id);
            manager.updateSnapshot(new TowerRecord(id, level.dimension().location().toString(), pos, Set.of(key), true, RelayCatalysts.GILDED, true, 2, Set.of(pos.asLong())));
        }
        BlockPos receiverPos = sourcePos.offset(9000, 0, 0);
        // Only the listener's chunk is explicitly loaded, representing a player at the far end.
        level.getChunkAt(receiverPos);
        FrequencingType senderType = type("test_sender", 1900, 0);
        FrequencingType listenerType = type("test_listener", 0, 0);
        RadioTransmitter transmitter = new RadioTransmitter(channel, WorldlyPosition.of(sourcePos, level)); transmitter.frequencingType(senderType);
        transmitter.identifier = (short) 23000;
        RadioReceiver receiver = new RadioReceiver(channel, WorldlyPosition.of(receiverPos, level)); receiver.frequencingType(listenerType);
        receiver.identifier = (short) 24000;
        CountingRouter terminal = new CountingRouter(receiver.position); terminal.identifier = (short) 24001; receiver.addRouter(terminal);
        channel.getReceivers().add(receiver);
        try {
            UUID owner = UUID.randomUUID(), originalOwner = UUID.randomUUID(); byte[] bytes = new byte[]{1, 2, 3};
            RadioSource source = new RadioSource(owner, WorldlyPosition.of(sourcePos, level), bytes, 1);
            source.originalOwner = originalOwner; source.activity = 42;
            transmitter.accept(source);
            check(terminal.count == 1, "The far-end receiver must get exactly one packet through the chain");
            check(terminal.last.owner.equals(owner) && terminal.last.originalOwner.equals(originalOwner), "Relay must preserve both stream identities");
            check(terminal.last.data == bytes && terminal.last.activity == 42, "Encoded voice and activity must be preserved");
            check(((RelaySourceState) terminal.last).repeaters$getFrame() != null, "Source-copy mixin must carry relay metadata");
            transmitter.accept(source); check(terminal.count == 1, "Re-entering the same frame must not replay it");
            RadioSource duplicate = terminal.last.copy(); duplicate.record.clear(); receiver.accept(duplicate);
            check(terminal.count == 1, "Receiver mixin must reject duplicates before native short-circuit or decoding behavior");
            for (BlockPos pos : towerPositions) check(!level.hasChunkAt(pos), "Voice routing must not load a tower chunk");
        } finally {
            channel.getReceivers().remove(receiver);
            for (BlockPos pos : towerPositions) manager.removeAt(level.dimension().location().toString(), pos);
        }
        helper.succeed();
    }

    private static FrequencingType type(String name, int power, int reception) {
        FrequencingType type = new FrequencingType(); type.transmissionPowerAM = power; type.transmissionPowerFM = power;
        type.receptionPower = reception; type.receptionFloor = 0; type.antennaAptitude = 0;
        type.diminishThresholdAM = 1; type.diminishThresholdFM = 1; type.transmissionDiminishment = 1;
        type.diminishmentMethod = FrequencingType.DiminishmentMethod.ADDITIVE;
        return FrequencingRegistry.register(SimpleRadioRepeaters.id(name), type);
    }
    private static final class CountingRouter extends RadioRouter {
        int count; RadioSource last;
        CountingRouter(WorldlyPosition location) { super(location); }
        @Override public void take(Source source) { count++; last = (RadioSource) source; }
    }
}
