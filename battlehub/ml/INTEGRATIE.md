# Het gevechtsmodel in de mod

Hoe het geleerde gevecht in BattleHub zit (stand van 1.38, spec v2 met actiemaskers), en wat je moet doen als je opnieuw
traint of de spec verandert. De simulatie en de training staan in `battlehub/ml`, de mod in `battlehub/src`.

## Hoe het werkt

```
BotBrain.melee()                         elke tick
  Perception  ->  Situation              wat de bot weet (zicht, gehoor, geheugen)
  CombatMind.tick(s)
     Decider kiest een tactiek           gescript: vechten, helen, schieten, het doel van de game
     de controller mag beslissen?
        ja  -> LearnedFight.act(...)     model v2 (of LearnedMelee: v1 in sword en boxing)  -> Intent
        nee -> CombatSteering.steer      gescript  -> Intent
  execute(Intent)                        invoer, kruisdraadcontrole, randveiligheid, hand en rechtermuisknop
```

Elke 2 ticks doet `LearnedFight` dit:
1. `FightState.build` maakt een `CombatState` uit wat de bot weet: kit en effecten, wat hij bij zich heeft en vasthoudt,
   het doelwit zoals waargenomen (op lage niveaus een paar ticks vertraagd), een tweede vijand, een projectiel op hem af,
   en het terrein in 8 richtingen (`TerrainRays` over `WorldColumns`).
2. `CombatObservation.build` rekent de 118 observaties uit, met precies dezelfde formules als `ml/pvp_sim2/obs.py`.
3. Het model (ONNX Runtime) geeft 38 logits: 9 koppen (vooruit, opzij, springen, sprinten, slaan, draaien, kijken, hand,
   rechtermuisknop).
4. `CombatMasks` zet wat niet mag op min oneindig: geen hand die de bot niet bij zich heeft, geen eten bij volle honger,
   geen rechtermuisknop als er niets te gebruiken is (`ml/spec/combat_v2_masks.json`).
5. Per kop de hoogste toegestane logit, of op lage niveaus een trekking (`CombatAction.best` en `sample` met masker).
6. De actie geldt 2 ticks: slaan in de eerste tick, de draai verdeeld over beide. Hand en rechtermuisknop alleen voor
   de spullen die het model geleerd heeft (de lijst "items" in zijn metadata; de master van 1.38: food, gapple, wind)
   en met `bots.learnedItems` aan. Een hand die niet op de lijst staat, maskeert de mod. Anders eet, schiet en gooit
   de bot met zijn eigen regels, en mag het model alleen het schild opsteken.

De strategie blijft gescript: looten, bedden, bruggen en de goal. Het model neemt alleen het gevecht over. Met een doel
vecht het alleen binnen 8 blokken, en een vijand die de bot niet ziet ook alleen binnen 8 blokken (verder weg zoekt de
mind een pad om dekking heen).

## Bestanden

| Bestand | Waar | Wat |
|---|---|---|
| `ml/spec/combat_v2.json` | gekopieerd naar `assets/battlehub/ml` | de spec: observaties, schalen, acties (sha256 in elk model) |
| `ml/spec/combat_v2_masks.json` | idem | de maskers, aan de sha van de spec gebonden |
| `ml/spec/combat_v2_ranges.json`, `lessons.json` | idem | wat het model in training zag, en wat het moet oefenen (modelrapporten) |
| `combat_v2_<niveau>.onnx`, `difficulties_v2.json` | `src/main/resources/assets/battlehub/ml` | één model per niveau en de extra's per niveau |
| `ml/spec/combat_v2_golden.json`, `combat_v2_masks_golden.json` | testresource | golden-gevallen voor de Java-tests |

## Wat de tests bewaken

- `CombatObservationV2Test`: elke observatie van de golden-gevallen op 1e-6, de acties en de terreinstralen, gelijk
  aan Python.
- `CombatMasksTest`: het masker en de beste toegestane keuze van 211 gevallen gelijk aan Python, ook aan de randen
  (volle honger, een schild met cooldown, niets bij zich); trekken kiest nooit iets wat niet mag.
- `CombatModelsTest`: de meegeleverde modellen geven in ONNX Runtime wat PyTorch gaf.
- `DecisionTimerTest`: de tijdmeting per beslissing.
- Python: `test_golden2.py` en `test_masks.py` controleren dezelfde bestanden vanaf de kant van de simulatie.

## Wat een beslissing kost

`/battlehub bots inspect <bot>` toont het gemiddelde, de p99 en het maximum per beslissing over alle bots, en hoeveel
daarvan de maskers kosten. De zelftest van het v2-bijlduel zet dezelfde regel in de log. De gemeten waarden staan in
`PLAN.md` onder ML fase 3.

## Opnieuw trainen

```
cd battlehub/ml
pip install -r requirements-sim.txt -r requirements-train.txt
python tools/make_bc_data.py --model <checkpoint> --out bc.npz                 # wat de leraar met zijn spullen doet
python train/bc.py --data bc.npz --init <checkpoint> --out runs/bc --epochs2 0 # nadoen: alleen hand en use
python train/selfplay2.py --run runs/v3a --init runs/bc/ckpt_bc.zip --pool-from runs/v2 --masks --extra-items 1.0 \
    --scripted-latch --steps 14000000                                          # zo is de master van 1.38 gemaakt
python train/eval2.py runs/v3a/ckpt_012000000.zip --hands food,gapple,wind      # winst en spullen per gevecht
python train/league2.py --versus "runs/v3a/ckpt_012000000.zip#hands=food,gapple,wind" <master> --episodes 120
python train/league2.py --include <checkpoints van de huidige niveaus> runs/v3a/ckpt_012000000.zip \
    --items-from runs/v3a --item-hands food,gapple,wind --levels master \
    --keep ../src/main/resources/assets/battlehub/ml/difficulties_v2.json --out league_v3a
```

Daarna de `.onnx`-bestanden en `difficulties_v2.json` uit de league-map naar `src/main/resources/assets/battlehub/ml/`,
en de `.check.json` van elk nieuw model naar `src/test/resources/net/battlehub/ml/`. De BC-term naast PPO
(`--bc-data`) is er nog, maar haalde in fase 3A het gevecht onderuit (zie `PLAN.md`).
Een model met de vlag "items" kiest zijn spullen zelf (met `bots.learnedItems`); andere modellen laten de spullen aan de
regels van de bot.

## Als je de spec verandert

1. `version` in de spec ophogen; de maskers en ranges horen bij de sha, dus die bestanden ook opnieuw maken
   (`tools/make_ranges.py`, en de `spec_sha256` in `combat_v2_masks.json` nalopen).
2. `python tools/make_golden2.py`: schrijft beide golden-bestanden.
3. Opnieuw trainen en exporteren.
4. De Java-kant bijwerken tot de golden-tests weer slagen.
