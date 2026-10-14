package dev.paul.simpleradiorepeaters.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Loaded-chunk-only component lookup. Attachment never loads neighbouring terrain. */
public final class BankConnection {
    public static final Direction[] RELAY_PRIORITY = {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};
    public static final Direction[] BANK_PRIORITY = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN};
    private BankConnection() {}
    @Nullable public static LevelChunk chunk(Level level, BlockPos pos) {
        return level instanceof ServerLevel server ? server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) : null;
    }
    @Nullable public static BlockState state(Level level, BlockPos pos) {
        LevelChunk chunk = chunk(level, pos); return chunk == null ? null : chunk.getBlockState(pos);
    }
    @Nullable public static BlockEntity entity(Level level, BlockPos pos) {
        LevelChunk chunk = chunk(level, pos);
        if (chunk == null || !((ServerLevel) level).areEntitiesLoaded(chunk.getPos().toLong())) return null;
        return chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
    }
    /** Coordinates in the model's north-facing frame, shared by card hit regions and render placement. */
    public static Vec3 localHit(BlockPos pos, Direction facing, Vec3 hit) {
        double x = hit.x - pos.getX(), y = hit.y - pos.getY(), z = hit.z - pos.getZ();
        return switch (facing) {
            case EAST -> new Vec3(z, y, 1 - x);
            case SOUTH -> new Vec3(1 - x, y, 1 - z);
            case WEST -> new Vec3(1 - z, y, x);
            default -> new Vec3(x, y, z);
        };
    }
    public static double slotX(int slot) { return slot % 2 == 0 ? 9 : 1; }
    public static double slotY(int slot) { return 11 - (slot / 2) * 5; }
    public static int cardAt(Vec3 local) {
        double x = local.x * 16, y = local.y * 16;
        for (int slot = 0; slot < 6; slot++)
            if (x >= slotX(slot) && x <= slotX(slot) + 6 && y >= slotY(slot) && y <= slotY(slot) + 4) return slot;
        return -1;
    }
}
