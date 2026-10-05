package com.selluastar.fealty.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.outlaw.HeatManager;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.trade.VillagerInteractions;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;

/** What an ordinary villager (or a named chain villager) says and lets the player do. */
final class VillagerDialogue {
    static final String TRADE = "trade";
    static final String WORK = "work";
    static final String NEWS = "news";
    static final String GIFT = "gift";
    static final String THREATEN = "threaten";

    private VillagerDialogue() {
    }

    static DialogueNode node(ServerPlayer player, Villager villager, @Nullable Component reply) {
        ResourceLocation faction = FactionResolver.factionOf(villager).orElse(Factions.WANDERERS);
        RepManager.meet(player, faction);
        int rep = RepManager.getRep(player, faction);
        RepTier tier = RepManager.tierOf(rep);
        Component subtitle = Component.translatable("fealty.dialogue.subtitle", Factions.displayName(player.server, faction),
                tier.displayName().copy().withColor(tier.color()), rep);
        Component text = reply != null ? reply
                : DialogueLines.pick("greet", SpeakerContext.of(villager, player), villager.getRandom(), player.getDisplayName())
                .orElse(Component.translatable("fealty.dialogue.fallback"));

        List<DialogueNode.Option> options = new ArrayList<>();
        boolean canTrade = !villager.getOffers().isEmpty() && villager.getVillagerData().getProfession() != VillagerProfession.NITWIT;
        options.add(canTrade ? DialogueNode.Option.of(TRADE, Component.translatable("fealty.dialogue.option.trade"), "trade")
                : DialogueNode.Option.disabled(TRADE, Component.translatable("fealty.dialogue.option.trade"), "trade",
                Component.translatable("fealty.dialogue.no_trades")));
        options.add(DialogueNode.Option.of(WORK, Component.translatable(ChainManager.hasRole(villager)
                ? "fealty.dialogue.option.task" : "fealty.dialogue.option.work"), "quest"));
        options.add(DialogueNode.Option.of(NEWS, Component.translatable("fealty.dialogue.option.news"), "talk"));
        ItemStack held = player.getMainHandItem();
        if (VillagerInteractions.isGift(held)) {
            options.add(DialogueNode.Option.of(GIFT, Component.translatable("fealty.dialogue.option.gift", held.getHoverName()), "gift"));
        }
        if (VillagerInteractions.isThreatWeapon(held)) {
            options.add(DialogueNode.Option.of(THREATEN, Component.translatable("fealty.dialogue.option.threaten"), "sword"));
        }
        options.add(DialogueNode.Option.of(DialogueService.BYE, Component.translatable("fealty.dialogue.option.bye"), "door"));
        villager.getLookControl().setLookAt(player);
        return new DialogueNode(villager.getDisplayName(), subtitle, text, options);
    }

    static void handle(ServerPlayer player, Villager villager, String option) {
        ResourceLocation faction = FactionResolver.factionOf(villager).orElse(Factions.WANDERERS);
        switch (option) {
            case TRADE -> {
                Optional<Component> refusal = VillagerInteractions.refusesTrade(player, villager, faction);
                if (refusal.isPresent()) {
                    DialogueService.reply(player, villager, refusal.get());
                } else {
                    DialogueService.end(player);
                    villager.interact(player, InteractionHand.MAIN_HAND);
                }
            }
            case WORK -> {
                if (ChainManager.onTalk(player, villager)) {
                    DialogueService.end(player);
                } else {
                    DialogueService.reply(player, villager, VillagerInteractions.speak(player, villager, "work_none",
                            Component.translatable("fealty.dialogue.work_none")));
                }
            }
            case NEWS -> {
                Component news = news(player, villager, faction);
                Speech.say(villager, news);
                DialogueService.reply(player, villager, news);
            }
            case GIFT -> {
                ItemStack held = player.getMainHandItem();
                if (VillagerInteractions.isGift(held)) {
                    DialogueService.reply(player, villager, VillagerInteractions.gift(player, villager, faction, held));
                } else {
                    DialogueService.refresh(player, villager);
                }
            }
            case THREATEN -> {
                if (VillagerInteractions.isThreatWeapon(player.getMainHandItem())) {
                    DialogueService.reply(player, villager, VillagerInteractions.threaten(player, villager, faction));
                } else {
                    DialogueService.refresh(player, villager);
                }
            }
            default -> DialogueService.refresh(player, villager);
        }
    }

    /** Village gossip: who rules, how the elder is, what the village thinks of the player, and one rumour. */
    private static Component news(ServerPlayer player, Villager villager, ResourceLocation faction) {
        SpeakerContext context = SpeakerContext.of(villager, player);
        Component flavour = DialogueLines.pick("news", context, villager.getRandom(), player.getDisplayName()).orElse(Component.empty());
        Optional<VillageRecord> village = Factions.isVillage(faction) ? FealtyWorldData.get(player.server).village(faction) : Optional.empty();
        Component fact;
        if (village.isEmpty()) {
            fact = Component.translatable("fealty.news.wanderer");
        } else if (HeatManager.wantedLevel(player, faction) > 0) {
            fact = Component.translatable("fealty.news.wanted", player.getDisplayName());
        } else if (village.get().isBroken()) {
            fact = Component.translatable("fealty.news.broken", village.get().elder().name());
        } else if (village.get().lord().uuid() != null) {
            fact = village.get().lord().isLord(player.getUUID())
                    ? Component.translatable("fealty.news.your_lordship")
                    : Component.translatable("fealty.news.lord", village.get().lord().name());
        } else if (village.get().hasElder() && !village.get().elder().name().isEmpty()) {
            fact = Component.translatable("fealty.news.elder", village.get().elder().name());
        } else {
            fact = Component.translatable("fealty.news.quiet");
        }
        return flavour.getString().isEmpty() ? fact : Component.empty().append(fact).append(" ").append(flavour);
    }
}
