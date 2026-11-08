# Space, galaxy map and technology power

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b035` (Space, galaxy map and technology power) · 7 files · 4691 lines of Java.

## Summary

Two libraries run the non-combat world physics. `CoffeeDark` (the `GalacticMap` service) keeps every space object in a 3D spatial index, moves ships each tick under gravity from planets and stars, detects collisions, plots routes around obstacles, computes intercepts and orbits, and divides the galaxy into a grid of named sectors. `GroundWired` (the `TechLibrary` service) groups electronic components into circuits by location and, each tick, shares power from generators and batteries among the panels on each circuit. The same library holds the manufacturers and a global tech level that give items their make and era. `SpaceObject` defines the unit system (decameters, speeds per tick) and the distance, mass, velocity and g-force constants. The physics is a simple tick model that could be borrowed, but several space-math routines (intercept, safe trajectory, sector map) contain defects that are described under Problems and cruft.

## Key files and types

| File | Role | Lines |
|---|---|---|
| com/planet_ink/coffee_mud/Libraries/CoffeeDark.java | `GalacticMap` implementation: spatial index, space tick, gravity, collisions, steering, routing, intercept, orbits, sectors | 1785 |
| com/planet_ink/coffee_mud/Libraries/GroundWired.java | `TechLibrary` implementation: circuit registry, per-tick power pass, battery seeking, manufacturers, tech levels | 1091 |
| com/planet_ink/coffee_mud/Libraries/interfaces/GalacticMap.java | API for all space math; javadoc states intent | 827 |
| com/planet_ink/coffee_mud/core/interfaces/SpaceObject.java | `SpaceObject` interface, `Distance` enum, unit, mass, acceleration and velocity constants | 340 |
| com/planet_ink/coffee_mud/Libraries/interfaces/TechLibrary.java | API for circuits, power, manufacturers and tech level | 259 |
| com/planet_ink/coffee_mud/Common/DefaultManufacturer.java | Default `Manufacturer` data object with XML persistence | 242 |
| com/planet_ink/coffee_mud/Common/interfaces/Manufacturer.java | `Manufacturer` interface | 147 |

Supporting classes used throughout but outside this bucket: `core/collections/Dir3D.java` (angles), `Coord3D.java` and `BigVector.java` (BigDecimal vectors), `BoundedTube.java`, `BoundedSphere.java`, `RTree.java` (spatial index), `core/BigCMath.java`, and `Common/interfaces/CMMsg.java` (message codes). Both are registered in `application/VFShell.java:1167` (GALACTIC gives `CoffeeDark`) and `application/VFShell.java:1238` (TECH gives `GroundWired`).

## How it works

### 1. Units, coordinates and constants

- Positions are `Coord3D` values with BigDecimal components. The distance unit is the decameter, abbreviated "dm" or "dam" (10 m). See `SpaceObject.java:53-54` and the `Distance` enum at `SpaceObject.java:156-216`.
- Angles: `Dir3D.xy()` is yaw (azimuth in the XY plane). `Dir3D.z()` is the polar angle from the +z axis, in [0, pi]. `moveSpaceObject` uses `cos(yaw) sin(z)`, `sin(yaw) sin(z)` and `cos(z)` as the unit vector (`CoffeeDark.java:631-643`). `getBigDirection` uses `atan2` for yaw and `acos` for z (`CoffeeDark.java:651-667`). `Dir3D` setters wrap angles when the object is in safe mode (`core/collections/Dir3D.java:218-252`).
- Speed is distance per space tick, not per second. `runSpace` is called once per tick (`CoffeeDark.java:1747`, `1761`), and the tick length defaults to `TIME_TICK = 4000` ms (`core/CMProps.java:681`, overridable with `TICKTIME` at `core/CMProps.java:2720`).

Distance ladder (values from `SpaceObject.java`, converted with 1 dam = 10 m):

| Name (abbr) | dam | Notes |
|---|---|---|
| Decameter (dam) | 1 | 10 m |
| Kilometer (km) | 100 | |
| AstroUnit (au) | 14,959,787,100 | about 1.5e11 m |
| LightSecond (ls) | 29,999,779 | LightYear / 31,536,000 (computed) |
| Parsec (pc) | 3,085,677,580,000,000 | |
| LightYear (ly) | 946,073,047,258,080 | |
| PlanetRadius (pr) | 639,875 | about 6,400 km |
| StarGRadius (gr) | 69,550,000 | about 695,500 km |
| MoonRadius (mr) | 173,740 | about 1,737 km |
| GalaxyRadius (xr) | 8,514,657,425,322,720,000 | 9000 ly (`SpaceObject.java:173`) |

Speed constants (`SpaceObject.java:290-339`), in dam per second as documented. `VELOCITY_LIGHT = 29,979,245` is c. Sublight is c/4 (7,494,811). Warp N is N^2 times c (warp 1 to 10). Transwarp N is N^3 times c (1 to 10). Orbital speed is 770 and escape speed is 680. Sound is 34.

G-force thresholds (`SpaceObject.java:255-269`, `ACCELERATION_G = 1`): typical rocket 2G, typical space rocket 3G, roller coaster 5G, unconsciousness 15G, damaged 30G, instant death 60G, atmosphere 300G. The crew-damage code reads these at `Libraries/MUDFight.java:2493-2515` and `Areas/StdSpaceShip.java:692-709`.

Mass constants (`SpaceObject.java:244-252`): planets have mass `933 * radius` (`Areas/StdPlanet.java:161`, `Areas/StdThinPlanet.java:75`), stars `1989000 * radius` (`Items/Basic/Star.java:63`). `MOONLET_MASS = 16,209,942` and `ASTEROID_MASS = 3,241,988` (computed from `SpaceObject.java:250-252`).

### 2. Space registry and queries (CoffeeDark)

- All space objects sit in one `RTree<SpaceObject>` (`CoffeeDark.java:52`). `addObjectToSpace(O, coords)` sets coordinates before inserting (`CoffeeDark.java:102-109`). `moveSpaceObject` removes and reinserts an object that is already indexed (`CoffeeDark.java:609-622`). `StdSpaceBody.setCoords` routes through `moveSpaceObject` (`Items/Basic/StdSpaceBody.java:136-140`), so moving an object by `setCoords` keeps the index consistent.
- Queries: centre-point donut (`getSpaceObjectsByCenterpointWithin`, `CoffeeDark.java:784-803`), cube (`:806-814`), tube (`:817-831`), and `getSpaceObjectsWithin(ofObj, min, max)` (`:834-868`). The last filters on surface gap, `|centre distance - r1 - r2|` (`:848`), and sorts by centre distance (`:855-866`).
- Name lookup goes through `CMLib.english().fetchEnvironmental` (`CoffeeDark.java:721-733`). `getSpaceObject(CMObject, ignoreMobs)` climbs from items, mobs and rooms to the enclosing space body (`:736-769`).
- Planets and stars are `Area`s that also implement `SpaceObject`. `spaceAreas()` filters for them (`CoffeeDark.java:74-81`, `:121-124`).

### 3. The space tick: `runSpace` (CoffeeDark.java:1599-1737)

1. Copy all space objects into a list sorted by speed, fastest first (`:1603-1604`). Increment `tickCounter`, which keys the gravity cache (`:1605`).
2. Skip destroyed objects and drop their cache entries (`:1609-1612`). Skip planets and stars, which never move (`:1614`). Skip ships whose area is not `ACTIVE` (`:1617-1620`). Suspended areas are therefore frozen.
3. If the object is moving (speed at least 1), move it along its heading (`:1622-1630`).
4. Scan neighbours within `max(10 light-seconds, 2 * speed)` (`:1636`).
5. For each neighbour, take the swept clearance `getMinDistanceFrom(start, end, neighbourCentre)` (`:1652`). That is the closest approach of the step travelled this tick to the neighbour's centre. Accumulate the neighbour's pull into a gravity vector (`:1658-1667`).
6. Collision happens if the clearance is less than the sum of radii, something is moving, and not both bodies are at least `MOONLET_MASS` (`:1674-1676`). The mover is snapped to the other body's centre (`:1680`). The message is `MSG_COLLISION` (`common/interfaces/CMMsg.java:1617`), or `MSG_DAMAGE` with the weapon's damage type for weapons (`:1681-1699`). Delivery is two-phase: both parties get `okMessage` (`:1705-1709`), then `executeMsg` (`:1710-1715`), with `swapTargetTool` addressing each side (`:1581-1586`).
7. Apply net gravity through `accelSpaceObject` (`:1719-1726`). Ships in any gravity get the `IN_THE_AIR` flag (`:1727-1733`, `Items/interfaces/SpaceShip.java:48`).
8. Trim the gravity cache to live objects (`:1736`).

The tick is one thread. It is started with `startTickDown(... CMProps.getTickMillis(), 1)` (`:1747`) and is named `THSpace` plus a character from the thread group (`:1746`).

### 4. Gravity (CoffeeDark.java:1417-1529)

- `getGravityForce(S, cO)` (`:1514-1529`): let `d` be the centre distance minus `cO.radius` (the surface gap). Pull applies only if `cO` is a planet or star (an `Area`) or has mass at least `ASTEROID_MASS`, `S` has mass below `MOONLET_MASS`, and `0 < d <= 1.037 * radius` (`:1519-1522`, `SpaceObject.java:242`).
- Magnitude is `0.5 + 0.5 * (1 - d / (0.037 * radius))` inside the shell (`:1417-1420`, `:1524-1526`). It is 1.0 at the surface and 0.5 at the outer edge, then 0 outside (`:1527-1528`), which is a discontinuity at the edge. For `PlanetRadius` (639,875 dam) the shell is 23,675 dam, about 237 km thick (computed).
- `getGravityForcer(S)` (`:1465-1512`) sums the pull vectors from every object in a 10-light-second cube (`:1480-1493`). It normalises the sum into a direction and magnitude, and caches the result for the current tick (`:1471-1473`, `:1502-1510`). `getNetAccelerationAfterGravity` subtracts that pull from a thrust vector (`:1422-1438`).

### 5. Thrust and steering: `accelSpaceObject` (CoffeeDark.java:453-525)

The model applies thrust `a` in a chosen direction. Speed changes by `a * cos(delta)`, and the heading turns toward the thrust direction, where `delta` is the angle between heading and thrust (`:468`). It is a heuristic, not vector addition.

- If the thrust is directly opposite and the speed exceeds `a`, speed drops by `a` with no turn (`:469-471`).
- Yaw step: `yawMin = max(0, sin(delta) * (0.01 + yawDelta * (1.01 - sin(pitch))))` (`:482`, constants `BigCMath.java:40`, `:43`). If the remaining yaw error is at most `yawMin`, the heading snaps to the thrust yaw (`:496-497`). Otherwise it turns by `sin(yawDelta)` times a multiplier, but at least `yawMin` (`:500-503`).
- Multiplier: 1 if speed is at most `a`. Otherwise `(a / speed) * sin(delta)`, floored at 0.2 when `delta < 90` degrees (`:484-495`, `BigCMath.java:42`).
- Pitch: the step is at least `BigCMath.POINT1`, which is 0.1 (`:505-513`, `BigCMath.java:41`).
- Speed: `v' = v + a * cos(delta)` (`:515`). If `v'` is negative, the ship reverses and adopts the thrust heading (`:517-521`).

### 6. Direction helpers (CoffeeDark.java:527-601, 154-186, 1230-1256)

- `getDirectionFromDir(facing, roll, direction)` returns a `ShipDir` (`:547-577`): FORWARD, AFT, PORT, STARBOARD, DORSEL or VENTRAL (`Items/interfaces/ShipDirectional.java:50-57`). FORWARD and AFT are chosen when the pitch difference is under 45 or over 135 degrees and the yaw difference is within 45 degrees of zero (FORWARD) or between 135 and 225 degrees (AFT). Otherwise roll decides in 90-degree bands: DORSEL (under 45 or 315 and over), PORT (45 to 135), VENTRAL (135 to 225), STARBOARD (225 to 315). `getAbsoluteDirectionalFromDir` (`:580-601`) classifies by pitch first (under 45 gives DORSEL, over 135 gives VENTRAL), then by yaw.
- `getOppositeDir` is `(pi + yaw, pi - z)` (`:541-545`).
- `getPerpendicularAngles` returns seven directions: a pitch flip, four yaw offsets at 90 and 45 degrees, and the opposite (`:154-186`).
- `getCurrentBattleCoveredDirections` picks a contiguous arc of the permitted firing directions around a random centre (`:1230-1256`). Weapons call it at `Items/CompTech/StdShipWeapon.java:174` and `Items/CompTech/StdCompLauncher.java:172`.

### 7. Routing: `plotCourse` (CoffeeDark.java:1259-1415)

- `makeCourseTubeRay` builds a `BoundedTube` from the source's surface to the target's surface, with 1.037 radius margins (`:1259-1278`, `:1264-1265`). Objects that intersect the tube are blockers (`checkSubCourse`, `:1280-1290`).
- `plotFullCourse` (`:1292-1399`): when blocked, it takes the nearest blocker and widens an angular offset `dirDelta = atan((srcR * err + blockerR * 1.037 * err) / distance)` for `err = 2, 4, ... 65536` (`:1327-1332`). It tries four tangent headings (`:1336-1353`), sorts candidate waypoints by distance to the target (`:1359-1375`), and recurses to depth 50 (`:1295`, `:1386-1394`).
- `plotCourse` truncates the result to `maxTicks` waypoints (`:1412-1413`) and returns an empty list if no route is found (`:1410-1411`).
- No callers of `plotCourse` were found in the Java tree.

### 8. Intercept (CoffeeDark.java:934-1002)

- `calculateIntercept(chaser, runner, maxChaserSpeed, maxTicks)` returns a heading and speed for a missile or ship to meet a runner. Let `V0` be the runner's velocity per tick and `A = V0.V0 - S^2`. If `A <= 0`, a closest-approach test is used (`:966-976`). Otherwise a quadratic is solved (`:979-993`). Both branches are defective (see Problems).
- Callers: `Items/BasicTech/StdSmartTorpedo.java:78` (homing) and `Items/Software/ShipTacticalProgram.java:232`.
- `canMaybeIntercept` (`:934-941`) is a cheap tube-overlap pre-test. Only `Tests/Spaceinterception.java:96` calls it.

### 9. Safe trajectory (CoffeeDark.java:1194-1227)

The interface says it reports whether the straight path to the target stays a safe distance from every object in space (`GalacticMap.java:215-228`). The implementation does not do that, as described under Problems. No Java callers were found.

### 10. Orbits (CoffeeDark.java:1134-1191, 1440-1463)

- `estimateOrbitalSpeed` (`:1440-1463`): picks an orbit radius 75% of the way from the surface to the outer edge of the pull shell. It returns `sqrt(g * orbitRadius)`, where `g` is `calculateGravityForce` at that distance.
- `calculateOrbit(ship, planet)` (`:1167-1191`): speed is `sqrt(centreDistance * force)`. The heading is whichever of the seven perpendicular directions is closest to the current heading (`:1175-1186`). Caller: `Items/Software/ShipNavProgram.java:1433`.
- `getOrbitalMaintenance(ship, planet, desiredRadius)` (`:1134-1164`): target speed is the circular speed for `desiredRadius` plus a drift term `dv = (|drift| / R) * baseSpeed * sign(drift)` (`:1144-1145`). The heading is 10% toward the perpendicular of the radial, with pitch fixed at pi/2, so the orbit lies in the horizontal plane (`:1156-1159`). The orbital point is placed on the ship's side of the planet (`:1162`). Callers: `Items/Software/RocketShipProgram.java:158` and `Items/Software/ShipNavProgram.java:1549`.

### 11. Sensing: emissions (CoffeeDark.java:1556-1579)

`sendSpaceEmissionEvent` creates a `MSG_EMISSION` (`CMMsg.java:1653`) sourced from the deity MOB (`:1568-1569`). It gathers objects within a range that defaults to 30 light-seconds (`:1559`, `:1565`). The source gets `okMessage`, and then every object within range gets `executeMsg` (`:1571-1576`). Callers: `Items/Basic/StdSpaceBody.java:262`, `Items/BasicTech/GenSpaceShip.java:589-617`, `Items/BasicTech/StdSpaceTechWeapon.java:93`, and `Items/CompTech/StdShipShieldGenerator.java:251`.

### 12. Galaxy sectors (CoffeeDark.java:214-313)

- A sector name is `X + " " + Y + " " + Z`, with each index taken from one of three lists of 88, 88 and 72 entries (`:217-219`, `:242`, `resources/lists.ini:1821-1841`).
- The galaxy is a cube from `-GalaxyRadius` to `+GalaxyRadius` (9000 ly per radius). Each axis is divided by its list length (`:228-230`), so intended sectors are about 205 x 205 x 250 ly (computed).
- `getSectorMap` (`:270-313`) would build one `BoundedCube` per cell, 88 x 88 x 72 = 557,568 cells in all.
- `getInSectorCoords` (`:245-267`) folds negative coordinates to positive and takes the remainder by the sector width.
- Callers: `Items/Software/DatabaseProgram.java:723` and `:816`, `Items/Software/RocketShipProgram.java:188-191` (displayed as "Sector" and "Location"), and `Tests/Spacesectorsmap.java:61`. The overflow defect that breaks these is described under Problems.

### 13. Landing points (CoffeeDark.java:870-925)

`getLandingPoints(ship, O)` takes the `LocationRoom`s from the planet's metro map (`:887-891`). It sorts spaceports first, then by distance from the ship (`:895-923`). Callers: `Items/Software/ShipNavProgram.java:1918` and `Items/BasicTech/GenSpaceShip.java:584`.

### 14. Circuits and the electronic bus (GroundWired.java:133-365)

- Circuit key (`getElectronicsKey`, `:134-164`): a ship, and a room inside a ship, use the ship's area key (`:136-137`, `:152-156`). Other electronics use their owner's key. An area's key is its name, lowercased, with its `REGISTRY` blurb appended if set (`:141-149`). A room's key is the land title's unique lot ID if it has one, else `getExtendedRoomID` (`:157-161`).
- Registration (`registerElectrics`, `:167-235`): only electronics owned directly by a room register (`:177`). Ships offered for sale (land title with no owner) do not (`:183-187`). Each key holds weak references (`:52-62`, `:206-208`). Listeners are indexed by `TechCommand` (`:210-225`). `okMessage` and `executeMsg` route a tech command only to listeners on the same circuit (`:263-315`).
- Tick: `GroundWired` runs its own thread, named `THWired` plus a character (`:509`), at `CMProps.getTickMillis()` (`:510`, `:516-533`). Each key runs `runElectricCurrent` (`:921-935`). Keys whose area is not active are skipped (`:838-839`).

### 15. The power pass: `processElectricCurrents` (GroundWired.java:548-734)

1. Ping each generator with a zero-value `MSG_POWERCURRENT` (`:551-563`, `CMMsg.java:1603`), then pool each generator's stored power and zero it (`:564-576`). Pool each battery's stored power the same way (`:577-587`).
2. If the pool is zero, send zero-value messages to panels and batteries (`:588-622`). Nothing is powered.
3. Otherwise:
   a. Each panel gets a share of the pool in proportion to `powerNeeds()`, where a need of 0 or less counts as 1.0 (`:627-640`). Each taken share is at least 1 while power remains (`:641-643`). Consumption is read back from the message value (`:656`). Panels read `msg.value()` as the power on offer and take from it for each electronic they contain (`Items/BasicTech/StdElecPanel.java:213-222`).
   b. Batteries are reimbursed for the energy drawn from them, up to what remains (`:660-664`). Unused battery energy therefore goes back first.
   c. The remainder is split evenly among batteries that are not full, in repeated rounds (`:665-696`).
   d. Any power still left is charged into batteries evenly (`:699-718`).
   e. Anything left over goes back to generators, each capped at its capacity (`:720-732`).

Power is reconciled per tick in integers, with a few `long` to `int` casts (`:681`, `:703`, `:722`).

### 16. Emergency battery seeking (GroundWired.java:850-919)

`seekBatteryPower(panel, key)` is called when a device is off and has no power (`Items/CompTech/StdComputerConsole.java:433`). The library looks for the first battery on the circuit that is inactive and has charge (`:858-875`). It activates it through a factory MOB (`:863-866`). If activation succeeds, it runs a power pass with only that battery and the requesting panel (`:909-911`). If activation fails, the battery moves to the end of the circuit list (`:877-897`), so the next attempt tries another battery.

### 17. Manufacturers and tech levels (GroundWired.java:938-1090, DefaultManufacturer.java, Manufacturer.java)

- The global tech level is an integer read from the `TECH` / `GLOBALLEVEL` resource property at startup (`GroundWired.java:86`). `bumpTechLevel` increments it and writes it back (`:102-107`). No caller of `bumpTechLevel` exists in the Java tree.
- `getRandomGlobalTechLevel` is `rollLowBiased(1, 10, global - 1)` (`:97-100`). That gives a value from `global` to `global + 9` (`Libraries/Dice.java:273-288`).
- A manufacturer has a name, a minimum and maximum tech-level difference (bytes 0 to 10; defaults 0 and 10), efficiency and reliability multipliers (default 1.0), an item mask in zapper syntax, and a types list (`DefaultManufacturer.java:46-55`, `Manufacturer.java:37-147`).
- RANDOM resolution (`getManufacturerOf`, `GroundWired.java:1007-1033`): keep manufacturers whose mask accepts the item (an empty mask accepts everything, `Libraries/MUDZapper.java:5683-5688`). Keep those whose window `[global + min, global + max]` contains the item's tech level (`:1026-1027`). Pick one uniformly (`:1032`). If none qualify, use the default "ACME" manufacturer (`:1030-1031`, `DefaultManufacturer.java:46`).
- `fixItemTechLevel` (`:110-131`) turns a RANDOM item into a concrete manufacturer and tech level. It renames the item "Manufacturer + old name", and appends a tech mark to the display text from `TECH_LEVEL_NAMES[techLevel % 79]` (`:121-129`).
- Persistence: the manufacturer file is `/resources/tech/manufacturers.xml` (`:1043-1046`). If that file is missing, the code loads `/resources/examples/manufacturers.xml` (`:1064-1069`). The first path does not exist in this repository, so the 90-entry examples file is the live data (checked). Saves rewrite the whole file on every add, update or delete (`:946-994`, `:1048-1062`).

## Content worth mining

### Tech-era marks (resources/lists.ini:1767-1771)

`TECH_LEVEL_NAMES` has 79 entries that are cycled by `techLevel % 79`:

| Series | Entries | Range |
|---|---|---|
| Mk. | 20 | Mk.I to Mk.XX (Roman numerals) |
| Ver | 20 | Ver2.0 to Ver9.9 (decimal versions) |
| Mach. | 20 | Mach.I to Mach.XX |
| Gen. | 19 | Gen.1 to Gen.20, with Gen.18 missing (see Problems) |

The mark is appended to the display text, so an item can show a progression signal such as "... Mk.IV" (`GroundWired.java:125`).

### Galaxy names (resources/lists.ini:1821-1841)

- X axis (88 entries): constellations, for example Andromeda, Cygnus, Orion, Hydra and Vulpecula.
- Y axis (88 entries): Greek-letter pairs, for example "alpha-beta", "beta-gamma", "omega-psi" and "pi-upsilon".
- Z axis (72 entries): grade labels in nine tiers of eight numbered steps, from Inferior-I through Superior-VIII.
- A name is the three parts joined with spaces (`CoffeeDark.java:242`).

### Manufacturers (resources/examples/manufacturers.xml)

Ninety fictional manufacturers, with statistics computed from the file:

- Efficiency runs from 0.76 to 1.25, and reliability from 0.76 to 1.25.
- Minimum tech-level difference runs from 0 to 7, and maximum from 3 to 10. Windows are 3 to 5 levels wide.
- No example entry sets a mask, so all accept any item.
- Types are drawn from `TechType` values such as SHIP_COMPUTER, SHIP_WEAPON, SHIP_SHIELD, SHIP_ENGINE, SHIP_SENSOR, SHIP_POWER, PERSONAL_WEAPON, PERSONAL_SHIELD and SHIP_SOFTWARE. Six entries use ANY.
- Example rows: picoSystems (window 3 to 7, efficiency 1.13, reliability 0.8, SHIP_COMPUTER) and Nucleomatic (window 5 to 10, efficiency 0.94, reliability 0.83, SHIP_SHIELD and SHIP_WEAPON).

### Techno-babble word lists (resources/lists.ini:1800-1818)

`TECH_BABBLE_VERBS`, `TECH_BABBLE_ADJ1`, `TECH_BABBLE_ADJ2` and `TECH_BABBLE_NOUN` are used outside this bucket (`Abilities/Tech/AstroEngineering.java`, `core/CMProps.java`). How they assemble a phrase was not checked.

## Notable design ideas

- **Circuits without wiring.** Electronics join a circuit by sitting in the same location. The key is the area name plus an optional `REGISTRY` blurb, so two areas with the same name can stay separate (`GroundWired.java:141-149`). Components hold weak references, so the registry does not keep them alive (`:52-62`).
- **Battery seeking as a mechanic.** A device with no power can wake a battery on its own circuit (`GroundWired.java:850-911`). This is a plausible game hook for a single-player design.
- **Per-tick power bus with reimbursement.** Batteries get back what they supplied before any recharge happens, and leftovers return to generators (`GroundWired.java:660-732`).
- **Gravity shells.** Each body's pull is limited to a thin shell of 0.037 radius (`CoffeeDark.java:1524`). Orbits and landings are local events, and only nearby bodies are summed.
- **Gravity cache keyed by tick.** `getGravityForcer` caches per tick (`CoffeeDark.java:1471-1473`), so repeated queries in one tick are cheap.
- **Sector grid from word lists.** Constellation, Greek pair and grade combine into 557,568 cell names with no stored table (`CoffeeDark.java:214-243`).
- **Era marks in item names.** Tech levels show as Mk, Ver, Mach or Gen suffixes that a player can read at a glance (`GroundWired.java:125`).
- **Manufacturers as data.** A tech window relative to the global level, an item mask, and efficiency and reliability multipliers make a catalogue editable as XML (`GroundWired.java:1007-1033`).
- **Two-phase messages in space.** Collisions and emissions use `okMessage` to veto and then `executeMsg` to apply, the same flow as the rest of the engine (`CoffeeDark.java:1705-1715`, `:1571-1576`).
- **Detour search with growing error.** Routing widens an angular offset geometrically from 2 to 65536 and tries four tangents (`CoffeeDark.java:1327-1353`).

## Problems and cruft

Items marked [read] were seen by reading the code. Items marked [sim] were confirmed by a Python port of the same arithmetic. The port was not run inside the game.

### Defects that change results

1. **Sector width overflows (CoffeeDark.java:228-230, 252-254, 285-287).** `2L * GalaxyRadius.dm` is about 1.70e19, which is beyond the signed 64-bit maximum. The sector width therefore comes out negative: -16,107,150,262,092,177 along X and Y, and -19,686,516,987,001,550 along Z [sim]. As a result `getSectorMap` steps X only 45 times instead of 88, creates 74,925 cubes instead of 557,568, and writes upper bounds below lower bounds [sim]. `getSectorName` maps coordinates to different names: for x = 10^18 the intended X index is 49 and the code gives 70 [sim]. Callers that show wrong output: `Items/Software/DatabaseProgram.java:723,816` and `Items/Software/RocketShipProgram.java:188-191`. A fix would divide before doubling, or use BigInteger.

2. **`calculateIntercept` fly-by branch (CoffeeDark.java:966-976).** The perpendicular distance is `|V0 x w|` without dividing by `|V0|`, so it is scaled by the runner's speed [read]. The closest-approach parameter `projT = (P0 - P1).V0 / |V0|^2` has the wrong sign; the correct value is `(P1 - P0).V0 / |V0|^2` [read]. Test case: runner at the origin moving 1 unit per tick toward a chaser 10 units away with speed 2. The code returns no intercept, but the true intercept is at tick 10/3 [sim]. A second test: perpendicular offset 0.8 with runner speed 2 and radii summing to 1 gives a computed distance of 1.6, so the intercept is wrongly rejected [sim]. Callers: `Items/Software/ShipTacticalProgram.java:232` and `Items/BasicTech/StdSmartTorpedo.java:78`.

3. **`calculateIntercept` quadratic branch (CoffeeDark.java:979-993).** There is no check that the discriminant is non-negative. `BigCMath.sqrt` returns 0 for non-positive input (`core/BigCMath.java:79-80`), so when no real root exists the code still returns a course, using T = -B/(2A) [sim]. Test case: runner at the origin moving 2 per tick, chaser at (10, 10) with speed 1. The code returns a target reached at tick 6.7, which the chaser cannot reach [sim]. This branch also never checks `maxTicks`. Only the other branch does (`:975-976`).

4. **`canMaybeIntercept` is always true (CoffeeDark.java:936-940).** Line 938 builds the chaser's sphere from `runnerO`, so both tubes start at the runner's centre [read]. Their segment distance is zero, so the test passes for practically any pair (`core/collections/BoundedTube.java:132-150`). Only `Tests/Spaceinterception.java:96` and `Tests/Spaceinterception2.java:96` call it.

5. **`isSafeTrajectory` never checks other objects (CoffeeDark.java:1194-1227).** It measures `currR` as distance from the start point, not from any body (`:1218-1219`). So `minR` ends up as the first step's length, and only that is compared against the threshold (`:1221-1226`) [read]. The threshold variable is named `planetR` but is `ship.radius() * 2` (`:1198`). Gravity is sampled once at the ship's real position for every simulated step (`:1211`). No Java callers were found.

6. **Orbit correction sign looks inverted (CoffeeDark.java:1144-1145).** When the ship is outside the desired radius (drift above 0), the speed correction is positive, which raises the target speed. Speeding up at a larger radius would raise the orbit further. This looks inverted for a corrective burn. Not checked dynamically. Callers: `Items/Software/RocketShipProgram.java:158` and `Items/Software/ShipNavProgram.java:1549`.

### Documentation and unit mismatches

7. **Speed units.** `SpaceObject` documents velocities and accelerations in decameters per second (`SpaceObject.java:254-261`, `:290-299`). `c = 29,979,245` dam/s matches per second. But `runSpace` applies `speed` once per tick (`CoffeeDark.java:635-641`, `:1628`, `:1725`), and the default tick is 4 s (`core/CMProps.java:681`). Unless the tick is 1 s, simulated speeds are per tick, not per second [read].

8. **Galaxy crossing comment.** `SpaceObject.java:339` says 1000c would cross the galaxy in about a real year. `GalaxyRadius` (`SpaceObject.java:173`) makes the galaxy 18,000 ly across, which is about 18 years at 1000c if speed is per second (computed).

9. **Manufacturer tech window.** `Manufacturer.java:58-63` and `:72-77` describe bounds as a "difference from Max Tech Level-10". The code uses the global tech level plus the difference (`GroundWired.java:1026-1027`).

10. **Perpendicular angles.** `GalacticMap.java:396` and `:410` say "usually 4 of them" and "90 degrees". The code returns seven directions, including two sets at 45 degrees (`CoffeeDark.java:178-183`).

11. **Acceleration javadoc.** `SpaceObject.java:265-267`: `ACCELERATION_ATMOSPHERE` has the same javadoc as `ACCELERATION_DAMAGED` ("severely damaged (40" fall)").

12. **Planet mass.** `SpaceObject.java:114-118` and `:244` say the mass is "off by about 15 zeroes". An Earth-sized `PlanetRadius` gives `933 * 639,875 = 597,003,375`, about 6e8. Earth's mass is about 6e24 kg, so the gap is about 16 orders of magnitude [computed]. The comment acknowledges that the values are approximate.

13. **Tech-level distribution.** `TechLibrary.java:236-239` says "gaussian-distributed". The code is a low-biased folded normal (`Libraries/Dice.java:273-288`). Minor.

### Dead code, copy-paste and small faults

14. `Common/DefaultManufacturer.java:72`: `newInstance` falls back to `new DefaultPoll()`, which is the wrong type. The imports `Poll.PollOption` and `Poll.PollResult` (`:13-14`) are unused, which suggests copy-paste from `DefaultPoll`.
15. `Libraries/GroundWired.java:345`: `sets.remove(oldKey)` lacks the `toLowerCase()` that lines 326 and 375 use. It only matters for mixed-case keys.
16. `Libraries/CoffeeDark.java:1621-1627`: `tube` is computed and never read.
17. `Libraries/CoffeeDark.java:1603`: the local `space` shadows the `RTree` field at `:52`.
18. `Libraries/GroundWired.java:74`: `nextKey` is never used. `:102-107`: `bumpTechLevel` has no callers. `GLOBALREACHEDON` (`:77`, `:87`, `:106`) is read at startup and reset to 0, but nothing in the repository writes it as a timestamp.
19. `Libraries/CoffeeDark.java:848`: `Math.abs(distance - r1 - r2)` reports penetration as a positive gap.
20. `Libraries/GroundWired.java:126`: `indexOf(oldName) > 0` means display text that starts with the old name is replaced by the generic "@x1 is here." (`:129`). This looks unintended.
21. `core/interfaces/SpaceObject.java:174-175`: `MoonRadius` and `AsteroidRadius` share the abbreviation "mr", so abbreviation lookup cannot be unique.
22. `core/interfaces/SpaceObject.java:221-235`: `DISTANCES` omits `Hectometer`. Check whether that is intended.
23. `resources/lists.ini:1771`: `TECH_LEVEL_NAMES` omits Gen.18, so there are 19 Gen entries and the cycle length is 79. `resources/lists.ini:1822`: one sector name contains a non-UTF-8 byte where a letter with a diaeresis belongs.
24. `Libraries/CoffeeDark.java:256-266`: `getInSectorCoords` folds negative coordinates to positive before taking the remainder, so mirror-image points share in-sector coordinates [read].
25. `Libraries/CoffeeDark.java:1736`: `keySet().retainAll(space)` runs against a `Vector`, so each `contains` is linear and the per-tick cost grows quadratically with object count [read].
26. `Libraries/CoffeeDark.java:1634` is a TODO about passifying stellar objects. Line 1636 means every moving object scans a 10-light-second neighbourhood each tick (about 3e8 dam). This is the main per-tick cost [read].

### Threading and multiplayer assumptions

27. Collisions snap the mover to the other body's centre, not its surface (`CoffeeDark.java:1680`).
28. Objects are updated fastest-first (`:1603-1604`), so the neighbour position used in the swept test may be before or after its own move in the same tick (`:1652`) [read].
29. `GroundWired.getPowerMsg` returns one shared mutable `CMMsg` (`GroundWired.java:483-500`). The tick thread uses it, and so does `seekBatteryPower`, which command code reaches through `Items/CompTech/StdComputerConsole.java:433`. A race is possible but was not observed [read].
30. Emissions and activations use system actors: the deity MOB (`CoffeeDark.java:1568`) and a factory MOB (`GroundWired.java:863`). Circuits also depend on land titles and owners (`GroundWired.java:157-159`, `:183-187`). These are multiplayer-era patterns.

## Takeaways for a Python SUD

**Borrow**

- **Tech-era marks and procedural galaxy names.** A progression label and a three-part name generator fit a text game and need no content table. Sketch:

  ```python
  import random
  CONSTELLATIONS = ["Andromeda", "Cygnus", "Orion"]           # 88 in the source
  PAIRS = ["alpha-beta", "omega-psi", "pi-upsilon"]           # 88 in the source
  GRADES = ["Inferior-I", "Lesser-II", "Superior-VIII"]       # 72 in the source
  def sector_name(rng: random.Random) -> str:
      return f"{rng.choice(CONSTELLATIONS)} {rng.choice(PAIRS)} {rng.choice(GRADES)}"
  ```

- **The power bus.** Each tick, allocate available power among demanders in proportion to their need, with a battery buffer that refills first. The core is short:

  ```python
  def share_power(available: float, panels: list) -> dict:
      needs = {p: (p.need if p.need > 0 else 1.0) for p in panels}   # GroundWired: need <= 0 counts as 1.0
      total = sum(needs.values())
      return {p: available * n / total for p, n in needs.items()}
  ```

- **Manufacturers as data.** A dataclass with a tech window relative to the global level, an efficiency and a reliability multiplier, and a set of item kinds. Pick at random among those whose window contains the item's level. This is easy to test and to serialise.
- **Gravity shell and swept collision.** Use a pull that depends on distance above the surface, and test each step's segment against each body's centre. Use a continuous falloff in place of the source's jump from 0.5 to 0 at the shell edge.
- **Intercept, corrected.** This formula passed four test cases in a Python check (head-on, moving away, faster runner, no real root):

  ```python
  from math import sqrt
  def intercept_time(d, v, s):
      """d = runner_pos - chaser_pos, v = runner velocity per tick, s = chaser speed per tick."""
      a = (v[0]**2 + v[1]**2) - s*s
      b = 2*(d[0]*v[0] + d[1]*v[1])
      c = d[0]**2 + d[1]**2
      if abs(a) < 1e-12:
          return -c/b if b < 0 else None
      disc = b*b - 4*a*c
      if disc < 0:
          return None
      roots = sorted(((-b - sqrt(disc))/(2*a), (-b + sqrt(disc))/(2*a)))
      pos = [t for t in roots if t > 0]
      return pos[0] if pos else None
  ```

**Simplify**

- Replace BigDecimal with floats, and the RTree with a grid hash. At single-player scale a few hundred bodies do not need a tree.
- Pick one unit system (km and seconds is simplest) and state it once. The source mixes decameters and per-tick speeds.
- Replace the heuristic steering in `accelSpaceObject` with a plain vector update, `v += a * dt`, and integrate position each tick.
- Generate the sector grid lazily from indices. Do not build 557,568 cubes.
- Drop weak references, deity and factory actors, the per-tick thread, and the registry bookkeeping. A single player needs none of them.

**Drop or rethink**

- Shared power messages and a shared mutable message object. A single-threaded game does not need them.
- Multi-player ownership in circuits (land titles, owner checks) is not needed. For a single player, a circuit can be a plain list of components per location.
- The recursive detour planner is heavy. For a single ship, a straight route with one blocker check, or a coarse grid search, would probably do.
- Real time or turn-based is undecided. Per-tick physics suits real time. For turn-based play, the orbit and intercept formulas can be used as closed-form calculations at each decision point.

## Coverage

- **Read in full (7 files):** `Libraries/CoffeeDark.java` (1785 lines, in three chunks), `Libraries/GroundWired.java` (1091 lines, in two chunks), `Libraries/interfaces/GalacticMap.java` (827), `core/interfaces/SpaceObject.java` (340), `Libraries/interfaces/TechLibrary.java` (259), `Common/DefaultManufacturer.java` (242), and `Common/interfaces/Manufacturer.java` (147).
- **Read in part or by search (supporting):** `core/collections/Dir3D.java` (setters), `BigVector.java` (subtract, dot, cross), `BoundedTube.java` (constructor and intersects), `BoundedSphere.java` (expand), `RTree.java` (insert and remove), `core/BigCMath.java` (constants and sqrt), `Libraries/Dice.java` (rollLowBiased), `Libraries/MUDZapper.java` (maskCheck), `Common/interfaces/CMMsg.java` (codes), `Items/BasicTech/StdElecPanel.java` (lines 213-222 only), `Items/Basic/StdSpaceBody.java` (setCoords, getCube), `resources/lists.ini` (tech blocks, lines 1767-1850), and `resources/examples/manufacturers.xml` (parsed for statistics; not read line by line).
- **Callers checked by search:** ShipNavProgram, ShipTacticalProgram, StdSmartTorpedo, RocketShipProgram, DatabaseProgram, StdComputerConsole, StdShipWeapon, StdCompLauncher, GenSpaceShip, StdSpaceBody, StdSpaceTechWeapon, StdShipShieldGenerator, the `Tests/Spaceinterception*.java` and `Tests/Spacesectors*.java` files, and VFShell.
- **Skipped:** the internals of `Items/` and `Areas/` space-ship and planet classes, the body of `MUDFight.java` beyond the G-force thresholds, and the full manufacturer XML.
- **Verified with Python ports (not executed in the game):** the sector-loop arithmetic (defect 1), the three `calculateIntercept` cases (defects 2 and 3), and the corrected intercept formula used in the Takeaways.
