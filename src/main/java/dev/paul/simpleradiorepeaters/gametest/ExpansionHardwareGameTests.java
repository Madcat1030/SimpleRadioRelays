package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks;
import com.codinglitch.simpleradio.central.Frequency;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.RelayAttachment;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

public class ExpansionHardwareGameTests implements FabricGameTest {
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    private static ItemStack card(int offset) {
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        ItemStack card = new ItemStack(SimpleRadioRepeaters.CARD);
        SimpleRadioRepeaters.CARD.setFrequency(card, frequencies.incrementFrequency(frequencies.defaultFrequency(), offset), Frequency.Modulation.AMPLITUDE);
        return card;
    }
    private static BlockHitResult hit(BlockPos pos, Direction facing, double x, double y) {
        Vec3 local = switch (facing) {
            case EAST -> new Vec3(1, y / 16, x / 16);
            case SOUTH -> new Vec3(1 - x / 16, y / 16, 1);
            case WEST -> new Vec3(0, y / 16, 1 - x / 16);
            default -> new Vec3(x / 16, y / 16, 0);
        };
        return new BlockHitResult(local.add(pos.getX(), pos.getY(), pos.getZ()), facing, pos, false);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void transponder_controls_and_relay_sockets_target_the_visible_parts_in_all_facings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(); player.getAbilities().instabuild = false; var hand = InteractionHand.MAIN_HAND;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            level.setBlock(pos, SimpleRadioRepeaters.TRANSPONDER.defaultBlockState().setValue(ChannelBankBlock.FACING, facing), 3);
            TransponderBlockEntity module = (TransponderBlockEntity) level.getBlockEntity(pos);
            check(module.getContainerSize() == 3 && !module.transponderChannels().ready(), "The transponder must start with three empty physical slots");
            for (int slot : new int[]{2, 0, 1}) {
                ItemStack held = card(450 + slot); player.setItemInHand(hand, held);
                SimpleRadioRepeaters.TRANSPONDER.use(module.getBlockState(), level, pos, player, hand,
                        hit(pos, facing, TransponderBlock.slotX(slot) + 2, TransponderBlock.slotY(slot) + 3.5));
                check(held.isEmpty() && ItemStack.isSameItemSameTags(module.getItem(slot), card(450 + slot)),
                        "Clicks in the one-pixel band above a card must address that slot in every facing");
            }
            check(module.transponderChannels().ready(), "A tuned left/right pair must enable directed ports");
            ItemStack extra = card(454); player.setItemInHand(hand, extra); CompoundTag before = module.saveWithoutMetadata();
            SimpleRadioRepeaters.TRANSPONDER.use(module.getBlockState(), level, pos, player, hand, hit(pos, facing.getOpposite(), 8, 8));
            check(extra.getCount() == 1 && before.equals(module.saveWithoutMetadata()), "Card clicks on the back must do nothing");
            player.setItemInHand(hand, ItemStack.EMPTY);
            SimpleRadioRepeaters.TRANSPONDER.use(module.getBlockState(), level, pos, player, hand, hit(pos, facing, 8, 8));
            check(module.duplex() && module.getItem(0).getCount() == 1 && module.getItem(1).getCount() == 1,
                    "Clicking the center display must toggle direction without removing any card");
            // A card's boundary belongs to the card rather than the adjacent display.
            SimpleRadioRepeaters.TRANSPONDER.use(module.getBlockState(), level, pos, player, hand, hit(pos, facing, 10, 8));
            check(module.duplex() && module.getItem(0).isEmpty() && !module.transponderChannels().ready(),
                    "The left-card edge must eject the card and leave the mode unchanged");
            player.setItemInHand(hand, ItemStack.EMPTY);
            SimpleRadioRepeaters.TRANSPONDER.use(module.getBlockState(), level, pos, player, hand, hit(pos, facing, 8, 1));
            check(!module.getItem(1).isEmpty(), "An empty front click outside controls must not eject cards");
            var restored = new TransponderBlockEntity(pos, module.getBlockState()); restored.load(module.saveWithoutMetadata());
            check(restored.duplex() && restored.transponderChannels().equals(module.transponderChannels())
                    && restored.displayCards().size() == 3, "Card positions, mode and cached labels must survive NBT loading");
            level.setBlock(pos, SimpleRadioRepeaters.RELAY.defaultBlockState().setValue(RelayBlock.FACING, facing), 3);
            RelayBlockEntity relay = (RelayBlockEntity) level.getBlockEntity(pos);
            relay.installCatalyst(new ItemStack(Items.AMETHYST_BLOCK), false);
            for (Item item : List.of(SimpleRadioRepeaters.NETHER_FOCUS, SimpleRadioRepeaters.END_FOCUS, SimpleRadioRepeaters.RELAY_AMPLIFIER)) {
                ItemStack held = new ItemStack(item, 2); player.setItemInHand(hand, held);
                SimpleRadioRepeaters.RELAY.use(relay.getBlockState(), level, pos, player, hand, hit(pos, facing, 12, 4));
                check(held.getCount() == 1 && relay.attachment().is(item) && relay.hasCatalyst(), "The left socket must insert one attachment without disturbing the catalyst");
                SimpleRadioRepeaters.RELAY.use(relay.getBlockState(), level, pos, player, hand, hit(pos, facing, 12, 4));
                check(held.getCount() == 1, "A filled attachment socket must not consume a second item");
                RelayBlockEntity reloaded = new RelayBlockEntity(pos, relay.getBlockState()); reloaded.load(relay.saveWithoutMetadata());
                check(reloaded.attachment().is(item) && reloaded.hasCatalyst(), "Both socket items must persist together");
                player.setItemInHand(hand, ItemStack.EMPTY);
                SimpleRadioRepeaters.RELAY.use(relay.getBlockState(), level, pos, player, hand, hit(pos, facing, 12, 4));
                check(relay.attachment().isEmpty() && relay.hasCatalyst() && player.getInventory().contains(new ItemStack(item)),
                        "An empty-hand socket click must return only its attachment");
            }
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void transponder_edits_reconcile_with_an_unloaded_relay_and_survive_pending_entity_load(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2)), pos = core.north();
        level.setBlock(core, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(core.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState(), 3);
        level.setBlock(pos, SimpleRadioRepeaters.RELAY.defaultBlockState(), 3);
        level.setBlock(pos.above(), SimpleRadioRepeaters.TRANSPONDER.defaultBlockState(), 3);
        RelayBlockEntity relay = (RelayBlockEntity) level.getBlockEntity(pos);
        TransponderBlockEntity module = (TransponderBlockEntity) level.getBlockEntity(pos.above());
        module.setItem(0, card(460)); relay.installCatalyst(new ItemStack(Items.GILDED_BLACKSTONE), false);
        relay.installAttachment(new ItemStack(SimpleRadioRepeaters.END_FOCUS), false); relay.bankChanged();
        helper.runAfterDelay(35, () -> {
            TowerManager manager = TowerManager.get(level.getServer());
            check(!manager.isEligible(relay.relayId()), "One card must not activate a transponder");
            module.setItem(1, card(461)); relay.bankChanged();
            check(manager.isEligible(relay.relayId()), "A tuned pair and real antenna must activate it");
            CompoundTag oldRelay = relay.saveWithoutMetadata(); level.getChunkAt(pos).removeBlockEntity(pos);
            module.setDuplex(true); module.setItem(2, card(462));
            TowerRecord latest = manager.snapshot(relay.relayId());
            check(latest.transponder().duplex() && latest.transponder().rightBottom() != null && latest.attachment() == RelayAttachment.END_FOCUS,
                    "A loaded module must update its unloaded Relay's mode and ordered ports without losing its focus");
            check(TowerRecord.load(latest.save()).equals(latest), "The saved network snapshot must retain attachment and directional channels");
            CompoundTag latestModule = module.saveWithoutMetadata(); level.getChunkAt(pos.above()).removeBlockEntity(pos.above());
            RelayBlockEntity restored = new RelayBlockEntity(pos, relay.getBlockState()); restored.load(oldRelay); level.setBlockEntity(restored);
            helper.runAfterDelay(5, () -> {
                check(manager.snapshot(restored.relayId()).transponder().equals(latest.transponder()),
                        "Older Relay NBT must not overwrite edits while the module entity is still loading");
                var restoredModule = new TransponderBlockEntity(pos.above(), module.getBlockState()); restoredModule.load(latestModule); level.setBlockEntity(restoredModule);
                helper.runAfterDelay(8, () -> {
                    check(manager.isEligible(restored.relayId()) && restored.findAssignedBank() == restoredModule
                                    && manager.snapshot(restored.relayId()).transponder().equals(latest.transponder()),
                            "Restoring the transponder entity must retain its latest configuration and verified Relay");
                    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                    check(!manager.isEligible(restored.relayId()), "Removing the transponder must immediately disable its Relay");
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); helper.succeed();
                });
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void expansion_recipes_load_against_native_module_and_wire_ingredients(GameTestHelper helper) {
        var level = helper.getLevel();
        for (var pair : Map.of("nether_focus", SimpleRadioRepeaters.NETHER_FOCUS, "end_focus", SimpleRadioRepeaters.END_FOCUS,
                "relay_amplifier", SimpleRadioRepeaters.RELAY_AMPLIFIER, "transponder", SimpleRadioRepeaters.TRANSPONDER.asItem()).entrySet()) {
            var recipe = level.getRecipeManager().byKey(new ResourceLocation(SimpleRadioRepeaters.ID, pair.getKey())).orElseThrow();
            check(recipe.getResultItem(level.registryAccess()).is(pair.getValue()), "Each new recipe must resolve its native ingredients and return its registered item");
        }
        helper.succeed();
    }
}
