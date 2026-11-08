# Exits: doors, gates, openings and terrain links

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b122` (Exits) · 33 files · 4102 lines of Java.

## Summary

An exit is one end of a passage between two rooms. It is a physical object (`com/planet_ink/coffee_mud/Exits/interfaces/Exit.java:39`) that can carry flags, effects, behaviours and scripts, so the same mechanisms used for rooms and items apply to it. All door, lock, movement and message rules sit in `StdExit` (1422 lines). The other 31 classes are mostly small subclasses that set a few defaults or add one trait: a crevasse jump, a climbable face, a hidden walkway, a view-only window. Exits do not move anyone; `MUDTracker.move` asks each exit to veto an ENTER message, and the exit only gates and decorates the move. Terrain links are assembled from reusable effects (`Prop_Climbable`, `Prop_Crawlspace`, `Prop_Hidden`, `Prop_RoomView`) and from room code that generates sky and underwater links.

## Key files and types

Java paths are relative to the package root `com/planet_ink/coffee_mud/`, so a citation such as `Exits/StdExit.java:448` means `com/planet_ink/coffee_mud/Exits/StdExit.java:448`. The table below spells out the full paths. Data files (`resources/`, `coffeemud.ini`) are relative to the CoffeeMud folder.

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Exits/StdExit.java` | Base exit: door and lock state, ENTER/OPEN/CLOSE/LOCK/UNLOCK rules, reopen timer, usage counter, pending room link | 1422 |
| `com/planet_ink/coffee_mud/Exits/GenExit.java` | Data-driven exit; every field is a Gen stat and saves as XML | 486 |
| `com/planet_ink/coffee_mud/Exits/interfaces/Exit.java` | Exit contract: door words, usage counter, look text, last room | 133 |
| `com/planet_ink/coffee_mud/Exits/interfaces/PrepositionExit.java` | Optional "through X" entry and exit phrases | 67 |
| `com/planet_ink/coffee_mud/Exits/ClimbableExit.java` | Sheer surface; delegates to `Prop_Climbable` | 109 |
| `com/planet_ink/coffee_mud/Exits/NamedRedirectable.java` | Destination pinned at first use; rewrites the ENTER target | 106 |
| `com/planet_ink/coffee_mud/Exits/GapExit.java` | Crevasse; a jump can fail and kill | 101 |
| `com/planet_ink/coffee_mud/Exits/GenAirLock.java` | Ship air-lock door, defaults closed | 100 |
| `com/planet_ink/coffee_mud/Exits/Skyway.java` | Open exit to the sky; LOOK adds time and weather | 99 |
| `com/planet_ink/coffee_mud/Exits/OpenPrepositional.java` | Open exit named by its text, with "through X" phrases | 96 |
| `com/planet_ink/coffee_mud/Exits/GenCurtain.java` | Cloth curtain that reads closed but can be walked through | 92 |
| `com/planet_ink/coffee_mud/Exits/AjarDoor.java` | Door that starts open and never closes itself | 90 |
| `com/planet_ink/coffee_mud/Exits/StdClosedDoorway.java` | Base for closed-by-default doors | 90 |
| `com/planet_ink/coffee_mud/Exits/Grate.java` | Barred grate; "remove" instead of "open" | 83 |
| `com/planet_ink/coffee_mud/Exits/Impassable.java` | Exit that blocks every move (view-only sides) | 73 |
| `com/planet_ink/coffee_mud/Exits/FlyingExit.java` | "The open air"; needs flight | 67 |
| `com/planet_ink/coffee_mud/Exits/Gate.java` | Closed gate | 62 |
| `com/planet_ink/coffee_mud/Exits/OpenDescriptable.java` | Open exit "the ground"; text in miscText | 60 |
| `com/planet_ink/coffee_mud/Exits/StdLockedDoorway.java` | Base for locked-by-default doors | 60 |
| `com/planet_ink/coffee_mud/Exits/StdOpenDoorway.java` | Base for plain open walkways | 60 |
| `com/planet_ink/coffee_mud/Exits/GenDoor.java` | Generic door, defaults closed | 59 |
| `com/planet_ink/coffee_mud/Exits/LockedGate.java` | Locked gate | 58 |
| `com/planet_ink/coffee_mud/Exits/OpenNameable.java` | Open exit whose name is its text | 58 |
| `com/planet_ink/coffee_mud/Exits/HiddenClosedDoorway.java` | Closed door with IS_HIDDEN | 55 |
| `com/planet_ink/coffee_mud/Exits/PitOpen.java` | Open pit with an enter trap | 54 |
| `com/planet_ink/coffee_mud/Exits/HiddenWalkway.java` | Open walkway with IS_HIDDEN | 49 |
| `com/planet_ink/coffee_mud/Exits/UnseenWalkway.java` | Open walkway with IS_NOT_SEEN | 49 |
| `com/planet_ink/coffee_mud/Exits/NeedleDoor.java` | Closed door with a needle trap | 48 |
| `com/planet_ink/coffee_mud/Exits/TrappedDoor.java` | Closed door with an open trap | 48 |
| `com/planet_ink/coffee_mud/Exits/TrappedLockedDoor.java` | Locked door with a generic trap | 48 |
| `com/planet_ink/coffee_mud/Exits/Door.java` | Plain closed door (ID only) | 40 |
| `com/planet_ink/coffee_mud/Exits/LockedDoor.java` | Plain locked door (ID only) | 40 |
| `com/planet_ink/coffee_mud/Exits/Open.java` | Plain open walkway (ID only) | 40 |

## How it works

### Data model

- **Stored state** (`com/planet_ink/coffee_mud/Exits/StdExit.java:45-62`). `phyStats` and `basePhyStats` carry the flags that make an exit flying, climbing, hidden, unseen or unhelpful. `isOpen` and `isLocked` (`StdExit.java:47-48`) are the only door state. `usage` (`:53`) is a reference counter and `lastRoomID` (`:55`) remembers the room the exit last led to.
- **One string, several jobs.** `miscText` (`StdExit.java:49`) is the key name of a lockable exit (`keyName()`, `:911-914`), the readable text, the name of `OpenNameable` and `OpenDescriptable`, and a pending room link written as a `{#roomid#}` prefix (`:943-973`). `GenExit` splits some of these into fields, but its key name and readable text still share one field (see Problems).
- **Door type as overrides.** Four booleans describe a door: `hasADoor()`, `hasALock()`, `defaultsClosed()` and `defaultsLocked()` (`StdExit.java:105-127`). The words are `doorName()`, `closeWord()`, `openWord()` and `closedText()` (`StdExit.java:148-169`). `openDelayTicks()` returns 45 (`StdExit.java:1337-1340`). A new door type is a class that overrides these methods.
- **No destination field.** A room holds the exit in a direction slot (`com/planet_ink/coffee_mud/Locales/StdRoom.java:395-419`), and the destination is the room in that direction. An exit learns where it went only when a mob tries to enter (`StdExit.java:488-490`).

### Door and lock state machine

Commands arrive as `CMMsg` messages. `okMessage` can refuse with a message to the actor, and `executeMsg` applies the change. Source: `com/planet_ink/coffee_mud/Exits/StdExit.java`.

| Message | Refused in `okMessage` when | Change in `executeMsg` |
|---|---|---|
| CLOSE (`:527-542`) | no door ("There is nothing to close!"); already closed | `isOpen=false` (`:748-752`) |
| OPEN (`:543-563`) | no door; already open; locked with a lock ("The door is locked.") | `isLocked=false`, `isOpen=true`; if `defaultsClosed()` or `defaultsLocked()`, the reopen timer is restarted (`:753-763`) |
| LOCK (`:583-621`) | no door or no lock; open; already locked; no matching key (shared check with UNLOCK) | `isOpen=false`, `isLocked=true` (`:764-768`) |
| UNLOCK (`:590-641`) | no door or no lock; open; not locked; no matching key | `isLocked=false` (`:774-778`) |
| PUSH / PULL (`:564-570`, `:576-582`) | open or no door | no state change; "It doesn't appear to be doing any good." (`:770-773`) |

**Keys.** The actor must carry a `DoorKey` whose `getKey()` equals `keyName()` (`StdExit.java:622-636`). The key can be held directly, or inside a key-holding container that the actor carries (one level only).

**Auto-revert.** The reopen timer is a countdown that calls `tick(this, TICKID_EXIT_REOPEN)` (`StdExit.java:758-759`, `Tickable.java:86`). When it fires, a `defaultsClosed` exit closes (`StdExit.java:802-805`), and a `defaultsLocked` exit also re-locks (`:806-810`). It sends no message. Exits with `defaultsClosed` false, such as `AjarDoor` (`AjarDoor.java:79-83`), never close themselves.

### Movement gate on ENTER

`StdExit.okMessage` runs for an ENTER whose tool is the exit (`StdExit.java:488-523`). Behaviours, scripts and effects are consulted first (`:452-469`). Then:

1. Closed door. If the actor cannot see the exit, "You can't go that way." Otherwise "The door is closed." (`:491-498`; the template is `"The @x1 is @x2."`, `:496`).
2. Flying exit, and the actor is neither in flight nor falling: "You can't fly." (`:499-505`).
3. Climbing exit, and the actor is not climbing, falling or in flight. The code looks for a ladder with `CMLib.tracking().findALadder` and mounts it with `postMountLadder` (`:511-515`). If there is no ladder: "You need to climb that way, if you know how." (`:519`).

Subclasses add their own refusals: the crevasse jump roll (`com/planet_ink/coffee_mud/Exits/GapExit.java:67-88`), the blanket move veto in `Impassable` (`Exits/Impassable.java:48-64`), `Prop_Crawlspace` (`com/planet_ink/coffee_mud/Abilities/Properties/Prop_Crawlspace.java:94-113`), and the temporary pass in `GenCurtain` (`Exits/GenCurtain.java:63-91`). `NamedRedirectable` rewrites the target instead (`Exits/NamedRedirectable.java:96-103`).

### Movement sequence (MUDTracker.move)

Source: `com/planet_ink/coffee_mud/Libraries/MUDTracker.java`.

- The exit and destination come from `thisRoom.getExitInDir` and `getRoomInDir` (`:1670-1671`).
- The enter message is built with the mob as source, the destination room as target and the exit as tool (`:1720`, `:1730`). Exit code therefore tests `msg.tool()==this`.
- `PrepositionExit` phrases replace the direction words in the messages (`:1691-1697`). A `StdPortal` pair uses this to read "through <portal name>" (`com/planet_ink/coffee_mud/Items/Basic/StdPortal.java:360-363`).
- Refusal checks run in this order (`:1752-1767`): mob (leave), exit (enter), origin room (leave), opposite exit (leave), destination room (enter), mob (enter). Any refusal ends the move.
- On success the exit gets `executeMsg(enter)` (`:1831`), the mob leaves the origin room (`:1833-1835`), the mob is placed (`:1843-1844`), the destination is told (`:1845`), and the opposite exit gets `executeMsg(leave)` (`:1848`).

### Timing and numbers

- Default tick is 4000 ms (`com/planet_ink/coffee_mud/core/CMProps.java:681`). A reopen delay of 45 ticks is therefore 180 seconds (`StdExit.java:1337-1340`; `GenExit.java:52`, `:80`).
- Player-built doors get `setOpenDelayTicks(9999)` (`com/planet_ink/coffee_mud/Abilities/Common/BuildingSkill.java:725`). At the default tick that is about 11 hours (39,996 s).
- The pit-trap door uses 10 ticks, which is 40 seconds (`com/planet_ink/coffee_mud/Abilities/Traps/Trap_RoomPit.java:260`).
- Restarting the reopen timer first deletes the old one (`StdExit.java:758-759`).
- Behaviour and script ticks start with a 1-tick countdown the first time one is attached (`StdExit.java:1152-1153`, `:1259-1260`).
- Crevasse jump: fall when a percentage roll is below `round(mobWeight / maxCarry * (100 - 3*STR))` (`GapExit.java:78-79`). `mobWeight` is base weight plus carried weight, minus items worn "floating nearby" (`GapExit.java:55-65`). Worked example from the formula: STR 10 with carried weight equal to max carry gives 70 percent.
- Crawl threshold: `height` defaults to 24 and can be set with `HEIGHT` (`Prop_Crawlspace.java:67`, `:72`). Taller actors need to be sitting (`:94-95`). Above 120 the refusal is "You cannot fit in there." (`:107-109`).
- Sky: `SKYSIZE` defaults to 3 (`com/planet_ink/coffee_mud/coffeemud.ini:1061-1069`). A positive value gives a `Skyway`, a negative one an `UnseenWalkway`, and zero gives no sky (`com/planet_ink/coffee_mud/Locales/StdRoom.java:559-571`).

### Messages shown to players

All from `StdExit.java` unless a file is named.

- "You can't go that way." for a closed door that is not visible (`:494`); the same text from `Impassable.java:60`.
- "The door is closed." and "The door is locked." (`:496`, `:559`). "The door is already closed." and "...already open!" (`:540`, `:554`).
- "There is nothing to close!" (`:535`), "There is nothing to @x1 that way!" (`:549`), "There is nothing to push over there." (`:567`).
- "You don't seem to have the key." (`:638`), "There is no lock!" (`:604`).
- "You can't fly." (`:503`), "You need to climb that way, if you know how." (`:519`; also `Prop_Climbable.java:100`).
- "It doesn't appear to be doing any good." when a push or pull passes (`:772`).
- Default refusal: "You can't do that to <T-NAME>." (`:649`).
- Crawl: "You must crawl that way.", "You cannot fit in there.", "You cannot fully stand up here.", and on waking "You wake up, but you are still crawling." (`Prop_Crawlspace.java:109-132`).
- Crevasse: "<S-NAME> attempt(s) to jump the crevasse, but miss(es) the far ledge!", "<S-NAME> fall(s)!!!!" (`GapExit.java:81-82`), and on success "<S-NAME> jump(s) the crevasse!" (`:99`).
- Sky look: "It is dawn." or "It is dusk." for those times of day, "It is daytime." or "It is nighttime." for day and night (`Skyway.java:79-89`), then the weather line (`:90`) and moon phase (`:91-92`).
- Look-through: "In @x1 you can see:" (`com/planet_ink/coffee_mud/Abilities/Properties/Prop_RoomView.java:178-213`).
- Doors close silently. Nothing tells the room when a timer fires (`StdExit.java:802-812`).
- Sysop look (`StdExit.java:660-666`) shows the room ID and the exit's class ID.

### Visibility

- `viewableText` (`StdExit.java:655-697`): an open exit shows its `displayText()`, or the room's text if that is empty, and says "darkness" in a dark room (`:672-690`). A closed exit shows `closedText()` when it can be seen (`:692-694`).
- `Sense.isSeeable` is false for IS_NOT_SEEN (`com/planet_ink/coffee_mud/Libraries/Sense.java:518-521`).
- IS_HIDDEN is refused to any seer without `canSeeHidden` (`Sense.java:1507-1520`). An exit is not a MOB, so the hide-versus-detect score comparison never applies to it.
- Flight and climbing are read from the exit's disposition bits: `isFlying` (`Sense.java:1083-1086`), `isClimbing` (`:1089-1092`), `isInFlight` (`:1779-1787`).

### Exit families and what each adds

Grouped by family. "Used by" lists code or data that creates the class. "none found" means no reference to the class name outside `Exits/` in the code, the data or `guides/`.

**Family 1: generated and data-driven doors**

| Class (file) | Extends | What it adds | Shown as | Used by |
|---|---|---|---|---|
| `Exits/StdExit.java` | (Exit) | Base rules. No door, no lock, open. `setName`, `setDisplayText`, `setDescription` and `setExitParams` are empty (`:205-208`, `:416-419`, `:421-424`, `:905-908`) | "a walkway" (`:84`) | every exit |
| `Exits/GenExit.java` | StdExit | All fields data-driven: name, description, words, closed text, door and lock flags, key, reopen ticks, readable text, tags. Saved as XML (`:90-92`); Gen stat codes at `:300-305` | "a walkway" or "exit" defaults (`:62-81`) | `Abilities/Spells/Spell_Portal.java:203`; `Abilities/Spells/Spell_PortalOther.java:179`; `Abilities/Prayers/Prayer_Gateway.java:163`; `Commands/Import.java:6643`; construction table (5 rows) |
| `Exits/GenDoor.java` | GenExit | Door, defaults closed, no lock (`:43-58`) | "a door" | construction (30 rows); random areas (29 uses) |
| `Exits/GenCurtain.java` | GenExit | Reads closed but passes entrants (`:63-91`) | "a curtain" | construction (6 rows); `resources/randareas/oceanfloor.xml:127` |
| `Exits/GenAirLock.java` | GenExit | Door, defaults closed, no lock (`:55-77`) | "an air lock" | none found |

**Family 2: open openings (no door)**

| Class (file) | Extends | What it adds | Shown as | Used by |
|---|---|---|---|---|
| `Exits/StdOpenDoorway.java` | StdExit | Open, empty display and description (`:46-58`) | "a walkway" | `Commands/Link.java:159`; `Behaviors/MovingRoom.java:475`, `:512`; `Abilities/Traps/Trap_Trap.java:334`; `Abilities/Traps/Trap_RoomPit.java:181`; `Locales/WaterSurface.java:130` |
| `Exits/Open.java` | StdOpenDoorway | ID only | "a walkway" | `Abilities/Druid/Chant_Den.java:174`; `Abilities/Skills/Skill_Burrow.java:152`; 40 random-area uses |
| `Exits/OpenNameable.java` | StdOpenDoorway | Name, display and description all return miscText (`:42-56`) | the text | none found |
| `Exits/OpenDescriptable.java` | StdExit | Name "the ground"; display and description return miscText (`:44-59`) | "the ground" | `Abilities/Thief/Thief_Hideout.java:229`; `Commands/Import.java:7507` |
| `Exits/OpenPrepositional.java` | StdOpenDoorway, PrepositionExit | Name is the text. `setMiscText` also sets "through <text>" entry and exit phrases (`:63-72`) | the text | `Items/Basic/StdPortal.java:360-363` |
| `Exits/NamedRedirectable.java` | StdOpenDoorway | Destination pinned by the first `lastRoomUsedFrom(room)` call; rewrites the ENTER target (`:82-105`) | set by the caller | `Items/Basic/GenSailingShip.java:459-462`; `Items/Basic/GenAirShip.java:436-440` |
| `Exits/PitOpen.java` | StdOpenDoorway | Name "a pit"; `Trap_EnterPit` attached in the constructor (`:41-53`) | "a pit" | none found |

**Family 3: closed doors and gates**

| Class (file) | Extends | What it adds | Shown as | Used by |
|---|---|---|---|---|
| `Exits/StdClosedDoorway.java` | StdExit | Has a door, defaults closed. Closed text "a closed door" (`:88`); description "An ordinary wooden door with swinging hinges and a latch." (`:58`) | "a door" | `Abilities/Traps/Trap_Trap.java:339`; `Abilities/Traps/Trap_RoomPit.java:258` |
| `Exits/Door.java` | StdClosedDoorway | ID only | "a door" | 9 random-area uses |
| `Exits/AjarDoor.java` | StdExit | Has a door, defaults open. Never closes itself (`:79-83`) | "a door" | none found |
| `Exits/Gate.java` | StdClosedDoorway | Name "a gate", door name "gate", closed text "a closed gate" (`:41-61`) | "a gate" | `resources/randareas/prison.xml:296` |
| `Exits/Grate.java` | StdClosedDoorway | Open word "remove"; description "A metal grate of thick steel bars is inset here." (`:44`, `:69`, `:81`) | "a barred grate" | none found |
| `Exits/HiddenClosedDoorway.java` | StdClosedDoorway | IS_HIDDEN in the base disposition (`:49-54`) | "a door" | none found |

**Family 4: locked doors and gates**

| Class (file) | Extends | What it adds | Shown as | Used by |
|---|---|---|---|---|
| `Exits/StdLockedDoorway.java` | StdClosedDoorway | Has a lock, defaults locked. Closed text "a closed, locked door" (`:58`) | "a door" | base of LockedDoor and TrappedLockedDoor |
| `Exits/LockedDoor.java` | StdLockedDoorway | ID only | "a door" | 6 random-area uses (prison, lair) |
| `Exits/LockedGate.java` | Gate | Has a lock, defaults locked. "a closed, locked gate" (`:41-57`) | "a gate" | `resources/randareas/prison.xml:148` |

**Family 5: trapped doors**

| Class (file) | Extends | What it adds | Shown as | Used by |
|---|---|---|---|---|
| `Exits/TrappedDoor.java` | StdClosedDoorway | `Trap_Open` attached in the constructor (`:41-47`) | door | none found |
| `Exits/NeedleDoor.java` | StdClosedDoorway | `Trap_OpenNeedle` attached (`:41-47`) | door | none found |
| `Exits/TrappedLockedDoor.java` | StdLockedDoorway | `Trap_Trap` attached (`:41-47`) | locked door | none found |

**Family 6: terrain, vertical and movement-limiting exits**

| Class (file) | Extends | What it adds | Shown as | Used by |
|---|---|---|---|---|
| `Exits/GapExit.java` | StdExit | Jump roll on ENTER; death on failure (`:67-88`) | "a crevasse" | none found |
| `Exits/ClimbableExit.java` | StdExit | `Prop_Climbable` effect drives climbing (`:64-108`) | "a sheer surface" | `Abilities/Druid/Chant_Treehouse.java:165`, `:169` |
| `Exits/FlyingExit.java` | StdExit | IS_FLYING in the base disposition (`:61-66`) | "the open air" | none found |
| `Exits/Impassable.java` | GenExit | Blocks any move (`:48-64`); IS_UNHELPFUL (`:66-72`) | "a blocked way" | `Abilities/Druid/Chant_Treehouse.java:200` |
| `Exits/Skyway.java` | Open | LOOK adds a time, weather and moon trailer (`:55-98`) | "the sky" | `Locales/StdRoom.java:568` |
| `Exits/HiddenWalkway.java` | Open | IS_HIDDEN (`:43-48`) | walkway | `Abilities/Druid/Chant_Den.java:169`; `Abilities/Skills/Skill_Burrow.java:148`; `resources/randareas/oceanfloor.xml:126` |
| `Exits/UnseenWalkway.java` | Open | IS_NOT_SEEN (`:43-48`); passable but not listed | walkway | `Locales/StdRoom.java:570`; `Locales/WaterSurface.java:134` |

The 31 classes plus the two interfaces make up the 33 files.

### Special mechanics by family

**Sky and underwater links.** `StdRoom.giveASky` (`Locales/StdRoom.java:543`) gives an outdoor room an UP exit into a generated `EndlessThinSky` grid (`:572`). The UP exit is a `Skyway` or an `UnseenWalkway` depending on the SKYSIZE sign (`:566-570`). The way back down is a `StdOpenDoorway` (`:566`). Indoor, underwater and air rooms are skipped (`:559-564`). `WaterSurface` does the same downward (`Locales/WaterSurface.java:130-134`). With a positive SKYSIZE it uses one exit object for both sides (`:132`), so that object's `lastRoomID` is shared.

**Crawlways.** A crawlway is a normal exit carrying `Prop_Crawlspace`, as in the construction table row "crawlway" (`resources/skills/construction.txt`). Taller actors may enter only while sitting (`Prop_Crawlspace.java:94-95`). A sleeping actor who stands up in such a room wakes up still crawling (`:116-129`). Fleeing into one sits the actor down and then refuses (`:97-112`). Crawling is modelled as the sitting bit (`IS_SITTING`).

**Climbing and ladders.** `ClimbableExit` forwards `okMessage`, `executeMsg` and `recoverPhyStats` to its `Prop_Climbable` (`ClimbableExit.java:86-108`). That effect sets IS_CLIMBING on the exit (`Abilities/Properties/Prop_Climbable.java:69-73`) and, on ENTER, mounts a nearby ladder or refuses (`:76-106`). The treehouse chant uses this for the ground-to-treehouse link and the way back down (`Abilities/Druid/Chant_Treehouse.java:163-171`).

**Flight.** `FlyingExit` sets IS_FLYING in its constructor (`FlyingExit.java:61-66`). Only actors who are in flight, falling, or riding a flier get past the check (`StdExit.java:499-505`; `Sense.java:1779-1787`).

**Crevasse.** The jump roll and the death are described above. The death uses `CMLib.combat().postDeath(null,mob,null)` (`GapExit.java:83`).

**Curtains.** `GenCurtain.isOpen()` returns `isOpen || passThrough` (`GenCurtain.java:63-67`). `okMessage` sets `passThrough` only around the ENTER check, inside a `passSync` lock (`:69-91`). The curtain stays closed in state and text, but anyone can pass. The curtain recipes use the verbs "draw open" and "draw close", or "raise" and "lower" (`resources/skills/construction.txt` lines 7, 16, 24, 38, 43, 48).

**Hidden and unseen passages.** `HiddenWalkway` serves dens and burrows, `UnseenWalkway` the hidden sky and sea, and `HiddenClosedDoorway` hidden doors. `Prop_Hidden` can set IS_HIDDEN on any non-MOB, non-item object, which covers exits (`Abilities/Properties/Prop_Hidden.java:155`). The "obscured", "hidden" and "secret" door recipes use it.

**Look-through.** `Prop_RoomView` (`Abilities/Properties/Prop_RoomView.java:178-213`) redirects a LOOK or EXAMINE aimed at an exit or room to the far room. It then vetoes the normal look. Its text is the far room's ID, optionally with a direction for "NOUT" and with LONGLOOK. Users: the portal spell (`Abilities/Spells/Spell_Portal.java:203-215`), the den (`Chant_Den.java:172-173`), the burrow (`Skill_Burrow.java:150-151`), and the glass door and window recipes.

**View-only sides.** The treehouse links each neighbouring outdoor room with an `Impassable` exit that carries a `Prop_RoomView` (`Chant_Treehouse.java:200`). You can look out, but the move is refused.

**Traps on exits.** `CoffeeUtensils.setTrapped` (`com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java:411-422`) removes existing traps and adds the new one as an effect. `Trap_Open` reacts to OPEN (`com/planet_ink/coffee_mud/Abilities/Traps/Trap_Open.java:78`). `Trap_Enter` reacts to ENTER and LEAVE (`Abilities/Traps/Trap_Enter.java:74-75`). The trap travels with the exit. The trap classes themselves are outside this bucket and were not read in full.

**Redirecting exits for ships.** When a ship is sunk, each outdoor room in its area that has no UP exit gets a `NamedRedirectable` titled with the ship room's name. `lastRoomUsedFrom(R)` pins the destination (`Items/Basic/GenSailingShip.java:459-462`; `Items/Basic/GenAirShip.java:436-440`). ENTER through it is rewritten to the ship room (`NamedRedirectable.java:96-103`).

**Hideout.** `Thief_Hideout` builds a shelter room whose exit back to the thief's room is an `OpenDescriptable` (`Abilities/Thief/Thief_Hideout.java:222-236`). The intended "The way back to <room>" text is lost (see Problems).

**Portals and gateways.** `Spell_Portal` creates a `GenExit` named "a swirling portal" with a `Prop_RoomView` on each side (`Spell_Portal.java:203-215`). `Prayer_Gateway` creates "a divine gateway" on the GATE direction and applies a timed effect with the value 15 (`Prayer_Gateway.java:163-174`; the unit of that 15 was not checked). `Spell_Portal` applies the same 15 at `Spell_Portal.java:227`.

### Interactions with other systems

- **Rooms.** `StdRoom.setRawExit` adds 1 to `usage` for the new exit and subtracts 1 from the old one (`StdRoom.java:403-406`). The matching destroy is commented out (`StdRoom.java:412-417`).
- **Loading and linking.** An unresolved link becomes a pending `{#id#}` link (`com/planet_ink/coffee_mud/core/database/RoomLoader.java:832`). `CoffeeMaker` sets links (`com/planet_ink/coffee_mud/Libraries/CoffeeMaker.java:1354`, `:1369`) and clears them when the room is linked (`:1436-1438`). Deleting a room repoints generic exits at a dead room (`com/planet_ink/coffee_mud/Libraries/CMMap.java:1547`).
- **Map location.** `CMMap.roomLocation` uses `lastRoomUsedFrom(null)` for an exit (`CMMap.java:1630`). An exit's location is therefore unknown until someone has tried to enter it.
- **Crafting.** `BuildingSkill.buildDoor` (`com/planet_ink/coffee_mud/Abilities/Common/BuildingSkill.java:685-746`) creates the exit from the recipe's class, sets its words, the 9999-tick timer (`:725`), and its level, then writes the opposite side. The build duration comes from `getDuration(ticks, mob, level, 10)` (`:2024`). `generify` (`:254-270`) converts a non-generic exit to `GenExit`.
- **Keys.** The lock check reads the actor's inventory (`StdExit.java:622-636`), so key items are a separate system.
- **Combat.** `GapExit` calls the combat library's death routine (`GapExit.java:83`).

## Content worth mining

### Player-buildable doors, curtains and stairs

`resources/skills/construction.txt` has 41 rows whose build type is DOOR (read by `com/planet_ink/coffee_mud/Abilities/Common/Construction.java:72`). The column meanings come from the `BuildingSkill` constants: level (`RCP_LEVEL`), build ticks (`RCP_TICKS`, `BuildingSkill.java:152`), material quantity and type (`DAT_WOOD`, `DAT_WOODTYPE`), flags (`DAT_FLAG`), class (`DAT_CLASS`) and properties (`DAT_PROPERTIES`), at `BuildingSkill.java:152-162`. The "Props" column is the effect list applied to the exit.

| Name | Lvl | Ticks | Material | Flags | Class | Props |
|---|---|---|---|---|---|---|
| archway | 1 | 25 | 10 | DIR | GenExit | |
| curtain | 3 | 25 | 20 cloth | INDOOR DIR | GenCurtain | |
| weak gate | 5 | 25 | 20 | OUTDOOR DIR | GenDoor | |
| flimsy door | 6 | 25 | 20 | INDOOR DIR | GenDoor | |
| simple gate | 8 | 25 | 30 | OUTDOOR DIR | GenDoor | |
| weak door | 10 | 35 | 40 | INDOOR DIR | GenDoor | |
| crawlway | 11 | 50 | 250 | INDOOR DIR NOWALL | GenExit | Prop_Crawlspace |
| beaded curtain | 13 | 35 | 25 cloth | INDOOR DIR | GenCurtain | |
| half door | 15 | 35 | 45 | DIR INDOOR | GenDoor | |
| light gate | 18 | 35 | 35 | OUTDOOR DIR | GenDoor | |
| simple door | 20 | 45 | 60 | INDOOR DIR | GenDoor | |
| tab drape | 23 | 45 | 50 cloth | INDOOR DIR | GenCurtain | |
| light door | 25 | 45 | 70 | INDOOR DIR | GenDoor | |
| window | 26 | 45 | 50 glass | INDOOR DIR NOWALL | GenDoor | Prop_Crawlspace, Prop_RoomView |
| standard gate | 28 | 45 | 40 | OUTDOOR DIR | GenDoor | |
| standard door | 30 | 55 | 80 | INDOOR DIR | GenDoor | |
| obscured door | 30 | 100 | 100 | DIR INDOOR | GenDoor | Prop_Hidden |
| trap door | 33 | 55 | 120 | DIRUPDOWN INDOOR | GenDoor | |
| sturdy door | 35 | 55 | 90 | INDOOR DIR | GenDoor | |
| sturdy gate | 38 | 55 | 45 | OUTDOOR DIR | GenDoor | |
| strong door | 40 | 65 | 100 | INDOOR DIR | GenDoor | |
| back curtain | 43 | 65 | 100 cloth | INDOOR DIR | GenCurtain | |
| heavy door | 45 | 65 | 120 | INDOOR DIR | GenDoor | |
| strong gate | 48 | 65 | 90 | OUTDOOR DIR | GenDoor | |
| folding door | 50 | 75 | 125 | INDOOR DIR | GenDoor | |
| front curtain | 53 | 75 | 150 cloth | INDOOR DIR | GenCurtain | |
| saloon door | 55 | 75 | 135 | INDOOR DIR | GenDoor | |
| heavy gate | 58 | 75 | 60 | OUTDOOR DIR | GenDoor | |
| hidden door | 60 | 150 | 200 | INDOOR DIR | GenDoor | Prop_Hidden |
| grand drape | 63 | 85 | 200 cloth | INDOOR DIR | GenCurtain | |
| glass door | 65 | 85 | 100 glass | INDOOR DIR | GenDoor | Prop_RoomView |
| reinforced gate | 68 | 85 | 75 | OUTDOOR DIR | GenDoor | |
| reinforced door | 70 | 95 | 150 | INDOOR DIR | GenDoor | |
| massive gate | 78 | 95 | 100 | OUTDOOR DIR | GenDoor | |
| massive door | 80 | 105 | 200 | INDOOR DIR | GenDoor | |
| siege gate | 88 | 105 | 125 | OUTDOOR DIR | GenDoor | |
| barricade door | 90 | 115 | 250 | INDOOR DIR | GenDoor | |
| secret door | 90 | 200 | 250 | DIR INDOOR | GenDoor | Prop_Hidden |
| ladder | 41 | 65 | 120 | DIRUPDOWN INDOOR | GenExit | |
| spiral staircase | 61 | 75 | 250 | DIRUPDOWN INDOOR | GenExit | |
| grand staircase | 81 | 95 | 500 | DIRUPDOWN INDOOR | GenExit | |

Rows are in file order. The doors are in level order, and the ladder and staircases come last (`construction.txt` lines 67-69). Build time goes through `getDuration`, so the real time depends on the builder's skill (`BuildingSkill.java:2024`; the function itself was not read).

### Where exits appear in random areas

Counts are occurrences of `class="..."` in `resources/randareas/*.xml`: `Open` 40, `GenDoor` 29, `Door` 9, `LockedDoor` 6, `Gate` 1, `LockedGate` 1, `GenCurtain` 1, `HiddenWalkway` 1.

A weighted choice from `resources/randareas/oceanfloor.xml:124-127` shows the pattern: an open walkway with pick weight 5, a hidden walkway with 1, and a curtain described as seaweed with 2. Doors in the town file use `<EXIT class="GenDoor" defclosed="true" />` (`resources/randareas/town.xml:137`). Locked prison doors use `text="$areaname cellkey"` (`resources/randareas/prison.xml:122`). Presumably the text is the key name, since `keyName()` returns miscText for a lockable exit (`StdExit.java:911-914`). The XML-to-stat mapping was not read, so this is unverified.

### Names and descriptions worth reusing

| Exit | Name | Description as coded |
|---|---|---|
| GapExit | a crevasse | "Looks like you'll have to jump it." (`GapExit.java:52`) |
| ClimbableExit | a sheer surface | "Looks like you'll have to climb it." (`ClimbableExit.java:59`) |
| FlyingExit | the open air | "Looks like you'll have to fly up there." (`FlyingExit.java:58`) |
| Skyway | the sky | (none; LOOK adds the time and weather trailer) |
| Grate | a barred grate | "A metal grate of thick steel bars is inset here." (`Grate.java:44`) |
| HiddenClosedDoorway | a door | "a cleverly concealed door." (`HiddenClosedDoorway.java:46`) |
| GenAirLock | an air lock | "This door leads to the outside of the ship through a small air lock." (`GenAirLock.java:90`) |
| Portal spell | a swirling portal | "A swirling portal to somewhere" (`Spell_Portal.java:204`) |
| Gateway prayer | a divine gateway | "A divine gateway to somewhere" (`Prayer_Gateway.java:164`) |
| Impassable | a blocked way | "It doesn't look like you can go that way." (`Impassable.java:39`) |
| GenCurtain | a curtain | "An opague cloth is hanging across the doorway." (`GenCurtain.java:48`; the spelling is the code's) |

## Notable design ideas

- **Effects on exits.** Traps, look-through, crawl limits and climbing are attached as effects, the same mechanism rooms and items use. Each one adds a rule to a single exit without a new code path.
- **Terrain as flag bits.** IS_FLYING, IS_CLIMBING, IS_HIDDEN, IS_NOT_SEEN and IS_UNHELPFUL live in the exit's disposition. The movement code tests the same bits for every kind of link (`Sense.java:1083-1092`).
- **Curtain that reads closed.** `GenCurtain` overrides `isOpen()` only during the ENTER check. A closed object stays closed in state and text but still passes anyone (`GenCurtain.java:63-91`).
- **Look without travel.** `Impassable` plus `Prop_RoomView` lets a treehouse show the street below while forbidding the walk (`Chant_Treehouse.java:200`).
- **A link that follows a moving ship.** The sunk-ship redirect exits pin their destination to the ship room, so the water above stays linked to the ship as it moves (`GenSailingShip.java:459-462`).
- **Sky as generated space.** Every outdoor room gets a sky link into a generated grid, and a sign on `SKYSIZE` chooses visible or hidden (`StdRoom.java:559-571`).
- **Recipes as data.** The 41-row construction table maps directly onto `GenDoor`, `GenCurtain` and `GenExit`, with level, time, material and effects. Building a door uses the same path as loading one from data (`BuildingSkill.java:685-746`).
- **Door timer as one countdown.** One timer per exit handles both the auto-close and the re-lock for `defaultsLocked` doors (`StdExit.java:758-759`, `:802-810`).

## Problems and cruft

1. **Swapped verb arguments in the empty-word fallback.** `setExitParams(newDoorName, newCloseWord, newOpenWord, newClosedText)` (`Exits/interfaces/Exit.java:106-109`) is called as `setExitParams(doorName(),openWord(),"close",closedText())` (`StdExit.java:530`) and `setExitParams(doorName(),"open",closeWord(),closedText())` (`:546`). Both pass the words in the wrong slots. This only runs when a GenExit has an empty close or open word, so it is a latent bug (verified by reading).
2. **Lost "The way back" text.** `Thief_Hideout` calls `E.setDisplayText(...)` on an `OpenDescriptable` (`Abilities/Thief/Thief_Hideout.java:230`). `StdExit.setDisplayText` is empty (`StdExit.java:416-419`), and `OpenDescriptable` does not override it (`Exits/OpenDescriptable.java:33-60`). The text is silently dropped. This is verified by reading.
3. **Key name and readable text share one field in GenExit.** `setReadableText` and `setKeyName` both write `keyName` (`GenExit.java:237`, `:249`), and `readableText()` returns `keyName` (`:225`). Setting one overwrites the other, and a readable exit shows its key string (verified by reading).
4. **Pending-link prefix can leak into names and keys.** While a `{#id#}` link is pending, the prefix is part of the stored string. That string is what `keyName()` returns (`StdExit.java:911-914`, `GenExit.java:241-243`), what the key check compares against (`StdExit.java:627`), and what `OpenNameable.Name()` returns raw (`Exits/OpenNameable.java:42-46`). Whether a pending link lasts long enough to be seen in play is unverified. It is cleared on linking (`CoffeeMaker.java:1436-1438`).
5. **Usage comment contradicts the code.** `Exit.exitUsage` says "Not currently functional" (`Exits/interfaces/Exit.java:41-47`), but `StdExit.tick` destroys any exit whose usage is 0 (`StdExit.java:796-800`). The matching cleanup in `StdRoom` is commented out (`StdRoom.java:412-417`). Exits that are removed from a room are destroyed on their next tick, and only if a tick is running on them.
6. **`lastRoomUsedFrom` ignores its argument in the base.** `StdExit` returns the stored last room whatever the caller passes (`StdExit.java:923-926`). `NamedRedirectable` uses the argument (`Exits/NamedRedirectable.java:82-89`), and the interface documents it as "the room from which a perspective is desired" (`Exit.java:127-132`).
7. **Last room records attempts, not uses.** `lastRoomID` is written during `okMessage`, before any door, flying or climbing refusal (`StdExit.java:488-491`). A failed attempt through a locked door still updates it.
8. **Side effects in the veto phase.** `GapExit.okMessage` rolls dice and can kill the actor (`GapExit.java:73-85`). Any other caller that asks for an ENTER `okMessage` gets the same result. Other callers were not checked. The chance is also not clamped: `weight/maxCarry` can exceed 1, and STR of 34 or more makes it negative (`GapExit.java:78`).
9. **Duplicate climb check.** `ClimbableExit.okMessage` runs `Prop_Climbable` first (`ClimbableExit.java:89`), and `StdExit` then repeats the IS_CLIMBING test (`StdExit.java:506-522`). The text is the same in both (`Prop_Climbable.java:100`).
10. **Player doors effectively never close.** The 9999-tick reopen (`BuildingSkill.java:725`) means a built door stays open for about 11 hours. The intent is not stated. It may be a placeholder for "never".
11. **Prop_RoomView parses ATTACK into the wrong flag.** `"ATTACK"` sets `longlook` instead of `attack` (`Abilities/Properties/Prop_RoomView.java:78-79`). This is outside the bucket but affects every look-through exit that uses the attack option.
12. **Constructor calls overridable methods.** `StdExit`'s constructor reads `defaultsClosed()` and `defaultsLocked()` (`StdExit.java:68-69`) before a subclass's fields are set. `GenExit` resets the state afterwards, so it works, but the pattern is fragile.
13. **Base setters are silent no-ops.** `setName`, `setDisplayText`, `setDescription` and `setExitParams` do nothing in `StdExit` (`:205-208`, `:416-424`, `:905-908`). A subclass that forgets to override one loses data without an error. Problem 2 is an instance of this.
14. **Unused classes.** No reference outside `Exits/` was found for `AjarDoor`, `GenAirLock`, `Grate`, `HiddenClosedDoorway`, `NeedleDoor`, `OpenNameable`, `PitOpen`, `TrappedDoor`, `TrappedLockedDoor`, `GapExit` or `FlyingExit`. The class registry loads everything in the package (`coffeemud.ini:2266`, `EXITS=%DEFAULT%`), so they can still be created by name.
15. **Small robustness gaps.** `ClimbableExit.copyOf` dereferences `getAbility` without a null check (`ClimbableExit.java:80-81`). `StdExit.delScript` replaces an emptied list with a new empty list instead of null (`StdExit.java:1272-1273`). `GenCurtain`'s description has a typo, "opague".

## Takeaways for a Python SUD

**Borrow.**

- The door state machine: a few booleans, default flags and one reopen timer. A `refusal()` method that returns the first reason to refuse, or `None`, mirrors `StdExit.okMessage`. A `commit()` method mirrors `executeMsg`.
- Traits as named flags: `flying`, `climbable`, `hidden`, `unseen`, plus a crawl height (24) and a look-through room. A crawl limit is a height check that requires sitting.
- Pinned redirect exits for ships and hideouts, which is a destination fixed at creation.
- The construction table as data. It ports directly to a CSV or YAML file with level, time, material and traits, and it also supplies the crafting costs.
- The curtain idea: a closed object that a player can still pass.

Sketch (a suggested shape, not CoffeeMud code):

```python
from dataclasses import dataclass, field

@dataclass
class Exit:
    dest: str                       # room id this exit leads to
    name: str = "a walkway"
    has_door: bool = False
    is_open: bool = True
    default_closed: bool = False    # closes itself after reopen_turns
    has_lock: bool = False
    is_locked: bool = False
    default_locked: bool = False
    key_id: str = ""                # matched against the key item's key_id
    reopen_turns: int = 45          # CoffeeMud default
    traits: set[str] = field(default_factory=set)   # "flying", "climbable", "hidden", "unseen"
    crawl_height: int | None = None # taller actors must sit to pass
    look_room: str | None = None    # LOOK shows this room instead
    timer: int = 0

    def refusal(self, actor) -> str | None:
        if self.has_door and not self.is_open:
            return "The door is closed."
        if "flying" in self.traits and not actor.flying:
            return "You can't fly."
        if self.crawl_height and actor.height > self.crawl_height and not actor.sitting:
            return "You must crawl that way."
        return None

    def open(self) -> None:
        self.is_open, self.is_locked = True, False
        if self.default_closed or self.default_locked:
            self.timer = self.reopen_turns

    def tick(self) -> None:
        if self.timer > 0:
            self.timer -= 1
            if self.timer == 0 and self.default_closed:
                self.is_open = False
                if self.default_locked:
                    self.is_locked = True
```

Whether to count in turns or in real time depends on the open decision. For a turn-based game, `timer` is a turn counter. For a real-time game, store a deadline timestamp and compare it when the player acts. The 45-tick value is about three minutes at the default tick.

**Simplify.**

- Separate attributes for name, key, readable text and destination. Do not share `miscText`.
- A destination id resolved at load time. Drop the pending `{#id#}` links.
- Drop `usage` counting, `tags`, the XML Gen layer and sysop views.
- Drop the subclass-per-type pattern for pure data. Keep a class only where behaviour differs, such as crevasse rolls or the look trailer.

**Drop or rethink for one player.**

- No others see you leave or arrive, so the two-phase "others" messages are not needed. Keep the actor's own refusal text and any narration.
- The synchronised `passThrough` flag becomes a pure function, `is_passable(actor)`.
- The sky and sea grids are useful only if the SUD has vertical or underwater space. Otherwise they can go.
- Visibility checks against other seers become a single detection rule for the player.

## Coverage

**Read in full (33 of 33 manifest files).** `StdExit.java` and `GenExit.java` in full. Every other class and both interfaces (`Exit.java`, `PrepositionExit.java`) were read in full, with license and import blocks skimmed.

**Read in part, outside the bucket.** `Libraries/MUDTracker.java` (`:1642-1930`, selected lines), `Libraries/Sense.java` (`:518-521`, `:1083-1092`, `:1482-1530`, `:1779-1787`), `Locales/StdRoom.java` (`:395-419`, `:543-585`), `Locales/WaterSurface.java` (`:122-145`), `Abilities/Properties/Prop_Climbable.java` (`:69-106`), `Prop_Crawlspace.java` (`:67-132`), `Prop_Hidden.java` (`:133-156`), `Prop_RoomView.java` (`:60-96`, `:178-227`), `Abilities/Druid/Chant_Treehouse.java` (`:150-215`), `Chant_Den.java` (`:160-178`), `Abilities/Skills/Skill_Burrow.java` (`:140-152`), `Abilities/Thief/Thief_Hideout.java` (`:222-240`), `Items/Basic/GenSailingShip.java` and `GenAirShip.java` (`:425-470`), `Items/Basic/StdPortal.java` (`:352-368`), `Abilities/Spells/Spell_Portal.java` (`:198-222`), `Abilities/Prayers/Prayer_Gateway.java` (`:160-180`), `Libraries/CoffeeMaker.java` (grep and `:1354-1438`), `core/database/RoomLoader.java` (`:815-840`), `Libraries/CMMap.java` (`:1525-1560`, `:1620-1632`), `Libraries/CoffeeUtensils.java` (`:403-422`), `Abilities/Common/BuildingSkill.java` (`:152`, `:233-270`, `:685-746`, `:2024`), `Abilities/Traps/Trap_Open.java` and `Trap_Enter.java` (hook lines only), `Trap_Trap.java` and `Trap_RoomPit.java` (grep lines only), `core/CMProps.java:681`, `core/interfaces/Tickable.java:86,104`, `resources/skills/construction.txt` (DOOR rows), `resources/randareas/*.xml` (EXIT counts and examples), `coffeemud.ini` (`:1058-1069`, `:2266`).

**Not read.** `Abilities/Common/Construction.java` (class line only), the bodies of the trap classes, `DoorKey` and container code, the GenExit XML serialisation in `CoffeeMaker`, the random-area loader that maps EXIT attributes to stats, and the body of `getDuration`.

**Unverified.** Whether the `defclosed` and `text` XML attributes map to the GenExit stat names. Whether pending `{#id#}` links are visible in play. Whether any caller besides `MUDTracker.move` asks an exit for an ENTER veto, which decides how far the GapExit death reaches. The meaning of the 15 passed to `beneficialAffect` in `Prayer_Gateway`. The column names of the construction table, which are inferred from the `BuildingSkill` constants.

**Skimmed.** Import and license blocks of every bucket file.
