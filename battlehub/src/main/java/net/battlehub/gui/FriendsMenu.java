package net.battlehub.gui;

import java.util.List;
import java.util.UUID;

import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.social.Friends;
import net.battlehub.social.Groups;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Friends and your party: every friend with what they are doing (click an online friend to invite them to your
 * party, right-click a friend in a match to watch), requests to accept, and the party with a leave button.
 */
public final class FriendsMenu extends Screen {
	public FriendsMenu(ServerPlayer viewer) {
		super(viewer);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.friends.title");
	}

	@Override
	public Style.Section section() {
		return Style.Section.SOCIAL;
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
		MinecraftServer server = viewer.level().getServer();
		PlayerProfile me = PlayerStore.get(viewer);
		c.set(4, Icon.of(Items.BOOK).name(Lang.item(viewer, "menu.friends.info", me.friends.size()))
				.lore(Lang.loreLines(viewer, "menu.friends.info.lore")).build());
		List<PlayerProfile> friends = Friends.list(server, viewer);
		int[] slots = c.inner();
		int i = 0;
		for (String r : me.friendRequests) {
			if (i >= slots.length) break;
			PlayerProfile from = PlayerStore.get(UUID.fromString(r));
			c.set(slots[i++], Icon.head(from.name).name(Lang.item(viewer, "menu.friends.request", from.name))
					.lore(Lang.lore(viewer, "menu.friends.request_click")).build(), click -> {
						if (click.right()) Friends.deny(viewer, from.name);
						else Friends.accept(viewer, from.name);
						new FriendsMenu(viewer).open();
					});
		}
		for (PlayerProfile f : friends) {
			if (i >= slots.length) break;
			UUID fid = UUID.fromString(f.uuid);
			ServerPlayer online = server.getPlayerList().getPlayer(fid);
			Match m = online == null ? null : Matches.of(fid);
			Icon icon = Icon.head(f.name).name(Lang.item(viewer, online != null ? "menu.friends.online" : "menu.friends.offline", f.name))
					.lore(Friends.status(viewer, server, f));
			if (online != null) icon.lore(Lang.lore(viewer, "menu.friends.invite"));
			if (m != null && m.canWatch()) icon.lore(Lang.lore(viewer, "menu.friends.watch"));
			icon.lore(Lang.lore(viewer, "menu.friends.remove"));
			c.set(slots[i++], icon.build(), click -> {
				if (click.shift()) {
					Friends.remove(viewer, f.name);
					new FriendsMenu(viewer).open();
				} else if (click.right() && m != null && m.canWatch()) {
					viewer.closeContainer();
					net.battlehub.spectate.Spectators.join(viewer, m);
				} else if (online != null) {
					Groups.invite(viewer, online);
				}
			});
		}
		Groups.Group g = Groups.of(viewer.getUUID());
		Icon party = Icon.of(Items.CAKE).name(Lang.item(viewer, "menu.friends.party", g == null ? 1 : g.members().size(), Groups.MAX));
		if (g == null) {
			party.lore(Lang.loreLines(viewer, "menu.friends.party_none"));
		} else {
			for (UUID id : g.members()) {
				party.lore(Lang.lore(viewer, id.equals(g.leader()) ? "menu.friends.party_leader" : "menu.friends.party_member",
						PlayerStore.get(id).name));
			}
			party.lore(Component.empty()).lore(Lang.lore(viewer, "menu.friends.party_leave"));
		}
		c.set(48, party.build(), click -> {
			if (g != null) {
				Groups.leave(viewer, false);
				new FriendsMenu(viewer).open();
			}
		});
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
