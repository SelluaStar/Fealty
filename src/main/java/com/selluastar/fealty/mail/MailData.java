package com.selluastar.fealty.mail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.selluastar.fealty.Fealty;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Letters, stored with the overworld ({@code data/fealty_mail.dat}): each player's inbox, and letters on their way to
 * villages. Kept apart from the rest of Fealty's data because parcels are items, which need the registries to save.
 */
public final class MailData extends SavedData {
    private static final String NAME = "fealty_mail";
    private static final Codec<Map<UUID, List<Letter>>> INBOXES_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Letter.CODEC.listOf());
    private static final SavedData.Factory<MailData> FACTORY = new SavedData.Factory<>(MailData::new, MailData::load, null);

    private final Map<UUID, List<Letter>> inboxes = new HashMap<>();
    private final List<Letter> toVillages = new ArrayList<>();

    public static MailData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static MailData load(CompoundTag tag, HolderLookup.Provider registries) {
        MailData data = new MailData();
        RegistryOps<net.minecraft.nbt.Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        if (tag.contains("inboxes")) {
            INBOXES_CODEC.parse(ops, tag.get("inboxes"))
                    .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to load mail: {}", e))
                    .ifPresent(map -> map.forEach((player, letters) -> data.inboxes.put(player, new ArrayList<>(letters))));
        }
        if (tag.contains("to_villages")) {
            Letter.CODEC.listOf().parse(ops, tag.get("to_villages"))
                    .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to load mail: {}", e))
                    .ifPresent(data.toVillages::addAll);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        RegistryOps<net.minecraft.nbt.Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        INBOXES_CODEC.encodeStart(ops, inboxes)
                .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to save mail: {}", e))
                .ifPresent(t -> tag.put("inboxes", t));
        Letter.CODEC.listOf().encodeStart(ops, toVillages)
                .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to save mail: {}", e))
                .ifPresent(t -> tag.put("to_villages", t));
        return tag;
    }

    /** A player's letters, oldest first, including those still on their way. */
    public List<Letter> inbox(UUID player) {
        return inboxes.computeIfAbsent(player, k -> new ArrayList<>());
    }

    public Map<UUID, List<Letter>> inboxes() {
        return inboxes;
    }

    /** Letters on their way to villages. */
    public List<Letter> toVillages() {
        return toVillages;
    }
}
