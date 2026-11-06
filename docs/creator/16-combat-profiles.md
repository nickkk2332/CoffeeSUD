# Optional combat profiles and result explanations

Stage 3 implements the first A09/A04 slice: versioned damage/defense profiles,
two conversion models, removable equipment/effect stats, and explanations of
**resolved** events. It extends [ability composition](15-ability-composition.md)
and is off by default. Installing files alone enables nothing.

The integration covers composed direct hits and burns created by profiled
casts. Ordinary weapon attacks, native spells and existing effects retain their
native combat rules. Profile stats do not automatically alter those abilities.
This is a foundation for further adapters, rather than a global combat replacement.

Stage 4's [Ember Trial](examples/ember-trial/README.md) is a ready-to-build
playable example combining these rules with skills, equipment, enemies and a boss.

## Install and play

In a [separate deployment](examples/README.md), install the parent composition
pack first, then merge these directories into its world:

```sh
cp -R docs/creator/examples/combat-profiles/combat /your/deployment/world/
cp docs/creator/examples/combat-profiles/modules/*.json /your/deployment/world/modules/
```

Merge this root selection into `world.ini`, preserving other modules/settings:

```ini
MODULES=combat.profiles
```

The manifest declares `abilities.composition` as a dependency, so selecting the
child selects both. New campaigns adopt them. For an existing campaign, use
`WORLD MODULES ADOPT combat.profiles` after world selection. Grant `Skill_Compose`
through normal authoring tools as described in the parent guide. Neither module
grants abilities or adds monster stats automatically.

```text
WORLD MODULES CHECK
WORLD COMPOSITIONS CHECK
WORLD COMBAT CHECK
WORLD CHECK ONLY=COMBAT
COMPOSE LEARN example.ember
COMPOSE LINK example.ember example.burning
COMPOSE SHOW example.ember
COMPOSE CAST example.ember goblin
COMBATRESULT
COMBATRESULT 2
COMBATRESULT STATS
```

`COMBATRESULT [1-8]` reads your recent results; `STATS` shows your current merged
profile stats. Both have zero scheduler action cost and do not rerun events,
consume random numbers or write campaign state. A chain records one result per
target; each burn tick records a result. Inspection therefore may show a burn
as the newest entry. Results are separate for party characters.

Creators can use `WORLD COMBAT TRACE [character]` and `WORLD COMBAT STATS
[character]` to inspect available participants. These require builder mode,
`DEV=1`, or the existing room-creation permission. Players inspect only their
own character. Set `WORLD MODULES CONFIGURE combat.profiles player_trace false`
to hide both player inspection forms; creator tools remain available.

## Author a profile

Files are flat UTF-8 `world/combat/<id>.v<version>.json` definitions:

```json
{
  "schema": 1, "id": "myworld.combat", "version": 1,
  "title": "My combat rules", "conversion": "final",
  "hit": {"minimum": 5, "maximum": 95},
  "armor": {"constant": 5, "maximum_reduction": 90},
  "resistance": {"minimum": -100, "maximum": 75},
  "block_cap": 75, "block_reduction": 100,
  "maximum_damage": 1000000,
  "defaults": {"accuracy": 100, "crit_multiplier": 150}
}
```

Schema, ID, positive version and plain-text title are required. Other fields
default to the values above; omitted defaults have accuracy 100, critical
multiplier 150%, and zero other numeric stats. Defaults apply equally to every
participant. Supported types are `physical`, `fire`, `cold`, `electric`, `acid`.

| Field | Allowed values |
| --- | --- |
| `conversion` | `final` or `history` |
| `hit.minimum`, `hit.maximum` | 0–100%; minimum must not exceed maximum |
| `armor.constant` | 0.01–100 |
| `armor.maximum_reduction` | 0–100% |
| `resistance.minimum` | −100–0% |
| `resistance.maximum` | 0–100% |
| `block_cap`, `block_reduction` | 0–100% |
| `maximum_damage` | Positive integer, at most 1,000,000 per component |

Definitions reject unknown/duplicate keys, incorrect types, mismatched filenames,
symlinks, files outside the world, more than 128 files and files over 64 KiB.
Even title edits change the canonical SHA-256 content hash.

## Add defense and offense stats

Attach `Prop_CombatStats` to a MOB or item through its effects editor. Its
parameter text is a strict JSON **delta**, for example:

```json
{
  "schema": 1,
  "armor": 200, "spell_block": 15,
  "resistance": {"fire": 50, "cold": -20},
  "penetration": {"fire": 20},
  "crit_chance": 10, "crit_multiplier": 50,
  "immune": ["burning"]
}
```

This adds 50 percentage points to the default critical multiplier: 150% becomes
200%. Immunities are a union; negative numeric deltas can offset other sources,
but cannot remove immunity. `immune` accepts the five types plus `burning`.
Fire immunity also prevents fire burns. Burning immunity permits direct fire
hits while preventing/removing this burn effect.

| Stat | Per-property delta | Final aggregate |
| --- | --- | --- |
| `accuracy`, `evasion`, `armor` | ±1,000,000 | 0–10,000,000 |
| `block`, `spell_block`, `crit_chance` | ±100 points | 0–100% |
| `crit_multiplier` | ±1,000 points | 100–1,000% |
| `resistance.TYPE` | ±200 points | Clamped by profile during resolution |
| `penetration.TYPE` | ±100 points | 0–100% |

Nested profile `defaults` accepts the same fields, but supplies absolute starting
values: nonnegative scalars, critical multiplier at least 100%, and nonnegative
penetration. Its optional schema, if present, must be 1. Properties require
`schema:1` and are limited to 8,192 characters each.

Sources are MOB effects plus effects on **owned, worn, top-level items**. Items
in inventory, in containers, or owned by someone else contribute nothing. There
are at most 64 active sources per participant. Removing an effect or unequipping
an item removes its contribution without permanently changing ordinary stats.
An invalid primary participant source refuses a cast before payment; invalid
secondary targets are rejected and explained. `WORLD CHECK ONLY=COMBAT` also
checks properties on loaded MOBs and items.
Attacker stats are sampled once for the whole cast; defender stats are rebuilt
for each hit and each burn tick. Mid-cast equipment changes affect the attacker's
next cast and the defender's next hit/tick.

These are independent stats: native armor rating, saving throws, intelligence,
equipment bonuses and race immunities do not automatically become profile stats.
Explicitly author the profile properties your game needs. Native ability
proficiency, prerequisites, costs/components and external world rules still run.

## Calculation order

1. Validate the pinned composition/profile and primary participants. Pay normal
   native resources once for the cast, then test the learned adapter's proficiency.
2. Sample target conditions; apply composition flats and one simultaneous
   conversion pass. Apply increased/more bonuses using the selected model, then
   final-type floors, caps and composition safety limits. A floor raises only a
   type the hit deals; it never adds one.
3. Attacks roll against `100 * accuracy / (accuracy + evasion)`, clamped to the
   profile's hit range. Zero total starts at zero before clamping. Spells skip
   accuracy. One hit roll covers all components against that target.
4. Roll target `block` for attacks or `spell_block` for spells, capped by the
   profile. Full block stops before critical calculation. Otherwise roll the
   attacker's critical chance once for the entire hit, multiplying its components.
5. For physical damage, reduce by `armor / (armor + constant * damage)`, capped
   by `maximum_reduction`. Larger hits penetrate this armor formula more readily.
6. Clamp each resistance between the profile bounds, subtract that type's
   penetration, then clamp again. Multiply damage by `1 - effective resistance/100`.
   Thus 100% raw resistance with a 75% cap and 20 penetration becomes 55% effective.
7. Apply partial block reduction, if any. Clamp damage to the profile limit and
   round half up once per component. Typed immunity rejects its component.
8. Deliver through native combat messaging. Each target, chained ones included,
   first receives an attack message, and each later component another, so native
   attack rules, downed and yielded protections and the target's fight reaction
   apply to every target and component. A refused target uses one event and
   takes nothing; a refused component is not delivered. Record world approval and
   actual HP removed. A successful damaging hit may attempt its authored burn,
   within the shared event budget; the attempt uses its event before any roll.

Arithmetic uses DECIMAL128. Random tests use integer percentiles 1–100 with
`roll <= chance`; fractional chances therefore have one-percent granularity.
Zero and 100% outcomes consume no roll. Native proficiency can still consume
dice before profile resolution. Authored mana/movement and **observed net spent**
resources both appear in traces because native cheats, compounding, components
and callbacks can change the net amount.

Profile messages bypass native spell damage scaling/criticals and typed/native
saving throws only for their exact owned event. This prevents double mitigation.
Ordinary room/property vetoes, difficulty rules, defeat and death handling remain
active. Redirecting the source, recipient, tool or message type during approval
refuses delivery; mitigation calculated for one target is never reused for another.
The trace distinguishes the profile result, world-approved amount and HP removed.
It does not claim to explain arbitrary scripts' internal calculations.

### Two conversion models

`final` applies global and destination-type bonuses once. `history` applies
global bonuses once plus each unique original/destination type's bonuses once.
They are inspired by the proposals' PoE2-like and PoE1-like distinction; neither
claims exact replication of either game's full combat system.

For 100 physical converted entirely to fire, with +20% global, +50% physical and
+100% fire increased damage, the models produce **220 final** or **270 history**.
Adding another 100 original fire produces 440 or 490, respectively: original
fire does not inherit the converted packet's physical bonus.

Conversion is simultaneous and happens once. Converted fire does not then
convert to cold in the same pass. Global modifiers and same-type history never
apply twice. Negative destination flat damage offsets incoming conversion
proportionally before scaling; final-type floors/caps apply once after packets
aggregate. `COMPOSE SHOW` uses the selected model for unconditional damage;
criticals, conditions and target defenses remain resolved-event concerns.

## Burns and save/restore

Profile burns save raw authored fire damage, remaining duration, stable caster
ID, profile ID/version/hash, and source fire penetration. They do not snapshot
target defenses: equipment changes and immunity apply on the next tick.
They skip accuracy/block/criticals and use the pinned profile's resistance
rules. Removing supports/equipment or switching the campaign profile does not
rewrite an existing burn. The strongest **raw snapshot damage** wins; equal
damage refreshes. This is not a comparison of post-mitigation damage.
`burn_damage` is its own composition stat, rather than a percentage of the
preceding hit. A critical hit does not automatically increase it.

Missing/changed profile files or invalid defense sources suspend an individual
tick while ordinary duration continues; repairing them can resume later ticks.
Fire/burning immunity or disabling the profile module removes the effect next
tick. Missing/dead sources, a downed target and illegal combat relationships
also terminate it.
Terminated effects do not reappear on enable. Burns created by the legacy
stage-2 path retain that path even if combat profiles are enabled later.

Campaign selection, properties, equipment, composition cooldowns and pending
burn snapshots survive fresh-process saves. Result histories are deliberately
process-local: at most eight entries per participant, 256 participants, bounded
detail per result, with final delivery outcomes retained. Boot/restore clears
them. Reading a trace is inspection of an actual event, not replay simulation.

## Select, pin and update profiles

```text
WORLD COMBAT LIST
WORLD COMBAT CHECK
WORLD COMBAT RELOAD
WORLD COMBAT PIN myworld.combat 1
WORLD COMBAT USE myworld.combat 1
SAVE test-profile
```

`PIN` prints a JSON-escaped string containing ID, version and content hash.
Use that string for a new manifest's `settings.profile.default`, or as the
`combat.profiles.profile` value in `MODULESETTINGS` for future campaigns. The
example manifest already pins `example.final` v1. Omit the version from PIN/USE
to explicitly select the highest installed version. `USE` changes the active
campaign setting; SAVE preserves it. It does not alter world defaults.

`CHECK` reads disk without accepting it. `RELOAD` accepts a library and its
combat-file provenance only if every profile validates. Failed reloads retain
the old library/provenance. `WORLD RELOAD` does not implicitly accept this family.
Missing/changed active pins suspend profile casts before payment. Installing
v2 or restoring a campaign never silently upgrades v1. Keep published profile
files alongside newer versions, including versions referenced by pending burns.
Repair the exact file or explicitly USE the new version. Follow the module
guide's version/migration rules when changing a released manifest's contract.

Native module capsules retain `requires_handler: combat.profiles` even when
disabled or missing definitions, so engines without this handler refuse these
saves rather than silently discarding the new combat semantics.

## Boundaries and verification

This slice supplies direct hits, five damage types, simple burning, numeric
defenses and their traces. Further ailment buildup, damage pools, energy shields,
leech/recharge, arbitrary trigger graphs, gain-as-extra damage, chained conversion
graphs and native-ability adapters remain future work. Existing save tooling
and module migrations remain responsible for broader campaign compatibility.

For engine extensions, `CombatRules.offense` and `resolve` are pure calculations
with an explicit `Roller`; they do not send messages or change HP. An adapter
can use `CombatProfiles.prepare` before native costs, `paid` for the observed
resource delta, and `hit` with a bounded event budget for delivery and recording.
An inactive child returns no profile, allowing the adapter's legacy path.
Preserve the ordinary target legality, proficiency, cooldown and reentrancy
gates demonstrated by `Skill_Compose`. Do not calculate profile mitigation and
then independently post ordinary native damage: that would apply native saves
and scaling again outside the receipt adapter.

Custom Java stat effects can implement the shared
[`CombatStatSource`](../../CoffeeMud/com/planet_ink/coffee_mud/core/interfaces/CombatStatSource.java)
interface and return `CombatRules.bonuses` parsed deltas. Use this interface
rather than concrete ability-class casts: CMClass loads abilities separately
from core code. These extensions are trusted engine code, with the same author
responsibility as other native extensions.

```sh
tools/combat-rules-tests.sh
tools/fixes.sh combat-profiles ability-composition optional-modules
tools/fixes.sh combat
python3 tools/creator_docs.py --check
```

Pure tests cover arithmetic, thresholds, conversion goldens, schemas and pins;
live tests compare real HP and costs, source removal and legacy isolation;
played tests use actual commands and fresh processes with equipment and an
in-progress burn. Source: [pure rules](../../CoffeeMud/com/planet_ink/coffee_mud/core/CombatRules.java),
[runtime and receipts](../../CoffeeMud/com/planet_ink/coffee_mud/core/CombatProfiles.java),
[stat property](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_CombatStats.java).
