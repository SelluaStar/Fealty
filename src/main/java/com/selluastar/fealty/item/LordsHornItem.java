package com.selluastar.fealty.item;

import java.util.List;

import com.selluastar.fealty.guard.GarrisonManager;
import com.selluastar.fealty.guard.HornOrder;
import com.selluastar.fealty.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A lord's horn. Use it anywhere to give the guards of your village the selected order: call them to you (those
 * far away arrive shortly), hold, guard the area, or go home. Sneak-use to pick the village and the order.
 */
public class LordsHornItem extends Item {
    public LordsHornItem(Properties properties) {
        super(properties);
    }

    public static HornOrder order(ItemStack stack) {
        return HornOrder.byIndex(stack.getOrDefault(ModDataComponents.HORN_ORDER.get(), 0));
    }

    /** The horn in the player's hands, if they hold one. */
    public static ItemStack held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof LordsHornItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive()) {
                GarrisonManager.openHorn(serverPlayer, stack);
            } else {
                GarrisonManager.blow(serverPlayer, stack, order(stack));
                player.getCooldowns().addCooldown(this, 60);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("fealty.horn.current", Component.translatable("fealty.horn.order." + order(stack).id()))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.fealty.lords_horn.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
