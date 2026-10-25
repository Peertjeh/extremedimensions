package net.battlehub.games.hunger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.bot.Bots;
import net.battlehub.games.HungerGamesMatch;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.util.Ids;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * No locator bar in the Hunger Games (1.49). The game's bar at the top of the screen (vanilla since 1.21.6, shown over the
 * hotbar) points at every other player in range, which in a game of hiding and hunting gives everyone away. Tributes
 * neither send nor receive waypoints there: their waypoint ranges go to zero (a temporary modifier) for as long as they are
 * in play, and come back after. Everywhere else the bar works as always.
 */
public final class Locator {
	private Locator() {}

	static final Identifier ID = Ids.id("battlehub:hunger_no_locator");
	static final String[] RANGES = {"minecraft:waypoint_transmit_range", "minecraft:waypoint_receive_range"};

	private static List<Holder<Attribute>> ranges;
	/** Who has the bar off now. */
	private static final Set<UUID> OFF = new HashSet<>();

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) return;
		Set<UUID> tributes = new HashSet<>();
		for (Match m : Matches.all()) {
			if (!(m instanceof HungerGamesMatch) || (m.phase() != Match.Phase.RUNNING && m.phase() != Match.Phase.COUNTDOWN)) continue;
			for (UUID id : m.participants()) {
				ServerPlayer p = m.player(id);
				if (p == null || Bots.isBot(p) || !m.inPlay(id)) continue;
				tributes.add(id);
				if (OFF.add(id) || !off(p)) set(p, true);
			}
		}
		for (java.util.Iterator<UUID> it = OFF.iterator(); it.hasNext();) {
			UUID id = it.next();
			if (tributes.contains(id)) continue;
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) set(p, false);
			it.remove();
		}
	}

	/** Whether the bar is off for this player (both ranges at zero by this modifier). */
	public static boolean off(ServerPlayer p) {
		for (Holder<Attribute> a : ranges()) {
			AttributeInstance inst = p.getAttribute(a);
			if (inst == null || !inst.hasModifier(ID)) return false;
		}
		return !ranges().isEmpty();
	}

	private static void set(ServerPlayer p, boolean off) {
		for (Holder<Attribute> a : ranges()) {
			AttributeInstance inst = p.getAttribute(a);
			if (inst == null) continue;
			// times (1 - 1): nothing, whatever the range was
			if (off) inst.addOrUpdateTransientModifier(new AttributeModifier(ID, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			else if (inst.hasModifier(ID)) inst.removeModifier(ID);
		}
	}

	private static List<Holder<Attribute>> ranges() {
		if (ranges == null) {
			ranges = new ArrayList<>();
			for (String r : RANGES) {
				var h = BuiltInRegistries.ATTRIBUTE.get(Ids.id(r));
				if (h.isPresent()) ranges.add(h.get());
				else BattleHub.LOGGER.warn("No attribute {}: the locator bar stays on in the Hunger Games", r);
			}
		}
		return ranges;
	}

	public static void clear() {
		OFF.clear();
	}
}
