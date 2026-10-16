package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.central.Catalyst;
import com.codinglitch.simpleradio.core.registry.CatalystRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** Fixture-only registered strengths; every value still pays the universal relay-count decay. */
final class TestCatalysts {
    private TestCatalysts() {}
    static ResourceLocation neutral() {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(Items.NETHER_STAR);
        if (CatalystRegistry.get(Items.NETHER_STAR) == null)
            CatalystRegistry.register(item, new Catalyst(Items.NETHER_STAR).setEfficiency(1));
        return item;
    }
    static ResourceLocation boosted() {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(Items.ENDER_PEARL);
        if (CatalystRegistry.get(Items.ENDER_PEARL) == null)
            CatalystRegistry.register(item, new Catalyst(Items.ENDER_PEARL).setEfficiency(2));
        return item;
    }
}
