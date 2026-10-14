package com.extremedimensions.network;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Every network payload of the mod. */
public final class ModPackets {
	private ModPackets() {}

	/**
	 * Server -> client: the full player state for the HUD and screens.
	 * nearNether/nearEnd: own charge in charged anchors nearby, -1 = no anchor nearby.
	 * runCharge/runMax: charge left on the current expedition and what it started with.
	 * penalties: "id:stacks:max;..." for every curse the player carries.
	 * scars: curse stacks taken off (each one lightens the drain).
	 * tetherDist: -1 = not bound, else blocks to the arrival portal (beyond) or to the anchor (Overworld).
	 * guide: steps of the first-expedition guide done (Guide.STEPS = finished).
	 */
	public record StatePayload(int nearNether, int nearEnd, int runCharge, int runMax, int dimCode, int drainX100,
							int expeditions, int collapses, int longestRun, int runSecs, String penalties,
							int scars, int tetherDist, int guide) implements CustomPacketPayload {
		public static final Type<StatePayload> TYPE = new Type<>(ExtremeDimensions.id("state"));
		public static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC = StreamCodec.ofMember(StatePayload::write, StatePayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(nearNether);
			buf.writeVarInt(nearEnd);
			buf.writeVarInt(runCharge);
			buf.writeVarInt(runMax);
			buf.writeVarInt(dimCode);
			buf.writeVarInt(drainX100);
			buf.writeVarInt(expeditions);
			buf.writeVarInt(collapses);
			buf.writeVarInt(longestRun);
			buf.writeVarInt(runSecs);
			buf.writeUtf(penalties);
			buf.writeVarInt(scars);
			buf.writeVarInt(tetherDist);
			buf.writeVarInt(guide);
		}

		private static StatePayload read(FriendlyByteBuf buf) {
			return new StatePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: open the curse choice screen; cards "id:multiplier:stacks:max:valuePerStack", comma separated. */
	public record OfferPayload(String ids) implements CustomPacketPayload {
		public static final Type<OfferPayload> TYPE = new Type<>(ExtremeDimensions.id("offer"));
		public static final StreamCodec<FriendlyByteBuf, OfferPayload> CODEC = StreamCodec.ofMember(OfferPayload::write, OfferPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(ids);
		}

		private static OfferPayload read(FriendlyByteBuf buf) {
			return new OfferPayload(buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: open the anchor status screen. */
	public record OpenStatusPayload() implements CustomPacketPayload {
		public static final Type<OpenStatusPayload> TYPE = new Type<>(ExtremeDimensions.id("open_status"));
		public static final StreamCodec<FriendlyByteBuf, OpenStatusPayload> CODEC = StreamCodec.ofMember(OpenStatusPayload::write, OpenStatusPayload::read);

		private void write(FriendlyByteBuf buf) {
		}

		private static OpenStatusPayload read(FriendlyByteBuf buf) {
			return new OpenStatusPayload();
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: play an animation (see {@link Fx}). */
	public record FxPayload(int kind, int value) implements CustomPacketPayload {
		public static final Type<FxPayload> TYPE = new Type<>(ExtremeDimensions.id("fx"));
		public static final StreamCodec<FriendlyByteBuf, FxPayload> CODEC = StreamCodec.ofMember(FxPayload::write, FxPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(kind);
			buf.writeVarInt(value);
		}

		private static FxPayload read(FriendlyByteBuf buf) {
			return new FxPayload(buf.readVarInt(), buf.readVarInt());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: open the Tether Journal with these unlocked pages (comma separated). */
	public record OpenJournalPayload(String ids) implements CustomPacketPayload {
		public static final Type<OpenJournalPayload> TYPE = new Type<>(ExtremeDimensions.id("open_journal"));
		public static final StreamCodec<FriendlyByteBuf, OpenJournalPayload> CODEC = StreamCodec.ofMember(OpenJournalPayload::write, OpenJournalPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(ids);
		}

		private static OpenJournalPayload read(FriendlyByteBuf buf) {
			return new OpenJournalPayload(buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: the player opened the journal from the inventory. */
	public record RequestJournalPayload() implements CustomPacketPayload {
		public static final Type<RequestJournalPayload> TYPE = new Type<>(ExtremeDimensions.id("request_journal"));
		public static final StreamCodec<FriendlyByteBuf, RequestJournalPayload> CODEC = StreamCodec.ofMember(RequestJournalPayload::write, RequestJournalPayload::read);

		private void write(FriendlyByteBuf buf) {
		}

		private static RequestJournalPayload read(FriendlyByteBuf buf) {
			return new RequestJournalPayload();
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: the chosen penalty. */
	public record ChoosePayload(String id) implements CustomPacketPayload {
		public static final Type<ChoosePayload> TYPE = new Type<>(ExtremeDimensions.id("choose"));
		public static final StreamCodec<FriendlyByteBuf, ChoosePayload> CODEC = StreamCodec.ofMember(ChoosePayload::write, ChoosePayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(id);
		}

		private static ChoosePayload read(FriendlyByteBuf buf) {
			return new ChoosePayload(buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
	/** Client -> server: the Curses button was pressed (open), or the open Curses screen wants fresh numbers. */
	public record RequestCursesPayload(boolean open) implements CustomPacketPayload {
		public static final Type<RequestCursesPayload> TYPE = new Type<>(ExtremeDimensions.id("request_curses"));
		public static final StreamCodec<FriendlyByteBuf, RequestCursesPayload> CODEC = StreamCodec.ofMember(RequestCursesPayload::write, RequestCursesPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeBoolean(open);
		}

		private static RequestCursesPayload read(FriendlyByteBuf buf) {
			return new RequestCursesPayload(buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: every curse for the Curses screen. data = "scars|atoning|overworldOn|entries|nearby", entries
	 * "id:stacks:max:atonement:cost:valuePerStack" separated by ';', nearby players "uuid,name,beyond,overworld,everywhere"
	 * separated by ';'. open = open the screen (else only refresh it).
	 */
	public record CursesPayload(String data, boolean open) implements CustomPacketPayload {
		public static final Type<CursesPayload> TYPE = new Type<>(ExtremeDimensions.id("curses"));
		public static final StreamCodec<FriendlyByteBuf, CursesPayload> CODEC = StreamCodec.ofMember(CursesPayload::write, CursesPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(data);
			buf.writeBoolean(open);
		}

		private static CursesPayload read(FriendlyByteBuf buf) {
			return new CursesPayload(buf.readUtf(), buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: atone for this curse (and mend it first). */
	public record AtonePayload(String id) implements CustomPacketPayload {
		public static final Type<AtonePayload> TYPE = new Type<>(ExtremeDimensions.id("atone"));
		public static final StreamCodec<FriendlyByteBuf, AtonePayload> CODEC = StreamCodec.ofMember(AtonePayload::write, AtonePayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(id);
		}

		private static AtonePayload read(FriendlyByteBuf buf) {
			return new AtonePayload(buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: the world's settings (the config file's layout, as JSON) and whether this player may change
	 * them (an operator, or the owner of a world that is not open to LAN).
	 */
	public record SettingsPayload(String json, boolean canEdit) implements CustomPacketPayload {
		public static final Type<SettingsPayload> TYPE = new Type<>(ExtremeDimensions.id("settings"));
		public static final StreamCodec<FriendlyByteBuf, SettingsPayload> CODEC = StreamCodec.ofMember(SettingsPayload::write, SettingsPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(json, 262144);
			buf.writeBoolean(canEdit);
		}

		private static SettingsPayload read(FriendlyByteBuf buf) {
			return new SettingsPayload(buf.readUtf(262144), buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: send the world's settings (the settings screen opened). */
	public record RequestSettingsPayload() implements CustomPacketPayload {
		public static final Type<RequestSettingsPayload> TYPE = new Type<>(ExtremeDimensions.id("request_settings"));
		public static final StreamCodec<FriendlyByteBuf, RequestSettingsPayload> CODEC = StreamCodec.ofMember(RequestSettingsPayload::write, RequestSettingsPayload::read);

		private void write(FriendlyByteBuf buf) {
		}

		private static RequestSettingsPayload read(FriendlyByteBuf buf) {
			return new RequestSettingsPayload();
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: change world settings; changes = JSON object of key -> new value as text (see Config.set). */
	public record ChangeSettingsPayload(String changes) implements CustomPacketPayload {
		public static final Type<ChangeSettingsPayload> TYPE = new Type<>(ExtremeDimensions.id("change_settings"));
		public static final StreamCodec<FriendlyByteBuf, ChangeSettingsPayload> CODEC = StreamCodec.ofMember(ChangeSettingsPayload::write, ChangeSettingsPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(changes, 262144);
		}

		private static ChangeSettingsPayload read(FriendlyByteBuf buf) {
			return new ChangeSettingsPayload(buf.readUtf(262144));
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: take the relic off the Tether. */
	public record UnbindRelicPayload() implements CustomPacketPayload {
		public static final Type<UnbindRelicPayload> TYPE = new Type<>(ExtremeDimensions.id("unbind_relic"));
		public static final StreamCodec<FriendlyByteBuf, UnbindRelicPayload> CODEC = StreamCodec.ofMember(UnbindRelicPayload::write, UnbindRelicPayload::read);

		private void write(FriendlyByteBuf buf) {
		}

		private static UnbindRelicPayload read(FriendlyByteBuf buf) {
			return new UnbindRelicPayload();
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: swear a pact; pact = "id:n,id:n" (empty renounces it). */
	public record SwearPactPayload(String pact) implements CustomPacketPayload {
		public static final Type<SwearPactPayload> TYPE = new Type<>(ExtremeDimensions.id("swear_pact"));
		public static final StreamCodec<FriendlyByteBuf, SwearPactPayload> CODEC = StreamCodec.ofMember(SwearPactPayload::write, SwearPactPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(pact);
		}

		private static SwearPactPayload read(FriendlyByteBuf buf) {
			return new SwearPactPayload(buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
