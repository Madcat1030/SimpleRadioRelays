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
import dev.paul.simpleradiorepeaters.radio.RelaySourceState;
import dev.paul.simpleradiorepeaters.screen.RepeaterBankMenu;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Runs only in Fabric's dedicated game-test environment; it does not capture microphones. */
public class RepeaterGameTests implements FabricGameTest {
    private static void check(boolean value, String description) { if (!value) throw new IllegalStateException(description); }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 150)
    public void inventory_noops_do_not_restart_antenna_verification(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RepeaterBankBlockEntity bank = configuredBank(level, core, 96);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer());
            var player = helper.makeMockPlayer(); player.setPos(bank.getBlockPos().getCenter());
            RepeaterBankMenu menu = (RepeaterBankMenu) bank.createMenu(77, player.getInventory(), player);
            check(menu.status() == 0 && manager.isEligible(bank.bankId()), "Fixture must have finished antenna verification");
            TowerRecord snapshot = manager.snapshot(bank.bankId());
            bank.setChanged(); bank.setItem(0, bank.getItem(0).copy()); bank.setEnabled(true);
            menu.clicked(1, 0, ClickType.PICKUP, player); // Empty channel slot.
            menu.setCarried(new ItemStack(Items.DIRT)); menu.clicked(1, 0, ClickType.PICKUP, player);
            check(menu.getCarried().is(Items.DIRT) && bank.getItem(1).isEmpty(), "Wrong items must stay outside channel slots");
            RepeaterBankBlockEntity.tick(level, bank.getBlockPos(), bank.getBlockState(), bank);
            check(menu.status() == 0 && manager.snapshot(bank.bankId()).equals(snapshot),
                    "Rejected items, empty clicks and equivalent writes must not start a relay check");
            bank.removeItem(9, 1);
            check(menu.status() == 2 && !manager.isEligible(bank.bankId()), "Catalyst removal must immediately stop relaying");
            bank.setItem(9, new ItemStack(Items.GILDED_BLACKSTONE));
            check(menu.status() == 6, "Adding the catalyst must start verification while retaining the antenna cache");
            helper.runAfterDelay(30, () -> {
                check(menu.status() == 0, "Actual catalyst insertion must complete verification");
                bank.setEnabled(false);
                check(menu.status() == 4 && !manager.isEligible(bank.bankId()), "Disabling must immediately update status and eligibility");
                bank.setEnabled(true);
                check(menu.status() == 6 && manager.isEligible(bank.bankId()), "Turning on must trigger one verification");
                helper.runAfterDelay(30, () -> {
                    check(menu.status() == 0, "Enable verification must finish");
                    ItemStack card = bank.removeItem(0, 1);
                    check(menu.status() == 3 && !manager.isEligible(bank.bankId()), "Removing the last card must immediately remove its channel");
                    bank.setItem(0, card);
                    check(menu.status() == 6 && manager.isEligible(bank.bankId()), "Adding a card must request verification");
                    helper.runAfterDelay(30, () -> {
                        check(menu.status() == 0, "Card verification must finish");
                        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
                        String newNumber = frequencies.incrementFrequency(frequencies.defaultFrequency(), 97);
                        bank.getItem(0).getOrCreateTag().putString("frequency", newNumber); bank.setChanged();
                        check(menu.status() == 6 && manager.snapshot(bank.bankId()).channels().contains(new ChannelKey(newNumber, "AM")),
                                "An in-place change to card tuning must still publish and verify its configuration");
                        level.setBlock(bank.getBlockPos(), Blocks.AIR.defaultBlockState(), 3); helper.succeed();
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
    public void bank_scans_native_antenna_and_saves_inventory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos bankPos = core.north();
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        level.setBlock(bankPos, SimpleRadioRepeaters.BANK.defaultBlockState(), 3);
        RepeaterBankBlockEntity bank = (RepeaterBankBlockEntity) level.getBlockEntity(bankPos);
        ItemStack card = new ItemStack(SimpleRadioRepeaters.CARD);
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        SimpleRadioRepeaters.CARD.setFrequency(card, frequencies.incrementFrequency(frequencies.defaultFrequency(), 98), Frequency.Modulation.AMPLITUDE);
        bank.setItem(0, card); bank.setItem(9, new ItemStack(Items.GILDED_BLACKSTONE));
        helper.runAfterDelay(30, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); bank.refresh(manager);
            check(bank.getAntennaPower() > 0, "Antenna scan must produce native antenna power");
            check(manager.isEligible(bank.bankId()), "Configured bank must be eligible");
            CompoundTag saved = bank.saveWithoutMetadata();
            RepeaterBankBlockEntity restored = new RepeaterBankBlockEntity(bankPos, bank.getBlockState()); restored.load(saved);
            check(restored.bankId().equals(bank.bankId()), "Bank identity must survive save/reload");
            check(restored.getItem(0).is(SimpleRadioRepeaters.CARD) && restored.getItem(9).is(Items.GILDED_BLACKSTONE), "Cards and catalyst must survive save/reload");
            bank.setEnabled(false); check(!manager.isEligible(bank.bankId()), "Disable must immediately remove the tower from routing");
            level.setBlock(bankPos, Blocks.AIR.defaultBlockState(), 3);
            check(manager.records().stream().noneMatch(n -> n.id().equals(bank.bankId())), "Destroying a bank must remove the saved node"); helper.succeed();
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
        RepeaterBankBlockEntity bank = configuredBank(level, core, 90);
        helper.runAfterDelay(10, () -> {
            InsulatorBlockEntity near = (InsulatorBlockEntity) level.getBlockEntity(nearPos);
            InsulatorBlockEntity far = (InsulatorBlockEntity) level.getBlockEntity(farPos);
            check(near.getRouter() != null && far.getRouter() != null, "Native insulator routers must initialize");
            Wire wire = Wire.connect(near, far, level);
            check(wire != null, "Fixture must use a real native copper wire");
            bank.markDirty();
            helper.runAfterDelay(30, () -> {
                TowerManager manager = TowerManager.get(level.getServer());
                check(bank.getAntennaPower() > 0 && manager.isEligible(bank.bankId()), "Wired antenna must first be verified");
                int score = bank.getAntennaPower();
                CompoundTag tag = bank.saveWithoutMetadata();
                tag.putInt("AntennaScore", 0); // A block-entity cache can lag the authoritative saved network record.
                near.getRouter().wires.clear(); far.getRouter().wires.clear();
                AntennaScanner.Result early = AntennaScanner.scan(level, bank.getBlockPos(), bank.corePos());
                check(early.complete() && early.score() == 0, "Reproduce native lookup's transient zero before the wire reattaches");
                bank.setRemoved();
                RepeaterBankBlockEntity restored = new RepeaterBankBlockEntity(bank.getBlockPos(), bank.getBlockState());
                restored.load(tag); level.setBlockEntity(restored);
                RepeaterBankBlockEntity.tick(level, restored.getBlockPos(), restored.getBlockState(), restored);
                check(restored.getAntennaPower() == score, "Reload must recover the authoritative cached score");
                check(manager.isEligible(restored.bankId()), "The first reload tick must retain relay eligibility");
                helper.runAfterDelay(30, () -> {
                    check(near.hasWire(wire) && far.hasWire(wire), "Native wire tick must reconnect both endpoints");
                    check(restored.getAntennaPower() == score && manager.isEligible(restored.bankId()), "Bank must recover without an enable toggle");
                    check(restored.saveWithoutMetadata().getInt("AntennaScore") == score, "Refreshed score must be saved in the block entity");
                    wire.burnOut(); restored.markDirty(); // Explicit refresh; do not patch upstream cut-wire notifications.
                    helper.runAfterDelay(30, () -> {
                        check(manager.isEligible(restored.bankId()), "One settled empty scan must not discard the cache");
                    });
                    helper.runAfterDelay(250, () -> {
                        check(!manager.isEligible(restored.bankId()), "A genuinely removed wire must eventually stop relaying");
                        check(manager.snapshot(restored.bankId()).antennaScore() == 0, "Confirmed disconnection must publish a zero score");
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
        RepeaterBankBlockEntity bank = configuredBank(level, core, 92);
        helper.runAfterDelay(40, () -> {
            TowerManager manager = TowerManager.get(level.getServer());
            TowerRecord verified = manager.snapshot(bank.bankId());
            check(verified != null && verified.eligible(), "Fixture antenna must have a verified record");
            BlockPos unavailable = core.offset(20000, 0, 0);
            check(!level.hasChunkAt(unavailable), "The historical antenna dependency must be unloaded");
            Set<Long> footprint = new HashSet<>(verified.footprint()); footprint.add(unavailable.asLong());
            TowerRecord snapshot = new TowerRecord(verified.id(), verified.dimension(), verified.position(), verified.channels(),
                    verified.enabled(), verified.catalyst(), verified.verified(), verified.antennaScore(), footprint);
            manager.updateSnapshot(snapshot); bank.markDirty();
            level.setBlock(core.east(), Blocks.STONE.defaultBlockState(), 3);
            check(manager.isEligible(bank.bankId()), "An unrelated neighbouring block change must not invalidate the snapshot");
            helper.runAfterDelay(30, () -> {
                check(manager.snapshot(bank.bankId()).equals(snapshot), "Partial loading must preserve the entire saved snapshot");
                check(manager.isEligible(bank.bankId()), "The saved antenna must continue relaying while verification waits");
                check(!level.hasChunkAt(unavailable), "Verification must not load the unavailable chunk");
                level.setBlock(bank.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
                helper.succeed();
            });
        });
    }

    private static RepeaterBankBlockEntity configuredBank(ServerLevel level, BlockPos core, int channelOffset) {
        BlockPos pos = core.north(); level.setBlock(pos, SimpleRadioRepeaters.BANK.defaultBlockState(), 3);
        RepeaterBankBlockEntity bank = (RepeaterBankBlockEntity) level.getBlockEntity(pos);
        ItemStack card = new ItemStack(SimpleRadioRepeaters.CARD);
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        SimpleRadioRepeaters.CARD.setFrequency(card, frequencies.incrementFrequency(frequencies.defaultFrequency(), channelOffset), Frequency.Modulation.AMPLITUDE);
        bank.setItem(0, card); bank.setItem(9, new ItemStack(Items.GILDED_BLACKSTONE));
        return bank;
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void saved_record_round_trip(GameTestHelper helper) {
        TowerRecord record = new TowerRecord(UUID.randomUUID(), helper.getLevel().dimension().location().toString(),
                new BlockPos(9000, 80, 9000), Set.of(new ChannelKey("123.4", "AM"), new ChannelKey("123.4", "FM")),
                true, true, true, 12, Set.of(new BlockPos(9000, 80, 9000).asLong()));
        check(record.equals(TowerRecord.load(record.save())), "Saved tower metadata must round-trip without merging AM/FM");
        TowerSavedData data = new TowerSavedData(); data.towers.put(record.id(), record);
        TowerSavedData restored = TowerSavedData.load(data.save(new CompoundTag()));
        check(restored.towers.get(record.id()).equals(record), "Whole network store must restore unchanged"); helper.succeed();
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
            manager.updateSnapshot(new TowerRecord(id, level.dimension().location().toString(), pos, Set.of(key), true, true, true, 2, Set.of(pos.asLong())));
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
