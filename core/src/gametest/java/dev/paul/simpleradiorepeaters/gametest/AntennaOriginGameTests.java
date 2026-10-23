package dev.paul.simpleradiorepeaters.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import java.util.*;

public class AntennaOriginGameTests implements FabricGameTest {
    private static short nextId = 31650;
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    private static final class Tap extends RadioRouter {
        final RadioReceiver receiver; int count; RadioSource last;
        Tap(ServerLevel world, BlockPos pos, Frequency frequency) {
            super(new WorldlyPosition((float) pos.getCenter().x, (float) pos.getCenter().y, (float) pos.getCenter().z, world));
            identifier = ++nextId;
            receiver = new RadioReceiver(frequency, position).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
            receiver.identifier = ++nextId; receiver.owner = new ArmorStand(world, pos.getX(), pos.getY(), pos.getZ());
            receiver.addRouter(this); frequency.getReceivers().add(receiver);
        }
        @Override public void take(Source source) { count++; last = (RadioSource) source; }
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void saved_relay_entrance_exit_and_network_geometry_use_antenna_origins(GameTestHelper helper) {
        ServerLevel world = helper.getLevel(); TowerManager manager = TowerManager.get(world.getServer());
        var frequencies = ServerSimpleRadioApi.getInstance().frequencies();
        var input = frequencies.getOrCreate("097.83", Frequency.Modulation.AMPLITUDE);
        var output = frequencies.getOrCreate("097.84", Frequency.Modulation.AMPLITUDE);
        ChannelKey in = ChannelKey.from(input), out = ChannelKey.from(output);
        BlockPos box = new BlockPos(1100000, 80, -1100000), base = box.east(20000);
        TowerRecord record = new TowerRecord(UUID.randomUUID(), world.dimension().location().toString(), box,
                Set.of(in, out), true, RelayCatalysts.GILDED, true, 32, Set.of(), null, RelayAttachment.NONE,
                new TransponderChannels(in, out, null, false), base);
        record = TowerRecord.load(record.save());
        record = record.settings(record.channels(), true, record.catalystItem(), null, record.attachment(), record.transponder());
        check(record.antennaBase().equals(base) && record.invalidate().antennaBase().equals(base), "Settings and invalidation must preserve RF origin");
        var legacy = record.save(); legacy.remove("AntennaBase");
        check(TowerRecord.load(legacy).antennaBase().equals(box), "Old saved records must retain their site until the first verified scan");
        TowerRecord peer = new TowerRecord(UUID.randomUUID(), record.dimension(), box.west(20000), Set.of(out), true,
                RelayCatalysts.GILDED, true, 32, Set.of(), null, RelayAttachment.NONE, null, base.east(100));
        Tap nearBase = new Tap(world, base.east(1), output), nearBox = new Tap(world, box.east(1), output);
        manager.updateSnapshot(record); manager.updateSnapshot(peer);
        try {
            var network = manager.network(world, in);
            int index = -1;
            for (int i = 0; i < network.ports().size(); i++) if (network.ports().get(i).tower().id().equals(record.id())) index = i;
            check(index >= 0 && !network.edges().get(index).isEmpty(), "Nearby antenna bases must link despite distant relay boxes");
            send(world, box, input);
            check(nearBase.count == 0 && nearBox.count == 0, "Standing beside only the offset box must not enter the network");
            send(world, base, input);
            check(nearBase.count == 1 && nearBox.count == 0, "Only the endpoint near the antenna must hear the translated carrier");
            check(new net.minecraft.world.phys.Vec3(nearBase.last.origin).equals(base.getCenter()), "The emitted carrier origin must be the antenna base centre");
            for (BlockPos pos : List.of(box, base, peer.position(), peer.antennaBase()))
                check(!world.hasChunkAt(pos), "Saved-origin routing must not force terrain loading");
        } finally {
            output.getReceivers().remove(nearBase.receiver); output.getReceivers().remove(nearBox.receiver);
            manager.removeAt(record.dimension(), record.position()); manager.removeAt(peer.dimension(), peer.position());
        }
        helper.succeed();
    }
    private static void send(ServerLevel world, BlockPos pos, Frequency frequency) {
        var centre = pos.getCenter(); var location = new WorldlyPosition((float) centre.x, (float) centre.y, (float) centre.z, world);
        var transmitter = new RadioTransmitter(frequency, location).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
        transmitter.identifier = ++nextId;
        transmitter.accept(new RadioSource(UUID.randomUUID(), location, new byte[]{1}, 1));
    }
}
