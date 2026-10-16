package net.battlehub.progression;

import net.battlehub.bot.Bots;
import net.battlehub.core.CosmeticDef;
import net.battlehub.core.CosmeticRules;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.hub.HubWorld;
import net.battlehub.lang.Lang;
import net.battlehub.session.Sessions;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * The wardrobe: a hat and an outfit (dyed leather, one colour or a fade) worn in the lobby only. The pieces are locked BattleHub items;
 * the kit replaces them in a match and they are never taken along to the normal world. Armour of the player's
 * own in those slots is left alone.
 */
public final class Wardrobe {
	private Wardrobe() {}

	/** Tag prefix of wardrobe pieces. */
	public static final String TAG = "wardrobe:";

	public static boolean isPiece(String tag) {
		return tag.startsWith(TAG);
	}

	/** Puts on (or takes off) what the player chose; only in the lobby. */
	public static void apply(ServerPlayer p) {
		if (!HubWorld.is(p.level()) || !Sessions.inLobby(p.getUUID()) || p.isSpectator() || Bots.isBot(p)) {
			return;
		}
		PlayerProfile prof = PlayerStore.get(p);
		CosmeticDef hat = CosmeticRules.active(prof, "hat", Cosmetics.all());
		CosmeticDef outfit = CosmeticRules.active(prof, "outfit", Cosmetics.all());
		set(p, EquipmentSlot.HEAD, hat == null || !Ids.isItem(hat.effect) ? ItemStack.EMPTY : piece(p, hat, hat.effect, -1));
		// One colour, or a fade from the chestplate down to the boots
		int[] rgb = outfit == null ? null : CosmeticRules.outfitColors(outfit.effect);
		set(p, EquipmentSlot.CHEST, rgb == null ? ItemStack.EMPTY : piece(p, outfit, "minecraft:leather_chestplate", rgb[0]));
		set(p, EquipmentSlot.LEGS, rgb == null ? ItemStack.EMPTY : piece(p, outfit, "minecraft:leather_leggings", rgb[1]));
		set(p, EquipmentSlot.FEET, rgb == null ? ItemStack.EMPTY : piece(p, outfit, "minecraft:leather_boots", rgb[2]));
	}

	private static ItemStack piece(ServerPlayer p, CosmeticDef c, String item, int rgb) {
		ItemStack s = Icon.of(Ids.item(item)).name(Lang.colored(c.display)).lore(Lang.lore(p, "wardrobe.lore"))
				.tag(TAG + c.id).clean().build();
		if (rgb >= 0) {
			s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		}
		return s;
	}

	private static void set(ServerPlayer p, EquipmentSlot slot, ItemStack want) {
		ItemStack current = p.getItemBySlot(slot);
		String tag = Icon.tagOf(current);
		boolean ours = isPiece(tag);
		if (!current.isEmpty() && !ours) {
			return;
		}
		if (want.isEmpty()) {
			if (ours) p.setItemSlot(slot, ItemStack.EMPTY);
			return;
		}
		if (ours && tag.equals(Icon.tagOf(want)) && current.getItem() == want.getItem()) {
			return;
		}
		p.setItemSlot(slot, want);
	}
}
