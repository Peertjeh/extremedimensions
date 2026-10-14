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

## Versie 1.1

### Keuzes

| Onderwerp | Keuze |
|---|---|
| Eigen wereld | Een eigen dimensie `battlehub:hub` (leeg, met een lobby die de mod zelf bouwt). `/hub` brengt je erheen; je survival-toestand (inventaris, XP, levens, honger, effecten, spelmodus en positie) wordt bewaard in `<wereld>/battlehub/survival/` en teruggezet met `/survival`, het deur-item of de knop in het menu. De overworld wordt nergens meer aangeraakt: geen kompas, geen bescherming, geen schadeblokkering. Wie buiten de mod om de hub in- of uitgaat (admin-`/tp`), wordt automatisch in- of uitgecheckt. In de hub toont de XP-balk je BattleHub-level. |
| Best of 1 / Best of 3 | Keuze per speler (onthouden), met een knop in het modus- en ranked-menu en `/duel <speler> <modus> [bo1\|bo3]`. Aparte wachtrijen per formaat. Rondes in dezelfde arena: na elke ronde iedereen genezen, opnieuw de kit, terug naar de spawn en een korte countdown. Een serie telt als één match voor statistieken, coins en ELO. |
| Opnieuw spelen | Na elke match een klikbare knop in de chat en een item in de hotbar (30 seconden): dezelfde modus, hetzelfde formaat en ranked of niet; na een uitdaging een rematch; na een party game dezelfde lobby. |
| Cosmetics | Trails, kill-effecten, pijl-trails, overwinningseffecten en naamkleuren, te koop met coins via een nieuwe knop in het hoofdmenu en in te stellen in `cosmetics.json`. Alles met vanilla-deeltjes en -geluiden, dus zonder client-mod. |
| Arena's | Een generator bouwt voor elke match een nieuwe arena: vorm, reliëf, obstakels, dekking en decor zijn willekeurig, in 13 thema's. Duel-arena's zijn puntsymmetrisch, zodat beide spelers precies hetzelfde terrein hebben. Ook de party-arena's krijgen willekeurige vormen. |
| Parkour | Elke race een nieuw parcours: veel langer, met ladders, halve blokken, hekken, ijs, 1×1-blokken, diagonale sprongen en bochten. Moeilijkheid easy, medium, hard, extreme of random: spelers stemmen bij het joinen, de meeste stemmen winnen. Solo kan ook (oefenen tegen je eigen record, per moeilijkheid). Elke sprong wordt bij het genereren getoetst aan wat een speler kan halen; unit tests controleren dat voor duizenden parcoursen. |

### Fases

* **A** – eigen hub-wereld · **B** – Bo1/Bo3 en opnieuw spelen · **C** – willekeurige arena's ·
  **D** – parkour v2 · **E** – cosmetics · daarna README, CHANGELOG en versie 1.1.0.

Elke fase: compileert, unit tests groen, zelftest op een echte server in CI groen.

### Extra keuzes tijdens het bouwen van 1.1

* **Kill-effect "Lightning"** is een kolom van vonken met het geluid van de donder, geen echte bliksem-entity:
  zo ontstaat er geen vuur of schade en blijft de arena schoon.
* **Naamkleur** loopt via hetzelfde scoreboard-team als de titel. In Capture The Flag gaat de teamkleur voor.
* **Effecten alleen in BattleHub**: trails, kill- en wineffecten spelen alleen in de hub- en arenadimensie,
  nooit in de normale wereld. Trails in matches kunnen uit met `trailsInMatches`.
* **Parkour** gaat nooit naar het westen, zodat een parcours zichzelf niet kan kruisen; de generator
  rekent per sprong met halve blokken hoogteverschil en zoekt terug uit doodlopende stukken.
* **Duel-arena's** zijn puntsymmetrisch (gespiegeld door het midden), zodat elke speler vanuit de eigen spawn
  precies hetzelfde terrein ziet.

## Versie 1.2

| Onderwerp | Keuze |
|---|---|
| Totem | De death-event van Fabric komt vóór de totem-check van vanilla; BattleHub annuleerde de dood altijd, dus een totem deed niets. Nu laat de match vanilla zijn gang gaan als de speler een item met doodsbescherming vasthoudt (niet tegen de void). |
| Mace-arena | Duel-arena's hebben een schaal per modus (`arenaScale`); mace standaard 1,8: groter hoofdeiland, meer en verder weg gelegen eilanden. |
| Parkour-coins | Een aandeel van de coin-beloning per moeilijkheid; easy en medium 0. XP blijft. |
| Free for All | Party game met respawns op het spawnpunt het verst van de anderen, 3 seconden spawnbescherming (aanvallen beëindigt die), winst bij het killdoel of de meeste kills. |
| Toernooi | Party game tot 16 spelers. In de lobby stemmen op teamgrootte (1, 2, 4, 8) en kit (een duel-kit of willekeurig). Willekeurige teams met hoogstens één speler verschil; minder spelers geeft kleinere teams. Knock-out met byes in de eerste ronde, elke wedstrijd best of 3 in de duel-arena van de kit (groter voor grotere teams), wedstrijden van een ronde tegelijk. Tussen de wedstrijden wacht je in de hub en kun je toekijken; /hub of het verlaat-item verlaat het toernooi; een team zonder spelers verliest walkover. Winnaars krijgen coins en een toernooiwinst in hun stats. |

## Versie 1.3

| Onderwerp | Keuze |
|---|---|
| Coins geven | `/battlehub coins <speler>` toont het saldo; `give`, `take` en `set` passen het aan (admins, ook offline spelers, nooit onder nul). |
| PvP-tab | De knop Duels in het kompasmenu is PvP geworden: Free for All, 1v1, 1v2 en 1v3, daarna de kit (zwaard, bijl of mace). 1v1 zijn de bestaande duels. Free for All staat niet meer bij de party games. |
| Free for All-kit | Een kit kiezen is meedoen met een stem op die kit; de meeste stemmen beslissen, zonder stemmen de eigen FFA-kit. |
| 1v2 / 1v3 | Per kit een eigen wachtrij waarin je kiest: alleen, in het team of maakt niet uit. Groepen vormen in volgorde van binnenkomst; wie alleen wil, krijgt de plek alleen vóór wie het niet uitmaakt, net zo voor het team. Eén ronde (instelbaar met `roundsToWin`), geen friendly fire, arena groeit met het aantal spelers. Geen extra levens voor de solospeler (niet gevraagd). |
| Extreme Dimensions | In de werelden van BattleHub (namespace `battlehub`) staat Extreme Dimensions stil: geen drain, dimensiewissels tellen niet, geen penalty's (het Litteken wordt tijdelijk opgeheven), het keuzescherm wacht, geen HUD, het altaar doet niets. Bij het verlaten loopt alles verder. Daarbij twee bestaande compileerfouten in Extreme Dimensions opgelost. |
