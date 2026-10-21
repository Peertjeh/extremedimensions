# Extreme Dimensions: master expansion brief

The brief as it was given (only set in markdown), kept for reference. `docs/roadmap-3.0.md` is the plan built from it: it places the brief's
proposed releases (2.27 to 2.30, numbers since taken) on the road from 2.33, and records the decisions.

---

EXTREME DIMENSIONS — MASTER EXPANSION PROMPT
From the current release to version 3.0
Lore-driven content, new enchantments, items, crafting, world events, and long-term progression

You are the lead game designer, narrative director, senior Fabric mod developer, gameplay systems architect, and release engineer for Extreme Dimensions.
Your responsibility is to expand an existing, substantial Minecraft mod into a coherent, polished, lore-driven experience culminating in a meaningful version 3.0 release.
You are not starting a new mod.
You are continuing an existing project with established mechanics, narrative, progression, entities, dimensions, items, recipes, curses, relics, bosses, structures, events, user interfaces, and multiplayer behavior.
Your job is to understand what already exists, identify the strongest opportunities for expansion, design a connected progression system, and implement it without damaging the existing game.
Do not produce a generic list of fantasy items.
Do not add features merely because they sound impressive.
Every significant addition must belong to the world, solve a gameplay problem, introduce a meaningful decision, deepen the lore, or create a memorable experience.
The result should feel as though it was always part of Extreme Dimensions.

## 1. Establish the source of truth

Before proposing changes, inspect the actual repository.
Find and read the complete walkthrough titled: "Extreme Dimensions: de complete walkthrough".
It describes the existing narrative, progression, mechanics, items, recipes, curses, relics, dimensions, bosses, structures, events, and endings.
Locate the document in the repository or the accessible project files. If it is unavailable, explicitly identify that limitation and ask for the document while continuing with the source code that is available.
Do not pretend to have read an inaccessible document.
Use the walkthrough as the primary narrative and gameplay reference, but use the actual source code as the authority for implementation details.
If the walkthrough and code disagree, document the discrepancy rather than silently inventing a reconciliation.
In particular, inspect the actual release version, Minecraft version, Java version, Fabric API version, dependencies, registration patterns, resource structure, and build configuration.
Do not assume that version numbers or filenames in the walkthrough necessarily match the current repository.

Create a source-of-truth inventory containing:

- Existing features and their implementation locations.
- Existing item, block, entity, effect, enchantment, and recipe registrations.
- Existing dimensions, structures, loot tables, and world-generation features.
- Existing journal pages and narrative triggers.
- Existing progression gates and boss prerequisites.
- Existing curse, relic, Tether, anchor, and Seam systems.
- Existing world events and their scheduling mechanisms.
- Existing client-server boundaries and multiplayer synchronization.
- Existing translation keys and supported languages.
- Existing configuration, gamerules, commands, and persistence systems.
- Existing tests and available verification procedures.

Do not implement a feature before checking whether an equivalent system already exists.
Do not replace functioning systems simply because you would have designed them differently from scratch.

## 2. Preserve the narrative identity

The defining principle of Extreme Dimensions is: "Every portal is a wound. Every wound remembers."
The world is not merely a collection of dangerous dimensions.
It is a world where travel has a cost, memories survive in objects and places, ancient beings observe or manipulate events, and the player gradually discovers the consequences of opening and binding wounds between worlds.

The current narrative includes:

- The Tether, anchors, timed expeditions, and Dimensional Collapse.
- The Wanderer's journal and its incomplete, unreliable account.
- The Lurker, Hollowlings, Keeper, Matriarch, and the memory of the Tribe.
- Hoards, stolen possessions, soul light, reputation, and Hollowling companionship.
- The Seam, Seam Silk, Hush Stone, Veil Rays, the Thinned, and the Fray.
- The Rift Trials, Tether upgrades, relics, pacts, and curses.
- The Ender Dragon and the breaking of the Seal.
- Wound Storms and the awakening of the Starved One.
- The Matriarch, Ferryman, and Last Weaver as distinct Ancient beings.
- The Mending Thread and Severing Shears.
- The Mended and Severed world states.

These elements are established narrative canon.
Treat them as interconnected systems, not isolated content categories.
New content should deepen existing mysteries, provide new interpretations, or reveal consequences that logically follow from known events.
Do not casually rewrite established lore.
Do not arbitrarily identify the Wanderer, explain every mystery, or declare a new ultimate villain without evidence from the existing narrative and a compelling design reason.
Mystery, uncertainty, environmental storytelling, contradictory testimony, and incomplete historical records are essential parts of the atmosphere.
A new discovery can change the player's understanding without invalidating earlier chapters.

## 3. Define the 2.27–3.0 expansion strategy

Design a version roadmap that gradually expands the mod rather than introducing every new system simultaneously.
Use the following proposed themes as starting points, not immutable requirements.

**Version 2.27 — The Forgotten Relics.** Focus on exploration, hidden history, old structures, unusual relic interactions, journal discoveries, and overlooked existing mechanics. Possible deliverables: new discoveries in existing structures; rare interactions with the Tribe; additional journal pages; previously inaccessible recipes or crafting clues; environmental storytelling; additional uses for existing materials; optional challenges with meaningful rewards.

**Version 2.28 — The Art of Binding.** Focus on crafting, enchantments, Tether-related equipment, specialized tools, and new strategic choices. Possible deliverables: new enchantments integrated with vanilla enchantment rules; specialized items with distinct uses; new recipes using established mod materials; new material combinations; alternative approaches to existing expedition challenges; trade-offs between power, safety, mobility, and resource consumption.

**Version 2.29 — The Wounds Remember.** Focus on dynamic world events and consequences. Possible deliverables: new Wound Storm variants; temporary dimensional anomalies; events influenced by player actions and world state; unusual Lurker encounters; temporary anchor disturbances; environmental clues that lead to optional discoveries; event-specific loot, journal entries, and achievements.

**Version 2.30 — The First Weaver.** Focus on a coherent narrative investigation. Possible deliverables: a multi-stage discovery quest; new structures, chambers, or hidden locations; connected clues across existing dimensions; new crafting unlocks tied to discoveries; optional encounters or a new specialized enemy; a meaningful conclusion to the new chapter without replacing the existing final boss.

**Version 3.0 — The Unfinished World.** Treat 3.0 as a major expansion, not simply the next numbered patch. The release should bring together the best systems developed in the preceding versions. Potential pillars: a substantial new narrative chapter; a new progression loop that complements existing expeditions; a distinctive new structure or region; new items, enchantments, recipes, and event variants; deeper consequences for existing world states; additional uses for existing relics and materials; a meaningful new challenge, encounter, or optional boss; new journal entries, advancements, and environmental storytelling; a complete balance, compatibility, migration, and regression-testing pass.

Do not automatically create a new dimension or a new boss merely to make 3.0 feel large.
A new dimension, boss, or major progression system must be justified by the narrative, implementation cost, gameplay value, and performance implications.
If a smaller expansion offers more depth and polish, prefer it over a larger but incoherent one.
For each version, define its core theme, player fantasy, progression entry point, major features, dependencies, narrative revelations, testing requirements, and definition of done.

## 4. Build a connected lore and discovery system

Design new content so that the player discovers it naturally through gameplay.
Use multiple complementary discovery methods: journal fragments; environmental details; structure-specific clues; rare mob behavior; loot with unusual descriptions; new advancements; crafting hints; existing entity interactions; changes to world events; optional discoveries after major milestones.
Avoid putting all new lore into a sequence of ordinary written pages.
Let the environment communicate information.
A damaged altar, an abandoned loom, an unusual arrangement of soul lanterns, or a relic with an unexpected interaction can reveal something that text alone cannot.

**Journal integration.** Inspect the current journal implementation and its progression system. Add new pages only when they provide meaningful information. Each page must have: a unique identifier; a clear discovery trigger; English and Dutch translations where those languages are supported; narrative text consistent with the Wanderer's voice; appropriate spoiler sensitivity; correct multiplayer and persistence behavior; a meaningful relationship to the wider narrative. Avoid duplicate discoveries and repeated notifications. New journal entries must not break existing page order, completion tracking, or saved player progress.

**Narrative continuity.** For every major addition, document:

1. What the player initially observes.
2. What the player can reasonably infer.
3. What remains uncertain.
4. What gameplay action reveals the next clue.
5. How the discovery changes the player's understanding.
6. How it relates to existing canon.
7. Whether it behaves differently in a Mended or Severed world.

Do not explain mysteries simply because the author knows the answer.
The player's interpretation should emerge from the evidence.

## 5. Design new items as gameplay systems

Create a complete item proposal catalog before implementing individual items.
Each proposed item must have: name and internal identifier; narrative purpose; acquisition method; crafting recipe, if appropriate; intended progression stage; primary gameplay function; secondary interaction; limitations or costs; compatibility with existing systems; multiplayer behavior; balance risks; visual and audio requirements; translation requirements; testing requirements.
Favor items that interact with existing mechanics rather than items that only add raw damage or protection.

Potential design directions include:

- **A. Tether technology.** Explore items that modify how players prepare, navigate, or recover from expeditions. Can players prepare for an expedition in a new way? Can an item help interpret a dangerous dimensional anomaly? Can the player temporarily stabilize an unstable anchor? Can a tool reveal information about a wound without removing its danger? Can the player make a meaningful trade-off between tether efficiency and safety?
- **B. Seam technology.** Explore alternative uses for Seam Silk, Hush Stone, Veil Wings, Tether Fragments, and other existing materials. New recipes should give these materials meaningful roles without making earlier upgrades obsolete.
- **C. Tribe-related items.** Consider items that interact with reputation, soul light, hoards, Hollowling companionship, or the behavior of ancient members of the Tribe. Respect the established distinction between the Tribe's members. Do not turn every hostile creature into a conventional trading NPC.
- **D. Relics and cursed artifacts.** New relic-like items must preserve the design philosophy: "Each gives, each takes." Their drawbacks should be interesting, understandable, and relevant to the benefits. Do not introduce unlimited stacking of powerful effects. Before adding new relics, evaluate whether existing relics need improved utility, clearer drawbacks, or additional situational value.
- **E. Rare discoveries.** Some items may be non-craftable, unique, or obtainable only through an optional challenge. A rare item must not become mandatory for basic progression unless its acquisition is appropriately integrated into the main route.

## 6. Design a professional enchantment system

New enchantments must fit Minecraft's existing enchantment conventions and the mod's thematic identity.
Do not add enchantments just to populate an item list.
For each proposed enchantment, specify: name and registry identifier; applicable equipment; maximum level; effect at each level; acquisition method; enchanting-table eligibility; loot and trading eligibility, if appropriate; conflicts with vanilla or modded enchantments; whether it is treasure-only, mutually exclusive, or otherwise restricted; balance implications; translation keys; tooltip and visual presentation; unit and integration tests.
Investigate the actual Minecraft version's enchantment API before choosing an implementation.
Do not assume older Fabric tutorials are compatible with the project's target version.

Consider three broad design categories:

1. Expedition enchantments: improve preparation, mobility, resource management, or navigation.
2. Combat enchantments: create specialized advantages against certain threats without trivializing bosses.
3. Dimensional enchantments: interact with the Tether, curses, anchors, the Seam, or specific environmental conditions.

Examples for evaluation, not mandatory implementation: an enchantment that improves the detection of dimensional disturbances; an enchantment that rewards successful expeditions but increases risk if the player overextends; an enchantment that interacts with hostile entities from a specific dimensional origin; an enchantment that provides a limited defensive effect at the cost of a meaningful resource; an enchantment that changes a tool's interaction with Seam-related materials.
These are conceptual directions. Replace any idea that cannot be balanced or implemented cleanly.
Never introduce an enchantment that makes Dimensional Collapse, the curse system, or the central expedition loop irrelevant.
Avoid duplicating the effects of existing enchantments unless the new version creates a clearly distinct gameplay choice.

## 7. Crafting and recipe design

Every new recipe must have a clear place in progression.
For each recipe, document: required ingredients; recipe type; unlock conditions, if any; intended acquisition timing; resource economy; alternative acquisition routes; potential farming exploits; compatibility with recipe books and advancements; translation and user-facing guidance.
Use shaped, shapeless, smithing, stonecutting, or custom recipes according to the actual design.
Do not add a custom crafting interface when a vanilla recipe type provides a better player experience.
Avoid recipes that require materials obtainable only after the item they craft is already needed.
Avoid excessive ingredient inflation and arbitrary recipe complexity.
Existing materials should gain new uses without losing their established progression purpose.
Consider the complete economy of Seam Silk, Tether Fragments, Echo Shards, Veil Wings, and other relevant resources.
Before introducing a new recipe, evaluate how it affects: early-game accessibility; mid-game progression; late-game optimization; multiplayer resource sharing; existing loot tables; farming and automation; Mended and Severed worlds.

## 8. Design a world event framework

Create new events that reinforce the narrative and provide interesting gameplay.
Inspect existing Wound Storms, Lurker encounters, anchor behavior, Seam transitions, and boss events before designing a new event system.
Prefer extending existing architecture where practical.
Each event must define: trigger conditions; eligible dimensions and world states; start and end conditions; frequency and cooldown; warning signals; gameplay effects; entity behavior; rewards and consequences; failure and recovery behavior; multiplayer synchronization; persistence across logout, death, and server restart where relevant; configuration options; debugging and testing hooks.

Potential event concepts:

- **Echoes of Collapse.** A temporary anomaly preserves traces of a previous dimensional failure. The player may investigate, recover a clue, or leave before the anomaly becomes dangerous.
- **Unstable Anchors.** A dimensional disturbance temporarily changes an anchor's behavior, creating a preparation or repair challenge.
- **The Tribe's Offering.** A Lurker or Hollowling leaves an unusual object under specific conditions. The discovery provides a clue, a trade opportunity, or a new interaction.
- **Frayed Seam.** A limited region of the Seam temporarily changes environmental conditions, movement, visibility, enemy behavior, or available rewards.
- **Wound Convergence.** A rare event links phenomena from two dimensions for a limited time, creating an unusual encounter or exploration opportunity.

These are starting concepts, not requirements.
Do not activate all events randomly or simultaneously.
The player should receive recognizable warnings, understand the immediate stakes, and have meaningful options.
Use deterministic conditions for testing and configurable random scheduling for live gameplay.
Events must respect the current world's narrative state.

## 9. Respect Mended and Severed world states

The final choice is a permanent, world-wide decision.
Treat it as a major design constraint.
New features must be reviewed against both states.
For each feature, ask: Does it work before the ending? Does it work in a Mended world? Does it work in a Severed world? Should its behavior change after the ending? Can it be discovered after the player has already completed the story? Does it undermine the consequences of either ending? Can multiplayer players encounter inconsistent world states?
The Mended world represents a safer, more peaceful relationship with the wounds.
The Severed world represents freedom from the normal expedition timer at the cost of greater consequences, ongoing danger, and continued instability.
Do not treat these states as cosmetic difficulty settings.
They should meaningfully influence the world, but new content must not arbitrarily punish players for choosing one ending.
Where appropriate, create different interpretations, alternate rewards, or different interactions rather than completely separate implementations.
Do not make the new 3.0 story require the player to reverse an irreversible ending.

## 10. Progression, advancements, and player guidance

Every significant feature must have an appropriate discovery path.
Use a combination of: existing journal pages; advancements; recipe unlocks; environmental hints; item tooltips; existing sound and visual language; optional discoveries; existing progression milestones.
Avoid turning the game into a checklist of notifications.
The player should feel rewarded for understanding the world.
For every new major feature, define its intended entry point: early-game discovery; first Nether expedition; first End expedition; first encounter with the Tribe; first visit to the Seam; after breaking the Seal; after the Starved One; after choosing an ending; optional late-game content.
Use the appropriate entry point for the feature rather than automatically locking everything behind the final boss.
Ensure new journal pages and advancements have stable identifiers and do not corrupt existing saves.

## 11. Art, audio, and atmosphere

Inspect the existing resource assets and visual conventions.
New assets must belong to the same game.
Prefer the established Minecraft pixel-art language, deliberate silhouettes, restrained palettes, and recognizable material identities.
The visual distinction between the Overworld, Nether, End, Seam, and Fray must remain clear.
For each major feature, define the necessary assets: item textures; block textures; entity textures and models; particle effects; sounds; music, where justified; structure palettes; loot presentation; journal illustrations, if supported; UI icons and tooltips.
Do not create assets merely to increase the asset count.
A new item must be recognizable in the inventory and readable at Minecraft's native texture scale.
A major event should be communicated through a coherent combination of sound, particles, environmental changes, and gameplay cues.
Reuse existing effects and assets where that produces a stronger result.

## 12. Balance and anti-exploit design

Before implementing each major feature, analyze its potential exploits.
Pay special attention to: automated farming; repeated event triggering; multiplayer reward duplication; disconnecting to avoid consequences; death and respawn interactions; repeated boss summons; inventory manipulation; unloaded chunks; world-state transitions; item duplication; persistent entity data; invalid recipe or loot conditions.
Use server-authoritative validation for gameplay-critical actions.
Never rely solely on client-side checks for item rewards, progression unlocks, or world modifications.
New content must preserve the central tension between preparation, risk, exploration, and reward.
A player should become more capable by learning and making strategic choices, not by acquiring one item that invalidates every challenge.
Use measurable balance targets where possible.
If a feature changes expedition duration, curse recovery, resource generation, boss survivability, or event frequency, evaluate its effect on the complete progression system.

## 13. Multiplayer, saves, and compatibility

Extreme Dimensions supports a world with shared progression and player-specific state.
Maintain that distinction.
Verify whether each new feature belongs to: individual player data; world-level persistent data; entity state; temporary event state; client-only display state.
Use the existing persistence architecture where possible.
Test multiple players interacting with the same event, structure, boss, or reward.
Ensure that world-state changes, event outcomes, and progression rewards are synchronized correctly.
Handle logout, death, dimension changes, server restart, and chunk unloading.
Do not introduce unnecessary migrations or delete existing player data.
If a schema migration is required, implement it explicitly and test both old and new saves.
Avoid breaking changes to established registries, resource identifiers, saved data, commands, or configuration keys.

## 14. Implementation roadmap

Work in stages.

**Stage 0 — Audit.** Read the walkthrough and inspect the code. Produce: a feature inventory; a narrative-canon summary; an existing progression map; a list of current extension points; a list of technical risks; a list of inconsistencies between documentation and code; a compatibility assessment. Do not edit gameplay code until the architecture and existing behavior are understood.

**Stage 1 — Expansion Design.** Develop a prioritized roadmap for 2.27, 2.28, 2.29, 2.30, and 3.0. For every proposed feature, score: narrative value; gameplay value; technical complexity; compatibility risk; balance risk; reuse of existing systems; testing cost; importance to the 3.0 release. Identify dependencies between features. Select a small number of high-value features for each release. Do not fill the roadmap with dozens of low-impact items.

**Stage 2 — Vertical Slice.** Select one coherent, achievable feature that connects several systems. For example, a new discovery could unlock a journal entry, introduce a rare item, reveal a recipe, and create a small optional event. Implement the entire feature end to end. Do not implement only the item registration or journal text. The vertical slice should demonstrate how new content will be added safely to the existing mod. Compile the project and run the relevant tests.

**Stage 3 — Release-by-Release Development.** Implement the roadmap in small, coherent milestones. For every milestone:

1. Inspect relevant code.
2. Explain the planned changes briefly.
3. Implement the feature.
4. Add or update tests.
5. Compile the project.
6. Run targeted tests.
7. Inspect resource and translation completeness.
8. Check existing progression and save compatibility.
9. Update the documentation.
10. Record the actual verification results.

Keep the project buildable wherever practical. Do not implement every feature in a single enormous change.

**Stage 4 — Version 3.0 Integration.** Before declaring 3.0 ready: verify the complete new progression; verify all narrative triggers; verify recipes and enchantments; verify event scheduling and cleanup; verify Mended and Severed behavior; verify multiplayer; verify existing saves; verify all translations; verify registries and resources; verify performance; run regression tests; review release notes and migration requirements. Distinguish implemented, tested, manually verified, and unverified features. Never claim successful testing unless the test was actually executed.

## 15. Development rules

Work directly in the existing repository.
Use the project's actual Minecraft, Java, Fabric, and dependency versions.
Follow existing naming, registration, networking, data-generation, configuration, and persistence conventions.
Do not assume APIs from older versions.
Do not add dependencies without a clear reason.
Do not create duplicate systems when existing ones can be extended.
Do not rewrite unrelated features.
Do not silently change existing balance or narrative canon.
Do not leave core functionality as pseudocode, empty registrations, placeholder implementations, or TODO comments.
Do not invent test results.
Do not use external AI services for ordinary in-game logic.
Do not overengineer features that can be implemented cleanly using the existing architecture.
If a design idea is incompatible with the current architecture, explain why and propose a viable alternative.
If a feature is too large for the current milestone, split it into complete, testable increments.

## 16. Required outputs

After the initial audit, create a concise design package containing:

1. Current architecture and feature inventory.
2. Canonical narrative and progression summary.
3. Proposed 2.27–3.0 roadmap.
4. New item proposal catalog.
5. New enchantment proposal catalog.
6. New recipe proposal catalog.
7. New world-event proposal catalog.
8. Journal and advancement integration plan.
9. Balance and exploit analysis.
10. Technical implementation plan.
11. Prioritized first milestone.
12. Acceptance criteria and tests.

For each proposed item, enchantment, recipe, event, or narrative feature, explicitly identify whether it is: existing functionality to improve; a proposed new feature; a dependency of another feature; deferred until a later version.
Keep the design package concise enough to be actionable but detailed enough to guide implementation.
Store the roadmap and design documents in an appropriate documentation location in the repository, following its existing conventions.
Do not overwrite existing documentation without reviewing it first.

## 17. Your first assignment

Begin now.
First inspect the repository and locate the complete walkthrough.
Read the relevant source material before proposing new lore.
Map the existing narrative and progression, inspect the implementation, and identify the most valuable opportunities for expansion.
Then produce the proposed 2.27–3.0 roadmap.
Choose the first high-value vertical slice and implement it only after the design is grounded in the actual codebase.
Compile the project, execute relevant tests, fix any failures introduced by your changes, and report the actual results.
Do not stop at a design document unless a genuine blocker prevents implementation.
The ultimate goal is to make Extreme Dimensions feel larger, stranger, more coherent, and more rewarding to explore.
Every new item should have a reason to exist.
Every enchantment should create a decision.
Every recipe should fit the progression.
Every event should tell the player something about the world.
Every lore revelation should deepen the mystery rather than merely explain it away.
And every major release should feel like another chapter of the same unforgettable journey.

"Every portal is a wound. Every wound remembers."
