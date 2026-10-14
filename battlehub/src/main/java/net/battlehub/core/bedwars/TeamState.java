package net.battlehub.core.bedwars;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One Bed Wars team: its members, who is still in the game, and its bed. While the bed stands, a dead member
 * comes back; once it is gone, a death is final.
 */
public final class TeamState {
	public final int index;
	private final List<UUID> members;
	private final Set<UUID> eliminated = new LinkedHashSet<>();
	private final Set<UUID> left = new LinkedHashSet<>();
	private boolean bed = true;
	private int finalKills;
	private int bedsBroken;

	public TeamState(int index, List<UUID> members) {
		this.index = index;
		this.members = List.copyOf(members);
	}

	public List<UUID> members() {
		return members;
	}

	public boolean has(UUID id) {
		return members.contains(id);
	}

	public boolean bedAlive() {
		return bed;
	}

	/** True when this call destroyed the bed (false if it was already gone). */
	public boolean destroyBed() {
		boolean was = bed;
		bed = false;
		return was;
	}

	/** A member died: true if they come back (the bed still stands), false if they are out for good. */
	public boolean died(UUID id) {
		if (!members.contains(id) || left.contains(id)) {
			return false;
		}
		if (bed) {
			return true;
		}
		eliminated.add(id);
		return false;
	}

	public void left(UUID id) {
		if (members.contains(id)) {
			left.add(id);
		}
	}

	public boolean isEliminated(UUID id) {
		return eliminated.contains(id) || left.contains(id);
	}

	/** Members still in the game (not eliminated, not left). */
	public List<UUID> playing() {
		List<UUID> out = new ArrayList<>();
		for (UUID id : members) {
			if (!isEliminated(id)) out.add(id);
		}
		return out;
	}

	/** Out when nobody of the team is left in the game (an empty team cannot win, bed or not). */
	public boolean isOut() {
		return playing().isEmpty();
	}

	public void addFinalKill() {
		finalKills++;
	}

	public int finalKills() {
		return finalKills;
	}

	public void addBedBroken() {
		bedsBroken++;
	}

	public int bedsBroken() {
		return bedsBroken;
	}
}
