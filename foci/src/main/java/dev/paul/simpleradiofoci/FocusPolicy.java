package dev.paul.simpleradiofoci;
import dev.paul.simpleradiorepeaters.radio.*;
import dev.paul.simpleradiorepeaters.network.TowerRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Focus-only dimensional access, independent of Simple Radio's global dimensional switch. */
public final class FocusPolicy implements RadioPolicy {
    @Override public boolean nativeCrossDimensional(boolean configured) { return false; }
    @Override public int fingerprint() { return FociConfig.INSTANCE.netherTweaks ? 1 : 0; }
    @Override public List<RadioSpace.Site> sites(MinecraftServer server, TowerRecord tower, RadioSpace.Site physical) {
        ServerLevel home = physical.level();
        physical = new RadioSpace.Site(home, physical.position(), false, FociConfig.INSTANCE.netherTweaks
                && home.dimension().equals(Level.NETHER) && tower.attachment() == DimensionalFoci.NETHER);
        ServerLevel target = null;
        if (home.dimension().equals(Level.OVERWORLD)) {
            if (tower.attachment() == DimensionalFoci.NETHER) target = server.getLevel(Level.NETHER);
            if (tower.attachment() == DimensionalFoci.END) target = server.getLevel(Level.END);
        } else if (home.dimension().equals(Level.END) && tower.attachment() == DimensionalFoci.END)
            target = server.getLevel(Level.OVERWORLD);
        if (target == null || target == home) return List.of(physical);
        double scale = home.dimensionType().coordinateScale() / target.dimensionType().coordinateScale();
        double y = target.dimension().equals(Level.NETHER) ? target.getMinBuildHeight() + target.dimensionType().logicalHeight()
                : target.dimension().equals(Level.END) ? target.getMinBuildHeight()
                : Math.max(target.getMinBuildHeight() + 1, Math.min(target.getMaxBuildHeight() - 1, physical.position().y));
        return List.of(physical, new RadioSpace.Site(target,
                new Vec3(physical.position().x * scale, y, physical.position().z * scale), true, false));
    }
    @Override public RadioPropagation.Profile propagation(RadioSpace.Site from, RadioSpace.Site to, boolean dimensional) {
        return FociConfig.INSTANCE.netherTweaks ? FocusPropagation.profile(
                from.level().dimension().equals(Level.NETHER), to.level().dimension().equals(Level.NETHER),
                dimensional, from.enhancedPropagation() || to.enhancedPropagation()) : RadioPropagation.NORMAL;
    }
}
