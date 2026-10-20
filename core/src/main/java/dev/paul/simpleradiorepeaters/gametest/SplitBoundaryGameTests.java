package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.*;
import dev.paul.simpleradiorepeaters.radio.*;
import dev.paul.simpleradiorepeaters.network.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Runs with core alone and with the optional addon to exercise the actual module boundary. */
public class SplitBoundaryGameTests implements FabricGameTest {
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void optional_items_and_saved_projections_follow_the_installed_module(GameTestHelper helper) {
        boolean addon = FabricLoader.getInstance().isModLoaded("simpleradio_repeaters_foci");
        var level = helper.getLevel();
        for (String name : List.of("nether_focus", "end_focus")) {
            check(BuiltInRegistries.ITEM.containsKey(SimpleRadioRepeaters.id(name)) == addon, "Focus item registration must belong only to its addon");
            check(level.getRecipeManager().byKey(SimpleRadioRepeaters.id(name)).isPresent() == addon, "Focus recipes must belong only to their addon");
        }
        var channel = new ChannelKey("000.01", "AM");
        var record = new TowerRecord(UUID.randomUUID(), level.dimension().location().toString(), new BlockPos(6000000,80,6000000),
                Set.of(channel), true, RelayCatalysts.GILDED, true, 32, Set.of(), null);
        var tag = record.save(); tag.putString("Attachment", "END_FOCUS");
        var loaded = TowerRecord.load(tag);
        check(RadioSpace.sites(level.getServer(), loaded, false).size() == (addon ? 2 : 1),
                "Removing the addon must not retain a saved focus projection");
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void native_wireless_dimensional_behavior_matches_the_installation(GameTestHelper helper) {
        var overworld = helper.getLevel(); var nether = overworld.getServer().getLevel(Level.NETHER);
        boolean addon = FabricLoader.getInstance().isModLoaded("simpleradio_repeaters_foci");
        boolean before = SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional;
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        var frequency = frequencies.getOrCreate("092.17", Frequency.Modulation.AMPLITUDE);
        var from = new WorldlyPosition(400000,64,400000,overworld);
        var to = new WorldlyPosition(50000,8,50000,nether);
        try {
            for (boolean enabled : new boolean[]{false,true,false}) {
                SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional = enabled;
                var sender = new RadioTransmitter(frequency, from).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
                var receiver = new RadioReceiver(frequency, to).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
                var source = new RadioSource(UUID.randomUUID(), from, new byte[]{1}, 1);
                source.frequencingType = SimpleRadioFrequencing.TRANSCEIVER.id; source.transmissionPower = 2000; source.transmissionCap = 2000;
                var received = sender.prepareSource(source, receiver);
                check((received.transmissionPower > 0) == (enabled && !addon),
                        "Core must preserve native ON/OFF; Foci must block unassisted crossings for both settings");
                check(RadioSpace.nativeCrossDimensional() == (enabled && !addon), "Relay graph and native endpoint policies must agree");
                check(SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional == enabled, "Neither module may rewrite the native setting");
            }
        } finally { SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional = before; }
        helper.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void amplifier_tiers_keep_their_saved_effects_and_only_ender_resets_decay(GameTestHelper helper) {
        var items = List.of(SimpleRadioRepeaters.COPPER_AMPLIFIER, SimpleRadioRepeaters.REDSTONE_AMPLIFIER, SimpleRadioRepeaters.RELAY_AMPLIFIER);
        double[] gains = {1.25,1.5,1.75};
        for (int i = 0; i < items.size(); i++) {
            var effect = RelayAttachment.of(new ItemStack(items.get(i)));
            check(effect.antennaMultiplier() == gains[i] && effect.resetsDecay() == (i == 2), "Amplifier tiers must have distinct gain and decay behavior");
            check(RelayAttachment.stored(effect.name()) == effect, "Saved attachment identity must resolve to the registered effect");
        }
        helper.succeed();
    }
}
