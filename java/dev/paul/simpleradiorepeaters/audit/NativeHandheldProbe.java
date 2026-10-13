package dev.paul.simpleradiorepeaters.audit;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.SimpleRadioItems;
import com.codinglitch.simpleradio.core.registry.menus.RadiosmitherMenu;
import com.codinglitch.simpleradio.radio.RadioManager;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/** Diagnostic assertion of existing native tuning behavior; deliberately contains no addon imports. */
public final class NativeHandheldProbe implements FabricGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void quick_bench_retune_leaves_native_registration_on_old_channel(GameTestHelper helper) {
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        String oldNumber = frequencies.incrementFrequency(frequencies.defaultFrequency(), 330);
        String newNumber = frequencies.incrementFrequency(frequencies.defaultFrequency(), 331);
        Player player = helper.makeMockPlayer(); player.setUUID(UUID.randomUUID());
        var item = SimpleRadioItems.WALKIE_TALKIE;
        ItemStack stack = new ItemStack(item);
        item.setFrequency(stack, oldNumber, Frequency.Modulation.AMPLITUDE);
        player.getInventory().setItem(0, stack); item.entityTick(stack, player);
        UUID id = stack.getTag().getUUID("reference");
        try {
            Frequency oldChannel = frequencies.get(oldNumber, Frequency.Modulation.AMPLITUDE);
            RadioReceiver original = (RadioReceiver) oldChannel.getReceiver(id);
            check(original != null, "Original native receiver must exist");
            SimpleContainer bench = new SimpleContainer(1);
            player.getInventory().setItem(0, ItemStack.EMPTY); bench.setItem(0, stack);
            new RadiosmitherMenu(98, player.getInventory(), bench).updateTinkering(newNumber, Frequency.Modulation.AMPLITUDE);
            player.getInventory().setItem(0, bench.removeItemNoUpdate(0));
            // Retune and reclaim before the next once-per-second native garbage-collection pass.
            item.entityTick(stack, player);
            RadioManager.garbageCollect();
            item.entityTick(stack, player);
            Frequency newChannel = frequencies.get(newNumber, Frequency.Modulation.AMPLITUDE);
            check(stack.getTag().getString("frequency").equals(newNumber), "Bench must update the visible item tuning");
            check(oldChannel.getReceiver(id) == original && original.valid,
                    "Native item activation must reproduce the stale old-channel registration");
            check(newChannel == null || newChannel.getReceiver(id) == null,
                    "Native activation unexpectedly repaired the quick-retune condition");
            LoggerFactory.getLogger("handheld-native-audit").info(
                    "Confirmed quick-retune mismatch: item tag {}; active receiver {}. Probe contains no addon calls.",
                    newNumber, original.frequency.getFrequency());
            // Keeping the item out of the inventory across a native collection pass recreates its routers.
            player.getInventory().setItem(0, ItemStack.EMPTY); bench.setItem(0, stack);
            RadioManager.garbageCollect();
            player.getInventory().setItem(0, bench.removeItemNoUpdate(0));
            item.entityTick(stack, player);
            newChannel = frequencies.get(newNumber, Frequency.Modulation.AMPLITUDE);
            check(newChannel != null && newChannel.getReceiver(id) != null,
                    "Native recreation after a normal bench delay must register the new tuning");
        } finally {
            player.getInventory().clearContent(); player.discard();
            item.inactivate(helper.getLevel(), oldNumber, Frequency.Modulation.AMPLITUDE, id);
            item.inactivate(helper.getLevel(), newNumber, Frequency.Modulation.AMPLITUDE, id);
            RadioManager.garbageCollect();
        }
        helper.succeed();
    }
}
