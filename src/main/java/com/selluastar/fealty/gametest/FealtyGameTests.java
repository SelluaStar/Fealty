package com.selluastar.fealty.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.RepTiers;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.crime.CrimeHandlers;
import com.selluastar.fealty.crime.CrimeService;
import com.selluastar.fealty.crime.Fines;
import com.selluastar.fealty.crime.Gossip;
import com.selluastar.fealty.crime.Locks;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.dialogue.DialogueNode;
import com.selluastar.fealty.dialogue.DialogueService;
import com.selluastar.fealty.guard.Garrison;
import com.selluastar.fealty.lordship.LordshipManager;
import com.selluastar.fealty.mail.MailService;
import com.selluastar.fealty.outlaw.BanditCamps;
import com.selluastar.fealty.outlaw.ThievesGuild;
import com.selluastar.fealty.quest.QuestLog;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.quest.RepQuestDefinition;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyCalendar;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.trade.TradeHooks;
import com.selluastar.fealty.trade.VillagerInteractions;
import com.selluastar.fealty.trade.VillagerMemory;
import com.selluastar.fealty.village.SitePlanner;
import com.selluastar.fealty.village.StructureMatcher;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageLayout;
import com.selluastar.fealty.village.VillageLayouts;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import io.netty.channel.embedded.EmbeddedChannel;

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
}
