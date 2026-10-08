package com.selluastar.fealty.entity;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.entity.goal.FollowMasterGoal;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageNames;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Bandits of the camps. Hostile to honest folk, neutral to outlaws (Renown at or below Distrusted) unless the
 * player has turned the bandits against them, and for hire by Hated players.
 */
public class BanditEntity extends Monster implements RangedAttackMob {
    private final RangedBowAttackGoal<BanditEntity> bowGoal = new RangedBowAttackGoal<>(this, 1.0, 20, 15.0F);
    private final MeleeAttackGoal meleeGoal = new MeleeAttackGoal(this, 1.2, false);
    @Nullable
    private UUID master;

    public BanditEntity(EntityType<? extends BanditEntity> type, Level level) {
        super(type, level);
        xpReward = 8;
        reassessWeaponGoal();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    public static AttributeSupplier.Builder createCaptainAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    public boolean isCaptain() {
        return getType() == ModEntities.BANDIT_CAPTAIN.get();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new FollowMasterGoal(this, 1.1, 6.0F, 2.5F));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(BanditEntity.class));
        targetSelector.addGoal(2, new DefendMasterGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> p instanceof ServerPlayer sp && isHostileTo(sp)));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                m -> master != null && m instanceof Mob mob && GuardManager.isGuard(mob) && masterIsOutlawHere(mob)));
    }

    /** Use the bow goal when holding a bow, the melee goal otherwise. */
    public void reassessWeaponGoal() {
        if (level() == null || level().isClientSide) {
            return;
        }
        goalSelector.removeGoal(meleeGoal);
        goalSelector.removeGoal(bowGoal);
        if (getMainHandItem().getItem() instanceof BowItem) {
            bowGoal.setMinAttackInterval(level().getDifficulty() == net.minecraft.world.Difficulty.HARD ? 20 : 40);
            goalSelector.addGoal(2, bowGoal);
        } else {
            goalSelector.addGoal(2, meleeGoal);
        }
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        super.setItemSlot(slot, stack);
        if (!level().isClientSide) {
            reassessWeaponGoal();
        }
    }

    // ---- Hostility ----

    public boolean isHostileTo(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (master != null) {
            return false;
        }
        boolean outlaw = RepManager.renown(player) <= FealtyConfig.BLACK_MARKET_MAX_RENOWN.get();
        boolean banditsHateYou = RepManager.getTier(player, Factions.BANDITS).rank()
                < com.selluastar.fealty.data.TierManager.neutral().rank();
        return !outlaw || banditsHateYou;
    }

    private boolean masterIsOutlawHere(Mob guard) {
        if (!(level() instanceof ServerLevel server) || master == null) {
            return false;
        }
        ServerPlayer owner = server.getServer().getPlayerList().getPlayer(master);
        if (owner == null || owner.distanceToSqr(this) > 24 * 24) {
            return false;
        }
        Optional<ResourceLocation> faction = FactionResolver.factionOf(guard);
        return faction.isPresent() && GuardManager.isHostileTo(faction.get(), owner);
    }

    // ---- Followers ----

    @Nullable
    public UUID master() {
        return master;
    }

    @Nullable
    public LivingEntity masterEntity() {
        return master != null ? level().getPlayerByUUID(master) : null;
    }

    public void setMaster(@Nullable UUID master) {
        this.master = master;
        if (master != null) {
            setPersistenceRequired();
            removeData(ModAttachments.CAMP);
            clearRestriction();
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer) || isCaptain()) {
            return super.mobInteract(player, hand);
        }
        if (master != null && master.equals(player.getUUID()) && player.isSecondaryUseActive()) {
            setMaster(null);
            setTarget(null);
            serverPlayer.displayClientMessage(Component.translatable("fealty.bandit.dismissed", getDisplayName()), true);
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (master == null && held.is(Items.EMERALD) && getTarget() != player) {
            return tryHire(serverPlayer, held) ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }

    private boolean tryHire(ServerPlayer player, ItemStack emeralds) {
        if (RepManager.renown(player) > FealtyConfig.FOLLOWERS_MAX_RENOWN.get()) {
            player.displayClientMessage(Component.translatable("fealty.bandit.not_infamous"), true);
            return false;
        }
        long followers = player.serverLevel().getEntitiesOfClass(BanditEntity.class, player.getBoundingBox().inflate(128),
                b -> player.getUUID().equals(b.master)).size();
        if (followers >= FealtyConfig.MAX_FOLLOWERS.get()) {
            player.displayClientMessage(Component.translatable("fealty.bandit.too_many"), true);
            return false;
        }
        int cost = FealtyConfig.FOLLOWER_COST.get();
        if (emeralds.getCount() < cost) {
            player.displayClientMessage(Component.translatable("fealty.bandit.cost", cost), true);
            return false;
        }
        emeralds.shrink(cost);
        setMaster(player.getUUID());
        setTarget(null);
        if (!hasCustomName()) {
            setCustomName(Component.literal(VillageNames.personName(getRandom())));
        }
        player.displayClientMessage(Component.translatable("fealty.bandit.hired", getDisplayName()), true);
        FealtyEvents.fire(player, FealtyEvents.FOLLOWER_HIRED);
        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && master != null && tickCount % 600 == 0) {
            ServerPlayer owner = ((ServerLevel) level()).getServer().getPlayerList().getPlayer(master);
            if (owner != null && RepManager.renown(owner) > FealtyConfig.FOLLOWERS_MAX_RENOWN.get() + 20) {
                // An outlaw gone respectable: the bandit wants no part of it.
                owner.displayClientMessage(Component.translatable("fealty.bandit.deserted", getDisplayName()), false);
                setMaster(null);
            }
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (master != null && master.equals(target.getUUID())) {
            return false;
        }
        if (target instanceof BanditEntity) {
            return false;
        }
        return super.canAttack(target);
    }

    // ---- Combat ----

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack weapon = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack ammo = getProjectile(weapon);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, velocity, weapon);
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + distance * 0.2, dz, 1.6F, (float) (14 - level().getDifficulty().getId() * 4));
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        populateDefaultEquipmentSlots(getRandom(), difficulty);
        reassessWeaponGoal();
        if (isCaptain()) {
            setCustomName(Component.translatable("entity.fealty.bandit_captain.named", VillageNames.personName(getRandom())));
        }
        return result;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (isCaptain()) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
            setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            return;
        }
        float roll = random.nextFloat();
        if (roll < 0.4F) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        } else if (roll < 0.75F) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        } else {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
        }
        if (random.nextFloat() < 0.5F) {
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (isCaptain() && hasData(ModAttachments.CAMP) && level() instanceof ServerLevel level) {
            BlockPos standard = BlockPos.of(getData(ModAttachments.CAMP));
            if (level.isLoaded(standard) && level.getBlockEntity(standard) instanceof com.selluastar.fealty.block.BanditStandardBlockEntity camp) {
                camp.markCleared(level.getGameTime());
            }
            if (source.getEntity() instanceof ServerPlayer killer) {
                FealtyEvents.fire(killer, FealtyEvents.CAMP_CLEARED);
            }
        }
    }

    // ---- Persistence ----

    @Override
    public boolean removeWhenFarAway(double distance) {
        return master == null && !hasData(ModAttachments.CAMP) && super.removeWhenFarAway(distance);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (master != null) {
            tag.putUUID("FealtyMaster", master);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        master = tag.hasUUID("FealtyMaster") ? tag.getUUID("FealtyMaster") : null;
        reassessWeaponGoal();
        if (hasData(ModAttachments.CAMP)) {
            restrictTo(BlockPos.of(getData(ModAttachments.CAMP)), 20);
        }
    }

    /** Remember the camp (its standard) this bandit belongs to and stay near it. */
    public void bindToCamp(BlockPos standard) {
        setData(ModAttachments.CAMP, standard.asLong());
        restrictTo(standard, 20);
        setPersistenceRequired();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    /** Hired bandits fight whoever hurts their master, and whoever their master attacks. */
    static final class DefendMasterGoal extends TargetGoal {
        private final BanditEntity bandit;
        @Nullable
        private LivingEntity candidate;
        private int lastHurtBy;
        private int lastHurt;

        DefendMasterGoal(BanditEntity bandit) {
            super(bandit, false);
            this.bandit = bandit;
        }

        @Override
        public boolean canUse() {
            LivingEntity master = bandit.masterEntity();
            if (master == null) {
                return false;
            }
            LivingEntity attacker = master.getLastHurtByMob();
            if (attacker != null && master.getLastHurtByMobTimestamp() != lastHurtBy && attacker != bandit) {
                candidate = attacker;
                lastHurtBy = master.getLastHurtByMobTimestamp();
                return canAttack(candidate, TargetingConditions.DEFAULT);
            }
            LivingEntity victim = master.getLastHurtMob();
            if (victim != null && master.getLastHurtMobTimestamp() != lastHurt && !(victim instanceof BanditEntity)) {
                candidate = victim;
                lastHurt = master.getLastHurtMobTimestamp();
                return canAttack(candidate, TargetingConditions.DEFAULT);
            }
            return false;
        }

        @Override
        public void start() {
            mob.setTarget(candidate);
            targetMob = candidate;
            super.start();
        }
    }

    static boolean isBandit(LivingEntity entity) {
        return entity.getType().is(FealtyTags.Entities.BANDITS);
    }
}
