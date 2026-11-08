# Movement, tracking and world hunting

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b022` (Movement, tracking and world hunting) · 4 files · 6885 lines of Java.

Citations are `path:line`. Paths are relative to the CoffeeMud folder, and the common prefix `com/planet_ink/coffee_mud/` is written once here and dropped below. In tables and some lists, `MT` stands for `Libraries/MUDTracker.java`, `WHU` for `Libraries/WorldHuntUtils.java`, and `MT:n` or `WHU:n` for those files at line n. `TrackingLibrary.java` and `WorldHuntLibrary.java` are cited with their `interfaces/` folder.

## Summary

This bucket is the movement layer. It decides how a mob or player gets from one room to another, how NPCs wander and get placed, how a route is computed under a set of rules (open doors only, no air, and so on), and how a name typed by a player is resolved to a room, mob, item, shop stock or area. Routes come from a breadth-first "radiant" search that is walked back one ring at a time, and every real step goes through one `move()` pipeline that offers its leave, enter and crossing messages for veto before anything changes. The world-hunt library wraps its scans in time limits, duty cycles and caches, because a live MUD searches thousands of rooms per command. A much larger area-level router (area groups, then a Dijkstra-like search over links) is implemented here but has no callers in this tree. For a single-player Python game the useful parts are the rule sets as predicates, the ring-based route reconstruction, the route text, and the water, fall and door rules; the caching, time-slicing and multi-actor checks can go.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Libraries/MUDTracker.java` (`MUDTracker`) | Route search, NPC movement, the `move()` pipeline, riding and followers, water, fall and sink hooks, and the unused area router | 4301 |
| `Libraries/interfaces/TrackingLibrary.java` | Interface, plus nested `RFilter`, `RFilters`, `TrackingFlags`, `TrackingFlag` (23 constants at 1114-1149) and `TrailFlag` | 1150 |
| `Libraries/WorldHuntUtils.java` (`WorldHuntUtils`) | Name searches over rooms, mobs, floor items, inventories, shop stock and areas; caches; containment and admin tests | 1129 |
| `Libraries/interfaces/WorldHuntLibrary.java` | Interface; documents the search codes at 46-48 | 305 |

`CMLib.tracking()` returns `TrackingLibrary` (implemented by `MUDTracker`), and `CMLib.hunt()` returns `WorldHuntLibrary` (implemented by `WorldHuntUtils`), as resolved with `cm.py lib`.

Nested types inside `MUDTracker.java`:

| Type | Role | Lines |
|---|---|---|
| `RFilterNode`, `DefaultRFilters` | Linked list of filters; an edge or room is skipped if any filter says so | 65-75, 96-160 |
| `DefaultTrackingFlags` | `HashSet` of flags with a hand-maintained hash code | 162-239 |
| `AreaTrackData`, `AreaRoomGroup`, `TrackLink`, `TrackEnum`, `WeightedTrail` | Data for the area router (unused) | 3248-3375 |
| `RideFallChecker` | Runnable that dismounts a rider whose mount has moved away | 1414-1464 |

## How it works

### 1. Rules as composable filters

Each `TrackingFlag` is an enum constant that carries an `RFilter` (`Libraries/interfaces/TrackingLibrary.java:1114-1149`). The filters are attached in `MUDTracker.initializeClass()` (`Libraries/MUDTracker.java:4020-4300`). A filter is asked about one edge and its target: `isFilteredOut(hostR, R, E, dir)` (`Libraries/interfaces/TrackingLibrary.java:990-1004`) returns true when the edge or room must be skipped. A flag set becomes an `RFilters` chain in `convertTrackingFlagsToFilters` (`Libraries/MUDTracker.java:542-560`), cached in a `Hashtable` keyed by the flag set (`Libraries/MUDTracker.java:56`).

What the main flags test, as the code does it:

| Flag | Edge or room is skipped when | Lines |
|---|---|---|
| OPENONLY | exit is closed or marked unhelpful | MT 4035-4042 |
| UNLOCKEDONLY | exit is locked or unhelpful | MT 4044-4051 |
| PASSABLE | exit is marked unhelpful | MT 4053-4060 |
| AREAONLY | target room belongs to a different Area object than the host | MT 4062-4069 |
| NOHOMES | target room has a land title | MT 4026-4033 |
| NOPRIVATEPROPERTY | target room has no land title, or an empty owner | MT 4111-4121 |
| NOAIR | target is an air domain, or exit is flying | MT 4100-4110 |
| NOWATER | target is watery, or exit is swimmable | MT 4122-4131 |
| NOTHINAREAS | target's area has FLAG_THIN | MT 4071-4080 |
| NOHIDDENAREAS | target room or its area is hidden | MT 4081-4090 |
| NOEMPTYGRIDS | grid child whose grid parent has no room ID | MT 4091-4099 |
| WATERSURFACEONLY | target is not a water surface | MT 4132-4140 |
| WATERSURFACEORSHOREONLY, SHOREONLY | target is not a surface or shore next to water | MT 4154-4207 |
| UNDERWATERONLY | target is not underwater | MT 4208-4216 |
| FLOORSONLY | target has a room below it | MT 4217-4225 |
| CEILINGSSONLY | target has a room above it | MT 4226-4234 |
| NOCLIMB, NOCRAWL | target or exit is climbable or crawlable | MT 4235-4252 |
| OUTDOORONLY, INDOORONLY | target is indoor, or outdoor | MT 4253-4270 |
| DRIVEABLEONLY | target is not drivable, or exit is closed, flying or swimming | MT 4141-4153 |
| WILDERNESSONLY | target domain is not in the city, indoor, air, seaport or space list | MT 4271-4297 |

Note that FLOORSONLY keeps rooms with no room below them, and CEILINGSSONLY keeps rooms with no room above them. The code is the authority here; the names do not say this directly.

### 2. Radiant (ring) searches

`getRadiantRooms(room, rooms, filters, radiateTo, maxDepth, ignoreRooms)` (`Libraries/MUDTracker.java:576-624`) is a breadth-first search from the origin, one ring per level, stopping at `maxDepth`. For each exit it resolves the destination through `map.getRoom` (`MT:611`), skips rooms that are visited, ignored or filtered (`MT:612-615`), appends the room, and returns early if it reaches `radiateTo` (`MT:619-620`). The list comes back ordered by ring with the origin first. The code calls it the radiant list, and every route function below works on one.

Variants:
- `getRadiantRoomsEnum` (`MT:689-768`) is lazy. It expands the queue one full ring at a time as the caller iterates (`MT:726-765`).
- `getRadiantRoomsToTarget` (`MT:771-839`) stops at the first room that `radiateTo` accepts and leaves it last (`MT:828-829`). The public overload builds its filters with its own copy of the cache logic (`MT:775-787`). Skill_Track uses it to find a named target (`Abilities/Skills/Skill_Track.java:624`).
- `getRadiantRoomTarget` (`MT:842-874`) is an unbounded search for the first acceptable room. Its visited set is a `TreeSet` (see Problems, item 2).
- `getRadiantAreas` (`MT:626-686`) runs the same idea over areas. Each ring rescans every room of every frontier area (`MT:657-678`).

### 3. Routes: build, walk back, describe

`findTrailToRoom` (`Libraries/MUDTracker.java:288-384`) works in three steps:
1. If no radiant list was supplied, it builds one from `location` out to `destRoom` with the flags and `maxRadius` (`MT:296-302`), with `location` at index 0.
2. It builds `incoming`: for each radiant room, the radiant rooms that have an exit into it (`MT:323-340`).
3. It walks back from `destRoom`. At each step it takes the untried predecessor with the lowest radiant index, and stops at `location`. It returns null if `destRoom` was not reached or the walk gets stuck (`MT:316-317`, `MT:347-381`).

On an unfiltered graph, every predecessor of a room is at least one ring closer, and the BFS parent is exactly one closer, so each step drops exactly one ring. The route is therefore a shortest one, and the walk needs no backtracking. The result runs destination first and ends at the location, as the interface documents (`Libraries/interfaces/TrackingLibrary.java:55-57`).

The walk does not apply the flags. `incoming` uses every exit between two radiant rooms (`MT:326-338`). The flags decide which rooms enter the radiant list, but not which edges the walk may take. A closed door between two rooms that were each reached some other way can end up in the route. The two checked walks, `canValidTrail` and `getTrailToDescription`, do test each step with the flags, through `getRoomDirection` (`MT:2156-2167`, used at `MT:2268` and `MT:2316`). See Problems, item 1.

`findTrailToAnyRoom(location, List destRooms, flags, maxRadius)` (`MT:405-448`) builds one shared radiant list when there is more than one destination (`MT:415-419`). It then tries up to five randomly picked destinations (`MT:420-435`) and keeps the shortest (`MT:433-434`). It removes each pick from the caller's list (`MT:424`). For a same-area target it adds AREAONLY to a copy of the flags (`MT:425-427`), but the shared radiant list was built without it, so that attempt and its fallback (`MT:431`) search the same rooms. Any remaining destinations are tried one at a time (`MT:436-446`).

`findTrailToAnyRoom(location, RFilter destFilter, ...)` (`MT:451-461`) finds the nearest room the filter accepts with `getRadiantRoomsToTarget`, then routes to it with `findTrailToRoom`.

`trackNextDirectionFromHere(theTrail, location, openOnly)` (`MT:464-496`) is the stateless step helper. It returns 999 when the location is the trail's first entry, meaning arrived (`MT:470-471`). Otherwise it returns the direction of the trail neighbour with the lowest index, or -1. It is the per-step call in Skill_Track (`Abilities/Skills/Skill_Track.java:375`, `:684`).

`findAllTrails` (`MT:2106-2154`) recurses from the destination back along decreasing radiant indices and returns every such route, not only the shortest. Callers are `Abilities/Skills/Skill_ResearchRegionMap.java:348`, `Abilities/Thief/Thief_UndergroundConnections.java:345`, and `Abilities/Druid/Chant_SiftWrecks.java:138` (the last through `getShortestTrail`, `MT:2091-2103`). Nothing caps the number of routes (how large it gets is unverified).

`getIndexEnsureSet` (`MT:2223-2240`) builds a NOEMPTYGRIDS radiant list when none is given. `canValidTrail` (`MT:2243-2283`) checks that a route exists, and `getTrailToDescription` (`MT:2286-2400`) builds the text for one. Both walk back from the target through radiant rooms, choosing an earlier room that `getRoomDirection` accepts. Both give up after `maxSecs` (`MT:2262`, `MT:2310`).

`getNearestValidIDRoom` (`MT:877-884`) finds the nearest room with a usable ID, using `validIDFilter` (`MT:78-94`). `Libraries/CMMap.java:669` uses it to give rooms without an ID an approximate one.

### 4. The movement pipeline `move()`

`walk` and `run` (`MT:1642-1663`, `MT:1925-1928`) all reach `move()` (`MT:1665-1922`). Its steps, in order:

1. Resolve the exit and destination. No destination gives "You can't go that way." (`MT:1672-1681`).
2. Build the leave and enter messages. There are variants for flee, crawl-flee, riding and plain movement (`MT:1701-1739`). The verbs come from `CMFlagLibrary` (`MT:1728`, `MT:1735`), so "leaves" and "arrives" depend on the mob's disposition.
3. Admin bypass. A non-monster with GOTO security can pass any veto (`MT:1742`, `MT:1743-1768`). With no exit at all, the step prints that the area to the side "shimmers and becomes transparent" (`MT:1750`).
4. Veto chain (`MT:1752-1768`). The mob's leave message, the exit's enter message, the source room's leave message, the far exit's leave message, the destination's enter message and the mob's enter message are each offered to their owners. The first refusal returns false.
5. Cost and fatigue, for non-monsters on foot (`MT:1777-1792`). Each step calls `CMLib.combat().expendEnergy` once per cost point, with WALKCOST (default 1) or RUNCOST (default 2) (`core/CMProps.java:3132-3133`; `Libraries/MUDFight.java:3426-3450`). Movement drops by the room's points-per-move, increased in proportion to overweight. Thirst and hunger tick by base weight divided by 500. "You are too tired." is printed at zero movement unless the move is a flight, forced, or GOTO (`MT:1787-1790`).
6. Hygiene for players (`MT:1793-1800`).
7. Riders ahead (`MT:1803-1811`). A refusal stops the move.
8. Trailing messages are held back until the move completes (`MT:1814-1829`, `MT:1911-1920`).
9. Execute: the exit's enter message, removal from the old room, the leave message, relocation, the enter message, and the far exit's leave message (`MT:1830-1848`).
10. For players with AUTOWEATHER, a weather line when moving from indoors into a non-clear outdoor room (`MT:1853-1860`).
11. Riders behind (`MT:1863-1864`).
12. Followers, when the move is not a flight. A follower still in the origin room and awake moves along (`MT:1866-1910`). AUTOGUARD followers stay and say "remain(s) on guard here." (`MT:1879-1880`). When the leader is sitting, a follower that is not sitting is first sent a sit message, and after the move it is told to stand (`MT:1886-1904`, `postStand` at `MT:1903-1904`).

### 5. Riding, leading and vehicles

- `ridersAhead` (`MT:1574-1639`) gathers the riders of a chain of rideables and moves the mounts first. If one refuses, the rider is told "@x1 won't seem to let you go that way." (`MT:1620-1621`), and moved items and NPC riders are put back (`MT:1622-1633`).
- `ridersBehind` (`MT:1476-1525`) moves riders after the mount. A rider in neither the source nor the destination is detached (`MT:1506-1507`). A rider whose own move fails falls off, and is told "You fall off @x1!" (`MT:1502`).
- `addRiders` (`MT:1527-1546`) collects the riders of mobile rideables recursively.
- `walk(Item, dir)` (`MT:1931-2029`) moves a rideable item, such as a cart or boat, with its riders. Riders on furniture, table, hook, sleep or ladder bases are dismounted rather than carried (`MT:2000-2005`, `MT:2021-2022`). NPC riders who are not in the room are brought back to it (`MT:1955-1959`).
- `RideFallChecker` (`MT:1414-1464`) is scheduled 99 ms ahead (`MT:1451`, `MT:1472`). It checks whether a rider's mount is still in the same room. It dismounts after 20 checks, or at the second check if the mount is then in a different area (`MT:1440-1447`).
- `createNavigationMob` (`MT:1549-1572`) creates a temporary factory MOB to stand for a ship. Water-based ships get IS_SWIMMING, and the clan comes from the owner.
- `findALadder` and `postMountLadder` (`MT:2403-2436`) handle LADDER-basis rideables.

### 6. NPC wandering and placement

`beMobile` (`MT:898-1181`) moves one NPC one step and returns whether it moved. Its gates, in order:
- No move while the mob has riders or is following someone nearby (`MT:923-931`).
- No move in a room with an admin who has sysop messages on (`MT:935`). The same test applies to the destination (`MT:982-983`).
- Grid rooms: go to a random child (`MT:942-948`). Lost room: players go to their start room, NPCs are killed (`MT:950-963`, which logs "Inexplicable lost room").

Direction choice is up to ten random rolls (`MT:970-972`). A roll is rejected when:
- the room is not cached (`MT:973-977`). For `StdRoom` this means there is no door object or it is a ThinRoom placeholder (`Locales/StdRoom.java:1912-1921`).
- the exit is trapped, or hidden or invisible to the mob, or the opposite exit is trapped (`MT:989-1000`).
- it crosses into an air or underwater room without flight or swimming (`MT:1002-1013`).
- `wander` is false and the area name changes (`MT:1015-1017`).
- the destination is in the avoid set (`MT:1019-1021`), or outside the prefer set (`MT:1023-1025`).

Doors: an NPC does not open a door on owned land (`MT:1054-1059`). A locked door is unlocked by offering the MSG_UNLOCK message, with no key check in this method (`MT:1068-1078`). Whether a listener checks keys is unverified. The mob then opens the door with the open command (`MT:1082`), and after moving it closes the door behind and re-locks it if it had unlocked it (`MT:1158-1177`).

Movement mode (`MT:1094-1154`): a mob with no abilities walks (`MT:1094-1095`). In a watery surface room with Skill_Swim it swims (`MT:1097-1111`). It climbs if the destination's room ID contains "Surface" or the room or exit is climbable (`MT:1113-1131`), and sneaks with Thief_Sneak when it has it (`MT:1133-1150`). Skill paths restore mana and movement afterwards (`MT:1106-1110`), and raise a proficiency below 50 to roll(1,50,level*15) (`MT:1104-1105`, `MT:1124-1125`, `MT:1138-1140`).

Other placement helpers:
- `wanderAway` (`MT:1184-1214`) and `wanderCheckedAway` (`MT:1241-1263`) call `beMobile` up to nine times to leave a room. `wanderAway` can also send the mob to its start room, and it ignores the veto result when it does (`MT:1208-1211`).
- `wanderFromTo`, `wanderCheckedFromTo` and `wanderIn` (`MT:1266-1355`) place a mob in a room and print "<S-NAME> wanders in from @x1." (`MT:1328`, `MT:1352`). `getCheckedDir` (`MT:1276-1303`) tries up to 99 random directions. It refuses air, underwater and surface rooms unless the mob can handle them, and refuses locked exits in its first 64 attempts.
- `markToWanderHomeLater` (`MT:387-402`) attaches the WanderHomeLater ability with a one-shot delay in ticks.
- `forceRecall` (`MT:2590-2643`) moves the mob, and optionally its NPC followers, to the start room. A mob in combat flees first (`MT:2626-2627`). The departure text reads "<S-NAME> disappear(s) into the Java Plane!" (`MT:2628`).

Callers: `beMobile` has 17 call sites in nine files, including `Behaviors/Mobile.java:371`. That call passes the neighbours the mob objects to, built from `okRoomForMe` over the cached exits (`Behaviors/Mobile.java:350-371`). Mobile's emergency movement (`Behaviors/Mobile.java:266-307`) uses a radiant list limited by AREAONLY and OPENONLY (`Behaviors/Mobile.java:271-274`). Other callers include `Behaviors/Wimpy.java`, `Behaviors/AntiVagrant.java` and `Abilities/Prayers/Prayer_AuraFear.java`.

### 7. Water, falls and sinks

- `isOkWaterSurfaceAffect` (`MT:2471-2552`) is called by water rooms on enter, leave and flee messages (`Locales/WaterSurface.java:218`, `Locales/EndlessOcean.java:80`, `Locales/EndlessThinOcean.java:76`, `Abilities/Druid/Chant_Flood.java:177`). A non-swimmer with no boat and no flight is refused with "You need to swim or ride a boat that way." (`MT:2512-2516`). A boat counts if the exit leads into a boardable area, or the actor carries a WATER_BASED rideable (`MT:2489-2511`). A swimmer whose weight is over half of max carry is refused with "You are too encumbered to swim." (`MT:2522-2524`). Sitting or sleeping in water is refused with "You cannot rest here." unless the actor can breathe water or rides a swimmer (`MT:2530-2536`). Drinking saltwater is refused (`MT:2544-2546`).
- `makeFall` (`MT:2439-2468`) applies to a subject that is not flying when there is a room below it (or above, if reversed). It invokes the Falling ability with misc text NORMAL or REVERSED (`MT:2457-2463`).
- `makeSink` (`MT:2555-2587`) does the same with the Sinking ability, when the room above or below is underwater (`MT:2560-2566`).

### 8. Thin areas and lazy rooms

Some areas are thin: their rooms are placeholders until they are needed. `StdRoom.isRoomInDirCached` returns false for an exit that has no door object or leads to a ThinRoom (`Locales/StdRoom.java:1912-1921`). NPC wandering skips uncached exits (`MT:973-977`), and NOTHINAREAS removes thin areas from a route (`MT:4071-4080`). Building the area-track data for a thin area reads the navigation skeleton from the database (`MT:3927-3941`) and destroys the area's rooms afterwards (`MT:4010-4011`).

### 9. Name-based world hunts (`WorldHuntUtils`)

Entry points: `findWorldRoomLiberally` and `findWorldRoomsLiberally` (`Libraries/WorldHuntUtils.java:551-564`), `findAreaRoomLiberally` and `findAreaRoomsLiberally` (`WHU:566-579`), and list-based finders for mobs, floor items, inventories and shop stock (`WHU:166-549`).

Search codes, parsed at `WHU:767-797` and documented at `Libraries/interfaces/WorldHuntLibrary.java:46-48`:

| Code | Searches |
|---|---|
| R | rooms, by ID and by name or description |
| A | strict area, by exact name |
| E | weak area, by partial name |
| P | online players |
| M | mobs in rooms |
| I | items on the floor |
| V | items carried by mobs in rooms |
| K | shop stock |

Resolution order when the code runs (`WHU:799-985`). The first hit wins:
1. A direction word gives the adjacent room. This needs R (`WHU:799-803`).
2. Room ID, or ID within the current area (`WHU:804-808`).
3. A `#` prefix gives an ID in the current area (`WHU:813-818`).
4. P: players (`WHU:823-828`).
5. A: strict area (`WHU:834-838`).
6. M: mobs (`WHU:860-880`).
7. R: room name, then room description (`WHU:885-905`).
8. I: floor items (`WHU:908-928`).
9. V: mob inventories (`WHU:934-954`).
10. K: shop stock (`WHU:957-977`).
11. E: weak area (`WHU:980-985`).

Matching is substring-based, on text with colour codes removed (`WHU:132`, `WHU:154`). Each room must pass the acting mob's `canAccess` (`WHU:133`, `WHU:155`, `WHU:194`, `WHU:224`, `WHU:291`, `WHU:417`).

Time limits come in two forms. `timePct` duty-cycles a scan by sleeping the calling thread after each slice that runs out of time (`WHU:96-105`, `WHU:184-205`, `WHU:269-306`). At 100 there is no sleep. Callers pass values from 5 (`Behaviors/Concierge.java:507`) to 100. `maxMillis` is checked between stages (`WHU:630-635`, with checks at `WHU:830`, `:841`, `:881`, `:904`, `:927`, `:953`, `:976`). Area searches fix it at 120 ms (`WHU:569`, `WHU:578`). `Commands/Goto.java:99` allows 120000 ms for its world search.

Caches. Each stage stores its matches for the lower-cased search text in a time-limited map (the maps are built with the EXPIRE constants at `WHU:48-53` and `WHU:670-739`). The puts and gets are at `WHU:867-875`, `WHU:891-899`, `WHU:914-922`, `WHU:940-948` and `WHU:963-971`. The cache applies only when the acting mob is null or a monster (`WHU:865`, `WHU:889`, `WHU:912`, `WHU:938`, `WHU:961`). `Commands/Goto.java` passes a player, so it never caches. `Common/DefaultScriptingEngine.java:1328` passes null, so it does. The code comment explains the design (`WHU:750-753`). The `MAPFINDSNOCACHE` property turns caching off (`WHU:754`). Cached MOB, room and item lists are rejected when an entry is destroyed, and cached items are also rejected when their owner has changed (`WHU:637-668`).

Containment and admin. `isHere` (`WHU:1010-1064`) tests whether an object is in a room or area, counting exits, owners and boat areas (`WHU:992-1008`). `isAnAdminHere` (`WHU:1066-1089`) tests for an in-game admin with mob or room security who may have sysop messages on. `getAllGroupRiders` (`WHU:1113-1127`) gathers a group, meaning followers, leaders, riders and rideables, optionally limited to one room.

## Content worth mining

### Route text

`getTrailToDescription` writes a route as direction characters, joined by a space by default (`MT:2290-2291`). When a direction repeats, a count is written in front of it, and a count of one is omitted (`MT:2352-2369`). The characters come from `CMLib.directions().getDirectionChar` (`MT:2336`). Optional extras:
- a CONFIRM block with the trail, the first step and the first room (`MT:2339-2388`);
- a "*" marker for unnamed rooms (`MT:2378-2383`);
- an "Areas crossed" list (`MT:2389-2396`).

Pattern, in the code's own terms: count then character, space-joined, count omitted for one. Example: a run of two steps east and then one north is written "2e n" in this pattern (the exact letters depend on `getDirectionChar`).

### Message catalogue

Short texts, quoted or paraphrased. `@x1`, `@x2` and `@x3` are argument slots, and `<S-NAME>` is the actor.

| Text | Where | Context |
|---|---|---|
| "You can't go that way." | MT 1674, 1745, 1840 | No exit, or the move failed |
| "You are too tired." | MT 1790 | Zero movement |
| "@x1 won't seem to let you go that way." | MT 1621 | A mount refuses to move |
| "You fall off @x1!" | MT 1502 | The mount left without its rider |
| "You ride @x1 @x2." | MT 1494 | Rider's move on a mount |
| "You are ridden @x1." | MT 1617 | Mount's view of its rider |
| "You flee @x1." | MT 1722 | Riding flight |
| "<S-NAME> flee(s) @x1." | MT 1732 | Flight on foot |
| "remain(s) on guard here." | MT 1880 | AUTOGUARD follower |
| "You follow @x1 @x2." | MT 1885 | Follower moves along |
| "The area to the @x1 shimmers and becomes transparent." | MT 1750 | Admin move into a direction with no exit |
| "You need to swim or ride a boat that way." | MT 2516 | Water entry |
| "You are too encumbered to swim." | MT 2524 | Heavy swimmer |
| "You cannot rest here." | MT 2536 | Sitting or sleeping in water |
| "You don't want to be drinking saltwater." | MT 2546 | Drinking seawater |
| "<S-NAME> wanders in from @x1." | MT 1328 | NPC placed from a direction |
| "<S-NAME> disappear(s) into the Java Plane!" | MT 2628 | Forced recall |
| "You can't get there from here." | MT 2311, 2328, 2398 | Route search failed or timed out |
| "Unable to determine '@x1'." | MT 2295 | Route target not found |
| "You can't get to '@x1' from here." | MT 2298 | Target not in the radius |
| "<S-NAME> unlock(s) <T-NAMESELF><O-WITHNAME>." | MT 1075 | NPC unlocking a door |

### Constants

| Constant | Value | Where |
|---|---|---|
| AREA_TRAILS_TIME_MS | 300 | MT 62 |
| AREA_TRAIL_TIMEOUT_MS | 5000 | MT 63 |
| Cache expiries | 1 minute to 1 hour, via EXPIRE_* | WHU 48-53 |
| WALKCOST, RUNCOST | 1 and 2 by default | `core/CMProps.java:3132-3133` |
| Random tries, NPC step | 10 | MT 970 |
| Random tries, placement | 99 | MT 1281 |
| Locked-exit cut-off | first 64 attempts | MT 1292 |
| Skill proficiency seed | roll(1,50,level*15), raised below 50 | MT 1104-1105 |
| Swimmer encumbrance | weight over half of max carry | MT 2522 |
| Ride checks | 20, polled every 99 ms | MT 1446, 1451 |
| Radiant capacity hint | 4096 | MT 583-587 |

### Search codes as a text-game idea

The letter-code string in WHU:767-797 is a compact way to say what a search should cover. A single-player game could use the same idea: one query string, with scope letters and a `#` for local IDs.

## Notable design ideas

- **Rules as enum-with-predicate.** Each route rule is a named enum constant with its own test (`Libraries/interfaces/TrackingLibrary.java:1114-1149`). Rule sets are sets of these names, and they are cached per set (`Libraries/MUDTracker.java:542-560`). NPC and player pathing share the same rules.
- **One ring list, many uses.** The same breadth-first list serves routes, nearest-target search, radius-limited scans and the "objections" set that Mobile builds (`Behaviors/Mobile.java:371`).
- **Ring-index walk-back.** Taking the predecessor with the lowest ring index gives a shortest route with no priority queue (`Libraries/MUDTracker.java:347-381`).
- **Two-phase veto in `move()`.** The same message objects are offered to the mob, the exits and the rooms before anything happens. New rules, such as a guard or a trap, can hook in without changing `move()` (`Libraries/MUDTracker.java:1752-1768`).
- **Lazy rooms.** Placeholder rooms and the uncached-exit skip mean an NPC never walks into a room that has not been built (`Locales/StdRoom.java:1912-1921`, `Libraries/MUDTracker.java:973-977`).
- **Tracking as a state.** Skill_Track holds the trail and updates its next direction when the tracker looks around (`Abilities/Skills/Skill_Track.java:375`). The route is an effect, not a one-off result.
- **Environment through abilities.** Falling and Sinking are abilities invoked on the subject, with a REVERSED variant for upward motion (`Libraries/MUDTracker.java:2457-2463`, `:2578-2582`). Water entry is checked in the room's own message handler (`Libraries/MUDTracker.java:2471-2552`).
- **Route text as run-length code.** The direction string is compact and readable (`Libraries/MUDTracker.java:2352-2369`).

## Problems and cruft

### Bugs found by reading (not run)

1. **The route walk ignores the flags** (`Libraries/MUDTracker.java:326-338`, `:347-381`). The flags only shape the radiant list, so a walk can take an edge that the flags exclude when both rooms are inside the radius. Flags of this kind are OPENONLY, UNLOCKEDONLY and PASSABLE (exit-level tests), and also the exit-level tests of NOAIR, NOWATER, NOCLIMB, NOCRAWL and DRIVEABLEONLY. `canValidTrail` and `getTrailToDescription` avoid this by checking each step (`MT:2156-2167`, `:2268`, `:2316`). Impact on live play is unverified.
2. **Visited set is a `TreeSet` of rooms** (`Libraries/MUDTracker.java:846`, `:862-868`). `StdRoom.compareTo` orders rooms by `CMClass.classID` (`Locales/StdRoom.java:1402-1404`), and for a CMObject that is its `ID()` (`core/CMClass.java:2926-2936`). `StdRoom.ID()` is "StdRoom" (`Locales/StdRoom.java:43-46`), and `Locales/Desert.java:38-41` returns "Desert". Any two rooms of one class compare equal, so after the first room of a class is visited, the rest are skipped. `getRadiantRoomTarget` is therefore cut short in any area where room classes repeat. Its callers are `Areas/StdThinInstance.java:399` and `:408`, and `getNearestValidIDRoom` (`MT:877-884`), used by `Libraries/CMMap.java:669`. This is the most serious finding in the bucket.
3. **Area-scoped searches can read world-wide cache entries.** The cache keys are the search text alone (`WHU:867`, `:891`, `:914`, `:940`, `:963`), but the result depends on the area argument (the area sets the room list at `WHU:845-857`). `findAreaRoomLiberally` passes returnFirst=true (`WHU:569`) and still reads the cache (`WHU:867`, `:891`). A null-mob area search can therefore return a room outside the area, if a world search for the same text was cached first. The null-mob caller is `Common/DefaultScriptingEngine.java:1328`. Cached monster results are also reused across monsters with different access rules, since `canAccess` runs only when the result is built (`WHU:133`, `:155`). Impact is unverified.
4. **AREAONLY is ignored when a radiant list is shared** (`Libraries/MUDTracker.java:415-431`). Both the same-area attempt and its fallback search the same rooms.
5. **`findTrailToAnyRoom` edits the caller's list** (`Libraries/MUDTracker.java:424`). Current callers pass fresh lists, so this is latent.
6. **`DefaultTrackingFlags` hash is wrong after `plus(TrackingFlags)`** (`Libraries/MUDTracker.java:179-189`, `:199-203`). `addAll` calls `add` for each new member, which XORs it into the hash, and then XORs every member again in its own loop. New members cancel out, and members already present are XORed once. For example, the empty set plus {A, B} hashes like the empty set. `Behaviors/Concierge.java:110-111` and `:197-198` use this path. The visible effect is cache misses. `equals` still holds, so the filters are not wrong. Mutable flag sets used as `Hashtable` keys (`MT:546-557`, `MT:775-787`, `MT:2889`) do not produce wrong filters either, because a `Hashtable` compares the stored hash first. They can leave stale entries.
7. **`getCalculatedAdjacentGridRoom` ignores `R`** (`Libraries/MUDTracker.java:2697-2707`). It looks up coordinates offset from (0,0,0), so it is correct only for the origin of `buildGridList`. All three callers pass the origin (`Abilities/Common/BuildingSkill.java:571-579`, `:884-892`; `Abilities/Properties/Prop_LotsForSale.java:338-342`). Latent.
8. **The `getRadiantRoomsEnum` enumerator does not stop at `radiateTo`** (`Libraries/MUDTracker.java:748-766`). `finished` stops only later expansions, so the rest of the current ring and the queue still come out. The interface says `radiateTo` is last (`Libraries/interfaces/TrackingLibrary.java:539`).
9. **`getShortestTrail` returns an empty list when its first candidate is empty** (`Libraries/MUDTracker.java:2095-2101`).
10. **Vetoes are ignored in two places.** `wanderAway` ignores the result of its okMessage on the start room (`MT:1208-1211`). `forceRecall` ignores both previews (`MT:2629`, `:2632`), though the interface says it "previews and sends" (`Libraries/interfaces/TrackingLibrary.java:675-676`).
11. **`walk(Item)` sets the rider's riding before the veto checks** (`Libraries/MUDTracker.java:1953`, against the early return at `:1965-1967`). A refused vehicle move leaves riders attached.
12. **`walkForced` between non-adjacent rooms uses the last exits it finds in any direction** (`Libraries/MUDTracker.java:1381-1395`). The enter and leave exits are not the ones between the two rooms, so the messages can name the wrong exits.
13. **`findRooms` does not clamp `timePct`** (`Libraries/WorldHuntUtils.java:96`), unlike the other finders (`WHU:184-186`). Above 100 the sleep argument goes negative, and `core/CMLib.java:1585-1594` catches only InterruptedException. Latent, since callers use 5 or 100.
14. **`charAt(0)` on search text with no empty check** (`WHU:59`, `:71`, `:813`). Latent, depending on callers.
15. **`isAnAdminHere` retries without bound** on ConcurrentModificationException (`WHU:1083-1085`).
16. **Area-router null dereferences** (dead code): `validateRadiantAreaGroups` (`MT:2973-2974`) and `getRadiantAreaGroups` (`MT:3404`).
17. **`TrackLink` is a `TreeMap` key but is not Comparable** (`Libraries/MUDTracker.java:3316`, `:3331`, `:2885-2886`). The cache branch is unreachable because `prevLink` is always null (`MT:3118`, and see `MT:2883`). If it were reached, it would throw ClassCastException.
18. **`findTrailToRoomByAreas` computes `shortest` and never uses it.** It returns the first candidate that succeeds (`MT:3117`, `:3190-3192`, and the commented return at `:3197-3198`).
19. **Area groups are as large as a whole area, as written.** `buildAreaGroup` runs an unbounded breadth-first search from the first start room (`MT:3658-3750`, call at `MT:3821`). One connected region of same-area rooms joined by mutual exits therefore normally becomes one group. That contradicts the comments on group size (`MT:3275`, `:3283-3290`). The path is dead, so impact is low.
20. **`Abilities/Skills/Skill_Track.java:670` appends the start room again** to a trail that already ends in it (`MT:343-381`). It is harmless, because `trackNextDirectionFromHere` uses `indexOf`, which finds the first copy (`MT:472`).

Minor: `int distance` accumulates `Math.sqrt` values, which truncates each step (`MT:2945`, `:2953`, dead path). `buildInnerWeights` records weight 1 for an unreachable pair, because the parent map has no entry for the target (`MT:3797-3809`, dead path). `findShopStock` calls `Thread.sleep` directly (`WHU:404`) where the others use `CMLib.s_sleep`. `findWorldRoomsLiberally` repeats an `enforceTimeLimit` check (`WHU:927-931`). `RideFallChecker` synchronizes on the mob only to read it (`MT:1429-1432`). `createNavigationMob` creates its factory MOB before the room null check (`MT:1554-1555`).

### Dead code

- The whole area router: `findTrailToRoomByAreas` (`MT:3106-3202`) and its helpers (`MT:2845-4015`). Nothing outside `MUDTracker.java` calls them (checked by grep). The interface still declares the entry point (`Libraries/interfaces/TrackingLibrary.java:101`).
- `findTrailToRoomOld` and `getRadiantRoomsOld` (`MT:2709-2843`). The interface still declares the first (`TrackingLibrary.java:112`).
- `forceEntry` (`MT:1357-1367`), `getCachedAreaTrackData` (`MT:3866-3880`), `testAreaDataIntegrity` (`MT:2993-3067`, its call commented out at `MT:4012`), and the debug helpers `logGroupLinks` and `dumpTrail` (`MT:3069-3103`).
- The `Tests/AreaTrails.java` calls that look at these paths are inside comments.

### Documentation versus code

- `TrackingLibrary.java:72`, `:97`, `:107`: flags described as "not used in the trail calculation". They do build the radiant list (`MT:296-300`), and the walk ignores them.
- `TrackingLibrary.java:284-285`: `canValidTrail` "optionally ignoring any ignore rooms". The set is used only when no radiant list is supplied (`MT:2225-2228`).
- `TrackingLibrary.java:575`: `buildGridList` says `""` means any owner. The test compares each owner's name to `""` (`MT:2679`), so only an empty owner matches.
- `TrackingLibrary.java:709`: beMobile says it will "crawl if crawling needed". No crawl branch exists (`MT:1094-1154`).
- `TrackingLibrary.java:78-82`: the area router's own doc says it "sucks at its job". It is unused.

### Multiplayer assumptions

- Admin bypass and sysop-message gating (`MT:935`, `MT:1742`, `WHU:1066-1089`).
- Follower and AUTOGUARD logic (`MT:1866-1910`).
- Per-player access checks, with monster caches keyed on search text (`WHU:637-668`, `WHU:862-876`).
- Player-only branches: hygiene (`MT:1793-1800`), search of online players (`WHU:823-828`), and the thirst and hunger ticks in movement cost.
- Duty-cycled scans that sleep the calling thread (`WHU:105`, `:202`, `:304`).

## Takeaways for a Python SUD

1. **Rules as data.** Model route rules as an enum of named flags, each with a predicate over (source, destination, exit). A route query takes a frozenset of flags. Keep the predicates edge-level, and apply them in the search and in the reconstruction. This fixes bug 1 by construction.
2. **Ring search with parent pointers.** Store the BFS parent of each room as it is discovered, instead of re-deriving predecessors afterwards. The path then uses only edges the search accepted. Sketch:

   ```python
   def route(start, goal, passable):
       parent, ring, frontier = {start: None}, 0, [start]
       while frontier and goal not in parent:
           nxt = []
           for room in frontier:
               for ex in room.exits.values():
                   if ex.dest not in parent and passable(room, ex):
                       parent[ex.dest] = room
                       nxt.append(ex.dest)
           frontier = nxt
       if goal not in parent:
           return None
       path, cur = [], goal
       while cur is not None:
           path.append(cur)
           cur = parent[cur]
       return path[::-1]
   ```

   Use Dijkstra or A* only once steps have different costs, such as doors, terrain or time.
3. **Cache routes safely.** Key a cache on (start, goal, frozenset(flags), world_version), and bump world_version when a door, lock or room changes. Immutable keys avoid the `Hashtable` problems above.
4. **Identity for visited sets.** Use room IDs or object identity, never an ordering by type-level ID. Bug 2 is the warning.
5. **One movement check.** Put all vetoes in `can_move(actor, src, exit) -> (ok, reason)`: locked, trapped, water, too tired. Then `do_move` applies the leave and arrive text, with `{actor}` placeholders and a disposition verb. Costs: movement points per step, run at twice walk, and an overweight multiplier. The formula is at `Libraries/MUDTracker.java:1779-1784` and `Libraries/MUDFight.java:3437-3441`.
6. **NPC wandering.** Pick a random valid exit under the same gates: area limits, an avoid set, no owned doors, and optionally never the player's room. For a large world, use the thin-room idea: generate rooms only near the player.
7. **Name resolution.** One resolver with an ordered list of scopes: direction, room ID, `#local`, NPC, item, shop stock, area. For one player, drop the other players, the time slices and the caches. A single-player world is small enough to scan. Keep the substring matching and the colour stripping.
8. **Environment tags.** Encode water, air, underwater and climbable as room and exit tags. Refuse entry without a boat, swimming, flight or climbing. Use the half-carry-weight limit for swimmers. Make falls a status effect, applied when there is a room below and the subject is not flying.
9. **Route text.** Run-length encode the direction list, as in `getTrailToDescription`. A short sketch:

   ```python
   def describe(dirs):
       out, i = [], 0
       while i < len(dirs):
           j = i
           while j < len(dirs) and dirs[j] == dirs[i]:
               j += 1
           n = j - i
           out.append(f"{n}{dirs[i]}" if n > 1 else dirs[i])
           i = j
       return " ".join(out)
   ```
10. **Tracking as state.** Store the route on the tracking effect, advance it on each step, and recompute only when the target moves. This follows Skill_Track (`Abilities/Skills/Skill_Track.java:375`).
11. **Companions.** A short list of followers that move with the player, with a same-room check, is enough. Ships, ladders, grid layouts and multi-rider mounts can be dropped unless the game needs them.
12. **Drop for single-player.** GOTO bypass, sysop gates, duty-cycled scans, time-limited caches, follower AUTOGUARD logic and the area-group router (unless the world proves huge).

## Coverage

- **Read in full:** `Libraries/MUDTracker.java` (lines 1-4301), `Libraries/interfaces/TrackingLibrary.java` (1-1150), `Libraries/WorldHuntUtils.java` (1-1129) and `Libraries/interfaces/WorldHuntLibrary.java` (1-305).
- **Read in part, to check interactions:** `Behaviors/Mobile.java` (255-375), `Abilities/Skills/Skill_Track.java` (370-380, 496-530, 600-690), `Locales/StdRoom.java` (43-46, 1402-1404, 1912-1921), `Libraries/MUDFight.java` (3426-3470), `core/CMProps.java` (3130-3134), `core/CMLib.java` (1585-1597), `core/CMath.java` (1083-1090), `Libraries/Dice.java` (398-406) and `core/CMClass.java` (2926-2938).
- **Checked by grep:** caller counts and dead-code status for the tracking and hunt methods, and the accessor resolution through `cm.py`.
- **Not checked:** `CMFlagLibrary`, `LegalLibrary`, the ability classes that `move()` and `beMobile` invoke (Skill_Swim, Skill_Climb, Thief_Sneak, Thief_Hide, Power_SuperClimb, Falling, Sinking, WanderHomeLater), `StdThinInstance` and `CMMap` beyond the call lines, and the database navigation loader.
- **Not run.** Every bug above comes from reading the code. Where I did not confirm the effect, I have said so.
