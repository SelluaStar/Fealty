package com.selluastar.fealty.trade;

import java.util.Optional;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.event.ThreatEvent;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.crime.CrimeService;
import com.selluastar.fealty.data.TierData;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.data.TradePolicy;
import com.selluastar.fealty.dialogue.DialogueLines;
import com.selluastar.fealty.dialogue.DialogueService;
import com.selluastar.fealty.dialogue.SpeakerContext;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.item.TyrantCrownItem;
import com.selluastar.fealty.outlaw.ThievesGuild;
import com.selluastar.fealty.quest.QuestEvents;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * What happens when a player uses a villager:
 * <ul>
 *     <li>plain use: the dialogue box (trade, work, news, gifts, threats), unless {@code villager_dialogue} is off</li>
 *     <li>sneak + drawn weapon: threaten (Neutral price once, costs rep, guards may react)</li>
 *     <li>sneak + a wanted item: gift (+rep, once a day per villager)</li>
 *     <li>sneak + empty hand from behind: pick their pocket</li>
 *     <li>sneak otherwise: trade straight away, unless the villager refuses you</li>
 * </ul>
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class VillagerInteractions {
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
            } else if (isGift(held)) {
                gift(player, villager, faction.get(), held);
                handled = true;
            } else if (held.isEmpty() && ThievesGuild.tryPickpocket(player, villager, faction.get())) {
                handled = true;
            } else {
                handled = refusesTrade(player, villager, faction.get()).isPresent();
            }
        } else if (FealtyConfig.VILLAGER_DIALOGUE.get()) {
            handled = DialogueService.open(player, villager);
        } else {
            handled = refusesTrade(player, villager, faction.get()).isPresent();
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

    public static boolean isGift(ItemStack stack) {
        return !stack.isEmpty() && stack.is(FealtyTags.Items.VILLAGER_GIFTS);
    }

    // ---- Trading ----

    /**
     * Whether the villager refuses to trade with the player. A refusal is said out loud and returned as the
     * villager's reply.
     */
    public static Optional<Component> refusesTrade(ServerPlayer player, Villager villager, ResourceLocation faction) {
        if (villager.getOffers().isEmpty()) {
            return Optional.empty();
        }
        long now = player.level().getGameTime();
        VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
        if (memory.refusedUntil > now) {
            return Optional.of(refuse(player, villager, "refuse_threats", "fealty.trade.refused_threats"));
        }
        TierData tier = TierManager.data(RepManager.getTier(player, faction));
        if (tier.tradePolicy() == TradePolicy.REFUSE_UNLESS_THREATENED && !memory.threatPrice) {
            return Optional.of(refuse(player, villager, "refuse_hated", "fealty.trade.refused_hated"));
        }
        return Optional.empty();
    }

    private static Component refuse(ServerPlayer player, Villager villager, String context, String fallbackKey) {
        villager.setUnhappyCounter(40);
        villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, villager.getVoicePitch());
        return speak(player, villager, context, Component.translatable(fallbackKey, villager.getDisplayName()));
    }

    /** Say a line from a context out loud (or the fallback text) and return it. */
    public static Component speak(ServerPlayer player, Villager villager, String context, Component fallback) {
        villager.getLookControl().setLookAt(player);
        Component line = DialogueLines.pick(context, SpeakerContext.of(villager, player), villager.getRandom(), player.getDisplayName())
                .orElse(fallback);
        Speech.say(villager, line);
        return line;
    }

    // ---- Threats ----

    /** Threaten a villager. @return what the villager says */
    public static Component threaten(ServerPlayer player, Villager villager, ResourceLocation faction) {
        long now = player.level().getGameTime();
        VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
        if (memory.refusedUntil > now) {
            return refuse(player, villager, "threat_refuse", "fealty.threat.too_scared");
        }
        if (now - memory.lastThreat < FealtyConfig.THREAT_COOLDOWN.get()) {
            return refuse(player, villager, "threat_refuse", "fealty.threat.cooldown");
        }
        if (NeoForge.EVENT_BUS.post(new ThreatEvent(player, villager, faction)).isCanceled()) {
            return Component.empty();
        }
        memory.lastThreat = now;
        memory.threats++;
        Component reply;
        if (memory.threats >= FealtyConfig.THREATS_BEFORE_REFUSAL.get()) {
            memory.threats = 0;
            memory.threatPrice = false;
            memory.refusedUntil = now + FealtyConfig.REFUSAL_TICKS.get();
            reply = speak(player, villager, "threat_refuse", Component.translatable("fealty.threat.refuses", villager.getDisplayName()));
        } else {
            memory.threatPrice = true;
            reply = speak(player, villager, "threat_cower", Component.translatable("fealty.threat.cowers", villager.getDisplayName()));
        }
        villager.setUnhappyCounter(60);
        villager.playSound(SoundEvents.VILLAGER_HURT, 1.0F, villager.getVoicePitch());
        if (!TyrantCrownItem.isWearing(player)) {
            CrimeService.commit(player, faction, RepSources.THREATEN, villager.blockPosition(), villager, false);
        }
        FealtyEvents.fire(player, FealtyEvents.THREATENED);
        return reply;
    }

    // ---- Gifts ----

    /** Give the held item to a villager. @return what the villager says */
    public static Component gift(ServerPlayer player, Villager villager, ResourceLocation faction, ItemStack held) {
        long now = player.level().getGameTime();
        VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
        if (now - memory.lastGift < FealtyConfig.GIFT_COOLDOWN.get()) {
            return speak(player, villager, "gift_cooldown", Component.translatable("fealty.gift.cooldown", villager.getDisplayName()));
        }
        memory.lastGift = now;
        held.consume(1, player);
        RepManager.meet(player, faction);
        int before = RepManager.getRep(player, faction);
        int change = RepManager.applySource(player, faction, RepSources.GIFT);
        Component reply;
        if (change == 0 && before < 0) {
            reply = speak(player, villager, "gift_refused", Component.translatable("fealty.gift.not_enough", villager.getDisplayName()));
        } else {
            reply = speak(player, villager, "gift_thanks", Component.translatable("fealty.gift.thanks", villager.getDisplayName()));
        }
        RepManager.data(player).addStat("gifts_given", 1);
        villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, villager.getVoicePitch());
        ((ServerLevel) villager.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getEyeY() + 0.3,
                villager.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
        FealtyEvents.fire(player, FealtyEvents.GIFT_GIVEN);
        return reply;
    }
}
