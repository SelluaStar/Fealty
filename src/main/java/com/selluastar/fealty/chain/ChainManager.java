package com.selluastar.fealty.chain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.chain.ChainContext;
import com.selluastar.fealty.api.chain.ChainHandler;
import com.selluastar.fealty.api.dialogue.DialogueContext;
import com.selluastar.fealty.api.event.ChainStageEvent;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.network.OpenQuestScreenPayload.ActionEntry;
import com.selluastar.fealty.network.OpenQuestScreenPayload;
import com.selluastar.fealty.network.QuestActionPayload;
import com.selluastar.fealty.outlaw.ThievesGuild;
import com.selluastar.fealty.quest.ActiveQuest;
import com.selluastar.fealty.quest.QuestGiver;
import com.selluastar.fealty.quest.QuestGivers;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.PlayerRepData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.story.script.Scripts;
import com.selluastar.fealty.story.script.Texts;
import com.selluastar.fealty.util.Inventories;
import com.selluastar.fealty.util.Maps;
import com.selluastar.fealty.village.VillageNames;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Quest chains. The rare villager chain: rumours from the elder, named villagers who each set one task (roles and
 * tasks are picked for each run, and any trade can be picked, the village fool and the jobless included), a map to the
 * hidden hamlet, one of the Keeper's trials, and the Royal Writ. Other chains (stories from data packs, kinds other
 * mods register) share the same runs, named villagers or single giver, step bookkeeping and rewards; what is special
 * to a kind is up to its {@link ChainHandler}.
 */
public final class ChainManager {
    public static final String ACTION_RUMOURS = "rumours";
    public static final String ACTION_COMBINE = "combine";
    public static final ResourceLocation RARE_CHAIN = Fealty.id("rare_villager");
    private static final ResourceLocation FOUND_HAMLET = Fealty.id("found_hamlet");

    public static final int STAGE_NONE = 0;
    public static final int STAGE_STEPS = 1;
    public static final int STAGE_HAMLET = 2;
    public static final int STAGE_TRIAL = 3;
    public static final int STAGE_COMBINE = 4;
    public static final int STAGE_DONE = 5;

    /** The steps of the rare villager chain before runs were picked from a pool, in their old order. */
    private static final List<String> LEGACY_STEPS = List.of("smith", "cartographer", "cleric");

    public static final QuestGiver NAMED_VILLAGER = new NamedVillagerGiver();
    public static final QuestGiver KEEPER = new KeeperGiver();

    private ChainManager() {
    }

    // ---- Keys ----

    public static ResourceLocation stepKey(ResourceLocation chain, int step) {
        return ResourceLocation.fromNamespaceAndPath(chain.getNamespace(), "chain/" + chain.getPath() + "/step_" + step);
    }

    public static ResourceLocation trialKey(ResourceLocation chain) {
        return ResourceLocation.fromNamespaceAndPath(chain.getNamespace(), "chain/" + chain.getPath() + "/trial");
    }

    /** A quest-log key pointing into a chain: a step index, or -1 for the trial. */
    public record ChainKey(ResourceLocation chain, int step) {
    }

    public static Optional<ChainKey> parse(ResourceLocation key) {
        String path = key.getPath();
        if (!path.startsWith("chain/")) {
            return Optional.empty();
        }
        String rest = path.substring("chain/".length());
        int slash = rest.lastIndexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        ResourceLocation chain = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), rest.substring(0, slash));
        String tail = rest.substring(slash + 1);
        if (tail.equals("trial")) {
            return Optional.of(new ChainKey(chain, -1));
        }
        if (tail.startsWith("step_")) {
            try {
                return Optional.of(new ChainKey(chain, Integer.parseInt(tail.substring(5))));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static boolean hasRole(Villager villager) {
        return villager.hasData(ModAttachments.CHAIN_ROLE) && !villager.getData(ModAttachments.CHAIN_ROLE).isNone();
    }

    private static Optional<QuestChainDefinition> rareChain() {
        return FealtyDataManager.chain(RARE_CHAIN);
    }

    @Nullable
    private static ChainProgress progress(ServerPlayer player, ResourceLocation chain) {
        return RepManager.data(player).chains().get(chain);
    }

    private static Optional<VillageRecord> record(ServerPlayer player, @Nullable ResourceLocation village) {
        return village == null ? Optional.empty() : FealtyWorldData.get(player.server).village(village);
    }

    /** The player's stage in a chain (0 before it starts). */
    public static int stage(ServerPlayer player, ResourceLocation chain) {
        ChainProgress progress = progress(player, chain);
        return progress == null ? STAGE_NONE : progress.stage();
    }

    /** How many steps of the current run the player has handed in. */
    public static int stepsDone(ServerPlayer player, ResourceLocation chain) {
        ChainProgress progress = progress(player, chain);
        return progress == null ? 0 : progress.stepsDone().size();
    }

    /** Whether a run is giving out its steps: the rare chain only in its steps stage, other kinds until they are done. */
    static boolean inSteps(QuestChainDefinition def, ChainProgress progress) {
        return def.isRare() ? progress.stage() == STAGE_STEPS : progress.stage() >= STAGE_STEPS && progress.stage() < STAGE_DONE;
    }

    // ---- Roles ----

    /** The role a villager holds. Roles saved before roles had names are read from their old step number. */
    public static Optional<String> roleOf(Villager villager, QuestChainDefinition def) {
        if (!hasRole(villager)) {
            return Optional.empty();
        }
        ChainRole role = villager.getData(ModAttachments.CHAIN_ROLE);
        if (!role.role().isEmpty()) {
            return Optional.of(role.role());
        }
        if (!def.pooled() && role.step() < def.steps().size()) {
            return Optional.of(def.steps().get(role.step()).role());
        }
        return role.step() < LEGACY_STEPS.size() ? Optional.of(LEGACY_STEPS.get(role.step())) : Optional.empty();
    }

    /** Whether a villager is this village's holder of a role in a chain. */
    private static boolean holds(Villager villager, QuestChainDefinition def, ResourceLocation chain, ResourceLocation village, String role) {
        if (!hasRole(villager)) {
            return false;
        }
        ChainRole held = villager.getData(ModAttachments.CHAIN_ROLE);
        return held.chain().equals(chain) && held.village().equals(village) && roleOf(villager, def).map(role::equals).orElse(false);
    }

    private static boolean fits(Villager villager, QuestChainDefinition.Step step) {
        return step.professions().isEmpty() || step.professions().contains(profession(villager));
    }

    private static ResourceLocation profession(Villager villager) {
        return BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
    }

    private static Component roleTitle(QuestChainDefinition.Step step) {
        return step.title().orElse(Component.translatable("fealty.chain.role." + step.role()));
    }

    /** The villager's own name, without a role title. */
    private static Optional<String> personName(Villager villager) {
        Component name = villager.getCustomName();
        if (name == null) {
            return Optional.empty();
        }
        if (name.getContents() instanceof TranslatableContents contents && contents.getKey().equals("fealty.chain.named")
                && contents.getArgs().length > 0) {
            Object first = contents.getArgs()[0];
            return Optional.of(first instanceof Component component ? component.getString() : String.valueOf(first));
        }
        return Optional.of(name.getString());
    }

    /** The title of a single-giver chain's giver. */
    private static Component giverTitle(QuestChainDefinition def) {
        return def.giverTitle().orElseGet(() -> Component.translatableWithFallback("fealty.chain.role." + def.giverRole(), def.giverRole()));
    }

    private static void giveRole(Villager villager, ResourceLocation chain, ResourceLocation village, String role, Component title, boolean fixed) {
        villager.setData(ModAttachments.CHAIN_ROLE, new ChainRole(chain, 0, village, role, fixed));
        String name = personName(villager).orElseGet(() -> VillageNames.personName(villager.getRandom()));
        villager.setCustomName(Component.translatable("fealty.chain.named", name, title));
        villager.setCustomNameVisible(true);
        lockProfession(villager);
    }

    /**
     * Give a villager a role that stays until taken away (data packs and other mods, through the API). It belongs to the
     * village the villager lives in.
     *
     * @return false if the villager holds a role in another chain
     */
    public static boolean assignRole(Villager villager, ResourceLocation chain, String role, @Nullable Component title) {
        if (hasRole(villager) && !villager.getData(ModAttachments.CHAIN_ROLE).chain().equals(chain)) {
            return false;
        }
        ResourceLocation village = FactionResolver.factionOf(villager).filter(Factions::isVillage).orElse(ResourceLocation.withDefaultNamespace("none"));
        Component shown = title != null ? title : FealtyDataManager.chain(chain).filter(def -> def.single() && def.giverRole().equals(role))
                .map(ChainManager::giverTitle).orElseGet(() -> Component.translatableWithFallback("fealty.chain.role." + role, role));
        giveRole(villager, chain, village, role, shown, true);
        return true;
    }

    /** Whether a villager holds this role in this chain. */
    public static boolean holdsRole(Villager villager, ResourceLocation chain, String role) {
        if (!hasRole(villager)) {
            return false;
        }
        ChainRole held = villager.getData(ModAttachments.CHAIN_ROLE);
        return held.chain().equals(chain) && role.equals(FealtyDataManager.chain(chain).flatMap(def -> roleOf(villager, def)).orElse(held.role()));
    }

    /** The villager keeps their name but loses the title and the role. */
    public static void clearRole(Villager villager) {
        Optional<String> name = personName(villager);
        villager.removeData(ModAttachments.CHAIN_ROLE);
        name.ifPresent(n -> villager.setCustomName(Component.literal(n)));
        villager.setCustomNameVisible(false);
    }

    /**
     * Vanilla lets a villager with no trading experience lose their profession when their workstation goes; a named
     * villager keeps theirs, so their title stays true.
     */
    public static void lockProfession(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT && villager.getVillagerXp() < 1) {
            villager.setVillagerXp(1);
        }
    }

    /**
     * Whether a player's run in this village is still gathering its steps and has someone in this role (or, for a
     * single-giver chain, needs its giver).
     */
    private static boolean roleNeeded(MinecraftServer server, ResourceLocation chain, ResourceLocation village, String role) {
        Optional<QuestChainDefinition> def = FealtyDataManager.chain(chain);
        boolean giver = def.isPresent() && def.get().single() && role.equals(def.get().giverRole());
        for (PlayerRepData data : FealtyWorldData.get(server).players().values()) {
            ChainProgress progress = data.chains().get(chain);
            if (progress == null || !village.equals(progress.origin())) {
                continue;
            }
            if (giver) {
                if (inSteps(def.get(), progress)) {
                    return true;
                }
                continue;
            }
            if (progress.stage() != STAGE_STEPS) {
                continue;
            }
            if (progress.run().isEmpty() || progress.indexOf(role) >= 0) {
                return true; // (a run saved before runs were laid out needs everyone it had)
            }
        }
        return false;
    }

    /**
     * Called when someone talks to a villager: a role no run needs any more is dropped (the runs that used it may
     * have ended while the villager was far away), and a named villager's profession is kept.
     */
    public static void checkRole(Villager villager) {
        if (!hasRole(villager) || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        ChainRole role = villager.getData(ModAttachments.CHAIN_ROLE);
        if (role.fixed()) {
            lockProfession(villager);
            return;
        }
        Optional<String> key = FealtyDataManager.chain(role.chain()).flatMap(def -> roleOf(villager, def));
        if (key.isEmpty() || !roleNeeded(level.getServer(), role.chain(), role.village(), key.get())) {
            clearRole(villager);
        } else {
            lockProfession(villager);
        }
    }

    /** Drop the roles in a village that no run needs any more (villagers that are loaded). */
    private static void releaseRoles(MinecraftServer server, ResourceLocation chain, @Nullable ResourceLocation villageId) {
        Optional<VillageRecord> village = villageId == null ? Optional.empty() : FealtyWorldData.get(server).village(villageId);
        Optional<QuestChainDefinition> def = FealtyDataManager.chain(chain);
        ServerLevel level = village.map(v -> server.getLevel(v.dimension())).orElse(null);
        if (village.isEmpty() || def.isEmpty() || level == null) {
            return;
        }
        for (Villager villager : villagersOf(level, village.get())) {
            if (!hasRole(villager) || !villager.getData(ModAttachments.CHAIN_ROLE).chain().equals(chain)
                    || !villager.getData(ModAttachments.CHAIN_ROLE).village().equals(villageId) || villager.getData(ModAttachments.CHAIN_ROLE).fixed()) {
                continue;
            }
            Optional<String> key = roleOf(villager, def.get());
            if (key.isEmpty() || !roleNeeded(server, chain, villageId, key.get())) {
                clearRole(villager);
            }
        }
    }

    // ---- The elder's rumours ----

    public static Optional<ActionEntry> rumoursAction(ServerPlayer player, VillageRecord village) {
        Optional<QuestChainDefinition> def = rareChain();
        if (def.isEmpty() || !def.get().isRare()) {
            return Optional.empty();
        }
        Component label = Component.translatable("fealty.chain.rumours");
        ChainProgress progress = progress(player, RARE_CHAIN);
        if (progress != null && progress.stage() > STAGE_NONE && progress.stage() < STAGE_DONE) {
            return village.id().equals(progress.origin())
                    ? Optional.of(ActionEntry.disabled(ACTION_RUMOURS, label, hint(player, progress, def.get())))
                    : Optional.empty();
        }
        if (progress != null && progress.stage() == STAGE_DONE && !def.get().repeatable()) {
            return Optional.empty();
        }
        Optional<RepTier> min = TierManager.byId(def.get().startTier());
        if (min.isPresent() && RepManager.getTier(player, village.id()).rank() < min.get().rank()) {
            return Optional.of(ActionEntry.disabled(ACTION_RUMOURS, label,
                    Component.translatable("fealty.chain.rumours_locked", min.get().displayName())));
        }
        return Optional.of(ActionEntry.of(ACTION_RUMOURS, label));
    }

    /** The elder tells of the rare villager chain (what the elder's "rumours" did before rumours came from data). */
    public static void startFromElder(ServerPlayer player, VillageRecord village) {
        start(player, RARE_CHAIN, village);
    }

    // ---- Starting chains ----

    /**
     * Whether a chain can start for a player in a village: its kind is registered (and is not the guild, which the
     * fence starts), it is not running, it is repeatable if done, and the player is trusted enough there.
     */
    public static boolean canStart(ServerPlayer player, ResourceLocation chainId, VillageRecord village) {
        Optional<QuestChainDefinition> def = FealtyDataManager.chain(chainId);
        if (def.isEmpty() || def.get().isGuild() || ChainKinds.handler(def.get()).isEmpty()) {
            return false;
        }
        ChainProgress progress = progress(player, chainId);
        if (progress != null && progress.stage() > STAGE_NONE && progress.stage() < STAGE_DONE) {
            return false;
        }
        if (progress != null && progress.stage() == STAGE_DONE && !def.get().repeatable()) {
            return false;
        }
        Optional<RepTier> min = TierManager.byId(def.get().startTier());
        return min.isEmpty() || RepManager.getTier(player, village.id()).rank() >= min.get().rank();
    }

    /**
     * Start a chain for a player in a village: lay out a run, ask its kind's handler, and give its villagers their
     * roles. {@code ChainStageEvent.Start} may stop it first.
     *
     * @return whether it started
     */
    public static boolean start(ServerPlayer player, ResourceLocation chainId, VillageRecord village) {
        if (!canStart(player, chainId, village)) {
            return false;
        }
        QuestChainDefinition def = FealtyDataManager.chain(chainId).orElseThrow();
        ChainHandler handler = ChainKinds.handler(def).orElseThrow();
        if (NeoForge.EVENT_BUS.post(new ChainStageEvent.Start(player, chainId, Optional.of(village.id()))).isCanceled()) {
            return false;
        }
        PlayerRepData data = RepManager.data(player);
        ChainProgress saved = data.chains().containsKey(chainId) ? data.chains().get(chainId).copy() : null;
        ChainProgress progress = data.chains().computeIfAbsent(chainId, ChainProgress::new);
        ResourceLocation previous = progress.origin();
        if (progress.stage() == STAGE_DONE) {
            progress.reset();
        }
        progress.setOrigin(village.id());
        progress.setStage(STAGE_STEPS);
        progress.stepsDone().clear();
        progress.setTarget(null);
        progress.setTrial(null);
        progress.run().clear();
        List<Villager> villagers = villagersOf(player.serverLevel(), village);
        progress.run().addAll(pickRun(villagers, village, def, chainId, player.getRandom()));
        if (!handler.start(new Context(player, chainId, def, progress))) {
            if (saved != null) {
                data.chains().put(chainId, saved);
            } else {
                data.chains().remove(chainId);
            }
            return false;
        }
        FealtyWorldData.get(player.server).setDirty();
        if (previous != null && !previous.equals(village.id())) {
            releaseRoles(player.server, chainId, previous);
        }
        List<Component> names = assignRoles(player.serverLevel(), village, def, chainId, progress.run());
        if (def.isRare()) {
            player.sendSystemMessage(Component.translatable("fealty.chain.rumours_told", ComponentUtils.formatList(names, Component.literal(", "))));
            Feedback.toast(player, "crown", Component.translatable("fealty.toast.chain_started"),
                    Component.translatable("fealty.toast.chain_people", names.size()));
            FealtyEvents.fire(player, FealtyEvents.CHAIN_STARTED);
        } else {
            player.sendSystemMessage(Component.translatable("fealty.chain.story.started", ComponentUtils.formatList(names, Component.literal(", "))));
            Feedback.toast(player, "seal", Component.translatable("fealty.toast.story_started"),
                    Component.translatable("fealty.toast.story_people", names.size()));
        }
        return true;
    }

    /**
     * Whether a rumour of a chain's stage has news for the player: stage 1 if the chain can start in this village, a
     * later stage if a running chain is one short of it and its kind lets rumours move it on.
     */
    public static boolean rumourFits(ServerPlayer player, ResourceLocation chainId, int stage, @Nullable VillageRecord village) {
        if (stage <= STAGE_STEPS) {
            return village != null && canStart(player, chainId, village);
        }
        Optional<QuestChainDefinition> def = FealtyDataManager.chain(chainId);
        Optional<ChainHandler> handler = def.flatMap(ChainKinds::handler);
        ChainProgress progress = progress(player, chainId);
        if (def.isEmpty() || handler.isEmpty() || progress == null || progress.stage() != stage - 1 || !inSteps(def.get(), progress)) {
            return false;
        }
        return handler.get().canAdvance(new Context(player, chainId, def.get(), progress), stage);
    }

    /** A rumour of a chain's stage was heard: stage 1 starts the chain, a later one moves it on. */
    public static boolean reachStage(ServerPlayer player, ResourceLocation chainId, int stage, @Nullable VillageRecord village) {
        if (stage <= STAGE_STEPS) {
            return village != null && start(player, chainId, village);
        }
        if (!rumourFits(player, chainId, stage, village)) {
            return false;
        }
        new Context(player, chainId, FealtyDataManager.chain(chainId).orElseThrow(), progress(player, chainId)).setStage(stage);
        return true;
    }

    private static List<Villager> villagersOf(ServerLevel level, VillageRecord village) {
        if (!village.dimension().equals(level.dimension())) {
            return List.of();
        }
        return level.getEntitiesOfClass(Villager.class, AABB.of(village.bounds()),
                v -> v.isAlive() && !v.isBaby() && village.id().equals(FactionResolver.factionOf(v).orElse(null)));
    }

    /**
     * Pick a run's steps: from the pool, roles the village has someone for (or someone already in the role) come
     * first, each claiming a different villager; then a quest variant for each.
     */
    private static List<ChainProgress.RunStep> pickRun(List<Villager> villagers, VillageRecord village, QuestChainDefinition def,
                                                       ResourceLocation chain, RandomSource random) {
        List<QuestChainDefinition.Step> chosen = new ArrayList<>();
        if (!def.pooled()) {
            chosen.addAll(def.steps());
        } else if (def.single()) {
            // One villager gives every step, so any steps of the pool will do.
            List<QuestChainDefinition.Step> pool = new ArrayList<>(def.stepPool().stream().filter(step -> !step.quests().isEmpty()).toList());
            Util.shuffle(pool, random);
            chosen.addAll(pool.subList(0, Math.min(def.stepsCount(), pool.size())));
        } else {
            List<QuestChainDefinition.Step> pool = new ArrayList<>();
            for (QuestChainDefinition.Step step : def.stepPool()) {
                if (!step.quests().isEmpty()) {
                    pool.add(step);
                }
            }
            Util.shuffle(pool, random);
            Set<UUID> claimed = new HashSet<>();
            List<QuestChainDefinition.Step> unfilled = new ArrayList<>();
            for (QuestChainDefinition.Step step : pool) {
                if (chosen.size() >= def.stepsCount()) {
                    break;
                }
                Optional<Villager> person = villagers.stream()
                        .filter(v -> !claimed.contains(v.getUUID()) && holds(v, def, chain, village.id(), step.role())).findFirst()
                        .or(() -> villagers.stream().filter(v -> !claimed.contains(v.getUUID()) && !hasRole(v) && fits(v, step)).findFirst());
                if (person.isPresent()) {
                    claimed.add(person.get().getUUID());
                    chosen.add(step);
                } else {
                    unfilled.add(step);
                }
            }
            for (QuestChainDefinition.Step step : unfilled) {
                if (chosen.size() >= def.stepsCount()) {
                    break;
                }
                chosen.add(step);
            }
        }
        List<ChainProgress.RunStep> run = new ArrayList<>();
        for (QuestChainDefinition.Step step : chosen) {
            List<ResourceLocation> quests = step.quests();
            if (!quests.isEmpty()) {
                run.add(new ChainProgress.RunStep(step.role(), quests.get(random.nextInt(quests.size()))));
            }
        }
        return run;
    }

    /** Runs saved before runs were laid out get one now: the old fixed steps, keeping what was done. */
    private static void ensureRun(ServerPlayer player, ChainProgress progress, QuestChainDefinition def) {
        if (progress.stage() != STAGE_STEPS || !progress.run().isEmpty()) {
            return;
        }
        List<String> roles = def.pooled() ? LEGACY_STEPS : def.steps().stream().map(QuestChainDefinition.Step::role).toList();
        for (String role : roles) {
            def.step(role).flatMap(QuestChainDefinition.Step::firstQuest)
                    .ifPresent(quest -> progress.run().add(new ChainProgress.RunStep(role, quest)));
        }
        progress.stepsDone().removeIf(index -> index >= progress.run().size());
        FealtyWorldData.get(player.server).setDirty();
    }

    /** Give each step of a run to a villager, reusing whoever already holds that role here. @return their names */
    private static List<Component> assignRoles(ServerLevel level, VillageRecord village, QuestChainDefinition def, ResourceLocation chain,
                                               List<ChainProgress.RunStep> run) {
        List<Villager> villagers = villagersOf(level, village);
        Comparator<Villager> nearest = Comparator.comparingDouble(v -> v.blockPosition().distSqr(village.center()));
        if (def.single()) {
            Villager giver = villagers.stream().filter(v -> holds(v, def, chain, village.id(), def.giverRole())).findFirst()
                    .or(() -> villagers.stream().filter(v -> !hasRole(v)).min(nearest)).orElse(null);
            if (giver != null && !hasRole(giver)) {
                giveRole(giver, chain, village.id(), def.giverRole(), giverTitle(def), false);
            }
            return List.of(giver != null ? giver.getName() : Component.translatable("fealty.chain.someone", giverTitle(def)));
        }
        List<Component> names = new ArrayList<>();
        for (ChainProgress.RunStep runStep : run) {
            Optional<QuestChainDefinition.Step> step = def.step(runStep.role());
            Villager holder = villagers.stream().filter(v -> holds(v, def, chain, village.id(), runStep.role())).findFirst().orElse(null);
            if (holder == null && step.isPresent()) {
                holder = pickHolder(villagers, step.get(), nearest);
                if (holder != null) {
                    giveRole(holder, chain, village.id(), step.get().role(), roleTitle(step.get()), false);
                }
            }
            names.add(holder != null ? holder.getName()
                    : Component.translatable("fealty.chain.someone", step.map(ChainManager::roleTitle).orElse(Component.literal(runStep.role()))));
        }
        return names;
    }

    /** Someone of the right trade; else someone without work, who takes up the trade; else anyone. */
    @Nullable
    private static Villager pickHolder(List<Villager> villagers, QuestChainDefinition.Step step, Comparator<Villager> nearest) {
        Optional<Villager> pick = villagers.stream().filter(v -> !hasRole(v) && fits(v, step)).min(nearest);
        if (pick.isPresent()) {
            return pick.get();
        }
        Optional<VillagerProfession> trade = step.professions().stream()
                .map(id -> BuiltInRegistries.VILLAGER_PROFESSION.getOptional(id).orElse(VillagerProfession.NONE))
                .filter(p -> p != VillagerProfession.NONE && p != VillagerProfession.NITWIT).findFirst();
        if (trade.isPresent()) {
            pick = villagers.stream().filter(v -> !hasRole(v) && v.getVillagerData().getProfession() == VillagerProfession.NONE).min(nearest);
            if (pick.isPresent()) {
                pick.get().setVillagerData(pick.get().getVillagerData().setProfession(trade.get()));
                return pick.get();
            }
        }
        return villagers.stream().filter(v -> !hasRole(v)).min(nearest).orElse(null);
    }

    /**
     * Asking a villager for work. A named villager opens their step; a villager without a role steps into a role
     * of one of the player's runs here that nobody in the village holds any more (one of their own trade first), or
     * becomes a single-giver chain's giver if it has lost its own.
     */
    public static boolean onTalk(ServerPlayer player, Villager villager) {
        Optional<QuestChainDefinition> rare = rareChain();
        ChainProgress rareProgress = progress(player, RARE_CHAIN);
        if (rare.isPresent() && rareProgress != null) {
            ensureRun(player, rareProgress, rare.get());
        }
        checkRole(villager);
        if (hasRole(villager)) {
            QuestGivers.open(player, villager);
            return true;
        }
        Optional<ResourceLocation> faction = FactionResolver.factionOf(villager);
        if (faction.isEmpty()) {
            return false;
        }
        // The rare chain first, as before; then the others by id.
        List<ResourceLocation> chains = new ArrayList<>(RepManager.data(player).chains().keySet());
        chains.sort(Comparator.comparing((ResourceLocation id) -> !id.equals(RARE_CHAIN)).thenComparing(ResourceLocation::toString));
        for (ResourceLocation chain : chains) {
            Optional<QuestChainDefinition> def = FealtyDataManager.chain(chain);
            if (def.isPresent() && takeUpRole(player, villager, faction.get(), chain, def.get(), progress(player, chain))) {
                QuestGivers.open(player, villager);
                return true;
            }
        }
        return false;
    }

    private static boolean takeUpRole(ServerPlayer player, Villager villager, ResourceLocation faction, ResourceLocation chain,
                                      QuestChainDefinition def, @Nullable ChainProgress progress) {
        if (progress == null || def.isGuild() || !inSteps(def, progress) || !faction.equals(progress.origin())) {
            return false;
        }
        Optional<VillageRecord> village = record(player, progress.origin());
        if (village.isEmpty()) {
            return false;
        }
        List<Villager> villagers = villagersOf(player.serverLevel(), village.get());
        if (def.single()) {
            if (villagers.stream().anyMatch(v -> holds(v, def, chain, village.get().id(), def.giverRole()))) {
                return false;
            }
            giveRole(villager, chain, village.get().id(), def.giverRole(), giverTitle(def), false);
            return true;
        }
        QuestChainDefinition.Step pick = null;
        for (int i = 0; i < progress.run().size(); i++) {
            String role = progress.run().get(i).role();
            Optional<QuestChainDefinition.Step> step = def.step(role);
            if (progress.stepsDone().contains(i) || step.isEmpty()
                    || villagers.stream().anyMatch(v -> holds(v, def, chain, village.get().id(), role))) {
                continue;
            }
            if (fits(villager, step.get())) {
                pick = step.get();
                break;
            }
            if (pick == null) {
                pick = step.get();
            }
        }
        if (pick == null) {
            return false;
        }
        giveRole(villager, chain, village.get().id(), pick.role(), roleTitle(pick), false);
        return true;
    }

    private static Component hint(ServerPlayer player, ChainProgress progress, QuestChainDefinition def) {
        int steps = progress.run().isEmpty() ? def.stepsCount() : progress.run().size();
        return switch (progress.stage()) {
            case STAGE_STEPS -> Component.translatable("fealty.chain.hint.steps", progress.stepsDone().size(), steps);
            case STAGE_HAMLET -> progress.target() != null
                    ? Component.translatable("fealty.chain.hint.hamlet", progress.target().getX(), progress.target().getZ())
                    : Component.translatable("fealty.chain.hint.hamlet_unknown");
            case STAGE_TRIAL -> Component.translatable("fealty.chain.hint.trial");
            case STAGE_COMBINE -> Component.translatable("fealty.chain.hint.combine");
            default -> Component.empty();
        };
    }

    private static boolean stillTrusted(ServerPlayer player, ChainProgress progress, QuestChainDefinition def) {
        if (progress.origin() == null) {
            return false;
        }
        Optional<RepTier> min = TierManager.byId(def.startTier());
        return min.isEmpty() || RepManager.getTier(player, progress.origin()).rank() >= min.get().rank();
    }

    /** The trial the Keeper sets this run: the one picked when the hamlet was found, else the first. */
    private static Optional<ResourceLocation> trialFor(ChainProgress progress, QuestChainDefinition def) {
        List<ResourceLocation> trials = def.trials();
        if (progress.trial() != null && trials.contains(progress.trial())) {
            return Optional.of(progress.trial());
        }
        return trials.isEmpty() ? Optional.empty() : Optional.of(trials.getFirst());
    }

    /** A random trial, other than the one just failed when there is a choice. */
    private static void rollTrial(ChainProgress progress, QuestChainDefinition def, RandomSource random) {
        List<ResourceLocation> trials = new ArrayList<>(def.trials());
        if (trials.size() > 1 && progress.trial() != null) {
            trials.remove(progress.trial());
        }
        progress.setTrial(trials.isEmpty() ? null : trials.get(random.nextInt(trials.size())));
    }

    // ---- Completion ----

    /** A chain quest ended without success. A failed or abandoned Keeper's trial can be taken up again, as a new trial. */
    public static void onQuestEnded(ServerPlayer player, ActiveQuest quest) {
        Optional<ChainKey> key = parse(quest.giver());
        if (key.isEmpty() || key.get().step() >= 0) {
            return;
        }
        ChainProgress progress = progress(player, key.get().chain());
        if (progress != null && progress.stage() == STAGE_TRIAL) {
            progress.setStage(STAGE_HAMLET);
            FealtyDataManager.chain(key.get().chain()).ifPresent(def -> rollTrial(progress, def, player.getRandom()));
            FealtyWorldData.get(player.server).setDirty();
        }
    }

    public static void onQuestCompleted(ServerPlayer player, ActiveQuest quest) {
        Optional<ChainKey> key = parse(quest.giver());
        if (key.isEmpty()) {
            return;
        }
        Optional<QuestChainDefinition> def = FealtyDataManager.chain(key.get().chain());
        ChainProgress progress = progress(player, key.get().chain());
        if (def.isEmpty() || progress == null) {
            return;
        }
        ResourceLocation chain = key.get().chain();
        int before = progress.stage();
        if (def.get().isGuild()) {
            ThievesGuild.onStepCompleted(player, def.get(), progress, key.get().step());
            FealtyWorldData.get(player.server).setDirty();
            if (progress.stage() != before) {
                NeoForge.EVENT_BUS.post(new ChainStageEvent.Advance(player, chain, before, progress.stage(), key.get().step()));
                if (progress.stage() >= def.get().steps().size()) {
                    NeoForge.EVENT_BUS.post(new ChainStageEvent.Complete(player, chain, progress.stage(), progress.timesCompleted()));
                }
            }
            return;
        }
        Optional<ChainHandler> handler = ChainKinds.handler(def.get());
        if (handler.isEmpty()) {
            return;
        }
        Context ctx = new Context(player, chain, def.get(), progress);
        ensureRun(player, progress, def.get());
        int index = key.get().step();
        boolean complete = false;
        if (index >= 0 && index < progress.run().size()) {
            stepAt(def.get(), progress, index).ifPresent(step -> step.rewards().forEach(stack -> Maps.give(player, stack.copy())));
            progress.stepsDone().add(index);
            int left = progress.run().size() - progress.stepsDone().size();
            if (left <= 0) {
                def.get().stepsReward().ifPresent(stack -> Maps.give(player, stack.copy()));
            } else {
                player.sendSystemMessage(Component.translatable("fealty.chain.step_done", left));
                Feedback.toast(player, "seal", Component.translatable("fealty.toast.chain_step"),
                        Component.translatable("fealty.toast.chain_left", left));
            }
            complete = handler.get().onStepComplete(ctx, index);
            NeoForge.EVENT_BUS.post(new ChainStageEvent.Advance(player, chain, before, progress.stage(), index));
        } else if (index < 0) {
            def.get().trialReward().ifPresent(stack -> Maps.give(player, stack.copy()));
            complete = handler.get().onStepComplete(ctx, index);
            NeoForge.EVENT_BUS.post(new ChainStageEvent.Advance(player, chain, before, progress.stage(), ChainStageEvent.Advance.TRIAL));
        }
        FealtyWorldData.get(player.server).setDirty();
        if (complete) {
            complete(ctx);
        }
    }

    /** The rare chain after a step: the hamlet once every step is done; after the Keeper's trial, the charter. */
    static void rareStepDone(Context ctx, int index) {
        ServerPlayer player = ctx.player;
        ChainProgress progress = ctx.progress;
        if (index >= 0) {
            if (progress.run().size() - progress.stepsDone().size() <= 0) {
                finishSteps(player, ctx.def, progress);
            }
            return;
        }
        progress.setStage(STAGE_COMBINE);
        FealtyEvents.fire(player, FealtyEvents.CHARTER_EARNED);
        player.sendSystemMessage(Component.translatable("fealty.chain.charter"));
        Feedback.banner(player, Component.translatable("fealty.banner.charter"), Component.translatable("fealty.banner.charter.detail"),
                0xE0B040, "crown");
    }

    /** The rare chain's steps are done ({@code steps_reward} was just given): the signet, and a map to the hamlet. */
    private static void finishSteps(ServerPlayer player, QuestChainDefinition def, ChainProgress progress) {
        FealtyEvents.fire(player, FealtyEvents.SIGNET_EARNED);
        progress.setStage(STAGE_HAMLET);
        rollTrial(progress, def, player.getRandom());
        releaseRoles(player.server, progress.chainId(), progress.origin());
        Feedback.banner(player, Component.translatable("fealty.banner.signet"), Component.translatable("fealty.banner.signet.detail"),
                0xE0B040, "seal");
        Feedback.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
        if (def.mapStructure().isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos from = record(player, progress.origin()).map(VillageRecord::center).orElse(player.blockPosition());
        BlockPos hamlet = level.findNearestMapStructure(def.mapStructure().get(), from, 100, false);
        if (hamlet == null) {
            player.sendSystemMessage(Component.translatable("fealty.chain.no_hamlet"));
            return;
        }
        progress.setTarget(hamlet);
        Maps.give(player, Maps.treasureMap(level, hamlet, MapDecorationTypes.RED_X, Component.translatable("item.fealty.hamlet_map")));
        player.sendSystemMessage(Component.translatable("fealty.chain.map_given"));
    }

    /** A chain is complete: {@code final_reward}, the done stage, its roles let go, the event and the kind's own ending. */
    static void complete(Context ctx) {
        if (ctx.progress.stage() == STAGE_DONE) {
            return;
        }
        ctx.def.finalReward().ifPresent(stack -> Maps.give(ctx.player, stack.copy()));
        ctx.progress.setStage(STAGE_DONE);
        FealtyWorldData.get(ctx.player.server).setDirty();
        releaseRoles(ctx.player.server, ctx.chain, ctx.progress.origin());
        NeoForge.EVENT_BUS.post(new ChainStageEvent.Complete(ctx.player, ctx.chain, STAGE_DONE, ctx.progress.timesCompleted()));
        ChainKinds.handler(ctx.def).ifPresent(handler -> handler.onChainComplete(ctx));
    }

    /** The Keeper forged the Royal Writ. */
    static void writForged(ServerPlayer player) {
        FealtyEvents.fire(player, FealtyEvents.WRIT_FORGED);
        player.sendSystemMessage(Component.translatable("fealty.keeper.forged"));
        Feedback.banner(player, Component.translatable("fealty.banner.writ"), Component.translatable("fealty.banner.writ.detail"),
                0xE0B040, "crown");
        Feedback.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.8F);
    }

    /**
     * The step at an index of the run: the run's role looked up in the pool, or, for fixed steps, the step in that
     * place (so fixed steps may share a role).
     */
    static Optional<QuestChainDefinition.Step> stepAt(QuestChainDefinition def, ChainProgress progress, int index) {
        if (index < 0 || index >= progress.run().size()) {
            return Optional.empty();
        }
        String role = progress.run().get(index).role();
        if (!def.pooled()) {
            List<QuestChainDefinition.Step> laidOut = def.steps().stream().filter(step -> !step.quests().isEmpty()).toList();
            if (index < laidOut.size() && laidOut.get(index).role().equals(role)) {
                return Optional.of(laidOut.get(index));
            }
        }
        return def.step(role);
    }

    /** The first step of the run not handed in yet, or -1. */
    private static int nextStep(ChainProgress progress) {
        for (int i = 0; i < progress.run().size(); i++) {
            if (!progress.stepsDone().contains(i)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether a step's {@code unlock} condition passes (or it has none). */
    private static boolean unlocked(ServerPlayer player, Entity npc, Optional<QuestChainDefinition.Step> step) {
        return Scripts.test(step.flatMap(QuestChainDefinition.Step::unlock), DialogueContext.of(player, npc));
    }

    /**
     * Take up a step: the variant picked for this run, or, if it cannot start here (no structure of its kind
     * nearby, nobody to talk to), another of the step's variants.
     */
    private static void acceptStep(ServerPlayer player, Villager giver, ChainProgress progress, QuestChainDefinition def, int index) {
        ChainProgress.RunStep runStep = progress.run().get(index);
        ResourceLocation key = stepKey(progress.chainId(), index);
        List<ResourceLocation> order = new ArrayList<>();
        order.add(runStep.quest());
        stepAt(def, progress, index).ifPresent(step -> {
            List<ResourceLocation> others = new ArrayList<>(step.quests());
            others.remove(runStep.quest());
            Util.shuffle(others, player.getRandom());
            order.addAll(others);
        });
        CompoundTag state = new CompoundTag();
        state.putUUID("giver_uuid", giver.getUUID());
        state.put("giver_pos", NbtUtils.writeBlockPos(giver.blockPosition()));
        state.putString("giver_name", giver.getDisplayName().getString());
        for (ResourceLocation quest : order) {
            if (QuestManager.accept(player, key, progress.origin(), quest, state)) {
                if (!quest.equals(runStep.quest())) {
                    progress.run().set(index, new ChainProgress.RunStep(runStep.role(), quest));
                    FealtyWorldData.get(player.server).setDirty();
                }
                return;
            }
            if (!QuestManager.hasRoom(player, key, 1)) {
                return;
            }
        }
    }

    // ---- Givers ----

    /**
     * The step a named villager gives this player now, or -1: their role's step of the player's run in their village,
     * or, for a single-giver chain's giver, the first step not handed in yet.
     */
    private static int stepIndex(Villager villager, ChainRole role, Optional<QuestChainDefinition> def, @Nullable ChainProgress progress) {
        if (def.isEmpty() || progress == null || !inSteps(def.get(), progress) || !(role.fixed() || role.village().equals(progress.origin()))) {
            return -1;
        }
        Optional<String> key = roleOf(villager, def.get());
        if (key.isEmpty()) {
            return -1;
        }
        if (def.get().single() && key.get().equals(def.get().giverRole())) {
            return nextStep(progress);
        }
        return progress.indexOf(key.get());
    }

    /** A villager given a role in a chain. */
    private static final class NamedVillagerGiver implements QuestGiver {
        @Override
        public OpenQuestScreenPayload screen(ServerPlayer player, Entity entity) {
            Villager villager = (Villager) entity;
            ChainRole role = villager.getData(ModAttachments.CHAIN_ROLE);
            Optional<QuestChainDefinition> def = FealtyDataManager.chain(role.chain());
            ChainProgress progress = progress(player, role.chain());
            String villageName = record(player, role.village()).map(VillageRecord::name).orElse("?");
            Component subtitle = Component.translatable("fealty.chain.subtitle", villageName);
            int rep = RepManager.getRep(player, role.village());
            Optional<String> key = def.flatMap(d -> roleOf(villager, d));
            int index = stepIndex(villager, role, def, progress);
            Component greeting;
            List<OpenQuestScreenPayload.QuestEntry> quests = List.of();
            if (index < 0) {
                greeting = Component.translatable("fealty.chain.villager.idle");
            } else if (progress.stepsDone().contains(index)) {
                greeting = Component.translatable("fealty.chain.villager.done");
            } else {
                Optional<QuestChainDefinition.Step> step = stepAt(def.get(), progress, index);
                ResourceLocation questKey = stepKey(role.chain(), index);
                boolean taken = !QuestManager.contexts(player, questKey).isEmpty();
                if (!taken && !unlocked(player, villager, step)) {
                    greeting = step.flatMap(QuestChainDefinition.Step::unlockHint).map(text -> Texts.say(text, player, villager))
                            .orElseGet(() -> Component.translatable("fealty.chain.villager.waiting"));
                } else {
                    greeting = step.flatMap(QuestChainDefinition.Step::text).map(text -> Texts.say(text, player, villager))
                            .orElseGet(() -> def.get().single() && key.get().equals(def.get().giverRole())
                                    ? Component.translatable("fealty.chain.giver.offer", player.getDisplayName())
                                    : Component.translatableWithFallback("fealty.chain.villager.greet." + key.get(),
                                    "So the elder sent you, %s. Then you should hear what I know of the old crown.", player.getDisplayName()));
                    quests = QuestGivers.entries(player, questKey, List.of(progress.run().get(index).quest()));
                }
            }
            return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), subtitle, greeting, rep, true, quests, List.of());
        }

        @Override
        public void handleAction(ServerPlayer player, Entity entity, String action, String argument) {
            Villager villager = (Villager) entity;
            ChainRole role = villager.getData(ModAttachments.CHAIN_ROLE);
            Optional<QuestChainDefinition> def = FealtyDataManager.chain(role.chain());
            ChainProgress progress = progress(player, role.chain());
            int index = stepIndex(villager, role, def, progress);
            if (index < 0 || progress.stepsDone().contains(index)) {
                return;
            }
            ResourceLocation questKey = stepKey(role.chain(), index);
            switch (action) {
                case QuestActionPayload.ACCEPT -> {
                    if (unlocked(player, villager, stepAt(def.get(), progress, index))) {
                        acceptStep(player, villager, progress, def.get(), index);
                    }
                }
                case QuestActionPayload.TURN_IN -> QuestManager.turnIn(player, questKey);
                case QuestActionPayload.ABANDON -> QuestManager.abandon(player, questKey, false);
                default -> {
                }
            }
        }
    }

    /** The Keeper of the hidden hamlet. */
    private static final class KeeperGiver implements QuestGiver {
        @Override
        public OpenQuestScreenPayload screen(ServerPlayer player, Entity entity) {
            Optional<QuestChainDefinition> def = rareChain();
            ChainProgress progress = progress(player, RARE_CHAIN);
            Component subtitle = Component.translatable("fealty.keeper.subtitle");
            if (def.isEmpty() || progress == null || progress.stage() < STAGE_HAMLET || progress.origin() == null) {
                return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), subtitle,
                        Component.translatable("fealty.keeper.stranger"), 0, false, List.of(), List.of());
            }
            if (RepManager.data(player).setFlag(FOUND_HAMLET)) {
                FealtyEvents.fire(player, FealtyEvents.HAMLET_FOUND);
            }
            String origin = record(player, progress.origin()).map(VillageRecord::name).orElse("?");
            int rep = RepManager.getRep(player, progress.origin());
            if (!stillTrusted(player, progress, def.get())) {
                FealtyEvents.fire(player, FealtyEvents.KEEPER_REFUSED);
                return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), subtitle,
                        Component.translatable("fealty.keeper.fallen", origin), rep, true, List.of(), List.of());
            }
            List<OpenQuestScreenPayload.QuestEntry> quests = new ArrayList<>();
            List<ActionEntry> actions = new ArrayList<>();
            Component greeting;
            switch (progress.stage()) {
                case STAGE_HAMLET -> {
                    greeting = Component.translatable("fealty.keeper.trial_offer", origin);
                    trialFor(progress, def.get()).flatMap(QuestManager::offerEntry).ifPresent(quests::add);
                }
                case STAGE_TRIAL -> {
                    greeting = Component.translatable("fealty.keeper.trial_active");
                    quests.addAll(QuestGivers.entries(player, trialKey(RARE_CHAIN), List.of()));
                }
                case STAGE_COMBINE -> {
                    greeting = Component.translatable("fealty.keeper.combine");
                    boolean ready = def.get().stepsReward().map(s -> Inventories.has(player, s.getItem())).orElse(true)
                            && def.get().trialReward().map(s -> Inventories.has(player, s.getItem())).orElse(true);
                    Component label = Component.translatable("fealty.keeper.combine_action");
                    actions.add(ready ? ActionEntry.of(ACTION_COMBINE, label)
                            : ActionEntry.disabled(ACTION_COMBINE, label, Component.translatable("fealty.keeper.combine_missing")));
                }
                default -> greeting = Component.translatable("fealty.keeper.done");
            }
            return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), subtitle, greeting, rep, true, quests, actions);
        }

        @Override
        public void handleAction(ServerPlayer player, Entity entity, String action, String argument) {
            Optional<QuestChainDefinition> def = rareChain();
            ChainProgress progress = progress(player, RARE_CHAIN);
            if (def.isEmpty() || progress == null || !stillTrusted(player, progress, def.get())) {
                return;
            }
            ResourceLocation key = trialKey(RARE_CHAIN);
            switch (action) {
                case QuestActionPayload.ACCEPT -> {
                    if (progress.stage() == STAGE_HAMLET) {
                        acceptTrial(player, entity, progress, def.get(), key);
                    }
                }
                case QuestActionPayload.TURN_IN -> QuestManager.turnIn(player, key);
                case QuestActionPayload.ABANDON -> {
                    if (QuestManager.abandon(player, key, false)) {
                        progress.setStage(STAGE_HAMLET);
                    }
                }
                case ACTION_COMBINE -> combine(player, def.get(), progress);
                default -> {
                }
            }
            FealtyWorldData.get(player.server).setDirty();
        }

        /** The Keeper's trial for this run, or another one if it cannot start here. */
        private static void acceptTrial(ServerPlayer player, Entity keeper, ChainProgress progress, QuestChainDefinition def, ResourceLocation key) {
            List<ResourceLocation> order = new ArrayList<>();
            trialFor(progress, def).ifPresent(order::add);
            List<ResourceLocation> others = new ArrayList<>(def.trials());
            others.removeAll(order);
            Util.shuffle(others, player.getRandom());
            order.addAll(others);
            CompoundTag init = new CompoundTag();
            init.put("anchor", NbtUtils.writeBlockPos(keeper.blockPosition()));
            init.put("giver_pos", NbtUtils.writeBlockPos(keeper.blockPosition()));
            init.putString("giver_name", keeper.getDisplayName().getString());
            for (ResourceLocation trial : order) {
                if (QuestManager.accept(player, key, progress.origin(), trial, init)) {
                    progress.setTrial(trial);
                    progress.setStage(STAGE_TRIAL);
                    return;
                }
                if (!QuestManager.hasRoom(player, key, 1)) {
                    return;
                }
            }
        }

        private static void combine(ServerPlayer player, QuestChainDefinition def, ChainProgress progress) {
            if (progress.stage() != STAGE_COMBINE) {
                return;
            }
            Optional<ItemStack> a = def.stepsReward();
            Optional<ItemStack> b = def.trialReward();
            if ((a.isPresent() && !Inventories.has(player, a.get().getItem())) || (b.isPresent() && !Inventories.has(player, b.get().getItem()))) {
                return;
            }
            a.ifPresent(stack -> Inventories.takeOne(player, stack.getItem()));
            b.ifPresent(stack -> Inventories.takeOne(player, stack.getItem()));
            ChainManager.complete(new Context(player, RARE_CHAIN, def, progress));
        }
    }

    // ---- What a chain kind's handler sees ----

    static final class Context implements ChainContext {
        final ServerPlayer player;
        final ResourceLocation chain;
        final QuestChainDefinition def;
        final ChainProgress progress;

        Context(ServerPlayer player, ResourceLocation chain, QuestChainDefinition def, ChainProgress progress) {
            this.player = player;
            this.chain = chain;
            this.def = def;
            this.progress = progress;
        }

        @Override
        public ServerPlayer player() {
            return player;
        }

        @Override
        public ResourceLocation chain() {
            return chain;
        }

        @Override
        public ResourceLocation kind() {
            return def.kind();
        }

        @Override
        public Optional<ResourceLocation> origin() {
            return Optional.ofNullable(progress.origin());
        }

        @Override
        public int stage() {
            return progress.stage();
        }

        @Override
        public void setStage(int stage) {
            int before = progress.stage();
            if (before == stage) {
                return;
            }
            progress.setStage(stage);
            FealtyWorldData.get(player.server).setDirty();
            NeoForge.EVENT_BUS.post(new ChainStageEvent.Advance(player, chain, before, stage, ChainStageEvent.Advance.NO_STEP));
        }

        @Override
        public int stepCount() {
            return progress.run().size();
        }

        @Override
        public Set<Integer> stepsDone() {
            return Set.copyOf(progress.stepsDone());
        }

        @Override
        public Optional<ResourceLocation> stepQuest(int step) {
            return step >= 0 && step < progress.run().size() ? Optional.of(progress.run().get(step).quest()) : Optional.empty();
        }

        @Override
        public int timesCompleted() {
            return progress.timesCompleted();
        }

        @Override
        public void complete() {
            ChainManager.complete(this);
        }
    }
}
