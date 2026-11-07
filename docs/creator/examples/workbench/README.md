# Example pack: the workbench

[Chapter 17](../../17-item-modifiers-crafting.md)

A three-operation workbench and one staged recipe for `items.modifiers`, beside
the [item-modifier pack](../item-modifiers/README.md) (whose affixes it uses):

- `workbench.cloak`, a cloak's base (one prefix, one suffix), whose declared
  successor is `workbench.greatcloak` (two prefixes);
- `workbench.protect` binds one modifier (1 IRON);
- `workbench.replace` unpicks one modifier and weaves another in its place, in
  one settlement (2 IRON): `MODCRAFT PREPARE workbench.replace "workbench cloak" 2 embertrial.piercing`;
- `workbench.upgrade` extends a cloak to a greatcloak, keeping its modifiers
  (3 IRON);
- `workbench.cloak-work`, a staged recipe: spin (2 COTTON), stitch (1 COTTON)
  and rivet (1 IRON), then a woven cloak with one modifier.

Targeted crafting ([chapter 17](../../17-item-modifiers-crafting.md#targeted-crafting-imbue-reforge-and-catalysts)):

- `workbench.imbue` draws one modifier (2 IRON); `workbench.reforge` draws
  again every unprotected one (3 IRON), leaning to defense (`defense` x2);
- three catalysts, named after WITH and paid with the craft:
  `workbench.coal` (1 COAL, offense x4), `workbench.ash` (1 ASH, excludes
  offense) and `workbench.amber` (1 AMBER, the first modifier drawn is of the
  defense family);
- `workbench.ward-cloak`, a two-stage recipe whose last stage takes the same
  catalysts: a woven greatcloak with up to two modifiers.

```text
MODCRAFT PREVIEW workbench.imbue "plain workbench cloak" WITH workbench.coal
MODCRAFT PREPARE workbench.imbue "plain workbench cloak" WITH workbench.ash
MODCRAFT COMMIT
```

All need an `ember-forge` workstation. The template
`randareas/workbench.xml` makes a cloak with two modifiers (ID
`workbench_cloak`) and one with none (ID `workbench_plain`).

Install into a world that has the item-modifier pack:

```text
itemmods/*.json      -> world/itemmods/
resources/randareas/ -> world/resources/randareas/
```

The recipe's product is a catalog item named `a woven cloak`: make a GenArmor
of that name (worn about the body) and `CATALOG` it. Then:

```text
MODCRAFT LIST
MODCRAFT BEGIN workbench.cloak-work
MODCRAFT PREPARE workbench.cloak-work "unfinished cloak"
MODCRAFT COMMIT
MODCRAFT STATUS
```

Tests: [i04-workbench.js](../../../../tools/fixtures/fixes/i04-workbench.js) and
[i06-targeted.js](../../../../tools/fixtures/fixes/i06-targeted.js).
