# Common skills, part 6: crafting, publishing, salvage, animal and perception skills

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b047` (Abilities/Common [part 6/7: PaintingSkill .. Smelting]) · 26 files · 8951 lines of Java.

## Summary

This bucket is the sixth of seven slices of `Abilities/Common`, from `PaintingSkill` to `Smelting`. It holds the working crafts (ship building, pottery, sculpting, scrimshaw, smelting, paper making, scroll scribing, rod, shard and siege work), the economy and salvage skills (publishing, self-investing, salvaging, scrapping), the animal and NPC skills (shearing, shepherding, scalping, slave trading), and the perception and knowledge skills (searching, plant lore, recollecting, playing an instrument). Almost every craft runs on one engine: `CraftingSkill` and `EnhancedCraftingSkill` turn a recipe row into a timed job that consumes materials lying on the floor when it starts, runs as a countdown effect on the crafter, and produces or ruins an item when the countdown ends. Expertise words such as "Light" or "Lethal" change material use, time, value and stats through a stage table. For a single-player text game the most useful parts are that job-as-timer pattern, the recipe-as-data tables (63 ship prototypes and about 735 recipe rows across nine files), and the expertise modifier grammar. Several real defects are listed under Problems and cruft.

## Key files and types

Citations use `file.java:line`. A bare file name is in `com/planet_ink/coffee_mud/Abilities/Common/`; paths are otherwise relative to the CoffeeMud folder.

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/Common/Shipwright.java` | Ship building, doors, titles, descriptions, demolition and mending of boardable ships. `CraftingSkill` subclass | 1035 |
| `com/planet_ink/coffee_mud/Abilities/Common/Publishing.java` | Copyright a written work and place copies in local book shops; royalties paid on purchases | 692 |
| `com/planet_ink/coffee_mud/Abilities/Common/ScrimShaw.java` | Bone and ivory items: weapons, armour, furniture, containers, statues. `EnhancedCraftingSkill` | 661 |
| `com/planet_ink/coffee_mud/Abilities/Common/Sculpting.java` | Stone items: furniture, dice, containers, statues | 615 |
| `com/planet_ink/coffee_mud/Abilities/Common/Pottery.java` | Clay and china items; the job needs a fire | 597 |
| `com/planet_ink/coffee_mud/Abilities/Common/ScrollScribing.java` | Writing spells onto blank scrolls. `SpellCraftingSkill` subclass | 591 |
| `com/planet_ink/coffee_mud/Abilities/Common/Smelting.java` | Alloys from metal inputs; the product is a raw material, not a recipe item | 514 |
| `com/planet_ink/coffee_mud/Abilities/Common/PaperMaking.java` | Paper, books, scrolls and recipe books from wood, hemp, silk or cloth | 483 |
| `com/planet_ink/coffee_mud/Abilities/Common/PaintingSkill.java` | Base of the dye and lacquer skills; applies colour words to names and descriptions | 398 |
| `com/planet_ink/coffee_mud/Abilities/Common/PlayInstrument.java` | Playing a carried or ridden instrument for a fixed time; proficiency kept per instrument type | 341 |
| `com/planet_ink/coffee_mud/Abilities/Common/SelfInvesting.java` | `StdSkill`; turns a bank of sales receipts into experience, once per game day | 326 |
| `com/planet_ink/coffee_mud/Abilities/Common/Salvaging.java` | Breaks a sunk sailing ship into raw materials | 299 |
| `com/planet_ink/coffee_mud/Abilities/Common/Scrapping.java` | Breaks identical items in a room into raw material by weight | 278 |
| `com/planet_ink/coffee_mud/Abilities/Common/Shepherding.java` | Moves a herd of smaller same-race animals one room | 259 |
| `com/planet_ink/coffee_mud/Abilities/Common/Recollecting.java` | Searches the text of readable items in reach for a phrase | 244 |
| `com/planet_ink/coffee_mud/Abilities/Common/Shearing.java` | Takes wool from a creature whose race has wool | 214 |
| `com/planet_ink/coffee_mud/Abilities/Common/Scalp.java` | Takes a trophy from a corpse that has a head | 204 |
| `com/planet_ink/coffee_mud/Abilities/Common/SlaveTrading.java` | Sells an owned NPC "slave" to a shopkeeper, or transfers its ownership | 184 |
| `com/planet_ink/coffee_mud/Abilities/Common/PlantLore.java` | Reports nearby plant and wood resources, by direction | 182 |
| `com/planet_ink/coffee_mud/Abilities/Common/Pimping.java` | Hires an owned NPC out to another player for a period | 160 |
| `com/planet_ink/coffee_mud/Abilities/Common/Searching.java` | Hidden-sense and save bonuses while searching a room | 145 |
| `com/planet_ink/coffee_mud/Abilities/Common/Shrooming.java` | `Farming` subclass; grows mushrooms and fungus in the dark | 124 |
| `com/planet_ink/coffee_mud/Abilities/Common/Rodsmithing.java` | `StaffMaking` subclass; metal and mithril rods and staves | 119 |
| `com/planet_ink/coffee_mud/Abilities/Common/ShardMaking.java` | `WandMaking` subclass; precious-material shards | 100 |
| `com/planet_ink/coffee_mud/Abilities/Common/SlaveMarketeering.java` | `Merchant` subclass whose shop deals in the caster's own slaves | 94 |
| `com/planet_ink/coffee_mud/Abilities/Common/Siegecraft.java` | `Fletching` subclass; siege ammunition and engines | 92 |

Base classes that the bucket relies on:

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/Common/CommonSkill.java` | Timed-action base: tick, abort rules, duration, invoke costs, stop, recipe loading | 1016 |
| `com/planet_ink/coffee_mud/Abilities/Common/CraftingSkill.java` | Recipe matching, material pickup, product drop and ruin, XP, learning by taking items apart | 2459 |
| `com/planet_ink/coffee_mud/Abilities/Common/EnhancedCraftingSkill.java` | Expertise stage words and their effects | 913 |
| `com/planet_ink/coffee_mud/Abilities/Common/SpellCraftingSkill.java` | Spell-to-recipe lookup used by `ScrollScribing` | 156 |
| `com/planet_ink/coffee_mud/Abilities/Common/GatheringSkill.java` | Parent of `CraftingSkill`; `bundle()` at line 365 | 513 |
| `com/planet_ink/coffee_mud/Abilities/StdAbility.java` | Proficiency roll, clone-as-effect, effect countdown, uninvoke | 3018 |
| `com/planet_ink/coffee_mud/Common/DefaultCharState.java` | Mana pool clamp at zero, `adjMana` at line 310 | (see line) |

## How it works

### 1. The timed job

Every craft and most timed actions share one sequence.

1. **Invoke.** `CommonSkill.invoke` (`CommonSkill.java:861`) refuses the action while the mob is in combat (`:866`), mounted, in the dark, sitting or asleep, or unable to move. It then charges the base usage cost (`CommonSkill.java:560` sets 25 points; the usage type is movement, `:109-112`), subtracts mana, movement and hit points (`:936-937`), and calls `helpProficiency` (`:942`).
2. **Consume.** The subclass resolves the recipe and destroys the inputs. In most crafts this comes after `super.invoke`, so a refused action costs nothing (`Shipwright.java:912-920`). Pottery and Smelting check for a fire before invoking (`Pottery.java:357-359`; `Smelting.java:398-404`).
3. **Start the timer.** The job is a clone of the skill object attached to the crafter. `StdAbility.beneficialAffect` (`StdAbility.java:2341-2350`) calls `copyOf`, which is a shallow Java `clone()` (`StdAbility.java:1424-1436`), sets the countdown, and `startTickDown` adds the clone to the mob as an effect (`StdAbility.java:698-720`). The code sets `savable=false` on the effect (`StdAbility.java:702`), so the job is not meant to be written out with the character. The save path itself was not traced.

   ```java
   // StdAbility.java:2344-2346
   final Ability newOne=(Ability)this.copyOf();   // shallow clone; fields are copied by reference
   tickAdjustmentFromStandard=getBeneficialTickdownTime(mob,target,tickAdjustmentFromStandard,asLevel);
   newOne.startTickDown(invoker,target,tickAdjustmentFromStandard);
   ```

   Because the clone is taken after the subclass has set its fields, the job receives the item, the outcome flag and the room. Shipwright then clears its own field (`Shipwright.java:1024`), relying on the clone's copy.
4. **Tick.** `CommonSkill.tick` (`CommonSkill.java:230-290`) runs each mob tick. It aborts and calls `unInvoke` when the mob is in combat, has left the room where the job started, or is not awake and mobile (`:237-241`). It prints "almost done" when `tickDown` reaches 4 (`:254`) and a progress line every four ticks (`:264-268`). `StdAbility.tick` (`StdAbility.java:2852-2857`) decrements `tickDown` and calls `unInvoke` at zero.
5. **Finish.** The subclass `unInvoke` decides the outcome: completed and not aborted, produce (`Shipwright.java:286-364`); aborted, produce nothing (`Shipwright.java:286`, `Pottery.java:158`, `Salvaging.java:158`); messed up, ruin or lose (section 3).

`stop` goes through `CommonSkill.checkStop` (`CommonSkill.java:828-843`), which sets `aborted` and calls `unInvoke`. The consequence is that an abort never returns the inputs (see Problems).

### 2. Duration

`CommonSkill.getDuration` (`CommonSkill.java:346-360`) takes a base in ticks and a minimum:

```java
// CommonSkill.java:346-360, abridged
pct = ((mobLevel - itemLevel) / LASTPLAYERLEVEL) * 0.5;   // LASTPLAYERLEVEL = 91 (coffeemud.ini:515)
ticks -= round(ticks * pct);
lastBaseDuration = max(ticks, minDuration);               // feeds the XP formula
ticks -= round(ticks * XTIME * 0.05);                     // TIME expertise, 5% per level
return max(ticks, minDuration);
```

The default tick is 4000 ms (`coffeemud.ini:2151`). A worked case: the penteconter has a recipe base of 480 ticks (section 7.1). A level 91 crafter building an item of level 1 with no TIME expertise gets `480 - round(480 * 90/91 * 0.5) = 243` ticks, about 16 minutes. A level 1 crafter on the same item gets the full 480.

Base durations by action:

| Action | Base ticks | Minimum | Source |
|---|---|---|---|
| Ship build | recipe ticks (weight / 10) | 6 | `Shipwright.java:953` |
| Ship door, demolish door | 25 | 10 | `Shipwright.java:787`, `:847` |
| Ship description | 40 | 10 | `Shipwright.java:705` |
| Ship title | 10 | 3 | `Shipwright.java:664` |
| Ship mending | fixed at 4 (the mend branch never calls `getDuration`) | none | `Shipwright.java:472`, `:574-580` |
| Pottery, Sculpting, ScrimShaw, Smelting, PaperMaking | recipe ticks | 4 | `Pottery.java:474`, `Sculpting.java:418`, `ScrimShaw.java:529`, `Smelting.java:427`, `PaperMaking.java:371` |
| Publishing | 30 | 3 | `Publishing.java:683` |
| Salvaging | 45, plus sqrt(total weight / 5) | 10 | `Salvaging.java:274`, `:294` |
| Scrapping | 45, plus the weight of the scrapped items (full weight for metal, precious and mithril; half otherwise) | 10 | `Scrapping.java:253`, `:266-271` |
| ScrollScribing | `getDuration(qualifying level * 5)` | 10 | `ScrollScribing.java:288` |
| Shearing | weight / (10 + XLEVEL), capped at 40 | 10 | `Shearing.java:75-80` |
| Scalp | weight / (10 + XLEVEL), clamped 3 to 40 | 3 | `Scalp.java:195-199` |
| Recollecting | 30 | 1 | `Recollecting.java:233` |
| PlantLore | 45 | 5 | `PlantLore.java:173` |
| Searching | 3 + XLEVEL | none | `Searching.java:133` |
| PlayInstrument | 30, fixed (the `getDuration` call is commented out) | none | `PlayInstrument.java:324` |
| Shepherding | one tick per herd member | none | `Shepherding.java:255` |
| SelfInvesting | instant | none | `SelfInvesting.java:303` |

### 3. Success roll and failure outcomes

**The roll.** `StdAbility.proficiencyCheck` (`StdAbility.java:1441-1486`) starts from the skill's proficiency, adds the PROF adjustments, adds twice the XLEVEL expertise, clamps to 5..95 unless the DIS955RULE setting is on (`:1472-1474`), adds half the XLEVEL, and succeeds when a 1..100 roll is under the result (`:1485`). SUPERSKILL staff always pass (`:1455-1456`). Crafts call the roll at invoke time, store the failure in `messedUp`, and only then clone, so the job carries the outcome.

**Failure.** Each family fails differently:

| Skill | What happens on a failed roll | Source |
|---|---|---|
| Shipwright, ScrimShaw, Sculpting, Pottery, PaperMaking | "mess(es) up" and a ruined product is dropped (`dropALoser`) | `CraftingSkill.java:471-505`; `Shipwright.java:289-306` |
| Mending (any craft) | the item is not ruined; a messed-up mend adds only 1 to 10 uses, less when the item is nearly new | `CraftingSkill.java:302-325` |
| Smelting | the inputs are gone and nothing is produced | `Smelting.java:135-147` |
| ScrollScribing | "Something went wrong! @x1 explodes!"; the scroll is destroyed | `ScrollScribing.java:133-137` |
| Salvaging, Scrapping | "You've messed up salvaging/scrapping"; no product | `Salvaging.java:160-161`; `Scrapping.java:116-117` |
| Shearing, Scalp | "You messed up your shearing/scalping completely"; no yield | `Shearing.java:130-131`; `Scalp.java:114-115` |
| Recollecting, PlantLore | checked at the sixth tick: "Your recollection attempt failed." or "Your growth observation attempt failed." and the job ends | `Recollecting.java:83-89`; `PlantLore.java:79-85` |
| Searching | checked at the first tick: "You get distracted from your search." | `Searching.java:96-103` |
| PlayInstrument | not a hard failure; four bands of verb text by proficiency (below 25, 50, 75, otherwise), and "beautifully" at 100 | `PlayInstrument.java:292-322` |
| Publishing | "You messed up your attempt to get published." | `Publishing.java:198-199` |
| SelfInvesting | mana is spent, but the bank and the day are not used up | `SelfInvesting.java:305-322` |

**Crafting XP.** `CraftingSkill.setMsgXPValue` (`CraftingSkill.java:379-424`) gives XP from the base duration, capped at 75 ticks, divided by 25. The level factor is 100 percent at equal level and rises with the item's level above the crafter's, up to the `EXPRATE` setting (6 in `coffeemud.ini:406`). An item more than 6 levels below the crafter gives nothing. Each of the last five crafted names cuts that job's XP by a quarter (`:404-419`). Keys give no XP unless the crafter is a LockSmith (`:394`). A "lottery" also exists: if the built item is not placed in the room, the crafter gets 50 XP and the message "You have won the common-skill-failure LOTTERY! Congratulations!" (`CraftingSkill.java:446-449`).

### 4. Materials, weight and value

- **Room-bound materials.** `CraftingSkill.fetchFoundResourceData` (`CraftingSkill.java:709-800`) looks for the material on the floor (`findMostOfMaterial(mob.location(), ...)`, `findFirstResource`). The consumption is `CMLib.materials().destroyResources(mob.location(), ...)` (`Shipwright.java:919-920`). A crafter needs the stock lying in the room.
- **Component expressions.** `MATERIALS_REQUIRED` is either a number of units or an expression such as `(onground:consumed:1:TIN:)&&(onground:consumed:1:COPPER:)` (`smelting.txt`, the bronze row). `CraftingSkill.getNonStandardComponentRequirements` parses it (`CraftingSkill.java:2230-2258`) and `getAbilityComponents` checks it through `CMLib.ableComponents()` (`:2260-2303`). The connector `kept` (`ithildin` row in `smelting.txt`) needs an item without using it up.
- **Expertise trims wood.** `adjustWoodRequired` removes 5 percent per XPCOST level, with a minimum of one unit (`CraftingSkill.java:361-372`).
- **Stage changes to the requirement.** LITECRAFT takes 10, 25 or 50 percent off; DURACRAFT adds 10, 20 or 25 percent; CNTRCRAFT adds 0, 5 or 10 percent (`EnhancedCraftingSkill.java:119-141`, `:142-164`, `:198-216`).
- **Value.** Recipe value plus units times the material's unit value: `Sculpting.java:433`, `ScrimShaw.java:543`, `PaperMaking.java:385`.
- **Weight.** `getStandardWeight` is the units times the multiplier, plus 1 if a second resource is used, and at least 1 for nonzero input (`CraftingSkill.java:192-200`). Paper divides that by ten with a floor of 1 (`PaperMaking.java:381-383`).
- **Material type.** `getBuildingMaterial` takes the primary resource unless it is missing, then the secondary (`CraftingSkill.java:347-359`).

### 5. Recipes: data model and lookup

- **Rows.** A recipe is one tab-separated line, read by position. Pottery's layout is name, level, build ticks, material units, value, class ID, a misc field (lid, lock, stone or statue), capacity, and a spell list (`Pottery.java:82-85`). ScrimShaw, Sculpting, Smelting and PaperMaking follow the same pattern with different misc fields (`ScrimShaw.java:80-84`, `Sculpting.java:81-85`, `Smelting.java:79-81`, `PaperMaking.java:81-82`). `ScrollScribing` has two columns, spell ID and ingredient (`ScrollScribing.java:82-85`).
- **Loading and order.** `CommonSkill.loadRecipes(String)` (`CommonSkill.java:299`) caches parsed rows in the resource table. `EnhancedCraftingSkill.loadList` sorts the rows by the `_LEVEL` column (`EnhancedCraftingSkill.java:347-381`).
- **Matching.** `CraftingSkill.matchingRecipes` (`CraftingSkill.java:1409`) tries an exact name, then a level filter ("N", "N+", "N-", "N-M"), then a loose match, then synonyms for weapon classes and wear locations.
- **Level gate.** The recipe must satisfy `level <= xlevel(mob)`, where `xlevel = level + 2 * XLEVEL` (`CommonSkill.java:645-647`).
- **Learning by taking items apart.** `learn <item>` requires an item the crafter carries, empty, not above the crafter's level, and not already recorded in a blank `RecipesBook` (`CraftingSkill.java:2356-2459`). The job takes `getDuration(10 + item level, ..., 10)` (`:2454`). `deconstructRecipeInto` (`CraftingSkill.java:1928-1963`) appends `makeRecipeFromItem` to the book. The per-item chance function `getPercentChanceToDeconstruct` (`:1916-1921`) is never called (see Problems).
- **Spells.** `SpellCraftingSkill` finds a spell by exact name, by containment, or by case-insensitive substring (`SpellCraftingSkill.java:58-124`).

### 6. Expertise enhancements (EnhancedCraftingSkill)

Expertise definitions live in `resources/skills/expertises.txt`. Each crafting enhancement has up to three stages, and each stage has a word that is prefixed to the item name (for example `DATA_=Light,Supple,Agile` for LITECRAFT). Which expertises apply to which skill comes from `CMLib.expertises().getApplicableExpertises(ID, flag)` (`EnhancedCraftingSkill.java:328-343`).

**Typing the word.** `enhancedTypes` (`EnhancedCraftingSkill.java:465-563`) reads the words from the command in order. A stage is only available if the crafter's expertise level reaches it (`:509-511`). Each expertise may be used once (`:528`), and the word "hide" before a stage keeps it out of the name (`:475-481`, `:701`). `enhanceList` prints the available words when the crafter lists recipes (`:383-426`).

**What each stage does.** The effects are applied in `enhanceItem` (`EnhancedCraftingSkill.java:685-912`), only while the job is in the crafting state and not aborted (`:689-693`). Time changes go through `bumpTickDown` on the running job (`CommonSkill.java:814-818`), so the choice changes the length of the job.

| Code (flag) | Stage words | Effect per stage, stages 1 to 3 | Prerequisite (`expertises.txt`) |
|---|---|---|---|
| ADVNCRAFT (LEVEL) | Improved, Enhanced, Superior | item level +1, +2, +3, with stat rebalance (`affectLevelBy`, `:627-683`; `:760-768`) | class level >= 1 + 20 × stage; WIS and INT >= 10 + 4 × stage (`:445`) |
| LITECRAFT (X1) | Light, Supple, Agile | material -10, -25, -50 percent; value +10, +20, +30 percent; time +0, +25, +50 percent (`:713-734`) | DEX >= 10 + 4 × stage (`:466`) |
| RUSHCRAFT (X6) | Hurried, Rushed, Cruddy | time -10, -20, -30 percent; value -10, -20, -30 percent (at least -1); item level -2, -3, -4 (`:735-759`) | DEX >= 5 + 4 × stage (`:459`) |
| DURACRAFT (X2) | Strong, Reinforced, Fortified | material +10, +20, +25 percent; armour +1 and then percentage gains; value +10, +20, +30 percent; time +0, +25, +50 percent. Armour only (`:769-796`) | STR >= 10 + 4 × stage (`:501`) |
| QUALCRAFT (X3) | Fine, Beautiful, Exquisite | value +50, +150, +250 percent; time +25, +50, +75 percent; stage 3 adds a "well dressed" armour spell (`:797-821`) | CHA >= 10 + 4 × stage (`:529`) |
| LTHLCRAFT (X4) | Damaging, Brutal, Lethal | weapon damage +5, +10, +15 percent (the last also +1); value +10, +20, +30 percent; weight +0, +10, +10 percent; time +25, +50, +75 percent (`:822-851`) | CON >= 10 + 4 × stage (`:579`) |
| CNTRCRAFT (X5) | Even, Balanced, Counterbalanced | attack +3, +6, +9; value +10, +20, +30 percent; weight +0, +5, +10 percent; time +25, +50, +125 percent (`:852-881`) | INT >= 10 + 4 × stage (`:586`) |

The stage tables are data. A reader can see the design intent: LITECRAFT trades material for time, RUSHCRAFT trades value and level for time, QUALCRAFT trades time for value, and CNTRCRAFT trades time for a weapon's attack bonus. `QUICKCRAFT` ("Quick Worker", flag TIME, `expertises.txt:564`) is defined in the data but has no entry in the `EnhancedExpertise` enum; its effect comes through the TIME flag in `getDuration` (`CommonSkill.java:357`).

### 7. Mechanics by family

#### 7.1 Ship building (Shipwright)

- **Catalogue.** `resources/skills/shipbuilding.cmare` holds 63 ship prototypes as XML item records. `Shipwright.getShips` reads them through `CMLib.coffeeMaker().addItemsFromXML` (`Shipwright.java:116-132`) and caches the result under `<ID>_PARSED`. `loadRecipes` writes one row per ship: name, level, ticks (weight / 10), wood (weight), value, index (`Shipwright.java:150-181`, rows built at `:165-170`).
- **Weight is a field of the item.** The ninth number in each `PROP` string is the item's weight (`CoffeeMaker.java:5618-5620` maps the list to ability, armour, attack, damage, disposition, level, rejuvenation, speed, weight, height and senses). The ship's wood cost is therefore its weight.
- **Worked case.** The penteconter (level 1, value 3,000) needs 4,800 wood on the floor (`Shipwright.java:892-920`) and has a base of 480 ticks (`:953`).
- **Modes.** `build` by name, `list [all]`, `learn`, `scan`, `mend`, `help` (assist another crafter), `title`, `desc`, `door`, `demolish`, `stop`, `bundle` (`Shipwright.java:476-1034`).
- **Doors.** A door costs 125 wood, must be built below decks, and needs an existing exit in that direction with no door yet (`Shipwright.java:745-762`, `:764`). `buildDoor` creates a `GenDoor` exit on both sides (`:183-221`).
- **Titles.** At most 250 characters (`:644`), unique among rooms within 20 steps (`:650-659`), and only in a room the crafter owns (`:626-629`). The title is stored on the room (`:660-663`, applied at `:253`).
- **Ownership and handover.** A ship is moved to the buyer through a temporary `StdShopkeeper` with the `DEAL_SHIPSELLER` mask and a `MSG_GET` (`Shipwright.java:223-236`). NPC builders rename the ship to a random number instead (`:338-341`). Owned ships get a land-title deed item (`:344-355`). The ship is docked and given a home port (`:358-360`).
- **Mending.** Adds 5 uses while below 95, otherwise sets 100 (`:309-316`). The duration is always four ticks (see Problems).
- **Eligibility.** Only wooden `NavigableItem`s on water (`Shipwright.java:380-394`).

Ten sample rows from `shipbuilding.cmare`, with values and weights from the `VALUE` tag and the `PROP` weight:

| Level | Ship | Value | Wood (weight) | Base ticks |
|---|---|---|---|---|
| 1 | penteconter | 3,000 | 4,800 | 480 |
| 2 | unireme | 9,000 | 4,000 | 400 |
| 3 | barge | 16,000 | 16,000 | 1,600 |
| 4 | felucca | 28,000 | 2,400 | 240 |
| 6 | knarr | 27,000 | 3,600 | 360 |
| 18 | castleship | 196,000 | 14,400 | 1,440 |
| 60 | small galleon | 1,017,000 | 13,400 | 1,340 |
| 64 | medium galleon | 1,240,000 | 14,700 | 1,470 |
| 84 | large galleass | 3,298,000 | 23,100 | 2,310 |
| 91 | very large man-of-war | 5,197,500 | 21,000 | 2,100 |

The whole list runs from level 1 to 91, values from 3,000 to 5,197,500, and weights from 2,400 to 32,300.

#### 7.2 Publishing and royalties (Publishing)

- **Data.** The author's `Publishing` ability stores a JSON object as its text (`Publishing.java:83-102`). Each title maps to `locs` (shop name and room), `author`, `price`, `level`, `copies_sold`, `paid` and `who` (a list of buyers). A top-level `lastpub` holds the game hour of the last publication (`:350-351`).
- **Preconditions.** Paper, leather, silk or hide; readable; and generic, meaning not custom-made (`Publishing.java:645-672`). One publication per in-game month, unless the crafter has the CMDMOBS or ALLSKILLS security flag (`:588-616`).
- **Reach.** Rooms within `10 + 10 × XLEVEL` (`:210`) that contain a shop dealing in books, or a non-player shopkeeper of that kind (`:215-237`). Copies are added to each shop with level `adjustedLevel` and the asking price (`:292-295`).
- **Copyright.** The book gets a `Copyright` effect that names the author (`:248-255`). Later sales check that effect (`:373-374`).
- **Royalties.** On a sale to a customer, royalties are 1 if the shop's stock price is 10 or less, otherwise the stock price divided by 10, rounded (`:462-468`). The sale is added to `copies_sold` and the royalties to `paid` (`:477-493`).
- **Popularity.** `who` collects distinct buyers. The code treats a buyer as distinct when the last login address and the account differ (`:494-511`). The heuristic is a stand-in for "different readers" and is not reproduced here.
- **Sell-back.** A book sold back to a shop decrements `copies_sold` and removes that buyer (`:418-435`).
- **Reporting.** `LIST` prints copies sold, royalties paid and the popularity count (`:532-578`).

The help text says published works cannot be edited, re-titled or copied (`common_help.ini:1856-1866`). The code in this file enforces only the generic-item check (`:668-672`) and the copyright effect. The edit lock is not visible here (unverified elsewhere).

#### 7.3 Pottery, Sculpting, ScrimShaw and Smelting

- **Materials.** Pottery takes clay or china (`Pottery.java:73-77`, `:207`). Sculpting takes rock-type stone, but not bone (`Sculpting.java:73-76`, `:192-196`). ScrimShaw takes bone or ivory (`ScrimShaw.java:71-75`). Smelting takes metal or mithril that has a subtype (`Smelting.java:70-74`, `:193-194`).
- **Fire.** Pottery needs a fire in the room to start, and the job fails if the fire goes out while work continues (`Pottery.java:115-128`; the fire check is `CommonSkill.java:501-525`, which prints "A fire will need to be built first."). Smelting needs fire unless bundling (`Smelting.java:398-406`). Learning in Pottery skips the fire (`Pottery.java:144-148`).
- **Statues.** When the misc field is `statue` and no name is given, the server prompts "What is this of?" (`Pottery.java:434`; Sculpting and ScrimShaw say "What is this a statue of?", `Sculpting.java:458`, `ScrimShaw.java:487`). The reply is re-invoked with a `STATUE=` token appended to the command (`Pottery.java:297-304`, `:446-451`). Input with a "<" is discarded (`Pottery.java:446`). A `%` in the recipe name is replaced by the subject (`Pottery.java:501-505`).
- **Locks and keys.** A LOCK container gets a `GenKey` whose key name is `Double.toString(Math.random())` (`ScrimShaw.java:573-575`; `Pottery.java:526-528`). The key is a fresh random string each time, so no two keys match by accident.
- **Capacity.** Drinks hold `capacity * 50` and quench up to 250 (`Pottery.java:543-547`). Containers gain `capacity + wood` (`Pottery.java:519`).
- **Dice.** A class whose ID ends in "Dice" stores its sides in the ability field (`Sculpting.java:549-552`; `ScrimShaw.java:611-614`).
- **Furniture.** Non-gettable items can be built only where the crafter owns the property (`Sculpting.java:553-560`).
- **Weapons and armour.** ScrimShaw sets attack and weapon class from the bonus and the misc field (`ScrimShaw.java:586-601`). Feet armour cannot be made (`:244-245`).
- **Smelting output.** The product is a raw material named "a pound of X" or "some Xs" (`Smelting.java:446-449`), with subtype from the recipe (`:462-467`). Weight is 2 when the calculation returns 0 (`:454-455`).

#### 7.4 Paper, books and scrolls

- **PaperMaking.** Wood, hemp, silk or cloth, with "WOOD" mapped to wooden (`PaperMaking.java:73-76`, `:280-281`). Wooden or rice output becomes paper (`:390-392`). A recipe book's page count is the recipe's material count (`:393-394`). Books take "maxPages/charsPerPage" (`:399-407`).
- **ScrollScribing.** Rules are these:

| Rule | Code |
|---|---|
| A scroll holds 25 points of spell level; each spell takes its qualifying level, and the remainder must stay above zero | `ScrollScribing.java:393-408` |
| Spell level on the scroll: 0 to 0, 1 to 1, 2 to 3, 3 to 4, 4 to 6, 5 to 7, 6 to 9, 7 to 10, 8 to 12, 9 to 13, otherwise +5 | `ScrollScribing.java:155-185` |
| All spells on one scroll must share one family (Spell, Prayer or Chant); clan magic is refused | `ScrollScribing.java:498-518` |
| Mana: 10 + 10 × qualifying level - 5 × class level, minimum 10 | `ScrollScribing.java:429-492`, `:535-536` |
| XP: 10 + qualifying level - class level, at least the qualifying level; charged with a negative `postExperience` | `ScrollScribing.java:486-491`, `:552-563` |
| One unit of the recipe's ingredient, destroyed | `ScrollScribing.java:520-550` |
| Duration: at least 10 ticks, from `getDuration(qualifying level * 5)` | `ScrollScribing.java:286-292` |
| Charges: a scribed scroll gains one use; erasing a spell costs one use; moving a spell from another scroll uses "from" | `ScrollScribing.java:225-226`, `:269`, `:433-475` |

#### 7.5 Salvage and scrapping

**Salvaging** breaks down a sunk ship.

- Only a water-based `NavigableItem` with an area and no remaining wear qualifies (`Salvaging.java:240-247`). No one may be aboard (`:258-262`). Flesh, liquid, paper, energy, gas and vegetation materials are refused (`:202-214`). Enchanted items are refused (`:227-231`). The crafter needs privileges on the dock (`:233-238`).
- Cargo that is gettable moves to the dock (`:253-270`, `:290-291`). The ship is destroyed when the job starts (`:292`).
- Yield: `pct = .1 + .05 × XLEVEL + .05 × (adjustedLevel / 15)`, capped at 1, and `amount = round(pct × weight)`, at least 1 (`:275-282`). At adjusted level 15 with no XLEVEL, the penteconter's 4,800 weight gives 720 units.
- Products: the primary material, grouped into ten items when there are 20 or more units (`:115-121`); plus iron of `round(sqrt(amount))` unless the hull is metal (`:167-172`); and cotton of `round(sqrt(sqrt(amount)))` unless the hull is cloth (`:173-179`).

**Scrapping** breaks down goods lying in the room.

- All items in the room that are the same as the target are pooled. The yield is the total weight divided by five (`Scrapping.java:196-224`). The help says the same (`common_help.ini`, "SCRAPPING": "1 pound of material resources per 5 pounds of items").
- Glass, metal, synthetic and mithril need a fire (`Scrapping.java:230-240`). The pooled items are destroyed at the start and their weight is added to the duration (`:264-273`). Items that are protected by a property record or that are navigable ships are refused (`:242-248`).
- Each unit of product is a separate item dropped and auto-picked up (`:132-138`).

#### 7.6 Animal and creature skills

- **Shearing.** The target's race must have a `RESOURCE_WOOL` resource (`Shearing.java:101-118`). Each shear yields `baseYield + abilityCode` units, at least 1 (`:134`). The sheep is given `TemporaryImmunity` with "+Shearing" so it cannot be sheared again (`:139`; `StdAbility.java:2351-2364`). Monsters pick any target in the room with wool (`:171-184`).
- **Shepherding.** The herd is the animal-intelligence NPCs in the room that share the example animal's race and weigh less than half the shepherd (`Shepherding.java:145-203`, `:234-246`). The herd is capped at `3 × (followers + (level / 10) × (1 + XLEVEL))` (`:228`). On the first tick each member walks one room in the chosen direction (`:96-106`) and is set to wander home after one game hour (`:103`). A command therefore moves a herd one room.
- **Scalp.** The corpse must be a `DeadBody` whose race has a head body part (`Scalp.java:171-178`), and it can be scalped once (`:179-183`). The job produces a meat-type trophy item (`:119-135`), and the trophy's secret identity names the scalper (`:134`). The effect carries the torturing flag (`:74`). The description text is grim and is not reproduced here.

#### 7.7 Economy and criminal-domain skills

- **SelfInvesting.** A `StdSkill` that is auto-invoked and cannot be removed (`SelfInvesting.java:97-106`) and is for players only (`:233-237`). A sale adds the item's value to a bank (`:205-228`). The bank is capped at the XP needed for the next level, or 5,000 at maximum level (`:179-187`). One conversion is allowed per game day, read from the home clock (`:168-174`, `:262-266`), up to `100 × qualifying level` XP per use (`:200-203`). The display says "1 gold = 1 XP" (`:252`). No help entry exists in `resources/help` (searched all 20 files).
- **SlaveTrading.** The caster sells an owned NPC to a shopkeeper who deals in slaves, or transfers the NPC to another mob. A transfer changes the owner name and makes the NPC follow the new master (`SlaveTrading.java:125-181`, `:161-172`). The help text describes the same two uses (`common_help.ini:2091-2096`).
- **SlaveMarketeering.** A `Merchant` variant whose stock is limited to the caster's own NPC slaves (`SlaveMarketeering.java:45-49`, `:68-81`).
- **Pimping.** An owned NPC can be hired out to another player for a period. The code records the hirer's name as `PIMP` and an expiry as `PIMPEXPIRE` (milliseconds) on the NPC's property record, and makes the NPC follow the hirer (`Pimping.java:137-150`). The expiry is `(ticks per MUD hour - XLEVEL) × tick milliseconds` (`:140-141`). The help says "for one mud hour", with expertise shortening it (`common_help.ini:1801-1806`). The code that returns the NPC when the time runs out is not in this file (unverified).

#### 7.8 Perception, lore and music

- **Searching.** Each tick in the same room adds 5 to a per-room bonus, up to the mob's level (`Searching.java:109-117`). On success the bonus plus proficiency adds to save-overlooking (`:71`), and the searcher gets `CAN_SEE_HIDDEN` while in that room (`:78-79`).
- **PlantLore.** Range is `2 + XLEVEL / 2 + XMAXRANGE` (`PlantLore.java:111`). It reports nearby rooms with vegetation, cotton, hemp, sap or wooden resources (`:116-125`), phrased "There looks like X to the north" or "far" (`:127-149`). Indoors it says "You don't find any good plant life around here." (`:153-155`).
- **Recollecting.** It scans readable paper items in the mob's inventory and in the room, the room items only if the crafting brand matches the mob or the mob has room privileges (`Recollecting.java:96-125`). It reads each item through a factory MOB (`:140`, `:173-186`), and scans books page by page (`:151-170`) under a budget of 100,000 characters (`:75`, `:145`). The result is "You seem to recall reading that in ..." (`:200`).
- **PlayInstrument.** The instrument must be carried and worn properly, or ridden with free hands (`PlayInstrument.java:180-191`). Proficiency is kept per instrument type (`:119-170`). The job runs 30 ticks and aborts if the instrument leaves the hands (`:193-211`, `:324`). The song text is passed in the message (`:325-326`).

#### 7.9 Painting base (PaintingSkill)

`PaintingSkill` is the base of Dyeing and Lacquerring (other buckets). `addPaintJob` saves the original name and display text on an `ExtraData` effect (`PaintingSkill.java:368-387`, `:389-397`), recolours the description (`:307-348`), and inserts the colour word after an article or after "of", "some" or "the" (`:102-129`). `removePaintJob` restores the saved name, or strips known colour phrases when none was saved (`:228-260`). The colour phrases come from every painting recipe file (`:131-161`) and from the Color256 name table (`:163-214`).

## Content worth mining

**Ships.** The 63 prototypes in `shipbuilding.cmare` cover the sailing range from a one-masted open penteconter to a very large man-of-war valued at 5,197,500. Each has a level, a value, a weight that doubles as the wood cost, and a room list (ShipDeck, WoodenDeck, ShipHold, ShipLightGunDeck, ShipMedGunDeck, ShipHeavyGunDeck, ShipMagazine, ShipQuarter). See the sample table in 7.1.

**Recipe catalogues.** Counts from the files, by item class:

| File | Rows | Levels | Mix |
|---|---|---|---|
| `pottery.txt` | 107 | 1 to 91 | 45 containers, 33 drinks, 16 items, 5 resources, 2 wands, 2 fountains, 1 tub, 1 pipe |
| `sculpting.txt` | 108 | 1 to 90 | 27 containers, 15 items, 14 dice, 11 rideables, 11 drinks, 8 beds, 7 chairs, 3 tubs |
| `scrimshaw.txt` | 149 | 1 to 91 | 57 weapons, 24 armour, 15 dice, 11 chairs, 9 spears, 7 beds, 6 items, 5 rideables |
| `papermaking.txt` | 73 | 1 to 90 | 18 player books, 15 scrolls, 15 paper, 8 recipe items, 5 items, 5 cigars, 4 containers, 1 deck of cards |
| `smelting.txt` | 48 | 1 to 90 | 48 alloys (resources) |
| `rodsmithing.txt` | 49 | 1 to 73 | 37 items, 8 staves, 4 wands |
| `shardmaking.txt` | 84 | 1 to 90 | 47 wands, 37 items |
| `siegecraft.txt` | 54 | 4 to 90 | 36 ammunition, 16 siege engines, 2 grapples |
| `shipbuilding.cmare` | 63 | 1 to 91 | sailing ships |
| `scribing.txt` | 238 | n/a | 82 spells, 87 prayers, 69 chants, each mapped to one ingredient |

Samples:

- **Dice.** Sculpting's dice run from a six-sided die (level 23, 40 ticks, value 9) to a twenty-sided die (level 37, 80 ticks, value 30), with large variants at higher levels (`sculpting.txt`).
- **Smelting alloys.** The data covers 48 alloys. Examples:

| Alloy | Level | Inputs | Value |
|---|---|---|---|
| bronze | 1 | 1 tin + 1 copper | 11 |
| pewter | 5 | 1 tin + 1 copper + 1 lead | 13 |
| electrum | 13 | 1 gold + 1 silver | 90 |
| steel | 15 | 1 iron + 1 coal | 75 |
| stainless steel | 20 | 9 iron + 2 coal + 2 chromium + 1 nickel | 85 |
| adamantite | 30 | 1 mithril + 1 steel | 175 |
| damascus steel | 38 | 2 iron + 2 coal + 1 bronze (corinthiacum) | 90 |
| truesilver | 43 | 1 mithril + 1 silver (sterling) | 300 |
| galvorn | 45 | 1 platinum + 2 mithril + 1 steel + 1 diamond | 1000 |
| moonsilver | 56 | 2 mithril + 1 tin | 125 |
| orichalcum | 70 | 1 mithril + 1 seaweed + 1 copper | 200 |
| ithildin | 80 | 2 adamantite + 1 dragonscales, plus 1 platinum kept | 300 |
| uru | 90 | 7 copper + 7 mithril + 6 steel + 3 platinum + 1 palladium | 500 |

  Several alloys use a `kept` connector (`ithildin`), which asks for an item without using it up.
- **Scribing.** `Spell_Advancement` takes platinum; `Spell_Anchor` lead; `Spell_Blindness` flint; `Prayer_AnimateSkeleton` bone; `Prayer_AnimateZombie` meat; `Chant_AiryAura` sapphire; `Chant_Barkskin` wood (`scribing.txt`).
- **Shards and wands.** Shard recipes produce wands with `Prop_ItemSlot` entries for a "shardspirit" slot (`shardmaking.txt`, e.g. "small % shard", level 1, 10 ticks, value 140).
- **Expertise words.** The crafting stage words are listed in section 6.

## Notable design ideas

1. **The job is an effect.** The running craft is the crafter's own countdown effect (`StdAbility.java:2341-2350`). Progress lines, combat aborts, stop and completion all reuse the effect machinery, so a fight stops work without any special code.
2. **Commit at the start, pay out at the end.** Inputs are consumed on start and products appear on completion. Aborting forfeits both. This is a cheap way to make the player commit (see Problems for the cost of that rule).
3. **Expertise words as a modifier grammar.** A word in the command selects a stage, and each stage trades something off (time for material, value for time, level for speed). The table in section 6 can be read as a small game design in data.
4. **Recipes are text rows, and some are generated.** The ship recipes are built from the item records at load time (`Shipwright.java:150-181`). The catalogue is data, not code.
5. **Materials as a tiny expression language.** `(onground:consumed:N:TYPE:)&&...` is a readable rule for "these materials, on the floor, used up" (`CraftingSkill.java:2230-2303`). `kept` gives tools.
6. **Magic on paper has a budget.** A scroll has 25 points of spell level, and a family lock keeps spells of different kinds apart (`ScrollScribing.java:393-511`). That is a neat way to limit power per object.
7. **Messages as hooks.** Royalties are paid inside the shop purchase message, not by polling (`Publishing.java:449-521`). Economy events arrive through the same bus as everything else.
8. **Knowledge in the world.** `Recollecting` answers "where did I read about X" by scanning the text of books and papers in reach (`Recollecting.java:96-186`). This is lore discovery through in-world documents, which suits a text game.
9. **Directional sense.** `PlantLore` and `Searching` turn perception into text that names a direction and a kind of thing (`PlantLore.java:127-149`). It reads well and needs no map UI.
10. **A statue prompt with a token.** Asking a follow-up question and re-running the command with a `STATUE=` token (`Pottery.java:297-304`, `:420-454`) is a clean pattern for any input that the command line cannot carry.
11. **A daily valve.** `SelfInvesting` turns sales into experience, once per game day, with a cap (`SelfInvesting.java:179-203`). It gives the player a reason to sell.
12. **Herding as group movement.** A herd is a list that moves one step per command and returns home after an hour (`Shepherding.java:96-106`). Simple and legible.

## Problems and cruft

1. **Sculpting mending charges twice.** The mend branch calls `super.invoke` at `Sculpting.java:321` and again at `:327`. `CommonSkill.invoke` charges the usage cost each time (`CommonSkill.java:936-937`). The proficiency help is rate limited to once per five minutes (`StdAbility.java:1810-1835`), so only the cost doubles.
2. **Publishing failure count.** `Publishing.java:364` prints the `illegal` count under the "used for something else" message. It should print `failed`.
3. **ScrollScribing ignores the mana check.** At `ScrollScribing.java:538-541` the message "You need at least N mana to accomplish that." is printed, but the code does not return. The scribing continues and `adjMana` clamps the pool at zero (`DefaultCharState.java:310-323`).
4. **Salvaging remainder loop.** `Salvaging.java:134-145` creates `extra` items, each set to weight `extra`, so the remainder is `extra` squared, not `extra`. A 22-unit salvage gives 10 items of 2 plus 2 items of 2, which is 24 units (assuming the raw copies start at weight 1; unverified).
5. **Stopping destroys the inputs.** Salvaging destroys the ship when the job starts (`Salvaging.java:292`) and pays nothing when aborted (`:158`). Scrapping destroys its items at the start (`Scrapping.java:264-273`) and pays nothing when aborted (`:114`). The enhanced crafts and Shipwright consume at the start (`Pottery.java:462-463`, `Shipwright.java:919-920`) and ignore aborted jobs (`Pottery.java:158`, `Shipwright.java:286`). Leaving the room also aborts (`CommonSkill.java:237-241`). Accidents cost the player everything.
6. **Shepherding's roll is dead.** `Shepherding.java:226` stores the proficiency result in `failed`. Nothing reads it (the field is only declared at `:73` and assigned at `:226`). Herding always works.
7. **Smelting's amount rule differs.** `Smelting.java:384-385` does `woodRequired *= amount`. Pottery (`Pottery.java:404-405`), Sculpting (`Sculpting.java:397-398`), ScrimShaw (`ScrimShaw.java:469-470`) and Shipwright (`Shipwright.java:901-902`) assign `woodRequired = amount`. The results diverge whenever the recipe needs more than one unit.
8. **Smelting list wrap can throw.** `Smelting.java:314-320` uses `lastIndexOf(' ', width)`. For a token longer than the column with no space, that returns -1 and `substring(0, -1)` throws. Latent; no current recipe was checked against it.
9. **Dead chance function.** `CraftingSkill.getPercentChanceToDeconstruct` (`CraftingSkill.java:1916-1921`) is never called anywhere in the tree. Learning by taking items apart is decided only by the ordinary proficiency roll.
10. **The "last 25" list holds five.** `CraftingSkill.java:75` names it `last25items`, but `:416-418` trims it to five entries, and the repeat penalty uses those five.
11. **String hack for china.** `Pottery.java:489-490` switches the material to china when the name contains "CHINA ".
12. **Statue with no name returns false.** `Sculpting.java:441-479` sets an auto-generated statue name when a session exists, and then returns `false` anyway. Edge case, but it means an auto-generated statue is never made.
13. **Scrapping makes one object per unit.** `Scrapping.java:132-138` drops `msg.value()` separate items. Salvaging groups them in tens (`Salvaging.java:115-121`). A large scrapping run creates a large number of objects.
14. **Colour list casing.** `PaintingSkill.java:173-174` adds the raw name on first insert, although the test and the intent use lower case.
15. **Commented-out code.** The siege hull repair block (`Shipwright.java:547-573`) is commented out and refers to a `SiegableItem` path that is not active.
16. **Typo.** `Shrooming.java:112`: "You to be in a dark place to do shrooming."
17. **No-op override.** `PlayInstrument.unInvoke` only calls `super` (`PlayInstrument.java:93-97`).
18. **Per-sale player loads.** `Publishing.java:386` and `:455` load the author with `getLoadPlayer` on each sale. That may read from disk for an offline author.
19. **Non-persistent jobs.** The job effect is flagged non-savable (`StdAbility.java:702`). What that means for a character who logs off mid-job was not traced.
20. **Multiplayer assumptions.** Ownership gates (`Shipwright.java:626-629`, `Scrapping.java:208-213`, `Salvaging.java:233-238`), assist-by-another-crafter (`Shipwright.java:582-616`), player-only checks (`SelfInvesting.java:233-237`, `Pimping.java:93`), distinct-reader popularity (`Publishing.java:494-511`), and player-to-player hiring and transfer (`Pimping.java`, `SlaveTrading.java:161-176`) all assume other people in the world.

## Takeaways for a Python SUD

- **Model a craft as a job object.** Keep the recipe, the crafter, the room, the remaining time, and a messed-up flag in one explicit record that the world ticks. Do not reproduce the shallow-clone trick. In Python the job should be a plain dataclass, so nothing aliases by accident.

  ```python
  @dataclass
  class Job:
      recipe: "Recipe"
      crafter: str
      room: str
      turns_left: int
      messed_up: bool = False
      aborted: bool = False
  ```

- **Decide the abort policy deliberately.** The code loses the inputs on a stop or a move. For a single-player game, a refund on a player-initiated stop, and a loss only on a failed roll, reads as fairer. Make it a named rule, not an accident of `unInvoke`.
- **Duration as one function.**

  ```python
  def job_turns(base, actor_level, item_level, time_bonus, min_turns, max_level=91):
      t = base - round(base * 0.5 * (actor_level - item_level) / max_level)
      t = t - round(t * 0.05 * time_bonus)
      return max(t, min_turns)
  ```

  This matches `CommonSkill.getDuration`. The same function works in ticks for real time or in turns for a turn-based game, which the brief leaves open.
- **Success roll.** `p = clamp(prof + 2*xl, 5, 95) + xl // 2`, and succeed on a roll under `p`. Keep the 5 and 95 bounds, since the code uses them to stop certainty.
- **Recipes as data.** Use TSV or YAML rows with named columns: name, level, turns, inputs, tools, value, product, misc, capacity, spell. Parse inputs with a tiny grammar that supports `consume` and `keep`, as `CraftingSkill` does, but name the fields and avoid the positional PROP string that `shipbuilding.cmare` uses.
- **Expertise stages as a table.** Put each enhancement's stage words and per-stage multipliers in data. Keep the trade-off structure: every stage should cost something.
- **Materials.** A room-bound material model is attractive for a living world: the player builds where the wood is. Keep it, but make the message say where the materials are and what is missing (the "You lack the necessary materials" message at `CraftingSkill.java:2298` is a good model).
- **Budgeted containers.** Scrolls with a level budget and a family lock are a compact way to limit magic per object. Port the rule, not the table.
- **Learn by taking things apart.** The learning path is a good discovery loop. Add the chance roll that `getPercentChanceToDeconstruct` was written for, and keep the blank-book requirement.
- **Ask follow-ups with a token.** The `STATUE=` pattern gives a text game a clean way to ask "what is this of?" and then continue.
- **Perception and lore.** `Searching`, `PlantLore` and `Recollecting` port well as text-first mechanics: a bonus per turn in a room, a directional hint list, and a search of in-world books within a text budget.
- **Economy valves.** `SelfInvesting` is a simple daily conversion of sales into experience, and it is easy to port. Royalties and the copyright record are less useful without other readers, but a per-title sales count could feed a shop's stock.
- **Drop the multiplayer machinery.** Leave out the assist command, distinct-buyer heuristics based on addresses, and player-to-player transfers unless the design needs them. Keep the ownership check for rooms and property, since a single player still owns a home.
- **Drop the slave-trading family** unless the design calls for it. If kept, describe it as a neutral trade rule with no sexual or abusive content.
- **Do not copy** the double invocation, the lost-on-stop rule, the unused chance function, or the mismatched amount rule.

## Coverage

- **Read in full (Java bodies; import and licence headers skipped):** all 26 files in the bucket.
- **Base classes, in part:** `CommonSkill.java` (lines 36-400, 496-560, 640-660, 820-1016); `CraftingSkill.java` (1-140, 182-200, 302-326, 347-505, 709-800, 1409 signature, 1916-1964, 2230-2459); `EnhancedCraftingSkill.java` (39-913, essentially all); `SpellCraftingSkill.java` (all); `StdAbility.java` (`proficiencyCheck`, `unInvoke`, `tick`, `beneficialAffect`, `copyOf`, `startTickDown`, `helpProficiency`, `spreadImmunity`, and the expertise accessors, around lines 444-470, 698-740, 1424-1486, 1501, 1810-1835, 2341-2364, 2843-2862); `DefaultCharState.java:310-323`; `CoffeeMaker.java:5600-5625`.
- **Skimmed or grep-only:** `GatheringSkill.java` (`bundle`, line 365); `Painting.java`, `Dyeing.java`, `Lacquerring.java` (class declarations and recipe filenames only); `Merchant`, `Farming`, `Fletching`, `StaffMaking`, `WandMaking` (class declarations only).
- **Data:** all 63 ship records in `shipbuilding.cmare`, parsed by a throwaway script (not kept in this repository); row counts, level ranges and class mixes for the nine recipe files and `scribing.txt`, computed with awk; the crafting entries in `expertises.txt` (lines 445-586, with stage words); help entries for Publishing, Shepherding, Salvaging, Scrapping, Pottery, the slave-related skills and Pimping in `resources/help/common_help.ini`.
- **Not read:** the other buckets' base classes and the Painting family in detail.
