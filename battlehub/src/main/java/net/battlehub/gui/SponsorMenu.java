package net.battlehub.gui;

import java.util.List;
import java.util.UUID;

import net.battlehub.data.PlayerStore;
import net.battlehub.games.HungerGamesMatch;
import net.battlehub.games.hunger.Sponsors;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Sponsoring in the Hunger Games (watchers and the fallen): first the living tributes, then what to send with your
 * coins. Three gifts per match.
 */
public final class SponsorMenu extends Screen {
	private final HungerGamesMatch match;
	private final UUID tribute;

	public SponsorMenu(ServerPlayer viewer, HungerGamesMatch match, UUID tribute) {
		super(viewer);
		this.match = match;
		this.tribute = tribute;
	}

	@Override
	public Component title() {
		return tribute == null ? Lang.tr(viewer, "menu.sponsor.title") : Lang.tr(viewer, "menu.sponsor.gift_title", match.name(tribute));
	}

	@Override
	public int rows() {
		return tribute == null ? 4 : 3;
	}

	@Override
	public int refreshTicks() {
		return 20;
	}

	@Override
	public void draw(Canvas c) {
		Sponsors s = match.capitol().sponsors();
		long coins = PlayerStore.get(viewer).coins;
		c.set(4, Icon.of(Items.GOLD_INGOT).name(Lang.item(viewer, "menu.sponsor.coins", coins))
				.lore(Lang.lore(viewer, "menu.sponsor.left", s.giftsLeft(viewer))).build());
		if (tribute == null) {
			List<ServerPlayer> alive = match.tributes();
			int[] slots = c.inner();
			for (int i = 0; i < alive.size() && i < slots.length; i++) {
				ServerPlayer t = alive.get(i);
				var caps = match.capitol();
				Icon icon = Icon.of(Items.PLAYER_HEAD).name(Component.literal(match.name(t.getUUID())).withStyle(st -> st.withItalic(false)))
						.lore(Lang.lore(viewer, "menu.sponsor.district", caps.tributes().district(t.getUUID()), caps.tributes().score(t.getUUID())))
						.lore(Lang.lore(viewer, "menu.sponsor.health", Math.round(t.getHealth()), Math.round(t.getMaxHealth())))
						.lore(Lang.lore(viewer, "menu.sponsor.kills", match.kills(t.getUUID())))
						.lore(Component.empty())
						.lore(Lang.lore(viewer, "menu.sponsor.pick"));
				c.set(slots[i], icon.build(), click -> new SponsorMenu(viewer, match, t.getUUID()).open());
			}
			return;
		}
		int[] at = {10, 12, 14, 16};
		Sponsors.Gift[] gifts = Sponsors.Gift.values();
		for (int i = 0; i < gifts.length; i++) {
			Sponsors.Gift g = gifts[i];
			String id = g.name().toLowerCase(java.util.Locale.ROOT);
			Icon icon = Icon.of(g.icon).name(Lang.item(viewer, "hg.gift." + id))
					.lore(Lang.loreLines(viewer, "menu.sponsor.gift." + id))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, coins >= g.cost ? "menu.sponsor.buy" : "menu.sponsor.too_expensive", g.cost))
					.clean();
			c.set(at[i], icon.build(), click -> {
				if (s.sponsor(viewer, tribute, g)) viewer.closeContainer();
			});
		}
		c.set(22, Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.back")).build(), click -> new SponsorMenu(viewer, match, null).open());
	}
}
