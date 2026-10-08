package com.selluastar.fealty.quest.type;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.registry.ModQuestTypes;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** {@code fealty:vault_raid}: empty village coffers without anyone seeing (a lockpick keeps it quiet). */
public record VaultRaidObjective(int count) implements QuestObjective {
    public static final MapCodec<VaultRaidObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 16).optionalFieldOf("count", 1).forGetter(VaultRaidObjective::count)
    ).apply(i, VaultRaidObjective::new));

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.VAULT_RAID.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        return true;
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        return List.of(Component.translatable("fealty.quest.vault_raid.line", count, Math.min(ctx.getInt("done"), count), count));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.vault_raid.preview", count));
    }

    @Override
    public void onTheft(QuestContext ctx, ResourceLocation village, Map<Item, Integer> stolen, boolean witnessed, boolean coffer) {
        if (!coffer || witnessed || ctx.quest().isReady()) {
            return;
        }
        // Each coffer raided must belong to a different village.
        net.minecraft.nbt.ListTag raided = ctx.state().getList("villages", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < raided.size(); i++) {
            if (raided.getString(i).equals(village.toString())) {
                return;
            }
        }
        raided.add(net.minecraft.nbt.StringTag.valueOf(village.toString()));
        ctx.state().put("villages", raided);
        int done = ctx.getInt("done") + 1;
        ctx.putInt("done", done);
        if (done >= count) {
            ctx.setReady();
        }
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        return List.of(QuestView.Line.count(Component.translatable("fealty.objective.vault_raid"), ctx.getInt("done"), count));
    }

}
