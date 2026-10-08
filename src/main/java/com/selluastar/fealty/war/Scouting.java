package com.selluastar.fealty.war;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.datafixers.util.Pair;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.api.Stronghold;
import com.selluastar.fealty.api.event.StrongholdEvent;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.village.VillageRecord;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Finding pillager strongholds: outposts a player walks up to are noted, and a lord's scouts search the land around
 * the village for camps and outposts.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class Scouting {
    /** Fealty's own camps (they also note themselves, from their War Banner). */
    public static final TagKey<Structure> CAMPS = TagKey.create(Registries.STRUCTURE, Fealty.id("pillager_camps"));
    private static final int APPROACH_RANGE = 64;
    private static final int MAX_FOUND = 5;

    private Scouting() {
    }

    /** Every five seconds, note strongholds a player is close to. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 100 == 37 && !player.isSpectator()) {
            discoverNear(player.serverLevel(), player.blockPosition());
        }
    }

    /** Note any stronghold whose structure reaches within {@link #APPROACH_RANGE} blocks of a position. */
    public static void discoverNear(ServerLevel level, BlockPos pos) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkPos center = new ChunkPos(pos);
        int radius = APPROACH_RANGE / 16 + 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<Structure, LongSet> ref : chunk.getAllReferences().entrySet()) {
                    Holder<Structure> holder = registry.wrapAsHolder(ref.getKey());
                    if (!holder.is(FealtyTags.Structures.PILLAGER_STRONGHOLDS) || holder.is(CAMPS)) {
                        continue;
                    }
                    for (long startChunk : ref.getValue()) {
                        noteOutpost(level, holder, ref.getKey(), new ChunkPos(startChunk), pos);
                    }
                }
            }
        }
    }

    private static void noteOutpost(ServerLevel level, Holder<Structure> holder, Structure structure, ChunkPos startPos, BlockPos near) {
        Strongholds strongholds = Strongholds.get(level.getServer());
        if (strongholds.at(level.dimension(), startPos.getMiddleBlockPosition(near.getY()), 64) != null) {
            return;
        }
        ChunkAccess access = level.getChunk(startPos.x, startPos.z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = access.getStartForStructure(structure);
        if (start == null || !start.isValid()) {
            return;
        }
        BoundingBox box = start.getBoundingBox();
        BlockPos heart = new BlockPos(box.getCenter().getX(), box.minY() + 1, box.getCenter().getZ());
        if (Strongholds.flatDistSqr(heart, near) > (double) (APPROACH_RANGE + box.getXSpan()) * (APPROACH_RANGE + box.getXSpan())) {
            return;
        }
        ResourceLocation id = holder.unwrapKey().map(ResourceKey::location).orElse(Fealty.id("pillager_outpost"));
        int reach = Math.max(16, Math.max(box.getXSpan(), box.getZSpan()) / 2);
        strongholds.register(level, heart, Stronghold.Kind.OUTPOST, id, reach, StrongholdEvent.Discovered.How.APPROACHED);
    }

    /**
     * A lord's scouts search the land within {@code war_range} of the village: from the middle and four points
     * half-way out, for camps and for outposts.
     *
     * @return strongholds they found that were not known before
     */
    public static List<Strongholds.Entry> scout(ServerLevel level, VillageRecord village) {
        int range = FealtyConfig.WAR_RANGE.get();
        int chunks = Math.max(8, range / 32);
        BlockPos center = village.center();
        List<BlockPos> from = List.of(center, center.offset(range / 2, 0, 0), center.offset(-range / 2, 0, 0),
                center.offset(0, 0, range / 2), center.offset(0, 0, -range / 2));
        Strongholds strongholds = Strongholds.get(level.getServer());
        List<Strongholds.Entry> found = new ArrayList<>();
        for (BlockPos origin : from) {
            if (found.size() >= MAX_FOUND) {
                break;
            }
            noteFound(level, strongholds, center, range, found, nearest(level, CAMPS, origin, chunks), Stronghold.Kind.CAMP);
            noteFound(level, strongholds, center, range, found, nearest(level, FealtyTags.Structures.PILLAGER_OUTPOSTS, origin, chunks),
                    Stronghold.Kind.OUTPOST);
        }
        return found;
    }

    private static void noteFound(ServerLevel level, Strongholds strongholds, BlockPos center, int range, List<Strongholds.Entry> found,
                                  @Nullable Pair<BlockPos, ResourceLocation> find, Stronghold.Kind kind) {
        if (find == null || Strongholds.flatDistSqr(find.getFirst(), center) > (double) range * range) {
            return;
        }
        BlockPos pos = find.getFirst();
        boolean known = strongholds.at(level.dimension(), pos, 64) != null;
        BlockPos ground = level.isLoaded(pos) ? level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos) : pos;
        Strongholds.Entry entry = strongholds.register(level, ground, kind, find.getSecond(), radiusOf(level, find.getSecond()),
                StrongholdEvent.Discovered.How.SCOUTED);
        if (!known && !found.contains(entry)) {
            found.add(entry);
        }
    }

    /**
     * The nearest structure in a tag, as the map knows it: where it is and which structure it is (a camp may turn out
     * to be a fort or a castle).
     */
    @Nullable
    public static Pair<BlockPos, ResourceLocation> nearest(ServerLevel level, TagKey<Structure> tag, BlockPos origin, int chunks) {
        if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return null;
        }
        Optional<HolderSet.Named<Structure>> set = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(tag);
        if (set.isEmpty()) {
            return null;
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, set.get(), origin,
                chunks, false);
        if (found == null) {
            return null;
        }
        return Pair.of(found.getFirst(), found.getSecond().unwrapKey().map(ResourceKey::location).orElse(Strongholds.PILLAGER_CAMP));
    }

    /** How far a stronghold built as this structure reaches from its heart. */
    public static int radiusOf(ServerLevel level, ResourceLocation structure) {
        return StrongholdKinds.get(StrongholdKinds.idFor(level.getServer(), structure)).radius();
    }
}
