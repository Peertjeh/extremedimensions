package net.battlehub.fx;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.battlehub.BattleHub;
import net.battlehub.core.PixelIcons;

/**
 * Builds the optional resource pack: the pixel icons as a font, BattleHub's sounds (made from vanilla sound
 * files with their own pitch and volume) and lobby music. Files in {@code config/battlehub/resourcepack/overlay/}
 * are added on top, so a server can put in its own .ogg sounds, textures or a pack.png.
 */
public final class PackBuilder {
	private PackBuilder() {}

	public static byte[] build(Path overlay) throws IOException {
		Map<String, byte[]> files = new LinkedHashMap<>();
		files.put("pack.mcmeta", mcmeta().getBytes(StandardCharsets.UTF_8));
		files.put("assets/minecraft/font/default.json", font().getBytes(StandardCharsets.UTF_8));
		files.put("assets/battlehub/textures/font/icons.png", iconsPng());
		files.put("assets/battlehub/sounds.json", sounds().getBytes(StandardCharsets.UTF_8));
		if (overlay != null && Files.isDirectory(overlay)) {
			try (Stream<Path> walk = Files.walk(overlay)) {
				for (Path f : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
					String name = overlay.relativize(f).toString().replace('\\', '/');
					files.put(name, Files.readAllBytes(f));
				}
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			Set<String> done = new HashSet<>();
			for (Map.Entry<String, byte[]> e : files.entrySet()) {
				if (!done.add(e.getKey())) continue;
				ZipEntry entry = new ZipEntry(e.getKey());
				// A fixed time, so the same content always gives the same zip (and hash)
				entry.setTime(0L);
				zip.putNextEntry(entry);
				zip.write(e.getValue());
				zip.closeEntry();
			}
		}
		return out.toByteArray();
	}

	/** The pack format of this game version, read without depending on how the method is called in this version. */
	static int packFormat() {
		try {
			Object version = Class.forName("net.minecraft.SharedConstants").getMethod("getCurrentVersion").invoke(null);
			Class<?> packType = Class.forName("net.minecraft.server.packs.PackType");
			Object client = packType.getField("CLIENT_RESOURCES").get(null);
			Object v = version.getClass().getMethod("packVersion", packType).invoke(version, client);
			if (v instanceof Number n) {
				return n.intValue();
			}
			for (String m : new String[] {"major", "getMajor"}) {
				try {
					return ((Number) v.getClass().getMethod(m).invoke(v)).intValue();
				} catch (NoSuchMethodException ignored) {
					// try the next name
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			BattleHub.LOGGER.debug("Pack format unknown: {}", e.toString());
		}
		return 0;
	}

	static String mcmeta() {
		int format = packFormat();
		JsonObject pack = new JsonObject();
		pack.addProperty("description", "BattleHub: icons, sounds and music");
		if (format > 0) {
			pack.addProperty("pack_format", format);
			pack.addProperty("min_format", format);
			pack.addProperty("max_format", format);
		} else {
			pack.addProperty("min_format", 1);
			pack.addProperty("max_format", 1000);
		}
		JsonObject root = new JsonObject();
		root.add("pack", pack);
		return root.toString();
	}

	/** One bitmap font provider for all icons, added to the default font (fonts from packs are combined). */
	static String font() {
		StringBuilder chars = new StringBuilder();
		for (String name : PixelIcons.ICONS.keySet()) {
			chars.append(PixelIcons.glyph(name));
		}
		JsonObject provider = new JsonObject();
		provider.addProperty("type", "bitmap");
		provider.addProperty("file", "battlehub:font/icons.png");
		provider.addProperty("ascent", 7);
		provider.addProperty("height", 8);
		JsonArray rows = new JsonArray();
		rows.add(chars.toString());
		provider.add("chars", rows);
		JsonArray providers = new JsonArray();
		providers.add(provider);
		JsonObject root = new JsonObject();
		root.add("providers", providers);
		return root.toString();
	}

	static byte[] iconsPng() throws IOException {
		int n = PixelIcons.ICONS.size();
		int size = PixelIcons.SIZE;
		BufferedImage img = new BufferedImage(size * n, size, BufferedImage.TYPE_INT_ARGB);
		int i = 0;
		for (String[] rows : PixelIcons.ICONS.values()) {
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					img.setRGB(i * size + x, y, PixelIcons.pixel(rows, x, y));
				}
			}
			i++;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}

	private record Sound(String file, double volume, double pitch, boolean stream) {}

	private static JsonObject event(List<Sound> sounds) {
		JsonArray list = new JsonArray();
		for (Sound s : sounds) {
			JsonObject o = new JsonObject();
			o.addProperty("name", s.file());
			if (s.volume() != 1.0) o.addProperty("volume", s.volume());
			if (s.pitch() != 1.0) o.addProperty("pitch", s.pitch());
			if (s.stream()) o.addProperty("stream", true);
			list.add(o);
		}
		JsonObject e = new JsonObject();
		e.add("sounds", list);
		return e;
	}

	static String sounds() {
		JsonObject root = new JsonObject();
		root.add(Sounds.INTRO, event(List.of(new Sound("minecraft:block/beacon/activate", 0.8, 1.3, false))));
		root.add(Sounds.KILL, event(List.of(new Sound("minecraft:random/levelup", 0.5, 1.9, false))));
		root.add(Sounds.HIT, event(List.of(new Sound("minecraft:random/successful_hit", 0.6, 1.2, false))));
		root.add(Sounds.VICTORY, event(List.of(new Sound("minecraft:ui/toast/challenge_complete", 0.9, 1.1, false))));
		root.add(Sounds.JUMP, event(List.of(new Sound("minecraft:mob/enderdragon/wings1", 0.35, 1.8, false))));
		root.add(Sounds.DUMMY, event(List.of(new Sound("minecraft:random/successful_hit", 0.5, 0.8, false))));
		root.add(Sounds.MUSIC_LOBBY, event(List.of(new Sound("minecraft:music/menu/menu1", 1, 1, true),
				new Sound("minecraft:music/menu/menu2", 1, 1, true), new Sound("minecraft:music/menu/menu3", 1, 1, true),
				new Sound("minecraft:music/menu/menu4", 1, 1, true))));
		return root.toString();
	}
}
