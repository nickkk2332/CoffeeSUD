# World map, room sets and area statistics

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b023` (World map, room sets and area statistics) · 9 files · 6721 lines of Java.

## Summary

CMMap is the world registry. It holds every area, indexes ships, deities, script hosts and tagged objects, resolves names to areas and rooms, and runs a periodic maintenance sweep that expires temporary rooms and items, sends stuck mobs home, and updates land titles. Room membership lives in a RoomnumberSet, which keeps numbers per area as compressed ranges and packs grid coordinates into one long. DefaultAreaIStats derives 31 statistics for an area (population, median and mode level, alignment spread, boss and shopkeeper counts, room counts by terrain), building them in chunks for thin areas whose rooms load on demand. CityCafe caches city service NPCs (banks, post offices, auction houses, craft brokers, libraries) and the holy places of each deity. For a single-player SUD the reusable parts are the registry and lookup logic, expiry of temporarily loaded rooms, and the area summary statistics; the packed numbering and most of the multiplayer maintenance are not worth copying.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Libraries/CMMap.java` (class `CMMap`, implements `WorldMap`) | World registry, area and room lookup, room and area reset, expiry sweep, object registries, virtual-file view of the map | 3073 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/WorldMap.java` | Public contract for `CMLib.map()` (`CMLib.java:680`) | 1048 |
| `com/planet_ink/coffee_mud/Common/DefaultRoomnumberSet.java` | RoomnumberSet implementation: sorted room numbers per area, `null` meaning the whole area | 545 |
| `com/planet_ink/coffee_mud/Common/OrderedRoomnumberSet.java` | Behaviourally identical copy of DefaultRoomnumberSet under another ID (see Problems) | 545 |
| `com/planet_ink/coffee_mud/Common/DefaultAreaIStats.java` | Builds the 31 area statistics, synchronously or in scheduled chunks | 512 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/CityMap.java` | Contract for `CMLib.city()` (`CMLib.java:570`) | 386 |
| `com/planet_ink/coffee_mud/Libraries/CityCafe.java` | CityMap implementation: service NPC caches and holy places | 382 |
| `com/planet_ink/coffee_mud/Common/interfaces/RoomnumberSet.java` | Interface; class comment on compressed room-ID storage at lines 34-44 | 151 |
| `com/planet_ink/coffee_mud/Common/interfaces/AreaIStats.java` | Interface for area statistics | 79 |

Outside the bucket, read only at the cited lines: `com/planet_ink/coffee_mud/Areas/interfaces/Area.java:764-797` (the `Area.Stats` enum), `com/planet_ink/coffee_mud/core/collections/LongSet.java:31-39` (range constants), `com/planet_ink/coffee_mud/Libraries/Dice.java:398-406` (`roll`), `com/planet_ink/coffee_mud/core/collections/PrioritizingLimitedMap.java:84` (constructor), `com/planet_ink/coffee_mud/core/collections/SLinkedList.java:23-26` (backing list), `com/planet_ink/coffee_mud/core/interfaces/MudHost.java:46` (save-thread period).

## How it works

Paths below are relative to the CoffeeMUD folder, as in the table above.

### 1. Registries and name lookup (CMMap)

Data model (`com/planet_ink/coffee_mud/Libraries/CMMap.java`):
- `areasList` (line 60) is a sorted vector of every `Area`. Space ships are areas too; `numRooms()` (552-559) sums proper rooms over `areas()`, and `areasPlusShips()` (343-349) adds ship interiors.
- `deitiesList` (61), `shipList` (62), `globalHandlers` (80), `scriptHostMap` (81), `taggedMap` (82) and `clockCache` (64) are registries. They are filled by `registerWorldObjectLoaded` (2132-2178) and emptied by `registerWorldObjectDestroyed` (2099-2130).

Area lookup, `getArea` (267-287) and `findArea` (313-335):
1. Check the cache `SYSTEM_AREA_FINDER_CACHE`, a `PrioritizingLimitedMap` built with `(50, 30 min, 1 h, 100)` (255-265; constructor at `core/collections/PrioritizingLimitedMap.java:84`). The cache is cleared on `renamedArea` (196-202) and `delArea` (226).
2. Otherwise `areasList.find(name)` (279).
3. `MAPFINDSNOCACHE` disables cache writes (282, 292, 305, 329).

`findArea` tries `findAreaStartsWith` (289-311) first, then the cache, then `CMLib.english().containsString` against every area name (327).

Quirk: the prefix loop at line 303 compares `A.Name().toUpperCase().startsWith(calledThis)` without upper-casing `calledThis`, so that fallback is case-sensitive. The `getArea` step before it is not affected.

Room lookup, `getRoom` (725-855), `getCachedRoom` (857-861) and `getRoomAllHosts` (863-880):
- An ID ending in `)` is first resolved through its grid parent (733-746).
- Otherwise the area named before `#` is tried, then every area, then ship areas (750-816). Each step calls `Area.getRoom`, which may load the room (not traced).
- `getCachedRoom` passes `cachedOnly`, so it checks `getProperRoomnumbers().contains` and `isRoomCached` and never loads (756-764).
- `getRoom(Room)` re-resolves a destroyed room by its extended ID (846-847).
- `getRoomAllHosts` falls back to other WorldMap libraries (869-878).

Random choice. `getRandomRoom` (912-941) draws one index with `CMLib.dice().roll(1,numRooms,-1)` (921) and walks the areas by `properSize()`, so every room is equally likely. `getRandomArea` (415-430) is uniform over areas. `Dice.roll(n,d,m)` is n dice of d sides plus m (`Libraries/Dice.java:398-406`), so `roll(1,n,-1)` gives 0 to n-1.

### 2. Room IDs and room-number sets

ID forms, all strings:
- Proper room: `AREA#N`, for example `Midgaard#3001` (`RoomnumberSet.java:34-36`).
- Grid child: `AREA#FIRST#(SECOND,THIRD)` (`DefaultRoomnumberSet.java:167-181`).
- Unknown room: `AREA#?` (`CMMap.java:658`, `:679`).
- Room in a grid with no ID of its own: `<compass direction> <neighbour ID>` (`CMMap.java:631-659`, line 649).

Data model (`DefaultRoomnumberSet.java:52`): `root` is a `STreeMap<String,LongSet>` keyed by upper-case area name. Each value is either:
- a `LongSet` of numbers, which stores runs as start and end pairs (`core/collections/LongSet.java:162-245`; the interface comment at `RoomnumberSet.java:34-44` gives the example that 5-10 is stored as 5 and 10), or
- `null`, meaning every room of the area. `roomCountAllAreas()` counts such an entry as 1 (`DefaultRoomnumberSet.java:200-201`), and `random()` returns the bare area name for it (264-268).

Packing of grid coordinates (`add` 442-446, `remove` 175-179): `roomNum = FIRST<<30 + SECOND<<15 + THIRD`. If the result is below `INT_BITS`, bit `OPTION_FLAG_LONG` (2^61) is set so it cannot be mistaken for a plain number. Constants (`LongSet.java:31-39`): `NEXT_FLAG` = 2^30, `INT_BITS` = 2^30-1, `LONG_BITS` = 2^62-1, `OPTION_FLAG_LONG` = 2^61. `convertRoomID` (273-311) decodes: 30 bits for the first field, 15 each for the other two.

Operations:
- `add(String)` (420-473) parses an ID and creates the area entry. A bare non-numbered ID other than the literal `START` is logged as a debug message (453-458).
- `remove(String)` (138-192).
- `contains(String)` (330-374): `AREA#N` tests membership; a bare `AREA` is true whenever the area key exists, even if it has specific numbers (369-371).
- `getRoomIDs()` (475-538) enumerates every ID, area by area.
- `xml()` (376-388) and `parseXML()` (390-418) serialize as `<AREAS><AREA><ID>..</ID><NUMS>..</NUMS></AREA>...`.
- `roomCount(area)` (223-237) ignores any `#` suffix and returns 0 for a whole-area entry.

Use: each `Area` holds its proper and cached room IDs in these sets (`CMMap.java:384`, `:705`, `:758`). `findRoomIDArea` (694-723) uses `contains` to find which area owns an ID.

### 3. Area statistics (DefaultAreaIStats)

Data model: `statData` is an `int[31]` indexed by the `Area.Stats` ordinal (`DefaultAreaIStats.java:39`; enum at `Areas/interfaces/Area.java:764-797`). A temporary `AISBuild` (49-57) holds the alignment faction, the lists `levelRanges` and `alignRanges`, the alignment total, a race-to-count map, and the `done` set of room IDs already counted. `state` moves NOT_STARTED, RUNNING, FINISHED (42-47, set at 398). `isFinished()` (103-107) reads that volatile field. Callers test it, for example `Areas/StdArea.java:1777`, `:1818`, `:2424` and `Areas/StdBoardableShip.java:1472`, `:1477`, `:2336`.

Control flow, `build(Area)` (403-511):
1. Return at once if the build has already started (405-407).
2. Choose the alignment faction: the last faction flagged `showInSpecialReported` (409-414).
3. Reset the counters (418-427). VISITABLE_ROOMS is set to the proper-room count (428). The `PIETY_` cache entry is removed (429).
4. Non-thin area: visit each proper room with `buildAreaIRoomStats` (430-434), then `finishBuildingStats` (509).
5. Thin area with fewer cached rooms than proper rooms (436-437): a runnable is scheduled (439-507, first run after 1 ms, line 506). Each run takes the next room ID. If it is already in `done` it reschedules after 1 ms (460-461). Otherwise it loads the room without contents (465), loads the contents (482), counts the room and its mobs and items (484-488), and destroys the room (497). An instance-child ID of the form `N_Name` falls back to `Name` (465-475). After each room the runnable reschedules itself after `10 + elapsed` ms (500). It returns without finishing if the host is shutting down or the area is destroyed (448-450), so `state` stays RUNNING.
6. `build()` returns before thin-area statistics are ready (506-510).

Per room, `buildAreaIRoomStats` (240-295) adds to the room-type counts (below), calls the mob and item visitors, and adds the room ID to `done` (288-294). A boardable item that is not owned property (ship, castle) contributes the monsters of its own interior area (207-223).

Per mob, `buildAreaIMobStats` (127-199): only monsters count (`isMonster`, 129), and unattackable ones are skipped (131). Each counted mob adds to POPULATION, TOTAL_LEVELS, the level minimum and maximum, and its race tally, and is then classified (table below).

Finish, `finishBuildingStats` (297-400):
- Levels are sorted. MED_LEVEL = `levels[floor(n/2)]`, the upper median for even n (314). AVG_LEVEL = round(TOTAL_LEVELS / POPULATION) (321).
- AVG_ALIGNMENT = round(totalAlignments / POPULATION) (322).
- MODE_LEVEL and MODE_ALIGNMENT come from a run-length scan of the sorted lists (323-351, 354-381).
- The common race is the race with the highest count; the first one found wins ties (384-394). Animals count toward it.
- The area's base and current level are set to MED_LEVEL (395-396).
- The `HELP_<AREA>` cache entry is removed (397).

Statistics catalogue (`DefaultAreaIStats.java` line numbers):

| Stat (`Area.Stats`) | How it is computed | Lines |
|---|---|---|
| POPULATION | count of attackable monsters; players and unattackable mobs are skipped | 129-131, 147 |
| TOTAL_LEVELS | sum of base levels | 148 |
| AVG_LEVEL | round(TOTAL_LEVELS / POPULATION) | 321 |
| MIN_LEVEL, MAX_LEVEL | lowest and highest base level; both 0 when POPULATION is 0 | 163-171, 307-308 |
| MAX_LEVEL_MOBS | number of mobs at the maximum level | 165-173 |
| MED_LEVEL | sorted levels at `floor(n/2)` | 314 |
| MODE_LEVEL | most frequent level (run-length scan) | 323-351 |
| TOTAL_INTELLIGENT_LEVELS, INTELLIGENT_MOBS, ANIMALS | split by `CMLib.flags().isAnimalIntelligence` | 150-159 |
| AVG_ALIGNMENT, MED_ALIGNMENT, MIN_ALIGNMENT, MAX_ALIGNMENT, MODE_ALIGNMENT | faction value of mobs that have one | 141-146, 315-319, 322, 354-381 |
| GOOD_MOBS, EVIL_MOBS | `isGood` / `isEvil`, only if the good axis is loaded | 174-181 |
| LAWFUL_MOBS, CHAOTIC_MOBS | `isLawful` / `isChaotic`, only if the law axis is loaded | 182-188 |
| BOSS_MOBS | mobs carrying the `Prop_ShortEffects` effect | 189-190 |
| AGGRO_MOBS | `CMLib.flags().isAggressiveTo(mob, null)` | 191-192 |
| SHOPKEEPER_MOBS | `instanceof ShopKeeper` | 193-194 |
| HUMANOIDS | racial category is Humanoid, Elf, Dwarf, Halfling or HalfElf | 195-196 |
| VISITABLE_ROOMS | proper room IDs, minus one per grid parent | 428, 245 |
| COUNTABLE_ROOMS | rooms counted, with a grid counted as its cell count | 242-250 |
| INDOOR_ROOMS | rooms with the INDOORS domain bit | 251-253 |
| CAVE_ROOMS | indoor cave domain | 256-257 |
| CITY_ROOMS | indoor metal, stone or wood; outdoor city | 259-262, 274-275 |
| WATER_ROOMS | indoor underwater or water surface; outdoor underwater or water surface | 264-266, 280-282 |
| DESERT_ROOMS | outdoor desert | 277-278 |

Outdoor domains other than city, desert and water are not counted in any statistic.

Timing: stats are built in the calling thread for ordinary areas. Thin areas are built by the scheduler, one room at a time, with a gap of 10 ms plus the time the room took to load (500).

Messages: no text reaches a player. The only string is a debug log, `Unknown roomID building '<area>' istats: <id>` (477).

### 4. Room and area reset, and expiry

Room reset, `resetRoom` (1930-1981):
1. Lock on `SYNC<roomID>` (1943) and re-resolve the room (1945).
2. Clear the grid if asked (1946-1947). Stop if the grid parent has been destroyed (1948-1952).
3. Set `SENSE_ROOMSYNC` in the base senses mask (1955). Send `MSG_ROOMRESET` through `executeMsg` with the shared `resetMsg` (1956-1959).
4. Empty the room of mobs and items if it is savable (1961-1962; `emptyRoom` at 1747-1852).
5. Uninvoke effects that allow it (1962-1967).
6. For savable rooms, reread the room data and contents from the database (1968-1972).
7. Restart item rejuvenation and set the room resource to -1 (1973-1974). Clear the sense flag in `finally` (1976-1979).

Area reset, `resetArea` (2012-2087):
1. Set state FROZEN (2016).
2. An `AutoGenArea` delegates to `resetInstance`, with a return room chosen from a neighbour, and returns (2017-2038).
3. Otherwise collect the players in the area, including their followers, through `getAllPlayersHere` (2040; 1984-2010, which uses `CMLib.sessions().localOnlineIterable()` at 1989). Remove them from their rooms (2042-2047) and remove owned ships (2048-2061).
4. Reset every proper room (2062-2063), then `fillInAreaRooms` (2064).
5. Put players back at their remembered extended room ID, or the start room (2065-2075). Re-place owned property at its remembered room or a safe room (2076-2084).
6. Reread the area data (2085) and restore the saved state (2086).

Expiry, `roomMaintSweep` (2609-2841):
- A thin room is given an expiry date of now plus `ROOM_EXPIRATION_MILLIS` = 2,500,000 ms, about 41.7 minutes (`WorldMap.java:48`). Users include `Areas/StdThinArea.java:56`, `Locales/ThinRoom.java:257` and `Areas/PlayerInstanceArea.java:174` (from grep).
- A room past its expiry date gets `MSG_EXPIRE` if its `okMessage` agrees (2627-2630, 2674-2689). Items owned by the room are expired the same way (2635-2644), corpses only when the ROOMITEMS save flag is set (2611, 2639). Mobs are expired unless ROOMMOBS is set (2612, 2645-2655). Removal itself is left to the listeners, not traced.
- Land titles are updated (2694-2738). Script hosts with no remaining script are dropped (2740-2745).
- Stuck mobs: a mob whose last tick is more than 5 minutes old (2747-2749, 2764-2765) and that is not ticking now is handled. Followers are removed from the room (2783-2792). A non-player with no start room is destroyed (2794-2800). Other non-players are sent home with `bringToLife` (2801-2807). Players are removed, and re-lifed if their session is live (2809-2822).

Timing: `tick` (2461-2489) runs the sweep only when the host is RUNNING and the save thread is enabled (2464-2468), then `saveTimeClocks` (2477), then `Resources.savePropResources` (2480). The tick is scheduled with `MudHost.TIME_SAVETHREAD_SLEEP` (`CMMap.java:2439`), which the constant's comment gives as 60 minutes (`core/interfaces/MudHost.java:46`).

Messages: `MSG_ROOMRESET` and `MSG_EXPIRE` are message codes with no text. Log lines name the outcome for stuck mobs, for example "This mob has been put aside.", "This mob has been re-lifed." and "Sent home." (2823-2825, 2812, 2822).

### 5. Creating, linking and deleting rooms

- `createNewExit` (506-550) links two rooms in both directions. It returns "Bad direction" (510, 512) or "Opposite room already exists and heads this way." (521), otherwise an empty string. Both rooms receive the same `Exit` object (531-545). The reverse link is set only if the far side is empty (542-546), so an occupied far side leaves a one-way exit, and the call still returns an empty string (549). Both rooms are saved with `DBUpdateExits` (537, 546).
- `obliterateRoom` (1440-1584) removes one room. It first finds rooms that point at it: from the database's incoming-exit map when the room has an ID (1459-1474), or by checking neighbours (1478-1493). It then scans every cached room for links into it (1500-1531) and clears those doors (1532-1549). It removes grid cross-exits that point at it (1551-1561), empties and destroys the room (1571-1572), deletes it from the database (1577), and drops the area's `HELP_` and `STATS_` cache entries (1580-1581).
- `obliterateArea` (1878-1926) empties every room twice (1886-1898), obliterates each room that has a link into the area (1901-1921), removes the area (1922), and drops its `HELP_` and `STATS_` entries (1923-1924).
- `getSafeRoomToMovePropertyTo` (1693-1745) picks where to put property that has to leave a room. It tries a boardable's home port, the grid parent, a neighbour, the parent's neighbours, a random room of the area, then up to 100 random world rooms. It returns null if none qualifies (1744).

### 6. Other registries and the message protocol

- Object registration: `registerWorldObjectLoaded` (2132-2178) sends deities to `deitiesList`, boardables to `shipList` (`addShip`, 996-1005), post offices, banks, libraries and auction houses to `CityCafe` (2141-2151), and any object with a savable script to `scriptHostMap` under its area name (2153-2176; script test at 2199-2216). `registerWorldObjectDestroyed` (2099-2130) reverses this.
- Global messages (`addGlobalHandler` 432-452, `sendGlobalMessage` 561-613): listeners are held as weak references under an integer category. `sendGlobalMessage` first offers the message to each live listener with `okMessage` and stops at the first refusal (576-593), then runs `executeMsg` on all of them (597-601). This is the two-phase protocol the brief describes for CMMsg.
- Tags (2343-2429): tag names are case-insensitive keys, and each tag holds a weak list of objects. Empty tags are kept (2391-2392).
- Deity stand-in (470-489): with no deity object, a `StdMOB` named "god" is created and placed in a random room (483-486). `getFactoryMOB` (497-504) creates a temporary mob named "somebody" to act as a message source (501).

### 7. Virtual file view of the world (getMapRoot 2915-3071)

`getMapRoot` exposes the world as a virtual directory `map/` (2918). Each area has an `.cmare` XML file (2927-2932) and a directory holding one `.cmare` file per room (2949-2954). Each room directory has `items/` and `mobs/` subdirectories (2963-3015). Every entity has a `stats/` directory (`addMapStatFiles`, 2851-2913) with one file per stat code. Read access follows the CMSecurity area, room, mob or item flags (2871-2882). A write calls `setStat` and saves the record to the database (2894-2905). Contents are generated on each browse in `getFiles()`.

### 8. City cache (CityCafe)

- Lists per service type (46-51): `SVector`s of PostOffice, Auctioneer, CraftBroker, Banker and Librarian. `CMMap` adds and removes them (2141-2151, 2108-2118).
- Lookup takes a chain and an area or branch. Each getter first matches the chain and branch name, then resolves the area with `CMLib.map().findArea` and matches by start area: `getPostOffice` (71-93), `getAuctionHouse` (127-149), `getCraftBroker` (177-199), `getBank` (219-240), `getLibrary` (335-356). A post-office lookup with `*` matches any branch (74-75).
- Chains: `bankChains` and `libraryChains` return distinct chain names, optionally filtered to an area and its child areas (249-264, 364-380).
- Holy places (266-314): for each upper-case deity name, a `TreeSet<Places>` ordered by `Places.placeComparator`. Destroyed places are pruned when the list is read (302-312).

Registration: `CMLib.registerLibrary(new CityCafe())` at `application/VFShell.java:1132`.

### 9. Interactions

- Areas and the rest of the game call `CMLib.map()` for lookups and `CMLib.city()` for service NPCs (`CMLib.java:680`, `:570`).
- Thin-area classes depend on `ROOM_EXPIRATION_MILLIS` (found by grep in `Abilities/Druid/Chant_SummonCoral.java:159`, `Areas/StdThinGridArea.java:56`, and others). Callers store area statistics under `STATS_<AREA>` in `Resources`: `Areas/StdThinInstance.java:693-707`, `Abilities/Misc/InstanceArea.java:1941`, `Abilities/StdPlanarAbility.java:744`, `Areas/SubThinInstance.java:94`. `Areas/interfaces/Area.java:497` declares `getAreaStats()`.
- `DefaultRoomnumberSet` uses `CMLib.dice()` (245), the XML library (393, 396) and `Log` (456-457).
- Database calls used but not traced: `DBReadRoomObject`, `DBReadContent`, `DBUpdateExits`, `DBReadIncomingRoomExitIDsMap`, `DBDeleteRoom`, `DBDeleteAreaAndRooms`, `DBReCreate`, `DBReadAreaData`.

Messages shown to players: none in these nine files. Builder-facing replies are the strings in section 5.

## Content worth mining

- The 31-statistic catalogue above, as a summary of an area: population, median and mode level, mob composition, and terrain mix. For the SUD this suits a one-line area description, such as the number of hostile mobs and the median level.
- Room-address grammar: `AREA#N` for a room and `AREA#FIRST#(X,Y)` for a grid cell (`DefaultRoomnumberSet.java:167-181`).
- Terrain buckets for counts (section 3 table). The bucket names are the `Room.DOMAIN_*` constants.
- Thin-room lifetime of 2,500,000 ms (`WorldMap.java:48`).
- Fallback names: "god" and "somebody" (`CMMap.java:484`, `:501`), `<direction> <room ID>` for a grid room with no ID (`CMMap.java:649`), and `Area#?` (`CMMap.java:658`).
- No prose or data files are in this bucket. The manifest lists only Java.

## Notable design ideas

- Room membership as sorted runs per area, plus a `null` entry meaning "every room in this area". It is cheap to store, and a whole area can be tested without listing its rooms.
- Expiry by timestamp and a periodic sweep, rather than per-object timers. `MSG_EXPIRE` lets listeners veto the expiry (2627-2665).
- The two-phase message flow (`okMessage` then `executeMsg`) is used for global messages (561-613) as well as for ordinary objects.
- Thin areas load rooms from the database when needed, and their statistics are built in small scheduled steps (439-506), so a large world does not stall the server.
- Reset keeps players and owned property in place by remembering their extended room IDs (2065-2084).
- Instance naming: an instance named `N_ModelName` maps back to the model area `ModelName` (`CMMap.java:235-253`; `DefaultAreaIStats.java:465-475`).
- The whole world is browsable as files under `map/` (`CMMap.java:2915-3071`), with each stat as its own file.

## Problems and cruft

Everything here was found by reading. No code was run. "Latent" means no caller was found in the searches made for this chapter.

1. Merging a RoomnumberSet into another is broken. In `DefaultRoomnumberSet.add(RoomnumberSet)` (113-136), `mine` is read from the source set (123). When the source has a group, `mine` is that group, so the `else` branch runs and adds the group to itself (133). Nothing is copied into the target. A whole-area source entry instead overwrites the target's entry with a whole-area marker (130). `OrderedRoomnumberSet` has the same code. Latent: no caller of `Roomnumbers().add(` found.
2. Whole-area entries cannot be removed. `remove(String)` (149-158, 186-187) only removes a non-null group, so removing a bare area name does nothing when the area is stored as a whole-area entry.
3. Adding a numbered room to a whole-area entry replaces that entry with a one-room set (459-467), so the whole-area meaning is lost.
4. Counts disagree. `roomCount` returns 0 for a whole-area entry (233-236), while `roomCountAllAreas` and `random` count it as 1 (200-201, 252-253).
5. Large plain room numbers decode wrongly. `add` stores plain integers up to Integer.MAX_VALUE (450-451), but `convertRoomID` treats values of 2^30 or more as packed coordinates (300-311). The long overload uses `<=` (280) and the string overload uses `<` (300), so 2^30-1 also decodes as packed. Enumeration and `random()` therefore return the wrong ID for such numbers, while `contains` is still consistent. Typical IDs are small, so this is latent.
6. `OrderedRoomnumberSet` duplicates `DefaultRoomnumberSet`. A diff shows only the class name, the copyright header and one equivalent expression at line 451. The name suggests ordering, but both classes use sorted maps and sorted sets.
7. `DefaultAreaIStats.copyOf()` (85-96) uses `clone()`, so the copy shares `statData` with the original (39). Latent: no caller of `AreaIStats.copyOf` found.
8. The average alignment is biased. The total sums only mobs that have a faction value (141-146), but the divisor is every counted mob (147, 322).
9. Mode tie-breaking. In the run-length scan (323-351), the lowest run starts at count 0, so it loses ties to any other level. Traced by hand: levels [1, 2] give MODE_LEVEL 2.
10. The common race is decided by `Hashtable` iteration order on ties (386-393). The animal flag stored with each race (161-162) is never read in `finishBuildingStats`.
11. Thin statistics can stay unfinished. When the area is destroyed or the host shuts down, the runnable returns without calling `finishBuildingStats` (448-453). `state` stays RUNNING, and `build` cannot restart (405). How callers handle `isFinished()` false was not traced.
12. `build()` tests and sets `state` without synchronization (405-407), so two threads can both start a build.
13. Building statistics changes the area: `build` sets the area's base and current level to the median mob level (395-396).
14. The shutdown timeout does nothing useful. `numAreasChk` is always 0 (2497), so the 30-second cancel at 2583-2589 fires only when no area has finished.
15. `addShip` (1002-1003) sets the area to ACTIVE only if it is already ACTIVE. It is a no-op.
16. Asymmetric guard: `registerWorldObjectDestroyed` skips `Area` objects (2105), but `registerWorldObjectLoaded` does not (2138-2139). Whether a boardable area reaches either path was not traced.
17. Removing during iteration. `cleanScriptHosts` (2183-2195), `delGlobalHandler` (462-465) and `sendGlobalMessage` (573-595) call `remove` inside a for-each over an `SLinkedList`. That class wraps a `java.util.LinkedList` (`SLinkedList.java:26`, iterator at 427-429). This can throw ConcurrentModificationException on the next step, unless the removed element was the last one.
18. `obliterateArea` lists each room twice. Both passes of its `for (i<2)` loop add the room to `allRooms` (1886-1893), so `obliterateRoom` runs twice per room (1920-1921). The two passes look intended to empty each room twice, not to list it twice.
19. `emptyRoom` processes grid children twice, once before and once after the parent (1754-1758 and 1849-1851).
20. `emptyRoom` checks `startRoom.ID()` (1806), which is the class ID, not the room ID. The intent was probably `roomID()`. Not confirmed.
21. Dead code in `roomMaintSweep`. The `if(ticked)` branch (2775-2781) sits inside `if(!ticked)` (2768), and the `ticked ? ... : ...` text choices (2787-2804, 2825) always take the second value.
22. Unused members within CMMap.java: `getGlobalIndex` (167-194), `areaComparator` (69-78) and `lastVReset` (59). A grep of the whole tree found no caller for `getGlobalIndex`.
23. Documented behaviour not implemented: `getArrivalRoom` returns a random room (`CMMap.java:943-952`; TODO at 946-950), but `WorldMap.java:478-487` says the destination is reverse-mapped and cached. `getSafeRoomToMovePropertyTo` can return null (`CMMap.java:1744`), while `WorldMap.java:823-825` says a room "is always returned".
24. Case-sensitive prefix fallback in `findAreaStartsWith` (`CMMap.java:303`), described in section 1.
25. `CityCafe.getBank` with a null branch throws. The first loop accepts a null branch (`CityCafe.java:224-225`). If no bank matches the chain, the code calls `CMLib.map().findArea(null)` (229). That reaches `getArea(null).toLowerCase()` (`CMMap.java:271`) and throws NullPointerException. Traced, not run.
26. Shared reset message. `resetMsg` (`CMMap.java:1928`) is one instance reused for every room. It is guarded only by the per-room lock (1943), so resets of two different rooms can race on `setTarget` and `executeMsg` (1958-1959).
27. `createNewExit` leaves a one-way exit when the far side is occupied (542-546), yet returns an empty string (549).
28. Fragile overloading in `obliterateRoom`. The same set holds direction indices and database exit keys. The code separates them only by `d < Directions.NUM_DIRECTIONS()` (1540). The range of database keys was not traced.
29. Javadoc copy-paste errors. `CityMap.getPostOffice` documents its chain as "a Banker" (`CityMap.java:113`). `delLibrary` documents "the Librarian to add" (`CityMap.java:356`).
30. Holy-place and service lists are written without synchronization (`CityCafe.java:59-63`, 276-295), while `holyPlaces()` synchronizes on the set it returns (302).
31. `DefaultRoomnumberSet.xml()` does not escape area names (382).
32. Multiplayer assumptions: `getAllPlayersHere` reads online sessions (1989); `isClearableRoom` refuses rooms with players (1276-1278) and moves NPCs through `wanderAway` as a side effect (1286); the stuck-player re-life path (2809-2822); and cross-mud arrival (943-952).

## Takeaways for a Python SUD

1. Room addressing. Python integers have no size limit, so the bit packing is unnecessary. Use a small immutable type instead:
   ```python
   @dataclass(frozen=True, order=True)
   class RoomId:
       area: str      # keep the area name's canonical case
       number: int    # plain room number, e.g. 3001
   ```
2. Registry. Keep a dict from area name to sorted room numbers. Decide whether a "whole area" entry is needed at all, and if so use `None` only for that case. Make the lookup cache key on lower-cased names on both sides of the comparison, which avoids the case bug at line 303.
3. Expiry. Store `expires_at` in game turns or ticks, not wall-clock time, since the real-time versus turn-based choice is still open. Sweep it from the main loop instead of a 60-minute service thread.
4. Area summary. A dataclass filled in one pass, with the statistics the game actually shows. Borrow median, mode and terrain counts. Drop alignment and faction statistics unless the SUD has them. Make ties deterministic:
   ```python
   def area_stats(levels: list[int]) -> AreaStats:
       if not levels:
           return AreaStats()
       levels = sorted(levels)
       counts = Counter(levels)
       mode = min(counts, key=lambda lv: (-counts[lv], lv))  # most common, lowest level on ties
       return AreaStats(population=len(levels), min_level=levels[0], max_level=levels[-1],
                        median_level=levels[len(levels) // 2], mode_level=mode)
   ```
5. Messages. Replace the global handler registry with one bus that has the two-phase flow, and no weak references. The `okMessage`-then-`executeMsg` pattern is worth keeping:
   ```python
   def send(self, category, event) -> bool:
       listeners = list(self._listeners.get(category, []))  # copy, so handlers may unsubscribe
       if not all(l.ok(event) for l in listeners):          # phase 1: any refusal stops it
           return False
       for l in listeners:                                   # phase 2
           l.apply(event)
       return True
   ```
6. Reset. Write `reset_area(area)` to move the one player out of the area and back to a remembered room ID, rather than the Java's multi-list handling.
7. Services. A dict keyed by `(service, area)`, falling back to the area's own entry. The chain and branch matching is unnecessary for one town.
8. Debugging. A `dump_world()` that writes each area to JSON is cheap and mirrors the `map/` file tree.
9. Drop: bit packing, weak references, the script-host and tag registries, multi-host lookup, grid-child maintenance, the cross-mud arrival stub, and the stuck-mob sweep (a single player cannot strand a mob the way concurrent threads can).

## Coverage

- Read in full (9 of 9): `Libraries/CMMap.java` (3073 lines, in four chunks), `Libraries/interfaces/WorldMap.java`, `Common/interfaces/RoomnumberSet.java`, `Common/interfaces/AreaIStats.java`, `Common/DefaultRoomnumberSet.java`, `Common/OrderedRoomnumberSet.java` (plus a diff against DefaultRoomnumberSet), `Common/DefaultAreaIStats.java`, `Libraries/interfaces/CityMap.java`, `Libraries/CityCafe.java`.
- Read in part or grepped for specific facts: `Areas/interfaces/Area.java:764-797` (Stats enum), `core/collections/LongSet.java:25-40` (constants) and grep hits at 162-245, `Libraries/Dice.java:398-406` (roll), `core/collections/PrioritizingLimitedMap.java:84`, `core/collections/SLinkedList.java:23-45`, `core/collections/CMNSortSVec.java:30`, `core/interfaces/MudHost.java:46`, `application/VFShell.java:1132` and `:1196`, `Abilities/Properties/Prop_Transporter.java:56`. Callers were checked by grep for `ROOM_EXPIRATION_MILLIS`, `STATS_`, `isFinished()`, `AreaIStats`, `copyOf` and `Roomnumbers().add(`.
- Skipped: `Room`, `Area`, `Exit`, `GridLocale` and ship implementations, the database layer, and `CoffeeMaker`. Named only where CMMap calls them.
- No code was executed. All behaviour in this chapter comes from reading the source.
