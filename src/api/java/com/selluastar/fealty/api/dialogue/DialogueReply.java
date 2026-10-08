package com.selluastar.fealty.api.dialogue;

import net.minecraft.network.chat.Component;

/**
 * What happens after the player picks a reply another mod added to the dialogue box.
 *
 * @since API 1.3.0
 */
public record DialogueReply(Kind kind, Component text) {
    public enum Kind {
        /** The NPC answers with {@code text}; the dialogue box stays open. */
        SAY,
        /** The dialogue box is drawn again with the NPC's usual greeting. */
        REFRESH,
        /** The dialogue box closes. */
        CLOSE,
        /** The conversation ends but the screen is left alone (you opened one of your own). */
        END
    }

    public static DialogueReply say(Component text) {
        return new DialogueReply(Kind.SAY, text);
    }

    public static DialogueReply refresh() {
        return new DialogueReply(Kind.REFRESH, Component.empty());
    }

    public static DialogueReply close() {
        return new DialogueReply(Kind.CLOSE, Component.empty());
    }

    public static DialogueReply end() {
        return new DialogueReply(Kind.END, Component.empty());
    }
}
