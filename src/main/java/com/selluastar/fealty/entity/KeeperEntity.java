package com.selluastar.fealty.entity;

import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.quest.QuestGiver;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * The rare villager at the end of the chain, living in a hidden hamlet. Only deals with players who are still
 * Trusted where the chain began. Cannot be harmed by players.
 */
public class KeeperEntity extends QuestGiverEntity {
    public KeeperEntity(EntityType<? extends KeeperEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseAttributes();
    }

    @Override
    public QuestGiver giver() {
        return ChainManager.KEEPER;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }
}
