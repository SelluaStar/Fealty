package com.selluastar.fealty.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.network.TrackQuestPayload;

import net.neoforged.neoforge.network.PacketDistributor;

/** The client's copy of the player's accepted quests, for the journal and the tracker. */
public final class ClientQuestCache {
    private static List<QuestView> quests = List.of();
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private ClientQuestCache() {
    }

    public static void set(List<QuestView> list) {
        quests = List.copyOf(list);
        LISTENERS.forEach(Runnable::run);
    }

    public static List<QuestView> quests() {
        return quests;
    }

    /** Quests the player tracks, in the order the server sent them. */
    public static List<QuestView> tracked() {
        return quests.stream().filter(QuestView::tracked).toList();
    }

    public static QuestView byInstance(UUID instance) {
        for (QuestView quest : quests) {
            if (quest.instance().equals(instance)) {
                return quest;
            }
        }
        return null;
    }

    /** Screens register here to refresh when quests change; they remove themselves when closed. */
    public static void listen(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void unlisten(Runnable listener) {
        LISTENERS.remove(listener);
    }

    /** Track the next untracked quest, or cycle the first tracked one to the back. */
    public static void trackNext() {
        if (quests.isEmpty()) {
            return;
        }
        QuestView untracked = quests.stream().filter(q -> !q.tracked()).findFirst().orElse(null);
        if (untracked != null) {
            setTracked(untracked.instance(), true);
        } else if (quests.size() > 1) {
            setTracked(quests.getFirst().instance(), false);
        }
    }

    public static void setTracked(UUID instance, boolean tracked) {
        PacketDistributor.sendToServer(new TrackQuestPayload(instance, tracked));
    }

    public static void clear() {
        quests = List.of();
    }
}
