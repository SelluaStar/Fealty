package com.selluastar.fealty.rep;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Fealty's day counter. It moves forward when the overworld's day advances (sleeping counts), keeps counting
 * once per 24000 ticks while the daylight cycle is frozen, and never goes back when {@code /time set} rewinds
 * the clock. Taxes, tribute, guard respawns, daily offers and daily caps all use it.
 */
public final class FealtyCalendar {
    private static final long DAY = 24000L;
    /** Most days credited at once (e.g. after {@code /time add} of a large amount). */
    private static final int MAX_JUMP = 30;

    private FealtyCalendar() {
    }

    public static long day(MinecraftServer server) {
        FealtyWorldData data = FealtyWorldData.get(server);
        start(server, data);
        return data.calendarDay;
    }

    /** Called regularly from the server tick. @return how many days passed since the last call */
    public static int tick(MinecraftServer server) {
        FealtyWorldData data = FealtyWorldData.get(server);
        if (start(server, data)) {
            return 0;
        }
        ServerLevel overworld = server.overworld();
        long index = overworld.getDayTime() / DAY;
        long gameTime = overworld.getGameTime();
        int advanced = 0;
        if (index > data.calendarLastIndex) {
            advanced = (int) Math.min(MAX_JUMP, index - data.calendarLastIndex);
        } else if (index == data.calendarLastIndex && gameTime - data.calendarLastAdvance >= DAY) {
            advanced = 1;
        }
        if (index != data.calendarLastIndex) {
            data.calendarLastIndex = index;
            data.setDirty();
        }
        if (advanced > 0) {
            data.calendarDay += advanced;
            data.calendarLastAdvance = gameTime;
            data.setDirty();
        }
        return advanced;
    }

    /** First use in a world: start counting from the overworld's current day. @return true if it just started */
    private static boolean start(MinecraftServer server, FealtyWorldData data) {
        if (data.calendarStarted) {
            return false;
        }
        ServerLevel overworld = server.overworld();
        data.calendarDay = overworld.getDayTime() / DAY;
        data.calendarLastIndex = data.calendarDay;
        data.calendarLastAdvance = overworld.getGameTime();
        data.calendarStarted = true;
        data.setDirty();
        return true;
    }
}
