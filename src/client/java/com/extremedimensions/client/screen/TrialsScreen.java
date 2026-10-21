package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.config.Config;
import com.extremedimensions.network.ModPackets;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Rift Trials: this week's goal in the Seam and its twist, the medals and what they pay, the world's scoreboard,
 * and the best three of the last trial. The trial is taken up here and begins with the next rift stepped through.
 */
public class TrialsScreen extends Screen {
	private static final int PW = 396, PH = 212;
	/** The column split: the trial on the left, the board on the right. */
	private static final int SPLIT = 210;
	private static final int[] MEDALS = {0xC9814B, 0xC8D0DA, 0xFFC94A};

	private static TrialsScreen shown;
	/** Set when the screen was asked for, so the answer opens it (other answers only refresh it). */
	private static boolean pendingOpen;
	private static Screen returnTo;

	private record Row(String name, int score, int left, int medal) {}

	private final Screen parent;
	private final long openAt = System.currentTimeMillis();
	private boolean unlocked, inSeam, armed, active;
	private String kind = "veins", twist = "calm";
	private final int[] need = new int[3];
	private long secondsLeft;
	private int best = -1, bestMedal, claimed, rank;
	private final List<Row> board = new ArrayList<>();
	private String lastKind = "";
	private final List<Row> lastTop = new ArrayList<>();
	private Button take;

	/** The Trials screen on show, or null. */
	public static TrialsScreen shown() {
		return shown;
	}

	/** Asks the server for the screen; parent is where Done goes back to. */
	public static void request(Screen parent) {
		pendingOpen = true;
		returnTo = parent;
		ClientPlayNetworking.send(new ModPackets.RequestTrialsPayload(true));
	}

	public static void receive(String data) {
		if (pendingOpen || shown == null) {
			boolean open = pendingOpen;
			pendingOpen = false;
			if (!open) {
				return;
			}
			Minecraft.getInstance().gui.setScreen(new TrialsScreen(data, returnTo));
			returnTo = null;
		} else {
			shown.update(data);
		}
	}

	public TrialsScreen(String data, Screen parent) {
		super(Component.translatable("gui.extremedimensions.trial.screen"));
		this.parent = parent;
		update(data);
	}

	private static int num(String s, int fallback) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private void update(String data) {
		String[] parts = (data == null ? "" : data).split("\\|", -1);
		String[] h = parts[0].split(";", -1);
		if (h.length >= 15) {
			unlocked = "1".equals(h[0]);
			inSeam = "1".equals(h[1]);
			armed = "1".equals(h[2]);
			active = "1".equals(h[3]);
			kind = h[5];
			twist = h[6];
			need[0] = num(h[7], 0);
			need[1] = num(h[8], 0);
			need[2] = num(h[9], 0);
			try {
				secondsLeft = Long.parseLong(h[10]);
			} catch (NumberFormatException e) {
				secondsLeft = 0;
			}
			best = num(h[11], -1);
			bestMedal = num(h[12], 0);
			claimed = num(h[13], 0);
			rank = num(h[14], 0);
		}
		board.clear();
		if (parts.length > 1 && !parts[1].isEmpty()) {
			for (String e : parts[1].split(";")) {
				String[] f = e.split(",");
				if (f.length >= 4) {
					board.add(new Row(f[0], num(f[1], 0), num(f[2], 0), num(f[3], 0)));
				}
			}
		}
		lastTop.clear();
		lastKind = "";
		if (parts.length > 2 && !parts[2].isEmpty()) {
			String[] l = parts[2].split(";");
			lastKind = l[0];
			for (int i = 1; i < l.length; i++) {
				String[] f = l[i].split(",");
				if (f.length >= 3) {
					lastTop.add(new Row(f[0], num(f[1], 0), 0, num(f[2], 0)));
				}
			}
		}
		refresh();
	}

	private int x0() {
		return Math.max(2, (this.width - PW) / 2);
	}

	private int y0() {
		return Math.max(4, (this.height - PH) / 2);
	}

	@Override
	protected void init() {
		shown = this;
		int x0 = x0(), y0 = y0();
		take = this.addRenderableWidget(Button.builder(Component.empty(), b -> ClientPlayNetworking.send(new ModPackets.ArmTrialPayload(!armed)))
				.pos(x0 + 14, y0 + PH - 22).size(150, 18).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).pos(x0 + PW - 14 - 100, y0 + PH - 22).size(100, 18).build());
		refresh();
	}

	private void refresh() {
		if (take == null) {
			return;
		}
		take.setMessage(Component.translatable(armed ? "gui.extremedimensions.trial.let_go" : "gui.extremedimensions.trial.take_up"));
		take.active = unlocked && !inSeam && !active;
	}

	@Override
	public void onClose() {
		if (shown == this) {
			shown = null;
		}
		Minecraft.getInstance().gui.setScreen(parent);
	}

	public boolean isPauseScreen() {
		return false;
	}

	private static String left(long secs) {
		long d = secs / 86400, h = (secs % 86400) / 3600, m = (secs % 3600) / 60;
		return d > 0 ? Ui.tr("gui.extremedimensions.trial.left_days", d, h) : Ui.tr("gui.extremedimensions.trial.left_hours", h, m);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		double fade = Ui.ease((System.currentTimeMillis() - openAt) / 260.0);
		int x0 = x0(), y0 = y0();
		int accent = Ui.SOUL;
		g.fill(0, 0, this.width, this.height, Ui.argb((int) (80 * fade), 0x001410));
		Ui.panel(g, x0, y0, PW, PH, accent, fade);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.trial.screen").toUpperCase(), x0 + PW / 2, y0 + 8, accent, fade);
		String next = left(secondsLeft);
		g.text(this.font, next, x0 + PW - 14 - this.font.width(next), y0 + 8, Ui.argb(fade, Ui.MUTED));
		g.fill(x0 + SPLIT, y0 + 24, x0 + SPLIT + 1, y0 + PH - 30, Ui.argb(fade * 0.5, accent));
		trial(g, x0 + 14, y0 + 24, SPLIT - 24, fade);
		board(g, x0 + SPLIT + 10, y0 + 24, PW - SPLIT - 24, fade);
	}

	/** The left column: this week's trial, its twist, the medal table and where the player stands. */
	private void trial(GuiGraphicsExtractor g, int x, int y, int w, double fade) {
		g.text(this.font, Ui.tr("gui.extremedimensions.trial.kind." + kind).toUpperCase(), x, y, Ui.argb(fade, 0xFFC060));
		int ly = y + 13;
		for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.trial.kind." + kind + ".goal"), w)) {
			g.text(this.font, line, x, ly, Ui.argb(fade, Ui.TEXT));
			ly += 10;
		}
		ly += 3;
		g.text(this.font, Ui.tr("gui.extremedimensions.trial.twist_label", Ui.tr("gui.extremedimensions.trial.twist." + twist)), x, ly,
				Ui.argb(fade, "calm".equals(twist) ? Ui.OK : Ui.WARN));
		ly += 10;
		for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.trial.twist." + twist + ".desc"), w)) {
			g.text(this.font, line, x, ly, Ui.argb(fade, Ui.MUTED));
			ly += 10;
		}
		// The medal table: what each medal asks and pays (once a week); a tick marks the ones paid this week
		ly = Math.max(ly + 4, y + 70);
		int cGoal = x + 90, cAtone = x + 126, cFrag = x + 156, cRelic = x + w;
		String[] head = {"gui.extremedimensions.trial.col_goal", "gui.extremedimensions.trial.col_atone", "gui.extremedimensions.trial.col_frag",
				"gui.extremedimensions.trial.col_relic"};
		int[] cols = {cGoal, cAtone, cFrag, cRelic};
		for (int i = 0; i < 4; i++) {
			String t = Ui.tr(head[i]);
			g.text(this.font, t, cols[i] - this.font.width(t), ly, Ui.argb(fade * 0.8, Ui.MUTED));
		}
		ly += 11;
		int[][] pay = {{Config.TRIAL_BRONZE_ATONE, Config.TRIAL_BRONZE_FRAGMENTS, 0}, {Config.TRIAL_SILVER_ATONE, Config.TRIAL_SILVER_FRAGMENTS, 0},
				{Config.TRIAL_GOLD_ATONE, Config.TRIAL_GOLD_FRAGMENTS, Config.TRIAL_GOLD_RELICS}};
		for (int i = 0; i < 3; i++) {
			int c = MEDALS[i];
			boolean paid = claimed > i;
			g.fill(x, ly + 1, x + 7, ly + 8, Ui.argb(fade, Ui.mix(c, 0x000000, 0.45)));
			g.fill(x + 1, ly + 2, x + 6, ly + 7, Ui.argb(fade, c));
			String name = Ui.tr("gui.extremedimensions.trial.medal." + (i + 1)) + (paid ? " \u2714" : "");
			g.text(this.font, name, x + 11, ly, Ui.argb(fade, paid ? Ui.OK : Ui.PARCHMENT));
			String[] cells = {String.valueOf(need[i]), "+" + pay[i][0], String.valueOf(pay[i][1]), pay[i][2] > 0 ? String.valueOf(pay[i][2]) : "-"};
			for (int k = 0; k < 4; k++) {
				g.text(this.font, cells[k], cols[k] - this.font.width(cells[k]), ly, Ui.argb(fade, paid ? Ui.MUTED : k == 0 ? c : Ui.TEXT));
			}
			ly += 11;
		}
		ly += 5;
		String mine = best < 0 ? Ui.tr("gui.extremedimensions.trial.no_run")
				: Ui.tr("gui.extremedimensions.trial.your_best", best, Ui.tr("gui.extremedimensions.trial.medal." + Math.max(0, Math.min(3, bestMedal))), rank);
		g.text(this.font, Ui.ellipsize(this.font, mine, w), x, ly, Ui.argb(fade, best < 0 ? Ui.MUTED : bestMedal > 0 ? MEDALS[bestMedal - 1] : Ui.TEXT));
		ly += 13;
		String state = !unlocked ? "gui.extremedimensions.trial.locked" : active ? "gui.extremedimensions.trial.state_active"
				: inSeam ? "gui.extremedimensions.trial.in_seam" : armed ? "gui.extremedimensions.trial.state_armed" : "gui.extremedimensions.trial.state_idle";
		int sc = !unlocked || inSeam && !active ? Ui.WARN : armed || active ? Ui.OK : Ui.MUTED;
		for (String line : Ui.wrap(this.font, Ui.tr(state), w)) {
			if (ly > y0() + PH - 34) {
				break;
			}
			g.text(this.font, line, x, ly, Ui.argb(fade, sc));
			ly += 10;
		}
	}

	/** The right column: this week's board, then the last trial's best three. */
	private void board(GuiGraphicsExtractor g, int x, int y, int w, double fade) {
		g.text(this.font, Ui.tr("gui.extremedimensions.trial.board"), x, y, Ui.argb(fade, Ui.MUTED));
		int ly = y + 12;
		if (board.isEmpty()) {
			for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.trial.board_empty"), w)) {
				g.text(this.font, line, x, ly, Ui.argb(fade * 0.8, Ui.MUTED));
				ly += 10;
			}
		}
		String me = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString();
		for (int i = 0; i < board.size(); i++) {
			row(g, x, ly, w, i + 1, board.get(i), board.get(i).name().equals(me), fade);
			ly += 10;
		}
		int ty = y + 124;
		g.fill(x, ty - 4, x + w, ty - 3, Ui.argb(fade * 0.4, Ui.SOUL));
		String last = lastKind.isEmpty() ? Ui.tr("gui.extremedimensions.trial.last_none")
				: Ui.tr("gui.extremedimensions.trial.last", Ui.tr("gui.extremedimensions.trial.kind." + lastKind));
		g.text(this.font, Ui.ellipsize(this.font, last, w), x, ty, Ui.argb(fade, Ui.MUTED));
		ty += 11;
		for (int i = 0; i < lastTop.size(); i++) {
			row(g, x, ty, w, i + 1, lastTop.get(i), false, fade);
			ty += 10;
		}
	}

	private void row(GuiGraphicsExtractor g, int x, int y, int w, int place, Row r, boolean mine, double fade) {
		if (mine) {
			g.fill(x - 2, y - 1, x + w + 2, y + 9, Ui.argb(fade * 0.25, Ui.SOUL));
		}
		String n = place + ".";
		g.text(this.font, n, x, y, Ui.argb(fade, Ui.MUTED));
		int c = r.medal() > 0 ? MEDALS[Math.min(3, r.medal()) - 1] : Ui.TRACK;
		g.fill(x + 14, y + 1, x + 20, y + 7, Ui.argb(fade, c));
		String score = String.valueOf(r.score());
		g.text(this.font, Ui.ellipsize(this.font, r.name(), w - 26 - this.font.width(score) - 4), x + 24, y, Ui.argb(fade, mine ? Ui.PARCHMENT : Ui.TEXT));
		g.text(this.font, score, x + w - this.font.width(score), y, Ui.argb(fade, r.medal() > 0 ? c : Ui.TEXT));
	}
}
