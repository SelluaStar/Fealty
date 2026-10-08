package com.selluastar.fealty.client;

import com.selluastar.fealty.client.screen.QuestGiverScreen;
import com.selluastar.fealty.network.OpenQuestScreenPayload;
import com.selluastar.fealty.network.SyncStandingsPayload;
import com.selluastar.fealty.network.SyncTiersPayload;

import net.minecraft.client.Minecraft;

/** Client-side payload handlers. Only ever loaded on the physical client. */
public final class ClientPayloads {
    private ClientPayloads() {
    }

    public static void tiers(SyncTiersPayload payload) {
        ClientRepCache.setTiers(payload.tiers());
    }

    public static void standings(SyncStandingsPayload payload) {
        ClientRepCache.update(payload.standings(), payload.renown(), payload.replace());
    }

    public static void villageTrades(com.selluastar.fealty.network.SyncVillageTradesPayload payload) {
        ClientRepCache.setVillageTrades(payload.trades());
    }

    public static void feedback(com.selluastar.fealty.network.FeedbackPayload payload) {
        com.selluastar.fealty.client.hud.ClientFeedback.handle(payload);
    }

    public static void quests(com.selluastar.fealty.network.SyncQuestsPayload payload) {
        ClientQuestCache.set(payload.quests());
    }

    public static void openScreen(com.selluastar.fealty.network.OpenScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (com.selluastar.fealty.network.OpenScreenPayload.CLOSE.equals(payload.screen())) {
            if (minecraft.screen instanceof com.selluastar.fealty.client.screen.DialogueScreen) {
                minecraft.setScreen(null);
            }
            return;
        }
        if (com.selluastar.fealty.network.OpenScreenPayload.JOURNAL.equals(payload.screen())) {
            com.selluastar.fealty.client.screen.JournalScreen.Page page = com.selluastar.fealty.client.screen.JournalScreen.Page.QUESTS;
            for (com.selluastar.fealty.client.screen.JournalScreen.Page p : com.selluastar.fealty.client.screen.JournalScreen.Page.values()) {
                if (p.name().equalsIgnoreCase(payload.argument())) {
                    page = p;
                }
            }
            minecraft.setScreen(new com.selluastar.fealty.client.screen.JournalScreen(page));
        }
    }

    public static void dialogue(com.selluastar.fealty.network.OpenDialoguePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.selluastar.fealty.client.screen.DialogueScreen screen && screen.entityId() == payload.entityId()) {
            screen.update(payload.node());
        } else {
            minecraft.setScreen(new com.selluastar.fealty.client.screen.DialogueScreen(payload.entityId(), payload.node()));
        }
    }

    public static void speech(com.selluastar.fealty.network.SpeechBubblePayload payload) {
        com.selluastar.fealty.client.bubble.SpeechBubbles.add(payload.entityId(), payload.text(), payload.ticks());
    }

    public static void markers(com.selluastar.fealty.network.QuestMarkersPayload payload) {
        com.selluastar.fealty.client.bubble.QuestMarkers.set(payload.markers());
    }

    public static void retinue(com.selluastar.fealty.network.RetinuePayload payload) {
        com.selluastar.fealty.client.hud.RetinueLayer.set(payload.members());
    }

    public static void openLockpick(com.selluastar.fealty.network.OpenLockpickPayload payload) {
        Minecraft.getInstance().setScreen(new com.selluastar.fealty.client.screen.LockpickScreen(payload));
    }

    public static void lockpickResult(com.selluastar.fealty.network.LockpickResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof com.selluastar.fealty.client.screen.LockpickScreen screen) {
            screen.result(payload);
        }
    }

    public static void openHorn(com.selluastar.fealty.network.OpenHornPayload payload) {
        Minecraft.getInstance().setScreen(new com.selluastar.fealty.client.screen.HornScreen(payload));
    }

    public static void openMailbox(com.selluastar.fealty.network.OpenMailboxPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.selluastar.fealty.client.screen.MailboxScreen screen && screen.pos().equals(payload.pos())) {
            screen.refresh(payload);
        } else {
            minecraft.setScreen(new com.selluastar.fealty.client.screen.MailboxScreen(payload));
        }
    }

    public static void mailStatus(com.selluastar.fealty.network.MailStatusPayload payload) {
        com.selluastar.fealty.client.hud.MailLayer.setUnread(payload.unread());
    }

    public static void fineStatus(com.selluastar.fealty.network.FineStatusPayload payload) {
        ClientRepCache.setFine(payload.pending(), payload.village(), payload.cost(), payload.secondsLeft());
        if (Minecraft.getInstance().screen instanceof com.selluastar.fealty.client.screen.JournalScreen screen) {
            screen.refreshFine();
        }
    }

    public static void openHall(com.selluastar.fealty.network.OpenHallPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.selluastar.fealty.client.screen.LordshipScreen screen && screen.village().equals(payload.village())) {
            screen.refresh(payload);
        } else {
            minecraft.setScreen(new com.selluastar.fealty.client.screen.LordshipScreen(payload));
        }
    }

    public static void openQuestScreen(OpenQuestScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof QuestGiverScreen screen && screen.entityId() == payload.entityId()) {
            screen.refresh(payload);
        } else {
            minecraft.setScreen(new QuestGiverScreen(payload));
        }
    }
}
