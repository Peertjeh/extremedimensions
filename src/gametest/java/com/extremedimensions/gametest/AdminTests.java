package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.UUID;

import com.extremedimensions.admin.AdminService;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.CurseLog;
import com.extremedimensions.penalty.Penalty;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 3.27: the operators' Players screen. Every player the world knows is listed, online or not; the one picked shows in
 * full; an operator lays a stack on, lifts one off (plainly: no scar) or lifts them all, for a player online or offline.
 * Nobody else gets the data or changes anything.
 */
public class AdminTests {
	/** A player without operator rights is refused, changes nothing, and is not offered the screen. */
	@GameTest
	public void onlyOperatorsChangeAnothersCurses(GameTestHelper helper) {
		ServerPlayer asker = TestPlayers.survivor(helper, "test-asker");
		ServerPlayer target = TestPlayers.survivor(helper, "test-target");
		PlayerData d = DataStore.get(target.getUUID());
		check(helper, !AdminService.allowed(asker), "a player without operator rights may open the Players screen");
		String why = AdminService.act(asker, target.getStringUUID(), "add", Penalty.GLASS_JAW.id);
		check(helper, why != null, "a player without operator rights laid a curse on another");
		check(helper, d.owned(Penalty.GLASS_JAW) == 0, "a refused request still laid %s stacks", d.owned(Penalty.GLASS_JAW));
		String data = Sync.cursesData(asker, DataStore.get(asker.getUUID()));
		check(helper, data.endsWith("|0"), "the Curses screen offers the Players screen to a player without operator rights");
		helper.succeed();
	}

	/** Laid on up to the curse's maximum, lifted off plainly, and all lifted at once; strays are refused. */
	@GameTest
	public void anOperatorLaysOnAndLiftsOffCurses(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer target = TestPlayers.survivor(helper, "test-target");
		PlayerData d = DataStore.get(target.getUUID());
		String id = target.getStringUUID();
		Penalty pen = Penalty.GLASS_JAW;
		d.curseToasts.clear();
		check(helper, AdminService.change(server, null, id, "add", pen.id) == null, "a stack could not be laid on");
		check(helper, d.owned(pen) == 1, "laying a stack on left %s", d.owned(pen));
		CurseLog.Entry first = CurseLog.entries(d).get(0);
		check(helper, first.source() == CurseLog.Source.OPERATOR, "the curse is written down as coming from %s", first.source());
		check(helper, d.curseToasts.size() == 1, "the player is told by %s toasts, not one", d.curseToasts.size());
		for (int i = 1; i < pen.max; i++) {
			AdminService.change(server, null, id, "add", pen.id);
		}
		check(helper, d.owned(pen) == pen.max, "laid to %s of %s", d.owned(pen), pen.max);
		check(helper, "gui.extremedimensions.admin.full".equals(AdminService.change(server, null, id, "add", pen.id)), "a full curse took another stack");
		check(helper, d.owned(pen) == pen.max, "a full curse went past its maximum (%s)", d.owned(pen));

		// Lifted off plainly: one stack, and no scar or lift counted as an atonement would
		int scars = d.scars, lifted = d.lifted;
		check(helper, AdminService.change(server, null, id, "lift", pen.id) == null, "a stack could not be lifted off");
		check(helper, d.owned(pen) == pen.max - 1, "lifting a stack left %s", d.owned(pen));
		check(helper, d.scars == scars && d.lifted == lifted, "a stack lifted by an operator counted as atoned (scars %s, lifted %s)", d.scars, d.lifted);
		check(helper, "gui.extremedimensions.admin.none".equals(AdminService.change(server, null, id, "lift", Penalty.CRACKED_ARMOR.id)),
				"a curse not carried was lifted");

		// Every curse at once
		d.addStacks(Penalty.CRACKED_ARMOR, 2);
		d.addStacks(Penalty.MIRROR_WOUND, 1);
		check(helper, AdminService.change(server, null, id, "clear", "") == null, "every curse could not be lifted");
		check(helper, d.curses.isEmpty(), "curses were left after lifting them all: %s", d.curses);
		check(helper, "gui.extremedimensions.admin.none_at_all".equals(AdminService.change(server, null, id, "clear", "")), "nothing was lifted twice");

		// Strays: a player the world never saw (and no data is made for them), a curse that does not exist, an unknown action
		UUID stranger = UUID.randomUUID();
		check(helper, "gui.extremedimensions.admin.no_player".equals(AdminService.change(server, null, stranger.toString(), "add", pen.id)),
				"a stranger was cursed");
		check(helper, !DataStore.playerIds().contains(stranger.toString()), "asking after a stranger made data for them");
		check(helper, "gui.extremedimensions.admin.no_player".equals(AdminService.change(server, null, "not-a-uuid", "add", pen.id)), "a broken id was taken");
		check(helper, "gui.extremedimensions.admin.no_curse".equals(AdminService.change(server, null, id, "add", "no_such_curse")), "an unknown curse was laid");
		check(helper, "gui.extremedimensions.admin.no_action".equals(AdminService.change(server, null, id, "bless", pen.id)), "an unknown action was done");
		helper.succeed();
	}

	/**
	 * 3.27.2: silently, the stacks come and go all the same, but the player is not told: nothing written in their newest
	 * curses, no toast waiting, and a lifted stack leaves no scar either way.
	 */
	@GameTest
	public void anOperatorCanLayAndLiftCursesSilently(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer target = TestPlayers.survivor(helper, "test-quiet");
		PlayerData d = DataStore.get(target.getUUID());
		String id = target.getStringUUID();
		Penalty pen = Penalty.GLASS_JAW;
		d.curseToasts.clear();
		int logged = CurseLog.entries(d).size();
		check(helper, AdminService.change(server, null, id, "add", pen.id, true) == null, "a stack could not be laid on silently");
		check(helper, d.owned(pen) == 1, "laying a stack on silently left %s", d.owned(pen));
		check(helper, CurseLog.entries(d).size() == logged, "a silent stack was written in the player's newest curses");
		check(helper, d.curseToasts.isEmpty(), "a silent stack waits to be told as a toast (%s)", d.curseToasts.size());
		// Told again once silent is off: the next stack is written down and toasted
		check(helper, AdminService.change(server, null, id, "add", pen.id, false) == null && d.curseToasts.size() == 1,
				"with silent off the player is not told (%s toasts)", d.curseToasts.size());
		int scars = d.scars;
		check(helper, AdminService.change(server, null, id, "lift", pen.id, true) == null, "a stack could not be lifted silently");
		check(helper, d.owned(pen) == 1 && d.scars == scars, "lifting silently left %s stacks and %s scars (was %s)", d.owned(pen), d.scars, scars);
		d.addStacks(Penalty.CRACKED_ARMOR, 2);
		check(helper, AdminService.change(server, null, id, "clear", "", true) == null && d.curses.isEmpty(), "every curse could not be lifted silently: %s",
				d.curses);
		helper.succeed();
	}

	/** The list holds every player (online first, offline after, the asker among them); the one picked shows in full. */
	@GameTest
	public void thePlayersScreenListsEveryoneAndThePickedPlayer(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer viewer = TestPlayers.survivor(helper, "test-viewer");
		ServerPlayer target = TestPlayers.survivor(helper, "test-target");
		DataStore.get(target.getUUID()).addStacks(Penalty.GLASS_JAW, 2);
		UUID gone = UUID.randomUUID();
		PlayerData away = DataStore.get(gone);
		away.name = "test-offline";
		away.addStacks(Penalty.CRACKED_ARMOR, 1);

		JsonObject o = JsonParser.parseString(AdminService.data(server, viewer.getUUID(), target.getStringUUID())).getAsJsonObject();
		check(helper, viewer.getStringUUID().equals(o.get("you").getAsString()), "the asker is not marked");
		int targetAt = -1, goneAt = -1, viewerAt = -1, i = 0;
		for (JsonElement e : o.getAsJsonArray("players")) {
			JsonObject p = e.getAsJsonObject();
			String pid = p.get("id").getAsString();
			if (pid.equals(target.getStringUUID())) {
				targetAt = i;
				check(helper, p.get("on").getAsBoolean() && p.get("n").getAsInt() == 2, "the online player is listed as %s", p);
			} else if (pid.equals(gone.toString())) {
				goneAt = i;
				check(helper, !p.get("on").getAsBoolean() && p.get("n").getAsInt() == 1 && "test-offline".equals(p.get("name").getAsString()),
						"the offline player is listed as %s", p);
			} else if (pid.equals(viewer.getStringUUID())) {
				viewerAt = i;
			}
			i++;
		}
		check(helper, targetAt >= 0 && goneAt >= 0 && viewerAt >= 0, "not everyone is listed (target %s, offline %s, asker %s)", targetAt, goneAt, viewerAt);
		check(helper, targetAt < goneAt, "an offline player is listed before one online");
		JsonObject sel = o.getAsJsonObject("sel");
		check(helper, target.getStringUUID().equals(sel.get("id").getAsString()), "the player picked is not the one shown");
		check(helper, sel.get("on").getAsBoolean() && sel.has("world"), "the player picked shows no world");
		check(helper, sel.getAsJsonObject("curses").getAsJsonArray(Penalty.GLASS_JAW.id).get(0).getAsInt() == 2, "the player picked carries the wrong stacks");
		check(helper, sel.getAsJsonObject("curses").size() == Penalty.values().length, "not every curse is shown for the player picked");

		// A stray pick shows the asker; an offline player can be picked and changed too
		JsonObject stray = JsonParser.parseString(AdminService.data(server, viewer.getUUID(), UUID.randomUUID().toString())).getAsJsonObject();
		check(helper, viewer.getStringUUID().equals(stray.getAsJsonObject("sel").get("id").getAsString()), "a stray pick did not fall back to the asker");
		JsonObject off = JsonParser.parseString(AdminService.data(server, viewer.getUUID(), gone.toString())).getAsJsonObject();
		check(helper, !off.getAsJsonObject("sel").get("on").getAsBoolean(), "the offline player shows as online");
		check(helper, AdminService.change(server, null, gone.toString(), "lift", Penalty.CRACKED_ARMOR.id) == null, "an offline player's curse could not be lifted");
		check(helper, away.owned(Penalty.CRACKED_ARMOR) == 0, "the offline player still carries %s stacks", away.owned(Penalty.CRACKED_ARMOR));
		helper.succeed();
	}
}
