package com.selluastar.fealty.client.bubble;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Speech bubbles currently shown above entities, by entity id. */
public final class SpeechBubbles {
    public record Bubble(Component text, long start, int ticks) {
        public float age(long now, float partialTick) {
            return now - start + partialTick;
        }
    }

    private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();

    private SpeechBubbles() {
    }

    public static void add(int entityId, Component text, int ticks) {
        long now = now();
        BUBBLES.put(entityId, new Bubble(text, now, ticks));
    }

    public static Bubble get(int entityId) {
        Bubble bubble = BUBBLES.get(entityId);
        if (bubble != null && now() - bubble.start() > bubble.ticks()) {
            BUBBLES.remove(entityId);
            return null;
        }
        return bubble;
    }

    public static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null ? minecraft.level.getGameTime() : 0L;
    }

    /** Drop expired bubbles (called each client tick). */
    public static void tick() {
        long now = now();
        Iterator<Bubble> it = BUBBLES.values().iterator();
        while (it.hasNext()) {
            Bubble bubble = it.next();
            if (now - bubble.start() > bubble.ticks() || now < bubble.start()) {
                it.remove();
            }
        }
    }

    public static void clear() {
        BUBBLES.clear();
    }
}
