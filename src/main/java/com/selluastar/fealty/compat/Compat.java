package com.selluastar.fealty.compat;

import com.selluastar.fealty.Fealty;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/**
 * Loads optional integrations. Each lives in its own source folder and is only compiled when enabled in
 * gradle.properties, so it is looked up by name and skipped if missing.
 */
public final class Compat {
    private Compat() {
    }

    public static void init(IEventBus modBus) {
        load("ftbquests", "com.selluastar.fealty.compat.ftbquests.FtbQuestsCompat", modBus);
        load("kubejs", "com.selluastar.fealty.compat.kubejs.KubeJsCompat", modBus);
    }

    private static void load(String modId, String className, IEventBus modBus) {
        if (!ModList.get().isLoaded(modId)) {
            return;
        }
        try {
            Class<?> type = Class.forName(className);
            type.getMethod("init", IEventBus.class).invoke(null, modBus);
            Fealty.LOGGER.info("Fealty: {} integration enabled", modId);
        } catch (ClassNotFoundException e) {
            Fealty.LOGGER.info("Fealty: built without {} integration", modId);
        } catch (ReflectiveOperationException | LinkageError e) {
            Fealty.LOGGER.error("Fealty: failed to enable {} integration", modId, e);
        }
    }
}
