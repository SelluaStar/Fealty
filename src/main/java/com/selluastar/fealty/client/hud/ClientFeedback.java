package com.selluastar.fealty.client.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import com.selluastar.fealty.client.ui.FealtyToast;
import com.selluastar.fealty.config.FealtyClientConfig;
import com.selluastar.fealty.network.FeedbackPayload;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-side queue behind the reputation feed and announcement banner. */
public final class ClientFeedback {
    /** One line in the rep feed. Repeated changes to the same faction merge while the line is fresh. */
    public static final class FeedLine {
        public final Component faction;
        public int amount;
        public final int color;
        public long start;

        FeedLine(Component faction, int amount, int color, long start) {
            this.faction = faction;
            this.amount = amount;
            this.color = color;
            this.start = start;
        }
    }

    public record Banner(Component title, Component detail, int color, String icon) {
    }

    public static final long FEED_LIFETIME = 4500L;
    public static final long BANNER_LIFETIME = 4200L;
    private static final int FEED_MAX = 6;

    private static final List<FeedLine> FEED = new ArrayList<>();
    private static final Deque<Banner> BANNERS = new ArrayDeque<>();
    private static Banner currentBanner;
    private static long bannerStart;

    private ClientFeedback() {
    }

    public static void handle(FeedbackPayload payload) {
        switch (payload.kind()) {
            case REP -> addRep(payload.title(), payload.amount(), payload.color());
            case BANNER -> {
                if (FealtyClientConfig.ANNOUNCEMENTS.get()) {
                    BANNERS.add(new Banner(payload.title(), payload.detail(), payload.color(), payload.icon()));
                }
            }
            case TOAST -> Minecraft.getInstance().getToasts().addToast(
                    new FealtyToast(payload.icon(), payload.item(), payload.title(), payload.detail()));
        }
    }

    private static void addRep(Component faction, int amount, int color) {
        if (!FealtyClientConfig.REP_FEED.get()) {
            return;
        }
        long now = Util.getMillis();
        for (FeedLine line : FEED) {
            if (line.faction.getString().equals(faction.getString()) && now - line.start < FEED_LIFETIME / 2
                    && Integer.signum(line.amount) == Integer.signum(amount)) {
                line.amount += amount;
                line.start = now;
                return;
            }
        }
        FEED.add(new FeedLine(faction, amount, color, now));
        while (FEED.size() > FEED_MAX) {
            FEED.removeFirst();
        }
    }

    public static List<FeedLine> feed() {
        long now = Util.getMillis();
        Iterator<FeedLine> it = FEED.iterator();
        while (it.hasNext()) {
            if (now - it.next().start > FEED_LIFETIME) {
                it.remove();
            }
        }
        return FEED;
    }

    /** The banner on screen now, if any, advancing the queue as banners expire. */
    public static Banner banner() {
        long now = Util.getMillis();
        if (currentBanner != null && now - bannerStart > BANNER_LIFETIME) {
            currentBanner = null;
        }
        if (currentBanner == null && !BANNERS.isEmpty()) {
            currentBanner = BANNERS.poll();
            bannerStart = now;
        }
        return currentBanner;
    }

    public static long bannerAge() {
        return Util.getMillis() - bannerStart;
    }

    public static void clear() {
        FEED.clear();
        BANNERS.clear();
        currentBanner = null;
    }
}
