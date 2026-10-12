package com.extremedimensions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.extremedimensions.config.Config;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItemGroups;
import com.extremedimensions.registry.ModItems;
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

		ServerEvents.register();
		LOGGER.info("Extreme Dimensions initialised");
	}
}
