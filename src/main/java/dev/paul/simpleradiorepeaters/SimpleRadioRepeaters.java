package dev.paul.simpleradiorepeaters;

import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.item.FrequencyCardItem;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import dev.paul.simpleradiorepeaters.radio.RelayCatalysts;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
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
    public static final FrequencyCardItem CARD = new FrequencyCardItem(new Item.Properties().stacksTo(1));
    public static BlockEntityType<RelayBlockEntity> RELAY_ENTITY;
    public static BlockEntityType<ChannelBankBlockEntity> CHANNEL_BANK_ENTITY;

    public static ResourceLocation id(String path) { return new ResourceLocation(ID, path); }
    public static final ResourceLocation CATALYST_CATALOG = id("catalysts");
    private static void syncCatalysts(ServerPlayer player) {
        var packet = PacketByteBufs.create(); var items = RelayCatalysts.items(); packet.writeVarInt(items.size());
        items.forEach(packet::writeResourceLocation); ServerPlayNetworking.send(player, CATALYST_CATALOG, packet);
    }
    @Override public void onInitialize() {
        RepeatersConfig.load();
        Registry.register(BuiltInRegistries.BLOCK, id("relay"), RELAY);
        Registry.register(BuiltInRegistries.ITEM, id("relay"), new BlockItem(RELAY, new Item.Properties()));
        Registry.register(BuiltInRegistries.BLOCK, id("channel_bank"), CHANNEL_BANK);
        Registry.register(BuiltInRegistries.ITEM, id("channel_bank"), new BlockItem(CHANNEL_BANK, new Item.Properties()));
        Registry.register(BuiltInRegistries.ITEM, id("frequency_card"), CARD);
        RELAY_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("relay"),
                FabricBlockEntityTypeBuilder.create(RelayBlockEntity::new, RELAY).build());
        CHANNEL_BANK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("channel_bank"),
                FabricBlockEntityTypeBuilder.create(ChannelBankBlockEntity::new, CHANNEL_BANK).build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("repeaters"), FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.simpleradio_repeaters"))
                .icon(() -> new ItemStack(RELAY))
                .displayItems((parameters, output) -> { output.accept(RELAY); output.accept(CHANNEL_BANK); output.accept(CARD); }).build());
        ServerLifecycleEvents.SERVER_STARTED.register(TowerManager::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(TowerManager::stop);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) { TowerManager.resourcesReloaded(server); server.getPlayerList().getPlayers().forEach(SimpleRadioRepeaters::syncCatalysts); }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncCatalysts(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(TowerManager::tick);
        ServerChunkEvents.CHUNK_LOAD.register(TowerManager::chunkLoaded);
        ServerChunkEvents.CHUNK_UNLOAD.register(TowerManager::chunkUnloaded);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
            dispatcher.register(literal("radiorepeaters")
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
                .then(literal("refresh").requires(source -> source.hasPermission(2)).executes(context -> {
                    TowerManager manager = TowerManager.get(context.getSource().getServer());
                    if (manager != null) manager.refreshLoaded();
                    context.getSource().sendSuccess(() -> Component.literal("Checking saved relays and card banks in loaded chunks and refreshing loaded antennas."), false);
                    return 1;
                }))));
        LOGGER.info("Simple Radio Repeaters ready; persistent towers do not force-load chunks.");
    }
}
