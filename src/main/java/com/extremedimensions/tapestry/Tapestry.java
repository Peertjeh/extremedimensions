package com.extremedimensions.tapestry;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * The Tapestry: the story goes on in the player's own hand. A Weaver's Loom (built once an Elder has fallen) holds the
 * world's tapestry of six panels; each is woven from a thread earned by a great deed (an Elder slain, the Held's letter
 * read, the ending itself), and each, once woven, is a lasting boon that differs between a mended and a severed world.
 * Weaving takes the ending: before it the loom will not take a thread. The sixth panel begins the Weaving (see
 * {@link Weaving}); held, the tapestry is finished and the loom offers New Thread+.
 *
 * <p>The tapestry is the world's: every player's threads go into the same one, on the first loom used (any other is
 * only a loom until that one is gone). A thread lost can be spun back on the loom from Seam Silk by anyone whose deed
 * it was, so long as nobody carries it.
 */
public final class Tapestry {
	private Tapestry() {}

	/** The six panels, in the order the tapestry shows them, each with the advancement its deed gives. */
	public enum Panel {
		ROOTS("roots", Advancements.MATRIARCH_SLAIN),
		TOLL("toll", Advancements.FERRYMAN_SLAIN),
		UNRAVELLING("unravelling", Advancements.WEAVER_SLAIN),
		CHAIN("chain", Advancements.HELD_FOUND),
		KNOT("knot", Advancements.KNOT_MOTHER),
		/** The ending itself: the deed is the world's, not one player's. */
		HEART("heart", null);

		public final String id;
		final String deed;

		Panel(String id, String deed) {
			this.id = id;
			this.deed = deed;
		}

		public Item thread() {
			return switch (this) {
				case ROOTS -> ModItems.THREAD_OF_ROOTS;
				case TOLL -> ModItems.THREAD_OF_TOLL;
				case UNRAVELLING -> ModItems.THREAD_OF_UNRAVELLING;
				case CHAIN -> ModItems.THREAD_OF_THE_CHAIN;
				case KNOT -> ModItems.THREAD_OF_THE_KNOT;
				case HEART -> ModItems.THREAD_OF_THE_HEART;
			};
		}

		public String nameKey() {
			return "gui.extremedimensions.tapestry.panel." + id;
		}

		public String deedKey() {
			return "gui.extremedimensions.tapestry.deed." + id;
		}

		/** What the panel gives in a world of this ending ("mended" or "severed"). */
		public String boonKey(String ending) {
			return "gui.extremedimensions.tapestry.boon." + id + "." + ("severed".equals(ending) ? "severed" : "mended");
		}

		/** The numbers in that line, as the server's settings have them. */
		public Object[] boonArgs(String ending) {
			boolean severed = "severed".equals(ending);
			return switch (this) {
				case ROOTS -> severed ? new Object[0] : new Object[] {Math.max(1, Config.TAPESTRY_ROOTS_GIFTS)};
				case TOLL -> severed ? new Object[] {Math.max(0, Config.TAPESTRY_TOLL_LEVELS)}
						: new Object[] {Math.round((1 - Math.max(0, Math.min(1, Config.TAPESTRY_TOLL_DRAIN))) * 100)};
				case UNRAVELLING -> severed ? new Object[] {Math.max(3, Config.TAPESTRY_UNRAVELLING_SHARE)}
						: new Object[] {Math.max(0, Config.TAPESTRY_UNRAVELLING_SECONDS)};
				case CHAIN -> severed ? new Object[0] : new Object[] {Math.max(0, Config.TAPESTRY_CHAIN_SECONDS) / 60};
				case KNOT -> severed ? new Object[] {Math.round((1 - Math.max(0, Math.min(1, Config.TAPESTRY_KNOT_STRANDS))) * 100)}
						: new Object[] {Config.TAPESTRY_KNOT_FRAY, Config.SEAM_FRAY_DRAIN};
				case HEART -> severed ? new Object[] {Math.max(0, Config.TAPESTRY_HEART_SPARE_MINUTES)}
						: new Object[] {Math.round(Math.max(Math.max(0, Math.min(1, Config.ECHO_RETURN)), Math.min(1, Config.TAPESTRY_HEART_ECHO)) * 100)};
			};
		}

		public static Panel byId(String id) {
			for (Panel p : values()) {
				if (p.id.equals(id)) {
					return p;
				}
			}
			return null;
		}

		/** The panel a thread is woven into, or null for anything else. */
		public static Panel of(ItemStack stack) {
			for (Panel p : values()) {
				if (stack.is(p.thread())) {
					return p;
				}
			}
			return null;
		}
	}

	// ------------------------------------------------------------------ state

	public static boolean woven(Panel p) {
		return DataStore.panels().contains(p.id);
	}

	public static int count() {
		int n = 0;
		for (Panel p : Panel.values()) {
			if (woven(p)) {
				n++;
			}
		}
		return n;
	}

	/** Woven, in a mended world. */
	public static boolean mended(Panel p) {
		return woven(p) && "mended".equals(DataStore.ending());
	}

	/** Woven, in a severed world. */
	public static boolean severed(Panel p) {
		return woven(p) && "severed".equals(DataStore.ending());
	}

	/** Whether this player's deed for a panel is done (the Heart's is the world's: its ending). */
	public static boolean deedDone(ServerPlayer p, Panel panel) {
		return panel.deed == null ? !DataStore.ending().isEmpty() : WorldFx.hasAdvancement(p, panel.deed);
	}

	static boolean carries(ServerPlayer p, Item item) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}

	/** Whether anyone in the world carries this thread now. */
	private static boolean anyoneCarries(MinecraftServer s, Item item) {
		for (ServerPlayer q : s.getPlayerList().getPlayers()) {
			if (carries(q, item)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ the boons

	/** Roots, mended: how many seconds a bound Hollowling's next gift comes nearer each second. */
	public static int giftStep() {
		return mended(Panel.ROOTS) ? Math.max(1, Config.TAPESTRY_ROOTS_GIFTS) : 1;
	}

	/** Roots, severed: whether the tribe still sends its Keepers to hunt those it hates. */
	public static boolean keepersHunt() {
		return !(severed(Panel.ROOTS) && Config.TAPESTRY_ROOTS_PEACE > 0);
	}

	/** Toll, mended: the drain multiplier in this world (only the Nether's is eased). */
	public static double drain(String dim) {
		return mended(Panel.TOLL) && Dims.NETHER.equals(dim) ? Math.max(0, Config.TAPESTRY_TOLL_DRAIN) : 1.0;
	}

	/** Toll, severed: levels given back on a safe return. */
	public static int levelsBack() {
		return severed(Panel.TOLL) ? Math.max(0, Config.TAPESTRY_TOLL_LEVELS) : 0;
	}

	/** Unravelling, mended: seconds more in the Seam. */
	public static int seamSeconds() {
		return mended(Panel.UNRAVELLING) ? Math.max(0, Config.TAPESTRY_UNRAVELLING_SECONDS) : 0;
	}

	/** Unravelling, severed: the Thinned hunt only in the last 1/n of a visitor's time (3 without it). */
	public static int thinShare() {
		return severed(Panel.UNRAVELLING) ? Math.max(3, Config.TAPESTRY_UNRAVELLING_SHARE) : 3;
	}

	/** Chain, mended: seconds more charge each anchor holds. */
	public static int anchorSeconds() {
		return mended(Panel.CHAIN) ? Math.max(0, Config.TAPESTRY_CHAIN_SECONDS) : 0;
	}

	/** Chain, severed: anchors no longer turn unstable. */
	public static boolean steadyAnchors() {
		return severed(Panel.CHAIN) && Config.TAPESTRY_CHAIN_STEADY > 0;
	}

	/** Knot, mended: how fast Seam time runs out in the Fray ({@code normal} without it). */
	public static double frayDrain(double normal) {
		return mended(Panel.KNOT) ? Math.max(0, Math.min(normal, Config.TAPESTRY_KNOT_FRAY)) : normal;
	}

	/** Knot, severed: share of the chance a strand gives way. */
	public static double strandChance() {
		return severed(Panel.KNOT) ? Math.max(0, Math.min(1, Config.TAPESTRY_KNOT_STRANDS)) : 1.0;
	}

	/** Heart, mended: share of what a Collapse cost that its echo gives back ({@code normal} without it). */
	public static double echoShare(double normal) {
		return mended(Panel.HEART) ? Math.max(normal, Math.min(1, Config.TAPESTRY_HEART_ECHO)) : normal;
	}

	/**
	 * Heart, severed: whether this fall beyond the wound keeps what the player carried (once in a while; using it up
	 * starts the wait again).
	 */
	public static boolean spares(ServerPlayer p, PlayerData d) {
		if (!severed(Panel.HEART) || Config.TAPESTRY_HEART_SPARE_MINUTES <= 0) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (d.heartSparedAt > 0 && now - d.heartSparedAt < Config.TAPESTRY_HEART_SPARE_MINUTES * 60_000L && now >= d.heartSparedAt) {
			return false;
		}
		d.heartSparedAt = now;
		DataStore.markDirty();
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.tapestry.spared", Config.TAPESTRY_HEART_SPARE_MINUTES)
				.withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** New Thread+: a multiplier of {@code 1 + per} for every thread the world has taken up. */
	public static double threadFactor(double per) {
		return 1 + Math.max(0, per) * DataStore.newThread();
	}

	// ------------------------------------------------------------------ threads from deeds

	/**
	 * A great deed was done: the one who did it (or stood by) takes its thread, unless its panel is woven already or
	 * they carry it. An Elder's fall also teaches the Weaver's Loom.
	 */
	public static void earn(ServerPlayer p, Panel panel) {
		if (panel == Panel.ROOTS || panel == Panel.TOLL || panel == Panel.UNRAVELLING || panel == Panel.KNOT) {
			Advancements.grant(p, Advancements.LOOM_RECIPE);
		}
		if (woven(panel) || carries(p, panel.thread())) {
			return;
		}
		if (!p.isAlive()) {
			// One who fell by the Elder takes it on rising again, with what Homebound keeps (not into a bag about to be dropped)
			String kept = com.extremedimensions.binding.Homebound.encode(p.level().registryAccess(), new ItemStack(panel.thread()));
			if (kept != null) {
				com.extremedimensions.data.DataStore.get(p.getUUID()).homebound.add(kept);
				com.extremedimensions.data.DataStore.markDirty();
			}
			return;
		}
		WorldFx.give(p, new ItemStack(panel.thread()));
		WorldFx.soundSelf(p, "minecraft:block.amethyst_block.resonate", 0.9f, 0.6f);
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.2, 0.5, 0.02, 16);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.tapestry.thread_earned", Component.translatable(panel.thread().getDescriptionId()))
				.withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	// ------------------------------------------------------------------ the loom

	/** Where a block is, as the world data keeps the loom: "dim|x|y|z" with the world's full id. */
	static String where(ServerLevel level, BlockPos pos) {
		return level.dimension().identifier() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
	}

	/** The loom the tapestry hangs on: {dim, x, y, z} (dim as its full id), or null when there is none. */
	public static Object[] loom() {
		String[] v = DataStore.loom().split("\\|");
		if (v.length != 4) {
			return null;
		}
		try {
			return new Object[] {v[0], Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3])};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Whether the loom the tapestry hung on is gone (none was used yet, or its block was taken away). */
	private static boolean gone() {
		Object[] l = loom();
		if (l == null) {
			return true;
		}
		ServerLevel level = WorldFx.level((String) l[0]);
		BlockPos pos = new BlockPos((int) l[1], (int) l[2], (int) l[3]);
		// (a loom in a part of the world nobody has loaded is taken to stand)
		return level == null || Loaded.ready(level, pos) && !level.getBlockState(pos).is(ModBlocks.WEAVERS_LOOM);
	}

	/**
	 * A player uses a Weaver's Loom: with a thread, to weave its panel; with Seam Silk, to spin a lost thread back; with
	 * the tapestry whole but its Weaving never held, to begin it again; otherwise it shows the tapestry.
	 */
	public static InteractionResult use(ServerPlayer p, BlockPos pos, ItemStack held) {
		ServerLevel level = (ServerLevel) p.level();
		String here = where(level, pos);
		if (!here.equals(DataStore.loom())) {
			if (!gone()) {
				Object[] l = loom();
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.tapestry.other_loom", l[1], l[2], l[3]), true);
				WorldFx.soundSelf(p, "minecraft:ui.loom.select_pattern", 0.6f, 0.6f);
				return InteractionResult.SUCCESS;
			}
			DataStore.setLoom(here);
		}
		PlayerData d = DataStore.get(p.getUUID());
		String ending = DataStore.ending();
		if (!ending.isEmpty()) {
			// The first time at the loom after the ending, the journal goes on in the player's own hand
			Journal.unlock(p, d, "mended".equals(ending) ? JournalEntry.CONTINUED_MENDED : JournalEntry.CONTINUED_SEVERED);
			DataStore.markDirty();
		}
		// Tetherite or a Tether Cord in hand: the Unweaving, where a full curse set is unpicked
		if (Config.UNWEAVING > 0 && (held.is(ModItems.TETHERITE_INGOT) || held.is(ModItems.TETHER_CORD))) {
			Unweaving.open(p);
			return InteractionResult.SUCCESS;
		}
		Panel panel = Panel.of(held);
		if (panel != null) {
			return weave(p, level, pos, held, panel);
		}
		if (held.is(ModItems.SEAM_SILK) && respin(p, held)) {
			return InteractionResult.SUCCESS;
		}
		if (!ending.isEmpty() && count() == Panel.values().length && !DataStore.tapestryDone() && !Weaving.active()
				&& System.currentTimeMillis() >= DataStore.weaveAgainAt()) {
			Weaving.start(level, pos);
			return InteractionResult.SUCCESS;
		}
		open(p);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult weave(ServerPlayer p, ServerLevel level, BlockPos pos, ItemStack held, Panel panel) {
		String ending = DataStore.ending();
		if (ending.isEmpty()) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.tapestry.not_yet"), true);
			WorldFx.soundSelf(p, "minecraft:block.wool.break", 0.8f, 0.5f);
			return InteractionResult.SUCCESS;
		}
		if (woven(panel)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.tapestry.already", Component.translatable(panel.nameKey())), true);
			return InteractionResult.SUCCESS;
		}
		if (Weaving.active()) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.tapestry.busy"), true);
			return InteractionResult.SUCCESS;
		}
		long wait = DataStore.weaveAgainAt() - System.currentTimeMillis();
		if (wait > 0 && panel.id.equals(DataStore.lastPanel())) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.tapestry.not_again", Math.max(1, (wait + 59_999) / 60_000)), true);
			WorldFx.soundSelf(p, "minecraft:block.wool.break", 0.8f, 0.5f);
			return InteractionResult.SUCCESS;
		}
		if (!p.isCreative()) {
			held.shrink(1);
		}
		DataStore.panels().add(panel.id);
		DataStore.setLastPanel(panel.id);
		String dim = level.dimension().identifier().toString();
		double x = pos.getX() + 0.5, y = pos.getY() + 1.1, z = pos.getZ() + 0.5;
		WorldFx.particleIn(dim, "extremedimensions:soul_mote", x, y, z, 0.5, 0.5, 0.5, 0.02, 40);
		WorldFx.particleIn(dim, "minecraft:end_rod", x, y + 0.4, z, 0.3, 0.5, 0.3, 0.03, 16);
		WorldFx.soundIn(dim, "minecraft:ui.loom.take_result", "block", x, y, z, 1.0f, 0.7f);
		WorldFx.soundIn(dim, "minecraft:block.amethyst_block.resonate", "block", x, y, z, 1.0f, 0.5f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.tapestry.woven", Component.translatable(panel.nameKey()),
				Component.translatable(panel.boonKey(ending), panel.boonArgs(ending))).withStyle(ChatFormatting.LIGHT_PURPLE));
		com.extremedimensions.compat.DiscordWebhook.panel(panel, p);
		if (count() == Panel.values().length && !DataStore.tapestryDone()) {
			Weaving.start(level, pos);
		} else {
			open(p);
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Seam Silk on the loom spins back the first thread whose deed this player did, whose panel is not woven and which
	 * nobody carries. False when there is none to spin (the loom then only shows the tapestry).
	 */
	static boolean respin(ServerPlayer p, ItemStack silk) {
		MinecraftServer s = ((ServerLevel) p.level()).getServer();
		for (Panel panel : Panel.values()) {
			if (woven(panel) || !deedDone(p, panel) || anyoneCarries(s, panel.thread())) {
				continue;
			}
			int cost = Math.max(0, Config.TAPESTRY_RESPIN_SILK);
			if (!p.isCreative() && silk.getCount() < cost) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.tapestry.more_silk", cost, Component.translatable(panel.thread().getDescriptionId())), true);
				return true;
			}
			if (!p.isCreative()) {
				silk.shrink(cost);
			}
			WorldFx.give(p, new ItemStack(panel.thread()));
			WorldFx.soundSelf(p, "minecraft:ui.loom.take_result", 1.0f, 0.9f);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.tapestry.respun", Component.translatable(panel.thread().getDescriptionId())), true);
			return true;
		}
		return false;
	}

	/** Shows this player the tapestry (and refreshes it if it is open). */
	public static void open(ServerPlayer p) {
		ServerPlayNetworking.send(p, new ModPackets.TapestryPayload(data(p)));
	}

	/**
	 * The tapestry screen's data: "woven;ending;deeds;carried;done;threads;weaving;minutes until the unravelled panel is
	 * taken again;last panel", the masks with one bit per panel in order.
	 */
	public static String data(ServerPlayer p) {
		int woven = 0, deeds = 0, carried = 0;
		for (Panel panel : Panel.values()) {
			int bit = 1 << panel.ordinal();
			woven |= woven(panel) ? bit : 0;
			deeds |= deedDone(p, panel) ? bit : 0;
			carried |= carries(p, panel.thread()) ? bit : 0;
		}
		long wait = Math.max(0, DataStore.weaveAgainAt() - System.currentTimeMillis());
		return woven + ";" + DataStore.ending() + ";" + deeds + ";" + carried + ";" + (DataStore.tapestryDone() ? 1 : 0) + ";" + DataStore.newThread()
				+ ";" + (Weaving.active() ? 1 : 0) + ";" + (wait + 59_999) / 60_000 + ";" + DataStore.lastPanel();
	}

	// ------------------------------------------------------------------ New Thread+

	/**
	 * New Thread+: a player at the finished tapestry takes up a harder thread for the whole world. The Elders grow
	 * stronger and atonement dearer with every thread, the Rift Trials go on past gold, the Matriarch comes again for
	 * those she fell to, and the tapestry's boons stay.
	 */
	public static void newThread(ServerPlayer p) {
		Object[] l = loom();
		if (!DataStore.tapestryDone() || l == null || !l[0].equals(p.level().dimension().identifier().toString())
				|| p.distanceToSqr((int) l[1] + 0.5, (int) l[2] + 0.5, (int) l[3] + 0.5) > 8 * 8) {
			return;
		}
		int tier = DataStore.newThread() + 1;
		DataStore.setNewThread(tier);
		for (PlayerData d : DataStore.players()) {
			d.matriarchSlain = false;
		}
		DataStore.markDirty();
		MinecraftServer s = ((ServerLevel) p.level()).getServer();
		for (ServerPlayer q : s.getPlayerList().getPlayers()) {
			q.sendSystemMessage(Component.translatable("gui.extremedimensions.tapestry.new_thread", p.getName(), tier).withStyle(ChatFormatting.DARK_PURPLE));
			WorldFx.soundSelf(q, "minecraft:block.end_portal.spawn", 0.5f, 0.6f);
			Advancements.grant(q, Advancements.NEW_THREAD);
		}
		open(p);
	}

	// ------------------------------------------------------------------ ticking

	/** Every two seconds: a finished tapestry keeps soul light round its loom for whoever is near. */
	public static void tick(MinecraftServer s) {
		Weaving.tick(s);
		Unweaving.tick(s);
		if (s.getTickCount() % 40 != 0 || !DataStore.tapestryDone()) {
			return;
		}
		Object[] l = loom();
		ServerLevel level = l == null ? null : WorldFx.level((String) l[0]);
		if (level == null) {
			return;
		}
		double x = (int) l[1] + 0.5, y = (int) l[2] + 1.5, z = (int) l[3] + 0.5;
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(x, y, z) < 32 * 32) {
				WorldFx.particleIn((String) l[0], "extremedimensions:soul_mote", x, y + 1, z, 3.0, 2.0, 3.0, 0.01, 8);
				WorldFx.particleIn((String) l[0], "minecraft:end_rod", x, y, z, 0.6, 0.8, 0.6, 0.005, 2);
				return;
			}
		}
	}

	public static void clear() {
		Weaving.clear();
		Unweaving.clear();
	}
}
