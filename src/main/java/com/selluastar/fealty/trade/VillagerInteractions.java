package com.selluastar.fealty.trade;

import java.util.Optional;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.event.ThreatEvent;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.crime.CrimeService;
import com.selluastar.fealty.data.TierData;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.data.TradePolicy;
import com.selluastar.fealty.item.TyrantCrownItem;
import com.selluastar.fealty.outlaw.ThievesGuild;
import com.selluastar.fealty.quest.QuestEvents;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * What happens when a player uses a villager:
 * <ul>
 *     <li>sneak + drawn weapon: threaten (Neutral price once, costs rep, guards may react)</li>
 *     <li>sneak + a wanted item: gift (+rep, once a day per villager)</li>
 *     <li>sneak + empty hand: talk (or pick their pocket from behind)</li>
 *     <li>plain use: trade, unless the villager refuses you</li>
 * </ul>
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class VillagerInteractions {
    private static final java.util.Set<String> DIALOGUE_GROUPS =
            java.util.Set.of("hated", "distrusted", "neutral", "trusted", "honored", "lord", "broken");

    private VillagerInteractions() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Villager villager)
                || event.getHand() != InteractionHand.MAIN_HAND || villager.isBaby() || villager.isSleeping() || player.isSpectator()) {
            return;
        }
        Optional<ResourceLocation> faction = FactionResolver.factionOf(villager);
        if (faction.isEmpty()) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        boolean handled;
        if (player.isSecondaryUseActive()) {
            if (QuestEvents.onUseItemOnVillager(player, villager, held)) {
                handled = true;
            } else if (isThreatWeapon(held)) {
                threaten(player, villager, faction.get());
                handled = true;
            } else if (!held.isEmpty() && held.is(FealtyTags.Items.VILLAGER_GIFTS)) {
                gift(player, villager, faction.get(), held);
                handled = true;
            } else if (held.isEmpty()) {
                if (!ChainManager.onTalk(player, villager) && !ThievesGuild.tryPickpocket(player, villager, faction.get())) {
                    talk(player, villager, faction.get());
                }
                handled = true;
            } else {
                handled = false;
            }
        } else {
            handled = refusesTrade(player, villager, faction.get());
        }
        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    public static boolean isThreatWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(FealtyTags.Items.THREAT_WEAPONS) || stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem || stack.getItem() instanceof MaceItem
                || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    // ---- Trading ----

    private static boolean refusesTrade(ServerPlayer player, Villager villager, ResourceLocation faction) {
        if (villager.getOffers().isEmpty()) {
            return false;
        }
        long now = player.level().getGameTime();
        VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
        if (memory.refusedUntil > now) {
            refuse(player, villager, "fealty.trade.refused_threats");
            return true;
        }
        TierData tier = TierManager.data(RepManager.getTier(player, faction));
        if (tier.tradePolicy() == TradePolicy.REFUSE_UNLESS_THREATENED && !memory.threatPrice) {
            refuse(player, villager, "fealty.trade.refused_hated");
            return true;
        }
        return false;
    }

    private static void refuse(ServerPlayer player, Villager villager, String key) {
        villager.setUnhappyCounter(40);
        villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, villager.getVoicePitch());
        player.displayClientMessage(Component.translatable(key, villager.getDisplayName()), true);
    }

    // ---- Threats ----

    public static void threaten(ServerPlayer player, Villager villager, ResourceLocation faction) {
        long now = player.level().getGameTime();
        VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
        if (memory.refusedUntil > now) {
            refuse(player, villager, "fealty.threat.too_scared");
            return;
        }
        if (now - memory.lastThreat < FealtyConfig.THREAT_COOLDOWN.get()) {
            refuse(player, villager, "fealty.threat.cooldown");
            return;
        }
        if (NeoForge.EVENT_BUS.post(new ThreatEvent(player, villager, faction)).isCanceled()) {
            return;
        }
        memory.lastThreat = now;
        memory.threats++;
        if (memory.threats >= FealtyConfig.THREATS_BEFORE_REFUSAL.get()) {
            memory.threats = 0;
            memory.threatPrice = false;
            memory.refusedUntil = now + FealtyConfig.REFUSAL_TICKS.get();
            player.displayClientMessage(Component.translatable("fealty.threat.refuses", villager.getDisplayName()), false);
        } else {
            memory.threatPrice = true;
            player.displayClientMessage(Component.translatable("fealty.threat.cowers", villager.getDisplayName()), false);
        }
        villager.setUnhappyCounter(60);
        villager.playSound(SoundEvents.VILLAGER_HURT, 1.0F, villager.getVoicePitch());
        if (!TyrantCrownItem.isWearing(player)) {
            CrimeService.commit(player, faction, RepSources.THREATEN, villager.blockPosition(), villager, false);
        }
        FealtyEvents.fire(player, FealtyEvents.THREATENED);
    }

    // ---- Gifts ----

    private static void gift(ServerPlayer player, Villager villager, ResourceLocation faction, ItemStack held) {
        long now = player.level().getGameTime();
        VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
        if (now - memory.lastGift < FealtyConfig.GIFT_COOLDOWN.get()) {
            player.displayClientMessage(Component.translatable("fealty.gift.cooldown", villager.getDisplayName()), true);
            return;
        }
        memory.lastGift = now;
        held.consume(1, player);
        RepManager.meet(player, faction);
        int before = RepManager.getRep(player, faction);
        int change = RepManager.applySource(player, faction, RepSources.GIFT);
        if (change == 0 && before < 0) {
            player.displayClientMessage(Component.translatable("fealty.gift.not_enough", villager.getDisplayName()), true);
        } else {
            player.displayClientMessage(Component.translatable("fealty.gift.thanks", villager.getDisplayName()), true);
        }
        villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, villager.getVoicePitch());
        ((ServerLevel) villager.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getEyeY() + 0.3,
                villager.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
        FealtyEvents.fire(player, FealtyEvents.GIFT_GIVEN);
    }

    // ---- Talk ----

    private static void talk(ServerPlayer player, Villager villager, ResourceLocation faction) {
        RepManager.meet(player, faction);
        RepTier tier = RepManager.getTier(player, faction);
        String group = tier.id().getPath();
        if (Factions.isVillage(faction)) {
            Optional<VillageRecord> village = FealtyWorldData.get(player.server).village(faction);
            if (village.isPresent() && village.get().lord().isLord(player.getUUID())) {
                group = "lord";
            } else if (village.isPresent() && village.get().isBroken()) {
                group = "broken";
            }
        }
        if (!DIALOGUE_GROUPS.contains(group)) {
            // Tiers added by data packs borrow the lines of the nearest default tier.
            int neutral = TierManager.neutral().rank();
            group = tier.rank() < neutral ? "distrusted" : tier.rank() > neutral ? "trusted" : "neutral";
        }
        int line = villager.getRandom().nextInt(4);
        Component text = Component.translatable("fealty.dialogue." + group + "." + line, player.getDisplayName());
        player.sendSystemMessage(Component.translatable("fealty.dialogue.format", villager.getDisplayName(), text));
        villager.getLookControl().setLookAt(player);
        villager.playSound(tier.rank() >= TierManager.neutral().rank() ? SoundEvents.VILLAGER_AMBIENT : SoundEvents.VILLAGER_NO,
                1.0F, villager.getVoicePitch());
    }
}
