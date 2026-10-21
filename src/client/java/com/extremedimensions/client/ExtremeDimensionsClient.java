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
import com.extremedimensions.client.hud.GuideCloseButton;
import com.extremedimensions.client.hud.StomachHud;
import com.extremedimensions.client.inventory.JournalButton;
import com.extremedimensions.client.inventory.CursesButton;
import com.extremedimensions.client.inventory.SealedSlots;
import com.extremedimensions.client.screen.JournalScreen;
import com.extremedimensions.client.sky.SeamAmbience;
import com.extremedimensions.client.sky.SeamSky;
import com.extremedimensions.client.screen.CursesScreen;
import com.extremedimensions.client.screen.PenaltyChoiceScreen;
import com.extremedimensions.client.screen.SettingsScreen;
import com.extremedimensions.client.screen.StatusScreen;
import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.title.TitleArt;
import com.extremedimensions.client.tooltip.Tooltips;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.registry.ModEntities;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/** Client entrypoint: HUD, screens, title art, menu music, tooltips, key mappings and packet receivers. */
public class ExtremeDimensionsClient implements ClientModInitializer {
	private static final KeyMapping.Category KEYS = KeyMapping.Category.register(ExtremeDimensions.id("main"));
	/** Unbound until the player picks keys (Controls): the inventory buttons and the settings gear are always there. */
	private static final KeyMapping OPEN_CURSES = new KeyMapping("key.extremedimensions.curses", InputConstants.UNKNOWN.getValue(), KEYS);
	private static final KeyMapping OPEN_JOURNAL = new KeyMapping("key.extremedimensions.journal", InputConstants.UNKNOWN.getValue(), KEYS);
	private static final KeyMapping OPEN_SETTINGS = new KeyMapping("key.extremedimensions.settings", InputConstants.UNKNOWN.getValue(), KEYS);
	private static final KeyMapping OPEN_TRIALS = new KeyMapping("key.extremedimensions.trials", InputConstants.UNKNOWN.getValue(), KEYS);

	@Override
	public void onInitializeClient() {
		ClientOptions.load();
		KeyMappingHelper.registerKeyMapping(OPEN_CURSES);
		KeyMappingHelper.registerKeyMapping(OPEN_JOURNAL);
		KeyMappingHelper.registerKeyMapping(OPEN_SETTINGS);
		KeyMappingHelper.registerKeyMapping(OPEN_TRIALS);
		// Key mappings only register presses while no screen is open, so these never open over another screen
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_SETTINGS.consumeClick()) {
				client.gui.setScreen(new SettingsScreen(null));
			}
			while (OPEN_CURSES.consumeClick()) {
				if (client.getConnection() != null && ClientState.modOnServer()) {
					ClientPlayNetworking.send(new ModPackets.RequestCursesPayload(true));
				}
			}
			while (OPEN_TRIALS.consumeClick()) {
				if (client.getConnection() != null && ClientState.modOnServer()) {
					com.extremedimensions.client.screen.TrialsScreen.request(null);
				}
			}
			while (OPEN_JOURNAL.consumeClick()) {
				if (client.getConnection() != null && ClientState.modOnServer()) {
					ClientPlayNetworking.send(new ModPackets.RequestJournalPayload());
				}
			}
		});
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
			// On a server without the mod (a hub behind a proxy, say) its buttons would only ask into the void
			if (screen instanceof InventoryScreen inv && ClientState.modOnServer()) {
				JournalButton.attach(inv);
				CursesButton.attach(inv);
			}
			if (screen instanceof AbstractContainerScreen<?> menu) {
				SealedSlots.attach(menu);
			}
			if (screen instanceof ChatScreen chat) {
				GuideCloseButton.attach(chat);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientState.tick());
		ClientTickEvents.END_CLIENT_TICK.register(MenuMusic::tick);
		ClientTickEvents.END_CLIENT_TICK.register(com.extremedimensions.client.audio.BossMusic::tick);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> com.extremedimensions.client.fx.CameraShake.tick());
		ClientTickEvents.END_CLIENT_TICK.register(SeamSky::prepare);
		ClientTickEvents.END_CLIENT_TICK.register(SeamAmbience::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientState::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(com.extremedimensions.client.coop.ClientFallen::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(com.extremedimensions.client.fx.BossCinema::reset));
		HudElementRegistry.addLast(ExtremeDimensions.id("charge_hud"), new ChargeHud());
		HudElementRegistry.addLast(ExtremeDimensions.id("stomach_hud"), new StomachHud());
		HudElementRegistry.addLast(ExtremeDimensions.id("boss_cinema"), new com.extremedimensions.client.fx.BossCinema());
		HudElementRegistry.addLast(ExtremeDimensions.id("seam_briefing"), new com.extremedimensions.client.hud.SeamBriefing());
		Tooltips.register();
		MoteParticle.register();
		com.extremedimensions.client.fx.AttackFx.register();
		ModelLayerRegistry.registerModelLayer(LurkerModel.LAYER, LurkerModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.LURKER, ctx -> new LurkerRenderer<>(ctx, "lurker", 1.4F, 1.1F, false));
		EntityRendererRegistry.register(ModEntities.HOLLOWLING, ctx -> new LurkerRenderer<>(ctx, "hollowling", 0.42F, 0.4F, true));
		EntityRendererRegistry.register(ModEntities.KEEPER, ctx -> new LurkerRenderer<>(ctx, "keeper", 1.75F, 1.4F, false));
		EntityRendererRegistry.register(ModEntities.STARVED_ONE, ctx -> new LurkerRenderer<>(ctx, "starved_one", 2.35F, 1.8F, false));
		ModelLayerRegistry.registerModelLayer(VeilRayModel.LAYER, VeilRayModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.VEIL_RAY, VeilRayRenderer::new);
		EntityRendererRegistry.register(ModEntities.THINNED, ctx -> new LurkerRenderer<>(ctx, "thinned", 1.05F, 0.7F, false));
		EntityRendererRegistry.register(ModEntities.RIFT_PORTAL, com.extremedimensions.client.entity.RiftPortalRenderer::new);
		com.extremedimensions.seam.RiftPortal.viewer = () -> {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			return mc.player == null ? -1 : mc.player.getId();
		};
		// The Elders: colossal, each with a model of its own (see tools/elders)
		ModelLayerRegistry.registerModelLayer(com.extremedimensions.client.entity.elder.MatriarchModel.LAYER,
				com.extremedimensions.client.entity.elder.MatriarchGeometry::create);
		ModelLayerRegistry.registerModelLayer(com.extremedimensions.client.entity.elder.FerrymanModel.LAYER,
				com.extremedimensions.client.entity.elder.FerrymanGeometry::create);
		ModelLayerRegistry.registerModelLayer(com.extremedimensions.client.entity.elder.WeaverModel.LAYER,
				com.extremedimensions.client.entity.elder.WeaverGeometry::create);
		EntityRendererRegistry.register(ModEntities.MATRIARCH, ctx -> new com.extremedimensions.client.entity.elder.ElderRenderer<>(ctx,
				new com.extremedimensions.client.entity.elder.MatriarchModel(ctx.bakeLayer(com.extremedimensions.client.entity.elder.MatriarchModel.LAYER)),
				new com.extremedimensions.client.entity.elder.MatriarchModel(ctx.bakeLayer(com.extremedimensions.client.entity.elder.MatriarchModel.LAYER)),
				"matriarch", 1.5F, 4.5F));
		EntityRendererRegistry.register(ModEntities.FERRYMAN, ctx -> new com.extremedimensions.client.entity.elder.ElderRenderer<>(ctx,
				new com.extremedimensions.client.entity.elder.FerrymanModel(ctx.bakeLayer(com.extremedimensions.client.entity.elder.FerrymanModel.LAYER)),
				new com.extremedimensions.client.entity.elder.FerrymanModel(ctx.bakeLayer(com.extremedimensions.client.entity.elder.FerrymanModel.LAYER)),
				"ferryman", 1.5F, 5.0F));
		EntityRendererRegistry.register(ModEntities.WEAVER, ctx -> new com.extremedimensions.client.entity.elder.ElderRenderer<>(ctx,
				new com.extremedimensions.client.entity.elder.WeaverModel(ctx.bakeLayer(com.extremedimensions.client.entity.elder.WeaverModel.LAYER)),
				new com.extremedimensions.client.entity.elder.WeaverModel(ctx.bakeLayer(com.extremedimensions.client.entity.elder.WeaverModel.LAYER)),
				"weaver", 1.5F, 3.0F));

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
						new JournalScreen(new HashSet<>(Arrays.asList(payload.ids().split(","))), payload.beasts(), payload.ledger(),
								new HashSet<>(Arrays.asList(payload.notes().split(","))), payload.path(), JournalButton.takeReturn()))));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.BossFxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.fx.BossCinema.handle(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.AttackFxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.fx.AttackFx.handle(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.BossMusicPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					if (payload.stop()) {
						com.extremedimensions.client.audio.BossMusic.stop(payload.track());
					} else {
						com.extremedimensions.client.audio.BossMusic.request(payload.track());
					}
				}));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.TrialsPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.screen.TrialsScreen.receive(payload.data())));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.SettingsPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientState.applySettings(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenStatusPayload.TYPE, (payload, context) ->
				context.client().execute(() -> Minecraft.getInstance().gui.setScreen(new StatusScreen(payload.sharing()))));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.BurdenPayload.TYPE, (payload, context) ->
				context.client().execute(() -> Minecraft.getInstance().gui.setScreen(
						new com.extremedimensions.client.screen.BurdenScreen(payload.friend(), payload.name(), payload.curses()))));
		com.extremedimensions.client.coop.ClientFallen.init();
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FallenPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.coop.ClientFallen.receive(payload.fallen())));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.BarterPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.screen.BarterScreen.show(payload.young(), payload.rep(), payload.offers())));
	}
}
