# Software programs and ship software

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b134` (Items/Software) · 13 files · 8906 lines of Java.

Citations are relative to the CoffeeMUD folder, with the package prefix `com/planet_ink/coffee_mud/` dropped, so `Items/Software/ShipNavProgram.java:1476` is that file at line 1476. Nothing here was run. Bugs come from reading the code, and anything I could not settle is marked "unverified". The base item plumbing (`StdItem`) is covered in b172.

## Summary

A software program in CoffeeMud is an item that only works inside a computer. The player types into the computer, the computer offers its installed programs power on a regular pulse, and programs call one another through a small service protocol. The bucket has three families. Personal scanners (HEALTHSCAN, LIFESCAN, EMISSIONSCAN) search rooms and ships through the ordinary message system, so the world can veto a detection. DATABASE keeps a JSON knowledge base of coordinates, names and notes that other programs query. The ship set (sensors, navigation, tactical fire, a pilot console, diagnostics and a raw TELNET bridge) is most of the code, and ShipNavProgram alone is 2,516 lines: it measures each engine's real thrust curve at runtime and flies a state machine with obstacle detours. The power-pulse clock, the service protocol and scanning-by-message are the parts a single-player Python game could borrow most directly, while several concrete bugs listed below should not be copied.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Items/Software/StdProgram.java` | Base program: message hooks, screen queue, service protocol, peer discovery, root-menu start | 645 |
| `Items/Software/GenSoftware.java` | Generic (Gen) layer: MANUFACTURER, SETTINGS, PMENU, MNAME stats; readable text | 228 |
| `Items/Software/GenShipProgram.java` | Ship base: 8 s power cycle, parts found by circuit key, ACTIVATE and DEACTIVATE verbs | 648 |
| `Items/Software/ShipSensorProgram.java` | Sensor reports from SENSE callbacks; renaming through IDENTIFICATION; `SENSORn` verb | 438 |
| `Items/Software/ShipNavProgram.java` | Thrust calibration, turning, navigation state machine, course plotting, flight verbs | 2516 |
| `Items/Software/ShipTacticalProgram.java` | TARGET, FIRE with intercept, WEAPONn, SHIELDS; TARGETING provider | 424 |
| `Items/Software/RocketShipProgram.java` | Pilot console: verb dispatch, flight status, sensor and systems screens | 742 |
| `Items/Software/DatabaseProgram.java` | DATABASE or DB: JSON knowledge base and query grammar; IDENTIFICATION provider | 1342 |
| `Items/Software/ShipDiagProgram.java` | DIAG 1-3 and DAMAGE: timed jobs, power sampling by a temporary listener | 700 |
| `Items/Software/ShipTelnetProgram.java` | TELNET: outbound TCP session bridged to the screen; ArchonOnly | 279 |
| `Items/Software/HealthScanProgram.java` | HEALTHSCAN: life test, health, diseases, conditions | 266 |
| `Items/Software/LifeScanProgram.java` | LIFESCAN: living things within two rooms, on demand or continuous | 332 |
| `Items/Software/EmissionScanProgram.java` | EMISSIONSCAN: activated electronics within three rooms | 346 |
| `Items/Software/DiagProgram.js` (not in manifest) | DIAGTERM: JavaScript eval console; ArchonOnly | 71 |

Class hierarchy:

```
StdItem (b172)
 StdProgram (645)              hooks, screen queue, services, root auto-start
  GenSoftware (228)            Gen stats, readable text
   HealthScanProgram (266), LifeScanProgram (332), EmissionScanProgram (346)
   GenShipProgram (648)        8 s power cycle, parts by circuit key
    ShipTelnetProgram (279), ShipDiagProgram (700), DatabaseProgram (1342)
    ShipSensorProgram (438)
     ShipNavProgram (2516)
      ShipTacticalProgram (424)
       RocketShipProgram (742)
```

## How it works

### 1. Programs as message listeners

`StdProgram` extends `StdItem` (StdProgram.java:42) and reports the tech type `PERSONAL_SOFTWARE` (143-146). Ship programs report `SHIP_SOFTWARE` (GenShipProgram.java:81-84). Like every item, a program reacts in two phases: `okMessage` may veto, then `executeMsg` applies the effect. Its dispatch:

| Message targeted at the program | Veto phase (`okMessage`) | Effect phase (`executeMsg`) |
|---|---|---|
| `TYP_ACTIVATE` with `MASK_CNTRLMSG` | returns true, skipping the activation check (StdProgram.java:258-259) | `handleServices` (559-561) |
| `TYP_ACTIVATE` (from a player or computer) | `checkActivate` (261) | `onActivate` once, sets `isActivated` (562-568) |
| `TYP_DEACTIVATE` | `checkDeactivate` (264-266) | `onDeactivate` if active (569-575) |
| `TYP_WRITE` | `checkTyping` (268-271) | `onTyping` (576-579, also for `TYP_REWRITE`) |
| `TYP_POWERCURRENT` | `checkPowerCurrent` (272-275) | `onPowerCurrent` (580-582) |

Output: `addScreenMessage` appends a line to a queue (StdProgram.java:204-210), and `getScreenMessage` drains it (193-201). The tricorder drains the queues of all its programs and has the text spoken to the people reading it (`Items/BasicTech/StdTriCorder.java:476-486`, "says '...'"). Program output is therefore speech from a device, not a private panel.

Menus: a program's internal name is the menu the computer can switch to, and `forceUpMenu` returns the computer to the parent (StdProgram.java:212-216). A program with an empty internal name is a root program and starts on the first positive power pulse (466-483). `RocketShipProgram` is a root, so the pilot console starts without being typed.

### 2. Power is the clock and the bill

The computer drives the software, not the other way round. On each of its own power messages, `StdComputerConsole` (Items/CompTech/StdComputerConsole.java:698-730) sends every installed program a message, calls `okMessage` and then `executeMsg`, and adds up the value each program leaves on the message. The console sets that value to `1`, or `2` when the program is the foreground menu (line 715). No program in this bucket changes it (grep of `setValue` in Items/Software finds none), so a program costs one power unit per pulse and two while it is on screen. The console switches itself off 12 seconds after it last had stored power (701-704, 726-728).

Ship programs keep their own 8-second cycle. `GenShipProgram` runs `onPowerTimer` when more than 8 s have passed (GenShipProgram.java:56, 161-164) and counts down 100 cycles, about 800 s, before `onActivationTimer` (54, 165-168). Sensors refresh on each cycle (section 7). Diagnostics measure component draw from the power messages to components, not from software pulses (section 10).

The two-phase design has a side effect here. `checkPowerCurrent` runs in `okMessage`, so ShipNavProgram runs its whole navigation step inside a veto check (ShipNavProgram.java:1718-1724). It works, but a veto should not move a ship.

### 3. Services: programs asking programs

Programs cooperate through three service names (Software.java:56-61: `TARGETING`, `IDENTIFICATION`, `COORDQUERY`) and a message handshake (StdProgram.java:403-449, 501-550):

1. On activation, a program sends `SWSVCALLOW` for each service it provides and `SWSVCNEED` for each service it requires or appreciates.
2. A program that requires or appreciates a service registers any `ALLOW` it hears for that service (handleServices, 522-528). A provider that receives a `NEED` answers with an `ALLOW`, as a trailer message when both sit on the same owner (530-541).
3. A call (`doServiceTransaction`, 334-378) sends `SWSVCREQ` with arguments to each registrant and collects the `SWSVCRES` replies from the trailer messages.
4. A required service with no provider prints "Software X failure: no Y service found." and switches the program off (430-447). No program in this bucket declares a required service, so this path never runs here.

Peers are found through the circuit key. `getPeerComputers` walks `CMLib.tech().getMakeRegisteredElectronics(circuitKey)` (StdProgram.java:301-315). Programs on one ship share a key, so they find each other without knowing where the others are.

Services in use:

| Service | Provider | Consumers | Use |
|---|---|---|---|
| IDENTIFICATION | DatabaseProgram (`getProvidedServices`, DatabaseProgram.java:118-121) | ShipSensorProgram (appreciates, 228-232), inherited by ShipNav, Tactical and the pilot console | Sensors ask for the real name of an object (ShipSensorProgram.java:279-288); navigation asks whether a point is a known object (ShipNavProgram.java:654-667, used at 798-799) |
| TARGETING | ShipTacticalProgram (ShipTacticalProgram.java:57-61) | DatabaseProgram (appreciates, DatabaseProgram.java:124-127) | `TARGET` and `TARGET.n` as names in database queries (DatabaseProgram.java:1059-1066, 1240-1276) |
| COORDQUERY | none | GenShipProgram.findCoordinates (GenShipProgram.java:507-524) | Declared but never registered (Problems 7) |

### 4. Finding the ship's parts

`GenShipProgram` finds parts lazily. `getTechComponents` returns every `TechComponent` on the program's circuit key (GenShipProgram.java:284-302). Typed views are built from that list and cached: engines (267-282), sensors (304-317), weapons (349-363), shields (365-378), dampeners (380-393) and inertials (395-408). A launcher counts as a weapon only when it is empty or holds a weapon (333-347). `decache()` clears every cache when the program is picked up, put down, pushed, pulled or installed (200-221, 236-246).

Parts have stable names. `findComponentByName` accepts `ENGINE3` (the third engine), with the prefixes ENGINE, SENSOR, WEAPON, SHIELD and SYSTEM, and falls back to a fuzzy name match through `CMLib.english().fetchEnvironmental` (440-459). `findComponentByID` uses a static cache keyed by the component's string ID (`cachedComponents`, lines 70 and 461-476), which is shared by every program in the game.

The activation verbs (`activateProcedure`, 526-590; `deactivateProcedure`, 592-647) turn on either every non-engine, non-weapon, non-tractor component (`ALL`) or one named part. Activating an engine sends `THRUST(AFT, 0.0000001)` (565), a token non-zero thrust that starts it.

### 5. Scanners

All three scanners send a control message through the room, with a factory MOB as the source. Every candidate must pass `R.okMessage` (LifeScanProgram.java:73-75, 141-142; EmissionScanProgram.java:72-74, 144-145; HealthScanProgram.java:71-74, 212-214). The message is `MSG_LOOK` for life and health and `MSG_SNIFF` for emissions, so a scan is a look that other world rules can veto.

- **Life** is a race test. A mob is alive if it is not a golem and its race `canBreedWith` itself (LifeScanProgram.java:67-71; HealthScanProgram.java:65-69). The code comment calls this "the definition of life".
- **LIFESCAN** range is `weight + 1` rooms (LifeScanProgram.java:233). The base weight is 1 (63), so the scan reaches two rooms out. The walk builds a direction code: a lowercase letter for each step, `D` for a closed door (216-217), `I` or `o` for crossing indoors or outdoors (218-220), and `S` for entering a ship (182-204). `getDirDesc` turns the code into text using the phrases " here", "behind a door", "inside a room", "outdoors", "inside a ship" and ", and then" (78-127).
- **EMISSIONSCAN** finds any activated `Electronics` carried by an inhabitant or lying in a room (EmissionScanProgram.java:67-70, 135-193). Its base weight is 2 (63), so it reaches three rooms out. Its phrases are "Something on <name>" (only in the scanner's own room, and only when the carrier is visible, 150-154), "Something in <container>", "Something inside something else" and plain "Something" (175-190).
- **HEALTHSCAN** takes a named mob in the room, or a caged animal uncaged for the purpose (HealthScanProgram.java:167-194). It reports race and gender, biological age when ageing is on (82-86), health (87-88), each disease with its level and spread routes (89-108), other health conditions (110-120) and sleep (123-125).

Continuous mode is incomplete. `onActivate` scans once (LifeScanProgram.java:292-300). `onPowerCurrent` scans again while `--activatedTickdown >= 0` (325-330), with `AUTO_TICKDOWN = 4` (51). That gives four automatic rescans, after which it is silent until the program is re-activated. EmissionScanProgram does the same (309, 338-344). See Problems 5.

### 6. DATABASE

**Data model.** The whole content is one JSON object, serialised as the `SETTINGS` stat of the generic item (DatabaseProgram.java:83-115; GenSoftware.java:185). Top-level keys are either coordinates `x,y,z` or names. A value is one of:
- a string: a name, or the sentinel `SPACE`, meaning that a real sensed object at those coordinates is authoritative (DatabaseProgram.java:330-348);
- an object with `NAME`, `NOTE`, `COORDS`, `MASS`, `RADIUS` (the `BASIC_FIELDS`, 58-60) and any custom field (`getSpaceObjectDataResults`, 596-642).

Two reserved keys matter. `DISABLED` makes the database read-only and drops key queries (1018-1024, 406-408, 976). `SPACECUBE` names a box, given by `COORDS` and `RADIUS`, in which the database is authoritative. Real objects inside it are listed even with no entry (97-107, 374-381, 764-776).

**Placeholders.** `findDBSpaceObjects` builds a `StdSpaceBody` for each named entry with radius 0, which the code's comment says means "it's not real" (430-435). A note can therefore point at a place that has never been sensed.

**What is worth recording.** `isDBSpaceObject` (315-328) excludes ships, moving objects, objects smaller than half a moon's radius (when the radius is known) and objects lighter than half a moonlet (when the mass is known).

**Commands.** Commands are recognised only while the program is active (`isCommandString` returns false when not active, 162-163). They are bare queries, `QUERY`, `HELP`, and the editing verbs `ADD <key> <name>`, `DEL <key>` and `SET.<FIELD> <key> <text>` (`onTyping`, 1000-1228). The editing verbs work only when no other program on the same computer holds the active menu (`actingAlone`, 167-179), or when the command is prefixed `DATABASE` or `DB`. Names are limited to 40 characters and must be unique (1105-1111, 1120-1126). Free text is limited to 120 characters (1113-1118).

**Query grammar.**
- Coordinates are `x,y,z`, each part a space distance, for example `-10gm,40dm,1km` (help text, 1203; parsing 286-294 and 490-505 through `CMLib.english().parseSpaceDistance`).
- `NEAR` or `AROUND` widens the spatial search from 10 dm to one astronomical unit (801).
- Key matching: exact, `PREFIX*`, `*SUFFIX` and `*INFIX*` (856-926). Sector names take the same wildcards (720-742).
- `HERE` is the computer's own location (782-787). `TARGET` and `TARGET.n` take a name from the tactical program's sensor report (1059-1066, 1240-1276).
- An ambiguous query returns "Multiple targets selected.  Please narrow search." (1078, 1092).

The database also answers the IDENTIFICATION service. A sensor asks for the name at a coordinate and gets one back (DatabaseProgram.java:1281-1311). This is how a ship learns what a sensed object is called.

**Capacity.** The constructor sets weight 100 with the comment "weight shall be how many entries can be held" (71). Nothing in this file checks it, so the limit is not enforced here.

### 7. Sensors (ShipSensorProgram)

`ShipSensorProgram` turns each sensor's raw detections into a per-sensor report.
- Each refresh sends the sensor a `SENSE` command (ShipSensorProgram.java:130-153; `TechCommand.SENSE`, Technical.java:184-200). The sensor answers by sending each detected object back as a message whose tool is that object (executeMsg, 259-294). The reports are kept in a `TreeSet` ordered by `XTreeSet.comparator` (ShipSensorProgram.java:83, 91). They are sorted by distance only when shown or targeted, using `DistanceSorter`, which uses centre distance minus radius (98-128).
- Refresh happens once per 8 s cycle through `onPowerTimer` (165-170). `onActivationTimer` drops the cached list of sensor parts every 100 cycles (172-176; the counter is at GenShipProgram.java:165-168).
- Renaming: if an object's real name differs from its display name and IDENTIFICATION is available, the program asks for the real name and replaces the display name (279-288).
- `SENSORn <target>` prints a table of Identifier, Direction, Distance, Moving, Speed, Size and Mass (336-370). `SENSORn <dir> [<dir> ...]` aims the sensor with `DIRSET` (371-392).
- On each pulse, sensors the program has not yet activated are switched on by the program, so the program has "a feedback mech" (197-225; comment at 204).

### 8. Navigation (ShipNavProgram)

ShipNavProgram is the largest file. It has three layers: thrust calibration, turning, and a per-pulse controller.

**Vocabulary.** `ShipNavProcess` has five processes: `STOP`, `LAUNCH`, `LAND`, `ORBIT` and `APPROACH` (ShipNavProgram.java:158-165). `ShipNavState` has nine states: `LAUNCHING`, `STOP`, `PRE_STOP`, `LANDING_APPROACH`, `LANDING`, `ORBITSEARCH`, `ORBITCRUISE`, `APPROACH` and `DEPROACH` (144-156). A `ShipNavTrack` holds a process, a state, arguments checked against the process's type template, and a `nextTrack` (77-106). Plans chain: APPROACH to ORBIT (1848-1850), or APPROACH to LAND (1980-1995).

**Thrust calibration.** `profileThrusters` (515-573) does not trust an engine's listed stats. For each engine with an aft port it sends 11 injection commands, `THRUST(AFT, x)`, with x = 0.001 and then 0.1 to 1.0 (541-546). Before each sample it flushes the ship's speed ticker with `ship.tick` (547), reads the acceleration from the `ACCELERATED` reply that `executeMsg` captures (607-638), and restores the previous injection (553-554). The result is an `EngProfile` with an 11-point acceleration chart (217-309). An engine that gives no non-zero reading is logged as "could not be profiled" (559-563).

Two rules come from the profile:
- `injection(a)` interpolates the chart to find the injection that gives acceleration `a` (291-308). `acceleration(x)` reads the nearest chart point (272-283).
- Without an inertial dampener in good repair (`hasDampeners`, 206-215, with a 30-unit threshold marked TODO), acceleration is capped at 3 G (`ACCELERATION_TYPICALSPACEROCKET`, 3 G at SpaceObject.java:261; EngProfile 279-281 and 293-295).

**Turning.** `changeFacing` (393-513) measures the engine's angular response before it corrects anything. It fires a port burn of 1.0 and reads the angle achieved from `ACCELERATED` (426-430). It then issues bursts proportional to the remaining error, yaw first (433-464) and then pitch (466-491), for at most 100 iterations (412-413). A turn needs an engine with all four lateral ports (`canTurn`, 234-237).

**Per-pulse controller.** `doNavigation` (1111-1716) runs on each power pulse, called from `checkPowerCurrent` (1718-1724). Its main parts:
- Completion (`checkNavComplete`, 859-986). APPROACH ends inside `100 + speed`, plus `speed` if the ship is aligned within `MAX_DIR_DIFF` = 0.08 (876-882). LAUNCH ends beyond `1.037 × planet radius` (952-955). STOP ends at or below 2 G (974-976). ORBIT never ends (971-972).
- Braking (1320-1351). The stopping distance is `(speed/2)·(ticksToStop+1) + (100 + speed/2)` (1322). When braking is due, the state becomes DEPROACH and the ship turns to face its direction of motion. Gravity adds a drag term, `gravAccel·|sin θ|·t²/2` (1278-1281).
- Acceleration target: the lowest of the engine maximum, the speed limit (1160), the atmosphere cap of 300 G (1161-1162, 1566) and half the remaining distance (1247-1248).
- Landing: a 10-unit soft-landing margin (1654-1657), a deceleration ladder by speed (1665-1683), and acceleration when far and slow (1694-1704).
- Orbit: the target band runs from radius + 0.75 × (outer − radius) up to `1.037 × radius` (1429-1430), aimed at its median (1431). The ship moves to ORBITCRUISE when its speed is within 0.5 to 2 times the orbital speed and its distance is inside the band (1492-1495). ORBITCRUISE then overwrites facing, direction, speed and coordinates every pulse (1540-1562), so the orbit is kept by setting state rather than by thrust.
- Thrust messages from the program carry `MASK_INTERMSG` (`setThrust`, line 346), so an engine can tell program thrust from manual thrust.

**Course plotting.** `COURSE` sets a target, and `calculateNavigation` runs on a later pulse (ShipNavProgram.java:576-604, 780-846).
- Only stationary objects are obstacles (`getCollision`, line 706), and only if the program has sensed them or IDENTIFICATION knows their coordinates (797-799).
- Each collision on the leg gets two candidate rings of points. `subCourseCheck` keeps the collision-free candidate nearest the leg and makes it a new "Nav Point" Moonlet (739-778, 806-813).
- If no candidate works and the ship is close, a "Safe Point" is placed beyond the gravity radius (815-826). The plan holds at most 50 nav points (789), and collinear points are then removed (837-844).
- The next `ENGINE` command with a plotted course starts an APPROACH track along it (2375-2381).

**Procedures.** Each verb is a `SoftwareProcedure` (Software.java:190-192):

| Verb | Procedure (lines) | Behaviour |
|---|---|---|
| `LAUNCH`, `ORBIT` (when docked) | 1735-1769, 1771-1854 | LAUNCH burns until clear of `1.037 × radius`. ORBIT plans an APPROACH to the planet, or to an "Orbital Point" if closer than the band, then chains an ORBIT track |
| `STOP` | 1856-1888 | Flips the ship to face opposite its motion (`flipForAllStop`, 385-391), then runs a STOP track |
| `LAND` | 1890-2015 | Finds the nearest body with landing points (`CMLib.space().getLandingPoints`), plans to "Orbit Point above <zone>", then LAND |
| `COURSE` | 2017-2077 | Sets the course target from a sensed object |
| `FACE` | 2079-2197 | Turns yaw, then pitch, with direct `THRUST` messages in degrees (2149, 2173) |
| `APPROACH` | 2216-2288 | Refuses unless the free distance is at least 200 (2253-2259); otherwise runs an APPROACH track, which completes at `100 + speed` (876-882) |
| `ENGINEn [dir] amount` | 2290-2393 | Amount is raw thrust, a percentage of maximum, or a speed; zero deactivates the engine |
| `MOON` | 2395-2514 | Like FACE, but the ship's facing points directly away from the target (2432-2433) |
| `CANCEL` | 2199-2214 | Clears the current track |

### 9. Tactical (ShipTacticalProgram)

- `TARGET` sets `currentTarget` to the sensed object whose name matches, and refuses objects without coordinates (ShipTacticalProgram.java:64-108).
- `FIRE` (110-257) picks a weapon. It takes a named one, else an active weapon whose covered arc contains the target's bearing (`getCurrentBattleCoveredDirections`, 166-183), else the first weapon (184-188). Launchers get a `TARGETSET(x,y,z)` (199-204). Their ammunition starts at the ship's position plus `ship radius + ammo radius + 1 + a forward-speed term` along the aim (219-228). The program then leads the target with `calculateIntercept`, limited to the ammunition's top speed (`speed stat / 100 × VELOCITY_LIGHT`, line 231) and a 300000 ms chase (229-232). `AIMSET` and `FIRE` follow (241-246).
- `WEAPONn PCT` sets the weapon's power to the given fraction of its capacity (`POWERSET`, 259-296). `SHIELDS UP` or `DOWN`, and `SHIELDn PCT`, do the same for shields (298-381).
- As a provider, the tactical program answers TARGETING with the target's name, and with its coordinates only when the target is stationary and bigger than an asteroid (385-415, 399-402).

### 10. Diagnostics (ShipDiagProgram)

- `DIAG 1-3 [system]` and `DAMAGE` (ShipDiagProgram.java:205-209, 555-660). Level 1 reports condition, level 2 adds power draw, and level 3 adds efficiency.
- A job takes time: `count × tick × k`, where `k` is 1, 3 or 9 for levels 1, 2 and 3, and `count` is 1 for one named system or hull-plus-components for the whole ship (613-654). The tick is `TIME_TICK` = 4000 ms by default (CMProps.java:681).
- Levels 2 and 3 measure power with a temporary `ExtendableAbility` attached to the ship's Area, which expires after 10 minutes (`makePowerWatcher`, 149-161; attached at 629-633 and 644-648). Its `MsgListener` pairs the value offered to each component's power message with the value left after it (114-147). Average draw is `ceil(sum / count)` (423, 496).
- Level 3 efficiency is `100 × install factor × condition × manufacturer efficiency × reliability` (469-471, 503-505).
- Condition is the percentage of uses remaining (293-294, 308-309), drawn as a five-star bar (243-261) with a colour from green (100) down to red (under 25) (263-281).

### 11. Pilot console (RocketShipProgram)

The shuttle console is the top of the stack and the one thing a pilot types into. `isCommandString` lists its verbs (RocketShipProgram.java:93-121). `onTyping` (440-676) is a chain of `if` tests that calls each procedure. The status screen (`getFlightStatus`, 123-217) shows "Docked at", or Speed, Direction, Orbiting or Altitude, Sector and Location, then Facing and the running track with its state. `getSensorMenu` (230-321) lists each sensor and its last report, skipping any with zero power capacity ("its just a window", 242-243). `getShipSystemsMenu` (323-400) lists ENGINEn, WEAPONn, SHIELDn and SYSTEMn with status and fuel.

### 12. TELNET and DIAGTERM

- **TELNET** (ShipTelnetProgram.java) is `ArchonOnly` (line 40). `TELNET host port` opens a plain TCP socket from the game server in `checkActivate` (145-172), and the constructor blocks (158). Reads are polled on power pulses with `available()` (202-237). Writes go out on each typed line (240-261), and the session closes when no one is reading the computer (264-271). There is no telnet option handling; the only byte-level filter drops NUL bytes (216-217).
- **DIAGTERM** is a JavaScript file (`Items/Software/DiagProgram.js`, not in the manifest). Its header lines `//extends` and `//implements ArchonOnly` are read by the JavaScript class loader (CMClass.java:3065-3110). `onTyping` runs whatever the player types through `eval`, after turning backticks into quotes (DiagProgram.js:53-71).

## Content worth mining

**Software catalogue**

| Program | Typed word | What it does | Notes |
|---|---|---|---|
| HealthScanProgram (`HealthScanProgram`) | `HEALTHSCAN [target]` | Life, health, disease and sleep readout for one mob | Health percentage is wrong (Problems 1) |
| LifeScanProgram | `LIFESCAN` | Lists living things within two rooms, continuous or on demand | Continuous mode stops after four rescans |
| EmissionScanProgram | `EMISSIONSCAN` | Lists activated electronics within three rooms | Named "lifescan" by mistake |
| DatabaseProgram | `DATABASE` or `DB` | JSON knowledge base of places, names and notes | Editing verbs need the program to be alone on screen |
| ShipSensorProgram | `SENSORn ...` | Sensor reports, names and aim | Reached through the pilot console |
| ShipNavProgram | `ENGINEn`, `LAUNCH`, `LAND`, `ORBIT`, `APPROACH`, `COURSE`, `FACE`, `MOON`, `STOP`, `CANCEL` | Thrust, turning and flight | Largest file |
| ShipTacticalProgram | `TARGET`, `FIRE`, `WEAPONn`, `SHIELDS` | Targeting and fire | TARGETING provider |
| RocketShipProgram | the full pilot verb set, `HELP` | Pilot console | Root program, starts on power |
| ShipDiagProgram | `DIAG 1-3`, `DAMAGE`, `HELP` | Timed diagnostics | Hull power shows 0 |
| ShipTelnetProgram | `TELNET host port` | Raw TCP session | ArchonOnly |
| DiagProgram (JS) | `DIAGTERM` | JavaScript eval console | ArchonOnly |
| GenSoftware | (generic) | Gen-editable disk with data-held names | Described as a "tricorder minidisk" (GenSoftware.java:61) |

**Pilot help text** (RocketShipProgram.java:456-471) gives the verb list: ACTIVATE and DEACTIVATE `[SYSTEM/ALL]`, LAUNCH or ORBIT, TARGET, FACE, APPROACH, COURSE, MOON, `[SENSOR NAME] [DIR]`, FIRE `[WEAPON]`, STOP, LAND, CANCEL, and HELP. The ENGINE help (480-488) explains that AFT gives sustained acceleration and the other directions give corrective bursts.

**Tech commands** (Technical.java:184-200). Those used in this bucket are `THRUST(dir, value)`, `ACCELERATED(dir, accel, effective, speed change)`, `SENSE(sensor, flag)`, `POWERSET(value)`, `DIRSET(dir)`, `AIMSET(x, y)`, `TARGETSET(x, y, z)`, `FIRE()` and the four service verbs `SWSVCALLOW`, `SWSVCNEED`, `SWSVCREQ` and `SWSVCRES`.

**Short messages worth lifting:** "Target set for @x1.", "Course plotted.", "Failed to plot course.", "Launch procedure initiated.", "Approach completed, stop initiated.", "Stop program completed successfully.", "Processing diagnostic level @x1 (@x2%)", "Diagnostic level @x1 completed.", "Multiple targets selected.  Please narrow search.", "Telnet connection closed."

**Scanner phrasing:** "No life signs detected.", "No emissions detected.", "Something on <name>", "Something in <container>", "Something inside something else", "<race>" with the direction phrases, and "is sleeping".

**Named places:** "Nav Point" (ShipNavProgram.java:749, 2068), "Safe Point" (820), "Orbital Point" (1835) and "Orbit Point above <zone>" (1967).

**Condition bars** (ShipDiagProgram.java:245-260): `*****`, `****-`, `**** `, `***  `, `**   ` and `*    `, from good to failing.

**Systems and states.** Parts are named ENGINEn, SENSORn, WEAPONn, SHIELDn and SYSTEMn. Flight states are the nine `ShipNavState` values (ShipNavProgram.java:144-156).

**Tech levels.** Engine and sensor screens show a tech-level name from the list `TECH_LEVEL_NAMES` (RocketShipProgram.java:498, 532, 555; `resources/lists.ini:1767-1769`), which runs Mk.I to Mk.XX and then Ver2.0 onward.

## Notable design ideas

1. **Scanning as a look.** Detection is a control message that rooms can veto (LifeScanProgram.java:141-142), so closed doors and other world rules apply to scanners with no special code.
2. **Power as clock and bill.** Each installed program takes one unit per pulse, or two in the foreground (StdComputerConsole.java:715-719). Devices run on the hardware's own pulse, with no separate game tick.
3. **Calibrate, do not trust.** `profileThrusters` measures each engine across 11 injection levels and interpolates after that (ShipNavProgram.java:515-573, 291-308).
4. **Turn by feedback.** `changeFacing` measures one probe burn and then corrects in proportion (393-491).
5. **Detours from knowledge.** Navigation only detours around obstacles that the ship has sensed or the database knows about (797-799). The plotted route reflects what the crew knows, not the world's truth.
6. **Instrumenting by listener.** Diagnostics attach a temporary ability to the area for ten minutes and observe power messages directly (ShipDiagProgram.java:114-161), so measurement needs no special hooks in the hardware.
7. **A knowledge base that can say "unknown".** A placeholder object with radius 0 gives a note a place in space even where nothing has been sensed (DatabaseProgram.java:430-435).
8. **Capability requests between devices.** Programs ask for a service by name (IDENTIFICATION, TARGETING) without knowing the provider (StdProgram.java:403-449). A missing provider is reported as a failure, not an exception.
9. **Views over shared lists.** The pilot console's sensor and systems screens are views over the same component lists (RocketShipProgram.java:230-400).

## Problems and cruft

Everything below was verified by reading the code, and none of it was run.

### Wrong behaviour

1. **Health percentage is almost always 0.** `CMath.toPct(curHP / maxHP)` divides two `int`s (HealthScanProgram.java:87; `getHitPoints` returns `int`, CharState.java:107), so the result is 0 unless the target is at full health. A zero maximum would throw `ArithmeticException`.
2. **`fasterTurns()` ignores inertials.** The inner test is `activated && (!wear || uses>30)` with a precedence error (ShipNavProgram.java:258-259), and the method ends with `return true;` (262) whatever the inertials, so any engine with four lateral ports reports "faster turns" (252-263).
3. **All engine profiles share one array.** `accelValues` is created once, outside the engine loop (526), and passed to every `EngProfile` (564). `EngProfile` keeps the reference (231). After profiling, every engine reads the last engine's chart.
4. **A sensor aim drops a direction.** For three or more directions, the loop sends `dirs[0]`, `dirs[1]`, `dirs[1]` and never `dirs[2]` (ShipSensorProgram.java:384-392). The index at line 389 should be the loop variable.
5. **Continuous scanning stops after four rescans.** `activatedTickdown` is set only in `onActivate` (LifeScanProgram.java:296; EmissionScanProgram.java:309) and decremented on each pulse (325; 338). Because it is a `short`, it would wrap back to positive after roughly 32,000 further pulses and restart scanning (arithmetic from the type; unverified in practice).
6. **Landing with no landing zone throws.** If a massive body is in range but has no landing rooms, `landProcedure` calls `new ShipNavTrack(LAND, planet, engines, null)` (ShipNavProgram.java:2004). The constructor calls `args[i].getClass()` on every argument (98-104), so the null throws `NullPointerException`. How the command handler reacts was not traced.
7. **COURSE to coordinates cannot work.** Nothing in the source tree declares a provider for COORDQUERY (the enum is at Software.java:56-61, and grep finds no other reference). `findCoordinates` therefore always returns null (GenShipProgram.java:507-524), and `COURSE x,y,z` ends in "Unable to find course target" (ShipNavProgram.java:2052-2055). The COORDQUERY responders in DatabaseProgram (1313-1340) and ShipSensorProgram (397-437) are unreachable.
8. **Database custom fields.** Printing an entry with a custom field and no `NOTE` throws `NullPointerException`. The loop calls `jo.get("NOTE").toString()` for every non-basic field (DatabaseProgram.java:634-640, at 639). When a note exists, it is printed once per custom field, instead of printing each field's value.
9. **`changeFacing` reports success when feedback is missing.** The loop breaks when no `ACCELERATED` reply arrives (ShipNavProgram.java:429-430, 449-450, 480-481), but the result test is `tries > 0` (505-506). A broken engine therefore reads as a successful turn. The callers that test for engine failure (1198-1202, 2269-2271) rarely see it.
10. **A failed fire says "Unknown command".** `sendMessage` reports "Error: Unknown command" when the message is null (GenShipProgram.java:192-195). When the aim or fire step fails (ShipTacticalProgram.java:249, 252), the final send at 254 reports that message.
11. **Shields cannot be deactivated by name.** `deactivateProcedure` searches engines, sensors and misc systems (GenShipProgram.java:628-635), and misc systems exclude shields (326). So `DEACTIVATE SHIELD1` returns "Unknown system". `activateProcedure` does search shields (570).
12. **A boxed `Double` is compared with `!=`.** `setThrust` tests `thrustInject != this.lastInject` (ShipNavProgram.java:342). That compares references, so the de-duplication almost never applies and repeated THRUST messages go out.
13. **A units comment is off by ten.** ShipNavProgram.java:1476 says "dam/s is km/s times 10". `VELOCITY_LIGHT = 29979245` (SpaceObject.java:291) is the speed of light in decametres per second, and a kilometre is 100 decametres (Distance enum, SpaceObject.java:158-163). The comment's factor is wrong.
14. **The emission disk has the wrong name.** It is called "a lifescan minidisk", a copy-paste (EmissionScanProgram.java:59).
15. **Diagnostics header and hull power are constants.** `damageFound` is always `false` (ShipDiagProgram.java:286, 333, 384, 448), so the red damage header never shows. Hull power is hard-coded to 0 (399, 463).
16. **Power-loss shutdown never fires in three programs.** DatabaseProgram (1234), ShipTelnetProgram (274) and ShipDiagProgram (666) compare against a timer that their own `checkPowerCurrent` refreshes in `okMessage` (DatabaseProgram.java:222-226; ShipTelnetProgram.java:196-200; ShipDiagProgram.java:237-241). That happens immediately before `onPowerCurrent` runs (StdComputerConsole.java:716-718). The only power-loss shutdown I found is the computer's own 12-second check. Telnet's reader check (269-271) still works.

### Dead or unused code

17. `ShipSensorProgram.onPowerCurrent` lines 182-196 never run. The `super` call has just advanced the same `nextPowerCycleTmr` (GenShipProgram.java:161-164), so the test at 182 is false.
18. Nothing in the tree calls `appendToLength` (RocketShipProgram.java:219), `performSingleThrust` (ShipNavProgram.java:364), `getPeerComputingRooms` (StdProgram.java:317), `findTargetAcceleration` (ShipNavProgram.java:375) or `getComponent` (GenShipProgram.java:248).
19. `HealthScanProgram` returns "No life signs detected." only when the string is empty (126-127). The first line is always appended (83), so that branch is dead.
20. A non-launcher's `TARGETSET` is built (ShipTacticalProgram.java:202) and overwritten (242) without being sent. Only launchers reach the send at 204.
21. `changeFacing` calls `getOppositeDir` at line 407 and discards the result, with a comment saying it "normalizes" the facing.
22. The comment at HealthScanProgram.java:61 ("the higher the weight, the wider the scan") was copied from the scanner. Health scanning does not use weight.

### Security and multiplayer assumptions

23. **DIAGTERM is an eval loop.** `eval(cmd)` runs typed text as JavaScript with access to `Packages` (DiagProgram.js:65). The only gate is the `ArchonOnly` marker, applied through `//IMPLEMENTS` (CMClass.java:3092) and checked at creation (Commands/Create.java:282-285). Anything that puts this item in a non-admin's hands is code execution.
24. **TELNET makes the server a dialler.** `ShipTelnetProgram` opens an outbound TCP connection to any host and port the player gives (checkActivate, 145-172), from the game server, with a blocking connect (158) and no timeout. `shutdown` closes the socket in a `finally` without a null check (127-130), and the outer `catch` hides the resulting exception (132).
25. **Static caches shared across ships.** `cachedComponents` is static, keyed only by component ID (GenShipProgram.java:70, 465-466), and is cleared for every program on any deactivation (145-146). It assumes IDs are unique across the whole game.
26. **Factory actors everywhere.** Program messages are sent by `CMClass.getFactoryMOB()` (for example StdProgram.java:342, ShipSensorProgram.java:135 and 205). That works, but the source of an action is never a real person.
27. **Unverified:** `EmissionScanProgram.onPowerCurrent` passes a null viewer when the computer sits on the floor (340-341). The calls `canBeSeenBy(…, viewerM)` and `name(viewerM)` at lines 150, 178 and 185 then receive null. I did not check whether they tolerate it.

### Cruft

28. `nextPowerCycleTmr` is redeclared in DatabaseProgram (53), ShipTelnetProgram (51) and ShipDiagProgram (48), hiding GenShipProgram's field (56).
29. `throw null` serves as a control-flow signal for a missing argument (ShipNavProgram.java:122, 133). It is caught as an NPE in `doNavigation` (1123) and in RocketShipProgram (209).
30. `DatabaseProgram.convertStringToCoords` (267-284) repeats GenShipProgram's version (488-505) line for line.
31. `RocketShipProgram.getActivationMenu` reads the `engines` field directly (415) and relies on `getShipSystemsMenu` having filled it earlier (327). It works, but it is fragile.
32. The verb dispatch in `RocketShipProgram.onTyping` (440-676) is a long `if`/`else` chain of `equalsIgnoreCase` tests. The `!HELP` test at line 664 is always true, because every HELP path returns earlier.
33. DatabaseProgram's weight of 100 is a comment only (71).

## Takeaways for a Python SUD

**Borrow**

1. **Devices as listeners on a power pulse.** Keep the pulse as a per-device event. The device draws power on each pulse, more while it is in the foreground (StdComputerConsole.java:715-719). A sketch:

```python
@dataclass
class Program:
    name: str
    verbs: dict[str, Callable[[list[str]], str]]      # typed command -> screen text
    provides: set[str] = field(default_factory=set)   # e.g. {"TARGETING"}
    wants: set[str] = field(default_factory=set)      # e.g. {"IDENTIFICATION"}

    def on_pulse(self, foreground: bool) -> int:
        """Called on every pulse; returns the power units drawn."""
        return 2 if foreground else 1
```

   If the SUD is turn-based, the pulse becomes the turn. If it is real-time, GenShipProgram's 8-second cycle is a sensible default.
2. **A service registry instead of a handshake.** Replace SWSVCALLOW, NEED, REQ and RES (StdProgram.java:403-449) with `registry.provider("IDENTIFICATION")` and a plain call. Keep the "no X service" failure message for a missing provider.
3. **Calibrated engines as data.** Store each engine's 11-point chart as data, measured once or set by the designer, and interpolate it as in ShipNavProgram.java:291-308. The "3 G without dampeners" rule (SpaceObject.java:261) becomes a single cap.

```python
def injection_for(chart: list[float], g: float) -> float:
    """chart[i] = acceleration at injection i/10, for i = 0..10; must increase."""
    for i in range(10):
        lo, hi = chart[i], chart[i + 1]
        if lo <= g < hi:
            return i / 10 + 0.1 * (g - lo) / (hi - lo)
    return 1.0
```

4. **Scanning as a perception check.** Make `can_perceive(scanner, target, room)` a world rule that every scanner must pass. Keep the phrase set ("Something on X", "Something inside something else", "No life signs detected.") and make the range a per-device value, like weight plus one rooms (LifeScanProgram.java:233).
5. **A player-kept knowledge base.** DatabaseProgram's model (keys as names or coordinates, values with NAME, NOTE and COORDS plus free fields, and placeholder entries flagged as not real) suits a single-player notebook or map-notes feature. Keep the query grammar (exact, `prefix*`, `*suffix`, `*infix*`, NEAR) and the "narrow your search" answer.
6. **Measure by observing.** The diagnostics listener pattern (ShipDiagProgram.java:114-161) suits a maintenance mechanic in which the game watches what the devices actually draw.

**Simplify**

- Drop TELNET (an ArchonOnly outbound socket has no use in a single-player game) and DIAGTERM (eval).
- Replace circuit keys with "devices in this ship or room".
- Pass a `source` object instead of building factory actors.
- Keep one thrust model. Avoid the ORBITCRUISE state overwrite unless you want orbits that teleport.
- Keep the navigation small: LAUNCH, APPROACH, STOP, LAND and ORBIT, each a function that returns done or not done. The nine-state ladder is hard to test.

**Drop**

- The COORDQUERY service (never wired up), the Gen stat plumbing (b172 covers the item system), and the wide pilot verb set. Use one verb table with one place to validate arguments.

**Multiplayer assumptions to rethink**

- Locking (`synchronized`, `volatile`) in every program is unnecessary for single-player.
- The `ArchonOnly` and creation gate (Create.java:282) assumes many players and admins. Replace it with a debug-only switch.
- Veto-phase side effects (ShipNavProgram.java:1718-1724) should not happen. Keep world vetoes as pure functions and run device logic on the tick.
- Shared static caches (GenShipProgram.java:70) should be per-ship state held on the ship object.

## Coverage

**Read in full (13 manifest files).** StdProgram.java, GenSoftware.java, GenShipProgram.java, ShipNavProgram.java (2516 lines, in four chunks), DatabaseProgram.java (1342 lines, in two chunks), RocketShipProgram.java, ShipDiagProgram.java, ShipTacticalProgram.java, ShipSensorProgram.java, ShipTelnetProgram.java, HealthScanProgram.java, LifeScanProgram.java and EmissionScanProgram.java. Skipped only the package and import blocks (lines 1-19 in most) and the licence headers.

**Read in full (not in the manifest).** `Items/Software/DiagProgram.js` (71 lines), which sits in the same folder.

**Consulted by targeted reads** (each to confirm one claim): Items/interfaces/Software.java (56-61, 190-192); Items/interfaces/Technical.java (184-200); Items/interfaces/ShipDirectional.java (50-58); core/interfaces/SpaceObject.java (158-185, 238-269, 291-295); core/CMProps.java (681); core/CMClass.java (3024-3110, the JavaScript loader); Items/CompTech/StdComputerConsole.java (698-730); Items/BasicTech/GenSpaceShip.java (425-432); Items/BasicTech/StdTriCorder.java (476-486); Items/Basic/GenCaged.java (135-157); Commands/Create.java (278-285); resources/lists.ini (1767-1769); Common/interfaces/CharState.java (107); MOBS/interfaces/MOB.java (588-602); core/CMath.java (1097). Also searched the tree for COORDQUERY providers, for call sites of the unused helpers, and for `setValue` in Items/Software.

**Skipped.** Nothing in the bucket was skipped.
