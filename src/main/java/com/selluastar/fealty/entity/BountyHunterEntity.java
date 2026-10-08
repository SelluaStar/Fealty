package com.selluastar.fealty.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.outlaw.TyrantEvent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Hunts one wanted player for the village that put a price on them, and gives up after a while. The Tyrant
 * Lord's enforcers are bounty hunters too, set on whoever he is after.
 */
public class BountyHunterEntity extends BanditEntity {
    @Nullable
    private UUID quarry;
    private long expireAt;
    private boolean enforcer;

    public BountyHunterEntity(EntityType<? extends BountyHunterEntity> type, Level level) {
        super(type, level);
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    public void hunt(ServerPlayer player, long until, boolean asEnforcer) {
        this.quarry = player.getUUID();
        this.expireAt = until;
        this.enforcer = asEnforcer;
        setPersistenceRequired();
        setTarget(player);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 5, false, false, this::isQuarry));
    }

    private boolean isQuarry(net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
            return false;
        }
        return player.getUUID().equals(quarry) || (enforcer && TyrantEvent.isHunted(player));
    }

    @Override
    public boolean isHostileTo(ServerPlayer player) {
        return isQuarry(player);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel level && tickCount % 100 == 0) {
            boolean expired = expireAt > 0 && level.getGameTime() > expireAt && getTarget() == null;
            ServerPlayer target = quarry != null ? level.getServer().getPlayerList().getPlayer(quarry) : null;
            boolean lost = !enforcer && (target == null || target.level() != level || target.distanceToSqr(this) > 160 * 160);
            if (expired || lost) {
                discard();
            }
        }
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextFloat() < 0.5F ? Items.BOW : Items.IRON_SWORD));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(enforcer ? Items.IRON_CHESTPLATE : Items.LEATHER_CHESTPLATE));
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (source.getEntity() instanceof ServerPlayer player && player.getUUID().equals(quarry)) {
            FealtyEvents.fire(player, FealtyEvents.BOUNTY_HUNTER_SLAIN);
            player.displayClientMessage(Component.translatable("fealty.bounty.slain"), true);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (quarry != null) {
            tag.putUUID("FealtyQuarry", quarry);
        }
        tag.putLong("FealtyExpire", expireAt);
        tag.putBoolean("FealtyEnforcer", enforcer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        quarry = tag.hasUUID("FealtyQuarry") ? tag.getUUID("FealtyQuarry") : null;
        expireAt = tag.getLong("FealtyExpire");
        enforcer = tag.getBoolean("FealtyEnforcer");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VINDICATOR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VINDICATOR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VINDICATOR_DEATH;
    }
}
