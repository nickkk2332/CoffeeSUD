# Example pack: saved choices

[Chapter 42](../../42-saved-choices.md)

The `items.choices` manifest, one reveal and one offering, and the veiled
charms they work on, for the [item-modifier pack](../item-modifiers/README.md)
and the [loot-policy manifest](../loot-policies/README.md):

- `choices.charm`, a charm's base (two prefixes, one suffix) over six affixes
  of the item-modifier pack;
- `choices.veiled`, a loot policy whose one rarity gives two modifiers, the
  second veiled;
- `choices.seer`, a reveal at a `seer-table` (1 AMBER): three options; the
  first reveal of an affix gives 25 XP;
- `choices.altar`, an offering at an `ember-altar` (1 AMBER): it takes a charm
  and offers three `an altar charm`s of two modifiers; abandoning gives the
  amber back.

`randareas/saved-choices.xml` makes one charm (ID `choices_veiled`) or two
(ID `choices_supply`, for a loot table).

Install into a world that has the item-modifier pack and the `items.loot`
manifest:

```text
modules/items.choices.json -> world/modules/
itemmods/*.json            -> world/itemmods/
loot/*.json                -> world/loot/
offerings/*.json           -> world/offerings/
resources/randareas/       -> world/resources/randareas/
```

Add `items.loot,items.choices` to `MODULES`. The offering's result is a catalog
item named `an altar charm`: make a GenArmor of that name and `CATALOG` it.
Then, at the stations, with amber:

```text
CHOICES PREVIEW choices.seer "seer's charm"
CHOICES OPEN choices.seer "seer's charm"
CHOICES PICK "seer's charm" 1
CHOICES OPEN choices.altar "seer's charm"
CHOICES PICK OFFERING 2
```

Tests: [i07-choices.js](../../../../tools/fixtures/fixes/i07-choices.js) and
[choices-integration.py](../../../../tools/choices-integration.py).
