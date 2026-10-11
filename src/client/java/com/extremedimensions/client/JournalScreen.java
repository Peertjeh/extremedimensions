package com.extremedimensions.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.extremedimensions.JournalEntry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Het Tether Journal: de gevonden pagina's van een naamloze Wanderer, om door te bladeren. */
public class JournalScreen extends Screen {
	private static final int PANEL_W = 236;
	private static final int PANEL_H = 220;
	private static final int TEXT_W = PANEL_W - 36;
	private static final int LINES_PER_PAGE = 10;

	/** Een pagina in het boek: titel (alleen op de eerste pagina van een entry) en regels. */
	private record Page(String title, List<String> lines) {}

	private final Set<String> unlocked;
	private final List<Page> pages = new ArrayList<>();
	private int page = 0;
	private final long openAt = System.currentTimeMillis();
	private long flipAt = 0;
	private Button prev;
	private Button next;

	public JournalScreen(Set<String> unlocked) {
		super(Component.literal("Tether Journal"));
		this.unlocked = unlocked;
	}

	@Override
	protected void init() {
		pages.clear();
		for (JournalEntry e : JournalEntry.values()) {
			if (!unlocked.contains(e.id)) {
				continue;
			}
			List<String> lines = wrap(this.font, e.text, TEXT_W);
			for (int i = 0; i < lines.size(); i += LINES_PER_PAGE) {
				pages.add(new Page(i == 0 ? e.title : null, lines.subList(i, Math.min(lines.size(), i + LINES_PER_PAGE))));
			}
		}
		page = Math.max(0, Math.min(page, pages.size() - 1));

		int x = this.width / 2 - PANEL_W / 2;
		int y = this.height / 2 - PANEL_H / 2;
		prev = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> flip(-1))
				.pos(x + 10, y + PANEL_H - 26).size(24, 18).build());
		next = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> flip(1))
				.pos(x + PANEL_W - 34, y + PANEL_H - 26).size(24, 18).build());
		this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
				.pos(this.width / 2 - 30, y + PANEL_H - 26).size(60, 18).build());
		updateButtons();
	}

	private void flip(int dir) {
		int np = page + dir;
		if (np >= 0 && np < pages.size()) {
			page = np;
			flipAt = System.currentTimeMillis();
		}
		updateButtons();
	}

	private void updateButtons() {
		if (prev != null) {
			prev.active = page > 0;
			next.active = page < pages.size() - 1;
		}
	}

	/** Simpele woordterugloop op pixelbreedte. */
	static List<String> wrap(Font font, String text, int width) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (font.width(candidate) > width && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (line.length() > 0) {
			out.add(line.toString());
		}
		return out;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		long now = System.currentTimeMillis();
		double pe = ClientState.ease((now - openAt) / 350.0);
		int x = this.width / 2 - PANEL_W / 2;
		int y = this.height / 2 - PANEL_H / 2 + (int) ((1 - pe) * 20);
		int pa = (int) (255 * pe);

		// Donker leer met een zielenblauwe rand die zacht ademt
		double breath = 0.5 + 0.5 * Math.sin(now / 900.0);
		int edge = breath > 0.5 ? 0x3FA7B8 : 0x2A6F7C;
		g.fill(x - 2, y - 2, x + PANEL_W + 2, y + PANEL_H + 2, ClientState.argb(pa, 0x0B0B10));
		g.fill(x - 1, y - 1, x + PANEL_W + 1, y + PANEL_H + 1, ClientState.argb(pa, edge));
		g.fill(x, y, x + PANEL_W, y + PANEL_H, ClientState.argb((int) (245 * pe), 0x1A1512));
		g.fill(x + 6, y + 6, x + PANEL_W - 6, y + PANEL_H - 32, ClientState.argb((int) (255 * pe), 0x2B231C));

		// Zwevende zielvonkjes
		for (int i = 0; i < 14; i++) {
			double t = now / 1000.0;
			double seed = ClientState.hash(i * 31L + 7);
			double phase = (t / (6 + seed * 6) + seed * 9) % 1.0;
			int sx = x + 8 + (int) (ClientState.hash(i * 17L + 3) * (PANEL_W - 16));
			int sy = y + PANEL_H - 34 - (int) (phase * (PANEL_H - 42));
			int alpha = (int) (Math.sin(phase * Math.PI) * 120 * pe);
			g.fill(sx, sy, sx + 2, sy + 2, ClientState.argb(alpha, 0x6FE3F0));
		}

		int cx = this.width / 2;
		g.centeredText(this.font, "TETHER JOURNAL", cx, y + 12, ClientState.argb(pa, 0x8FE6F0));

		if (pages.isEmpty()) {
			g.centeredText(this.font, "The pages are blank.", cx, y + 80, ClientState.argb(pa, 0x9A8F80));
			return;
		}
		Page pg = pages.get(page);
		double fe = flipAt > 0 ? ClientState.ease((now - flipAt) / 300.0) : 1.0;
		int ta = (int) (pa * fe);
		int ty = y + 30;
		if (pg.title() != null) {
			g.centeredText(this.font, pg.title(), cx, ty, ClientState.argb(ta, 0xE8D9B8));
			g.fill(cx - 40, ty + 11, cx + 40, ty + 12, ClientState.argb(ta / 2, 0x8FE6F0));
			ty += 18;
		}
		for (String line : pg.lines()) {
			g.text(this.font, line, x + 18, ty, ClientState.argb(ta, 0xCDBFA5));
			ty += 11;
		}
		String counter = (page + 1) + " / " + pages.size()
				+ "     pages found: " + unlocked.size() + " / " + JournalEntry.values().length;
		g.centeredText(this.font, counter, cx, y + PANEL_H - 40, ClientState.argb(pa / 2 + 40, 0x8A7F70));
	}

	public boolean isPauseScreen() {
		return false;
	}
}
