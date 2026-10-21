package com.extremedimensions.seam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.config.Config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Making the Seam anew, for operators. {@code /tether seam_reset confirm} asks for it; the next time the world loads,
 * before the Seam does, the Seam's own folder is wiped (or kept aside, see {@link Config#SEAM_RESET_BACKUP}), and
 * nothing else of the world is touched. A world cannot be let go of while the server runs, so until then the Seam
 * stays as it is. The seal on its floor goes with the folder, and {@link SeamService#checkSeal} then forgets every
 * island the world data remembers and brings whoever was inside home.
 *
 * <p>The request is a small file in the world's folder (who asked, and when), so it holds through a crash before the
 * restart and can be called off until then.
 */
public final class SeamReset {
	private SeamReset() {}

	private static final String REQUEST = "extremedimensions-seam-reset.txt";
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	public static Path root(MinecraftServer s) {
		return s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
	}

	private static Path request(Path root) {
		return root.resolve(REQUEST);
	}

	public static boolean waiting(Path root) {
		return Files.exists(request(root));
	}

	/** Who asked for the reset that is waiting ("" if none is, or it does not say). */
	public static String askedBy(Path root) {
		try {
			return waiting(root) ? Files.readString(request(root), StandardCharsets.UTF_8).lines().findFirst().orElse("").trim() : "";
		} catch (IOException e) {
			return "";
		}
	}

	/** Asks for the Seam to be made anew the next time the world loads; false if the world's folder cannot be written. */
	public static boolean ask(Path root, String by) {
		try {
			Files.writeString(request(root), by + "\n" + LocalDateTime.now() + "\n", StandardCharsets.UTF_8);
			ExtremeDimensions.LOGGER.info("{} asked for the Seam to be made anew: it will be the next time the world loads", by);
			return true;
		} catch (IOException e) {
			ExtremeDimensions.LOGGER.error("Could not ask for the Seam to be made anew", e);
			return false;
		}
	}

	/** Calls off a reset that is waiting; false if none was. */
	public static boolean callOff(Path root) {
		try {
			return Files.deleteIfExists(request(root));
		} catch (IOException e) {
			ExtremeDimensions.LOGGER.error("Could not call off the Seam's reset", e);
			return false;
		}
	}

	/** As the server starts, before its worlds load: a reset that was asked for is carried out. */
	public static void starting(MinecraftServer s) {
		Path root = root(s);
		if (waiting(root)) {
			wipe(root, Config.SEAM_RESET_BACKUP > 0);
		}
	}

	/**
	 * Wipes the Seam's folder under a world's folder (or, with {@code keep}, moves it aside beside itself) and takes the
	 * request away. The folder is first moved aside in one step, so the Seam is new even if deleting the old one is
	 * cut short; whatever cannot be deleted is left there for the admin. False (and the request still waiting, to be
	 * tried again the next time) if the folder could not be moved.
	 */
	public static boolean wipe(Path root, boolean keep) {
		Path seam = folder(root);
		Path aside = null;
		try {
			if (seam != null) {
				aside = seam.resolveSibling(seam.getFileName() + "-before-reset-" + LocalDateTime.now().format(STAMP));
				Files.move(seam, aside);
			}
			Files.deleteIfExists(request(root));
		} catch (IOException e) {
			ExtremeDimensions.LOGGER.error("Could not make the Seam anew; it will be tried again the next time the world loads", e);
			return false;
		}
		if (aside == null) {
			ExtremeDimensions.LOGGER.info("The Seam is made anew (it had no folder yet)");
		} else if (keep) {
			ExtremeDimensions.LOGGER.info("The Seam is made anew; the old one is kept in {}", root.relativize(aside));
		} else {
			try {
				delete(aside);
				ExtremeDimensions.LOGGER.info("The Seam is made anew; the old one is wiped");
			} catch (IOException e) {
				ExtremeDimensions.LOGGER.warn("The Seam is made anew, but not all of the old one could be deleted: what is left is in {}",
						root.relativize(aside), e);
			}
		}
		return true;
	}

	/**
	 * The Seam's own folder under a world's folder, or null if it has none yet. It is where a world keeps a dimension of
	 * a mod; should a version keep them elsewhere, it is the folder of that name with chunks in it, never one in a data
	 * pack.
	 */
	public static Path folder(Path root) {
		Path usual = root.resolve("dimensions").resolve(ExtremeDimensions.MOD_ID).resolve("the_seam");
		if (Files.isDirectory(usual)) {
			return usual;
		}
		try (Stream<Path> walk = Files.walk(root, 4)) {
			return walk.filter(p -> p.getNameCount() > root.getNameCount() + 1 && p.getFileName().toString().equals("the_seam")
					&& p.getParent().getFileName().toString().equals(ExtremeDimensions.MOD_ID) && !root.relativize(p).startsWith("datapacks")
					&& Files.isDirectory(p.resolve("region"))).findFirst().orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	private static void delete(Path dir) throws IOException {
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
				if (e != null) {
					throw e;
				}
				Files.delete(d);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
