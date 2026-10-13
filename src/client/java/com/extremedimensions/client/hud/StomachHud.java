package com.extremedimensions.client.hud;

import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.ui.Ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

/** Shrunken Stomach: the drumsticks the stomach can no longer hold are shaded and crossed on the hunger bar. */
public class StomachHud implements HudElement {
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()
				|| mc.player.getVehicle() instanceof LivingEntity) {
			return;
		}
		int lost = ClientState.stacksOf("shrunken_stomach") * (int) Math.round(ClientState.valueOf("shrunken_stomach"));
		if (lost <= 0) {
			return;
		}
		int w = g.guiWidth(), h = g.guiHeight();
		int edge = Ui.argb(230, Ui.mix(Ui.EMBER_DEEP, Ui.SHADOW, 0.2));
		for (int i = 9; i > 9 - Math.min(9, lost); i--) {
			int x = w / 2 + 91 - i * 8 - 9, y = h - 39;
			g.fill(x, y, x + 9, y + 9, Ui.argb(170, Ui.INK));
			for (int k = 1; k < 8; k++) {
				g.fill(x + k, y + k, x + k + 1, y + k + 1, edge);
			}
		}
	}
}
