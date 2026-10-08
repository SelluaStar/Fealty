package com.selluastar.fealty.registry;

import java.util.function.Supplier;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.block.BanditStandardBlockEntity;
import com.selluastar.fealty.block.MailboxBlockEntity;
import com.selluastar.fealty.block.VillageCofferBlockEntity;
import com.selluastar.fealty.block.WarBannerBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Fealty.MOD_ID);

    public static final Supplier<BlockEntityType<VillageCofferBlockEntity>> VILLAGE_COFFER = BLOCK_ENTITIES.register("village_coffer",
            () -> BlockEntityType.Builder.of(VillageCofferBlockEntity::new, ModBlocks.VILLAGE_COFFER.get()).build(null));
    public static final Supplier<BlockEntityType<BanditStandardBlockEntity>> BANDIT_STANDARD = BLOCK_ENTITIES.register("bandit_standard",
            () -> BlockEntityType.Builder.of(BanditStandardBlockEntity::new, ModBlocks.BANDIT_STANDARD.get()).build(null));

    public static final Supplier<BlockEntityType<WarBannerBlockEntity>> WAR_BANNER = BLOCK_ENTITIES.register("war_banner",
            () -> BlockEntityType.Builder.of(WarBannerBlockEntity::new, ModBlocks.WAR_BANNER.get()).build(null));

    public static final Supplier<BlockEntityType<MailboxBlockEntity>> MAILBOX = BLOCK_ENTITIES.register("mailbox",
            () -> BlockEntityType.Builder.of(MailboxBlockEntity::new, ModBlocks.MAILBOX.get()).build(null));

    private ModBlockEntities() {
    }
}
