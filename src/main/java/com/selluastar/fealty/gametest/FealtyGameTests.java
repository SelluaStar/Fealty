package com.selluastar.fealty.gametest;

import java.util.List;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.RepTiers;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.crime.CrimeService;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.outlaw.ThievesGuild;
import com.selluastar.fealty.quest.QuestLog;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.quest.RepQuestDefinition;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.trade.TradeHooks;
import com.selluastar.fealty.trade.VillagerInteractions;
import com.selluastar.fealty.trade.VillagerMemory;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the core rules. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Fealty.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FealtyGameTests {
    private static int counter;

    private FealtyGameTests() {
    }

    private static ResourceLocation freshFaction() {
        return Fealty.id("test_faction_" + (counter++));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        RepManager.data(player).setRenown(0);
        RepManager.data(player).rep().clear();
        return player;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    @GameTest(template = "empty")
    public static void tierTable(GameTestHelper helper) {
        check(helper, TierManager.tiers().size() >= 5, "expected 5 tiers, got " + TierManager.tiers().size());
        check(helper, RepManager.tierOf(-100).is(RepTiers.HATED), "-100 should be Hated");
        check(helper, RepManager.tierOf(-61).is(RepTiers.HATED), "-61 should be Hated");
        check(helper, RepManager.tierOf(-60).is(RepTiers.DISTRUSTED), "-60 should be Distrusted");
        check(helper, RepManager.tierOf(0).is(RepTiers.NEUTRAL), "0 should be Neutral");
        check(helper, RepManager.tierOf(21).is(RepTiers.TRUSTED), "21 should be Trusted");
        check(helper, RepManager.tierOf(100).is(RepTiers.HONORED), "100 should be Honored");
        check(helper, TierManager.honored().priceMultiplier() == 0.7F, "Honored price should be 0.7");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clampAndRenownShare(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ResourceLocation faction = freshFaction();
        RepManager.change(player, faction, 50, RepSources.COMMAND, true);
        check(helper, RepManager.getRep(player, faction) == 50, "rep should be 50");
        check(helper, RepManager.renown(player) == 5, "renown should move 10% (5), got " + RepManager.renown(player));
        RepManager.change(player, faction, -400, RepSources.COMMAND, true);
        check(helper, RepManager.getRep(player, faction) == -100, "rep should clamp at -100");
        check(helper, RepManager.renown(player) == -10, "renown should follow the applied change (-10), got " + RepManager.renown(player));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newFactionStartsAtQuarterRenown(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        RepManager.data(player).setRenown(40);
        check(helper, RepManager.getRep(player, freshFaction()) == 10, "a quarter of 40 Renown is 10");
        RepManager.data(player).setRenown(100);
        check(helper, RepManager.getRep(player, freshFaction()) == 25, "start is capped at 25");
        RepManager.data(player).setRenown(-100);
        check(helper, RepManager.getRep(player, freshFaction()) == -25, "start is capped at -25");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void negativeRepOnlyHealsThroughQuests(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ResourceLocation faction = freshFaction();
        RepManager.set(player, faction, -10, RepSources.COMMAND);
        check(helper, RepManager.applySource(player, faction, RepSources.GIFT) == 0, "a gift must not heal negative rep");
        check(helper, RepManager.change(player, faction, 5, RepSources.QUEST) == 5, "a quest must heal negative rep");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tradeRepIsCapped(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ResourceLocation faction = freshFaction();
        RepManager.meet(player, faction);
        int total = 0;
        for (int i = 0; i < 30; i++) {
            total += RepManager.applySource(player, faction, RepSources.TRADE);
        }
        check(helper, total == 3, "+1 per 5 trades, max +3 a day; got " + total);
        helper.succeed();
    }

    private static Villager villager(GameTestHelper helper, ResourceLocation faction, BlockPos pos) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, pos);
        villager.setData(ModAttachments.FACTION, faction);
        return villager;
    }

    @GameTest(template = "empty")
    public static void unwitnessedCrimeIsFree(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ResourceLocation faction = freshFaction();
        RepManager.meet(player, faction);
        int before = RepManager.getRep(player, faction);
        CrimeService.Result result = CrimeService.commit(player, faction, RepSources.STEAL, player.blockPosition(), null, false);
        check(helper, !result.witnessed(), "nobody was there to see it");
        check(helper, RepManager.getRep(player, faction) == before, "unwitnessed theft costs nothing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void witnessedCrimeCostsRep(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ResourceLocation faction = freshFaction();
        BlockPos playerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.moveTo(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        Villager witness = villager(helper, faction, new BlockPos(5, 1, 5));
        witness.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
        witness.setYHeadRot(witness.getYRot());
        RepManager.meet(player, faction);
        int before = RepManager.getRep(player, faction);
        CrimeService.Result result = CrimeService.commit(player, faction, RepSources.STEAL, player.blockPosition(), null, false);
        check(helper, result.witnessed(), "the villager should have seen it");
        check(helper, RepManager.getRep(player, faction) == before - 15, "theft costs 15, got " + (RepManager.getRep(player, faction) - before));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void honoredGetsBestPrices(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ResourceLocation faction = freshFaction();
        Villager villager = villager(helper, faction, new BlockPos(3, 1, 3));
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.BREAD), 12, 1, 0.05F));
        villager.setOffers(offers);
        RepManager.set(player, faction, 80, RepSources.COMMAND);
        TradeHooks.updatePrices(villager, player);
        int honored = villager.getOffers().getFirst().getCostA().getCount();
        TradeHooks.restore(villager);
        villager.getOffers().getFirst().resetSpecialPriceDiff();
        RepManager.set(player, faction, -80, RepSources.COMMAND);
        TradeHooks.updatePrices(villager, player);
        int hated = villager.getOffers().getFirst().getCostA().getCount();
        TradeHooks.restore(villager);
        check(helper, honored == 7, "Honored price should be 7 emeralds, got " + honored);
        check(helper, hated == 20, "Hated price should be 20 emeralds, got " + hated);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void threatsHaveACooldown(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ResourceLocation faction = freshFaction();
        Villager villager = villager(helper, faction, new BlockPos(2, 1, 2));
        RepManager.meet(player, faction);
        int before = RepManager.getRep(player, faction);
        VillagerInteractions.threaten(player, villager, faction);
        VillagerInteractions.threaten(player, villager, faction);
        VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
        check(helper, memory.threats == 1, "the second threat should be ignored during the cooldown");
        check(helper, memory.threatPrice, "a threatened villager offers the Neutral price once");
        check(helper, RepManager.getRep(player, faction) == before - 3, "a threat costs 3 rep");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void questPoolCycles(GameTestHelper helper) {
        QuestLog log = new QuestLog();
        List<ResourceLocation> all = QuestManager.roll(log, RepQuestDefinition.REDEMPTION, TierManager.neutral(), helper.getLevel().getRandom(), 100);
        check(helper, !all.isEmpty(), "the redemption pool should offer quests at Neutral");
        log.completed().addAll(all);
        List<ResourceLocation> again = QuestManager.roll(log, RepQuestDefinition.REDEMPTION, TierManager.neutral(), helper.getLevel().getRandom(), 3);
        check(helper, !again.isEmpty(), "once everything is done the pool should cycle");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void defaultDataLoads(GameTestHelper helper) {
        check(helper, FealtyDataManager.faction(Factions.VILLAGE_TEMPLATE).isPresent(), "village faction template missing");
        check(helper, FealtyDataManager.faction(Factions.BANDITS).isPresent(), "bandits faction missing");
        check(helper, FealtyDataManager.chain(ChainManager.RARE_CHAIN).isPresent(), "rare villager chain missing");
        check(helper, FealtyDataManager.chain(ThievesGuild.CHAIN).isPresent(), "thieves guild chain missing");
        check(helper, FealtyDataManager.quests().size() >= 15, "expected the default quest pool, got " + FealtyDataManager.quests().size());
        check(helper, !FealtyDataManager.blackMarket().isEmpty(), "black market offers missing");
        check(helper, !FealtyDataManager.villageTrades().isEmpty(), "village trades missing");
        check(helper, RepManager.baseAmount(RepSources.KILL_GUARD) == -50, "kill guard should cost 50");
        helper.succeed();
    }
}
