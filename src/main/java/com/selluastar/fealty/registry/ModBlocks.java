package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.block.BanditStandardBlock;
import com.selluastar.fealty.block.MailboxBlock;
import com.selluastar.fealty.block.RubbleBlock;
import com.selluastar.fealty.block.VillageCofferBlock;
import com.selluastar.fealty.block.WarBannerBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Fealty.MOD_ID);

    public static final DeferredBlock<VillageCofferBlock> VILLAGE_COFFER = BLOCKS.register("village_coffer",
            () -> new VillageCofferBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(4.0F, 12.0F)
                    .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<RubbleBlock> RUBBLE = BLOCKS.register("rubble",
            () -> new RubbleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(0.6F)
                    .sound(SoundType.GRAVEL)));
    public static final DeferredBlock<BanditStandardBlock> BANDIT_STANDARD = BLOCKS.register("bandit_standard",
            () -> new BanditStandardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().noCollission()));

    public static final DeferredBlock<WarBannerBlock> WAR_BANNER = BLOCKS.register("war_banner",
            () -> new WarBannerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(25.0F, 1200.0F)
                    .sound(SoundType.WOOD).noOcclusion().noCollission()));

    public static final DeferredBlock<MailboxBlock> MAILBOX = BLOCKS.register("mailbox",
            () -> new MailboxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD).noOcclusion()));

    private ModBlocks() {
    }
}
