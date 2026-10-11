package com.extremedimensions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ExtremeDimensions implements ModInitializer {
	public static final String MOD_ID = "extremedimensions";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		DataStore.load();
		ModItems.init();

		// Pakketten registreren (server -> client)
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.StatePayload.TYPE, ModPackets.StatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.NoticePayload.TYPE, ModPackets.NoticePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.OfferPayload.TYPE, ModPackets.OfferPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.OpenStatusPayload.TYPE, ModPackets.OpenStatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModPackets.FxPayload.TYPE, ModPackets.FxPayload.CODEC);
		// Pakketten registreren (client -> server)
		PayloadTypeRegistry.serverboundPlay().register(ModPackets.ChoosePayload.TYPE, ModPackets.ChoosePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.ChoosePayload.TYPE,
				(payload, context) -> ServerLogic.onChoose(context.player(), payload.id()));

		ServerLogic.register();
		LOGGER.info("Extreme Dimensions geladen: Nether Charge en End Charge actief.");
	}
}
