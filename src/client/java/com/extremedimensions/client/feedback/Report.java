package com.extremedimensions.client.feedback;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * 3.56: the way back to the maker without Discord. "Report a problem" in the settings copies the versions and the mod
 * list to the clipboard and opens a new GitHub issue with them already filled in; "Wiki" opens the wiki. Both ask first,
 * as every link in the game does.
 */
public final class Report {
	public static final String REPO = "https://github.com/Peertjeh/extremedimensions";
	public static final String WIKI = "https://peertjeh.github.io/extremedimensions/";
	/** GitHub turns longer addresses away; the full list is on the clipboard either way. */
	private static final int URL_ROOM = 6000;

	private Report() {
	}

	/** The versions, the system and every top-level mod, as plain text for the issue's details field. */
	public static String details() {
		FabricLoader loader = FabricLoader.getInstance();
		StringBuilder b = new StringBuilder();
		b.append("Extreme Dimensions ").append(version("extremedimensions")).append('\n');
		b.append("Minecraft ").append(version("minecraft")).append(", Fabric Loader ").append(version("fabricloader"))
				.append(", Fabric API ").append(version("fabric-api")).append('\n');
		b.append("Java ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name"))
				.append(' ').append(System.getProperty("os.arch")).append('\n');
		b.append("Language ").append(Minecraft.getInstance().getLanguageManager().getSelected()).append('\n');
		List<String> mods = new ArrayList<>();
		for (ModContainer m : loader.getAllMods()) {
			String id = m.getMetadata().getId();
			if (m.getContainingMod().isPresent() || id.equals("java") || id.equals("minecraft") || id.equals("fabricloader")
					|| id.equals("extremedimensions") || id.equals("fabric-api") || id.startsWith("fabric-")) {
				continue;
			}
			mods.add(id + " " + m.getMetadata().getVersion().getFriendlyString());
		}
		mods.sort(String::compareTo);
		b.append("Other mods (").append(mods.size()).append("): ").append(mods.isEmpty() ? "none" : String.join(", ", mods));
		return b.toString();
	}

	/** The new-issue address with the bug form and its details field filled in. */
	public static String issueUrl(String details) {
		String base = REPO + "/issues/new?template=bug.yml&details=";
		String enc = enc(details);
		if (base.length() + enc.length() > URL_ROOM) {
			String cut = details.substring(0, Math.min(details.length(), 1500)) + " ... (the full list is on your clipboard)";
			enc = enc(cut);
		}
		return base + enc;
	}

	/** Copies the details to the clipboard, then asks before opening the new issue in the browser. */
	public static void open(Screen parent) {
		String details = details();
		Minecraft.getInstance().keyboardHandler.setClipboard(details);
		ConfirmLinkScreen.confirmLinkNow(parent, URI.create(issueUrl(details)));
	}

	public static void openWiki(Screen parent) {
		ConfirmLinkScreen.confirmLinkNow(parent, URI.create(WIKI));
	}

	private static String version(String id) {
		return FabricLoader.getInstance().getModContainer(id).map(m -> m.getMetadata().getVersion().getFriendlyString())
				.orElse("absent");
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
