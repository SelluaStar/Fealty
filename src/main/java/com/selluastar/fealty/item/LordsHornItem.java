package com.selluastar.fealty.item;

import java.util.List;

import com.selluastar.fealty.guard.GuardOrders;
import com.selluastar.fealty.lordship.LordshipManager;
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
 * A lord's horn. Use it in a village you rule to give its guards the selected order; sneak-use to change the
 * order (follow me, hold position, defend the village).
 */
public class LordsHornItem extends Item {
    public static final GuardOrders.Mode[] ORDERS = {GuardOrders.Mode.FOLLOW, GuardOrders.Mode.HOLD, GuardOrders.Mode.DEFEND};

    public LordsHornItem(Properties properties) {
        super(properties);
    }

    public static GuardOrders.Mode order(ItemStack stack) {
        int index = stack.getOrDefault(ModDataComponents.HORN_ORDER.get(), 0);
        return ORDERS[Math.floorMod(index, ORDERS.length)];
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            int next = Math.floorMod(stack.getOrDefault(ModDataComponents.HORN_ORDER.get(), 0) + 1, ORDERS.length);
            stack.set(ModDataComponents.HORN_ORDER.get(), next);
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("fealty.horn.selected",
                        Component.translatable("fealty.horn.order." + ORDERS[next].getSerializedName())), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            LordshipManager.blowHorn(serverPlayer, order(stack));
        }
        player.getCooldowns().addCooldown(this, 60);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("fealty.horn.current",
                Component.translatable("fealty.horn.order." + order(stack).getSerializedName())).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.fealty.lords_horn.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
