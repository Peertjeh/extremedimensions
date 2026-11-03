package com.extremedimensions.elder;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Knot Mother: what is tied into the First Knot. Whether she is the First Weaver, what the Fray made of her, or the
 * Held, the journal does not say. Where the Last Weaver unmakes the thread, she binds it: a tall veiled figure with a
 * crown of thorns and rings floating about her, who controls the floor with threads and knots and ties herself to
 * anchors round her chamber. Directed by {@link WeaverService}, with a kit of her own: the Threadline, the Knot Snare,
 * the Weaver's thread and cradle, and her anchors; break one and she is exposed for a moment.
 */
public class KnotMother extends Weaver {
	/** Threadline: a hand raised, a thread of amber drawn tight, then swept low across the floor (jump it, or step out of its lane). */
	public static final int THREADLINE = 7;
	/** Knot Snare: patterns drawn on the floor that close inwards; whoever is still in one when it closes is held fast. */
	public static final int SNARE = 8;
	/** Exposed: an anchor of hers broke; she recoils, her crown falters and the core at her breast lies open. */
	public static final int EXPOSED = 9;

	public KnotMother(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return elderAttributes(560.0, 4.0);
	}

	/** Her anchors standing shield her; exposed, she takes more (see {@link WeaverService#knotFactor}). */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return super.hurtServer(level, source, amount * WeaverService.knotFactor(this));
	}

	@Override
	public float getVoicePitch() {
		return 0.8F + this.random.nextFloat() * 0.08F;
	}
}
