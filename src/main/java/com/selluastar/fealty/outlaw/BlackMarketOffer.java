package com.selluastar.fealty.outlaw;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Something the black marketeer may sell, from {@code data/<ns>/fealty/black_market/<id>.json}. Each marketeer
 * stocks a handful of these, picked by weight.
 *
 * <pre>{@code
 * { "buy": {"id": "minecraft:emerald", "count": 6},
 *   "sell": {"id": "fealty:smoke_bomb", "count": 3}, "max_uses": 4, "weight": 10 }
 * }</pre>
 */
public record BlackMarketOffer(ItemCost buy, Optional<ItemCost> buyB, ItemStack sell, int maxUses, int weight) {
    public static final Codec<BlackMarketOffer> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemCost.CODEC.fieldOf("buy").forGetter(BlackMarketOffer::buy),
            ItemCost.CODEC.optionalFieldOf("buy_b").forGetter(BlackMarketOffer::buyB),
            ItemStack.CODEC.fieldOf("sell").forGetter(BlackMarketOffer::sell),
            Codec.intRange(1, 100).optionalFieldOf("max_uses", 4).forGetter(BlackMarketOffer::maxUses),
            Codec.intRange(1, 1000).optionalFieldOf("weight", 10).forGetter(BlackMarketOffer::weight)
    ).apply(i, BlackMarketOffer::new));

    public MerchantOffer create() {
        return new MerchantOffer(buy, buyB, sell.copy(), maxUses, 1, 0.0F);
    }
}
