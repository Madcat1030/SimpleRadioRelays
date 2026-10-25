package dev.paul.simpleradiorepeaters.terrain;

import com.codinglitch.simpleradio.central.FrequencingType;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.*;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
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
    private static final int MAX_PENDING = 128, MAX_DEVICE_LINKS = 16_384, MAX_RAYS = 2048;
    private record Ray(TerrainProfiles.Key path, double ay, double by) {}
    private record DeviceKey(UUID from, UUID to) {}
    private record RelayPair(UUID a, UUID b) {
        static RelayPair of(UUID a, UUID b) { return a.compareTo(b) <= 0 ? new RelayPair(a, b) : new RelayPair(b, a); }
        boolean contains(UUID id) { return a.equals(id) || b.equals(id); }
    }
    private static final class RelayState {
        double loss = Double.NaN;
        TerrainProfiles.Key pending;
        boolean waitingForTip;
    }
    private static final class DeviceState {
        Vec3 a, b;
        double loss = Double.POSITIVE_INFINITY;
        long used;
        boolean pending;
    }
    private final MinecraftServer server;
    private final TerrainProfiles profiles;
    private final Map<UUID, BlockPos> tips = new HashMap<>();
    private final Map<RelayPair, RelayState> relayStates = new HashMap<>();
    private final Map<Ray, Double> rays = new LinkedHashMap<>(16, .75f, true);
    // Active or pending entries are never evicted by a packet's traversal order.
    private final Map<DeviceKey, DeviceState> devices = new HashMap<>();
    private final Deque<Job> relayJobs = new ArrayDeque<>(), deviceJobs = new ArrayDeque<>();
    private final Set<TerrainProfiles.Key> queued = new HashSet<>(), failed = new LinkedHashSet<>();
    private final Map<TerrainProfiles.Key, int[]> completed = new LinkedHashMap<>();
    private Set<Long> columns = Set.of();
    private int revision, settingsHash, workTurn;
    private long sampledColumns, deviceColumns, sampleNanos, completedProfiles, filteredDeviceChecks, deviceOverflows;
    private long maxWorkTickNanos, budgetOverrunTicks;
    private boolean changed, warned;

    private TerrainManager(MinecraftServer server, Collection<TowerRecord> records) {
        this.server = server;
        profiles = server.overworld().getDataStorage().computeIfAbsent(TerrainProfiles::load, TerrainProfiles::new, TerrainProfiles.NAME);
        profiles.initialize(server.overworld().getSeed());
        updateTips(records);
        profiles.retainColumns(columns);
        settingsHash = settingsHash();
        changed = false;
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
        return tip == null ? 1 : TerrainMath.elevationMultiplier(tip.getY() + 1.0, cfg.elevationReferenceY,
                cfg.elevationFullBonusY, cfg.maxElevationAntennaBonus);
    }
    private static Vec3 tip(TowerRecord tower, RadioSpace.Site fallback) {
        var manager = current; BlockPos tip = manager == null ? null : manager.tips.get(tower.id());
        return tip == null ? fallback.position() : new Vec3(tip.getX() + .5, tip.getY() + 1.0, tip.getZ() + .5);
    }
    private static double fraction(int[] heights, double ay, double by) {
        var cfg = RepeatersConfig.INSTANCE.terrain;
        return TerrainMath.profileLoss(heights, ay, by, cfg.nearGroundHeight, cfg.nearGroundLoss, cfg.undergroundLosses);
    }

    /** Existing links keep their committed penalty during remapping; new links wait in SCAN. */
    public static double relayLoss(RadioSpace.Leg leg, TowerRecord from, TowerRecord to) {
        return relayLoss(leg, from, to, true);
    }
    public static double relayLoss(RadioSpace.Leg leg, TowerRecord from, TowerRecord to, boolean request) {
        var m = current;
        if (m == null || !RepeatersConfig.INSTANCE.terrain.mode.relayLinks() || !overworld(leg) || leg.distance() == 0) return 0;
        // Legacy records retain ordinary propagation until their first verified mast scan.
        if (from.antennaTip() == null || to.antennaTip() == null) return 0;
        var pair = RelayPair.of(from.id(), to.id());
        RelayState state = request ? m.relayStates.computeIfAbsent(pair, ignored -> new RelayState()) : m.relayStates.get(pair);
        if (!m.tips.containsKey(from.id()) || !m.tips.containsKey(to.id())) {
            if (state != null) state.waitingForTip = true;
            return previousLoss(leg.distance(), state);
        }
        var a = tip(from, leg.from()); var b = tip(to, leg.to()); var cfg = RepeatersConfig.INSTANCE.terrain;
        var key = TerrainProfiles.Key.between(Mth.floor(a.x), Mth.floor(a.z), Mth.floor(b.x), Mth.floor(b.z), cfg.sampleSpacing, cfg.maxSamplesPerPath);
        if (state != null) state.waitingForTip = false;
        if (m.failed.contains(key)) {
            if (state != null) { state.loss = 0; state.pending = null; }
            return 0;
        }
        int[] heights = m.profiles.get(key);
        boolean forward = key.startsAt(Mth.floor(a.x), Mth.floor(a.z));
        double ay = forward ? a.y : b.y, by = forward ? b.y : a.y;
        if (heights == null) {
            if (state != null) state.pending = key;
            if (request) m.enqueueRelay(key, leg.from().level());
            int[] previous = m.profiles.previous(key);
            if (previous != null) {
                double loss = fraction(previous, ay, by);
                if (state != null) state.loss = loss;
                return TerrainMath.extraDistance(leg.distance(), loss);
            }
            return previousLoss(leg.distance(), state);
        }
        var ray = new Ray(key, ay, by);
        double loss = m.rays.computeIfAbsent(ray, ignored -> fraction(heights, ay, by));
        trim(m.rays, MAX_RAYS);
        if (state != null) { state.loss = loss; state.pending = null; }
        return TerrainMath.extraDistance(leg.distance(), loss);
    }
    private static double previousLoss(double distance, RelayState state) {
        return state != null && Double.isFinite(state.loss)
                ? TerrainMath.extraDistance(distance, state.loss) : Double.POSITIVE_INFINITY;
    }
    private void enqueueRelay(TerrainProfiles.Key key, ServerLevel level) {
        if (queued.size() >= MAX_PENDING || !queued.add(key)) return;
        Vec3 start = new Vec3(key.ax() + .5, 0, key.az() + .5), end = new Vec3(key.bx() + .5, 0, key.bz() + .5);
        relayJobs.add(new Job(level, start, end, key, samples -> { completed.put(key, samples); changed = true; },
                () -> {
                    queued.remove(key); failed.add(key);
                    changed = true;
                }));
    }
    /** 0=ready, 1=initial SCAN, 2=CHECK with a previous committed result. Display only. */
    public static int mappingState(UUID tower) {
        var m = current;
        if (m == null || !RepeatersConfig.INSTANCE.terrain.mode.relayLinks()) return 0;
        int result = 0;
        for (var entry : m.relayStates.entrySet()) {
            var state = entry.getValue();
            if (entry.getKey().contains(tower) && (state.waitingForTip
                    || state.pending != null && m.profiles.get(state.pending) == null && !m.failed.contains(state.pending)))
                result = Math.max(result, Double.isFinite(state.loss) ? 2 : 1);
        }
        return result;
    }

    public static double ingressLoss(RadioSpace.Leg leg, UUID sender, TowerRecord receiver) {
        if (!RepeatersConfig.INSTANCE.terrain.mode.pointToPoint()) return 0;
        return deviceLoss(leg, sender, receiver.id(), leg.from().position(), tip(receiver, leg.to()));
    }
    public static double egressLoss(RadioSpace.Leg leg, TowerRecord sender, RadioReceiver receiver) {
        if (!RepeatersConfig.INSTANCE.terrain.mode.pointToPoint()) return 0;
        return deviceLoss(leg, sender.id(), receiver.reference, tip(sender, leg.from()), leg.to().position());
    }
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
        return state == null ? Double.POSITIVE_INFINITY : TerrainMath.extraDistance(leg.distance(), state.loss);
    }
    /** Retained for native diagnostics and addons; real routing supplies the actual packet below. */
    public static double nativeLoss(RadioTransmitter sender, RadioReceiver receiver) {
        return nativeLoss(sender, receiver, null, -1);
    }
    public static double nativeLoss(RadioTransmitter sender, RadioReceiver receiver, RadioSource source, float nativeDistance) {
        if (!RepeatersConfig.INSTANCE.terrain.mode.pointToPoint()) return 0;
        var from = sender.getLocation(); var to = receiver.getLocation();
        if (from == null || to == null || !(from.level instanceof ServerLevel a) || to.level != a
                || !a.dimension().equals(Level.OVERWORLD) || sender.frequency == null) return 0;
        var leg = RadioSpace.between(RadioSpace.physical(a, new Vec3(from)), RadioSpace.physical(a, new Vec3(to)), false);
        if (leg == null || !overworld(leg)) return 0;
        if (nativeDistance >= 0) leg = new RadioSpace.Leg(leg.from(), leg.to(), nativeDistance, false, leg.propagation());
        FrequencingType type = source == null || source.frequencingType == -1 ? sender.frequencingType : source.getFrequencingType();
        double power = source == null || source.frequencingType == -1 ? sender.getPower(sender.frequency.getModulation()) : source.transmissionPower;
        double cap = source == null || source.frequencingType == -1 ? power : source.transmissionCap;
        // Includes the real packet power, native reception floor and optional directional reception.
        if (type == null || NativeRadioAdapter.receiveAtReceiver(leg, power, cap, type, receiver, ChannelKey.from(sender.frequency)) == null) {
            if (current != null) current.filteredDeviceChecks++;
            return 0;
        }
        return deviceLoss(leg, sender.reference, receiver.reference, leg.from().position(), leg.to().position());
    }
    private static double deviceLoss(RadioSpace.Leg leg, UUID from, UUID to, Vec3 a, Vec3 b) {
        var m = current;
        if (m == null || !RepeatersConfig.INSTANCE.terrain.mode.pointToPoint() || !overworld(leg)
                || m.server != leg.from().level().getServer() || !m.server.isSameThread()) return 0;
        var key = new DeviceKey(from, to);
        if (TerrainMath.free(a.distanceToSqr(b))) { m.devices.remove(key); return 0; }
        DeviceState state = m.devices.get(key);
        if (state == null) {
            // A safety ceiling must not evict live work or turn overload into permanent silence.
            if (m.devices.size() >= MAX_DEVICE_LINKS) { m.deviceOverflows++; return 0; }
            state = new DeviceState(); m.devices.put(key, state);
        }
        state.used = m.server.overworld().getGameTime();
        if (!state.pending && (state.a == null || TerrainMath.moved(a.distanceToSqr(state.a)) || TerrainMath.moved(b.distanceToSqr(state.b)))
                && m.deviceJobs.size() < MAX_PENDING) {
            DeviceState pending = state;
            state.pending = true;
            m.deviceJobs.add(m.new Job(leg.from().level(), a, b, null, null, null) {
                @Override boolean valid() { return m.devices.get(key) == pending; }
                @Override void finish() {
                    pending.a = a; pending.b = b; pending.loss = sum / (count - 1); pending.pending = false;
                }
                @Override void fail() { pending.a = a; pending.b = b; pending.loss = 0; pending.pending = false; }
            });
        }
        return TerrainMath.extraDistance(leg.distance(), state.loss);
    }

    /** Geometry/results publish on the native antenna cadence; sampling runs between boundaries. */
    public static void tick(MinecraftServer server, Collection<TowerRecord> records) {
        var m = current; if (m == null || m.server != server) return;
        long now = server.overworld().getGameTime();
        if (now % 200 == 0 && m.settingsHash != settingsHash()) reconfigure(server);
        if (!RepeatersConfig.INSTANCE.terrain.mode.relayLinks()) return;
        if (now % 200 == 0) {
            m.updateTips(records);
            m.completed.forEach((key, heights) -> {
                if (key.usesOnly(m.columns)) { m.profiles.put(key, heights); m.completedProfiles++; }
                m.queued.remove(key);
            });
            m.completed.clear();
            m.profiles.retainColumns(m.columns);
            m.relayJobs.removeIf(job -> {
                if (job.path.usesOnly(m.columns)) return false;
                m.queued.remove(job.path); return true;
            });
            if (m.changed) {
                m.changed = false; m.revision++;
                TowerManager.resourcesReloaded(server, "terrain_profile_or_height_changed");
            }
        }
        if (now % 20 == 0) m.devices.values().removeIf(state -> now - state.used > 600);
        long started = System.nanoTime(); var cfg = RepeatersConfig.INSTANCE.terrain;
        long budget = TerrainMath.samplingBudgetNanos(cfg.sampleBudgetMs, server.getAverageTickTime());
        for (int i = 0; i < cfg.samplesPerTick && (i == 0 || System.nanoTime() - started < budget); i++) {
            boolean device = !m.deviceJobs.isEmpty() && (m.relayJobs.isEmpty() || m.workTurn++ % 4 == 3);
            Deque<Job> queue = device ? m.deviceJobs : m.relayJobs;
            if (queue.isEmpty()) break;
            Job job = queue.peek();
            if (!job.valid()) { queue.remove(); i--; continue; }
            if (job.step()) queue.remove();
        }
        long elapsed = System.nanoTime() - started;
        m.maxWorkTickNanos = Math.max(m.maxWorkTickNanos, elapsed);
        if (elapsed > budget) m.budgetOverrunTicks++;
    }
    private void updateTips(Collection<TowerRecord> records) {
        Map<UUID, BlockPos> next = new HashMap<>(); Set<UUID> ids = new HashSet<>();
        records.forEach(t -> {
            ids.add(t.id());
            if (t.antennaTip() != null && t.dimension().equals("minecraft:overworld")) next.put(t.id(), t.antennaTip());
        });
        if (!next.equals(tips)) { tips.clear(); tips.putAll(next); changed = true; }
        Set<Long> referenced = new HashSet<>();
        tips.values().forEach(p -> referenced.add(TerrainProfiles.column(p.getX(), p.getZ())));
        columns = Set.copyOf(referenced);
        relayStates.keySet().removeIf(pair -> !ids.contains(pair.a()) || !ids.contains(pair.b()));
        relayStates.forEach((pair, state) -> {
            if (state.pending != null && !state.pending.usesOnly(columns)) state.pending = null;
            if (tips.containsKey(pair.a()) && tips.containsKey(pair.b())) state.waitingForTip = false;
        });
        failed.removeIf(key -> !key.usesOnly(columns));
    }
    public static int engineHeight(ServerLevel level, int x, int z) {
        return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                level, level.getChunkSource().randomState());
    }
    private class Job {
        final ServerLevel level; final Vec3 a, b; final int count; final int[] heights;
        final TerrainProfiles.Key path;
        final Consumer<int[]> consumer; final Runnable failure; final long enqueued;
        int index; double sum;
        Job(ServerLevel level, Vec3 a, Vec3 b, TerrainProfiles.Key path, Consumer<int[]> consumer, Runnable failure) {
            this.level = level; this.a = a; this.b = b; this.path = path; this.consumer = consumer; this.failure = failure;
            var cfg = RepeatersConfig.INSTANCE.terrain;
            count = TerrainMath.sampleCount(Math.hypot(b.x - a.x, b.z - a.z), cfg.sampleSpacing, cfg.maxSamplesPerPath);
            heights = path == null ? null : new int[count];
            enqueued = server.overworld().getGameTime();
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
                            * TerrainMath.loss(TerrainMath.height(a.y, b.y, t) - h, cfg.nearGroundHeight, cfg.nearGroundLoss, cfg.undergroundLosses);
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
                c.nearGroundHeight, c.nearGroundLoss, Arrays.hashCode(c.undergroundLosses),
                c.maxElevationAntennaBonus, c.elevationReferenceY, c.elevationFullBonusY);
    }
    public static int configurationStamp() { return current == null ? 0 : 31 * current.revision + current.settingsHash; }
    private void resetWork() {
        rays.clear(); devices.clear(); relayJobs.clear(); deviceJobs.clear(); queued.clear(); completed.clear(); failed.clear();
        relayStates.values().forEach(state -> { state.pending = null; state.waitingForTip = false; });
        changed = false;
    }
    public static void reconfigure(MinecraftServer server) {
        var m = current; if (m == null || m.server != server) return;
        RepeatersConfig.INSTANCE.terrain.validate();
        int hash = settingsHash(); if (hash == m.settingsHash) return;
        m.settingsHash = hash; m.resetWork(); m.revision++;
        TowerManager.resourcesReloaded(server, "terrain_configuration_changed");
    }
    public static void clear(MinecraftServer server) {
        var m = current; if (m == null || m.server != server) return;
        m.profiles.clear(); m.resetWork(); m.revision++;
        TowerManager.resourcesReloaded(server, "terrain_profiles_remapping");
    }
    public static Map<String, Object> statistics() {
        var m = current; if (m == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("relay_profiles", m.profiles.size()); result.put("pending_relay_profiles", m.queued.size());
        result.put("device_results", m.devices.size()); result.put("pending_device_checks", m.deviceJobs.size());
        result.put("device_measured_results", m.devices.values().stream().filter(s -> Double.isFinite(s.loss)).count());
        result.put("sampled_columns", m.sampledColumns); result.put("device_columns", m.deviceColumns);
        result.put("sampling_ms", m.sampleNanos / 1_000_000.0); result.put("completed_relay_profiles", m.completedProfiles);
        result.put("filtered_device_checks", m.filteredDeviceChecks); result.put("device_capacity_fallbacks", m.deviceOverflows);
        result.put("remaining_relay_columns", m.relayJobs.stream().mapToInt(j -> j.count - j.index).sum());
        result.put("remaining_device_columns", m.deviceJobs.stream().mapToInt(j -> j.count - j.index).sum());
        result.put("oldest_relay_wait_ticks", m.relayJobs.isEmpty() ? 0 : m.server.overworld().getGameTime() - m.relayJobs.peek().enqueued);
        result.put("max_sampling_tick_ms", m.maxWorkTickNanos / 1_000_000.0); result.put("sampling_budget_overrun_ticks", m.budgetOverrunTicks);
        return result;
    }
}
