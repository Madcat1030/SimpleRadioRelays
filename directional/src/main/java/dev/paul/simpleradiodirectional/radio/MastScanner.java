package dev.paul.simpleradiodirectional.radio;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.Antennal;
import com.codinglitch.simpleradio.core.registry.blocks.InsulatorBlockEntity;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Main-thread-only native geometry, guarded before every position read. Null means retry after load. */
public final class MastScanner {
    private static final class Incomplete extends RuntimeException {
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }
    private MastScanner() {}
    public static boolean dependenciesAvailable(ServerLevel level, Set<Long> masts) {
        for (long packed : masts) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.hasChunkAt(pos) || !level.areEntitiesLoaded(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) return false;
        }
        return true;
    }
    public static Set<Long> scan(ServerLevel level, BlockPos core) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Mast scans require the server thread");
        Set<Long> observed = new HashSet<>();
        LevelAccessor safe = (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(), new Class<?>[]{LevelAccessor.class},
                (proxy, method, args) -> {
                    if (args != null && args.length > 0 && args[0] instanceof BlockPos pos) {
                        if (!level.hasChunkAt(pos) || observed.size() >= 4096) throw new Incomplete();
                        observed.add(pos.asLong());
                        if (BlockEntity.class.isAssignableFrom(method.getReturnType())
                                && !level.areEntitiesLoaded(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) throw new Incomplete();
                    }
                    try { return method.invoke(level, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
        try {
            for (Direction side : Direction.values()) {
                var entity = safe.getBlockEntity(core.relative(side));
                if (!(entity instanceof InsulatorBlockEntity insulator)) continue;
                var router = insulator.getRouter();
                if (router == null || !router.isValid()) throw new Incomplete();
                for (var wire : insulator.getWires()) {
                    if (!wire.isValid()) continue;
                    var other = wire.transport(router);
                    if (other == null || !other.isValid()) throw new Incomplete();
                }
            }
            BlockPos base = ServerSimpleRadioApi.getInstance().travelExtension(core, safe).above();
            var state = safe.getBlockState(base);
            if (!(state.getBlock() instanceof Antennal antenna)) return Set.of();
            List<BlockPos> visited = new ArrayList<>();
            antenna.climbColumn(base.mutable(), safe, new AtomicInteger(), 0, visited);
            Set<Long> result = new HashSet<>(); visited.forEach(p -> result.add(p.asLong()));
            return Set.copyOf(result);
        } catch (Incomplete e) { return null; }
    }
}
