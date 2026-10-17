package dev.paul.simpleradiorepeaters.qa;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.RelayBlock;
import dev.paul.simpleradiorepeaters.block.RelayBlockEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Temporary native client QA. Uses ordinary play packets against the production prototype server. */
public final class SocketVisualClient implements ClientModInitializer {
    private int stage, ticks, insertedAt, joinedAt;
    private static final BlockPos TARGET = new BlockPos(-2, 80, 0);
    private static void check(boolean result, String message) {
        if (!result) throw new IllegalStateException(message);
    }
    private static void marker(String name) {
        System.out.println("SOCKET_VISUAL_QA " + name);
        try { Files.writeString(Path.of(System.getenv("REPEATERS_QA_ROOT"), name + ".ready"), name); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static RelayBlockEntity relay(Minecraft client, int x) {
        return client.level.getBlockEntity(new BlockPos(x, 80, 0)) instanceof RelayBlockEntity relay ? relay : null;
    }
    private static void click(Minecraft client, int hotbar) {
        client.player.getInventory().selected = hotbar;
        var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atLowerCornerOf(TARGET).add(.25, .25, -.001), Direction.NORTH, TARGET, false));
        System.out.println("SOCKET_VISUAL_QA click hotbar=" + hotbar + " result=" + result);
    }
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks > 4000) throw new IllegalStateException("Socket visual QA timed out at stage " + stage);
            if (stage == 0 && client.getOverlay() == null && ticks > 20) {
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1600, 1000);
                client.options.guiScale().set(1); client.options.fov().set(55);
                client.options.renderDistance().set(3); client.options.bobView().set(false);
                client.options.hideGui = true; client.resizeDisplay();
                String address = "127.0.0.1:25578";
                stage = 1;
                ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                        new ServerData("Local socket QA", address, false), false);
            } else if (stage == 1 && client.player != null && client.level != null && relay(client, -2) != null
                    && relay(client, 0) != null && relay(client, 2) != null) {
                marker("joined"); joinedAt = ticks; stage = 2;
            } else if (stage == 2 && ticks - joinedAt > 40 && client.player.getInventory().getItem(0).is(Items.AMETHYST_BLOCK)
                    && client.player.getInventory().getItem(1).isEmpty()) {
                Item[] catalysts = {Items.GILDED_BLACKSTONE, Items.AMETHYST_BLOCK, Items.GILDED_BLACKSTONE};
                Item[] upgrades = {SimpleRadioRepeaters.NETHER_FOCUS, SimpleRadioRepeaters.END_FOCUS, SimpleRadioRepeaters.RELAY_AMPLIFIER};
                for (int i = 0; i < 3; i++) {
                    RelayBlockEntity relay = relay(client, i * 2 - 2);
                    check(relay.catalyst().is(catalysts[i]), "Initial chunk catalyst item mismatch at " + relay.getBlockPos());
                    check(relay.attachment().is(upgrades[i]), "Initial chunk upgrade item mismatch");
                    int inside = LevelRenderer.getLightColor(client.level, relay.getBlockPos());
                    int front = LevelRenderer.getLightColor(client.level,
                            relay.getBlockPos().relative(relay.getBlockState().getValue(RelayBlock.FACING)));
                    check(LightTexture.sky(front) > LightTexture.sky(inside), "Fixture must expose the dark-housing/light-front case");
                    System.out.println("SOCKET_VISUAL_QA light " + relay.getBlockPos() + " inside=" + inside + " front=" + front);
                }
                marker("initial-items-and-front-light"); click(client, 1); stage = 3;
            } else if (stage == 3 && relay(client, -2).catalyst().isEmpty()
                    && client.player.getInventory().contains(new ItemStack(Items.GILDED_BLACKSTONE))) {
                marker("blackstone-removed"); click(client, 0); stage = 4;
            } else if (stage == 4 && relay(client, -2).catalyst().is(Items.AMETHYST_BLOCK)) {
                check(relay(client, -2).attachment().is(SimpleRadioRepeaters.NETHER_FOCUS), "Catalyst swap must retain the upgrade");
                var amethyst = client.getItemRenderer().getModel(relay(client, -2).catalyst(), client.level, null, 0);
                var gilded = client.getItemRenderer().getModel(new ItemStack(Items.GILDED_BLACKSTONE), client.level, null, 0);
                check(amethyst != gilded, "Amethyst must not resolve to the blackstone model");
                check(amethyst.getParticleIcon().contents().name().getPath().equals("block/amethyst_block"), "Expected native amethyst texture");
                marker("amethyst-installed-and-model-resolved"); insertedAt = ticks; stage = 5;
            } else if (stage == 5 && ticks - insertedAt > 80 && client.screen == null) {
                stage = 6;
                Screenshot.grab(new File("."), "Relay-Sockets-0.4.4-In-World.png", client.getMainRenderTarget(), message -> {
                    System.out.println("SOCKET_VISUAL_QA " + message.getString()); marker("pass"); client.stop();
                });
            }
        });
    }
}
