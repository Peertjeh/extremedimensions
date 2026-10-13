package com.extremedimensions.client;

import java.util.Arrays;
import java.util.HashSet;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.client.audio.MenuMusic;
import com.extremedimensions.client.particle.MoteParticle;
import com.extremedimensions.client.entity.LurkerModel;
import com.extremedimensions.client.entity.LurkerRenderer;
import com.extremedimensions.client.entity.VeilRayModel;
import com.extremedimensions.client.entity.VeilRayRenderer;
import com.extremedimensions.client.hud.ChargeHud;
import com.extremedimensions.client.inventory.JournalButton;
import com.extremedimensions.client.inventory.CursesButton;
import com.extremedimensions.client.screen.JournalScreen;
import com.extremedimensions.client.sky.SeamAmbience;
import com.extremedimensions.client.sky.SeamSky;
import com.extremedimensions.client.screen.CursesScreen;
import com.extremedimensions.client.screen.PenaltyChoiceScreen;
import com.extremedimensions.client.screen.StatusScreen;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.title.TitleArt;
import com.extremedimensions.client.tooltip.Tooltips;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.registry.ModEntities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/** Client entrypoint: HUD, screens, title art, menu music, tooltips and packet receivers. */
public class ExtremeDimensionsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof TitleScreen) {
				ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, tickProgress) ->
						TitleArt.draw(graphics, s.width, s.height, mouseX, mouseY));
			}
			if (screen instanceof DeathScreen && ClientState.inCollapse() && client.player != null) {
				// The Collapse sequence carries the player home: no death screen in between
				client.player.respawn();
				client.gui.setScreen(null);
				return;
			}
			if (screen instanceof InventoryScreen inv) {
				JournalButton.attach(inv);
				CursesButton.attach(inv);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(MenuMusic::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SeamSky::prepare);
		ClientTickEvents.END_CLIENT_TICK.register(SeamAmbience::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientState::reset));
		HudElementRegistry.addLast(ExtremeDimensions.id("charge_hud"), new ChargeHud());
		Tooltips.register();
		MoteParticle.register();
		ModelLayerRegistry.registerModelLayer(LurkerModel.LAYER, LurkerModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.LURKER, ctx -> new LurkerRenderer<>(ctx, "lurker", 1.4F, 1.1F, false));
		EntityRendererRegistry.register(ModEntities.HOLLOWLING, ctx -> new LurkerRenderer<>(ctx, "hollowling", 0.42F, 0.4F, true));
		EntityRendererRegistry.register(ModEntities.KEEPER, ctx -> new LurkerRenderer<>(ctx, "keeper", 1.75F, 1.4F, false));
		EntityRendererRegistry.register(ModEntities.STARVED_ONE, ctx -> new LurkerRenderer<>(ctx, "starved_one", 2.35F, 1.8F, false));
		ModelLayerRegistry.registerModelLayer(VeilRayModel.LAYER, VeilRayModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.VEIL_RAY, VeilRayRenderer::new);
		EntityRendererRegistry.register(ModEntities.THINNED, ctx -> new LurkerRenderer<>(ctx, "thinned", 1.05F, 0.7F, false));

		ClientPlayNetworking.registerGlobalReceiver(ModPackets.StatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientState.apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientState.onFx(payload.kind(), payload.value())));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OfferPayload.TYPE, (payload, context) ->
				context.client().execute(() -> Minecraft.getInstance().gui.setScreen(
						new PenaltyChoiceScreen(Arrays.asList(payload.ids().split(","))))));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CursesPayload.TYPE, (payload, context) ->
				context.client().execute(() -> CursesScreen.receive(payload.data(), payload.open())));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenJournalPayload.TYPE, (payload, context) ->
				context.client().execute(() -> Minecraft.getInstance().gui.setScreen(
						new JournalScreen(new HashSet<>(Arrays.asList(payload.ids().split(","))),
								JournalButton.takeReturn()))));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenStatusPayload.TYPE, (payload, context) ->
				context.client().execute(() -> Minecraft.getInstance().gui.setScreen(new StatusScreen())));
	}
}
