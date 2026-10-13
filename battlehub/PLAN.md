# BattleHub – plan

BattleHub is een server-side Fabric-mod: een PvP- en minigame-hub die volledig via één kompas en vanilla
kist-menu's werkt. Vrienden met een gewone (vanilla) client kunnen joinen.

## Keuzes

| Onderwerp | Keuze | Waarom |
|---|---|---|
| Plek in de repo | Eigen Gradle-project in `battlehub/` | In de root van deze repo staat al de mod *Extreme Dimensions*; die blijft onaangeroerd. |
| Minecraft-versie | **26.3**, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Java 25, Loom 1.18 | Dat is de versie van het bestaande Gradle-project in deze repo, dus de versie van jullie server. |
| Mappings | Geen (officiële Mojang-namen) | Minecraft 26.x wordt niet meer geobfusceerd; de actuele Fabric-template gebruikt de `net.fabricmc.fabric-loom`-plugin zonder mappings. |
| Arena-wereld | Eigen lege dimensie `battlehub:arenas` (datapack in de mod) met **slots** op een raster | Zie hieronder. |
| Menu's | Vanilla `ChestMenu` met een eigen subklasse die elke klik onderschept | Werkt met elke vanilla client, items kunnen er niet uit gehaald worden. |
| Teksten | Eigen vertaalsysteem aan de serverkant (`assets/battlehub/lang/en_us.json` + overrides in `config/battlehub/lang/`) met `&`-kleurcodes | Vanilla clients hebben het taalbestand van de mod niet, dus `Component.translatable` zou kale sleutels tonen. De server vertaalt zelf, per speler in diens clienttaal als er een bestand voor is. |
| Titels | Scoreboard-team per speler met prefix | Eén vanilla mechanisme dat de titel tegelijk in chat, boven het hoofd (naamtag) en in de tablist toont. |
| Dood | `ALLOW_DEATH` wordt afgevangen; de match beslist (eliminatie, respawn, verlies) | Geen death-screen, geen drops. |
| Inventaris-herstel | Snapshot vóór de match, ook als NBT op schijf (`<wereld>/battlehub/restore/`) | Herstel na de match, bij disconnect én na een crash (bij de volgende join). |
| Opslag spelersdata | JSON per UUID in `<wereld>/battlehub/players/`, eerst `.tmp` dan atomisch hernoemen | Zoals gevraagd; opslaan na elke match en bij uitloggen. |
| Spellogica | Puur Java in `net.battlehub.core` (ELO, levels, matchmaking, stats, titels, lobby-countdown, CTF-regels, TNT Run, KOTH, Parkour, border) | Unit-testbaar zonder Minecraft. |

### Arena-wereld: waarom geen Fantasy

Fantasy (NucleoidMC) maakt tijdelijke werelden met mixins diep in `MinecraftServer`. Voor 26.3 kon ik niet
verifiëren dat er een werkende release is (de Nucleoid-maven was vanuit de bouwomgeving niet bereikbaar), en
een kapotte mixin laat de hele server crashen. Daarom het tweede advies uit de opdracht:

* De mod levert een datapack-dimensie `battlehub:arenas`: een lege (void) wereld met vaste daglicht-instelling.
  Die werkt met vanilla clients en heeft geen extra library nodig.
* De dimensie is verdeeld in **slots** (standaard 1024 blokken uit elkaar, ver buiten elkaars kijkafstand).
  Elke match krijgt een vrij slot; zo lopen meerdere matches tegelijk, elk als losse instantie.
* De arena wordt per match in code gebouwd (gespreid over ticks), na afloop wordt het slot leeggemaakt
  (blokken en entities) en pas daarna weer vrijgegeven. Elke match begint dus schoon.

### Dingen die niet precies kunnen zoals beschreven

* **Vertrek-item in de hotbar voor toeschouwers.** In echte spectator mode verbergt de client de hotbar en
  kun je geen items gebruiken. Alternatief: een klikbare **[Leave]**-knop in de chat, een melding in de
  actiebalk en de commando's `/hub` en `/spectate leave`. Toeschouwers blijven daardoor echte spectators
  die niets kunnen beïnvloeden.
* **Krimpende world border bij Last Man Standing.** Een world border geldt voor een hele dimensie, en er
  kunnen meerdere matches tegelijk lopen. Daarom krijgt elke speler in de match een eigen (virtuele) border
  via pakketten; de server rekent zelf uit wie erbuiten staat en geeft daar schade.
* **`./gradlew runServer` testen.** Deze bouwomgeving mag `maven.fabricmc.net` en de Mojang-servers niet
  bereiken. Compileren en testen gebeurt daarom in GitHub Actions (`.github/workflows/battlehub.yml`):
  `./gradlew build` (inclusief unit tests) en daarna een echte Fabric-server met de release-jar die opstart,
  een zelftest draait (nep-spelers die matches spelen) en weer stopt. Dat test de jar die op Modrinth komt.

## Architectuur

```
net.battlehub
├── BattleHub            entrypoint: config, events, commando's
├── core/                pure spellogica (geen Minecraft-imports) + unit tests
├── config/              JSON-config in config/battlehub/ (config, kits, titels)
├── lang/                server-side vertalingen en &-kleurcodes
├── data/                spelersprofielen (JSON) en inventaris-snapshots (NBT)
├── lobby/               lobby, kompas, bescherming
├── gui/                 kist-menu's (hoofdmenu, submenu's, spectate, stats, leaderboards, titels)
├── session/             per speler: in lobby / wachtrij / match / toeschouwer
├── match/               Match-basisklasse, MatchManager, duels, kits, beloningen
├── games/               de zeven party games
├── arena/               arena-dimensie, slots, thema's (Forest, Castle, Nether, Ice, Sky), layouts
├── queue/               wachtrijen, ranked matchmaking, uitdagingen, party-lobby's
├── spectate/            toeschouwers
├── progression/         coins, XP, levels, titels, streaks, scoreboard-teams
├── command/             /hub /duel /stats /spectate /battlehub …
└── mixin/               vergrendelde items (kompas) niet verplaatsen/droppen
```

Een speler zit altijd in precies één toestand (lobby, wachtrij/party-lobby, match of toeschouwer); de
sessielaag dwingt dat af, dus niemand zit ooit in twee matches tegelijk.

## Fases

1. **Fundament** – mod-skelet, config, vertalingen, lobby (+ `/battlehub setlobby`, `/hub`), kompas
   (vast slot, niet te droppen of verplaatsen), hoofdmenu en submenu's als kist-GUI, opslag van spelersdata.
2. **Eerste speelbare loop** – sessies, match-systeem, arena-dimensie en slots, Sword PvP in één arena,
   countdown, winst/verlies, terug naar de lobby met herstel, coins en XP.
3. **Alle duels** – Axe, Mace, Bow, Crossbow, wachtrijen, `/duel <speler>` en uitdagingen, Ranked met ELO.
4. **Spectator en stats** – Spectate-menu (ververst live), Join Spectator, Stats-GUI, leaderboards, `/stats`.
5. **Party games** – Bow Spleef, TNT Run, Sumo, King of the Hill, Capture The Flag, Parkour Race,
   Last Man Standing, met party-lobby's (min/max, countdown).
6. **Afwerking** – alle vijf arena's per modus, titels (ook kopen met coins), win streaks met
   aankondigingen, titel in chat/naamtag/tablist, README (Modrinth-tekst), CHANGELOG, icoon, release-jar.

Elke fase: compileert, unit tests groen, server start in CI zonder fouten, zelftest geslaagd.

## Testen

* `src/test/java`: JUnit-tests voor alles in `net.battlehub.core`.
* CI: `./gradlew build` en een echte server-start met de release-jar plus `/battlehub selftest`
  (alleen actief met `-Dbattlehub.selftest=true`), die met nep-spelers elke modus in elke passende arena speelt.

## Extra keuzes tijdens het bouwen

* **Vertrekken van de server** loopt via een mixin op `PlayerList.remove` in plaats van het netwerk-event
  `DISCONNECT`. Die draait vóórdat vanilla het spelersbestand opslaat, dus wie midden in een match uitlogt,
  krijgt zijn eigen inventaris opgeslagen en niet de kit.
* **Nep-spelers in de zelftest** doen wat een echte client automatisch doet: ze bevestigen dat de wereld
  geladen is en dat ze in een andere dimensie zijn aangekomen. Zonder die bevestiging blijft de server ze
  onkwetsbaar houden.
* **Arena's**: elke modus gebruikt standaard alle vijf de thema's (Mace alleen Sky); de zelftest bouwt elke
  layout in elk thema en controleert dat spawns op vaste grond staan, er hoofdruimte is en de nodige
  markeringen (KOTH-zone, CTF-vlaggen en teamspawns) bestaan.
* **Talen**: naast Engels (standaard) zit er een Nederlandse vertaling in de jar. Elke speler krijgt de taal
  van zijn eigen client als die er is (`nl_be` valt terug op `nl_nl`), anders de standaardtaal van de server.
