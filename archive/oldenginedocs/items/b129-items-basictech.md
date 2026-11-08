# Technology items: energy weapons, shields, panels, tricorders and ships

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b129` (Items/BasicTech) · 42 files · 8196 lines of Java.

Citations are relative to the CoffeeMUD folder with the `com/planet_ink/coffee_mud/` prefix dropped, so `Items/BasicTech/StdGun.java:53` is `com/planet_ink/coffee_mud/Items/BasicTech/StdGun.java` line 53. Base classes (StdItem, StdContainer, StdWeapon) are covered in b172 and are not repeated here. Numbers marked "derived" are arithmetic on the cited constants, not values observed at runtime.

## Summary

This bucket is CoffeeMud's technology layer: energy weapons, slug guns, worn shields and stealth fields, wall panels that route power and hold ship parts, a handheld tricorder that runs software menus, and the space side (space objects, torpedoes and the ship itself). Two resource models run through it. Personal kit spends a fixed power store on events (each hit fired, each hit blocked), while worn fields and the tricorder drain one unit every few ticks whether or not anyone uses them. An energy weapon's mode sets both its damage type and its side effects, and shields filter by damage type, so a few small rules give a rock-paper-scissors matchup. Several parts are sound and worth reusing; a few are broken or inverted (see Problems), so the formulas below describe what the code does, not what it is meant to do.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Items/BasicTech/StdTechItem.java` | Base for guns and space objects: manufacturer name (default `RANDOM`), `TechType.GIZMO`, tech level from ability | 105 |
| `Items/BasicTech/StdElecItem.java` | Electronics base: power capacity and remaining (both 100 by default), activation flag, `powerNeeds()` | 171 |
| `Items/BasicTech/StdElecContainer.java` | Powered container; never a power consumer; efficiency hook | 169 |
| `Items/BasicTech/StdElecPanel.java` | Ship wall panel: installs ship parts, distributes power to contents, cascades activation | 258 |
| `Items/BasicTech/StdTriCorder.java` | Handheld computer: holds software only, menu state machine, self-draining power | 528 |
| `Items/BasicTech/StdGun.java` | Ammunition weapon: rounds stored in `usesRemaining`, reload and unload, ammo carries effects | 472 |
| `Items/BasicTech/StdElecWeapon.java` | Energy weapon: modes, power per hit, damage scaling, stun, maim, disrupt, disintegrate | 543 |
| `Items/BasicTech/StdPersonalShield.java` | Worn shield base: decides per hit whether to block or scale damage, pays power per event | 292 |
| `Items/BasicTech/GenTickerShield.java` | Worn field that drains one unit per electronics tick; base of the stealth fields | 378 |
| `Items/BasicTech/StdSpaceTech.java` | Space object base: coordinates, radius, direction, speed, mass | 168 |
| `Items/BasicTech/StdSpaceTechWeapon.java` | Space weapon base: removes itself after a hit or a ballistic tick | 155 |
| `Items/BasicTech/StdTorpedo.java` | Torpedo: damage 100, weight 1000, five-minute chase clock | 123 |
| `Items/BasicTech/StdSmartTorpedo.java` | Homing torpedo: intercept calculation and periodic rescans | 107 |
| `Items/BasicTech/GenSpaceShip.java` | Ship: boardable area, hull, thrust commands, collisions, docking, sensors | 1149 |
| `Items/BasicTech/Gen*.java` (30 files) | Data-driven templates with `MYCODES` arrays and `getStat`/`setStat` switches; most are name, mode or weight variants | 53 to 228 each |

## How it works

### 1. Shared electronics model

Every electronic item carries `powerCapacity` and `power` (both default 100), an `activated` flag and `powerNeeds()`, which is capacity minus remaining power (`Items/BasicTech/StdElecItem.java:46-48`, `:113-116`). A container reports zero need (`Items/BasicTech/StdElecContainer.java:112-115`). The helper `getComputedEfficiency()` multiplies by `usesRemaining/100` when wear applies and uses are 200 or fewer (`StdElecItem.java:163-169`). Nothing in this bucket calls it; the CompTech subclasses do.

Power moves by message, not by a global pool. A panel receives `TYP_POWERCURRENT` with the power on offer, splits it across its electronic contents in proportion to their need (an item with zero or negative need counts as weight 1), gives each at least one unit while power lasts, and returns the leftover in the message value. It also stores the total need as its own `powerNeeds` (`Items/BasicTech/StdElecPanel.java:213-253`, weights at `:223-224`, share at `:236-239`, leftover at `:245` and `:251`, stored need at `:249`). Generators and sources are excluded from the split (`:223`, `:231`).

Components join a circuit when a room owns them. `StdElecPanel.setOwner` calls `CMLib.tech().registerElectrics` for a room owner and unregisters otherwise (`StdElecPanel.java:107-122`). In `Libraries/GroundWired.java:134-165` the circuit key for a ship room is the ship area name plus its `REGISTRY` blurb, lower-cased; a land lot uses its lot ID and any other room its extended room ID. Registrations hold weak references (`GroundWired.java:167-200`).

### 2. Manufacturers

Each tech item stores a manufacturer name that defaults to `RANDOM` (`Items/BasicTech/StdTechItem.java:46`). `getFinalManufacturer()` resolves the name once and caches the result in memory (`StdTechItem.java:95-104`). `RANDOM` picks at random among manufacturers whose zapper item mask matches the item and whose tech-level band fits, falling back to the default (`Libraries/GroundWired.java:1007-1035`). The default manufacturer has efficiency and reliability both 1.0 (`Common/DefaultManufacturer.java:49-50`). The interface describes efficiency as a power multiplier (1.0 standard, 2.0 very inefficient, 0.5 super efficient) and reliability as 1.0 standard, 2.0 super-standard, 0.5 sub-standard (`Common/interfaces/Manufacturer.java:98`, `:114`). The `RANDOM` pick is not saved, so an item named `RANDOM` may get a new manufacturer each time it is created (inferred from the code; the save path was not traced).

The two uses of reliability disagree in direction; see Problems 3.

### 3. Energy weapons (StdElecWeapon, GenElecWeapon and seven named types)

The data model is `mode` (current) and `modeTypes` (allowed), both on `StdElecWeapon`, over an eight-value `ModeType` enum: STUN, NORMAL, KILL, DISINTEGRATE, MAIM, DISRUPT, LASER, SONIC (`Items/BasicTech/StdElecWeapon.java:50-63`). Defaults are base damage 20, range 0 to 10, base value 1500 and a 1000 power store (`StdElecWeapon.java:76-83`). Weapon mode, not a separate field, decides the damage type that shields later test.

Mode to damage type (`StdElecWeapon.java:90-109`), using `Items/interfaces/Weapon.java:51-73` constants:

| Mode | `weaponDamageType()` | Damage value set in `recoverPhyStats` | Power per hit (derived, default damage 20, efficiency 1.0) |
|---|---|---|---|
| NORMAL, KILL | field default `TYPE_SHOOT` (6) | unchanged (20) | 20 |
| STUN | `TYPE_SONICING` (12) | forced to 1 (`:159`) | 1 |
| MAIM | `TYPE_SLASHING` (1) | unchanged | 20 |
| DISRUPT, DISINTEGRATE | `TYPE_MELTING` (9) | `1 + 2 × damage` (`:169`), so 41 | 41 |
| LASER | `TYPE_LASERING` (11) | unchanged | 20 |
| SONIC | `TYPE_SONICING` (12) | unchanged | 20 |

Mode selection uses a prefix match on the allowed modes (`StdElecWeapon.java:111-120`). A `TYP_ACTIVATE` carrying a setting name switches the mode in `executeMsg`; an unknown name is refused in `okMessage` with "'X' is an unknown setting" (`:421-435`, `:241-254`).

The firing sequence is split across the two phases:

1. The attack is posted by `CMLib.combat().postAttack` (`Libraries/MUDFight.java:580-600`). The hit roll happens later; a miss sends a miss message and a hit calls `postWeaponDamage` (`Libraries/MUDFight.java:1190-1232`). So a miss sends no `TYP_DAMAGE` and costs no power.
2. `okMessage` on `TYP_WEAPONATTACK` refuses the swing when the weapon is switched off or has no power, and nothing is charged. A ranged weapon prints "goes *click* and does not fire"; a melee-held one prints "seems to be out of power" or "seems to be turned off" (`StdElecWeapon.java:440-458`).
3. `okMessage` on `TYP_DAMAGE` (`:460-483`) sets the success factor to `reliability × 0.5` (`:463-465`). The cost is the weapon's current damage times `max(0.33, |2 − efficiency|)` (`:466`). If the store covers the cost, it is paid and the damage is multiplied by the success factor (`:467-472`). If not, the store is set to zero and the damage is left at full strength (`:473-476`). A stun hit then clamps damage to 1 (`:477-481`).
4. `executeMsg` on `TYP_DAMAGE` applies the mode's effect (`:285-404`):
   - STUN: if the hit has value, invoke `CombatSleep` on the target, or set the sleeping disposition if that ability is missing; make both sides stop fighting with `makePeace`; set the value to 0 (`:290-308`; ability at `Abilities/Misc/CombatSleep.java`).
   - MAIM: invoke `Amputation` (`:366-375`; `Abilities/Misc/Amputation.java:42`).
   - DISRUPT and DISINTEGRATE: if the hit value exceeds 1.5 × the base damage, and (for a mob) reaches its current hit points, the target dies. A mob goes through `CMLib.combat().postDeath` (`:385-388`); DISINTEGRATE also removes the matching non-player corpse from the room (`:328-352`). Item targets are destroyed (`:354-360`, `:390-396`), but see Problems 1.
   - NORMAL, KILL, LASER, SONIC: damage only (`:311-318`).

With the default manufacturer, a hit deals half the damage the attack computed and costs the weapon's full current damage in power. A 1000-unit store therefore buys about 50 default hits (derived: 1000 ÷ 20). A DISRUPT weapon buys about 24 (derived: 1000 ÷ 41).

### 4. Ammunition guns (StdGun and GenGun)

Rounds are stored in `usesRemaining` (`ammunitionRemaining()` returns it, `StdGun.java:400-403`). The ammunition type string is stored in `readableText` (`:387-397`). `requiresAmmunition()` is true only when that string is non-empty (`:373-379`). A gun with an empty ammo type is therefore never decremented.

- **Spending.** Every attack attempt that requires ammunition spends one round in `okMessage` (`StdGun.java:246-277`, decrement at `:276`). Because the check runs at `TYP_WEAPONATTACK`, misses cost rounds too.
- **Empty gun.** The swing is refused with "is out of <ammo>", and the holder auto-enqueues `LOAD ALL` (or `REMOVE` for a monster with no matching ammo), at most once per tick (`:253-271`).
- **Reload.** A `TYP_RELOAD` moves the smaller of capacity, magazine count and remaining room (`:169-208`). Savable effects on the magazine are copied onto the gun and made non-savable, and non-savable effects already on the gun are cleared first (`:183-202`). An empty magazine is destroyed (`:203-204`). This lets ammunition carry an effect, such as a coating, into the gun.
- **Unload.** `Commands/Unload.java:131` builds a new ammunition item holding the gun's count. `StdGun` then moves non-savable effects onto that item, zeroes the gun and drops the item in the room (`StdGun.java:209-230`).
- **Enchantment.** Wielded guns with ability add 2 damage and 10 attack per point (`StdGun.java:133-150`, lines `:145-146`).
- **Ranges and capacity** can be overridden by bit fields packed into `phyStats().armor()` (`StdGun.java:347-357`, `:434-439`).
- **Thrown guns** add a drop message after the attack (`StdGun.java:234-237`).

Defaults (`StdGun.java:61-71`): damage 20, ranges 1 to 10, base value 1500, ammunition type `bullets`, capacity 10, loaded at 10. The GenGun template exposes `AMMOTYPE` and `AMMOCAPACITY` to the builder (`Items/BasicTech/GenGun.java:83-84`, `:100-106`).

### 5. Personal shields (StdPersonalShield and seven Gen kinds)

A personal shield is worn about the body, is active by default and holds 1000 power (`StdPersonalShield.java:52-62`). It has no idle drain. Only hits spend its store.

The hit flow in `okMessage` on `TYP_DAMAGE` (`StdPersonalShield.java:148-183`):

1. Skip unless the shield is active, has power, and is worn properly (`:149-151`).
2. Compute `s = 0.5 × reliability of the shield's own manufacturer`, plus `0.05` for each tech level the shield exceeds the weapon's, clamped to plus or minus 10 levels (`:153-162`). The weapon's tech level comes from the `Electronics` tool, or the global tech level for other tools (`:156-158`).
3. Ask `doesShield(mob, msg, s)`. Subclasses override this. The base version is `Math.random() >= s`, which gives block probability `1 − s` (`:106-109`).
4. If it fires, cost = raw hit value × `max(0.33, |2 − efficiency|)` (`:165`). If the store covers it, the store is debited and `doShield` runs (messages); the hit is then vetoed, because `doShield` returns false in every blocking kind (`:166-170`). If the store cannot cover it, the store is set to zero and the hit lands (`:173`).
5. At zero power, a deactivate message is sent to the room (`:175-181`).

The kinds differ only in `doesShield` and `doShield` (messages). Each rule below is the condition in that class's `doesShield`:

| Class | Lines | Hits affected | Notes |
|---|---|---|---|
| GenEnergyShield | `GenEnergyShield.java:91-103` | Electronic weapons in `TYPE_SHOOT` (NORMAL and KILL modes) | Matches "standard blaster and energy fire" in its description |
| GenIntegrityShield | `GenIntegrityShield.java:91-103` | Electronic weapons in `TYPE_MELTING` or `TYPE_SLASHING` (DISRUPT, DISINTEGRATE, MAIM) | Stops disruption and disintegration |
| GenReflectionShield | `GenReflectionShield.java:91-103` | Electronic weapons in `TYPE_LASERING` | Laser only |
| GenMutingField | `GenMutingField.java:91-104` | Electronic weapons in `TYPE_SONICING` (STUN, SONIC) | Also sets `CAN_NOT_HEAR` on the wearer's stats (`:71`) |
| GenKineticField | `GenKineticField.java:91-103` | Any non-Technical `Weapon` (melee, mechanical guns) | Also sets `CAN_NOT_HEAR` (`:71`) |
| GenDeflectionShield | `GenDeflectionShield.java:91-94` | Any hit, by probability | Generic blocker |
| GenAbsorbantShield | `GenAbsorbantShield.java:69-101` | Any hit; `doesShield` is always true | Scales the hit to `s × value` (`:81`), and full absorb at `s ≥ 1.0` (`:73-77`) |

Consequences of the formula, using the Manufacturer doc values:

- Default reliability 1.0 gives `s = 0.5`, so a blocking shield stops half of its hits.
- Reliability 2.0 gives `s = 1.0`. `Math.random()` is below 1.0, so a blocking kind never blocks. The manufacturer doc calls 2.0 "super-standard".
- Reliability 0.5 gives `s = 0.25`, so a blocking kind stops three hits in four.

Because the same `s` is the damage fraction that gets through for the absorbent shield, the design reading is "`s` is the portion that passes". Under that reading, better shield quality passes more damage. See Problems 3.

### 6. Ticker fields (GenTickerShield, GenStealthShield, GenEclipseField)

The ticker family is not a damage filter. It is a timer. While worn and active, the field loses one power unit each electronics tick (`GenTickerShield.java:95-118`, drain at `:101-102`). The tick starts when the item first gets an owner (`:83-92`, `Tickable.java:126`). The default store is 100 (`GenTickerShield.java:62-63`), which is about 400 seconds at the default 4-second tick (derived; `core/CMProps.java:681`). Power is clamped at zero, and at zero the field deactivates (`:103-112`); if the owner is not a mob it deactivates silently (`:113-114`).

- **Stealth.** `GenStealthShield` gives the wearer `IS_INVISIBLE` while it is active, worn and powered (`GenStealthShield.java:68-78`, bit at `:76`). Looking at the wearer is denied unless the looker's tool is `Technical`, uses a control message, and has a higher tech level than the field (`:85-96`).
- **Eclipse.** `GenEclipseField` does the same with `IS_HIDDEN` (`GenEclipseField.java:68-78`, bit at `:76`), and has no scan check.
- Bare `GenTickerShield` has no stat effect of its own; only the subclasses change anything.

### 7. Tricorder (StdTriCorder and GenTriCorder)

The tricorder is a `Computer` whose contents must be `Software` of type `PERSONAL_SOFTWARE` (`StdTriCorder.java:101-105`; `panelType()` returns that type at `:73-76`). Its capacity is 3 (`:65`) and it starts with 1000 power (`:66-67`). Its only current reader is its owner (`:176-185`).

- **Menus.** The current menu is the internal name of an active software item (`:50`, `:84-87`). `readableText()` builds the screen from the software in the current menu and its parent (`:131-174`). Typing `<` returns to the parent (`:279-281`). An unknown command produces "Unknown command. Please read the screen for a menu." (`:302`).
- **Boot.** `TYP_ACTIVATE` with no text boots the device and clears the menu (`:351-361`). With text, the text is matched against each software item's activation strings (`:362-388`). Reading and writing require holding it and an active system (`:204-218`).
- **Software ticks.** On each electronics tick, each software item receives `TYP_POWERCURRENT` with value 1, or 2 if it is the active menu (`:442-457`, value at `:452`). The base program only tests the value for being positive, to decide whether it is active (`Items/Software/StdProgram.java:466-476`, `:580-581`). The extra unit has no effect in the base class.
- **Drain.** Every `POWER_RATE` = 4 electronics ticks, the device loses one power unit (`StdTriCorder.java:45`, `:459-465`). This happens whether or not it is active. At zero it calls `deactivateSystem()`, which is a no-op when already off (`:503-527`). At the default tick this is one unit per 16 seconds, so 1000 units last about 4.4 hours (derived).
- **Shutdown** notifies each software item and prints "The screen on <T-NAME> goes blank." (`:503-527`).

### 8. Panels and installation (StdElecPanel and GenElecPanel)

A panel is a wall fixture: it can be opened, is always active, has no idle power draw, and accepts only `Technical` items whose tech type matches its own panel type, or any type if the panel is `ANY` (`StdElecPanel.java:48-59`, `:85-94`). Its own tech type is `SHIP_PANEL` (`:61-65`). The generic panel has capacity 25000 (`GenElecPanel.java:54`).

Installation runs through a skill:

- A non-admin putting a `TechComponent` in with value 0 or less is redirected to the `AstroEngineering` skill's INSTALL operation (`StdElecPanel.java:133-154`; `Abilities/Tech/AstroEngineering.java:198-206`).
- The skill sends `MSG_INSTALL` with value `50 + bonus`, where the bonus is up to 50 from manufacturer and tech-type expertise (`AstroEngineering.java:112-136`, `:200-201`).
- `executeMsg` stores `value / 100` as the component's installed factor, so a fully skilled installer gets 1.0 and an unskilled one 0.5 (derived; `StdElecPanel.java:179-184`).
- Admins bypass the test and get factor 1.0 (`:134-135`, `:176`).
- Activating a panel activates it and shows "connect(s)" (`:192-196`). Deactivating it sends `MSG_DEACTIVATE` to every electronic item in its deep contents (`:197-211`).

### 9. Space objects and munitions (StdSpaceTech, StdSpaceTechWeapon, StdTorpedo, StdSmartTorpedo)

- **Space object.** Holds coordinates, radius, direction, speed and known source and target (`StdSpaceTech.java:44-49`). Radius is random near one kilometre from `new Random(System.currentTimeMillis())` (`:56-57`). Mass is weight × radius (`:163-166`). Destroying the object removes it from space (`:64-69`).
- **Space weapon.** A damage or collision message involving it emits a space event ("is hit by") and destroys it (`StdSpaceTechWeapon.java:67-106`). A ballistic tick also destroys it (`:54-65`). Space weapons are single-use projectiles.
- **Torpedo.** Damage 100, burning, weight 1000, radius 2, phyStats speed 0.3 (`StdTorpedo.java:45-53`). It is ammunition that never runs out: its round count is fixed at 1 and cannot be set (`:113-122`).
- **Chase clock.** Each ballistic tick, a torpedo in space resets its clock if its speed is zero (`StdTorpedo.java:86-87`). It destroys itself after 300000 ms of movement (`:37`, `:89-93`). The clock is the fixed five-minute limit the comment calls "sounds good". A stationary torpedo never times out.
- **Homing.** A smart torpedo with a known target computes an intercept with `space.calculateIntercept` at maximum speed `phyStats().speed()/100 × VELOCITY_LIGHT` (`StdSmartTorpedo.java:70`, `:77-78`). It rescans when the direction is unset, when speed is zero, or every third tick when the predicted miss distance exceeds the sum of the two radii (`:71-76`). It accelerates at max speed ÷ 8 (`:83`, `:89`) and turns at speed ÷ 8, with a minimum of 1 (`:97-100`).

Launchers start the ballistic tick with their own cadence (`Items/CompTech/StdShipWeapon.java:353`, `Items/CompTech/StdCompLauncher.java:366`), and `core/threads/StdTickClient.java:148-150` re-arms a tick every `numTicks` ticks.

### 10. The ship (GenSpaceShip)

The ship is a boardable item that owns an `Area` (`GenSpaceShip.java:46`, `:134-152`). The area is themed technology, and its first room is a metal room called "The Cockpit" (`:136-148`). Mass is the item's weight plus the area's mass, or 1000 if the area is not a ship (`:853-857`). Radius falls back to 50 (`:965-968`). The ship's own power methods are stubs returning zero (`:715-752`), so power lives in its parts and reaches them through the registry.

- **Registry key.** Area name plus the `REGISTRY` blurb (`:204-215`). Renaming draws a new random registry value (`:218-229`), but the unregister call in that method does nothing (Problems 5).
- **Thrust.** Commands arrive as `TechCommand.ACCELERATION` (`Items/interfaces/Technical.java:184-203`) with a direction, an amount and a reaction flag (`GenSpaceShip.java:313-443`).
  - STARBOARD and PORT yaw, DORSEL and VENTRAL pitch, by the amount in degrees, and only when undocked (`:325-378`).
  - FORWARD and AFT thrust along the facing or against it (`:379-423`). A non-reaction burn zeroes speed first (`:400-407`). The thrust is `amount × inAirFactor` (`:397`, `:409`), minus `speedTick`, which accumulates the thrust already applied this area tick (`:410`, `:418`; reset at `:121-131`).
  - A docked ship undocks when FORWARD thrust exceeds 1 G, `SpaceObject.ACCELERATION_G` (`:388-392`; `core/interfaces/SpaceObject.java:255`).
  - A `MASK_INTERMSG` flag makes the command a simulation with no state change (`:315`).
  - Each command sends an `ACCELERATED` message to the ship's computers (`:425-438`, `sendComputerMessage` at `:759-780`).
- **Hull.** Incoming damage is divided by the material's hardness, with a floor of 1.0 (`:257-267`, `RawMaterial.CODES.HARDNESS`). Hull points are the item's `usesRemaining` (`:506-531`).
- **Damage split** (`:445-534`). Collision damage goes entirely to the hull (`:456-457`). Energy damage (electric, acid, cold, fire, gas, laser, paralysis, poison, sonic, undead, water; `:459-469`) costs the hull one point only when `random + damage/100 > hull reliability` (`:471-472`). The rest goes to one random registered component, chosen from those that are not destroyed, not panels, not software, not uninstalled parts and not idle non-parts (`:475-499`). Hull points at or below zero destroy the ship (`:514`).
- **Messages** scale with the damage: above 75 "booming crash and crushing jolt", above 50 "loud crash and hard jolt", above 25 "noise and small jolt", otherwise "bump and short rattle" (`:520-529`, paraphrased).
- **Collisions** (`:535-624`). A huge object (mass at least 100 × 1 km) stops the ship and shuts its engines down (`:539-545`, `:681-701`). The damage absorbed is `floor(speed × mass + other speed × other mass)` (`:564`). A landing on an area docks at a landing point (`:582-595`).
- **Docking.** `findNearestDocks` searches up to 25 radiant rooms for spaceports, preferring the same metro area, and falls back to the metro map (`:632-679`).
- **Sensors.** Every message from outside the ship's area is forwarded to the ship's sensors (`:278-282`). The sensor list is computed once and cached (`:100-118`).
- **Listeners.** External programs register for a `TechCommand` (`:998-1027`). The ship consults them before and after handling a command (`:234-251`, `:284-298`).

### 11. Gen templates

Each `Gen` class is a builder-editable template. Its fields appear as a `MYCODES` array, and `getStat`/`setStat` switch on the index (`GenElecWeapon.java:82-83`, `:85-154`). Shared codes from `GenericBuilder.GenItemCode` are prepended (`:168-182`). `sameAs` compares every code string (`:185-196`). Serialization goes through `text()` and `setMiscText()` via `CMLib.coffeeMaker()` (`:56-80`). This is the in-game editing surface; the Std class is the hard-coded default.

## Content worth mining

### Energy weapon templates

| Class | Name | Current mode | Modes offered | Weight |
|---|---|---|---|---|
| GenBlaster | a blaster gun | KILL | KILL | 5 (`GenBlaster.java:48`) |
| GenLaserGun | a laser pistol | LASER | LASER | 5 (`GenLaserGun.java:48`) |
| GenPhaser | a phaser | KILL | STUN, KILL | not set (`GenPhaser.java:44-52`) |
| GenPhaser2 | a type-II phaser | KILL | STUN, KILL, DISINTEGRATE | not set (`GenPhaser2.java:44-52`) |
| GenDisruptor | a disruptor weapon | DISRUPT | STUN, DISRUPT | 5 (`GenDisruptor.java:50-54`) |
| GenDisruptor2 | a disruptor type II weapon | MAIM | STUN, MAIM, DISRUPT | 5 (`GenDisruptor2.java:50-54`) |
| GenSonicGun | a sonic resonater gun | SONIC | SONIC | 5 (`GenSonicGun.java:48-51`) |

The stun setting is the non-lethal option on every phaser-type weapon. Disintegrate exists only on the type-II phaser. The disruptor family is the only one whose top mode can outright kill a target.

### Shields and fields

| Class | Stops | Power model | Lines |
|---|---|---|---|
| GenEnergyShield | Electronic NORMAL/KILL fire | Per blocked hit (1000 store) | `GenEnergyShield.java:91-103` |
| GenIntegrityShield | Disruption, disintegration, maim | Per blocked hit | `GenIntegrityShield.java:91-103` |
| GenReflectionShield | Laser | Per blocked hit | `GenReflectionShield.java:91-103` |
| GenMutingField | Sonic and stun | Per blocked hit; silences the wearer | `GenMutingField.java:91-104` |
| GenKineticField | Melee and mechanical weapons | Per blocked hit; silences the wearer | `GenKineticField.java:91-103` |
| GenDeflectionShield | Any weapon, by chance | Per blocked hit | `GenDeflectionShield.java:91-94` |
| GenAbsorbantShield | Any hit, by scaling | Per hit scaled | `GenAbsorbantShield.java:69-101` |
| GenTickerShield | Nothing (base timer) | 1 unit per 4 s (100 store) | `GenTickerShield.java:95-118` |
| GenStealthShield | Sight (invisibility) | 1 unit per 4 s; scan check by tech level | `GenStealthShield.java:68-98` |
| GenEclipseField | Sight (hidden) | 1 unit per 4 s | `GenEclipseField.java:68-78` |

### Ship and space items

| Class | Key numbers and facts | Lines |
|---|---|---|
| GenSpaceShip | Cockpit room; hull points as uses; hardness divides damage; 1 G docking threshold | `GenSpaceShip.java:134-148`, `:259-263`, `:388-392` |
| StdTorpedo | Damage 100, weight 1000, radius 2, burning, five-minute chase | `StdTorpedo.java:37`, `:45-53` |
| StdSmartTorpedo | Intercept with rescans; max speed from the torpedo's speed stat | `StdSmartTorpedo.java:70-100` |
| GenCannon | Siege cannon, piercing, ammunition `cannonballs`; parent in a bucket not covered here | `GenCannon.java:38`, `:55-57` |
| GenTriCorder | Templated tricorder; capacity 3 and 1000 power by default | `StdTriCorder.java:65-67` |
| GenElecPanel | Ship panel, capacity 25000, default type ANY | `GenElecPanel.java:54` |

### Constants worth reusing

- `SpaceObject.VELOCITY_LIGHT = 29979245` (`core/interfaces/SpaceObject.java:291`), with warp multiples built from it (`:301-327`).
- Acceleration unit: `ACCELERATION_G = 1`. Collision damage applies only when the previous speed exceeds `ACCELERATION_DAMAGED`, which is 30 G (`SpaceObject.java:255`, `:265`; `GenSpaceShip.java:553-554`).
- Atmospheric drag: streamlined 0.05, brick 0.30 (`SpaceObject.java:283-285`).
- Weapon types as integers (`Items/interfaces/Weapon.java:51-73`): slashing 1, piercing 2, burning 4, shoot 6, melting 9, lasering 11, sonicing 12.

### Messages (short, paraphrased)

- Weapon misfire: "goes *click* and does not fire" (ranged) and "seems to be out of power" (`StdElecWeapon.java:447-450`).
- Stun: the target "goes unconscious" (`:299`).
- Disruptor on an item: the item "explodes" (`:394`); disintegrator on an item "disintegrates" (`:358`).
- Shield on: "A field of energy surrounds <O-NAME>" (`StdPersonalShield.java:65-70`), with per-kind variants.
- Absorbent shield partial absorb: "absorbs a little / some / much / most" of the damage, by the scaled fraction (`GenAbsorbantShield.java:83-92`).
- Tricorder: "boots up", "shuts down", "The screen on <T-NAME> goes blank." (`StdTriCorder.java:358`, `:423`, `:524`).

## Notable design ideas

- **The mode is the damage type.** One enum drives the damage type, the damage scaling and the side effect, and the comment at `StdElecWeapon.java:87-89` says the shield and weapon balance depends on this. Shields then need only one type test each.
- **Two budgets.** Events spend power (hits fired, hits blocked; `StdElecWeapon.java:466-471`, `StdPersonalShield.java:165-168`). Time spends it for fields and the tricorder (`GenTickerShield.java:101-102`, `StdTriCorder.java:459-465`). The split tells a player whether a gadget is for fights or for an outing.
- **Power by message.** Power is distributed by panels passing `TYP_POWERCURRENT` (`StdElecPanel.java:213-253`). Nothing holds a global pool, so a circuit can be cut or re-routed by moving things.
- **Ammo carries effects.** A loaded magazine's savable effects transfer to the gun on reload and come back on unload (`StdGun.java:183-202`, `:209-221`).
- **Hull damage goes inside.** Energy hits mostly hit a random system rather than the hull (`GenSpaceShip.java:459-499`). A ship fight is about systems, not one hit-point bar.
- **Tech-level stealth.** A scanner beats a stealth field only if its tech is higher (`GenStealthShield.java:85-96`), which gives a clear, data-driven contest.
- **Templates for everything.** Every object has a `Gen` twin that is editable in game, so new weapons are data, not code (`GenElecWeapon.java:82-154`).

## Problems and cruft

1. **Item casts in disintegrate and disrupt.** `Items/BasicTech/StdElecWeapon.java:358` and `:394` cast `targ` to `MOB` to call `location()`, but this branch runs when `targ` is an `Item`, so the cast throws `ClassCastException` as written. Whether `TYP_DAMAGE` with an item target reaches these lines in play is unverified.
2. **Last shot is not scaled.** If the store cannot cover the cost, the weapon empties and the hit keeps full strength (`StdElecWeapon.java:473-476`). A nearly empty gun delivers its strongest shot.
3. **Shield quality appears inverted.** The shield's own `s` (reliability × 0.5 plus tech difference) is used as the chance the hit gets through, so a better shield blocks less (`StdPersonalShield.java:153-163`, `:106-109`). The manufacturer doc calls reliability 2.0 "super-standard" (`Common/interfaces/Manufacturer.java:114`). At 2.0, a blocking shield never blocks. The weapon side uses the same reliability as a damage multiplier, so the two disagree on direction. Intent is unverified.
4. **Tricorder drain has no floor and no gate.** The unit decrement runs whether or not the device is active (`StdTriCorder.java:459-465`), and the setter does not clamp (`StdElecItem.java:95-98`). Power can go negative while the tick is running, so a long-abandoned tricorder may need many units to become usable again. How long the tick keeps running is unverified.
5. **Rename's unregister is a no-op.** `GenSpaceShip.java:225` calls `unregisterElectronics(null, key)`. `GroundWired.java:318-320` only works with a non-null component, so the old registry set is never cleared. Whether the ship's systems are stranded depends on re-registration, which was not traced.
6. **Sensor cache never refreshes.** `GenSpaceShip.java:100-118` caches the sensor list on first use and never clears it. If the first outside message arrives before the sensors register, the ship stays blind to them. Unverified in practice.
7. **Collision damage is not kinetic energy.** `GenSpaceShip.java:564` sums speed × mass products. The comment at `:557-559` says the approach is technically wrong.
8. **Overloaded fields.** The gun's ammunition type lives in `readableText` (`StdGun.java:387-397`). Ranges and capacity are packed into `phyStats().armor()` bits (`:347-357`, `:434-439`; `StdElecWeapon.java:510-523`). Both are hard to read or extend.
9. **Inconsistent "unlimited" sentinels.** `StdGun.java:298-299` maps `Integer.MAX_VALUE` to 100 rounds, but `:409-410` maps it to 20.
10. **Stale comment.** `StdElecWeapon.java:460` says "remember 50% miss rate", but the code scales damage. Misses are decided earlier, in `Libraries/MUDFight.java:1190-1232`.
11. **Dead code.**
    - `StdGun.weaponHealth()` (`StdGun.java:303-330`) is never called here; `Items/Weapons/StdWeapon.java:346` has its own copy.
    - The `readFlag` branches in `StdTriCorder.java:376`, `:386`, `:405`, `:415` are always false.
    - `lastReader` is written at `:265` and cleared at `:430-431`, but read nowhere in this bucket. The source comment says "whats this do?".
    - `GenDisruptor.java:44` and `GenDisruptor2.java:44` declare an unused `state`.
    - `StdElecPanel.java:181-182` cannot run: `okMessage` refuses install values of 0 or less (`:155-161`).
12. **Duplicate stat code.** `TECHLEVEL` appears twice in `GenTorpedo.java:97`, `:99` and `GenSmartTorpedo.java:87`, `:89`. `GenCannon.setStat` also writes extension values for codes it already handled (`GenCannon.java:135`).
13. **Copy-paste text.** `GenAbsorbantShield.java:48-49` sets the description twice; the second line is the integrity shield's text, so the absorbent shield ends up described as an integrity field. `StdPersonalShield.java:54` describes a muting field. `GenTickerShield.java:73` reads "The around <S-NAME>" (a word is missing). Grammar slips: "An powerful" (`GenKineticField.java:56`) and "An dense" (`GenMutingField.java:56`).
14. **Side effects in the veto phase.** A round is spent in `okMessage` before later listeners can refuse the shot (`StdGun.java:276`). Shields send messages and change power inside `okMessage` (`StdPersonalShield.java:163-170`). Two shields set the wearer's sensing mask during a check (`GenKineticField.java:71`, `GenMutingField.java:71`).
15. **Shared random seed per object.** Each space object seeds its own `Random` from the clock (`StdSpaceTech.java:56`), so objects created in the same millisecond get the same radius.
16. **Comment versus behaviour on software power.** The "active menu costs double" intent (`StdTriCorder.java:452`) has no cost in the base program, which only tests the value for being positive (`Items/Software/StdProgram.java:466-476`).
17. **Torpedo clock resets at zero speed.** A torpedo that stops in space never times out (`StdTorpedo.java:86-87`). This may be deliberate; the code does not say.

Multiplayer assumptions, to rethink for a single player: `makePeace` on both fighters (`StdElecWeapon.java:303-306`), `CMLib.combat().postDeath` with corpse clean-up (`:328-352`), the shared circuit registry with weak references (`GroundWired.java:167-200`), and `lastReader` (dead, above).

## Takeaways for a Python SUD

**Borrow:**

- **Weapon as a mode table.** A weapon holds a few named modes. Each mode gives a damage type, a damage multiplier, a power cost per hit and at most one effect name.

  ```python
  @dataclass(frozen=True)
  class WeaponMode:
      damage_type: str          # "shoot", "melting", "sonic", "lasering", "slashing"
      damage_scale: float       # 1.0, or 1 + 2*base for disruptors
      cost_per_hit: int        # defaults to the scaled damage
      effect: str | None       # "sleep", "amputate", "disintegrate", ...
  ```

- **Shields as type filters.** A shield names the damage types it stops, a block chance from its quality, and a power cost per block. Keep one rule for all kinds.
- **Two budgets, one clock.** Use per-event costs for combat gear and per-time drains for fields and handheld computers. Route every drain through one `tick_or_turn()` method, since the real-time versus turn-based choice is still open.
- **Magazines.** A magazine is a count plus an ammo type. Reload and unload move counts. Effect transfer on reload is a neat, optional mechanic.
- **Tricorder as a menu tree.** Model the menus as a dict of states, each with its commands and software. Skip the control-message overloading: a plain command dispatcher is enough.

**Simplify:**

- Drop the panel and registry circuits. One room-level `power` integer per device covers a single player.
- Replace the space physics with a ship object that has hull points, a heading and a speed. A collision can deal `mass × speed` with a documented constant.
- Drop the listener registries and the thread-based tick service. Use explicit calls.

**Drop or rethink:**

- Multiplayer concerns: `makePeace`, PvP corpse rules and any faction assumptions. Single-player NPCs can be scripted instead.
- The `RANDOM` manufacturer roll. Use fixed per-item quality numbers in data.

**Fix before copying:**

- Choose one sign convention for quality, and check that a better item blocks more.
- Clamp every power store at zero, and gate drains on active state.
- Scale the final shot the same way as every other shot.
- Fix the item-target cast in disintegrate and disrupt by writing a separate path for item targets.

## Coverage

- **Read in full** (all lines except package, import and licence headers): 41 files. This covers the three core electronics bases, the panel and the container, the tricorder, both gun families, the energy weapon, the personal and ticker shield bases and all of their Gen subclasses, the space object and torpedo bases, the cannon, the tech-item base, and the ship (`GenSpaceShip.java`, all 1149 lines).
- **Skimmed:** `Items/BasicTech/GenSmartTorpedo.java:96-199` (stat-code boilerplate matching `GenTorpedo.java:95-209`).
- **Followed out of bucket, only as far as the cited interactions:** `Libraries/GroundWired.java` (registry, manufacturer lookup), `Common/DefaultManufacturer.java`, `Common/interfaces/Manufacturer.java`, `Libraries/MUDFight.java` (attack and damage posting), `Commands/Unload.java`, `Abilities/Tech/AstroEngineering.java` (install), `Abilities/Misc/CombatSleep.java` and `Amputation.java` (names only), `Items/Software/StdProgram.java` (power messages), `Items/Basic/StdItem.java` (electronics tick), `core/threads/ServiceEngine.java` and `StdTickClient.java` (tick timing), `core/CMProps.java` (tick length), `core/interfaces/SpaceObject.java`, `Items/interfaces/Weapon.java`, `Items/interfaces/Technical.java` (constants and enums), and two launcher call sites in `Items/CompTech/`.
- **Not followed:** `GenSiegeWeapon` (parent of `GenCannon`), `GenBoardable` (parent of `GenSpaceShip`), `StdContainer`, `StdItem` (b172), and the rest of the CompTech and Software families.
- **Not examined at runtime:** all numbers and behaviour are from reading the code. Derived figures are marked as such.
