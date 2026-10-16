package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.*;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** Native receiver/bridge tests; foreign towers and listener positions stay in unloaded chunks. */
public class ExpansionGameTests implements FabricGameTest {
    private static short nextId = 28000;
    private static void check(boolean result, String message) { if (!result) throw new GameTestAssertException(message); }
    private static ChannelKey channel(int number) { return channel(number, Frequency.Modulation.AMPLITUDE); }
    private static ChannelKey channel(int number, Frequency.Modulation mode) {
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        return ChannelKey.from(frequencies.getOrCreate(frequencies.incrementFrequency(frequencies.defaultFrequency(), number), mode));
    }
    private static Frequency frequency(ChannelKey key) { return ServerSimpleRadioApi.getInstance().frequencies().getOrCreate(key.frequency(), key.mode()); }
    private static WorldlyPosition at(ServerLevel level, Vec3 position) {
        return new WorldlyPosition((float) position.x, (float) position.y, (float) position.z, level);
    }
    private static final class Tap extends RadioRouter {
        final RadioReceiver receiver; int count; RadioSource last;
        Tap(ServerLevel level, Vec3 position, ChannelKey channel) {
            super(at(level, position)); identifier = ++nextId;
            receiver = new RadioReceiver(frequency(channel), at(level, position)).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
            receiver.identifier = ++nextId; receiver.owner = new ArmorStand(level, position.x, position.y, position.z);
            receiver.addRouter(this); frequency(channel).getReceivers().add(receiver);
        }
        @Override public void take(Source source) { count++; last = (RadioSource) source; }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper; final ServerLevel overworld, nether, end; final TowerManager manager;
        final boolean oldNative; final List<TowerRecord> towers = new ArrayList<>(); final List<Tap> taps = new ArrayList<>();
        Fixture(GameTestHelper helper) {
            this.helper = helper; overworld = helper.getLevel(); manager = TowerManager.get(overworld.getServer());
            nether = overworld.getServer().getLevel(Level.NETHER); end = overworld.getServer().getLevel(Level.END);
            oldNative = SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional;
            SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional = false;
            check(nether != null && end != null, "Standard server dimensions must exist");
        }
        TowerRecord tower(ServerLevel level, BlockPos position, int score, RelayAttachment attachment,
                          TransponderChannels transponder, ChannelKey... channels) {
            check(!level.hasChunkAt(position), "Virtual tower chunk must start unloaded");
            TowerRecord tower = new TowerRecord(UUID.randomUUID(), level.dimension().location().toString(), position,
                    Set.of(channels), true, RelayCatalysts.GILDED, true, score, Set.of(position.asLong()), null, attachment, transponder);
            towers.add(tower); manager.updateSnapshot(tower); return tower;
        }
        Tap tap(ServerLevel level, Vec3 position, ChannelKey channel) {
            check(!level.hasChunkAt(BlockPos.containing(position)), "Virtual listener chunk must start unloaded");
            Tap tap = new Tap(level, position, channel); taps.add(tap); return tap;
        }
        RadioSource send(ServerLevel level, Vec3 position, ChannelKey channel) { return send(level, position, channel, 0, 0); }
        RadioSource send(ServerLevel level, Vec3 position, ChannelKey channel, int hops, int decay) {
            return send(level, position, channel, hops, decay, 10);
        }
        RadioSource send(ServerLevel level, Vec3 position, ChannelKey channel, int hops, int decay, float power) {
            RadioTransmitter transmitter = new RadioTransmitter(frequency(channel), at(level, position)).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
            transmitter.identifier = ++nextId;
            RadioSource source = new RadioSource(UUID.randomUUID(), at(level, position), new byte[]{1, 2, 3}, 1);
            source.originalOwner = UUID.randomUUID(); source.activity = 42; source.frequencyMedium = frequency(channel);
            source.frequencingType = SimpleRadioFrequencing.TRANSCEIVER.id;
            source.transmissionPower = power; source.transmissionCap = transmitter.getPower(channel.mode());
            ((RelaySourceState) source).repeaters$setRelayCount(hops); ((RelaySourceState) source).repeaters$setDecayCount(decay);
            transmitter.accept(source); return source;
        }
        void noChunkLoads() {
            for (TowerRecord tower : towers) {
                ServerLevel level = List.of(overworld, nether, end).stream()
                        .filter(world -> world.dimension().location().toString().equals(tower.dimension())).findFirst().orElseThrow();
                check(!level.hasChunkAt(tower.position()), "Routing must never load a saved tower chunk");
            }
            for (Tap tap : taps) check(!((ServerLevel) tap.position.level).hasChunkAt(tap.position.blockPos()),
                    "A player-like listener must not force terrain loading");
        }
        @Override public void close() {
            for (Tap tap : taps) tap.receiver.frequency.getReceivers().remove(tap.receiver);
            for (TowerRecord tower : towers) manager.removeAt(tower.dimension(), tower.position());
            SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional = oldNative;
        }
    }
    private static float nativeArrival(Vec3 scaledOrigin, ServerLevel originLevel, Tap destination, ChannelKey key, float power) {
        boolean before = SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional;
        try {
            SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional = true;
            RadioTransmitter transmitter = new RadioTransmitter(frequency(key), at(originLevel, scaledOrigin)).frequencingType(SimpleRadioFrequencing.TRANSMITTER);
            RadioSource source = new RadioSource(UUID.randomUUID(), at(originLevel, scaledOrigin), new byte[]{1}, 1);
            source.frequencingType = SimpleRadioFrequencing.TRANSMITTER.id; source.transmissionCap = power; source.transmissionPower = power;
            return transmitter.shouldRouteTo(source, destination.receiver) ? transmitter.prepareSource(source, destination.receiver).transmissionPower : 0;
        } finally { SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional = before; }
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_cross_dimension_mode_needs_no_focus_and_uses_native_y_and_attenuation(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey key = channel(410); BlockPos pos = new BlockPos(800000, 80, 800000);
            TowerRecord tower = f.tower(f.overworld, pos, 32, RelayAttachment.NONE, null, key);
            Vec3 listener = pos.getCenter().scale(1d / 8).add(100, 0, 0); Tap tap = f.tap(f.nether, listener, key);
            f.send(f.overworld, pos.getCenter(), key); check(tap.count == 0, "No catalyst-only cross-dimension path may exist with native mode off");
            SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional = true;
            RadioSource source = f.send(f.overworld, pos.getCenter(), key);
            check(tap.count == 1 && Math.abs(tap.last.transmissionPower - nativeArrival(pos.getCenter(), f.overworld, tap, key,
                            NativeRadioAdapter.departurePower(tower, key, 1))) < .02,
                    "Native mode must match the actual native transmitter travel without needing a focus");
            check(tap.last.origin.level == f.overworld && ((RelaySourceState) tap.last).repeaters$getRelayCount() == 1,
                    "Native dimension mode must retain the original carrier origin and count its relay");
            TowerRecord focused = tower.settings(tower.channels(), true, tower.catalystItem(), null, RelayAttachment.END_FOCUS, null);
            f.manager.updateSnapshot(focused);
            check(RadioSpace.sites(f.overworld.getServer(), focused, true).size() == 1,
                    "Native mode must ignore focus projections rather than granting an extra bottom/roof antenna");
            source = f.send(f.overworld, pos.getCenter(), key); check(tap.count == 2, "An installed focus must not block ordinary native connectivity");
            SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional = false;
            f.send(f.overworld, pos.getCenter(), key); check(tap.count == 2, "Policy changes must stop reusing native dimension paths immediately");
            check(tap.last.data == source.data && tap.last.computeSeverity() >= source.computeSeverity(), "Native-mode relaying must retain encoded audio and worst noise");
            f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void nether_focus_injects_at_the_roof_and_receives_the_return_without_chunk_loads(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey key = channel(412); BlockPos pos = new BlockPos(-80000, 72, 120000);
            TowerRecord tower = f.tower(f.overworld, pos, 32, RelayAttachment.NETHER_FOCUS, null, key);
            RadioSpace.Site pocket = RadioSpace.sites(f.overworld.getServer(), tower, false).get(1);
            check(pocket.projected() && pocket.level() == f.nether && pocket.position().y == 128
                            && pocket.position().x == pos.getCenter().x / 8 && pocket.position().z == pos.getCenter().z / 8,
                    "Nether injection must use mapped X/Z and the roof, ignoring Overworld height");
            Vec3 listener = pocket.position().add(40, -30, 0); Tap nether = f.tap(f.nether, listener, key);
            Tap overworld = f.tap(f.overworld, pos.getCenter().add(1, 0, 0), key);
            RadioSource first = f.send(f.overworld, pos.getCenter(), key);
            check(nether.count == 1 && nether.last.origin.level == f.nether && nether.last.data == first.data
                            && nether.last.originalOwner.equals(first.originalOwner),
                    "A focus must deliver a locally regenerated carrier through the ordinary native receiver, preserving owners and bytes");
            // Exercise the native speaker's independent cross-dimension origin gate without decoding test bytes.
            RadioSpeaker speaker = new RadioSpeaker(at(f.nether, listener));
            try {
                var field = RadioSpeaker.class.getDeclaredField("effect"); field.setAccessible(true);
                var effect = (com.codinglitch.simpleradio.radio.effects.AudioEffect) field.get(speaker);
                effect.severity = -123; RadioSource playable = nether.last.copy(); playable.data = null; playable.sound = null;
                speaker.speak(playable);
                check(effect.severity >= 0, "The actual native speaker must accept the focus-regenerated carrier with native cross-dimension radio disabled");
            } catch (ReflectiveOperationException error) { throw new GameTestAssertException(error.toString()); }
            finally {
                // A native speaker belongs to both the speaker container and the router map.
                // Remove both so its delayed validation cannot remove a newly reused router ID.
                ServerSimpleRadioApi.getInstance().speakers().contents().removeIf(candidate -> candidate == speaker);
                ServerSimpleRadioApi.getInstance().removeRouter(speaker);
            }
            int previous = overworld.count; f.send(f.nether, listener, key, 0, 0, 2000);
            check(overworld.count == previous + 1 && overworld.last.origin.level == f.overworld,
                    "A handheld in the Nether pocket must reach the Overworld Relay without its own focus");
            f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void end_focus_maps_xz_to_the_bottom_and_has_no_500_block_cap(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey key = channel(414); BlockPos pos = new BlockPos(90000, 140, -90000);
            TowerRecord tower = f.tower(f.overworld, pos, 114, RelayAttachment.END_FOCUS, null, key);
            RadioSpace.Site pocket = RadioSpace.sites(f.overworld.getServer(), tower, false).get(1);
            check(pocket.position().equals(new Vec3(pos.getCenter().x, 0, pos.getCenter().z)),
                    "End injection must use corresponding X/Z at the bottom rather than the center island");
            Tap end = f.tap(f.end, pocket.position().add(600, 0, 0), key);
            Tap overworld = f.tap(f.overworld, pos.getCenter().add(1, 0, 0), key);
            f.send(f.overworld, pos.getCenter(), key);
            float expected = nativeArrival(pocket.position(), f.overworld, end, key, NativeRadioAdapter.departurePower(tower, key, 1));
            check(end.count == 1 && expected > 0 && Math.abs(end.last.transmissionPower - expected) < .02,
                    "Beyond 500 blocks, an End focus must use actual native dimensional travel loss without an artificial cutoff");
            int previous = overworld.count; f.send(f.end, pocket.position().add(600, 0, 0), key, 0, 0, 2000);
            check(overworld.count == previous + 1, "Return reception beyond 500 blocks must also use native gain and loss");
            f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void two_focus_pockets_need_a_physical_hub_in_the_target_dimension(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey key = channel(416); BlockPos aPos = new BlockPos(80000, 64, 80000), bPos = aPos.offset(5000, 0, 0);
            TowerRecord a = f.tower(f.overworld, aPos, 32, RelayAttachment.END_FOCUS, null, key);
            TowerRecord b = f.tower(f.overworld, bPos, 32, RelayAttachment.END_FOCUS, null, key);
            var aSite = RadioSpace.sites(f.overworld.getServer(), a, false).get(1);
            var bSite = RadioSpace.sites(f.overworld.getServer(), b, false).get(1);
            check(RadioSpace.between(aSite, bSite, false) == null, "Two virtual pockets must not directly link");
            Tap target = f.tap(f.overworld, bPos.getCenter().add(1, 0, 0), key);
            f.send(f.overworld, aPos.getCenter(), key); check(target.count == 0, "Distant Overworld pockets need a real End hub");
            TowerRecord hub = f.tower(f.end, aPos.offset(2500, -64, 0), 300, RelayAttachment.NONE, null, key);
            f.send(f.overworld, aPos.getCenter(), key);
            check(target.count == 1 && ((RelaySourceState) target.last).repeaters$getRelayCount() == 3,
                    "A real unloaded End relay must connect both pockets and consume its own relay visit");
            f.manager.removeAt(hub.dimension(), hub.position()); f.send(f.overworld, aPos.getCenter(), key);
            check(target.count == 1, "Removing the hub must immediately invalidate the cached focus route"); f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void amplifier_resets_decay_but_retains_total_visits_catalyst_ceiling_and_noise(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey key = channel(418); BlockPos pos = new BlockPos(100000, 80, 100000);
            TowerRecord tower = f.tower(f.overworld, pos, 32, RelayAttachment.NONE, null, key);
            Tap tap = f.tap(f.overworld, pos.getCenter().add(1, 0, 0), key);
            f.send(f.overworld, pos.getCenter(), key, 30, 30);
            float weak = tap.last.transmissionCap;
            for (Item catalyst : List.of(Items.AMETHYST_BLOCK, Items.GILDED_BLACKSTONE)) {
                TowerRecord amplified = tower.settings(tower.channels(), true, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(catalyst),
                        null, RelayAttachment.AMPLIFIER, null); f.manager.updateSnapshot(amplified);
                check(NativeRadioAdapter.towerPower(amplified, key) - NativeRadioAdapter.towerPower(tower, key) == 160,
                        "A 32-point antenna must gain 16 points, without multiplying the native transmitter base");
                check(NativeRadioAdapter.receiveAtTower(1000, 300, 300, SimpleRadioFrequencing.TRANSMITTER, tower, key) == null
                                && NativeRadioAdapter.receiveAtTower(1000, 300, 300, SimpleRadioFrequencing.TRANSMITTER, amplified, key) != null,
                        "Amplification must also increase reception, while retaining the raw saved antenna score");
                RadioSource source = f.send(f.overworld, pos.getCenter(), key, 30, 30);
                check(((RelaySourceState) tap.last).repeaters$getRelayCount() == 31
                                && ((RelaySourceState) tap.last).repeaters$getDecayCount() == 1
                                && Math.abs(tap.last.transmissionCap - NativeRadioAdapter.towerPower(amplified, key) * amplified.efficiency()) < .02
                                && tap.last.transmissionCap > weak && tap.last.computeSeverity() >= source.computeSeverity(),
                        "Amplifiers reset only decay, retain their catalyst ceiling, and cannot heal degraded audio");
                RadioSource copy = tap.last.copy(); ((RelaySourceState) copy).repeaters$setDecayCount(12);
                check(((RelaySourceState) tap.last).repeaters$getDecayCount() == 1, "Source branches must not share mutable decay state");
            }
            f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void transponder_simplex_and_duplex_preserve_card_direction_and_modulation(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey left = channel(420), right = channel(421), second = channel(422, Frequency.Modulation.FREQUENCY);
            BlockPos pos = new BlockPos(110000, 80, 110000);
            TowerRecord tower = f.tower(f.overworld, pos, 32, RelayAttachment.NONE,
                    new TransponderChannels(left, right, second, false), left, right, second);
            Tap leftTap = f.tap(f.overworld, pos.getCenter().add(1000, 0, 0), left);
            Tap rightTap = f.tap(f.overworld, pos.getCenter().add(1, 0, 0), right), secondTap = f.tap(f.overworld, pos.getCenter().add(1, 0, 0), second);
            RadioSource source = f.send(f.overworld, pos.getCenter(), left);
            check(leftTap.count == 0 && rightTap.count == 1 && secondTap.count == 1
                            && secondTap.last.frequencyMedium == frequency(second) && secondTap.last.data == source.data,
                    "Simplex must convert the left input to both selected right outputs, including AM to FM, without retaining an unselected input channel");
            f.send(f.overworld, pos.getCenter(), right); check(leftTap.count == 0 && secondTap.count == 1, "Simplex must not accept reverse traffic");
            f.manager.updateSnapshot(tower.settings(tower.channels(), true, tower.catalystItem(), null, RelayAttachment.NONE,
                    new TransponderChannels(left, right, second, true)));
            f.send(f.overworld, pos.getCenter(), right); check(leftTap.count == 1 && secondTap.count == 1,
                    "Duplex converts right to left, without silently cross-connecting the two right slots");
            f.send(f.overworld, pos.getCenter(), second); check(leftTap.count == 2, "Both right slots must work as duplex inputs"); f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void transponder_can_keep_the_main_frequency_live_and_feed_another_network(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey left = channel(424), right = channel(425); BlockPos pos = new BlockPos(120000, 80, 120000);
            f.tower(f.overworld, pos, 32, RelayAttachment.NONE, new TransponderChannels(left, left, right, false), left, right);
            Tap same = f.tap(f.overworld, pos.getCenter().add(1000, 0, 0), left), copy = f.tap(f.overworld, pos.getCenter().add(1, 0, 0), right);
            f.send(f.overworld, pos.getCenter(), left);
            check(same.count == 1 && copy.count == 1 && ((RelaySourceState) same.last).repeaters$getRelayCount() == 1,
                    "Putting the input frequency on a right card must repeat the main channel and branch once to the other"); f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void transponders_route_through_ordinary_banks_and_keep_count_across_conversions(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey left = channel(426), middle = channel(427), right = channel(428); BlockPos pos = new BlockPos(130000, 80, 130000);
            f.tower(f.overworld, pos, 32, RelayAttachment.NONE, new TransponderChannels(left, middle, null, false), left, middle);
            TowerRecord bridge = f.tower(f.overworld, pos.offset(2500, 0, 0), 32, RelayAttachment.NONE, null, middle);
            f.tower(f.overworld, pos.offset(5000, 0, 0), 32, RelayAttachment.NONE, new TransponderChannels(middle, right, null, false), middle, right);
            Tap tap = f.tap(f.overworld, pos.getCenter().add(5001, 0, 0), right); f.send(f.overworld, pos.getCenter(), left);
            check(tap.count == 1 && ((RelaySourceState) tap.last).repeaters$getRelayCount() == 3
                            && ((RelaySourceState) tap.last).repeaters$getDecayCount() == 3,
                    "A transponder-bank-transponder route must cross both channel conversions without resetting either count");
            f.manager.removeAt(bridge.dimension(), bridge.position()); f.send(f.overworld, pos.getCenter(), left);
            check(tap.count == 1, "The far conversion must stop when the required middle relay is removed"); f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void duplex_conversion_cycles_deliver_once_per_receiver_and_channel(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey left = channel(430), right = channel(431); BlockPos pos = new BlockPos(140000, 80, 140000);
            f.tower(f.overworld, pos, 32, RelayAttachment.NONE, new TransponderChannels(left, right, null, true), left, right);
            f.tower(f.overworld, pos.offset(100, 0, 0), 32, RelayAttachment.NONE, new TransponderChannels(right, left, null, true), left, right);
            Tap a = f.tap(f.overworld, pos.getCenter().add(1000, 0, 0), left), b = f.tap(f.overworld, pos.getCenter().add(1000, 0, 0), right);
            f.send(f.overworld, pos.getCenter(), left);
            check(a.count == 1 && b.count == 1 && ((RelaySourceState) a.last).repeaters$getRelayCount() <= RepeatersConfig.INSTANCE.maxRelayHops,
                    "Duplex loops must retain frame deduplication and a finite total visit budget"); f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_Nether_radios_use_quarter_range_and_a_moderate_noise_floor(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey key = channel(432, Frequency.Modulation.FREQUENCY);
            Vec3 origin = new Vec3(480000, 64, 480000);
            Tap near = f.tap(f.nether, origin.add(400, 0, 0), key);
            Tap far = f.tap(f.nether, origin.add(551, 0, 0), key);
            RadioTransmitter sender = new RadioTransmitter(frequency(key), at(f.nether, origin)).frequencingType(SimpleRadioFrequencing.TRANSMITTER);
            RadioSource source = new RadioSource(UUID.randomUUID(), at(f.nether, origin), new byte[]{1}, 1);
            source.frequencingType = SimpleRadioFrequencing.TRANSMITTER.id; source.transmissionCap = 2000; source.transmissionPower = 2000;
            check(sender.shouldRouteTo(source, near.receiver) && !sender.shouldRouteTo(source, far.receiver),
                    "Native reach checks must use 4x physical distance, not the old 8x or unscaled distance");
            RadioSource arrival = sender.prepareSource(source, near.receiver);
            check(Math.abs(arrival.transmissionPower - 600) < .01 && arrival.computeSeverity() == 12,
                    "Native travel must match the reach check and apply a non-cumulative +10-point baseline noise floor");
            var leg = RadioSpace.between(RadioSpace.physical(f.nether, origin), RadioSpace.physical(f.nether, new Vec3(near.position)), false);
            var expected = NativeRadioAdapter.receiveAtReceiver(leg, 2000, 2000, SimpleRadioFrequencing.TRANSMITTER, near.receiver, key);
            check(expected != null && Math.abs(expected.power() - arrival.transmissionPower) < .01
                            && expected.severity() == arrival.computeSeverity(), "Native and virtual Nether propagation must agree");
            f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void Nether_focus_improves_local_reach_in_both_directions_without_an_Overworld_pocket(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey key = channel(434); BlockPos pos = new BlockPos(500000, 64, 500000);
            TowerRecord ordinary = f.tower(f.nether, pos, 32, RelayAttachment.NONE, null, key);
            Tap remote = f.tap(f.nether, pos.getCenter().add(1200, 0, 0), key);
            Tap local = f.tap(f.nether, pos.getCenter().add(1, 0, 0), key);
            f.send(f.nether, pos.getCenter(), key, 0, 0, 1800);
            check(remote.count == 0, "An ordinary 32-score Nether relay must not reach this distant handheld");
            TowerRecord focused = ordinary.settings(ordinary.channels(), true, ordinary.catalystItem(), null, RelayAttachment.NETHER_FOCUS, null);
            f.manager.updateSnapshot(focused);
            for (boolean nativeMode : new boolean[]{false, true}) {
                var sites = RadioSpace.sites(f.overworld.getServer(), focused, nativeMode);
                check(sites.size() == 1 && sites.get(0).level() == f.nether && sites.get(0).focusedNether(),
                        "A Nether focus in the Nether improves only the physical site, including when native mode is enabled");
            }
            var site = RadioSpace.sites(f.overworld.getServer(), focused, false).get(0);
            var incoming = RadioSpace.between(RadioSpace.physical(f.nether, pos.getCenter()), site, false);
            var outgoing = RadioSpace.between(site, RadioSpace.physical(f.nether, new Vec3(remote.position)), false);
            var receivedIn = NativeRadioAdapter.receiveAtTower(incoming, 1800, 1800, SimpleRadioFrequencing.TRANSCEIVER, focused, key);
            var receivedOut = NativeRadioAdapter.receiveAtReceiver(outgoing, 4248, 4248, SimpleRadioFrequencing.TRANSMITTER, remote.receiver, key);
            RadioSource sent = f.send(f.nether, pos.getCenter(), key, 0, 0, 1800);
            check(remote.count == 1 && Math.abs(remote.last.computeSeverity() - 15) < .00001,
                    "A local focus must double reach and remove the extra Nether noise floor: deliveries=" + remote.count
                            + ", severity=" + (remote.last == null ? "none" : remote.last.computeSeverity())
                            + ", initial=" + sent.computeSeverity() + ", in=" + receivedIn + ", out=" + receivedOut
                            + ", inherited=" + (remote.last == null ? "none" : ((RelaySourceState) remote.last).repeaters$getInheritedSeverity()));
            int previous = local.count;
            f.send(f.nether, new Vec3(remote.position), key, 0, 0, 1800);
            check(local.count == previous + 1, "The local focus must improve incoming handheld reach as well as outgoing reach");
            f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void Nether_crossings_have_twice_the_native_dimensional_loss(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey key = channel(436); Vec3 origin = new Vec3(160000, 512, 160000);
            Tap tap = f.tap(f.nether, new Vec3(20100, 64, 20000), key);
            check(Math.abs(nativeArrival(origin, f.overworld, tap, key, 4000) - 1000) < .01,
                    "An 800-unit AM crossing must lose 5 per effective unit instead of the native 3");
            var leg = RadioSpace.between(RadioSpace.physical(f.overworld, origin), RadioSpace.physical(f.nether, new Vec3(tap.position)), true);
            var received = NativeRadioAdapter.receiveAtReceiver(leg, 4000, 4000, SimpleRadioFrequencing.TRANSMITTER, tap.receiver, key);
            check(received != null && Math.abs(received.power() - 1000) < .01, "Virtual crossings must use the same high penalty");
            f.noChunkLoads();
        }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void End_focus_on_an_End_relay_projects_back_into_the_Overworld(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ChannelKey key = channel(438); BlockPos pos = new BlockPos(520000, 70, 520000);
            TowerRecord tower = f.tower(f.end, pos, 32, RelayAttachment.END_FOCUS, null, key);
            var pocket = RadioSpace.sites(f.overworld.getServer(), tower, false).get(1);
            check(pocket.level() == f.overworld && pocket.projected() && pocket.position().equals(pos.getCenter()),
                    "An End relay must project to mapped Overworld X/Z at its own clamped height");
            Tap target = f.tap(f.overworld, pocket.position().add(500, 0, 0), key);
            Tap local = f.tap(f.end, pos.getCenter().add(1, 0, 0), key);
            f.send(f.end, pos.getCenter(), key, 0, 0, 1800);
            check(target.count == 1 && target.last.origin.level == f.overworld, "The reverse End focus must pass the native speaker origin gate");
            int previous = local.count;
            f.send(f.overworld, new Vec3(target.position), key, 0, 0, 1800);
            check(local.count == previous + 1, "The reverse projection must accept replies too");
            TowerRecord misplaced = f.tower(f.nether, pos.offset(2000, 0, 0), 32, RelayAttachment.END_FOCUS, null, key);
            check(RadioSpace.sites(f.overworld.getServer(), misplaced, false).size() == 1,
                    "The End focus connects only Overworld and End, not an unintended Nether bridge");
            f.noChunkLoads();
        }
        helper.succeed();
    }
}
