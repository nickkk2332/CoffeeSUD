# Special mobs: shopkeepers, postmen, rideables, proxies and monsters

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b148` (MOBS [part 4/4: StdMobWrapper .. Zombie]) · 26 files · 6148 lines of Java.

## Summary

This bucket finishes the MOBS package. It holds the special-purpose NPC base classes that extend `StdMOB` (`StdShopKeeper`, `StdPostman`, `StdRideable`), two proxy classes that stand in for other mobs and rideables (`StdMobWrapper`, `StdRideableWrapper`), a trainer (`Teacher`) and twenty small monster classes. The shop and postal classes carry the real mechanics: a shop has a cash budget that limits what it will pay players and restocks from saved stock on a tick timer, and a postman runs a mail system kept in the player database, with postage, holding fees, COD and forwarding, delivered once per game day. Rideables are mounts with a seat list, a basis enum that governs where they may go, and weight and capacity limits enforced as message vetoes. The proxies are inert stand-ins that the planar spells use to show other occupants in a shadow room. The monsters are almost pure data: each constructor sets name, stats, race, hit points and behaviours, and only the Wyvern and the Umber Hulk add a periodic special attack.

## Key files and types

Paths are relative to `com/planet_ink/coffee_mud/` (the package root), as in the sibling chapters. Line counts come from the manifest.

| File or class | Role | Lines |
|---|---|---|
| `MOBS/StdShopKeeper.java` | Shop layer over StdMOB: sale message handlers, cash budget, restock tick, per-area defaults | 655 |
| `MOBS/StdPostman.java` | Postal branch (extends StdShopKeeper): mail store, delivery tick, postage, COD, boxes, forwarding | 1150 |
| `MOBS/StdRideable.java` | Mount or vehicle base (extends StdMOB): riders, ride basis, mount and room rules, combat adjustments | 901 |
| `MOBS/StdMobWrapper.java` | Inert proxy for any MOB: delegates descriptive calls, ticks and vetoes nothing | 1559 |
| `MOBS/StdRideableWrapper.java` | Proxy for a Rideable (extends StdMobWrapper): delegates ride queries | 237 |
| `MOBS/Teacher.java` | "Cornelius" trainer: very high level, adopts the class of its last visitor | 111 |
| 20 monster classes, `MOBS/Wyvern.java` to `MOBS/WhiteDragon.java` | Stat-and-race NPCs; two have periodic attacks (catalogue below) | 46 to 134 each, 1535 in total |

Read alongside, outside the bucket: `core/interfaces/ShopKeeper.java:45-126` (deal categories and conflict table), `core/interfaces/Rideable.java:44-55` (ride basis enum), `Libraries/CoffeeShops.java` (price and sale evaluation, signatures only), `Libraries/CMMap.java:1635-1642` and `1669-1690` (`getStartArea`, `areaLocation`), `Libraries/Dice.java:80-96` and `398-406` (`rollHP`, `roll`).

The shop's sold-category bit layout, the Gen shop XML and price arithmetic are covered in `actors/b173-mobs-the-mob-model-stdmob-and-generic-npc-classes.md` (section 13) and are not repeated here.

## How it works

### 1. Shopkeepers: StdShopKeeper

`StdShopKeeper` extends `StdMOB` and implements `ShopKeeper`. Its own state is the stock (`shop`, `MOBS/StdShopKeeper.java:50`), a sold-category mask (`whatIsSoldMask`, 51), a restock timer (`invResetRate` and `invResetTickDown`, 52-53), a cash budget (`budgetRemaining`, `budgetMax` and `budgetTickDown`, 54-56, with `budgetTickDown` starting at 2), a devaluation rate and currency (57-58), item pricing adjustments (59), a zapper mask for the items it deals in (`itemZapperMask`, 60) and the view types it shows (`viewTypes`, 61). The constructor (65-87) makes a plain shopkeeper: name "a shopkeeper", good alignment, weight 150, intelligence 16, charisma 25, hit points 1000 and no wimpy threshold.

**Sales are message handlers.** Each transaction is a `CMMsg` (see the brief). `okMessage` (285-347) asks the shopping library whether the deal is acceptable. A sell-to from a player (VALUE and SELL) goes to `pawnEvaluation` with the remaining budget (292-300). A buy or view goes to `sellEvaluation` (327-333). A bid is refused with "I'm afraid my prices are firm." (302-306). `executeMsg` (349-491) does the work:

- **GIVE** (357-375). A gift becomes stock only when the giver has ORDER security or room privileges (or the monster command flags) and the shop deals in that item or sells inventory only. The shop then says "Yes, I will now sell @x1." (367) and adds one copy to its stock (368). Anything else falls through to the normal give.
- **VALUE** (376-387). The shop announces its pawn price: "I'll give you @x1 for @x2." (384).
- **SELL** (388-406). A player sells to the shop. The shop pays (`transactPawn`, 393) and reduces `budgetRemaining` by the amount paid (396). For generic shops the stock is re-serialised (`text()`, 402).
- **VIEW** (407-421). If the item is in stock the shop describes it, using the view types.
- **BUY** (422-469). The product is taken from stock (`removeSellableProduct`, 438) and then paid for. Three kinds of product are handled: items (`purchaseItems`, 446), mobs such as pets or slaves (`purchaseMOB`, 452), and abilities (`purchaseAbility`, 460). Training is sold this way, so a trainer is a shop whose product is an ability. The buy path does not change the budget.
- **LIST** (470-483). The stock list is composed for the viewer. A `LIMIT` parameter in the prejudice text caps its length (476).

**Cash budget.** `setBudget` parses an amount and a period and sets `budgetTickDown` to 0 (`MOBS/StdShopKeeper.java:567-572`), which forces a refill on the next mob tick. `doBudgetReset` (228-241) sets `budgetRemaining` to the amount and sets the next refill at `getTicksPerMudHour()` times the hours in the period (238). Ticks per mud hour are `MILLIS_PER_MUDHOUR / TIME_TICK`, which is 600000 / 4000 = 150 at defaults (`core/CMProps.java:681-682`, `3539-3542`). So a budget that refills every N mud hours refills every 150N ticks. Both refills run only for generic shops (248-256). Until a budget is set, `budgetRemaining` is effectively unlimited (`Long.MAX_VALUE / 2`, 54-55 and 231). The budget caps what the shop pays players: only the pawn check reads it (298), and only sell-to reduces it (396). Purchases from the shop never refill it.

**Restock.** `tick` (243-258) calls `doInventoryReset` (200-226) and `doBudgetReset` (254-255) only when `isGeneric()` is true (248-250). `GenMob` returns true (`MOBS/GenMob.java:60-63`); `StdMOB` returns false (`MOBS/StdMOB.java:780-783`). So only data-built shops restock. The timer is `getFinalInvResetRate()` (594-600): the shop's own rate, or the area's when it is zero. `setInvResetRate` zeroes the countdown (608-613), so the next tick restocks. A reset empties every shelf (209) and then re-populates from `miscText`, which is the XML written by the last `text()` call (210-223; `CoffeeMaker.populateShops`, `Libraries/CoffeeMaker.java:3105`). A restock therefore restores the stock as it was at the last serialisation, not the original template. The serialisation includes the sold mask and the store (`Libraries/CoffeeMaker.java:960-978`).

**Area defaults.** Most per-shop settings fall back to the start area when they are empty or zero (`getFinal*`, 493-625): prejudice factors (494-499), the ignore mask (514-519), item pricing (533-539), the budget (554-559), the devaluation rate (575-579), the restock rate (595-599) and the currency (628-632). The fallback chain ends in `getStartArea` (190-198), which has a defect (see Problems).

**Other shopkeepers.** `StdBanker`, `StdLibrarian` and `StdCraftBroker` extend `StdShopKeeper` (`MOBS/StdBanker.java:38`, `MOBS/StdLibrarian.java:42`, `MOBS/StdCraftBroker.java:47`); they are in other buckets. `StdPostman` is the mail variant below. The data-driven versions are `GenShopkeeper` (`MOBS/GenShopkeeper.java:38`), whose `text()` (89) writes the whole mob, stock included, into `miscText`, and `GenPostman` (`MOBS/GenPostman.java:36`, `text()` at 63).

### 2. The postman: StdPostman

**Defaults and identity** (`MOBS/StdPostman.java:47-52`): minimum postage 1.0, postage per pound 1.0, holding fee per pound per mud month 1.0, a 50.0 fee to open a box, and mail held for at most 40 mud months. The postal chain is the shop's `miscText` (`postalChain`, 157-161). The branch is the room ID (`postalBranch`, 169-173). The sold mask is forced to either the postman or the clan postman category (`setWhatIsSoldMask`, 147-155).

**Storage.** Mail is not kept in memory. Each parcel is a row in the player-data store (`CMLib.database().DBCreatePlayerData`, 206-214). A row has an owner, a section and a key. For a parcel dropped at this office the owner is the chain name (the outgoing queue) and the key is the branch, a semicolon and the item's string form plus a random number (206-214). For a box the key is the branch and "/" and the forwarding name (274-278). The parcel payload is `from;to;time;cod;classID;xml`, parsed by `parsePostalItemData` (360-395). A malformed payload returns null (368-371).

**Dropping a parcel (GIVE, 659-788).** A player with a session who gives an item to the postman hands it over with `autoGive` (643-649, 666-668), a drop followed by a get. A prompt chain then asks "Address this to whom?" (672-685) and checks the name against players and clans (689-690). It then asks "Would you like to P)ay this now, or be C)harged on delivery?" (704). Paying takes the postage at once (761-764). Choosing C asks for a COD amount (719-745), which is validated and converted to coins (744-745). Either way the item is then destroyed and its record is queued (746-748, 762-764). A timeout, a bad name or a bad amount returns the item to the sender (683, 710, 730, 740, 757, 773). If the postman does not end up holding the item, it says "I can't seem to deliver ..." (779). After the postal steps, a privileged giver returns early (782-786), which skips the shopkeeper's own give handling (787).

Postage (`MOBS/StdPostman.java:412-427`):

- chargeable weight is the item's weight minus one pound (or zero);
- postage is `minimumPostage + postagePerPound * chargeableWeight`.

**Delivery (tick, 540-641).** The tick runs for each chain and branch at most once per wait period. The last run time is kept in a static map (`postalTimes`, 54; check 553-563). The first wait is a sentinel value of 10038 ms (533). After that the wait is `hoursInDay * MILLIS_PER_MUDHOUR` (536), which is one mud day. Each run does four things.

1. It takes every queued row for this branch and deletes it from the store (572-580). The rows are deleted before they are processed.
2. For each parcel it finds the recipient's branch with `findProperBranch` (586). If there is none, it tries the sender (598). If neither works, nothing re-queues the parcel, so it is lost.
3. It adds the parcel to the recipient's box at that branch (593). If that fails, the parcel is bounced to the sender's box, recorded as from the intended recipient and addressed to "POSTMASTER" (604).
4. It moves parcels older than the holding limit into the shop's stock (611-637). The age is counted in mud months (621-625, with `millisPerMudMonth = daysInMonth * millisPerMudHour * hoursInDay`, 622). Unclaimed goods go to `getShop().addStoreInventory` (631) unless they are ArchonOnly.

**Routing (474-528).** A player's box rows give, for each branch, either the player's own name (a box there) or another branch ID (a forward). `getBranchPostableTo` (457-472) accepts a direct box, or follows one forward and checks for a box there. This is one hop as read. If the recipient has no box anywhere, a box is created at their home area's branch (486-495).

**Holding and COD.** The holding charge is `floor(age in mud months) * holdFeePerPound * chargeableWeight` (429-443). The total charge on pickup is postage plus the COD amount plus holding, when a COD amount exists (445-455). Withdrawal is refused when the recipient cannot afford the total (`MOBS/StdPostman.java:1099-1104`). On pickup the charge is taken (811). If there was a COD amount, that amount is mailed back to the sender as coins, queued through the same outgoing mechanism (814-819).

**Boxes and speech (TYP_SPEAK, 843-956).** The postman responds to words the player says to it.

- "open" creates a box (847-880). It costs the box fee when the fee is above zero (858-872), and is free otherwise (874-878).
- "close" removes an empty box (882-900). A box with pending parcels cannot be closed (894).
- "forward <area>" re-points the box at another branch (902-954), resolving the area with `CMLib.map().findAreaStartsWith` (925). "forward stop" removes forwarding (914-922).

**Withdrawal and listing.** Withdrawal (`MOBS/StdPostman.java:789-834`, check 1075-1106) finds the parcel by exact match with `findExactBoxData` (340-358). If the player is married to a liege, that liege's box is also searched (797-798, 1092-1093). The listing (957-1021) prints a table with COD, sender, time sent and item columns, and footers that quote the postage rates (1010-1017).

**Clan variant.** When the postman deals in clan postage (DEAL_CLANPOSTMAN), `getSenderName` (176-199) uses the player's clan when the player has the right clan function (180). Otherwise it refuses with "I'm sorry, you aren't authorized by your clan to do that." (188) or "I'm sorry, I only deal with clans." (193).

**Lifecycle.** A retiring player's box is emptied (1027-1028). The postman cannot be expired out of its own room (1036-1039).

### 3. Rideables: StdRideable and StdRideableWrapper

`StdRideable` extends `StdMOB` and implements `Rideable` (`MOBS/StdRideable.java:35`). Its state (43-63) is a ride basis (default `LAND_BASED`), a rider capacity (default 2), a list of riders (45) and six customisable verb strings. Each verb has a default: "ride(s)", "follow(s)", "mount(s)", "dismount(s)", "mounted with", "being ridden by", "riding on", "is pulled", "pulling along". The constructor (65-74) makes a horse: name "a horse", race "Horse", weight 700. Horse, Pegasus, PegasusGreater and GenRideable extend it in other buckets.

**Ride basis** (`core/interfaces/Rideable.java:44-55`). The enum has ten values: LAND_BASED, WATER_BASED, AIR_FLYING, FURNITURE_SIT, FURNITURE_SLEEP, FURNITURE_TABLE, ENTER_IN, LADDER, WAGON and FURNITURE_HOOK. Furniture, ladders and entry points are not mobile (`isMobileRideBasis`, 113-129). A WAGON is pulled rather than ridden (459-460). Water and air bases add a swimming or flying disposition (`recoverPhyStats`, 254-258).

**Mounting is a chain of vetoes in `okMessage` (506-823).** A mount attempt (TYP_MOUNT, 579-678) is refused, in this order, when:

- the mount is in combat ("won't let you do that right now", 600-604);
- the basis is a furniture hook (605-608);
- the mount is itself riding the would-be rider (610-617);
- the rider is a mob that is neither bound nor held (595-599). A rider already on the mount is simply re-attached (586-594);
- the rider is more than five times the mount's weight: `basePhyStats().weight()*5 < rider weight` gives "... is too small for ..." (644-648). A 700-weight horse therefore takes riders up to 3500;
- the seats are full: `numRiders() >= riderCapacity()` gives "No more can fit on ..." (650-658);
- on a player-controlled mount (where `playerStats` is set), the mount's race must allow ride-class riders, the rider must not be ignored by the mount, and the rider must be in the mount's group (665-674).

A Rideable item can be mounted only if its basis is WAGON (629-635). A wagon that is also a land-based NavigableItem skips the weight check, because it "handles its own pullers" (636-641).

**Moving with riders (TYP_ENTER, 679-750).** Land mounts may not enter air or deep-water rooms (692-696). Other indoor rooms are allowed when the room is large (`maxRange > 4`), heavy (room weight above 2), or when the mount weighs under 200, is not following an item, and carries no item riders (698-708). Water mounts that cannot breathe water need a water-surface room at either end and may not enter an underwater room. Mounts that can breathe water need a watery room at either end. Neither kind may enter an air room (712-729). Flying mounts are not restricted here (710-711). A sitting rider cannot move the mount (738-742). A player mount whose race does not allow ride-class riders cannot carry anyone (745-749).

**Combat while mounted (affectPhyStats, 291-315).** When the rider is mounted and in melee range (`rangeToTarget` 0), its own attack adjustment and damage are subtracted out (302-305). The attack therefore uses the mount's numbers. Abilities on the mount that are flagged `bubbleAffect()` apply to every rider (307-312 for physical stats, 265-273 for character stats and 280-288 for state).

**Hands and malice (780-821).** A rider cannot use its hands on anything it cannot reach, and gives the message "You cannot do that while ..." (780-793). A rider cannot attack its mount or the mount's rider, and the mount cannot attack its own rider (794-821, messages at 800 and 813).

**Ending a ride.** Death, quitting and panic dismount every rider (`killMeDead` 83-96, `destroy` 98-111, `executeMsg` 884-899). Sitting or lying down while already mounted re-attaches the rider to the mount (546-578).

**StdRideableWrapper** (`MOBS/StdRideableWrapper.java:36`) is the proxy for a Rideable. It keeps the wrapped Rideable in a field (44, set at 46-54). Ride queries delegate to that field, for example `rideBasis` (68-72, which defaults to `FURNITURE_SIT` when nothing is wrapped). Its mutators, including `addRider` and `delRider`, do nothing (74-77, 108-116). Section 4 covers its use.

### 4. Proxies: StdMobWrapper and StdRideableWrapper

These are proxies for whole creatures. `StdMobWrapper` implements `MOB` and `CMObjectWrapper` (`MOBS/StdMobWrapper.java:41`). `setWrappedObject` (61-70) keeps a reference to the real MOB and copies its physical, character and state stats into the wrapper's own base objects (66-68). After that:

- **Descriptive calls pass straight through.** `name`, `displayText`, `description`, `image`, tags, `titledName`, `healthText`, `amDead`, `amActive` and similar delegate to the real MOB (for example 91-106, 1001-1017, 1087-1091). They are live, so the proxy always shows the real name.
- **Stats are a snapshot.** `phyStats()` (383-386), `charStats()` (1054-1057) and `curState()` (1296-1299) return the copies taken at wrap time. `recoverPhyStats` (389-391) and `recoverCharStats` (1060-1062) do nothing.
- **It is deaf and mute.** `executeMsg` (308-310) and both `tell` methods (1137-1145) do nothing. `okMessage` returns true (313-316). `tick` returns false (257-260), so no behaviour, effect or script runs.
- **It holds nothing.** Effects, behaviours, items, abilities and followers are all empty (for example `numItems` 567-570, `numAllEffects` 1480-1483, `personalEffects` 1491-1494).
- **It has its own location.** `location()` returns the proxy's own field (1397-1400), so the proxy can stand in a different room (`setLocation`, 1409-1412).
- **It claims to be a monster.** `isMonster` is true (1159-1162) and `isPlayer` is false (1020-1023). `sameAs` compares the wrapped objects (210-213).

The planar spells use these proxies. `Spell_PlanarBubble` (`Abilities/Spells/Spell_PlanarBubble.java:257-292`) and `Spell_PlanarDistortion` (`Abilities/Spells/Spell_PlanarDistortion.java:255-304`) build a shadow copy of a room for the people inside the bubble. Each item gets an `Items/Basic` wrapper of the matching kind (`StdPortalWrapper`, `StdRideableWrapper`, `StdContainerWrapper` or `StdItemWrapper`, Bubble 265-274). Each inhabitant gets a `StdRideableWrapper` or `StdMobWrapper` from `CMClass.getMOB` (Bubble 286-292). Distortion copies the planar inhabitants instead (285-293). The shadow room is rebuilt only when a hash of item and occupant names, display texts and dispositions changes (Bubble 257). The MOB `StdRideableWrapper` here and the item `StdRideableWrapper` in `Items/Basic` share a name and a purpose but are different classes.

### 5. Teacher

`Teacher` (`MOBS/Teacher.java:35-93`) is the one NPC in this bucket with no combat role. Its constructor sets the name "Cornelius, Knower of All Things" (46), money 100 (50), wimpy at 200 (52) and three behaviours: MOBTeacher, MudChat and CombatAbilities (54-62). Every base stat is 25 (64-65), the race is Human (66), and the attribute NOTEACH is cleared (83), so it will teach. For each class that the login library reports as available, it sets class level 31 and adds 31 to its own level (70-81). Its level is therefore `1 + 31 * (number of available classes)`. Hit points, mana and movement are 4999 each (85-87), and armour is -500 (82).

Its one unusual rule is in `okMessage` (95-110). When another mob enters its room, the teacher sets its current class to that mob's class, provided it has levels in that class (`setCurrentClass`, 105). Since every available class has level 31, the teacher always adopts the class of the last visitor.

### 6. Monsters: one constructor recipe

Seventeen of the twenty monster classes extend `StdMOB` directly. The exceptions are Zombie (which extends `Undead`), WhiteBear (`BrownBear`) and WhiteDragon (`Dragon`). Nearly all follow the same constructor, typified by `MOBS/Wyvern.java:45-80`:

1. name, description, display text and alignment (`CMLib.factions().setAlignment`);
2. money and weight, often randomised per instance, for example `2000 + |rand % 550|`;
3. intelligence, strength and dexterity as base stats;
4. race, by `CMClass.getRace(name)` and then `startRacing(this,false)`, which grants the race's abilities and body data (`Races/StdRace.java:764-791`);
5. damage, speed, level, armour and any flying or swimming disposition;
6. hit points, usually `CMLib.dice().roll(level, 20, level)`, which is `level` twenty-sided dice plus `level` (`Libraries/Dice.java:398-406`), so between 2 times and 21 times the level. Exceptions: Swordfish uses d11 (`MOBS/Swordfish.java:63`), Walrus d15 (`MOBS/Walrus.java:63`), Whale d21 (`MOBS/Whale.java:63`), and Toad calls `rollHP(level, 20)` (`MOBS/Toad.java:62`). That call uses the configurable formula `FORMULA_NPCHITPOINTS` (`Libraries/Dice.java:45`, `80-96`);
7. recovery of the max state and the stats.

Behaviours are attached by name with `CMClass.getBehavior`: Aggressive for the Wyvern (74), the Tiger (69) and the StoneGiant (67); Mobile for the Worm (71); CombatAbilities for the Worm only when it has abilities (78-79). In this design the race holds most of what makes a creature distinct, and the class holds the numbers.

**Two monsters add periodic specials.** Both use a countdown in the mob tick, so they are the simplest form of monster ability in the codebase.

- **Wyvern** (`MOBS/Wyvern.java:82-132`). Every 5 ticks it stings if it is in combat (87-91). At the default 4-second tick (`core/CMProps.java:681`) that is every 20 seconds. The sting rolls 0 to 99 and succeeds below 20 (111-114), which is about a 20 per cent chance. A hit sends "sting(s)" (117). If `msg.value()<=0` after sending, the Poison ability is invoked on the victim (122-127). The Wyvern gets +100 to poison saves (97-102).
- **UmberHulk** (`MOBS/UmberHulk.java:78-115`). Every 3 ticks, about 12 seconds, `confuse()` runs. It casts Spell_Confusion with itself as the caster and no target (113). Spell_Confusion is a malicious spell (`Abilities/Spells/Spell_Confusion.java:61-63`). With no target text, a malicious spell targets the current victim, and only while the caster is in combat (`Abilities/StdAbility.java:1034-1060`). So the hulk confuses its opponent every 12 seconds of a fight and does nothing outside one.

**Undead family.** `Undead` (`MOBS/Undead.java:43-73`) sets race Undead (54-55), makes it see in the dark (63), clears its disposition to disable infrared (62), and gives it Skill_AllBreathing as a permanent effect (67). `Zombie` extends `Undead` (`MOBS/Zombie.java:35`), sets level 2 (56) and race Zombie (52-53). The race classes of the same names are in `Races/Undead.java:35` and `Races/Zombie.java:35`.

## Content worth mining

### Monster catalogue

All are `StdMOB` subclasses unless noted. "Dmg" is the damage field as set, and "+12" means added to the default. "HP" is the hit-point roll. "default" means the constructor does not set the value. Race names are the strings passed to `CMClass.getRace`.

| Class (`MOBS/`) | Race | Lvl | Weight | Armour | Dmg | Speed | HP | Behaviour and notes | Lines |
|---|---|---|---|---|---|---|---|---|---|
| Wyvern | Wyvern | 7 | 2000-2549 | 70 | 16 | 2.0 | 7d20+7 | Aggressive; flying; periodic sting with Poison (82-132) | 134 |
| UmberHulk | UmberHulk | 8 | 350 | 60 | +12 | 2.0 | 8d20+8 | Evil; +20 to hit; sees dark and infrared; confuses victim every 3 ticks in combat | 117 |
| Teacher (see section 5) | Human | 1 + 31N | 150 | -500 | default | default | fixed 4999 | MOBTeacher, MudChat, CombatAbilities | 111 |
| Worm | Worm | 1 | 0-1 | 90 | 10 | 1.0 | 1d20+1 | Mobile; intelligence, strength and dexterity 1; combat abilities only if it has some | 81 |
| WildEagle | Eagle | 2 | 1-15 | 90 | 2 | 3.0 | 2d20+2 | Flying; dexterity 17; no behaviour | 78 |
| Tiger | GreatCat | 5 | 300-354 | 70 | 10 | 2.0 | 5d20+5 | Aggressive; strength 13, dexterity 17 | 77 |
| Troll | Troll | 8 | 350 | 60 | +5, +20 hit | 3.0 | 8d20+8 | Evil; intelligence 8-11; sees dark and infrared; no behaviour; unused fields | 77 |
| Wolf | Wolf | 4 | 50-104 | 80 | 6 | 1.0 | 4d20+4 | Neutral; no behaviour; not aggressive | 77 |
| Wolverine | GiantRat | 4 | 20-34 | 80 | 6 | 1.0 | 4d20+4 | Race string is a giant rat, not a wolverine (60) | 77 |
| StoneGiant | none set (StdMOB default Human) | 14 | 8000-9000 | 0 | 20 | 1.0 | 14d20+14 | Aggressive; evil; strength 20 | 75 |
| Turtle | Turtle | 1 | 20-74 | 90 | 4 | 0.5 | 1d20+1 | Slow; wimpy at 2 | 75 |
| Undead | Undead | 1 | 30 | 90 | 8 | 1.0 | 1d20+1 | Evil; sees dark; breathing as a permanent effect | 75 |
| Vulture | GreatBird | 1 | 20-24 | 90 | 4 | default | 1d20+1 | Intelligence 1; wimpy at 2 | 74 |
| Swordfish | Swordfish | 1 | default | 90 | 6 | default | 1d11+1 | Hit points use d11 | 71 |
| Walrus | Walrus | 1 | default | 90 | 6 | default | 1d15+1 | Hit points use d15 | 71 |
| Whale | Whale | 1 | default | 90 | 4 | default | 1d21+1 | Hit points use d21 | 71 |
| StoneGolem | StoneGolem | 25 | default | -100 | 4 | default | 25d20+25 | Intelligence 1 | 70 |
| Toad | Frog | 1 | default | 90 | 6 | default | formula (`FORMULA_NPCHITPOINTS`) | Hit points from the configured formula (62) | 70 |
| Zombie (extends Undead) | Zombie | 2 | 30 | 90 | 8 | 1.0 | 2d20+2 | Inherits Undead's senses and breathing | 68 |
| WhiteBear (extends BrownBear) | inherited | inherited | inherited | inherited | inherited | inherited | inherited | Only name and description are set (46-48) | 51 |
| WhiteDragon (extends Dragon) | from Dragon | from Dragon | from Dragon | from Dragon | from Dragon | from Dragon | from Dragon | `super(WHITE)` (44), where WHITE is 0 (`MOBS/Dragon.java:68`) | 46 |

### Postal fee schedule (defaults)

| Parameter | Default | Where |
|---|---|---|
| Minimum postage | 1.0 | `MOBS/StdPostman.java:47` |
| Postage per pound (above the first) | 1.0 | 48 |
| Holding fee per pound per mud month | 1.0 | 49 |
| Fee to open a box | 50.0 | 50 |
| Mud months before unclaimed mail goes to stock | 40 | 51 |
| Delivery period | one mud day | 533-536 |

Parcel charge on pickup is postage, plus COD if any, plus holding. The COD amount is returned as coins in the mailbox.

### Messages worth reusing (short, paraphrased where long)

- Shop: "I'm afraid my prices are firm." (`MOBS/StdShopKeeper.java:304`); "Yes, I will now sell @x1." (367); "I'll give you @x1 for @x2." (384); "Too bad you can't afford it." (`MOBS/StdPostman.java:862`).
- Postal: "You can't afford postage." (756); "I'll deliver that for ya right away!" (747); "The COD amount of @x1 has been sent back to @x2." (818); "That box has pending items which must be removed first." (894); "Ok, mail will now be forwarded to our branch in @x1." (950).
- Riding: "@x1 is too small for @x2." (646); "No more can fit on @x1." (654); "You cannot ride @x1 that way." (735); "@x1 is far too burdened!" (747); "You cannot crawl while ..." (740).

### Ride verbs as a vocabulary

The default verbs in section 3 (`MOBS/StdRideable.java:53-63`) give a text game a ready set of words for a rider, a mount, a pulled cart and a seat, including the "is pulled" and "pulling along" pair for wagons.

## Notable design ideas

1. **Cash as a budget.** A shop pays players only from a budget that refills on a schedule (`MOBS/StdShopKeeper.java:228-241`, 396). A merchant that runs out of money is a cheap and effective economic limit.
2. **One queue for all mail.** Outgoing parcels, postage deliveries and COD refunds all go through the same store and the same delivery tick, keyed by the chain name (`MOBS/StdPostman.java:566`, 746, 762, 819). One mechanism covers three uses.
3. **Abandoned goods enter the economy.** Mail left for longer than the holding limit becomes the postman's stock (631). Nothing is lost, and the world absorbs the item.
4. **Fees by mud month.** Holding costs accrue per in-game month, not per tick (429-443), so they follow the calendar of the game rather than real time.
5. **Rides as vetoes.** Each riding rule is one check in `okMessage` with its own message (506-823), and the room table is one `switch` on the basis (690-730). The rules are easy to read and to change.
6. **Deaf and mute proxies.** The wrapper answers only "what are you called and what do you look like" and nothing else (`MOBS/StdMobWrapper.java:257-316`). That is exactly what a shadow room needs.
7. **Trainers that adopt the visitor's class.** The teacher's class follows whoever last walked in (`MOBS/Teacher.java:95-110`). It is a quirk, but a neat way to keep a trainer relevant.
8. **Periodic specials as countdowns.** The Wyvern and the Umber Hulk show that a monster's signature move can be a counter in the tick (`MOBS/Wyvern.java:82-95`, `MOBS/UmberHulk.java:78-90`).

## Problems and cruft

1. **A bad mail row loses a batch.** `StdPostman.tick` deletes every queued row first (`MOBS/StdPostman.java:572-580`) and then reads `parsed.get(v).to` (584-585). `parsePostalItemData` returns null for a malformed row (368-371), so one bad row throws a NullPointerException after the batch is already gone. Verified by reading; not reproduced.
2. **Shop area fallback is a no-op.** `StdShopKeeper.getStartArea` (190-196) calls `CMLib.map().areaLocation(this)` and discards the result. `areaLocation` is a pure function that returns an Area (`Libraries/CMMap.java:1669-1690`), so the call does nothing. The fallback is then the first area in the world (196). A shop with no start room takes its defaults from an arbitrary area.
3. **Poison condition.** `MOBS/Wyvern.java:122` invokes poison only when `msg.value()<=0`. Whether `value()` holds damage at that point is unverified. If it does, the condition is inverted.
4. **Sender argument.** `MOBS/Wyvern.java:121` calls `location().send(target,msg)`, passing the victim as the sender. The postman passes itself (`MOBS/StdPostman.java:646`). The effect is unverified.
5. **Wolverine race.** `MOBS/Wolverine.java:60` uses the race "GiantRat", which looks like a copy-paste.
6. **StoneGiant has no race.** It sets none, so it gets StdMOB's default race Human (`MOBS/StdMOB.java:166-170`). Any race-keyed rule would treat it as a human. Impact unverified.
7. **Zombie applies two races.** `MOBS/Undead.java:54-55` applies race Undead, and `MOBS/Zombie.java:52-53` applies race Zombie. `startRacing` grants abilities on each call (`Races/StdRace.java:791`), and the Zombie constructor does not undo the Undead grants. Whether they remain is unverified.
8. **Null return from a proxy.** `StdMobWrapper.getRideBuddies` returns null (`MOBS/StdMobWrapper.java:1497-1500`), while `StdRideableWrapper` returns its argument (232-236). `Behaviors/Nanny.java:598` uses the return value directly. Plain wrappers are rarely message sources, so the impact is unverified.
9. **Dead and duplicate code.** `MOBS/UmberHulk.java:92-98` (`addNaturalAbilities`) does nothing. `MOBS/Troll.java:42-43` declares an unused `randomizer` and `regDown`. `MOBS/StdShopKeeper.java:230` and 236 both set `budgetTickDown` to 100. `MOBS/StdRideable.java:244-248` overrides `displayText` only to call super. `MOBS/StdRideable.java:655-656` keeps a commented-out message. Lines 53-63 of `StdRideable` build their strings when the class loads, through `CMLib.lang().L`.
10. **Stock can be stale.** The buy path (`MOBS/StdShopKeeper.java:422-469`) does not call `text()`, which runs only after gives and sells (370, 402). A purchase is therefore not written back until the next sale or gift. A restock in between brings the item back. How often database saves call `text()` is unverified.
11. **Budget only falls.** Purchases never refill `budgetRemaining`, because no code in the buy path touches it. Whether a shop should regain budget from sales to customers is a design question, not a settled behaviour.
12. **Unread row value.** `MOBS/StdPostman.java:277` writes "50" as the data of each box row. `getOurOpenBoxes` reads only keys (259-263), so the value is never read.
13. **Parcel keys from object strings.** `MOBS/StdPostman.java:207-208` builds keys from the item's string form plus `Math.random()`. Uniqueness is probabilistic and the key is not a stable identifier.
14. **Magic numbers.** The postal sentinel 10038 (`MOBS/StdPostman.java:533`), the 200-weight limit (`MOBS/StdRideable.java:699`), the room-weight threshold of 2 (689) and the 5-times weight ratio (644) are unexplained in code.
15. **Session-bound dialogs.** The postal drop uses session prompt callbacks (`MOBS/StdPostman.java:672-776`), and shop messages go to `mySession` (`MOBS/StdShopKeeper.java:397-398`, 462-463). Both assume a connected player.
16. **Process-wide state.** `MOBS/StdPostman.java:54` keeps `postalTimes` as a static map shared by every postal branch in the process.
17. **Multiplayer concepts.** Liege and marriage checks (797-798, 969-971), offline recipients via `getLoadPlayer` (477, 694), clan postage and forwarding between branches all assume many players.

## Takeaways for a Python SUD

- **Shops as small dataclasses.** Model a shop as `stock`, `cash`, `cash_refill` (amount and period in days) and `restock_every`, with `sells` as a Python set of categories. Pricing is a pure function. Sell-to subtracts from `cash`, buying removes stock, and nothing else is needed. Drop the bit-packed mask and the conflict table (use explicit sets), and drop the XML round trip. Store the state directly and save it with the game.
- **Restock on game days, not mob ticks.** A single-player world can refill a shop when the day changes, and a shop's cash can refill on the same event.
- **Mail as a list of parcels.** A `Parcel` holds sender, recipient, arrival day, COD, weight and item. A daily `deliver(day)` moves due parcels into the recipient's pickup list. Keep the postage formula, the COD flow (coins mailed back on pickup), the holding fee per game month, and the expiry into shop stock after N months. Drop branches, forwarding, clans and offline recipients.
- **Mounts with a rule function.** A `Mount` with capacity 2, a 5-times weight limit and a `Basis` Enum. `can_mount(rider)` returns a pair of result and reason, so the reason strings can be reused. Room rules become one function, `can_enter(mount, room)`. While mounted and in melee, use the mount's attack profile.
- **Monsters as data.** One table or dict per monster with `level`, `hp_dice` (`dice(level, die, level)` in Python), stats, race traits and behaviour names. Periodic specials become a pair of (every N turns, action), so the Wyvern's sting is a data entry and not a class.
- **Proxies only for display.** For a "seen through" effect, use a small view object that forwards name and description to the real object, freezes its stats at creation, and ignores all actions. Python `__getattr__` makes that a few lines.
- **Trainers.** Keep the trainer's offer list tied to the player's own class and drop the adopt-the-visitor rule, which only makes sense with many players.
- **Rethink multiplayer assumptions.** Replace session prompts with a command or a small dialog state. Drop the static postal map and the liege and marriage checks, which a single player does not need.
- **Simplify the postal failure path.** Process parcels one at a time and remove each only after it is routed, so a bad record cannot lose the batch.

## Coverage

**Read in full:** `MOBS/StdMobWrapper.java`, `MOBS/StdShopKeeper.java`, `MOBS/StdPostman.java`, `MOBS/StdRideable.java`, `MOBS/StdRideableWrapper.java`, `MOBS/Teacher.java`, `MOBS/Wyvern.java`, `MOBS/UmberHulk.java`, `MOBS/Worm.java`, `MOBS/WildEagle.java`, `MOBS/Tiger.java`, `MOBS/Troll.java`, `MOBS/Wolf.java`, `MOBS/Wolverine.java`, `MOBS/StoneGiant.java`, `MOBS/Turtle.java`, `MOBS/Undead.java`, `MOBS/Vulture.java`, `MOBS/Swordfish.java`, `MOBS/Walrus.java`, `MOBS/Whale.java`, `MOBS/StoneGolem.java`, `MOBS/Toad.java`, `MOBS/Zombie.java`. Wyvern, UmberHulk, Worm, WildEagle, Tiger, Troll, Wolf and Wolverine were printed in full. StoneGiant, Turtle, Undead, Vulture, Swordfish, Walrus, Whale, StoneGolem, Toad and Zombie were printed from the class declaration (line 35), so their package, import and licence block (lines 1-34) was not printed.

**Read in part:** `MOBS/WhiteBear.java` (class body only) and `MOBS/WhiteDragon.java` (class line and body; its imports skipped).

**Skimmed:** `core/interfaces/ShopKeeper.java` (deal constants and enum, via grep), `core/interfaces/PostOffice.java` (lines 1-80), `core/interfaces/Rideable.java` (basis enum), `Libraries/Dice.java` (`roll`, `rollHP`, the formula), `Libraries/CMMap.java` (`areaLocation`, `getStartArea`), `Libraries/CoffeeShops.java` (signatures only), `Libraries/CoffeeMaker.java` (shop serialisation and `populateShops`, by grep and partial read), `MOBS/StdMOB.java` (constructor, `isGeneric`, `text`), `MOBS/GenMob.java` and `MOBS/GenShopkeeper.java` and `MOBS/GenPostman.java` (`isGeneric` and `text`), `Abilities/StdAbility.java` (target resolution), `Abilities/Spells/Spell_Confusion.java` (quality and invoke), `Abilities/Spells/Spell_PlanarBubble.java` and `Spell_PlanarDistortion.java` (wrapper sites), `Races/StdRace.java` (`startRacing`), `core/CMProps.java` (tick, mud-hour and HP constants), and `actors/b173-mobs-the-mob-model-stdmob-and-generic-npc-classes.md` (to avoid repeating it).

**Skipped:** the behaviour classes `Behaviors/Aggressive.java`, `Mobile.java`, `CombatAbilities.java`, `MOBTeacher.java` and `MudChat.java` (named only); the `Poison` ability (named only); the race classes for the monsters' races (not read); and the subclasses in other buckets, named only: `MOBS/Horse.java:35`, `MOBS/Pegasus.java:35`, `MOBS/PegasusGreater.java:35`, `MOBS/GenRideable.java:36`, `MOBS/StdBanker.java:38`, `MOBS/StdLibrarian.java:42`, `MOBS/StdCraftBroker.java:47`, `MOBS/GenBanker.java`, `MOBS/GenLibrarian.java`, `MOBS/GenCraftBroker.java`, `MOBS/GenAuctioneer.java`, `MOBS/StdAuctioneer.java:39`, `MOBS/BrownBear.java:35` and `MOBS/Dragon.java:36`. Deities (`StdDeity`) are in another bucket.
