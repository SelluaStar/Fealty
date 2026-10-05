package com.selluastar.fealty.compat.jei;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.network.TradeView;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One village-exclusive trade: what it costs, what it gives, and the standing it needs. */
public class VillageTradeCategory implements IRecipeCategory<TradeView> {
    public static final RecipeType<TradeView> TYPE = RecipeType.create(Fealty.MOD_ID, "village_trade", TradeView.class);
    private static final int WIDTH = 140;
    private static final int HEIGHT = 44;
    private final IDrawable icon;
    private final IDrawable arrow;

    public VillageTradeCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(new ItemStack(Items.EMERALD));
        this.arrow = helper.getRecipeArrow();
    }

    @Override
    public RecipeType<TradeView> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("fealty.jei.village_trades");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TradeView trade, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 4, 4).addItemStack(trade.costA());
        if (!trade.costB().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 24, 4).addItemStack(trade.costB());
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 84, 4).addItemStack(trade.result());
    }

    @Override
    public void draw(TradeView trade, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 52, 4);
        Component profession = trade.professions().isEmpty() ? Component.translatable("fealty.jei.any_villager")
                : Component.translatable("entity.minecraft.villager." + professionPath(trade.professions().getFirst()));
        Component line = Component.translatable("fealty.jei.requires", trade.tier().copy().withColor(trade.tierColor()), profession, trade.minLevel());
        graphics.drawString(Minecraft.getInstance().font, line, 4, 28, 0x404040, false);
    }

    private static String professionPath(ResourceLocation id) {
        return BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id) ? id.getPath() : "none";
    }
}
