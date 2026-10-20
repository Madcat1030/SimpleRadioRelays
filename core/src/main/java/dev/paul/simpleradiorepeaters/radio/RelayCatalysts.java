package dev.paul.simpleradiorepeaters.radio;

import com.codinglitch.simpleradio.central.Catalyst;
import com.codinglitch.simpleradio.core.registry.CatalystRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import java.util.Comparator;
import java.util.Collection;
import java.util.List;

/** The native server registry owns accepted items and efficiencies; saved towers retain item identity. */
public final class RelayCatalysts {
    public static final ResourceLocation GILDED = new ResourceLocation("minecraft", "gilded_blackstone");
    private static final ClientSessionCatalog<ResourceLocation> CLIENT = new ClientSessionCatalog<>();
    private RelayCatalysts() {}
    @Nullable public static ResourceLocation item(ItemStack stack) { return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()); }
    @Nullable private static Catalyst get(@Nullable ResourceLocation item) {
        return item == null || !BuiltInRegistries.ITEM.containsKey(item) ? null : CatalystRegistry.get(BuiltInRegistries.ITEM.get(item));
    }
    public static boolean accepts(ItemStack stack) { return registered(item(stack)); }
    public static boolean acceptsClient(ItemStack stack) { return !stack.isEmpty() && CLIENT.contains(item(stack)); }
    public static boolean clientReady() { return CLIENT.ready(); }
    public static void beginClient(Object connection) { CLIENT.begin(connection); }
    public static boolean updateClient(Object connection, Collection<ResourceLocation> items) { return CLIENT.update(connection, items); }
    public static void endClient(Object connection) { CLIENT.end(connection); }
    public static List<ResourceLocation> items() {
        return CatalystRegistry.getCatalysts().stream().filter(c -> registered(BuiltInRegistries.ITEM.getKey(c.associate)))
                .map(c -> BuiltInRegistries.ITEM.getKey(c.associate)).distinct().sorted().toList();
    }
    public static boolean registered(@Nullable ResourceLocation item) {
        Catalyst catalyst = get(item);
        return catalyst != null && Float.isFinite(catalyst.efficiency) && catalyst.efficiency > 0;
    }
    public static double efficiency(@Nullable ResourceLocation item) {
        Catalyst catalyst = get(item);
        return catalyst != null && Float.isFinite(catalyst.efficiency) && catalyst.efficiency > 0 ? catalyst.efficiency : 0;
    }
    public static int configurationStamp() {
        int result = 1;
        for (Catalyst catalyst : CatalystRegistry.getCatalysts().stream()
                .sorted(Comparator.comparing(c -> c.location.toString())).toList())
            result = 31 * result + java.util.Objects.hash(catalyst.location,
                    BuiltInRegistries.ITEM.getKey(catalyst.associate), catalyst.efficiency);
        return result;
    }
}
