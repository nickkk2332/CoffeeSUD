# Optional ability composition

Stage 2 implements the first A01/A02 slice from the ARPG/MUD proposals: authored
direct-hit attacks/spells, tags, supports, deterministic modifiers, bounded
chaining and burning, and a compiled skill sheet. It builds on [optional
modules](14-optional-modules.md). It is off by default in every existing world.

## Install the playable example

Use a [separate deployment](examples/README.md). Copy the example directories
into that world's directory; merge them if these directories already exist:

```sh
cp -R docs/creator/examples/ability-composition/modules /your/deployment/world/
cp -R docs/creator/examples/ability-composition/compositions /your/deployment/world/
```

Merge into its `world.ini`, preserving existing selections and settings:

```ini
MODULES=abilities.composition
MODULESETTINGS={"abilities.composition":{"max_supports":3,"max_chains":4,"event_budget":32}}
```

New campaigns adopt the module. For existing campaigns, a creator must use
`WORLD MODULES ADOPT abilities.composition`. Validate with:

```text
WORLD MODULES CHECK
WORLD COMPOSITIONS CHECK
WORLD CHECK ONLY=COMPOSITIONS
```

Grant `Skill_Compose` through your class/race/ability grants, teaching, or
creator tools. A temporary DEV=1 JRUN script can grant it for an experiment:

```javascript
var a = Packages.com.planet_ink.coffee_mud.core.CMClass.getAbility("Skill_Compose");
a.setProficiency(100);
mob().addAbility(a);
```

Installing the module grants no abilities. `COMPOSE LEARN` chooses an available
authored skill; it does not teach the adapter or its prerequisites. Once taught:

```text
COMPOSE LIST
COMPOSE LEARN example.ember
COMPOSE LINK example.ember example.chain
COMPOSE LINK example.ember example.burning
COMPOSE LINK example.ember example.power
COMPOSE SHOW example.ember
COMPOSE CAST example.ember goblin
COMPOSE UNLINK example.ember example.power
COMPOSE FORGET example.ember
```

Player commands accept IDs in either case; authored IDs stay lowercase.
Configuration commands are free and usable during combat in this first
adapter. Changing supports or selecting a skill again retains its cooldown.

Ember bolt starts at 12 fire damage, 6 mana, one action, two-tick cooldown.
With all three supports: 14.4 damage (rounded to 14 at the hit boundary),
15 mana, 1.25 actions, two additional targets, and a 75% attempt to apply
three ticks of 3 fire damage. Proficiency, attack rolls for attacks, saves,
immunities, engine damage adjustments, criticals and death handling still apply.
Compiled values describe the composition layer, rather than guaranteed HP loss.

## Author skills and supports

Flat UTF-8 files in `world/compositions/` use `id.vVERSION.json` filenames:

```json
{
  "schema": 1, "kind": "skill", "id": "myworld.spark", "version": 1,
  "name": "Spark", "tags": ["spell", "projectile", "electric", "stormcraft"],
  "requires_ability": "Spell_Lightning", "mask": "-LEVEL +>=5",
  "damage": {"electric": 16},
  "stats": {"mana": 8, "movement": 2, "actions": 1, "cooldown": 3, "range": 5}
}
```

Exactly one of `spell` or `attack` is required. Other tags are arbitrary
lowercase IDs. Tags control compatibility and filters; a `movement` tag does
not move an actor, nor does a `fire` tag change damage. Damage types are
`physical`, `fire`, `cold`, `electric`, `acid`. Physical uses bashing damage and
a justice message; elements use matching engine types. Mixed pools resolve as
separate typed components. Attacks use the engine hit roll per target; spells
use typed saves without an attack roll. Existing abilities are unchanged.

Optional `requires_ability` gates access on an additional learned ability; it
does not invoke that ability. `mask` uses ordinary character masks against
base values. Gates run at selection, linking, inspection and every cast.
Losing prerequisites suspends choices without deleting them. The learned
adapter's proficiency governs success; prerequisite proficiency does not replace
it. Your normal class/ability tools control progression and teaching.

```json
{
  "schema": 1, "kind": "support", "id": "myworld.overcharge", "version": 1,
  "name": "Overcharge", "group": "overcharge",
  "requires_all": ["projectile"], "requires_any": ["spell", "attack"],
  "excludes_tags": ["melee"], "adds_tags": ["overcharged"],
  "modifiers": [
    {"stat":"damage", "op":"increased", "value":20},
    {"stat":"mana", "op":"more", "value":30},
    {"stat":"damage.electric", "op":"more", "value":50,
      "when_tags":["stormcraft"], "condition":"target_wounded"}
  ]
}
```

Supports may require abilities/masks too. Compatibility uses **base skill**
tags: all `requires_all`, at least one nonempty `requires_any`, and no
`excludes_tags`. Added tags cannot make another incompatible support legal.
`adds_tags` feeds modifier filters after compatibility succeeds; reserved
`attack`/`spell` cannot be added. A group appears once; its default is the
support ID. Shared groups make distinct supports mutually exclusive.

There are at most three supports and four chosen skills per character, plus
4096 characters each for loadout and cooldown JSON. Long IDs can hit that
budget first. Failed edits are atomic. Lower campaign support limits suspend
over-limit bindings until enough supports are removed. `UNLINK` and `FORGET`
work even if a definition or prerequisite is missing.

| Stat | Default | Hard range | Runtime use |
| --- | --- | --- | --- |
| `mana`, `movement` | 0 | 0–10000 | Round up; once per cast |
| `actions` | 1 | 0.25–10 | Scheduler action cost |
| `cooldown` | 0 | 0–1000 | Round up; game-clock ticks per base skill ID |
| `range` | 5 | 0–10 | Round down; initial combat range within current room |
| `chains` | 0 | 0–8 | Round down; additional unique room targets |
| `burn_damage` | 0 | 0–10000 | Round nearest; fire damage per tick |
| `burn_ticks` | 0 | 0–20 | Round down; number of damage ticks |
| `burn_chance` | 100 | 0–100 | Percent roll then fire save |
| `damage.TYPE` | 0 | 0–100000 | Round nearest per hit component |

Stats are optional. Burn application requires an actual direct hit removing
HP, positive rounded burn damage, and at least one duration tick. All direct
targets must be living, visible, legal combat targets outside the caster's
group. No self-targeting, remote rooms or implicit friendly fire.

## Modifier algebra

Both skills and supports accept `modifiers`, with `stat`, `op`, numeric `value`,
optional `when_tags` and `condition`. The deterministic order is:

1. Base values plus all `flat` modifiers.
2. One simultaneous damage conversion pass using those pools.
3. Sum `increased` percentages; multiply by `1 + sum/100`.
4. Multiply each `more` separately by `1 + value/100`.
5. Greatest `floor`, smallest `cap`, hard safety limits.
6. Round at the runtime boundary as shown above.

The pipeline uses 34-significant-digit decimal arithmetic so percentages do
not add an accidental resource/timing unit or change an integer chance threshold.

Global `damage` bonuses combine with destination-channel bonuses. Flat damage
must name a type, such as `damage.fire`. Global damage floor/cap applies to
each component, not their sum. Increased/more may be negative down to -100%;
flat may be negative too. Negative pools and increased factors clamp to zero.
Conflicting floors/caps are rejected before selection or spending resources.

```json
{"stat":"damage", "op":"convert", "from":"physical", "to":"fire", "value":50}
```

Conversion totals at most 100% per source, subtracts from that source, and
receives destination bonuses. Newly converted fire does not convert again to
cold in the same pass. This is CoffeeSUD's specified algebra, not an exact
recreation of either Path of Exile game's conversion rules.

Conditions: `always` (default), `target_wounded` (below half maximum HP),
`target_burning` (has `CompositionBurn`). They are sampled once per hit, before
its components, and may affect only damage/burn damage. Target-dependent costs,
timing and target counts are refused. `when_tags` requires all listed compiled
tags. `COMPOSE SHOW` explains base/unconditional values, source ID/version,
each modifier/filter/condition, campaign budgets and cooldown. Inspection
spends nothing and writes no state. Conditional and final engine adjustments
are explicitly separate from unconditional results.

## Runtime and persistence

The full loadout validates before normal costs/components. Costs are paid
once, then proficiency is tested. A fizzle consumes the cast/cooldown. A later
room veto follows ordinary paid-attempt behavior. Invalid choices, illegal
targets, cooldowns and inactive modules fail before payment. Scripted/auto
invocations cannot bypass these gates.

Chains snapshot eligible inhabitants in room order after the primary target,
up to the lesser of authored and campaign hop limits. Every actor appears
once. Positive typed damage attempts and burn attempts each use one event;
misses use one. Exhausting `event_budget` stops remaining events. Reentrant
composed casts are refused. Burning cannot chain or apply supports. Arbitrary
author scripts retain their normal Java access outside this event graph.

One burn exists per target: stronger damage replaces weaker, equal refreshes,
weaker leaves it unchanged. The native timed effect saves snapshot damage,
caster instance ID and remaining ticks. Attribution uses that stable ID after
restore, rather than matching display names. Missing/dead sources or illegal
combat relationships terminate it safely. Removing a support affects future
casts; an existing burn keeps its snapshot. Module disable prevents further
damage and removes burns next tick; terminated effects do not return on enable.

Cooldowns use the campaign game clock in both clock modes, separately from
loadouts. Forgetting, unlinking, upgrading or switching cannot clear them.
Suspension retains loadouts/cooldowns without freezing global time. Character
scope and stable instance ownership keep party members' data independent.
Capsules follow stage 1's module-aware save/migration rules.

The supplied manifest uses the registered `abilities.composition` handler,
whose character-state contract is validated before activation. Native capsules
include `requires_handler`; this marker survives suspension and missing
definitions. Earlier module-aware engines reject these saves, including when
the manifest was removed, rather than silently dropping the adapter or burns.

## Versioning and creator tools

`LEARN` explicitly pins the highest installed skill version and clears supports.
`LINK` pins the highest installed support version. Pins store ID, version and
canonical SHA-256 content hash. Keep released files beside newer versions:
`myworld.spark.v1.json` and `myworld.spark.v2.json`.

`WORLD COMPOSITIONS RELOAD` accepts the new library and provenance only after
all files validate. Failed reloads keep the previously accepted library.
`CHECK` reads disk without replacing it; `WORLD RELOAD` does not silently
accept composition changes. Validation runs even while the module is disabled.
Limits: 256 files, 64 KiB/file, 32 modifiers/definition, 32 entries/tag list.
Unknown/duplicate keys, mismatched filenames, symlinks, unsupported types,
conditions/operations and missing prerequisite ability IDs fail validation.

Existing bindings retain pins across reload, restore and newer installations.
Missing versions and edits to pinned versions visibly suspend those bindings;
choices are not silently upgraded, reinterpreted or deleted. Restore the old
file, remove the affected support, or explicitly select the skill again.
Upgrade supports by unlinking then linking. Even a title edit changes the hash:
release a new version when changing published content. SAVE tracks/packages
these hand-authored files but never generates or deletes them.

## Extension boundaries and verification

This release provides a reusable compiler and one direct-hit adapter. Future
work includes gem-item sockets, support loot, passive trees, arbitrary trigger
graphs, healing/movement/summoning operators, persistent projectiles, AI support
selection and adapters for other abilities. Tags alone do not implement those.
Existing GenAbility casts do not automatically become composed skills.

Sources: [runtime](../../CoffeeMud/com/planet_ink/coffee_mud/core/AbilityComposition.java),
[schema/compiler](../../CoffeeMud/com/planet_ink/coffee_mud/core/CompositionDefinitions.java),
[combat adapter](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Skills/Skill_Compose.java).
Another opt-in family can consume `CompositionDefinitions.Compiled` directly.

Run from the repository root, with a JDK and graphical test prerequisites:

```sh
tools/composition-tests.sh
tools/fixes.sh ability-composition optional-modules
tools/fixes.sh composition
python3 tools/creator_docs.py --check
```

Pure tests cover algebra, compatibility, removal, pins and malformed content.
Live tests cover native costs, targeting, cooldowns, character isolation,
effect expiry, suspension and updates. Played tests verify actual commands
and fresh-process save/restore with a burn still in progress.
