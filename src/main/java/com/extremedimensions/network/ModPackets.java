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
	 * penalties: "id:stacks:seconds;..." (seconds -1 = next expedition, -2 = XP debt).
	 * penaltyClock: 0 = not counting here, 1 = wearing off, 2 = paused (idle).
	 * tetherDist: -1 = not bound, else blocks to the arrival portal (beyond) or to the anchor (Overworld).
	 */
	public record StatePayload(int nearNether, int nearEnd, int runCharge, int runMax, int dimCode, int drainX100,
							int expeditions, int collapses, int longestRun, int runSecs, String penalties,
							int penaltyClock, int tetherDist) implements CustomPacketPayload {
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
			buf.writeVarInt(penaltyClock);
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

	/** Server -> client: open the penalty choice screen with these ids (comma separated). */
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
}
