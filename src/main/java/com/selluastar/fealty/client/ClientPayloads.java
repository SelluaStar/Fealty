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

    public static void openQuestScreen(OpenQuestScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof QuestGiverScreen screen && screen.entityId() == payload.entityId()) {
            screen.refresh(payload);
        } else {
            minecraft.setScreen(new QuestGiverScreen(payload));
        }
    }
}
