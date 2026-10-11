package com.extremedimensions;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Alle netwerkpakketten van de mod. */
public final class ModPackets {
	private ModPackets() {}

	/**
	 * Server -> client: de volledige status van de speler (voor HUD en statusscherm).
	 * nearNether/nearEnd: charge van geladen altaren in de buurt, -1 = geen altaar in de buurt.
	 * runCharge/runMax: charge van de lopende expeditie en de charge waarmee hij begon.
	 * penaltyClock: 0 = penalty's lopen niet af, 1 = lopen af, 2 = gepauzeerd (AFK).
	 * tetherDist: -1 = niet vastgemaakt, anders afstand in blokken tot je aankomstportaal (0 in de Overworld).
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

	/** Server -> client: korte melding in beeld. */
	public record NoticePayload(String text, int color) implements CustomPacketPayload {
		public static final Type<NoticePayload> TYPE = new Type<>(ExtremeDimensions.id("notice"));
		public static final StreamCodec<FriendlyByteBuf, NoticePayload> CODEC = StreamCodec.ofMember(NoticePayload::write, NoticePayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(text);
			buf.writeInt(color);
		}

		private static NoticePayload read(FriendlyByteBuf buf) {
			return new NoticePayload(buf.readUtf(), buf.readInt());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: open het penalty-keuzescherm met deze penalty-id's (komma-gescheiden). */
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

	/** Server -> client: open het statusscherm (altaar). */
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

	/** Server -> client: start een animatie (zie Fx). */
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

	/** Server -> client: open het Tether Journal met deze ontgrendelde pagina's (komma-gescheiden). */
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

	/** Client -> server: de gekozen penalty. */
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
