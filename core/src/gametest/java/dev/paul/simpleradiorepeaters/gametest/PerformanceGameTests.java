package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.*;
import com.codinglitch.simpleradio.radio.*;
import com.google.gson.*;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Separate batches keep the automatic timer from overlapping other capture lifecycle tests. */
public class PerformanceGameTests implements FabricGameTest {
    private static short nextId = 31400;
    private static void check(boolean value, String text) { if (!value) throw new GameTestAssertException(text); }
    private static int command(MinecraftServer server, String text) {
        try { return server.getCommands().getDispatcher().execute(text, server.createCommandSourceStack().withSuppressedOutput()); }
        catch (CommandSyntaxException error) { throw new GameTestAssertException(error.getMessage()); }
    }
    private static void refused(MinecraftServer server, String text, int permission) {
        try { server.getCommands().getDispatcher().execute(text, server.createCommandSourceStack().withPermission(permission).withSuppressedOutput()); }
        catch (CommandSyntaxException expected) { return; }
        throw new GameTestAssertException("Command should be refused: " + text);
    }
    private static Set<Path> reports() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("logs/radiorepeaters");
        if (!Files.isDirectory(dir)) return Set.of();
        try (var files = Files.list(dir)) { return new HashSet<>(files.filter(p -> p.toString().endsWith(".json")).toList()); }
        catch (IOException error) { throw new GameTestAssertException(error.toString()); }
    }
    private static JsonObject report(Set<Path> before) {
        Set<Path> files = reports(); files.removeAll(before); check(files.size() == 1, "Exactly one completed report must be written");
        try { return JsonParser.parseString(Files.readString(files.iterator().next())).getAsJsonObject(); }
        catch (IOException error) { throw new GameTestAssertException(error.toString()); }
    }
    private static long count(JsonObject report, String key) { return report.getAsJsonObject("counters").get(key).getAsLong(); }
    private static WorldlyPosition at(ServerLevel level, Vec3 pos) { return new WorldlyPosition((float) pos.x, (float) pos.y, (float) pos.z, level); }
    private static final class Tap extends RadioRouter {
        final RadioReceiver receiver; RadioSource last; int count;
        Tap(ServerLevel level, Vec3 pos, Frequency frequency) {
            super(at(level, pos)); identifier = ++nextId;
            receiver = new RadioReceiver(frequency, at(level, pos)).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
            receiver.identifier = ++nextId; receiver.owner = new ArmorStand(level, pos.x, pos.y, pos.z);
            receiver.addRouter(this); frequency.getReceivers().add(receiver);
        }
        @Override public void take(Source source) { last = (RadioSource) source; count++; }
    }
    private static RadioSource send(RadioTransmitter sender, byte[] data) {
        var packet = new RadioSource(UUID.randomUUID(), sender.getLocation(), data, 1);
        packet.originalOwner = UUID.randomUUID(); packet.activity = 42; packet.frequencingType = SimpleRadioFrequencing.TRANSCEIVER.id;
        packet.transmissionCap = packet.transmissionPower = 2000; sender.accept(packet); return packet;
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "performance_manual")
    public void capture_preserves_native_delivery_and_reports_nested_work_and_cache_contexts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); MinecraftServer server = level.getServer(); TowerManager manager = TowerManager.get(server);
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        Frequency input = frequencies.getOrCreate("096.81", Frequency.Modulation.AMPLITUDE), output = frequencies.getOrCreate("096.82", Frequency.Modulation.AMPLITUDE);
        ChannelKey in = ChannelKey.from(input), out = ChannelKey.from(output); BlockPos pos = new BlockPos(8800000, 80, -8800000);
        var tower = new TowerRecord(UUID.randomUUID(), level.dimension().location().toString(), pos, Set.of(in, out), true,
                RelayCatalysts.GILDED, true, 32, Set.of(), null, RelayAttachment.NONE, new TransponderChannels(in, out, null, false));
        // An output-only peer needs a translated path from the first tower, exercising the live path cache.
        // A single-node graph can legitimately prune its entire search without consulting that cache.
        var peer = new TowerRecord(UUID.randomUUID(), tower.dimension(), pos.east(1000), Set.of(out), true,
                RelayCatalysts.GILDED, true, 32, Set.of());
        manager.updateSnapshot(tower); manager.updateSnapshot(peer);
        Tap tap = new Tap(level, pos.getCenter().add(10, 0, 0), output);
        var inner = new RadioTransmitter(input, at(level, pos.getCenter())).frequencingType(SimpleRadioFrequencing.TRANSCEIVER); inner.identifier = ++nextId;
        var sender = new RadioTransmitter(input, at(level, pos.getCenter())).frequencingType(SimpleRadioFrequencing.TRANSCEIVER); sender.identifier = ++nextId;
        sender.routerAcceptor = packet -> { packet.transmissionPower -= 100; RadioSource branch = packet.copy(); branch.activity = packet.activity; inner.accept(branch); return false; };
        Set<Path> before = reports();
        try {
            send(sender, new byte[]{1}); check(tap.count == 1, "Unprofiled nested route must deliver exactly once");
            float baselinePower = tap.last.transmissionPower; double baselineNoise = tap.last.computeSeverity();
            int baselineHops = ((RelaySourceState) tap.last).repeaters$getRelayCount();
            refused(server, "radiorepeaters test start", 0); refused(server, "radiorepeaters test start 0", 4);
            command(server, "radiorepeaters test start"); refused(server, "radiorepeaters test start", 4);
            var graph = manager.network(level, in); manager.updateSnapshot(tower);
            check(graph == manager.network(level, in), "Recording a no-op snapshot must retain network identity");
            for (int i = 0; i < 3; i++) {
                byte[] data = {(byte) (i + 2)}; RadioSource packet = send(sender, data);
                check(tap.count == i + 2 && tap.last.data == data && tap.last.activity == 42 && tap.last.originalOwner.equals(packet.originalOwner),
                        "Capture must preserve deduplication, bytes and stream metadata");
                check(tap.last.transmissionPower == baselinePower && tap.last.computeSeverity() == baselineNoise
                        && ((RelaySourceState) tap.last).repeaters$getRelayCount() == baselineHops, "Capture must leave carrier, noise and counts unchanged");
            }
            ItemStack held = new ItemStack(SimpleRadioItems.TRANSCEIVER);
            SimpleRadioItems.TRANSCEIVER.setFrequency(held, input.getFrequency(), input.getModulation());
            RelayDiagnostics.describe(level, pos.getCenter(), held);
            tap.receiver.antennaPower++; send(sender, new byte[]{7});
            TowerManager.resourcesReloaded(server, "performance_fixture_refresh"); send(sender, new byte[]{8});
            command(server, "radiorepeaters test stop"); check(!RelayPerformance.running(server), "Explicit stop must disable capture");
            refused(server, "radiorepeaters test stop", 4);
            var report = report(before);
            check(count(report, "route_calls") >= 10 && count(report, "delivery_accepted") == 5, "Nested route calls and real accepted deliveries must be counted");
            check(count(report, "callback_rf_changes") >= 5 && count(report, "source_rf_repeats") > 0, "Capture must identify changed callback RF and stable subsequent packets");
            check(count(report, "snapshots_unchanged") > 0 && count(report, "receiver_state_changes") > 0, "No-op snapshots and endpoint RF changes must be observable");
            check(count(report, "egress_rf_checks") > 0 && count(report, "initial_ingress_rf_checks") > 0 && count(report, "final_ingress_rf_checks") > 0,
                    "Both entrance passes and receiver fan-out must be measured");
            check(report.getAsJsonObject("network_builds").has("diagnostic") && report.getAsJsonObject("network_builds").has("routing"),
                    "Diagnostic graph rebuilds must be separate from live cache misses");
            check(report.getAsJsonObject("cache_operations").keySet().stream().anyMatch(k -> k.startsWith("routing/path/hit")), "Live path-cache reuse must be counted");
            check(report.getAsJsonObject("invalidation_reasons").get("performance_fixture_refresh").getAsLong() == 1, "Invalidation reason must be preserved");
            double self = report.getAsJsonObject("route_self").get("total_ms").getAsDouble();
            double inclusive = report.getAsJsonObject("route_inclusive").get("total_ms").getAsDouble();
            double phases = report.getAsJsonObject("phase_self").entrySet().stream().mapToDouble(e -> e.getValue().getAsJsonObject().get("total_ms").getAsDouble()).sum();
            check(self > 0 && inclusive > self && Math.abs(self - phases) < .00001, "Exclusive phases must partition routing time without counting nested routing twice");
            check(!level.hasChunkAt(pos) && !level.hasChunkAt(peer.position()), "Profiling must not load saved tower terrain");
        } finally {
            if (RelayPerformance.running(server)) command(server, "radiorepeaters test stop");
            output.getReceivers().remove(tap.receiver); manager.removeAt(tower.dimension(), tower.position());
            manager.removeAt(peer.dimension(), peer.position());
        }
        helper.succeed();
    }
    // GameTest ticks are accelerated; allow a real second even on a fast host.
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "performance_timer", timeoutTicks = 30_000)
    public void timed_capture_stops_on_elapsed_seconds_and_writes_an_idle_report(GameTestHelper helper) {
        var server = helper.getLevel().getServer(); Set<Path> before = reports();
        check(server.getCommands().getDispatcher().getRoot().getChild("radiorepeater") == null,
                "Only the plural command root should be registered");
        command(server, "radiorepeaters test start 1");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(!RelayPerformance.running(server), "Timed capture must finish automatically"))
                .thenExecute(() -> {
                    var report = report(before); check(report.get("stop_reason").getAsString().equals("duration_elapsed"), "Timer must identify its stop reason");
                    check(report.get("elapsed_seconds").getAsDouble() >= 1, "Seconds must use elapsed time, not an assumed tick rate");
                    check(count(report, "route_calls") == 0, "Idle recording must not generate fake voice traffic");
                    check(report.getAsJsonArray("timeline").size() > 0, "Idle recording must still have a timeline");
                }).thenSucceed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "performance_shutdown")
    public void shutdown_flushes_capture_and_allows_a_fresh_session(GameTestHelper helper) {
        var server = helper.getLevel().getServer(); Set<Path> before = reports();
        command(server, "radiorepeaters test start"); RelayPerformance.stopping(server);
        check(!RelayPerformance.running(server), "Shutdown hook must release capture state");
        check(report(before).get("stop_reason").getAsString().equals("server_shutdown"), "Shutdown must save a complete report");
        command(server, "radiorepeaters test start"); command(server, "radiorepeaters test stop"); helper.succeed();
    }
}
