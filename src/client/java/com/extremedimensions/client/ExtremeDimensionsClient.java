package com.extremedimensions.client;

import java.util.Arrays;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.ModPackets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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

		// Menumuziek tot de wereld geladen is
		ClientTickEvents.END_CLIENT_TICK.register(MenuMusic::tick);

		// Elke wereld begint schoon
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientState::reset));

		// HUD
		HudElementRegistry.addLast(ExtremeDimensions.id("charge_hud"), new ChargeHud());

		// Status van de server
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.StatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					ClientState.nearNether = payload.nearNether();
					ClientState.nearEnd = payload.nearEnd();
					ClientState.runCharge = payload.runCharge();
					ClientState.runMax = payload.runMax();
					ClientState.dimCode = payload.dimCode();
					ClientState.drainX100 = payload.drainX100();
					ClientState.expeditions = payload.expeditions();
					ClientState.collapses = payload.collapses();
					ClientState.longestRun = payload.longestRun();
					ClientState.runSecs = payload.runSecs();
					ClientState.penalties = payload.penalties();
					ClientState.penaltyClock = payload.penaltyClock();
					ClientState.tetherDist = payload.tetherDist();
					ClientState.syncMs = System.currentTimeMillis();
				}));

		// Animaties van de server
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientState.onFx(payload.kind(), payload.value())));

		// Penalty-keuzescherm
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OfferPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					Minecraft mc = Minecraft.getInstance();
					mc.gui.setScreen(new PenaltyChoiceScreen(Arrays.asList(payload.ids().split(","))));
				}));

		// Tether Journal
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenJournalPayload.TYPE, (payload, context) ->
				context.client().execute(() -> Minecraft.getInstance().gui.setScreen(new JournalScreen(
						new java.util.HashSet<>(Arrays.asList(payload.ids().split(",")))))));

		// Statusscherm (altaar)
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenStatusPayload.TYPE, (payload, context) ->
				context.client().execute(() -> Minecraft.getInstance().gui.setScreen(new StatusScreen())));
	}
}
