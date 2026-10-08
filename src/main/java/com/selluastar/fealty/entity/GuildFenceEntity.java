package com.selluastar.fealty.entity;

import com.selluastar.fealty.outlaw.ThievesGuild;
import com.selluastar.fealty.quest.QuestGiver;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/** The thieves guild's contact, found in bandit camps. Hands out the guild's stealth questline to outlaws. */
public class GuildFenceEntity extends QuestGiverEntity {
    public GuildFenceEntity(EntityType<? extends GuildFenceEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseAttributes();
    }

    @Override
    public QuestGiver giver() {
        return ThievesGuild.FENCE;
    }
}
