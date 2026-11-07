# Example pack: encounter director

[Chapter 37](../../37-encounter-director.md)

The module manifest and two encounters built from the same schema:

- `example.warden`: a three-phase boss. The Warden wakes; below 66% health it
  calls two imps; below 33% its armor cracks. Defeating it offers the third
  room's spoils through the [reward escrow](../reward-escrow/README.md) trial.
  The Warden's health grows by 25% for each player past the first (up to four),
  chosen once when the fight begins.
- `example.siege`: a defense in three waves. The old keeper in the room must
  live; each wave comes when the last is cleared; holding the gate banks the
  second room's spoils.

The encounters spawn catalog mobs: catalog `the Cinder Warden`, `a cinder imp`,
`a raider` and `a raider captain` before using them. The siege binds a mob that
is already in its room, `the old keeper`.

Install into a world (with the reward escrow pack, which this module requires):

```text
modules/encounters.director.json -> world/modules/
directors/*.json                 -> world/directors/
```

Add `encounters.director` and `rewards.escrow` to `MODULES`, then put the
director on the boss's room:

```text
EncounterDirector ENCOUNTER=example.warden
```

The encounter begins when a player comes in. `OBJECTIVE` shows its phase,
warning, objective and foes; `WORLD DIRECTORS SHOW` adds its run, budgets and
notes for a builder.
