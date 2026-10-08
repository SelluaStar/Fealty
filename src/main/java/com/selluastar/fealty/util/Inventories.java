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

    public static int count(Player player, Item item) {
        return player.getInventory().countItem(item);
    }

    /** Remove {@code amount} of the item if the player has that many. @return whether they were removed */
    public static boolean take(Player player, Item item, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (count(player, item) < amount) {
            return false;
        }
        int left = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        player.getInventory().setChanged();
        return true;
    }
}
