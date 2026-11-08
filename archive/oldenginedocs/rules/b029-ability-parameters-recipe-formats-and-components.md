# Ability parameters: recipe formats and ability components

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b029` (Ability parameters (recipe formats) and components) · 6 files · 5081 lines of Java.

## Summary

Most crafting skills in CoffeeMud are data driven. Each such skill names a tab-separated recipe file under `resources/skills/` and returns a *format string* such as `ITEM_NAME\tITEM_LEVEL\tBUILD_TIME_TICKS\t...` (for example `Abilities/Common/Fletching.java:79-82`). The format string is the schema: each column name selects a column editor (102 classes in `Libraries/editors/`) that validates the value, prompts for it on the command line or in a web form, and can derive it from an existing item. One declaration therefore drives the file parser, the in-game recipe editor, the web editor and the "learn this recipe from an item" path.

The inputs to a craft are *ability components*: a small text grammar of the form `(LOCATION:FATE:AMOUNT:ID(SUB):MASK)`, joined by `&&` or `||` and optionally ending in a quoted description. Component definitions live in `resources/skills/components.txt` or inline in a recipe's MATERIALS column. The same machinery gates spells and other abilities (`StdAbility.checkComponents`), and a *trigger* component can replace materials with a ritual of spoken phrases and socials. Common-skill slot limits (`AbilityLimits`) are served by the same library.

Two readers parse the recipe files: a runtime reader and an editor reader. They agree on the common case but differ in several edge cases, and the component evaluator has connector-logic defects, both listed under Problems.

## Key files and types

Paths are relative to `com/planet_ink/coffee_mud/` unless they begin with `resources/`, which is relative to the CoffeeMud folder.

| File or type | Role | Lines |
|---|---|---|
| `Libraries/CMAbleParms.java` | Format-string parser, recipe-file parser and writer, interactive and web editing hooks, `makeRecipeFromItem`, crafting brands, misc-text validator (`isValidMiscText`) | 2073 |
| `Libraries/CMAbleComps.java` | Component text parser (`addAbilityComponent`), `componentCheck`, `makeComponents`, `destroyAbilityComponents`, descriptions, `components.txt` loading and rewriting, trigger hooks, common-skill limits | 1612 |
| `Libraries/interfaces/AbilityParameters.java` | `AbilityParmEditor` contract (the per-column editor), `ParmType` enum, `AbilityRecipeData` and `AbilityRecipeRow` | 500 |
| `Libraries/interfaces/AbilityComponents.java` | Component library interface and `AbilityLimits` interface | 427 |
| `Common/interfaces/AbilityComponent.java` | Component descriptor interface; enums `CompConnector`, `CompLocation`, `CompType` (216-223, 231) | 232 |
| `Common/DefaultAbilityComponent.java` | Descriptor implementation and defaults (38-49) | 237 |

Related code outside this bucket, followed only as far as needed:

| File and lines | Role |
|---|---|
| `Libraries/CoffeeUtensils.java` 163-300 | Runtime recipe reader `loadRecipeList` (242-300) and merging of recipe pages into a skill (163-200) |
| `Abilities/Common/CraftingSkill.java` 204-235, 258-299, 1227-1258, 1409-1530, 1928-1962, 2230-2302, 2356-2440 | Recipe loading and sorting, `%` name substitution, level-based choice, recipe-name matching, deconstruction and learning, component lookup for crafts |
| `Abilities/Common/CommonSkill.java` 293-310, 346-362, 751-812 | Runtime loader and cache, craft duration formula, learn limits |
| `Abilities/Common/EnhancedCraftingSkill.java` 347-370 | Level sort for expertise-based crafts |
| `Abilities/Common/GenWrightSkill.java` 113-115, 191-264 | Recipes generated from `.cmare` item XML |
| `Abilities/Common/Fletching.java` 79-82, 383-472 | A typical craft flow (one of about 40 crafting skill classes) |
| `Libraries/editors/EditorMaterialsRequired.java` 68-71, 132-166 | Editor for the MATERIALS_REQUIRED column |
| `Libraries/editors/EditorNA.java` (whole) | Fallback column editor |
| `Common/interfaces/Triggerer.java` 57, 114; `Common/DefaultTriggerer.java` 38, 244-290, 1727, 1774, 1837-1851 | Ritual (trigger) engine used by trigger components |
| `Abilities/StdAbility.java` 2067, 2088-2110 | Component gate for spells and other non-crafting abilities |
| `MOBS/StdMOB.java` 3700, 4335 | Message and tick hooks for trigger rituals |
| `Commands/Create.java` 1080-1102, `Commands/Modify.java` 2124-2145, `Commands/Destroy.java` 897-904, `Commands/ListCmd.java` 4524-4541 | Builder commands for component definitions |

## How it works

### 1. The format string is the schema

`CMAbleParms.parseRecipeFormatColumns` (`CMAbleParms.java:302-376`) turns a format string into an alternating list of *data columns* (a `List<String>` of one or more names) and *separators* (literal text, normally a tab). Names joined by `||` form one multi-use column. A `...` separator is a repeat marker that only the editor reader understands (349-356).

Fletching's format (`Abilities/Common/Fletching.java:79-82`) gives thirteen columns, and its first recipe line in `resources/skills/fletching.txt` is:

```
ITEM_NAME  ITEM_LEVEL  BUILD_TIME_TICKS  MATERIALS_REQUIRED  ITEM_BASE_VALUE  ITEM_CLASS_ID  AMMO_TYPE  AMMO_CAPACITY  BASE_DAMAGE  MIN_MAX_RANGE  OPTIONAL_RESOURCE_OR_MATERIAL  CODED_SPELL_LIST  WEAPON_HANDS_REQUIRED
crude % atlatl   4          20                6                   160              GenSling       javelins   1              8            2              (empty)                        (empty)           (empty)
```

Points that follow from the code:

- **Column editors validate.** `fixDataColumn` (`CMAbleParms.java:698-758`) looks up each named column's editor and checks the value with `confirmValue`. A bad value raises `Item id X has bad data ... at row N` (746-755).
- **Multi-use columns pick an editor per row.** For `STATUE||RIDE_BASIS||CONTAINER_TYPE_OR_LIDLOCK||CODED_WEAR_LOCATION` (Blacksmithing), the editor with the highest `appliesToClass` score for the row's sample object is chosen (721-734).
- **The sample object comes from the class column.** `getClassFieldIndex` (433-460) looks for `ITEM_CLASS_ID`, `FOOD_DRINK`, `COLOR_TERM`, `ITEM_CMARE` or `BUILDING_CODE`. `getSampleObject` (463-532) turns that value into a prototype: `GenFood` for FOOD, `GenDrink` for DRINK, an item, mob, area or room prototype for XML values starting with `<ITEM>`, `<MOB>`, `<AREA>` or `<ROOM>`, and `StdItem` by default (531).
- **Fallback column.** A name with no editor, or an editor that does not apply to the row, falls back to `N_A` (`CMAbleParms.java:709-712`). `EditorNA` applies to nothing (`appliesToClass` returns -1) and accepts only "", "0", "NA" or "-".
- **The `%` marks the material name.** In `crude % atlatl` the `%` is replaced by the material's name when the item is made (`CraftingSkill.java:279-299`, `determineFinalName` at 258-263). The same helper strips the `%` for name matching.
- **Level 0 means no level gate.** Fletching accepts a recipe when `level<=xlevel(mob)` (`Fletching.java:392`), which is always true for 0. Level 0 is excluded from `getCraftableLevelRange` (`CraftingSkill.java:1260-1273`).
- **Recipes are sorted by level on load.** `CraftingSkill.loadRecipes(filename)` sorts by the level index (204-235). `EnhancedCraftingSkill.loadList` sorts by the first column whose name ends in `_LEVEL` (347-370).
- **Recipe-name matching.** `matchingRecipes` (`CraftingSkill.java:1409` onward) accepts `name.N` to select the Nth match and a bare integer to select by level. On non-loose calls, exact names (with `%` removed) are tried first (1500-1520). The word-prefix and word tiers run only when nothing has matched yet (1618-1650).

### 2. Two readers of the same files

**Runtime reader.** `CommonSkill.loadList` (`CommonSkill.java:293-296`) calls `CMLib.utensils().loadRecipeList(text, true)` (`Libraries/CoffeeUtensils.java:242-300`). That method splits each line on tabs, skips lines that begin with `#`, ignores lines with no tab when `tabs` is true, accepts CR or LF endings, and pads every row with empty strings to the longest row. Results are cached as `PARSED_RECIPE: <file>` (`CommonSkill.java:299-310`; `CraftingSkill.java:204-235`). Herbology and Floristry pass `tabs=false`, which keeps any line of two or more characters as a one-field recipe (`Abilities/Common/Herbology.java:91`, `Floristry.java:91`).

**Editor reader.** `CMAbleParms.parseDataRows` (`CMAbleParms.java:585-689`) uses `stripData` (552-582) to cut each column at its exact separator text. A row ends at a newline. `#` lines are skipped only at the start of a row (630-641). A row that runs out of input before all columns are filled raises `Row N has X/Y` (679-680). `fixDataColumns` (760-776) then validates every cell.

Both readers pad a short row at its end: `decorations.txt` rows have six fields against a seven-column format, and the empty last field (`DECORATION_FLAG`) is a valid choice because `EditorDecorationFlag` lists "" among its choices.

Where the two readers disagree (traced by reading, not executed):

- A blank line in the middle of a file is ignored by the runtime reader. The editor reader builds a row of empty cells, because `stripData` returns no data at a newline and the last column consumes the newline (`CMAbleParms.java:552-582`, 628-636, 679-681). The empty ITEM_NAME then fails `confirmValue` (1364-1378), so the editor marks the whole file with a parse error (1196-1201). None of the formatted recipe files in `resources/skills/` contains a blank line.
- `.cmare` item files are an XML wrapper of `<ITEM>` elements. Only the editor reader unwraps them (`CMAbleParms.java:596-625`). Runtime handles them in `GenWrightSkill` (section 5).

### 3. Editing recipes

- **In game.** `modifyRecipesList` (`CMAbleParms.java:974-1080`) shows the rows with numbers. `A` adds a row through `newRow`, a number edits that row column by column, and saving offers V)FS, F)ilesystem or C)ancel (1064-1079). It is called from `Commands/Modify.java:2687`. `ListCmd.java:3895` prints the same table.
- **Saving.** `resaveRecipeFile` (1083-1127) joins the columns back into lines, wraps `<ITEM>` content in `<ITEMS>` (1101-1106), writes to the VFS (`::`) or the local folder (`//`), mirrors a local save into the VFS copy if one exists (1113-1121), and clears the `PARSED_RECIPE` and multi-list caches (1124-1125).
- **Round trip.** `testRecipeParsing` (797-852) parses a recipe, runs every editor's `fakeUserInput`/`commandLinePrompt` over it through a fake session, and optionally saves. It validates lines written into a recipe book (`Libraries/CMGenEditor.java:2452`, which converts commas to tabs first) and is also used by `WebMacros/grinder/GrinderItems.java:534`.
- **Web.** `WebMacros/AbilityRecipeData.java` uses `parseRecipe`, `getEditors` and `resaveRecipeFile` (84-254).

### 4. Learning a recipe from an item

- **Deconstruction.** `CraftingSkill.doLearnRecipe` (`CraftingSkill.java:2356-2440`) finds a recipe book with a free page (2418-2423) and refuses an item whose name is already written in the book (2431-2437). It starts a learning activity, and the skill's completion calls `deconstructRecipeInto` (for example `Abilities/Common/Blacksmithing.java:226`). `deconstructRecipeInto` (`CraftingSkill.java:1928-1962`) requires that the skill supports deconstruction and may craft the item (1932-1940). On success it calls `CMLib.ableParms().makeRecipeFromItem(C, I)` (1952) and appends the new line to the book.
- **Generation.** `makeRecipeFromItem` (`CMAbleParms.java:379-430`) asks each column editor for `convertFromItem`. `EditorMaterialsRequired.convertFromItem` (132-166) returns an integer computed from weight, `round((weight-1) × the crafter's item weight multiplier)` with a minimum of 1. If the item has extra materials, it returns a component string of ONGROUND terms: one MATERIAL term for the main material and one RESOURCE term per extra material.
- **Merging into a skill.** `CoffeeUtensils.addExtRecipe` (163-200) appends the book's lines to the skill's recipe list. A book line shorter than the previous recipe is padded with empty fields to that width. Book lines therefore become additional recipes in the same column layout.

### 5. Recipes generated from data (GenWrightSkill)

`GenWrightSkill.getRecipeFormat` returns the single column `ITEM_CMARE` (`GenWrightSkill.java:113-115`). The recipe source is an XML export of item prototypes under `resources/skills/`: `shipbuilding.cmare` (671,557 bytes), `caravanbuilding.cmare` (988,321 bytes, named by `CaravanBuilding.java:80`) and `clancastles.cmare` (6,062 bytes). `getWrightables` parses the XML with `CMLib.coffeeMaker().addItemsFromXML` (201-228). `loadRecipes` then writes a generated tab file under `::skills/` with six columns per prototype: name, level, `weight/10` as build ticks, weight as the material amount, gold value and an index. The lines end in `\r\n` (`GenWrightSkill.java:236-264`, generated line at 249-258). The generated file is loaded through the normal `CraftingSkill.loadRecipes`.

### 6. The component data model

Fields of `AbilityComponent`, with the defaults set in `DefaultAbilityComponent.java:38-49`:

| Field | Values | Default | Meaning |
|---|---|---|---|
| connector | AND, OR, MESSAGE (216) | AND | How this term joins the previous one. MESSAGE marks the trailing description |
| location | INVENTORY, HELD, WORN, HAVE, NEARBY, ONGROUND, TRIGGER (223) | INVENTORY | Where the item must be |
| consumed | boolean | true | Whether a matched item is destroyed |
| amount | int | 1 | Item count for STRING terms; weight in pounds for RESOURCE and MATERIAL terms (`makeItemComponent` sets the weight, `CMAbleComps.java:80-81`) |
| type | RESOURCE, MATERIAL, STRING (231) | STRING | How the item is identified |
| long type, string type, subtype | resource or material mask; name; subtype text | 0, "", "" | The match value. `setType` uppercases the subtype (`DefaultAbilityComponent.java:189-204`) |
| compiled mask, mask string | zapper mask (see b018) | null, "" | Whether this term applies to the player at all |
| trigger definition | ritual text | "" | Used only when location is TRIGGER |
| ability ID | owning skill or spell | "" | The key in the component map |

### 7. The text grammar and its loader

Each entry is `ID=term term ...`. A term is `(LOC:FATE:AMOUNT:TYPE(SUB):MASK)`, or `(trigger:RITUAL:MASK)` for a trigger term. Terms are joined by `&&` or `||`. A final `"text"` is a description. `CMAbleComps.getAbilityComponentDesc` returns that text in place of the generated description (`CMAbleComps.java:647-648`).

`addAbilityComponent` (`CMAbleComps.java:657-851`) parses the grammar:

- Connector prefixes are read at 683-692. A quoted message ends the list (694-709).
- The term's parentheses are matched, with nesting allowed (717-737).
- DISPOSITION is matched by enum name. An empty disposition keeps INVENTORY, and an unknown one is an error (739-758).
- FATE is `kept` or `consumed` (default consumed, 760-777). AMOUNT is an integer (779-793).
- TYPE is tried as a resource code (`findResourceCode`), then as a material (`findMaterialCode`), and otherwise as a name, uppercased (795-824). A parenthesised suffix on a resource or material is its subtype (805-813).
- MASK is the rest of the term (829-831).
- Terms with a ritual are indexed in `abilitiesWithCompsWithTriggers` (833-839).
- Any malformed term produces a text error, and the whole entry is refused (846-847).

`getAbilityComponentMap` (`CMAbleComps.java:856-896`) reads every extended copy of `skills/components.txt` (866-877). It skips lines beginning with `#`, `;` or `:` (885), sends the rest to `addAbilityComponent`, and caches the result as `COMPONENT_MAP`. A line with no `=` is a component-only social, which the same file's header permits (`components.txt:26`; `CMAbleComps.java:662`).

The file's live entries (`resources/skills/components.txt:34-38`):

```
BANDAGING=(inventory:consumed:1:CLOTH:)||(held:consumed:5:DUST:-race +elf)||(inventory:consumed:1:A STRIP OF COTTON:)||(inventory:consumed:1:A STRIP OF WOOL:)
```

This reads as one cloth, or five dust held (only for elves, by the mask `-race +elf`), or a strip of cotton, or a strip of wool. The four thief lines below it require a prop: rune tile, stone or cube; a deck of tarot cards; coffee; a crystal ball.

Builders create and change components with `Create.java:1080-1102`, `Modify.java:2124-2145` and `Destroy.java:897-904`. Each one writes an entry through `alterAbilityComponentFile` (`CMAbleComps.java:1027-1122`), which replaces the existing `ID=` line in the file or appends a new one to the last file (1110-1117), and then clears `COMPONENT_MAP` (1119).

### 8. Checking requirements: componentCheck

`componentCheck(mob, req, mithrilOK)` (`CMAbleComps.java:230-314`) walks the terms in order:

1. A MESSAGE term ends the list (246-247).
2. A true value followed by an OR term returns the items found so far (249-250). A false value followed by an AND term returns null (251-252).
3. A term whose zapper mask does not apply to the mob is skipped, and the running value becomes false (254-260). The comment there says the term is treated as if it were not present. Problems item 3 describes what the code does with it.
4. A TRIGGER term asks whether the mob's ritual completed recently (265-275). A non-player satisfies every item term automatically (277-281).
5. Items are searched in the mob's inventory, except for ONGROUND terms, and in the room for ONGROUND and NEARBY terms (283-304).
6. A failed AND term after the first term returns null immediately (305-306). Otherwise the running value is true when the remaining amount is zero or less (307-309).
7. The result lists the matched items. After each term's items comes a Boolean holding that term's consumed flag (200-207, 309). A null means failure (311-313).

`IsItemComponent` (`CMAbleComps.java:97-209`) tests one item against one term:

- **Location.** INVENTORY needs the item carried and not worn (153). HELD needs the wielded/held slot (161). WORN needs the item worn (165). HAVE needs it carried (157). NEARBY needs it visible (170). ONGROUND needs it in the room and visible (174). The outermost container is what is tested (147-149), so an item inside a carried bag counts as carried.
- **Type.** RESOURCE matches the exact resource code (113-114) or the liquid type of a drink (118-124). MATERIAL matches the material mask (131, 140). Mithril satisfies a metal material only when `mithrilOK` is true (64-68). STRING matches the item name with `CMLib.english().containsString` (107).
- **Exclusions.** Items on fire or enchanted cannot satisfy RESOURCE or MATERIAL terms (178-180).
- **Amounts.** For STRING terms the amount is an item count (188). For weight terms the amount is a weight. If the item is heavier than what is still needed, `CMLib.materials().splitBundle` returns the portion that is needed (190-199).

`mithrilOK` is true for crafts (`CraftingSkill.java:2268`) and false for spells (`StdAbility.java:2097`). Nothing in `componentCheck` prevents one item from matching two different terms (see Problems).

### 9. Consumption and description

- **Consumption.** `destroyAbilityComponents` (`CMAbleComps.java:906-1013`) destroys each item in a group whose trailing Boolean is true. It collects the destroyed items' effects and behaviours as lost properties (934-937) and records the median material of the destroyed set (957-961). The return value is a `DeadResourceRecord` with lost value, lost weight, resource code and subtype.
- **When consumption happens.** For spells and other abilities it happens at check time, inside `checkComponents` (`StdAbility.java:2097-2107`; `CommonSkill.java:934` calls the same check). For crafts it happens after `super.invoke` succeeds and before the item is created (`Fletching.java:453-466`).
- **Description.** `getAbilityComponentDesc` (`CMAbleComps.java:536-654`) builds phrases: "N pounds of X", "a X held", "X worn or wielded", "X nearby", "X on the ground" (597-615). A term whose mask does not match the viewer is left out (545-548). With no mob, which is the builder view, the mask text is shown (549-553).
- **Messages.** "The requirements to use this @x1 are: @x2." (`StdAbility.java:2100`). "You lack the necessary materials to @x1, the requirements are: @x2." (`CraftingSkill.java:2298`). The list of requirements is the description above.

### 10. Trigger (ritual) components

A trigger term is `(trigger:RITUAL:MASK)`. RITUAL is a list of steps joined by `&` (and) or `|` (or). `DefaultTriggerer.addTrigger` (`Common/DefaultTriggerer.java:244-290`) uppercases the text, splits it on those two characters, and parses each step into a `TriggerCode` (`Common/interfaces/Triggerer.java:57`). The codes are SAY, TIME, PUTTHING, BURNMATERIAL, BURNTHING, EAT, DRINK, INROOM, RIDING, CAST, EMOTE, PUTVALUE, PUTMATERIAL, BURNVALUE, SITTING, STANDING, SLEEPING, READING, RANDOM, CHECK, WAIT, YOUSAY, OTHERSAY, ALLSAY, SOCIAL, INCLUDE and TIMEOUT. Parsed rituals are cached in a static map keyed by the text (`DefaultTriggerer.java:38`, 247-249).

The flow is as follows:

- **Player.** Each message in which the mob is the source is passed to `handleAbilityComponentTriggers` (the block opens at `StdMOB.java:3484`, the call is at 3700, both in `executeMsg`; the handler is `CMAbleComps.java:1573-1610`). When the last step completes, `DefaultTriggerer` records the key as completed (`DefaultTriggerer.java:1727`). `handleAbilityComponentTriggers` then queues `ability.invoke` as a trailer runnable (`CMAbleComps.java:1597-1607`).
- **Check.** `componentCheck` calls `wasCompletedRecently(mob, abilityID)` (`CMAbleComps.java:268`). That returns true once and removes the completion (`DefaultTriggerer.java:1837-1848`).
- **Non-player.** When a non-player fails a check, `StdAbility.checkComponents` calls `startAbilityComponentTrigger` (`StdAbility.java:2104`; `CMAbleComps.java:1495-1519`). `tickAbilityComponentTriggers` (`CMAbleComps.java:1522-1570`, called from `StdMOB.java:4335`) performs one step per tick and finishes waiting steps.
- **Per-mob state.** A mob carries one triggerer. It is `NonTriggerer` when no ability has trigger terms (`CMAbleComps.java:1423-1438`). Otherwise `confirmAbilityComponentTriggers` builds a `DefaultTriggerer` holding every trigger whose mask applies (1452-1484).

The header comment of `components.txt` gives an example at line 32 (not active in the data): `SPELL_SLEEP=(held:consumed:5:SAND:-class +wizard)&&(trigger:say you are feeling sleepy&social+ GOODNIGHT <T-NAME>:-class +wizard)`. The engine's multi-player features (ASSIST, INCLUDE, `addAssistingTriggerer` at `CMAbleComps.java:1441-1450`) are not needed in single-player use.

### 11. The craft path and duration

`CraftingSkill.getAbilityComponents` (`CraftingSkill.java:2260-2302`) handles the MATERIALS_REQUIRED column:

- An integer (for example `6` in fletching) is not a component name. The lookup finds nothing, no component list is created, and the craft uses the integer as a raw amount (`Fletching.java:412`).
- A value beginning with `(` is parsed as an inline component definition by `getNonStandardComponentRequirements` (2230-2245), using `addAbilityComponent("ID=" + text)`.
- A named value is looked up in the component map (2249-2250).

The inline form is common in data. Smelting's 48 rows are all multi-term, for example `bronze`: `(onground:consumed:1:TIN:)&&(onground:consumed:1:COPPER:)`, and 13 of the 17 textile rows use two terms or more.

Craft duration (`CommonSkill.getDuration`, `CommonSkill.java:346-362`) starts from the recipe's build ticks. The code then computes:

```
pct   = ((mobLevel - itemLevel) / LASTPLAYERLEVEL) * 0.5
ticks = ticks - round(ticks * pct)                 (never below the minimum)
ticks = ticks - round(ticks * getXTIMELevel(mob) * 0.05)
```

Each `CMProps` tick is 4000 ms by default (`core/CMProps.java:681`), so a 20-tick fletching recipe takes 80 seconds before level and quickness adjustments. Fletching's minimum is 4 ticks (`Fletching.java:472`).

### 12. Common-skill slot limits

`AbilityLimits` (`AbilityComponents.java:334-426`) carries four counts: common skills, crafting skills, non-crafting skills and language skills, plus a specific-skill slot.

- `getSpecialSkillLimit` (`CMAbleComps.java:1125-1289`) takes the maxima from the character class. A class maximum of 0 means unlimited (`Integer.MAX_VALUE`, 1243-1258). Player and account bonuses are added (1260-1287).
- `getSpecialSkillRemainders` (1332-1390) subtracts learned skills. A skill is free if it is one of the race's cultural abilities or if any class grants it (1343-1386).
- `CommonSkill.canBeLearnedBy` (`CommonSkill.java:751-788`) refuses when the remaining common or crafting count is 0. The messages are "You have learned the maximum @x1 crafting skills, and may not learn any more." and similar (767-783). `teach` reports the remaining count (790-812).

### 13. Crafted-item brands

`createCraftingBrand` writes "This is the work of <maker>." (`CMAbleParms.java:218-227`), using "an anonymous craftsman" when there is no maker. `getCraftingBrand` (188-215) recovers the name from the item's secret identity with a pattern built from the same translated sentence. A `Copyright` effect's text takes precedence (192-194). The brand is used by familiarity, forgery and recollecting code, which was not read.

### 14. Non-crafting abilities

`StdAbility.checkComponents` (`StdAbility.java:2088-2110`) skips the check when the caller has the staff COMPONENTS security flag (2090). Traps use the same functions: `GenTrap.java:214-227` calls `componentCheck` and `makeComponents(null, ...)`, and `GenTrap.java:216` calls `destroyAbilityComponents`. Each caller passes its own `mithrilOK` value.

## Content worth mining

### Recipe catalogue

Row counts are data lines with at least one tab, and ranges come from the ITEM_LEVEL column. "Columns" is the number of format columns in the class's `getRecipeFormat`.

| Skill class | Recipe file | Rows | Level range | Columns | Notes |
|---|---|---|---|---|---|
| Painting | `painting.txt` | 910 | 1-87 | 9 | Largest file (57 KB) |
| Fletching | `fletching.txt` | 199 | 1-92 | 13 | 138 numeric amounts, 61 inline |
| Carpentry | `carpentry.txt` | 202 | 1-91 | 11 | Numeric amounts |
| ScrollScribing | `scribing.txt` | 238 | none | 2 | Spell ID to resource |
| Thief_MakeBomb | `bombs.txt` | 117 | 1-93 | 8 | Bomb trap IDs |
| JewelMaking | `jewelmaking.txt` | 176 | 1-90 | 11 | Numeric amounts |
| Blacksmithing | `blacksmith.txt` | 149 | 1-91 | 9 | Multi-use columns |
| ScrimShaw | `scrimshaw.txt` | 149 | 1-91 | 9 | |
| Armorsmithing | `armorsmith.txt` | 185 | 1-30 | 11 | |
| Weaponsmithing | `weaponsmith.txt` | 135 | 0-30 | 14 | Level-0 entries exist |
| Alchemy | `alchemy.txt` | 129 | none | 3 | One row per spell |
| Cooking | `recipes.txt` | 56 | 1-91 | 18 | Widest format; repeating resource and keyword pairs |
| Embellishing | `embellishing.txt` | 84 | 1-30 | 7 | All 84 inline; 13 multi-term |
| Sculpting | `sculpting.txt` | 108 | 1-90 | 10 | |
| Pottery | `pottery.txt` | 107 | 1-91 | 9 | |
| Smelting | `smelting.txt` | 48 | 1-90 | 9 | All 48 inline and multi-term |
| StaffMaking | `staffmaking.txt` | 105 | 1-90 | 13 | 32 inline |
| WandMaking | `wandmaking.txt` | 84 | 1-90 | 11 | 32 inline |
| Tailoring | `tailor.txt` | 121 | 0-30 | 11 | Level-0 entries exist |
| GlassBlowing | `glassblowing.txt` | 88 | 1-90 | 9 | |
| PaperMaking | `papermaking.txt` | 73 | 1-90 | 10 | 21 inline |
| Boatwright | `boatwright.txt` | 67 | 1-88 | 11 | Hulls with locale masks |
| Wainwrighting | `wainwright.txt` | 55 | 1-88 | 12 | |
| InstrumentMaking | `instruments.txt` | 79 | 1-45 | 10 | |
| Textiling | `textiling.txt` | 17 | 1-30 | 9 | 13 multi-term |
| ClanCrafting | `clancraft.txt` | 46 | 1-15 | 15 | Clan-item columns such as CLAN_ITEM_CODENUMBER |
| Chant_EndowGemstones | `endowgemstones.txt` | 80 | 1-90 | 5 | Prop_WearAdjuster arguments |
| Thief_Trap | `traps.txt` | 41 | 1-29 | 8 | Trap IDs |
| Torturesmithing | `torturesmith.txt` | 13 | 15-25 | 11 | |
| Herbalism | `herbalism.txt` | 36 | 1-25 | 8 | |
| Mycology | `mycology.txt` | 43 | none | 4 | Mushroom name, frequency, value |
| Decorating | `decorations.txt` | 14 | 1-1 | 7 | Display templates |
| SmokeRings | `smokerings.txt` | 10 | 1-1 | 4 | |
| BodyPiercing | `bodypierce.txt` | 6 | 1-25 | 6 | |
| Costuming | `costume.txt` | 0 | none | 11 | File is empty; so is `mastercostume.txt` |
| Herbology | `herbology.txt` | 31 lines | none | 1 | One herb per line (`tabs=false`) |

Of the 81 classes that declare `getRecipeFilename`, 72 return a literal `.txt` or `.cmare` name. The table covers only the classes with their own `getRecipeFormat` that were measured. Taxidermy uses its own blank-line-separated blocks (`Abilities/Common/Taxidermy.java:128-150`, partly read), and `CraftingSkill` returns an empty format by default (`CraftingSkill.java:2305`).

### Column editors

The 102 editor classes in `Libraries/editors/` break down by value type:

| Editor type (`ParmType`) | Editors | Examples |
|---|---|---|
| CHOICES | 33 | ITEM_CLASS_ID, RESOURCE_NAME, RIDE_BASIS, STATUE, SMOKE_FLAG, DECORATION_FLAG |
| NUMBER | 21 | ITEM_LEVEL, BUILD_TIME_TICKS, ITEM_BASE_VALUE, BASE_DAMAGE, CONTAINER_CAPACITY |
| STRING | 16 | ITEM_NAME, ACTIVE_VERB, AMMO_TYPE, DISPLAY_MASK, COLOR_TERM |
| SPECIAL | 16 | MATERIALS_REQUIRED, CODED_SPELL_LIST, CODED_WEAR_LOCATION, WEAR_LOC, RIDE_OVERRIDE_STRS |
| STRINGORNULL | 11 | AVOID_MSG, DAMAGE_MSG, ADJUSTER_ARGS, READABLE_TEXT, ZAPPERMASK |
| NUMBERORNULL | 2 | EXPERTISENUM, WEAPON_HANDS_REQUIRED |
| NUMBER_PAIR | 1 | MIN_MAX_RANGE |
| MULTICHOICES | 1 | CONTAINER_TYPE |
| ONEWORD | 1 | POSE_NAME |

The `ParmType` values are in `AbilityParameters.java:55-66`. The counts come from each editor's `super(...)` call.

### Example rows worth studying

- **Smelting alloys** (`smelting.txt`): bronze is one tin and one copper. Terne is four lead and one tin. Pewter is one each of tin, copper and lead. All terms are `onground:consumed`.
- **Textile composites** (`textiling.txt`): "bolt of copper lame" needs `COTTON(COTTON)` plus copper, with the resource name COTTON and subtype COPPER LAME.
- **Hull weights** (`boatwright.txt`): the level-5 "small % jon boat" needs 148 pounds of metal, and the level-25 jon boat needs 340. The umiak uses hide and the coracle uses cloth. The boat rows also attach a locale mask that refuses ocean use, with its message.
- **Alchemy** (`alchemy.txt`): each spell maps to a resource and a potion type. For example, `Spell_IronGrip` maps to IRON and `Spell_InsatiableThirst` to SALT WATER, both as POTION.
- **Painting progression** (`painting.txt`): finger-painting (level 1, 12 ticks), sketch (4, 15 ticks), drawing (7, 18 ticks) and painting (10, 21 ticks), each with its own verb.
- **Display templates** (`decorations.txt`, `embellishing.txt`): `hanging @x1`, `mounting @x1` and `sticking up @x1`, and `@x1 with @x2` for two-part names. The `@x` placeholders are template markers; their substitution was not traced.
- **Thief bombs and traps** (`bombs.txt`, `traps.txt`): "Tiny water bomb" maps to `Bomb_Water`, and "noisy trap" maps to `Trap_Noise` at level 1. Both formats have TRIGGER_MSG, DAMAGE_MSG and AVOID_MSG columns, which the sample rows leave blank.
- **Components** (`components.txt:34-38`): the BANDAGING alternatives, and four thief lines that require a prop (rune pieces, tarot cards, coffee, a crystal ball).

## Notable design ideas

- **One declaration, four uses.** The format string drives the file parser, the in-game editor, the web editor and the learn-from-item generator (`CMAbleParms.java:302`, `974`, `379`; `CraftingSkill.java:1952`). Adding a column means adding one editor class.
- **Columns that change type by row.** A multi-use column such as `STATUE||RIDE_BASIS||...` lets one column hold a container type for a box and a ride basis for a boat, chosen by the row's class (`CMAbleParms.java:721-734`).
- **A requirement language with a voice.** Terms are alternatives or conjunctions, and a trailing quoted string is the sentence players see (`CMAbleComps.java:647-648`). A builder can replace the generated list with a sentence, which is shown verbatim.
- **Requirements that depend on who you are.** A term's zapper mask decides whether the term exists for this viewer. The description hides it (`CMAbleComps.java:545-548`), so a player sees only the alternatives that apply to them. The BANDAGING line gives elves an alternative with dust.
- **Rituals as ingredients.** A trigger term is satisfied by a sequence of spoken and social steps, and the engine tracks progress per mob (`DefaultTriggerer.java:1727`, `1837-1848`). NPCs can perform the same ritual on their own (`CMAbleComps.java:1495-1570`).
- **Materials by weight, with bundles.** Amounts for raw materials are pounds, and the code splits a stack or bundle when only part of it is needed (`CMAbleComps.java:190-199`). A requirement can be spread across several stacks.
- **Items that cannot be spent.** Burning or enchanted items are refused as materials (`CMAbleComps.java:178-180`).
- **Mithril as a metal substitute, for crafts only.** The `mithrilOK` flag lets mithril stand in for metal in crafting but not in spells (`CMAbleComps.java:64-68`; `CraftingSkill.java:2268`; `StdAbility.java:2097`).
- **Learning by taking things apart.** A recipe can be written into a book by deconstructing an item, and the material amount is computed from the item's weight (`CraftingSkill.java:1932-1962`; `EditorMaterialsRequired.java:132-166`).
- **Recipes generated from prototypes.** Ship and wagon recipes are derived from item XML: build time is weight divided by ten, and material is weight (`GenWrightSkill.java:249-258`).
- **Crafted items remember their maker.** The brand sentence (`CMAbleParms.java:218-227`) makes the crafter's name part of the item, and a copyright effect can override it (192-194).
- **Level 0 as a free tier.** A level-0 recipe has no level gate (`Fletching.java:392`). Level-0 rows are skipped when a recipe is chosen by level (`CraftingSkill.java:1227-1258`) and when the level range is computed (1260-1273).

## Problems and cruft

Items marked "traced" were read from the code and not run. Each one cites the lines that produce the behaviour.

1. **A bad inline component fails open (traced).** If MATERIALS_REQUIRED is not an integer and does not resolve to a component, `getNonStandardComponentRequirements` returns null (`CraftingSkill.java:2230-2258`). `getAbilityComponents` then returns an empty list and the craft proceeds with no material cost (2260-2301). A malformed inline string logs `Error parsing custom component` (2240-2241). A missing named component logs nothing. The editor does not catch the malformed string, because `EditorMaterialsRequired.confirmValue` always returns true (`EditorMaterialsRequired.java:68-71`).

2. **Mixed AND and OR terms do not backtrack (traced).** For `(A)&&(B)||(C)`, if B is missing, `componentCheck` returns null at line 305-306 before it evaluates C (B is an AND term after the first term). If A is missing, the AND after A returns null at 251-252. The OR alternative C is therefore never considered after an AND group. `components.txt:5` documents this mixed form. The example at line 32 uses AND only. No shipped entry mixes connectors.

3. **A masked term inside an AND chain fails the whole chain (traced).** A term whose mask does not apply sets the running value to false and continues (`CMAbleComps.java:254-258`). If another AND term follows, line 251-252 returns null. The comment at 254 says such a term should be treated as if it were not there. This contradicts the comment whenever the masked term is followed by another AND term.

4. **One item can satisfy two terms (documented and traced).** Nothing in `componentCheck` or `IsItemComponent` excludes an item that an earlier term already matched. `components.txt:19` documents the consequence ("the same item may be found multiple times"). `destroyAbilityComponents` then calls destroy on the same item twice (`CMAbleComps.java:931-944`).

5. **Trigger completion is consumed when checked, even if the check then fails (traced).** `componentCheck` calls `wasCompletedRecently` (`CMAbleComps.java:268`), which removes the completion (`DefaultTriggerer.java:1844-1846`). A later failing term (for example an item missing after a ritual term in an AND group) returns null, but the completion is gone and the player must repeat the ritual.

6. **Consumption happens before the outcome is known.** Spell components are destroyed in `checkComponents` (`StdAbility.java:2097-2107`), before the ability's effect. The file does not show a refund path. Crafts destroy at `Fletching.java:463`, after `super.invoke` and before the item object is created (465).

7. **Every recipe save rewrites the file (traced).** `resaveRecipeFile` compares `file.text()` and `saveBuf` with `equals`, and both are `StringBuffer`s, so the comparison is identity and is always false (`CMAbleParms.java:1111`). The file is rewritten and the caches cleared on every save, even when nothing changed.

8. **Blank lines cause a parse error in the editor and are ignored at runtime (traced).** See section 2. The two readers are not kept in step.

9. **The misc-text validator accepts any placeholder word.** `matchMTLiteral` returns true for any placeholder whose name is not an editor ID. The branch for `ParmType` names under it is an empty `TODO: finish` (`CMAbleParms.java:1870-1875`). `Tests/Validmisctext.java:75-81` relies on `[NAME]` accepting "Bob". No caller outside the test file was found.

10. **Trigger text cannot contain `:`, `&` or `|` (traced).** The component grammar splits on colons (`CMAbleComps.java:740, 761`), and the ritual parser splits on `&` and `|` (`DefaultTriggerer.java:251-258`). A SAY phrase containing one of these characters is cut.

11. **Offset-0 entry edge in `alterAbilityComponentFile` (minor).** The code tests `delFromHere>0` (`CMAbleComps.java:1097`), so a matching entry at offset 0 that is the last matching line is not removed. In practice `components.txt` starts with comments.

12. **Dead branch in GenWrightSkill (traced).** `getWrightables` never returns null, so the `else if(F.exists()) F.delete()` branch at `GenWrightSkill.java:246-263` cannot run, and a stale generated file is never removed.

13. **Unbounded static cache.** `DefaultTriggerer.ritualCache` (`DefaultTriggerer.java:38`) keeps every ritual text ever parsed for the life of the process.

14. **Smaller cruft.** `isAbilityComponentTriggerCompletedRecently` (`CMAbleComps.java:1486-1492`) has no caller in the tree. `CMAbleComps` imports `CMSecurity.DbgFlag` (line 10), and the file never uses it. `calculateRecipeCols` (`CMAbleParms.java:907-917`) never terminates for a format with 37 or more data columns, because each column width stops at 1 while the total is still above the limit. The widest shipped format has 18 columns.

## Takeaways for a Python SUD

- **Recipes as typed data, not text.** Keep the idea of one schema per craft (ordered field list with a type and a validator per field), but store the recipes as TOML or JSON, or as a TSV loaded through a dataclass. Validate at load time and refuse bad rows, which avoids the fail-open problem in item 1 above.

  ```python
  @dataclass(frozen=True)
  class Recipe:
      name: str             # "crude % atlatl"; "%" becomes the material name
      level: int            # 0 means no gate
      ticks: int            # base build time
      needs: Requirement | int   # raw amount, or a structured requirement
      value: int
      make: str             # item template id
  ```

- **Requirements as structured data with an explicit shape.** Represent a requirement as "any of" a list of "all of" groups, so that no connector mixing is needed and the backtracking defect in item 2 cannot occur:

  ```python
  @dataclass(frozen=True)
  class Need:
      where: str                 # "carried", "held", "worn", "room", "ground", "ritual"
      kind: str                  # "weight" (pounds), "count", "name" or "ritual"
      key: str                   # "TIN", "COTTON", "crystal ball", or a ritual id
      amount: float = 1
      consume: bool = True
      applies: Callable[[Player], bool] = lambda p: True   # replaces the zapper mask

  Requirement = list[list[Need]]     # any of the groups; each group must be fully met
  ```

  Track which items are already claimed while checking, which avoids item 4.

- **Keep the good parts.** The ones worth borrowing are the weight-based raw materials with partial consumption across stacks, the description generated from the requirement with a human sentence as an override, the level-0 free tier, the `%` name template, the duration formula (`CommonSkill.java:346-362`, which is simple enough to copy), and learning recipes by taking an item apart.

- **Simplify the ritual system.** For single-player, a ritual is a short list of phrases the player types (for example `say` with an exact phrase, or a timed wait), checked against the player's own messages. Drop ASSIST, INCLUDE, socials and the NPC auto-performance unless NPC casting is wanted. Keep a per-player, per-recipe "ritual done" flag, cleared when it is used, and clear it on failure too, so that item 5 cannot happen.

- **Drop.** Web editors, the in-game text grammar and file rewriting, `components.txt` as a live editing target, clan and copyright brand logic, multiplayer masks, the `.cmare` XML path (unless the SUD has large item prototypes), and the editor's `...` repeat marker.

- **Single-player notes.** Recipes need no other player, so the clan items and assist-triggers do not apply. Mithril-for-metal substitution is a useful pattern if the SUD ever has material classes. Crafted-by brands can record the player's own name for flavour.

## Coverage

- **Read in full:** `Libraries/CMAbleParms.java` (1-2073), `Libraries/CMAbleComps.java` (1-1612), `Libraries/interfaces/AbilityParameters.java` (1-500), `Libraries/interfaces/AbilityComponents.java` (1-427), `Common/interfaces/AbilityComponent.java` (1-232), `Common/DefaultAbilityComponent.java` (1-237).
- **Read in part, outside the bucket:** `Libraries/CoffeeUtensils.java` (163-300); `Abilities/Common/CraftingSkill.java` (204-235, 258-299, 1227-1273, 1409-1440, 1497-1521, 1618-1650, 1928-1962, 2230-2440); `Abilities/Common/CommonSkill.java` (293-362, 751-812); `Abilities/Common/EnhancedCraftingSkill.java` (347-370); `Abilities/Common/GenWrightSkill.java` (113-115, 191-264); `Abilities/Common/Fletching.java` (79-102, 360-472 in part); `Abilities/Common/Taxidermy.java` (128-150); `Abilities/StdAbility.java` (2067, 2088-2110); `MOBS/StdMOB.java` (3700, 4335); `Common/DefaultTriggerer.java` (38, 244-290, 1727, 1774, 1837-1851); `Common/interfaces/Triggerer.java` (57, 114); `Libraries/editors/EditorMaterialsRequired.java` (68-71, 132-166); `Libraries/editors/EditorDecorationFlag.java` (whole); `Libraries/editors/EditorNA.java` (whole); `core/interfaces/RecipeDriven.java` (35-40); `Commands/Create.java`, `Modify.java`, `Destroy.java` and `ListCmd.java` (component sections); `Libraries/EnglishParser.java` (1591-1650, 1346-1368).
- **Grepped or surveyed:** every `getRecipeFormat` and `getRecipeFilename` in the tree (49 and 81 declarations); the call sites of `deconstructRecipeInto`; the `super(...)` calls of all 102 editor classes; every caller of `AbilityComponents` and `AbilityParameters`; all of `resources/skills/*.txt` for row, column, blank-line and MATERIALS_REQUIRED shape; `resources/skills/components.txt` in full.
- **Not read:** the bodies of most editors (only the listed ones), `MaterialLibrary` (`splitBundle`, `unbundle`, `destroyResources`, `findResourceCode`), `Triggerer` step semantics beyond the enum, `CMGenEditor.modifyComponents` beyond the entry, the rest of `Taxidermy`, the web macros other than `AbilityRecipeData`, and the `.cmare` XML beyond the first item.
