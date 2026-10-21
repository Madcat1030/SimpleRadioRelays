package dev.paul.simpleradiodirectional.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Last verified, immutable physical attachment. Safe to retain with an unloaded relay. */
public record Dish(ResourceLocation dimension, BlockPos position, Direction facing, int aim) {
    public Dish {
        position = position.immutable();
        if (!DishAim.valid(aim)) throw new IllegalArgumentException("Invalid dish aim: " + aim);
    }
    public Dish(ResourceLocation dimension, BlockPos position, Direction facing) {
        this(dimension, position, facing, DishAim.STRAIGHT);
    }
    public BlockPos mast() { return position.relative(facing.getOpposite()); }
    public double weight(double x, double z) {
        return DirectionalMath.weight(x - (mast().getX() + 0.5), z - (mast().getZ() + 0.5),
                DishAim.x(facing.getStepX(), facing.getStepZ(), aim),
                DishAim.z(facing.getStepX(), facing.getStepZ(), aim));
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putString("Dimension", dimension.toString());
        tag.putLong("Position", position.asLong()); tag.putString("Facing", facing.getName());
        tag.putInt("Aim", aim); return tag;
    }
    public static Dish load(CompoundTag tag) {
        var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        var facing = Direction.byName(tag.getString("Facing"));
        int aim = tag.contains("Aim", Tag.TAG_ANY_NUMERIC) ? tag.getInt("Aim") : DishAim.STRAIGHT;
        return dimension == null || facing == null || facing.getAxis().isVertical() || !DishAim.valid(aim) ? null
                : new Dish(dimension, BlockPos.of(tag.getLong("Position")), facing, aim);
    }
}
