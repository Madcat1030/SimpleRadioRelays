package dev.paul.simpleradiorepeaters.client;

import net.fabricmc.api.ClientModInitializer;

public final class BankPreviewClient implements ClientModInitializer {
    @Override public void onInitializeClient() { CatalystPreview.install(); }
}
