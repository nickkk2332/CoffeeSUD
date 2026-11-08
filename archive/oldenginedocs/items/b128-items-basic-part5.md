# Basic items, part 5: paper, books, titles, quest boards, portals, rideables, siege vessels, space bodies and small consumables

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b128` (Items/Basic [part 5/5: StdPaper .. Wineskin]) · 19 files · 7590 lines of Java.

Citations are paths relative to the CoffeeMud folder, with line numbers. In the running text, a bare `Name.java:line` refers to a file in `com/planet_ink/coffee_mud/Items/Basic/` unless another folder is shown, and the Key files table gives the full paths. Where a mechanic depends on a class outside this bucket, that file is cited and marked as outside. The default tick is 4000 ms (`com/planet_ink/coffee_mud/core/CMProps.java:681`), so "ticks" below mean 4 seconds unless a server sets `TICKTIME`. Where a comment or the guide disagrees with the code, the code is reported and the disagreement is named.

## Summary

The 19 classes here finish the basic item family. Some hold text or a reference rather than physical state: a page (`StdPaper`) or a multi-chapter book (`StdPlayerBook`) stores text, a quest board (`StdQuestBoard`) is a filtered view over the quest engine, and a deed (`StdTitle`) is only a pointer to a property record. Consumables and light (`StdPerfume`, `StdSmokable`, `StdTub`, `Waterskin`, `Wineskin`, `Torch`) are small variations on the drink, container and light-source rules. The most elaborate code is the entrance and ride layer (`StdPortal`, `StdRideable`) and the ship-combat layer (`StdSiegableBoardable`): a portal is both an item and a temporary exit, a rideable has a verb table for each posture, and a siege vessel fights on a tactical grid with aimed volleys. Space objects (`StdSpaceBody`, `StdWormhole`) and two proxy families (`StdPortalWrapper`, `StdRideableWrapper`) complete the bucket. The pointer-style deed, the portal-as-exit and the posture table port well to a single-player Python game; the quest board and the siege layer assume other players or large NPC populations and need reshaping.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Items/Basic/StdSiegableBoardable.java` | Ship-scale siege layer: tactical grid, TARGET and AIM commands, aimed shots, magazine volleys, hull as a percentage, condition text | 1531 |
| `com/planet_ink/coffee_mud/Items/Basic/StdRideable.java` | Riding and furniture: ten ride bases, per-posture verbs, rider limits, verb restrictions, siege-weapon volley on riders | 1209 |
| `com/planet_ink/coffee_mud/Items/Basic/StdThinItem.java` | Null-object `Item` implemented directly, all methods no-op or constant; no other reference in the source tree | 758 |
| `com/planet_ink/coffee_mud/Items/Basic/StdTitle.java` | Property deed: a pointer to a PrivateProperty record, with sell, give, buy and withdraw rules | 744 |
| `com/planet_ink/coffee_mud/Items/Basic/StdPortal.java` | Item that is both a Rideable and an Exit; random destination; temporary GATE exit on entry | 629 |
| `com/planet_ink/coffee_mud/Items/Basic/StdTub.java` | Bathtub: rideable furniture that is also a liquid container; drink, fill, hygiene | 427 |
| `com/planet_ink/coffee_mud/Items/Basic/StdSmokable.java` | Cigar or pipe: timed light that puffs while worn, can carry an item payload, can cause addiction or disease | 411 |
| `com/planet_ink/coffee_mud/Items/Basic/StdQuestBoard.java` | Read-only board listing running quests, filtered by zapper masks and text filters | 353 |
| `com/planet_ink/coffee_mud/Items/Basic/StdSpaceBody.java` | Base space object: radius, coordinates, direction, mass, star and planet naming, damage by mass | 274 |
| `com/planet_ink/coffee_mud/Items/Basic/StdRideableWrapper.java` | Read-through proxy for a Rideable; writes ignored | 237 |
| `com/planet_ink/coffee_mud/Items/Basic/StdPlayerBook.java` | Player-written book; each chapter is a player-data row | 202 |
| `com/planet_ink/coffee_mud/Items/Basic/StdPaper.java` | One writable page of up to 2048 characters | 180 |
| `com/planet_ink/coffee_mud/Items/Basic/StdPerfume.java` | Drink-type item applied by WEAR; attaches a timed smell emoter | 159 |
| `com/planet_ink/coffee_mud/Items/Basic/StdWormhole.java` | Paired space gateway that relocates a colliding ship | 121 |
| `com/planet_ink/coffee_mud/Items/Basic/StdPortalWrapper.java` | Read-through proxy for an Exit | 120 |
| `com/planet_ink/coffee_mud/Items/Basic/Torch.java` | LightSource preset: 200-tick torch, destroyed when burnt out | 65 |
| `com/planet_ink/coffee_mud/Items/Basic/Wineskin.java` | StdDrink preset, capacity 5 | 60 |
| `com/planet_ink/coffee_mud/Items/Basic/Waterskin.java` | StdDrink preset, capacity 15 | 59 |
| `com/planet_ink/coffee_mud/Items/Basic/TrappedChest.java` | LargeChest with a generic Trap_Trap attached | 51 |

Outside this bucket and cited: `Items/Basic/StdBoardable.java` (extends StdPortal; base of the siege vessels), `Items/Basic/StdNavigableBoardable.java`, `Items/Basic/StdBook.java`, `Items/Basic/LightSource.java`, `Items/Basic/StdDrink.java`, `Items/Basic/StdContainer.java`, `Items/Basic/LargeChest.java`, `Items/Weapons/StdSiegeWeapon.java`, `Behaviors/Emoter.java`, `Abilities/Traps/Trap_Trap.java`, `Abilities/Traps/Trap_Open.java`, `Libraries/CoffeeUtensils.java`, `Libraries/MUDFight.java`, `core/interfaces/SpaceObject.java`, `core/interfaces/Rideable.java`, `Items/interfaces/SiegableItem.java`.

Class tree (this bucket, with the outside classes they depend on):

```
StdItem
 +-- StdPaper (Book)                      -> GenPaper
 +-- StdBook (outside)                    -> StdPlayerBook -> GenPlayerBook
 +-- StdTitle (LandTitle)                 -> GenTitle
 +-- StdQuestBoard                        -> GenQuestBoard
 +-- StdSpaceBody (SpaceObject)           -> GenSpaceBody; StdWormhole -> GenWormhole
 +-- LightSource (outside)                -> Torch
StdContainer (outside)
 +-- StdSmokable (Light)                  -> GenCigar
 +-- StdDrink (outside)                   -> StdPerfume -> GenPerfume; Waterskin; Wineskin
 +-- StdRideable (Rideable)               -> StdTub (Drink) -> GenTub
 |                                           (also Chair, Boat, HorseCart, StdCageRideable, StdSiegeWeapon: other buckets)
 +-- StdPortal (Rideable, Exit)           -> StdGrapples; GenPortal
 |       +-- StdBoardable (outside)       -> StdSiegableBoardable -> GenSiegableBoardable, StdNavigableBoardable (outside)
 +-- LockableContainer -> LargeChest -> TrappedChest
StdItemWrapper -> StdContainerWrapper -> StdRideableWrapper -> StdPortalWrapper
StdThinItem (implements Item directly, no superclass)
```

## How it works

### 1. Paper and books

**Data model.** `StdPaper` keeps its text in the item's misc-text field (`com/planet_ink/coffee_mud/Items/Basic/StdPaper.java:72-81`; `setReadableText` calls `super.setMiscText`). It has exactly one page (`getMaxPages` returns 1, `StdPaper.java:90-93`) and a per-page limit `maxCharsPage = 2048`, where 0 means unlimited (`StdPaper.java:61`, `138-147`). The item is readable through `basePhyStats().setSensesMask(PhyStats.SENSE_ITEMREADABLE)` (`StdPaper.java:57`). `canRead` and `canWrite` always return true (`StdPaper.java:168-178`).

**Writing.** A write is a `CMMsg` with `TYP_WRITE` and the new text in `targetMessage`. `StdPaper.okMessage` refuses it when `readableText().length() + targetMessage().length() > maxCharsPage` (`StdPaper.java:154-164`). `StdItem.executeMsg` then appends with `setReadableText((readableText()+" "+msg.targetMessage()).trim())` (`com/planet_ink/coffee_mud/Items/Basic/StdItem.java:1724-1727`). `TYP_REWRITE` replaces the text, or clears it when the text starts with `DELETE ` (`StdItem.java:1728-1735`). The page limit is checked only for `TYP_WRITE` (see Problems).

**Messages.** Refusal: "There isn't enough space left on the paper to write all that. It looks like you can fit @x1 more characters." (`StdPaper.java:161`). The number passed is the page maximum, not the space left (Problems).

**Books.** `StdPlayerBook` (outside parent `StdBook`) stores each chapter as a player-data row in category `"BOOK_"+KEY` (`com/planet_ink/coffee_mud/Items/Basic/StdPlayerBook.java:66`, `135`, `198`).
- On first write the book gets a random KEY: `Math.random()+"_"+Math.random()`, stored in the book text as `KEY="..."` (`StdPlayerBook.java:195-196`).
- A chapter row key is `FROM_BOOK_<KEY>_<n>`, where `n` is the current chapter count (`StdPlayerBook.java:199`). The row body is `::subject::message`, with `::` inside the subject replaced by `;;` (`StdPlayerBook.java:200`).
- Reading sorts rows by the number after the last `_` (`StdPlayerBook.java:137-156`). Chapter entries are titled `Chapter N by <author>` when several authors contributed and `Chapter N` otherwise (`StdPlayerBook.java:98-102`).
- The table-of-contents header is "An unfinished work." with no chapters, "By <author>" for one author, and "By Various Authors" otherwise (`StdPlayerBook.java:70-83`).
- Deletion comes from the parent through `REWRITE DELETE n`, which calls `delOldChapter` (`com/planet_ink/coffee_mud/Items/Basic/StdBook.java:467`; `StdPlayerBook.java:86-90`).

### 2. Property titles (StdTitle)

**Data model.** A title stores only a property id, in its misc text (`landPropertyID()` returns `text()`, `com/planet_ink/coffee_mud/Items/Basic/StdTitle.java:281-285`). `setLandPropertyID` also renames it (`StdTitle.java:305-309`). Everything else is looked up on each call. `fetchAPropertyRecord()` tries, in order, a Room with that id, an Area, a Boardable ship, and finally the land title of the room the deed names (`StdTitle.java:257-279`). Price and owner come from that record (`getPrice` at `StdTitle.java:101-111` adds back taxes for land titles; `getOwnerName` at `StdTitle.java:232-239`). Back taxes, rental, theft and grid settings are read through the land title of the titled room instead (`StdTitle.java:133-215`, via `fetchALandTitle`, `:252-255`). The deed is therefore a bearer token that points at state held elsewhere. Default value is 10000 (`StdTitle.java:86`); a copy whose name contains "(Copy)" is worth 10 (`StdTitle.java:67-74`).

**Naming.** `updateTitleName` yields "the title to <id>" for a single-room, area or ship property, and "the title to rooms around <room id>" when the property has several connected rooms (`StdTitle.java:287-302`).

**Transfer and refusal rules.**
- Writing on a title is refused: "You shouldn't write on @x1." (`StdTitle.java:413-419`).
- Reading reports the owner: "It states that the property herein known as '@x1' is deeded to @x2." or "...is available for ownership." (`StdTitle.java:570-575`). The read message is then replaced with the literal `CANCEL` (`StdTitle.java:579`); how the read handler uses that sentinel was not traced.
- A shopkeeper GET with no record destroys the title: "You can't buy that." (`StdTitle.java:421-431`). A shop sale to a player assigns the owner (`StdTitle.java:697-742`). For a clan seller or clan ship seller, the owner is the clan with the PROPERTY_OWNER function if the buyer holds one (`:710-725`); otherwise it is the buyer's name (`:727-729`). Back taxes reset to 0 (`StdTitle.java:732`).
- Selling back to a shop clears the owner (`StdTitle.java:584-603`).
- Giving the title to a mob (`TYP_GIVE`) needs the giver to be the owner, married to the owner's liege, or hold the clan function (`StdTitle.java:605-615`). The recipient must accept a simulated `MSG_BUY` (`StdTitle.java:624-629`). Bankers, auctioneers, librarians and post offices are excluded as recipients (`StdTitle.java:612-615`). Result: "@x1 is now signed over to @x2." (`StdTitle.java:662`).
- A shop or bank that is not allowed to hold the title destroys it. "I'm sorry, '@x1 is not for sale. It already belongs to @x2. It should be destroyed." (`StdTitle.java:440-466`), and "I'm sorry, '@x1 must be destroyed." on withdrawal (`StdTitle.java:468-491`).
- For a Boardable record (a ship), `addBoardableProperty` and `removeBoardableProperty` move the ship's item between the owner's external item lists, with achievement bumps for clans (`StdTitle.java:495-559`).

### 3. Quest board (StdQuestBoard)

**Data model.** Parameters are `KEY="VALUE"` pairs in the readable text (`com/planet_ink/coffee_mud/Items/Basic/StdQuestBoard.java:80-86`). Six codes are recognised (`StdQuestBoard.java:60-70`, `MYCODES` at `:70`): `SAREAMASK`, `TAREAMASK`, `AAREAMASK` and `GIVERMASK` are compiled zapper masks, and `TYPFILTER` and `CATFILTER` are substring filters.

**Which quests show.** `getQuestList()` rebuilds a cached list at most every 5 seconds (`StdQuestBoard.java:124-131`, `nextCheck=now + 5000`). It rebuilds only when `calculateQuestHash()` changes (`StdQuestBoard.java:88-97`, an XOR over every quest of `hashCode() + (running ? 1 : 0)`). For each quest that is running and not stopping (`:140`):
1. the type and category substring filters (`:142-147`);
2. `GIVERMASK` against the quest giver mob, and `SAREAMASK` and `AAREAMASK` against the giver's area (`:149-166`);
3. when `TAREAMASK` or `AAREAMASK` is set, the areas touched by the quest's rooms (`Q.getQuestRoom(i)` for i = 1, 2, ..., plus each area's parents, `:168-189`), then an accept test: the quest passes if some area matches `SAREAMASK`, or if some area fails `AAREAMASK` (`:190-207`);
4. `isWellFormedQuest` (`:111-120`): a display name, instructions, and `canAcceptQuest(null)`.

The per-reader list then filters by `canAcceptQuest(mob)` (`:99-109`).

**READ.** A plain READ lists quests with three-digit numbers: `CMStrings.padLeft(""+num++, 3)+") "+displayName` (`StdQuestBoard.java:244-252`). READ with a number shows a detail block with labels padded to ten columns: `Quest`, `Giver` (the mob's name, "at", its room), `In` (the area) and `Descrip.` (`StdQuestBoard.java:267-280`). The reply is a trailer message "It says '...'" (`:285-288`). If a quest was chosen and the reader has a session, the board asks "Accept this quest (y/N)? " with N as the default (`:297-312`). On Y it calls `quest.acceptQuest(M)` and, if the mob has a script with the quest's default name, prints "You are now on the quest '@x1'." (`:322-333`). The board returns before the normal read text is shown (`:341`).

**Design note.** The board holds no quests. It is a view over `CMLib.quests().enumQuests()` (`StdQuestBoard.java:137`), and its filters are data, so the same class serves a town board and a mercenary board.

### 4. Portals (StdPortal)

**Data model.** `StdPortal` extends `StdContainer` and implements both `Rideable` and `Exit` (`com/planet_ink/coffee_mud/Items/Basic/StdPortal.java:36`). Defaults: weight 10000 (`:62`), capacity 10000 (`:64`), material nothing (`:65`), one rider (`:95-98`), ride basis `ENTER_IN` (`:84-87`). Destinations are room ids separated by semicolons in the readable text. `getDestinationRoom` picks one with `CMLib.dice().roll(1,V.size(),-1)` (`StdPortal.java:338-345`). Mount and dismount default to "enter(s)" and "emerge(s) from" (`:47`, `:209-242`).

**Enter flow.** ENTER reaches the portal as a SIT-type message. `okMessage` recognises a portal entry by searching the source message for the mount text (`StdPortal.java:398`: `msg.sourceMessage().indexOf(mountString(CMMsg.TYP_SIT,msg.source()))>0`). It refuses with "This portal is broken.. nowhere to go!" when there is no destination (`:400-404`), and with "@x1 is closed." when a door is shut (`:405-409`). Otherwise it rewrites the message to an ENTER (`:410-414`).

`executeMsg` then does the crossing (`StdPortal.java:455-477`):
- it saves the room's GATE exit (direction index 6, `com/planet_ink/coffee_mud/core/Directions.java:87`) and puts in a temporary `OpenPrepositional` exit (`StdPortal.java:360-361`);
- it points the destination's GATE at a second temporary exit marked `IS_NOT_SEEN` (`:467`);
- it walks the mob through GATE with `CMLib.tracking().walk(msg.source(),Directions.GATE,false,false,false)` (`:469`);
- in a `finally`, it restores the room's original exits (`:472-477`); the message target is then cleared (`:478`).
A lock keyed on the two room ids guards the swap (`:463`).

**Look.** `viewableText` names the destination room for system operators (`:547-550`). Others see the portal name when it is open, the closed text when it is shut, or "darkness" when the destination room is not visible (`:553-566`).

**Other verbs.** Sleep, mount and sit on a portal are refused with "You cannot sleep on @x1.", "You cannot mount @x1, try Enter." and "You cannot sit on @x1." (`StdPortal.java:416`, `:423`, `:430`).

**Subclasses.** `StdBoardable` (ship entrances, outside), `StdGrapples` (`"a set of ship grapples"`, `com/planet_ink/coffee_mud/Items/Basic/StdGrapples.java:50-52`) and `GenPortal` (data-driven). Siege vessels inherit the portal entry.

### 5. Rideables and furniture (StdRideable, StdTub)

**Data model.** `rideBasis` is one of ten `Rideable.Basis` values (`com/planet_ink/coffee_mud/core/interfaces/Rideable.java:44`). `riderCapacity` defaults to 4 (`com/planet_ink/coffee_mud/Items/Basic/StdRideable.java:45`) and the riders are kept in a list (`:46`). The default instance is "a boat": weight 2000, capacity 3000, oak, 100 uses (`StdRideable.java:79-86`). Five strings (put, ride, state, mount, dismount) can override the defaults through `setXxx`, and a value equal to the default is stored as empty (`StdRideable.java:325-509`).

**Per-posture verbs.** Each basis chooses its own verbs. Defaults from `mountString` (`StdRideable.java:418-447`), `dismountString` (`:463-489`), `stateString` (`:290-317`) and `stateStringSubject` (`:511-539`):

| Basis | Enter (mount) | Leave (dismount) | Riding state | Occupant subject |
|---|---|---|---|---|
| LAND_BASED | board(s) | disembark(s) from | riding in | being ridden by |
| WATER_BASED | board(s) | disembark(s) from | riding in | being ridden by |
| AIR_FLYING | board(s) | disembark(s) from | riding in | being ridden by |
| WAGON | board(s) | get(s) off of | riding in | being ridden by |
| FURNITURE_SIT | sit(s) on | get(s) off of | on | (none) |
| FURNITURE_SLEEP | lie(s) down on, or sit(s) down on when sitting | get(s) off of | on | (none) |
| FURNITURE_TABLE | sit(s) at | get(s) up from | at | occupied by |
| ENTER_IN | get(s) into | get(s) out of | in | occupied by |
| LADDER | climb(s) onto | get(s) off of | climbing on | occupied by |
| FURNITURE_HOOK | climb(s) onto | remove(s) themselves from | on | holding |

**Verb restrictions** (`StdRideable.java:673-1032`, `okMessage`):
- PUSH and PULL refused while riding (`:679-686`); LADDER ADVANCE and RETREAT refused while riding (`:687-702`).
- PUT onto a hook refused at capacity: "@x1 can't hold any more." (`:703-714`).
- SIT refused when full: "@x1 is full." (`:772-778`); closed-lid ENTER_IN refused: "@x1 is closed." (`:780-785`).
- MOUNT of a third party requires that mob to be bound or held, else "@x1 won't let you do that." (`:868-883`).
- WAGON moves only when its riding slot holds a MOB at least one fifth of the wagon's weight, else "@x1 doesn't seem to be moving." (`:950-957`).
- LAND and WAGON may not enter air or watery rooms; WATER_BASED needs a watery surface room at one end and refuses air or underwater rooms (`:944-974`), with "You cannot ride @x1 that way." (`:980`).
- GIVE to a rider needs them to dismount first: "<T-NAME> must disembark first." or "must exit first." for ENTER_IN (`:991-1003`).
- BUY, BID and SELL refused while riding, except at tables and seats (`:1005-1015`): "You can not do that while @x1 @x2."

**Effects.** Riding changes stats (`StdRideable.java:583-603`). A rider in melee range loses the mob's base attack and damage bonuses (`:591-595`). A LADDER rider gains `IS_CLIMBING` and half speed (`:596-601`). `recoverPhyStats` sets `IS_FLYING` or `IS_SWIMMING` on air and water items while they are usable, and `IS_FALLING` once destroyed (`:561-580`). `subjectToWearAndTear` is true only for wooden or vegetation WATER_BASED, LADDER or AIR_FLYING items (`:89-107`).

**Look.** `displayText` lists riders: "a boat being ridden by A, B and C" (`:605-632`). Examine adds the condition text when `usesRemaining() <= 100` (`:1040-1066`), using the thresholds in `StdSiegableBoardable.appendCondition`.

**StdTub.** The tub overrides most verbs: it is "in" and entered with "get(s) into" (`com/planet_ink/coffee_mud/Items/Basic/StdTub.java:182-265`). It is also a liquid container: `CONTAIN_LIQUID`, capacity 1000 (`StdTub.java:56-57`), 2000 units of freshwater held and remaining, 250 per drink (`:43-49`).
- Drinking needs the drinker to own the tub, or the tub to weigh over 1000, or the tub to be ungettable (`StdTub.java:304`); otherwise "You don't have that." (`:319`). Salt water and lamp oil are refused: "You don't want to be drinking @x1." (`:311-316`).
- Each drink subtracts 250 (`:388`). The messages are "You are no longer thirsty." and "You have drunk all you can." (`:391-395`).
- Filling from a Drink item is refused when full ("@x1 is full.", `:322-327`) or when a different liquid is still in the tub: "There is still some @x1 left in @x2. You must empty it before you can fill it with @x3." (`:337-343`).
- The amount moved is `min(held - remaining, source remaining)` (`:359-363`), or all of the source when the tub holds 500000 or more (`:360-361`).
- Riding the tub while it holds clean freshwater is a hygiene event (`:370-380`, `PlayerStats.HYGIENE_WATERCLEAN`, `com/planet_ink/coffee_mud/Common/interfaces/PlayerStats.java:1305`).
- `containsLiquid` returns false for an immovable tub outdoors during drought (`:170-175`).

### 6. Siege vessels (StdSiegableBoardable, plus the rideable volley)

This is the most complex code in the bucket. `StdSiegableBoardable` extends `StdBoardable` (`com/planet_ink/coffee_mud/Items/Basic/StdBoardable.java:43`, outside) and implements `SiegableItem`. Its concrete children are `StdNavigableBoardable` and `GenSiegableBoardable` (outside this bucket).

**State.** `aimings` maps a weapon to its target grid point; `coordinates` is the combat field; `siegeTarget` and `siegeCombatRoom` name the current engagement (`StdSiegableBoardable.java:41-47`).

**Tactical grid.** A room holding vessels is a grid whose size is `room.maxRange()` (`getMagicCoords`, `StdSiegableBoardable.java:436-446`). New vessels get random grid points, retried up to ten times to avoid occupied ones (`:488-494`). All siegable items in a room share one combat field (`:459-481`), guarded by a lock keyed on the room and `_SIEGE_TACTICAL` (`:462`). Distance is `ceil(sqrt(dx^2 + dy^2))` (`:373`). With no target, `rangeToTarget` returns `room.maxRange() + 1` (`:364-367`).

**Commands.** Vessel commands arrive as `TYP_HUH` or `TYP_COMMANDFAIL` messages and are interpreted by the vessel (`StdSiegableBoardable.java:795-805`, `:966-976`). `SiegableItem.SiegeCommand` has three values: `TARGET`, `AIM` and `IMPLODE` (`com/planet_ink/coffee_mud/Items/interfaces/SiegableItem.java:39-45`).
- **TARGET <name>** finds an item in the room that is a visible SiegableItem and that the actor may attack (`mayPhysicallyAttack`, `StdSiegableBoardable.java:604-612`). A factory mob named after the vessel represents it (`getFactoryAttacker`, `:588-599`). The room receives "^F<S-NAME> engage(s) @x1.^?" (`:617`). Both vessels record the engagement (`:621-630`). A named inhabitant can instead be attacked from outside with a ranged or thrown weapon (`:640-723`), paying the Kill command's action cost and temporarily renaming the attacker (`:684-706`).
- **AIM <weapon> <lead>** checks that the vessel has a target (`:859-865`: "Your @x1 must be targeting an enemy to aim weapons."), that the weapon is a siege weapon, and that the target's distance lies within the weapon's `minRange()` to `maxRange()` (`:918-925`). The weapon must be loaded (`:926-934`: "@x1 needs to be LOADed first."). The aim point is the target's current grid point moved `lead` steps: along the target's facing when it is navigable, otherwise in a random direction (`:908-917`). The reply gives the seconds to the next combat tick (`:935`): "<S-NAME> aim(s) <O-NAME> at <T-NAME> (@x1)." (`:936`).
- **IMPLODE** runs only for users with the room-building security flag (`:842-850`). Otherwise the command passes through. An admin's IMPLODE deals 99999 damage ("SINK!!!", `:846-848`).

**What makes a siege weapon fire.** `CMLib.combat().isASiegeWeapon` requires an `AmmunitionWeapon` that is `Rideable`, free-standing and needs ammunition (`com/planet_ink/coffee_mud/Libraries/MUDFight.java:679-687`). `isASiegeWeaponReadyToFire` requires the weapon to be fully crewed when it has a rider capacity: `numRiders() >= riderCapacity()` (`StdSiegableBoardable.java:727-736`).

**Firing tick.** The `TICKID_SPECIALCOMBAT` tick (`StdSiegableBoardable.java:1119-1259`) collects every ready weapon in the area's proper rooms (`:1126-1151`). For each:
- a weapon that needs ammunition and has none counts as `notLoaded` (`:1173-1176`);
- an aimed weapon compares the aim point with the target's current point: `wasHit = Arrays.equals(coordsAimedAt, coordsToHit)` (`:1185`). The target must still be on the leaded point;
- armour then adjusts the result: `armor = target.armor - weapon.attackAdjustment`. If `armor > 0` and a percentage roll is under `armor`, a hit becomes a miss. If `armor < 0` and `-roll > armor`, a miss becomes a hit (`:1186-1196`);
- `CMLib.combat().postSiegeAttack` sends a `WEAPONATTACK` with value 1 or 0 (`com/planet_ink/coffee_mud/Libraries/MUDFight.java:690-708`).
An unaimed ready weapon counts as `notAimed` (`:1207`). The status line is "@x1 of your weapons were not loaded, and @x2 were ready but not aimed." and its variants (`:1211-1221`). An identical repeat is announced at most three times (`:1222-1236`).

**Magazine volley.** A siege weapon with more than one round, hit by a `WEAPONATTACK`, fires its entire magazine: shots = remaining + 1, then the ammunition is set to 0 (`StdSiegableBoardable.java:1365-1369`). Each shot picks a random target among all inhabitants of outdoor rooms in the area (`:1371-1383`). The chance to hit is `targets * 20` percent (`:1384`). A hit does `round(roll(1, weapon damage) / 100 * target max hit points)` (`:1388`, `:1401`). A miss against a chosen target posts an attack result (`:1405-1406`); with no targets the miss is announced to the outside (`:1408`). For each shot the factory shooter takes the target's level and stands in the vessel's own room (`:1392-1398`), and its location is restored afterwards (`:1413`).

**Hull.** Hull is a percentage in `usesRemaining` (0 to 100). Maximum hull is `5 * numberOfProperIDedRooms() + armor` (`StdSiegableBoardable.java:1281-1284`). On `TYP_DAMAGE`, `pctLostAmt = round(value / maxHull * 100)` (`:1452-1454`). Hull percentage falls by that amount (`:1476`). A loss at or above what remains defeats the vessel: hull is set to 0, stats are recovered, `doCombatDefeat` is called and tactical mode is cleared (`:1465-1473`). Hit text is "<O-NAME> fired from <S-NAME> hits and @x1 @x2." on deck and "Something hits and @x1 the @x2." below (`:1459-1463`), with the hit word from `CMLib.combat().standardHitWord` (`:1458`).

**Condition text.** `appendCondition` maps the hull percentage to twelve tiers (see Content).

**Looking over the rail.** `fixArea` installs a listener on the vessel's area (`StdSiegableBoardable.java:179-259`). Someone looking at an open exit into the vessel's room from a neighbouring room sees the deck, prefixed by `head_offTheDeck` ("Off the side you see: ", `com/planet_ink/coffee_mud/Items/Basic/StdNavigableBoardable.java:79`, outside). The first call to `getArea()` also sets the area's theme to fantasy (`StdSiegableBoardable.java:262-274`).

**Engagement permission.** `mayIFight` chooses one mob to represent the area, preferring players with privileges (`:124-177`), and asks `CMLib.combat().mayIAttackThisVessel`.

**Defeat.** `doCombatDefeat` is empty here (`:1261-1264`) and is left to the navigable subclass (outside).

### 7. Rideable volley (StdRideable)

A `WEAPONATTACK` on a rideable that is being ridden by a siege crew uses a second, smaller volley (`com/planet_ink/coffee_mud/Items/Basic/StdRideable.java:1128-1191`). The volley fires the whole magazine when the weapon has more than one round (`:1137-1138`). Targets are the riders of this rideable only (`:1146-1152`). The hit chance is `targets.size() * 20` (`:1153`). Damage to a rider is `msg.value() / phyStats().level()` as a fraction of hit points (`:1163-1164`). The attacker is moved to the rider's room during the shot and restored afterwards (`:1154-1185`).

### 8. Light and smoke: Torch, LightSource, StdSmokable

**Torch.** A `LightSource` preset: `durationTicks = 200`, oak, destroyed when burnt out, goes out in rain, gold value 1 (`com/planet_ink/coffee_mud/Items/Basic/Torch.java:57-62`). Its description says it is "lightly covered in oil near the end" (`:56`).

**LightSource** (outside; consulted for the torch mechanics). `light(true)` starts a tick every tick on `TICKID_LIGHT_FLICKERS` (`com/planet_ink/coffee_mud/Items/Basic/LightSource.java:107-118`). Each tick decrements `durationTickDown`. At zero it prints "@x1 flickers and burns out." and destroys the item if `destroyedWhenBurnedOut` (`LightSource.java:172-211`). Rain or water sets the counter to 1 and ticks at once (`LightSource.java:258-260`: "The rain makes @x1 go out."). A torch set to burn out is therefore destroyed by rain. Lighting in rain or water is refused with "It's too wet to light @x1 here." (`LightSource.java:138-144`). At the default tick a torch lasts 200 ticks, about 800 seconds (13 minutes).

**StdSmokable** (cigar or pipe).
- The duration is stored in the readable text. `setDuration` writes it there (`com/planet_ink/coffee_mud/Items/Basic/StdSmokable.java:76-81`), and `setReadableText` parses an integer back into the duration (`StdSmokable.java:84-92`). Default 200 (`:61`).
- While lit and worn in the mouth, it puffs every `30000 / TICKTIME` ticks, which is 7 at the default tick (`StdSmokable.java:43`, `:206-208`). The message is "<S-NAME> puff(s) on <T-NAME>.", shown when the wearer is an awake, living MOB (`:211-215`). Each puff has a 1-in-1000 chance (`roll(1,1000,0)==1`) of invoking `Disease_Cancer` unless disease is disabled (`:216-221`).
- Lighting rolls a 1-in-100 chance of an addiction (`StdSmokable.java:343`, `:358`, via `getAddictedTo`, `:284-293`). If the cigar has a bowl (capacity > 0), the first item in it is consumed, and its ability effects move onto the cigar as a payload that applies to the smoker while it burns (`:338-354`; tick side at `:247-257`, `:368-372`).
- Burnout in a room prints "@x1 burns out." and destroys the item when `destroyedWhenBurnedOut` (`:227-237`). Burnout in hand prints "<O-NAME> burns out." and recovers the wearer's stats (`:263-276`).
- Rain or water on a carried, lit smokable prints "The rain makes @x1 go out." or "The water makes @x1 go out." (`:313-319`). Extinguishing an unlit one is refused: "@x1 is not lit!" (`:176-181`).

These are the crude or drug-like mechanics in the bucket. They are documented here plainly, and the figures above are the complete set.

### 9. Consumables: perfume, waterskin, wineskin

- **StdPerfume** (`com/planet_ink/coffee_mud/Items/Basic/StdPerfume.java:46-62`). A StdDrink with liquid `RESOURCE_PERFUME`, 10 units, 1 unit per use, capacity 0, 100 gold, glass. Its only use is WEAR. `executeMsg` runs the item's behaviours and effects, subtracts one unit (`:150`), then calls `wearIfAble` (`:151`). `wearIfAble` attaches a non-savable `Prop_MOBEmoter` with the SMELL text and `expires=50` (`:86-104`). If the wearer already has a MOB emoter it refuses with "You can't put any perfume on right now." (`:88-90`). Drinking is refused: "You don't want to be drinking that." (`:115-119`). When the bottle is empty it is destroyed (`:152-153`).
- **Waterskin** and **Wineskin** (`Waterskin.java:46-56`, `Wineskin.java:45-56`). StdDrink presets with 1000 units held and 200 per drink (five drinks), weight 10, leather, 10 gold. Waterskin has capacity 15 and Wineskin has capacity 5. Wineskin sets no liquid type, so it holds the StdDrink default, freshwater (`com/planet_ink/coffee_mud/Items/Basic/StdDrink.java:47`, outside).

### 10. Trapped chest

`TrappedChest` is a `LargeChest` (`com/planet_ink/coffee_mud/Items/Basic/LargeChest.java:33-50`: capacity 150, weight 50, oak, 50 gold). Its constructor attaches a `Trap_Trap` through `CMLib.utensils().setTrapped` and sets `isLocked=false` (`com/planet_ink/coffee_mud/Items/Basic/TrappedChest.java:44-48`). The helper removes existing traps and adds the new one (`com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java:411-420`).

`Trap_Trap` does not spring by itself. It has no message listener: `StdAbility`'s `executeMsg` and `okMessage` are empty (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:2831-2841`), and `Trap_Trap` defines only `tick`, which handles reset and destruction (`com/planet_ink/coffee_mud/Abilities/Traps/Trap_Trap.java:457-473`). Trap subclasses such as `Trap_Open` spring on `TYP_OPEN` (`Abilities/Traps/Trap_Open.java:78`, outside). In the base class, `trapType()` is `roll(1,3,-1)` (`Trap_Trap.java:72-75`), giving 0 to 2. These map to needle (0), pit or blade (1) and gas (2) (`com/planet_ink/coffee_mud/Abilities/interfaces/Trap.java:40-52`). The spell type (3) cannot be produced by the base class. The reset is 60 ticks by default (`Trap_Trap.java:68`).

### 11. Space bodies and the wormhole

**StdSpaceBody** (`com/planet_ink/coffee_mud/Items/Basic/StdSpaceBody.java:40-66`) extends `StdItem` and implements `SpaceObject`. Its default radius is `Kilometer.dm + random % (Kilometer.dm / 2)` (`:61`). Distances are in decameters: `SpaceObject.Distance` gives `Decameter` 1, `Kilometer` 100, `PlanetRadius` 639875 (`com/planet_ink/coffee_mud/core/interfaces/SpaceObject.java:156-176`). So one unit is 10 m, and the default body has a radius of about 0.5 to 1.5 km. Default weight is 100, level 1, stone (`StdSpaceBody.java:62-65`). Mass for physics is `weight * radius` (`:209-213`).

Damage to a body is scaled by its mass and material hardness (`StdSpaceBody.java:216-232`):

    msg.setValue(round((usesRemaining * (value / myMass)) / (hardness * Kilometer.dm)))

The body then returns true, which skips the item's own damage handling (`:225-227`). A collision by a non-tech weapon prints "<S-NAME> is hit by <O-NAME>" to nearby space (`:246-266`).

**StdWormhole** (`com/planet_ink/coffee_mud/Items/Basic/StdWormhole.java:40-61`). Radius is about `PlanetRadius` plus or minus half of it (`:56`), weight 99,999,999 (`:57`), level 1, stone. It is paired through `knownTarget()`. On a ship collision (`TYP_COLLISION`, `:100-106`), if the approach direction is one of the permitted directions (all six by default, `:48-49`), the ship is moved to the far mouth's coordinates along its own heading. The distance is kept from the collision, but never less than both radii plus 2 (`:109-118`).

### 12. Proxies and placeholders

- **StdPortalWrapper** (`com/planet_ink/coffee_mud/Items/Basic/StdPortalWrapper.java:36-120`) and **StdRideableWrapper** (`com/planet_ink/coffee_mud/Items/Basic/StdRideableWrapper.java:36-237`) delegate every read to the wrapped object and make every write a no-op. For example `setRideBasis` (`StdRideableWrapper.java:75-77`) and `addRider` (`:109-111`) do nothing, and a null wrapped object gives `WATER_BASED` (`:71`). The wrapper is chosen by the most specific interface first (`com/planet_ink/coffee_mud/Abilities/Spells/Spell_PlanarBubble.java:262-291`, same pattern in `Spell_PlanarDistortion.java:265-273`): Exit, then Rideable, then Container, then plain item. The planar spells use these proxies to show copies of a room's contents (outside).
- **StdThinItem** (`com/planet_ink/coffee_mud/Items/Basic/StdThinItem.java:36-758`) implements `Item` directly and does nothing. It reports a usable count of 100 (`:698-701`), value 0 (`:714-717`), and no effects. The only occurrence of the class name in the source tree is its own file.

## Content worth mining

### Ride postures

The table in section 5 is reusable as-is: ten postures, each with a verb for entering, leaving, the state and who is in it. A text game can use these phrases directly ("you sit at the table", "the hook is holding X").

### Hull condition text (shared by vessels and boats)

From `appendCondition` (`com/planet_ink/coffee_mud/Items/Basic/StdSiegableBoardable.java:751-787`). The name is inserted where `@x1` is. Colour codes are omitted.

| Hull remaining | Text |
|---|---|
| 0 or less | is nothing but wreckage! |
| under 10% | is near destruction! |
| under 20% | is massively splintered and damaged. |
| under 30% | is extremely splintered and damaged. |
| under 40% | is very splintered and damaged. |
| under 50% | is splintered and damaged. |
| under 60% | is splintered and slightly damaged. |
| under 70% | is showing large splinters. |
| under 80% | is showing some splinters. |
| under 90% | is showing small splinters. |
| under 99% | is no longer in perfect condition. |
| 99% and above | is in perfect condition. |

### Space body names

From `StdSpaceBody.genericName` (`com/planet_ink/coffee_mud/Items/Basic/StdSpaceBody.java:71-98`). Thresholds are in decameters (10 m each), and the reachable branches are marked.

| Radius at least (decameters) | Name | Reachable? |
|---|---|---|
| 347,750,000 | a B-type star | yes |
| 34,775,000 | a G-type star | yes |
| 959,812 | a D-type star | yes, but it also catches every larger body (see Problems) |
| 6,026,800 | an enormous planet | no |
| 3,013,400 | a huge planet | no |
| 1,279,750 | an large planet | no |
| 319,937 | a planet | yes |
| 86,870 | a moon | yes |
| 26,300 | a moonlet | yes |
| below that | an asteroid | yes |

### Presets and stats

| Item | Base class | Key numbers and materials | Source |
|---|---|---|---|
| Torch | LightSource | 200 ticks; oak; burns out and is destroyed; 1 gold | `Torch.java:57-62` |
| Waterskin | StdDrink | 1000 held; 200 per drink; capacity 15; weight 10; leather; 10 gold | `Waterskin.java:46-55` |
| Wineskin | StdDrink | as Waterskin, capacity 5, no liquid set (freshwater default) | `Wineskin.java:45-56` |
| Perfume | StdDrink | 10 uses of 1; capacity 0; glass; 100 gold; emoter `expires=50` | `StdPerfume.java:46-62`, `:96` |
| Cigar | StdSmokable | 200 ticks; puff every 7 ticks; pipeweed; worn on mouth; 5 gold | `StdSmokable.java:43-63` |
| Tub | StdRideable, Drink | 2000 held; 250 per drink; capacity 1000; 4 riders; clay; 500 gold | `StdTub.java:43-63` |
| Boat (default rideable) | StdRideable | weight 2000; capacity 3000; 4 riders; oak; 100 uses | `StdRideable.java:44-86` |
| Portal | StdPortal | weight and capacity 10000; 1 rider; enter-in | `StdPortal.java:62-98` |
| Paper | StdPaper | 2048 characters; one page; paper | `StdPaper.java:50-61` |
| Book | StdPlayerBook | chapters are player-data rows; paper | `StdPlayerBook.java:49-58` |
| Title (deed) | StdTitle | base value 10000; paper; points at a property record | `StdTitle.java:81-90` |
| Quest board | StdQuestBoard | oak; readable; list cache 5 seconds | `StdQuestBoard.java:49-58`, `:131` |
| Trapped chest | TrappedChest | oak; capacity 150; weight 50; not locked; Trap_Trap | `TrappedChest.java:41-49` |
| Wormhole | StdWormhole | weight 99,999,999; level 1 | `StdWormhole.java:55-60` |

### Quest board labels and messages

- Listing rows: `NNN) <quest name>`; detail labels: "Quest", "Giver", "In", "Descrip.".
- Prompt: "Accept this quest (y/N)? " and reply "You are now on the quest '@x1'."
- With no quests listed, the read-back is a blank line (`StdQuestBoard.java:242`, `:283`).

### Other short messages worth reusing

- Title: "It states that the property herein known as '@x1' is deeded to @x2." and "It appears to be a blank property title." (`StdTitle.java:570-575`).
- Portal: "This portal is broken.. nowhere to go!", "@x1 is closed.", "You cannot sit on @x1." (`StdPortal.java:402`, `:407`, `:416`).
- Torch and cigar: "@x1 flickers and burns out." (`LightSource.java:185`), "The rain makes @x1 go out." (`LightSource.java:258`), "It's too wet to light @x1 here." (`LightSource.java:143`), "<S-NAME> puff(s) on <T-NAME>." (`StdSmokable.java:215`).
- Tub: "You are no longer thirsty.", "You have drunk all you can." (`StdTub.java:391-395`).
- Perfume: "You can't put any perfume on right now." (`StdPerfume.java:90`).
- Siege: "You are now targeting @x1.", "<S-NAME> aim(s) <O-NAME> at <T-NAME> (@x1).", "@x1 needs to be LOADed first." (`StdSiegableBoardable.java:828`, `:936`, `:932`).

## Notable design ideas

1. **A deed is a pointer.** The title stores an id, and the owner, price, back taxes and rental flag live on the record it points at (`StdTitle.java:257-279`). Transfers change the record, so every verb that moves a title (sell, give, buy, withdraw) applies one set of rules.
2. **A portal is an item, an exit and a vehicle at once.** `StdPortal` reuses the container for storage, the exit for destinations and the rideable for entry (`StdPortal.java:36`). Entering swaps in temporary exits, walks the mob through them with the normal movement code, and restores the originals in a `finally` (`:455-477`). No special travel code is needed.
3. **Posture-specific verbs.** One rideable class covers a boat, a chair, a bed, a table, a hook, a ladder and a wagon, selected by `Basis`. Each posture chooses its own words and its own restrictions (section 5).
4. **Aimed fire with a lead.** The aim point is a predicted position. A shot hits only if the target is still on that point when the weapon fires, and armour then turns a hit into a miss or a miss into a hit by percentage (`StdSiegableBoardable.java:1185-1196`).
5. **Magazine volleys.** A siege weapon's whole magazine goes off in one tick on a hit (`:1365-1369`), which makes ammunition a tactical choice.
6. **Hull as a percentage.** The same integer (`usesRemaining`) drives damage, the condition text and defeat (`:1452-1476`). One table turns it into prose.
7. **Consumables that hand over behaviour.** The perfume is a timed emoter (`StdPerfume.java:96-101`), and the cigar's bowl item is a payload of abilities that apply to the smoker while burning (`StdSmokable.java:338-354`).
8. **Read-through proxies.** The wrapper classes delegate reads and ignore writes, and are picked by the most specific interface (`Spell_PlanarBubble.java:262-291`). This is a clean way to show one object as another without copying it.
9. **Chapters as keyed rows.** A book is a set of rows with a shared book key and a chapter counter, and the subject delimiter is escaped (`StdPlayerBook.java:195-200`).
10. **Filters as data.** The quest board's behaviour is set by a short string of masks and filters in its own text (`StdQuestBoard.java:80-86`), so builders vary it without new code.

## Problems and cruft

Listed roughly by severity. "Unverified" marks points whose runtime effect was not traced.

1. **Page-limit message reports the maximum, not the space left.** `StdPaper.java:161` passes `getMaxCharsPerPage()` (2048) where the text says "fit @x1 more characters". Verified by reading.
2. **Page limit is bypassed by rewriting.** `StdPaper.okMessage` checks only `TYP_WRITE` (`StdPaper.java:154-155`); `TYP_REWRITE` sets the whole text (`com/planet_ink/coffee_mud/Items/Basic/StdItem.java:1728-1735`) with no length check.
3. **Perfume spends a dose even when the smell cannot go on.** The unit is subtracted at `StdPerfume.java:150` before `wearIfAble` at `:151`, and `wearIfAble` can refuse at `:88-90`.
4. **Dead condition in the perfume.** `StdPerfume.java:97` tests `s.toUpperCase().trim().startsWith("SMELL ")`, but `s` always begins with `;` or `expires=50 `, so the branch never runs. The effect is unchanged, because the prefix is always added at `:100`.
5. **Smell lines also become ambient emotes.** In `com/planet_ink/coffee_mud/Behaviors/Emoter.java:104-121` the SMELL branch is followed by a separate `if` for PUFF, with an `else` that adds every non-PUFF line to the ambient emote list. A SMELL line therefore goes into both lists. This is outside the bucket and the in-game effect was not traced.
6. **Unverified: an empty smell list may never expire.** A default perfume has no smell text (`StdPerfume.java:44`). The emoter then has no emotes, and expiry counts down only when `emotes.size()>0` (`Behaviors/Emoter.java:404-408`). The `Prop_MOBEmoter` would then stay and block further perfume (`StdPerfume.java:88-90`) until cleared.
7. **Property-tax notice can never print.** `StdTitle.java:687` requires `taxs.length()==0` and `CMath.s_double(taxs) > 0.0`. An empty string gives 0.0 (`com/planet_ink/coffee_mud/core/CMath.java:2016-2026`), so the branch is dead. The likely intent was `length()>0`.
8. **Space-body naming is wrong for large bodies.** The D-type check (`StdSpaceBody.java:77-78`, 959,812) comes before the enormous, huge and large-planet checks (`:80-87`), so every body with radius at least about 9,598 km is named a D-type star. Those three branches are unreachable. Jupiter-sized bodies would be called stars.
9. **Quest filter TAREAMASK is parsed but never applied.** It is compiled into `tareaMask` (`StdQuestBoard.java:64`, `:85`) and tested only for presence (`:168`). The guide describes it as a mask for areas other than the giver's (`guides/ArchonGuide.html:3812-3815`). Unverified whether the AAREAMASK logic (`:199-203`) is inverted: it passes a quest when some area fails the mask, while the guide describes a mask for areas referenced by the quest (`ArchonGuide.html:3816-3817`).
10. **Quest board hashes twice.** `calculateQuestHash()` is computed in the test (`StdQuestBoard.java:132`) and again in the assignment (`:134`), with no lock between them. Harmless on a single thread but wasteful.
11. **Quest detail may dereference a missing room.** `questGiverR.displayText(mob)` and `questGiverR.getArea()` (`StdQuestBoard.java:275-276`) assume the giver has a location. Unverified whether a quest giver can be unplaced at read time.
12. **Book chapter keys can collide after deletion.** The chapter key uses the current count (`StdPlayerBook.java:199`), and `StdBook.java:467` can delete a middle chapter. The next chapter then reuses an existing key. The database's behaviour on a duplicate key was not traced.
13. **Trapped chest never springs.** See section 10. `TrappedChest` attaches a `Trap_Trap`, which has no trigger. `TRAP_SPELL` cannot be selected (`Trap_Trap.java:72-75`). The comment on the reset says "5 minute" (`:68`), but 60 ticks at the default tick is 4 minutes. `Exits/TrappedLockedDoor.java:44` uses the same pattern (not traced).
14. **Portal destination is re-rolled on every call.** `getDestinationRoom` picks at random (`StdPortal.java:338-345`) and is called from look (`:541`), from the sit check (`:400`), from the enter swap (`:452`) and from `lastRoomUsedFrom` (`:350`). A multi-destination portal can show one room and send you to another.
15. **Portal entry is recognised by message text.** `StdPortal.java:398` and `:449` search the message for the mount string. Any change to the wording breaks entry, and a custom mount string can be matched by accident.
16. **Portal defaultsLocked returns the wrong flag.** `StdPortal.java:508-511` returns `hasALock()`, while `StdContainer` returns its own `defaultsLocked` field (`com/planet_ink/coffee_mud/Items/Basic/StdContainer.java:614-616`). Intent unverified.
17. **Portal temporary exit name is fixed at creation.** `getTemporaryExits` sets the name only once (`StdPortal.java:353-367`), so renaming the portal later has no effect on the temporary exits.
18. **Rideable hook-contents list uses the wrong count.** `StdRideable.java:653` tests `r == numRiders()-1` while looping over the hook's contents, so the word "and" is never inserted for hooks.
19. **Two volleys with different damage formulas.** The rideable volley uses `value / level` (`StdRideable.java:1163`); the vessel volley uses a weapon damage roll divided by 100 (`StdSiegableBoardable.java:1388`). The same logic is written out twice (`StdRideable.java:1128-1191`; `StdSiegableBoardable.java:1365-1415`).
20. **Vessel volley fires at anyone in the outdoor area.** `StdSiegableBoardable.java:1374-1381` takes every inhabitant of outdoor rooms. The rideable version takes only riders (`StdRideable.java:1146-1150`). This may be intended as a battlefield, but it is friendly-fire by design.
21. **Unguarded casts in THROW.** `StdSiegableBoardable.java:1076` and `:1082` cast a SiegableItem to `StdNavigableBoardable` without a test. `GenSiegableBoardable` is a SiegableItem that is not navigable (`StdSiegableBoardable.java:39`). A ClassCastException is possible; reachability not verified.
22. **Small hull damage is lost.** `pctLostAmt = round(value / maxHull * 100)` (`StdSiegableBoardable.java:1454`) rounds any loss under 0.5% of maximum hull to zero. The same code divides by a maximum hull that can be zero, which gives infinity and then `(int)` wraps it to -1, so the damage is silently ignored (`:1452`, `:1283`).
23. **Area theme side effect.** The first `getArea()` call sets the area's theme to fantasy (`StdSiegableBoardable.java:267-270`), which surprises any other caller.
24. **Tub overflow guard drops liquid.** When `remaining + taken` would exceed `Integer.MAX_VALUE`, the tub skips the add (`StdTub.java:410-411`), but the source has already been drained (`:407`).
25. **Wineskin name does not match its contents.** It is called a wineskin and holds freshwater (`Wineskin.java:45-56`; `StdDrink.java:47`).
26. **Random seeds per object.** `StdSpaceBody.java:60` and `StdWormhole.java:55` create `new Random(System.currentTimeMillis())` for every instance. Generated radii cannot be reproduced.
27. **Cigar duration shares the text field.** `setReadableText` changes the duration whenever the text is an integer (`StdSmokable.java:84-92`). Any code that rewrites the text can change the burn time.
28. **Typos and cruft.** "an large planet" (`StdSpaceBody.java:87`); "will be engage in" (`StdSiegableBoardable.java:955`); "porcelin" (`StdTub.java:59`); duplicate conditions in `StdQuestBoard.java:113-116`; `StdThinItem` has no users; `StdTitle.value()` writes to `baseGoldValue` as a side effect (`StdTitle.java:69-72`).

## Takeaways for a Python SUD

**Borrow.**
- **Deed as a pointer.** Keep ownership on the property, not on the deed. A deed holds an id and a few verbs.

  ```python
  @dataclass
  class Deed:
      property_id: str          # the owner and price live on Property
  ```

- **Portal as container, exit and vehicle.** A portal holds a list of destination room ids and picks one on use. Entry is one movement call, not a rewrite of the map. Decide once, not on every look: pick the destination when the actor enters, and show the same one in the description.
- **Posture table.** One `Rideable` class with a `Basis` enum and a dictionary of verbs per basis. Restrictions are small functions keyed on the verb.
- **Light and smoke as timed objects.** `Light(turns=200, lit=False)` decremented once per turn, destroyed at zero, extinguished by weather. A `Smokable` with `puff_every=7` and a list of effect payloads.
- **Condition text table.** The twelve hull tiers work for any damaged object: a boat, a door, a chair.
- **Timed emoter.** A consumable can attach a list of lines with a remaining count, removed when the count reaches zero.
- **Aimed shots with a lead.** A small version of the siege rule suits a sea or fortress scene: aim at a predicted square, the shot lands only if the target is still there, and armour gives a percentage chance to deflect.

**Simplify.**
- Drop the zapper masks and the cache. A quest board is `available_quests(player, filters)`, where filters are plain predicates.
- Drop the factory mobs, the rename hack and the area listeners from the siege code. They exist to make NPC-like objects fit the engine.
- Replace the posture verbs with one table, not per-class overrides.
- Drop the double bookkeeping of the trap, the reset timers and the wrapper machinery. Plain fields do the same job.

**Drop.**
- Space bodies and the wormhole, unless the SUD has space travel.
- Clan, shop, bank and marriage rules on titles. Keep ownership, transfer and "destroy if not allowed".
- Multi-author books. A single-author `Note` with text is enough.
- The volley against all inhabitants. In a single-player game the tactical battle is one actor against NPCs, so the volley targets only the enemy.

**Multiplayer assumptions to rethink.** The quest board's "accept quest" flow, the book's author table and the vessel's engagement rules all assume several human players. The rideable's "bound or held" rule, the title's clan rules and the siege combat each need one-player versions.

## Coverage

- **Read in full (19 of 19):** `StdSiegableBoardable.java` (1531 lines, in two reads), `StdRideable.java`, `StdThinItem.java`, `StdTitle.java`, `StdPortal.java`, `StdTub.java`, `StdSmokable.java`, `StdQuestBoard.java`, `StdSpaceBody.java`, `StdRideableWrapper.java`, `StdPlayerBook.java`, `StdPaper.java`, `StdPerfume.java`, `StdWormhole.java`, `StdPortalWrapper.java`, `Torch.java`, `Wineskin.java`, `Waterskin.java`, `TrappedChest.java`.
- **Outside the bucket, consulted in part:** `Items/Basic/LightSource.java` (tick, burnout, rain, hold), `Items/Basic/LargeChest.java`, `Items/Basic/StdItem.java` (write and rewrite), `Items/Basic/StdBook.java` (delete, base methods), `Items/Basic/StdDrink.java` (liquid default), `Items/Basic/StdContainer.java` (defaultsLocked), `Behaviors/Emoter.java` (smell branch, expiry), `Abilities/Traps/Trap_Trap.java`, `Abilities/Traps/Trap_Open.java`, `Abilities/StdAbility.java` (message hooks), `Abilities/interfaces/Trap.java`, `Abilities/Spells/Spell_PlanarBubble.java` and `Spell_PlanarDistortion.java` (wrapper use), `Libraries/CoffeeUtensils.java` (setTrapped), `Libraries/MUDFight.java` (siege weapon test, siege attack), `Commands/Read.java` (READ parsing), `core/CMath.java`, `core/CMParms.java`, `Libraries/Dice.java`, `core/Directions.java`, `core/CMProps.java`, `core/interfaces/SpaceObject.java`, `core/interfaces/Rideable.java`, `Items/interfaces/SiegableItem.java`, `Common/interfaces/PhyStats.java`, `Common/interfaces/PlayerStats.java`, `guides/ArchonGuide.html` (quest board parameters).
- **Skipped:** the other buckets' classes named in the Key files table (`StdBoardable`, `StdNavigableBoardable`, `StdGrapples`, `GenPortal`, `StdSiegeWeapon` and the Gen classes) were checked only as subclass lists and by name.
