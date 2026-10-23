package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.*;
import com.codinglitch.simpleradio.core.registry.blocks.RadioBlockEntity;
import com.codinglitch.simpleradio.core.registry.blocks.ReceiverBlockEntity;
import com.codinglitch.simpleradio.core.registry.blocks.TransmitterBlockEntity;
import com.codinglitch.simpleradio.core.central.FrequencyChannel;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Real placed-radio subscribers, plus the saved 4,000-block out-and-back conversion route. */
public class TransponderRoutingGameTests implements FabricGameTest {
    private static short nextId = 31800;
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    private static Frequency frequency(int offset, Frequency.Modulation modulation) {
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        return frequencies.getOrCreate(frequencies.incrementFrequency(frequencies.defaultFrequency(), offset), modulation);
    }
    private static ItemStack card(Frequency frequency) {
        ItemStack stack = new ItemStack(SimpleRadioRepeaters.CARD);
        SimpleRadioRepeaters.CARD.setFrequency(stack, frequency.getFrequency(), frequency.getModulation());
        return stack;
    }
    private static final class Tap extends RadioRouter {
        final RadioBlockEntity block;
        final RadioReceiver receiver;
        int count;
        RadioSource last;
        Tap(ServerLevel level, BlockPos position, Frequency frequency) {
            super(WorldlyPosition.of(position, level)); identifier = ++nextId;
            level.setBlock(position, SimpleRadioBlocks.RADIO.defaultBlockState(), 3);
            block = (RadioBlockEntity) level.getBlockEntity(position);
            block.frequency = frequency; block.activate();
            receiver = (RadioReceiver) block.receiver;
            // Keep the actual native subscription; capture before decoding deliberately synthetic voice bytes.
            receiver.routers.clear(); receiver.addRouter(this);
        }
        @Override public void take(Source source) { count++; last = (RadioSource) source; }
        void close(ServerLevel level) { level.setBlock(block.getBlockPos(), Blocks.AIR.defaultBlockState(), 3); }
    }
    private static RadioSource send(ServerLevel level, BlockPos position, Frequency frequency) {
        var sender = new RadioTransmitter(frequency, WorldlyPosition.of(position, level))
                .frequencingType(SimpleRadioFrequencing.WALKIE_TALKIE);
        sender.identifier = ++nextId;
        var source = new RadioSource(UUID.randomUUID(), WorldlyPosition.of(position, level), new byte[]{1, 2, 3}, 1);
        source.originalOwner = UUID.randomUUID(); source.activity = 42;
        sender.accept(source); return source;
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void client_receiver_cleanup_preserves_live_server_radios(GameTestHelper helper) {
        verifySeparateRegistryCleanup(helper, false, 621);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void client_transmitter_cleanup_preserves_live_server_radios(GameTestHelper helper) {
        verifySeparateRegistryCleanup(helper, true, 623);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void stale_channel_cleanup_preserves_its_replacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        Frequency previous = frequency(625, Frequency.Modulation.FREQUENCY); ChannelKey key = ChannelKey.from(previous);
        UUID reference = UUID.randomUUID();
        var staleReceiver = new RadioReceiver(previous, WorldlyPosition.of(helper.absolutePos(BlockPos.ZERO), level), reference);
        previous.getReceivers().add(staleReceiver);
        frequencies.remove(previous);
        Frequency current = frequencies.getOrCreate(key.frequency(), key.mode());
        Tap radio = new Tap(level, helper.absolutePos(new BlockPos(2, 2, 2)), current);
        try {
            check(previous.removeReceiver(reference) == staleReceiver, "Old endpoint removal must still complete");
            check(frequencies.get(key.frequency(), key.mode()) == current && current.getReceivers().contains(radio.receiver),
                    "Cleaning an old channel must not remove the current frequency generation");
            send(level, radio.block.getBlockPos().north(2), current);
            check(radio.count == 1, "The replacement channel must retain native direct delivery");
        } finally { radio.close(level); }
        check(frequencies.get(key.frequency(), key.mode()) == null,
                "Removing the last real server endpoint must still clean up its own empty channel");
        helper.succeed();
    }

    private static void verifySeparateRegistryCleanup(GameTestHelper helper, boolean transmitter, int offset) {
        ServerLevel level = helper.getLevel(); TowerManager manager = TowerManager.get(level.getServer());
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        Frequency input = frequency(offset, Frequency.Modulation.AMPLITUDE), output = frequency(offset + 1, Frequency.Modulation.AMPLITUDE);
        ChannelKey in = ChannelKey.from(input), out = ChannelKey.from(output);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2)), receiverPos = origin.south(3);
        TowerRecord relay = new TowerRecord(UUID.randomUUID(), level.dimension().location().toString(), origin,
                Set.of(in, out), true, RelayCatalysts.GILDED, true, 32, Set.of(), null, RelayAttachment.NONE,
                new TransponderChannels(in, out, null, false));
        Tap radio = new Tap(level, origin.east(3), output);
        level.setBlock(receiverPos, SimpleRadioBlocks.RECEIVER.defaultBlockState(), 3);
        var block = (ReceiverBlockEntity) level.getBlockEntity(receiverPos);
        block.frequency = output; block.activate();
        RadioReceiver wiredReceiver = (RadioReceiver) block.receiver;
        int[] count = {0};
        RadioRouter capture = new RadioRouter(WorldlyPosition.of(receiverPos, level)) {
            @Override public void take(Source source) { count[0]++; }
        };
        capture.identifier = ++nextId; wiredReceiver.routers.clear(); wiredReceiver.addRouter(capture);
        manager.updateSnapshot(relay);
        try {
            send(level, origin.north(3), input);
            check(radio.count == 1 && count[0] == 1, "Both native receivers must work before client-side cleanup");
            for (int visit = 0; visit < 3; visit++) {
                // The integrated client has a separate registry containing the same frequency numbers.
                // Use that exact native registry/channel removal path without loading client-only classes.
                var clientFrequencies = new FrequenciesImpl();
                Frequency clientChannel = clientFrequencies.getOrCreate(out.frequency(), out.mode());
                UUID reference = UUID.randomUUID();
                if (transmitter) {
                    var endpoint = new RadioTransmitter(clientChannel, WorldlyPosition.of(origin, level), reference);
                    clientChannel.getTransmitters().add(endpoint);
                    check(clientChannel.removeTransmitter(reference) == endpoint, "Client TX must be removed from its own channel");
                } else {
                    var endpoint = new RadioReceiver(clientChannel, WorldlyPosition.of(origin, level), reference);
                    clientChannel.getReceivers().add(endpoint);
                    check(clientChannel.removeReceiver(reference) == endpoint, "Client RX must be removed from its own channel");
                }
                check(frequencies.get(out.frequency(), out.mode()) == output,
                        "Unloading the last client endpoint must not delete the live server channel");
                check(clientFrequencies.get(out.frequency(), out.mode()) == null,
                        "Empty-channel cleanup must remove the channel from its owning registry");
                check(radio.block.active && block.active && output.getReceivers().contains(radio.receiver)
                                && output.getReceivers().contains(wiredReceiver),
                        "Server receivers must stay active and subscribed without reactivation");
                send(level, origin.north(3), input);
                check(radio.count == visit + 2 && count[0] == visit + 2,
                        "Radio and wired RX must keep receiving converted packets after each client unload");
            }
            helper.succeed();
        } finally {
            // Restore the fixture if the pre-fix implementation deleted its live server channel.
            if (frequencies.get(out.frequency(), out.mode()) == null) frequencies.add(output);
            radio.close(level); level.setBlock(receiverPos, Blocks.AIR.defaultBlockState(), 3);
            manager.removeAt(relay.dimension(), relay.position());
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void delayed_ground_radio_activation_retains_a_reachable_native_frequency(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); TowerManager manager = TowerManager.get(level.getServer());
        Frequency input = frequency(614, Frequency.Modulation.AMPLITUDE), output = frequency(615, Frequency.Modulation.AMPLITUDE);
        ChannelKey in = ChannelKey.from(input), out = ChannelKey.from(output);
        BlockPos relayPos = helper.absolutePos(new BlockPos(2, 2, 2)), radioPos = relayPos.east(3);
        TowerRecord relay = new TowerRecord(UUID.randomUUID(), level.dimension().location().toString(), relayPos,
                Set.of(in, out), true, RelayCatalysts.GILDED, true, 32, Set.of(), null, RelayAttachment.NONE,
                new TransponderChannels(in, out, null, false));
        manager.updateSnapshot(relay);
        level.setBlock(radioPos, SimpleRadioBlocks.RADIO.defaultBlockState(), 3);
        var block = (RadioBlockEntity) level.getBlockEntity(radioPos);
        block.loadTag(out.save());
        check(block.frequency == output && !block.active && output.getReceivers().isEmpty(),
                "Deserializing a radio must retain its frequency before the first activation tick");
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        // A visible chunk may deserialize entities before they start ticking. Native GC runs every 20 ticks.
        // Reproduce only its empty-channel eviction, without globally collecting other tests' routers.
        check(!((FrequencyChannel) output).validate(), "An unactivated radio's channel is eligible for native GC");
        frequencies.remove(output);
        check(frequencies.get(out.frequency(), out.mode()) == null, "GC must have evicted the empty output channel");
        block.activate();
        RadioReceiver receiver = (RadioReceiver) block.receiver;
        int[] count = {0};
        RadioRouter capture = new RadioRouter(WorldlyPosition.of(radioPos, level)) {
            @Override public void take(Source source) { count[0]++; }
        };
        capture.identifier = ++nextId; receiver.routers.clear(); receiver.addRouter(capture);
        try {
            send(level, relayPos.north(3), input);
            check(count[0] == 1, "A radio activated after empty-channel GC must receive transponded packets: channelRegistered="
                    + (frequencies.get(out.frequency(), out.mode()) != null) + ", receiverRegistered="
                    + (frequencies.get(out.frequency(), out.mode()) != null
                    && frequencies.get(out.frequency(), out.mode()).getReceivers().contains(receiver)) + ", " + manager.summary());
            check(receiver.frequency == frequencies.get(out.frequency(), out.mode()), "The radio must join the live canonical channel");
            helper.succeed();
        } finally {
            level.setBlock(radioPos, Blocks.AIR.defaultBlockState(), 3);
            manager.removeAt(relay.dimension(), relay.position());
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void delayed_native_devices_rejoin_the_current_frequency_generation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        var blocks = List.of(SimpleRadioBlocks.RADIO, SimpleRadioBlocks.RECEIVER, SimpleRadioBlocks.TRANSMITTER);
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            Frequency previous = frequency(616 + i, Frequency.Modulation.AMPLITUDE); ChannelKey key = ChannelKey.from(previous);
            level.setBlock(pos, blocks.get(i).defaultBlockState(), 3);
            var device = (AuditoryBlockEntity) level.getBlockEntity(pos); device.loadTag(key.save());
            check(!((FrequencyChannel) previous).validate(), "The loaded but inactive device must not keep an empty channel registered");
            frequencies.remove(previous);
            Frequency current = frequencies.getOrCreate(key.frequency(), key.mode());
            check(current != previous, "Another device must have created a new generation of the same channel");
            try {
                device.activate();
                check(device.frequency == current, "Every native station must replace its deserialized channel with the live generation");
                if (device.receiver instanceof RadioReceiver receiver)
                    check(receiver.frequency == current && current.getReceivers().contains(receiver), "Native RX must subscribe to the canonical channel");
                if (device.transmitter instanceof RadioTransmitter transmitter)
                    check(transmitter.frequency == current && current.getTransmitters().contains(transmitter)
                                    && (Object) transmitter.routers == (Object) current.getReceivers(),
                            "Native TX must route through the canonical channel's current receiver list");
            } finally { level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void diagnostic_probe_preserves_live_routes_registration_and_unloaded_chunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); TowerManager manager = TowerManager.get(level.getServer());
        Frequency input = frequency(619, Frequency.Modulation.AMPLITUDE), output = frequency(620, Frequency.Modulation.AMPLITUDE);
        ChannelKey in = ChannelKey.from(input), out = ChannelKey.from(output);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos remote = new BlockPos(790000, 80, 790000);
        check(!level.hasChunkAt(remote), "Diagnostic remote fixture must start unloaded");
        TowerRecord tower = new TowerRecord(UUID.randomUUID(), level.dimension().location().toString(), remote,
                Set.of(in, out), true, RelayCatalysts.GILDED, true, 32, Set.of(remote.asLong()), null, RelayAttachment.AMPLIFIER,
                new TransponderChannels(in, out, null, false));
        manager.updateSnapshot(tower);
        ItemStack held = new ItemStack(SimpleRadioItems.WALKIE_TALKIE);
        SimpleRadioItems.WALKIE_TALKIE.setFrequency(held, input.getFrequency(), input.getModulation());
        var before = manager.network(level, in); long frames = manager.forwardedFrames, deliveries = manager.receiverDeliveries;
        var routers = ServerSimpleRadioApi.getInstance().getRouters();
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies().get();
        try {
            List<String> lines = RelayDiagnostics.describe(level, origin.getCenter(), held);
            check(lines.stream().anyMatch(line -> line.startsWith("CACHE ") && line.contains("samePorts=true") && line.contains("sameFirstLegs=true"))
                            && lines.stream().anyMatch(line -> line.startsWith("PROBE fresh "))
                            && lines.stream().anyMatch(line -> line.startsWith("LIVE ") && line.endsWith("chunk unloaded")),
                    "Diagnostic must report cached/fresh graphs and saved-only tower state");
            check(manager.network(level, in) == before && manager.snapshot(tower.id()).equals(tower)
                            && frames == manager.forwardedFrames && deliveries == manager.receiverDeliveries
                            && routers.equals(ServerSimpleRadioApi.getInstance().getRouters())
                            && frequencies.equals(ServerSimpleRadioApi.getInstance().frequencies().get()) && !level.hasChunkAt(remote),
                    "Diagnostic must not replace caches, mutate devices, emit audio or load tower chunks");
            helper.succeed();
        } finally { manager.removeAt(tower.dimension(), tower.position()); }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void physical_simplex_transponder_delivers_to_a_placed_radio(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Frequency input = frequency(610, Frequency.Modulation.AMPLITUDE), output = frequency(611, Frequency.Modulation.AMPLITUDE);
        BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2)), relayPos = core.north();
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        level.setBlock(relayPos, SimpleRadioRepeaters.RELAY.defaultBlockState(), 3);
        level.setBlock(relayPos.above(), SimpleRadioRepeaters.TRANSPONDER.defaultBlockState(), 3);
        var relay = (RelayBlockEntity) level.getBlockEntity(relayPos);
        var module = (TransponderBlockEntity) level.getBlockEntity(relayPos.above());
        relay.installCatalyst(new ItemStack(Items.GILDED_BLACKSTONE), false);
        module.setItem(0, card(input)); module.setItem(1, card(output));
        Tap direct = new Tap(level, core.west(3), input), converted = new Tap(level, core.east(3), output);
        TowerManager manager = TowerManager.get(level.getServer());
        helper.runAfterDelay(40, () -> {
            try {
                check(manager.isEligible(relay.relayId()), "Physical transponder must have a verified ready Relay: " + manager.describe());
                TowerRecord snapshot = manager.snapshot(relay.relayId());
                check(snapshot.transponder() != null && snapshot.transponder().ports().equals(List.of(
                        new TransponderChannels.Port(ChannelKey.from(input), ChannelKey.from(output)))),
                        "The installed cards must publish a left-to-right port");
                long deliveries = manager.receiverDeliveries;
                RadioSource source = send(level, core.north(3), input);
                check(direct.count == 1, "The input radio must receive the native direct transmission");
                check(converted.count == 1 && converted.last.frequencyMedium == output,
                        "A ready local transponder must convert to the native placed 0.02 radio; counters=" + manager.summary());
                check(manager.receiverDeliveries == deliveries + 2 && converted.last.data == source.data
                                && converted.last.activity == 42 && ((RelaySourceState) converted.last).repeaters$getRelayCount() == 1,
                        "Conversion must record both actual deliveries and preserve encoded voice metadata");
                helper.succeed();
            } finally {
                direct.close(level); converted.close(level);
                level.setBlock(relayPos.above(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(relayPos, Blocks.AIR.defaultBlockState(), 3);
            }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void multicast_and_existing_modes_follow_the_full_channel_matrix(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); TowerManager manager = TowerManager.get(level.getServer());
        List<Frequency> channels = List.of(frequency(805, Frequency.Modulation.AMPLITUDE),
                frequency(806, Frequency.Modulation.AMPLITUDE), frequency(806, Frequency.Modulation.FREQUENCY));
        var keys = channels.stream().map(ChannelKey::from).toList(); BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        List<Tap> taps = new ArrayList<>();
        var tower = new TowerRecord(UUID.randomUUID(), level.dimension().location().toString(), origin,
                Set.copyOf(keys), true, RelayCatalysts.GILDED, true, 32, Set.of(), null, RelayAttachment.NONE,
                new TransponderChannels(keys.get(0), keys.get(1), keys.get(2), TransponderChannels.Mode.SIMPLEX));
        try {
            for (int i = 0; i < 3; i++) taps.add(new Tap(level, origin.east(i + 1), channels.get(i)));
            for (var mode : TransponderChannels.Mode.values()) {
                var selected = new TransponderChannels(keys.get(0), keys.get(1), keys.get(2), mode);
                var snapshot = tower.settings(selected.channels(), true, tower.catalystItem(), null, tower.attachment(), selected);
                manager.updateSnapshot(snapshot);
                check(TowerRecord.load(snapshot.save()).transponder().mode() == mode, "Saved relay routing must retain its mode");
                for (int input = 0; input < 3; input++) {
                    int[] before = taps.stream().mapToInt(t -> t.count).toArray();
                    RadioSource packet = send(level, origin.north(3), channels.get(input));
                    for (int output = 0; output < 3; output++) {
                        boolean expected = input == output || mode == TransponderChannels.Mode.MULTICAST
                                || input == 0 || (mode == TransponderChannels.Mode.DUPLEX && output == 0);
                        check(taps.get(output).count == before[output] + (expected ? 1 : 0),
                                mode + " must deliver exactly the allowed channel once: input=" + input + " output=" + output);
                        if (expected) check(taps.get(output).last.data == packet.data
                                        && taps.get(output).last.originalOwner.equals(packet.originalOwner),
                                "Transponding must preserve encoded audio and stream ownership");
                    }
                }
            }
        } finally { for (Tap tap : taps) tap.close(level); manager.removeAt(tower.dimension(), tower.position()); }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void panel_cycles_all_three_modes_and_preserves_older_world_settings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var state = SimpleRadioRepeaters.TRANSPONDER.defaultBlockState().setValue(ChannelBankBlock.FACING, net.minecraft.core.Direction.NORTH);
        level.setBlock(pos, state, 3); var module = (TransponderBlockEntity) level.getBlockEntity(pos);
        var player = helper.makeMockPlayer(); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var hit = new net.minecraft.world.phys.BlockHitResult(pos.getCenter().add(0, 0, -.5), net.minecraft.core.Direction.NORTH, pos, false);
        try {
            check(module.mode() == TransponderChannels.Mode.SIMPLEX, "New modules must start in Simplex");
            for (var expected : List.of(TransponderChannels.Mode.DUPLEX, TransponderChannels.Mode.MULTICAST, TransponderChannels.Mode.SIMPLEX)) {
                SimpleRadioRepeaters.TRANSPONDER.use(state, level, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                check(module.mode() == expected, "Front panel must cycle Simplex, Duplex, Multicast, Simplex");
                var restored = new TransponderBlockEntity(pos, state); restored.load(module.saveWithoutMetadata());
                check(restored.mode() == expected, "Module save and client update data must preserve every mode");
            }
            var legacy = module.saveWithoutMetadata(); legacy.remove("Mode"); legacy.putBoolean("Duplex", true);
            module.load(legacy); check(module.mode() == TransponderChannels.Mode.DUPLEX, "Existing Duplex modules must stay Duplex");
            legacy.putBoolean("Duplex", false); module.load(legacy);
            check(module.mode() == TransponderChannels.Mode.SIMPLEX, "Existing Simplex modules must stay Simplex");
        } finally { level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void multicast_requires_both_card_sides_and_deduplicates_repeated_channels(GameTestHelper helper) {
        ChannelKey a = ChannelKey.from(frequency(808, Frequency.Modulation.AMPLITUDE));
        ChannelKey b = ChannelKey.from(frequency(809, Frequency.Modulation.AMPLITUDE));
        var mode = TransponderChannels.Mode.MULTICAST;
        check(new TransponderChannels(a, null, null, mode).ports().isEmpty()
                        && new TransponderChannels(null, a, b, mode).ports().isEmpty(),
                "Multicast must still require an RX card and at least one TX card");
        var selected = new TransponderChannels(a, b, b, mode);
        check(selected.ports().size() == 4 && Set.copyOf(selected.ports()).size() == 4,
                "Two unique channels must create four unique Multicast ports, including unchanged channels");
        var old = new TransponderChannels(a, b, null, true).save(); old.remove("Mode"); old.putBoolean("Duplex", true);
        check(TransponderChannels.load(old).mode() == TransponderChannels.Mode.DUPLEX, "Saved tower records must retain old Duplex settings");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void saved_am_transponder_returns_through_the_same_banks_with_ender_amplifiers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); TowerManager manager = TowerManager.get(level.getServer());
        Frequency input = frequency(612, Frequency.Modulation.AMPLITUDE), output = frequency(613, Frequency.Modulation.AMPLITUDE);
        ChannelKey in = ChannelKey.from(input), out = ChannelKey.from(output);
        BlockPos start = new BlockPos(760004, 80, 760004);
        level.getChunkAt(start);
        List<TowerRecord> records = new ArrayList<>();
        // RT1 is represented by a loaded-chunk record for this synchronous packet. RT2 and RT3 remain saved only.
        for (int i = 0; i < 3; i++) {
            BlockPos position = start.offset(i * 4000, 0, 0);
            if (i > 0) check(!level.hasChunkAt(position), "Remote Relay chunks must start unloaded");
            TowerRecord record = new TowerRecord(UUID.randomUUID(), level.dimension().location().toString(), position,
                    Set.of(in, out), true, RelayCatalysts.GILDED, true, 16, Set.of(position.asLong()), null,
                    RelayAttachment.AMPLIFIER, i == 2 ? new TransponderChannels(in, out, null, false) : null);
            records.add(TowerRecord.load(record.save()));
        }
        Tap converted = new Tap(level, start.east(3), output);
        try {
            for (TowerRecord record : records) manager.updateSnapshot(record);
            RadioSource source = send(level, start.north(3), input);
            check(converted.count == 1 && converted.last.frequencyMedium == output,
                    "A 16-score AM chain with Ender amplifiers must return through RT3 conversion and the same RT2/RT1 banks; " + manager.summary());
            check(((RelaySourceState) converted.last).repeaters$getRelayCount() == 5
                            && ((RelaySourceState) converted.last).repeaters$getDecayCount() == 1
                            && converted.last.data == source.data && converted.last.computeSeverity() < 100,
                    "The out-and-back path must use five visits, resetting decay without dropping voice data");
            for (int i = 1; i < 3; i++) check(!level.hasChunkAt(records.get(i).position()), "Routing must leave the remote chunks unloaded");
            for (TowerRecord record : records) manager.updateSnapshot(record.settings(record.channels(), true,
                    record.catalystItem(), null, RelayAttachment.NONE, record.transponder()));
            send(level, start.north(3), input);
            check(converted.count == 1, "Without amplifiers, 4,000-block AM spacing must fail on the decayed return leg");
            helper.succeed();
        } finally {
            converted.close(level);
            for (TowerRecord record : records) manager.removeAt(record.dimension(), record.position());
        }
    }
}
