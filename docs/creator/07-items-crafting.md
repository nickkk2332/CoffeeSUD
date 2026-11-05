# Items, puzzles, production and homes

[Handbook](README.md) · Feature families [I](feature-index.md#i), [CR](feature-index.md#cr)

## Item definitions and powers

Choose an item class that implements the needed mechanics: a container, weapon,
armor, readable book, light, recipe book or rideable. Set material, weight, value,
wear positions, durability, level and descriptive text. Use tags for authored
categories; a prose label such as “iron” does not replace the material code.

Books retain pages in the item and world export. READ also handles directional
exit writing. Foreign inscriptions use language tags or readable language
effects. `FILE=` content links remain external assets, so ship the referenced
file. `CHECKTEXT` checks missing links and markup.

| Property/system | What it adds |
| --- | --- |
| Prop_When | Conditional property/behavior activation |
| Prop_Transform / MPTRANSFORM | Change form over time/conditions, or replace an item in place |
| Prop_ItemSet | Named sets, worn counts, tier effects and identification progress |
| Prop_Cursed | Hidden curse, detection threshold and clean Remove Curse behavior |
| Prop_Invokable | Activated powers, charges, recharge, cooldown and use masks |
| Prop_Attunement | Unlock powers after an authored attunement requirement |
| Prop_ItemGrowth | Growth through use with saved progress |
| Durability options | Wear rate, broken rather than destroyed, unbreakable behavior |
| ItemUpgrader | Upgrade paths at a smith/workbench service |

Item sets can declare tiers on a piece or in `world/itemsets.txt`. For example,
one piece can have `SET=bone PIECES=4 TIER2="Prop_WearAdjuster(con+2)"` and
other pieces `SET=bone`. Count correctly worn pieces, not items merely carried.
Legacy `ALLSET="name-3"` also supports mixed adjusters, spell casters, skill
enablers, resisters and extra damage with independent thresholds. Test equip,
remove, destruction, death and restore. Identification shows active/inactive
tiers; hidden curse detection depends on the examiner's level.

## Puzzle primitives

| Primitive | Player action / author contract |
| --- | --- |
| Prop_Switch | Pull/push/turn/press, named or numeric states, state actions and reset |
| Prop_PressurePlate | Weight/presence activation and release |
| Receptacles | Accept/consume/match items and run actions |
| Prop_ComboLock | Dials, code and keypad checks |
| Riddler | Alternative answers, tries and consequences |
| Prop_Discoverable | Searchable hidden content without a specific stock class skill |
| Prop_Detector | A carried tool reacting to nearby matches |
| Prop_BlockedExit | Clear, dig or attack an obstruction using its configured rules |
| GenTrap | Authored trigger/disarm conditions and script-only traps |
| Rich keys/locks | Master keys, consumable keys and unpickable locks |
| USE / COMBINE | Event scripts and rows in `world/combinations.txt` |

Switch parameters use `STATES`, `START`, `ACTION_state`, `MSG_state`,
`DESC_state`, `RESET`, `REQUIRES` and `ONCE`. Actions can manipulate doors,
set variables or send signals. `MPDOOR` updates both sides of a door and can
target a remote room. The [switch recipe](cookbook.md#3-a-lever-that-opens-a-gate)
shows exact syntax. A blocked exit attacked with a weapon uses combat-adjusted
damage and normal message hooks; digging/clearing retains tool/skill rules.

`PUZZLE LIST`, `CHECK`, `SET`, `RESET` and `SIGNAL` operate existing primitives.
SET and RESET run real actions and can change the world. RESET rearms a
one-shot switch. This console does not create a generic PuzzleController;
that feature remains deferred. Build multi-step puzzles with explicit saved
variables/signals, and make reset semantics part of the design.

Always provide a recoverable path after a wrong input, a dropped component,
an NPC's death or leaving halfway through. A successful clue is usually saved
per character/profile or physical puzzle instance, rather than keyed to its
current description. Duplicate source IDs intentionally share clue state.

## Crafting and learning recipes

Generic craft/gather/wright skills are data definitions. Existing native
crafting skills also consume recipe resources. Overlay world-specific files
under `world/resources/`; keep their tab-separated column layout. Use
`VALIDATERECIPES` before asking a player to spend materials.

Recipe requirement keys can include:

| Key | Meaning |
| --- | --- |
| MASK | Crafter eligibility; ineligible recipes are hidden/refused |
| ROOMMASK | Required location/conditions |
| TOOL | Required carried/held/nearby item |
| STATION | Required workstation tag |
| FAILMSG | Useful refusal for unmet requirements |
| XP / TATTOO | Reward per success / first-success mark |
| HIDDEN | Must know the recipe |
| EXPERIMENT / HINT | Ingredients and partial discovery hint |
| NOQUALITY | Skip quality rolls for this recipe |

For GenCraftSkill these live in KEY_VALUE_PARMS; other crafting skills can use
a trailing requirements column in the supported format. These keys describe
the craft; they do not become arbitrary stats of its output. Consult
[RECIPEKEYS](tool-reference.md#recipekeys) for exact quoting.

`Prop_KnownRecipes`, recipe books and RECIPE rewards control learning. A
GenCraftSkill with CANLEARN can dismantle a matching item into a carried book's
blank page; LEARNMASK narrows eligible items. EXPERIMENT discovers authored
hidden recipes with daily attempt limits; unsuccessful experiments can consume
materials. Keep the hint and failure cost understandable.

`Prop_Workstation` supplies TAG, skill restrictions, speed/proficiency bonuses,
maximum recipe level and quality adjustment. Tagged objects can satisfy a
station requirement without giving bonuses. Tool wear and crafted quality tiers
add progression; disable quality for outputs that should have fixed mechanics.

## Resources and gathering

`materials.txt` defines custom material codes: keep codes stable after release.
`resources.txt` adds named variants. Weighted/masked data gathering entries,
hunting/fishing masks and seasonal room resources control what can be found.
Depletion and regrowth use game time and save their state. Do not promise
infinite daily harvesting when the world's model depletes a source.

`crops.txt` and Prop_CropRules control seasons, sun, domains, water, growth
hours, yield and refusal text. File rules merge with room/seed overrides. Crop
growth, in-progress production and supported assisted work survive a save.
Assistance links bind back to the producer's live ability before work ticks;
arbitrary custom transient references need their own state contract.

Salvage/dismantle rules provide data-driven yields. Craft/gather XP can apply
to every class; balance repeatable XP against material supply and time.

## Housing, construction and furniture

Homes can belong to the profile and be pre-owned. Property script functions
and commands expose ownership and changes. A world can allow building without
a deed; use masks and location rules so construction cannot accidentally erase
critical routes. GenWrightSkill defines building operations as data.
`Prop_Furnishing` changes room conditions through furniture, and party home
travel supplies a route back.

Test a home from two characters in the same profile, after leaving and after
restore. Ownership and shared use are not the same as a linked inventory:
`Prop_SharedStash` remains deferred. Ordinary chests and profile-owned property
are available now.
