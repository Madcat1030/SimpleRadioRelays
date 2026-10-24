package dev.paul.simpleradiorepeaters;

import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.item.FrequencyCardItem;
import dev.paul.simpleradiorepeaters.item.RelayAttachmentItem;
import dev.paul.simpleradiorepeaters.item.HandbookItem;
import dev.paul.simpleradiorepeaters.radio.RelayAttachment;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import dev.paul.simpleradiorepeaters.radio.RelayCatalysts;
import dev.paul.simpleradiorepeaters.radio.RelayDiagnostics;
import dev.paul.simpleradiorepeaters.radio.RelayPerformance;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.commands.Commands.literal;

public final class SimpleRadioRepeaters implements ModInitializer {
    public static final String ID = "simpleradio_repeaters";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static final RelayBlock RELAY = new RelayBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final ChannelBankBlock CHANNEL_BANK = new ChannelBankBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final TransponderBlock TRANSPONDER = new TransponderBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final FrequencyCardItem CARD = new FrequencyCardItem(new Item.Properties().stacksTo(1));
    public static final HandbookItem HANDBOOK = new HandbookItem();
    public static final RelayAttachmentItem COPPER_AMPLIFIER = new RelayAttachmentItem(RelayAttachment.COPPER_AMPLIFIER);
    public static final RelayAttachmentItem REDSTONE_AMPLIFIER = new RelayAttachmentItem(RelayAttachment.REDSTONE_AMPLIFIER);
    public static final RelayAttachmentItem RELAY_AMPLIFIER = new RelayAttachmentItem(RelayAttachment.AMPLIFIER);
    public static BlockEntityType<RelayBlockEntity> RELAY_ENTITY;
    public static BlockEntityType<ChannelBankBlockEntity> CHANNEL_BANK_ENTITY;
    public static BlockEntityType<TransponderBlockEntity> TRANSPONDER_ENTITY;

    public static ResourceLocation id(String path) { return new ResourceLocation(ID, path); }
    public static final ResourceLocation CATALYST_CATALOG = id("catalysts");
    public static final ResourceLocation CATALYST_REQUEST = id("request_catalysts");
    private static void syncCatalysts(ServerPlayer player) {
        var packet = PacketByteBufs.create(); var items = RelayCatalysts.items(); packet.writeVarInt(items.size());
        items.forEach(packet::writeResourceLocation); ServerPlayNetworking.send(player, CATALYST_CATALOG, packet);
    }
    @Override public void onInitialize() {
        RepeatersConfig.load();
        dev.paul.simpleradiorepeaters.radio.RelayIntegrations.initialize();
        Registry.register(BuiltInRegistries.BLOCK, id("relay"), RELAY);
        Registry.register(BuiltInRegistries.ITEM, id("relay"), new BlockItem(RELAY, new Item.Properties()));
        Registry.register(BuiltInRegistries.BLOCK, id("channel_bank"), CHANNEL_BANK);
        Registry.register(BuiltInRegistries.ITEM, id("channel_bank"), new BlockItem(CHANNEL_BANK, new Item.Properties()));
        Registry.register(BuiltInRegistries.BLOCK, id("transponder"), TRANSPONDER);
        Registry.register(BuiltInRegistries.ITEM, id("transponder"), new BlockItem(TRANSPONDER, new Item.Properties()));
        Registry.register(BuiltInRegistries.ITEM, id("frequency_card"), CARD);
        Registry.register(BuiltInRegistries.ITEM, id("handbook"), HANDBOOK);
        Registry.register(BuiltInRegistries.ITEM, id("copper_amplifier"), COPPER_AMPLIFIER);
        Registry.register(BuiltInRegistries.ITEM, id("redstone_amplifier"), REDSTONE_AMPLIFIER);
        Registry.register(BuiltInRegistries.ITEM, id("relay_amplifier"), RELAY_AMPLIFIER);
        RELAY_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("relay"),
                FabricBlockEntityTypeBuilder.create(RelayBlockEntity::new, RELAY).build());
        CHANNEL_BANK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("channel_bank"),
                FabricBlockEntityTypeBuilder.create(ChannelBankBlockEntity::new, CHANNEL_BANK).build());
        TRANSPONDER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("transponder"),
                FabricBlockEntityTypeBuilder.create(TransponderBlockEntity::new, TRANSPONDER).build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("repeaters"), FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.simpleradio_repeaters"))
                .icon(() -> new ItemStack(RELAY))
                .displayItems((parameters, output) -> {
                    output.accept(HANDBOOK); output.accept(RELAY); output.accept(CHANNEL_BANK); output.accept(TRANSPONDER); output.accept(CARD);
                    output.accept(COPPER_AMPLIFIER); output.accept(REDSTONE_AMPLIFIER); output.accept(RELAY_AMPLIFIER);
                }).build());
        ServerLifecycleEvents.SERVER_STARTED.register(TowerManager::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(RelayPerformance::stopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(TowerManager::stop);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) { TowerManager.resourcesReloaded(server, "server_datapack_reload"); server.getPlayerList().getPlayers().forEach(SimpleRadioRepeaters::syncCatalysts); }
        });
        ServerPlayNetworking.registerGlobalReceiver(CATALYST_REQUEST, (server, player, handler, packet, sender) ->
                server.execute(() -> {
                    if (server.getPlayerList().getPlayer(player.getUUID()) == player && player.connection == handler)
                        syncCatalysts(player);
                }));
        ServerTickEvents.END_SERVER_TICK.register(TowerManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(RelayPerformance::tick);
        ServerChunkEvents.CHUNK_LOAD.register(TowerManager::chunkLoaded);
        ServerChunkEvents.CHUNK_UNLOAD.register(TowerManager::chunkUnloaded);
        ServerEntityEvents.ENTITY_LOAD.register(TowerManager::wireLoaded);
        ServerEntityEvents.ENTITY_UNLOAD.register(TowerManager::wireUnloaded);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(literal("radiorepeaters")
                .then(RelayPerformance.commands())
                .then(literal("status").executes(context -> {
                    TowerManager manager = TowerManager.get(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal(manager == null
                            ? "Repeater network is starting." : manager.summary()), false);
                    return 1;
                }))
                .then(literal("list").requires(source -> source.hasPermission(2)).executes(context -> {
                    TowerManager manager = TowerManager.get(context.getSource().getServer());
                    if (manager != null) for (String line : manager.describe())
                        context.getSource().sendSuccess(() -> Component.literal(line), false);
                    return 1;
                }))
                .then(literal("diagnose").requires(source -> source.hasPermission(2)).executes(context -> {
                    for (String line : RelayDiagnostics.describe(context.getSource().getPlayerOrException()))
                        LOGGER.info("[relay-diagnostic] {}", line);
                    context.getSource().sendSuccess(() -> Component.literal("Relay diagnostics written to logs/latest.log. No radios or relay settings were changed."), false);
                    return 1;
                }))
                .then(literal("refresh").requires(source -> source.hasPermission(2)).executes(context -> {
                    TowerManager manager = TowerManager.get(context.getSource().getServer());
                    if (manager != null) manager.refreshLoaded();
                    context.getSource().sendSuccess(() -> Component.literal("Checking saved relays and card banks in loaded chunks and refreshing loaded antennas."), false);
                    return 1;
                })));
        });
        LOGGER.info("Simple Radio Repeaters ready; persistent towers do not force-load chunks.");
    }
}
