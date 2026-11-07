# Inspection: references, comparisons, ledgers and work limits

[Handbook](README.md)

The optional module families of chapters 34 to 40 come with four ways to look
at them without changing anything:

- **WORLD CHECK ONLY=REFS** follows the references between the families (Q02);
- **COMPOSE COMPARE** puts two builds side by side with exact chances (Q03);
- **LEDGER** shows what the modules hold for a character, an item, a pair or an
  expedition, with the rule behind each thing (Q05);
- **WORLD MODULES REPORT** counts the work done and reads it against declared
  limits (Q12).

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
Tests: [q02-refs.js](../../tools/fixtures/fixes/q02-refs.js), [q03-compare.js](../../tools/fixtures/fixes/q03-compare.js),
[q05-ledger.js](../../tools/fixtures/fixes/q05-ledger.js), [q12-stress.js](../../tools/fixtures/fixes/q12-stress.js),
and the expedition ledger in [expedition-integration.py](../../tools/expedition-integration.py).
