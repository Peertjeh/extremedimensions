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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
			Sessions.clear();
			Tasks.clear();
			PlayerStore.unload();
			BattleHub.setServer(null);
		});
		ServerTickEvents.END_SERVER_TICK.register(HubEvents::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> HubCommands.register(dispatcher));

		ServerPlayerEvents.JOIN.register(HubEvents::join);
		// Leaving is handled by PlayerListMixin, before vanilla saves the player file.

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p) || allowDamage(p, source));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p) || allowDeath(p, source));

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) ->
				!(player instanceof ServerPlayer sp) || Protection.mayBreak(sp));
		UseItemCallback.EVENT.register((player, level, hand) -> useItem(player, player.getItemInHand(hand)));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (Icon.isHubItem(held)) {
				return useItem(player, held);
			}
			if (player instanceof ServerPlayer sp && !Protection.mayPlace(sp, held)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (Icon.isHubItem(held)) {
				return useItem(player, held);
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
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
		ArenaSlots.init(server);
	}

	private static void stopping(MinecraftServer server) {
		Matches.shutdown(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Queues.leaveAll(p);
		}
		PlayerStore.saveAll();
	}

	private static void tick(MinecraftServer server) {
		tick++;
		Tasks.tick();
		ArenaBuilder.tick();
		Matches.tick();
		Queues.tick(server);
		net.battlehub.spectate.Spectators.tick(server);
		Menus.tick(server);
		if (net.battlehub.debug.SelfTest.enabled()) {
			net.battlehub.debug.SelfTest.tick();
		}
		if (tick % 10 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				lobbyTick(p);
			}
		}
	}

	private static void lobbyTick(ServerPlayer p) {
		if (!Sessions.inLobby(p.getUUID()) || p.isSpectator()) {
			return;
		}
		Lobby.refresh(p);
		if (ConfigManager.config().lobby.keepFed && Lobby.inArea(p)) {
			if (p.getFoodData().getFoodLevel() < 20) {
				p.getFoodData().setFoodLevel(20);
			}
			if (p.getHealth() < p.getMaxHealth() && ConfigManager.config().protection.noDamageOutsideMatches) {
				p.setHealth(p.getMaxHealth());
			}
		}
		if (Lobby.isLobbyDimension(p.level()) && p.getY() < ConfigManager.config().lobby.voidY) {
			Lobby.teleport(p);
		}
	}

	private static void join(ServerPlayer p) {
		PlayerStore.get(p);
		Sessions.toLobby(p.getUUID());
		if (Snapshots.has(p.getUUID())) {
			// Left during a match or the server crashed: give everything back first.
			Snapshots.restore(p, true);
			if (ConfigManager.config().lobby.teleportOnJoin) {
				Lobby.teleport(p);
			}
		} else if (ConfigManager.config().lobby.teleportOnJoin || ArenaWorld.is(p.level())) {
			Lobby.teleport(p);
		}
		HubItems.ensure(p, false);
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
		PlayerStore.save(p.getUUID());
		Sessions.remove(p.getUUID());
	}

	private static boolean allowDamage(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
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
		if (Sessions.state(p.getUUID()) == Sessions.State.MATCH) {
			Match m = Matches.of(p.getUUID());
			if (m != null) {
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

	private static InteractionResult useItem(Player player, ItemStack held) {
		String tag = Icon.tagOf(held);
		if (tag.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			switch (tag) {
				case HubItems.COMPASS -> new MainMenu(sp).open();
				case HubItems.LEAVE_QUEUE -> {
					Queues.leaveAll(sp);
					Msg.send(sp, "queue.left_all");
				}
				default -> { }
			}
		}
		return InteractionResult.SUCCESS;
	}
}
