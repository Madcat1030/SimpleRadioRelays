package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.central.Catalyst;
import com.codinglitch.simpleradio.core.registry.CatalystRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** Fixture-only neutral catalyst isolates the existing near-tie carrier regressions from RF decay. */
final class TestCatalysts {
    private TestCatalysts() {}
    static ResourceLocation neutral() {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(Items.NETHER_STAR);
        if (CatalystRegistry.get(Items.NETHER_STAR) == null)
            CatalystRegistry.register(item, new Catalyst(Items.NETHER_STAR).setEfficiency(1));
        return item;
    }
}
