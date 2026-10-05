package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.item.LedgerItem;
import com.selluastar.fealty.item.LockpickItem;
import com.selluastar.fealty.item.LordsHornItem;
import com.selluastar.fealty.item.RogueArmorItem;
import com.selluastar.fealty.item.SealedLetterItem;
import com.selluastar.fealty.item.SmokeBombItem;
import com.selluastar.fealty.item.TooltipItem;
import com.selluastar.fealty.item.TyrantCrownItem;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Fealty.MOD_ID);

    // Reputation
    public static final DeferredItem<LedgerItem> LEDGER = ITEMS.register("ledger",
            () -> new LedgerItem(new Item.Properties().stacksTo(1)));
    // Quests
    public static final DeferredItem<SealedLetterItem> SEALED_LETTER = ITEMS.register("sealed_letter",
            () -> new SealedLetterItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TooltipItem> ELDERS_MANTLE = ITEMS.register("elders_mantle",
            () -> new TooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), false));
    // Rare villager chain: Item A, Item B and the writ they form
    public static final DeferredItem<TooltipItem> SIGNET = ITEMS.register("signet_of_the_old_crown",
            () -> new TooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant(), true));
    public static final DeferredItem<TooltipItem> CHARTER = ITEMS.register("keepers_charter",
            () -> new TooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant(), true));
    public static final DeferredItem<TooltipItem> ROYAL_WRIT = ITEMS.register("royal_writ",
            () -> new TooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), true));
    // Lordship
    public static final DeferredItem<LordsHornItem> LORDS_HORN = ITEMS.register("lords_horn",
            () -> new LordsHornItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    // Outlaw path
    public static final DeferredItem<RogueArmorItem> ROGUE_HOOD = ITEMS.register("rogue_hood",
            () -> new RogueArmorItem(ArmorItem.Type.HELMET));
    public static final DeferredItem<RogueArmorItem> ROGUE_CLOAK = ITEMS.register("rogue_cloak",
            () -> new RogueArmorItem(ArmorItem.Type.CHESTPLATE));
    public static final DeferredItem<RogueArmorItem> ROGUE_LEGGINGS = ITEMS.register("rogue_leggings",
            () -> new RogueArmorItem(ArmorItem.Type.LEGGINGS));
    public static final DeferredItem<RogueArmorItem> ROGUE_BOOTS = ITEMS.register("rogue_boots",
            () -> new RogueArmorItem(ArmorItem.Type.BOOTS));
    public static final DeferredItem<LockpickItem> LOCKPICK = ITEMS.register("lockpick",
            () -> new LockpickItem(new Item.Properties().durability(24)));
    public static final DeferredItem<SmokeBombItem> SMOKE_BOMB = ITEMS.register("smoke_bomb",
            () -> new SmokeBombItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<TooltipItem> BOUNTY_NOTICE = ITEMS.register("bounty_notice",
            () -> new TooltipItem(new Item.Properties().stacksTo(16), false));
    public static final DeferredItem<TyrantCrownItem> TYRANT_CROWN = ITEMS.register("tyrant_crown",
            () -> new TyrantCrownItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()
                    .durability(ArmorItem.Type.HELMET.getDurability(40))));

    // Blocks
    public static final DeferredItem<BlockItem> VILLAGE_COFFER = ITEMS.registerSimpleBlockItem(ModBlocks.VILLAGE_COFFER);
    public static final DeferredItem<BlockItem> RUBBLE = ITEMS.registerSimpleBlockItem(ModBlocks.RUBBLE);
    public static final DeferredItem<BlockItem> BANDIT_STANDARD = ITEMS.registerSimpleBlockItem(ModBlocks.BANDIT_STANDARD);

    // Spawn eggs
    public static final DeferredItem<DeferredSpawnEggItem> ELDER_SPAWN_EGG = ITEMS.register("village_elder_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VILLAGE_ELDER, 0x5B3A7A, 0xE0C068, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> KEEPER_SPAWN_EGG = ITEMS.register("keeper_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.KEEPER, 0x1F4D3A, 0xB8D4C0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BANDIT_SPAWN_EGG = ITEMS.register("bandit_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BANDIT, 0x5A4632, 0x2B2B2B, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BANDIT_CAPTAIN_SPAWN_EGG = ITEMS.register("bandit_captain_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BANDIT_CAPTAIN, 0x5A4632, 0x9E2A2A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BLACK_MARKETEER_SPAWN_EGG = ITEMS.register("black_marketeer_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BLACK_MARKETEER, 0x2B2B2B, 0x7A5C2E, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GUILD_FENCE_SPAWN_EGG = ITEMS.register("guild_fence_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GUILD_FENCE, 0x1E1E2A, 0x6B6B80, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BOUNTY_HUNTER_SPAWN_EGG = ITEMS.register("bounty_hunter_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BOUNTY_HUNTER, 0x3C3226, 0xC8A050, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> TYRANT_LORD_SPAWN_EGG = ITEMS.register("tyrant_lord_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.TYRANT_LORD, 0x2A1A1A, 0xD4AF37, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VILLAGE_GUARD_SPAWN_EGG = ITEMS.register("village_guard_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VILLAGE_GUARD, 0x8A8F96, 0x1565C0, new Item.Properties()));

    private ModItems() {
    }
}
