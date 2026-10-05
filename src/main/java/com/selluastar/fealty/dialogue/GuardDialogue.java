package com.selluastar.fealty.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.GuardStance;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.entity.VillageGuardEntity;
import com.selluastar.fealty.guard.Garrison;
import com.selluastar.fealty.guard.GarrisonManager;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.guard.GuardOrders;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Talking to a village guard: lords give orders, Honored players can ask for an escort, anyone can ask for news. */
final class GuardDialogue {
    static final String FOLLOW = "follow";
    static final String HOLD = "hold";
    static final String DISMISS = "dismiss";
    static final String ESCORT = "escort";
    static final String END_ESCORT = "end_escort";
    static final String REPORT = "report";

    private GuardDialogue() {
    }

    private static Optional<VillageRecord> village(ServerPlayer player, VillageGuardEntity guard) {
        return FactionResolver.factionOf(guard).flatMap(id -> FealtyWorldData.get(player.server).village(id));
    }

    private static boolean followsPlayer(ServerPlayer player, VillageGuardEntity guard) {
        GuardOrders orders = guard.orders();
        return orders.isActive(guard.level().getGameTime()) && player.getUUID().equals(orders.leader())
                && (orders.mode() == GuardOrders.Mode.FOLLOW || orders.mode() == GuardOrders.Mode.ESCORT);
    }

    static DialogueNode node(ServerPlayer player, VillageGuardEntity guard, @Nullable Component reply) {
        Optional<VillageRecord> village = village(player, guard);
        Component text = reply != null ? reply
                : DialogueLines.pick("guard_greet", SpeakerContext.of(guard, player), guard.getRandom(), player.getDisplayName())
                .orElse(Component.translatable("fealty.guard.dialogue.fallback"));
        boolean lord = village.map(v -> v.lord().isLord(player.getUUID())).orElse(false);
        boolean following = followsPlayer(player, guard);
        List<DialogueNode.Option> options = new ArrayList<>();
        if (lord) {
            options.add(following
                    ? DialogueNode.Option.of(HOLD, Component.translatable("fealty.guard.option.hold"), "shield")
                    : DialogueNode.Option.of(FOLLOW, Component.translatable("fealty.guard.option.follow"), "guard"));
            if (guard.hasOrders()) {
                options.add(DialogueNode.Option.of(DISMISS, Component.translatable("fealty.guard.option.dismiss"), "house"));
            }
        } else {
            Optional<ResourceLocation> faction = FactionResolver.factionOf(guard);
            if (following) {
                options.add(DialogueNode.Option.of(END_ESCORT, Component.translatable("fealty.guard.option.end_escort"), "door"));
            } else if (faction.isPresent()) {
                boolean honored = GuardManager.stance(player, faction.get()) == GuardStance.ESCORT;
                options.add(honored
                        ? DialogueNode.Option.of(ESCORT, Component.translatable("fealty.guard.option.escort"), "guard")
                        : DialogueNode.Option.disabled(ESCORT, Component.translatable("fealty.guard.option.escort"), "guard",
                        Component.translatable("fealty.guard.escort_hint")));
            }
        }
        options.add(DialogueNode.Option.of(REPORT, Component.translatable("fealty.guard.option.report"), "scroll"));
        options.add(DialogueNode.Option.of(DialogueService.BYE, Component.translatable("fealty.dialogue.option.bye"), "door"));
        guard.getLookControl().setLookAt(player);
        Component rank = Component.translatable("fealty.guard.rank." + guard.rank().getSerializedName());
        Component subtitle = village.map(v -> Component.translatable("fealty.guard.dialogue.subtitle", rank, v.name())).orElse(rank);
        return new DialogueNode(guard.getDisplayName(), subtitle, text, options);
    }

    static void handle(ServerPlayer player, VillageGuardEntity guard, String option) {
        Optional<VillageRecord> village = village(player, guard);
        boolean lord = village.map(v -> v.lord().isLord(player.getUUID())).orElse(false);
        GuardOrders orders = guard.orders();
        long now = guard.level().getGameTime();
        String answer = null;
        switch (option) {
            case FOLLOW -> {
                if (lord) {
                    orders.set(GuardOrders.Mode.FOLLOW, player.getUUID(), 0, null);
                    answer = "fealty.guard.reply.follow";
                }
            }
            case HOLD -> {
                if (lord) {
                    orders.set(GuardOrders.Mode.HOLD, player.getUUID(), 0, guard.blockPosition());
                    answer = "fealty.guard.reply.hold";
                }
            }
            case DISMISS -> {
                if (lord) {
                    boolean nearPost = guard.post() != null && guard.level() instanceof ServerLevel level
                            && GarrisonManager.inHomeDimension(level, guard) && guard.blockPosition().distSqr(guard.post()) < 48 * 48;
                    if (nearPost || guard.slot() < 0) {
                        orders.clear();
                    } else {
                        orders.set(GuardOrders.Mode.RETURN, player.getUUID(), 0, null);
                    }
                    answer = "fealty.guard.reply.dismiss";
                }
            }
            case ESCORT -> {
                Optional<ResourceLocation> faction = FactionResolver.factionOf(guard);
                if (!lord && faction.isPresent() && GuardManager.stance(player, faction.get()) == GuardStance.ESCORT) {
                    orders.set(GuardOrders.Mode.ESCORT, player.getUUID(), now + FealtyConfig.ESCORT_TICKS.get(), null);
                    FealtyEvents.fire(player, FealtyEvents.ESCORTED);
                    answer = "fealty.guard.reply.escort";
                }
            }
            case END_ESCORT -> {
                if (followsPlayer(player, guard)) {
                    orders.clear();
                    answer = "fealty.guard.reply.end_escort";
                }
            }
            case REPORT -> {
                Component report = report(player, village);
                Speech.say(guard, report);
                DialogueService.reply(player, guard, report);
                return;
            }
            default -> {
            }
        }
        if (answer == null) {
            DialogueService.refresh(player, guard);
            return;
        }
        Component text = Component.translatable(answer);
        Speech.say(guard, text);
        DialogueService.reply(player, guard, text);
        GarrisonManager.syncRetinue(player, true);
    }

    /** How the watch stands, and who rules the village. */
    private static Component report(ServerPlayer player, Optional<VillageRecord> village) {
        if (village.isEmpty()) {
            return Component.translatable("fealty.guard.report.none");
        }
        VillageRecord record = village.get();
        Garrison garrison = record.garrison();
        int total = garrison.slots().size();
        int alive = garrison.alive();
        Component watch = total == 0 ? Component.translatable("fealty.guard.report.no_watch")
                : alive < total ? Component.translatable("fealty.guard.report.fallen", alive, total, total - alive)
                : Component.translatable("fealty.guard.report.full", alive);
        Component rule;
        if (record.lord().uuid() == null) {
            rule = Component.translatable("fealty.guard.report.no_lord");
        } else if (record.lord().isLord(player.getUUID())) {
            rule = Component.translatable("fealty.guard.report.your_lordship");
        } else {
            rule = Component.translatable("fealty.guard.report.lord", record.lord().name());
        }
        return Component.empty().append(watch).append(" ").append(rule);
    }
}
