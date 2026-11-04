package com.extremedimensions.tetherite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

import com.extremedimensions.config.Config;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.registry.ModEnchantments;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.seam.SeamDepth;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The binding (3.26): netherite takes Tetherite only if the metal holds. Taking the result of a Tetherite upgrade from
 * the smithing table rolls its chance; if it fails the ingot shatters, the piece is worn and keeps a scar, and the
 * template survives (see {@link Config#TETHERITE_BIND_KEEP_TEMPLATE}).
 *
 * <p>The chance starts at {@link Config#TETHERITE_BIND_CHANCE}. Enchantments that hold a piece together raise it
 * (Unbreaking for each level, Mending, Knitting, and the Curse of Binding: the metal takes to what is already bound);
 * every other curse lowers it. Luck raises it and Bad Luck lowers it, the Seam (and the Fray below it) raises it, a Tether
 * Cord held in the other hand raises it and is spent on the try, and every scar of an earlier failure on the piece raises
 * the next try. It never goes below {@link Config#TETHERITE_BIND_MIN} nor above {@link Config#TETHERITE_BIND_MAX}. In
 * Creative the binding always holds.
 *
 * <p>The player sees the chance and what makes it beside the smithing table while a binding waits there (sent by
 * {@link #tick}).
 *
 * <p>Where it can be done is a setting of its own ({@link Config#TETHERITE_BIND_WHERE}, 3.26.1): anywhere, only in the
 * Seam (the Fray included), or only down in the Fray. Elsewhere the table shows the result but will not let it be
 * taken, and the panel says where to take it.
 */
public final class TetheriteBinding {
	private TetheriteBinding() {}

	/** The lore line a failed binding leaves on the piece (its one argument the number of scars). */
	public static final String SCAR_KEY = "item.extremedimensions.binding_scars";

	/** The roll, in [0, 1): a binding holds when it falls below the chance (the tests set it). */
	public static DoubleSupplier roll = () -> ThreadLocalRandom.current().nextDouble();

	/** One share of the chance: what it is ({@code kind}: base, ench, luck, seam, fray, cord, scar), a name and a level. */
	public record Part(String kind, String label, int level, double share) {}

	/** A binding's chance (clamped) and the shares it was made of. */
	public record Odds(double chance, List<Part> parts, boolean capped) {}

	/** Set while a shift-click already rolled the binding, so the take that follows does not roll it again. */
	private static final ThreadLocal<Boolean> ROLLED = ThreadLocal.withInitial(() -> false);

	/** Set on the client from what the server sent: where the binding waiting at its table would have to be done (0: here). */
	public static volatile int clientBlocked;

	/** When each player was last told where a binding can be done (game time), so it is said once a second at most. */
	private static final Map<UUID, Long> TOLD = new ConcurrentHashMap<>();

	/** The chance each player was last shown (in permille; -1 for none), so it is sent only when it changes. */
	private static final Map<UUID, Integer> SHOWN = new ConcurrentHashMap<>();

	/** True when the result is a Tetherite upgrade of a piece that is not Tetherite yet. */
	public static boolean isBinding(ItemStack base, ItemStack result) {
		return Tetherite.isGear(result) && !base.isEmpty() && !Tetherite.isGear(base);
	}

	/** True when the menu holds a binding ready to be taken. */
	public static boolean pending(SmithingMenu menu) {
		return isBinding(menu.getSlot(SmithingMenu.BASE_SLOT).getItem(), menu.getSlot(SmithingMenu.RESULT_SLOT).getItem());
	}

	// ------------------------------------------------------------------ where

	/** Where a binding would have to be done for this player: 0 here, 1 in the Seam, 2 down in the Fray. Creative: here. */
	public static int needed(ServerPlayer p) {
		int where = Math.max(0, Math.min(2, Config.TETHERITE_BIND_WHERE));
		if (where == 0 || p.isCreative()) {
			return 0;
		}
		boolean ok = where == 1 ? SeamService.inSeam(p) : SeamService.inSeam(p) && SeamDepth.inFray(p);
		return ok ? 0 : where;
	}

	/**
	 * Whether the player may take a binding's result here (the table asks before anything moves). Refused, they are
	 * told where the metal binds, once a second at most.
	 */
	public static boolean mayTake(ServerPlayer p) {
		int need = needed(p);
		if (need == 0) {
			return true;
		}
		long now = p.level().getGameTime();
		Long last = TOLD.get(p.getUUID());
		if (last == null || now - last >= 20) {
			TOLD.put(p.getUUID(), now);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.binding.where." + need).withStyle(ChatFormatting.RED), true);
			WorldFx.soundSelf(p, "minecraft:block.chain.hit", 0.8f, 0.6f);
		}
		return false;
	}

	// ------------------------------------------------------------------ the chance

	/** The chance a player binding this piece has now, and what makes it. */
	public static Odds odds(Player p, ItemStack base) {
		List<Part> parts = new ArrayList<>();
		double chance = Config.TETHERITE_BIND_CHANCE;
		parts.add(new Part("base", "", 0, Config.TETHERITE_BIND_CHANCE));
		for (var e : base.getEnchantments().entrySet()) {
			Holder<Enchantment> h = e.getKey();
			int level = e.getIntValue();
			double share;
			if (h.is(Enchantments.UNBREAKING)) {
				share = Config.TETHERITE_BIND_UNBREAKING * level;
			} else if (h.is(Enchantments.MENDING)) {
				share = Config.TETHERITE_BIND_MENDING;
			} else if (h.is(ModEnchantments.KNITTING)) {
				share = Config.TETHERITE_BIND_KNITTING;
			} else if (h.is(Enchantments.BINDING_CURSE)) {
				share = Config.TETHERITE_BIND_BINDING_CURSE;
			} else if (h.is(EnchantmentTags.CURSE)) {
				share = -Config.TETHERITE_BIND_CURSE;
			} else {
				continue;
			}
			if (share != 0) {
				chance += share;
				parts.add(new Part("ench", key(h), level, share));
			}
		}
		int luck = level(p, MobEffects.LUCK) - level(p, MobEffects.UNLUCK);
		if (luck != 0 && Config.TETHERITE_BIND_LUCK != 0) {
			double share = Config.TETHERITE_BIND_LUCK * luck;
			chance += share;
			parts.add(new Part("luck", "", luck, share));
		}
		if (p instanceof ServerPlayer sp && SeamService.inSeam(sp)) {
			if (Config.TETHERITE_BIND_SEAM != 0) {
				chance += Config.TETHERITE_BIND_SEAM;
				parts.add(new Part("seam", "", 0, Config.TETHERITE_BIND_SEAM));
			}
			if (SeamDepth.inFray(sp) && Config.TETHERITE_BIND_FRAY != 0) {
				chance += Config.TETHERITE_BIND_FRAY;
				parts.add(new Part("fray", "", 0, Config.TETHERITE_BIND_FRAY));
			}
		}
		if (cord(p) && Config.TETHERITE_BIND_CORD != 0) {
			chance += Config.TETHERITE_BIND_CORD;
			parts.add(new Part("cord", "", 0, Config.TETHERITE_BIND_CORD));
		}
		int scars = Math.min(scars(base), Math.max(0, Config.TETHERITE_BIND_SCARS_MAX));
		if (scars > 0 && Config.TETHERITE_BIND_SCAR != 0) {
			double share = Config.TETHERITE_BIND_SCAR * scars;
			chance += share;
			parts.add(new Part("scar", "", scars, share));
		}
		double low = Math.max(0, Math.min(1, Config.TETHERITE_BIND_MIN));
		double high = Math.max(low, Math.min(1, Config.TETHERITE_BIND_MAX));
		double clamped = Math.max(low, Math.min(high, chance));
		return new Odds(clamped, parts, chance > high);
	}

	private static int level(Player p, Holder<net.minecraft.world.effect.MobEffect> effect) {
		MobEffectInstance i = p.getEffect(effect);
		return i == null ? 0 : i.getAmplifier() + 1;
	}

	/** An enchantment's name key (what the client shows it by). */
	private static String key(Holder<Enchantment> h) {
		return h.value().description().getContents() instanceof TranslatableContents tc ? tc.getKey()
				: h.unwrapKey().map(k -> "enchantment." + k.identifier().getNamespace() + "." + k.identifier().getPath()).orElse("");
	}

	/** True when the player holds a Tether Cord in the other hand. */
	public static boolean cord(Player p) {
		return p.getOffhandItem().is(ModItems.TETHER_CORD);
	}

	// ------------------------------------------------------------------ scars

	/** The scars of failed bindings a piece carries (a line of its lore). */
	public static int scars(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return 0;
		}
		for (Component line : lore.lines()) {
			if (line.getContents() instanceof TranslatableContents tc && SCAR_KEY.equals(tc.getKey()) && tc.getArgs().length > 0) {
				Object a = tc.getArgs()[0];
				try {
					return Integer.parseInt((a instanceof Component c ? c.getString() : String.valueOf(a)).trim());
				} catch (NumberFormatException e) {
					return 1;
				}
			}
		}
		return 0;
	}

	/** Sets the scars a piece carries (0 takes the line off), keeping the rest of its lore. */
	public static void setScars(ItemStack stack, int n) {
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		List<Component> keep = new ArrayList<>();
		for (Component line : lore.lines()) {
			if (!(line.getContents() instanceof TranslatableContents tc && SCAR_KEY.equals(tc.getKey()))) {
				keep.add(line);
			}
		}
		if (n > 0) {
			keep.add(Component.translatable(SCAR_KEY, Component.literal(String.valueOf(n))).withStyle(ChatFormatting.DARK_RED));
		}
		if (keep.isEmpty()) {
			stack.remove(DataComponents.LORE);
		} else {
			stack.set(DataComponents.LORE, new ItemLore(keep));
		}
	}

	// ------------------------------------------------------------------ the try

	/**
	 * A player takes a binding's result from the table (the take itself, see the smithing mixins). Rolls the chance
	 * (unless a shift-click rolled it already) and spends a Tether Cord held for it. True when the binding holds and the
	 * take goes on; false when it failed: the result is gone, the ingot shattered and the piece worn and scarred.
	 */
	public static boolean take(ServerPlayer p, SmithingMenu menu, ItemStack result) {
		if (ROLLED.get()) {
			ROLLED.set(false);
			held(p, result);
			return true;
		}
		if (attempt(p, menu)) {
			held(p, result);
			return true;
		}
		result.setCount(0);
		return false;
	}

	/**
	 * A shift-click on a binding's result: rolls before anything moves. True when the move may go on (the binding held,
	 * the take that follows does not roll again); false when nothing moves (it failed, or there is no room for it).
	 */
	public static boolean quickMove(ServerPlayer p, SmithingMenu menu) {
		if (p.getInventory().getFreeSlot() < 0 || !mayTake(p)) {
			return false;
		}
		if (attempt(p, menu)) {
			ROLLED.set(true);
			return true;
		}
		return false;
	}

	/** After a shift-click: nothing left rolled for a take that did not come. */
	public static void quickMoveDone() {
		ROLLED.set(false);
	}

	/** Rolls a binding at this table: the cord is spent either way; a failure is dealt with here. */
	static boolean attempt(ServerPlayer p, SmithingMenu menu) {
		ItemStack base = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
		if (p.isCreative()) {
			return true;
		}
		Odds odds = odds(p, base);
		if (cord(p)) {
			p.getOffhandItem().shrink(1);
		}
		boolean holds = roll.getAsDouble() < odds.chance();
		if (!holds) {
			fail(p, menu, odds);
		}
		SHOWN.remove(p.getUUID());
		return holds;
	}

	/** A failed binding: the ingot shatters, the template stays (if so set), the piece is worn and keeps a scar. */
	private static void fail(ServerPlayer p, SmithingMenu menu, Odds odds) {
		var additionSlot = menu.getSlot(SmithingMenu.ADDITIONAL_SLOT);
		ItemStack ingot = additionSlot.getItem().copy();
		ingot.shrink(1);
		additionSlot.set(ingot);
		if (Config.TETHERITE_BIND_KEEP_TEMPLATE == 0) {
			var templateSlot = menu.getSlot(SmithingMenu.TEMPLATE_SLOT);
			ItemStack template = templateSlot.getItem().copy();
			template.shrink(1);
			templateSlot.set(template);
		}
		var baseSlot = menu.getSlot(SmithingMenu.BASE_SLOT);
		ItemStack piece = baseSlot.getItem().copy();
		if (piece.isDamageableItem()) {
			int max = piece.getMaxDamage();
			int wear = (int) Math.ceil(max * Math.max(0, Math.min(0.9, Config.TETHERITE_BIND_WEAR)));
			piece.setDamageValue(Math.min(max - 1, piece.getDamageValue() + wear));
		}
		int scars = Math.min(scars(piece) + 1, Math.max(0, Config.TETHERITE_BIND_SCARS_MAX));
		setScars(piece, scars);
		baseSlot.set(piece);
		WorldFx.sound(p, "minecraft:block.anvil.destroy", 0.8f, 0.6f);
		WorldFx.sound(p, "minecraft:block.chain.break", 1.0f, 0.5f);
		WorldFx.particles(p, "minecraft:smoke", 1.0, 0.3, 0.02, 16);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.binding.failed", Math.round(odds.chance() * 100))
				.withStyle(ChatFormatting.RED), true);
	}

	/** A binding that held: the scars go with the old metal, and the table rings. */
	private static void held(ServerPlayer p, ItemStack result) {
		if (scars(result) > 0) {
			setScars(result, 0);
		}
		if (!p.isCreative()) {
			WorldFx.sound(p, "minecraft:block.chain.place", 1.0f, 0.7f);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.binding.held").withStyle(ChatFormatting.DARK_AQUA), true);
		}
	}

	// ------------------------------------------------------------------ what the player sees

	/** Twice a second: the chance of a binding waiting at the table a player has open, sent when it changes. */
	public static void tick(ServerPlayer p) {
		int now = -1;
		Odds odds = null;
		int need = 0;
		if (p.containerMenu instanceof SmithingMenu menu && pending(menu)) {
			odds = p.isCreative() ? new Odds(1, List.of(), false) : odds(p, menu.getSlot(SmithingMenu.BASE_SLOT).getItem());
			now = (int) Math.round(odds.chance() * 1000);
			need = needed(p);
		}
		Integer was = SHOWN.get(p.getUUID());
		int signature = odds == null ? -1 : (now * 31 + odds.parts().hashCode()) * 3 + need;
		if (was == null ? signature == -1 : was == signature) {
			return;
		}
		SHOWN.put(p.getUUID(), signature);
		if (!ServerPlayNetworking.canSend(p, ModPackets.BindingPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(p, odds == null ? new ModPackets.BindingPayload(-1, List.of(), false, 0)
				: new ModPackets.BindingPayload(now, odds.parts(), odds.capped(), need));
	}

	public static void forget(UUID id) {
		SHOWN.remove(id);
		TOLD.remove(id);
	}
}
