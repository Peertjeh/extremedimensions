package net.battlehub.util;

import java.lang.reflect.Method;
import java.util.Set;

import net.minecraft.world.entity.Entity;

/** Reads the command tags of an entity ({@code /tag}); the getter's name differs between versions. */
public final class Tags {
	private Tags() {}

	private static Method getter;
	private static boolean looked;

	/** Whether the entity has this tag, or null when tags cannot be read. */
	public static Boolean has(Entity e, String tag) {
		if (!looked) {
			looked = true;
			for (Method m : Entity.class.getMethods()) {
				if (m.getParameterCount() == 0 && Set.class.isAssignableFrom(m.getReturnType()) && m.getName().toLowerCase().contains("tag")) {
					getter = m;
					break;
				}
			}
		}
		if (getter == null) {
			return null;
		}
		try {
			return ((Set<?>) getter.invoke(e)).contains(tag);
		} catch (ReflectiveOperationException | RuntimeException ex) {
			return null;
		}
	}
}
