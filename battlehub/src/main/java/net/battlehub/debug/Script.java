package net.battlehub.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.battlehub.BattleHub;

/** A tiny step runner: actions, waits and checks executed tick by tick. Prints SELFTEST PASS/FAIL lines. */
final class Script {
	private interface Step {
		/** Called every tick until it returns true. */
		boolean tick(int ticksInStep);
	}

	private final List<String> names = new ArrayList<>();
	private final List<Step> steps = new ArrayList<>();
	private int index;
	private int ticksInStep;
	private int passed;
	private int failed;
	private boolean finished;
	private final Runnable onFinish;

	Script(Runnable onFinish) {
		this.onFinish = onFinish;
	}

	Script run(String name, Runnable action) {
		names.add(name);
		steps.add(t -> {
			action.run();
			return true;
		});
		return this;
	}

	Script pause(int ticks) {
		names.add("pause " + ticks);
		steps.add(t -> t >= ticks);
		return this;
	}

	/** Waits until the condition holds; fails the check after the timeout. */
	Script waitFor(String name, int timeoutTicks, BooleanSupplier cond) {
		names.add(name);
		steps.add(t -> {
			boolean ok;
			try {
				ok = cond.getAsBoolean();
			} catch (Exception e) {
				BattleHub.LOGGER.error("SELFTEST FAIL {}: {}", name, e.toString(), e);
				failed++;
				return true;
			}
			if (ok) {
				pass(name);
				return true;
			}
			if (t >= timeoutTicks) {
				fail(name, "timed out after " + timeoutTicks + " ticks");
				return true;
			}
			return false;
		});
		return this;
	}

	Script check(String name, BooleanSupplier cond) {
		return waitFor(name, 0, cond);
	}

	void pass(String name) {
		passed++;
		BattleHub.LOGGER.info("SELFTEST PASS {}", name);
	}

	void fail(String name, String why) {
		failed++;
		BattleHub.LOGGER.error("SELFTEST FAIL {}: {}", name, why);
	}

	void tick() {
		if (finished) {
			return;
		}
		if (index >= steps.size()) {
			finished = true;
			BattleHub.LOGGER.info("SELFTEST FINISHED: {} passed, {} failed", passed, failed);
			onFinish.run();
			return;
		}
		boolean done;
		try {
			done = steps.get(index).tick(ticksInStep++);
		} catch (Exception e) {
			fail(names.get(index), e.toString());
			BattleHub.LOGGER.error("Self-test step crashed", e);
			done = true;
		}
		if (done) {
			index++;
			ticksInStep = 0;
		}
	}

	boolean finished() {
		return finished;
	}
}
