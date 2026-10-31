# CoffeeSUD: developer tools and engine capabilities

`PLAN.md` turned CoffeeMud into a single-player game. This file plans what
comes next on the engine side: the capabilities a world needs that the
engine lacks or only half has, and the tools to build with them. The world
itself is the owner's to write (see `PLAN.md`, phase 6).

Each item says what the engine has now, what is missing, the design, and
where it stands. Ground rules are those of `PLAN.md`: every step keeps the
build green and the smoke test passing, larger ones also the engine suite,
and changes to the engine's behaviour need a reason.

## 1. Unique and rare items: planned

A builder wants, say, one unique sword in the world, with a rare chance to
be found in a certain kind of chest.

### What the engine has

The catalog (the `CATALOG` command in builder mode, kept in
`world/catalog.cmare`). A catalog item can have a spawn rule:

- `LIVE`: a mob that matches the rule's mask spawns carrying it.
- `DROP`: it may be on a matching mob's corpse when the mob dies.
- `ROOM`: it appears in a random matching room, on the catalog's timer
  (`CMCatalog.tick`).

Each rule has a rate (the chance), a mask (a zapper mask saying which mobs
or rooms) and a cap (the most copies in the world at once), so a cap of 1
already makes an item unique, at a time. An item has one rule.

### What is missing

1. **Containers.** No rule puts an item in a container: "a rare chance to be
   in an iron chest" cannot be said.
2. **The cap across saves.** The catalog counts a copy when one is made by
   copying (`CMCatalog.newInstance`, from `copyOf`), not when one is loaded:
   a copy restored from a save, lying in a room or in a character's pack,
   is not counted, so after a restore a second "unique" item can appear.
3. **Unique ever.** The cap means at most so many at once: a copy that is
   destroyed or lost can appear again. There is no "once only".
4. **One rule per item.** "Drops from the chieftain, or is rarely found in
   iron chests" needs two.

### Design

Both choices below are made per item, not for the whole game.

- **How unique.** Each catalog item chooses one of:
  - *at a time* (as now): at most the cap's number of copies in the world
    at once; one lost can appear again;
  - *once ever*: once the cap's number have appeared, no more, even if all
    are destroyed;
  - *once for a while*: as once ever, but a copy lost or destroyed can
    appear again after a given time (in game days).

  The game keeps, for each item, how many copies have ever appeared and
  when the last was lost, and a save holds that with the rest of the game.
- **Counting every copy.** Copies are counted as they come into the world
  from anywhere: made, loaded with the world or a save, or carried in by a
  character. The cap then holds after a restore.
- **A container rule.** `CONTAINER`, with a mask on containers (by name,
  kind, area and the like), a rate and the item's cap. When it rolls, per
  rule:
  - *on opening*: the first time a matching container is opened, the rate
    is rolled once; a container rolled stays rolled until its contents
    reset, so opening it again does not roll again;
  - *on a timer*: as `ROOM`, the item is put in a random matching container
    somewhere in the world, to be found;
  - or both.
- **Several rules per item**, each with its own spawn kind, rate and mask,
  sharing the item's cap and uniqueness.
- **Authoring.** The `CATALOG` editor in builder mode sets all of it, and
  `world/catalog.cmare` holds it, readable and editable by hand.

### Tests

A smoke session: a catalog item with a container rule, on opening, at a rate
of 100% and a cap of 1. Opening a matching chest finds it; opening a second
does not; a save and a restore do not make a second; destroyed, a once-ever
item does not come back, and a once-for-a-while one does after its time.

## 2. Builder capabilities audit: for the owner to choose from

What a builder can do without editing the engine's Java, and what is
missing, was audited across the engine. The result is in
[`audit/README.md`](audit/README.md): 409 proposed additions in 18 themes
(script triggers, script language, JavaScript, quests, NPCs and dialogue,
combat, items and puzzles, world systems, generators, crafting and housing,
factions, travel, languages, progression, presentation, saves and world
files, builder tools, testing and debugging tools), each with what exists
today, what to add and an example, and a list of what turned out to be
possible already.

None of it is planned yet. The owner marks each item yes or no, in
`tools/audit-review.html` or in the audit's summary table. Each item said
yes to is checked against the code again, then comes here as a numbered
item, in the order the owner chooses.

## Later

Items to come, as the owner raises them.
