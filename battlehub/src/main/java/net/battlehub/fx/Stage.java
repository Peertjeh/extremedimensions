package net.battlehub.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.battlehub.hub.ClientEntities;
import net.battlehub.mixin.DisplayAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A stage for one viewer (1.47): blocks, items and texts in the air that exist only on that player's client, so every
 * viewer can have the show over their own head and nothing in the world changes. Things grow and shrink smoothly (the
 * client interpolates a display's size). The data fields a display needs are looked up by name once; a field this
 * game version does not have costs only that part of the show.
 */
public final class Stage {
	private final ServerPlayer viewer;
	private final ServerLevel level;
	private final List<Integer> ids = new ArrayList<>();
	private final Map<Integer, Float> centred = new HashMap<>();

	public Stage(ServerPlayer viewer) {
		this.viewer = viewer;
		this.level = viewer.level();
	}

	public ServerPlayer viewer() {
		return viewer;
	}

	// ------------------------------------------------------------------ the data fields, by name

	private static final Map<String, Object> FIELDS = new HashMap<>();

	@SuppressWarnings("unchecked")
	private static <T> EntityDataAccessor<T> field(Class<?> owner, String name) {
		Object f = FIELDS.computeIfAbsent(owner.getName() + "." + name, k -> {
			try {
				java.lang.reflect.Field fl = owner.getDeclaredField(name);
				fl.setAccessible(true);
				return fl.get(null);
			} catch (ReflectiveOperationException | RuntimeException e) {
				net.battlehub.BattleHub.LOGGER.warn("BattleHub stage: no {} in this game version", name);
				return Boolean.FALSE;
			}
		});
		return f instanceof EntityDataAccessor<?> a ? (EntityDataAccessor<T>) a : null;
	}

	private static EntityDataAccessor<BlockState> blockState() {
		return field(Display.BlockDisplay.class, "DATA_BLOCK_STATE_ID");
	}

	private static EntityDataAccessor<ItemStack> itemStack() {
		return field(Display.ItemDisplay.class, "DATA_ITEM_STACK_ID");
	}

	private static EntityDataAccessor<Integer> interpolationStart() {
		return field(Display.class, "DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID");
	}

	private static EntityDataAccessor<Integer> interpolationLength() {
		return field(Display.class, "DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID");
	}

	private static EntityDataAccessor<Float> viewRange() {
		return field(Display.class, "DATA_VIEW_RANGE_ID");
	}

	private static EntityDataAccessor<Float> width() {
		return field(Display.class, "DATA_WIDTH_ID");
	}

	private static EntityDataAccessor<Float> height() {
		return field(Display.class, "DATA_HEIGHT_ID");
	}

	/** How far off a thing is drawn (times the usual distance), and the box the client keeps on screen for it. */
	private float range = 1f;

	/** Things put up from now on are drawn from {@code range} times as far (a stage high over a city: about 4). */
	public Stage range(float range) {
		this.range = range;
		return this;
	}

	private void sized(List<SynchedEntityData.DataValue<?>> data, float w, float h) {
		if (range != 1f) put(data, viewRange(), range);
		// a big thing whose anchor goes off screen would vanish without a box of its size
		put(data, width(), w);
		put(data, height(), h);
	}

	private static <T> void put(List<SynchedEntityData.DataValue<?>> data, EntityDataAccessor<T> acc, T value) {
		if (acc != null) data.add(SynchedEntityData.DataValue.create(acc, value));
	}

	// ------------------------------------------------------------------ things on the stage

	private int add(String type, Vec3 at, float yaw, List<SynchedEntityData.DataValue<?>> data) {
		EntityType<?> t = ClientEntities.type(type);
		ClientEntities.Handle h = ClientEntities.handle(level, t);
		if (h == null) return -1;
		ClientEntities.add(viewer, h, t, at, yaw, yaw);
		if (!data.isEmpty()) viewer.connection.send(new ClientboundSetEntityDataPacket(h.id(), data));
		ids.add(h.id());
		return h.id();
	}

	/** A block, its middle at {@code at}, {@code scale} big (0: invisible until it grows), glowing. */
	public int block(Vec3 at, BlockState state, float scale) {
		List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
		put(data, blockState(), state);
		put(data, DisplayAccessor.battlehub$scale(), (org.joml.Vector3fc) new Vector3f(scale, scale, scale));
		put(data, DisplayAccessor.battlehub$translation(), (org.joml.Vector3fc) new Vector3f(-scale / 2, -scale / 2, -scale / 2));
		put(data, DisplayAccessor.battlehub$brightness(), ClientEntities.FULL_BRIGHT);
		sized(data, Math.max(2f, scale), Math.max(2f, scale));
		int id = add("minecraft:block_display", at, 0f, data);
		if (id >= 0) centred.put(id, 1f);
		return id;
	}

	/** A tall pillar (a block stretched up from {@code base}), glowing: a beam of light. */
	public int beam(Vec3 base, BlockState state, float width, float height) {
		List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
		put(data, blockState(), state);
		put(data, DisplayAccessor.battlehub$scale(), (org.joml.Vector3fc) new Vector3f(width, height, width));
		put(data, DisplayAccessor.battlehub$translation(), (org.joml.Vector3fc) new Vector3f(-width / 2, 0, -width / 2));
		put(data, DisplayAccessor.battlehub$brightness(), ClientEntities.FULL_BRIGHT);
		sized(data, Math.max(1f, width), height);
		return add("minecraft:block_display", base, 0f, data);
	}

	/** An item (a player's head), always turned to the viewer, {@code scale} big, glowing. */
	public int item(Vec3 at, ItemStack stack, float scale) {
		List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
		put(data, itemStack(), stack);
		put(data, DisplayAccessor.battlehub$scale(), (org.joml.Vector3fc) new Vector3f(scale, scale, scale));
		put(data, DisplayAccessor.battlehub$billboard(), ClientEntities.BILLBOARD_CENTER);
		put(data, DisplayAccessor.battlehub$brightness(), ClientEntities.FULL_BRIGHT);
		sized(data, 16f, 16f);
		return add("minecraft:item_display", at, 0f, data);
	}

	/** A text, always turned to the viewer, without a background. */
	public int text(Vec3 at, Component text, float scale) {
		List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
		sized(data, 6 * scale, 2 * scale);
		int id = add("minecraft:text_display", at, 0f, data);
		if (id < 0) return id;
		ClientEntities.textStyle(viewer, id, 400, 0, ClientEntities.SHADOW, scale, ClientEntities.BILLBOARD_CENTER);
		ClientEntities.text(viewer, id, text);
		return id;
	}

	/**
	 * Grows or shrinks a thing to {@code scale} over {@code ticks} (a block keeps its middle where it was, a pillar its
	 * foot, an item or text its anchor).
	 */
	public void scale(int id, float scale, int ticks) {
		if (id < 0) return;
		List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
		put(data, interpolationStart(), 0);
		put(data, interpolationLength(), ticks);
		put(data, DisplayAccessor.battlehub$scale(), (org.joml.Vector3fc) new Vector3f(scale, scale, scale));
		if (centred.containsKey(id)) put(data, DisplayAccessor.battlehub$translation(), (org.joml.Vector3fc) new Vector3f(-scale / 2, -scale / 2, -scale / 2));
		viewer.connection.send(new ClientboundSetEntityDataPacket(id, data));
	}

	/** Moves a thing to {@code at}, gliding there over {@code ticks}. */
	public void move(int id, Vec3 at, int ticks) {
		if (id < 0) return;
		ClientEntities.glide(viewer, id, ticks);
		ClientEntities.move(viewer, id, at, 0f, 0f);
	}

	public void remove(int id) {
		if (id < 0) return;
		ids.remove((Integer) id);
		centred.remove(id);
		ClientEntities.remove(viewer, new int[] {id});
	}

	/** Takes everything down. */
	public void clear() {
		if (!ids.isEmpty() && !viewer.hasDisconnected()) ClientEntities.remove(viewer, ids.stream().mapToInt(Integer::intValue).toArray());
		ids.clear();
		centred.clear();
	}

	/** Things on the stage now (self-test). */
	public int size() {
		return ids.size();
	}
}
