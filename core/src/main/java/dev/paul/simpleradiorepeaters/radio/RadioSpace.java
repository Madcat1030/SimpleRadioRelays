package dev.paul.simpleradiorepeaters.radio;

import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import dev.paul.simpleradiorepeaters.network.TowerRecord;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Terrain-free antenna geometry. Optional addons supply extra sites and propagation rules. */
public final class RadioSpace {
    public record Site(ServerLevel level, Vec3 position, boolean projected, boolean enhancedPropagation) {}
    public record Leg(Site from, Site to, double distance, boolean dimensional, RadioPropagation.Profile propagation) {}
    private static volatile RadioPolicy policy = RadioPolicy.NATIVE;
    private RadioSpace() {}
    public static void installPolicy(RadioPolicy value) {
        if (policy != RadioPolicy.NATIVE) throw new IllegalStateException("A radio propagation policy is already installed");
        policy = Objects.requireNonNull(value);
    }
    public static boolean nativeCrossDimensional() {
        return policy.nativeCrossDimensional(SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional);
    }
    public static int policyFingerprint() { return Objects.hash(policy.getClass().getName(), policy.fingerprint()); }
    public static Site physical(ServerLevel level, Vec3 position) { return new Site(level, position, false, false); }
    public static RadioPropagation.Profile nativePropagation(WorldlyPosition from, WorldlyPosition to) {
        if (!(from.level instanceof ServerLevel a) || !(to.level instanceof ServerLevel b)) return RadioPropagation.NORMAL;
        return policy.propagation(physical(a, new Vec3(from.x, from.y, from.z)), physical(b, new Vec3(to.x, to.y, to.z)), a.dimensionType() != b.dimensionType());
    }
    public static List<Site> sites(MinecraftServer server, TowerRecord tower, boolean nativeCrossDimensional) {
        ResourceLocation id = ResourceLocation.tryParse(tower.dimension());
        ServerLevel home = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        return home == null ? List.of() : List.copyOf(policy.sites(server, tower, physical(home, tower.antennaBase().getCenter())));
    }
    @Nullable public static Leg between(Site from, Site to, boolean nativeCrossDimensional) {
        if (from.projected() && to.projected()) return null;
        if (from.level() != to.level() && !policy.nativeCrossDimensional(nativeCrossDimensional)) return null;
        double distance = from.position().scale(from.level().dimensionType().coordinateScale())
                .distanceTo(to.position().scale(to.level().dimensionType().coordinateScale()));
        boolean dimensional = from.projected() || to.projected() || from.level().dimensionType() != to.level().dimensionType();
        var propagation = policy.propagation(from, to, dimensional);
        return new Leg(from, to, distance * propagation.distanceMultiplier(), dimensional, propagation);
    }
    public static List<Leg> between(List<Site> from, List<Site> to, boolean nativeCrossDimensional) {
        List<Leg> result = new ArrayList<>();
        for (Site a : from) for (Site b : to) { Leg leg = between(a, b, nativeCrossDimensional); if (leg != null) result.add(leg); }
        return List.copyOf(result);
    }
}
