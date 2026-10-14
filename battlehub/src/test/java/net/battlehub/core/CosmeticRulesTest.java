package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CosmeticRulesTest {
	private final CosmeticDef flames = new CosmeticDef("flames", "trail", "Flames", "minecraft:blaze_powder", 400, "flame");
	private final CosmeticDef hearts = new CosmeticDef("hearts", "trail", "Hearts", "minecraft:poppy", 600, "heart");
	private final CosmeticDef red = new CosmeticDef("red", "color", "Red", "minecraft:red_dye", 300, "red");

	@Test
	void buyAndSelect() {
		PlayerProfile p = new PlayerProfile().normalize();
		p.coins = 1000;
		assertFalse(CosmeticRules.select(p, flames), "not owned yet");
		assertEquals(CosmeticRules.BuyResult.BOUGHT, CosmeticRules.buy(p, flames));
		assertEquals(600, p.coins);
		assertEquals(CosmeticRules.BuyResult.ALREADY_OWNED, CosmeticRules.buy(p, flames));
		assertEquals(600, p.coins);
		assertTrue(CosmeticRules.select(p, flames));
		assertEquals(flames, CosmeticRules.active(p, "trail", List.of(flames, hearts, red)));
		assertEquals(CosmeticRules.BuyResult.BOUGHT, CosmeticRules.buy(p, hearts));
		assertEquals(0, p.coins);
		assertTrue(CosmeticRules.select(p, hearts));
		assertEquals(hearts, CosmeticRules.active(p, "trail", List.of(flames, hearts, red)), "one per category");
		assertEquals(CosmeticRules.BuyResult.TOO_EXPENSIVE, CosmeticRules.buy(p, red));
		assertNull(CosmeticRules.active(p, "color", List.of(flames, hearts, red)));
		CosmeticRules.clear(p, "trail");
		assertNull(CosmeticRules.active(p, "trail", List.of(flames, hearts, red)));
	}

	@Test
	void removedCosmeticsAreIgnored() {
		PlayerProfile p = new PlayerProfile().normalize();
		p.ownedCosmetics.add("old");
		p.cosmetics.put("trail", "old");
		assertNull(CosmeticRules.active(p, "trail", List.of(flames)));
	}
}
