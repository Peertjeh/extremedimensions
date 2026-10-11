package com.extremedimensions;

/**
 * De pagina's van het Tether Journal. Elke pagina wordt ontgrendeld door iets wat de speler doet.
 * De teksten zijn bewust vaag: de speler moet zelf de puzzel leggen.
 * Geschreven door een naamloze Wanderer, lang geleden.
 */
public enum JournalEntry {
	TETHERED("tethered", "I. Tethered",
			"If you can read this, you are still held. Good. Most who find this book are not. "
			+ "There is a cord older than the mountains, woven through every living thing in this world. "
			+ "We called it the Tether. It does not speak. It does not need to. The Tether knows."),
	ANCHOR("anchor", "II. The Anchor",
			"The lodestone drinks what you give it and turns warm, like a hand. "
			+ "Coal and blaze for the burning place. Pearls and echoes for the pale one. "
			+ "Set it close to the wound, never far. An anchor too far from the wound is only a stone, "
			+ "and stones do not keep anyone alive."),
	WOUND("wound", "III. Wounds",
			"A portal is not a door. Doors are built. Portals are torn. "
			+ "Stand close and you will feel it pulling at the edges of you, the way a wound pulls at stitches. "
			+ "When the path between anchor and wound is blocked, the anchor bleeds into nothing. "
			+ "Keep the way open."),
	EMBER_DEEP("ember_deep", "IV. The Ember Deep",
			"Below the fire there is a heat that is not fire. "
			+ "The pigmen trade in gold and will not tell you why. I believe they are paying someone. "
			+ "Every breath down there costs warmth, and the anchor pays it for you. "
			+ "Count the seconds. They are counting too."),
	PALE_VOID("pale_void", "V. The Pale Void",
			"There is no ground in the pale place, only islands that pretend. "
			+ "The tall ones watch you with too much interest. They do not attack because you look at them. "
			+ "They attack because you remind them. Of what, I did not want to know."),
	STARVED("starved", "VI. The Starved",
			"They are not demons. They were never demons. "
			+ "They are what is left when a soul stays too long where it was not anchored. "
			+ "Hungry for warmth they no longer make. "
			+ "When your anchor runs dry, they do not kill you. They drink. Killing is just what is left."),
	ENDED("ended", "VII. Ended",
			"I lost a companion beyond the wound today. Not to the hunger. To an ordinary blade. "
			+ "His anchor went cold the moment he fell, as if it had only ever been holding him. "
			+ "The Tether pulled him home empty. He woke in the grass and did not remember my name for an hour."),
	SCARS("scars", "VIII. What They Keep",
			"Every return costs something. A little strength. A little warmth. A little of your name. "
			+ "The marks fade if you keep walking, keep breathing, keep moving where they can see you. "
			+ "Stand still and they stay. I think the Tether wants us to earn our wholeness back."),
	FRAGMENT("fragment", "IX. A Fragment",
			"It fell from one of them, glowing the colour of soul fire. It hums against the anchor. "
			+ "It is a piece of the cord itself, frayed loose. "
			+ "Offered to an anchor, it mends. What it mends, and with whose soul, I prefer not to ask."),
	MENDING("mending", "X. Mending",
			"I gave the fragment to the anchor and a mark on my soul closed like an eye. "
			+ "Somewhere, I am sure of it, someone else's opened. The Tether does not create. It only moves things."),
	WEAVERS("weavers", "XI. The Weavers",
			"Deep under everything there are cities of black stone, built by hands that did not need light. "
			+ "In their center stands a great frame, and nothing in it. "
			+ "They did not build a portal. They tried to close one. "
			+ "They used the only thread strong enough. Themselves."),
	UNANCHORED("unanchored", "XII. The Unanchored",
			"It dropped a pearl when it died, and in the pearl I saw a field of grass. "
			+ "Endermen carry blocks. They cannot bear water. They scream when you meet their eyes. "
			+ "I used to think they were thieves. Now I think they are trying to build something. "
			+ "An anchor. A way home."),
	HABIT("habit", "XIII. Habit",
			"Five times through the wound and back. It gets easier, and that is the danger. "
			+ "The Tether held. It always holds. But I have started to feel it tighten when I leave, "
			+ "like it is reminding me who it belongs to."),
	SILENCE("silence", "XIV. Silence",
			"I asked the Tether what it wants. It did not answer. It never answers. "
			+ "But my anchor flared, and for a moment I heard a thousand voices hold their breath. "
			+ "Keep your anchors full. Not for you. For them.");

	public final String id;
	public final String title;
	public final String text;

	JournalEntry(String id, String title, String text) {
		this.id = id;
		this.title = title;
		this.text = text;
	}

	public static JournalEntry byId(String id) {
		for (JournalEntry e : values()) {
			if (e.id.equals(id)) {
				return e;
			}
		}
		return null;
	}
}
