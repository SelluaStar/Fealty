package com.selluastar.fealty.dialogue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.network.SpeechBubblePayload;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Speech bubbles: lines NPCs say out loud, shown above their heads to everyone nearby. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class Speech {
    /** Players further away than this do not see the bubble. */
    public static final double HEARING = 24.0;
    private static final Map<UUID, Long> LAST_BARK = new HashMap<>();

    private Speech() {
    }

    /** Show a line above an entity's head to every player within hearing. */
    public static void say(Entity speaker, Component text) {
        if (!(speaker.level() instanceof ServerLevel level)) {
            return;
        }
        int ticks = Math.min(200, 50 + text.getString().length() * 2);
        SpeechBubblePayload payload = new SpeechBubblePayload(speaker.getId(), text, ticks);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(speaker) <= HEARING * HEARING) {
                FealtyNetwork.send(player, payload);
            }
        }
        LAST_BARK.put(speaker.getUUID(), level.getGameTime());
    }

    /**
     * Say a random line from a dialogue context, chosen for the listener. Returns the line, or empty if the context
     * has no matching line or the speaker spoke less than {@code cooldown} ticks ago.
     */
    public static Optional<Component> bark(Entity speaker, String context, @Nullable ServerPlayer listener, int cooldown) {
        long now = speaker.level().getGameTime();
        Long last = LAST_BARK.get(speaker.getUUID());
        if (cooldown > 0 && last != null && now - last < cooldown) {
            return Optional.empty();
        }
        Optional<Component> line = DialogueLines.pick(context, SpeakerContext.of(speaker, listener), speaker.level().getRandom(),
                listener != null ? listener.getDisplayName() : Component.empty());
        line.ifPresent(text -> say(speaker, text));
        return line;
    }

    /** Ticks since the entity last spoke, or a large number if never. */
    public static long sinceLastSpoke(Entity speaker) {
        Long last = LAST_BARK.get(speaker.getUUID());
        return last == null ? Long.MAX_VALUE : speaker.level().getGameTime() - last;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_BARK.clear();
    }
}
