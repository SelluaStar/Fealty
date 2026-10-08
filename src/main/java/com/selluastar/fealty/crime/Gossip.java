package com.selluastar.fealty.crime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.mail.MailService;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.PlayerRepData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Word travels. Part of what a witnessed crime costs the player reaches the other villages nearby that know them,
 * at the start of the next day, and the nearest of them writes to say so.
 */
public final class Gossip {
    private Gossip() {
    }

    /** A crime was seen in a village and cost the player {@code change} (negative) there. */
    public static void heard(ServerPlayer player, ResourceLocation village, int change) {
        double share = FealtyConfig.WORD_TRAVELS_SHARE.get();
        if (change >= 0 || share <= 0 || !Factions.isVillage(village)) {
            return;
        }
        int amount = (int) Math.round(change * share);
        if (amount != 0) {
            RepManager.data(player).gossip().merge(village, amount, Integer::sum);
        }
    }

    /** At the start of each day, yesterday's news reaches the neighbours. */
    public static void spread(MinecraftServer server) {
        FealtyWorldData world = FealtyWorldData.get(server);
        double radius = FealtyConfig.WORD_TRAVELS_RADIUS.get();
        boolean changed = false;
        for (Map.Entry<UUID, PlayerRepData> entry : world.players().entrySet()) {
            PlayerRepData data = entry.getValue();
            if (data.gossip().isEmpty()) {
                continue;
            }
            Map<ResourceLocation, Integer> news = new HashMap<>(data.gossip());
            data.gossip().clear();
            changed = true;
            ServerPlayer online = server.getPlayerList().getPlayer(entry.getKey());
            for (Map.Entry<ResourceLocation, Integer> item : news.entrySet()) {
                Optional<VillageRecord> origin = world.village(item.getKey());
                if (origin.isEmpty()) {
                    continue;
                }
                List<VillageRecord> heard = new ArrayList<>();
                for (VillageRecord other : world.villages()) {
                    if (other.id().equals(origin.get().id()) || !other.dimension().equals(origin.get().dimension())
                            || !data.rep().containsKey(other.id()) || other.center().distSqr(origin.get().center()) > radius * radius) {
                        continue;
                    }
                    heard.add(other);
                }
                if (heard.isEmpty()) {
                    continue;
                }
                for (VillageRecord other : heard) {
                    if (online != null) {
                        RepManager.change(online, other.id(), item.getValue(), RepSources.WORD_TRAVELS);
                    } else {
                        RepManager.changeOffline(server, entry.getKey(), other.id(), item.getValue());
                    }
                }
                heard.sort(Comparator.comparingDouble(v -> v.center().distSqr(origin.get().center())));
                VillageRecord nearest = heard.getFirst();
                MailService.fromVillage(server, entry.getKey(), nearest, heard.size() == 1 ? "gossip" : "gossip_many", List.of(),
                        MailService.travelTime(Math.sqrt(nearest.center().distSqr(origin.get().center()))), origin.get().name(), heard.size() - 1);
            }
        }
        if (changed) {
            world.setDirty();
        }
    }
}
