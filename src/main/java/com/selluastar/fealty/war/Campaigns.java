package com.selluastar.fealty.war;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.Campaign;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.Stronghold;
import com.selluastar.fealty.api.event.CampaignEvent;
import com.selluastar.fealty.api.event.RepQuestEvent;
import com.selluastar.fealty.api.event.VillageEvent;
import com.selluastar.fealty.block.WarBannerBlock;
import com.selluastar.fealty.block.WarBannerBlockEntity;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.entity.VillageGuardEntity;
import com.selluastar.fealty.guard.Garrison;
import com.selluastar.fealty.guard.GarrisonManager;
import com.selluastar.fealty.guard.GuardOrders;
import com.selluastar.fealty.lordship.LordshipManager;
import com.selluastar.fealty.mail.MailService;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.Inventories;
import com.selluastar.fealty.util.Maps;
import com.selluastar.fealty.village.VillageNames;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * A lord's raids on pillager strongholds. The lord declares a raid from the Village Hall: they pay for arms and food,
 * take a War Map and the raid as a tracked quest, and the guards with them fall in. Near the stronghold the rest of
 * the warband catches up (the village's guards and a levy of militia), and the fight begins with a bar of the
 * defenders left. Victory frees the captives, razes the stronghold, fills the treasury and wins the village a spell
 * of peace; the lord falling, the time running out or the lord leaving the field ends the raid.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class Campaigns extends SavedData {
    private static final String NAME = "fealty_campaigns";
    private static final SavedData.Factory<Campaigns> FACTORY = new SavedData.Factory<>(Campaigns::new, Campaigns::load, null);
    /** The raid as a quest: tracked, with a waypoint, and its reward on victory. */
    public static final ResourceLocation QUEST = Fealty.id("war/raid_stronghold");
    /** The raid quest's giver: the village's war council (one raid at a time). */
    public static final ResourceLocation GIVER = Fealty.id("war");
    public static final String LEVY_TAG = "fealty.levy";
    private static final String LEVY_LORD = "fealty_levy_lord";
    /** The rest of the warband joins the lord this close to the stronghold. */
    private static final int MUSTER_RANGE = 112;
    /** Players this close to the fighting see the bar and count as fighters. */
    private static final int FIELD_RANGE = 64;
    /** Leaving the field for this long ends the raid. */
    private static final long LEFT_FIELD_TICKS = 1200;
    private static final int GUARDS_FALL_IN_RANGE = 32;

    /** One lord's raid, kept across restarts. */
    static final class State {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("lord").forGetter(s -> s.lord),
                ResourceLocation.CODEC.fieldOf("village").forGetter(s -> s.village),
                UUIDUtil.CODEC.fieldOf("stronghold").forGetter(s -> s.stronghold),
                Codec.LONG.fieldOf("declared").forGetter(s -> s.declaredDay),
                Codec.BOOL.optionalFieldOf("mustered", false).forGetter(s -> s.mustered)
        ).apply(i, (lord, village, stronghold, declared, mustered) -> {
            State s = new State(lord, village, stronghold, declared);
            s.mustered = mustered;
            return s;
        }));

        final UUID lord;
        final ResourceLocation village;
        final UUID stronghold;
        final long declaredDay;
        boolean mustered;

        State(UUID lord, ResourceLocation village, UUID stronghold, long declaredDay) {
            this.lord = lord;
            this.village = village;
            this.stronghold = stronghold;
            this.declaredDay = declaredDay;
        }
    }

    /** The fighting at a stronghold (not saved: a restart mid-battle starts it afresh). */
    private static final class Battle {
        final Set<UUID> defenders = new HashSet<>();
        final Set<UUID> fighters = new HashSet<>();
        final ServerBossEvent bar;
        int total;
        long lastOnField;

        Battle(Component name, long now) {
            this.bar = new ServerBossEvent(Component.translatable("fealty.war.bar", name), BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.NOTCHED_10);
            this.lastOnField = now;
        }
    }

    private record Homeward(UUID lord, ResourceLocation village, long due) {
    }

    private final Map<UUID, State> states = new LinkedHashMap<>();
    private static final Map<UUID, Battle> BATTLES = new HashMap<>();
    private static final List<Homeward> HOMEWARD = new ArrayList<>();
    /** Lords whose raid is being ended, so the quest failing does not end it twice. */
    private static final Set<UUID> ENDING = new HashSet<>();

    public static Campaigns get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static Campaigns load(CompoundTag tag, HolderLookup.Provider registries) {
        Campaigns data = new Campaigns();
        if (tag.contains("campaigns")) {
            State.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("campaigns"))
                    .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to load campaigns: {}", e))
                    .ifPresent(list -> list.forEach(s -> data.states.put(s.lord, s)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        State.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(states.values()))
                .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to save campaigns: {}", e))
                .ifPresent(t -> tag.put("campaigns", t));
        return tag;
    }

    // ---- Queries ----

    /** The raid a lord is on, for the API and the Hall. */
    public static Optional<Campaign> of(MinecraftServer server, UUID lord) {
        State state = get(server).states.get(lord);
        if (state == null) {
            return Optional.empty();
        }
        Campaign.Phase phase = BATTLES.containsKey(lord) ? Campaign.Phase.BATTLE : Campaign.Phase.MARCHING;
        return Optional.of(new Campaign(state.lord, state.village, state.stronghold, phase, state.declaredDay));
    }

    /** Whether a warband is fighting at a stronghold right now (its garrison is not topped up mid-battle). */
    public static boolean fightingAt(ServerLevel level, Strongholds.Entry entry) {
        for (Map.Entry<UUID, State> e : get(level.getServer()).states.entrySet()) {
            if (e.getValue().stronghold.equals(entry.id()) && BATTLES.containsKey(e.getKey())) {
                return true;
            }
        }
        return false;
    }

    /** Defenders still standing and how many there were, while the lord is fighting. */
    public static Optional<int[]> battleCount(UUID lord) {
        Battle battle = BATTLES.get(lord);
        return battle == null ? Optional.empty() : Optional.of(new int[]{battle.total - battle.defenders.size(), battle.total});
    }

    /** Villagers the village can call up as militia. */
    public static int levySize(VillageRecord village) {
        int population = Math.max(0, village.garrison().population());
        return Math.min(FealtyConfig.MAX_LEVY.get(), population / FealtyConfig.LEVY_PER_VILLAGERS.get());
    }

    /** Guards the village can send: its Fealty guards still standing (others are on the roster too, but stay unless near). */
    public static int guardsReady(VillageRecord village) {
        int ready = 0;
        for (Garrison.Slot slot : village.garrison().slots()) {
            if (!slot.isDead()) {
                ready++;
            }
        }
        return Math.min(FealtyConfig.WARBAND_SIZE.get(), ready);
    }

    /** Days before the village can raise a warband again. */
    public static int cooldown(MinecraftServer server, VillageRecord village) {
        long day = RepManager.day(server);
        return (int) Math.max(0, village.sites().lastCampaignDay() + FealtyConfig.CAMPAIGN_COOLDOWN_DAYS.get() - day);
    }

    /** Why the lord cannot raid this stronghold now, if they cannot. */
    public static Optional<Component> problem(ServerPlayer lord, VillageRecord village, Strongholds.Entry target, boolean inVillage) {
        MinecraftServer server = lord.server;
        long day = RepManager.day(server);
        if (!village.lord().isLord(lord.getUUID())) {
            return Optional.of(Component.translatable("fealty.war.not_lord", village.name()));
        }
        if (get(server).states.containsKey(lord.getUUID())) {
            return Optional.of(Component.translatable("fealty.war.already"));
        }
        if (!inVillage) {
            return Optional.of(Component.translatable("fealty.war.away", village.name()));
        }
        int wait = cooldown(server, village);
        if (wait > 0) {
            return Optional.of(Component.translatable("fealty.war.cooldown", wait));
        }
        int range = FealtyConfig.WAR_RANGE.get();
        if (!target.dimension().equals(village.dimension()) || Strongholds.flatDistSqr(target.pos(), village.center()) > (double) range * range) {
            return Optional.of(Component.translatable("fealty.war.too_far", target.name()));
        }
        if (target.isRazed(day)) {
            return Optional.of(Component.translatable("fealty.war.razed", target.name(), target.razedDays(day)));
        }
        if (guardsReady(village) + levySize(village) + GarrisonManager.guardsNear(lord, village, GUARDS_FALL_IN_RANGE).size() == 0) {
            return Optional.of(Component.translatable("fealty.war.no_warband", village.name()));
        }
        return Optional.empty();
    }

    // ---- Declaring ----

    /** Raise the warband against a stronghold. @return the problem, or empty if the raid began */
    public static Optional<Component> declare(ServerPlayer lord, VillageRecord village, Strongholds.Entry target, boolean inVillage) {
        Optional<Component> problem = problem(lord, village, target, inVillage);
        if (problem.isPresent()) {
            return problem;
        }
        MinecraftServer server = lord.server;
        long day = RepManager.day(server);
        Stronghold view = target.view(day);
        CampaignEvent.Declare declare = NeoForge.EVENT_BUS.post(new CampaignEvent.Declare(server, lord.getUUID(), village.id(), view,
                FealtyConfig.RAID_COST_EMERALDS.get()));
        if (declare.isCanceled()) {
            return Optional.of(declare.getCancelReason().orElse(Component.translatable("fealty.war.forbidden")));
        }
        int cost = declare.getCost();
        if (!lord.isCreative() && cost > 0 && Inventories.count(lord, Items.EMERALD) < cost) {
            return Optional.of(Component.translatable("fealty.war.cost", cost));
        }
        CompoundTag questState = new CompoundTag();
        questState.putUUID("stronghold", target.id());
        questState.put("target", NbtUtils.writeBlockPos(target.pos()));
        questState.putString("giver_name", Component.translatable("fealty.war.council", village.name()).getString());
        questState.putString("village", village.id().toString());
        if (!QuestManager.accept(lord, GIVER, village.id(), QUEST, questState, 1)) {
            return Optional.of(Component.translatable("fealty.war.quest_full"));
        }
        if (!lord.isCreative() && cost > 0) {
            Inventories.take(lord, Items.EMERALD, cost);
        }
        Campaigns data = get(server);
        data.states.put(lord.getUUID(), new State(lord.getUUID(), village.id(), target.id(), day));
        data.setDirty();
        village.sites().setLastCampaignDay(day);
        FealtyWorldData.get(server).setDirty();

        ItemStack map = Maps.treasureMap(lord.serverLevel(), target.pos(), MapDecorationTypes.RED_X,
                Component.translatable("item.fealty.war_map", target.name()));
        Maps.give(lord, map);

        // The guards with the lord fall in at once.
        List<Mob> near = GarrisonManager.guardsNear(lord, village, GUARDS_FALL_IN_RANGE);
        List<Mob> warband = new ArrayList<>(near.subList(0, Math.min(near.size(), FealtyConfig.WARBAND_SIZE.get())));
        CampaignEvent.Muster muster = NeoForge.EVENT_BUS.post(new CampaignEvent.Muster(server, lord.getUUID(), village.id(), view,
                CampaignEvent.Muster.Stage.VILLAGE, warband, 0));
        for (Mob member : muster.members()) {
            GarrisonManager.orderFollow(member, lord);
        }
        ServerLevel level = lord.serverLevel();
        level.playSound(null, lord.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.PLAYERS, 6.0F, 1.0F);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, lord.getBoundingBox().inflate(16), v -> v.isAlive() && !v.isBaby())) {
            if (level.getRandom().nextInt(3) == 0) {
                Speech.bark(villager, "war_sendoff", lord, 200);
            }
        }
        Feedback.banner(lord, Component.translatable("fealty.war.declared"), Component.translatable("fealty.war.declared.detail",
                target.name(), muster.members().size()), 0xC0392B, "sword");
        lord.sendSystemMessage(Component.translatable("fealty.war.declared.chat", village.name(), target.name()).withStyle(ChatFormatting.GOLD));
        NeoForge.EVENT_BUS.post(new CampaignEvent.Started(server, lord.getUUID(), village.id(), view,
                muster.members().size() + guardsReady(village)));
        FealtyEvents.fire(lord, FealtyEvents.WAR_DECLARED);
        return Optional.empty();
    }

    /** The lord calls off their raid from the Hall. */
    public static boolean callOff(ServerPlayer lord) {
        State state = get(lord.server).states.get(lord.getUUID());
        if (state == null) {
            return false;
        }
        end(lord.server, state, CampaignEvent.Ended.Reason.CALLED_OFF, true);
        return true;
    }

    // ---- Each second ----

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 3) {
            return;
        }
        Campaigns data = get(server);
        for (State state : new ArrayList<>(data.states.values())) {
            tick(server, data, state);
        }
        homeward(server);
    }

    private static void tick(MinecraftServer server, Campaigns data, State state) {
        ServerPlayer lord = server.getPlayerList().getPlayer(state.lord);
        if (lord == null) {
            return; // the raid waits for its lord
        }
        Optional<VillageRecord> village = FealtyWorldData.get(server).village(state.village);
        if (village.isEmpty() || !village.get().lord().isLord(lord.getUUID())) {
            end(server, state, CampaignEvent.Ended.Reason.LORDSHIP_LOST, true);
            return;
        }
        Optional<Strongholds.Entry> found = Strongholds.get(server).get(state.stronghold);
        if (found.isEmpty()) {
            lord.sendSystemMessage(Component.translatable("fealty.war.target_gone").withStyle(ChatFormatting.GRAY));
            end(server, state, CampaignEvent.Ended.Reason.CALLED_OFF, true);
            return;
        }
        Strongholds.Entry target = found.get();
        long day = RepManager.day(server);
        if (day - state.declaredDay >= FealtyConfig.CAMPAIGN_DAYS.get()) {
            end(server, state, CampaignEvent.Ended.Reason.TIMED_OUT, true);
            return;
        }
        ServerLevel level = lord.serverLevel();
        Battle battle = BATTLES.get(state.lord);
        if (!level.dimension().equals(target.dimension())) {
            if (battle != null) {
                battle.bar.removeAllPlayers();
            }
            return;
        }
        double distance = Math.sqrt(Strongholds.flatDistSqr(lord.blockPosition(), target.pos()));
        if (!state.mustered && distance <= MUSTER_RANGE) {
            fieldMuster(lord, village.get(), state, target);
            data.setDirty();
        }
        if (battle == null && distance <= target.radius() + 40 && level.isLoaded(target.pos())
                && level.areEntitiesLoaded(ChunkPos.asLong(target.pos()))) {
            startBattle(lord, village.get(), state, target);
            return;
        }
        if (battle != null) {
            tickBattle(lord, village.get(), state, target, battle, distance);
        }
    }

    /** Near the stronghold, the rest of the warband catches up: the village's guards, and its militia. */
    private static void fieldMuster(ServerPlayer lord, VillageRecord village, State state, Strongholds.Entry target) {
        state.mustered = true;
        MinecraftServer server = lord.server;
        List<Mob> retinue = GarrisonManager.retinue(lord, village);
        long own = retinue.stream().filter(m -> m instanceof VillageGuardEntity).count();
        int need = (int) Math.max(0, FealtyConfig.WARBAND_SIZE.get() - own);
        int sent = GarrisonManager.dispatch(lord, village, need, retinue);
        CampaignEvent.Muster muster = NeoForge.EVENT_BUS.post(new CampaignEvent.Muster(server, lord.getUUID(), village.id(),
                target.view(RepManager.day(server)), CampaignEvent.Muster.Stage.FIELD, retinue, levySize(village)));
        for (Mob member : muster.members()) {
            if (!retinue.contains(member)) {
                GarrisonManager.orderFollow(member, lord);
            }
        }
        int levy = 0;
        for (int i = 0; i < muster.getLevySize(); i++) {
            if (raiseLevy(lord, village)) {
                levy++;
            }
        }
        lord.serverLevel().playSound(null, lord.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(), SoundSource.PLAYERS,
                4.0F, 1.0F);
        Feedback.banner(lord, Component.translatable("fealty.war.mustered"), Component.translatable("fealty.war.mustered.detail",
                sent + muster.members().size(), levy), 0xD4AF37, "guard");
    }

    /** A villager called up as militia: a guard without a place on the roster, who goes home when the raid ends. */
    private static boolean raiseLevy(ServerPlayer lord, VillageRecord village) {
        ServerLevel level = lord.serverLevel();
        BlockPos spot = GarrisonManager.arrivalSpot(level, lord);
        VillageGuardEntity militia = spot == null ? null : ModEntities.VILLAGE_GUARD.get().create(level);
        if (militia == null) {
            return false;
        }
        militia.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);
        militia.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        militia.joinVillage(village);
        militia.setRank(level.getRandom().nextInt(3) == 0 ? Garrison.Rank.ARCHER : Garrison.Rank.SWORDSMAN);
        militia.setCustomName(Component.translatable("entity.fealty.village_guard.levy.named", VillageNames.personName(level.getRandom())));
        militia.addTag(LEVY_TAG);
        militia.getPersistentData().putUUID(LEVY_LORD, lord.getUUID());
        militia.setPersistenceRequired();
        GarrisonManager.orderFollow(militia, lord);
        level.addFreshEntity(militia);
        level.sendParticles(ParticleTypes.CLOUD, militia.getX(), militia.getY() + 0.5, militia.getZ(), 6, 0.3, 0.4, 0.3, 0.01);
        return true;
    }

    /** The fight begins: the defenders are counted, the bar goes up and the warband closes on the stronghold. */
    private static void startBattle(ServerPlayer lord, VillageRecord village, State state, Strongholds.Entry target) {
        ServerLevel level = lord.serverLevel();
        MinecraftServer server = lord.server;
        if (target.kind() == Stronghold.Kind.OUTPOST && level.isLoaded(target.pos())) {
            // Scouts only knew where it was on the map: put the heart on the ground.
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.pos());
            if (Math.abs(ground.getY() - target.pos().getY()) > 4) {
                target.settle(ground);
                Strongholds.get(server).setDirty();
            }
        }
        List<LivingEntity> defenders = new ArrayList<>(defendersAt(level, target));
        CampaignEvent.BattleStarted started = NeoForge.EVENT_BUS.post(new CampaignEvent.BattleStarted(server, lord.getUUID(), village.id(),
                target.view(RepManager.day(server)), defenders));
        Battle battle = new Battle(target.name(), level.getGameTime());
        for (LivingEntity defender : started.defenders()) {
            if (defender instanceof Mob mob && !WarDefenders.isDefender(mob)) {
                WarDefenders.enlist(mob);
                WarDefenders.arm(mob);
                mob.setPersistenceRequired();
                if (mob instanceof PatrollingMonster patrolling) {
                    patrolling.setPatrolling(false);
                }
            }
            battle.defenders.add(defender.getUUID());
        }
        battle.total = battle.defenders.size();
        battle.fighters.add(lord.getUUID());
        BATTLES.put(state.lord, battle);
        if (battle.total == 0) {
            win(lord, village, state, target, battle);
            return;
        }
        for (Mob member : GarrisonManager.retinue(lord, village)) {
            member.getData(ModAttachments.GUARD_ORDERS).set(GuardOrders.Mode.GUARD, lord.getUUID(), 0, target.pos());
        }
        battle.bar.addPlayer(lord);
        level.playSound(null, lord.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 8.0F, 1.0F);
        Feedback.banner(lord, Component.translatable("fealty.war.battle"), Component.translatable("fealty.war.battle.detail", target.name(),
                battle.total), 0xC0392B, "sword");
    }

    /** Who holds a stronghold: a camp's garrison, and any raider on its ground. */
    public static List<LivingEntity> defendersAt(ServerLevel level, Strongholds.Entry target) {
        Set<LivingEntity> found = new HashSet<>();
        if (target.kind() == Stronghold.Kind.CAMP) {
            found.addAll(WarBannerBlockEntity.garrison(level, target.pos()));
        }
        int reach = target.radius() + 16;
        found.addAll(level.getEntitiesOfClass(Mob.class, new AABB(target.pos()).inflate(reach, 32, reach),
                m -> m.isAlive() && m.getType().is(FealtyTags.Entities.STRONGHOLD_DEFENDERS) && !Captives.isCaptive(m)));
        return List.copyOf(found);
    }

    private static void tickBattle(ServerPlayer lord, VillageRecord village, State state, Strongholds.Entry target, Battle battle,
                                   double distance) {
        ServerLevel level = lord.serverLevel();
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            boolean near = Strongholds.flatDistSqr(player.blockPosition(), target.pos()) <= (double) (target.radius() + FIELD_RANGE)
                    * (target.radius() + FIELD_RANGE);
            if (near && !player.isSpectator()) {
                battle.bar.addPlayer(player);
                if (!player.isCreative()) {
                    battle.fighters.add(player.getUUID());
                }
            } else {
                battle.bar.removePlayer(player);
            }
        }
        Iterator<UUID> it = battle.defenders.iterator();
        while (it.hasNext()) {
            Entity defender = level.getEntity(it.next());
            if (defender == null || !defender.isAlive()) {
                it.remove();
            }
        }
        battle.bar.setProgress(battle.total == 0 ? 0F : (float) battle.defenders.size() / battle.total);
        if (battle.defenders.isEmpty()) {
            win(lord, village, state, target, battle);
            return;
        }
        if (distance <= target.radius() + FIELD_RANGE * 2) {
            battle.lastOnField = now;
        } else if (now - battle.lastOnField > LEFT_FIELD_TICKS) {
            boolean warband = !GarrisonManager.retinue(lord, village).isEmpty();
            end(lord.server, state, warband ? CampaignEvent.Ended.Reason.CALLED_OFF : CampaignEvent.Ended.Reason.WARBAND_FELL, true);
        }
    }

    // ---- Victory and defeat ----

    private static void win(ServerPlayer lord, VillageRecord village, State state, Strongholds.Entry target, Battle battle) {
        MinecraftServer server = lord.server;
        ServerLevel level = lord.serverLevel();
        long day = RepManager.day(server);
        List<Villager> captives = Captives.near(level, target.pos(), target.radius() + 8);
        for (Villager captive : captives) {
            Captives.free(level, captive, null);
        }
        List<ServerPlayer> fighters = new ArrayList<>();
        for (UUID id : battle.fighters) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                fighters.add(player);
            }
        }
        CampaignEvent.Won won = NeoForge.EVENT_BUS.post(new CampaignEvent.Won(server, lord.getUUID(), village.id(), target.view(day), fighters,
                captives.size(), FealtyConfig.PEACE_DAYS.get(), FealtyConfig.RAZE_DAYS.get(), FealtyConfig.WAR_SPOILS.get()));
        battle.bar.removeAllPlayers();
        BATTLES.remove(state.lord);
        Campaigns data = get(server);
        data.states.remove(state.lord);
        data.setDirty();

        if (won.getRazeDays() > 0) {
            Strongholds.get(server).raze(server, target, village.id(), won.getRazeDays());
            flyColours(level, village, target);
        }
        if (won.getPeaceDays() > 0) {
            village.sites().setPeaceUntil(day + won.getPeaceDays());
            NeoForge.EVENT_BUS.post(new VillageEvent(server, village.id(), VillageEvent.Type.PEACE_STARTED));
        }
        if (won.getSpoils() > 0) {
            village.lord().setTreasury(Math.min(LordshipManager.treasuryCap(village), village.lord().treasury() + won.getSpoils()));
        }
        FealtyWorldData.get(server).setDirty();

        for (ServerPlayer fighter : fighters) {
            if (!fighter.getUUID().equals(lord.getUUID())) {
                RepManager.meet(fighter, village.id());
                RepManager.applySource(fighter, village.id(), RepSources.RAID_STRONGHOLD);
            }
            FealtyEvents.fire(fighter, FealtyEvents.STRONGHOLD_RAZED);
            Feedback.banner(fighter, Component.translatable("fealty.war.won"), Component.translatable("fealty.war.won.detail", target.name(),
                    captives.size(), won.getPeaceDays()), 0xD4AF37, "crown");
            Feedback.sound(fighter, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
        }
        FealtyEvents.fire(lord, FealtyEvents.WARLORD);
        quest(lord).ifPresent(QuestContext::setReady);
        MailService.fromVillage(server, lord.getUUID(), village, "war_victory", List.of(), 20 * 60, target.name(), captives.size());
        HOMEWARD.add(new Homeward(lord.getUUID(), village.id(), level.getGameTime() + 1200));
    }

    /** Fly the village's colours over the stronghold, and leave its War Banner in tatters. */
    private static void flyColours(ServerLevel level, VillageRecord village, Strongholds.Entry target) {
        BlockState banner = BannerBlock.byColor(nearestDye(village.color())).defaultBlockState();
        if (target.kind() == Stronghold.Kind.CAMP) {
            WarBannerBlockEntity war = WarBannerBlockEntity.at(level, target.pos());
            if (war != null) {
                BlockState state = level.getBlockState(target.pos());
                if (state.hasProperty(WarBannerBlock.RAZED)) {
                    level.setBlock(target.pos(), state.setValue(WarBannerBlock.RAZED, true), Block.UPDATE_ALL);
                }
                war.raiseVictoryBanner(level, banner);
                return;
            }
        }
        if (!level.isLoaded(target.pos())) {
            return;
        }
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.pos());
        if (level.getBlockState(ground).canBeReplaced() && level.getBlockState(ground.below()).isSolid()) {
            level.setBlock(ground, banner, Block.UPDATE_ALL);
        }
    }

    /** The banner colour closest to a village's colour. */
    public static DyeColor nearestDye(int rgb) {
        DyeColor best = DyeColor.WHITE;
        int bestDistance = Integer.MAX_VALUE;
        for (DyeColor dye : DyeColor.values()) {
            int c = dye.getTextureDiffuseColor();
            int dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            int dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            int db = (c & 0xFF) - (rgb & 0xFF);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = dye;
            }
        }
        return best;
    }

    /**
     * End a raid without victory.
     *
     * @param failQuest whether to fail the raid quest too (false when the quest ending is what ended the raid)
     */
    private static void end(MinecraftServer server, State state, CampaignEvent.Ended.Reason reason, boolean failQuest) {
        if (!ENDING.add(state.lord)) {
            return;
        }
        try {
            Campaigns data = get(server);
            data.states.remove(state.lord);
            data.setDirty();
            Battle battle = BATTLES.remove(state.lord);
            if (battle != null) {
                battle.bar.removeAllPlayers();
            }
            long day = RepManager.day(server);
            Stronghold view = Strongholds.get(server).get(state.stronghold).map(e -> e.view(day))
                    .orElse(new Stronghold(state.stronghold, server.overworld().dimension(), BlockPos.ZERO, Stronghold.Kind.CAMP,
                            Strongholds.PILLAGER_CAMP, Component.empty(), false, 0));
            NeoForge.EVENT_BUS.post(new CampaignEvent.Ended(server, state.lord, state.village, view, reason));
            ServerPlayer lord = server.getPlayerList().getPlayer(state.lord);
            Optional<VillageRecord> village = FealtyWorldData.get(server).village(state.village);
            if (lord != null) {
                if (failQuest) {
                    quest(lord).ifPresent(ctx -> QuestManager.failQuietly(ctx, reason == CampaignEvent.Ended.Reason.TIMED_OUT
                            ? RepQuestEvent.Reason.EXPIRED : RepQuestEvent.Reason.ABANDONED));
                }
                if (village.isPresent() && reason != CampaignEvent.Ended.Reason.CALLED_OFF && reason != CampaignEvent.Ended.Reason.LORDSHIP_LOST) {
                    RepManager.applySource(lord, state.village, RepSources.FAILED_CAMPAIGN);
                }
                lord.sendSystemMessage(Component.translatable("fealty.war.ended." + reason.name().toLowerCase(java.util.Locale.ROOT), view.name())
                        .withStyle(ChatFormatting.GRAY));
                if (village.isPresent()) {
                    GarrisonManager.sendHome(lord, village.get());
                }
            }
            disbandLevy(server, state.lord);
        } finally {
            ENDING.remove(state.lord);
        }
    }

    /** The lord's raid quest, if it is still running. */
    private static Optional<QuestContext> quest(ServerPlayer lord) {
        return QuestManager.context(lord, GIVER, QUEST);
    }

    /** After a victory the warband lingers a minute, then goes home. */
    private static void homeward(MinecraftServer server) {
        if (HOMEWARD.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        Iterator<Homeward> it = HOMEWARD.iterator();
        while (it.hasNext()) {
            Homeward next = it.next();
            if (now < next.due()) {
                continue;
            }
            it.remove();
            ServerPlayer lord = server.getPlayerList().getPlayer(next.lord());
            Optional<VillageRecord> village = FealtyWorldData.get(server).village(next.village());
            if (lord != null && village.isPresent() && !get(server).states.containsKey(next.lord())) {
                GarrisonManager.sendHome(lord, village.get());
            }
            disbandLevy(server, next.lord());
        }
    }

    /** The militia go home: they leave the world wherever they are. */
    private static void disbandLevy(MinecraftServer server, UUID lord) {
        for (ServerLevel level : server.getAllLevels()) {
            List<VillageGuardEntity> levy = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof VillageGuardEntity guard && guard.getTags().contains(LEVY_TAG)
                        && guard.getPersistentData().hasUUID(LEVY_LORD) && lord.equals(guard.getPersistentData().getUUID(LEVY_LORD))) {
                    levy.add(guard);
                }
            }
            for (VillageGuardEntity guard : levy) {
                level.sendParticles(ParticleTypes.POOF, guard.getX(), guard.getY() + 0.8, guard.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                guard.discard();
            }
        }
    }

    // ---- Events ----

    /** A defender falls (one fewer on the bar), or the lord does (the raid is lost). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || BATTLES.isEmpty()) {
            return;
        }
        MinecraftServer server = victim.getServer();
        if (server == null) {
            return;
        }
        if (victim instanceof ServerPlayer player && BATTLES.containsKey(player.getUUID())) {
            State state = get(server).states.get(player.getUUID());
            if (state != null) {
                end(server, state, CampaignEvent.Ended.Reason.LORD_DIED, true);
            }
            return;
        }
        for (Battle battle : BATTLES.values()) {
            if (battle.defenders.remove(victim.getUUID())) {
                if (event.getSource().getEntity() instanceof ServerPlayer killer && !killer.isCreative()) {
                    battle.fighters.add(killer.getUUID());
                }
                return;
            }
        }
    }

    /** Abandoning the raid quest calls the raid off. */
    @SubscribeEvent
    public static void onQuestFailed(RepQuestEvent.Fail event) {
        if (!event.getQuest().equals(QUEST) || ENDING.contains(event.getPlayer().getUUID())) {
            return;
        }
        State state = get(event.getPlayer().server).states.get(event.getPlayer().getUUID());
        if (state != null) {
            end(event.getPlayer().server, state, CampaignEvent.Ended.Reason.CALLED_OFF, false);
        }
    }

    /**
     * No illagers spawn on a razed stronghold's ground or where a battle is on, and no pillager patrols turn up in a
     * village at peace.
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        MobSpawnType type = event.getSpawnType();
        if (!(event.getLevel().getLevel() instanceof ServerLevel level) || !mob.getType().is(FealtyTags.Entities.STRONGHOLD_DEFENDERS)
                || !(type == MobSpawnType.NATURAL || type == MobSpawnType.STRUCTURE || type == MobSpawnType.PATROL
                || type == MobSpawnType.CHUNK_GENERATION)) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (quietAt(level, pos, type)) {
            event.setSpawnCancelled(true);
        }
    }

    /** Whether illagers may not spawn here: a razed or embattled stronghold, or (patrols) a village at peace. */
    public static boolean quietAt(ServerLevel level, BlockPos pos, MobSpawnType type) {
        MinecraftServer server = level.getServer();
        long day = RepManager.day(server);
        Strongholds.Entry entry = Strongholds.get(server).around(level.dimension(), pos, 32);
        if (entry != null && (entry.isRazed(day) || fightingAt(level, entry))) {
            return true;
        }
        if (type == MobSpawnType.PATROL) {
            for (VillageRecord village : FealtyWorldData.get(server).villages()) {
                if (village.sites().atPeace(day) && village.dimension().equals(level.dimension())
                        && AABB.of(village.bounds()).inflate(64).contains(pos.getCenter())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Once a day: a village's peace runs out. */
    public static void tickDay(MinecraftServer server, long day) {
        for (VillageRecord village : FealtyWorldData.get(server).villages()) {
            long until = village.sites().peaceUntil();
            if (until >= 0 && day >= until) {
                village.sites().setPeaceUntil(-1L);
                FealtyWorldData.get(server).setDirty();
                NeoForge.EVENT_BUS.post(new VillageEvent(server, village.id(), VillageEvent.Type.PEACE_ENDED));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Battle battle = BATTLES.remove(event.getEntity().getUUID());
        if (battle != null) {
            battle.bar.removeAllPlayers(); // the fight starts afresh when they return
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BATTLES.values().forEach(battle -> battle.bar.removeAllPlayers());
        BATTLES.clear();
        HOMEWARD.clear();
        ENDING.clear();
    }

    /** Only used by tests and commands: make the lord's raid a victory now. */
    public static boolean forceWin(ServerPlayer lord) {
        MinecraftServer server = lord.server;
        State state = get(server).states.get(lord.getUUID());
        Optional<VillageRecord> village = state == null ? Optional.empty() : FealtyWorldData.get(server).village(state.village);
        Optional<Strongholds.Entry> target = state == null ? Optional.empty() : Strongholds.get(server).get(state.stronghold);
        if (state == null || village.isEmpty() || target.isEmpty()) {
            return false;
        }
        Battle battle = BATTLES.computeIfAbsent(lord.getUUID(), k -> new Battle(target.get().name(), lord.level().getGameTime()));
        battle.fighters.add(lord.getUUID());
        win(lord, village.get(), state, target.get(), battle);
        return true;
    }

    /** Whether an entity marches with a lord's raid (their retinue or the levy). */
    public static boolean isWarbandMember(Entity entity) {
        if (entity.getTags().contains(LEVY_TAG)) {
            return true;
        }
        if (entity instanceof Mob mob && mob.hasData(ModAttachments.GUARD_ORDERS) && entity.getServer() != null) {
            GuardOrders orders = mob.getData(ModAttachments.GUARD_ORDERS);
            return orders.leader() != null && get(entity.getServer()).states.containsKey(orders.leader());
        }
        return false;
    }

    @Nullable
    static State state(MinecraftServer server, UUID lord) {
        return get(server).states.get(lord);
    }
}
