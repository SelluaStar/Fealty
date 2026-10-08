package com.selluastar.fealty.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.Stronghold;
import com.selluastar.fealty.api.event.StrongholdEvent;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModBlockEntities;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.war.Campaigns;
import com.selluastar.fealty.war.Captives;
import com.selluastar.fealty.war.StrongholdKind;
import com.selluastar.fealty.war.StrongholdKinds;
import com.selluastar.fealty.war.StrongholdTrait;
import com.selluastar.fealty.war.Strongholds;
import com.selluastar.fealty.war.WarDefenders;
import com.selluastar.fealty.world.PillagerCampPiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The heart of a pillager camp. A generated War Banner registers its camp as a stronghold, mans it with pillagers,
 * vindicators and a banner-bearing captain, and puts captives in its cages. While the camp is razed the banner hangs
 * in tatters with the razing village's colours flying beside it; when the raze runs out the pillagers return.
 * Nothing spawns until every chunk around the camp has its entities loaded, and never mid-battle.
 */
public class WarBannerBlockEntity extends BlockEntity {
    /** Garrison members stay within this many blocks of the banner. */
    public static final int MEMBER_RANGE = 32;
    private boolean natural;
    /** The stronghold this banner belongs to, and where its captives stand (from the banner). */
    private ResourceLocation structure = Strongholds.PILLAGER_CAMP;
    private List<BlockPos> cages = List.of(PillagerCampPiece.CAGES);
    private boolean populated;
    private boolean captivesPlaced;
    @Nullable
    private BlockPos victoryBanner;
    private long lastCheck;

    public WarBannerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WAR_BANNER.get(), pos, state);
    }

    /** Set when the stronghold is generated: only generated banners keep one. */
    public void setNatural(ResourceLocation structure, BlockPos... cages) {
        this.natural = true;
        this.structure = structure;
        this.cages = List.of(cages);
        setChanged();
    }

    public boolean isNatural() {
        return natural;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WarBannerBlockEntity banner) {
        if (!(level instanceof ServerLevel server) || !banner.natural) {
            return;
        }
        long now = level.getGameTime();
        if (now - banner.lastCheck < (banner.populated ? 600 : 40)) {
            return;
        }
        banner.lastCheck = now;
        Strongholds strongholds = Strongholds.get(server.getServer());
        Strongholds.Entry entry = strongholds.register(server, pos, Stronghold.Kind.CAMP, banner.structure,
                StrongholdKinds.get(StrongholdKinds.idFor(server.getServer(), banner.structure)).radius(), StrongholdEvent.Discovered.How.GENERATED);
        long day = RepManager.day(server.getServer());
        boolean razed = entry.isRazed(day);
        if (state.getValue(WarBannerBlock.RAZED) != razed) {
            server.setBlock(pos, state.setValue(WarBannerBlock.RAZED, razed), Block.UPDATE_ALL);
            if (!razed) {
                banner.lowerVictoryBanner(server);
                banner.captivesPlaced = false;
            }
            banner.setChanged();
        }
        if (razed || !entitiesLoaded(server, pos) || Campaigns.fightingAt(server, entry)) {
            return;
        }
        if (!banner.populated) {
            banner.populated = true;
            man(server, pos, entry);
        } else if (server.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 64, false) == null) {
            man(server, pos, entry);
        }
        if (!banner.captivesPlaced) {
            banner.captivesPlaced = true;
            int placed = Captives.fillCages(server, pos, banner.cages, entry.strongholdKind().captives(pos));
            entry.setCaptives(placed);
            strongholds.setDirty();
        }
        banner.setChanged();
    }

    /** Whether every chunk garrison members could be in has its entities loaded. */
    private static boolean entitiesLoaded(ServerLevel level, BlockPos pos) {
        int range = MEMBER_RANGE + 8;
        for (int cx = (pos.getX() - range) >> 4; cx <= (pos.getX() + range) >> 4; cx++) {
            for (int cz = (pos.getZ() - range) >> 4; cz <= (pos.getZ() + range) >> 4; cz++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Fill the garrison up to its kind's strength (the same every time), with its captain and its trait's extras. */
    private static void man(ServerLevel level, BlockPos pos, Strongholds.Entry entry) {
        StrongholdKind kind = entry.strongholdKind();
        StrongholdTrait trait = entry.trait();
        List<Mob> members = garrison(level, pos);
        RandomSource random = level.getRandom();
        int reach = Math.max(6, Math.min(16, kind.radius() - 4));
        for (Map.Entry<EntityType<?>, Integer> squad : kind.plan(trait, pos).entrySet()) {
            long have = members.stream().filter(m -> m.getType() == squad.getKey() && !isCaptain(m)).count();
            for (long i = have; i < squad.getValue(); i++) {
                spawn(level, pos, squad.getKey(), random, false, reach, kind.radius(), trait);
            }
        }
        if (kind.captain().isPresent() && members.stream().noneMatch(WarBannerBlockEntity::isCaptain)) {
            spawn(level, pos, kind.captain().get(), random, true, reach, kind.radius(), trait);
        }
    }

    /** The camp's living garrison. */
    public static List<Mob> garrison(ServerLevel level, BlockPos pos) {
        long camp = pos.asLong();
        return level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(MEMBER_RANGE + 16),
                m -> m.isAlive() && m.hasData(ModAttachments.CAMP) && m.getData(ModAttachments.CAMP) == camp);
    }

    private static boolean isCaptain(Mob mob) {
        return mob instanceof PatrollingMonster patrolling && patrolling.isPatrolLeader();
    }

    private static void spawn(ServerLevel level, BlockPos banner, EntityType<?> type, RandomSource random, boolean captain, int reach,
                              int restrict, StrongholdTrait trait) {
        if (!(type.create(level) instanceof Mob mob)) {
            return;
        }
        Optional<BlockPos> spot = BanditStandardBlockEntity.findSpot(level, banner, mob, random, 3, reach, false);
        if (spot.isEmpty()) {
            mob.discard();
            return;
        }
        BlockPos pos = spot.get();
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360F, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        mob.setPersistenceRequired();
        mob.setData(ModAttachments.CAMP, banner.asLong());
        if (mob instanceof PatrollingMonster patrolling) {
            patrolling.setPatrolLeader(captain);
            if (captain) {
                HolderLookup.RegistryLookup<net.minecraft.world.level.block.entity.BannerPattern> patterns =
                        level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
                patrolling.setItemSlot(EquipmentSlot.HEAD, Raid.getLeaderBannerInstance(patterns));
                patrolling.setDropChance(EquipmentSlot.HEAD, 2.0F);
            }
        }
        if (mob instanceof PathfinderMob pathfinder) {
            pathfinder.restrictTo(banner, restrict);
        }
        if (trait == StrongholdTrait.VETERANS) {
            equipVeteran(level, mob, random);
        }
        WarDefenders.enlist(mob);
        level.addFreshEntity(mob);
    }

    /**
     * An outpost has no War Banner to muster it, so its trait shows when a battle starts there: an evoker or a ravager
     * joins its defenders, or they turn out in veterans' gear.
     *
     * @return defenders who joined
     */
    public static List<LivingEntity> reinforce(ServerLevel level, Strongholds.Entry target, List<LivingEntity> defenders) {
        RandomSource random = level.getRandom();
        StrongholdTrait trait = target.trait();
        if (trait == StrongholdTrait.VETERANS) {
            for (LivingEntity defender : defenders) {
                if (defender instanceof Mob mob) {
                    equipVeteran(level, mob, random);
                }
            }
            return List.of();
        }
        EntityType<?> extra = trait == StrongholdTrait.EVOKER ? EntityType.EVOKER : trait == StrongholdTrait.BEASTS ? EntityType.RAVAGER : null;
        if (extra == null || !(extra.create(level) instanceof Mob mob)) {
            return List.of();
        }
        Optional<BlockPos> spot = BanditStandardBlockEntity.findSpot(level, target.pos(), mob, random, 3, Math.max(6, target.radius() - 4), false);
        if (spot.isEmpty()) {
            mob.discard();
            return List.of();
        }
        BlockPos pos = spot.get();
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360F, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        mob.setPersistenceRequired();
        if (mob instanceof PathfinderMob pathfinder) {
            pathfinder.restrictTo(target.pos(), target.radius());
        }
        level.addFreshEntity(mob);
        return List.of(mob);
    }

    /** Veterans' gear: iron helm and mail, and an enchanted weapon. Beasts go as they are. */
    public static void equipVeteran(ServerLevel level, Mob mob, RandomSource random) {
        if (mob.getType() == EntityType.RAVAGER) {
            return;
        }
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        mob.setDropChance(EquipmentSlot.HEAD, 0.05F);
        mob.setDropChance(EquipmentSlot.CHEST, 0.05F);
        ItemStack weapon = mob.getMainHandItem();
        if (!weapon.isEmpty()) {
            HolderLookup.RegistryLookup<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            if (weapon.is(Items.CROSSBOW)) {
                weapon.enchant(enchantments.getOrThrow(random.nextBoolean() ? Enchantments.QUICK_CHARGE : Enchantments.PIERCING), 2);
            } else {
                weapon.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 2);
            }
        }
    }

    /** Fly the razing village's colours beside the tattered banner. */
    public void raiseVictoryBanner(ServerLevel level, BlockState bannerState) {
        BlockPos spot = worldPosition.east();
        if (!level.getBlockState(spot).canBeReplaced()) {
            spot = worldPosition.west();
        }
        if (level.getBlockState(spot).canBeReplaced()) {
            level.setBlock(spot, bannerState, Block.UPDATE_ALL);
            victoryBanner = spot.immutable();
            setChanged();
        }
    }

    private void lowerVictoryBanner(ServerLevel level) {
        if (victoryBanner != null && level.getBlockState(victoryBanner).is(net.minecraft.tags.BlockTags.BANNERS)) {
            level.setBlock(victoryBanner, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        victoryBanner = null;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        natural = tag.getBoolean("Natural");
        populated = tag.getBoolean("Populated");
        captivesPlaced = tag.getBoolean("CaptivesPlaced");
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Structure"));
        structure = id != null && tag.contains("Structure") ? id : Strongholds.PILLAGER_CAMP;
        if (tag.contains("Cages")) {
            List<BlockPos> loaded = new ArrayList<>();
            for (long packed : tag.getLongArray("Cages")) {
                loaded.add(BlockPos.of(packed));
            }
            cages = List.copyOf(loaded);
        }
        victoryBanner = NbtUtils.readBlockPos(tag, "VictoryBanner").orElse(null);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Natural", natural);
        tag.putBoolean("Populated", populated);
        tag.putBoolean("CaptivesPlaced", captivesPlaced);
        tag.putString("Structure", structure.toString());
        tag.putLongArray("Cages", cages.stream().mapToLong(BlockPos::asLong).toArray());
        if (victoryBanner != null) {
            tag.put("VictoryBanner", NbtUtils.writeBlockPos(victoryBanner));
        }
    }

    @Nullable
    public static WarBannerBlockEntity at(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WarBannerBlockEntity banner ? banner : null;
    }
}
