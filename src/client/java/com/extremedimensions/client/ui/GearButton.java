package com.extremedimensions.client.ui;

import com.extremedimensions.client.screen.SettingsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A small gear in a panel's corner: opens the settings, and closing them comes back here. */
public final class GearButton extends AbstractButton {
	private static final String[] GEAR = {
		".#.#.#.",
		"#######",
		".##.##.",
		"###.###",
		".##.##.",
		"#######",
		".#.#.#.",
	};
	private final Screen from;
	private final int accent;

	public GearButton(int x, int y, Screen from, int accent) {
		super(x, y, 13, 13, Component.translatable("gui.extremedimensions.settings.title"));
		this.from = from;
		this.accent = accent;
		this.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.settings.title")));
	}

	@Override
	public void onPress(InputWithModifiers input) {
		Minecraft.getInstance().gui.setScreen(new SettingsScreen(from));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = this.getX(), y = this.getY();
		int col = this.isHovered() ? Ui.WARN : Ui.mix(accent, Ui.MUTED, 0.4);
		for (int r = 0; r < GEAR.length; r++) {
			for (int k = 0; k < GEAR[r].length(); k++) {
				if (GEAR[r].charAt(k) == '#') {
					g.fill(x + 3 + k, y + 3 + r, x + 4 + k, y + 4 + r, Ui.argb(255, col));
				}
			}
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(NarratedElementType.TITLE, this.getMessage());
	}
}
