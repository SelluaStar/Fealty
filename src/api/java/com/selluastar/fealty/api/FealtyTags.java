package com.selluastar.fealty.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Tags Fealty reads. Packs and mods can add entries to them. */
public final class FealtyTags {
    public static final class Entities {
        /** Entities that act as village guards (iron golems, Guard Villagers' guards, ...). */
        public static final TagKey<EntityType<?>> GUARDS = TagKey.create(Registries.ENTITY_TYPE, FealtyApi.id("guards"));
        /** Entities that belong to the village they stand in and can witness crimes. */
        public static final TagKey<EntityType<?>> VILLAGE_MEMBERS = TagKey.create(Registries.ENTITY_TYPE, FealtyApi.id("village_members"));
        /** Members of the bandit faction. */
        public static final TagKey<EntityType<?>> BANDITS = TagKey.create(Registries.ENTITY_TYPE, FealtyApi.id("bandits"));

        private Entities() {
        }
    }

    public static final class Blocks {
        /** Blocks that count as village property when broken inside a village. */
        public static final TagKey<Block> VILLAGE_PROPERTY = TagKey.create(Registries.BLOCK, FealtyApi.id("village_property"));
        /** Containers whose contents belong to the village. */
        public static final TagKey<Block> VILLAGE_CONTAINERS = TagKey.create(Registries.BLOCK, FealtyApi.id("village_containers"));
        /** Workstations restocked by repair quests. */
        public static final TagKey<Block> WORKSTATIONS = TagKey.create(Registries.BLOCK, FealtyApi.id("workstations"));

        private Blocks() {
        }
    }

    public static final class Items {
        /** Items that count as a drawn weapon for threatening villagers. */
        public static final TagKey<Item> THREAT_WEAPONS = TagKey.create(Registries.ITEM, FealtyApi.id("threat_weapons"));
        /** Items villagers welcome as gifts. */
        public static final TagKey<Item> VILLAGER_GIFTS = TagKey.create(Registries.ITEM, FealtyApi.id("villager_gifts"));

        private Items() {
        }
    }

    public static final class Structures {
        /** Village structures whose village gets a trusting elder (the pack's castle villages). */
        public static final TagKey<Structure> ELDER_VILLAGES = TagKey.create(Registries.STRUCTURE, FealtyApi.id("elder_villages"));
        /** Structures the rare villager chain's treasure map can point to. */
        public static final TagKey<Structure> HIDDEN_HAMLETS = TagKey.create(Registries.STRUCTURE, FealtyApi.id("hidden_hamlets"));
        /** Bandit camps, used by defend quests and the outlaw path. */
        public static final TagKey<Structure> BANDIT_CAMPS = TagKey.create(Registries.STRUCTURE, FealtyApi.id("bandit_camps"));

        private Structures() {
        }
    }

    private FealtyTags() {
    }
}
