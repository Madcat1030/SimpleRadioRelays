package dev.paul.simpleradiodirectional.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.*;
import com.codinglitch.simpleradio.core.registry.blocks.*;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import dev.paul.simpleradiodirectional.DirectionalAntenna;
import dev.paul.simpleradiodirectional.block.*;
import dev.paul.simpleradiodirectional.radio.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

public final class WideArrayGameTests implements FabricGameTest {
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    private static BlockPos setup(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos core = helper.absolutePos(new BlockPos(1, 1, 2));
        level.setBlockAndUpdate(core, Blocks.IRON_BLOCK.defaultBlockState());
        for (int y = 1; y <= 3; y++) level.setBlockAndUpdate(core.above(y), SimpleRadioBlocks.ANTENNA.defaultBlockState()
                .setValue(AntennaBlock.AXIS, Direction.Axis.Y).setValue(Antennal.UP, y < 3)
                .setValue(Antennal.DOWN, true).setValue(Antennal.ATTACHED, y == 1).setValue(AntennaBlock.COLUMN, true));
        BlockPos center = core.above(2).north();
        level.setBlockAndUpdate(center, DirectionalAntenna.ARRAY.defaultBlockState());
        DishGeometry.build(level, center, Direction.NORTH);
        return core;
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void wide_array_is_a_real_aimable_assembly_and_drops_the_correct_item(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos core = setup(helper), center = core.above(2).north();
        check(DishGeometry.validate(level, center, Direction.NORTH) == DishGeometry.Validity.VALID, "Wide array must validate on the native mast");
        for (int part = 0; part < 9; part++) if (part != 4)
            check(level.getBlockState(DishGeometry.cell(center, Direction.NORTH, part)).is(DirectionalAntenna.ARRAY_PART),
                    "Wide arrays must reserve their own parts and loot identity");
        var player = helper.makeMockPlayer(); BlockPos edge = DishGeometry.cell(center, Direction.NORTH, 0);
        var state = level.getBlockState(edge);
        DirectionalAntenna.ARRAY_PART.use(state, level, edge, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(edge.getCenter(), Direction.NORTH, edge, false));
        check(level.getBlockState(center).getValue(ParabolicReceiverBlock.AIM) == 4, "Clicking a grille must swivel the center");
        var saved = new Dish(level.dimension().location(), center, Direction.NORTH, 4, DishPattern.WIDE);
        check(Dish.load(saved.save()).equals(saved), "Saved arrays must preserve their kind and bearing");
        var old = new Dish(level.dimension().location(), center, Direction.NORTH).save(); old.remove("Pattern");
        check(Dish.load(old).pattern() == DishPattern.PARABOLIC, "Existing parabolic records must retain their original gain");
        var bounds = new AABB(center).inflate(4);
        long before = level.getEntitiesOfClass(ItemEntity.class, bounds).stream().filter(e -> e.getItem().is(DirectionalAntenna.ARRAY.asItem())).count();
        level.destroyBlock(edge, true);
        long after = level.getEntitiesOfClass(ItemEntity.class, bounds).stream().filter(e -> e.getItem().is(DirectionalAntenna.ARRAY.asItem())).count();
        check(after == before + 1, "Breaking one grille must drop exactly one wide array");
        for (int part = 0; part < 9; part++) check(level.getBlockState(DishGeometry.cell(center, Direction.NORTH, part)).isAir(),
                "Breaking the grille must remove its complete assembly");
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void placed_array_applies_half_the_dish_bonus_to_native_reception(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos core = setup(helper), center = core.above(2).north();
        DishBlockEntity.tick(level, center, level.getBlockState(center), (DishBlockEntity) level.getBlockEntity(center));
        BlockPos receiverPos = core.east();
        level.setBlockAndUpdate(receiverPos, SimpleRadioBlocks.RECEIVER.defaultBlockState().setValue(ReceiverBlock.FACING, Direction.EAST));
        var block = (ReceiverBlockEntity) level.getBlockEntity(receiverPos);
        var frequency = ServerSimpleRadioApi.getInstance().frequencies().getOrCreate("089.19", Frequency.Modulation.AMPLITUDE);
        var receiver = new RadioReceiver(frequency, WorldlyPosition.of(receiverPos, level)).frequencingType(SimpleRadioFrequencing.RECEIVER);
        receiver.antennaPower = 32; block.receiver = receiver; block.frequency = frequency; block.catalyzed = true;
        block.catalyst = CatalystRegistry.get(Items.GILDED_BLACKSTONE); block.active = true;
        DishManager.refreshReceiver(level, block);
        var from = WorldlyPosition.of(core.north(2000), level);
        double base = receiver.getPower();
        double fullDishBonus = DirectionalMath.bonus(base, SimpleRadioFrequencing.TRANSMITTER.getTransmissionPower(frequency.getModulation())
                + 32 * SimpleRadioFrequencing.TRANSMITTER.antennaAptitude, SimpleRadioFrequencing.TRANSMITTER.transmissionDiminishment, 1);
        double got = DirectionalReception.reception((float) base, receiver, from, frequency.getModulation());
        check(Math.abs(got - (base + fullDishBonus * .5)) < .01, "Physical arrays must publish their weaker pattern to native RF hooks");
        check(receiver.getPower() == base, "Directional gain must remain contextual and leave ordinary reception intact");
        check(DirectionalReception.reception((float) base, receiver, WorldlyPosition.of(core.south(2000), level), frequency.getModulation()) == base,
                "The rear of an array must retain only ordinary reception");
        helper.succeed();
    }
}
