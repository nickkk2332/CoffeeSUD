# Optional combat-profile pack

Install the [ability-composition pack](../ability-composition/README.md) first.
Merge this pack's `modules/` and `combat/` into a separate world's directories,
then select `MODULES=combat.profiles` in its world.ini. The module depends on
`abilities.composition` and enables nothing in ordinary worlds.

`example.final` scales converted damage by final type; `example.history` also
retains original-type scaling. Both use the same explicit defense rules. The
manifest pins the exact hash of final v1 for newly adopted campaigns.

Use `WORLD COMBAT USE example.history 1` to select the alternative in a campaign;
SAVE retains the choice. Grant `Skill_Compose`, select Ember bolt and inspect
actual hits/burn ticks with `COMBATRESULT`. Equipment needs explicitly authored
`Prop_CombatStats` effects to contribute to these rules.

See the [full guide](../../16-combat-profiles.md) for the schema, cookbook,
formula order, controls, persistence and limitations. The pure and played tests
validate this pack without changing the starter world.
