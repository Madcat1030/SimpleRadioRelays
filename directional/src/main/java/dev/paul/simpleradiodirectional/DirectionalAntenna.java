package dev.paul.simpleradiodirectional;

import dev.paul.simpleradiodirectional.block.*;
import dev.paul.simpleradiodirectional.radio.DishManager;
import dev.paul.simpleradiodirectional.radio.DishPattern;
import com.codinglitch.simpleradio.core.registry.blocks.ReceiverBlockEntity;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import java.util.List;

public final class DirectionalAntenna implements ModInitializer {
    public static final String ID = "simpleradio_directional";
    public static final ParabolicReceiverBlock DISH = new ParabolicReceiverBlock(properties());
    public static final ParabolicReceiverBlock ARRAY = new ParabolicReceiverBlock(properties(), DishPattern.WIDE);
    public static final DishPartBlock PART = new DishPartBlock(properties());
    public static final DishPartBlock ARRAY_PART = new DishPartBlock(properties(), DishPattern.WIDE);
    public static BlockEntityType<DishBlockEntity> DISH_ENTITY;
    public static ResourceLocation id(String name) { return new ResourceLocation(ID, name); }
    public static ParabolicReceiverBlock receiver(DishPattern pattern) { return pattern == DishPattern.WIDE ? ARRAY : DISH; }
    public static DishPartBlock part(DishPattern pattern) { return pattern == DishPattern.WIDE ? ARRAY_PART : PART; }
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK);
    }
    @Override public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, id("parabolic_receiver"), DISH);
        Registry.register(BuiltInRegistries.BLOCK, id("parabolic_part"), PART);
        Registry.register(BuiltInRegistries.BLOCK, id("wide_angle_receiver"), ARRAY);
        Registry.register(BuiltInRegistries.BLOCK, id("wide_angle_part"), ARRAY_PART);
        registerItem("parabolic_receiver", DISH, "pattern");
        registerItem("wide_angle_receiver", ARRAY, "wide_pattern");
        DISH_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("parabolic_receiver"),
                FabricBlockEntityTypeBuilder.create(DishBlockEntity::new, DISH, ARRAY).build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("components"), FabricItemGroup.builder()
                .icon(() -> new ItemStack(DISH)).title(Component.translatable("itemGroup.simpleradio_directional"))
                .displayItems((parameters,entries) -> { entries.accept(DISH); entries.accept(ARRAY); }).build());
        ServerLifecycleEvents.SERVER_STARTED.register(DishManager::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(DishManager::stop);
        ServerTickEvents.END_SERVER_TICK.register(DishManager::tick);
        ServerEntityEvents.ENTITY_LOAD.register(DishManager::wireLoaded);
        ServerEntityEvents.ENTITY_UNLOAD.register(DishManager::wireUnloaded);
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity,level) -> {
            if (entity instanceof ReceiverBlockEntity receiver) {
                if (receiver.receiver instanceof RadioReceiver router) DishManager.forget(router);
                else if (receiver.id != null) DishManager.forget(receiver.id);
            }
        });
    }
    private static void registerItem(String name, ParabolicReceiverBlock block, String patternKey) {
        Registry.register(BuiltInRegistries.ITEM, id(name), new BlockItem(block, new Item.Properties()) {
            @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
                lines.add(Component.translatable("tooltip.simpleradio_directional.mount").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.simpleradio_directional." + patternKey).withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("tooltip.simpleradio_directional.aim").withStyle(ChatFormatting.GRAY));
            }
        });
    }
}
