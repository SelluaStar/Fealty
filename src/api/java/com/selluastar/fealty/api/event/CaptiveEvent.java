package com.selluastar.fealty.api.event;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** A villager held captive in a pillager camp. Fired on {@code NeoForge.EVENT_BUS}. */
public abstract class CaptiveEvent extends Event {
    private final LivingEntity captive;
    private final BlockPos camp;
    @Nullable
    private final ResourceLocation home;

    protected CaptiveEvent(LivingEntity captive, BlockPos camp, @Nullable ResourceLocation home) {
        this.captive = captive;
        this.camp = camp;
        this.home = home;
    }

    public LivingEntity getCaptive() {
        return captive;
    }

    /** The camp's War Banner. */
    public BlockPos getCamp() {
        return camp;
    }

    /** The village they were taken from, if one is known. */
    public Optional<ResourceLocation> getHome() {
        return Optional.ofNullable(home);
    }

    /** A captive is put in a camp's cage. Cancel to leave the cage empty. */
    public static class Taken extends CaptiveEvent implements ICancellableEvent {
        public Taken(LivingEntity captive, BlockPos camp, @Nullable ResourceLocation home) {
            super(captive, camp, home);
        }
    }

    /** A captive is freed and sets off home. */
    public static class Freed extends CaptiveEvent {
        @Nullable
        private final ServerPlayer rescuer;

        public Freed(LivingEntity captive, BlockPos camp, @Nullable ResourceLocation home, @Nullable ServerPlayer rescuer) {
            super(captive, camp, home);
            this.rescuer = rescuer;
        }

        /** Who freed them; empty when a raid's victory did. */
        public Optional<ServerPlayer> getRescuer() {
            return Optional.ofNullable(rescuer);
        }
    }
}
