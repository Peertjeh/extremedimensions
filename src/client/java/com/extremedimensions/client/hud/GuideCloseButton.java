package com.extremedimensions.client.hud;

import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.ui.Ui;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * The guide card's close cross, made clickable while the chat is open (the only time the cursor is free over the
 * HUD). It follows the card wherever the HUD puts it; closing switches the guide off (it can be switched on again in
 * the display settings). It is for the mouse only: the keyboard stays the chat's.
 */
public final class GuideCloseButton extends AbstractButton {
	private static final int SIZE = 14;
	private final ChatScreen chat;

	private GuideCloseButton(ChatScreen chat) {
		super(-100, -100, SIZE, SIZE, Component.translatable("gui.extremedimensions.guide.close"));
		this.chat = chat;
		this.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.guide.close")));
	}

	public static void attach(ChatScreen screen) {
		Screens.getWidgets(screen).add(new GuideCloseButton(screen));
	}

	/**
	 * Never reached from the keyboard. In the chat the arrow keys and Enter are the chat's own (up brings back what was
	 * sent last): a cross that could take the focus took it at the first press of up, and the Enter after it pressed the
	 * cross instead of sending the message.
	 */
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent event) {
		return null;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (ChargeHud.guideClose != null) {
			closeGuide();
		}
	}

	/** Gives the focus back to the chat's line, so the typing and its keys go on there. */
	private void handBack() {
		// (from the screen's children: the chat adds its line as a widget it draws itself, not among those drawn for it)
		for (GuiEventListener l : chat.children()) {
			if (l instanceof EditBox box) {
				chat.setFocused(box);
				return;
			}
		}
	}

	/** Switches the guide off for good (it can be switched on again in the display settings) and says so. */
	public static void closeGuide() {
		ClientOptions.setGuidance(false);
		ChargeHud.guideClose = null;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// A click takes the focus (the screen gives it to whatever was clicked, after the click): handed straight back
		if (chat.getFocused() == this) {
			handBack();
		}
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
