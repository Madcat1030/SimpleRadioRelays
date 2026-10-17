package dev.paul.simpleradiorepeaters.radio;

import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import dev.paul.simpleradiorepeaters.RepeatersConfig;
import dev.paul.simpleradiorepeaters.network.TowerRecord;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Numeric antenna sites, never block entities or chunk lookups. Focus pockets need a physical radio at the other end. */
public final class RadioSpace {
    public record Site(ServerLevel level, Vec3 position, boolean projected, boolean focusedNether) {}
    public record Leg(Site from, Site to, double distance, boolean dimensional, RadioPropagation.Profile propagation) {}
    private RadioSpace() {}
    public static Site physical(ServerLevel level, Vec3 position) { return new Site(level, position, false, false); }
    public static RadioPropagation.Profile nativePropagation(WorldlyPosition from, WorldlyPosition to) {
        // Wireless routing is server-authoritative. A client's local config never changes server range.
        if (from.level.isClientSide || !RepeatersConfig.INSTANCE.netherTweaksActive(
                SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional)) return RadioPropagation.NORMAL;
        return RadioPropagation.profile(from.level.dimension().equals(Level.NETHER), to.level.dimension().equals(Level.NETHER),
                from.level.dimensionType() != to.level.dimensionType(), false);
    }
    public static List<Site> sites(MinecraftServer server, TowerRecord tower, boolean nativeCrossDimensional) {
        ResourceLocation id = ResourceLocation.tryParse(tower.dimension());
        ServerLevel home = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (home == null) return List.of();
        Site physical = new Site(home, tower.position().getCenter(), false,
                RepeatersConfig.INSTANCE.netherTweaksActive(nativeCrossDimensional)
                        && home.dimension().equals(Level.NETHER) && tower.attachment() == RelayAttachment.NETHER_FOCUS);
        if (!RepeatersConfig.INSTANCE.interdimensionalRelaysActive(nativeCrossDimensional)) return List.of(physical);
        ServerLevel target = null;
        if (home.dimension().equals(Level.OVERWORLD)) {
            if (tower.attachment() == RelayAttachment.NETHER_FOCUS) target = server.getLevel(Level.NETHER);
            if (tower.attachment() == RelayAttachment.END_FOCUS) target = server.getLevel(Level.END);
        } else if (home.dimension().equals(Level.END) && tower.attachment() == RelayAttachment.END_FOCUS)
            target = server.getLevel(Level.OVERWORLD);
        if (target == null || target == home) return List.of(physical);
        double scale = home.dimensionType().coordinateScale() / target.dimensionType().coordinateScale();
        double y = target.dimension().equals(Level.NETHER) ? target.getMinBuildHeight() + target.dimensionType().logicalHeight()
                : target.dimension().equals(Level.END) ? target.getMinBuildHeight()
                : Math.max(target.getMinBuildHeight() + 1, Math.min(target.getMaxBuildHeight() - 1, physical.position().y));
        Vec3 anchor = new Vec3(physical.position().x * scale, y, physical.position().z * scale);
        return List.of(physical, new Site(target, anchor, true, false));
    }
    @Nullable public static Leg between(Site from, Site to, boolean nativeCrossDimensional) {
        // Two virtual pockets cannot act as a free interdimensional backbone. A physical hub must receive and repeat.
        if (from.projected() && to.projected()) return null;
        if (from.level() != to.level() && !nativeCrossDimensional) return null;
        double distance = from.position().scale(from.level().dimensionType().coordinateScale())
                .distanceTo(to.position().scale(to.level().dimensionType().coordinateScale()));
        boolean dimensional = from.projected() || to.projected() || from.level().dimensionType() != to.level().dimensionType();
        var propagation = RepeatersConfig.INSTANCE.netherTweaksActive(nativeCrossDimensional)
                ? RadioPropagation.profile(from.level().dimension().equals(Level.NETHER),
                    to.level().dimension().equals(Level.NETHER), dimensional, from.focusedNether() || to.focusedNether())
                : RadioPropagation.NORMAL;
        return new Leg(from, to, distance * propagation.distanceMultiplier(), dimensional, propagation);
    }
    public static List<Leg> between(List<Site> from, List<Site> to, boolean nativeCrossDimensional) {
        List<Leg> result = new ArrayList<>();
        for (Site a : from) for (Site b : to) { Leg leg = between(a, b, nativeCrossDimensional); if (leg != null) result.add(leg); }
        return List.copyOf(result);
    }
}
