# Items/Basic part 3: containers, drinks, books, vehicles, lights and curiosities

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b126` (Items/Basic [part 3/5: HoleInTheGround .. StdInnKey]) · 35 files · 7247 lines of Java.

## Summary

This bucket is the middle of the basic item library: the container family (StdContainer with its lids, locks and sub-types), liquid and food containers (StdDrink, StdFood), light sources, coins, books and learnable scrolls, plus a set of special objects. Most classes are short subclasses that set defaults in their constructors and override one or two hooks, so the real logic sits in a few files. The distinctive ones are StdBoardable (a vehicle that owns its own room graph and docks at ports), StdDissertation (a scroll that teaches abilities), StdBook (chapters stored as journal records, with permissions in a text string), LightSource and Lantern (fuel counted in ticks, put out by weather), StdInnKey (a key that expires back into the innkeeper's stock) and StdGrapples (a line joining two ships). The bucket also contains real defects, listed under Problems, including a self-comparison that disables an upper-bound check in StdBook and a copy-paste error in the ring table. The base item rules belong to b172; StdContainer is described here only as far as the subclasses depend on it.

Conventions: file paths are given from `com/planet_ink/coffee_mud/` (the Java package root under the CoffeeMud folder). Line numbers refer to the files as read. Timing figures assume the shipped defaults (see the Timing subsection).

## Key files and types

| File | Class and role | Lines |
|---|---|---|
| `Items/Basic/StdBoardable.java` | Vehicle or ship item that owns a whole Area of rooms, docks at ports, can be bought, renamed and transferred. Extends StdPortal. | 1254 |
| `Items/Basic/StdBook.java` | Multi-chapter book; chapters are journal records in the database; READ, WRITE and ADMIN permissions in its text. | 868 |
| `Items/Basic/StdContainer.java` | Base container: weight capacity, lid, lock, key name, default-state timer, contents held as back-references. | 783 |
| `Items/Basic/StdDissertation.java` | Scroll (Scroll interface) that teaches one or more abilities. | 496 |
| `Items/Basic/StdDrink.java` | Liquid container base: quench amount, fill and pour rules, drinking. Extends StdContainer. | 414 |
| `Items/Basic/LightSource.java` | Burning light base: fuel in ticks, rain and water extinguish it. | 326 |
| `Items/Basic/StdGrapples.java` | Grapple line joining the areas of two boardable ships. Extends StdPortal. | 273 |
| `Items/Basic/StdCoins.java` | Stack of coins: count held in the ability stat, currency and denomination in the text. | 251 |
| `Items/Basic/StdContainerWrapper.java` | Wrapper that presents a container inside a planar-bubble copy of a room. | 246 |
| `Items/Basic/HoleInTheGround.java` | Buried stash (extends GenContainer), hidden until dug, expires when no players are present. | 195 |
| `Items/Basic/StdFood.java` | Food: nourishment, bites, overeating roll, soiling of containers. | 194 |
| `Items/Basic/SeaMap.java` | Map item that draws an ASCII chart of known rooms from their domain types. Extends BardMap. | 189 |
| `Items/Basic/Ring_Ornamental.java` | Ring with one of 14 random kinds (name, material, value). | 185 |
| `Items/Basic/StdInnKey.java` | Innkeeper's key to one inn room; expires back into stock. Extends StdKey. | 157 |
| `Items/Basic/StdCage.java` | Cage container for bodies and caged animals; an open-door timer releases occupants. | 132 |
| `Items/Basic/Lantern.java` | Refillable lamp (extends LightSource), 800 ticks of fuel, refilled from lamp oil. | 120 |
| `Items/Basic/QuestPoint.java` | Ground coin that gives one quest point when taken. | 96 |
| `Items/Basic/StdCageRideable.java` | Cage wagon, extends Items/Basic `StdRideable` (b128). | 96 |
| `Items/Basic/Pot.java` | Cooking pot (StdDrink subclass). | 67 |
| `Items/Basic/Star.java` | Space star (GenSpaceBody). | 66 |
| `Items/Basic/Ring.java` | Plain worn ring, either ring finger. | 63 |
| `Items/Basic/Pan.java` | Iron pan (StdDrink subclass). | 61 |
| `Items/Basic/OilFlask.java` | Lamp-oil flask (StdDrink subclass), five units. | 60 |
| `Items/Basic/Spring.java` | Fixed, endless fresh-water spring (StdDrink subclass). | 60 |
| `Items/Basic/MassiveStar.java` | Large star (GenSpaceBody). | 59 |
| `Items/Basic/Moonlet.java` | Small moon (GenSpaceBody). | 57 |
| `Items/Basic/HorseCart.java` | Horse cart, extends Items/Basic `StdRideable` (b128). | 56 |
| `Items/Basic/Ration.java` | Ration pack (StdFood subclass). | 55 |
| `Items/Basic/LargeChest.java` | Large lockable chest (LockableContainer). | 54 |
| `Items/Basic/SmallChest.java` | Small lockable chest (LockableContainer). | 54 |
| `Items/Basic/LargeSack.java` | Large sack, no lid (StdContainer). | 53 |
| `Items/Basic/SmallSack.java` | Small sack, no lid (StdContainer). | 53 |
| `Items/Basic/Parchment.java` | Sheet of parchment (GenReadable). | 52 |
| `Items/Basic/LockableContainer.java` | Container with lid and lock, starts closed and locked. | 51 |
| `Items/Basic/NeedleChest.java` | Large chest with a needle trap, starts unlocked (extends LargeChest). | 51 |

Related classes outside this bucket that the code above depends on: `Items/Basic/StdPortal.java:36` (b128), `Items/Basic/StdRideable.java:36` (b128, extends StdContainer; not the MOB-based `MOBS/StdRideable.java`), `Items/Basic/GenContainer.java:35`, `Items/Basic/StdKey.java:35`, `Items/Basic/StdMap.java:36`, `Items/Basic/GenSpaceBody.java:35`, `Items/Basic/StdSpaceBody.java:40`, `Items/interfaces/Boardable.java:122-129`, `core/interfaces/SpaceObject.java`, and the boardable area class `Areas/StdBoardableShip.java` (b101).

## How it works

### 1. Containers, lids and locks (StdContainer and its family)

**Data model.**
- `capacity` is a weight limit, not an item count. A PUT is refused when the new item weighs more than `capacity` (`Items/Basic/StdContainer.java:163`) or when `recursiveWeight()+weight` exceeds it (line 169). Messages: "is too small" when empty (172), "is full" otherwise (177).
- `containType` is a bitmask of accepted categories. `canContain()` (lines 482-581) maps each bit to a type test: liquid, coins, swords, daggers, keys, drinkables, eatables, clothes, footwear, raw materials, other weapons, one-handed weapons, bodies, smokeables, caged animals, readables, scrolls and tech components. A value of 0 accepts anything (484-485).
- Lid and lock state is six fields: `hasALid`, `isOpen`, `hasALock`, `isLocked`, `defaultsClosed`, `defaultsLocked` (lines 43-48). `setDoorsNLocks` writes them together (620-628).
- The key name is the container's `miscText` (`setMiscText` 631-636, `keyName` 639-642). A key opens the lock when its text is equal to that name: `((DoorKey)item).getKey().equals(keyName())` (line 349).
- Contents are not stored in the container. Each item points to its container with `setContainer`, and `getContents()` scans the owner's item list for `I.container()==this` (lines 750-765). `recursiveWeight()` sums the items whose `ultimateContainer` is this container (698-726). A chest's contents therefore stay in the room's or mob's list. This matters for the wrapper in section 12.

**Control flow.**
- `okMessage` (100-370) handles PUT (117-191), GET (193-256), REMOVE (257-281), CLOSE (282-293), OPEN (294-310) and LOCK or UNLOCK (311-363). GET refuses an item whose level is more than `10 + mob.level/5` above the taker's, unless the taker is a shopkeeper or in a leveless race or class (line 213). GET also checks carry weight and item count (222, 228).
- Keys are searched in the mob's inventory (344-359). A key inside a top-level container counts only when that container's contain types include CONTAIN_KEYS (350-352).
- `executeMsg` (393-467): PUT sets the container (404). OPEN (417-427) and UNLOCK (434-443) start the `TICKID_EXIT_REOPEN` timer, with `openDelayTicks` (default 30, line 51), only when the container sits in a room and is not gettable. This is how fixed furniture doors re-close by themselves.
- `tick` (373-390): on `TICKID_EXIT_REOPEN` the container returns to its defaults through `setDoorsNLocks` (386). It stays open instead if a key for it is lying inside and the container is unlocked or open (381-383).

**Timing.** 30 ticks at 4 seconds each is two real minutes (`core/CMProps.java:681`, `coffeemud.ini:2151`).

**Messages.** "There is nothing to open on X." (296), "X is locked." (307), "You don't have the key." (360), "You can't put anything in X!" (152), "X is too powerful to endure possessing it." (218).

**StdCage** (`Items/Basic/StdCage.java`). Capacity 1000 weight, and it accepts bodies and caged animals (line 50). The class sets no lid, so `StdContainer.okMessage` refuses OPEN ("There is nothing to open", `Items/Basic/StdContainer.java:295-299`) unless one is configured. When the cage is open and the timer fires, `StdCage.tick` (66-89) releases each `CagedAnimal` inside, then falls through to the StdContainer tick, which resets the lid and lock to their defaults. A release calls `unCageMe`, brings the mob to life in the room, and destroys the cage's item. Message: "<S-NAME> escapes from <O-NAME>!" (line 82). Closing the cage cancels the timer (101-102). LOOK or EXAMINE on a closed cage opens it briefly to show the contents (113-125).

**NeedleChest** (`Items/Basic/NeedleChest.java:41-50`). A LargeChest with `Trap_OpenNeedle` attached through `CMLib.utensils().setTrapped` (lines 44-46), and unlocked (line 47). The trap class is `Abilities/Traps/Trap_OpenNeedle.java`, which is not traced here.

### 2. Vehicles and ships (StdBoardable, StdGrapples, wagons)

**Data model** (`Items/Basic/StdBoardable.java`).
- The item extends StdPortal and implements Boardable (line 42). Defaults: `price` 1000 (line 52), weight 10000 (72), capacity 0 (73), uses 100 (74), base value 20000 (75), not gettable (77).
- It owns an Area of rooms, created lazily by `getArea()` (127-149). The area class is `StdBoardableShip` (`getAreaClassType`, 101-104). Its name is `UNNAMED_` followed by digits from `Math.random()` (136-139). One first room is added (141-146), and `readableText` is set to that room's ID (146).
- `setArea(xml)` unpacks a saved area (151-196) and totals the value of every item in its rooms into `internalPrice` (169-180).
- `value()` (420-427) returns base value + `price` + `internalPrice`. A ship's worth therefore includes what is aboard it.
- `homePortID` records the port the ship is docked at (`dockHere` 199-212, `unDock` 215-226, `getIsDocked` 229-236).
- `readableText` also holds a semicolon list of room IDs. `getDestinationRoom` (347-355) picks one at random with `CMLib.dice().roll(1,V.size(),-1)` (line 353). This is where a portal move lands.

**Control flow.**
- `executeMsg` (938-1008) forwards each action to the rooms of the ship's area (`sendAreaMessage` 724-777). Outdoor rooms receive the message as outer viewers (`canViewOuterRoom` 627-630). When the actor is not riding the ship, the outer message gets the prefix in `head_offTheDeck` (line 998), whose default text is "Outside, you see: " (line 58).
- `okMessage` (882-935) asks every room whether it accepts the message, through `confirmAreaMessage` (779-829). That is the veto path. Lines 793-812 repair a ship found inside itself.
- Security (`securityCheck` 601-619): an admin may always use it, and anyone may when the ship has no owner (608-609). Otherwise the owner, or a member with the property privilege (`CMLib.law().doesOwnThisProperty`, 610-611), may use it. A sailor wearing the owner clan's cap also qualifies (612-617).
- Purchase (`okMessage` 897-909 and `executeMsg` 943-956): a shopkeeper sale calls `transferOwnership` (1010-1194). That removes the old owner's record (1010-1033), records the new owner or clan (1034-1046), and prompts the buyer for a new name through an `InputCallback` (1054-1059). The name must be unused (1073-1082) and pass `CMLib.login().isOkName` (1086). After renaming, the ship docks at the nearest port (1113-1123) and is registered in the buyer's extra items (1149-1153) or the clan's (1140-1146).
- `isSavable` (1247-1253) is true only when the ship is unowned. Owned ships are saved through their owner's extra-items list instead.
- `rename` (475-505) replaces name markers in the ship's text. The markers are `NAME`, `NEWNAME`, `SHIPNAME` and `SHIP`, in upper or lower case, between `<>`, `[]`, `{}` or `()` (`Items/interfaces/Boardable.java:122-129`).
- `tick` (508-531) does nothing except on the area tick.

**Messages.** "X is now signed over to Y." (1096, 1160). "Enter a new name for your X:" (1059). "That is not a permitted name." (1089). "You'll find X docked at 'Y'." (1123).

**StdGrapples** (`Items/Basic/StdGrapples.java`). A set of grapple lines between two ships that are in the same room.
- Throwing the grapples from one ship's area to a room in another ship's area (`executeMsg` THROW, 150-178) adds a trailer runnable. The runnable sets `sourceR` and `targetR`, moves the item into the source room, and adds it to the target room (168-173).
- `ungrappleCheck` (84-114) releases the line when the rooms no longer exist, when both ends are in the same area, when either area is not boardable, or when the two ships are not in the same room (106-112).
- `okMessage` ADVANCE (188-227) refuses to move a grappled ship: "Your ship is grappled to X and cannot move." (lines 209 and 220).
- SIT on the grapples is turned into an ENTER that crosses to the other ship when the far room exists (228-242). Otherwise the message is "You cannot sit on X." (243).
- GET or DROP releases the grapple (142-147).

**Wagons.** `Items/Basic/HorseCart.java` (capacity 1000 weight, item weight 500, base value 500, WAGON basis, rider capacity 10; lines 47-52) and `Items/Basic/StdCageRideable.java` (capacity 5000, weight 1000, WAGON basis, cage containment; lines 49-54) both extend the item `StdRideable`. A LOOK on the cage wagon opens it briefly to show the contents (`StdCageRideable.java:67-76`).

### 3. Books and writing (StdBook, Parchment)

**Data model.**
- A book's chapters are database journal records, keyed by the book's current name: `getJournalName()` returns `Name()` (`Items/Basic/StdBook.java:602-605`). Chapters are read with `DBReadJournalMsgsByCreateDate` (612-615), added with `DBWriteJournal` (617-620), edited (622-630) and deleted (632-635).
- Permissions live in the book's own text, as `KEY`, `READ`, `WRITE`, `REPLY` and `ADMIN` parameters, parsed by `CMParms.parseEQParms` (`getParm` 836-845). READ and WRITE hold zapper masks. `canRead` (218-227) and `canWrite` (230-239) check them. ADMIN or the JOURNALS security flag bypasses both.
- `maxPages` (line 59) limits how many chapters a book takes (checked at 123-131). `maxCharsPage` (60) limits chapter length (checked at 432-433 and 446-447).
- The item's description tells the player the usage: "Enter `READ [NUMBER] [BOOK]` to read a chapter. Use your WRITE skill to add new chapters." (line 53).

**Control flow.**
- READ (`executeMsg` 293-402) checks permission (305-308), parses the words NEW, ALL and OLD (313-325), and calls `DBRead` (326).
- `DBRead` (637-756). With no chapter number it builds a table of contents (663-685). It lists chapters addressed to ALL, to the reader, from the reader, or to a mask (670-673). Only the newest `JOURNALLIMIT` chapters are shown (686-703), and when some are hidden it prints "Use READ ALL [BOOK] to see missing chapters." (715). With a chapter number it returns that one chapter (717-753).
- WRITE and REWRITE (403-589) open a multi-line editor through `CMLib.journals().makeMessageASync` (551-564). Inline text is also accepted (495-499), and a "::subject::" prefix sets the subject (502-510). EDIT and DELETE on a chapter require that the caller wrote it, unless admin (`okMessage` 153-162, 176-185, 199-208).
- Pages whose text starts with `<cmvp>` are web-macro pages. They are run through `CMLib.webMacroFilter().virtualPageFilter` when read (`DBRead` 743-745). Only admins and JOURNALS-privileged mobs may write them (423-429).

**Messages.** "You are not allowed to write on X" (`okMessage` 120), "All the available pages in X are used." (128), "There is no Chapter N" (150), "You need permission to edit that chapter." (159), "Chapter added." (441), "Chapter modified." (456), "Aborted." (542).

**Parchment** (`Items/Basic/Parchment.java:41-50`) is a paper sheet (`RESOURCE_PAPER`, weight 1) on the GenReadable base. Its text is set in the data.

### 4. Learning scrolls (StdDissertation)

**Data model.**
- The list of abilities is stored in `miscText` as `AbilityID(params);AbilityID(params)` (`buildSpellsList` 340-352, parser 354-377). Abilities in the archon domain are dropped (line 369).
- Value is `200 + 100 × lowestQualifyingLevel(ability)` summed over the abilities (`calculateValue` 379-388). One level-5 spell therefore gives a base value of 700. Default base value is 200 (line 60), and default uses are 0 (line 61).
- `readableScrollBy` restricts reading to one name. Null means anyone (`isReadableScrollBy` 418-421).

**Control flow** (`readIfAble` 118-308).
1. The reader must own the scroll (`mob.isMine`, line 120) and be on the readable list (122). Otherwise: "You don't quite understand the writing." (306).
2. With no argument, the scroll lists its contents: "The dissertation contains instructions for the following:" followed by "Level NN: name" lines (292-301).
3. With an argument, the spell name is matched. The code tries an exact match, then a prefix match, and then drops trailing words one at a time, keeping them as parameters (131-152). If there is exactly one spell and nothing matched, that spell is used (154-155).
4. If the reader is below the spell's level (`qualifiesByLevel`, line 159), the class maps the ability's class code to a craft skill: Skill_Skillcraft, Skill_Spellcraft, Skill_Prayercraft, Skill_Songcraft, Skill_Thiefcraft or Skill_Chantcraft (lines 163-192). The reader's copy of that skill gets the ability ID as its misc text, and an OK_VISUAL message runs through it (193-212). The effect of that skill is not traced here.
5. Learning requires `useTheScroll`, which checks `canBeLearnedBy(null, mob)` (80-86). For a player with a session, a yes/no prompt follows: "Would you like to learn X from Y (y/N)?" (line 269, prompt 262-285). The session uppercases the reply and reads only its first character (`Common/interfaces/Session.java:1343-1357`), so `equals("Y")` at line 280 is correct. Monsters and sessionless mobs learn at once (258-259).
6. `learnIt` (221-256) makes a temporary teacher MOB at the lowest qualifying level, gives the teacher a copy of the ability at proficiency 100, and calls `CMLib.expertises().postTeach(teacher,mob,learnA)` (239-241). It then sets the reader's own proficiency to 0 (242-244). The code comment at line 244 says "really? Why use these, ever?".
7. Uses: if `usesRemaining` is below 2 the scroll is destroyed, otherwise it drops by one (251-254). With the default of 0 the first learning destroys it, so a scroll teaches as many times as its uses (minimum once).

**Other hooks.** `executeMsg` (397-415): a TYP_READ with no source or other message calls `readIfAble` (404-406). Any other read gets a trailer message (408). `getStat` and `setStat` expose CLASS, LEVEL, ABILITY and TEXT for the builders (429-465).

### 5. Liquids (StdDrink and its subclasses)

**Data model** (`Items/Basic/StdDrink.java:42-47`). `amountOfThirstQuenched` 250 per drink. `amountOfLiquidHeld` 2000 and `amountOfLiquidRemaining` 2000. `liquidType` fresh water. `disappearsAfterDrinking` false. `decayTime` 0. `containType` is CONTAIN_LIQUID (line 57) and `capacity` is 0 (line 56), so drink containers hold no solids by default.

**Drinking** (`okMessage` 225-243, `executeMsg` 330-342).
- Anyone may drink from an item they carry, from one heavier than 1000 weight (line 226), or from a fixed one (not gettable). A fountain is therefore drinkable where it stands.
- Refused messages: "X is empty." (230), and "You don't want to be drinking X." for saltwater or lamp oil (237, 234-235).
- Each drink subtracts 250 from the remaining amount (331). It adds to the thirst state through `adjThirst(amountOfThirstQuenched, maxThirst)` (333). `adjThirst` multiplies the change by THIRST_GAIN_PCT and returns false when the value hits the cap (`Common/DefaultCharState.java:250-280`). Thirst is a satiation counter: zero means thirsty.
- Messages: "You are no longer thirsty." (335) when the drinker was at zero before the drink. "You have drunk all you can." (338) when the cap was reached.
- A RawMaterial liquid (a world-generated one) is destroyed when it empties (340-341).

**Filling and pouring** (`okMessage` 244-302, `amountTakenToFillMe` 311-319, `executeMsg` 343-392).
- Refused as "X is full." when the held contents reach the capacity (245-249), unless the held amount is 500000 or more (246). The Spring's 999999 units sit above that limit (`Spring.java:48-49`).
- Filling from a container of drinks uses the first liquid found in it (253-278).
- A different liquid type is refused while the target still holds liquid (290-296), with a message naming both liquids.
- The amount moved is the room left in the target, capped at what the source holds (313-318).
- A RawMaterial source pours a copy of itself into a container when the target is empty (352-375).

**Drought.** `containsLiquid` (193-213) returns false for a fixed container in a room whose weather is `WEATHER_DROUGHT` (195-200). A well therefore dries up in drought.

**Subclasses** (the catalogue is in Content worth mining).
- `Pot` (`Items/Basic/Pot.java:41-58`) starts empty (52), holds 20 units (49-50), accepts drinkables and eatables (54), and gives 1 quench per drink (51).
- `Pan` (`Items/Basic/Pan.java:41-52`) overrides no liquid value, so it inherits the base 2000 units of fresh water (see Problems).
- `OilFlask` (`Items/Basic/OilFlask.java:43-58`) holds five units of lamp oil and disappears after drinking (54).
- `Spring` (`Items/Basic/Spring.java:43-58`) holds 999999 units of fresh water and is not gettable (line 55).

### 6. Light sources (LightSource and Lantern)

**Data model** (`Items/Basic/LightSource.java:42-46`). `durationTicks` is the full fuel, 200 by default. `durationTickDown` counts down while lit. `destroyedWhenBurnedOut` is true by default and `goesOutInTheRain` is true by default.

**Lighting.**
- TYP_HOLD (lighting) is refused when the fuel is used up: "X looks used up." (130-133). It is also refused when the light is in the rain (and `goesOutInTheRain`) or in water, if the holder owns it and fuel remains (136-145): "It's too wet to light X here." `inTheRain` (214-221) means outdoors with weather RAIN or THUNDERSTORM. `inTheWater` (223-234) means an underwater room or a swimming mob who is not flying or riding.
- On a successful light, `light(true)` (108-118) starts a one-per-tick countdown with `TICKID_LIGHT_FLICKERS`. Lighting also sends "<S-NAME> light(s) up X." as a trailer (279) and "X is already lit." if it was lit (281).
- Burn-out (`tick` 172-212). When `durationTickDown` reaches zero: "X flickers and burns out." is shown to the room (185) or to the holder (194). The item is destroyed if `destroyedWhenBurnedOut` (186-187, 195-196). Its description becomes "It looks all used up." (206-207), and the light goes out (204-205).
- A lit light contributes to the room only while it burns. `recoverPhyStats` sets or clears the `IS_LIGHTSOURCE` disposition bit (161-169). The bit is what other code reads to see light; that reader is not traced.

**Rain and water.** In `executeMsg` (246-261), a lit light owned by a mob standing in rain (when `goesOutInTheRain`) or in water goes out at once. The message is "The rain makes X go out." (258) or "The water makes X go out." (256). The code sets `durationTickDown=1` and calls `tick`, which runs the normal burn-out path. A light whose destroy-on-burn-out flag is set (the default) is therefore destroyed by the rain as well.

**Lantern** (`Items/Basic/Lantern.java`). Fuel is 800 ticks (`DURATION_TICKS`, line 43, set at 53-54). The lantern is not destroyed when it burns out (55), is not put out by rain (56), and holds the name "a hooded lantern". Refilling is done by pouring lamp oil into it. Each pour takes one unit of oil from the source (105-108) and resets the fuel to 800 (109). A five-unit flask therefore gives five full refills. The code never destroys the empty flask on this path, because `Lantern.executeMsg` (101-111) does not call `destroy` and the flask's own `executeMsg` only handles DRINK as a tool.

### 7. Food and coins (StdFood, Ration, StdCoins)

**StdFood** (`Items/Basic/StdFood.java`). Nourishment is 500 units (line 43). `nourishmentPerBite` is 0, which means the whole item is eaten at once (44). The item's material is meat, weight 2, base value 5 (51-55).
- Eating (`okMessage` 113-123) sets the message value to one bite, or to 0 when the whole item goes (118-121).
- `executeMsg` (140-169) adds the bite to hunger through `adjHunger` (159), with "You are no longer hungry." (161) or "You are full." (164). The food is destroyed when empty (165-166).
- Overeating: when the eater is already at maximum hunger and not hungry (141-143), `CMLib.dice().roll(1,100,0)==1` is tested (144). That is a 1-in-100 chance, since `Libraries/Dice.java:398-406` returns a uniform value from 1 to 100. On success, `Disease_Obesity` is invoked unless the eater is a golem or already has the effect (145-153).
- Soiling. When this food is the tool of a GET whose target is a container without the "Soiled" effect (174-178), that container gets a long-lasting "Soiled" effect with `DIRTY=TRUE` (184-190). It applies only to non-raw food, or to raw flesh (180-181). The exact trigger is not traced.

**Ration** (`Items/Basic/Ration.java:43-54`). Nourishment 500, weight 10, base value 15, meat, described as "Bits of salt dried meat, dried fruit, and hard bread."

**StdCoins** (`Items/Basic/StdCoins.java`).
- The count is stored in `phyStats().ability()` (`getNumberOfCoins` 112-115, `setNumberOfCoins` 118-125, capped at `Integer.MAX_VALUE`). The stat is also the item's value (`value()` 44-47).
- Weight is `round(count/100)`, unless the material is cloth or paper (`recoverPhyStats` 231-233). So 100 coins weigh one unit, and notes weigh nothing.
- Currency and denomination are kept in the text as "currency/denomination" (`setMiscText` 172-187). The name and display text are generated from them (`Name` 79-82, `displayText` 85-88). `setDynamicMaterial` (90-109) sets the material from words in the name: "note", "bill" or "dollar" make it paper, and otherwise any raw-material name found in the item name sets the material. The description comes from `beanCounter().getConvertableDescription`.
- `okMessage` (196-221) refuses to drop, give, put or deposit a non-tradable currency, unless the actor has the CMDPLAYERS security flag.
- `autoBundle` (244-250) merges coins back into a pile in the owner's inventory.

### 8. Maps (SeaMap)

`roomChar` (`Items/Basic/SeaMap.java:50-104`) maps a room's domain to one character. City is `=`, woods `T`, rocks `:`, plains `_`, underwater and watersurface `~`, air and indoor air a space, jungle `J`, seaport `P`, swamp `x`, desert `.`, hills `h`, mountains `M`, spaceport `@`, and indoor stone `=` only when the room is heavy and has range above 4 (92-95), otherwise `#`. Indoor wood, cave, magic and metal are `#`, and anything else is `?`.

`finishMapMaking` (107-188) turns the room grid into text. Each cell is three lines: direction marks for north, south, east and west, and the room character in the middle (164-168). Output is emitted in column groups of nine grid cells (`x/9`, line 141).

`BardMap` (`Items/Basic/BardMap.java:36-94`) supplies the room set: a semicolon list of room IDs in the item text (`makeMapRooms` 73-93). It skips rooms marked SENSE_ROOMUNMAPPABLE (84). The width comes from the reader's session. The `StdMap` read path uses 80 by default, the session's wrap plus 2, or 254 when the wrap is 0 (`Items/Basic/StdMap.java:619-628`). The skill that gives this is Skill_SeaMapping (`Abilities/Skills/Skill_SeaMapping.java:35`), which the Pirate, Sailor and Scholar classes grant (`CharClasses/Pirate.java:137`, `Sailor.java:162`, `Scholar.java:164`).

### 9. Rings (Ring and Ring_Ornamental)

`Ring` (`Items/Basic/Ring.java:43-54`) is worn on either ring finger (`WORN_LEFT_FINGER | WORN_RIGHT_FINGER`, line 50), with `wornLogicalAnd` false (51) and base value 50 (52).

`Ring_Ornamental` (`Items/Basic/Ring_Ornamental.java`) chooses one kind with a single roll, `CMLib.dice().roll(1,14,-1)`, which gives 0 to 13 (line 64). It stores the kind in the item's base level (65). `setItemDescription` (82-184) then sets the name, display text, description, value and material from the kind. `recoverPhyStats` (72-80) re-derives those fields whenever the level changes. The full table is in Content worth mining.

### 10. Inn keys (StdInnKey)

**Data model.** An innkeeper's key is a StdKey whose key text is "INN" plus a number (`setMiscText`, `Items/Basic/StdInnKey.java:95`). `hangOnRack` (81-97) numbers the key by counting the inn keys already in the shop's stock (86-93), then names it "key to room N" (93) and describes it as expiring (94).

**Expiry.**
- When a customer takes the key from the shopkeeper, a tick of `TICKSPERMUDDAY` starts (`okMessage` 132-136).
- When that tick fires (`tick` 58-76), the key goes back into the shop's stock as a copy (72), and the original is destroyed (73).
- Giving the key back to the same innkeeper does the same at once (`executeMsg` 103-112).
- `TICKSPERMUDDAY` is computed at boot as milliseconds per mud hour × hours per day ÷ tick milliseconds (`application/MUD.java:1620`, `Common/DefaultTimeClock.java:313`). Defaults give 900 ticks (see Timing).

**Area binding.** When the holder enters a room in a different area from the innkeeper's, the key text gets a "-" prefix. Returning to the innkeeper's area removes it (`okMessage` 138-153). Doors and containers compare key text exactly (`Exits/StdExit.java:627`, `Items/Basic/StdContainer.java:349`), so a prefixed key does not open the room outside the inn. The location is encoded in the key's name, which is fragile.

**Other messages.** Offering the key to another shopkeeper: "I'm not interested." (`okMessage` 121-129).

### 11. Quest points (QuestPoint)

`QuestPoint` (`Items/Basic/QuestPoint.java:44-57`) is a coin of weight 0 with unlimited uses. Its sense mask marks it as not ruinable and not wishable (line 55). On GET or REMOVE (`executeMsg` 75-88) it removes itself from any worn slot and container, adds one quest point if the taker does not already own it (80-82), bumps the QUESTPOINTS_EARNED pride statistic (83), and destroys itself (87).

### 12. Wrappers (StdContainerWrapper)

`StdContainerWrapper` (`Items/Basic/StdContainerWrapper.java`) wraps an existing container and passes the Container interface through to it.
- `setWrappedObject` stores the container (47-54). Open, locked, lid, lock, key, capacity and contain-type queries are forwarded (62-96, 163-166, 209-212). The setters do nothing (99-101, 110-112, 121-123, 169-171, 215-217).
- Its contents are the items whose container is itself (`getContents` 146-160, `isInside` 196-206), so it uses the same back-reference model as StdContainer.

Use: `Abilities/Spells/Spell_PlanarBubble.java:265-279` builds a copy of a room in which each item is wrapped by type: `StdPortalWrapper` for exits, `StdRideableWrapper` for rideables, `StdContainerWrapper` for containers and `StdItemWrapper` for the rest. Mobs are wrapped the same way (`StdRideableWrapper` or `StdMobWrapper`, 281-294). `Spell_PlanarDistortion` does the same (`Abilities/Spells/Spell_PlanarDistortion.java:271-273`).

### 13. Space bodies (Star, MassiveStar, Moonlet)

Each class takes random coordinates from `Math.random()` (for example `Star.java:49-52`) and a radius from `SpaceObject.Distance` constants. Radii in these constants are in decameters (`core/interfaces/SpaceObject.java:53`, Decameter is 10 m at line 158).
- Star: radius `StarGRadius` (69,550,000 dam, about 695,500 km, `SpaceObject.java:178`) plus or minus up to 30% (`Star.java:54`). The Random is seeded from the clock (line 53), so two stars made in the same millisecond share a radius.
- MassiveStar: radius `StarBRadius` (695,500,000 dam, ten times the Star constant, `SpaceObject.java:180`) with the same plus or minus 30% (`MassiveStar.java:54`).
- Moonlet: base `MoonRadius/10` (173,740 dam divided by ten, about 174 km, `SpaceObject.java:174`) plus `random % (0.099 × MoonRadius)` (`Moonlet.java:54`). See Problems for the spread.
- Mass: `Star.getMass` returns `radius × MULTIPLIER_STAR_MASS` (`Star.java:61-63`, constant at `SpaceObject.java:247`, 1989000). `SpaceObject.java:250` defines `MOONLET_MASS` from the planet mass multiplier and the moon radius instead.

### 14. Timing summary

| Quantity | Value | Source |
|---|---|---|
| One tick | 4000 ms (15 ticks per real minute) | `core/CMProps.java:681`, `coffeemud.ini:2151` |
| One mud hour | 600000 ms = 150 ticks | `coffeemud.ini:1099` |
| Hours per mud day | 6 | `coffeemud.ini:1104`, `Common/DefaultTimeClock.java:93` |
| TICKSPERMUDDAY | 600000 × 6 ÷ 4000 = 900 ticks, about 60 real minutes | `application/MUD.java:1620` |
| Light source default fuel | 200 ticks, about 13 minutes | `Items/Basic/LightSource.java:43` |
| Lantern fuel | 800 ticks, about 53 minutes | `Items/Basic/Lantern.java:43` |
| Container or cage open timer | 30 ticks, two minutes | `Items/Basic/StdContainer.java:51`, `StdCage.java:110` |

These figures assume the shipped configuration. All of them are configurable.

## Content worth mining

**Rings** (`Items/Basic/Ring_Ornamental.java:43-56` for the kinds, `82-184` for the data). Each kind is named, given a material and a base value. The kind's index is also its base level (line 65).

| Index | Constant | Name as coded | Material | Base value |
|---|---|---|---|---|
| 0 | GOLD_RING | a gold ring | gold | 50 |
| 1 | SILVER_RING | a silver ring | silver | 5 |
| 2 | COPPER_RING | a copper ring | copper | 1 |
| 3 | PLATINUM_RING | a platinum ring | platinum | 500 |
| 4 | GOLD_RING_DIAMOND | a diamond ring | diamond | 1000 |
| 5 | GOLD_RING_RUBY | a ruby ring | gem | 100 |
| 6 | GOLD_RING_OPAL | "a opal ring" (sic) | gem | 75 |
| 7 | GOLD_RING_TOPAZ | "a diamond ring" (see Problems) | gem | 65 |
| 8 | GOLD_RING_SAPPHIRE | a sapphire ring | gem | 200 |
| 9 | MITHRIL_RING | a mithril ring | mithril | 20 |
| 10 | GOLD_RING_PEARL | a pearl ring | pearl | 65 |
| 11 | GOLD_RING_EMERALD | "a emerald ring" (sic) | gem | 100 |
| 12 | STEEL_RING (default branch) | a metal ring | steel | 50, inherited from Ring |
| 13 | BRONZE_RING | a bronze ring | bronze | 2 |

**Containers** (sizes are the weight limit from `capacity`, item weight and base value as set in each class).

| Class | Parent | Capacity | Weight | Value | Lid and lock | Notes |
|---|---|---|---|---|---|---|
| StdContainer (default) | StdItem | 25 (`StdContainer.java:59`) | default | 10 | none | Cotton, accepts anything |
| SmallSack | StdContainer | 25 (`SmallSack.java:47`) | default | 1 | none | Cotton |
| LargeSack | StdContainer | 100 (`LargeSack.java:47`) | default | 5 | none | Cotton |
| LockableContainer | StdContainer | 25 (inherited) | default | 10 (inherited) | lid and lock, closed and locked at start (`LockableContainer.java:44-47`) | Oak |
| SmallChest | LockableContainer | 50 (`SmallChest.java:47`) | 25 | 15 | lid and lock | Oak, "key hole" in description |
| LargeChest | LockableContainer | 150 (`LargeChest.java:47`) | 50 | 50 | lid and lock | Oak |
| NeedleChest | LargeChest | 150 | 50 | 50 | unlocked | Trap_OpenNeedle |
| StdCage | StdContainer | 1000 (`StdCage.java:49`) | 25 | 15 | none set in class | Bodies and caged animals |
| HoleInTheGround | GenContainer | 0 at start (`HoleInTheGround.java:47`) | 0 | 0 | none | Dust, hidden |
| StdCageRideable | Items/Basic StdRideable | 5000 (`StdCageRideable.java:49`) | 1000 | 15 | none set in class | Wagon with cage |
| HorseCart | Items/Basic StdRideable | 1000 (`HorseCart.java:47`) | 500 | 500 | none | Wagon, rider capacity 10 |

**Drinks.** The base is `StdDrink.java:42-44` (2000 held, 2000 remaining, 250 quench per drink).

| Class | Liquid | Capacity or held | Weight | Value | Notes |
|---|---|---|---|---|---|
| Pot | fresh water, starts empty (`Pot.java:52`) | 20 held (line 49), 20 weight capacity (line 50) | 5 | 5 | Accepts drinkables and eatables; 1 quench per drink |
| Pan | fresh water, inherits 2000 units | 25 weight capacity (`Pan.java:47`) | 5 | 5 | Iron |
| OilFlask | lamp oil, 5 units (`OilFlask.java:53-56`) | none | 3 | 5 | Glass; disappears after drinking; not drinkable |
| Spring | fresh water, 999999 units (`Spring.java:48-49`) | none | 5 | 10 | Fixed in place; quench 250 |

**Food and light fuel.**

| Class | Parent | Quantity | Weight | Value | Material | Notes |
|---|---|---|---|---|---|---|
| StdFood | StdItem | 500 nourishment (`StdFood.java:43`) | 2 | 5 | meat | "mystery meat" description |
| Ration | StdFood | 500 | 10 | 15 | meat | Dried rations, described in the text |
| LightSource | StdItem | 200 ticks (`LightSource.java:43`) | default | 5 | oak, held | Burns out and is destroyed |
| Lantern | LightSource | 800 ticks (`Lantern.java:43`) | 5 | 60 | steel | Not destroyed on burn-out; not put out by rain; refilled by pouring lamp oil |

**Space bodies.**

| Class | Radius basis | Spread | Mass rule | Notes |
|---|---|---|---|---|
| Star | StarGRadius, about 695,500 km | plus or minus 30% | radius × 1989000 | Hydrogen, glowing light source |
| MassiveStar | StarBRadius, ten times the Star constant | plus or minus 30% | radius × 1989000 | Same kind of object, larger |
| Moonlet | MoonRadius ÷ 10, about 174 km | see Problems | `MOONLET_MASS` constant | Named "a small moon" |

**Quoted messages worth keeping.** These are short and reusable.
- "You are no longer thirsty." and "You have drunk all you can." (StdDrink 335, 338)
- "You are full." and "You are no longer hungry." (StdFood 161-164)
- "The rain makes X go out." and "X flickers and burns out." (LightSource 258, 185)
- "Your ship is grappled to X and cannot move." (StdGrapples 209)
- "Outside, you see: " as a prefix for what outsiders see on a ship's deck (StdBoardable 58)
- "Would you like to learn X from Y (y/N)?" (StdDissertation 269)
- "key to room N" and "I'm not interested." (StdInnKey 93, 128)

## Notable design ideas

- **A vehicle that owns a map.** StdBoardable's interior is a real Area, so movement, look and combat reuse the room code (`StdBoardable.java:127-149`). Actions inside are relayed to all rooms of the area, and outdoor rooms receive a short "Outside, you see:" line (`executeMsg` 991-1005).
- **Rope rechecked on use.** A grapple line is not stored as a fixed link. `ungrappleCheck` (84-114) runs on each message that touches it (`executeMsg` 139, ADVANCE in `okMessage` 190), so a line that no longer joins two docked ships is dropped at the next use.
- **Learn-by-reading with a craft step.** A reader below the spell's level first gets an OK_VISUAL message through the matching craft skill, with the ability ID set on it (`StdDissertation.java:163-212`). The scroll still teaches the spell at the lowest qualifying level, instead of refusing.
- **Permissions as a text parameter.** A book's own text carries READ, WRITE and ADMIN masks (`StdBook.java:836-845`). Any zapper mask can be used, with no separate ACL object.
- **Keys as text equality.** A lock opens when its key text equals the lock's name (`StdContainer.java:349`). It is simple and easy to build, and an inn key that changes text when carried out of its area is a neat way to make a key local.
- **Burial that grows.** HoleInTheGround starts with no capacity, grows when dug (`HoleInTheGround.java:139-148`), hides when closed with contents (157-166), and vanishes when empty and no players are present (77-93).
- **Refuel by pouring.** Lantern takes one unit of oil per fill and resets its fuel (`Lantern.java:105-109`), so oil becomes a consumable resource with no separate fuel item.
- **Rain and water as light hazards.** A lit light goes out in rain or water at once (`LightSource.java:246-261`), using the same burn-out path as running out of fuel.
- **Coins that name themselves.** The name, description and material of a coin stack come from its currency text (`StdCoins.java:79-109`).
- **Maps from domain types.** A single function turns room domains into map glyphs (`SeaMap.java:50-104`), which is an easy way to get a terrain map from data already in the world.
- **Physical quest currency.** A quest point is a coin on the ground, and taking it is the reward (`QuestPoint.java:75-88`).
- **Space bodies from real constants.** Radii come from real-world distances in decameters (`SpaceObject.java:174-180`), and mass is a radius multiple.

## Problems and cruft

1. **StdBook DELETE bound never checked.** `Items/Basic/StdBook.java:194` reads `(numEntries>numEntries)`, a self-comparison that is always false. So `okMessage` never rejects a chapter number above the count. `executeMsg` then calls `readChaptersByCreateDate().get(entryNum-1)` without a bound check (lines 465-466). An out-of-range number would therefore throw at runtime (list `get` behaviour assumed; not run).
2. **StdBook broadcasts array identity text.** `subject` and `message` are declared as `String[1]` (lines 407-408). `subject+"\n\r"+message` on lines 438 and 454 concatenates the arrays themselves, so the room broadcast contains the array's type and hash, not the chapter text. Verified by the declared types; output not run.
3. **StdBook READ NEW never shows chapters.** The line that would mark recent entries with `*` is commented out (`StdBook.java:675-681`). Line 697 then tests `str.charAt(0) != '*'` on entries that never start with `*`, so every chapter is blanked. Only the "Use READ ALL" hint remains (715). Only the single-chapter path (737-738) sets the mark.
4. **StdBook blank subject aborts instead of keeping the old one.** Lines 537-539 read `if(subj is blank AND (subject is null OR empty)) subj=subject[0]`. The inner assignment only applies when there was no old subject, so an empty answer to "Enter the name of the chapter (X):" aborts with "Aborted." (542). The prompt implies the old name is kept.
5. **StdBook dead code.** The `else if(eTrim.startsWith(chStart))` branch at lines 369-378 repeats the test on line 352, so it can never run. The `'#'` branch at 328-331 is also unreachable: nothing in `DBRead` writes `#`.
6. **StdBook duplicate branches.** `getRawContent` (lines 798-801) returns the same string on both branches.
7. **StdBook `which` from the whole string.** Line 318 sets `which=CMath.s_int(msg.targetMessage())`, reading the entire argument string rather than the word that matched. A word such as NEW before the number may give the wrong chapter (behaviour not run).
8. **StdBook chapters keyed by name.** Journal records are looked up by `Name()` (`StdBook.java:602-605`). Renaming a book could orphan its chapters. No rename hook was found in this file, so the consequence is unverified.
9. **StdBook silent failures.** Empty `catch` blocks at lines 581-583 and 746-748 hide errors from the chapter lookup and the web-macro filter.
10. **`<cmvp>` pages run code.** A chapter that begins with `<cmvp>` is evaluated as a web macro on read (`StdBook.java:743-745`). Writing one is restricted to admins and journal-privileged mobs (423-429). This is a deliberate capability and a risk if the restriction is loosened.
11. **StdDissertation comma before "and".** `makeSecretIdentity` (lines 93-101) puts a comma before the last item, so a two-spell scroll reads "X, and Y". Cosmetic.
12. **StdDissertation proficiency reset.** `StdDissertation.java:242-244` sets the reader's proficiency to 0 after teaching, with a comment asking why. The reason is not documented.
13. **Dissertation default uses.** The constructor sets uses to 0 (line 61), so a default scroll is destroyed on its first learning. This is probably intended, but it is not stated.
14. **Pan starts full.** `Pan.java:41-52` overrides no liquid value, so it inherits `StdDrink`'s 2000 units of fresh water (`StdDrink.java:43-44`). Whether a generator overrides this is unverified.
15. **StdDrink commented-out code and a joke comment.** Lines 128-139 and 146-157 are commented-out code. The comment at line 251 calls a rule "really a stupid rule".
16. **StdDrink weight heuristic.** Line 226 lets anyone drink from an item heavier than 1000 weight. This is a heuristic for fixtures, not a documented rule.
17. **Pot sets capacity twice.** `Pot.java:47` sets 25, then `setCapacity(20)` at line 50. Harmless but confusing.
18. **LightSource redundant test.** Line 303 tests `msg.amITarget(this)` again inside a block already guarded by the same test (263).
19. **Rain destroys any light.** Rain and water work by forcing the burn-out path (`LightSource.java:259-260`), so a non-Lantern light is destroyed (its default destroys on burn-out, line 45), not just put out.
20. **Ring names.** `Ring_Ornamental.java:136` names the topaz ring "a diamond ring". Line 129 gives "a opal ring" and line 164 "a emerald ring" (article errors). Line 173 has "broze". Line 117 leaves its description incomplete ("It is a simple gold " with no full stop).
21. **Ring kind stored as level.** `Ring_Ornamental.java:65` puts the ring kind (0 to 13) in the level field. Any level-based rule then sees a ring as a low-level item, which includes the "too powerful" check (`StdContainer.java:213`). This is a data-model smell.
22. **StdBoardable possible null dereference.** In the error branches of `transferOwnership`, `R.getArea()` is called at lines 1116-1117 and 1186-1187. `R` comes from `roomLocation` (line 1049) and may be null. Reachability not traced.
23. **StdBoardable stub.** `findNearestDocks` (1218-1221) returns the room it is given. Any override is outside this bucket.
24. **StdBoardable leftovers.** A commented-out block (514-527) says "this is necessary, but why wasn't it there before?". The comment at 758-760 says trailer suspension was "disabled to have NO EFFECT, until I figure out why this is here". A TODO at 328-329 notes that electronics are not registered after a purchase.
25. **StdInnKey hacks.** `this.destroyed=false` at line 63 resurrects an item before restocking it. The "-" prefix that encodes area (148-153) is fragile (see section 10).
26. **SeaMap division by zero.** `SeaMap.java:117` computes `numXSquares = round(floor((width-6)/8))`. For widths from 6 to 13 this is 0, and line 119 divides by it. Widths come from the reader's wrap plus 2 (`StdMap.java:619-628`), so a very narrow terminal (wrap 4 to 11) reaches it, unless an upstream catch exists (unverified). The divisors 8 (line 117) and 9 (line 141) also do not match, and the intent is unverified.
27. **Moonlet spread.** `Moonlet.java:54` adds `random % (0.099 × MoonRadius)` to a base of `MoonRadius/10`. The spread is therefore about 99% of the base, not 9.9%. Radii would range from about 1.7 km to about 345 km. This looks like a mismatch and may not be intended.
28. **Star seed.** `Star.java:53` seeds its Random from `System.currentTimeMillis()`, so objects created in the same millisecond share a radius. `MassiveStar` and `Moonlet` do the same.
29. **Soiling trigger.** The condition at `StdFood.java:175-178` (food as the tool of a GET from a container) reads as if it is aimed at something else. Intent unverified.
30. **HoleInTheGround capacity units.** Capacity grows by the DIG message value (`HoleInTheGround.java:147`). The unit is not checked.
31. **QuestPoint null location.** `QuestPoint.java:86` calls `mob.location().recoverRoomStats()` with no null check.

## Takeaways for a Python SUD

**Borrow.**
- **Container as data.** A single class with weight capacity, an accepted-kinds set (empty means anything), an optional lock, and a list of contents. Keys match by equal strings, as in `StdContainer.java:349`.

  ```python
  @dataclass
  class Container:
      weight_capacity: int
      accepts: frozenset[str] = frozenset()   # empty means anything
      has_lid: bool = False
      is_open: bool = True
      lock_key: str | None = None
      is_locked: bool = False
      contents: list["Item"] = field(default_factory=list)
  ```

  Keep contents as a plain list rather than CoffeeMud's back-references (`StdContainer.java:750-765`). That is simpler and fits one player.
- **Liquid vessel.** One `Vessel(capacity_units, liquid: str | None, units: int)` with a `pour` method that checks the liquid type, as `StdDrink.java:290-296` does. Drinking subtracts units and raises a satiation meter that is 0 when thirsty.
- **Light with fuel and weather.** `Light(fuel_turns, destroy_when_spent)`, a `refuel_from(flask)` method (see Lantern), and a weather check that puts the light out. Keep the one-line messages.
- **Books as data.** `Book(chapters: list[Chapter])` in a dictionary or in a JSON file, with `Chapter(title, author, text)`. Chapters need no database. Permission can be a plain field, not zapper masks.
- **Scrolls that teach.** `Scroll(ability_ids: list[str], uses: int)`. Reading offers the abilities, asks the yes/no question, and teaches with `player.learn(ability_id)`. The craft-skill message for below-level readers can be dropped.
- **Time-limited keys.** `Key(id, expires_on_game_hour, valid_area)`. Use game time, not ticks, so the expiry reads naturally in a turn-based game.
- **Coins.** `Coins(count, currency)`, with weight `count // 100` and notes weighing nothing. A `Currency` object holds the denomination.
- **Maps from domains.** Port the `roomChar` table (`SeaMap.java:50-104`) as a dictionary, and draw the map as monospaced text.

**Simplify.**
- Merge StdDrink, Pot, Pan, OilFlask and Spring into one `Vessel` with data rows.
- Merge Ration and StdFood into a `Food(nourishment)` record. Keep the overeating roll only if hunger matters.
- Replace the teacher-MOB trick in StdDissertation with a direct learn call.
- Treat HoleInTheGround as a `Cache(hidden: bool, capacity)` at a location.
- Fold Lantern into `LightSource` as a refuelable flag instead of a subclass.
- Replace QuestPoint with a counter.

**Drop (multiplayer-only).**
- Ownership by player name or clan, plus transfer prompts and extra-items storage (StdBoardable 430-466, 1010-1194; HoleInTheGround 170-180).
- Shopkeeper stock and innkeeper rules (StdInnKey), unless the SUD has inns.
- Checks on other players being present (HoleInTheGround 80-81, StdBoardable 562 and 854).
- Journal sharing with "ALL" and "MASK=" recipients (StdBook 670-673).
- Outdoor-viewer relays for ships (StdBoardable 627-777), unless the SUD has vehicles with interiors.

**Vehicles.** If the SUD has ships, a vehicle is a set of rooms that moves between ports. Keep the idea that the interior is a real area, and drop the naming and ownership flows. Grapples can be edges between two docked vehicles, checked whenever they are used, as `ungrappleCheck` does.

**Multiplayer assumptions to rethink.** Most of the ownership, presence and sharing rules assume several players. In a single-player game, ownership becomes a single flag or the player's id, and "who else is here" checks disappear.

## Coverage

- **Read in full (bodies, after the license and import header):** all 35 files in the manifest. The short classes were read with line numbers from their class bodies. The large ones were read in ranges: `StdBoardable.java` (1254 lines), `StdBook.java` (868), `StdContainer.java` (783), `StdDissertation.java` (496), `StdDrink.java` (414), `LightSource.java` (326), `StdGrapples.java` (273), `StdCoins.java` (251), `StdContainerWrapper.java` (246), `HoleInTheGround.java` (195), `StdFood.java` (194), `SeaMap.java` (189), `Ring_Ornamental.java` (185), `StdInnKey.java` (157), `StdCage.java` (132), `Lantern.java` (120), `QuestPoint.java` (96), `StdCageRideable.java` (96), `Pot.java`, `Star.java`, `Ring.java`, `Pan.java`, `OilFlask.java`, `Spring.java`, `MassiveStar.java`, `Moonlet.java`, `HorseCart.java`, `Ration.java`, `LargeChest.java`, `SmallChest.java`, `LargeSack.java`, `SmallSack.java`, `Parchment.java`, `LockableContainer.java`, `NeedleChest.java`.
- **Read for context, not analysed:** `Items/Basic/StdPortal.java` (b128, by grep), `Items/Basic/StdRideable.java` (b128, by grep and imports), `Items/Basic/StdMap.java` (read path, lines 610-630), `Items/Basic/StdKey.java` (key-text accessors, by grep), `Items/Basic/StdContainerWrapper.java` callers and `Spell_PlanarBubble.java` (lines 240-300), `Spell_PlanarDistortion.java` (by grep), `Items/interfaces/Boardable.java` (name constants), `core/interfaces/SpaceObject.java` (constants), `Libraries/Dice.java` (roll), `Common/DefaultCharState.java` (adjThirst and adjHunger), `Common/interfaces/Session.java` (confirm prompts), `application/MUD.java` (line 1620, by sed), `Common/DefaultTimeClock.java` (lines 93 and 313), `coffeemud.ini` (timing), and the class-to-skill lookups for Skill_SeaMapping and the Pirate, Sailor and Scholar classes (by grep).
- **Not followed:** `Trap_OpenNeedle`, the Skill_*craft family, `CMLib.expertises().postTeach`, `JournalsLibrary.makeMessageASync`, `CMLib.utensils().setTrapped`, the callers of the thirst and hunger functions, and the effect of Skill_SeaMapping itself.
- **Tool note.** The `cm.py chain` command resolved `StdRideable` to the MOB class `MOBS/StdRideable.java`. It is the item class in `Items/Basic/StdRideable.java` (b128) that HorseCart and StdCageRideable extend, as their imports confirm. The chain was corrected by hand.
