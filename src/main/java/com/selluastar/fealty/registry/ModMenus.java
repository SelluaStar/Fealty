package com.selluastar.fealty.registry;

import java.util.function.Supplier;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.mail.MailWriteMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Fealty.MOD_ID);

    /** Writing a letter at a mailbox. */
    public static final Supplier<MenuType<MailWriteMenu>> MAIL_WRITE = MENUS.register("mail_write",
            () -> IMenuTypeExtension.create(MailWriteMenu::new));

    private ModMenus() {
    }
}
