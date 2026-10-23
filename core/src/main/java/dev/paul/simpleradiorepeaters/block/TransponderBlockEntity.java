package dev.paul.simpleradiorepeaters.block;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.network.TransponderChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Three physical card slots and a direction switch; antenna, catalyst and attachment remain in the Relay. */
public final class TransponderBlockEntity extends ChannelBankBlockEntity {
    private TransponderChannels.Mode mode = TransponderChannels.Mode.SIMPLEX;
    public TransponderBlockEntity(BlockPos pos, BlockState state) { super(SimpleRadioRepeaters.TRANSPONDER_ENTITY, 3, pos, state); }
    public TransponderChannels.Mode mode() { return mode; }
    public void setMode(TransponderChannels.Mode value) {
        if (mode != value) { mode = java.util.Objects.requireNonNull(value); configurationChanged(); }
    }
    @Override public TransponderChannels transponderChannels() { return TransponderChannels.of(cardStates(), mode); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putString("Mode", mode.name()); }
    @Override public void load(CompoundTag tag) { super.load(tag); mode = TransponderChannels.Mode.load(tag); }
}
