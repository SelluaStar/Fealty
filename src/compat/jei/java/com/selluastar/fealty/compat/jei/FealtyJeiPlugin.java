package com.selluastar.fealty.compat.jei;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.client.ClientRepCache;
import com.selluastar.fealty.network.TradeView;
import com.selluastar.fealty.registry.ModItems;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** JEI: a "Village trades" category listing the trades villages keep for trusted friends. */
@JeiPlugin
public class FealtyJeiPlugin implements IModPlugin {
    @Nullable
    private static IJeiRuntime runtime;
    private static final Set<String> SHOWN = new HashSet<>();

    public FealtyJeiPlugin() {
        ClientRepCache.onVillageTrades(FealtyJeiPlugin::refresh);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return Fealty.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new VillageTradeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        SHOWN.clear();
        List<TradeView> trades = ClientRepCache.villageTrades();
        trades.forEach(t -> SHOWN.add(t.key()));
        registration.addRecipes(VillageTradeCategory.TYPE, trades);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.LEDGER.get()), VillageTradeCategory.TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private static void refresh() {
        if (runtime == null) {
            return;
        }
        List<TradeView> added = new ArrayList<>();
        for (TradeView trade : ClientRepCache.villageTrades()) {
            if (SHOWN.add(trade.key())) {
                added.add(trade);
            }
        }
        if (!added.isEmpty()) {
            runtime.getRecipeManager().addRecipes(VillageTradeCategory.TYPE, added);
        }
    }
}
