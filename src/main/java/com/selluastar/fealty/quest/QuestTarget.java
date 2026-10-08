package com.selluastar.fealty.quest;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/** Marks a mob as the target of a player's quest. */
public record QuestTarget(UUID owner, UUID questInstance) {
    private static final UUID NIL = new UUID(0, 0);

    public static final Codec<QuestTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(QuestTarget::owner),
            UUIDUtil.CODEC.fieldOf("quest").forGetter(QuestTarget::questInstance)
    ).apply(i, QuestTarget::new));

    public static QuestTarget empty() {
        return new QuestTarget(NIL, NIL);
    }

    public boolean isEmpty() {
        return NIL.equals(owner);
    }
}
