# Inspection: references, comparisons, ledgers and work limits

[Handbook](README.md)

The optional module families of chapters 34 to 40 come with four ways to look
at them without changing anything:

- **WORLD CHECK ONLY=REFS** follows the references between the families (Q02);
- **COMPOSE COMPARE** puts two builds side by side with exact chances (Q03);
- **LEDGER** shows what the modules hold for a character, an item, a pair or an
  expedition, with the rule behind each thing (Q05);
- **WORLD MODULES REPORT** counts the work done and reads it against declared
  limits (Q12);
- **tools/simulate.py** fights builds against a fixed boss over seeds, and keeps
  what it observed apart from the exact chances (Q04).

The previews of the earlier chapters belong here too: `MODCRAFT PREVIEW` (a
craft), `EXPEDITION PREVIEW` (a chart), `WORLD LOOT PREVIEW` and `SAMPLE` (a loot
policy). None of these spends, draws a random value or marks anything as done.

## References between families

`WORLD CHECK ONLY=REFS` (also part of a full `WORLD CHECK`) reads every installed
definition and follows what it names in another family or in the world:

| From | What it names |
| --- | --- |
| A composition | its `requires_ability` (a native ability), the pools its `resources` cost and give; a support must fit at least one installed skill |
| A trigger | its cast skill (a composition), its gain pool, its summoned catalog mob, its cost pools |
| A reward | its catalog items, its pools, its claim rooms |
| An encounter | each spawned catalog mob, each phase's reward, its reward and activity |
| An expedition | its destination (a generated instance area), its return room, each modifier's effect, its activity |
| A track | the skill of each `cast` earning rule |
| A loot policy | its modifier base |
| The item modifier library | each base's pool affixes and successor, each operation's affixes, each recipe's catalog product and its base or policy |

Each broken reference is reported where it is written, as file and JSON path:

```text
References between the module families: 38 followed, 27 found, 10 broken, 1 unknown.
ERROR compositions/fix.orphan.v1.json: /requires_all: support fix.orphan fits no installed skill
ERROR itemmods/workbench.cloak-work.v1.json: /result/catalog: no catalog item 'a woven cloak'
ERROR expeditions/example.depths.v1.json: /destination/area: no area Ember Depths
UNKNOWN directors/fix.scripted.v1.json: /phases/0/spawn/0/mob: 'a fixq02 scripted imp' carries scripts; what they do is not checked
```

What the check cannot see into is **unknown**, not passed or failed: a catalog
mob or item that carries scripts, an expedition generator that runs scripts. In
`WORLD CHECK`, a broken reference is an error in a world that selects the
family it is written in, and a warning otherwise; an unknown is a warning.

## Comparing two builds

`COMPOSE COMPARE example.ember fix.strike [target]` compares two of your composed
skills, each with its supports, against a target in the room, or against one with
the combat profile's defaults:

```text
Build comparison against a cinder imp (profile example.final.v1):
example.ember (Ember bolt): a spell; costs mana 9, movement 0; cooldown 0 tick(s) left
  hit no roll (a spell); blocked 0%; critical 10%
  lands: fire 14; critical: fire 21; blocked: none
  expected damage per cast: 14.70
fix.strike (Heavy strike): a weapon skill; costs mana 2, movement 0; cooldown 0 tick(s) left
  hit 50%; blocked 0%; critical 10%
  lands: physical 20; critical: physical 30; blocked: none
  expected damage per cast: 10.50
example.ember deals 4.20 more expected damage per cast.
```

The chances are the profile's own: each outcome (hit or miss, blocked or not,
critical or not) is worked out by the combat profile's resolver with its rolls
fixed, and weighted by its exact probability. A roll succeeds on a whole number
from 1 to its chance out of 100, so a 37.5% chance is 37%. The target's
conditions, the event budget and the world's own rules can change a real cast.
COMPARE rolls, pays and cools down nothing. A player sees the chances and the
outcomes; the target's own stats are added only in a creator's view (builder
mode or DEV=1).

## Ledgers

| Command | Shows |
| --- | --- |
| `LEDGER` / `LEDGER [character]` | Who controls the character; what each active module holds for them (composed skills, pools, triggers, combat sources, rewards, tracks, a pending craft, an expedition); their recent events; their last refused transition. A player reads their own and their party's. |
| `LEDGER ITEM [item]` | Who holds it; its modifier record (base, revision, how it was made, each modifier's source and protection); a work piece's order, a chart, its tracks. |
| `LEDGER BETWEEN [a] [b]` | Who follows whom, a shared party, and every module's relationship record between the two, in each order. |
| `LEDGER EXPEDITION` | Your run: its definition pin, status, attempts, tier, seed and the scenario that draws it again (the stream, the draw and the campaign seed), modifiers, instance and party. |

The recent events name the module and the rule each came from:

```text
Recent events (since this game began or was restored):
  rewards.escrow (example.room-one.v1): reward 1 offered in example.trial, provisional
  progression.tracks (example.emberwright.v1): milestone 1 reached: Kindled
Last refused: rewards.escrow refused it for Ann (rule claim of reward 2): reward 2 is claimed at Shrine#0. To recover: put right what stops it (room to carry, the place it is claimed at, a reward still provisional), then REWARDS CLAIM again; nothing was given
```

Events are kept in memory, 16 for each of up to 256 characters and items, since
the game began or was restored; what the modules themselves hold is in the save.
A craft that `MODCRAFT COMMIT` refuses, an expedition chart that will not open and
a reward claim that fails are each kept as the last refusal. Reading a ledger
changes nothing and takes no game time.

```javascript
var L = Packages.com.planet_ink.coffee_mud.core.Ledgers;
L.character(mob()); L.item(item); L.between(a, b); L.expedition(mob());
Packages.com.planet_ink.coffee_mud.core.Provenance.event(mob(), "my.module", "my.rule.v1", "what happened");
```

## Simulating builds

`tools/simulate.py` (Q04) fights builds against a fixed boss over a set of seeds,
headless, and reports what it saw apart from what the combat profile says
exactly. A scenario file names everything:

```json
{
  "schema": 1,
  "title": "A supported ember bolt against a heavy strike, on a training warden",
  "packs": ["ability-composition", "combat-profiles"],
  "modules": "abilities.composition,combat.profiles",
  "definitions": {"compositions": [{"schema": 1, "kind": "skill", "id": "sim.strike", "version": 1, "name": "Heavy strike",
                    "tags": ["attack", "melee"], "damage": {"physical": 20}, "stats": {"mana": 2, "actions": 1, "cooldown": 0, "range": 1}}]},
  "builds": [
    {"name": "ember", "skills": [{"id": "example.ember", "supports": ["example.power"]}], "stats": {"schema": 1, "crit_chance": 10}},
    {"name": "strike", "skills": [{"id": "sim.strike"}], "stats": {"schema": 1, "accuracy": 150, "crit_chance": 10}}
  ],
  "boss": {"name": "a training warden", "hp": 300, "stats": {"schema": 1, "evasion": 100, "block": 10, "armor": 20, "resistance": {"fire": 25}}},
  "seeds": [11, 12, 13, 14, 15, 16],
  "turn_limit": 120, "stall_turns": 15, "work_limit": 2000
}
```

| Field | Meaning |
| --- | --- |
| `packs` | Example packs copied over the base world, in order. |
| `definitions` | Extra definitions, by folder (`compositions`, `combat`, ...), each written as `id.vN.json`. |
| `modules` | The world's `MODULES`. |
| `builds` | 1 to 8: a name, composed skills with their supports (cast in the order given, the first ready one each turn), the caster's combat stats (as `Prop_CombatStats` takes them), and `mana` (refilled each turn; the simulation measures damage). |
| `boss` | Its name, hit points, level and combat stats. It does not act. |
| `seeds` | 1 to 64. Each run seeds the campaign dice with one. |
| `turn_limit`, `stall_turns`, `work_limit` | A run stops at the turn limit, or when it has done no damage for `stall_turns` turns (stalled), or when the build has cast `work_limit` times in all. |

```bash
python3 tools/simulate.py SUDDocs/creator/examples/simulation/ember-vs-strike.json --json results.json
```

Each build runs in its own engine process. A turn casts the first ready skill
and moves the clock one tick, so cooldowns pass. The report, per build:

```text
Build strike (its own engine process):
  Observed over 6 seeded runs (sampled):
    killed 6; stalled 0; stopped at the turn limit 0; at the work limit 0
    turns to kill: min 23, median 26.0, mean 26.17, max 29
    damage per turn: 11.46
  Exact, from the combat profile's chances (not sampled):
    sim.strike: expected 11.44 damage per resolved cast (hit 71%, blocked 10%, critical 10%); cooldown 0 tick(s)
    analytical estimate: 26.23 turns to kill (hit points over the expected damage of a cast, one cast whenever the skill is ready)
```

The observed part is sampled: the cast's proficiency check (a cast can fizzle),
damage that overshoots the last hit points and the world's own rules are in it.
The exact part is COMPOSE COMPARE's figure for each skill against the boss, and
the estimate that follows for a one-skill build. The same scenario and seeds give
the same runs. `--seeds 1,2,3` runs other seeds; `--work DIR` keeps each batch's
world and output.

## Work and limits

`WORLD MODULES REPORT` (builder or DEV=1) counts this session's module work by
family, source and measure, and shows each limited measure's highest reading
against its limit:

```text
Optional module work in this session (family | source | measure: count):
  items | crafts | committed: 3
  rewards | example.trial | duplicate events suppressed: 120
  triggers | fix.stress | actions: 200
  triggers | fix.stress | duplicate events suppressed: 200
Highest readings against declared limits:
  rewards | ledger entries | example.trial: 24 of 24
  triggers | actions in one root | fix.stress: 1 of 64
  triggers | depth | fix.stress: 0 of 8
```

The declared limits are the most the engine allows: 64 actions in one causal
root and a depth of 8 (A03), 50 actors at once in an encounter (G03), 24 entries
in a reward ledger (I18). A reading past a limit is counted against its source
(`rewards | example.trial | ledger entries over limit: 1`), so the definition
that did it is named. Catch-up of a named resource after time passes is settled
in one step however long it was (`settled periods`). The report holds families,
definition IDs and counts only: no character's name, room or campaign data.

Scripts and native code can declare their own limits and readings:

```javascript
var MS = Packages.com.planet_ink.coffee_mud.core.ModuleStats;
MS.limit("my.family", "spawns at once", 10);
MS.reading("my.family", "my.source", "spawns at once", n);   // false past the limit
MS.count("my.family", "my.source", "things done");
```

Implementation: [CrossRefs.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/CrossRefs.java),
[BuildCompare.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/BuildCompare.java),
[Ledgers.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Ledgers.java),
[Provenance.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Provenance.java),
[ModuleStats.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/ModuleStats.java).
Simulation: [simulate.py](../../tools/simulate.py), tested by [simulate-test.py](../../tools/simulate-test.py) (`tools/fixes.sh simulate`).
Tests: [q02-refs.js](../../tools/fixtures/fixes/q02-refs.js), [q03-compare.js](../../tools/fixtures/fixes/q03-compare.js),
[q05-ledger.js](../../tools/fixtures/fixes/q05-ledger.js), [q12-stress.js](../../tools/fixtures/fixes/q12-stress.js),
and the expedition ledger in [expedition-integration.py](../../tools/expedition-integration.py).
