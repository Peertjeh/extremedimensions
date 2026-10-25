package net.battlehub.games.hunger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.core.Orbit;
import net.battlehub.core.hunger.NightPlan;
import net.battlehub.fx.Cinematics;
import net.battlehub.fx.Music;
import net.battlehub.fx.Stage;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The night's tribute to the fallen (1.47), as in the films: the game holds still (nobody can hurt or be hurt, the
 * tributes stand where they are, the Gamemakers wait), the sky turns to night, the camera of everyone in the match
 * rises from their eyes and looks up at a stage over their own spot: the Capitol's emblem in gold, and under it the
 * fallen, one after the other on the beat of the anthem: a giant head with their own skin, a pillar of light, their name
 * and district, and a cannon shot. Then the camera comes back down and the game goes on. The timing and the camera's
 * path are in {@link NightPlan}; the stage exists only on each viewer's own client ({@link Stage}).
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
	/** How big a head of the fallen grows (about five blocks across). */
	static final float FACE = 10f;

	private final HungerHost host;
	private final Tributes tributes;
	private NightPlan plan;
	private int start;
	private List<UUID> fallen = List.of();
	private final Map<UUID, View> views = new HashMap<>();
	private final Map<UUID, Vec3> frozen = new HashMap<>();

	/** One viewer's stage: where it hangs, the emblem's blocks, the things of the one shown now. */
	private static final class View {
		final Stage stage;
		final Vec3 sky;
		final List<Integer> emblem = new ArrayList<>();
		final List<Integer> face = new ArrayList<>();
		int head = -1, beam = -1;

		View(Stage stage, Vec3 sky) {
			this.stage = stage;
			this.sky = sky;
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

	void start(List<UUID> fallenNow) {
		if (active()) end();
		fallen = new ArrayList<>(fallenNow);
		plan = new NightPlan(fallen.size());
		start = host.runTicks();
		for (ServerPlayer p : host.tributes()) {
			frozen.put(p.getUUID(), p.position());
			p.setDeltaMovement(Vec3.ZERO);
		}
		for (ServerPlayer p : host.audience()) {
			Msg.title(p, Lang.tr(p, "hg.night.title"), Lang.tr(p, fallen.isEmpty() ? "hg.night.none" : "hg.night.sub", fallen.size()), 20, 60, 20);
			Ids.effect("minecraft:blindness").ifPresent(h -> p.addEffect(new MobEffectInstance(h, 30, 0, false, false, false)));
			if (Bots.isBot(p)) continue;
			float yaw = p.getYRot();
			double[] sky = NightPlan.sky(p.getX(), p.getY(), p.getZ(), yaw);
			views.put(p.getUUID(), new View(new Stage(p), new Vec3(sky[0], sky[1], sky[2])));
			double x = p.getX(), y = p.getEyeY(), z = p.getZ();
			NightPlan np = plan;
			Cinematics.shot(p, host.matchId(), host.level(), np.length, t -> np.camera(x, y, z, yaw, (int) Math.round(t * np.length)));
		}
		Music.play(host.audience(), Music.ANTHEM);
		night(true);
	}

	void tick() {
		if (plan == null) return;
		int t = host.runTicks() - start;
		views.values().removeIf(v -> v.stage.viewer().hasDisconnected());
		// everyone stays where they stood
		for (ServerPlayer p : host.tributes()) {
			Vec3 at = frozen.get(p.getUUID());
			if (at == null) continue;
			if (p.position().distanceToSqr(at) > 0.04) {
				p.teleportTo(host.level(), at.x, at.y, at.z, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
			}
			p.setDeltaMovement(Vec3.ZERO);
		}
		night(true);
		if (t == NightPlan.EMBLEM) emblem(true);
		if (plan.fallen == 0 && t == NightPlan.INTRO) {
			for (View v : views.values()) {
				v.face.add(v.stage.text(v.sky.add(0, -2, 0), Lang.tr(v.stage.viewer(), "hg.night.nobody"), 4f));
			}
		}
		for (int i = 0; i < plan.fallen; i++) {
			int at = plan.faceAt(i);
			if (t == at) show(i);
			if (t == at + 1) grow(FACE, 12);
			if (t == at + plan.each - 14) grow(0f, 12);
			if (t == at + plan.each) hide();
		}
		if (t == plan.outroAt()) {
			hide();
			emblem(false);
		}
		if (t >= plan.length) end();
	}

	/** The emblem grows out of nothing (or shrinks away), block by block from the middle. */
	private void emblem(boolean in) {
		BlockState gold = Ids.state("minecraft:gold_block"), raw = Ids.state("minecraft:raw_gold_block"), quartz = Ids.state("minecraft:quartz_block");
		for (View v : views.values()) {
			if (in) {
				Vec3 c = v.sky.add(0, 9, 0);
				Vec3 toViewer = v.stage.viewer().position().subtract(c);
				Vec3 right = new Vec3(toViewer.z, 0, -toViewer.x).normalize();
				int n = EMBLEM.length;
				for (int r = 0; r < n; r++) {
					for (int k = 0; k < EMBLEM[r].length(); k++) {
						char ch = EMBLEM[r].charAt(k);
						BlockState st = ch == 'G' ? gold : ch == 'Y' ? raw : ch == 'W' ? quartz : null;
						if (st == null) continue;
						Vec3 at = c.add(right.scale(k - (n - 1) / 2.0)).add(0, (n - 1) / 2.0 - r, 0);
						int id = v.stage.block(at, st, 0f);
						v.emblem.add(id);
						int dist = Math.abs(k - n / 2) + Math.abs(r - n / 2);
						int delay = dist;
						net.battlehub.util.Tasks.later(1 + delay, () -> {
							if (plan != null) v.stage.scale(id, 0.92f, 14);
						});
					}
				}
			} else {
				for (int id : v.emblem) v.stage.scale(id, 0f, 30);
			}
		}
		if (in) {
			for (ServerPlayer p : host.audience()) sound(p, "minecraft:block.beacon.power_select", 1f, 0.6f);
		}
	}

	/** The {@code i}-th of the fallen appears (still small): the head, the pillar of light, name and district. */
	private void show(int i) {
		UUID id = fallen.get(i);
		String name = host.name(id);
		int district = tributes.district(id);
		for (View v : views.values()) {
			hideOne(v);
			v.head = v.stage.item(v.sky, Icon.head(name).build(), 0f);
			v.beam = v.stage.beam(v.sky.add(0, -30, 0), Ids.state("minecraft:white_stained_glass"), 0.9f, 30f);
			v.face.add(v.stage.text(v.sky.add(0, -4.8, 0), Lang.tr(v.stage.viewer(), "hg.night.name", name), 4.5f));
			v.face.add(v.stage.text(v.sky.add(0, -6.2, 0), Lang.tr(v.stage.viewer(), "hg.night.district", district), 2.8f));
			for (int f : v.face) v.stage.scale(f, 0f, 0);
		}
		for (ServerPlayer p : host.audience()) {
			sound(p, "minecraft:entity.generic.explode", 0.9f, 0.5f);
			sound(p, "minecraft:entity.firework_rocket.blast_far", 1f, 0.6f);
		}
	}

	private void grow(float scale, int ticks) {
		for (View v : views.values()) {
			v.stage.scale(v.head, scale, ticks);
			for (int k = 0; k < v.face.size(); k++) v.stage.scale(v.face.get(k), scale == 0 ? 0 : (k == 0 ? 4.5f : 2.8f), ticks);
		}
	}

	private void hide() {
		for (View v : views.values()) hideOne(v);
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

	/** The night is over: everything down, the cameras back, the game goes on. */
	void end() {
		if (plan == null) return;
		for (View v : views.values()) {
			v.stage.clear();
			if (!v.stage.viewer().hasDisconnected()) Cinematics.stop(v.stage.viewer());
		}
		views.clear();
		frozen.clear();
		night(false);
		plan = null;
	}

	private static void sound(ServerPlayer p, String id, float volume, float pitch) {
		Msg.sound(p, Holder.direct(SoundEvent.createVariableRangeEvent(Ids.id(id))), volume, pitch);
	}

	/** The camera path of a viewer (for the self-test: where it is at a tick). */
	Orbit.Pose cameraAt(double x, double y, double z, float yaw, int tick) {
		return plan == null ? null : plan.camera(x, y, z, yaw, tick);
	}
}
