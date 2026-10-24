package net.battlehub.gui;

import java.util.function.Consumer;

import net.battlehub.util.Icon;
import net.battlehub.util.Msg;
import net.battlehub.util.Tasks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * A vanilla chest menu whose items are buttons. Every click is cancelled, so nothing can be taken out, moved,
 * dropped or shift-clicked; the server resends the real contents and runs the button action on the next tick.
 */
public final class HubMenu extends ChestMenu {
	private final Screen screen;
	private final SimpleContainer box;
	private Canvas canvas;
	private boolean resyncQueued;

	private HubMenu(int id, Inventory inv, Screen screen, SimpleContainer box) {
		super(type(screen.rows()), id, inv, box, screen.rows());
		this.screen = screen;
		this.box = box;
		redraw();
	}

	static HubMenu create(int id, Inventory inv, Screen screen) {
		int rows = Math.max(1, Math.min(6, screen.rows()));
		return new HubMenu(id, inv, screen, new SimpleContainer(rows * 9));
	}

	private static MenuType<?> type(int rows) {
		return switch (Math.max(1, Math.min(6, rows))) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	public Screen screen() {
		return screen;
	}

	public void redraw() {
		Canvas c = new Canvas(screen.viewer(), getRowCount(), screen.section());
		try {
			screen.draw(c);
		} catch (Exception e) {
			net.battlehub.BattleHub.LOGGER.error("Menu {} failed to draw", screen.getClass().getSimpleName(), e);
		}
		canvas = c;
		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack item = c.item(i);
			if (!item.isEmpty() && !Icon.isHubItem(item)) {
				// Recognisable anywhere: if it ever leaves the menu, MenuGuard removes it.
				item = item.copy();
				Icon.mark(item, Icon.MENU);
			}
			box.setItem(i, item);
		}
		// The player's tick broadcasts the changed slots to the client.
	}

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player player) {
		if (!(player instanceof ServerPlayer sp) || canvas == null) {
			return;
		}
		// Nothing below moves an item. The client may have predicted a move (sorting mods send many clicks at
		// once), so the real contents are sent back right after this tick's packets.
		resyncSoon(sp);
		if (slot < 0 || slot >= box.getContainerSize()) {
			return;
		}
		if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE) {
			return;
		}
		Consumer<Canvas.Click> action = canvas.action(slot);
		if (action == null) {
			return;
		}
		Canvas.Click click = new Canvas.Click(sp, slot, button, input);
		Msg.sound(sp, SoundEvents.UI_BUTTON_CLICK, 0.6f, 1.0f);
		// Leave the packet handler first: actions may open another menu or teleport the player.
		Tasks.next(() -> {
			if (sp.containerMenu == this && !sp.hasDisconnected()) {
				action.accept(click);
			}
		});
	}

	private void resyncSoon(ServerPlayer sp) {
		if (resyncQueued) {
			return;
		}
		resyncQueued = true;
		Tasks.next(() -> {
			resyncQueued = false;
			MenuGuard.sweep(sp);
			if (sp.containerMenu == this) {
				sendAllDataToRemote();
			}
		});
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (player instanceof ServerPlayer sp) {
			MenuGuard.sweep(sp);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
