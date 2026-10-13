package dev.paul.simpleradiorepeaters.audit;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.SimpleRadioItems;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.core.registry.menus.RadiosmitherMenu;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.RelaySourceState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Isolated audit overlay, not included in the distributed addon. No client/audio playback is simulated. */
public final class HandheldAuditGameTests implements FabricGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void handheld_frames_survive_collection_owner_transfer_and_reload(GameTestHelper helper) {
        int offset = 310;
        for (TransceiverItem item : List.of(SimpleRadioItems.WALKIE_TALKIE, SimpleRadioItems.TRANSCEIVER)) {
            try (Fixture f = new Fixture(helper, offset++)) {
                Handset left = f.handset(item, f.origin), right = f.handset(item, f.origin.east(9000));
                exercise(left, right, 100);
                RadioManager.garbageCollect();
                check(left.receiver.validate() && right.receiver.validate(), "Valid inventory radios must survive native collection");
                exercise(left, right, 100);

                Player nextHolder = f.player(f.origin);
                left.player.getInventory().setItem(0, ItemStack.EMPTY);
                nextHolder.getInventory().setItem(0, left.stack);
                item.entityTick(left.stack, nextHolder);
                left = f.inspect(item, left.stack, nextHolder);
                check(left.receiver.owner == nextHolder && left.transmitter.owner == nextHolder,
                        "Ownership transfer must update both RF routers");
                exercise(left, right, 100);

                CompoundTag serialized = left.stack.save(new CompoundTag());
                UUID playerId = left.player.getUUID(), radioId = left.stack.getTag().getUUID("reference");
                left.player.getInventory().setItem(0, ItemStack.EMPTY);
                left.player.discard();
                RadioManager.garbageCollect();
                Player rejoined = f.player(f.origin); rejoined.setUUID(playerId);
                ItemStack restored = ItemStack.of(serialized);
                rejoined.getInventory().setItem(0, restored);
                item.entityTick(restored, rejoined);
                left = f.inspect(item, restored, rejoined);
                check(restored.getTag().getUUID("reference").equals(radioId), "Saving must retain the radio identity");
                check(left.receiver.owner == rejoined, "Inventory activation after reconnect must use the new owner instance");
                exercise(left, right, 100);
                for (BlockPos pos : f.towers) check(!f.level.hasChunkAt(pos), "Audit routing must not load tower chunks");
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void tuning_one_handheld_does_not_interrupt_another_stream(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 320)) {
            Handset sender = f.handset(SimpleRadioItems.TRANSCEIVER, f.origin);
            Handset peer = f.handset(SimpleRadioItems.WALKIE_TALKIE, f.origin.east(9000));
            Handset other = f.handset(SimpleRadioItems.WALKIE_TALKIE, f.origin.east(9002));
            sender.item.begin(sender.stack, sender.player);
            byte[] packet = new byte[]{4, 5, 6};
            UUID talker = UUID.randomUUID();
            int delivered = other.capture.count;
            for (int round = 0; round < 20; round++) {
                send(sender, talker, packet);
                check(other.capture.count == ++delivered, "An independent radio must receive each fresh packet before tuning");
                peer.player.getInventory().setItem(0, ItemStack.EMPTY);
                SimpleContainer bench = new SimpleContainer(1); bench.setItem(0, peer.stack);
                // A normal native garbage-collection pass while the item is in the bench.
                RadioManager.garbageCollect();
                RadiosmitherMenu menu = new RadiosmitherMenu(99, peer.player.getInventory(), bench);
                menu.updateTinkering(f.channel.getFrequency(), Frequency.Modulation.AMPLITUDE);
                peer.player.getInventory().setItem(0, bench.removeItemNoUpdate(0));
                peer.item.entityTick(peer.stack, peer.player);
                peer = f.inspect(peer.item, peer.stack, peer.player);
                int received = peer.capture.count;
                send(sender, talker, packet);
                check(peer.capture.count == received + 1, "A reactivated tuned radio must receive immediately");
                check(other.capture.count == ++delivered, "Tuning another radio must not change unrelated delivery");
            }
            sender.item.end(sender.stack, sender.player);
            check(other.receiver.active && peer.receiver.active, "Reception must remain active after repeated tuning");
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void channel_conversion_returns_to_wired_radio_and_handheld_receivers(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 340)) {
            var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
            Frequency back = frequencies.getOrCreate(frequencies.incrementFrequency(f.channel.getFrequency(), 1),
                    Frequency.Modulation.AMPLITUDE);
            for (TowerRecord tower : f.manager.records()) {
                if (!f.towers.contains(tower.position())) continue;
                f.manager.updateSnapshot(new TowerRecord(tower.id(), tower.dimension(), tower.position(),
                        Set.of(ChannelKey.from(f.channel), ChannelKey.from(back)), tower.enabled(), tower.catalyst(),
                        tower.verified(), tower.antennaScore(), tower.footprint()));
            }
            BlockPos far = f.origin.east(9000); f.level.getChunkAt(far);
            RadioTransmitter outbound = new RadioTransmitter(f.channel, WorldlyPosition.of(f.origin, f.level));
            outbound.frequencingType(SimpleRadioFrequencing.TRANSMITTER); outbound.antennaPower = 19; outbound.identifier = 29000;
            RadioReceiver converter = new RadioReceiver(f.channel, WorldlyPosition.of(far, f.level));
            converter.frequencingType(SimpleRadioFrequencing.RECEIVER); converter.antennaPower = 114; converter.identifier = 29001;
            RadioTransmitter returning = new RadioTransmitter(back, WorldlyPosition.of(far, f.level));
            returning.frequencingType(SimpleRadioFrequencing.TRANSMITTER); returning.antennaPower = 114; returning.identifier = 29002;
            RadioRouter wire = new RadioRouter(WorldlyPosition.of(far, f.level)) {
                @Override public void take(Source packet) {
                    RadioSource source = (RadioSource) packet;
                    // Native copper wire's cap-relative loss, without unrelated wire-entity lifecycle in this routing test.
                    source.transmissionPower -= source.transmissionCap * SimpleRadioFrequencing.WIRE.transmissionDiminishment;
                    returning.accept(source);
                }
            };
            wire.identifier = 29003; converter.addRouter(wire); f.channel.getReceivers().add(converter);
            RadioReceiver wired = new RadioReceiver(back, WorldlyPosition.of(f.origin, f.level));
            wired.frequencingType(SimpleRadioFrequencing.RECEIVER); wired.antennaPower = 19; wired.identifier = 29004;
            RadioReceiver radio = new RadioReceiver(back, WorldlyPosition.of(f.origin.east(), f.level));
            radio.frequencingType(SimpleRadioFrequencing.RADIO); radio.identifier = 29005;
            Capture wiredOutput = new Capture(wired.position); wiredOutput.identifier = 29006; wired.addRouter(wiredOutput);
            Capture radioOutput = new Capture(radio.position); radioOutput.identifier = 29007; radio.addRouter(radioOutput);
            back.getReceivers().add(wired); back.getReceivers().add(radio);
            Handset handset = f.handset(SimpleRadioItems.WALKIE_TALKIE, f.origin, back);
            try {
                UUID talker = UUID.randomUUID(); byte[] bytes = new byte[]{8, 9, 10};
                for (int packet = 0; packet < 100; packet++) {
                    outbound.accept(new RadioSource(talker, outbound.getLocation(), bytes, 1));
                    check(wiredOutput.count == packet + 1 && radioOutput.count == packet + 1 && handset.capture.count == packet + 1,
                            "Each return packet must independently reach wired, bare radio, and handheld receivers exactly once");
                    check(handset.capture.last.data == bytes && handset.capture.last.owner.equals(talker),
                            "Changing channels must preserve the original encoded stream");
                }
            } finally {
                f.channel.getReceivers().remove(converter); back.getReceivers().remove(wired); back.getReceivers().remove(radio);
            }
        }
        helper.succeed();
    }

    private static void exercise(Handset left, Handset right, int packets) {
        int rightCount = right.capture.count, leftCount = left.capture.count;
        byte[] data = new byte[]{1, 2, 3};
        UUID talker = UUID.randomUUID();
        left.item.begin(left.stack, left.player);
        check(left.transmitter.active, "PTT must enable the native transmitter");
        if (left.item == SimpleRadioItems.WALKIE_TALKIE) check(!left.receiver.active, "Walkie PTT must retain native half duplex");
        for (int n = 0; n < packets; n++) {
            send(left, talker, data);
            check(right.capture.count == rightCount + n + 1, "A fresh packet must reach the far handheld exactly once");
            check(right.capture.last.data == data && right.capture.last.originalOwner.equals(talker), "Relay must preserve encoded data and original stream identity");
            check(((RelaySourceState) right.capture.last).repeaters$getFrame().managed, "Delivery must actually use the repeater path");
        }
        left.item.end(left.stack, left.player);
        check(!left.transmitter.active && left.receiver.active, "Releasing PTT must restore reception");
        right.item.begin(right.stack, right.player);
        for (int n = 0; n < packets; n++) {
            send(right, talker, data);
            check(left.capture.count == leftCount + n + 1, "The reverse direction must receive each packet exactly once");
        }
        right.item.end(right.stack, right.player);
    }

    private static void send(Handset handset, UUID talker, byte[] data) {
        RadioSource source = new RadioSource(talker, handset.listener.getLocation(), data, 1);
        source.activity = 42;
        handset.listener.listen(source);
    }

    private record Handset(TransceiverItem item, ItemStack stack, Player player, RadioListener listener,
                           RadioReceiver receiver, RadioTransmitter transmitter, Capture capture) {}

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos origin;
        final Frequency channel;
        final TowerManager manager;
        final List<BlockPos> towers;
        final List<UUID> radioIds = new ArrayList<>();
        final List<Player> players = new ArrayList<>();

        Fixture(GameTestHelper helper, int offset) {
            this.helper = helper; level = helper.getLevel(); origin = helper.absolutePos(new BlockPos(2, 2, 2));
            var api = ServerSimpleRadioApi.getInstance();
            String number = api.frequencies().incrementFrequency(api.frequencies().defaultFrequency(), offset);
            channel = api.frequencies().getOrCreate(number, Frequency.Modulation.AMPLITUDE);
            manager = TowerManager.get(level.getServer());
            towers = List.of(origin.east(1000), origin.east(4500), origin.east(8000));
            for (BlockPos pos : towers) {
                check(!level.hasChunkAt(pos), "Audit tower must start unloaded");
                manager.updateSnapshot(new TowerRecord(UUID.randomUUID(), level.dimension().location().toString(), pos,
                        Set.of(ChannelKey.from(channel)), true, true, true, 114, Set.of(pos.asLong())));
            }
        }
        Player player(BlockPos pos) {
            Player player = helper.makeMockPlayer(); player.setUUID(UUID.randomUUID());
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            players.add(player); return player;
        }
        Handset handset(TransceiverItem item, BlockPos pos) {
            return handset(item, pos, channel);
        }
        Handset handset(TransceiverItem item, BlockPos pos, Frequency tunedChannel) {
            Player player = player(pos); ItemStack stack = new ItemStack(item);
            item.setFrequency(stack, tunedChannel.getFrequency(), tunedChannel.getModulation());
            player.getInventory().setItem(0, stack);
            item.entityTick(stack, player);
            radioIds.add(stack.getTag().getUUID("reference"));
            return inspect(item, stack, player);
        }
        Handset inspect(TransceiverItem item, ItemStack stack, Player player) {
            UUID id = stack.getTag().getUUID("reference");
            Frequency current = ServerSimpleRadioApi.getInstance().frequencies().fromTag(stack.getTag());
            RadioReceiver receiver = (RadioReceiver) current.getReceiver(id);
            RadioTransmitter transmitter = (RadioTransmitter) current.getTransmitter(id);
            RadioListener listener = (RadioListener) ServerSimpleRadioApi.getInstance().listeners().get(id);
            check(receiver != null && transmitter != null && listener != null, "Native activation must register the full RF/listening set");
            check(receiver.active && receiver.valid && transmitter.valid, "Native receiver/transmitter must be active and valid");
            // Observe the native receiver's output without creating audio clients or decoding synthetic bytes.
            receiver.routers.clear();
            Capture capture = new Capture(receiver.getLocation());
            capture.identifier = (short) (30000 + radioIds.indexOf(id)); receiver.addRouter(capture);
            return new Handset(item, stack, player, listener, receiver, transmitter, capture);
        }
        @Override public void close() {
            for (Player player : players) { player.getInventory().clearContent(); player.discard(); }
            for (UUID id : radioIds) {
                SimpleRadioItems.TRANSCEIVER.inactivate(level, channel.getFrequency(), channel.getModulation(), id);
            }
            for (BlockPos pos : towers) manager.removeAt(level.dimension().location().toString(), pos);
            RadioManager.garbageCollect();
        }
    }

    private static final class Capture extends RadioRouter {
        int count; RadioSource last;
        Capture(WorldlyPosition position) { super(position); }
        @Override public void take(Source source) { count++; last = (RadioSource) source; }
    }
}
