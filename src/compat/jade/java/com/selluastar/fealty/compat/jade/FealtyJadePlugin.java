package com.selluastar.fealty.compat.jade;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.client.ClientRepCache;
import com.selluastar.fealty.entity.QuestGiverEntity;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Shows a villager's or guard's faction and your standing with it in the Jade tooltip. */
@WailaPlugin
public class FealtyJadePlugin implements IWailaPlugin {
    public static final ResourceLocation STANDING = Fealty.id("standing");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(StandingProvider.INSTANCE, LivingEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(StandingProvider.INSTANCE, LivingEntity.class);
    }

    public enum StandingProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("FealtyFaction")) {
                return;
            }
            Component name = Component.Serializer.fromJson(data.getString("FealtyFaction"), accessor.getLevel().registryAccess());
            int rep = data.getInt("FealtyRep");
            RepTier tier = ClientRepCache.tierFor(rep);
            tooltip.add(Component.translatable("fealty.jade.standing", name == null ? Component.empty() : name,
                    tier.displayName().copy().withColor(tier.color()), rep));
            if (data.getBoolean("FealtyLord")) {
                tooltip.add(Component.translatable("fealty.jade.your_village"));
            }
        }

        @Override
        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            Entity entity = accessor.getEntity();
            if (!(accessor.getPlayer() instanceof ServerPlayer player)
                    || !(FactionResolver.canWitness(entity) || entity instanceof QuestGiverEntity)) {
                return;
            }
            FactionResolver.factionOf(entity).ifPresent(faction -> {
                Component name = Factions.displayName(player.server, faction);
                data.putString("FealtyFaction", Component.Serializer.toJson(name, player.registryAccess()));
                data.putInt("FealtyRep", RepManager.getRep(player, faction));
                if (Factions.isVillage(faction)) {
                    FealtyWorldData.get(player.server).village(faction)
                            .filter(v -> v.lord().isLord(player.getUUID()))
                            .ifPresent(v -> data.putBoolean("FealtyLord", true));
                }
            });
        }

        @Override
        public ResourceLocation getUid() {
            return STANDING;
        }
    }
}
