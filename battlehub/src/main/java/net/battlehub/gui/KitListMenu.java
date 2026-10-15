package net.battlehub.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.KitConfig;
import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.match.Kits;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The kits a player can arrange: one per mode that hands out a kit with at least two items. */
public final class KitListMenu extends Screen {
	public KitListMenu(ServerPlayer viewer) {
		super(viewer);
	}

	/** Kit name per mode, first mode wins (Free for All and 1v2 use the duel kits). */
	public static Map<String, GameMode> editable() {
		Map<String, GameMode> out = new LinkedHashMap<>();
		for (GameMode m : GameMode.values()) {
			String kit = ConfigManager.config().game(m).kit;
			KitConfig.Kit k = kit == null ? null : ConfigManager.kits().kits.get(kit);
			if (k != null && !out.containsKey(kit) && Kits.numericSlots(k).size() >= 2) {
				out.put(kit, m);
			}
		}
		return out;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.kits.title");
	}

	@Override
	public int rows() {
		return 4;
	}

	@Override
	public void draw(Canvas c) {
		List<Map.Entry<String, GameMode>> kits = new ArrayList<>(editable().entrySet());
		int[] slots = c.inner();
		for (int i = 0; i < kits.size() && i < slots.length; i++) {
			String kit = kits.get(i).getKey();
			GameMode mode = kits.get(i).getValue();
			c.set(slots[i], Icon.of(Icons.mode(mode)).name(Lang.item(viewer, Icons.modeKey(mode)))
					.lore(Lang.lore(viewer, "menu.kits.kit", kit))
					.lore(Lang.lore(viewer, "menu.click_to_open")).clean().build(),
					click -> new KitEditorMenu(viewer, kit, -1).open());
		}
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
