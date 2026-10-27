package dev.paul.simpleradiorepeaters.terrain;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.paul.simpleradiorepeaters.RepeatersConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import static net.minecraft.commands.Commands.*;

public final class TerrainCommands {
    private TerrainCommands() {}
    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        var root = literal("terrain").requires(s -> s.hasPermission(2)).executes(c -> status(c.getSource()));
        for (TerrainMode mode : TerrainMode.values()) root.then(literal(mode.id()).executes(c -> select(c, mode)));
        return root.then(literal("reload").executes(c -> {
                    boolean previousBalance = RepeatersConfig.INSTANCE.antennaDependentBasePower;
                    if (!RepeatersConfig.load()) {
                        c.getSource().sendFailure(Component.literal("Could not reload simpleradio-repeaters.json. Current settings were kept; see latest.log for details."));
                        return 0;
                    }
                    TerrainManager.reconfigure(c.getSource().getServer());
                    if (RepeatersConfig.usedFallbackCurve())
                        c.getSource().sendSuccess(() -> Component.translatable("message.simpleradio_repeaters.terrain_curve_fallback")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW), false);
                    if (previousBalance != RepeatersConfig.INSTANCE.antennaDependentBasePower)
                        dev.paul.simpleradiorepeaters.network.TowerManager.resourcesReloaded(
                                c.getSource().getServer(), "antenna_balance_changed");
                    return status(c.getSource());
                }))
                .then(literal("clear").executes(c -> {
                    TerrainManager.clear(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Remapping relay terrain. Existing links keep their previous result until replacement samples are ready."), false);
                    return 1;
                }));
    }
    private static int select(CommandContext<CommandSourceStack> context, TerrainMode mode) {
        var cfg = RepeatersConfig.INSTANCE.terrain; TerrainMode old = cfg.mode;
        cfg.mode = mode;
        try { RepeatersConfig.save(); }
        catch (IOException e) {
            cfg.mode = old;
            context.getSource().sendFailure(Component.literal("Could not save terrain settings: " + e.getMessage())); return 0;
        }
        TerrainManager.reconfigure(context.getSource().getServer()); return status(context.getSource());
    }
    private static int status(CommandSourceStack source) {
        var cfg = RepeatersConfig.INSTANCE.terrain;
        source.sendSuccess(() -> Component.literal("Overworld terrain mode=" + cfg.mode.id() + "; " + TerrainManager.statistics()), false);
        return 1;
    }
}
