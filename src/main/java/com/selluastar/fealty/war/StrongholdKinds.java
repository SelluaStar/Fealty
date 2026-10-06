package com.selluastar.fealty.war;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.selluastar.fealty.Fealty;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;

/** The loaded {@link StrongholdKind}s. */
public final class StrongholdKinds {
    /** Used for strongholds no kind matches: a camp's worth of pillagers. */
    public static final ResourceLocation FALLBACK_ID = Fealty.id("camp");
    private static final StrongholdKind FALLBACK = StrongholdKind.of(List.of(), Integer.MIN_VALUE, "fealty.stronghold.camp", 2,
            List.of(new StrongholdKind.Squad(EntityType.PILLAGER, 3, 5), new StrongholdKind.Squad(EntityType.VINDICATOR, 1, 2)),
            Optional.of(EntityType.PILLAGER), 2, 4, -1, -1, -1, -1, 1, Map.of("none", 1), 20);
    private static Map<ResourceLocation, StrongholdKind> all = Map.of();
    private static List<Map.Entry<ResourceLocation, StrongholdKind>> ordered = List.of();

    private StrongholdKinds() {
    }

    public static void apply(Map<ResourceLocation, StrongholdKind> loaded) {
        all = Map.copyOf(loaded);
        List<Map.Entry<ResourceLocation, StrongholdKind>> list = new ArrayList<>(loaded.entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<ResourceLocation, StrongholdKind> e) -> e.getValue().priority()).reversed());
        ordered = List.copyOf(list);
    }

    public static Optional<StrongholdKind> byId(ResourceLocation id) {
        return Optional.ofNullable(all.get(id));
    }

    /** The kind with this id, or the fallback camp. */
    public static StrongholdKind get(ResourceLocation id) {
        return id == null ? FALLBACK : all.getOrDefault(id, FALLBACK);
    }

    /** The id of the kind that covers a structure, or the fallback camp. */
    public static ResourceLocation idFor(MinecraftServer server, ResourceLocation structure) {
        var holder = server.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(ResourceKey.create(Registries.STRUCTURE, structure));
        if (holder.isPresent()) {
            for (Map.Entry<ResourceLocation, StrongholdKind> entry : ordered) {
                if (entry.getValue().matches(holder.get())) {
                    return entry.getKey();
                }
            }
        }
        return FALLBACK_ID;
    }
}
