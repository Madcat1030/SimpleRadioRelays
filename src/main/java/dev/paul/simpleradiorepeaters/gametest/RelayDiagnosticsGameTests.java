package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.*;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.*;
import com.codinglitch.simpleradio.core.registry.blocks.*;
import com.codinglitch.simpleradio.core.registry.entities.Wire;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.*;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Reproduces a real RX -> copper wire -> TX channel bridge and antenna maintenance. */
public class RelayDiagnosticsGameTests implements FabricGameTest {
    private static void check(boolean result, String message) { if (!result) throw new IllegalStateException(message); }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void native_channel_bridge_uses_local_relay_and_keeps_working_during_reload(GameTestHelper helper) {
        bridge(helper, false, 201);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void equal_audio_quality_must_not_strand_the_wired_bridge_with_a_weak_carrier(GameTestHelper helper) {
        bridge(helper, true, 211);
    }

    private static void bridge(GameTestHelper helper, boolean edge, int offset) {
        ServerLevel level = helper.getLevel(); TowerManager manager = TowerManager.get(level.getServer());
        ServerSimpleRadioApi api = ServerSimpleRadioApi.getInstance();
        var frequencies = api.frequencies();
        Frequency incoming = frequencies.getOrCreate(frequencies.incrementFrequency(frequencies.defaultFrequency(), offset), Frequency.Modulation.AMPLITUDE);
        Frequency returning = frequencies.getOrCreate(frequencies.incrementFrequency(frequencies.defaultFrequency(), offset + 1), Frequency.Modulation.AMPLITUDE);
        Set<ChannelKey> channels = Set.of(ChannelKey.from(incoming), ChannelKey.from(returning));
        BlockPos coreA = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(coreA, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(coreA.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity a = relay(level, coreA, incoming, returning);
        BlockPos bPos = coreA.offset(4000, 0, 0); UUID bId = UUID.randomUUID();
        TowerRecord b = new TowerRecord(bId, level.dimension().location().toString(), bPos, channels, true, true, true, 114, Set.of(bPos.asLong()));
        int dx = 4000, dz = 0;
        if (edge) {
            int smallScore = AntennaScanner.scan(level, a.getBlockPos(), coreA).score();
            double limit = NativeRadioAdapter.towerPower(b, ChannelKey.from(incoming))
                    + SimpleRadioFrequencing.RECEIVER.receptionPower + smallScore * SimpleRadioFrequencing.RECEIVER.antennaAptitude;
            dx = (int) Math.floor(limit) - 1;
            for (int z = 1; z < 500; z++) {
                double margin = limit - Math.hypot(dx, z);
                if (margin > .03 && margin < .3) { dz = z; break; }
            }
            check(dz > 0, "Fixture must have a positive but tiny B-to-native-RX carrier margin");
        }
        BlockPos coreC = bPos.offset(dx - 1, 0, dz);
        List<ChunkPos> tickets = new ArrayList<>();
        for (int x = (coreC.getX() >> 4) - 1; x <= (coreC.getX() >> 4) + 1; x++)
            for (int z = (coreC.getZ() >> 4) - 1; z <= (coreC.getZ() >> 4) + 1; z++) {
                ChunkPos chunk = new ChunkPos(x, z); tickets.add(chunk); level.setChunkForced(x, z, true); level.getChunk(x, z);
            }
        level.setBlock(coreC, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(coreC.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity c = relay(level, coreC, incoming, returning);
        level.setBlock(coreC.east(), SimpleRadioBlocks.RECEIVER.defaultBlockState().setValue(ReceiverBlock.FACING, Direction.EAST), 3);
        level.setBlock(coreC.west(), SimpleRadioBlocks.TRANSMITTER.defaultBlockState().setValue(TransmitterBlock.FACING, Direction.WEST), 3);
        ReceiverBlockEntity receiverBlock = (ReceiverBlockEntity) level.getBlockEntity(coreC.east());
        TransmitterBlockEntity transmitterBlock = (TransmitterBlockEntity) level.getBlockEntity(coreC.west());
        receiverBlock.frequency = incoming; receiverBlock.catalyst = CatalystRegistry.get(Items.GILDED_BLACKSTONE);
        transmitterBlock.frequency = returning; transmitterBlock.catalyst = CatalystRegistry.get(Items.GILDED_BLACKSTONE);
        var player = helper.makeMockPlayer(); player.setPos(coreA.getCenter().add(-1000, 0, 0));
        RadioTransmitter sender = new RadioTransmitter(incoming, player).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
        api.registerRouter(sender, incoming);
        RadioReceiver listener = new RadioReceiver(returning, player).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
        ItemStack speakingRadio = new ItemStack(SimpleRadioItems.TRANSCEIVER), listeningRadio = new ItemStack(SimpleRadioItems.RADIO);
        SimpleRadioItems.TRANSCEIVER.setFrequency(speakingRadio, incoming.getFrequency(), incoming.getModulation());
        SimpleRadioItems.RADIO.setFrequency(listeningRadio, returning.getFrequency(), returning.getModulation());
        speakingRadio.getOrCreateTag().putUUID("reference", sender.reference); listeningRadio.getOrCreateTag().putUUID("reference", listener.reference);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, speakingRadio);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, listeningRadio);
        Capture terminal = new Capture(WorldlyPosition.of(coreA, level)); terminal.identifier = (short)(31000 + offset); listener.addRouter(terminal);
        Runnable cleanup = () -> {
            manager.removeAt(b.dimension(), b.position());
            level.setBlock(a.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(c.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(coreC.east(), Blocks.AIR.defaultBlockState(), 3); level.setBlock(coreC.west(), Blocks.AIR.defaultBlockState(), 3);
            for (RadioRouter router : List.of(sender, listener, terminal)) if (api.getRouter(router.reference) != null) api.removeRouter(router);
            returning.getReceivers().remove(listener);
            for (ChunkPos chunk : tickets) level.setChunkForced(chunk.x, chunk.z, false);
        };
        helper.runAfterDelay(40, () -> {
            try {
                check(manager.isEligible(a.relayId()) && manager.isEligible(c.relayId()), "Both physical Relays must finish verification");
                RadioReceiver rx = (RadioReceiver) receiverBlock.getRouter(); RadioTransmitter tx = (RadioTransmitter) transmitterBlock.getRouter();
                check(rx != null && tx != null, "Native RX/TX routers must activate");
                rx.antennaPower = c.getAntennaPower(); tx.antennaPower = c.getAntennaPower();
                receiverBlock.antennaPower = c.getAntennaPower(); transmitterBlock.antennaPower = c.getAntennaPower();
                receiverBlock.isDirty = false; transmitterBlock.isDirty = false;
                Wire wire = Wire.connect(receiverBlock, transmitterBlock, level); check(wire != null, "Bridge must use an actual native copper wire");
                check(rx.distributes, "Native Receiver must distribute its packet onto the wire");
                double[] receiverPower = {-1}; int[] transmitterFrames = {0};
                var originalReceiverAcceptor = rx.routerAcceptor; var originalTransmitterAcceptor = tx.routerAcceptor;
                rx.routerAcceptor = source -> { receiverPower[0] = source.transmissionPower; return originalReceiverAcceptor != null && originalReceiverAcceptor.apply(source); };
                tx.routerAcceptor = source -> { transmitterFrames[0]++; return originalTransmitterAcceptor != null && originalTransmitterAcceptor.apply(source); };
                int delay = Math.max(0, (int) Math.ceil(wire.getLength() * SimpleRadioLibrary.SERVER_CONFIG.wire.transmissionTime)) + 3;
                manager.updateSnapshot(b);
                Runnable send = () -> {
                    RadioSource source = new RadioSource(player.getUUID(), WorldlyPosition.of(coreA, level), new byte[]{1, 2, 3}, 1);
                    source.originalOwner = player.getUUID(); source.activity = 42;
                    if (edge) ((RelaySourceState) source).repeaters$setInheritedSeverity(99.999);
                    sender.accept(source);
                };
                send.run();
                helper.runAfterDelay(delay, () -> {
                    try {
                        check(receiverPower[0] > 0, "Channel 1 must arrive at C's native receiver");
                        check(terminal.count == 1, "A received packet at C must return through the wire and channel-2 relay network; RX power="
                                + receiverPower[0] + ", TX frames=" + transmitterFrames[0] + ", listener active/valid=" + listener.active + "/" + listener.valid
                                + ", C channels=" + c.channels() + ", wire delay=" + delay + ", counters=" + manager.forwardedFrames + "/" + manager.receiverDeliveries);
                        check(transmitterFrames[0] == 1 && terminal.last.frequencyMedium == returning, "Native bridge must emit exactly once on channel 2");
                        check(terminal.last.activity == 42 && terminal.last.originalOwner.equals(player.getUUID()), "Bridge must preserve stream metadata");
                        if (edge) {
                            check(receiverPower[0] > 1000, "Equal-quality exit selection must retain the local Relay's regenerated carrier");
                            check(terminal.last.computeSeverity() >= 99.999, "Carrier regeneration must preserve the weak audio quality");
                            wire.burnOut(); cleanup.run(); helper.succeed(); return;
                        }
                        ChannelBankBlockEntity bank = c.findAssignedBank(); ItemStack incomingCard = bank.removeItem(0, 1), returnCard = bank.removeItem(1, 1);
                        send.run();
                        helper.runAfterDelay(delay, () -> {
                            try {
                                check(terminal.count == 1, "Without C's Relay, the weak native wire-bridge carrier must not magically reach B");
                                bank.setItem(0, incomingCard); bank.setItem(1, returnCard);
                                CompoundTag saved = c.saveWithoutMetadata(); level.getChunkAt(c.getBlockPos()).removeBlockEntity(c.getBlockPos());
                                RelayBlockEntity restored = new RelayBlockEntity(c.getBlockPos(), c.getBlockState()); restored.load(saved); level.setBlockEntity(restored);
                                RelayBlockEntity.tick(level, restored.getBlockPos(), restored.getBlockState(), restored);
                                check(restored.displayStatus() == 6 && manager.isEligible(restored.relayId()), "Reload CHECK must keep the saved Relay eligible");
                                send.run();
                                helper.runAfterDelay(delay, () -> {
                                    try { check(terminal.count == 2, "Packet return must continue during reload verification"); wire.burnOut(); cleanup.run(); helper.succeed(); }
                                    catch (RuntimeException e) { wire.burnOut(); cleanup.run(); throw e; }
                                });
                            } catch (RuntimeException e) { wire.burnOut(); cleanup.run(); throw e; }
                        });
                    } catch (RuntimeException e) { wire.burnOut(); cleanup.run(); throw e; }
                });
            } catch (RuntimeException e) { cleanup.run(); throw e; }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 340)
    public void missed_mast_notification_is_recovered_on_the_native_antenna_cycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3); level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity relay = relay(level, core, channel(221), null);
        helper.runAfterDelay(40, () -> {
            int before = relay.getAntennaPower(); check(before > 0, "Fixture must have a verified antenna");
            // Reproduce an antenna change for which no core-neighbor dirty callback was received.
            for (int y = 1; y <= 5; y++) {
                BlockPos pos = core.above(y); var chunk = level.getChunkAt(pos);
                var pole = SimpleRadioBlocks.ANTENNA.defaultBlockState().setValue(Antennal.UP, y < 5).setValue(Antennal.DOWN, true).setValue(Antennal.ATTACHED, y == 1);
                chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, pole);
            }
            int expected = relay.calculateAntennaPower(core, level); check(expected > before, "Native antenna score must increase");
            helper.runAfterDelay(220, () -> {
                check(relay.getAntennaPower() == expected, "Relay must recover a missed notification without a manual toggle or refresh");
                check(relay.getUpdateTag().getInt("AntennaScore") == expected, "Display update must contain the current antenna score");
                level.setBlock(relay.getBlockPos(), Blocks.AIR.defaultBlockState(), 3); helper.succeed();
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 130)
    public void repeated_build_notifications_do_not_keep_postponing_the_scan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3); level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        RelayBlockEntity relay = relay(level, core, channel(222), null);
        helper.runAfterDelay(40, () -> {
            int before = relay.getAntennaPower(); level.setBlock(core.above(2), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
            int expected = relay.calculateAntennaPower(core, level); check(expected > before, "Fixture antenna must gain strength");
        });
        // Register the many build callbacks before ticking; adding them during a callback can resize GameTest's iterator.
        for (int tick = 41; tick < 81; tick++) helper.runAfterDelay(tick, relay::markDirty);
        helper.runAfterDelay(70, () -> {
                int expected = relay.calculateAntennaPower(core, level);
                check(relay.getAntennaPower() == expected, "Repeated dirty notifications must not restart the settling timer every tick");
                check(TowerManager.get(level.getServer()).isEligible(relay.relayId()), "Checking must retain routing eligibility");
                level.setBlock(relay.getBlockPos(), Blocks.AIR.defaultBlockState(), 3); helper.succeed();
        });
    }

    private static Frequency channel(int offset) {
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        return frequencies.getOrCreate(frequencies.incrementFrequency(frequencies.defaultFrequency(), offset), Frequency.Modulation.AMPLITUDE);
    }
    private static RelayBlockEntity relay(ServerLevel level, BlockPos core, Frequency first, Frequency second) {
        BlockPos pos = core.north(); level.setBlock(pos, SimpleRadioRepeaters.RELAY.defaultBlockState(), 3);
        level.setBlock(pos.above(), SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState(), 3);
        RelayBlockEntity relay = (RelayBlockEntity) level.getBlockEntity(pos); ChannelBankBlockEntity bank = (ChannelBankBlockEntity) level.getBlockEntity(pos.above());
        for (int slot = 0; slot < (second == null ? 1 : 2); slot++) {
            Frequency frequency = slot == 0 ? first : second; ItemStack card = new ItemStack(SimpleRadioRepeaters.CARD);
            SimpleRadioRepeaters.CARD.setFrequency(card, frequency.getFrequency(), frequency.getModulation()); bank.setItem(slot, card);
        }
        relay.installCatalyst(new ItemStack(Items.GILDED_BLACKSTONE), false); relay.bankChanged(); return relay;
    }
    private static final class Capture extends RadioRouter {
        int count; RadioSource last;
        Capture(WorldlyPosition pos) { super(pos); }
        @Override public void take(Source source) { count++; last = (RadioSource) source; }
    }
}
