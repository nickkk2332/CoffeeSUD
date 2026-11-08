# Rooms, grids and exits: models and base classes

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b171` (Rooms, grids and exits: models and base classes) · 11 files · 9361 lines of Java.

Citations are `path:line`. Paths are relative to the CoffeeMud folder, and the common prefix `com/planet_ink/coffee_mud/` is written once here and dropped below. Short names in the text: `StdRoom` is `Locales/StdRoom.java`, `StdExit` is `Exits/StdExit.java`, and so on. Items marked **[unverified]** were reasoned from the code but not run.

## Summary

CoffeeMud's world is a graph of `Room` objects joined by `Exit` objects, and this bucket holds the base classes that define that graph and the rules for moving through it. `StdRoom` is the common place: it stores identity, description, climate, contents, occupants and direction-indexed exits, and it routes every `CMMsg` through a two-phase pass (`okMessage` to veto, then `executeMsg` to apply) that reaches the room's area, mobs, items, effects, behaviours, scripts and exits. `StdExit` and `GenExit` model passages that can be open, closed, locked and keyed, with a timer that re-closes doors opened from a default-closed state. `StdGrid`, `StdThinGrid` and `StdMaze` let one grid locale stand for a rectangle or maze of identical child rooms, built either eagerly or on demand, and stitched to the neighbouring rooms. For a single-player Python game these are the parts to reproduce: locations, edges that carry state, and a small message bus that lets terrain, objects and scripts veto or react to an action.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Locales/StdRoom.java` | Base room: contents, occupants, exits and neighbour links, message routing, ticks, stats, copy, save and destroy | 3363 |
| `Exits/StdExit.java` | Base exit: door and lock state, open/close/lock/unlock rules, key check, effects, behaviours, usage counter, reopen timer | 1422 |
| `Locales/StdGrid.java` | "Fat" grid: builds an `xsize` by `ysize` array of child rooms up front; redirects entries; cross exits | 1284 |
| `Locales/StdThinGrid.java` | "Thin" grid: creates children on demand in a sorted list; child expiry; neighbour links filled lazily | 1139 |
| `Locales/StdMaze.java` | Maze: a `StdGrid` whose internal passages come from a randomised depth-first carve | 212 |
| `Exits/GenExit.java` | Data-driven exit: door, lock, text and timer fields that builders edit at run time | 486 |
| `Locales/interfaces/Room.java` | Room contract: domain codes, description variation tags, link and resolve API, message helpers | 1003 |
| `Exits/interfaces/Exit.java` | Exit contract: door words, closed text, temporary link, last room used | 133 |
| `Locales/interfaces/GridLocale.java` | Grid contract and the `CrossExit` record | 183 |
| `Locales/interfaces/LocationRoom.java` | Room placed at a coordinate on a planet surface | 69 |
| `Exits/interfaces/PrepositionExit.java` | Exit with its own entry and exit phrases | 67 |

Outside this bucket, the following are cited for interactions: `Locales/ThinRoom.java` (lazy placeholder room), `Libraries/MUDTracker.java` (the movement pipeline and NPC movement), `Abilities/StdAbility.java` (room effects), `Commands/Open.java` (the open command), `core/Directions.java`, `core/CMProps.java`, `core/interfaces/Tickable.java`, `Libraries/interfaces/ThreadEngine.java`, `Libraries/interfaces/WorldMap.java` and `Libraries/CoffeeMaker.java`.

## How it works

### 1. Room data model (StdRoom)

`StdRoom` implements `Room` (`Locales/interfaces/Room.java:46`, which extends `PhysicalAgent`, `ItemPossessor`, `Places` and `MOBPossessor`). The fields that matter:

- **Address.** `_roomID` (`StdRoom.java:50`) is the world-map address, of the form `AreaName#N` for primary rooms. Grid children and temporary rooms keep `""` (`Room.java:49-53`). `ID()` is the Java class identity and is a different thing.
- **Text.** `name` (`:51`, default `"the room"`), `displayText` (`:52`, default `"Standard Room"` from `:48`) and `description` (`:55`). The description is a `String`, or compressed bytes when `ROOMDCOMPRESS` is set (`:453-455`, `:467-470`). When `ROOMDNOCACHE` is set and the room has an address, the description is read back from the database (`:437-446`).
- **Links.** `exits[]` (`:59`) holds the `Exit` object for each direction. `doors[]` (`:60`) holds the `Room` the passage leads to, which may be a grid, a stand-in or a placeholder. `Room.java:391-405` explains why the two are kept apart: `doors[d]` is the raw destination and may be temporary, so callers should use `getRoomInDir` (`:416`).
- **Occupants.** `inhabitants` (`:77`) and `contents` (`:78`) are `SVector`s. `SVector` is backed by a `CopyOnWriteArrayList` (`core/collections/SVector.java:40-42`), so a handler can remove an item while the room is iterating over its contents.
- **Physical numbers.** `phyStats().weight()` is the movement cost, set to `2` at `:91` and read at `:171-174`. `phyStats().height()` is the room size, `1` indoors and `10` outdoors (set at `:92` from `domainType()&Room.INDOORS`). `maxRange()` returns that height (`:2895-2898`), and the `Room` contract says it sets the ranged-weapon limit and the room size in ship combat (`Room.java:418-425`).
- **Climate.** `atmosphere` and `climask` (`:65-66`) default to an INHERIT sentinel. Lookups go to the grid parent first, then to the area (`:188-209`, `:359-380`).
- **Resources.** `myResource` and `lastResourceTime` (`:67-68`), covered under section 7.

The direction set is fixed when the server starts. `Directions.NUM_DIRECTIONS` defaults to 7 (`core/Directions.java:175`), giving NORTH 0, SOUTH 1, EAST 2, WEST 3, UP 4, DOWN 5 and GATE 6 (`core/Directions.java:80-92`). The `DIRECTIONS` config value (read at `core/CMProps.java:3241`) can set it to 11, which adds NORTHEAST 7, NORTHWEST 8, SOUTHEAST 9 and SOUTHWEST 10 (`core/Directions.java:366` is where `reInitialize` stores the count). `Directions.getOpDirectionCode` gives the reverse direction (`core/Directions.java:1251`). GATE is an ordinary slot used for portal-like passages, and the grid code skips it when linking the perimeter (`Locales/StdGrid.java:749-750`).

A passage is two writes. Each side of a link gets its own `Exit` object, and `setRawExit` maintains a usage count on each exit (`StdRoom.java:395-410`: `E.exitUsage((short)-1)` for the replaced exit and `to.exitUsage((short)1)` for the new one). `getReverseExit` (`:1617-1626`) finds the far side's exit, and `getPairedExit` (`:1629-1638`) returns it only if both sides agree on whether there is a door. `StdExit.exitUsage` (`Exits/StdExit.java:189-203`) is the counter itself; section 4 covers what happens when it reaches zero.

**Resolution.** `getRoomInDir` (`StdRoom.java:1662-1676`) first lets the grid parent adjust the destination (`prepareGridLocale`), then asks the destination to resolve itself (`prepareRoomInDir`), and returns `null` if that resolution finds the room destroyed. `getExitInDir` (`:1679-1686`) asks for `getRoomInDir` once when a grid child has no raw exit, so that lazy grids create their edges on demand.

### 2. Descriptions and variation tags

`Room.VariationCode` (`Locales/interfaces/Room.java:242-308`) lists the tags that can appear in a room's title or description. Each tag has a letter class and a numeric value. The full list is in the content section. The tag is written `<NAME>` and closed `</NAME>` (the `openTag` and `closeTag` are built in the enum constructor at `:298-307`). Per viewer, `displayText(mob)` and `description(mob)` call `CMLib.commands().parseVaries(mob, area, this, text)` (`StdRoom.java:1408-1417`). I did not read `parseVaries`, so the exact rule for choosing a branch is **[unverified]**.

### 3. Message routing: the two phases

Every action is a `CMMsg` (`Common/interfaces/CMMsg.java`) with a source, a target, a tool, and separate codes and texts for each party. In a room the entry points are:

- `send(source, msg)` (`StdRoom.java:1734-1739`). It calls `source.executeMsg` first, then `sendAndExec`, then the trailer messages.
- `sendAndExec` (`:1688-1699`). It runs `executeMsg` on every other occupant (`:1692-1696`), then on the room itself (`:1697`), then passes the message to `CMLib.commands().monitorGlobalMessage` (`:1698`).
- `sendOthers` (`:1742-1746`) skips the source.
- `show(...)` and its many overloads (`:1772-1839`) build a message with `CMClass.getMsg`, call `okMessage` unless the code carries `CMMsg.MASK_ALWAYS` (`:1775`), and return `false` if the message is refused. `showOthers` and `showSource` (`:1842-1895`) follow the same pattern.
- **Trailers.** `sendTrailermsgs` (`:1701-1731`) sends follow-on messages, but only down to a depth of 3 (`:1704`).

The **okMessage** pass (`StdRoom.java:745-903`) runs in this order:

1. The area's `okMessage` (`:747`). A refusal from the area stops everything.
2. Room-specific checks on messages that target the room (`:750-854`). Examples: expiry is refused unless the room is clearable (`:755-768`); entering runs faction and achievement updates for players from another area and calls `giveASky(0)` (`:775-789`); shop verbs need a shopkeeper (`:801-812`); digging is limited by domain (`:815-841`).
3. The source mob, if it is an inhabitant (`:856-860`), then the other inhabitants (`:861-867`).
4. Items (`:868-873`), effects (`:874-879`), behaviours (`:880-885`) and scripts (`:886-891`).
5. Every exit, in descending direction order (`:893-901`).

The **executeMsg** pass (`:906-1196`) applies the change:

- The area first (`:908`).
- Target-specific effects (`:910-969`). ENTER and RECALL refresh room stats unless the `MASK_OPTIMIZE` flag is set (`:927-931`) and record a room visit for the player (`:932-937`). LOOK_EXITS prints the exit list (`:940-945`). DIG adds a `HoleInTheGround` item (`:962-965`).
- A gravity relay that copies the message to each inhabitant (`:972-991`). Its comment asks whether it belongs in the space-ship area.
- Items (`:993-1003`), then exits (`:1005-1010`), then behaviours, scripts and effects (`:1012-1044`).
- Special handling for shutdown saves (`:1046-1088`) and for expiry (`:1090-1195`).

The walking example shows the whole pattern. `Libraries/MUDTracker.java:1665-1850` (`move`) builds two messages. The leave message has the old room as its target and the opposite exit as its tool (`:1724`, `:1737`). The enter message has the new room as its target and the exit as its tool (`:1720`, `:1730`). Then it runs the veto chain in this order: mob leave (`:1752`), exit enter (`:1755`), old room leave (`:1758`), opposite exit leave, new room enter (`:1764`), and mob enter. Only after every check passes does it run `exit.executeMsg(mob, enterMsg)` (`:1831`), remove the mob from the old room, send the leave message to the old room (`:1835`), set the new location (`:1843`), send the enter message to the new room (`:1845`), and run `opExit.executeMsg(mob, leaveMsg)` (`:1848`). The messages a player sees are built at `:1720-1737`, and use the exit's preposition phrases when it is a `PrepositionExit` (`:1690-1697`).

Exits are part of that relay, so an exit hears every message in its room. `StdExit.okMessage` runs its behaviours, scripts and effects before it checks whether it is the target (`StdExit.java:451-469`, target filter at `:472`). `StdExit.executeMsg` does the same (`:702-734`, filter at `:737-738`). See problem 6 in the cruft section.

### 4. Ticks, timers and expiry

Tick constants: the default tick is 4000 ms (`core/CMProps.java:681`, overridable with `TICKTIME` at `:2720`). The tick IDs are in `core/interfaces/Tickable.java:82-104`. The ones used here are `TICKID_MOB` (0), `TICKID_EXIT_REOPEN` (2), `TICKID_ROOM_BEHAVIOR` (8), `TICKID_ROOM_ITEM_REJUV` (10) and `TICKID_EXIT_BEHAVIOR` (11). The scheduler contract is in `Tickable.java:70`: a tick returns `false` to stop being called.

**Room tick** (`StdRoom.java:1225-1275`). The status field is set at each stage: `STATUS_START`, `STATUS_BEHAVIOR`, `STATUS_SCRIPT`, `STATUS_AFFECT`, then `STATUS_NOT` (`:1227`, `:1238`, `:1247`, `:1259`, `:1273`). If the area has been destroyed, the room destroys itself and logs `"Destroying ... because of destroyed area."` (`:1228-1233`). A `TICKID_ROOM_BEHAVIOR` tick runs behaviours and then scripts, and returns `false` when the room has neither (`:1236-1237`). Any other tick runs the room's effects and calls `unInvoke` on each effect whose `tick` returns `false` (`:1257-1272`).

Behaviours and scripts start the room's tick: `addBehavior` calls `startTickDown(this, TICKID_ROOM_BEHAVIOR, 1)` the first time the list is non-empty (`:3039-3040`), and `addScript` does the same (`:3150-3151`). Effects on rooms start `TICKID_MOB` with a count of 1, because `StdAbility.addEffect` does that for any target that is not a mob (`Abilities/StdAbility.java:685-690`, reached from the non-mob branch of `startTickDown` at `:766-775`). The result is that a room with an effect is called every tick.

**Exit tick** (`StdExit.java:790-856`):
- Usage sweep first. If `usage<=0` the exit destroys itself and returns `false` (`:796-799`). The consequences are covered below.
- `TICKID_EXIT_REOPEN` (`:802-812`). If the exit defaults closed it sets `isOpen=false`; if it defaults locked it also sets `isLocked=true`; then it returns `false`. The handler sends no message to anyone in the room, and it does not check for an occupant in the doorway.
- `TICKID_EXIT_BEHAVIOR` (`:813-839`) runs behaviours and scripts.
- Anything else runs effects (`:840-855`).

**Reopen timer.** When a mob opens a door that defaults closed or defaults locked, `StdExit.executeMsg` cancels any pending timer and then calls `startTickDown(this, TICKID_EXIT_REOPEN, openDelayTicks())` (`StdExit.java:756-760`). `openDelayTicks()` returns `45` (`StdExit.java:1337-1340`); `GenExit` stores the value in a field whose default is also `45` (`GenExit.java:52`, `:80`, setter at `:259-262`). At the default 4000 ms tick this is 180 seconds. The doc comment on `ThreadEngine.startTickDown` calls the last argument "the number of given ticks between Tickable ticks" (`Libraries/interfaces/ThreadEngine.java:65-74`), but the reopen handler returns `false`, so it fires once; I read this as a countdown of 45 ticks.

**Room expiry.** Rooms that only exist to support a grid or a thin area carry an expiry time. The constant is `WorldMap.ROOM_EXPIRATION_MILLIS = 2500000` (`Libraries/interfaces/WorldMap.java:48`), about 41.7 minutes. Thin-grid children are stamped with it when they are created (`Locales/StdThinGrid.java:369`) and refreshed each time they are fetched (`:298-304`). `StdRoom.prepareRoomInDir` refreshes it when the room has an expiry (`StdRoom.java:1656-1657`). A stand-in room that loads a thin area's room sets it too (`Locales/ThinRoom.java:235-290`).

**Expiry message.** `TYP_EXPIRE` is handled in `StdRoom.executeMsg` (`:1090-1195`). Player corpses in the room are moved to the start room and the owner is told "Your corpse has been moved to @x1" (`:1108-1116`). The grid parent is notified (`:1118-1119`). If the room is not part of a shutdown, it is emptied and destroyed, and its room number is returned to the area through `addProperRoomnumber` (`:1186-1193`).

**Usage sweep.** An `Exit`'s usage counter is a reference count: each room slot that points at it adds one (`StdRoom.java:406`). The code that would destroy an exit when its count reaches zero is commented out with the note `cant be done` (`StdRoom.java:412-417`). So an exit whose count drops to zero is removed only if it happens to tick (`StdExit.java:796-799`), and an exit with no behaviour, script, effect or reopen timer never ticks.

### 5. Exits: doors, locks and keys

**Flags and defaults.** `StdExit` has four rule methods, `hasADoor`, `hasALock`, `defaultsClosed` and `defaultsLocked`, which all return `false` by default (`StdExit.java:105-127`). The state is `isOpen` (`:47`) and `isLocked` (`:48`). The constructor sets them from the defaults (`:68-69`). Subclasses override the rules, so the door type is defined by its class:

| Class | Base | Door | Lock | Default closed | Default locked | Source |
|---|---|---|---|---|---|---|
| `StdOpenDoorway` | `StdExit` | no | no | no | no | `Exits/StdOpenDoorway.java` |
| `StdClosedDoorway` | `StdExit` | yes | no | yes | no | `Exits/StdClosedDoorway.java:62-82` |
| `StdLockedDoorway` | `StdClosedDoorway` | yes | yes | yes | yes | `Exits/StdLockedDoorway.java:44-52` |
| `AjarDoor` | `StdExit` | yes | no | no (starts open) | no | `Exits/AjarDoor.java:62-82` |
| `GenAirLock` | `GenExit` | yes | no | yes | no | `Exits/GenAirLock.java:56-76` |
| `LockedGate` | `Gate` (`StdClosedDoorway`) | yes | yes | yes | yes | `Exits/LockedGate.java:48-56` |

`Open` extends `StdOpenDoorway` with the ID `Open`. `Door`, `Gate`, `Grate`, `NeedleDoor`, `TrappedDoor` and `HiddenClosedDoorway` extend `StdClosedDoorway`. `LockedDoor` and `TrappedLockedDoor` extend `StdLockedDoorway`. `HiddenWalkway`, `UnseenWalkway` and `Skyway` extend `Open`. The door types show what changes in each: the words, the closed text and the rules.

**Rules by message** (`StdExit.okMessage`, `:448-653`; `executeMsg`, `:699-782`):

| Message | Refused when | Message shown | Lines |
|---|---|---|---|
| Look, examine, read, knock, ok-visual, ok-action | never | none | `:480-487` |
| Enter | door shut (and mob height is zero or more) | "The @x1 is @x2." or "You can't go that way." | `:488-498` |
| Enter | flying exit, mob not flying or falling | "You can't fly." | `:499-505` |
| Enter | climbing exit, mob not climbing, flying or falling (a ladder may be mounted first) | "You need to climb that way, if you know how." | `:506-522` |
| Close | no door; already shut | "There is nothing to @x1!"; "The @x1 is already @x2." | `:527-542` |
| Open | no door; already open; locked | "There is nothing to @x1 that way!"; "The @x1 is already @x2!"; "The @x1 is locked." | `:543-563` |
| Push, pull | open, or no door | "There is nothing to push over there." (and "pull") | `:564-582` |
| Lock | no door; open; no lock; already locked | "There is nothing to lock that way!"; "The @x1 is already @x2!"; "There is no lock!"; "The @x1 is already locked." | `:583-641` |
| Unlock | no door; open; no lock; not locked | as above, with "The @x1 is not locked." | `:590-641` |
| Anything else aimed at the exit | always | "You can't do that to <T-NAME>." | `:646-651` |

The state messages use past-tense forms of the door verbs. `StdExit` builds them with `closeWordPastTense()` and `openWordPastTense()` (`:438-446`), which give "closed" and "opened" by default. The door word is also a parameter: `setExitParams` on `GenExit` (`GenExit.java:210-220`) replaces the door name, the close and open verbs and the closed text, so a builder can make a "hatch" that opens with "lift".

**Effects of the messages** (`StdExit.executeMsg`, `:739-781`):
- Close sets `isOpen=false` (`:748-752`).
- Open clears the lock, sets `isOpen=true` and arms the reopen timer (`:753-763`).
- Lock sets `isOpen=false` and `isLocked=true` (`:764-769`).
- Unlock clears the lock (`:774-778`).
- Push and pull say "It doesn't appear to be doing any good." (`:770-773`).

**Key check** (`StdExit.java:583-641`). Unlock and lock look through the mob's inventory for a `DoorKey` whose `getKey()` equals the exit's `keyName()` (`:622-627`). A key qualifies if it is carried directly, or if it sits in a container the mob carries that accepts keys (`:628-630`, which tests `Container.CONTAIN_KEYS`). A match becomes the message tool (`:633-634`). Without a match the message is "You don't seem to have the key." (`:638`).

**Viewing.** `viewableText` (`StdExit.java:655-697`) returns the open description or the closed text, adds disposition blurbs from the flags library, and says "darkness" when the room cannot be seen. For a system operator it shows the room ID and the class ID instead (`:660-666`).

**Last room memory.** `StdExit` writes `lastRoomID` during the enter check, before the door test (`:488-490`). `lastRoomUsedFrom` returns `CMLib.map().getRoom(lastRoomID)` and ignores its argument (`:922-925`). Callers use it, for example `Abilities/Druid/Chant_Farsight.java:138`.

**Ladders.** Climbing exits can trigger `CMLib.tracking().findALadder` and `postMountLadder` (`StdExit.java:511-515`) before the climb check.

**GenExit.** `GenExit` (`Exits/GenExit.java:36`) keeps its state in editable fields: name, description, display text, closed text, door name, close and open words, the has-door, lock and default flags, readable, and `openDelayTicks` (`:38-54`). Its builder codes are the `CODES` array at `:300-305`, which runs from `CLASS` to `TAGS`. `setMiscText` reloads the XML and then recomputes `isOpen` and `isLocked` from the defaults (`:96-102`). `text()` writes the XML (`:90-93`).

**Temporary links.** An exit whose target room is not loaded yet keeps the target ID in its text as a prefix, `{#roomid#}` (`StdExit.java:943-973`; `GenExit.java:265-292`). The form `{{#roomid#}}` is a special case that only sets `lastRoomID` (`StdExit.java:958-962`). The loaders and the builder set these (`core/database/RoomLoader.java:832` and `:871`; `Libraries/CoffeeMaker.java:1354-1369`), and the link is cleared when the room is found (`Libraries/CoffeeMaker.java:1436-1438`).

### 6. Grid locales

`GridLocale` (`Locales/interfaces/GridLocale.java:51-157`) is a `Room` that also holds child rooms in a rectangle. The contract is: children share one class (`getGridChildLocaleID`, `:58`), are placed by coordinates, and reach the outside only through the perimeter or through outer exits. `CrossExit` (`:164-182`) is the record for an outer exit: `x`, `y`, `dir`, `destRoomID` and `out`. With `out` set the exit leaves the grid from that cell (`StdGrid.java:558-585` reads `EX.out` that way). With `out` clear it is an entrance into that cell from the named room (`StdGrid.java:329-341`).

**Fat grid: `StdGrid`.**
- Size. The default is 5 by 5 (`:51-52`). The setters ignore values of zero or less (`:145-156`).
- Child creation (`:1159-1195`). Each child is a `StdRoom` (`getGridChildLocaleID`, `:73-75`), with an empty address and its grid parent set (`:1166-1167`), the grid's area (`:1169`), and a description and display text chosen from the `<P>`-separated variants (`:1170-1185`). The effects and behaviours of the grid are copied to each child (`:1187-1193`). Scripts are not copied.
- Build (`:887-925`). All cells are created, then each is linked to its north and west neighbours and, when the 11-direction set is enabled, to its north-west and south-west neighbours (`:893-912`; the diagonal guard is `:906`). `buildFinalLinks` (`:744-807`) then joins the edge cells to the rooms in `doors[]`: the north edge to the north door, and so on, and UP and DOWN to every cell. `fillInTheExtraneousExternals` (`:833-885`) handles the outer exits. `distributeStuff` (`:946-970`) moves occupants and loose items into random children, but only for a grid that has an address and when no administrator is present (`:948`).
- Trigger. `getBuiltGrid` builds the grid when `subMap` is null (`:347-354`), so the cost arrives at the first access.
- Entry from outside (`prepareRoomInDir`, `:185-196`, which calls `getAltRoomFrom`). First, an outer entrance whose coordinates and destination match is used (`:329-341`). Otherwise `getGridRoomFrom` (`:265-316`) checks whether the outside room is itself a child of another grid of the same size. If it is, the mob enters at the cell on the opposite edge, in the same row or column: arriving from the east lands in `grid[length-1][xy.y]` (`:281-291`). Corners map to corners (`:293-296`, `:307-310`). Otherwise `findCenterRoom` (`:697-742`) searches outward from the middle of the entry edge, using the starting points in `initCenterRoomXY` (`:620-657`) and step sizes from `initCenterRoomAdjustsXY` (`:661-695`).
- The grid as a destination (`okMessage`, `:1198-1232`). An ENTER aimed at the grid object itself is rewritten to the child that matches the direction from the mob's room, through `msg.modify`. If no direction matches, the message is refused with "Some great evil is preventing your movement that way." (`:1217`). See problem 19 about whether this branch can ever succeed.

**Thin grid: `StdThinGrid`.**
- Storage (`:52-53`). Materialised children are kept in `rooms`, a list of `ThinGridEntry` sorted by the key `(x<<31)+y` (`:215`, `:226`, `:257`). `properRoomIndex` (`:208-249`) is a binary search over that key. `getSortedRoom` (`:279-296`) and `addSortedRoom` (`:251-277`) use it.
- Creation (`:327-376`). A child is created the first time a cell is needed, with the same description and display choice as the fat grid (`:347-360`), its effects and behaviours (`:362-368`), an expiry (`:369`) and its area (`:371`). A double check on the list stops two threads from creating the same cell (`:332-337`).
- Lazy neighbours (`:407-572`). When a child is first used, `prepareGridLocale` (`:307-313`) fills its exits. `fillExitsOfGridRoom` makes or finds up to eight neighbours and links them, then adds the outer exits (`:516-563`). A bit in the room's `SENSE_ROOMSYNC` sense mask guards against re-entry (`:412-418`, cleared at `:567-570`).
- `buildGrid` only clears the grid (`:883-887`). Nothing is built up front.
- Expiry (`:1012-1046`). When a child receives `TYP_EXPIRE`, its entry is removed from the list and every neighbour's raw door to it is cleared (`:1030-1040`).
- Entry (`getAltRoomFrom`, `:620-722`). The same order as the fat grid applies, with the matching cell taken from the sorted list. The outer entrance check comes first (`:627-635`). For a centre fallback it uses the middle of the edge, with DOWN mapped to bottom centre (`:716-718`; see problem 7).

**Maze: `StdMaze`.**
- `getGridRoom` (`:48-59`) is an override that copies a sense flag to the child if the child already has it (see problem 9).
- The entry link. `findCenterRoom` (`:61-78`) takes the cell that the centre search finds for an edge and links it to the outside room in that direction, using `linkRoom` (`:72-73`).
- Walls. `goodDir` (`:80-97`) refuses UP, DOWN, GATE and any step off the edge. `roomDir` and `getX` and `getY` (`:99-128`) step through the grid.
- Carve (`:130-177`). This is a depth-first, randomised carve. From the current cell it picks a random direction (`:162`, `roll(1, NUM_DIRECTIONS, 0) - 1`) until it finds an unvisited neighbour, links to it (`:173`), and recurses (`:174`). It loops until no neighbour is unvisited (`:139-176`).
- Build (`:187-211`). Every cell is created up front (`:193-202`), the maze is carved from the centre cell `(xsize/2, ysize/2)` (`:182-184`), then the perimeter is linked and the children are distributed.

### 7. Climate, movement cost and resources

The numbers that a room hands to the rest of the engine:

- **Movement cost per step.** The base is `2` (`StdRoom.java:91`). `pointsPerMove()` returns `getArea().getClimateObj().adjustMovement(getMovementCost(), this)` (`:2860-2863`), so the climate can change it.
- **Thirst per round.** `baseThirst()` is `1` (`:2865-2868`). `thirstPerRound()` (`:2871-2886`) adds `+1` for hot, `-1` for wet, `+1` for dry and `+1` for windy (`:2875-2882`), then passes the total to `adjustWaterConsumption`. It returns `0` if the area has no climate object (`:2883-2885`).
- **Walking cost.** `MUDTracker.move` spends energy for each step, `RUNCOST` when running and `WALKCOST` otherwise (`Libraries/MUDTracker.java:1780-1783`). A mob with no movement left is told "You are too tired." (`:1790`) unless it is fleeing, the action is forced (`always`), or the mob holds goto security rights. A player's hygiene is adjusted by `pointsPerMove()` (`:1799`).
- **Resources** (`StdRoom.java:692-742`). A room may have a gatherable resource. It is cleared after 30 minutes (`:696-697`, `30*TimeManager.MILI_MINUTE`). When it is re-rolled, the roll is weighted by `RawMaterial.CODES.FREQUENCY` (`:722`). Each neighbouring room of the same class that already holds a resource adds a preference of `map.getMax() / 4 / preferredChoices.size()` to the range (`:728`, `:731-732`). The final pick is `CMLib.dice().roll(1, map.getMax(), 0)` (`:736`).
- **Sky.** `giveASky(depth)` (`:543-625`) gives an outdoor room with no UP door an endless sky above it, unless the room is underwater or in the air (`:559-563`) or the `SKYSIZE` setting is zero (`:563`). If `SKYSIZE` is positive the link is a `Skyway`, otherwise an `UnseenWalkway` (`:566-570`). The sky grid is an `EndlessThinSky` (`:572`), joined to the room in both directions (`:575-578`). A player entering the room triggers this (`:779-788`).

### 8. Lifecycle

- **Creation.** The constructor sets the movement cost, room size and a fresh `PhyStats` (`StdRoom.java:86-94`). `copyOf` and `cloneFix` (`:250-350`) copy the contents (each item is copied and re-owned), the occupants that are savable mobs (`:304-315`), effects that cannot be uninvoked (`:316-321`), behaviours and scripts. Exits are copied one by one (`:266-276`).
- **Saving.** A room is savable when it has an address and its area is not an instance child, or is a player instance (`:1939-1947`). Shutdown saves mobs and shops (`:1046-1088`), and expiry saves them again (`:1121-1182`).
- **Destroy** (`:1956-2005`). It returns at once if the room is undestroyable (`:1958`). Otherwise it removes the room from the map registry (`:1960`), clears effects (`:1961`), inhabitants (`:1962`), behaviours and scripts, the tick, items (`:1966`), grid children and sky (`:1967-1969`). For a room with no address it unlinks each neighbour's raw door to it (`:1970-1988`), then sets its area to null, which removes it from the map cache (`:1991`).
- **Rejuvenation.** When a room is built, `startItemRejuv` (`:1198-1216`) starts the rejuvenation ticker for each top-level item whose rejuv value is set.

### 9. Interactions with other systems

- **Area.** `getArea()` never returns null (`:510-515`). Each room asks its area first in `okMessage`, `executeMsg`, `affectPhyStats`, `affectCharStats` and `affectCharState` (`:747`, `:908`, `:1356`, `:1374`, `:1389`). Climate comes from `getArea().getClimateObj()` (`:2862`, `:2883`), and atmosphere from the area's `getAtmosphere()` when the room has none of its own (`:200-209`).
- **Map registry.** `CMLib.map()` supplies `getRoom`, `getExtendedRoomID`, `registerWorldObjectDestroyed` (`StdRoom.java:1960`; `StdExit.java:246`), `emptyRoom` (`StdRoom.java:1189`), `isClearableRoom` (`Libraries/CMMap.java:1267`) and `getSafeRoomToMovePropertyTo` (`StdGrid.java:1028`).
- **Database.** `DBReadRoom`, `DBReadRoomExits` and `DBReadContent` load a stand-in room (`Locales/ThinRoom.java:235-290`). `DBUpdateMOBs` and `DBUpdateItems` save on expiry (`StdRoom.java:1145`, `:1181`). `DBReadRoomDesc` reads a description that is not cached (`:439`).
- **Threads.** `CMLib.threads().startTickDown` and `deleteTick` (`Libraries/interfaces/ThreadEngine.java:65-124`).
- **Commands and display.** `CMLib.commands()` provides `parseVaries`, `lookAtExits` and `lookAtExitsShort` (`:942-944`), `handleBeingLookedAt` (`:743`, `:948`), `handleBeingSniffed` (`:951`), `handleBeingRead` (`:746`) and `monitorGlobalMessage` (`:1698`). The open command (`Commands/Open.java:55-62`) builds a `MSG_OPEN` aimed at the exit and sends it through the room with `okMessage` and then `send`, so the exit's own rules run through the room's exit loop (`StdRoom.java:1005-1010`).
- **Movement and NPCs.** `MUDTracker.move` (`Libraries/MUDTracker.java:1665-1850`) is the player and NPC walking pipeline. Its NPC driver `beMobile` (`:910-1183`) opens a closed door that it is about to pass through and closes and relocks it afterwards (`:1061-1084`, `:1158-1170`).
- **Ability effects.** Effects on rooms and exits are ticked through `TICKID_MOB` (`Abilities/StdAbility.java:685-690`).
- **Factions and achievements.** A player entering a room from a different area updates faction standing and the `AREAVISIT` achievement (`StdRoom.java:782-786`).

## Content worth mining

**Outdoor domain codes** (`Locales/interfaces/Room.java:81-107`, names at `:110-126`):

| Code | Name | Notes |
|---|---|---|
| 0 | CITY | default for `StdRoom` (`StdRoom.java:353-356`) |
| 1 | WOODS | |
| 2 | ROCKY | |
| 3 | PLAINS | |
| 4 | UNDERWATER | |
| 5 | AIR | excluded from getting a sky; the code comment says this stops the air room from having one (`StdRoom.java:561-562`) |
| 6 | WATERSURFACE | |
| 7 | JUNGLE | |
| 8 | SWAMP | |
| 9 | DESERT | |
| 10 | HILLS | |
| 11 | MOUNTAINS | |
| 12 | SPACEPORT | |
| 13 | SEAPORT | |

**Indoor domain codes** (`Room.java:134-152`, names at `:155-167`). The indoor flag is `128` (`Room.java:78`). The codes are STONE, WOODEN, CAVE, MAGIC, IN_UNDERWATER, GAP, CAVELAKESURFACE, METAL, INNERSEAPORT and CAVESEAPORT, in that order from offset 0 to 9.

**Description variation tags** (`Room.java:242-308`). A tag is shown only when its condition holds for the viewer.

| Class letter | Tags |
|---|---|
| S (season) | SUMMER, SPRING, WINTER, FALL |
| C (time of day) | DAY, DAYTIME, NIGHT, NIGHTTIME, DAWN, DUSK |
| W (weather) | RAIN, SLEET, SNOW, CLEAR, HEATWAVE, FOG, THUNDERSTORM, BLIZZARD, WINDY, DROUGHT, DUSTSTORM, COLD, HAIL, CLOUDY |
| M (state) | SWIMMING, FLYING, CRAWLING, SITTING, FALLING, CLIMBING, INVISIBLE, HIDDEN |
| V | VISITED (the viewer has been here) |
| F (entry direction) | FROMUNKOWN, FROMNORTH, FROMSOUTH, FROMEAST, FROMWEST, FROMUP, FROMDOWN, FROMGATE, FROMNORTHEAST, FROMNORTHWEST, FROMSOUTHEAST, FROMSOUTHWEST |
| other | MASK (X), ELSE (newline), VARIES (carriage return) |

The `CRAWLING` and `SITTING` tags share the same state bit (`Room.java:270-271`).

**Directions** (`core/Directions.java:80-92`): NORTH 0, SOUTH 1, EAST 2, WEST 3, UP 4, DOWN 5, GATE 6, NORTHEAST 7, NORTHWEST 8, SOUTHEAST 9, SOUTHWEST 10. The 7-direction set is the default, and the 11-direction set comes from the `DIRECTIONS` config (`core/CMProps.java:3241`). A ship-name set also exists (`core/Directions.java:172`, `:305`). `StdRoom` matches exit names through the room's direction type (`StdRoom.java:2842-2846`).

**Exit classes** (`Exits/`): the table in section 5 lists the base types. The rest are named here so the set is complete: `AjarDoor`, `ClimbableExit`, `Door`, `FlyingExit`, `GapExit`, `Gate`, `GenAirLock`, `GenCurtain`, `GenDoor`, `Grate`, `HiddenClosedDoorway`, `HiddenWalkway`, `Impassable`, `LockedDoor`, `LockedGate`, `NamedRedirectable`, `NeedleDoor`, `Open`, `OpenDescriptable`, `OpenNameable`, `OpenPrepositional`, `PitOpen`, `Skyway`, `StdClosedDoorway`, `StdLockedDoorway`, `StdOpenDoorway`, `TrappedDoor`, `TrappedLockedDoor` and `UnseenWalkway`.

**Grid and maze families** (`Locales/`). Many terrains have a fat grid, a thin grid and a maze, named by a common pattern. Examples from the directory listing: `WoodsGrid`, `WoodsThinGrid`, `WoodsMaze`, `MountainsGrid`, `MountainsThinGrid`, `MountainsMaze`, `DesertGrid`, `DesertThinGrid`, `DesertMaze`, `JungleGrid`, `JungleThinGrid`, `CaveGrid`, `StoneGrid`, `StoneThinGrid`, `StoneMaze`, `WetCaveMaze`, `SewerMaze`, `MetalRoomMaze`, `UnderWaterGrid`, `UnderWaterThinGrid`, `UnderWaterMaze`, `AutoGrid`, plus the sky and sea grids `EndlessSky`, `EndlessThinSky`, `EndlessOcean` and `EndlessThinOcean`. The terrain classes themselves are other buckets.

**Player-facing messages** (verbatim, with `@x1` and `@x2` filled in by the code):

| Message | Source |
|---|---|
| "The @x1 is @x2." (for example "The door is closed.") | `StdExit.java:496` |
| "You can't go that way." | `StdExit.java:494`; `MUDTracker.java:1674` |
| "You can't fly." | `StdExit.java:503` |
| "You need to climb that way, if you know how." | `StdExit.java:519` |
| "The @x1 is locked." | `StdExit.java:559` |
| "There is nothing to @x1 that way!" | `StdExit.java:549` |
| "The @x1 is already @x2!" | `StdExit.java:554`, `:598` |
| "The @x1 is not locked." | `StdExit.java:611` |
| "There is no lock!" | `StdExit.java:604` |
| "You don't seem to have the key." | `StdExit.java:638` |
| "It doesn't appear to be doing any good." | `StdExit.java:772` |
| "You can't do that to <T-NAME>." | `StdExit.java:649` |
| "There is nothing written here." | `StdRoom.java:955` |
| "You can't see that!" | `StdRoom.java:957` |
| "You can't shop here." | `StdRoom.java:809` |
| "You can't really dig here." | `StdRoom.java:818`, `:838` |
| "You can't do that here." | `StdRoom.java:849` |
| "Your corpse has been moved to @x1" | `StdRoom.java:1114` |
| "Some great evil is preventing your movement that way." | `StdGrid.java:1217`; `StdThinGrid.java:1071` |
| "The area to the @x1 shimmers and becomes transparent." | `MUDTracker.java:1750` |
| "You are too tired." | `MUDTracker.java:1790` |

## Notable design ideas

- **Rooms route, they do not own.** A room is a dispatcher. It hands each `CMMsg` to every child that may care, and each child answers yes or no in phase one and applies the change in phase two. The same pattern covers a door, a sword, a mob and a fire spell, so new kinds of object need no changes to the room (`StdRoom.java:745-1196`).
- **Refusal carries its own explanation.** The veto is paired with a line of text for the player: most refusals call `mob.tell` with a specific message and return `false` (`StdExit.java:488-651`). The reason and the rule are the same code.
- **The grid is a proxy.** An outside room sees one object, the grid. The grid picks the real child from the direction of entry, so a whole terrain patch can be entered from any side with the right edge cell (`StdGrid.java:265-316`, `:1198-1232`). When two grids of equal size meet, rows and columns line up across the boundary (`:281-291`).
- **Two grid implementations behind one contract.** The fat grid is simple and eager. The thin grid creates a neighbour ring as the player moves, and gives each child an expiry timestamp that removes it from the list later (`StdThinGrid.java:307-313`, `:298-304`, `:369`, `:1012-1046`). Which one a world gets is a runtime switch: when the `FATGRIDS` security flag is disabled, `StdGrid.newInstance` swaps in the thin class (`StdGrid.java:95-106`).
- **Descriptions vary by split and by index.** A grid stores its description and display text as lists split on `<P>` (`StdGrid.java:204-240`). Each child takes one random index, and the display and description use the same index when the lists have the same length (`:1172-1185`). That gives a patch of forest that reads differently from cell to cell with two small arrays.
- **A maze is a grid with a carve.** `StdMaze` reuses the grid's cell creation and linking and adds only a randomised depth-first carve (`StdMaze.java:130-185`). It also links its centre cell to the outside world when a door points at it (`:61-78`).
- **Lazy exits by touch.** Asking a grid child for an exit forces it to create its exits (`StdRoom.java:1679-1686`), and the thin grid builds the ring when a child is first used (`StdThinGrid.java:307-313`). Nothing is built until someone looks.
- **Passage memory.** An exit remembers the last room it was used toward (`StdExit.java:489-490`). Abilities such as Farsight use that memory (`Abilities/Druid/Chant_Farsight.java:138`).
- **Reference counts on exits.** Each room slot that points at an exit adds one to its count (`StdRoom.java:403-406`). It is a cheap sharing model, and the exit's own tick uses the count to decide when to destroy it (see problem 3).
- **Description tags are data.** A room text can say `<RAIN>`, `<FROMNORTH>`, `<VISITED>` or `<SWIMMING>`, and one string covers every season, weather, state and entry direction (`Room.java:242-308`).

## Problems and cruft

Each item says what was verified by reading and what was not run.

1. **Temporary-link prefix leaks into the key and the readable text.** `StdExit.keyName()` returns the raw text field (`StdExit.java:911-914`), and `GenExit.keyName()` returns its raw `keyName` field (`GenExit.java:241-244`). A pending temporary link is stored as a `{#id#}` prefix in that same string (`StdExit.java:965-972`, `GenExit.java:284-291`). The key test at `StdExit.java:627` compares against `keyName()`, so a lockable exit with a pending link cannot be unlocked with its own key. `GenExit.readableText()` (`:225`) would show the prefix too. Links are cleared when the room is found (`Libraries/CoffeeMaker.java:1436-1438`), so the impact depends on how long a link stays pending **[unverified in practice]**.
2. **One field for two meanings.** `GenExit` stores the key name and the readable text in the same field, `keyName` (`GenExit.java:225`, `:237`, `:249`). Setting the readable text overwrites the key, so one exit cannot be both readable and lockable with separate text.
3. **Unreferenced exits are removed only by chance.** The destroy call in `setRawExit` is commented out with "cant be done" (`StdRoom.java:412-417`). An exit whose count reaches zero is removed only when it ticks (`StdExit.java:796-799`), and an exit with no behaviour, script, effect or reopen timer never ticks. So reclamation depends on a timer that may never fire. A Python port gets this for free from garbage collection, but the reference is worth knowing about if exits are ever pooled.
4. **A room that has had an effect keeps ticking.** On the effect path the room returns `!amDestroyed()` (`StdRoom.java:1273-1274`), so it returns `true` for as long as it lives. The contract says a tick returns `false` to stop (`core/interfaces/Tickable.java:70`). Whether the scheduler removes such rooms elsewhere was not checked **[unverified]**.
5. **Last-room memory ignores its argument and is written too early.** `lastRoomUsedFrom(fromRoom)` ignores `fromRoom` (`StdExit.java:922-925`). The value is written before the door check (`:488-490`), so an entry attempt that is refused still changes what callers see. Callers include `Abilities/Druid/Chant_Farsight.java:138`.
6. **Every message is broadcast to every exit's rules.** `StdRoom` offers each message to every exit, in both phases (`StdRoom.java:893-901` and `:1005-1010`). `StdExit` runs its behaviours, scripts and effects before checking whether it is the target (`StdExit.java:451-469`, `:702-734`), with the filter only at `:472` and `:737`. A room with many exits and busy scripts does that work for every message.
7. **Fat and thin grids disagree on DOWN.** `StdThinGrid` sends DOWN to the bottom centre (`StdThinGrid.java:716-718`). `StdGrid` sends DOWN to the centre, because `initCenterRoomXY` has no case for DOWN and falls through to the default (`StdGrid.java:620-656`, default at `:651-654`).
8. **The thin-grid neighbour comment admits a gap.** The comment in `fillExitsOfGridRoom` says the neighbours created there "should also take into account the possibility that they are on the edge. it does NOT" (`StdThinGrid.java:422-423`).
9. **Maze sense flag is a no-op as written.** `StdMaze.getGridRoom` only copies `SENSE_ROOMUNEXPLORABLE` onto a child that already has it (`StdMaze.java:52-57`). Nothing in this bucket sets the flag, so the override does nothing unless another system sets it first **[unverified where]**.
10. **Dead null tests.** `getArea()` never returns null (`StdRoom.java:510-515`), yet `StdRoom.java:1122`, `:1943-1944` and `StdThinGrid.java:1018` test for null.
11. **Unchecked start room on corpse move.** `StdRoom.java:1113` gets a room from `CMLib.map().getRoom` and uses `startRoom.displayText()` at `:1114` without a null test. Whether `getRoom` can return null here was not checked **[unverified]**.
12. **Smaller cruft.** `StdThinGrid.tickStarted` (`:54`) is declared and never used. `StdRoom.setResource` assigns `lastResourceTime` twice in one expression (`:688`). `StdThinGrid.destroy` replaces its lists with new empty ones (`:151-153`), while `StdGrid.destroy` sets them to null (`:127-129`).
13. **Static caches and ordering.** `StdRoom.getStatCodes` caches one array in a static field (`StdRoom.java:3244`, `:3249-3250`), and `getCodeNum` reads that static directly (`:3260-3263`). If it has not been filled yet, the lookup returns `-1` (`core/CMParms.java:4237-4240` returns `-1` for a null list) and the code falls through to the extension branch (`StdRoom.java:3291`). The effect depends on the order in which stats are first read **[unverified in practice]**.
14. **Constructor order in `GenExit`.** `StdExit`'s constructor calls `defaultsClosed()` and `defaultsLocked()` (`StdExit.java:68-69`) before `GenExit`'s field initialisers run (`GenExit.java:48-50`), so those calls see `false`. The `GenExit` constructor body then sets the right values (`GenExit.java:74-77`). The final state is correct, but the `StdExit` lines are misleading for `GenExit`.
15. **Mobs of negative height walk through shut doors.** The closed-door test applies only when `mob.phyStats().height()>=0` (`StdExit.java:491`). What gives a mob negative height was not checked.
16. **Doors close without a word.** The reopen handler changes the door's state with no message (`StdExit.java:802-811`) and does not check for an occupant in the doorway.
17. **Grids start with a non-empty address.** Both grid constructors set `_roomID` to the class's simple name (`StdGrid.java:57`, `StdThinGrid.java:59`). An unaddressed grid is therefore treated as addressed by `distributeStuff` (`StdGrid.java:948`) and `isSavable` (`StdRoom.java:1942`) until the loader overwrites the ID.
18. **Maze carving recurses once per cell.** `mazify` recurses at `StdMaze.java:174`, so the stack depth grows with the number of cells **[unverified risk for very large mazes]**.
19. **The grid's ENTER redirect looks unreachable in its success case.** `StdGrid.okMessage` and `StdThinGrid.okMessage` look for a direction where `mob.location().getRoomInDir(d)==this` (`StdGrid.java:1212`, `StdThinGrid.java:1066`). By my reading `getRoomInDir` never returns the grid itself: it passes the grid through `prepareRoomInDir`, which returns a child cell or `null` (`StdGrid.java:195`, `StdThinGrid.java:204`), and it returns `null` for a destroyed room (`StdRoom.java:1672`). So the loop finds no direction, and the branch refuses with "Some great evil is preventing your movement that way." whenever an ENTER aimed at the grid object reaches it **[reasoned from code; not run]**.
20. **A destroyed room can stay off the ticker.** `StdRoom.tick` destroys a room whose area is destroyed (`:1228-1232`), but `destroy` returns at once when `SENSE_UNDESTROYABLE` is set (`:1958-1959`). The ticker is then stopped while the room is still live **[edge case]**.

## Takeaways for a Python SUD

These are suggestions for a single-player game. Nothing here is CoffeeMud code except where it is named.

- **Keep the model small.** A `Room` holds an id, a name, description variants, a domain code, a climate, `exits: dict[str, Exit]`, `items`, `npcs` and `resources`. An `Exit` holds the target room id, the door and lock flags, a key id, the door words, and the closed text. Use one `Exit` object for a two-way passage when the door state must be shared (the thin grid does this for internal links, `StdThinGrid.java:874-876`). Use two objects when the sides can differ, which is how `StdRoom` works.
- **Use the veto pattern for actions, without the broadcast.** Replace the two-phase bus with `check(action) -> reason | None` and `apply(action)`. Check the room, then the actor, then the items that are involved, then the exit. Let only the parties named in the action see it, and give the exits no broadcast, since problem 6 is the cost of broadcasting. Keep the refusal text next to the rule, as `StdExit` does.
- **Doors as a small state machine.** The shape to copy is this sketch (not CoffeeMud code):

```python
@dataclass
class Exit:
    target: str
    has_door: bool = False
    has_lock: bool = False
    default_closed: bool = False
    default_locked: bool = False
    key_id: str = ""
    is_open: bool = True
    is_locked: bool = False
    reclose_in: int | None = None   # turns until the door shuts itself

    def open(self) -> str:
        if not self.has_door: return "There is nothing to open that way!"
        if self.is_open:      return "The door is already opened!"
        if self.is_locked:    return "The door is locked."
        self.is_open = True
        if self.default_closed or self.default_locked:
            self.reclose_in = 45
        return "You open the door."

    def tick(self) -> None:
        if self.reclose_in is not None:
            self.reclose_in -= 1
            if self.reclose_in <= 0:
                self.reclose_in = None
                self.is_open = False
                self.is_locked = self.default_locked
```

  The 45 is CoffeeMud's `openDelayTicks()` value. If the game is turn-based, read it as 45 turns. If it is real-time, 45 ticks at 4 seconds is 3 minutes, which is what CoffeeMud gives.
- **Grids as lazy dictionaries.** Build a `Grid(width, height, make_cell)` whose cells are created on first access and cached in a dict keyed by `(x, y)`. That is the thin-grid model without the sorted list. Keep the edge rule: an entry from a neighbouring grid of the same height lands on the matching edge cell, a corner maps to a corner, and anything else maps to the middle of the edge (`StdGrid.java:265-316`). Decide the DOWN case explicitly, since the two CoffeeMud grids disagree (problem 7).
- **Mazes with an explicit stack.** Carve with an iterative depth-first search from the centre cell, keeping the stack in a list. This avoids the per-cell recursion in problem 18.
- **Descriptions with variants.** Keep the `<P>` split for alternative descriptions per cell, and the variation tags as conditions the renderer checks (season, time of day, weather, visited, entry direction). Those tags give a single text a lot of range for little data (`Room.java:242-308`).
- **Keep these rules.** Keys, including keys inside a key-holding container (`StdExit.java:628-630`). Climate and atmosphere resolved from the nearest parent (`StdRoom.java:188-209`). Movement cost per room, adjusted by climate (`:2860-2863`). The thirst table (`:2871-2886`). The resource roll that favours neighbours (`:700-738`). Flying and climbing gates on exits (`StdExit.java:499-522`).
- **Drop these.** The usage counter and sweep (use Python's own memory management), the thin-room database loading and expiry (the world fits in memory), session and sysop checks, the shutdown saves, the `SENSE_ROOMSYNC` re-entrancy guard, the `SVector` copy-on-write lists (plain lists are safe in a single thread), and the tick registration API (a single loop calls `tick()` on the active objects).
- **Single-player changes.** Occupants are the player and NPCs. `numPCInhabitants` and the session tests disappear. The follower logic in `bringMobHere` (`StdRoom.java:1431-1441`) is worth keeping as a companion rule. The room's `TYP_DIG` and shop checks are better written as explicit actions than as message codes.

## Coverage

- **Read in full (11 of 11 files):** `Locales/StdRoom.java` (3363 lines), `Exits/StdExit.java` (1422), `Locales/StdGrid.java` (1284), `Locales/StdThinGrid.java` (1139), `Locales/interfaces/Room.java` (1003), `Exits/GenExit.java` (486), `Locales/StdMaze.java` (212), `Locales/interfaces/GridLocale.java` (183), `Exits/interfaces/Exit.java` (133), `Locales/interfaces/LocationRoom.java` (69) and `Exits/interfaces/PrepositionExit.java` (67).
- **Read in part, for interactions:** `Locales/ThinRoom.java` (lines 1-140, 215-290, 353-470, 1209-1224); `Libraries/MUDTracker.java` (the `move` pipeline at 1665-1850 and the NPC door handling at 1036-1090 and 1150-1180 inside `beMobile`, which starts at 910; the rest of `beMobile` was not read); `Abilities/StdAbility.java` (685-735); `Commands/Open.java` (50-66); `Libraries/CoffeeMaker.java` (1425-1445); `Libraries/CMMap.java` (1267-1279 and 1540-1552); `core/Directions.java` (constants, `reInitialize`, `getOpDirectionCode`); `core/CMProps.java` (681, 2720, 3241); `core/CMParms.java` (4237-4242); `core/collections/SVector.java` (36-42, 302-314, 437-440); `core/interfaces/Tickable.java` (70-104); `Libraries/interfaces/ThreadEngine.java` (52-75); `Libraries/interfaces/WorldMap.java` (48); `Libraries/interfaces/TimeManager.java` (438-440).
- **Exit subclasses:** the four base doorway classes (`StdOpenDoorway`, `StdClosedDoorway`, `StdLockedDoorway`, `Open`) were read in full. The door and lock overrides of `AjarDoor`, `GenAirLock`, `LockedGate` and the other subclasses were checked by grep only, and the rest are listed by name.
- **Skipped:** the terrain room classes in `Locales/` (other buckets), `Items` for `DoorKey` and `Container.CONTAIN_KEYS`, `CMLib.commands().parseVaries`, `CMLib.map()` internals beyond the lines cited, `CMLib.tracking().findALadder`, `CMLib.flags()` and the rest of `Libraries/MUDTracker.java`.
