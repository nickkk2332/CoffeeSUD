# Progression tracks: disciplines, research and mastery

[Handbook](README.md) · [Example pack](examples/progression-tracks/README.md)

The optional module `progression.tracks` adds tracks of progress beside a
character's class and level. A track belongs to one kind of owner: a
**character** (a discipline), an **organization** (a research project), or an
**item** (a weapon's mastery). Each has its own points, earning rules, level
curve, cap, milestones and reset policy, and each owner earns on its own
record.

## Turn it on

1. Copy [the manifest](examples/progression-tracks/modules/progression.tracks.json)
   into `world/modules/` and your tracks into `world/tracks/`.
2. Add `progression.tracks` to `MODULES` and run `WORLD CHECK ONLY=MODULES,TRACKS`.

The manifest is native. It allows the `character`, `organization` and `item`
scopes; its state is one string field, `tracks`.

## A track

```json
{
  "schema": 1, "id": "example.emberwright", "version": 1, "title": "Emberwright discipline",
  "owner": "character", "points": "embers",
  "earn": [{"on": "kill", "amount": 5}, {"on": "cast", "skill": "example.ember", "amount": 2}],
  "curve": {"type": "table", "points": [10, 30, 60]},
  "milestones": [
    {"level": 1, "title": "Kindled", "stats": {"schema": 1, "penetration": {"fire": 5}}, "message": "Your embers burn deeper."},
    {"level": 3, "title": "Emberwright", "unlock": "emberwright"}
  ],
  "reset": "command",
  "replaces": []
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `owner` | required | `character`, `organization` or `item`. |
| `points` | "points" | What its points are called. |
| `earn` | none | Up to 12 rules, below. |
| `curve` | required | `{"type": "table", "points": [10, 30, 60]}` (the total each level needs, rising) or `{"type": "linear", "per_level": 50, "cap": 3}`. The last level is the cap; points stop there. |
| `milestones` | none | Up to 32: a `level`, a `title`, an optional `message`, and what it gives: `stats` (combat profile stats, as `Prop_CombatStats` takes them, for a character or an item) and/or `unlock` (a flag). |
| `reset` | `never` | `command`: the owner may reset the track with `TRACKS RESET`, losing its points and milestones. |
| `replaces` | none | Old track IDs whose records this track takes over (see Migration). |

An earning rule has `on` and an `amount` (default 1):

| `on` | Who earns | When |
| --- | --- | --- |
| `kill` | A character track: the killer. An organization track: each of the killer's organizations. An item track: each item the killer wears or wields that carries the track. | A kill. |
| `cast` | As for kills. With `skill`, only that composed skill counts. | A composed cast resolves. |
| `craft` | As for kills. | A [modifier craft](17-item-modifiers-crafting.md) settles; a staged recipe counts once, when its last stage makes the product. |
| `contribute` | An organization track. | A member gives coin with `TRACKS CONTRIBUTE`, at `money_per_point`. |
| `script` | Any. | A script grants points (below). |

## Owners and isolation

Each owner has its own record: two characters, two organizations and two copies
of a sword each earn alone. An item's track belongs to the item: its notches stay
with the sword when it changes hands, and a copy of the sword starts with none.
An item carries its tracks with `Prop_Tracks TRACKS=example.blade-mastery`, and
earns only while it is worn or wielded.

**One source, once.** A kill, a craft and a script grant with a key give points
once per track and owner: the same kill seen twice, or a save restored and the
same event played again, adds nothing. A track remembers its last 24 keys.

## Milestones

When a track reaches a milestone, its owner (or the item's holder) is told. Its
`stats` count in composed combat through `Prop_Tracks`: on an item, the one that
names its tracks; on a character, one added the first time a milestone gives
stats. An `unlock` is a flag content can test: `ProgressionTracks.unlockedFor(mob, "guild-forge")`
is true when the character or one of their organizations has it. A reset takes
the milestones away with the points.

## Migration (Q06)

To rename a track, install the new one with `"replaces": ["old.id"]` and remove
the old file. The first time a record of the old ID is read, it moves to the new
track, its points kept (to the new cap) and its keys with it. This happens once,
the same way every time, also for a save made before the change. Two tracks may
not replace the same ID (CHECK says which takes it), and a replaced track must
not still be installed.

## Commands and scripts

| Command | Who | What it does |
| --- | --- | --- |
| `TRACKS` | players | Your tracks, your organizations' and your equipment's, as a tree: each track's level, points and what the next level needs, and each milestone marked `[x]` reached or `[ ]` not. No game time. |
| `TRACKS CONTRIBUTE track coin` | players | Gives coin to your organization's track, whole points only. |
| `TRACKS RESET track` | players | Resets a track whose `reset` is `command`. |
| `WORLD TRACKS LIST` / `CHECK` / `RELOAD` | builder, DEV=1 | Definitions; problems (a skill that is not installed, a replaced track still installed, a curve that does not rise); a fresh read. |
| `WORLD TRACKS GRANT track n [key]` | builder, DEV=1 | Points to yourself, your organization or your wielded item. |

```javascript
var T = Packages.com.planet_ink.coffee_mud.core.ProgressionTracks;
T.earn(T.of(mob()), "example.emberwright", 3, "quest:lantern");   // points, once for this key
var p = T.progress(T.of(mob()), "example.emberwright");             // [points, level]
T.unlockedFor(mob(), "guild-forge");
```

Implementation: [ProgressionTracks.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/ProgressionTracks.java),
[Prop_Tracks](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Tracks.java).
Tests: [p01-tracks.js](../../tools/fixtures/fixes/p01-tracks.js).
