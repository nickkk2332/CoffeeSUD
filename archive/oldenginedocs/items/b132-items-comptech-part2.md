# Ship and electronics components, part 2: power, engines, shields, weapons, sensors and consoles

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b132` (Items/CompTech [part 2/2: GenWarpDrive .. StdShipWeapon]) · 22 files · 6356 lines of Java.

Citations are relative to `com/planet_ink/coffee_mud/`, shortened to `Items/...` or `Libraries/...`. A bare `File.java:line` is in `Items/CompTech/`. The base classes `StdElecItem` and `StdElecContainer` (in `Items/BasicTech/`) and the generic item code are in other buckets and are cited only where these files depend on them. Everything here comes from reading the code. Nothing was run, so claims that need a run to confirm are marked "by reading".

## Summary

The second half of the CompTech family is a starship's equipment: power sources and stores, the panels that wire them together, engines and thrusters, an inertial battery, a dampener and a gravity generator, shields, a beam weapon, a missile launcher, a sensor, and the computer console that runs programs against them. All of them share one electrical model. A `POWERCURRENT` message carries an offered amount of power, every item takes what it needs as the message passes, and a support thread (`Libraries/GroundWired.java`) pools generator and battery output and hands it to panels in proportion to their demand. Ship systems are driven by typed command strings (`THRUST`, `SHIELDSET`, `FIRE` and so on) sent as activate messages, so a console or a program can operate any component without knowing its class. The bucket is worth studying for the power protocol, for its single wear-and-efficiency model, and for short formulas for thrust, shield absorption and inertial braking. Reading it also turned up a number of bugs (see Problems and cruft). The most consequential are the panel's power absorption and the thruster's wear throttle.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Items/CompTech/StdElecCompItem.java` | Base of most components: power store, installed factor, wear (`usesRemaining`), repair, enhance, circuit registration, activation gate | 346 |
| `Items/CompTech/StdElecCompContainer.java` | Same role for components that hold items; installed factor clamped to 0-2 | 321 |
| `Items/CompTech/StdCompFuelConsumer.java` | Burns raw-material fuel on power ticks; shuts down when fuel runs out | 253 |
| `Items/CompTech/StdCompGenerator.java` | Fuel-fed generator: adds stored power on each zero-value power tick | 136 |
| `Items/CompTech/StdCompFuellessGenerator.java` | Generator with no fuel; `canGenerateRightNow()` is the hook for environment-driven subclasses | 145 |
| `Items/CompTech/GenWindGenerator.java` | Data-driven wind generator; output scaled by weather and climate | 117 |
| `Items/CompTech/GenWaterPowerGenerator.java` | Data-driven generator that works only on a watery surface room | 72 |
| `Items/CompTech/StdCompBattery.java` | Charged store (1000 by default) that malfunctions as wear grows | 116 |
| `Items/CompTech/StdCompPanel.java` | Wall panel: distributes incoming power to contents by demand, and installs components | 256 |
| `Items/CompTech/StdCompLauncher.java` | Launch tube: charges, locks a target, aims, and fires its first contained item | 465 |
| `Items/CompTech/StdShipEngine.java` | Ship engine: a fuel generator that also runs the thrust accelerator | 205 |
| `Items/CompTech/StdShipThruster.java` | Fuel-burning thruster; contains `StdAccelerator`, the thrust and fuel logic shared by all engines | 580 |
| `Items/CompTech/StdShipFuellessThruster.java` | Thruster whose fuel is its own stored power | 212 |
| `Items/CompTech/GenWarpEngine.java` | Data-driven warp engine (extends `GenShipEngine`); max thrust at WARP9 | 102 |
| `Items/CompTech/GenWarpDrive.java` | Data-driven warp drive (extends `GenShipFuellessThruster`); thrust ceiling falls as forward speed rises | 102 |
| `Items/CompTech/StdShipInertialBattery.java` | Stored-power brake: absorbs ship speed on deceleration | 197 |
| `Items/CompTech/StdShipDampener.java` | Inertial dampener: cancels gravity overload above 1.0 for a power cost | 139 |
| `Items/CompTech/StdShipGravityGen.java` | Gravity generator: sets the ship's artificial-gravity flag on a power- and wear-scaled roll | 152 |
| `Items/CompTech/StdShipShieldGenerator.java` | Directional shield: absorbs a share of incoming ship damage each tick from stored power | 402 |
| `Items/CompTech/StdShipWeapon.java` | Ship beam weapon: charges, aims inside its coverage arc, fires a beam object | 387 |
| `Items/CompTech/StdElecCompSensor.java` | Ship sensor: finds space objects in range, reports detections and losses to attached programs | 811 |
| `Items/CompTech/StdComputerConsole.java` | Computer console: boots, runs installed programs as menus, blue-screens with wear | 840 |

Inheritance. `StdElecCompItem` extends `StdElecItem`, and `StdElecCompContainer` extends `StdElecContainer`. `StdCompFuelConsumer` extends `StdElecCompContainer`, `StdCompGenerator` extends `StdCompFuelConsumer`, `StdShipThruster` extends `StdCompFuelConsumer`, and `StdShipEngine` extends `StdCompGenerator`. `StdCompPanel` and `StdCompLauncher` extend `StdElecCompContainer`. `StdCompFuellessGenerator`, `StdCompBattery`, `StdShipFuellessThruster`, `StdShipInertialBattery`, `StdShipDampener`, `StdShipGravityGen`, `StdShipShieldGenerator`, `StdShipWeapon` and `StdElecCompSensor` extend `StdElecCompItem`. `StdComputerConsole` extends `StdRideable`, not the electronics base. The `Gen*` classes in this bucket are data-driven subclasses: `GenWindGenerator` and `GenWaterPowerGenerator` extend `GenFuellessGenerator` (which extends `StdCompFuellessGenerator`), `GenWarpEngine` extends `GenShipEngine` (which extends `StdShipEngine`), and `GenWarpDrive` extends `GenShipFuellessThruster` (which extends `StdShipFuellessThruster`). Their `Gen*` siblings are in part 1.

## How it works

### 1. The component base and its wear model

Data model (`Items/CompTech/StdElecCompItem.java:44-62`, with inherited fields at `Items/BasicTech/StdElecItem.java:46-47`):

- `powerCapacity` (default 100) and `power`, the stored charge. The store starts full.
- `usesRemaining`, the wear value from 0 to 100. Constructors set it to 100 (`StdElecCompItem.java:58`, `StdElecCompContainer.java:57`).
- `installedFactor`, set when the component is installed in a panel. It starts at 1.0.
- `maxRechargePer`, the most that can be restored per tick. `StdElecCompItem` defaults to a fraction of capacity (1.0, `:62`). `StdElecCompContainer` sets it to its capacity (`:61`), so the default is a flat 100 units per tick (`StdElecCompContainer.java:106`).
- `circuitKey`. When placed in a room the item registers on a circuit with `CMLib.tech()`, and unregisters when moved (`StdElecCompItem.java:156-170`). Computers on the same circuit receive status messages through `CMLib.tech().getComputers(circuitKey)`.

Efficiency is the single number that scales most outputs. `getComputedEfficiency()` multiplies the base value by `installedFactor` (`StdElecCompItem.java:96-99`). The base value is the wear fraction, `usesRemaining/100`, whenever the item is subject to wear and uses are at most 200 (`Items/BasicTech/StdElecItem.java:163-169`). Individual formulas then multiply by the manufacturer's `getEfficiencyPct()`, a separate factor covered under Problems.

Wear and tuning are handled by messages:

- `TYP_DAMAGE` (`StdElecCompItem.java:252-277`). If the damage value exceeds remaining uses, the item sparks and fizzes out (`"@x1 sparks and fizzes out."`), its uses drop to 0 and it is deactivated. Otherwise it sparks (`"@x1 sparks."`) and loses uses equal to the damage value.
- `TYP_REPAIR` (`:284-302`). A roll under the message value fully repairs the item to at least 100 uses. Otherwise it regains `(100 - uses) * value / 100`, at least 1, and the player sees `"@x1 is now @x2% repaired."`.
- `TYP_ENHANCE` (`:303-315`). Two rolls must succeed, one under the message value and one under 50%. Then `installedFactor` rises by 0.01, or by at least 0.1 when it is below 1.0. The base class has no upper bound here (`:313`). The container version clamps to 0-2 (`StdElecCompContainer.java:73-74`).
- `TYP_DROP` (`StdElecCompItem.java:239-241`) sets `installedFactor` from the message value divided by 100.

Activation gates: `TYP_ACTIVATE` is refused if the item is not installed, or if its panel is not activated, with the message `"@x1 is not installed or connected."` or `"The panel containing @x1 is not activated or connected."` (`StdElecCompItem.java:189-206`). If the message is not a control message, the base class also shows `"<S-NAME> activate(s) <T-NAME>."` (`:242-245`).

### 2. The power protocol (POWERCURRENT)

The message is `MSG_POWERCURRENT` (`Common/interfaces/CMMsg.java:1603`), and its `value()` is the power on offer. It has two phases, following the usual `okMessage` then `executeMsg` order.

- Taking (okMessage). An item that is activated, is neither a fuel consumer nor a generator, and needs power takes `min(powerNeeds(), value)` from the message. It multiplies that by its manufacturer efficiency, reduces it for wear when uses are at most 200, and adds it to its own store, capped at capacity (`StdElecCompItem.java:212-226`). `StdElecCompContainer.java:212-225` repeats the same rule.
- Value 0 as a heartbeat. Generators and fuel consumers act only on messages whose value is 0. `StdCompGenerator` adds `generatedAmtPerTick * computedEfficiency * manufacturerEfficiency` when the value is 0 and it is activated (`StdCompGenerator.java:110-124`). `StdCompFuelConsumer` counts `fuelTickDown` down on each zero-value message and burns one unit of fuel when it reaches zero (`StdCompFuelConsumer.java:233-244`). The zero-value message is therefore a clock signal carried on the power channel.
- The grid. `Libraries/GroundWired.java` runs `processElectricCurrents()` from a support thread started with `CMProps.getTickMillis()` (`GroundWired.java:510`). The default tick is 4000 ms (`core/CMProps.java:681`). Each pass does the following:
  1. Sends zero-value pulses to each generator (`GroundWired.java:552-556`).
  2. Pools the output of all active generators and batteries and zeroes their stores (`:571-585`).
  3. If the pool is empty, sends zero-value pulses to panels and batteries (`:588-613`).
  4. Otherwise offers each panel a share of the pool, proportional to its `powerNeeds()` and at least 1 unit (`:627-644`).
  5. Returns unused power to batteries and then recharges them (`:660` onward).
- The panel. `StdCompPanel.executeMsg` handles `TYP_POWERCURRENT` (`Items/CompTech/StdCompPanel.java:197-243`). It works out each contained consumer's demand, sends each consumer a share (`powerToTake = needs / totalRequest * remaining`, minimum 1, `:223-230`), subtracts what each one actually took (`:236`), stores the total demand in its `powerNeeds` field (`:239`), and returns the remainder in the original message (`:241`). Generators and batteries inside a panel are skipped (`:208-210`, `:220-221`). Thrusters and other fuel consumers are not skipped.

### 3. Generation, fuel and storage

- Fuel. `StdCompFuelConsumer` accepts only the raw-material types in `generatedFuelTypes`, which default to deuterium (`StdCompFuelConsumer.java:59`, filter at `:139-147`). Each `ticksPerFuelConsume` zero-value ticks, which default to 10 (`:44`), burns one unit (`:237-244`). If the fuel cannot cover the burn, `engineShutdown()` sends a deactivation from the deity with the message `"<T-NAME> sputters and shuts itself down."` (`:149-156`, `:200-201`).
- Fuel-fed generator. `StdCompGenerator` starts with capacity 1000 and no charge (`:53-55`). Output is `generatedAmtPerTick` per tick, default 1 (`:60`), times efficiency. The cap is enforced, with a wrinkle covered under Problems.
- Fuelless generator. `StdCompFuellessGenerator` uses the same output loop but gates it with `canGenerateRightNow()`, which defaults to true (`:86-89`, `:117-132`). Its `TYP_GET` and `TYP_INSTALL` cases do nothing, since there is no fuel cache to clear (`:99-102`).
- Wind generator. `GenWindGenerator.canGenerateRightNow()` needs an activated generator in an outdoor, dry, non-underwater room with a gas atmosphere (`GenWindGenerator.java:62-68`). It then sets `lastWindFactor` from the area's climate (table in Content worth mining), and multiplies by 1.75 if the room has the windy climate bit (`:102-103`). Output is `Math.round(generatedAmtPerTick * lastWindFactor)` (`:53-57`). This rounding happens before efficiency is applied (`StdCompFuellessGenerator.java:124-125`).
- Water generator. `GenWaterPowerGenerator` works only where `CMLib.flags().isWaterySurfaceRoom(R)` is true (`GenWaterPowerGenerator.java:59`), and sets the `IS_SWIMMING` disposition so that it floats (`:49-50`).
- Battery. `StdCompBattery` starts with capacity and charge 1000 and is always activated (`StdCompBattery.java:55-57`). `setMiscText` sets the capacity from an integer (`:69-70`). On each power tick, if it is activated and its uses are at most 100, the battery malfunctions when `Math.random() > uses/100`. The malfunction forces a deactivation in the room (`:95-110`). A battery at 50 uses therefore fails on about half of its power ticks.

### 4. Panels and launchers

Panel installation. A component placed into a `StdCompPanel` gets `installedFactor` 1.0 if the mover is an admin, and 0.0 otherwise (`StdCompPanel.java:158-160`). A component with no install value is routed to the `AstroEngineering` skill's INSTALL command (`:117-137`). Installed factor multiplies efficiency, so an uninstalled component does nothing. A panel's deactivation sends a factory-MOB deactivate message to each electronic item inside it, and then returns without clearing the panel's own `activated` flag (`:181-196`).

Launcher. `StdCompLauncher` holds `CONTAIN_SSCOMPONENTS` items (`:68`). Its recharge rate is 10 times capacity (`:70`), so `powerNeeds()` fills it toward its target in a single tick (`:101-104`).

- `TARGETSET` picks the nearest space object within 1000 units of the given point (`:267-299`). It fails with `"@x1 did not lock on."` if nothing is found.
- `AIMSET` checks the aim against the ship's battle coverage (`:301-326`).
- `FIRE` (`:328-419`) needs contents, a valid aim, non-zero power and non-zero computed efficiency. It then removes the first contained item and tick-starts it (`:360-366`). In space, its launch speed is `(power / 100.0) * speed * computedEfficiency` (`:384-385`), where the base speed is the item's own speed or `SpaceObject.VELOCITY_SOUND` (34), and it is placed a little ahead of the ship (`:375-382`). When docked, the item "flies out of" the ship into the dock room (`:407-408`). Power is then reset to 0 (`:416`).

### 5. Engines and thrust

There are three engine families, and all use one accelerator.

- `StdShipEngine` extends `StdCompGenerator`. It produces power as a fuel generator and also runs its thrust on power ticks (`StdShipEngine.java:150-155`). Defaults are max thrust 8,900,000, specific impulse 0.33, minimum thrust 0, and constant (reaction) thrust (`:47-51`).
- `StdShipThruster` extends `StdCompFuelConsumer`. Its fuel is raw-material contents, and its capacity is its weight plus 100,000 (`StdShipThruster.java:66`).
- `StdShipFuellessThruster` extends `StdElecCompItem`. Its fuel is its own stored power. `consumeFuel` succeeds only if the store exceeds the amount, and then subtracts it (`StdShipFuellessThruster.java:174-183`).

`StdAccelerator` (`StdShipThruster.java:216-579`) does the work:

- Thrust for an injection `x` between 0 and 1 is `installed * (min + (max - min) * x)`, clamped to max and to `installed * min` (`:343-356`).
- Fuel per pulse. `linearBase = (thrust/1e6) * (mass/1e6) / (isp * efficiency)`, `theta = min(pi/2, linearBase / 1e12 * pi/2)` and `fuel = round(1000 * sin(theta))`, with a minimum of 1 for any thrust (`:315-332`). The 1e12 denominator is the "fuel thrust cap" (`:302-306`). At the default maximum thrust (8.9e6) and a 1e6 ship mass, `fuel` rounds to 0, so each pulse costs the minimum of 1 unit. Computed from the formula, `fuel` rounds to 1 only once mass exceeds about 1.2e13 (unverified by run).
- Wear throttle. When uses are below 75, `pct = uses * 100 + 0.35 * reliability` and the thrust is multiplied by `pct` only if `pct < 1` (`:390-398`). See Problems item 1.
- Aft (continuous) burns save their thrust in `engine.setThrust` (`:399-407`). Each later power tick runs `executeOngoingThrustCommand`, which burns fuel for the saved thrust and sends a `THRUST` control message back through the engine's room (`:498-530`). Other directions are one-shot. The ship receives an `ACCELERATION` control message with the direction reversed (`portDir.opposite()`, `:439`).
- Failure messages include `"@x1 @x2 and fires, but nothing happens."` (`:379`), `"Failure: @x1: exhaust ports."` (`:380`), `"@x1 @x2 loudly, then sputters down."` (`:452`) and `"Failure: @x1: insufficient fuel."` (`:453`).
- Stopping. `executeDeactivateCommand` sends `ACCELERATION(AFT, 0, reaction)` to the ship and zeroes thrust (`:558-578`).

`GenWarpEngine` and `GenWarpDrive` are data-driven versions that fix the thrust range at `VELOCITY_WARP9` (81 times `VELOCITY_LIGHT`, 2,428,318,845) as the maximum and `VELOCITY_WARP1` (29,979,245) as the minimum (`GenWarpDrive.java:50-55`, `GenWarpEngine.java:50-55`; constants at `core/interfaces/SpaceObject.java:291-317`). When the item sits in a `SpaceShip`, `getMaxThrust()` returns `(WARP9 - max(0, speed * cos(angle between direction and facing))) * shipMass` (`GenWarpDrive.java:73-87`). Forward speed therefore consumes the thrust ceiling. Both are non-reaction engines (`:50`).

### 6. Inertia and gravity

Inertial battery (`StdShipInertialBattery.java:42-58`). Its capacity is `VELOCITY_LIGHT / 4`, which is 7,494,811 (`:57`), and it recharges 1% of capacity per tick (`:56`). It registers as an acceleration listener on its ship (`:68-79`). When an aft or forward acceleration command arrives while the ship is moving and the facing is more than 90 degrees from the direction of travel (`:129-131`), the battery computes:

- `speedAbsorbAbility = power * (computedEfficiency * manufacturerEfficiency)` (`:134-136`).
- `powerCost = removedSpeed` (`:144`).
- If the cost is affordable, the store is set to `absorb - cost` (`:147-148`). Otherwise the store is set to 0 (`:150-153`).

The ship's speed is then reduced by `removedSpeed` (`:154`) and the command's remaining acceleration is rewritten (`:168-170`). Reading the code closely shows that `removedSpeed` is always the full ship speed, so braking is not gated by power (Problems item 7).

Dampener (`StdShipDampener.java:86-116`). It intercepts any gravity message (`othersMinor == TYP_GRAVITY`) with a value above 1.0 while activated. It rewrites the value to `round(value - (value - 1) * wear * powerFactor)` (`:101-113`), where the power cost is `rooms * efficiency` (`:102`) and `powerFactor` falls when the store is short (`:104-109`). With full wear and power, the overload is cancelled completely.

Gravity generator (`StdShipGravityGen.java:96-125`). While activated, and while the ship has neither the artificial-gravity flag nor the in-air flag, it draws `rooms * efficiency` from its store each power tick (`:109-119`). It then sets the artificial-gravity flag if a roll under `150 * wear * powerFactor` succeeds (`:120-121`). A full-power, unworn generator therefore always succeeds.

### 7. Shields

`StdShipShieldGenerator` (`:41-402`) combines a power model with a coverage model.

- Power. Each tick it can take up to 10% of capacity (`:52`, `:80-84`). `POWERSET` sets a target, clamped to capacity (`:322-330`).
- Use it or lose it. On each power tick it turns its stored charge into `lastPowerConsumption` and clears the store (`:372-384`). Its strength for the following interval is `lastPowerConsumption / powerCapacity` (`:234`). The shield has to be refed every tick.
- Coverage. Directions come from `ShipDirectional.ShipDir`, six values in the order AFT, PORT, VENTRAL, DORSEL, STARBOARD, FORWARD (`Items/interfaces/ShipDirectional.java:50-58`). `SHIELDSET(dir, count)` builds a contiguous arc around `dir` by alternating outward (`StdShipShieldGenerator.java:332-365`). The default covers all directions (`:64-66`, `:153-154`).
- Absorption (`:178-260`). Only ship-targeted `TYP_DAMAGE` whose source type is in the shield's damage list is considered (`CMParms.contains(getDamageMsgTypes(), msg.sourceMinor())`, `:185`; the default list is `AVAIL_DAMAGE_TYPES`, `Items/interfaces/ShipWarComponent.java:49`). The attack must come from a covered direction, computed from the attacker's known source (`:200-208`). A shield with higher tech than the attacker multiplies the damage by `1 - diff/10`, and at a difference of 10 or more the damage is multiplied to zero and the message value cut to 5% (`:214-230`).
- Damage split. The residual is `newVal = value - value * pctShields * efficiency * wear` (`:240`). The shield itself takes `round(50 * multiplier * (1 - pctShields) * (1 - reliability))` as a copied damage message (`:241-248`). The room sees `"<T-YOUPOSS> shields are struck by <O-NAME>"` (`:251-252`).
- Shield wear. Damage to the shield reduces its uses, and damage beyond the remaining uses sets them to 0, deactivates it, and sends a `COMPONENTFAILURE` "shield_failure" command to the computers (`:284-299`).

### 8. Weapons

`StdShipWeapon` (`:43-387`) is the beam weapon. It defaults to a single FORWARD arc and an ELECTRIC damage type (`:64-66`). It recharges 20% of capacity times computed efficiency per tick (`:48`, `:83`).

- Bleed. `getBleedAmount()` returns `capacity * (0.1 - 0.1 * computedEfficiency)` (`:186-190`). Each power tick reduces power by `min(bleed, 1)` (`:365-366`). The FIRE check instead requires power of at least `min(capacity, target) - bleed` (`:264-265`).
- FIRE (`:258-356`). The weapon builds a `StdSpaceTechWeapon` beam (`:277`). Its damage type is picked at random from the configured list (`:278-280`). Its name follows the damage type (table in Content worth mining). It is placed at `ship.radius + 10 + ship.speed + 4` along the aim (`:338-342`), moves at `VELOCITY_LIGHT` (`:347`), and has damage equal to its own damage times computed efficiency (`:351-352`). It is tick-started for 10 ticks (`:353`), and power resets to 0 (`:355`).

### 9. Sensing

`StdElecCompSensor` (811 lines, a little under half of which is an anonymous read-only proxy, `directConverter`, at `:132-508`) detects space objects.

- Range. Maximum range is `SpaceObject.Distance.Parsec.dm * computedEfficiency` (`:514-516`, `:582`). Objects are found with `CMLib.space().getSpaceObjectsWithin` (`:583`).
- Power gate. A sweep runs only when `powerRemaining() > powerNeeds()` (`:580`). With the default recharge rate of 1.0, `powerNeeds()` is `capacity - power`, so the sensor works only above half charge. Each explicit `SENSE` costs one unit (`:750-754`).
- Reliability. Every 360 seconds of wall-clock time (`:593`), a sweep with more than one contact rolls `> 100 * reliability`. On failure the farthest tenth of contacts (minimum 1) is hidden until the next window (`:587-606`). Between windows the hidden contacts are removed (`:605-606`).
- Reporting. `doSensing` diffs the contact set against the previous sweep. It sends `SENSE(this, TRUE)` or `SENSE(this, FALSE)` control messages to the attached program, and writes `"@x1: <T-NAME> has been detected."` or `"... is no longer detected."` to the program's screen (`:647-708`).
- Passive relay. Any message with an `othersMessage` that does not come from the sensor's own area is rendered with the coffee filter and shown as `"@x1 detects: ..."` on the attached programs (`:793-801`; source test at `:112-123`).

### 10. The computer console

`StdComputerConsole` extends `StdRideable` with one rider slot (`:66-68`). It is the only class here that is not an electronics item in the base hierarchy, but it implements `ElecPanel` and `Computer`, so the grid treats it as a panel.

- Capacity. `powerCapacity()` is `1 + programs + (1 if a menu is open)` (`:92-98`). On each power tick each installed program is sent a `POWERCURRENT` of 1, and the program in the open menu is sent 2 (`:712-716`). The console subtracts what each returns (`:719`, `:723`). Base programs leave the value unchanged (`Items/Software/StdProgram.java:272-275`, `:580-587`), so each program costs 1 unit per tick, and the active one costs 2.
- Boot. Activation needs power or a battery in the circuit (`:431-434` via `CMLib.tech().seekBatteryPower`, implemented at `Libraries/GroundWired.java:848`). A successful boot shows `"<S-NAME> boot(s) up <T-NAME>."` (`:580-582`).
- Typed commands (`TYP_WRITE`, `:466-560`). A line is matched against the open menu's programs, or `"<"` returns to the parent menu (`:482-500`). If nothing matches, the message is `"Unknown command. Please read the screen for a menu."` (`:509-511`).
- Blue screen, per command. If the console is subject to wear and `Math.random() >= uses/100`, it shows `"<T-NAME> blue screens!!"` to its readers and shuts down (`:515`, `:542-551`). At 100 uses it never fails. At 50 uses half of all commands fail.
- Activation failure. A program activation fails with probability `1 - installedFactor` or `1 - damageFailChance`, where `damageFailChance = uses/100 + 0.35 * reliability` when uses are below 75 (`:615-622`).
- Watchdog. Its auto-shutdown uses wall-clock time, not ticks. Its timer is refreshed while it has power, and after 12 seconds (`:701-704`) without power it shuts down (`:726-729`).
- Screens. `readableText()` renders the open menu, command list and help hint (`:308-368`). Some strings: `"The screen is blank.  Try activating/booting it first."` (`:314`), `"The system is booting up."` (`:361`), `"This system is ready to receive software."` (`:363`), `"Enter \"<\" to return to the previous menu."` (`:350`).
- Program list. `getSoftware()` re-scans the contents of the console at most every 10 seconds of wall-clock time (`:265-287`).

### 11. Interactions with other systems

- `CMLib.tech()` (Libraries) owns circuits and their registration, computer lookup and battery seeking (`StdElecCompItem.java:162-168`, `StdShipGravityGen.java:133`, `StdComputerConsole.java:433`).
- `CMLib.space()` supplies distances, directions, coverage arcs and object lookup. `SpaceShip` (an `Area`) supplies flags (`StdShipGravityGen.java:101-102`), the boardable item and the space object (`StdShipThruster.java:433`).
- Rooms deliver every message to every item in the room (`Locales/StdRoom.java:868-871`). This is how panels, batteries and shields see messages, and it is the reason for Problems item 6.
- `getFinalManufacturer()` supplies efficiency and reliability for every formula (`Items/BasicTech/StdElecItem.java:152-160`; documentation at `Common/interfaces/Manufacturer.java:94-114`).
- Programs (`Items/Software/`) receive power messages and screen messages (`StdProgram.java:272-275`, `:580-587`).
- Skills. `AstroEngineering` performs installation (`StdCompPanel.java:125-133`).

## Content worth mining

### Component catalogue

Default names and numbers come from each class's constructor, or from the nearest superclass that sets them. Classes that do not set a name show the base default.

| Class | Default name | Tech type | Power (capacity, per-tick rate) | Distinctive mechanic |
|---|---|---|---|---|
| `StdCompBattery` | a battery | SHIP_POWER | 1000 stored, full at start | Malfunction chance rises as uses fall (`:95-98`) |
| `StdCompGenerator` | a generator | SHIP_GENERATOR | 1000 cap, +1 per zero tick | Burns deuterium, 1 unit per 10 ticks |
| `StdCompFuellessGenerator` | a generator | SHIP_GENERATOR | 1000 cap, +1 per zero tick | `canGenerateRightNow()` gate |
| `GenWindGenerator` | a wind power generator | SHIP_GENERATOR | 1000 cap, weather-scaled | Weather and climate table |
| `GenWaterPowerGenerator` | a water power generator | SHIP_GENERATOR | 1000 cap | Works only on a watery surface, floats |
| `StdCompPanel` | an engineering panel | SHIP_PANEL | Distributes, has no store of its own | Demand-proportional split |
| `StdCompLauncher` | a launcher tube | SHIP_LAUNCHER | Recharge 10x capacity | Locks targets, launch speed from charge |
| `StdComputerConsole` | a computer console | SHIP_COMPUTER | 1 + programs + menu | Menus, typed commands, blue screen |
| `StdElecCompSensor` | inherits "an electric component" | SHIP_SENSOR | Inherits 100 and 1.0 | Range of one parsec times efficiency |
| `StdShipShieldGenerator` | a ship shield generator | SHIP_SHIELD | 10% per tick | Arc coverage, tech-level reduction |
| `StdShipWeapon` | inherits "an electric component" | SHIP_WEAPON | 20% per tick, damage 100 | Beam name by damage type |
| `StdShipEngine` | a ships engine (sic) | SHIP_ENGINE | Fuel generator | Thrust 8.9e6, isp 0.33 |
| `StdShipThruster` | a thruster engine | SHIP_ENGINE | Fuel consumer, capacity 105,000 | Continuous aft burn |
| `StdShipFuellessThruster` | a space drive | SHIP_ENGINE | Stored power is the fuel | Charge is spent per pulse |
| `GenWarpEngine` | a generic warp engine | SHIP_ENGINE | Max thrust WARP9, min WARP1 | Non-reaction engine |
| `GenWarpDrive` | a generic impulse thruster | SHIP_ENGINE | Max thrust WARP9, min WARP1 | Thrust ceiling falls with forward speed |
| `StdShipInertialBattery` | an inertial battery | SHIP_INERTIAL | 7,494,811 cap, 1% per tick | Absorbs braking speed |
| `StdShipDampener` | an inertial dampener | SHIP_DAMPENER | Inherits 100 and 1.0 | Rewrites gravity messages |
| `StdShipGravityGen` | a gravity generator | SHIP_ENVIRO_CONTROL | Inherits 100 and 1.0 | Sets artificial gravity on a roll |

Other constants worth keeping. Stored power and burn units are abstract, so a port needs its own scale. Per-tick timing comes from the tick thread, which defaults to 4000 ms (`core/CMProps.java:681`).

### Beam names by damage type

From `StdShipWeapon.java:287-337`, summarised into a table. The beam names are the in-game text.

| Damage type | Beam name | Material |
|---|---|---|
| Collision | a metal slug | steel |
| Electric (default) | an energy beam | energy |
| Acid | a disruptor beam | chlorine |
| Cold | a "distintegration" beam (typo in source, `:302`) | fresh water |
| Fire | a photonic beam | energy |
| Gas | a particle beam | air |
| Laser | a laser beam | energy |
| Paralyze | a fusion beam | energy |
| Poison | a magnetic beam | energy |
| Sonic | a tight radio beam | energy |
| Undead | an anti-matter beam | antimatter |
| Water | a graviton beam | energy |

### Weather factors for wind power

From `GenWindGenerator.java:73-103`. The factor is multiplied by a base of 1 per tick, then rounded with `Math.round`, which rounds halves up. A 0.5 factor therefore becomes 1 at the default output of 1.

| Weather | Factor |
|---|---|
| Windy | 2.0 |
| Thunderstorm, blizzard, dust storm | 1.5 |
| Winter cold | 0.75 |
| Rain, snow, hail, sleet | 1.0 |
| Clear, cloudy, heat wave, drought, fog | 0.5 |
| Room has the windy climate bit (extra, multiplied) | 1.75 |

### Short messages worth reusing

These are short and non-explicit, so they are quoted as they appear.

- `"<T-NAME> sputters and shuts itself down."` (`StdCompFuelConsumer.java:152`)
- `"@x1 sparks and fizzes out."` (`StdElecCompItem.java:258`)
- `"<S-NAME> power(s) up <T-NAME>."` (`StdCompGenerator.java:97`)
- `"<S-NAME> connect(s) <T-NAME>."` (`StdCompPanel.java:178`)
- `"@x1 did not lock on."` (`StdCompLauncher.java:295`)
- `"@x1 flies out of @x2."` (`StdCompLauncher.java:407`)
- `"<T-NAME> blue screens!!"` (`StdComputerConsole.java:549`)
- `"The screen on <T-NAME> goes blank."` (`StdComputerConsole.java:828`)
- `"<T-YOUPOSS> shields are struck by <O-NAME>"` (`StdShipShieldGenerator.java:252`)
- `"@x1: <T-NAME> has been detected."` (`StdElecCompSensor.java:632`)

## Notable design ideas

1. One channel for power and clock. `POWERCURRENT` carries a demand, a supply and a heartbeat. A value of 0 tells generators to produce and fuel consumers to burn, so nothing needs a separate scheduler (`StdCompGenerator.java:110-111`, `StdCompFuelConsumer.java:233-244`).
2. Absorb as the message passes. A consumer takes what it needs from the passing demand and decrements it (`StdElecCompItem.java:212-226`). The panel then gets the remainder, which is a simple way to share power without a global allocator (`StdCompPanel.java:197-243`). See Problems item 6 for the risk.
3. Shields spend their charge each tick. Their strength is the previous tick's charge, and the store is then emptied (`StdShipShieldGenerator.java:372-384`). This makes "keep it fed" a real trade-off.
4. Interception by message type. The dampener does not model gravity. It rewrites the gravity message in flight, which keeps the physics and the device separate (`StdShipDampener.java:90-113`).
5. Speed-dependent ceiling. The warp drive's maximum thrust shrinks as forward speed approaches the warp ceiling (`GenWarpDrive.java:73-87`). The cap is part of the thrust model, not an external rule.
6. Detection by set difference. The sensor reports "detected" and "lost" by diffing its contact set on each sweep (`StdElecCompSensor.java:647-708`). A failure window hides the most distant tenth of contacts for a time (`:587-606`).
7. Wear as one number. Uses drive efficiency, failure chance, repair and the damage-to-wear rule (`StdElecCompItem.java:252-302`, `Items/BasicTech/StdElecItem.java:163-169`). One integer is easy to save and show.
8. The console as a menu machine. Programs have activation and command strings, and the console tracks the open menu. Typed input becomes program actions, and an unknown command returns the screen (`StdComputerConsole.java:466-512`). This is a good model for an in-world terminal.

## Problems and cruft

Items are ordered roughly by consequence. Each is "by reading" unless noted.

1. Thruster wear throttle multiplies where it should divide. `StdShipThruster.java:394` computes `CMath.mul(engine.usesRemaining(), 100.0)`. Because `pct` is then at least 100 for any uses above zero, the throttle in `:390-398` applies only at zero uses. Compare `StdComputerConsole.java:618`, which divides by 100 for the same idea.
2. Panels absorb their contents' demand before distributing. `StdCompPanel` inherits the consumer branch of `StdElecCompContainer.okMessage` (`StdElecCompContainer.java:212-225`), because a panel is neither a fuel consumer nor a generator. That branch takes `min(powerNeeds(), value)` from the message and stores it in the panel's own store. `StdCompPanel` overrides `powerNeeds()` to return the total demand of its contents (`StdCompPanel.java:80-84`, set at `:239`). `StdRoom.okMessage` calls `okMessage` on every item in the room (`Locales/StdRoom.java:868-871`), so the absorption happens before `executeMsg` distributes. As read, contents receive only the surplus above their total demand. The panel's own store is written but never read, since `GroundWired` reads store values only for generators and batteries (`GroundWired.java:571-584`, `:672-689`, `:848-870`). A one-panel, one-battery, one-consumer run would confirm or refute this.
3. Efficiency has two sign conventions. `Common/interfaces/Manufacturer.java:94-95` documents 0.5 as "super efficient" and 2.0 as "very inefficient", so efficiency is a cost. Some sites use it as a cost: `StdShipGravityGen.java:110` and `StdShipDampener.java:102` draw `rooms * efficiency`. Others treat it as a multiplier of output or storage. Generators do this (`StdCompGenerator.java:117`, `StdCompFuellessGenerator.java:125`), so do charging (`StdElecCompItem.java:221`, `StdElecCompContainer.java:219`, `StdComputerConsole.java:407`), shield absorption (`StdShipShieldGenerator.java:240`) and inertial absorption (`StdShipInertialBattery.java:134-136`). Fuel is also inverted: `StdShipThruster.java:327` divides by efficiency, so a 2.0 part burns less fuel.
4. The inertial battery's braking is not gated by power. `StdShipInertialBattery.java:137` sets `removedSpeed` to the full ship speed. The only code that reduces it (`:141-143`) can never run (item 8). So `powerCost` equals the speed, and `efficiency *= powerCost / speed` at `:151` is always a factor of 1. Every braking command therefore stops the ship, whatever the store holds. The shortfall branch changes only the stored power. `massFactor` (`:138-140`) is computed and never used.
5. Heading wobble depends on manufacturer efficiency only. `StdShipInertialBattery.java:157-165` perturbs the heading when `efficiency < 1.0`, by `(1 - efficiency)` times the angle in each plane, with a random sign. Under the documented meaning of efficiency (item 3), the best manufacturers are the only ones that wobble.
6. Activation happens before the accelerator validates. `StdElecCompItem.java:242-245`, `StdElecCompContainer.java:237-241`, `StdCompFuelConsumer.java:221-223` and `StdCompGenerator.java:94-98` all activate the object before the subclass runs its accelerator check. So the check at `StdShipThruster.java:186-188`, `StdShipEngine.java:140-142` and `StdShipFuellessThruster.java:193-195` cannot prevent activation. Non-control activations also show two room messages for generators, for example `StdElecCompItem.java:244` plus `StdCompFuellessGenerator.java:105`, and `StdElecCompContainer.java:239` plus `StdCompGenerator.java:97`.
7. The "insufficient fuel" failure fires on zero thrust. `StdShipThruster.java:534-549` runs when thrust is zero or there is no aft port, not when fuel runs out. A non-generator thruster activated without aft thrust (after a sideways burst, for example) reports `"Failure: ... insufficient_fuel."` to its computers and deactivates on its next power tick. Fuel exhaustion during an aft burn is handled by `engineShutdown()` (`StdCompFuelConsumer.java:200-201`, `:149-156`), which shows only the room message.
8. A dead branch. `StdShipInertialBattery.java:131` returns when `delta < pi/2`, so at `:141` the value `retainingDelta = 1 - delta/(pi/2)` is never above 0. The `> 0.0` test at `:142` is therefore never true, and `removedSpeed` is never reduced. `getAngleDelta` returns an arc-cosine (`Libraries/CoffeeDark.java:323-350`), so `delta` lies in [0, pi].
9. Gravity generator's `sameAs` tests the wrong class. `StdShipGravityGen.java:148` checks `instanceof StdShipDampener`, so two gravity generators are never `sameAs`.
10. Unchecked ship references. `StdShipGravityGen.java:100-101` and `StdShipDampener.java:94-102` use `getMyShip()` without a null check. The cache is null when the item is not on a `SpaceShip` (`StdShipGravityGen.java:77-81`). This is reached only if the item receives power while activated off-ship, which is possible if a panel is not on a ship. `StdShipInertialBattery.java:107` checks for null before use. The shield compares its cached ship in its guard (`StdShipShieldGenerator.java:182-185`) before it dereferences it.
11. Weapon bleed and its clamp disagree. `StdShipWeapon.java:366` uses `Math.min(getBleedAmount(), 1)`, while the FIRE charge check uses the full bleed (`:264`). The clamp limits discharge to 1 unit per tick. Separately, when computed efficiency exceeds 1 (possible after ENHANCE, which has no upper bound at `StdElecCompItem.java:313`), the bleed is negative and power grows by that amount each tick, with no cap in that path.
12. Random arc coverage can read index -1. `StdShipShieldGenerator.java:157-168` builds the arc from a random centre, and the loop reads `permittedDirs.get(centralIndex - offset)`. If the centre is 0 and fewer directions are covered than there are ports, the index is -1, and `XVector` extends `Vector` (`core/collections/XVector.java:25`), so the call throws. The `SHIELDSET` path avoids this by adding the array length (`:349-350`). The default covers all directions (`:65`, `:153-154`), so the bug is latent.
13. Generators stall below capacity. `StdCompGenerator.java:113-114` and `StdCompFuellessGenerator.java:120-121` add power only when there is room for a whole tick, or when the store holds less than one tick. With output above 1 per tick, the store stops up to output minus 1 below capacity.
14. Possible null in synchronised blocks (unverified). `getSoftware()` can return null (`StdComputerConsole.java:286`) when the host is not running. Several `synchronized(software)` blocks then throw: `:474-476`, `:662-664`, `:708-710`, `:747-748` and `:809-813`.
15. Latent null in sensor. `doSensing` dereferences `controlI` at `StdElecCompSensor.java:665` and `:695`. Its caller passes null when the message tool is not a `Software` (`:725`).
16. Dead flags and code. `readFlag` is always false on the activate and deactivate paths (`StdComputerConsole.java:602`, `:655`, `:675`, `:686`). Panel and launcher deactivation return before clearing their own `activated` flag (`StdCompPanel.java:181-196`, `StdCompLauncher.java:425-440`).
17. Duplication. `sendComputerMessage` is copied into six classes (`StdShipGravityGen.java:131-143`, `StdShipDampener.java:118-130`, `StdShipInertialBattery.java:176-188`, `StdShipShieldGenerator.java:262-274`, `StdShipWeapon.java:157-169`, `StdCompLauncher.java:154-167`) and into `StdAccelerator` (`StdShipThruster.java:285-297`). `GenWarpDrive.java:55-101` and `GenWarpEngine.java:55-101` are the same code.
18. Copy-paste text. `GenWarpDrive.java:46` calls the drive "a generic impulse thruster", and `GenWarpEngine.java:48` says "a generic impulse warp sits here." `StdShipEngine.java:60` reads "a ships engine".

## Takeaways for a Python SUD

1. Use a per-turn power pipeline, not a message bus. CoffeeMud passes one message through every item in a room, and that makes the panel bug in item 2 possible. For a single-player game, make the order explicit:

   ```python
   def power_tick(grid):
       pool = sum(g.drain() for g in grid.generators if g.active)
       pool += sum(b.drain() for b in grid.batteries if b.active)
       demand = sum(p.demand() for p in grid.panels)
       for p in grid.panels:
           if demand:
               offer = pool * p.demand() / demand
               pool -= p.draw(offer)   # the consumer takes what it can use
       grid.recharge(pool)
   ```

   Generators and batteries give up their stored power, panels share it by demand, and whatever is not drawn goes back to batteries. Keeping the store on the component, not the panel, makes the flow easy to follow.
2. Choose one efficiency convention and apply it in one place. Define it as a cost multiplier (lower is better, as CoffeeMud documents) and write each formula so that a higher value costs more.
3. Wear as a single 0-100 value. It scales output (`condition / 100`), gates failures (a battery with 50 condition fails half its ticks) and is restored by a skill roll. Keep the messages ("sparks", "fizzes out") as text events.
4. Fuel per turn. Replace the 1e12 cap with a per-turn burn such as `fuel = thrust * mass / (isp * efficiency)`, using units you document. Discrete turns make a per-turn burn natural in text.
5. Shields as charge spent per turn. Spend power each turn to set the next turn's strength, and clear the store. This is cheap to implement and easy to explain.
6. Gravity and braking as hooks. Model the dampener as a rule that rewrites the gravity value during the turn's resolution, and the inertial battery as a rule on the acceleration command. Gate braking on power, which CoffeeMud does not do (item 4).
7. Sensors with turns, not clocks. Keep the set-difference reporting ("detected", "lost") and the reliability roll, but measure the 360-second window in turns, and make each sweep cost explicit power.
8. Consoles as terminals. A menu stack of programs with command strings, a screen rendered from the open menu, and a wear-based blue screen is a good in-world interface. Single-player means one reader, so the broadcast to readers in `StdComputerConsole.java:742-778` reduces to printing to the one screen.
9. Weapons as data. A table `{damage_type: (beam_name, material)}` and a random choice per shot, as in `StdShipWeapon.java:278-337`.
10. Drop the transport. The `CMMsg` control-message strings (`TechCommand`, `Items/interfaces/Technical.java:184-203`) suit a multi-part server. In Python use typed command objects. Drop the factory MOB and deity senders.
11. Multiplayer assumptions to rethink. `getCurrentReaders()` and `forceReadersSeeNew()` assume several readers (`StdComputerConsole.java:371-392`, `:742-778`). Wall-clock timers (12 s for the console at `:701-729`, 10 s for the program rescan at `:282`, 360 s for the sensor at `:593`) need turn-based equivalents.

## Coverage

- **Read in full (every line after the package, imports and licence header):** all 22 files: `StdElecCompItem.java`, `StdElecCompContainer.java`, `StdCompFuelConsumer.java`, `StdCompGenerator.java`, `StdCompFuellessGenerator.java`, `GenWindGenerator.java`, `GenWaterPowerGenerator.java`, `StdCompBattery.java`, `StdCompPanel.java`, `StdCompLauncher.java`, `StdShipEngine.java`, `StdShipThruster.java` (including `StdAccelerator`), `StdShipFuellessThruster.java`, `GenWarpEngine.java`, `GenWarpDrive.java`, `StdShipInertialBattery.java`, `StdShipDampener.java`, `StdShipGravityGen.java`, `StdShipShieldGenerator.java`, `StdShipWeapon.java`, `StdElecCompSensor.java` and `StdComputerConsole.java`.
- **Read but skimmed:** the anonymous read-only proxy `directConverter` in `StdElecCompSensor.java:132-508`. Its delegating methods are boilerplate.
- **Read in part, outside this bucket:** `Items/BasicTech/StdElecItem.java` (40-171), `Locales/StdRoom.java` (745-900), `Libraries/GroundWired.java` (500-700 and 848-870), `Items/Software/StdProgram.java` (245-280, 466-480, 580-590), `Items/Basic/StdItem.java` (67, 259, 1555-1575), `Items/interfaces/Technical.java` (51-81, 184-215), `Common/interfaces/Manufacturer.java` (94-114), `core/interfaces/SpaceObject.java` (172, 291-329), `Libraries/CoffeeDark.java` (323-352), `Libraries/Dice.java` (398-408), `core/CMProps.java` (681), `core/collections/XVector.java` (25), and `Items/interfaces/ShipDirectional.java` and `ShipWarComponent.java` (constants only).
- **Grep only:** `Items/BasicTech/StdElecContainer.java` (no POWERCURRENT or okMessage override), `Items/CompTech/GenFuellessGenerator.java`, `GenShipFuellessThruster.java` and `GenShipEngine.java` (superclass lines only), and the `POWERCURRENT` senders across the tree.
- **Skipped:** the `Gen*` classes of part 1; the implementations of `CMLib.space()`, `CMLib.tech()` registration and `SpaceShip`; `Climate`; the `Software` programs beyond their power and screen hooks; `Weapon.MSG_TYPE_MAP`; and `GroundWired` beyond the power loop and `seekBatteryPower`.
- **Not done:** no runtime test. Every behaviour and every Problems item is from reading. Items 2, 6, 7, 11 and 14 are the first candidates for a run.
