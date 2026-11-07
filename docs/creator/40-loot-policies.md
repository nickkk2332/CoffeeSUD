# Loot policies: rarities, tier limits, budgets and a guarantee

[Handbook](README.md) · [Example pack](examples/loot-policies/README.md)

The optional module `items.loot` decides how a tracked item
([chapter 17](17-item-modifiers-crafting.md)) is rolled when it drops. A **loot
policy** names one modifier base and says:

- which **rarities** an item can have, how likely each is, and how many
  modifiers each gives;
- which of the base's affixes an item may draw: up to a **tier** set by its
  level, carrying the policy's **tags**, and within a **power budget**;
- a **guarantee**: after a run of drops without a rarity, the next is that
  rarity.

Every draw comes from the campaign's own `loot` random stream (`WORLD MODULES
STREAMS`), so a loot roll never moves any other dice. A preview draws nothing,
and a sample shows exactly what the next drops would be without moving the
stream.

## Turn it on

1. Install the [item-modifier pack](examples/item-modifiers/README.md) and turn
   on `items.modifiers` (chapter 17).
2. Copy [the manifest](examples/loot-policies/modules/items.loot.json) into
   `world/modules/` and your policies into `world/loot/`.
3. Add `items.loot` to `MODULES` and run `WORLD CHECK ONLY=MODULES,ITEMMODS,LOOT`.

The manifest is native. It requires `items.modifiers` version 1 and allows the
`world` scope (the guarantee counters) and the `character` scope (each
character's LOOT filter); its state is two string fields, `guarantees` and
`filter`.

## A policy

```json
{
  "schema": 1, "id": "example.coffer", "version": 1, "title": "Supply coffer drops",
  "base": "embertrial.mantle",
  "rarities": [
    {"id": "plain", "title": "Plain", "weight": 60, "modifiers": 0},
    {"id": "magic", "title": "Magic", "weight": 30, "modifiers": 1},
    {"id": "rare", "title": "Rare", "weight": 10, "modifiers": 2}
  ],
  "tiers": [{"level": 1, "max_tier": 1}, {"level": 10, "max_tier": 2}],
  "tags": ["elemental"],
  "budget": 2,
  "guarantee": {"rarity": "rare", "after": 5}
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `base` | required | A modifier base in `world/itemmods/`. Its pool, item kind and capacity apply. |
| `rarities` | required | 1 to 8, in order from common to rare. `weight` (default 1) is its share; `modifiers` (0 to 6) how many the item gets. `title` is what LOOT shows. |
| `tiers` | none | Tier limits, by the item's level: from `level` on, affixes up to `max_tier`. Below the first entry no affix is allowed; with no entries, any tier. Levels must rise. |
| `tags` | none | Only affixes carrying every one of these tags. |
| `budget` | 0 (none) | The tiers of an item's modifiers add up to at most this. |
| `guarantee` | none | `rarity` and `after`: when `after - 1` drops in a row were not that rarity, the next one is. |

A rarity's modifier count is the most the item gets: each modifier is drawn, by
its affix weight, among the affixes still eligible, and the drawing stops when
none is. The base's groups and capacity, the campaign's `max_modifiers`, the
tier limit, the tags and the budget all apply to each pick.

## Using a policy

A generator item takes `MODBASE="POLICY=[id]"` instead of a base ID:

```xml
<ITEM ID="lootpolicy_mantle" CLASS="GenArmor" LEVEL="1" NAME="a coffer mantle"
      DISPLAY="A coffer mantle lies folded here." MATERIAL="COTTON" WEIGHT="1"
      PROPERWORN="body" MODBASE="POLICY=example.coffer" />
```

`AFFIXES` is not needed: the rarity decides. Everything else is chapter 17's
generation: loot tables, their once-only rolls, unique catalog caps and saving
work as before. While `items.loot` is not active, a policy item is refused as
unavailable, so a loot table keeps its roll for later. A
[staged recipe](17-item-modifiers-crafting.md#staged-recipes) can make its product
by a policy too.

The item records its policy and rarity with its modifiers (its source reads
`loot:example.coffer.v1:rare from ...`). The rolls draw from the `loot` stream;
a template's `SEED` does not change a policy's drop.

**The guarantee counter is the campaign's.** It is kept in the save, one per
policy, and every drop of the policy counts: loot a mob carried from its spawn,
a coffer opened by any member of the party. A forced drop and a natural drop of
the guaranteed rarity both reset it.

## Looking before rolling

`WORLD LOOT PREVIEW example.coffer 1` (builder or DEV=1) shows what a drop at
level 1 can be, and draws nothing:

```text
Loot policy Supply coffer drops (example.coffer.v1): base embertrial.mantle.v1, item level 1
Rarities:
  Plain (plain): 60.0%, up to 0 modifier(s)
  Magic (magic): 30.0%, up to 1 modifier(s)
  Rare (rare): 10.0%, up to 2 modifier(s)
Guarantee: after 4 drop(s) without Rare, the next is Rare. Drops since the last: 0.
Tier limit at level 1: tier 1 and below
Tags required: elemental
Power budget: the tiers of an item's modifiers add up to at most 2
Modifiers the first pick can draw:
  embertrial.fireguard1 (Wardwoven, prefix tier 1): eligible, 50.0% of first picks
  embertrial.fireguard2 (Flameward, prefix tier 2): not eligible: tier 2 is above the limit of 1 at level 1
  embertrial.piercing (Embers, suffix tier 1): eligible, 50.0% of first picks
Later picks leave out the groups and slots already taken. Nothing was drawn.
```

`WORLD LOOT SAMPLE example.coffer 500 1` draws 500 drops on a cursor at the loot
stream's next draw, with the guarantee counter as it is now. Neither the stream
nor the counter moves, so the next real drops of the policy are the first drops
of the sample. It counts each rarity beside its chance, counts each affix drawn,
and checks itself against the preview: "Every sampled modifier is one the
preview lists as eligible", or the ones that disagree.

## LOOT: what dropped, in plain text

`LOOT [container or corpse]` lists the tracked items in it by rarity, with each
modifier's name and values, and counts the rest:

```text
In a policy coffer:
  Rare: a Wardwoven coffer mantle of Embers; Wardwoven (resistance fire 14); Embers (penetration fire 9)
  Plain: a coffer mantle, no modifiers
1 other item(s).
```

`LOOT FILTER rare magic` makes your LOOT list only those rarities and count the
others ("2 tracked item(s) your filter leaves out"); `LOOT FILTER ALL` lists
everything again. Each character has their own filter, kept in the save. LOOT
takes no game time and changes nothing; a closed container says so.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `LOOT [container]` | players | Its tracked loot by rarity, through your filter. No game time. |
| `LOOT FILTER [rarity...]` / `ALL` | players | The rarities your LOOT lists. |
| `WORLD LOOT LIST` / `CHECK` / `RELOAD` | builder, DEV=1 | Policies; problems (a base not installed, tags no affix carries, a guaranteed rarity the policy lacks), with file and JSON path; a fresh read. |
| `WORLD LOOT PREVIEW [policy] ([level])` | builder, DEV=1 | Chances, limits and each affix's eligibility. Draws nothing. |
| `WORLD LOOT SAMPLE [policy] [n] ([level])` | builder, DEV=1 | n drops on a cursor; agreement with the preview. Moves nothing. |

```javascript
var L = Packages.com.planet_ink.coffee_mud.core.ItemLoot;
L.preview("example.coffer", 10);   // the text above
L.since("example.coffer");         // drops since the guaranteed rarity last fell
L.dropOf(item);                    // [policy, version, rarity], or null
```

## Limits

A policy draws for one base. The guarantee is a count of drops, not a chance that
grows. Rarity is recorded on the item and shown by LOOT; it does not change the
item's name. Up to 8 rarities and 16 tier limits; the save keeps the counters of
as many policies as fit in 4,096 characters.

Implementation: [ItemLoot.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/ItemLoot.java).
Tests: [i02-loot.js](../../tools/fixtures/fixes/i02-loot.js) and
[loot-policy-integration.py](../../tools/loot-policy-integration.py) (`tools/fixes.sh loot`).
