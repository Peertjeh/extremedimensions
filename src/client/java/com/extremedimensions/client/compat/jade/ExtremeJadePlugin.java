package com.extremedimensions.client.compat.jade;

import com.extremedimensions.client.compat.LookLines;
import com.extremedimensions.client.compat.PluginStatus;
import com.extremedimensions.compat.AnchorLook;
import com.extremedimensions.tapestry.CurseKnotBlock;
import com.extremedimensions.tetherite.SpindleBlock;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * The mod in Jade: under an anchor the charge it holds for the one looking, its crack and its owners; under a Spindle
 * what it holds; under a Curse Knot its set; under a Lurker or its kin what it came for (all of it from
 * {@link LookLines}). Every provider is a toggle of its own in Jade's plugin settings, under its id.
 *
 * <p>Loaded by Jade alone, through the {@code jade} entrypoint, so it is never touched without Jade. Jade calls it on a
 * dedicated server too, for {@link #register} alone: the anchor's server half, which only the server can answer,
 * touches nothing of the client's.
 */
public class ExtremeJadePlugin implements IWailaPlugin {
	@Override
	public void register(IWailaCommonRegistration registration) {
		// (the anchors are plain blocks of the mod, no class of their own: the provider sorts them out itself)
		registration.registerBlockDataProvider(Anchors.INSTANCE, Block.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(Anchors.INSTANCE, Block.class);
		registration.registerBlockComponent(Spindles.INSTANCE, SpindleBlock.class);
		registration.registerBlockComponent(Knots.INSTANCE, CurseKnotBlock.class);
		for (Class<? extends Entity> moody : LookLines.MOODY) {
			registration.registerEntityComponent(Moods.INSTANCE, moody);
		}
		PluginStatus.jade = true;
	}

	/** An anchor: what the server says of it, asked for only while one of the mod's anchors is looked at. */
	private enum Anchors implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
		INSTANCE;

		@Override
		public Identifier getUid() {
			return LookLines.ANCHOR;
		}

		@Override
		public boolean shouldRequestData(BlockAccessor accessor) {
			return AnchorLook.isAnchor(accessor.getBlockState());
		}

		@Override
		public void appendServerData(CompoundTag data, BlockAccessor accessor) {
			if (accessor.getPlayer() instanceof ServerPlayer p) {
				AnchorLook.write(data, p, accessor.getPosition());
			}
		}

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			if (AnchorLook.isAnchor(accessor.getBlockState())) {
				tooltip.addAll(LookLines.anchor(accessor.getServerData()));
			}
		}
	}

	private enum Spindles implements IBlockComponentProvider {
		INSTANCE;

		@Override
		public Identifier getUid() {
			return LookLines.SPINDLE;
		}

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			tooltip.addAll(LookLines.spindle(accessor.getBlockState()));
		}
	}

	private enum Knots implements IBlockComponentProvider {
		INSTANCE;

		@Override
		public Identifier getUid() {
			return LookLines.CURSE_KNOT;
		}

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			tooltip.add(LookLines.knot(accessor.getBlockState()));
		}
	}

	private enum Moods implements IEntityComponentProvider {
		INSTANCE;

		@Override
		public Identifier getUid() {
			return LookLines.MOOD;
		}

		@Override
		public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
			Component mood = LookLines.mood(accessor.getEntity());
			if (mood != null) {
				tooltip.add(mood);
			}
		}
	}
}
