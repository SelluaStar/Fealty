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
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.network.OpenQuestScreenPayload;
import com.selluastar.fealty.network.OpenQuestScreenPayload.ActionEntry;
import com.selluastar.fealty.network.QuestActionPayload;
import com.selluastar.fealty.outlaw.ThievesGuild;
import com.selluastar.fealty.quest.ActiveQuest;
import com.selluastar.fealty.quest.QuestGiver;
import com.selluastar.fealty.quest.QuestGivers;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.PlayerRepData;
import com.selluastar.fealty.rep.RepManager;
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

/**
 * The rare villager chain: rumours from the elder, named villagers who each set one task (roles and tasks are
 * picked for each run, and any trade can be picked, the village fool and the jobless included), a map to the hidden
 * hamlet, one of the Keeper's trials, and the Royal Writ.
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

    private static void giveRole(Villager villager, ResourceLocation chain, VillageRecord village, QuestChainDefinition.Step step) {
        villager.setData(ModAttachments.CHAIN_ROLE, ChainRole.of(chain, village.id(), step.role()));
        String name = personName(villager).orElseGet(() -> VillageNames.personName(villager.getRandom()));
        villager.setCustomName(Component.translatable("fealty.chain.named", name, roleTitle(step)));
        villager.setCustomNameVisible(true);
        lockProfession(villager);
    }

    /** The villager keeps their name but loses the title and the role. */
    private static void clearRole(Villager villager) {
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

    /** Whether a player's run in this village is still gathering its steps and has someone in this role. */
    private static boolean roleNeeded(MinecraftServer server, ResourceLocation chain, ResourceLocation village, String role) {
        for (PlayerRepData data : FealtyWorldData.get(server).players().values()) {
            ChainProgress progress = data.chains().get(chain);
            if (progress == null || progress.stage() != STAGE_STEPS || !village.equals(progress.origin())) {
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
                    || !villager.getData(ModAttachments.CHAIN_ROLE).village().equals(villageId)) {
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
        if (def.isEmpty() || def.get().kind() != QuestChainDefinition.Kind.RARE_VILLAGER) {
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

    public static void startFromElder(ServerPlayer player, VillageRecord village) {
        Optional<ActionEntry> action = rumoursAction(player, village);
        if (action.isEmpty() || !action.get().enabled()) {
            return;
        }
        QuestChainDefinition def = rareChain().orElseThrow();
        PlayerRepData data = RepManager.data(player);
        ChainProgress progress = data.chains().computeIfAbsent(RARE_CHAIN, ChainProgress::new);
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
        progress.run().addAll(pickRun(villagers, village, def, RARE_CHAIN, player.getRandom()));
        FealtyWorldData.get(player.server).setDirty();
        if (previous != null && !previous.equals(village.id())) {
            releaseRoles(player.server, RARE_CHAIN, previous);
        }
        List<Component> names = assignRoles(player.serverLevel(), village, def, RARE_CHAIN, progress.run());
        player.sendSystemMessage(Component.translatable("fealty.chain.rumours_told", ComponentUtils.formatList(names, Component.literal(", "))));
        Feedback.toast(player, "crown", Component.translatable("fealty.toast.chain_started"),
                Component.translatable("fealty.toast.chain_people", names.size()));
        FealtyEvents.fire(player, FealtyEvents.CHAIN_STARTED);
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
        List<Component> names = new ArrayList<>();
        for (ChainProgress.RunStep runStep : run) {
            Optional<QuestChainDefinition.Step> step = def.step(runStep.role());
            Villager holder = villagers.stream().filter(v -> holds(v, def, chain, village.id(), runStep.role())).findFirst().orElse(null);
            if (holder == null && step.isPresent()) {
                holder = pickHolder(villagers, step.get(), nearest);
                if (holder != null) {
                    giveRole(holder, chain, village, step.get());
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
     * of the player's run that nobody in the village holds any more (one of their own trade first).
     */
    public static boolean onTalk(ServerPlayer player, Villager villager) {
        Optional<QuestChainDefinition> def = rareChain();
        ChainProgress progress = progress(player, RARE_CHAIN);
        if (def.isPresent() && progress != null) {
            ensureRun(player, progress, def.get());
        }
        checkRole(villager);
        if (hasRole(villager)) {
            QuestGivers.open(player, villager);
            return true;
        }
        if (progress == null || progress.stage() != STAGE_STEPS || def.isEmpty()) {
            return false;
        }
        Optional<ResourceLocation> faction = FactionResolver.factionOf(villager);
        Optional<VillageRecord> village = record(player, progress.origin());
        if (faction.isEmpty() || village.isEmpty() || !faction.get().equals(progress.origin())) {
            return false;
        }
        List<Villager> villagers = villagersOf(player.serverLevel(), village.get());
        QuestChainDefinition.Step pick = null;
        for (int i = 0; i < progress.run().size(); i++) {
            String role = progress.run().get(i).role();
            Optional<QuestChainDefinition.Step> step = def.get().step(role);
            if (progress.stepsDone().contains(i) || step.isEmpty()
                    || villagers.stream().anyMatch(v -> holds(v, def.get(), RARE_CHAIN, village.get().id(), role))) {
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
        giveRole(villager, RARE_CHAIN, village.get(), pick);
        QuestGivers.open(player, villager);
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
        if (def.get().kind() == QuestChainDefinition.Kind.GUILD) {
            ThievesGuild.onStepCompleted(player, def.get(), progress, key.get().step());
            FealtyWorldData.get(player.server).setDirty();
            return;
        }
        ensureRun(player, progress, def.get());
        int index = key.get().step();
        if (index >= 0 && index < progress.run().size()) {
            def.get().step(progress.run().get(index).role())
                    .ifPresent(step -> step.rewards().forEach(stack -> Maps.give(player, stack.copy())));
            progress.stepsDone().add(index);
            int left = progress.run().size() - progress.stepsDone().size();
            if (left <= 0) {
                finishSteps(player, def.get(), progress);
            } else {
                player.sendSystemMessage(Component.translatable("fealty.chain.step_done", left));
                Feedback.toast(player, "seal", Component.translatable("fealty.toast.chain_step"),
                        Component.translatable("fealty.toast.chain_left", left));
            }
        } else if (index < 0) {
            def.get().trialReward().ifPresent(stack -> Maps.give(player, stack.copy()));
            progress.setStage(STAGE_COMBINE);
            FealtyEvents.fire(player, FealtyEvents.CHARTER_EARNED);
            player.sendSystemMessage(Component.translatable("fealty.chain.charter"));
            Feedback.banner(player, Component.translatable("fealty.banner.charter"), Component.translatable("fealty.banner.charter.detail"),
                    0xE0B040, "crown");
        }
        FealtyWorldData.get(player.server).setDirty();
    }

    private static void finishSteps(ServerPlayer player, QuestChainDefinition def, ChainProgress progress) {
        def.stepsReward().ifPresent(stack -> Maps.give(player, stack.copy()));
        FealtyEvents.fire(player, FealtyEvents.SIGNET_EARNED);
        progress.setStage(STAGE_HAMLET);
        rollTrial(progress, def, player.getRandom());
        releaseRoles(player.server, RARE_CHAIN, progress.origin());
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

    /**
     * Take up a step: the variant picked for this run, or, if it cannot start here (no structure of its kind
     * nearby, nobody to talk to), another of the step's variants.
     */
    private static void acceptStep(ServerPlayer player, Villager giver, ChainProgress progress, QuestChainDefinition def, int index) {
        ChainProgress.RunStep runStep = progress.run().get(index);
        ResourceLocation key = stepKey(progress.chainId(), index);
        List<ResourceLocation> order = new ArrayList<>();
        order.add(runStep.quest());
        def.step(runStep.role()).ifPresent(step -> {
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
            boolean onChain = progress != null && progress.stage() == STAGE_STEPS && role.village().equals(progress.origin());
            int index = onChain && key.isPresent() ? progress.indexOf(key.get()) : -1;
            Component greeting;
            List<OpenQuestScreenPayload.QuestEntry> quests = List.of();
            if (index < 0) {
                greeting = Component.translatable("fealty.chain.villager.idle");
            } else if (progress.stepsDone().contains(index)) {
                greeting = Component.translatable("fealty.chain.villager.done");
            } else {
                greeting = Component.translatableWithFallback("fealty.chain.villager.greet." + key.get(),
                        "So the elder sent you, %s. Then you should hear what I know of the old crown.", player.getDisplayName());
                quests = QuestGivers.entries(player, stepKey(role.chain(), index), List.of(progress.run().get(index).quest()));
            }
            return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), subtitle, greeting, rep, true, quests, List.of());
        }

        @Override
        public void handleAction(ServerPlayer player, Entity entity, String action, String argument) {
            Villager villager = (Villager) entity;
            ChainRole role = villager.getData(ModAttachments.CHAIN_ROLE);
            Optional<QuestChainDefinition> def = FealtyDataManager.chain(role.chain());
            ChainProgress progress = progress(player, role.chain());
            Optional<String> key = def.flatMap(d -> roleOf(villager, d));
            if (def.isEmpty() || key.isEmpty() || progress == null || progress.stage() != STAGE_STEPS
                    || !role.village().equals(progress.origin())) {
                return;
            }
            int index = progress.indexOf(key.get());
            if (index < 0 || progress.stepsDone().contains(index)) {
                return;
            }
            ResourceLocation questKey = stepKey(role.chain(), index);
            switch (action) {
                case QuestActionPayload.ACCEPT -> acceptStep(player, villager, progress, def.get(), index);
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
            def.finalReward().ifPresent(stack -> Maps.give(player, stack.copy()));
            progress.setStage(STAGE_DONE);
            FealtyEvents.fire(player, FealtyEvents.WRIT_FORGED);
            player.sendSystemMessage(Component.translatable("fealty.keeper.forged"));
            Feedback.banner(player, Component.translatable("fealty.banner.writ"), Component.translatable("fealty.banner.writ.detail"),
                    0xE0B040, "crown");
            Feedback.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.8F);
        }
    }
}
