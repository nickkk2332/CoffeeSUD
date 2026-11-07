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
  upkeep, say). What is reserved cannot be spent until it is released.

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
supports' amounts add up. `COMPOSE SHOW` lists them. A composition that costs
resources cannot be cast while `abilities.resources` is off; gains are skipped
while it is off. WORLD CHECK ONLY=POOLS reports a composition that names a pool
that is not installed.

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

`set`, `reserve` and `release` are also there. Every call refuses when the
module is not active in the campaign.

## Saves

Each character's pools are kept in the campaign save with the module: value,
the clock reading they were settled at, reservations, and the version used. With
the module suspended (switched off or its manifest removed), the records are kept
and the pools cannot be used; switching it back on resumes them.

Implementation: [NamedResources.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/NamedResources.java).
Tests: [a15-resources.js](../../tools/fixtures/fixes/a15-resources.js).
