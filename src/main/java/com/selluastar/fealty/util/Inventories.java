package com.selluastar.fealty.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Small inventory helpers. */
public final class Inventories {
    private Inventories() {
    }

    public static ItemStack find(Player player, Item item) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean has(Player player, Item item) {
        return !find(player, item).isEmpty();
    }

    /** Remove one of the item. @return whether one was removed */
    public static boolean takeOne(Player player, Item item) {
        ItemStack stack = find(player, item);
        if (stack.isEmpty()) {
            return false;
        }
        stack.shrink(1);
        player.getInventory().setChanged();
        return true;
    }
}
