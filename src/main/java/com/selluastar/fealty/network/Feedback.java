package com.selluastar.fealty.network;

import com.selluastar.fealty.config.FealtyConfig;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Server-side helpers for the player feedback layer: the rep feed, banners, toasts and private sounds. */
public final class Feedback {
    private Feedback() {
    }

    /** A line in the reputation feed, e.g. "+5 Oakshire". */
    public static void rep(ServerPlayer player, Component faction, int amount, int color) {
        if (amount == 0 || !FealtyConfig.SHOW_REP_CHANGES.get()) {
            return;
        }
        send(player, new FeedbackPayload(FeedbackPayload.Kind.REP, faction, Component.empty(), amount, color, "", ItemStack.EMPTY));
    }

    /** A banner across the middle of the screen. */
    public static void banner(ServerPlayer player, Component title, Component detail, int color, String icon) {
        send(player, new FeedbackPayload(FeedbackPayload.Kind.BANNER, title, detail, 0, color, icon, ItemStack.EMPTY));
    }

    /** A toast with a GUI icon. */
    public static void toast(ServerPlayer player, String icon, Component title, Component detail) {
        send(player, new FeedbackPayload(FeedbackPayload.Kind.TOAST, title, detail, 0, 0, icon, ItemStack.EMPTY));
    }

    /** A toast showing an item. */
    public static void toast(ServerPlayer player, ItemStack item, Component title, Component detail) {
        send(player, new FeedbackPayload(FeedbackPayload.Kind.TOAST, title, detail, 0, 0, "scroll", item.copyWithCount(1)));
    }

    /** A sound only this player hears. */
    public static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        sound(player, sound.value(), volume, pitch);
    }

    private static void send(ServerPlayer player, FeedbackPayload payload) {
        FealtyNetwork.send(player, payload);
    }
}
