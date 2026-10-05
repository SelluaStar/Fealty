package com.selluastar.fealty.entity;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.outlaw.TyrantEvent;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * The Tyrant Lord: seizes a castle village to catch a wanted outlaw. Three phases: at two thirds and one third of
 * his health he calls enforcers; in the last phase he is enraged and faster. A ground slam knocks back anyone close.
 */
public class TyrantLordEntity extends Monster {
    private static final ResourceLocation ENRAGED = Fealty.id("tyrant_enraged");
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.fealty.tyrant_lord"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    @Nullable
    private UUID quarry;
    @Nullable
    private ResourceLocation village;
    private int phase;
    private int slamCooldown = 100;

    public TyrantLordEntity(EntityType<? extends TyrantLordEntity> type, Level level) {
        super(type, level);
        xpReward = 150;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    public void setQuarry(ServerPlayer player, ResourceLocation village) {
        this.quarry = player.getUUID();
        this.village = village;
        setTarget(player);
    }

    @Nullable
    public ResourceLocation village() {
        return village;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> p instanceof ServerPlayer sp && !sp.isCreative() && !sp.isSpectator()
                        && (sp.getUUID().equals(quarry) || TyrantEvent.isHunted(sp))));
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        populateDefaultEquipmentSlots(getRandom(), difficulty);
        return result;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.TYRANT_CROWN.get()));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
        setDropChance(EquipmentSlot.HEAD, 2.0F);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        float fraction = getHealth() / getMaxHealth();
        if (phase == 0 && fraction < 0.66F) {
            phase = 1;
            summonEnforcers(2);
        } else if (phase == 1 && fraction < 0.33F) {
            phase = 2;
            summonEnforcers(3);
            AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && !speed.hasModifier(ENRAGED)) {
                speed.addPermanentModifier(new AttributeModifier(ENRAGED, 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        }
        LivingEntity target = getTarget();
        if (--slamCooldown <= 0 && target != null && distanceToSqr(target) < 16) {
            slam();
            slamCooldown = phase == 2 ? 70 : 120;
        }
    }

    private void slam() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 6, 1.5, 0.2, 1.5, 0.0);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.0),
                e -> e != this && e.isAlive() && !(e instanceof BountyHunterEntity));
        for (LivingEntity entity : hit) {
            entity.hurt(damageSources().mobAttack(this), 8.0F);
            Vec3 push = entity.position().subtract(position()).normalize().scale(1.4);
            entity.push(push.x, 0.6, push.z);
            entity.hurtMarked = true;
        }
    }

    private void summonEnforcers(int count) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.8F);
        ServerPlayer target = getTarget() instanceof ServerPlayer p ? p : null;
        for (int i = 0; i < count; i++) {
            BountyHunterEntity enforcer = ModEntities.BOUNTY_HUNTER.get().create(level);
            if (enforcer == null) {
                continue;
            }
            BlockPos pos = blockPosition().offset(getRandom().nextInt(7) - 3, 0, getRandom().nextInt(7) - 3);
            enforcer.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, getRandom().nextFloat() * 360F, 0);
            enforcer.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
            enforcer.setCustomName(Component.translatable("entity.fealty.enforcer"));
            if (target != null) {
                enforcer.hunt(target, level.getGameTime() + 6000, true);
            }
            level.addFreshEntity(enforcer);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel) {
            TyrantEvent.onTyrantSlain(this, source);
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
        if (village != null) {
            tag.putString("FealtyVillage", village.toString());
        }
        tag.putInt("FealtyPhase", phase);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        quarry = tag.hasUUID("FealtyQuarry") ? tag.getUUID("FealtyQuarry") : null;
        village = tag.contains("FealtyVillage") ? ResourceLocation.tryParse(tag.getString("FealtyVillage")) : null;
        phase = tag.getInt("FealtyPhase");
        if (hasCustomName()) {
            bossEvent.setName(getDisplayName());
        }
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.5F;
    }
}
