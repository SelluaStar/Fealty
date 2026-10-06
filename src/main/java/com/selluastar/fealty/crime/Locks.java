package com.selluastar.fealty.crime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.block.VillageCofferBlockEntity;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.item.LockpickItem;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.network.LockpickResultPayload;
import com.selluastar.fealty.network.OpenLockpickPayload;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.registry.ModItems;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Village locks. A village's coffer is locked to everyone but its lord, and its chests and barrels to anyone it does
 * not yet trust. A lockpick opens them: the player moves the pick to find where the lock gives and tries to turn it;
 * the closer the pick, the further the lock turns. Each miss strains the pick and may be heard. The spot where a lock
 * gives is kept on the server and changes every day.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class Locks {
    /** How close (degrees) the pick must be to open a chest, and a coffer. */
    private static final float SWEET_SPOT_CHEST = 9F;
    private static final float SWEET_SPOT_COFFER = 5F;
    /** Beyond the sweet spot the lock turns less and less, and not at all this many degrees further out. */
    private static final float FALLOFF = 45F;
    /** A picked lock stays open to the player this long (ticks). */
    private static final int UNLOCKED_TICKS = 2400;
    private static final int ATTEMPT_COOLDOWN = 6;
    /** The lock turns all the way before the container opens (ticks). */
    private static final int OPEN_DELAY = 10;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<String, Long> UNLOCKED = new HashMap<>();
    private static final Map<UUID, Session> OPENING = new HashMap<>();

    private Locks() {
    }

    private static final class Session {
        final BlockPos pos;
        final boolean coffer;
        final float target;
        final boolean crouching;
        long lastAttempt;
        long openAt;

        Session(BlockPos pos, boolean coffer, float target, boolean crouching) {
            this.pos = pos;
            this.coffer = coffer;
            this.target = target;
            this.crouching = crouching;
        }
    }

    private static Optional<VillageRecord> villageOf(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof VillageCofferBlockEntity coffer && coffer.village() != null) {
            return FealtyWorldData.get(level.getServer()).village(coffer.village());
        }
        return VillageResolver.villageAt(level, pos);
    }

    /** Whether the container is locked to the player. */
    public static boolean isLocked(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        boolean coffer = state.is(ModBlocks.VILLAGE_COFFER.get());
        if (!coffer && !(FealtyConfig.LOCKED_VILLAGE_CHESTS.get() && state.is(FealtyTags.Blocks.VILLAGE_CONTAINERS))) {
            return false;
        }
        if (PlacedBlockTracker.isPlaced(level, pos)) {
            return false;
        }
        Optional<VillageRecord> village = villageOf(level, pos);
        if (village.isEmpty() || village.get().lord().isLord(player.getUUID())) {
            return false;
        }
        return coffer || RepManager.getTier(player, village.get().id()).rank() < TierManager.trusted().rank();
    }

    private static String key(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.getUUID() + "|" + level.dimension().location() + "|" + pos.asLong();
    }

    /** Runs before the block is used, so sneaking with a pick in hand works too. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        boolean coffer = state.is(ModBlocks.VILLAGE_COFFER.get());
        if (coffer && event.getItemStack().is(Items.EMERALD) && !player.isSecondaryUseActive()) {
            return; // a donation, not a break-in
        }
        if (!isLocked(player, level, pos, state)) {
            return;
        }
        Long open = UNLOCKED.get(key(player, level, pos));
        if (open != null && open > level.getGameTime()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack pick = LockpickItem.held(player);
        if (pick.isEmpty()) {
            player.displayClientMessage(Component.translatable(coffer ? "fealty.lock.coffer" : "fealty.lock.locked")
                    .withStyle(ChatFormatting.GRAY), true);
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 1.0F);
            return;
        }
        Session session = new Session(pos.immutable(), coffer, target(level, pos), player.isCrouching());
        SESSIONS.put(player.getUUID(), session);
        level.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.6F);
        FealtyNetwork.send(player, new OpenLockpickPayload(session.pos, coffer, usesLeft(pick), spare(player)));
    }

    /** Where the lock gives: the same for everyone, changing each day. */
    private static float target(ServerLevel level, BlockPos pos) {
        long seed = pos.asLong() * 31L + level.getSeed() + RepManager.day(level.getServer()) * 7919L;
        return 15F + new Random(seed).nextFloat() * 150F;
    }

    private static int usesLeft(ItemStack pick) {
        return pick.isEmpty() ? 0 : pick.getMaxDamage() - pick.getDamageValue();
    }

    /** Lockpicks besides the one in hand. */
    private static int spare(ServerPlayer player) {
        int count = player.getInventory().countItem(ModItems.LOCKPICK.get());
        return Math.max(0, count - (LockpickItem.held(player).isEmpty() ? 0 : 1));
    }

    /** The player tries to turn the lock with the pick at {@code angle} degrees. */
    public static void attempt(ServerPlayer player, BlockPos pos, float angle) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.pos.equals(pos)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now - session.lastAttempt < ATTEMPT_COOLDOWN) {
            return;
        }
        session.lastAttempt = now;
        ItemStack pick = LockpickItem.held(player);
        if (pick.isEmpty() || player.distanceToSqr(Vec3.atCenterOf(pos)) > 36 || !isLocked(player, level, pos, level.getBlockState(pos))) {
            SESSIONS.remove(player.getUUID());
            FealtyNetwork.send(player, LockpickResultPayload.close());
            return;
        }
        float width = session.coffer ? SWEET_SPOT_COFFER : SWEET_SPOT_CHEST;
        float off = Math.abs(Mth.clamp(angle, 0F, 180F) - session.target);
        if (off <= width) {
            SESSIONS.remove(player.getUUID());
            session.openAt = now + OPEN_DELAY;
            OPENING.put(player.getUUID(), session);
            UNLOCKED.put(key(player, level, pos), now + UNLOCKED_TICKS);
            if (UNLOCKED.size() > 256) {
                UNLOCKED.entrySet().removeIf(e -> e.getValue() <= now);
            }
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.5F);
            FealtyEvents.fire(player, FealtyEvents.LOCK_PICKED);
            FealtyNetwork.send(player, new LockpickResultPayload(1F, usesLeft(pick), spare(player), LockpickResultPayload.OPENED));
            return;
        }
        float turn = Mth.clamp(1F - (off - width) / FALLOFF, 0F, 0.9F);
        // The pick strains against the lock...
        EquipmentSlot slot = player.getMainHandItem() == pick ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        pick.hurtAndBreak(session.coffer ? 2 : 1, player, slot);
        boolean broke = pick.isEmpty();
        if (!broke) {
            level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.5F, 1.4F + level.getRandom().nextFloat() * 0.3F);
        }
        // ...and someone nearby may hear it.
        boolean heard = false;
        if (level.getRandom().nextFloat() < (session.coffer ? 0.25F : 0.15F)) {
            Optional<VillageRecord> village = villageOf(level, pos);
            if (village.isPresent()) {
                heard = WitnessService.asSneaking(player, session.crouching,
                        () -> CrimeService.commit(player, village.get().id(), RepSources.LOCKPICKING, pos, null, true)).witnessed();
            }
        }
        if (broke) {
            SESSIONS.remove(player.getUUID());
        }
        int flags = (broke ? LockpickResultPayload.BROKE : 0) | (heard ? LockpickResultPayload.HEARD : 0);
        FealtyNetwork.send(player, new LockpickResultPayload(turn, usesLeft(LockpickItem.held(player)), spare(player), flags));
    }

    /** Opens picked locks once the lock has turned. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (OPENING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Session>> it = OPENING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            Session session = entry.getValue();
            ServerLevel level = player.serverLevel();
            if (level.getGameTime() < session.openAt) {
                continue;
            }
            it.remove();
            if (player.distanceToSqr(Vec3.atCenterOf(session.pos)) > 36) {
                continue;
            }
            // The container opens as if the player had used it, sneaking or not, so theft is judged as usual.
            CrimeHandlers.noteClick(player, session.pos, session.crouching);
            level.getBlockState(session.pos).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(session.pos), Direction.UP, session.pos, false));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SESSIONS.remove(id);
        OPENING.remove(id);
        String prefix = id + "|";
        UNLOCKED.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        OPENING.clear();
        UNLOCKED.clear();
    }
}
