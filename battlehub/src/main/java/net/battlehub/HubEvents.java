package net.battlehub;

import net.battlehub.arena.ArenaBuilder;
import net.battlehub.arena.ArenaSlots;
import net.battlehub.arena.ArenaWorld;
import net.battlehub.command.HubCommands;
import net.battlehub.data.Snapshots;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.config.ConfigManager;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.MainMenu;
import net.battlehub.gui.Menus;
import net.battlehub.lang.Lang;
import net.battlehub.lobby.HubItems;
import net.battlehub.lobby.Lobby;
import net.battlehub.lobby.Protection;
import net.battlehub.progression.Ranks;
import net.battlehub.progression.TitleService;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.battlehub.util.Icon;
import net.battlehub.util.Msg;
import net.battlehub.util.Tasks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

/** Wires BattleHub to the Fabric events. */
public final class HubEvents {
	private HubEvents() {}

	private static int tick;

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(HubEvents::started);
		ServerLifecycleEvents.SERVER_STOPPING.register(HubEvents::stopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			Queues.clear();
			net.battlehub.bot.Bots.clear();
			net.battlehub.hub.HubNpcs.clear();
			net.battlehub.hub.HubDummy.clear();
			net.battlehub.fx.Cinematics.clear();
			net.battlehub.fx.Killfeed.clear();
			net.battlehub.fx.Sounds.clear();
			net.battlehub.fx.ResourcePack.stop();
			net.battlehub.arena.CustomMaps.clear();
			net.battlehub.lobby.DoubleJump.clear();
			net.battlehub.fair.Afk.clear();
			net.battlehub.fair.AntiCheat.clear();
			net.battlehub.fair.Reports.clear();
			net.battlehub.social.Groups.clear();
			net.battlehub.social.CustomGames.clear();
			net.battlehub.social.Clans.clear();
			net.battlehub.games.Visibility.clear();
			Sessions.clear();
			net.battlehub.hub.HubBoards.clear();
			Tasks.clear();
			PlayerStore.unload();
			BattleHub.setServer(null);
		});
		ServerTickEvents.END_SERVER_TICK.register(HubEvents::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> HubCommands.register(dispatcher));

		ServerPlayerEvents.JOIN.register(HubEvents::join);
		// Leaving is handled by PlayerListMixin, before vanilla saves the player file.

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p) {
				return allowDamage(p, source);
			}
			// Shopkeepers and other game NPCs cannot be hurt
			return !net.battlehub.games.Visibility.isProtectedNpc(entity);
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p) || allowDeath(p, source));
		// Damage in matches feeds the recap, the teaming check and the reach check
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer victim) {
				afterDamage(victim, source, taken);
			}
		});
		// Arrows that players in a match shoot count for the recap
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Projectile proj && proj.getOwner() instanceof ServerPlayer shooter
					&& entity instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow) {
				Match m = matchOf(shooter);
				if (m != null) {
					m.recordShot(shooter);
				}
			}
		});

		// In a match the game decides about blocks, clicks and attacks; elsewhere the lobby protection does
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (!(player instanceof ServerPlayer sp)) {
				return true;
			}
			Match m = matchOf(sp);
			return m != null ? m.onBreakBlock(sp, pos, state) : Protection.mayBreak(sp);
		});
		UseItemCallback.EVENT.register((player, level, hand) -> useItem(player, player.getItemInHand(hand)));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			Match m = player instanceof ServerPlayer sp ? matchOf(sp) : null;
			if (m != null) {
				InteractionResult r = m.onUseBlock((ServerPlayer) player, hit, held);
				if (r != InteractionResult.PASS) {
					return r;
				}
			}
			if (Icon.isHubItem(held)) {
				return useItem(player, held);
			}
			if (m != null) {
				if (!(held.getItem() instanceof net.minecraft.world.item.BlockItem) && !(held.getItem() instanceof net.minecraft.world.item.BucketItem)) {
					return InteractionResult.PASS;
				}
				BlockPos clicked = hit.getBlockPos();
				BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
				return m.onPlaceBlock((ServerPlayer) player, target, held) ? InteractionResult.PASS : InteractionResult.FAIL;
			}
			if (player instanceof ServerPlayer sp && !Protection.mayPlace(sp, held)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && (net.battlehub.hub.HubDummy.use(sp, entity) || net.battlehub.hub.HubNpcs.click(sp, entity))) {
				return InteractionResult.SUCCESS;
			}
			Match m = player instanceof ServerPlayer sp ? matchOf(sp) : null;
			if (m != null) {
				InteractionResult r = m.onUseEntity((ServerPlayer) player, entity);
				if (r != InteractionResult.PASS) {
					return r;
				}
			}
			ItemStack held = player.getItemInHand(hand);
			if (Icon.isHubItem(held)) {
				return useItem(player, held);
			}
			return InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			Match m = player instanceof ServerPlayer sp ? matchOf(sp) : null;
			return m == null ? InteractionResult.PASS : m.onAttackBlock((ServerPlayer) player, pos);
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && (net.battlehub.hub.HubDummy.hit(sp, entity) || net.battlehub.hub.HubNpcs.click(sp, entity))) {
				return InteractionResult.FAIL;
			}
			Match m = player instanceof ServerPlayer sp ? matchOf(sp) : null;
			if (m != null) {
				InteractionResult r = m.onAttackEntity((ServerPlayer) player, entity);
				if (r != InteractionResult.PASS) {
					return r;
				}
			}
			if (player instanceof ServerPlayer sp && !(entity instanceof Player) && !Protection.mayBreak(sp)
					&& Sessions.state(sp.getUUID()) != Sessions.State.MATCH) {
				// Item frames, armour stands and paintings in the lobby stay where they are.
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	private static void started(MinecraftServer server) {
		BattleHub.setServer(server);
		Ranks.reload();
		PlayerStore.load(server);
		Snapshots.init(server);
		net.battlehub.hub.Travel.init(server);
		ArenaSlots.init(server);
		net.battlehub.hub.HubWorld.ensureBuilt(server);
		net.battlehub.progression.Seasons.init(server);
		net.battlehub.social.Clans.load(server);
		net.battlehub.fx.ResourcePack.start(server);
		net.battlehub.arena.CustomMaps.load(server);
	}

	private static void stopping(MinecraftServer server) {
		Matches.shutdown(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Queues.leaveAll(p);
		}
		PlayerStore.saveAll();
		net.battlehub.social.Clans.save();
	}

	private static void tick(MinecraftServer server) {
		tick++;
		Tasks.tick();
		ArenaBuilder.tick();
		Matches.tick();
		net.battlehub.bot.Bots.tick(server);
		Queues.tick(server);
		net.battlehub.spectate.Spectators.tick(server);
		Menus.tick(server);
		net.battlehub.progression.Cosmetics.tick(server, tick);
		net.battlehub.hub.HubBoards.tick(server);
		net.battlehub.hub.HubNpcs.tick(server);
		net.battlehub.hub.HubDummy.tick(server);
		net.battlehub.fx.Cinematics.tick(server);
		net.battlehub.lobby.DoubleJump.tick(server);
		net.battlehub.progression.Seasons.tick(server);
		net.battlehub.fair.Afk.tick(server);
		net.battlehub.fair.AntiCheat.tick(server);
		if (tick % 1200 == 0) {
			net.battlehub.social.Clans.tick(server);
		}
		if (net.battlehub.debug.SelfTest.enabled()) {
			net.battlehub.debug.SelfTest.tick();
		}
		if (tick % 10 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				net.battlehub.hub.Travel.guard(p);
				lobbyTick(p);
			}
		}
	}

	/** Players in the hub world: lobby items, full health and hunger, back up when falling off. */
	private static void lobbyTick(ServerPlayer p) {
		if (!net.battlehub.hub.HubWorld.is(p.level()) || !Sessions.inLobby(p.getUUID()) || p.isSpectator()) {
			return;
		}
		Lobby.refresh(p);
		net.battlehub.hub.Travel.showLevel(p);
		net.battlehub.fx.Sounds.lobbyMusic(p, tick);
		if (ConfigManager.config().lobby.keepFed) {
			if (p.getFoodData().getFoodLevel() < 20) {
				p.getFoodData().setFoodLevel(20);
			}
			if (p.getHealth() < p.getMaxHealth() && ConfigManager.config().protection.noDamageOutsideMatches) {
				p.setHealth(p.getMaxHealth());
			}
		}
		if (p.getY() < ConfigManager.config().lobby.voidY) {
			Lobby.teleport(p);
		}
	}

	private static void join(ServerPlayer p) {
		PlayerStore.get(p);
		Sessions.toLobby(p.getUUID());
		if (Snapshots.has(p.getUUID())) {
			// Left during a match or the server crashed: give everything back first.
			Snapshots.restore(p, true);
		}
		if (ArenaWorld.is(p.level())) {
			Lobby.teleport(p);
		}
		if (net.battlehub.hub.HubWorld.is(p.level())) {
			if (ConfigManager.config().lobby.teleportOnJoin) {
				Lobby.teleport(p);
			}
			net.battlehub.hub.Travel.guard(p);
			HubItems.ensure(p, false);
			net.battlehub.hub.Travel.showLevel(p);
			net.battlehub.progression.Retention.login(p);
			net.battlehub.fx.ResourcePack.offer(p);
		} else if (ConfigManager.config().hub.sendToHubOnJoin) {
			net.battlehub.hub.Travel.enter(p);
		} else if (ConfigManager.config().hub.joinHint) {
			Msg.raw(p, Lang.tr(p, "hub.hint").append(" ").append(Msg.button(p, "hub.hint_button", "hub.hint_hover", "/hub")));
		}
		TitleService.apply(p);
	}

	/** The player is leaving the server: counts as giving up a match, ends spectating and queues, saves stats. */
	public static void leaving(ServerPlayer p) {
		if (p == null || BattleHub.server() == null) {
			return;
		}
		if (Sessions.state(p.getUUID()) == Sessions.State.SPECTATING) {
			net.battlehub.spectate.Spectators.leave(p, false);
		} else {
			Match m = Matches.of(p.getUUID());
			if (m != null) {
				m.quit(p);
			}
		}
		Queues.leaveAll(p);
		TitleService.remove(p);
		net.battlehub.progression.Cosmetics.forget(p.getUUID());
		net.battlehub.fair.AntiCheat.forget(p.getUUID());
		net.battlehub.fx.Sounds.forget(p.getUUID());
		net.battlehub.fx.ResourcePack.forget(p.getUUID());
		net.battlehub.lobby.DoubleJump.forget(p.getUUID());
		net.battlehub.hub.HubDummy.forget(p.getUUID());
		net.battlehub.social.Groups.forget(p);
		net.battlehub.social.CustomGames.forget(p);
		PlayerStore.save(p.getUUID());
		Sessions.remove(p.getUUID());
	}

	private static boolean allowDamage(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
		if (!net.battlehub.hub.HubWorld.isBattleHub(p.level())) {
			return true;
		}
		if (Sessions.state(p.getUUID()) == Sessions.State.MATCH) {
			Match m = Matches.of(p.getUUID());
			return m != null && m.allowDamage(p, source);
		}
		if (Sessions.state(p.getUUID()) == Sessions.State.SPECTATING) {
			return false;
		}
		return !ConfigManager.config().protection.noDamageOutsideMatches;
	}

	private static boolean allowDeath(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
		if (!net.battlehub.hub.HubWorld.isBattleHub(p.level())) {
			return true;
		}
		if (Sessions.state(p.getUUID()) == Sessions.State.MATCH) {
			Match m = Matches.of(p.getUUID());
			if (m != null) {
				if (m.totemSaves(p, source)) {
					// Vanilla uses the totem of undying (it comes after this event)
					return true;
				}
				m.handleDeath(p, source);
				return false;
			}
		}
		if (!ConfigManager.config().protection.preventDeathOutsideMatches) {
			return true;
		}
		p.setHealth(p.getMaxHealth());
		p.clearFire();
		p.resetFallDistance();
		Tasks.next(() -> Lobby.teleport(p));
		return false;
	}

	/** An arm swing (from the network, on the server thread). */
	public static void swung(ServerPlayer p) {
		net.battlehub.fair.Afk.active(p.getUUID());
		net.battlehub.fair.AntiCheat.swing(p);
		net.battlehub.hub.HubDummy.swing(p);
		Match m = matchOf(p);
		if (m != null && !p.hasDisconnected()) {
			m.recordSwing(p);
			m.onSwing(p);
		}
	}

	private static void afterDamage(ServerPlayer victim, net.minecraft.world.damagesource.DamageSource source, float taken) {
		Match m = matchOf(victim);
		if (m == null) {
			return;
		}
		ServerPlayer attacker = source.getEntity() instanceof ServerPlayer a ? a : null;
		boolean arrow = source.getDirectEntity() instanceof Projectile;
		m.recordHit(attacker, victim, taken, arrow);
		if (attacker != null && !arrow && source.getDirectEntity() == attacker) {
			net.battlehub.fair.AntiCheat.hit(attacker, victim);
		}
	}

	/** The match this player takes part in (not watches), or null. */
	public static Match matchOf(ServerPlayer p) {
		return Sessions.state(p.getUUID()) == Sessions.State.MATCH ? Matches.of(p.getUUID()) : null;
	}

	private static InteractionResult useItem(Player player, ItemStack held) {
		String tag = Icon.tagOf(held);
		if (tag.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp && tag.startsWith("game.")) {
			Match m = matchOf(sp);
			if (m != null) {
				m.onUseGameItem(sp, tag);
			}
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer sp) {
			switch (tag) {
				case HubItems.COMPASS -> new MainMenu(sp).open();
				case HubItems.LEAVE_QUEUE -> {
					Queues.leaveAll(sp);
					Msg.send(sp, "queue.left_all");
				}
				case HubItems.EXIT -> net.battlehub.hub.Travel.leave(sp);
				case HubItems.AGAIN -> net.battlehub.queue.PlayAgain.accept(sp);
				default -> { }
			}
		}
		return InteractionResult.SUCCESS;
	}
}
