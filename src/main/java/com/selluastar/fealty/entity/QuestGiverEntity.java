package com.selluastar.fealty.entity;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.entity.goal.TalkToPlayerGoal;
import com.selluastar.fealty.quest.QuestGiver;
import com.selluastar.fealty.quest.QuestGivers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Base for NPCs that hand out quests. They stay near home, open doors, flee monsters and never despawn. */
public abstract class QuestGiverEntity extends PathfinderMob {
    @Nullable
    private BlockPos home;

    protected QuestGiverEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(true);
        }
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createBaseAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new TalkToPlayerGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 0.75));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Monster.class, 8.0F, 0.6, 0.75));
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.4));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public abstract QuestGiver giver();

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !isAlive()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            getLookControl().setLookAt(player);
            onInteract(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    protected void onInteract(ServerPlayer player) {
        if (!com.selluastar.fealty.dialogue.DialogueService.open(player, this)) {
            QuestGivers.open(player, this);
        }
    }

    public void setHome(BlockPos pos) {
        this.home = pos.immutable();
        restrictTo(this.home, 10);
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) {
            tag.put("FealtyHome", NbtUtils.writeBlockPos(home));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "FealtyHome").ifPresent(this::setHome);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }
}
