package com.selluastar.fealty.command;

import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.story.PlayerFlags;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /fealty flag <player> get|set|clear <flag> [value]} and {@code /fealty flag <player> list}, for testing stories. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class FlagCommand {
    private FlagCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fealty")
                .then(Commands.literal("flag").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("list").executes(FlagCommand::list))
                                .then(Commands.literal("get")
                                        .then(Commands.argument("flag", ResourceLocationArgument.id()).suggests(FlagCommand::suggestFlags)
                                                .executes(FlagCommand::get)))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("flag", ResourceLocationArgument.id()).suggests(FlagCommand::suggestFlags)
                                                .executes(ctx -> set(ctx, 1))
                                                .then(Commands.argument("value", IntegerArgumentType.integer())
                                                        .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "value"))))))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("flag", ResourceLocationArgument.id()).suggests(FlagCommand::suggestFlags)
                                                .executes(FlagCommand::clear))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestFlags(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        try {
            return SharedSuggestionProvider.suggestResource(PlayerFlags.of(EntityArgument.getPlayer(ctx, "player")).flags().keySet(), builder);
        } catch (CommandSyntaxException e) {
            return builder.buildFuture();
        }
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation flag = ResourceLocationArgument.getId(ctx, "flag");
        int value = PlayerFlags.get(player, flag);
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.flag.get", player.getDisplayName(), flag.toString(), value), false);
        return value;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation flag = ResourceLocationArgument.getId(ctx, "flag");
        PlayerFlags.set(player, flag, value);
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.flag.set", player.getDisplayName(), flag.toString(), value), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation flag = ResourceLocationArgument.getId(ctx, "flag");
        PlayerFlags.clear(player, flag);
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.flag.clear", player.getDisplayName(), flag.toString()), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Map<ResourceLocation, Integer> flags = PlayerFlags.of(player).flags();
        if (flags.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.flag.none", player.getDisplayName()), false);
            return 0;
        }
        String text = flags.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
        ctx.getSource().sendSuccess(() -> Component.translatable("fealty.command.flag.list", player.getDisplayName(), text), false);
        return flags.size();
    }
}
