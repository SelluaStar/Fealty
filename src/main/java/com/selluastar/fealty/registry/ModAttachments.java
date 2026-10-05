package com.selluastar.fealty.registry;

import java.util.function.Supplier;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.chain.ChainRole;
import com.selluastar.fealty.crime.PlacedBlocks;
import com.selluastar.fealty.guard.GuardOrders;
import com.selluastar.fealty.quest.QuestTarget;
import com.selluastar.fealty.trade.VillagerMemory;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Fealty.MOD_ID);

    /** Forces an entity into a faction (elders, the Keeper, quest NPCs, or anything a pack sets). */
    public static final Supplier<AttachmentType<ResourceLocation>> FACTION = ATTACHMENTS.register("faction",
            () -> AttachmentType.builder(() -> Fealty.id("wanderers")).serialize(ResourceLocation.CODEC).build());

    /** Blocks players placed inside villages, per chunk, so moving your own things is not a crime. */
    public static final Supplier<AttachmentType<PlacedBlocks>> PLACED_BLOCKS = ATTACHMENTS.register("placed_blocks",
            () -> AttachmentType.builder(PlacedBlocks::new).serialize(PlacedBlocks.CODEC).build());

    /** A villager's memory of threats and gifts, per player. */
    public static final Supplier<AttachmentType<VillagerMemory>> VILLAGER_MEMORY = ATTACHMENTS.register("villager_memory",
            () -> AttachmentType.builder(VillagerMemory::new).serialize(VillagerMemory.CODEC).build());

    /** Orders for a guard: escorting a player, or a lord's command. */
    public static final Supplier<AttachmentType<GuardOrders>> GUARD_ORDERS = ATTACHMENTS.register("guard_orders",
            () -> AttachmentType.builder(GuardOrders::new).serialize(GuardOrders.CODEC).build());

    /** Marks a mob as the target of someone's quest (hunts, trials). */
    public static final Supplier<AttachmentType<QuestTarget>> QUEST_TARGET = ATTACHMENTS.register("quest_target",
            () -> AttachmentType.builder(QuestTarget::empty).serialize(QuestTarget.CODEC).build());

    /** Makes a villager one of a quest chain's named villagers. */
    public static final Supplier<AttachmentType<ChainRole>> CHAIN_ROLE = ATTACHMENTS.register("chain_role",
            () -> AttachmentType.builder(ChainRole::none).serialize(ChainRole.CODEC).build());

    /** A bandit's camp (its standard's position) and hired master, if any. */
    public static final Supplier<AttachmentType<Long>> CAMP = ATTACHMENTS.register("camp",
            () -> AttachmentType.builder(() -> 0L).serialize(com.mojang.serialization.Codec.LONG).build());

    private ModAttachments() {
    }
}
