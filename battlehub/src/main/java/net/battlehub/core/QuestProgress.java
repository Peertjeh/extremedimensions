package net.battlehub.core;

/** Progress of one quest a player has this day or week. Plain fields for Gson. */
public final class QuestProgress {
	public String id = "";
	public int progress;
	public boolean done;

	public QuestProgress() {}

	public QuestProgress(String id) {
		this.id = id;
	}
}
