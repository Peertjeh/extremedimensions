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
	 * trial: the Rift Trial under way in the Seam, "kind;twist;score;bronze;silver;gold" (empty without one).
	 * riftX/riftY/riftZ: where the rift home hangs in the Seam (its floor), for the column of light and the compass.
	 */
	public record StatePayload(int nearNether, int nearEnd, int runCharge, int runMax, int dimCode, int drainX100,
							int expeditions, int collapses, int longestRun, int runSecs, String penalties,
							int scars, int tetherDist, int guide, String trial, float riftX, float riftY, float riftZ) implements CustomPacketPayload {
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
			buf.writeUtf(trial);
			buf.writeFloat(riftX);
			buf.writeFloat(riftY);
			buf.writeFloat(riftZ);
		}

		private static StatePayload read(FriendlyByteBuf buf) {
			return new StatePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readFloat());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: open the curse choice screen; cards "id:multiplier:stacks:max:valuePerStack:readable", comma separated. */
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
	public record OpenStatusPayload(boolean sharing) implements CustomPacketPayload {
		public static final Type<OpenStatusPayload> TYPE = new Type<>(ExtremeDimensions.id("open_status"));
		public static final StreamCodec<FriendlyByteBuf, OpenStatusPayload> CODEC = StreamCodec.ofMember(OpenStatusPayload::write, OpenStatusPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeBoolean(sharing);
		}

		private static OpenStatusPayload read(FriendlyByteBuf buf) {
			return new OpenStatusPayload(buf.readBoolean());
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

	/**
	 * Server -> client: open the Tether Journal with these unlocked pages (comma separated), the bestiary
	 * ("id:seen:slain;..."), the ledger ("page:key=value;..."), the margin notes found, the path, and the player's clan
	 * (as {@code ClanService.journal} writes it).
	 */
	public record OpenJournalPayload(String ids, String beasts, String ledger, String notes, String path, String clan) implements CustomPacketPayload {
		public static final Type<OpenJournalPayload> TYPE = new Type<>(ExtremeDimensions.id("open_journal"));
		public static final StreamCodec<FriendlyByteBuf, OpenJournalPayload> CODEC = StreamCodec.ofMember(OpenJournalPayload::write, OpenJournalPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(ids);
			buf.writeUtf(beasts);
			buf.writeUtf(ledger);
			buf.writeUtf(notes);
			buf.writeUtf(path);
			buf.writeUtf(clan);
		}

		private static OpenJournalPayload read(FriendlyByteBuf buf) {
			return new OpenJournalPayload(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
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
	 * "id:stacks:max:atonement:cost:valuePerStack:readable:deed:deedTarget" separated by ';', nearby players "uuid,name,<stacks per set>,clan" (clan: 0 none, 1 the player's own, 2 another)
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

	/** Server -> client: the Trials screen's data (see TrialService.data); opens the screen or refreshes it. */
	public record TrialsPayload(String data) implements CustomPacketPayload {
		public static final Type<TrialsPayload> TYPE = new Type<>(ExtremeDimensions.id("trials"));
		public static final StreamCodec<FriendlyByteBuf, TrialsPayload> CODEC = StreamCodec.ofMember(TrialsPayload::write, TrialsPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(data);
		}

		private static TrialsPayload read(FriendlyByteBuf buf) {
			return new TrialsPayload(buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: send the Trials screen's data. */
	public record RequestTrialsPayload(boolean open) implements CustomPacketPayload {
		public static final Type<RequestTrialsPayload> TYPE = new Type<>(ExtremeDimensions.id("request_trials"));
		public static final StreamCodec<FriendlyByteBuf, RequestTrialsPayload> CODEC = StreamCodec.ofMember(RequestTrialsPayload::write, RequestTrialsPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeBoolean(open);
		}

		private static RequestTrialsPayload read(FriendlyByteBuf buf) {
			return new RequestTrialsPayload(buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: the operators' Players screen (see AdminService.data, JSON); opens the screen or refreshes it. */
	public record AdminPayload(String data, boolean open) implements CustomPacketPayload {
		public static final Type<AdminPayload> TYPE = new Type<>(ExtremeDimensions.id("admin"));
		public static final StreamCodec<FriendlyByteBuf, AdminPayload> CODEC = StreamCodec.ofMember(AdminPayload::write, AdminPayload::read);
		/** The longest data sent: hundreds of players and every curse of one fit well inside it. */
		public static final int MAX = 1 << 19;

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(data, MAX);
			buf.writeBoolean(open);
		}

		private static AdminPayload read(FriendlyByteBuf buf) {
			return new AdminPayload(buf.readUtf(MAX), buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: send the Players screen's data, with this player picked (a UUID, or empty for the asker). */
	public record RequestAdminPayload(String selected, boolean open) implements CustomPacketPayload {
		public static final Type<RequestAdminPayload> TYPE = new Type<>(ExtremeDimensions.id("request_admin"));
		public static final StreamCodec<FriendlyByteBuf, RequestAdminPayload> CODEC = StreamCodec.ofMember(RequestAdminPayload::write, RequestAdminPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(selected, 64);
			buf.writeBoolean(open);
		}

		private static RequestAdminPayload read(FriendlyByteBuf buf) {
			return new RequestAdminPayload(buf.readUtf(64), buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Client -> server: an operator lays a stack on a player ("add"), lifts one off ("lift") or lifts every curse
	 * ("clear"); silent: the player is not told (3.27.2).
	 */
	public record AdminActionPayload(String target, String action, String curse, boolean silent) implements CustomPacketPayload {
		public static final Type<AdminActionPayload> TYPE = new Type<>(ExtremeDimensions.id("admin_action"));
		public static final StreamCodec<FriendlyByteBuf, AdminActionPayload> CODEC = StreamCodec.ofMember(AdminActionPayload::write, AdminActionPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(target, 64);
			buf.writeUtf(action, 16);
			buf.writeUtf(curse, 64);
			buf.writeBoolean(silent);
		}

		private static AdminActionPayload read(FriendlyByteBuf buf) {
			return new AdminActionPayload(buf.readUtf(64), buf.readUtf(16), buf.readUtf(64), buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: take up the week's trial for the next visit to the Seam (or let it go). */
	public record ArmTrialPayload(boolean arm) implements CustomPacketPayload {
		public static final Type<ArmTrialPayload> TYPE = new Type<>(ExtremeDimensions.id("arm_trial"));
		public static final StreamCodec<FriendlyByteBuf, ArmTrialPayload> CODEC = StreamCodec.ofMember(ArmTrialPayload::write, ArmTrialPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeBoolean(arm);
		}

		private static ArmTrialPayload read(FriendlyByteBuf buf) {
			return new ArmTrialPayload(buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: whether the wound is after the player right now, so that leaving would cost them curses (see
	 * FleeService): {@code why} "swarm", "hunt", "keeper", "raid", "elder" or "blow" ("" when it is not), {@code seconds}
	 * how long a blow keeps them in danger yet (-1 while what hunts them lasts), {@code curses} what leaving would cost.
	 */
	public record DangerPayload(String why, int seconds, int curses) implements CustomPacketPayload {
		public static final Type<DangerPayload> TYPE = new Type<>(ExtremeDimensions.id("danger"));
		public static final StreamCodec<FriendlyByteBuf, DangerPayload> CODEC = StreamCodec.ofMember(DangerPayload::write, DangerPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(why, 16);
			buf.writeVarInt(seconds + 1);
			buf.writeVarInt(curses);
		}

		private static DangerPayload read(FriendlyByteBuf buf) {
			return new DangerPayload(buf.readUtf(16), buf.readVarInt() - 1, buf.readVarInt());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: what an expedition that ended in a Collapse (or a throw out of the Seam) came to, for the card the
	 * player is shown once home (see ExpeditionRecord): {@code realm} the world's short id, {@code outcome} the playtest
	 * log's word for how it ended, {@code seconds} how long it lasted and {@code charge} the charge (or thread) it began
	 * with, {@code farthest} in blocks from the portal (or the rift home); {@code distanceX100} and {@code drainX100} a
	 * hundred times how fast the timer ran on average against its base rate, from the distance alone (0 where the distance
	 * does not count) and all told; what it cost: {@code curses} as "id:stacks,...", the Seam Silk and Tether Fragments the
	 * Seam kept, and the atonement the death undid.
	 */
	public record RecapPayload(String realm, String outcome, int seconds, int charge, int farthest, int distanceX100, int drainX100,
			String curses, int silk, int fragments, int atonement) implements CustomPacketPayload {
		public static final Type<RecapPayload> TYPE = new Type<>(ExtremeDimensions.id("recap"));
		public static final StreamCodec<FriendlyByteBuf, RecapPayload> CODEC = StreamCodec.ofMember(RecapPayload::write, RecapPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(realm, 64);
			buf.writeUtf(outcome, 32);
			buf.writeVarInt(seconds);
			buf.writeVarInt(charge);
			buf.writeVarInt(farthest);
			buf.writeVarInt(distanceX100);
			buf.writeVarInt(drainX100);
			buf.writeUtf(curses, 4096);
			buf.writeVarInt(silk);
			buf.writeVarInt(fragments);
			buf.writeVarInt(atonement);
		}

		private static RecapPayload read(FriendlyByteBuf buf) {
			return new RecapPayload(buf.readUtf(64), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readUtf(4096), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: one tick of a Pale Squall's gust on the player, added to their own motion where it is made, on
	 * their client ({@code x}, {@code z}: blocks a tick). The server cannot push a player itself: the client moves them.
	 */
	public record SquallPayload(float x, float z) implements CustomPacketPayload {
		public static final Type<SquallPayload> TYPE = new Type<>(ExtremeDimensions.id("squall"));
		public static final StreamCodec<FriendlyByteBuf, SquallPayload> CODEC = StreamCodec.ofMember(SquallPayload::write, SquallPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeFloat(x);
			buf.writeFloat(z);
		}

		private static SquallPayload read(FriendlyByteBuf buf) {
			return new SquallPayload(buf.readFloat(), buf.readFloat());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: the player's motion as the server just set it (a shove, a pull, a drag), to be taken on by their
	 * client, which moves them: a motion set on the server alone never reaches the player it was set on (3.39.2).
	 */
	public record MotionPayload(double x, double y, double z) implements CustomPacketPayload {
		public static final Type<MotionPayload> TYPE = new Type<>(ExtremeDimensions.id("motion"));
		public static final StreamCodec<FriendlyByteBuf, MotionPayload> CODEC = StreamCodec.ofMember(MotionPayload::write, MotionPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeDouble(x);
			buf.writeDouble(y);
			buf.writeDouble(z);
		}

		private static MotionPayload read(FriendlyByteBuf buf) {
			return new MotionPayload(buf.readDouble(), buf.readDouble(), buf.readDouble());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: the chance of the Tetherite binding waiting at the smithing table the player has open, in
	 * permille, with the shares that make it ({@code permille} -1: no binding waits there), and where it would have to be
	 * done ({@code where}: 0 here, 1 in the Seam, 2 down in the Fray).
	 */
	public record BindingPayload(int permille, java.util.List<com.extremedimensions.tetherite.TetheriteBinding.Part> parts, boolean capped, int where)
			implements CustomPacketPayload {
		public static final Type<BindingPayload> TYPE = new Type<>(ExtremeDimensions.id("binding"));
		public static final StreamCodec<FriendlyByteBuf, BindingPayload> CODEC = StreamCodec.ofMember(BindingPayload::write, BindingPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(permille + 1);
			buf.writeBoolean(capped);
			buf.writeVarInt(where);
			buf.writeVarInt(Math.min(parts.size(), 32));
			for (int i = 0; i < Math.min(parts.size(), 32); i++) {
				var part = parts.get(i);
				buf.writeUtf(part.kind(), 16);
				buf.writeUtf(part.label(), 256);
				buf.writeInt(part.level());
				buf.writeDouble(part.share());
			}
		}

		private static BindingPayload read(FriendlyByteBuf buf) {
			int permille = buf.readVarInt() - 1;
			boolean capped = buf.readBoolean();
			int where = buf.readVarInt();
			int n = Math.min(buf.readVarInt(), 32);
			java.util.List<com.extremedimensions.tetherite.TetheriteBinding.Part> parts = new java.util.ArrayList<>();
			for (int i = 0; i < n; i++) {
				parts.add(new com.extremedimensions.tetherite.TetheriteBinding.Part(buf.readUtf(16), buf.readUtf(256), buf.readInt(), buf.readDouble()));
			}
			return new BindingPayload(permille, parts, capped, where);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: an Elder's fight music (0 the Matriarch, 1 the Ferryman, 2 the Last Weaver): a track asked for
	 * again (it plays while asked for), or, with {@code stop}, a fight's track ended (-1 with stop: every track).
	 */
	public record BossMusicPayload(int track, boolean stop) implements CustomPacketPayload {
		public static final Type<BossMusicPayload> TYPE = new Type<>(ExtremeDimensions.id("boss_music"));
		public static final StreamCodec<FriendlyByteBuf, BossMusicPayload> CODEC = StreamCodec.ofMember(BossMusicPayload::write, BossMusicPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(track + 1);
			buf.writeBoolean(stop);
		}

		private static BossMusicPayload read(FriendlyByteBuf buf) {
			return new BossMusicPayload(buf.readVarInt() - 1, buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: the cinema of an Elder's fight. {@code kind}: 0 = its title card as it arrives, 1 = the screen
	 * shakes ({@code strength}, for {@code value} ticks), 2 = no longer sent (blows went unnamed in 2.30), 3 = its
	 * ultimate begins ({@code key} its name, {@code value} ticks long: black bars, unnamed, a gathering dark), 4 = a flash of
	 * light ({@code strength}), 5 = the ultimate breaks (the bars lift), 6 = it dies ({@code value} its entity id,
	 * {@code strength} the ticks its death takes, {@code key} its title: the bars close in and the view is held on it),
	 * 7 = it forms ({@code value} its entity id, {@code strength} the ticks it takes, negative to leave the view free,
	 * {@code key} a line for the bars). {@code boss}: 0 the Matriarch, 1 the Ferryman, 2 the Last Weaver.
	 */
	public record BossFxPayload(int kind, int boss, int value, float strength, String key) implements CustomPacketPayload {
		public static final int INTRO = 0;
		public static final int SHAKE = 1;
		public static final int ULTIMATE = 3;
		public static final int FLASH = 4;
		public static final int ULTIMATE_END = 5;
		public static final int DEATH = 6;
		/** An Elder forming: the bars close in, and with a positive strength (its length in ticks) the view is held on entity {@code value}. */
		public static final int SPAWN = 7;
		/**
		 * A blow landing with weight, near this player: the view jolts down, and the striker (entity {@code value}) holds
		 * still for an instant, an impact frame. {@code strength} 0..1.
		 */
		public static final int PUNCH = 8;
		public static final Type<BossFxPayload> TYPE = new Type<>(ExtremeDimensions.id("boss_fx"));
		public static final StreamCodec<FriendlyByteBuf, BossFxPayload> CODEC = StreamCodec.ofMember(BossFxPayload::write, BossFxPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(kind);
			buf.writeVarInt(boss);
			buf.writeVarInt(value);
			buf.writeFloat(strength);
			buf.writeUtf(key == null ? "" : key);
		}

		private static BossFxPayload read(FriendlyByteBuf buf) {
			return new BossFxPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * The Elders' attack effects of one tick, for one player: sigils, impacts, shockwaves, threads and beams, wisps and
	 * bursts (see {@code AttackFx} on each side). The client draws them in fine detail; {@code detail} scales how much
	 * debris it throws.
	 */
	public record AttackFxPayload(float detail, java.util.List<Fx> fx) implements CustomPacketPayload {
		public static final Type<AttackFxPayload> TYPE = new Type<>(ExtremeDimensions.id("attack_fx"));
		public static final StreamCodec<FriendlyByteBuf, AttackFxPayload> CODEC = StreamCodec.ofMember(AttackFxPayload::write, AttackFxPayload::read);

		/**
		 * One effect: what it is, whose (0 the Matriarch, 1 the Ferryman, 2 the Last Weaver), which kind of it, a point,
		 * a second point (or a speed or velocity), a size and a length in ticks (or an id).
		 */
		public record Fx(int kind, int elder, int variant, float x, float y, float z, float x2, float y2, float z2, float size, int ticks) {}

		private void write(FriendlyByteBuf buf) {
			buf.writeFloat(detail);
			buf.writeVarInt(fx.size());
			for (Fx f : fx) {
				buf.writeByte(f.kind());
				buf.writeByte(f.elder());
				buf.writeByte(f.variant());
				buf.writeFloat(f.x());
				buf.writeFloat(f.y());
				buf.writeFloat(f.z());
				buf.writeFloat(f.x2());
				buf.writeFloat(f.y2());
				buf.writeFloat(f.z2());
				buf.writeFloat(f.size());
				buf.writeVarInt(f.ticks());
			}
		}

		private static AttackFxPayload read(FriendlyByteBuf buf) {
			float detail = buf.readFloat();
			int n = buf.readVarInt();
			java.util.List<Fx> fx = new java.util.ArrayList<>(Math.min(n, 4096));
			for (int i = 0; i < n; i++) {
				fx.add(new Fx(buf.readByte(), buf.readByte(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
						buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));
			}
			return new AttackFxPayload(detail, fx);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: open (or close) the player's anchors to their allies. */
	public record ShareAnchorsPayload(boolean on) implements CustomPacketPayload {
		public static final Type<ShareAnchorsPayload> TYPE = new Type<>(ExtremeDimensions.id("share_anchors"));
		public static final StreamCodec<FriendlyByteBuf, ShareAnchorsPayload> CODEC = StreamCodec.ofMember(ShareAnchorsPayload::write, ShareAnchorsPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeBoolean(on);
		}

		private static ShareAnchorsPayload read(FriendlyByteBuf buf) {
			return new ShareAnchorsPayload(buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: an ally's curses, to carry one for them: "id:stacks:max:mine:readable;..." (mine: the stacks of it the
	 * player already carries).
	 */
	/** Client -> server: from the Curses screen, hold out a Blood Cord to a player standing near (see ClanService). */
	public record ClanInvitePayload(java.util.UUID target) implements CustomPacketPayload {
		public static final Type<ClanInvitePayload> TYPE = new Type<>(ExtremeDimensions.id("clan_invite"));
		public static final StreamCodec<FriendlyByteBuf, ClanInvitePayload> CODEC = StreamCodec.ofMember(ClanInvitePayload::write, ClanInvitePayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUUID(target);
		}

		private static ClanInvitePayload read(FriendlyByteBuf buf) {
			return new ClanInvitePayload(buf.readUUID());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record BurdenPayload(java.util.UUID friend, String name, String curses) implements CustomPacketPayload {
		public static final Type<BurdenPayload> TYPE = new Type<>(ExtremeDimensions.id("burden"));
		public static final StreamCodec<FriendlyByteBuf, BurdenPayload> CODEC = StreamCodec.ofMember(BurdenPayload::write, BurdenPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUUID(friend);
			buf.writeUtf(name);
			buf.writeUtf(curses);
		}

		private static BurdenPayload read(FriendlyByteBuf buf) {
			return new BurdenPayload(buf.readUUID(), buf.readUtf(), buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: offer to carry one stack of an ally's curse (they are asked first). */
	public record CarryPayload(java.util.UUID friend, String curse) implements CustomPacketPayload {
		public static final Type<CarryPayload> TYPE = new Type<>(ExtremeDimensions.id("carry"));
		public static final StreamCodec<FriendlyByteBuf, CarryPayload> CODEC = StreamCodec.ofMember(CarryPayload::write, CarryPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUUID(friend);
			buf.writeUtf(curse);
		}

		private static CarryPayload read(FriendlyByteBuf buf) {
			return new CarryPayload(buf.readUUID(), buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: barter with a Hollowling (its entity id, the player's standing, the wares "id:price:left;..."). */
	public record BarterPayload(int young, int rep, String offers) implements CustomPacketPayload {
		public static final Type<BarterPayload> TYPE = new Type<>(ExtremeDimensions.id("barter"));
		public static final StreamCodec<FriendlyByteBuf, BarterPayload> CODEC = StreamCodec.ofMember(BarterPayload::write, BarterPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(young);
			buf.writeVarInt(rep);
			buf.writeUtf(offers);
		}

		private static BarterPayload read(FriendlyByteBuf buf) {
			return new BarterPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: the players fallen now (drawn crawling, and the player themselves made to crawl). */
	public record FallenPayload(java.util.List<java.util.UUID> fallen) implements CustomPacketPayload {
		public static final Type<FallenPayload> TYPE = new Type<>(ExtremeDimensions.id("fallen"));
		public static final StreamCodec<FriendlyByteBuf, FallenPayload> CODEC = StreamCodec.ofMember(FallenPayload::write, FallenPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(fallen.size());
			for (java.util.UUID id : fallen) {
				buf.writeUUID(id);
			}
		}

		private static FallenPayload read(FriendlyByteBuf buf) {
			int n = buf.readVarInt();
			java.util.List<java.util.UUID> list = new java.util.ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				list.add(buf.readUUID());
			}
			return new FallenPayload(list);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: buy a ware from the Hollowling the player barters with. */
	public record BarterTradePayload(int young, int ware) implements CustomPacketPayload {
		public static final Type<BarterTradePayload> TYPE = new Type<>(ExtremeDimensions.id("barter_trade"));
		public static final StreamCodec<FriendlyByteBuf, BarterTradePayload> CODEC = StreamCodec.ofMember(BarterTradePayload::write, BarterTradePayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(young);
			buf.writeVarInt(ware);
		}

		private static BarterTradePayload read(FriendlyByteBuf buf) {
			return new BarterTradePayload(buf.readVarInt(), buf.readVarInt());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: the tapestry on the Weaver's Loom (see Tapestry.data); opens its screen or refreshes it. */
	public record TapestryPayload(String data) implements CustomPacketPayload {
		public static final Type<TapestryPayload> TYPE = new Type<>(ExtremeDimensions.id("tapestry"));
		public static final StreamCodec<FriendlyByteBuf, TapestryPayload> CODEC = StreamCodec.ofMember(TapestryPayload::write, TapestryPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(data);
		}

		private static TapestryPayload read(FriendlyByteBuf buf) {
			return new TapestryPayload(buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: take up a new thread (New Thread+) at the finished tapestry. */
	public record NewThreadPayload() implements CustomPacketPayload {
		public static final Type<NewThreadPayload> TYPE = new Type<>(ExtremeDimensions.id("new_thread"));
		public static final StreamCodec<FriendlyByteBuf, NewThreadPayload> CODEC = StreamCodec.ofMember(NewThreadPayload::write, NewThreadPayload::read);

		private void write(FriendlyByteBuf buf) {
		}

		private static NewThreadPayload read(FriendlyByteBuf buf) {
			return new NewThreadPayload();
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: the Unweaving at the Weaver's Loom (see Unweaving.data); opens its screen or refreshes it. */
	public record UnweavePayload(String data) implements CustomPacketPayload {
		public static final Type<UnweavePayload> TYPE = new Type<>(ExtremeDimensions.id("unweave"));
		public static final StreamCodec<FriendlyByteBuf, UnweavePayload> CODEC = StreamCodec.ofMember(UnweavePayload::write, UnweavePayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(data);
		}

		private static UnweavePayload read(FriendlyByteBuf buf) {
			return new UnweavePayload(buf.readUtf());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: show the Unweaving ("") or unweave a set (its name) at the loom. */
	public record UnweaveRequestPayload(String set) implements CustomPacketPayload {
		public static final Type<UnweaveRequestPayload> TYPE = new Type<>(ExtremeDimensions.id("unweave_request"));
		public static final StreamCodec<FriendlyByteBuf, UnweaveRequestPayload> CODEC = StreamCodec.ofMember(UnweaveRequestPayload::write,
				UnweaveRequestPayload::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(set, 32);
		}

		private static UnweaveRequestPayload read(FriendlyByteBuf buf) {
			return new UnweaveRequestPayload(buf.readUtf(32));
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
