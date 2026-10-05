package com.selluastar.fealty.client.bubble;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.selluastar.fealty.network.QuestMarkersPayload;

/** Which nearby NPCs have work for the player, as last sent by the server. */
public final class QuestMarkers {
    private static final Map<Integer, QuestMarkersPayload.Kind> MARKERS = new HashMap<>();

    private QuestMarkers() {
    }

    public static void set(List<QuestMarkersPayload.Marker> markers) {
        MARKERS.clear();
        for (QuestMarkersPayload.Marker marker : markers) {
            MARKERS.put(marker.entityId(), marker.kind());
        }
    }

    public static QuestMarkersPayload.Kind get(int entityId) {
        return MARKERS.get(entityId);
    }

    public static void clear() {
        MARKERS.clear();
    }
}
