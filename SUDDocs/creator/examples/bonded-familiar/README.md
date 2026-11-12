# Worked pack: the bonded familiar

[Chapter 35](../../35-causal-triggers.md) · [Chapter 39](../../39-progression-tracks.md) · [All packs](../README.md)

A charm at the [Ember Trial](../ember-trial/README.md)'s camp. Whoever wears it
calls an ember wisp to their side when they make a kill (at most once in 30
ticks; the wisp stays 60). Each kill also grows the wearer's bond: at the first
point the wisp's warmth gives 5 fire resistance in composed combat; at the
third the bond is complete.

| File | What it is |
| --- | --- |
| `modules/abilities.triggers.json`, `modules/progression.tracks.json` | The two module manifests (as in the [trigger](../causal-triggers/README.md) and [tracks](../progression-tracks/README.md) packs). |
| `triggers/familiar.call.v1.json` | `familiar.call`: on a kill, summon the catalog mob `an ember wisp` for 60 ticks; a 30-tick cooldown. |
| `tracks/familiar.bond.v1.json` | `familiar.bond`, a character track: a point a kill, a level a point, up to 3. Level 1, *The wisp knows you*, gives 5 fire resistance; level 3, *Bonded*, unlocks `bonded-wisp`. Its owner may reset it (`TRACKS RESET familiar.bond`). |
| `resources/creator/familiar-build.js` | The builder recipe: the catalog mob `an ember wisp`, `a familiar's charm` (worn on the neck, `Prop_Triggers TRIGGERS=familiar.call`) at camp, and two cinder moths to practise on in the training stones. |

## Create

The three worked packs install together: follow [Create](../guild-laboratory/README.md#create)
in the guild laboratory's guide. That makes `/tmp/sud-packs`, copies all three
packs, selects their modules and runs `familiar-build.js` with the others. This
pack alone needs `abilities.triggers` and `progression.tracks` in `MODULES`.

## Play

```bash
python3 SUDDocs/creator/examples/ember-trial/play.py /tmp/sud-packs --new
```

Make a character. At camp:

```text
GET charm
WEAR charm
EAST
COMPOSE CAST embertrial.ember cinder moth
TRACKS
LOOK
SAVE familiar
QUIT
```

The ember bolt kills the moth (`A cinder moth is DEAD!!!`); the charm's trigger
calls the wisp (`An ember wisp appears at <your name>'s side.`), and the kill
starts the bond (`The wisp knows you. The wisp's warmth settles on you.`).
`TRACKS` shows `Familiar bond: level 1 of 3, 1 bond (1 to level 2)` and
`[x] 1. The wisp knows you (resistance.fire=5)`. `LOOK` shows the wisp:
`An ember wisp drifts here.` It goes when its 60 ticks are up.

## Restore

```bash
python3 SUDDocs/creator/examples/ember-trial/play.py /tmp/sud-packs --restore familiar
```

```text
TRACKS
```

The bond is kept: `Familiar bond: level 1 of 3`. The second cinder moth in the
training stones adds another point, and level 2; the wisp answers a kill again
once its 30-tick cooldown has passed.

Tested by [packs-integration.py](../../../../tools/packs-integration.py) (`tools/fixes.sh packs`).
