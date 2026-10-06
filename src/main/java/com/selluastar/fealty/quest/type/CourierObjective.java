package com.selluastar.fealty.quest.type;

import com.selluastar.fealty.network.QuestView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.dialogue.DialogueNode;
import com.selluastar.fealty.item.LetterInfo;
import com.selluastar.fealty.mail.MailService;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.quest.QuestType;
import com.selluastar.fealty.registry.ModDataComponents;
import com.selluastar.fealty.registry.ModItems;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.Maps;
import com.selluastar.fealty.village.VillageNames;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

/**
 * {@code fealty:courier}: carry a sealed letter to another village. The {@code recipient} is the village's trusting
 * {@code elder} (the default), a {@code person} who lives there (one of their villagers is named when the player
 * arrives, of the given {@code profession} if there is one, and takes the letter from the player's hand), or the
 * village's {@code mailbox}. Completes on delivery and earns a little standing with the receiving village.
 */
public record CourierObjective(String recipient, Optional<ResourceLocation> profession) implements QuestObjective {
    public static final MapCodec<CourierObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("recipient", "elder").forGetter(CourierObjective::recipient),
            ResourceLocation.CODEC.optionalFieldOf("profession").forGetter(CourierObjective::profession)
    ).apply(i, CourierObjective::new));
    public static final String ELDER = "elder";
    public static final String PERSON = "person";
    public static final String MAILBOX = "mailbox";
    private static final String DELIVER = "deliver_letter";

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.COURIER.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        Optional<VillageRecord> origin = ctx.village();
        if (origin.isEmpty()) {
            return false;
        }
        boolean toElder = ELDER.equals(recipient);
        Optional<VillageRecord> destination = findDestination(ctx.level(), origin.get(), ctx.level().getRandom(), toElder);
        if (destination.isEmpty()) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.courier.none"));
            return false;
        }
        VillageRecord to = destination.get();
        ctx.state().putString("to", to.id().toString());
        ctx.state().putString("to_name", to.name());
        ctx.state().putString("recipient", recipient);
        ctx.state().put("anchor", NbtUtils.writeBlockPos(to.center()));
        String toName = to.name();
        if (PERSON.equals(recipient)) {
            String person = VillageNames.personName(ctx.level().getRandom());
            ctx.state().putString("person", person);
            toName = Component.translatable("fealty.courier.person_address", person, to.name()).getString();
        } else if (MAILBOX.equals(recipient)) {
            toName = Component.translatable("fealty.courier.mailbox_address", to.name()).getString();
        }
        ItemStack letter = new ItemStack(ModItems.SEALED_LETTER.get());
        letter.set(ModDataComponents.LETTER.get(), new LetterInfo(origin.get().id(), origin.get().name(), to.id(), toName,
                to.center(), ctx.player().getUUID(), ctx.quest().instanceId()));
        Maps.give(ctx.player(), letter);
        return true;
    }

    static Optional<VillageRecord> findDestination(ServerLevel level, VillageRecord origin, RandomSource random, boolean elder) {
        int min = FealtyConfig.COURIER_MIN_DISTANCE.get();
        int max = FealtyConfig.COURIER_MAX_DISTANCE.get();
        List<VillageRecord> known = new ArrayList<>();
        for (VillageRecord record : FealtyWorldData.get(level.getServer()).villages()) {
            if (record != origin && record.dimension().equals(origin.dimension()) && (!elder || record.hasElder()) && !record.isBroken()) {
                double d = Math.sqrt(record.center().distSqr(origin.center()));
                if (d >= min && d <= max) {
                    known.add(record);
                }
            }
        }
        if (!known.isEmpty()) {
            return Optional.of(known.get(random.nextInt(known.size())));
        }
        if (!origin.dimension().equals(level.dimension())) {
            return Optional.empty();
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            BlockPos probe = origin.center().offset(Mth.floor(Math.cos(angle) * min), 0, Mth.floor(Math.sin(angle) * min));
            TagKey<Structure> kind = elder ? FealtyTags.Structures.ELDER_VILLAGES : StructureTags.VILLAGE;
            BlockPos found = level.findNearestMapStructure(kind, probe, 50, false);
            if (found == null || origin.bounds().isInside(found)) {
                continue;
            }
            double d = Math.sqrt(found.distSqr(origin.center()));
            if (d < min || d > max) {
                continue;
            }
            ChunkAccess chunk = level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
            for (StructureStart start : chunk.getAllStarts().values()) {
                Holder<Structure> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(start.getStructure());
                if (start.isValid() && holder.is(kind)) {
                    Optional<VillageRecord> record = VillageResolver.villageForStart(level, start);
                    if (record.isPresent() && (!elder || record.get().hasElder())) {
                        return record;
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        BlockPos to = ctx.anchor();
        return List.of(Component.translatable("fealty.quest.courier.line", ctx.state().getString("to_name"), to.getX(), to.getZ()),
                objectiveLine(ctx));
    }

    private static String recipientOf(QuestContext ctx) {
        return ctx.state().contains("recipient") ? ctx.state().getString("recipient") : ELDER;
    }

    private static Component objectiveLine(QuestContext ctx) {
        return switch (recipientOf(ctx)) {
            case PERSON -> Component.translatable("fealty.objective.courier.person", ctx.state().getString("person"), ctx.state().getString("to_name"));
            case MAILBOX -> Component.translatable("fealty.objective.courier.mailbox", ctx.state().getString("to_name"));
            default -> Component.translatable("fealty.objective.courier", ctx.state().getString("to_name"));
        };
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.courier.preview"));
    }

    @Override
    public boolean completesOnReady() {
        return true;
    }

    /** The letter reached the right elder. */
    public static void deliver(QuestContext ctx, ItemStack letter) {
        letter.shrink(1);
        ResourceLocation to = ResourceLocation.tryParse(ctx.state().getString("to"));
        if (to != null) {
            RepManager.meet(ctx.player(), to);
            RepManager.applySource(ctx.player(), to, RepSources.COURIER);
        }
        FealtyEvents.fire(ctx.player(), FealtyEvents.COURIER_DELIVERED);
        ctx.setReady();
    }

    @Override
    public void cleanup(QuestContext ctx, boolean success) {
        if (success) {
            return;
        }
        for (ItemStack stack : ctx.player().getInventory().items) {
            LetterInfo info = stack.get(ModDataComponents.LETTER.get());
            if (info != null && info.quest().equals(ctx.quest().instanceId())) {
                stack.setCount(0);
            }
        }
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        return List.of(QuestView.Line.check(objectiveLine(ctx), ctx.quest().isReady()));
    }

    // ---- Delivering to a person ----

    private static Optional<UUID> person(QuestContext ctx) {
        return ctx.state().hasUUID("person_uuid") ? Optional.of(ctx.state().getUUID("person_uuid")) : Optional.empty();
    }

    /** When the player reaches the village, one of its villagers turns out to be the person the letter is for. */
    @Override
    public void tick(QuestContext ctx) {
        if (!PERSON.equals(recipientOf(ctx)) || ctx.quest().isReady()) {
            return;
        }
        ServerPlayer player = ctx.player();
        ResourceLocation to = ResourceLocation.tryParse(ctx.state().getString("to"));
        Optional<VillageRecord> village = to == null ? Optional.empty() : FealtyWorldData.get(player.server).village(to);
        if (village.isEmpty() || !village.get().contains(player.level().dimension(), player.blockPosition())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Optional<UUID> bound = person(ctx);
        if (bound.isPresent() && level.getEntity(bound.get()) instanceof Villager existing && existing.isAlive()) {
            return;
        }
        List<Villager> candidates = level.getEntitiesOfClass(Villager.class, AABB.of(village.get().bounds()),
                v -> v.isAlive() && !v.isBaby() && !ChainManager.hasRole(v) && village.get().id().equals(FactionResolver.factionOf(v).orElse(null)));
        if (candidates.isEmpty()) {
            return;
        }
        List<Villager> fitting = profession.map(p -> candidates.stream()
                .filter(v -> p.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()))).toList())
                .orElse(List.of());
        List<Villager> pool = fitting.isEmpty() ? candidates : fitting;
        Villager chosen = pool.stream().filter(v -> v.getCustomName() == null).findAny().orElse(pool.get(0));
        chosen.setCustomName(Component.literal(ctx.state().getString("person")));
        ChainManager.lockProfession(chosen);
        ctx.state().putUUID("person_uuid", chosen.getUUID());
        ctx.dirty();
        if (bound.isEmpty()) {
            Feedback.toast(player, "mail", Component.translatable("fealty.toast.courier_arrived", village.get().name()),
                    Component.translatable("fealty.toast.courier_find", ctx.state().getString("person")));
        }
    }

    @Override
    public List<UUID> markedEntities(QuestContext ctx) {
        return ctx.quest().isReady() ? List.of() : person(ctx).map(List::of).orElse(List.of());
    }

    @Override
    public Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        Optional<UUID> person = person(ctx);
        if (person.isPresent() && ctx.player().level().dimension().equals(ctx.dimension())) {
            Entity entity = ctx.questLevel().getEntity(person.get());
            if (entity != null) {
                return Optional.of(new QuestView.Waypoint(ctx.dimension(), entity.blockPosition(), Component.literal(ctx.state().getString("person"))));
            }
        }
        return ctx.storedAnchor().map(pos -> new QuestView.Waypoint(ctx.dimension(), pos, Component.literal(ctx.state().getString("to_name"))));
    }

    @Override
    public List<DialogueNode.Option> dialogueOptions(QuestContext ctx, Entity npc) {
        if (ctx.quest().isReady() || !person(ctx).map(npc.getUUID()::equals).orElse(false)) {
            return List.of();
        }
        return List.of(DialogueNode.Option.of(DELIVER, Component.translatable("fealty.dialogue.option.deliver"), "mail"));
    }

    @Override
    public Optional<Component> onDialogue(QuestContext ctx, Entity npc, String option) {
        if (!DELIVER.equals(option) || !person(ctx).map(npc.getUUID()::equals).orElse(false)) {
            return Optional.empty();
        }
        ItemStack letter = letterFor(ctx);
        if (letter.isEmpty()) {
            return Optional.of(Component.translatable("fealty.courier.no_letter"));
        }
        if (npc instanceof Villager villager) {
            MailService.handOver(villager, letter.copyWithCount(1));
        }
        LetterInfo info = letter.get(ModDataComponents.LETTER.get());
        deliver(ctx, letter);
        return Optional.of(Component.translatable("fealty.courier.person_thanks", info != null ? info.fromName() : "?"));
    }

    private static ItemStack letterFor(QuestContext ctx) {
        for (ItemStack stack : ctx.player().getInventory().items) {
            LetterInfo info = stack.get(ModDataComponents.LETTER.get());
            if (info != null && info.quest().equals(ctx.quest().instanceId())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ---- Who a letter goes to ----

    /** The kind of recipient a letter's quest names ({@code elder} for letters from before there were others). */
    public static String recipientOf(ServerPlayer player, LetterInfo info) {
        return QuestManager.byInstance(player, info.quest()).map(CourierObjective::recipientOf).orElse(ELDER);
    }

    public static boolean goesToElder(ServerPlayer player, LetterInfo info) {
        return ELDER.equals(recipientOf(player, info));
    }

    public static boolean goesToMailbox(ServerPlayer player, LetterInfo info) {
        return MAILBOX.equals(recipientOf(player, info));
    }

    /** The player posts a letter at the right village's mailbox. */
    public static void postAtMailbox(ServerPlayer player, LetterInfo info) {
        Optional<QuestContext> ctx = QuestManager.byInstance(player, info.quest());
        if (ctx.isEmpty()) {
            return;
        }
        ItemStack letter = letterFor(ctx.get());
        if (letter.isEmpty()) {
            return;
        }
        player.sendSystemMessage(Component.translatable("fealty.quest.courier.posted", info.fromName(), ctx.get().state().getString("to_name")));
        deliver(ctx.get(), letter);
    }

}
