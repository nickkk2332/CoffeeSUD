# The encounter director: phases, waves and budgets

[Handbook](README.md) · [Example pack](examples/encounter-director/README.md)

The optional module `encounters.director` runs an encounter in a room as one
saved state machine: a boss with phases, or waves to hold off, built from the
same definition schema. It spawns each phase's foes once per run, offers rewards
through the [reward escrow](36-reward-escrow.md) once per run, and declares
what a wipe and a retreat do. Each encounter has budgets for its actors,
director actions and rewards.

## Turn it on

1. Copy [the manifest](examples/encounter-director/modules/encounters.director.json)
   into `world/modules/` and your encounters into `world/directors/`. Install
   `rewards.escrow` too; the example manifest requires it.
2. Add `encounters.director` to `MODULES` and run
   `WORLD CHECK ONLY=MODULES,DIRECTORS`.
3. Put `EncounterDirector ENCOUNTER=example.warden` on the room.

The manifest is native and allows the `world` scope; the director keeps its
state in the room's saved behavior state.

## An encounter

```json
{
  "schema": 1, "id": "example.warden", "version": 1, "title": "The Cinder Warden",
  "kind": "boss", "activity": "example.trial", "reward": "example.room-three",
  "scaling": {"rule": "party_size", "per_member": 25, "max_members": 4},
  "budget": {"actors": 4, "actions": 2, "loot": 1},
  "on_wipe": "reset", "on_retreat": "reset", "retreat_ticks": 5,
  "phases": [
    {"id": "wakes", "enter": {"on": "start"}, "warning": "The Cinder Warden raises its hammer.",
     "spawn": [{"mob": "the Cinder Warden", "role": "boss"}]},
    {"id": "imps", "enter": {"on": "hp_below", "role": "boss", "percent": 66},
     "warning": "Imps pour out of the furnace!", "spawn": [{"mob": "a cinder imp", "count": 2, "role": "imp"}],
     "say": ["The Warden roars: Burn them!"]}
  ],
  "complete": {"when": "defeated", "role": "boss"}
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `kind` | `boss` | `boss`, `waves` or `event`: a label; the phases decide what happens. |
| `start` | `enter` | `enter`: the run begins when a player is in the room. `script`: only a script or builder starts it. |
| `phases` | required | One to eight phases, below. |
| `complete` | required | `{"when": "defeated", "role": "boss"}` (every actor of a role defeated), `{"when": "cleared"}` (every foe of the last phase), or `{"when": "survive", "ticks": 30}`. |
| `bind`, `protect` | none | `"bind": ["keeper=the old keeper"]` gives a role to mobs already in the room; `"protect": "keeper"` fails the encounter if one of them is defeated. |
| `scaling` | none | `{"rule": "party_size", "per_member": 25, "max_members": 4}`: spawned foes get 25% more hit points for each participant past the first, up to four. The scale is chosen once, when the run begins. |
| `budget` | required | `actors` (most spawned foes alive at once, 1 to 50), `actions` (director lines, 0 to 100), `loot` (rewards per run, 0 to 20). |
| `on_wipe` | `reset` | Every participant dead or down: `reset` or `fail`. |
| `on_retreat` | `reset` | No participant in the room for `retreat_ticks` (10): `reset`, `pause` (wait for them) or `fail`. |
| `activity`, `reward`, `bank` | none | Rewards are offered in this escrow activity; `reward` on completion; `bank: true` banks the run's rewards when it completes. |

A phase:

| Field | Meaning |
| --- | --- |
| `id`, `title` | Its name. |
| `enter` | `{"on": "start"}` (the first phase only); `{"on": "hp_below", "role": "boss", "percent": 66}`; `{"on": "cleared"}` (the previous phase's foes all defeated or removed); `{"on": "after", "ticks": 20}`. |
| `warning` | Told to the room as plain text, "Warning: ...", when the phase begins, and shown again by `OBJECTIVE`. |
| `spawn` | Up to ten catalog mobs, each with a `count` and a `role`. |
| `say` | Up to four lines the director tells the room; each is one director action. |
| `reward` | A bundle offered when the phase ends. |

## How a run goes

- **Start.** The participants are the players in the room. The scale is chosen,
  mobs in `bind` take their roles, and the first phase begins.
- **Each tick.** Players who come in join. An actor that is gone without a
  death (destroyed, moved away) is **removed**, never counted as defeated. A wipe,
  a retreat or a fallen protected role ends the run as declared. Then the
  completion is checked, then the next phase's `enter`. At most one phase begins
  in a tick.
- **Once per run.** A phase spawns only the first time it begins in a run, even
  after a restore. A reward is offered once per run and key, to each participant
  in the room, within the loot budget.
- **Budgets.** An encounter whose phases could never fit its budgets is refused
  when it is read: a phase spawning more than `actors`, more `say` lines than
  `actions`, or more rewards than `loot`. In play, a spawn that would pass the
  actor budget is not made, and the director's notes say so ("actor budget 4
  reached: 1 of 2 a cinder imp not spawned").
- **Reset and fail.** A reset sends away the foes the run spawned and waits for a
  new run. A failure ends the activity's run for the participants with
  `on_fail` (chapter 36).

## Saves

The director's state is the room's behavior state in the save: the pinned
definition, stage, run, phase, ticks, scale, participants, every actor (by
instance ID, with role, phase and state), the phases spawned and the rewards
given. Its spawned foes are ordinary world mobs, saved in the room. A restored
director rebinds them by instance ID and goes on.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `OBJECTIVE` | players | The encounter here: its phase, warning, objective and each foe with its health, in plain text. No game time. |
| `WORLD DIRECTORS LIST` / `CHECK` / `RELOAD` | builder, DEV=1 | Definitions, running directors; problems (a catalog mob, reward or activity that is not there, a role no earlier phase spawns); a fresh read. |
| `WORLD DIRECTORS SHOW` | builder, DEV=1 | The encounter here with its run, scale, budgets used and notes. |
| `WORLD MODULES REPORT` | builder, DEV=1 | Per encounter: runs, phases, actors spawned, removed and defeated, wipes, retreats, resets, completions, and the highest number of actors at once. |

Implementation: [EncounterDirectors.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/EncounterDirectors.java),
[EncounterDirector.java](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/EncounterDirector.java).
Tests: [g03-director.js](../../tools/fixtures/fixes/g03-director.js).
