package com.extremedimensions.server;

import com.extremedimensions.anchor.AnchorPaths;
import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.command.TetherCommand;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.Hoard;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.hollow.Hollowling;
import com.extremedimensions.hollow.Keeper;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.lurker.LurkerService;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.mending.StarvedOne;
import com.extremedimensions.world.WorldService;
import net.minecraft.core.registries.BuiltInRegistries;
import com.extremedimensions.tether.TetherService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import java.util.ArrayList;
import java.util.List;

/** Wires every subsystem to Fabric events and runs the per-player tick in a fixed order. */
public final class ServerEvents {
	private ServerEvents() {}

	private static final String[] RECIPES = {
			"minecraft:lodestone", "extremedimensions:tether_journal", "extremedimensions:tether",
			"extremedimensions:ember_tether", "extremedimensions:void_tether", "extremedimensions:mending_cloth"};

	private static int effectTimer = 0;

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			WorldFx.bind(s);
			DataStore.load(s);
		});
		// The mod's data is written with the world's own saves (autosave, /save-all, stop), never ahead of them
		ServerLifecycleEvents.AFTER_SAVE.register((s, flush, force) -> DataStore.flush());
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			DataStore.markDirty();
			DataStore.flush();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			LurkerService.clear();
			HollowService.clear();
			SeamService.clear();
			com.extremedimensions.seam.SeamFauna.clear();
			WorldService.clear();
			MendingService.clear();
			DataStore.unload();
			WorldFx.bind(null);
		});
		ServerTickEvents.END_SERVER_TICK.register(ServerEvents::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> TetherCommand.register(dispatcher));

		ServerPlayerEvents.JOIN.register(ServerEvents::onJoin);
		// A leaving player's inventory is saved now; their curses and progress go with it
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> DataStore.flush());
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PlayerData d = DataStore.get(newPlayer.getUUID());
			if (!alive) {
				ExpeditionService.onRespawn(newPlayer, d);
			}
			Sync.state(newPlayer, d);
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register(PenaltyService::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if ("minecraft:ender_dragon".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()) && WorldFx.server() != null) {
				WorldService.onDragonDeath(WorldFx.server());
			}
			if (entity instanceof ServerPlayer p) {
				if ("severed".equals(DataStore.ending()) && Dims.isBeyond(Dims.of(p))) {
					// A severed world keeps nothing of those who fall beyond the wound
					for (ItemEntity item : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8), i -> i.distanceToSqr(p) <= 64)) {
						item.discard();
					}
				}
				ExpeditionService.onDeath(p);
			} else if (entity instanceof StarvedOne so) {
				MendingService.onDefeat(so);
			} else if (entity instanceof Keeper k) {
				HollowService.onKeeperDeath(k, source.getEntity());
			} else if (entity instanceof Hollowling h) {
				HollowService.onHollowlingDeath(h);
			} else if (entity instanceof Lurker l) {
				LurkerService.onDeath(l, source.getEntity());
			} else if (entity instanceof com.extremedimensions.seam.VeilRay || entity instanceof com.extremedimensions.seam.Thinned) {
				com.extremedimensions.seam.SeamFauna.onDeath(entity);
			} else if (source.getEntity() instanceof ServerPlayer killer) {
				ExpeditionService.onMobKilled(killer, entity);
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND && MendingService.isPedestal(hit.getBlockPos())
					&& SeamService.inSeam(sp)) {
				InteractionResult r = MendingService.onPedestal(sp, hit.getBlockPos(), player.getItemInHand(hand));
				if (r != InteractionResult.PASS) {
					return r;
				}
			}
			if (player.getItemInHand(hand).is(ModItems.SEAM_NEEDLE) && level.getBlockState(hit.getBlockPos()).is(Blocks.CRYING_OBSIDIAN)) {
				return player instanceof ServerPlayer sp
						? SeamService.openRift(sp, hit.getBlockPos(), hit.getDirection(), player.getItemInHand(hand))
						: InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND && level.getBlockState(hit.getBlockPos()).is(Blocks.BARREL)) {
				Hoard h = HollowService.hoardAt(Dims.of(level), hit.getBlockPos());
				if (h != null) {
					HollowService.onHoardOpened(sp, h);
				}
			}
			if (hand != InteractionHand.MAIN_HAND || !AnchorService.isAnchorBlock(level.getBlockState(hit.getBlockPos()))) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer sp)) {
				// Client: don't place the held block against the anchor, the server handles it
				ItemStack held = player.getMainHandItem();
				boolean status = held.isEmpty() && player.isShiftKeyDown();
				return status || AnchorService.isUsableOnAnchor(held.getItem()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			return AnchorService.use(sp, hit.getBlockPos());
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp && player.getItemInHand(hand).is(ModItems.TETHER_JOURNAL)) {
				Journal.open(sp, DataStore.get(sp.getUUID()));
				return InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer sp && player.getItemInHand(hand).is(ModItems.MENDING_CLOTH)) {
				return PenaltyService.cloth(sp, player.getItemInHand(hand));
			}
			if (player instanceof ServerPlayer sp && (player.getItemInHand(hand).is(ModItems.MENDING_THREAD) || player.getItemInHand(hand).is(ModItems.SEVERING_SHEARS))) {
				return MendingService.useEnding(sp, player.getItemInHand(hand));
			}
			if (player instanceof ServerPlayer sp && player.getItemInHand(hand).is(ModItems.WANDERERS_PAGE)) {
				if (!sp.isCreative()) {
					player.getItemInHand(hand).shrink(1);
				}
				Journal.readPage(sp, DataStore.get(sp.getUUID()));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			AnchorService.onBroken(Dims.of(level), pos);
			if (player instanceof ServerPlayer sp) {
				PenaltyService.wear(sp);
			}
			if (state.is(ModBlocks.SEAM_VEIN) && player instanceof ServerPlayer sp) {
				Journal.unlock(sp, DataStore.get(sp.getUUID()), JournalEntry.LOOM);
			}
			if (state.is(Blocks.BARREL)) {
				HollowService.onBroken(level, pos, player instanceof ServerPlayer sp ? sp : null);
			}
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && entity instanceof net.minecraft.world.entity.LivingEntity) {
				PenaltyService.wear(sp);
			}
			return InteractionResult.PASS;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && entity instanceof Hollowling h) {
				return HollowService.onInteract(sp, h, player.getItemInHand(hand));
			}
			return InteractionResult.PASS;
		});
	}

	private static void onJoin(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		d.dim = Dims.of(p);
		d.collapsing = false;
		d.collapseRetry = 0;
		d.inRun = Dims.isBeyond(d.dim) && !d.linked.isEmpty();
		d.lastWarn = Integer.MAX_VALUE;
		if (!d.pendingOffer.isEmpty()) {
			d.pendingPenalties = Math.max(1, d.pendingPenalties);
			while (d.owed.size() < d.pendingPenalties) {
				d.owed.add("beyond");
			}
			d.offerDelay = 40;
		}
		d.joinDone = false;
		d.ticksOnline = 0;
		d.hasRopeEnd = false;
		Sync.state(p, d);
	}

	/** Shortly after joining, once the player really stands in the world and commands can target them. */
	private static void afterJoin(ServerPlayer p, PlayerData d) {
		PenaltyService.applyEffects(p, d);
		List<RecipeHolder<?>> recipes = new ArrayList<>();
		for (String recipe : RECIPES) {
			p.level().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, WorldFx.id(recipe))).ifPresent(recipes::add);
		}
		p.awardRecipes(recipes);
		Journal.deliverFirst(p, d);
		Advancements.grant(p, Advancements.ROOT);
	}

	private static void tick(MinecraftServer s) {
		boolean slowTick = ++effectTimer >= 100;
		if (slowTick) {
			effectTimer = 0;
		}

		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.get(p.getUUID());
			String dim = Dims.of(p);
			boolean sync = false;

			if (d.grace > 0) {
				d.grace--;
			}
			if (!d.joinDone && ++d.ticksOnline >= Config.JOIN_DELAY_TICKS) {
				d.joinDone = true;
				afterJoin(p, d);
			}
			if (d.offerDelay > 0 && --d.offerDelay == 0) {
				d.offerDelay = -1;
				Sync.offer(p, d);
			}

			if (!dim.equals(d.dim)) {
				String from = d.dim;
				d.dim = dim;
				sync = true;
				ExpeditionService.onDimensionChange(p, d, from, dim);
			}

			sync |= ExpeditionService.tick(p, d, dim);
			sync |= TetherService.tick(p, d, dim);
			boolean onExpedition = Dims.isBeyond(dim) && d.inRun && !d.collapsing;
			sync |= PenaltyService.tick(p, d, onExpedition, Dims.OVERWORLD.equals(dim) && !d.collapsing);

			if (slowTick) {
				PenaltyService.refreshEffects(p, d);
				discover(p, d, dim);
			}

			d.tickCounter++;
			if (sync || d.tickCounter % Config.SYNC_INTERVAL_TICKS == 0) {
				Sync.state(p, d);
			}
		}

		TetherService.sweep(s);
		AnchorService.tick(s);
		LurkerService.tick(s);
		HollowService.tick(s);
		SeamService.tick(s);
		com.extremedimensions.seam.SeamFauna.tick(s);
		WorldService.tick(s);
		MendingService.tick(s);
	}

	/** Journal pages found by exploring. */
	private static void discover(ServerPlayer p, PlayerData d, String dim) {
		if (p.level().getBiome(p.blockPosition()).is(Biomes.DEEP_DARK)) {
			Journal.unlock(p, d, JournalEntry.WEAVERS);
		}
		// Near a portal without knowing about anchors yet? The journal teaches them.
		if (Dims.OVERWORLD.equals(dim) && !d.journal.contains(JournalEntry.ANCHOR.id)) {
			ServerLevel level = (ServerLevel) p.level();
			if (AnchorPaths.findPortal(level, p.blockPosition(), Dims.NETHER) != null
					|| AnchorPaths.findPortal(level, p.blockPosition(), Dims.END) != null) {
				Journal.unlock(p, d, JournalEntry.ANCHOR);
			}
		}
	}
}
