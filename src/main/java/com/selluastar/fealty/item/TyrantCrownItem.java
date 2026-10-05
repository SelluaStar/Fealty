package com.selluastar.fealty.item;

import java.util.List;

import com.selluastar.fealty.registry.ModArmorMaterials;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Dropped by the Tyrant Lord. While worn, threatening villagers costs no reputation. */
public class TyrantCrownItem extends ArmorItem {
    public TyrantCrownItem(Properties properties) {
        super(ModArmorMaterials.TYRANT, Type.HELMET, properties);
    }

    public static boolean isWearing(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof TyrantCrownItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.fealty.tyrant_crown.desc").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }
}
