package com.selluastar.fealty.guard;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

/**
 * What a guard has been told to do by an Honored player (escort) or a lord (horn and spoken commands). The
 * {@code leader} is the player who gave the order; a lord's orders lapse when they stop being lord.
 */
public final class GuardOrders {
    public static final Codec<GuardOrders> CODEC = RecordCodecBuilder.create(i -> i.group(
            Mode.CODEC.optionalFieldOf("mode", Mode.NONE).forGetter(o -> o.mode),
            UUIDUtil.CODEC.optionalFieldOf("leader").forGetter(o -> Optional.ofNullable(o.leader)),
            Codec.LONG.optionalFieldOf("until", 0L).forGetter(o -> o.until),
            BlockPos.CODEC.optionalFieldOf("hold").forGetter(o -> Optional.ofNullable(o.holdPos))
    ).apply(i, (mode, leader, until, hold) -> {
        GuardOrders o = new GuardOrders();
        o.mode = mode;
        o.leader = leader.orElse(null);
        o.until = until;
        o.holdPos = hold.orElse(null);
        return o;
    }));

    private Mode mode = Mode.NONE;
    @Nullable
    private UUID leader;
    private long until;
    @Nullable
    private BlockPos holdPos;

    public Mode mode() {
        return mode;
    }

    @Nullable
    public UUID leader() {
        return leader;
    }

    public long until() {
        return until;
    }

    @Nullable
    public BlockPos holdPos() {
        return holdPos;
    }

    public void set(Mode mode, @Nullable UUID leader, long until, @Nullable BlockPos holdPos) {
        this.mode = mode;
        this.leader = leader;
        this.until = until;
        this.holdPos = holdPos;
    }

    public void clear() {
        set(Mode.NONE, null, 0, null);
    }

    public boolean isActive(long gameTime) {
        return mode != Mode.NONE && (until <= 0 || gameTime < until);
    }

    /** Whether these are a lord's orders (as opposed to an escort, or none). */
    public boolean isLordsOrder() {
        return mode == Mode.FOLLOW || mode == Mode.HOLD || mode == Mode.GUARD || mode == Mode.RETURN;
    }

    public enum Mode implements StringRepresentable {
        NONE("none"),
        /** Follow an Honored player for a while. */
        ESCORT("escort"),
        /** Lord's command: follow the lord. */
        FOLLOW("follow"),
        /** Lord's command: stay here. */
        HOLD("hold"),
        /** Lord's command: patrol and defend the village (the normal behaviour). */
        DEFEND("defend"),
        /** Lord's command: keep watch over the area around a spot. */
        GUARD("guard"),
        /** Lord's command: head back to the village. */
        RETURN("return");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
