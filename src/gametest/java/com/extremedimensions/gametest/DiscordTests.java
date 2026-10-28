package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremedimensions.compat.DiscordWebhook;
import com.extremedimensions.compat.StatsExport;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.tapestry.Tapestry;
import com.extremedimensions.tapestry.Tapestry.Panel;
import com.extremedimensions.tapestry.Weaving;
import com.extremedimensions.trial.Trial;
import com.extremedimensions.trial.TrialRun;
import com.extremedimensions.trial.TrialService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The Discord webhook: each great moment's message as the game words it, the JSON Discord is sent (within its limit,
 * pinging nobody), nothing at all without a webhook, a webhook that is no web address never throwing on the server's
 * thread, and the moments posted from where the game announces them. Nothing here goes over the network: the messages
 * are caught on their way out ({@code DiscordWebhook.testSink}).
 */
public class DiscordTests {
	private static final String HOOK = "https://discord.com/api/webhooks/0/extremedimensions-test";

	/** Sets the webhook and catches what it would be sent, for one test; {@link #close} puts both back. */
	private static List<String> catchPosts(String webhook) {
		List<String> caught = new ArrayList<>();
		DiscordWebhook.testSink = caught::add;
		Config.DISCORD_WEBHOOK = webhook;
		return caught;
	}

	private static void close(String webhook) {
		Config.DISCORD_WEBHOOK = webhook;
		DiscordWebhook.testSink = null;
	}

	private static String content(String body) {
		return JsonParser.parseString(body).getAsJsonObject().get("content").getAsString();
	}

	/** Every moment once, for this player. */
	private static void everyMoment(ServerPlayer p) {
		TrialRun run = new TrialRun();
		run.week = TrialService.week();
		run.kind = Trial.Kind.HUNT.id;
		run.twist = Trial.Twist.CALM.id;
		run.score = 40;
		for (StatsExport.Elder e : StatsExport.Elder.values()) {
			DiscordWebhook.elder(e, List.of(p));
		}
		DiscordWebhook.starvedOne(List.of(p));
		DiscordWebhook.ending(true, p);
		DiscordWebhook.ending(false, p);
		DiscordWebhook.panel(Panel.ROOTS, p);
		DiscordWebhook.tapestry(List.of(p));
		DiscordWebhook.gold(p, run);
	}

	@GameTest
	public void eachMomentReadsAsTheGameSaysIt(GameTestHelper helper) {
		List<String> three = List.of("Steve", "Alex", "Bob");
		check(helper, DiscordWebhook.elderText("The Ferryman", three).equals("**The Ferryman** has been defeated by Steve, Alex and Bob."),
				"an Elder's fall reads: %s", DiscordWebhook.elderText("The Ferryman", three));
		check(helper, DiscordWebhook.elderText("The Matriarch", List.of()).equals("**The Matriarch** has been defeated."),
				"an Elder's fall with nobody near reads: %s", DiscordWebhook.elderText("The Matriarch", List.of()));
		String starved = DiscordWebhook.starvedText(List.of("Steve", "Alex"));
		check(helper, starved.startsWith("**The Starved One** has been defeated at the Heart of the Seam by Steve and Alex. The Mending Thread and the Severing Shears"),
				"the Starved One's fall reads: %s", starved);
		check(helper, DiscordWebhook.endingText(true, "Steve").equals("**The world is Mended.** Steve mended the first wound with the Mending Thread."),
				"a mended world reads: %s", DiscordWebhook.endingText(true, "Steve"));
		check(helper, DiscordWebhook.endingText(false, "Alex").equals("**The world is Severed.** Alex severed the Tether from the world with the Severing Shears."),
				"a severed world reads: %s", DiscordWebhook.endingText(false, "Alex"));
		check(helper, DiscordWebhook.panelText("Roots", "Steve", 3, 6).equals("Steve wove the **Roots** panel into the tapestry (3/6)."),
				"a panel reads: %s", DiscordWebhook.panelText("Roots", "Steve", 3, 6));
		check(helper, DiscordWebhook.panelText("Heart", "Steve", 6, 6).equals("Steve wove the **Heart** panel into the tapestry (6/6)."
				+ " The last panel is set. The wounds converge on the loom: hold it until the weave is done!"), "the last panel reads: %s",
				DiscordWebhook.panelText("Heart", "Steve", 6, 6));
		check(helper, DiscordWebhook.tapestryText(List.of("Steve", "Alex")).equals("**The weave holds.** The tapestry is finished, held by Steve and Alex, and the story is yours."),
				"the finished tapestry reads: %s", DiscordWebhook.tapestryText(List.of("Steve", "Alex")));
		check(helper, DiscordWebhook.goldText("Steve", "Hunter · Still Seam", 42).equals("Steve won **Gold** in this week's Rift Trial (Hunter · Still Seam) with a score of 42."),
				"gold reads: %s", DiscordWebhook.goldText("Steve", "Hunter · Still Seam", 42));
		// The tapestry's own names, and the Elders' as the game shows them
		Set<String> panels = new HashSet<>();
		for (Panel panel : Panel.values()) {
			panels.add(DiscordWebhook.panelName(panel));
		}
		check(helper, panels.equals(Set.of("Roots", "Toll", "Unravelling", "Chain", "Knot", "Heart")), "the panels are named %s", panels);
		check(helper, StatsExport.Elder.WEAVER.title.equals("The Last Weaver") && StatsExport.Elder.KNOT_MOTHER.title.equals("The Knot Mother"),
				"the Elders are misnamed");

		// Names: each once, markdown kept out, and a crowd counted rather than listed
		check(helper, DiscordWebhook.list(List.of("dark_knight", "Steve", "dark_knight")).equals("dark\\_knight and Steve"), "names read %s",
				DiscordWebhook.list(List.of("dark_knight", "Steve", "dark_knight")));
		List<String> crowd = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			crowd.add("player" + i);
		}
		String many = DiscordWebhook.elderText("The Ferryman", crowd);
		check(helper, many.endsWith("player13, player14 and 25 others.") && !many.contains("player15"), "a crowd reads: %s", many);

		// What Discord is sent: the message, within its limit, and no mention may ping
		JsonObject body = JsonParser.parseString(DiscordWebhook.body("@everyone @here look")).getAsJsonObject();
		check(helper, "@everyone @here look".equals(body.get("content").getAsString()), "the message became %s", body.get("content"));
		check(helper, body.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty(), "mentions may ping: %s", body.get("allowed_mentions"));
		String huge = content(DiscordWebhook.body("x".repeat(5000)));
		check(helper, huge.length() == DiscordWebhook.LIMIT && huge.endsWith("…"), "a long message is %s characters", huge.length());
		helper.succeed();
	}

	@GameTest
	public void nothingIsSentWithoutAWebhook(GameTestHelper helper) {
		String before = Config.DISCORD_WEBHOOK;
		ServerPlayer p = TestPlayers.survivor(helper, "discord-quiet");
		try {
			List<String> caught = catchPosts("");
			everyMoment(p);
			check(helper, !DiscordWebhook.on() && DiscordWebhook.post("anything") == null, "a post went out without a webhook");
			check(helper, caught.isEmpty(), "%s messages went out without a webhook", caught.size());
			// With one, every moment is a message within the limit that pings nobody
			Config.DISCORD_WEBHOOK = HOOK;
			everyMoment(p);
			check(helper, caught.size() == 10, "%s messages for 10 moments", caught.size());
			for (String body : caught) {
				JsonObject o = JsonParser.parseString(body).getAsJsonObject();
				check(helper, o.get("content").getAsString().length() <= DiscordWebhook.LIMIT && o.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty(),
						"a message is too long or may ping: %s", body);
			}
			check(helper, content(caught.get(9)).equals("discord-quiet won **Gold** in this week's Rift Trial (Hunter · Still Seam) with a score of 40."),
					"gold reads: %s", content(caught.get(9)));
			helper.succeed();
		} finally {
			close(before);
		}
	}

	@GameTest
	public void aBadWebhookNeverThrows(GameTestHelper helper) {
		String before = Config.DISCORD_WEBHOOK;
		ServerPlayer p = TestPlayers.survivor(helper, "discord-bad");
		try {
			List<String> caught = catchPosts("");
			for (String bad : List.of("not a webhook", "discord.com/api/webhooks/1/x", "ftp://discord.com/api/webhooks/1/x", "https://", "http://exa mple.com/x",
					"https://[broken/x", "mailto:someone@example.com")) {
				Config.DISCORD_WEBHOOK = bad;
				Object sent;
				try {
					everyMoment(p);
					sent = DiscordWebhook.post("anything");
				} catch (RuntimeException e) {
					throw helper.assertionException("the webhook \"%s\" threw on the server's thread: %s", bad, e);
				}
				check(helper, DiscordWebhook.uri(bad) == null && sent == null, "\"%s\" was taken for a webhook", bad);
			}
			check(helper, caught.isEmpty(), "%s messages went to a webhook that is no web address", caught.size());
			check(helper, DiscordWebhook.uri(HOOK) != null, "a Discord webhook's URL is not taken");
			helper.succeed();
		} finally {
			close(before);
		}
	}

	@GameTest
	public void theMomentsArePostedWhereTheGameAnnouncesThem(GameTestHelper helper) {
		String before = Config.DISCORD_WEBHOOK;
		String ending = DataStore.ending(), loom = DataStore.loom(), last = DataStore.lastPanel();
		Set<String> woven = new HashSet<>(DataStore.panels());
		long again = DataStore.weaveAgainAt();
		boolean done = DataStore.tapestryDone();
		ServerPlayer p = TestPlayers.survivor(helper, "discord_weaver");
		PlayerData d = DataStore.get(p.getUUID());
		try {
			List<String> caught = catchPosts(HOOK);
			// The ending, chosen with the Mending Thread
			DataStore.setEnding("");
			MendingService.useEnding(p, new ItemStack(ModItems.MENDING_THREAD));
			check(helper, caught.size() == 1 && content(caught.get(0)).equals("**The world is Mended.** discord\\_weaver mended the first wound with the Mending Thread."),
					"the ending posted %s", caught);

			// The sixth panel, and the Weaving held
			p.getInventory().clearContent();
			DataStore.setLoom("");
			DataStore.setTapestryDone(false);
			DataStore.setWeaveAgainAt(0);
			BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
			helper.getLevel().setBlockAndUpdate(at, ModBlocks.WEAVERS_LOOM.defaultBlockState());
			p.teleportTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 2.5);
			DataStore.panels().clear();
			for (Panel panel : Panel.values()) {
				if (panel != Panel.HEART) {
					DataStore.panels().add(panel.id);
				}
			}
			ItemStack heart = new ItemStack(ModItems.THREAD_OF_THE_HEART);
			p.setItemInHand(InteractionHand.MAIN_HAND, heart);
			Tapestry.use(p, at, heart);
			check(helper, Weaving.active() && caught.size() == 2 && content(caught.get(1)).startsWith("discord\\_weaver wove the **Heart** panel into the tapestry (6/6). The last panel is set."),
					"the sixth panel posted %s", caught);
			Weaving.finishNow(true);
			// (players of other tests nearby may be counted as holding it too)
			check(helper, caught.size() == 3 && content(caught.get(2)).startsWith("**The weave holds.** The tapestry is finished, held by ")
					&& content(caught.get(2)).contains("discord\\_weaver") && content(caught.get(2)).endsWith(", and the story is yours."),
					"the held Weaving posted %s", caught);

			// Gold in this week's trial, the first time this week only
			int gold = Trial.Kind.HUNT.thresholds()[2];
			for (int i = 0; i < 2; i++) {
				TrialRun run = new TrialRun();
				run.week = TrialService.week();
				run.kind = Trial.Kind.HUNT.id;
				run.twist = Trial.Twist.CALM.id;
				run.score = gold;
				d.trial = run;
				TrialService.finish(p, d);
			}
			check(helper, caught.size() == 4 && content(caught.get(3)).equals("discord\\_weaver won **Gold** in this week's Rift Trial (Hunter · Still Seam) with a score of " + gold + "."),
					"gold posted %s", caught);
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(4)).forEach(ItemEntity::discard);
			helper.succeed();
		} finally {
			close(before);
			Weaving.clear();
			d.trial = null;
			DataStore.setEnding(ending);
			DataStore.panels().clear();
			DataStore.panels().addAll(woven);
			DataStore.setLoom(loom);
			DataStore.setLastPanel(last);
			DataStore.setWeaveAgainAt(again);
			DataStore.setTapestryDone(done);
		}
	}
}
