package com.selluastar.fealty.client;

import com.selluastar.fealty.client.screen.LedgerScreen;

import net.minecraft.client.Minecraft;

/** Client-only entry points called from common code behind side checks. */
public final class FealtyClientHooks {
    private FealtyClientHooks() {
    }

    public static void openLedger() {
        Minecraft.getInstance().setScreen(new LedgerScreen());
    }
}
