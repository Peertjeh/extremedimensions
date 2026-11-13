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
import com.extremedimensions.client.screen.StatusScreen;
import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.title.Menus;
import com.extremedimensions.client.title.TitleArt;
import com.extremedimensions.client.tooltip.Tooltips;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.registry.ModEntities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/** Client entrypoint: HUD, screens, title art, menu music, tooltips, key mappings and packet receivers. */
public class ExtremeDimensionsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientOptions.load();
		// The mod's keys (see ModKeys): the screens they open, and a word on them after the first join
		ModKeys.register();
		ClientTickEvents.END_CLIENT_TICK.register(ModKeys::tick);
		// The cards under the crosshair and the guide card, put away on a key or (the first two) by moving
		ClientTickEvents.END_CLIENT_TICK.register(com.extremedimensions.client.hud.PutAway::tick);
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			Menus.shown(screen);
			ScreenEvents.remove(screen).register(Menus::gone);
			if (screen instanceof TitleScreen) {
				ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, tickProgress) ->
						TitleArt.drawTitle(graphics, s.width, s.height, mouseX, mouseY));
			}
			// (the world loading screen and the message screens draw their own background: see LoadingArtMixin)
			if (screen instanceof ConnectScreen || screen instanceof ProgressScreen) {
				ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, tickProgress) ->
						TitleArt.drawLoading(graphics, s.width, s.height));
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
			// A Tetherite binding waiting at the smithing table: its chance, and what makes it, beside the table
			if (screen instanceof net.minecraft.client.gui.screens.inventory.SmithingScreen smithing) {
				ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, tickProgress) ->
						com.extremedimensions.client.hud.BindingPanel.draw(smithing, graphics, client.font));
			}
			// In danger, the pause menu says so above its title: its button to leave would cost curses, and asks first
			if (screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
				ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, tickProgress) ->
						com.extremedimensions.client.hud.DangerChip.pause(graphics, client.font, s.width));
				com.extremedimensions.client.hud.DangerChip.guardLeave(screen);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientState.tick());
		ClientTickEvents.END_CLIENT_TICK.register(Menus::tick);
		ClientTickEvents.END_CLIENT_TICK.register(MenuMusic::tick);
		ClientTickEvents.END_CLIENT_TICK.register(com.extremedimensions.client.audio.BossMusic::tick);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> com.extremedimensions.client.fx.CameraShake.tick());
		ClientTickEvents.END_CLIENT_TICK.register(SeamSky::prepare);
		ClientTickEvents.END_CLIENT_TICK.register(SeamAmbience::tick);
		// The waypoints of a map mod (JourneyMap), which listens to them; nothing happens here without one
		ClientTickEvents.END_CLIENT_TICK.register(com.extremedimensions.client.compat.MapMarks::tick);
		// (Xaero's Minimap has no entrypoint for other mods: its waypoints are set up here, where it is loaded)
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaerominimap")) {
			com.extremedimensions.client.compat.xaero.ExtremeXaeroWaypoints.start();
		}
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientState::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(com.extremedimensions.client.compat.MapMarks::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(com.extremedimensions.client.coop.ClientFallen::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(com.extremedimensions.client.fx.BossCinema::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(com.extremedimensions.client.state.ClientDanger::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(com.extremedimensions.client.hud.CollapseRecap::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(JournalScreen::forget));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(com.extremedimensions.client.hud.PutAway::reset));
		HudElementRegistry.addLast(ExtremeDimensions.id("charge_hud"), new ChargeHud());
		HudElementRegistry.addLast(ExtremeDimensions.id("stomach_hud"), new StomachHud());
		HudElementRegistry.addLast(ExtremeDimensions.id("boss_cinema"), new com.extremedimensions.client.fx.BossCinema());
		HudElementRegistry.addLast(ExtremeDimensions.id("seam_briefing"), new com.extremedimensions.client.hud.SeamBriefing());
		HudElementRegistry.addLast(ExtremeDimensions.id("collapse_recap"), new com.extremedimensions.client.hud.CollapseRecap());
		Tooltips.register();
		MoteParticle.register();
		com.extremedimensions.client.fx.AttackFx.register();
		ModelLayerRegistry.registerModelLayer(LurkerModel.LAYER, LurkerModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.LURKER, ctx -> new LurkerRenderer<>(ctx, "lurker", 1.4F, 1.1F, false));
		// (the Lurker's body brought down to fit a two-high tunnel)
		EntityRendererRegistry.register(ModEntities.CAVE_LURKER, ctx -> new LurkerRenderer<>(ctx, "cave_lurker", 0.72F, 0.5F, false));
		// (the size of a Hollowling, with a Lurker's long build)
		EntityRendererRegistry.register(ModEntities.SCULK_LURKER, ctx -> new LurkerRenderer<>(ctx, "sculk_lurker", 0.44F, 0.35F, false));
		// (the Lurker's long build brought down to a little over a man's height)
		EntityRendererRegistry.register(ModEntities.MAGPIE, ctx -> new LurkerRenderer<>(ctx, "magpie", 0.95F, 0.5F, false));
		// (the smallest of them, with a young one's big head)
		EntityRendererRegistry.register(ModEntities.WICK, ctx -> new LurkerRenderer<>(ctx, "wick", 0.24F, 0.25F, true));
		// (long and lean, a little taller than a man)
		EntityRendererRegistry.register(ModEntities.TIDEWALKER, ctx -> new LurkerRenderer<>(ctx, "tidewalker", 1.05F, 0.6F, false));
		// (the biggest of them, taller than a Keeper)
		EntityRendererRegistry.register(ModEntities.GATEBREAKER, ctx -> new LurkerRenderer<>(ctx, "gatebreaker", 1.95F, 1.6F, false));
		// (tall and thin, as the End's own walkers are)
		EntityRendererRegistry.register(ModEntities.LOCKWARDEN, ctx -> new LurkerRenderer<>(ctx, "lockwarden", 1.45F, 0.8F, false));
		// The Pale Hauler (3.39): an Enderman's height, lanky under its pale hood and shaggy mantle, with a body of its
		// own after its concept sheet (see tools/elders/pale_hauler.py)
		ModelLayerRegistry.registerModelLayer(com.extremedimensions.client.entity.elder.PaleHaulerModel.LAYER,
				com.extremedimensions.client.entity.elder.PaleHaulerGeometry::create);
		EntityRendererRegistry.register(ModEntities.PALE_HAULER, com.extremedimensions.client.entity.elder.PaleHaulerRenderer::new);
		// The Seam's own: the Threadbiter knee-high with a young one's big head, the Edgecrawler a little over a man's
		// height, the Cragback broad and as tall as a Keeper
		EntityRendererRegistry.register(ModEntities.THREADBITER, ctx -> new LurkerRenderer<>(ctx, "threadbiter", 0.28F, 0.3F, true));
		EntityRendererRegistry.register(ModEntities.EDGECRAWLER, ctx -> new LurkerRenderer<>(ctx, "edgecrawler", 0.9F, 0.55F, false));
		EntityRendererRegistry.register(ModEntities.CRAGBACK, ctx -> new LurkerRenderer<>(ctx, "cragback", 1.7F, 1.8F, false));
		EntityRendererRegistry.register(ModEntities.HOLLOWLING, ctx -> new LurkerRenderer<>(ctx, "hollowling", 0.42F, 0.4F, true));
		EntityRendererRegistry.register(ModEntities.KEEPER, ctx -> new LurkerRenderer<>(ctx, "keeper", 1.75F, 1.4F, false));
		ModelLayerRegistry.registerModelLayer(com.extremedimensions.client.entity.elder.StarvedOneModel.LAYER,
				com.extremedimensions.client.entity.elder.StarvedOneGeometry::create);
		EntityRendererRegistry.register(ModEntities.STARVED_ONE, com.extremedimensions.client.entity.elder.StarvedOneRenderer::new);
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
		// The Knot Mother: a Weaver tied into the First Knot, with a shape of her own (3.23)
		ModelLayerRegistry.registerModelLayer(com.extremedimensions.client.entity.elder.KnotMotherModel.LAYER,
				com.extremedimensions.client.entity.elder.KnotMotherGeometry::create);
		EntityRendererRegistry.register(ModEntities.KNOT_MOTHER, ctx -> new com.extremedimensions.client.entity.elder.ElderRenderer<>(ctx,
				new com.extremedimensions.client.entity.elder.KnotMotherModel(ctx.bakeLayer(com.extremedimensions.client.entity.elder.KnotMotherModel.LAYER)),
				new com.extremedimensions.client.entity.elder.KnotMotherModel(ctx.bakeLayer(com.extremedimensions.client.entity.elder.KnotMotherModel.LAYER)),
				"knot_mother", 1.5F, 3.5F));
		// The Pale Mason (3.40)
		ModelLayerRegistry.registerModelLayer(com.extremedimensions.client.entity.elder.PaleMasonModel.LAYER,
				com.extremedimensions.client.entity.elder.PaleMasonGeometry::create);
		EntityRendererRegistry.register(ModEntities.PALE_MASON, ctx -> new com.extremedimensions.client.entity.elder.ElderRenderer<>(ctx,
				new com.extremedimensions.client.entity.elder.PaleMasonModel(ctx.bakeLayer(com.extremedimensions.client.entity.elder.PaleMasonModel.LAYER)),
				new com.extremedimensions.client.entity.elder.PaleMasonModel(ctx.bakeLayer(com.extremedimensions.client.entity.elder.PaleMasonModel.LAYER)),
				"pale_mason", 1.5F, 3.5F));

		ClientPlayNetworking.registerGlobalReceiver(ModPackets.StatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientState.apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientState.onFx(payload.kind(), payload.value())));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OfferPayload.TYPE, (payload, context) ->
				context.client().execute(() -> Minecraft.getInstance().gui.setScreen(
						new PenaltyChoiceScreen(Arrays.asList(payload.ids().split(","))))));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CursesPayload.TYPE, (payload, context) ->
				context.client().execute(() -> CursesScreen.receive(payload.data(), payload.open())));
		// (it opens where it was left this session, 3.55.0)
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenJournalPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					JournalScreen journal = new JournalScreen(new HashSet<>(Arrays.asList(payload.ids().split(","))), payload.beasts(), payload.ledger(),
							new HashSet<>(Arrays.asList(payload.notes().split(","))), payload.path(), payload.clan(), payload.anchors(),
							payload.hints(), JournalButton.takeReturn());
					journal.resume();
					Minecraft.getInstance().gui.setScreen(journal);
				}));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.BossFxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.fx.BossCinema.handle(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.AttackFxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.fx.AttackFx.handle(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.BindingPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.hud.BindingPanel.set(payload.permille(), payload.parts(), payload.capped(), payload.where())));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.MotionPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					// A shove, pull or drag the server set on the player: taken on here, where the player is moved
					net.minecraft.client.player.LocalPlayer me = Minecraft.getInstance().player;
					if (me != null) {
						me.setDeltaMovement(payload.x(), payload.y(), payload.z());
					}
				}));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.SquallPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					// A Pale Squall's gust: added to the player's own motion here, where it is made
					net.minecraft.client.player.LocalPlayer me = Minecraft.getInstance().player;
					if (me != null) {
						me.setDeltaMovement(me.getDeltaMovement().add(payload.x(), 0, payload.z()));
					}
				}));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.DangerPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.state.ClientDanger.set(payload.why(), payload.seconds(), payload.curses())));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.RecapPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.hud.CollapseRecap.show(payload)));
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
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.AdminPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.screen.AdminScreen.receive(payload.data(), payload.open())));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.TapestryPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.screen.TapestryScreen.receive(payload.data())));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.UnweavePayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.screen.UnweaveScreen.receive(payload.data())));
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
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.AnchorsPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.compat.MapMarks.anchors(payload.anchors())));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.BarterPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.extremedimensions.client.screen.BarterScreen.show(payload.young(), payload.rep(), payload.offers())));
	}
}
