package dev.paul.simpleradiodirectional.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Last verified, immutable physical attachment. Safe to retain with an unloaded relay. */
public record Dish(ResourceLocation dimension, BlockPos position, Direction facing) {
    public Dish { position = position.immutable(); }
    public BlockPos mast() { return position.relative(facing.getOpposite()); }
    public double weight(double x, double z) {
        return DirectionalMath.weight(x - (mast().getX() + 0.5), z - (mast().getZ() + 0.5),
                facing.getStepX(), facing.getStepZ());
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putString("Dimension", dimension.toString());
        tag.putLong("Position", position.asLong()); tag.putString("Facing", facing.getName()); return tag;
    }
    public static Dish load(CompoundTag tag) {
        var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        var facing = Direction.byName(tag.getString("Facing"));
        return dimension == null || facing == null || facing.getAxis().isVertical() ? null
                : new Dish(dimension, BlockPos.of(tag.getLong("Position")), facing);
    }
}
