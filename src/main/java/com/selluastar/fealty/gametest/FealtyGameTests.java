package com.selluastar.fealty.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.FealtyApi;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.RepTiers;
import com.selluastar.fealty.api.Severity;
import com.selluastar.fealty.api.Stronghold;
import com.selluastar.fealty.api.dialogue.DialogueReply;
import com.selluastar.fealty.api.event.BanditRaidEvent;
import com.selluastar.fealty.api.event.CampaignEvent;
import com.selluastar.fealty.api.event.ChainStageEvent;
import com.selluastar.fealty.api.event.DialogueBuildEvent;
import com.selluastar.fealty.api.event.RumourEvent;
import com.selluastar.fealty.api.event.StrongholdEvent;
import com.selluastar.fealty.api.event.VillagerChatEvent;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.chain.QuestChainDefinition;
import com.selluastar.fealty.chatter.Chatter;
import com.selluastar.fealty.chatter.ChatterTopic;
import com.selluastar.fealty.chatter.ChatterTopics;
import com.selluastar.fealty.chatter.LiveTopics;
import com.selluastar.fealty.chatter.Rumours;
import com.selluastar.fealty.chatter.Topic;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.crime.CrimeHandlers;
import com.selluastar.fealty.crime.CrimeService;
import com.selluastar.fealty.crime.Fines;
import com.selluastar.fealty.crime.Gossip;
import com.selluastar.fealty.crime.Locks;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.dialogue.Conversation;
import com.selluastar.fealty.dialogue.Conversations;
import com.selluastar.fealty.dialogue.DialogueLines;
import com.selluastar.fealty.dialogue.DialogueNode;
import com.selluastar.fealty.dialogue.DialogueService;
import com.selluastar.fealty.dialogue.GuardDialogue;
import com.selluastar.fealty.guard.Garrison;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.lordship.LordshipManager;
import com.selluastar.fealty.mail.MailService;
import com.selluastar.fealty.network.QuestActionPayload;
import com.selluastar.fealty.outlaw.BanditCamps;
import com.selluastar.fealty.outlaw.BanditRaids;
import com.selluastar.fealty.outlaw.ThievesGuild;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestLog;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.quest.RepQuestDefinition;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.rep.DailyTicker;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyCalendar;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.story.PlayerFlags;
import com.selluastar.fealty.story.RumourDefinition;
import com.selluastar.fealty.story.RumourService;
import com.selluastar.fealty.trade.TradeHooks;
import com.selluastar.fealty.trade.VillagerInteractions;
import com.selluastar.fealty.trade.VillagerMemory;
import com.selluastar.fealty.util.Compass;
import com.selluastar.fealty.village.SitePlanner;
import com.selluastar.fealty.village.StructureMatcher;
import com.selluastar.fealty.village.VillageLayout;
import com.selluastar.fealty.village.VillageLayouts;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;
import com.selluastar.fealty.war.Campaigns;
import com.selluastar.fealty.war.Captives;
import com.selluastar.fealty.war.StrongholdKind;
import com.selluastar.fealty.war.StrongholdKinds;
import com.selluastar.fealty.war.StrongholdNames;
import com.selluastar.fealty.war.StrongholdTrait;
import com.selluastar.fealty.war.Strongholds;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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

    /**
     * A survival player. The vanilla {@link GameTestHelper#makeMockServerPlayerInLevel()} player always counts as
     * creative, and creative players commit no crimes.
     */
    private static ServerPlayer player(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
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

    // ---- 0.2: villages from any mod, the calendar, mail, camps, crimes, fines, word travelling, locks ----

    @GameTest(template = "empty")
    public static void structurePatterns(GameTestHelper helper) {
        Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder<Structure> plains = structures.getHolderOrThrow(BuiltinStructures.VILLAGE_PLAINS);
        Holder<Structure> desert = structures.getHolderOrThrow(BuiltinStructures.VILLAGE_DESERT);
        Holder<Structure> city = structures.getHolderOrThrow(BuiltinStructures.ANCIENT_CITY);
        check(helper, new StructureMatcher(List.of("#minecraft:village"), List.of()).matches(plains), "the village tag should match");
        StructureMatcher glob = new StructureMatcher(List.of("*:*village*", "*:*city*"), List.of("minecraft:village_plains", "minecraft:ancient_*"));
        check(helper, glob.matches(desert), "a pattern should match village_desert");
        check(helper, !glob.matches(plains), "an excluded id should not match");
        check(helper, !glob.matches(city), "an excluded pattern should not match");
        check(helper, !new StructureMatcher(List.of("*:*castle*"), List.of()).matches(plains), "castle should not match a village");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void calendarNeverGoesBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        long time = overworld.getDayTime();
        FealtyCalendar.tick(server);
        long day = FealtyCalendar.day(server);
        overworld.setDayTime(Math.max(0, time - 48000));
        FealtyCalendar.tick(server);
        check(helper, FealtyCalendar.day(server) == day, "rewinding the clock must not change the day");
        overworld.setDayTime(time + 48000);
        FealtyCalendar.tick(server);
        check(helper, FealtyCalendar.day(server) > day, "a day passing should advance the calendar");
        overworld.setDayTime(time);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mailTakesLongerFurther(GameTestHelper helper) {
        long near = MailService.travelTime(0);
        long far = MailService.travelTime(2000);
        long worldAway = MailService.travelTime(10_000_000);
        check(helper, near == FealtyConfig.MAIL_BASE_DELAY.get() * 20L, "a letter next door takes the base delay");
        check(helper, far > near, "a letter further away takes longer");
        check(helper, worldAway == FealtyConfig.MAIL_MAX_DELAY.get() * 20L, "no letter takes longer than the cap");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void campsKeepTheirDistance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BanditCamps camps = BanditCamps.get(level.getServer());
        BlockPos first = new BlockPos(5_000_000 + counter++ * 10_000, 64, 5_000_000);
        BlockPos second = first.offset(100, 0, 0);
        BlockPos far = first.offset(5_000, 0, 0);
        camps.register(level, first);
        camps.register(level, second);
        camps.register(level, far);
        check(helper, camps.isActive(level, first), "the first camp found holds the ground");
        check(helper, !camps.isActive(level, second), "a camp close to a manned one stands empty");
        check(helper, camps.isActive(level, far), "a camp far away is manned");
        camps.unregister(level, first);
        check(helper, camps.isActive(level, second), "with the first camp gone, the next takes its place");
        camps.unregister(level, second);
        camps.unregister(level, far);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hittingAChildIsWorse(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ResourceLocation faction = freshFaction();
        Villager child = villager(helper, faction, new BlockPos(2, 1, 2));
        child.setBaby(true);
        RepManager.meet(player, faction);
        int before = RepManager.getRep(player, faction);
        CrimeHandlers.assault(player, child, faction);
        check(helper, RepManager.getRep(player, faction) == before + RepManager.baseAmount(RepSources.HIT_CHILD),
                "hitting a child costs " + RepManager.baseAmount(RepSources.HIT_CHILD) + ", got " + (RepManager.getRep(player, faction) - before));
        CrimeHandlers.assault(player, child, faction);
        check(helper, RepManager.getRep(player, faction) == before + RepManager.baseAmount(RepSources.HIT_CHILD),
                "a second blow right after the first is the same assault");
        helper.succeed();
    }

    /** A village made by hand at the test's spot (and forgotten again by {@link #forget}). */
    private static VillageRecord testVillage(GameTestHelper helper, BlockPos offset, String name) {
        return VillageResolver.createManual(helper.getLevel(), helper.absolutePos(BlockPos.ZERO).offset(offset), name, 8);
    }

    private static void forget(GameTestHelper helper, VillageRecord... villages) {
        for (VillageRecord village : villages) {
            FealtyWorldData.get(helper.getLevel().getServer()).removeVillage(village.id());
        }
        VillageResolver.clearCache();
    }

    @GameTest(template = "empty")
    public static void finesClearYourName(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Finebury");
        try {
            RepManager.meet(player, village.id());
            RepManager.set(player, village.id(), -30, RepSources.COMMAND);
            RepManager.data(player).aggroUntil().put(village.id(), player.level().getGameTime() + 2400);
            Optional<Fines.Quote> quote = Fines.quote(player, village);
            check(helper, quote.isPresent() && quote.get().available(), "a player with a bad name may pay a fine");
            int cost = quote.get().cost();
            int restore = quote.get().restore();
            check(helper, restore == 20, "a fine wins back at most 20, got " + restore);
            player.getInventory().add(new ItemStack(Items.EMERALD, cost));
            Fines.payElder(player, village, player);
            check(helper, RepManager.getRep(player, village.id()) == -30 + restore, "paying should win back " + restore);
            check(helper, player.getInventory().countItem(Items.EMERALD) == 0, "the fine should cost " + cost + " emeralds");
            check(helper, !RepManager.data(player).aggroUntil().containsKey(village.id()), "paying calls off the watch");
        } finally {
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wordTravels(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        VillageRecord home = testVillage(helper, BlockPos.ZERO, "Gossiping");
        VillageRecord neighbour = testVillage(helper, new BlockPos(600, 0, 0), "Listening");
        VillageRecord distant = testVillage(helper, new BlockPos(5000, 0, 0), "Faraway");
        try {
            for (VillageRecord village : List.of(home, neighbour, distant)) {
                RepManager.meet(player, village.id());
                RepManager.set(player, village.id(), 0, RepSources.COMMAND);
            }
            Gossip.heard(player, home.id(), -40);
            Gossip.spread(player.server);
            int heard = RepManager.getRep(player, neighbour.id());
            check(helper, heard == (int) Math.round(-40 * FealtyConfig.WORD_TRAVELS_SHARE.get()) || FealtyConfig.WORD_TRAVELS_RADIUS.get() < 600,
                    "a neighbour should hear a quarter of it, got " + heard);
            check(helper, RepManager.getRep(player, distant.id()) == 0, "a distant village hears nothing");
            check(helper, RepManager.getRep(player, home.id()) == 0, "the village itself already counted the crime");
        } finally {
            forget(helper, home, neighbour, distant);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void locksKeepStrangersOut(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Lockton");
        try {
            ServerLevel level = helper.getLevel();
            BlockPos chest = helper.absolutePos(new BlockPos(1, 1, 1));
            BlockPos coffer = helper.absolutePos(new BlockPos(2, 1, 1));
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
            helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.VILLAGE_COFFER.get());
            RepManager.meet(player, village.id());
            RepManager.set(player, village.id(), 0, RepSources.COMMAND);
            boolean lockedChest = Locks.isLocked(player, level, chest, level.getBlockState(chest));
            check(helper, lockedChest == FealtyConfig.LOCKED_VILLAGE_CHESTS.get(), "village chests are locked to strangers");
            check(helper, Locks.isLocked(player, level, coffer, level.getBlockState(coffer)), "the coffer is locked");
            RepManager.set(player, village.id(), 40, RepSources.COMMAND);
            check(helper, !Locks.isLocked(player, level, chest, level.getBlockState(chest)), "a trusted friend may open the chests");
            check(helper, Locks.isLocked(player, level, coffer, level.getBlockState(coffer)), "only the lord may open the coffer");
        } finally {
            forget(helper, village);
        }
        helper.succeed();
    }

    // ---- Dialogue, guards and the treasury ----

    @GameTest(template = "empty")
    public static void dialogueOptionsInOrder(GameTestHelper helper) {
        DialogueNode node = new DialogueNode(Component.literal("Ann"), Component.empty(), Component.literal("Hello"), List.of(
                DialogueNode.Option.of(DialogueService.BYE, Component.literal("Bye"), "door"),
                DialogueNode.Option.of("trade", Component.literal("Trade"), "trade"),
                DialogueNode.Option.of("q:abc:deliver", Component.literal("Here's your letter"), "mail"),
                DialogueNode.Option.of("news", Component.literal("News?"), "talk")));
        List<String> ids = DialogueService.ordered(node).options().stream().map(DialogueNode.Option::id).toList();
        check(helper, ids.equals(List.of("q:abc:deliver", "trade", "news", DialogueService.BYE)),
                "quest business first, goodbye last, the rest as offered; got " + ids);
        helper.succeed();
    }

    private static boolean chatVeto;
    private static boolean extraRumour;
    private static boolean chatListening;

    /** Test-only listeners for chats and rumours, switched by flags (the event bus has no easy way to remove a lambda). */
    private static void listenForChatter() {
        if (!chatListening) {
            chatListening = true;
            NeoForge.EVENT_BUS.addListener((VillagerChatEvent.Started e) -> {
                if (chatVeto) {
                    e.setCanceled(true);
                }
            });
            NeoForge.EVENT_BUS.addListener((RumourEvent.Gather e) -> {
                if (extraRumour) {
                    e.add(Fealty.id("test/extra"), Component.literal("extra"), 1_000_000);
                }
            });
        }
    }

    @GameTest(template = "empty")
    public static void chatterTopicsLoad(GameTestHelper helper) {
        check(helper, ChatterTopics.all().size() >= 20, "villagers should have things to chat about, got " + ChatterTopics.all().size());
        ChatterTopics.all().forEach((id, topic) -> check(helper, topic.lines().size() >= 2 && !topic.tell().getString().isEmpty(),
                id + " needs two lines and something to tell"));
        for (String context : List.of("rumour_trivia", "rumour_refuse", "chatter_hush")) {
            check(helper, DialogueLines.has(context), "the " + context + " lines should load");
        }
        check(helper, Compass.point(BlockPos.ZERO, new BlockPos(100, 0, 0)).equals("e"), "east");
        check(helper, Compass.point(BlockPos.ZERO, new BlockPos(100, 0, -100)).equals("ne"), "north is -z");
        check(helper, Compass.roughDistance(BlockPos.ZERO, new BlockPos(120, 0, 0)) == 100
                && Compass.roughDistance(BlockPos.ZERO, new BlockPos(130, 0, 0)) == 150, "distances round to 50");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void villagersChatThenTell(GameTestHelper helper) {
        listenForChatter();
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Gossipton");
        MinecraftServer server = helper.getLevel().getServer();
        Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 1));
        ChatterTopic file = ChatterTopics.all().get(Fealty.id("bagel"));
        Chatter.reset();
        try {
            check(helper, file != null, "the bagel topic should load");
            Topic topic = new Topic(Fealty.id("bagel"), file.lines(), file.tell(), false, 1, null);
            chatVeto = true;
            check(helper, !Chatter.start(helper.getLevel(), a, b, topic), "a listener can veto a chat");
            chatVeto = false;
            check(helper, Chatter.start(helper.getLevel(), a, b, topic), "two villagers close by can start chatting");
            check(helper, Chatter.isChatting(a) && Chatter.isChatting(b) && Chatter.active() == 1, "both are chatting");
            check(helper, !Chatter.start(helper.getLevel(), b, a, topic), "a villager chats with one other at a time");
            check(helper, Chatter.tellable(a).isEmpty(), "before a word is said there is nothing to tell");
            Chatter.fastForward(server);
            check(helper, Chatter.tellable(b).isPresent(), "once they have spoken, either can be asked what it was about");
            RepManager.meet(player, village.id());
            Rumours.Told told = Rumours.ask(player, b);
            check(helper, told.fromChat() && told.topic().equals(topic.id()) && told.text().equals(topic.tell()),
                    "they tell what the chat was about");
            check(helper, !Chatter.isChatting(a) && Chatter.tellable(a).isEmpty(), "the chat is over once told");
            check(helper, !Rumours.ask(player, a).fromChat(), "asked again, they have other news");
        } finally {
            chatVeto = false;
            Chatter.reset();
            a.discard();
            b.discard();
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rumoursFollowStanding(GameTestHelper helper) {
        listenForChatter();
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Hearsay");
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        try {
            RepManager.meet(player, village.id());
            RepManager.set(player, village.id(), TierManager.hated().min(), RepSources.COMMAND);
            check(helper, Rumours.ask(player, villager).topic().equals(Rumours.REFUSED), "a village that hates you tells you nothing");

            RepManager.set(player, village.id(), TierManager.distrusted().min(), RepSources.COMMAND);
            for (int i = 0; i < 25; i++) {
                check(helper, !Rumours.ask(player, villager).useful(), "distrust gets you small talk only");
            }

            RepManager.set(player, village.id(), TierManager.neutral().min(), RepSources.COMMAND);
            int useful = 0;
            for (int i = 0; i < 40; i++) {
                if (Rumours.ask(player, villager).useful()) {
                    useful++;
                }
            }
            check(helper, useful == 1, "a villager tells real news once a day, got " + useful);
        } finally {
            villager.discard();
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void otherModsAddRumours(GameTestHelper helper) {
        listenForChatter();
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Extraford");
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        extraRumour = true;
        try {
            RepManager.meet(player, village.id());
            RepManager.set(player, village.id(), TierManager.neutral().min(), RepSources.COMMAND);
            Rumours.Told told = null;
            for (int i = 0; i < 40 && (told == null || !told.useful()); i++) {
                told = Rumours.ask(player, villager);
            }
            check(helper, told != null && told.topic().equals(Fealty.id("test/extra")), "a listener's rumour joins the pool");
        } finally {
            extraRumour = false;
            villager.discard();
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void villagersChatAboutStrongholds(GameTestHelper helper) {
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Smokewatch");
        ServerLevel level = helper.getLevel();
        Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 1));
        Strongholds strongholds = Strongholds.get(level.getServer());
        Strongholds.Entry camp = strongholds.register(level, village.center().offset(200, 0, 0), Stronghold.Kind.OUTPOST,
                ResourceLocation.withDefaultNamespace("pillager_outpost"), 24, StrongholdEvent.Discovered.How.SCOUTED);
        try {
            Topic found = null;
            for (int i = 0; i < 80 && found == null; i++) {
                Optional<Topic> topic = LiveTopics.forChat(level, village, a, b, level.getRandom());
                if (topic.isPresent() && topic.get().id().equals(LiveTopics.STRONGHOLD)) {
                    found = topic.get();
                }
            }
            check(helper, found != null, "villagers near a known stronghold gossip about it");
            check(helper, found.lines().size() == 2, "the bubbles only hint at it");
            check(helper, found.tell().getContents() instanceof TranslatableContents tell && tell.getKey().equals("fealty.rumour.stronghold")
                    && tell.getArgs()[1].equals(200) && ((Component) tell.getArgs()[2]).getContents() instanceof TranslatableContents dir
                    && dir.getKey().equals("fealty.compass.e"), "the tale gives the distance and the direction");
        } finally {
            strongholds.remove(camp);
            a.discard();
            b.discard();
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finesPayFromTheButtons(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Buttonford");
        // A mob standing in for another mod's guard.
        Mob guard = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        guard.setData(ModAttachments.FACTION, village.id());
        guard.setNoAi(true);
        try {
            RepManager.meet(player, village.id());
            check(helper, Fines.status(player).isEmpty(), "no fine is owed to begin with");
            check(helper, Fines.issue(player, guard, village.id(), RepSources.HIT_VILLAGER, -10), "the guard demands a fine");
            Optional<Fines.Status> status = Fines.status(player);
            check(helper, status.isPresent() && status.get().cost() == 5 && status.get().secondsLeft() > 0, "the fine is owed: " + status);
            check(helper, !Fines.payPending(player) && Fines.status(player).isPresent(), "short of emeralds, the fine stays");
            player.getInventory().add(new ItemStack(Items.EMERALD, 5));
            check(helper, Fines.payPending(player), "the button pays the fine without the guard");
            check(helper, Fines.status(player).isEmpty() && player.getInventory().countItem(Items.EMERALD) == 0, "paid, and the emeralds are gone");
            check(helper, !Fines.refusePending(player), "nothing left to refuse");

            check(helper, Fines.issue(player, guard, village.id(), RepSources.HIT_VILLAGER, -10), "a second fine");
            check(helper, Fines.refusePending(player), "the button refuses the fine");
            check(helper, Fines.status(player).isEmpty() && RepManager.data(player).aggroUntil().containsKey(village.id()),
                    "a refused fine turns the watch hostile");
        } finally {
            Fines.refusePending(player);
            guard.discard();
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void otherGuardsTalkLikeGuards(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Guardford");
        Mob guard = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        guard.setData(ModAttachments.FACTION, village.id());
        guard.setNoAi(true);
        Mob golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(3, 1, 1));
        try {
            check(helper, !GuardManager.talksLikeGuard(golem), "iron golems have nothing to say");
            check(helper, !GuardManager.talksLikeGuard(guard), "a mob outside #fealty:guards is no guard");
            List<String> ids = GuardDialogue.node(player, guard, null).options().stream().map(DialogueNode.Option::id).toList();
            check(helper, ids.contains("report") && ids.contains("open_pack") && ids.contains(DialogueService.BYE),
                    "another mod's guard offers news, its own screen and goodbye; got " + ids);
            RepManager.meet(player, village.id());
            check(helper, Fines.issue(player, guard, village.id(), RepSources.HIT_VILLAGER, -10), "the guard demands a fine");
            ids = GuardDialogue.node(player, guard, null).options().stream().map(DialogueNode.Option::id).toList();
            check(helper, ids.equals(List.of("pay_fine", "refuse_fine")), "a guard waiting on a fine talks of nothing else; got " + ids);
        } finally {
            Fines.refusePending(player);
            guard.discard();
            golem.discard();
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void otherGuardsOnTheRoster(GameTestHelper helper) {
        Garrison garrison = new Garrison();
        UUID golem = UUID.randomUUID();
        UUID guard = UUID.randomUUID();
        garrison.noteOther(golem, "golem", "", 10);
        garrison.noteOther(guard, "guard", "Bert", 10);
        check(helper, garrison.allTotal() == 2 && garrison.allAlive() == 2, "two other guards on the roster");
        garrison.otherDied(guard, 11);
        check(helper, garrison.allAlive() == 1, "a fallen guard is not counted as standing");
        garrison.forgetOthers(12);
        check(helper, garrison.allTotal() == 2, "the fallen stay on the roster a little while");
        garrison.forgetOthers(13);
        check(helper, garrison.allTotal() == 0, "the fallen and the long unseen are forgotten");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void giftsOfLoveNeedLightTaxes(GameTestHelper helper) {
        check(helper, LordshipManager.giftChance(4, 100) == 0.0, "no gifts under crushing taxes");
        check(helper, LordshipManager.giftChance(0, 95) > LordshipManager.giftChance(0, 70), "an adored lord gets more gifts");
        check(helper, LordshipManager.giftChance(0, 70) > LordshipManager.giftChance(0, 30), "an honoured lord gets more than a trusted one");
        check(helper, LordshipManager.giftChance(0, 70) > LordshipManager.giftChance(2, 70), "lighter taxes, likelier gifts");
        check(helper, LordshipManager.giftChance(0, 0) == 0.0, "a village that does not love its lord gives no gifts");
        check(helper, LordshipManager.tributePerDay(new VillageRecord(Fealty.id("test_tribute"), Level.OVERWORLD, BlockPos.ZERO,
                        new BoundingBox(0, 0, 0, 1, 1, 1), Factions.VILLAGE_TEMPLATE, null, "Test", false), 4)
                > 3 * LordshipManager.tributePerDay(new VillageRecord(Fealty.id("test_tribute"), Level.OVERWORLD, BlockPos.ZERO,
                        new BoundingBox(0, 0, 0, 1, 1, 1), Factions.VILLAGE_TEMPLATE, null, "Test", false), 2),
                "crushing taxes bring in more than three times what fair ones do");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void villageLayoutsPickTheHall(GameTestHelper helper) {
        Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder<Structure> plains = structures.getHolderOrThrow(BuiltinStructures.VILLAGE_PLAINS);
        Optional<VillageLayout> vanilla = VillageLayouts.byId(Fealty.id("vanilla"));
        Optional<VillageLayout> bwg = VillageLayouts.byId(Fealty.id("biomeswevegone"));
        Optional<VillageLayout> epic = VillageLayouts.byId(Fealty.id("epic_villages"));
        check(helper, vanilla.isPresent() && bwg.isPresent() && epic.isPresent(), "the built-in village layouts should load");
        check(helper, VillageLayouts.forStructure(plains) == vanilla.get(), "a plains village should use the vanilla layout without Epic Villages");
        check(helper, vanilla.get().elderRank(ResourceLocation.parse("minecraft:village/plains/houses/plains_big_house_1")).orElse(99) == 0,
                "the big house should be the vanilla elder's first choice");
        check(helper, vanilla.get().avoids(ResourceLocation.parse("minecraft:village/plains/town_centers/plains_meeting_point_1")),
                "a vanilla well is no home");
        check(helper, bwg.get().avoids(ResourceLocation.parse("biomeswevegone:village/skyris/streets/straight_01")),
                "streets are no home");
        check(helper, bwg.get().avoids(ResourceLocation.parse("biomeswevegone:village/salem/houses/animal_pen_1")),
                "animal pens are no home");
        int temple = bwg.get().elderRank(ResourceLocation.parse("biomeswevegone:village/salem/houses/temple_1")).orElse(99);
        int house = bwg.get().elderRank(ResourceLocation.parse("biomeswevegone:village/salem/houses/small_house_1")).orElse(99);
        check(helper, temple < house && house < 99, "a BWG temple should rank above a small house, and both should be listed");
        check(helper, epic.get().elderRank(ResourceLocation.parse("minecraft:village/plains/town_centers/plains_fountain_01")).orElse(99) == 0,
                "the Epic Villages town centre should be the elder's first choice");
        Optional<ResourceLocation> single = SitePlanner.template(StructurePoolElement.single("minecraft:village/plains/houses/plains_temple_3")
                .apply(StructureTemplatePool.Projection.RIGID));
        check(helper, single.isPresent() && single.get().getPath().endsWith("plains_temple_3"), "a jigsaw piece should name its template");
        Optional<ResourceLocation> list = SitePlanner.template(StructurePoolElement.list(List.of(
                StructurePoolElement.empty(), StructurePoolElement.single("minecraft:village/plains/houses/plains_library_1")))
                .apply(StructureTemplatePool.Projection.RIGID));
        check(helper, list.isPresent() && list.get().getPath().endsWith("plains_library_1"), "a list piece should name its first template");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void honouredCrimesOnlyCostRep(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Forgiving");
        try {
            IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(1, 1, 1));
            golem.setData(ModAttachments.FACTION, village.id());
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            RepManager.meet(player, village.id());
            RepManager.set(player, village.id(), 80, RepSources.COMMAND);
            GuardManager.alert(helper.getLevel(), player, village.id(), pos, Severity.SEVERE, Fealty.id("hit_child"), -20);
            check(helper, !GuardManager.isHostileTo(village.id(), player), "an Honored player's crime should only cost rep");
            RepManager.set(player, village.id(), 30, RepSources.COMMAND);
            GuardManager.alert(helper.getLevel(), player, village.id(), pos, Severity.MODERATE, Fealty.id("steal"), -15);
            check(helper, !GuardManager.isHostileTo(village.id(), player), "a first small crime below Honored gets a warning, not an attack");
            GuardManager.alert(helper.getLevel(), player, village.id(), pos, Severity.SEVERE, Fealty.id("hit_child"), -20);
            check(helper, GuardManager.isHostileTo(village.id(), player), "a severe crime below Honored turns the guards hostile");
            golem.discard();
        } finally {
            RepManager.data(player).aggroUntil().remove(village.id());
            RepManager.data(player).warnedAt().remove(village.id());
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stayingAwayFades(GameTestHelper helper) {
        check(helper, DailyTicker.neglectAfter(0) == 0 && DailyTicker.neglectAfter(1) == 0, "nothing fades in the first two days");
        check(helper, DailyTicker.neglectAfter(2) == 10, "two days away costs 10, got " + DailyTicker.neglectAfter(2));
        check(helper, DailyTicker.neglectAfter(3) == 15 && DailyTicker.neglectAfter(5) == 25, "then 5 more each day");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lordsGetTwoDaysOfGrace(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Fickleford");
        MinecraftServer server = helper.getLevel().getServer();
        try {
            RepManager.meet(player, village.id());
            RepManager.set(player, village.id(), 80, RepSources.COMMAND);
            LordshipManager.swear(player, village, true);
            long day = RepManager.day(server);
            RepManager.set(player, village.id(), 50, RepSources.COMMAND);
            check(helper, village.lord().isLord(player.getUUID()), "falling below Honored should not cost the lordship at once");
            check(helper, village.lord().lowSince() == day, "the grace should start the day the lord falls");
            LordshipManager.onNewDay(server, day + 1);
            check(helper, village.lord().isLord(player.getUUID()), "one day below Honored is still within the grace");
            RepManager.set(player, village.id(), 70, RepSources.COMMAND);
            check(helper, village.lord().lowSince() < 0, "being Honored again stops the clock");
            RepManager.set(player, village.id(), 50, RepSources.COMMAND);
            LordshipManager.onNewDay(server, village.lord().lowSince() + 2);
            check(helper, !village.lord().isLord(player.getUUID()), "two days below Honored and the village renounces its lord");
        } finally {
            LordshipManager.clearLord(server, village, null);
            forget(helper, village);
        }
        helper.succeed();
    }

    private static boolean warVeto;
    private static boolean banditVeto;
    private static boolean vetoListening;

    /** Test-only listeners that veto raids while a flag is set (the event bus has no easy way to remove a lambda). */
    private static void listenForVetoes() {
        if (!vetoListening) {
            vetoListening = true;
            NeoForge.EVENT_BUS.addListener((CampaignEvent.Declare e) -> {
                if (warVeto) {
                    e.cancel(Component.literal("truce"));
                }
            });
            NeoForge.EVENT_BUS.addListener((BanditRaidEvent.Start e) -> {
                if (banditVeto) {
                    e.setCanceled(true);
                }
            });
        }
    }

    @GameTest(template = "empty")
    public static void strongholdNamesAreStable(GameTestHelper helper) {
        BlockPos a = new BlockPos(1200, 70, -340);
        check(helper, StrongholdNames.stem(a).equals(StrongholdNames.stem(a)), "a stronghold's name should not change");
        check(helper, !StrongholdNames.stem(a).isEmpty(), "a stronghold should have a name");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void levyGrowsWithTheVillage(GameTestHelper helper) {
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Levyton");
        try {
            village.garrison().setPopulation(17);
            check(helper, Campaigns.levySize(village) == 2, "17 villagers should give 2 militia, got " + Campaigns.levySize(village));
            village.garrison().setPopulation(200);
            check(helper, Campaigns.levySize(village) == FealtyConfig.MAX_LEVY.get(), "the levy is capped");
        } finally {
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void lordsRaidAndRazeStrongholds(GameTestHelper helper) {
        listenForVetoes();
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Warbridge");
        MinecraftServer server = helper.getLevel().getServer();
        Strongholds strongholds = Strongholds.get(server);
        Strongholds.Entry target = strongholds.register(helper.getLevel(), village.center().offset(300, 0, 0), Stronghold.Kind.OUTPOST,
                ResourceLocation.withDefaultNamespace("pillager_outpost"), 24, StrongholdEvent.Discovered.How.SCOUTED);
        target.setTrait(StrongholdTrait.NONE);
        try {
            check(helper, Campaigns.raidCost(target.threat()) == 12, "an outpost with no trait costs the config's 12 emeralds");
            village.garrison().setPopulation(16);
            RepManager.meet(player, village.id());
            RepManager.set(player, village.id(), 80, RepSources.COMMAND);
            check(helper, Campaigns.declare(player, village, target, true).isPresent(), "only the lord may raise the warband");
            LordshipManager.swear(player, village, true);

            warVeto = true;
            player.getInventory().add(new ItemStack(Items.EMERALD, 12));
            check(helper, Campaigns.declare(player, village, target, true).isPresent(), "a vetoed raid should not begin");
            check(helper, player.getInventory().countItem(Items.EMERALD) == 12, "a vetoed raid costs nothing");
            warVeto = false;

            check(helper, Campaigns.declare(player, village, target, false).isPresent(), "the warband is raised in the village");
            Optional<Component> problem = Campaigns.declare(player, village, target, true);
            check(helper, problem.isEmpty(), "the raid should begin: " + problem.map(Component::getString).orElse(""));
            check(helper, player.getInventory().countItem(Items.EMERALD) == 0, "raising the warband costs its emeralds");
            check(helper, Campaigns.of(server, player.getUUID()).isPresent(), "the lord should be leading a raid");
            Optional<QuestContext> quest = QuestManager.context(player, Campaigns.GIVER, Campaigns.QUEST);
            check(helper, quest.isPresent(), "the raid should be a tracked quest");
            check(helper, quest.get().definition().objective().waypoint(quest.get()).map(w -> w.pos().equals(target.pos())).orElse(false),
                    "the quest should point at the stronghold");

            check(helper, Campaigns.forceWin(player), "the raid should be won");
            long day = RepManager.day(server);
            check(helper, target.isRazed(day), "a won raid razes the stronghold");
            check(helper, village.sites().atPeace(day), "a won raid brings the village peace");
            check(helper, Campaigns.of(server, player.getUUID()).isEmpty(), "the raid is over");
            check(helper, Campaigns.quietAt(helper.getLevel(), target.pos(), MobSpawnType.NATURAL), "no illagers spawn on razed ground");
            check(helper, !Campaigns.quietAt(helper.getLevel(), target.pos().offset(400, 0, 0), MobSpawnType.NATURAL),
                    "illagers still spawn elsewhere");
            check(helper, Campaigns.declare(player, village, target, true).isPresent(), "a razed stronghold cannot be raided again at once");
        } finally {
            warVeto = false;
            Campaigns.callOff(player);
            QuestManager.context(player, Campaigns.GIVER, Campaigns.QUEST).ifPresent(ctx -> QuestManager.failQuietly(ctx,
                    com.selluastar.fealty.api.event.RepQuestEvent.Reason.ABANDONED));
            strongholds.remove(target);
            LordshipManager.clearLord(server, village, null);
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void strongholdKindsMatchTheirStructures(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String kind : List.of("scout_camp", "camp", "outpost", "fort", "castle")) {
            check(helper, StrongholdKinds.byId(Fealty.id(kind)).isPresent(), "the " + kind + " kind should load");
        }
        check(helper, StrongholdKinds.idFor(server, Fealty.id("pillager_scout_camp")).equals(Fealty.id("scout_camp")), "scout camps");
        check(helper, StrongholdKinds.idFor(server, Fealty.id("pillager_camp")).equals(Fealty.id("camp")), "camps");
        check(helper, StrongholdKinds.idFor(server, Fealty.id("pillager_fort")).equals(Fealty.id("fort")), "forts");
        check(helper, StrongholdKinds.idFor(server, Fealty.id("pillager_castle")).equals(Fealty.id("castle")), "castles");
        check(helper, StrongholdKinds.idFor(server, ResourceLocation.withDefaultNamespace("pillager_outpost")).equals(Fealty.id("outpost")),
                "vanilla outposts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void traitsAddThreat(GameTestHelper helper) {
        Strongholds strongholds = Strongholds.get(helper.getLevel().getServer());
        Strongholds.Entry scouts = stronghold(helper, strongholds, 0, "pillager_scout_camp");
        Strongholds.Entry castle = stronghold(helper, strongholds, 1, "pillager_castle");
        try {
            scouts.setTrait(StrongholdTrait.NONE);
            check(helper, scouts.threat() == 1, "a plain scout camp is one skull, got " + scouts.threat());
            scouts.setTrait(StrongholdTrait.VETERANS);
            check(helper, scouts.threat() == 2, "veterans make a scout camp two skulls, got " + scouts.threat());
            castle.setTrait(StrongholdTrait.NONE);
            check(helper, castle.threat() == 4, "a plain castle is four skulls, got " + castle.threat());
            castle.setTrait(StrongholdTrait.EVOKER);
            check(helper, castle.threat() == 5, "a castle with a trait is five skulls, got " + castle.threat());
            check(helper, castle.view(0).threat() == 5 && castle.view(0).tier().equals(Fealty.id("castle")),
                    "the API record carries the threat and tier");
        } finally {
            strongholds.remove(scouts);
            strongholds.remove(castle);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void raidsScaleWithThreat(GameTestHelper helper) {
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Skullford");
        try {
            int base = FealtyConfig.RAID_COST_EMERALDS.get();
            check(helper, Campaigns.raidCost(2) == base, "an ordinary camp costs the config's emeralds");
            check(helper, Campaigns.raidCost(1) < base && Campaigns.raidCost(4) > base && Campaigns.raidCost(5) > Campaigns.raidCost(4),
                    "a raid costs more the more skulls");
            int warband = FealtyConfig.WARBAND_SIZE.get();
            check(helper, Campaigns.warbandSize(1) == warband && Campaigns.warbandSize(3) == warband + 2 && Campaigns.warbandSize(5) == warband + 4,
                    "more guards march against a harder stronghold");
            village.garrison().setPopulation(400);
            check(helper, Campaigns.levySize(village, 4) > Campaigns.levySize(village, 1), "more militia are called up against a castle");
        } finally {
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void castlesAreHeldInForce(GameTestHelper helper) {
        BlockPos pos = new BlockPos(52000, 70, 52000);
        Map<EntityType<?>, Integer> castle = StrongholdKinds.get(Fealty.id("castle")).plan(StrongholdTrait.NONE, pos);
        check(helper, castle.getOrDefault(EntityType.EVOKER, 0) >= 2, "a castle keeps evokers");
        check(helper, castle.getOrDefault(EntityType.RAVAGER, 0) >= 1, "a castle keeps a ravager");
        StrongholdKind scouts = StrongholdKinds.get(Fealty.id("scout_camp"));
        check(helper, !scouts.plan(StrongholdTrait.NONE, pos).containsKey(EntityType.EVOKER), "a plain scout camp has no evoker");
        check(helper, scouts.plan(StrongholdTrait.EVOKER, pos).getOrDefault(EntityType.EVOKER, 0) == 1, "an evoker trait adds one");
        check(helper, scouts.plan(StrongholdTrait.BEASTS, pos).getOrDefault(EntityType.RAVAGER, 0) == 1, "war beasts add a ravager");
        check(helper, castle.equals(StrongholdKinds.get(Fealty.id("castle")).plan(StrongholdTrait.NONE, pos)),
                "a stronghold musters the same garrison every time");
        check(helper, StrongholdKinds.get(Fealty.id("castle")).defenders(StrongholdTrait.NONE, pos)
                > scouts.defenders(StrongholdTrait.NONE, pos), "a castle has more defenders than a scout camp");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spoilsScaleWithThreat(GameTestHelper helper) {
        Strongholds strongholds = Strongholds.get(helper.getLevel().getServer());
        Strongholds.Entry scouts = stronghold(helper, strongholds, 2, "pillager_scout_camp");
        Strongholds.Entry castle = stronghold(helper, strongholds, 3, "pillager_castle");
        try {
            scouts.setTrait(StrongholdTrait.NONE);
            castle.setTrait(StrongholdTrait.NONE);
            check(helper, castle.spoils() > scouts.spoils(), "razing a castle wins more tribute than a scout camp");
            check(helper, castle.peaceDays() > scouts.peaceDays(), "razing a castle wins a longer peace");
            check(helper, castle.menaceRange() > scouts.menaceRange(), "a castle menaces villages further off");
            int plain = scouts.spoils();
            scouts.setTrait(StrongholdTrait.BEASTS);
            check(helper, scouts.spoils() == plain + 1, "a trait adds to the spoils");
        } finally {
            strongholds.remove(scouts);
            strongholds.remove(castle);
        }
        helper.succeed();
    }

    /** A stronghold far from everything, built as a Fealty structure. */
    private static Strongholds.Entry stronghold(GameTestHelper helper, Strongholds strongholds, int slot, String structure) {
        return strongholds.register(helper.getLevel(), new BlockPos(60000 + slot * 500, 70, 60000), Stronghold.Kind.CAMP, Fealty.id(structure),
                24, StrongholdEvent.Discovered.How.SCOUTED);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void freedCaptivesGoHome(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Homecoming");
        Villager captive = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Captives.makeCaptive(captive, helper.absolutePos(new BlockPos(1, 1, 1)), village.id());
        check(helper, Captives.isCaptive(captive), "the villager should be a captive");
        RepManager.meet(player, village.id());
        int before = RepManager.getRep(player, village.id());
        Captives.free(helper.getLevel(), captive, player);
        check(helper, !Captives.isCaptive(captive), "a freed captive is no longer held");
        check(helper, RepManager.getRep(player, village.id()) > before, "freeing a captive wins their village's thanks");
        helper.runAfterDelay(110, () -> {
            check(helper, village.sites().incomingVillagers() == 1, "the freed villager should be on their way home");
            forget(helper, village);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void banditRaidsCanBeVetoed(GameTestHelper helper) {
        listenForVetoes();
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Sparedale");
        try {
            banditVeto = true;
            check(helper, BanditRaids.start(helper.getLevel(), village, null) == 0, "a vetoed bandit raid should not start");
        } finally {
            banditVeto = false;
            forget(helper, village);
        }
        helper.succeed();
    }

    // ---- Stories: rumours, single-giver chains, conversations, contributors and flags (API 1.3.0) ----

    private static int chainStarts;
    private static int chainAdvances;
    private static int chainCompletes;
    private static int rumoursHeard;
    private static boolean contributing;
    private static int waves;
    private static boolean storyListening;

    /** Test-only listeners for the story events, switched by flags (the event bus has no easy way to remove a lambda). */
    private static void listenForStories() {
        if (!storyListening) {
            storyListening = true;
            NeoForge.EVENT_BUS.addListener((ChainStageEvent.Start e) -> chainStarts++);
            NeoForge.EVENT_BUS.addListener((ChainStageEvent.Advance e) -> chainAdvances++);
            NeoForge.EVENT_BUS.addListener((ChainStageEvent.Complete e) -> chainCompletes++);
            NeoForge.EVENT_BUS.addListener((RumourEvent.Heard e) -> rumoursHeard++);
            NeoForge.EVENT_BUS.addListener((DialogueBuildEvent e) -> {
                if (contributing) {
                    e.addLine(Component.literal("The weather turns."));
                    e.addOption("example:wave", Component.literal("Wave"), (player, npc, option) -> {
                        waves++;
                        return DialogueReply.say(Component.literal("They wave back."));
                    });
                }
            });
        }
    }

    /** Read a data file the way a data pack would. */
    private static <T> T parse(GameTestHelper helper, Codec<T> codec, String json) {
        return codec.parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), JsonParser.parseString(json))
                .getOrThrow(IllegalStateException::new);
    }

    /** Stand the player next to someone, so the dialogue box accepts their replies. */
    private static void standBy(ServerPlayer player, Villager villager) {
        player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ());
    }

    @GameTest(template = "empty")
    public static void rumoursRollWithPity(GameTestHelper helper) {
        listenForStories();
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Whisperwell");
        Villager villager = villager(helper, village.id(), new BlockPos(1, 1, 1));
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "test/gossip");
        ResourceLocation heardFlag = ResourceLocation.fromNamespaceAndPath("example", "test/heard_gossip");
        RumourService.define(id, parse(helper, RumourDefinition.CODEC, """
                {"told_by": "villagers", "weight": 5, "lines": ["example.rumour.gossip"], "grants": [{"set_flag": "example:test/heard_gossip"}]}
                """));
        try {
            double base = FealtyConfig.RUMOUR_CHANCE.get();
            double step = FealtyConfig.RUMOUR_PITY_STEP.get();
            double cap = FealtyConfig.RUMOUR_PITY_CAP.get();
            int guarantee = FealtyConfig.RUMOUR_GUARANTEE_AFTER.get();
            check(helper, RumourService.chance(0) == base, "the first roll is the plain chance");
            check(helper, Math.abs(RumourService.chance(3) - Math.min(cap, base + 3 * step)) < 1e-9, "each miss adds the pity step");
            check(helper, RumourService.chance(guarantee - 1) <= Math.max(base, cap), "pity stops at its cap");
            check(helper, RumourService.chance(guarantee) == 1.0, "after enough misses a rumour is sure");

            RepManager.meet(player, village.id());
            RepManager.set(player, village.id(), TierManager.neutral().min(), RepSources.COMMAND);
            VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
            PlayerFlags flags = PlayerFlags.of(player);
            check(helper, RumourService.converse(player, villager, 0.9999).isEmpty() && flags.rumourMisses() == 1, "a roll that misses counts");
            check(helper, RumourService.converse(player, villager, 0.0).isEmpty() && flags.rumourMisses() == 1,
                    "a villager rolls once a day for each player");
            for (int i = 1; i < guarantee; i++) {
                memory.lastRumourRoll = -1;
                RumourService.converse(player, villager, 0.9999);
            }
            check(helper, flags.rumourMisses() == guarantee, "every missed day adds up; got " + flags.rumourMisses());
            memory.lastRumourRoll = -1;
            int heard = rumoursHeard;
            List<Component> lines = RumourService.converse(player, villager, 0.9999);
            check(helper, lines.size() == 1 && lines.getFirst().getContents() instanceof TranslatableContents key
                    && key.getKey().equals("example.rumour.gossip"), "after " + guarantee + " misses the rumour is told; got " + lines);
            check(helper, rumoursHeard == heard + 1, "RumourEvent.Heard fires");
            check(helper, flags.rumourMisses() == 0 && flags.hasHeard(id), "the pity resets and the rumour is remembered");
            check(helper, PlayerFlags.get(player, heardFlag) == 1, "its grants apply");
            memory.lastRumourRoll = -1;
            check(helper, RumourService.converse(player, villager, 0.0).isEmpty() && flags.rumourMisses() == 0,
                    "a rumour is told once; with nothing left to tell, a quiet day is no miss");
        } finally {
            RumourService.define(id, null);
            villager.discard();
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void singleGiverChainEndToEnd(GameTestHelper helper) {
        listenForStories();
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Talesend");
        Villager teller = villager(helper, village.id(), new BlockPos(1, 1, 1));
        Villager other = villager(helper, village.id(), new BlockPos(2, 1, 2));
        ResourceLocation chain = ResourceLocation.fromNamespaceAndPath("example", "test/tale");
        ResourceLocation rumour = ResourceLocation.fromNamespaceAndPath("example", "test/tale");
        ResourceLocation meeting = ResourceLocation.fromNamespaceAndPath("example", "test/stranger_meeting");
        ResourceLocation met = ResourceLocation.fromNamespaceAndPath("example", "test/met");
        List<ResourceLocation> quests = List.of(ResourceLocation.fromNamespaceAndPath("example", "test/fetch_dirt"),
                ResourceLocation.fromNamespaceAndPath("example", "test/fetch_sand"), ResourceLocation.fromNamespaceAndPath("example", "test/fetch_clay"));
        List<ItemStack> wanted = List.of(new ItemStack(Items.DIRT), new ItemStack(Items.SAND), new ItemStack(Items.CLAY_BALL));
        for (int i = 0; i < quests.size(); i++) {
            FealtyDataManager.defineQuest(quests.get(i), parse(helper, RepQuestDefinition.CODEC, """
                    {"title": {"text": "Errand %d"}, "pool": "fealty:none",
                     "objective": {"type": "fealty:fetch", "items": [{"ingredient": {"item": "%s"}, "count": 1}]}}
                    """.formatted(i, BuiltInRegistries.ITEM.getKey(wanted.get(i).getItem()))));
        }
        FealtyDataManager.defineChain(chain, parse(helper, QuestChainDefinition.CODEC, """
                {"kind": "story", "start_tier": "fealty:neutral", "repeatable": false,
                 "giver": "single", "giver_role": "stranger", "giver_title": "example.role.stranger",
                 "steps": [
                   {"role": "first", "quest": "example:test/fetch_dirt", "unlock": {"flag": "example:test/met"}, "unlock_hint": "example.hint.letter"},
                   {"role": "second", "quest": "example:test/fetch_sand"},
                   {"role": "third", "quest": "example:test/fetch_clay", "rewards": [{"id": "minecraft:emerald", "count": 2}]}
                 ],
                 "final_reward": {"id": "minecraft:diamond", "count": 3}}
                """));
        RumourService.define(rumour, parse(helper, RumourDefinition.CODEC, """
                {"chain": "example:test/tale", "stage": 1, "min_tier": "fealty:neutral", "weight": 5, "told_by": "villagers",
                 "lines": ["example.rumour.stranger"], "grants": [{"give_item": "minecraft:paper"}]}
                """));
        Conversations.define(meeting, parse(helper, Conversation.CODEC, """
                {"id": "example:test/stranger_meeting", "speaker": "example:stranger", "if": {"not": {"flag": "example:test/met"}},
                 "start": "greet",
                 "nodes": {
                   "greet": {"text": "example.dialogue.greet", "replies": [
                     {"label": "example.reply.show_letter", "if": {"has_item": "minecraft:paper"}, "hint": "example.hint.need_letter", "goto": "letter"},
                     {"label": "example.reply.bye", "end": true}]},
                   "letter": {"text": "example.dialogue.letter", "effects": [{"take_item": "minecraft:paper"}, {"set_flag": "example:test/met"}],
                     "replies": [{"label": "example.reply.ready", "end": true}]}
                 }}
                """));
        int starts = chainStarts;
        int advances = chainAdvances;
        int completes = chainCompletes;
        try {
            RepManager.meet(player, village.id());
            RepManager.set(player, village.id(), TierManager.neutral().min(), RepSources.COMMAND);

            // A rumour starts the tale, gives the letter and names the stranger.
            List<Component> told = RumourService.converse(player, teller, 0.0);
            check(helper, !told.isEmpty(), "the villager tells the tale's rumour");
            check(helper, ChainManager.stage(player, chain) == ChainManager.STAGE_STEPS && chainStarts == starts + 1, "the rumour starts the chain");
            check(helper, player.getInventory().countItem(Items.PAPER) == 1, "the rumour's grant hands over the letter");
            Villager giver = ChainManager.holdsRole(teller, chain, "stranger") ? teller : other;
            check(helper, ChainManager.holdsRole(giver, chain, "stranger"), "one villager becomes the stranger");
            check(helper, FealtyApi.get().hasRole(giver, chain, "stranger"), "the API sees the role too");
            ResourceLocation first = ChainManager.stepKey(chain, 0);
            ChainManager.NAMED_VILLAGER.handleAction(player, giver, QuestActionPayload.ACCEPT, "");
            check(helper, QuestManager.contexts(player, first).isEmpty(), "the first step waits for its unlock");

            // The conversation: show the letter.
            standBy(player, giver);
            check(helper, DialogueService.open(player, giver), "the stranger has something to say");
            check(helper, DialogueService.conversation(player).equals(Optional.of(meeting + "#greet")),
                    "the first meeting takes over; got " + DialogueService.conversation(player));
            check(helper, DialogueService.options(player).contains("c:0"), "with the letter, it can be shown");
            DialogueService.choose(player, giver.getId(), "c:0");
            check(helper, DialogueService.conversation(player).equals(Optional.of(meeting + "#letter")), "showing it goes to the next node");
            check(helper, player.getInventory().countItem(Items.PAPER) == 0 && FealtyApi.get().hasFlag(player, met),
                    "the node's effects take the letter and set the flag");
            DialogueService.choose(player, giver.getId(), "c:0");
            check(helper, DialogueService.conversation(player).isEmpty() && DialogueService.options(player).contains(DialogueService.BYE),
                    "the conversation ends in the stranger's usual dialogue");
            DialogueService.end(player);

            // Three steps, in order, from the one giver.
            for (int i = 0; i < quests.size(); i++) {
                ResourceLocation key = ChainManager.stepKey(chain, i);
                ChainManager.NAMED_VILLAGER.handleAction(player, giver, QuestActionPayload.ACCEPT, "");
                check(helper, QuestManager.contexts(player, key).size() == 1, "the stranger gives step " + i);
                player.getInventory().add(wanted.get(i).copy());
                ChainManager.NAMED_VILLAGER.handleAction(player, giver, QuestActionPayload.TURN_IN, "");
                check(helper, QuestManager.contexts(player, key).isEmpty() && ChainManager.stepsDone(player, chain) == i + 1,
                        "step " + i + " is handed in");
            }
            check(helper, chainAdvances >= advances + 3, "every step fires ChainStageEvent.Advance");
            check(helper, ChainManager.stage(player, chain) == ChainManager.STAGE_DONE && chainCompletes == completes + 1, "the tale is complete");
            check(helper, player.getInventory().countItem(Items.DIAMOND) == 3 && player.getInventory().countItem(Items.EMERALD) == 2,
                    "the last step's rewards and the final reward are given");
            check(helper, !ChainManager.hasRole(giver), "the stranger goes back to being a villager");
            check(helper, !RumourService.eligible(player, teller).stream().anyMatch(e -> e.getKey().equals(rumour)),
                    "a finished tale that does not repeat has no more rumours");
        } finally {
            DialogueService.end(player);
            Conversations.define(meeting, null);
            RumourService.define(rumour, null);
            FealtyDataManager.defineChain(chain, null);
            quests.forEach(id -> FealtyDataManager.defineQuest(id, null));
            RepManager.data(player).chains().remove(chain);
            teller.discard();
            other.discard();
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void contributorReplies(GameTestHelper helper) {
        listenForStories();
        ServerPlayer player = player(helper);
        VillageRecord village = testVillage(helper, BlockPos.ZERO, "Wavebury");
        Villager villager = villager(helper, village.id(), new BlockPos(1, 1, 1));
        contributing = true;
        try {
            boolean refused = false;
            try {
                new DialogueBuildEvent(player, villager, false).addOption("q:sneaky", Component.literal("Sneaky"), (p, n, o) -> DialogueReply.close());
            } catch (IllegalArgumentException e) {
                refused = true;
            }
            check(helper, refused, "reply ids must be <modid>:<name> and stay clear of Fealty's own");
            standBy(player, villager);
            check(helper, DialogueService.open(player, villager), "the villager talks");
            check(helper, DialogueService.options(player).contains("example:wave"), "another mod's reply is offered");
            int before = waves;
            DialogueService.choose(player, villager.getId(), "example:wave");
            check(helper, waves == before + 1, "picking it calls the listener that added it");
            check(helper, DialogueService.options(player).contains("example:wave"), "the box stays open with the answer");
        } finally {
            contributing = false;
            DialogueService.end(player);
            villager.discard();
            forget(helper, village);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flagsSurviveRelogAndDeath(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ResourceLocation flag = ResourceLocation.fromNamespaceAndPath("example", "test/remembered");
        FealtyApi.get().setFlag(player, flag, 7);
        check(helper, FealtyApi.get().getFlag(player, flag) == 7 && FealtyApi.get().hasFlag(player, flag), "the flag is set");

        // Logging out saves the player; logging back in loads them into a new player object.
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        ServerPlayer relogged = new ServerPlayer(player.server, helper.getLevel(), player.getGameProfile(), ClientInformation.createDefault());
        relogged.load(saved);
        check(helper, FealtyApi.get().getFlag(relogged, flag) == 7, "the flag survives relogging");

        // Dying makes a new player too, copying what is kept on death.
        ServerPlayer respawned = new ServerPlayer(player.server, helper.getLevel(), player.getGameProfile(), ClientInformation.createDefault());
        respawned.restoreFrom(relogged, false);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, relogged, true));
        check(helper, FealtyApi.get().getFlag(respawned, flag) == 7, "the flag survives death");

        FealtyApi.get().clearFlag(respawned, flag);
        check(helper, !FealtyApi.get().hasFlag(respawned, flag), "the flag clears");
        helper.succeed();
    }
}
