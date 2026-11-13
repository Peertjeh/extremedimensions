package com.extremedimensions.client.hud;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.expedition.RecapText;
import com.extremedimensions.network.ModPackets;
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
 * the Collapse, fades in, stays long enough to read and fades out; it never takes a click. The server sends it
 * ({@code collapse_recap}, see ExpeditionRecord).
 */
public class CollapseRecap implements HudElement {
	/** When it starts to show after it came (or after the screen came back from the dark), how long it shows, and its fades. */
	static final long DELAY_MS = 900, SHOW_MS = 14000, FADE_IN_MS = 450, FADE_OUT_MS = 900;
	/** How long the Overworld takes to fade back in after a Collapse (the charge HUD's fade). */
	private static final long HOME_FADE_MS = 2200;

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

	/** Whether the card is on the screen now (the guide card steps aside for it). */
	public static boolean showing() {
		long t = elapsed();
		return t >= 0 && t <= SHOW_MS && !SeamBriefing.showing();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		long t = elapsed();
		if (t < 0) {
			return;
		}
		if (t > SHOW_MS) {
			reset();
			return;
		}
		// (a briefing on arrival somewhere new comes first; the recap waits behind it)
		if (SeamBriefing.showing()) {
			return;
		}
		double alpha = Math.min(Ui.ease(t / (double) FADE_IN_MS), Ui.clamp01((SHOW_MS - t) / (double) FADE_OUT_MS));
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
		boolean seam = RecapText.seam(r);
		int accent = seam ? Ui.SOUL : Dims.END.equals(r.realm()) ? Ui.VOID : Ui.EMBER;
		List<SeamBriefing.Row> rows = new ArrayList<>();
		rows.add(new SeamBriefing.Row(Ui.EMBER, Ui.tr("gui.extremedimensions.recap.away"), tr(RecapText.away(r))));
		rows.add(new SeamBriefing.Row(Ui.SOUL, Ui.tr("gui.extremedimensions.recap.far"), tr(RecapText.far(r))));
		rows.add(new SeamBriefing.Row(Ui.WARN, Ui.tr("gui.extremedimensions.recap.why"), tr(RecapText.why(r))));
		rows.add(new SeamBriefing.Row(Ui.BLOOD, Ui.tr("gui.extremedimensions.recap.cost"), cost(r)));
		SeamBriefing.card(g, font, sw, sh, alpha, accent, tr(RecapText.title(r)), null, rows, null, true);
	}

	/**
	 * A recap's words in the player's language (the words are shared with the journal and the chat since 3.55.0, see
	 * RecapText), its arguments translated where they are words of their own.
	 */
	public static String tr(RecapText.Text t) {
		Object[] args = new Object[t.args().length];
		for (int i = 0; i < args.length; i++) {
			args[i] = t.args()[i] instanceof RecapText.Text inner ? tr(inner) : t.args()[i];
		}
		return Ui.tr(t.key(), args);
	}

	/** What it cost, in one line: the curses (by name, with their stacks), the Tether, what the Seam kept, the atonement undone. */
	public static String cost(ModPackets.RecapPayload r) {
		List<String> parts = new ArrayList<>();
		for (RecapText.Text part : RecapText.cost(r)) {
			parts.add(tr(part));
		}
		String all = String.join(", ", parts);
		// (the first word of the line is a sentence's first)
		return all.isEmpty() ? all : Character.toUpperCase(all.charAt(0)) + all.substring(1);
	}
}
