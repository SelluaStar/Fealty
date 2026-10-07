package com.selluastar.fealty.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.crime.Fines;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /fealty fine pay} and {@code /fealty fine refuse}: what the buttons under a guard's demand in chat run. Open
 * to every player, since they only ever act on the player's own fine.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class FineCommand {
    private static final SimpleCommandExceptionType NO_FINE = new SimpleCommandExceptionType(Component.translatable("fealty.fine.none"));

    private FineCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fealty")
                .then(Commands.literal("fine")
                        .then(Commands.literal("pay").executes(ctx -> {
                            if (Fines.status(ctx.getSource().getPlayerOrException()).isEmpty()) {
                                throw NO_FINE.create();
                            }
                            return Fines.payPending(ctx.getSource().getPlayerOrException()) ? 1 : 0;
                        }))
                        .then(Commands.literal("refuse").executes(ctx -> {
                            if (!Fines.refusePending(ctx.getSource().getPlayerOrException())) {
                                throw NO_FINE.create();
                            }
                            return 1;
                        }))));
    }
}
