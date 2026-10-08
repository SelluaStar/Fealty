package com.selluastar.fealty.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.selluastar.fealty.api.dialogue.DialogueOption;
import com.selluastar.fealty.api.dialogue.DialogueReply;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;

/**
 * Fealty is putting together what an NPC says and the replies the player has: a villager's, a quest giver's (the elder,
 * the Keeper, the fence, other mods' givers) or a guard's usual dialogue. Add lines to what the NPC says, and replies
 * of your own. A reply's id must start with your mod id and a colon ({@code yourmod:wave}); when the player picks it,
 * Fealty calls the handler you gave it. Fired on {@code NeoForge.EVENT_BUS} every time the box is drawn; since
 * API 1.3.0.
 */
public class DialogueBuildEvent extends Event {
    /** Namespaces Fealty uses for its own reply ids. */
    private static final Set<String> RESERVED = Set.of("q", "c", "t", "action");

    private final ServerPlayer player;
    private final Entity npc;
    private final boolean answer;
    private final List<Component> lines = new ArrayList<>();
    private final List<Contribution> options = new ArrayList<>();

    public DialogueBuildEvent(ServerPlayer player, Entity npc, boolean answer) {
        this.player = player;
        this.npc = npc;
        this.answer = answer;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Entity getNpc() {
        return npc;
    }

    /** Whether the NPC is answering something the player picked, rather than greeting them. */
    public boolean isAnswer() {
        return answer;
    }

    /** Add a line to what the NPC says (after their own words). */
    public void addLine(Component line) {
        lines.add(line);
    }

    /** Add a reply with the {@code talk} icon. */
    public void addOption(String id, Component label, Handler handler) {
        addOption(DialogueOption.of(id, label), handler);
    }

    /**
     * Add a reply.
     *
     * @throws IllegalArgumentException if the id is not {@code <modid>:<name>} or is longer than 64 characters
     */
    public void addOption(DialogueOption option, Handler handler) {
        String id = option.id();
        int colon = id.indexOf(':');
        if (colon <= 0 || id.length() > 64 || ResourceLocation.tryParse(id) == null || RESERVED.contains(id.substring(0, colon))) {
            throw new IllegalArgumentException("Dialogue reply ids look like <modid>:<name> (64 characters at most): " + id);
        }
        options.removeIf(c -> c.option().id().equals(id));
        options.add(new Contribution(option, handler));
    }

    public List<Component> getLines() {
        return List.copyOf(lines);
    }

    public List<Contribution> getOptions() {
        return List.copyOf(options);
    }

    public record Contribution(DialogueOption option, Handler handler) {
    }

    /** Called on the server when the player picks the reply. */
    @FunctionalInterface
    public interface Handler {
        DialogueReply choose(ServerPlayer player, Entity npc, String option);
    }
}
