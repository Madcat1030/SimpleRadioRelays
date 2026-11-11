package dev.paul.simpleradiorepeaters.block;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.item.CardState;
import dev.paul.simpleradiorepeaters.monitor.*;
import dev.paul.simpleradiorepeaters.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** The client receives only the visible page. Exact TX-only coordinates stay server-side. */
public final class RelayMonitorBlockEntity extends ChannelBankBlockEntity {
    private static final List<String> INSERT_CARD = List.of("Insert tuned card", "for Relay diagnostics");
    private enum MapPage { CLOSE, WIDE }
    private List<String> lines = INSERT_CARD;
    @Nullable private MonitorMap displayedMap;
    private final Map<MapPage, MonitorMap> mapReadings = new EnumMap<>(MapPage.class);
    @Nullable private MapPage mapPage;
    @Nullable private RelaySurvey.Result reading;
    @Nullable private UUID selected, lastRelay;
    @Nullable private ChannelKey lastChannel;
    private long lastRevision = Long.MIN_VALUE;

    public RelayMonitorBlockEntity(BlockPos pos, BlockState state) { super(SimpleRadioRepeaters.RELAY_MONITOR_ENTITY, 1, pos, state); }
    @Override public List<CardState> routingCards() { return List.of(); }
    public List<String> lines() { return lines; }
    @Nullable public MonitorMap displayedMap() { return displayedMap; }
    @Nullable public RelaySurvey.Result reading() { return reading; }
    public static void tick(Level level, BlockPos pos, BlockState state, RelayMonitorBlockEntity monitor) {
        ChannelBankBlockEntity.tick(level, pos, state, monitor);
        ChannelKey channel = CardState.of(monitor.getItem(0)).channel();
        if (level.getGameTime() % 20 == 0 || !Objects.equals(channel, monitor.lastChannel)) monitor.refresh();
    }
    public void refresh() {
        if (!(level instanceof ServerLevel server)) return;
        TowerManager manager = TowerManager.get(server.getServer());
        var relay = relay(); ChannelKey channel = CardState.of(getItem(0)).channel();
        UUID id = relay == null ? null : relay.relayId();
        long revision = manager == null ? -1 : manager.revision();
        if (revision == lastRevision && Objects.equals(channel, lastChannel) && Objects.equals(id, lastRelay)) return;
        if (!Objects.equals(channel, lastChannel) || !Objects.equals(id, lastRelay)) {
            selected = null; mapPage = null;
        }
        lastChannel = channel; lastRelay = id; lastRevision = revision;
        reading = null; mapReadings.clear();
        if (channel == null) { setLines(INSERT_CARD); return; }
        if (relay == null || manager == null) { setLines(List.of("RELAY MONITOR", "Attach atop a Relay")); return; }
        TowerRecord home = manager.snapshot(id);
        if (home == null || !home.hardwareReady()) {
            String status = !relay.enabled() ? "RELAY OFF" : !relay.hasCatalyst() ? "INSERT CATALYST"
                    : relay.getAntennaPower() <= 0 ? "NO ANTENNA" : "CHECK ANTENNA";
            setLines(List.of("RELAY MONITOR", channel.frequency() + " " + channel.modulation(), status)); return;
        }
        reading = RelaySurvey.scan(server.getServer(), manager, home, channel);
        if (selected != null && reading.entries().stream().noneMatch(e -> e.id().equals(selected))) selected = null;
        updateDisplay();
    }
    public void nextPage() {
        refresh();
        if (reading == null) return;
        if (mapPage == null && selected == null) mapPage = MapPage.CLOSE;
        else if (mapPage == MapPage.CLOSE) mapPage = MapPage.WIDE;
        else {
            int next = selectedIndex() + 1;
            selected = next >= reading.entries().size() ? null : reading.entries().get(next).id();
            mapPage = null;
        }
        updateDisplay();
    }
    private int selectedIndex() {
        if (reading != null && selected != null)
            for (int i = 0; i < reading.entries().size(); i++) if (reading.entries().get(i).id().equals(selected)) return i;
        return -1;
    }
    private static String dimension(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "Overworld"; case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End"; default -> id;
        };
    }
    private static String coordinates(BlockPos pos) { return "X " + pos.getX() + " Y " + pos.getY() + " Z " + pos.getZ(); }
    private void updateDisplay() {
        if (reading == null) return;
        if (mapPage != null && level instanceof ServerLevel server) {
            MonitorMap map = mapReadings.get(mapPage);
            if (map == null) {
                TowerManager manager = TowerManager.get(server.getServer());
                TowerRecord home = manager == null ? null : manager.snapshot(lastRelay);
                if (home == null) return;
                map = RelayMapSurvey.scan(server, manager, home, reading.channel(), mapPage == MapPage.WIDE);
                mapReadings.put(mapPage, map);
            }
            setDisplay(List.of(), map);
            return;
        }
        List<String> text = new ArrayList<>();
        String channel = reading.channel().frequency() + " " + reading.channel().modulation();
        int index = selectedIndex();
        if (index < 0) {
            text.add("TX OVERVIEW  " + channel);
            text.add(dimension(reading.dimension())); text.add(coordinates(reading.origin()));
            text.add(String.format(Locale.ROOT, "ANT %.1f (mast)", reading.antenna()));
            text.add(String.format(Locale.ROOT, "Effective %.1f", reading.effectiveAntenna()));
            text.add("TX " + reading.tx() + "   RX " + reading.rx());
            text.add("Other nodes " + reading.other());
            text.add(reading.pending() > 0 ? "Surveying " + reading.pending() + " paths" : "Nominal direct links");
        } else {
            var entry = reading.entries().get(index);
            text.add(channel + "  " + entry.directionLabel()); text.add(dimension(entry.dimension()));
            if (entry.rx()) {
                text.add(coordinates(Objects.requireNonNull(entry.position())));
                text.add(String.format(Locale.ROOT, "Radio distance %.0f blocks", entry.distance()));
                text.add(String.format(Locale.ROOT, "Terrain +%.1f%%", entry.terrainPercent()));
            } else {
                text.add(entry.bearing()); text.add("~" + MonitorMath.approximateDistance(entry.distance()) + " blocks");
            }
            text.add("Screen " + (index + 4) + "/" + (reading.entries().size() + 3));
        }
        setLines(List.copyOf(text));
    }
    private void setLines(List<String> value) {
        setDisplay(value, null);
    }
    private void setDisplay(List<String> value, @Nullable MonitorMap map) {
        if (value.equals(lines) && Objects.equals(map, displayedMap)) return;
        lines = value; displayedMap = map;
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    public boolean bindCompass(Player player, InteractionHand hand) {
        UUID requested = selected;
        refresh();
        if (!(level instanceof ServerLevel server) || reading == null || mapPage != null || !player.getItemInHand(hand).is(Items.COMPASS)) return false;
        if (requested != null && !requested.equals(selected)) {
            player.displayClientMessage(Component.translatable("message.simpleradio_repeaters.monitor_target_gone"), true); return false;
        }
        TowerManager manager = TowerManager.get(server.getServer());
        TowerRecord target = manager == null ? null : manager.snapshot(selected == null ? lastRelay : selected);
        if (target == null) return false;
        var entry = selected == null ? null : reading.entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null);
        if (selected != null && entry == null) return false;
        boolean estimated = entry != null && !entry.rx();
        BlockPos position = entry == null ? reading.origin() : entry.compassTarget();
        ItemStack held = player.getItemInHand(hand), bound = held.copy(); bound.setCount(1);
        CompoundTag tag = bound.getOrCreateTag();
        tag.put(CompassItem.TAG_LODESTONE_POS, NbtUtils.writeBlockPos(position));
        tag.putString(CompassItem.TAG_LODESTONE_DIMENSION, target.dimension());
        tag.putBoolean(CompassItem.TAG_LODESTONE_TRACKED, false);
        if (held.getCount() == 1) held.setTag(tag);
        else {
            if (!player.getAbilities().instabuild) held.shrink(1);
            give(player, bound);
        }
        player.displayClientMessage(Component.translatable(estimated ? "message.simpleradio_repeaters.monitor_bound_estimate"
                : "message.simpleradio_repeaters.monitor_bound", dimension(target.dimension())), true);
        return true;
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag(); ListTag text = new ListTag();
        if (displayedMap != null) {
            tag.putIntArray("MonitorMap", displayedMap.cells());
            tag.putBoolean("MonitorMapPending", displayedMap.pending());
            tag.putBoolean("MonitorMapWide", displayedMap.wide());
        } else {
            lines.forEach(line -> text.add(StringTag.valueOf(line))); tag.put("MonitorLines", text);
        }
        return tag;
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        var text = tag.getList("MonitorLines", Tag.TAG_STRING); List<String> value = new ArrayList<>();
        for (int i = 0; i < Math.min(8, text.size()); i++) value.add(text.getString(i));
        displayedMap = tag.contains("MonitorMap", Tag.TAG_INT_ARRAY)
                ? MonitorMap.fromCells(tag.getIntArray("MonitorMap"), tag.getBoolean("MonitorMapPending"), tag.getBoolean("MonitorMapWide")) : null;
        lines = value.isEmpty() && displayedMap == null ? INSERT_CARD : List.copyOf(value);
        reading = null; mapReadings.clear(); mapPage = null;
        selected = null; lastRelay = null; lastChannel = null; lastRevision = Long.MIN_VALUE;
    }
}
