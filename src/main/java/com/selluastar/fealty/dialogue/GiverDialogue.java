package com.selluastar.fealty.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.entity.VillageElderEntity;
import com.selluastar.fealty.network.OpenQuestScreenPayload;
import com.selluastar.fealty.quest.QuestGiver;
import com.selluastar.fealty.quest.QuestGivers;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Dialogue for quest givers (the elder, the Keeper, the guild fence): their greeting, the way to their quests, and
 * their special actions (rumours, swearing fealty, the Writ, ...) as replies.
 */
final class GiverDialogue {
    static final String WORK = "work";
    static final String DELIVER = "deliver";
    static final String ACTION = "action:";

    private GiverDialogue() {
    }

    static DialogueNode node(ServerPlayer player, Entity npc, QuestGiver giver, @Nullable Component reply) {
        OpenQuestScreenPayload screen = giver.screen(player, npc);
        Component subtitle = screen.subtitle();
        if (screen.showRep()) {
            RepTier tier = RepManager.tierOf(screen.rep());
            subtitle = Component.translatable("fealty.dialogue.subtitle", screen.subtitle(),
                    tier.displayName().copy().withColor(tier.color()), screen.rep());
        }
        Component text = reply != null ? reply
                : screen.greeting().getString().isEmpty() ? Component.translatable("fealty.dialogue.fallback") : screen.greeting();

        List<DialogueNode.Option> options = new ArrayList<>();
        boolean ready = screen.quests().stream().anyMatch(q -> q.status() == OpenQuestScreenPayload.Status.READY);
        boolean active = screen.quests().stream().anyMatch(q -> q.status() == OpenQuestScreenPayload.Status.ACTIVE);
        if (ready) {
            options.add(DialogueNode.Option.of(WORK, Component.translatable("fealty.dialogue.option.hand_in"), "ready"));
        } else if (active) {
            options.add(DialogueNode.Option.of(WORK, Component.translatable("fealty.dialogue.option.task"), "quest"));
        } else if (!screen.quests().isEmpty()) {
            options.add(DialogueNode.Option.of(WORK, Component.translatable("fealty.dialogue.option.quests"), "quest"));
        } else {
            options.add(DialogueNode.Option.disabled(WORK, Component.translatable("fealty.dialogue.option.quests"), "quest",
                    Component.translatable("fealty.dialogue.no_work")));
        }
        Optional<VillageRecord> village = elderVillage(player, npc);
        if (village.isPresent() && QuestManager.hasLettersFor(player, village.get())) {
            options.add(DialogueNode.Option.of(DELIVER, Component.translatable("fealty.dialogue.option.deliver"), "mail"));
        }
        for (OpenQuestScreenPayload.ActionEntry action : screen.actions()) {
            options.add(new DialogueNode.Option(ACTION + action.id(), action.label(), iconFor(action.id()), action.enabled(), action.hint()));
        }
        options.add(DialogueNode.Option.of(DialogueService.BYE, Component.translatable("fealty.dialogue.option.bye"), "door"));
        if (npc instanceof Mob mob) {
            mob.getLookControl().setLookAt(player);
        }
        return new DialogueNode(screen.title(), subtitle, text, options);
    }

    static void handle(ServerPlayer player, Entity npc, QuestGiver giver, String option) {
        if (WORK.equals(option)) {
            DialogueService.end(player);
            QuestGivers.open(player, npc);
        } else if (DELIVER.equals(option)) {
            Optional<VillageRecord> village = elderVillage(player, npc);
            if (village.isPresent() && QuestManager.tryDeliverLetters(player, village.get())) {
                Component thanks = Component.translatable("fealty.dialogue.letter_thanks");
                Speech.say(npc, thanks);
                DialogueService.reply(player, npc, thanks);
            } else {
                DialogueService.refresh(player, npc);
            }
        } else if (option.startsWith(ACTION)) {
            giver.handleAction(player, npc, option.substring(ACTION.length()), "");
            if (npc.isAlive()) {
                DialogueService.refresh(player, npc);
            }
        }
    }

    private static Optional<VillageRecord> elderVillage(ServerPlayer player, Entity npc) {
        if (npc instanceof VillageElderEntity elder && elder.village() != null) {
            return FealtyWorldData.get(player.server).village(elder.village());
        }
        return Optional.empty();
    }

    private static String iconFor(String action) {
        if (action.contains("rumour")) {
            return "scroll";
        }
        if (action.contains("swear") || action.contains("lord")) {
            return "crown";
        }
        if (action.contains("tax")) {
            return "tax";
        }
        if (action.contains("horn")) {
            return "horn";
        }
        if (action.contains("combine") || action.contains("writ")) {
            return "seal";
        }
        return "talk";
    }
}
