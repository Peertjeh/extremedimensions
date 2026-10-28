package net.battlehub.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class DiscordTest {
	@Test
	void namesShowAsTyped() {
		assertEquals("Bot\\_Nova", Discord.escape("Bot_Nova"));
		assertEquals("\\*\\*x\\*\\* \\~a\\~ \\`b\\` \\|c\\| \\> d \\\\", Discord.escape("**x** ~a~ `b` |c| > d \\"));
		assertEquals("Steve", Discord.escape("Steve"));
	}

	@Test
	void payloadPingsNobodyAndFitsDiscord() {
		JsonObject o = JsonParser.parseString(Discord.payload("@everyone won")).getAsJsonObject();
		assertEquals("@everyone won", o.get("content").getAsString());
		assertEquals("BattleHub", o.get("username").getAsString());
		assertTrue(o.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty());
		String longText = "x".repeat(3000);
		assertTrue(JsonParser.parseString(Discord.payload(longText)).getAsJsonObject().get("content").getAsString().length() <= 2000);
	}
}
