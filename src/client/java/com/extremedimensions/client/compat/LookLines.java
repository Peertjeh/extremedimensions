package com.extremedimensions.client.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.compat.AnchorLook;
import com.extremedimensions.hollow.Hollowling;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.lurker.LurkerBody;
import com.extremedimensions.lurker.SculkLurker;
import com.extremedimensions.tapestry.CurseKnotBlock;
import com.extremedimensions.tapestry.Unweaving;
import com.extremedimensions.tetherite.SpindleBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The lines Jade and WTHIT show under what the player looks at: an anchor's charge, cracks, blockage and owners (from
 * what the server sent, see {@link AnchorLook}), what a Spindle holds, a Curse Knot's set, and what a Lurker or one of
 * its kin came for, which its glow alone tells otherwise (blue to steal, red to hurt). Both plugins show the same, from here; each
 * kind is a toggle in the tooltip mod's own settings, under these ids.
 */
public final class LookLines {
	private LookLines() {}

	public static final Identifier ANCHOR = ExtremeDimensions.id("anchor");
	public static final Identifier SPINDLE = ExtremeDimensions.id("spindle");
	public static final Identifier CURSE_KNOT = ExtremeDimensions.id("curse_knot");
	public static final Identifier MOOD = ExtremeDimensions.id("lurker_mood");
	public static final List<Identifier> TOGGLES = List.of(ANCHOR, SPINDLE, CURSE_KNOT, MOOD);

	/** The creatures that wear a mood: every kind of Lurker (the Keepers among them), the Sculk Lurker and the young. */
	public static final List<Class<? extends Entity>> MOODY = List.of(Lurker.class, SculkLurker.class, Hollowling.class);

	/** What an anchor tells the one looking, from the server's word; nothing when there is none (the mod is not on the server). */
	public static List<Component> anchor(CompoundTag data) {
		List<Component> out = new ArrayList<>();
		if (data == null || !data.contains(AnchorLook.YOURS)) {
			return out;
		}
		int yours = data.getIntOr(AnchorLook.YOURS, 0);
		out.add(yours > 0 ? Component.translatable("gui.extremedimensions.look.anchor.yours", time(yours)).withStyle(ChatFormatting.GOLD)
				: Component.translatable("gui.extremedimensions.look.anchor.none").withStyle(ChatFormatting.GRAY));
		int cracked = data.getIntOr(AnchorLook.CRACKED, 0);
		if (cracked > 0) {
			out.add(Component.translatable("gui.extremedimensions.look.anchor.cracked", time(cracked)).withStyle(ChatFormatting.RED));
		}
		if (data.getIntOr(AnchorLook.BLOCKED, 0) > 0) {
			// (3.55.0) its way to its portal is blocked, and it leaks
			out.add(Component.translatable("gui.extremedimensions.look.anchor.blocked").withStyle(ChatFormatting.RED));
		}
		List<Component> owners = new ArrayList<>();
		if (data.getIntOr(AnchorLook.MINE, 0) > 0) {
			owners.add(Component.translatable("gui.extremedimensions.look.anchor.you"));
		}
		owners.addAll(names(data.getStringOr(AnchorLook.OWNERS, "")));
		if (data.getIntOr(AnchorLook.EVERYONE, 0) > 0) {
			owners.add(Component.translatable("gui.extremedimensions.lens.anchor.everyone"));
		}
		int more = data.getIntOr(AnchorLook.MORE, 0);
		if (more > 0) {
			owners.add(Component.translatable("gui.extremedimensions.look.anchor.more", more));
		}
		out.add(owners.isEmpty() ? Component.translatable("gui.extremedimensions.look.anchor.empty").withStyle(ChatFormatting.GRAY)
				: Component.translatable("gui.extremedimensions.look.anchor.owners", join(owners)));
		List<Component> lenders = names(data.getStringOr(AnchorLook.LENDERS, ""));
		if (!lenders.isEmpty()) {
			out.add(Component.translatable("gui.extremedimensions.look.anchor.lenders", join(lenders)).withStyle(ChatFormatting.AQUA));
		}
		return out;
	}

	/** What a Spindle holds, and how far the turning has come (or that it may be turned). */
	public static List<Component> spindle(BlockState s) {
		List<Component> out = new ArrayList<>();
		int fragments = s.getValue(SpindleBlock.FRAGMENTS), silk = s.getValue(SpindleBlock.SILK), spun = s.getValue(SpindleBlock.SPUN);
		out.add(Component.translatable("gui.extremedimensions.look.spindle.fragments", fragments, SpindleBlock.MAX_FRAGMENTS));
		out.add(Component.translatable("gui.extremedimensions.look.spindle.silk", silk, SpindleBlock.MAX_SILK));
		if (spun > 0) {
			out.add(Component.translatable("gui.extremedimensions.look.spindle.turns", spun, SpindleBlock.turnsPerCord()).withStyle(ChatFormatting.LIGHT_PURPLE));
		} else if (fragments >= SpindleBlock.fragmentsPerCord() && silk >= SpindleBlock.silkPerCord()) {
			out.add(Component.translatable("gui.extremedimensions.look.spindle.ready").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return out;
	}

	/** Which curse set a Curse Knot was tied from (the names the Curses screen gives its tabs). */
	public static Component knot(BlockState s) {
		int set = Math.max(0, Math.min(Unweaving.SETS.length - 1, s.getValue(CurseKnotBlock.SET)));
		return Component.translatable("gui.extremedimensions.look.curse_knot",
				Component.translatable("gui.extremedimensions.curses.tab_" + Unweaving.SETS[set].id())).withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	/**
	 * What a Lurker or one of its kin came for, as its glow tells it: to steal (blue) or to hurt (red); nothing while it is
	 * calm. The Sculk Lurker's other mood is not theft: it leads the Warden to you then.
	 */
	public static Component mood(Entity e) {
		if (!(e instanceof LurkerBody body)) {
			return null;
		}
		int mood = body.getLurkerMood();
		if (mood == LurkerBody.MOOD_HUNTER) {
			return Component.translatable("gui.extremedimensions.look.mood.hurt").withStyle(ChatFormatting.RED);
		}
		if (mood == LurkerBody.MOOD_THIEF) {
			return e instanceof SculkLurker ? Component.translatable("gui.extremedimensions.look.mood.lure").withStyle(ChatFormatting.RED)
					: Component.translatable("gui.extremedimensions.look.mood.steal").withStyle(ChatFormatting.AQUA);
		}
		return null;
	}

	/** Names sent one per line; one the server did not know is "someone". */
	private static List<Component> names(String lines) {
		List<Component> out = new ArrayList<>();
		if (lines.isEmpty()) {
			return out;
		}
		for (String name : lines.split("\n", -1)) {
			out.add(name.isEmpty() ? Component.translatable("gui.extremedimensions.lens.anchor.unknown") : Component.literal(name));
		}
		return out;
	}

	private static MutableComponent join(List<Component> parts) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(parts.get(i));
		}
		return out;
	}

	private static String time(int secs) {
		return String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
	}
}
