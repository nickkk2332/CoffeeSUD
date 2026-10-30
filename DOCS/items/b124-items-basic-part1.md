# Basic items, part 1: corpses, vehicles, generic "Gen" items and space bodies

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b124` (Items/Basic [part 1/5: Apple .. GenKey]) · 46 files · 7824 lines of Java.

## Summary

This slice of `Items/Basic` covers three groups. The largest is the "Gen" family (`GenItem`, `GenContainer`, `GenFood`, `GenDrink`, `GenCoins`, `GenCage`, `GenBook` and others): thin subclasses whose fields are exposed as stat codes so that builders can edit them in the running game. The second group is the death body (`Corpse`) and the vehicles and buildings that move, sink, fly or fight (`GenBoat`, `GenAirShip`, `GenBalloon`, `GenCaravan`, `GenCastle`). The third is a handful of fixed objects (`Gem`, `Apple`, `Backpack`, the space bodies). The mechanics worth studying are the corpse's looting policy and its saved-character snapshot, the vehicle model (navigation verbs, speed tied to a 0-100 condition value, turn and move cooldowns in ticks), the wreck behaviour of caravans and castles, which damages every creature in their area, cages that store a whole creature inside an item, and deeds that transfer property. The base classes (`StdItem`, `StdContainer`, `StdRideable`, `StdBoardable`, `StdNavigableBoardable`, `StdSiegableBoardable`) are outside this bucket (the base item classes are covered by bucket b172) and are described here only where the Gen classes depend on them.

## Key files and types

Paths are relative to the CoffeeMud folder. Every file in the table sits in `com/planet_ink/coffee_mud/Items/Basic/`.

| File | Base class | Role | Lines |
|---|---|---|---|
| `Corpse.java` | `GenContainer` | Body of a dead character: saved MOB, looting guard, golem resurrection, decay message | 505 |
| `GenCorpse.java` | `Corpse` | Builder-editable corpse (sets `isGeneric`) | 48 |
| `GenBoat.java` | `GenRideable` | Water vessel: own damage formula, sinking, tactical siege coordinates | 483 |
| `GenAirShip.java` | `GenNavigableBoardable` | Flying ship: hull scales with rooms, falls at zero condition, sky maintenance, crew-sink hook | 482 |
| `GenBalloon.java` | `GenAirShip` | Slow balloon; drifts down when unattended | 174 |
| `GenCaravan.java` | `GenNavigableBoardable` | Land wagon pulled by a team; weight and team checks; wreck behaviour | 462 |
| `GenCastle.java` | `GenSiegableBoardable` | Stationary castle; entry permission; wreck behaviour | 289 |
| `GenBoardable.java` | `StdBoardable` | Generic boardable area with a full structural `sameAs` | 282 |
| `GenCageRideable.java` | `StdCageRideable` | Wagon with a cage (capacity 5000) | 259 |
| `Boat.java` | `StdRideable` | Fixed boat, water basis | 53 |
| `GenFurniture.java` | `GenRideable` | Generic furniture: sit basis, 2 riders, push/pull allowed | 82 |
| `GenBed.java` | `GenRideable` | Generic bed: sleep basis, 2 riders | 68 |
| `Bed.java` | `GenRideable` | Fixed bed, near-duplicate of `GenBed` | 61 |
| `GenChair.java` | `GenRideable` | Generic chair: sit basis, 1 rider | 60 |
| `Chair.java` | `StdRideable` | Fixed chair, 1 rider; unused `readableText` field | 59 |
| `BardMap.java` | `GenMap` | Map item that shows the rooms listed in its map-area text | 94 |
| `GenCoins.java` | `GenItem` | Coins: count, denomination, currency; weight from count | 325 |
| `GenCertificate.java` | `StdItem` | Ownership deed (`PrivateProperty`), transferable, bundleable | 408 |
| `GenFood.java` | `StdFood` | Generic food | 176 |
| `GenFoodResource.java` | `GenFood` | Raw edible resource; rot delegated to `Prayer_Rot`; bundles by weight | 261 |
| `Apple.java` | `StdFood` | Fixed apple | 55 |
| `GenDrink.java` | `StdDrink` | Generic drink container; liquid type stored in the text field | 247 |
| `GenEmptyDrink.java` | `GenDrink` | Drink container with zero liquid | 48 |
| `GenFountain.java` | `GenWater` | Effectively infinite water source, not gettable | 68 |
| `GenContainer.java` | `StdContainer` | Generic container (capacity 50) | 209 |
| `GenBurnOven.java` | `GenContainer` | Oven that redirects fire effects to burnable contents | 82 |
| `GenGiftContainer.java` | `GenContainer` | Wrapped container; opening it empties and destroys the wrapper | 70 |
| `Backpack.java` | `CloseableContainer` | Fixed backpack (capacity 25) | 54 |
| `CloseableContainer.java` | `StdContainer` | Container with a lid, starts closed | 50 |
| `GenCage.java` | `StdCage` | Generic cage (capacity 1000) | 204 |
| `GenCaged.java` | `GenItem` | Cage item holding a serialized creature; releases it on get or drop | 203 |
| `GenBook.java` | `StdBook` | Generic book of chapters; hard-coded self-destruct for one named book | 174 |
| `GenJournal.java` | `StdJournal` | Generic journal of entries; same hard-coded self-destruct | 144 |
| `GenDissertation.java` | `StdDissertation` | Parchment whose value and spell list come from its spells | 153 |
| `GenFatWallpaper.java` | `GenWallpaper` | Wallpaper with builder-set display text; expires | 161 |
| `GenAmmunition.java` | `StdItem` | Batch of ammunition; count stored as uses | 152 |
| `GenGrapples.java` | `StdGrapples` | Generic grapples | 125 |
| `GenCigar.java` | `StdSmokable` | Generic cigar; duration from text | 144 |
| `GenInstrument.java` | `GenItem` | Musical instrument; 18 instrument types | 218 |
| `GenKey.java` | `GenItem` | Door key with a key name | 77 |
| `GenItem.java` | `StdItem` | Base of most Gen items; passes stat codes through | 128 |
| `Gem.java` | `StdItem` | Gem picked at random when created (12 types) | 175 |
| `GenFixture.java` | `GenItem` | Scenery fixture, not gettable; push/pull allowed | 79 |
| `DwarfStar.java` | `GenSpaceBody` | Star at a random position with a random radius | 59 |
| `GasGiant.java` | `GenSpaceBody` | Gas giant at a random position | 57 |
| `Asteroid.java` | `GenSpaceBody` | Asteroid at a random position | 57 |

## How it works

### 1. The Gen pattern: data as stat codes

Most classes in the bucket share one shape (`GenContainer.java:35-209` is representative).

- `ID()` returns the class name, and `isGeneric()` returns true (`GenContainer.java:37-41`, `:81-85`). Generic items are the ones a builder can define in data.
- Each class lists its own codes in a `MYCODES` array (`GenContainer.java:101`: `CLASS, HASLOCK, HASLID, CAPACITY, CONTAINTYPES, RESETTIME, DEFCLOSED, DEFLOCKED`). `getStat` and `setStat` first try the shared builder codes through `CMLib.coffeeMaker().getGenItemCodeNum(code)` (`GenContainer.java:106-107`, `:134-135`), then fall back to the class's own switch, and finally to extension codes (`CMProps.getStatCodeExtensionValue`, `GenContainer.java:127`).
- `sameAs` compares every stat code of two items (`GenContainer.java:197-208`). The `GenBoardable` version is stricter: it also compares every room, item and mob of the boardable's area (`GenBoardable.java:223-281`).
- The `getStatCodes` arrays are cached in `static` fields (`GenContainer.java:178-194`, the same in most Gen classes). The cache is computed once from the first instance that asks.

Many Gen classes also overload their fields. The same few fields mean different things per class:

| Storage | Meaning in each class | Where |
|---|---|---|
| `readableText` (string) | Liquid type number in `GenDrink` (`GenDrink.java:76-90`); duration in `GenCigar` when the text is an integer (`GenCigar.java:63-72`); ammunition type in `GenAmmunition` (`GenAmmunition.java:96-99`); key name in `GenKey` (`GenKey.java:67-76`); spell list in `GenDissertation` (`GenDissertation.java:83-89`); cage key name in `GenCage` and `GenCageRideable` through `keyName()` (`GenCage.java:68-78`, `GenCageRideable.java:82-91`); instrument type number in `GenInstrument` (`GenInstrument.java:86-95`) | as listed |
| `phyStats().ability()` | Coin count in `GenCoins` (`GenCoins.java:112-125`); bundle size minus one in `GenCertificate` (`GenCertificate.java:398-407`); cage flag bits in `GenCaged` (`GenCaged.java:191-202`); maximum speed on navigable vessels (`StdNavigableBoardable.java:160-161`, set to 2 in `GenAirShip.java:67`, 1 in `GenBalloon.java:67`) | as listed |
| `basePhyStats().weight()` | Bundle size in `GenFoodResource` (`GenFoodResource.java:153-163`) | as listed |

`GenItem.setStat` and several siblings call the generic setter and then also write the extension code, without an `else` (`GenItem.java:98-103`; the same in `GenDissertation.java:124-129`, `GenCigar.java:115-120`, `GenJournal.java:115-120`, `GenGrapples.java:94-99`). This is harmless: `CMProps.setStatCodeExtensionValue` only writes to the trailing extension codes (`CMProps.java:3641-3653`), so a generic code cannot match.

### 2. Corpse (`Corpse.java`)

**Creation.** The corpse is made by the race, not by the item class. `StdRace.getCorpseContainer` (`Races/StdRace.java:953-1000`) creates a `Corpse`, copies the character's base stats, records the time of death, sets `setIsPlayerCorpse(mob.isPlayer())`, copies the PK flag, names it "the body of X" with apostrophes replaced by backticks, copies the `Age` effect if present, and places it with `Expire.Player_Body` or `Expire.Monster_Body`. For a monster that is following a player it stores a copy without preservation; otherwise it stores the monster itself with `preserve=true` (`StdRace.java:960-975`). `StdMOB.killMeDead` makes player bodies non-savable and puts them in the player's extra item list (`MOBS/StdMOB.java:1704-1708`).

**Data model.** `Corpse.java:43-56` holds the copied `CharStats`, `mobName`, `mobDesc`, `killerName`, `killerPlayer`, `lastMessage`, `killingTool`, `playerCorpse`, `timeOfDeath`, `mobPKFlag`, `savedMOB` with `saveSavedMOB`, `deadMobHash` and `lootDestroy`. Player corpses can also be written through the secret-identity string as `name/description` (`Corpse.java:105-121`); a slash sets `playerCorpse=true`.

**Looting guard (`okMessage`, `Corpse.java:303-364`).** The guard applies only to a player corpse with a name (line 305-307). It covers `TYP_GET` and any ability used on the body, except `Prayer_Resurrect`, `Prayer_PreserveBody` and `Song_Rebirth` (`Corpse.java:319-324`). The policy is read from `CORPSEGUARD` (`Corpse.java:326`). The checks, in order:

1. Anyone with the CMDITEMS security permission may always take it (`:329-330`).
2. A looter whose group leader is a monster may take it (`:332-333`).
3. The value `ANY` allows everyone (`:334-335`).
4. The owner may take it (`:336-337`).
5. `SELFONLY` refuses with "You may not loot another players corpse." (`:339-343`).
6. `PKONLY` requires the looter to have the PLAYERKILL attribute ("You can not get that.  You are not a player killer.") and the corpse to carry the PK flag ("...@x1 was not a player killer.") (`:345-357`).
7. Otherwise the function returns true (`:361`).

The shipped default is `CORPSEGUARD=ALL` (`coffeemud.ini:413`), which the code never tests for. `ALL` falls through to the final `return true`, so the default behaves as permissive as `ANY`. The golem check (a creature with height below zero and weight at most zero sitting on a player corpse) bypasses the guard (`:309-314`).

**Resurrection and decay (`executeMsg`, `Corpse.java:281-300`).** A golem of that shape that sits on a player corpse calls `CMLib.utensils().resurrect(source, room, this, -1)` and the event stops (`:283-294`). A sniff at the corpse after half an hour of real time (`TimeManager.MILI_HOUR/2`, `Libraries/interfaces/TimeManager.java:442`) prints "@x1 has definitely started to decay." (`:295-297`). The actual expiry comes from the inherited item and room expiry rules (bucket b172, not verified here).

**Saved character.** `destroy()` (`Corpse.java:124-133`) destroys the saved MOB unless `saveSavedMOB` is set or the MOB is a registered player. Stat codes `MOB<code>` pass through to `CMLib.coffeeMaker().getGenMobStat` for the saved MOB (`Corpse.java:406-416`).

### 3. Vehicles and craft: the shared navigation model

The three navigable craft (`GenAirShip`, `GenBalloon`, `GenCaravan`) inherit from `StdNavigableBoardable`, which extends `StdSiegableBoardable`, which extends `StdBoardable` (`StdNavigableBoardable.java:40`, `StdSiegableBoardable.java:39`).

**Verb vocabulary.** Each class maps typed words onto the base enum `NavigatingCommand` (`StdNavigableBoardable.java:103-116`: RAISE_ANCHOR, LOWER_ANCHOR, STEER, NAVIGATE, RISE, DIVE, COURSE, SET_COURSE, TENDER, RAISE, LOWER, JUMP, FOLLOW). The mapping is data inside `findNavCommand`:

| Class | NAVIGATE | RAISE_ANCHOR | LOWER_ANCHOR | Verbs in constructor (verb / noun / anchor) |
|---|---|---|---|---|
| `GenAirShip` | `SAIL` (`GenAirShip.java:121-122`) | `RELEASE_ROPE(S)`, `RELEASE_TIEDOWN(S)`, `RELEASE_TIES` (`:123-130`) | `TIEDOWN`, `TIE_DOWN` (`:131-134`) | fly / ship / rope (`:60-64`) |
| `GenBalloon` | `FLOAT` (`GenBalloon.java:109-111`) | as airship | as airship | float / balloon / rope (`:60-64`) |
| `GenCaravan` | `DRIVE` (`GenCaravan.java:109-111`) | `RELEASE_BRAKE`, `UNSET_BRAKE`, and the misspelt `*_BREAK` forms (`:116-121`) | `SET_BRAKE`, `SET_BREAK` (`:122-125`) | drive / drive / brake (`:61-66`) |

`GenAirShip` also maps `SINK` to `SiegeCommand.IMPLODE` (`GenAirShip.java:162-163`; the enum is at `Items/interfaces/SiegableItem.java:39-44`, with TARGET, AIM and IMPLODE).

**Speed.** `getMaxSpeed` (`StdNavigableBoardable.java:160-187`) starts from `phyStats().ability()`. On a non-land vessel in a room, windy weather adds 1, and one more if the room's own climate is windy; otherwise a windy room climate adds 1 on a roll above 50 (`:162-184`). If the condition `usesRemaining()` is below 10 the speed is 0; otherwise speed is scaled by condition/100 and rounded (`:185-187`). `usesRemaining` is a percentage: `StdBoardable.java:75` sets it to 100.

**Timing.** Navigation runs on the area tick, or on the special-maneuver tick while in tactical mode (`StdNavigableBoardable.java:1260`). Each navigation tick increments `ticksSinceMove` and `ticksSinceLastTurn` (`:1291-1292`). A change of heading needs `ticksSinceLastTurn >= ticksPerTurn` ("can't change direction that quickly", `:698-701`), and a move needs `ticksSinceMove >= ticksPerMove` ("can't move that quickly", `:704-707`). The defaults are 1 and 1 (`StdNavigableBoardable.java:58-59`). The balloon sets 1 and 3 (`GenBalloon.java:70-71`).

**Flight, anchoring and grounding.**
- `GenAirShip.recoverPhyStats` sets IS_FLYING while condition is above zero and IS_FALLING at zero (`GenAirShip.java:79-90`).
- The ship can anchor only where there is sky and no room below, except at a spaceport (`GenAirShip.java:92-100`). An anchored ship refuses the air-current effect (`:314-322`).
- `isGrounded()` is true at zero condition when the ship is not in an air room or has no room below (`GenAirShip.java:462-475`). `amDead()` is grounded or destroyed (`:477-481`).
- A moving airship keeps the sky right. `executeMsg` clears the sky in each outdoor room of its area and gives it sky again (`giveASky`), and when the ship is sunk it adds an UP exit that redirects to the ship's last room (`GenAirShip.java:387-452`).

**Hull points.** Maximum hull is set per class:

| Class | `getMaxHullPoints` | Source |
|---|---|---|
| Base siegable boardable | 5 × proper rooms of the area + armor | `StdSiegableBoardable.java:1281-1284` |
| `GenAirShip` | 25 × rooms + armor | `GenAirShip.java:179-182` |
| `GenCaravan` | 10 × rooms + armor | `GenCaravan.java:212-216` |
| `GenCastle` | 100 × rooms + armor | `GenCastle.java:76-80` |
| `GenBoat` | 12 + armor | `GenBoat.java:98-102` |

The balloon has armor -50 (`GenBalloon.java:68`), so its hull is 50 lower than an airship of the same size. Hull is a size measure, not a point pool: the base class turns damage into a percentage of condition (section 4).

**Drift (balloon only).** On 1% of area ticks (`CMLib.dice().rollPercentage()==1`, `GenBalloon.java:144`), if the area has no inhabitants (`:150-158`) and the room has a room below (`:147-148`), the balloon calls `navMove(DOWN)` (`:158-159`). An unattended balloon sinks back to the ground.

**Horizontal flight.** A balloon can move only up or down over the sky. A horizontal move into a room that is not an air room is refused with "scrape(s) along the ground, going nowhere" (`GenBalloon.java:83-94`).

### 4. Damage and wreck behaviour

**Siegable family (airship, caravan, castle).** These inherit the damage branch of `StdSiegableBoardable.executeMsg` (`StdSiegableBoardable.java:1448-1480`). The percentage lost is `value / maxHull`, rounded as `pctLostAmt = round(pct * 100)` (`:1451-1454`). If that is at least the remaining condition, the object is set to 0, `recoverPhyStats` is called, and `doCombatDefeat(source, true)` runs (`:1465-1471`). Otherwise the condition drops by `pctLostAmt`. For example, a 10-room airship with no armor has a hull of 250 (derived from the 25-per-room formula), so a 25-point hit costs 10 condition points.

**Boat (`GenBoat`).** `GenBoat` is a rideable, not a navigable, so it has no navigation vocabulary, and it does not inherit the siegable damage code. Its `executeMsg` does its own accounting (`GenBoat.java:433-482`):

```java
// GenBoat.java:444-448 (paraphrased formula, source lines)
int level = phyStats().level(); if (level < 10) level = 10;
double pctLoss = CMath.div(msg.value(), level) * 10.0;   // value/level*10
int pointsLost = (int)Math.round(pctLoss * level);        // = value*10 (level cancels)
```

So one point of damage costs 10 condition points, whatever the boat's hull. The boat's `getMaxHullPoints` (12 + armor) is ignored by its own damage code but is read by other systems (for example `Abilities/Common/Shipwright.java:552` and `Behaviors/Conquerable.java:884`), so it still matters elsewhere. When the damage reaches the remaining condition, the boat is set to 0 and `killMeDead(true)` runs (`GenBoat.java:460-470`).

**Sinking and destruction (`GenBoat`).** `killMeDead` (`GenBoat.java:386-414`) sinks the boat with `CMLib.tracking().makeSink` when the room is deep surface water (`:392-398`). Otherwise it sends "<T-NAME> <T-IS-ARE> destroyed!", replaces the boat with the ruined version from `CMLib.utensils().ruinItem`, and destroys itself (`:401-407`). `amDead` is true when the boat is in an underwater room whose grid parent has no room ID (`:373-383`).

**Airship fall and crew.** `GenAirShip.doCombatDefeat` (`GenAirShip.java:347-385`) starts a fall with `CMLib.tracking().makeFall` and announces "<T-NAME> start(s) falling!" (`:353-356`). Then it runs a crew loop over the area's inhabitants (`:358-381`), sends a `MSG_CAUSESINK` message to each one, and bumps the SHIPSSUNK achievement. The loop is guarded by a name comparison; see Problems and cruft, item 2. The crew then receives 500 experience tagged `SIEGE:<ID>` (`:382-383`).

**Caravan and castle wreck.** `GenCaravan.doCombatDefeat` (`GenCaravan.java:393-453`) and `GenCastle.doCombatDefeat` (`GenCastle.java:220-280`) share one design:

- "<T-NAME> start(s) collapsing!" is shown in the wreck's room and to non-outer viewers (`GenCaravan.java:399-401`; `GenCastle.java:225-227`).
- Every creature in the area (`getFilledCompleteMap`) is moved into the wreck's room (`bringMobHere`, `GenCaravan.java:415`) and takes `roll(1,150)/100` of its current hit points as malicious weapon damage (`:416-418`). The castle does the same (`GenCastle.java:242-244`).
- Every gettable item not inside a container in the area is moved into the room (`:428`). Items that wear and tear take `roll(1,150)` item damage in the caravan (`:431`), and `roll(1,300)` in the castle (`GenCastle.java:257`).
- If `createBody` is set, the wreck is ruined into a new item and added to the room. The original is destroyed (`:440-447`).

Both classes place the 500-experience award after the room-branch `return` (see Problems and cruft, item 1).

**Castle entry (`GenCastle.okMessage`, `GenCastle.java:91-119`).** Sitting on the castle while it is owned requires the actor to be flying, to have law privileges, or to be climbing. A non-climbing rider is refused ("You'll need permission to get inside this castle from @x1, such as some means to climb up."). A climber is removed from its mount (`setRiding(null)`, `:114`).

**Caravan drive (`GenCaravan`).** The wagon moves only when a team pulls it. `preNavigateCheck` (`GenCaravan.java:256-324`) finds the top of the riding chain, computes the pull weight with `CMLib.utensils().getPullWeight` (`CoffeeUtensils.java:105-146`), and refuses the move if the weight is zero ("due to a lack of team to pull it", `:280-286`) or below the caravan's own weight ("due to weight", `:287-292`). `getPullWeight` sums the weight of the top rideable and its MOB riders recursively, multiplies by 4, and divides by 300 when the top rideable is a WAGON. For a MOB leader it returns the MOB's weight times 4. The leader is then walked with `CMLib.tracking().walk` (`:299-300`), and every puller must end in the destination room (`:306-316`). A `volatile isDriving` flag suppresses the "must be driven from the deck" guard during these moves (`:74`, `:297-321`, and `okMessage` at `:329-344`).

### 5. Coins and certificates (money and property)

**Coins (`GenCoins.java`).** The count is held in `phyStats().ability()` and read by `getNumberOfCoins` (`:112-115`). `setNumberOfCoins` caps the value at `Integer.MAX_VALUE` (`:118-125`). `denomination` is a double, and 0 becomes 1.0 (`:146-152`). `value()` returns the count (`:45-48`), and `getTotalValue()` returns denomination times count (`:155-158`). `Name()` and `displayText()` are computed from the currency, denomination and count through `CMLib.beanCounter` (`:72-82`).

Weight is computed from the count: `round(ability/100.0)`, applied unless the material is cloth or paper (`GenCoins.java:180-184`). The method then copies the stats and re-applies the effects by hand, instead of calling `super`, because calling `super` would make the ability value count as magic (comment at `:186`). Material is set dynamically. A name containing "note", "bill" or "dollar" becomes paper; otherwise the material is taken from a metal named in the coin's name (`:90-109`).

Trade is restricted. Dropping, giving, putting or depositing a coin whose currency cannot be traded is refused unless the actor has the CMDPLAYERS security flag (`:196-232`; message "You can't seem to let go of @x1."). `autoBundle` merges coins into the holder's stack through `CMLib.beanCounter().putCoinsBack` (`:234-240`).

**Certificates (`GenCertificate.java`).** The deed holds `owner`, `price` and `readableText` as its title ID (`:50-52`, `:76-85`). `isProperlyOwned` checks that the owner is a clan or an existing player (`:100-109`).

- Reading the deed shows the owner, or that it is "available for ownership", and then cancels the default read text by replacing the target message with the sentinel `CANCEL` (`:188-203`, sentinel at `:202`).
- Giving the deed to a MOB transfers it, if the giver is the owner by name, or the owner's liege while married, or holds clan `PROPERTY_OWNER` (`:215-225`). The recipient must accept a buy message from the MOB (`:227-232`). The deed then changes owner and a global give message is sent (`:233-236`).
- Selling the deed to a shopkeeper clears the owner (`:207-213`). Taking an unowned deed from a shopkeeper makes the taker the owner (`:239-252`).
- Value is price times bundle size (`:71-74`). Bundle size is `ability + 1` (`:398-407`), and `autoBundle` merges two deeds with the same ID and title in the same container (`:366-395`).
- Writing on the deed is refused (`:156-164`, "You shouldn't write on @x1.").

### 6. Food, drink and containers

- **Food.** `GenFood` defaults to meat, weight 2, value 5, nourishment 500 and no decay (`GenFood.java:44-58`). `GenFoodResource` is an edible raw material. Its rot is delegated to the shared ability `Prayer_Rot` (`GenFoodResource.java:43`, `:87-99`, `:121-136`), which is switched off by the FOODROT security flag (`:97`, `:130`). A resource bundles by weight (`:153-163`) and does not auto-bundle (`:102-106`). Its generic name is "some <material>" (`:79-84`).
- **Drink.** `GenDrink` holds 100 thirst-quench per drink, 700 units of liquid, capacity 5, and freshwater as the default liquid (`GenDrink.java:46-61`). `GenEmptyDrink` starts with no liquid (`GenEmptyDrink.java:46`). `GenFountain` extends `GenWater`, which extends `GenDrink`, and holds 9,999,999 units with 250 quench per drink. It cannot be taken (`GenFountain.java:43-58`).
- **Containers.** `GenContainer` has capacity 50 and weight 2 (`GenContainer.java:45-55`). `CloseableContainer` starts with a lid, closed (`CloseableContainer.java:41-48`). `Backpack` holds 25, is leather, and can be worn on the back or held (`Backpack.java:41-52`).
- **Oven (`GenBurnOven.java`).** The oven is an open stone container with capacity 150 and weight 20 (`:43-57`). When a fire ability (`FireBuilding`) targets the open oven, `okMessage` retargets the message to the first content item with a burn duration above zero and vetoes the original (`:60-81`). How the caller re-dispatches the retargeted message is outside this bucket (not verified).
- **Gift wrap (`GenGiftContainer.java`).** Opening the wrapper (`TYP_OPEN`) empties it into the room and destroys it, if it has a lid, is open and is not locked (`:59-69`).

### 7. Cages

`GenCage` (`StdCage`) holds 1000, weight 25, value 15, bodies and caged animals (`GenCage.java:42-54`). `GenCageRideable` is the wagon version: 5000 capacity, 1000 weight, wagon basis (`GenCageRideable.java:42-55`).

`GenCaged` is the one that stores a creature. `cageMe(mob)` (`GenCaged.java:80-104`):
- copies the creature's name, description, level, weight and height;
- marks the item as not gettable if the creature cannot move and sees well (`:92-94`);
- stores an XML block `<MOBITEM>` with `MICLASS` (class ID), `MISTART` (start room) and `MIDATA` (the misc-text XML of the creature, `:95-101`).

The XML is compressed into `readableText` (`:58-70`). `unCageMe` parses the block, makes the MOB with `CMClass.getMOB`, resets it with `CMLib.coffeeMaker().resetGenMOB`, sets its start room, and clears its rejuvenation (`:135-176`). `executeMsg` releases the creature when the cage is got or dropped, unless the cage has the `CAGEFLAG_TO_MOB_PROGRAMMATICALLY` bit (`CagedAnimal.java:47`). The creature is brought to life in the actor's room, and the cage is destroyed (`GenCaged.java:118-132`).

### 8. Books, journals, dissertations and other paper

- **Books and journals.** The text is a list of numbered chapters or entries. The description tells the reader to use `READ [NUMBER] [BOOK]` and the WRITE skill to add entries (`GenBook.java:48`; `GenJournal.java:49`). `GenJournal` also parses journal parameters through `StdJournal.JOURNAL_PARMS_LIST` (READ, WRITE, REPLY, ADMIN, PRIVATE, MAILBOX, SORTBY, FILTER, EXPIRE, LIMIT, MAXCHARS, `StdJournal.java:979-983`).
- **Hard-coded deletion.** Any message whose target or tool is an item named "THE ARCHON BOOK" or "THE ARCHON JOURNAL" destroys that item (`GenBook.java:67-74`; `GenJournal.java:68-75`).
- **Dissertation (`GenDissertation.java`).** The spell list is a string of `SpellID(params)` entries separated by semicolons (`StdDissertation.java:340-352` builds it, `:354-377` parses it, and parse drops spells in the archon domain). Its value is 200 plus 100 times the lowest qualifying level of each spell (`StdDissertation.java:379-387`). Setting the text rebuilds the spells and recalculates the value (`GenDissertation.java:83-89`).
- **Wallpaper (`GenFatWallpaper.java`).** Display text is a builder field (`:43-58`). A wallpaper destroys itself when it receives an EXPIRE or DEATH message (`:74-94`).
- **Ammunition (`GenAmmunition.java`).** The count is the uses remaining, default 100 (`:45-55`). The type is text such as "arrows" (`:96-99`). Ammunition does not wear out (`:57-61`), and cannot be worn or wielded ("You can't wear @x1.", "You can't wield @x1 as a weapon.", `:131-151`).
- **Cigar (`GenCigar.java`).** Duration defaults to 200 (`:53`). An integer in the text sets the duration (`:63-72`). The tick unit is set by `StdSmokable` (not verified).
- **Instrument (`GenInstrument.java`).** The type is one of 18 values in `MusicalInstrument.InstrumentType` (`Items/interfaces/MusicalInstrument.java:49-68`). Weight 12, value 15 (`:46-57`). An instrument in the wield slot cannot be used as a weapon. The attack is vetoed and the room sees "<S-NAME> play(s) <O-NAME>." (`:122-140`).
- **Key (`GenKey.java`).** Implements `DoorKey`; the key name lives in `readableText` (`:66-76`). Default material iron.

### 9. Furniture, beds, chairs and fixtures

- `GenFurniture` is sit-basis, 2 riders, weight 40, value 100 (`GenFurniture.java:43-56`). `GenBed` is sleep-basis, 2 riders, weight 150, cotton, value 5 (`GenBed.java:43-59`). `GenChair` is sit-basis, 1 rider, weight 150 (`GenChair.java:43-59`). `Bed` and `GenBed` are nearly identical, and `Chair` is the fixed counterpart of `GenChair`.
- `GenFurniture` and `GenFixture` are not gettable. Push and pull are allowed by clearing the not-gettable sense bit for the duration of `super.okMessage` and restoring it in a `finally` (`GenFurniture.java:59-81`; `GenFixture.java:55-78`).

### 10. Map and space bodies

- `BardMap` overrides `GenMap` (`BardMap.java:44-94`). It makes no map-area computation of its own: `doMapArea` is empty (`:57-61`) and `makeMapRooms` takes the semicolon-separated room IDs in the map-area text, and drops rooms that have the unmappable sense bit (`:73-93`). The caching line is commented out (`:66-67`), so the map is rebuilt on each call.
- `Asteroid`, `GasGiant` and `DwarfStar` extend `GenSpaceBody`. Each sets three random coordinates over the full `long` range (`Math.round(Long.MAX_VALUE*Math.random())`) and a radius from `SpaceObject.Distance` (`Asteroid.java:49-54`; `GasGiant.java:49-54`; `DwarfStar.java:49-54`). The distance enum's `dm` unit is 10 metres (`Kilometer` is 100 units, a comment says 1000 m, `core/interfaces/SpaceObject.java:155-160`). The radii, using those units, are:
  - Asteroid: `5*Kilometer.dm` plus `nextLong % 4*Kilometer.dm`, which is 5 km plus or minus 4 km (`Asteroid.java:54`).
  - Gas giant: `SaturnRadius.dm` (6,026,800 units) plus `nextLong % (SaturnRadius.dm/2)`, about 60,000 km plus or minus 30,000 km (`GasGiant.java:54`; `SpaceObject.java:177`).
  - Dwarf star: `StarDRadius.dm` (959,812 units) plus `nextLong % 30%` of it (`DwarfStar.java:54`; `SpaceObject.java:179`).
- The star is a light source and glowing (`DwarfStar.java:55`). The gas giant and the star are hydrogen (`GasGiant.java:55`, `DwarfStar.java:56`).
- All three seed their random generator with `System.currentTimeMillis()`, so the layout is not reproducible.

### 11. Gems and fixed items

`Gem` is a `StdItem`, not a Gen item, so builders cannot edit it. In its constructor it picks a type with `Math.abs(randomizer.nextInt() % 12)` (`Gem.java:58-66`), which gives 0-11. The type is stored as the item's `level`. `recoverPhyStats` calls `setItemDescription(level)` whenever the level changes (`:68-77`). `setItemDescription` sets name, description, value and material from a switch over the types (`:79-174`). The values are in the content table below.

`Apple` is a fixed fruit: weight 10, nourishment 500, value 15 (`Apple.java:43-54`).

### 12. Messages shown to players

Short strings from this bucket, quoted as they appear in the code:

| Where | Message |
|---|---|
| `Corpse.java:341` | "You may not loot another players corpse." |
| `Corpse.java:297` | "@x1 has definitely started to decay." |
| `GenCaravan.java:342` | "@x1 must be driven from the deck." |
| `GenCaravan.java:283` | "You can't seem to @x2 @x1 due to a lack of team to pull it, <S-NAME> go(es) nowhere." |
| `GenCaravan.java:399`, `GenCastle.java:225` | "<T-NAME> start(s) collapsing!" |
| `GenAirShip.java:354` | "<T-NAME> start(s) falling!" |
| `GenCastle.java:104` | "You'll need permission to get inside this castle." |
| `GenInstrument.java:136` | "<S-NAME> play(s) <O-NAME>." |
| `GenCertificate.java:198` | "It states that the property herein known as '@x1' is owned by @x2." |
| `StdNavigableBoardable.java:707` | "@x1 can't move that quickly.  You must wait a bit longer." |

## Content worth mining

**Gem types** (`Gem.java:43-174`). The index is the gem type and the level value.

| Index | Name | Value | Material |
|---|---|---|---|
| 0 | a piece of quartz | 30 | crystal |
| 1 | a piece of azurite | 20 | gem |
| 2 | a bloodstone | 100 | gem |
| 3 | a jade stone | 200 | jade |
| 4 | a diamond | 5000 | diamond |
| 5 | a ruby | 5000 | gem |
| 6 | an opal | 2000 | gem |
| 7 | a piece of topaz | 500 | gem |
| 8 | a sapphire | 1000 | gem |
| 9 | an onyx stone | 100 | gem |
| 10 | a pearl | 300 | pearl |
| 11 | an emerald | 5000 | gem |
| 12 | (no branch) | (not set) | falls through to "a hunk of metal", steel |

**Condition wording** (`GenBoat.java:416-431`, list at `resources/lists.ini:700-707`). Condition is a percent, and the thresholds are 95, 85, 75, 50, 25, 10, 5 and 0. The lines are:
- 95 and above: nothing.
- 85: looks slightly used.
- 75: somewhat worn.
- 50: worn.
- 25: damaged.
- 10: heavily damaged.
- 5: so damaged it is practically harmless.
- 0: nearly destroyed and completely harmless.

**Stored-value formulas** (data the game already computes, reusable as-is):
- Dissertation value: 200 + sum of 100 × lowest qualifying level per spell (`StdDissertation.java:379-387`).
- Coin weight: round(count / 100), not counting cloth or paper (`GenCoins.java:184`).
- Vessel hull: 25 per room for airships, 10 for caravans, 100 for castles, 12 for boats, all plus armor (section 3).
- Certificate value: price × number of deeds in the stack (`GenCertificate.java:71-74`).

**Default stats of the Gen items** (values from the constructors):

| Class | Weight | Value | Capacity / other | Material |
|---|---|---|---|---|
| `GenItem` | 2 | 5 | level 1 | oak |
| `GenContainer` | 2 | 5 | capacity 50 | (not set) |
| `GenCage` | 25 | 15 | capacity 1000 | oak |
| `GenCageRideable` | 1000 | 15 | capacity 5000, wagon | oak |
| `GenCaged` | 150 | 5 | level 1 | meat |
| `GenFood` | 2 | 5 | nourishment 500 | meat |
| `GenFoodResource` | 1 | (not set) | nourishment 200 | berries |
| `GenDrink` | 1 | 1 | capacity 5, quench 100, 700 units | leather (freshwater) |
| `GenFountain` | 5 | 10 | 9,999,999 units, quench 250 | freshwater |
| `GenBurnOven` | 20 | 10 | capacity 150 | stone |
| `GenGiftContainer` | 1 | 1 | capacity 50 | paper |
| `GenDissertation` | 1 | 200 (recalculated) | spells | paper |
| `GenInstrument` | 12 | 15 | level 1 | oak |
| `GenFurniture` | 40 | 100 | 2 riders | oak |
| `GenBed` / `Bed` | 150 | 5 | 2 riders, sleep | cotton |
| `GenChair` / `Chair` | 150 | 5 | 1 rider, sit | oak |
| `GenFixture` | 20 | 10 | not gettable | oak |
| `Corpse` | 150 | 1 | capacity 5 | meat |
| `Backpack` | (not set) | 5 | capacity 25 | leather |
| `Apple` | 10 | 15 | nourishment 500 | fruit |

**Caged creature record.** A cage stores the class ID, start room and the creature's misc-text XML, compressed (`GenCaged.java:95-101`). It is a ready model for a captured or tamed creature in a Python game.

**Descriptive text worth reusing (short samples only).** "Give or Sell this certificate to transfer ownership." (`GenCertificate.java:80`). "Looks like a nice comfortable wooden chair" (`Chair.java:50`). "Bloody and bruised, obviously mistreated." (`Corpse.java:64`). "Perfectly round, pure and white." (`Gem.java:156`).

## Notable design ideas

1. **Wreck as area-wide consequence.** When a caravan or castle is destroyed, everyone in its area is moved to the wreck and hurt, and unattended items are moved too (`GenCaravan.java:405-435`; `GenCastle.java:231-261`). It is a striking consequence system, but it is blunt.
2. **Speed tied to condition.** One number, the condition percent, drives speed (zero below 10%) and damage thresholds (`StdNavigableBoardable.java:185-187`; `StdSiegableBoardable.java:1465`). A vessel degrades without any separate state.
3. **Pull physics.** Wagon movement depends on the weight of the team and its load, with a fixed divisor for the wagon (`CoffeeUtensils.java:105-146`; `GenCaravan.java:279-292`). This is a small, playable rule.
4. **Unattended drift.** A balloon sinks when no one is aboard, at a small random chance per tick (`GenBalloon.java:137-164`). The world moves when the player is absent.
5. **Corpse as a saved character.** The body keeps a snapshot of the character (`savedMOB`, copied stats, the Age effect), so resurrection and preservation can work later (`Races/StdRace.java:953-1000`; `Corpse.java:267-278`).
6. **Veto phase with redirection.** The oven retargets a fire effect to flammable contents and vetoes the original (`GenBurnOven.java:64-79`). Messages can be changed as they pass the veto phase, not only accepted or refused.
7. **Cage as serialized creature.** A cage item holds a whole creature as text and releases it on pickup (`GenCaged.java:80-176`).
8. **Stat codes for builders.** Every Gen item can be read and written by name (`getStat`/`setStat`), which is how builders edit items in the running game (`GenContainer.java:101-166`).

## Problems and cruft

Found by reading only; none of these were run.

1. **The 500-experience award after a wreck is unreachable in the normal case.** In `GenCaravan.doCombatDefeat` the room branch (`GenCaravan.java:396-449`) returns on every path, and the `postExperienceToAllAboard` call with `SIEGE:` + ID (`:450-451`) comes after it, so it runs only when there is no room. `GenCastle.java:220-280` has the same shape (award at `:277-278`). `GenAirShip` does not have this problem (`:382-384`).
2. **Crew-sink loop guarded by an unlikely comparison.** `GenAirShip.doCombatDefeat` checks `victorM.Name().equals(victorM.riding().Name())` (`GenAirShip.java:358-359`), which compares the victor's name with the ship's name. A normal player never has the ship's name, so the sink notices and the SHIPSSUNK achievement loop (`:361-380`) would likely never run. The intended test is not clear from the code.
3. **Boat damage ignores hull and the level cancels.** The formula at `GenBoat.java:447-448` reduces to 10 × damage. The comment says siege weapons against rideables are "harsh" (`:447`), so this may be intended, but the `level` computation is dead weight and the boat's hull value is not used by its own damage.
4. **Wreck damage is not limited to the crew.** Caravans and castles hurt every creature in the area (`GenCaravan.java:415-418`, `GenCastle.java:241-244`), including players and bystanders. This is a multiplayer-style hazard and is hard to justify in a single-player game.
5. **Off-by-one for `READ` in journal stats.** `GenJournal.java:109` tests `CMParms.indexOf(JOURNAL_PARMS_LIST, ucode) > 0`. `indexOf` returns 0 for the first entry (`CMParms.java:4237-4249`), and `READ` is the first entry (`StdJournal.java:980`). So `getStat("READ")` falls through to the extension lookup and never reaches `getParm("READ")`.
6. **Amethyst has no branch.** `Gem.AMETHYST` is defined (`Gem.java:55`) and has no case in `setItemDescription`. The random roll is 0-11 (`:63`), so it is never generated at random. A level of 12 gives "a hunk of metal".
7. **Policy mismatch in corpse guard.** `Corpse.java:334` tests for `ANY`, while the shipped default and the comment use `ALL` (`coffeemud.ini:408-413`). Both are permissive by accident (section 2).
8. **Dead or duplicate code.**
   - `Corpse.java:479-480` tests `codes != null` inside a block that already requires `codes == null`.
   - `GenCertificate.java:81` sets `baseGoldValue=10000`, but the method override at `:63-66` returns `getPrice()`, so the field is shadowed for that method.
   - `GenCertificate.java:166-183` has three empty `if` bodies.
   - `Chair.java:43` declares `readableText` and never uses it.
   - `Bed.java` and `GenBed.java` duplicate each other.
9. **Shared text field.** The overloading in section 1 makes the data model hard to read. A key name, a liquid type and a spell list all live in `readableText`. Copying or serializing one of them without the others' context is fragile.
10. **State changed during the veto phase.** `okMessage` changes state in several places: `GenCastle.java:114` removes the rider, `GenBurnOven.java:75` retargets the message, and `GenFurniture.java:68-69` and `GenFixture.java:65-66` clear sense bits before the check and restore them after. These are real side effects inside a phase that is meant to only permit or refuse.
11. **Hard-coded item names.** `GenBook.java:70` and `GenJournal.java:71` destroy the item named "THE ARCHON BOOK" or "THE ARCHON JOURNAL" whenever it is targeted. This is an admin or test hook left in the player-facing class.
12. **Unused message arguments.** `GenBalloon.java:89` passes the direction and verb to a string with no placeholders for them.
13. **Parse helper inconsistency.** `GenBook.java:123` and `:126` use `CMath.parseIntExpression`, where the other Gen classes use the `s_` variant. Behaviour on bad input is not verified.
14. **Typos in player-visible text.** "a beutiful green stone" (`Gem.java:100`), "a onyx stone lies here" (`Gem.java:148`), "It dark grey stone" (`Gem.java:93`), "It burn coal or wood" (`GenBurnOven.java:49`).
15. **Static caches of stat codes.** `getStatCodes` caches a `static` array (`Corpse.java:472-491`, `GenCertificate.java:334-350`, and others), so the list is fixed by the first instance. The effect when a subclass or an instance differs is not verified.

## Takeaways for a Python SUD

**Borrow.**

- **Vehicles as data plus one condition value.** A vehicle has a kind, a maximum speed, a per-room hull and a condition percent. Speed is `max_speed * condition / 100`, and zero below 10. Turn and move cooldowns are tick counters. A verb table per vehicle kind maps player words to actions.

  ```python
  @dataclass
  class Vehicle:
      kind: str             # "balloon", "airship", "wagon", "boat", "castle"
      max_speed: int        # CoffeeMud's phyStats().ability()
      hull_per_room: int    # 25 airship, 10 caravan, 100 castle (CoffeeMud values)
      condition: int = 100  # percent, as usesRemaining is in CoffeeMud

      def speed(self) -> int:
          return 0 if self.condition < 10 else round(self.max_speed * self.condition / 100)
  ```

- **Pull weight as a formula.** Keep the arithmetic from `getPullWeight` (sum of weights, ×4, ÷300 for a wagon) as a pure function, and check it against the wagon's weight. The formula is a simplification of the engine's recursive version, not a copy.
- **Coins as a value object.** `Coins(count, denomination, currency)` with `total_value = count * denomination` and `weight = round(count / 100)`. Use explicit fields, not a spare numeric slot.
- **Corpse as a snapshot.** Store the dead character's state in the corpse object with a decay time. Keep the saved snapshot only if resurrection exists in the game.
- **Cages.** A `CagedCreature(snapshot: dict, start_room)` item releases the creature when it is picked up. Keep the snapshot as plain data, not a serialized XML string.
- **Gems.** An `Enum` with value and material, drawn from a weighted table. Do not leave a named constant with no entry.
- **Scroll or dissertation value.** Use 200 plus 100 × spell tier as a starting formula, with the spell list as a list of IDs.
- **Balloon drift as a world rule.** Unattended airborne things descend at a small random rate per tick.

**Simplify.**

- Replace the `getStat`/`setStat` string protocol with dataclass fields and a `to_dict`/`from_dict` pair. Keep a string editor only if builders need one.
- Replace the overloaded text field with named fields for each meaning.
- Make the veto phase pure. Compute the redirect or removal in a separate step, not inside the permission check.
- Drop the stat-code caches and the duplicate Bed and Chair classes.

**Drop (multiplayer assumptions).**

- The looting guard, CORPSEGUARD, SELFONLY and PKONLY, and the PK flag. A single-player corpse is only the player's own body and gear.
- Crew experience and ship achievements. A single-player vehicle has at most the player and companions.
- The area-wide wreck. If a vehicle is destroyed, let it drop its own contents and hurt only the occupants.
- Liege, clan and marriage checks on deeds. Ownership can be a simple owner field.
- Siege coordinates and tactical maneuvering, unless the game adds naval or aerial combat.

## Coverage

- **Read in full (class body from the declaration to the end; package, import and license lines skipped):** all 46 files in the manifest. `Corpse.java` and `GenBoat.java` were read from line 1.
- **Read in part, as supporting code for the explanations:**
  - `Items/Basic/StdNavigableBoardable.java`: lines 40-60, 103-125, 146-187, 698-712, 1258-1300.
  - `Items/Basic/StdSiegableBoardable.java`: lines 1278-1284, 1446-1492.
  - `Items/Basic/StdDissertation.java`: lines 340-387.
  - `Items/Basic/StdJournal.java`: lines 979-983.
  - `Items/Basic/StdBoardable.java`: line 75 only.
  - `Items/Basic/GenFoodResource.java` and the other base classes were read only where the bucket files call them.
  - `MOBS/StdMOB.java`: lines 1690-1708.
  - `Races/StdRace.java`: lines 953-1000.
  - `Libraries/CoffeeUtensils.java`: lines 80-146.
  - `core/CMath.java`: the `div` overloads (lines 433-688).
  - `core/CMParms.java`: lines 4237-4287.
  - `core/CMProps.java`: lines 3641-3653.
  - `core/interfaces/SpaceObject.java`: lines 155-180.
  - `Items/interfaces/SiegableItem.java`: lines 39-44.
  - `Items/interfaces/MusicalInstrument.java`: lines 49-69.
  - `resources/lists.ini`: lines 700-707; `coffeemud.ini`: lines 405-413.
  - `Libraries/interfaces/TimeManager.java` (`MILI_HOUR`): line 442 only.
  - Grep-only checks for consumers of `getMaxHullPoints` (`Abilities/Common/Shipwright.java`, `Behaviors/Conquerable.java`, others) and for corpse creation.
- **Skipped (other buckets or not needed):** `StdItem`, `StdContainer`, `StdRideable`, `StdBoardable`, `StdFood`, `StdDrink`, `StdCage`, `StdCageRideable`, `StdBook`, `StdSmokable`, `StdGrapples`, `GenRideable`, `GenWater`, `GenWallpaper`, `GenMap`, `GenSpaceBody`, `GenNavigableBoardable`, `GenSiegableBoardable`, `StdSpaceBody`, `GenericBuilder.GenItemCode`, and the expiry and decay rules. Items that depend on these are described from the calls the bucket makes, and any claim about their internals is marked unverified where it appears.
