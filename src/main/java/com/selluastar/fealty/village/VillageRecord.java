package com.selluastar.fealty.village;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.guard.Garrison;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** What Fealty knows about one village faction. */
public final class VillageRecord {
    public static final Codec<VillageRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(VillageRecord::id),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(VillageRecord::dimension),
            BlockPos.CODEC.fieldOf("center").forGetter(VillageRecord::center),
            BoundingBox.CODEC.fieldOf("bounds").forGetter(VillageRecord::bounds),
            ResourceLocation.CODEC.fieldOf("template").forGetter(VillageRecord::template),
            ResourceLocation.CODEC.optionalFieldOf("structure").forGetter(r -> Optional.ofNullable(r.structure)),
            Codec.STRING.fieldOf("name").forGetter(VillageRecord::name),
            Codec.BOOL.optionalFieldOf("has_elder", false).forGetter(VillageRecord::hasElder),
            ElderInfo.CODEC.optionalFieldOf("elder").forGetter(r -> Optional.of(r.elder)),
            LordInfo.CODEC.optionalFieldOf("lord").forGetter(r -> Optional.of(r.lord)),
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(VillageRecord::isInitialized),
            UUIDUtil.CODEC.optionalFieldOf("tyrant_for").forGetter(r -> Optional.ofNullable(r.tyrantFor)),
            Codec.BOOL.optionalFieldOf("tyrant_spawned", false).forGetter(VillageRecord::isTyrantSpawned),
            Sites.CODEC.optionalFieldOf("sites").forGetter(r -> Optional.of(r.sites)),
            Garrison.CODEC.optionalFieldOf("garrison").forGetter(r -> Optional.of(r.garrison))
    ).apply(i, (id, dim, center, bounds, template, structure, name, hasElder, elder, lord, init, tyrantFor, tyrantSpawned, sites,
                garrison) -> {
        VillageRecord r = new VillageRecord(id, dim, center, bounds, template, structure.orElse(null), name, hasElder);
        // (Each record needs its own copy: a shared default would tie every village missing the field together.)
        r.elder = elder.orElseGet(ElderInfo::new);
        r.lord = lord.orElseGet(LordInfo::new);
        r.initialized = init;
        r.tyrantFor = tyrantFor.orElse(null);
        r.tyrantSpawned = tyrantSpawned;
        r.sites = sites.orElseGet(Sites::new);
        r.garrison = garrison.orElseGet(Garrison::new);
        return r;
    }));

    /** Heraldic colours villages are given (tabards, journal markers), picked from the village id. */
    private static final int[] COLORS = {
            0xB71C1C, 0x1565C0, 0x2E7D32, 0xF9A825, 0x6A1B9A, 0xEF6C00, 0x00838F, 0x4E342E,
            0xAD1457, 0x283593, 0x558B2F, 0x37474F
    };

    private final ResourceLocation id;
    private final ResourceKey<Level> dimension;
    private BlockPos center;
    private final BoundingBox bounds;
    private final ResourceLocation template;
    @Nullable
    private final ResourceLocation structure;
    private String name;
    private boolean hasElder;
    private Sites sites = new Sites();
    private Garrison garrison = new Garrison();
    private ElderInfo elder = new ElderInfo();
    private LordInfo lord = new LordInfo();
    private boolean initialized;
    @Nullable
    private UUID tyrantFor;
    private boolean tyrantSpawned;

    public VillageRecord(ResourceLocation id, ResourceKey<Level> dimension, BlockPos center, BoundingBox bounds,
                         ResourceLocation template, @Nullable ResourceLocation structure, String name, boolean hasElder) {
        this.id = id;
        this.dimension = dimension;
        this.center = center;
        this.bounds = bounds;
        this.template = template;
        this.structure = structure;
        this.name = name;
        this.hasElder = hasElder;
    }

    public ResourceLocation id() {
        return id;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos center() {
        return center;
    }

    public void setCenter(BlockPos center) {
        this.center = center.immutable();
    }

    public BoundingBox bounds() {
        return bounds;
    }

    public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
        return dimension.equals(dim) && bounds.isInside(pos);
    }

    public ResourceLocation template() {
        return template;
    }

    @Nullable
    public ResourceLocation structure() {
        return structure;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Whether this village is meant to have a trusting elder (its structure is a castle village). */
    public boolean hasElder() {
        return hasElder;
    }

    public void setHasElder(boolean hasElder) {
        this.hasElder = hasElder;
    }

    /** Where the village keeps its things (mailbox, guard post) and how it was found. */
    public Sites sites() {
        return sites;
    }

    /** The village's own guards. */
    public Garrison garrison() {
        return garrison;
    }

    /** The village's heraldic colour, used for guard tabards and journal markers. */
    public int color() {
        return COLORS[Math.floorMod(id.hashCode(), COLORS.length)];
    }

    /** The chunk of the structure start for structure villages, parsed from the id. */
    public java.util.Optional<net.minecraft.world.level.ChunkPos> startChunk() {
        if (structure == null) {
            return java.util.Optional.empty();
        }
        String path = id.getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        int split = last.indexOf('_');
        if (split <= 0) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new net.minecraft.world.level.ChunkPos(Integer.parseInt(last.substring(0, split)),
                    Integer.parseInt(last.substring(split + 1))));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    public ElderInfo elder() {
        return elder;
    }

    public LordInfo lord() {
        return lord;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    /** The wanted player the Tyrant Lord has seized this village to catch, if any. */
    @Nullable
    public UUID tyrantFor() {
        return tyrantFor;
    }

    public void setTyrantFor(@Nullable UUID player) {
        this.tyrantFor = player;
        this.tyrantSpawned = false;
    }

    public boolean isTyrantSpawned() {
        return tyrantSpawned;
    }

    public void setTyrantSpawned(boolean spawned) {
        this.tyrantSpawned = spawned;
    }

    public boolean isBroken() {
        return elder.state == ElderState.BROKEN;
    }

    /**
     * Places and flags that are worked out after a village is first found.
     *
     * @param refined whether the centre and elder flag have been re-checked with the village loaded
     * @param manual  whether an operator created this village by command
     */
    public static final class Sites {
        public static final Codec<Sites> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("refined", false).forGetter(s -> s.refined),
                Codec.BOOL.optionalFieldOf("manual", false).forGetter(s -> s.manual),
                BlockPos.CODEC.optionalFieldOf("mailbox").forGetter(s -> Optional.ofNullable(s.mailbox)),
                BlockPos.CODEC.optionalFieldOf("guard_post").forGetter(s -> Optional.ofNullable(s.guardPost)),
                Codec.LONG.optionalFieldOf("last_raid_day", -1L).forGetter(s -> s.lastRaidDay)
        ).apply(i, (refined, manual, mailbox, post, lastRaid) -> {
            Sites s = new Sites();
            s.refined = refined;
            s.manual = manual;
            s.mailbox = mailbox.orElse(null);
            s.guardPost = post.orElse(null);
            s.lastRaidDay = lastRaid;
            return s;
        }));

        private boolean refined;
        private boolean manual;
        @Nullable
        private BlockPos mailbox;
        @Nullable
        private BlockPos guardPost;
        private long lastRaidDay = -1L;

        public boolean refined() {
            return refined;
        }

        public void setRefined(boolean refined) {
            this.refined = refined;
        }

        public boolean manual() {
            return manual;
        }

        public void setManual(boolean manual) {
            this.manual = manual;
        }

        @Nullable
        public BlockPos mailbox() {
            return mailbox;
        }

        public void setMailbox(@Nullable BlockPos mailbox) {
            this.mailbox = mailbox;
        }

        @Nullable
        public BlockPos guardPost() {
            return guardPost;
        }

        public void setGuardPost(@Nullable BlockPos guardPost) {
            this.guardPost = guardPost;
        }

        public long lastRaidDay() {
            return lastRaidDay;
        }

        public void setLastRaidDay(long day) {
            this.lastRaidDay = day;
        }
    }

    public enum ElderState implements StringRepresentable {
        NONE("none"),
        ALIVE("alive"),
        BROKEN("broken");

        public static final Codec<ElderState> CODEC = StringRepresentable.fromEnum(ElderState::values);
        private final String name;

        ElderState(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** The trusting elder: who they are, where they live and where the village coffer is. */
    public static final class ElderInfo {
        static final Codec<ElderInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
                ElderState.CODEC.optionalFieldOf("state", ElderState.NONE).forGetter(e -> e.state),
                UUIDUtil.CODEC.optionalFieldOf("uuid").forGetter(e -> Optional.ofNullable(e.uuid)),
                BlockPos.CODEC.optionalFieldOf("home").forGetter(e -> Optional.ofNullable(e.home)),
                BlockPos.CODEC.optionalFieldOf("coffer").forGetter(e -> Optional.ofNullable(e.coffer)),
                Codec.STRING.optionalFieldOf("name", "").forGetter(e -> e.name),
                Codec.LONG.optionalFieldOf("broken_since", 0L).forGetter(e -> e.brokenSince)
        ).apply(i, (state, uuid, home, coffer, name, broken) -> {
            ElderInfo e = new ElderInfo();
            e.state = state;
            e.uuid = uuid.orElse(null);
            e.home = home.orElse(null);
            e.coffer = coffer.orElse(null);
            e.name = name;
            e.brokenSince = broken;
            return e;
        }));

        private ElderState state = ElderState.NONE;
        @Nullable
        private UUID uuid;
        @Nullable
        private BlockPos home;
        @Nullable
        private BlockPos coffer;
        private String name = "";
        private long brokenSince;

        public ElderState state() {
            return state;
        }

        public void setState(ElderState state) {
            this.state = state;
        }

        @Nullable
        public UUID uuid() {
            return uuid;
        }

        public void setUuid(@Nullable UUID uuid) {
            this.uuid = uuid;
        }

        @Nullable
        public BlockPos home() {
            return home;
        }

        public void setHome(@Nullable BlockPos home) {
            this.home = home;
        }

        @Nullable
        public BlockPos coffer() {
            return coffer;
        }

        public void setCoffer(@Nullable BlockPos coffer) {
            this.coffer = coffer;
        }

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long brokenSince() {
            return brokenSince;
        }

        public void setBrokenSince(long brokenSince) {
            this.brokenSince = brokenSince;
        }
    }

    /** The village's sworn lord, if any, and their tax settings. */
    public static final class LordInfo {
        static final Codec<LordInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.optionalFieldOf("uuid").forGetter(l -> Optional.ofNullable(l.uuid)),
                Codec.STRING.optionalFieldOf("name", "").forGetter(l -> l.name),
                Codec.intRange(0, 4).optionalFieldOf("tax", 2).forGetter(l -> l.taxLevel),
                Codec.LONG.optionalFieldOf("last_tribute_day", 0L).forGetter(l -> l.lastTributeDay),
                Codec.LONG.optionalFieldOf("last_tax_day", 0L).forGetter(l -> l.lastTaxDay),
                Codec.LONG.optionalFieldOf("sworn_day", 0L).forGetter(l -> l.swornDay),
                Codec.INT.optionalFieldOf("pending_tribute", 0).forGetter(l -> l.pendingTribute),
                Codec.DOUBLE.optionalFieldOf("treasury", 0.0).forGetter(l -> l.treasury),
                Codec.LONG.optionalFieldOf("last_feast_day", -1000L).forGetter(l -> l.lastFeastDay),
                Codec.LONG.optionalFieldOf("last_report_day", 0L).forGetter(l -> l.lastReportDay)
        ).apply(i, (uuid, name, tax, tribute, taxDay, sworn, pending, treasury, feast, report) -> {
            LordInfo l = new LordInfo();
            l.uuid = uuid.orElse(null);
            l.name = name;
            l.taxLevel = tax;
            l.lastTributeDay = tribute;
            l.lastTaxDay = taxDay;
            l.swornDay = sworn;
            l.pendingTribute = pending;
            l.treasury = treasury;
            l.lastFeastDay = feast;
            l.lastReportDay = report;
            return l;
        }));

        @Nullable
        private UUID uuid;
        private String name = "";
        private int taxLevel = 2;
        private long lastTributeDay;
        private long lastTaxDay;
        private long swornDay;
        private int pendingTribute;
        private double treasury;
        private long lastFeastDay = -1000L;
        private long lastReportDay;

        @Nullable
        public UUID uuid() {
            return uuid;
        }

        public boolean isLord(UUID player) {
            return player.equals(uuid);
        }

        public String name() {
            return name;
        }

        public int taxLevel() {
            return taxLevel;
        }

        public void setTaxLevel(int taxLevel) {
            this.taxLevel = Math.max(0, Math.min(4, taxLevel));
        }

        public long lastTributeDay() {
            return lastTributeDay;
        }

        public void setLastTributeDay(long day) {
            this.lastTributeDay = day;
        }

        public long lastTaxDay() {
            return lastTaxDay;
        }

        public void setLastTaxDay(long day) {
            this.lastTaxDay = day;
        }

        public long swornDay() {
            return swornDay;
        }

        /** Tribute periods owed but not yet paid into the coffer (paid when the village is next loaded). */
        public int pendingTribute() {
            return pendingTribute;
        }

        public void setPendingTribute(int pendingTribute) {
            this.pendingTribute = Math.max(0, pendingTribute);
        }

        /** Tribute gathered for the lord and not yet collected, in loot rolls of the tribute table. */
        public double treasury() {
            return treasury;
        }

        public void setTreasury(double treasury) {
            this.treasury = Math.max(0.0, treasury);
        }

        public long lastFeastDay() {
            return lastFeastDay;
        }

        public void setLastFeastDay(long day) {
            this.lastFeastDay = day;
        }

        /** The day the lord was last sent a tribute report. */
        public long lastReportDay() {
            return lastReportDay;
        }

        public void setLastReportDay(long day) {
            this.lastReportDay = day;
        }

        public void swear(UUID lord, String lordName, long day) {
            this.uuid = lord;
            this.name = lordName;
            this.swornDay = day;
            this.lastTributeDay = day;
            this.lastTaxDay = day;
            this.lastReportDay = day;
            this.taxLevel = 2;
            this.pendingTribute = 0;
            this.treasury = 0.0;
        }

        public void clear() {
            this.uuid = null;
            this.name = "";
        }
    }
}
