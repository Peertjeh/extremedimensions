package net.battlehub.core.ai;

/**
 * Something else that fights for the mind: the learned combat model. The mind still decides what to do (fight, heal,
 * go for the objective, get away from an edge); while it fights, a controller may steer instead of
 * {@link CombatSteering}. Returning null leaves the tick to the scripted steering.
 */
public interface MeleeController {
	Intent act(Tactic tactic, Situation s, Track track);
}
