# Example pack: causal triggers

[Chapter 35](../../35-causal-triggers.md)

The module manifest and three triggers:

- `example.riposte`: when you block a composed hit, use `example.ember` on the
  attacker; three ticks' cooldown.
- `example.harvest`: when you, or a creature you summoned, kill, gain one
  `example.cinder` charge.
- `example.second-wind`: when you kill, a 50% chance to heal 20, for 10 focus.

They use the composed skill of the [ability composition pack](../ability-composition/README.md)
and the pools of the [named resources pack](../named-resources/README.md).
Blocks come from [combat profiles](../combat-profiles/README.md).

Install into a world:

```text
modules/abilities.triggers.json -> world/modules/
triggers/*.json                 -> world/triggers/
```

Add `abilities.triggers` to `MODULES`, and grant triggers with a property on a
character or on equipment they wear:

```text
Prop_Triggers TRIGGERS=example.riposte,example.harvest
```

`TRIGGERS` lists a character's reactions; `WORLD TRIGGERS LAST` shows the last
causal root, each reaction and what became of it.
