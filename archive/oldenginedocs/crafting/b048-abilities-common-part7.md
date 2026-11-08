# Common skills, part 7: trades, scholarship, scouting and macabre crafts (SmokeRings to Welding)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b048` (Abilities/Common [part 7/7: SmokeRings .. Welding]) · 22 files · 8865 lines of Java.

Citations are `Folder/File.java:line`, relative to `com/planet_ink/coffee_mud/`, unless they name another root. The full path of each file is in the table below. Other roots are written out in full.

## Summary

This slice of `Abilities/Common` holds 22 skills in three families. The first is recipe-driven crafting: weaving, staff and wand making, weaponsmithing, tailoring, wainwrighting, textiling, torturesmithing, taxidermy and welding (metal room construction). Most of these run one template. A recipe row from a data file names the item, the materials are consumed when the job starts, a proficiency roll decides whether the product is spoiled, and a Gen item is dropped when a timed effect ends. The second family is scholarship and writing: `Studying` (teacher-bound timed learning with per-class slot limits), `Transcribing`, `Titling` and `Surveying`, which turn knowledge and scouting results into book pages. The third is field and body skills: `Speculate`, `Trawling`, `Tanning`, `Unbinding`, `SnakeOilSelling` (placebo potions), `SmokeRings` (cosmetic) and `Tattooing`. These matter for a text game because nearly every rule is data-driven and time-based, which maps well onto a Python recipe table plus a tick loop.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/Common/Studying.java` | Timed learning from a teacher (NPC, player or dissertation book); per-class slot limits; learned skills stored as text on the effect | 984 |
| `Abilities/Common/Weaving.java` | Plant and cloth crafting: baskets, bags, quilts, ropes, hammocks, nets, whips; mend, refit, bundle, lock/key | 672 |
| `Abilities/Common/StaffMaking.java` | Wooden staves and staff crowns; fire required; hardness adjusts level, attack and damage | 616 |
| `Abilities/Common/Weaponsmithing.java` | Metal weapons; fire is tended during the job; specialization gate | 613 |
| `Abilities/Common/WandMaking.java` | Wands from wood plus wand cores; max charges; enchant type | 599 |
| `Abilities/Common/Tailoring.java` | Cloth armour, containers and rideables ("knit") | 588 |
| `Abilities/Common/Surveying.java` | Writes a survey of the room or area into a catalog book; expertise tiers | 513 |
| `Abilities/Common/Wainwrighting.java` | Carts, carriages and wagons; rider capacity | 492 |
| `Abilities/Common/Textiling.java` | Cloth bolts (GenResource) with ability-component material expressions | 471 |
| `Abilities/Common/Torturesmithing.java` | Restraint and punishment furniture recipes; `mayICraft` accepts only items flagged deadly or malicious | 469 |
| `Abilities/Common/Titling.java` | Gives a book a title and a catalog category | 354 |
| `Abilities/Common/Tattooing.java` | Tattoos a target's body location; costs HP; can injure a limb | 304 |
| `Abilities/Common/Taxidermy.java` | Stuffs a non-player corpse into a posed statue that keeps the race | 291 |
| `Abilities/Common/Transcribing.java` | Copies a book or recipe book page to another book | 286 |
| `Abilities/Common/SnakeOilSelling.java` | Brews placebo potions and stocks them on a merchant shelf | 276 |
| `Abilities/Common/SmokeRings.java` | Auto-skill: styles the smoke when a lit mouth-worn light is puffed | 239 |
| `Abilities/Common/Tanning.java` | Hides into leather; needs a lit fire | 235 |
| `Abilities/Common/Trawling.java` | Fishing from a ship's deck | 227 |
| `Abilities/Common/Unbinding.java` | Removes an effect flagged `FLAG_BINDING` from a mob or item | 204 |
| `Abilities/Common/Speculate.java` | Scouts the current room and adjacent open exits for resources | 173 |
| `Abilities/Common/SpellCraftingSkill.java` | Base for spell-to-item crafting (`ScrollScribing` extends it) | 156 |
| `Abilities/Common/Welding.java` | Metal variant of the building skill; recipes build rooms and exits | 103 |
| `Abilities/Common/CommonSkill.java` | Outside bucket. Base for non-crafting skills: duration, fire, messages, yields | 1016 |
| `Abilities/Common/CraftingSkill.java` | Outside bucket. Base for crafting: recipe loading, materials, weight, naming, components | 2459 |
| `Abilities/Common/EnhancedCraftingSkill.java` | Outside bucket. Adds enhancement types and `enhanceItem` | 913 |
| `Abilities/Common/BuildingSkill.java` | Outside bucket. Room and exit construction; `Welding` extends it | 2035 |
| `Abilities/Common/GatheringSkill.java` | Outside bucket. Yield helpers and room over-harvest counter | 513 |
| `Abilities/StdAbility.java` | Outside bucket. `proficiencyCheck`, `commonFaiL`, `beneficialAffect` | 3018 |
| `Abilities/Common/Merchant.java` | Outside bucket. Shop state (`shop` field) used by `SnakeOilSelling` | 974 |
| `CharClasses/Scholar.java` | Outside bucket. Grants `Studying` by class level | 538 |

## How it works

### 1. The recipe-driven crafting template

Eight skills (Weaving, StaffMaking, Weaponsmithing, WandMaking, Tailoring, Wainwrighting, Textiling, Torturesmithing) share this flow. Taxidermy uses a simpler variant of it. Welding inherits `BuildingSkill`'s own construction flow (section 9).

**Data model.** Each recipe is one tab-separated row from `resources/skills/<name>.txt`, loaded by `CraftingSkill.loadRecipes` (`Abilities/Common/CraftingSkill.java:204`) as a `List<List<String>>`. Field positions are named constants in each class. Weaving's are: `RCP_TICKS=2`, `RCP_WOOD=3` (materials), `RCP_VALUE=4`, `RCP_CLASSTYPE=5` (the Gen class to instantiate), `RCP_MISCTYPE=6`, `RCP_CAPACITY=7`, `RCP_ARMORDMG=8`, `RCP_CONTAINMASK=9`, `RCP_SPELL=10` (`Weaving.java:89-97`). Index 0 is the name and index 1 is the level. Column sets differ per skill. Weaponsmithing adds weapon class, weapon type, base damage, attack modifier, hands and range (`Weaponsmithing.java:82-88`). WandMaking adds max uses, an optional extra material with amount, and wand type (`WandMaking.java:77-83`).

A `%` in a recipe name is replaced by the material name and lower-cased (`CraftingSkill.java:258-262`, `determineFinalName`). So `% Basket` becomes "wicker basket" or similar depending on the material.

**Control flow** (representative: `Weaving.autoGenInvoke`, `Weaving.java:311-671`; the others follow it):

1. `checkStop` (`CommonSkill.java:828`) and `checkInfo` run first.
2. Sub-commands are dispatched: `list`, `learn`, `scan`, `mend`, `refit`, `bundle`. Anything else is a craft request.
3. The recipe is matched exactly, then loosely (`matchingRecipes`, `CraftingSkill.java:1409`). The level must be at or below `xlevel(mob)`, which is `mob level + 2 × expertise level` (`CommonSkill.java:645-648`).
4. Materials are found and components resolved (`fetchFoundResourceData`, `getAbilityComponents` at `CraftingSkill.java:2260`). They are destroyed right away (`Weaving.java:527-535`), not when the job ends.
5. The product is built from `CMClass.getItem(recipe class)` with material, name, description, weight, value and level (`Weaving.java:537-568`).
6. The quality roll happens here: `messedUp=!proficiencyCheck(mob,0,auto)` (`Weaving.java:638`). The same line appears in every skill of the family (StaffMaking 582, Weaponsmithing 579, WandMaking 565, Tailoring 554, Wainwrighting 458, Textiling 437, Torturesmithing 451, Taxidermy 245).
7. The job starts as a timed effect: `beneficialAffect(mob,mob,asLevel,duration)` (`Weaving.java:660`). Its `tick` (`Weaving.java:122-131`) ends the job early if `buildingI` has gone.
8. `unInvoke` finalizes (`Weaving.java:145-213`). If `aborted` is set, nothing is produced (`:153`). If `messedUp`, the item is dropped as a loser (`:172`). Otherwise it is dropped as a winner and the crafting achievement is bumped (`:196-197`). Learning deconstructs the item back into the recipe book (`:185`). Mending resets uses to 100 (`:179`), and refit clears height (`:191`).

**Proficiency formula** (`Abilities/StdAbility.java:1441-1485`):

```
pct  = proficiency()                       # 0..100
pct += PROF adjustments from stats
pct += 2 * xlevel                          # expertise
pct  = clamp(pct, 5, 95)                   # unless DIS955RULE
pct += xlevel // 2                         # added after the clamp
if adjustment >= 0: pct += adjustment
elif roll(1..100) > 100 + adjustment: fail # hard gate for negative adjustment
success = roll(1..100) < pct
```

**Duration formula** (`CommonSkill.java:346-362`):

```
ticks = base - round(base * (mobLevel - itemLevel) / LASTPLAYERLEVEL * 0.5)
ticks -= round(ticks * XTIME * 0.05)          # speed expertise, 5% per level
ticks = max(ticks, minDuration)
```

`LASTPLAYERLEVEL` defaults to 91 (`coffeemud.ini:515`). A tick is 4000 ms (`core/CMProps.java:681`). Higher-level crafters finish faster, and items above their level take longer. Minimums used by each skill: Weaving and the others pass 4 (`Weaving.java:544`), Tanning and Speculate 10 (`Tanning.java:214`, `Speculate.java:164`), Taxidermy 10 (`Taxidermy.java:251`), Tattooing 6 (`Tattooing.java:258`), Titling 3 (`Titling.java:345`), Trawling 45 (`Trawling.java:82-85`).

**Weight and material.** `getStandardWeight` (`CraftingSkill.java:192-203`) is `round(wood × multiplier)`, plus 1 if there is a second material, with a minimum of 1 when wood is above 0. The multiplier is 0.5 for non-bundled Weaving (`Weaving.java:221-225`) and Tailoring (`Tailoring.java:185-187`), and 1.0 for Textiling (`Textiling.java:163-165`). Material savings from expertise are `adjustWoodRequired` (`CraftingSkill.java:361-371`): `wood - round(0.05 × wood × XPCOST)`, minimum 1.

**Per-skill item formulas.** Each skill derives the final item from the recipe and the material's hardness, a value from `RawMaterial.CODES.HARDNESS` and `VALUE`:

| Skill | Hardness | Level | Combat or armour | Value |
|---|---|---|---|---|
| Weaponsmithing (`:545-572`) | `HARDNESS - 6` | recipe + 3 × hardness (`:550`) | attack = ATTACK + 5×h + (yield−1); damage = ARMORDMG + h (`:571-572`) | recipe/4 + wood × VALUE (`:549`) |
| StaffMaking (`:554-573`) | `HARDNESS - 3` | recipe + h, min 1 (`:555-557`) | attack = yield + 5×h − 1; damage = ARMORDMG + h (`:572-573`) | recipe value (`:553`) |
| WandMaking (`:545-559`) | `HARDNESS - 3` | recipe + h, min 1 (`:546-548`) | none; max charges from MAX_WAND_USES (`:552-556`) | recipe value (`:544`) |
| Tailoring (`:514-542`) | `HARDNESS - 1` | recipe + 3 × h (`:516`) | armour = ARMORDMG + (yield−1) + h (`:542`) | recipe value (`:515`) |
| Torturesmithing (`:389-394`) | `HARDNESS - 3` | recipe level (`:390`) | armour via `setWearLocation` with h (`:427`) | recipe + wood × VALUE (`:389`) |
| Weaving (`:565-600`) | none | recipe level (`:567`) | armour = ARMORDMG + (yield−1) (`:600`) | recipe value (`:566`) |

"Yield" here is `baseYield() + abilityCode()`. `baseYield()` is 1 (`CommonSkill.java:84-87`). `abilityCode()` returns `bonusYield` (`CommonSkill.java:201-209`).

**Fire.** `CraftingSkill.isFireRequired()` defaults to `true` (`CraftingSkill.java:187-190`). StaffMaking (`:428-434`) and WandMaking (`:421-427`) therefore need a lit fire for every job, wooden or metal. Metal jobs also need a fire in the room, and the message is "You'll need to build a fire first." (`StaffMaking.java:512`, `WandMaking.java:503`). Weaponsmithing overrides `isFireRequired` through its `fireRequired` field (`Weaponsmithing.java:104-110`), and `doLearnRecipe` switches it off so learning needs no fire (`:131-135`). Its `tick` spoils the job if the fire goes out while the crafter is still in the room where the job began (`:113-128`). Tanning does the same (`Tanning.java:94-101`). Weaving, Tailoring, Textiling, Wainwrighting, Torturesmithing and Taxidermy never call `getRequiredFire`.

**Stopping.** `checkStop` sets `aborted` and calls `unInvoke` (`CommonSkill.java:828-845`). Because `unInvoke` skips production when `aborted` is set, a stopped job loses its materials. Nothing refunds them.

**Family-specific twists.**
- *Weaving*: the material whitelist is the `pm` array (`Weaving.java:101-111`). A `LABEL` prefix is allowed for names up to 7 characters with no spaces (`:455-461`). Lock keys get a random name from `Math.random()` (`:618`). Rideables are restricted to ladders and furniture (`mayICraft`, `:245-253`).
- *Tailoring*: items named "DESIGNER" are blocked from the master path (`Tailoring.java:190-197`). A weapon with non-zero base damage is refused, and one with a non-zero attack modifier is accepted only if it is a native item (`:232-239`).
- *Weaponsmithing*: `masterCraftCheck` refuses level above 30 or names with "MASTER" (`Weaponsmithing.java:274-281`). The recipe matcher calls `canDo()` (`:477-478`), which requires a `Specialization_*` ability for the weapon class (`:229-272`). The comment at `:223-228` says `canDo` is not called, which is wrong (see Problems).
- *Wainwrighting*: every rideable is forced to basis `WAGON` (`Wainwrighting.java:421-424`). Rider count comes from the recipe.
- *Torturesmithing*: `mayICraft` accepts only items for which `isDeadlyOrMaliciousEffect` is true (`Torturesmithing.java:166-167`). Its recipes are restraint and punishment furniture and devices, levels 15 to 25 (see Content).
- *Taxidermy*: needs a `DeadBody` that is not a player corpse and has a mob name (`Taxidermy.java:212-214`). Cloth stuffing is `weight/5` (`:228`). The corpse is destroyed at the start (`:288`). Duration is `15 + wood/3`, capped at 65, before `getDuration` (`:248-251`).
- *Textiling*: material requirements are component expressions such as `(onground:consumed:1:SILK:)` and `&&` joins (see Content). The product is a GenResource bolt with subtype set (`Textiling.java:419-424`).

### 2. Studying (education)

`Studying` (`Studying.java:39`) is an `AbilityContainer` that stores what a student has learned as text on its own effect.

**Data model.** The text is `ABILITY_ID,proficiency;ABILITY_ID,proficiency;...` (see `Studying.java:405-411`). `distributeSkills` (`:433-468`) rebuilds in-memory copies with `setSavable(false)` and the stored proficiency. `skillList` is the live list.

**Per-class slots.** The `perLevelLimits` enum (`:69-112`) gives each ability class a slot allowance. Its constructor takes (num, perLevels, aboveLevel, type1, type2):

| Row | Ability codes | Min level | Slots |
|---|---|---|---|
| COMMON | COMMON_SKILL, LANGUAGE | 1 | 1 + floor((L−1)/6) |
| SKILL | SKILL, THIEF_SKILL | 2 | 1 + floor((L−2)/6) |
| SONG | SONG | 3 | 1 + floor((L−3)/6) |
| SPELL | SPELL | 4 | 1 + floor((L−4)/6) |
| CHANT | CHANT | 5 | 1 + floor((L−5)/6) |
| PRAYER | PRAYER | 6 | 1 + floor((L−6)/6) |

The formula is `numAllowed = num + num × round(floor((L − above)/perLevels))`, and it returns 0 below `aboveLevel` (`Studying.java:106-111`). `L` is the qualifying class level from `CMLib.ableMapper().qualifyingClassLevel` (`:546`, `:722`).

**Teaching time** (`Studying.java:782-788`):

```
teachMs = teacherQualLevel*60000 - 10000*(teacherClassLevel - teacherQualLevel) - 15000*xlevel
teachTicks = teachMs / TICK_MS
duration = teachTicks - round(teachTicks * XTIME * 0.05)
```

The comment at `:790-794` says the minimum is one minute. The code clamps to one tick (`:833-835`).

**Outcome.** `successfullyTaught` is a proficiency check (`:795`). When the lesson ends, the student's new proficiency is the teacher's proficiency plus 5 per LEVEL expertise (`:408`). It is written to the text and the skill is distributed (`:409-414`). An interrupted lesson, one where the teacher leaves, enters combat, or the student moves, records an abort (`:326-343`).

**Teachers.** NPC and item teachers run at once (`:843`). A player teacher must confirm with a Y/N prompt. Items must be dissertation books, which is an ID test on "issertation" (`:667`), and their spell list is used (`:672-673`). The item teacher becomes a factory MOB with proficiency 100 (`:377-381`) and is destroyed afterwards (`:424-425`).

**Messages.** "You've been taught: " (`:511`), "You may learn " (`:533`), "You may not study any more @x1 at this time." (`:766`), "You aren't qualified to be taught @x1." (`:727`), "<S-NAME> start(s) learning @x1 from <T-NAME>." (`:820`), "<S-NAME> stop(s) learning @x1 from <T-NAME>." (`:386`), "If @x1 agrees to teach you, you will begin studying together." (`:847`).

**Forgetting and practising.** `okMessage` blocks practising a taught skill ("You don't know how to do that in practice.", `:289-296`). Casting Scribe, EnchantWand, MagicItem, StoreSpell, WardArea, or writing a dissertation of a skill removes the record (`:198-211`, `:303-310`).

**Who has it.** The `Scholar` class maps `Studying` at level 1 (`CharClasses/Scholar.java:142`). The honorary degree skills map it at levels 12 to 18 (`Scholar.java:180-204`).

### 3. Transcribing, Titling and Surveying (knowledge as books)

**Book rules.** A target must be paper, leather, silk or hide (`Titling.java:290-293`, `Transcribing.java:182-185`, `Surveying.java:356-360`), readable (`Titling.java:298`), and generic (`Titling.java:312`, `Transcribing.java:200`). A book with a "Copyright" effect cannot be retitled (`Titling.java:323-327`). Books already named by `BookNaming` are refused (`:318-322`). Brand and ownership checks are commented out in all three files (`Titling.java:249-261`, `:304-311`; `Transcribing.java:158-175`).

**Titling** (`Titling.java:114-142`). A title is appended to the item name and display text as ` entitled \`TITLE\``. The title may not exceed 60 characters (`:329`). The last word can name a catalog category from the `CATALOGS` enum (`:71-99`). The category is written as ` This is a catalog of type \`X\`.` in the description (`:129-142`). Writing is filtered through `CMLib.coffeeFilter().secondaryUserInputFilter` (`:338`). The result is cleared to empty if either proficiency check fails (`:343-344`).

**Catalog typing** (`Titling.java:157-201`). Rooms and areas become `Survey` (`:159-162`). An item whose secret identity contains a statue race becomes `Races` (`:164-166`). Armour becomes `Armor`, weapons `Weapons`, rideables `Furniture`, paper items `Books`, and so on.

**Transcribing** (`Transcribing.java:85-144`). The skill copies by simulating a read. It fires a `TYP_READ` message at the source (`:102-103`), collects the `TYP_WASREAD` text from the trailers (`:105-115`), and writes it to the target with `TYP_WRITE` (`:129-134`). For a single-page non-book target it strips the `::Subject::` header (`:121-126`). Recipe books copy their common skill id (`:116-119`). Duration is `getDuration(30 - xlevel/2, mob, 1, 1)` (`:263`). If that comes out as 1 the copy completes at once (`:271-280`).

**Surveying** (`Surveying.java:84-352`). The survey text is built on `unInvoke`, so an aborted job writes nothing.
- *Room* (`:197-308`): name, area and room number always. Description at expertise above 1 (`:209-210`). Exits at above 0, with exit room numbers (`:225-226`), and a trap marker at above 6 (`:227-232`). Items above 2 (`:236-255`), resource types above 3 (`:256-270`), inhabitants above 4 (`:271-294`), and the room trap above 5 (`:295-304`).
- *Area* (`:101-195`): percent visited above 0 (`:136-137`), median level and player level above 1 (`:138-143`), level range above 2 (`:144-148`), median alignment above 3 (`:149-154`), alignment range above 4 (`:155-165`), climate above 5 (`:166-184`), and room-type counts above 6 (`:185-192`). Only rooms the mob has visited are counted (`:110-116`).

The write is a `MSG_WRITE` of `::subject::message` (`:340-343`). If the book already has a page for that subject, the page is rewritten with `EDIT n` (`:327-330`). The job needs `Skill_Write` (`:488-493`), and both `Surveying` and `Skill_Write` must succeed (`:501-503`). The base duration is 15 ticks (`:504`).

### 4. Speculate, Trawling and Tanning (field skills)

**Speculate** (`Speculate.java:73-172`). A proficiency roll at start sets `success` (`:162`). The job runs `getDuration(45, mob, 1, 10)` (`:164`). At the sixth tick a failed attempt ends it with "Your speculate attempt failed." (`:73-90`, `:83`). On success, `unInvoke` probes the current room, then each open exit (`:115-141`). `getRoomResource` (`:92-104`) sends a `MSG_THINK` through `room.okMessage` and `room.send`, and reads back the `targetMessage`. Room listeners can therefore change what the probe reveals. The reports are "You think this spot would be good for @x1." (`:125`) and "There looks like @x1 @x2." (`:138`), with direction names taken from `CMLib.directions()`.

**Trawling** (`Trawling.java:94-226`). The mob must be on a boardable area's outdoor deck (`:174-182`), otherwise "You need to be on the deck of a ship to trawl." (`:180`). The fish are `RawMaterial.CODES.FISHES()` matched against the room's resource or its choices (`:183-199`). A catch needs three things: a proficiency roll, `checkIfAnyYield(room)`, and a non-zero fish code (`:205-208`). At the sixth tick the mob is told "You feel your nets filling up!" or "Nothing is coming up around here." and the job ends if nothing is there (`:99-108`). The yield is `1d30 × (baseYield + abilityCode)`, adjusted for room over-harvest (`:124`, `GatheringSkill.java:111-130`). Base duration is `getDuration(135, mob, level, 45)` (`:82-85`, `:214`).

**Tanning** (`Tanning.java:150-234`). Needs a lit fire (`:205-208`) and a RESOURCE_HIDE item (`:163-169`). Enchanted items are refused (`:171-175`). A land-title permission check applies (`:189-194`). Every identical hide in the room is counted (`:177-187`), and each adds `weight/2` to the duration (`:227`). The yield is the total hide weight times `baseYield + abilityCode` (`:121`, `:230`). Each unit becomes one leather item (`:133-140`). Failing is "You've messed up tanning @x1!" (`:118`).

### 5. Unbinding (undoing a binding)

`Unbinding` (`Unbinding.java:157-203`) can target a mob or an item (`:70-74`). It picks the first affect flagged `FLAG_BINDING` (`:174-180`). The flag is set by ties, grapples, webs and similar skills. Examples are `Skill_TieDown`, `Skill_HandCuff`, `Thief_HogTie`, `Thief_Bind`, `Spell_Web`, `Chant_Tangle` and `Power_WebSpinning`, found by grep.

- Duration = `lowestQualifyingLevel(binding) - (qualifyingLevel(mob) + 2 × xlevel)`, with a minimum of 4 (`:187-189`).
- Difficulty is 0 for your own binding and `-abilityCode()` otherwise (`:197`). The proficiency roll at start decides whether `found` stays set (`:198`).
- At tick 3 the first flagged affect becomes `removing` (`:85-96`). Each later tick checks that the target is still in the room, is visible, and that the unbinder is free (`:105-127`). It also checks that the binding is still attached (`:123-127`).
- `unInvoke` removes the binding and reports success or failure (`:141-150`).

### 6. SnakeOilSelling (placebo potions)

`SnakeOilSelling` extends `BlackMarketeering` (`SnakeOilSelling.java:37`), which extends `Merchant` (`BlackMarketeering.java:37`). `OPEN` (`:115-246`):

1. Consumes one "snake blood" item (`:121-126`, `:241`).
2. Requires the Alchemy and Herbalism `ItemCraftor` skills (`:128-133`).
3. Sets the number of potion types to `1 + adjustedLevel // 10`, capped (`:134-141`), and their level bands to 1, 4, 7 and so on (`:142-144`).
4. Creates up to `MAX_POTIONS = 1 + proficiency()` potions (`:112`, `:150-233`). Herbal potions are allowed at expertise 6 and above (`:154-155`, `:188-189`), prayers at 3 and above (`:185-186`), and spells always (`:182-183`). Malicious spells are excluded (`:190`).
5. Each potion is priced at `max(1, 10% of value)` (`:176`). The potion gets a fake `ExtAbility` named after the real spell with `_Fake` appended (`:203-205`). Its callback tells the drinker one of 24 vague sensations (`:218`). It may start a fake tick-down of 5 to 10 ticks (`:219-220`). It does not cast the spell.
6. The potion goes on the merchant shelf (`:224-231`). `Merchant.shop` is initialized at field declaration (`Merchant.java:105`), and `text()` saves it as XML (`:140`).

`CLOSE` (`:248-271`) destroys every shelf product and removes the effect.

### 7. Tattooing, Taxidermy and Torturesmithing (body and macabre crafts)

These three are documented plainly and without detail. They use the same consent and injury machinery.

**Tattooing** (`Tattooing.java:142-303`). A target must consent: be self, a group member, bound, held or asleep (`:198-205`). The target must be at full HP (`:217-221`), and the location must be free and not covered (`:207-216`). Valid locations are arms, legs, hands, head, feet, left and right wrist, neck, back and torso (`:159-168`). On completion the tattoo is saved as `LOCATION:A tattoo of <text>` (`:253`, `:100`). Each tattoo costs HP: two piercing damage events of `maxHP/8` each, only when current HP exceeds twice that (`:266-271`). On completion an Injury effect damages one random body part at that location (`:102-113`, `:278-291`). The target's HP is pinned during the job (`:135-136`). The job aborts if the target leaves or can no longer be seen (`:126-133`). Removal is by the word "REMOVE" (`:236-249`). It finds tattoos whose names begin "A TATTOO OF" or "A SCARRED" (`:231-233`). `FLAG_TORTURING` is set on the skill (`:65-69`).

**Taxidermy** (`Taxidermy.java:170-290`). Takes a non-player corpse and cloth stuffing, and produces a GenItem statue, "the stuffed body of NAME" (`:258`). Poses come from `taxidermy.txt`, one random line from the block (`:191-200`, `:129-167`). Pose tokens such as `<S-NAME>` and `<S-HIS-HER>` are replaced (`:264-267`). The race is written into the secret identity as "This statue was once RACE." (`:272-276`). `Titling` reads that to select the Races catalog (`Titling.java:165`).

**Torturesmithing** (`Torturesmithing.java:159-468`). Its `mayICraft` accepts only items flagged deadly or malicious (`:166-167`), and its recipes are restraint and punishment furniture and devices. The recipe file is short: 13 rows at levels 15 to 25 (see Content). The write-up stays at the level of "furniture and restraint devices", with no more detail.

### 8. SmokeRings

`SmokeRings` (`SmokeRings.java:35`) is an always-on auto-skill (`:59-69`). Its `executeMsg` (`:156-175`) fires when a lit light worn at the mouth is puffed (`:159-168`). A proficiency roll at `10 × xlevel` (`:168`) adds a trailer message with the chosen smoke text (`:172`). A rare success gives a proficiency bump (`:170-171`). The invoke sets the style: random, stop, or one of the ten `smokerings.txt` rows (`:184-238`). "stop" disables the effect (`:164`).

### 9. Welding (metal construction)

`Welding` is 103 lines. It is a `BuildingSkill` (`Welding.java:38`) with trigger "WELDING" (`:54`), resource "METAL|MITHRIL" (`:62-65`), recipe file `welding.txt` (`:68-72`), and default material "metal" in place of `BuildingSkill`'s default "rock" (`:91-101`; `BuildingSkill.java:233-236`). The recipe columns are defined in `BuildingSkill.getRecipeFormat` (`BuildingSkill.java:165-171`): name, level, ticks, materials, optional material, building flags, building code, room or exit class, grid or exit names, spells, noun, builder mask and description. Welding therefore builds rooms, exits, walls and roofs. It does not make metal items. Builder masks are zapper masks, and the secret door row uses `-BASECLASS +Thief` (see Content).

### 10. SpellCraftingSkill

`SpellCraftingSkill` (`SpellCraftingSkill.java:37`) is the base for spell-to-item crafting, which `ScrollScribing` extends (`Abilities/Common/ScrollScribing.java:39`). It matches a spell name in three passes: exact, then the `english().containsString` test, then a substring test (`:72-107`). With no name it picks a random recipe (`:58-70`). The level comes from the recipe row or from `lowestQualifyingLevel` (`:126-155`).

## Content worth mining

Recipe sizes and levels were measured with `awk` on the resource files. Only the four small files were read in full.

| Data file (`resources/skills/`) | Rows | Level range | Notes |
|---|---|---|---|
| `weaponsmith.txt` | 135 | 0 to 30 | 117 GenWeapon, 17 GenSpear, 1 GenBoffWeapon. Classes include DAGGER, BLUNT, HAMMER, POLEARM, SWORD, AXE, FLAILED, STAFF, THROWN. E.g. butter knife L0, mallet L1 (HAMMER), spear L1 (POLEARM), hand ax L2 (AXE) |
| `staffmaking.txt` | 105 | 1 to 90 | 68 staves in material tiers (copper-shod L2, brass-shod L13, steel-shod L48, diamond-tipped L79). 37 staff crowns (L5 to L65) |
| `wandmaking.txt` | 84 | 1 to 90 | 47 wands and 37 wand cores (bat wand core L15, dire wolf L33, troll L51, faerie dragon L69). Cores set property slots such as Con +1 or Mana +450 |
| `tailor.txt` | 121 | 0 to 30 | 116 armour pieces, 3 rideables, 2 boff weapons. E.g. Cushion L5, Poncho L11, Skullcap L25, Belt L7 (capacity 25) |
| `wainwright.txt` | 55 | 1 to 88 | 49 rideables and 6 containers. Travois L1 (capacity 200), small carriage L14 (500, 4 riders), medium carriage L22 (1000, 6), large wagon L50 (7500), huge unsprung cart L64 (30000), siege tower L78 (16 riders) |
| `textiling.txt` | 17 bolts | 1 to 30 | Full read. Wool L1, cotton L2, silk L10 (value 400), chiffon L16, arcanin L24 (adds savemagic+2), spidersteel L30. Materials are expressions such as `(onground:consumed:1:SILK:)` and `(onground:consumed:1:COTTON(SATIN):)&&(onground:consumed:1:SILK(SILK):)` |
| `weaving.txt` | 25 | 1 to 30 | Full read. Baskets, bags, sacks (capacity 10 to 200), quilts with resistance properties, hair ribbons, rope (ladder), hammock (bed), whip, net (thrown), snood, belt, winter cap, sweater |
| `welding.txt` | 21 | 1 | Full read. Construction recipes: Wall, Roof, Low Roof, Wide Roof, Small Maze, Maze, Archway, Pool, Deep Pool, Portcullis, Upstairs, Hole Floor, Window, Crawlway, Secret Door, Shallow Water, and so on. Builder masks such as `-BASECLASS +Thief` |
| `smokerings.txt` | 10 | 1 | Full read. Styles: perfect, swirling string, huge, tiny train, tiny couple, round, big three, enormous, tiny swirl, galley |
| `taxidermy.txt` | 12 poses | n/a | Pose blocks with a name line and description lines. Mood labels such as Scary, Happy, Sneaky, Holy, Silly, Confused, Proud |
| `torturesmith.txt` | 13 | 15 to 25 | Furniture and restraint devices: whipping post, torture bed, stockade, gallows, torture rack, brank helmet, and others. Items are armour, chairs, beds, tables, a tent |

**Titling catalog categories** (`Titling.java:71-99`): Armor, Jewelry, Weapons, Furniture, Books, Potions, Pills, Wands, Foods, Drinks, Alcohol, Races, Survey, Misc, None.

**Tattoo body locations** (`Tattooing.java:159-168`): arms, legs, hands, head, feet, left wrist, right wrist, neck, back, torso.

**Snake oil sensations** (`SnakeOilSelling.java:67-93`): 24 vague one-liners, such as a faint warmth, a brief shiver, a feeling of quiet calm, and something you cannot name.

**Speculate and Surveying output** (`Speculate.java:125,138`, `Surveying.java:206-304`): short labelled fields that can be copied as a template for a "field report" item.

## Notable design ideas

- **One recipe template, many skills.** The craft pipeline (lookup, consume, roll, timed job, finalize) is a single pattern. Each skill only overrides the recipe columns and the item derivation. That is the core reason so many skills fit into under 700 lines each.
- **Expertise is four separate dials.** `XLEVEL` (range and bonus), `XTIME` (speed, 5% per level), `XPCOST` (materials saved, 5% per level), and `X1` (diversify ranks for Snake Oil). The effects are readable and separately balanced.
- **Consume at start, produce at end, and stopping loses the materials.** This makes the job a real commitment, which is good for a single-player game as a risk, provided the rule is explicit.
- **Knowledge as physical objects.** Titling, Surveying and Transcribing write into books. A scouted area becomes a book page, and a copy is a second book. Study is tied to a teacher who is present. Knowledge can be carried, stolen or lost.
- **Scouting by message.** `Speculate` asks a room "what resources do you have?" through the normal message flow, so rooms and listeners can modify the answer. This is a clean way to add hidden or altered information.
- **Placebo products.** `SnakeOilSelling` makes items that look real (the real spell name), act on the drinker only through flavour text, and can still sell. A small, useful model for a black-market economy.
- **Flags, not hard-coded skill links.** `FLAG_BINDING` lets `Unbinding` remove bindings made by many unrelated skills. `isDeadlyOrMaliciousEffect` gates which items Torturesmithing can make.
- **Surveys with detail tiers.** Survey detail grows with expertise, and an area only lists rooms the surveyor has visited. This is good progressive information design.

## Problems and cruft

1. **`Studying.delAbility` compares a value with itself and removes while iterating.** `Studying.java:917-921` tests `A.ID()` against `A.ID()`, not against `to.ID()`, and calls `remove` inside a for-each. The first entry is removed whatever its ID. With three or more entries the loop then throws a `ConcurrentModificationException`. With two entries only the first is removed. Read from the code; runtime effect unverified.
2. **`Studying` minimum duration.** The comment at `:790-794` says one minute, but the code clamps to one tick (`:833-835`).
3. **`Studying.addAbility` throws** `UnsupportedOperationException` (`:893-896`). Generic `AbilityContainer` code will fail on it.
4. **`WandMaking` error message repeats the verb.** `WandMaking.java:459` reads `"You don't know how to @x1 a '@x1'..."` with args (verb, recipe name, keyword). The player's typed name never appears, and `@x2` is needed. `L()` substitutes 1-based indices (`Libraries/StdLibrary.java:88-91`, `core/CMStrings.java:1940-1960`).
5. **`Weaponsmithing.canDo` comment is wrong.** The comment at `Weaponsmithing.java:223-228` says the method is not called. It is called at `:478`.
6. **`Titling.getCatalogEntryType` has a dead branch.** `Titling.java:168-171` tests `instanceof Armor` twice. The Jewelry result is unreachable.
7. **Surveying's catalog mismatch does not stop the job.** `Surveying.java:386-393` prints "is not a proper catalog" but never returns false. `isPossibleCatalog` returns true at `:394`, so the survey is written anyway.
8. **Surveying's exit trap check uses the wrong room.** `Surveying.java:227-232` calls `fetchMyTrap(room)` for every exit. The current room's trap is therefore marked on every exit line.
9. **Surveying dereferences `playerStats()` unguarded.** `Surveying.java:137` calls `mob.playerStats().percentVisited`. The loop at `:114` guards against null, but this line does not.
10. **Tattooing's "REMOVE" test is backwards.** `Tattooing.java:236`, `:260` and `:273` use `"REMOVE".startsWith(message)`. Any prefix of "remove", including the single letter "r", triggers removal. The check should be `message.equalsIgnoreCase("REMOVE")`.
11. **`Transcribing` builds an unused factory MOB.** `Transcribing.java:99` creates `factM`, and the only use is `destroy()` at `:138`.
12. **`Taxidermy` has a dead branch and a broken helper.** `Taxidermy.java:246-247` tests `buildingI` after it was cleared at `:241`. `getStatueRace` (`:110-126`) returns the whole prefix plus the race, not just the race. `Titling` only checks that the result is non-empty.
13. **`Taxidermy` destroys the corpse before the output is confirmed.** `Taxidermy.java:282-288` destroys the corpse at `:288` whether or not `okMessage` at `:282` passed. If it fails, no statue is made. Inferred from the code.
14. **`Textiling` has identical branches and wrong verbs.** `Textiling.java:190-192` returns `isANativeItem` on both paths. Its messages say "weaving" (`:138`, `:342`).
15. **`Textiling` wrap can throw.** `Textiling.java:290-291` calls `substring(0, x)` where `x` comes from `lastIndexOf(' ', 45)`. If there is no space in the first 45 columns, `x` is -1. Latent.
16. **`SmokeRings` list header is truncated with zero styles.** `SmokeRings.java:191` and `:201` cut two characters from a header ending in "or ", giving "...STOP, o.".
17. **`Unbinding` can call `unInvoke` up to four times in one tick.** `Unbinding.java:110-127` does not return after it sets `aborted`.
18. **`Unbinding` fails silently.** A failed proficiency check sets `found` to null (`:198`). The job then ends at tick 3 (`:85-103`) with no failure message in this file. Whether the base class adds one is unverified.
19. **`SnakeOilSelling` potion count scales with skill percent.** `MAX_POTIONS = 1 + proficiency()` (`:112`). Proficiency is a 0 to 100 percent, so one OPEN can stock up to 101 potions. Probably unintended.
20. **`SnakeOilSelling` has no-op code.** `:174-175` calls `getSpells()` with the comment "Populate value before destroying". `:176` and `:225` test `price>0`, which is always true.
21. **`Tanning` makes one object per unit of weight.** `Tanning.java:133-140` loops `msg.value()` times. A large stack of hides creates a large number of objects.
22. **Hard-coded tick timings.** `Speculate.java:79` and `Trawling.java:99` use `tickUp == 6`, whatever the job's duration.
23. **`Trawling.supportedResourceString` is "FLESH".** `Trawling.java:65-69`. Its purpose is unclear for a fishing skill. Unverified.
24. **Completion messages go only to the actor.** `Tattooing.java:96` and `:99` use `commonEmote`. For players `commonEmote` sends a private `tell` (`CommonSkill.java:379-385`), so bystanders see nothing. `Weaponsmithing.java:416` does the same with its list.
25. **Materials lost on stop.** Covered under the template. It is a design choice, but it is not refunded or warned about.

**Multiplayer assumptions.** Consent is built around other players. `Studying` asks a player teacher to confirm (`Studying.java:843-885`). `Tattooing` requires group membership (`:198-205`). Land permissions apply to Tanning (`Tanning.java:189-194`). Ships are boardable areas (`Trawling.java:174-176`). Brand and ownership checks have been commented out in `Titling` and `Transcribing`, which is acceptable for one player.

## Takeaways for a Python SUD

1. **Make recipes data, not code.** Load a CSV or TOML table per craft into a dataclass: `Recipe(name, level, ticks, materials, value, kind, props, capacity, damage, spells)`. Keep the `%` material placeholder. Keep the material expression language small (`WOOL:2`, `IRON:1`, `ALL_OF(...)`). Let each craft class supply only its item derivation, using a small hardness table per craft.
2. **Model a craft as a timed action with explicit outcomes.** The state is `start (consume) -> tick x N -> finish`. Track two flags separately: `spoiled` (the roll at start) and `cancelled` (the player stopped). For a single-player game, refund a cancelled job at a penalty rather than losing it outright.
3. **Port the proficiency roll and duration formulas.** The math is small. Here is a Python sketch (values from `StdAbility.java:1441-1485`, `CommonSkill.java:346-362`):

   ```python
   def prof_check(prof, xlevel, adjustment=0, rng=random):
       chance = min(95, max(5, prof + 2 * xlevel)) + xlevel // 2
       if adjustment >= 0:
           chance += adjustment
       elif rng.randint(1, 100) > 100 + adjustment:
           return False
       return rng.randint(1, 100) < chance

   def craft_ticks(base, player_level, item_level, xtime, last_level=91, minimum=4):
       t = base - round(base * (player_level - item_level) / last_level * 0.5)
       t -= round(t * xtime * 0.05)
       return max(t, minimum)
   ```

4. **Use four expertise dials.** Speed, material saving, bonus and range each get their own named effect. Store them as a dict of perks on the character. That keeps balance tunable.
5. **Use books as the record of knowledge.** Give a survey, a field report or a copied recipe a physical book item. Let study require a teacher who is present in the room and interrupt when the teacher leaves. The slot formula from `Studying.java:106-111` ports as is and gives a natural cap on how many spells or skills a character can take on.
6. **Use the message probe for hidden information.** Speculate's pattern (ask the room, let listeners alter the answer) is easy to rebuild as a list of observers that can change a reported value before the player sees it.
7. **Consider placebo potions for an economy.** A `Potion` with a `real_name` for display, a `flavour` list and no effect is a cheap way to add a dishonest merchant. It needs no effect engine.
8. **Use a flag for bindings.** A `binding` tag on an effect, plus a generic "remove binding" action with range, sight and free-hands checks, reproduces Unbinding cleanly. The fixes in Problems 17 and 18 belong in the rewrite.
9. **Leave out most of the body-modification and torture content.** Tattooing, Taxidermy and Torturesmithing are violent or macabre. For a single-player text game, keep at most the mechanic that a corpse can be preserved as a trophy statue. Skip the rest.

## Coverage

- **Read in full** (all 22 files, from the class declaration to the closing brace; licence headers and imports skipped): Studying, Weaving, StaffMaking, Weaponsmithing, WandMaking, Tailoring, Surveying, Wainwrighting, Textiling, Torturesmithing, Titling, Tattooing, Taxidermy, Transcribing, SnakeOilSelling, Tanning, Trawling, Unbinding, Speculate, SmokeRings, SpellCraftingSkill, Welding.
- **Read in part** (outside this bucket): `CommonSkill.java` (80-90, 199-215, 346-400, 499-530, 643-655, 826-860), `StdAbility.java` (791-870, 1441-1486, 1810 header only), `CraftingSkill.java` (185-203, 258-275, 347-375, 426-440), `BuildingSkill.java` (36, 67-82, 165-185, 233-236, 1960-1975), `GatheringSkill.java` (82-130), `Merchant.java` (105, 124-140), `BlackMarketeering.java` (37-75), `CoffeeUtensils.java` (425-436), `CMStrings.java` (1940-1960, 2002-2015), `StdLibrary.java` (88-91), `coffeemud.ini` (515).
- **Located by grep only**: `EnhancedCraftingSkill.java`, `BookNaming.java`, `Alchemy.java`, `Herbalism.java`, `ScrollScribing.java`, `Skill_Write.java`, `Scholar.java`.
- **Data files**: `textiling.txt`, `weaving.txt`, `smokerings.txt` and `welding.txt` read in full. `staffmaking.txt`, `weaponsmith.txt`, `wandmaking.txt`, `tailor.txt`, `wainwright.txt`, `torturesmith.txt` and `taxidermy.txt` measured with `awk` for counts, levels and sample rows, not read line by line. Master and legendary variants are outside this bucket and were not read.
