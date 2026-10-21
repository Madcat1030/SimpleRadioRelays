package dev.paul.simpleradiodirectional.client;

import dev.paul.simpleradiodirectional.radio.DishAim;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Reflector geometry is transformed once per resource bake, never per frame or voice packet. */
final class RotatedDishModel implements BakedModel {
    private final BakedModel original;
    private final List<BakedQuad> quads;
    RotatedDishModel(BakedModel original, int aim) {
        this.original = original;
        var baked = new ArrayList<BakedQuad>();
        var random = RandomSource.create(0);
        for (var quad : original.getQuads(null, null, random)) baked.add(rotate(quad, aim));
        // Angled faces must not be culled against an unrelated axis-aligned neighboring block.
        for (var side : Direction.values()) {
            random.setSeed(0);
            for (var quad : original.getQuads(null, side, random)) baked.add(rotate(quad, aim));
        }
        quads = List.copyOf(baked);
    }
    private static BakedQuad rotate(BakedQuad quad, int aim) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int i = 0; i < vertices.length; i += stride) {
            double x = Float.intBitsToFloat(vertices[i]) - 0.5;
            double z = Float.intBitsToFloat(vertices[i + 2]) - 0.5;
            vertices[i] = Float.floatToRawIntBits((float) (0.5 + DishAim.x(x, z, aim)));
            vertices[i + 2] = Float.floatToRawIntBits((float) (0.5 + DishAim.z(x, z, aim)));
        }
        var face = quad.getDirection();
        var direction = Direction.getNearest((float) DishAim.x(face.getStepX(), face.getStepZ(), aim),
                face.getStepY(), (float) DishAim.z(face.getStepX(), face.getStepZ(), aim));
        return new BakedQuad(vertices, quad.getTintIndex(), direction, quad.getSprite(), quad.isShade());
    }
    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return side == null ? quads : List.of();
    }
    @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return original.isGui3d(); }
    @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return original.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return original.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return original.getOverrides(); }
}
