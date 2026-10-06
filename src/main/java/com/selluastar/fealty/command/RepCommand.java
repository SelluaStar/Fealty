package com.selluastar.fealty.command;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.lordship.LordshipManager;
import com.selluastar.fealty.outlaw.BanditCamps;
import com.selluastar.fealty.outlaw.BanditRaids;
import com.selluastar.fealty.outlaw.HeatManager;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.PlayerRepData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.ElderManager;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /rep} commands for pack makers and testing. Faction arguments accept a faction id, {@code here} for the
 * village you stand in, or {@code fealty:renown}.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class RepCommand {
    private static final ResourceLocation HERE = ResourceLocation.withDefaultNamespace("here");
    private static final SimpleCommandExceptionType NO_VILLAGE = new SimpleCommandExceptionType(Component.translatable("fealty.command.no_village"));
    private static final SimpleCommandExceptionType ALREADY_VILLAGE = new SimpleCommandExceptionType(Component.translatable("fealty.command.already_village"));
    private static final SimpleCommandExceptionType UNKNOWN_VILLAGE = new SimpleCommandExceptionType(Component.translatable("fealty.command.unknown_village"));

    private RepCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rep").requires(s -> s.hasPermission(2))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("faction", ResourceLocationArgument.id()).suggests(RepCommand::suggestFactions)
                                        .executes(RepCommand::get))))
                .then(Commands.literal("add")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("faction", ResourceLocationArgument.id()).suggests(RepCommand::suggestFactions)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(ctx -> add(ctx, false))))))
                .then(Commands.literal("set")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("faction", ResourceLocationArgument.id()).suggests(RepCommand::suggestFactions)
                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                .executes(ctx -> add(ctx, true))))))
                .then(Commands.literal("list")
                        .then(Commands.argument("player", EntityArgument.player()).executes(RepCommand::list)))
                .then(Commands.literal("heat")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("faction", ResourceLocationArgument.id()).suggests(RepCommand::suggestFactions)
                                        .executes(RepCommand::getHeat)
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(RepCommand::setHeat)))))
                .then(Commands.literal("village")
                        .then(Commands.literal("info").executes(RepCommand::villageInfo))
                        .then(Commands.literal("list").executes(RepCommand::villageList))
                        .then(Commands.literal("scan").executes(RepCommand::villageScan))
                        .then(Commands.literal("create")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(8, 256))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(RepCommand::villageCreate))))
                        .then(Commands.literal("rename")
                                .then(Commands.argument("village", ResourceLocationArgument.id()).suggests(RepCommand::suggestVillages)
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(RepCommand::villageRename))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("village", ResourceLocationArgument.id()).suggests(RepCommand::suggestVillages)
                                        .executes(RepCommand::villageDelete)))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("village", ResourceLocationArgument.id()).suggests(RepCommand::suggestVillages)
                                        .executes(RepCommand::restore)))
                        .then(Commands.literal("raid")
                                .then(Commands.argument("village", ResourceLocationArgument.id()).suggests(RepCommand::suggestVillages)
                                        .executes(RepCommand::raid)))
                        .then(Commands.literal("lord")
                                .then(Commands.argument("village", ResourceLocationArgument.id()).suggests(RepCommand::suggestVillages)
                                        .then(Commands.literal("clear").executes(RepCommand::clearLord))
                                        .then(Commands.literal("tax")
                                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 4)).executes(RepCommand::lordTax)))
                                        .then(Commands.literal("treasury")
                                                .then(Commands.argument("rolls", IntegerArgumentType.integer(0, 10000)).executes(RepCommand::lordTreasury)))
                                        .then(Commands.argument("player", EntityArgument.player()).executes(RepCommand::setLord)))))
                .then(Commands.literal("quest")
                        .then(Commands.literal("complete")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("giver", ResourceLocationArgument.id()).suggests(RepCommand::suggestFactions)
                                                .executes(ctx -> quest(ctx, true)))))
                        .then(Commands.literal("abandon")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("giver", ResourceLocationArgument.id()).suggests(RepCommand::suggestFactions)
                                                .executes(ctx -> quest(ctx, false)))))));
    }

    private static CompletableFuture<Suggestions> suggestFactions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        builder.suggest("here");
        builder.suggest(Factions.RENOWN.toString());
        FealtyDataManager.factions().forEach((id, def) -> builder.suggest(id.toString()));
        FealtyWorldData.get(ctx.getSource().getServer()).villages().forEach(v -> builder.suggest(v.id().toString()));
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestVillages(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        builder.suggest("here");
        return SharedSuggestionProvider.suggestResource(
                FealtyWorldData.get(ctx.getSource().getServer()).villages().stream().map(VillageRecord::id), builder);
    }

    private static ResourceLocation faction(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, arg);
        if (HERE.equals(id)) {
            CommandSourceStack source = ctx.getSource();
            return VillageResolver.villageAt(source.getLevel(), BlockPos.containing(source.getPosition()))
                    .map(VillageRecord::id).orElseThrow(NO_VILLAGE::create);
        }
        return id;
    }

    private static VillageRecord village(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = faction(ctx, "village");
        return FealtyWorldData.get(ctx.getSource().getServer()).village(id).orElseThrow(UNKNOWN_VILLAGE::create);
    }

    private static Component describe(ServerPlayer player, ResourceLocation faction) {
        int rep = RepManager.getRep(player, faction);
        RepTier tier = RepManager.tierOf(rep);
        return Component.translatable("fealty.command.standing", player.getDisplayName(),
                Factions.displayName(player.server, faction), rep, tier.displayName().copy().withColor(tier.color()));
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation faction = faction(ctx, "faction");
        ctx.getSource().sendSuccess(() -> describe(player, faction), false);
        return RepManager.getRep(player, faction);
    }

    private static int add(CommandContext<CommandSourceStack> ctx, boolean set) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        ResourceLocation faction = faction(ctx, "faction");
        int value = IntegerArgumentType.getInteger(ctx, set ? "value" : "amount");
        for (ServerPlayer player : players) {
            if (set) {
                RepManager.set(player, faction, value, RepSources.COMMAND);
            } else {
                RepManager.change(player, faction, value, RepSources.COMMAND, true);
            }
            ctx.getSource().sendSuccess(() -> describe(player, faction), true);
        }
        return players.size();
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        PlayerRepData data = RepManager.data(player);
        RepTier renownTier = RepManager.renownTier(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.renown", player.getDisplayName(),
                RepManager.renown(player), renownTier.displayName().copy().withColor(renownTier.color())), false);
        for (Map.Entry<ResourceLocation, Integer> entry : data.rep().entrySet()) {
            ResourceLocation faction = entry.getKey();
            ctx.getSource().sendSuccess(() -> Component.literal(" - ").append(describe(player, faction))
                    .append(Component.literal(" [" + faction + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return data.rep().size();
    }

    private static int getHeat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation faction = faction(ctx, "faction");
        int heat = HeatManager.heat(player, faction);
        int level = HeatManager.wantedLevel(player, faction);
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.heat", player.getDisplayName(),
                Factions.displayName(player.server, faction), heat, level), false);
        return heat;
    }

    private static int setHeat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation faction = faction(ctx, "faction");
        HeatManager.setHeat(player, faction, IntegerArgumentType.getInteger(ctx, "value"));
        return getHeat(ctx);
    }

    private static int villageInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        VillageRecord record = VillageResolver.villageAt(source.getLevel(), BlockPos.containing(source.getPosition()))
                .orElseThrow(NO_VILLAGE::create);
        sendInfo(source, record);
        return 1;
    }

    private static void sendInfo(CommandSourceStack source, VillageRecord r) {
        String lord = r.lord().uuid() == null ? "-" : r.lord().name();
        source.sendSuccess(() -> Component.translatable("fealty.command.village_info", r.name(), r.id().toString(),
                r.center().toShortString(), r.structure() == null ? "bell" : r.structure().toString(),
                r.elder().state().getSerializedName(), lord, r.lord().taxLevel()), false);
    }

    private static int villageList(CommandContext<CommandSourceStack> ctx) {
        Collection<VillageRecord> villages = FealtyWorldData.get(ctx.getSource().getServer()).villages();
        for (VillageRecord record : villages) {
            sendInfo(ctx.getSource(), record);
        }
        return villages.size();
    }

    private static int villageScan(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        BlockPos pos = BlockPos.containing(source.getPosition());
        source.sendSuccess(() -> Component.translatable("fealty.command.scan_header", pos.toShortString()).withStyle(ChatFormatting.GOLD), false);
        java.util.List<String> lines = VillageResolver.scan(source.getLevel(), pos);
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(" - " + line), false);
        }
        Optional<VillageRecord> here = VillageResolver.villageAt(source.getLevel(), pos);
        source.sendSuccess(() -> here.map(r -> Component.translatable("fealty.command.scan_village", r.name(), r.id().toString(),
                Component.translatable(r.hasElder() ? "fealty.command.yes" : "fealty.command.no")))
                .orElse(Component.translatable("fealty.command.scan_none")), false);
        return lines.size();
    }

    private static int villageCreate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        BlockPos pos = BlockPos.containing(source.getPosition());
        if (VillageResolver.villageAt(source.getLevel(), pos).isPresent()) {
            throw ALREADY_VILLAGE.create();
        }
        String name = StringArgumentType.getString(ctx, "name");
        VillageRecord record = VillageResolver.createManual(source.getLevel(), pos, name, IntegerArgumentType.getInteger(ctx, "radius"));
        source.sendSuccess(() -> Component.translatable("fealty.command.village_created", record.name(), record.id().toString()), true);
        return 1;
    }

    private static int villageRename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageRecord record = village(ctx);
        String name = StringArgumentType.getString(ctx, "name");
        record.setName(name);
        FealtyWorldData.get(ctx.getSource().getServer()).setDirty();
        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(com.selluastar.fealty.network.FealtyNetwork::syncAll);
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.village_renamed", name), true);
        return 1;
    }

    private static int villageDelete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageRecord record = village(ctx);
        FealtyWorldData.get(ctx.getSource().getServer()).removeVillage(record.id());
        VillageResolver.clearCache();
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.village_deleted", record.name()), true);
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageRecord record = village(ctx);
        boolean ok = ElderManager.restore(ctx.getSource().getServer(), record, Optional.empty());
        ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "fealty.command.restored" : "fealty.command.restore_pending", record.name()), true);
        return ok ? 1 : 0;
    }

    private static int setLord(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageRecord record = village(ctx);
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        LordshipManager.swear(player, record, true);
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.lord_set", player.getDisplayName(), record.name()), true);
        return 1;
    }

    private static int clearLord(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageRecord record = village(ctx);
        LordshipManager.clearLord(ctx.getSource().getServer(), record, null);
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.lord_cleared", record.name()), true);
        return 1;
    }

    /** Send a bandit raid against a village now, from the nearest manned camp if there is one in reach. */
    private static int raid(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageRecord record = village(ctx);
        ServerLevel level = ctx.getSource().getServer().getLevel(record.dimension());
        int raiders = 0;
        if (level != null) {
            BlockPos camp = BanditCamps.get(ctx.getSource().getServer()).nearestActive(level, record.center(), FealtyConfig.RAID_RANGE.get()).orElse(null);
            raiders = BanditRaids.start(level, record, camp);
        }
        int count = raiders;
        ctx.getSource().sendSuccess(() -> Component.translatable(count > 0 ? "fealty.command.raid_started" : "fealty.command.raid_failed",
                record.name(), count), true);
        return count;
    }

    private static int lordTax(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageRecord record = village(ctx);
        record.lord().setTaxLevel(IntegerArgumentType.getInteger(ctx, "level"));
        FealtyWorldData.get(ctx.getSource().getServer()).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.lord.tax_set", record.name(),
                LordshipManager.taxName(record.lord().taxLevel())), true);
        return 1;
    }

    private static int lordTreasury(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageRecord record = village(ctx);
        record.lord().setTreasury(IntegerArgumentType.getInteger(ctx, "rolls"));
        FealtyWorldData.get(ctx.getSource().getServer()).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.treasury_set", record.name(), (int) record.lord().treasury()), true);
        return 1;
    }

    private static int quest(CommandContext<CommandSourceStack> ctx, boolean complete) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation giver = faction(ctx, "giver");
        boolean done = complete ? QuestManager.forceComplete(player, giver) : QuestManager.abandon(player, giver, false);
        ctx.getSource().sendSuccess(() -> Component.translatable(done ? "fealty.command.quest_done" : "fealty.command.no_quest"), true);
        return done ? 1 : 0;
    }
}
