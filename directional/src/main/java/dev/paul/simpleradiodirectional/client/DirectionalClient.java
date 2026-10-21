package dev.paul.simpleradiodirectional.client;

import dev.paul.simpleradiodirectional.DirectionalAntenna;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import dev.paul.simpleradiodirectional.radio.DishAim;
import net.minecraft.client.renderer.RenderType;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public final class DirectionalClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ResourceManagerHelper.registerBuiltinResourcePack(DirectionalAntenna.id("mesh_parabolic"),
                FabricLoader.getInstance().getModContainer(DirectionalAntenna.ID).orElseThrow(),
                Component.literal("Simple Radio: Stepped Mesh Receiver"), ResourcePackActivationType.NORMAL);
        ResourceManagerHelper.registerBuiltinResourcePack(DirectionalAntenna.id("refined_parabolic"),
                FabricLoader.getInstance().getModContainer(DirectionalAntenna.ID).orElseThrow(),
                Component.literal("Simple Radio: Refined Parabolic Receiver"), ResourcePackActivationType.NORMAL);
        ResourceManagerHelper.registerBuiltinResourcePack(DirectionalAntenna.id("octagonal_parabolic"),
                FabricLoader.getInstance().getModContainer(DirectionalAntenna.ID).orElseThrow(),
                Component.literal("Simple Radio: Octagonal Parabolic Receiver"), ResourcePackActivationType.NORMAL);
        BlockRenderLayerMap.INSTANCE.putBlock(DirectionalAntenna.DISH, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(DirectionalAntenna.ARRAY, RenderType.cutout());
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, context) -> {
            var id = context.id();
            if (model == null || !id.getNamespace().equals(DirectionalAntenna.ID)) return model;
            String prefix = id.getPath().startsWith("block/wide_angle_receiver_aim_")
                    ? "block/wide_angle_receiver_aim_" : "block/parabolic_receiver_aim_";
            if (!id.getPath().startsWith(prefix)) return model;
            String suffix = id.getPath().substring(prefix.length());
            if (suffix.length() != 1 || suffix.charAt(0) < '0' || suffix.charAt(0) > '6') return model;
            int aim = suffix.charAt(0) - '0';
            return aim == DishAim.STRAIGHT ? model : new RotatedDishModel(model, aim);
        }));
    }
}
