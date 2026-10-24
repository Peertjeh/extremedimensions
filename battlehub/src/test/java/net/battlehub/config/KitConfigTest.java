package net.battlehub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The mace kit gets a sword (1.46), in a new kits.json and in one from before. */
class KitConfigTest {
	private static KitConfig.KitItem item(String id, int count, String slot) {
		KitConfig.KitItem it = new KitConfig.KitItem();
		it.item = id;
		it.count = count;
		it.slot = slot;
		return it;
	}

	/** The mace kit as it came out of the box before 1.46. */
	private static KitConfig.Kit oldMace() {
		KitConfig.Kit k = new KitConfig.Kit();
		k.items.add(item("minecraft:mace", 1, "0"));
		k.items.add(item("minecraft:wind_charge", 16, "1"));
		k.items.add(item("minecraft:golden_apple", 2, "2"));
		for (String part : new String[] {"helmet:head", "chestplate:chest", "leggings:legs", "boots:feet"}) {
			String[] p = part.split(":");
			k.items.add(item("minecraft:diamond_" + p[0], 1, p[1]));
		}
		return k;
	}

	private static String slotOf(KitConfig.Kit k, String id) {
		return k.items.stream().filter(it -> it.item.equals(id)).map(it -> it.slot).findFirst().orElse(null);
	}

	/** The numeric slots in kit order (what a player's layout is about). */
	private static List<String> numeric(KitConfig.Kit k) {
		return k.items.stream().filter(it -> it.slot.matches("\\d+")).map(it -> it.item).toList();
	}

	@Test
	void aNewKitHasTheSwordFirstInTheHotbarAndLastInTheList() {
		KitConfig c = new KitConfig().normalize();
		KitConfig.Kit mace = c.kits.get("mace");
		assertEquals("0", slotOf(mace, "minecraft:diamond_sword"));
		assertEquals("1", slotOf(mace, "minecraft:mace"));
		assertEquals(List.of("minecraft:mace", "minecraft:wind_charge", "minecraft:golden_apple", "minecraft:diamond_sword"), numeric(mace));
		assertEquals(2, c.configVersion);
	}

	@Test
	void theOldKitBecomesTheNewOne() {
		KitConfig c = new KitConfig();
		c.kits.put("mace", oldMace());
		c.normalize();
		KitConfig.Kit mace = c.kits.get("mace");
		assertEquals(KitConfig.defaults().get("mace").items.size(), mace.items.size());
		for (int i = 0; i < mace.items.size(); i++) {
			KitConfig.KitItem want = KitConfig.defaults().get("mace").items.get(i), got = mace.items.get(i);
			assertEquals(want.item, got.item);
			assertEquals(want.slot, got.slot);
			assertEquals(want.count, got.count);
		}
		// once only: someone who takes the sword out again keeps it out
		mace.items.removeIf(it -> it.item.endsWith("_sword"));
		c.normalize();
		assertTrue(c.kits.get("mace").items.stream().noneMatch(it -> it.item.endsWith("_sword")));
	}

	@Test
	void aChangedKitKeepsEverythingAndGetsTheSwordInAFreeSlot() {
		KitConfig c = new KitConfig();
		KitConfig.Kit mace = oldMace();
		mace.items.get(1).count = 64;
		mace.items.add(3, item("minecraft:ender_pearl", 16, "3"));
		c.kits.put("mace", mace);
		c.normalize();
		assertEquals("0", slotOf(mace, "minecraft:mace"));
		assertEquals("1", slotOf(mace, "minecraft:wind_charge"));
		assertEquals("3", slotOf(mace, "minecraft:ender_pearl"));
		assertEquals("4", slotOf(mace, "minecraft:diamond_sword"));
		assertEquals("minecraft:diamond_sword", numeric(mace).get(numeric(mace).size() - 1), "the sword last, so old layouts keep working");
	}

	@Test
	void aKitWithASwordStaysAsItIs() {
		KitConfig c = new KitConfig();
		KitConfig.Kit mace = oldMace();
		mace.items.add(item("minecraft:netherite_sword", 1, "5"));
		c.kits.put("mace", mace);
		int before = mace.items.size();
		c.normalize();
		assertEquals(before, mace.items.size());
		assertEquals("0", slotOf(mace, "minecraft:mace"));
	}
}
