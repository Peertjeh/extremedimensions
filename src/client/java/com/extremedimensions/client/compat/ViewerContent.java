package com.extremedimensions.client.compat;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.anchor.FuelValues;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.tetherite.SpindleBlock;
import com.extremedimensions.tribe.BarterService;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the recipe viewers show beside the mod's recipes (3.35): every one of those is an ordinary crafting, smelting,
 * smithing or stonecutting recipe the viewer finds by itself, but much of the mod is had in other ways. Here are the
 * anchors' fuel, the young's barter and gifts, the Spindle, what each creature and find leaves, and a page for every
 * item no recipe makes, saying where it comes from.
 *
 * <p>Kept apart from any viewer's own API, so it loads without one and a client test can check it.
 */
public final class ViewerContent {
	private ViewerContent() {}

	/** A fuel and the charge it gives an anchor of its world (seconds). */
	public record Fuel(ItemStack fuel, boolean nether, double seconds) {
		public Component line() {
			return Component.translatable(nether ? "viewer.extremedimensions.fuel.nether" : "viewer.extremedimensions.fuel.end", Ui.time(seconds));
		}
	}

	/**
	 * A ware of the young or a gift for them: what they want (each stack one choice), what they give (none for a gift),
	 * when they offer it, and a word on what it costs beside.
	 */
	public record Trade(List<ItemStack> want, List<ItemStack> give, Component when, Component more) {
		public boolean gift() {
			return give.isEmpty();
		}
	}

	/** The Spindle: what a Tether Cord takes, and how many turns. */
	public record Spin(List<ItemStack> inputs, ItemStack cord, int turns) {}

	/** What a creature or a find leaves: one slot per thing (a slot of several is one of them, as a relic is). */
	public record Spoils(Component source, List<List<ItemStack>> slots) {}

	/** A page for an item: where it comes from and what it is for. */
	public record Info(ItemStack stack, Component text) {}

	private static ItemStack stack(String id, int count) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(ExtremeDimensions.MOD_ID, id));
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
	}

	/** Every fuel, Nether first, each world's from the least charge to the most. */
	public static List<Fuel> fuels() {
		List<Fuel> out = new ArrayList<>();
		for (boolean nether : new boolean[] {true, false}) {
			List<Fuel> part = new ArrayList<>();
			(nether ? FuelValues.NETHER : FuelValues.END).forEach((item, seconds) -> part.add(new Fuel(new ItemStack(item), nether, seconds)));
			part.sort(Comparator.comparingDouble(Fuel::seconds));
			out.addAll(part);
		}
		return out;
	}

	/** A ware's price as a tolerated player pays it (trust takes some off). */
	private static int price(BarterService.Ware w) {
		return Math.max(1, (int) Math.ceil(w.price() * Math.max(0, Config.TRIBE_BARTER_PRICE)));
	}

	private static Trade ware(BarterService.Ware w, Component when) {
		List<ItemStack> give = new ArrayList<>();
		if (w.id() == BarterService.RELIC) {
			for (Item relic : ModItems.RELICS.values()) {
				give.add(new ItemStack(relic));
			}
		} else {
			give.add(new ItemStack(w.give(), w.count()));
		}
		return new Trade(List.of(new ItemStack(w.want(), price(w))), give, when, Component.translatable("viewer.extremedimensions.barter.cost"));
	}

	/** The young's wares (the fragment every day, the daily ones, the trusted's and the kin's) and then their gifts. */
	public static List<Trade> trades() {
		List<Trade> out = new ArrayList<>();
		out.add(ware(BarterService.ware(BarterService.FRAGMENT), Component.translatable("viewer.extremedimensions.barter.always")));
		for (BarterService.Ware w : BarterService.DAILY) {
			out.add(ware(w, Component.translatable("viewer.extremedimensions.barter.daily", w.uses())));
		}
		if (Config.CLAN_ENABLED > 0) {
			out.add(ware(BarterService.ware(BarterService.CORD), Component.translatable("viewer.extremedimensions.barter.trusted")));
		}
		if (Config.TRIBE_BARTER_RELIC_PRICE > 0) {
			out.add(ware(BarterService.ware(BarterService.RELIC), Component.translatable("viewer.extremedimensions.barter.kin")));
		}
		out.add(ware(BarterService.ware(BarterService.CLOTH), Component.translatable("viewer.extremedimensions.barter.kin")));
		BarterService.LIKES.forEach((item, count) -> out.add(new Trade(List.of(new ItemStack(item, count)), List.of(),
				Component.translatable("viewer.extremedimensions.barter.gift"), Component.translatable("viewer.extremedimensions.barter.gift.desc"))));
		return out;
	}

	/** The Spindle, as the settings have it. */
	public static Spin spin() {
		List<ItemStack> in = new ArrayList<>();
		in.add(new ItemStack(ModItems.TETHER_FRAGMENT, SpindleBlock.fragmentsPerCord()));
		if (SpindleBlock.silkPerCord() > 0) {
			in.add(new ItemStack(ModItems.SEAM_SILK, SpindleBlock.silkPerCord()));
		}
		return new Spin(in, new ItemStack(ModItems.TETHER_CORD), SpindleBlock.turnsPerCord());
	}

	/** Where each loot table's items come from: the loot table under gameplay/, and the name of who or what leaves it. */
	private static final String[][] SOURCES = {{"matriarch_spoils", "entity.extremedimensions.matriarch"},
			{"ferryman_spoils", "entity.extremedimensions.ferryman"}, {"weaver_spoils", "entity.extremedimensions.weaver"},
			{"knot_mother_spoils", "entity.extremedimensions.knot_mother"}, {"keeper_spoils", "entity.extremedimensions.keeper"},
			{"gatebreaker_spoils", "entity.extremedimensions.gatebreaker"}, {"lockwarden_spoils", "entity.extremedimensions.lockwarden"},
			{"tidewalker_haul", "entity.extremedimensions.tidewalker"}, {"edgecrawler_spoils", "entity.extremedimensions.edgecrawler"},
			{"cragback_spoils", "entity.extremedimensions.cragback"}, {"veil_ray", "entity.extremedimensions.veil_ray"},
			{"frayed_ray", "entity.extremedimensions.frayed_ray"}, {"thinned", "entity.extremedimensions.thinned"},
			{"thinned_fray", "viewer.extremedimensions.source.thinned_fray"}, {"lurker_offering", "viewer.extremedimensions.source.lurker_offering"},
			{"hollowling_gift", "viewer.extremedimensions.source.hollowling_gift"}, {"trial_gold", "viewer.extremedimensions.source.trial_gold"},
			{"pale_hauler_spoils", "entity.extremedimensions.pale_hauler"},
			{"pale_mason_spoils", "entity.extremedimensions.pale_mason"}, {"pale_anchor", "viewer.extremedimensions.source.pale_anchor"}};

	/**
	 * What the game gives besides a loot table, by the loot table's name (or a creature's, for those without one): the
	 * Elders' fragments of their elegy (two to every fighter), their thread for the tapestry and a relic; a relic with a
	 * Keeper and the Starved One; What Listens from the Cave and Sculk Lurkers.
	 */
	private static final Map<String, String[]> BESIDES = Map.of("matriarch_spoils", new String[] {"disc_fragment_elegy*2", "thread_of_roots", "relic"},
			"ferryman_spoils", new String[] {"disc_fragment_elegy*2", "thread_of_toll", "relic"},
			"weaver_spoils", new String[] {"disc_fragment_elegy*2", "thread_of_unravelling", "relic"},
			"knot_mother_spoils", new String[] {"disc_fragment_elegy*2", "thread_of_the_knot", "relic"},
			"pale_mason_spoils", new String[] {"disc_fragment_elegy*2"},
			"keeper_spoils", new String[] {"relic"}, "starved_one", new String[] {"relic"},
			"cave_lurker", new String[] {"music_disc_what_listens"}, "sculk_lurker", new String[] {"music_disc_what_listens"});

	/** Those that leave something without a loot table of their own, after the loot tables' sources. */
	private static final String[][] WITHOUT_TABLE = {{"starved_one", "entity.extremedimensions.starved_one"},
			{"cave_lurker", "entity.extremedimensions.cave_lurker"}, {"sculk_lurker", "entity.extremedimensions.sculk_lurker"}};

	/** Every relic, as one slot (one of them is left). */
	private static List<ItemStack> relics() {
		List<ItemStack> out = new ArrayList<>();
		for (Item relic : ModItems.RELICS.values()) {
			out.add(new ItemStack(relic));
		}
		return out;
	}

	private static void besides(String source, List<List<ItemStack>> slots) {
		for (String b : BESIDES.getOrDefault(source, new String[0])) {
			if ("relic".equals(b)) {
				slots.add(relics());
				continue;
			}
			String[] idCount = b.split("\\*");
			ItemStack s = stack(idCount[0], idCount.length > 1 ? Integer.parseInt(idCount[1]) : 1);
			if (!s.isEmpty()) {
				slots.add(List.of(s));
			}
		}
	}

	/**
	 * What every creature and find leaves: what the mod's own loot tables hold (a datapack's changes are not seen here),
	 * and what the game gives besides.
	 */
	public static List<Spoils> spoils() {
		List<Spoils> out = new ArrayList<>();
		for (String[] s : SOURCES) {
			Map<Item, Integer> items = new LinkedHashMap<>();
			Optional<Path> file = FabricLoader.getInstance().getModContainer(ExtremeDimensions.MOD_ID)
					.flatMap(c -> c.findPath("data/" + ExtremeDimensions.MOD_ID + "/loot_table/gameplay/" + s[0] + ".json"));
			if (file.isPresent()) {
				try (Reader r = Files.newBufferedReader(file.get())) {
					collect(JsonParser.parseReader(r), items);
				} catch (Exception e) {
					ExtremeDimensions.LOGGER.warn("Could not read the loot table {} for the recipe viewer: {}", s[0], e.toString());
				}
			}
			List<List<ItemStack>> slots = new ArrayList<>();
			items.forEach((item, count) -> slots.add(List.of(new ItemStack(item, count))));
			besides(s[0], slots);
			if (!slots.isEmpty()) {
				out.add(new Spoils(Component.translatable(s[1]), slots));
			}
		}
		for (String[] s : WITHOUT_TABLE) {
			List<List<ItemStack>> slots = new ArrayList<>();
			besides(s[0], slots);
			out.add(new Spoils(Component.translatable(s[1]), slots));
		}
		return out;
	}

	/** Every item entry in a loot table (with a plain count when it sets one), however deep it lies. */
	private static void collect(JsonElement e, Map<Item, Integer> into) {
		if (e.isJsonArray()) {
			for (JsonElement c : e.getAsJsonArray()) {
				collect(c, into);
			}
			return;
		}
		if (!e.isJsonObject()) {
			return;
		}
		JsonObject o = e.getAsJsonObject();
		if (o.has("type") && "minecraft:item".equals(o.get("type").getAsString()) && o.has("name")) {
			Identifier id = Identifier.tryParse(o.get("name").getAsString());
			Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
			if (item != Items.AIR) {
				into.merge(item, Math.max(1, count(o)), Math::max);
			}
			return;
		}
		for (Map.Entry<String, JsonElement> c : o.entrySet()) {
			collect(c.getValue(), into);
		}
	}

	/** The count a set_count inside an entry gives (the top of a range), or 0. */
	private static int count(JsonElement e) {
		if (e.isJsonArray()) {
			int best = 0;
			for (JsonElement c : (JsonArray) e) {
				best = Math.max(best, count(c));
			}
			return best;
		}
		if (!e.isJsonObject()) {
			return 0;
		}
		JsonObject o = e.getAsJsonObject();
		if (o.has("type") && "minecraft:set_count".equals(o.get("type").getAsString()) && o.has("count")) {
			JsonElement c = o.get("count");
			if (c.isJsonPrimitive() && c.getAsJsonPrimitive().isNumber()) {
				return Math.min(64, (int) Math.round(c.getAsDouble()));
			}
			if (c.isJsonObject() && c.getAsJsonObject().has("max") && c.getAsJsonObject().get("max").isJsonPrimitive()) {
				return Math.min(64, (int) Math.round(c.getAsJsonObject().get("max").getAsDouble()));
			}
			return 0;
		}
		int best = 0;
		for (Map.Entry<String, JsonElement> c : o.entrySet()) {
			best = Math.max(best, count(c.getValue()));
		}
		return best;
	}

	/** The items with a page of their own, by id (the relics share one, below). */
	public static final String[] INFO = {"tether_fragment", "wanderers_page", "loose_leaf", "seam_silk", "tether_cord", "tetherite_scrap",
			"tetherite_ore", "seam_vein", "veil_wing", "blood_cord", "mending_thread", "severing_shears", "homeward_keystone", "pale_lock", "echo_thread", "ember_thread",
			"pale_thread", "silk_thread", "thread_of_roots", "thread_of_toll", "thread_of_unravelling", "thread_of_the_chain",
			"thread_of_the_knot", "thread_of_the_heart", "music_disc_tick_of_the_boundary", "music_disc_wound_remembers",
			"music_disc_what_listens", "disc_fragment_elegy", "disc_fragment_abyss", "helds_chain"};

	private static String percent(double v) {
		return Math.round(v * 100) + "%";
	}

	/** The Binding's odds as this world's settings have them, for the template's and the ingot's pages. */
	private static Component binding() {
		int where = Math.max(0, Math.min(2, Config.TETHERITE_BIND_WHERE));
		Component place = where == 1 ? Component.translatable("viewer.extremedimensions.info.binding.where.1", percent(Config.TETHERITE_BIND_SEAM))
				: Component.translatable("viewer.extremedimensions.info.binding.where." + where);
		return Component.translatable("viewer.extremedimensions.info.binding", percent(Config.TETHERITE_BIND_CHANCE), place,
				percent(Config.TETHERITE_BIND_CORD), percent(Config.TETHERITE_BIND_SCAR));
	}

	/** Every item page, in the order above: those with their own words, the relics, the Binding's and the lodestone's. */
	public static List<Info> infos() {
		List<Info> out = new ArrayList<>();
		for (String id : INFO) {
			ItemStack s = stack(id, 1);
			if (!s.isEmpty()) {
				out.add(new Info(s, Component.translatable("viewer.extremedimensions.info." + id)));
			}
		}
		for (Map.Entry<com.extremedimensions.relic.Relic, Item> e : ModItems.RELICS.entrySet()) {
			if (!"helds_chain".equals(BuiltInRegistries.ITEM.getKey(e.getValue()).getPath())) {
				// (whether a relic serves once, as this world has it)
				out.add(new Info(new ItemStack(e.getValue()), Component.translatable(Config.RELIC_SINGLE_USE > 0 ? "viewer.extremedimensions.info.relic"
						: "viewer.extremedimensions.info.relic_kept")));
			}
		}
		out.add(new Info(new ItemStack(ModItems.TETHERITE_UPGRADE_SMITHING_TEMPLATE), binding()));
		out.add(new Info(new ItemStack(ModItems.TETHERITE_INGOT), binding()));
		out.add(new Info(new ItemStack(Items.LODESTONE), Component.translatable("viewer.extremedimensions.info.lodestone")));
		return out;
	}
}
