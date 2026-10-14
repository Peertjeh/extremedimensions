package com.extremedimensions.client;

import java.util.Arrays;

import com.extremedimensions.Config;
import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.ModPackets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

public class ExtremeDimensionsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Titelscherm: The Tether-art als achtergrond
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof TitleScreen) {
				ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, tickProgress) ->
						TitleArt.draw(graphics, s.width, s.height, mouseX, mouseY));
			}
		});

		// HUD
		HudElementRegistry.addLast(ExtremeDimensions.id("charge_hud"), new ChargeHud());

		// Status van de server
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.StatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					ClientState.nether = payload.nether();
					ClientState.end = payload.end();
					ClientState.dimCode = payload.dimCode();
					ClientState.drainX100 = payload.drainX100();
					ClientState.expeditions = payload.expeditions();
					ClientState.collapses = payload.collapses();
					ClientState.longestRun = payload.longestRun();
					ClientState.runSecs = payload.runSecs();
					ClientState.penalties = payload.penalties();
					ClientState.syncMs = System.currentTimeMillis();
				}));

		// Animaties van de server
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientState.onFx(payload.kind(), payload.value())));

		// Meldingen
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.NoticePayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientState.notice(payload.text(), payload.color())));

		// Penalty-keuzescherm
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OfferPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.level != null && Config.PAUSED_NAMESPACES.contains(mc.level.dimension().identifier().getNamespace())) {
						return; // in BattleHub: de server stuurt de keuze opnieuw als je eruit gaat
					}
					mc.setScreenAndShow(new PenaltyChoiceScreen(Arrays.asList(payload.ids().split(","))));
				}));

		// Statusscherm (altaar)
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenStatusPayload.TYPE, (payload, context) ->
				context.client().execute(() -> Minecraft.getInstance().setScreenAndShow(new StatusScreen())));
	}
}
