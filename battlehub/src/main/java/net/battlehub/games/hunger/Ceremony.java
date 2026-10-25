package net.battlehub.games.hunger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.core.hunger.NightPlan;
import net.battlehub.fx.Music;
import net.battlehub.fx.Sfx;
import net.battlehub.fx.Stage;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The night's tribute to the fallen (1.47; high over the plaza while the game goes on since 1.49), as in the films:
 * the sky turns to night and the anthem plays, and high in the middle of the city, where everyone can see it, the
 * Capitol's emblem appears in gold, and under it the fallen, one after the other on the beat: a giant head with their
 * own skin, a pillar of light down to the plaza, their name and district, a funeral bell. Nobody stops: the
 * fighting, the Gamemakers and the border go on. The timing and the stage's measures are in {@link NightPlan}; the stage
 * exists only on the clients ({@link Stage}), its emblem turned towards each viewer.
 */
final class Ceremony {
	/** The emblem of the Capitol, a ring of gold round a sun with rays: G gold, Y raw gold, W quartz. */
	static final String[] EMBLEM = {
			"....GGG....",
			"..GG...GG..",
			".G...W...G.",
			".G.W.Y.W.G.",
			"G...YYY...G",
			"G.WYYYYYW.G",
			"G...YYY...G",
			".G.W.Y.W.G.",
			".G...W...G.",
			"..GG...GG..",
			"....GGG...."};

	private final HungerHost host;
	private final Tributes tributes;
	private NightPlan plan;
	private int start;
	private List<UUID> fallen = List.of();
	private final Map<UUID, View> views = new HashMap<>();
	/** Which of the fallen is up now (-1: none), so a viewer who comes in late sees them too. */
	private int showing = -1;
	private boolean emblemUp;

	/** One viewer's stage: the emblem's blocks with their place in it, the things of the one shown now. */
	private static final class View {
		final Stage stage;
		final List<int[]> emblem = new ArrayList<>();
		final List<Integer> face = new ArrayList<>();
		int head = -1, beam = -1;
		double bearing = Double.NaN;

		View(Stage stage) {
			this.stage = stage;
		}
	}

	Ceremony(HungerHost host, Tributes tributes) {
		this.host = host;
		this.tributes = tributes;
	}

	boolean active() {
		return plan != null;
	}

	/** How many fallen this night shows (0 when there is no night now). */
	int fallen() {
		return plan == null ? 0 : plan.fallen;
	}

	/** The things on a viewer's stage now (self-test). */
	int onStage(UUID viewer) {
		View v = views.get(viewer);
		return v == null ? 0 : v.stage.size();
	}

	/** Where the faces hang: high over the plaza. */
	Vec3 middle() {
		return host.plaza().add(0, NightPlan.FACE_UP, 0);
	}

	void start(List<UUID> fallenNow) {
		if (active()) end();
		fallen = new ArrayList<>(fallenNow);
		plan = new NightPlan(fallen.size());
		start = host.runTicks();
		showing = -1;
		emblemUp = false;
		for (ServerPlayer p : host.audience()) {
			// a short title: nobody stops fighting for it
			Msg.title(p, Lang.tr(p, "hg.night.title"), Lang.tr(p, fallen.isEmpty() ? "hg.night.none" : "hg.night.sub", fallen.size()), 10, 40, 15);
			Msg.send(p, "hg.night.look");
		}
		Sfx.play(host.audience(), Sfx.SEAL);
		Music.play(host.audience(), Music.ANTHEM);
		night(true);
	}

	void tick() {
		if (plan == null) return;
		int t = host.runTicks() - start;
		views.values().removeIf(v -> v.stage.viewer().hasDisconnected() || v.stage.viewer().level() != host.level());
		// everyone in the match sees it, also who comes in during it
		for (ServerPlayer p : host.audience()) {
			if (Bots.isBot(p) || p.level() != host.level() || views.containsKey(p.getUUID())) continue;
			View v = new View(new Stage(p).range(4f));
			views.put(p.getUUID(), v);
			if (emblemUp) emblem(v, true, false);
			if (showing >= 0) show(v, showing, true);
		}
		night(true);
		if (t == NightPlan.EMBLEM) {
			emblemUp = true;
			for (View v : views.values()) emblem(v, true, true);
			Sfx.play(host.audience(), Sfx.RISE);
		}
		if (t % 20 == 0) turn();
		if (plan.fallen == 0 && t == NightPlan.INTRO) {
			for (View v : views.values()) {
				v.face.add(v.stage.text(middle(), Lang.tr(v.stage.viewer(), "hg.night.nobody"), NightPlan.NAME));
			}
		}
		for (int i = 0; i < plan.fallen; i++) {
			int at = plan.faceAt(i);
			if (t == at) {
				showing = i;
				for (View v : views.values()) show(v, i, false);
				Sfx.play(host.audience(), Sfx.TOLL);
			}
			if (t == at + 1) grow(true, 12);
			if (t == at + plan.each - 14) grow(false, 12);
			if (t == at + plan.each) {
				showing = -1;
				for (View v : views.values()) hideOne(v);
			}
		}
		if (t == plan.outroAt()) {
			showing = -1;
			for (View v : views.values()) {
				hideOne(v);
				for (int[] b : v.emblem) v.stage.scale(b[0], 0f, 30);
			}
		}
		if (t >= plan.length) end();
	}

	/** The emblem in a viewer's stage, turned to face them; {@code grow}: block by block from the middle. */
	private void emblem(View v, boolean in, boolean grow) {
		BlockState gold = Ids.state("minecraft:gold_block"), raw = Ids.state("minecraft:raw_gold_block"), quartz = Ids.state("minecraft:quartz_block");
		ServerPlayer p = v.stage.viewer();
		Vec3 c = middle().add(0, NightPlan.EMBLEM_UP, 0);
		v.bearing = NightPlan.bearing(p.getX(), p.getZ(), c.x, c.z);
		int n = EMBLEM.length;
		float size = (float) (NightPlan.EMBLEM_BLOCK * 0.92);
		for (int r = 0; r < n; r++) {
			for (int k = 0; k < EMBLEM[r].length(); k++) {
				char ch = EMBLEM[r].charAt(k);
				BlockState st = ch == 'G' ? gold : ch == 'Y' ? raw : ch == 'W' ? quartz : null;
				if (st == null) continue;
				int id = v.stage.block(place(p, c, r, k), st, grow ? 0f : size);
				v.emblem.add(new int[] {id, r, k});
				if (!grow) continue;
				int delay = Math.abs(k - n / 2) + Math.abs(r - n / 2);
				net.battlehub.util.Tasks.later(1 + delay, () -> {
					if (plan != null) v.stage.scale(id, size, 14);
				});
			}
		}
	}

	/** Where block (row r, column k) of the emblem goes for a viewer: in a plane at right angles to the line to them. */
	private static Vec3 place(ServerPlayer p, Vec3 c, int r, int k) {
		double[] a = NightPlan.across(p.getX(), p.getZ(), c.x, c.z);
		int n = EMBLEM.length;
		double s = NightPlan.EMBLEM_BLOCK;
		return c.add(a[0] * (k - (n - 1) / 2.0) * s, ((n - 1) / 2.0 - r) * s, a[1] * (k - (n - 1) / 2.0) * s);
	}

	/** A viewer who walked far round the stage: their emblem swings round to face them again. */
	private void turn() {
		if (!emblemUp) return;
		Vec3 c = middle().add(0, NightPlan.EMBLEM_UP, 0);
		for (View v : views.values()) {
			ServerPlayer p = v.stage.viewer();
			double now = NightPlan.bearing(p.getX(), p.getZ(), c.x, c.z);
			if (Double.isNaN(v.bearing) || !NightPlan.turned(v.bearing, now)) continue;
			v.bearing = now;
			for (int[] b : v.emblem) v.stage.move(b[0], place(p, c, b[1], b[2]), 15);
		}
	}

	/** The {@code i}-th of the fallen on a viewer's stage: the head, the pillar of light, name and district. */
	private void show(View v, int i, boolean grown) {
		UUID id = fallen.get(i);
		String name = host.name(id);
		int district = tributes.district(id);
		Vec3 m = middle();
		hideOne(v);
		v.head = v.stage.item(m, Icon.head(name).build(), grown ? NightPlan.FACE : 0f);
		double down = m.y - host.plaza().y - NightPlan.FACE / 4;
		v.beam = v.stage.beam(host.plaza(), Ids.state("minecraft:white_stained_glass"), 1.4f, (float) down);
		v.face.add(v.stage.text(m.add(0, -NightPlan.NAME_DOWN, 0), Lang.tr(v.stage.viewer(), "hg.night.name", name), NightPlan.NAME));
		v.face.add(v.stage.text(m.add(0, -NightPlan.DISTRICT_DOWN, 0), Lang.tr(v.stage.viewer(), "hg.night.district", district), NightPlan.DISTRICT));
		if (!grown) for (int f : v.face) v.stage.scale(f, 0f, 0);
	}

	private void grow(boolean up, int ticks) {
		for (View v : views.values()) {
			v.stage.scale(v.head, up ? NightPlan.FACE : 0f, ticks);
			for (int k = 0; k < v.face.size(); k++) v.stage.scale(v.face.get(k), up ? (k == 0 ? NightPlan.NAME : NightPlan.DISTRICT) : 0f, ticks);
		}
	}

	private static void hideOne(View v) {
		v.stage.remove(v.head);
		v.stage.remove(v.beam);
		for (int f : v.face) v.stage.remove(f);
		v.face.clear();
		v.head = v.beam = -1;
	}

	/** The night's sky for everyone in the match (only on their screens), or the world's own time back. */
	private void night(boolean on) {
		for (ServerPlayer p : host.audience()) {
			if (Bots.isBot(p)) continue;
			NightSky.send(p, on);
		}
	}

	/** The night is over: the stage comes down, the sky goes back. */
	void end() {
		if (plan == null) return;
		for (View v : views.values()) v.stage.clear();
		views.clear();
		night(false);
		plan = null;
		showing = -1;
		emblemUp = false;
	}
}
