# Expeditions: charts, entry contracts and seeded instances

[Handbook](README.md) · [Example pack](examples/expeditions/README.md)

The optional module `expeditions.contracts` turns an item into the way into a
generated dungeon. A **chart** names an expedition and carries a seed and a
tier. **Opening** it is an entry contract: the chart and its cost are consumed
once, and a **run** is recorded with the seed, the modifiers chosen, the
attempts left and the party. The run's seed lays out the generated instance
the party enters, and its modifiers change the foes in it. A death inside uses
an attempt; the run keeps its instance across a save and restore; and there is
a recovery path when an instance or a member goes missing.

## Turn it on

1. Copy [the manifest](examples/expeditions/modules/expeditions.contracts.json)
   into `world/modules/` and your expeditions into `world/expeditions/`.
2. Make the destination: a `StdAutoGenInstance` area with its generator
   ([chapter 12](12-generation.md), [chapter 30](30-travel-vehicles-instances.md#generated-instances)).
   Give it one area ID and fixed variables (a `THEME`), so that the seed alone
   decides the layout.
3. Add `expeditions.contracts` (and `rewards.escrow`, if runs give rewards) to
   `MODULES`, and run `WORLD CHECK ONLY=MODULES,EXPEDITIONS`.

The manifest is native: it allows the `world` scope, and its state has one
string field, `runs`.

## An expedition

```json
{
  "schema": 1, "id": "example.depths", "version": 1, "title": "The Ember Depths",
  "destination": {"area": "Ember Depths", "gated": true},
  "return_room": "Camp#0",
  "modifiers": [
    {"id": "hardy", "title": "Hardy foes", "mob_hp_percent": 50},
    {"id": "veteran", "title": "Veteran foes", "mob_level": 2},
    {"id": "marked", "title": "Burning foes", "mob_effect": {"id": "Prop_CombatStats", "text": "{\"schema\":1,\"penetration\":{\"fire\":10}}"}}
  ],
  "offer": 2, "choose": 1,
  "costs": {"money": 20},
  "attempts": 3,
  "party": {"max": 4},
  "tier": {"min": 1, "max": 3},
  "activity": "example.depths-run",
  "variables": {"THEME": "FIRE"}
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `destination` | required | `area`: the `StdAutoGenInstance` area. `gated` (true): only a group with a run for it may enter. |
| `return_room` | required | Where the party goes when a run fails, and where recovery brings members back. |
| `modifiers` | none | Up to 16. Each changes the foes of the instance: `mob_hp_percent` (-90 to 500), `mob_level` (-20 to 50), or `mob_effect` (a property or ability, with its text). |
| `offer`, `choose` | 2, 1 | How many modifiers a chart offers (drawn from its seed, the same every time it is read) and how many the party chooses when opening it. |
| `costs` | none | `money` taken when the chart is opened. |
| `attempts` | 1 | Deaths inside the instance the run survives. |
| `party` | 4 | The most characters who go: the opener and their party in the room. |
| `tier` | 1 | The chart's tier is kept within `min` and `max`, and given to the generator as `TIER`. |
| `activity` | none | A [reward escrow](36-reward-escrow.md) activity: a failed run ends it for the party with `on_fail`; a completed run ends it as completed, which banks its rewards. |
| `complete` | `encounter` | What completes the run: `encounter`, an [encounter](37-encounter-director.md) completed inside the instance; `cleared`, the last foe inside defeated. |
| `reward` | none | A reward bundle each member is offered in the run's `activity` when the run is completed (the activity is required). |
| `variables` | none | Generator variables set for the instance (upper case; `SEED` is the chart's own). |

## Charts

A chart is an item with `Prop_ExpeditionChart EXPEDITION=example.depths SEED=0 TIER=1`.
A seed of 0 means the chart has not been read: its seed is drawn from the
campaign's `expeditions` random stream (`WORLD MODULES STREAMS`) when it is first
opened, and written into it. A chart can also carry a fixed seed. The modifiers a
chart offers come from its seed. Make charts as rewards, loot or shop goods;
`WORLD EXPEDITIONS CHART example.depths` gives a builder one to test with.

## Opening and entering

`EXPEDITION PREVIEW a chart` (Q03) shows everything opening would do, and
changes nothing: the destination, the seed (for an unread chart, the seed it
would have if opened now, without drawing it), the tier, the modifiers it offers,
the cost and whether it can be paid, the attempts, who would go, and anything
that would refuse it.

`EXPEDITION OPEN a chart marked` opens it with the modifier `marked`. It checks
everything first: the destination exists, no member is already on an
expedition, the cost can be paid, the modifiers chosen are among those offered.
Then, in one step, it draws the seed if need be (recording the scenario, so the
same draw can be made again), takes the cost, makes the run and destroys the
chart. A chart is opened once.

The party then walks into the destination. A gated destination refuses a group
without a run ("Only an opened expedition chart leads there."). The first entry
lays out the instance with the run's seed, variables and tier, and applies its
modifiers to every foe in it. Members who come back later, after a death or a
restore, rejoin the same instance.

## Attempts, failure and recovery

- **Attempts.** A participant's death inside the run's instance uses an attempt.
  When none is left, the run fails: the party is brought to the return room, the
  instance goes, and the run's escrow activity ends with `on_fail`.
- **Completion.** With `"complete": "encounter"`, an [encounter](37-encounter-director.md)
  completed inside the instance completes the run. With `"cleared"`, the run is
  complete when a foe falls and none is left standing inside: a foe is a monster
  that is not in the party, not summoned and not following a participant. Each
  member is told `The expedition is complete.`; the `reward` is offered to each
  in the activity, and the activity ends as completed, banking what it holds. The
  [branching expedition](examples/branching-expedition/README.md) is a worked
  example.
- **Saves.** The run is in the campaign save with its instance's name; the
  generated instance is saved with the world (chapter 31), so a restore finds
  the same instance with the same attempts left.
- **Recovery.** `EXPEDITION RECOVER` brings members who are nowhere back to the
  return room. If the run's instance is gone (an empty instance can expire), the
  run opens again and its next entry lays the instance out again from the same
  seed: the same layout, a new instance. A run with no member left is closed.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `EXPEDITION PREVIEW [chart]` | players | Everything opening the chart would do. No game time. |
| `EXPEDITION OPEN [chart] [modifier]...` | players | Opens the chart, choosing modifiers among those offered. One action. |
| `EXPEDITION STATUS` | players | Your run: status, seed, tier, attempts left, modifiers, instance, party. No game time. |
| `EXPEDITION RECOVER` | players | The recovery path. No game time. |
| `WORLD EXPEDITIONS LIST` / `CHECK` / `RELOAD` | builder, DEV=1 | Definitions; problems (a destination that is not a generated area, a return room or modifier property that is not there); a fresh read. |
| `WORLD EXPEDITIONS RUNS` | builder, DEV=1 | Every run of the campaign. |
| `WORLD EXPEDITIONS CHART [id] ([tier])` | builder, DEV=1 | A test chart, unread. |

## Limits and honesty

The seed decides the layout only if the generator's own choices are fixed: one
area ID and a fixed theme. A generator that picks among several area IDs or
themes picks before the seed applies. Arbitrary scripts in a generator are not
reproduced by the seed. The campaign keeps its last 12 runs, open ones first.

Implementation: [Expeditions.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Expeditions.java),
[Prop_ExpeditionChart](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_ExpeditionChart.java),
the hooks in [StdAutoGenInstance](../../CoffeeMud/com/planet_ink/coffee_mud/Areas/StdAutoGenInstance.java).
Tests: [expedition-integration.py](../../tools/expedition-integration.py) (`tools/fixes.sh expeditions`).
