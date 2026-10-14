package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.p;

import net.battlehub.config.ConfigManager;
import net.battlehub.gui.HubMenu;
import net.battlehub.gui.MainMenu;
import net.battlehub.gui.MenuGuard;
import net.battlehub.lobby.HubItems;
import net.battlehub.util.Icon;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Menu items must never end up in a player's inventory: not by clicks (sorting mods click every slot), not by
 * creative slot packets (creative clients set their own slots), not by any other route (swept away).
 */
final class MenuSecurityTests {
	private MenuSecurityTests() {}

	/** Inventory-menu index of hotbar slot {@code i}. */
	private static short hotbar(int i) {
		return (short) (36 + i);
	}

	private static int menuItems(ServerPlayer p) {
		int n = 0;
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (MenuGuard.isMenuItem(inv.getItem(i))) n++;
		}
		if (MenuGuard.isMenuItem(p.containerMenu.getCarried())) n++;
		return n;
	}

	private static boolean isPane(ItemStack it) {
		return it.is(net.battlehub.util.Ids.item("minecraft:black_stained_glass_pane"))
				|| it.is(net.battlehub.util.Ids.item("minecraft:gray_stained_glass_pane"));
	}

	private static void creativeSet(ServerPlayer p, short slot, ItemStack stack) {
		p.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(slot, stack));
	}

	static void all(MinecraftServer server, Script s) {
		String tag = "menu security";
		SelfTest.reset(s, tag);
		s.run(tag + ": A is in creative mode with the main menu open", () -> {
			SelfTest.join(server, "BHTestA");
			ServerPlayer a = p("BHTestA");
			a.setGameMode(GameType.CREATIVE);
			a.getInventory().setItem(0, ItemStack.EMPTY);
			a.getInventory().setItem(1, ItemStack.EMPTY);
			new MainMenu(a).open();
		});
		s.check(tag + ": every menu item is marked", () -> {
			HubMenu m = (HubMenu) p("BHTestA").containerMenu;
			for (int i = 0; i < m.getRowCount() * 9; i++) {
				ItemStack it = m.getSlot(i).getItem();
				if (!it.isEmpty() && !MenuGuard.isMenuItem(it)) return false;
			}
			return true;
		});
		s.run(tag + ": a sorting mod clicks every slot", () -> {
			ServerPlayer a = p("BHTestA");
			HubMenu m = (HubMenu) a.containerMenu;
			int top = m.getRowCount() * 9;
			for (ContainerInput input : new ContainerInput[] {ContainerInput.QUICK_MOVE, ContainerInput.PICKUP_ALL,
					ContainerInput.SWAP, ContainerInput.CLONE, ContainerInput.THROW, ContainerInput.QUICK_CRAFT}) {
				for (int i = 0; i < m.slots.size(); i++) {
					// Shift-clicking a button presses it, by design; every other click hits every slot
					boolean button = i < top && !isPane(m.getSlot(i).getItem());
					if (button && input == ContainerInput.QUICK_MOVE) continue;
					m.clicked(i, 0, input, a);
				}
			}
			m.clicked(net.minecraft.world.inventory.AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, 0, ContainerInput.PICKUP, a);
		});
		s.pause(2);
		s.check(tag + ": nothing came out of the menu", () -> menuItems(p("BHTestA")) == 0
				&& p("BHTestA").containerMenu instanceof HubMenu m && m.getSlot(10).getItem().is(Items.DIAMOND_SWORD));
		s.run(tag + ": the creative client puts a menu item in its hotbar", () -> {
			ServerPlayer a = p("BHTestA");
			HubMenu m = (HubMenu) a.containerMenu;
			creativeSet(a, hotbar(0), m.getSlot(10).getItem().copy());
			creativeSet(a, hotbar(1), m.getSlot(0).getItem().copy());
		});
		s.check(tag + ": refused", () -> menuItems(p("BHTestA")) == 0 && p("BHTestA").getInventory().getItem(0).isEmpty());
		s.run(tag + ": the same client strips the mark and tries again", () -> {
			ServerPlayer a = p("BHTestA");
			ItemStack plain = ((HubMenu) a.containerMenu).getSlot(10).getItem().copy();
			plain.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
			creativeSet(a, hotbar(0), plain);
		});
		s.check(tag + ": refused while a BattleHub menu is open", () -> p("BHTestA").getInventory().getItem(0).isEmpty());
		s.run(tag + ": a menu item reaches the inventory some other way", () -> {
			ServerPlayer a = p("BHTestA");
			a.getInventory().setItem(1, ((HubMenu) a.containerMenu).getSlot(12).getItem().copy());
		});
		s.waitFor(tag + ": swept away within a tick", 3, () -> menuItems(p("BHTestA")) == 0);
		s.run(tag + ": close the menu", () -> p("BHTestA").closeContainer());
		s.run(tag + ": the creative client clears and copies the compass", () -> {
			ServerPlayer a = p("BHTestA");
			int slot = ConfigManager.config().compass.slot;
			ItemStack compass = a.getInventory().getItem(slot).copy();
			creativeSet(a, hotbar(slot), ItemStack.EMPTY);
			creativeSet(a, hotbar(slot == 1 ? 2 : 1), compass);
		});
		s.check(tag + ": the compass stays put and is not copied", () -> {
			ServerPlayer a = p("BHTestA");
			int n = 0;
			for (int i = 0; i < a.getInventory().getContainerSize(); i++) {
				if (HubItems.COMPASS.equals(Icon.tagOf(a.getInventory().getItem(i)))) n++;
			}
			return n == 1 && HubItems.COMPASS.equals(Icon.tagOf(a.getInventory().getItem(ConfigManager.config().compass.slot)));
		});
		s.run(tag + ": outside the menu creative works as usual", () -> creativeSet(p("BHTestA"), hotbar(0), new ItemStack(Items.STONE, 7)));
		s.check(tag + ": the stone arrived", () -> p("BHTestA").getInventory().getItem(0).is(Items.STONE));
		s.run(tag + ": back to survival", () -> {
			ServerPlayer a = p("BHTestA");
			a.getInventory().setItem(0, ItemStack.EMPTY);
			a.setGameMode(GameType.SURVIVAL);
		});
	}
}
