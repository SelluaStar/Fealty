package com.selluastar.fealty.registry;

import net.neoforged.bus.api.IEventBus;

/** Registers every deferred register and mod-bus registry hook. */
public final class ModRegistries {
    private ModRegistries() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FealtyRegistries::onNewRegistry);
        ModRepSources.SOURCES.register(modBus);
        ModQuestTypes.TYPES.register(modBus);
        ModChainKinds.KINDS.register(modBus);
        ModDialogueScripts.CONDITIONS.register(modBus);
        ModDialogueScripts.EFFECTS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModCriteria.TRIGGERS.register(modBus);
        ModLootConditions.CONDITIONS.register(modBus);
        ModDataComponents.COMPONENTS.register(modBus);
        ModArmorMaterials.MATERIALS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModStructures.STRUCTURE_TYPES.register(modBus);
        ModStructures.PIECES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
    }
}
