# Common skills, part 3: the crafting engine, gathering and lore skills (CraftingSkill to Floristry)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b044` (Abilities/Common [part 3/7: CraftingSkill .. Floristry]) · 22 files · 8971 lines of Java.

Citation base: Java paths are relative to `com/planet_ink/coffee_mud/` (for example `Abilities/Common/CraftingSkill.java:379`). `resources/` and `coffeemud.ini` are relative to the CoffeeMUD folder.

## Summary

`CraftingSkill` and `EnhancedCraftingSkill` form the engine behind most of CoffeeMud's trade skills. They load tab-separated recipe rows, take physical materials from the room floor, run a timed action, and produce an item (a ruined one on failure) with an XP award scaled by level difference. The other classes are concrete skills built on that engine (weapons and ammunition, clothing text, liquors, scrolls), on the gathering base (farming, fishing, digging, drilling), or standing alone (Enrolling as a paid class-change service, Domesticating, FishLore, the racial DiligentStudying). Expertises add named stage bonuses that change material cost, value, time, level and stats through one switch table. Gathering draws from per-room resources and applies a per-room yield decline that clears after 30 minutes of real time. For a single-player Python game the reusable parts are the recipe-as-data design, the enhancement table, room depletion (on game time), mending as a 0 to 100 condition, and learning recipes by taking finished items apart. Land titles, group checks and shared room depletion are multiplayer assumptions to drop or rethink.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Common/CraftingSkill.java` | Base crafting engine: recipe loading and matching, floor-material lookup, XP, success and ruined-item outputs, mending, deconstruction, the `info` preview | 2459 |
| `Abilities/Common/EnhancedCraftingSkill.java` | Expertise enhancements: enhancement words, stage effects on cost, value, time, level and stats | 913 |
| `Abilities/Common/Fletching.java` | Wooden bows, slings, crossbows and ammunition; the template subclass for the crafting lifecycle | 578 |
| `Abilities/Common/Farming.java` | Planting and harvesting crops in a room: growth ticks, sunlight, weather, compost | 558 |
| `Abilities/Common/Dissertating.java` | Writing spell scrolls ("dissertations"), copying a spell from another scroll, mana and XP costs | 521 |
| `Abilities/Common/Decorating.java` | Mounting decorations on room walls as persistent fixtures | 445 |
| `Abilities/Common/Embellishing.java` | Adding named embellishments (collars, sleeves, hoods, skirts) and spells to clothing | 368 |
| `Abilities/Common/FireBuilding.java` | Building a campfire from deadwood, or lighting an item; burn timers | 351 |
| `Abilities/Common/Enrolling.java` | Paid, timed service that moves a character from the default class into a career | 298 |
| `Abilities/Common/Fishing.java` | Weighted catch table keyed to the room's fish resource | 275 |
| `Abilities/Common/Drilling.java` | Filling an empty container with a liquid resource | 273 |
| `Abilities/Common/Floristry.java` | Identifying unknown flowers and giving them their real names | 232 |
| `Abilities/Common/Digging.java` | Gem, sand and stone digging from room resources | 229 |
| `Abilities/Common/Domesticating.java` | Taming animals into followers, and naming them | 227 |
| `Abilities/Common/Dyeing.java` | Dyeing cloth, paper, leather, vegetation and liquids (extends `PaintingSkill`) | 222 |
| `Abilities/Common/FishLore.java` | Finding fishing spots on water or from a boat; scouting adjacent rooms | 185 |
| `Abilities/Common/Engraving.java` | Engraving words onto a crafted item's description | 179 |
| `Abilities/Common/Embroidering.java` | Embroidering words onto cloth or leather | 152 |
| `Abilities/Common/Distilling.java` | Turning liquor recipes into intoxicating drinks (extends `Cooking`) | 151 |
| `Abilities/Common/DiligentStudying.java` | Automatic racial ability: bonus practice points on level gain | 125 |
| `Abilities/Common/DrugCutting.java` | Cooking-process variant for drug recipes (extends `Cooking`) | 116 |
| `Abilities/Common/Excavation.java` | Cave construction recipes (extends `BuildingSkill`) | 114 |

Shared constants come from `core/interfaces/RecipeDriven.java:35,40` (`RCP_FINALNAME=0`, `RCP_LEVEL=1`). Base classes outside this bucket are consulted only at the points named below.

## How it works

### 1. Class structure and the timed-action pattern

Inheritance: `CraftingSkill` extends `GatheringSkill` and implements `RecipeDriven` (`Abilities/Common/CraftingSkill.java:44`). `EnhancedCraftingSkill` extends it and implements `ItemCraftor` (`EnhancedCraftingSkill.java:39`). `Fletching` extends `EnhancedCraftingSkill` (`Fletching.java:40`). `Dissertating` extends `CraftingSkill` (`Dissertating.java:38`). Decorating, Embellishing, Engraving, Embroidering, FireBuilding, Enrolling, Floristry, Domesticating and FishLore extend `CommonSkill`. Dyeing extends `PaintingSkill` (`Dyeing.java:36`). Excavation extends `BuildingSkill` (`Excavation.java:38`). Distilling and DrugCutting extend `Cooking`. Farming, Fishing, Drilling and Digging extend `GatheringSkill`. DiligentStudying extends `StdAbility` (`DiligentStudying.java:36`).

Every skill is an ability with a classification code: a common-skill flag plus a domain. The domains used here are `DOMAIN_CRAFTINGSKILL` (`CraftingSkill.java:61-64`), `DOMAIN_GATHERINGSKILL` (Farming, Fishing, Drilling, Digging), `DOMAIN_ARTISTIC` (Decorating, Embellishing, Dyeing, Distilling), `DOMAIN_CALLIGRAPHY` (Engraving, Embroidering), `DOMAIN_NATURELORE` (FireBuilding, FishLore, Floristry), `DOMAIN_EDUCATIONLORE` (Enrolling) and `DOMAIN_ANIMALAFFINITY` (Domesticating).

The lifecycle is the same across the bucket:

1. `invoke()` validates the command and the inputs, sets `verb` and `displayText`, and calls `super.invoke()`, which runs the base checks.
2. The outcome roll is `messedUp = !proficiencyCheck(mob, adjustment, auto)`, as in `Fletching.java:544`. `proficiencyCheck` is defined in `StdAbility.java:1441`; its body was not read, so the success curve is unverified.
3. A start message goes through `CMClass.getMsg` and `okMessage`/`send`, then `beneficialAffect(mob, mob, asLevel, duration)` creates the timed effect (`Fletching.java:561-568`).
4. When the timer ends, `unInvoke()` runs and branches on `messedUp`, `aborted` and `activity`. Fletching is the template (`Fletching.java:111-163`): on a failed mend it calls `messedUpCrafting`; on a successful mend it sets `usesRemaining` to 100; on learning it calls `deconstructRecipeInto`; otherwise it calls `dropAWinner` (success) or `dropALoser` (failure).
5. `tick()` guards against interruption. Fletching unInvokes when its building item is gone (`Fletching.java:165-174`). Decorating and Embellishing set `messedUp` and unInvoke when the item leaves the mob or the room (`Decorating.java:134-154`, `Embellishing.java:132-158`).

Duration comes from `CommonSkill.getDuration(baseTicks, mob, itemLevel, minDuration)` (`Abilities/Common/CommonSkill.java:346-360`):

- `level = mob level - item level`; `pct = (level / LASTPLAYERLEVEL) * 0.5`, where `LASTPLAYERLEVEL = 91` (`coffeemud.ini:515`). Then `ticks -= round(ticks * pct)`. A higher-level crafter is faster. An item above the crafter's level makes `pct` negative and the job longer.
- `lastBaseDuration` is set to the result (floored at `minDuration`). The XP code reads it later.
- `quickPct = TIME-expertise level * 0.05`; `ticks -= round(ticks * quickPct)`; floored at `minDuration`.
- Worked from the formula only (not executed): base 100, crafter level 50, item level 40 gives `pct` of about 0.055 and about 95 ticks before expertise. One tick is `TICKTIME = 4000` ms (`coffeemud.ini:2151`).

Adjustments to the proficiency roll are used for situational modifiers. FireBuilding uses weather (section 8). Domesticating uses `-(levelDiff * 5)`, so a harder animal lowers the chance (`Domesticating.java:200-203`).

### 2. Recipe data and matching

`loadRecipes(filename)` (`CraftingSkill.java:204-243`) reads each extended file under `skills/` (`CMFile.getExistingExtendedFiles`, so extension files can add rows), parses each one, sorts by `RCP_LEVEL` (`CraftingSkill.java:220-233`), logs "Recipes not found!" when the result is empty (`234-238`), and caches the parsed list in `Resources` under `PARSED_RECIPE: <file>` (`240`). `addRecipes(mob, recipes)` merges per-mob extra recipes through `CMLib.utensils().addExtRecipes` (`CraftingSkill.java:272-277`).

Each subclass declares its row layout in `getRecipeFormat()` as tab-separated column names, and the positions are fixed constants. Fletching's 13 columns are `ITEM_NAME`, `ITEM_LEVEL`, `BUILD_TIME_TICKS`, `MATERIALS_REQUIRED`, `ITEM_BASE_VALUE`, `ITEM_CLASS_ID`, `AMMO_TYPE`, `AMMO_CAPACITY`, `BASE_DAMAGE`, `MIN_MAX_RANGE`, `OPTIONAL_RESOURCE_OR_MATERIAL`, `CODED_SPELL_LIST`, `WEAPON_HANDS_REQUIRED` (`Fletching.java:77-97`). `EnhancedCraftingSkill.loadList` re-sorts rows by whichever column ends in `_LEVEL` (`EnhancedCraftingSkill.java:346-381`).

A `%` in a recipe name is replaced with the material name (`replacePercent`, `CraftingSkill.java:279-300`). This lets one row such as `crude % atlatl` cover every wood type.

Player input is resolved by `matchingRecipes` (`CraftingSkill.java:1409-1756`), in this order:

- `N.name` or `name.N` picks the Nth match (`1414-1430`; `selectMatches` at `1398-1406`).
- A bare integer means exact level N (`1433-1447`). A negative integer means levels up to |N| (`1434-1449`).
- `N+` and `N-` both mean level N or higher (`1465-1480`). The minus suffix does not mean "at most".
- `A-B` means a level range (`1482-1500`).
- An exact name match (`1502-1516`), then loose matches: word prefix, substring and last-word fallback (`1613-1703`).
- For weapon and armour skills, matches by weapon class or type words (`1517-1553`, `1705-1732`) and by wear-location words (`1554-1611`, `1734-1754`).

Level gating uses `xlevel(mob) = mob level + 2 * LEVEL-expertise count` (`CommonSkill.java:645-648`). Fletching hides recipes above it in `list` (`Fletching.java:317`) and refuses them when crafting (`392`).

### 3. Inputs: floor materials, components and wood cost

Materials are found on the floor, not in the inventory. `fetchFoundResourceData` (`CraftingSkill.java:709-910`) searches `mob.location()` for raw resources by material code, subtype, or the `(subtype)` syntax (`751-767`). Seeds are skipped as building material (`758-762`, `797-799`). Shortfalls are reported in messages that tell the player to put the material down first:

- "There is no @x1 here to make anything from! It might need to be put down first." (`828`, `841`).
- "You need a @x1 pound bundle of @x2 to make that. There is not enough here. Are you sure you set it all on the ground first?" (`891`).

Consumed resources are destroyed by `CMLib.materials().destroyResources` (`Fletching.java:460-461`), which returns a `DeadResourceRecord` describing what was lost. That record names the product (`determineFinalName` and `determineDescription`, `CraftingSkill.java:245-270`). Weight comes from `getStandardWeight` (`192-200`) and the output material from `getBuildingMaterial` (`347-359`).

Wood cost falls with the XP-cost expertise. `adjustWoodRequired` (`CraftingSkill.java:361-372`) gives `wood - round(0.05 * wood * XPCOST-level)`, with a minimum of 1 when wood is above zero.

Recipes can also name ability components. A `(...)` string builds one inline (`CraftingSkill.java:2230-2258`). A missing component prints the requirement list, "You lack the necessary materials to @x1, the requirements are: @x2." (`2298`).

Metal outputs need a fire in the room (`Fletching.java:429-450`, "You'll need to build a fire first.", `446`).

System and NPC crafting bypass the player's input. `randomRecipeFix` (`CraftingSkill.java:912-947`) picks a random row for non-animal monsters or auto-generation. It rejects rows above the crafter's `xlevel` and retries up to 100 times. `fetchFoundResourceData` fakes materials when `autoGeneration > 0` (`720-729`). System-side crafting uses a shared invisible worker MOB at level `Integer.MAX_VALUE/2` (`978-993`).

### 4. Outcomes: success, failure, lottery and XP

- Failure (`messedUp`): `dropALoser` (`CraftingSkill.java:471-504`) passes the output through `CMLib.utensils().ruinItem` and drops it in the room. The crafter still gets an item. The message is "<S-NAME> mess(es) up making @x1." (`Fletching.java:134`).
- Success: `dropAWinner` (`CraftingSkill.java:425-469`) sends a `TYP_ITEMGENERATED` message, checks `okMessage`, adds the item to the room with `Expire.Player_Drop`, then checks whether the item is still in the room. If it is gone, the crafter hears "You have won the common-skill-failure LOTTERY! Congratulations!" and gets 50 XP (`448-449`, same pattern at `496-497`). If the item is not visible to the crafter it "fades from view" or "visually blends into the background" (`457-459`).
- Crafted items with wear start at `usesRemaining = 100` (`Fletching.java:535-536`).

XP is attached as the value of the generated-item message (`setMsgXPValue`, `CraftingSkill.java:379-423`). The rules are:

- `diff = output level - crafter level`, and `limit = EXPRATE`, which `coffeemud.ini:406` sets to 6.
- Factor is 0 when `diff < -limit`. Otherwise it is `100 * (1 + min(diff / limit, limit))`. That gives 0 at -6, 100 at equal level, 200 at +6, and 700 from +36 upward.
- Base XP is `min(lastBaseDuration, 75) * factor / 25`.
- For each of the last five crafted names kept on the crafter's skill instance (`last25items`, `CraftingSkill.java:404-418`), XP drops by 25 percent of base.
- DoorKey outputs give 0 XP unless the skill is LockSmith (`394-395`). A crafter without the skill gets 0 (`399-401`).
- Arithmetic example from the formula (not executed): a 40-tick job at equal level gives 160 XP before the repeat penalty.

### 5. Expertise enhancements (EnhancedCraftingSkill)

The enhancement families are the enum `EnhancedExpertise` (`CraftingSkill.java:121-133`). Each one maps to an `ExpertiseLibrary.XType`: `ADVN` to LEVEL, `LITE` to X1, `DURA` to X2, `QUAL` to X3, `LTHL`, `FORT`, `IMBU` and `VIGO` to X4, `CNTR` to X5 and `RUSH` to X6. The stage names come from `resources/skills/expertises.txt`. Rows for the crafting families include `ADVNCRAFT` (line 445), `RUSHCRAFT` (459), `LITECRAFT` (466), `DURACRAFT` (501), `QUALCRAFT` (529), `LTHLCRAFT` (579) and `CNTRCRAFT` (586). No `FORTCRAFT`, `VIGOCRAFT` or `IMBUCRAFT` rows exist in shipped resources (grep of `resources/`).

Input. The player puts an enhancement word, one of the stage names of a family they hold, before the item name, optionally preceded by `hide`. The parser is `enhancedTypes` (`EnhancedCraftingSkill.java:465-563`). Each family can be used once, and only at a stage the character has reached (`511`). The words are removed from the command before recipe matching (`523`). The `list` and craft hints tell the player which words are available (`enhanceList`, `383-426`):

- "You can use your expertises to enhance this skill by prepending one or more of the following words to the name of the item you wish to craft: ..."
- "Put the word 'hide' before any enhancement you want hidden from the name."

Naming. `applyName` (`296-312`) inserts the word after any article and re-articles the result. A hidden enhancement keeps the item's name but still changes its description (`314-326`).

Effects. These are the stage effects in `enhanceItem` (`685-912`), applied only when the crafter's activity is CRAFTING (`692`). Percentages apply to the base value unless stated. Material changes come from `fetchFoundResourceData` (`111-232`). Time changes call `bumpTickDown` on the crafter's enhancement effect (`CommonSkill.java:814-820`).

| Family (key) | Stage 0 | Stage 1 | Stage 2 | Lines |
|---|---|---|---|---|
| LITECRAFT (LITE) | materials -10%; value +10% | materials -25%; value +20%; time +25% | materials -50%; value +30%; time +50% | `119-141`, `713-734` |
| DURACRAFT (DURA), protective armour only | materials +10%; armour +1; value +10% | materials +20%; armour x1.1 +1; value +20%; time +25% | materials +25%; armour x1.25 +1; value +30%; time +50% | `142-164`, `769-796` |
| QUALCRAFT (QUAL) | value +50%; time +25% | value +150%; time +50% | value +250%; time +75%; adds Spell_WellDressed to non-multiwear armour | `797-821` |
| LTHLCRAFT (LTHL), weapons only | damage +5%; value +10%; time +25% | damage +10%; value +20%; weight +10%; time +50% | damage +15% +1; value +30%; weight +10%; time +75% | `822-851` |
| CNTRCRAFT (CNTR), weapons only | attack +3; value +10%; time +25% | attack +6; value +20%; weight +5%; materials +5%; time +50% | attack +9; value +30%; weight +10%; materials +10%; time +125% | `198-216`, `852-881` |
| RUSHCRAFT (RUSH) | value -10%; time -10%; item level -2 | value -20%; time -20%; item level -3 | value -30%; time -30%; item level -4 | `735-759` |
| ADVNCRAFT (ADVN), level family | item level +1 | item level +2 | item level +3 | `760-768`, `affectLevelBy` at `627-665` |

Two details beyond the table. `affectLevelBy` rebuilds weapons and armour at the new level with `CMLib.itemBuilder().timsLevelCalculator` and `balanceItemByLevel`, and applies the damage or armour difference to the real item (`631-665`). Container capacity scales by `1 + 0.1 * (stage+1)`, and food and drink by `1 + 0.5 * (stage+1)` (`667-682`). Material changes use `multiplyMinResult1` (`265-281`), which rounds and forces at least a change of 1.

The FORT, VIGO and IMBU branch (`882-905`) gives a `Prop_UseAdjuster` of `(stage+1) * recipeLevel`, a time factor of `1.1 + 0.1 * stage`, and a value increase of 25 percent, once per item. Its stat defaults to `STAT_MOVE` (`710`), so FORT and VIGO both add movement; only IMBU switches to `STAT_MANA` and sets the `IS_BONUS` disposition (`887-891`). No shipped expertise rows exist for these three, so the branch is dormant with the shipped data.

Encoding. A stage is stored as a bit-packed integer with `HIDE_MASK = 1<<30` (`EnhancedCraftingSkill.java:66-67`, `530`), in a `PairVector<EnhancedExpertise,Integer>`.

XP. `cleanBuildingNameForXP` (`602-625`) strips the enhancement words before the repeat-craft check, so an enhanced sword still counts as the same sword.

### 6. Mending and learning recipes by deconstruction

Mending is a crafting activity. `canMend` (`CraftingSkill.java:2204-2228`) requires the item to be subject to wear and tear and to have `usesRemaining < 100`; otherwise it says "@x1 is in good condition already." A successful mend sets `usesRemaining` to 100 and bumps the MENDER achievement (`Fletching.java:140-143`).

A botched mend still helps, but less as the item gets better (`messedUpCrafting`, `CraftingSkill.java:302-325`):

| Condition before | Added |
|---|---|
| above 90 | +1 |
| 81 to 90 | +3 |
| 71 to 80 | +5 |
| 61 to 70 | +7 |
| 60 or below | +10 |

Nothing is added once the item is at 100. `publicScan` (`1871-1914`) lists items that need mending, with their percentage.

Deconstruction (`doLearnRecipe`, `CraftingSkill.java:2356-2458`; `deconstructRecipeInto`, `1928-1963`) learns a recipe from a finished item:

- The item must be in the character's inventory, not worn, with its container empty (`2395-2404`). It must be learnable (`2379-2389`), must not carry a crafting brand (`2380`, so player-made items are excluded), and must not be above the character's level (`2390-2394`).
- A blank page or a recipe book with spare pages is needed (`2405-2425`). A duplicate name is refused (`2426-2439`).
- Duration is `getDuration(10 + level, mob, level, 10)` (`2454`).
- On success the recipe line is added to the book with `CMLib.ableParms().makeRecipeFromItem` and the item is destroyed (`1952-1954`, `Fletching.java:148-149`).

`getPercentChanceToDeconstruct` (`CraftingSkill.java:1916-1921`) is defined but has no caller in the code searched.

### 7. Room yield and depletion (shared by gathering skills)

`GatheringSkill` holds the depletion model (`Abilities/Common/GatheringSkill.java:69`, `77-80`, `82-109`, `111-150`):

- `roomSpamCounter` maps each room to its resource code, a harvest count and an expiry. `EXPIRE_DURATION` is 30 minutes of real time (`69`).
- The drop rate is 0.20 per prior harvest (`getRoomSpamDropRate`, `77-80`).
- The first harvest in a room gets full yield and creates the record with count 1 (`111-150`).
- Each later harvest yields `round(initial * (1 - 0.2 * count))`. In order that is 100, 80, 60, 40 and 20 percent.
- `checkIfAnyYield` (`82-109`) refuses before any yield once `round(10 - 10 * 0.2 * count)` falls below 1. That is after five harvests in one room.
- The record is cleared when its room's resource changes, or when 30 minutes have passed.

Callers check `checkIfAnyYield` after their proficiency roll (for example `Fishing.java:206-208`, `Digging.java:203-205`).

### 8. Skill-by-skill notes

#### Fletching (`Fletching.java`)

- Recipes come from `skills/fletching.txt` (`100-103`). Only wooden ammunition and ranged or thrown weapons qualify (`mayICraft`, `176-193`).
- Duration: `getDuration(RCP_TICKS, mob, level, 4)` (`472`). Hardness is `HARDNESS(material) - 3` (`474`). The output level is recipe level plus hardness (`487`). Attack adjustment is `baseYield + abilityCode - 1 + 5 * hardness` (`520`). Damage is the recipe's damage value plus hardness (`521`).
- Bundles: `fletch bundle ...` goes through `GatheringSkill.bundle` (`272-280`, `546-553`).
- `list` shows item, level and material columns, filtered to levels at or below `xlevel` unless `all` is given (`286-338`).
- Messages: "Make what? Enter ... \"@x1 list\" ... or \"@x1 stop\" to cancel." (`268-269`).

#### Farming (`Farming.java`)

- Planting runs first as an effect on the planter (`beneficialAffect(mob, mob, ...)`, `Farming.java:554`). When that effect ends, `unInvoke` creates the crop as a copy on the room with a 50-tick timer (`263-275`). The copy is made only when the ended effect was on a MOB (`aff instanceof MOB`, `267`). The crop's own tick and unInvoke run on the room (`135-167`).
- Growing needs clear sun, an outdoor domain (hills, plains, woods, jungle, swamp) or dirt, and no drought (`canGrowHere`, `321-352`). "You need clear sunlight to do your farming." and "The current drought conditions make planting useless." are the refusals.
- `goodticks` counts sunny ticks (`142-143`). A crop with no product (`found` is null, typically after a failed planting roll) fails at tick 6, "Your @x1 crop has failed." (`145-151`). Later, once `tickUp > 10`, it fails when `goodticks < tickUp / 2`: "Your @x1 crop has failed due to lack of sunlight." (`156-161`).
- Growth messages come from the remaining fraction `tickDown / (tickDown + tickUp)` (`executeMsg`, `105-132`, at `116`): under 0.25 is "Mature", under 0.5 is "growing", under 0.75 is "Young", and above that the seeds are shown.
- Harvest (`unInvoke`, `195-276`): `amount = d7 * (baseYield + abilityCode)` (`206`).
- Compost: items whose secret identity is `compost` (`171`) are consumed by weight. Each unit adds `origAmount` to the yield (`208-245`). A budget of `amount * 10` caps the compost counted (`208`).
- No automatic regrowth. The room crop's own `unInvoke` harvests when `affected == room` (`202-259`) and does not re-arm itself, because the re-arm needs a MOB (`267`). Each crop has to be planted again.
- Planting inputs: seeds, edible plants, or vegetation, cotton, hemp and wood materials (`plantableResource`, `294-303`). Mushrooms and fungus are excluded (`296-298`). The room must list the resource among its choices, or be dirt (`isPotentialCrop`, `278-292`).

#### Fishing and FishLore (`Fishing.java`, `FishLore.java`)

- Fishing needs a fish resource in the room's resource or resource-choice list (`Fishing.java:183-195`). Boardable areas, such as boats, use the boat's room (`173-178`).
- The catch comes from `skills/fishing.txt` (58 rows) by weighted draw: `winner = roll(1, total, -1)` and a cumulative pick (`229-248`). The product is named "a pound of @x1" and takes the row's value (`252-257`).
- Yield per unit is `roll(1,3,0) * (baseYield + abilityCode)` with room depletion (`132`). Catches go into the inventory, or into the container the character is riding (`143-148`).
- Tick 6: "You got a tug on the line!" when something is on the hook, or "Nothing is biting around here." (`104-112`).
- FishLore needs water, a water-based mount or a boat, and no indoor room (`151-170`). Its duration is `getDuration(45, mob, 1, 10)` (`176`). On completion it scans its own room and each adjacent room through an open exit (`116-131`), in reverse direction order, and reports "You think this spot would be good for @x1." or "There looks like @x1 @x2." (`113`, `127`). With no hits it says "You don't find any good fishing spots around here." (`134`).

#### Digging and Drilling (`Digging.java`, `Drilling.java`)

- Digging needs one of precious, glass, sand or stone at the location (`Digging.java:192-199`). Its tick-3 hint searches in that order (`116-119`).
- Yield: precious gives 1, with a 10 percent chance of 2. Other material gives `roll(1,15,0)` (`142-146`), multiplied by `baseYield + abilityCode` (`147`).
- Precious finds add `bumpTickDown(tickDown * 2)` (`112`). That adds twice the remaining ticks, so the remainder triples. The intent is unclear and the effect is probably unintended.
- Drilling needs an empty container with capacity above its own weight, which is a Drink and not made of liquid (`206-217`). Any liquid already inside must match the room's resource and be fully used up (`219-237`, "needs to have the @x2 removed first", `228`).
- Yield: `roll(1,3,0)` (cloth resources roll `1d10`), multiplied by `baseYield + abilityCode`, clamped to free capacity and to the container's capacity (`139-145`). Each unit goes into the container (`166-177`).

#### Dyeing, Engraving and Embroidering (`Dyeing.java`, `Engraving.java`, `Embroidering.java`)

- Dyeing (`PaintingSkill` base): colour choices come from `skills/dyeing.txt`, filtered by level and by the LEVEL-expertise count (`Dyeing.java:129-143`, `168-185`). Dyeable materials are cloth, paper, liquid, vegetation and leather, and only generic items qualify (`158-167`). Duration is the recipe's ticks (default 30), doubled for leather, then `getDuration(..., 1, 6)` (`208-213`).
- A failed roll sets `writing` to an empty string (`206-207`). `unInvoke` then says "<S-NAME> mess(es) up the dyeing." (`103`). Success calls `removePaintJob` and `addPaintJob` (`106-108`; `PaintingSkill` body not read).
- Dyeing's list fails when no colour is available (see Problems).
- Engraving: the target must be a crafted item, meaning it has a craft brand (`Engraving.java:130-134`). The character needs the `Skill_Write` ability (`143-148`). Materials are glass, metal, rock, precious, synthetic, wood and mithril (`150-161`). Both the engraving roll and the writing roll must pass (`168`). Success sets the description to "... Engraved on it are the words `...`", replacing any earlier inscription (`94-98`). Duration is `getDuration(30 - LEVEL-expertise / 2, mob, 1, 3)` (`170`).
- Embroidering is the same engine on cloth or leather (`128-134`). It has no craft-brand check, which Engraving does have (compare `130`). The text says "Embroidered on it are the words `...`" (`92-96`).

#### Embellishing and Decorating (`Embellishing.java`, `Decorating.java`)

- Embellishing (`skills/embellishing.txt`, 84 rows): the target must match the recipe's zapper mask (`326-331`). The list command can filter by an item the character holds (`249-261`). An already-embellished item is refused (`332-336`). Components are consumed (`323`, `349`).
- On completion (`unInvoke`, `161-207`), the item's name becomes the recipe's name template, with `@x1` the old name and `@x2` the material (`355`). The old name and display text are saved in an `ExtraData` effect (`184-193`). The item gets `SENSE_ITEMUNLEARNABLE` (`196`), and the recipe's spell list is applied through a temporary `CraftingSkill` (`197-198`). The saved names exist but no reverting code was found in this bucket.
- Decorating (`skills/decorations.txt`, 14 rows, each a verb such as HANG, MOUNT or PLACE): requires a cave, metal, stone or wood indoor room and building privileges (`420-430`, `414`). Flags come from the recipe's seventh column (`407-412`). On every fourth tick (`tickUp % 4 == 2`) it may print one of eight flavour lines, such as "Hmmm, no, you think it might look better over there." (`156-184`).
- On completion the decoration effect is copied onto the item with its flags and old display text as misc text (`220-226`). It is set non-uninvokable (`227`), moved to the room with `Expire.Never` (`231`), and its display changes to the mounted phrase (`229-230`).
- Mounted items keep their senses flags: "blended" adds `SENSE_ALWAYSCOMPRESSED` and "hips" adds `SENSE_HIDDENINPLAINSIGHT` (`affectPhyStats`, `334-345`). While mounted, push, pull and sell are blocked (`okMessage`, `251-257`).
- Taking the item removes the decoration and restores its display text (`executeMsg`, `313-323`).

#### Dissertating (`Dissertating.java`)

- Writes a scroll (`GenDissertation`, `buildScrollItem`, `250-267`) on paper, hide, hemp or silk. The scroll must be blank, and magic items, recipes and non-generic items are refused (`324-352`). The scroll name becomes "<item> on the study of <spell>", and its description is built from two random adjectives (`200-248`).
- Knowledge check: `spellLevel` (`162-192`) maps the qualifying level 0 to 0, 1 to 1, 2 to 3, 3 to 4, 4 to 6, 5 to 7, 6 to 9, 7 to 10, 8 to 12, 9 to 13, and above that adds 5. The character needs `xlevel >= spellLevel` (`372`, `416`).
- Costs. Writing from memory costs mana `10 + 10 * qualifying level - 5 * class level` (minimum 10) and XP `(10 + qualifying level) - class level`, at least the qualifying level (`440-447`). Copying from another scroll costs mana `10 + 10 * spell level` (`438`) and no XP (`481-492` apply only to the memory path).
- Duration: `getDuration(100 + 10 * qualifying level, mob, lowest qualifying level, 10)` (`275-281`).
- Copying takes the spell off the source. On success `eraseFromScrollItem` removes the spell from the source scroll, takes one use off it and removes `100 * lowest level` from its value (`118-120`, `142-160`).
- A failed roll prints "You got writer`s block! Your dissertation on @x1 fails!" (`115`).

#### FireBuilding (`FireBuilding.java`)

- "light fire" builds a fire from deadwood. Indoor rooms are refused (`175-178`), and only outdoor hills, jungle, mountains, plains and woods qualify (`180-191`). Duration is `getDuration(25, mob, 1, 3)`, and the burn is `150 + 5 * xlevel` (`192-193`).
- The result is a GenItem "a roaring campfire", weight 50, wood material (`94-100`), with a `Burning` effect whose ability code 512 destroys the item when the burn ends (`104-106`).
- Lighting an item (`197-304`) uses the item's burn duration. "You need to cook that, if you can." and "That won't burn." are the refusals (`293`, `299`). Containers and drinks have their burnable contents lit instead (`230-266`).
- Weather changes the roll (`306-329`): blizzard, snow and thunderstorm -80; drought +50; dust storm, windy and fog -10; heat wave +10; rain, sleet and hail -50.
- Burning an item that is not raw material on claimed land needs privileges (`281-289`).

#### Enrolling (`Enrolling.java`)

- The teacher must hold the class or have an active title that names it (`221-227`). The target must be a monster on the default class (`244-248`), must qualify (`261-265`), and must be willing, which means in the group, or unable to move, or bound or held (`249-255`).
- Cost: `enrollCost` is meant to scale with level, but returns a constant 500 (bug, see Problems). The fee is taken only after the timed effect starts (`290-293`).
- Duration: the base is `CMProps.getTicksPerMudHour() * hours in the area's day` (`277-281`), or 3600 ticks when there is no area. Then `getDuration(base, mob, target level, 10)` (`283`).
- Completion writes a `Prop_Adjuster` with the text `class=<ID> clstart=<level>` (`171`) and recalculates stats (`174-176`). On failure the fee is refunded: "You recover @x1 in fees from the guild." (`151-152`).
- While enrolling, 20 percent of ticks produce a lecture line from the teacher (`94-131`).

#### Domesticating (`Domesticating.java`)

- Only monsters that are animals can be domesticated (`140-155`). The duration is `35 + level difference`, minimum 10 (`204-206`). The roll is `-5` per level the animal is above the character's `xlevel` (`200-203`).
- Success makes the animal follow the character (`postFollow`, `108`). Otherwise the room hears "@x1 won't follow <S-HIM-HER>" (`112`).
- Naming: "name <animal> <name>" works only on an animal already following someone (`157-161`), with a single word and no spaces (`165-174`). The name is applied through a `Chant_BestowName` effect (`212-220`).
- The tick checks that the animal is still in the room and cancels the taming if not (`81-85`).

#### Floristry (`Floristry.java`)

- Identifies a generic flower material whose name ends in "flowers" (`201-210`). It chooses a random name from `skills/floristry.txt` (`109-117`). An existing hidden identity wins (`119-123`).
- The name is rebuilt by cutting the last 8 characters, the " flowers" suffix, and "a pound of" becomes "some" (`126-134`). The message is "@x1 appears to be @x2." (`125`).
- `isLimitedToOne()` returns true by default (`172-175`), and a bundle is then split to one unit (`211-212`). Duration is `getDuration(15, mob, 1, 2)` (`177-180`).

#### Distilling, DrugCutting and Excavation

- Distilling (`liquors.txt`, 97 rows) runs the cooking machinery with "distill" words (`58-62`). It stirs every 5 ticks (`102-117`). Only intoxicating liquor drinks qualify (`119-135`). After cooking, the drink's liquid type and material are set to liquor (`137-150`). Herbs are not honoured (`70-74`).
- DrugCutting (`drugs.txt`, 8 rows) uses "cut" words and skips the lid requirement (`83-86`). Recipe content is not summarised here.
- Excavation (`excavation.txt`, 32 rows) extends `BuildingSkill`. Its verb is "excavating the <feature>" with the direction (`92-100`). Stone material comes from `fetchFoundResourceData` (`103-113`). The construction logic is in `BuildingSkill`, outside this bucket.

#### DiligentStudying (`DiligentStudying.java`)

- Auto-invoked, non-removable, applied to mobs (`58-86`), and hidden (`displayText` is empty, `52-56`).
- On any level gain (`TYP_LEVEL`, `97`), it adds `(new level - old level) * multiplier` practice points, where the multiplier is the effect's text (`101-104`). A zero multiplier counts as 1. The gain is told to the player (`105-118`) and added with `setPractices` (`120`).
- Humans get it as a racial effect with empty parameters (`Races/Human.java:155-157`). Read literally, that gives one practice point per level gained. That mapping from racial parameter to effect text is unverified.
- `expertises.txt` rows `QUICKCRAFT` (564) and `QUICKWORK` (570) name DiligentStudying in their requirement lists. The semantics were not decoded.

## Content worth mining

Summaries of the data files this code reads. All rows are not reproduced.

| File (`resources/skills/`) | Rows | What it holds | Sample rows |
|---|---|---|---|
| `fletching.txt` | 199 | Bows, slings, crossbows and ammunition. Classes: GenAmmunition 151, GenBow 27, GenSling 18, GenHandBow 3. Ammo types: arrows, bolts, darts, bullets, javelins | "practice shortbow": level 3, 60 ticks, value 27, GenBow, arrows, capacity 20, damage 2, range 1. "composite longbow": level 21, 90 ticks, value 600, damage 10, range 4 |
| `decorations.txt` | 14 | Mounting verbs with an activity phrase and a display phrase | HANG: level 1, 5 ticks, "hanging @x1", "@x1 is hanging here." Others include MOUNT, STICK, LAY, STAND, PLACE, DISPLAY, ARRANGE, FIX, CENTER, ONLEFT |
| `embellishing.txt` | 84 | Clothing embellishments with zapper masks for body slot | "a lace collar": level 1, 5 ticks, torso. "a ruffled skirt": level 6, 25 ticks, legs. "a deep hood": level 7, head |
| `dyeing.txt` | 451 | Dye colours (448 distinct names), with level, ticks, colour markup, mask and an expertise tier | "red": level 1. "delphinium-blue": level 62, expertise tier 5. "oysterbay-blue": level 87, tier 10 |
| `excavation.txt` | 32 | Cave features: rooms, walls, archways, water, boulders, mazes, pools, tunnels, doors | "description" (level 1, 10 ticks); "room" (level 3, 145 ticks); "small maze" (level 15, 190 ticks); "escape tunnel" (level 80, 5000 ticks) |
| `liquors.txt` | 97 | Drinks and their alcohol effect classes, levels and malt or other material | "ale": level 1; "beer": level 2 |
| `fishing.txt` | 58 | Catch table: base fish (carp, catfish, salmon, shrimp, trout, tuna) with weights, override names and values | "Shrimp" weight 10 value 1. "Shrimp" weight 2 gives "Lobster", value 5 |
| `floristry.txt` | 52 | Flower names chosen at random when a flower is identified | roses, violets, lilies, irises |
| `drugs.txt` | 8 | Drug-cutting recipes. Adult content; contents not summarised | not reproduced |
| `expertises.txt` | 720 | Expertise definitions, including crafting enhancement families | `ADVNCRAFT` (445), `RUSHCRAFT` (459), `LITECRAFT` (466), `DURACRAFT` (501), `QUALCRAFT` (529), `LTHLCRAFT` (579), `CNTRCRAFT` (586) |

Messages worth reusing: "There is no @x1 here to make anything from! It might need to be put down first." (`CraftingSkill.java:828`); "Your @x1 crop has failed due to lack of sunlight." (`Farming.java:159`); "The land is not suitable for farming here." (`Farming.java:342`); "Nothing is biting around here." (`Fishing.java:110`); "You don't find any good fishing spots around here." (`FishLore.java:134`); "You can't seem to find anything worth digging up here. You might try @x1." (`Digging.java:122-123`).

## Notable design ideas

- One recipe row covers every material. The `%` placeholder is filled from the consumed material, so names, descriptions and weights follow the material (`CraftingSkill.java:245-300`).
- Crafting consumes what is physically on the floor, and the message tells the player to put it down first. The workshop is the room (`fetchFoundResourceData`, `709-910`).
- Failure produces a ruined item rather than nothing (`dropALoser`, `471-504`). A failed job still changes the world.
- Expertises are typed into the command as words ("sturdy" style enhancements), so the player's phrasing does the choosing. The effects are in one table (`enhanceItem`, `685-912`).
- The "lottery" is a hook: if a listener removes a freshly crafted item, the crafter gets a bonus (`CraftingSkill.java:448-449`).
- Mending is condition on a 0 to 100 scale, with a failed repair still adding something that shrinks as the item improves (`messedUpCrafting`, `302-325`).
- Recipes are learned by taking finished items apart, and player-made items are excluded (`doLearnRecipe`, `2356-2458`).
- Gathering has a per-room depletion model with a time-based recovery (`GatheringSkill.java:69-150`).
- Farming makes compost meaningful: it is an ordinary item identified by its secret identity, consumed by weight to multiply the yield (`Farming.java:169-245`).
- Unknown items keep a hidden true identity that a skill reveals (`Floristry.java:119-123`).
- A campfire is a world object with a burn effect that destroys it, instead of a flag on a room (`FireBuilding.java:94-106`).
- Decorations become room fixtures that carry their old name in a text field, and can be taken down again (`Decorating.java:220-231`, `313-323`).
- Copying a dissertation moves the spell from one scroll to another (`Dissertating.java:118-120`, `142-160`).
- The `info` command previews an item by actually building and destroying one (`checkInfo`, `CraftingSkill.java:1084-1199`).

## Problems and cruft

1. Enrolling's fee is a constant. `enrollCost` computes a level-based cost, then returns 500 (`Enrolling.java:185-193`, the return on line 192). Every enrolment costs 500 whatever the levels, and refunds match.
2. `craftAnyItemNearLevel` ignores its own filter. It builds `choices` for the level window (`CraftingSkill.java:1227-1252`), then picks from all `recipes` (`1255`). Callers include `Thief_Digsite.java:180` and `SnakeOilSelling.java:156`. Alchemy overrides it (`Alchemy.java:288`).
3. The weapon search skips rows. The class and type loops at `CraftingSkill.java:1532-1533` and `1544-1545` start at `RCP_LEVEL + 1` (2) over the row list, so the first two rows are never checked there. A later loose pass (`1705-1732`) starts at 0, which limits the damage.
4. Dyeing's list can throw. `colors.substring(0, colors.length() - 2)` (`Dyeing.java:141`) fails when the builder is empty, which happens when the character has no colour available. Found by reading; not executed.
5. Dissertating's mana check does not stop the job. `Dissertating.java:468-471` prints "You need at least @x1 mana" but does not return, and `adjMana` clamps at zero (`Common/DefaultCharState.java:310-317`). A caster short of mana still completes the job.
6. `last25items` is trimmed at 5, not 25. The field is named for 25 but `CraftingSkill.java:416` tests `size() == 5`, so the repeat window is five.
7. Match rules differ between skills. `Decorating.java:378-387` keeps the last matching row (no break), while `Embellishing.java:303` keeps the first. `matchingRecipes` also has two weapon blocks and two armour blocks with different word rules (`1517-1611`, `1705-1754`).
8. Tick guards call `unInvoke()` and keep going. `Enrolling.java:83-92`, `Decorating.java:145-154` and `Embellishing.java:143-152` have no return after unInvoke, so the second check can run on a finished action. `Enrolling.java:88` dereferences `mob.location()` after the null test on line 83 (possible NPE). `Domesticating.java:81` dereferences `mob.location()` with no null test. `Farming.java:145-151` passes a possibly null `mob` to `commonTelL`. These paths are reachable only if the location or the invoker is null while ticking, which the code does not exclude.
9. `Digging.java:112`, `bumpTickDown(tickDown * 2)`, triples the remaining time (`CommonSkill.java:814-818`). The comment-free intent is unclear.
10. Dead branch. `Dissertating.java:356` and `410` set `ingredient = ""` and never change it. The resource branch at `450-464` and `478-479` therefore never runs.
11. `FORT` and `VIGO` both add to `STAT_MOVE`, and only IMBU changes the stat (`EnhancedCraftingSkill.java:710`, `887-899`). This may be a copy-paste slip. Their expertise rows are absent from the shipped data, so the branch is dormant.
12. Duplicate code. Engraving and Embroidering are near copies that differ in words, materials and brand check (`Engraving.java:35-179`, `Embroidering.java:35-152`). Fishing, Digging and Drilling each re-implement the same bundle and yield steps.
13. `Floristry.java:127-128` strips a fixed 8 characters. A name not ending in " flowers" is cut wrongly. The multi-item loop at `141-165` mixes a proficiency roll into its flow, and its termination was not proven.
14. Smaller issues. `DiligentStudying.java:118` says "where" for "were". `Decorating.java:317-318` uses the room without a null check. `Embellishing.java:197` builds a `CraftingSkill` just to call a helper. `FishLore.java:107` masks the room resource but `110` does not.
15. Multiplayer assumptions. Ownership and land-title checks for crafting and burning (`CraftingSkill.java:2114-2121`, `FireBuilding.java:281-289`). Group checks and "honorary title" checks for enrolment (`Enrolling.java:221-255`). A shared room depletion counter (`GatheringSkill.java:69`). A per-crafter `last25items` list. Each one needs rethinking for one player.
16. Hard-coded magic numbers, such as 50 XP for the lottery, the 75-tick XP cap, 0.05 time per TIME-expertise level, the 100-tick base for dissertations (`Dissertating.java:277`), and the burn code 512 (`FireBuilding.java:105`). Several are unexplained in the code.

## Takeaways for a Python SUD

- Recipes as data. Load rows from TOML or CSV into a frozen dataclass. Keep the `%`-style material placeholder, and drop the positional tab columns.

  ```python
  @dataclass(frozen=True)
  class Recipe:
      name: str                # "crude {material} atlatl"
      level: int
      turns: int               # base time in game turns
      inputs: dict[str, int]   # resource kind -> amount found on the floor
      value: int
      output: str              # template id
  ```

- Matching. Keep only what players need: exact name, level filter (`N`, `N+`, `A-B`) and `N.name` selection. Use separate operators for "at most" and "at least". In the original, a minus suffix means "at least" (see section 2), which players may not expect.
- Timed actions. Model a `Job` with start turn, duration, reserved inputs, `messed_up`, and `aborted`. Resolve it on the turn it ends. An effect system is unnecessary.
- Duration. Copy the shape of `CommonSkill.java:346-360`, but in turns:

  ```python
  def craft_turns(base, crafter_level, item_level, time_skill, floor, cap=91):
      pct = (crafter_level - item_level) / cap * 0.5
      t = base - round(base * pct)
      t = max(t, floor)
      t -= round(t * 0.05 * time_skill)
      return max(t, floor)
  ```

- XP. Keep the level-difference shape and drop the repeat penalty, or base it on a per-character list of recent recipes.

  ```python
  def craft_xp(turns, item_level, crafter_level, limit=6):
      diff = item_level - crafter_level
      if diff < -limit:
          return 0
      factor = 100 * (1 + min(diff / limit, limit))
      return min(turns, 75) * factor / 25
  ```

- Enhancements as a table. Use explicit enum values and a table, not bit masks:

  ```python
  RUSH = [  # value x, time x, item level delta  (EnhancedCraftingSkill.java:735-759)
      Enhancement(value=0.9, time=0.9, level=-2),
      Enhancement(value=0.8, time=0.8, level=-3),
      Enhancement(value=0.7, time=0.7, level=-4),
  ]
  ```

  Keep the hidden flag as a plain field. Keep the rule that one enhancement per family applies.
- Room depletion on game time. Track `(resource, count, last_game_turn)` per room and use the factors 1.0, 0.8, 0.6, 0.4 and 0.2 for successive harvests. Refuse the sixth. Reset after a set number of game hours, not 30 real minutes.
- Failure. A failed job produces a ruined item (`dropALoser`). This keeps time and materials meaningful without a dead-end.
- Mending. Use `condition: int` from 0 to 100. Repair sets it to 100. A botched repair uses the table in section 6, so the code is three lines.
- Learn by taking apart. A finished item that is not player-made can teach its recipe to a blank recipe book. This is a good exploration hook.
- Preview. Offer an `info` action that builds the output in memory, reports its description and time, and discards it (`checkInfo`).
- Fire and tools as world state. Metal work needs a fire object in the room (`Fletching.java:429-450`). Do not model it as a skill flag.
- Drop. Land titles, group and "honorary title" checks, the shared worker MOB, the bundle grammar, the bit-packed stage codes, and the secret-identity string encoding. Keep the veto hook only if scripts need it.
- Rethink for one player. Room depletion can stay as a world rule, but it is a world property rather than a shared multiplayer limit. The enrolment and domestication effects can be ordinary NPC actions with a fee and a timer.
- Time units. The code's tick is 4 seconds (`TICKTIME = 4000`, `coffeemud.ini:2151`). A turn-based SUD should state its time unit once and convert the constants (for example 91 for the level cap, 6 for the XP limit) to its own scale.

## Coverage

- Read in full (all 22 files, every line): `CraftingSkill.java` (2459 lines, in four chunks), `EnhancedCraftingSkill.java`, `Fletching.java`, `Farming.java`, `Dissertating.java`, `Decorating.java`, `Embellishing.java`, `FireBuilding.java`, `Enrolling.java`, `Fishing.java`, `Drilling.java`, `Floristry.java`, `Digging.java`, `Domesticating.java`, `Dyeing.java`, `FishLore.java`, `Engraving.java`, `Embroidering.java`, `Distilling.java`, `DiligentStudying.java`, `DrugCutting.java`, `Excavation.java`.
- Skimmed: none in this bucket.
- Skipped: none in this bucket.
- Outside the bucket, consulted at named points only: `CommonSkill.java` (`getDuration` 346-360, `xlevel` 645-648, `lookingForMat` 449, `bumpTickDown` 814-820, `setBrand` 371); `GatheringSkill.java` (room depletion 69-150, `bundle` 365); `StdAbility.java` (`canBeUninvoked` field 56, method 1542, `proficiencyCheck` declaration 1441, `getXLEVELLevel` 444-447, body not read); `DefaultCharState.java:310-317`; `core/interfaces/RecipeDriven.java:35,40`; `Races/Human.java:155-157`; `coffeemud.ini` lines 406 (EXPRATE=6), 515 (LASTPLAYERLEVEL=91) and 2151 (TICKTIME=4000); `resources/skills/expertises.txt` rows named above (grep of prefixes; FORT, VIGO and IMBU crafting rows absent).
- Data files: sampled by row count and selected rows, not read in full: `fletching.txt`, `decorations.txt`, `embellishing.txt`, `dyeing.txt`, `excavation.txt`, `liquors.txt`, `fishing.txt`, `floristry.txt`; `drugs.txt` counted only; `expertises.txt` searched by key.
- Not verified: the bodies of `proficiencyCheck` (`StdAbility.java:1441`), `PaintingSkill.addPaintJob` and `removePaintJob`, `CMLib.utensils().ruinItem`, `CMLib.utensils().addExtRecipes`, `CMLib.materials().destroyResources`, the `Burning` ability, and the `getXTIMELevel` body. No code was executed. The Dyeing list crash, the Dissertating mana bypass and the Enrolling fee are reading-based findings.
