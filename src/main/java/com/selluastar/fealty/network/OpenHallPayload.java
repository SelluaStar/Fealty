package com.selluastar.fealty.network;

import java.util.ArrayList;
import java.util.List;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
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
                              List<Float> giftChance, int gifts)
        implements CustomPacketPayload {
    public static final Type<OpenHallPayload> TYPE = new Type<>(Fealty.id("open_hall"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenHallPayload> STREAM_CODEC = StreamCodec.of(OpenHallPayload::write, OpenHallPayload::read);

    /**
     * One guard on the roster: a Fealty guard's rank (or {@code golem} / {@code guard} for the village's other guards) and
     * name, and whether they are on duty (0), with the lord (1), fallen (2), a post not yet filled (3) or not seen lately (4).
     */
    public record GuardRow(String rank, String name, int state, int days) {
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
        return new OpenHallPayload(village, name, color, lord, days, population, alive, total, standing, tax, treasury, tribute, loyalty,
                feast, near, roster, recruitCost, feastFood, feastEmeralds, giftChance, gifts);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
