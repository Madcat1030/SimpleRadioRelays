package dev.paul.simpleradiorepeaters.block;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.network.TransponderChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Three physical card slots and a direction switch; antenna, catalyst and attachment remain in the Relay. */
public final class TransponderBlockEntity extends ChannelBankBlockEntity {
    private boolean duplex;
    public TransponderBlockEntity(BlockPos pos, BlockState state) { super(SimpleRadioRepeaters.TRANSPONDER_ENTITY, 3, pos, state); }
    public boolean duplex() { return duplex; }
    public void setDuplex(boolean value) {
        if (duplex != value) { duplex = value; configurationChanged(); }
    }
    @Override public TransponderChannels transponderChannels() { return TransponderChannels.of(cardStates(), duplex); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putBoolean("Duplex", duplex); }
    @Override public void load(CompoundTag tag) { super.load(tag); duplex = tag.getBoolean("Duplex"); }
}
