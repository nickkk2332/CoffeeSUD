# Generation, loot and random content

[Handbook](README.md) · [G feature index](feature-index.md#g)
· [RandomAreas reference](../../CoffeeMud/guides/RandomAreas.html)

## Templates produce engine objects

The optional [tracked-modifier family](17-item-modifiers-crafting.md) adds
`MODBASE="base.id"` beside existing `AFFIXES="2"` on a generated ITEM. It records
each numeric affix's identity, tier, group and rolled values, and permits
selective crafting afterward. An ITEM SEED covers its tracked selection and
rolls; new instance IDs remain distinct. Without MODBASE, existing generation
keeps its ordinary affix behavior.

The generator reads XML definitions of strings, items, mobs, rooms, areas and
quests. IDs identify callable templates. Variables supply level, theme and
context. Selection, conditions, validation and layout rules determine the
result. Put a world overlay at `world/resources/randareas/name.xml`, then
refer to it as `randareas/name.xml`.

```xml
<AREADATA>
  <STRING id="museum_greeting" select="any-1">
    <STRING>The bells are silent.</STRING>
    <STRING>A gull circles the tower.</STRING>
  </STRING>
</AREADATA>
```

`gen.string('randareas/name.xml','museum_greeting',{})` rolls this from a
JavaScript scope. A string template does not place a room or creature.
`gen.item` and `gen.mob` return unplaced live objects; the caller must own/place
or destroy them. `gen.room` and `gen.area` link from the host's room through a
free direction. `gen.layout` returns nodes for inspection.

## Inspect before committing a dungeon

```text
GENERATE CHECK randareas/name.xml
GENLAYOUT SPOTTEDMESH 20 SEED=13
GENERATE PREVIEW AREA your_template FROM randareas/name.xml SEED=13
```

The AREA command requires an actual area template; the tiny STRING example
above cannot be used for it. Add template-specific required variables. Preview
builds a throwaway result and reports it. KEEP repeats the selected generation
for real with the same seed. SANDBOX generation keeps trial content outside
world exports. `NOSAVE` by itself is not a promise that future builder exports
will omit a live generated area.

Seeded generation owns a thread-local dice stream and stores the seed. Cold
runs, including crafted equipment, are covered by regression tests; setup no
longer requires a hidden warm-up. Other campaign threads keep their own stream
progress. Fixed seeds reproduce the same inputs on the same implementation;
changing templates, recipes or engine code can change output.

## Layout and contextual placement

Depth/position variables include ROOMTAG_DEPTH, DEPTHPCT, ISDEEPEST, INDEX,
X and Y; area summaries include maximum depth and room count. Use these to
put a boss far from the entry rather than relying on construction order.
The generator supports build ordering, multi-level layouts, themes, name
generation, affixes and catalog-backed content. See each [G entry](feature-index.md#g)
for exact fields and examples. Validate that selected room/area classes exist.

Requirements declare needed inputs. Conditions decide eligibility; validation
can reject a produced result. A rejected build needs bounded attempts and a
useful failure. Variables passed into a call are distinct from saved script
variables. Shared library IDs are public callable entry points, so being
unreferenced inside one file does not make them dead content.

`${js:expression}` and `condition="js:expression"` expose cm, require,
defined variables and relevant object/context bindings. XML-escape `<`, `>`
and `&` in attributes. Percent operators and literal percent text are preserved;
do not URL-encode the template. JS approval and runtime budgets still apply.
Keep checks and selectors cheap; avoid blocking native calls during generation.

## Generating in play

MPGENERATE creates fresh items, mobs, rooms, areas or quests with explicit
destinations and returns the created reference through its documented binding.
GENSTRING returns text. These calls supply party/player/area/time context.
Legacy FROMGENFILE is cached unless the supported FRESH form is requested;
choose whether identical calls should reroll. Unlimited unique nonce keys are
not a good substitute for fresh generation.

`Prop_LootTable` rolls items from XML on DEATH/OPEN/SPAWN/RESET, with chance,
roll count, gold and context. Openable container state prevents repeated farming
until reset. Catalog caps and once-ever rules still apply. The current audit records a
[cached-policy smoke failure](../../audit/developer-tools-audit.md#open-cached-catalog-smoke-failure);
verify those policies across fresh-process restores and cache reuse in your world. Affixes and crafted
quality can compose, so inspect final stats and value rather than balancing
only the base item.

Random encounters and spawn behaviors use catalogs/tables rather than full
area generation. RandomQuests and quest boards use bounded attempts and report
permanent failures. Generated quests, areas and instances have campaign save
support; seed policy does not replace save state or a reset policy.

## Validate the generated game

GENERATE CHECK covers class/reference/selector/layout/JS problems, unknown
attributes, undeclared variables, contradictory requirements and incompatible
constant definitions. It cannot prove every random combination is reachable,
solvable or balanced. Run several seeds, WORLD CHECK the resulting content,
play it as a mortal, save inside, restore in another process and complete it.
Check a failed attempt does not leave orphaned objects or block a mandatory
quest. Use stable IDs for content whose discovery must persist across versions.

## Shape, guarantees, decoration and regeneration

Use `layout="MAP"` when the authored floor plan matters more than a random
shape. MAP/MAPFILE draws room glyphs and links; GLYPH maps a character to a
room template. Multi-floor separators and stair glyphs provide vertical links.
This fixes the plan while still generating its descriptions/inhabitants. CAVE,
CLUMP, LINEAR, RING and ISLANDS are additional layout choices. The
[MAP fixture](../../tools/fixtures/fixes/g8-map.js) provides a complete XML file.

ENSURE rules guarantee counts and placements, including a key reachable without
passing its own lock. FALLBACK supplies missing content and MAX can prune extras.
Room replacement must happen before guaranteed item placement, so it cannot erase
the key. Guarantees do not prove an arbitrary scripted puzzle is solvable. See
[the ENSURE example](../../tools/fixtures/fixes/g7-ensure.js).

GENERATE FILL applies theme/content templates to existing hand-built rooms while
retaining their links. ROOM/AREA scope and KEEPTEXT choose what to decorate;
GENLOCK rooms can be protected. Trial it on a copy: filling can add another
population rather than replacing the old one. Built-in theme packs include
crypt, ruins, castle, volcano, sky, mine and sewer; shared set pieces and a theme
skeleton help new themes use consistent generation rules.

`world/affixes.xml` supplies data affixes, falling back to resource affixes.
Each AFFIX defines prefix/suffix, eligible kinds/levels, weight, power, curse
and AFFECT entries. AFFIXES on loot/spawns/templates chooses rolls. Name styles
live in `world/namegen/style.ini`, with resource fallbacks, syllables, given and
family names, prefixes/suffixes, titles and race/language defaults. NAMESTYLE,
RANDNAME and SYSTEM_RANDOM_NAME consumers can share a culture's naming rules.

Generated areas record GENSOURCE so REGENERATE can rebuild with SAME or a new
seed. UNLINKED builds without attaching the area to the host. Prop_GenLock /
REGENERATE LOCK protects hand-edited rooms and connections; regeneration
behavior and MPREGENERATE can reroll by game-time rules. Define absence,
population cleanup and save behavior before letting this run in play. Never
reroll an occupied mandatory story room without a designed recovery policy.
