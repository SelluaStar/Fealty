package com.selluastar.fealty.entity;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.dialogue.DialogueService;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.entity.goal.DefendVillagersGoal;
import com.selluastar.fealty.entity.goal.GuardBowGoal;
import com.selluastar.fealty.entity.goal.TalkToPlayerGoal;
import com.selluastar.fealty.guard.Garrison;
import com.selluastar.fealty.guard.GarrisonManager;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.guard.GuardOrders;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A guard of a village's own watch: a swordsman, an archer or a sergeant in the village's colours. Each guard
 * patrols their own beat in the village (closer to it at night), defend villagers, fight monsters and raiders, turn on players their
 * village is hostile to, and obey the village's lord. Each one holds a slot in the village's {@link Garrison}.
 */
public class VillageGuardEntity extends PathfinderMob implements RangedAttackMob {
    private static final EntityDataAccessor<Integer> DATA_RANK = SynchedEntityData.defineId(VillageGuardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(VillageGuardEntity.class, EntityDataSerializers.INT);
    public static final int PATROL_RADIUS = 24;
    private static final int NIGHT_RADIUS = 10;

    @Nullable
    private ResourceLocation village;
    private int slot = -1;
    private int generation;
    @Nullable
    private BlockPos post;
    private int upkeep;
    private int leaderGone;
    /** Whether this guard's beat has been checked since it was loaded (see {@code GarrisonManager.tickVillage}). */
    public boolean beatChecked;
    private int returning;
    @Nullable
    private MeleeAttackGoal meleeGoal;
    @Nullable
    private GuardBowGoal bowGoal;

    public VillageGuardEntity(EntityType<? extends VillageGuardEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            setDropChance(equipmentSlot, 0.0F);
        }
        if (getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(true);
        }
        reassessWeaponGoal();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RANK, 0);
        builder.define(DATA_COLOR, 0x1565C0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new TalkToPlayerGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.9));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, VillageGuardEntity.class, AbstractVillager.class).setAlertOthers());
        targetSelector.addGoal(3, new DefendVillagersGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
                e -> e instanceof Enemy && !(e instanceof Creeper)));
    }

    /** Bow for archers, sword for the rest. */
    public void reassessWeaponGoal() {
        if (level().isClientSide) {
            return;
        }
        if (meleeGoal == null || bowGoal == null) {
            meleeGoal = new MeleeAttackGoal(this, 1.15, true);
            bowGoal = new GuardBowGoal(this, 1.0, 30, 16.0F);
        }
        goalSelector.removeGoal(meleeGoal);
        goalSelector.removeGoal(bowGoal);
        goalSelector.addGoal(2, getMainHandItem().getItem() instanceof BowItem ? bowGoal : meleeGoal);
    }

    @Override
    public void setItemSlot(EquipmentSlot equipmentSlot, ItemStack stack) {
        super.setItemSlot(equipmentSlot, stack);
        if (!level().isClientSide && equipmentSlot == EquipmentSlot.MAINHAND) {
            reassessWeaponGoal();
        }
    }

    // ---- Who this guard is ----

    public Garrison.Rank rank() {
        return Garrison.Rank.byId(entityData.get(DATA_RANK));
    }

    /** The village's colour, worn on the tabard. */
    public int color() {
        return entityData.get(DATA_COLOR);
    }

    @Nullable
    public ResourceLocation village() {
        return village;
    }

    public int slot() {
        return slot;
    }

    public int generation() {
        return generation;
    }

    @Nullable
    public BlockPos post() {
        return post;
    }

    /** Move the guard's beat (where they patrol and stand at night). */
    public void setPost(BlockPos post) {
        this.post = post.immutable();
        updateRestriction();
    }

    /** Take up a slot in a village's watch: colours, rank, gear, name and post. */
    public void enlist(VillageRecord record, int slotIndex, Garrison.Slot slotData, BlockPos post, String name) {
        this.village = record.id();
        this.slot = slotIndex;
        this.generation = slotData.generation();
        this.post = post.immutable();
        setData(ModAttachments.FACTION, record.id());
        entityData.set(DATA_COLOR, record.color());
        setRank(slotData.rank());
        setCustomName(Component.translatable("entity.fealty.village_guard." + slotData.rank().getSerializedName() + ".named", name));
        updateRestriction();
    }

    /** A guard that joins a village without a slot (from a spawn egg): its colours, but no place on the roster. */
    public void joinVillage(VillageRecord record) {
        setData(ModAttachments.FACTION, record.id());
        entityData.set(DATA_COLOR, record.color());
        this.post = blockPosition().immutable();
        updateRestriction();
    }

    public void setRank(Garrison.Rank rank) {
        entityData.set(DATA_RANK, rank.ordinal());
        double health = switch (rank) {
            case SWORDSMAN -> 30.0;
            case ARCHER -> 24.0;
            case SERGEANT -> 40.0;
        };
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(rank == Garrison.Rank.SERGEANT ? 2.0 : 1.0);
        getAttribute(Attributes.ARMOR).setBaseValue(switch (rank) {
            case SWORDSMAN -> 6.0;
            case ARCHER -> 4.0;
            case SERGEANT -> 9.0;
        });
        setHealth(getMaxHealth());
        if (rank == Garrison.Rank.ARCHER) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        } else {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
    }

    public GuardOrders orders() {
        return getData(ModAttachments.GUARD_ORDERS);
    }

    /** Whether the guard is under orders that take it away from its post. */
    public boolean hasOrders() {
        GuardOrders orders = orders();
        return orders.isActive(level().getGameTime()) && orders.mode() != GuardOrders.Mode.DEFEND;
    }

    // ---- Ticking ----

    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
        if (level() instanceof ServerLevel level && isAlive()) {
            serverStep(level);
        }
    }

    private void serverStep(ServerLevel level) {
        if (--upkeep <= 0) {
            upkeep = 40;
            if (!GarrisonManager.checkHolder(level, this)) {
                return;
            }
            followUpOrders(level);
            updateRestriction();
        }
        if (tickCount % 50 == 0 && getTarget() == null && getHealth() < getMaxHealth()) {
            heal(1.0F);
        }
    }

    /** Patrol near the post (closer at night), keep watch near a spot, or roam free while following someone. */
    private void updateRestriction() {
        GuardOrders orders = orders();
        long now = level().getGameTime();
        if (orders.isActive(now) && (orders.mode() == GuardOrders.Mode.HOLD || orders.mode() == GuardOrders.Mode.GUARD)
                && orders.holdPos() != null) {
            restrictTo(orders.holdPos(), orders.mode() == GuardOrders.Mode.HOLD ? 2 : 8);
        } else if (orders.isActive(now) && orders.mode() != GuardOrders.Mode.DEFEND) {
            clearRestriction();
        } else if (post != null) {
            restrictTo(post, level().isNight() ? NIGHT_RADIUS : PATROL_RADIUS);
        }
    }

    /** Orders lapse when whoever gave them is gone for a while; a guard far from home then goes back. */
    private void followUpOrders(ServerLevel level) {
        GuardOrders orders = orders();
        if (!orders.isActive(level.getGameTime()) || orders.mode() == GuardOrders.Mode.DEFEND) {
            leaderGone = 0;
            returning = 0;
            if (orders.mode() != GuardOrders.Mode.NONE) {
                orders.clear();
            }
            if (village != null && !GarrisonManager.inHomeDimension(level, this) && slot >= 0) {
                GarrisonManager.sendHome(level, this);
            }
            return;
        }
        ServerPlayer leader = orders.leader() != null ? level.getServer().getPlayerList().getPlayer(orders.leader()) : null;
        boolean present = leader != null && leader.isAlive() && leader.level() == level;
        if (orders.mode() == GuardOrders.Mode.RETURN) {
            returning += 40;
            if (post != null && GarrisonManager.inHomeDimension(level, this) && blockPosition().distSqr(post) < 40 * 40) {
                orders.clear();
                returning = 0;
                return;
            }
            boolean outOfSight = !present || (distanceToSqr(leader) > 24 * 24 && !leader.hasLineOfSight(this));
            if (outOfSight || returning >= 600) {
                GarrisonManager.sendHome(level, this);
                return;
            }
            if (getNavigation().isDone()) {
                net.minecraft.world.phys.Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(this, 16, 7, leader.position());
                if (away != null) {
                    getNavigation().moveTo(away.x, away.y, away.z, 1.0);
                }
            }
            return;
        }
        leaderGone = present ? 0 : leaderGone + 40;
        int patience = orders.mode() == GuardOrders.Mode.FOLLOW || orders.mode() == GuardOrders.Mode.ESCORT ? 200 : 1200;
        if (leaderGone >= patience) {
            leaderGone = 0;
            orders.clear();
            if (slot >= 0 && (!GarrisonManager.inHomeDimension(level, this) || post == null || blockPosition().distSqr(post) > 64 * 64)) {
                GarrisonManager.sendHome(level, this);
            }
        }
    }

    // ---- Combat ----

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof AbstractVillager || target instanceof VillageGuardEntity || target instanceof IronGolem) {
            return false;
        }
        if (target instanceof Mob mob && GuardManager.isGuard(mob)) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack weapon = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, new ItemStack(Items.ARROW), velocity, weapon);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + distance * 0.2, dz, 1.6F, 6.0F);
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            GarrisonManager.onDied(level, this, source);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (getTarget() == serverPlayer || GuardManager.isHostileTo(this, serverPlayer)) {
                Speech.bark(this, "guard_hostile", serverPlayer, 100);
                return InteractionResult.CONSUME;
            }
            DialogueService.open(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // ---- Persistence ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (village != null) {
            tag.putString("Village", village.toString());
        }
        tag.putInt("Slot", slot);
        tag.putInt("Generation", generation);
        tag.putInt("Rank", entityData.get(DATA_RANK));
        tag.putInt("Color", color());
        if (post != null) {
            tag.put("Post", NbtUtils.writeBlockPos(post));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        village = tag.contains("Village") ? ResourceLocation.tryParse(tag.getString("Village")) : null;
        slot = tag.contains("Slot") ? tag.getInt("Slot") : -1;
        generation = tag.getInt("Generation");
        entityData.set(DATA_RANK, tag.getInt("Rank"));
        if (tag.contains("Color")) {
            entityData.set(DATA_COLOR, tag.getInt("Color"));
        }
        post = NbtUtils.readBlockPos(tag, "Post").orElse(null);
        updateRestriction();
        reassessWeaponGoal();
    }
}
