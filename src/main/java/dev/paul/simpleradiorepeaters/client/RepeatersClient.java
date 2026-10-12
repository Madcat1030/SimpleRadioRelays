package dev.paul.simpleradiorepeaters.client;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
public final class RepeatersClient implements ClientModInitializer {
    @Override public void onInitializeClient() { MenuScreens.register(SimpleRadioRepeaters.BANK_MENU, RepeaterBankScreen::new); }
}
