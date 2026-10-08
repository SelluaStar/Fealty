package com.selluastar.fealty.chatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.api.Stronghold;
import com.selluastar.fealty.api.event.StrongholdEvent;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.outlaw.BanditCamps;
import com.selluastar.fealty.outlaw.HeatManager;
import com.selluastar.fealty.quest.FavorManager;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.Compass;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.war.Menace;
import com.selluastar.fealty.war.Scouting;
import com.selluastar.fealty.war.StrongholdKind;
import com.selluastar.fealty.war.StrongholdKinds;
import com.selluastar.fealty.war.StrongholdNames;
import com.selluastar.fealty.war.Strongholds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Real news a village's people know, built from the world as it is: strongholds and bandit camps nearby, who needs help,
 * what a villager sells, how the watch and the lord's village fare. Two villagers may chat about it (the bubbles only
 * hint), and a villager asked "What's up?" tells it ({@link Rumours}).
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class LiveTopics {
    public static final ResourceLocation STRONGHOLD = Fealty.id("rumour/stronghold");
    public static final ResourceLocation BANDITS = Fealty.id("rumour/bandits");
    public static final ResourceLocation GUARDS = Fealty.id("rumour/guards");
    public static final ResourceLocation MENACE = Fealty.id("rumour/menace");
    public static final ResourceLocation PEACE = Fealty.id("rumour/peace");
    public static final ResourceLocation TAX = Fealty.id("rumour/tax");
    public static final ResourceLocation TRADE = Fealty.id("rumour/trade");
    public static final ResourceLocation FAVOR = Fealty.id("rumour/favor");
    public static final ResourceLocation LEADER = Fealty.id("rumour/leader");

    /** Roughly the blocks across one cell of the camp and outpost placements (the structure search counts in cells). */
    private static final int CELL_BLOCKS = 512;
    private static final int GLOW_TICKS = 200;
    private static final double NEIGHBOURHOOD = 32.0;

    /** What the day's search for strongholds near a village found, so asking around does not search again. */
    private record Search(long day, List<Found> found) {
    }

    /** A stronghold a rumour can be about: one already known, or only found on the map. */
    private record Found(BlockPos pos, ResourceLocation structure, Stronghold.Kind kind, @Nullable Strongholds.Entry entry) {
    }

    private static final Map<ResourceLocation, Search> SEARCHES = new HashMap<>();

    private LiveTopics() {
    }

    // ---- For a chat between two villagers ----

    /** Something worth chatting about that is true of the world, whoever overhears it. */
    public static Optional<Topic> forChat(ServerLevel level, VillageRecord village, Villager first, Villager second, RandomSource random) {
        return pick(worldFacts(level, village, first, random, false), random);
    }

    // ---- For a villager asked "What's up?" ----

    /** Everything this villager could tell this player now. */
    public static List<Topic> facts(ServerLevel level, VillageRecord village, Villager teller, ServerPlayer player, RandomSource random) {
        List<Topic> facts = new ArrayList<>(worldFacts(level, village, teller, random, true));
        leader(player, village).ifPresent(facts::add);
        favor(level, village, teller, player).ifPresent(facts::add);
        return facts;
    }

    /** A weighted random topic. */
    public static Optional<Topic> pick(List<Topic> topics, RandomSource random) {
        int total = topics.stream().mapToInt(Topic::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Topic topic : topics) {
            roll -= topic.weight();
            if (roll < 0) {
                return Optional.of(topic);
            }
        }
        return Optional.empty();
    }

    // ---- The facts ----

    /** Facts true of the world, not of one player. {@code search} lets it look for strongholds not yet known. */
    private static List<Topic> worldFacts(ServerLevel level, VillageRecord village, Villager teller, RandomSource random, boolean search) {
        List<Topic> facts = new ArrayList<>();
        stronghold(level, village, random, search).ifPresent(facts::add);
        bandits(level, village, random).ifPresent(facts::add);
        guards(village, random).ifPresent(facts::add);
        menaceOrPeace(level, village, random).ifPresent(facts::add);
        tax(village, random).ifPresent(facts::add);
        trade(level, village, teller, random).ifPresent(facts::add);
        return facts;
    }

    private static Topic topic(ResourceLocation id, String kind, RandomSource random, Component tell, int weight, @Nullable Topic.Reveal reveal) {
        int variant = random.nextInt(2);
        String base = "fealty.chatter.live." + kind + "." + variant;
        return new Topic(id, List.of(Component.translatable(base + ".0"), Component.translatable(base + ".1")), tell, true, weight, reveal);
    }

    /** A pillager stronghold within the rumour range. */
    private static Optional<Topic> stronghold(ServerLevel level, VillageRecord village, RandomSource random, boolean search) {
        List<Found> found = strongholdsNear(level, village, search);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Found f = found.get(random.nextInt(found.size()));
        BlockPos center = village.center();
        Component name;
        int threat;
        if (f.entry() != null) {
            name = f.entry().name();
            threat = f.entry().threat();
        } else {
            StrongholdKind kind = StrongholdKinds.get(StrongholdKinds.idFor(level.getServer(), f.structure()));
            name = Component.translatable(kind.name(), StrongholdNames.stem(f.pos()));
            threat = kind.threat();
        }
        Component tell = Component.translatable("fealty.rumour.stronghold", name, Compass.roughDistance(center, f.pos()),
                Compass.name(center, f.pos()), Component.translatable("fealty.rumour.threat." + Math.max(1, Math.min(5, threat))));
        Topic.Reveal reveal = f.entry() != null ? null : (player, lvl, teller) -> {
            if (!trusts(player, village)) {
                return;
            }
            Strongholds strongholds = Strongholds.get(lvl.getServer());
            boolean isNew = strongholds.at(lvl.dimension(), f.pos(), 80) == null;
            BlockPos ground = lvl.isLoaded(f.pos()) ? lvl.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, f.pos()) : f.pos();
            Strongholds.Entry entry = strongholds.register(lvl, ground, f.kind(), f.structure(), Scouting.radiusOf(lvl, f.structure()),
                    StrongholdEvent.Discovered.How.RUMOURED);
            if (isNew) {
                player.sendSystemMessage(Component.translatable("fealty.rumour.noted", entry.name()).withStyle(ChatFormatting.GOLD));
            }
        };
        return Optional.of(topic(STRONGHOLD, "stronghold", random, tell, 5, reveal));
    }

    /** Strongholds already known near the village (not razed), and those a search of the land finds (once a day). */
    private static List<Found> strongholdsNear(ServerLevel level, VillageRecord village, boolean search) {
        int range = FealtyConfig.RUMOUR_RANGE.get();
        long day = RepManager.day(level.getServer());
        Strongholds strongholds = Strongholds.get(level.getServer());
        List<Found> found = new ArrayList<>();
        for (Strongholds.Entry entry : strongholds.near(level.dimension(), village.center(), range)) {
            if (!entry.isRazed(day)) {
                found.add(new Found(entry.pos(), entry.structure(), entry.kind(), entry));
            }
        }
        Search cached = SEARCHES.get(village.id());
        if ((cached == null || cached.day() != day) && search) {
            List<Found> looked = new ArrayList<>();
            look(level, village, looked, Scouting.CAMPS, Stronghold.Kind.CAMP);
            look(level, village, looked, FealtyTags.Structures.PILLAGER_OUTPOSTS, Stronghold.Kind.OUTPOST);
            cached = new Search(day, looked);
            SEARCHES.put(village.id(), cached);
        }
        if (cached != null) {
            for (Found f : cached.found()) {
                if (Strongholds.flatDistSqr(f.pos(), village.center()) <= (double) range * range
                        && strongholds.at(level.dimension(), f.pos(), 80) == null) {
                    found.add(f);
                }
            }
        }
        return found;
    }

    private static void look(ServerLevel level, VillageRecord village, List<Found> into, TagKey<Structure> tag, Stronghold.Kind kind) {
        int rings = Math.max(1, Math.min(6, 1 + FealtyConfig.RUMOUR_RANGE.get() / CELL_BLOCKS));
        var nearest = Scouting.nearest(level, tag, village.center(), rings);
        if (nearest != null) {
            into.add(new Found(nearest.getFirst(), nearest.getSecond(), kind, null));
        }
    }

    private static Optional<Topic> bandits(ServerLevel level, VillageRecord village, RandomSource random) {
        Optional<BlockPos> camp = BanditCamps.get(level.getServer()).nearestActive(level, village.center(), FealtyConfig.RUMOUR_RANGE.get());
        return camp.map(pos -> topic(BANDITS, "bandits", random, Component.translatable("fealty.rumour.bandits",
                Compass.roughDistance(village.center(), pos), Compass.name(village.center(), pos)), 3, null));
    }

    private static Optional<Topic> guards(VillageRecord village, RandomSource random) {
        int total = village.garrison().allTotal();
        int alive = village.garrison().allAlive();
        if (total <= 0) {
            return Optional.empty();
        }
        Component tell = alive < total ? Component.translatable("fealty.rumour.guards", total - alive, total)
                : Component.translatable("fealty.rumour.guards_full", total);
        return Optional.of(topic(GUARDS, "guards", random, tell, alive < total ? 3 : 1, null));
    }

    /** The peace a raid won, or else pillagers who might raid. */
    private static Optional<Topic> menaceOrPeace(ServerLevel level, VillageRecord village, RandomSource random) {
        long day = RepManager.day(level.getServer());
        if (village.sites().atPeace(day)) {
            return Optional.of(topic(PEACE, "menace", random, Component.translatable("fealty.rumour.peace",
                    (int) (village.sites().peaceUntil() - day)), 2, null));
        }
        if (!FealtyConfig.PILLAGER_MENACE.get()) {
            return Optional.empty();
        }
        return Menace.menaceOf(level, village, day).map(entry -> topic(MENACE, "menace", random, Component.translatable("fealty.rumour.menace",
                entry.name(), Compass.roughDistance(village.center(), entry.pos()), Compass.name(village.center(), entry.pos())), 3, null));
    }

    /** How the village takes its lord's taxes. */
    private static Optional<Topic> tax(VillageRecord village, RandomSource random) {
        if (village.lord().uuid() == null) {
            return Optional.empty();
        }
        int level = Math.max(0, Math.min(4, village.lord().taxLevel()));
        return Optional.of(topic(TAX, "tax", random, Component.translatable("fealty.rumour.tax." + level, village.lord().name()),
                level >= 3 ? 4 : 2, null));
    }

    /** A villager nearby with something special for sale: an enchanted book, enchanted gear, diamond or netherite. */
    private static Optional<Topic> trade(ServerLevel level, VillageRecord village, Villager teller, RandomSource random) {
        List<Villager> neighbours = new ArrayList<>(level.getEntitiesOfClass(Villager.class, teller.getBoundingBox().inflate(NEIGHBOURHOOD),
                v -> v != teller && v.isAlive() && !v.isBaby() && sells(v) && FactionResolver.factionOf(v).map(village.id()::equals).orElse(false)));
        for (int i = neighbours.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Villager swap = neighbours.get(i);
            neighbours.set(i, neighbours.get(j));
            neighbours.set(j, swap);
        }
        for (Villager seller : neighbours) {
            for (MerchantOffer offer : seller.getOffers()) {
                ItemStack result = offer.getResult();
                if (notable(result)) {
                    Component tell = Component.translatable("fealty.rumour.trade", professionName(seller), Compass.name(teller.blockPosition(),
                            seller.blockPosition()), describe(result));
                    return Optional.of(topic(TRADE, "trade", random, tell, 3, glow(seller.getUUID(), village)));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean sells(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT;
    }

    private static boolean notable(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        if (result.is(Items.ENCHANTED_BOOK) || result.isEnchanted()) {
            return true;
        }
        String path = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
        return path.startsWith("diamond_") || path.startsWith("netherite_");
    }

    /** "Mending II" for an enchanted book, else the item's name. */
    private static Component describe(ItemStack stack) {
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stack.is(Items.ENCHANTED_BOOK) && stored != null && !stored.isEmpty()) {
            var entry = stored.entrySet().iterator().next();
            return Enchantment.getFullname(entry.getKey(), entry.getIntValue());
        }
        return stack.getHoverName();
    }

    /** A villager nearby with a favor to ask this player today. */
    private static Optional<Topic> favor(ServerLevel level, VillageRecord village, Villager teller, ServerPlayer player) {
        Villager best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Villager other : level.getEntitiesOfClass(Villager.class, teller.getBoundingBox().inflate(NEIGHBOURHOOD),
                v -> v != teller && v.isAlive() && !v.isBaby() && FactionResolver.factionOf(v).map(village.id()::equals).orElse(false))) {
            double distance = teller.distanceToSqr(other);
            if (distance < bestDistance && FavorManager.todaysFavor(player, other).isPresent()) {
                bestDistance = distance;
                best = other;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        Component tell = Component.translatable("fealty.rumour.favor", professionName(best), Compass.name(teller.blockPosition(), best.blockPosition()));
        return Optional.of(new Topic(FAVOR, List.of(), tell, true, 4, glow(best.getUUID(), village)));
    }

    /** Who leads the village, or that the player is wanted there: what this used to be all "the news" was. */
    private static Optional<Topic> leader(ServerPlayer player, VillageRecord village) {
        Component fact;
        if (HeatManager.wantedLevel(player, village.id()) > 0) {
            fact = Component.translatable("fealty.news.wanted", player.getDisplayName());
        } else if (village.isBroken()) {
            fact = Component.translatable("fealty.news.broken", village.elder().name());
        } else if (village.lord().uuid() != null) {
            fact = village.lord().isLord(player.getUUID()) ? Component.translatable("fealty.news.your_lordship")
                    : Component.translatable("fealty.news.lord", village.lord().name());
        } else if (village.hasElder() && !village.elder().name().isEmpty()) {
            fact = Component.translatable("fealty.news.elder", village.elder().name());
        } else {
            fact = Component.translatable("fealty.news.quiet");
        }
        return Optional.of(new Topic(LEADER, List.of(), fact, true, 3, null));
    }

    // ---- Helpers ----

    /** Villagers trust a player enough to point things out for them (and for strongholds to land on the War tab). */
    private static boolean trusts(ServerPlayer player, VillageRecord village) {
        return RepManager.getTier(player, village.id()).rank() >= TierManager.trusted().rank();
    }

    /** Make a villager glow for a few seconds, for a trusted player. */
    private static Topic.Reveal glow(UUID target, VillageRecord village) {
        return (player, level, teller) -> {
            if (trusts(player, village) && level.getEntity(target) instanceof Villager villager && villager.isAlive()) {
                villager.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
            }
        };
    }

    private static Component professionName(Villager villager) {
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        return Component.translatableWithFallback("entity." + key.getNamespace() + ".villager." + key.getPath(), key.getPath().replace('_', ' '));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SEARCHES.clear();
    }
}
