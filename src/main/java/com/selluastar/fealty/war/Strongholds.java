package com.selluastar.fealty.war;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.Stronghold;
import com.selluastar.fealty.api.event.StrongholdEvent;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Every pillager stronghold Fealty knows of ({@code data/fealty_strongholds.dat}): its own pillager camps, which
 * register when their War Banner first loads, and pillager outposts, found when a player comes near or a lord's
 * scouts go looking. A stronghold a warband razed stays empty for a while.
 */
public final class Strongholds extends SavedData {
    private static final String NAME = "fealty_strongholds";
    private static final SavedData.Factory<Strongholds> FACTORY = new SavedData.Factory<>(Strongholds::new, Strongholds::load, null);
    /** Two finds closer than this are the same stronghold. */
    private static final int SAME_PLACE = 48;
    public static final ResourceLocation PILLAGER_CAMP = Fealty.id("pillager_camp");

    /** One known stronghold. */
    public static final class Entry {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(e -> e.id),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(e -> e.dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(e -> e.pos),
                Codec.STRING.fieldOf("kind").forGetter(e -> e.kind.name()),
                ResourceLocation.CODEC.fieldOf("structure").forGetter(e -> e.structure),
                Codec.STRING.fieldOf("name").forGetter(e -> e.stem),
                Codec.INT.optionalFieldOf("radius", 24).forGetter(e -> e.radius),
                Codec.LONG.optionalFieldOf("razed_until", -1L).forGetter(e -> e.razedUntil),
                Codec.INT.optionalFieldOf("captives", 0).forGetter(e -> e.captives),
                Codec.BOOL.optionalFieldOf("reoccupy_pending", false).forGetter(e -> e.reoccupyPending),
                ResourceLocation.CODEC.optionalFieldOf("tier").forGetter(e -> Optional.ofNullable(e.tier)),
                StrongholdTrait.CODEC.optionalFieldOf("trait").forGetter(e -> Optional.ofNullable(e.trait))
        ).apply(i, (id, dimension, pos, kind, structure, stem, radius, razed, captives, pending, tier, trait) -> {
            Entry e = new Entry(id, dimension, pos, Stronghold.Kind.valueOf(kind), structure, stem, radius);
            e.razedUntil = razed;
            e.captives = captives;
            e.reoccupyPending = pending;
            e.tier = tier.orElse(null);
            e.trait = trait.orElse(null);
            return e;
        }));

        private final UUID id;
        private final ResourceKey<Level> dimension;
        private BlockPos pos;
        private final Stronghold.Kind kind;
        private ResourceLocation structure;
        private final String stem;
        /** How far from {@link #pos} the stronghold's ground reaches. */
        private int radius;
        private long razedUntil = -1L;
        private int captives;
        private boolean reoccupyPending;
        /** Its stronghold kind (found from the structure the first time it is needed). */
        @Nullable
        private ResourceLocation tier;
        @Nullable
        private StrongholdTrait trait;

        Entry(UUID id, ResourceKey<Level> dimension, BlockPos pos, Stronghold.Kind kind, ResourceLocation structure, String stem, int radius) {
            this.id = id;
            this.dimension = dimension;
            this.pos = pos.immutable();
            this.kind = kind;
            this.structure = structure;
            this.stem = stem;
            this.radius = radius;
        }

        public UUID id() {
            return id;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        /** A camp's War Banner, or the middle of an outpost. */
        public BlockPos pos() {
            return pos;
        }

        public Stronghold.Kind kind() {
            return kind;
        }

        public ResourceLocation structure() {
            return structure;
        }

        public int radius() {
            return radius;
        }

        public Component name() {
            return Component.translatable(strongholdKind().name(), stem);
        }

        /** Its kind's id. */
        public ResourceLocation tier() {
            return tier != null ? tier : StrongholdKinds.FALLBACK_ID;
        }

        public StrongholdKind strongholdKind() {
            return StrongholdKinds.get(tier);
        }

        public StrongholdTrait trait() {
            return trait != null ? trait : StrongholdTrait.NONE;
        }

        /** How dangerous it is: 1 to 5 skulls, its kind's threat plus its trait. */
        public int threat() {
            return Math.max(1, Math.min(5, strongholdKind().threat() + trait().threat()));
        }

        /** Find its kind from its structure, and roll its trait, if not done yet. @return whether anything changed */
        boolean resolve(MinecraftServer server) {
            boolean changed = false;
            if (tier == null || StrongholdKinds.byId(tier).isEmpty()) {
                ResourceLocation found = StrongholdKinds.idFor(server, structure);
                changed = !found.equals(tier);
                tier = found;
            }
            if (trait == null) {
                trait = strongholdKind().rollTrait(pos);
                changed = true;
            }
            return changed;
        }

        /** Rolls of tribute razing it adds to the treasury: its kind's (or the config's), one more for a trait. */
        public int spoils() {
            int base = strongholdKind().spoils() >= 0 ? strongholdKind().spoils() : FealtyConfig.WAR_SPOILS.get();
            return base + (trait() != StrongholdTrait.NONE ? 1 : 0);
        }

        /** Days of peace razing it wins the village. */
        public int peaceDays() {
            return strongholdKind().peaceDays() >= 0 ? strongholdKind().peaceDays() : FealtyConfig.PEACE_DAYS.get();
        }

        /** Days it stays empty once razed. */
        public int razeDays() {
            return strongholdKind().razeDays() >= 0 ? strongholdKind().razeDays() : FealtyConfig.RAZE_DAYS.get();
        }

        /** How far (blocks) it menaces villages. */
        public int menaceRange() {
            return strongholdKind().menaceRange() >= 0 ? strongholdKind().menaceRange() : FealtyConfig.MENACE_RANGE.get();
        }

        /** The Raid Omen its raids bring before the days add to it: its kind's, one more for a trait. */
        public int menaceOmen() {
            return Math.min(5, strongholdKind().menaceOmen() + (trait() != StrongholdTrait.NONE ? 1 : 0));
        }

        /** About how many hold it, by its War Banner's muster (0 when it is whoever is there). */
        public int defenders() {
            return strongholdKind().defenders(trait(), pos);
        }

        /** For tests and commands: give it a trait. */
        public void setTrait(StrongholdTrait trait) {
            this.trait = trait;
        }

        public boolean isRazed(long day) {
            return razedUntil >= 0 && day < razedUntil;
        }

        public long razedUntil() {
            return razedUntil;
        }

        /** Days left of the raze, or 0. */
        public int razedDays(long day) {
            return isRazed(day) ? (int) (razedUntil - day) : 0;
        }

        public int captives() {
            return captives;
        }

        public void setCaptives(int captives) {
            this.captives = Math.max(0, captives);
        }

        /** Whether a position is on the stronghold's ground (plus a margin), going by distance across the ground. */
        public boolean contains(ResourceKey<Level> dim, BlockPos at, int margin) {
            int reach = radius + margin;
            return dimension.equals(dim) && flatDistSqr(at, pos) <= (double) reach * reach;
        }

        /** Move the heart to where it really is (scouts only know roughly; the banner or the ground knows better). */
        public void settle(BlockPos exact) {
            this.pos = exact.immutable();
        }

        /**
         * The War Banner knows what the stronghold really is, and where: scouts only see a camp on the map, which may
         * be a fort or a castle. A different structure means a different kind, so the trait is rolled again.
         *
         * @return whether anything changed
         */
        boolean confirm(MinecraftServer server, BlockPos exact, ResourceLocation built, int reach) {
            boolean changed = !pos.equals(exact) || !structure.equals(built) || radius != reach;
            pos = exact.immutable();
            radius = reach;
            if (!structure.equals(built)) {
                structure = built;
                tier = null;
                trait = null;
                resolve(server);
            }
            return changed;
        }

        public Stronghold view(long day) {
            return new Stronghold(id, dimension, pos, kind, structure, name(), isRazed(day), captives, tier(), threat(),
                    trait().getSerializedName());
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    /** Whether every entry's kind and trait were worked out since loading. */
    private boolean resolved;

    public static Strongholds get(MinecraftServer server) {
        Strongholds data = server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        if (!data.resolved) {
            data.resolved = true;
            for (Entry entry : data.entries) {
                if (entry.resolve(server)) {
                    data.setDirty();
                }
            }
        }
        return data;
    }

    private static Strongholds load(CompoundTag tag, HolderLookup.Provider registries) {
        Strongholds data = new Strongholds();
        if (tag.contains("strongholds")) {
            Entry.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("strongholds"))
                    .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to load strongholds: {}", e))
                    .ifPresent(data.entries::addAll);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Entry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, entries)
                .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to save strongholds: {}", e))
                .ifPresent(t -> tag.put("strongholds", t));
        return tag;
    }

    /**
     * Note a stronghold, or find the one already known there.
     *
     * @return the entry, and whether it is new
     */
    public Entry register(ServerLevel level, BlockPos pos, Stronghold.Kind kind, ResourceLocation structure, int radius,
                          StrongholdEvent.Discovered.How how) {
        Entry known = at(level.dimension(), pos, SAME_PLACE);
        if (known != null) {
            if (how == StrongholdEvent.Discovered.How.GENERATED && known.confirm(level.getServer(), pos, structure, radius)) {
                setDirty();
            }
            return known;
        }
        Entry entry = new Entry(UUID.randomUUID(), level.dimension(), pos, kind, structure, StrongholdNames.stem(pos), radius);
        entry.resolve(level.getServer());
        entries.add(entry);
        setDirty();
        NeoForge.EVENT_BUS.post(new StrongholdEvent.Discovered(level.getServer(), entry.view(RepManager.day(level.getServer())), how));
        return entry;
    }

    public void remove(Entry entry) {
        if (entries.remove(entry)) {
            setDirty();
        }
    }

    public Optional<Entry> get(UUID id) {
        for (Entry entry : entries) {
            if (entry.id.equals(id)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public List<Entry> all() {
        return List.copyOf(entries);
    }

    /** The stronghold at a place, within {@code within} blocks of its heart. */
    @Nullable
    public Entry at(ResourceKey<Level> dimension, BlockPos pos, int within) {
        for (Entry entry : entries) {
            if (entry.dimension.equals(dimension) && flatDistSqr(entry.pos, pos) <= (double) within * within) {
                return entry;
            }
        }
        return null;
    }

    /** The stronghold whose ground (plus a margin) takes in a position. */
    @Nullable
    public Entry around(ResourceKey<Level> dimension, BlockPos pos, int margin) {
        for (Entry entry : entries) {
            if (entry.contains(dimension, pos, margin)) {
                return entry;
            }
        }
        return null;
    }

    /** Known strongholds within {@code radius} blocks, nearest first. */
    public List<Entry> near(ResourceKey<Level> dimension, BlockPos pos, double radius) {
        List<Entry> found = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.dimension.equals(dimension) && flatDistSqr(entry.pos, pos) <= radius * radius) {
                found.add(entry);
            }
        }
        found.sort(Comparator.comparingDouble(e -> flatDistSqr(e.pos, pos)));
        return found;
    }

    /** Squared distance across the ground, ignoring height (scouts only know where a stronghold is on the map). */
    public static double flatDistSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** Raze a stronghold for some days. */
    public void raze(MinecraftServer server, Entry entry, ResourceLocation village, int days) {
        long day = RepManager.day(server);
        entry.razedUntil = day + days;
        entry.reoccupyPending = true;
        setDirty();
        NeoForge.EVENT_BUS.post(new StrongholdEvent.Razed(server, entry.view(day), village, days));
    }

    /**
     * Called once a day: strongholds whose raze is over are reoccupied. Camps fill up again the next time their
     * War Banner is loaded.
     */
    public void tickDay(MinecraftServer server, long day) {
        for (Entry entry : entries) {
            if (entry.reoccupyPending && !entry.isRazed(day)) {
                entry.reoccupyPending = false;
                entry.razedUntil = -1L;
                setDirty();
                NeoForge.EVENT_BUS.post(new StrongholdEvent.Reoccupied(server, entry.view(day)));
            }
        }
    }
}
