# Example pack: loot policies

[Chapter 40](../../40-loot-policies.md)

The `items.loot` manifest and one policy, `example.coffer`, for the trial
mantle base of the [item-modifier pack](../item-modifiers/README.md):

- Plain (60%, no modifiers), Magic (30%, one) and Rare (10%, two);
- tier 1 affixes from level 1, tier 2 from level 10;
- only affixes tagged `elemental`; a power budget of 2;
- a guarantee: after four drops without a Rare, the fifth is Rare.

`randareas/loot-policies.xml` (ID `lootpolicy_supply`) makes two mantles by the
policy, for a loot table such as
`Prop_LootTable FILE=randareas/loot-policies.xml ID=lootpolicy_supply ON=OPEN`.

Install into a world that has the item-modifier pack:

```text
modules/items.loot.json -> world/modules/
loot/*.json             -> world/loot/
resources/randareas/    -> world/resources/randareas/
```

Add `items.loot` to `MODULES`, then:

```text
WORLD LOOT PREVIEW example.coffer 1
WORLD LOOT SAMPLE example.coffer 500 10
LOOT coffer
LOOT FILTER rare
```

Tests: [i02-loot.js](../../../../tools/fixtures/fixes/i02-loot.js) and
[loot-policy-integration.py](../../../../tools/loot-policy-integration.py).
