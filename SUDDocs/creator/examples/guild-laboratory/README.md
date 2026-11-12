# Worked pack: the guild laboratory

[Chapter 39](../../39-progression-tracks.md) · [Chapter 17](../../17-item-modifiers-crafting.md) · [All packs](../README.md)

A fellowship's laboratory at the [Ember Trial](../ember-trial/README.md)'s camp.
A party founds a fellowship; its members weave a laboratory mantle at the
laboratory bench, one stage at a time; each finished mantle adds 3 points of
the fellowship's research. The unfinished piece keeps its stages across a save.

| File | What it is |
| --- | --- |
| `modules/progression.tracks.json` | The tracks module's manifest (as in the [tracks pack](../progression-tracks/README.md)). |
| `tracks/lab.research.v1.json` | `lab.research`, an organization track: 3 points a finished craft (a staged recipe counts once, when its product is made), or 10 coin a point given with `TRACKS CONTRIBUTE`. Level 1 (3 points) sets up the benches (`lab-benches`), level 2 (9 points) lights the furnace (`lab-furnace`). |
| `itemmods/lab.mantle-work.v1.json` | `lab.mantle-work`, a staged recipe at a `guild-lab` station: *Cut the cloth* (1 IRON), *Bind the seams* (1 IRON), then a lab mantle with one modifier from the Ember Trial's mantle base. |
| `resources/creator/lab-build.js` | The builder recipe: a laboratory bench (`Prop_Workstation TAG=guild-lab`) at camp and the catalog item `a lab mantle`. |

It needs `progression.tracks` and `items.modifiers` (the Ember Trial selects the
second). The three worked packs, this one, the
[bonded familiar](../bonded-familiar/README.md) and the
[branching expedition](../branching-expedition/README.md), install into one
deployment the same way; the steps below install all three.

## Create

From the repository root, with Python 3.8+ and a JDK on PATH. Make and publish
an Ember Trial deployment, then copy the packs into its world:

```bash
python3 SUDDocs/creator/examples/ember-trial/make_world.py /tmp/sud-packs
```

```bash
for pack in guild-laboratory bonded-familiar branching-expedition; do (cd SUDDocs/creator/examples/$pack && cp -r $(ls -d */) /tmp/sud-packs/world/); done
```

In `/tmp/sud-packs/world/world.ini`, add the packs' modules to the `MODULES`
line, and let a fellowship be founded without coin:

```ini
MODULES=embertrial.adventure,progression.tracks,abilities.triggers,rewards.escrow,expeditions.contracts
CLANCOST=0
```

Open the deployment's builder (its profile, `Trialbuilder`, was made when the
deployment was published):

```bash
CoffeeMud/sud.sh BUILDER=1 WORLDDIR=/tmp/sud-packs/world DBSERVICE=jdbc:fakedb:/tmp/sud-packs/db SAVEDIR=/tmp/sud-packs/.build/saves TIMEMODE=TURNS
```

Log in as `Trialbuilder`, then:

```text
JRUN /resources/creator/lab-build.js
JRUN /resources/creator/familiar-build.js
JRUN /resources/creator/branch-build.js
WORLD CHECK ONLY=MODULES,REFS,TRACKS,TRIGGERS,EXPEDITIONS,REWARDS,ITEMMODS
SAVE
QUIT
```

Each recipe says what it built (`LAB BUILT: ...`). The check should end with
`WORLD CHECK: 0 errors`; answer the quit prompt with `Y`. Run each recipe once:
they add to the world each time.

## Play

```bash
python3 SUDDocs/creator/examples/ember-trial/play.py /tmp/sud-packs --new
```

Make a character (Human/Apprentice works). At camp:

```text
CLANCREATE The Ember Lab party
OPEN supply coffer
GET "cinder iron" coffer
MODCRAFT BEGIN lab.mantle-work
MODCRAFT PREPARE lab.mantle-work "unfinished lab mantle"
MODCRAFT COMMIT
MODCRAFT STATUS
SAVE lab
QUIT
```

`CLANCREATE` founds a party-type fellowship (`Your party founded The Ember Lab`).
`BEGIN` sets the unfinished lab mantle out on the bench; `PREPARE` shows the
first stage and its cost and prints the request number; only `COMMIT` spends the
iron. `STATUS` shows the work piece: `1 of 2 stages done; next Bind the seams`.

## Restore

```bash
python3 SUDDocs/creator/examples/ember-trial/play.py /tmp/sud-packs --restore lab
```

```text
MODCRAFT STATUS
MODCRAFT PREPARE lab.mantle-work "unfinished lab mantle"
MODCRAFT COMMIT
TRACKS
LEDGER
```

The piece kept its first stage: `STATUS` still shows `1 of 2 stages done`. The
second stage makes the lab mantle, its one modifier drawn as it is made
(`... a lab mantle of Embers is finished`). That finished craft gives the
fellowship 3 research: `TRACKS` shows `Laboratory research: level 1 of 2, 3
research (6 to level 2)` and `[x] 1. Benches set up (unlocks lab-benches)`.
`LEDGER` shows the same tree under `[progression.tracks]`, and the committed
craft among its recent events, under `items.modifiers (craft)`. The research
belongs to the fellowship: every member sees it. The furnace needs 6 more
points: craft again, or give coin, 10 a point (`TRACKS CONTRIBUTE lab.research 60`;
the [branching expedition](../branching-expedition/README.md)'s spoils pay for it).

Tested by [packs-integration.py](../../../../tools/packs-integration.py) (`tools/fixes.sh packs`).
