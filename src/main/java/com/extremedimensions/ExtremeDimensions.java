package com.extremedimensions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.extremedimensions.config.Config;
import com.extremedimensions.config.SettingsService;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.lore.WorldLoot;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItemGroups;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.registry.ModParticles;
import com.extremedimensions.registry.ModSounds;
import com.extremedimensions.server.ServerEvents;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Common entrypoint: configuration, registries, networking and server events. */
public class ExtremeDimensions implements ModInitializer {
	public static final String MOD_ID = "extremedimensions";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Config.load();

		ModBlocks.init();
		ModItems.init();
		ModSounds.init();
		ModParticles.init();
		WorldLoot.register();
		ModEntities.init();
		ModItemGroups.init();

		PayloadTypeRegistry.clientboundPlay().register(ModPackets.StatePayload.TYPE, ModPackets.StatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.OfferPayload.TYPE, ModPackets.OfferPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.OpenStatusPayload.TYPE, ModPackets.OpenStatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.FxPayload.TYPE, ModPackets.FxPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.OpenJournalPayload.TYPE, ModPackets.OpenJournalPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ModPackets.ChoosePayload.TYPE, ModPackets.ChoosePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.ChoosePayload.TYPE,
				(payload, context) -> PenaltyService.choose(context.player(), payload.id()));

		PayloadTypeRegistry.clientboundPlay().register(ModPackets.CursesPayload.TYPE, ModPackets.CursesPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ModPackets.RequestCursesPayload.TYPE, ModPackets.RequestCursesPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.RequestCursesPayload.TYPE,
				(payload, context) -> Sync.curses(context.player(), DataStore.get(context.player().getUUID()), payload.open()));
		PayloadTypeRegistry.serverboundPlay().register(ModPackets.AtonePayload.TYPE, ModPackets.AtonePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.AtonePayload.TYPE,
				(payload, context) -> PenaltyService.setAtoning(context.player(), payload.id()));

		PayloadTypeRegistry.serverboundPlay().register(ModPackets.RequestJournalPayload.TYPE, ModPackets.RequestJournalPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.RequestJournalPayload.TYPE,
				(payload, context) -> Journal.open(context.player(), DataStore.get(context.player().getUUID())));

		PayloadTypeRegistry.clientboundPlay().register(ModPackets.SettingsPayload.TYPE, ModPackets.SettingsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ModPackets.RequestSettingsPayload.TYPE, ModPackets.RequestSettingsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.RequestSettingsPayload.TYPE,
				(payload, context) -> SettingsService.send(context.player()));
		PayloadTypeRegistry.serverboundPlay().register(ModPackets.ChangeSettingsPayload.TYPE, ModPackets.ChangeSettingsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.ChangeSettingsPayload.TYPE,
				(payload, context) -> SettingsService.change(context.player(), payload.changes()));

		PayloadTypeRegistry.serverboundPlay().register(ModPackets.UnbindRelicPayload.TYPE, ModPackets.UnbindRelicPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.UnbindRelicPayload.TYPE,
				(payload, context) -> com.extremedimensions.relic.RelicService.unbind(context.player()));

		PayloadTypeRegistry.serverboundPlay().register(ModPackets.SwearPactPayload.TYPE, ModPackets.SwearPactPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.SwearPactPayload.TYPE, (payload, context) -> {
			java.util.Map<String, Integer> wanted = new java.util.HashMap<>();
			for (String part : payload.pact().split(",")) {
				String[] kv = part.split(":");
				if (kv.length == 2) {
					try {
						wanted.put(kv[0], Integer.parseInt(kv[1]));
					} catch (NumberFormatException e) {
						// skip a malformed entry
					}
				}
			}
			String refused = com.extremedimensions.pact.PactService.swear(context.player(), wanted);
			if (refused != null) {
				context.player().sendSystemMessage(net.minecraft.network.chat.Component.translatable(refused));
			}
		});

		PayloadTypeRegistry.clientboundPlay().register(ModPackets.BossMusicPayload.TYPE, ModPackets.BossMusicPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.BossFxPayload.TYPE, ModPackets.BossFxPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.AttackFxPayload.TYPE, ModPackets.AttackFxPayload.CODEC);
		// Co-op: an ally's curses to carry one of, the offer to carry it, and anchors opened to allies
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.BurdenPayload.TYPE, ModPackets.BurdenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ModPackets.CarryPayload.TYPE, ModPackets.CarryPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.CarryPayload.TYPE, (payload, context) ->
				com.extremedimensions.coop.BurdenService.offer(context.player(), payload.friend(), payload.curse()));
		PayloadTypeRegistry.serverboundPlay().register(ModPackets.ShareAnchorsPayload.TYPE, ModPackets.ShareAnchorsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.ShareAnchorsPayload.TYPE, (payload, context) ->
				com.extremedimensions.coop.Allies.share(context.player(), payload.on()));
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.TrialsPayload.TYPE, ModPackets.TrialsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ModPackets.RequestTrialsPayload.TYPE, ModPackets.RequestTrialsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.RequestTrialsPayload.TYPE, (payload, context) ->
				ServerPlayNetworking.send(context.player(), new ModPackets.TrialsPayload(com.extremedimensions.trial.TrialService.data(context.player()))));
		PayloadTypeRegistry.serverboundPlay().register(ModPackets.ArmTrialPayload.TYPE, ModPackets.ArmTrialPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.ArmTrialPayload.TYPE, (payload, context) -> {
			String refused = com.extremedimensions.trial.TrialService.arm(context.player(), payload.arm());
			if (refused != null) {
				context.player().sendSystemMessage(net.minecraft.network.chat.Component.translatable(refused));
			}
			ServerPlayNetworking.send(context.player(), new ModPackets.TrialsPayload(com.extremedimensions.trial.TrialService.data(context.player())));
		});

		ServerEvents.register();
		LOGGER.info("Extreme Dimensions initialised");
	}
}
