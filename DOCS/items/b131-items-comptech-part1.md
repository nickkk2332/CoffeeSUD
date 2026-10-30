# CompTech Gen items, part 1: sensors, power, drives and ship systems

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b131` (Items/CompTech [part 1/2: GenBussardCollector .. GenSpaceDrive]) · 27 files · 6350 lines of Java.

Citation convention: `path:line` is relative to the CoffeeMud folder. A bare name such as `GenLightSwitch.java:84` means `com/planet_ink/coffee_mud/Items/CompTech/GenLightSwitch.java:84`. Other paths are written in full. Behaviour described as "by reading" was not executed.

## Summary

The 27 `Gen*` classes are the builder-editable layer of CoffeeMud's ship and electronics components: batteries, generators, panels, light switches, sensors, engines and thrusters, shields, weapons, a viewscreen and a window. Each one is a thin subclass of a `Std*` class in the same folder, which holds the mechanics. The Gen layer adds a default name, a builder stat table exposed as string codes, XML persistence and a few overrides. The mechanics worth studying are the sensors (what a ship can see depends on apparent size, mass and occlusion), the circuit model (generators produce only when polled and their stored power is pooled each tick), the velocity-limited impulse drive, and a light-switch network in which hidden switches follow visible ones. Most of the code is repeated stat-table boilerplate, and it contains several real bugs, listed under Problems.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `GenShipViewScreen` | Viewscreen: lists contacts in its facing directions, look command, DIRSET control, arrival and departure messages | 753 |
| `GenShipOpticalSensor` | Optical sensor base for the viewscreen and window: apparent-size occlusion, docked view, passive sight | 622 |
| `GenGraviticSensor` | Gravitic sensor: detects mass, not identity; size-class names; mass falloff and mass-based occlusion | 620 |
| `GenElecCompSensor` | Generic sensor base (stat table only) | 170 |
| `GenShipWindow` | Free, always-on, omnidirectional viewport (subclass of the viewscreen) | 103 |
| `GenShipEngine` | Fuel-burning ship engine (stat table on `StdShipEngine`) | 283 |
| `GenShipThruster` | Fuel-consuming thruster, weight 5000 | 278 |
| `GenShipFuellessThruster` | Fuelless drive ("space drive") | 216 |
| `GenSpaceDrive` | Near-copy of `GenShipFuellessThruster` with a different ID and a setter bug | 216 |
| `GenImpulseEngine` | Engine whose maximum thrust falls as forward speed rises | 100 |
| `GenImpulseThruster` | Thruster with the same velocity-limited maximum thrust | 100 |
| `GenShipDampener` | Inertial dampener (stat table on `StdShipDampener`) | 170 |
| `GenShipGravityGen` | Artificial gravity generator (stat table on `StdShipGravityGen`) | 170 |
| `GenShipInertialBattery` | Battery that absorbs braking speed (stat table on `StdShipInertialBattery`) | 170 |
| `GenShipShieldGenerator` | Shield generator; stat table includes damage message types | 205 |
| `GenShipWeapon` | Ship weapon; stat table identical to the shield generator | 205 |
| `GenCompLauncher` | Torpedo and probe launcher with permitted directions | 180 |
| `GenShipPanel` | Engineering panel: a container filtered by TechType | 223 |
| `GenLightSwitch` | Room light switch that is also an ElecPanel; timed power; ganged hidden switches | 239 |
| `GenCompEnviroSystem` | Environmental (air) system: power per pulse, periodic air refresh | 122 |
| `GenComputerConsole` | Rideable, readable computer console with mount and state strings | 267 |
| `GenCompBattery` | Battery, 1000 capacity by default | 172 |
| `GenCompGenerator` | Fuel-burning generator with fuel list and door and lock fields | 250 |
| `GenFuellessGenerator` | Base for fuelless generators; stat table with generated amount | 189 |
| `GenSolarGenerator` | Generates when the sun can be seen (planet, dock, or star in space) | 88 |
| `GenBussardCollector` | Generates while its ship moves faster than sublight | 69 |
| `GenElecCompItem` | Generic electronics item base for this bucket (stat table) | 170 |

All class files are in `com/planet_ink/coffee_mud/Items/CompTech/`. `GenWindGenerator` and `GenWaterPowerGenerator` are in part 2 and are not covered here.

## How it works

### 1. The Gen pattern

- **Identity and defaults.** `ID()` returns the class name (`GenLightSwitch.java:41`). Constructors set the default name and room text, for example `GenLightSwitch.java:48-63` and `GenShipWindow.java:44-52`. `isGeneric()` is true (`GenElecCompItem.java:51-55`).
- **Persistence.** `text()` and `setMiscText()` round-trip the item through `CMLib.coffeeMaker().getEnvironmentalMiscTextXML` and `unpackEnvironmentalMiscTextXML` (`GenElecCompItem.java:57-68`; encoder at `com/planet_ink/coffee_mud/Libraries/CoffeeMaker.java:214`), then call `recoverPhyStats()`.
- **Stat table.** `MYCODES` lists the builder-visible codes. For `GenElecCompItem` they are `POWERCAP, ACTIVATED, POWERREM, MANUFACTURER, INSTFACT, RECHRATE` (`GenElecCompItem.java:71`). `getStatCodes()` (`:141-155`) puts the common `GenericBuilder.GenItemCode` names first (`com/planet_ink/coffee_mud/Libraries/interfaces/GenericBuilder.java:56-68`: CLASS, USES, LEVEL, ABILITY, NAME ... TAGS), then the class's own codes, and caches the result in a static field.
- **Dispatch by position.** `getStat` and `setStat` switch on the index of the code in `MYCODES` (`getInternalCodeNum`, `GenElecCompItem.java:129-137`). Unknown codes fall back to `CMProps.getStatCodeExtensionValue` (`:93`, `:124`). The array and the switch must agree by position. Several classes do not (see Problems).
- **Equality.** `sameAs()` is true when every builder code reads the same (`GenElecCompItem.java:157-169`).

### 2. Sensors

**Base mechanics (Std, read for context).** Sensing is on demand. A control program sends a SENSE command, and `doSensing` runs only then (`StdElecCompSensor.java:748-756`). It converts each contact with `getSensedObjectConverter()` and sends one message per contact (`:647-673`). A successful scan costs one power unit (`:752-754`). Range is `getSensorMaxRange()` times the computed efficiency (`:576-583`). A reliability roll fails at most once per 360 000 ms (`:587-593`) and then drops the farthest tenth of the results (`:594-602`). Passive traffic goes through `canPassivelySense` and reaches attached screens as "`<name> detects: ...`" (`:795-798`).

| Sensor | Range | Reads | Hidden by |
|---|---|---|---|
| Optical (`GenShipOpticalSensor`) | 1 parsec | apparent size and generic name | a larger apparent disc on the same line of sight |
| Viewscreen (`GenShipViewScreen`) | 1 parsec | apparent size of at least 0.0015 rad, in facing directions | the optical occlusion, inherited |
| Window (`GenShipWindow`) | 1 astronomical unit | as the viewscreen, in every permitted direction | as the viewscreen |
| Gravitic (`GenGraviticSensor`) | 1 parsec | mass, size-class name, distance | a heavier object on the same line of sight |

**Optical sensor (`GenShipOpticalSensor`).**
- Range is `SpaceObject.Distance.Parsec.dm` (`GenShipOpticalSensor.java:56-59`; `com/planet_ink/coffee_mud/core/interfaces/SpaceObject.java:172`, 3 085 677 580 000 000 dm).
- The converter (`:68-444`) wraps each contact in a proxy whose `name()` is the object's `genericName()` (`:61-66`, `:77`). The proxy's `Name()` still returns the real name (`:88-91`) beneath the comment "do not show!". Whether any output path calls `Name()` was not traced.
- Apparent size is `atan(radius/distance)`, computed in double (`:480-500`, `:494`).
- Occlusion (`isHiddenFromSensors`, `:452-478`): a contact is hidden when another object looks larger (`hL < bL`) and the cone from the ship to the contact meets that object's sphere (`:469-473`).
- Docked, the sensor has no position in space. It lists the dock room, visible inhabitants, and items with display text (`:527-561`); the room itself is added at `:547`.
- Passive sight (`canPassivelySense`, `:565-606`) applies only to `MASK_EYES` messages (`:576-578`). Its occlusion test uses integer division (`:592`, `:600`; see Problems).
- It clears `SENSE_ITEMNOTGET` (`:51-52`), so the sensor can be taken from a room. The light switch sets that flag (`GenLightSwitch.java:59`).

**Gravitic sensor (`GenGraviticSensor`).** It detects mass rather than identity.
- Range is one parsec (`GenGraviticSensor.java:53-55`).
- Contacts get a size-class name from their radius (`:58-94`; table in Content).
- The proxy exposes the real `getMass()` (`:449-456`). `direction()` is a zero vector (`:403-406`), `speed()` is 0 (`:413-417`) and `knownTarget()` is null (`:424-428`). The player learns mass and distance, not heading.
- Detection filter (`:478-499`): the ship itself, non-space objects and anything within the sum of the two radii are rejected (`:488-496`). Otherwise an object is kept only when `adjustedMax > 100`, where `adjustedMax = round(mass × (1 − distance / maxRange))` (`:493-496`). Detectability falls linearly to zero at one parsec.
- Occlusion (`:501-530`): a lighter contact is hidden when a heavier object lies in the cone (`:515-524`).
- Not in space (`:535-552`): the sensor returns only the dock-target body or the ship's own space object.
- Passive (`canPassivelySense`, `:574-611`): only `MASK_MOVE` messages (`:585`), blocked when an object in the cone has `mass >= target.mass` (`:606`).
- `getMassDetected()` (`:469-476`) is never called. It is dead code.

**Viewscreen (`GenShipViewScreen`) and window (`GenShipWindow`).**
- Defaults: all six `ShipDir` values are permitted (`GenShipViewScreen.java:46`), with one facing direction (`:47`). Range is one parsec (`:59-63`).
- `getFacingDirs()` (`:117-131`) returns `{FORWARD}` when FORWARD is permitted and only one direction is set. Otherwise it returns the first N entries of the permitted list (`:125-128`). The enum order is AFT, PORT, VENTRAL, DORSEL, STARBOARD, FORWARD (`com/planet_ink/coffee_mud/Items/interfaces/ShipDirectional.java:52-57`). So a screen with two directions and the defaults faces AFT and PORT, not FORWARD.
- Filter (`:551-583`): a contact must lie in a facing direction (`:562-570`) and subtend at least `0.0015` rad (`:572-575`). This check uses doubles and is correct.
- Look (`:586-634`): in space the screen lists the filtered contacts with `CMLib.lister()` under "You see:" (`:621-631`), or "You see the blackness of space." when the list is empty (`:614-620`). Docked, look is handed to the dock room (`:597-603`).
- DIRSET (`:652-670`): with one permitted direction it replaces the facing (`:660-661`). With several, it drops the oldest facing and appends the new one (`:663-668`).
- Arrival and departure (`:65-115`): "`<T-NAME> appears on @x1.`" and "`<T-NAME> is no longer on @x1.`" (`:76-77`, `:102-103`), sent only when the room has inhabitants (`:71`, `:97`).
- `GenShipWindow` (`GenShipWindow.java:44-103`) is a free, always-on viewport. It reports zero capacity, one unit of remaining power, always activated, no power needs and no power requirement (`:60-88`). Its facing is every permitted direction (`:90-94`), and its range is one astronomical unit (`:54-58`; `SpaceObject.java:163`).

### 3. Power and circuits

Power is distributed by a tick process in `GroundWired`. Each circuit is serviced on the support tick (`com/planet_ink/coffee_mud/Libraries/GroundWired.java:510`, call at `:841`), and `processElectricCurrents` (`:548-650`) does three things.
1. It sends a POWERCURRENT message with value 0 to every generator (`:551-565`). An active generator that receives value 0 produces power in response (`StdCompFuellessGenerator.java:117-131`).
2. It collects each active generator's stored power and zeroes it (`:571-575`). It does the same for active batteries.
3. If the pool is empty it sends 0 to panels and batteries (`:588` onward). Otherwise each ElecPanel receives a share in proportion to its `powerNeeds()`, with a floor of 1 (`:636-643`).

**Generators.** `StdCompFuellessGenerator` defaults to capacity 1000 and one unit per tick (`StdCompFuellessGenerator.java:54`, `:61`). Output is `amount × computed efficiency × manufacturer efficiency`, capped at capacity (`:124-129`). The fuel-burning `StdCompGenerator` has the same shape without a `canGenerateRightNow` check (`StdCompGenerator.java:110-123`). The Gen generators in this bucket differ only in `canGenerateRightNow()` and the amount:
- `GenSolarGenerator` (`:51-79`) runs when activated and: docked, if the dock room's area lets the sun be seen (`:59-61`); in space, if a star-sized body with radius at least `StarDRadius/2` (479 906 dm) and mass at least `radius × MULTIPLIER_STAR_MASS` (`SpaceObject.java:247`, value 1 989 000) lies within the system diameter (`:62-72`); or on a planet, if the climate lets the sun be seen (`:74-76`).
- `GenBussardCollector` (`:51-60`) runs only when its ship's speed exceeds `VELOCITY_SUBLIGHT` (`:57`; `SpaceObject.java:291-293`: light speed 29 979 245 dm/s, sublight one quarter, 7 494 811 dm/s).
- `GenFuellessGenerator` is the base for these; `GenCompGenerator` is the fuel-burning variant.

**Batteries.** `GenCompBattery` starts full at 1000 (`GenCompBattery.java:43-51`).

Timing: the circuit runs on the tick; a generator's output is produced when polled, once per circuit tick. Stored power is not generated continuously.

### 4. Lighting: `GenLightSwitch`

A light switch is an ElecPanel of type CONTROL_PANEL (`GenLightSwitch.java:36`, `:230-233`) with capacity 1 and a recharge rate of 1.0 (`:56-58`).
- **Timer.** `LIGHT_TIME` is ten minutes of wall-clock time, 600 000 ms (`:45`). `nextPowerNeed` is set to now plus that at construction (`:46`), and renewed on each POWERCURRENT whose value is above 1 (`:215-221`). The circuit gives each panel a share with a floor of 1 (`GroundWired.java:641-642`), so a switch that receives only the floor does not renew. See Problems.
- **Effects on the room.** While on and within its timer, the switch's own stats gain IS_LIGHTSOURCE; otherwise IS_GLOWING (`GenLightSwitch.java:66-72`). Its `affectPhyStats` runs against the room when the room recovers its stats (`StdRoom.java:1299`; `com/planet_ink/coffee_mud/core/interfaces/EachApplicable.java:36-50`). When the switch is off or expired it removes glow and light bits from the room and sets IS_DARK (`:74-86`). Line 84 is buggy (see Problems).
- **Ganged switches.** Only a visible switch (non-empty display text and seeable; `:93`, `:143`) relays a signal. `powerOtherSwitched` walks the room graph through exits (`:95-133`) and sends a copy of the message to each hidden switch it finds (`:110-122`). A hidden switch is refreshed only if its timer is older than the source's (`:115`). That comparison is the only termination test, because the walk keeps no visited set. `tellOtherSwitches` does the same for toggling: hidden switches in the same state as the source are switched with `MASK_CNTRLMSG`, so no extra messages are shown (`:150`, `:164-167`).
- **Activation.** An ACTIVATE message calls `tellOtherSwitches`, then the base handler, then `activate(true)` and a room stat recovery (`:198-205`). The comments at `:201` and `:210` say the base call "would have toggled yet AGAIN". In fact `StdElecCompItem.java:245` calls `activate(true)` directly, so the explicit calls are redundant. Activation is refused unless the item is installed, and inside a panel the panel must be active (`StdElecCompItem.java:191-205`).

### 5. Drives, engines and thrusters

**Stat schema.** Engines and thrusters share `MAXTHRUST`, `MINTHRUST`, specific impulse (raw as `NA`, times 100 as `SPECIMPL`), `ISCONST`, `AVAILPORTS` and `RECHRATE`. Fuel-burning classes also take `CONSUMEDTYPES`. Fuelless drives do not.

**Thrust (Std, read for context).** `StdShipThruster`'s injected thrust is `installedFactor × (min + (max − min) × injection)`, capped at max and floored at `installedFactor × min` (`StdShipThruster.java:344-356`). The maximum is what the Gen classes change.

**Impulse classes.** `GenImpulseEngine` (`:71-85`) and `GenImpulseThruster` (`:71-85`) override `getMaxThrust()`:
- With a ship, the cap is `(VELOCITY_SUBLIGHT − max(0, v·cosφ)) × ship mass`, where φ is the angle between the ship's motion and its facing (`GenImpulseEngine.java:74-82`).
- At rest the cap is a quarter of light speed times mass. It falls as the ship moves forward. Above forward speed `VELOCITY_SUBLIGHT` the cap goes negative, and nothing in these classes clamps it (`:81-82`). The consequence for thrust was not traced.
- Without a ship the base value is used (`:84`). The constant is `maxSpeed = SpaceObject.VELOCITY_SUBLIGHT` (`GenImpulseEngine.java:53`).
- `getShip()` caches the ship and `setOwner` clears the cache (`:57-69`).

**Stat-code oddities.** `NA` returns the raw specific impulse and `SPECIMPL` returns it times 100 (`GenShipEngine.java:138-141`, `GenShipThruster.java:136-139`). `ISCONST` toggles `constantThrust` through `setReactionEngine` (`StdShipEngine.java:173-180`). The web grinder names the same flag `ISCONSTTHRUST` (`com/planet_ink/coffee_mud/WebMacros/grinder/GrinderItems.java:76`, `:763-764`).

### 6. Other ship systems

- **Inertial dampener** (`GenShipDampener` on `StdShipDampener.java:86-115`). On a `TYP_GRAVITY` message with value above 1, it reduces the excess over 1 and pays `rooms × manufacturer efficiency` power (`:90-111`). If it cannot pay in full it runs at the fraction it can afford (`:103-109`). Wear scales the cut (`:97-99`, `:112`).
- **Artificial gravity** (`GenShipGravityGen` on `StdShipGravityGen.java:88-128`). On a POWERCURRENT aimed at it, while active and while the ship has neither ARTI_GRAV nor IN_THE_AIR set, it pays `rooms × efficiency` (`:109-119`). It then sets ARTI_GRAV with probability `min(150, 150 × wear × power fraction)` percent (`:120-121`). This method never clears the flag. The ship does: `StdSpaceShip.java:675-676` resets a countdown to 2 and clears ARTI_GRAV when it reaches zero. So the generator holds a short lease that each powered pulse renews.
- **Inertial battery** (`GenShipInertialBattery` on `StdShipInertialBattery.java:102-173`). The battery registers an ACCELERATION listener on its ship (`:69-78`). When the ship is moving and a forward or aft accelerator points more than 90° away from its travel (`:129-132`), the battery absorbs speed up to `powerRemaining × computed efficiency × manufacturer efficiency` (`:134-136`). Absorbed speed costs the same amount of power (`:144`, `:147-152`). If power is short, efficiency drops and the course is randomly jittered (`:157-165`). The requested acceleration is reduced by the absorbed speed (`:168-170`). Its power need is `min(capacity − power, capacity × rate)` while active (`:82-86`).
- **Shield generator and weapon** (`GenShipShieldGenerator`, `GenShipWeapon`). The two classes are identical apart from names. Both have `SDIRNUMPORTS` and `SDIRPORTS` for permitted directions, and `SWARMTYPES` for damage message types, parsed against `CMMsg.TYPE_DESCS` (`GenShipWeapon.java:142-154`; `com/planet_ink/coffee_mud/Common/interfaces/CMMsg.java:1291`).
- **Launcher** (`GenCompLauncher` on `StdCompLauncher`). Its FIRE path (`StdCompLauncher.java:328-390`) launches the first contained item, but only when the target direction is in the battle-covered set and power and efficiency are nonzero. The launch speed is `(power / 100) × launch speed × efficiency` (`:384`). The default launch speed is `VELOCITY_SOUND` (`:375`).
- **Environmental system** (`GenCompEnviroSystem.java:67-113`). Each POWERCURRENT pulse while active costs one unit (`:89`) and shuts the system down when fewer than 10 remain (`:82-87`). Every sixth pulse (`ENVIRO_TICKS = 6`, `:47`, `:90-92`) it sends an AIRREFRESH command to the ship, with a fraction of `usesRemaining / 50` under wear and tear (`:98-103`).
- **Panel** (`GenShipPanel.java:46-58`). Capacity 25 000, weight 2, value 5. PANTYPE sets the panel's TechType by name and silently ignores names that do not parse (`:161-169`). `StdCompPanel` accepts a component only when TechTypes match (`StdCompPanel.java:92`).
- **Computer console** (`GenComputerConsole.java:44-82`). It inherits the rideable and computer behaviour of `StdComputerConsole` and adds ride basis, mount and state strings. `setMiscText` always sets SENSE_ITEMREADABLE (`:71`), and `setReadableText` does nothing (`:61-64`).

### 7. Messages shown to players (short quotes)

- Light switch default text: "A light switch is on the wall here." (`GenLightSwitch.java:53`).
- Viewscreen: "The screen is deactivated." (`GenShipViewScreen.java:594`); "You see the blackness of space." (`:617`, `:619`); "You see:" and "You see (dirs):" (`:625`, `:627`); "`<T-NAME> appears on @x1.`" (`:77`); "screen does not support the @x2 direction." (`:658`).
- Environmental system: "@x1 is currently operating." or "deactivated/disconnected." (`GenCompEnviroSystem.java:77`).
- Panel access: "The panel containing @x1 is not activated or connected." (`StdElecCompItem.java:204`).

### 8. Interactions

- `GroundWired` (power distribution), `CMLib.space()` (positions, directions, distances, occlusion), `CMLib.flags()` (seeable, light, glow), `CMLib.map()` (rooms and areas), `CMLib.tech()` (computers on a circuit key, `StdShipDampener.java:118-130`), `SpaceShip` and `Area` (ship state and flags), `Climate` (sun and weather).
- Light switches refresh a room with `R.recoverRoomStats()` (`GenLightSwitch.java:203`).

## Content worth mining

### Gravitic size classes

The gravitic sensor names a contact from its radius (`GenGraviticSensor.java:58-94`). Thresholds are in decameters from `SpaceObject.java:174-180`.

| Radius at least (dm) | Label used by the sensor |
|---|---|
| 347 750 000 (StarBRadius / 2) | a super star-sized object |
| 959 812 (StarDRadius) | a star-sized object |
| 6 026 800 (SaturnRadius) | an enormous planet-sized object |
| 3 013 400 (SaturnRadius / 2) | a huge planet-sized object |
| 1 279 750 (PlanetRadius × 2) | an large planet-sized object (sic; line 77) |
| 319 937 (PlanetRadius / 2) | a planet-sized object |
| 86 870 (MoonRadius / 2) | a moon-sized object |
| 26 300 (AsteroidRadius) | a moonlet-sized object |
| 2 630 (AsteroidRadius / 10) | an asteroid-sized object |
| below that | an object |

### Sensor and drive numbers

- One parsec: 3 085 677 580 000 000 dm. One astronomical unit: 14 959 787 100 dm (`SpaceObject.java:163`, `:172`).
- Sublight speed: 7 494 811 dm/s (`SpaceObject.java:291-293`).
- Viewscreen minimum apparent size: 0.0015 rad (`GenShipViewScreen.java:575`).
- Gravitic detection floor: adjusted mass above 100 (`GenGraviticSensor.java:496`).
- Sensor failure check: once per 360 000 ms, dropping the farthest tenth (`StdElecCompSensor.java:593-594`).

### Catalogue of the 27 types

TechType is the value that `getTechType()` returns. Where a class does not override it, the value is inherited from its Std parent.

| Class | Std parent | TechType | Distinct behaviour | Default name |
|---|---|---|---|---|
| `GenBussardCollector` | `StdCompFuellessGenerator` (via `GenFuellessGenerator`) | SHIP_GENERATOR | runs only above sublight | a bussard collector generator |
| `GenCompBattery` | `StdCompBattery` | SHIP_POWER | starts full at 1000 | a generic battery |
| `GenCompEnviroSystem` | `GenElecCompItem` | SHIP_ENVIRO_CONTROL | air refresh per six pulses | a generic environment system |
| `GenCompGenerator` | `StdCompGenerator` | SHIP_GENERATOR | burns a fuel list | a generic generator |
| `GenCompLauncher` | `StdCompLauncher` | SHIP_LAUNCHER | torpedo and probe launcher | a generic launcher |
| `GenComputerConsole` | `StdComputerConsole` | SHIP_COMPUTER | rideable, readable console | (Std default) |
| `GenElecCompItem` | `StdElecCompItem` | (Std default) | stat table only | a generic ship component |
| `GenElecCompSensor` | `StdElecCompSensor` | SHIP_SENSOR | stat table only | a generic ship sensor |
| `GenFuellessGenerator` | `StdCompFuellessGenerator` | SHIP_GENERATOR | base for fuelless generators | a power generator |
| `GenGraviticSensor` | `GenElecCompSensor` | SHIP_SENSOR | mass detection, size names | a gravitic sensor |
| `GenImpulseEngine` | `GenShipEngine` | SHIP_ENGINE | speed-limited maximum thrust | a generic impulse engine |
| `GenImpulseThruster` | `GenShipFuellessThruster` | SHIP_ENGINE | speed-limited maximum thrust | a generic impulse thruster |
| `GenLightSwitch` | `GenElecCompItem`, implements ElecPanel | CONTROL_PANEL | ganged switches, 10-minute timer | a light switch |
| `GenShipDampener` | `StdShipDampener` | SHIP_DAMPENER | cuts excess gravity | a generic inertial dampener |
| `GenShipEngine` | `StdShipEngine` | SHIP_ENGINE | fuel, consumed types | a generic ship engine |
| `GenShipFuellessThruster` | `StdShipFuellessThruster` | SHIP_ENGINE | fuelless drive | a generic space drive |
| `GenShipGravityGen` | `StdShipGravityGen` | SHIP_ENVIRO_CONTROL (see Problems) | artificial gravity lease | a generic gravity generator |
| `GenShipInertialBattery` | `StdShipInertialBattery` | SHIP_INERTIAL | absorbs braking | a generic inertial battery |
| `GenShipOpticalSensor` | `GenElecCompSensor` | SHIP_SENSOR | apparent-size occlusion | a generic optical ship sensor |
| `GenShipPanel` | `StdCompPanel` | SHIP_PANEL | TechType filter | an engineering panel |
| `GenShipShieldGenerator` | `StdShipShieldGenerator` | SHIP_SHIELD | damage message types | a generic ship shield generator |
| `GenShipThruster` | `StdShipThruster` | SHIP_ENGINE | fuel-consuming thruster | a generic thruster |
| `GenShipViewScreen` | `GenShipOpticalSensor` | SHIP_SENSOR (inherited) | facing directions, look, DIRSET | the view screen |
| `GenShipWeapon` | `StdShipWeapon` | SHIP_WEAPON | damage message types | a generic ship weapon |
| `GenShipWindow` | `GenShipViewScreen` | SHIP_SENSOR (inherited) | free, omnidirectional | the viewport |
| `GenSolarGenerator` | `GenFuellessGenerator` | SHIP_GENERATOR | runs in sunlight | a solar generator |
| `GenSpaceDrive` | `StdShipFuellessThruster` | SHIP_ENGINE | near-copy of the fuelless drive | a space drive |

Default text for generic items is set in each constructor (for example "a generic ship component sits here." in `GenElecCompItem.java:47`). Several classes set no name at all (`GenComputerConsole.java:44-47`).

## Notable design ideas

- **Sensing as an information filter.** A sensor never hands out the real object. Its converter exposes only what the sensor type allows: a generic label (optical), a mass (gravitic), or nothing more. The same function decides what the player may know.
- **Detection by mass falloff** (`GenGraviticSensor.java:493-496`). Detectable mass shrinks linearly with distance to zero at one parsec, with a floor and a "not inside me" test. It is one line of physics and gives a clear rule.
- **Occlusion as shadowing.** A lighter object is hidden behind a heavier one in the same cone (`GenGraviticSensor.java:515-524`). An optical target is hidden by a larger apparent disc (`GenShipOpticalSensor.java:469-473`).
- **Pull-and-pool power.** A generator produces only when the circuit asks, and the circuit collects all stored power each tick (`GroundWired.java:551-575`). Generators that depend on conditions (sun, sublight speed) become "works when conditions are right".
- **Speed-limited impulse drive** (`GenImpulseEngine.java:77-82`). Thrust capacity falls as forward speed rises, so the ship has a soft top speed without a hard cap.
- **Ganged switches.** Visible switches drive hidden ones across the room graph (`GenLightSwitch.java:95-133`, `:138-188`), so one switch can own a building's lights.
- **Renewed lease for gravity.** The generator must keep asserting ARTI_GRAV, and the ship clears it when no one renews (`StdSpaceShip.java:675-676`).
- **A free window.** `GenShipWindow` overrides the power and facing methods so that it always works and sees every permitted direction (`GenShipWindow.java:60-94`).

## Problems and cruft

Bugs, found by reading and not executed:

1. **Remaining-power setter writes capacity.** In three classes, the POWERREM branch of `setStat` calls `setPowerCapacity`: `GenFuellessGenerator.java:124-126`, `GenCompGenerator.java:179-181`, and `GenSpaceDrive.java:136-138`. A builder who sets remaining power changes the capacity. `GenShipFuellessThruster.java:137` and `GenElecCompItem.java:111-112` are correct.
2. **Integer division in optical occlusion.** `GenShipOpticalSensor.java:592` and `:600` compute `Math.atan(hO.radius()/hDistance)` and `Math.atan(cO.radius()/cDistance)`. `radius()` and `getDistanceFrom()` both return `long` (`SpaceObject.java:56`, `:149`), so the quotient truncates to 0 for any object smaller than its distance. The test `vC >= vO` at `:601` then becomes `0 >= 0`, and any object whose sphere meets the cone blocks the target. The comparison the code intends is lost. The viewscreen does the same test in doubles (`GenShipViewScreen.java:572-575`).
3. **The viewscreen converter wraps the screen, not the contact.** In `GenShipViewScreen.java:134-143`, the proxy's field is `final Environmental obj = me;`. Its sibling converters first declare a local `me = obj` (`GenShipOpticalSensor.java:73`; `StdElecCompSensor.java:137`). Without that local, `me` resolves to `StdItem.me` (`StdItem.java:67`), the screen itself. Read literally, every proxy names and identifies the screen. For the stock name "the view screen", `StdItem.genericName()` returns "an item" (`StdItem.java:197-202`). The "You see:" list (`GenShipViewScreen.java:621-631`) and the arrival messages would then describe every contact as "an item". This is the most visible bug in the bucket.
4. **Light switch writes its own disposition into the room.** `GenLightSwitch.java:84` sets `affectableStats.setDisposition(phyStats().disposition()|PhyStats.IS_DARK)`. `phyStats()` is the switch's own stats, and the write replaces the room's disposition instead of adding to it. An off switch carries IS_GLOWING (`:70-71`), which therefore also lands on the room. Whether that changes seeing in the dark (`com/planet_ink/coffee_mud/Libraries/Sense.java:1528-1530`, `:1059-1061`) was not traced.
5. **Fractional recharge rates are lost.** RECHRATE is a float (`TechComponent.java:74`). These branches parse it with `CMath.s_parseLongExpression`, which returns a long (`com/planet_ink/coffee_mud/core/CMath.java:1277-1284`): `GenFuellessGenerator.java:140`, `GenCompGenerator.java:201`, `GenComputerConsole.java:199`, `GenShipEngine.java:234`, `GenShipFuellessThruster.java:167`, `GenShipThruster.java:229`. Most other Gen classes use `(float)CMath.s_parseMathExpression`.
6. **Facing cache is never refreshed, with a possible null dereference.** `facingDirs` is computed once (`GenShipViewScreen.java:117-131`). `setPermittedDirections` (`:526-530`) and `setPermittedNumDirections` (`:538-542`) do not clear it, so builder changes apply only after a DIRSET or a reboot. The DIRSET path with several directions reads `facingDirs.length` (`:665`). If no contact has been filtered yet, `facingDirs` is still null and that line would throw. Reachability was not confirmed.
7. **Gravity generator reports the wrong TechType.** `StdShipGravityGen.java:61` returns SHIP_ENVIRO_CONTROL. `ElecPanel.java:63` lists SHIP_GRAVGEN as a panel category, and `com/planet_ink/coffee_mud/Commands/Reset.java:2548` assigns SHIP_GRAVGEN to a manufacturer group. Panels match by TechType (`StdCompPanel.java:92`), so a panel of type SHIP_GRAVGEN will not accept the generator. `GenShipGravityGen` inherits the wrong type.
8. **Silent failures on builder input.** `GenShipPanel.java:161-169` catches and ignores bad PANTYPE names. The SWARMTYPES parse (`GenShipWeapon.java:142-154`, `GenShipShieldGenerator.java:142-154`) leaves unknown names as 0, which is the type NOTHING (`CMMsg.java:1291-1292`).
9. **Possible null dereferences outside a ship.** `StdShipGravityGen.java:100-101` calls `ship.getShipFlag` without a null check, and `StdShipDampener.java:94-102` calls `ship.getArea()` the same way. The inertial battery does check (`StdShipInertialBattery.java:106-107`). Reachability was not confirmed.
10. **Light switch refresh threshold.** Renewal needs a value above 1 (`GenLightSwitch.java:217`), while the circuit's floor share is exactly 1 (`GroundWired.java:641-642`). A switch that gets only the floor never renews. Whether that is intended was not established.

Naming and data mismatches:

11. **Stat codes named wrongly.** `NA` (`GenShipEngine.java:89`, `GenShipThruster.java:89`, `GenShipFuellessThruster.java:86`, `GenSpaceDrive.java:87`) returns the raw specific impulse, and `SPECIMPL` returns it times 100. `ISCONST` is the constant-thrust flag. The web grinder calls it `ISCONSTTHRUST` and calls specific impulse `SPECIMPULSE` (`GrinderItems.java:74`, `:76` for `ISCONSTTHRUST`), so the two editors use different names for the same data.
12. **Misleading constant.** `ENVIRO_POWER_CONSUMED = 10` (`GenCompEnviroSystem.java:48`) is a shutdown threshold (`:82`). The actual draw is one unit per pulse (`:89`).
13. **Stale comment on toggling.** `GenLightSwitch.java:201` and `:210` say the base call "would have toggled yet AGAIN". `StdElecCompItem.java:245` sets `activate(true)` directly, so the explicit calls at `:202` and `:211` add nothing.
14. **Typo.** "an large planet-sized object" (`GenGraviticSensor.java:77`).
15. **Real name in a proxy.** `GenShipOpticalSensor.java:88-91` returns the real `Name()` beneath a "do not show!" comment. Whether any caller uses it in output was not checked.
16. **Outside this bucket, noted for the Std chapter.** `StdCompLauncher.java:125` and `StdCompPanel.java:92` read `(I instanceof Technical) && (type matches) || (panelType == ANY)`, so an ANY panel accepts any item that passes the base check. `SpaceObject.java:174-175` gives MoonRadius and AsteroidRadius the same abbreviation, "mr".

Cruft:

17. **Duplicate classes.** `GenImpulseEngine` and `GenImpulseThruster` are the same code under different names. `GenShipWeapon` and `GenShipShieldGenerator` differ only in names. `GenShipDampener`, `GenShipGravityGen` and `GenShipInertialBattery` differ only in names. `GenElecCompItem` and `GenElecCompSensor` differ only in names and ID. Each copy re-declares `getStat`, `setStat`, `getStatCodes` and `sameAs`, which is roughly 100 to 200 lines per class.
18. **Dead code.** `GenGraviticSensor.java:469-476` (`getMassDetected` and the static `star`) is never called.
19. **No-op override.** `GenLightSwitch.setPanelType` does nothing (`:235-238`), so the light switch's panel type is fixed.
20. **Exemption that never applies.** `GenComputerConsole.sameAs` skips "READABLETEXT" (`:261-262`), but READABLETEXT is not in its code list.

Multiplayer assumptions:

- Sensors keep `lastSensedObjects` per sensor (`StdElecCompSensor.java:651-662`), and the viewscreen announces arrivals to every room occupant (`GenShipViewScreen.java:65-89`).
- Messages use dummy MOBs (`GenShipViewScreen.java:73`, `:99`), and the circuit makes a dummy power MOB in a dummy room (`GroundWired.java:486-498`).

## Takeaways for a Python SUD

1. **Drop the stat-code machinery.** Model each component as a dataclass (or a YAML record) with named fields. Dispatch by position and `sameAs` as "every code equal" are unnecessary. Validate on load instead of silently defaulting bad values, which is what `GenShipPanel` and the SWARMTYPES parse do.
2. **Keep the contact model.** A sensor returns `Contact(label, mass, distance, bearing=None)`. The label comes from a size table (see Content). The sensor never sees the real object, only what its kind allows.
3. **Gravitic detection** can port as:
   ```python
   def detectable(mass, distance, max_range, own_radius, target_radius):
       adjusted = mass * (1 - distance / max_range)  # float, not integer division
       return adjusted > 100 and distance > own_radius + target_radius
   ```
4. **Optical occlusion** with floats: `visual = math.atan(radius / distance)`. Hide a target when a larger visual disc lies on the line of sight. Do not port the integer division at `GenShipOpticalSensor.java:592`.
5. **Impulse drive rule:** `max_thrust = max(0.0, (C_SUB - max(0.0, v_forward)) * mass)`. Clamp at zero, which the Java does not do.
6. **Power as a turn pass, not a circuit tick.** Once per turn, ask each generator for its output (sun, wind, speed checks), then let the player's devices draw from the pool. This keeps the "produces only when conditions allow" idea without messages or timestamps. The 10-minute light timer (`GenLightSwitch.java:45`) becomes a turn count.
7. **Lights as groups.** Give each switch a group id. Toggling one toggles its group, and hidden switches are only flags on the group. This removes the message passing and the timestamp race (`GenLightSwitch.java:115`).
8. **Environment system.** A per-turn air check every six turns, costing one power unit per turn. Name the constants for what they are.
9. **Drop the multiplayer parts.** Leave out room-wide arrival messages, dummy MOBs, the web grinder and XML misc-text persistence. A single-player "You see" list belongs to one player, so the facing-cone and look logic can become one "scan" action.
10. **Keep one free observation action.** `GenShipWindow` (always on, every direction, no power) is a good model for a free "look out the window" command in a single-player game.

## Coverage

- **Read in full:** `GenElecCompItem`, `GenElecCompSensor`, `GenBussardCollector`, `GenFuellessGenerator`, `GenSolarGenerator`, `GenCompGenerator`, `GenCompBattery`, `GenCompEnviroSystem`, `GenCompLauncher`, `GenLightSwitch`, `GenComputerConsole`, `GenShipOpticalSensor`, `GenShipEngine`, `GenShipPanel`, `GenShipShieldGenerator`, `GenShipWeapon`, `GenShipWindow`, `GenImpulseEngine`, `GenImpulseThruster`, `GenShipFuellessThruster`.
- **Read in full except one part:** `GenShipViewScreen` (the proxy boilerplate at lines 215-280 was skimmed); `GenGraviticSensor` (the converter body at lines 130-340 was skimmed); `GenShipThruster` (lines 36-200 read, the rest compared by diff with `GenShipEngine`); `GenSpaceDrive` (lines 36-164 read, the rest compared by diff with `GenShipFuellessThruster`).
- **Skimmed:** `GenShipDampener`, `GenShipGravityGen`, `GenShipInertialBattery` (compared by diff with each other and with `GenCompBattery`; stat tables read).
- **Std context read (not part of this bucket):** `StdCompFuellessGenerator` and `StdCompGenerator` (full); `StdShipDampener` (full); `StdShipGravityGen` (88-131); `StdShipInertialBattery` (60-176); `StdElecCompSensor` (105-127, 512-680, 720-811); `StdElecCompItem` (135-140, 188-246); `StdShipThruster` (344-356); `StdCompLauncher` (55-200, 328-390); `StdCompPanel` (196-215); `StdItem` (67, 197-202, 219-227, 620-680); `StdRoom` (1285-1300); `GroundWired` (483-660); `StdSpaceShip` (660-680); `Sense` (1047-1062); `CMath` (1277-1284); `SpaceObject` (constants and declarations).
- **Not traced:** the body of `StdElecCompSensor.doSensing` beyond the announcement loop; `StdCompLauncher` beyond line 390; the rendering path that would call `Name()` on an optical proxy; the climate code behind `canSeeTheSun`; the power-message behaviour of consumers in `GroundWired.fillCurrentLists`.
