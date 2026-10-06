package com.selluastar.fealty.network;

import java.util.ArrayList;
import java.util.List;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: open (or refresh) the Village Hall, where a lord runs their village. {@code near} says whether the
 * lord is in the village (collecting tribute, feasts and recruiting need them there). {@code tribute},
 * {@code loyalty} and {@code giftChance} give, for each tax level, the tribute gathered per day, the lord's daily
 * change in standing and the daily chance of a gift of love; {@code gifts} is how many gifts wait in the treasury.
 */
public record OpenHallPayload(String village, String name, int color, String lord, int daysRuled, int population, int guardsAlive,
                              int guardsTotal, int standing, int taxLevel, double treasury, List<Float> tribute, List<Integer> loyalty,
                              int feastCooldown, boolean near, List<GuardRow> roster, int recruitCost, int feastFood, int feastEmeralds,
                              List<Float> giftChance, int gifts, War war)
        implements CustomPacketPayload {
    public static final Type<OpenHallPayload> TYPE = new Type<>(Fealty.id("open_hall"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenHallPayload> STREAM_CODEC = StreamCodec.of(OpenHallPayload::write, OpenHallPayload::read);

    /**
     * One guard on the roster: a Fealty guard's rank (or {@code golem} / {@code guard} for the village's other guards) and
     * name, and whether they are on duty (0), with the lord (1), fallen (2), a post not yet filled (3) or not seen lately (4).
     */
    public record GuardRow(String rank, String name, int state, int days) {
    }

    /**
     * A pillager stronghold on the War tab: kind 0 for a camp (or fort or castle), 1 for an outpost; distance and
     * bearing (degrees from north, clockwise) from the village; captives known to be held; days left of a raze;
     * whether the lord's current raid is against it; its threat (1 to 5 skulls) and trait; about how many defend it
     * (0: whoever is there); what a raid on it costs; the guards and militia who would march against it; and the
     * tribute and days of peace razing it wins.
     */
    public record StrongholdRow(Component name, int kind, int distance, int bearing, int captives, int razedDays, boolean target,
                                int threat, String trait, int defenders, int cost, int warband, int levy, int spoils, int peaceDays) {
    }

    /**
     * The War tab: known strongholds, the guards ready to march and the levy, what a raid and scouting cost, days
     * before the village can raise a warband again, whether scouts went out today, the lord's raid (its target's
     * name, or empty; phase 1 marching, 2 fighting; distance from the lord) and days of peace left.
     */
    public record War(List<StrongholdRow> rows, int ready, int levy, int raidCost, int scoutCost, int cooldown, boolean scouted,
                      Component campaign, int phase, int distance, int peaceDays) {
        public static final War NONE = new War(List.of(), 0, 0, 0, 0, 0, false, Component.empty(), 0, 0, 0);
    }

    private static void write(RegistryFriendlyByteBuf buf, OpenHallPayload p) {
        ByteBufCodecs.STRING_UTF8.encode(buf, p.village());
        ByteBufCodecs.STRING_UTF8.encode(buf, p.name());
        buf.writeInt(p.color());
        ByteBufCodecs.STRING_UTF8.encode(buf, p.lord());
        buf.writeVarInt(p.daysRuled());
        buf.writeVarInt(p.population());
        buf.writeVarInt(p.guardsAlive());
        buf.writeVarInt(p.guardsTotal());
        buf.writeInt(p.standing());
        buf.writeVarInt(p.taxLevel());
        buf.writeDouble(p.treasury());
        buf.writeVarInt(p.tribute().size());
        p.tribute().forEach(buf::writeFloat);
        buf.writeVarInt(p.loyalty().size());
        p.loyalty().forEach(buf::writeInt);
        buf.writeVarInt(p.feastCooldown());
        buf.writeBoolean(p.near());
        buf.writeVarInt(p.roster().size());
        for (GuardRow row : p.roster()) {
            ByteBufCodecs.STRING_UTF8.encode(buf, row.rank());
            ByteBufCodecs.STRING_UTF8.encode(buf, row.name());
            buf.writeVarInt(row.state());
            buf.writeVarInt(row.days());
        }
        buf.writeVarInt(p.recruitCost());
        buf.writeVarInt(p.feastFood());
        buf.writeVarInt(p.feastEmeralds());
        buf.writeVarInt(p.giftChance().size());
        p.giftChance().forEach(buf::writeFloat);
        buf.writeVarInt(p.gifts());
        War war = p.war();
        buf.writeVarInt(war.rows().size());
        for (StrongholdRow row : war.rows()) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, row.name());
            buf.writeVarInt(row.kind());
            buf.writeVarInt(row.distance());
            buf.writeVarInt(row.bearing());
            buf.writeVarInt(row.captives());
            buf.writeVarInt(row.razedDays());
            buf.writeBoolean(row.target());
            buf.writeVarInt(row.threat());
            buf.writeUtf(row.trait(), 32);
            buf.writeVarInt(row.defenders());
            buf.writeVarInt(row.cost());
            buf.writeVarInt(row.warband());
            buf.writeVarInt(row.levy());
            buf.writeVarInt(row.spoils());
            buf.writeVarInt(row.peaceDays());
        }
        buf.writeVarInt(war.ready());
        buf.writeVarInt(war.levy());
        buf.writeVarInt(war.raidCost());
        buf.writeVarInt(war.scoutCost());
        buf.writeVarInt(war.cooldown());
        buf.writeBoolean(war.scouted());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, war.campaign());
        buf.writeVarInt(war.phase());
        buf.writeVarInt(war.distance());
        buf.writeVarInt(war.peaceDays());
    }

    private static OpenHallPayload read(RegistryFriendlyByteBuf buf) {
        String village = ByteBufCodecs.STRING_UTF8.decode(buf);
        String name = ByteBufCodecs.STRING_UTF8.decode(buf);
        int color = buf.readInt();
        String lord = ByteBufCodecs.STRING_UTF8.decode(buf);
        int days = buf.readVarInt();
        int population = buf.readVarInt();
        int alive = buf.readVarInt();
        int total = buf.readVarInt();
        int standing = buf.readInt();
        int tax = buf.readVarInt();
        double treasury = buf.readDouble();
        List<Float> tribute = new ArrayList<>();
        for (int i = Math.min(buf.readVarInt(), 16); i > 0; i--) {
            tribute.add(buf.readFloat());
        }
        List<Integer> loyalty = new ArrayList<>();
        for (int i = Math.min(buf.readVarInt(), 16); i > 0; i--) {
            loyalty.add(buf.readInt());
        }
        int feast = buf.readVarInt();
        boolean near = buf.readBoolean();
        List<GuardRow> roster = new ArrayList<>();
        for (int i = Math.min(buf.readVarInt(), 64); i > 0; i--) {
            roster.add(new GuardRow(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), buf.readVarInt(), buf.readVarInt()));
        }
        int recruitCost = buf.readVarInt();
        int feastFood = buf.readVarInt();
        int feastEmeralds = buf.readVarInt();
        List<Float> giftChance = new ArrayList<>();
        for (int i = Math.min(buf.readVarInt(), 16); i > 0; i--) {
            giftChance.add(buf.readFloat());
        }
        int gifts = buf.readVarInt();
        List<StrongholdRow> rows = new ArrayList<>();
        for (int i = Math.min(buf.readVarInt(), 64); i > 0; i--) {
            rows.add(new StrongholdRow(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(32), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        War war = new War(rows, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        return new OpenHallPayload(village, name, color, lord, days, population, alive, total, standing, tax, treasury, tribute, loyalty,
                feast, near, roster, recruitCost, feastFood, feastEmeralds, giftChance, gifts, war);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
