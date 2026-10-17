package net.battlehub.arch.mat;

import java.util.EnumMap;
import java.util.Map;

import net.battlehub.arch.Slot;

/**
 * Materials by role, with inheritance: a building palette asks its district's palette for what it does not set
 * itself, the district asks the dimension's, so neighbouring buildings share a family likeness while each can
 * change its roof or its stone.
 */
public final class Palette {
	private final String name;
	private final Palette parent;
	private final Map<Slot, Ramp> ramps = new EnumMap<>(Slot.class);

	public Palette(String name, Palette parent) {
		this.name = name;
		this.parent = parent;
	}

	public String name() {
		return name;
	}

	public Palette parent() {
		return parent;
	}

	/** Sets a role (a null ramp, e.g. from names the game lacks, leaves it to the parent). */
	public Palette set(Slot slot, Ramp ramp) {
		if (ramp != null) ramps.put(slot, ramp);
		return this;
	}

	public Palette set(Slot slot, String... names) {
		return set(slot, Ramp.of(names));
	}

	/** A child palette that starts out the same as this one. */
	public Palette child(String childName) {
		return new Palette(childName, this);
	}

	/** This role's own ramp, or the parent's. */
	public Ramp own(Slot slot) {
		Ramp r = ramps.get(slot);
		if (r != null) return r;
		return parent == null ? null : parent.own(slot);
	}

	/** The ramp for a role, trying its fallback roles when neither this palette nor its parents set it. */
	public Ramp ramp(Slot slot) {
		for (Slot s = slot; s != null; s = s.fallback) {
			Ramp r = own(s);
			if (r != null) return r;
		}
		return null;
	}

	/** How deep the inheritance goes (dimension 0, district 1, building 2...). */
	public int depth() {
		return parent == null ? 0 : parent.depth() + 1;
	}

	public Map<Slot, Ramp> ownRamps() {
		return java.util.Collections.unmodifiableMap(ramps);
	}
}
