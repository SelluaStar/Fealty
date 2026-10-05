package com.selluastar.fealty.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** "Bring N of these": an ingredient (item or tag) and a count. */
public record ItemRequirement(Ingredient ingredient, int count) {
    public static final Codec<ItemRequirement> CODEC = RecordCodecBuilder.create(i -> i.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(ItemRequirement::ingredient),
            Codec.intRange(1, 100000).optionalFieldOf("count", 1).forGetter(ItemRequirement::count)
    ).apply(i, ItemRequirement::new));

    public int countIn(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Remove the required amount. Call only after checking {@link #countIn}. */
    public void take(Player player) {
        int remaining = count;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (ingredient.test(stack)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (remaining <= 0) {
                break;
            }
            if (ingredient.test(stack)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        player.getInventory().setChanged();
    }

    public Component displayName() {
        ItemStack[] items = ingredient.getItems();
        return items.length > 0 ? items[0].getHoverName() : Component.literal("?");
    }
}
