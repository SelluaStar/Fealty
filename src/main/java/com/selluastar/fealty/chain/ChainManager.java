package com.selluastar.fealty.chain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.TierManager;
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

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.phys.AABB;

/**
 * The rare villager chain: rumours from the elder, three named villagers, a map to the hidden hamlet, the
 * Keeper's trial, and the Royal Writ.
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
        if (progress.stage() == STAGE_DONE) {
            progress.reset();
        }
        progress.setOrigin(village.id());
        progress.setStage(STAGE_STEPS);
        progress.stepsDone().clear();
        progress.setTarget(null);
        FealtyWorldData.get(player.server).setDirty();
        List<Component> names = assignRoles(player.serverLevel(), village, def, RARE_CHAIN);
        player.sendSystemMessage(Component.translatable("fealty.chain.rumours_told", ComponentUtils.formatList(names, Component.literal(", "))));
        FealtyEvents.fire(player, FealtyEvents.CHAIN_STARTED);
    }

    private static List<Villager> villagersOf(ServerLevel level, VillageRecord village) {
        if (!village.dimension().equals(level.dimension())) {
            return List.of();
        }
        return level.getEntitiesOfClass(Villager.class, AABB.of(village.bounds()),
                v -> v.isAlive() && !v.isBaby() && village.id().equals(FactionResolver.factionOf(v).orElse(null)));
    }

    private static List<Component> assignRoles(ServerLevel level, VillageRecord village, QuestChainDefinition def, ResourceLocation chain) {
        List<Villager> villagers = villagersOf(level, village);
        Component[] names = new Component[def.steps().size()];
        for (Villager villager : villagers) {
            if (hasRole(villager)) {
                ChainRole role = villager.getData(ModAttachments.CHAIN_ROLE);
                if (role.chain().equals(chain) && role.village().equals(village.id()) && role.step() < names.length) {
                    names[role.step()] = villager.getName();
                }
            }
        }
        Comparator<Villager> nearest = Comparator.comparingDouble(v -> v.blockPosition().distSqr(village.center()));
        for (int step = 0; step < names.length; step++) {
            if (names[step] != null) {
                continue;
            }
            QuestChainDefinition.Step s = def.steps().get(step);
            Villager pick = villagers.stream().filter(v -> !hasRole(v) && s.professions().contains(profession(v))).min(nearest).orElse(null);
            if (pick == null) {
                pick = villagers.stream().filter(v -> !hasRole(v)).min(nearest).orElse(null);
            }
            if (pick != null) {
                giveRole(pick, chain, step, village, s);
                names[step] = pick.getName();
            }
        }
        List<Component> result = new ArrayList<>();
        for (int step = 0; step < names.length; step++) {
            result.add(names[step] != null ? names[step]
                    : Component.translatable("fealty.chain.someone", roleTitle(def.steps().get(step))));
        }
        return result;
    }

    private static ResourceLocation profession(Villager villager) {
        return BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
    }

    private static Component roleTitle(QuestChainDefinition.Step step) {
        return step.title().orElse(Component.translatable("fealty.chain.role." + step.role()));
    }

    private static void giveRole(Villager villager, ResourceLocation chain, int step, VillageRecord village, QuestChainDefinition.Step s) {
        villager.setData(ModAttachments.CHAIN_ROLE, new ChainRole(chain, step, village.id()));
        villager.setCustomName(Component.translatable("fealty.chain.named", VillageNames.personName(villager.getRandom()), roleTitle(s)));
        villager.setCustomNameVisible(true);
    }

    /** Sneak-talking to a villager. Opens a named villager's quest, or makes this villager fill a missing role. */
    public static boolean onTalk(ServerPlayer player, Villager villager) {
        if (hasRole(villager)) {
            QuestGivers.open(player, villager);
            return true;
        }
        ChainProgress progress = progress(player, RARE_CHAIN);
        Optional<QuestChainDefinition> def = rareChain();
        if (progress == null || progress.stage() != STAGE_STEPS || def.isEmpty()) {
            return false;
        }
        Optional<ResourceLocation> faction = FactionResolver.factionOf(villager);
        Optional<VillageRecord> village = record(player, progress.origin());
        if (faction.isEmpty() || village.isEmpty() || !faction.get().equals(progress.origin())) {
            return false;
        }
        List<Villager> villagers = villagersOf(player.serverLevel(), village.get());
        for (int step = 0; step < def.get().steps().size(); step++) {
            if (progress.stepsDone().contains(step)) {
                continue;
            }
            final int s = step;
            boolean held = villagers.stream().anyMatch(v -> hasRole(v) && v.getData(ModAttachments.CHAIN_ROLE).step() == s
                    && v.getData(ModAttachments.CHAIN_ROLE).chain().equals(RARE_CHAIN));
            if (!held) {
                giveRole(villager, RARE_CHAIN, step, village.get(), def.get().steps().get(step));
                QuestGivers.open(player, villager);
                return true;
            }
        }
        return false;
    }

    private static Component hint(ServerPlayer player, ChainProgress progress, QuestChainDefinition def) {
        return switch (progress.stage()) {
            case STAGE_STEPS -> Component.translatable("fealty.chain.hint.steps", progress.stepsDone().size(), def.steps().size());
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

    // ---- Completion ----

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
        if (key.get().step() >= 0 && key.get().step() < def.get().steps().size()) {
            QuestChainDefinition.Step step = def.get().steps().get(key.get().step());
            progress.stepsDone().add(key.get().step());
            step.rewards().forEach(stack -> Maps.give(player, stack.copy()));
            if (progress.stepsDone().size() >= def.get().steps().size()) {
                finishSteps(player, def.get(), progress);
            } else {
                player.sendSystemMessage(Component.translatable("fealty.chain.step_done",
                        def.get().steps().size() - progress.stepsDone().size()));
            }
        } else if (key.get().step() < 0) {
            def.get().trialReward().ifPresent(stack -> Maps.give(player, stack.copy()));
            progress.setStage(STAGE_COMBINE);
            FealtyEvents.fire(player, FealtyEvents.CHARTER_EARNED);
            player.sendSystemMessage(Component.translatable("fealty.chain.charter"));
        }
        FealtyWorldData.get(player.server).setDirty();
    }

    private static void finishSteps(ServerPlayer player, QuestChainDefinition def, ChainProgress progress) {
        def.stepsReward().ifPresent(stack -> Maps.give(player, stack.copy()));
        FealtyEvents.fire(player, FealtyEvents.SIGNET_EARNED);
        progress.setStage(STAGE_HAMLET);
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
            if (def.isEmpty() || role.step() >= def.get().steps().size()) {
                return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), subtitle,
                        Component.translatable("fealty.chain.villager.idle"), rep, true, List.of(), List.of());
            }
            QuestChainDefinition.Step step = def.get().steps().get(role.step());
            boolean onChain = progress != null && progress.stage() == STAGE_STEPS && role.village().equals(progress.origin());
            Component greeting;
            List<OpenQuestScreenPayload.QuestEntry> quests = List.of();
            if (!onChain) {
                greeting = Component.translatable("fealty.chain.villager.idle");
            } else if (progress.stepsDone().contains(role.step())) {
                greeting = Component.translatable("fealty.chain.villager.done");
            } else {
                greeting = Component.translatableWithFallback("fealty.chain.villager.greet." + step.role(),
                        "", player.getDisplayName());
                quests = QuestGivers.entries(player, stepKey(role.chain(), role.step()), List.of(step.quest()));
            }
            return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), subtitle, greeting, rep, true, quests, List.of());
        }

        @Override
        public void handleAction(ServerPlayer player, Entity entity, String action, String argument) {
            Villager villager = (Villager) entity;
            ChainRole role = villager.getData(ModAttachments.CHAIN_ROLE);
            Optional<QuestChainDefinition> def = FealtyDataManager.chain(role.chain());
            ChainProgress progress = progress(player, role.chain());
            if (def.isEmpty() || progress == null || progress.stage() != STAGE_STEPS || !role.village().equals(progress.origin())
                    || role.step() >= def.get().steps().size() || progress.stepsDone().contains(role.step())) {
                return;
            }
            ResourceLocation key = stepKey(role.chain(), role.step());
            switch (action) {
                case QuestActionPayload.ACCEPT -> QuestManager.accept(player, key, progress.origin(),
                        def.get().steps().get(role.step()).quest(), new CompoundTag());
                case QuestActionPayload.TURN_IN -> QuestManager.turnIn(player, key);
                case QuestActionPayload.ABANDON -> QuestManager.abandon(player, key, false);
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
                    def.get().trialQuest().flatMap(QuestManager::offerEntry).ifPresent(quests::add);
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
                    if (progress.stage() == STAGE_HAMLET && def.get().trialQuest().isPresent()) {
                        CompoundTag init = new CompoundTag();
                        init.put("anchor", NbtUtils.writeBlockPos(entity.blockPosition()));
                        if (QuestManager.accept(player, key, progress.origin(), def.get().trialQuest().get(), init)) {
                            progress.setStage(STAGE_TRIAL);
                        }
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
        }
    }
}
