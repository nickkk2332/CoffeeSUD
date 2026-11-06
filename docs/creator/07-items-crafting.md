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
| `[SKILL] LEARN [ITEM]` | Takes an item apart to write its recipe into a recipe book (skills that support deconstruction; see [learning by taking apart](#recipe-books-known-recipes-and-learning)) |
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
  safest way to extend a shipped skill, because stock updates still apply.
  Keep the numbers consecutive: reading stops at the first missing file from
  `.2` on, so `.3` is ignored when there is no `.2`.
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
| MasterTailoring | MASTERKNIT, MKNIT, MASTERTAILOR | `mastertailor.txt` |
| Costuming, MasterCostuming | COSTUME, MASTERCOSTUME | `costume.txt`, `mastercostume.txt` |
| LeatherWorking, MasterLeatherWorking | LEATHERWORK, MASTERLEATHERWORKING | `leatherworking.txt`, `masterleatherworking.txt` |
| Weaving, Textiling | WEAVING, TEXTILING | `weaving.txt`, `textiling.txt` |
| Pottery | POT | `pottery.txt` |
| GlassBlowing | GLASSBLOW | `glassblowing.txt` |
| Sculpting | SCULPT | `sculpting.txt` |
| ScrimShaw | SCRIM | `scrimshaw.txt` |
| JewelMaking | JEWEL | `jewelmaking.txt` |
| Smelting | SMELTING | `smelting.txt` |
| Fletching, Gunsmithing, Siegecraft | FLETCH, GUNSMITH, SIEGECRAFT | `fletching.txt`, `gunsmith.txt`, `siegecraft.txt` |
| InstrumentMaking | INSTRUMENTMAKING | `instruments.txt` |
| WandMaking, ShardMaking | WANDMAKE, SHARDMAKE | `wandmaking.txt`, `shardmaking.txt` |
| StaffMaking, Rodsmithing | STAFFMAKE, RODSMITH | `staffmaking.txt`, `rodsmithing.txt` |
| PaperMaking | PAPERMAKE | `papermaking.txt` |
| ScrollScribing | ENSCRIBE | `scribing.txt` |
| Alchemy, ImprovedAlchemy | BREW, IBREW | `alchemy.txt`, `improvedalchemy.txt` |
| Herbalism, ImprovedHerbalism | HBREW, IHBREW | `herbalism.txt`, `improvedherbalism.txt` |
| Cooking and the cooks (Baking, FoodPrep, Distilling, GaolFood, DrugCutting, Apothecary) | COOK, BAKING, FOODPREPPING, DISTILLING, GAOLFOOD, DRUGCUTTING, APOTHECARY | `recipes.txt`, `bake.txt`, `foodprep.txt`, `liquors.txt`, `gaolfood.txt`, `drugs.txt`, `poisons.txt` |
| Painting, Dyeing, Lacquerring (and Master versions) | PAINT, DYE, MASTERDYEING, MASTERLACQUERING | `painting.txt`, `dyeing.txt`, `lacquering.txt`, `masterdyeing.txt`, `masterlacquering.txt` |
| Embellishing, Decorating | EMBELLISH, DECORATE | `embellishing.txt`, `decorations.txt` |
| SmokeRings, MasterSmokeRings | SMOKERINGS, MASTERSMOKERINGS | `smokerings.txt`, `mastersmokerings.txt` |
| BodyPiercing | BODYPIERCE | `bodypierce.txt` |
| ClanCrafting | CLANCRAFT | `clancraft.txt` |
| CageBuilding | BUILDCAGE | `cagebuilding.txt` |
| Construction, Masonry, Excavation, Landscaping, Irrigation, Welding | CONSTRUCT, MASONRY, EXCAVATE, LANDSCAPE, IRRIGATE, WELDING | `construction.txt`, `masonry.txt`, `excavation.txt`, `landscaping.txt`, `irrigation.txt`, `welding.txt` |
| Shipwright, CaravanBuilding | SHIPBUILD, CARAVANBUILD | `shipbuilding.cmare`, `caravanbuilding.cmare` |
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
| Blacksmithing | `... ITEM_CLASS_ID, STATUE\|\|RIDE_BASIS\|\|CONTAINER_TYPE_OR_LIDLOCK\|\|CODED_WEAR_LOCATION, CONTAINER_CAPACITY\|\|LIQUID_CAPACITY\|\|MAX_WAND_USES\|\|LIGHT_DURATION\|\|DICE_SIDES, CODED_SPELL_LIST` |
| GlassBlowing | `... ITEM_CLASS_ID, CONTAINER_TYPE_OR_LIDLOCK\|\|RIDE_BASIS\|\|STATUE, CONTAINER_CAPACITY\|\|LIQUID_CAPACITY\|\|MAX_WAND_USES\|\|LIGHT_DURATION\|\|DICE_SIDES, CODED_SPELL_LIST` |
| Pottery | `... ITEM_CLASS_ID, CONTAINER_TYPE_OR_LIDLOCK\|\|STONE_FLAG\|\|STATUE, CONTAINER_CAPACITY\|\|LIQUID_CAPACITY\|\|MAX_WAND_USES, CODED_SPELL_LIST` |
| ScrimShaw | `... ITEM_CLASS_ID, STATUE\|\|CONTAINER_TYPE_OR_LIDLOCK\|\|WEAPON_CLASS\|\|RIDE_BASIS\|\|CODED_WEAR_LOCATION, BASE_ARMOR_AMOUNT\|\|CONTAINER_CAPACITY\|\|BASE_DAMAGE\|\|LIGHT_DURATION\|\|MAX_WAND_USES\|\|DICE_SIDES, CODED_SPELL_LIST` |
| Sculpting | `... ITEM_CLASS_ID, STATUE\|\|LID_LOCK\|\|RIDE_BASIS, CONTAINER_CAPACITY\|\|LIGHT_DURATION\|\|MAX_WAND_USES\|\|DICE_SIDES, CONTAINER_TYPE, CODED_SPELL_LIST` |
| Armorsmithing, Cobbling | `ITEM_NAME, ITEM_LEVEL, BUILD_TIME_TICKS, MATERIALS_REQUIRED, ITEM_BASE_VALUE, ITEM_CLASS_ID, CODED_WEAR_LOCATION, CONTAINER_CAPACITY, BASE_ARMOR_AMOUNT, CONTAINER_TYPE, CODED_SPELL_LIST` |
| Carpentry | `... ITEM_CLASS_ID, LID_LOCK\|\|RIDE_BASIS\|\|WEAPON_CLASS\|\|CODED_WEAR_LOCATION\|\|SMOKE_FLAG\|\|STATUE, CONTAINER_CAPACITY\|\|WEAPON_HANDS_REQUIRED\|\|LIQUID_CAPACITY\|\|LIGHT_DURATION\|\|MAX_WAND_USES\|\|DICE_SIDES, BASE_ARMOR_AMOUNT\|\|BASE_DAMAGE\|\|REQ_RESOURCE_OR_MATERIAL, CONTAINER_TYPE\|\|ATTACK_MODIFICATION\|\|RES_SUBTYPE, CODED_SPELL_LIST` |
| Weaponsmithing (and Master, Legendary) | `... ITEM_CLASS_ID, WEAPON_CLASS, WEAPON_TYPE, BASE_DAMAGE, ATTACK_MODIFICATION, WEAPON_HANDS_REQUIRED, MIN_MAX_RANGE, OPTIONAL_RESOURCE_OR_MATERIAL, CODED_SPELL_LIST` |
| Tailoring, Weaving, Costuming | `... ITEM_CLASS_ID, WEAPON_TYPE\|\|CODED_WEAR_LOCATION\|\|RIDE_BASIS (Weaving and Costuming have WEAPON_CLASS in place of WEAPON_TYPE; Weaving also LID_LOCK), CONTAINER_CAPACITY\|\|WEAPON_HANDS_REQUIRED, BASE_ARMOR_AMOUNT\|\|BASE_DAMAGE, CONTAINER_TYPE, CODED_SPELL_LIST` |
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
crafted lumberjack saw a gathering tool). An entry written `*ID;__DELETE__` (a `*` entry whose parameter is
`__DELETE__`) removes that effect or behaviour from the item instead of
adding one. In a Cooking row the same column holds the smell list
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
| `LIST RECIPES` | Lists the skills that have a recipe file (needs the `CMDRECIPES` or `LISTADMIN` security flag) |
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

**Profile sharing.** With `PROFILERECIPES=true` in `coffeemud.ini`, a recipe any character of the profile knows is known to all of
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
is used; they do not add. An item counts only when it lies loose in the room;
one inside a container is ignored.

**Tags without the property.** An item with a tag (`TAGS`, the item editor's
Tags entry) lying in the room counts as a station of that name, with no bonus.
A "travel anvil" tagged `anvil` satisfies `STATION=anvil`.

**`STATION=`** asks for one. On a recipe, as the [key](#recipe-requirement-keys)
`STATION=forge`; for every recipe of a data skill, the `STATION` field of the
GenCraftSkill. A player lacking it is told "You need a forge here."

**Fire.** Skills that need a fire (Blacksmithing sets `fireRequired`; a
GenCraftSkill has the `NEEDFIRE` field) look for a burning item in the room;
CoffeeSUD also accepts an
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
refuses flesh, liquid, paper, energy, gas, vegetation and unknown materials,
and refuses enchanted items that have no `Prop_SalvageYield`.

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
[Weighted finds](#data-gathering-skills) are described below.

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
rune of ward | 3 | 30 | 2 | 40 | GenItem |  |  |  |  | Prop_HaveResister(fire 5) | STATION=carvingbench XP=15
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

## Resources and gathering

### Gathering skills

A gathering skill finds raw resources (or, for the data kind, any items) in
the room where it is used. Typical commands: `MINE`, `GDIG` (Digging), `CHOP`,
`DRILL`, `FORAGE`, `FISH`, `TRAWL`, `HUNT`, `BUTCHER`, `SHEAR`, `PLANT` (Farming),
`GPLANT` (Gardening), `COMPOST`, `BAIT`, `ATRAP`. "Master" versions
(`MMINE`, `MCHOP`, `MFISH`, `MFORAGE`, `MBUTCHERING`, `MSHEAR`, `MTRAWL`,
`MPLANT`...) are faster or better. The finds go into the room as raw-resource
items, which crafting skills then `BUNDLE` or use.

How much a gather yields:

1. The skill finds out what the room offers (below).
2. A proficiency roll and the room's `Yields` record decide whether and how
   much: each harvest in a room takes a share (20 percent for most skills) off
   the next. See [Yield, depletion and regrowth](#yield-depletion-and-regrowth).
3. `Prop_ImproveGather` on the gatherer, a tool they carry, the room or the
   area multiplies the yield:

   ```text
   AMT=3 MASK="-SEASON +SPRING" SKILLS=Farming,Foraging
   ```

   `AMT` is the multiplier (0 removes the yield), `MASK` is tested on the
   gatherer, and `SKILLS` is a list of skill IDs or `ALL`.

CoffeeSUD gives every class `GATHERXP` per unit (see [Craft and gather
XP](#craft-and-gather-xp)).

### Where resources come from

- **A room's locale.** Outdoor and indoor locales (Woods, Plains, Mountains,
  Cave...) each have a list of resources. A room rolls one from its locale's
  list, weighted by each resource's frequency, and a quarter of the weight goes to
  the resources its neighbours of the same locale rolled (so patches form).
- **`ResourceOverride`** (a behaviour on a room, area or mob) replaces that list.
  Parameters: resources by name or code, room types to apply to when put on an
  area, `MIN=`, `MAX=` and `CHANCE=` (timing and chance), a custom item as
  `"item name(RESOURCE SUBTYPE)"`, and `MOBMASK=` for corpses (butchering). After changing an area's ResourceOverride, `RESET AREA` to put
  the rooms back. See [24-behaviors-and-masks.md](24-behaviors-and-masks.md).
- **A named variant** such as `"moonpetal" HERBS` (see [resource
  variants](#resource-variants-resourcestxt)): `ResourceOverride(moonpetal(HERBS moonpetal))`.
- **A building skill** can install it: the stock "tomato field" Landscaping
  row adds `ResourceOverride(Tomatoes)` to an outdoor room, and "till land"
  removes it.
- **Data skills** (`GenGatheringSkill`) do not use the room's resource at all:
  they find the items named in their own list.

### Seasons and hours

Gathering does not read the calendar by itself. CoffeeSUD (CR11) adds
calendar parameters to `ResourceOverride`, evaluated on the room's area clock:

| Parameter | Meaning |
| --- | --- |
| `SPRING=`, `SUMMER=`, `AUTUMN=` (or `FALL=`), `WINTER=` | A comma list of resources for that season, or `none` for nothing. A season without a list uses the plain list if there is one, else the room's own resource |
| `SEASON=`, `MONTH=`, `HOUR=` | Comma lists (ranges like `20-23` allowed) that limit when the override applies at all; outside them the room has its own resource back |
| `MSG="text"` (or `MESSAGE=`) | Shown in the room when the calendar changes its resource |

```text
SPRING=flowers,herbs SUMMER=blueberries AUTUMN=apples WINTER=none MSG="The orchard turns with the season."
MUSHROOMS SEASON=autumn HOUR=20-23,0-4
```

For a season-based *yield* (not what is there), put `Prop_ImproveGather
AMT=0 MASK="-SEASON +WINTER"` on the area. Seasons and the clock are described
in [10-world-simulation.md](10-world-simulation.md).

### Yield, depletion and regrowth

Each room remembers how often it has been harvested. By default the first
harvest is full, each later one takes a further 20 percent off, and the room
regrows 30 game minutes after the first harvest.

`Prop_Yield` on a room or an area (and, through the area, its child rooms)
changes that:

```text
HARVESTS=5 DROP=0% REGROW=7 DAYS EMPTYMSG="The glade is picked clean."
```

| Parameter | Meaning |
| --- | --- |
| `HARVESTS` | How many harvests before the room is bare (0 or omitted: no hard limit) |
| `DROP` | Share taken off each successive harvest, as `20%` (default: the skill's own, 20% for most) |
| `REGROW` | How long after the *last* harvest the room grows back: a number plus `HOURS`, `DAYS`, `MONTHS` or `YEARS` of the area's calendar (a bare number is game hours) |
| `EMPTYMSG` | Shown when someone tries a bare room, which finds nothing |

All of it runs on game time and waits while the game is paused or between
turns; the records are saved (`YIELDS` in the save). A skill that finds a room
bare reports it and gives nothing.

### Hunting, fishing, herb and mushroom tables

Several gathering skills pick from data tables in `resources/skills/` (a
world's copy replaces them, as for recipes). Rows are tab-separated, `#` starts
a comment. CoffeeSUD (CR10) added an optional last **MASK** column to the
tables that pick a name, a zapper mask the hunter or their room must pass
(room keys `-AREA`, `-SEASON`, `-HOUR`, `-WEATHER`, mob keys `-LEVEL`, `-CLASS`;
see [24-behaviors-and-masks.md](24-behaviors-and-masks.md)). A row without a
mask applies everywhere.

| File | Skill | Columns |
| --- | --- | --- |
| `hunting.txt` | Hunting | `RESOURCE`, `DOMAIN` (`*`, a room domain or a locale class ID), `FREQUENCY`, `MOB` (a mob or race the hunter finds), `MASK` |
| `fishing.txt` | Fishing and relatives | `RESOURCE`, `FREQUENCY`, `NAME` (the catch), `VALUE`, `MASK` |
| `herbology.txt` | Herbology | One herb name per line, optional tab and `MASK` |
| `floristry.txt` | Floristry | One flower per line, optional tab and `MASK` |
| `mycology.txt` | Mycology | `NAME`, `FREQUENCY`, `VALUE`, `SPELLS` (may be empty), `MASK` |
| `taxidermy.txt` | Taxidermy | Pose names and the messages for each (text blocks, not rows) |

```text
Shrimp	10	Shrimp	1
Shrimp	1	lantern eel	12	-HOUR +22
```

A masked fishing row carries its own value, so a fish can be worth more in one
area. Hunting and fishing pick among the matching rows weighted by their frequency;
when a mushroom table has masks, Mycology weighs each row by frequency plus one.
`VALIDATE RECIPES ALL` checks `hunting.txt` and `fishing.txt`.

### Data gathering skills

A **GenGatheringSkill** (`CREATE GATHERSKILL`) gets its finds from the `ITEMXML`
field, a list of items. Per item, CoffeeSUD (CR9) adds three optional tags
inside the item's `ITEM` element of the XML:

| Tag | Meaning |
| --- | --- |
| `<FREQ>n</FREQ>` | The item's weight (default 1; 0 means never found) |
| `<MASK>zapper</MASK>` | Found only when the gatherer or their room passes it. Write `<` and `>` as `&lt;` and `&gt;` |
| `<YIELD>formula</YIELD>` | How many, in place of `YIELDFORMULA` |

`NOTHINGFREQ` is the weight of finding nothing. `YIELDFORMULA` and `YIELD`
take variables: `@x1` the gatherer's level, `@x2` their proficiency, `@x3`
their expertise bonus, `@x4` the percent of the room's yield left (100 when
fresh). Formulas are worked strictly left to right, so bracket them: `1+(@x2/40)`.

The editor adds items to the list (`new [item name from your inventory]`) and
removes them by name; the FREQ, MASK and YIELD tags are kept when the list is
rebuilt but are written into the text of `ITEMXML` (for example by editing the
skill's definition file, or its `ITEMXML` stat). "Beachcombing" example:
driftwood `<FREQ>60</FREQ>`, shells `<FREQ>30</FREQ>`, amber
`<FREQ>5</FREQ><MASK>-WEATHER +THUNDERSTORM</MASK>`, a catalog "message in a
bottle" `<FREQ>1</FREQ>`, `YIELDFORMULA` `1+(@x2/40)`.

The skill's messages are `MSGSTART`, `MSGFOUND` (`@x1` the item), `MSGNOTFOUND`
and `MSGCOMPLETE` (`@x1` the count, `@x2` the name). `ROOMMASK` decides which
rooms work at all, `PLAYMASK` who may try.

### Crops and farming

Farming (`PLANT`) and Gardening (`GPLANT`) plant a seed in a room, and the
crop grows over time into harvestable resources. Defaults: it grows in 50
ticks, needs `canSeeTheSun` and an outdoor hills, plains, woods, jungle or
swamp room (or a dirt resource), is refused in a drought, and yields 1d7
times the skill's base. CoffeeSUD (CR16) lets a world change all of that with
**crop rules** (`core/CropRules`):

| Rule | Meaning |
| --- | --- |
| `GROWHOURS=n` | Game hours to grow (instead of 50 ticks). A crop given hours is not set back by the nights while it grows |
| `SEASONS=` | When it may be planted: `SPRING`, `SUMMER`, `AUTUMN` (or `FALL`), `WINTER`; otherwise "This is not the season for planting that." |
| `NEEDSUN=true\|false` | `false` lets it grow without sunlight (default true) |
| `DOMAINS=` | Kinds of room it grows in (`PLAINS`, `HILLS`, `WOODS`, `JUNGLE`, `SWAMP`, `CAVE`, `STONE`...; `ANY`), in place of the usual farmland. A dirt resource still counts |
| `WATER=n` | That many water rooms (this one or the ones next to it; a pond Irrigation digs counts) must be near |
| `YIELD=dice` | Instead of 1d7, such as `2d6+1`; still multiplied by the skill's yield |
| `FAILMSG="text"` | Told when a rule refuses |

Rules come from, in this order, each over the last:

1. **`world/crops.txt`**: one line per crop, `[RESOURCE or SUBTYPE] [rules...]`, `#`
   comments:

   ```text
   WHEAT GROWHOURS=240 SEASONS=AUTUMN YIELD=2d4+1 FAILMSG="The wheat will not take root here."
   ```
2. a **`Prop_CropRules`** on the area (and its parents), then the room;
3. a **`Prop_CropRules`** on the seed item.

A crop with none grows as before. The in-progress growth is part of the save.
`MasterFarming` shortens planting only; the other rules are the same.
For a cellar farm add `NEEDSUN=false DOMAINS=CAVE` on the cellar room.
Remember that rules about the sun are not rules about soil: a stone floor
fails the domain test unless `DOMAINS` names it or the room's resource is dirt.

## Housing, construction and furniture

### Ownership model

Land is sold through a **title** (a deed) tied to a room or an area:

| Property | Targets | Purpose |
| --- | --- | --- |
| `Prop_RoomForSale` | Room | One room for sale. Parameter `[OWNER/][RENTAL ][GRID ][ALLOWTHEFT ]PRICE`, such as `1000000` or `Bob/100000` |
| `Prop_RoomsForSale` | Room | A group of adjacent rooms sold together |
| `Prop_RoomPlusForSale` | Room | As above, and building skills may add rooms |
| `Prop_LotsForSale` | Room | A room and its future neighbours: when one is sold, adjacent lots appear so the owner can expand; `GRID` links new rooms by demolishable walls |
| `Prop_AreaForSale` | Area | A whole area |
| `Prop_PrivateProperty` | Item | A private item: `OWNER="name" PRICE=n EXPIRESEC=n`; a ship or other boardable can be owned this way |
| `Prop_ReqCapacity` | Room, area | Limits on people, items, weight and rooms (`ROOMS=n` limits how far a lot grows) |

Once sold, a lot shows as `Owner/price`. A shopkeeper of type **LAND**
(`ShopKeeper` deal type, "LAND") lists the lots of its own area; any shopkeeper
can sell a `StdTitle` or `GenTitle` item whose property ID is the lot's ID.
The buyer gets a title they can `GIVE` or `SELL`. `LOOK ID` in a lot shows its
ID. A `RENTAL` price is charged every month from the owner's bank account;
failing to pay returns the lot to sale. `ALLOWTHEFT` turns off robbery
enforcement there. The engine keeps the items left in a sold room.

**CoffeeSUD ownership by profile (CR1).** Single-player replaces accounts
with a *profile* (the player's set of characters, see
[31-saves-and-party.md](31-saves-and-party.md)). Every character of a profile
counts as owner of what another of them owns (land, rooms, private items,
vessels). The reserved owner `@PROFILE` stands for the whole profile:

```text
@PROFILE/0
```

as the parameter of a `Prop_RoomForSale` is a home that is already the
player's, never wiped as an owner who "does not exist". `PROFILEPROPERTY=true`
in the ini records everything the player buys as `@PROFILE`. In scripts,
`MPGRANT [item] [who|NOBODY] [PROFILE] [NAME=new name]` gives a deed, vessel
or private item (and registers a vessel so it saves, docking it if needed).

**Scripts and property.** `OWNER(room|area|HERE|$n|$i)` (the owner's name,
`@PROFILE`, or empty), `ISOWNER($n [room|area|$i])`, `LANDTITLE(room|area|HERE)`
(lot ID and price); commands `MPDEED room|area|lotID $n|@PROFILE [price]` and
`MPUNDEED room|area|lotID` (the lot returns to sale). These honour the profile
rule. See [27-mobprog-commands.md](27-mobprog-commands.md) and
[28-mobprog-functions.md](28-mobprog-functions.md).

### Building without a deed: Prop_Homestead

Building skills normally need land the builder owns (or the `CMDROOMS`
security flag). `Prop_Homestead` (CR7) on a room or an area lets a character
build where no deed was ever sold:

```text
SKILLS=Construction,Masonry MAXROOMS=3
```

| Parameter | Default | Meaning |
| --- | --- | --- |
| `MASK` | none | A zapper mask the builder must pass |
| `SKILLS` | all | Building skills allowed, by ID or name |
| `CLAIM` | `true` | `true`: the first build in a room with no title claims it; `PROFILE`: claims it for `@PROFILE`; `false`: builders may build there with no title at all |
| `MAXROOMS` | 0 | With a claim, adds `Prop_ReqCapacity rooms=n` so the lot grows to n rooms at most |
| `PRICE` | 1 | The claimed lot's price. At 1, no property tax is due |
| `NOTAX` | | Forces the price to 1 |

A claim creates a `Prop_LotsForSale` on the room owned by the builder (or
`@PROFILE`). If the build does not start (no materials), the claim is taken
back. The homestead can also sit on a parent area. Use masks and `MAXROOMS` so
construction cannot erase routes the story needs.

### Building recipes

Building skills (Construction, Masonry, Excavation, Landscaping, Irrigation,
Welding and GenBuildingSkill data skills) read a different recipe layout, one
row per project (see the column list above). The important columns:

| Column | Meaning |
| --- | --- |
| `BUILDING_FLAGS` (6th) | Where it may be used, space-separated: `DIR` (needs a direction), `DIRUPDOWN`, `NOWALL`, `INDOOR`, `OUTDOOR`, `CAVEONLY`, `NODOWN`, `DOWNONLY`, `WATERONLY`, `WATERSURFACEONLY`, `UNDERWATERONLY`, `SALTWATER`, `FRESHWATER`, `UPONLY` |
| `BUILDING_CODE` (7th) | What the project does: `WALL`, `DOOR` (an exit of the class in column 8), `ROOM` (a new room of that class), `ITEM` (an item class), `DEMOLISH`, `TITLE` and `DESC` (let the owner set the room's title or description), `STAIRS`, `EXCAVATE`, `ROOMEFFECT` (put the ability in the spell column on the room), `EXITEFFECT`, `DELEFFECT` (remove it) |
| Class column (8th) | The room, exit or item class |
| `PCODED_SPELL_LIST` | Properties or behaviours to attach, with parameters, such as `Prop_Crawlspace()` or `ResourceOverride(Tomatoes)` |
| `BUILDING_NOUN` | Shown while working, with `@x1` for the direction |
| `BUILDER_MASK` | Zapper mask on the builder (`-ANYCLASS +Minstrel`) |
| `BUILDER_DESC` | Description in the `LIST` |

A project the builder cannot start (materials, land, a missing roof) is
refused with a reason. A claimed homestead lot expands by the usual building
rules (each added room is covered by the lot's `Prop_ReqCapacity`). Building is
refused aboard a boardable (a vessel).

### A home the party can use

`Prop_HomeBeacon` (CR13) on a room makes it a **home**: sleeping there (or `HOME
SET`) makes it the start room of every character in the party who has
privileges there, so `RECALL` returns everyone to it. In the ini,
`HOMEBEACONS=true` makes every owned room (one with an owned land title) a home
without the property.

| Command | Effect |
| --- | --- |
| `HOME` | Takes the whole party home, over `HOMERECALL` turns (default 3; 0 means no HOME), not while anyone is fighting. Needs a home set |
| `HOME SET` | Makes the room you are in your home. Needs a home room and privileges there |

### Furnishings

`Prop_Furnishing` (CR20) on an item makes furniture that changes its room:

```text
ROOMEFFECT=Prop_RoomLit COMFORT=2 ACTS=fire
COMFORT=3 ROOMBEHAVIOR=Emoter(min=5 max=9 chance=50;creaks.)
```

| Parameter | Meaning |
| --- | --- |
| `ROOMEFFECT=ID(PARMS)` | An ability put on the room while the item is there |
| `ROOMBEHAVIOR=ID(PARMS)` | A behaviour put on the room |
| `COMFORT=n` | Adds to the room's comfort, which `COMFORT(room\|HERE\|$n)` reads in scripts |
| `ACTS=a,b` | Makes the item a workstation of those names with no bonus (`ACTS=fire`) |

It takes effect while the item is **placed** (not gettable, like a mounted or
decorated thing) or lies on the floor of an **owned** room, and ends when the
item leaves. Effects are never saved with the room. A gettable item in an
unowned room does nothing.

Other furnishing helpers: `Prop_IceBox` on a room or container (nothing in it
spoils: a pantry), `Prop_LimitedContents` (see below), and the `Decorating`
building skill for description changes.

### What is not there

A linked chest shared by the profile (`Prop_SharedStash`, CR22) is deferred.
Ordinary chests and profile-owned property work. Test a home from two
characters of one profile, after leaving it, and after a restore.

## Item definitions and powers

Choose the item class that already does the mechanical thing you need: a
container, weapon, armour, light, food, drink, wand, recipe book, rideable,
key, readable book, or a plain `GenItem`. Set material, weight, value, wear
positions, level and the descriptive text. The fields of every class, the
wear locations and the item editor are in [22-items.md](22-items.md). Use
**tags** (the editor's *Tags* entry, `TAGS` in a world file) for your own
categories, because several systems here match on a tag (`tag:anvil` in a
component, `-TAGS +rune` in a mask, `tag:X` in a combination). A prose label
in the name does not replace the material code or a tag.

**Giving an item behaviour.** Everything in this chapter is a *property*
(an ability that sits on the item) or a *behaviour*. In the item editor
(`MODIFY ITEM`), the **Effects** entry takes properties (`Enter an effect to add,
-ID to remove, =ID or its number to edit, ?P for the properties that fit`) and
the **Behaviors** entry takes behaviours. In JavaScript world builders and
tests you create one with `CMClass.getAbility("Prop_Switch")`, call
`setMiscText("...")` and `item.addNonUninvokableEffect(effect)`; the Creator
Museum example does exactly that
([build.js](examples/world/resources/creator/build.js)). A property's
parameter text is saved with the item in the world file.

**Books.** `GenTome` (`CREATE ITEM GenTome`) keeps its pages in the item, so
they go into world files and every new game. Its readable text holds
`<PAGE TITLE="..." LANG=Elvish LORE=ost.fall>text</PAGE>` pages one after
another, or `FILE=world/texts/ost.txt`, in which a line `=== The Fall of Ost
===` (or `=== The Fall of Ost | LANG=Elvish LORE=ost.fall ===`) starts each
page. `READ BOOK` lists pages; `READ 3 BOOK`, `READ NEXT BOOK`, `READ PREV
BOOK` and `READ [TITLE] BOOK` read one. A page in a language is read only by one
who knows it; `LORE=key` gives the reader the tattoo `LORE:key`; `READ_PROG
PAGE 7` (or `PAGE [TITLE]`) fires a script for that page; `TEAR PAGE 3 FROM
BOOK` gives a loose page. Ship the `FILE=` text with the world; `CHECKTEXT`
checks missing links. `READ [DIRECTION]` reads writing on an exit. Help:
`AHELP GENTOME`.

| Property / system | What it adds |
| --- | --- |
| [Prop_When](#prop_when) | Properties and behaviours that exist only while a condition holds |
| [Prop_Transform](#prop_transform) / [MPTRANSFORM](#mptransform) | Change form over time or condition; replace an item in place |
| [Prop_ItemSet](#prop_itemset) | Named sets, worn counts, tier effects, identification progress |
| [Prop_Cursed](#prop_cursed) | A curse that Remove Curse ends cleanly |
| [Prop_Invokable](#prop_invokable) | Powers on demand: words, charges, recharge, cooldown, masks |
| [Prop_Attunement](#prop_attunement) | Powers that wake only after an attunement |
| [Prop_ItemGrowth](#prop_itemgrowth) | Growth with use, with saved progress |
| [Durability options](#prop_durability) | Wear rate, broken instead of destroyed, unbreakable |
| [ItemUpgrader](#itemupgrader-and-prop_upgradepath) | Upgrade paths at a smith or workbench |
| Prop_ImproveGather | A gathering tool; see [Gathering skills](#gathering-skills) |
| Prop_Workstation, Prop_Furnishing | Workstations and furniture; see above |

## Puzzle primitives

CoffeeSUD gives world creators these pieces. None needs Java or a script,
although each can run script commands when something happens.

| Primitive | Where | Player action / author contract |
| --- | --- | --- |
| [Prop_Switch](#prop_switch) | Item or exit | Pull, push, turn, press or flip; named or numeric states; actions per state; reset; one-shot |
| [Prop_PressurePlate](#prop_pressureplate) | Room, item | Weight or presence activates and releases |
| [Prop_ComboLock](#prop_combolock) | Exit, container | Dials, code words, a keypad |
| [Receptacles](#receptacles-prop_limitedcontents) | Container, room | Accept, count or consume the right items; run actions |
| [Riddler](#riddler) | Mob or item | Questions with several answers, tries, consequences |
| [Prop_Discoverable](#prop_discoverable) | Item, exit, mob | Hidden things found by SEARCH or EXAMINE |
| [Prop_BlockedExit](#prop_blockedexit) | Exit | A blockage cleared by digging, hitting or burning |
| [Prop_Detector](#prop_detector) | Item | A carried tool that reacts to what is near |
| [Keys and locks](#keys-and-locks) | Key, exit, container | Master keys, consumable keys, unpickable locks |
| [USE and COMBINE](#use-and-combine) | Any two things | Event scripts and rows of `world/combinations.txt` |
| [Traps](#traps) | Room, exit, item | Authored triggers and disarms |
| [PUZZLE](#the-puzzle-command) | Command | List, set, reset and signal the above |

### Actions

Most puzzle properties take **actions**: a string of script commands to run when
something happens (`ACTION_up=`, `ON=`, `OFF=`, `ONOPEN=`, `ONWRONG=`,
`ONFULL=`, `ONCLEAR=`, `ONRIGHT=`, `ONOUT=`, `ONSTART=`, `ONEND=`, a combination
row's `ACTIONS=`, a Cursed item's deeds). Rules (`core/Actions`):

- Commands are separated by `;` (or a newline). A semicolon always splits,
  even inside quotes, so an `echo` in an action cannot contain one.
- They are MOBPROG commands, and the `MP` prefix may be left off: `door open
  Gate#3 east; signal WORLD gate1 up; unhide 'a narrow stair'` runs `MPDOOR`,
  `MPSIGNAL` and `MPUNHIDE`. A word that is a command in its own right is not
  prefixed. The full command list is [27-mobprog-commands.md](27-mobprog-commands.md).
- They run as the object's own script would, `$n` being the one who did it.
  For an exit, they run for the room of the one who did it.
- The commonest are `door [OPEN|CLOSE|LOCK|UNLOCK|TOGGLE] [ROOM] [DIR|CONTAINER]
  [QUIET|"message"]` (`MPDOOR`, below), `signal [ROOM|AREA|WORLD|TARGET] [NAME]
  [ARGS]` (`MPSIGNAL`), `setvar [SCOPE] [NAME] [VALUE]`, `echo`, `exp $n 100`,
  `cast [SPELL] $n`, `unhide '[item]'`.

**`MPDOOR OPEN|CLOSE|LOCK|UNLOCK|TOGGLE room dir|container [QUIET|"message"]`**
changes a door or container the way a character would: **both sides** at
once, with a message in both rooms, and it starts the door's relock timer.
`room` is `HERE`, a room ID, or a thing whose room it is, so a lever can open a
door in a faraway room. `DOORSTATE(room dir|container)` reads it back as
`open`, `closed` or `locked`.

### Prop_Switch

A lever, button, crank or dial that **keeps its state**, on an item or an exit.

| Parameter | Meaning |
| --- | --- |
| `VERBS="pull,push,turn,press,flip"` | Words that move it to the next state (this is the default list). `ACTIVATE` and `DEACTIVATE` always work: second and first state |
| `STATES="down,up"` | State names (words, commas between) or a number range `1-8` for a dial. Default `down,up` |
| `START=` | The first state (default: the first listed) |
| `DESC_[state]=` | Text added to LOOK and EXAMINE in that state |
| `MSG_[state]=` | What the room sees when it moves to that state (`<S-NAME>` who, `<T-NAME>` it); a default `<S-NAME> pulls <T-NAME>.` otherwise |
| `ACTION_[state]=` | [Actions](#actions) run on arriving in that state |
| `RESET=ticks` | After that many ticks away from `START`, it goes back to `START`, running that state's action |
| `REQUIRES=` (or `MASK=`) | Zapper mask on who may move it; `REQMSG=` is told to the others (default "It will not move.") |
| `ONCE=true` | It can be moved once only; later tries get `REQMSG`. `PUZZLE RESET` rearms |

Players type `PULL LEVER`, `TURN DIAL` (to the next state), `TURN DIAL TO 3`
(a specific one), `ACTIVATE LEVER`. The verb is taken *before* the game's own
command of that name. State names are lower-cased, and the keys use them:
`ACTION_up`. The current state is kept in the property's text as `STATE=` (and
`MOVED=true`), so saves and world files keep it. Scripts read it with
`SWITCHSTATE(thing)` or `SWITCHSTATE(room:dir)` and react with the trigger
`SWITCH_PROG [state|ALL]` on the switch's own scripts or its room's
([26-mobprog-triggers.md](26-mobprog-triggers.md)).

```text
STATES="down,up" START=down MSG_up="The east gate grinds open." MSG_down="The east gate grinds shut." ACTION_up="door open CreatorMuseum#0 east QUIET; setvar WORLD MUSEUM_GATE 1" ACTION_down="door close CreatorMuseum#0 east QUIET; setvar WORLD MUSEUM_GATE 0"
VERBS=turn STATES=1-8 ACTION_5="signal WORLD vault dial5"
```

`PROPCHECK` may warn about the `MSG_up`-style keys even though they are
valid (a known validator gap, see
[developer tools audit](../../audit/developer-tools-audit.md#open-property-key-validation-gap)).
A switch is not a multi-step puzzle by itself; give a puzzle with several
switches shared state with `setvar` and `signal`, below.

### Prop_PressurePlate

A pressure plate on a **room** weighs the mobs there (with all they carry) and the items lying there. On a
**fixture** (an item) it weighs what is on or in it and who sits on it.

| Parameter | Meaning |
| --- | --- |
| `WEIGHT=test` | Total weight test |
| `COUNT=test` | Number of mobs test |
| `MASK=` | A zapper mask for what counts |
| `ON=` / `OFF=` | [Actions](#actions) when it goes down / comes up again |
| `MSGON=` / `MSGOFF=` | Messages shown there |
| `DELAY=ticks` | It comes back up that many ticks after the weight leaves; pressed again meanwhile, it stays down |

A *test* is `>=120`, `>50`, `<=10`, `<3`, `=2`, or a bare number (at least).
The plate looks again whenever someone enters or leaves, things are got, put
or dropped, or someone sits or stands on it. Scripts get `ROOMWEIGHT(room)` and
`CONTENTWEIGHT(thing)`.

```text
WEIGHT=">=50" OFF="cast Spell_Lightning $n" MSGOFF="The pedestal rises with a click!"
COUNT=">=2" ON="door open here north" OFF="door close here north" DELAY=2
```

### Prop_ComboLock

A combination lock on an exit or a container.

| Parameter | Meaning |
| --- | --- |
| `CODE=` | The code: `739`, `7-3-9`, `sun-moon-star`, or `RANDOM` |
| `SYMBOLS=` | Each dial's symbols: `0-9` (default), or a list `"sun,moon,star"` |
| `DIALS=` | Number of dials (default: as many as the code has; 3 for RANDOM) |
| `VERB=` | The word for turning a dial (default `turn`) |
| `SHOW=true` | LOOK reads what the dials show |
| `TRIES=n` | After n wrong codes, `ONWRONG` runs (0 = never) |
| `ONWRONG=`, `ONOPEN=` | [Actions](#actions) |
| `RELOCK=ticks` | After opening, it shuts and locks again that many ticks later |
| `MSG=` | What is seen when it opens |
| `VAR=NAME` | With `CODE=RANDOM`: the rolled code is put in the script variable `$<COMBO NAME>` so a clue can print it |

Players: `TURN DIAL 2 TO 3` (one dial), `SET DIALS 7 3 9` (all), `ENTER 739` (a
keypad code); `ON [THING]` picks the lock when there are several. When the
dials read the code it unlocks and opens, on both sides of a door, runs
`MSG` and `ONOPEN`. `CODE=RANDOM` is rolled for each new game and kept with the
lock. The dials are kept as `READ=` and a rolled code as `GOT=` in the text.

```text
CODE=sun-star-moon SYMBOLS="sun,moon,star" SHOW=true TRIES=3 ONWRONG="cast Spell_ShockingGrasp $n"
CODE=739 RELOCK=30
CODE=RANDOM DIALS=4 VAR=VAULTCODE
```

### Receptacles: Prop_LimitedContents

A **receptacle** is a container (or room) that reacts to the right objects.
`Prop_LimitedContents` takes `[MAX NUM] ([MASK])`, and several such
clauses separated by `;`. No more than `NUM` items matching the mask may be
put in a container (or dropped in a room). The first clause whose mask the item
matches applies; matching none means no limit. CoffeeSUD adds:

| Parameter | Meaning |
| --- | --- |
| `MSG="text"` | Refusal text |
| `ACTUAL=true\|false` | Count the real contents |
| `LOCKIN=true` | What is put in cannot be taken out; `LOCKMSG` is told |
| `CONSUME=true` | What is put in vanishes but is still counted (kept as `TALLY=`) |
| `ONPUT=` | [Actions](#actions) run when something goes in |
| `ONFULL=` | Run when each limit above 0 is met |
| `ONEMPTY=` | Run when the last thing is taken out |

```text
2 -TAGS +DRAGONEYE;0;MSG="It does not fit.";LOCKIN=true;ONFULL="door open here north; echo The dragon's mouth grinds open."
3 -javaclass +genweapon
```

The mask is an item zapper mask (see [24-behaviors-and-masks.md](24-behaviors-and-masks.md)).
In the example, two items tagged `DRAGONEYE` fit; anything else (a limit of 0)
is refused; when the second goes in, the door opens.

### Riddler

`Riddler` is a behaviour on a mob or an item that asks riddles.

| Parameter | Meaning |
| --- | --- |
| `RIDDLE=`, `ANSWERS="a\|b"` | The question and its accepted answers. `RIDDLE1`..`RIDDLE9` with `ANSWERS1`..`ANSWERS9` add more; `RANDOM=true` picks one, else the first |
| `FUZZY=n` | Forgives n wrong letters (1 forgives one wrong letter, or two swapped). Case, a/an/the and punctuation never count |
| `ASK=` | `GREET` (asks one of the player's side who comes in), `LOOK` (one who looks at it), `COMMAND` (one who types `RIDDLE`). All three by default |
| `TRIES=n` | After n wrong answers (3), `ONOUT` runs |
| `ONRIGHT=`, `ONWRONG=`, `ONOUT=` | [Actions](#actions); `$n` is who answered, `$i` the riddler |
| `RIGHTMSG=`, `WRONGMSG=` | Text for each answer |
| `ONCE=` | `CHARACTER` (default): asks each character until they solve it; `SAVE`: until anyone does, kept with it; `NONE`: always |

Players answer by saying it (`SAY mountain`) or `ANSWER mountain`.

```text
RIDDLE="What has roots nobody sees, and is taller than trees?" ANSWERS="mountain|mountains" FUZZY=1 TRIES=3 ONRIGHT="door open here north; exp $n 100" ONOUT="force $i kill $n"
```

### Prop_Discoverable

Something hidden that anyone can find, on an item, an exit or a mob. Until it
is found nobody sees it, and nobody goes through a hidden exit.

| Parameter | Meaning |
| --- | --- |
| `FOUNDBY=` | `search`, `examine:[THING]` or `look:[THING]`, separated by `\|`. `SEARCH` needs no skill, and one SEARCH looks for everything hidden in the room. Default `search` |
| `CHECK="INT 12"` | A d20 plus the stat's (N-10)/2 plus a tenth of Searching's or Thief_Search's proficiency against the number. No check means found at once |
| `DC=n`, `STAT=` | The same check in two parameters |
| `MSG=` | What the finder is told |
| `HINT=` | Text added when one of its `FOUNDBY` things is examined or looked at |
| `STAYS=false` | It hides again `RESET` ticks (30) after being found |

Once found it stays found (`FOUND=true` kept in the text).

```text
FOUNDBY=examine:tapestry MSG="Behind the tapestry is a narrow door." HINT="The tapestry stirs in a draught."
FOUNDBY=search|examine:fireplace CHECK="INT 12"
```

### Prop_BlockedExit

A blockage on an exit that the player must clear. While it stands, nobody goes
that way and LOOK at the exit says so.

| Parameter | Meaning |
| --- | --- |
| `NAME=` | The blockage's name ("a pile of rubble") |
| `HP=n` | Hit points (default 40) |
| `TOOL=` | A zapper mask on the tool held or wielded (`-TAGS +PICKAXE`) |
| `SKILL=STAT+SKILL` | Adds a stat's (N-10)/2 and a skill's proficiency/10 to each try |
| `FIRE=true` | `BURN` with a lit light in hand clears it at once |
| `MSG=` | Shown with each try |
| `CLEARMSG=` | Shown on both sides when it is gone |
| `ONCLEAR=` | [Actions](#actions) |
| `RESPAWN=ticks` | It comes back that many ticks later (0 = never) |

`CLEAR`, `DIG`, `SMASH`, `HIT` or `ATTACK` the direction (or the blockage by name)
wears it down by 1d6 plus the skill each try. A weapon strike uses normal
combat-adjusted damage and the normal message hooks; digging and clearing keep
their tool and skill rules. What is left is kept with it (`LEFT=`), and a
blockage on the other side of the exit is kept the same.

```text
NAME="a pile of rubble" HP=40 TOOL="-TAGS +PICKAXE" SKILL=Digging+STR CLEARMSG="The rubble gives way to a dark tunnel."
NAME="a wall of vines" HP=60 FIRE=true RESPAWN=3000
```

### Prop_Detector

An item that reacts to what is near.

| Parameter | Meaning |
| --- | --- |
| `DETECT="mask"` | Mobs it detects (a zapper mask) |
| `DETECTITEM="mask"` | Items it detects (an item mask) |
| `RANGE=0-3` | Rooms away it can sense (0 = same room) |
| `WHERE=` | `WORN`, `HELD` or `ANY` (carried, or lying anywhere) |
| `EVERY=ticks` | How often it looks (1) |
| `AMBIANCE=` | Shown on the item while it finds something |
| `MSG=`, `STOPMSG=` | Told to the carrier when it starts and stops (`$p` the item) |

```text
DETECT="-RACECAT +Orc +Goblinoid" RANGE=2 WHERE=WORN AMBIANCE="(glowing blue)" MSG="$p glows faintly blue." STOPMSG="$p's glow fades."
DETECTITEM="-TAGS +TREASURE" MSG="$p twitches in your hands." WHERE=HELD
```

### Keys and locks

Doors, locks and the key item itself are described in
[20-areas-rooms-exits.md](20-areas-rooms-exits.md). CoffeeSUD adds two
properties for richer rules.

**`Prop_KeyRules`** on a key:

| Parameter | Meaning |
| --- | --- |
| `CONSUME=true` | The key crumbles once it has unlocked something |
| `BREAK=pct` | That percent of the time it snaps when used |
| `RETURN=ROOM_ID` | Once used it goes back to that room |
| `MASK=`, `MSG=` | Who may use it; what the others are told |

A key's code may be a **list** (`crypt;vault` opens both) or end in `*`
(`jail.*` is a master key for `jail.1`, `jail.2`...). A key on a key ring is
found however deep the ring is in a pack.

**`Prop_LockRules`** on an exit or container:

| Parameter | Meaning |
| --- | --- |
| `PICK=NEVER` or `PICK=LEVEL:n` | No thief picks it, or only one of level n or more (`MSG` tells the others) |
| `KEYS=code;code` | Key codes it takes besides its own (a code ending in `*` takes all that begin so) |
| `RELOCK=ticks` | That many ticks after it is unlocked it shuts and locks again |
| `MSG=` | Text for refusals |

`Prop_CloseCommand` on an open container or exit makes a phrase close it
(`twist knob;tw knob;noclose`; `noclose` also disables the normal `CLOSE`): a
way to block ordinary actions and require the puzzle's own wording.

### USE and COMBINE

`USE [ITEM] (ON [TARGET])` and `COMBINE [ITEM] WITH [ITEM]` do whatever the
world says. What happens is one of:

1. A `USE_PROG` or `COMBINE_PROG` script on either thing: `USE_PROG
   ([KEYWORDS]/ALL)`; `$n` who, `$o` the first, `$t` the second (or the first
   alone); the keywords are looked for in the other thing's name.
2. A row of `world/combinations.txt` (only if no script took it). Otherwise
   "Nothing happens."

Each row is `A | B | RESULT | keys`, one per line, `#` for a note:

| Field | Meaning |
| --- | --- |
| `A`, `B` | A name (or part of one), `tag:X`, or `catalog:X`. `B` empty means `USE A` alone. The order does not matter |
| `RESULT` | A catalog item or class given to the user; blank for nothing |
| `VERB=` | `USE` or `COMBINE` (either by default) |
| `CONSUME=` | `A`, `B`, `BOTH` or `NONE`: which things are used up |
| `MASK=` | Zapper mask on who does it |
| `SKILL=` | A skill the user must have |
| `FAILMSG=` | What a failure says (default "You do not know how.") |
| `MSG=` | What the room sees (`<S-NAME>` who, `<O-NAME>` the first, `<T-NAME>` the second) |
| `ACTIONS=` | [Actions](#actions) for the first thing |

```text
a torn map | a torn map | the old smuggler's map | VERB=COMBINE CONSUME=BOTH MSG="<S-NAME> fit(s) the torn halves together."
oil flask | squeaky hinge | | VERB=USE CONSUME=A ACTIONS="setvar WORLD HINGE oiled" MSG="<S-NAME> oil(s) <T-NAME>."
tag:WAND | | | VERB=USE MASK="-NAMES +Bo" FAILMSG="You cannot make it work."
```

The file is re-read when it changes.

### Traps

`GenTrap` is a data-defined trap (`CREATE ABILITY GenTrap`, `MODIFY ABILITY
[ID]`). Besides its messages, damage and ability it has:

| Field | Meaning |
| --- | --- |
| `TRIGGERS` | What springs it instead of its class default: message types (`SIT`, `READ`, `LOOK`, `EXAMINE`, `OPEN`, `CLOSE`, `WEAR`, `HOLD`, `PUT`, `DROP`, `GET`), commas between; on a room `ENTER`, `LEAVE:[DIRECTION]` and the others, by anyone there |
| `DISARM` | `verb:[PHRASE]`, typed there, disarms it until it resets, besides the usual skills |
| `ONCE` | It springs once and is gone |
| Script | `ACT_PROG [TRAP ID]` when it springs, `$t` the one who sprang it; `FUNCTION_PROG ONTRIGGER` ( `$n` who sprang it) and `ONDISARM` (`$n` who disarmed, if known). It runs with or without an ability |

Help: `AHELP GENTRAP`. See also [25-abilities-and-effects.md](25-abilities-and-effects.md).

### The PUZZLE command

```text
PUZZLE LIST ([AREA])
PUZZLE CHECK (AREA [NAME])
PUZZLE SET [TARGET] [STATE]
PUZZLE RESET [TARGET]
PUZZLE SIGNAL [ROOM/AREA/WORLD/TARGET] [NAME] ([ARGS])
```

`LIST` shows the switches, plates and blockages and the script signal wiring.
`CHECK` checks rooms, doors and loadable objects the puzzle refers to. `SET`
moves a switch and **runs its real actions** (it changes the world); `RESET`
returns it to `START` and rearms `ONCE`. `SIGNAL` uses the normal `SIGNAL_PROG`
delivery path. This console works with the existing primitives; a generic
puzzle controller (I12) is deferred. Build multi-step puzzles with explicit
saved variables and signals, and plan reset behaviour as part of the design.

### Designing a puzzle

- Provide a **recoverable path** after a wrong input, a dropped component, an
  NPC's death, or leaving halfway through. `RESET=` on a switch, `RELOCK=` on
  a lock, `RESPAWN=` on a blockage, and `PUZZLE RESET` for the builder.
- Store a solved clue **per character or profile**, or per physical puzzle
  instance, and not by its current description. Duplicate source IDs
  intentionally share clue state (see
  [state-and-persistence.md](state-and-persistence.md)).
- Put durable facts in `setvar WORLD ...` or a `signal` instead of relying on
  the state of a description. A description can read the variable with
  `<VARIES><IFVAR MUSEUM_GATE=1>...</IFVAR></VARIES>`
  ([11-presentation-language.md](11-presentation-language.md)).
- Use the same door action in both directions, `MPDOOR`, so both sides agree.
- Test: operate, leave, save, restore, operate again.

## Changing items: transformation, growth, attunement and more

### Prop_When

Properties and behaviours that are there only while a condition holds.

| Parameter | Meaning |
| --- | --- |
| `PROPS="Prop_X(args);Behavior_Y(args)"` | What it wraps (abilities or behaviours, `;` between), or `ID=` with `PARMS=` for one |
| `WHEN="mask"` | A zapper mask tested on what `CHECK` says (it may use an `-IF` condition, as `-IF ISMOON(FULL)`) |
| `CHECK=` | `HOST` (the thing it is on; default), `OWNER` (an item's owner), `ROOMPLAYERS` (any of the player's side in its room), `TRIGGERER` (the last of them to come in) |
| `EVERY=ticks` | How often it looks again (1) |
| `CHANCE=pct` | Chance each time it would come on |
| `COOLDOWN=ticks` or `nH` | Ticks, or game hours with `H`, before it may come on again |
| `ONCE=CHAR\|PROFILE\|WORLD` | Once a character (a tattoo), a profile, or a game |
| `ONSTART=`, `ONEND=` | [Actions](#actions) |
| `ELSEMSG=` | Told to one who comes in while it is off |

What it wraps is added when the condition comes true and removed when it
stops; it is **not** saved. Its cooldown and `ONCE=WORLD` are.

```text
WHEN="-IF ISMOON(FULL)" PROPS="RandomMonsters(catalog:werewolf;3;5);Emoter(min=5 max=10 howls)"
CHECK=ROOMPLAYERS COOLDOWN=24H ONCE=CHAR PROPS="Prop_Transporter(Feywild#1)"
```

### Prop_Transform

A thing that changes form.

| Parameter | Meaning |
| --- | --- |
| `AFTER=ticks` / `DAYS=n` | The time: ticks or game days; what is left is kept (`LEFT=`) |
| `WHEN=` | Instead: `NIGHT`, `DAY`, `DAWN`, `DUSK`, `FULLMOON`, a season (`WINTER`...), or a zapper mask tested on its room |
| `START=` | When the timer runs: `GET` (carried), `DROP` (lying in a room), `WEAR` (worn), `ALWAYS` (default) |
| `RESET=true` | Start again when `START` stops holding |
| `ONLYIN=` | `ROOM`, `CONTAINER` or `CARRIED`: where the time runs |
| `INTO="item"` | An item (from the catalog, or a class) in its place |
| `MOB="mob"` | A mob appears there instead |
| `SET="NAME=.. DISPLAY=.. DESC=.. PROPS=.."` | Changes it in place (`PROPS` adds properties) |
| `REVERT=true` | With `WHEN`, changes back when it ends |
| `KEEP=` | `FOLLOW` (a mob follows the carrier), plus `STATE,TAGS,USES,CONTAINER,WORN` (the last two by default) |
| `MSG=` | Shown (`$p` is what it became) |

What it becomes may have its own `Prop_Transform`: a chain.

```text
DAYS=3 START=GET ONLYIN=CARRIED MOB="a wyrmling" KEEP=FOLLOW MSG="$p cracks open!"
WHEN=NIGHT REVERT=true MOB="a stone gargoyle" MSG="$p stirs and spreads its wings!"
```

### MPTRANSFORM

`MPTRANSFORM item name [KEEP=state,tags,uses,container,worn|ALL|NONE]`
replaces an item in place (carried by the same one or lying in the same room;
`$p` is the new one). JavaScript has `transform()`. The same engine
(`core/Transform`) serves Prop_Transform and ItemUpgrader. `KEEP`: `STATE` (script
variables and states), `TAGS`, `USES` (uses or condition), `CONTAINER`, `WORN`.
Contents stay in it (or fall out if the new item holds nothing).

### Prop_ItemSet

A piece of an item set.

| Parameter | Meaning |
| --- | --- |
| `SET=id` | The set's id. Every piece has it |
| `NAME=` | Display name |
| `COUNT=` | `WORN` (default) or `HELD` (carrying counts) |
| `TIERn="Prop_X(args);..."` | What wearing n pieces gives. Every tier met applies; its properties go on one of the counted pieces, unsaved, and come off when a piece does |
| `PIECES=n` | How many pieces there are |
| `ONMSG=`, `OFFMSG=` | Told as a tier comes and goes (`$t` the tier) |
| `MASK=` | Who may have the tiers |

One piece carries the tiers; the others need only `SET=id`. A set may instead
be defined in `world/itemsets.txt`, one `SET=[ID] NAME=... TIER2=...` line per
set, for a set none of whose pieces says. LOOK at a piece shows
"Set: 3/5 worn" and each tier, and identification shows active and inactive
tiers. Scripts: `SETCOUNT([CODE] [SET])` and `SET_PROG`. The legacy
`ALLSET="name-3"` option on `Prop_HaveAdjuster`/`Prop_WearAdjuster` gives each
piece its own number of pieces needed. Count correctly worn pieces, not items
merely carried (unless `COUNT=HELD`). Test equip, remove, destruction, death and
restore.

```text
SET=coastguard NAME="Coastguard's Oath" PIECES=2 TIER2="Prop_WearAdjuster(con+2)"
SET=coastguard
SET=drowned_king NAME="Regalia of the Drowned King" PIECES=5 TIER2="Prop_WearAdjuster(savewat+20)" TIER4="Prop_WearSpellCast(Spell_WaterBreathing);Prop_WearEnabler(Chant_Tsunami)" ONMSG="The regalia stirs."
```

### Prop_Cursed

A curse on an item that Remove Curse ends cleanly. It is of the cursing
domain, so Remove Curse finds it, but only a priest of `LEVEL` or more lifts
it.

| Parameter | Meaning |
| --- | --- |
| `LEVEL=n` | Least level of the priest who can lift it |
| `HIDDEN` | Shows nothing until first worn, when `REVEAL` is told. Detection also depends on the examiner's level |
| `REVEAL="text"` | Told when it first shows |
| `NOREMOVE`, `NODROP` | It will not come off, or be dropped |
| `PENALTY="Prop_X(args);..."` | What it does while it holds |
| `BREAK="SAY:words;ENTER:ROOM_ID"` | Deeds, in order, by the one who has it, that break the curse |
| `BREAKMASK="mask"` | A mask the wearer comes to fit |
| `ONBREAK=` | `KEEP` (default), `CRUMBLE` (the item goes), or `BECOME:[ITEM]` |
| `BREAKMSG=` | Told when it breaks |

Lifted or broken, its penalties go with it. Its state is kept in the text
(`SHOWN=`, `STEP=`).

### Prop_Invokable

An item's powers on demand, for anyone, with no class skill.

| Parameter | Meaning |
| --- | --- |
| `WORD="Ignis"` | The holder says it. Without `WORD`, the user types `VERB` and its name (`INVOKE [ITEM]` by default) |
| `VERB=` | The command word |
| `SPELLS="Spell_Fireball;LEVEL=12,Spell_Light"` | What it casts; each at `LEVEL` (or the item's level) |
| `TARGET=` | `SELF`, `ENEMY` or `ROOM` |
| `CHARGES=n` | Uses held; what is left is kept with it (`LEFT=`) |
| `RECHARGE=` | `DAWN`, `DAILY` or `TICKS=n` |
| `COOLDOWN=ticks` | Ticks between uses |
| `REQUIRE=` | `WORN` or `HELD` |
| `MASK=` | Who may use it |
| `MSG=`, `EMPTYMSG=` | What the room sees (`<S-NAME>`, `<T-NAME>`); what the user is told when it is spent |

Identifying it tells its charges and recharge.

```text
WORD=Ignis SPELLS="Spell_Fireball;LEVEL=12" TARGET=ENEMY CHARGES=3 RECHARGE=DAWN REQUIRE=WORN EMPTYMSG="The ring is cold."
```

### Prop_Attunement

Powers that stay inert until the item is attuned.

| Parameter | Meaning |
| --- | --- |
| `PROPS="Prop_X(args);..."` | What wakes on attunement |
| `HOW=REST:ticks` | Attune by resting (sitting or sleeping) with it that long |
| `RITUAL="INROOM ROOM_ID & SAY words"` | Attune by a ritual: in the room, saying the words (either part may be left out) |
| `ON=EQUIP` | Attune by wearing it |
| `LIMIT=n` | How many items one may be attuned to (3) |
| `BIND=true` | It stays inert in anyone else's hands |
| `MSG=`, `MASK=` | Told to the one who attunes; who may |

`UNATTUNE [ITEM]` frees one so the character can attune another. The bond is
kept as `ATTUNED=name` in the text. Identification says it needs attunement.

### Prop_ItemGrowth

An item that grows with use.

| Parameter | Meaning |
| --- | --- |
| `GAIN=` | `KILL` (kills by the wielder or wearer; `KILLMASK` says which), `HIT`, `USE`, `WORNTICK` |
| `XP="formula"` | XP per event (`@x1` the slain's level; default 1) |
| `KILLMASK="mask"` | Which kills count |
| `STAGES="100:NAME='a humming blade' PROPS=Prop_WearAdjuster(dam+2);400:NAME='Dawnbringer' PROPS=..."` | At each amount the item takes that stage's `NAME` (and `DISPLAY`) and `PROPS` |
| `MSG=` | Told to its holder on a new stage (`$p`) |
| `BIND=CHARACTER` | It grows for its first owner only |

Growth, stage and owner are kept with it, and identification shows the stage
and the way to the next.

### Prop_Durability

An item's own wear and tear. Defaults come from `ITEMWEAR` (percent of usual
wear, 100) and `ITEMBREAK` (`DESTROY`, or `BREAK` for unusable-until-mended)
in `coffeemud.ini`.

| Parameter | Meaning |
| --- | --- |
| `MULT=pct` | Percent of the usual wear it takes |
| `UNBREAKABLE` | No wear at all |
| `NOREPAIR` | Nobody mends it |
| `BREAK=true` | Worn out, it breaks rather than goes |
| `BROKENNAME="a shattered %s"` | Names the broken item (`%s` the old name); also implies breaking |

A broken item cannot be worn, held or wielded, gives no armour or damage, and
shows `(broken)` until mended (MEND, a smith). Below half its condition it shows
`(worn)`. This is the same "wear" as a recipe component's `wear(n)`, applied to
tools.

### ItemUpgrader and Prop_UpgradePath

`ItemUpgrader` is a behaviour on a smith (mob) or a workbench (item). Its rows
are `UPGRADE1=` to `UPGRADE9=`, or one per line in the file named by
`UPGRADES=` (for example `world/upgrades.txt`). A row:

| Key | Meaning |
| --- | --- |
| `FROM=name\|tag:X\|catalog:X` | What it takes (`\|` between alternatives) |
| `TO=catalog:ITEM` | The item becomes this in its place |
| `ADJ="dam+2 att+5 arm+3"` | Or the same item improved (`dam`, `att`, `arm`, `lvl`) |
| `COST=gold` | Price |
| `MATERIALS="starmetal ingot x3, coal"` | What is used up (`x3` for three) |
| `LEVEL=n` | Least level |
| `KEEP=` | As MPTRANSFORM's (`STATE`, `TAGS`, `USES`...) |
| `MSG=` | What is seen |

Players type `UPGRADE [ITEM]` to see what it takes and `UPGRADE [ITEM] CONFIRM`
to do it. An item can carry its own row, without `FROM`, as `Prop_UpgradePath`
(`TO="catalog:Starfall, the reforged blade" COST=2000 MATERIALS="starmetal ingot x3"`).

```text
UPGRADE1="FROM=notched TO=\"catalog:Starfall, the reforged blade\" COST=2000 MATERIALS=\"starmetal ingot x3\" LEVEL=20 KEEP=TAGS,STATE"
```

## Worked examples

Each example is built in builder mode ([02-world-workflow.md](02-world-workflow.md)).
Properties are added in the item's or room's **Effects** entry (`MODIFY ITEM`,
`MODIFY ROOM`); the quoted text is the property's parameter text. Where an
example needs a file, it goes in the world folder (paths below start at
`world/`). Check each with a fresh restore before shipping.

### 1. A forge with a custom recipe

Goal: a dwarven forge in the smithy that helps Blacksmithing, serves as the
fire, and unlocks a recipe nobody else can make.

1. **The forge.** `CREATE ITEM GenItem`, then `MODIFY ITEM` it: name "a dwarven
   forge", display text, description, and *Is Gettable* = No. Add the effect
   `Prop_Workstation` with:

   ```text
   TAG=forge,anvil,fire SKILLS=Blacksmithing SPEED=25 PROF=+10 MAXLEVEL=30 QUALITY=+1
   ```

   The `fire` tag lets it stand in for the burning item Blacksmithing wants;
   `MAXLEVEL=30` lets a crafter below level 30 make recipes up to 30 here.

2. **The recipe.** Create `world/resources/skills/blacksmith.txt.1` (an extension
   file, so the stock rows stay). One line, columns separated by tabs, shown here
   with ` | `:

   ```text
   % rune chisel | 12 | 40 | 3 | 45 | GenItem |  |  |  | STATION=forge TOOL=hammer MASK="-LEVEL +>11" XP=25 TATTOO=RUNE_CHISEL FAILMSG="Only a forge can temper runes into metal."
   ```

   Columns: name (`%` is the metal), level 12, 40 ticks, 3 units of
   metal, value 45, class `GenItem`, three empty class columns (a
   spell column may hold `*Prop_...`), then the keys column.

3. **Check.** `VALIDATE RECIPES blacksmithing` must report no problem.
   As a player below level 12, `BLACKSMITH LIST` does not show the chisel.
   At level 12 or more it appears; `BLACKSMITH iron rune chisel` without the
   forge, or without a hammer, is refused with the `FAILMSG` (a recipe's
   `FAILMSG` replaces the plain "You need a forge here." for every refusal);
   in the smithy, with the forge and a hammer, it works faster and awards 25 XP
   and the tattoo `RUNE_CHISEL` the first time.

4. **Optional quality.** Copy `resources/skills/craftquality.txt` to
   `world/resources/skills/craftquality.txt` and uncomment the `masterwork`
   and `fine` tiers. Items made at the forge now roll them with `@x3` raised
   by `QUALITY=+1`. Add `NOQUALITY=1` to a row to keep a recipe fixed.

For an unusual output material, add a row to `world/materials.txt` and let the
recipe name it; Blacksmithing already takes any METAL.

### 2. A herb garden

Goal: a kitchen garden that grows herbs only in warm seasons, a hedgerow that
gives wild herbs by season and gets picked bare, and a global rule for all
herbs.

1. **Planting rules for the whole world** (`world/crops.txt`, one crop a line,
   resource name first):

   ```text
   HERBS GROWHOURS=48 SEASONS=SPRING,SUMMER,AUTUMN YIELD=2d4 FAILMSG="Herbs will not take root in this season."
   ```

2. **The garden room.** An outdoor Plains room. Give it the effect
   `Prop_CropRules` to be stricter than the world file:

   ```text
   SEASONS=SPRING,SUMMER GROWHOURS=24 FAILMSG="It is too late in the year to sow the kitchen garden."
   ```

   The area's rule, the room's and the seed's come over the file's, in that
   order. Players plant with `GPLANT HERBS` after dropping a sample herb on the
   ground; the herbs appear in the room a day (24 game hours) later.

3. **The hedgerow.** A second outdoor room with the behaviour
   `ResourceOverride`:

   ```text
   SPRING=herbs SUMMER=herbs,flowers AUTUMN=herbs WINTER=none MSG="The hedgerow changes with the season."
   ```

   and the effect `Prop_Yield`:

   ```text
   HARVESTS=4 DROP=10% REGROW=3 DAYS EMPTYMSG="The hedgerow has been picked bare."
   ```

   `FORAGE` there finds herbs in spring, summer and autumn, nothing in winter;
   after four harvests it is bare for three game days.

4. **Check.** Plant in winter and read the refusal; `FORAGE` repeatedly and
   watch the yield fall; save and restore (growth and depletion are saved);
   advance the clock with the time tools and see it regrow. Crop growth uses
   game time, not the computer's clock.

If the cellar should grow mushrooms in the dark, put `NEEDSUN=false
DOMAINS=CAVE` in a `Prop_CropRules` on the cellar room.

### 3. A player home

Goal: a cottage the player already owns, where the party sleeps, stores things
and crafts, and which the party can return to.

1. **The room.** An indoor room "The Hearthstone Cottage". Effects:

   | Effect | Text |
   | --- | --- |
   | `Prop_RoomForSale` | `@PROFILE/0` (already owned by the whole profile, price 0) |
   | `Prop_HomeBeacon` | (empty) |
   | `Prop_ReqCapacity` | `items=60 people=6` |

   Every character of the profile owns it, so nothing in it is theft between
   them, and the owner `@PROFILE` is never wiped as a nonexistent player.

2. **Furniture.**
   - A hearth: `GenItem`, not gettable, effect `Prop_Furnishing`
     `ROOMEFFECT=Prop_RoomLit COMFORT=3 ACTS=fire`. The room is lit and the
     hearth counts as a fire for cooking.
   - A chest: a `GenContainer` that stays where it is.
   - A larder: a `GenContainer` with the effect `Prop_IceBox`, so food in it
     never spoils.
   - Optionally a cooking pot or bench with `Prop_Workstation`.

3. **A yard to build in.** The outdoor room outside can have `Prop_Homestead`
   with `SKILLS=Construction,Masonry MAXROOMS=3 CLAIM=PROFILE`, so the party
   can add a shed with the building skills without buying a deed.

4. **Behaviour.** `SLEEP` in the cottage makes it the start room of the
   party's characters with privileges there; `HOME` takes the whole party home
   (not during a fight) after `HOMERECALL` turns; `HOME SET` sets it without
   sleeping. A script can give the cottage as a reward with `MPDEED
   Hearthstone#1 @PROFILE` (or `MPDEED ... $n`) and ask `ISOWNER($n)`.

5. **Check.** From two characters of one profile: enter, use the chest, `HOME
   SET`, leave and `HOME`. Save, restore and test again. Leave a rack of items
   and a lit hearth: the effects go away with the item and are not saved with
   the room.

### 4. A lever and a door

Goal: a rusted lever in the antechamber opens the locked stone door east; the
door closes and locks again on its own.

1. **The door.** Between `Crypt#1` (east) and `Crypt#2` (west) make a closeable,
   lockable, closed and locked door, with no key. Put `Prop_LockRules` with
   `PICK=NEVER` on the exit so no thief bypasses the lever.
2. **The lever.** In `Crypt#1` make an ungettable `GenItem` "a rusted lever" and
   add `Prop_Switch`:

   ```text
   STATES="down,up" START=down VERBS="pull,push" DESC_up="The lever is jammed upward." DESC_down="The lever is down." MSG_up="<S-NAME> heave(s) <T-NAME> upward. Something grinds in the walls." MSG_down="<S-NAME> force(s) <T-NAME> down." ACTION_up="door unlock Crypt#1 east QUIET; door open Crypt#1 east; setvar WORLD CRYPT_OPEN 1" ACTION_down="door close Crypt#1 east QUIET; door lock Crypt#1 east QUIET; setvar WORLD CRYPT_OPEN 0" RESET=60
   ```

   `PULL LEVER` moves it to `up`; the unlock and open act on **both** sides of
   the door; after 60 ticks (about four real minutes with the default 4-second
   tick) it returns to `down` and its `ACTION_down` shuts and locks the door.
3. **A description that follows.** In `Crypt#1`'s description:
   `<VARIES><IFVAR CRYPT_OPEN=1>The east door stands open.</IFVAR><ELSE>The east
   door is sealed.</ELSE></VARIES>` (see
   [11-presentation-language.md](11-presentation-language.md)).
4. **Check.** `PUZZLE LIST` shows the lever with its state. Pull it, walk
   through, and wait for the reset with the party on the other side: make sure
   the door cannot strand them (give the other side a way back, or remove
   `RESET`). `PUZZLE SET lever up` and `PUZZLE RESET lever` test it without a
   player; remember `SET` runs the real actions. Save open and restore: the
   switch and the world variable are saved, so the door's state must agree
   with `STATE=` (a restart from the world files begins closed).

The Creator Museum example pack builds the same lever
([examples](examples/README.md); [cookbook, recipe 3](cookbook.md#3-a-lever-that-opens-a-gate)).

### 5. A combination lock

Goal: a vault door whose code is different every game, with a clue the player
can read.

1. **The lock.** On the exit east of `Vault#1` (a closed, locked door) add
   `Prop_ComboLock`:

   ```text
   CODE=RANDOM DIALS=3 SYMBOLS="sun,moon,star" VAR=VAULTCODE SHOW=true TRIES=3 RELOCK=100 MSG="The vault door clicks open." ONWRONG="echo The dials shudder and the floor trembles." ONOPEN="setvar WORLD VAULT_OPEN 1"
   ```

   The lock rolls its three symbols for each new game and keeps them with the
   lock. `SHOW=true` lets `LOOK` read the dials. Players use `TURN DIAL 1 TO
   moon`, `SET DIALS sun moon star`, or `ENTER` for a keypad lock with
   single-character symbols. After three wrong codes, `ONWRONG` runs; it opens
   with a click and shuts 100 ticks later.
2. **The clue.** `VAR=VAULTCODE` writes the rolled code into the script
   variable `$<COMBO VAULTCODE>`. Put it on a note item (a `GenItem` readable
   or any item) with a script (item scripts: Scriptable behavior text):

   ```text
   READ_PROG ALL
   MPECHOAT $n The faded note reads: $<COMBO VAULTCODE>.
   ~
   ```

   Print it the way you wish a clue to be (the player may need several
   notes, each giving one symbol).
3. **A fixed lock** is simply `CODE=739 SYMBOLS=0-9`. A three-dial lock with
   `CODE=sun-star-moon` has a fixed, learnable answer, good for a riddle
   puzzle whose answer is in a book.
4. **Check.** Look at the lock, turn dials, enter wrong codes until
   `ONWRONG` runs, then the right one; save and restore (the dials are kept as
   `READ=`); start a new game and see the code change.

### 6. A cursed item

Goal: a ring that looks fine, tightens on first wear, costs wisdom, cannot be
removed, and is broken by a confession in a chapel (or by a high-level priest's
Remove Curse).

1. Make a wearable `GenItem` "a gold ring" (wear location *left finger*).
2. Add `Prop_Cursed`:

   ```text
   LEVEL=15 HIDDEN NOREMOVE PENALTY="Prop_WearAdjuster(wis-3);Prop_WearSpellCast(Prayer_CurseLuck)" REVEAL="The ring tightens on your finger!" BREAK="SAY:i confess;ENTER:Chapel#3" ONBREAK=CRUMBLE BREAKMSG="The ring crumbles to ash."
   ```

   `HIDDEN` shows nothing until it is first worn; then `REVEAL` is told.
   `NOREMOVE` keeps it on. `PENALTY` lists the properties applied while the
   curse holds. `BREAK` lists deeds done in order by the wearer: say "i confess"
   and enter room `Chapel#3`. When broken, the penalties go and the ring
   crumbles; `ONBREAK=KEEP` would leave it as an ordinary ring and
   `BECOME:[ITEM]` would turn it into another.
3. **Check.** Identify it as a low-level character (the curse is hidden) and
   as a high-level one (examiner's level matters); wear it, try to remove it,
   cast Remove Curse at too low a level, then complete the deeds; save and restore
   with it worn.

## Persistence notes

| System | What is kept, and where |
| --- | --- |
| Recipe files, `craftquality.txt`, `hunting.txt`, `fishing.txt` | Files in `resources/skills/` and `world/resources/skills/`. Cached while running |
| `materials.txt` | Read once at start. Items save the resource name next to its code |
| `resources.txt`, `crops.txt`, `combinations.txt`, `itemsets.txt` | Read again when the file changes |
| Data skills | World files, `world/custom/abilities/` |
| Known recipes | `Prop_KnownRecipes` on the character, in the campaign save |
| Experiment tries | In memory, per name and game date |
| Crop growth, in-progress crafting and supported assistance | In the campaign save |
| Gather depletion | `YIELDS` in the save, on game time |
| Switch state, dials, found flags, curse state, charges, attunement, growth | In the property's own text (`STATE=`, `READ=`, `GOT=`, `FOUND=`, `SHOWN=`/`STEP=`, `LEFT=`, `ATTUNED=`, stage), so saves and world files keep it |
| Prop_When, Prop_Furnishing room effects, Prop_ItemSet tier properties | The wrapped or applied pieces are **not** saved; they are recreated. `Prop_When` cooldowns and `ONCE=WORLD` are |
| Ownership (`Prop_RoomForSale` and kin) | In the room's property text; with `@PROFILE`, every character of the profile |
| Script variables set by puzzles (`setvar WORLD ...`) | The scripting engine's saved scopes ([state-and-persistence.md](state-and-persistence.md)) |

A reload (`WORLD RELOAD`, new game) rebuilds everything from the world files;
the live state of puzzles in a continuing campaign comes from the save. A
puzzle whose door state lives in the exit and whose switch state lives in the
property must be tested through save and restore.

## Troubleshooting

- **A custom recipe does not show.** Rows must be tab-separated; check with
  `VALIDATE RECIPES [SKILL]`. A level above the crafter's, a failing `MASK` or
  an unlearned `HIDDEN=1` hides it. Extension files are read in order
  (`.1`, `.2`...); reading stops at the first missing file from `.2` on.
- **A stock update overwrote my change.** You edited the stock file. Put a copy
  in `world/resources/skills/`, or use `.1` extension files.
- **A recipe key does nothing.** Keys apply only to player characters, and only
  to rows whose column starts with `KEY=`. Check quoting (`FAILMSG="..."`).
- **The forge is ignored.** The item must be loose in the same room, its `TAG`
  must match `STATION=` and (for Blacksmithing) include `fire` or a burning item
  must be present; `SKILLS=` must list the skill.
- **No quality tier ever rolls.** The stock `craftquality.txt` has the tiers
  commented out, the skill has `QUALITY=false`, or the recipe has `NOQUALITY=1`.
- **EXPERIMENT does nothing.** The recipe needs both `HIDDEN=1` and
  `EXPERIMENT=`; the crafter's level must reach the recipe's; you may have used
  today's tries (`CRAFTEXPERIMENTS`).
- **Gathering gives nothing.** The room's resource may not suit the skill, may
  be depleted (`Prop_Yield`), may be blocked by a `Prop_ImproveGather AMT=0`, or
  a masked table row did not pass.
- **A crop will not plant.** Look at the refusal: season, sun, land, drought,
  water. `crops.txt` rows, then area, room and seed rules each apply.
- **A building skill refuses.** The builder needs a title, `CMDROOMS`, or a
  `Prop_Homestead` that fits their skill and mask.
- **A switch's actions do nothing.** The action text is a script command line;
  semicolons separate commands and cannot be quoted inside an action. A door
  action needs a room ID and direction that exist. Try the same line with
  `PUZZLE SET`.
- **A door opens on one side only.** Use `MPDOOR` (`door` in actions), which
  changes both sides.
- **A puzzle resets after a restore.** World files rebuild the initial state;
  campaign saves keep the live state. Test restore from a save and from a fresh
  start.
- **A property key warning in `PROPCHECK`.** The `ACTION_x`/`MSG_x` keys of
  `Prop_Switch` are valid; the warning is the validator's known gap.

## Reference: ini keys and world files

| Key / file | Where | Meaning |
| --- | --- | --- |
| `CRAFTXP` | `coffeemud.ini` | Percent of a craft's XP value every player character earns (0/blank: none) |
| `GATHERXP` | `coffeemud.ini` | XP per unit gathered, every player character |
| `CRAFTEXPERIMENTS` | `coffeemud.ini` | Experiments per character per game day (10 blank; 0 unlimited) |
| `DISMANTLEPCT` | `coffeemud.ini` | Percent of recipe materials DISMANTLE returns (50 blank; 0 off) |
| `PROFILERECIPES` | `coffeemud.ini` | `true`: recipes are shared by every character of the profile |
| `PROFILEPROPERTY` | `coffeemud.ini` | `true`: purchases are recorded as `@PROFILE` |
| `HOMEBEACONS` | `coffeemud.ini` | `true`: every owned room is a home |
| `HOMERECALL` | `coffeemud.ini` | Turns `HOME` takes (3 blank; 0 no `HOME`) |
| `ITEMWEAR`, `ITEMBREAK` | `coffeemud.ini` | Wear percent (100) and `DESTROY`/`BREAK` for worn-out items |
| `ADDMATERIAL_name` | `coffeemud.ini` | Older way to add a resource; `materials.txt` is preferred |
| `resources/skills/*.txt`, `world/resources/skills/*.txt(.1-.9)` | Recipe files | Rows per skill; the world's copy is read first |
| `resources/skills/components.txt` | Components | Ability components |
| `resources/skills/craftquality.txt` | Quality tiers | Tab-separated tiers |
| `world/materials.txt` | Resources | World materials |
| `world/resources.txt` | Variants | Named resource variants |
| `world/crops.txt` | Crops | `[RESOURCE or SUBTYPE] rules...` |
| `world/combinations.txt` | USE / COMBINE | `A \| B \| RESULT \| keys` |
| `world/itemsets.txt` | Item sets | `SET=id NAME=... TIERn=...` |
| `world/custom/abilities/` | Data skills | One file per skill |

These keys are in `CoffeeMud/coffeemud.ini` (search for the key). None of them is on
the `world.ini` allowlist ([world-settings.md](world-settings.md)), so they are not
set per world.

## See also

- [22-items.md](22-items.md): item classes and fields, materials, tags, catalog.
- [23-properties-reference.md](23-properties-reference.md): every property, including
  [Prop_Workstation](23-properties-reference.md#prop_workstation),
  [Prop_KnownRecipes](23-properties-reference.md#prop_knownrecipes),
  [Prop_Homestead](23-properties-reference.md#prop_homestead),
  [Prop_Furnishing](23-properties-reference.md#prop_furnishing),
  [Prop_Switch](23-properties-reference.md#prop_switch),
  [Prop_ComboLock](23-properties-reference.md#prop_combolock),
  [Prop_Cursed](23-properties-reference.md#prop_cursed).
- [24-behaviors-and-masks.md](24-behaviors-and-masks.md): `ResourceOverride`, `Riddler`,
  `ItemUpgrader`, and the zapper mask language used in recipe keys.
- [20-areas-rooms-exits.md](20-areas-rooms-exits.md): doors, locks and keys, room editing.
- [25-abilities-and-effects.md](25-abilities-and-effects.md): common skills, expertises, traps.
- [27-mobprog-commands.md](27-mobprog-commands.md), [28-mobprog-functions.md](28-mobprog-functions.md):
  `MPDOOR`, `MPLEARNRECIPE`, `MPDEED`, `MPGRANT`, `OWNER()`, `KNOWSRECIPE()`.
- [17-item-modifiers-crafting.md](17-item-modifiers-crafting.md): the optional tracked
  modifier workshop and its crafting transactions. Its costs use plain
  raw-resource piles; the legacy recipe crafting above keeps its own rules.
- [cookbook.md](cookbook.md): recipes 3, 16, 18, 19 and 29 for short versions of
  switches, sets, forges and crops.
- [tool-reference.md](tool-reference.md): generated help for `RECIPEKEYS`,
  `CRAFTQUALITY`, `GENCRAFTSKILL`, `WORLDMATERIALS` and every property.
- [state-and-persistence.md](state-and-persistence.md), [31-saves-and-party.md](31-saves-and-party.md).
