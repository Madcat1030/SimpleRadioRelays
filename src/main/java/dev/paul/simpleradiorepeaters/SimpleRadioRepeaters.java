package dev.paul.simpleradiorepeaters;

import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.item.FrequencyCardItem;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import dev.paul.simpleradiorepeaters.screen.RepeaterBankMenu;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
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
    public static final RepeaterBankBlock BANK = new RepeaterBankBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final FrequencyCardItem CARD = new FrequencyCardItem(new Item.Properties().stacksTo(1));
    public static BlockEntityType<RepeaterBankBlockEntity> BANK_ENTITY;
    public static MenuType<RepeaterBankMenu> BANK_MENU;

    public static ResourceLocation id(String path) { return new ResourceLocation(ID, path); }
    @Override public void onInitialize() {
        RepeatersConfig.load();
        Registry.register(BuiltInRegistries.BLOCK, id("repeater_bank"), BANK);
        Registry.register(BuiltInRegistries.ITEM, id("repeater_bank"), new BlockItem(BANK, new Item.Properties()));
        Registry.register(BuiltInRegistries.ITEM, id("frequency_card"), CARD);
        BANK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("repeater_bank"),
                FabricBlockEntityTypeBuilder.create(RepeaterBankBlockEntity::new, BANK).build());
        BANK_MENU = Registry.register(BuiltInRegistries.MENU, id("repeater_bank"),
                new MenuType<>(RepeaterBankMenu::new, FeatureFlags.VANILLA_SET));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("repeaters"), FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.simpleradio_repeaters"))
                .icon(() -> new ItemStack(BANK))
                .displayItems((parameters, output) -> { output.accept(BANK); output.accept(CARD); }).build());
        ServerLifecycleEvents.SERVER_STARTED.register(TowerManager::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(TowerManager::stop);
        ServerTickEvents.END_SERVER_TICK.register(TowerManager::tick);
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
                    context.getSource().sendSuccess(() -> Component.literal("Refreshing loaded repeater banks."), false);
                    return 1;
                }))));
        LOGGER.info("Simple Radio Repeaters ready; persistent towers do not force-load chunks.");
    }
}
