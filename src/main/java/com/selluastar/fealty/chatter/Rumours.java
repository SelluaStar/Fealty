package com.selluastar.fealty.chatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.event.RumourEvent;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.dialogue.DialogueLines;
import com.selluastar.fealty.dialogue.SpeakerContext;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.trade.VillagerMemory;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.common.NeoForge;

/**
 * What a villager answers when the player asks "What's up?". If they were chatting with another villager (or just
 * were), they say what it was about. Otherwise, depending on how much they trust the player, they refuse, make small
 * talk, or tell them something real ({@link LiveTopics}), which they do once a day.
 */
public final class Rumours {
    public static final ResourceLocation TRIVIA = Fealty.id("rumour/trivia");
    public static final ResourceLocation REFUSED = Fealty.id("rumour/refused");
    public static final ResourceLocation ENOUGH = Fealty.id("rumour/enough");
    /** Percent of answers that are small talk even when there is real news to tell. */
    private static final int TRIVIA_PERCENT = 35;

    /** What the villager says, which topic it was, whether it was real news, and whether it was their chat. */
    public record Told(ResourceLocation topic, Component text, boolean useful, boolean fromChat) {
    }

    private Rumours() {
    }

    public static Told ask(ServerPlayer player, Villager villager) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        Optional<ResourceLocation> faction = FactionResolver.factionOf(villager);
        Optional<VillageRecord> village = faction.filter(Factions::isVillage).flatMap(id -> FealtyWorldData.get(player.server).village(id));
        RepTier tier = faction.map(id -> RepManager.getTier(player, id)).orElseGet(TierManager::neutral);
        boolean hated = tier.rank() <= TierManager.hated().rank();
        boolean sour = tier.rank() <= TierManager.distrusted().rank();
        SpeakerContext context = SpeakerContext.of(villager, player);

        if (hated) {
            return done(player, villager, new Told(REFUSED, line("rumour_refuse", context, random, player, "fealty.rumour.refuse"), false, false));
        }

        // What they were talking about.
        Optional<Topic> chat = Chatter.tellable(villager);
        if (chat.isPresent()) {
            Topic topic = chat.get();
            if (sour && topic.useful()) {
                // They would not tell someone they distrust; the talk stays tellable for someone they do trust.
                return done(player, villager, new Told(REFUSED, Component.translatable("fealty.rumour.private"), false, false));
            }
            reveal(topic, player, level, villager);
            Chatter.told(villager, player);
            return done(player, villager, new Told(topic.id(), topic.tell(), topic.useful(), true));
        }

        // Something they know.
        long day = RepManager.day(player.server);
        VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
        boolean news = FealtyConfig.RUMOURS.get() && village.isPresent() && !sour;
        boolean spent = news && memory.lastRumourDay == day;
        if (news && !spent && random.nextInt(100) >= TRIVIA_PERCENT) {
            List<Topic> facts = new ArrayList<>(LiveTopics.facts(level, village.get(), villager, player, random));
            RumourEvent.Gather gather = NeoForge.EVENT_BUS.post(new RumourEvent.Gather(player, villager));
            for (RumourEvent.Gather.Rumour rumour : gather.getRumours()) {
                facts.add(new Topic(rumour.id(), List.of(), rumour.tell(), true, rumour.weight(), null));
            }
            Optional<Topic> picked = LiveTopics.pick(facts, random);
            if (picked.isPresent()) {
                memory.lastRumourDay = day;
                reveal(picked.get(), player, level, villager);
                return done(player, villager, new Told(picked.get().id(), picked.get().tell(), true, false));
            }
        }
        if (spent && random.nextBoolean()) {
            return done(player, villager, new Told(ENOUGH, Component.translatable("fealty.rumour.enough"), false, false));
        }
        return done(player, villager, new Told(TRIVIA, line("rumour_trivia", context, random, player, "fealty.rumour.trivia.fallback"), false, false));
    }

    private static void reveal(Topic topic, ServerPlayer player, ServerLevel level, Villager villager) {
        if (topic.reveal() != null) {
            topic.reveal().run(player, level, villager);
        }
    }

    private static Component line(String context, SpeakerContext speaker, RandomSource random, ServerPlayer player, String fallback) {
        return DialogueLines.pick(context, speaker, random, player.getDisplayName()).orElse(Component.translatable(fallback));
    }

    private static Told done(ServerPlayer player, Villager villager, Told told) {
        NeoForge.EVENT_BUS.post(new RumourEvent.Told(player, villager, told.topic(), told.text(), told.useful(), told.fromChat()));
        return told;
    }
}
