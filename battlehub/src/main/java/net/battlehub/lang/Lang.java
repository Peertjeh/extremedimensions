package net.battlehub.lang;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.LegacyText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side translations. Vanilla clients do not have BattleHub's language file, so every text is resolved here
 * and sent as a literal component. Files: the built-in {@code assets/battlehub/lang/en_us.json}, overridden or
 * extended by {@code config/battlehub/lang/<code>.json}. Templates use {@code &} colour codes and {@code {0}}
 * placeholders.
 */
public final class Lang {
	private Lang() {}

	private static final String BUILTIN = "en_us";
	/** Translations shipped in the jar besides English; files in config/battlehub/lang/ override them. */
	private static final List<String> SHIPPED = List.of("nl_nl");
	private static final Map<String, Map<String, String>> LANGS = new HashMap<>();

	public static void load() {
		LANGS.clear();
		Map<String, String> builtin = new LinkedHashMap<>();
		try (InputStream in = Lang.class.getResourceAsStream("/assets/battlehub/lang/en_us.json")) {
			if (in != null) {
				read(new InputStreamReader(in, StandardCharsets.UTF_8), builtin);
			} else {
				BattleHub.LOGGER.error("Built-in language file is missing");
			}
		} catch (Exception e) {
			BattleHub.LOGGER.error("Could not read the built-in language file", e);
		}
		LANGS.put(BUILTIN, builtin);
		for (String code : SHIPPED) {
			try (InputStream in = Lang.class.getResourceAsStream("/assets/battlehub/lang/" + code + ".json")) {
				if (in != null) {
					read(new InputStreamReader(in, StandardCharsets.UTF_8), LANGS.computeIfAbsent(code, k -> new LinkedHashMap<>()));
				}
			} catch (Exception e) {
				BattleHub.LOGGER.error("Could not read the built-in language file {}", code, e);
			}
		}
		Path dir = ConfigManager.dir().resolve("lang");
		try {
			Files.createDirectories(dir);
			Path example = dir.resolve("en_us.json");
			if (!Files.exists(example)) {
				// Exported once as a template for translations; later updates still add new keys from the jar.
				JsonObject o = new JsonObject();
				builtin.forEach(o::addProperty);
				ConfigManager.writeJson(example, ConfigManager.GSON.toJson(o));
			}
			try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
				for (Path f : files) {
					String code = f.getFileName().toString().replace(".json", "").toLowerCase(Locale.ROOT);
					Map<String, String> map = LANGS.computeIfAbsent(code, k -> new LinkedHashMap<>());
					try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
						read(r, map);
					} catch (Exception e) {
						BattleHub.LOGGER.error("Could not read language file {}: {}", f, e.getMessage());
					}
				}
			}
		} catch (Exception e) {
			BattleHub.LOGGER.error("Could not load language files from {}", dir, e);
		}
	}

	private static void read(Reader r, Map<String, String> into) {
		JsonElement root = JsonParser.parseReader(r);
		if (root.isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
				if (e.getValue().isJsonPrimitive()) {
					into.put(e.getKey(), e.getValue().getAsString());
				}
			}
		}
	}

	public static boolean has(String key) {
		return LANGS.getOrDefault(BUILTIN, Map.of()).containsKey(key);
	}

	/** The language code used for this player (their client language if a file exists, else the default). */
	public static String languageOf(ServerPlayer p) {
		if (p != null && ConfigManager.config().perPlayerLanguage) {
			String code = p.clientInformation().language().toLowerCase(Locale.ROOT);
			if (LANGS.containsKey(code)) {
				return code;
			}
			// Same language, other region (nl_be uses nl_nl)
			int cut = code.indexOf('_');
			String prefix = (cut < 0 ? code : code.substring(0, cut)) + "_";
			for (String known : new java.util.TreeSet<>(LANGS.keySet())) {
				if (known.startsWith(prefix)) {
					return known;
				}
			}
		}
		return ConfigManager.config().language.toLowerCase(Locale.ROOT);
	}

	public static String template(String lang, String key) {
		String v = lookup(lang, key);
		if (v == null) v = lookup(ConfigManager.config().language.toLowerCase(Locale.ROOT), key);
		if (v == null) v = lookup(BUILTIN, key);
		return v == null ? key : v;
	}

	private static String lookup(String lang, String key) {
		Map<String, String> m = LANGS.get(lang);
		return m == null ? null : m.get(key);
	}

	/** Translated text for one player. */
	public static MutableComponent tr(ServerPlayer p, String key, Object... args) {
		return format(template(languageOf(p), key), args);
	}

	/** Translated text in the default language (console, broadcasts without a receiver). */
	public static MutableComponent tr(String key, Object... args) {
		return format(template(ConfigManager.config().language.toLowerCase(Locale.ROOT), key), args);
	}

	/** Plain string (colour codes stripped), e.g. for log lines. */
	public static String plain(String key, Object... args) {
		return tr(key, args).getString();
	}

	/** Item names and lore: same text, never italic, white unless coloured. */
	public static MutableComponent item(ServerPlayer p, String key, Object... args) {
		return Component.empty().withStyle(s -> s.withItalic(false).withColor(TextColor.fromRgb(0xFFFFFF))).append(tr(p, key, args));
	}

	/** Lore lines: grey, not italic. */
	public static MutableComponent lore(ServerPlayer p, String key, Object... args) {
		return Component.empty().withStyle(s -> s.withItalic(false).withColor(TextColor.fromRgb(0xAAAAAA))).append(tr(p, key, args));
	}

	/** Splits a translated template on {@code \n} into lore lines. */
	public static List<Component> loreLines(ServerPlayer p, String key, Object... args) {
		String t = template(languageOf(p), key);
		java.util.ArrayList<Component> out = new java.util.ArrayList<>();
		for (String line : t.split("\n", -1)) {
			out.add(Component.empty().withStyle(s -> s.withItalic(false).withColor(TextColor.fromRgb(0xAAAAAA))).append(format(line, args)));
		}
		return out;
	}

	/** Builds a component from a template with {@code &} codes and {@code {n}} placeholders. */
	public static MutableComponent format(String template, Object... args) {
		MutableComponent root = Component.empty();
		for (LegacyText.Segment seg : LegacyText.parse(template)) {
			Style style = styleOf(seg);
			if (seg.isArg()) {
				Object a = seg.arg() < args.length ? args[seg.arg()] : "{" + seg.arg() + "}";
				if (a instanceof Component c) {
					root.append(Component.empty().withStyle(style).append(c.copy()));
				} else {
					root.append(Component.literal(String.valueOf(a)).withStyle(style));
				}
			} else {
				root.append(Component.literal(seg.text()).withStyle(style));
			}
		}
		return root;
	}

	private static Style styleOf(LegacyText.Segment seg) {
		Style s = Style.EMPTY;
		if (seg.color() >= 0) s = s.withColor(TextColor.fromRgb(seg.color()));
		if (seg.bold()) s = s.withBold(true);
		if (seg.italic()) s = s.withItalic(true);
		if (seg.underlined()) s = s.withUnderlined(true);
		if (seg.strikethrough()) s = s.withStrikethrough(true);
		if (seg.obfuscated()) s = s.withObfuscated(true);
		return s;
	}

	/** Colour-coded text without a lang key (titles from the config, player names). */
	public static MutableComponent colored(String text) {
		return format(text == null ? "" : text);
	}
}
