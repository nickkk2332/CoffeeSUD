# Abilities/Common part 4: gathering, crafting, brewing and food skills

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b045` (Abilities/Common [part 4/7: FlowerArranging .. JewelMaking]) · 22 files · 9076 lines of Java.

## Summary

These 22 classes implement the gathering, crafting, brewing and food skills in the middle of the `Abilities/Common` alphabet. Each skill is a timed action: `invoke()` checks requirements, consumes inputs and attaches a `CommonSkill` effect with a duration in ticks; when the effect expires, `unInvoke()` delivers the result (a gathered resource, a built item, a brewed potion, a harvested crop, a summoned prey animal). Two data-driven engines carry most of the content: `GenGatheringSkill` and `GenCraftSkill`/`GenWrightSkill` read rows from `resources/skills/*.txt`, while the hand-written classes read their own tables (hunting, herbalism, jewels, glass, instruments, foraging). Gathering stays sustainable through a per-room diminishing-returns counter, and crafting resolves through one proficiency roll that chooses between success and a "messed up" outcome. The logic is table-driven and easy to read, which makes it a good model for Python data files.

## Key files and types

| File (under `com/planet_ink/coffee_mud/Abilities/Common/`) | Role | Lines |
|---|---|---|
| `GatheringSkill.java` | Base for all gathering: room yield depletion, resource lookup, `bundle` command | 513 |
| `GenGatheringSkill.java` | Data-driven gatherer (`Gen*`): item pool, yield formula, timing, messages | 784 |
| `Foraging.java` | Gathers vegetation, hemp, silk, cotton, salt from the room | 273 |
| `Hunting.java` | Spawns a prey animal from `resources/skills/hunting.txt`; prey and hunter AI | 389 |
| `Herding.java` | Mounted herding of same-race animals in one direction | 286 |
| `Gardening.java` | Plants a seed; the crop is a room-level effect that grows and is harvested | 485 |
| `GenCraftSkill.java` | Data-driven crafting from recipe rows (extends `EnhancedCraftingSkill`) | 1031 |
| `GenWrightSkill.java` | Shipwright and construction: ship recipes, room title and description, doors, demolition | 1444 |
| `JewelMaking.java` | Jewel crafting, encrust/mount of gems, refit, fire required | 814 |
| `GlassBlowing.java` | Glass items; needs a live fire during the work | 654 |
| `InstrumentMaking.java` | Musical instruments with race gating and instrument type | 436 |
| `Herbalism.java` | Brews chant potions from herbs placed in a container; costs XP | 561 |
| `ImprovedHerbalism.java` | `Herbalism` with level bounds and its own recipe file | 101 |
| `ImprovedAlchemy.java` | `Alchemy` with a level-adjusted brew duration | 115 |
| `Herbology.java` | Identifies unknown herbs; assigns their identity from a table | 267 |
| `FoodPreserving.java` | Preserves food by attaching a `Prayer_Purify` effect | 174 |
| `FoodPrep.java` | Food preparation (extends `Cooking`); also slices a food item in two | 197 |
| `GaolFood.java` | `Cooking` variant for gaol food recipes | 118 |
| `Irrigation.java` | `BuildingSkill` variant that works on rock and stone, and on room titles | 120 |
| `FlowerArranging.java` | Passive: adds a flavour message when flowers are put in a container | 100 |
| `ImprovedTeaching.java` | Passive: changes the value of lessons the character teaches | 116 |
| `Gunsmithing.java` | `Fletching` variant with its own `mayICraft` rule | 98 |

Base classes outside this bucket that matter: `CommonSkill.java` (timing, abort rules, fake fire, drop logic), `CraftingSkill.java` (`buildingI`, learn path), `EnhancedCraftingSkill.java`, `SpellCraftingSkill.java`, `Cooking.java`, `BuildingSkill.java`, `Alchemy.java` and `Fletching.java`.

## How it works

### 1. Shared skeleton

- **Class chain.** `GatheringSkill` extends `CommonSkill` (`Common/CommonSkill.java:38`), which extends `StdAbility`. Crafting adds `CraftingSkill` (`CraftingSkill.java:44`), then `EnhancedCraftingSkill` or `SpellCraftingSkill`, then the concrete class. `Foraging`, `Hunting`, `Gardening` and `Herbology` stop at `GatheringSkill` or `CommonSkill`.
- **Invoke, then effect, then result.** `invoke()` validates the request, consumes inputs, sets `duration` and calls `beneficialAffect(mob,mob,asLevel,duration)`. The room gets a `CMMsg` through `okMessage()` and `send()`, so other listeners can veto it. `unInvoke()` runs when the effect ends and produces the result. The same pattern appears in every class in this bucket.
- **Duration.** `CommonSkill.getDuration(baseTicks, mob, itemLevel, minDuration)` (`CommonSkill.java:346-363`) lowers `baseTicks` by `round(ticks * ((mobLevel - itemLevel) / LASTPLAYERLEVEL * 0.5))`, floors the result at `minDuration`, then lowers it again by `getXTIMELevel(mob) * 0.05` (5% per extended-time level). Gathering passes different `itemLevel` values (see section 3).
- **Abort rules.** `CommonSkill.tick` (`CommonSkill.java:230-251`) sets `aborted=true` and unwinds when the character is in combat, has left the activity room, is not awake and mobile, or is sitting when the skill cannot be done sitting. `unInvoke` then prints "<S-NAME> stop(s) <verb>" instead of "<S-NAME> <S-IS-ARE> done <verb>" (`CommonSkill.java:333-337`).
- **Proficiency roll.** `StdAbility.proficiencyCheck(mob, adjustment, auto)` (`Abilities/StdAbility.java:1441-1480`) starts from the skill's proficiency, adds `PROF+` adjustments, adds `2*XLEVEL`, clamps to 5-95 unless the `DIS955RULE` security flag is set, then adds `XLEVEL/2`. Auto-invoked effects always pass.
- **Dice.** `CMLib.dice().roll(n, d, m)` (`Libraries/Dice.java:398-406`) returns `n` dice of `d` plus `m`. `roll(1, N, -1)` returns an index from 0 to N-1, which is how the code picks list items.

### 2. GatheringSkill: room depletion and bundling

- **Depletion.** `GatheringSkill` keeps `roomSpamCounter`, an `ExpireHashMap` keyed by room with a 30-minute lifetime (`GatheringSkill.java:69-70`). The record stores the room, the room's resource code, a counter and an expiry time set on the first gather (`:136`) and not refreshed.
- **Yield after k prior gathers.** The drop rate is `0.20` per prior gather (`GatheringSkill.java:79`). `adjustYieldBasedOnRoomSpam` returns `round(initial - initial * 0.2 * k)`, or 0 if that is 0 or less (`:144-149`). With a base yield of 10 that gives 100%, 80%, 60%, 40% and 20% for the first five gathers.
- **Refusal.** `checkIfAnyYield` returns false once `round(10 - 10 * 0.2 * count) < 1` (`:103-106`), which happens from the sixth gather. The record is cleared when it expires or when the room's `myResource()` changes (`:92-96`, `:123-125`).
- **Resource lookup.** `myResources()` (`:268-363`) parses a `|`-separated mask. Each token is a `_RESOURCE` name, a `Material` group with optional `-EXCLUDE` names, or a resource name. The result is sorted by `RawMaterial.CODES.FREQUENCY` (higher first), then by value (lower first on ties).
- **Resource fix-ups.** `fixResourceRequirement` (`:161-183`) halves mithril and balsa, divides adamantite by 3 and doubles ironwood, with a minimum of 1. Crafting calls it too (`CraftingSkill.java:848, 882`).
- **Bundling.** `bundle()` (`:365-512`) merges loose raw items on the floor of the same resource and subtype into one pile whose weight equals the requested amount, using `allFound` and `foundAblesH` to carry over non-removable effects. The `name.N` suffix selects the Nth matching pile.
- **Stealth.** `affectPhyStats` adds `PhyStats.CAN_NOT_TRACK` (`:157`).

### 3. GenGatheringSkill: data-defined gatherers

- **Storage.** Each instance is keyed by its `ID` in static maps (`vars`, `items`). `V(ID, n)` lazily creates defaults in `makeEmpty()` (`GenGatheringSkill.java:84-108`). Fields are addressed by `CODES` (`:197-219`), so builders can edit them in-game with `setStat`/`getStat`.
- **Defaults.** Verb `gathering`, trigger `GATHER`, resource `WOODEN`, base duration 40 ticks (`BDUR`), minimum 12 (`MDUR`), find tick 6 (`FDUR`), yield formula `(1?5)+3`, sound `sawing.wav` (`:87-100`).
- **Constraints.** `MINDUR` must be positive, `BASEDUR` must be at least `MINDUR`, and `FINDTICK` must be at most `MINDUR` (`:413-423`).
- **Item pool.** `getItems()` (`:564-604`) parses the `ITEMXML` field, which is either an `<ITEMS>` or `<ITEM>` XML block or a file name. Entries are picked uniformly with `roll(1, n, -1)` (`:759`). `ISCOSMETIC` produces a generic `StdItem` named "Something" when the pool is empty (`:761-767`).
- **Timeline.** `invoke()` (`:716-783`) starts the activity and calls `beneficialAffect` with `getDuration(mob, mob.basePhyStats().level())`. At tick 6 (`FDUR`) the find is announced, or "You don't find anything useful to gather here." cancels the skill (`:486-514`).
- **Yield.** `unInvoke()` (`:516-562`) evaluates the yield formula with `CMath.s_parseIntExpression`, multiplies by `baseYield() + abilityCode()` (`baseYield` is 1 here, `:480-484`), and applies room depletion. It then calls `okMessage` and `send`, and copies the found item once per yield unit with `dropAWinner`.
- **Formula semantics.** In `CMath`, `a?b` evaluates to `a + (b - a + 0.5) * rand`, then rounds (`core/CMath.java:1474-1476`). `(1?5)+3` therefore yields 4 to 8. The value 1 is about half as likely as each of 2 to 5.

### 4. Foraging, Hunting and Herding

- **Foraging** (`Foraging.java`). Duration `getDuration(45, mob, 1, 10)` (`:87-90`). The room must hold a vegetation, hemp, silk, salt or cotton resource (`:184-208`). At tick 6 the result is announced, or the skill ends with a hint about a neighbouring room that has vegetation (`:104-120`). The yield on completion is `roll(1,10) * (1 + bonus)` for cloth and `roll(1,3) * (1 + bonus)` otherwise (`:137-139`), reduced by room depletion. Herb items with a name ending in " HERBS" get a secret identity drawn from a per-room table in `skills/herbs.txt` (`:216-264`). That file is not present in this checkout (see Problems).
- **Hunting** (`Hunting.java`). `invoke()` (`:234-388`) reads the room's resource and domain (`:247-259`), passes the proficiency and depletion gate, and needs an open exit (`nearByRoom`, `:80-99`). It then loads `hunting.txt` and makes a weighted pick over rows matching the resource and domain (`:265-300`). The pick is `roll(1, totalWeights, -1)` against cumulative frequency. The mob comes from `CMClass.getMOB`, then `CMLib.catalog().getCatalogMob`, then a generic `GenMob` or `GenRideable` built from the race (`:303-348`).
  - Level: `moblevel = round(sqrt(hunterLevel))`, with `addlevel = moblevel - EXPRATE` once it reaches `EXPRATE` (`:349-351`). `CMLib.leveler().fillOutMOB` then fills the prey (`:357`).
  - Duration: `10 + hunterLevel + 2 * XTIME` (`:379`).
  - Each tick (`tick`, `:128-209`) the prey is moved out of the room with `moveFound` when the hunter is a player (`:141-156`). An NPC hunter attacks it with `postAttack` if it can see it, or otherwise walks toward it with 50% chance per tick (`:146-155`, `:190-206`). At `tickUp==0` the prey is announced, given `Prop_ModExperience` "=20%" (`:172-174`) and brought to life in an adjacent room (`:175`). Leaving the skill early destroys the prey and prints "lost the trail" (`:219-227`).
- **Herding** (`Herding.java`). Requires riding a mount (`:179-183`). It collects animals of the same race in the room (`:218-227`), then computes the herd size as `round(totalWeight / avgWeight * (1 + XLEVEL))`, where `totalWeight` is the weight of the mount and all its riders (`:100-108`, `:251-254`) and `avgWeight = lightestWeight + weightVariance / 2`. At tick 0 every herd member and the mount step in the direction, and each member is marked to wander home after one hour (`:131-143`). The effect duration equals the herd size (`:282-283`).

### 5. Gardening: a crop as a room effect

- **Two phases.** `invoke()` (`Gardening.java:274` onward) checks sunlight, a suitable land domain or `DIRT` resource, no drought (`:293-310`), and that no garden is already present (`:315`). It then finds a seed item (flowers, herbs or garlic) on the floor, unbundles one unit (`:431`) and destroys it (`:468`). The planter gets a `beneficialAffect` lasting `getDuration(45, mob, 1, 15)` (`:89-91`).
- **Growth.** When the planting effect ends, `unInvoke()` (`:162-242`) copies a new `Gardening` effect onto the room with `tickDown = 50` (`:239-240`). That room effect counts room ticks where the sun is visible (`:108`). At room tick 6 it fails if no crop was planted or the planter is gone (`:111-121`). After tick 10 it fails if sunlit ticks are below half the elapsed ticks (`:122`).
- **Harvest.** The yield is `roll(1,7) * (1 + bonus)` (`:172`). Compost items (secret identity "compost") in the room add yield: each unit of compost weight adds the base yield, up to ten times the base in total weight (`:174-205`). The crop is then copied out with `dropAWinner(null, room, ...)` and announced as "<n> <crop> have grown here." (`:218-224`).

### 6. GenCraftSkill: recipe-driven crafting

- **Recipe row.** `getRecipeFormat()` (`GenCraftSkill.java:86-101`) defines the columns: name, level, build ticks, materials, base value, class ID, a misc field (lid, lock, wear location, weapon class, ride basis, smoke flag), capacity, base armour or damage, container mask, spell list, and a key-value string. The key-value column can carry `RACEREQUIREMENT` and any `setStat` field (`:981-991`).
- **Gating.** Level must be at most `xlevel(mob)` (`:808-810`, `list` at `:694`). Race is checked by `checkRecipeRace` (`:598-616`). The name is matched exactly first, then loosely (`:800-802`). A trailing number sets the amount (`:792-797`).
- **Materials.** Components are reserved first (`:827`). Materials are then destroyed, and `lostValue` is recorded from the destroyed material and component value (`:853-862`). Bundled output uses the amount as its quantity (`:873-874`).
- **Quality from material hardness.** The item level is `recipeLevel + hardness`, where `hardness = HARDNESS(material) - 3` unless `NOHARDBONUS` (`:886-889`). Weapons get `attackAdjustment = base + bonus + hardness*5 - 1` and `damage = armordmg + hardness` (`:954-955`). Armour gets `armor = armordmg + base + bonus - 1` (`:964`). Containers get `capacity = recipeCapacity + numRequired` (`:905`). Drinks get `liquidHeld = capacity*50` (`:929`).
- **Duration.** Recipe ticks are passed through `getDuration(..., minimum 4)` (`:870`). Bundling takes 1 tick (`:1002`).
- **Outcome.** `messedUp = !proficiencyCheck(mob, 0, auto)` (`:997`). `unInvoke()` (`:476-531`) then either drops the item, or emotes "<S-NAME> mess(es) up <verb> <item>" and drops a ruined copy with `dropALoser`. Mending resets uses to 100 (`:501-503`).
- **Locks.** A lock adds a `GenKey` whose key name is `Math.random()` (`:914-923`).
- **Matching by regex.** `mayICraft` (`:533-557`) turns the recipe name into a regular expression (`%` becomes `.*`) and calls `Pattern.matches` (`:552-553`). See Problems.

### 7. GenWrightSkill: shipwright and construction

- **Scope.** This class builds boats and ships (`Boardable` items). `CraftorType.LargeConstructions` (`:107-110`). It has five activities: build, mend, retitle the room (`title`), redescribe it (`desc`), and cut or demolish a door (`door`, `demolish`). Recipes come from `skills/<FILENAME>`, which holds XML item prototypes such as `skills/shipbuilding.cmare` (`:201-221`). The recipe list is rebuilt into a temp `.txt` file under `skills/` (`:236-268`), a side effect the code performs at runtime.
- **Ship build.** The prototype is copied with `copyOf()` (`:1359`). The duration is `getDuration(ticks, mob, level, 6)` (`:1360`). The value is recipe value plus `lostValue` (`:1372`).
- **Ownership.** Clan-only recipes need a clan role that can own property (`:817-834`). Repairs check ownership for `PrivateProperty` items (`:534-565`). Titles use `CMLib.law().doesOwnThisProperty` (`:1027`).
- **Room work.** `title` needs a title between 1 and 250 characters, unique among the rooms within a 20-room radius (`:1044`, `:1049-1059`), and takes `getDuration(10, mob, level, 3)` (`:1064`). `desc` takes `getDuration(40, ..., 10)` (`:1105`). A door costs 125 material units (`:1164`), must be indoors ("You can only build a door below decks.", `:1147`), and takes `getDuration(25, ..., 10)` (`:1190`). Demolition takes the same time and replaces the exit with an `Open` exit (`:360-375`).
- **Door build.** `buildDoor` creates a `GenDoor` on both sides of the wall, sets `setOpenDelayTicks(9999)` (`:290`, meaning not verified), and saves both exits to the database (`:270-308`).
- **Sale.** On completion of a ship, `doTitleTransfer` runs a `MSG_GET` through a temporary `StdShopkeeper` set to `DEAL_SHIPSELLER` or `DEAL_CSHIPSELLER` (`:310-327`). The buyer gets a `LandTitle` (`:440-445`), the ship is docked and given a home port (`:449-451`), and a monster buyer gets a random name (`:429-432`).

### 8. JewelMaking, GlassBlowing and InstrumentMaking

- **JewelMaking.** Recipe row (`getRecipeFormat`, `JewelMaking.java:78-87`) has the usual columns plus `STATUE` or coded wear location, dice sides, a precious material, and spells. Fire is required (`:103-125`). If the fire goes out while a piece is being worked, `messedUp` is set and the work is lost. `encrust` and `mount` (`:480-520`) destroy the gem at the start (`:516`) and attach it at the end. On success the piece's value rises by twice the gem's base value (`:169`), and its description records the gem (`:170`). On failure the unInvoke only emotes "mess(es) up" (`:165`), so the gem is lost and no value is added (the value line `:169` runs only on success).
  - Price and weight: the item value is recipe value plus `woodRequired * VALUE(metal)` plus the value of the secondary material (`:743-748`). Weight comes from `getStandardWeight`, with a floor of 1 (`:740-742`).
- **GlassBlowing.** `tick()` (`GlassBlowing.java:113-127`) aborts with `messedUp` whenever there is no fire at the crafter's location. `doLearnRecipe` sets `fireRequired=false` (`:142-146`), and `CommonSkill.getRequiredFire` then returns a fabricated fire (`CommonSkill.java:501-511`) so that learning still passes the fire check. The failure message is "@x1 explodes!" (`:166`). Glass items are containers (`:574-580`), drinks (`:599`) or light sources.
- **InstrumentMaking.** Race gating is by recipe column `RCP_RACES` (`InstrumentMaking.java:259-262`, `:293-296`). `RCP_TYPE` is matched against the `InstrumentType` enum (`:377-381`). Rideable instruments become furniture-sit seats, with rider capacity from the misc field (`:383-389`). `mayICraft` accepts names the recipe list already knows (`CraftingSkill.isANativeItem`, `CraftingSkill.java:2011`) or any `MusicalInstrument` (`:133-147`).

### 9. Brewing: Herbalism, ImprovedHerbalism and ImprovedAlchemy

- **Herbalism** (`Herbalism.java`). The player names a chant and a container. The container must be a `Drink` holding liquid and must contain every herb or material in the recipe, and nothing else (`:401-527`). The recipe row is `SPELL_ID, LEVEL, ingredient x6` (`:79-89`, data in `herbalism.txt`).
  - Cost: for chants, `experienceToLose = 10 + spellLevel*10 - qualifyingClassLevel*5`, minimum 10 (`:466-478`, `:529-530`). The cost is charged with `postExperience` before the brew, whatever the outcome (`:535-541`). If the adjusted cost is zero or less, the brew stops with "You are unable to complete this brewing." (`:539-543`).
  - Brew: the container is destroyed and replaced by a `GenMultiPotion` carrying the spell list, with thirst 10, liquid 100 and level equal to the recipe level (`:544-546`, `buildItem` at `:269-284`). Duration is `max(10, 5 * level)` (`:292-298`).
  - Failure: "Something went wrong! <potion> explodes!" (`:248`), and the potion is destroyed.
  - Refusal rules in `mayICraft` (`:169-230`): liquor and poison liquids are refused, and only chant-class spells are allowed. The recipe list (`herbalism.txt`) covers 36 chants at levels 1 to 25.
  - LEARN is disabled because ingredients cannot be determined (`:392-398`).
- **ImprovedHerbalism** (`ImprovedHerbalism.java:69-100`). Uses `improvedherbalism.txt`, which is byte-identical to `herbalism.txt`. It refuses brews below the spell's lowest qualifying level (`:87-91`), and tries to refuse brews above `level + 2*XLEVEL` (`:93-97`), but the second check does not return (see Problems).
- **ImprovedAlchemy** (`ImprovedAlchemy.java:69-114`). Brew duration grows by 3 per spell level above the base, with a floor of 10 (`:71-76`). `spellLevel` caps the requested level at the caster's level, at `level + EXPRATE`, and raises it to the spell's lowest qualifying level (`:81-98`). `improvedalchemy.txt` maps 83 `Spell_*` and 46 `Prayer_*` entries to a material and a form (POTION or POWDER).

### 10. Herbology, FoodPreserving, FoodPrep, GaolFood, Irrigation and Gunsmithing

- **Herbology** (`Herbology.java`). Study takes `getDuration(15, mob, 1, 2)` (`:212-215`). Unknown herbs have an empty or "unknown" secret identity. At the end, a random herb name from `herbology.txt` (31 names, `:107-110`) is chosen (`:157-160`), the item is renamed "<name> <herb>", and its subtype is set (`:165-175`). Bundles are unbundled one unit at a time (`:150-153`). Only one item is identified (`isLimitedToOne`, `:207-210`).
- **FoodPreserving** (`FoodPreserving.java`). Only non-flesh `Food` that is not already rotten (`Poison_Rotten`) or preserved (`Prayer_Purify`) can be preserved (`:138-155`). Duration is `getDuration(1 + 2*weight, mob, 1, 3)` (`:165`). On success `Prayer_Purify` is attached permanently (`:107`). On failure it is attached for 20 ticks (`:109`), so the food is only briefly protected.
- **FoodPrep** (`FoodPrep.java`). Its `slice` subcommand (`:129-192`) halves a food item's weight and nourishment into two items. It names the halves with a prefix ladder, "a slice of", "a small slice of", "a smaller slice of", "a tiny slice of" (`:120-127`), and returns false even when the slice succeeds (`:192`).
- **GaolFood** (`GaolFood.java`). A `Cooking` subclass whose only override is its recipe file (`gaolfood.txt`, 19 rows) and its stir messages (`:103-118`).
- **Irrigation** (`Irrigation.java`). Extends `BuildingSkill`, uses ROCK or STONE, and restricts `canDescTitleHere` to underwater or water-surface rooms (`:92-107`). Its recipe file (`irrigation.txt`, 31 rows) holds room verbs: description, title, door, demolish.
- **Gunsmithing** (`Gunsmithing.java`). Uses `gunsmith.txt` (114 rows) with METAL and one column (`:69-78`). `mayICraft` accepts ammunition only (`:81-96`).

### 11. Passive skills: FlowerArranging and ImprovedTeaching

- **FlowerArranging** (`FlowerArranging.java:51-99`). Auto-invoked and not removable. Its `executeMsg` watches for a PUT of a flower item into a container that has no door. The check is `proficiencyCheck(null, 10 * XLEVEL, false)`. On success it adds a trailer message "<S-NAME> arrange(s) <O-NAME> in <T-NAME>" (`:90`). Proficiency rises on a 1-in-100 roll (`:88-89`). It has no mechanical effect beyond the message. Its `tick` returns true and does nothing (`:96-99`).
- **ImprovedTeaching** (`ImprovedTeaching.java:95-115`). Auto-invoked and not removable. It listens to `okMessage` for `TYP_TEACH` messages that carry an ability. On a proficiency success the lesson value becomes `15`, scaled by the teacher's proficiency when they know the skill (`:107-109`), plus `XLEVEL` (`:110`). The value goes into the message (`:111`). `StdMOB` passes it to `CMLib.expertises().handleBeingTaught` (`StdMOB.java:3471-3473`).

### 12. Messages shown to players

Short examples, quoted and paraphrased from the code:

- Foraging: "You have found some @x1!", "You can't seem to find anything worth foraging around here.", "<S-NAME> manage(s) to gather @x1 pounds of @x2." (`Foraging.java:108-150`).
- Hunting: "You have found some @x1 tracks!", "You need to find the @x1 nearby before the trail goes cold!", "<S-NAME> <S-HAS-HAVE> lost the trail." (`Hunting.java:164-226`).
- Gardening: "Your @x1 garden has failed.", "@x1 @x3@x2 have grown here." (`Gardening.java`).
- Crafting: "<S-NAME> mess(es) up @x1.", "Something went wrong! @x1 explodes!" (`Herbalism.java:248`), "You lose @x1 experience points for the effort." (`Herbalism.java:538`).
- Wright: "You can only build a door below decks.", "That title has already been taken. Choose another." (`GenWrightSkill.java:1147`, `:1056`).

## Content worth mining

**Gatherer tables (`resources/skills/hunting.txt`, 82 rows, 38 creatures).** Columns are resource, domain, frequency and creature. Resources are led by FISH (17 rows), HIDE (12), TUNA and SHRIMP (9 each), DIRT (8), and then SCALES, MEAT, LEATHER, FUR and FEATHERS. Domains are mostly `*` (29 rows), with UnderSaltWater (18) and SaltWaterSurface (9) for sea species. The creatures include Sheep, Cow, Bull, Wolf, Buffalo, Lion, Seal, Walrus, Whale, Shark and Chicken. The 4 FUR rows are broken (see Problems).

**Herbalism brews (`herbalism.txt`, 36 chants, 1633 bytes).** Levels 1 to 25, for example:

| Chant | Level | Ingredients (first two) |
|---|---|---|
| `Chant_PredictWeather` | 1 | CARROTS, HERBS |
| `Chant_HardenSkin` | 1 | PINE, HERBS |
| `Chant_LocatePlants` | 2 | GREENS, HERBS |
| `Chant_SensePoison` | 2 | LIQUOR, HERBS |
| `Chant_LocateAnimals` | 4 | FUR, Damiana |
| `Chant_VenomWard` | 8 | SAND, Myrrh |

**Herb identities (`herbology.txt`).** 31 names, for example angelica, mustard, anise, myrrh, cassia, peppermint, chamomile, poppy, cloves, rosemary, lemon grass, sage, damiana, sarsaparilla, gentian, valerian, yerba mate, garlic, ginger, lavender, nutmeg and peppercorn.

**Alchemy (`improvedalchemy.txt`, 129 rows).** Each row maps a spell or prayer to a material and a form, for example `Spell_IronGrip` to IRON and POTION, `Spell_InsatiableThirst` to SALT WATER and POTION, `Spell_Blur` to COTTON and POWDER.

**Jewels (`jewelmaking.txt`, 176 rows).** GenArmor 83, GenItem 60, GenEarring 17, GenDice 14, GenLimb 2. Examples: "Barette" (head), "Wedding Band" (finger, with `Prop_WearSpellCast` and `Prayer_Fidelity`).

**Glass (`glassblowing.txt`, 88 rows).** Mostly containers and drinks: GenContainer 24, GenEmptyDrink 22, GenDice 14, GenItem 9, GenPipe 4, GenMirror 4, GenResource 3, GenLantern 2 (top classes).

**Instruments (`instruments.txt`, 79 rows).** Types from the `InstrumentType` enum, for example GUITARS (8), OBOES (7), DRUMS (7), TUBAS, FLUTES, CYMBALS, CLARINETS, XYLOPHONES, VIOLINS, TROMBONES, PIANOS, ORGANS. Example: "a pair of Clappers" (held, wooden, WOODS type).

**Food (`foodprep.txt`, 52 rows; `gaolfood.txt`, 19 rows).** Plain food recipes such as dough (wheat and water), flour, and cheese (milk). Gaol food has comic names such as "spoiled dough", "infested flour" and "stale water".

**Ships and gunsmith.** Ship prototypes are XML files named in `FILENAME` (e.g., `skills/shipbuilding.cmare`, `caravanbuilding.cmare`, `clancastles.cmare`), not in this bucket. Gunsmith recipes (`gunsmith.txt`, 114 rows) include "pack of 30 small rounds" and "% pipe gun".

## Notable design ideas

- **Diminishing returns per place.** The room counter (`GatheringSkill.java:82-152`) gives 100/80/60/40/20% and then refuses, and it resets after 30 minutes. A place feels used but recovers, with no global economy needed.
- **Fire as a fabricated requirement.** `CommonSkill.getRequiredFire` (`:501-511`) returns a fake glowing "Burning" item when a recipe does not need fire, so every fire check follows one code path, and learning reuses the same check.
- **Mystery herbs.** Foraged herbs have no identity until Herbology identifies them, and the identity is drawn from a table (`Herbology.java:157-160`). That is a natural single-player discovery mechanic.
- **Crops as room effects.** The garden is a room-level timer that depends on sun and on compost (`Gardening.java:108-200`). It is a good model for a living-world plot object.
- **Prey as an encounter.** Hunting spawns a weighted creature scaled to the hunter, which flees or is chased, with an XP bonus (`Hunting.java:141-206`). The encounter is a small chase game, not a dice roll.
- **Learning costs XP.** Herbalism charges XP up front (`Herbalism.java:529-543`). Brewing is a real trade-off.
- **Passive hooks through the message system.** FlowerArranging adds flavour through a trailer message, and ImprovedTeaching changes a message value (`ImprovedTeaching.java:107-111`). Passives need no special code path.
- **Preservation as an effect.** Food preservation attaches `Prayer_Purify` (`FoodPreserving.java:99-110`), so rot is a removable status effect.

## Problems and cruft

1. **Shipwright crafting never delivers.** `GenWrightSkill.java:1427-1433` sets `buildingI = null` right after `beneficialAffect`. On the next mob tick, `GenWrightSkill.tick` (`:177-189`) sees `buildingI == null` with activity CRAFTING and calls `unInvoke()`. `unInvoke` only delivers when `buildingI != null` (`:376-379`), so nothing is produced. The player sees "start(s) building X" and then "is done building X" (`CommonSkill.java:335-337`). `CraftingSkill.doLearnRecipe` (`CraftingSkill.java:2371-2455`) keeps `buildingI`, and RETITLING, DOORING and DEMOLISH work because `tick` excludes them. Mending through the same path is also a no-op. This is by code reading; it was not run.
2. **Four dead FUR rows in `hunting.txt`.** Lines 15-18 have a double tab (`FUR\t\t*\t4\tWolf`). The tokenizer keeps empty fields (`Libraries/CoffeeUtensils.java:246-290`), so the domain is `""` and the frequency column holds `*`. `Hunting.java:275-282` never selects these rows. FUR cannot be hunted with this table.
3. **Zero yields are still announced.** When depletion gives 0, `Foraging.java:147-152` still prints "manage(s) to gather up <item>", and `GenGatheringSkill.java:531-541` prints "gather 0 ...", with nothing dropped.
4. **Broken message arguments.** `GenGatheringSkill.java:553-557` passes one argument to `MSG4`, which has two tokens (`@x1 @x2`). `CMStrings.java:1975-1981` deletes unmatched tokens, so the item name is silently lost. This only runs when the yield formula evaluates to 0 or less.
5. **ImprovedHerbalism does not refuse over-level brews.** `ImprovedHerbalism.java:93-97` prints "You can not brew a potion above level" but does not return, unlike the lower bound at `:87-91`.
6. **Gunsmithing refuses all guns.** `Gunsmithing.java:81-96` falls through to `return false` at `:95` for every metal `AmmunitionWeapon`, so `mayICraft` is true only for ammunition. This may be intended, but it is what the code does.
7. **Vetoed crafts lose materials.** In `GenCraftSkill`, materials are destroyed before the start message (`:853-860`). If `okMessage` vetoes a non-bundle craft, no item is made, nothing is refunded, and the method still returns true (`:1014-1028`).
8. **Recipe names are regular expressions.** `GenCraftSkill.java:552-553` and `GenWrightSkill.java:489-498` build a regex from recipe names (`%` becomes `.*`, other metacharacters are not escaped). A name with `(`, `+`, `[` or `?` would be misread or throw `PatternSyntaxException`. I checked four recipe files and found no such names, but did not check every data file.
9. **Fragile weight parity.** `FoodPrep.java:174-176` checks `F.phyStats().weight() % 2` after `basePhyStats().setWeight(...)` and before `recoverPhyStats()`. It works only because derived stats are not yet recomputed. The nourishment check (`:184`) uses the saved old value and is correct.
10. **Slice returns false on success.** `FoodPrep.java:192`. The effect on the caller is not verified.
11. **Brewing destroys the container.** `Herbalism.java:544-546` destroys the container. What happens to its ingredient contents is not verified.
12. **Herbalism charges XP even on failure.** The cost is taken before the proficiency result is used (`Herbalism.java:529-543`, `:550`). Failure only causes an explosion (`:248`).
13. **Missing data for Foraging.** `Foraging.java:221` loads `skills/herbs.txt`. That file is not in `resources/skills/` in this checkout, so the herb-identity branch is inert here (unverified whether it ships elsewhere).
14. **Duplicate recipe file.** `herbalism.txt` and `improvedherbalism.txt` are byte-identical (`cmp` confirmed).
15. **Copy-paste triggers.** `GatheringSkill.java:51` gives FLETCH and FLETCHING as base triggers. Every subclass here overrides them.
16. **Unused field.** `Herding.java:73` and `:250` set `failed`, and nothing reads it. A failed proficiency has no effect on herding.
17. **Dead branches in `setStat`.** `GenGatheringSkill.java:340-344`, `GenCraftSkill.java:348-352` and `GenWrightSkill.java:712-716` both `break` in each arm.
18. **Garden depends on the planter.** `Gardening.java:111-121` fails the crop at room tick 6 if the planter is no longer in the world. Possibly intended; unverified in play.
19. **Inconsistent duration level term.** `GenGatheringSkill.invoke` passes the mob's own base level as `itemLevel` (`GenGatheringSkill.java:771`), which makes the level term close to zero. `Foraging.java:210` passes 1, so high-level foragers get a large reduction. The two gatherers disagree.
20. **Unseeded randomness.** Lock key names use `Math.random()` (`GenCraftSkill.java:914`) rather than the seeded `CMLib.dice()`.

## Takeaways for a Python SUD

- **Recipes as data.** Load rows from TOML, YAML or the same TSV layout into a `Recipe` dataclass: `name, level, ticks, materials, value, product_class, misc, capacity, damage, spells, extra`. Keep the `extra` key-value column (`RACEREQUIREMENT`, and any attribute) as a dict. Match names exactly first, then by substring, and gate on level and race as `GenCraftSkill` does (`:800-810`).
- **Resource tables.** Store gatherable resources as lists of `(resource, domain, weight, creature)`. Pick with `random.choices(rows, weights=...)`. Keep the `*` wildcard for "any domain", and keep the data free of broken rows (see Problems 2).
- **Depletion with game time.** Use `dict[(place_id, resource)] -> (count, expires_at)` with `expires_at` in game time (turns or in-game hours), not wall-clock time. The Java uses 30 real minutes (`GatheringSkill.java:69`). Use the curve 1.0, 0.8, 0.6, 0.4, 0.2 and then refuse, or tune it.
- **Actions with ticks.** Model an action as `Action(kind, remaining, on_complete)`. In turn-based play `remaining` counts turns. Keep the abort rule from `CommonSkill.tick` (`:231-251`): combat, moving away or being unable to act cancels the action and prints "stop(s)".
- **Proficiency.** Use `chance = clamp(skill + 2*level_bonus, 5, 95) + level_bonus // 2`, with those numbers as tuning knobs (`StdAbility.java:1441-1480`).
- **Fire and other tools as predicates.** Model "needs fire" as a room predicate checked each tick (`GlassBlowing.java:119-124`), and let a learning path satisfy it with a stand-in, as `getRequiredFire` does, so there is one code path.
- **Veto then apply.** Only needed for passives that must intercept an event. A small event bus with `allow(event) -> bool` followed by `apply(event)` gives the same two-phase flow (`CMMsg` `okMessage` then `executeMsg`) without the class hierarchy.
- **Mystery identities.** Keep the Herbology idea: unidentified herbs get a hidden identity, which is revealed by study (`Herbology.java:157-175`). It gives single-player discovery without a quest system.
- **Plots as objects.** Gardening's room-level timer with sun and compost (`Gardening.java:108-200`) becomes a `Plot` object with a timer, a condition and a yield table, updated by the world clock.
- **Encounters.** Hunting's prey (`Hunting.java:141-206`) becomes a small chase: a weighted creature, a flee step each turn, and a loss condition.
- **Drop for one player.** Clan authority and title sales (`GenWrightSkill.java:310-327`, `:817-834`, `:440-445`), shared per-room depletion between players, and the `ITEMXML` file-or-XML storage (`GenGatheringSkill.java:391-405`) are multiplayer or builder-tool machinery. A single-player game needs none of them. Keep the recipe format, not the XML embedding.

## Coverage

- **Read in full:** `GatheringSkill.java` (513), `GenGatheringSkill.java` (784), `GenCraftSkill.java` (1031), `GenWrightSkill.java` (1444), `Herbalism.java` (561), `Foraging.java` (273), `Hunting.java` (389), `Herding.java` (286), `Herbology.java` (267), `FoodPreserving.java` (174), `FlowerArranging.java` (100), `ImprovedTeaching.java` (116), `ImprovedAlchemy.java` (115), `ImprovedHerbalism.java` (101), `Gunsmithing.java` (98), `Gardening.java` (485, header and triggers read through grep), `FoodPrep.java` (197, class body), `GaolFood.java` (118, class body), `Irrigation.java` (120, class body).
- **Read in part:** `JewelMaking.java` (814). Recipe format, tick, `unInvoke`, the encrust and mount flow, value and weight rules, and fire checks were read. `autoGenInvoke` was only searched by grep. `GlassBlowing.java` (654). Recipe format, tick, learn override, `unInvoke`, and value, duration and fire lines were read. `autoGenInvoke` was only searched by grep. `InstrumentMaking.java` (436). Recipe format, tick, `mayICraft`, `unInvoke`, race gating and instrument type were read. The rest of `autoGenInvoke` was only searched by grep.
- **Data files:** `hunting.txt`, `herbalism.txt`, `herbology.txt`, `improvedalchemy.txt`, `glassblowing.txt`, `jewelmaking.txt`, `instruments.txt`, `foodprep.txt`, `gaolfood.txt`, `irrigation.txt` and `gunsmith.txt` were sampled (head rows, counts, one `cmp`). None was read in full.
- **Outside the bucket, consulted for interactions:** `CommonSkill.java` (tick, `getDuration`, `getRequiredFire`, `unInvoke`, `dropAWinner`), `CraftingSkill.java` (`buildingI`, `doLearnRecipe`, `isANativeItem`), `StdAbility.java` (`proficiencyCheck`), `StdMOB.java` (`TYP_TEACH`), `Libraries/Dice.java`, `Libraries/CoffeeUtensils.java` (recipe tokenizer), `core/CMath.java` (`?` operator), `core/CMStrings.java` (`replaceVariables`). `Composting.java`, `BuildingSkill.java` and `Cooking.java` were not read.
- **Skipped:** no file in the bucket was skipped entirely.
