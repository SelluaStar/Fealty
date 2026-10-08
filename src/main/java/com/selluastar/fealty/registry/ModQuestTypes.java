package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.quest.type.ClearCampObjective;
import com.selluastar.fealty.quest.type.CourierObjective;
import com.selluastar.fealty.quest.type.DefendRaidObjective;
import com.selluastar.fealty.quest.type.ExploreObjective;
import com.selluastar.fealty.quest.type.FetchObjective;
import com.selluastar.fealty.quest.type.FreeCaptivesObjective;
import com.selluastar.fealty.quest.type.HuntObjective;
import com.selluastar.fealty.quest.type.KillObjective;
import com.selluastar.fealty.quest.type.PickpocketObjective;
import com.selluastar.fealty.quest.type.RaidStrongholdObjective;
import com.selluastar.fealty.quest.type.RebuildObjective;
import com.selluastar.fealty.quest.type.RestockObjective;
import com.selluastar.fealty.quest.type.RestoreElderObjective;
import com.selluastar.fealty.quest.type.RetrieveObjective;
import com.selluastar.fealty.quest.type.StealObjective;
import com.selluastar.fealty.quest.type.TalkToObjective;
import com.selluastar.fealty.quest.type.VaultRaidObjective;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModQuestTypes {
    public static final DeferredRegister<QuestType<?>> TYPES = DeferredRegister.create(FealtyRegistries.QUEST_TYPE_KEY, Fealty.MOD_ID);

    /** Bring items to the giver. */
    public static final DeferredHolder<QuestType<?>, QuestType<FetchObjective>> FETCH =
            TYPES.register("fetch", () -> new QuestType<>(FetchObjective.CODEC));
    /** Hold the village through a raid (starts a vanilla raid at the village). */
    public static final DeferredHolder<QuestType<?>, QuestType<DefendRaidObjective>> DEFEND_RAID =
            TYPES.register("defend_raid", () -> new QuestType<>(DefendRaidObjective.CODEC));
    /** A lord's raid on a pillager stronghold (started from the Village Hall). */
    public static final DeferredHolder<QuestType<?>, QuestType<RaidStrongholdObjective>> RAID_STRONGHOLD =
            TYPES.register("raid_stronghold", () -> new QuestType<>(RaidStrongholdObjective.CODEC));
    /** Free villagers held captive in a pillager camp. */
    public static final DeferredHolder<QuestType<?>, QuestType<FreeCaptivesObjective>> FREE_CAPTIVES =
            TYPES.register("free_captives", () -> new QuestType<>(FreeCaptivesObjective.CODEC));
    /** Clear the nearest bandit camp by defeating its captain. */
    public static final DeferredHolder<QuestType<?>, QuestType<ClearCampObjective>> CLEAR_CAMP =
            TYPES.register("clear_camp", () -> new QuestType<>(ClearCampObjective.CODEC));
    /** Restock the village with workstations. */
    public static final DeferredHolder<QuestType<?>, QuestType<RestockObjective>> RESTOCK =
            TYPES.register("restock", () -> new QuestType<>(RestockObjective.CODEC));
    /** Rebuild a damaged building (blocks turned to rubble). */
    public static final DeferredHolder<QuestType<?>, QuestType<RebuildObjective>> REBUILD =
            TYPES.register("rebuild", () -> new QuestType<>(RebuildObjective.CODEC));
    /** Carry a sealed letter to another village's elder. */
    public static final DeferredHolder<QuestType<?>, QuestType<CourierObjective>> COURIER =
            TYPES.register("courier", () -> new QuestType<>(CourierObjective.CODEC));
    /** Kill a named monster. */
    public static final DeferredHolder<QuestType<?>, QuestType<HuntObjective>> HUNT =
            TYPES.register("hunt", () -> new QuestType<>(HuntObjective.CODEC));
    /** Restore a Broken village's elder. */
    public static final DeferredHolder<QuestType<?>, QuestType<RestoreElderObjective>> RESTORE_ELDER =
            TYPES.register("restore_elder", () -> new QuestType<>(RestoreElderObjective.CODEC));
    /** Thieves guild: steal items from village containers. */
    public static final DeferredHolder<QuestType<?>, QuestType<StealObjective>> STEAL =
            TYPES.register("steal", () -> new QuestType<>(StealObjective.CODEC));
    /** Thieves guild: pick villagers' pockets unseen. */
    public static final DeferredHolder<QuestType<?>, QuestType<PickpocketObjective>> PICKPOCKET =
            TYPES.register("pickpocket", () -> new QuestType<>(PickpocketObjective.CODEC));
    /** Thieves guild: raid village coffers unseen. */
    public static final DeferredHolder<QuestType<?>, QuestType<VaultRaidObjective>> VAULT_RAID =
            TYPES.register("vault_raid", () -> new QuestType<>(VaultRaidObjective.CODEC));

    /** Defeat a number of creatures (by id or tag), optionally near the village or at night. */
    public static final DeferredHolder<QuestType<?>, QuestType<KillObjective>> KILL =
            TYPES.register("kill", () -> new QuestType<>(KillObjective.CODEC));
    /** Find a structure and report back. */
    public static final DeferredHolder<QuestType<?>, QuestType<ExploreObjective>> EXPLORE =
            TYPES.register("explore", () -> new QuestType<>(ExploreObjective.CODEC));
    /** Carry a message to another villager. */
    public static final DeferredHolder<QuestType<?>, QuestType<TalkToObjective>> TALK_TO =
            TYPES.register("talk_to", () -> new QuestType<>(TalkToObjective.CODEC));
    /** Recover a lost item from a cache, cart or bandit stash out in the wilds. */
    public static final DeferredHolder<QuestType<?>, QuestType<RetrieveObjective>> RETRIEVE =
            TYPES.register("retrieve", () -> new QuestType<>(RetrieveObjective.CODEC));

    private ModQuestTypes() {
    }
}
