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
- **tools/simulate.py** fights builds against a fixed boss (passive, or fighting
  back) over seeds, and keeps what it observed apart from the exact chances (Q04).

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
| `cultures.ini`, `origins.ini` | a culture's abilities; an origin's `CULTURE` |
| `talents.ini` | each talent's lore entries and its ability |
| `challenges.ini` | the achievements a book names |
| `formations.ini` | the role a role `GUARDS` |
| `orgpools.ini` | a pool's `MATERIAL` |
| `affiliations.ini` | the organizations its `TAG`, `KEEP` and `LEAVE` lines name, and the abilities `LEAVE` takes |
| A service record (`Prop_Service`) on an NPC in the world or the catalog | each service's pool and organization; its workstation, which must be in the NPC's room; and whether something on the NPC answers its words (a `SPEECH_PROG` naming one of them, or `ALL`) |
| An offer book (`Prop_OfferBook`) on a mob or item in the world or the catalog | its catalog items |

Each broken reference is reported where it is written, as file and JSON path
(for the world files, the file and the entry; for a record on a mob or item, its
room or catalog entry and the record's path):

```text
References between the module families: 38 followed, 27 found, 10 broken, 1 unknown.
ERROR compositions/fix.orphan.v1.json: /requires_all: support fix.orphan fits no installed skill
ERROR itemmods/workbench.cloak-work.v1.json: /result/catalog: no catalog item 'a woven cloak'
ERROR expeditions/example.depths.v1.json: /destination/area: no area Ember Depths
UNKNOWN directors/fix.scripted.v1.json: /phases/0/spawn/0/mob: 'a fixq02 scripted imp' carries scripts; what they do is not checked
```

What the check cannot see into is **unknown**, not passed or failed: a catalog
mob or item that carries scripts, an expedition generator that runs scripts, an
NPC whose scripts are loaded from files or run JavaScript (whether they answer a
service's word), a catalog NPC's workstation (it has no room until placed).
A service record that cannot be read at all is broken, and `PROPCHECK` says why. In
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
| `LEDGER` / `LEDGER [character]` | Who controls the character; what each active module holds for them (composed skills, pools, triggers, combat sources, rewards, tracks, a pending craft, an expedition); their culture and what it gave, their decided talents, their formation role; their recent events; their last refused transition. A player reads their own and their party's. |
| `LEDGER ITEM [item]` | Who holds it; its modifier record (base, revision, how it was made, each modifier's source and protection); a work piece's order, a chart, its tracks; its binding; the kind one must learn to know it. |
| `LEDGER BETWEEN [a] [b]` | Who follows whom, a shared party, and every module's relationship record between the two, in each order. |
| `LEDGER EXPEDITION` | Your run: its definition pin, status, attempts, tier, seed and the scenario that draws it again (the stream, the draw and the campaign seed), modifiers, instance and party. |

The recent events name the module and the rule each came from. Besides the
module families, they hold the character's service payments, holds and refunds
(`services`), their gifts to and takings from shared pools and the spends made
for them (`orgpools`), each culture taken up or left (`cultures`), each talent
decided (`talents`), new personal records (`challenges`), formation roles given
and falls back (`formations`), what leaving an organization took
(`affiliations`), offers held, rerolled and bought (`offers`); and an item's
binding and its end (`binding`). A refused service, pool action, offer, culture
or join is the last refused transition:

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
exactly. The boss stands still by default, or [fights back](#a-boss-that-fights-back).
A scenario file names everything:

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
| `profile` | Optional `{"id", "version"}`: the combat profile the runs use, chosen as `WORLD COMBAT USE` chooses it, when it is not the module's default (one defined in `definitions`, say). |
| `builds` | 1 to 8: a name, composed skills with their supports (cast in the order given, the first ready one each turn: off its cooldown, and its named resource costs payable), the caster's combat stats (as `Prop_CombatStats` takes them), and `mana` (refilled each turn; the simulation measures damage). Optional: `hp` (the caster's hit points; 100000 unless the boss acts), `pools` (named resource pools it holds from the start, at their start values: a guard pool, a combo) and `effects` (properties put on it, each `{"id", "text"}`: a `Prop_Reserve` reservation, say). |
| `boss` | Its name, hit points, level and combat stats. It does not act unless `acts` is true ([below](#a-boss-that-fights-back)); it can have elite `traits`, `pools` and `effects` either way. |
| `seeds` | 1 to 64. Each run seeds the campaign dice with one. |
| `turn_limit`, `stall_turns`, `work_limit` | A run stops at the turn limit, or when it has done no damage for `stall_turns` turns (stalled), or when the build has cast `work_limit` times in all. |

```bash
python3 tools/simulate.py SUDDocs/creator/examples/simulation/ember-vs-strike.json --json results.json
```

Each build runs in its own engine process. A turn casts the first ready skill
and moves the clock one tick (and the turn count one), so cooldowns pass and
pools change. The report, per build:

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

### A boss that fights back

With `"acts": true` the boss fights the caster with the engine's own combat, so
the caster's hit points, its guard pool and the ailments the boss inflicts
matter ([example](examples/simulation/frost-warden.json)):

```json
"profile": {"id": "sim.frost", "version": 1},
"formations": "[GUARD]\nNAME=Guard\nROW=0\nTARGET=ATTACKER\nINTERCEPT=50\n",
"builds": [{"name": "warded", "hp": 150, "pools": ["sim.ward"], "skills": [{"id": "sim.finisher"}, {"id": "sim.strike"}]}],
"allies": [{"name": "Gil the shield-bearer", "hp": 80, "role": "guard"}],
"boss": {"name": "a frost warden", "hp": 300, "level": 3, "acts": true, "skills": [{"id": "sim.frostclaw"}], "traits": ["sim.brute"]}
```

| Field | Meaning |
| --- | --- |
| `boss.acts` | The boss fights back. Each build then needs `hp`, and a run also stops when the caster falls. |
| `boss.skills` | Composed skills the boss casts at its foe, the first ready one each turn, as a build's are. They need `acts`. |
| `boss.traits` | [Elite traits](37-encounter-director.md#elite-traits) from the world's `traits/`, given to the boss as the encounter director gives them to a foe it spawns: their effects work, and the hit points they add count (the report gives the boss's hit points with them). |
| `boss.damage`, `boss.attack`, `boss.armor` | Its native melee figures (what a combat round uses), over what its level gives. |
| `allies` | Up to six, each with a `name` and `hp`, and optionally `level`, `damage`, `attack`, `armor`, `stats` (as `Prop_CombatStats` takes them: their defenses against the boss's composed skills) and a `role`. They follow the caster and fight the boss. They need `acts`. |
| `formations` | The text of the run's `formations.ini`: the [formation roles](31-saves-and-party.md#formation-roles) the allies' `role` names. An ally is given its role as `FORMATION name ROLE role` gives it. |

A turn, with the boss acting:

1. The caster casts its first ready skill at the boss.
2. Each ally standing takes a combat round (`tickCombat`): its role reacts
   (draws an attacker off, falls back) and picks its foe, then it attacks.
3. The boss turns on its foe: the caster, or whoever drew it off (back to the
   caster when that one falls). It casts its first ready composed skill at it,
   or, with none ready, takes a combat round with its natural attack.
4. Every fighter's effects tick once: ailment meters decay and statuses and
   immunity count down, burns burn, leech comes back, the traits' effects tick.
5. The clock moves one tick and the turn count one.

The fight is not broken off between turns (a combo that resets when a fight
ends keeps its steps), while a passive boss is still made peaceful after each
turn. The report adds, per build: how often the caster fell, its hit points
left on average (a fallen caster counts 0) and the damage it took per turn; the
boss's composed casts and combat rounds; the allies fallen and the damage they
dealt; the ailments begun on each side (a status beginning: `on the caster chill
6`); what the guard pool absorbed of the boss's hits on the caster and of the
caster's on the boss; the turns a reservation on the caster did not fit; and
the optional modules' own counters over the runs, as `WORLD MODULES REPORT`
counts them (`encounters | sim.brute | traits given`, `resources | sim.combo |
gained`, `reset on miss`, `spent`, `formations | guard | interceptions`).
Under the exact figures it adds the boss's skills against the caster:

```text
Build bare (its own engine process):
  Observed over 6 seeded runs (sampled):
    killed 3; stalled 0; stopped at the turn limit 0; at the work limit 0; the caster fell 3
    turns to kill: min 27, median 30, mean 30, max 33
    damage per turn: 12.14 (the allies dealt 129 in all)
    the caster (150 hit points): 29.33 left on average (a fallen caster counts 0); 3.73 damage taken per turn
    the boss: 97 composed casts and 94 combat rounds in all
    allies fallen: 6 in all
    ailments begun: on the allies chill 6; on the caster chill 10
  Exact, from the combat profile's chances (not sampled):
    the boss's sim.frostclaw on the caster: expected 9.50 damage per resolved cast (hit 95%, blocked 0%, critical 0%); cooldown 2 tick(s)
```

What it leaves out:

- **Guard pools and ailments come with composed hits only**, as in play: a
  combat round (the boss's natural attack, the allies' attacks) goes to hit
  points by the world's own rules and builds no ailment. A boss inflicts
  ailments and meets a guard pool through its `skills`.
- **Only the effects tick.** The fighters' own ticks are not run: no hit point
  or mana recovery, no behaviors or scripts, no fleeing; mana is refilled before
  each cast. The allies have no composed skills or pools of their own.
- **Traits are named, not drawn.** The scenario gives the boss its traits; a
  director's picks within a power budget are not made, and a trait that
  possesses never passes on (the boss has no pack).
- **Reservations have no engine counter**: the simulation counts the turns
  each reservation on the caster does not fit (`NamedResources.fits`).
- **The exact figures** are still the composed skills' against their target;
  combat rounds, allies, guard pools and ticks are only observed.

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
