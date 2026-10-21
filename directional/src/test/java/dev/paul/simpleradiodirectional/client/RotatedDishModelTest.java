package dev.paul.simpleradiodirectional.client;

import dev.paul.simpleradiodirectional.radio.DishAim;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RotatedDishModelTest {
    private static BakedQuad face(Direction normal) {
        int[] vertices = new int[32];
        for (int i=0;i<4;i++) {
            vertices[i*8] = Float.floatToRawIntBits(.5f + normal.getStepX());
            vertices[i*8+1] = Float.floatToRawIntBits(i < 2 ? .25f : .75f);
            vertices[i*8+2] = Float.floatToRawIntBits(.5f + normal.getStepZ());
            vertices[i*8+3] = 0xffabcdef;
            vertices[i*8+4] = Float.floatToRawIntBits(i/4f);
            vertices[i*8+5] = Float.floatToRawIntBits(1-i/4f);
        }
        return new BakedQuad(vertices, 2, normal, null, true);
    }
    private static final class Source implements BakedModel {
        final BakedQuad quad;
        int requests;
        Source(Direction normal) { quad=face(normal); }
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            requests++; return side==quad.getDirection() ? List.of(quad) : List.of();
        }
        public boolean useAmbientOcclusion() { return false; }
        public boolean isGui3d() { return true; }
        public boolean usesBlockLight() { return true; }
        public boolean isCustomRenderer() { return false; }
        public TextureAtlasSprite getParticleIcon() { return null; }
        public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
        public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
    @Test void bakedReflectorMatchesRadioBearingForAllTwentyEightStates() {
        for (var facing : Direction.Plane.HORIZONTAL) for(int aim=0;aim<=6;aim++) {
            var source=new Source(facing);
            var model=new RotatedDishModel(source,aim);
            var quad=model.getQuads(null,null,RandomSource.create(0)).get(0);
            double x=DishAim.x(facing.getStepX(),facing.getStepZ(),aim),z=DishAim.z(facing.getStepX(),facing.getStepZ(),aim);
            for(int i=0;i<4;i++) {
                assertEquals(.5+x,Float.intBitsToFloat(quad.getVertices()[i*8]),1e-6);
                assertEquals(.5+z,Float.intBitsToFloat(quad.getVertices()[i*8+2]),1e-6);
                for(int field : new int[]{1,3,4,5,6,7}) assertEquals(source.quad.getVertices()[i*8+field],quad.getVertices()[i*8+field]);
            }
            assertEquals(2,quad.getTintIndex()); assertTrue(quad.isShade());
            assertArrayEquals(face(facing).getVertices(),source.quad.getVertices(),"Baking a swivel must not mutate shared source geometry");
        }
    }
    @Test void renderRequestsReuseImmutableQuadsAndDoNotReadSourceAgain() {
        var source=new Source(Direction.NORTH);var model=new RotatedDishModel(source,6);
        var random=RandomSource.create(0);var quads=model.getQuads(null,null,random);int bakedCalls=source.requests;
        for(int i=0;i<100;i++) assertSame(quads,model.getQuads(null,null,random));
        assertEquals(bakedCalls,source.requests);
        for(var side:Direction.values()) assertTrue(model.getQuads(null,side,random).isEmpty());
        assertThrows(UnsupportedOperationException.class,()->quads.add(source.quad));
    }
}
