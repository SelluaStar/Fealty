package com.selluastar.fealty.story;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.dialogue.DialogueContext;
import com.selluastar.fealty.api.event.RumourEvent;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.dialogue.DialogueService;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.entity.VillageElderEntity;
import com.selluastar.fealty.network.OpenQuestScreenPayload.ActionEntry;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.story.script.Scripts;
import com.selluastar.fealty.story.script.Texts;
import com.selluastar.fealty.trade.VillagerMemory;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Rumours from {@code fealty/rumours/}. The first time each day a player talks to a villager, the villager rolls
 * {@code rumour_chance} (plus {@code pity_step} for each conversation in a row that could have turned one up but did
 * not, up to {@code pity_cap}; a sure thing after {@code guarantee_after}) to tell one of the rumours they could tell.
 * Asking an elder about rumours draws from the same pool, the rare villager chain's rumour among them.
 */
public final class RumourService {
    private static Map<ResourceLocation, RumourDefinition> loaded = Map.of();
    /** Rumours added in code (tests); data packs win on the same id. */
    private static final Map<ResourceLocation, RumourDefinition> EXTRA = new LinkedHashMap<>();

    private RumourService() {
    }

    public static void apply(Map<ResourceLocation, RumourDefinition> rumours) {
        loaded = Map.copyOf(rumours);
    }

    public static Map<ResourceLocation, RumourDefinition> all() {
        if (EXTRA.isEmpty()) {
            return loaded;
        }
        Map<ResourceLocation, RumourDefinition> all = new LinkedHashMap<>(EXTRA);
        all.putAll(loaded);
        return all;
    }

    /** Add (or with null, remove) a rumour without a data pack. */
    public static void define(ResourceLocation id, @Nullable RumourDefinition rumour) {
        if (rumour == null) {
            EXTRA.remove(id);
        } else {
            EXTRA.put(id, rumour);
        }
    }

    // ---- Who could tell what ----

    /** The village a teller belongs to: an elder's own, else the teller's faction if it is a village. */
    public static Optional<VillageRecord> villageOf(ServerPlayer player, Entity teller) {
        ResourceLocation id = teller instanceof VillageElderEntity elder ? elder.village() : null;
        if (id == null) {
            id = FactionResolver.factionOf(teller).filter(Factions::isVillage).orElse(null);
        }
        return id == null ? Optional.empty() : FealtyWorldData.get(player.server).village(id);
    }

    /** Every rumour this teller could tell the player now, in id order. */
    public static List<Map.Entry<ResourceLocation, RumourDefinition>> eligible(ServerPlayer player, Entity teller) {
        boolean elder = teller instanceof VillageElderEntity;
        Optional<VillageRecord> village = villageOf(player, teller);
        Optional<ResourceLocation> faction = village.map(VillageRecord::id).or(() -> FactionResolver.factionOf(teller));
        PlayerFlags flags = PlayerFlags.of(player);
        List<Map.Entry<ResourceLocation, RumourDefinition>> pool = new ArrayList<>();
        for (Map.Entry<ResourceLocation, RumourDefinition> entry : all().entrySet()) {
            if (fits(player, teller, elder, entry.getKey(), entry.getValue(), village.orElse(null), faction, flags)) {
                pool.add(entry);
            }
        }
        pool.sort(Map.Entry.comparingByKey());
        return pool;
    }

    private static boolean fits(ServerPlayer player, Entity teller, boolean elder, ResourceLocation id, RumourDefinition rumour,
                                @Nullable VillageRecord village, Optional<ResourceLocation> faction, PlayerFlags flags) {
        if (!rumour.toldBy().tells(elder)) {
            return false;
        }
        if (!elder && !rumour.professions().isEmpty() && !(teller instanceof Villager villager
                && rumour.professions().contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession())))) {
            return false;
        }
        Optional<RepTier> min = TierManager.byId(rumour.minTier());
        if (min.isPresent() && (faction.isEmpty() || RepManager.getTier(player, faction.get()).rank() < min.get().rank())) {
            return false;
        }
        if (rumour.chain().isPresent()) {
            if (!ChainManager.rumourFits(player, rumour.chain().get(), rumour.stage(), village)) {
                return false;
            }
        } else if (flags.hasHeard(id)) {
            return false;
        }
        return Scripts.test(rumour.requires(), DialogueContext.of(player, teller));
    }

    /** A weighted pick. */
    public static Optional<Map.Entry<ResourceLocation, RumourDefinition>> pick(List<Map.Entry<ResourceLocation, RumourDefinition>> pool,
                                                                               RandomSource random) {
        int total = pool.stream().mapToInt(e -> e.getValue().weight()).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Map.Entry<ResourceLocation, RumourDefinition> entry : pool) {
            roll -= entry.getValue().weight();
            if (roll < 0) {
                return Optional.of(entry);
            }
        }
        return Optional.of(pool.getLast());
    }

    // ---- Villagers ----

    /** The chance of a rumour after this many misses in a row. */
    public static double chance(int misses) {
        if (misses >= FealtyConfig.RUMOUR_GUARANTEE_AFTER.get()) {
            return 1.0;
        }
        double base = FealtyConfig.RUMOUR_CHANCE.get();
        double withPity = Math.min(FealtyConfig.RUMOUR_PITY_CAP.get(), base + misses * FealtyConfig.RUMOUR_PITY_STEP.get());
        return Math.max(base, withPity);
    }

    /**
     * A villager's roll as the player starts talking to them (once a day per villager and player).
     *
     * @return what the villager adds to their greeting
     */
    public static List<Component> converse(ServerPlayer player, Villager villager) {
        return converse(player, villager, villager.getRandom().nextDouble());
    }

    /** {@link #converse(ServerPlayer, Villager)} with the roll given (0 to 1; under the chance tells a rumour). */
    public static List<Component> converse(ServerPlayer player, Villager villager, double roll) {
        if (FealtyConfig.RUMOUR_CHANCE.get() <= 0) {
            return List.of();
        }
        long day = RepManager.day(player.server);
        VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
        if (memory.lastRumourRoll == day) {
            return List.of();
        }
        memory.lastRumourRoll = day;
        List<Map.Entry<ResourceLocation, RumourDefinition>> pool = eligible(player, villager);
        if (pool.isEmpty()) {
            return List.of(); // nothing to tell: not a miss
        }
        PlayerFlags flags = PlayerFlags.of(player);
        if (roll >= chance(flags.rumourMisses())) {
            flags.setRumourMisses(flags.rumourMisses() + 1);
            return List.of();
        }
        flags.setRumourMisses(0);
        Optional<Map.Entry<ResourceLocation, RumourDefinition>> picked = pick(pool, villager.getRandom());
        return picked.flatMap(e -> hear(player, villager, e.getKey(), e.getValue())).orElse(List.of());
    }

    // ---- Elders ----

    /** The elder's "rumours" button: open when the elder has a rumour for the player, otherwise as it always was. */
    public static Optional<ActionEntry> elderAction(ServerPlayer player, Entity elder, VillageRecord village) {
        if (!eligible(player, elder).isEmpty()) {
            return Optional.of(ActionEntry.of(ChainManager.ACTION_RUMOURS, Component.translatable("fealty.chain.rumours")));
        }
        return ChainManager.rumoursAction(player, village);
    }

    /** The player asked the elder about rumours. */
    public static void askElder(ServerPlayer player, Entity elder, VillageRecord village) {
        Optional<Map.Entry<ResourceLocation, RumourDefinition>> picked = pick(eligible(player, elder), elder.getRandom());
        if (picked.isEmpty()) {
            ChainManager.startFromElder(player, village);
            return;
        }
        Optional<List<Component>> lines = hear(player, elder, picked.get().getKey(), picked.get().getValue());
        if (lines.isPresent() && !lines.get().isEmpty()) {
            Component text = join(lines.get());
            Speech.say(elder, text);
            DialogueService.answer(player, text);
        }
    }

    // ---- Hearing one ----

    /**
     * A rumour is told: {@link RumourEvent.Heard} (which may stop it), its chain started or moved on, its grants, and it
     * is remembered as heard.
     *
     * @return what the teller says, or empty if nothing was told
     */
    public static Optional<List<Component>> hear(ServerPlayer player, Entity teller, ResourceLocation id, RumourDefinition rumour) {
        List<Component> lines = new ArrayList<>();
        if (!rumour.lines().isEmpty()) {
            lines.add(Texts.say(rumour.lines().get(teller.getRandom().nextInt(rumour.lines().size())), player, teller));
        }
        RumourEvent.Heard event = NeoForge.EVENT_BUS.post(new RumourEvent.Heard(player, teller, id, rumour.chain(), lines));
        if (event.isCanceled()) {
            return Optional.empty();
        }
        if (rumour.chain().isPresent()
                && !ChainManager.reachStage(player, rumour.chain().get(), rumour.stage(), villageOf(player, teller).orElse(null))) {
            return Optional.empty();
        }
        PlayerFlags.of(player).markHeard(id);
        Scripts.apply(rumour.grants(), DialogueContext.of(player, teller));
        return Optional.of(List.copyOf(event.getLines()));
    }

    /** Lines one under another. */
    public static Component join(List<Component> lines) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                text.append("\n");
            }
            text.append(lines.get(i));
        }
        return text;
    }
}
