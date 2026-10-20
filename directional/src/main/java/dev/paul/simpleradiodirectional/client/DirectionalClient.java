package dev.paul.simpleradiodirectional.client;

import dev.paul.simpleradiodirectional.DirectionalAntenna;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;

public final class DirectionalClient implements ClientModInitializer {
    @Override public void onInitializeClient() { BlockRenderLayerMap.INSTANCE.putBlock(DirectionalAntenna.DISH, RenderType.cutout()); }
}
