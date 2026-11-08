# Crafting recipes and skill data, part 2

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `c05` (content: Crafting recipes and skill data, part 2) · 65 files · 1,172,527 bytes of data.

## Summary

This bucket is the second half of CoffeeMud's crafting data. It holds recipe tables for smithing (weapons, armour, staves, wands, guns, siege engines and ammunition), tailoring and leatherwork, jewellery, pottery, glass, paper, instruments, vehicles and sailing ships, the cooking family (food, drink, poisons, liquor and prison food), dye and lacquer colours, building-room recipes, and gathering tables for fishing, hunting, herbs, flowers and mushrooms. The recipe tables are all read by the same `CraftingSkill` machinery: each row is one tab-separated line, and each skill class declares what each column means with its own index constants. The bucket also holds non-recipe data that reuses the same conventions: the prophecy scripts (`predictions.xml`), the planes-of-existence configuration, the Wish spell's forbidden-ability lists, druid shapeshift forms, racial mounts, song lyrics, joke word lists, urchin names and taxidermy poses. The largest file, `shipbuilding.cmare` (672 KB), is a serialized item export. Its ship prototypes become the build recipes for sailing ships, and each ship's own stats (weight, level, value) become that recipe's cost, build time and price. For a single-player Python game the most borrowable parts are that self-describing prototype idea, the habitat-filtered weighted gathering tables, and prophecies written as data that schedule a later outcome.

## Key files and types

Paths are relative to the CoffeeMud folder. "Lines" is the Java file's line count. The data files are catalogued in "Content worth mining".

| File or class | Role | Lines |
|---|---|---|
| `core/interfaces/RecipeDriven.java` | Interface for recipe-driven skills and items. Fixes `RCP_FINALNAME = 0` and `RCP_LEVEL = 1` (lines 35, 40) and declares `getRecipeFormat()` and `getRecipeFilename()` | 77 |
| `Abilities/Common/CraftingSkill.java` | Base recipe loader `loadRecipes(String)` (l.204-243), `%` name substitution (l.279-300), craft entry points (l.949-1051), material and component checks | 2459 |
| `Abilities/Common/CommonSkill.java` | Older loader `loadRecipes(String)` (l.299-318), `baseYield()` (l.84), `getDuration()` (l.346-360) | 1016 |
| `Abilities/Common/SpellCraftingSkill.java` | Base for spell-keyed skills: scribing, herbalism, alchemy | 156 |
| `Abilities/Common/EnhancedCraftingSkill.java` | Expertise-based item enhancement: `enhancedTypes()` (l.465), `enhanceItem()` (l.685). Base of most smithing, tailoring and cooking skills | 913 |
| `Abilities/Common/BuildingSkill.java` | Room, door, wall, stair and roof recipes. Building-code enum (l.106-121), flag enum (l.122-130), column map (l.153-162) | 2035 |
| `Libraries/CoffeeUtensils.java` | `loadRecipeList` tab and line parser (l.242-300). Player-owned recipe books through `addExtRecipes` (l.202-238) | 3876 |
| `core/CMFile.java` | `getExistingExtendedFiles` (l.261-276) returns `name`, `name.1` to `name.9` | 2306 |
| `Abilities/Common/Shipwright.java` | Ship builder. Derives its recipe table from the `.cmare` prototypes (`getShips` l.116, `loadRecipes` l.151-180) | 1035 |
| `Abilities/Common/Fletching.java` | Reference smithing implementation. `autoGenInvoke` (l.253-578) | 578 |
| `Abilities/Common/Weaponsmithing.java` | Weapon column map (l.90-99) and stat formulas (l.545-572). Parent of the master and legendary weapon skills | 613 |
| `Abilities/Common/Cooking.java` | Pot cooking: ingredient matching and consumption (l.540-606, 738-750). Parent of Apothecary, Distilling, FoodPrep and GaolFood | 1573 |
| `Abilities/Common/Apothecary.java` | Poison recipes and antidote mode (`loadRecipes`, l.113-150) | 242 |
| `Abilities/Poisons/UniPoison.java` | Data-defined poison. Parses `KEY=VALUE` options in `setMiscText` (l.178-190) | 275 |
| `Abilities/Common/PaintingSkill.java`, `Painting.java`, `Lacquerring.java` | Painting and lacquer colour recipes. Column constants at PaintingSkill l.57-62 | 398, 656, 240 |
| `Abilities/Common/MasterPaintingSkill.java` | Pattern colouring. `applyColorMask` (l.62-), `requiredColorsInRecipe` (l.291-296) | 334 |
| `Abilities/Common/Mycology.java` | Mushroom identification with a weighted range map (l.150-192) | 309 |
| `Abilities/Common/Fishing.java`, `Hunting.java` | Weighted catch and kill tables keyed by resource, and for hunting by habitat | 275, 389 |
| `Abilities/Common/Herbology.java`, `Floristry.java`, `Herbalism.java` | Herb and flower identification; herbal brewing | 267, 232, 561 |
| `Abilities/Thief/Thief_Trap.java`, `Thief_MinorTrap.java` | Trap setting from `traps.txt`, sorted by level, random pick (`invoke` l.107-160) | 443, 70 |
| `Abilities/Common/Taxidermy.java` | Pose-group parser (`loadRecipes`, l.129-170) | 291 |
| `Abilities/Common/ScrollScribing.java` | Spell-scroll reagents and scroll naming (l.221, l.317-345) | 591 |
| `Abilities/Common/SmokeRings.java` | Random smoke-ring text per puff (l.132-155) | 239 |
| `Abilities/Common/PaperMaking.java` | Books, recipe pages and pages-by-characters parsing (l.394-412) | 483 |
| `Abilities/Prayers/Prayer_Prophecy.java` | Loads `predictions.xml`, shows the summary, schedules the outcome (`generateEventPrediction` l.134-200) | 428 |
| `Abilities/Thief/Thief_Runecasting.java` | Second reader of `predictions.xml` (`generateEventPrediction` l.339-) | 800 |
| `Abilities/StdPlanarAbility.java` | Planes parser `getAllPlanesMap` (l.1565-1636); in-game editor `addOrEditPlane` (l.353-) | 2235 |
| `Abilities/Spells/Spell_Wish.java` | Parses `[NOCAST]` and `[NOGAIN]` sections (`getWishParms` l.123-170) | 2199 |
| `Abilities/Druid/Druid_ShapeShift.java` | Shapeform parser (`ShiftShapeForm` l.98-114, `fillShapeField` l.174-214, `getShapeData` l.218-249). Stat adjustments (l.323-327). Tier selection (l.417-428) | 760 |
| `Abilities/Fighter/Fighter_RacialMount.java` | Racial mount table (`getRacialMountChoices` l.143-190) | 330 |
| `Abilities/Songs/Song.java` | Lyric loader `getLyrics` (l.252-284) and per-tick singing (l.347-372) | 661 |
| `Abilities/Songs/Skill_Joke.java` | Joke generator (l.96-175) | 184 |
| `Abilities/Thief/Thief_NameUrchin.java` | Urchin naming, including `random` (l.150-160) | 220 |
| `Items/Basic/GenSailingShip.java` | The generic ship item the prototypes are instances of (name token l.58) | 498 |
| `Libraries/CoffeeMaker.java` | `.cmare` reader `addItemsFromXML` (l.2299-2302). Item stat serialisation `getPhyStatsStr` (l.5490) and `setPhyStats` (l.5578) | 6719 |
| `Items/interfaces/Boardable.java` | Rename tokens for boardable names (`NAME_REPL_STRINGS`, l.122-123) | 130 |

## How it works

### 1. The recipe row and its column map

A recipe table is a plain text file. One line is one recipe. Fields are separated by tabs. A line that starts with `#` is a comment. Trailing empty fields are allowed, and the parser pads every row to the width of the longest row (`CoffeeUtensils.loadRecipeList`, `Libraries/CoffeeUtensils.java:242-300`). The parser splits on tabs only and does not trim, so a key with a trailing space is a different key (see Problems).

The file does not say what its columns mean. Each class does that. `getRecipeFormat()` returns a header string that names the columns (for example `Fletching.java:78-83`). The class then declares index constants. The first two come from the interface. Fletching adds `RCP_TICKS = 2`, `RCP_WOOD = 3`, `RCP_VALUE = 4`, `RCP_CLASSTYPE = 5`, `RCP_AMMOTYPE = 6`, `RCP_AMOCAPACITY = 7`, `RCP_ARMORDMG = 8`, `RCP_MAXRANGE = 9`, `RCP_EXTRAREQ = 10`, `RCP_SPELL = 11` and `RCP_HANDS = 12` (`Fletching.java:85-97`). Each skill family has its own map. The cooking family uses `RCP_FINALFOOD`, `RCP_FOODDRINK`, `RCP_BONUSSPELL`, `RCP_LEVEL`, then alternating ingredient and amount pairs from `RCP_MAININGR = 4` (`Cooking.java:122-127`). Building recipes use a 13-column map (`BuildingSkill.java:153-162`).

Loading works like this:

- The base loader collects every matching file, sorts the rows by level, and caches the result under the key `PARSED_RECIPE: <file>` (`CraftingSkill.java:204-243`). Rows whose level column is missing sort first.
- `CMFile.getExistingExtendedFiles` (`core/CMFile.java:261-276`) returns `name`, then `name.1` through `name.9`. The loader concatenates them, so content can be added without editing a base file. The scan stops at the first missing number from `.2` upward, so `.1` may be absent but the later numbers must be contiguous.
- Player-owned recipes are added at lookup time. `CoffeeUtensils.addExtRecipes` (`:202-238`) appends rows from any `Recipes` item in the crafter's inventory or effects whose common skill ID matches. Padding follows the previous row's width (`addExtRecipe`, `:163-200`). The papermaking skill creates these items: recipe pages and recipe books (`papermaking.txt`, GenRecipe rows).
- `%` in a name is a placeholder for the material. `replacePercent` (`CraftingSkill.java:279-300`) replaces the first `%` with the material name, or removes it when there is no material, so `% butter knife` becomes a name such as "iron butter knife".
- An empty table logs "Recipes not found!" (`CraftingSkill.java:234-238`). Gen-family skills and Costuming subclasses are exempt. The same exemption exists in `CommonSkill.java:312-314`.

### 2. Lookup and level gating

`autoGenInvoke` is each skill's entry point. Fletching does this:

- It looks for an exact name match first and then a loose one (`Fletching.java:383`).
- It accepts a recipe only if `level <= xlevel(mob)`, the crafter's effective skill level (`Fletching.java:392`). Automatic generation (`autoGenerate > 0`) bypasses the gate.
- `list` shows name, level and a materials description. It also prints a note that some items need extra material (`Fletching.java:317` and surrounding).

### 3. The craft sequence (Fletching as the reference)

`Fletching.autoGenInvoke` (`Fletching.java:253-578`) runs in this order:

1. Stop and info checks, then `randomRecipeFix`.
2. Material class selection (wood or metal) through `checkMaterialFrom`.
3. Recipe selection by name and level.
4. Components. `getAbilityComponents` handles the material column when it is not a plain number (section 4 below).
5. Raw materials. `fetchFoundResourceData` finds matching material in the room and inventory. For metal the code checks for a lit fire in the room and fails with "You'll need to build a fire first." (`Fletching.java:446`).
6. `super.invoke` starts the timed activity. Then `CMLib.materials().destroyResources` consumes the raw materials and `destroyAbilityComponents` consumes the components. Both return "lost value" records, which later affect the item's value.
7. The item is created from `RCP_CLASSTYPE` through `CMClass.getItem`. Name, description, weight, value, level, ammunition and range are set from the row and the material (section 5).
8. `messedUp = !proficiencyCheck(...)` decides whether the work is spoiled.
9. The result goes out as an activity message through `mob.location().okMessage`. On success the code applies `beneficialAffect` for the duration and then `enhanceItem` for expertise bonuses.

The craft duration uses `getDuration(ticks, mob, level, minimum)` (`CommonSkill.java:346-360`). The recipe's tick column is the base. It drops by `((mobLevel - itemLevel) / LASTPLAYERLEVEL) * 0.5`, which means up to half off for a crafter well above the item's level. It is then floored at the minimum and reduced by 5% per point of the XTIME expertise (`:357-358`). Fletching uses a minimum of 4 ticks (`Fletching.java:472`), Shipwright 6, and BuildingSkill 10.

### 4. The material column has three forms

`MATERIALS_REQUIRED` (`RCP_WOOD` in most families) is handled by `getNonStandardComponentRequirements` (`CraftingSkill.java:2230-2258`):

- A number is a count of raw material units. Fletching's "crude % atlatl" uses `6`. The penteconter uses `4800`.
- A string that starts with `(` is an ability-component expression, parsed by `CMLib.ableComponents().addAbilityComponent`. The smelting row for bronze uses `(onground:consumed:1:TIN:)&&(onground:consumed:1:COPPER:)`.
- Any other string is a key into the component map loaded from `skills/components.txt` (`getAbilityComponentMap`). That file is in the c04 bucket.

### 5. Formulas that turn a row into an item

These are the stat formulas as written in the code. Values in the tables are the inputs.

- Fletching, the template for metal and wood (`Fletching.java:474-521`): `hardness = RawMaterial.CODES.HARDNESS(material) - 3`. Then `level = RCP_LEVEL + hardness` (l.487). Attack is `baseYield + abilityCode - 1 + 5*hardness` (l.520). Damage is `RCP_ARMORDMG + hardness` (l.521).
- Weapons, `Weaponsmithing.java:545-572`: `hardness = HARDNESS - 6`. Level is `RCP_LEVEL + 3*hardness` and cannot fall below 1 (l.550-552). Value is `RCP_VALUE/4 + woodRequired * VALUE(material)` (l.549). Attack is `RCP_ATTACK + 5*hardness + (baseYield + abilityCode - 1)` (l.571). Damage is `RCP_ARMORDMG + hardness` (l.572).
- `baseYield()` returns 1 (`CommonSkill.java:84-87`). `abilityCode()` returns `bonusYield` (`:201-203`), which is set by `setAbilityCode`.
- Ships (`Shipwright.java:151-177`). The derived recipe row for each ship is `Item.Name()`, then `level`, then `weight / 10` as build ticks, then `weight` as materials, then `baseGoldValue`, then the ship's index. At build time the materials value is read with `CMath.s_int` (l.898) and the wood is consumed (l.904-907). The built ship's base value is `RCP_VALUE + lostValue` (l.965).
- Cooking (`Cooking.java:540-606`). Each recipe ingredient is taken once per portion, and `amountMade` counts how many full portions the pot can supply. Water is multiplied by `LIQUID_RATIO = 10` (l.565). Negative amounts are optional for matching (l.747-750) and are consumed at their absolute value (l.563).
- Mycology (`Mycology.java:150-179`). Each mushroom with frequency `f` gets the integer range `[cur, cur+f]`, and `cur` then advances by `f+1` (l.162-164). The roll is `roll(1, max, 0)`, which returns 1 to `max`.
- Fishing (`Fishing.java:224-252`). Rows whose resource key matches the water type are summed. `winner = roll(1, total, -1)` picks a value from 0 to total-1. The first row whose running total exceeds `winner` wins.
- Hunting (`Hunting.java:273-305`). The same weighted pick, filtered by habitat: the row's domain must be `*`, the room's domain name, or the room's domain ID.
- Traps (`Thief_Trap.java:107-160`). Traps the thief can set (`maySetTrap` with the thief's qualifying class level) are sorted by level. A random one is picked with `roll(1, n, -1)` and then checked with `canSetTrapOn`.
- Poisons (`UniPoison.java:178-190`). Each poison's options are a `KEY=VALUE` string: `NAME`, `TICKS`, `DELAY`, `DAMAGE` as a formula, and optional `ADJUSTMENTS`. Variables `@x1` to `@x3` are substituted at runtime. Their meaning is not traced.
- Prophecy timing (`Prayer_Prophecy.java:174-188`). A `roll(1,5,0)` decides how far off the event lands. Result 1 adds no weeks. Results 2 to 4 add `roll(1,10,5)` weeks. Result 5 adds `roll(1,10,3)` months. Every case then adds `roll(1,10,3)` days and `roll(1,10,3)` hours.
- Druid shapeforms (`Druid_ShapeShift.java:323-327`): attack `+= round(level*attackAdj/2)`, armour `-= round(level*armorAdj/2)`, damage `+= round(level*dmgAdj/2)`, speed `+= speedAdj * CMProps.getSpeedAdjustment() * (1 + xlvl/3.0)`.
- Herbal brewing for chants (`Herbalism.java:466-476`): `experienceToLose = 10 + 10*spellLevel - 5*classLevel`.

### 6. Timing

The default tick is 4000 ms (`core/CMProps.java:681`, settable as TICKTIME). A recipe's tick column becomes craft duration through `getDuration` (above). Song lyrics advance one line per tick (`Song.java:347-372`). A `PAUSE n` line sets `lyricPauseCtdn` and skips n ticks (`:353-372`). Prophecy outcomes are not run at once. They are stored with the `ScriptLater` effect and a computed time code (`Prayer_Prophecy.java:189-191`).

### 7. Messages shown to players

These strings are taken from the code:

- "You'll need to build a fire first." (`Fletching.java:446`)
- "You don't know how to make a '@x1'.  Try \"@x2 list\" for a list." (`Fletching.java:402`)
- "There's no such thing as a @x1!!!" (`Fletching.java:469`)
- "@x1 appears to be @x2." (`Mycology.java:192`, `Herbology.java:164`). The unknown item is named by what it turns out to be.
- "a pound of @x1 has been left here." (`Fishing.java:253`)
- "<S-NAME> blow(s) out a perfect smoke ring." is the fallback when no phrase qualifies (`SmokeRings.java:149`). Otherwise a phrase is used. A phrase starting with `*` is wrapped as "blow(s) out ..." (l.151-152).
- "<S-NAME> sing(s) '@x1'" (`Song.java:369`)
- "<S-NAME> joke(s) to <T-NAMESELF>:" followed by the joke (`Skill_Joke.java:159`)
- "Your name contains illegal characters." (`Thief_NameUrchin.java:152`)
- "Taxidermy Poses" is the header for the pose list (`Taxidermy.java:178`)

### 8. Interactions with other systems

- Class families. Fletching has `Gunsmithing` and `Siegecraft`. `Weaponsmithing` has `MasterWeaponsmithing` and `LegendaryWeaponsmithing`. `Armorsmithing` has `MasterArmorsmithing`. `StaffMaking` has `Rodsmithing`. `WandMaking` has `ShardMaking`. `Tailoring` has `MasterTailoring`. `Cooking` has `Apothecary`, `Distilling`, `FoodPrep` and `GaolFood`. `PaintingSkill` has `Lacquerring` and `Dyeing`. `MasterPaintingSkill` has `MasterLacquerring` and `MasterDyeing`. `SmokeRings` has `MasterSmokeRings`. `Alchemy` has `ImprovedAlchemy`. `Herbalism` has `ImprovedHerbalism`. `Thief_Trap` has `Thief_MinorTrap`. `Shipwright` has `CaravanBuilding` and `ClanShipwrighting`.
- Abilities by ID. Poisons apply `Poison_*` effects. Coffee carries `Poison_Caffeine`, and liquors carry `Poison_Alcohol`, `Poison_Liquor` or `Poison_Firebreather`. Skill_Joke chooses `Spell_Laughter` 25% of the time and `Disease_Giggles` otherwise (`Skill_Joke.java:165-172`). Scribing and herbalism name spells and chants that `CMClass.getAbility` resolves. Trap IDs are `Trap_*` classes that are not in this bucket.
- Scripting. Prophecy reads its scripts with the percolator (`CMLib.percolator()`), runs the `Prediction` function on a `DefaultScriptingEngine`, and stores the outcome with `ScriptLater` (`Prayer_Prophecy.java:139-189`). The script language is the MOBPROG language documented in `guides/Scriptable.html`. The `select="any-1"` attribute is the RandomAreas document mechanism (`guides/RandomAreas.html`).
- Masks. Painting eligibility uses `CMLib.masking().maskCheck(zmask, mob, true)` (`Painting.java:277`, `:327`). This is the Zapper language of `Libraries/MUDZapper.java`.
- Planes. Planar spells and prayers read the planes map (`StdPlanarAbility.getAllPlanesMap`, l.1565). Users include `Spell_Teleport`, `Spell_Planarmorph`, `Prayer_PlanarTravel` and `Skill_PlanarLore`.
- Wish. `Spell_Wish.getWishParms` (l.123) is used by `getSpellsForbiddenToGain` and `getSpellsForbiddenToCast` (l.177-183).
- Race. Racial mounts and shapeforms look up races through `CMClass.getRace` and `racialCategory()` (`Fighter_RacialMount.java:130-139`; `Druid_ShapeShift.java:439-446`).
- Ship prototypes. The `.cmare` file is parsed by `CMLib.coffeeMaker().addItemsFromXML` (`Shipwright.java:124-131`; `CoffeeMaker.java:2299`). The prototypes are `GenSailingShip` items (`GenSailingShip.java:47`). Their stats are serialised by `getPhyStatsStr` as `ability|armor|attackAdjustment|damage|disposition|level|rejuv|speed|weight|height|sensesMask|` (`CoffeeMaker.java:5490-5503`). That is why a ship's PROP string reads `1|0|0|0|0|1|0|1.0|4800|0|16|`: ability 1, level 1, speed 1.0, weight 4800, sensesMask 16.

## Content worth mining

### Catalogue of the 65 data files

Owner class and its Java line count are in parentheses. The arrow shows the base class when the file uses an inherited loader. "Rows" counts non-comment data lines.

| File | Owner class (lines) | Rows | What it holds |
|---|---|---|---|
| `shipbuilding.cmare` | Shipwright (1035) | 63 ships | Serialized ship prototypes, 671,557 bytes; the recipe table is derived from them |
| `predictions.xml` | Prayer_Prophecy (428), Thief_Runecasting (800) | 100 scripts | Prophecy scripts: 28 "good", 16 "bad", 56 "meh" |
| `painting.txt` | Painting (656) | 910 | Paintings and drawings. Verbs: drawing 245, sketching 210, painting 210, coloring 210, finger painting 35 |
| `planesofexistence.txt` | StdPlanarAbility (2235) | 37 planes | Plane definitions with `KEY=VALUE` options |
| `lacquering.txt` | Lacquerring (240) | 451 | Lacquer colour names with colour codes. Differs from `dyeing.txt` only by the final newline |
| `fletching.txt` | Fletching (578) | 199 | Bows, slings, crossbows and ammunition (151 ammunition rows) |
| `masterarmorsmith.txt` | MasterArmorsmithing (112) ← Armorsmithing (600) | 230 | Master armour and shields, levels 12-91 |
| `jewelmaking.txt` | JewelMaking (814) | 176 | Jewellery, dice and small items, levels 1-90 |
| `staffmaking.txt` | StaffMaking (616) | 105 | Staves and shod staves (68 GenStaff), levels 2-90 |
| `gunsmith.txt` | Gunsmithing (98) ← Fletching | 114 | Guns (26) and rounds (88), levels 1-91 |
| `landscaping.txt` | Landscaping (111) ← BuildingSkill | 74 | Outdoor terrain effects (54 ROOMEFFECT rows) |
| `poisons.txt` | Apothecary (242) ← Cooking | 82 | 72 poison drinks, 10 perfumes, 35 data-defined poison families |
| `shardmaking.txt` | ShardMaking (100) ← WandMaking | 84 | Shards, levels 1-90 |
| `wandmaking.txt` | WandMaking (599) | 84 | Wands, levels 1-90 |
| `legendaryweaponsmith.txt` | LegendaryWeaponsmithing (108) ← Weaponsmithing | 134 | Weapons, levels 60-90 |
| `masterweaponsmith.txt` | MasterWeaponsmithing (108) ← Weaponsmithing | 134 | Weapons, levels 30-60 |
| `scrimshaw.txt` | ScrimShaw (661) | 149 | Carved items, 15 classes, levels 1-91 |
| `weaponsmith.txt` | Weaponsmithing (613) | 135 | Basic weapons, levels 0-30 |
| `liquors.txt` | Distilling (151) ← Cooking | 97 | Alcoholic drinks, levels 1 and up |
| `mastertailor.txt` | MasterTailoring (96) ← Tailoring | 127 | Designer textiles: sleeping bags, tents, pillows, levels 0-91 |
| `wish.txt` | Spell_Wish (2199) | 196 + 209 | `[NOCAST]` and `[NOGAIN]` ability lists |
| `smelting.txt` | Smelting (514) | 48 | Alloys, levels 1-90 |
| `masonry.txt` | Masonry (116) ← BuildingSkill | 49 | Room, door, stair and item builds |
| `rodsmithing.txt` | Rodsmithing (119) ← StaffMaking | 49 | Rods (37 GenItem), levels 1-73 |
| `tailor.txt` | Tailoring (588) | 121 | Basic textiles, levels 0-30 |
| `scribing.txt` | ScrollScribing (591) ← SpellCraftingSkill | 238 | Spell to reagent pairs for scrolls |
| `siegecraft.txt` | Siegecraft (92) ← Fletching | 54 | Siege ammunition and engines (16 GenSiegeWeapon) |
| `pottery.txt` | Pottery (597) | 107 | Pottery, 10 classes, levels 1-91 |
| `instruments.txt` | InstrumentMaking (436) | 79 | Instruments (69 GenInstrument, 10 GenPiano), levels 1-45 |
| `sculpting.txt` | Sculpting (615) | 108 | Sculpted goods, 16 classes, levels 1-90 |
| `irrigation.txt` | Irrigation (120) ← BuildingSkill | 31 | Water-channel and well builds |
| `papermaking.txt` | PaperMaking (483) | 73 | Paper, scrolls, books and recipe pages (8 GenRecipe) |
| `glassblowing.txt` | GlassBlowing (654) | 88 | Glass goods, 13 classes, levels 1-90 |
| `wainwright.txt` | Wainwrighting (492) | 55 | Vehicles and carts (49 GenRideable), levels 1-88 |
| `improvedalchemy.txt` | ImprovedAlchemy (115) ← Alchemy | 129 | Spell potions. Byte-identical to `alchemy.txt` |
| `songlyrics.txt` | Song (661) | 6 songs | About 103 lyric and PAUSE lines |
| `recipes.txt` | Cooking (1573) | 56 | Food (52), drink (3) and soap (1) |
| `masterdyeing.txt` | MasterDyeing (339) ← MasterPaintingSkill (334) | 41 | Pattern-coloured dye recipes. Has 15 comment lines |
| `masterlacquering.txt` | MasterLacquerring (355) ← MasterPaintingSkill | 41 | Same pattern language, for lacquer |
| `masterleatherworking.txt` | MasterLeatherWorking (760) | 55 | Master leather goods. Same item names as `leatherworking.txt`, with higher armour values |
| `welding.txt` | Welding (103) ← BuildingSkill | 21 | Walls, doors and roofs for metal buildings |
| `leatherworking.txt` | LeatherWorking (731) | 55 | Leather armour and gear, levels 1-19 |
| `taxidermy.txt` | Taxidermy (291) | 12 groups, 69 lines | Emote poses, one group per mood |
| `foodprep.txt` | FoodPrep (197) ← Cooking | 52 | Basic ingredients: dough, flour, cheese |
| `textiling.txt` | Textiling (471) | 17 | Cloth bolts (GenResource), levels 1-30 |
| `shapeshift.txt` | Druid_ShapeShift (760) | 12 forms, 5 tiers each | Shapeshift forms |
| `hunting.txt` | Hunting (389) | 82 | Game drops by resource and habitat. 78 usable rows and 4 malformed |
| `herbalism.txt` | Herbalism (561) | 36 | Herbal brews: a spell and its herbs. Byte-identical to `improvedherbalism.txt` |
| `herbology.txt` | Herbology (267) | 31 | Herb names |
| `torturesmith.txt` | Torturesmithing (469) | 13 | Restraint and punishment furniture and devices, levels 15-25 |
| `traps.txt` | Thief_Trap (443) | 41 | Traps: trap class and level |
| `fishing.txt` | Fishing (275) | 59 | Catch table by water resource. 55 usable rows |
| `weaving.txt` | Weaving (672) | 25 | Baskets, mats, bags, rope, levels 1-30 |
| `gaolfood.txt` | GaolFood (118) ← Cooking | 19 | Prison food: spoiled, infested or mouldy items |
| `mycology.txt` | Mycology (309) | 43 | Mushroom types with weights, values and spells |
| `insultd.txt` | Skill_Joke (184) | 63 words | Descriptive words for jokes |
| `insulto.txt` | Skill_Joke (184) | 60 words | Object words for jokes |
| `floristry.txt` | Floristry (232) | 52 | Flower names. Has duplicates |
| `racialmounts.txt` | Fighter_RacialMount (330) | 20 | Mount table per race, with a `*` default |
| `mastersmokerings.txt` | MasterSmokeRings (65) ← SmokeRings | 10 | Smoke-ring phrases, master tier |
| `smokerings.txt` | SmokeRings (239) | 10 | Smoke-ring phrases |
| `urchins.txt` | Thief_NameUrchin (220) | 62 | Nicknames for urchin companions |
| `mtraps.txt` | Thief_MinorTrap (70) ← Thief_Trap | 5 | Minor traps |
| `mastercostume.txt` | MasterCostuming (94) ← Costuming | 0 (empty file) | No recipes |

### Ships (shipbuilding.cmare)

The file holds 63 ship prototypes, each stored as a `GenSailingShip` item with its full interior: rooms, exits, a boardable area and properties. Every record has the same ride and material fields. The ship's name is written with the rename token `[NEWNAME]` (`GenSailingShip.java:58`; token list in `Boardable.java:122-123`). The `ability` field has values 1 to 5. Value 2 is most common, with 37 ships. Levels run from 1 to 91. Sample records, with values taken from the file's own fields:

| Ship | Level | Value | Weight (materials) | Rooms |
|---|---|---|---|---|
| the penteconter | 1 | 3,000 | 4,800 | 3 |
| the felucca | 4 | 28,000 | 2,400 | 2 |
| the castleship | 18 | 196,000 | 14,400 | 8 |
| the small caravel | 72 | 637,500 | 7,200 | 6 |
| the medium galleon | 64 | 1,240,000 | 14,700 | 14 |
| the very large merchantman | 48 | 524,000 | 32,300 | 22 |
| the very large man-of-war | 91 | 5,197,500 | 21,000 | 20 |

Each record also has a `PRICE` of 1000 and a material code of 1328, identical across all 63. Their meanings are not traced. The lightest weights are 2,400 and the heaviest 32,300. Values rise steeply with level.

### Painting and colour

- `painting.txt` has 910 rows. Each is a named piece built from a style adjective and a medium, such as "elvish sketch" or "impressionist splatter-painting". Style families come in groups of 26 rows, and the level runs from 2 to 87. The verb column chooses one of five medium words.
- 650 rows carry a zapper mask in column 8. They restrict who can make the piece by race, race category or gender. The masks are `-RACE +ELF`, `-RACECAT "+FAIRY-KIN"` and `-GENDER +FEMALE`, among others. There are 25 distinct masks, each on 26 rows (the rule is `Painting.java:277`).
- `lacquering.txt` and `dyeing.txt` hold 451 colour names, each with a colour code: a CoffeeMud `^x` code or an ANSI-256 `^#nnn` code. Examples include `red`, `green` and `boysenberry-colored`. The colour is set by `RCP_COLOR` (`PaintingSkill.java:58`).
- The pattern language in `masterdyeing.txt` and `masterlacquering.txt` is described in comments at the top of each file. Symbols are `1`, `2`, `3` for colour slots, `w` to end of word, `W` for a fixed-length word, `|` for alternatives, `e` `E` `F` `c` `C` `m` for skip rules, `-` to step back and `*` to stop. Samples: `solid 1 10 "solid @x1" 1E1w` and `pinstriped 3 10 "@x1 and @x2 pinstriped" 1Cc21*`. A recipe needs three colours if its mask contains `3`, otherwise two (`MasterPaintingSkill.java:291-296`).

### Weapons, armour and tiers

- Weapon tiers are separate files. `weaponsmith.txt` covers levels 0-30 with items such as "% butter knife", "% club", "% khopesh" and "% dizzy boff stick". `masterweaponsmith.txt` covers levels 30-60 with "master" prefixes. `legendaryweaponsmith.txt` covers levels 60-90 with "legendary" prefixes. Each has 117 `GenWeapon` rows and 17 `GenSpear` rows.
- `fletching.txt` has tier words built into the names. "crude % atlatl" is level 4, "basic" 19, "expert" 43, "master" 67 and "legendary" 91. The 151 ammunition rows are the bulk of the file.
- `masterarmorsmith.txt` builds names from three parts: a prefix ("Spiked", "Banded"), the material, and the garment. Examples are "Spiked % Banded Armbands" at level 12 and "Spiked % Banded Vest" at level 13. It has 222 `GenArmor` and 8 `GenShield` rows.
- `leatherworking.txt` and `masterleatherworking.txt` share item names. The master tier raises armour values, for example `% cestus` from 1 to 3 in the armour column.

### Cooking, drink and poison

- Food uses ingredient lists. "fried eggs" (level 2) needs eggs. "pancakes" (level 11) needs wheat, water and eggs. Soups list optional vegetables with negative amounts. "tomato soup" (level 13) requires tomatoes and water and optionally takes greens, carrots, potatoes, corn and tomatoes.
- `recipes.txt` includes "coffee", a drink that carries `*Poison_Caffeine`.
- `liquors.txt` has 97 drinks. Most carry `Poison_Alcohol` (54 rows) or `Poison_Liquor` (41 rows), and one carries `Poison_Firebreather`. Early entries include "ale" (level 1), "beer" (2), "blood wine" (3) and "mead" (3).
- `gaolfood.txt` is prison food: "spoiled dough" (1), "infested flour" (2), "stale water" (3), "moldy cheese" (4).
- `poisons.txt` has 72 poison drinks, 10 perfumes (`GenPerfume`), and 35 data-defined poisons. The 35 come from five strength tiers times seven kinds. The tiers are lesser (TICKS 24, DELAY 2), moderate (27, 3), greater (30, 5), enduring (50+10*x, 5) and concentrated (8, 4). The kinds are a plain toxin and six stat-debuffs: weakening (Str), sickening (Con), quavering (Dex), foggening (Wis), dulling (Int) and churlening (Cha). Each debuff carries an `ADJUSTMENTS=prop_adjuster` clause.
- The named single poisons (for example "flysting venom", which applies `Poison_Sting`, and "beesting venom", which applies `Poison_BeeSting`) are ordinary `DRINK` recipes.

### Gathering tables

- Fishing (`fishing.txt`). Catches are grouped by water resource: Shrimp (8 usable rows: shrimp, lobster, crab, mussels, oysters, prawn, king crab, snow crab), Salmon (7 rows: salmon, crappie, bluegill, freshwater drum, whitefish, walleye, perch), Catfish (8 usable rows, mostly bass species), Tuna (24 usable rows: swordfish, pompano, albacore, bonito, cod, halibut, bluefin tuna, grouper, blue marlin, mackerel and others), Trout (4 usable rows, plus the unusable "Tiger Trout") and Carp (4 usable rows, plus the unusable "Carp"). The unusable rows are described in Problems.
- Hunting (`hunting.txt`). 78 usable rows across 15 resources, led by FISH (17), HIDE (12), TUNA (9) and SHRIMP (9). Habitat filters use the room domain names `UnderSaltWater` (18 rows), `SaltWaterSurface` (9), `Jungle`, `Swamp`, `UnderWater`, `WaterSurface`, `FrozenPlains`, `IcePlains` and `Shore`. A `*` means any habitat. The 34 mob IDs include Sheep, Cow, Bull, Doe, Buck, Gorilla, Wolf, Shark, Swordfish, Whale and Walrus. A mob ID is resolved as a catalogue mob, then a race, which becomes a generic mob or rideable (`Hunting.java:300-330`).
- Mycology (`mycology.txt`). 43 mushrooms with a frequency weight that totals 616. The commonest frequency is 5, shared by 20 entries. "button" has frequency 1 and a value multiplier of 100, so it is the rarest and most valuable. Seven species carry a poison spell. Death cap and destroying angel apply `Poison_Heartstopper`. Lepidella, foolshroom, webcap, false morel and false parasol apply `Poison_Sickening`.
- Herbs and flowers. `herbology.txt` lists 31 herbs (angelica, mustard, anise, myrrh, cassia, peppermint, chamomile, poppy, cloves, rosemary and others). `floristry.txt` lists 52 flowers, including duplicates (see Problems).
- Herbal brewing (`herbalism.txt`). Each row names a spell and the herbs it needs. "Chant_PredictWeather" (level 1) needs CARROTS and HERBS. "Chant_HardenSkin" (level 1) needs PINE and HERBS. "Chant_LocatePlants" (level 2) needs GREENS and HERBS. "Chant_SenseLife" (level 3) needs HEMP and HERBS. Ingredients are matched against the item name or the raw material name (`Herbalism.java:440-500`).

### Prophecy scripts (predictions.xml)

`predictions.xml` is a RandomAreas document. The container `random_prediction` has `select="any-1"`, so one child script is picked at random, and each child is a MOBPROG program. A typical script has two parts. The `FUNCTION_PROG Prediction` block returns a vague sentence, which the player sees at once. One or more `RAND_PROG <n>` blocks run later with an n-percent chance, and they carry the real effect. Across the file, the most used MOBPROG verbs are `MPGSET` (431 uses), `MPSCRIPT` (108), `MPBEHAVE` (73), `MPECHOAT` (68), `MPOLOADROOM` (47), `MPFORCE` (36), `MPAFFECT` (29), `MPMLOAD` (28) and `MPALARM` (28).

Paraphrased examples:

- "good_money": a benefactor's gift of up to RANDNUM (1 to 1000) coins, applied at 50%, but only when the target is not fighting, idle or in a special room.
- "bad_money": the reverse. Coins are taken, guarded the same way.
- "bad_dragon_fireball": a warning that the player will be tested by fire. The script loads a red dragon that spots the target and forces a firebreath, then applies a strength buff and removes the script.
- "bad_powerful_artifact": an artifact that may be cursed. "good_death_herb": a herb that will save the player's life at a critical moment.

Prophecy counts: 28 "good", 16 "bad" and 56 "meh" scripts. The "meh" group is mostly flavour. The two readers use the same ID, `RANDOM_PREDICTION`, and the same parser, so the scripts are shared by the prayer and the thief spell.

### Planes of existence (planesofexistence.txt)

The file defines 37 planes, one per quoted line. The header comment lists about 35 option keys. The main ones are:

- `CATEGORY`: Inner, Outer, Elite, Central, Lower, Higher, Transitional, Quasi.
- `OPPOSED`: a pair for each plane. Examples are Earth/Air, Fire/Water, Positive/Negative, Magma/Ice, Ooze/Smoke, Minerals/Vacuum, Dust/Lightning, Radiance/Salt, Ash/Steam, Nirvana/Limbo, Arcadia/Pandemonium, Seven Heavens/Abyss, Elysium/Hades and Gladsheim/Acheron.
- `ALIGNMENT`: values such as 2500, -2500, 5000, -10000 and 10000.
- `PROMOTIONS`: name and percent pairs such as `Stout(20),Solid(5),Dense(1)` for Earth. The percent is the chance to pick that name for a planar mob.
- `LIKE`, `ELITE`, `ADJSIZE`, `FACTIONS`, `ROOMADJS`, `ABSORB` and similar keys, documented in the header.

### Shapeshift forms and racial mounts

- `shapeshift.txt` has 12 forms, each with five tiers. Tier 1 is the first name and tier 5 the last. Rodent: Rat, Beaver, Porcupine, Dire Rat, WereBat. Feline: Kitten, Cat, Puma, Lion, Manticore. Canine: Puppy, Dog, Wolf, Dire Wolf, WereWolf. Bird: Robin, Owl, Hawk, Eagle, Harpy. Reptile: Garden Snake, Snake, Python, Cobra, Naga. Ursine: Cub, Black Bear, Grizzly Bear, Polar Bear, WereBear. Insect: Cricket, Millipede, Tarantula, Scarab, ManScorpion. Primate: Spider Monkey, Chimp, Ape, Gorilla, Sasquatch. Bovine: Calf, Cow, Buffalo, Bull, Minotaur. Fish: Clown Fish, Angelfish, Swordfish, Shark, Merfolk. Sea mammal: Seal, Walrus, Dolphin, Whale, Selkie. Mustelid: Badger, Otter, Wolverine, Dire Otter, Mustie.
- Each form has four multipliers (ATTADJ, DMGADJ, ARMADJ and SPEEDADJ) and lists of shapes and races. The rodent form, for example, has 0.7, 0.4, 1.0 and 0.2.
- `racialmounts.txt` maps a host race to a mount category and a mount race. Examples: Beastkin and Orc to Canine/DireWolf, Dwarf to Ursine/DireBear, Elf to Feline/DireCat, Gnome to Mustelid/DireOtter, Goblin to Rodent/DireRat, Ogre to Pachyderm/Elephant, Svirfneblin to Elemental/EarthElemental, Merfolk to Fish/GiantSeahorse. Human, Githyanki, Half-elf, Halfling and Tiefling take Equine/Horse. The `*` default is Equine/Horse.

### Songs, jokes, urchin names and taxidermy poses

- Songs. `songlyrics.txt` has six songs, each under a section header that matches a `Song_` class: PirateShanty (67 lines), Seeing (9), Valor (7), Death (7), Charm (7) and Babble (6). The sea-shanty opens with a familiar public-domain verse about a dead man's chest. The other songs are short rhymed couplets. Lines are sung one per tick, and `PAUSE n` lines give a pause.
- Jokes. `insultd.txt` (63 words) and `insulto.txt` (60 words) are word banks. Some entries are descriptive ("a rusty", "an out-of-tune", "a squodgy") and some are objects ("banana", "screwdriver", "piece of chipboard", "larch"). `Skill_Joke` picks one of seven joke shapes, such as a call-and-response riddle, a knock-knock joke and a "what do you call" pun. Each joke draws three object words and three descriptor words.
- Urchins. `urchins.txt` lists 62 nicknames for pet urchins, such as Dox, Gully, Skunk, Pockets, Fingers, Alley, Swindle, Ferret, Pinch, Pip, Sparrow and Grub. "random" picks one.
- Taxidermy poses. `taxidermy.txt` has 12 groups with 69 pose lines in total: Scary (6), Happy (7), Sneaky (5), Holy (7), Silly (9), Confused (5), Sexy (4), Proud (4), Quiet (5), Bashful (5), Misc (8) and Playful (4). Each line is an emote with `<S-NAME>` as the actor. The "Sexy" group is flirtatious gesture, paraphrased here and not reproduced.

### Smoke rings, scribing and paper

- Smoke rings. `smokerings.txt` and `mastersmokerings.txt` each hold 10 phrases: a shape (perfect, huge, tiny), its level and expertise gate, and a display string. A string beginning with `*` is an action sentence, such as "a train of tiny smoke rings" (`SmokeRings.java:136-155`).
- Scribing. `scribing.txt` has 238 spell-to-reagent pairs, for example Chant_AiryAura with sapphire and Chant_AnimalFriendship with fish. Scrolls are named "<item> of <spell>" (`ScrollScribing.java:221`).
- Paper. `papermaking.txt` includes 18 `GenPlayerBook` items. Their capacity column is written as pages/characters, for example `10/5000`. It also includes 8 `GenRecipe` items, whose material count sets the number of recipe pages (`PaperMaking.java:394-412`).

## Notable design ideas

- Self-describing prototypes. A ship is written once as a full item. Its recipe, with build time, cost and price, is derived from that item's stats. There is one source of truth for each ship.
- Positional rows with per-skill column maps. One parser serves products as different as cakes, cannons, poisons and sea vessels. Each skill declares its own columns.
- Numbered extension files. Content packs are added as `name.1`, `name.2` and so on, so base data never has to be edited (`CMFile.java:261-276`).
- Weighted gathering with habitat. Fishing and hunting rows carry a frequency and a habitat key. The same resource can behave differently in a swamp and in open sea.
- Random identification. A found mushroom or herb is named by a weighted roll and then stores its true identity in a secret field, so it stays consistent (`Mycology.java:150-192`; `Herbology.java:134-206`).
- Prophecy as a promise. The player gets a vague sentence now, and the real effect runs later through a scheduled script (`Prayer_Prophecy.java:139-189`). That creates dramatic irony that fits a text game.
- A small colour-pattern language. The mask grammar turns "1E1w" into a colour-patterned name. It is compact and testable.
- Audience masks on art. Painting restricts who can make a piece by race, category or gender using the common Zapper language.
- Five-tier shapes. Each shapeshift form has five stages, and the stage follows the druid's race level. Each stage has its own race and shape names.
- Song playback as data. Songs are plain lines with `PAUSE` tokens, played once per tick. Adding a song needs no code.
- Template jokes. Seven sentence templates and two word banks give a lot of variety from little data.

## Problems and cruft

Each item was confirmed by reading the code. None was run.

1. Fishing keys with trailing spaces. `fishing.txt:17` ("Carp "), `:22` ("Trout ") and `:36` ("Tuna ") have a space before the tab. The comparison at `Fishing.java:226` is `toLowerCase().equals(foundShortName)` with no trim, so these three rows can never match. That removes 3 of the 59 rows: the plain Carp, Tiger Trout and the plain Tuna.
2. A malformed fishing row. `fishing.txt:27` reads "Catfish 25" with a space instead of a tab. Its three fields fail the `subl.size() < 4` check at `Fishing.java:224`, so the row is dropped. The intended row is probably Catfish, frequency 25.
3. Malformed hunting rows. The last four rows of `hunting.txt` ("FUR" for Wolf, Buffalo, BrownBear and BlackBear) have an extra tab. That makes the domain column empty and the frequency column `*`, which parses as 0. The rows match no habitat, and no other FUR rows exist, so they are dead (`Hunting.java:273-281`).
4. Mycology weights are inflated. Each entry takes `freq + 1` slots, because `cur` advances by `v+1` (`Mycology.java:162-164`). The 20 entries with frequency 5 therefore behave like frequency 6. The roll `roll(1, max, 0)` never returns 0 (`:179`), so the first slot is lost.
5. Wrong variable in `UniPoison.setMiscText`. Line 186 reads `CMath.s_double(map.get(newMiscText))`, but it should read `map.get(code)`. The key `RANK` is therefore never read. The lookup returns null and `CMath.s_double` falls back to 0.0 (`CMath.java:2016-2024`).
6. Stray apostrophe in a joke. The seventh joke template ends with `!'` (`Skill_Joke.java:156`). The output has an unmatched quote.
7. Name check misses position 0. `Thief_NameUrchin.java:179` tests `indexOf(myName) > 0`. A name at the start of the current name is not caught as "already named".
8. Duplicate flower names. `floristry.txt` lists "daisies" at lines 7 and 42 and "antheriums" at lines 16 and 49. Duplicates change the odds without adding anything.
9. Misspelled flower names. `floristry.txt:6` "calalilies" (calla lilies), `:13` "ponsiettas" (poinsettias), `:16` "antheriums" (anthuriums). `:14` "saladego" is not a name I could identify.
10. Duplicate tables. `alchemy.txt` and `improvedalchemy.txt` are byte-identical (4,038 bytes each). `herbalism.txt` and `improvedherbalism.txt` are byte-identical (1,633 bytes each). `lacquering.txt` and `dyeing.txt` differ only by a final newline. `masterlacquering.txt` and `masterdyeing.txt` share a 41-row structure. `leatherworking.txt` and `masterleatherworking.txt` share item names. Each pair is a maintenance risk.
11. An empty file. `mastercostume.txt` is 0 bytes, so MasterCostuming has no recipes. The error is suppressed for Costuming IDs (`CraftingSkill.java:234-238`; `CommonSkill.java:312-314`), so the empty load is silent.
12. Derived file written at runtime. `Shipwright.loadRecipes` writes a derived `shipbuilding.txt` into the skills path with `F.saveText` (`Shipwright.java:157-179`). The stored name is `Item.Name()`, which still includes the `[NEWNAME]` token (`GenSailingShip.java:58`).
13. Very large ship material costs. The materials column is the full PhyStats weight. A penteconter needs 4,800 wood units, and the largest ship needs 32,300 (`Shipwright.java:898-907`). This may be deliberate, but I did not verify the intent.
14. Duplicate parsers. `predictions.xml` is parsed by two classes with near-identical code, `Prayer_Prophecy.java:139-160` and `Thief_Runecasting.java:339-365`. The only difference is the key read: `STRING` at line 160 and `SCRIPT` at line 365.
15. Overloaded negative amounts. In cooking, a negative amount means optional for matching (`Cooking.java:747-750`) but means "consume the absolute value" for use (`:563`). The same column carries two meanings.
16. Planes editor writes to the last file. `addOrEditPlane` appends new planes to the last numbered extension file (`StdPlanarAbility.java:368`). The code comment says "last one is always correct", which holds only while extension numbering is contiguous.

## Takeaways for a Python SUD

- Recipes as typed records. Replace positional columns with a dataclass per craft, with named fields and a schema check at load time. The bugs above (trailing spaces, short rows, dead FUR rows) would all be caught at load.

  ```python
  @dataclass(frozen=True)
  class Recipe:
      name: str            # "% butter knife": % is replaced by the material
      level: int
      build_ticks: int
      materials: int       # raw units; or a named component set
      value: int
      kind: str            # e.g. "weapon", "armour"
  ```

- Items once, recipes derived. Keep one item definition, such as a ship or a sword, and compute its recipe from it: `materials = weight`, `build_ticks = weight // 10`, `value = base_value`. Drop the duplicate tables.
- Weighted gathering with habitat. Use `random.choices` over rows that match the current habitat, with a seeded RNG for repeatable tests. Keep the `*` wildcard. Drop the range-map trick.
- Prophecies as data. Model each prophecy as `summary`, `delay` (a random number of days or hours), and a list of effects. Deliver the summary now and apply the effects when the game clock reaches the delay. Rewrite the MOBPROG-style effects as plain Python callables. Any effect that touches other mobs (MPFORCE or MPECHOAT aimed at a third party) needs rethinking in a single-player world, and random room loads need a clear owner.
- Forms with tiers. Model the shapeforms as five-stage records, each with a name and modifiers that scale with level. The arithmetic in `Druid_ShapeShift.java:323-327` is simple enough to copy directly.
- Songs and banks. Store lyrics, joke words and urchin names as plain lists in TOML or YAML. Keep the `PAUSE` idea as a tick-counted pause.
- Drop. The XML `.cmare` format, the numbered-file extension mechanism (unless you want content packs), the colour-pattern grammar (use simple templates like `"{c1} and {c2} striped"`), the Zapper mask language (use small predicate lists such as `races_allowed` and `genders_allowed`), the in-game planes editor, the Wish exclusion lists (keep the idea of a per-spell exclusion set, not the file), and the clan shipwright and clan crafting paths.
- Keep. Planes as a small list of `Realm` records with an `opposed` field and a few modifiers. Racial mounts as a dict from race to mount. Smoke rings and taxidermy poses as emote banks. The map from skills to their item stats as a pure function.
- Time. The CoffeeMud tick is 4 seconds (`CMProps.java:681`). In a turn-based SUD, convert recipe ticks into game turns or minutes. In a real-time SUD, keep the same tick idea and derive durations with the skill formula from `CommonSkill.java:346-360`.

## Coverage

Java, read in the ranges named (not every line):

- Read: `core/interfaces/RecipeDriven.java` (whole file); `Libraries/CoffeeUtensils.java` 100-330; `Abilities/Common/CraftingSkill.java` 170-320, 949-1052, 1298-1330, 2230-2258; `Abilities/Common/CommonSkill.java` 84-87, 201-209, 293-318, 346-360; `Abilities/Common/Fletching.java` 78-97 and 253-578 (whole `autoGenInvoke`); `Abilities/Common/Weaponsmithing.java` 530-575; `Abilities/Common/Shipwright.java` 76-200, 892-975; `Abilities/Common/Cooking.java` 108-140, 540-606, 738-750; `Abilities/Common/Apothecary.java` 90-160; `Abilities/Poisons/UniPoison.java` 170-200; `Abilities/Common/Painting.java` 84-150 and 268-280; `Abilities/Common/PaintingSkill.java` 40-60 and 136-166; `Abilities/Common/MasterPaintingSkill.java` 62-90, 270-320; `Abilities/Common/Lacquerring.java` 150-240 (partial); `Abilities/Common/Mycology.java` 95-200; `Abilities/Common/Fishing.java` 205-262; `Abilities/Common/Hunting.java` 258-310; `Abilities/Common/Herbology.java` 89-206; `Abilities/Common/Herbalism.java` 430-500; `Abilities/Thief/Thief_Trap.java` 95-175 and 386-412; `Abilities/Thief/Thief_NameUrchin.java` 150-185; `Abilities/Prayers/Prayer_Prophecy.java` 130-200; `Abilities/Thief/Thief_Runecasting.java` 339-375; `Abilities/Songs/Song.java` 245-372; `Abilities/Songs/Skill_Joke.java` 96-175; `Abilities/Common/Taxidermy.java` 129-200; `Abilities/Common/SmokeRings.java` 96-186; `Abilities/Common/ScrollScribing.java` 83-85, 221, 317-345; `Abilities/Common/PaperMaking.java` 394-412; `Abilities/Common/BuildingSkill.java` 100-175; `Abilities/Druid/Druid_ShapeShift.java` 98-250, 320-330, 417-446; `Abilities/Fighter/Fighter_RacialMount.java` 130-190; `Abilities/Spells/Spell_Wish.java` 97-175; `Abilities/StdPlanarAbility.java` 355-470 and 1565-1640; `core/CMFile.java` 261-276; `Libraries/CoffeeMaker.java` 2299-2302 and 5490-5503; `Items/Basic/GenSailingShip.java` (name token only); `Items/interfaces/Boardable.java` 112-130.
- Skimmed: the rest of `Shipwright.java`; `Weaving`, `Textiling`, `Pottery`, `Sculpting`, `GlassBlowing`, `ScrimShaw`, `JewelMaking`, `InstrumentMaking`, `Landscaping`, `Masonry`, `Irrigation`, `Welding`, `Gunsmithing`, `Siegecraft`, `Torturesmithing` and the armour and leather classes, by their `getRecipeFormat` and `loadRecipes` headers only.

Data, all 65 files:

- Parsed by script for row counts, column counts, level ranges, group counts and duplicate keys. The script also searched the whole bucket for sexual or minor-related terms. Every hit was benign on reading: "children" as a ship-data tag, "boy" and "baby" inside names such as boysenberry, baby-blue and baby carriage, "rape" inside grape, "infant" inside infantry, "kid" inside a shirt name, "naked" in a taxidermy message, "teen" inside fifteen, "minor" inside a prayer name, and "pregnan" inside the spell ID `Chant_SensePregnancy`, which is not described.
- Read directly: `mastercostume.txt` (empty); `mtraps.txt`; `racialmounts.txt`; `planesofexistence.txt` (header and all 37 entries); `predictions.xml` (all 100 script IDs and their return lines, plus a sample of effects); `shipbuilding.cmare` (all 63 records parsed for their fields); `shapeshift.txt`; `wish.txt` (section structure and counts); `songlyrics.txt` (section structure); `fishing.txt`, `hunting.txt`, `mycology.txt` and `poisons.txt` (every row summarised by script); `taxidermy.txt` (group headers and counts); `herbalism.txt` and `herbology.txt` (sample rows and names); `floristry.txt` and `urchins.txt` (names and duplicates); `insultd.txt` and `insulto.txt` (sampled).
- Sampled or summarised: `painting.txt`, `lacquering.txt`, `dyeing.txt`, `masterlacquering.txt`, `masterdyeing.txt`, the weapon and armour files, `jewelmaking.txt`, `staffmaking.txt`, `scrimshaw.txt`, `glassblowing.txt`, `sculpting.txt`, `pottery.txt`, `papermaking.txt`, `instruments.txt`, `wainwright.txt`, `scribing.txt`, `smokerings.txt`, `mastersmokerings.txt`, `liquors.txt`, `recipes.txt`, `foodprep.txt`, `gaolfood.txt`, `traps.txt`, `leatherworking.txt`, `masterleatherworking.txt`, `masonry.txt`, `irrigation.txt`, `welding.txt`, `landscaping.txt`, `torturesmith.txt`, `weaving.txt`, `textiling.txt`, `smelting.txt`, `siegecraft.txt`, `gunsmith.txt`, `tailor.txt`, `mastertailor.txt`, `rodsmithing.txt`, `wandmaking.txt`, `shardmaking.txt`, `masterarmorsmith.txt`, `fletching.txt`, `improvedalchemy.txt` (identity with `alchemy.txt` checked by `cmp`).
- Skipped: the body of `CraftingSkill.matchingRecipes` (l.1409-1757); `CMLib.ableComponents` (components are in c04); the `Trap_*`, `Poison_*` and ClanShipwrighting classes; the `@x1`-`@x3` substitution in poison formulas; `GenSailingShip` behaviour beyond its name token; `CoffeeMaker` beyond the two serialisation functions.
