package com.selluastar.fealty.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.outlaw.HeatManager;
import com.selluastar.fealty.quest.FavorManager;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.quest.RepQuestDefinition;
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

/** What an ordinary villager (or a named chain villager) says and lets the player do, favors included. */
final class VillagerDialogue {
    static final String TRADE = "trade";
    static final String WORK = "work";
    static final String NEWS = "news";
    static final String GIFT = "gift";
    static final String THREATEN = "threaten";
    static final String BACK = "back";
    static final String FAVOR_ACCEPT = "favor_accept";
    static final String FAVOR_DECLINE = "favor_decline";
    static final String FAVOR_HAND_IN = "favor_hand_in";
    static final String FAVOR_GIVE_UP = "favor_give_up";

    private VillagerDialogue() {
    }

    private static Component subtitle(ServerPlayer player, Villager villager) {
        ResourceLocation faction = FactionResolver.factionOf(villager).orElse(Factions.WANDERERS);
        RepManager.meet(player, faction);
        int rep = RepManager.getRep(player, faction);
        RepTier tier = RepManager.tierOf(rep);
        return Component.translatable("fealty.dialogue.subtitle", Factions.displayName(player.server, faction),
                tier.displayName().copy().withColor(tier.color()), rep);
    }

    static DialogueNode node(ServerPlayer player, Villager villager, @Nullable Component reply) {
        ChainManager.checkRole(villager);
        Component text = reply != null ? reply
                : DialogueLines.pick("greet", SpeakerContext.of(villager, player), villager.getRandom(), player.getDisplayName())
                .orElse(Component.translatable("fealty.dialogue.fallback"));

        List<DialogueNode.Option> options = new ArrayList<>();
        boolean canTrade = !villager.getOffers().isEmpty() && villager.getVillagerData().getProfession() != VillagerProfession.NITWIT;
        options.add(canTrade ? DialogueNode.Option.of(TRADE, Component.translatable("fealty.dialogue.option.trade"), "trade")
                : DialogueNode.Option.disabled(TRADE, Component.translatable("fealty.dialogue.option.trade"), "trade",
                Component.translatable("fealty.dialogue.no_trades")));
        options.add(workOption(player, villager));
        options.addAll(DialogueService.questOptions(player, villager));
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
        return new DialogueNode(villager.getDisplayName(), subtitle(player, villager), text, options);
    }

    /** "Need a hand?", or the state of a favor the player is doing for this villager. */
    private static DialogueNode.Option workOption(ServerPlayer player, Villager villager) {
        if (ChainManager.hasRole(villager)) {
            return DialogueNode.Option.of(WORK, Component.translatable("fealty.dialogue.option.task"), "quest");
        }
        List<QuestContext> favors = QuestManager.contexts(player, FavorManager.key(villager.getUUID()));
        if (!favors.isEmpty()) {
            boolean ready = favors.getFirst().definition().objective().canTurnIn(favors.getFirst());
            return DialogueNode.Option.of(WORK, Component.translatable(ready ? "fealty.dialogue.option.favor_done"
                    : "fealty.dialogue.option.task"), ready ? "ready" : "quest");
        }
        return DialogueNode.Option.of(WORK, Component.translatable("fealty.dialogue.option.work"), "quest");
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
            case WORK -> work(player, villager);
            case FAVOR_ACCEPT -> {
                Optional<ResourceLocation> favor = FavorManager.todaysFavor(player, villager);
                if (favor.isPresent() && FavorManager.accept(player, villager, favor.get())) {
                    DialogueService.reply(player, villager, VillagerInteractions.speak(player, villager, "favor_accepted",
                            Component.translatable("fealty.favor.accepted")));
                } else {
                    DialogueService.refresh(player, villager);
                }
            }
            case FAVOR_DECLINE -> DialogueService.reply(player, villager, VillagerInteractions.speak(player, villager, "favor_declined",
                    Component.translatable("fealty.favor.declined")));
            case FAVOR_HAND_IN -> {
                boolean done = QuestManager.turnIn(player, FavorManager.key(villager.getUUID()));
                DialogueService.reply(player, villager, VillagerInteractions.speak(player, villager, done ? "favor_thanks" : "favor_not_yet",
                        Component.translatable(done ? "fealty.favor.thanks" : "fealty.favor.not_yet")));
            }
            case FAVOR_GIVE_UP -> {
                QuestManager.abandon(player, FavorManager.key(villager.getUUID()), false);
                DialogueService.reply(player, villager, VillagerInteractions.speak(player, villager, "favor_given_up",
                        Component.translatable("fealty.favor.given_up")));
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

    /** Named chain villagers hand out their step; others ask their favor of the day, or have nothing. */
    private static void work(ServerPlayer player, Villager villager) {
        if (ChainManager.onTalk(player, villager)) {
            return; // their quest board is open now, and keeps them by the player
        }
        Component name = villager.getDisplayName();
        Component subtitle = subtitle(player, villager);
        List<QuestContext> favors = QuestManager.contexts(player, FavorManager.key(villager.getUUID()));
        if (!favors.isEmpty()) {
            QuestContext ctx = favors.getFirst();
            boolean ready = ctx.definition().objective().canTurnIn(ctx);
            List<DialogueNode.Option> options = new ArrayList<>();
            Component text;
            if (ready) {
                text = Component.translatable("fealty.favor.ready", ctx.definition().title());
                options.add(DialogueNode.Option.of(FAVOR_HAND_IN, Component.translatable("fealty.dialogue.option.favor_hand_in"), "ready"));
            } else {
                text = Component.translatable("fealty.favor.waiting", ctx.definition().title());
                options.add(DialogueNode.Option.of(BACK, Component.translatable("fealty.dialogue.option.working_on_it"), "quest"));
                options.add(DialogueNode.Option.of(FAVOR_GIVE_UP, Component.translatable("fealty.dialogue.option.favor_give_up"), "cross"));
            }
            options.add(DialogueNode.Option.of(DialogueService.BYE, Component.translatable("fealty.dialogue.option.bye"), "door"));
            DialogueService.show(player, villager, new DialogueNode(name, subtitle, text, options));
            return;
        }
        Optional<ResourceLocation> favor = FavorManager.todaysFavor(player, villager);
        Optional<RepQuestDefinition> def = favor.flatMap(FealtyDataManager::quest);
        if (def.isPresent()) {
            Component text = def.get().description().getString().isEmpty() ? def.get().title() : def.get().description();
            Speech.say(villager, text);
            List<DialogueNode.Option> options = new ArrayList<>();
            options.add(DialogueNode.Option.of(FAVOR_ACCEPT, Component.translatable("fealty.dialogue.option.favor_accept",
                    def.get().reward().rep()), "quest"));
            options.add(DialogueNode.Option.of(FAVOR_DECLINE, Component.translatable("fealty.dialogue.option.favor_decline"), "door"));
            DialogueService.show(player, villager, new DialogueNode(name, subtitle, text, options));
            return;
        }
        DialogueService.reply(player, villager, VillagerInteractions.speak(player, villager, "work_none",
                Component.translatable("fealty.dialogue.work_none")));
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
