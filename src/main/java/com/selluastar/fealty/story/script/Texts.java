package com.selluastar.fealty.story.script;

import org.jetbrains.annotations.Nullable;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Text in story data: a string is a translation key, anything else a text component. */
public final class Texts {
    public static final Codec<Component> CODEC = Codec.either(Codec.STRING, ComponentSerialization.CODEC).xmap(
            either -> either.map(key -> (Component) Component.translatable(key), component -> component), Texts::encode);

    private static Either<String, Component> encode(Component component) {
        if (component.getContents() instanceof TranslatableContents key && key.getArgs().length == 0 && key.getFallback() == null
                && component.getSiblings().isEmpty() && component.getStyle().isEmpty()) {
            return Either.left(key.getKey());
        }
        return Either.right(component);
    }

    private Texts() {
    }

    /**
     * A line as said to this player: a translation key given without arguments gets the player's name as {@code %1$s}
     * and the speaker's as {@code %2$s}.
     */
    public static Component say(Component text, ServerPlayer player, @Nullable Entity speaker) {
        if (!(text.getContents() instanceof TranslatableContents key) || key.getArgs().length > 0) {
            return text;
        }
        Component name = speaker != null ? speaker.getDisplayName() : Component.empty();
        MutableComponent named = key.getFallback() != null
                ? Component.translatableWithFallback(key.getKey(), key.getFallback(), player.getDisplayName(), name)
                : Component.translatable(key.getKey(), player.getDisplayName(), name);
        named.setStyle(text.getStyle());
        text.getSiblings().forEach(named::append);
        return named;
    }
}
