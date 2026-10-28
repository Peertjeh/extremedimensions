package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import java.util.Map;

import com.extremedimensions.compat.TabPlaceholders;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.tapestry.Unweaving;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The tablist placeholders: what they say of a player and of the world, the badge's colour, the snapshot the tablist
 * reads (made anew on the server's thread, forgetting those who left), and nothing at all when switched off. (Reading
 * them through Text Placeholder API itself is the compatibility job's part, see ClientCompat.)
 */
public class PlaceholderTests {
	private static String text(Map<String, Component> m, String name) {
		Component c = m.get(name);
		return c == null ? null : c.getString();
	}

	@GameTest
	public void theTablistReadsAPlayersCursesAndWhereTheyAre(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		Map<String, Component> fresh = TabPlaceholders.values(p);
		check(helper, fresh.keySet().containsAll(TabPlaceholders.PLAYER) && TabPlaceholders.PLAYER.containsAll(fresh.keySet()),
				"the placeholders worked out are %s, not the ones offered", fresh.keySet());
		check(helper, "0".equals(text(fresh, "curses")) && text(fresh, "badge").isEmpty(), "a player without curses reads %s, badge '%s'",
				text(fresh, "curses"), text(fresh, "badge"));

		UnweavingTests.fill(d, Penalty.Group.BEYOND);
		Penalty abyssal = null;
		for (Penalty pen : Penalty.values()) {
			if (pen.abyssal && pen.max > 0) {
				abyssal = pen;
				break;
			}
		}
		d.addStacks(abyssal, 1);
		d.scars = 3;
		d.collapses = 7;
		Map<String, Component> m = TabPlaceholders.values(p);
		int beyond = Unweaving.most(Penalty.Group.BEYOND);
		check(helper, String.valueOf(beyond + 1).equals(text(m, "curses")), "curses reads %s, not %s", text(m, "curses"), beyond + 1);
		check(helper, String.valueOf(beyond).equals(text(m, "curses_beyond")) && "0".equals(text(m, "curses_overworld")),
				"the sets read %s beyond and %s overworld", text(m, "curses_beyond"), text(m, "curses_overworld"));
		check(helper, "1".equals(text(m, "abyssal")) && "3".equals(text(m, "scars")) && "7".equals(text(m, "collapses")),
				"abyssal, scars and collapses read %s, %s, %s", text(m, "abyssal"), text(m, "scars"), text(m, "collapses"));
		check(helper, ("☠" + (beyond + 1) + " ✦").equals(text(m, "badge")), "the badge reads '%s'", text(m, "badge"));
		Component badge = m.get("badge");
		check(helper, badge.getStyle().getColor() != null && badge.getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(
				beyond + 1 >= 60 ? ChatFormatting.DARK_RED : beyond + 1 >= 30 ? ChatFormatting.RED : ChatFormatting.YELLOW)),
				"the badge of %s curses is coloured %s", beyond + 1, badge.getStyle().getColor());
		check(helper, "Overworld".equals(text(m, "realm")) && "At home".equals(text(m, "status")) && text(m, "time_left").isEmpty(),
				"a player at home reads realm %s, status %s, time left '%s'", text(m, "realm"), text(m, "status"), text(m, "time_left"));
		helper.succeed();
	}

	@GameTest
	public void theWorldsPlaceholdersAndTheSnapshot(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		int before = Config.PLACEHOLDERS;
		try {
			Config.PLACEHOLDERS = 1;
			Map<String, Component> w = TabPlaceholders.values(helper.getLevel().getServer());
			check(helper, w.keySet().containsAll(TabPlaceholders.WORLD), "the world's placeholders are %s", w.keySet());
			check(helper, java.util.List.of("Easy", "Normal", "Hard", "Extreme", "Custom").contains(text(w, "difficulty")),
					"difficulty reads %s", text(w, "difficulty"));
			check(helper, java.util.List.of("Mended", "Severed", "Unended").contains(text(w, "ending")), "ending reads %s", text(w, "ending"));
			check(helper, text(w, "tapestry").matches("[0-6]/6"), "tapestry reads %s", text(w, "tapestry"));

			// The snapshot: made anew on the server's thread, read by name; a player who left is forgotten
			DataStore.get(p.getUUID()).collapses = 4;
			TabPlaceholders.refresh(helper.getLevel().getServer());
			check(helper, "4".equals(TabPlaceholders.player(p.getUUID(), "collapses").getString()), "the snapshot reads %s collapses",
					TabPlaceholders.player(p.getUUID(), "collapses").getString());
			check(helper, TabPlaceholders.world("tapestry").getString().endsWith("/6"), "the world's snapshot reads %s",
					TabPlaceholders.world("tapestry").getString());
			check(helper, TabPlaceholders.player(java.util.UUID.randomUUID(), "curses").getString().isEmpty(), "a stranger has curses in the snapshot");
			check(helper, TabPlaceholders.player(p.getUUID(), "no_such_thing").getString().isEmpty(), "an unknown placeholder reads something");

			// Switched off, every placeholder stays empty
			Config.PLACEHOLDERS = 0;
			check(helper, TabPlaceholders.player(p.getUUID(), "collapses").getString().isEmpty() && TabPlaceholders.world("tapestry").getString().isEmpty(),
					"the placeholders still read with placeholders = 0");
			helper.succeed();
		} finally {
			Config.PLACEHOLDERS = before;
		}
	}

	@GameTest
	public void timeReadsAsAClock(GameTestHelper helper) {
		check(helper, "0:00".equals(TabPlaceholders.clock(0)) && "1:05".equals(TabPlaceholders.clock(64.2)) && "59:59".equals(TabPlaceholders.clock(3599))
				&& "1:00:00".equals(TabPlaceholders.clock(3600)), "the clock reads %s, %s, %s, %s", TabPlaceholders.clock(0), TabPlaceholders.clock(64.2),
				TabPlaceholders.clock(3599), TabPlaceholders.clock(3600));
		helper.succeed();
	}
}
