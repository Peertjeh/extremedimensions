package com.extremedimensions.client.hud;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.server.Dims;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

/**
 * After a Collapse (or a throw out of the Seam), once the player is home and has chosen what it cost: what happened, on
 * a card in the Seam briefing's look (see {@link SeamBriefing#card}). A title and four lines: how long they were away,
 * how far out they went, why the charge ran out faster, and what it cost. It waits while the screen is still dark from
 * the Collapse, fades in, stays long enough to read and fades out; it never takes a click, but it can be put away
 * (3.55.0): on a key, or by moving once it has shown three seconds (see PutAway). The server sends it
 * ({@code collapse_recap}, see ExpeditionRecord).
 */
public class CollapseRecap implements HudElement {
	/** When it starts to show after it came (or after the screen came back from the dark), how long it shows, and its fades. */
	static final long DELAY_MS = 900, SHOW_MS = 14000, FADE_IN_MS = 450, FADE_OUT_MS = 900;
	/** How long the Overworld takes to fade back in after a Collapse (the charge HUD's fade). */
	private static final long HOME_FADE_MS = 2200;
	/** Below this (a hundredth over the base rate) the drain counts as no faster at all. */
	private static final int FASTER = 105;

	private static ModPackets.RecapPayload recap = null;
	private static long at = 0;

	/** The server sent a recap: it shows once the player is home. */
	public static void show(ModPackets.RecapPayload r) {
		recap = r;
		at = System.currentTimeMillis();
	}

	/** Forgotten on leaving a world. */
	public static void reset() {
		recap = null;
		at = 0;
	}

	/** Milliseconds it has been showing (negative while it waits, and when there is none). */
	private static long elapsed() {
		if (recap == null || ClientState.away() || ClientState.inCollapse()) {
			return -1;
		}
		long start = at + DELAY_MS;
		if (ClientState.homeAt > ClientState.collapseAt) {
			start = Math.max(start, ClientState.homeAt + HOME_FADE_MS);
		}
		return System.currentTimeMillis() - start;
	}

	/** Whether the card was put away, and fades out (3.55.0); known by when it came. */
	private static final PutAway.Card CARD = new PutAway.Card();

	/** Whether the card is on the screen now (the guide card steps aside for it). */
	public static boolean showing() {
		long t = elapsed();
		return t >= 0 && t <= SHOW_MS && !SeamBriefing.showing() && !CARD.gone(at, System.currentTimeMillis());
	}

	/** A tick while it shows: the key, or a step after three seconds, puts it away. */
	static void watch(long now, boolean key, net.minecraft.client.player.LocalPlayer player) {
		CARD.watch(at, now, key, player);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		long t = elapsed();
		if (t < 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (t > SHOW_MS || CARD.gone(at, now)) {
			reset();
			return;
		}
		// (a briefing on arrival somewhere new comes first; the recap waits behind it)
		if (SeamBriefing.showing()) {
			return;
		}
		double alpha = Math.min(Ui.ease(t / (double) FADE_IN_MS), Ui.clamp01((SHOW_MS - t) / (double) FADE_OUT_MS)) * CARD.fade(at, now);
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		float scale = ClientOptions.hudScale() / 100f;
		int sw = Math.round(g.guiWidth() / scale), sh = Math.round(g.guiHeight() / scale);
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		draw(g, font, sw, sh, alpha, recap);
		g.pose().popMatrix();
	}

	private static void draw(GuiGraphicsExtractor g, Font font, int sw, int sh, double alpha, ModPackets.RecapPayload r) {
		boolean seam = Dims.SEAM.equals(r.realm());
		int accent = seam ? Ui.SOUL : Dims.END.equals(r.realm()) ? Ui.VOID : Ui.EMBER;
		String head = Ui.tr("gui.extremedimensions.recap.title." + (seam ? Dims.SEAM : Dims.END.equals(r.realm()) ? Dims.END : Dims.NETHER));
		List<SeamBriefing.Row> rows = new ArrayList<>();
		rows.add(new SeamBriefing.Row(Ui.EMBER, Ui.tr("gui.extremedimensions.recap.away"),
				Ui.tr(seam ? "gui.extremedimensions.recap.away.seam" : "gui.extremedimensions.recap.away.text", Ui.time(r.seconds()), Ui.time(r.charge()))));
		rows.add(new SeamBriefing.Row(Ui.SOUL, Ui.tr("gui.extremedimensions.recap.far"),
				Ui.tr(seam ? "gui.extremedimensions.recap.far.seam" : "gui.extremedimensions.recap.far.text", r.farthest())));
		rows.add(new SeamBriefing.Row(Ui.WARN, Ui.tr("gui.extremedimensions.recap.why"), why(r, seam)));
		rows.add(new SeamBriefing.Row(Ui.BLOOD, Ui.tr("gui.extremedimensions.recap.cost"), cost(r, seam)));
		SeamBriefing.card(g, font, sw, sh, alpha, accent, head, null, rows, null, true);
	}

	/** Why the charge (or the thread) ran out faster: the distance, or the rest (curses, the wound, the world), or nothing. */
	private static String why(ModPackets.RecapPayload r, boolean seam) {
		if (seam) {
			if ("fell".equals(r.outcome())) {
				return Ui.tr("gui.extremedimensions.recap.why.fell");
			}
			return r.drainX100() >= FASTER ? Ui.tr("gui.extremedimensions.recap.why.fray", times(r.drainX100()))
					: Ui.tr("gui.extremedimensions.recap.why.thread");
		}
		if (r.distanceX100() >= FASTER) {
			return Ui.tr("gui.extremedimensions.recap.why.distance", times(r.distanceX100()), times(r.drainX100()));
		}
		return r.drainX100() >= FASTER ? Ui.tr("gui.extremedimensions.recap.why.drain", times(r.drainX100()))
				: Ui.tr("gui.extremedimensions.recap.why.base");
	}

	/** What it cost: the curses (by name, with their stacks), the Tether (lost in a Collapse), what the Seam kept, the atonement undone. */
	private static String cost(ModPackets.RecapPayload r, boolean seam) {
		List<String> parts = new ArrayList<>();
		for (String part : r.curses().split(",")) {
			int colon = part.indexOf(':');
			Penalty pen = Penalty.byId(colon < 0 ? part : part.substring(0, colon));
			if (pen == null) {
				continue;
			}
			int stacks = 1;
			try {
				stacks = colon < 0 ? 1 : Integer.parseInt(part.substring(colon + 1));
			} catch (NumberFormatException e) {
				// (one stack, then)
			}
			String name = Ui.tr(pen.nameKey());
			parts.add(stacks > 1 ? Ui.tr("gui.extremedimensions.recap.cost.stacks", name, stacks) : name);
		}
		if (!seam) {
			parts.add(Ui.tr("gui.extremedimensions.recap.cost.tether"));
		}
		if (r.silk() > 0) {
			parts.add(Ui.tr("gui.extremedimensions.recap.cost.silk", r.silk()));
		}
		if (r.fragments() > 0) {
			parts.add(Ui.tr("gui.extremedimensions.recap.cost.fragments", r.fragments()));
		}
		if (r.atonement() > 0) {
			parts.add(Ui.tr("gui.extremedimensions.recap.cost.atonement", r.atonement()));
		}
		String all = parts.isEmpty() ? Ui.tr("gui.extremedimensions.recap.cost.none") : String.join(", ", parts);
		// (the first word of the line is a sentence's first)
		return all.isEmpty() ? all : Character.toUpperCase(all.charAt(0)) + all.substring(1);
	}

	/** A hundredth of a rate as the HUD shows a drain ("2.4"). */
	private static String times(int x100) {
		return String.format("%.1f", x100 / 100.0);
	}
}
