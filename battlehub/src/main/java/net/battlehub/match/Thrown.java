package net.battlehub.match;

import net.battlehub.hub.HubWorld;
import net.battlehub.session.Sessions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Snowballs and eggs in matches. Vanilla throws them at players for 0 damage, and a player ignores a 0-damage hit
 * completely (no damage, no push). In a BattleHub match they hurt ({@code combat.snowballDamage}), or, in modes that
 * want only the push (Spleef), they push.
 */
public final class Thrown {
	private Thrown() {}

	public static boolean isThrown(Entity e) {
		if (e == null) return false;
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
		return id.equals("minecraft:snowball") || id.equals("minecraft:egg");
	}

	/**
	 * From the start of {@code Player.hurtServer}: the result of this hit, or null to let vanilla handle it (not a
	 * snowball or egg, not 0 damage, not in a match, or the match does not allow this hit).
	 */
	public static Boolean hit(ServerPlayer p, ServerLevel level, DamageSource source, float amount) {
		if (amount != 0f || !isThrown(source.getDirectEntity())) return null;
		if (!HubWorld.isBattleHub(p.level()) || Sessions.state(p.getUUID()) != Sessions.State.MATCH) return null;
		Match m = Matches.of(p.getUUID());
		if (m == null || !m.allowDamage(p, source)) return null;
		float damage = m.throwableDamage();
		if (damage > 0) {
			// the same hit again, now with damage: knockback, armour and the match's rules as for any hit
			return p.hurtServer(level, source, damage);
		}
		Entity ball = source.getDirectEntity();
		Vec3 v = ball.getDeltaMovement();
		Vec3 flat = new Vec3(v.x, 0, v.z);
		if (flat.lengthSqr() < 1e-6) flat = new Vec3(p.getX() - ball.getX(), 0, p.getZ() - ball.getZ());
		if (flat.lengthSqr() < 1e-6) return false;
		// a push away from the throw, like a light hit
		Vec3 now = p.getDeltaMovement();
		Vec3 push = flat.normalize().scale(0.45);
		p.setDeltaMovement(now.x / 2 + push.x, Math.max(now.y, 0) / 2 + 0.3, now.z / 2 + push.z);
		p.connection.send(new ClientboundSetEntityMotionPacket(p));
		return false;
	}
}
