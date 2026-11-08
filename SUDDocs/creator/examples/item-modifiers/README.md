# Optional tracked item modifier pack

Install the parent ability-composition and combat-profile modules in a separate
deployment. Merge this pack's `modules/`, `itemmods/`, `js/` and `resources/` into its
world. Merge `affixes.xml` entries with existing affixes rather than discarding
legacy entries. Select `MODULES=items.modifiers` and validate with WORLD ITEMMODS
CHECK plus WORLD CHECK ONLY=ITEMMODS.

The pack reuses the existing XML generator and loot tables. A template's MODBASE
selects its modifier contract; AFFIXES chooses up to that many eligible tracked
affixes. Ordinary affix definitions and consumers retain their legacy path.
An ember-forge workstation and plain IRON materials enable the five example
operations: add, remove, reroll, protect and unprotect.
A loot table whose tracked items cannot be made (module disabled, definitions
failing CHECK) keeps its once-only roll. Attach the supplied
itemmods-loot-guard.js through Prop_JScript to an OPEN coffer to also tell the
player why it waits.

For a ready-to-play world use the [Ember Trial](../ember-trial/README.md).
For schemas, versioning, controls, transaction boundaries and limitations see
the [complete guide](../../17-item-modifiers-crafting.md).
