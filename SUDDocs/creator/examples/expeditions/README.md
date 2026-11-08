# Example pack: expeditions

[Chapter 38](../../38-expeditions.md)

The module manifest and one expedition, the Ember Depths: a chart offers two of
three modifiers (hardier foes, veteran foes, marked foes) from its seed; the
party chooses one; opening it costs 20 in coin; the run survives three deaths.

The expedition needs a generated destination in the world: a
`StdAutoGenInstance` area named `Ember Depths`, with a generator file and one
area ID (for example `GENERATIONFILEPATH=randareas/example.xml` and
`OTHERVARS=AREA_ID=maze_dungeon areasize=6`), and a room `Camp#0` to return to.
Edit `destination` and `return_room` for your own world.

Install into a world:

```text
modules/expeditions.contracts.json -> world/modules/
expeditions/*.json                 -> world/expeditions/
```

Add `expeditions.contracts` to `MODULES`, run
`WORLD CHECK ONLY=MODULES,EXPEDITIONS`, then try it:

```text
WORLD EXPEDITIONS CHART example.depths
EXPEDITION PREVIEW chart
EXPEDITION OPEN chart hardy
EXPEDITION STATUS
```

and walk into the Ember Depths.
