package net.battlehub.arch.bldg;

/**
 * How old and how kept a building is: how much its stone weathers (moss low down, cracks higher up), how much of
 * it is damaged or fallen, and how far plants have taken it back.
 */
public enum Condition {
	NEW(0, 0, 0), MAINTAINED(0.08, 0, 0.02), AGED(0.25, 0, 0.06), WEATHERED(0.45, 0.04, 0.12), DAMAGED(0.5, 0.22, 0.15),
	ABANDONED(0.6, 0.35, 0.3), RUINED(0.75, 0.55, 0.45), ANCIENT(0.9, 0.7, 0.6), RECONSTRUCTED(0.5, 0.18, 0.1);

	public final double weathering;
	public final double damage;
	public final double vegetation;

	Condition(double weathering, double damage, double vegetation) {
		this.weathering = weathering;
		this.damage = damage;
		this.vegetation = vegetation;
	}
}
