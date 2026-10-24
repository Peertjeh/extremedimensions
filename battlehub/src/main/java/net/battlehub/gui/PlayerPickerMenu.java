package net.battlehub.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.queue.Challenges;
import net.battlehub.session.Sessions;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Online players as heads; click one to challenge (duels) or invite (party games). */
public final class PlayerPickerMenu extends Screen {
	private final GameMode mode;
	private final int page;

	public PlayerPickerMenu(ServerPlayer viewer, GameMode mode) {
		this(viewer, mode, 0);
	}

	public PlayerPickerMenu(ServerPlayer viewer, GameMode mode, int page) {
		super(viewer);
		this.mode = mode;
		this.page = page;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, mode.isParty() ? "menu.picker.invite_title" : "menu.picker.title", Lang.tr(viewer, Icons.modeKey(mode)));
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
	public int refreshTicks() {
		return 40;
	}

	@Override
	public void draw(Canvas c) {
		List<ServerPlayer> others = new ArrayList<>(viewer.level().getServer().getPlayerList().getPlayers());
		others.remove(viewer);
		others.sort(Comparator.comparing(p -> p.getGameProfile().name().toLowerCase(java.util.Locale.ROOT)));
		int[] slots = c.inner();
		int pages = Math.max(1, (others.size() + slots.length - 1) / slots.length);
		int pg = Math.max(0, Math.min(page, pages - 1));
		if (others.isEmpty()) {
			c.set(22, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.picker.nobody"))
					.lore(Lang.loreLines(viewer, "menu.picker.nobody.lore")).build());
		}
		for (int i = 0; i < slots.length; i++) {
			int idx = pg * slots.length + i;
			if (idx >= others.size()) break;
			ServerPlayer target = others.get(idx);
			Sessions.State st = Sessions.state(target.getUUID());
			boolean free = st == Sessions.State.LOBBY || st == Sessions.State.QUEUED;
			Icon head = Icon.head(target).name(Lang.item(viewer, "menu.picker.player", target.getGameProfile().name()))
					.lore(Lang.lore(viewer, "menu.picker.state." + st.name().toLowerCase(java.util.Locale.ROOT)));
			if (Challenges.hasPending(viewer.getUUID(), target.getUUID())) {
				head.lore(Lang.lore(viewer, "menu.picker.pending"));
			}
			head.lore(Component.empty()).lore(Lang.lore(viewer, free
					? (mode.isParty() ? "menu.picker.click_invite" : "menu.picker.click_challenge")
					: "menu.picker.unavailable"));
			c.set(slots[i], head.build(), click -> {
				viewer.closeContainer();
				Challenges.send(viewer, target, mode);
			});
		}
		if (pg > 0) {
			c.set(48, Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.page.previous")).build(), click -> new PlayerPickerMenu(viewer, mode, pg - 1).open());
		}
		if (pg < pages - 1) {
			c.set(50, Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.page.next")).build(), click -> new PlayerPickerMenu(viewer, mode, pg + 1).open());
		}
		c.back(() -> {
			if (mode.isParty()) {
				new PartyGameMenu(viewer, mode).open();
			} else {
				new ModeMenu(viewer, mode, new ModeListMenu(viewer, mode.isRanged() ? ModeListMenu.Kind.RANGED : ModeListMenu.Kind.MELEE)).open();
			}
		});
		c.fill();
	}
}
