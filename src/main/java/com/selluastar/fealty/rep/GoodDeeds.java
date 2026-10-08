package com.selluastar.fealty.rep;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.block.VillageCofferBlockEntity;
import com.selluastar.fealty.crime.CrimeHandlers;
import com.selluastar.fealty.crime.PlacedBlockTracker;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.entity.VillageElderEntity;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Good deeds villages notice: fighting off what attacks them, healing their people, warning them with the bell,
 * mending their golems, lighting dark corners, giving them beds, tending their crops and filling their coffer.
 * Amounts and daily caps come from {@code fealty/rep_actions/<deed>.json}.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class GoodDeeds {
    /** Bandits and raiders killed this close to a village count as defending it. */
    private static final int NEAR_VILLAGE = 64;
    /** A bell rung with danger this close is a warning, not a nuisance. */
    private static final double BELL_DANGER_RADIUS = 32;
    /** A light placed with no other light this close brightens a dark corner. */
    private static final int DARK_RADIUS = 5;
    private static final int EMERALDS_PER_DONATION = 8;
    /** Emeralds given towards the next donation reward, per player and village. */
    private static final Map<String, Integer> DONATED = new HashMap<>();

    private GoodDeeds() {
    }

    private static void reward(ServerPlayer player, ResourceLocation village, ResourceLocation deed) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        RepManager.meet(player, village);
        RepManager.applySource(player, village, deed);
    }

    /** Villagers, the elder and the guards: the people a village counts as its own. */
    public static boolean isVillager(LivingEntity entity) {
        return entity instanceof Villager || entity instanceof VillageElderEntity || entity instanceof Mob mob && GuardManager.isGuard(mob);
    }

    private static Optional<ResourceLocation> villageOf(LivingEntity entity) {
        return FactionResolver.factionOf(entity).filter(Factions::isVillage);
    }

    // ---- Fighting for the village ----

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean bandit = victim.getType().is(FealtyTags.Entities.BANDITS);
        if (!bandit && !(victim instanceof Enemy)) {
            return;
        }
        if (victim instanceof Mob mob && mob.getTarget() != null && isVillager(mob.getTarget())) {
            LivingEntity saved = mob.getTarget();
            Optional<ResourceLocation> village = villageOf(saved);
            if (village.isPresent()) {
                reward(player, village.get(), RepSources.DEFEND_VILLAGER);
                if (saved.isAlive()) {
                    Speech.bark(saved, "thanks_rescue", player, 100);
                }
                return;
            }
        }
        if (bandit) {
            VillageResolver.nearestVillage(level, victim.blockPosition(), NEAR_VILLAGE)
                    .ifPresent(village -> reward(player, village.id(), RepSources.KILL_BANDIT));
        } else if (victim instanceof Raider) {
            VillageResolver.nearestVillage(level, victim.blockPosition(), NEAR_VILLAGE)
                    .ifPresent(village -> reward(player, village.id(), RepSources.KILL_RAIDER));
        } else {
            VillageResolver.villageAt(level, victim.blockPosition()).ifPresent(village -> reward(player, village.id(), RepSources.KILL_MONSTER));
        }
    }

    // ---- Potions: healing villagers is a kindness, poisoning them is an assault ----

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPotionImpact(ProjectileImpactEvent event) {
        if (event.isCanceled() || !(event.getProjectile() instanceof ThrownPotion potion) || !(potion.level() instanceof ServerLevel level)
                || !(potion.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        PotionContents contents = potion.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        boolean helps = false;
        boolean harms = false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            MobEffectCategory category = effect.getEffect().value().getCategory();
            helps |= category == MobEffectCategory.BENEFICIAL;
            harms |= category == MobEffectCategory.HARMFUL;
        }
        if (!helps && !harms) {
            return;
        }
        Vec3 at = event.getRayTraceResult().getLocation();
        Set<ResourceLocation> thanked = new HashSet<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(4.0, 2.0, 4.0),
                e -> e.isAlive() && isVillager(e))) {
            Optional<ResourceLocation> village = villageOf(target);
            if (village.isEmpty()) {
                continue;
            }
            if (harms) {
                CrimeHandlers.assault(player, target, village.get());
            } else if (target.getHealth() < target.getMaxHealth() && thanked.add(village.get())) {
                reward(player, village.get(), RepSources.HEAL_VILLAGER);
                Speech.bark(target, "thanks_healed", player, 100);
            }
        }
    }

    // ---- Helping around the village ----

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BellBlock) {
            if (inDanger(level, pos)) {
                VillageResolver.villageAt(level, pos).ifPresent(village -> reward(player, village.id(), RepSources.RING_BELL));
            }
        } else if (state.is(ModBlocks.VILLAGE_COFFER.get()) && event.getItemStack().is(Items.EMERALD) && !player.isSecondaryUseActive()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            donate(player, level, pos, event.getItemStack());
        }
    }

    /** Monsters, bandits or a raid near the bell. */
    private static boolean inDanger(ServerLevel level, BlockPos pos) {
        if (level.getRaidAt(pos) != null) {
            return true;
        }
        return !level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(BELL_DANGER_RADIUS),
                m -> m.isAlive() && (m instanceof Enemy || m.getType().is(FealtyTags.Entities.BANDITS))).isEmpty();
    }

    /** Using emeralds on a village coffer puts them in: the village's thanks for every few. */
    private static void donate(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
        if (!(level.getBlockEntity(pos) instanceof VillageCofferBlockEntity coffer)) {
            return;
        }
        Optional<VillageRecord> village = coffer.village() != null ? FealtyWorldData.get(player.server).village(coffer.village())
                : VillageResolver.villageAt(level, pos);
        if (village.isEmpty()) {
            return;
        }
        int offered = held.getCount();
        List<ItemStack> leftovers = coffer.deposit(List.of(held.copy()));
        int given = offered - leftovers.stream().mapToInt(ItemStack::getCount).sum();
        if (given <= 0) {
            player.displayClientMessage(Component.translatable("fealty.donate.full"), true);
            return;
        }
        if (!player.isCreative()) {
            held.shrink(given);
        }
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 1.6F);
        player.displayClientMessage(Component.translatable("fealty.donate.given", given, village.get().name()), true);
        if (village.get().lord().isLord(player.getUUID())) {
            return;
        }
        String key = player.getUUID() + "|" + village.get().id();
        int total = DONATED.getOrDefault(key, 0) + given;
        while (total >= EMERALDS_PER_DONATION) {
            total -= EMERALDS_PER_DONATION;
            reward(player, village.get().id(), RepSources.DONATE);
        }
        DONATED.put(key, total);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof IronGolem golem)
                || !event.getItemStack().is(Items.IRON_INGOT) || golem.getHealth() >= golem.getMaxHealth() || !GuardManager.isGuard(golem)) {
            return;
        }
        villageOf(golem).ifPresent(village -> reward(player, village, RepSources.REPAIR_GOLEM));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getPlacedBlock();
        BlockPos pos = event.getPos();
        ResourceLocation deed;
        if (state.getBlock() instanceof BedBlock) {
            deed = RepSources.PLACE_BED;
        } else if (state.getBlock() instanceof CropBlock) {
            deed = RepSources.TEND_CROPS;
        } else if (state.getLightEmission() >= 10 && !(state.getBlock() instanceof BaseFireBlock) && isDark(level, pos)) {
            deed = RepSources.LIGHT_VILLAGE;
        } else {
            return;
        }
        VillageResolver.villageAt(level, pos).ifPresent(village -> reward(player, village.id(), deed));
    }

    /** Whether no other light source is close to the spot. */
    private static boolean isDark(ServerLevel level, BlockPos pos) {
        for (BlockPos other : BlockPos.betweenClosed(pos.offset(-DARK_RADIUS, -3, -DARK_RADIUS), pos.offset(DARK_RADIUS, 3, DARK_RADIUS))) {
            if (!other.equals(pos) && level.getBlockState(other).getLightEmission() >= 10) {
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBonemeal(BonemealEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state) && !PlacedBlockTracker.isPlaced(level, event.getPos())) {
            VillageResolver.villageAt(level, event.getPos()).ifPresent(village -> reward(player, village.id(), RepSources.TEND_CROPS));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        String prefix = event.getEntity().getUUID() + "|";
        DONATED.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DONATED.clear();
    }
}
