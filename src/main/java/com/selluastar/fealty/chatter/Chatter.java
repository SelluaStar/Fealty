package com.selluastar.fealty.chatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.event.VillagerChatEvent;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.dialogue.DialogueService;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.war.Captives;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Villagers chatting with each other in speech bubbles. Now and then, near a player in a village, one villager walks up
 * to another and they trade a few lines about a topic (small talk from {@code fealty/chatter/}, or real news from
 * {@link LiveTopics}). The bubbles only hint at what it is about: ask either villager "What's up?" and they tell you
 * ({@link Rumours}). Everything here lives in memory; a chat that is interrupted by a restart is simply forgotten.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class Chatter {
    /** How far from the player a chat can start. */
    private static final double START_RADIUS = 24.0;
    /** How far apart the two villagers may be when a chat starts: one walks over to the other. */
    private static final double PARTNER_RANGE_SQR = 10.0 * 10.0;
    /** Close enough to talk. */
    private static final double TALK_DISTANCE_SQR = 3.5 * 3.5;
    /** Give up if the walk takes longer than this (six seconds). */
    private static final int APPROACH_TICKS = 120;
    private static final int LINGER_TICKS = 40;
    /** How long after a chat either villager can still be asked what it was about. */
    private static final int TELLABLE_TICKS = 600;
    /** A villager the player talks to keeps their chat partner waiting this long, and the topic stays tellable much longer. */
    private static final int PARTNER_WAITS_TICKS = 200;
    private static final int INTERRUPTED_TELLABLE_TICKS = 6000;
    /** Percent of chats that are about real news when there is any. */
    private static final int LIVE_SHARE = 30;

    private enum Phase {
        APPROACH, TALK, LINGER, DONE
    }

    private static final class Conversation {
        final ResourceKey<Level> dimension;
        final UUID first;
        final UUID second;
        final Vec3 where;
        final Topic topic;
        final long startedAt;
        Phase phase = Phase.APPROACH;
        int next;
        long nextLineAt;
        long lingerUntil;
        long tellableUntil;
        boolean interrupted;
        boolean told;
        /** Taken off the books: nothing more happens. */
        boolean gone;

        Conversation(ResourceKey<Level> dimension, UUID first, UUID second, Vec3 where, Topic topic, long startedAt) {
            this.dimension = dimension;
            this.first = first;
            this.second = second;
            this.where = where;
            this.topic = topic;
            this.startedAt = startedAt;
        }
    }

    private static final List<Conversation> CONVERSATIONS = new ArrayList<>();
    private static final Map<UUID, Conversation> BY_VILLAGER = new HashMap<>();
    /** Villagers who chatted lately, and the game time they may chat again. */
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private Chatter() {
    }

    // ---- Starting a chat ----

    /** Called once a second for a player in a village: with some chance, two villagers nearby strike up a chat. */
    public static void tickNearPlayer(ServerPlayer player, VillageRecord village) {
        if (!FealtyConfig.VILLAGER_CHATTER.get()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        int chance = FealtyConfig.CHATTER_CHANCE.get();
        if (chance <= 0 || random.nextInt(100) >= chance || activeNear(player) >= FealtyConfig.CHATTER_MAX_ACTIVE.get()) {
            return;
        }
        List<Villager> pool = new ArrayList<>(level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(START_RADIUS),
                v -> eligible(level, v, village)));
        if (pool.size() < 2) {
            return;
        }
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Villager swap = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, swap);
        }
        for (int i = 0; i < Math.min(4, pool.size()); i++) {
            Villager first = pool.get(i);
            Villager second = nearest(first, pool);
            if (second == null) {
                continue;
            }
            Optional<Topic> topic = pickTopic(level, village, first, second, random);
            if (topic.isPresent() && start(level, first, second, topic.get())) {
                return;
            }
        }
    }

    private static Optional<Topic> pickTopic(ServerLevel level, VillageRecord village, Villager first, Villager second, RandomSource random) {
        if (FealtyConfig.RUMOURS.get() && random.nextInt(100) < LIVE_SHARE) {
            Optional<Topic> live = LiveTopics.forChat(level, village, first, second, random);
            if (live.isPresent()) {
                return live;
            }
        }
        Optional<Topic> small = ChatterTopics.pick(random, first, second, level.isNight());
        return small.isPresent() ? small : LiveTopics.forChat(level, village, first, second, random);
    }

    /** Whether a villager could start or join a chat right now. */
    private static boolean eligible(ServerLevel level, Villager villager, VillageRecord village) {
        if (!villager.isAlive() || villager.isBaby() || villager.isSleeping() || villager.isTrading()
                || BY_VILLAGER.containsKey(villager.getUUID()) || COOLDOWN.getOrDefault(villager.getUUID(), 0L) > level.getGameTime()
                || Speech.sinceLastSpoke(villager) < 200 || !calm(villager)) {
            return false;
        }
        if (DialogueService.listener(villager).isPresent() || ChainManager.hasRole(villager) || Captives.isCaptive(villager)) {
            return false;
        }
        return FactionResolver.factionOf(villager).map(village.id()::equals).orElse(false);
    }

    /** Going about its day: not frightened, hiding, resting or in a raid. */
    private static boolean calm(Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        return !(brain.isActive(Activity.PANIC) || brain.isActive(Activity.RAID) || brain.isActive(Activity.PRE_RAID)
                || brain.isActive(Activity.HIDE) || brain.isActive(Activity.REST));
    }

    @Nullable
    private static Villager nearest(Villager villager, List<Villager> pool) {
        Villager best = null;
        double bestDistance = PARTNER_RANGE_SQR;
        for (Villager other : pool) {
            double distance = other == villager ? Double.MAX_VALUE : villager.distanceToSqr(other);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    private static int activeNear(ServerPlayer player) {
        int count = 0;
        for (Conversation c : CONVERSATIONS) {
            if (c.phase != Phase.DONE && c.dimension.equals(player.level().dimension())
                    && c.where.distanceToSqr(player.position()) <= START_RADIUS * START_RADIUS) {
                count++;
            }
        }
        return count;
    }

    /**
     * Start a chat: {@code first} walks up to {@code second} and they talk about the topic.
     *
     * @return whether it began (not if either is already chatting, or a listener cancelled it)
     */
    public static boolean start(ServerLevel level, Villager first, Villager second, Topic topic) {
        if (first == second || BY_VILLAGER.containsKey(first.getUUID()) || BY_VILLAGER.containsKey(second.getUUID())) {
            return false;
        }
        if (NeoForge.EVENT_BUS.post(new VillagerChatEvent.Started(level, first, second, topic.id())).isCanceled()) {
            return false;
        }
        long now = level.getGameTime();
        Conversation c = new Conversation(level.dimension(), first.getUUID(), second.getUUID(), first.position(), topic, now);
        CONVERSATIONS.add(c);
        BY_VILLAGER.put(c.first, c);
        BY_VILLAGER.put(c.second, c);
        if (first.distanceToSqr(second) <= TALK_DISTANCE_SQR) {
            c.phase = Phase.TALK;
            c.nextLineAt = now + 10;
            hold(first, second);
        } else {
            walkTo(first, second);
        }
        hold(second, first);
        return true;
    }

    // ---- The chat, tick by tick ----

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!CONVERSATIONS.isEmpty() && event.getServer().getTickCount() % 2 == 0) {
            tick(event.getServer());
        }
    }

    /** Move every chat on (every other tick; public so tests can step it). */
    public static void tick(MinecraftServer server) {
        for (Conversation c : List.copyOf(CONVERSATIONS)) {
            ServerLevel level = server.getLevel(c.dimension);
            if (level == null) {
                forget(c, null);
                continue;
            }
            long now = level.getGameTime();
            if (c.phase == Phase.DONE) {
                if (now > c.tellableUntil) {
                    forget(c, level);
                }
                continue;
            }
            Villager first = villager(level, c.first);
            Villager second = villager(level, c.second);
            if (first == null || second == null || !calm(first) || !calm(second)) {
                end(c, level, now);
                continue;
            }
            if (!c.interrupted && (first.isTrading() || second.isTrading() || DialogueService.listener(first).isPresent()
                    || DialogueService.listener(second).isPresent())) {
                interrupt(c, level, now);
                if (c.gone || c.phase == Phase.DONE) {
                    continue;
                }
            }
            hold(second, first);
            switch (c.phase) {
                case APPROACH -> {
                    if (now - c.startedAt > APPROACH_TICKS) {
                        forget(c, level);
                    } else if (first.distanceToSqr(second) <= TALK_DISTANCE_SQR) {
                        c.phase = Phase.TALK;
                        c.nextLineAt = now + 10;
                        hold(first, second);
                    } else if ((now - c.startedAt) % 20 < 2) {
                        walkTo(first, second);
                    }
                }
                case TALK -> {
                    hold(first, second);
                    if (now >= c.nextLineAt) {
                        if (c.next < c.topic.lines().size()) {
                            Villager speaker = c.next % 2 == 0 ? first : second;
                            Component line = c.topic.lines().get(c.next++);
                            Speech.say(speaker, line);
                            c.nextLineAt = now + Math.max(50, Speech.bubbleTicks(line) * 7 / 10);
                        } else {
                            c.phase = Phase.LINGER;
                            c.lingerUntil = now + LINGER_TICKS;
                        }
                    }
                }
                case LINGER -> {
                    hold(first, second);
                    if (now >= c.lingerUntil) {
                        end(c, level, now);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** The chat is over: the villagers go back to their day, and for a while the topic can still be asked about. */
    private static void end(Conversation c, ServerLevel level, long now) {
        if (c.phase == Phase.DONE) {
            return;
        }
        c.phase = Phase.DONE;
        c.tellableUntil = Math.max(c.tellableUntil, now + TELLABLE_TICKS);
        RandomSource random = level.getRandom();
        COOLDOWN.put(c.first, now + 1200 + random.nextInt(2400));
        COOLDOWN.put(c.second, now + 1200 + random.nextInt(2400));
        release(level, c);
        if (c.next == 0) {
            forget(c, level);
        }
    }

    private static void forget(Conversation c, @Nullable ServerLevel level) {
        c.gone = true;
        CONVERSATIONS.remove(c);
        BY_VILLAGER.remove(c.first, c);
        BY_VILLAGER.remove(c.second, c);
        if (level != null && c.phase != Phase.DONE) {
            release(level, c);
        }
    }

    private static void release(ServerLevel level, Conversation c) {
        for (UUID id : new UUID[]{c.first, c.second}) {
            Villager villager = villager(level, id);
            if (villager != null) {
                villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            }
        }
    }

    @Nullable
    private static Villager villager(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof Villager villager && villager.isAlive() ? villager : null;
    }

    /** Keep a villager where it stands, looking at the other (unless a player is talking to it: it watches them). */
    private static void hold(Villager villager, Villager toward) {
        if (villager.isTrading() || DialogueService.listener(villager).isPresent()) {
            return;
        }
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(toward, true));
    }

    private static void walkTo(Villager villager, Villager target) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), 0.5F, 2));
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
    }

    // ---- Being talked to, and asked ----

    /**
     * A player starts talking to this villager (or trades with them). A chat they were in stops: the other villager
     * waits a few seconds, and what they were talking about can be asked for as long as the player stays.
     */
    public static void interrupt(Villager villager) {
        Conversation c = BY_VILLAGER.get(villager.getUUID());
        if (c != null && villager.level() instanceof ServerLevel level) {
            interrupt(c, level, level.getGameTime());
        }
    }

    private static void interrupt(Conversation c, ServerLevel level, long now) {
        if (c.phase == Phase.DONE) {
            if (c.next > 0 && !c.told) {
                c.tellableUntil = Math.max(c.tellableUntil, now + INTERRUPTED_TELLABLE_TICKS);
            }
            return;
        }
        if (c.interrupted) {
            return;
        }
        c.interrupted = true;
        if (c.phase == Phase.APPROACH || c.next == 0) {
            forget(c, level);
            return;
        }
        // No more lines; the one the player is not talking to waits a little, and the topic stays tellable.
        c.next = c.topic.lines().size();
        c.phase = Phase.LINGER;
        c.lingerUntil = now + PARTNER_WAITS_TICKS;
        c.tellableUntil = now + INTERRUPTED_TELLABLE_TICKS;
    }

    /** Whether this villager is in the middle of a chat. */
    public static boolean isChatting(Entity entity) {
        Conversation c = BY_VILLAGER.get(entity.getUUID());
        return c != null && c.phase != Phase.DONE;
    }

    /** The chat this villager is in, or was in lately, if they can be asked about it. */
    public static Optional<Topic> tellable(Entity entity) {
        Conversation c = BY_VILLAGER.get(entity.getUUID());
        if (c == null || c.told || c.next < 1 || c.phase == Phase.DONE && entity.level().getGameTime() > c.tellableUntil) {
            return Optional.empty();
        }
        return Optional.of(c.topic);
    }

    /**
     * The player has been told what the chat was about: it is spent, and the other villager may hush the one who told.
     */
    public static void told(Villager villager, @Nullable ServerPlayer player) {
        Conversation c = BY_VILLAGER.get(villager.getUUID());
        if (c == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        c.told = true;
        Villager other = villager(level, villager.getUUID().equals(c.first) ? c.second : c.first);
        if (other != null && other != villager && level.getRandom().nextInt(2) == 0) {
            Speech.bark(other, "chatter_hush", player, 0);
        }
        if (c.phase != Phase.DONE) {
            end(c, level, level.getGameTime());
        }
        forget(c, level);
    }

    /** Skip the waiting between lines and step every chat once (for tests). */
    public static void fastForward(MinecraftServer server) {
        for (Conversation c : CONVERSATIONS) {
            c.nextLineAt = 0;
            c.lingerUntil = 0;
        }
        tick(server);
    }

    /** Chats going on now (for tests). */
    public static int active() {
        return (int) CONVERSATIONS.stream().filter(c -> c.phase != Phase.DONE).count();
    }

    /** Forget every chat and cooldown (for tests). */
    public static void reset() {
        CONVERSATIONS.clear();
        BY_VILLAGER.clear();
        COOLDOWN.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        reset();
    }
}
