package dev.paul.simpleradiorepeaters.radio;

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
    public record Site(ServerLevel level, Vec3 position, boolean projected) {}
    public record Leg(Site from, Site to, double distance, boolean dimensional) {}
    private RadioSpace() {}
    public static Site physical(ServerLevel level, Vec3 position) { return new Site(level, position, false); }
    public static List<Site> sites(MinecraftServer server, TowerRecord tower, boolean nativeCrossDimensional) {
        ResourceLocation id = ResourceLocation.tryParse(tower.dimension());
        ServerLevel home = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (home == null) return List.of();
        Site physical = physical(home, tower.position().getCenter());
        if (nativeCrossDimensional) return List.of(physical);
        ServerLevel target = tower.attachment() == RelayAttachment.NETHER_FOCUS ? server.getLevel(Level.NETHER)
                : tower.attachment() == RelayAttachment.END_FOCUS ? server.getLevel(Level.END) : null;
        if (target == null || target == home) return List.of(physical);
        double scale = home.dimensionType().coordinateScale() / target.dimensionType().coordinateScale();
        double y = target.getMinBuildHeight() + (tower.attachment() == RelayAttachment.NETHER_FOCUS
                ? target.dimensionType().logicalHeight() : 0);
        Vec3 anchor = new Vec3(physical.position().x * scale, y, physical.position().z * scale);
        return List.of(physical, new Site(target, anchor, true));
    }
    @Nullable public static Leg between(Site from, Site to, boolean nativeCrossDimensional) {
        // Two virtual pockets cannot act as a free interdimensional backbone. A physical hub must receive and repeat.
        if (from.projected() && to.projected()) return null;
        if (from.level() != to.level() && !nativeCrossDimensional) return null;
        double distance = from.position().scale(from.level().dimensionType().coordinateScale())
                .distanceTo(to.position().scale(to.level().dimensionType().coordinateScale()));
        boolean dimensional = from.projected() || to.projected() || from.level().dimensionType() != to.level().dimensionType();
        return new Leg(from, to, distance, dimensional);
    }
    public static List<Leg> between(List<Site> from, List<Site> to, boolean nativeCrossDimensional) {
        List<Leg> result = new ArrayList<>();
        for (Site a : from) for (Site b : to) { Leg leg = between(a, b, nativeCrossDimensional); if (leg != null) result.add(leg); }
        return List.copyOf(result);
    }
}
