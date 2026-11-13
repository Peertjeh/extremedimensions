package com.extremedimensions.client;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.client.hud.Toasts;
import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.screen.CursesScreen;
import com.extremedimensions.client.screen.SettingsScreen;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.network.ModPackets;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * The mod's keys (Controls, under its own heading). The Curses and the journal come bound (3.55.0): K and H, which
 * vanilla 26.3 leaves free (its G opens a server's Quick Actions) and which JourneyMap (J), Xaero's Minimap (Y, U, B, M)
 * and JEI do not take as they come. The rest stay unbound until the player picks keys: the inventory buttons, the
 * settings gear and the anchor are always there.
 *
 * <p>Key mappings only count presses while no screen is open, so these never open over another screen; a screen of
 * the mod listens for its own key itself and closes on it (see {@link #closes}).
 */
public final class ModKeys {
	private ModKeys() {}

	private static final KeyMapping.Category KEYS = KeyMapping.Category.register(ExtremeDimensions.id("main"));
	public static final KeyMapping CURSES = new KeyMapping("key.extremedimensions.curses", InputConstants.KEY_K, KEYS);
	public static final KeyMapping JOURNAL = new KeyMapping("key.extremedimensions.journal", InputConstants.KEY_H, KEYS);
	public static final KeyMapping SETTINGS = new KeyMapping("key.extremedimensions.settings", InputConstants.UNKNOWN.getValue(), KEYS);
	public static final KeyMapping TRIALS = new KeyMapping("key.extremedimensions.trials", InputConstants.UNKNOWN.getValue(), KEYS);
	/** The anchor status screen (3.55.0), which otherwise opens only from an anchor with a sneak and an empty hand. */
	public static final KeyMapping STATUS = new KeyMapping("key.extremedimensions.status", InputConstants.UNKNOWN.getValue(), KEYS);
	/** The pact (3.55.0), which otherwise opens only from its small brand in the Curses screen's header. */
	public static final KeyMapping PACT = new KeyMapping("key.extremedimensions.pact", InputConstants.UNKNOWN.getValue(), KEYS);
	/** Puts the card under the crosshair (a briefing, a recap) or the guide card away (3.55.0; see PutAway). */
	public static final KeyMapping PUT_AWAY = new KeyMapping("key.extremedimensions.put_away", InputConstants.UNKNOWN.getValue(), KEYS);

	/** Ticks spent in a world of the mod since joining, for the word on the keys a moment after the first join. */
	private static int inWorld;

	public static void register() {
		for (KeyMapping k : new KeyMapping[] {CURSES, JOURNAL, SETTINGS, TRIALS, STATUS, PACT, PUT_AWAY}) {
			KeyMappingHelper.registerKeyMapping(k);
		}
	}

	/** On a server of the mod, where asking for its screens is heard. */
	private static boolean online(Minecraft client) {
		return client.getConnection() != null && ClientState.modOnServer();
	}

	public static void tick(Minecraft client) {
		while (SETTINGS.consumeClick()) {
			client.gui.setScreen(new SettingsScreen(null));
		}
		while (CURSES.consumeClick()) {
			if (online(client)) {
				ClientPlayNetworking.send(new ModPackets.RequestCursesPayload(true));
			}
		}
		while (TRIALS.consumeClick()) {
			if (online(client)) {
				com.extremedimensions.client.screen.TrialsScreen.request(null);
			}
		}
		while (JOURNAL.consumeClick()) {
			if (online(client)) {
				ClientPlayNetworking.send(new ModPackets.RequestJournalPayload());
			}
		}
		while (STATUS.consumeClick()) {
			if (online(client) && ClientPlayNetworking.canSend(ModPackets.RequestStatusPayload.TYPE)) {
				ClientPlayNetworking.send(new ModPackets.RequestStatusPayload());
			}
		}
		while (PACT.consumeClick()) {
			if (online(client)) {
				CursesScreen.requestPact();
			}
		}
		welcome(client);
	}

	/**
	 * Once on this computer (3.55.0), a few seconds into the first world of the mod: a toast naming the keys the Curses
	 * and the journal open with, as they are bound now. Nothing when both have been unbound.
	 */
	private static void welcome(Minecraft client) {
		if (client.player == null || !online(client)) {
			inWorld = 0;
			return;
		}
		if (ClientOptions.keysTold() || ++inWorld < 100) {
			return;
		}
		ClientOptions.setKeysTold();
		boolean curses = !CURSES.isUnbound(), journal = !JOURNAL.isUnbound();
		if (!curses && !journal) {
			return;
		}
		Component text = curses && journal
				? Component.translatable("gui.extremedimensions.keys_toast.both", CURSES.getTranslatedKeyMessage(), JOURNAL.getTranslatedKeyMessage())
				: curses ? Component.translatable("gui.extremedimensions.keys_toast.curses", CURSES.getTranslatedKeyMessage())
						: Component.translatable("gui.extremedimensions.keys_toast.journal", JOURNAL.getTranslatedKeyMessage());
		Toasts.tell(Component.translatable("gui.extremedimensions.keys_toast.title"), text);
	}

	/**
	 * A screen of the mod pressed with its own key (3.55.0): it closes, as the inventory's key closes the inventory.
	 * Not while a text box has the keys (the settings' search, say). True when the key was taken.
	 */
	public static boolean closes(Screen screen, KeyMapping key, KeyEvent event) {
		return closes(screen, key, event, screen::onClose);
	}

	/** As {@link #closes(Screen, KeyMapping, KeyEvent)}, closing the screen the way given. */
	public static boolean closes(Screen screen, KeyMapping key, KeyEvent event, Runnable close) {
		if (screen.getFocused() instanceof EditBox || !key.matches(event)) {
			return false;
		}
		close.run();
		return true;
	}

	/** Whether this key press is Shift held with it (Shift+Tab goes back through the tabs). */
	public static boolean shift(KeyEvent event) {
		return (event.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
	}
}
