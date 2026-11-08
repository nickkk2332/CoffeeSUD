# Crafting recipes and skill data, part 1

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `c04` (content: Crafting recipes and skill data, part 1) · 29 files · 1,275,317 bytes of data.

## Summary

Most of this bucket is the data behind CoffeeMud's crafting trades: tab-separated recipe tables for smithing, carpentry, boatwrighting, alchemy, cooking-style baking, dyeing, bomb-making and clan items, plus building-skill tables for rooms, doors and cave excavation. Two `.cmare` files are not tables at all: they are serialized item (and one multi-room castle) exports that the engine reads back as recipe sources for caravans and clan castles. The rest are rule files: expertise (skill-enhancement) definitions, the class skill-grant list, component requirements, instance-area types, a 256-colour table, and small lookup tables for trapping, aging, body piercing and decoration verbs. Each recipe row is interpreted through column indexes that every skill class declares, so the same parser serves very different products. For a single-player Python game, the strongest borrowable parts are the level-gated, expertise-scaled recipe model and the rule that an item's own stats (weight, level, value) can serve as its build recipe.

## Key files and types

Paths are relative to the CoffeeMUD folder. "Lines" is the line count of the file.

| File or class | Role | Lines |
|---|---|---|
| `resources/skills/blacksmith.txt` | Metal household goods, statues, stoves, beds; levels 1-91 | 149 |
| `resources/skills/carpentry.txt` | Woodwork: containers, weapons, chairs, shields, light sources; levels 1-91 | 202 |
| `resources/skills/armorsmith.txt` | Metal armour and shields; levels 1-30 | 185 |
| `resources/skills/boatwright.txt` | 67 boats and canoes; levels 1-88 | 67 |
| `resources/skills/cobbler.txt` | Footwear and hose; levels 0-18 | 19 |
| `resources/skills/construction.txt` | Building-skill rooms, doors, walls, roofs, stairs | 69 |
| `resources/skills/excavation.txt` | Cave-digging rooms, boulder doors, mazes; levels 1-90 | 32 |
| `resources/skills/clancraft.txt` | Clan banners, tools, uniforms, signposts (clan system) | 46 |
| `resources/skills/embellishing.txt` | Clothing details (collars, sleeves, skirts); levels 1-30 | 84 |
| `resources/skills/bake.txt` | Baked goods and pies (cooking family) | 47 |
| `resources/skills/bombs.txt` | Thief bombs: 9 sizes x 13 kinds | 117 |
| `resources/skills/alchemy.txt` | Spell and prayer potions/powders from reagents | 129 |
| `resources/skills/dyeing.txt` | Dye recipes, one per colour name; levels 1-91 | 451 |
| `resources/skills/colors.txt` | 256-colour master table (names, hex, cube codes) | 233 |
| `resources/skills/expertises.txt` | Expertise (skill-enhancement) definitions | 720 |
| `resources/skills/allqualifylist.txt` | Which skills each class is granted, and at what level | 121 |
| `resources/skills/components.txt` | Component requirement syntax and rules | 38 |
| `resources/skills/endowiounstone.txt` | Ioun-stone recipes (249 rows) | 249 |
| `resources/skills/endowgemstones.txt` | Gemstone bonus recipes (80 rows) | 85 |
| `resources/skills/areainstancetypes.txt` | Instance-area (dungeon-type) definitions | 157 |
| `resources/skills/atrapping.txt` | Animal-trapping loot table (weighted) | 63 |
| `resources/skills/age.txt` | Race-to-race aging rules | 10 |
| `resources/skills/cagebuilding.txt` | Cage and cage-cart recipes | 6 |
| `resources/skills/bodypierce.txt` | Body-piercing sites by wear location | 7 |
| `resources/skills/decorations.txt` | Decoration verbs (hang, mount, lay, ...) | 14 |
| `resources/skills/drugs.txt` | Cutting recipes for drug and poison items | 8 |
| `resources/skills/costume.txt` | Costume recipe file, empty (0 bytes) | 0 |
| `resources/skills/clancastles.cmare` | One clan castle, three rooms (XML) | 4 |
| `resources/skills/caravanbuilding.cmare` | 94 caravan prototypes (XML) | 386 |
| `core/interfaces/RecipeDriven.java` | Standard recipe column constants `RCP_FINALNAME`=0, `RCP_LEVEL`=1 | 77 |
| `Libraries/CoffeeUtensils.java` | `loadRecipeList` parser; `addExtRecipes` merges learned recipes | 3876 |
| `Abilities/Common/CraftingSkill.java` | `loadRecipes`, `matchingRecipes`, weight and wood rules | 2459 |
| `Abilities/Common/CommonSkill.java` | `loadList`/`loadRecipes` twin, `xlevel`, `getDuration` | 1016 |
| `Abilities/Common/GenCraftSkill.java` | Data-driven craft skill (file name and format as stats) | 1031 |
| `Abilities/Common/BuildingSkill.java` | Building codes and flags for rooms and exits | 2035 |
| `Abilities/Common/Armorsmithing.java` | Worked example of a full build (`invoke`, lines 277-553) | 600 |
| `Libraries/CMAbleParms.java` | Recipe table editor, column parser, wear-location parser | 2073 |
| `Abilities/Common/Shipwright.java` | Turns a `.cmare` into a generated recipe file | 1035 |
| `Abilities/Common/CaravanBuilding.java` | Caravan craft: wooden land vehicles | 106 |
| `Abilities/Common/ClanCrafting.java` | Clan items; castles appended as recipes | 693 |
| `Libraries/ColumbiaUniv.java` | Expertise file parser and lookups | 1438 |
| `Libraries/interfaces/ExpertiseLibrary.java` | `XType` enum of expertise effects (lines 421-435) | 619 |
| `Abilities/StdAbility.java` | Expertise accessors (`getXLEVELLevel` etc., lines 444-472) | 3018 |
| `Libraries/Brown.java` | Colour table loader; unused dye-recipe generator | 955 |
| `Abilities/Misc/Age.java` | Parses and applies `age.txt` | 1563 |
| `Abilities/Misc/InstanceArea.java` | Parses `areainstancetypes.txt` | 2669 |
| `Libraries/CMAbleMap.java` | Parses `allqualifylist.txt` | 2912 |
| `Libraries/CMAbleComps.java` | Parses `components.txt` (`getAbilityComponentMap`, line 856) | 1612 |
| `Libraries/CoffeeMaker.java` | Item XML: `getItemXML`, `addItemsFromXML`, `getPhyStatsStr` | 6719 |
| `Items/interfaces/Boardable.java` | Rename placeholders for boats and ships (lines 122-129) | 130 |
| `Abilities/Common/AnimalTrapping.java` | Weighted pick from `atrapping.txt` | 305 |
| `Abilities/Common/Cooking.java` | Format and column constants for bake and drug recipes | 1573 |
| `Abilities/Common/Decorating.java` | Decoration verbs and their XLEVEL gate | 445 |
| `Abilities/Common/BodyPiercing.java` | Body-piercing recipe use | 395 |
| `Abilities/Druid/Chant_EndowGemstones.java` | Gemstone endowment recipe format | 391 |
| `Abilities/Druid/Chant_EndowIounStone.java` | Ioun-stone endowment; wraps a `GenCraftSkill` | 333 |
| `Abilities/Thief/Thief_MakeBomb.java` | Bomb recipe format and loader | 302 |
| `Abilities/Common/Alchemy.java` | Alchemy recipe format and file | 644 |

## How it works

### 1. Binding a skill to its table

Each craft class names its file in `getRecipeFilename()` and its columns in `getRecipeFormat()`. Examples: `Alchemy.java:78-81` (format `SPELL_ID`, `RESOURCE_NAME`, `POTION_POWDER`) and `Alchemy.java:149-151` (file `alchemy.txt`); `Armorsmithing.java:77-83` and `:130`; `Blacksmithing.java:78-84` and `:129`; `Carpentry.java:79-86` and `:117`; `Boatwright.java:77-83` and `:117-119`; `Embellishing.java:97-102` and `:107`; `Decorating.java:99-104`; `Cooking.java:109-125`. Some skills are wrappers. `GenCraftSkill.java:86-94` builds its format from a `FILENAME` stat (`V_FNAM`, line 71), and `Chant_EndowIounStone.java:78-84` creates one with `FILENAME=endowiounstone.txt` and a trigger string. Wrappers reuse `GenCraftSkill` this way, so a new craft can apparently be added as data plus a configured instance (inferred from `Chant_EndowIounStone.java:78-84`).

### 2. Parsing and caching

`CraftingSkill.loadRecipes(String)` (`CraftingSkill.java:204-243`) does the following:

- Caches the parsed table under the key `"PARSED_RECIPE: <file>"` (lines 206 and 240).
- Reads every "extended" copy of the file with `CMFile.getExistingExtendedFiles` (line 215), so every copy of a file with that name is appended (for example an override in another resource root; not traced).
- Parses each file with `loadList`, which is `CoffeeUtensils.loadRecipeList` (`CoffeeUtensils.java:242-300`; `CommonSkill.java:293-296`).
- Sorts rows by the level column (lines 220-233).
- Logs "Recipes not found!" if the table is empty (`CraftingSkill.java:238`), except for `GenCraftSkill`, `GenWrightSkill` and `*Costuming` IDs (lines 235-237). A similar rule applies in the older twin `CommonSkill.java:298-318`, which logs at line 314 and exempts only `GenCraftSkill` and `Costuming` IDs (lines 311-313).

Parser rules in `loadRecipeList` (`CoffeeUtensils.java:242-300`): a line beginning with `#` is a comment; with `tabs=true` a line is a record only if it contains a TAB; short rows are padded with empty strings to the longest row. Column meaning is purely positional.

Column constants: `RecipeDriven.RCP_FINALNAME` = 0 (the name) and `RCP_LEVEL` = 1 (`core/interfaces/RecipeDriven.java:35` and `:40`). Each skill then declares its own indexes. Example, `Armorsmithing.java:85-95`: `RCP_TICKS`=2, `RCP_WOOD`=3, `RCP_VALUE`=4, `RCP_CLASSTYPE`=5, `RCP_MISCTYPE`=6, `RCP_CAPACITY`=7, `RCP_ARMORDMG`=8, `RCP_CONTAINMASK`=9, `RCP_SPELL`=10.

A `%` in a name is a placeholder for the material used. `determineFinalName` (`CraftingSkill.java:258-262`) and `determineFinalResourceName` (`:245-256`) pick the sub-type, then the resource type, then the material code, and substitute it. So the row `% cup` becomes "<material> cup" at build time.

### 3. Choosing a recipe and the level gate

`matchingRecipes` (`CraftingSkill.java:1409-1500`) accepts these forms:

- `N`: exact level.
- `-N`: level up to N.
- `N+` or `N-`: level at least N.
- `lo-hi`: a level range.
- A name, optionally with `.k` to pick the k-th match (lines 1414-1430).

The gate then compares the row level with the player's crafting ceiling. In `Armorsmithing.invoke` (lines 461-462): `level<=xlevel(mob)`. `CommonSkill.xlevel` (`CommonSkill.java:645-647`) is `mob.phyStats().level() + 2*getXLEVELLevel(mob)`, that is, player level plus twice the rank of the skill's LEVEL expertise. `Decorating` uses a different rule (`Decorating.java:380-383`): row level at most adjusted level, and `getXLEVELLevel(mob) >= XLEVEL column`.

Learned recipes: `CoffeeUtensils.addExtRecipes` (`CoffeeUtensils.java:202-225`) adds rows from `Recipes` items the mob carries (and `Recipes` effects) whose common-skill ID matches, unless the item is flagged uncraftable. Items implementing `Recipes` therefore extend the table per player, not per file. `CraftingSkill.addRecipes` (`:272-277`) calls it.

### 4. Turning a row into an item (worked example: `Armorsmithing.invoke`, lines 277-553)

- Materials. `RCP_WOOD` is passed to `getAbilityComponents` (`Armorsmithing.java:478`), which accepts the component syntax (section 7). The raw amount is then reduced by `adjustWoodRequired` (`Armorsmithing.java:481-482`, `CraftingSkill.java:361-372`): `wood - round(0.05 * wood * XPCOST rank)`, never below 1 for a positive amount. The metal is drawn with `fetchFoundResourceData(..., "metal", ...)` (`Armorsmithing.java:488-490`).
- Time. `duration = getDuration(RCP_TICKS, mob, RCP_LEVEL, 6)` (`Armorsmithing.java:518`). `CommonSkill.getDuration` (`CommonSkill.java:346-363`) takes the base ticks, reduces them by `round(ticks * ((player level - item level) / LASTPLAYERLEVEL * 0.5))`, then by 5% per XTIME rank (`:357-358`), never below the minimum (6 here). Ticks are the engine's ability ticks, `TIME_TICK = 4000` ms by default (`core/CMProps.java:681`, overridable with `TICKTIME` at `:2720-2722`). So a 4000-tick row such as the Colossus in `blacksmith.txt:140` is about 4.4 hours at the default tick, before reductions.
- Item level. `level = RCP_LEVEL + (HARDNESS(material) - 6) * 3`, floor 1 (`Armorsmithing.java:534-537`). Harder metals make higher-level items from the same row.
- Weight. `setWeight(getStandardWeight(materials + component amount, ...))` (`Armorsmithing.java:532`; `CraftingSkill.java:192-199`) is `round(materials * getItemWeightMultiplier(bundling))`, plus 1 if a second resource is used, and at least 1 if materials were positive. The multiplier itself was not traced.
- Value and armour. Base value is `RCP_VALUE` (`Armorsmithing.java:533`). For armour with a non-zero `armordmg`, `armor = armordmg + (baseYield() + abilityCode() - 1)` (`:549-550`).
- Wear location. The column is parsed by `CMAbleParms.parseWearLocation` (`CMAbleParms.java:259-300`), which also sets the hardness bonus (section 6).
- Messages. Start: `"<S-NAME> start(s) smithing @x1."` and `"You are smithing @x1"` (`Armorsmithing.java:526-527`). Failure: `"You don't know how to make a '@x1'.  Try \"armorsmith list\" for a list."` (`:472`).

### 5. Column dialects

The same parser serves the following layouts. Columns are listed after name and level.

| File (skill class) | Columns after name and level |
|---|---|
| `armorsmith.txt` (`Armorsmithing`) | TICKS, MATERIALS, VALUE, CLASS, WEAR (layer-prefixed), CAPACITY, ARMOUR, CONTAINER TYPE, SPELL list |
| `blacksmith.txt` (`Blacksmithing`) | TICKS, MATERIALS, VALUE, CLASS, MISC (`LIQUID`, `EATABLES`, `LID`, `STATUE`, `TABLE`, `CHAIR`...), CAPACITY, SPELL list |
| `carpentry.txt` (`Carpentry`) | TICKS, MATERIALS, VALUE, CLASS, one of LID / RIDE / WEAPON CLASS / WEAR / SMOKE / STATUE, one of CAPACITY / HANDS / LIQUID / LIGHT / WAND USES / DICE, ARMOUR or DAMAGE, TYPE / ATTACK / SUBTYPE, SPELL list (`Carpentry.java:79-86`) |
| `boatwright.txt` (`Boatwright`) | TICKS, MATERIALS, VALUE, CLASS, RIDE BASIS, RIDE CAPACITY, CONTAINER CAPACITY, RIDE OVERRIDE strings, SPELL list (`Boatwright.java:79-82`) |
| `construction.txt`, `excavation.txt` (`BuildingSkill`) | TICKS, MATERIALS, OPTIONAL RESOURCE, FLAGS, BUILD CODE, CLASS, GRID or EXIT names, SPELL list, NOUN, BUILDER MASK, DESCRIPTION (`BuildingSkill.java:174-176`) |
| `bake.txt`, `drugs.txt` (`Cooking`) | FOOD/DRINK, SMELL or SPELL, LEVEL, then up to six RESOURCE/AMOUNT pairs (`Cooking.java:109-125`) |
| `dyeing.txt` (`Dyeing`, `PaintingSkill`) | TICKS, COLOR TERM (embedded colour codes), COLOR MASK, EXPERTISE number, KEY=VALUE parms (`PaintingSkill.java:73-79`) |
| `embellishing.txt` (`Embellishing`) | TICKS, NAME MASK, MATERIALS, ZAPPER MASK, SPELL list (`Embellishing.java:97-102`) |
| `clancraft.txt` (`ClanCrafting`) | TICKS, two RESOURCE/AMOUNT pairs, CLAN ITEM CODE, CLAN XP COST, VALUE, CLASS, AREA/WEAR/TEXT, CAPACITY, ARMOUR or POP, CONTAINER, SPELL, REQUIRED SKILL (`ClanCrafting.java:79-86`) |
| `decorations.txt` (`Decorating`) | TICKS, ACTIVE VERB, DISPLAY MASK, XLEVEL (`RCP_XLEVEL`=5), MISC (`RCP_MISC`=6) (`Decorating.java:73-79`) |
| `bodypierce.txt` (`BodyPiercing`) | Level, then body-part names (`BodyPiercing.java:90-93`) |
| `alchemy.txt` (`Alchemy`) | RESOURCE NAME, POTION or POWDER (`Alchemy.java:78-81`) |
| `bombs.txt` (`Thief_MakeBomb`) | blank, TRAP ID, ABILITY ID, three message columns (`Thief_MakeBomb.java:275-280`); the data fill only TRAP ID |
| `endowgemstones.txt` (`Chant_EndowGemstones`) | MASK (`Prop_WearAdjuster` arguments), XLEVEL, EXPERTISE id (`Chant_EndowGemstones.java:115-122`; header `endowgemstones.txt:1-5`) |
| `endowiounstone.txt` (`GenCraftSkill`) | Full GenCraftSkill layout (`GenCraftSkill.java:86-94`) |
| `cagebuilding.txt` (`CageBuilding`) | Wainwright layout: LID LOCK, CAPACITY, RIDE CAPACITY, CONTAINER TYPE, ... (`Wainwrighting.java:79-85`) |

### 6. Wear locations and layers

`CMAbleParms.parseLayers` (`CMAbleParms.java:229-256`) and `parseWearLocation` (`:259-300`) read the wear column. A prefix before `:` gives the clothing layer: `1:` is layer 1, `S` marks see-through, `M` marks multiwear, `MS`/`SM` both. Wear names are matched against `Wearable.CODES`. `||` means any of the listed locations and `&&` means all of them, and each location adds to the hardness bonus through `location_strength_points`. Data examples: `armorsmith.txt:1` uses `1:HANDS`; `armorsmith.txt:19` uses `1:TORSO&&ARMS`; `armorsmith.txt:22` uses `S1:TORSO`; `cobbler.txt:3` uses `-3:FEET` (negative layer); `cobbler.txt:8` uses `S-2:FEET&&LEGS`.

### 7. Components: the requirement language

`components.txt:1-33` documents a small language used in MATERIALS columns (boats, `boatwright.txt:6` uses `(onground:consumed:148:METAL:)`), and in whole-skill requirement lists. Each component is `(DISPOSITION:FATE:AMOUNT:ID:MASK)` or `(TRIGGER:RITUAL:MASK)`, combined with `&&` and `||`, with an optional quoted description. Dispositions are `inventory`, `have`, `held`, `worn`, `nearby`, `onground`. Fates are `consumed` or `kept`. The ID can be a material, a resource, a resource sub-type in parentheses, or an item-name match. The mask is a zapper mask (`components.txt:11-14` lists the fields). Examples at `components.txt:34-37` cover bandaging, rune casting, a tarot deck and a coffee requirement. `CMAbleComps.getAbilityComponentMap` (`CMAbleComps.java:856-880`) parses the file.

### 8. Expertises as modifiers

`expertises.txt:3-12` gives the format and flags: `[CODE]=[NAME],[LEVELS],[FLAGS],[SKILL MASK],[LIST MASK],[TRAIN MASK],[#PRACS],[#TRAINS],[#QPs],[#EXP],[#TIME]`. `@x1` numbers the stage and `@x2` gives the roman numeral in the name. A `HELP_` line gives help text and a `DATA_` line gives extra comma data. Example, `expertises.txt:24`: `ARMOROPT@x1=Armor Optimizing @x2,10,LEVEL,-SKILL +Fighter_ArmorTweaking,-MAXCLASSLEVEL +>={28+(5*@x1)},...`. This defines ten stages, each with a class-level requirement computed from the stage number.

Parsing is `ColumbiaUniv.confirmExpertiseLine` (`ColumbiaUniv.java:642`). It splits on commas (line 751), reads levels (759), splits flags on `|` (761), and stores five cost strings (775). Flags map to the `XType` enum (`ExpertiseLibrary.java:421-435`): `X1` to `X6`, `LEVEL`, `TIME`, `MAXRANGE`, `LOWCOST`, `XPCOST`, `LOWFREECOST`. Accessors are `StdAbility.java:444-472`. Consumers in the crafting code are:

- `LEVEL`: the recipe ceiling (`CommonSkill.java:647`).
- `XPCOST`: the wood reduction (`CraftingSkill.java:361-364`).
- `TIME`/XTIME: the duration reduction (`CommonSkill.java:357-358`).

`X1` to `X6` are generic slots whose meaning depends on each skill's code (`expertises.txt:12`).

Counts over the file (computed by script over all 205 definitions): flags `LEVEL` 91, `LOWCOST` 42, `X1` 28, `TIME` 20, `X4` 14, `MAXRANGE` 13, `X2` 12, `X3` 11, `XPCOST` 3, `LOWFREECOST` 1, `X5` 1, `X6` 1. There are 204 `HELP_` lines and 23 `DATA_` lines.

Stage-numbered colour expertise: `TUNING@x1=Artsy @x2,10,LEVEL,...` (`expertises.txt:78`) has ten stages. The dead dye generator looks up `"TUNING"+number` for each colour (`Brown.java:814`, section 9), and `dyeing.txt` uses expertise numbers 0 to 10 (computed).

### 9. Colour table and dye rows

`Brown.activate()` (`Libraries/Brown.java:851-915`) loads `colors.txt`. Columns used: index 0 the number, 1 the CoffeeMud name, 2 an alternate name, 4 an optional 16-colour fallback, 5 the hex value, 6 an exclude flag, 7 either a 16-colour base name (`BLACK`, `RED`, ...) or a three-digit 6x6x6 cube code. A cube code `abc` maps to `16 + 36a + 6b + c` (`Brown.java:884`). The file has 232 rows numbered 0 to 231. Sixteen rows name a base colour in field 7 and 216 carry cube codes (computed by script). The header at `colors.txt:1` lists nine labels, but every data row has eight fields.

`Brown.generateRecipes()` (`Brown.java:774-849`) is the only code that writes the dye recipes to `dyeing.txt` and `lacquering.txt` (`Brown.java:845-847`). Nothing calls it (grep finds only its definition), so `dyeing.txt` is effectively hand-maintained. Dye rows carry xterm-256 colour escapes such as `^#124` with an `ANSI256=TRUE` flag (the two six-field rows are listed under Problems and cruft).

### 10. Other mechanisms in the bucket

- Alchemy. `Alchemy.java:78-81`. `alchemy.txt` maps a spell or prayer ID to a reagent and a form. There are 129 rows: 83 `Spell_` and 46 `Prayer_`, 127 potions and 2 powders, 64 distinct reagents. All 128 distinct IDs exist as classes (checked by file name). `Spell_Blur` appears twice with different reagents.
- Ioun stones. `Chant_EndowIounStone.java:74-114`. `endowiounstone.txt` has 249 rows, levels 1-91, ticks 20-150, values 501-9000. Every row gives a `Prop_WearAdjuster` property (stat or save bonus) plus `Prop_LimitedEquip;1` with an `id=` key and a refusal message. `Prop_LimitedEquip;1` with an `id=` key appears to allow one equipped stone per id (`Prop_LimitedEquip` was not traced), as in `endowiounstone.txt:1`. Stone shapes are the name suffix (cube, cylinder, lozenge, and so on).
- Gemstones. `Chant_EndowGemstones.java:115-129`. `endowgemstones.txt` has 80 rows over ten masks: attack, cha, con, dex, hit, int, mana, move, str, wis. Each row maps a level to an adjuster such as `attack+2` at level 1 (`endowgemstones.txt:6`) and `con+6` at level 90. The XLEVEL column is described in the header as the XP level that sets how much XP is lost (`endowgemstones.txt:1-5`); the loss formula was not traced.
- Instance areas. `InstanceArea.java:2064` loads `areainstancetypes.txt`. There are 85 definitions. One base type `"Triumph"` (`areainstancetypes.txt:66`) sets `category="Main"`, `duration=900`, `LIMIT="1/DAY"`, `ELITE`, and `IEFFECT="Prop_NoCraftability Prop_ItemBinder"`. Its level formulas `ileveladj` and `leveladj` use `@x1` to `@x7` (base-area levels, the player level and the faction score). The other 84 entries use `LIKE="Triumph"` and vary `LEADERREQ`, `enable` and `behave`. They form four tiers, I to IV, of 21 entries each (computed).
- Ships and caravans. `Shipwright.java:116-181` reads the `.cmare` as XML with `CoffeeMaker.addItemsFromXML` (`CoffeeMaker.java:2235`). It writes a temp recipe file `::skills/<name>.txt` (`Shipwright.java:157-172`) with one row per prototype: name, level, weight/10 (ticks), weight (materials), gold value, and an index. `CaravanBuilding.java:78-99` names the file and allows wooden land or wagon vehicles only. The `Shipwright` format string is a single column, `ITEM_CMARE` (`Shipwright.java:78-81`), which does not match the six columns it writes.
- Clan castles. `ClanCrafting.java:132-188`. Castles are appended to the clan recipe list with formulas per room (room count `weight`, from `Boardable.getArea().numberOfProperIDedRooms()` at line 168): build time 450 ticks (line 173), material 2500 units (174), XP cost 1000 (177), population requirement `20 + 10 * rooms` (`getDefaultPopRequirement`, line 291-297), required skill `Masonry` (line 188, with the comment "?!").
- Renaming. `Boardable.java:122-129` lists `NAME_REPL_STRINGS` (`NAME`, `NEWNAME`, `SHIPNAME`, `SHIP`) and `NAME_REPL_MARKERS` (`<>`, `[]`, `{}`, `()`). `StdBoardable.java:67` gives the default name `a boardable [NEWNAME]`, so built vessels carry a placeholder the owner replaces.
- Animal trapping. `AnimalTrapping.java:174-200`. `atrapping.txt` rows are resource, domain (`*` for any, or names such as `Swamp` and `SaltWaterSurface`), frequency weight, and a MOB/race name. The engine sums the weights of matching rows, rolls `1..total`, and walks the list.
- Aging. `Age.java:204-223` parses `age.txt` into a map keyed by race and sex. Each row is `FROM, comparator, aging-chart index, sex, TO`. `Age.java:236-262` applies it: with `>` the change happens when age is at least `R.getAgingChart()[index]`, with `<` when it is below. Example: `age.txt:1`, a male Calf turns into a Bull at aging index 3.
- Decorations. `Decorating.java:99-104` and `:364-392`. The name column is the keyword that the player types. The list output begins `"Decoration terms: "` (line 364). Failure: `"Decorate what? '@x1' is unknown. Try DECORATE LIST."` (line 390).
- Body piercing. `BodyPiercing.java:90-93` and `:232-250`. Six wear locations, each with the body-part names a player may use (`bodypierce.txt:2-7`).
- Cutting drugs. `DrugCutting.java:35` extends `Cooking`. `drugs.txt` has eight rows whose outputs map to `Poison_*` and `Prayer_*` abilities (for example a sedative at `drugs.txt:1` and a stimulant at `drugs.txt:7`). One row (`drugs.txt:8`) is a `GenPowder` with an added chance and an adjustment.
- Allowed skills. `CMAbleMap.java:2574-2600` parses `allqualifylist.txt`. The `[ALL]` section (76 lines, levels 1-29) and the `[EACH]` section (8 lines, levels 1-4) grant skills by level. Keys used: `REQUIRES=` (27 lines), `MASK=` (45), `AUTOGAIN` (2), `PROF=` (1). Covers 84 distinct skill IDs (computed). Example: `allqualifylist.txt:21` grants `CaravanBuilding` at level 1 with a class and achievement mask and two prerequisites.

## Content worth mining

### Caravans (`caravanbuilding.cmare`)

All 94 items are `GenCaravan`, each with `IUSES=100`. Stats are read from each item's own text (`PROP` block: ability, armour, attack, damage, disposition, level, rejuv, speed, weight, height, senses; `CoffeeMaker.getPhyStatsStr`, `CoffeeMaker.java:5490-5501`). Computed over all 94:

- Weight 1,600 to 31,900; therefore build ticks (weight/10) 160 to 3,190 and materials equal to weight.
- Base value 1,100 to 23,750.
- Level 1 to 91, plus one at 1000 (item 38, `caravanbuilding.cmare:166`; see Problems and cruft).

Sample rows:

| Caravan | Level | Weight | Notes |
|---|---|---|---|
| the coach caravan | 1 | 1,600 | cheapest |
| the vault caravan | 45 | 13,200 | flag set `505` |
| the tower caravan | 91 | 17,600 | top tier |
| the armored treasure caravan | 90 | 31,900 | heaviest |
| the security caravan | 15 | 6,600 | flag set `505` |
| the light reinforced siege platform | 1000 | 5,600 | level outlier |

Names are assembled from a size or armour prefix (small, medium, large, heavy, light, reinforced, armored, darkened, lantern, glamorous, luxurious, posh) and a body (coach, touring, tanker, freight, doubledecker, icehouse, siege platform, spa, library, study, kitchen, restaurant, dining, office, temple, chapel, prayer, smithy, smithshop, factory, treasure, vault, security, tower, carroballista, traveller). Each name carries `[NEWNAME]` (section 10).

### Clan castle (`clancastles.cmare`)

One `GenClanCastle`, "Small Castle %", with three rooms: "The Courtyard" (plains), "Inside the Keep" (stone room) and "Top of the Tower" (ship deck). Their room IDs and names are inside the escaped `ITEXT` block (`&lt;AROOM&gt;`). The item carries level 1 in `ILEVL` and level 5 in its `PROP` block, and the recipe reads `basePhyStats().level()` (`ClanCrafting.java:172`). Which value wins depends on load order; not verified.

### Boats (`boatwright.txt`)

67 boats, levels 1 to 88, build ticks 35 to 760, values 200 to 6,104 (computed). 60 rows carry `Prop_MoveRestrictor` with messages for water type (ocean-worthy, shallow water). Names, with levels: logboat 1, raft 2, dugout 3, canoe 4, row boat 4, jon boat 5, umiak 6, skiff 8, makoro 10, pram 11, longboat 12, adirondack 13, drift boat 14, shad boat 15, coracle 16, punt 17, waka 18, sandolo 20, dinghy 21, kayak 22, weidling 23, zille 24, dory 26, surfboard 30, tjotter 31, baidarka 32, jolly boat 33, rodney boat 34, dragon boat 35, surfboat 36, sharpie 37, pirogue 39, shikara 40, mackinaw boat 41, gig 43, barge 44, longship 45, jukung 46, sampan 48, shallop 49, outrigger canoe 50, gondola 52, sea kayak 54, langschiff 55, pinnace 56, proa 57, pontoon 58, double scull 60, wherry 62, launch 66, catamaran 80, windsurfer 83, trimaran 88 (plus several scull, four and eight boats and a few others; see the file).

### Armour (`armorsmith.txt`, `cobbler.txt`)

Armorsmith: 185 rows, 175 `GenArmor` and 10 `GenShield`, levels 1 to 30 (computed). Plate variants (platemail, fieldplate, fullplate) account for about 87 rows (substring count), banded about 29, and ringmail, scalemail and chainmail 16 each. Cobbler: 19 rows, levels 0 to 18, footwear and hose from socks to riding boots, with layer-prefixed wear strings (section 6).

### Blacksmith and carpentry (`blacksmith.txt`, `carpentry.txt`)

Blacksmith: 149 rows, levels 1 to 91. Classes: `GenContainer` 38, `GenItem` 29, `GenEmptyDrink` 25, `GenDice` 14, `GenChair` 7, `GenBed` 7, `GenStove` 6, `GenLimb` 5, `GenLantern` 4 (computed). Examples: `% statue` (level 41, 450 material units, value 37,500, `blacksmith.txt:74`); `% claw-foot tub` (63, value 25,000); `% altar` (58, value 7,400); `% Colossus` (81, 4,000 ticks, 8,000 materials, value 26,500,000, with a spell list, `blacksmith.txt:140`).

Carpentry: 202 rows, levels 1 to 91. Classes: `GenContainer` 49, `GenWeapon` 42, `GenChair` 19, `GenEmptyDrink` 14, `GenDice` 14, `GenBed` 13, `GenRideable` 13, `GenShield` 12, `GenItem` 9, `GenLimb` 6 (computed). Examples: `% bokken` (level 1, blunt one-handed weapon); `legendary % lumberjack saw` (level 91).

### Building skill (`construction.txt`, `excavation.txt`)

Construction: 69 rows, levels 1 to 90. Build codes: `DOOR` 41, `ROOM` 14, `ROOMEFFECT` 5, `WALL` 2, `STAIRS` 2, `DELEFFECT` 2, one each of `DESC`, `TITLE`, `DEMOLISH` (computed). Excavation: 32 rows, ticks 10 to 5000. Examples: `archway` (level 5, `DOOR`, `GenExit`, `archway|an archway||||...`); `light boulder` (level 10, `DOOR`, cave-only, no wall); `small maze`, `medium maze`, `big maze` and `huge maze` (levels 15, 23, 30, 37, `ROOM` with `CaveMaze`); `escape tunnel` (level 80, 5000 ticks, `MountainSurface` exit); `secret door` (level 90, hidden, thief-only mask at `excavation.txt:31`); `druidic monument` (level 13, `ITEM`, `DruidicMonument`, mask `-ANYCLASS +Delver`, `excavation.txt:32`).

### Alchemy (`alchemy.txt`)

Thematic reagent use (computed and checked against the rows): WATER for cures and protections (26 rows, e.g. `Prayer_CureLight` WATER POTION, `alchemy.txt:84`), BLOOD for harms and curses (16 rows, e.g. `Prayer_Harm` BLOOD POTION), SALT WATER for sensing prayers (11 rows, e.g. `Prayer_SenseEvil`). Metals appear for protective spells: `Spell_Shield` BRASS, `Spell_Immunity` MITHRIL, `Spell_CombatPrecognition` STEEL, `Spell_IronGrip` IRON. Two rows use POWDER instead of POTION: `Spell_Sleep` SAND and `Spell_Blur` COTTON.

### Bombs (`bombs.txt`)

Nine size tiers (Tiny, Small, Lesser, Moderate, Improved, Greater, Large, Huge, Massive) by 13 kinds, each with its own trap class `Bomb_*`: Water, Sticky, Spores, Sonic, Smoke, Shaped, Poison, Pepper, Noxious (named "stink bomb" in the file), Heat, FlameBurst, Explosive, AcidBurst. Levels run from 1 (`bombs.txt:1`) to 93 (`bombs.txt:117`). All 13 trap classes exist (checked by file name).

### Expertise families (`expertises.txt`)

205 definitions over 202 base codes. Examples by family:

| Family (code) | Name in file | Flag | Effect |
|---|---|---|---|
| `EXPCRAFT` | Wise Crafting | XPCOST | modifies the XP cost of the listed spells and skills (help: "ameliorate the experience cost", `expertises.txt:22`) |
| `ARMOROPT` | Armor Optimizing | LEVEL | ten stages; class-level gates per stage (`expertises.txt:24`) |
| `ALERT` | Vigilantly | LEVEL | stages with class-level gates (`expertises.txt:39`) |
| `TUNING` | Artsy | LEVEL | ten stages; the colour-expertise family (`expertises.txt:78`) |
| `RANGED`/`REDUCED`/`POWER`/`EXTENDED` `DIVINATION` | Ranged, Reduced, Power, Extended Divining | several | divination modifiers with numbered stages; about 14, 12, 12 and 12 definitions by four-letter prefix (computed) |
| `*STRIKE`, `*SLICE`, `*PIERCE`, `*BASH`, `*FOCUS` | weapon-type families for axes, blunt, edge, flail, unarmed and siege | X1 to X4 | effect depends on the skill code (`expertises.txt:12`) |

### Iounstones and gemstones

Ioun stones: 249 rows. Every row's first property is `Prop_WearAdjuster` with a stat (STR, DEX, CON, INT, WIS, CHA, each +1), a save bonus (`save*`, +10; twelve save types with ten rows each), or a max-stat raise (MAXDEX 15, MAXINT 12, MAXWIS 12, MAXCHA 12, MAXSTR 9 rows; computed). Gemstones: 80 rows over ten masks, for example `diminuitive attack bonus` (level 1, `attack+2`, `endowgemstones.txt:6`), `lesser constitution bonus` (level 30, `con+2`, expertise `RANGEDDIVINATION2`, `endowgemstones.txt:81`), and `superior constitution bonus` (level 90, `con+6`).

### Instance-area types (`areainstancetypes.txt`)

The Triumph family, four tiers I to IV, 21 entries each. Tiers name a set of schools chosen per instance: for example `Triumph I/Electrocuting`, `Triumph I/Burning`, `Triumph IV/Martial-Electrocuting-Burning-Acidizing` (`areainstancetypes.txt:157`). The school names are Electrocuting, Burning, Acidizing, Freezing, Healing, Creation, Illusion, Transmutation, Cursing, Stealing, Martial and Criminal (from `DISPLAY` values).

### Colours and dyes

`colors.txt` names 16 base colours (black, maroon, dark green, brown, dark blue, ...) and 216 cube colours with alternate names (for example "forest green" for dark green). `dyeing.txt` gives 451 dye recipes from `red` and `blue` at level 1 to `luminous vivid orchid` and `shocking-pink` at levels 77 and 87, with the xterm-256 codes.

### Trapping, aging, decorations, cages, piercing

- Trapping: 61 rows over 16 resources (`FISH`, `FEATHERS`, `SCALES`, `WOOL`, `MUTTON`, ...), with frequency weights from 1 to 4 (computed).
- Aging: ten rules covering calves, kittens, puppies, cubs, bulls, cows, cats, dogs, bears (`age.txt`).
- Decorations: 14 verbs: HANG, MOUNT, STICK, LAY, STAND, PLACE, DISPLAY, ARRANGE, FIX, BUILDIN, CENTER, ONLEFT, ONRIGHT, FLAPBREEZE (`decorations.txt`).
- Cages: six recipes, from a small cage (level 1, 30 ticks, 10 materials; `cagebuilding.txt:2`) to a large cage wagon (level 15, 45 ticks, 400 materials; `cagebuilding.txt:6`).
- Piercing: six wear locations from ears (level 1) to torso (level 25, `bodypierce.txt:7`).

## Notable design ideas

- The recipe table is the schema. Column names live in `getRecipeFormat()` and the same string is the header of the in-game editor (`CMAbleParms.getRecipeList`, `CMAbleParms.java:936-970`; `modifyRecipesList`, `:974`). Builders can edit recipes in the running game.
- Item power follows material. The recipe level is shifted by three times the hardness of the metal above 6 (`Armorsmithing.java:534-535`), so the same recipe row yields a stronger item from a harder metal.
- Time and cost bend with skill. Build ticks shrink as the builder's level exceeds the item's level and as XTIME rank rises (`CommonSkill.java:346-363`). Material use shrinks with XPCOST rank (`CraftingSkill.java:361-372`). Both are cheap to reproduce.
- Expertises are stackable, stage-based modifiers defined as data with requirement formulas per stage (`expertises.txt:24`), so a single line defines a whole skill ladder.
- Requirements as data. The component syntax states who holds the item, whether it is consumed, how many, and which ritual or social unlocks it (`components.txt:1-33`). Consumption is explicit and separate from crafting logic.
- The object is the recipe. A caravan or castle is stored as a finished item; the engine generates the recipe from its weight, level and value (`Shipwright.java:157-172`; `ClanCrafting.java:163-177`). Any buildable object can be authored in one place.
- Unique-equipment rules by key. Ioun stones use `Prop_LimitedEquip;1` with an `id=` key and a message, so duplicate effects are blocked by data rather than code (`endowiounstone.txt:1`).
- Instance types inherit. 84 Triumph variants use `LIKE="Triumph"`, so one base row carries the shared limits (`areainstancetypes.txt:66`, `:157`).
- The colour table can be derived. The 6x6x6 cube formula gives IDs 16 to 231 (`Brown.java:884`), so the table needs only the base colours plus a formula.

## Problems and cruft

- Dead generator. `Brown.generateRecipes()` (`Brown.java:774`) is never called, although it writes `dyeing.txt` and `lacquering.txt` (`Brown.java:845-847`). Edits to the dye files will not be regenerated.
- Shipwright format mismatch. `Shipwright.getRecipeFormat()` returns the single column `ITEM_CMARE` (`Shipwright.java:78-81`), while the generated temp file has six tab-separated fields (`Shipwright.java:165-170`). A recipe editor built from the format string would, by inference, see one column.
- Caravan level outlier. Item 38 in `caravanbuilding.cmare` (line 166) has level 1000 in both `ILEVL` and `PROP`. A player would need a level near 1000 under the usual gate (`Armorsmithing.java:462` shows the rule; the caravan path's gate was not traced). Probably a typo; not verified.
- Colossus value. `blacksmith.txt:140` lists base value 26,500,000 with 8,000 materials and 4,000 ticks. It is probably a deliberate joke or monument. Not verified.
- Empty costume recipe file. `costume.txt` is 0 bytes. The loader accepts it silently for `Costuming` IDs (`CommonSkill.java:311-313`; `CraftingSkill.java:234-237`), which hides the missing content.
- Colour header mismatch. `colors.txt:1` labels nine columns, but rows carry eight fields; the label "Expertise" does not match field 7, which holds a base colour or cube code (`Brown.java:870`).
- Decorations column missing. `Decorating.java:73-79` defines `RCP_MISC` = 6, but all 14 rows in `decorations.txt` have six fields, so that column is always empty.
- Dye rows missing a field. `dyeing.txt:4` (white) and `dyeing.txt:20` (navy-blue) have six fields instead of seven, so the ANSI256 flag column is absent for them.
- Format split. `GenCraftSkill.java:88-90` writes the literal `"ITEM_NAME\tI" + "TEM_LEVEL\t"`, which works but reads as an error.
- Typos in data. "diminuitive" appears in `endowgemstones.txt` (lines 6, 17, 28, 39); "numerouse" appears in `excavation.txt:25`.
- Inconsistent gates. `Decorating.java:380-383` gates on the LEVEL expertise rank directly, while crafting uses level plus twice the rank (`CommonSkill.java:647`). A player with the same expertise sees different recipe ceilings for the two trades.
- Multiplayer assumptions. Clan items (`clancraft.txt`), clan castles with population requirements (`ClanCrafting.java:291-297`), account-achievement masks (`allqualifylist.txt:21`), instance-area leader requirements and faction scores (`areainstancetypes.txt:66`) and owned vessels all assume several players or accounts.
- Lookup case. `atrapping.txt` names the race `Seahorse`, but the class file is `Races/SeaHorse.java`. Works only if race lookup ignores case; not verified.
- Free-text formulas. `areainstancetypes.txt` level and enable formulas are strings evaluated by the engine. This is powerful but hard to validate.

## Takeaways for a Python SUD

- Store recipes as typed records, not TSV with format strings. A `Recipe` dataclass with `name`, `level`, `ticks`, `materials` (a dict of material to quantity), `value`, `produces` (a class or template name) and `requires` (a list of component requirements) keeps the schema in one place. Keep the level as the primary sort and gate key.
- Keep the level gate, but make it single-player: `max_level = player_level + 2 * rank(LEVEL)` is a clean rule. Use the same gate for every trade so the player has one ceiling.
- Build time and material formulas are cheap to port:
  - `ticks = base * (1 - clamp((player_level - item_level) / LAST_LEVEL * 0.5)) * (1 - 0.05 * rank(XTIME))`
  - `materials = max(1, round(base * (1 - 0.05 * rank(XPCOST))))`
  Express them in game turns, not ticks, so the choice between real-time and turn-based is deferred.
- Item level from material hardness (`level = row_level + 3 * (hardness - 6)`, minimum 1) is a good single-player rule, and gives crafted items meaningful progression.
- Requirements as data. Model `Requirement(where='inventory'|'held'|'worn'|'nearby', consume=bool, qty=int, match=str)` and optionally parse the original `(disposition:fate:amount:id:mask)` syntax for compatibility. Drop the multiplayer ritual trigger, or reimagine it as a typed command.
- Expertises as a dict of `Expertise(code, name, stages, flags, stage_requirements)`. Store stage requirements as numbers, not expressions. Do not `eval()` the masks.
- Caravans and castles as "built objects". A vehicle or house can carry its own weight, level and value and derive its recipe from them (weight/10 ticks, weight materials). That removes a whole recipe file for each large item.
- Instance types as inheritance. A dataclass `InstanceType` with a `like` field reproduces the Triumph family; put the level and limit fields in the base and override the element list.
- Colours: generate the 256-colour table in code (16 named plus the 6x6x6 cube) and do not store it.
- Trapping and aging are simple tables. Use `random.choices(rows, weights=...)` for trapping, and a list of `(from_race, sex, threshold_age, to_race)` for aging, checked with `>=` and `<`.
- Drop clan dues, population requirements, account achievement masks, rename placeholders that exist only for shared ownership, and the `.cmare` XML container. Keep the item-as-recipe idea and the expertise ladder.

## Coverage

- Read in full: `excavation.txt`, `clancraft.txt`, `cobbler.txt`, `decorations.txt`, `drugs.txt`, `cagebuilding.txt`, `bodypierce.txt`, `age.txt`, `atrapping.txt`, `alchemy.txt`, `components.txt` (header and examples), `costume.txt` (empty), `clancastles.cmare` (parsed by script, all fields), `caravanbuilding.cmare` (all 94 items parsed by script).
- Read in part and computed over all rows: `boatwright.txt` (all 67 names by script, first 10 and last 3 read), `armorsmith.txt`, `blacksmith.txt`, `carpentry.txt`, `construction.txt`, `embellishing.txt`, `bombs.txt`, `bake.txt`, `dyeing.txt`, `colors.txt`, `endowgemstones.txt`, `endowiounstone.txt`, `allqualifylist.txt`, `areainstancetypes.txt`, `expertises.txt` (all 205 definitions summarized by script; help and data text not read).
- Java read in the relevant ranges: the recipe loaders and parsers, the build path of `Armorsmithing`, the duration and gate helpers, `BuildingSkill` enums and format, `Shipwright`, `CaravanBuilding`, `ClanCrafting`, `Brown` (colour loader and dead generator), `ColumbiaUniv` (expertise parse), `CoffeeMaker` (item XML and `getPhyStatsStr`), `Decorating`, `Cooking` constants, `AnimalTrapping`, `Age`, `InstanceArea` (loader only), `CMAbleMap` (loader only), `Alchemy`, `Thief_MakeBomb`, `Chant_EndowGemstones`, `Chant_EndowIounStone`, `GenCraftSkill` format.
- Skipped: the bodies of `BuildingSkill` room construction, the `Cooking` invocation, the `Wainwrighting` and `PaintingSkill` builds, `CoffeeUtensils.addExtRecipe`, the `CMAbleComps` component evaluation, and the spell and skill code that consumes expertise flags beyond the crafting paths above.
