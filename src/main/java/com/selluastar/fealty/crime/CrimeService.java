package com.selluastar.fealty.crime;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.Severity;
import com.selluastar.fealty.api.event.CrimeWitnessedEvent;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.SourceSettings;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.outlaw.HeatManager;
import com.selluastar.fealty.registry.ModCriteria;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.common.NeoForge;

/** Resolves a crime: who saw it, what it costs, and who comes running. */
public final class CrimeService {
    private CrimeService() {
    }

    public record Result(boolean witnessed, int repChange, Severity severity) {
        static final Result UNSEEN = new Result(false, 0, Severity.MODERATE);
    }

    /**
     * @param victim a living victim who always counts as a witness (a threatened or struck villager), or null
     * @param loud   whether the crime is heard rather than seen (no line of sight needed)
     */
    public static Result commit(ServerPlayer player, ResourceLocation faction, ResourceLocation crime, BlockPos pos,
                                @Nullable LivingEntity victim, boolean loud) {
        if (player.isCreative() || player.isSpectator()) {
            return Result.UNSEEN;
        }
        ServerLevel level = player.serverLevel();
        SourceSettings settings = FealtyDataManager.source(crime);
        List<LivingEntity> witnesses = new ArrayList<>(WitnessService.witnesses(level, player, pos, faction, loud));
        if (victim != null && victim.isAlive() && !witnesses.contains(victim)) {
            witnesses.add(victim);
        }
        boolean needsWitness = settings.requiresWitness().orElse(true);
        if (witnesses.isEmpty() && needsWitness) {
            ModCriteria.CRIME.get().trigger(player, crime, false);
            return Result.UNSEEN;
        }

        Severity severity = settings.severity().orElse(Severity.MODERATE);
        if (!witnesses.isEmpty()) {
            CrimeWitnessedEvent event = NeoForge.EVENT_BUS.post(new CrimeWitnessedEvent(player, faction, crime, pos, witnesses, severity));
            if (event.isCanceled()) {
                ModCriteria.CRIME.get().trigger(player, crime, false);
                return Result.UNSEEN;
            }
            severity = event.getSeverity();
        }

        RepManager.meet(player, faction);
        int change = RepManager.applySource(player, faction, crime, severity.multiplier());
        if (settings.heat() > 0) {
            HeatManager.addHeat(player, faction, settings.heat());
        }
        if (settings.alertsGuards()) {
            GuardManager.alert(level, player, faction, pos, severity);
        }
        for (LivingEntity witness : witnesses) {
            if (witness instanceof AbstractVillager villager) {
                villager.setUnhappyCounter(40);
            }
        }
        if (!witnesses.isEmpty()) {
            player.displayClientMessage(Component.translatable("fealty.crime.seen",
                    witnesses.getFirst().getDisplayName()), true);
        }
        ModCriteria.CRIME.get().trigger(player, crime, true);
        return new Result(true, change, severity);
    }
}
