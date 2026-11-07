# Named resources: heat, focus and charges

[Handbook](README.md) · [Example pack](examples/named-resources/README.md)

The optional module `abilities.resources` gives characters named resources
besides hit points, mana and movement: a pool such as heat or focus, or a
count of charges. A resource can change by itself over time on a clock you
choose. Composed skills and supports ([chapter 15](15-ability-composition.md))
can cost and gain resources. Nothing changes for a world that does not
select the module.

## Turn it on

1. Copy [the manifest](examples/named-resources/modules/abilities.resources.json)
   into `world/modules/` and your pool definitions into `world/pools/`.
2. Add `abilities.resources` to `MODULES` in `world/world.ini`. It needs no other
   module. To use resources in composed skills, select `abilities.composition`
   too.
3. Run `WORLD CHECK ONLY=MODULES,POOLS` in builder mode, then start a new game.
   An existing campaign takes the module only with
   `WORLD MODULES ADOPT abilities.resources`.

The manifest is native: its ID and handler are `abilities.resources`, it allows
the `character` scope, and its state has one field, `pools`, a string of at most
4096 characters that starts empty. `WORLD MODULES CHECK` refuses a manifest
without these.

## Pool definitions

Each definition is `world/pools/<id>.v<version>.json`:

```json
{
  "schema": 1,
  "id": "example.heat",
  "version": 1,
  "title": "Heat",
  "kind": "pool",
  "max": 100,
  "start": 0,
  "change": {"amount": -5, "every": 1, "clock": "ticks"},
  "reservable": false
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `schema`, `id`, `version` | required | As for every module definition. The file name must be `<id>.v<version>.json`. |
| `title` | the ID | The name players see. A title change alone is not a rule change. |
| `kind` | `pool` | `pool` (up to 1,000,000) or `charges` (whole charges, up to 10). |
| `max` | required | The maximum, at least 1. |
| `start` | 0 | The value a character's pool starts at, from 0 to `max`. |
| `change` | none | A change over time: `amount` (negative drains, positive refills, not 0), `every` (how many clock units per change, default 1) and `clock`. |
| `reservable` | false | Whether part of the pool can be reserved, so that it cannot be spent. |
| `reset` | none | What sets the pool back to `start` at once: `"miss"` (a composed cast of the character's that hurt no one) and `"combat_end"` (the character's fight ends: they no longer have anyone to fight). |

The `clock` is one of:

- `ticks`: the game clock's ticks. They stop while the game is paused, and
  advance as turns are taken in turn-based play.
- `turns`: turns in turn-based play. In real-time play no turns pass, so a pool
  on this clock does not change there. WORLD CHECK warns about a `turns` pool
  when the module's manifest allows `REALTIME`.
- `hours`: hours of the game calendar.

A problem names its file and JSON path, for example
`pools/example.heat.v1.json: /change/clock: must be one of turns, ticks, hours`.

## How values behave

- **Settled from the clock.** A change is worked out when the pool is next read
  or used, from the clock readings since it was last settled. Reading writes
  nothing, so a save and restore give the same value as playing on.
- **Bounds.** A value stays from 0 to the maximum. A gain beyond the maximum is
  lost.
- **Costs.** When a skill or script spends several resources, every cost is
  checked first. If one cannot be paid, nothing is spent, and the reason names
  the pool and the shortfall.
- **Order in a composed cast.** The costs are checked before the cast and paid
  with its mana and movement. Its gains come after the cast resolves, the
  skill's first, then its supports'. A cast the world refuses, or that fails to
  start, spends none of them; a cast that fizzles pays as it pays mana, and gains
  nothing.
- **Maximums.** `Prop_ResourceMax` raises or lowers a maximum while it is on the
  character or an item they wear or hold (`example.heat=+20`). When a maximum
  falls, a value above it is cut to it the next time the pool is read or
  changed. A higher maximum adds nothing by itself.
- **Reservations.** A reservable pool can have part reserved (for an aura's
  upkeep, say). What is reserved cannot be spent until it is released. See
  [Reservations](#reservations) for reservations that come and go with an
  effect or a minion.

## Versions

A character's pool records the version it uses. When a newer version is
installed, the character's pool moves to it the next time it is used, and its
value is cut to the new maximum if that is lower. A version edited in place
(any change but its title, without a new version) is refused: the pool cannot
be read or used until the file is put back or given a new version. Keep old
versions installed while saves use them, or let the newest replace them.

## Composed skills

A composition definition can declare costs and gains:

```json
"resources": {"cost": {"example.focus": 20}, "gain": {"example.heat": 15, "example.cinder": 1}}
```

Each has at most eight pools, each amount from 1 to 1,000,000. A skill's and its
supports' amounts add up. `"gain_on": "hit"` in the skill's `resources` gives
its gains (and its supports') only when the cast lands, that is, hurts or kills
at least one of its targets; the default, `"cast"`, gains on any cast that
resolves. `COMPOSE SHOW` lists them. A composition that costs
resources cannot be cast while `abilities.resources` is off; gains are skipped
while it is off. WORLD CHECK ONLY=POOLS reports a composition that names a pool
that is not installed.

## Reservations

`Prop_Reserve` holds part of a reservable pool for as long as it is on its
host (fixture `a14-reserve.js`):

| Parameter | Default | Meaning |
| --- | --- | --- |
| `POOL=` | required | The pool; it must be `reservable`. |
| `AMOUNT=` | 0 | How much it holds. |
| `NAME="..."` | the minion's name, or "a reservation" | What `RESOURCES` calls it. |
| `OF=SELF` or `OF=LEADER` | `SELF` | `SELF`: from its host's own pool (an aura, a stance). `LEADER`: from the pool of the one its host follows (a minion's upkeep). |
| `EFFECTS="..."` | none | What it gives while it fits, in `Prop_SpellAdder` syntax (`Prop_Adjuster;STR+2`). These work through the reservation itself: stat, state, resistance and message properties. |
| `ONSUSPEND=PAUSE` or `LEAVE` | `PAUSE` | When it no longer fits: `PAUSE` stops its effects until there is room again; `LEAVE` dismisses a minion (it fades away) or takes the property off a character. |

```text
POOL=example.spirit AMOUNT=30 NAME="Aura of embers" EFFECTS="Prop_Adjuster;STR+2"
POOL=example.spirit AMOUNT=50 OF=LEADER ONSUSPEND=LEAVE
```

The rules:

- **Made and released with its holder.** Nothing is stored in the pool: the
  reservation *is* the property, read each time the pool is. Put it on (a
  spell's script, `MPAFFECT`, a summoning) and the share is held; take it off,
  or let the minion die or be dismissed, and the share is free at once. It is
  saved with its host, so a restore brings both back together.
- **What is held cannot be spent.** Costs see only what is left.
- **Order and suspension.** The reservations are fitted in the order they
  were made (the character's and their minions' together; the place in line is
  kept in the property's text as `SINCE=`, so a save keeps it). Each holds while
  the pool's value has room for it; one that does not fit (because the pool was
  drained, its maximum lowered, or it is too big) is suspended, and holds
  nothing. A newer reservation never pushes out an older one; when the pool
  falls, the newest go first. The character is told when one is suspended and when it
  holds again; it is checked each tick of its host.
- **Shown.** `RESOURCES`: `Spirit: 100 of 100, reserved: Aura of embers 30, a
  spirit wolf 50, suspended (not enough): Stone ward 40`. `IDENTIFY`-style
  text on the property says what it holds and whether it is suspended.
- With the module off, nothing fits and every reservation's effects stop.

## Combos

A combo is a pool that builds with hits and is lost when the chain breaks.
Put the pieces together (fixture `a20-combos.js`):

```json
{
  "schema": 1, "id": "example.combo", "version": 1, "title": "Combo",
  "max": 5, "start": 0,
  "change": {"amount": -1, "every": 3, "clock": "turns"},
  "reset": ["miss", "combat_end"]
}
```

and in the composed skill: `"resources": {"gain": {"example.combo": 1}, "gain_on": "hit"}`.

| Rule | How |
| --- | --- |
| Each landed hit adds a step | `gain` with `gain_on: hit` |
| A miss breaks the chain | `reset: ["miss"]`: a cast that hurt no one, whatever its own `gain_on` |
| The chain is lost when the fight ends | `reset: ["combat_end"]` |
| It fades between steps | `change`, on the `turns` (or `ticks`) clock; a stunned or slowed character loses the turns, so the combo fades while they cannot act |
| Finishers spend it | a skill with `"cost": {"example.combo": 3}`, or a script calling `NamedResources.value` and `spend` |

`RESOURCES` and `WORLD POOLS LIST` show a resetting pool as `back to 0 on a miss
and when a fight ends`.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `RESOURCES` | players | Your pools in words: "Heat: 40 of 100 (loses 5 every tick)". It takes no game time. |
| `WORLD POOLS LIST` / `CHECK` / `RELOAD` | builder, DEV=1 | The installed definitions, their problems, a fresh read. |
| `WORLD POOLS SHOW [character]` | builder, DEV=1 | A character's pools. |
| `WORLD POOLS SET pool n [character]` | builder, DEV=1 | Sets a test value, kept within the bounds. |
| `WORLD CHECK ONLY=POOLS` | builder, DEV=1 | The same checks as `WORLD POOLS CHECK`. |

## Scripts

A trusted JavaScript extension can use the module through `NamedResources`:

```javascript
var R = Packages.com.planet_ink.coffee_mud.core.NamedResources;
if (R.enabled()) {
    R.grant(mob(), "example.focus");          // at its start value, if new
    R.gain(mob(), "example.heat", 10);        // returns what was added
    var m = new java.util.TreeMap(); m.put("example.focus", java.lang.Long.valueOf(5));
    R.spend(mob(), m, "my-script");           // all or nothing; throws with the reason
    var heat = R.value(mob(), "example.heat"); // 0 when the character has none
}
```

`set`, `reserve`, `release` and `event(mob, "miss" or "combat_end", cause)`
(the resets, for a scripted miss) are also there. Every call refuses when the
module is not active in the campaign.

## Saves

Each character's pools are kept in the campaign save with the module: value,
the clock reading they were settled at, reservations, and the version used. With
the module suspended (switched off or its manifest removed), the records are kept
and the pools cannot be used; switching it back on resumes them.

Implementation: [NamedResources.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/NamedResources.java).
Tests: [a15-resources.js](../../tools/fixtures/fixes/a15-resources.js),
[a14-reserve.js](../../tools/fixtures/fixes/a14-reserve.js),
[a20-combos.js](../../tools/fixtures/fixes/a20-combos.js).
