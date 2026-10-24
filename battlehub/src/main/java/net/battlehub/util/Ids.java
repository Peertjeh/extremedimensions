package net.battlehub.util;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Registry lookups by string id ("minecraft:red_wool" or just "red_wool"). Unknown ids fall back to air. */
public final class Ids {
	private Ids() {}

	public static Identifier id(String s) {
		Identifier id = Identifier.tryParse(s == null ? "" : s.trim());
		return id == null ? Identifier.withDefaultNamespace("air") : id;
	}

	public static Item item(String s) {
		Identifier id = id(s);
		return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : Items.AIR;
	}

	public static boolean isItem(String s) {
		return BuiltInRegistries.ITEM.containsKey(id(s));
	}

	public static Block block(String s) {
		Identifier id = id(s);
		return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id) : Blocks.AIR;
	}

	public static boolean isBlock(String s) {
		return BuiltInRegistries.BLOCK.containsKey(id(s)) && block(s) != Blocks.AIR;
	}

	public static BlockState state(String s) {
		return block(s).defaultBlockState();
	}

	public static Optional<Holder<MobEffect>> effect(String s) {
		return BuiltInRegistries.MOB_EFFECT.get(id(s)).map(h -> (Holder<MobEffect>) h);
	}

	public static Optional<Holder<Enchantment>> enchantment(MinecraftServer server, String s) {
		return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, id(s)))
				.map(h -> (Holder<Enchantment>) h);
	}

	public static String key(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}
}
