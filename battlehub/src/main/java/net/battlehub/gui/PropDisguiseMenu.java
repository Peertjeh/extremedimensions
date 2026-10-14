package net.battlehub.gui;

import java.util.List;

import net.battlehub.games.prophunt.Disguise;
import net.battlehub.games.prophunt.PropHuntMatch;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Prop Hunt: pick what to look like from the blocks in prophunt.json (or right-click a block in the square). */
public final class PropDisguiseMenu extends Screen {
	private final PropHuntMatch match;

	public PropDisguiseMenu(ServerPlayer viewer, PropHuntMatch match) {
		super(viewer);
		this.match = match;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "prophunt.menu.title");
	}

	@Override
	public int rows() {
		return 6;
	}

	@Override
	public void draw(Canvas c) {
		List<String> list = match.disguiseList();
		Disguise mine = match.disguise(viewer.getUUID());
		int slot = 0;
		for (String id : list) {
			if (slot >= 45) break;
			BlockState st = Ids.state(id);
			Item item = st.getBlock().asItem();
			boolean current = mine != null && mine.state().getBlock() == st.getBlock();
			Icon icon = (item == Items.AIR ? Icon.of(Items.BARRIER) : Icon.of(item))
					.name(Lang.item(viewer, "prophunt.menu.block", Component.translatable(st.getBlock().getDescriptionId())))
					.lore(Lang.lore(viewer, current ? "prophunt.menu.current" : "prophunt.menu.click")).glint(current);
			c.set(slot++, icon.clean().build(), click -> {
				match.disguise(viewer, st);
				viewer.closeContainer();
			});
		}
		c.set(49, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.close")).build(), click -> viewer.closeContainer());
		c.fill();
	}
}
