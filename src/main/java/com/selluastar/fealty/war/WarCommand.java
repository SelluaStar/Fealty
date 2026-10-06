package com.selluastar.fealty.war;

import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /rep war ...}, for pack makers testing raids: list strongholds, send scouts, declare against the nearest
 * stronghold, win or call off a raid, and set off a pillager menace raid.
 */
public final class WarCommand {
    private static final SimpleCommandExceptionType UNKNOWN_VILLAGE =
            new SimpleCommandExceptionType(Component.translatable("fealty.command.unknown_village"));

    private WarCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> villages) {
        return Commands.literal("war")
                .then(Commands.literal("list").executes(WarCommand::list))
                .then(Commands.literal("scout").then(Commands.argument("village", ResourceLocationArgument.id()).suggests(villages)
                        .executes(WarCommand::scout)))
                .then(Commands.literal("declare").then(Commands.argument("village", ResourceLocationArgument.id()).suggests(villages)
                        .executes(WarCommand::declare)))
                .then(Commands.literal("win").executes(ctx -> {
                    boolean won = Campaigns.forceWin(ctx.getSource().getPlayerOrException());
                    ctx.getSource().sendSuccess(() -> Component.translatable(won ? "fealty.command.war_won" : "fealty.command.war_none"), true);
                    return won ? 1 : 0;
                }))
                .then(Commands.literal("cancel").executes(ctx -> {
                    boolean ended = Campaigns.callOff(ctx.getSource().getPlayerOrException());
                    ctx.getSource().sendSuccess(() -> Component.translatable(ended ? "fealty.command.war_cancelled" : "fealty.command.war_none"), true);
                    return ended ? 1 : 0;
                }))
                .then(Commands.literal("menace").then(Commands.argument("village", ResourceLocationArgument.id()).suggests(villages)
                        .executes(WarCommand::menace)));
    }

    private static VillageRecord village(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "village");
        Optional<VillageRecord> village = FealtyWorldData.get(ctx.getSource().getServer()).village(id);
        return village.orElseThrow(UNKNOWN_VILLAGE::create);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        long day = RepManager.day(player.server);
        List<Strongholds.Entry> near = Strongholds.get(player.server).near(player.level().dimension(), player.blockPosition(), 4000);
        if (near.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.war_list_none"), false);
            return 0;
        }
        for (Strongholds.Entry entry : near) {
            int distance = (int) Math.sqrt(Strongholds.flatDistSqr(entry.pos(), player.blockPosition()));
            ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.war_list_entry", entry.name(), entry.pos().getX(),
                    entry.pos().getZ(), distance, entry.captives(), entry.razedDays(day)), false);
        }
        return near.size();
    }

    private static int scout(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageRecord village = village(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int found = Scouting.scout(player.serverLevel(), village).size();
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.war_scouted", found, village.name()), true);
        return found;
    }

    private static int declare(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageRecord village = village(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<Strongholds.Entry> targets = WarTable.targets(player.server, village);
        if (targets.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("fealty.command.war_list_none"));
            return 0;
        }
        Optional<Component> problem = Campaigns.declare(player, village, targets.getFirst(), true);
        if (problem.isPresent()) {
            ctx.getSource().sendFailure(problem.get());
            return 0;
        }
        return 1;
    }

    private static int menace(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageRecord village = village(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        long day = RepManager.day(player.server);
        Optional<Strongholds.Entry> stronghold = Menace.menaceOf(player.serverLevel(), village, day);
        if (stronghold.isEmpty() || !Menace.start(player.serverLevel(), village, stronghold.get(), player, day)) {
            ctx.getSource().sendFailure(Component.translatable("fealty.command.war_no_menace", village.name()));
            return 0;
        }
        return 1;
    }
}
