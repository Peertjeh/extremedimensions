package net.battlehub.bot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import net.battlehub.BattleHub;
import net.battlehub.core.ai.Difficulty;
import net.battlehub.core.ml.CombatLevels;
import net.battlehub.core.ml.CombatSpec;

/**
 * The learned combat models shipped in {@code assets/battlehub/ml}, per spec: v1 (the sword duel; combat_v1.json,
 * difficulties.json, combat_v1_*.onnx) and v2 (every melee mode; combat_v2.json, difficulties_v2.json,
 * combat_v2_*.onnx). Each is a spec, a level table and one ONNX model per level, run with ONNX Runtime, loaded the first
 * time a bot needs it. Anything wrong (ONNX Runtime missing on this platform, a model made for another spec, files not
 * shipped) is logged once and the bots fight the scripted way (or with v1) instead.
 */
final class CombatModels {
	private CombatModels() {}

	static final Family V1 = new Family("combat_v1.json", "difficulties.json");
	static final Family V2 = new Family("combat_v2.json", "difficulties_v2.json");

	/** One model: obs (1, obs_dim) -> logits (1, all heads' choices). */
	static final class Model {
		final String file;
		final OrtSession session;
		final Map<String, String> meta;

		Model(String file, OrtSession session, Map<String, String> meta) {
			this.file = file;
			this.session = session;
			this.meta = meta;
		}

		float[] logits(float[] obs) {
			try (OnnxTensor t = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), new float[][] {obs});
					OrtSession.Result r = session.run(Map.of("obs", t))) {
				return ((float[][]) r.get(0).getValue())[0];
			} catch (OrtException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/** The spec, level table and models of one spec version. */
	static final class Family {
		final String specFile, levelsFile;
		private boolean tried;
		private CombatSpec spec;
		private CombatLevels levels;
		private final Map<String, Model> models = new HashMap<>();
		private final Map<String, Boolean> failed = new HashMap<>();

		Family(String specFile, String levelsFile) {
			this.specFile = specFile;
			this.levelsFile = levelsFile;
		}

		synchronized boolean ready() {
			if (!tried) {
				tried = true;
				try {
					spec = CombatSpec.shipped(specFile);
					levels = CombatLevels.parse(text(levelsFile));
					BattleHub.LOGGER.info("Learned combat: spec {} v{} ({} observations), {} levels", spec.name, spec.version, spec.obsDim(), levels.all().size());
				} catch (IOException | RuntimeException e) {
					BattleHub.LOGGER.warn("Learned combat ({}) is off: {}", specFile, e.toString());
					spec = null;
				}
			}
			return spec != null && levels != null;
		}

		CombatSpec spec() {
			return spec;
		}

		CombatLevels.Level level(Difficulty d) {
			return levels.of(d.id());
		}

		/** The model of a level, or null when it could not be loaded. */
		synchronized Model model(String file) {
			Model m = models.get(file);
			if (m != null || failed.containsKey(file)) return m;
			try {
				m = load(file, bytes(file), spec);
				models.put(file, m);
				BattleHub.LOGGER.info("Learned combat: loaded {} ({} steps of training, Elo {})", file, m.meta.get("steps"), m.meta.getOrDefault("elo", "?"));
			} catch (Throwable e) {
				// also UnsatisfiedLinkError: no ONNX Runtime for this platform
				failed.put(file, true);
				BattleHub.LOGGER.warn("Learned combat: {} could not be loaded, the bots fight without it: {}", file, e.toString());
			}
			return m;
		}
	}

	// v1, as LearnedMelee uses it
	static boolean ready() {
		return V1.ready();
	}

	static CombatSpec spec() {
		return V1.spec();
	}

	static CombatLevels.Level level(Difficulty d) {
		return V1.level(d);
	}

	static Model model(String file) {
		return V1.model(file);
	}

	/** Loads a model and checks it was made for this spec (shape and the spec's sha256 in its metadata). */
	static Model load(String file, byte[] onnx, CombatSpec spec) throws OrtException {
		OrtEnvironment env = OrtEnvironment.getEnvironment();
		OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
		opts.setIntraOpNumThreads(1);
		opts.setInterOpNumThreads(1);
		OrtSession session = env.createSession(onnx, opts);
		Map<String, String> meta = session.getMetadata().getCustomMetadata();
		String sha = meta.get("spec_sha256");
		if (spec != null && !spec.sha256.equals(sha)) {
			session.close();
			throw new IllegalStateException(file + " was made for another spec (" + sha + ", the mod has " + spec.sha256 + ")");
		}
		return new Model(file, session, meta);
	}

	private static byte[] bytes(String file) throws IOException {
		try (InputStream in = CombatModels.class.getResourceAsStream("/assets/battlehub/ml/" + file)) {
			if (in == null) throw new IOException("assets/battlehub/ml/" + file + " is missing");
			return in.readAllBytes();
		}
	}

	private static String text(String file) throws IOException {
		return new String(bytes(file), StandardCharsets.UTF_8);
	}
}
