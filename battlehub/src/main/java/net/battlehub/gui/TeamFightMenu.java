package net.battlehub.gui;

import net.battlehub.core.GameMode;
import net.battlehub.core.SideQueue;
import net.battlehub.lang.Lang;
import net.battlehub.queue.Queues;
import net.battlehub.queue.TeamFights;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** 1v2 / 1v3 with one kit: queue to play alone, in the team, or whichever side is needed. */
public final class TeamFightMenu extends Screen {
	private final GameMode mode;
	private final GameMode kit;

	public TeamFightMenu(ServerPlayer viewer, GameMode mode, GameMode kit) {
		super(viewer);
		this.mode = mode;
		this.kit = kit;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.teamfight.title", TeamFights.label(viewer, mode, kit));
	}

	@Override
	public int rows() {
		return 3;
	}

	@Override
	public int refreshTicks() {
		return 20;
	}

	@Override
	public void draw(Canvas c) {
		SideQueue.Side mine = TeamFights.sideOf(viewer.getUUID(), mode, kit);
		SideQueue.Side[] sides = {SideQueue.Side.SOLO, SideQueue.Side.ANY, SideQueue.Side.TEAM};
		int[] slots = {11, 13, 15};
		for (int i = 0; i < sides.length; i++) {
			SideQueue.Side side = sides[i];
			Icon icon = switch (side) {
				case SOLO -> Icon.of(Icons.mode(kit));
				case TEAM -> Icon.of(Items.PLAYER_HEAD).count(mode.opponents());
				case ANY -> Icon.of("minecraft:white_concrete");
			};
			boolean chosen = side == mine;
			icon.name(Lang.item(viewer, "teamfight.side." + side.id()))
					.lore(Lang.loreLines(viewer, "menu.teamfight." + side.id() + ".lore", mode.opponents()))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, "menu.pvp.waiting", TeamFights.count(mode, kit, side)))
					.lore(Lang.lore(viewer, chosen ? "menu.teamfight.waiting_here" : "menu.teamfight.click"))
					.glint(chosen);
			c.set(slots[i], icon.clean().build(), click -> {
				TeamFights.join(viewer, mode, kit, side);
				new TeamFightMenu(viewer, mode, kit).open();
			});
		}
		if (mine != null) {
			c.set(4, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.teamfight.leave")).build(), click -> {
				Queues.leaveAll(viewer);
				new TeamFightMenu(viewer, mode, kit).open();
			});
		}
		c.back(() -> new PvpKitMenu(viewer, mode).open());
		c.fill();
	}
}
