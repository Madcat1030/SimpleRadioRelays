package dev.paul.simpleradiorepeaters.radio;

import com.codinglitch.simpleradio.central.FrequencingType;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import com.codinglitch.simpleradio.radio.RadioSource;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.network.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static net.minecraft.commands.Commands.*;

/** Opt-in, server-thread-only observation. Never stores audio, chooses a route or clears a cache. */
public final class RelayPerformance {
    public enum Phase { SETUP, NETWORK_LOOKUP, INITIAL_ENTRANCES, CALLBACKS_AND_WIRES, FINAL_ENTRANCES,
        PATHS, OUTPUTS, RECEIVER_FILTER, DIRECT_CANDIDATES, RELAY_CANDIDATES, SELECT_WINNER, DELIVERY }
    public enum Counter { ROUTE_CALLS, TAKEN_OVER, FORWARDED_FRAMES, NO_PORTS, NO_ENTRANCE, HOP_LIMIT, DUPLICATE_EMITTERS,
        ROUTER_ACCEPTORS, WIRE_DISTRIBUTIONS, INGRESS_PASSES, INGRESS_SITE_PAIRS, INGRESS_RF_CHECKS, INGRESS_RF_SUCCESSES,
        INITIAL_INGRESS_RF_CHECKS, FINAL_INGRESS_RF_CHECKS, REACHABLE_OPTIONS, OUTPUT_CHANNELS, MISSING_FREQUENCIES,
        RECEIVER_VISITS, RECEIVERS_ELIGIBLE, SKIPPED_STATE, SKIPPED_CRITERIA, SKIPPED_DUPLICATE, SKIPPED_UNLOADED,
        DIRECT_RF_CHECKS, DIRECT_CANDIDATES, PORT_VISITS, MATCHING_PORTS, EGRESS_SITE_PAIRS, EGRESS_RF_CHECKS,
        RELAY_CANDIDATES, CUSTOM_ROUTE_CRITERIA, RECEIVER_SELECTIONS, DELIVERY_ATTEMPTS, DELIVERY_ACCEPTED,
        ROUTER_SOURCE_COPIES, CALLBACK_RF_CHANGES, SOURCE_RF_REPEATS, SOURCE_RF_CHANGES, SOURCE_MOVED_8_BLOCKS,
        RECEIVER_OBSERVATIONS, RECEIVER_MOVED_8_BLOCKS, RECEIVER_STATE_CHANGES, RECEIVER_REPLACEMENTS,
        SNAPSHOTS_UNCHANGED, RELAY_LOADS, RELAY_UNLOADS, INVALIDATIONS, DISCARDED_NETWORKS, ROUTE_EXCEPTIONS }
    private static volatile Capture active;
    private RelayPerformance() {}
    private static String name(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
    private static Capture current() {
        Capture c = active; return c != null && c.server.isSameThread() ? c : null;
    }
    public static boolean running(MinecraftServer server) { Capture c = active; return c != null && c.server == server; }
    public static long clock(MinecraftServer server) { return running(server) ? System.nanoTime() : 0; }
    public static Sample begin(MinecraftServer server) {
        Capture c = current(); return c != null && c.server == server ? new Sample(c) : null;
    }
    public static void count(Counter key) { Capture c = current(); if (c != null) c.add(key, 1); }
    public static void cache(String kind, String outcome) {
        Capture c = current(); if (c != null) c.cache[c.diagnosticDepth > 0 ? 2 : c.current != null ? 0 : 1]
                [kind.equals("network") ? 0 : kind.equals("path") ? 1 : 2]
                [outcome.equals("hit") ? 0 : outcome.equals("miss") ? 1 : outcome.equals("eviction") ? 2 : 3]++;
    }
    public static void networkLookup(MinecraftServer server, boolean hit, TowerManager.Network network) {
        Capture c = current(); if (c == null || c.server != server) return;
        cache("network", hit ? "hit" : "miss"); c.maxPorts = Math.max(c.maxPorts, network.ports().size());
    }
    public static void graphBuilt(MinecraftServer server, long started, TowerManager.Network graph, TowerManager.Scope scope) {
        Capture c = current(); if (c == null || c.server != server || started == 0) return;
        c.builds.computeIfAbsent(c.context(), k -> new PerformanceTiming()).add(System.nanoTime() - started);
        c.event("graph_built", Map.of("context", c.context(), "ports", graph == null ? -1 : graph.ports().size(),
                "dimension", scope.dimension(), "input_channel", scope.channel().toString(), "native_dimensions", scope.nativeCrossDimensional(), "policy_fingerprint", scope.policyFingerprint()));
    }
    public static void scanned(MinecraftServer server, long started, AntennaScanner.Result result) {
        Capture c = current(); if (c == null || c.server != server || started == 0) return;
        c.scans.add(System.nanoTime() - started);
        c.scanResults.merge(result == null ? "exception" : !result.complete() ? "deferred" : result.score() > 0 ? "positive" : "zero", 1L, Long::sum);
    }
    public static void invalidated(MinecraftServer server, String reason, int removed, TowerRecord before, TowerRecord after) {
        Capture c = current(); if (c == null || c.server != server) return;
        c.add(Counter.INVALIDATIONS, 1); c.add(Counter.DISCARDED_NETWORKS, removed);
        c.invalidations.merge(reason, 1L, Long::sum);
        Map<String, Object> detail = new LinkedHashMap<>(); detail.put("reason", reason); detail.put("cached_networks_removed", removed);
        TowerRecord tower = after == null ? before : after;
        if (tower != null) { detail.put("relay", tower.id().toString()); detail.put("dimension", tower.dimension()); detail.put("position", tower.position().toShortString()); }
        if (before != null && after != null) {
            List<String> changed = new ArrayList<>();
            if (!before.channels().equals(after.channels())) changed.add("channels");
            if (before.enabled() != after.enabled()) changed.add("enabled");
            if (!Objects.equals(before.catalystItem(), after.catalystItem())) changed.add("catalyst");
            if (before.verified() != after.verified()) changed.add("verified");
            if (before.antennaScore() != after.antennaScore()) changed.add("antenna_score");
            if (!before.antennaBase().equals(after.antennaBase())) changed.add("antenna_base");
            if (!before.footprint().equals(after.footprint())) changed.add("footprint");
            if (!Objects.equals(before.cardBank(), after.cardBank())) changed.add("controller");
            if (!before.attachment().equals(after.attachment())) changed.add("attachment");
            if (!Objects.equals(before.transponder(), after.transponder())) changed.add("transponder");
            detail.put("changed_fields", changed);
        }
        c.event("invalidation", detail);
    }
    public static <T> T diagnostic(MinecraftServer server, java.util.function.Supplier<T> body) {
        Capture c = current(); if (c == null || c.server != server) return body.get();
        c.diagnosticDepth++; try { return body.get(); } finally { c.diagnosticDepth--; }
    }
    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return literal("test").requires(source -> source.hasPermission(2))
                .then(literal("start").executes(context -> start(context, 0))
                        .then(argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(context -> start(context, IntegerArgumentType.getInteger(context, "seconds")))))
                .then(literal("stop").executes(context -> {
                    if (!running(context.getSource().getServer())) throw failure("No radio performance test is running.");
                    Path result = finish(context.getSource().getServer(), "command_stop");
                    if (result == null) { context.getSource().sendFailure(Component.literal("Capture stopped, but the report could not be written. See logs/latest.log.")); return 0; }
                    context.getSource().sendSuccess(() -> Component.literal("Radio performance test saved to " + displayPath(result)), false); return 1;
                }));
    }
    private static CommandSyntaxException failure(String text) { return new SimpleCommandExceptionType(Component.literal(text)).create(); }
    private static int start(CommandContext<CommandSourceStack> context, int seconds) throws CommandSyntaxException {
        if (active != null) throw failure("A radio performance test is already running. Use /radiorepeater test stop first.");
        MinecraftServer server = context.getSource().getServer(); active = new Capture(server, seconds, context.getSource());
        RelayGraph.observeCaches(RelayPerformance::cache);
        SimpleRadioRepeaters.LOGGER.info("[relay-test] START id={} seconds={} towers={}", active.id, seconds == 0 ? "manual" : seconds, active.initial.get("network"));
        context.getSource().sendSuccess(() -> Component.literal("Radio performance test started" + (seconds == 0 ? "; use /radiorepeater test stop to save it." : " for " + seconds + " seconds; it will save automatically.")), false);
        return 1;
    }
    public static void tick(MinecraftServer server) {
        Capture c = current(); if (c == null || c.server != server) return;
        long now = System.nanoTime(); if (now - c.bucketStarted >= 1_000_000_000L) c.bucket(now);
        if (c.deadline != 0 && now >= c.deadline) {
            Path path = finish(server, "duration_elapsed");
            if (path != null) c.requester.sendSuccess(() -> Component.literal("Radio performance test complete: " + displayPath(path)), false);
        }
    }
    public static void stopping(MinecraftServer server) { if (running(server)) finish(server, "server_shutdown"); }
    private static String displayPath(Path path) { return FabricLoader.getInstance().getGameDir().toAbsolutePath().relativize(path.toAbsolutePath()).toString(); }
    private static Path finish(MinecraftServer server, String reason) {
        Capture c = current(); if (c == null || c.server != server) return null;
        active = null; RelayGraph.observeCaches(null); long now = System.nanoTime(); c.bucket(now);
        Path file = FabricLoader.getInstance().getGameDir().resolve("logs/radiorepeaters/test-" + c.started.toString().replace(':', '-') + "-" + c.id + ".json");
        try {
            Map<String, Object> report = c.report(now, reason);
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardOpenOption.CREATE_NEW);
            SimpleRadioRepeaters.LOGGER.info("[relay-test] STOP id={} reason={} seconds={} calls={} comparisons={} routingSelfMs={} report={}",
                    c.id, reason, (now - c.began) / 1e9, c.value(Counter.ROUTE_CALLS), c.value(Counter.EGRESS_RF_CHECKS), c.self.total() / 1e6, displayPath(file));
            return file;
        } catch (IOException | RuntimeException error) {
            SimpleRadioRepeaters.LOGGER.error("[relay-test] Could not save capture {}. calls={}, comparisons={}, routingSelfMs={}",
                    c.id, c.value(Counter.ROUTE_CALLS), c.value(Counter.EGRESS_RF_CHECKS), c.self.total() / 1e6, error); return null;
        }
    }
    private record Input(short type, float power, float cap, double noise) {}
    private record FlowKey(UUID transmitter, String dimension, ChannelKey channel, int relays, int decay) {}
    private static final class Flow {
        final FlowKey key; final PerformanceTiming self = new PerformanceTiming();
        long calls, forwarded, receiverVisits, comparisons, delivered, rfRepeats, rfChanges, moved;
        Input lastInput; Vec3 anchor; Flow(FlowKey key) { this.key = key; }
        Map<String, Object> report() {
            Map<String, Object> out = new LinkedHashMap<>(); out.put("transmitter", key.transmitter().toString()); out.put("dimension", key.dimension());
            out.put("input_channel", key.channel().toString()); out.put("incoming_relays", key.relays()); out.put("incoming_decay", key.decay());
            out.put("calls", calls); out.put("forwarded_frames", forwarded); out.put("receiver_visits", receiverVisits); out.put("egress_rf_checks", comparisons);
            out.put("accepted_deliveries", delivered); out.put("rf_repeats", rfRepeats); out.put("rf_changes", rfChanges); out.put("source_moved_8_blocks", moved);
            out.put("route_self", self.report()); return out;
        }
    }
    private record ReceiverState(int identity, String dimension, Vec3 anchor, int antenna, short type, int reception, int floor) {}
    private static final class Capture {
        final MinecraftServer server; final CommandSourceStack requester; final String id = UUID.randomUUID().toString().substring(0, 8);
        final Instant started = Instant.now(); final long began = System.nanoTime(), deadline;
        final int startTick; final Map<String, Object> initial;
        final long[] counts = new long[Counter.values().length];
        final PerformanceTiming self = new PerformanceTiming(), inclusive = new PerformanceTiming(), scans = new PerformanceTiming();
        final EnumMap<Phase, PerformanceTiming> phases = new EnumMap<>(Phase.class);
        final long[][][] cache = new long[3][3][4];
        final Map<String, Long> invalidations = new TreeMap<>(), scanResults = new TreeMap<>();
        final Map<String, PerformanceTiming> builds = new TreeMap<>(); final Map<FlowKey, Flow> flows = new LinkedHashMap<>();
        final Map<UUID, ReceiverState> receivers = new HashMap<>();
        final ArrayDeque<Map<String, Object>> events = new ArrayDeque<>(), timeline = new ArrayDeque<>();
        long eventsDropped, timelineDropped, untrackedFlows, untrackedReceivers, bucketStarted = began, lastCalls, lastSelf, lastChecks, lastDeliveries;
        int diagnosticDepth, maxPorts; Sample current;
        Capture(MinecraftServer server, int seconds, CommandSourceStack requester) {
            this.server = server; this.requester = requester; startTick = server.getTickCount(); deadline = seconds == 0 ? 0 : began + seconds * 1_000_000_000L;
            initial = snapshot(server);
        }
        String context() { return diagnosticDepth > 0 ? "diagnostic" : current != null ? "routing" : "maintenance"; }
        void add(Counter key, long amount) {
            counts[key.ordinal()] += amount; Flow f = current == null ? null : current.flow; if (f == null) return;
            switch (key) { case FORWARDED_FRAMES -> f.forwarded += amount; case RECEIVER_VISITS -> f.receiverVisits += amount;
                case EGRESS_RF_CHECKS -> f.comparisons += amount; case DELIVERY_ACCEPTED -> f.delivered += amount; default -> {} }
        }
        long value(Counter key) { return counts[key.ordinal()]; }
        void event(String kind, Map<String, ?> detail) {
            Map<String, Object> out = new LinkedHashMap<>(); out.put("elapsed_seconds", (System.nanoTime() - began) / 1e9);
            out.put("tick", server.getTickCount()); out.put("kind", kind); out.putAll(detail);
            if (events.size() == 256) { events.removeFirst(); eventsDropped++; } events.addLast(out);
        }
        void bucket(long now) {
            Map<String, Object> out = new LinkedHashMap<>(); out.put("elapsed_seconds", (now - began) / 1e9); out.put("interval_seconds", (now - bucketStarted) / 1e9);
            out.put("tick", server.getTickCount()); out.put("route_calls", value(Counter.ROUTE_CALLS) - lastCalls);
            out.put("route_self_ms", (self.total() - lastSelf) / 1e6); out.put("egress_rf_checks", value(Counter.EGRESS_RF_CHECKS) - lastChecks);
            out.put("accepted_deliveries", value(Counter.DELIVERY_ACCEPTED) - lastDeliveries);
            if (timeline.size() == 600) { timeline.removeFirst(); timelineDropped++; } timeline.addLast(out);
            bucketStarted = now; lastCalls = value(Counter.ROUTE_CALLS); lastSelf = self.total(); lastChecks = value(Counter.EGRESS_RF_CHECKS); lastDeliveries = value(Counter.DELIVERY_ACCEPTED);
        }
        Map<String, Object> report(long now, String reason) {
            Map<String, Object> out = new LinkedHashMap<>(); double seconds = (now - began) / 1e9;
            out.put("schema", 1); out.put("capture_id", id); out.put("started_utc", started.toString()); out.put("stopped_utc", Instant.now().toString()); out.put("stop_reason", reason);
            out.put("elapsed_seconds", seconds); out.put("elapsed_ticks", server.getTickCount() - startTick);
            out.put("observed_tps", (server.getTickCount() - startTick) / Math.max(.000001, seconds)); out.put("start", initial); out.put("end", snapshot(server));
            Map<String, Object> numbers = new LinkedHashMap<>(); for (Counter counter : Counter.values()) numbers.put(name(counter), value(counter)); out.put("counters", numbers);
            out.put("route_self", self.report()); out.put("route_inclusive", inclusive.report());
            out.put("route_self_percent_of_capture_wall_time", self.total() * 100d / Math.max(1, now - began));
            Map<String, Object> times = new LinkedHashMap<>(); phases.forEach((phase, timing) -> times.put(name(phase), timing.report())); out.put("phase_self", times);
            Map<String, Long> cacheReport = new LinkedHashMap<>();
            String[] contexts = {"routing", "maintenance", "diagnostic"}, kinds = {"network", "path", "link_row"}, outcomes = {"hit", "miss", "eviction", "not_retained_over_weight"};
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 4; k++)
                if (cache[i][j][k] != 0) cacheReport.put(contexts[i] + "/" + kinds[j] + "/" + outcomes[k], cache[i][j][k]);
            out.put("cache_operations", cacheReport); Map<String, Object> graphTimes = new LinkedHashMap<>(); builds.forEach((context, timing) -> graphTimes.put(context, timing.report()));
            out.put("network_builds", graphTimes); out.put("invalidation_reasons", invalidations); out.put("max_network_ports_observed", maxPorts);
            out.put("antenna_scans", scans.report()); out.put("antenna_scan_results", scanResults);
            out.put("flows", flows.values().stream().map(Flow::report).toList()); out.put("untracked_flow_observations", untrackedFlows);
            out.put("unique_receivers_tracked", receivers.size()); out.put("untracked_receiver_observations", untrackedReceivers);
            out.put("events", List.copyOf(events)); out.put("events_dropped", eventsDropped); out.put("timeline", List.copyOf(timeline)); out.put("timeline_buckets_dropped", timelineDropped);
            out.put("notes", List.of("Elapsed timings include instrumentation and JVM/OS pauses; they are not CPU sampling or allocation measurements.",
                    "Self timings exclude nested repeater routing; inclusive timings overlap during wired re-entry. Phase self times partition route self time.",
                    "p50/p95/p99 values are powers-of-two histogram bucket upper bounds; means and maxima use measured nanoseconds.",
                    "Cache operations and graph builds are separated into routing, maintenance and explicit diagnostic contexts.",
                    "Counters observe the existing algorithm. No routing, cache lifetime, packet rate, RF math or delivery choice is changed.",
                    "Router source copies exclude additional copies made inside native Simple Radio. Audio contents are never recorded.")); return out;
        }
    }
    private static Map<String, Object> snapshot(MinecraftServer server) {
        Map<String, Object> out = new LinkedHashMap<>(); var manager = TowerManager.get(server);
        out.put("network", manager == null ? "starting" : manager.summary()); out.put("towers", manager == null ? List.of() : manager.describe());
        out.put("players", server.getPlayerList().getPlayerCount()); out.put("java", System.getProperty("java.version")); out.put("available_processors", Runtime.getRuntime().availableProcessors());
        Map<String, String> mods = new TreeMap<>(); FabricLoader.getInstance().getAllMods().stream()
                .filter(mod -> mod.getMetadata().getId().startsWith("simpleradio") || Set.of("minecraft", "fabricloader", "voicechat").contains(mod.getMetadata().getId()))
                .forEach(mod -> mods.put(mod.getMetadata().getId(), mod.getMetadata().getVersion().getFriendlyString())); out.put("mods", mods);
        out.put("rf_configuration_stamp", NativeRadioAdapter.configurationStamp());
        var tx = com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing.TRANSMITTER;
        var rx = com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing.RECEIVER;
        var frequency = com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG.frequency;
        Map<String, Object> rf = new LinkedHashMap<>();
        rf.put("tower_tx_am", tx.transmissionPowerAM); rf.put("tower_tx_fm", tx.transmissionPowerFM); rf.put("tx_antenna_aptitude", tx.antennaAptitude);
        rf.put("tx_loss", tx.transmissionDiminishment); rf.put("tx_loss_method", tx.diminishmentMethod.name());
        rf.put("threshold_am", tx.diminishThresholdAM); rf.put("threshold_fm", tx.diminishThresholdFM);
        rf.put("rx_base", rx.receptionPower); rf.put("rx_floor", rx.receptionFloor); rf.put("rx_antenna_aptitude", rx.antennaAptitude);
        rf.put("base_noise_am", frequency.baseAMInterference); rf.put("base_noise_fm", frequency.baseFMInterference);
        rf.put("native_dimensional_enabled", frequency.crossDimensional); rf.put("dimensional_interference", frequency.dimensionalInterference);
        rf.put("relay_dimensional_enabled", RadioSpace.nativeCrossDimensional()); rf.put("policy_fingerprint", RadioSpace.policyFingerprint());
        Map<String, Double> catalysts = new TreeMap<>(); RelayCatalysts.items().forEach(item -> catalysts.put(item.toString(), RelayCatalysts.efficiency(item)));
        rf.put("catalysts", catalysts); out.put("rf_parameters", rf);
        out.put("persistent_unloaded_towers", dev.paul.simpleradiorepeaters.RepeatersConfig.INSTANCE.persistentUnloadedTowers);
        out.put("max_relay_hops", dev.paul.simpleradiorepeaters.RepeatersConfig.INSTANCE.maxRelayHops); return out;
    }
    public static final class Sample implements AutoCloseable {
        private final Capture capture; private final Sample parent; private final long began = System.nanoTime();
        private long phaseStarted = began, children; private Phase phase = Phase.SETUP;
        private Flow flow; private Input first, last; private Vec3 emission; private boolean closed;
        private Sample(Capture capture) { this.capture = capture; parent = capture.current; capture.current = this; capture.add(Counter.ROUTE_CALLS, 1); }
        public void phase(Phase next) {
            long now = System.nanoTime(); capture.phases.computeIfAbsent(phase, k -> new PerformanceTiming()).add(now - phaseStarted);
            phase = next; phaseStarted = now;
        }
        public void add(Counter counter) { capture.add(counter, 1); }
        public void add(Counter counter, long amount) { capture.add(counter, amount); }
        public void flow(UUID transmitter, String dimension, ChannelKey channel, int relays, int decay) {
            FlowKey key = new FlowKey(transmitter, dimension, channel, relays, decay); flow = capture.flows.get(key);
            if (flow == null && capture.flows.size() < 128) { flow = new Flow(key); capture.flows.put(key, flow); }
            if (flow == null) capture.untrackedFlows++; else flow.calls++;
        }
        public void emission(Vec3 position) { emission = position; }
        public void input(RadioSource source, FrequencingType type, double noise) {
            Input input = new Input(type == null ? -1 : type.id, source.transmissionPower, source.transmissionCap, noise);
            if (phase == Phase.INITIAL_ENTRANCES) first = input; else last = input;
        }
        public void receiver(RadioReceiver receiver, RadioSpace.Site site) {
            add(Counter.RECEIVER_OBSERVATIONS); UUID id = receiver.reference; ReceiverState old = capture.receivers.get(id);
            var type = receiver.frequencingType; Vec3 anchor = old == null ? site.position() : old.anchor(); String dimension = site.level().dimension().location().toString();
            if (old != null) {
                if (old.identity() != System.identityHashCode(receiver)) add(Counter.RECEIVER_REPLACEMENTS);
                if (!old.dimension().equals(dimension) || anchor.distanceToSqr(site.position()) > 64) { add(Counter.RECEIVER_MOVED_8_BLOCKS); anchor = site.position(); }
                if (old.antenna() != receiver.antennaPower || old.type() != type.id || old.reception() != type.receptionPower || old.floor() != type.receptionFloor) add(Counter.RECEIVER_STATE_CHANGES);
            }
            if (old != null || capture.receivers.size() < 512) capture.receivers.put(id, new ReceiverState(System.identityHashCode(receiver), dimension, anchor, receiver.antennaPower, type.id, type.receptionPower, type.receptionFloor));
            else capture.untrackedReceivers++;
        }
        @Override public void close() {
            if (closed) return; closed = true; long end = System.nanoTime(), duration = end - began, own = duration - children;
            capture.phases.computeIfAbsent(phase, k -> new PerformanceTiming()).add(end - phaseStarted);
            capture.self.add(own); capture.inclusive.add(duration);
            if (first != null && last != null && !first.equals(last)) add(Counter.CALLBACK_RF_CHANGES);
            Input input = last == null ? first : last;
            if (flow != null) {
                flow.self.add(own);
                if (input != null) { if (input.equals(flow.lastInput)) { add(Counter.SOURCE_RF_REPEATS); flow.rfRepeats++; }
                    else if (flow.lastInput != null) { add(Counter.SOURCE_RF_CHANGES); flow.rfChanges++; } flow.lastInput = input; }
                if (emission != null) { if (flow.anchor == null) flow.anchor = emission;
                    else if (flow.anchor.distanceToSqr(emission) > 64) { add(Counter.SOURCE_MOVED_8_BLOCKS); flow.moved++; flow.anchor = emission; } }
            }
            capture.current = parent;
            if (parent != null) { parent.children += duration; parent.phaseStarted += duration; }
        }
    }
}
