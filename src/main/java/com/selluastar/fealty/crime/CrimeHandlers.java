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
import com.selluastar.fealty.quest.QuestEvents;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;
import com.selluastar.fealty.village.VillageTracker;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Turns things players do in villages into crimes. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class CrimeHandlers {
    private static final Map<UUID, Click> LAST_CLICK = new HashMap<>();
    private static final Map<UUID, OpenContainer> OPEN = new HashMap<>();
    private static final Map<String, Long> HIT_COOLDOWNS = new HashMap<>();
    private static final int HIT_COOLDOWN_TICKS = 40;
    private static final Map<String, Long> TRAMPLES = new HashMap<>();
    private static final int TRAMPLE_COOLDOWN = 100;
    private static final Map<String, Long> EXPLOSIONS = new HashMap<>();
    private static final int EXPLOSION_COOLDOWN = 100;
    private static final Map<String, Integer> HARVESTS = new HashMap<>();
    private static final int CROPS_PER_HARVEST = 8;
    /** When each player was first found in a villager's home at night. */
    private static final Map<UUID, Long> TRESPASSING = new HashMap<>();
    private static final int TRESPASS_GRACE = 300;
    private static final int TRESPASS_REPEAT = 1200;
    /** Scoreboard tag on animals bred by players. */
    public static final String BRED_TAG = "fealty.bred";

    private CrimeHandlers() {
    }

    private record Click(BlockPos pos, long time, boolean crouching) {
    }

    /** A village container a player has open. Theft is judged the moment the first item leaves it. */
    private static final class OpenContainer {
        final AbstractContainerMenu menu;
        final BlockPos pos;
        final ResourceLocation village;
        final Map<Item, Integer> before;
        final boolean coffer;
        /** Whether the player was sneaking when they opened it (the screen lets go of the sneak key). */
        final boolean crouching;
        boolean judged;
        boolean witnessed;
        ContainerListener listener;

        OpenContainer(AbstractContainerMenu menu, BlockPos pos, ResourceLocation village, Map<Item, Integer> before, boolean coffer,
                      boolean crouching) {
            this.menu = menu;
            this.pos = pos;
            this.village = village;
            this.before = before;
            this.coffer = coffer;
            this.crouching = crouching;
        }
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
            crime = be instanceof VillageCofferBlockEntity coffer && !coffer.isEmpty() ? RepSources.VAULT_RAID : RepSources.BREAK_FIXTURE;
        } else if (state.getBlock() instanceof BellBlock || state.is(ModBlocks.MAILBOX.get())) {
            crime = RepSources.BREAK_FIXTURE;
        } else if (state.getBlock() instanceof CropBlock) {
            // Every few crops taken from the village's fields.
            String key = player.getUUID() + "|" + village.get().id();
            int taken = HARVESTS.merge(key, 1, Integer::sum);
            if (taken >= CROPS_PER_HARVEST) {
                HARVESTS.remove(key);
                crime = RepSources.HARVEST_CROPS;
            }
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
        if (event.getPlacedBlock().getBlock() instanceof BaseFireBlock) {
            arson(player, level, event.getPos());
        }
    }

    /** Pouring lava in a village is arson too. */
    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getItemStack().is(Items.LAVA_BUCKET)) {
            return;
        }
        if (player.pick(player.blockInteractionRange(), 1.0F, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            arson(player, player.serverLevel(), hit.getBlockPos().relative(hit.getDirection()));
        }
    }

    /** Fire set against something that burns, in a village the player does not rule. */
    private static void arson(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Optional<VillageRecord> village = VillageResolver.villageAt(level, pos);
        if (village.isEmpty() || village.get().lord().isLord(player.getUUID())) {
            return;
        }
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.getBlockState(near).ignitedByLava() && !PlacedBlockTracker.isPlaced(level, near)) {
                CrimeService.commit(player, village.get().id(), RepSources.ARSON, pos, null, false);
                return;
            }
        }
    }

    // ---- Fields, beds and homes ----

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Optional<VillageRecord> village = VillageResolver.villageAt(level, event.getPos());
        if (village.isEmpty() || village.get().lord().isLord(player.getUUID()) || PlacedBlockTracker.isPlaced(level, event.getPos())) {
            return;
        }
        if (cooldown(TRAMPLES, player.getUUID().toString(), level.getGameTime(), TRAMPLE_COOLDOWN)) {
            CrimeService.commit(player, village.get().id(), RepSources.TRAMPLE_CROPS, event.getPos(), null, false);
        }
    }

    /** Sleeping in a bed a villager calls home, uninvited (Trusted friends are welcome to it). */
    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (event.getProblem() != null || player.isCreative() || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        Optional<VillageRecord> village = VillageResolver.villageAt(level, pos);
        if (village.isEmpty() || village.get().lord().isLord(player.getUUID()) || PlacedBlockTracker.isPlaced(level, pos)
                || RepManager.getTier(player, village.get().id()).rank() >= TierManager.trusted().rank()) {
            return;
        }
        Optional<Villager> owner = level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(48), v -> v.getBrain()
                .getMemory(MemoryModuleType.HOME).filter(home -> home.dimension() == level.dimension() && home.pos().equals(pos)).isPresent())
                .stream().findFirst();
        if (owner.isPresent()) {
            player.displayClientMessage(Component.translatable("fealty.crime.sleep_in_bed", owner.get().getDisplayName())
                    .withStyle(ChatFormatting.YELLOW), true);
            CrimeService.commit(player, village.get().id(), RepSources.SLEEP_IN_BED, pos, null, false);
        }
    }

    /** Lingering in villagers' homes at night: a warning, then a crime every minute. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0) {
            return;
        }
        Optional<VillageRecord> village = trespassing(player);
        if (village.isEmpty()) {
            TRESPASSING.remove(player.getUUID());
            return;
        }
        long now = player.level().getGameTime();
        Long since = TRESPASSING.get(player.getUUID());
        if (since == null) {
            TRESPASSING.put(player.getUUID(), now);
            player.displayClientMessage(Component.translatable("fealty.crime.trespass_warning").withStyle(ChatFormatting.YELLOW), true);
        } else if (now - since >= TRESPASS_GRACE) {
            // The next offence comes a minute later.
            TRESPASSING.put(player.getUUID(), now + TRESPASS_REPEAT - TRESPASS_GRACE);
            CrimeService.commit(player, village.get().id(), RepSources.TRESPASS, player.blockPosition(), null, false);
        }
    }

    private static Optional<VillageRecord> trespassing(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (player.isCreative() || player.isSpectator() || player.isSleeping() || !level.isNight()) {
            return Optional.empty();
        }
        Optional<VillageRecord> village = VillageTracker.currentVillage(player).flatMap(id -> FealtyWorldData.get(player.server).village(id));
        if (village.isEmpty() || village.get().lord().isLord(player.getUUID())
                || RepManager.getTier(player, village.get().id()).rank() >= TierManager.trusted().rank()
                || level.canSeeSky(player.blockPosition())) {
            return Optional.empty();
        }
        Optional<BlockPos> bed = level.getPoiManager().findClosest(h -> h.is(PoiTypes.HOME), player.blockPosition(), 3, PoiManager.Occupancy.ANY);
        return bed.isPresent() && !PlacedBlockTracker.isPlaced(level, bed.get()) ? village : Optional.empty();
    }

    /** Explosions a player set off that tear into a village. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getExplosion().getIndirectSourceEntity() instanceof ServerPlayer player)) {
            return;
        }
        Map<ResourceLocation, BlockPos> hit = new HashMap<>();
        Map<ResourceLocation, Integer> blocks = new HashMap<>();
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (level.getBlockState(pos).isAir() || PlacedBlockTracker.isPlaced(level, pos)) {
                continue;
            }
            VillageResolver.villageAt(level, pos).filter(v -> !v.lord().isLord(player.getUUID())).ifPresent(village -> {
                hit.putIfAbsent(village.id(), pos.immutable());
                blocks.merge(village.id(), 1, Integer::sum);
            });
        }
        long now = level.getGameTime();
        for (Map.Entry<ResourceLocation, Integer> entry : blocks.entrySet()) {
            if (entry.getValue() >= 3 && cooldown(EXPLOSIONS, player.getUUID() + "|" + entry.getKey(), now, EXPLOSION_COOLDOWN)) {
                CrimeService.commit(player, entry.getKey(), RepSources.EXPLOSION, hit.get(entry.getKey()), null, true);
            }
        }
    }

    /** Animals bred by players are theirs, not the village's. */
    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() != null && event.getChild() != null) {
            event.getChild().addTag(BRED_TAG);
        }
    }

    /** @return true (and restarts the cooldown) if the key is not cooling down */
    private static boolean cooldown(Map<String, Long> map, String key, long now, int ticks) {
        Long last = map.get(key);
        if (last != null && now - last < ticks) {
            return false;
        }
        map.put(key, now);
        if (map.size() > 512) {
            map.entrySet().removeIf(e -> now - e.getValue() > ticks);
        }
        return true;
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
            noteClick(player, event.getPos(), player.isCrouching());
        }
    }

    /** Remember the block the player is about to open (picked locks open through here too). */
    public static void noteClick(ServerPlayer player, BlockPos pos, boolean crouching) {
        LAST_CLICK.put(player.getUUID(), new Click(pos.immutable(), player.level().getGameTime(), crouching));
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
        AbstractContainerMenu menu = event.getContainer();
        OpenContainer open = new OpenContainer(menu, click.pos(), village.get().id(), count(menu, player), coffer, click.crouching());
        open.listener = new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu changed, int slot, ItemStack stack) {
                if (!open.judged && slot >= 0 && slot < changed.slots.size() && changed.slots.get(slot).container != player.getInventory()) {
                    judgeIfTaken(player, open);
                }
            }

            @Override
            public void dataChanged(AbstractContainerMenu changed, int id, int value) {
            }
        };
        OPEN.put(player.getUUID(), open);
        menu.addSlotListener(open.listener);
    }

    /** The first item out of the container: whoever sees it, sees a theft. */
    private static void judgeIfTaken(ServerPlayer player, OpenContainer open) {
        Map<Item, Integer> now = count(open.menu, player);
        boolean taken = false;
        for (Map.Entry<Item, Integer> entry : open.before.entrySet()) {
            if (now.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                taken = true;
                break;
            }
        }
        if (!taken) {
            return;
        }
        open.judged = true;
        ResourceLocation crime = open.coffer ? RepSources.VAULT_RAID : RepSources.STEAL;
        CrimeService.Result result = WitnessService.asSneaking(player, open.crouching,
                () -> CrimeService.commit(player, open.village, crime, open.pos, null, false));
        open.witnessed = result.witnessed();
        if (!open.witnessed) {
            player.displayClientMessage(Component.translatable("fealty.theft.unseen").withStyle(ChatFormatting.GRAY), true);
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        OpenContainer open = OPEN.remove(player.getUUID());
        if (open == null || open.menu != event.getContainer()) {
            return;
        }
        if (open.listener != null) {
            open.menu.removeSlotListener(open.listener);
        }
        Map<Item, Integer> after = count(open.menu, player);
        Map<Item, Integer> stolen = new HashMap<>();
        int total = 0;
        for (Map.Entry<Item, Integer> entry : open.before.entrySet()) {
            int taken = entry.getValue() - after.getOrDefault(entry.getKey(), 0);
            if (taken > 0) {
                stolen.put(entry.getKey(), taken);
                total += taken;
            }
        }
        if (total <= 0) {
            return;
        }
        if (!open.judged) {
            judgeIfTaken(player, open);
        }
        if (!open.witnessed) {
            FealtyEvents.fire(player, FealtyEvents.UNSEEN_THEFT);
        }
        if (open.coffer) {
            FealtyEvents.fire(player, FealtyEvents.VAULT_RAIDED);
        }
        QuestEvents.onTheft(player, open.village, stolen, open.witnessed, open.coffer);
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
        TRESPASSING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_CLICK.clear();
        OPEN.clear();
        HIT_COOLDOWNS.clear();
        TRAMPLES.clear();
        EXPLOSIONS.clear();
        HARVESTS.clear();
        TRESPASSING.clear();
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
        assault(player, victim, faction.get());
    }

    /** Striking (or poisoning) a member of a village: hitting a child or a guard is its own crime. */
    public static void assault(ServerPlayer player, LivingEntity victim, ResourceLocation faction) {
        if (victim instanceof Mob mob && mob.getTarget() == player) {
            return; // self-defence against a guard that is already attacking
        }
        if (!cooldown(HIT_COOLDOWNS, victim.getUUID() + "|" + player.getUUID(), player.level().getGameTime(), HIT_COOLDOWN_TICKS)) {
            return;
        }
        ResourceLocation crime = victim instanceof Villager villager && villager.isBaby() ? RepSources.HIT_CHILD
                : FactionResolver.isGuard(victim) ? RepSources.HIT_GUARD : RepSources.HIT_VILLAGER;
        CrimeService.commit(player, faction, crime, victim.blockPosition(), victim, false);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        QuestEvents.onEntityDeath(victim, killer);
        if (killer == null) {
            return;
        }
        if (victim instanceof WanderingTrader) {
            killTrader(killer, victim);
            return;
        }
        if (victim instanceof Animal animal) {
            killAnimal(killer, animal);
            return;
        }
        if (!(isVillageMember(victim) || victim.getType().is(FealtyTags.Entities.BANDITS))) {
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
        } else if (victim instanceof Villager villager) {
            crime = villager.isBaby() ? RepSources.KILL_CHILD : RepSources.KILL_VILLAGER;
        } else {
            crime = RepSources.KILL_MEMBER;
        }
        CrimeService.commit(killer, faction.get(), crime, victim.blockPosition(), null, false);
    }

    /** Wandering traders are friends of every village, and of the road. */
    private static void killTrader(ServerPlayer killer, LivingEntity trader) {
        VillageResolver.nearestVillage(killer.serverLevel(), trader.blockPosition(), 48)
                .ifPresent(village -> CrimeService.commit(killer, village.id(), RepSources.KILL_TRADER, trader.blockPosition(), null, false));
        if (!killer.isCreative()) {
            RepManager.meet(killer, Factions.WANDERERS);
            RepManager.applySource(killer, Factions.WANDERERS, RepSources.KILL_TRADER);
        }
    }

    /** The village's own animals and cats: not ones players bred, tamed or named. */
    private static void killAnimal(ServerPlayer killer, Animal animal) {
        if (animal.getTags().contains(BRED_TAG) || animal.hasCustomName() || animal instanceof TamableAnimal tame && tame.isTame()
                || animal instanceof AbstractHorse horse && horse.isTamed()) {
            return;
        }
        Optional<VillageRecord> village = VillageResolver.villageAt(killer.serverLevel(), animal.blockPosition());
        if (village.isPresent() && !village.get().lord().isLord(killer.getUUID())) {
            CrimeService.commit(killer, village.get().id(), animal instanceof Cat ? RepSources.KILL_CAT : RepSources.KILL_ANIMAL,
                    animal.blockPosition(), null, false);
        }
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
