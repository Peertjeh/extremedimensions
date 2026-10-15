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

## Versie 1.4

| Onderwerp | Keuze |
|---|---|
| Opbouw | Regels zonder Minecraft-code in `core.bedwars` en `core.prophunt` (getest met JUnit), een algemene `StateMachine` voor de fases binnen een spel, instellingen in `bedwars.json` en `prophunt.json`, schermen apart (`BedWarsUi`, `PropHuntUi`, menu's) en per spel losse onderdelen (generators, bouwregels, winkel, vermomming). |
| Fases Bed Wars | Lobby en aftellen zijn de bestaande party-lobby en de start-countdown; daarna ACTIVE → SUDDEN_DEATH → PODIUM. Overtime is het moment dat iedereen gaat gloeien; bij het einde van de tijd wint het team met de meeste spelers, dan met bed, dan met de meeste final kills. |
| Teams | 2 tot 4 teams, afhankelijk van het aantal spelers (max. 4 per team), of vast via `teams`. |
| Bedden | Een bed is een gewoon blok in de arena; slopen gebeurt door de match (beide helften tegelijk), het eigen bed kan niet. Op een bed klikken doet niets (in de arenawereld zou een bed ontploffen); sluipen met een blok bouwt ertegenaan. |
| Bouwen | Alleen blokken die de winkel als blok verkoopt (dus geen werkbank waarmee je goedkoop zwaarden kunt maken), binnen het bouwgebied, niet vlak bij spawns, winkels en generators. Alleen door spelers geplaatste blokken kunnen kapot. Het arenavak is groot genoeg voor alles wat gebouwd kan worden en wordt na afloop helemaal leeggemaakt. |
| TNT | Gaat bij het plaatsen meteen aan. De match laat hem zelf ontploffen zonder blokschade en breekt daarna alleen geplaatste blokken (glas en obsidiaan niet), zodat de map heel blijft. |
| Winkel / dupes | Kopen is alles of niets: eerst prijs, niveau en ruimte controleren, dan precies betalen, dan pas uitdelen. Menu-items kunnen het menu nooit uit (zoals alle BattleHub-menu's). Harnas zit vast (niet uit te doen of te laten vallen), zwaarden en gereedschap kunnen niet gedropt worden, grondstoffen en blokken wel (om te delen). Na een dood gaan pikhouweel en bijl één niveau omlaag, de rest van de inventaris is weg. |
| Winkelier | Een villager zonder AI die niet gewond kan raken en terugkeert als hij wordt geduwd; klikken op hem of op de ton ernaast opent de winkel. |
| Verbinding weg | Wie de verbinding verliest of /hub doet, ligt eruit (zoals in de andere modi) en krijgt zijn eigen spullen terug; een team zonder spelers ligt eruit. Opnieuw meedoen in een lopende match kan niet. |
| Prop-vermomming | De speler zelf wordt voor anderen niet meer verstuurd (mixin op `Entity.broadcastToPlayer`); anderen zien een block display met een onzichtbare 1×1-hitbox. Stilstaan maakt de prop "vast": een nepblok op het raster dat alleen de anderen krijgen. De zelftest controleert dat het verbergen werkt. |
| Missen | Een armzwaai zonder raak (mixin op `LivingEntity.swing`; het aparte zwaai-pakket bestaat niet meer in 26.3) die niets binnen bereik raakt, of een klik op een echt object uit de lijst, kost de hunter levens en punten (hoogstens twee keer per seconde). Gewone vloer en muren tellen niet als fout object. |
| Prop Hunt-fases | WAITING → HIDING → HUNTING → ROUND_END, per ronde opnieuw; wie het minst gezocht heeft, wordt eerst hunter. Gevonden props worden hunter (instelbaar). Na de laatste ronde winnen de meeste punten. |

## Versie 1.5

| Onderwerp | Keuze |
|---|---|
| Bed Wars solo / duo | In de lobby stem je op solo (iedereen voor zich) of duo's (teams van twee, `duoTeamSize`); zonder stemmen geldt `defaultFormat` (duo). Hoogstens 8 eilanden (`maxTeams`), altijd minstens twee teams. Zijn er in solo meer dan 8 spelers, dan delen sommigen een eiland (8 kleuren is het maximum dat mooi past). De vroegere automatische 2–4 teams van hoogstens 4 zijn vervallen. |
| Arena | Meer eilanden geeft een grotere cirkel (straal 36 tot 56); bij meer dan vier teams vier diamanteilanden in plaats van één tussen elk paar. |
| Props | Een kwart trager (`propSpeed` 0,75) en 10 levens in plaats van 20 (`propHealth`), via tijdelijke attribuutwijzigingen die verdwijnen zodra de speler geen prop meer is. Bestaande `prophunt.json`-bestanden met de oude standaardwaarden worden bijgewerkt. |
| Misslaan | In de lucht slaan kost geen levens meer (`airMissDamage` 0), alleen punten; een verkeerd object raken kost nog steeds allebei. |

## Versie 1.6

| Onderwerp | Keuze |
|---|---|
| Plek | Boven de lage muur rond het plein, op de twee plekken uit de screenshots: noordwest (tussen de brug naar het ijs- en het kasteeleiland) voor PvP, zuidwest (tussen nether en ijs) voor Ranked. De borden staan gelijk verdeeld over de kwartcirkel tussen twee bruggen, net boven de treden van de muur, en kijken naar de fontein. |
| Techniek | Tekst-displays die alleen bij de spelers bestaan (eigen pakketten, zoals de sidebar): per speler in de eigen taal, met de eigen rij gemarkeerd en de eigen plek onderaan, en er blijft niets achter in de wereld. Echte entiteiten zouden voor iedereen dezelfde tekst tonen en met de wereld worden opgeslagen. Na wisselen van wereld of respawnen worden ze opnieuw gestuurd; alleen veranderde borden worden bijgewerkt. |
| Inhoud | PvP: kills, keer dood, K/D (minstens 10 kills) en verdiende coins. Ranked: ELO (hoogste), kills, keer dood, K/D en coins uit ranked-potjes. "PvP" telt Free for All, de duels (ook boog en kruisboog), 1v2 en 1v3. Elke muur wisselt om de 8 seconden van modus (alle modi samen, dan per modus); de bolletjes onderaan laten zien welke. Alles in te stellen onder `leaderboards`. |
| Klikken | Borden zijn niet aanklikbaar (een tekst-display heeft geen hitbox, en klikken op iets dat alleen bij de speler bestaat komt niet bij de server aan); daarom wisselen ze vanzelf. Het menu Stats → Ranglijsten blijft er ook. |
| Verdiende coins | Werden nog niet bijgehouden. Nu per modus, per ranked-modus en in totaal (`coinsEarned`, ook levelbonussen en toernooiprijzen; admin-coins tellen niet mee). Oude profielen krijgen één keer een schatting uit hun gespeelde, gewonnen en kills per modus met de huidige beloningen; het totaal is minstens wat ze hebben plus wat ze aan titels en cosmetics uitgaven. |

## Versie 1.6.1

| Onderwerp | Keuze |
|---|---|
| Minder borden | Op verzoek drie borden in plaats van negen: totaal kills en totaal keer dood over alle spellen in BattleHub (noordwest), en de beste parkour runner (meeste parkour races gewonnen, midden op de zuidwestmuur). Geen wisselende modi meer, iets grotere tekst (`scale` 1,25). |
| Parkour-winst | Telt zoals de mod een parkour-winst al bijhoudt: eerste in een race, of alleen het parcours uitlopen (oefenen telt dus mee). |
| Bijwerken | Een `config.json` met precies de borden van 1.6.0 krijgt de nieuwe drie; wie de borden zelf had aangepast, houdt die. De andere statistieken (K/D, coins, ELO, per modus, ranked) blijven in te stellen. |

## Versie 1.7 – Fase 1: nooit meer wachten

| Onderwerp | Keuze |
|---|---|
| Bots | Echte (gesimuleerde) spelers met een eigen nep-verbinding, zoals de testspelers: zo werkt elke bestaande modus zonder aanpassingen. Hun "brein" zet elke tick de invoer die een client zou sturen (lopen, strafen, sprinten, springen, kijken, slaan, boog spannen) en ze draaien hun eigen natuurkunde. Slaan gaat via het gewone aanval-event, voorwerpen via de gamemode, dus dezelfde regels als voor spelers. |
| Niveaus | Makkelijk, normaal, moeilijk: reactietijd, mikfout, draaisnelheid, strafen, crits, w-tap, schild, gouden appels, boogfout, kans om een parkoursprong te verkeerd te schatten, zoekafstand in Prop Hunt. |
| Gedrag per spel | Elke match geeft een stijl (vechten, schieten, rennen, parkour, bow spleef, prop hunt), wie vijand is (teams) en een doel (heuvel, vlag, eigen bed). Boogschoten met voorsprong en de juiste boog voor de afstand (pijlbaan zoals in vanilla). Parkoursprongen met precies genoeg snelheid volgens de spelernatuurkunde; Bed Wars-bots bewaken hun bed maar bouwen en kopen niet (te ingewikkeld voor nu). |
| Opvullen | Na 15 s alleen in een gewone duelrij, partylobby (tot 4), 1v2 of 1v3; komt er een tweede mens, dan gaan de bots weg. Nooit ranked. Na het potje vertrekken ze na 4 s. Hoogstens 12 tegelijk, en nooit als de server bijna vol is (ze tellen mee voor het spelerslimiet). |
| Eerlijk | Namen beginnen met `Bot_`. Geen spelersbestanden, profielen alleen in het geheugen, niet op ranglijsten, geen join-, leave- of advancementberichten. Potjes met bots: halve beloning, geen statistieken, reeksen of ELO, zodat je ze niet kunt farmen. |
| Lobbyfiguren | Acht figuren rond de fontein (op 10,5 blokken, op de noordhelft, kijkend naar het spawnpunt). Lichaam (mannequin, anders armor stand) en tekst bestaan alleen bij de spelers, in hun taal, met live aantallen (alleen mensen); het hoofd draait naar wie dichtbij staat. Klikken gaat via een echte, onzichtbare interaction-entiteit; oude kopieën na een herstart worden opgeruimd. |
| Modus van de dag | Elke dag een andere uit een lijst (alle modi een keer voordat er een terugkomt), dubbele coins, aangekondigd bij het binnenkomen van de hub, met een ster in het menu en op de figuur. |

