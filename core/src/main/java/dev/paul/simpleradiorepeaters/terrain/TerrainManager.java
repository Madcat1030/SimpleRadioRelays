package dev.paul.simpleradiorepeaters.terrain;

import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.*;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.RadioSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Consumer;

/** Server-thread work queue: generator columns are never sampled on an audio routing call. */
public final class TerrainManager {
    private static TerrainManager current;
    private static final int MAX_PENDING = 128, MAX_DEVICE_LINKS = 512, MAX_RAYS = 2048;
    private record Ray(TerrainProfiles.Key path, double ay, double by) {}
    private record DeviceKey(UUID from, UUID to) {}
    private static final class DeviceState {
        Vec3 a, b;
        double loss = Double.POSITIVE_INFINITY;
        long used;
        boolean pending;
    }
    private final MinecraftServer server;
    private final TerrainProfiles profiles;
    private final Map<UUID, BlockPos> tips = new HashMap<>();
    private final Map<Ray, Double> rays = new LinkedHashMap<>(16, .75f, true);
    private final Map<DeviceKey, DeviceState> devices = new LinkedHashMap<>(16, .75f, true);
    private final Deque<Job> relayJobs = new ArrayDeque<>(), deviceJobs = new ArrayDeque<>();
    private final Set<TerrainProfiles.Key> queued = new HashSet<>(), failed = new LinkedHashSet<>();
    private final Map<TerrainProfiles.Key, int[]> completed = new LinkedHashMap<>();
    private int revision, settingsHash;
    private long sampledColumns, deviceColumns, sampleNanos, completedProfiles;
    private boolean changed, warned, deviceTurn = true;

    private TerrainManager(MinecraftServer server, Collection<TowerRecord> records) {
        this.server = server;
        profiles = server.overworld().getDataStorage().computeIfAbsent(TerrainProfiles::load, TerrainProfiles::new, TerrainProfiles.NAME);
        profiles.initialize(server.overworld().getSeed());
        records.forEach(t -> { if (t.antennaTip() != null && t.dimension().equals(Level.OVERWORLD.location().toString())) tips.put(t.id(), t.antennaTip()); });
        settingsHash = settingsHash();
    }
    public static void start(MinecraftServer server, Collection<TowerRecord> records) {
        if (current == null || current.server != server) current = new TerrainManager(server, records);
    }
    public static void stop(MinecraftServer server) {
        if (current != null && current.server == server) current = null;
    }
    private static boolean overworld(RadioSpace.Leg leg) {
        return !leg.dimensional() && !leg.from().projected() && !leg.to().projected()
                && leg.from().level() == leg.to().level() && leg.from().level().dimension().equals(Level.OVERWORLD);
    }
    public static double elevationMultiplier(TowerRecord tower) {
        var manager = current; var cfg = RepeatersConfig.INSTANCE.terrain;
        if (!cfg.mode.relayLinks() || manager == null || !tower.dimension().equals("minecraft:overworld")) return 1;
        BlockPos tip = manager.tips.get(tower.id());
        if (tip == null) return 1;
        return TerrainMath.elevationMultiplier(tip.getY() + 1.0, cfg.elevationReferenceY,
                cfg.elevationFullBonusY, cfg.maxElevationAntennaBonus);
    }
    private static Vec3 tip(TowerRecord tower, RadioSpace.Site fallback) {
        var manager = current; BlockPos tip = manager == null ? null : manager.tips.get(tower.id());
        return tip == null ? fallback.position() : new Vec3(tip.getX() + .5, tip.getY() + 1.0, tip.getZ() + .5);
    }

    /** Extra distance for the RF calculation; sampled terrain and clearance memo survive graph rebuilds. */
    public static double relayLoss(RadioSpace.Leg leg, TowerRecord from, TowerRecord to) {
        return relayLoss(leg, from, to, true);
    }
    public static double relayLoss(RadioSpace.Leg leg, TowerRecord from, TowerRecord to, boolean request) {
        var manager = current;
        if (manager == null || !RepeatersConfig.INSTANCE.terrain.mode.relayLinks() || !overworld(leg)) return 0;
        if (leg.distance() == 0) return 0;
        // Old saved towers have no verified tip until their normal loaded antenna scan runs.
        if (from.antennaTip() == null || to.antennaTip() == null) return 0;
        if (!manager.tips.containsKey(from.id()) || !manager.tips.containsKey(to.id())) return Double.POSITIVE_INFINITY;
        var a = tip(from, leg.from()); var b = tip(to, leg.to());
        var cfg = RepeatersConfig.INSTANCE.terrain;
        var key = TerrainProfiles.Key.between(Mth.floor(a.x), Mth.floor(a.z), Mth.floor(b.x), Mth.floor(b.z), cfg.sampleSpacing, cfg.maxSamplesPerPath);
        if (manager.failed.contains(key)) return 0;
        int[] heights = manager.profiles.get(key);
        if (heights == null) {
            if (request && manager.queued.size() < MAX_PENDING && manager.queued.add(key)) {
                Vec3 start = new Vec3(key.ax() + .5, 0, key.az() + .5), end = new Vec3(key.bx() + .5, 0, key.bz() + .5);
                manager.relayJobs.add(manager.new Job(leg.from().level(), start, end, true,
                        samples -> { manager.completed.put(key, samples); manager.changed = true; },
                        () -> {
                            manager.queued.remove(key); manager.failed.add(key);
                            if (manager.failed.size() > MAX_RAYS) manager.failed.remove(manager.failed.iterator().next());
                            manager.changed = true;
                        }));
            }
            return Double.POSITIVE_INFINITY;
        }
        boolean forward = key.startsAt(Mth.floor(a.x), Mth.floor(a.z));
        var ray = new Ray(key, forward ? a.y : b.y, forward ? b.y : a.y);
        Double fraction = manager.rays.get(ray);
        if (fraction == null) {
            fraction = TerrainMath.profileLoss(heights, ray.ay(), ray.by(), cfg.clearance, cfg.nearGroundLoss, cfg.buriedLoss);
            manager.rays.put(ray, fraction);
            trim(manager.rays, MAX_RAYS);
        }
        return leg.distance() * fraction;
    }

    public static double ingressLoss(RadioSpace.Leg leg, UUID sender, TowerRecord receiver) {
        if (!RepeatersConfig.INSTANCE.terrain.mode.pointToPoint()) return 0;
        return deviceLoss(leg, sender, receiver.id(), leg.from().position(), tip(receiver, leg.to()));
    }
    public static double egressLoss(RadioSpace.Leg leg, TowerRecord sender, RadioReceiver receiver) {
        if (!RepeatersConfig.INSTANCE.terrain.mode.pointToPoint()) return 0;
        return deviceLoss(leg, sender.id(), receiver.reference, tip(sender, leg.from()), leg.to().position());
    }
    /** Read-only diagnostics use observed device results, without creating sampling jobs. */
    public static double observedIngressLoss(RadioSpace.Leg leg, UUID sender, TowerRecord receiver) {
        return observedDeviceLoss(leg, sender, receiver.id(), leg.from().position(), tip(receiver, leg.to()));
    }
    public static double observedEgressLoss(RadioSpace.Leg leg, TowerRecord sender, RadioReceiver receiver) {
        return observedDeviceLoss(leg, sender.id(), receiver.reference, tip(sender, leg.from()), leg.to().position());
    }
    private static double observedDeviceLoss(RadioSpace.Leg leg, UUID from, UUID to, Vec3 a, Vec3 b) {
        var m = current;
        if (m == null || !RepeatersConfig.INSTANCE.terrain.mode.pointToPoint() || !overworld(leg) || TerrainMath.free(a.distanceToSqr(b))) return 0;
        var state = m.devices.get(new DeviceKey(from, to));
        return state == null ? Double.POSITIVE_INFINITY : leg.distance() * state.loss;
    }
    public static double nativeLoss(RadioTransmitter sender, RadioReceiver receiver) {
        if (!RepeatersConfig.INSTANCE.terrain.mode.pointToPoint()) return 0;
        var from = sender.getLocation(); var to = receiver.getLocation();
        if (from == null || to == null || !(from.level instanceof ServerLevel a) || to.level != a) return 0;
        var leg = RadioSpace.between(RadioSpace.physical(a, new Vec3(from)), RadioSpace.physical(a, new Vec3(to)), false);
        return leg == null ? 0 : deviceLoss(leg, sender.reference, receiver.reference, leg.from().position(), leg.to().position());
    }
    private static double deviceLoss(RadioSpace.Leg leg, UUID from, UUID to, Vec3 a, Vec3 b) {
        var manager = current;
        if (manager == null || !RepeatersConfig.INSTANCE.terrain.mode.pointToPoint() || !overworld(leg)
                || manager.server != leg.from().level().getServer() || !manager.server.isSameThread()) return 0;
        var key = new DeviceKey(from, to);
        // No state creation and no generator work anywhere inside the 16-chunk free radius.
        if (TerrainMath.free(a.distanceToSqr(b))) { manager.devices.remove(key); return 0; }
        var state = manager.devices.computeIfAbsent(key, ignored -> new DeviceState());
        state.used = manager.server.overworld().getGameTime();
        trim(manager.devices, MAX_DEVICE_LINKS);
        if (!state.pending && (state.a == null || TerrainMath.moved(a.distanceToSqr(state.a)) || TerrainMath.moved(b.distanceToSqr(state.b)))
                && manager.deviceJobs.size() < MAX_PENDING) {
            state.pending = true;
            var job = manager.new Job(leg.from().level(), a, b, false, null, null) {
                @Override boolean valid() { return manager.devices.get(key) == state; }
                @Override void finish() {
                    state.a = a; state.b = b; state.loss = sum / (count - 1); state.pending = false;
                }
                @Override void fail() { state.a = a; state.b = b; state.loss = 0; state.pending = false; }
            };
            manager.deviceJobs.add(job);
        }
        return leg.distance() * state.loss;
    }

    /** Runs after antenna block entities. Height changes and completed relay profiles publish only at tick % 200. */
    public static void tick(MinecraftServer server, Collection<TowerRecord> records) {
        var manager = current; if (manager == null || manager.server != server) return;
        long now = server.overworld().getGameTime();
        if (now % 200 == 0 && manager.settingsHash != settingsHash()) reconfigure(server);
        if (!RepeatersConfig.INSTANCE.terrain.mode.relayLinks()) return;
        if (now % 200 == 0) {
            Map<UUID, BlockPos> tips = new HashMap<>();
            records.forEach(t -> { if (t.antennaTip() != null && t.dimension().equals(Level.OVERWORLD.location().toString())) tips.put(t.id(), t.antennaTip()); });
            if (!tips.equals(manager.tips)) { manager.tips.clear(); manager.tips.putAll(tips); manager.rays.clear(); manager.changed = true; }
            manager.completed.forEach((key, heights) -> {
                manager.profiles.put(key, heights); manager.queued.remove(key); manager.completedProfiles++;
            });
            manager.completed.clear();
            if (manager.changed) {
                manager.changed = false; manager.revision++;
                TowerManager.resourcesReloaded(server, "terrain_profile_or_height_changed");
            }
        }
        if (now % 20 == 0) manager.devices.values().removeIf(state -> now - state.used > 600);
        long started = System.nanoTime(); var cfg = RepeatersConfig.INSTANCE.terrain;
        for (int i = 0; i < cfg.samplesPerTick && (i == 0 || System.nanoTime() - started < 2_000_000); i++) {
            boolean device = !manager.deviceJobs.isEmpty() && (manager.deviceTurn || manager.relayJobs.isEmpty());
            Deque<Job> queue = device ? manager.deviceJobs : manager.relayJobs;
            if (queue.isEmpty()) break;
            Job job = queue.peek();
            if (!job.valid()) { queue.remove(); i--; continue; }
            manager.deviceTurn = !device;
            if (job.step()) queue.remove();
        }
    }
    public static int engineHeight(ServerLevel level, int x, int z) {
        return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                level, level.getChunkSource().randomState());
    }
    private class Job {
        final ServerLevel level; final Vec3 a, b; final int count; final int[] heights;
        final Consumer<int[]> consumer; final Runnable failure;
        int index; double sum;
        Job(ServerLevel level, Vec3 a, Vec3 b, boolean relay, Consumer<int[]> consumer, Runnable failure) {
            this.level = level; this.a = a; this.b = b; this.consumer = consumer; this.failure = failure;
            var cfg = RepeatersConfig.INSTANCE.terrain;
            count = TerrainMath.sampleCount(Math.hypot(b.x - a.x, b.z - a.z), cfg.sampleSpacing, cfg.maxSamplesPerPath);
            heights = relay ? new int[count] : null;
        }
        boolean valid() { return true; }
        boolean step() {
            double t = (double) index / (count - 1); long started = System.nanoTime();
            try {
                int h = engineHeight(level, Mth.floor(TerrainMath.height(a.x, b.x, t)), Mth.floor(TerrainMath.height(a.z, b.z, t)));
                sampledColumns++; if (heights == null) deviceColumns++;
                if (heights != null) heights[index] = h;
                else {
                    var cfg = RepeatersConfig.INSTANCE.terrain;
                    sum += (index == 0 || index == count - 1 ? .5 : 1)
                            * TerrainMath.loss(TerrainMath.height(a.y, b.y, t) - h, cfg.clearance, cfg.nearGroundLoss, cfg.buriedLoss);
                }
                if (++index == count) { finish(); return true; }
            } catch (RuntimeException problem) {
                if (!warned) {
                    warned = true;
                    SimpleRadioRepeaters.LOGGER.warn("Terrain height sampling is unavailable for this generator; affected links keep ordinary propagation", problem);
                }
                fail(); return true;
            } finally { sampleNanos += System.nanoTime() - started; }
            return false;
        }
        void finish() { consumer.accept(heights); }
        void fail() { failure.run(); }
    }
    private static <K, V> void trim(Map<K, V> map, int limit) {
        while (map.size() > limit) map.remove(map.keySet().iterator().next());
    }
    private static int settingsHash() {
        var c = RepeatersConfig.INSTANCE.terrain;
        return Objects.hash(c.mode, c.sampleSpacing, c.maxSamplesPerPath,
                c.clearance, c.nearGroundLoss, c.buriedLoss, c.maxElevationAntennaBonus, c.elevationReferenceY, c.elevationFullBonusY);
    }
    public static int configurationStamp() { return current == null ? 0 : 31 * current.revision + current.settingsHash; }
    public static void reconfigure(MinecraftServer server) {
        var m = current; if (m == null || m.server != server) return;
        RepeatersConfig.INSTANCE.terrain.validate(); m.settingsHash = settingsHash();
        m.rays.clear(); m.devices.clear(); m.relayJobs.clear(); m.deviceJobs.clear(); m.queued.clear(); m.completed.clear(); m.failed.clear();
        m.changed = false; m.revision++; TowerManager.resourcesReloaded(server, "terrain_configuration_changed");
    }
    public static void clear(MinecraftServer server) {
        var m = current; if (m == null || m.server != server) return;
        m.profiles.clear(); reconfigure(server);
    }
    public static Map<String, Object> statistics() {
        var m = current; if (m == null) return Map.of();
        return Map.of("relay_profiles", m.profiles.size(), "pending_relay_profiles", m.queued.size(),
                "device_results", m.devices.size(), "pending_device_checks", m.deviceJobs.size(),
                "sampled_columns", m.sampledColumns, "device_columns", m.deviceColumns,
                "sampling_ms", m.sampleNanos / 1_000_000.0, "completed_relay_profiles", m.completedProfiles);
    }
}
