package dev.paul.simpleradiorepeaters.client;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.radio.RelayCatalysts;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
public final class RepeatersClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlockEntityRendererRegistry.register(SimpleRadioRepeaters.RELAY_ENTITY, RelayRenderer::new);
        BlockEntityRendererRegistry.register(SimpleRadioRepeaters.CHANNEL_BANK_ENTITY, ChannelBankRenderer::new);
        ModelLoadingPlugin.register(context -> {
            context.addModels(ChannelBankRenderer.CARDS);
        });
        ClientPlayNetworking.registerGlobalReceiver(SimpleRadioRepeaters.CATALYST_CATALOG, (client, handler, packet, sender) -> {
            int count = packet.readVarInt(); List<ResourceLocation> items = new ArrayList<>();
            for (int i = 0; i < count; i++) items.add(packet.readResourceLocation());
            client.execute(() -> RelayCatalysts.updateClient(items));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RelayCatalysts.updateClient(List.of()));
    }
}
