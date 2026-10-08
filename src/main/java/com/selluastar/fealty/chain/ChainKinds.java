package com.selluastar.fealty.chain;

import java.util.Optional;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.chain.ChainContext;
import com.selluastar.fealty.api.chain.ChainHandler;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.registry.FealtyRegistries;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/** Fealty's own kinds of quest chain, and looking up the handler for a chain. */
public final class ChainKinds {
    public static final ResourceLocation RARE_VILLAGER = Fealty.id("rare_villager");
    public static final ResourceLocation GUILD = Fealty.id("guild");
    public static final ResourceLocation STORY = Fealty.id("story");

    private ChainKinds() {
    }

    /** The handler for a chain's kind; empty if no mod registers that kind. */
    public static Optional<ChainHandler> handler(QuestChainDefinition def) {
        return Optional.ofNullable(FealtyRegistries.CHAIN_KINDS.get(def.kind()));
    }

    /** The rare villager chain: after the steps, the hidden hamlet and the Keeper; complete when the Writ is forged. */
    public static final class RareVillager implements ChainHandler {
        @Override
        public boolean onStepComplete(ChainContext ctx, int step) {
            ChainManager.rareStepDone((ChainManager.Context) ctx, step);
            return false;
        }

        @Override
        public void onChainComplete(ChainContext ctx) {
            ChainManager.writForged(ctx.player());
        }

        @Override
        public boolean canAdvance(ChainContext ctx, int stage) {
            return false;
        }
    }

    /** The thieves guild: the fence hands out each job and keeps the count itself. */
    public static final class Guild implements ChainHandler {
        @Override
        public boolean start(ChainContext ctx) {
            return false;
        }

        @Override
        public boolean onStepComplete(ChainContext ctx, int step) {
            return false;
        }

        @Override
        public boolean canAdvance(ChainContext ctx, int stage) {
            return false;
        }
    }

    /** Steps in order, then the final reward. */
    public static final class Story implements ChainHandler {
        @Override
        public void onChainComplete(ChainContext ctx) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.chain.story.complete"));
            Feedback.banner(ctx.player(), Component.translatable("fealty.banner.story"), Component.translatable("fealty.banner.story.detail"),
                    0xE0B040, "seal");
            Feedback.sound(ctx.player(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
        }
    }
}
