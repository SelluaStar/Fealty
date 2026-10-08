package com.selluastar.fealty.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Shows the player's Renown and standing with every faction they have met. */
public class LedgerItem extends Item {
    public LedgerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.selluastar.fealty.network.FealtyNetwork.send(serverPlayer,
                    new com.selluastar.fealty.network.OpenScreenPayload(com.selluastar.fealty.network.OpenScreenPayload.JOURNAL, "reputation"));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.fealty.ledger.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
