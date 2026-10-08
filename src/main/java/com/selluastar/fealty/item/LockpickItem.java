package com.selluastar.fealty.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Hold it while opening a village coffer to open it quietly. Without one the lock is forced, and everyone
 * nearby hears it, line of sight or not.
 */
public class LockpickItem extends Item {
    public LockpickItem(Properties properties) {
        super(properties);
    }

    /** Find a lockpick in either hand. */
    public static ItemStack held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof LockpickItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.fealty.lockpick.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
