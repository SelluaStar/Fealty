package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.entity.BanditEntity;
import com.selluastar.fealty.entity.BlackMarketeerEntity;
import com.selluastar.fealty.entity.BountyHunterEntity;
import com.selluastar.fealty.entity.GuildFenceEntity;
import com.selluastar.fealty.entity.KeeperEntity;
import com.selluastar.fealty.entity.SmokeBombEntity;
import com.selluastar.fealty.entity.TyrantLordEntity;
import com.selluastar.fealty.entity.VillageElderEntity;
import com.selluastar.fealty.entity.VillageGuardEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Fealty.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<VillageElderEntity>> VILLAGE_ELDER = ENTITIES.register("village_elder",
            () -> EntityType.Builder.of(VillageElderEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).eyeHeight(1.62F)
                    .clientTrackingRange(10).build("village_elder"));
    public static final DeferredHolder<EntityType<?>, EntityType<KeeperEntity>> KEEPER = ENTITIES.register("keeper",
            () -> EntityType.Builder.of(KeeperEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).eyeHeight(1.62F)
                    .clientTrackingRange(10).build("keeper"));
    public static final DeferredHolder<EntityType<?>, EntityType<GuildFenceEntity>> GUILD_FENCE = ENTITIES.register("guild_fence",
            () -> EntityType.Builder.of(GuildFenceEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
                    .clientTrackingRange(10).build("guild_fence"));
    public static final DeferredHolder<EntityType<?>, EntityType<BlackMarketeerEntity>> BLACK_MARKETEER = ENTITIES.register("black_marketeer",
            () -> EntityType.Builder.of(BlackMarketeerEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
                    .clientTrackingRange(10).build("black_marketeer"));
    public static final DeferredHolder<EntityType<?>, EntityType<BanditEntity>> BANDIT = ENTITIES.register("bandit",
            () -> EntityType.Builder.of(BanditEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F).eyeHeight(1.62F)
                    .clientTrackingRange(8).build("bandit"));
    public static final DeferredHolder<EntityType<?>, EntityType<BanditEntity>> BANDIT_CAPTAIN = ENTITIES.register("bandit_captain",
            () -> EntityType.Builder.of(BanditEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F).eyeHeight(1.62F)
                    .clientTrackingRange(8).build("bandit_captain"));
    public static final DeferredHolder<EntityType<?>, EntityType<BountyHunterEntity>> BOUNTY_HUNTER = ENTITIES.register("bounty_hunter",
            () -> EntityType.Builder.of(BountyHunterEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F).eyeHeight(1.62F)
                    .clientTrackingRange(8).build("bounty_hunter"));
    public static final DeferredHolder<EntityType<?>, EntityType<TyrantLordEntity>> TYRANT_LORD = ENTITIES.register("tyrant_lord",
            () -> EntityType.Builder.of(TyrantLordEntity::new, MobCategory.MONSTER).sized(0.84F, 2.6F).eyeHeight(2.25F)
                    .fireImmune().clientTrackingRange(10).build("tyrant_lord"));
    public static final DeferredHolder<EntityType<?>, EntityType<VillageGuardEntity>> VILLAGE_GUARD = ENTITIES.register("village_guard",
            () -> EntityType.Builder.of(VillageGuardEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).eyeHeight(1.62F)
                    .clientTrackingRange(10).build("village_guard"));
    public static final DeferredHolder<EntityType<?>, EntityType<SmokeBombEntity>> SMOKE_BOMB = ENTITIES.register("smoke_bomb",
            () -> EntityType.Builder.<SmokeBombEntity>of(SmokeBombEntity::new, MobCategory.MISC).sized(0.25F, 0.25F)
                    .clientTrackingRange(4).updateInterval(10).build("smoke_bomb"));

    private ModEntities() {
    }

    static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(VILLAGE_ELDER.get(), VillageElderEntity.createAttributes().build());
        event.put(KEEPER.get(), KeeperEntity.createAttributes().build());
        event.put(GUILD_FENCE.get(), GuildFenceEntity.createAttributes().build());
        event.put(BLACK_MARKETEER.get(), BlackMarketeerEntity.createAttributes().build());
        event.put(BANDIT.get(), BanditEntity.createAttributes().build());
        event.put(BANDIT_CAPTAIN.get(), BanditEntity.createCaptainAttributes().build());
        event.put(BOUNTY_HUNTER.get(), BountyHunterEntity.createAttributes().build());
        event.put(TYRANT_LORD.get(), TyrantLordEntity.createAttributes().build());
        event.put(VILLAGE_GUARD.get(), VillageGuardEntity.createAttributes().build());
    }
}
