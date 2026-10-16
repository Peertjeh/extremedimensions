package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CosmeticEffectsTest {
	@Test
	void particleTexts() {
		assertEquals(new ParticleSpec(ParticleSpec.Kind.SIMPLE, "minecraft:flame", 0, 0, 1f), ParticleSpec.parse("flame"));
		assertEquals("minecraft:end_rod", ParticleSpec.parse("minecraft:end_rod").id());
		ParticleSpec dust = ParticleSpec.parse("dust:#FF0000:1.5");
		assertEquals(ParticleSpec.Kind.DUST, dust.kind());
		assertEquals(0xFF0000, dust.color());
		assertEquals(1.5f, dust.scale());
		ParticleSpec fade = ParticleSpec.parse("dust:#ff0000>#0000ff");
		assertEquals(ParticleSpec.Kind.DUST_FADE, fade.kind());
		assertEquals(0x0000FF, fade.color2());
		assertEquals(ParticleSpec.Kind.RAINBOW, ParticleSpec.parse("rainbow").kind());
		assertEquals(2f, ParticleSpec.parse("rainbow:2").scale());
		assertEquals(ParticleSpec.Kind.TRAIL, ParticleSpec.parse("trail:#00FFFF").kind());
		assertEquals("minecraft:gold_block", ParticleSpec.parse("block:gold_block").id());
		assertEquals(ParticleSpec.Kind.ITEM, ParticleSpec.parse("item:minecraft:diamond").kind());
		assertEquals(0x55FF55, ParticleSpec.parse("tint:entity_effect:#55FF55").color());
		assertNull(ParticleSpec.parse("tint:flame:#55FF55"), "flame takes no colour");
		assertNull(ParticleSpec.parse("dust:#GG0000"));
		assertNull(ParticleSpec.parse("dust:#FF0000:9"), "too big");
		assertNull(ParticleSpec.parse("Bad Id!"));
		assertEquals(2, ParticleSpec.parseAll("flame+dust:#FFFFFF").size());
		assertTrue(ParticleSpec.parseAll("flame+nope nope").isEmpty(), "one bad part spoils the lot");
		assertTrue(ParticleSpec.parseAll("").isEmpty());
	}

	@Test
	void rainbowRunsThroughColours() {
		ParticleSpec r = ParticleSpec.parse("rainbow");
		assertEquals(0xFF2626, r.colorAt(0, 100) | 0, "starts red");
		assertNotEquals(r.colorAt(0, 100), r.colorAt(50, 100));
		assertEquals(r.colorAt(10, 100), r.colorAt(110, 100), "repeats");
		assertEquals(0xFF0000, ParticleSpec.hsb(0f, 1f, 1f));
		assertEquals(0x00FF00, ParticleSpec.hsb(1f / 3, 1f, 1f));
		assertEquals(0x0000FF, ParticleSpec.hsb(2f / 3, 1f, 1f));
		assertEquals(0x123456, ParticleSpec.parse("dust:#123456").colorAt(77, 100), "a fixed colour stays");
	}

	@Test
	void auraShapes() {
		for (String shape : AuraShape.SHAPES) {
			List<AuraShape.Point> pts = AuraShape.points(shape, 7, 33f);
			assertFalse(pts.isEmpty(), shape);
			assertTrue(pts.size() <= 60, shape + " draws " + pts.size() + " points a frame");
			for (AuraShape.Point p : pts) {
				assertTrue(Math.abs(p.x()) < 1.6 && Math.abs(p.z()) < 1.6 && p.y() > -0.1 && p.y() < 3.2, shape + " point out of reach: " + p);
			}
		}
		assertTrue(AuraShape.points("nope", 0, 0).isEmpty());
		assertEquals("wings", AuraShape.shapeOf("wings:dust:#FFFFFF"));
		assertEquals("dust:#FFFFFF", AuraShape.particleOf("wings:dust:#FFFFFF"));
		assertEquals("", AuraShape.particleOf("halo"));
	}

	@Test
	void wingsAreOnTheBackAndMirrored() {
		// Facing south (+Z): the back is north (-Z), and the wings spread east and west alike
		List<AuraShape.Point> pts = AuraShape.points("wings", 0, 0f);
		double east = 0;
		double west = 0;
		for (AuraShape.Point p : pts) {
			assertTrue(p.z() < 0, "behind the player: " + p);
			if (p.x() > 0) east += p.x(); else west -= p.x();
		}
		assertEquals(east, west, 1e-6);
		// Facing east (yaw -90): the back is west (-X)
		for (AuraShape.Point p : AuraShape.points("wings", 0, -90f)) {
			assertTrue(p.x() < 0, "behind the player: " + p);
		}
	}

	@Test
	void outfitsSkinsAndCries() {
		assertArrayEquals(new int[] {0xFF0000, 0xFF0000, 0xFF0000}, CosmeticRules.outfitColors("#FF0000"));
		assertArrayEquals(new int[] {0xFF0000, 0x800080, 0x0000FF}, CosmeticRules.outfitColors("#FF0000>#0000FF"));
		assertArrayEquals(new int[] {1, 2, 3}, CosmeticRules.outfitColors("#000001,#000002,#000003"));
		assertNull(CosmeticRules.outfitColors("#FF0000,#00FF00"));
		assertNull(CosmeticRules.outfitColors("red"));
		assertEquals("minecraft:blaze_rod", CosmeticRules.skinModel("blaze_rod;glint"));
		assertTrue(CosmeticRules.skinGlint("blaze_rod;glint"));
		assertFalse(CosmeticRules.skinGlint("minecraft:bone"));
		assertEquals("minecraft:entity.goat.screaming.death", CosmeticRules.crySound("entity.goat.screaming.death;1.3"));
		assertEquals(1.3f, CosmeticRules.cryPitch("entity.goat.screaming.death;1.3"));
		assertEquals(1f, CosmeticRules.cryPitch("minecraft:entity.wither.death"));
		assertEquals(11, CosmeticRules.CATEGORIES.size());
	}
}
