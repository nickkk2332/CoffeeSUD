# Common skills, part 2: building, cooking and crafting

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b043` (Abilities/Common [part 2/7: BuildingSkill .. Costuming]) · 16 files · 9397 lines of Java.

## Summary

These classes are the non-combat "common skills": activities a character performs over time, such as building rooms and exits (BuildingSkill, Construction), carving wood (Carpentry), cooking (Cooking), cobbling, costuming, clan crafting, composting, butchering, chopping, cataloguing and cargo loading. Every one of them derives from `CommonSkill`, directly or through `GatheringSkill` or `CraftingSkill`, and `CommonSkill` supplies the shared lifecycle: start checks, a resource cost, a tick-counted activity that aborts on combat, movement or incapacity, and an outcome that is applied only when the timer ends. The crafting families add recipe tables loaded from text files and a two-stage outcome, where success is rolled at the start and shown in the product's name. BuildingSkill is a data-driven world editor that changes rooms, exits and land titles from recipe rows. For a single-player SUD the activity lifecycle, recipes with optional ingredients and quality-in-name results are worth borrowing; clans, land ownership, helping and cargo ships are multiplayer assumptions.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Common/CommonSkill.java` | Base for all common skills: activity timer, abort rules, duration and cost formulas, recipe loading | 1016 |
| `Abilities/Common/BuildingSkill.java` | Recipe-driven construction and demolition of rooms, exits, walls, stairs, titles and room effects. Extends `CraftingSkill` | 2035 |
| `Abilities/Common/Construction.java` | Thin subclass of BuildingSkill; recipes from `resources/skills/construction.txt` | 105 |
| `Abilities/Common/Cooking.java` | Cooking in a pot: ingredient matching, batch counting, food, drink and resource generation. Extends `EnhancedCraftingSkill` | 1573 |
| `Abilities/Common/Carpentry.java` | Carving wooden weapons, containers, furniture, dice and light from `carpentry.txt`. Extends `EnhancedCraftingSkill` | 749 |
| `Abilities/Common/Cobbling.java` | Footwear from metal, wood, leather and cloth. Extends `EnhancedCraftingSkill` | 541 |
| `Abilities/Common/Costuming.java` | Paper garments; file recipes are empty, so recipes come from learned books | 651 |
| `Abilities/Common/ClanCrafting.java` | Clan-branded items that cost clan experience; siege structures need area population | 693 |
| `Abilities/Common/ClanShipwrighting.java` | Clan ship construction on the Shipwright base; clan-office check and ownership transfer | 138 |
| `Abilities/Common/CaravanBuilding.java` | Land and wagon vehicles on the Shipwright base; recipes from `caravanbuilding.cmare` | 106 |
| `Abilities/Common/CageBuilding.java` | Cages and cage carts on the Wainwrighting base | 110 |
| `Abilities/Common/Composting.java` | Turns vegetable or flesh food lying on the ground into compost. Extends `GatheringSkill` | 447 |
| `Abilities/Common/Butchering.java` | Skins a corpse into its race's resources. Extends `GatheringSkill` | 238 |
| `Abilities/Common/Chopping.java` | Finds a good tree and fells it for wood. Extends `GatheringSkill` | 222 |
| `Abilities/Common/Cataloging.java` | Writes an item's record into a catalog book. Extends `CommonSkill` | 409 |
| `Abilities/Common/CargoLoading.java` | Moves gettable items between a room and a ship's rooms. Extends `CommonSkill` | 364 |

## How it works

### 1. The activity lifecycle (CommonSkill)

- **Start checks.** `invoke()` (`CommonSkill.java:861-905`) refuses a new activity while the mob is in combat (866), riding (871), unable to see (878), sitting or asleep (884-885), or unable to move (904). It also aborts every other common-skill effect the mob carries, except those named by `getUninvokeException()` (890-902). A character therefore runs one activity at a time.
- **Costs.** `usageCost()` (`CommonSkill.java:533-643`) starts at `consumed=25` (560). If the recipe level is more than two below the mob's qualifying level, the cost falls by `diff/3`. A difference over 60 sets the cost to 5 (573-584). The floor is 5 (624). The result is a three-slot array for mana, movement and hit points, charged at 936-938. This class's `usageType()` is `USAGE_MOVEMENT` (124-127). How `buildCostArray` places the base amount in those slots was not read.
- **Duration.** `getDuration(baseTicks, mob, itemLevel, minDuration)` (`CommonSkill.java:346-362`):

  ```java
  final double pct=CMath.div(level,CMProps.get(mob.session()).getInt(CMProps.Int.LASTPLAYERLEVEL))*.5;
  ticks-=(int)Math.round(CMath.mul(ticks, pct));    // mob level above item level shortens the job
  ...
  final double quickPct = getXTIMELevel(mob) * 0.05; // the XTIME skill shortens it by 5% per level
  ```

  Each class passes its own floor, for example 10 (BuildingSkill.java:2024), 4 (Carpentry.java:559), 5 (Cooking.java:197) and 15 (Chopping.java:89).
- **Tick.** `tick()` (`CommonSkill.java:230-290`) aborts when the mob is in combat, has left its room, or is not alive, awake and unbound (237-242). It also aborts on ride, dark or sit conditions (245-252). At `tickDown==4` it prints the almost-done line (254-262). Every four ticks it prints progress with a percentage (264-272). It also nudges hygiene upward during work (278-282). `tickUp` accumulates the ticks already run (288).
- **Finish and abort.** `unInvoke()` (`CommonSkill.java:321-344`) prints "done" or "stop(s)". Each subclass applies its real effect here rather than in `invoke()`. Aborts come from the "stop" command (`checkStop`, 828-845), from `setMiscText("abort")` (848-858), or from a shutdown or quit message (213-227).
- **Clone pitfall.** `beneficialAffect` (`StdAbility.java:2341-2349`) copies the ability with `copyOf()` (`StdAbility.java:1424-1431`, a `clone()` plus an empty `cloneFix`, 1419-1421) and attaches the copy to the target. Fields set in `invoke()` before that call travel with the copy as a shallow copy. Fields set after it do not. ClanCrafting patches the live copy for this reason (`ClanCrafting.java:681-688`).
- **Persistence.** `startTickDown` sets `savable=false` (`StdAbility.java:703`). From the flag alone, an activity in progress is not meant to survive a reboot. This was not tested.
- **Teaching limits.** `canBeLearnedBy` caps how many common skills and how many crafting skills a student may hold (`CommonSkill.java:751-787`).
- **Failed placement.** If the output cannot be placed, `dropAWinner` prints "You have won the common-skill-failure LOTTERY! Congratulations!" and grants 50 experience (`CommonSkill.java:439-441`).

### 2. Outcome decided at the start, delivered at the end

Crafting classes call `messedUp=!proficiencyCheck(mob,0,auto)` inside `invoke()`, after the product already exists: Carpentry.java:715, Cobbling.java:507, Costuming.java:618, Cooking.java:1424, ClanCrafting.java:668 and BuildingSkill.java:2021. The result is visible straight away:

- **Cooking** prefixes the product with "burnt " when a fire is required and "ruined " otherwise. The description reads "It looks burnt!" (`Cooking.java:869-877`). A ruined food gets zero nourishment (881-882) and no material effects (1005-1007).
- **Carpentry** turns a failed carve into "<S-NAME> mess(es) up carving @x1." and drops the item as a loser (`Carpentry.java:152-153`). A good result goes to `dropAWinner` (177).
- **BuildingSkill** prints "You've ruined the @x1!" and leaves the room unchanged (`BuildingSkill.java:212-214, 272-276`).
- **Cataloging** rolls twice, once for itself and once for the writing skill (`Cataloging.java:397-399`). A failure clears the catalog and prints "You mess up your cataloging." (94-95).
- **Interruption.** Cobbling's `tick()` treats an interrupted build as messed up (`Cobbling.java:101-105`). Carpentry's `tick()` simply ends it (107-109).

### 3. Recipe data

- Recipe files are tab-separated text under `resources/skills/`. `CommonSkill` parses them with `CMLib.utensils().loadRecipeList` and caches them as `PARSED_RECIPE:` resources (`CommonSkill.java:293-318`). Each class documents its columns in `getRecipeFormat()`: Carpentry.java:79-87, Cooking.java:109-119, BuildingSkill.java:174-176 and ClanCrafting.java:79-87.
- Recipes also come from the character. `addRecipes` merges recipe items whose common-skill ID matches (`CraftingSkill.java:272-277`, `CoffeeUtensils.java:202-215`).
- A column written as `A||B||C` holds a different kind of value depending on the item class. In Carpentry, the armor-or-damage column is read as a number (`Carpentry.java:585`) and, when a container mask is set, as a resource name (583-584). The same string serves both purposes.
- `%` in a name is replaced by the main ingredient, the statue subject or the clan name (`CraftingSkill.replacePercent`; `Cooking.java:841`; `Carpentry.java:483, 595`; `ClanCrafting.java:563-566`).
- **Row choice.** BuildingSkill keeps the last matching row, because its loop has no `break` (`BuildingSkill.java:1439-1480`). Carpentry takes the first row at or below the character's level (`Carpentry.java:458-474`), and so do Costuming and Cobbling.

### 4. Cooking

**Pot and fire.** The pot must be a container of metal, glass, rock, mithril or precious material (`Cooking.java:1402-1413`). It must sit on or in a fire or oven unless `requireFire()` is false (1416-1422). The tick aborts if the pot contents change (301) or the heat goes out (303). Touching a hot pot is refused with "Ouch! That's HOT!" (265).

**Ingredient model.** `getPotIngredients()` (458-526) builds `PotIngredient` records holding raw name, subtype, category, material name, item name and secret identity. A liquid counts as `liquidRemaining/10` (`LIQUID_RATIO`, 138 and 468). Raw materials count by weight and other items count one each (519-522). Fish and berries get a category (501-505).

**Matching.** `ingredientMatch()` (609-676) tries, in order: item name, subtype, category, resource name and material name. An item with no raw resource falls back to a word-suffix match on its name or secret identity (659-671). A negative recipe amount is an optional additive, shown with `~` in the list (1344-1346). The recipe word "water" counts ten times over (564-565). Herbs are free when the herb rule is honoured (722-725).

**Batch count.** `countIngredients()` (539-607) takes one serving at a time from the pot contents. It stops when some entry reaches zero ("ran out") or goes negative ("not enough"). The number of full servings is the batch size (582). Shortfalls are reported through `missingIngredientsFromPot()` and `extraIngredientsInPot()` (734-755, 716-732).

**Selection.** `autoGenInvoke()` (1431-1512) looks first for "perfect" recipes, where every required ingredient is present and nothing is left over. Failing that, it accepts "closer" recipes that allow optional items to be absent. Otherwise it prints one line per near miss: "you need to remove ..." or "you need to add ...". Ties go to the first recipe in the file (1497-1512).

**Output.** A FOOD row becomes a `GenFood`, a DRINK row a `GenLiquidResource`, a class ID that exists becomes that class, and anything else becomes a `GenResource` (`Cooking.java:1176-1189`). Nourishment for food (881-929):

- Each Food ingredient adds `2n + 25`, where n is its nourishment. Each other ingredient adds 25. Herbs double the total.
- With no pot contents (auto-generation), each recipe ingredient adds `100 * amount` (923).
- The cook's skill adds `X1 level^2 * 10` (`homeCookValue`, 757-761, used at 998), and the total is divided by the batch size.
- Material is the first flesh or vegetation ingredient named in the recipe, else the first one in the pot, else the recipe name, else a liquid in the pot, else `RESOURCE_BEEF` (930-993).

Drinks sum `nourishment + 25` per Food item, add the same skill bonus (1093-1116), and set thirst quench to remaining divided by twice the weight (1118). Each batch yields `finalAmount * (baseYield + abilityCode)` copies (`Cooking.java:399`). Every finished dish is set to level 1, which the code labels "the newbie exception" (396-397).

**Data.** `recipes.txt` has 56 rows: 52 FOOD, 3 DRINK and 1 SOAP, with levels from 1 to 91. Dishes include "% stew" (levels 22-25, the `%` becoming the meat's name) and "tomato soup" (level 13, with five optional vegetables marked with a negative amount).

**Latent non-termination.** The `while` loop in `countIngredients()` (550-583) ends only when some pot entry reaches zero or goes negative. If a recipe has only optional ingredients and none of them is in the pot, nothing changes and the loop never ends. A script check of the 56 shipped rows found that every row has at least one required ingredient, so the shipped data cannot reach this case.

### 5. Carpentry

- **Materials.** Wood only (`supportedResourceString` is WOODEN, `Carpentry.java:73-76`; `checkMaterialFrom` at 453). `mayICraft()` (197-256) decides what can be carved or mended. Weapons must be blunt or staff type. Rideables must be ladder, bed, chair, table or hook type. Drinks are allowed except potions. Containers are allowed except cage types. Armor is allowed only if its name is a native item.
- **Derived stats** come from the material's hardness, `RawMaterial.CODES.HARDNESS(material) - 3` (575):
  - level = recipe level + hardness, with a minimum of 1 (576-578)
  - weapon attack adjustment = yield + abilityCode + hardness * 5 - 1, and damage = the armor-or-damage value + hardness (683-684)
  - armor = armor value + yield + abilityCode - 1 (693)
  - weight = `getStandardWeight(wood + compData[CF_AMOUNT])` (573)
- **Containers.** LID sets a door with no lock. LOCK sets a locked door and creates a `GenKey` whose key name is `Double.toString(Math.random())` (620-630).
- **Drinks.** Liquid held is `capacity * 50`, and thirst quench is capped at 250 (644-647).
- **Statues.** A "What is this of?" prompt runs through `session.prompt`, and the command is then re-run with `STATUE=` appended (505-539). The `%` in the name is replaced by the subject (595-599).
- **Sub-activities.** Mending sets 100 uses on success (158-162). Refit resets height to 0 (172-174). Learn deconstructs the item into a recipe (166). Bundling forces success and a one-tick duration (717-724).

### 6. BuildingSkill: data-driven world editing

- **Effects.** A recipe row names its effect through the `Building` enum: WALL, DOOR, ROOM, ITEM, DEMOLISH, TITLE, DESC, STAIRS, EXCAVATE, ROOMEFFECT, EXITEFFECT and DELEFFECT (`BuildingSkill.java:106-120`).
- **Placement flags.** The `Flag` enum (122-141) includes DIR, NOWALL, INDOOR, OUTDOOR, CAVEONLY, WATERONLY, SALTWATER, FRESHWATER, UPONLY, DOWNONLY and FIRSTFLOORONLY. All are checked in `invoke()` (1605-1860).
- **Cost.** Wood or rock comes from nearby resources through `getBasicMaterials` (233-243, 1970). "MONEY" and "VALUE" recipes are paid in currency, and VALUE is scaled by the land price (1296, 1941-1975).
- **Permission.** The mob must own the land or have CMDROOMS security (1436-1437, 1977-1991). Demolishing a room is refused if more than one neighbour is titled (1573-1586).
- **Apply.** The change happens at `unInvoke()` through `buildComplete()` (218, 1030-1103):
  - **Doors** (`buildDoor`, 685-745) read a pipe-separated name field of the form `door|open|close|closed|display|desc` (698-713). They also set the open delay to 9999 ticks (725). The meaning of that delay was not checked.
  - **Stairs** (`buildStairs`, 782-911) create a new room and join the two with passage exits. A floor number is shown only when it is above zero (839-869). The floor comes from `findFloorNumber` (747-780), which walks UP and DOWN through the land-title network. It is a heuristic, because a neighbour without a title returns the value it was given.
  - **Rooms** (`buildNewRoomType`, 474-683) swap the room for one of another locale class. Effects, items, inhabitants and exits move across, and the old room is destroyed. An indoor water surface becomes an outdoor one when needed (482-484). Digging below water creates an `UnderWater` room (629-661).
  - **Effects** (`addEffects`, 371-422) parse lists such as `Spell(parms)` and attach abilities or behaviours to rooms, exits or items. `(@remove)` removes them (386-401). `Prop_RoomView` receives the room id (409-410).
- **Demolition** (278-342 and 999-1028) returns occupants to a neighbouring owned room, or to a start room, and then clears the land title.
- **Limits.** Title uniqueness is checked among rooms within 20 steps (1881-1894). A `Prop_ReqCapacity` effect with `rooms=N` caps the number of rooms (1704-1714). Stairs up need a roof first (1683-1687). You cannot build projects at a boardable location such as a ship (1489-1494).
- **Construction** is only a data binding. `construction.txt` has 69 rows: 41 DOOR, 14 ROOM, 5 ROOMEFFECT, 2 WALL, 2 STAIRS, 2 DELEFFECT, and one each of DEMOLISH, DESC and TITLE. Levels run from 1 to 90 (`Construction.java:70-79`).

### 7. The other skills

- **Composting** (`GatheringSkill`). `isCompostable()` accepts raw food or drink that is vegetation or flesh and lies on the ground (`Composting.java:138-147`). Success yields "a pound of compost" with secret identity "compost" and a `Prop_Rotten` effect (409-416). Failure consumes the material and gives nothing, with no message (406-409). Bundling merges piles into a packaged stack (155-244).
- **Butchering** (`GatheringSkill`). A corpse yields its race's `myResources()` (`Butchering.java:120`). The yield count is `baseYield + abilityCode` (116). Diseases spread by consumption or contact are copied onto food and drink (120-131, 138-141). Duration is weight / (10 + XLEVEL), clamped to 3-40 (84-88).
- **Chopping** (`GatheringSkill`). The tick at 6 looks for a good tree. With none, `lookingForMat` names a direction, as in "You might try north" (104-119). Each fell yields `roll(1,7,3) * (baseYield + abilityCode)` pounds (139). `roll` returns the dice sum plus the modifier (`Dice.java:398-406`), so the base result is 4-10 pounds.
- **Cataloging** (`CommonSkill`). The entry is built from the display text, location, property title id, container contents, description, the examine string, and the output of the optional Thief_Appraise and Thief_Lore skills (`Cataloging.java:102-191`). Statues add race and body-part lines (192-208). The entry is written with MSG_WRITE (214-217). Recipes and unreadable items are refused (227-257).
- **CargoLoading** (`CommonSkill`). Loading needs a gettable, non-boardable item and a Boardable ship with a free room in its area (`CargoLoading.java:239-285`). The room search radius is `5 + XLEVEL` (125, 151). The weight cap is `1000 + 1000 * XLEVEL` (330). Unloading takes the last matching item it finds, because the loop has no `break` (313-321).
- **ClanCrafting** (`CraftingSkill`). Recipes come from `clancraft.txt` and from castle prototypes built at load time (`ClanCrafting.java:152-213`). Each recipe costs clan experience. It is spent at the start (684) and refunded if the item is messed up (229-233). The crafter needs the ENCHANT privilege (328-334). Siege items must stand in an area the clan fully controls, with population left after existing siege items (496-546). Resource amounts become the item's weight (604).
- **ClanShipwrighting, CaravanBuilding, CageBuilding.** ClanShipwrighting needs a clan office with PROPERTY_OWNER authority (`ClanShipwrighting.java:119-137`) and transfers ownership through a shopkeeper message (66-79). CaravanBuilding accepts only land or wagon navigable items (`CaravanBuilding.java:84-99`). `Shipwright.loadRecipes` converts the `.cmare` XML into a temporary text recipe, with ticks = weight / 10 and materials = weight (`Shipwright.java:140-186`). CageBuilding accepts cage containers and native items (`CageBuilding.java:77-96`).
- **Costuming** (`CraftingSkill`, `MendingSkill`). Learning works by deconstruction. `deconstructRecipeInto()` writes a recipe line from any garment the character holds (`Costuming.java:204-274`). Ticks are `5 + level/5` (225). The paper cost is the sum over the body parts covered: 4 for about-body, 3 for torso, 2 for arms, legs or back, and 1 for anything else (231-242). The layer is the clothing layer plus 10 (250). Wear locations are joined with "&&" or "||" (251-262). `mayICraft()` accepts only paper, weight of 50 or less, zero armor, no see-through layers, a clothing layer of at least 10, and a level of 31 or less (291-316, 277-282). The recipe file `costume.txt` is 0 bytes, so recipes exist only in learned books.

## Content worth mining

### Cooking recipes (`resources/skills/recipes.txt`, 56 rows)

| Level | Dish | Inputs (required unless noted) | Note |
|---|---|---|---|
| 1 | mush | hops 2, water 1 | Water counts as ten units |
| 1 | hard-boiled eggs | eggs 1, water 1 | |
| 2 | corn on the cob | corn 1 | |
| 13 | tomato soup | tomatoes 1, water 1; optional greens, carrots, potatoes, corn, tomatoes | Optional additives are negative amounts |
| 22-25 | "% stew" | meat (beef, pork or poultry) 1; optional vegetables | The `%` becomes the meat's name |
| 28 | fettucini | fish 1, pasta 2, cream 1, herbs 1 | |
| 62 | beef and noodles | beef 1, pasta 1, water 1, carrots 1 | |
| 91 | burrito | corn tortilla 1, beans 1, cheese 1 | Highest level in the file |

Drinks include coffee (level 5, with a caffeine spell attached) and hot chocolate (level 18).

### Carpentry (`resources/skills/carpentry.txt`, 202 rows, levels 1-91)

| Output class | Rows | Examples |
|---|---|---|
| GenContainer | 49 | small chest (25, lock), huge crate (80) |
| GenWeapon | 42 | bokken (1), the ultimate quarterstaff (84, 270 ticks, 12 wood, value 8004) |
| GenChair | 19 | |
| GenEmptyDrink | 14 | bowl (1), wine barrel (41) |
| GenDice | 14 | six-sided die (13), hundred-sided die (85) |
| GenRideable | 13 | ladder (7), decorative desk (88) |
| GenBed | 13 | small bed (6), giant-sized bed (86) |
| GenShield | 12 | small shield (4), tower shield (64) |
| Others | 26 | GenItem, GenLimb, GenLightSource, GenShoes, GenPipe, GenWand, GenTub (tub: level 23, 200 wood) |

### Other data files

| File | Rows | Content |
|---|---|---|
| `construction.txt` | 69 | Doors, rooms, walls, stairs and room effects. Examples: archway (level 1, 10 wood), roof (level 1, 110 ticks, 350 wood) |
| `cobbler.txt` | 19 | Footwear, levels 0-18. Examples: Slippers (1), Hose (7, feet and legs), Baby Booties (0) |
| `cagebuilding.txt` | 6 | Cages and cage carts, levels 1-15. The large cage wagon is level 15 and uses 400 wood |
| `clancraft.txt` | 46 | Clan items with an experience cost. Flag (level 1, 2500 XP); Book of the Laws (level 10, 2000 XP); Membership Card (5 XP). Skill-gated tools such as Miners Pick (Mining) and Lumberjacks Saw (Chopping). Alignment items: Executioners Scales (GOOD, 500 XP) and Assassins Eye (EVIL, 500 XP) |
| `clancastles.cmare` | 1 prototype | "Small Castle %", level 1, with two rooms in its area |
| `caravanbuilding.cmare` | 94 | Caravan vehicles, levels 1-91 plus one outlier at 1000 |


### Messages worth reusing

- "You've ruined the @x1!" (`BuildingSkill.java:275`)
- "A pound of compost is ready here." (`Composting.java:127`)
- "Ouch! That's HOT!" (`Cooking.java:265`)
- "You need to demolish the upstairs rooms first." (`BuildingSkill.java:1509`)
- "You might try @x1." for a tree direction (`Chopping.java:119`)

## Notable design ideas

- **Outcome decided at the start, delivered at the end.** The roll is taken when the activity begins, and the result is visible in the product's name ("burnt", "ruined", "spoiled") before the timer runs out (see section 2).
- **An activity as an interruptible effect.** Work is a timed object that aborts on combat, movement, sitting or sleeping, and quitting (`CommonSkill.java:230-290`). It is not a blocking call.
- **Recipes with optional additives.** A negative amount marks an optional item. The "list" and "make" commands then say what is missing or in excess (`Cooking.java:1344-1346, 1447-1512`).
- **Recipes generated from data at load time.** ClanCrafting builds recipes from castle prototypes, with cost derived from the prototype's weight (`ClanCrafting.java:163-189`).
- **Learn by deconstruction.** A garment held in hand becomes a recipe line in a recipe book, and its paper cost follows from the body parts it covers (`Costuming.java:225-245`).
- **Food effects from the ingredient list.** Nourishment and thirst come from the ingredients and the cook's skill, not from a fixed value per dish (`Cooking.java:881-929, 1093-1118`).
- **World edits as data.** A recipe row can attach an effect such as `Prop_RoomView` to a new room, exit or item, and `@remove` undoes it (`BuildingSkill.java:371-422`).
- **Failure hints.** Chopping names a direction when no tree is nearby (`Chopping.java:115-119`).
- **Dish naming by template.** "% stew" takes the meat's name from the ingredient list (`Cooking.java:804-842`).
- **Random lock keys.** Each locked container gets a key with a random name (`Carpentry.java:625`).

## Problems and cruft

1. **Freshwater check is wrong.** `BuildingSkill.java:1852-1859` tests `liquidType()` against `RESOURCE_SALTWATER` on line 1855. A salt-water drink room therefore passes the FRESHWATER check, and a fresh-water drink room whose atmosphere is not fresh fails it.
2. **Catalog type mismatch is not enforced.** In `Cataloging.java:259-266` the mismatch branch prints a message but does not return false, and line 267 returns true. The check in `invoke()` (359-363) is therefore advisory.
3. **Aborted loads still complete.** In `CargoLoading.java:170-183` the loading branch does not test `isaborted`, while the unloading branch does (190). A "stop" during loading still moves the cargo.
4. **Composting's room branch is dead code.** `Composting.java:98-110` tests `affected instanceof Room`. `beneficialAffect(mob, mob, ...)` at line 443 attaches the effect to the mob (`StdAbility.java:698-720`), so the branch never runs. A failed compost also consumes material before the roll (406-407, 409) and gives no message.
5. **Two dead conditions in Composting bundling.** `Composting.java:204` tests `foundOnes.size()<0`, which is never true. Lines 215-216 return success for an amount of 1 without doing anything. The intent was not verified.
6. **Aborted butchering destroys the corpse.** `Butchering.java:156-160` destroys the body in a `finally` block, and lines 232-234 have already removed it from the room. An aborted butchering therefore loses the corpse and gives nothing. Line 146 uses `y=9999` as a loop-exit sentinel.
7. **Shared static stat-code cache.** `CommonSkill.java:999` and 1002-1015 cache stat codes in a static field shared by every subclass. Codes are built from `this` only on the first call.
8. **Static fake-fire item is shared.** `CommonSkill.java:68` and 506-512 hand every crafter the same static item as fire.
9. **Field read where a method is meant.** `CargoLoading.java:165` reads `canBeUninvoked` as a field (`StdAbility.java:56`) rather than calling the method (1542), so any override is bypassed.
10. **Material lists disagree.** Cobbling's `supportedResourceString` is "WOODEN|METAL" (`Cobbling.java:73`), but `checkMaterialFrom` also accepts MITHRIL, CLOTH and LEATHER (385-388). The effect of the mismatch depends on where `supportedResourceString` is used, which was not checked.
11. **Inconsistent comparisons in Carpentry.** Line 700 compares "SMOKE" case-sensitively, while lines 493, 505 and 591 use `equalsIgnoreCase`. Line 595 tests `indexOf('%') > 0`, which misses a `%` in position 0.
12. **Costuming skips hardness.** `Costuming.java:578` computes hardness and then passes 0 to `setWearLocation` (607). Carpentry (694) and Cobbling (492) pass the hardness value.
13. **Comparator contract broken.** `ClanCrafting.java:196-199` returns -1 whenever the first list is short, even when the second is also short.
14. **Drink liquid scales with item count.** `Cooking.java:1036-1037` adds the container's held capacity once per ingredient item, so three items give three times the capacity. This looks unintended. It was not verified in play.
15. **Case mismatch in liquid names.** `Cooking.java:481-482` does not upper-case liquid names, but lines 497-498 do for raw items. Some comparisons use case-sensitive `equals` (623, 628, 645, 650). The impact was not verified.
16. **Empty SURVEY branch.** `BuildingSkill.java:1380-1383` is an empty TODO. The word falls through into the build path.
17. **Unchecked data codes.** `Building.valueOf()` (`BuildingSkill.java:1032, 1222, 1441, 1455, 1470`) throws on an unknown code, and nothing validates the recipe files.
18. **Discarded helper roll.** A helper's `messedUp` roll (`BuildingSkill.java:1402`) is never used, because `unInvoke()` skips the build for helpers (207).
19. **Overstated catalog comment.** The comment at `Cataloging.java:213` says the entry includes value, level, material and weight. Those come from the examine string and the optional Thief skills (120, 178-191).
20. **Multiplayer assumptions.** Helping (`BuildingSkill.java:1400-1433`, `CommonSkill.java:273-277`), clan offices and experience (ClanCrafting, ClanShipwrighting), land titles and room owners (BuildingSkill, `CMLib.law()`), and cargo to other players' ships (CargoLoading) all assume other people.

## Takeaways for a Python SUD

1. **Make the activity an object with a tick budget.** Start with validation, charge costs, roll the outcome, then count ticks. Abort on enemy contact, movement or incapacity. The shape is:

   ```python
   @dataclass
   class Activity:
       actor: "Actor"
       recipe: "Recipe"
       ticks_left: int
       botched: bool            # rolled at start, shown in the product name
       def tick(self, world) -> bool:   # True when done
           if world.interrupted(self.actor):
               raise Aborted(self)
           self.ticks_left -= 1
           return self.ticks_left <= 0
   ```

   Keeping the activity as one mutable object avoids the clone pitfall CoffeeMud works around (section 1).
2. **Recipes as data, with explicit optional inputs.** Use a `name` template (`"{main} stew"`), `level`, `ticks`, `inputs` with an explicit `optional: true` flag rather than negative numbers, and an `output`. Match ingredients by name, then category, then material, in that order.
3. **Simplify the timing curve.** Keep the form `ticks * (1 - bonus)`, with bonus driven by the gap between character and recipe level and by skill, plus a floor (CommonSkill.java:346-362). A small clamp is enough.
4. **Quality in the name.** Carry the botched flag into the product's name and description ("burnt", "ruined", "spoiled"). This is cheap to port and easy to read in a text game.
5. **Keep the list-and-hint interface.** Telling the player what to add or remove (`Cooking.java:1447-1512`) is worth copying directly.
6. **Learning by deconstruction** (Costuming) suits single-player exploration: examine an item to write its recipe.
7. **Drop the multiplayer parts.** Clan experience, siege population, land titles, helping and cargo to other players' ships are not needed. Keep the optional additives, the batch count and the room-edit-as-data idea (`BuildingSkill.java:371-422`) if the SUD needs a world builder.
8. **Decide persistence on purpose.** CoffeeMud marks activities non-savable (`StdAbility.java:703`). An SUD should either save an activity in progress or cancel it on save, and say which.
9. **Avoid the reviewed defects.** Return from validation helpers on every branch, never let an aborted action complete or destroy its inputs silently, and do not cache per-class data in a static field.

## Coverage

- **Read in full (16 of 16):** BuildingSkill.java (2035 lines), Cooking.java (1573), CommonSkill.java (1016), Carpentry.java (749), ClanCrafting.java (693), Costuming.java (651), Cobbling.java (541), Composting.java (447), Cataloging.java (409), CargoLoading.java (364), Butchering.java (238), Chopping.java (222), ClanShipwrighting.java (138), CageBuilding.java (110), CaravanBuilding.java (106) and Construction.java (105).
- **Spot-checked outside the bucket:** `CraftingSkill.java` (addRecipes 272-277, FOUND_AMT and CF_TOTAL constants), `StdAbility.java` (canBeUninvoked 56 and 1542; copyOf and cloneFix 1419-1431; beneficialAffect 2341-2349; startTickDown 698-720), `Dice.java` (roll 398-406), `CoffeeUtensils.java` (addExtRecipes 202-215), `Shipwright.java` (recipe conversion 140-186).
- **Not read:** GatheringSkill, EnhancedCraftingSkill, CraftingSkill and Wainwrighting in full; Shipwright in full; MasterCostuming (other bucket; its data file `mastercostume.txt` is also 0 bytes); Titling; Thief_Appraise and Thief_Lore; `buildCostArray`; `dropALoser`.
- **Data:** `recipes.txt` (full), `carpentry.txt` (full), `cobbler.txt` (full), `clancraft.txt` (full), `cagebuilding.txt` (full), `construction.txt` (summarized by script), `caravanbuilding.cmare` and `clancastles.cmare` (summarized by grep), `costume.txt` (0 bytes).
