package dev.paul.simpleradiofoci;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.item.RelayAttachmentItem;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import dev.paul.simpleradiorepeaters.radio.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.commands.Commands.*;
import com.mojang.brigadier.arguments.BoolArgumentType;

public final class DimensionalFoci implements ModInitializer {
    public static final String ID = "simpleradio_repeaters_foci";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static final RelayAttachment NETHER = RelayAttachment.register("NETHER_FOCUS", 1, false);
    public static final RelayAttachment END = RelayAttachment.register("END_FOCUS", 1, false);
    public static final RelayAttachmentItem NETHER_FOCUS = new RelayAttachmentItem(NETHER, "tooltip.simpleradio_repeaters.focus_policy");
    public static final RelayAttachmentItem END_FOCUS = new RelayAttachmentItem(END, "tooltip.simpleradio_repeaters.focus_policy");
    @Override public void onInitialize() {
        FociConfig.load();
        // Retain established item IDs; only this addon registers their items and resources.
        Registry.register(BuiltInRegistries.ITEM, SimpleRadioRepeaters.id("nether_focus"), NETHER_FOCUS);
        Registry.register(BuiltInRegistries.ITEM, SimpleRadioRepeaters.id("end_focus"), END_FOCUS);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new net.minecraft.resources.ResourceLocation(ID, "foci"),
                FabricItemGroup.builder().title(Component.translatable("itemGroup.simpleradio_repeaters_foci"))
                        .icon(() -> new ItemStack(END_FOCUS)).displayItems((parameters, output) -> {
                            output.accept(NETHER_FOCUS); output.accept(END_FOCUS);
                        }).build());
        RadioSpace.installPolicy(new FocusPolicy());
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var setting = literal("netherTweaks").executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("netherTweaks: " + FociConfig.INSTANCE.netherTweaks
                        + "; dimensional access is always focus-only while this addon is installed."), false); return 1;
            }).then(argument("enabled", BoolArgumentType.bool()).executes(context -> {
                boolean old = FociConfig.INSTANCE.netherTweaks;
                FociConfig.INSTANCE.netherTweaks = BoolArgumentType.getBool(context, "enabled");
                try { FociConfig.save(); }
                catch (java.io.IOException error) {
                    FociConfig.INSTANCE.netherTweaks = old;
                    context.getSource().sendFailure(Component.literal("Could not save foci settings; previous value retained.")); return 0;
                }
                TowerManager.resourcesReloaded(context.getSource().getServer());
                context.getSource().sendSuccess(() -> Component.literal("netherTweaks: " + FociConfig.INSTANCE.netherTweaks + ". Saved."), true); return 1;
            }));
            dispatcher.register(literal("radiofoci").requires(source -> source.hasPermission(2))
                    .then(literal("config").then(setting)));
        });
        LOGGER.info("Dimensional Foci active: focus-only dimensional radio overrides the native switch without rewriting it.");
    }
}
