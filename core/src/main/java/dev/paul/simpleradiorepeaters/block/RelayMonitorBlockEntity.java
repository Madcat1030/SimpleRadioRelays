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
    private enum Page { MAP, OVERVIEW, DIRECT_LINKS, DIRECT, TOWER }
    private List<String> lines = INSERT_CARD;
    @Nullable private MonitorMap displayedMap;
    private Page page = Page.MAP;
    private boolean showBack, showHome, towerDirect;
    private int directPage;
    @Nullable private RelayMapSurvey.Network network;
    @Nullable private RelayMapSurvey.View mapView;
    @Nullable private UUID focus;
    @Nullable private RelaySurvey.Result reading;
    @Nullable private UUID selected, lastRelay;
    @Nullable private ChannelKey lastChannel;
    private long lastRevision = Long.MIN_VALUE;

    public RelayMonitorBlockEntity(BlockPos pos, BlockState state) { super(SimpleRadioRepeaters.RELAY_MONITOR_ENTITY, 1, pos, state); }
    @Override public List<CardState> routingCards() { return List.of(); }
    public List<String> lines() { return lines; }
    @Nullable public MonitorMap displayedMap() { return displayedMap; }
    @Nullable public RelaySurvey.Result reading() { return reading; }
    public boolean showBack() { return showBack; }
    public boolean showHome() { return showHome; }
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
            selected = null; focus = null; page = Page.MAP; directPage = 0; towerDirect = false;
        }
        lastChannel = channel; lastRelay = id; lastRevision = revision;
        reading = null; network = null; mapView = null;
        if (channel == null) { setLines(INSERT_CARD); return; }
        if (relay == null || manager == null) { setLines(List.of("RELAY MONITOR", "Attach atop a Relay")); return; }
        TowerRecord home = manager.snapshot(id);
        if (home == null || !home.hardwareReady()) {
            String status = !relay.enabled() ? "RELAY OFF" : !relay.hasCatalyst() ? "INSERT CATALYST"
                    : relay.getAntennaPower() <= 0 ? "NO ANTENNA" : "CHECK ANTENNA";
            setLines(List.of("RELAY MONITOR", channel.frequency() + " " + channel.modulation(), status)); return;
        }
        reading = RelaySurvey.scan(server.getServer(), manager, home, channel);
        if (page == Page.DIRECT && directEntry(selected) == null) {
            selected = null;
            page = Page.DIRECT_LINKS;
        }
        directPage = Math.min(directPage, directPageCount() - 1);
        updateDisplay();
    }
    public void clickScreen(double x, double y) {
        // Resolve map hits against the displayed cells before a refresh can move them.
        UUID clicked = displayedMap == null || mapView == null ? null
                : mapView.targets().get(displayedMap.cellAt(x, y));
        var shownEntries = page == Page.DIRECT_LINKS ? directEntries() : List.<RelaySurvey.Entry>of();
        Page shown = page;
        refresh();
        if (reading == null || shown != page) return;
        if (page == Page.MAP) {
            if (focus != null && MonitorScreen.home(x, y)) {
                focus = null;
                mapView = null;
            } else if (MonitorScreen.footer(x, y)) {
                openCenteredDetails();
            } else if (clicked != null && network != null && network.tower(clicked) != null) {
                if (clicked.equals(focus == null ? lastRelay : focus)) openCenteredDetails();
                else {
                    focus = clicked.equals(lastRelay) ? null : clicked;
                    mapView = null;
                }
            } else return;
        } else if (MonitorScreen.back(x, y)) {
            page = page == Page.DIRECT ? Page.DIRECT_LINKS : Page.MAP;
            selected = null;
            towerDirect = false;
        } else {
            switch (page) {
                case OVERVIEW -> {
                    if (!reading.entries().isEmpty()) { page = Page.DIRECT_LINKS; directPage = 0; }
                }
                case DIRECT_LINKS -> {
                    int row = MonitorScreen.directRow(x, y);
                    if (row >= 0 && row < shownEntries.size() && directEntry(shownEntries.get(row).id()) != null) {
                        selected = shownEntries.get(row).id();
                        page = Page.DIRECT;
                    } else if (directPage + 1 < directPageCount()) directPage++;
                    else page = Page.OVERVIEW;
                }
                case TOWER -> {
                    if (focusedDirectEntry() != null) towerDirect = !towerDirect;
                }
                default -> { }
            }
        }
        updateDisplay();
    }

    private void openCenteredDetails() {
        page = focus == null ? Page.OVERVIEW : Page.TOWER;
        towerDirect = false;
    }

    private int directPageCount() {
        return Math.max(1, (reading.entries().size() + MonitorScreen.DIRECT_ROWS - 1) / MonitorScreen.DIRECT_ROWS);
    }

    private List<RelaySurvey.Entry> directEntries() {
        if (reading == null) return List.of();
        int first = directPage * MonitorScreen.DIRECT_ROWS;
        return reading.entries().subList(first, Math.min(first + MonitorScreen.DIRECT_ROWS, reading.entries().size()));
    }

    @Nullable private RelaySurvey.Entry directEntry(@Nullable UUID id) {
        return id == null ? null : reading.entries().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }

    @Nullable private RelaySurvey.Entry focusedDirectEntry() {
        var tower = network == null || focus == null ? null : network.tower(focus);
        var entry = directEntry(focus);
        return tower == null || entry == null || tower.estimated() && entry.rx() ? null : entry;
    }

    private static String dimension(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "Overworld"; case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End"; default -> id;
        };
    }
    private static String coordinates(BlockPos pos) { return RelayMapSurvey.coordinates(pos); }
    private void updateDisplay() {
        if (reading == null) return;
        if ((page == Page.MAP || page == Page.TOWER) && level instanceof ServerLevel server) {
            if (network == null) {
                TowerManager manager = TowerManager.get(server.getServer());
                TowerRecord home = manager == null ? null : manager.snapshot(lastRelay);
                if (home == null) return;
                network = RelayMapSurvey.scan(server, manager, home, reading.channel());
            }
            var tower = focus == null ? null : network.tower(focus);
            if (focus != null && tower == null) {
                focus = null;
                mapView = null;
                page = Page.MAP;
            }
            if (page == Page.TOWER && tower != null) {
                var direct = focusedDirectEntry();
                setDisplay(towerDirect && direct != null ? directDetails(direct, "Direct link  2/2")
                        : towerDetails(tower, direct != null), null, true, false);
            } else {
                if (mapView == null) mapView = network.view(focus);
                setDisplay(List.of(), mapView.map(), false, focus != null);
            }
            return;
        }
        List<String> text = new ArrayList<>();
        if (page == Page.DIRECT) {
            text.addAll(directDetails(Objects.requireNonNull(directEntry(selected)), "Direct link from home"));
        } else if (page == Page.DIRECT_LINKS) {
            text.add("DIRECT LINKS  " + (directPage + 1) + "/" + directPageCount());
            for (var entry : directEntries()) {
                String position = entry.rx() ? coordinates(Objects.requireNonNull(entry.position()))
                        : "~" + MonitorMath.approximateDistance(entry.distance()) + " " + entry.bearing();
                text.add(entry.directionLabel() + "  " + position + "  " + dimension(entry.dimension()));
            }
        } else {
            text.add("TX OVERVIEW  " + channelLabel());
            text.add(dimension(reading.dimension())); text.add(coordinates(reading.origin()));
            text.add(String.format(Locale.ROOT, "ANT %.1f (mast)", reading.antenna()));
            text.add(String.format(Locale.ROOT, "Effective %.1f", reading.effectiveAntenna()));
            text.add("TX " + reading.tx() + "   RX " + reading.rx());
            text.add("Other nodes " + reading.other());
            text.add(reading.pending() > 0 ? "Surveying " + reading.pending() + " paths" : "Nominal direct links");
        }
        setDisplay(text, null, true, false);
    }

    private String channelLabel() { return reading.channel().frequency() + " " + reading.channel().modulation(); }

    private List<String> directDetails(RelaySurvey.Entry entry, String caption) {
        List<String> text = new ArrayList<>();
        text.add(channelLabel() + "  " + entry.directionLabel()); text.add(dimension(entry.dimension()));
        if (entry.rx()) {
            text.add(coordinates(Objects.requireNonNull(entry.position())));
            text.add(String.format(Locale.ROOT, "Radio distance %.0f blocks", entry.distance()));
            text.add(String.format(Locale.ROOT, "Terrain +%.1f%%", entry.terrainPercent()));
        } else {
            text.add(entry.bearing()); text.add("~" + MonitorMath.approximateDistance(entry.distance()) + " blocks");
        }
        text.add(caption);
        return text;
    }

    private List<String> towerDetails(RelayMapSurvey.Tower tower, boolean hasDirect) {
        String direction = switch (tower.kind()) {
            case TX_RX -> "TX/RX";
            case RX_ONLY -> "RX only";
            case ESTIMATE -> "TX only";
            case HOME -> "Home";
        };
        List<String> text = new ArrayList<>(List.of(channelLabel() + "  " + direction, dimension(reading.dimension())));
        if (tower.estimated()) {
            text.add(tower.estimate() + " from");
            text.add(coordinates(Objects.requireNonNull(tower.anchor())));
            text.add("Estimated location");
        } else text.add(tower.location());
        text.add(hasDirect ? "Network reach  1/2" : "Network reach from home");
        return text;
    }

    private void setLines(List<String> value) { setDisplay(value, null, false, false); }

    private void setDisplay(List<String> value, @Nullable MonitorMap map, boolean back, boolean home) {
        if (value.equals(lines) && Objects.equals(map, displayedMap) && back == showBack && home == showHome) return;
        lines = List.copyOf(value); displayedMap = map; showBack = back; showHome = home;
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public boolean bindCompass(Player player, InteractionHand hand) {
        UUID requested = selected;
        UUID requestedFocus = focus;
        Page requestedPage = page;
        refresh();
        if (reading == null || !player.getItemInHand(hand).is(Items.COMPASS)) return false;
        if (requestedPage == Page.TOWER) {
            var tower = network == null || requestedFocus == null ? null : network.tower(requestedFocus);
            if (page != Page.TOWER || tower == null) return false;
            var direct = towerDirect ? focusedDirectEntry() : null;
            return direct == null ? bindCompass(player, hand, tower.compassTarget(), reading.dimension(), tower.estimated())
                    : bindCompass(player, hand, direct.compassTarget(), direct.dimension(), !direct.rx());
        }
        if (page == Page.MAP || page == Page.DIRECT_LINKS || !(level instanceof ServerLevel server)) return false;
        if (requested != null && !requested.equals(selected)) {
            player.displayClientMessage(Component.translatable("message.simpleradio_repeaters.monitor_target_gone"), true); return false;
        }
        TowerManager manager = TowerManager.get(server.getServer());
        TowerRecord target = manager == null ? null : manager.snapshot(selected == null ? lastRelay : selected);
        if (target == null) return false;
        var entry = directEntry(selected);
        if (selected != null && entry == null) return false;
        boolean estimated = entry != null && !entry.rx();
        BlockPos position = entry == null ? reading.origin() : entry.compassTarget();
        return bindCompass(player, hand, position, target.dimension(), estimated);
    }
    private boolean bindCompass(Player player, InteractionHand hand, BlockPos position, String dimensionId, boolean estimated) {
        ItemStack held = player.getItemInHand(hand), bound = held.copy(); bound.setCount(1);
        CompoundTag tag = bound.getOrCreateTag();
        tag.put(CompassItem.TAG_LODESTONE_POS, NbtUtils.writeBlockPos(position));
        tag.putString(CompassItem.TAG_LODESTONE_DIMENSION, dimensionId);
        tag.putBoolean(CompassItem.TAG_LODESTONE_TRACKED, false);
        if (held.getCount() == 1) held.setTag(tag);
        else {
            if (!player.getAbilities().instabuild) held.shrink(1);
            give(player, bound);
        }
        player.displayClientMessage(Component.translatable(estimated ? "message.simpleradio_repeaters.monitor_bound_estimate"
                : "message.simpleradio_repeaters.monitor_bound", dimension(dimensionId)), true);
        return true;
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag(); ListTag text = new ListTag();
        if (showBack) tag.putBoolean("MonitorBack", true);
        if (showHome) tag.putBoolean("MonitorHome", true);
        if (displayedMap != null) {
            tag.putIntArray("MonitorMap", displayedMap.cells());
            if (!displayedMap.links().isEmpty()) tag.putIntArray("MonitorMapLinks", displayedMap.linkCells());
            tag.putBoolean("MonitorMapPending", displayedMap.pending());
            tag.putBoolean("MonitorMapWide", true);
            tag.putInt("MonitorMapCenter", displayedMap.centerKind().ordinal());
            tag.putString("MonitorMapLocation", displayedMap.location());
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
                ? MonitorMap.fromCells(tag.getIntArray("MonitorMap"), tag.getIntArray("MonitorMapLinks"),
                        tag.getBoolean("MonitorMapPending"), tag.contains("MonitorMapCenter") ? tag.getInt("MonitorMapCenter")
                                : MonitorMap.Kind.HOME.ordinal(), tag.getString("MonitorMapLocation")) : null;
        lines = value.isEmpty() && displayedMap == null ? INSERT_CARD : List.copyOf(value);
        showBack = tag.getBoolean("MonitorBack"); showHome = tag.getBoolean("MonitorHome");
        reading = null; network = null; mapView = null; focus = null; page = Page.MAP;
        directPage = 0; towerDirect = false;
        selected = null; lastRelay = null; lastChannel = null; lastRevision = Long.MIN_VALUE;
    }
}
