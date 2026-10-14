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
import com.extremedimensions.penalty.AtonementGuard;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.ExtremeCurses;
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
			// Thorns and chains an Elder raised sink back before the world is saved
			com.extremedimensions.elder.ElderTerrain.clear();
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
			com.extremedimensions.elder.FerrymanService.clear();
			com.extremedimensions.elder.MatriarchService.clear();
			com.extremedimensions.elder.ElderStrikes.clear();
			com.extremedimensions.elder.WeaverService.clear();
			AtonementGuard.clear();
			DataStore.unload();
			WorldFx.bind(null);
		});
		ServerTickEvents.END_SERVER_TICK.register(ServerEvents::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> TetherCommand.register(dispatcher));

		ServerPlayerEvents.JOIN.register(ServerEvents::onJoin);
		// A leaving player's inventory is saved now; their curses and progress go with it
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> DataStore.flush());
		// Every player plays by the server's numbers: the HUD and screens get the world's settings on joining
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> com.extremedimensions.config.SettingsService.send(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PlayerData d = DataStore.get(newPlayer.getUUID());
			if (!alive) {
				ExpeditionService.onRespawn(newPlayer, d);
			}
			Sync.state(newPlayer, d);
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register(CurseEffects::afterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(ExtremeCurses::oath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(AtonementGuard::damaged);
		// The Elders' blows: the Ferryman takes levels, the Weaver unpicks Seam time
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseTaken, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && !blocked && taken > 0) {
				if (source.getEntity() instanceof com.extremedimensions.elder.Ferryman) {
					com.extremedimensions.elder.FerrymanService.toll(p);
				} else if (source.getEntity() instanceof com.extremedimensions.elder.Weaver) {
					com.extremedimensions.elder.WeaverService.unweave(p);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if ("minecraft:ender_dragon".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()) && WorldFx.server() != null
					&& Dims.ours(entity.level())) {
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
			} else if (entity instanceof com.extremedimensions.elder.Matriarch m) {
				com.extremedimensions.elder.MatriarchService.onDefeat(m, source.getEntity());
			} else if (entity instanceof com.extremedimensions.elder.Ferryman f) {
				com.extremedimensions.elder.FerrymanService.onDefeat(f, source.getEntity());
			} else if (entity instanceof com.extremedimensions.elder.Weaver w) {
				com.extremedimensions.elder.WeaverService.onDefeat(w, source.getEntity());
			} else if (entity instanceof Keeper k) {
				HollowService.onKeeperDeath(k, source.getEntity());
			} else if (entity instanceof Hollowling h) {
				HollowService.onHollowlingDeath(h);
			} else if (entity instanceof Lurker l) {
				LurkerService.onDeath(l, source.getEntity());
			} else if (entity instanceof com.extremedimensions.seam.VeilRay || entity instanceof com.extremedimensions.seam.Thinned) {
				com.extremedimensions.seam.SeamFauna.onDeath(entity);
				if (source.getEntity() instanceof ServerPlayer killer) {
					com.extremedimensions.trial.TrialService.progress(killer, com.extremedimensions.trial.Trial.Kind.HUNT, 1);
				}
			} else if (source.getEntity() instanceof ServerPlayer killer && Dims.ours(entity.level())) {
				ExpeditionService.onMobKilled(killer, entity);
			}
			if (!(entity instanceof ServerPlayer)) {
				AtonementGuard.forget(entity);
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!Dims.ours(level)) {
				return InteractionResult.PASS;
			}
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
				com.extremedimensions.trial.TrialService.plundered(sp, level, hit.getBlockPos());
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
			if (player instanceof ServerPlayer sp && !Dims.ours(level) && foreignRefuses(player.getItemInHand(hand))) {
				// In another mod's world the mod's things wait until the player is back (the journal can still be read)
				sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.foreign.refused"), true);
				return InteractionResult.FAIL;
			}
			if (player instanceof ServerPlayer sp && player.getItemInHand(hand).is(ModItems.MENDING_CLOTH)) {
				return PenaltyService.cloth(sp, player.getItemInHand(hand));
			}
			if (player instanceof ServerPlayer sp && com.extremedimensions.relic.RelicService.of(player.getItemInHand(hand)) != null) {
				return com.extremedimensions.relic.RelicService.bind(sp, player.getItemInHand(hand));
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
			if (!Dims.ours(level)) {
				return;
			}
			AnchorService.onBroken(Dims.of(level), pos);
			if (player instanceof ServerPlayer sp) {
				CurseEffects.wear(sp);
			}
			if (state.is(ModBlocks.SEAM_VEIN) && player instanceof ServerPlayer sp) {
				Journal.unlock(sp, DataStore.get(sp.getUUID()), JournalEntry.LOOM);
				com.extremedimensions.trial.TrialService.progress(sp, com.extremedimensions.trial.Trial.Kind.VEINS, 1);
			}
			if (state.is(Blocks.BARREL)) {
				HollowService.onBroken(level, pos, player instanceof ServerPlayer sp ? sp : null);
				if (player instanceof ServerPlayer sp) {
					com.extremedimensions.trial.TrialService.plundered(sp, level, pos);
				}
			}
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && entity instanceof net.minecraft.world.entity.LivingEntity) {
				CurseEffects.wear(sp);
			}
			return InteractionResult.PASS;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!Dims.ours(level)) {
				return InteractionResult.PASS;
			}
			InteractionResult feral = ExtremeCurses.refuseTaming(player, entity);
			if (feral != InteractionResult.PASS) {
				return feral;
			}
			if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && entity instanceof Hollowling h) {
				return HollowService.onInteract(sp, h, player.getItemInHand(hand));
			}
			return InteractionResult.PASS;
		});
	}

	private static void onJoin(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		// Joining in another mod's world: the mod picks the player up again where they come back to one of its own
		d.away = !Dims.ours(p);
		d.dim = d.away ? Dims.OVERWORLD : Dims.of(p);
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

	/** Into another mod's world the curses fall asleep (and their attribute curses come off); back out, they stir again. */
	public static void setAway(ServerPlayer p, PlayerData d, boolean away) {
		if (away == d.away) {
			return;
		}
		d.away = away;
		CurseEffects.applyEffects(p, d);
		p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(away
				? "gui.extremedimensions.foreign.enter" : "gui.extremedimensions.foreign.leave"), true);
	}

	/** The mod's things that act (cloth, relics, endings) and so wait in another mod's world; the journal and pages are only read. */
	private static boolean foreignRefuses(ItemStack held) {
		return held.is(ModItems.MENDING_CLOTH) || held.is(ModItems.MENDING_THREAD) || held.is(ModItems.SEVERING_SHEARS)
				|| com.extremedimensions.relic.RelicService.of(held) != null;
	}

	/** Shortly after joining, once the player really stands in the world and commands can target them. */
	private static void afterJoin(ServerPlayer p, PlayerData d) {
		CurseEffects.applyEffects(p, d);
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

			// Another mod's world lies outside the wound's reach: the curses sleep, an expedition waits for the
			// player's return, and nothing else of the mod acts on them there
			boolean away = !Dims.ours(p);
			if (away != d.away) {
				setAway(p, d, away);
				sync = true;
			}
			if (away) {
				d.tickCounter++;
				if (sync || d.tickCounter % Config.SYNC_INTERVAL_TICKS == 0) {
					Sync.state(p, d);
				}
				continue;
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
				CurseEffects.refreshEffects(p, d);
				discover(p, d, dim);
			}

			d.tickCounter++;
			if (sync || d.tickCounter % Config.SYNC_INTERVAL_TICKS == 0) {
				Sync.state(p, d);
			}
		}

		TetherService.sweep(s);
		if (s.getTickCount() % 1200 == 0) {
			AtonementGuard.sweep(s);
		}
		AnchorService.tick(s);
		LurkerService.tick(s);
		HollowService.tick(s);
		SeamService.tick(s);
		com.extremedimensions.seam.SeamFauna.tick(s);
		WorldService.tick(s);
		MendingService.tick(s);
		com.extremedimensions.elder.FerrymanService.tick(s);
		com.extremedimensions.elder.MatriarchService.tick(s);
		com.extremedimensions.elder.ElderStrikes.tick(s);
		com.extremedimensions.elder.ElderTerrain.tick(s);
		com.extremedimensions.elder.WeaverService.tick(s);
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
