package net.battlehub.core.chaos;

import java.util.ArrayList;
import java.util.List;

/**
 * One event of The Bridge: Chaos, without Minecraft. An event is a list of actions for everyone in play:
 * <ul>
 * <li>{@code effect <effect> <amplifier> [seconds]}: a potion effect (for the event's length unless given);</li>
 * <li>{@code attr <attribute> <add|base|total> <amount>}: a temporary attribute change for the event's length;</li>
 * <li>{@code give <item> <count> [<enchantment> <level>]}: an item;</li>
 * <li>{@code potion <splash|lingering|drink> <potion> <count>}: potions;</li>
 * <li>{@code do <special>}: something the match does itself (see {@link ChaosCatalog#SPECIALS}).</li>
 * </ul>
 */
public record ChaosEvent(String id, Category category, int seconds, int weight, List<Action> actions) {
	/** What kind of thing happens; two events of the same kind are not planned back to back. */
	public enum Category { ITEM, EFFECT, BODY, WORLD, RULE, FUN }

	public record Action(String verb, List<String> args) {
		public String arg(int i) {
			return i < args.size() ? args.get(i) : "";
		}

		public static Action parse(String text) {
			String[] parts = text.trim().split("\\s+");
			List<String> args = new ArrayList<>();
			for (int i = 1; i < parts.length; i++) args.add(parts[i]);
			return new Action(parts[0], List.copyOf(args));
		}
	}

	/** Lang key of the title; the subtitle is this plus ".sub" (with the length in seconds as {0}). */
	public String langKey() {
		return "bridge_chaos.event." + id;
	}

	public int ticks() {
		return seconds * 20;
	}

	public boolean uses(String verb) {
		for (Action a : actions) {
			if (a.verb().equals(verb)) return true;
		}
		return false;
	}
}
