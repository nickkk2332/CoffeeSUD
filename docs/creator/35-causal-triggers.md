# Causal triggers: reactions to blocks and kills

[Handbook](README.md) · [Example pack](examples/causal-triggers/README.md)

The optional module `abilities.triggers` lets equipment and effects react to
events: when a character blocks a composed hit, or kills. A reaction is one
action: a composed skill, a gain of a [named resource](34-named-resources.md),
healing, or a summoned helper. Reactions are bounded: every reaction belongs to
the causal root of the event that began it, and a root has one action budget
and a depth limit, so effects that trigger each other always stop.

## Turn it on

1. Copy [the manifest](examples/causal-triggers/modules/abilities.triggers.json)
   into `world/modules/` and your trigger definitions into `world/triggers/`.
2. Add `abilities.triggers` to `MODULES`. Select the modules your triggers use:
   `abilities.composition` for composed skills, `combat.profiles` for blocks,
   `abilities.resources` for costs and gains.
3. Run `WORLD CHECK ONLY=MODULES,TRIGGERS`, then start a new game, or adopt the
   module into a campaign with `WORLD MODULES ADOPT abilities.triggers`.

The manifest is native. It allows the `character` scope; its state has the
string fields `cooldowns` and `recent` (4096 characters, empty to start); its
settings declare `action_budget` (an integer within 1 to 64) and `max_depth`
(within 1 to 8). The settings are pinned to the campaign like any module
setting: change them for a campaign with
`WORLD MODULES CONFIGURE abilities.triggers max_depth 2`.

## Granting triggers

`Prop_Triggers` grants triggers while it is on a character, or on an item they
wear or hold:

```text
Prop_Triggers TRIGGERS=example.riposte,example.harvest
```

A character or item carries one `Prop_Triggers`; list all its triggers in it.
`TRIGGERS` shows a player their reactions and cooldowns.

## Trigger definitions

Each is `world/triggers/<id>.v<version>.json`:

```json
{
  "schema": 1,
  "id": "example.riposte",
  "version": 1,
  "title": "Riposte",
  "on": "block",
  "priority": 60,
  "cooldown": {"amount": 3, "clock": "ticks"},
  "action": {"type": "cast", "skill": "example.ember", "target": "other"}
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `on` | required | `block`: the owner blocked a composed hit (the profile's block or spell block, [chapter 16](16-combat-profiles.md)). `kill`: the owner killed. |
| `priority` | 50 | 0 to 100. A character's triggers for one event run highest first, then by ID. |
| `chance` | 100 | Percent. Drawn from the campaign's `triggers` random stream, only once everything else allows the reaction. |
| `cooldown` | none | `amount` and `clock` (`ticks`, `turns` or `hours`, as for [resources](34-named-resources.md)). |
| `cost` | none | Named resources the reaction costs, all or nothing: `{"example.focus": 10}`. |
| `include_summons` | false | For `kill`: a kill by a creature the owner summoned counts too. |
| `action` | required | One of the actions below. |

The actions:

| `type` | Fields | What happens |
| --- | --- | --- |
| `cast` | `skill`, `target` | The owner uses a composed skill they have learned, paying its costs. `target` is `other` (for a block, the attacker), `enemy` (the owner's current opponent) or `self`. A kill's `other` is the dead, so CHECK refuses it. The reaction is not rolled against the skill's proficiency, so it cannot fizzle. |
| `gain` | `pool`, `amount` | The owner gains a named resource. |
| `set` | `pool`, `amount` (0 or more) | The owner's pool is set to the amount, within its bounds (a combo back to 0, a charge filled). |
| `drain` | `pool`, `amount` | The owner loses up to the amount, as much as is not reserved; a character without the pool loses nothing. |
| `heal` | `amount` | The owner recovers hit points. |
| `summon` | `mob`, `ticks` | A copy of a catalog mob appears and follows the owner for that many ticks. It belongs to the owner: its `Prop_Summoned` names them, it leaves if they do, and it is not saved. |

## How reactions run

- **After the cause.** A block's reactions run when the composed cast that was
  blocked has finished. A kill's reactions run on the game thread right after
  the death has been handled. A reaction never runs in the middle of another
  action.
- **One root.** An event that is not itself caused by a reaction begins a new
  causal root. Events caused while a reaction runs (its skill is blocked, or
  kills) belong to the same root, one level deeper.
- **Bounds.** In one root, each reaction takes one action from the shared
  budget (`action_budget`); a reaction deeper than `max_depth` is refused; and a
  trigger runs at most once per character. So two characters whose ripostes
  trigger each other each riposte once, and then it stops.
- **Order of checks.** A reaction runs only if its owner is still there, it is
  within the depth and budget, it has not run in this root, its event was not
  already committed, it is not cooling down, and its cost can be paid. Then the
  chance is drawn. Then the cost is paid, the cooldown starts, and the event is
  recorded, all before the action happens.
- **Summons.** A kill by a summoned creature fires its own triggers, and its
  owner's `include_summons` triggers, as the owner's kill. No one else's.

## Saves and replays

Each character's cooldowns, and the keys of the last 32 events their triggers
committed (such as "the kill of this creature"), are kept in the save. A
restored campaign cannot run a committed trigger again for the same event, and
a cooldown in progress continues on its clock. Reactions waiting to run are not
saved: a save is made between actions, when none wait.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `TRIGGERS` | players | Your reactions, chances, costs and cooldowns. No game time. |
| `WORLD TRIGGERS LIST` / `CHECK` / `RELOAD` | builder, DEV=1 | The definitions, their problems (also a skill, pool or catalog mob that is not there), a fresh read. |
| `WORLD TRIGGERS SHOW [character]` | builder, DEV=1 | A character's triggers and cooldowns. |
| `WORLD TRIGGERS LAST` | builder, DEV=1 | The last causal root: each reaction, its depth and its outcome ("refused: the root's action budget is spent"). |
| `WORLD MODULES REPORT` | builder, DEV=1 | Counts per trigger: roots, actions, refusals by reason, duplicate events suppressed. |

## What this module does not do

Native attacks and spells have no block event; only a composed hit that the
combat profile blocks does. Reactions to other events (a critical, being hit, a
craft) are not part of this module yet.

Implementation: [AbilityTriggers.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/AbilityTriggers.java).
Tests: [a03-triggers.js](../../tools/fixtures/fixes/a03-triggers.js).
