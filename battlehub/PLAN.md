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
| Eigen maps | Structuurbestanden (.nbt van een structure block) in `config/battlehub/maps/<modus>/` of `maps/all/`. Markers zijn structure blocks in Data-modus met tekst `spawn`, `spawn:90`, `spectator`, `goal_red`, ... De map wordt in één keer geplaatst (de vanilla-plaatsing) en daarna worden de markers weggehaald; het arenavak wordt na afloop gewist zoals altijd. Alleen modi die niet van een vaste vorm afhangen nemen eigen maps (zie README); de rest blijft gegenereerd. Zonder stem wordt een eigen map in de helft van de gevallen gebruikt (`customMapChance`). |
| Menu's | Knoppen worden nu automatisch gecentreerd per rij (er zijn 8 duels en 13 partyspellen); statistieken per modus staan op een eigen pagina. |

## Versie 1.13 – Fase 7: stats-export en wekelijkse toernooien

| Onderwerp | Keuze |
|---|---|
| Discord | Bewust niet gebouwd (zo gevraagd): geen webhooks of andere Discord-koppeling. |
| Wekelijks toernooi | Een vaste dag en tijd in de tijdzone van de server (of een ingestelde). Aanmelden opent een kwartier vooraf (melding met knop), herinneringen op 5 en 1 minuut. Bij de start spelen alle aangemelde spelers die vrij in BattleHub staan het gewone toernooi (zelfde bracket, best of 3), met de ingestelde kit en teamgrootte. Te weinig spelers: afgelast. Eén keer per ISO-week, ook na een herstart (`weekly.json` in de wereldmap). Bovenop de gewone prijzen: 2000/500 coins, `weeklyWins` in het profiel en twee titels (1 en 5 keer). Admins kunnen de aanmelding meteen openen (`/battlehub weekly open <seconden>`). |
| Export | JSON-bestanden voor een website, elke 5 minuten (en bij het stoppen van de server): spelers, ranglijsten, clans, seizoen, recente wedstrijden (alleen in het geheugen, de laatste 100), wekelijkse toernooien en serverstatus. Verzamelen op de serverthread, schrijven op een eigen thread, elk bestand in één keer vervangen (tijdelijk bestand + verplaatsen), dus een website leest nooit een half bestand. Een eenvoudige `index.html` wordt één keer neergezet. Bots tellen niet mee. |
| Webserver | Optioneel (standaard uit): een kleine ingebouwde HTTP-server die alleen de exportmap serveert (geen paden erbuiten), met `Access-Control-Allow-Origin: *` zodat een website op een ander adres de bestanden kan ophalen. |

