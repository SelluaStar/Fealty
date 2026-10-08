package com.selluastar.fealty.block;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.selluastar.fealty.registry.ModBlockEntities;
import com.selluastar.fealty.war.Strongholds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The ominous banner at the heart of a pillager camp. It keeps the camp's garrison and fills its cages; when a
 * lord's warband razes the camp it hangs in tatters until the pillagers return.
 */
public class WarBannerBlock extends BaseEntityBlock {
    public static final MapCodec<WarBannerBlock> CODEC = simpleCodec(WarBannerBlock::new);
    public static final BooleanProperty RAZED = BooleanProperty.create("razed");
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

    public WarBannerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RAZED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RAZED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Pulling down a camp's banner ends the camp for good. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof WarBannerBlockEntity banner && banner.isNatural()) {
            Strongholds strongholds = Strongholds.get(server.getServer());
            Strongholds.Entry entry = strongholds.at(server.dimension(), pos, 2);
            if (entry != null) {
                strongholds.remove(entry);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WarBannerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.WAR_BANNER.get(), WarBannerBlockEntity::serverTick);
    }
}
