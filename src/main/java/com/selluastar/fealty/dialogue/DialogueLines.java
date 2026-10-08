package com.selluastar.fealty.dialogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.selluastar.fealty.data.TierManager;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Lines NPCs say, loaded from {@code data/<ns>/fealty/dialogue/*.json}. Files are merged by context, so a data
 * pack adds lines to a context by shipping its own file with the same {@code context}.
 */
public final class DialogueLines {
    private static Map<String, List<DialogueLine>> lines = Map.of();

    private DialogueLines() {
    }

    public static void apply(Map<ResourceLocation, DialogueLine.File> files) {
        Map<String, List<DialogueLine>> merged = new HashMap<>();
        files.values().forEach(file -> merged.computeIfAbsent(file.context(), k -> new ArrayList<>()).addAll(file.lines()));
        lines = Map.copyOf(merged);
    }

    /**
     * A weighted random line for a context whose conditions match. Tiers added by data packs fall back to the
     * nearest default tier's lines.
     */
    public static Optional<Component> pick(String context, SpeakerContext speaker, RandomSource random, Object... args) {
        Optional<Component> line = pickExact(context, speaker, random);
        if (line.isEmpty() && speaker.tier() != null && !isDefaultTier(speaker.tier())) {
            ResourceLocation fallback = fallbackTier(speaker.tier());
            line = pickExact(context, new SpeakerContext(fallback, speaker.profession(), speaker.entityType(), speaker.lord(),
                    speaker.broken(), speaker.night()), random);
        }
        return line.map(text -> withArgs(text, args));
    }

    private static Optional<Component> pickExact(String context, SpeakerContext speaker, RandomSource random) {
        List<DialogueLine> candidates = lines.getOrDefault(context, List.of()).stream().filter(l -> l.matches(speaker)).toList();
        int total = candidates.stream().mapToInt(DialogueLine::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (DialogueLine line : candidates) {
            roll -= line.weight();
            if (roll < 0) {
                return Optional.of(line.text());
            }
        }
        return Optional.empty();
    }

    public static boolean has(String context) {
        return lines.containsKey(context);
    }

    /** Lines written as {@code {"translate": "..."}} get the speaker's arguments (the listener's name first). */
    private static Component withArgs(Component text, Object... args) {
        if (args.length > 0 && text.getContents() instanceof TranslatableContents contents && contents.getArgs().length == 0) {
            return contents.getFallback() != null
                    ? Component.translatableWithFallback(contents.getKey(), contents.getFallback(), args)
                    : Component.translatable(contents.getKey(), args);
        }
        return text;
    }

    private static boolean isDefaultTier(ResourceLocation tier) {
        return tier.getNamespace().equals("fealty") && List.of("hated", "distrusted", "neutral", "trusted", "honored").contains(tier.getPath());
    }

    private static ResourceLocation fallbackTier(ResourceLocation tier) {
        int neutral = TierManager.neutral().rank();
        int rank = TierManager.byId(tier).map(t -> t.rank()).orElse(neutral);
        if (rank == neutral) {
            return TierManager.neutral().id();
        }
        return rank < neutral ? TierManager.distrusted().id() : TierManager.trusted().id();
    }
}
