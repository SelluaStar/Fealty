package com.selluastar.fealty.item;

import java.util.List;

import com.selluastar.fealty.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Carried by courier quests to another village's elder. Deliver it by using it on that elder. */
public class SealedLetterItem extends Item {
    public SealedLetterItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LetterInfo info = stack.get(ModDataComponents.LETTER.get());
        if (info == null) {
            tooltip.add(Component.translatable("item.fealty.sealed_letter.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.fealty.sealed_letter.from", info.fromName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.fealty.sealed_letter.to", info.toName()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.fealty.sealed_letter.where", info.toPos().getX(), info.toPos().getZ())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
