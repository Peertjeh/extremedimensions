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

- **Altaar** = een **Lodestone**. Rechtermuisklik met een laad-item of Tether in je hand om Charge te storten. (Een kompas gebruiken op de Lodestone werkt nog gewoon.)
- **Statusscherm**: sneak + lege hand op het altaar.
- **Nether Charge**: steenkool (2s), houtskool (2s), steenkoolblok (18s), magmablok (6s), fire charge (12s), blaze powder (15s), blaze rod (30s).
- **End Charge**: amethist shard (3s), chorus fruit (6s), ender pearl (20s), eye of ender (45s), shulker shell (90s), echo shard (120s).
- Max 30 minuten per dimensie. Alles aanpasbaar in `ChargeItems.java` en `Config.java`.
- Een dimensie in zonder charge: de poort is slapend en je wordt teruggestuurd.
- In de dimensie loopt je charge leeg (1 seconde per seconde). Bij 0 = Collapse.
- Normaal doodgaan in Nether/End: je tank van die dimensie loopt leeg (geen penalty-keuze).
- Veilig terug in de Overworld = expeditie geslaagd (penalty's tellen af).

## Lodestone en de twee Tether-items

De Lodestone is aangepast: zonder Netherite te maken, zodat je vóór de Nether al kunt beginnen.

| Item | Recept (3x3) |
|---|---|
| **Lodestone** (aangepast) | 8 Chiseled Stone Bricks rondom 1 **Diamond** |
| **Ember Tether** (Nether) | 8 Coal Blocks rondom 1 Lodestone |
| **Void Tether** (End) | 4 Ender Pearls (hoeken) + 4 Eyes of Ender (zijkanten) rondom 1 Lodestone |

Rechtermuisklik op het altaar (Lodestone) met een Tether in je hand: +10 minuten Charge in een keer
(`TETHER_ITEM_CHARGE` in `Config.java`). Items staan nog niet in de creatieve tab: gebruik
`/give @s extremedimensions:ember_tether` en `/give @s extremedimensions:void_tether`.

## Animaties

- **Titelscherm:** eigen logo, langzame zoom, parallax met de muis, pulserende portaalgloed en zwevende vonken.
- **Storten en Tether binden:** vlam- of portaaldeeltjes, enchant-glyphs, geluid, balk die oplicht en een zwevende "+tijd".
- **Expeditie start:** timerbalk schuift van boven in, deeltjes en geluid.
- **Weinig tijd:** rode rand, trillende balk, klok- en hartslaggeluid.
- **Collapse:** witte flits, rode gloed, glitchbalken, de balk knapt in tweeen, explosie en bliksemgeluid.
- **Veilig terug:** groene lichtveeg, "VEILIG TERUG" en vonkjes, totem-deeltjes.
- **Penalty-keuze:** kaarten schuiven in, lichten op bij hover, gekozen kaart flitst.
- **Statusscherm:** balken vullen zich en getallen tellen op.

## Penalty's (kies 1 uit 3 willekeurige)

| Penalty | Effect |
|---|---|
| Versnelde Drain | Charge loopt 1,5x sneller, 2 geslaagde runs lang |
| Schuld | Items leveren 25% minder charge op, 2 runs lang |
| Litteken | -2 hartjes, 2 runs lang |
| Lekkende Tank | Meteen 30% van je charge weg |
| Rouw | Weakness en Hunger, 5 minuten |

## Bekende beperkingen (versie 0.1)

- Uitloggen in een dimensie pauzeert de timer (exploit). Kan in een volgende versie bestraft worden.
- Spelerdata staat in `config/extremedimensions-data.json` (bij de server dus in de serverconfig), niet in de wereldmap.
- Het altaar is een bestaand blok (Lodestone), geen eigen blok.
- Commando's die de mod intern gebruikt (kill, tp, effect, attribute, particle, playsound) kunnen feedback in de serverconsole en bij ops geven.
- Geluids- en deeltjesnamen in de animaties kunnen in 26.3 anders heten; een verkeerde naam geeft alleen geen effect.

## Samen met BattleHub

Draait BattleHub (map `battlehub/`) op dezelfde server, dan staat Extreme Dimensions stil zolang je in
de werelden van BattleHub bent (de hub en de arena's):

- je charge loopt niet leeg en een dimensiewissel telt niet: ga je vanuit de Nether naar BattleHub en
  weer terug, dan loopt dezelfde expeditie gewoon verder;
- penalty's werken daar niet (het Litteken geeft je je hartjes terug zolang je in BattleHub bent) en het
  penalty-keuzescherm verschijnt pas als je BattleHub verlaat;
- de HUD met de tankmeters is verborgen;
- het altaar doet niets.

Welke werelden dit zijn staat in `Config.PAUSED_NAMESPACES`.
