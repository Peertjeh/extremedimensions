package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.Fx;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.server.WorldFx;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * What an Elder's ultimate leaves behind when it breaks: every player within its reach takes extreme curses (two by
 * default, at random among those that still have room). Someone whose extreme curses are all full cannot be cursed
 * further, so the ultimate takes it out on what they carry instead: everything is flung out around them.
 */
public final class ElderUltimate {
	private ElderUltimate() {}

	/** Whether this share of health is the moment for the ultimate (once per fight). */
	static boolean due(ElderBoss b, boolean spent) {
		double at = Config.ELDER_ULTIMATE_AT;
		return !spent && at > 0 && b.getHealth() <= b.getMaxHealth() * at;
	}

	/** The ultimate's length in ticks. */
	static int length() {
		return Math.max(2, Config.ELDER_ULTIMATE_SECONDS) * 20;
	}

	/** Lays the curse on everyone within reach; returns how many it touched. */
	public static int curse(ElderBoss b, String bossKey) {
		ServerLevel level = (ServerLevel) b.level();
		double r = Math.max(0, Config.ELDER_ULTIMATE_RADIUS);
		int n = 0;
		for (ServerPlayer p : List.copyOf(level.players())) {
			if (p.isSpectator() || p.isCreative() || !p.isAlive() || p.distanceToSqr(b) > r * r) {
				continue;
			}
			strike(p, bossKey);
			n++;
		}
		return n;
	}

	/** One player: extreme curses, or, when they have no room for them, everything they carry flung around them. */
	public static void strike(ServerPlayer p, String bossKey) {
		PlayerData d = DataStore.get(p.getUUID());
		List<Penalty> room = new ArrayList<>(Penalty.available(d, Penalty.Group.EXTREME));
		Collections.shuffle(room);
		int want = Math.max(0, Config.ELDER_ULTIMATE_CURSES);
		List<Penalty> laid = new ArrayList<>();
		for (Penalty pen : room) {
			if (laid.size() >= want) {
				break;
			}
			if (d.addStacks(pen, 1) > 0) {
				laid.add(pen);
			}
		}
		if (!laid.isEmpty()) {
			CurseEffects.applyEffects(p, d);
			WorldFx.fx(p, Fx.PENALTY, 0);
			WorldFx.particles(p, "minecraft:sculk_soul", 1.2, 0.6, 0.03, 30);
			Journal.unlock(p, d, JournalEntry.SCARS);
			MutableComponent names = Component.empty();
			for (int i = 0; i < laid.size(); i++) {
				if (i > 0) {
					names.append(", ");
				}
				names.append(Component.translatable(laid.get(i).nameKey()));
			}
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ultimate_curse", Component.translatable(bossKey), names));
		}
		if (laid.size() < want && Config.ELDER_ULTIMATE_SCATTER > 0) {
			if (scatter(p) > 0) {
				WorldFx.soundSelf(p, "minecraft:entity.item.break", 1.0f, 0.5f);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ultimate_scatter", Component.translatable(bossKey)));
			}
		}
		Sync.state(p, d);
		Sync.curses(p, d, false);
		DataStore.markDirty();
	}

	/** Everything the player carries (armour too), flung out around them; returns how many stacks. */
	public static int scatter(ServerPlayer p) {
		Inventory inv = p.getInventory();
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			inv.setItem(i, ItemStack.EMPTY);
			double a = rnd.nextDouble() * Math.PI * 2;
			double sp = 0.2 + rnd.nextDouble() * 0.35;
			ItemEntity item = new ItemEntity(level, p.getX(), p.getEyeY() - 0.3, p.getZ(), stack);
			item.setDeltaMovement(Math.cos(a) * sp, 0.3 + rnd.nextDouble() * 0.25, Math.sin(a) * sp);
			item.setPickUpDelay(40);
			level.addFreshEntity(item);
			n++;
		}
		return n;
	}
}
