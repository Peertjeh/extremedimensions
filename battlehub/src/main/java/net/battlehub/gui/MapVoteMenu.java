package net.battlehub.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.battlehub.arena.CustomMaps;
import net.battlehub.arena.MapRules;
import net.battlehub.arena.Theme;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.queue.Parties;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Vote for the map of a party game: any, one of the themes, or a hand-built map. Clicking joins the lobby. */
public final class MapVoteMenu extends Screen {
	private final GameMode mode;

	public MapVoteMenu(ServerPlayer viewer, GameMode mode) {
		super(viewer);
		this.mode = mode;
	}

	/** One choice: its vote value ("random", "theme:forest", "custom:sky_castle"), name and icon. */
	public record Option(String value, Component name, Item icon) {}

	public static List<Option> options(ServerPlayer viewer, GameMode mode) {
		List<Option> out = new ArrayList<>();
		out.add(new Option("random", Lang.tr(viewer, "menu.mapvote.random"), Items.ENDER_EYE));
		List<String> themes = ConfigManager.config().arenas.themes.get(mode.id());
		if (themes != null) {
			RandomSource rnd = RandomSource.create(1);
			for (String id : themes) {
				Theme t = Theme.byId(id);
				if (t == null) continue;
				Item icon = t.floor(rnd).getBlock().asItem();
				out.add(new Option("theme:" + t.id(), Lang.tr(viewer, "theme." + t.id()), icon == Items.AIR ? Items.GRASS_BLOCK : icon));
			}
		}
		for (CustomMaps.Map m : MapRules.maps(mode)) {
			out.add(new Option("custom:" + m.id, Component.literal(m.name()), Items.FILLED_MAP));
		}
		return out;
	}

	/** The name of what the lobby is voting for most (or "any map"). */
	public static Component leading(ServerPlayer viewer, GameMode mode) {
		Map<String, Integer> counts = new HashMap<>();
		for (String v : Parties.votes(mode, "map")) counts.merge(v, 1, Integer::sum);
		String best = null;
		int most = 0;
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			if (e.getValue() > most && !e.getKey().equals("random")) {
				best = e.getKey();
				most = e.getValue();
			}
		}
		if (best != null) {
			for (Option o : options(viewer, mode)) {
				if (o.value().equals(best)) return o.name();
			}
		}
		return Lang.tr(viewer, "menu.mapvote.random");
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.mapvote.title", Lang.tr(viewer, Icons.modeKey(mode)));
	}

	@Override
	public Style.Section section() {
		return Style.Section.PLAY;
	}

	@Override
	public int rows() {
		return 6;
	}

	@Override
	public void draw(Canvas c) {
		String mine = Parties.vote(viewer.getUUID(), mode, "map");
		Map<String, Integer> counts = new HashMap<>();
		for (String v : Parties.votes(mode, "map")) counts.merge(v, 1, Integer::sum);
		int[] slots = c.inner();
		List<Option> options = options(viewer, mode);
		for (int i = 0; i < options.size() && i < slots.length; i++) {
			Option o = options.get(i);
			boolean chosen = o.value().equals(mine);
			Icon icon = Icon.of(o.icon()).name(Lang.item(viewer, "menu.mapvote.option", o.name()))
					.lore(Lang.lore(viewer, "menu.parkour.votes", counts.getOrDefault(o.value(), 0)))
					.lore(Lang.lore(viewer, chosen ? "menu.parkour.voted" : "menu.parkour.click_vote"))
					.glint(chosen).clean();
			c.set(slots[i], icon.build(), click -> {
				Parties.join(viewer, mode, "map:" + o.value());
				new PartyGameMenu(viewer, mode).open();
			});
		}
		c.back(() -> new PartyGameMenu(viewer, mode).open());
		c.fill();
	}
}
