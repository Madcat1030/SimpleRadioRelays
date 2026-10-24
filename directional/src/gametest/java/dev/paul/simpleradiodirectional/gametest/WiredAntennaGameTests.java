package dev.paul.simpleradiodirectional.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.*;
import com.codinglitch.simpleradio.core.registry.blocks.*;
import com.codinglitch.simpleradio.core.registry.entities.Wire;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import dev.paul.simpleradiodirectional.DirectionalAntenna;
import dev.paul.simpleradiodirectional.block.*;
import dev.paul.simpleradiodirectional.radio.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

public final class WiredAntennaGameTests implements FabricGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "directional_wire_restore", timeoutTicks = 500)
    public void wire_restoration_preserves_gain_but_a_real_cut_removes_it(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos core = helper.absolutePos(new BlockPos(1, 1, 1)).offset(1500, 0, 1500);
        BlockPos remote = core.east(4), nearPos = core.east(), farPos = remote.west();
        List<ChunkPos> tickets = new ArrayList<>();
        for (int x = (core.getX() >> 4) - 1; x <= (remote.getX() >> 4) + 1; x++) {
            for (int z = (core.getZ() >> 4) - 1; z <= (core.getZ() >> 4) + 1; z++) {
                tickets.add(new ChunkPos(x, z));
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
            }
        }
        level.setBlockAndUpdate(core, Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(remote, Blocks.IRON_BLOCK.defaultBlockState());
        var mast = SimpleRadioBlocks.ANTENNA.defaultBlockState().setValue(AntennaBlock.AXIS, Direction.Axis.Y)
                .setValue(AntennaBlock.DOWN, true).setValue(AntennaBlock.COLUMN, true);
        level.setBlockAndUpdate(remote.above(), mast.setValue(AntennaBlock.UP, true).setValue(AntennaBlock.ATTACHED, true));
        level.setBlockAndUpdate(remote.above(2), mast.setValue(AntennaBlock.UP, false).setValue(AntennaBlock.ATTACHED, false));
        BlockPos dishPos = remote.above().north();
        level.setBlockAndUpdate(dishPos, DirectionalAntenna.DISH.defaultBlockState().setValue(ParabolicReceiverBlock.FACING, Direction.NORTH));
        DishGeometry.build(level, dishPos, Direction.NORTH);
        var dishEntity = (DishBlockEntity) level.getBlockEntity(dishPos);
        DishBlockEntity.tick(level, dishPos, dishEntity.getBlockState(), dishEntity);
        level.setBlockAndUpdate(nearPos, SimpleRadioBlocks.INSULATOR.defaultBlockState().setValue(InsulatorBlock.FACING, Direction.EAST));
        level.setBlockAndUpdate(farPos, SimpleRadioBlocks.INSULATOR.defaultBlockState().setValue(InsulatorBlock.FACING, Direction.WEST));
        BlockPos receiverPos = core.west();
        level.setBlockAndUpdate(receiverPos, SimpleRadioBlocks.RECEIVER.defaultBlockState().setValue(ReceiverBlock.FACING, Direction.WEST));
        BlockPos relayPos = core.north();
        boolean repeaters = FabricLoader.getInstance().isModLoaded("simpleradio_repeaters");
        if (repeaters) level.setBlockAndUpdate(relayPos, dev.paul.simpleradiorepeaters.SimpleRadioRepeaters.RELAY.defaultBlockState()
                .setValue(dev.paul.simpleradiorepeaters.block.RelayBlock.FACING, Direction.NORTH));
        helper.startSequence().thenWaitUntil(() -> {
            for (var ticket : tickets)
                check(level.areEntitiesLoaded(ticket.toLong()), "Fixture entity chunks must load");
            for (var pos : List.of(nearPos, farPos))
                check(((InsulatorBlockEntity) level.getBlockEntity(pos)).getRouter() != null, "Native insulator routers must initialize");
        }).thenExecute(() -> {
            var near = (InsulatorBlockEntity) level.getBlockEntity(nearPos);
            var far = (InsulatorBlockEntity) level.getBlockEntity(farPos);
            // This fixture is outside the test structure, so clear wires from a previous failed run.
            Set<Wire> priorWires = new HashSet<>();
            for (var socket : List.of(near, far))
                for (var prior : socket.getRouter().wires)
                    if (prior instanceof Wire existing) priorWires.add(existing);
            priorWires.forEach(Wire::burnOut);
            Wire wire = Wire.connect(near, far, level);
            check(wire != null, "Native wire fixture must connect");
            var block = (ReceiverBlockEntity) level.getBlockEntity(receiverPos);
            var frequency = ServerSimpleRadioApi.getInstance().frequencies().getOrCreate("089.39", Frequency.Modulation.AMPLITUDE);
            var receiver = new RadioReceiver(frequency, WorldlyPosition.of(receiverPos, level, receiverPos)).frequencingType(SimpleRadioFrequencing.RECEIVER);
            receiver.antennaPower = 64;
            block.receiver = receiver; block.frequency = frequency; block.active = true;
            block.catalyst = CatalystRegistry.get(Items.GILDED_BLACKSTONE); block.catalyzed = true;
            DishManager.dirtyReceiver(receiver);
            DishManager.refreshReceiver(level, block);
            if (repeaters) { DishManager.dirtyRelay(level, relayPos); DishManager.refreshRelay(level, relayPos, core); }
            check(!DishManager.receivers(receiver).isEmpty(), "Wired native receiver must discover the dish");
            if (repeaters) check(!DishManager.relays(level.dimension().location().toString(), relayPos).isEmpty(), "Wired relay must discover the dish");

            // On chunk load, insulator routers exist before the wire entity reconnects their lists.
            near.getRouter().wires.clear(); far.getRouter().wires.clear();
            check(MastScanner.scan(level, core).isEmpty(), "Reproduce the transient empty native traversal");
            DishManager.dirtyReceiver(receiver); DishManager.refreshReceiver(level, block);
            if (repeaters) { DishManager.dirtyRelay(level, relayPos); DishManager.refreshRelay(level, relayPos, core); }
            check(!DishManager.receivers(receiver).isEmpty(), "Temporary wire restoration must preserve native directional gain");
            if (repeaters) check(!DishManager.relays(level.dimension().location().toString(), relayPos).isEmpty(), "Temporary wire restoration must preserve saved relay directional gain");
            wire.tick();
            check(near.hasWire(wire) && far.hasWire(wire), "The real wire tick must reconnect both endpoints");

            wire.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            check(!DishManager.receivers(receiver).isEmpty(), "Chunk unloading must not be treated as a cut");
            Wire replacement = Wire.connect(near, far, level);
            check(replacement != null, "A replacement native wire must connect");
            int chunks = level.getChunkSource().getLoadedChunksCount();
            replacement.burnOut();
            check(DishManager.receivers(receiver).isEmpty(), "An actual wire cut must remove native gain immediately");
            if (repeaters) check(DishManager.relays(level.dimension().location().toString(), relayPos).isEmpty(), "An actual wire cut must remove saved relay gain immediately");
            check(chunks == level.getChunkSource().getLoadedChunksCount(), "Wire notifications must not load chunks");
            for (var ticket : tickets) level.setChunkForced(ticket.x, ticket.z, false);
            helper.succeed();
        });
    }
}
