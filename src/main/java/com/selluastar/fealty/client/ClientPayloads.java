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

    public static void openQuestScreen(OpenQuestScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof QuestGiverScreen screen && screen.entityId() == payload.entityId()) {
            screen.refresh(payload);
        } else {
            minecraft.setScreen(new QuestGiverScreen(payload));
        }
    }
}
