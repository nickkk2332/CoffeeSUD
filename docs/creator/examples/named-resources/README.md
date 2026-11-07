# Example pack: named resources

[Chapter 34](../../34-named-resources.md)

The module manifest and three pools: heat, which cools by 5 every tick; focus,
which refills by 1 every 2 ticks and can be reserved; and three cinder charges,
which lose one every 10 ticks.

Install into a world:

```text
modules/abilities.resources.json -> world/modules/
pools/*.json                     -> world/pools/
```

Then add `abilities.resources` to `MODULES` in `world/world.ini`, run
`WORLD CHECK ONLY=MODULES,POOLS`, and start a new game. `WORLD POOLS SET
example.focus 30` gives your character a pool to look at with `RESOURCES`.

To cost and gain them from a composed skill, add to its definition:

```json
"resources": {"cost": {"example.focus": 20}, "gain": {"example.heat": 15, "example.cinder": 1}}
```
