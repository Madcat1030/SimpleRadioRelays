package dev.paul.simpleradiodirectional.gametest;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.*;
import com.codinglitch.simpleradio.core.registry.blocks.*;
import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiodirectional.DirectionalAntenna;
import dev.paul.simpleradiodirectional.block.*;
import dev.paul.simpleradiodirectional.radio.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.concurrent.*;

public final class DirectionalGameTests implements FabricGameTest {
    private record Tower(ServerLevel level, BlockPos core, BlockPos mast, BlockPos center, Direction facing) {}
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    private static Tower mast(GameTestHelper helper, Direction facing) {
        var level=helper.getLevel(); BlockPos center=helper.absolutePos(new BlockPos(1,1,1));
        BlockPos mast=center.relative(facing.getOpposite()), core=mast.below();
        level.setBlockAndUpdate(core,Blocks.IRON_BLOCK.defaultBlockState());
        var state=SimpleRadioBlocks.ANTENNA.defaultBlockState().setValue(AntennaBlock.AXIS,Direction.Axis.Y)
                .setValue(AntennaBlock.DOWN,true).setValue(AntennaBlock.COLUMN,true);
        level.setBlockAndUpdate(mast,state.setValue(AntennaBlock.UP,true).setValue(AntennaBlock.ATTACHED,true));
        level.setBlockAndUpdate(mast.above(),state.setValue(AntennaBlock.UP,false).setValue(AntennaBlock.ATTACHED,false));
        return new Tower(level,core,mast,center,facing);
    }
    private static void dish(Tower tower) {
        tower.level().setBlockAndUpdate(tower.center(),DirectionalAntenna.DISH.defaultBlockState().setValue(ParabolicReceiverBlock.FACING,tower.facing()));
        DishGeometry.build(tower.level(),tower.center(),tower.facing());
        var entity=(DishBlockEntity)tower.level().getBlockEntity(tower.center());
        DishBlockEntity.tick(tower.level(),tower.center(),entity.getBlockState(),entity);
        check(DishGeometry.validate(tower.level(),tower.center(),tower.facing())==DishGeometry.Validity.VALID,"Dish assembly must be valid");
    }
    private static RadioReceiver receiver(Tower tower) {
        Direction face=tower.facing().getClockWise(); BlockPos pos=tower.core().relative(face);
        tower.level().setBlockAndUpdate(pos,SimpleRadioBlocks.RECEIVER.defaultBlockState().setValue(ReceiverBlock.FACING,face));
        var entity=(ReceiverBlockEntity)tower.level().getBlockEntity(pos);
        var frequency=ServerSimpleRadioApi.getInstance().frequencies().getOrCreate("089.17",Frequency.Modulation.FREQUENCY);
        var receiver=new RadioReceiver(frequency,WorldlyPosition.of(pos,tower.level(),pos)).frequencingType(SimpleRadioFrequencing.RECEIVER);
        receiver.antennaPower=16;
        entity.receiver=receiver; entity.catalyst=CatalystRegistry.get(net.minecraft.world.item.Items.GILDED_BLACKSTONE);
        entity.catalyzed=true; entity.active=true; entity.frequency=frequency;
        DishManager.refreshReceiver(tower.level(),entity);
        return receiver;
    }
    private static RadioTransmitter sender(Tower tower, RadioReceiver receiver, double bearing, double distance) {
        double angle=Math.toRadians(bearing);
        // All signal tests use the North-facing fixture.
        var location=new WorldlyPosition((float)(tower.mast().getX()+.5+Math.sin(angle)*distance),
                (float)(tower.mast().getY()+.5),(float)(tower.mast().getZ()+.5-Math.cos(angle)*distance),tower.level());
        return new RadioTransmitter(receiver.frequency,location).frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
    }
    private static RadioSource packet(RadioTransmitter sender, float power) {
        var source=new RadioSource(UUID.randomUUID(),sender.getLocation(),new byte[]{1},1);
        source.frequencingType=SimpleRadioFrequencing.TRANSCEIVER.id; source.transmissionPower=power; source.transmissionCap=power;
        return source;
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void vertical_non_top_mast_and_clearance_are_required(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH);
        check(DishGeometry.room(tower.level(),tower.center(),Direction.NORTH),"Valid vertical section must accept dish");
        check(!DishGeometry.validMast(tower.level(),tower.mast().above()),"Top section must not accept dish");
        tower.level().setBlockAndUpdate(tower.center().east(),Blocks.GLASS.defaultBlockState());
        check(!DishGeometry.room(tower.level(),tower.center(),Direction.NORTH),"Occupied reservation cell must reject placement");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void all_four_facings_reserve_exactly_three_by_three(GameTestHelper helper) {
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            // Clear the preceding fixture without relying on drops.
            BlockPos origin=helper.absolutePos(BlockPos.ZERO);
            for(int x=0;x<3;x++) for(int y=0;y<3;y++) for(int z=0;z<3;z++) helper.getLevel().removeBlock(origin.offset(x,y,z),false);
            var tower=mast(helper,facing); dish(tower);
            for(int i=0;i<9;i++) check(DishGeometry.center(DishGeometry.cell(tower.center(),facing,i),facing,i).equals(tower.center()),"Part offsets must invert for every facing");
            int count=0;
            for(int i=0;i<9;i++) { var state=tower.level().getBlockState(DishGeometry.cell(tower.center(),facing,i));
                if(state.is(DirectionalAntenna.PART)||state.is(DirectionalAntenna.DISH))count++; }
            check(count==9,"Dish must own nine cells");
        }
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void panel_break_cleans_assembly_and_drops_one_dish(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH); dish(tower);
        var bounds=new AABB(tower.center()).inflate(3);
        long before=tower.level().getEntitiesOfClass(ItemEntity.class,bounds).stream().filter(e->e.getItem().is(DirectionalAntenna.DISH.asItem())).count();
        tower.level().destroyBlock(DishGeometry.cell(tower.center(),Direction.NORTH,0),true);
        long after=tower.level().getEntitiesOfClass(ItemEntity.class,bounds).stream().filter(e->e.getItem().is(DirectionalAntenna.DISH.asItem())).count();
        check(after-before==1,"One broken panel must drop exactly one complete dish");
        for(int i=0;i<9;i++) check(tower.level().getBlockState(DishGeometry.cell(tower.center(),Direction.NORTH,i)).isAir(),"All nine cells must be removed");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void removing_upper_antenna_detaches_dish(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH); dish(tower);
        tower.level().removeBlock(tower.mast().above(),false);
        var entity=(DishBlockEntity)tower.level().getBlockEntity(tower.center());
        // The first periodic pass performs structural validation.
        helper.runAfterDelay(25,()->{
            check(!tower.level().getBlockState(tower.center()).is(DirectionalAntenna.DISH),"Dish must detach when its mast becomes the top");
            helper.succeed();
        });
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void full_wedge_extends_native_reach_and_preserves_audio_power(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH); dish(tower); var receiver=receiver(tower);
        var sender=sender(tower,receiver,0,2500); var source=packet(sender,1000);
        check(sender.shouldRouteTo(source,receiver),"Front signal beyond ordinary handheld reach must pass native gate");
        var received=sender.prepareSource(source,receiver);
        check(received.transmissionPower>900,"Travel must use directional gain too, preserving clean audio");
        check(receiver.getPower()==460,"Contextual gain must not mutate the receiver's ordinary omnidirectional power");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void angular_shoulder_is_weaker_and_side_has_no_bonus(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH); dish(tower); var receiver=receiver(tower);
        var front=sender(tower,receiver,45,2500); var shoulder=sender(tower,receiver,60,2500); var side=sender(tower,receiver,75,2500);
        check(front.shouldRouteTo(packet(front,1000),receiver),"45-degree edge must retain full gain");
        check(!shoulder.shouldRouteTo(packet(shoulder,1000),receiver),"At 15 degrees outside the edge the same distant sender must lose reach");
        check(!side.shouldRouteTo(packet(side,1000),receiver),"At 30 degrees outside the edge there must be no extra reach");
        double partial=DirectionalReception.reception(460,receiver,shoulder.getLocation(),Frequency.Modulation.FREQUENCY);
        check(Math.abs(partial-(460+(2595-460)*.25))<1,"Shoulder bonus must be one quarter");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void inherited_weak_power_is_not_repaired(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH); dish(tower); var receiver=receiver(tower); var sender=sender(tower,receiver,0,2500);
        var received=sender.prepareSource(packet(sender,100),receiver);
        check(received.transmissionPower>0 && received.transmissionPower<=100,"Reception must not amplify the packet's inherited power");
        check(received.computeSeverity()>SimpleRadioFrequencing.TRANSCEIVER.getDiminishThreshold(Frequency.Modulation.FREQUENCY)/100.0,"Weak inherited audio must remain degraded");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void nearby_unconnected_dish_gives_no_gain(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH);
        DishManager.verify(tower.level(),new Dish(tower.level().dimension().location(),tower.center().east(),Direction.NORTH));
        var receiver=receiver(tower); var sender=sender(tower,receiver,0,2500);
        check(!sender.shouldRouteTo(packet(sender,1000),receiver),"A dish on a neighboring, unconnected mast must not be adopted");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void receiver_bonus_does_not_change_transmitters_or_untracked_devices(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH); dish(tower); var receiver=receiver(tower); var sender=sender(tower,receiver,0,2500);
        check(sender.getPower(Frequency.Modulation.FREQUENCY)==SimpleRadioFrequencing.TRANSCEIVER.transmissionPowerFM,"Dish must never boost handheld transmission");
        var unrelated=new RadioReceiver(receiver.frequency,receiver.getLocation()).frequencingType(SimpleRadioFrequencing.RECEIVER);
        check(DirectionalReception.reception(unrelated.getPower(),unrelated,sender.getLocation(),Frequency.Modulation.FREQUENCY)==unrelated.getPower(),"Untracked receiver must keep normal power");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void missing_chunk_scans_fail_softly_and_packet_reads_are_thread_safe(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH); dish(tower); var receiver=receiver(tower);
        int before=tower.level().getChunkSource().getLoadedChunksCount();
        check(MastScanner.scan(tower.level(),new BlockPos(6000000,80,6000000))==null,"Missing chunk must return retry");
        try {
            float gain=CompletableFuture.supplyAsync(()->DirectionalReception.reception(receiver.getPower(),receiver,
                    new WorldlyPosition(tower.mast().getX()+.5f,80,-6000000,tower.level()),Frequency.Modulation.FREQUENCY)).get(5,TimeUnit.SECONDS);
            check(gain>receiver.getPower(),"Audio-thread lookup must use the published directional profile");
        } catch(Exception e) { throw new GameTestAssertException("Packet lookup failed: "+e); }
        check(tower.level().getChunkSource().getLoadedChunksCount()==before,"Neither missing scan nor packet read may load terrain");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void saved_dish_roundtrip_preserves_orientation_and_rejects_invalid_facing(GameTestHelper helper) {
        var dish=new Dish(helper.getLevel().dimension().location(),new BlockPos(5,80,9),Direction.WEST);
        check(dish.equals(Dish.load(dish.save())),"Saved physical dish must retain position and direction");
        var tag=dish.save(); tag.putString("Facing","up"); check(Dish.load(tag)==null,"Vertical/invalid saved facing must be rejected");
        var input=new CompoundTag(); var list=new ListTag(); list.add(dish.save()); input.put("Dishes",list);
        check(DishSavedData.load(input).save(new CompoundTag()).getList("Dishes",Tag.TAG_COMPOUND).size()==1,"Catalog must survive SavedData roundtrip");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void seven_aims_rotate_native_reception_and_keep_the_mast_binding(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH); dish(tower); var receiver=receiver(tower);
        var east=sender(tower,receiver,90,2500);
        check(!east.shouldRouteTo(packet(east,1000),receiver),"Straight dish must not hear a distant sender to the side");
        var player=helper.makeMockPlayer(); player.setShiftKeyDown(true);
        var hit=new BlockHitResult(tower.center().getCenter(),Direction.NORTH,tower.center(),false);
        for(int i=0;i<3;i++) DirectionalAntenna.DISH.use(tower.level().getBlockState(tower.center()),tower.level(),tower.center(),player,InteractionHand.MAIN_HAND,hit);
        player.setShiftKeyDown(false);
        for(int aim=0;aim<=6;aim++) {
            var state=tower.level().getBlockState(tower.center());
            check(state.getValue(ParabolicReceiverBlock.AIM)==aim,"Every click must select exactly one of the seven aims");
            check(state.getValue(ParabolicReceiverBlock.FACING)==Direction.NORTH,"Aiming must not move the mounting face");
            var saved=DishManager.receivers(receiver).get(0);
            check(saved.aim()==aim && saved.mast().equals(tower.mast()),"Packet snapshot must immediately retain the same mast and the new aim");
            var aligned=sender(tower,receiver,DishAim.degrees(aim),2500);
            check(aligned.shouldRouteTo(packet(aligned,1000),receiver),"Native routing must follow the new bearing");
            if(aim<6) DirectionalAntenna.DISH.use(state,tower.level(),tower.center(),player,InteractionHand.MAIN_HAND,hit);
        }
        check(east.shouldRouteTo(packet(east,1000),receiver),"A 45-degree swivel must bring the distant side sender into full gain");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void panel_clicks_aim_the_center_and_step_limits_do_not_publish_changes(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH); dish(tower); var player=helper.makeMockPlayer();
        BlockPos panel=DishGeometry.cell(tower.center(),tower.facing(),0);
        var hit=new BlockHitResult(panel.getCenter(),Direction.NORTH,panel,false);
        var part=tower.level().getBlockState(panel);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_INGOT));
        check(DirectionalAntenna.PART.use(part,tower.level(),panel,player,InteractionHand.MAIN_HAND,hit)==InteractionResult.PASS,"Held items must not adjust aim");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        for(int i=0;i<3;i++) DirectionalAntenna.PART.use(part,tower.level(),panel,player,InteractionHand.MAIN_HAND,hit);
        check(tower.level().getBlockState(tower.center()).getValue(ParabolicReceiverBlock.AIM)==6,"Outer panel must target the center");
        int revision=DishManager.revision();
        DirectionalAntenna.PART.use(part,tower.level(),panel,player,InteractionHand.MAIN_HAND,hit);
        check(DishManager.revision()==revision,"Clicking past the stop must not invalidate cached routes");
        player.setShiftKeyDown(true);
        for(int i=0;i<3;i++) DirectionalAntenna.PART.use(part,tower.level(),panel,player,InteractionHand.MAIN_HAND,hit);
        check(tower.level().getBlockState(tower.center()).getValue(ParabolicReceiverBlock.AIM)==3,"Sneak-click must turn back toward straight");
        check(DishGeometry.validate(tower.level(),tower.center(),tower.facing())==DishGeometry.Validity.VALID,"Aiming must preserve the physical assembly");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void aimed_saved_data_and_blockstate_roundtrip_and_mirror(GameTestHelper helper) {
        var block=DirectionalAntenna.DISH;
        for(var facing:Direction.Plane.HORIZONTAL) for(int aim=0;aim<=6;aim++) {
            var dish=new Dish(helper.getLevel().dimension().location(),new BlockPos(5,80,9),facing,aim);
            check(dish.equals(Dish.load(dish.save())),"Every mounting face and aim must survive saved-data reload");
            var state=block.defaultBlockState().setValue(ParabolicReceiverBlock.FACING,facing).setValue(ParabolicReceiverBlock.AIM,aim);
            var restored=NbtUtils.readBlockState(helper.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK),NbtUtils.writeBlockState(state));
            check(restored==state,"Aim must survive normal chunk blockstate serialization");
            for(var mirror:new Mirror[]{Mirror.LEFT_RIGHT,Mirror.FRONT_BACK}) {
                var mirrored=block.mirror(state,mirror);
                check(mirrored.getValue(ParabolicReceiverBlock.AIM)==6-aim,"Mirrors must reverse the relative swivel");
                check(block.mirror(mirrored,mirror)==state,"Mirroring twice must restore the entire state");
            }
            check(block.rotate(state,Rotation.CLOCKWISE_90).getValue(ParabolicReceiverBlock.AIM)==aim,"Cardinal rotation must retain relative aim");
        }
        var old=new Dish(helper.getLevel().dimension().location(),BlockPos.ZERO,Direction.NORTH).save();old.remove("Aim");
        check(Dish.load(old).aim()==DishAim.STRAIGHT,"Older dish records must point straight out");
        old.putInt("Aim",7);check(Dish.load(old)==null,"Invalid saved aim must be rejected");
        helper.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void restored_chunk_without_saved_dish_is_reconciled(GameTestHelper helper) {
        var tower=mast(helper,Direction.NORTH);
        DishManager.verify(tower.level(),new Dish(tower.level().dimension().location(),tower.center(),Direction.NORTH));
        var receiver=receiver(tower); var sender=sender(tower,receiver,0,2500);
        check(sender.shouldRouteTo(packet(sender,1000),receiver),"Fixture must begin with saved dish snapshot");
        helper.runAfterDelay(25,()->{
            check(!sender.shouldRouteTo(packet(sender,1000),receiver),"A loaded chunk with no physical dish must lose its saved effect");
            helper.succeed();
        });
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void optional_repeaters_use_directional_gain_and_invalidate_cached_links(GameTestHelper helper) {
        if(!FabricLoader.getInstance().isModLoaded("simpleradio_repeaters")) { helper.succeed(); return; }
        var tower=mast(helper,Direction.NORTH); dish(tower); var receiver=receiver(tower);
        BlockPos relay=tower.core().west();
        tower.level().setBlockAndUpdate(relay,dev.paul.simpleradiorepeaters.SimpleRadioRepeaters.RELAY.defaultBlockState()
                .setValue(dev.paul.simpleradiorepeaters.block.RelayBlock.FACING,Direction.WEST));
        DishManager.refreshRelay(tower.level(),relay,tower.core());
        var channel=new dev.paul.simpleradiorepeaters.network.ChannelKey("089.17","FM");
        var record=new dev.paul.simpleradiorepeaters.network.TowerRecord(UUID.randomUUID(),tower.level().dimension().location().toString(),relay,
                Set.of(channel),true,dev.paul.simpleradiorepeaters.radio.RelayCatalysts.GILDED,true,32,Set.of());
        var from=dev.paul.simpleradiorepeaters.radio.RadioSpace.physical(tower.level(),tower.mast().getCenter().add(0,0,-2300));
        var to=dev.paul.simpleradiorepeaters.radio.RadioSpace.physical(tower.level(),relay.getCenter());
        var leg=dev.paul.simpleradiorepeaters.radio.RadioSpace.between(from,to,false);
        var got=dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter.receiveAtTower(leg,1000,1000,SimpleRadioFrequencing.TRANSCEIVER,record,channel);
        check(got!=null && got.power()>900,"Actual relay adapter must accept and preserve the extended-range handheld signal");
        var toNative=dev.paul.simpleradiorepeaters.radio.RadioSpace.between(from,
                dev.paul.simpleradiorepeaters.radio.RadioSpace.physical(tower.level(),new Vec3(receiver.getLocation().x,receiver.getLocation().y,receiver.getLocation().z)),false);
        check(dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter.receiveAtReceiver(toNative,1000,1000,SimpleRadioFrequencing.TRANSCEIVER,receiver,channel)!=null,"Relay-to-native-receiver delivery must use the dish too");
        int stamp=dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter.configurationStamp();
        var eastFrom=dev.paul.simpleradiorepeaters.radio.RadioSpace.physical(tower.level(),tower.mast().getCenter().add(2300,0,0));
        var eastLeg=dev.paul.simpleradiorepeaters.radio.RadioSpace.between(eastFrom,to,false);
        check(dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter.receiveAtTower(eastLeg,1000,1000,SimpleRadioFrequencing.TRANSCEIVER,record,channel)==null,"Side tower must begin outside the original wedge");
        var manager=dev.paul.simpleradiorepeaters.network.TowerManager.get(tower.level().getServer());
        var cached=manager.network(tower.level(),channel);
        check(cached==manager.network(tower.level(),channel),"Unchanged reception must reuse the existing network cache");
        var player=helper.makeMockPlayer();var hit=new BlockHitResult(tower.center().getCenter(),Direction.NORTH,tower.center(),false);
        for(int i=0;i<3;i++) DirectionalAntenna.DISH.use(tower.level().getBlockState(tower.center()),tower.level(),tower.center(),player,InteractionHand.MAIN_HAND,hit);
        check(stamp!=dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter.configurationStamp(),"Aiming must immediately invalidate cached relay links");
        check(cached!=manager.network(tower.level(),channel),"Actual cached relay routes must rebuild immediately, without waiting for a periodic configuration check");
        check(dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter.receiveAtTower(eastLeg,1000,1000,SimpleRadioFrequencing.TRANSCEIVER,record,channel)!=null,"Relay-to-relay gain must follow the aimed wedge");
        stamp=dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter.configurationStamp();
        tower.level().destroyBlock(tower.center(),false);
        check(stamp!=dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter.configurationStamp(),"Dish changes must invalidate the graph fingerprint");
        check(dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter.receiveAtTower(leg,1000,1000,SimpleRadioFrequencing.TRANSCEIVER,record,channel)==null,"Removing dish must immediately remove its receive bonus");
        helper.succeed();
    }
}
