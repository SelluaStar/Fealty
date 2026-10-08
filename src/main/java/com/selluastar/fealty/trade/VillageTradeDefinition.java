package com.selluastar.fealty.trade;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Trades a village only offers to trusted friends, from {@code data/<ns>/fealty/village_trades/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "professions": ["minecraft:farmer"],
 *   "min_tier": "fealty:trusted",
 *   "min_level": 1,
 *   "offers": [
 *     { "buy": {"id": "minecraft:emerald", "count": 3}, "sell": {"id": "minecraft:golden_carrot", "count": 8},
 *       "max_uses": 8, "xp": 4 }
 *   ]
 * }
 * }</pre>
 * An empty {@code professions} list applies to every profession.
 */
public record VillageTradeDefinition(List<ResourceLocation> professions, ResourceLocation minTier, int minLevel, List<Offer> offers) {
    public static final Codec<VillageTradeDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("professions", List.of()).forGetter(VillageTradeDefinition::professions),
            ResourceLocation.CODEC.fieldOf("min_tier").forGetter(VillageTradeDefinition::minTier),
            Codec.intRange(1, 5).optionalFieldOf("min_level", 1).forGetter(VillageTradeDefinition::minLevel),
            Offer.CODEC.listOf().fieldOf("offers").forGetter(VillageTradeDefinition::offers)
    ).apply(i, VillageTradeDefinition::new));

    public record Offer(ItemCost buy, Optional<ItemCost> buyB, ItemStack sell, int maxUses, int xp, float priceMultiplier) {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemCost.CODEC.fieldOf("buy").forGetter(Offer::buy),
                ItemCost.CODEC.optionalFieldOf("buy_b").forGetter(Offer::buyB),
                ItemStack.CODEC.fieldOf("sell").forGetter(Offer::sell),
                Codec.intRange(1, 1000).optionalFieldOf("max_uses", 8).forGetter(Offer::maxUses),
                Codec.intRange(0, 1000).optionalFieldOf("xp", 2).forGetter(Offer::xp),
                Codec.FLOAT.optionalFieldOf("price_multiplier", 0.05F).forGetter(Offer::priceMultiplier)
        ).apply(i, Offer::new));

        public MerchantOffer create() {
            return new MerchantOffer(buy, buyB, sell.copy(), maxUses, xp, priceMultiplier);
        }
    }
}
