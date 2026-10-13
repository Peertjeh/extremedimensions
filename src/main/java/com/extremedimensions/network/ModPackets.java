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
	 */
	public record StatePayload(int nearNether, int nearEnd, int runCharge, int runMax, int dimCode, int drainX100,
							int expeditions, int collapses, int longestRun, int runSecs, String penalties,
							int scars, int tetherDist) implements CustomPacketPayload {
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
		}

		private static StatePayload read(FriendlyByteBuf buf) {
			return new StatePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
					buf.readVarInt(), buf.readVarInt());
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
}
