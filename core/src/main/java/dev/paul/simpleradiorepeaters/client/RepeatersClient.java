package dev.paul.simpleradiorepeaters.client;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.radio.RelayCatalysts;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
public final class RepeatersClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        SimpleRadioRepeaters.HANDBOOK.setReader(HandbookBook::open);
        BlockEntityRendererRegistry.register(SimpleRadioRepeaters.RELAY_ENTITY, RelayRenderer::new);
        BlockEntityRendererRegistry.register(SimpleRadioRepeaters.CHANNEL_BANK_ENTITY, ChannelBankRenderer::new);
        BlockEntityRendererRegistry.register(SimpleRadioRepeaters.TRANSPONDER_ENTITY, TransponderRenderer::new);
        ModelLoadingPlugin.register(context -> {
            context.addModels(ChannelBankRenderer.CARDS);
            context.addModels(TransponderRenderer.CARDS);
        });
        ClientPlayNetworking.registerGlobalReceiver(SimpleRadioRepeaters.CATALYST_CATALOG, (client, handler, packet, sender) -> {
            int count = packet.readVarInt(); List<ResourceLocation> items = new ArrayList<>();
            for (int i = 0; i < count; i++) items.add(packet.readResourceLocation());
            client.execute(() -> {
                if (client.getConnection() == handler) RelayCatalysts.updateClient(handler, items);
            });
        });
        ClientPlayConnectionEvents.INIT.register((handler, client) -> RelayCatalysts.beginClient(handler));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                sender.sendPacket(SimpleRadioRepeaters.CATALYST_REQUEST, PacketByteBufs.empty()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RelayCatalysts.endClient(handler));
    }
}
