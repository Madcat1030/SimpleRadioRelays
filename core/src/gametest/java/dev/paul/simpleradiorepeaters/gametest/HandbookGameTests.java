package dev.paul.simpleradiorepeaters.gametest;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Dedicated-server coverage: registered recipe and read action never require client book classes. */
public final class HandbookGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void handbook_recipe_and_server_use_preserve_the_item(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().byKey(SimpleRadioRepeaters.id("handbook")).orElseThrow();
        if (!recipe.getResultItem(level.registryAccess()).is(SimpleRadioRepeaters.HANDBOOK)
                || recipe.getIngredients().size() != 2
                || recipe.getIngredients().stream().noneMatch(i -> i.test(new ItemStack(Items.BOOK))))
            throw new GameTestAssertException("Handbook must have its cheap book-and-wire recipe");
        var player = helper.makeMockPlayer(); var book = new ItemStack(SimpleRadioRepeaters.HANDBOOK);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        var read = SimpleRadioRepeaters.HANDBOOK.use(level, player, InteractionHand.MAIN_HAND);
        if (!read.getResult().consumesAction() || read.getObject() != book || book.getCount() != 1 || book.hasTag())
            throw new GameTestAssertException("Reading must keep the handbook and store no stale addon-dependent pages");
        helper.succeed();
    }
}
