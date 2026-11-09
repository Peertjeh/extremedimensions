package com.extremedimensions.reach;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * The Homing Pearl (3.40), the Pale Mason's: what his Endermen made to find the way home, and never could use. Used out
 * in the End on an expedition, it takes its holder home to the Void Anchor feeding their expedition at once, whatever
 * stands in the way (the expedition ends as any return home does); then it is spent.
 */
public final class HomingPearl {
	private HomingPearl() {}

	/** The pearl used: home, or a word on why not. */
	public static InteractionResult use(ServerPlayer p, ItemStack stack) {
		PlayerData d = DataStore.get(p.getUUID());
		if (!d.inRun || !Dims.END.equals(Dims.of(p))) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.homing_pearl.idle").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.FAIL;
		}
		Anchor home = home(d);
		if (home == null) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.homing_pearl.lost").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.FAIL;
		}
		WorldFx.particleIn(p.level(), "minecraft:portal", p.getX(), p.getY() + 1, p.getZ(), 0.6, 1, 0.6, 1.2, 80);
		WorldFx.soundIn(Dims.of(p), "minecraft:entity.enderman.teleport", "players", p.getX(), p.getY() + 1, p.getZ(), 1.2f, 0.7f);
		WorldFx.tp(p, Dims.OVERWORLD, home.x + 0.5, home.y + 1, home.z + 0.5);
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		WorldFx.soundSelf(p, "minecraft:block.respawn_anchor.set_spawn", 1f, 0.8f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.homing_pearl.home").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		return InteractionResult.SUCCESS;
	}

	/** The Void Anchor feeding this expedition (the first of them), or null. */
	public static Anchor home(PlayerData d) {
		for (Anchor a : AnchorService.linked(d)) {
			if (Dims.END.equals(a.type)) {
				return a;
			}
		}
		return null;
	}
}
