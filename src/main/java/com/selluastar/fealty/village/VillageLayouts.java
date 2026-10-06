package com.selluastar.fealty.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.selluastar.fealty.Fealty;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.fml.ModList;

/** The loaded {@link VillageLayout}s. Layouts whose mods are missing are kept (for {@code byId}) but never used. */
public final class VillageLayouts {
    private static Map<ResourceLocation, VillageLayout> all = Map.of();
    private static List<VillageLayout> active = List.of();

    private VillageLayouts() {
    }

    public static void apply(Map<ResourceLocation, VillageLayout> loaded) {
        all = Map.copyOf(loaded);
        List<VillageLayout> usable = new ArrayList<>();
        loaded.forEach((id, layout) -> {
            if (layout.mods().stream().allMatch(VillageLayouts::modLoaded)) {
                usable.add(layout);
            } else {
                Fealty.LOGGER.debug("Fealty: village layout {} is off, it needs {}", id, layout.mods());
            }
        });
        usable.sort(Comparator.comparingInt(VillageLayout::priority).reversed());
        active = List.copyOf(usable);
    }

    private static boolean modLoaded(String modId) {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded(modId);
    }

    public static Optional<VillageLayout> byId(ResourceLocation id) {
        return Optional.ofNullable(all.get(id));
    }

    /** The layout for a structure, or {@link VillageLayout#NONE}. */
    public static VillageLayout forStructure(Holder<Structure> structure) {
        for (VillageLayout layout : active) {
            if (layout.matches(structure)) {
                return layout;
            }
        }
        return VillageLayout.NONE;
    }

    /** The layout for a village's structure, or {@link VillageLayout#NONE} for villages with no structure. */
    public static VillageLayout forVillage(ServerLevel level, VillageRecord record) {
        if (record.structure() == null) {
            return VillageLayout.NONE;
        }
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceKey.create(Registries.STRUCTURE, record.structure()))
                .<VillageLayout>map(VillageLayouts::forStructure).orElse(VillageLayout.NONE);
    }
}
