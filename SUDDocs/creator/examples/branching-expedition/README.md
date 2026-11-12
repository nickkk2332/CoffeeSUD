# Worked pack: the branching expedition

[Chapter 38](../../38-expeditions.md) · [Chapter 36](../../36-reward-escrow.md) · [All packs](../README.md)

A chart-sealed stair west of the [Ember Trial](../ember-trial/README.md)'s camp
leads into a generated crypt. The chart offers two branches drawn from its
seed: the hardy branch (foes with half again their health) and the marked
branch (foes that burn through 10 fire resistance). The party chooses one and
goes in. The expedition is complete when every foe inside is defeated, and the
spoils, 75 in coin, are banked in the run's activity until claimed.

| File | What it is |
| --- | --- |
| `modules/expeditions.contracts.json`, `modules/rewards.escrow.json` | The two module manifests (as in the [expedition](../expeditions/README.md) and [reward](../reward-escrow/README.md) packs). |
| `expeditions/branch.depths.v1.json` | `branch.depths`: the gated destination `Branching Depths`, back to camp (`EmberTrial#0`); offer 2 of the modifiers, choose 1; two attempts; up to 4 in the party; the activity `branch.run`; `"complete": "cleared"` and `"reward": "branch.spoils"`. |
| `rewards/branch.run.v1.json` | `branch.run`, the run's activity: leaving and death keep its rewards, a failed run forfeits them, a restore keeps them. |
| `rewards/branch.spoils.v1.json` | `branch.spoils`, 75 in coin. |
| `resources/creator/branch-build.js` | The builder recipe: the `StdAutoGenInstance` area `Branching Depths` (the crypt generator, `randareas/crypt.xml`, area ID `crypt_dungeon`), its stair west of camp, `a branching chart` (`Prop_ExpeditionChart EXPEDITION=branch.depths SEED=0 TIER=1`) and a hooded lantern at camp (the crypt is dark). |

`SEED=0` lets each chart draw its own seed when it is first read; give a number
to fix the branches offered and the crypt's layout.

## Create

The three worked packs install together: follow [Create](../guild-laboratory/README.md#create)
in the guild laboratory's guide. This pack alone needs `expeditions.contracts`
and `rewards.escrow` in `MODULES`.

## Play

```bash
python3 SUDDocs/creator/examples/ember-trial/play.py /tmp/sud-packs --new
```

Make a character. At camp:

```text
WEST
GET "branching chart"
GET lantern
HOLD lantern
EXPEDITION PREVIEW chart
EXPEDITION OPEN chart marked
WEST
```

Before the chart is opened the stair refuses you (`Only an opened expedition
chart leads there.`). The crypt is dark: holding the lantern lights it (`You
light up a hooded lantern.`). `PREVIEW` shows the chart's seed and the branches
offered (`Modifiers offered (choose 1):`) and changes nothing; `OPEN` names the
one chosen (use one the preview offered) and consumes the chart (`You open a
branching chart: expedition 1 begins. Go to its destination.`). Going west
makes the instance; its rooms and foes come from the chart's seed.
`EXPEDITION STATUS` shows the run; `LEDGER EXPEDITION` its seed and the scenario
that draws it again.

Defeat every foe inside (the Ember Trial's skills and `TRIAL REST` back at camp
help). When the last falls: `The expedition is complete. Its reward waits for
you: REWARDS.` Then:

```text
REWARDS
REWARDS CLAIM
SAVE depths
QUIT
```

`REWARDS` lists `1. banked, Spoils of the Branching Depths: 75 in coin`;
`REWARDS CLAIM` pays it (`Claimed reward 1: 75 gold coins.`). A death inside
uses an attempt and keeps what the run holds; a run that runs out of attempts
fails, and its rewards are forfeit (`on_fail`). Kills in the crypt count like
any other: with the [bonded familiar](../bonded-familiar/README.md)'s charm,
they grow its bond.

## Restore

```bash
python3 SUDDocs/creator/examples/ember-trial/play.py /tmp/sud-packs --restore depths
```

```text
REWARDS
EXPEDITION STATUS
```

You are back in the crypt where you saved. `REWARDS` shows `1. claimed, Spoils
of the Branching Depths: 75 in coin`, and `EXPEDITION STATUS` says `You are not
on an expedition.`: the run is complete, and once you leave the crypt its stair
no longer lets you back in. Save before the last foe
instead, and the restored run is still open, in the same instance with the same
attempts left.

Tested by [packs-integration.py](../../../../tools/packs-integration.py) (`tools/fixes.sh packs`).
