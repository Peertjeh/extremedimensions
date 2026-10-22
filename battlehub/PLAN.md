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

## Versie 1.8 – Fase 2: competitief

| Onderwerp | Keuze |
|---|---|
| Seizoenen | Vaste lengte (56 dagen) vanaf een startdatum in de config, dus het seizoensnummer volgt uit de datum. Welk seizoen al is afgesloten staat in `season.json`; een seizoen dat eindigde terwijl de server uit stond, wordt bij de volgende start afgesloten. Afsluiten gebeurt voor alle profielen tegelijk (ze staan toch in het geheugen). |
| Divisies | Elke tier in drie gelijke delen (III laag, I hoog); Netheriet heeft geen plafond en gaat per 100 punten omhoog. |
| Plaatsing | De eerste 5 ranked-potjes per modus per seizoen gebruiken een dubbele K-factor, per speler apart (dus de één kan in plaatsing zijn en de ander niet). |
| Einde | Piek van het seizoen (hoogste rating in welke modus dan ook) bepaalt tier, coins en de nieuwe titelvoorwaarde `season` (hoogste tier ooit aan het einde van een seizoen). Daarna gaat elke rating halverwege terug naar de start. Bestaande `titles.json` krijgen de vier seizoenstitels erbij. |
| Recap | Elke match houdt per speler schade, raak en zwaaien, pijlen, combo's en kills bij (via het schade-event van Fabric, het zwaai-event en nieuwe pijlen in de wereld). Na afloop krijgt iedereen zijn eigen regels en de MVP (kills tellen het zwaarst, dan schade). |
| Kit-editor | Per kit een eigen indeling (welke inventarisplek elk genummerd kit-item krijgt). In het menu: klik een item en dan een plek, ze wisselen; bewaard in het profiel. Harnas en offhand blijven waar ze horen. Ongeldige indelingen worden genegeerd. |
| Vroeg weggaan | Telt alleen in potjes met minstens twee mensen en zonder bots, zodra het aftellen bezig is. De wachttijd groeit per keer binnen een dag (30 s, 2, 5, 15, 30 min) en vergeten wordt een dag na de laatste keer. |
| AFK | Niet bewegen, rondkijken of zwaaien: na 90 s uit het potje (telt als weggaan), na 5 min uit de wachtrij. Props in Prop Hunt staan juist stil en worden met rust gelaten. |
| Teamen | Alleen in Free for All en Last Man Standing, vanaf drie spelers: twee spelers die lang (30 s) dicht bij elkaar blijven, wel anderen raken maar elkaar niet, krijgen een waarschuwing en gloeien 10 s. |
| Anti-cheat | Alleen meldingen aan admins en in het log, nooit straffen: reach (afstand van de ogen tot de hitbox, twee keer binnen 10 s), meer dan 20 klikken per seconde drie seconden lang, 4 s zweven zonder te vallen, en meer dan 13 blokken per seconde (niet direct na een klap of met speed). Echte anti-cheat kan niet goed zonder client-informatie; dit vangt de duidelijke gevallen. |
| Meldingen | `/report` schrijft één JSON-regel per melding in `reports.log`, met de match waar de speler in zat, en laat het online admins zien; één per minuut per speler. |

## Versie 1.9 – Fase 3: blijven terugkomen

| Onderwerp | Keuze |
|---|---|
| Quests | Een pool in `progress.json`; elke speler krijgt per dag 3 en per week 3 quests, gekozen met een seed uit zijn id en de datum (dus de hele dag dezelfde, maar niet voor iedereen dezelfde). Doelen: spelen, winnen, kills, schade (hartjes uit de recap), ranked winnen, de modus van de dag, MVP, eventueel voor één modus. Voortgang komt uit elk potje, ook potjes met bots (die tellen dan voor het deel dat ze opleveren). |
| Inlogreeks | De eerste keer per dag in de hub: coins voor dag 1, 2, 3, ... op rij (50 tot 400, de laatste herhaalt); een gemiste dag begint opnieuw. |
| Seizoenspas | Gratis, 50 tiers per ranked-seizoen (hetzelfde seizoen als ranked). Pas-XP per potje en per winst, en uit quests. Elke tier betaalt meteen uit: coins, meer elke vijfde tier, op elke tiende de goedkoopste cosmetic die je nog niet hebt (anders coins). Geen betaalde pas. |
| Prestaties | Twaalf doelen die uit het profiel worden gemeten (kills, winst, potjes, reeks, duels, level, verzameling, parkour, Bed Wars, Prop Hunt, seizoenen, quests), elk met tiers; elke tier betaalt één keer (100 × tier²). |
| Prestige | Op het hoogste level terug naar level 1 (bevestigen met een tweede klik), met sterren voor je naam in tab en chat en 5% meer coins per prestige (hoogstens 50%). Titels voor pas en prestige komen in bestaande `titles.json` erbij. |
| Menu | Het hoofdmenu heeft nu vijf rijen: spellen op de eerste rij, oefenen, kits, quests, seizoenspas en prestaties op de tweede, de deur onderaan. |

## Versie 1.10 – Fase 4: samen spelen

| Onderwerp | Keuze |
|---|---|
| Vrienden | `/friend add` stuurt een verzoek (klikbaar [Accepteren]/[Weigeren]); vraagt de ander ook, dan zijn jullie meteen vrienden. Hoogstens 100. De lijst laat zien wat een vriend doet (offline, normale wereld, lobby, wachten, spelen + modus); vrienden horen het als je BattleHub binnenkomt. Opgeslagen in het profiel, dus ook offline. |
| Party's | Apart van de party-*spellen* (die heten in de code al `Parties`) heten ze in de code `Groups`. Hoogstens 8, alleen in het geheugen (weg na een herstart). Doet de leider mee aan een partyspel, of aan 1v2/1v3 aan de teamkant, dan gaan de leden die vrij in de lobby zijn mee. Duelwachtrijen volgen niet (dan zouden ze tegen elkaar spelen). In CTF en Bed Wars worden party's eerst verdeeld (grootste eerst, naar het kleinste team) en daarna de rest; te grote party's worden gesplitst. |
| Clans | `clans.json` in de wereldmap. Beginnen kost 1000 coins; naam 3–16 tekens, tag 2–4 letters/cijfers, beide uniek. Tag in cyaan voor de naam (tab en chat). Elke winst van een lid: 1 punt, ranked-winst 3 (niet tegen bots, niet in privépotjes). Clanoorlog: elke maandag (ISO-week) wint de clan met de meeste weekpunten; elk lid krijgt 500 coins, daarna gaan de weekpunten naar 0. |
| Privépotjes | Een lobby met een code van vier letters (zonder letters die je makkelijk verwart: I, L, O), voor elk duel en elk partyspel behalve het toernooi. De maker start; alleen spelers die vrij in de lobby staan doen mee. Het potje is `unrated`: geen statistieken, coins, XP, ELO, quests, pas, clanpunten of vertrekstraf. |
| Menu | Hoofdmenu: vrienden & party (24), clan (25), privépotjes (31). |

## Versie 1.11 – Fase 5: wow-factor

| Onderwerp | Keuze |
|---|---|
| Intro | Bij de eerste aftelling vliegt een camera over de arena: een onzichtbare display-entity die alleen op de client van die speler bestaat, met `ClientboundSetCameraPacket`. De speler zelf blijft op zijn spawn staan, dus aan de wedstrijd verandert niets. Daarbij een grote titel (de modus) met wie er speelt. Daarna altijd nog drie seconden aftellen (aftellen = max(aftellen, intro + 3 s)). Niet bij een nieuwe ronde van best of 3. |
| Killfeed | Voor spellen zonder eigen scorebord (alles behalve Bed Wars en Prop Hunt) staan de laatste kills (standaard 5) rechts in beeld, nieuwste bovenaan, met een icoon: zwaard, boog, val of anders. De chatregel blijft. Echte killfeed-tekst rechtsboven zoals in shooters kan niet zonder client-mod; het zijbord is het dichtstbijzijnde. |
| Overwinning | Na de winst draait de camera langzaam om de winnaar (spelers en kijkers in de arena), tot net voor de terugkeer naar de lobby. Bij gelijkspel niet. |
| Double jump | In de lobby mogen spelers "vliegen"; zodra de client begint te vliegen (twee keer springen) wordt dat afgepakt en krijgt de speler een zet vooruit en omhoog. Alleen in adventure/survival, nooit in een wedstrijd of als toeschouwer, en we pakken alleen terug wat we zelf gaven (creative en /fly blijven ongemoeid). Voor een wedstrijd wordt het eerst weggehaald, zodat het nooit in de opgeslagen staat terechtkomt. |
| Oefenpop | Een pop op het plein (client-only lichaam + echte klikbox). Iedere speler ziet boven de pop zijn eigen getallen. Schade "als met een diamanten zwaard" (7 × cooldown, crit × 1,5), omdat je in de lobby geen wapen hebt; zo oefen je de timing. CPS komt uit armzwaaien, bereik is de afstand van oog tot klikbox. Een getal zweeft omhoog (alleen voor wie slaat). Rechtsklik = opnieuw beginnen. |
| Garderobe | Twee nieuwe cosmetic-categorieën: hoed (elk item op je hoofd) en outfit (leren kleren in een kleur). Alleen in de lobby, als vergrendelde BattleHub-items in de armorslots; eigen armor gaat voor. De kit vervangt ze in een wedstrijd en ze gaan nooit mee naar de normale wereld. Bestaande `cosmetics.json` krijgt ze erbij (configVersion 2). |
| Resourcepack | Optioneel en standaard uit. BattleHub bouwt het zelf bij het starten: pixel-icoontjes (8×8, in code getekend) als font op privé-tekens, geluiden die naar vanilla-geluidsbestanden met eigen toonhoogte verwijzen (eigen .ogg's kunnen we niet maken; via de map `overlay/` kan een server ze toevoegen) en lobbymuziek. Aanbieden als je BattleHub binnenkomt; nee zeggen mag (tenzij `required`). Icoontjes en extra geluiden alleen voor wie het pack echt geladen heeft; de rest ziet gewone symbolen. Serveren via een kleine ingebouwde HTTP-server (`host` + `port`), of een eigen `url`. Echte eigen GUI-achtergronden (de "negatieve ruimte"-truc) zijn niet gedaan: te breekbaar per versie. |

## Versie 1.12 – Fase 6: nieuwe modi en maps

| Onderwerp | Keuze |
|---|---|
| Indeling | The Bridge, Boxing en Build UHC zijn duels (wachtrij, uitdagen, ranked met eigen ELO, oefenen met bots) in een nieuw menu "Duelspellen". Spleef, SkyWars en Murder Mystery zijn partyspellen. Toernooien blijven bij de gewone kit-duels (de nieuwe duels hebben eigen regels die niet in een bracket passen). |
| The Bridge | Twee eilanden met een doel (gat van 3x3) en een smal pad ertussen; spelers bouwen bij met klei in hun teamkleur (alleen in het bouwvak, niet in doelen of op spawns; alleen geplaatste blokken kunnen weg). In het andere doel springen = punt, iedereen terug naar huis (3 s aftellen). Dood = terug naar huis, geen rondeverlies. Eerst 5 (instelbaar); bij tijd op wint wie de meeste doelpunten heeft. Altijd "best of 1" (de doelpunten zijn de rondes). Het smalle pad is er zodat bots kunnen meedoen. |
| Boxing | Resistance V in de kit: geen schade, wel knockback. Elke klap telt; eerst 100 (instelbaar) wint de ronde. De winst wordt na de klap bepaald (niet midden in de schadeafhandeling). Bij tijd op: meeste klappen. |
| Build UHC | Grote duelarena met ruimte om te bouwen. Kit met blokken, emmers, hengel, boog, gouden appels. "Geen natuurlijke genezing" kan niet per wedstrijd (dat is een gamerule van de hele arenawereld), dus wordt honger op 17 gehouden: natuurlijke genezing begint pas bij 18. |
| Spleef | Twee sneeuwvloeren; spelers staan in survival zodat de schep kan graven, maar de wedstrijd haalt het blok zelf weg (geen drops) en geeft een sneeuwbal (max 16). Alleen sneeuwballen raken. |
| SkyWars | Eén eiland per speler (minstens 4) met een kist, stapstenen en een groot midden met betere kisten. Buit uit `skywars.json` ("item min-max xGewicht"). Alles binnen de arena mag gebouwd en gebroken worden (de arena wordt na afloop toch gewist). Wie sterft laat alles vallen. Bij tijd op: meeste kills onder de levenden, anders gelijkspel. |
| Murder Mystery | Op het marktplein van Prop Hunt (veel verstopplekken). Rollen bij "GO". Moordenaar krijgt na 10 s een zwaard (één klap); detective een boog, een pijl terug 4 s na elk schot; onschuldigen kopen een boog met 10 goud (er valt elke 3 s goud). Een onschuldige raken kost je eigen leven. De boog van een dode detective blijft gloeiend liggen; pakt de moordenaar hem op, dan gaat hij terug. |
| Mapstem | Een knop in elke partylobby (behalve het toernooi) opent een stemmenu: maakt niet uit, elk thema van die modus, of elke gebouwde map. Meeste stemmen wint (bestaand stemsysteem, vraag "map"). |
| Eigen maps | Structuurbestanden (.nbt van een structure block) in `config/battlehub/maps/<modus>/` of `maps/all/`. Markers zijn structure blocks in Data-modus met tekst `spawn`, `spawn:90`, `spectator`, `goal_red`, ... BattleHub leest het bestand zelf (palet met eigenschappen, blokken, markers) en maakt er een gewone arena-blauwdruk van, die net als elke arena een paar blokken per tick wordt gebouwd; markers worden niet gebouwd. Inhoud van kisten en tekst op bordjes gaat niet mee (de vanilla-structuurlader bleek in 26.3 bij een met de hand opgebouwd bestand niets te plaatsen, en zo hangt het nergens van af). Alleen modi die niet van een vaste vorm afhangen nemen eigen maps (zie README); de rest blijft gegenereerd. Zonder stem wordt een eigen map in de helft van de gevallen gebruikt (`customMapChance`). |
| Menu's | Knoppen worden nu automatisch gecentreerd per rij (er zijn 8 duels en 13 partyspellen); statistieken per modus staan op een eigen pagina. |

## Versie 1.13 – Fase 7: stats-export en wekelijkse toernooien

| Onderwerp | Keuze |
|---|---|
| Discord | Bewust niet gebouwd (zo gevraagd): geen webhooks of andere Discord-koppeling. |
| Wekelijks toernooi | Een vaste dag en tijd in de tijdzone van de server (of een ingestelde). Aanmelden opent een kwartier vooraf (melding met knop), herinneringen op 5 en 1 minuut. Bij de start spelen alle aangemelde spelers die vrij in BattleHub staan het gewone toernooi (zelfde bracket, best of 3), met de ingestelde kit en teamgrootte. Te weinig spelers: afgelast. Eén keer per ISO-week, ook na een herstart (`weekly.json` in de wereldmap). Bovenop de gewone prijzen: 2000/500 coins, `weeklyWins` in het profiel en twee titels (1 en 5 keer). Admins kunnen de aanmelding meteen openen (`/battlehub weekly open <seconden>`). |
| Export | JSON-bestanden voor een website, elke 5 minuten (en bij het stoppen van de server): spelers, ranglijsten, clans, seizoen, recente wedstrijden (alleen in het geheugen, de laatste 100), wekelijkse toernooien en serverstatus. Verzamelen op de serverthread, schrijven op een eigen thread, elk bestand in één keer vervangen (tijdelijk bestand + verplaatsen), dus een website leest nooit een half bestand. Een eenvoudige `index.html` wordt één keer neergezet. Bots tellen niet mee. |
| Webserver | Optioneel (standaard uit): een kleine ingebouwde HTTP-server die alleen de exportmap serveert (geen paden erbuiten), met `Access-Control-Allow-Origin: *` zodat een website op een ander adres de bestanden kan ophalen. |

## Versie 1.13.1 – Opruimen, bouwgrens, hand-item

| Onderwerp | Keuze |
|---|---|
| Items in arena's | Drie oorzaken: (1) bij het wissen gooiden kisten hun inhoud op de grond (vanaf 1.21.5 doet het weghalen van een blok-entity dat), terwijl de entities al vóór de blokken werden opgeruimd; (2) delen van een grote arena konden tijdens het spel ontladen, waarna items en pijlen in die chunks werden opgeslagen en in de volgende arena op die plek terugkwamen; (3) oude restanten in bestaande werelden. Oplossing: kisten e.d. eerst leegmaken (`Clearable`) en wissen met `UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS`; entities voor én na het wissen opruimen (16 blokken rond de arena); de chunks van een arena blijven geforceerd geladen vanaf het bouwen tot het wissen klaar is (met een teller per chunk; bij het starten worden achtergebleven geforceerde chunks in de arenawereld losgelaten); losse items, pijlen, XP, TNT en vallende blokken worden opgeruimd als de arena klaar is en bij "GO". |
| Bouwgrens | Een blauwdruk kan nu een speelveld hebben: de kolommen die erbij horen en een maximale hoogte. Gegenereerde duelarena's: binnen de muur (eilanden: de eilanden met wat ruimte eromheen, niet de decoratie verder weg). Build UHC is altijd ommuurd, ook in de open thema's, met onzichtbare barrier-blokken boven de muur tot de bouwhoogte, zodat je er niet overheen kunt bouwen of klimmen en water binnen blijft. SkyWars: tot 8 blokken buiten de ring en 18 hoog. Bed Wars en The Bridge houden hun eigen bouwvak. |
| Water en lava | Een mixin op `FlowingFluid.spreadTo` laat vloeistof in de arenawereld alleen stromen binnen het speelveld van een arena en boven de void-lijn; anders bleven watervallen onder eilanden voor altijd staan (die vallen buiten het gebied dat gewist wordt). Een mixin op `BucketItem.emptyContents` controleert ook emmers die op de lucht of op water gericht worden (die gaan niet langs het blok-event). |
| Hand-item | De kit zette het geselecteerde hotbarvak alleen op de server op 0. De client hield het oude vak vast: hij liet blokken zien die je niet kon gebruiken, terwijl anderen het zwaard zagen. Nu stuurt BattleHub altijd `ClientboundSetHeldSlotPacket` mee (kit, Prop Hunt, terugzetten van je inventaris). |

## Versie 1.14 – Veel meer cosmetics

| Onderwerp | Keuze |
|---|---|
| Omvang | Van 66 naar 247 cosmetics in elf categorieën: trails, aura's (nieuw), pijltrails, killeffecten, wineffecten, doodskreten (nieuw), wapenskins (nieuw), huisdieren (nieuw), hoeden, outfits en naamkleuren. Bestaande `cosmetics.json` krijgen de nieuwe erbij (configVersion 3), zonder iets weg te halen. |
| Particles | Een eigen kleine notatie (`ParticleSpec`, los te testen): een particle-id, `dust:#RRGGBB[:grootte]`, `dust:#van>#naar` (vervagend), `rainbow` (de tint draait door), `trail:#RRGGBB` (lichtstreep), `block:`/`item:` (kruimels), `tint:entity_effect:#RRGGBB`, en combinaties met `+`. Zo zijn gekleurde trails en verlopen mogelijk zonder resourcepack. |
| Aura's | Vormen als punten rond de voeten (`AuraShape`, los te testen: vleugels die klapperen en aan de rugkant staan, halo, kroon, baan, helix, wolk met druppels, spiraal, melkweg, hart, ring, tornado, cape). Om de 3 ticks een frame en hoogstens zo'n 60 punten, zodat een lobby vol vleugels het netwerk niet overspoelt. Alleen in de lobby van de hub, niet in potjes. |
| Vuurwerk en bliksem | Echte vuurwerkexplosies (met kleuren en vormen) en bliksem bestaan alleen bij de kijkers: BattleHub stuurt een raket die meteen ontploft (entity-event 17) en een bliksemschicht die na 1,5 s weer verdwijnt. Op de server bestaat er niets, dus geen schade, geen brand en geen geraakte mobs (een echte vuurpijl met ontploffing doet wel schade). |
| Wapenskins | Via het `item_model`-component krijgt een zwaard uit de kit het uiterlijk van een ander vanilla item (blaze rod, trident, speer, end rod als lichtzwaard, ...), eventueel met glans. Het item blijft een zwaard met dezelfde schade, dus eerlijk. Alleen zwaarden (bogen hebben een span-animatie die met een ander model niet klopt). |
| Huisdieren | Een item display dat elke tick naar je rechterschouder springt (met interpolatie, dus vloeiend), op en neer deint en langzaam draait. Bewust geen passagier op de speler: dat geeft problemen bij teleporteren tussen werelden. Weg zodra je de lobby verlaat, terug als je terugkomt; achtergebleven exemplaren (na een crash) worden opgeruimd via een tag. |
| Doodskreten | Een vanilla geluid (met optionele toonhoogte) waar je in een potje sterft, voor iedereen in de buurt. |
| Outfits | Eén kleur, een verloop van boven naar beneden (`#van>#naar`, de broek krijgt de mengkleur) of drie losse kleuren. |
| Wat niet | Eigen 3D-modellen of textures voor items en hoeden vragen het resourcepack en een modelbestand per item; dat is te veel voor nu. Wapenskins en huisdieren gebruiken daarom vanilla modellen. |

## Versie 1.15 – Originelere maps

| Onderwerp | Keuze |
|---|---|
| Kleurverlopen | Per thema 2 tot 4 verlopen van blokken die in elkaar overgaan (`Palettes`), bijvoorbeeld cobblestone → andesite → stone → stone bricks → polished andesite → smooth stone, de terracotta-banden van Badlands, of gewaxt koper van vers tot groen (gewaxt, zodat het niet verder verweert). Een arena kiest er één, soms omgekeerd. |
| Patronen | Elke vloer volgt een willekeurig patroon (`FloorPattern`, los te testen): wolken (ruis), van het midden naar buiten, golvende banden, vlakken (Voronoi), ringen of marmeraders. Grootte en richting zijn ook willekeurig. Per blok een klein beetje "rafel" (dithering), zodat de grenzen tussen twee blokken geleidelijk lopen zoals bij een echte fade. Muren lopen van voet naar top door een eigen verloop (behalve hagen, paddenstoelhoeden en terracotta-banden, die hun eigen stijl houden). |
| Ruis | Eigen value noise met een paar octaven (`Noise`), zelfde seed = zelfde patroon; geen Minecraft-klassen nodig. |
| Variatie | Straal 13–24 (was 15–21), spawns 3 tot 7 blokken van de rand (maar altijd minstens 16 uit elkaar), muren 3–6 hoog, vijf soorten terrein (vlak tot heuvelachtig), meer of minder obstakels en planten, vier nieuwe middens (krater, plateau, ruïnes, vloermozaïek). Eilanden: 1 tot 5 satellieten op wisselende afstand en hoogte, het hoofdeiland 11–19 groot. Puntsymmetrie (eerlijkheid) blijft: alleen het uiterlijk van de vloer is niet symmetrisch. |
| Controle | De zelftest kijkt of elke bloknaam in thema's, verlopen en layouts bestaat (een onbekende naam zou als lucht verschijnen) en of twaalf arena's na elkaar echt verschillen. |

## Versie 1.16 – Een professionele hub

| Onderwerp | Keuze |
|---|---|
| Aanpak | Alles in code (geen schematic-bestanden): een kleine gereedschapskist (`HubKit`) met kleurverlopen met rafels, "textuur" (af en toe een ander blok, zoals bouwers een vlakke muur breken), schijven, ringen, cilinders, kegels, koepels, dozen, eilandonderkanten, lantaarns aan kettingen en bomen. Bloknamen mogen eigenschappen hebben (`quartz_stairs[facing=east,half=bottom]`). |
| Indeling | Het plein wordt groter (straal 27, was 22) zodat de figuren (10,5), de oefenpop (8) en de ranglijsten (boven de balustrade) op hun plek blijven. De ranglijsten houden meer afstand tot de poorten (14°). Vier poorten op de assen met torens, een boog, een hangende lantaarn en een gekleurde beacon; bruggen van 7 breed met lantaarns, leuningen en bogen eronder. Op 64 blokken vier eilanden (straal 20) met elk een groot gebouw; het colosseum ligt in het noorden, recht in beeld vanaf de spawn. Kleine zwevende eilanden met bomen op de diagonalen. |
| Kleur | Het plein loopt van smooth quartz in het midden via calcite en diorite naar andesite aan de rand, met een donkere kompasroos (zwart en deepslate, gouden punten, koperen diagonalen), een koperen ring met zeelantaarns, en een balustrade van quartz op zwarte bricks. Torens lopen van zwarte bricks via deepslate en stone bricks naar wit quartz; de spits van de toren loopt van groen naar vers koper (gewaxt, dus het blijft zo). |
| Bestaande servers | `LOBBY_VERSION` gaat naar 2: bij de eerste start wordt de oude hub weggehaald en de nieuwe gebouwd. |
| Controle | De zelftest kijkt of alle bloknamen (met eigenschappen) bestaan, of de vier poorten open zijn met een brug en een beacon, of het midden een beacon heeft en of de plekken van de figuren en de oefenpop vrij zijn. |

## Versie 1.17 – Meer Bridge

| Onderwerp | Keuze |
|---|---|
| Waar | 2v2 en 1v1v1v1 zijn partyspellen (een lobby die vol loopt, met bots als het te lang duurt), zodat er geen aparte team-wachtrij nodig is. Chaos is een duel (wachtrij, uitdagen, oefenen tegen bots), maar nooit ranked: een test hoort geen ELO te kosten. De gewone Bridge is niet veranderd. |
| Layout | Een nieuwe layout voor 2 tot 4 teams (`bridgeRing`): eilanden op een ring rond een klein middenplatform, met een smal pad van elk eiland naar het midden (voor de bots), het doel achterop elk eiland en spawns naast elkaar vooraan. Bij 3 teams staan ze een derde slag uit elkaar. De vloer krijgt het kleurverloop van 1.15. |
| 2v2 | Teams worden verdeeld zoals bij CTF (party's bij elkaar). Geen eigen vuur. Elk lid scoort voor het team; na een doelpunt gaan alle vier naar huis. |
| 1v1v1v1 | Ieder een eigen team en kleur (rood, blauw, groen, geel). Met 2 of 3 spelers worden het 2 of 3 eilanden. Scoren mag in elk doel behalve het eigen doel. Bots kiezen het dichtstbijzijnde vreemde doel. Standaard eerst 4 (in plaats van 5), omdat er meer doelen zijn. |
| Chaos | Power-ups vallen gloeiend op het middenplatform (gewoon oppakken): windcharges, enderparel, gouden appel, sneeuwballen, spinnenwebben, pijlen, witte klei, knockback-stok. Events (10 s): lage zwaartekracht (jump boost + slow falling), snelheid, dubbel doelpunt (tot het volgende doelpunt), instorten (alle geplaatste blokken boven het midden verdwijnen) en een cadeautje (2 windcharges). De tellers lopen door over doelpunten heen, zodat snelle doelpunten de events niet tegenhouden. |


## Versie 1.18 – Nieuwe lobby: procedurele architectuur-engine

| Onderwerp | Keuze |
|---|---|
| Uitgangspunt | De mod draait alleen op de server (vanilla clients), dus er zijn geen eigen blokken: de "custom blocks" uit de opdracht zijn hier de blokken van 26.3 en (optioneel) blokfamilies van andere mods, die bij het starten uit het register worden ontdekt (`lobby.discoverModBlocks`). Er was al een gereedschapskist (`HubKit`, `Build`, blueprints, kleurverlopen, `Noise`, `GridPath`, de isometrische preview); de engine is daar nieuw naast gebouwd in puur Java (`net.battlehub.arch`), zodat alles zonder Minecraft getest en gerenderd kan worden. |
| Lagen | Dimensie → district → gebouw → assemblage → module → blok. Een gebouw begint als **brief** (archetype, stijl, maten, keuzes, palet, staat, detail), dan **massa** (volumes met rol, plattegrond, hoogtes, dak, rang in de hoogte-hiërarchie; gecontroleerd door `Massing`), dan **assemblage** uit modules, dan **staat** (verwering, schade, planten), dan **materialen**, dan **validatie**. Elke stap heeft een eigen random-stroom: meer of minder decoratie verschuift nooit een muur. |
| Modules | Een module heeft een contract: categorie, stijlen, minimale/maximale maat, aansluitpunten (ingang, landing, dakzitting...), en tekent in zijn eigen frame (x langs de gevel, y omhoog, z naar buiten) in **rollen** (muur, hoek, band, omlijsting, dak, glas, glas-in-lood...), nooit in bloknamen. Frames draaien en spiegelen; richtingen, assen en eigenschappen draaien mee. Nieuwe modules worden geregistreerd in de bibliotheek en zonder wijziging van de engine gekozen (gewogen op stijl en maat). |
| Daken | Alle daken van een gebouw zijn één hoogteveld (zadel, schild, lessenaar, kegel, achtkantige spits, koepel): waar daken elkaar kruisen ontstaan kilgoten en graten vanzelf, en trappen krijgen hun hoeken zoals het spel ze zou geven (`Connect`, ook voor muren, hekken en ruiten, omdat blokken zonder buur-updates geplaatst worden). |
| Materialen | Een catalogus met metadata per blokfamilie (vormen zoals ze in 26.3 bestaan, gemiddelde textuurkleur, tags, verweerde varianten, vervangers). Paletten per rol met overerving (dimensie → district → gebouw). Gradiënten worden in **gebouwcoördinaten** uitgerekend (verticaal over waar de muren werkelijk zitten, donkerdere randen en lichtere brandpunten als plaatselijke verschuiving, zwakke ruis), dus een verloop loopt door over honderden modules. Overgangen tussen materialen vormen samenhangende plekken (stenen van twee blokken, patchruis), omlijstingen en banden wisselen op een vaste lijn. Een rol krijgt nooit een materiaal van een andere rol; ontbreekt een vorm, dan neemt de engine de dichtstbijzijnde familie qua kleur die de vorm wel heeft. |
| Archetypes | Kathedraal (schip, zijbeuken, transept, apsis, westfront met twee/één/geen torens, portaal, roosvenster, luchtbogen, vieringtoren of dakruiter, banken en altaar), kasteel (ringmuur met weergang, torens rond/vierkant/achtkantig met kantelen, machicoulis of kegels, poortgebouw, donjon, grote zaal, binnenplaats), magiertoren (getrapte toren, balkons, runen, torentjes, kristallen kroon of koperen spits, bijgebouw, zwevende kristallen), tempelruïne (podium, zuilengang of tholos, stoa, poort, obelisken; heel gebouwd en dan verouderd), herenhuis, wachttoren, kapel, markthal. |
| Lobby | Een zwevend eiland (straal 150) met grillige kust en rotsonderkant. Het plein van 1.16 blijft precies zoals het was (een geschreven module: spawn, figuren, ranglijsten). Vier lanen met trappen waar ze stijgen naar vier landmarks op geëgaliseerde terrassen (de kathedraal staat vanaf de spawn precies in de noordpoort), een stratenraster met huizen die de straat aankijken en tuinen in de blokken, wachttorens op de kliffen, bos naar de rand. Districten hebben eigen paletten, huizen ertussen kiezen naar hoek, zodat de stad geleidelijk verandert. |
| Controle | Een validator kijkt per gebouw naar maat, richtingen, bloknamen, zwevende blokken, ingangen, trappen (pad van onder naar boven), daken (geen muurtop open naar de lucht), paletten (geen grote sprongen) en compositie (hoogtevariatie, silhouet). Voor de lobby ook overlap en een looproute vanaf de spawn naar elke landmark. 34 JUnit-tests (reproduceerbaar, variatie, frames, aansluitpunten, doorlopende gradiënten, rollen, ontbrekende blokken, trappen, daken, grenzen, paletten, mislukte generatie, decoratie uit, detailniveaus, nieuwe modules, bloknamen tegen de 26.3-lijst, de lobby). De zelftest in een echte 26.3-server: catalogus compleet, geplaatste blokken gelijk aan het plan, looproutes in de echte wereld, plaatjes in het log. |
| Beeld | Een ray tracer in puur Java (echte blokvormen, zonlicht met schaduw, ambient occlusion, doorzichtig glas en water, lichtgevende blokken, lucht en nevel) voor ontwerpwerk en voor de plaatjes in de CI. Daarmee zijn de archetypes en de lobby bekeken en verbeterd (o.a. gradiënt over de echte muurhoogte, plaatselijke invloeden als verschuiving, grotere materiaalplekken, rustigere omlijstingen, zwevende onderdelen, vieringtoren boven het dak, roosvenster in de gevelspits). |
| Beperkingen | Geen echte Minecraft-screenshots in de CI (geen client); de ray tracer gebruikt gemiddelde textuurkleuren in plaats van texturen. Gebouwen draaien in stappen van 90 graden. Interieurs zijn per ruimte, niet per verdieping met eigen indeling. Het eiland is een hoogteveld (geen overhangende kliffen of grotten). |
| Bestaande servers | `LOBBY_VERSION` gaat naar 3 en de marker onthoudt stijl en seed: de oude lobby wordt weggehaald en de citadel gebouwd; een andere seed of stijl herbouwt hem bij de volgende start. |

## Versie 1.19 – Arena's uit de architectuur-engine

| Onderwerp | Keuze |
|---|---|
| Uitgangspunt | Dezelfde techniek als de lobby: alles in rollen tekenen, een palet per thema kiest de blokken, controleren vóór plaatsen. De engine kreeg een eigen pakket voor arena's (`net.battlehub.arch.arena`), in puur Java, zodat elke map zonder Minecraft getest en gerenderd kan worden. De gewone arenacode (`Blueprint`, layouts) bleef, en gebruikt de engine waar dat kan. |
| Thema's | Elk van de 13 thema's is een "dress": één palet voor grond, rots, paden en gebouwen, boomsoort, planten, landschapselement (ijspieken, basaltzuilen, cactussen, mesa's, obelisken...), lamp, landmarks, stofkleur en een sfeerdeeltje. Elk thema heeft minstens twee varianten. |
| Bed Wars | Elk teameiland: spawn op een plein in de teamkleur, generator onder een smidse (dekking tegen pijlen, aan alle kanten open), bed achteraan op een wollen sokkel met een boog erachter (drie blokken rondom vrij om te verdedigen), winkelier onder een marktkraam, een uitkijkpost vier hoog met borstwering en trap (hoogte boven de bruggen). Diamanteilanden: een ruïne met hoekdekking en drie brede doorgangen. Midden: zuilenrij met banieren van elk team, voetstukken met lampen als dekking, zwevend kristal. Rondom landmarks op rotsen, buiten het bouwgebied. |
| Eerlijkheid | Alle teameilanden komen uit één seed in hun eigen frame, dus zijn gelijk; het frame draait naar de dichtstbijzijnde as en spiegelt als het midden links ligt. Bij 8 teams staan de eilanden op een achthoek die 22,5° gedraaid is, zodat elk eiland het midden op dezelfde manier ziet. Bij 3, 5, 6 en 7 teams kan dat niet met kwartslagen (zoals voorheen); de eilanden zelf blijven gelijk. De controle vergelijkt elk eiland punt voor punt. |
| Tactiek | Lanen naar het midden, de buren en de twee dichtstbijzijnde diamanteilanden blijven vrij van bomen en decor; de controle eist een vrije kust in elke richting waar je vanaf de spawn naartoe kunt lopen. Zones rond spawn, bed, generator, winkel en uitkijkpost worden na het decoreren vrijgemaakt. |
| Andere arena's | Arena's met muren (duels, teamgevechten, Boxing, KOTH, FFA, LMS) staan in een tribune: zitrijen op een sokkelmuur (zichtbaar boven de arenamuur), wandelgang met lampen en vuurschalen, buitenmuur met vensters en banieren, poortgebouwen achter de spawnkanten, landmarks waar de toeschouwers kijken. Arena's boven de leegte en Sky/End krijgen zwevende eilandjes. KOTH, FFA en LMS hadden nog geen omgeving. Nieuwe dekkingsstukken op het veld (poortboog, zuilenrij, monument, put, kratten, kraam), altijd gelijk op beide helften. |
| Sfeer | Een paar deeltjes van het thema rond de spelers (vuurvliegjes, sporen, sneeuw, bloesem, as, zielen). Lichtvoorkeuren via lampen per thema. |
| Prestaties | Een Bed Wars-map kost warm ongeveer 10 ms voor het speelgebied en 20–120 ms met landmarks; de tribune 30–80 ms. Alleen de chunks van het speelgebied blijven geforceerd geladen; landmarks laden als gewone chunks. |
| Instellingen | `bedwars.json` `mapStyle` (`arch` of `classic`), `arenas.architecture` en `arenas.ambience`. |
| Controle | `ArenaCheck`: punten, vrije zones, looproutes, lanen, eerlijkheid, niets zwevend. 14 nieuwe JUnit-tests (`ArenaTest`, `SurroundingsTest`), en in de echte server: alle thema's en teamaantallen, een geplaatste map die te voet wordt nagelopen, duelarena's met tribune en symmetrische dekking, plaatjes in het log. |
| Beperkingen | Geen echte screenshots (de renderer gebruikt gemiddelde blokkleuren). De tribune is rond; vierkante arena's krijgen een geplaveid voorplein. Capture the Flag (lang veld) en de handgebouwde maps blijven zoals ze waren. |

## Versie 1.19.1 – Leaderboards 's nachts leesbaar

| Onderwerp | Keuze |
|---|---|
| Oorzaak | Tekst-displays nemen het licht van de wereld over (blok- en hemellicht op hun plek). Na zonsondergang zakt het hemellicht en worden de borden donker. |
| Oplossing | Elke tekst-display van BattleHub (borden, namen van de lobbyfiguren, oefenpop) krijgt een vaste helderheid (blok- en hemellicht 15, de "brightness override" van displays), dus altijd zo licht als overdag. De donkere achtergrond van de borden gaat van 44 % naar 50 % dekking, voor contrast tegen de nachtlucht. De dag-nachtcyclus van de hub zelf blijft zoals hij is. |
| Controle | De testverbinding onthoudt welke helderheid elke display krijgt; een zelftest kijkt of alle borden bij elke testspeler volledig verlicht zijn. |

## Versie 1.20 – Bots verdienen niets, en bots uit bij de wachtrij

| Onderwerp | Keuze |
|---|---|
| Regel | Eén echte speler (of geen) en minstens één bot in een potje: geen coins, geen XP, geen quest-, pas- of achievementvoortgang. De regel staat los van Minecraft in `core/BotRules` (JUnit) en overschrijft `bots.rewardFactor`; die geldt alleen nog als er ook andere mensen meedoen. |
| Toernooien | Een toernooi waarin naast de winnaar alleen bots zaten betaalt geen prijzengeld (winnaar en finalisten), telt geen toernooiwinst en geen weekwinst. De geschiedenis van het weektoernooi noteert het wel. |
| Bots uit: waar | Drie wegen, alle drie dezelfde keuze: een regel in de chat bij het joinen met een klikbare knop **[Geen bots]** / **[Bots aan]**; een schakelaar op de hotbar naast "wachtrij verlaten" (slot 8, naamkaartje = aan, grijze kleurstof = uit) zolang je wacht; `/bots`, `/bots on`, `/bots off`. De keuze staat in het profiel (`noBots`) en blijft bewaard. |
| Bots uit: wat | Duelwachtrij: geen bot erbij. Partylobby en 1v2/1v3: als iemand in de lobby of wachtrij nee zegt komt er geen bot, staan er al bots dan stappen die uit, en de wachttijd begint opnieuw zodra die speler weg is. |
| Waar niet | Ranked en een lopend toernooi vullen nooit met bots; daar verschijnen geen knop en geen chatregel. Oefenen (`/practice`) blijft gewoon werken, dat kies je zelf. |
| Afwijking | De hotbarschakelaar komt in slot 8 (index 7, naast het verlaat-item), behalve als het kompas daar is ingesteld; dan blijft hij weg en zijn chatknop en `/bots` er nog. |
| Controle | JUnit voor de regel (alleen met bots = 0, met andere mensen = het aandeel, zonder bots = alles). Zelftest: een oefenduel tegen een bot geeft 0 coins, 0 XP en geen pas-XP; in ranked geen schakelaar; `/bots off` wordt onthouden, de schakelaar toont "uit" en er valt geen bot in; na de klik op de schakelaar valt de bot wel in. |

## Versie 1.21 – Chaos als fan favorite, honderd events, en drie fixes

| Onderwerp | Keuze |
|---|---|
| Bedden in de lobby | Bedden en respawn anchors ontploffen buiten de overworld en de nether. In de hub- en arenawereld doet gebruik ervan nu niets (een regel in de actiebalk in de lobby). Sluipen met een item in de hand plaatst nog steeds tegen het bed. Zelftest: een echte rechtsklik op een bed in de lobby laat het bed staan. |
| NPC's verder uit het midden | Straal 10,5 → 16 blokken: tussen de fontein (6,6) en de plantenbakken (21 ± 2,6), op vrije vloer. Oude configs met precies de oude standaard schuiven mee (configVersion 4). De bestaande zelftest controleert dat elke plek vrij is. |
| Sneeuwballen | Oorzaak: vanilla gooit sneeuwballen en eieren met 0 schade, en `Player.hurtServer` negeert een hit van 0 helemaal (geen schade, ook geen duw), nog vóór de damage-callback van Fabric. Oplossing: een mixin aan het begin van `Player.hurtServer`. Bij een 0-hit van een sneeuwbal of ei in een match, die de match toestaat, volgt dezelfde hit opnieuw met `combat.snowballDamage` (2 = één hart), dus met terugslag, harnas en alle regels van een gewone hit. In Spleef doet hij geen schade maar duwt hij echt (dat werkte om dezelfde reden ook niet). Zelftest: een sneeuwbal van B op A kost A levens; in Spleef duwt hij A weg zonder schade. |
| Chaos in de normale rotatie | Geen "(test)" meer. Chaos staat in Duel Games direct na The Bridge, met glint en de badge ★ Fan favourite, en zit in de rotatie van de modus van de dag (ook in bestaande configs). Hij blijft wel buiten ranked: toeval speelt te veel mee voor ELO. |
| Honderd events | Een catalogus van precies 100 events in `core/chaos` (zonder Minecraft, JUnit). Een event is een lijst acties: `effect`, `attr` (tijdelijke attribuutwijziging), `give`, `potion` of `do <special>` (27 specials, zoals een glazen brug, bommen, wisselen of kippenregen). Titels en uitleg staan in en/nl. |
| Planning per match | Bij de start: minstens 20 willekeurige events (gewogen: zeldzaam 1, gewoon 3, zonder terugleggen), plus de vaste vijf (parel, lage zwaartekracht, afbrokkelen, spinnenweb, dubbele punten), één in elk vijfde deel van de eerste 15 beurten. Twee events van dezelfde soort volgen elkaar niet op als het anders kan. Elke 10 seconden een event (het eerste na 6 s), dus 25 events in ongeveer 4 minuten spel. Duurt een potje langer, dan volgen events die nog niet geweest zijn; pas na alle 100 begint het opnieuw. Instellingen: `eventSeconds`, `firstEventSeconds`, `randomEvents`. |
| Afwijking | "Minimaal 20 per match" geldt voor de planning: hoeveel je er ziet hangt af van de duur, zoals gevraagd. Een doelpunt beëindigt alle lopende events, zodat elk punt schoon begint. Wie doodgaat krijgt de lopende effecten en lichaamswijzigingen terug voor de resterende tijd. Alle attribuutwijzigingen zijn tijdelijk en worden weggehaald bij het einde van het event, bij een nieuw punt, bij verlaten en bij het einde van de match. |
| Controle | JUnit: 100 geldige events, ze hebben allemaal titels in beide talen, een plan heeft 25 verschillende events met de vaste vijf in de eerste 15, de soorten zijn gemengd, zeldzame events komen minder vaak, en een lang potje herhaalt pas na alle 100. Zelftest: alle ID's bestaan in het spel, Piepklein halveert de grootte, het bouwverbod werkt, alle 100 events tegelijk lopen zonder fouten, en daarna is alles weer normaal (grootte, zwaartekracht, vleugels, kippen). |

## Versie 1.22 – AI-trainingsframework: fase 0 en mijlpaal 1

### Fase 0 – Inventarisatie (wat er al was)

| Onderwerp | Bevinding |
|---|---|
| Platform | Fabric-mod voor Minecraft 26.3 (Java 25 in CI, Mojang-namen), server-side. Bots zijn echte `ServerPlayer`s zonder client (`BotPlayer`): ze zetten dezelfde invoer als een client (zza/xxa, springen, kijken, slaan via het attack-event) en draaien hun eigen physics. Daardoor gelden reikwijdte, cooldown, botsing en alle spelregels vanzelf. |
| Brein | Eén klasse `BotBrain` (761 regels) met per speltype een procedure (`BotStyle`: melee, ranged, runner, parkour, bow spleef, prop hunt). Drie niveaus (`BotLevel`): reactie, richtfout, strafe, w-tap, crits, schild, eten. Doelen per spel via `Match.botObjective`. |
| Navigatie | Eigen A* over blokken (`GridPath`, puur, getest) met stappen op/af en hoeken; `BotPath` koppelt het aan de wereld. Randbeveiliging (`keepSafe`) en losraken (`unstick`). |
| Wiskunde | `BotMath`: kijkhoeken, pijlbaan, spronghoogte en -afstand met de echte zwaartekracht en luchtweerstand (gebruikt door parkour en boog). |
| Gaten | Geen wereldmodel of geheugen (de bot "wist" altijd waar iedereen was, ook achter muren); geen afweging tussen opties (vaste if-ketens); geen persoonlijkheden; geen voorspelling van beweging in melee; geen leren; geen diagnose; Spleef-bots rennen alleen rond (graven niet); parkour faalt willekeurig in plaats van te leren. |

### Architectuur

| Laag | Waar | Inhoud |
|---|---|---|
| Kern (zonder Minecraft, JUnit) | `core/ai` | `Personality` + `Archetype` (8 stijlen), `Difficulty` (6 niveaus), `Track` (waarnemingen per speler: snelheid, voorspelling, vervagende zekerheid, strafe-wissels), `OpponentModel` (sessie: agressie, voorkeursafstand, strafe- en springgedrag; begrensde lopende gemiddelden), `OutcomeMemory` (wat werkte per tactiek), `Situation` (de waarneming), `CombatPolicy` (utility-scores met redenen), `Decider` (hysterese, onderbrekingen, bewuste fouten, overgangen), `CombatSteering` (tactiek → invoer), `CombatMind` (alles samen, reactietijd, denkritme, prestatiemeting, rapport). |
| Waarneming | `bot/Perception` | Bouwt de `Situation` uit de wereld met eerlijke regels (zie hieronder), randen in 8 richtingen, inkomende projectielen, reikwijdte uit het echte attribuut. |
| Uitvoering | `bot/BotBrain` | Eén intentie per tick, uitgevoerd met de bestaande primitieven (route, kijken met richtfout, slaan via het attack-event, schild, eten); `keepSafe` houdt het laatste woord bij randen. |
| Beheer | `/battlehub bots inspect/profile/debug` | Diagnose per bot, stijl wisselen, beslissingen loggen voor één bot. |

### Eerlijkheid (wat een bot mag weten)

| Regel | Waarom |
|---|---|
| In zicht (zichtlijn, binnen 48 blokken, niet onzichtbaar): positie, beweging, levens, zwaaien | Wat een speler ziet. |
| Buiten zicht maar niet sluipend en niet onzichtbaar: alleen de positie (naamlabel) | Vanilla tekent naamlabels door muren, behalve bij sluipen. |
| Gloeiend: altijd te zien | Zo werkt het effect. |
| Anders: alleen geheugen, met zekerheid die elke 3 s halveert | Laatst gezien. |
| Levens buiten zicht: onbekend (aangenomen vol) | Niet te zien. |
| Geen extra levens, schade of bereik; slaan alleen binnen het echte reikwijdte-attribuut en met zichtlijn | De moeilijkheid zit in beslissingen. |

### Mijlpaal 1 (deze versie) – wat gebouwd en hoe geverifieerd

| Onderdeel | Status |
|---|---|
| Kern `core/ai` | Gebouwd. 39 JUnit-tests: configuratie en validatie, voorspelling en geheugen, scores per scenario (rand achter je = noodgeval, genezen of terugtrekken bij weinig levens, counter wacht en straft, gemiste zwaai bestraffen, mislukte tactiek laten vallen, geduld raakt op tegen een passieve speler), beslisser (geen dithering, veiligheid onderbreekt, fouten nooit boven veiligheid, lagere niveaus vergissen zich vaker), sturing (alleen slaan in bereik/zicht/opgeladen, nooit naar een rand strafen, terugtrekken, vooruit mikken). |
| Duelsimulatie | Een vereenvoudigd duel op een lijn tegen een gescripte tegenstander: rusher drukt aantoonbaar meer, counter houdt aantoonbaar meer afstand; expert ruilt beter dan beginner; zelfde seed = zelfde gevecht; denken kost warm ~3 tot 7 µs (test: onder 100 µs). |
| MC-integratie | Melee (ook dichtbij in boog-modi) loopt via de mind. Schade gaat terug naar de mind (leren). Zelftest op een echte server: de mind beslist en legt uit, wisselt van tactiek, het profiel-commando maakt er een rusher van, inspect toont het, debug schakelt aan en uit; de bestaande tests (bot komt naar je toe, raakt je, ranked zonder bots, party-fill) blijven gelden. |
| Gevonden via de zelftest | Het tegenstandermodel noemde een stilstaande testspeler agressief (0,61), omdat het de afnemende afstand mat, ook als de bot zelf naderde. Nu telt alleen de eigen snelheid van de tegenstander richting de bot, en alleen buiten zwaardafstand (daar valt niets meer te naderen). JUnit-test toegevoegd. Ook: in 26.3 bestaat `getAttackAnim` niet; een zwaai wordt gezien als het wegvallen van de aanvalslading van het doelwit (een aanval reset die en is voor iedereen zichtbaar). |
| Afwijking | `BotLevel` (makkelijk/normaal/moeilijk) blijft de keuze in menu's en config; daarachter liggen casual/intermediate/expert. Beginner, advanced en master bestaan in de kern maar zijn nog niet kiesbaar in menu's. Andere stijlen (parkour, runner, prop hunt, boog op afstand) gebruiken nog het oude brein. |

### Volgende fasen (nog niet gebouwd)

| Fase | Inhoud |
|---|---|
| 2 – Combat verdiepen | Boog en kruisboog in de mind (afstand kiezen, dekking, trekken op het juiste moment), wapenkeuze, doelwisseling met meer tegenstanders, gevechtsstatistieken na afloop voor training. |
| 3 – Spleef | Speelveld-topologie (welke blokken er nog zijn), veilig staan, blokken kiezen onder de tegenstander op basis van voorspelling, eigen ontsnappingsroute bewaren. |
| 4 – Parkour | Sprongplanner met `BotMath`-simulatie over kandidaat-sprongen, landingskeuze, mislukte sprongen onthouden en een alternatief kiezen. |
| 5 – Adaptief | Opslaan van leren over sessies (instelbaar, uit te zetten en te wissen), adaptieve moeilijkheid (transparant, uit te zetten). |
| 6 – Meer modi | KOTH, CTF, Bed Wars en Bridge via dezelfde mind met eigen doelen en rollen. |
| 7 – Release | Profilering met veel bots, lastgrenzen, in-game visualisatie van paden en doelen. |

## Versie 1.23 – AI mijlpaal 2: niveaus, boog, doelwit, nabespreking

| Onderdeel | Keuze en controle |
|---|---|
| Zes niveaus kiesbaar | De bot draagt nu een `Difficulty` (beginner .. master); het oude `BotLevel` volgt eruit voor de stijlen die nog niet via de mind lopen. Oefenmenu: zes knoppen; `/practice <modus> <niveau>` met de zes namen (en nog steeds easy/normal/hard); `bots.fillLevel` accepteert ze ook. JUnit: elk oud niveau komt terug via `legacy()`. |
| Boog in de mind | Nieuwe tactiek SHOOT. Alleen een boog: melee scoort laag, schieten hoog, dichtbij achteruit lopen terwijl hij spant (kiten). Boog en zwaard: schieten op afstand, slaan dichtbij; een rusher komt vanaf middenafstand toch naar je toe. Mikken: borst, vooruit over de echte vliegtijd van de pijl (`BotMath.arrowTicks`), zo ver als het niveau kan inschatten (beginner niet, master tot 22 ticks); de wereldlaag voegt de boog en de richtfout toe. Zonder zichtlijn zoekt hij eerst (geen pijlen in dekking). Boog-modi lopen nu volledig via de mind. |
| Doelwit kiezen | `core/ai/Targeting`: afstand, zicht (gezien beter dan naamlabel beter dan herinnering), wie je net raakte (terugvechten), zwak doelwit afmaken (vooral agressieve en opportunistische stijlen), huidig doelwit iets voorrang (niet wisselen om niets). JUnit voor elk van die regels. |
| Nabespreking | Na een match met bots krijgt elke speler van de bot met wie hij het meest uitwisselde: schade over en weer, het hoofdplan van de bot (aandeel per tactiek), wat de bot van jou zag (agressie, afstand, strafe-wissels) en één tip (vaker van richting wisselen, geduld, druk zetten, afstand, volgend niveau, of ruilen met volle lading). `core/ai/Debrief` geeft taalsleutels terug (en/nl), JUnit; zelftest: na het oefenduel ontvangt A de nabespreking. |

## Versie 1.24 – AI mijlpalen 3, 4 en 5: Spleef, parkour, leren over sessies

Afwijking van het faseplan: mijlpaal 3, 4 en 5 komen samen in één release (1.24) in plaats van drie (1.24, 1.25,
1.26), omdat ze in hetzelfde brein-bestand zitten en één CI-ronde (bouwen plus zelftest op een echte server) een
half uur kost. Objectmodi worden 1.25, budget en visualisatie 1.26. Het bruggenbouwen (oorspronkelijk mijlpaal 3)
schuift naar 1.25, bij de objectmodi waar het nodig is (Bridge, Bed Wars).

| Onderdeel | Keuze en controle |
|---|---|
| Spleef-planner | `core/ai/SpleefPlanner`: het veld als raster (sneeuw of gat) rond de bot, tegenstanders op hetzelfde veld, bereik uit `BLOCK_INTERACTION_RANGE`. Graven: het laatste blok onder iemand die stilstaat, waar iemand naartoe loopt als hij rent (zo ver vooruit als het niveau kan voorspellen). Nooit de eigen steunblokken, niet direct naast zichzelf, en nooit een blok waardoor de bot op een eilandje van minder dan acht blokken komt te staan. Staan: stevige grond, op graafafstand, niet naast iemand die onder je kan graven; de stijl bepaalt hoe dicht (agressie) en hoeveel risico. JUnit: 6 tests. |
| Spleef in de wereld | Nieuwe botstijl `SPLEEF`: het veld wordt elke denkbeurt opnieuw gelezen (25×25), alleen zichtbare of niet-sluipende tegenstanders tellen, de bot kijkt naar de bovenkant van het blok, graaft pas als hij er op een paar graden na op richt en het blok echt kan zien (dezelfde clip als een klik), met een tempo dat bij het niveau hoort, via dezelfde regel als een speler (`onBreakBlock`: sneeuwbal erbij). Sneeuwballen naar iemand die naast een gat staat. Zelftest: drie spelers (een echte, twee bots), een bot krijgt sneeuwballen (heeft dus gegraven) en geen bot valt. |
| Spelerbeweging | `core/ai/PlayerSim`: de beweging van een speler met de formules van het spel (versnelling `0,98 × 0,216 / wrijving³`, behoud `wrijving × 0,91`, in de lucht 0,02/0,026 en 0,91, sprong 0,42 plus 0,2 met sprinten, zwaartekracht 0,08, 0,98). JUnit: lopen 0,216 en sprinten 0,281 blok per tick, een sprintsprong vliegt ongeveer 3,6 blokken. |
| Sprongplanner | `core/ai/JumpPlanner`: per sprong vijf manieren (sprint lang/kort, lopen lang/kort, sprint uit stand), elk gesimuleerd van aanloop tot landing; de manier met de meeste ruimte op de landing wint. Mislukte manieren worden per sprong onthouden en overgeslagen; als alles een keer mislukte, begint hij opnieuw. JUnit: gat 1 tot 4 haalbaar, 5 niet; één omhoog met gat 2 wel, met gat 4 niet; anderhalf omhoog niet; faalgeheugen; landingsmarge. |
| Parkour in de wereld | De bot loopt naar het startpunt van de aanloop (eerst draaien, dan lopen: op een platform van één blok is een zijstap een val), staat stil, kijkt de sprongrichting in, rent en springt op de tick die het plan zegt (lagere niveaus soms een tick te vroeg of te laat), stuurt in de lucht bij naar de landing en remt na de landing met de bewegingstoetsen (ijs). Geen `setDeltaMovement` meer: alleen invoer zoals een speler. Een gemiste sprong wordt onthouden (`inspect` toont het). De bestaande zelftest (bot haalt platform 3) blijft. |
| Leren over sessies | `core/ai/PlayerKnowledge`: per speler een paar voortschrijdende gemiddelden van het tegenstandermodel (naderen, afstand, strafe- en springfrequentie), welke tactieken tegen hem werkten, en de laatste acht resultaten tegen bots. Een nieuwe bot begint daarmee (als bekend, met een gewicht dat met het aantal gevechten groeit). `bot/AiMemory` houdt het in het geheugen en in `battlehub/ai/<uuid>.json` (config `bots.learning`, `bots.learningPersistent`; uit = elk gevecht begint blanco). Waarden uit een met de hand bewerkt bestand worden begrensd. |
| Transparant en terug te draaien | `/bots memory` laat zien wat bots over je onthouden (stijl, resultaten per niveau, het adaptieve niveau); `/bots forget` wist het (geheugen en bestand); admins: `/battlehub bots forget <speler\|all>`. |
| Adaptief niveau | Knop *Adaptief* in het oefenmenu (middelste vak) of `/practice <modus> adaptive`: drie van de laatste vier gevechten op het huidige niveau gewonnen is een niveau omhoog, drie verloren een omlaag; na elke stap een chatregel met de reden. Een vast niveau kiezen zet het weer uit. JUnit: omhoog, omlaag, alleen resultaten op het huidige niveau tellen, begrenzen. |
| Gevonden in de CI van 1.23 | (1) De nabespreking bleef leeg: de zelftest geeft de bot halverwege een andere stijl, en dat maakte een nieuwe mind zonder de schade tot dan toe. Nu neemt een stijlwissel alles over (`CombatMind.inherit`): wat hij zag, leerde en hoe het gevecht ging. Ook de dodelijke klap telt nu mee (die slaat de after-damage-gebeurtenis over). (2) De oude parkour-bot (snelheid zetten op een berekende sprong) kwam in die run niet van de start; vervangen door de sprongplanner. |
| Zelftest | Oefenduel: na de overwinning staat `W:expert` in het geheugen, het bestand bestaat, `/bots memory` toont het; `/bots forget` wist geheugen en bestand. |
| Testinfrastructuur (sinds 1.21) | De testspelers zijn nep-verbindingen zonder client; de server laadt daarom geen chunks rond ze. `debug/TestPlayer` verplaatst ze elke tick in de chunk-administratie, zodat chunks rond ze geladen blijven (anders zien bots en NPC-hitboxen de wereld niet). De NPC-test gebruikt bovendien geforceerde chunks als terugvaloptie en logt wat hij vond. |

### Status van de fasen

| Fase | Status |
|---|---|
| 0 – Inventarisatie | Klaar (1.22). |
| 1 – Kern en melee | Gebouwd, JUnit, zelftest (1.22). |
| 2 – Niveaus, boog, doelwit, nabespreking | Gebouwd, JUnit, zelftest (1.23; nabespreking gerepareerd in 1.24). |
| 3 – Spleef | Gebouwd, JUnit, zelftest (1.24). |
| 4 – Parkour | Gebouwd, JUnit, zelftest (1.24). |
| 5 – Leren en adaptief | Gebouwd, JUnit, zelftest voor geheugen en wissen (1.24); de adaptieve stap zelf alleen in JUnit. |
| 6 – Objectmodi en bruggen bouwen | Gepland voor 1.25. |
| 7 – Budget, metrics, visualisatie | Gepland voor 1.26. |

## Versie 1.25 – AI mijlpaal 6: objectmodi via de mind, bruggen bouwen

| Onderdeel | Keuze en controle |
|---|---|
| Doelen als kern-type | `core/ai/Objective`: vier soorten. ATTACK: ergens heen, alleen vechten wat in de weg staat. HOLD: binnen een zone staan en daar vechten. DEFEND: bij een post blijven en vechten wie dichtbij komt. CARRY: iets naar huis brengen en gevechten vermijden. Met straal (wanneer je er bent) en lijn (vijanden verder dan dit van het punt worden met rust gelaten). `Situation` draagt het doel in plaats van een los punt. |
| Beleid | `CombatPolicy` weegt het doel per soort. Een vlagdrager rent naar huis: naderen, zoeken, afstand houden en schieten kosten veel, slaan alleen wie in de weg staat. Op de heuvel vecht hij wie bovenkomt, maar jaagt niemand eraf ("too far from the hill"). Naast de heuvel gaat hij er eerst op. Een wacht blijft op post tot iemand binnen de lijn komt, en gaat terug als hij te ver weg is. Elke regel heeft een reden die `inspect` laat zien. Sturing: op de heuvel of post blijft hij staan (op de heuvel een beetje heen en weer), anders loopt hij erheen. |
| Rollen | `core/ai/Roles`: alleen bots krijgen een rol. Een team met iets te verdedigen en minstens twee spelers houdt één bot thuis, twee vanaf vier spelers: wie het best past (defensief, geduldig, niet agressief), bij gelijkspel wie het dichtst bij huis is, daarna het id. Zo is de verdeling stabiel van denkbeurt tot denkbeurt. Een team van alleen bots houdt altijd een aanvaller. Met een mens in een duo blijft de bot thuis en valt de mens aan (keuze: een bot weet niet wat de mens van plan is). |
| Modi | KOTH: HOLD op de heuvel. CTF: CARRY met de vlag; DEFEND voor de verdediger zolang de eigen vlag thuis is; anders ATTACK op de vijandelijke vlag (of de eigen drager volgen). Bed Wars: eerst blokken (ijzer bij de eigen generator, wol bij de eigen winkel, via de winkel zelf, dus dezelfde prijs als een speler), dan DEFEND van het bed of ATTACK op het dichtstbijzijnde vijandelijke bed. Zonder bedden: jagen. Bridge: ATTACK op het doel; in 2v2 houdt één bot het eigen doel (DEFEND). |
| Bruggen bouwen | `core/ai/Bridging`: de vloercellen van de bot naar het doel, alleen in stappen langs x of z (een blok gaat altijd tegen het vlak van een ander), en de eerste cel zonder vloer met de cel waartegen hij komt. In de wereld: naar het laatste vloerblok, sluipend naar de rand (de rand houdt een sluipende speler vast, met zijn midden net voorbij de rand, zodat hij de zijkant eronder ziet), kijken naar de zijkant, en plaatsen via `useItemOn`, dus via dezelfde regels als een speler (bouwgebied, beschermde blokken, het blok gaat uit de inventaris). Het plaatsen gebeurt alleen als de echte kijkstraal binnen bereik dat vlak raakt, en niet sneller dan het niveau toelaat. Sluipen vertraagt de toetsen (dat doet de client van een speler, voor een bot gebeurt het hier). JUnit: lijn langs vlakken (recht, diagonaal, achteruit, begrensd), eerste gat tegen de laatste vloer, niets buiten het bouwgebied. |
| Doorbreken naar het bed | Een bot die bij het doelbed is, mijnt wat echt in de weg zit: het bed, of een door spelers geplaatst blok eromheen (`botMayBreak`). Dat duurt even lang als voor een speler met hetzelfde item (`hardheid × 30 / snelheid`, of `× 100` zonder het juiste gereedschap). Daarna beslist de eigen breekregel van het spel. Kaartblokken worden niet gebroken: dan loopt hij eromheen. |
| Zelftests | KOTH: een bot staat op de heuvel en `inspect` zegt "goal: hold the hill". Bridge: het pad tussen de eilanden wordt weggehaald en de bot bouwt zelf over (minstens 3 blokken). Bed Wars solo tegen een bot: hij koopt wol met ijzer van zijn generator en bouwt richting het andere eiland. Elke 15 seconden logt de test wat de bot doet, zodat een fout te volgen is. |
| JUnit | `ObjectiveTest`, 12 tests: vlagdrager (naar huis, toch slaan wie blokkeert), heuvel (erop, vechten op de heuvel, niet eraf jagen), wacht (op post blijven, terug naar post), aanvallen zonder tegenstand, rollen (meest defensieve blijft thuis, onafhankelijk van volgorde, teamgrootte, niets te verdedigen, twee verdedigers vanaf vier), en bruggen bouwen. |

## Versie 1.26 – AI mijlpaal 7: denkbudget, metrics, visualisatie

| Onderdeel | Keuze en controle |
|---|---|
| Denkbudget | `core/ai/ThinkBudget`: hoort elke tick hoe lang ticks duren (gemiddeld) en hoeveel bots er zijn. Boven `bots.slowAboveMspt` (35 ms) gaat de tijd tussen denkbeurten ×2, ×3, ×4 (per 5 ms één stap). Omhoog gaat dat meteen, omlaag stap voor stap en pas met 2 ms marge, zodat het rond de grens niet heen en weer springt. Daarnaast een maximum aantal denkbeurten per tick voor alle bots samen (minstens 3, anders de helft van het aantal bots gedeeld door de rek): wat er niet meer in past, wacht een tick en houdt zolang zijn plan. Gevaar (een rand achter je met de tegenstander dichtbij, een pijl, een nieuw doelwit) vraagt niets en denkt altijd meteen. De Spleef-scan en de randmeting rekken mee. |
| Tickmeting | Zonder versie-afhankelijke API: het begin van de servertick (Fabric START) en het moment dat de bots aan de beurt zijn in END. Dat is vrijwel de hele tick. |
| Metrics | `/battlehub bots`: bots online en in een match, denkbeurten per seconde (alle bots samen), gemiddelde en langste denkbeurt in µs, uitgestelde denkbeurten, tickduur en de huidige rek. Uit goedkope tellers in de mind (geen volledig rapport per bot). |
| Visualisatie | `/battlehub bots show <bot>`: elke 5 ticks deeltjes die alleen die beheerder ziet. End rods voor de route, groen voor waar hij heen gaat (ook de geplande landing van een sprong, de plek in Spleef), crits voor waar hij op inwerkt (lijn naar het doelwit, het blok dat hij graaft, mijnt of bouwt, het afzetpunt), vlammen in een ring om het doel. Tactiek en reden staan boven de hotbar. De methode om deeltjes naar één speler te sturen verschilt per versie (één vlag meer of minder) en wordt daarom één keer opgezocht. Zonder die methode wordt niets getoond, en er verandert niets voor anderen. |
| JUnit | `ThinkBudgetTest`, 6 tests: snelle ticks normaal; trage ticks rekken tot het maximum; stapsgewijs herstel zonder heen-en-weer (wisselend 33/39 ms: hoogstens 2 wissels); maximum per tick en tellers; een mind houdt zijn plan als het budget nee zegt, maar denkt bij gevaar toch; rek 3 denkt minder dan half zo vaak. |
| Zelftest | Party-fill (drie bots in Sumo): de metrics tellen denkbeurten en `/battlehub bots` geeft drie regels (gelogd). Oefenduel: A kijkt mee met de bot, de bot blijft denken, en meekijken gaat weer uit. Of de deeltjes er echt zijn kan de server niet zien (dat is client-kant); wel dat het meekijken de bot niet stoort. |

### Status van de fasen (bijgewerkt)

| Fase | Status |
|---|---|
| 6 – Objectmodi en bruggen bouwen | Gebouwd, JUnit, zelftests KOTH, Bridge (bouwen) en Bed Wars (wol kopen, bouwen) (1.25). CTF-dragers en verdedigers: JUnit voor het beleid, geen eigen zelftest. |
| 7 – Budget, metrics, visualisatie | Gebouwd, JUnit, zelftest (1.26). Belastingtest met veel bots: niet als zelftest. Party-lobby's vullen tot 4. Het denken kost in de duelsimulatie ~3 tot 7 µs per beurt, dus 20 bots die elke 3 ticks denken kosten samen minder dan 0,1 ms per tick. |

## Versie 1.27 – Menselijke AI: review, richten, randen, zintuigen, echt leren

Aanleiding: de bots hadden een "lock" (elke klik binnen bereik raakte), vielen regelmatig in de void, en de vraag
was of de adaptieve modus echt leert.

### Review: wat er mis was

| Bevinding | Oorzaak in de code |
|---|---|
| Elke klik raakt | `execute` sloeg zodra het doelwit binnen bereik en in zicht stond. Waar de bot keek deed er niet toe: de richtfout was alleen cosmetisch. |
| Klikken op het perfecte moment | Een klik precies op de tick dat de lading de drempel haalde. Beginners wachtten zelfs op 100%, wat onnatuurlijk is (beginners spammen). |
| Strafen als een metronoom | Elke tick een vaste kans om om te draaien: een geometrische verdeling, soms één tick lang. |
| Ogen in het achterhoofd | Zicht was alleen een zichtlijn, zonder gezichtsveld. Naamlabels werkten ook achter de rug. |
| Vallen in de void | Springen keek nooit naar de landing: crit-sprongen, sprongen bij vastlopen, opstapjes. In de lucht na een klap werd niets gedaan. Bij nul invoer remde de randcontrole niet tegen glijden. Hij keek altijd 1,1 blok vooruit, ook bij sprintsnelheid. De randmeting voor de beslissingen werd maar elke denkbeurt ververst (bij beginners 10 ticks, dan heeft de bot al tot 2,8 blok gelopen). |
| "Leren" deed weinig | Het tegenstandermodel stuurde maar één score: afstand houden tegen agressieve spelers. Afstand, strafe-frequentie en springen werden alleen in de nabespreking gebruikt. |
| Adaptief | Alleen "3 van de laatste 4 op het huidige niveau": uitslagen op andere niveaus, de marge en gevechten buiten het oefenen telden niet. In de simulatie zat na 20 gevechten maar 43 tot 79% van de spelers op het juiste niveau, en het systeem schommelde. |

### Wat er nu is

| Onderdeel | Keuze en controle |
|---|---|
| `core/ai/AimModel` | De crosshair als hand. Reactievertraging (2 tot 5 ticks), smooth pursuit (de hand draait mee met het doelwit), een correctie op de fout, een begrensde en afgevlakte draaisnelheid, een trage slinger die nooit weggaat, overschieten bij een flick (draai > 25°) en verstoring door een klap. Per niveau één parameterset, oplopend. Gemeten (JUnit, doelwit op 2,8 blok dat strafet): sprint-strafe 21 / 45 / 53 / 61 / 72 / 79% op het doel van beginner tot master, walk-strafe 48 tot 86%, stilstaand 98 tot 100%. |
| Echte treffer | Een klik raakt alleen als de kijkstraal binnen het bereik (`ENTITY_INTERACTION_RANGE`) de hitbox raakt, zonder blok ertussen (`AABB.clip` plus een blok-clip). Een misser is een zwaai in de lucht en kost de lading. |
| `core/ai/Clicker` | Elke klik wacht op zijn eigen lading: rond `readyToHit` met spreiding (beginner 55% ± 20%, master 94% ± 4%). De vinger is wat laat (een kans per tick: 30 tot 88%). Een zwaai kan net te ver beginnen (beginner tot 0,7 blok, master 0,1). JUnit: beginners spammen, experts wachten, de drempel verschilt per klik, lagere niveaus zwaaien van verder. |
| Strafe-ritme | Een toets wordt een tijd vastgehouden: 5 tot 14 ticks bij hoge strafe-vaardigheid, tot ruim 40 bij lage. Nooit naar een rand: dan draait hij om met een nieuwe houdtijd. |
| `core/ai/Senses` | Gezichtsveld van 65° vanaf de kijkrichting. Naamlabel alleen op het scherm en niet bij sluipen. Horen: wie binnen 12 blokken loopt of rent (niet sluipend of stilstaand), en wie de bot de laatste 30 ticks raakte. Of iemand loopt, komt uit de positie van de vorige tick: de servervelocity van echte spelers is niet betrouwbaar. Ziet hij niemand, dan kijkt hij eerst rond en loopt dan pas wat. |
| `core/ai/Falls` | Een sprong of vlucht met de bewegingsformules van het spel (`PlayerSim`), tot de voeten (midden en hoeken) op een oppervlak landen of te diep vallen. Voor elke sprong buiten parkour: geen sprong die in de void eindigt. In de lucht, als de vlucht in de void eindigt: toetsen terug naar de laatste grond, na een korte reactietijd. Beide met een kans per niveau (`edgeCare` 45 tot 98%), want ook mensen vergeten het soms. JUnit: sprong naar de rand slecht en de andere kant op goed; na een klap (0,4 opzij en omhoog) redt terugsturen het, niets doen niet; een opstapje of water is goed, een diepe val niet. |
| Randcontrole | Bij een rand wordt de grond rondom elke tick gemeten. Hij kijkt verder vooruit naarmate hij sneller gaat (1,1 + 3 × snelheid boven 0,15). Glijdt hij na een klap naar een rand, dan houdt hij de toetsen tegen de glijrichting in. |
| Leren dat ertoe doet | Het tegenstandermodel leert nu ook het slagritme (ticks tussen slagen: spammen onder 8,5, getimed vanaf 11) en de raakkans van de speler. Tegen een spammer: ruilen met volle slagen ("they spam weak hits"). Tegen een getimede speler: afstand houden en straffen. Tegen iemand die veel mist: drukken. Tegen iemand die zelden mist: afstand. Het richten past zich aan hoe voorspelbaar iemand strafet (gemeten, JUnit): tegen een grillige strafer 3 tot 7 procentpunt meer raak op elk niveau, tegen een gelijkmatige strafer veel meer bij lagere niveaus (beginner 36 → 58%) en neutraal bij hogere. Alles wordt per speler onthouden en aan de volgende bot meegegeven. |
| Adaptieve rating | `PlayerKnowledge`: een Elo-achtige rating uit **alle** gevechten tegen bots (oefenen, wachtrij-bots, party-games). Een duel tegen één bot telt 1, een gevecht met meer spelers 0,5. Score = 75% winst + 25% schadeverhouding: een nipt verlies kost minder dan een pak slaag. De niveaus staan op 700 tot 1700 (200 uit elkaar). Het adaptieve niveau is waar de verwachte winkans het dichtst bij 55% ligt, met een kleine marge tegen heen-en-weer. De K-factor is 100, dan 60, dan 40, en groter zolang uitslagen steeds dezelfde kant op afwijken van de verwachting (dan zit de schatting ernaast). Nieuwe spelers starten op hun ranked ELO (de helft van het verschil met de start-ELO, begrensd 800 tot 1500), anders op 1050 (intermediate). |
| Gegevens van alle matches? | Ja voor alle gevechten tegen bots: die zeggen iets over je sterkte tegen bots. Gevechten tegen mensen alleen als startpunt (ranked ELO): winnen van mensen zegt weinig over welk botniveau past, en mensen-ELO is een andere schaal. |
| Meting adaptief | Simulatie (200 gesimuleerde spelers per sterkte, uitslag volgens de verwachte winkans): na 20 gevechten zit 76 tot 100% op het beste niveau (oud: 43 tot 79%), na 30 gevechten 83 tot 100% (oud: 35 tot 77%). Zwakte: heel sterke spelers (sterker dan master) hebben eerst meer gevechten nodig (na 10 gevechten 37% tegen 76% oud). Daarvoor dient het startpunt uit ranked. JUnit: de rating zit na 30 gevechten gemiddeld binnen 170 punten en het niveau geeft een echt gevecht; weging en marge; omhoog en omlaag met reden. |
| Zichtbaar | De nabespreking zegt hoeveel slagen de bot raakte. `/bots memory` toont je rating, het verwachte winpercentage op het adaptieve niveau en hoe je klikt. De chat legt elke stap uit (rating voor en na, verwachte kans). Inspect toont slagen en raakpercentage van de bot, en van de tegenstander slagritme en raakkans. |
| Zelftests | Oefenduel: A strafet (0,22 blok per tick, wisselend) terwijl de bot slaat; na 15 slagen moet hij minstens één keer geraakt en één keer gemist hebben (gelogd met percentage). Na de overwinning is de rating gestegen. Bridge (master-bot): de bot staat anderhalf blok van de noordrand en krijgt een klap richting void (0,4 opzij, 0,42 omhoog). In de lucht moeten zijn toetsen terug naar het eiland wijzen; waar hij landt wordt gelogd. |

### Versie 1.27.1 – wat de zelftest van 1.27.0 liet zien

| Bevinding | Oplossing |
|---|---|
| Een wissel naar "afstand houden" had soms geen reden (`strike -> space:` zonder tekst). De controle "elke wissel heeft een reden" faalde, en dat is ook een gat in de uitlegbaarheid. | Afstand houden heeft altijd een reden: "recharging (x%)" of "holding x blocks". De JUnit-test "elke score legt zich uit" geldt nu ook voor afstand houden (de uitzondering is weg). |
| In de wereld raakte de expert een stilstaande speler maar 3 van de 4 keer, en een strafende 1 van de 3 (model: ~100% en ~70%). De eigen beweging van de bot (strafen, w-tap, springen) veranderde de kijkhoek, en het model volgde dat met dezelfde vertraging als beweging van het doelwit. | `AimModel.stepAt`: richten vanuit posities. Waar het doelwit even geleden was (doorgetrokken met zijn eigen snelheid) wordt bekeken vanuit waar het oog nu is. De vertraging geldt dus alleen voor het doelwit, want een speler weet hoe hij zelf beweegt. JUnit: de bot strafet zelf rond een stilstaand doelwit en blijft meer dan 85% van de tijd op het doel (intermediate en expert). |
| Inspect toonde standaardwaarden voor het slagritme van iemand die nog niet geslagen had ("swings every 12 ticks, lands 60%"). | "swings not seen yet" tot er minstens 3 slagen gezien zijn. |


## Versie 1.28 – Kits, bestaande paden, het wapen van het gevecht

| Melding | Oorzaak | Oplossing |
|---|---|---|
| "De kits zijn niet meer hetzelfde" | (1) Oefenen in 1v2/1v3 gaf altijd de zwaardkit, zowel via het menu als via `/practice`, wat je ook koos (in het PvP-tabblad kies je zwaard, bijl of mace). (2) De klei van The Bridge werd na de kit apart op vak 4 en 5 gezet, buiten je indeling uit de kit-editor om. Stond daar al iets, dan kwam de klei op een willekeurig vrij vak. (3) Kreeg een kit er een item bij, dan was je hele indeling voor die kit ongeldig en kreeg je de standaard terug. | (1) Het oefenmenu vraagt eerst de kit (`PracticeKitMenu`). `/practice 1v2 <niveau> <kit>` en `/practice 1v2 mace` werken; zonder kit opent de kitkeuze. (2) De klei hoort nu bij de Bridge-kit (`{team}_terracotta`, ingevuld met rood, blauw, lime of geel via `kitTeam`), achteraan toegevoegd. De kit-editor toont hem en je indeling zet hem waar jij wilt. Een bestaande `kits.json` krijgt de klei er automatisch bij. (3) `KitLayout.resolve`: een indeling van vóór de nieuwe items blijft gelden; nieuwe items komen op hun eigen vak of anders het eerste vrije. JUnit voor (3); zelftest: een oude Bridge-indeling plus klei, en klei verplaatst in de editor komt op het nieuwe vak. |
| Bots bouwen een pad terwijl er al een is | Bruggen bouwen keek alleen of de rechte lijn naar het doel een gat had binnen twee blokken. Een bestaande route (het smalle pad van The Bridge, een gebouwde brug in Bed Wars) telde niet. | Eerst de routeplanner (A* over de echte blokken). Is er een route, dan loopt de bot die; bouwen alleen waar er geen weg is. Een zoektocht zonder resultaat (de dure) wordt hooguit elke seconde herhaald (elke kwart seconde als het doel verschoof) en niet meer elke tick. Zelftest: met het pad erbij bouwt de bot 10 seconden lang niets; zonder pad bouwt hij weer zelf over. |
| Geen voorkeur voor het wapen van het gevecht | De bot wist niet welk wapen een gevecht draaide. In een boogduel (boog plus ijzeren zwaard) was de boog binnen 6 blokken "te dichtbij" en won melee. Een mace sloeg hij als een zwaard: geen sprongaanvallen, geen windladingen. Voor melee pakte hij het eerste zwaard, de eerste bijl of mace in de hotbar. | `core/ai/Weapon` uit de kitnaam (`Match.botWeapon`). Boogduel: schieten +0,35 ("a bow fight"), pas onder 3,2 blokken "te dichtbij", naderen boven 6 blokken −0,3, slaan boven 2,5 blokken −0,15, achteruit om ruimte te maken +0,2. Mace: vaker sprongaanvallen (vallende klap), en af en toe (niet vaker dan elke 3 s, alleen dichtbij, geladen, op de grond en ver van een rand) een windlading recht naar beneden: de bot vliegt omhoog en komt met de smash op je neer. Melee kiest eerst het wapen van het gevecht. JUnit: boog wint in een boogduel op 5 blokken (en telt veel minder in een gemengde kit), het zwaard komt er vlak bij nog uit, lanceren alleen onder de juiste voorwaarden en met tussenpozen. Zelftests: in het boogduel schiet de bot meer dan eens; in het mace-duel gooit hij een windlading, vliegt hij meer dan een sprong hoog en vecht hij met de mace in de hand; 1v2-oefenen met de mace-kit geeft iedereen een mace. |

### 1.28.1 – CI-zelftests en twee parkourfouten

De zelftests van 1.28.0 vonden drie fouten. Twee zaten in de tests zelf. (1) De boogcheck keek direct na de eerste pijl. Hij wacht nu tot de bot opnieuw schiet en houdt A in leven. (2) In de Bridge-test liep de master-bot tijdens de 10 seconden met het pad al naar het andere eiland. Na het weghalen van het pad hoefde hij dus niets meer te bouwen. Hij gaat nu eerst terug naar zijn eigen eiland (`Match.home`).

De derde was een echte bug. Parkourbots konden geen ladders beklimmen: in de lucht lieten ze alle toetsen los, en een ladder zonder toetsen glij je af. In easy-banen zit in ongeveer een kwart van de runs een ladder in de eerste vier sprongen. Nu duwt de bot op een ladder vooruit en houdt hij springen ingedrukt (`climb`), en bovenaan stapt hij het platform op.

Bij het zoeken kwam nog een latente fout boven. `brake()` gaf volle toetsen tegen een trage glijbeweging in. Bij ongeveer 0,046 blokken per tick schoot hij dan steeds door naar de andere kant en terug, en kwam hij nooit onder de stilstandgrens: de bot bleef staan wachten op een sprong. De rem doseert nu naar de snelheid. De parkourlog in de zelftest toont voortaan de eerste vijf platforms (stijl, plek) en de inspect-regels van de bot.


## Versie 1.29 – Toeschouwers en kisten, een grote SkyWars

| Melding | Oorzaak | Oplossing |
|---|---|---|
| "Spectators kunnen in de kisten en dingen pakken" | Toeschouwers, uitgeschakelde spelers en wie wacht op respawn staan in de vanilla toeschouwermodus. Daarin mag je een kist openen om erin te kijken. Fabric roept `UseBlockCallback` aan vóór die toeschouwercontrole, en BattleHub hield toeschouwers daar niet tegen. | `Locks.spectatorBlocked`: een toeschouwer in de werelden van BattleHub gebruikt geen blokken (`UseBlockCallback` geeft FAIL). Opent er toch een kist (een ander pad, een mod), dan weigert `Locks.blocksClick` elke klik in dat menu, behalve in de eigen inventaris en de menu's van BattleHub. Zelftest in SkyWars: de uitgeschakelde B klikt rechts op een kist in het midden en krijgt geen menu. In een geforceerd geopende kist pakt hij niets en legt hij niets terug. |
| "Maak SkyWars groter, met kleinere middeneilanden en een super groot middeneiland met de beste loot" | De oude map: een ring van max(20, 3,2 per speler) met eilandjes van 3,6, kleine stapstenen ertussen en een midden van 7,5 met vier kisten. | Een nieuwe indeling uit `core/SkyWarsPlan` (pure geometrie met JUnit), zie hieronder. |
| Bots kwamen in SkyWars nooit van hun eiland | Ze openden geen kisten (dus geen blokken) en `botMayBuild` stond uit. | Zie hieronder. |

**Indeling (`SkyWarsPlan`):**
- **Midden:** straal 15 tot 18, groeit met het aantal spelers.
- **Middeneilanden:** straal 4, in een ring op 6 blokken van het midden.
  - Tot 5 spelers ligt er één recht voor elk spawn-eiland (anders lag een zijwaarts middeneiland verder weg dan het midden zelf).
  - Bij meer spelers ligt er één tussen elke twee spawn-eilanden, of tussen elke twee buren als dat er minder dan vier zou geven, of bij een oneven aantal.
- **Spawn-eilanden:** straal 4,5. Elk ligt precies 12 blokken van zijn dichtstbijzijnde middeneiland, minstens 12 van zijn buren en van het midden, en het midden altijd verder weg dan dat middeneiland. De buitenrand komt op 43 tot 51 blokken (was 24 tot 42).

**Inrichting:**
- **Midden:** twee terrassen, een paviljoen op vier palen met vier kisten eronder en een hangende lamp, plus vier kisten op de diagonalen aan de voet. Daartussen bomen, of lantaarnpalen als het thema geen bomen heeft.
- **Middeneilanden:** een lantaarnpaal met twee kisten (`mid_chest`).
- **Spawn-eilanden:** twee kisten (`chest`), een boom of paal. De kisten kijken naar het midden van hun eiland.

**Loot:**
- Drie lijsten in `skywars.json`: `islandLoot`, `midLoot` (nieuw, tussen eiland en midden) en `centerLoot`.
- De nieuwe `centerLoot` is rijker: meer diamant, een diamanten bijl, kruisboog, windladingen en af en toe een totem.
- Een `centerLoot` die nog gelijk is aan de oude standaard wordt vervangen.
- Een kist op een spawn-eiland heeft altijd blokken. Geven de dobbelstenen er geen, dan komt er één regel blokken uit de lijst bij. De eilanden liggen ver uit elkaar: zonder blokken komt niemand weg.
- Aantal trekkingen: `midChestRolls` 8.

**Rondetijd:** 15 minuten. Config-versie 5 zet een ongewijzigde 600 seconden om naar 900.

**Bots:**
- `SkyWarsMatch.botGoal` stuurt een bot naar de dichtstbijzijnde volle kist die hij nog niet leeghaalde: eerst op zijn eigen eiland, en met 8 of meer blokken ook verder weg (een middeneiland, dan het midden).
- `botTick` haalt binnen reikwijdte de kist leeg, zoals shift-klikken.
- De bot trekt daarna bepantsering aan als het vak leeg is of het stuk beter (leer tot netheriet).
- `botMayBuild` staat aan binnen het speelveld.
- Een brugblok moet een botsvorm hebben: geen spinnenweb (dat zat in de loot) en geen bloemen.
- Zelftest: een bot in SkyWars haalt twee kisten leeg en bouwt dan minstens drie blokken van zijn eiland af.

**CI:** de eerste run gaf 803 geslaagd, 1 mislukt. Alle nieuwe tests slaagden. De mislukte test was "retention: the quest is done and paid", een datumafhankelijke fout in een oude test. De dagquests worden per speler en per datum gekozen. Op 21 oktober kreeg testspeler A "play 3" als tweede quest, en de test zette diezelfde quest ook op de eerste plek. Na drie matches waren er dus twee klaar in plaats van één. De test vervangt een tweede "play 3" nu door een quest die niet afloopt.


## Versie 1.30 – Wereld resetten, een groter plein, SkyWars nog groter, bots blijven op de map

| Wens of melding | Oorzaak of uitgangspunt | Oplossing |
|---|---|---|
| "Een command om de wereld van de BattleHub te resetten" | Er was alleen `/battlehub lobby rebuild`: die haalt de vorige lobby weg en bouwt hem opnieuw, maar alleen binnen de doos van de vorige bouw. Spelers bleven tijdens het bouwen in de lobby staan. | `/battlehub world reset [new]` (`HubWorld.reset`). Iedereen in de lobby gaat terug naar de normale wereld (`Travel.leave`), met een bericht. Het hele lobbygebied wordt leeggemaakt, inclusief entiteiten: het eiland en de lucht-eilanden (eilandstraal + 70 rondom, 90 onder en 150 boven de vloer), samen met de doos van de vorige bouw. Daarna wordt de lobby opnieuw gebouwd. Met `new` krijgt hij een nieuwe seed (een andere stad op hetzelfde plan, opgeslagen in config.json). `/hub` wacht tot het klaar is, matches in de arenawereld lopen door, en iedereen krijgt een bericht als het klaar is. Wie tijdens het bouwen terugkwam uit een match, staat daarna op de spawn. Zelftest: A wordt eruit gestuurd, `/hub` wordt geweigerd, daarna staat het plein er weer (de bakens op de rand) en A kan erin. |
| "De ring in het midden 10 blokken groter, om de grootsheid van de wereld beter tot zijn recht te laten komen" | De ring is het plein met de balustrade (de leaderboards hangen erboven) met straal 27. | `Plaza.RADIUS` is 37, en de klassieke lobby gebruikt dezelfde maat. De vloer is getekend voor 27. Alles tussen de fontein (7) en de rand schuift nu naar verhouding mee (`Plaza.at`): de windroos (punten tot 34, iets breder), de koperen ring met de lampen, de binnenring en de plantenbakken (29 in plaats van 21). De NPC-ring gaat van 16 naar 22 (config-versie 6 zet alleen de oude standaard om). De stad schuift tien blokken op: landmarks op 66 tot 76 (was 56 tot 66), straten op 50/74/98 (was 40/64/88), en de klassieke districten op 74 (was 64). `LOBBY_VERSION` 4 bouwt de lobby bij de eerste start opnieuw. |
| (gevonden) Overlap in de lobby | Door het verschuiven raakte bij de standaard-seed een kapel een huis: een kapel (en soms een toren) steekt buiten zijn gereserveerde kavel. | Een huis of toren wordt pas geplaatst als zijn echte doos geen ander gebouw raakt (`Lobby.overlapsAny`, dezelfde regel als de validator). JUnit: alle lobbytests slagen, bij vijf seeds 0 overlappen. |
| "SkyWars nog groter: steeds tussen 1,5 en 2,5 keer zo groot als nu" | De map van 1.29 had een buitenrand van 43 tot 51 blokken. | Per match een willekeurige factor van 1,5 tot 2,5 (`SkyWarsPlan.factor`). `SkyWarsPlan.of(spelers, factor)` zoekt de schaal waarbij de map precies zo veel keer zo breed is als die van 1.29 (`of129`). Eilanden, midden en bruggen groeien gelijk mee. Bij 4 spelers moet de schaal hoger (2,1 tot 3,5), omdat de middeneilanden opzij liggen (zie hieronder). Buitenrand 64 tot 127 blokken, middeneiland straal 21 tot 52. De diepte van eilanden, de leegte, de toeschouwersplek en het speelveld groeien mee. Een heel groot midden (straal ≥ 28) krijgt vier kisten extra (12) en meer bomen. Het paviljoen groeit met het bovenste terras. |
| Genoeg blokken op een grote map | Bruggen tot rond de 45 blokken. | De twee kisten van een spawn-eiland hebben samen minstens 1,5 keer de brug naar het dichtstbijzijnde middeneiland aan blokken, die van een middeneiland 1,5 keer de brug naar het midden. Komen de dobbelstenen tekort, dan komen er stapels uit de bloklijnen van de lijst bij. |
| "De tussen-eilandjes mogen niet direct tussen het spawn-eiland en het midden zitten" | In 1.29 lag bij hooguit 5 spelers (dus ook 1 speler met 3 bots) het middeneiland recht voor elk spawn-eiland. | Middeneilanden liggen altijd tussen de spawn-eilanden. JUnit: bij elke grootte en elk aantal spelers ligt elk middeneiland minstens zijn straal + 2 naast de rechte lijn van elk spawn-eiland naar het midden. |
| "Bots vallen meteen van de map" | Drie dingen samen. (1) Het doel was de kist zelf. Een bot liep ertegenaan, sprong erop (de kisten staan aan de rand van het spawn-eiland) en liep door. (2) Werd een bot aan de rand tegengehouden, dan zag `unstick` dat als vastzitten en sprong hij, met de toetsen nog naar buiten. In de lucht geldt de randcontrole niet. (3) Na een eigen sprong stuurde `recover` op lagere niveaus soms niet terug (de kans op bevriezen was bedoeld voor knockback). | (1) Het doel is het vak vóór de kist (de voorkant wijst naar het midden van het eiland). Kisten op het eigen eiland worden herkend aan het eiland uit het plan, ook op de grote eilanden. (2) `keepSafe` meldt of hij de bot tegenhield. `unstick` springt dan niet, maar draait alleen weg. (3) Een vlucht die zonder treffer begon wordt altijd teruggestuurd, zonder reactietijd. Na een treffer kan een bot (zoals een speler) nog bevriezen. Zelftest: SkyWars met drie bots. Ze halen kisten leeg en bouwen een brug. Daarna nog een halve minuut kijken: elke val wordt gelogd, en een val zonder treffer in de twee seconden ervoor laat de test falen. |

**CI 1.30.0:** 810 geslaagd, 2 mislukt. De nieuwe tests slaagden allemaal. SkyWars (1,62 keer zo breed, 81 blokken vanaf het midden, middeneiland straal 34): de bots haalden 6 kisten leeg en bouwden een brug, met 0 keer vallen. De wereld-reset duurde 17 seconden, waarna A er weer in kon. Wat faalde was de bestaande mace-test ("gooit een windlading"), met de tweede check als gevolg. De bot kreeg een ongeduldige persoonlijkheid en sloeg steeds op 1,4 blokken van de stilstaande A met een half geladen mace. Een windlading hoort bij 2 tot 5,5 blokken afstand, dus de kans kwam nooit. De test laat A nu elke 4 seconden zonder lancering een paar blokken terugstappen, zoals een speler doet.

**CI, tweede run:** de mace-bot lanceerde nu wel, maar vloog niet hoger dan een sprong. Hij liep nog tijdens het gooien, waardoor de windlading achter hem ontplofte. Nu staat hij stil, springt en gooit recht naar beneden, zoals spelers dat doen, zodat de klap onder zijn opstijgende voeten komt. De Bridge-test faalde ook: de bot bleef bij zijn spawn staan (2 blokken van de rand, 3 van zijn eigen goal) en kwam niet meer los. De oorzaak was de nieuwe regel "aan een rand niet springen om los te komen". Die is vervangen door een sprong die altijd eerst kijkt (`jumpLooking`): nooit een sprong die in de leegte eindigt, op elk niveau. Na een eigen sprong stuurt de bot in de lucht toch al altijd terug.

**CI, derde run:** mace en Bridge groen. Twee nieuwe meldingen.

(1) In SkyWars viel één bot uit zichzelf. Hij had net één blok gelegd, dus het gebeurde tijdens het bruggen bouwen (de map was 2,19 keer zo breed, dus lange bruggen). Bij het bruggen schuifelde de bot hurkend naar de rand tot 0,25 voorbij de rand. Op hurksnelheid (ongeveer 0,047 per tick) gleed hij daarna nog zo'n 0,06 door, tot 0,31, terwijl je met een lichaam van 0,6 breed maar 0,3 kunt overhangen. Dat ging alleen goed zolang het hurken hem tegenhield, en daar reken ik niet meer op. Nu stopt hij op 0,18 en rekent hij zijn doorglijden mee (snelheid × 1,2): eerst minder hard lopen, daarna remmen.

(2) De parkourtest liep vast. De bot stond op het startplatform op hoogte 100,5, terwijl de planner van 101 uitging. Het startplatform krijgt de `trim` van het thema, en dat is in sommige thema's (oceaan) een halve plaat. Een sprong van 1 omhoog werd zo 1,5 omhoog: onmogelijk. Dat verklaart ook de parkourfout van 1.28.0 (toen ook 100,5). Platformbovenkanten zijn nu altijd volle blokken (`fullBlock`), behalve de bewuste halve-plaatstappen.

**CI, vierde run:** parkour groen, SkyWars 0 keer gevallen (map 2,12 keer zo breed), mace vloog 1,5 blokken, Bridge bouwde over. Eén melding: in de Bridge-test legde de bot één blok terwijl het pad er nog was. Hij was al bij het eiland van A en stond 5 blokken van diens doel. Dat doel is een gat (3×3, de leegte in) waar je in moet springen, en de bot begon er een brug overheen te bouwen. Een bot bouwt nu niet meer als zijn bestemming minder dan 4,5 blokken weg is: het gat is dan de bestemming zelf, geen oversteek.



## Versie 1.31 – Bots met knockback, Chaos die erger wordt, 1v1v1v1 Chaos

| Wens of melding | Oorzaak of uitgangspunt | Oplossing |
|---|---|---|
| "De bots hebben geen knockback als ze gehit worden" | Vanilla `Player.attack` duwt een geraakte `ServerPlayer` niet op de server. Hij stuurt een `ClientboundSetEntityMotionPacket` naar diens eigen client en zet daarna de serversnelheid terug. De client van de speler voert de duw uit. Een bot heeft geen client: zijn `BotConnection` gooide uitgaande pakketten weg, dus ook de duw. | `BotConnection` kent zijn bot en vangt in de pipeline dat ene pakket op. Is het voor de bot zelf, dan zet hij die snelheid op de bot (`setDeltaMovement`), precies wat een client zou doen. De velden van het pakket worden één keer via reflectie gezocht (`getId`/`id`, een `Vec3` of `getXa/Ya/Za`), zodat een hernoemde methode in 26.x niets breekt. Zelftest: A slaat een bot. Binnen 4 ticks moet de bot minstens 0,25 snelheid hebben en 0,4 blokken verplaatst zijn. |
| "Sommige bots hebben geen sla-animatie als ze slaan of hakken (Spleef)" | `LivingEntity.swing` heeft in 26.x andere overloads (met een `SwingAnimation`). De oude aanroep vond soms geen passende methode, en dan zag niemand de arm. | `BotActions.swing` kiest de kortste `swing`-overload die alleen een hand en booleans neemt. Geeft die aan dat er niets verstuurd is, of is er geen, dan stuurt hij zelf een `ClientboundAnimatePacket` (zwaai hoofdhand) naar iedereen binnen 64 blokken. |
| "Bots in Bed Wars geven geen voorkeur aan bed slopen als die kans er is" | De bot liep naar het bed, maar in melee won een aanval het van het slopen: met een verdediger naast het bed bleef hij vechten. Het aanvalsdoel woog in `CombatPolicy` even zwaar op 2 als op 20 blokken. | (1) In `BotBrain` gaat slopen voor slaan: ligt `botBreakTarget` binnen reikwijdte, dan hakt de bot, ook als iemand hem slaat. (2) `CombatPolicy` laat een aanvalsdoel onder 6 blokken zwaarder wegen (tot +0,8), zodat de laatste stappen naar een bed niet voor een gevecht wijken. Zelftest: de bot staat 2 blokken van het bed van A, met A ertussen. Binnen 10 seconden moet het bed weg zijn. |
| "Minstens 50 extra vloeken in de pool" | Er waren er 100. | 72 nieuwe, samen 172 (`ChaosCatalog`, met titels in het Engels en Nederlands). Ze gebruiken dezelfde acties als de bestaande (geven, effect, attribuut, speciaal), dus de zelftest "alle events tegelijk" dekt ze. De catalogus meldt een probleem bij minder dan 150. |
| "Na de 25 verschillende vloeken herhalen ze maar 2x zo erg, na 50 4x zo erg, enzovoort" | Een match plande 25 events (20 willekeurig en 5 vaste) en ging daarna door met events die hij nog niet gehad had. | `ChaosPlan.nextTurn()` geeft een event met een niveau: na de 25 komt dezelfde volgorde terug op niveau 1, na 50 op niveau 2, enzovoort (factor 2^niveau, tot 64). `ChaosIntensity` zegt wat "zo erg" betekent: langer (tot 3 keer, nooit langer dan 45 s), effecten één niveau sterker per keer (tot 6), items en drankjes zo veel keer zo veel (drankjes tot 8), attributen zo veel keer zo groot maar binnen grenzen (grootte 0,1 tot 3 keer, zwaartekracht nooit omgekeerd, nooit 0 hp, snelheid nooit negatief), bommen, pijlen, webben, kippen en vuurwerk zo veel keer zo veel, duwen per niveau de helft harder, bliksem vaker. De titel krijgt "x2", de chatregel "event 31, 2x zo erg". JUnit: de volgorde komt terug op niveau 1 en 2, en elk event van de catalogus blijft binnen de grenzen op elk niveau. |
| "En dit ook voor een 1v1v1v1" | The Bridge 1v1v1v1 was een `BridgePartyMatch`. Chaos zat in `BridgeChaosMatch` (een duel). | `ChaosDirector` werkt nu voor een `ChaosHost` (een interface), zodat duel en party hem allebei gebruiken. Nieuwe modus `bridge_chaos_1v1v1v1` (party, 2 tot 4, nooit ranked): `BridgeChaosPartyMatch` breidt de 1v1v1v1 uit met power-ups op het middenplatform, events, punten × multiplier en een windlading in de kit. "Van het midden" is daar de afstand tot het middelpunt en de brug zijn de lijnen van elk doel naar het midden. Zelftest: vier teams, een power-up-parel in het midden, "tiny" op niveau 1 maakt je 0,1 groot, het einde zet het terug, en double points met een goal laat A winnen. |

**CI 1.31.0:** de eerste run compileerde niet. In 26.3 is `hurtMarked` hernoemd, is `invulnerableTime` privé en heeft `ClientboundAnimatePacket` geen `SWING_MAIN_HAND` meer. Bij het oplossen bleek nog iets: vanilla zet de snelheid van een geraakte speler direct na het versturen van de knockback terug, zodat het rechtstreeks zetten in de pipeline toch werd overschreven. De bot onthoudt de duw nu en neemt hem aan het begin van zijn volgende tick. Het animatiepakket wordt via reflectie gemaakt (int-constante of enum) en gaat altijd mee naar iedereen binnen 64 blokken. De tweede run gaf 830 geslaagd, 0 mislukt. Knockback: 0,40 snelheid weg van A, 1,31 blokken verplaatst in 4 ticks. De Bed Wars-bot sloopte het bed van A ("nearly at the blue bed" in zijn afweging). Chaos "tiny" op niveau 1 gaf grootte 0,1.


## Versie 1.32 – Hunger Games in de lobbystad

| Wens | Uitgangspunt | Oplossing |
|---|---|---|
| "Een Hunger Games-modus waar de lobby de map is" | De lobbystad (`Lobby.generate`) is een eiland met straal 150: het plein, vier lanen, vier monumenten, ongeveer 100 huizen en wachttorens. Rond de 455.000 blokken, in 1,3 tot 1,6 s gegenereerd. De lobby zelf kan geen map zijn, want daar staan de andere spelers. | Nieuwe party-modus `hunger_games` (2 tot 16). Per match wordt de stad als blueprint gebouwd in een arenaslot (slots liggen 1024 uit elkaar, de stad is ongeveer 355 × 355). Bouwen kost met 40.000 blokken per tick ongeveer 12 ticks. |
| "Genereer elke week een nieuwe lobby" | Bedoeld is de stad die de map is. | `HungerMap.weekSeed(lobby.seed, datum)` geeft maandag tot en met zondag (ISO-week) dezelfde seed, daarna een nieuwe. `HungerMaps` bewaart de map van de week in het geheugen: alleen de blokken en de spots, de gebouwplannen worden weggegooid. De map wordt 10 seconden na de serverstart al gemaakt. De lobby waar spelers spawnen blijft gewoon staan: die verandert alleen met `/battlehub world reset new`. Dat was het dichtstbijzijnde alternatief: een hub die elke week ongevraagd verandert zou spelers in de war brengen. |
| "Het thema mag (mits het matcht) per match veranderen, als de layout maar hetzelfde blijft" | De generator kiest materialen via paletten. Opnieuw genereren met andere paletten kan ook de vorm veranderen en kost elke match ruim een seconde. | `LobbyThemes` zet alleen bloknamen om: grond, planten, bomen, steenfamilies (blok, trap, plaat en muur) en houtsoorten. Vorm en eigenschappen blijven gelijk (een trap blijft een trap die dezelfde kant op kijkt). Daardoor staat elk blok in elk thema op dezelfde plek. Er zijn negen thema's die bij een stad passen: zoals gebouwd, woestijn, winter, kersenbloesem, nether, badlands, jungle, deep dark en End. Andere thema's (sky, ocean, ...) vallen terug op "zoals gebouwd". Stemmen op een thema werkt. JUnit: elke omgezette naam bestaat in 26.3, trappen blijven trappen, platen blijven platen, en "zoals gebouwd" verandert niets. |
| Startplek en loot | Hunger Games: iedereen rond de cornucopia. | 16 spawns op de donkere ring van het plein (straal 16,5), met het gezicht naar de fontein. De cornucopia is een ring van 12 kisten op straal 10,5 met de beste loot. De bestaande kist en ton in elk huis worden gebruikt (200 bij de seed van deze week). In de monumenten en wachttorens komen kisten binnen, op een volle vloer tegen een muur, niet in een deuropening (15 bij deze seed). Drie lijsten in `hunger_games.json`. |
| Bots moeten de huizen in | Bots zoeken een route met A* over blokken, en een deur (ook een open deur) heeft een botsvorm. | Deuren worden uit de map gehaald (244 bij deze seed), zodat deuropeningen vrij zijn. JUnit: vanaf een spawn zijn minstens twee derde van de monumentkisten en een steekproef van de huiskisten lopend bereikbaar. Een bot loot de dichtstbijzijnde volle kist (hooguit 6 blokken hoger of lager, binnen de grens), trekt bepantsering aan, eet bij honger en gaat daarna naar het plein. |
| Spelregels | | 30 seconden vrede (geen schade tussen spelers; vallen, vuur en de grens doen wel pijn). Na 10 minuten worden alle kisten één keer opnieuw gevuld. Na 5 minuten trekt een grens zich in 10 minuten samen tot 24 blokken rond het plein: dezelfde vanilla-grens per speler via pakketten als bij LMS, 1 schade per seconde erbuiten. De stad blijft staan: alleen wat spelers neerzetten, bladeren, spinnenwebben en planten kunnen kapot. Wie sterft laat alles vallen en iedereen hoort een kanonschot. De laatste die overblijft wint; na 20 minuten wint wie de meeste kills heeft. Wie van de kliffen valt is uit (voidY −25, de laagste grond ligt op −4). |
| Zelftest | | Een party van vier stemt op nether. Gecontroleerd wordt: de seed van deze week, 16 spawns met iedereen op de ring, gras als nylium, steenstenen als netherstenen en sparrenhout als crimson op dezelfde posities, geen deur bij een huiskist, alle kisten gevuld, geen schade tijdens de vrede maar wel daarna, de stad kan niet kapot maar een neergezet blok wel, en buiten de grens krijg je schade. Daarna valt D (een diamant ligt op de grond), B en C vallen, en A wint. |

**CI 1.32.0:** in beide runs slaagden alle Hunger Games-tests. Gemeten: de stad van week 2026-W43 als nether, 16 spawns, op dezelfde posities gras → nylium, steenstenen → netherstenen en sparrenhout → crimson, geen deur bij een huiskist, en 200 huiskisten, 15 monumentkisten en 12 cornucopiakisten, alle 227 gevuld. In de bot-test plunderden de bots eerst 3 cornucopiakisten (één droeg al een ijzeren borstplaat) en liepen daarna de stad in naar een huiskist. Twee meldingen:
- In de eerste run faalde de oude test "de bot komt naar A". Een expert-bot bleef een halve minuut op 3,9 tot 4,0 blokken van de stilstaande A staan. Oorzaak: het "ongeduld", dat een wachtende bot laat aanvallen als er niets gebeurt, hield op bij 0,5 × (1 − 0,6 × geduld). Een bot met veel geduld, veel verdediging, weinig agressie en een voorkeursafstand van 4 tot 6 blokken bleef daardoor altijd liever op afstand. Dat zat al in de code sinds het ongeduld er is, maar komt alleen voor bij zulke uitersten van de persoonlijkheidsvariatie. Nu groeit het ongeduld door tot 2 × (1 − 0,6 × geduld), ongeveer 14 seconden na de laatste treffer. De eerste 5 seconden blijft alles zoals het was. JUnit: de meest geduldige stijl die er kan bestaan (agressie 0, geduld 1, verdediging 1, afstand 6) wacht op 3,6, 4 en 5,5 blokken eerst, maar valt na 15 seconden aan.
- In de tweede run stond het terugzetten van de Hunger Games-instellingen per ongeluk in de SkyWars-bot-test (een NullPointerException). Die staat nu in de Hunger Games-bot-test.
- In de derde run faalde de knockback-test één keer: de bot kreeg wel 0,40 snelheid, maar kwam maar 0,20 blokken ver. De test duwde in de richting waarin A keek, en daar stond deze keer dekking van de arena. De test zoekt nu eerst een richting met 6 blokken vrije ruimte en vaste grond, en draait A daarheen.
- In de vierde run was de knockback-test goed (1,31 blokken), maar liepen de Hunger Games-bots niet de stad in. Na de cornucopia hakten ze tijdens de vrede al op elkaar en op A in (zonder schade), en na de vrede vochten ze elkaar dood. De gelogde bot was al toeschouwer. Een bot wist niet dat er een vredesperiode is. Nu is in Hunger Games tijdens de vrede niemand iemands vijand (`isEnemy`), dus plunderen de bots. De bot-test zet de vrede op 10 minuten, zodat hij alleen het plunderen en lopen meet en niet een gevecht met A.


**1.32.1 (gevonden in de CI van de ML-commit):** de zelftest "de bot bouwt over met zijn klei" in The Bridge faalde. De bot was een rusher met geduld 0,05. Het ongeduld uit 1.32 groeide ook voor een tegenstander op 37 blokken, aan de andere kant van de leegte: naderen scoorde 2,69 tegen 0,69 voor het doel. De bot bleef daardoor aan de rand heen en weer gaan tussen naderen en de randbeveiliging. Ook met de oude grens (0,5) won naderen bij zo'n agressieve bot nog steeds (1,25 tegen 0,72). Daarom twee aanpassingen: ongeduld telt alleen bij een patstelling dichtbij (binnen voorkeursafstand + 3 blokken), en met een doel in de game is een tegenstander op meer dan 12 blokken minder waard (tot −0,6). JUnit: een ongeduldige rusher (MASTER) kiest na lange stilte het doel en niet de tegenstander op 37 blokken.


## ML fase 1 – Een geleerd PvP-gevecht (1v1, platte arena)

**Doel.** De strategie van de bots blijft gescript: looten, bed verdedigen, bruggen en parkour. Alleen het melee-gevecht wordt een getraind model, dat later in alle games wordt hergebruikt. Fase 1 is een 1v1 zwaardduel (en boxing) op een vlakke vloer. Sumo volgt als 1b.

**Stap 1 – overzicht (bevestigd).**
- Een bot is een echte `ServerPlayer`, dus Minecraft rekent zelf de physics. Het brein zet alleen de invoer: `zza`, `xxa`, sprinten, springen, kijkrichting en klikken.
- `CombatMind` kiest een tactiek met `CombatPolicy` en `Decider`. `CombatSteering` maakt daar elke tick een `Intent` van.
- De naad voor het model is `CombatMind.tick` (`steering.steer(...)`): het model levert dezelfde `Intent`. Reactietijd, strategie (`botGoal`), randveiligheid en de eerlijke kruisdradencontrole bij het slaan blijven gewoon werken.
- Keuzes:
  - eerst het zwaardduel, met boxing meteen mee;
  - ONNX Runtime for Java;
  - observaties, acties en rewards zoals voorgesteld;
  - trainen in de container.

**Stap 2 – simulatie (`battlehub/ml`).**
- **Eén spec als bron van waarheid.** `spec/combat_v1.json` definieert 43 observaties en 7 actiekoppen (MultiDiscrete [3,3,2,2,2,9,5], elke 2 ticks een beslissing).
  - De observaties staan in het assenstelsel van de bot (vooruit, links, op) en worden geschaald en afgekapt.
  - Ze gaan over tegenstander, richten, eigen status, wat zichtbaar is van de tegenstander, en 8 rand- en muurafstanden. Die afstanden zitten er nu al in, zodat sumo en arena's met dekking later hetzelfde model kunnen gebruiken.
  - `tools/make_golden.py` maakt `spec/combat_v1_golden.json`: 160 toestanden met hun observatie en 64 acties met hun decodering. Bij de integratie moet de JUnit-test in Java daar op 1e-6 mee overeenkomen. Een Python-test faalt zodra het gouden bestand niet meer bij de spec past (via de sha256 van de spec).
- **Physics** (`pvp_sim/physics.py`, numpy, veel arena's tegelijk). Dezelfde formules als de game en als `core/ai/PlayerSim.java`.
  - Gecontroleerd tegen de echte waarden (`tests/test_physics.py`):
    - lopen 4,317 m/s, sprinten 5,612 m/s, Speed II 40% sneller;
    - sprong 1,2522 hoog en 12 ticks in de lucht; sprint-springen ongeveer 7,1 m/s;
    - een diamanten zwaard op vol ijzer doet 3,78 schade;
    - aanvalskracht (t + 0,5) / 12,5 met schade × (0,2 + 0,8·kracht²);
    - knockback 0,4 met omhoog min(0,4, vy/2 + 0,4); op de grond is vy −0,0784, zoals in de game;
    - 10 ticks bescherming (sterkere schade telt alleen het verschil, zonder duw);
    - een crit doet × 1,5; een sprint-hit duwt 0,5 extra, remt de aanvaller tot 0,6 en stopt zijn sprint;
    - bereik 3 op een hitbox van 0,6 × 1,8 (ook een misser kost de lading);
    - muren, de rand van een platform en health-regeneratie.
- **Wat een bot weet.** Dit volgt de zintuigen uit de mod:
  - zichtbaar binnen 65° (hoofd, midden of voeten);
  - anders alleen de laatst bekende plek: gezien, gehoord binnen 12 blokken terwijl hij beweegt, of net door hem geraakt;
  - snelheid, health en dergelijke alleen als de tegenstander zichtbaar is.
- **Willekeur per episode.** Zodat het model daar niet van afhangt:
  - arena 7–14 blokken van het midden tot de muur (14–28 breed);
  - start op 5–10 blokken van elkaar, kijkrichting ±60°;
  - waarnemingsvertraging 0–2 ticks, mikruis en een draailimiet van 30–60° per beslissing.
- **Rewards.**
  - Schade ±/20.
  - Winst of verlies ±10; gelijkspel op tijd naar health-verschil.
  - Misser −0,02, per beslissing −0,001, tegenstander uit beeld −0,005.
  - Boxing: ±0,05 per treffer meer dan de tegenstander.
  - Zonder de kleine straffen is het zero-sum; dat wordt getest.
- **Omgevingen.** `PvpBatch` (n arena's, beide vechters handelen, met auto-reset, voor self-play) en `PvpEnv` (Gymnasium; slaagt voor `check_env`).
- **Gescripte tegenstander.** Werkt alleen op de observatie, dus hij weet niet meer dan het model. Hij dient als meetlat en als eerste tegenstander.
- **Snelheid.** Ongeveer 30.000 beslissingen per seconde, met beide vechters, op één kern.
- **Tests en metingen.** 24 tests slagen.
  - De gescripte bot wint meer dan 90% van willekeurig spel.
  - Skill 0,8 wint 69% van skill 0,5.
  - Een voorbeeldduel duurt 6,6 s met 6 tegen 5 treffers (`tools/show_fight.py`).
  - In CI draait een aparte job `ml` die de Python-tests uitvoert.

**Stap 4–5 – training (`ml/train`).**
- **Eerste runs leerden wegrennen.** Een geladen slag op de hitbox is te zeldzaam om toevallig te vinden, en de gescripte tegenstanders straften elke toenadering af. Na 2 miljoen stappen: 0 overwinningen, zelfs niet tegen een stilstaande pop. Het model hield ongeveer 16 blokken afstand en klikte in de lucht.
- **Oplossing:**
  - potentiaal-shaping: dichterbij komen en naar de tegenstander kijken (verandert niet wat de beste strategie is);
  - −1 voor allebei bij een gelijkspel op tijd;
  - geen straf meer per misser;
  - een opbouw: 1,5 miljoen stappen alleen een pop (die nooit slaat), willekeurig spel en zwakke gescripte bots, dan tot 5 miljoen stappen steeds meer de pool van eerdere versies.
- **Daarna leerde het snel:**
  - na 0,5 miljoen stappen elke pop verslagen;
  - na 1 miljoen 25–30% tegen de gescripte bot;
  - na 4 miljoen 95%;
  - na 8 miljoen 97% tegen de gescripte bot en 67% tegen eerdere versies.
- **Snelheid en duur.** Ongeveer 4–5 duizend stappen per seconde op 4 kernen. Gestopt na 17 miljoen stappen (70 minuten): tegen de gescripte bot was het verzadigd (99,5%), en voor de andere modi komt er toch een uitgebreidere spec.
- **League** (`train/league.py`, Elo; de gescripte bot met skill 0,5 = 1000; 200 potjes per paar, iedereen tegen iedereen):
  - random 107, gescript 0,3: 929, gescript 1,0: 1099;
  - checkpoint 1 miljoen 1044, 2 miljoen 1165, 4 miljoen 1461, 6 miljoen 1590, 9 miljoen 1739, 16 miljoen 1894 (het beste; 17 miljoen 1859).
  - De niveaus zijn gelijk verdeeld tussen gescript 0,3 en het beste checkpoint:

    | Niveau | Checkpoint | Extra vertraging | Mikruis | Draailimiet | Keuze |
    |---|---|---|---|---|---|
    | Beginner | 1 miljoen | 4 ticks | 2,5° per tick | 24° per beslissing | loten |
    | Casual | 2 miljoen | 3 ticks | 1,8° | 30° | loten |
    | Intermediate | 4 miljoen | 2 ticks | 1,2° | 36° | loten |
    | Advanced | 6 miljoen | 1 tick | 0,8° | 45° | beste actie |
    | Expert | 9 miljoen | 0 ticks | 0,5° | 54° | beste actie |
    | Master | 16 miljoen | 0 ticks | 0,2° | 60° | beste actie |

- **Export** (`train/export_onnx.py`): `obs` (n, 43) → `logits` (n, 26), ongeveer 100 KB per model. ONNX Runtime en PyTorch verschillen hooguit 1,2·10⁻⁷. De spec-sha256, het aantal stappen, het niveau en de Elo staan in de metadata. Per model schrijft de export ook een `.check.json` (8 observaties met logits) voor de Java-test.

**Stap 6–7 – in de mod (1.33.0).**
- **`net.battlehub.core.ml` (pure Java, JUnit zonder Minecraft):**
  - `MiniJson`, `CombatSpec` (met sha256), `CombatState`, `CombatObservation`, `CombatAction` (beste of loten per kop) en `CombatLevels`.
  - `CombatObservationTest` leest het gouden bestand uit `ml/spec` (Gradle-testresource). Alle 160 observaties komen op 1e-6 overeen, alle 64 acties hetzelfde. De sha256 van de spec in de mod is die van het gouden bestand.
- **ONNX Runtime 1.31.0** (`implementation` + `include`, de jar wordt ongeveer 51 MB groter). `CombatModels` laadt de spec, `difficulties.json` en per niveau een sessie (1 thread), en weigert een model met een andere spec-sha256. Lukt laden niet (ook een `UnsatisfiedLinkError`), dan komt er één melding en vecht de bot gescript. `CombatModelsTest`: elk niveau geeft in Java de logits die PyTorch gaf (op 1e-4), en een model voor een andere spec wordt geweigerd.
- **De naad.** `MeleeController` in `core/ai`. `CombatMind` laat hem sturen als de tactiek APPROACH, STRIKE, SPACE of RETREAT is en er geen doel is. Reactietijd, helen, randen, ontwijken en zoeken blijven gescript, net als de kruisdradencontrole bij elke klik in `BotBrain.execute`.
- **`LearnedMelee`:**
  - bouwt elke 2 ticks de `CombatState` uit wat de bot weet (dezelfde `Situation`: zicht, laatst bekende plek, health alleen als zichtbaar), plus 8 rand- en 8 muurstralen in de richtingen van de bot;
  - neemt de tegenstander zoals hij `extra_delay` ticks eerder was;
  - houdt de keuze 2 ticks vast: de klik alleen in de eerste tick, de draai over beide ticks verdeeld binnen de draailimiet, met mikruis;
  - zet yaw en pitch zelf, zonder het AimModel.
- **Config:** `bots.learnedCombat` (standaard aan) en `bots.learnedModes` (`sword`, `boxing`). `/bots inspect` toont hoeveel ticks het model stuurde.
- **Zelftest.** De bestaande bot-tests draaien met het model uit (zij testen het gescripte gevecht). Een nieuwe test zet het aan: een practice-duel tegen MASTER, het model stuurt en raakt A, en A verliest terwijl hij stil staat.

## ML fase 2 – Het geleerde gevecht in alle melee-modi (plan)

**Doel.** Hetzelfde idee als fase 1: de strategie blijft gescript (looten, bed, bruggen bouwen, de goal), alleen het gevecht zelf is het model. Nu voor alle modi waarin met zwaard, bijl of vuist gevochten wordt.
- **Modi:** SkyWars, Bed Wars, Hunger Games, The Bridge (ook Chaos, duos en 1v1v1v1), FFA, 1v2/1v3, Sumo, Axe, KOTH, CTF, LMS, Build UHC en de toernooien.
- **Blijft gescript:**
  - de kruisboog; de mace en het boogduel alleen tot het model ze aantoonbaar beter doet;
  - Spleef, TNT Run en Parkour: daar wordt niet gevochten.

**Wat er per modus anders is dan een zwaardduel.** Het model van fase 1 heeft alleen een vlakke vloer met muren gezien, één tegenstander en één kit. In een modus met een afgrond zou het er gewoon af lopen. Nu vangt de gescripte RECOVER dat op, maar duwen naar de rand kent het model niet.

| Modus | Wapen en kit | Terrein | Tegenstanders |
|---|---|---|---|
| SkyWars | loot: vuist tot diamanten zwaard of bijl, losse stukken harnas | eilanden en bruggen boven de void | tot 11, derde partijen |
| Bed Wars | houten zwaard en leer, uit de shop steen/ijzer/diamant en de knockback-stick | eilanden en bruggen boven de void | teams |
| Hunger Games | loot | de lobbystad: huizen, muren, trappen, halve blokken | tot 15 |
| The Bridge (+ duos, 1v1v1v1) | ijzeren zwaard, leer | een smalle brug boven de void | 1–3 |
| Bridge Chaos | idem, plus speed, slowness, strength, jump boost, grootte (scale), zwaartekracht en knockback uit de events | idem | 1–3 |
| Sumo | vuist, geen schade | een platform boven de void: eraf duwen wint | 1 of meer |
| FFA, 1v2, 1v3 | diamanten zwaard of bijl, ijzer | arena | 2–3 tegelijk |
| Axe | diamanten bijl en schild, ijzer | arena | 1 |

**Spec v2 (`spec/combat_v2.json`).** Eén spec voor alle modi. Dezelfde 7 actiekoppen, ongeveer 90 observaties:
- **Uit fase 1:** tegenstander, richten, eigen status en wat zichtbaar is.
- **Kit en effecten, van beide kanten** (wapen en harnas zie je in de game ook):
  - de schade van een volle klap op de ander, na diens harnas, als deel van zijn maximale health, en andersom;
  - de cooldown van beide wapens en de knockback-bonus van beide;
  - mijn loopsnelheid, sprongkracht en zwaartekracht;
  - de grootte van beiden;
  - of schade telt (sumo en boxing niet).
- **Terrein**, in 8 richtingen vanuit de bot, afstanden tot 4 blokken:
  - void (een dodelijke val);
  - richel (lager dan 1,25: eraf vallen);
  - opstap (springen nodig);
  - muur (kan er niet over).
- **Onder en achter:**
  - hoe ver de grond onder mij is (in de lucht boven de void na een klap);
  - voor de tegenstander: hoeveel grond er achter hem ligt in de richting waarin ik duw, en of hij boven de void hangt. Daarmee kan het model leren iemand van een brug of eiland te slaan.
- **Een tweede tegenstander:** de dichtstbijzijnde andere zichtbare vijand (vooruit, links, op, afstand, of hij naar mij kijkt, wanneer hij sloeg) en het aantal vijanden binnen 6 blokken. Zo leert het model niet tussen twee man in te gaan staan.
- **Teamgenoten** blijven buiten de observatie: het gescripte brein kiest het doelwit, en friendly fire staat uit.
- **Spullen** (toegevoegd na de bevestiging, op verzoek). Het model weet wat het bij zich heeft en kan het zelf gebruiken in het gevecht.
  - Bij zich: boog en pijlen, eten, gouden appels, ender pearls, wind charges en een schild, plus de cooldowns ervan.
  - Wat het nu in de hand heeft, en hoe lang het al spant of eet.
  - Van de tegenstander alleen wat je ziet: een boog, eten of iets om te gooien in de hand, of hij spant, eet of blokt.
  - Een pijl, pearl of wind charge die op mij afkomt.
  - Twee actiekoppen erbij: **hand** (wapen, boog, eten, gouden appel, pearl, wind charge) en **gebruiken** (rechtermuisknop vasthouden). Dat werkt zoals in de game:
    - de boog spant zolang je vasthoudt en schiet bij loslaten;
    - eten duurt 32 ticks;
    - een pearl of wind charge gaat bij het indrukken;
    - met wapen en schild blok je.
  - Wisselen kost de lading, en tijdens gebruik loop je op 20%.
  - De draai- en kijkstappen krijgen ±1° erbij om met de boog te mikken.
  - Blokken plaatsen blijft gescript (bruggen bouwen hoort bij de strategie).

**Simulatie v2.**
- **Een hoogtekaart per arena** (cellen van 1×1, hoogtes in halve blokken, void). Generatoren voor:
  - de arena met muren (fase 1);
  - het sumoplatform;
  - een brug van 1–3 breed tussen twee eilanden (Bridge, Bed Wars);
  - eilanden met bruggen (SkyWars);
  - een stadje met huizen, muren, trappen en halve blokken (HG);
  - heuvels.
- **Botsing per as:**
  - automatisch opstappen tot 0,6;
  - op 1 blok springen;
  - vallen, met valschade;
  - onder de void dood.
- **1–3 vechters per arena:** 1v1, 1v2 (twee tegen het model) en drie tegen elkaar.
- **Spullen in de simulatie:**
  - pijlen: spankracht, zwaartekracht, schade naar snelheid, de crit bij volle spanning;
  - eten en gouden appels: absorption en regeneration;
  - honger en natuurlijke regeneratie;
  - ender pearls: teleport met 5 schade, kwijt in de void;
  - wind charges: de explosie duwt iedereen in de buurt en lanceert jezelf, zonder valschade daarna;
  - het schild: blokt van voren na 5 ticks; een bijl schakelt het 5 s uit;
  - de mace: extra schade naar de valhoogte, zodat mace-gevechten later ook geleerd kunnen worden.
- **Kits en effecten** per vechter en per episode, gekozen uit de tabellen van de mod:
  - vuist, houten tot netherite zwaard, bijlen, de knockback-stick, sharpness;
  - harnas 0–20 met toughness;
  - de effecten van Chaos.
- **Rewards:** als fase 1. Daarbij is in de void vallen verliezen (−10), met +10 voor wie hem als laatste raakte. Bij sumo telt alleen eraf duwen.
- **Gelijkheid tussen Python en Java** komt er dubbel:
  - het gouden bestand v2 bevat ook kits en terrein;
  - de stralen worden in pure Java berekend (`core.ml`, over een interface die per kolom de hoogte van de grond geeft) en in JUnit vergeleken met Python op dezelfde hoogtekaarten. In de mod leest die interface de wereld, met een cache per beslissing.

**Training.**
- Een nieuw netwerk, omdat de observatie anders is.
- De opbouw:
  - eerst vlak 1v1 met de kit van fase 1;
  - dan terrein en void;
  - dan kits en effecten;
  - dan 1v2 en drie tegen elkaar.
- Geschat 50–80 miljoen stappen: enkele uren op 4 kernen.
- Een league per soort terrein. De niveaus worden gekozen zoals in fase 1.
- **Eis:** v2 moet op de vlakke arena minstens zo goed zijn als v1. Anders houden sword en boxing v1.

**In de mod.**
- **De naad:** het model vecht ook als er een doel is, zolang de Decider voor gevechten kiest: STRIKE, SPACE, RETREAT, en APPROACH tot ongeveer 8 blokken. RECOVER, EVADE, HEAL, OBJECTIVE, SEARCH en het bruggen bouwen blijven gescript.
- **`LearnedMelee` v2:**
  - de kit uit items, attributen en effecten;
  - het terrein uit de wereld;
  - de tweede vijand uit `Perception`.
- **`bots.learnedModes`** krijgt standaard alle melee-modi.
- **Zelftests per modus:**
  - SkyWars: vechten op een eiland zonder eraf te lopen, en A eraf slaan;
  - Bridge: vechten op de brug;
  - HG: vechten in de stad;
  - 1v2.

**Mijlpalen.**
- **2a** – spec v2, simulatie (terrein, kits, 3 vechters), tests, gouden bestand en Java-pariteit. Daarna bevestiging vragen, zoals afgesproken na de simulatie.
- **2b** – training en league.
- **2c** – integratie, zelftests per modus, docs, versie 1.34.0.

**Mijlpaal 2a – spec v2 en simulatie (klaar, wacht op bevestiging).**
- **Spec v2** (`ml/spec/combat_v2.json`): 118 observaties en 9 actiekoppen (de 7 van v1 met fijnere draai- en kijkstappen van ±1°, plus hand en gebruiken).
  - De observaties: het doelwit, ikzelf (ook absorption, honger, regeneratie en valhoogte), wat ik van het doelwit zie (ook schild, spannen, eten en wat het vasthoudt), kit en effecten, wat ik bij me heb en vasthoud, een projectiel op mij af, een tweede vijand, en terrein: 4×8 stralen plus onder mij, onder het doelwit en achter het doelwit.
  - Waar een vaste waarde niet klopte met de modi, is die nu een veld: bereik, grootte en ooghoogte per vechter.
- **`ml/pvp_sim2`**: terrein (hoogtekaarten met zes soorten kaarten), kits (alle kits van de mod, de Bed Wars-shop, loot en Chaos), physics (alles uit het plan), omgevingen en een gescripte bot die zijn spullen gebruikt.
  - **Fout gevonden en opgelost:** een harde duw (knockback II uit Chaos plus een sprint-hit, ruim 1,6 blok per tick) schoot door een muur heen. Horizontale beweging gaat nu in stapjes van hoogstens 0,45 blok.
- **Snelheid:** ongeveer 5.400 beslissingen per seconde met 256 arena's en 3 vechters op één kern. v1 haalde 30.000 met 2 vechters op een vlakke vloer. De dure delen zijn zicht over het terrein, de stralen en botsingen; die zijn gevectoriseerd.
- **Gedrag van de gescripte bots tegen elkaar** (diagnose):
  - Zwaard in de arena: ongeveer 15 s per gevecht, nooit meer door de muur.
  - Op de brug: ongeveer 5,5 s; bijna alle doden zijn van de brug geslagen (279 van 280 na een klap).
  - Loot in de stad: ongeveer 6,5 s.
  - Sumo: ongeveer 10 s, alleen door eraf duwen.
  - Een rondlopende pop valt bijna nooit van een brug: de randstralen werken.
  - `tools/show_fight2.py` tekent gevechten op hun kaart.
- **Tests:**
  - Python: 53, waarvan 27 nieuw voor v2: terrein en stralen, opstappen, valschade, void met kill credit, boog, eten, gouden appel, pearl, wind charge, mace, schild en bijl, teams, honger, de muur-duw, resistance V, een grote vechter naast een muur, de observaties, de omgeving en `check_env`.
  - Java (`CombatObservationV2Test`): elke observatie uit het gouden bestand op 1e-6, de acties met hand en gebruiken, en de terreinstralen (`core.ml.TerrainRays`) op 6 kaarten met 72 posities gelijk aan Python. v1 blijft ongewijzigd slagen.
  - Lokaal slagen 266 core-tests.

**Mijlpaal 2b – training en league (1.34.0).**
- **`train/selfplay2.py`:** de arena's in 3 workerprocessen (3 × 96), het hoofdproces doet PPO (MLP 256×256) en de tegenstanders.
  - Tegenstanders per arena en per plek: de pool van eerdere versies, de gescripte bot, willekeurig spel en poppen.
  - De opbouw over de eerste 12 miljoen stappen: van vlakke zwaardduels naar alle kaarten, kits, 1v2 en drie tegen elkaar, en van poppen naar de pool. De shaping zakt tussen 15 en 35 miljoen stappen naar de helft.
  - Elke 2 miljoen stappen een checkpoint met een evaluatie in een apart proces (`train/eval2.py`).
- **Verloop** (winst tegen de gescripte bot met skill 0,8, over acht vaste scenario's):

  | Stappen | Winst |
  |---|---|
  | 2 miljoen | 20% |
  | 8 miljoen | 55% |
  | 12 miljoen | 67% |
  | 20 miljoen | 75% |
  | 28 miljoen | 82% |
  | 32 miljoen | 84% |
  | 40 miljoen | 81% (afgevlakt) |

  - Op 40 miljoen stappen: sumo 48 van 48, Bridge 48 van 48, zwaard 46/48, bijl 43/48, boog 39/48, SkyWars-loot 40/48, Bed Wars drie tegen elkaar 36/48.
  - Het zwakste punt is 1 tegen 2 in de stad: 11/48 (de gescripte bots doen het daar niet beter).
  - Gestopt op 40 miljoen stappen (170 minuten, gemiddeld ongeveer 4.000 stappen per seconde; de pool van bevroren versies kost steeds meer tijd).
- **League** (`train/league2.py`): 11 checkpoints, de gescripte bot met 4 skills en willekeurig spel, iedereen tegen iedereen over 10 soorten 1v1-gevechten (arena met zwaard, bijl, boog en mace, brug, SkyWars-loot, stad, sumo, Bed Wars, heuvels met Build UHC), met 120 gevechten per paar.
  - Elo (gescript 0,5 = 1000): 40 miljoen stappen 1463, 34 miljoen 1453, 30 miljoen 1413, 22 miljoen 1311, 18 miljoen 1241, 10 miljoen 1099, 6 miljoen 1050; gescript 0,8 1054, 1,0 1044, 0,3 978; willekeurig 277.
  - De niveaus: beginner 6 miljoen (1050), casual 10 miljoen (1099), intermediate 18 miljoen (1241), advanced 22 miljoen (1311), expert 30 miljoen (1413), master 40 miljoen (1463), met dezelfde extra's per niveau als v1.
- **De eis tegenover v1:** in het zwaardduel op een vlakke vloer verliest v2 van v1 (79 tegen 121; v1 via een adapter in de v2-simulatie). Daarom houden sword en boxing v1 (`bots.learnedV1Modes`).
- **Spullen: niet geleerd.** Bij 20 miljoen stappen (en later): 0 pijlen, 0 keer eten, 0 pearls of wind charges, 0 keer blokken.
  - De hand-kop kiest vooral iets dat het niet bij zich heeft, en dat doet niets. Zo houdt het model gewoon zijn wapen vast, want wisselen kost de lading.
  - Eten vroeg 16 beslissingen achter elkaar de knop vasthouden; zo'n reeks vindt PPO niet door te verkennen.
  - Daarom in 1.34.0: het model vecht, en eten, de boog en wind charges blijven de regels van de bot. Met `bots.learnedItems` mag het model zijn spullen zelf gebruiken.
  - Eten is nu één druk die doorgaat tot het klaar is (simulatie en mod).
- **Vervolgtraining voor spullen:** `--init` vanaf 40 miljoen stappen, `--items`, entropie 0,02, 16 miljoen stappen (62 minuten).
  - Het schild leerde het model wel: 9–25% van de beslissingen blokken in bijlduels, 4–13% in loot-gevechten.
  - Pijlen, eten en gooien bleven ook nu 0.
  - Tegen het hoofdmodel wint het niet: 41–50% over de mix (120 gevechten per checkpoint). 1.34.0 houdt daarom de modellen van de hoofdtraining.
  - Het schild mag het model in de mod wel zelf opsteken (de rechterknop met het wapen), los van `learnedItems`.
  - Voor de spullen is een andere aanpak nodig:
    - actiemaskers, zodat "iets kiezen wat je niet hebt" geen veilige niets-doen-keuze meer is;
    - of eerst nadoen (imitatie van de gescripte bot, die zijn spullen wel gebruikt) en daarna PPO.

**Mijlpaal 2c – in de mod (1.34.0).**
- **`LearnedFight` (v2):** bouwt de 118 waarden uit de wereld:
  - kit en effecten: wapentabel `core.ml.Gear` gelijk aan de simulatie, plus enchantments, attributen en effecten;
  - wat de bot bij zich heeft, de cooldowns en wat hij vasthoudt;
  - het doelwit, zoals waargenomen, een paar ticks vertraagd op lage niveaus;
  - de dichtstbijzijnde andere vijand die hij ziet of hoort, en een vijandig projectiel op hem af;
  - terrein via `TerrainRays` over de blokken (`WorldColumns`, met een cache per beslissing).
- **De naad:** `CombatMind` laat de controller ook beslissen bij SHOOT en bij een doel. v1 weigert dan, zoals voorheen. v2 vecht bij een doel alleen binnen 8 blokken, en schiet alleen met `learnedItems`.
- **`Intent` krijgt `hand` en `use`.** `BotBrain.learnedHand` voert ze uit: wapen met schild, boog spannen en loslaten, één druk eten, pearl of wind charge gooien. Bij gebruik loopt de bot op 20%, zoals een speler.
- **Config:** `learnedModes` met alle melee-modi (een config met de standaard van 1.33 krijgt de nieuwe), `learnedSpec`, `learnedV1Modes` en `learnedItems`.
- **Tests:**
  - `CombatModelsTest` controleert ook de v2-modellen in ONNX Runtime;
  - zelftests met model v2: een bijlduel (het model stuurt en A verliest), The Bridge (bij A neemt het model het over en raakt A) en 1v2 (beide bots, A verliest).
- **De Bridge-bottest** ("while the path is there the bot walks it and builds nothing") faalde in 2 van de 3 runs; de bot zette 1–3 blokken bij een rand.
  - Oorzaak: de padzoeker nam als start alleen het blok onder het midden van de bot. Op de brug van 1 blok breed hangt dat midden soms net naast de brug, terwijl de voeten (0,6 breed) er nog op staan. Dan was er "geen route", en dan bouwt de bot.
  - Dat was niet genoeg (in de volgende run zette hij er weer 2). De echte oorzaak: het doel van The Bridge is een gat van 3×3, en de padzoeker moest tot 1,5 blok van het midden komen. Daar ligt alleen gat; de rand ligt op 2 blokken. Er was dus nooit een route naar het doel. Op één lijn met de brug liep de bot er gewoon recht naartoe; kwam hij er schuin aan, dan "bouwde" hij recht naar het doel.
  - Dat schuin aankomen kwam door het wisselen met RECOVER ("standing on an edge"). Op een brug van 1 blok staat de bot altijd naast een rand, en bij een karakter met weinig "objective" (rusher) won RECOVER om de paar ticks (0,70 tegen 0,71).
  - Opgelost:
    - een doel waar niemand kan staan, wordt bereikt vanaf de rand: tot 2,5 blok;
    - "standing on an edge" telt alleen nog als de bot naar die rand toe beweegt, of als hij stilstaat zonder doel (met een test in `PolicyTest`);
    - `BotPath` zoekt het startblok onder elk deel van de voeten.
  - **Bekend punt, nog open:** een rusher (veel sprinten, weinig voorzichtig) komt niet altijd goed de smalle brug op. Hij remt bij de ingang omdat zijn snelheid een beetje schuin staat, blijft dan hangen, stapt na twee seconden opzij en valt soms.
    - Eerder bouwde hij daar naast de brug; dat is nu weg.
    - De v2-Bridge-test zet A daarom op het eiland van de bot (het model vecht daar zoals bedoeld), zodat die test niet van het oversteken afhangt.
    - Een oplossing voor het oversteken (bijvoorbeeld niet sprinten op smalle grond, of de rem langs het pad laten kijken in plaats van langs de snelheid) volgt apart.

## Versie 1.35 – Modelrapporten: hoeveel een bot afwijkt van zijn model, en wat hij moet leren

**Vraag.** Een OP krijgt na een match in de chat van elke bot te horen hoeveel hij moest afwijken van zijn model, en wat hij dan moet leren in de testomgeving (de simulatie).

**Wat telt als afwijken.** Elke tick van een gevecht waarin een geleerd model meedoet, valt in één van drie bakken. Zo'n tick is er als het model stuurde, of als het doelwit binnen 8 blokken is (dan zou het model sturen).
- **Eigen model:** het model koos en de bot deed dat.
- **Aangepast:** het model koos, maar de eigen regels van de bot veranderden het:
  - afgeremd bij een rand (`edge`);
  - een sprong geweigerd die in de leegte zou eindigen (`jump`);
  - in de lucht teruggestuurd na knockback (`air`);
  - vastgelopen, waarna de bot sprong of opzij stapte (`stuck`);
  - gegeten volgens de regels (`heal`);
  - het bed of doel gesloopt in plaats van geslagen (`objective`).
- **Overgenomen:** de gescripte mind vocht zelf:
  - weg van een rand (`recover`), ontwijken (`evade`), eten (`heal`), de boog (`shoot`);
  - het doel (`objective`), zoeken (`search`), nog reageren (`react`), of iets anders (`other`).

**Nieuw voor het model.** Bij elke beslissing kijkt de mod welke waarden buiten wat de simulatie in de training liet zien liggen.
- `tools/make_ranges.py` speelt daarvoor ongeveer 400.000 beslissingen per spec, op volle breedte: de meegeleverde modellen (master en intermediate), de gescripte bot en willekeurig spel.
- Per waarde gaan het 0,1%- en het 99,9%-kwantiel naar `combat_v1_ranges.json` en `combat_v2_ranges.json`, met de sha256 van de spec.
- Een waarde die er meer dan 0,05 buiten ligt, is nieuw. Voorbeelden: creative-reach, slowness, meer dan drie vechters.

**Lessen (`ml/spec/lessons.json`).** Eén tabel voor de mod en de training.
- Elke reden heeft een les (of `null`: reageren, het doel en "anders" zijn zo bedoeld).
- Elke observatie hoort bij precies één groep met een les: afstand, hoogte, snelheid, richten, timing, honger, kits, effecten, spullen, projectielen, meer vijanden of terrein.
- Per les staat er welke scenario's van de simulatie vaker moeten. Voorbeeld: randen geeft bridge ×2, platform ×2 en islands ×1,5.
- Het gewicht van een les is het deel van de ticks dat zijn redenen verklaren, plus het deel van de beslissingen met een nieuwe waarde uit zijn groep.
- De bot noemt de grootste drie, vanaf 3%.

**De melding.**
- Na elke match krijgt elke OP online van elke bot die minstens 3 seconden met zijn model vocht vijf regels: wie (niveau, model, modus, seconden), de verdeling eigen/aangepast/overgenomen, de grootste redenen, de waarden die nieuw waren, en wat hij moet oefenen in de simulatie.
- `/battlehub bots report off` zet het uit voor jezelf (onthouden in het profiel); `bots.modelReport` zet het voor iedereen uit.
- `/battlehub bots report` toont de totalen sinds het begin, per spec en per modus. `reset` begint opnieuw.
- `/battlehub bots inspect` toont het rapport tot nu toe.

**Terug naar de training.**
- De totalen staan in `config/battlehub/ml_feedback.json`, met de lessen en hun focus.
- `train/focus.py` maakt daar scenario-gewichten van. Een les die een vijfde van de gevechten verklaart (of meer) krijgt zijn hele focus, een kleinere een deel.
- `train/selfplay2.py --focus <bestand>` traint daarmee, bijvoorbeeld vanaf het huidige model (`--init`).
- Een les met spullen (eten, de boog) heeft ook `--items` nodig. De training zegt dat erbij.

**Dichtstbijzijnde alternatief.**
- Wat het model gedaan zou hebben op een overgenomen tick wordt niet gevraagd: het model draait alleen bij gevechtstactieken, en een extra beslissing per tick kost rekentijd. De afwijking wordt dus geteld, met de reden, maar niet als ander gekozen actie.
- `aim` en `timing` hebben geen scenario-focus. Als die vaak nieuw zijn, wijkt de simulatie zelf af van het spel, en moet de simulatie worden aangepast in plaats van vaker getraind.

**Tests.**
- JUnit `ModelReportTest` (7): de tabel dekt elke observatie van v1 en v2 precies één keer, en elke reden; de bereiken horen bij hun spec; de telling en de verdeling; een gewoon duel is niets nieuws, creative-reach en slowness zijn "effecten"; lessen wegen redenen en nieuwe waarden samen; optellen en het feedbackbestand.
- pytest `test_lessons.py` (7): dezelfde dekking, elke focus bestaat in `Scenario`, de bereiken passen bij hun spec, en een feedbackbestand verschuift de scenario's zoals bedoeld.
- Zelftest in het v2-bijlduel: A telt als OP en krijgt na de match het rapport van de bot, en de totalen tellen de match (met het bestand).

**CI 1.35.0.**
- Eerste run: 874 geslaagd, 2 mislukt. De nieuwe tests slaagden.
  - De wereld-reset ("A komt er weer in") faalde zonder iets in het log. De server-stop-test daarna volgde daaruit: A stond nog buiten de lobby en kon geen duel starten.
  - De check logt nu bij een fout waar A is (al in BattleHub, enter gelukt, dimensie, resetstatus, sessie, match).
- Tweede run: 876 geslaagd, 0 mislukt; de reset slaagde. Het was dus een timingfout, die niet terugkwam.
- Het rapport in de zelftest: Bot_Mars (master, v2) in het bijlduel, 14 s gevecht, eigen model 100%, niets nieuws te leren.
- In het v1-zwaardduel (6 s) waren `edge_4` tot `edge_6` (tot 23% van de beslissingen) en `opp_dist` (15%) nieuw voor v1. De arena van de mod heeft randen en een grotere startafstand dan de vlakke arena van de v1-simulatie. Lessen: ander terrein (33%) en grotere afstanden (15%).
- Een reden of waarde die op 0% afrondt, noemt het rapport niet meer (de eerste versie zei "sprong geweigerd 0%").

## Versie 1.36 – Zelf kiezen hoeveel bots er meedoen

**Vraag.** Aangeven hoeveel bots er meedoen: de lobby vullen, of een eigen aantal.

**Party-lobby's.**
- Elke speler heeft een wens (`core/BotFill`, bewaard in het profiel als `botWish`):
  - standaard: zoals voorheen, tot `bots.partyFillTo` spelers, na `bots.fillAfterSeconds`;
  - een aantal bots (1 tot 15);
  - de lobby vol, tot het maximum van de game.
- Een aantal of een volle lobby komt meteen: wie erom vraagt, wil spelen. De standaard wacht eerst op mensen.
- De grootste wens van de mensen in de lobby telt: vol gaat boven een aantal, het grootste aantal boven een kleiner. Minstens het minimum van de game, zodat hij kan starten; nooit meer dan het maximum.
- Wie geen bots wil (`/bots off`), houdt ze nog steeds buiten de lobby.
- Kiezen gaat met de knop **Bots** (een harnasstandaard) in het menu van een party-game: linksklik meer, rechtsklik minder, shift-klik bots uit of aan. Of met `/bots standard`, `/bots fill` en `/bots <aantal>`.
- De knoppen in dat menu schoven daarvoor op: meedoen, mapstem, uitnodigen en bots staan nu op 10, 12, 14 en 16.

**Privé-games (`/custom`).**
- De eigenaar zet in het menu het aantal bots (geen, een aantal, of zoveel als er passen) en hun niveau (beginner tot meester). Bij de start doen ze mee.
- Lukt het starten niet, dan gaan de bots weer weg. `bots.maxBots` blijft gelden: als de limiet bereikt is, zegt de game hoeveel bots er wel mee konden.

**Dichtstbijzijnde alternatief.** In een gewone party-lobby met meer mensen telt de grootste wens. Een stemming zoals bij de map kan ook, maar is trager voor wie gewoon wil spelen.

**Tests.**
- JUnit `BotFillTest` (4): opslaan en teruglezen, de knop gaat rond, een party-lobby vult tot de grootste wens (meteen, binnen min en max), een privé-game krijgt de bots van de eigenaar.
- Zelftests:
  - A zegt `/bots 2` en gaat alleen Sumo in, met de standaardwachttijd op een minuut: twee bots komen meteen en de match start met hen.
  - Een privé-Sumo met drie beginnerbots start met A en die drie, zonder dat iets telt.

## Versie 1.36.1 – Bots die zich menselijker gedragen

**Feedback.** Bots hebben geen sla-animatie. Bij een kist pakken ze alles bizar snel en trekken ze het meteen aan.

**De sla-animatie.**
- `BotActions.swing` zocht een `swing`-methode met alleen een hand en een vlag. In 26.3 neemt de swing van de server ook de swing-animatie van het item (de prop-hunt-test roept hem al zo aan). Er werd dus geen methode gevonden.
- Daarnaast eindigt de swing-status van een bot niet altijd. Dan blijft elke volgende swing stil.
- Nu:
  - de vollediste `swing` waarvan alle argumenten te vullen zijn: de hand, `true`, en voor de rest de standaard van dat type (een enum-constante of een statisch veld zoals `DEFAULT`);
  - `swinging` wordt voor elke swing gewist;
  - het animatiepakket gaat er altijd ook uit.
- Zelftest: de testverbinding telt de swing-pakketten die een speler krijgt, en A moet de bot in het zwaardduel zien slaan. Het log noemt welke methode en welk pakket gebruikt worden.

**Kisten (SkyWars, Hunger Games), `bot/Looting` en `core/LootPace`.**
- De bot opent de kist: het deksel gaat open voor iedereen, je hoort het, en zijn arm zwaait (de rechtsklik).
- Hij staat stil, kijkt naar de kist en pakt na een moment (7 tot 14 ticks) één stapel per keer: 4 ticks per stapel op master, 10 op beginner, steeds een kwart meer of minder.
- Is de kist leeg, of past er niets meer bij, dan gaat het deksel dicht.
- Daarna trekt hij betere harnasdelen één voor één aan: 12 tot 30 ticks per stuk, en niet zolang hij geraakt wordt of er een vijand binnen 5 blokken is.
- Een treffer of een vijand dichtbij laat hem de kist sluiten. Drie seconden laat hij kisten met rust, daarna komt hij terug voor de rest.
- JUnit `LootPaceTest`: een kist van acht stapels kost een beginner ongeveer vier seconden en een master ongeveer twee; elke stap varieert.
- Zelftest SkyWars: een geleegde kist kostte minstens 6 ticks plus 3 per stapel.
