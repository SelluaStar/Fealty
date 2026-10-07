package com.selluastar.fealty.guard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.api.GuardStance;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.Severity;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.crime.Fines;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.entity.VillageGuardEntity;
import com.selluastar.fealty.mixin.MobAccessor;
import com.selluastar.fealty.outlaw.HeatManager;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.PlayerRepData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * Guards read the same tier table as villagers. Any mob in {@code #fealty:guards} (iron golems, Guard Villagers'
 * guards, ...) gets Fealty's goals when it joins the world, so no guard mod needs to depend on Fealty.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class GuardManager {
    private static final Map<String, Long> GREETED = new HashMap<>();
    private static final int GREET_COOLDOWN = 2400;

    private GuardManager() {
    }

    /**
     * Whether a mob guards a village. Golems players build are their own, except those a lord builds in a village
     * they rule, which join its guard.
     */
    public static boolean isGuard(Mob mob) {
        return mob.getType().is(FealtyTags.Entities.GUARDS)
                && !(mob instanceof IronGolem golem && golem.isPlayerCreated() && !golem.hasData(ModAttachments.FACTION));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof PathfinderMob mob)
                || !mob.getType().is(FealtyTags.Entities.GUARDS)) {
            return;
        }
        if (mob instanceof IronGolem golem && golem.isPlayerCreated() && !event.loadedFromDisk() && !golem.hasData(ModAttachments.FACTION)) {
            joinLordsVillage(level, golem);
        }
        MobAccessor accessor = (MobAccessor) mob;
        accessor.fealty$getTargetSelector().addGoal(1, new GuardHostilePlayerGoal(mob));
        accessor.fealty$getTargetSelector().addGoal(2, new GuardAssistGoal(mob));
        accessor.fealty$getGoalSelector().addGoal(0, new GuardStandDownGoal(mob));
        // Above the golem's own walk back to the village (priority 2), so a lord's orders win.
        accessor.fealty$getGoalSelector().addGoal(1, new GuardOrdersGoal(mob));
        accessor.fealty$getGoalSelector().addGoal(5, new GuardWatchGoal(mob));
    }

    /** A golem built by the lord of the village it stands in becomes one of that village's guards. */
    private static void joinLordsVillage(ServerLevel level, IronGolem golem) {
        Optional<VillageRecord> village = VillageResolver.villageAt(level, golem.blockPosition());
        if (village.isEmpty() || village.get().lord().uuid() == null) {
            return;
        }
        Player builder = level.getNearestPlayer(golem, 8.0);
        if (builder != null && village.get().lord().isLord(builder.getUUID())) {
            golem.setData(ModAttachments.FACTION, village.get().id());
        }
    }

    /** Whether a guard should attack the player right now. */
    public static boolean isHostileTo(Mob guard, ServerPlayer player) {
        if (!isGuard(guard)) {
            return false;
        }
        Optional<ResourceLocation> faction = FactionResolver.factionOf(guard);
        return faction.isPresent() && isHostileTo(faction.get(), player);
    }

    public static boolean isHostileTo(ResourceLocation faction, ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        PlayerRepData data = RepManager.data(player);
        Long aggro = data.aggroUntil().get(faction);
        if (aggro != null && aggro > player.level().getGameTime()) {
            return true;
        }
        if (RepManager.getTier(player, faction).guardStance() == GuardStance.ATTACK_ON_SIGHT) {
            return true;
        }
        return HeatManager.wantedLevel(player, faction) > 0;
    }

    /**
     * Whether a guard may fight the player: the village wants them hurt, or they just struck this guard and are
     * not someone the village forgives (see {@code guards_forgive_at_rep}).
     */
    public static boolean mayFight(Mob guard, ServerPlayer player) {
        if (isHostileTo(guard, player)) {
            return true;
        }
        boolean provoked = guard.getLastHurtByMob() == player && guard.tickCount - guard.getLastHurtByMobTimestamp() < 200;
        if (!provoked) {
            return false;
        }
        Optional<ResourceLocation> faction = FactionResolver.factionOf(guard);
        return faction.isEmpty() || RepManager.getRep(player, faction.get()) < FealtyConfig.GUARDS_FORGIVE_AT.get();
    }

    /**
     * Guards only take aim at a player when Fealty's rules allow it. This also stops iron golems and other mods'
     * guards from turning on a player by their own AI (for a stray hit on a villager, say): the crime system decides.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getNewAboutToBeSetTarget() instanceof ServerPlayer player
                && !mob.level().isClientSide && isGuard(mob) && !mayFight(mob, player)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    public static GuardStance stance(ServerPlayer player, ResourceLocation faction) {
        return RepManager.getTier(player, faction).guardStance();
    }

    /**
     * A witnessed crime alerts guards nearby. A player the village honoured before the crime is only scolded: the
     * crime costs them rep and nothing more. Otherwise severe crimes, or any crime by a player the guards already
     * watch, turn them hostile; lesser crimes get a warning first (a Fealty guard demands a fine), and a repeat
     * offence turns them hostile.
     *
     * @param change what the crime cost the player with the village
     */
    public static void alert(ServerLevel level, ServerPlayer player, ResourceLocation faction, BlockPos pos, Severity severity,
                             ResourceLocation crime, int change) {
        int radius = FealtyConfig.GUARD_ALERT_RADIUS.get();
        List<Mob> guards = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(radius), m -> m.isAlive() && isGuard(m));
        if (guards.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        PlayerRepData data = RepManager.data(player);
        if (RepManager.getRep(player, faction) - change >= FealtyConfig.GUARDS_FORGIVE_AT.get()) {
            forgive(player, faction, guards);
            return;
        }
        RepTier tier = RepManager.getTier(player, faction);
        boolean lowRenown = RepManager.renown(player) <= FealtyConfig.OUTSIDE_GUARD_RENOWN.get();
        Long warned = data.warnedAt().get(faction);
        boolean repeat = warned != null && now - warned <= FealtyConfig.GUARD_WARNING_WINDOW.get();
        boolean attack = severity == Severity.SEVERE || repeat
                || tier.guardStance() == GuardStance.ATTACK_ON_SIGHT || tier.guardStance() == GuardStance.WATCH;

        if (attack) {
            // (Set first, so the guards are allowed to take aim below.)
            data.aggroUntil().put(faction, now + FealtyConfig.GUARD_AGGRO_TICKS.get());
        }
        Mob speaker = null;
        for (Mob guard : guards) {
            Optional<ResourceLocation> guardFaction = FactionResolver.factionOf(guard);
            boolean ownGuard = guardFaction.isPresent() && guardFaction.get().equals(faction);
            if (!ownGuard && !lowRenown) {
                continue;
            }
            if (speaker == null || guard.distanceToSqr(player) < speaker.distanceToSqr(player)) {
                speaker = guard;
            }
            if (attack) {
                guard.setTarget(player);
            } else {
                guard.getLookControl().setLookAt(player);
            }
        }
        if (speaker == null) {
            return;
        }
        if (attack) {
            player.sendSystemMessage(Component.translatable("fealty.guard.attack", speaker.getDisplayName()));
        } else {
            data.warnedAt().put(faction, now);
            // Guards who can speak demand a fine (golems just stare the player down).
            boolean fined = !(speaker instanceof IronGolem) && FactionResolver.factionOf(speaker).filter(faction::equals).isPresent()
                    && Fines.issue(player, speaker, faction, crime, change);
            if (!fined) {
                player.sendSystemMessage(Component.translatable("fealty.guard.warning", speaker.getDisplayName()));
            }
        }
        FealtyWorldData.get(level.getServer()).setDirty();
    }

    /** The nearest of the village's guards looks the player over and lets it go. */
    private static void forgive(ServerPlayer player, ResourceLocation faction, List<Mob> guards) {
        Mob speaker = null;
        for (Mob guard : guards) {
            if (FactionResolver.factionOf(guard).filter(faction::equals).isPresent()
                    && (speaker == null || guard.distanceToSqr(player) < speaker.distanceToSqr(player))) {
                speaker = guard;
            }
        }
        if (speaker != null) {
            speaker.getLookControl().setLookAt(player);
            player.displayClientMessage(Component.translatable("fealty.guard.forgiven", speaker.getDisplayName()), true);
        }
    }

    /** Called every second for a player inside a village: guards greet friends. */
    public static void tickPlayer(ServerPlayer player, VillageRecord village) {
        GuardStance stance = stance(player, village.id());
        if (!stance.assists()) {
            return;
        }
        long now = player.level().getGameTime();
        List<Mob> guards = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6),
                m -> m.isAlive() && isGuard(m) && m.getTarget() == null && m.hasLineOfSight(player));
        for (Mob guard : guards) {
            String key = guard.getUUID() + "|" + player.getUUID();
            Long last = GREETED.get(key);
            if (last == null || now - last > GREET_COOLDOWN) {
                GREETED.put(key, now);
                guard.getLookControl().setLookAt(player);
                if (guard instanceof VillageGuardEntity) {
                    // Fealty guards say it out loud.
                    Speech.bark(guard, "guard_greet", player, 0);
                    break;
                }
                int line = guard.getRandom().nextInt(4);
                String key2 = village.lord().isLord(player.getUUID()) ? "fealty.guard.greet_lord." + line
                        : (stance == GuardStance.ESCORT ? "fealty.guard.greet_hero." : "fealty.guard.greet.") + line;
                player.displayClientMessage(Component.translatable(key2, guard.getDisplayName(), player.getDisplayName()), true);
                break;
            }
        }
        if (GREETED.size() > 1024) {
            GREETED.entrySet().removeIf(e -> now - e.getValue() > GREET_COOLDOWN);
        }
    }

    /**
     * Whether Fealty talks for this NPC as a guard: its own guards, and other mods' guards in {@code #fealty:guards}
     * that belong to a village. Iron golems have nothing to say.
     */
    public static boolean talksLikeGuard(Entity npc) {
        if (npc instanceof VillageGuardEntity) {
            return true;
        }
        return npc instanceof Mob mob && !(mob instanceof IronGolem) && isGuard(mob) && FactionResolver.factionOf(mob).isPresent();
    }
}
