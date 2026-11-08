# Example pack: progression tracks

[Chapter 39](../../39-progression-tracks.md)

The module manifest and three tracks, one for each kind of owner:

- `example.emberwright`, a character's discipline: 5 embers a kill and 2 a cast
  of `example.ember`; level 1 gives 5 fire penetration in composed combat, level
  3 unlocks `emberwright`. Its owner may reset it.
- `example.foundry`, an organization's research project: members give coin
  (`TRACKS CONTRIBUTE example.foundry 100`, 10 coin a point) and each member's
  kill adds a point; at 150 points the guild forge is lit (`guild-forge`).
- `example.blade-mastery`, an item's mastery: a weapon with
  `Prop_Tracks TRACKS=example.blade-mastery` earns a notch for each kill made
  while it is wielded; at 3 it gives 10 accuracy, at 10 a 5% critical chance.

Install into a world:

```text
modules/progression.tracks.json -> world/modules/
tracks/*.json                   -> world/tracks/
```

Add `progression.tracks` to `MODULES`. `TRACKS` shows a character's tracks, their
organization's and their equipment's as a tree of milestones.
