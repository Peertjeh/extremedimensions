package com.extremedimensions.trial;

import com.extremedimensions.config.Config;

/** A Rift Trial: what the week asks of a visit to the Seam, and the twist that makes it harder. */
public final class Trial {
	private Trial() {}

	public enum Kind {
		VEINS("veins"),
		HUNT("hunt"),
		WANDER("wander"),
		FAR("far"),
		PLUNDER("plunder");

		public final String id;

		Kind(String id) {
			this.id = id;
		}

		public static Kind byId(String id) {
			for (Kind k : values()) {
				if (k.id.equals(id)) {
					return k;
				}
			}
			return null;
		}

		/** What bronze, silver and gold ask. */
		public int[] thresholds() {
			return switch (this) {
				case VEINS -> new int[] {Config.TRIAL_VEINS_BRONZE, Config.TRIAL_VEINS_SILVER, Config.TRIAL_VEINS_GOLD};
				case HUNT -> new int[] {Config.TRIAL_HUNT_BRONZE, Config.TRIAL_HUNT_SILVER, Config.TRIAL_HUNT_GOLD};
				case WANDER -> new int[] {Config.TRIAL_WANDER_BRONZE, Config.TRIAL_WANDER_SILVER, Config.TRIAL_WANDER_GOLD};
				case FAR -> new int[] {Config.TRIAL_FAR_BRONZE, Config.TRIAL_FAR_SILVER, Config.TRIAL_FAR_GOLD};
				case PLUNDER -> new int[] {Config.TRIAL_PLUNDER_BRONZE, Config.TRIAL_PLUNDER_SILVER, Config.TRIAL_PLUNDER_GOLD};
			};
		}

		/** 0 = none, 1 = bronze, 2 = silver, 3 = gold. */
		public int medal(int score) {
			int[] t = thresholds();
			int medal = 0;
			for (int i = 0; i < t.length; i++) {
				if (score >= Math.max(1, t[i])) {
					medal = i + 1;
				}
			}
			return medal;
		}

		public String nameKey() {
			return "gui.extremedimensions.trial.kind." + id;
		}

		public String goalKey() {
			return "gui.extremedimensions.trial.kind." + id + ".goal";
		}
	}

	public enum Twist {
		CALM("calm"),
		SHORT("short"),
		HUNTED("hunted"),
		RESTLESS("restless"),
		HEAVY("heavy");

		public final String id;

		Twist(String id) {
			this.id = id;
		}

		public static Twist byId(String id) {
			for (Twist t : values()) {
				if (t.id.equals(id)) {
					return t;
				}
			}
			return CALM;
		}

		public String nameKey() {
			return "gui.extremedimensions.trial.twist." + id;
		}

		public String descKey() {
			return "gui.extremedimensions.trial.twist." + id + ".desc";
		}
	}

	public static String medalKey(int medal) {
		return "gui.extremedimensions.trial.medal." + Math.max(0, Math.min(3, medal));
	}
}
