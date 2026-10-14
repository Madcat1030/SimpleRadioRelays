package dev.paul.simpleradiorepeaters.client;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
public final class RepeatersClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlockEntityRendererRegistry.register(SimpleRadioRepeaters.RELAY_ENTITY, RelayRenderer::new);
        BlockEntityRendererRegistry.register(SimpleRadioRepeaters.CHANNEL_BANK_ENTITY, ChannelBankRenderer::new);
        ModelLoadingPlugin.register(context -> {
            context.addModels(RelayRenderer.CATALYST); context.addModels(ChannelBankRenderer.CARDS);
        });
    }
}
