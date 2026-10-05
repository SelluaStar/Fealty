package com.selluastar.fealty.crime;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.block.VillageCofferBlockEntity;
import com.selluastar.fealty.entity.VillageElderEntity;
import com.selluastar.fealty.item.LockpickItem;
import com.selluastar.fealty.quest.QuestEvents;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Turns things players do in villages into crimes. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class CrimeHandlers {
    private static final Map<UUID, Click> LAST_CLICK = new HashMap<>();
    private static final Map<UUID, OpenContainer> OPEN = new HashMap<>();
    private static final Map<String, Long> HIT_COOLDOWNS = new HashMap<>();
    private static final int HIT_COOLDOWN_TICKS = 40;

    private CrimeHandlers() {
    }

    private record Click(BlockPos pos, long time) {
    }

    private record OpenContainer(AbstractContainerMenu menu, BlockPos pos, ResourceLocation village, Map<Item, Integer> before,
                                 boolean coffer, boolean loud) {
    }

    // ---- Breaking and placing ----

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        QuestEvents.onBlockBroken(player, level, pos, state);
        Optional<VillageRecord> village = VillageResolver.villageAt(level, pos);
        if (village.isEmpty()) {
            return;
        }
        if (PlacedBlockTracker.removeIfPlaced(level, pos) || village.get().lord().isLord(player.getUUID())) {
            return;
        }
        ResourceLocation crime = null;
        if (state.is(ModBlocks.VILLAGE_COFFER.get())) {
            BlockEntity be = level.getBlockEntity(pos);
            crime = be instanceof VillageCofferBlockEntity coffer && !coffer.isEmpty() ? RepSources.VAULT_RAID : RepSources.BREAK_BLOCK;
        } else if (isVillageProperty(state)) {
            crime = RepSources.BREAK_BLOCK;
        }
        if (crime != null) {
            CrimeService.commit(player, village.get().id(), crime, pos, null, false);
        }
    }

    public static boolean isVillageProperty(BlockState state) {
        return state.is(FealtyTags.Blocks.VILLAGE_PROPERTY) || PoiTypes.forState(state).isPresent();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                trackPlacement(level, snapshot.getPos());
            }
        } else {
            trackPlacement(level, event.getPos());
        }
        QuestEvents.onBlockPlaced(player, level, event.getPos(), event.getPlacedBlock());
    }

    private static void trackPlacement(ServerLevel level, BlockPos pos) {
        if (VillageResolver.villageAt(level, pos).isPresent()) {
            PlacedBlockTracker.markPlaced(level, pos);
        }
    }

    // ---- Theft ----

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_CLICK.put(player.getUUID(), new Click(event.getPos().immutable(), player.level().getGameTime()));
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Click click = LAST_CLICK.remove(player.getUUID());
        ServerLevel level = player.serverLevel();
        if (click == null || level.getGameTime() - click.time() > 2 || player.isCreative() || player.isSpectator()) {
            return;
        }
        BlockState state = level.getBlockState(click.pos());
        boolean coffer = state.is(ModBlocks.VILLAGE_COFFER.get());
        if (!coffer && !state.is(FealtyTags.Blocks.VILLAGE_CONTAINERS)) {
            return;
        }
        Optional<VillageRecord> village = VillageResolver.villageAt(level, click.pos());
        if (village.isEmpty() || village.get().lord().isLord(player.getUUID()) || PlacedBlockTracker.isPlaced(level, click.pos())) {
            return;
        }
        boolean loud = false;
        if (coffer) {
            ItemStack pick = LockpickItem.held(player);
            if (pick.isEmpty()) {
                loud = true;
                level.playSound(null, click.pos(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.5F);
            } else {
                EquipmentSlot slot = player.getMainHandItem() == pick ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                pick.hurtAndBreak(1, player, slot);
            }
        }
        AbstractContainerMenu menu = event.getContainer();
        OPEN.put(player.getUUID(), new OpenContainer(menu, click.pos(), village.get().id(), count(menu, player), coffer, loud));
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        OpenContainer open = OPEN.remove(player.getUUID());
        if (open == null || open.menu() != event.getContainer()) {
            return;
        }
        Map<Item, Integer> after = count(open.menu(), player);
        Map<Item, Integer> stolen = new HashMap<>();
        int total = 0;
        for (Map.Entry<Item, Integer> entry : open.before().entrySet()) {
            int taken = entry.getValue() - after.getOrDefault(entry.getKey(), 0);
            if (taken > 0) {
                stolen.put(entry.getKey(), taken);
                total += taken;
            }
        }
        if (total <= 0) {
            return;
        }
        ResourceLocation crime = open.coffer() ? RepSources.VAULT_RAID : RepSources.STEAL;
        CrimeService.Result result = CrimeService.commit(player, open.village(), crime, open.pos(), null, open.loud());
        if (!result.witnessed()) {
            FealtyEvents.fire(player, FealtyEvents.UNSEEN_THEFT);
        }
        if (open.coffer()) {
            FealtyEvents.fire(player, FealtyEvents.VAULT_RAIDED);
        }
        QuestEvents.onTheft(player, open.village(), stolen, result.witnessed(), open.coffer());
    }

    private static Map<Item, Integer> count(AbstractContainerMenu menu, ServerPlayer player) {
        Map<Item, Integer> counts = new HashMap<>();
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_CLICK.remove(event.getEntity().getUUID());
        OPEN.remove(event.getEntity().getUUID());
    }

    // ---- Violence ----

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !(event.getSource().getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) {
            return;
        }
        if (!isVillageMember(victim)) {
            return;
        }
        if (victim instanceof Mob mob && mob.getTarget() == player) {
            return; // self-defence against a guard that is already attacking
        }
        Optional<ResourceLocation> faction = FactionResolver.factionOf(victim);
        if (faction.isEmpty() || Factions.BANDITS.equals(faction.get())) {
            return;
        }
        String key = victim.getUUID() + "|" + player.getUUID();
        long now = player.level().getGameTime();
        Long last = HIT_COOLDOWNS.get(key);
        if (last != null && now - last < HIT_COOLDOWN_TICKS) {
            return;
        }
        HIT_COOLDOWNS.put(key, now);
        if (HIT_COOLDOWNS.size() > 512) {
            HIT_COOLDOWNS.entrySet().removeIf(e -> now - e.getValue() > HIT_COOLDOWN_TICKS);
        }
        CrimeService.commit(player, faction.get(), RepSources.HIT_VILLAGER, victim.blockPosition(), victim, false);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        QuestEvents.onEntityDeath(victim, killer);
        if (killer == null || !(isVillageMember(victim) || victim.getType().is(FealtyTags.Entities.BANDITS))) {
            return;
        }
        Optional<ResourceLocation> faction = FactionResolver.factionOf(victim);
        if (faction.isEmpty()) {
            return;
        }
        ResourceLocation crime;
        if (victim instanceof VillageElderEntity) {
            crime = RepSources.KILL_ELDER;
        } else if (FactionResolver.isGuard(victim)) {
            crime = RepSources.KILL_GUARD;
        } else if (victim instanceof Villager) {
            crime = RepSources.KILL_VILLAGER;
        } else {
            crime = RepSources.KILL_MEMBER;
        }
        CrimeService.commit(killer, faction.get(), crime, victim.blockPosition(), null, false);
    }

    /** Villagers, guards and other village members (player-built golems belong to the player, not the village). */
    private static boolean isVillageMember(LivingEntity entity) {
        if (entity instanceof IronGolem golem && golem.isPlayerCreated()) {
            return false;
        }
        return entity instanceof Villager || entity instanceof VillageElderEntity || FactionResolver.isGuard(entity)
                || entity.getType().is(FealtyTags.Entities.VILLAGE_MEMBERS);
    }
}
