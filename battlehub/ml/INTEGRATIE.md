# Het gevechtsmodel in de mod inbouwen

Deze handleiding beschrijft wat er moet gebeuren om het getrainde model in BattleHub te laten vechten, en wat je zelf
moet beslissen of doen. Alles wat al klaar is staat in `battlehub/ml`; de stappen hieronder gaan over de mod
(`battlehub/src`).

## Hoe het straks werkt

```
BotBrain.melee()                      (elke tick, bestaat al)
  Perception  ->  Situation           (wat de bot weet, bestaat al)
  CombatMind.tick(s)
     Decider kiest een tactiek        (gescript, bestaat al: vechten, helen, doel van de game)
     tactiek is vechten en er is geen doel?
        ja  -> LearnedCombat.act(...) (nieuw)  -> Intent
        nee -> CombatSteering.steer   (zoals nu)  -> Intent
  execute(Intent)                     (bestaat al: invoer, kruisdraadcontrole, randveiligheid)
```

`LearnedCombat` doet elke 2 ticks dit:
1. Bouw een `CombatState` uit wat de bot weet (velden hieronder).
2. Reken de 43 observaties uit, met precies dezelfde formules als `ml/pvp_sim/obs.py`.
3. Laat het model de logits geven (26 getallen) en kies per actiekop de hoogste. Op lage niveaus wordt er geloot.
4. Zet de actie om in een `Intent`: vooruit/strafe/sprint/sprong, een kijkpunt (huidige yaw/pitch plus de draai) en
   slaan.

De strategie blijft gescript: looten, bedden, bruggen en helen kiest de `Decider` zoals nu. Het model neemt alleen
het gevecht over. Daarom werkt het straks in elke game zonder per game iets te veranderen.

## Eerst een keuze: hoe het model rekent

| | ONNX Runtime for Java | Eigen Java-code (aanbevolen) |
|---|---|---|
| Wat | De officiële bibliotheek leest het `.onnx`-bestand | Het netwerk (43 → 128 → 128 → 26, tanh) in ~60 regels Java; de gewichten worden naast de `.onnx` geëxporteerd |
| Grootte mod-jar | +51 MB (native libraries voor Windows, Linux en macOS) | +ongeveer 100 KB per model |
| Native code | Ja, uitgepakt bij het starten | Nee |
| Zekerheid | Leest elk ONNX-model | Een test vergelijkt de Java-uitkomst met ONNX Runtime (alleen in de test, niet in de mod) |
| Later grotere modellen | Zonder codewijziging | Alleen zolang het een gewoon MLP blijft |

Bij een netwerk van deze grootte kost een beslissing in Java minder dan 0,05 ms, dus snelheid maakt geen verschil.
De vraag is vooral of 51 MB extra in de mod-jar acceptabel is. Kies je ONNX Runtime, dan blijft de rest van de
stappen gelijk; alleen stap 4 verandert.

## Stappen

### 1. Eén bron voor de spec
- Laat Gradle `ml/spec/combat_v1.json` meekopiëren naar `assets/battlehub/ml/` in de mod (een `processResources`-
  regel), zodat er nooit twee versies van de spec bestaan.
- Kopieer de gekozen modellen (`combat_v1_<niveau>.onnx`, of de gewichten-bestanden) en `difficulties.json` uit de
  league-map naar `src/main/resources/assets/battlehub/ml/`.

### 2. Pure Java: `net.battlehub.core.ml` (geen Minecraft, testbaar met JUnit)
- `CombatSpec`: leest de JSON (namen, schalen, afkapping, actiewaarden, sha256).
- `CombatState`: een record met precies de velden van `ml/pvp_sim/obs.py` (`self_x` ... `walls`).
- `CombatObservation.build(CombatState)`: de 43 getallen. Let op de hoekafspraken van Minecraft: yaw 0 kijkt naar +z,
  links draaien verlaagt de yaw, pitch positief kijkt omlaag. "Links" is `(cos yaw, sin yaw)`.
- `CombatAction.decode(logits)`: per kop de hoogste logit (of loten met een temperatuur) naar de waarden uit de spec.
- **De test die alles bewaakt:** `CombatObservationTest` leest `ml/spec/combat_v1_golden.json` (via een
  Gradle-testresource), rekent elke toestand na en vergelijkt op 1e-6; ook de 64 acties. Hij controleert ook dat de
  sha256 in het gouden bestand die van de meegekopieerde spec is. Verandert iemand de spec aan één kant, dan faalt
  deze test (of de Python-test).

### 3. Waar de velden vandaan komen (in `Perception`)
| Veld | In de mod |
|---|---|
| eigen positie, snelheid, yaw, pitch | `bot.getX()`, `getDeltaMovement()`, `getYRot()`, `getXRot()` |
| op de grond, sprinten | `onGround()`, `isSprinting()` |
| aanvalskracht | `bot.getAttackStrengthScale(0.5f)` (gebruikt `Perception` al) |
| beschermingsticks | `bot.hurtTime` (10 na een volle treffer, telt af; komt overeen met de simulatie) |
| health | `getHealth()`, `getMaxHealth()` |
| positie tegenstander | de laatst bekende plek uit `Track` (zoals nu) |
| zichtbaar | `Known.seen()` (zelfde regels als de simulatie: 65 graden, hoofd/midden/voeten) |
| snelheid, grond, sprint, health, `hurtTime` van de tegenstander | alleen als zichtbaar (de formules maskeren de rest) |
| ticks sinds hij sloeg | `BotBrain` ziet al een slag aan de daling van zijn aanvalskracht; bijhouden per doel |
| randen (8) | nieuw: grond voor een afgrond, in de eigen richtingen van de bot (0 = vooruit, tegen de klok in per 45 graden), max 4 |
| muren (8) | nieuw: vrije ruimte op lichaamshoogte in dezelfde richtingen, max 4 |

### 4. `net.battlehub.bot.LearnedCombat`
- Laadt per moeilijkheidsgraad het model uit `difficulties.json` (eigen Java-code: de gewichten; of ONNX Runtime: een
  `OrtSession` per model, één keer geladen).
- Beslist elke 2 ticks en houdt de actie 2 ticks vast; slaan alleen in de eerste tick, draaien verdeeld over beide.
- Voegt per niveau toe wat `difficulties.json` zegt: extra waarnemingsvertraging (de tegenstander zoals hij k ticks
  eerder was, uit `Track`), mikruis per tick, een maximale draai per beslissing, en loten in plaats van de beste actie.
- Controleert bij het laden de metadata (`spec_sha256`): een model voor een andere spec wordt niet gebruikt en de bot
  valt terug op de gescripte besturing, met een melding in de log.

### 5. De naad in `CombatMind`
- Een interface `MeleeController` met `Intent act(Situation, CombatState, Track, Difficulty)`.
- In `CombatMind.tick`: is de gekozen tactiek APPROACH, STRIKE, SPACE of RETREAT, is er geen doel (`s.goal() == null`)
  en staat het model aan voor deze game, dan levert `LearnedCombat` de `Intent`; anders `CombatSteering` zoals nu.
  Reactietijd bij een nieuw doel, helen, randveiligheid (`keepSafe`, `recover`) en de kruisdraadcontrole bij het slaan
  blijven werken zoals nu.

### 6. Configuratie
- `bots.learnedCombat`: `off` (zoals nu), `practice` (alleen practice-duels) of `all`.
- `bots.learnedModes`: in welke games, voorlopig `["sword", "boxing"]`. Sumo pas na fase 1b (randen in de training).

### 7. Testen in de echte server
- JUnit: de gouden test (stap 2), het laden van de modellen en de metadata-check.
- Zelftest: een practice-duel tegen het model raakt een stilstaande A en wint; tegen de gescripte bot een paar
  potjes met de score in de log; geen vallen bij randen.

## Wat jij moet doen of beslissen
1. **Kies hoe het model rekent:** ONNX Runtime (+51 MB) of eigen Java-code (aanbevolen).
2. **Zeg of ik stap 6 en 7 mag bouwen** (ik volg deze handleiding), of dat je het zelf wilt doen.
3. **Speel tegen de bot zodra hij in de mod zit** en vertel wat er raar voelt. De simulatie benadert Minecraft;
   verschillen (knockback-gevoel, randen, dekking) zie je pas in het echt en kunnen terug de training in.
4. **Opnieuw trainen** kan altijd, ook op je eigen pc (een GPU is niet nodig):
   ```
   cd battlehub/ml
   pip install -r requirements-sim.txt -r requirements-train.txt
   python train/selfplay.py --run runs/sword --steps 30000000     # hervat met --resume
   python train/league.py runs/sword --out league                 # Elo-ranglijst, niveaus, ONNX-export
   ```
   Daarna de bestanden uit `league/` naar `src/main/resources/assets/battlehub/ml/` kopiëren.
5. **Verander je de observaties of acties,** dan: `version` in de spec verhogen, `python tools/make_golden.py`
   draaien, opnieuw trainen, en de Java-kant bijwerken tot de gouden test weer slaagt.
