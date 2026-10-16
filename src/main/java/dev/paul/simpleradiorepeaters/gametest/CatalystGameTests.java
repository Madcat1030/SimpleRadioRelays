package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.*;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import java.util.*;

/** Exercises the actual native registry, source mixins and receiver pipeline without decoding voice. */
public class CatalystGameTests implements FabricGameTest {
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static Frequency channel(int offset) {
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        return frequencies.getOrCreate(frequencies.incrementFrequency(frequencies.defaultFrequency(), offset), Frequency.Modulation.AMPLITUDE);
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
    public void amethyst_socket_insertion_persistence_and_catalyst_swap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2)), pos = core.north();
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        level.setBlock(pos, SimpleRadioRepeaters.RELAY.defaultBlockState(), 3);
        level.setBlock(pos.above(), SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState(), 3);
        RelayBlockEntity relay = (RelayBlockEntity) level.getBlockEntity(pos);
        ChannelBankBlockEntity bank = (ChannelBankBlockEntity) level.getBlockEntity(pos.above());
        ItemStack card = new ItemStack(SimpleRadioRepeaters.CARD); Frequency frequency = channel(310);
        SimpleRadioRepeaters.CARD.setFrequency(card, frequency.getFrequency(), frequency.getModulation()); bank.setItem(0, card);
        var player = helper.makeMockPlayer(); player.getAbilities().instabuild = false;
        ItemStack held = new ItemStack(Items.AMETHYST_BLOCK, 2); player.setItemInHand(InteractionHand.MAIN_HAND, held);
        SimpleRadioRepeaters.RELAY.use(relay.getBlockState(), level, pos, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(pos.getCenter(), Direction.NORTH, pos, false));
        check(held.getCount() == 1 && relay.catalyst().is(Items.AMETHYST_BLOCK) && relay.hasCatalyst(), "Native amethyst blocks must insert exactly one into the Relay socket");
        RelayBlockEntity copy = new RelayBlockEntity(pos, relay.getBlockState()); copy.load(relay.getUpdateTag());
        check(copy.catalyst().is(Items.AMETHYST_BLOCK) && !relay.installCatalyst(new ItemStack(Items.DIRT), false), "Installed catalyst must survive client/save tags and reject unrelated items");
        helper.runAfterDelay(35, () -> {
            TowerManager manager = TowerManager.get(level.getServer()); TowerRecord saved = manager.snapshot(relay.relayId());
            ResourceLocation amethyst = BuiltInRegistries.ITEM.getKey(Items.AMETHYST_BLOCK);
            check(saved != null && saved.eligible() && saved.catalystItem().equals(amethyst)
                    && Math.abs(saved.efficiency() - .6) < .0001 && TowerRecord.load(saved.save()).equals(saved),
                    "The authoritative saved record must retain amethyst identity and native efficiency");
            var before = manager.network(level, ChannelKey.from(frequency));
            ItemStack extracted = relay.removeCatalyst(); check(extracted.is(Items.AMETHYST_BLOCK) && !manager.isEligible(relay.relayId()), "Extraction must return the actual catalyst and immediately stop the tower");
            relay.installCatalyst(new ItemStack(Items.GILDED_BLACKSTONE), false);
            check(manager.snapshot(relay.relayId()).catalystItem().equals(RelayCatalysts.GILDED)
                    && manager.network(level, ChannelKey.from(frequency)) != before, "Changing between two valid catalysts must update the saved identity and routes");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); helper.succeed();
        });
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_receiver_gets_exact_counted_power_even_below_one_percent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); TowerManager manager = TowerManager.get(level.getServer());
        BlockPos pos = helper.absolutePos(new BlockPos(40000, 80, 40000)); Frequency frequency = channel(312); ChannelKey key = ChannelKey.from(frequency);
        check(!level.hasChunkAt(pos), "Virtual tower fixture must remain unloaded");
        var at = pos.getCenter(); WorldlyPosition location = new WorldlyPosition((float) at.x, (float) at.y, (float) at.z, level);
        var player = helper.makeMockPlayer(); player.setPos(at.add(1, 0, 0));
        RadioTransmitter transmitter = new RadioTransmitter(frequency, location).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
        RadioReceiver receiver = new RadioReceiver(frequency, player).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
        receiver.identifier = 26000; Capture capture = new Capture(receiver.getLocation()); capture.identifier = 26001; receiver.addRouter(capture);
        frequency.getReceivers().add(receiver); UUID id = UUID.randomUUID(); int deliveries = 0;
        try {
            for (Item item : List.of(Items.GILDED_BLACKSTONE, Items.AMETHYST_BLOCK)) {
                ResourceLocation catalyst = BuiltInRegistries.ITEM.getKey(item);
                TowerRecord tower = new TowerRecord(id, level.dimension().location().toString(), pos, Set.of(key), true, catalyst, true, 32, Set.of(pos.asLong()));
                manager.updateSnapshot(tower);
                for (int previous : new int[]{0, 9}) {
                    RadioSource source = new RadioSource(UUID.randomUUID(), location, new byte[]{1, 2, 3}, 1);
                    source.frequencingType = SimpleRadioFrequencing.TRANSCEIVER.id; source.frequencyMedium = frequency;
                    source.transmissionPower = 10; source.transmissionCap = 1800; source.activity = 42;
                    ((RelaySourceState) source).repeaters$setRelayCount(previous); double incomingNoise = source.computeSeverity();
                    transmitter.accept(source); deliveries++;
                    int count = previous + 1; double expected = NativeRadioAdapter.towerPower(tower, key) * Math.pow(CatalystRegistry.get(item).efficiency, count) - 1;
                    check(capture.count == deliveries && Math.abs(capture.last.transmissionPower - expected) < .01
                            && ((RelaySourceState) capture.last).repeaters$getRelayCount() == count,
                            "Actual receiver power must equal the catalyst-scaled departure minus its one-block final leg; count=" + count);
                    check(capture.last.computeSeverity() >= incomingNoise && capture.last.activity == 42 && capture.last.data == source.data,
                            "RF regeneration must preserve degraded audio, activity and encoded bytes");
                    RadioSource branch = capture.last.copy(); ((RelaySourceState) branch).repeaters$setRelayCount(40);
                    check(((RelaySourceState) capture.last).repeaters$getRelayCount() == count, "Branch copies must not share a mutable relay counter");
                }
            }
            check(!level.hasChunkAt(pos), "Routing and sub-one-percent delivery must not load the virtual tower chunk");
        } finally { frequency.getReceivers().remove(receiver); manager.removeAt(level.dimension().location().toString(), pos); }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_efficiency_change_invalidates_unloaded_tower_routes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); TowerManager manager = TowerManager.get(level.getServer());
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(Items.QUARTZ);
        Catalyst catalyst = CatalystRegistry.register(item, new Catalyst(Items.QUARTZ).setEfficiency(.6f));
        Frequency frequency = channel(314); ChannelKey key = ChannelKey.from(frequency);
        BlockPos pos = helper.absolutePos(new BlockPos(50000, 80, 50000)); UUID id = UUID.randomUUID();
        TowerRecord tower = new TowerRecord(id, level.dimension().location().toString(), pos, Set.of(key), true, item, true, 32, Set.of(pos.asLong()));
        manager.updateSnapshot(tower); var first = manager.network(level, key); float low = NativeRadioAdapter.departurePower(tower, key, 2);
        catalyst.setEfficiency(.9f); var second = manager.network(level, key);
        check(first != second && NativeRadioAdapter.departurePower(tower, key, 2) > low * 2
                && manager.snapshot(id).catalystItem().equals(item) && !level.hasChunkAt(pos),
                "Native registry changes must rebuild cached routes for unloaded towers without replacing their item identity or loading them");
        TowerManager.resourcesReloaded(level.getServer()); check(manager.network(level, key) != second, "Data-pack reload completion must also discard cached routes");
        TowerRecord empty = new TowerRecord(UUID.randomUUID(), tower.dimension(), pos, Set.of(key), true, null, true, 32, Set.of(pos.asLong()));
        check(TowerRecord.load(empty.save()).equals(empty), "An absent optional catalyst must remain absent after a save/load round trip");
        RelayCatalysts.updateClient(RelayCatalysts.items());
        check(RelayCatalysts.acceptsClient(new ItemStack(Items.AMETHYST_BLOCK)) && !RelayCatalysts.acceptsClient(new ItemStack(Items.DIRT)),
                "Client prediction must use the server catalog, including amethyst and excluding unrelated blocks");
        RelayCatalysts.updateClient(List.of());
        manager.removeAt(tower.dimension(), pos); helper.succeed();
    }
    private static final class Capture extends RadioRouter {
        int count; RadioSource last;
        Capture(WorldlyPosition location) { super(location); }
        @Override public void take(Source source) { count++; last = (RadioSource) source; }
    }
}
