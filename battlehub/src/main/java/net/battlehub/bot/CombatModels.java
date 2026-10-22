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
 * The learned combat models shipped in {@code assets/battlehub/ml}: the spec, the level table and one ONNX model per
 * level, run with ONNX Runtime. Loaded the first time a bot needs them. Anything wrong (ONNX Runtime missing on this
 * platform, a model made for another spec) is logged once and the bots fight with the scripted steering instead.
 */
final class CombatModels {
	private CombatModels() {}

	private static boolean tried;
	private static CombatSpec spec;
	private static CombatLevels levels;
	private static final Map<String, Model> MODELS = new HashMap<>();
	private static final Map<String, Boolean> FAILED = new HashMap<>();

	/** One model: obs (1, 43) -> logits (1, 26). */
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

	static synchronized boolean ready() {
		if (!tried) {
			tried = true;
			try {
				spec = CombatSpec.shipped();
				levels = CombatLevels.parse(text("difficulties.json"));
				BattleHub.LOGGER.info("Learned combat: spec {} v{} ({} observations), {} levels", spec.name, spec.version, spec.obsDim(), levels.all().size());
			} catch (IOException | RuntimeException e) {
				BattleHub.LOGGER.warn("Learned combat is off: {}", e.toString());
				spec = null;
			}
		}
		return spec != null && levels != null;
	}

	static CombatSpec spec() {
		return spec;
	}

	static CombatLevels.Level level(Difficulty d) {
		return levels.of(d.id());
	}

	/** The model of a level, or null when it could not be loaded (then the scripted steering fights). */
	static synchronized Model model(String file) {
		Model m = MODELS.get(file);
		if (m != null || FAILED.containsKey(file)) return m;
		try {
			m = load(file, bytes(file), spec);
			MODELS.put(file, m);
			BattleHub.LOGGER.info("Learned combat: loaded {} ({} steps of training, Elo {})", file, m.meta.get("steps"), m.meta.getOrDefault("elo", "?"));
		} catch (Throwable e) {
			// also UnsatisfiedLinkError: no ONNX Runtime for this platform
			FAILED.put(file, true);
			BattleHub.LOGGER.warn("Learned combat: {} could not be loaded, the scripted fight plays instead: {}", file, e.toString());
		}
		return m;
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
