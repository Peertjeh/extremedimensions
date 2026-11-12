package com.extremedimensions.client.compat.wthit;

import com.extremedimensions.client.compat.LookLines;
import com.extremedimensions.client.compat.PluginStatus;
import com.extremedimensions.compat.AnchorLook;
import com.extremedimensions.tapestry.CurseKnotBlock;
import com.extremedimensions.tetherite.SpindleBlock;

import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaPlugin;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The mod in WTHIT, showing what it shows in Jade (all of it from {@link LookLines}): under an anchor the charge it
 * holds for the one looking, its crack and its owners; under a Spindle what it holds; under a Curse Knot its set; under
 * a Lurker or its kin what it came for. Each is a toggle in WTHIT's plugin settings.
 *
 * <p>Loaded by WTHIT alone, named in {@code waila_plugins.json}, so it is never touched without WTHIT. WTHIT calls it
 * on a dedicated server too: there it gives only the anchor's server half, which touches nothing of the client's.
 *
 * <p>The registrar and the providers are used as WTHIT 21.0.0's jar has them. Still to be checked against that jar:
 * the raw NBT the server's data is written into and read back from ({@code IDataWriter#raw}, {@code IDataReader#raw}),
 * what the server's accessor gives ({@code IServerAccessor#getPlayer}, {@code #getHitResult}), the plugin's entry in
 * {@code waila_plugins.json}, and whether WTHIT asks the server about a block with no block entity, as the anchors are.
 */
public class ExtremeWthitPlugin implements IWailaPlugin {
	@Override
	public void register(IRegistrar registrar) {
		// (the anchors are plain blocks of the mod, no class of their own: the providers sort them out themselves)
		registrar.blockData(new AnchorData(), Block.class);
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
			return;
		}
		for (Identifier toggle : LookLines.TOGGLES) {
			registrar.localConfig(toggle, true);
		}
		registrar.body(new AnchorLines(), Block.class);
		registrar.body(new SpindleLines(), SpindleBlock.class);
		registrar.body(new KnotLines(), CurseKnotBlock.class);
		MoodLines moods = new MoodLines();
		for (Class<? extends Entity> moody : LookLines.MOODY) {
			registrar.body(moods, moody);
		}
		PluginStatus.wthit = true;
	}

	/** On the server: what the anchor looked at tells the one looking (nothing for any other block). */
	private static final class AnchorData implements IDataProvider<BlockEntity> {
		@Override
		public void appendData(IDataWriter data, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
			if (accessor.getPlayer() instanceof ServerPlayer p && accessor.getHitResult() instanceof BlockHitResult hit) {
				AnchorLook.write(data.raw(), p, hit.getBlockPos());
			}
		}
	}

	private static final class AnchorLines implements IBlockComponentProvider {
		@Override
		public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
			if (config.getBoolean(LookLines.ANCHOR) && AnchorLook.isAnchor(accessor.getBlockState())) {
				for (Component line : LookLines.anchor(accessor.getData().raw())) {
					tooltip.addLine(line);
				}
			}
		}
	}

	private static final class SpindleLines implements IBlockComponentProvider {
		@Override
		public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
			if (config.getBoolean(LookLines.SPINDLE)) {
				for (Component line : LookLines.spindle(accessor.getBlockState())) {
					tooltip.addLine(line);
				}
			}
		}
	}

	private static final class KnotLines implements IBlockComponentProvider {
		@Override
		public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
			if (config.getBoolean(LookLines.CURSE_KNOT)) {
				tooltip.addLine(LookLines.knot(accessor.getBlockState()));
			}
		}
	}

	private static final class MoodLines implements IEntityComponentProvider {
		@Override
		public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
			Entity e = accessor.getEntity();
			Component mood = config.getBoolean(LookLines.MOOD) ? LookLines.mood(e) : null;
			if (mood != null) {
				tooltip.addLine(mood);
			}
		}
	}
}
