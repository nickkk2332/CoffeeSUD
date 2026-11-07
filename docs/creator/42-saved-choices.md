# Saved choices: reveals and offerings

[Handbook](README.md) · [Example pack](examples/saved-choices/README.md)

The optional module `items.choices` keeps a few options until the player
chooses one. Each choice is drawn **once**, from the campaign's own `offers`
random stream (`WORLD MODULES STREAMS`), and kept: opening it again, or
restoring a save, shows the same options, and choosing settles it once. There
are two kinds:

- a **reveal** turns a *veiled* modifier of a tracked item into a few legal
  affixes, kept on the item, of which the player picks one;
- an **offering** takes an item for good and offers a few replacements, kept on
  the character, of which the player takes one, or abandons them all by the
  offering's refund rule.

## Turn it on

1. Install the [item-modifier pack](examples/item-modifiers/README.md) and turn
   on `items.modifiers` ([chapter 17](17-item-modifiers-crafting.md)); for
   veiled loot, also `items.loot` ([chapter 40](40-loot-policies.md)).
2. Copy [the manifest](examples/saved-choices/modules/items.choices.json) into
   `world/modules/` and your reveals and offerings into `world/offerings/`.
3. Add `items.choices` to `MODULES` and run
   `WORLD CHECK ONLY=MODULES,ITEMMODS,LOOT,CHOICES`.

The manifest is native. It requires `items.modifiers` version 1 and allows the
`character` scope; its state is two string fields: `offering` (the open
offering) and `codex` (the affixes the character has revealed).

## Veiled modifiers

A loot policy's rarity can give some of its modifiers veiled:

```json
{"id": "veiled", "title": "Veiled", "weight": 1, "modifiers": 2, "veiled": 1}
```

The rarity's last `veiled` picks (0 up to its `modifiers`) are drawn as usual,
then kept only as their slot: a veil holds a prefix or a suffix slot, gives no
stats, and has no affix yet. ITEMMODS lists it ("Veiled 1. a veiled prefix (a
Veiled drop): not yet revealed; it gives nothing until it is"), and LOOT shows
"a veiled prefix". A veil counts toward the item's capacity and the campaign's
modifier limit.

## A reveal

```json
{
  "schema": 1, "id": "choices.seer", "version": 1, "title": "The seer's reading",
  "kind": "reveal", "station": "seer-table",
  "cost": {"material": "AMBER", "units": 1},
  "options": 3,
  "discovery": "25 XP"
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `station` | required | The workstation tag it needs (a `Prop_Workstation`). |
| `cost` | none | `material` (a registered raw material) and `units`, paid in plain piles as crafting pays. |
| `options` | 3 | How many affixes to draw, 1 to 5, each different. |
| `affixes` | all | Only these affixes of the base's pool. |
| `modifier_tags` | none | Only affixes carrying every one of these tags. |
| `discovery` | none | Reward words ([quest rewards](08-quests.md)) given the first time a character reveals each affix. |

```text
CHOICES PREVIEW choices.seer "seer's charm"
CHOICES OPEN choices.seer "seer's charm"
CHOICES PICK "seer's charm" 2
```

PREVIEW lists every affix of the base's pool with its chance to be among the
options, or why it cannot be: not of the veil's position, its group already on
the item, outside the reveal's affixes or tags. OPEN pays the cost and draws the
options among the affixes the item could hold were the veil gone, by weight,
each different, each rolled; they are kept on the item with the reveal's pin.
Opening the same veil again is refused: "its options are kept". With a number,
OPEN reveals that veil (ITEMMODS numbers them); without one, the first not yet
revealed.

PICK settles the item's revealed veil with one option, if it still fits (a
modifier added since may have taken its group; then choose another). The veil
and the other options are gone. The first time a character settles an affix,
it goes in their **codex** and the reveal's `discovery` reward is given; an
affix in the codex already gives nothing more, from any veil. CHOICES lists the
codex.

When nothing could be revealed (every affix of the position is taken or left
out), PREVIEW and OPEN refuse, and nothing is paid or drawn.

## An offering

```json
{
  "schema": 1, "id": "choices.altar", "version": 1, "title": "The ember altar",
  "kind": "offering", "station": "ember-altar",
  "cost": {"material": "AMBER", "units": 1},
  "accepts": {"tags": ["charm"]},
  "options": 3,
  "result": {"catalog": "an altar charm", "base": "choices.charm", "modifiers": 2},
  "refund": "cost"
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `station`, `cost` | | As a reveal's. |
| `accepts` | required | `tags` (a tracked item carrying every one) and/or `mask` (a [zapper mask](24-behaviors-and-masks.md) the item passes). |
| `options` | 3 | How many replacements, 1 to 3. |
| `result` | required | `catalog`: the catalog item each option is made of, and either `base` with `modifiers` (0 to 3) or `policy` (a loot policy; its rarity decides the modifiers and veils). |
| `refund` | `none` | What abandoning gives back: `none`, or `cost` (the materials paid). |

```text
CHOICES PREVIEW choices.altar "seer's charm"
CHOICES OPEN choices.altar "seer's charm"
CHOICES PICK OFFERING 2
CHOICES ABANDON
```

OPEN pays the cost, takes the item (it is destroyed: it cannot also stay in
the inventory) and draws the options, each a whole item's modifiers, kept on
the character until PICK OFFERING or ABANDON. A character has one open offering
at a time. PICK OFFERING makes the chosen option from the catalog item, with
its modifiers as drawn; ABANDON closes the offering by its refund rule. Both
settle it once: a second one finds no offering open. The open offering is
kept in 4,096 characters, so an offering has at most three options of three
modifiers; WORLD CHECK flags a policy whose rarities give more.

## Inspect and test

- `CHOICES` lists the open offering, the revealed veils on what you carry, and
  your codex.
- `WORLD CHOICES LIST` lists the installed reveals and offerings;
  `WORLD CHECK ONLY=CHOICES` checks them (materials, affixes, bases, policies).
- `WORLD MODULES STREAMS` shows the `offers` stream's counter: a preview never
  moves it, an opening moves it by what it drew.

Tests: [i07-choices.js](../../tools/fixtures/fixes/i07-choices.js) (the rules)
and [choices-integration.py](../../tools/choices-integration.py) (real play, a
real save and restore).
