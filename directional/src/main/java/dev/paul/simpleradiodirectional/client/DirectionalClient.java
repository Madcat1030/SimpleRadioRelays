package dev.paul.simpleradiodirectional.client;

import dev.paul.simpleradiodirectional.DirectionalAntenna;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import dev.paul.simpleradiodirectional.radio.DishAim;
import net.minecraft.client.renderer.RenderType;

public final class DirectionalClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(DirectionalAntenna.DISH, RenderType.cutout());
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, context) -> {
            var id = context.id();
            String prefix = "block/parabolic_receiver_aim_";
            if (model == null || !id.getNamespace().equals(DirectionalAntenna.ID) || !id.getPath().startsWith(prefix)) return model;
            String suffix = id.getPath().substring(prefix.length());
            if (suffix.length() != 1 || suffix.charAt(0) < '0' || suffix.charAt(0) > '6') return model;
            int aim = suffix.charAt(0) - '0';
            return aim == DishAim.STRAIGHT ? model : new RotatedDishModel(model, aim);
        }));
    }
}
