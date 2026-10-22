package dev.paul.simpleradiorepeaters.network;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import dev.paul.simpleradiorepeaters.radio.RelayPerformance;
import com.codinglitch.simpleradio.central.Antennal;
import com.codinglitch.simpleradio.core.registry.blocks.InsulatorBlockEntity;
import com.codinglitch.simpleradio.routers.Router;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs native antenna geometry against a guarded accessor that cannot request absent chunks. */
public final class AntennaScanner {
    public record Result(boolean complete, int score, Set<Long> footprint, BlockPos antennaBase) {}
    private static final class MissingChunk extends RuntimeException {
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }
    /** Historical antenna dependencies remain authoritative until all their data has restored. */
    public static boolean dependenciesAvailable(ServerLevel level, Set<Long> footprint) {
        Set<Long> chunks = new HashSet<>();
        for (long packed : footprint) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.hasChunkAt(pos)) return false;
            chunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
        for (long chunk : chunks) if (!level.areEntitiesLoaded(chunk)) return false;
        return true;
    }
    public static Result scan(ServerLevel level, BlockPos bank, BlockPos core) {
        long started = RelayPerformance.clock(level.getServer()); Result result = null;
        try { return result = scanObserved(level, bank, core); }
        finally { RelayPerformance.scanned(level.getServer(), started, result); }
    }
    private static Result scanObserved(ServerLevel level, BlockPos bank, BlockPos core) {
        Set<Long> observed = new HashSet<>(); observed.add(bank.asLong()); observed.add(core.asLong());
        LevelAccessor guarded = (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(), new Class<?>[]{LevelAccessor.class},
            (proxy, method, args) -> {
                // Match typed position arguments; reflection names differ after intermediary remapping.
                if (args != null && args.length > 0 && args[0] instanceof BlockPos pos) {
                    observed.add(pos.asLong());
                    if (!level.hasChunkAt(pos)) throw new MissingChunk();
                    if (BlockEntity.class.isAssignableFrom(method.getReturnType())
                            && !level.areEntitiesLoaded(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)))
                        throw new MissingChunk();
                }
                try { return method.invoke(level, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
        try {
            // Native antennas need a sturdy top face. A floating mast must not give a bare Relay a score.
            if (!guarded.getBlockState(core).isFaceSturdy(guarded, core, Direction.UP))
                return new Result(true, 0, Set.copyOf(observed), core.immutable());
            // A loaded insulator can still have no router, or an unresolved wire endpoint.
            // Neither establishes that the antenna extension was disconnected.
            for (Direction direction : Direction.values()) {
                BlockEntity entity = guarded.getBlockEntity(core.relative(direction));
                if (!(entity instanceof InsulatorBlockEntity insulator)) continue;
                Router router = insulator.getRouter();
                if (router == null || !router.isValid()) throw new MissingChunk();
                for (var wire : insulator.getWires()) {
                    if (!wire.isValid()) continue;
                    Router other = wire.transport(router);
                    if (other == null || !other.isValid()) throw new MissingChunk();
                }
            }
            BlockPos base = ServerSimpleRadioApi.getInstance().travelExtension(core, guarded).above();
            BlockPos antennaCore = base.below();
            if (!guarded.getBlockState(antennaCore).isFaceSturdy(guarded, antennaCore, Direction.UP))
                return new Result(true, 0, Set.copyOf(observed), antennaCore.immutable());
            BlockState state = guarded.getBlockState(base); int score = 0;
            if (state.getBlock() instanceof Antennal antennal) {
                AtomicInteger total = new AtomicInteger(); List<BlockPos> visited = new ArrayList<>();
                antennal.climbColumn(base.mutable(), guarded, total, 0, visited); score = total.get();
                visited.forEach(p -> observed.add(p.asLong()));
            }
            Set<Long> footprint = new HashSet<>(observed);
            for (long packed : observed) for (Direction direction : Direction.values()) footprint.add(BlockPos.of(packed).relative(direction).asLong());
            return new Result(true, score, Set.copyOf(footprint), antennaCore.immutable());
        } catch (MissingChunk e) { return new Result(false, 0, Set.copyOf(observed), core.immutable()); }
    }
}
