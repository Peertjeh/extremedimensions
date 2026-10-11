# Extreme Dimensions (Minecraft 26.3, Fabric)

Nether Charge en End Charge: dimensies zijn expedities met een tijdlimiet.
Tijd op = **Dimensional Collapse**: je gaat dood, respawnt in de Overworld en kiest 1 van 3 penalty's.
Je wordt nooit uit het spel gegooid.

## Zo krijg je het .jar-bestand (zonder zelf te compileren)

1. Maak een gratis account op https://github.com en klik rechtsboven op **+ > New repository**. Naam: `extremedimensions`, zet hem op **Private**, klik **Create repository**.
2. Klik op **uploading an existing file** en sleep ALLE bestanden en mappen uit deze map erin
   (let op dat de map `.github` ook meegaat, daar zit de automatische build in). Klik **Commit changes**.
3. Ga naar het tabblad **Actions**. De build start vanzelf (duurt ca. 3 tot 6 minuten). Zie je niets? Klik links op **build** en dan **Run workflow**.
4. Als er een groen vinkje staat: klik op de run, scroll naar **Artifacts** en download **Artifacts**.
   Pak de zip uit. Je hebt nu `extremedimensions-0.3.0.jar` (de `-sources.jar` mag je negeren).

**Staat er een rood kruis?** Klik op de run, open de mislukte stap en kopieer de foutmelding naar Claude. Dan los ik het op.

## Installeren (iedereen, ook de server)

1. Installeer **Java 25** en de **Fabric Loader 0.19.5** voor **Minecraft 26.3** (via https://fabricmc.net/use/installer).
2. Download **Fabric API 0.162.0+26.3** (Modrinth of CurseForge).
3. Zet `fabric-api-....jar` en `extremedimensions-0.3.0.jar` in de map `mods`.
4. Alle spelers moeten de mod hebben (de UI draait op de client).

Maak de wereld als gewone **Survival** (niet de ingebouwde Hardcore-modus, want daarin word je bij dood toeschouwer).

## Titelscherm

De afbeelding `The_Tether.png` staat als achtergrond van het Minecraft-titelscherm (alleen bij spelers met de mod).
Vervangen? Overschrijf `src/client/resources/assets/extremedimensions/textures/gui/title.png` en pas
`IMG_W` en `IMG_H` in `TitleArt.java` aan op de nieuwe afmetingen.

## Hoe het werkt

- **Altaar** = een **Lodestone in de Overworld, binnen 5 blokken van een portaal**
  (Nether Portal voor de Nether, End Portal of End Portal Frame voor de End).
  Rechtermuisklik met een laad-item of Tether in je hand om Charge te storten.
- **Een altaar is een tank voor een dimensie.** De eerste lading bepaalt het type: de Lodestone verandert in een
  **Ember Lodestone** (Nether) of **Void Lodestone** (End) en accepteert daarna alleen nog die brandstof.
  Kapotmaken geeft een gewone Lodestone terug; de charge is dan weg.
- **Leeg altaar**: is de charge op, dan wordt het weer een gewone Lodestone (met rook en geluid) en kun je het
  opnieuw aan de Nether of de End binden.
- **Meerdere altaren** bij hetzelfde portaal tellen bij elkaar op. Max 30 minuten per altaar.
- **HUD linksboven**: alleen zichtbaar binnen 10 blokken van een geladen altaar (som van alle altaren in de buurt).
- **Deeltjes**: geladen altaren gloeien (vuur of portaaldeeltjes), hoe voller hoe meer. Een stroom deeltjes zoekt
  een vrije weg door de lucht naar het midden van het portaal (nooit door blokken heen).
- **Geblokkeerd**: is er geen vrije weg naar het portaal, dan stopt de stroom, rookt het altaar en verliest het
  1 seconde charge per seconde. Zo'n altaar voedt ook geen expedities.
- **Statusscherm**: sneak + lege hand op het altaar.
- **Nether Charge**: steenkool (2s), houtskool (2s), steenkoolblok (18s), magmablok (6s), fire charge (12s), blaze powder (15s), blaze rod (30s).
- **End Charge**: amethist shard (3s), chorus fruit (6s), ender pearl (20s), eye of ender (45s), shulker shell (90s), echo shard (120s).
- Ga je door een portaal, dan voeden de altaren van dat type rond dat portaal (binnen 8 blokken) je expeditie.
  Geen geladen altaar? Dan is de poort slapend en word je teruggestuurd.
- In de dimensie lopen die altaren leeg (1 seconde per seconde). Bij 0 = Collapse.
- Gewoon doodgaan in Nether/End: grote melding **EXPEDITION ENDED**, de altaren van je expeditie lopen leeg en je
  kiest een penalty. Doodgaan in de Overworld geeft (nog) geen straf.
- In singleplayer staat de timer stil als je het spel pauzeert.
- Veilig terug in de Overworld = expeditie geslaagd.
- **Elke wereld heeft zijn eigen voortgang** (opgeslagen in `extremedimensions.json` in de wereldmap).
- Alle teksten in het spel zijn Engels.
- Alles aanpasbaar in `ChargeItems.java` en `Config.java`.

## Lodestone en de twee Tether-items

De Lodestone is aangepast: zonder Netherite te maken, zodat je vóór de Nether al kunt beginnen.

| Item | Recept (3x3) |
|---|---|
| **Lodestone** (aangepast) | 8 Chiseled Stone Bricks rondom 1 **Diamond** |
| **Ember Tether** (Nether) | 8 Coal Blocks rondom 1 Lodestone |
| **Void Tether** (End) | 4 Ender Pearls (hoeken) + 4 Eyes of Ender (zijkanten) rondom 1 Lodestone |

Rechtermuisklik op het altaar met een Tether in je hand: +10 minuten Charge in een keer
(`TETHER_ITEM_CHARGE` in `Config.java`). Items staan nog niet in de creatieve tab: gebruik
`/give @s extremedimensions:ember_tether` en `/give @s extremedimensions:void_tether`.

## Animaties

- **Titelscherm:** eigen logo, langzame zoom, parallax met de muis, pulserende portaalgloed en zwevende vonken.
- **Storten en Tether binden:** vlam- of portaaldeeltjes, enchant-glyphs, geluid, balk die oplicht en een zwevende "+tijd".
- **Expeditie start:** timerbalk schuift van boven in, deeltjes en geluid (geen melding).
- **Weinig tijd:** rode rand, trillende balk, klok- en hartslaggeluid.
- **Collapse:** witte flits, rode gloed, glitchbalken, de balk knapt in tweeen, explosie en bliksemgeluid.
- **Veilig terug:** groene lichtveeg, "SAFELY BACK" en vonkjes, totem-deeltjes.
- **Penalty-keuze:** kaarten schuiven in, lichten op bij hover, gekozen kaart flitst.
- **Statusscherm:** balken vullen zich en getallen tellen op.

## Penalty's (kies 1 uit 3 willekeurige)

Na een Collapse of een dood in Nether/End. **Alles stackt**: kies je dezelfde penalty opnieuw, dan telt hij dubbel.
Penalty's met een duur blijven per stack **10 minuten actieve speeltijd in de Nether of End**. De klok loopt alleen
tijdens een expeditie en alleen als je echt speelt: wie 15 seconden niet rondkijkt of geen 2 blokken loopt, telt als
AFK en de klok pauzeert ("Penalties paused: you are idle!" linksboven). In de Overworld loopt de klok niet.

| Penalty | Effect (per stack) |
|---|---|
| Accelerated Drain | Charge loopt 75% sneller leeg |
| Debt | Brandstof geeft 40% minder charge |
| Scar | -3 hartjes (je houdt er altijd minstens 2) |
| Cracked Armor | -4 armor |
| Heavy Legs | 15% langzamer lopen |
| Clumsy Hands | 35% langzamer minen |
| Frailty | Constant Weakness |
| Famine | Constant Hunger |
| Shadowed | Darkness in Nether en End (vanaf 2 stacks ook Slowness) |
| Leaky Tank | Eenmalig: de altaren van je volgende expeditie verliezen 50% |
| Mourning | Eenmalig: Weakness II, Hunger II en Mining Fatigue, 10 minuten |
| Toll | Eenmalig: je verliest de helft van je XP-levels |

## Muziek en geluid

- Eigen menumuziek: "The Tether Knows" (`music/menu_1.ogg`) en "The Tether" (`music/menu_2.ogg`) in
  `src/client/resources/assets/extremedimensions/sounds/` spelen om en om tot de wereld geladen is en faden dan uit.
- Eigen geluiden voor het kiezen van een penalty en voor "Expedition Ended".

## Bekende beperkingen (versie 0.1)

- Uitloggen in een dimensie pauzeert de timer (exploit). Kan in een volgende versie bestraft worden.
- Geluids- en deeltjesnamen in de animaties kunnen in 26.3 anders heten; een verkeerde naam geeft alleen geen effect.
