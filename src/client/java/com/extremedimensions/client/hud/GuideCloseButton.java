package com.extremedimensions.client.hud;

import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.ui.Ui;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * The guide card's close cross, made clickable while the chat is open (the only time the cursor is free over the
 * HUD). It follows the card wherever the HUD puts it; closing switches the guide off (it can be switched on again in
 * the display settings).
 */
public final class GuideCloseButton extends AbstractButton {
	private static final int SIZE = 14;
	private GuideCloseButton() {
		super(-100, -100, SIZE, SIZE, Component.translatable("gui.extremedimensions.guide.close"));
		this.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.guide.close")));
	}

	public static void attach(ChatScreen screen) {
		Screens.getWidgets(screen).add(new GuideCloseButton());
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (ChargeHud.guideClose != null) {
			closeGuide();
		}
	}

	/** Switches the guide off for good (it can be switched on again in the display settings) and says so. */
	public static void closeGuide() {
		ClientOptions.setGuidance(false);
		ChargeHud.guideClose = null;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.translatable("gui.extremedimensions.guide.hidden"), false);
		}
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int[] at = ChargeHud.guideClose;
		if (at == null) {
			this.setX(-100);
			this.setY(-100);
			return;
		}
		// A fixed click area centred on the card's own cross, whatever the HUD size
		this.setX(at[0] + at[2] / 2 - SIZE / 2);
		this.setY(at[1] + at[2] / 2 - SIZE / 2);
		// Over the cross: a frame that says it can be clicked now
		int col = this.isHovered() ? Ui.WARN : Ui.SOUL;
		g.outline(at[0], at[1], at[2], at[2], Ui.argb(this.isHovered() ? 255 : 150, col));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(NarratedElementType.TITLE, this.getMessage());
	}
}
