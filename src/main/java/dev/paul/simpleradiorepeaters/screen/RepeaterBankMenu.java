package dev.paul.simpleradiorepeaters.screen;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.RepeaterBankBlockEntity;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

public final class RepeaterBankMenu extends AbstractContainerMenu {
    private final Container bank;
    private final ContainerData data;
    public RepeaterBankMenu(int sync, Inventory inventory) { this(sync, inventory, new SimpleContainer(10), new SimpleContainerData(5)); }
    public RepeaterBankMenu(int sync, Inventory inventory, Container bank, ContainerData data) {
        super(SimpleRadioRepeaters.BANK_MENU, sync); this.bank = bank; this.data = data;
        checkContainerSize(bank, 10); checkContainerDataCount(data, 5); bank.startOpen(inventory.player);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) addSlot(new Slot(bank, row * 3 + col, 26 + col * 18, 34 + row * 18) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(SimpleRadioRepeaters.CARD); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(bank, 9, 130, 52) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(Items.GILDED_BLACKSTONE); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 144 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 202));
        addDataSlots(data);
    }
    public int score() { return data.get(0); }
    public boolean enabled() { return data.get(1) != 0; }
    public int status() { return data.get(2); }
    public int channels() { return data.get(3); }
    public int peers() { return data.get(4); }
    @Override public boolean stillValid(Player player) { return bank.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); bank.stopOpen(player); }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && stillValid(player) && bank instanceof RepeaterBankBlockEntity blockEntity) { blockEntity.setEnabled(!blockEntity.enabled()); return true; }
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem(), result = original.copy();
        if (index < 10) { if (!moveItemStackTo(original, 10, 46, true)) return ItemStack.EMPTY; }
        else if (original.is(SimpleRadioRepeaters.CARD)) { if (!moveItemStackTo(original, 0, 9, false)) return ItemStack.EMPTY; }
        else if (original.is(Items.GILDED_BLACKSTONE)) { if (!moveItemStackTo(original, 9, 10, false)) return ItemStack.EMPTY; }
        else return ItemStack.EMPTY;
        if (original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, original); return result;
    }
}
