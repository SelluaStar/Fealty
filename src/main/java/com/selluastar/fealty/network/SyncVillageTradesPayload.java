package com.selluastar.fealty.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.trade.VillageTradeDefinition;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.trading.MerchantOffer;

/** Server to client: the village-exclusive trades, for recipe viewers. */
public record SyncVillageTradesPayload(List<TradeView> trades) implements CustomPacketPayload {
    public static final Type<SyncVillageTradesPayload> TYPE = new Type<>(Fealty.id("sync_village_trades"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncVillageTradesPayload> STREAM_CODEC = StreamCodec.composite(
            TradeView.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncVillageTradesPayload::trades,
            SyncVillageTradesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SyncVillageTradesPayload create() {
        List<TradeView> views = new ArrayList<>();
        for (Map.Entry<ResourceLocation, VillageTradeDefinition> entry : FealtyDataManager.villageTrades().entrySet()) {
            VillageTradeDefinition def = entry.getValue();
            RepTier tier = TierManager.byId(def.minTier()).orElse(TierManager.trusted());
            for (int k = 0; k < def.offers().size(); k++) {
                MerchantOffer offer = def.offers().get(k).create();
                views.add(new TradeView(entry.getKey() + "#" + k, def.professions(), tier.displayName(), tier.color(), def.minLevel(),
                        offer.getBaseCostA().copy(), offer.getCostB().copy(), offer.getResult().copy()));
            }
        }
        return new SyncVillageTradesPayload(views);
    }
}
