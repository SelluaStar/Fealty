package com.selluastar.fealty.api.dialogue;

import net.minecraft.network.chat.Component;

/**
 * A reply the player can pick in the dialogue box.
 *
 * @param id      sent back when picked (at most 64 characters)
 * @param icon    a GUI icon name, such as {@code talk}, {@code quest}, {@code ready}, {@code trade}, {@code mail},
 *                {@code coin}, {@code crown}, {@code scroll}, {@code seal} or {@code door}
 * @param enabled a disabled reply is shown greyed out with its {@code hint}
 * @since API 1.3.0
 */
public record DialogueOption(String id, Component label, String icon, boolean enabled, Component hint) {
    public static DialogueOption of(String id, Component label) {
        return of(id, label, "talk");
    }

    public static DialogueOption of(String id, Component label, String icon) {
        return new DialogueOption(id, label, icon, true, Component.empty());
    }

    public static DialogueOption disabled(String id, Component label, String icon, Component hint) {
        return new DialogueOption(id, label, icon, false, hint);
    }
}
