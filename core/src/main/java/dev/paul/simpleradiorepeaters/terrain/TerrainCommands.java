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
                    RepeatersConfig.load(); TerrainManager.reconfigure(c.getSource().getServer()); return status(c.getSource());
                }))
                .then(literal("clear").executes(c -> {
                    TerrainManager.clear(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Cleared saved relay terrain profiles. Active links will be sampled again."), false);
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
