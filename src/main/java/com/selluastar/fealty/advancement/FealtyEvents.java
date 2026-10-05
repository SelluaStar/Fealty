package com.selluastar.fealty.advancement;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.registry.ModCriteria;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Story moments reported through the {@code fealty:event} trigger (usable by advancements and FTB Quests). */
public final class FealtyEvents {
    public static final ResourceLocation MET_ELDER = Fealty.id("met_elder");
    public static final ResourceLocation THREATENED = Fealty.id("threatened");
    public static final ResourceLocation GIFT_GIVEN = Fealty.id("gift_given");
    public static final ResourceLocation GIFT_RECEIVED = Fealty.id("gift_received");
    public static final ResourceLocation ESCORTED = Fealty.id("escorted");
    public static final ResourceLocation RAID_DEFENDED = Fealty.id("raid_defended");
    public static final ResourceLocation REDEEMED = Fealty.id("redeemed");
    public static final ResourceLocation COURIER_DELIVERED = Fealty.id("courier_delivered");
    public static final ResourceLocation ELDER_RESTORED = Fealty.id("elder_restored");
    public static final ResourceLocation VILLAGE_BROKEN = Fealty.id("village_broken");
    public static final ResourceLocation CHAIN_STARTED = Fealty.id("chain_started");
    public static final ResourceLocation SIGNET_EARNED = Fealty.id("signet_earned");
    public static final ResourceLocation HAMLET_FOUND = Fealty.id("hamlet_found");
    public static final ResourceLocation KEEPER_REFUSED = Fealty.id("keeper_refused");
    public static final ResourceLocation CHARTER_EARNED = Fealty.id("charter_earned");
    public static final ResourceLocation WRIT_FORGED = Fealty.id("writ_forged");
    public static final ResourceLocation SWORN_LORD = Fealty.id("sworn_lord");
    public static final ResourceLocation USURPED = Fealty.id("usurped");
    public static final ResourceLocation LORDSHIP_LOST = Fealty.id("lordship_lost");
    public static final ResourceLocation TRIBUTE_COLLECTED = Fealty.id("tribute_collected");
    public static final ResourceLocation GUARDS_COMMANDED = Fealty.id("guards_commanded");
    public static final ResourceLocation BLACK_MARKET_TRADE = Fealty.id("black_market_trade");
    public static final ResourceLocation GUILD_JOINED = Fealty.id("guild_joined");
    public static final ResourceLocation GUILD_COMPLETED = Fealty.id("guild_completed");
    public static final ResourceLocation PICKPOCKETED = Fealty.id("pickpocketed");
    public static final ResourceLocation UNSEEN_THEFT = Fealty.id("unseen_theft");
    public static final ResourceLocation VAULT_RAIDED = Fealty.id("vault_raided");
    public static final ResourceLocation FOLLOWER_HIRED = Fealty.id("follower_hired");
    public static final ResourceLocation WANTED = Fealty.id("wanted");
    public static final ResourceLocation BOUNTY_HUNTER_SLAIN = Fealty.id("bounty_hunter_slain");
    public static final ResourceLocation TYRANT_RISEN = Fealty.id("tyrant_risen");
    public static final ResourceLocation TYRANT_SLAIN = Fealty.id("tyrant_slain");
    public static final ResourceLocation CAMP_CLEARED = Fealty.id("camp_cleared");

    private FealtyEvents() {
    }

    public static void fire(ServerPlayer player, ResourceLocation event) {
        ModCriteria.EVENT.get().trigger(player, event);
    }
}
