package com.selluastar.fealty.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.GuardStance;
import com.selluastar.fealty.api.RepTier;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

/**
 * One tier from {@code data/<ns>/fealty/tiers/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "name": {"translate": "fealty.tier.trusted"},
 *   "min": 21, "max": 60,
 *   "price_multiplier": 0.85,
 *   "color": "#4CAF50",
 *   "guard_stance": "assist",
 *   "trade_policy": "standard",
 *   "locked_trade_level": 0,
 *   "villagers_hide": false,
 *   "villager_gifts": false
 * }
 * }</pre>
 */
public record TierData(Component name, int min, int max, float priceMultiplier, int color, GuardStance guardStance,
                       TradePolicy tradePolicy, int lockedTradeLevel, boolean villagersHide, boolean villagerGifts) {

    public static final Codec<TierData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(TierData::name),
            Codec.INT.fieldOf("min").forGetter(TierData::min),
            Codec.INT.fieldOf("max").forGetter(TierData::max),
            Codec.FLOAT.optionalFieldOf("price_multiplier", 1.0F).forGetter(TierData::priceMultiplier),
            TextColor.CODEC.xmap(TextColor::getValue, TextColor::fromRgb).optionalFieldOf("color", 0xFFFFFF).forGetter(TierData::color),
            GuardStance.CODEC.optionalFieldOf("guard_stance", GuardStance.IGNORE).forGetter(TierData::guardStance),
            TradePolicy.CODEC.optionalFieldOf("trade_policy", TradePolicy.STANDARD).forGetter(TierData::tradePolicy),
            Codec.intRange(0, 5).optionalFieldOf("locked_trade_level", 0).forGetter(TierData::lockedTradeLevel),
            Codec.BOOL.optionalFieldOf("villagers_hide", false).forGetter(TierData::villagersHide),
            Codec.BOOL.optionalFieldOf("villager_gifts", false).forGetter(TierData::villagerGifts)
    ).apply(i, TierData::new));

    public RepTier toTier(ResourceLocation id, int rank) {
        return new RepTier(id, name, min, max, priceMultiplier, rank, color, guardStance);
    }
}
