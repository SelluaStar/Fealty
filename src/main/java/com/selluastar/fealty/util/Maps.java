package com.selluastar.fealty.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Explorer maps and item hand-outs. */
public final class Maps {
    private Maps() {
    }

    /** A filled explorer map centred on the target, marked like a treasure map. */
    public static ItemStack treasureMap(ServerLevel level, BlockPos target, Holder<MapDecorationType> decoration, Component name) {
        ItemStack map = MapItem.create(level, target.getX(), target.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, target, "+", decoration);
        map.set(DataComponents.ITEM_NAME, name);
        return map;
    }

    /** Put an item in the player's inventory, dropping it at their feet if full. */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
