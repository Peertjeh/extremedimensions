package net.battlehub.games.bedwars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.config.BedWarsConfig;
import net.battlehub.core.bedwars.Resource;
import net.battlehub.core.bedwars.Shop;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * The Bed Wars shop for one match: offers from bedwars.json, the tiers every player owns (armour, pickaxe, axe,
 * shears), the team upgrades, and the player's inventory as the wallet. Every purchase goes through
 * {@link Shop#buy}: checked first, then paid exactly, then handed out.
 */
public final class BedWarsShop {
	/** Tag on armour that stays on (it cannot be taken off or dropped). */
	public static final String ARMOR_TAG = "game.armor";
	public static final String[] CATEGORIES = {"blocks", "weapons", "armor", "tools", "utility", "upgrades"};

	private final BedWarsConfig cfg;
	private final Map<String, BedWarsConfig.Offer> raw = new LinkedHashMap<>();
	private final Map<String, Shop.Offer> offers = new LinkedHashMap<>();
	private final Map<UUID, Map<String, Integer>> owned = new HashMap<>();
	private final Map<Integer, Map<String, Integer>> teamUpgrades = new HashMap<>();
	private final Map<String, BedWarsConfig.Upgrade> upgrades = new LinkedHashMap<>();

	/** How the match tells the shop about teams and colours. */
	public interface Teams {
		int teamOf(UUID id);

		String color(int team);

		int leather(int team);

		List<ServerPlayer> online(int team);

		void upgraded(int team, String upgrade, int level, ServerPlayer buyer);
	}

	private final Teams teams;

	public BedWarsShop(BedWarsConfig cfg, Teams teams) {
		this.cfg = cfg;
		this.teams = teams;
		for (BedWarsConfig.Offer o : cfg.shop) {
			Resource cur = Resource.byId(o.currency);
			if (cur == null) continue;
			raw.put(o.id, o);
			offers.put(o.id, new Shop.Offer(o.id, o.category, o.item, o.count, cur, o.price, o.group, o.tier));
		}
		for (BedWarsConfig.Upgrade u : cfg.upgrades) {
			upgrades.put(u.id, u);
		}
	}

	public List<Shop.Offer> offers(String category) {
		List<Shop.Offer> out = new ArrayList<>();
		for (Shop.Offer o : offers.values()) {
			if (o.category().equals(category)) out.add(o);
		}
		return out;
	}

	public Shop.Offer offer(String id) {
		return offers.get(id);
	}

	public List<BedWarsConfig.Upgrade> upgrades() {
		return List.copyOf(upgrades.values());
	}

	public Map<String, Integer> owned(UUID id) {
		return owned.computeIfAbsent(id, k -> new HashMap<>());
	}

	public int upgradeLevel(int team, String upgrade) {
		return teamUpgrades.getOrDefault(team, Map.of()).getOrDefault(upgrade, 0);
	}

	// ------------------------------------------------------------------ buying

	public Shop.Result check(ServerPlayer p, Shop.Offer o) {
		return Shop.check(o, new InventoryWallet(p), owned(p.getUUID()));
	}

	public Shop.Result buy(ServerPlayer p, String offerId) {
		Shop.Offer o = offers.get(offerId);
		if (o == null) {
			return Shop.Result.TOO_EXPENSIVE;
		}
		Shop.Result r = Shop.buy(o, new InventoryWallet(p), owned(p.getUUID()));
		if (r == Shop.Result.BOUGHT) {
			p.inventoryMenu.sendAllDataToRemote();
		}
		return r;
	}

	/** Price of the next level of a team upgrade, or -1 when it is at the top. */
	public int nextPrice(int team, String upgrade) {
		BedWarsConfig.Upgrade u = upgrades.get(upgrade);
		if (u == null) return -1;
		int lvl = upgradeLevel(team, upgrade);
		return lvl >= u.prices.length ? -1 : u.prices[lvl];
	}

	public Resource upgradeCurrency(String upgrade) {
		BedWarsConfig.Upgrade u = upgrades.get(upgrade);
		Resource r = u == null ? null : Resource.byId(u.currency);
		return r == null ? Resource.DIAMOND : r;
	}

	public Shop.Result buyUpgrade(ServerPlayer p, String upgrade) {
		int team = teams.teamOf(p.getUUID());
		int price = nextPrice(team, upgrade);
		if (team < 0 || price < 0) {
			return Shop.Result.ALREADY_OWNED;
		}
		InventoryWallet w = new InventoryWallet(p);
		Resource cur = upgradeCurrency(upgrade);
		if (w.count(cur) < price || !w.take(cur, price)) {
			return Shop.Result.TOO_EXPENSIVE;
		}
		int level = upgradeLevel(team, upgrade) + 1;
		teamUpgrades.computeIfAbsent(team, k -> new HashMap<>()).put(upgrade, level);
		for (ServerPlayer m : teams.online(team)) {
			applyUpgrades(m);
		}
		teams.upgraded(team, upgrade, level, p);
		p.inventoryMenu.sendAllDataToRemote();
		return Shop.Result.BOUGHT;
	}

	// ------------------------------------------------------------------ items

	/** What an offer hands out (without upgrades applied to it yet). */
	public ItemStack stack(ServerPlayer p, Shop.Offer o) {
		int team = teams.teamOf(p.getUUID());
		String id = o.item().replace("{team}", teams.color(Math.max(0, team)));
		if (o.isUpgrade() && o.group().equals("armor")) {
			id = "minecraft:" + id + "_leggings";
		}
		Item item = Ids.item(id);
		if (item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		ItemStack s = new ItemStack(item, Math.max(1, Math.min(item.getDefaultMaxStackSize(), o.count())));
		BedWarsConfig.Offer r = raw.get(o.id());
		if (r != null && r.enchantments != null) {
			for (Map.Entry<String, Integer> e : r.enchantments.entrySet()) {
				Ids.enchantment(p.level().getServer(), e.getKey()).ifPresent(h -> s.enchant(h, Math.max(1, e.getValue())));
			}
		}
		if (s.isDamageableItem()) {
			s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		}
		return s;
	}

	private static String path(ItemStack s) {
		return Ids.key(s.getItem()).replace("minecraft:", "");
	}

	private static boolean isSword(ItemStack s) {
		return !s.isEmpty() && path(s).endsWith("_sword");
	}

	private static boolean inGroup(ItemStack s, String group) {
		if (s.isEmpty()) return false;
		String p = path(s);
		return switch (group) {
			case "pickaxe" -> p.endsWith("_pickaxe");
			case "axe" -> p.endsWith("_axe") && !p.endsWith("_pickaxe");
			case "shears" -> p.equals("shears");
			default -> false;
		};
	}

	/** The player's inventory as a wallet: counts and takes resources, checks the room and hands out goods. */
	final class InventoryWallet implements Shop.Wallet {
		private final ServerPlayer p;
		private final Inventory inv;

		InventoryWallet(ServerPlayer p) {
			this.p = p;
			this.inv = p.getInventory();
		}

		@Override
		public int count(Resource r) {
			return inv.countItem(Ids.item(r.item));
		}

		@Override
		public boolean take(Resource r, int amount) {
			Item item = Ids.item(r.item);
			if (amount <= 0) {
				return true;
			}
			if (inv.countItem(item) < amount) {
				return false;
			}
			int left = amount;
			for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
				ItemStack s = inv.getItem(i);
				if (s.is(item)) {
					int n = Math.min(left, s.getCount());
					s.shrink(n);
					if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
					left -= n;
				}
			}
			inv.setChanged();
			return left == 0;
		}

		@Override
		public boolean canFit(Shop.Offer o) {
			if (o.isUpgrade() && o.group().equals("armor")) {
				return true;
			}
			ItemStack s = stack(p, o);
			if (s.isEmpty()) {
				return false;
			}
			if (o.isUpgrade() && replaceSlot(o.group()) >= 0) {
				return true;
			}
			if (isSword(s) && woodenSwordSlot() >= 0) {
				return true;
			}
			return room(s) >= s.getCount();
		}

		@Override
		public void give(Shop.Offer o) {
			ItemStack s = stack(p, o);
			if (o.isUpgrade() && o.group().equals("armor")) {
				equipArmor(p, o.item());
				return;
			}
			applyUpgrades(p, s);
			int slot = o.isUpgrade() ? replaceSlot(o.group()) : -1;
			if (slot < 0 && isSword(s) && !path(s).equals("wooden_sword")) {
				slot = woodenSwordSlot();
			}
			if (slot >= 0) {
				inv.setItem(slot, s);
				return;
			}
			add(p, s);
		}

		/** Room for this stack in the main inventory (36 slots). */
		private int room(ItemStack s) {
			int room = 0;
			for (int i = 0; i < 36; i++) {
				ItemStack in = inv.getItem(i);
				if (in.isEmpty()) {
					room += s.getMaxStackSize();
				} else if (ItemStack.isSameItemSameComponents(in, s)) {
					room += Math.max(0, in.getMaxStackSize() - in.getCount());
				}
			}
			return room;
		}

		private int replaceSlot(String group) {
			for (int i = 0; i < 36; i++) {
				if (inGroup(inv.getItem(i), group)) return i;
			}
			return -1;
		}

		private int woodenSwordSlot() {
			for (int i = 0; i < 36; i++) {
				if (inv.getItem(i).is(Items.WOODEN_SWORD)) return i;
			}
			return -1;
		}
	}

	/** Into the inventory; what does not fit (should not happen after the room check) falls at the player's feet. */
	static void add(ServerPlayer p, ItemStack s) {
		p.getInventory().add(s);
		if (!s.isEmpty()) {
			ItemEntity e = new ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), s);
			e.setNoPickUpDelay();
			p.level().addFreshEntity(e);
		}
	}

	/** Leggings and boots of this material (chainmail, iron, diamond), locked on, with the team's protection. */
	void equipArmor(ServerPlayer p, String material) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			String piece = slot == EquipmentSlot.LEGS ? "_leggings" : "_boots";
			Item item = Ids.item("minecraft:" + material + piece);
			if (item == Items.AIR) continue;
			ItemStack s = new ItemStack(item);
			s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
			p.setItemSlot(slot, s);
		}
		lockArmor(p);
		applyUpgrades(p);
	}

	/** Armour cannot be taken off or dropped; leather is dyed in the team colour. */
	void lockArmor(ServerPlayer p) {
		int color = teams.leather(teams.teamOf(p.getUUID()));
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack s = p.getItemBySlot(slot);
			if (s.isEmpty()) continue;
			if (path(s).startsWith("leather_")) {
				s.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
			}
			Icon.mark(s, ARMOR_TAG);
		}
	}

	/** Team sharpness on every sword and team protection on all armour this player wears. */
	public void applyUpgrades(ServerPlayer p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty()) applyUpgrades(p, s);
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack s = p.getItemBySlot(slot);
			if (!s.isEmpty()) applyUpgrades(p, s);
		}
	}

	private void applyUpgrades(ServerPlayer p, ItemStack s) {
		int team = teams.teamOf(p.getUUID());
		if (team < 0) return;
		String path = path(s);
		int sharp = upgradeLevel(team, "sharpness");
		if (sharp > 0 && path.endsWith("_sword")) {
			Ids.enchantment(p.level().getServer(), "minecraft:sharpness").ifPresent(h -> s.enchant(h, sharp));
		}
		int prot = upgradeLevel(team, "protection");
		if (prot > 0 && (path.endsWith("_helmet") || path.endsWith("_chestplate") || path.endsWith("_leggings") || path.endsWith("_boots"))) {
			Ids.enchantment(p.level().getServer(), "minecraft:protection").ifPresent(h -> s.enchant(h, prot));
		}
	}

	/**
	 * After the start kit (wooden sword, leather armour) was handed out at a (re)spawn: the bought armour tier, the
	 * tools at their current tier, shears, and the team upgrades.
	 */
	public void equip(ServerPlayer p) {
		Map<String, Integer> mine = owned(p.getUUID());
		int armor = mine.getOrDefault("armor", 0);
		lockArmor(p);
		if (armor > 0) {
			for (Shop.Offer o : offers.values()) {
				if ("armor".equals(o.group()) && o.tier() == armor) {
					equipArmor(p, o.item());
				}
			}
		}
		for (String group : new String[] {"pickaxe", "axe", "shears"}) {
			int tier = mine.getOrDefault(group, 0);
			if (tier <= 0) continue;
			for (Shop.Offer o : offers.values()) {
				if (group.equals(o.group()) && o.tier() == tier) {
					ItemStack s = stack(p, o);
					if (!s.isEmpty()) add(p, s);
					break;
				}
			}
		}
		applyUpgrades(p);
		p.inventoryMenu.sendAllDataToRemote();
	}

	/** A death costs one tier of pickaxe and axe (never below the first). */
	public void downgradeTools(UUID id) {
		Map<String, Integer> mine = owned(id);
		for (String group : new String[] {"pickaxe", "axe"}) {
			int tier = mine.getOrDefault(group, 0);
			if (tier > 1) mine.put(group, tier - 1);
		}
	}

	/** Takes all iron, gold, diamonds and emeralds out of an inventory (for the killer). */
	public static List<ItemStack> takeResources(ServerPlayer p) {
		List<ItemStack> out = new ArrayList<>();
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && Resource.byItem(Ids.key(s.getItem())) != null) {
				out.add(s.copy());
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		return out;
	}

	public void forget(UUID id) {
		owned.remove(id);
	}
}
