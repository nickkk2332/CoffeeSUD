# Areas: rooms, zones, planets, instances and ships

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b101` (Areas) · 19 files · 10752 lines of Java.

## Summary

An `Area` groups rooms and gives them shared rules: a theme that decides whether magic, superpowers and technology work there (`com/planet_ink/coffee_mud/Areas/interfaces/Area.java:705-717`), plus a climate, an atmosphere, a time clock, a currency, price and budget settings, sub-operators, and its own behaviours, effects and scripts. Areas nest in parent and child trees, and most rules are resolved by walking up the parent chain. On top of the base `StdArea` the package adds thin areas that load rooms from the database on demand, grid areas addressed by X/Y coordinates, planets and moons that are also space objects, player-owned and party instances that are copied when a group enters, auto-generated instances built from XML templates, and boardable and space ships that dock to a room and carry their own rooms. Each area has a state (ACTIVE, PASSIVE, FROZEN, STOPPED) that decides whether the engine ticks it, which is how empty places stop costing time. The most borrowable parts are the theme gate, the state machine, the copy-on-enter instance, and the docking trick that rewires exits.

## Key files and types

All paths are relative to the CoffeeMud folder.

| File | Role | Lines |
|---|---|---|
| com/planet_ink/coffee_mud/Areas/StdArea.java | Base area: rooms, rule inheritance, message gating, ticking, stats, parent/child links | 2907 |
| com/planet_ink/coffee_mud/Areas/StdBoardableShip.java | Ship that is both an area and a boardable item; docking; relays messages to its host area | 2348 |
| com/planet_ink/coffee_mud/Areas/StdThinInstance.java | Instance parent and child areas: copy the room on enter, redirect a group, flush or reset | 935 |
| com/planet_ink/coffee_mud/Areas/interfaces/Area.java | The Area contract plus constants: themes, flags, 31 stats, states, enumerators | 918 |
| com/planet_ink/coffee_mud/Areas/StdAutoGenInstance.java | Instance generated from an XML template on first entry, sized by the group's levels | 913 |
| com/planet_ink/coffee_mud/Areas/StdSpaceShip.java | Space ship: stale air and vacuum, artificial gravity, mass, forced theme TECHNOLOGY | 895 |
| com/planet_ink/coffee_mud/Areas/StdGridArea.java | Grid of rooms with room IDs encoded from X and Y | 274 |
| com/planet_ink/coffee_mud/Areas/StdPlanet.java | Planet (space object, not thin) with random coordinates and radius | 229 |
| com/planet_ink/coffee_mud/Areas/StdThinPlanet.java | Planet whose rooms are thin (loaded on demand) | 196 |
| com/planet_ink/coffee_mud/Areas/StdThinArea.java | Database-backed area that loads rooms when they are needed | 194 |
| com/planet_ink/coffee_mud/Areas/PlayerInstanceArea.java | Player-owned instance area whose rooms are saved in the player's data | 192 |
| com/planet_ink/coffee_mud/Areas/SubThinInstance.java | Instance derived from a named super area; one shared child | 141 |
| com/planet_ink/coffee_mud/Areas/StdThinGridArea.java | Thin version of the grid area | 136 |
| com/planet_ink/coffee_mud/Areas/StdTimeZone.java | Base for planets and zones: each owns a time clock | 80 |
| com/planet_ink/coffee_mud/Areas/Moon.java | Moon: thin planet with a smaller radius range and its own names | 73 |
| com/planet_ink/coffee_mud/Areas/interfaces/AutoGenArea.java | Contract for auto-generated areas (generator path, variables, reset) | 70 |
| com/planet_ink/coffee_mud/Areas/Planet.java | Planet: thin planet with a wider radius range | 56 |
| com/planet_ink/coffee_mud/Areas/interfaces/SubArea.java | Contract for areas derived from a super area | 36 |
| com/planet_ink/coffee_mud/Areas/interfaces/GridZones.java | Contract for grid areas and rooms, with the `XYVector` coordinate holder | 159 |

## How it works

### 1. Data model

- **Names and text.** `_name`, `description`, `miscText`, `archPath`, `imageName` (`com/planet_ink/coffee_mud/Areas/StdArea.java:48-52`).
- **Rule fields.** `theme` and `atmosphere` default to the INHERIT sentinels, `Area.THEME_INHERIT` (0) and `Places.ATMOSPHERE_INHERIT` (-1). Resolved values are cached in `derivedTheme`, `derivedAtmo` and `derivedClimate` (`StdArea.java:54-59`).
- **Passive lapse.** `passiveLapseMs` defaults to `DEFAULT_TIME_PASSIVE_LAPSE = 60*1000*30`, which is 30 minutes (`Area.java:812`, `StdArea.java:62`).
- **Rooms.** `properRooms` is a `STreeMap` keyed by room ID, using a case-insensitive `RoomIDComparator` (`StdArea.java:79`, `Area.java:896-903`). `properRoomIDSet` lists every room ID the area owns, loaded or not (`StdArea.java:80, 421-426`). `metroRoomIDSet` also holds the IDs of child areas' rooms and is pushed to every ancestor (`StdArea.java:81, 2062-2076`).
- **Tree.** `parents` and `children` are `SLinkedList<Area>` (`StdArea.java:82-83`). Behaviours, effects, scripts and sub-operators are `SVector`s (`StdArea.java:84-87`).
- **Stats cache.** Statistics are cached in `Resources` under `STATS_<NAME>`, built by `DefaultAreaIStats.build` (`StdArea.java:1780-1796`). The builder file was not read.
- **Ships.** `StdBoardableShip` keeps its interior in a sorted `SVector<Room> myRooms` and finds rooms by binary search on the room ID (`com/planet_ink/coffee_mud/Areas/StdBoardableShip.java:58, 1698-1728`).

### 2. Rule inheritance up the parent chain

Each rule follows one pattern: `getX()` returns the resolved value and `getRawX()` returns the local value. A cached `derivedX` is filled by walking the parents with an explicit stack. The theme version is representative (`com/planet_ink/coffee_mud/Areas/StdArea.java:498-516`):

```java
if (derivedTheme != THEME_INHERIT) return derivedTheme;
// walk this area and its parents until one has a non-inherited theme
derivedTheme = CMProps.getIntVar(CMProps.Int.MUDTHEME);   // engine default, line 514
```

Currency, prejudice factors, ignore mask, item pricing adjustments, budget, devalue rate, inventory reset rate, climate and atmosphere use the same walk (`StdArea.java:164-175, 791-810, 833-844, 867-878, 901-912, 936-947, 982-993, 1043-1073, 189-220`). Changing a parent or the area's own state clears the caches: `setAreaState` (`StdArea.java:566-568`), `addParent` and `removeParent` (`StdArea.java:2650-2652, 2672-2674`), `setMiscText` (`StdArea.java:1019-1021`).

Children and parents:

- `addChild` refuses a child with the same name and gives the child this area's clock (`StdArea.java:2531-2549`).
- `canChild` and `canParent` walk the links so that cycles are refused. Boardable areas (ships) are refused (`StdArea.java:2560-2575, 2680-2695`).
- Messages go up, not down. `okMessage` asks each parent (`StdArea.java:1133-1140`), and `executeMsg` forwards to parents (`StdArea.java:1263-1267`). Nothing flows from a parent to its children.

### 3. Message flow: okMessage and executeMsg

The engine uses the same two-phase flow as the rest of the game. `StdArea.okMessage` (`com/planet_ink/coffee_mud/Areas/StdArea.java:1093-1230`) does, in order:

1. Asks each behaviour, script and effect for a veto (`1096-1113`).
2. If the source is a player, sets `lastPlayerTime` to now. A PASSIVE area becomes ACTIVE on ENTER, LEAVE or FLEE (`1114-1122`).
3. In a FROZEN or STOPPED area, or where `CMLib.flags().allowsMovement(this)` is false, it refuses ENTER, LEAVE and FLEE (`1124-1132`).
4. Asks each parent for a veto (`1133-1140`).
5. Applies the theme gate (`1141-1227`, below).

`executeMsg` (`1233-1268`) runs behaviours, scripts and effects, removes a sub-operator who retires (`1260-1261`), then forwards to the parents.

### 4. Theme gate

Theme bits (`Area.java:707-717`): FANTASY = 1 (magic), TECHNOLOGY = 2, HEROIC = 4 (superpowers), SKILLONLY mask = 8. ALLTHEMES is 7. Combinations have names in `THEME_NAMES` (`Area.java:719`) and readable phrases in `THEME_PHRASE_EXT` (`Area.java:735-751`). Races declare their own theme sets, for example Human is `FANTASY | TECHNOLOGY` (`com/planet_ink/coffee_mud/Races/Human.java:138`).

The gate in `StdArea.okMessage` (`StdArea.java:1141-1227`):

- **Magic.** If the theme is not zero and lacks FANTASY, any message whose source, target or others carry `MASK_MAGIC` is refused with "Magic doesn't seem to work here." when the actor is in the area (`1143-1160`).
- **Superpowers.** If the theme lacks HEROIC, an Ability classed `ACODE_SUPERPOWER` is refused with "Powers don't seem to work here." (`1162-1179`).
- **Technology.** The technology test sits in an `else` attached to the HEROIC test (`1180-1181`):

```java
if (!CMath.bset(getTheme(), Area.THEME_HEROIC)) { /* superpower check */ }
else
if (!CMath.bset(getTheme(), Area.THEME_TECHNOLOGY)) { /* technical tools refused */ }
```

Because of this nesting, technology is only refused when HEROIC is set (see Problems, item 7). When refused, the text is "Technology doesn't seem to work here." (`1221`). A whitelist passes shop and container verbs (BUY, BID, GET, PUT, REMOVE and others, `1185-1204`). POWERCURRENT is refused outright in the area (`1205-1209`).

Ships apply their own magic gate with different wording: "Magic doesn't seem to work here." or "...there." depending on whether the target is in the ship's own area (`com/planet_ink/coffee_mud/Areas/StdBoardableShip.java:751-774`).

### 5. Ticking and passive lapse

- Only `Tickable.TICKID_AREA` does anything (`StdArea.java:1277-1282`).
- **Lapse.** If the area is ACTIVE and more than `passiveLapseMs` has passed since `lastPlayerTime`, it becomes PASSIVE (`StdArea.java:1284-1290`). If the `PASSIVEAREAS` security flag is disabled, `lastPlayerTime` is reset instead, unless the area is an instance child (`1286-1289`).
- **Sub-ticks, in order.** Climate (`1292`), time clock (`1294`), behaviours (`1296-1303`), scripts (`1305-1312`), effects (`1314-1322`). An effect whose tick returns false is uninvoked.
- **Tick length.** `TIME_TICK = 4000` ms (`com/planet_ink/coffee_mud/core/CMProps.java:681`). A mud hour is `MILLIS_PER_MUDHOUR = 600000` ms by default (`CMProps.java:682`), which is 150 ticks.
- **Shared clock.** `DefaultTimeClock.tick` returns early unless a mud hour has passed since the last tick (`com/planet_ink/coffee_mud/Common/DefaultTimeClock.java:1087-1089`). Several areas sharing one clock therefore do not multiply the elapsed time.
- **Climate.** `DefaultClimate.tick` runs weather only when the ticking object is an Area (`com/planet_ink/coffee_mud/Common/DefaultClimate.java`, `tick`).

### 6. Physical effects of an area

`affectPhyStats` (`StdArea.java:1329-1360`) copies the area's senses (minus UNLOCATABLE and CAN_NOT_SEE) and disposition (minus SLEEPING and HIDDEN) into every affected room. A room with a sky becomes IS_DARK (`1337-1346`) when the weather is BLIZZARD or DUSTSTORM, unless `DARKWEATHER` is disabled, or when it is night, unless `DARKNIGHTS` is disabled, and the room has no light source. The area's weight is also added to the room (`1350`), with a comment that reads "well, that's weird". Only bubble effects are passed on (`1355-1357`).

### 7. Room IDs and grids

- **Default IDs.** `getNewRoomID` gives `<AREA>#<n>` (`StdArea.java:624-679`). It scans existing numbers from the lowest to highest plus 1000, and falls back to a random number (`678`).
- **Grid IDs.** `roomIDFromPos` (`com/planet_ink/coffee_mud/Areas/StdGridArea.java:131-139`) writes y in decimal when x is 0. Otherwise it writes x followed by y zero-padded to `yLength`, which is 3 for a 100-row grid. So (x=1, y=5) becomes `1005` and (x=0, y=5) becomes `5`. `getRoomXY` (`151-180`) decodes by splitting off the last `len` digits.
- **Grid child selection.** `getNewRoomID` (`StdGridArea.java:63-129`) first tries the cell in the travel direction (`70-97, 101-116`). Otherwise it takes the first empty cell in scan order (`118-126`), or returns `""` if the grid is full (`128`).

### 8. Thin areas and lazy loading

- `StdThinArea.getRoom` (`com/planet_ink/coffee_mud/Areas/StdThinArea.java:71-115`) loads a room that is not in memory. It calls `DBReadRoomObject`, `DBReadRoomExits` and `DBReadContent`, then `fillInAreaRoom`, sets expiry to `WorldMap.ROOM_EXPIRATION_MILLIS = 2500000` ms (about 41.7 minutes, `com/planet_ink/coffee_mud/Libraries/interfaces/WorldMap.java:48`), and sends a `MSG_NEWROOM` event from a temporary mob named "the wind" (`101-110`).
- `getRandomProperRoom` (`StdThinArea.java:127-151`) picks from the cached set when cached rooms are more than 30% of all rooms (`132-137`). Otherwise it picks from the full ID set.
- `getMetroMap` loads rooms until about 10% of the area is in memory (`StdThinArea.java:180-193`).
- Enumerating a room through `RoomIDEnumerator` loads it and extends its expiry by 10 minutes (`Areas/interfaces/Area.java:819-852`, the extension at `848-849`).
- `StdThinGridArea` (`com/planet_ink/coffee_mud/Areas/StdThinGridArea.java:61-82`) does the same without the cache heuristic.

### 9. Instances: copy on enter (StdThinInstance)

`StdThinInstance` (`com/planet_ink/coffee_mud/Areas/StdThinInstance.java`) is the model for private copies of a map.

- **Flags.** The parent is `FLAG_THIN | FLAG_INSTANCE_PARENT` (`47`). A child is named `<n>_<ParentName>` and set to `FLAG_INSTANCE_CHILD` (`createRedirectArea`, `740-764`, name at `752`, flag at `753`).
- **Redirect on enter.** In `okMessage` (`827-932`), a player entering a room of the parent who does not start there is assigned to a child area.
  - If someone in the player's group already has a child, the player joins it (`837`, search in `869-910`).
  - If the player is on a ship, the whole crew joins a child when a player is aboard (`838-864`).
  - Otherwise a new child is made.
  - The enter message is then retargeted to the same room in the child (`915`). If that room is not there, a random accessible room is tried, up to 1000 times (`916-928`).
- **Materialising rooms.** `getRoom` (`103-202`) copies the parent's room from the database. Exits to rooms in the same parent area are rewired to the child's own room IDs with `convertToMyArea` (`166-169`). Missing neighbours become `ThinRoom` placeholders (`175-177`).
- **Flush.** Every `childCheckDown` ticks (`57`, set to one mud hour at `533-535`), the parent flushes each child that is not ACTIVE (`541-549`). `flushInstance` (`363-480`):
  - Builds a protected set: players in the child, all players in the area, player corpses, and owned ships worth more than 2 gold (`getProtectedSet`, `226-309`).
  - Moves mobs to their start room (`378-385`).
  - Moves items to outside rooms with `CMLib.tracking().getRadiantRoomTarget`. Boats prefer water rooms (`397-419`).
  - Sends `MSG_EXPIRE` to mobs and rooms (`423-436`), empties and destroys each room (`439-471`), then deletes the area (`473-474`).
- **Reset.** A player who says "RESET INSTANCE" at an entrance resets the instance (`641-675`). `resetInstance` (`555-638`) returns the occupants to the exit room, then flushes. The messages are "The instance has been reset.", "The instance failed to reset." and "You must be at an entrance to reset the area." (`668-674`). `setStat` also accepts RESET_INSTANCE and FLUSH_INSTANCE (`779-786`). TIMEOUT_INSTANCE sets `childTimeout`, which counts down in `tick` and resets the child at zero (`525-530`).
- **Stats.** The parent's statistics are the average of its children's statistics (`687-733`, division by the child count at `722`).
- **Passive.** Every `okMessage` on a parent instance sets it PASSIVE (`824-826`), which also clears its derived caches (see Problems, item 18).

### 10. Auto-generated instances (StdAutoGenInstance)

`StdAutoGenInstance` extends `StdArea` and builds its child from an XML template with the random-area generator (`com/planet_ink/coffee_mud/Areas/StdAutoGenInstance.java`). The generator itself is in the random-area bucket and is only described here.

- **Template.** The generator path defaults to `randareas/example.xml` (`63`). Variables live in `varMap` (`64`). `setAutoGenVariables(String)` parses `KEY=VALUE` pairs and turns `PASSIVEMINS` into the passive lapse (`894-912`).
- **Adult gating.** A monster-only group is refused entry with a message that an adult must accompany them (`428-438`).
- **Group match.** If a member of the group already has a child, the group joins it (`445-459`). Otherwise a child is created (`498-744`):
  - `copyOf()` clones the template, and `cloneFix` copies behaviours, effects and scripts but not rooms (`StdArea.java:707-746`).
  - The child is named `<n>_<Name>` from an `AtomicInteger` (`508`) and gets `FLAG_INSTANCE_CHILD` with `FLAG_INSTANCE_PARENT` cleared (`509-510`).
- **Level variables.** Values are passed to the template as `level, lowest, median, average, highest, total, groupSize` (`584`). Defaults are `AREASIZE` = 50 (`600-601`), `LEVEL_RANGE` = `(level-4)?level` (`602-603`) and `AGGROCHANCE` = the level (`604-605`). The median is `sortedLevels[len/2]`, but the array is never sorted (see Problems, item 3).
- **Choosing a template.** The `AREA_ID` candidates come from variables or from `area` pieces with `INSERT` references (`521-564`). Those whose requirements are met are kept (`607-626`). One is chosen at random (`628`). A `THEME` is picked from the unfilled requirements if none is set (`644-650`). Missing requirements are reported (`653`).
- **Gate tags.** The room the group came from, and its exit direction, are written into the variables as `ROOMTAG_GATEEXITROOM`, `ROOMTAG_GATEEXITCLASS` and `ROOMTAG_NODEGATEEXIT` (`665-673`). Neighbours of a random inner room are tagged the same way (`674-696`). This lets the generator link the new area back to the arrival room.
- **Generation.** `CMLib.percolator().fillInArea` builds the rooms (`699`), then `postProcess` (`704`). Players see "Please stand by..." (`663`). The new area becomes ACTIVE (`715`). Room `<name>#0` gets an exit back to the arrival room (`726-743`).
- **Re-entry.** A later entry is redirected to the room whose exit points back toward the arrival room, or to a room with the ambiance tag `#GATE<dir>` (`745-783`).
- **Expiry.** Every mud hour, children that are not ACTIVE and have no player inside are destroyed (`145-208`).
- **Reset.** The parent resets all children (`290-309`). A child moves its occupants to the return room, then destroys itself (`312-363`).

### 11. Player instances and sub-instances

- **PlayerInstanceArea** (`com/planet_ink/coffee_mud/Areas/PlayerInstanceArea.java`) is a child area owned by a player and stored in that player's data under `PLAYERINSTANCE`. A room is unpacked from XML on first access (`136-185`), with ROOMID and RAREA overwritten (`161-168`). It tracks rooms with changed items in `dirtyFlags` on GET, DROP, PUSH, PULL and ITEMGENERATED (`108-132`). Dirty rooms are saved on EXPIRE (`93-107`) and all at once on SHUTDOWN (`78-89`).
- **SubThinInstance** (`com/planet_ink/coffee_mud/Areas/SubThinInstance.java`) takes its super area from the name suffix after `_`, or after a space (`76-78`). `createRedirectArea` makes one shared child that returns `this` (`113-140`), so every entrant shares one instance.

### 12. Boardable ships and docking

`StdBoardableShip` (`com/planet_ink/coffee_mud/Areas/StdBoardableShip.java`) implements `Area`, `Boardable` and `PrivateProperty` (`39`). Its interior is an area, and `shipItem` is the item it sits in (`65`, `setDockableItem` at `147-152`).

- **Delegation.** Theme, climate, time and atmosphere come from the area the item is in (`117-145, 209-214, 377-393`). When the ship is not in a room, its theme is ALLTHEMES (`383, 392`), its time is the global clock (`144`), and its climate is taken from the first world area (`123`).
- **Docking.** `dockHere` (`1225-1246`) rewires every interior exit that leads outside the ship to the dock room (`1236-1242`). `unDock` (`1249-1275`) clears those doors. The boundary exits are remembered in `shipExitCache` (`1230, 1242, 1272`), which only `StdSpaceShip` reads (`460, 759`).
- **Message relay.** HUH, COMMAND and similar messages go to the item (`775-786`). Other messages from inside the ship are offered to the area the item is in (`787-797`). `executeMsg` forwards the same way (`976-987`), and a LIFE message removes a monster whose siege item is dead (`960-974`).
- **Activity.** A player acting inside wakes the host StdArea if it is PASSIVE (`729-746`).
- **Owned ships.** An item dropped in an owned ship loses its expiry, and rejuvenation is turned off (`932-948`). An EXPIRE message on an item in an owned ship is refused (`688-692`).
- **Robbery.** GET, PUSH and PULL inside a ship go through `CMLib.law().robberyCheck` (`800-811`).
- **Limits.** A ship has no children (`canChild` returns false, `1911-1914`) and does not record metro rooms (`1814-1831`). `canParent` returns true (`2035-2037`).

### 13. Space ships: air, gravity and mass

`StdSpaceShip` (`com/planet_ink/coffee_mud/Areas/StdSpaceShip.java`) extends `StdBoardableShip`.

- **Theme.** Always `THEME_TECHNOLOGY` (`355-364`).
- **Constants.** `STALE_AIR_INTERVAL = 5 * 60 * 1000` (`41`) and `STALE_WARN_INTERVAL = 5 * 30 * 1000` (`43`). Vacuum is `RawMaterial.RESOURCE_NOTHING`, which is 0 because `MATERIAL_UNKNOWN` is 0 (`com/planet_ink/coffee_mud/Items/interfaces/RawMaterial.java:127, 366`). Air is `MATERIAL_GAS | 181` (`RawMaterial.java:547`).
- **Air leaks.** `doAtmosphereChanges` (`756-805`) takes each open boundary exit and spreads the outside room's air from the boundary room with `moveAtmosphereOut` (`759-768`). That method walks open exits within the same area (`595-605`). It adds vacuum rooms to `staleAirList` (`586-588`) and removes air rooms from it (`591-592`). The walk stops at the first room whose air changes (`593-594`), so each boundary moves at most one room per area tick.
- **Stale check.** Every 5 minutes, `doStaleCheck` (`609-626`) lists rooms that have inhabitants or no air as stale (`615-619`). Rooms that were already listed become vacuum (`622`), with the comment "WE NOW HAVE A VACUUM HERE!!!".
- **Warnings.** Every 2.5 minutes, PC inhabitants of stale rooms that still have air are told "The <gas> is seeming a bit stale." (`771-794`, text at `790`).
- **Air refresh.** The AIRREFRESH technology command (`428-469`) clears `round(staleCount * pct)` stale rooms, then sets a random room and every boundary room to the new air. `changeRoomAir` (`549-574`) prints "<gas> rushes out of the room." or "rushes into the room." (`557, 563`). A room whose air matches the ship's own default is set back to inherit (`567-568`).
- **Gravity.** `doGravityChangesTick` (`644-754`) takes the last engine that is activated with thrust above 1.0 (`646-651`). Otherwise it scans the electronics registry (`656-665`). Gravity exists when the ship is in the air or has ARTI_GRAV, is docked, or has thrust above 0.2 (`667-671`). ARTI_GRAV decays every two ticks (`673-677`).
- **Gravity change.** When the state changes, the game sends "You feel the pull of gravity returning." or "You no longer feel the pull of gravity." through a veto check across rooms (`683-712`). It then sets NO_GRAVITY (`713`), and invokes the GravityFloat ability on every mob and item (`738-749`). A very high thrust also sends an unconsciousness message (`692-696`).
- **Mass.** `getMass` (`285-313`) sums the weights of items and mobs in the ship's rooms. The result is cached until a GET or DROP (`421-422`).
- **Hooks.** Standing, dismounting, dropping and throwing under NO_GRAVITY re-apply the float through trailer runnables (`476-518`).

### 14. Planets, moons and time zones

- **StdTimeZone** (`com/planet_ink/coffee_mud/Areas/StdTimeZone.java`) gives an area its own `DefaultTimeClock` (`StdTimeZone.java:43-47`). `addChild` gives children and grandchildren the same clock (`73-78`).
- **StdPlanet** (`StdPlanet.java:48-59`) takes random coordinates in `[0, Long.MAX)` and a radius of `PlanetRadius.dm + nextLong % (PlanetRadius.dm/20)`. The `%` can go negative, so the radius varies both ways around the base (`58`).
- **Planet** (`Planet.java:53`) uses a 30% spread. **Moon** (`Moon.java:52`) uses `MoonRadius` with a 20% spread.
- **Names.** `StdThinPlanet` and `StdPlanet` use "a large planet" (radius at least `SaturnRadius`), "a tiny planet" (at most `MoonRadius`), "a small planet" (below half `PlanetRadius`) and "a planet" otherwise (`StdPlanet.java:124-133`). `Moon.genericName` (`Moon.java:57-71`) has its own steps.
- **Mass.** `getMass` returns `radius * MULTIPLIER_PLANET_MASS` (`StdPlanet.java:159-162`).

### 15. Stats and the save format

- **Codes.** `STDAREACODES` lists 20 codes: CLASS, CLIMATE, DESCRIPTION, TEXT, THEME, BLURBS, PREJUDICE, BUDGET, DEVALRATE, INVRESETRATE, IGNOREMASK, PRICEMASKS, ATMOSPHERE, AUTHOR, NAME, PLAYERLEVEL, PASSIVEMINS, CURRENCY, AFFBEHAV, TAGS (`com/planet_ink/coffee_mud/Areas/StdArea.java:2709-2714`). `getStat` and `setStat` switch on the index (`2737-2892`). PASSIVEMINS is in minutes (`2863-2873`).
- **Save loop.** `getSaveStatIndex` returns `codes.length - xtraValues.length` (`StdArea.java:2706`). `CoffeeMaker` writes only the codes from that index onward (`com/planet_ink/coffee_mud/Libraries/CoffeeMaker.java:5311-5312`, read back at `5760-5761`). So the extension codes are saved by this loop. How the standard codes are written was not traced, because it lies outside this bucket.
- **Ships.** `StdBoardableShip.getSaveStatIndex` returns the full length (`StdBoardableShip.java:2151-2153`), so the same loop writes no ship codes.

## Content worth mining

**Themes.** Area.java lists the bits and their names. The useful table for a game is the combinations in `THEME_PHRASE_EXT` (`Area.java:735-751`): "Fantasy", "Technical", "Heroic & Fantasy", "Fantasy Skills Only", "Tech Skill Only", "Powers only", "Powers & Spells only", "Any skill only" and others. A world region can be set to one of these, and a race or class can carry its own theme mask.

**Area statistics.** The 31 statistics in `Area.java:764-797` are a ready catalogue of per-region facts: POPULATION, MIN_LEVEL, MAX_LEVEL, AVG_LEVEL, MED_LEVEL, AVG_ALIGNMENT, MED_ALIGNMENT, TOTAL_LEVELS, TOTAL_INTELLIGENT_LEVELS, VISITABLE_ROOMS, INDOOR_ROOMS, MIN_ALIGNMENT, MAX_ALIGNMENT, ANIMALS, COUNTABLE_ROOMS, WATER_ROOMS, DESERT_ROOMS, CITY_ROOMS, CAVE_ROOMS, INTELLIGENT_MOBS, MAX_LEVEL_MOBS, HUMANOIDS, GOOD_MOBS, EVIL_MOBS, LAWFUL_MOBS, CHAOTIC_MOBS, BOSS_MOBS, MODE_LEVEL, MODE_ALIGNMENT, AGGRO_MOBS, SHOPKEEPER_MOBS. A single-player game could compute the same for each region and show it on a map or in a "survey" command.

**Short in-world messages** (quoted briefly, all in the source):

| Situation | Text | Location |
|---|---|---|
| Magic refused | "Magic doesn't seem to work here." | `StdArea.java:1157` |
| Powers refused | "Powers don't seem to work here." | `StdArea.java:1175` |
| Technology refused | "Technology doesn't seem to work here." | `StdArea.java:1221` |
| Air leaking out | "<gas> rushes out of the room." | `StdSpaceShip.java:557` |
| Stale air warning | "The <gas> is seeming a bit stale." | `StdSpaceShip.java:790` |
| Gravity off | "You no longer feel the pull of gravity." | `StdSpaceShip.java:689` |
| Gravity back | "You feel the pull of gravity returning." | `StdSpaceShip.java:687` |
| Instance reset | "The instance has been reset." | `StdThinInstance.java:672` |
| Generation | "Please stand by..." | `StdAutoGenInstance.java:663` |
| Bad template ID | "The area id '<id>' has not been defined in the data file." | `StdAutoGenInstance.java:633` |

**Planets and moons.** The three radius bands (5%, 20% and 30% spreads around the base) and the naming steps (`StdPlanet.java:124-133`, `Moon.java:57-71`) are a small generator for a solar system: generate a body, then name it by size.

**Grid room IDs.** The `x` plus zero-padded `y` scheme (`StdGridArea.java:131-139`) is compact and decodes without a table. The same idea works for a Python grid keyed by `(x, y)`.

**Auto-generated templates.** Each template declares `AREA_ID` candidates, `INSERT` references, `THEME` choices, a `LEVEL_RANGE` and gate tags (see section 10). The variables are the practical content: a dungeon can be written once and scaled to the player.

## Notable design ideas

- **Rules are scoped by area and inherited.** Theme, climate, atmosphere, currency, budget and prejudice all resolve up a parent chain with a cached result (`StdArea.java:498-516`). A region can set one value and its children follow.
- **Theme as a veto.** A region bans whole categories of action (magic, superpowers, technology) and says why, in the world's own words. The refusal is one of the veto checks in the message flow, so it applies to every action without special cases.
- **Area state as simulation detail.** ACTIVE, PASSIVE, FROZEN and STOPPED (`Area.java:805-808`) decide whether an area is ticked, and a player entering wakes it (`StdArea.java:1117-1121`). Empty places cost nothing.
- **Copy on enter.** An instance is a copy of the template with its own rooms, and the enter message is retargeted to it (`StdThinInstance.java:915`, `StdAutoGenInstance.java:745-783`). The same map can be private to each group.
- **Protected set on flush.** Before an instance is destroyed, the engine lists what must survive: players, corpses and owned ships (`StdThinInstance.java:226-309`). Items are moved out, not lost.
- **Docking rewires exits.** A ship is an area that connects to the world at a single room by pointing its boundary exits at the dock (`StdBoardableShip.java:1225-1275`). Moving the ship is just rewiring a few doors.
- **Stale air as a spreading process.** Air changes spread through open exits one room per boundary per tick, with a stale list and timed checks (`StdSpaceShip.java:576-626`). This is a cheap model of gradual change.
- **Gravity as a broadcast state machine.** A change is announced to every room, can be vetoed, and then applied to each occupant (`StdSpaceShip.java:644-754`).
- **Level-scaled generation.** The generator receives the group's level statistics as variables (`StdAutoGenInstance.java:584-605`). The intent is clear even though the sorting step is missing (see Problems).
- **Lazy rooms with expiry.** Rooms are read from the database when needed and expire from memory after about 41 minutes (`WorldMap.java:48`, `StdThinArea.java:100`).

## Problems and cruft

Each item below was found by reading the code. None was run.

1. **Ship RADIUS stat is wired to the drag coefficient.** `LOCAL_CODES={"RADIUS","AUTHOR"}` (`com/planet_ink/coffee_mud/Areas/StdSpaceShip.java:840`). The local `switch` returns `getOMLCoeff()` for index 0 (RADIUS) and `radius()` for index 1 (`867-875`). `setStat` does the same (`887-891`). So the "RADIUS" stat reads and writes the coefficient, and the radius is only reachable through `setRadius`. The "AUTHOR" entry is unreachable, because the inherited codes handle AUTHOR first.

2. **StdBoardableShip.setStat is off by one.** `com/planet_ink/coffee_mud/Areas/StdBoardableShip.java:2209-2264`. There is no `case 6`, so AUTHOR is ignored. `case 7` (the NAME code) calls `setAuthorID`, `case 8` (the ATMOSPHERE code) calls `setName`, and `case 9` can never run. `getStat` (`2198-2203`) uses 6, 7 and 8 correctly. `StdSpaceShip` inherits the bug.

3. **Levels are never sorted.** `StdAutoGenInstance.java:567-582` fills `levels` from a `HashSet` of followers (`568`) and reads `sortedLevels[0]`, the median and `sortedLevels[len-1]` without sorting. The "lowest" and "highest" values are therefore arbitrary for mixed-level groups.

4. **Map mutated during iteration.** `setAutoGenVariables(String)` removes `PASSIVEMINS` from `varMap` while iterating its `keySet()` (`StdAutoGenInstance.java:898-911`). `CMParms.java:2364-2370` builds that map as a `Hashtable`, whose iterators are fail-fast. A `ConcurrentModificationException` is thrown if `PASSIVEMINS` is not the last key visited. Whether it fires depends on hash order.

5. **Ship item ticked twice per area tick.** `StdSpaceShip.tick` calls `super.tick` (`StdSpaceShip.java:810`), which ticks the ship item (`StdBoardableShip.java:1061-1062`). It then ticks the item again (`StdSpaceShip.java:817-819`). The effect depends on what the item does per tick, which was not traced.

6. **Gravity flag changed even when vetoed.** `StdSpaceShip.java:713` sets NO_GRAVITY before the veto result is read at `723-726`. A vetoed change returns without applying the effect. On the next tick the flag already matches the new state, so nothing retries it unless something else clears the flag.

7. **Technology gate is nested under the HEROIC test.** `StdArea.java:1162-1181`. A FANTASY-only area (theme 1) therefore does not refuse technical tools through this path. The string "Technology doesn't seem to work here." (`1221`) is the only technology gate found in the source tree, so nothing else compensates.

8. **Duplicate-room check compares the wrong field.** `StdBoardableShip.java:1647-1654`. The test at `1649` compares `R2.ID()`, the class name, with the room ID. It should compare `R2.roomID()`. Two rooms with the same ID are therefore both inserted, and the replacement branch never runs. The effect at runtime was not confirmed.

9. **Climate shared by all space ships.** `StdSpaceShip.java:45` declares `protected static Climate climateObj`. `setClimateObj` (`81`) changes it for every ship. `destroy` (`321`) sets it to null, so destroying one ship resets the shared climate.

10. **Initial activity timer has an inverted sign.** `StdArea.java:63` sets `lastPlayerTime = now + (passiveLapseMs - 30000)`. The lapse test is `now - lastPlayerTime > passiveLapseMs` (`1284`). The first passive check therefore happens about 59.5 minutes after construction, not about 30 seconds. Intent not verified.

11. **Lock on a fresh string.** `StdArea.java:1787` synchronizes on `("STATS_" + Name())`, a new object on every call, so the lock excludes nothing. `StdThinInstance.java:696` and `StdAutoGenInstance.java:111` use `CMClass.getSync` for the same purpose. The effect is duplicate statistics builds under concurrency, which is harmless but wasteful.

12. **Non-atomic counter.** `StdThinInstance.java:56` declares `volatile int instanceCounter`, incremented with `++` at `752`. `StdAutoGenInstance.java:60` uses `AtomicInteger`.

13. **Dead code and cache misses in StdAutoGenInstance.** The `anyInside` test (`StdAutoGenInstance.java:173-174`) means the loop at `190-194` never runs. For a child, `getAreaIStats` returns the parent's cached statistics (`105-109`), and otherwise rebuilds them on every call without caching (`119-121`).

14. **Undocked ship climate is arbitrary.** `StdBoardableShip.java:121-123` takes the climate of `CMLib.map().areas().nextElement()`, the first area in the world map.

15. **AGGROCHANCE default is a level.** `StdAutoGenInstance.java:604-605` sets `AGGROCHANCE` to the player's level. A level is not a percentage, and the consumer of this variable was not checked.

16. **Typos and leftovers.** "gargatuan" (`Moon.java:60`), a TODO about filling a moon when first hit (`Moon.java:53`), and a `PlayerInstanceArea.getAreaIStats` override that only calls `super` (`PlayerInstanceArea.java:187-191`).

17. **Shared mutable direction object.** `StdPlanet.java:43` declares a static `emptyDirection`, and `direction()` returns it (`75-77`). A caller that changes it changes it for every planet. `StdPlanet.setDirection` discards its argument (`81-83`), while `StdThinPlanet.setDirection` keeps it (`107-109`).

18. **Performance.** `StdThinArea.getRandomProperRoom` calls `getCachedRoomnumbers()` (`StdThinArea.java:132`), which rebuilds a set from every loaded room on each call (`StdArea.java:430-442`). `StdThinGridArea.getMetroMap` loads `minimum` rooms (`StdThinGridArea.java:122-123`), while `StdThinArea` loads `minimum - numCached` (`StdThinArea.java:186-187`). Parent instances call `setAreaState(PASSIVE)` on every message (`StdThinInstance.java:824-826`), which clears the derived caches (`StdArea.java:566-568`).

19. **Unclear population line.** `StdArea.java:1828-1829` prints "Population: 0" only when `roomCountAllAreas()/2 < properRooms.size()`. The intent is not clear from the code.

20. **Multiplayer assumptions throughout.** Group redirect (item 9 of How it works), protected sets, ship crew following, player-presence wakeups, and adult gating all assume other people are present. In a single-player game each needs a different rule (see Takeaways).

## Takeaways for a Python SUD

**Borrow the theme gate.** Represent a region's rules as a set of flags, resolve them up a parent chain, and ask one question per action. A minimal version:

```python
from enum import Flag, auto

class Theme(Flag):
    MAGIC = auto()        # FANTASY bit, Area.java:709
    TECH = auto()         # TECHNOLOGY bit, Area.java:711
    POWERS = auto()       # HEROIC bit, Area.java:713
    SKILL_ONLY = auto()   # SKILLONLY mask, Area.java:717

class Area:
    def __init__(self, name, parent=None, theme=None):
        self.name, self.parent, self.theme = name, parent, theme
        self.rooms = {}

    def effective_theme(self):
        a = self
        while a is not None:
            if a.theme is not None:
                return a.theme
            a = a.parent
        return Theme.MAGIC | Theme.TECH | Theme.POWERS  # world default

    def refusal(self, kind):
        t = self.effective_theme()
        need = {"magic": Theme.MAGIC, "power": Theme.POWERS, "tech": Theme.TECH}[kind]
        if need not in t:          # checked independently: the nesting bug is avoided
            return {"magic": "Magic doesn't seem to work here.",
                    "power": "Powers don't seem to work here.",
                    "tech": "Technology doesn't seem to work here."}[kind]
        return None
```

Each action calls `refusal` before it runs, and a non-None result stops it and is printed. This replaces the two-phase `okMessage` and `executeMsg` with a simpler validate-then-apply pair. Keep the parent walk and the caches.

**Borrow the state machine for simulation detail.** Give each region ACTIVE, PASSIVE, FROZEN or STOPPED. Only ACTIVE regions run their NPC routines and environment effects each tick. Make the player's current region ACTIVE, neighbours PASSIVE after a few turns, and everything else FROZEN to disk. The "last visited" timer replaces `lastPlayerTime`.

**Borrow docking for vehicles.** A boat or wagon can be a small region whose boundary exits point at a host room. `dock(vehicle, room)` retargets boundary exits, and `undock` clears them. Keep the list of boundary exits, as `shipExitCache` does (`StdBoardableShip.java:1230, 1242`), so updates touch only the boundary.

**Borrow copy-on-enter with a level-scaled template for procedural dungeons.** On the first entry to a generated dungeon, build the rooms from a template with the player's level in its variables (`StdAutoGenInstance.java:584-605`). Keep the result and expire it after a period without the player (`StdAutoGenInstance.java:145-208`). Do not copy the multiplayer parts: no group match, no crew following, and no protected set beyond the player's own items.

**Borrow the atmosphere spread with a cap.** Vacuum or gas spreading through open exits, with at most one room changing per source per tick (`StdSpaceShip.java:593-594`), is a cheap way to model a hazard that moves gradually. It is optional for a text game.

**Drop for a single-player game.** Thin loading only matters for a very large world; a single save file is simpler. Drop the instance redirect, the child-per-party model, PlayerInstanceArea's per-player storage, the protected sets, the adult gating and robbery checks in ships (`StdBoardableShip.java:800-811`). Replace the group-based parts with "the player's own copy".

**Simplify.** One clock per world, advanced once per in-game hour, as `DefaultTimeClock` does (`Common/DefaultTimeClock.java:1087-1089`). One stats function computed on demand per region. Keep the statistics catalogue (section "Content worth mining") as a set of named fields.

**Fix while borrowing.** Sort the levels before taking statistics (item 3). Apply the tech check independently of the heroic check (item 7). Do not keep static state shared across instances (item 9).

## Coverage

- **Read in full (19 of 19):** StdArea.java (2907 lines), StdBoardableShip.java (2348), StdThinInstance.java (935), interfaces/Area.java (918), StdAutoGenInstance.java (913), StdSpaceShip.java (895), StdGridArea.java (274), StdPlanet.java (229), StdThinPlanet.java (196), StdThinArea.java (194), PlayerInstanceArea.java (192), interfaces/GridZones.java (159), SubThinInstance.java (141), StdThinGridArea.java (136), StdTimeZone.java (80), Moon.java (73), interfaces/AutoGenArea.java (70), Planet.java (56), interfaces/SubArea.java (36). The last six were read with imports, licence headers and blank lines filtered out; no code lines were removed.
- **Skimmed or checked by search, outside the bucket:** `Common/DefaultTimeClock.java` (tick guard, line 1084-1089), `Common/DefaultClimate.java` (tick), `core/CMParms.java` (`parseEQParms` returns a `Hashtable`, lines 2312-2370), `core/CMProps.java` (`TIME_TICK`, `MILLIS_PER_MUDHOUR`, lines 681-682), `Libraries/interfaces/WorldMap.java` (line 48), `Items/interfaces/RawMaterial.java` (constants, lines 127, 366, 547), `core/interfaces/Places.java` (constants), `Libraries/CoffeeMaker.java` (save loops, lines 5311-5312, 5760-5761), `Races/Human.java` (line 138), and a search for theme uses across the tree.
- **Not followed:** the room and grid classes in Locales (for `GridLocale`), the random-area generator and percolator (random-area bucket), `DefaultAreaIStats.build`, `CMLib.tracking().getRadiantRoomTarget`, `CMLib.law().robberyCheck`, and the ship electronics registry used by gravity.
- **Skipped in the bucket:** none.
