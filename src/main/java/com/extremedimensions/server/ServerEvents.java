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
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
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

	/** Strays found as they loaded, removed at the end of the tick. */
	private static final java.util.List<net.minecraft.world.entity.Entity> STRAYS = new java.util.ArrayList<>();
	/** How long the mod's own work took in the last server tick, in nanoseconds (read by the performance tests). */
	private static long lastTickNanos = 0;
	/** The CPU time of that work, while the performance tests listen (see TickProfile). */
	private static long lastTickCpuNanos = 0;

	public static void register() {
		// A reset of the Seam that an operator asked for is carried out before the worlds load
		ServerLifecycleEvents.SERVER_STARTING.register(com.extremedimensions.seam.SeamReset::starting);
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			WorldFx.bind(s);
			DataStore.load(s);
			com.extremedimensions.coop.Allies.init(s);
		});
		// The mod's data is written with the world's own saves (autosave, /save-all, stop), never ahead of them
		ServerLifecycleEvents.AFTER_SAVE.register((s, flush, force) -> {
			// Everything, every time: small changes (charge drained, atonement gathered) mark nothing dirty on their own
			DataStore.markDirty();
			DataStore.flush();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			// Thorns and chains an Elder raised sink back, and its arena comes down, before the world is saved
			com.extremedimensions.elder.ElderTerrain.clear();
			com.extremedimensions.elder.ElderArena.clear();
			com.extremedimensions.elder.ElderGaol.clear();
			// Events under way stop: the Endermen building go, the rays of a frayed island sink back
			com.extremedimensions.events.WorldEvents.clear();
			// Blocks of the Seam in the air land, and islands not yet finished are built again on the next visit
			SeamService.stopping();
			DataStore.markDirty();
			DataStore.flush();
			// The stats export, once more, as a web page should show the world while the server is down
			com.extremedimensions.compat.StatsExport.stopping(s);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			STRAYS.clear();
			com.extremedimensions.compat.TabPlaceholders.clear();
			com.extremedimensions.lore.FirstWeaver.clear();
			com.extremedimensions.tapestry.Tapestry.clear();
			com.extremedimensions.seam.FirstKnot.clear();
			LurkerService.clear();
			com.extremedimensions.penalty.AbyssalTolls.clear();
			com.extremedimensions.penalty.AbyssalWorld.clear();
			HollowService.clear();
			SeamService.clear();
			com.extremedimensions.seam.SeamFauna.clear();
			com.extremedimensions.expedition.ExpeditionBar.clear();
			WorldService.clear();
			MendingService.clear();
			com.extremedimensions.elder.FerrymanService.clear();
			com.extremedimensions.elder.MatriarchService.clear();
			com.extremedimensions.elder.ElderStrikes.clear();
			com.extremedimensions.elder.ElderSpells.clear();
			com.extremedimensions.elder.AttackFx.clear();
			com.extremedimensions.elder.WeaverService.clear();
			com.extremedimensions.coop.FallenService.clear();
			com.extremedimensions.coop.BurdenService.clear();
			com.extremedimensions.tribe.RaidService.clear();
			AtonementGuard.clear();
			BlockFlight.clear();
			com.extremedimensions.coop.Allies.clear();
			Requests.clear();
			DataStore.unload();
			WorldFx.bind(null);
		});
		ServerTickEvents.END_SERVER_TICK.register(ServerEvents::tick);
		// A flying block's display that nothing is using (left over from a crash) is removed as it loads
		// (at the end of the tick: the level is still adding the entity while the event runs)
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// ... and so are a gaol's posts, echo chains and builders left behind by a stop, or by an echo or a build
			// that is over
			if (BlockFlight.stray(entity) | com.extremedimensions.elder.ElderGaol.stray(entity)
					| com.extremedimensions.events.Echoes.stray(entity) | com.extremedimensions.events.Unanchored.stray(entity)) {
				STRAYS.add(entity);
			}
		});
		// A block of an Elder's arena broken by a player drops nothing
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				com.extremedimensions.elder.ElderArena.broken(level, pos));
		// A core of an Elder's arena takes hits rather than being mined
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				level instanceof net.minecraft.server.level.ServerLevel sl && !player.isSpectator()
						&& (com.extremedimensions.elder.ElderDomain.hit(player, sl, pos) || com.extremedimensions.tapestry.Unweaving.hit(player, sl, pos))
						? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(player instanceof ServerPlayer fallen && com.extremedimensions.coop.FallenService.fallen(fallen)));
		// An anchor that keeps someone else's charge cannot be broken out from under them
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(player instanceof ServerPlayer sp) || !AnchorService.isAnchorBlock(state) || AnchorService.mayBreak(sp, Dims.of(level), pos));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> TetherCommand.register(dispatcher));

		ServerPlayerEvents.JOIN.register(ServerEvents::onJoin);
		// A leaving player's inventory is saved now; their curses and progress go with it
		// (on the server's thread: a player in a single-player world leaves from the connection's own)
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> s.executeIfPossible(() -> {
			// A fallen player who leaves bleeds out
			com.extremedimensions.coop.FallenService.onLeave(handler.getPlayer());
			// ... and one the Held's Chain holds collapses
			ExpeditionService.leaving(handler.getPlayer(), DataStore.get(handler.getPlayer().getUUID()));
			Requests.forget(handler.getPlayer());
			com.extremedimensions.elder.ElderSpells.forget(handler.getPlayer().getUUID());
			com.extremedimensions.expedition.ExpeditionBar.forget(handler.getPlayer().getUUID());
			com.extremedimensions.binding.WoundLens.forget(handler.getPlayer().getUUID());
			com.extremedimensions.binding.Charms.forget(handler.getPlayer().getUUID());
			// Every player's inventory is saved with the world's data, so neither can run ahead of the other after a crash
			// (a stopping server saves them all once itself)
			if (s.isRunning()) {
				s.getPlayerList().saveAll();
			}
			com.extremedimensions.lore.WarmChains.forget(handler.getPlayer().getUUID());
			DataStore.markDirty();
			DataStore.flush();
		}));
		// Every player plays by the server's numbers: the HUD and screens get the world's settings on joining
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> com.extremedimensions.config.SettingsService.send(handler.getPlayer()));
		// ... and who is fallen, to draw them crawling
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> com.extremedimensions.coop.FallenService.sync(s, handler.getPlayer()));
		// What an echo gave back while they were away, and gear that came home and still waits
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			ServerPlayer p = handler.getPlayer();
			com.extremedimensions.events.Echoes.welcome(p, DataStore.get(p.getUUID()));
			if (p.isAlive()) {
				com.extremedimensions.binding.Homebound.restore(p);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			com.extremedimensions.coop.FallenService.forget(newPlayer);
			PlayerData d = DataStore.get(newPlayer.getUUID());
			if (!alive) {
				ExpeditionService.onRespawn(newPlayer, d);
				com.extremedimensions.binding.Homebound.restore(newPlayer);
				com.extremedimensions.penalty.AbyssalCurses.onRespawn(newPlayer);
			}
			Sync.state(newPlayer, d);
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register(CurseEffects::afterDamage);
		// Tethershot: an arrow hauls what it hits toward the archer
		ServerLivingEntityEvents.AFTER_DAMAGE.register(com.extremedimensions.binding.Charms::afterDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(com.extremedimensions.penalty.AbyssalCurses::afterDamage);
		// Bitter Cure: a zombie villager cured breaks it for those close by
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, context) -> com.extremedimensions.penalty.AbyssalTolls.converted(previous, converted));
		ServerLivingEntityEvents.ALLOW_DEATH.register(ExtremeCurses::oath);
		// Co-op: struck down with an ally near, a player falls instead of dying, and cannot be hurt while fallen
		ServerLivingEntityEvents.ALLOW_DEATH.register(com.extremedimensions.coop.FallenService::allowDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> com.extremedimensions.coop.FallenService.mayHurt(entity, source));
		ServerLivingEntityEvents.AFTER_DAMAGE.register(AtonementGuard::damaged);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(AtonementGuard::hurting);
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
			com.extremedimensions.journal.Bestiary.slain(entity, source.getEntity());
			// The Abyssal curses' deeds: a creature slain, an Elder fallen
			if (!(entity instanceof ServerPlayer) && source.getEntity() instanceof ServerPlayer killer) {
				com.extremedimensions.penalty.AbyssalCurses.onKill(killer, entity, source);
				// Reaping: the slain give a little back to the Tether
				com.extremedimensions.binding.Charms.reap(killer, entity);
			}
			if (entity instanceof com.extremedimensions.elder.ElderBoss) {
				com.extremedimensions.penalty.AbyssalCurses.elderSlain(entity);
			}
			if ("minecraft:ender_dragon".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()) && WorldFx.server() != null
					&& Dims.ours(entity.level())) {
				WorldService.onDragonDeath(WorldFx.server());
			}
			if (entity instanceof ServerPlayer p) {
				// Homebound things are taken up first: nothing, not even a severed world, keeps them
				com.extremedimensions.binding.Homebound.gather(p);
				// (the Heart panel of a severed world spares one fall in a while)
				if ("severed".equals(DataStore.ending()) && Dims.isBeyond(Dims.of(p))
						&& !com.extremedimensions.tapestry.Tapestry.spares(p, DataStore.get(p.getUUID()))) {
					// A severed world keeps nothing of those who fall beyond the wound
					boolean collapsing = DataStore.get(p.getUUID()).collapsing;
					// (only what this death dropped: another's things lying near are left alone)
					// (a death's drops have no thrower: what someone else threw down is theirs, not this death's)
					for (ItemEntity item : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8),
							i -> i.tickCount <= 2 && i.distanceToSqr(p) <= 64 && (i.getOwner() == null || i.getOwner() == p))) {
						if (collapsing) {
							// the echo of this Collapse keeps one of the things
							com.extremedimensions.events.Echoes.lost(p, item.getItem());
						}
						item.discard();
					}
				}
				if (Dims.ours(p)) {
					DataStore.get(p.getUUID()).deaths++;
				}
				com.extremedimensions.penalty.AbyssalCurses.onDeath(p);
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
				HollowService.onKeeperDeath(k, HollowService.culprit(k, source.getEntity()));
			} else if (entity instanceof Hollowling h) {
				HollowService.onHollowlingDeath(h, HollowService.culprit(h, source.getEntity()));
			} else if (entity instanceof Lurker l) {
				LurkerService.onDeath(l, HollowService.culprit(l, source.getEntity()));
			} else if (entity instanceof com.extremedimensions.seam.VeilRay || entity instanceof com.extremedimensions.seam.Thinned) {
				boolean weaving = com.extremedimensions.tapestry.Weaving.drives(entity.getUUID());
				com.extremedimensions.seam.SeamFauna.onDeath(entity);
				if (source.getEntity() instanceof ServerPlayer killer && !weaving) {
					com.extremedimensions.trial.TrialService.progress(killer, com.extremedimensions.trial.Trial.Kind.HUNT, 1);
				}
			} else if (source.getEntity() instanceof ServerPlayer killer && Dims.ours(entity.level())) {
				ExpeditionService.onMobKilled(killer, entity);
			}
			if (!(entity instanceof ServerPlayer)) {
				AtonementGuard.forget(entity);
			}
		});

		// Cold Forge and Barren Hand: the furnaces a cursed player works and the crops they plant are marked
		UseBlockCallback.EVENT.register(com.extremedimensions.penalty.AbyssalWorld::onUse);
		// Crouching with fragments or silk loads a Spindle with all that fits (the game skips the block for a crouching hand)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			net.minecraft.world.level.block.state.BlockState at = level.getBlockState(hit.getBlockPos());
			if (!player.isSecondaryUseActive() || !at.is(com.extremedimensions.registry.ModBlocks.SPINDLE)) {
				return InteractionResult.PASS;
			}
			return com.extremedimensions.tetherite.SpindleBlock.load(at, level, hit.getBlockPos(), player, player.getItemInHand(hand), true);
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer fallen && com.extremedimensions.coop.FallenService.fallen(fallen)) {
				return InteractionResult.FAIL;
			}
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
			// The First Weaver's trail: silk on the frame of an Ancient City, a loom in the Seam, the line on the First Knot's heart
			if (hand == InteractionHand.MAIN_HAND && player.getItemInHand(hand).is(ModItems.SEAM_SILK)
					&& level.getBlockState(hit.getBlockPos()).is(Blocks.REINFORCED_DEEPSLATE) && Dims.OVERWORLD.equals(Dims.of(level))) {
				return player instanceof ServerPlayer sp ? com.extremedimensions.lore.FirstWeaver.string(sp, hit.getBlockPos(), player.getItemInHand(hand))
						: InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND && level.getBlockState(hit.getBlockPos()).is(Blocks.LOOM)
					&& SeamService.inSeam(sp) && !sp.isSecondaryUseActive()) {
				InteractionResult r = com.extremedimensions.lore.FirstWeaver.loom(sp, hit.getBlockPos());
				if (r != InteractionResult.PASS) {
					return r;
				}
			}
			if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND && player.getItemInHand(hand).is(ModItems.PLUMB_LINE)
					&& SeamService.inSeam(sp) && com.extremedimensions.seam.FirstKnot.isHeart(hit.getBlockPos())) {
				return com.extremedimensions.seam.FirstKnot.wake(sp);
			}
			// The Weaver's Loom: the world's tapestry (sneaking with something in hand places or uses that instead)
			if (hand == InteractionHand.MAIN_HAND && level.getBlockState(hit.getBlockPos()).is(com.extremedimensions.registry.ModBlocks.WEAVERS_LOOM)
					&& !(player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty())) {
				return player instanceof ServerPlayer sp ? com.extremedimensions.tapestry.Tapestry.use(sp, hit.getBlockPos(), player.getItemInHand(hand))
						: InteractionResult.SUCCESS;
			}
			if (player.getItemInHand(hand).is(ModItems.SEAM_NEEDLE) && level.getBlockState(hit.getBlockPos()).is(Blocks.CRYING_OBSIDIAN)) {
				return player instanceof ServerPlayer sp
						? SeamService.openRift(sp, hit.getBlockPos(), hit.getDirection(), player.getItemInHand(hand))
						: InteractionResult.SUCCESS;
			}
			// Only when the barrel really opens (sneaking with something in hand places or uses it instead)
			if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND && level.getBlockState(hit.getBlockPos()).is(Blocks.BARREL)
					&& !(sp.isSecondaryUseActive() && (!sp.getMainHandItem().isEmpty() || !sp.getOffhandItem().isEmpty()))) {
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
			// The Elders' spells forbid some things outright, on both sides (milk under the Matriarch's hex, rockets
			// with bound wings)
			InteractionResult forbidden = com.extremedimensions.elder.ElderSpells.refuse(player, player.getItemInHand(hand));
			if (forbidden != InteractionResult.PASS) {
				return forbidden;
			}
			InteractionResult jaw = com.extremedimensions.penalty.AbyssalCurses.refuseFood(player, player.getItemInHand(hand));
			if (jaw != InteractionResult.PASS) {
				return jaw;
			}
			if (player instanceof ServerPlayer fallen && com.extremedimensions.coop.FallenService.fallen(fallen)) {
				return InteractionResult.FAIL;
			}
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
			if (player instanceof ServerPlayer sp && player.getItemInHand(hand).is(ModItems.LOOSE_LEAF)) {
				return Journal.readLeaf(sp, DataStore.get(sp.getUUID()));
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
			// Barren Hand, Cave-In: a harvest, deepslate, deep stone giving way
			com.extremedimensions.penalty.AbyssalWorld.afterBreak(level, player, pos, state);
			AnchorService.onBroken(Dims.of(level), pos);
			if (player instanceof ServerPlayer sp) {
				CurseEffects.wear(sp);
				// Veinseeker: the ore and veins near broken stone ring out
				com.extremedimensions.binding.Charms.seek(sp, pos, state);
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

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				player instanceof ServerPlayer fallen && com.extremedimensions.coop.FallenService.fallen(fallen) ? InteractionResult.FAIL : InteractionResult.PASS);
		// A bar of the Ferryman's gaol struck from inside: enough blows break it (the blow itself does nothing more)
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp) {
				com.extremedimensions.elder.ElderGaol.struck(sp, entity);
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && entity instanceof net.minecraft.world.entity.LivingEntity) {
				CurseEffects.wear(sp);
			}
			return InteractionResult.PASS;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer fallen && com.extremedimensions.coop.FallenService.fallen(fallen)) {
				return InteractionResult.FAIL;
			}
			if (!Dims.ours(level)) {
				return InteractionResult.PASS;
			}
			if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && entity instanceof ServerPlayer) {
				// Co-op: sneak and an empty hand on an ally, to carry one of their curses
				InteractionResult burden = com.extremedimensions.coop.BurdenService.interact(sp, entity);
				if (burden != InteractionResult.PASS) {
					return burden;
				}
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
		// Co-op: allies' teams are looked up by name, also while this player is away
		d.name = p.getScoreboardName();
		// Joining in another mod's world: the mod picks the player up again where they come back to one of its own
		d.away = !Dims.ours(p);
		d.dim = d.away ? Dims.OVERWORLD : Dims.of(p);
		// Left during the pull of a Collapse: the death it was owed follows now (no second curse, no way out)
		d.collapsing = d.collapseOwed && !d.away;
		d.collapseRetry = 0;
		d.inRun = !d.collapsing && Dims.isBeyond(d.dim) && !d.linked.isEmpty();
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
		// What they knew before the first-meeting hints existed is not told again
		com.extremedimensions.guide.Hints.known(p, d);
		// The Elders they helped defeat, from their advancements, for the stats export (also while they are offline)
		com.extremedimensions.compat.StatsExport.elders(p);
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
		com.extremedimensions.elder.FerrymanService.collectOwed(p, d);
		com.extremedimensions.elder.ElderUltimate.collectOwed(p, d);
		com.extremedimensions.tapestry.Unweaving.onJoin(p, d);
		List<RecipeHolder<?>> recipes = new ArrayList<>();
		for (String recipe : RECIPES) {
			p.level().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, WorldFx.id(recipe))).ifPresent(recipes::add);
		}
		p.awardRecipes(recipes);
		Journal.deliverFirst(p, d);
		Advancements.grant(p, Advancements.ROOT);
	}

	/** How long the mod's own work took in the last server tick, in nanoseconds. */
	public static long lastTickNanos() {
		return lastTickNanos;
	}

	/** The CPU time of the mod's own work in the last server tick, while the performance tests listen. */
	public static long lastTickCpuNanos() {
		return lastTickCpuNanos;
	}

	private static void tick(MinecraftServer s) {
		long t0 = System.nanoTime();
		long c0 = TickProfile.on() ? TickProfile.cpu() : 0;
		try {
			work(s);
		} finally {
			lastTickNanos = System.nanoTime() - t0;
			lastTickCpuNanos = c0 == 0 ? 0 : TickProfile.cpu() - c0;
			TickProfile.rest();
		}
	}

	private static void work(MinecraftServer s) {
		com.extremedimensions.compat.TabPlaceholders.tick(s);
		com.extremedimensions.compat.StatsExport.tick(s);
		// (each part timed while the performance tests listen; see TickProfile)
		long lap = TickProfile.start();
		com.extremedimensions.lore.FirstWeaver.tick(s);
		com.extremedimensions.tapestry.Tapestry.tick(s);
		if (!STRAYS.isEmpty()) {
			for (net.minecraft.world.entity.Entity e : STRAYS) {
				if (!e.isRemoved()) {
					e.discard();
				}
			}
			STRAYS.clear();
		}
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.get(p.getUUID());
			String dim = Dims.of(p);
			boolean sync = false;
			// Each player's slower work comes round on a tick of its own (by their id), never everyone's in one tick
			int beat = s.getTickCount() + p.getId();

			if (d.grace > 0) {
				d.grace--;
			}
			if (beat % 40 == 0 && !d.away) {
				// Something in hand for the first time: its hint
				com.extremedimensions.guide.Hints.look(p, d);
			}
			if (beat % 10 == 0) {
				// A Plumb Line in hand: which way it pulls
				com.extremedimensions.seam.FirstKnot.point(p);
			}
			if (!d.joinDone && ++d.ticksOnline >= Config.JOIN_DELAY_TICKS) {
				d.joinDone = true;
				afterJoin(p, d);
			}
			if (d.offerDelay > 0 && --d.offerDelay == 0) {
				d.offerDelay = -1;
				d.offerAge = 0;
				Sync.offer(p, d);
			}
			// The clock runs only while the offer can be seen: not on the death screen, nor before it is shown
			if (d.pendingOffer.isEmpty() || d.offerDelay > 0 || !p.isAlive()) {
				d.offerAge = 0;
			} else if (Config.OFFER_TIMEOUT_SECONDS > 0 && ++d.offerAge >= Config.OFFER_TIMEOUT_SECONDS * 20) {
				// An offer left unanswered too long: the Tether chooses
				d.offerAge = 0;
				String pick = d.pendingOffer.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(d.pendingOffer.size()));
				p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.choice.timeout"));
				PenaltyService.choose(p, pick);
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

			if (beat % 100 == 0) {
				CurseEffects.refreshEffects(p, d);
				discover(p, d, dim);
			}
			if (beat % 20 == 0) {
				com.extremedimensions.tribe.Standing.second(p, d);
			}
			if (beat % 40 == 7) {
				com.extremedimensions.journal.Bestiary.scan(p);
			}

			d.tickCounter++;
			if (sync || d.tickCounter % Config.SYNC_INTERVAL_TICKS == 0) {
				Sync.state(p, d);
			}
		}

		lap = TickProfile.lap("players", lap);
		TetherService.sweep(s);
		com.extremedimensions.expedition.ExpeditionBar.tick(s);
		com.extremedimensions.lore.WarmChains.tick(s);
		com.extremedimensions.binding.Veilsight.tick(s);
		com.extremedimensions.binding.Charms.tick(s);
		com.extremedimensions.binding.WoundLens.tick(s);
		com.extremedimensions.events.WorldEvents.tick(s);
		if (s.getTickCount() % 1200 == 0) {
			AtonementGuard.sweep(s);
		}
		AnchorService.tick(s);
		LurkerService.tick(s);
		com.extremedimensions.penalty.AbyssalTolls.serverTick(s);
		com.extremedimensions.penalty.AbyssalWorld.serverTick(s);
		HollowService.tick(s);
		lap = TickProfile.lap("world", lap);
		SeamService.tick(s);
		com.extremedimensions.seam.FirstKnot.tick(s);
		com.extremedimensions.seam.SeamFauna.tick(s);
		WorldService.tick(s);
		MendingService.tick(s);
		lap = TickProfile.lap("seam", lap);
		com.extremedimensions.elder.FerrymanService.tick(s);
		lap = TickProfile.lap("ferryman", lap);
		com.extremedimensions.elder.MatriarchService.tick(s);
		lap = TickProfile.lap("matriarch", lap);
		com.extremedimensions.elder.ElderStrikes.tick(s);
		lap = TickProfile.lap("strikes", lap);
		com.extremedimensions.elder.ElderSpells.sweep(s);
		com.extremedimensions.elder.ElderGaol.tick(s);
		lap = TickProfile.lap("spells and gaol", lap);
		com.extremedimensions.elder.ElderTerrain.tick(s);
		lap = TickProfile.lap("terrain", lap);
		com.extremedimensions.elder.WeaverService.tick(s);
		lap = TickProfile.lap("weaver", lap);
		com.extremedimensions.elder.ElderArena.tick(s);
		lap = TickProfile.lap("arena", lap);
		com.extremedimensions.coop.FallenService.tick(s);
		com.extremedimensions.tribe.RaidService.tick(s);
		com.extremedimensions.elder.AttackFx.flush(s);
		TickProfile.lap("fx and the rest", lap);
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
