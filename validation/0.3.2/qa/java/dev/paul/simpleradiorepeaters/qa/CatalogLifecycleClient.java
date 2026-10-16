package dev.paul.simpleradiorepeaters.qa;

import dev.paul.simpleradiorepeaters.radio.RelayCatalysts;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Temporary QA mod; never included in the release. Exercises actual Fabric play connections. */
public final class CatalogLifecycleClient implements ClientModInitializer {
    private int stage, ticks, disconnectedAt;
    private Object oldConnection;
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static boolean amethyst() { return RelayCatalysts.acceptsClient(new ItemStack(Items.AMETHYST_BLOCK)); }
    private static boolean custom() { return RelayCatalysts.acceptsClient(new ItemStack(Items.ENDER_PEARL)); }
    private static void marker(String name) {
        System.out.println("CATALOG_LIFECYCLE_QA " + name);
        try { Files.writeString(Path.of(System.getenv("REPEATERS_QA_ROOT"), name + ".ready"), name); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static void connect(Minecraft client) {
        String address = "127.0.0.1:25573";
        ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                new ServerData("Local catalyst QA", address, false), false);
    }
    private static void disconnect(Minecraft client) {
        // Match the normal pause-menu action: closing the level view alone does not close its connection.
        client.level.disconnect(); client.clearLevel(); client.setScreen(new TitleScreen());
    }
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks > 3600) throw new IllegalStateException("Catalog lifecycle QA timed out at stage " + stage);
            if (stage == 0 && client.getOverlay() == null && ticks > 20) {
                check(!RelayCatalysts.clientReady(), "A fresh client must wait for its server's catalog");
                stage = 1; connect(client);
            } else if (stage == 1 && client.level != null && RelayCatalysts.clientReady()) {
                check(amethyst() && !custom(), "First join must receive native defaults and no custom catalyst");
                oldConnection = client.getConnection(); marker("join-one"); stage = 2;
            } else if (stage == 2 && custom()) {
                check(amethyst(), "Reload must retain the default catalyst alongside the added one");
                marker("reload"); disconnect(client);
                disconnectedAt = ticks; stage = 3;
            } else if (stage == 3 && ticks - disconnectedAt > 20) {
                check(!RelayCatalysts.clientReady() && !amethyst() && !custom(), "Disconnect must clear the previous catalog and readiness");
                marker("disconnect"); stage = 4; connect(client);
            } else if (stage == 4 && client.level != null && RelayCatalysts.clientReady()) {
                check(client.getConnection() != oldConnection && amethyst() && custom(), "Reconnect must get the current server catalog");
                check(!RelayCatalysts.updateClient(oldConnection, List.of(BuiltInRegistries.ITEM.getKey(Items.DIRT))),
                        "A queued previous-session packet must be rejected");
                RelayCatalysts.endClient(oldConnection);
                check(RelayCatalysts.clientReady() && amethyst() && custom()
                                && !RelayCatalysts.acceptsClient(new ItemStack(Items.DIRT)),
                        "A stale disconnect must not clear or replace the current session");
                marker("reconnect-and-stale-callbacks"); disconnect(client);
                disconnectedAt = ticks; stage = 5;
            } else if (stage == 5 && ticks - disconnectedAt > 20) {
                check(!RelayCatalysts.clientReady() && !amethyst() && !custom(), "Final disconnect must clear readiness and items");
                marker("pass"); stage = 6; client.stop();
            }
        });
    }
}
