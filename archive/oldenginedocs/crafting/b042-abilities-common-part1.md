# Abilities/Common part 1: the CommonSkill family and its members

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b042` (Abilities/Common [part 1/7: Alchemy .. Branding]) · 22 files · 8638 lines of Java.

## Summary

Almost every non-combat job in CoffeeMud (brewing, smithing, animal handling, bandaging, bookkeeping, body work) is a subclass of `CommonSkill`, which turns a job into a timed, interruptible activity. The player types a command, pays a movement cost, and an ability object is attached to the player as an effect; each mob tick advances a counter, progress messages are shown, and when the counter runs out `unInvoke()` delivers the result (an item, a trained animal, a renamed book). The same effect-on-object mechanism is reused for persistent jobs: a lending library, a branded animal, a bonded companion and a body piercing all leave a non-removable effect on the thing they changed. The biggest member, `BookLoaning` (1776 lines), is less a skill than a shopkeeper/librarian that can live on a mob, room, exit, area or item. Crafting members (`Alchemy`, `Blacksmithing`, `Apothecary`, `Baking`, `Armorsmithing`, `Boatwright`) inherit the crafting pipeline from `CraftingSkill` and add recipe files and item-building code. Several code paths in this bucket look wrong on reading (notably loan payback and the maintenance pass); they are listed under Problems and should be verified before anyone relies on them.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Common/CommonSkill.java` | Family base: timed activity, costs, abort rules, learning limits, stats | 1016 |
| `Abilities/Common/BookLoaning.java` | Lending library as a ShopKeeper and Librarian; loans, returns, overdue charges, `bookloan` command | 1776 |
| `Abilities/Common/Alchemy.java` | Brews spell potions and dusts into glass containers (extends `SpellCraftingSkill`) | 644 |
| `Abilities/Common/Blacksmithing.java` | Metal items from `blacksmith.txt`, including containers, locks, lights, rideables, bundles (extends `EnhancedCraftingSkill`) | 657 |
| `Abilities/Common/Armorsmithing.java` | Metal armour from `armorsmith.txt` (extends `EnhancedCraftingSkill`) | 600 |
| `Abilities/Common/Boatwright.java` | Wooden boat parts from `boatwright.txt`; `buildDoor()` creates paired exits (extends `CraftingSkill`) | 572 |
| `Abilities/Common/Apothecary.java` | Poisons, perfumes and antidotes from `poisons.txt` (extends `Cooking`) | 242 |
| `Abilities/Common/Baking.java` | Bake recipes from `bake.txt` (extends `Cooking`) | 117 |
| `Abilities/Common/AnimalTrapping.java` | Sets a caged-animal trap in a room; weighted pick from `atrapping.txt` (extends `GatheringSkill`) | 305 |
| `Abilities/Common/Baiting.java` | Baits a water room so a fish resource appears for 50 ticks (extends `GatheringSkill`) | 306 |
| `Abilities/Common/AnimalTaming.java` | Strips random behaviours from a bound or caged animal | 259 |
| `Abilities/Common/AnimalTraining.java` | Gives an animal a behaviour or a language-keyed command | 346 |
| `Abilities/Common/AnimalHusbandry.java` | Pairs a male and female animal in the owner's pasture to breed | 281 |
| `Abilities/Common/AnimalBonding.java` | Bonds an animal that is following the player; permanent bond effect | 209 |
| `Abilities/Common/AnimalTrading.java` | Sells a bound or caged animal to a shopkeeper | 199 |
| `Abilities/Common/Branding.java` | Brands an animal to a property: home room and owner recorded on the animal | 360 |
| `Abilities/Common/AttributeTraining.java` | Trains a group-member NPC in one attribute (`Prop_StatAdjuster`) | 310 |
| `Abilities/Common/Bandaging.java` | Heals and sets bones (implements `MendingSkill`); classified as `ACODE_SKILL` | 200 |
| `Abilities/Common/BodyPiercing.java` | Pierces a body location (tattoo record, damage, possible injury); has `FLAG_TORTURING` | 395 |
| `Abilities/Common/BookEditing.java` | Edits or deletes pages of an existing book | 235 |
| `Abilities/Common/BookNaming.java` | Gives a book a title from 17 name forms | 282 |
| `Abilities/Common/ArtisanalFocus.java` | Passive property that gives Apprentice/Artisan players a display class from their skills (extends `StdAbility`) | 256 |
| `Abilities/Common/BlackMarketeering.java` | Merchant that only buys goods the seller may not legally own (extends `Merchant`) | 87 |

Parent classes outside this bucket (read only for declarations): `CraftingSkill.java` (2459 lines, `deadRecord` at line 146), `EnhancedCraftingSkill.java` (913), `Cooking.java` (1573), `Merchant.java` (974), `StdAbility.java` (cost helpers, `unInvoke`, `autoInvocation`).

## How it works

### 1. The family base: CommonSkill

#### 1.1 Identity, targeting and classification

- Every member overrides `ID()` and `triggerStrings()`. The base returns `"CommonSkill"` and an empty trigger list (`CommonSkill.java:41-60`).
- `classificationCode()` is `Ability.ACODE_COMMON_SKILL` (`CommonSkill.java:151-155`). Members add a domain, for example `ACODE_COMMON_SKILL | DOMAIN_ANIMALAFFINITY` (`AnimalTaming.java:59-61`) or `ACODE_COMMON_SKILL | DOMAIN_CALLIGRAPHY` (`BookLoaning.java:101-105`).
- Targets: `canAffectCode()` is `CAN_MOBS` and `canTargetCode()` is `CAN_ITEMS` (`CommonSkill.java:134-144`). Members override these; `BookLoaning` can affect mobs, rooms, exits, areas and items (`BookLoaning.java:90-93`).
- `usageType()` is `USAGE_MOVEMENT` (`CommonSkill.java:123-127`), so crafting is paid in movement, not mana. `BookLoaning` and `Merchant` override the cost to a flat 5 (`BookLoaning.java:65-69`, `Merchant.java:64-67`). `ArtisanalFocus` uses `usageType()` 0 because it is passive (`ArtisanalFocus.java:66-69`).
- `canBeDoneSittingDown()` is false by default (`CommonSkill.java:157-160`). `BookEditing` overrides it to true (`BookEditing.java:71-74`); `BuildingSkill` makes it a settable field (`BuildingSkill.java:98-103`).
- `allowedInTheDark()` is false, `allowedWhileMounted()` is true (`CommonSkill.java:118-132`).
- Shared state: `activityRoom`, `aborted`, `helping`, `bundling`, `tickUp`, `verb`, `playSound`, `bonusYield` (`CommonSkill.java:73-82`). `bonusYield` is what `abilityCode()` returns and what `AnimalTaming` adds to its yield (`AnimalTaming.java:125`).

#### 1.2 Invocation: `CommonSkill.invoke()`

`invoke()` (`CommonSkill.java:860-945`) is the common gate that every member calls via `super.invoke()` before it starts its own timer. For a player-initiated (not auto) call it checks, in order:

1. In combat: `"<S-NAME> <S-IS-ARE> in combat!"` (`:866-869`).
2. Mounted, when not allowed: `"You can't do that while ..."` (`:871-876`).
3. Cannot see, when not allowed in the dark: `"<S-NAME> can't see to do that!"` (`:878-883`).
4. Sitting (when the skill needs standing) or asleep: `"You need to stand up!"` (`:884-889`).
5. One common skill at a time: every other common skill effect on the mob is un-invoked, except those named by `getUninvokeException()` and auto effects (`:890-902`). Interrupted skills are flagged `aborted` first.
6. Not able to move freely (bound, paralysed): return false (`:904-905`).

Then the cost is taken from the cost array (`usageCost`, section 1.6): not enough mana, movement or hit points produces `"You must be at full mana to do that."` when the cost equals the maximum, otherwise `"You don't have enough movement to do that.  You are too tired."` (`:909-933`). `checkComponents()` (inherited) is checked, the cost is deducted (`:936-938`), `setAbilityCode(0)` resets the yield bonus (`:939`), `activityRoom` is set to the current room (`:940`), and `helpProficiency` is called for non-auto use (`:941-942`).

Members then do their own work (choose a target, roll proficiency, build a `CMMsg`) before calling `beneficialAffect(mob, mob, asLevel, duration)` (for example `AnimalTaming.java:248-257`), which attaches the activity as an effect with `duration` ticks.

#### 1.3 The activity as a ticking effect: `tick()`

`tick()` (`CommonSkill.java:229-290`) runs once per mob tick (`TICKID_MOB`, which the default 4000 ms tick drives; `CMProps.java:681`). When the effect is on a mob it aborts if any of these holds (`:237-252`):

- the mob is in combat,
- the mob has left the room it started in (`R != activityRoom`),
- the mob is not awake, mobile and unbound,
- the activity requires standing and the mob is sitting, or the mob is mounted, or it cannot be seen in the dark.

An abort sets `aborted = true`, calls `unInvoke()` and returns false, so no product is delivered (see 1.4).

Progress messages use the tick counters. When `tickDown == 4` the "almost done" line is shown (`:254-262`); otherwise every fourth tick (`tickUp % 4 == 0`) a "continue" line with the percentage is shown (`:264-272`). The strings are built in `:175-198`, for example `"<S-NAME> continue(s) @x1 (@x2% completed)."`.

Two side effects apply to any common skill run by a player: a helping ability is ticked too (`:273-277`), and the mob loses hygiene at `PlayerStats.HYGIENE_COMMONDIRTY` per tick unless it has a soulmate or hygiene is disabled (`:278-282`). `tickUp` accumulates the amount `tickDown` actually fell, so `bumpTickDown()` and extra time from other sources count as progress (`:285-289`, `:814-819`).

`addedTickTime()` always returns 0, so a common skill never lengthens its own time (`:364-369`).

`okMessage()` aborts the activity when the mob is shut down or quits (`:212-227`).

#### 1.4 Completion: `unInvoke()` and `dropAWinner()`

`unInvoke()` (`CommonSkill.java:320-344`) prints `"<S-NAME> stop(s) @x1."` when aborted or `"<S-NAME> <S-IS-ARE> done @x1."` otherwise, then clears helping state. Each member overrides it to deliver the result. The base helper `dropAWinner()` (`:399-447`) asks the room whether an item may be added (`ItemGenerated` message, `MASK_ALWAYS`), adds it with `ItemPossessor.Expire.Resource`, and then checks it is still in the room. If it is gone (a listener removed it), the crafter is told `"You have won the common-skill-failure LOTTERY! Congratulations!"` and receives 50 experience (`:439-440`). When the mob is `null` (an NPC-produced item), a factory mob is created for the message and destroyed in a `finally` (`:407-425`).

`setBrand()` (`:371-377`) stamps the item's secret identity: raw materials get their own name, crafted goods get `createCraftingBrand(mob)`. This brand is what later lets a smith be identified from an item.

#### 1.5 Duration

`getDuration(baseTicks, mob, itemLevel, minDuration)` (`CommonSkill.java:346-362`):

- `level = mob level - itemLevel`; `pct = level / LASTPLAYERLEVEL * 0.5`; ticks are reduced by `ticks * pct`.
- `lastBaseDuration` is set to that figure, clamped to `minDuration`.
- `quickPct = getXTIMELevel(mob) * 0.05` further reduces the ticks.
- The result is clamped to `minDuration`.

So a master at a skill finishes faster than a novice, and expertise in quick working adds up to a further 5% per level. Typical base values seen in this bucket: 35 for animal bonding, taming and branding (`AnimalBonding.java:199`, `AnimalTaming.java:249`, `Branding.java:350`), 45 for attribute training (`AttributeTraining.java:300`), 55 for husbandry (`AnimalHusbandry.java:271`), 30 for body piercing (`BodyPiercing.java:343`), and 30 minus half the expertise level for book work (`BookEditing.java:214`, `BookNaming.java:273`).

#### 1.6 Movement cost: `usageCost()`

`usageCost()` (`CommonSkill.java:532-643`) builds a cost array indexed by `Ability.USAGEINDEX_MANA/MOVEMENT/HITPOINTS` (`Ability.java:658-662`).

- Base: `consumed = 25` (`:560`).
- If the mob's class level plus the XLOW-cost expertise is more than 2 above the skill's lowest qualifying level, `consumed -= diff / 3`; if the difference is over 60, `consumed = 5` (`:565-583`).
- An ini override from `MANACOST` (`CMProps.getManaCostExceptionObject`, `CMProps.java:2222-2228`) can be a number, a formula or negative. The default is -1, and the code treats a negative value `n` as `consumed += lowest * |n|` (`:601-603`). So with the default, the base cost is 25 plus the skill's lowest qualifying level, reduced by the level difference.
- Minimums: `MANACOST` exceptions can force a minimum (`:607-621`), `overrideMana()` replaces the value when it is 0 or more (`:622-623`), and a class cost override may replace the mana value (`:625-630`).
- `StdAbility.buildCostArray()` (`StdAbility.java:1574-1640`) then divides by the number of cost types in use (1 here), subtracts an XLOW reduction, and enforces a minimum of 5.
- The ini comment says a negative value means `50 + n * lowest` (`coffeemud.ini:871`). The code computes `25 + |n| * lowest`. The code is what runs; the comment is out of date or wrong.

#### 1.7 Fire, materials and rooms

- `getRequiredFire(mob, autoGenerate)` (`:501-530`): a crafter needs a lit item in the room (`CMLib.flags().isOnFire`), otherwise `"A fire will need to be built first."`. A crafting skill that declares no fire requirement, or an auto-generate call, gets a shared fake fire item with a `Burning` effect (`:506-513`). The fake fire is a static field, lazily set.
- `lookingForMat()` and `lookingForRsc()` (`:449-499`) search the open exits for a neighbouring room whose resource is in a list, and pick one at random with `dice().roll(1, n, -1)`. Their callers were not traced.
- `confirmPossibleMaterialLocation()` (`:650-673`) and `supportedResourcesMap()` / `isMadeOfSupportedResource()` (`:675-748`) turn a skill's `supportedResourceString()` (for example `"METAL|MITHRIL"` in `Blacksmithing.java:71-74`, `"VEGETATION|COTTON|HEMP|WOODEN"` in `Baiting.java:72-75`) into a list of material and resource codes. Parsing is cached in the static `resourcesMap` (`:67`).

#### 1.8 Learning limits, teaching and stopping

- `canBeLearnedBy()` (`:750-787`): if the student's current class already qualifies for the skill, it is learnable. Otherwise the student's remaining common-skill allowance is checked: the overall count, then the crafting or non-crafting count (`CMLib.ableComponents().getSpecialSkillRemainder`). Messages such as `"You have learned the maximum @x1 crafting skills, and may not learn any more."`.
- `teach()` (`:789-812`) reports what the student may still learn.
- `checkStop()` (`:828-845`): the word `stop` on a running skill sets `aborted` and un-invokes it; otherwise `"You are not doing that right now."`.
- `setMiscText("abort")` (`:847-858`) forces an abort by setting `tickDown = 1` and ticking once.

### 2. Shared patterns in the members

#### 2.1 Companion-target skills (animals and branding)

Animal skills share one template: check the target (visible, an animal, `isBoundOrHeld` or can't move), start a timed activity, then in `tick()` abort if the target has left, and in `unInvoke()` apply the result. The target is validated in `invoke()` and then used as `taming` / `trainingM` / `bonding` / `branding` in `tick()`. Results are written as effects or behaviours on the animal. Examples:

- `AnimalBonding`: `bonding` is the animal; the bond is a copy of the effect with the owner's name as its misc text, marked non-uninvokable and added to the animal (`AnimalBonding.java:124-127`). Once bonded, the animal's `okMessage` moves the bond to the front of its effect list and rewrites the language of orders from the owner (`:138-162`).
- `Branding`: the animal gets a non-removable `Branding` effect with `OWNER="..."` (`Branding.java:215-217`) and its start room becomes the branding room (`:218`). A push or pull by the property owner makes the animal follow that owner instead (`okMessage`, `:236-245`, `:260-270`), and an owner's "nofollow" re-homes the animal to its current room (`executeMsg`, `:299-302`).

#### 2.2 Cages

Caged creatures are stored as XML inside a `GenCaged` item. `unCageMe()` builds a fresh `MOB` from that XML each time it is called (`Items/Basic/GenCaged.java:135-170`), and `cageMe(mob)` writes it back. Because `unCageMe()` makes a new object, any effect pointer taken from one copy does not point into the next (see Problems, item 7).

#### 2.3 Writing skills (books)

`BookEditing` and `BookNaming` share the same gate: the book must be paper, leather, silk or hide (`BookEditing.java:179-183`), readable (`:188`), generic (`:201`), the mob must know `Skill_Write` (`:172-176`), and two rolls are needed: the skill's and `Skill_Write`'s (`:212`). Failure sets `found = null` or `writing = ""` so the job ends with `"You mess up your book editing."` (`:91-92`) or `"You mess up your book naming."` (`BookNaming.java:141-142`).

### 3. BookLoaning (1776 lines)

#### 3.1 Data model

- `CheckedOutRecord` (from `MOBS/interfaces/Librarian.java`) holds `itemName`, `playerName`, `charges`, `mudDueDateMs` and `mudReclaimDateMs`. The list `records` is a `LinkedList` (`BookLoaning.java:128`), serialized as `<CHECKEDOUTRECORDS>` XML (`:202-217`, `:296-312`).
- Stock: a `CoffeeShop` (`shop`, `:112`), built with `DefaultCoffeeShop`. A second copy, `curShop`, is a derived view with the checked-out titles removed (`getShop()`, `:168-200`). `shopApply` chooses which one is returned (`:170`, `:198`).
- Policy fields and defaults: `minOverdueDays = 12`, `maxOverdueDays = 48`, `maxBorrowed = 2` (`:122-124`), a budget of 100000 per day (`:127`), `whatIsSoldMask = DEAL_BOOKS` (`:114`), `devalueRate`, prejudice and ignore masks, and a currency (`:113-121`). Setters and getters are at `:237-292` and `:296-508`. The overdue monetary charge and daily charge settings are stubs that return 0 (`:430-472`), so only the day-based limits are live.
- `text()` saves `shop.makeXML()` plus the records (`:226-229`). `setMiscText()` resets the day limits to 12, 24 and 2 whenever it finds saved records (`:319-321`); `DUEDAYS`, `MAXDAYS` and `MAX` parameters are read only when there are no saved records (`:334-336`). See Problems, item 4.

#### 3.2 Who is the librarian: `deriveLibrarian()`

`deriveLibrarian(roomHelper)` (`BookLoaning.java:736-774`) decides which mob acts as the shopkeeper for a given effect:

- If the effect sits on a mob, that mob is the librarian (`:740-741`).
- If it sits on an item: the item's owner if it is a mob (`:744-745`); if the item is gettable, there is no librarian (`:746-747`).
- Otherwise a static `StdMOB` is placed in the room, named "the shopkeeper" (room or exit), "the shop" (area), or the item's name, and its money is topped up to the budget (`:749-772`).

`isActive(mob)` (`:1506-1524`) and `makeActive(mob)` (`:1526-1553`) implement "one active store per mob": the first `ShopKeeper` effect in the mob's list is active, and a store the player uses is moved to the front with the message `"@x1 is now your active store."`.

On a player, `autoInvocation()` adds a private library as an effect (`:1759-1773`), which is why each player with the skill has a "Personal Library" (`libraryChain()`, `:511-516`). The help text (`resources/help/arc_properties.ini:106-119`) describes the property use: `Targets: Items, Mobs, Areas, Rooms, Exits`, parameters `DUEDAYS`, `MAXDAYS`, `MAX`, `DAYCHARGE` (the last is not implemented in this file; see Problems item 5). The class is mapped to the Scholar class at level 25 (`CharClasses/Scholar.java:225`).

#### 3.3 Lending, borrowing and returning

Borrowing is `okMessage` case `TYP_WITHDRAW` / `TYP_BORROW` (`:882-924`) and then `executeMsg` (`:1362-1410`):

- Refused with `"What do you want? I'm busy! Also, SHHHH!!!!"` if the tool is not an item or is coins (`:888-892`).
- Refused with `"We don't stock anything like that."` if the title is not in stock (`:895-899`).
- Refused with `"I'm sorry, but you have @x1 in overdue charges and may not borrow any more."` if the borrower owes money (`:900-906`).
- Refused with `"I'm sorry, but you may only borrow @x1 items."` when the borrower already holds `maxBorrowed` titles (`:907-911`).
- Refused with `"I'm sorry, but you already borrowed a copy of that."` (`:912-916`).
- Refused with `"Please come back a little later."` when `getShop()` returns the master shop (`:917-922`). Because `getShop()` returns the master whenever `shopApply` is false (`:198-199`), this refusal is what stops borrowing until some player has acted in the room (see executeMsg below).

If allowed, `executeMsg` creates a record: `dueDate = now + minOverdueDays * day`, `reclaimDate = now + maxOverdueDays * day` (`:1373-1381`). The title is removed from the derived shop (`removeSellableProduct`), dropped on the floor and handed to the borrower with `"There ya go! This is due back here by @x1!"` (`:1383-1402`). The record is added (`:1403`).

Returning is `TYP_GIVE` or `TYP_DEPOSIT` with an item tool (`:1174-1310`). The item is `autoGive`n to the librarian (`:1197`) and destroyed (`:1276`). If the returner owes money, the check `getTotalAbsoluteShopKeepersValue < rec.charges` decides whether the fee is paid on the spot (`:1281-1301`); otherwise the record keeps its charges and the item slot is cleared. A returned item with no matching record goes to "I assume you are returning this for X" (`:1256-1269`); with no match at all, `"What is this?!"` (`:1270-1275`).

Coins paid to a librarian are handled at `:1198-1243`; see Problems item 1, which is serious.

Putting a book up for loan: `TYP_PUT` on a container affected by the skill (`:1316-1324`), or a drop/throw on an ungettable affected room, exit or area (`:1466-1499`). `putUpForLoan()` (`:970-997`) requires the source to hold order or law privileges (`CMSecurity.SecFlag.ORDER`, `CMLib.law().doesHavePriviledgesHere`, or a clan role on the legal behaviour) and then says `"OK, I will now loan @x1."` and adds the item to the store (`:984`).

Selling to the librarian is refused with `"I'm afraid you lack the credentials to contribute to our stock."` if a contributor mask does not match (`:927-933`).

#### 3.4 Overdue and reclaim arithmetic

`processCheckedOutRecord()` (`BookLoaning.java:691-733`):

- The item's value is its pawn price if that price is in gold, otherwise the constant **10** (`:705`, and `0` for the reclaim step, `:724`).
- After the due date, charges accrue: `charges = value * min(overdue, window) / window`, where `window = reclaim - due` and, if the reclaim date is before the due date, `reclaim = due + 1 day` (`:706-712`).
- After the reclaim date the charges are raised to at least the item's value, and the item is cleared from the record (`rec.itemName = ""`) so it is back on the shelf (`:719-730`).

#### 3.5 Maintenance: where it should run and why it appears not to

The daily pass is inside `tick()` (`BookLoaning.java:624-689`). It runs at most once per mud day (`CMProps.getMillisPerMudHour() * clock.getHoursInDay()`, `:646`) and purges records with no player name or with a player who no longer exists (`:661-676`). It is gated on `tickID == Tickable.TICKID_MOB` (`:633`).

On reading, this block does not run in practice:

- For an effect on a mob, `BookLoaning.tick()` calls `CommonSkill.tick()` first (`:628`). That returns false whenever `R != activityRoom` (`CommonSkill.java:237-242`), and `activityRoom` is only set by `invoke()` on the instance that runs the command (`:940`), which is not the attached copy made by `autoInvocation()` (`StdAbility.java:2381-2386`). So the effect returns false each mob tick and the block at `:633-688` is skipped. `StdMOB` then calls `unInvoke()`, which only marks the effect as un-invoked because `canBeUninvoked()` is false (`StdMOB.java:4326`, `StdAbility.java:1501-1503`).
- For an effect on an area, the effect is ticked with `TICKID_AREA` (`Areas/StdArea.java:1282-1320`), which fails the `TICKID_MOB` guard.
- Rooms and items were not traced.

The consequence, as read: overdue charges and the purge of departed borrowers are computed lazily, only when a player or the librarian runs `bookloan list` (`:1573-1601`) or the `TYP_LIST` handler (`:1433`).

#### 3.6 The `bookloan` command

`invoke()` (`BookLoaning.java:1556-1756`):

- `bookloan list`: sends a `MSG_LIST` to the room, then for each record prints `"<player> checked out <item>, due on <date> and now owes <amount>."` or `"<player> has owed <amount> since <date>."` (`:1564-1603`).
- `bookloan remove <name>`: takes the title off the working shop and the master shop and returns the item(s) to the owner (`:1605-1643`), with `"@x1 has been removed from your loanable selections."`.
- `bookloan <item> [value]`: parses the price as an integer or as a coin amount (`:1645-1670`), gathers identical items including `all` and `.2`-style names (`:1672-1709`), refuses containers with contents (`:1695-1700`), and requires a price for titles not already in stock (`:1717-1721`). Capacity is `5 + adjustedLevel/8 + XLEVEL` titles (`:1723`). Then `super.invoke()` (`:1731`), a proficiency check (`:1734`), and a `MSG_SELL` that moves the items into `loanA.shop` with `addStoreInventory(I, 1, (int)val)` (`:1740-1751`).

### 4. Crafting members

#### 4.1 Alchemy (644 lines; `SpellCraftingSkill`)

- `supportedResourceString()` is `"MISC"` and the recipe file is `alchemy.txt` with rows `SPELL_ID / RESOURCE_NAME / POTION|POWDER` (`Alchemy.java:72-81`, `:149-151`). Recipes need no level column; `craftAnyItemNearLevel()` picks by the spell's lowest qualifying level (`:288-332`).
- Learning a recipe turns off the fire requirement (`:142-146`). Prayers never need fire (`:557-560`); spells do (`:565-571`).
- Spell-level adjustment (`spellLevelAdjustment`, `:239-268`): qualifying level 0-1 gives +0, 2-3 gives +1, 4-5 gives +2, 6-7 gives +3, 8-9 gives +4, 10 and above gives +5. In effect `min(level / 2, 5)`.
- Experience cost, charged up front: `10 + 10 * qualifyingLevel - 5 * qualifyingClassLevel`, at least 10 (`:556-570`, `:605-606`), passed through `getXPCOSTAdjustment` and `postExperience` (`:612-613`). A brew whose XP cost resolves to zero or less fails with `"You are unable to complete this brewing."`.
- The container must be a glass drink container with liquid in it (`:506-525`). Extra contents must be removed, and the recipe's ingredient must be present (`:572-604`).
- The product is a `GenPotion` (`"a potion of <spell>"`, `:334-347`) or a `GenPowder` (`"some dust of <spell>"`, `:349-362`). Duration is `max(10, 5 * qualifyingLevel)` (`:426-431`).
- `tick()` (`:106-138`) aborts if the fire is gone while the mob is still in the start room, or if there is no spell. The first tick sets the verb: "praying for X" for prayers, "brewing X" otherwise, with the sound `hotspring.wav` (`:121-135`).
- `unInvoke()` (`:203-237`): on failure the potion explodes (`"Something went wrong! @x1 explodes!"`) and is destroyed; while learning, a successful brew is deconstructed into the recipe (`deconstructRecipeInto`); otherwise the potion is given to the mob. If the activity was aborted, the potion is discarded silently (see Problems item 9).

#### 4.2 Blacksmithing (657 lines; `EnhancedCraftingSkill`)

- `supportedResourceString()` is `"METAL|MITHRIL"` (`Blacksmithing.java:71-74`); recipes come from `blacksmith.txt` (`:126-130`). Recipe columns, with indices `RCP_TICKS=2`, `RCP_WOOD=3`, `RCP_VALUE=4`, `RCP_CLASSTYPE=5`, `RCP_MISCTYPE=6`, `RCP_CAPACITY=7`, `RCP_SPELL=8` (`:88-94`), are: name, level, build ticks, metal needed, base value, item class ID, a misc type, a capacity field and a spell list (`:77-83`).
- `mayICraft()` (`:151-202`): accepts only metal or mithril items, excluding deadly or malicious effects and shields, weapons and armour (except native items). Accepts ladders and furniture rideables, keys, lights, containers, non-potion drinks, false limbs and wrist-held items.
- Fire is needed for everything except bundles (`:419-427`). `tick()` (`:107-123`) sets `messedUp = true` if the fire is gone, which ruins the item: `"You've ruined @x1!"` and a drop of the ruined item (`:205-222`).
- `invoke()` (`:254-655`) runs the whole pipeline: `list`, `learn`, optional `bundle`, the trailing `STATUE=` and amount tokens (`:354-367`), metal selection (`:368-369`), recipe match with a partial-name fallback (`:371-389`), components (`:399-401`), the wood adjustment (`:403`), fire (`:421-427`), and a statue prompt that re-invokes the command with `STATUE=<name>`. The prompt callback refuses input containing `<` (`:429-466`).
- Resources are consumed by `CMLib.materials().destroyResources(...)` unless ability components are present or the call is auto-generated (`:471-478`). In that case the no-loss `deadRecord` placeholder is used (`CraftingSkill.java:146`, `:472-473`) and the metal is consumed elsewhere (not traced).
- The item's value is `baseValue + woodRequired * RawMaterial.CODES.VALUE(material)` (`:503`). Weight comes from `getStandardWeight` (`:502`). Duration is `getDuration(ticks, mob, level, 4)` (`:488`).
- The `capacity` column is reinterpreted by item class (`:506-616`): armour sets its wear location (`:510-514`); a rideable's rider capacity is 1 when the column is 0 and the column value when it is under 5 (`:523-532`); a light sets its fuel duration (`:534-573`); a container sets its capacity to `capacity + woodRequired` (`:574-596`); a drink holds `capacity * 50` and quenches up to 250 (`:597-607`); dice store their sides in the item's ability stat (`:608-611`); wands store charges (`:612-616`). A `LOCK` misc type creates a `GenKey` with a random key name (`Math.random()`, `:541-552`, `:580-590`).
- The start message is `"<S-NAME> start(s) smithing @x1."` with the sound `tinktinktink2.wav` (`:496-499`). Bundles finish in one tick and are valued at the lost material value (`:617-631`).

#### 4.3 Other crafting members

- **Armorsmithing** (`Armorsmithing.java`, 600 lines): metal armour from `armorsmith.txt`; supports mending; `tick()` ruins the item when fire is gone (`:111-127`). The build body was not read.
- **Boatwright** (`Boatwright.java`, 572 lines): wooden items (`supportedResourceString` `"WOODEN"`, `:71-74`) from `boatwright.txt`. `buildDoor()` (`:128-168`) creates a `GenDoor` exit with open delay 9999, a matching exit in the neighbouring room, and saves both to the database. Used by ship recipes; the rest was not read.
- **Apothecary** (`Apothecary.java`, 242 lines; `Cooking`): `supportedResourceString` `"MISC"` (`:60`); `poisons.txt` (`:90-92`). `mayICraft()` (`:171-205`) accepts perfumes, drinks whose liquid is poison, and dusts with spells. `invoke()` (`:207-242`) makes poison by default (liquid and material set to `RESOURCE_POISON`). If the brew mode text contains `ANTIDOTE` it becomes drinkable, every poison effect on the item is removed, and their IDs are appended to an `Antidote` effect which is then attached non-removable (`:215-237`). Antidote recipes come from a separate parsed list (`:113-151`). The source of the mode text was not traced.
- **Baking** (`Baking.java`, 117 lines; `Cooking`): `bake.txt`, `cookWord "baking"`, `requireLid` true, `honorHerbs` false (`:65-91`). `stirThePot()` shows `"<S-NAME> check(s) on the <O-NAME> in <T-NAME>."` every fifth tick (`:101-111`).
- **AnimalTrapping** (`AnimalTrapping.java`, 305 lines; `GatheringSkill`): one trap per room (`R.fetchEffect(ID())` gives `"Animal traps have already been set here."`). The cage must be a container for bodies and cages, empty, with a door (`invoke()`). The trapped creature is chosen by weighted lottery from `atrapping.txt`: rows are (resource, domain or `*` or room ID, frequency, mob ID). Only rows matching the room's resource and domain count (`:184-187`), and the random pick uses `roll(1, totalWeights, -1)` (`:196-206`). On completion `unInvoke()` prints `"<cage> makes a clicking sound."`, puts the creature in the cage, and resets the cage doors and locks (`unInvoke()`).
- **Baiting** (`Baiting.java`, 306 lines; `GatheringSkill`): `getDuration(45, mob, level, 15)` (`:88-91`). Requires a water room, or a boat in a navigable area; `list` prints the fish types (`RawMaterial.CODES.FISHES()`). The chosen fish code is set as the room's resource each tick for 50 ticks by a copy effect (`:100-110`, `:132-133`). The room shows `"Some @x1 can be seen swimming around here."`.

### 5. Companion, social and person skills

- **AnimalTaming** (`AnimalTaming.java`): a successful taming removes `1 + bonusYield` random behaviours from the animal (`:125-147`), sets the `TAMED` ambiance, and restores the animal to full state. Caged animals are read with `unCageMe`, changed and re-caged (`:148-151`). Message: `"<S-NAME> manage(s) to tame @x1 @x2."`, where the second part is built from "of N of its behaviours" text (`:124-131`).
- **AnimalTraining** (`AnimalTraining.java`): the word typed (matched by prefix against the `Trainables` enum) selects a behaviour or a language translator. The mapping is in the table in section 6.
- **AnimalHusbandry** (`AnimalHusbandry.java`): requires a pasture the mob has privileges in (`:187-190`), two different-sex animals of a breed that can breed (`:219-261`), neither already pregnant. Duration 55, minimum 20 (`:271`). On completion the `MATE` social runs with a short fertility effect on both animals (`Chant_Fertility`, two ticks) when the species can breed (`:137-150`); if no social exists, a `Pregnancy` effect is invoked on the female when a proficiency check passes (`:151-163`). This is animal husbandry content; it is described here neutrally.
- **AnimalBonding** (`AnimalBonding.java`): the animal must be following the player and be an animal (`:182-193`). The bond is permanent (section 2.1).
- **AnimalTrading** (`AnimalTrading.java`): sells a bound animal, or one inside a caged container, to a shopkeeper (`invoke()`, `:80-197`). The sale is two `CMMsg`s, `MSG_SELL` and a value message; a successful sale destroys the cage item (`:191-192`).
- **AttributeTraining** (`AttributeTraining.java`): trains a group member NPC in one of the base attributes, found by prefix (`:207-226`). Cost: `2 * priceI` of the training cost (`:65-66`, `:268-270`), and the trainer's stat must be higher than the student's racial stat (`:264-275`). At completion, the student gets `+1` in a `Prop_StatAdjuster` text field named by the first three letters of the attribute (`:154-175`).
- **Bandaging** (`Bandaging.java`): targets a mob with `Bleeding`, `Injury`, `Hamstring` or `BrokenLimbs`, otherwise `"... not bleeding, broken or injured!"` (`:173-178`). Duration is `3 + round(10 * (1 - hp/maxhp)) - XLEVEL`, at least 3 (`:184-186`). Each tick heals `XLEVEL + round(level/2)` hit points (`:114`). Completion removes the bleeding, injury and hamstring effects and sets the broken-limbs effect to `+SETBONES` (`:134-149`).
- **Branding** (`Branding.java`): see section 2.1. A branding that is in progress checks the room, and when the animal is a savable, rejuvenating monster with a start room, the original is killed (`killMeDead(false)`) and a copy brought to life in the room (`:201-213`).
- **BodyPiercing** (`BodyPiercing.java`): needs a target at full health (`:282-285`) with the wear location free of clothing (`:276-280`). Piercing is tracked as a tattoo named `WEARLOC:Pierced <parts>` or `WEARLOC:A pierced <part>` (`:332-337`). Damage is two postings of `maxHP/8` piercing damage when the target has more than twice that (`:356-360`). `tick()` pins the target's hit points to their post-damage value while the work goes on (`:180-181`). On completion, a random body part of that wear location may take injury (`:146-157`, `:383-388`). Flag: `FLAG_TORTURING` (`:66-69`). Recipes come from `bodypierce.txt` (`:97-99`).
- **BookEditing** and **BookNaming**: see section 2.3. Book naming (`BookNaming.java`) uses 17 forms (table, section 6), a title of at most 20 characters (`:251-253`), and refuses books with a `Copyright` effect (`:214-217`). The result is `found.setName(writing)` (`:145`).
- **ArtisanalFocus** (`ArtisanalFocus.java`): a passive property for Apprentice and Artisan players. Every 21 to 30 real minutes (`20 + roll(1,10)`, `:184`) it scores the nine craft families in `nameSets` (`:89-99`). Each matching ability contributes `proficiency * (1 + 10 * expertiseStage / numStages)` for each expertise the ability has (`:195-205`). Family scores are averaged over the skills the player has (`:223-224`). The highest family is the display class, with " Apprentice" added for apprentices, if it is at least the mean and, with three or more families present, at least one standard deviation above it (`:239-248`). The display class is applied in `affectCharStats` (`:252-253`). `artfocus` toggles it off and on through the `HUH` message (`:148-169`).
- **BlackMarketeering** (`BlackMarketeering.java`, 87 lines; `Merchant`): a sale is accepted only when `CMLib.law().mayOwnThisItem(mob, item)` is false, that is, the goods are stolen; otherwise `"@x1 is not a stolen item."` (`:62-73`). Invoked as `bmarket`, it calls `makeActive` first (`:77-85`).

## Content worth mining

**Book name forms** (`BookNaming.java:70-87`, a `PREFIXES` table; `@x1` is the title, `@x2` the author): "The Book of @x1", "The Tales of @x1", "The Tale of @x1", "The Chronicles of @x1", "The Story of @x1", "The Tome of @x1", "The Booklet of @x1", "@x1: A Novel", "The `@x1` Book", "The `@x1` Journal", "The `@x1` Magazine", and six with the author: "`@x1`: A Book by @x2", "`@x1`: A Novel by @x2", "`@x1`: A Tome by @x2", "`@x1`: A Story by @x2", "`@x1`: A Volume by @x2", "`@x1`: A Treatise by @x2". Ready-made templates for in-world titles.

**Artisan families** (`ArtisanalFocus.java:89-99`), each with a display class name:

| Display class | Skills in the family (abridged) |
|---|---|
| Farmer | Irrigation, Farming, Shrooming, Composting, PlantLore, Floristry, Herbology, Gardening, Mycology |
| Rancher | AnimalHusbandry, CageBuilding, Taxidermy, Branding, Baiting, Shearing, Herding, Shepherding |
| Smith | Smelting, Armorsmithing, Weaponsmithing, JewelMaking, Blacksmithing, Rodsmithing, LockSmith |
| Tailor | Tailoring, LeatherWorking, Weaving, Cobbling, Tanning, Textiling |
| Sculptor | GlassBlowing, ScrimShaw, Sculpting, Pottery |
| Prospector | Chopping, Fishing, Foraging, Mining, Drilling, Trawling, Hunting, Digging, Speculate |
| Chef | Baking, FoodPrep, Cooking, Distilling, MeatCuring, FoodPreserving, Butchering |
| Carpenter | InstrumentMaking, Boatwright, Wainwrighting, Carpentry, WandMaking, StaffMaking, Fletching, Siegecraft, Costuming, PaperMaking |
| Architect | Shipwright, Masonry, Excavation, Construction, Landscaping |
| Artist | Lacquerring, Dyeing, Skill_Write, Painting, Decorating, Engraving, Embroidering |

**Trainable animal commands** (`AnimalTraining.java:69-85`, `:208-236`): WANDER gives the `Mobile` behaviour; HUNT gives the `Hunting` ability; ASSAULT gives `Aggressive`; DOORGUARD gives `DoorwayGuardian`; TALK gives `MudChat`; HUSH removes `MudChat`. KILL, SIT, SLEEP, STAND, FLEE, GET, FOLLOW and NOFOLLOW are taught through a `Prop_LangTranslator` effect that maps the spoken word to the command in the trainer's language.

**Alchemy spell level** (`Alchemy.java:239-268`): qualifying level 0 or 1 gives +0; 2 or 3 gives +1; 4 or 5 gives +2; 6 or 7 gives +3; 8 or 9 gives +4; 10 or more gives +5.

**Bandage timing** (`Bandaging.java:184-186`): 3 ticks at full health, up to 13 ticks at zero health, minus the healer's XLEVEL.

**Shop and library lines**, for flavour: `"I will now loan out @x1."`, `"There ya go! This is due back here by @x1!"`, `"Please come back a little later."`, `"You have @x1 checked out. It is past due."` (`BookLoaning.java`). `"Some @x1 can be seen swimming around here."` (`Baiting.java`).

**Poison and perfume kinds** are data rows in `poisons.txt`; the code only checks the liquid type and whether the item carries a poison effect (`Apothecary.java:171-205`). **Alchemy names**: "a potion of <spell>", "some dust of <spell>" (`Alchemy.java:339`, `:354`).

## Notable design ideas

- **Jobs as timed effects.** A craft is an ability object attached to the crafter, with duration in ticks and an abort list (combat, moving, sitting, darkness). This makes a job interruptible, visible in the normal effect list and cancellable with `stop` for free (`CommonSkill.java:229-290`, `:828-845`).
- **Persistent jobs leave effects on things.** The bond, the brand and the library are copies of the same skill class attached to the animal, room, exit or item they change, with non-removable flags (`AnimalBonding.java:124-127`, `Branding.java:215-218`). The state travels with the object and is saved with it.
- **One class, many hosts.** `BookLoaning` works as a personal library on a player, a librarian NPC, a drop-box on a room, a shelf on an area, and an item (`BookLoaning.java:90-93`, `:736-774`). `deriveLibrarian()` supplies the NPC for whichever object it is on.
- **Cages as serialized creatures.** A caged animal is the XML of a mob stored inside a container item. Taming and training rebuild it with `unCageMe()`, change it and re-cage it (`GenCaged.java:135-170`). No separate creature save format is needed.
- **Emergent job titles.** `ArtisanalFocus` names the player after the crafts they actually use, scoring each family by proficiency and expertise (`ArtisanalFocus.java:195-248`).
- **Environment-keyed loot.** `AnimalTrapping` picks the trapped creature by the room's resource and terrain domain, weighted by a frequency column (`AnimalTrapping.java:184-206`).
- **Experience as the price of brewing.** Alchemy charges XP up front (`Alchemy.java:556-613`), so a brew costs progress as well as movement, scaled by spell and class level.
- **Data-driven item attributes.** One Blacksmithing recipe row sets the item class and a `misctype` flag (LOCK, LID, STATUE, SMOKE, LADDER and so on) and reinterprets a capacity field by class (`Blacksmithing.java:506-616`).

## Problems and cruft

Items 1 to 9 are behaviour that looks wrong on reading. None has been run; each should be checked before it is relied on.

1. **Coin payments clear the whole debt.** In `BookLoaning.executeMsg` the payback loop starts from `totalPaidDown = totalDue` (`:1204`), not from the amount the customer gave (`totalGiven`, `:1200`). It subtracts the full overdue balance, record by record, and so clears every charge whatever the amount (`:1206-1226`). The coins are then destroyed (`:1229`), change is only given when `totalGiven > totalDue` (`:1232-1233`), and the `"Your still owe @x1."` branch (`:1235-1239`) is effectively unreachable because `totalPaidDown` ends at zero.
2. **Dropping or throwing a book to pay back never deposits.** The drop and throw branches build the deposit as `CMClass.getMsg(msg.source(), this, msg.tool(), MSG_DEPOSIT, ...)` (`:1476-1477`, `:1494-1495`), with the ability itself as the target. `executeMsg` then tests `amITarget(merchantM)` and `amITarget(affected)` (`:1174`), and `amITarget` is an identity comparison on the target (`Common/DefaultMessage.java:571-573`), so the deposit branch is never taken.
3. **The daily maintenance pass does not run from `tick()`** (section 3.5): `CommonSkill.tick()` aborts for mob-attached copies (`CommonSkill.java:237-242`), and the block is gated on `TICKID_MOB` (`BookLoaning.java:633`), which area ticks never use (`StdArea.java:1282-1320`). Record purge and due-date updates happen only on `list`.
4. **Loan settings are lost on reload.** `text()` saves only the stock and the records (`BookLoaning.java:226-229`). On reload `setMiscText` resets `DUEDAYS`/`MAXDAYS`/`MAX` to 12, 24 and 2 (`:319-321`), and the `DUEDAYS` etc. parameters are read only when no records are present (`:334-336`).
5. **Overdue money settings are stubs.** `getOverdueCharge`, `getDailyOverdueCharge` and the percentage variants return 0 and ignore their setters (`:430-472`), so the `DAYCHARGE` parameter that the help text documents (`resources/help/arc_properties.ini:106-119`) does nothing.
6. **Budget fallback is unreachable.** `getFinalBudget()` returns the budget whenever it is non-null (`:599-603`), and it is initialised non-null (`:127`), so the area fallback in the else branch never runs.
7. **Selling a branded animal is vetoed, and the brand does not go.** `Branding.okMessage` returns false for a `TYP_SELL` whose tool is the branded animal, after showing `"The brand on @x1 fades away."` (`Branding.java:276-283`). Nothing removes the brand (`executeMsg`, `:293-313`), and the sale is blocked. The help text says the brand is lost on sale (`guides/refs/skills.html` near line 2600).
8. **Caged animal training can drop the new command.** `AnimalTraining.invoke` takes `skill` from the copy of the caged animal that it unlocks (`:304`, `:314-315`). `unInvoke` unlocks a fresh copy (`unCageMe` builds a new mob each call, `GenCaged.java:135-170`), and when that copy already has `Prop_LangTranslator` the `setStat` calls go to the `skill` object, not to the copy that gets re-caged (`AnimalTraining.java:156-164`, `:175-178`).
9. **Products vanish on abort, and a vetoed start is silent.** `Alchemy.unInvoke` only delivers the potion when not aborted (`Alchemy.java:210-211`), while the container was already destroyed at brew start (`:622`); a `stop` therefore loses the container and the potion, and the XP is already spent. In `Blacksmithing.invoke`, when the start message is vetoed and not a bundle, the function still returns true (`Blacksmithing.java:640-655`), and the metal was consumed earlier (`:471-480`).
10. **`AnimalTrading` recently-traded guard never trips.** It tests `recentlyTraded.contains(mob.Name())` (`AnimalTrading.java:180`) but records `M.Name()`, the animal's name (`:184`). The value is set to -1 on every sale (`:185`).
11. **Stat codes are cached statically across all classes.** `private static String[] codes` (`CommonSkill.java:999`) is filled on the first `getStatCodes()` call (`:1002-1015`) and returned to every subclass after that.
12. **Mana formula disagrees with its documentation** (section 1.6): code `25 + |n| * lowest` against documented `50 + n * lowest` (`coffeemud.ini:871`, `CommonSkill.java:560`, `:602-603`).
13. **Crafted item sent to the wrong room in one branch.** `dropAWinner()` tests `okMessage` and adds the item in room `R` (`CommonSkill.java:430-433`), but sends the message to `mob.location()` (`:435`). They differ when the crafter has moved.
14. **Alchemy list headers and a literal escape.** The header line labels all three columns "Spell" (`Alchemy.java:397`), and the powder heading is written as `"\\n\\r..."` (`:483`), which prints a literal backslash-n.
15. **Double "of" in taming text.** With more than one behaviour removed, the message reads "of 2 of its behaviours" (`AnimalTaming.java:128-131`).
16. **Dead code.** `Blacksmithing.mayICraft` tests `DoorKey` as true at `:181-182` and as false at `:193-194`, so the second test never runs. `AttributeTraining` has a non-monster branch that `invoke` already excludes (`:152-181`, `:244-248`), and a caged-animal branch for a `MOB` that is never a cage (`:185-188`). `AnimalBonding` repeats `!M.isMonster()` (`:182-183`). `AnimalHusbandry` sets `verb` twice (`:184-185`). `BookLoaning`'s constructor calls `isAutoInvoked()` for no effect (`:137`).
17. **Typos.** "praticing" (`AttributeTraining.java:110`, `:125`).
18. **Body piercing injures on failure.** `bodyPart` is set whenever the piercing starts, regardless of the roll (`BodyPiercing.java:383-388`), and `unInvoke` applies the injury whether or not `writing` is set (`:146-157`). Whether this is intended is not clear from the code.
19. **Multiplayer assumptions.** The library is one shared stock with per-borrower records and a post-office approval flow (`BookLoaning.java:803-824`, `:1129-1166`). Stop-and-retry on the shop state (`:917-922`) only works once another player has acted in the room.
20. **`CommonSkill.getDuration` reads `CMProps.get(mob.session())`.** For an NPC the session is null; whether `CMProps.get(null)` is safe was not checked.

## Takeaways for a Python SUD

**Borrow:**

- **The activity engine.** Model a job as an object with `duration_ticks`, a list of abort predicates (in combat, moved room, not able to act, fire gone) and a per-tick method that returns whether it is still running. Keep the progress messages (25 percent steps, "almost done" at four ticks left) and the "one activity at a time" rule. This maps cleanly to a Python `Activity` class driven by the game loop; a generator per activity also works.
- **Duration by level gap.** `ticks * (1 - 0.5 * (player_level - item_level) / max_level)`, minimum a fixed value, gives the same feel with a single formula. Drop the XTIME expertise term unless it earns its place.
- **Jobs as attributes on objects.** Store the bond, brand and loan as fields on the animal, room or item, not in a global table. The brand as `home_room` and `owner` fields on the animal, and the loan as a `records` list on the library object, are small dataclasses.
- **Recipes as rows.** Keep the Blacksmithing row idea (name, level, ticks, material, value, class, flags, capacity, spells) as a CSV or TOML table, but split the capacity column into named fields per item class.
- **Title from skills.** `title = max(families, key=mean_score)` with the proficiency-times-expertise score and the spread test is a cheap way to give the player a profession name.

**Simplify:**

- Drop mana and hit-point costs for crafting. A single time-or-energy cost is enough for one player.
- Replace the `Prop_LangTranslator` mechanism with a plain set of trained command words on the animal.
- Replace the cage XML round trip with a plain object, and the copy-on-use problem goes away (see Problems 8).
- Use a single loan policy: a fixed due period, a daily charge, and pay-on-return. Implement payback as `paid = min(amount_given, owed)` and reduce the records in order (the code's version is Problem 1).

**Drop, for a single player:** contributor masks, the post-office approval flow, `recentlyTraded`, the shared library shop state, "come back later" refusals, and all messages naming other players. The lottery reward for an item that vanishes is a neat fallback, but it only makes sense if something can remove the item.

**Rethink:** the breeding and pregnancy mechanic (`AnimalHusbandry`) is a multi-step social chain; in a text SUD it would be a plain timer with a message. The body-piercing damage and injury (`BodyPiercing`) is a design choice that should be made explicitly rather than inherited.

## Coverage

- **Read in full:** `CommonSkill.java` (all 1016 lines), `BookLoaning.java` (all 1776 lines), `Alchemy.java` (644), `Blacksmithing.java` (657; comment-only lines omitted in the view).
- **Read in full, compact view** (blank and brace-only lines removed): `AnimalBonding.java`, `AnimalTrading.java`, `AnimalTaming.java`, `AnimalTraining.java`, `AnimalHusbandry.java`, `AttributeTraining.java`, `Bandaging.java`, `Branding.java`, `BodyPiercing.java`, `BookEditing.java`, `BookNaming.java`, `ArtisanalFocus.java`, `Baking.java`, `BlackMarketeering.java`.
- **Read selected methods:** `Apothecary.java` (`mayICraft`, `invoke`, the antidote path); `AnimalTrapping.java` (`invoke`, `unInvoke`, the weighted selection); `Baiting.java` (`tick`, `unInvoke`, `getDuration`, the start of `invoke`); `Armorsmithing.java` (`tick` and declarations); `Boatwright.java` (`buildDoor` and declarations).
- **Not read:** the bodies of `Armorsmithing.invoke`/`autoGenInvoke`/`unInvoke` and `Boatwright.invoke`/`unInvoke`; the tail of `Baiting.invoke`; `AnimalTrapping.tick` (trivial).
- **Followed outside the bucket, only as far as needed:** `CraftingSkill.java` (`deadRecord`, line 146), `StdAbility.java` (`usageType`, `overrideMana`, `buildCostArray`, `unInvoke`, `canBeUninvoked`, `autoInvocation`), `Ability.java` (constants), `CMProps.java` (`TIME_TICK`, mana exceptions), `Items/Basic/GenCaged.java` (`unCageMe`), `Areas/StdArea.java` (effect ticks), `MOBS/StdMOB.java` (effect tick loop), `Common/DefaultMessage.java` (`amITarget`), `core/CMClass.java` (`getMsg`), `CharClasses/Scholar.java:225`. Declarations only for `EnhancedCraftingSkill.java`, `Cooking.java`, `Merchant.java`, `GatheringSkill.java`, `SpellCraftingSkill.java`.
- **Documentation consulted:** `guides/refs/skills.html` (entries near line 2600, for the documented use costs), `resources/help/arc_properties.ini` (lines 106-119, 216-230), `coffeemud.ini` (lines 864-877).
- Verified against the code: the manifest (22 files, 8638 lines), the constants at `CMProps.java:681`, `Ability.java:622-689`.
