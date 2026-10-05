package com.selluastar.fealty.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.selluastar.fealty.trade.TradeHooks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Hooks Fealty's tier pricing into villagers and turns off vanilla gossip for prices and golems, so the two
 * reputation systems never stack.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {

    @Inject(method = "updateSpecialPrices", at = @At("HEAD"), cancellable = true)
    private void fealty$updateSpecialPrices(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && TradeHooks.updatePrices((Villager) (Object) this, serverPlayer)) {
            ci.cancel();
        }
    }

    @Inject(method = "resetSpecialPrices", at = @At("TAIL"))
    private void fealty$resetSpecialPrices(CallbackInfo ci) {
        TradeHooks.restore((Villager) (Object) this);
    }

    @Inject(method = "getPlayerReputation", at = @At("HEAD"), cancellable = true)
    private void fealty$getPlayerReputation(Player player, CallbackInfoReturnable<Integer> cir) {
        if (TradeHooks.gossipDisabled()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "onReputationEventFrom", at = @At("HEAD"))
    private void fealty$onReputationEventFrom(ReputationEventType type, Entity target, CallbackInfo ci) {
        TradeHooks.onReputationEvent((Villager) (Object) this, type, target);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void fealty$addAdditionalSaveData(CompoundTag tag, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        MerchantOffers clean = TradeHooks.offersForSaving(self);
        if (clean != null) {
            MerchantOffers.CODEC.encodeStart(self.registryAccess().createSerializationContext(NbtOps.INSTANCE), clean)
                    .ifSuccess(encoded -> tag.put("Offers", encoded));
        }
    }
}
