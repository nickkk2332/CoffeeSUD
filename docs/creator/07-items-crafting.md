# Items, crafting, gathering, homes and puzzles

[Handbook](README.md) · Feature families [I](feature-index.md#i), [CR](feature-index.md#cr)

This chapter is for world creators and scripters. It covers four connected
areas of CoffeeSUD content:

1. **Crafting**: the common skills players use to make things, the recipe files
   they read, and the CoffeeSUD additions (recipe requirement keys,
   workstations, quality tiers, experiments, salvage, known recipes, craft XP,
   data-defined skills).
2. **Gathering**: mining, foraging, fishing, farming and the rest; resource
   tables, yields, depletion, seasons and crops.
3. **Housing and homesteads**: property titles, building without a deed, a home
   the whole party can use, furnishings.
4. **Interactive items and puzzles**: switches, locks, receptacles, riddles,
   transformations, attunement, growth, item sets, curses, invokable items and
   the other item behaviour you can attach without Java.

Item basics (item classes, fields, materials as an item field, wear locations,
the item editor) are in [22-items.md](22-items.md). Parameter details for each
property are in [23-properties-reference.md](23-properties-reference.md) and
for behaviours in [24-behaviors-and-masks.md](24-behaviors-and-masks.md); this
chapter explains how to build with them and repeats the parameters that matter.
The opt-in tracked modifier workshop is a separate system:
[17-item-modifiers-crafting.md](17-item-modifiers-crafting.md).

## In this chapter

- [How crafting works](#how-crafting-works)
- [Crafting and learning recipes](#crafting-and-learning-recipes)
  - [Where recipe files live](#where-recipe-files-live)
  - [Row syntax](#row-syntax)
  - [Recipe files by skill](#recipe-files-by-skill)
  - [Common columns](#common-columns)
  - [MATERIALS_REQUIRED and components](#materials_required-and-components)
  - [Editing and checking recipes in the game](#editing-and-checking-recipes-in-the-game)
  - [Recipe requirement keys](#recipe-requirement-keys)
  - [Recipe books, known recipes and learning](#recipe-books-known-recipes-and-learning)
  - [Experiments](#experiments)
  - [Workstations, tools and fire](#workstations-tools-and-fire)
  - [Quality tiers](#quality-tiers)
  - [Craft and gather XP](#craft-and-gather-xp)
  - [Salvage and dismantling](#salvage-and-dismantling)
  - [Data-defined skills](#data-defined-skills)
- [Materials and resources](#materials-and-resources)
- [Resources and gathering](#resources-and-gathering)
  - [Gathering skills](#gathering-skills)
  - [Where resources come from](#where-resources-come-from)
  - [Seasons and hours](#seasons-and-hours)
  - [Yield, depletion and regrowth](#yield-depletion-and-regrowth)
  - [Hunting, fishing, herb and mushroom tables](#hunting-fishing-herb-and-mushroom-tables)
  - [Crops and farming](#crops-and-farming)
- [Housing, construction and furniture](#housing-construction-and-furniture)
- [Item definitions and powers](#item-definitions-and-powers)
- [Puzzle primitives](#puzzle-primitives)
  - [Actions](#actions)
  - [Prop_Switch](#prop_switch)
  - [Prop_PressurePlate](#prop_pressureplate)
  - [Prop_ComboLock](#prop_combolock)
  - [Receptacles: Prop_LimitedContents](#receptacles-prop_limitedcontents)
  - [Riddler](#riddler)
  - [Prop_Discoverable](#prop_discoverable)
  - [Prop_BlockedExit](#prop_blockedexit)
  - [Prop_Detector](#prop_detector)
  - [Keys and locks](#keys-and-locks)
  - [USE and COMBINE](#use-and-combine)
  - [Traps](#traps)
  - [The PUZZLE command](#the-puzzle-command)
  - [Designing a puzzle](#designing-a-puzzle)
- [Changing items: transformation, growth, attunement and more](#changing-items-transformation-growth-attunement-and-more)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting](#troubleshooting)
- [Reference: ini keys and world files](#reference-ini-keys-and-world-files)
- [See also](#see-also)

## How crafting works

A **common skill** is a skill any character can learn (they are in the
`Abilities/Common` folder) as opposed to a class spell or prayer. The ones this
chapter deals with fall into families.

| Family | What it does | Examples |
| --- | --- | --- |
| Crafting | Makes an item from raw materials or components, using a recipe file | Blacksmithing, Weaponsmithing, Armorsmithing, Carpentry, Tailoring, LeatherWorking, Pottery, JewelMaking, Fletching |
| Cooking and brewing | Makes food and drink from ingredients | Cooking, Baking, FoodPrep, Distilling; Alchemy and Herbalism make potions |
| Gathering | Finds raw resources in the room it is used in | Mining, Chopping, Foraging, Fishing, Hunting, Farming, Gardening |
| Building | Changes rooms and exits on land the builder may build on | Construction, Masonry, Excavation, Landscaping, Irrigation, Welding |
| Wrighting | Makes large transports from a `.cmare` recipe file | Shipwright, CaravanBuilding |
| Other | Take apart, mend, scribe, paint | Scrapping, Salvaging, Painting, Dyeing, ScrollScribing, WandMaking |

Every crafting skill reads a **recipe file**: one tab-separated line per thing
it can make. A recipe line names the item, its level, how long it takes, the
materials it needs, its value, its item class and a few class-specific values
(wear location, damage, capacity...). The skill builds the item from the line,
sets its material from what the player supplied, and drops it in the room.

What a player types depends on the skill. Most crafting skills answer to a
command word of their own (BLACKSMITH, WEAPONSMITH, CARVE for Carpentry, KNIT
for Tailoring, COOK, BREW for Alchemy, FLETCH, ENSCRIBE for ScrollScribing, and
so on; the word is the skill's first trigger, shown by `AHELP [SKILL]`). All the
item crafting skills share these sub-commands:

| Command | Effect |
| --- | --- |
| `[SKILL] LIST` (optionally `LIST [WORDS]` or `LIST ALL`) | Lists the recipes the crafter's level allows (`ALL` also lists those above their level) |
| `[SKILL] [ITEM]` or `[SKILL] [MATERIAL] [ITEM]` | Starts making the item. A material is needed when the item can be made from several |
| `[SKILL] INFO [ITEM]` | Shows what the item would be like and how long it takes, without making it |
| `[SKILL] LEARN [ITEM]` | Takes an item apart to write its recipe into a recipe book (see [learning by taking apart](#learning-by-taking-apart)) |
| `[SKILL] BUNDLE ...` | Bundles raw material (skills that can bundle; type the command for its usage line) |
| `[SKILL] MEND [ITEM]`, `REFIT [ITEM]` | Repairs, or resizes for another race (skills that can mend and refit) |
| `[SKILL] STOP` | Cancels the work |
| `[SKILL] EXPERIMENT [THING]...` | CoffeeSUD: try materials together to discover a hidden recipe |
| `[SKILL] DISMANTLE [ITEM]` | CoffeeSUD: take an item apart for materials |

`EXPERIMENT` and `DISMANTLE` are handled in `CraftingSkill.checkInfo`, so the
skills that call it respond to them. Individual skills differ in small ways
(the exact usage line appears when you type the command with no arguments). The
engine-wide parts of the skills (`CommonSkill`, `CraftingSkill`,
`EnhancedCraftingSkill`) are in
[Abilities/Common](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Common/).

Crafting is slowed or sped by level difference to the item, by the
EnhancedCraftingSkill expertises (more speed, quality and durability stages
that a character buys), and in CoffeeSUD by a [workstation](#workstations-tools-and-fire).
A crafter whose level is below the recipe's level cannot make it unless a
workstation's `MAXLEVEL` lets them. Skills and expertises are covered in
[25-abilities-and-effects.md](25-abilities-and-effects.md) and
[03-characters.md](03-characters.md).

## Crafting and learning recipes

### Where recipe files live

Stock recipe files are under `resources/skills/` (the folder is
`CoffeeMud/resources/skills/`). A skill asks for its file by name, for example
Blacksmithing asks for `blacksmith.txt`. These rules apply to every recipe file:

- **A world can replace a stock file.** A file at
  `world/resources/skills/blacksmith.txt` is read instead of the stock
  `resources/skills/blacksmith.txt` (`CMFile` prefers the world's copy of any file
  under `resources/`). Copy the stock file first, then edit the copy, or the
  skill will have only your lines.
- **A world can add to a stock file.** The loader reads `blacksmith.txt`, then
  `blacksmith.txt.1`, `blacksmith.txt.2` and so on up to `.9` and concatenates
  their rows (`CMFile.getExistingExtendedFiles`). Put your extra recipes in
  `world/resources/skills/blacksmith.txt.1` and the stock rows stay. This is the
  safest way to extend a shipped skill, because stock updates still apply. If
  the numbering skips (`.1` and `.3` but no `.2`), reading stops at the gap after
  `.1`; keep the numbers consecutive.
- **Rows are sorted by level.** Skills that extend `EnhancedCraftingSkill` (most
  item crafting skills) and `CraftingSkill` sort the loaded rows by their level
  column, lowest first, so file order does not matter for the player's list.
- **Recipes are cached.** The parsed file is cached under
  `PARSED_RECIPE: [file]` for the life of the process. Saving through
  `MODIFY RECIPE` clears the cache for that file; a file you edited by hand is
  picked up at the next start.
- **MODIFY RECIPE edits the main file only.** It reads and rewrites
  `blacksmith.txt` (the world's copy if there is one), not the `.1`-`.9`
  extension files, so rows that live in an extension are not shown there. In
  builder mode, a write to a file under `resources/` goes to the world's copy
  (`world/resources/skills/`), never the stock folder.
- A skill that finds no rows logs `Recipes not found!` (except the data skills
  and Costuming, whose files may be empty).

### Row syntax

- One recipe per line; columns separated by **TAB** characters. A space is not a
  separator, so editors that turn tabs into spaces will silently break a file.
- A line whose first character is `#` is a comment, and so is a `#` line after
  any newline. Blank lines are ignored. A line with no tab in it is ignored.
- Rows shorter than the longest row in the file are padded with empty columns;
  the loader works out the width from the longest line. Keep every row the same
  width to be safe. A recipe that comes from a recipe *book* or a character's
  known recipes (see below) is padded with empty cells when it is shorter than
  the skill's last row; a longer one is kept as it is.
- The first column is the item's final name. A `%` in it is replaced by the
  name of the material chosen, in lower case, so `% cup` becomes `iron cup`;
  without a `%` the name is used as written. In the `LIST` display the `%` is
  removed.
- Level is the second column in nearly every file. The level is both the
  level of the item made (adjusted for material hardness) and the least level
  of the crafter, unless a workstation raises their limit.
- Empty columns are legal and mean "none". A column that does not apply to an
  item class still has to exist as an empty cell.

### Recipe files by skill

The table lists the skill ID, its first command word where the class declares
one, and its recipe file. Skills that extend another share its code but have
their own file. (`resources/skills/` has more files than this table: data lists
for gathering and lore, described in later sections.)

| Skill ID | Command word | Recipe file |
| --- | --- | --- |
| Armorsmithing | ARMORSMITH | `armorsmith.txt` |
| MasterArmorsmithing | MARMORSMITH | `masterarmorsmith.txt` |
| Blacksmithing | BLACKSMITH | `blacksmith.txt` |
| Weaponsmithing | WEAPONSMITH | `weaponsmith.txt` |
| MasterWeaponsmithing | MWEAPONSMITH | `masterweaponsmith.txt` |
| LegendaryWeaponsmithing | LWEAPONSMITH | `legendaryweaponsmith.txt` |
| Torturesmithing | TORTURESMITH | `torturesmith.txt` |
| Carpentry | CARVE | `carpentry.txt` |
| Wainwrighting | WAINWRIGHTING | `wainwright.txt` |
| Boatwright | BOATWRIGHTING | `boatwright.txt` |
| Cobbling | COBBLE | `cobbler.txt` |
| Tailoring | KNIT | `tailor.txt` |
| MasterTailoring | (see skill help) | `mastertailor.txt` |
| Costuming, MasterCostuming | COSTUME | `costume.txt`, `mastercostume.txt` |
| LeatherWorking, MasterLeatherWorking | LEATHERWORK, MASTERLEATHERWORKING | `leatherworking.txt`, `masterleatherworking.txt` |
| Weaving, Textiling | WEAVING, TEXTILING | `weaving.txt`, `textiling.txt` |
| Pottery | POT | `pottery.txt` |
| GlassBlowing | GLASSBLOW | `glassblowing.txt` |
| Sculpting | SCULPT | `sculpting.txt` |
| ScrimShaw | SCRIM | `scrimshaw.txt` |
| JewelMaking | JEWEL | `jewelmaking.txt` |
| Smelting | SMELTING | `smelting.txt` |
| Fletching, Gunsmithing, Siegecraft | FLETCH | `fletching.txt`, `gunsmith.txt`, `siegecraft.txt` |
| InstrumentMaking | INSTRUMENTMAKING | `instruments.txt` |
| WandMaking, ShardMaking | WANDMAKE, SHARDMAKE | `wandmaking.txt`, `shardmaking.txt` |
| StaffMaking, Rodsmithing | STAFFMAKE, RODSMITH | `staffmaking.txt`, `rodsmithing.txt` |
| PaperMaking | PAPERMAKE | `papermaking.txt` |
| ScrollScribing | ENSCRIBE | `scribing.txt` |
| Alchemy, ImprovedAlchemy | BREW, IBREW | `alchemy.txt`, `improvedalchemy.txt` |
| Herbalism, ImprovedHerbalism | HBREW, IHBREW | `herbalism.txt`, `improvedherbalism.txt` |
| Cooking and the cooks (Baking, FoodPrep, Distilling, GaolFood, DrugCutting, Apothecary) | COOK, BAKING, FOODPREPPING, DISTILLING, DRUGCUTTING, APOTHECARY | `recipes.txt`, `bake.txt`, `foodprep.txt`, `liquors.txt`, `gaolfood.txt`, `drugs.txt`, `poisons.txt` |
| Painting, Dyeing, Lacquerring (and Master versions) | PAINT, DYE, MASTERDYEING, MASTERLACQUERING | `painting.txt`, `dyeing.txt`, `lacquering.txt`, `masterdyeing.txt`, `masterlacquering.txt` |
| Embellishing, Decorating | (see skill help) | `embellishing.txt`, `decorations.txt` |
| SmokeRings, MasterSmokeRings | MASTERSMOKERINGS for the master | `smokerings.txt`, `mastersmokerings.txt` |
| BodyPiercing | (see skill help) | `bodypierce.txt` |
| ClanCrafting | CLANCRAFT | `clancraft.txt` |
| CageBuilding | BUILDCAGE | `cagebuilding.txt` |
| Construction, Masonry, Excavation, Landscaping, Irrigation, Welding | BUILD family | `construction.txt`, `masonry.txt`, `excavation.txt`, `landscaping.txt`, `irrigation.txt`, `welding.txt` |
| Shipwright, CaravanBuilding | SHIPBUILD | `shipbuilding.cmare`, `caravanbuilding.cmare` |
| GenCraftSkill, GenWrightSkill, GenBuildingSkill (data skills) | set in the skill | the file named in the skill's FILENAME |

Because the loader prefers the world's copy and reads `.1` to `.9` extensions,
a world can reshape any of these without touching the stock folders.

### Common columns

Each file has its own columns (`getRecipeFormat()` of the skill). The most
widely shared ones are:

| Column | Meaning |
| --- | --- |
| `ITEM_NAME` | Final name; `%` becomes the material name |
| `ITEM_LEVEL` | Level of the item, and the least level of a crafter |
| `BUILD_TIME_TICKS` | Base work time in ticks. One tick is `TICKTIME` milliseconds (4000 by default, 4 seconds). The crafter's level advantage and workstation `SPEED` shorten it |
| `MATERIALS_REQUIRED` | An amount of raw material, or a [component](#materials_required-and-components) definition |
| `ITEM_BASE_VALUE` | Value in the base currency before material and level adjustments |
| `ITEM_CLASS_ID` | The item class to create: `GenItem`, `GenWeapon`, `GenArmor`, `GenContainer`, `GenEmptyDrink`, `GenLantern`, `GenLimb`... Any generic item class that the skill can fill in (see [22-items.md](22-items.md)) |
| `LID_LOCK`, `STATUE`, `RIDE_BASIS`, `WEAPON_CLASS`, `CODED_WEAR_LOCATION`, `SMOKE_FLAG` | One shared column whose meaning depends on the item class: `LID` or `LOCK` for containers, `STATUE` for name-able statues, a rideable type for rideables (chair, table...), a weapon class (`DAGGER`, `SWORD`, `AXE`, `HAMMER`, `POLEARM`, `BLUNT`, `THROWN`, `FLAILED`, `STAFF`...), the wear locations for worn items, or `SMOKE` for smokeables |
| `CONTAINER_CAPACITY`, `WEAPON_HANDS_REQUIRED`, `LIQUID_CAPACITY`, `LIGHT_DURATION`, `MAX_WAND_USES`, `DICE_SIDES` | The numeric second-class value: capacity in pounds, hands for a weapon, liquid capacity, ticks a light burns, wand charges |
| `BASE_ARMOR_AMOUNT`, `BASE_DAMAGE` | Armour points or weapon damage |
| `CONTAINER_TYPE`, `ATTACK_MODIFICATION` | What a container accepts (`LIQUID`, `LIQUID\|EATABLES`, `KEYS`, `RAWMATERIALS`, `LID`...), or a weapon's attack bonus |
| `CODED_SPELL_LIST` | Properties and spells put on the item; see below |
| `KEY_VALUE_PARMS` | GenCraftSkill only: `KEY=value` stats for the item, plus the [requirement keys](#recipe-requirement-keys) |

Weapon files (`weaponsmith.txt`) spell the shared columns out:
`WEAPON_CLASS` (the weapon *skill* class, as DAGGER), `WEAPON_TYPE` (the damage
type: PIERCING, SLASHING, BASHING...), `BASE_DAMAGE`, `ATTACK_MODIFICATION`,
`WEAPON_HANDS_REQUIRED`, `MIN_MAX_RANGE` and `OPTIONAL_RESOURCE_OR_MATERIAL`.
The first rows of the stock file look like this (tabs shown as ` | `):

```text
% butter knife | 0 | 20 | 1 | 2 | GenWeapon | DAGGER | PIERCING | 1 | -10 | 1 | 0 |  |
% club | 1 | 20 | 12 | 15 | GenWeapon | BLUNT | BASHING | 3 | 0 | 1 | 0 |  |
```

The exact set of columns for a given skill is its `getRecipeFormat()`. These are
the formats in the code, one per recipe file type. They are listed in the
order the columns appear.

| Skill (file type) | Columns |
| --- | --- |
| Blacksmithing, GlassBlowing, ScrimShaw, Pottery, Sculpting and the other item crafts | `ITEM_NAME, ITEM_LEVEL, BUILD_TIME_TICKS, MATERIALS_REQUIRED, ITEM_BASE_VALUE, ITEM_CLASS_ID`, then 1 to 4 class-specific columns (see the table below), then `CODED_SPELL_LIST` |
| Armorsmithing, Cobbling | `ITEM_NAME, ITEM_LEVEL, BUILD_TIME_TICKS, MATERIALS_REQUIRED, ITEM_BASE_VALUE, ITEM_CLASS_ID, CODED_WEAR_LOCATION, CONTAINER_CAPACITY, BASE_ARMOR_AMOUNT, CONTAINER_TYPE, CODED_SPELL_LIST` |
| Carpentry | `... ITEM_CLASS_ID, LID_LOCK\|\|RIDE_BASIS\|\|WEAPON_CLASS\|\|CODED_WEAR_LOCATION\|\|SMOKE_FLAG\|\|STATUE, CONTAINER_CAPACITY\|\|WEAPON_HANDS_REQUIRED\|\|LIQUID_CAPACITY\|\|LIGHT_DURATION\|\|MAX_WAND_USES\|\|DICE_SIDES, BASE_ARMOR_AMOUNT\|\|BASE_DAMAGE\|\|REQ_RESOURCE_OR_MATERIAL, CONTAINER_TYPE\|\|ATTACK_MODIFICATION\|\|RES_SUBTYPE, CODED_SPELL_LIST` |
| Weaponsmithing (and Master, Legendary) | `... ITEM_CLASS_ID, WEAPON_CLASS, WEAPON_TYPE, BASE_DAMAGE, ATTACK_MODIFICATION, WEAPON_HANDS_REQUIRED, MIN_MAX_RANGE, OPTIONAL_RESOURCE_OR_MATERIAL, CODED_SPELL_LIST` |
| Tailoring, Weaving, Costuming | `... ITEM_CLASS_ID, WEAPON_TYPE\|\|CODED_WEAR_LOCATION\|\|RIDE_BASIS (Weaving also LID_LOCK), CONTAINER_CAPACITY\|\|WEAPON_HANDS_REQUIRED, BASE_ARMOR_AMOUNT\|\|BASE_DAMAGE, CONTAINER_TYPE, CODED_SPELL_LIST` |
| LeatherWorking (and Master) | `... ITEM_CLASS_ID, WEAPON_CLASS\|\|CODED_WEAR_LOCATION, CONTAINER_CAPACITY\|\|LIQUID_CAPACITY\|\|WEAPON_HANDS_REQUIRED\|\|MAX_WAND_USES, BASE_DAMAGE\|\|BASE_ARMOR_AMOUNT, CONTAINER_TYPE\|\|MIN_MAX_RANGE, CODED_SPELL_LIST` |
| JewelMaking | `... ITEM_CLASS_ID, STATUE\|\|CODED_WEAR_LOCATION, N_A, BASE_ARMOR_AMOUNT\|\|DICE_SIDES, OPTIONAL_RESOURCE_OR_MATERIAL, CODED_SPELL_LIST` |
| Smelting, Textiling | `... ITEM_CLASS_ID, RESOURCE_NAME, RES_SUBTYPE, CODED_SPELL_LIST` |
| Fletching, Gunsmithing, Siegecraft | `... ITEM_CLASS_ID, AMMO_TYPE, AMMO_CAPACITY, BASE_DAMAGE, MIN_MAX_RANGE, OPTIONAL_RESOURCE_OR_MATERIAL, CODED_SPELL_LIST, WEAPON_HANDS_REQUIRED` |
| WandMaking, ShardMaking | `... ITEM_CLASS_ID, MAX_WAND_USES, OPTIONAL_RESOURCE_OR_MATERIAL, OPTIONAL_RESOURCE_OR_MATERIAL_AMT, CODED_SPELL_LIST, WAND_TYPE` |
| StaffMaking, Rodsmithing | `... ITEM_CLASS_ID, MAX_WAND_USES, BASE_DAMAGE, OPTIONAL_RESOURCE_OR_MATERIAL, OPTIONAL_RESOURCE_OR_MATERIAL_AMT, CODED_SPELL_LIST, WAND_TYPE, WEAPON_HANDS_REQUIRED` |
| InstrumentMaking | `... ITEM_CLASS_ID, RIDE_CAPACITY\|\|CODED_WEAR_LOCATION, METAL_OR_WOOD, OPTIONAL_RACE_ID, INSTRUMENT_TYPE` |
| PaperMaking | `... ITEM_CLASS_ID, RESOURCE_OR_MATERIAL, LID_LOCK\|\|STATUE\|\|RES_SUBTYPE, CONTAINER_CAPACITY\|\|PAGES_CHARS, CODED_SPELL_LIST` |
| Torturesmithing | `... ITEM_CLASS_ID, CONTAINER_TYPE_OR_LIDLOCK_OR_RIDEBASIS\|\|WEAPON_CLASS\|\|CODED_WEAR_LOCATION, CONTAINER_CAPACITY\|\|LIQUID_CAPACITY\|\|MAX_WAND_USES, BASE_ARMOR_AMOUNT, WOOD_METAL_CLOTH, CODED_SPELL_LIST` |
| Wainwrighting | `... ITEM_CLASS_ID, LID_LOCK, CONTAINER_CAPACITY, RIDE_CAPACITY, CONTAINER_TYPE, RIDE_OVERRIDE_STRS, CODED_SPELL_LIST` |
| Boatwright | `... ITEM_CLASS_ID, RIDE_BASIS, RIDE_CAPACITY, CONTAINER_CAPACITY, RIDE_OVERRIDE_STRS, CODED_SPELL_LIST` |
| ClanCrafting | `ITEM_NAME, ITEM_LEVEL, BUILD_TIME_TICKS, RESOURCE_NAME_AMOUNT_MATERIAL_REQUIRED (twice), CLAN_ITEM_CODENUMBER, CLAN_EXPERIENCE_COST_AMOUNT, ITEM_BASE_VALUE, ITEM_CLASS_ID, CLAN_AREA_FLAG\|\|CODED_WEAR_LOCATION\|\|READABLE_TEXT, CONTAINER_CAPACITY, BASE_ARMOR_AMOUNT\|\|BOARDABLE_POP, CONTAINER_TYPE, CODED_SPELL_LIST, REQUIRED_COMMON_SKILL_ID` |
| Alchemy, ScrollScribing | `SPELL_ID, RESOURCE_NAME[, POTION_POWDER]` (Alchemy has the third column) |
| Herbalism | `SPELL_ID, ITEM_LEVEL, RESOURCE_NAME_OR_HERB_NAME` six times |
| Painting | `ITEM_NAME, ITEM_LEVEL, BUILD_TIME_TICKS, ITEM_BASE_VALUE, ACTIVE_VERB, ITEM_CLASS_ID, EXPERTISENUM, ZAPPERMASK, CODED_SPELL_LIST` |
| Dyeing, Lacquerring (PaintingSkill) | `ITEM_NAME, ITEM_LEVEL, BUILD_TIME_TICKS, COLOR_TERM, COLOR_MASK, EXPERTISENUM, KEY_VALUE_PARMS` |
| Embellishing | `DISPLAY_MASK, ITEM_LEVEL, BUILD_TIME_TICKS, NAME_MASK, MATERIALS_REQUIRED, ZAPPERMASK, CODED_SPELL_LIST` |
| Decorating | `ITEM_NAME, ITEM_LEVEL, BUILD_TIME_TICKS, ACTIVE_VERB, DISPLAY_MASK, XLEVEL, DECORATION_FLAG` |
| SmokeRings | `ITEM_NAME, ITEM_LEVEL, XLEVEL, DISPLAY_MASK` |
| BodyPiercing | `WEAR_LOC, ITEM_LEVEL, BODY_PART_DESC` (four times) |
| Cooking family | `ITEM_NAME, FOOD_DRINK\|\|RESOURCE_NAME, SMELL_LIST\|\|CODED_SPELL_LIST, ITEM_LEVEL`, then up to seven pairs of `RESOURCE_OR_KEYWORD, OPTIONAL_AMOUNT_REQUIRED` |
| Construction family | `ITEM_NAME, ITEM_LEVEL, BUILD_TIME_TICKS, MATERIALS_REQUIRED, OPTIONAL_BUILDING_RESOURCE_OR_MATERIAL, BUILDING_FLAGS, BUILDING_CODE, ROOM_CLASS_ID\|\|EXIT_CLASS_ID\|\|ALLITEM_CLASS_ID\|\|ROOM_CLASS_ID_OR_NONE, BUILDING_GRID_SIZE\|\|EXIT_NAMES\|\|STAIRS_DESC, PCODED_SPELL_LIST, BUILDING_NOUN, BUILDER_MASK, BUILDER_DESC` |
| Shipwright, Wright data skills | `ITEM_CMARE`: a `.cmare` file of the vessel templates (see [30-travel-vehicles-instances.md](30-travel-vehicles-instances.md)) |
| GenCraftSkill | `ITEM_NAME, ITEM_LEVEL, BUILD_TIME_TICKS, MATERIALS_REQUIRED, ITEM_BASE_VALUE, ITEM_CLASS_ID, LID_LOCK\|\|STATUE\|\|RIDE_BASIS\|\|WEAPON_CLASS\|\|CODED_WEAR_LOCATION\|\|SMOKE_FLAG, CONTAINER_CAPACITY\|\|WEAPON_HANDS_REQUIRED\|\|LIQUID_CAPACITY\|\|LIGHT_DURATION\|\|MAX_WAND_USES, BASE_ARMOR_AMOUNT\|\|BASE_DAMAGE, CONTAINER_TYPE\|\|ATTACK_MODIFICATION, CODED_SPELL_LIST, KEY_VALUE_PARMS` |

The `...` stands for the first five columns `ITEM_NAME, ITEM_LEVEL,
BUILD_TIME_TICKS, MATERIALS_REQUIRED, ITEM_BASE_VALUE` (so `ITEM_CLASS_ID` is
column 6). A `||` between two names means the one cell holds whichever applies
to the item class. For weapon and armour classes the wear locations and
weapon classes are the same words the item editor shows
([22-items.md](22-items.md)). Use `MODIFY RECIPE [SKILL]` to see each skill's
column titles live; the editor builds its prompts from `getRecipeFormat()`, so
it is always correct for the code you are running.

### CODED_SPELL_LIST

The spell column lists properties or abilities attached to the finished item.
Entries are separated by semicolons; an entry is an ability ID, optionally
followed by its parameters in brackets, as `Prop_WearAdjuster(dex+1)` or
`Prop_HaveResister(fire 10)`. An entry that starts with `*` is not tried as a
spell, it is put on the item as a property (the stock file uses
`*Prop_ImproveGather;amt=2 skills=Chopping,MasterChopping`, which makes the
crafted lumberjack saw a gathering tool). The Guide's field list adds that a
parameter equal to `__DELETE__` removes an effect the item already has
instead of adding one. In a Cooking row the same column holds the smell list
(for food) or the spell effects of the dish (`*Poison_Caffeine;`).

### MATERIALS_REQUIRED and components

`MATERIALS_REQUIRED` is one of three things:

1. **A number**, the amount (in weight units, one per pound of raw resource) of
   raw material the skill uses. Which materials count is the skill's own list
   (`supportedResourceString()`): Blacksmithing takes metal, Carpentry wood,
   Tailoring cloth. The player names a material if the item can be made from
   more than one.
2. **An ability ID** naming a definition in `resources/skills/components.txt`
   (the cell holds the ID; it is looked up in upper case), as the spells that
   need components do.
3. **A coded component string** written in the cell itself. It must start with
   `(` and has the syntax of the right-hand side of a `components.txt` line.

The component syntax, from the header of `components.txt`:

```text
SKILL_ID=([DISPOSITION]:[FATE]:[AMOUNT]:[COMPONENT ID]:[MASK])&&(...)||(...) "description"
```

| Part | Values |
| --- | --- |
| `DISPOSITION` | `inventory` (default), `have`, `held`, `worn`, `nearby`, `onground` |
| `FATE` | `consumed` (default), `kept`, or CoffeeSUD's `wear(N)`: kept, losing `N` uses (its condition) each time, and breaking when none remain |
| `AMOUNT` | How many (default 1) |
| `COMPONENT ID` | A material name (`cloth`, `metal`), a resource name (`cotton`, `iron`), a resource with a subtype (`cotton(bolt)`, `herbs(moonpetal)`), an item name to match, or CoffeeSUD's `TAG(name)` for an item carrying that tag |
| `MASK` | A zapper mask on the crafter: the component applies only to those who match |

`&&` joins parts that are all needed and `||` separates alternatives. A trailing
quoted text describes the requirement to the player. A trigger component
(`TRIGGER:[RITUAL]:[MASK]`) works for spells; a crafting recipe uses the
resource and item forms. CoffeeSUD adds the `wear(N)` fate and the `TAG()` form.
A tool that is `kept` and never worn out is the old behaviour; `wear(N)` is the
CoffeeSUD way to make a hammer eventually break. As the cell of a recipe, write
only the part after the `=`:

```text
(inventory:wear(2):1:TAG(chisel):)&&(inventory:consumed:3:stone:)
```

Tag an item with the item editor's *Tags* entry (`MODIFY ITEM`) or the `TAGS`
element of a world file.

### Editing and checking recipes in the game

| Command | Use |
| --- | --- |
| `LIST RECIPES` | Lists the skills that have a recipe file (needs the `CMDRECIPES` or `LISTADMIN` security flag, or builder mode) |
| `MODIFY RECIPE [SKILL]` | Opens a grid of the skill's main recipe file. Enter a line number to edit that row, `A` to add one, or a blank line to finish; each column has its own prompt and validation. Finishing asks `Save to V)FS, F)ilesystem, or C)ancel` |
| `VALIDATE RECIPES ([SKILL]/ALL)` | Checks every row of a skill's file and its `.1`-`.9` extension files: each value as `MODIFY RECIPE` would accept it, and the number of columns. Reports every problem with file and row. `ALL` (the default) checks every skill, then `hunting.txt`, `fishing.txt` and `components.txt` |

`VALIDATE RECIPES` works in builder mode, a DEV game, or with the `CMDRECIPES`
flag ([ValidateCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/ValidateCmd.java)).
Run it after hand-editing a recipe file and before asking anyone to spend
materials. In builder mode, saving from `MODIFY RECIPE` writes the world's copy of the
file.

```text
validate recipes blacksmithing
validate recipes all
modify recipe Armorsmithing
```

### Recipe requirement keys

CoffeeSUD adds keys that gate or reward one recipe. They live in one of two
places:

- In a **GenCraftSkill's** `KEY_VALUE_PARMS` column (the last), as
  `MASK="-LEVEL +>5" XP=200`.
- In **any other crafting skill** (the stock skills), in a column of their own
  after the skill's own columns whose text starts with `KEY=`. Every column
  after the first is scanned, and a column counts if it begins with a word and
  an equals sign. Existing columns are unaffected: for example a spell column
  `*Prop_ImproveGather;amt=2` starts with `*`, so it is never mistaken for keys.

The keys are read by `RecipeKeys` and are **not item stats**; in a
`KEY_VALUE_PARMS` column every other `KEY=value` becomes a `setStat` on the
item made (`RACEREQUIREMENT` is a further reserved key for a racial limit, and
is not a stat).

| Key | Value | Effect |
| --- | --- | --- |
| `MASK` | A zapper mask on the crafter | A crafter who fails it does not see the recipe in `LIST` and cannot make it |
| `ROOMMASK` | A zapper mask tested on the crafter's *room* (`-LOCATION +ROOMID(Deepforge#3)`, `-AREA +Deepforge`) | Refuses the craft when it fails |
| `TOOL` | An item name | Refuses unless the crafter carries, holds or has in the room an item of that name |
| `FAILMSG` | Text | What the crafter is told when `ROOMMASK`, `TOOL`, `STATION` or `HIDDEN` refuses (a plain refusal otherwise) |
| `XP` | A number | Experience posted on each success |
| `TATTOO` | A name | A tattoo (upper-cased) given on the first success |
| `STATION` | A tag | A [workstation](#workstations-tools-and-fire) with that tag must be in the room: "You need a forge here." |
| `HIDDEN` | `1`, `true` or `yes` | Only those who [know the recipe](#recipe-books-known-recipes-and-learning) see or make it |
| `EXPERIMENT` | Comma list of ingredients | With `HIDDEN=1`: what [EXPERIMENT](#experiments) must offer to find it |
| `HINT` | Text | Told when an experiment offers at least half the ingredients |
| `NOQUALITY` | `1` or `true` | This recipe's output never rolls a [quality tier](#quality-tiers) |

Details that bite:

- The keys apply only to a **player character** (a mob with player stats).
  Crafting done by NPCs, loot generation or a stock factory ignores them.
- Quote any value with spaces: `FAILMSG="Only the Deepforge burns hot enough."`.
- The recipe the player asked for is matched to a row by name; where several
  recipes match, the first refusal found is what the player sees in place of
  "You don't know how to...".
- `LIST` hides a recipe whose `MASK` or `HIDDEN` check fails, but it still
  shows recipes that only fail `ROOMMASK`, `TOOL` or `STATION`, so a player can
  see what they would need.
- Full help text: `AHELP RECIPEKEYS` ([RECIPEKEYS](tool-reference.md#recipekeys)).

Example row for a stock skill (Carpentry has 11 columns before the keys;
tabs shown as ` | `):

```text
runed chest | 5 | 40 | 6 | 80 | GenContainer | LID | 60 |  |  |  | MASK="-TATTOO +RUNE_TRAINED" ROOMMASK="-LOCATION +ROOMID(Deepforge#3)" TOOL="rune chisel" XP=200 TATTOO=RUNE_CHEST FAILMSG="Only the Deepforge burns hot enough."
```

(The count of empty cells must match the skill's columns; check with
`MODIFY RECIPE Carpentry` or `VALIDATE RECIPES Carpentry`.)

### Recipe books, known recipes and learning

There are four ways a character comes to know a recipe the base file does not
give them. All feed the same list the skill uses.

**Recipe books (`GenRecipe`).** A recipe book is an item (`CREATE ITEM
GenRecipe`) with a skill ID and lines. Its stats are `SKILLID`, `RECIPES` (one
whole recipe line per line, tabs included) and `NUMRECIPES`. While it is in
the crafter's inventory (not inside a container, and not flagged uncraftable),
its lines are added to the skill's recipe list for that crafter. If the lines
are shorter than the skill's rows they are padded. Sell the book, lose the
recipes.

**Prop_KnownRecipes** (`CR4`). A permanent property on the *character*, kept in
the save. Its text is one `SKILLID|NAME` line per known recipe, or
`SKILLID|whole recipe line` for a recipe that exists nowhere else:

```text
Weaponsmithing|moonblade
Carpentry|fixcr4 harp
```

A name makes a base-file recipe marked `HIDDEN=1` visible to that character. A
whole line (tabs and all) adds a new recipe, as a book would, without the book.
The property is created for you the first time a recipe is learned.

**Ways to teach it:**

| Way | Effect |
| --- | --- |
| `LEARN FROM [BOOK]` | Copies every recipe of a carried recipe book the character does not know into their Prop_KnownRecipes. The character must have the book's skill |
| `MPLEARNRECIPE $n [SKILL] [RECIPE NAME]` | Script command: `$n` knows that recipe of the skill. A name must be a recipe of the skill; a whole line (with `\t` for tabs) adds a new one |
| An EXPERIMENT that finds a hidden recipe | See [Experiments](#experiments) |
| The script function `KNOWSRECIPE($n [SKILL] [NAME])` | Asks the question: true if `$n` knows it |

**Profile sharing.** With `PROFILERECIPES=true` in `coffeemud.ini` (or
`world.ini`), a recipe any character of the profile knows is known to all of
them (`KnownRecipes.knows` also asks the profile's other characters, and
`sharedLines` supplies their whole-line recipes). Off by default.

**Learning by taking apart** (`[SKILL] LEARN [ITEM]`, the older mechanism).
Skills that can deconstruct take an unbranded item apart and write its recipe
into a recipe book *with blank pages*. The conditions are in
`CraftingSkill.doLearnRecipe`: the skill must support deconstruction, the item
must be carried, empty, unworn, of a level no higher than the crafter's, not
flagged unlearnable (`SENSE_ITEMUNLEARNABLE`), not branded by a crafter, and
something the skill could make (`mayICraft`). The crafter needs a recipe book
(blank, or already holding that skill's recipes) with a free page. A recipe
already written there is refused.

A **GenCraftSkill** gets this with `CANLEARN=true` (otherwise
`supportsDeconstruction()` is false), and `LEARNMASK` (an item zapper mask,
such as `-TAGS +rune`) narrows which items it will learn from:

```text
MODIFY CRAFTSKILL Runecarving
(CANLEARN true, LEARNMASK "-TAGS +rune")
```

so a Runecarving skill learns designs by dismantling rune stones found in a
barrow. Cooking never supports it.

### Experiments

`[SKILL] EXPERIMENT [THING] [THING]...` lets a crafter discover a hidden recipe
by combining items they carry. The recipe in the skill's file is marked:

```text
a mug of spiced cider | 1 | 10 | 1 | 5 | GenItem | | | | | | HIDDEN=1 EXPERIMENT=honey,apple,mead XP=40 HINT="It needs something warming..."
```

Rules, from `core/Experiments.java`:

- Each word is looked up among the crafter's items (by name); an unknown one
  stops the attempt ("You don't have any [x] to try.").
- Only rows with `HIDDEN=1` *and* an `EXPERIMENT=` list, whose level is at or
  below the crafter's and which they do not already know, are candidates.
- **Found**: the offered things match the ingredients exactly (every
  ingredient named by `EXPERIMENT=` is matched by one offered thing whose name
  contains it, and nothing extra is offered). The recipe goes into
  Prop_KnownRecipes, the crafter gets `XP=` (or ten per recipe level when
  there is no `XP=`), and the offered items are **not** used up.
- **Near miss**: at least half of the ingredients (and at least one) were
  offered. The row's `HINT=` is told ("You feel you are close to something."
  without one), and one offered item is used up.
- **Miss**: "Nothing comes of it." and the first offered item is used up.
- A limit of tries per game day per character: `CRAFTEXPERIMENTS=n` in the
  ini (10 when blank, 0 for no limit). The count is kept in memory, keyed by
  name and game date, so it resets on restart.

Keep the hint understandable and the cost of a miss low; players cannot see
which recipes exist.

### Workstations, tools and fire

A **workstation** is anything a crafter must stand beside, or that improves
their craft. There are four ingredients.

**`Prop_Workstation`** goes on an item or a room:

```text
TAG=forge,anvil SKILLS=Blacksmithing,Weaponsmithing SPEED=25 PROF=+10 MAXLEVEL=40 QUALITY=+1
```

| Parameter | Meaning |
| --- | --- |
| `TAG` (or `TAGS`) | Comma list of what it is. A recipe's or skill's `STATION=` asks for one of these |
| `SKILLS` | Skill IDs or names it helps (all skills when omitted) |
| `SPEED` | Percent off the time a craft takes, 0 to 90 |
| `PROF` | Added to the crafter's proficiency check |
| `MAXLEVEL` | Recipes up to this level may be made here, even above the crafter's own level |
| `QUALITY` | Added to `@x3` in [quality tier](#quality-tiers) formulas |

It works for a crafter in the same room as the host. If several stations help
the same skill, the *best* value of each of SPEED, PROF, MAXLEVEL and QUALITY
is used; they do not add. Only stationary items count: an item inside a
container is ignored.

**Tags without the property.** An item with a tag (`TAGS`, the item editor's
Tags entry) lying in the room counts as a station of that name, with no bonus.
A "travel anvil" tagged `anvil` satisfies `STATION=anvil`.

**`STATION=`** asks for one. On a recipe, as the [key](#recipe-requirement-keys)
`STATION=forge`; for every recipe of a data skill, the `STATION` field of the
GenCraftSkill. A player lacking it is told "You need a forge here."

**Fire.** Skills that need a fire (Blacksmithing sets `fireRequired`, Cooking,
Smelting...) look for a burning item in the room; CoffeeSUD also accepts an
item that serves as a station named `fire`: a `Prop_Furnishing` with
`ACTS=fire`, a `Prop_Workstation` tagged `fire`, or an item tagged `fire`.

**Tools.** A recipe can require a tool two ways: the key `TOOL="rune chisel"`
(an item of that name carried, held, or in the room, checked only; not used up),
or a [component](#materials_required-and-components) of fate `kept` or
`wear(N)` with `held`/`inventory`, or `TAG(name)`. A `wear(N)` tool loses `N`
uses per craft and breaks at zero ("[tool] breaks."). The tool must have
`usesRemaining` set for the wear to mean anything.

Gathering tools use `Prop_ImproveGather`, which a recipe can attach to the tool
it makes (the stock "lumberjack saw" row ends `*Prop_ImproveGather;amt=2
skills=Chopping,MasterChopping`).

### Quality tiers

By default a craft either works or ruins the item. A world can turn on a
**rolled quality tier** (CR17): a prefix word and changed stats. The tiers are
in `resources/skills/craftquality.txt` (or the world's copy,
`world/resources/skills/craftquality.txt`, read first). The stock file has its
tiers commented out, so nothing rolls until a world gives some.

One tier per line, fields separated by tabs:

| Field | Meaning |
| --- | --- |
| `NAME` | The word inserted into the item's name (`fine iron sword`) |
| `CHANCE` | A percentage, as a formula: `@x1` the crafter's level minus the item's, `@x2` their proficiency in the skill, `@x3` the best helping workstation's `QUALITY`. Formulas are evaluated strictly left to right, so wrap each part in brackets |
| `VALUE`, `ARMOR`, `DAMAGE`, `USES` | Multipliers on the item's value, armour (armour only), damage (weapons only) and uses |
| `LEVEL` | Added to the item's level (never below 1) |
| `EFFECTS` | `ID(PARMS);ID(PARMS)` properties put on the item, as `Prop_HaveResister(fire 10);Prop_WearAdjuster(dex+1)` |

```text
masterwork	(@x2/25)+(@x1/4)+(@x3*3)	1.5	1.1	1.1	1.2	3
fine	(@x2/6)+@x1+(@x3*5)	1.1	1.05	1.05	1.1	1
crude	(100-@x2)/5	0.7		0.9
```

Tiers are tried in file order; the first whose chance rolls under its percent
is the item's. If none rolls, the item is plain. The roll happens only for
player characters. A data skill with `QUALITY=false` and a recipe with
`NOQUALITY=1` roll none. Put the best tier first, or it will never be reached.
The file is re-read when it changes.

### Craft and gather XP

A craft (crafting, cooking or building skill) has an XP value that an
**Artisan** class earns. CoffeeSUD lets every class earn some:

- `CRAFTXP=[PCT]` in the ini: every player character gets that percent of the
  craft's XP value.
- `GATHERXP=[N]` in the ini: every player character gets N XP per unit found by
  a gathering skill.
- **`Prop_CraftXP`** adds more where it applies: on the character, an item
  they carry, the room or an item lying in it, or the area and its parent
  areas.

```text
PCT=50 GATHER=2 SKILLS=Blacksmithing,Mining MASK="-LEVEL +<20"
```

`PCT` is added to `CRAFTXP`; `GATHER` to `GATHERXP`; `SKILLS` (IDs or names)
limits the skills; `MASK` limits who. A guild hall that teaches puts `PCT=50` on
its area. All the XP goes through `postExperience`, so `Prop_ModExperience` and
achievements see it. Both ini keys are 0 when blank. Recipe `XP=` is a separate
per-recipe reward.

### Salvage and dismantling

Two ways to take things apart.

**Scrapping** (`SCRAP`) gives a fifth of an item's weight in its material. It
refuses flesh, liquid, paper, energy, gas, vegetation and unknown materials and
refuses enchanted items.

**`Prop_SalvageYield`** on an item or a catalog entry replaces that with data:

```text
SKILL=Scrapping YIELD="brass:4,gear(clockwork):1@25%,catalog:soul gear:1@5%" XP=20 DESTROY=true
```

| Parameter | Meaning |
| --- | --- |
| `SKILL` (or `SKILLS`) | Which skill uses it: `Scrapping` (default), a crafting skill ID, `*`, or `ALL` |
| `YIELD` | Comma list of `RESOURCE:N`, `RESOURCE(VARIANT):N` or `catalog:ITEM NAME:N`, each optionally followed by `@PCT%` for a chance. Write it without spaces around the colon; an unquoted `YIELD=` value runs to the next space |
| `XP` | XP posted on success |
| `DESTROY` | `false` leaves the item (the property is removed, so it can be salvaged once) |

**`[SKILL] DISMANTLE [ITEM]`** works with crafting skills. If the item has a
`Prop_SalvageYield` for that skill, it gives that. Otherwise, if the skill
could make the item (and it is not enchanted), the crafter gets
`DISMANTLEPCT` percent of the materials the item's recipe uses, times their
proficiency. `DISMANTLEPCT` in the ini is 50 when blank; 0 turns DISMANTLE off
for items without a Prop_SalvageYield. Containers must be empty, and an item
inside owned property needs privileges.

### Data-defined skills

The common skills can be built in the game as **data**, with no Java. They are
generic abilities, kept with the world (as files under `world/custom/abilities/`
in the world folder) and reloaded at boot.

| Command | Makes | Class |
| --- | --- | --- |
| `CREATE CRAFTSKILL [SKILL ID]` | A crafting skill | GenCraftSkill |
| `CREATE GATHERSKILL [SKILL ID]` | A gathering skill | GenGatheringSkill |
| `CREATE WRIGHTSKILL [SKILL ID]` | A skill that makes vessels from a `.cmare` file | GenWrightSkill |
| `CREATE BUILDSKILL [SKILL ID]` | A building skill (CoffeeSUD, CR8) | GenBuildingSkill |

Each also has `MODIFY [KIND] [SKILL ID]` and `DESTROY [KIND] [SKILL ID]`, and
the editor menus describe each field. `CREATE BUILDSKILL Construction` (an ID
that already exists as a coded skill) makes a data skill in its place.
`MODIFY ABILITY` on a data skill tells you which `MODIFY` to use.

**GenCraftSkill fields** (`getStatCodes`): `NAME`, `HELP`, `TRIGSTR` (command
words, commas between), `FILENAME` (the recipe file), `MATLIST` (the raw
materials it works, such as `WOODEN`, `METAL`), `VERB` (shown while working),
`CANMEND`, `CANREFIT`, `CANBUNDLE`, `SOUND`, `CANSIT`, `NEEDFIRE`,
`NOHARDBONUS` (no level adjustment from material hardness), plus CoffeeSUD's
`STATION` (a workstation tag every recipe needs), `CANLEARN`, `LEARNMASK`,
`QUALITY` (default true), `ACTIONCOST`, `COMBATACTIONCOST`, `COMPONENTS` (as on
a generic ability, see [15-ability-composition.md](15-ability-composition.md))
and `SCRIPT`. The `SCRIPT` field takes functions `ONCRAFTED` (`$o` the item
made) and `ONFAILED` (`$o` the item ruined), `$i` the worker (`AHELP
SKILLSCRIPTS`). Its recipe file has the GenCraftSkill columns shown above.

**GenGatheringSkill fields:** `NAME`, `HELP`, `TRIGSTR`, `MATLIST`, `VERB`,
`SOUND`, `CANSIT`, `CANBUNDLE`, `ROOMMASK` (a zapper mask that rooms must pass;
usually `-LOCATION`), `PLAYMASK` (who may start), `YIELDFORMULA` (default
`(1?5)+3`), `MSGSTART`, `MSGFOUND` (`@x1` the item), `MSGNOTFOUND`,
`MSGCOMPLETE` (`@x1` count, `@x2` name), `ITEMXML` (the droppable items),
`ISCOSMETIC` (shown but not created), `MINDUR` (12), `BASEDUR` (40), `FINDTICK`
(6), `NOTHINGFREQ` (weight of finding nothing), `ACTIONCOST`,
`COMBATACTIONCOST`, `COMPONENTS` and `SCRIPT` (`ONFOUND`, `ONFAILED`).
[Weighted finds](#gathering-skills-as-data) are described below.

**GenWrightSkill fields:** `NAME`, `HELP`, `TRIGSTR`, `FILENAME`, `MATLIST`,
`VERB`, `CANMEND`, `CANDOOR`, `CANTITLE`, `SOUND`, `CANDESC`, `CLANONLY`.

**GenBuildingSkill fields:** `NAME`, `HELP`, `TRIGSTR`, `FILENAME`, `MATLIST`,
`MAINRESOURCE` (the material shown, such as `Ice`), `MAINMATERIAL` (the
material taken: `stone`, or a [world material](#world-materials-materialstxt)),
`VERB`, `SOUND`, `CANSIT`. Its recipe file uses the
[building recipe format](#building-recipes).

#### Example: a data crafting skill

This defines a Runecarving skill and the recipe file it reads. Make the recipe
file first (tabs as shown by ` | `):

```text
world/resources/skills/runecarving.txt
rune of ward | 3 | 30 | 2 | 40 | GenItem |  |  |  |  | Prop_HaveResister(magic 10) | STATION=carvingbench XP=15
rune of light | 1 | 20 | 1 | 15 | GenItem |  |  |  |  | Spell_Light |
```

Then, in the game:

```text
create craftskill Runecarving
```

and fill the menu: `NAME` Runecarving, `TRIGSTR` RUNECARVE, `FILENAME`
runecarving.txt, `MATLIST` ROCK, `VERB` carving, `CANLEARN` true, `LEARNMASK`
`-TAGS +rune`. A player then uses `RUNECARVE LIST`. The second line has an
empty `KEY_VALUE_PARMS`, which is fine.

## Materials and resources

A **resource** is a kind of raw material (IRON, OAK, WHEAT); a **material
family** groups resources (METAL, WOODEN, VEGETATION...). Items carry both a
material (the resource code) and use families in recipes: a crafting skill takes
any resource of its `MATLIST` families, so Blacksmithing works every METAL.

The families are: `UNKNOWN`, `CLOTH`, `LEATHER`, `METAL`, `MITHRIL`, `WOODEN`,
`GLASS`, `VEGETATION`, `FLESH`, `PAPER`, `ROCK`, `LIQUID`, `PRECIOUS`,
`ENERGY`, `SYNTHETIC`, `GAS` (the `RawMaterial.Material` enum).

### World materials (materials.txt)

CR2 lets a world define its own resources without Java or ini edits.
`world/materials.txt` has one resource per line, **tab**-separated:

| Field | Meaning |
| --- | --- |
| `NAME` | The resource name (upper-cased) |
| `FAMILY` | One of the families above. Required |
| `VALUE` | Value of a unit |
| `FREQUENCY` | 1-10, how common (default 5) |
| `HARDNESS` | 1-10 (default 1) |
| `BUOYANCY` | Default 1000 |
| `CATEGORY` | `FISH`, `BERRY` or `WOODY`, or blank |
| `SMELL` | Smell text |
| `EFFECTS` | Properties or spells put on the raw item, `;` between: `Spell_Light;Prop_HaveResister(fire 5)` |
| `DESCRIPTION` | The raw resource's description |
| `CODE` | A fixed number, if it must never move |

A `#` line is a comment and a first line starting `NAME` is a header. A row
named as a built-in resource changes that resource; every other row is added
after the built-ins, at its `CODE` or sorted by name. Items store the resource
name (`MTRALN`) next to its number, so they find their material again if codes
move. The file is read once at start.

```text
starsteel	METAL	300	1	6	9000			Prop_HaveResister(fire 5)	A sliver of metal that never cools.
```

Keep codes stable after release: give a row a `CODE` if saved items depend on
its number. A row without `NAME` or `FAMILY` is logged and skipped. The old way,
`ADDMATERIAL_` lines in `coffeemud.ini`, still works; a materials.txt row of the
same name takes its place. Help: `AHELP WORLDMATERIALS`.

### Resource variants (resources.txt)

A **variant** is a named subtype of a resource ("moonpetal" herbs). In
`world/resources.txt`, one per line, tab-separated, columns `SUBTYPE`,
`BASE_RESOURCE` (a resource such as HERBS or a family such as VEGETATION; blank
means any), `DISPLAY_NAME`, `VALUE` (per unit), `WEIGHT_PER_UNIT` (kept for the
format; not used), `DESCRIPTION`, `SMELL`, `EFFECTS`, `NOURISHMENT` (per unit,
for food):

```text
moonpetal	HERBS	silvery moonpetal	40		A pale petal that glows in the dark.	sweet	Spell_Light	25
```

Whenever a raw resource of that base is given that subtype (by gathering,
`ResourceOverride`, butchering, the hunting and fishing tables, or a skill's own
code), the row's name, value, description, effects and nourishment are put on
it, and the name follows the row when it is bundled or split. An item loaded
from a save keeps what it was saved with. Recipes and components ask for a
variant as `herbs(moonpetal)`. The file is re-read when it changes. Help:
`AHELP RESOURCEVARIANTS`.
