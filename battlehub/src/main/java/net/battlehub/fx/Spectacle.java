package net.battlehub.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.battlehub.util.Ids;
import net.battlehub.util.Tasks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;

/**
 * Real firework bursts and lightning bolts for kill and win effects, made only on the clients of the players
 * nearby: nothing exists on the server, so nothing can hurt anyone, set fire or turn a pig into a piglin.
 */
public final class Spectacle {
	private Spectacle() {}

	private static final double RANGE = 96;
	/** Bursts and bolts shown so far (the self-test checks that they work). */
	private static int shown;

	public static int shown() {
		return shown;
	}

	private static List<ServerPlayer> viewers(ServerLevel level, Vec3 at) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(at) < RANGE * RANGE) {
				out.add(p);
			}
		}
		return out;
	}

	/** A firework that bursts right here, with these colours (RGB) and fade colours. */
	public static void firework(ServerLevel level, Vec3 at, FireworkExplosion.Shape shape, int[] colors, int[] fades, boolean trail, boolean twinkle) {
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		FireworkExplosion explosion = new FireworkExplosion(shape, new IntArrayList(colors), new IntArrayList(fades), trail, twinkle);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));
		FireworkRocketEntity e = new FireworkRocketEntity(level, at.x, at.y, at.z, rocket);
		List<SynchedEntityData.DataValue<?>> data = e.getEntityData().getNonDefaultValues();
		for (ServerPlayer p : viewers(level, at)) {
			p.connection.send(new ClientboundAddEntityPacket(e.getId(), e.getUUID(), at.x, at.y, at.z, 0f, 0f, e.getType(), 0, Vec3.ZERO, 0));
			if (data != null && !data.isEmpty()) {
				p.connection.send(new ClientboundSetEntityDataPacket(e.getId(), data));
			}
			// 17: the rocket explodes
			p.connection.send(new ClientboundEntityEventPacket(e, (byte) 17));
			p.connection.send(new ClientboundRemoveEntitiesPacket(e.getId()));
		}
		shown++;
	}

	/** A firework in random bright colours. */
	public static void randomFirework(ServerLevel level, Vec3 at, java.util.Random rnd) {
		FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
		int[] colors = {bright(rnd), bright(rnd)};
		int[] fades = {bright(rnd)};
		firework(level, at, shapes[rnd.nextInt(shapes.length)], colors, fades, rnd.nextBoolean(), rnd.nextBoolean());
	}

	private static int bright(java.util.Random rnd) {
		return net.battlehub.core.ParticleSpec.hsb(rnd.nextFloat(), 0.75f + rnd.nextFloat() * 0.25f, 1f);
	}

	/** A lightning bolt that strikes here, with its flash and thunder, but only on the clients. */
	public static void lightning(ServerLevel level, Vec3 at) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Ids.id("minecraft:lightning_bolt"));
		Entity bolt = type == null ? null : type.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) {
			return;
		}
		int id = bolt.getId();
		UUID uuid = bolt.getUUID();
		List<UUID> seen = new ArrayList<>();
		for (ServerPlayer p : viewers(level, at)) {
			p.connection.send(new ClientboundAddEntityPacket(id, uuid, at.x, at.y, at.z, 0f, 0f, type, 0, Vec3.ZERO, 0));
			seen.add(p.getUUID());
		}
		Tasks.later(30, () -> {
			for (UUID v : seen) {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(v);
				if (p != null) {
					p.connection.send(new ClientboundRemoveEntitiesPacket(id));
				}
			}
		});
		shown++;
	}
}
