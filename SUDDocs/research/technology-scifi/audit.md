# Audit findings

[Start](README.md) · [Capabilities](inventory.md) · [Proposals](proposals.md)

The 28 findings below describe the baseline, not changes made by this dossier.
P1 means a foundation to repair before promoting or extending the affected
feature; P2 means a narrower defect, missing integration or selected design
work. These are development priorities, not a network security severity scale.
No data loss or exception was reproduced beyond the specific observations stated.

Runtime observation can coexist with additional static evidence in one finding.
A static defect follows directly from the inspected branch, but its full player
command path has not necessarily been exercised. Integration/design gaps require
a product contract before a test can meaningfully declare them fixed.

The older audit was not copied wholesale: current code uses GameTime in these
programs, StdSpaceTech uses CMath.newRandom, and ShipSensorProgram now has a live
onPowerTimer override. Blanket claims that those older clock/random/timer
problems remain would be wrong. S24 physical space persistence is implemented.
Other concerns below remain visible in current code.

## Priority overview

| Area | Findings | First repair proposal |
| --- | --- | --- |
| Generic editor data and classification | F01–F03 | R01 |
| Medical/database output and software discovery | F04–F06 | R02–R03 |
| Navigation, sensing and arcs | F07–F11 | R04–R05 |
| Power, quality and combat accounting | F12–F16 | R06–R08 |
| Space mathematics and incomplete damage | F17–F19 | R09–R10, S04–S05 |
| Identity, persistence and warnings | F20–F22 | R11–R12 |
| Missing device behaviors and world assets | F23–F24 | T01–T04, selected device proposals |
| Legacy network tool, coverage and remaining device faults | F25–F28 | T07, Q02–Q04, R04/R06 |

## Detailed findings

### F01 — Remaining-power edits overwrite capacity

**runtime observation · P1.**

**Evidence.** For GenFuellessGenerator, GenCompGenerator and GenSpaceDrive, start at capacity 1000 / remaining 250 and set POWERREM=40. Each ends at capacity 40 / remaining 250. Their POWERREM setter branches call setPowerCapacity.

**Effect.** Builder edits and generic field restoration can corrupt both a component budget and its saved configuration. Remaining power may exceed capacity.

**Proposed repair.** Correct the three setters; audit sibling generic fields and specify bounds without silently destroying recoverable legacy data.

**Closure criteria.** Each independent field edit and XML round trip preserves the other fields, including a non-full device. Migration reports invalid legacy values.

**Proposals:** R01.

**Code/fixtures:** [GenFuellessGenerator.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenFuellessGenerator.java) · [GenCompGenerator.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenCompGenerator.java) · [GenSpaceDrive.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenSpaceDrive.java).

### F02 — Fractional recharge settings are rounded to integers

**runtime observation · P1.**

**Evidence.** GenComputerConsole.setStat(RECHRATE, 0.5) produces 1. Its parser is s_parseLongExpression, which rounds; several generator and thruster setters use the same integer parser. This is rounding, not always truncation to zero.

**Effect.** A half-rate console becomes full-rate. Small nonzero authored rates can become zero. Not every component is affected: GenSpaceDrive uses the floating parser for this field.

**Proposed repair.** Use the floating expression parser consistently and validate finite nonnegative rates with a documented upper-bound policy.

**Closure criteria.** 0.1, 0.5 and 1.25 survive edits and export/import within float tolerance for all affected generic classes.

**Proposals:** R01.

**Code/fixtures:** [GenComputerConsole.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenComputerConsole.java) · [GenFuellessGenerator.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenFuellessGenerator.java) · [GenCompGenerator.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenCompGenerator.java) · [GenShipEngine.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenShipEngine.java) · [GenShipThruster.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenShipThruster.java) · [GenShipFuellessThruster.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenShipFuellessThruster.java).

### F03 — Gravity generators identify and compare as other component types

**runtime observation · P1.**

**Evidence.** StdShipGravityGen reports SHIP_ENVIRO_CONTROL; two fresh gravity generators return sameAs=false. getTechType returns the environment category and sameAs tests StdShipDampener.

**Effect.** Typed installation panels and manufacturer matching can reject or misclassify gravity generators. Equality checks misreport differences.

**Proposed repair.** Use SHIP_GRAVGEN, fix the equality guard, and diagnose existing panels that were authored around the wrong category.

**Closure criteria.** The generator fits its declared gravity panel, compares equal to an identical generator, and restores under a documented legacy category policy.

**Proposals:** R01.

**Code/fixtures:** [StdShipGravityGen.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdShipGravityGen.java) · [ElecPanel.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/ElecPanel.java).

### F04 — Health scans report zero percent for wounded targets

**runtime observation · P1.**

**Evidence.** A target at 50/100 HP reports health: 0%. HealthScanProgram divides two integer HP values before converting the result to a percentage.

**Effect.** Medical tools give misleading information at almost every non-full health value. A zero maximum is also unguarded in this expression.

**Proposed repair.** Perform guarded floating division and keep percentages consistent with textual health descriptions.

**Closure criteria.** 0/100, 1/100, 50/100, 100/100 and an invalid zero maximum produce meaningful bounded output without an arithmetic exception.

**Proposals:** R02.

**Code/fixtures:** [HealthScanProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/HealthScanProgram.java).

### F05 — Coordinate service handlers are not advertised

**runtime observation · P1.**

**Evidence.** DatabaseProgram advertises only IDENTIFICATION; ShipSensorProgram advertises no services. Both contain COORDQUERY handlers. GenShipProgram.findCoordinates uses the service registry. Scoped Java search found no advertised COORDQUERY provider.

**Effect.** The named coordinate lookup path used by navigation cannot reach these handlers. This does not establish that every COURSE command is broken: direct contact targeting follows other paths.

**Proposed repair.** Advertise the intended service, validate request/response formats, and test both database and sensed-contact lookups.

**Closure criteria.** A known beacon resolves through a real installed service, an unknown beacon fails clearly, and the result is isolated to the correct computer/circuit.

**Proposals:** R03.

**Code/fixtures:** [DatabaseProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/DatabaseProgram.java) · [ShipSensorProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/ShipSensorProgram.java) · [GenShipProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/GenShipProgram.java) · [StdProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/StdProgram.java).

### F06 — Database custom fields read NOTE instead of their own value

**runtime observation · P1.**

**Evidence.** Rendering a database entry with NAME and CUSTOM but no NOTE throws NullPointerException. getSpaceObjectDataResults reads jo.get(NOTE).toString() in the loop for every non-basic field.

**Effect.** Authored logs and star-chart metadata can break the terminal display. Adding NOTE masks the exception but repeats the note instead of each custom value.

**Proposed repair.** Render the actual key value, define null/missing-field display, and preserve unknown fields across exports.

**Closure criteria.** Two distinct custom fields, absent NOTE, null values and Unicode text render correctly without losing data.

**Proposals:** R02.

**Code/fixtures:** [DatabaseProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/DatabaseProgram.java).

### F07 — Engine profiling shares charts and turning capability is over-reported

**static defect · P1.**

**Evidence.** ShipNavProgram creates accelValues once outside its engine loop and EngProfile retains that array. fasterTurns ends with true even if no inertial component qualifies; its conditional also has mixed &&/|| precedence.

**Effect.** Autopilot can plan using the last profiled engine for every engine and assume fast turns without working inertial support. No complete flight was run for this finding.

**Proposed repair.** Allocate each chart independently, make profiling non-destructive, and make capability decisions explicit.

**Closure criteria.** A mixed two-engine vessel keeps distinct profiles; removing or disabling its inertial device changes the turn plan. Profiling restores thrust, fuel and flight state.

**Proposals:** R04.

**Code/fixtures:** [ShipNavProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/ShipNavProgram.java).

### F08 — The no-landing-zone path constructs a track with null

**static defect · P1.**

**Evidence.** landProcedure can construct ShipNavTrack(LAND, programPlanet, engines, null). ShipNavTrack immediately calls getClass on each supplied argument, including the null LocationRoom.

**Effect.** Landing near an otherwise eligible body with no authored landing room can throw instead of returning a useful rejection. The complete command path was not executed.

**Proposed repair.** Refuse the missing destination before track construction, or model an optional destination explicitly and define the fallback.

**Closure criteria.** Landing on a body with zero, one, multiple and removed landing zones either succeeds at a legal zone or leaves the ship safely under control with a clear reason.

**Proposals:** R04.

**Code/fixtures:** [ShipNavProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/ShipNavProgram.java).

### F09 — Sensor aiming repeats the second direction

**static defect · P2.**

**Evidence.** ShipSensorProgram sends dirs.get(0), then uses dirs.get(1) on every iteration of a loop whose index advances through all remaining directions.

**Effect.** A three-direction scan omits its third requested direction. Wider scan configurations do not match the command.

**Proposed repair.** Use the loop index and validate permitted directional sets before sending commands.

**Closure criteria.** A three-direction aim sends all three distinct directions once and rejects unsupported directions without partial retargeting.

**Proposals:** R05.

**Code/fixtures:** [ShipSensorProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/ShipSensorProgram.java).

### F10 — Optical contacts use the screen and angular sizes use integer division

**runtime observation · P1.**

**Evidence.** The viewscreen converter reports an Asteroid named audit asteroid as an item. Its nested proxy captures inherited me, the screen, rather than the convert argument. Separately, GenShipOpticalSensor computes atan(radius/distance) with long division on both operands; this second issue is static.

**Effect.** Screens misidentify contacts; small foreground objects can spuriously hide larger targets because both angular sizes round to zero. Zero-distance division also needs a policy.

**Proposed repair.** Capture the actual contact, use floating angular comparisons, and refresh directional caches after builder changes.

**Closure criteria.** Two distinct contacts retain their own identities; a smaller apparent occluder does not fully hide a larger one; coincident/overlapping objects do not crash.

**Proposals:** R05.

**Code/fixtures:** [GenShipViewScreen.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenShipViewScreen.java) · [GenShipOpticalSensor.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenShipOpticalSensor.java).

### F11 — Limited shield coverage can index before the array

**runtime observation · P1.**

**Evidence.** With StdShipShieldGenerator limited to three directions, repeatedly clearing/rebuilding coverage produced 10 index errors in 32 attempts at seed 42. The coverage loop reads centralIndex-offset without wrapping the index.

**Effect.** A valid partial-coverage shield can throw during an incoming attack. Full coverage avoids this particular branch.

**Proposed repair.** Share a bounds-safe arc implementation between weapons, sensors and shields, with explicit count and cache invalidation rules.

**Closure criteria.** Every center/count combination from zero through six has defined output, no duplicate arcs, no exception and the requested count where legal.

**Proposals:** R05.

**Code/fixtures:** [StdShipShieldGenerator.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdShipShieldGenerator.java) · [CoffeeDark.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CoffeeDark.java).

### F12 — Panel power accounting has two consumers for one allocation

**static defect · P1.**

**Evidence.** StdCompPanel inherits StdElecCompContainer.okMessage, which consumes its powerNeeds from the incoming power message into the panel store. Its executeMsg then distributes the remaining message to contents and powerNeeds is their aggregate demand.

**Effect.** Read literally, a panel can charge itself for child demand before forwarding that demand. Conservation across nested panels is uncertain; no full circuit-flow probe was run.

**Proposed repair.** Confirm with a one-source/one-panel/one-load fixture, then give panels one forwarding/accounting responsibility. Treat storage as an explicit component.

**Closure criteria.** For flat and nested networks: initial store plus generation equals final store plus useful consumption plus declared loss; under-supply is allocated once.

**Proposals:** R06.

**Code/fixtures:** [StdCompPanel.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdCompPanel.java) · [StdElecCompContainer.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdElecCompContainer.java) · [GroundWired.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/GroundWired.java).

### F13 — Efficiency and reliability have contradictory meanings

**integration gap · P1.**

**Evidence.** Manufacturer documents efficiency as a cost (0.5 better, 2 worse). Some generation/charging/shield paths multiply output by it, while other consumers multiply cost or divide fuel use. Personal shielding tests random >= reliability*0.5 plus tech difference, making higher reliability block less in the base shield.

**Effect.** Manufacturer quality has no coherent balance contract. Blindly reversing every multiplier would change existing content in incompatible ways.

**Proposed repair.** Specify separate quality, efficiency/cost and reliability semantics, keep a legacy calculation mode for migrated content, and inventory all consumers.

**Closure criteria.** A documented manufacturer matrix shows monotonic improvements in the new model, bounded probabilities and explicit legacy behavior for old worlds.

**Proposals:** R07.

**Code/fixtures:** [Manufacturer.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Common/interfaces/Manufacturer.java) · [StdPersonalShield.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdPersonalShield.java) · [StdCompGenerator.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdCompGenerator.java) · [StdShipThruster.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdShipThruster.java) · [StdElecCompItem.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdElecCompItem.java).

### F14 — Combat validation mutates resources and item destruction casts items to mobs

**static defect · P1.**

**Evidence.** StdGun.okMessage spends ammunition before later listeners can veto. Personal shields also spend power and emit output in okMessage. StdElecWeapon item-target DISINTEGRATE/DISRUPT branches cast the Item target to MOB to find a room.

**Effect.** A vetoed attack can spend ammunition or shield charge. If the item-target damage branch is reached, the cast throws. End-to-end reachability of that special mode was not established.

**Proposed repair.** Separate validation, resource reservation and committed resolution. Resolve item location through the map API instead of assuming a character target.

**Closure criteria.** A late veto spends nothing; an accepted attack spends once; a disintegrated item in a room, container or inventory follows the chosen legal policy without a cast exception.

**Proposals:** R08.

**Code/fixtures:** [StdGun.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdGun.java) · [StdPersonalShield.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdPersonalShield.java) · [StdElecWeapon.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdElecWeapon.java).

### F15 — An undercharged energy weapon avoids normal damage scaling

**static defect · P1.**

**Evidence.** In StdElecWeapon TYP_DAMAGE, sufficient charge subtracts cost and applies successFactor to damage. Insufficient positive charge empties the store but leaves the incoming damage unchanged.

**Effect.** The last undercharged shot can be stronger than a normally powered shot. The intended weak-shot versus refusal policy is undefined.

**Proposed repair.** Choose an explicit insufficient-charge policy per weapon; apply the normal modifiers in every allowed resolution path.

**Closure criteria.** At cost-1, cost and cost+1 stored charge, damage and resource consumption match the authored policy and never reward a power shortfall.

**Proposals:** R08.

**Code/fixtures:** [StdElecWeapon.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdElecWeapon.java).

### F16 — Inertial braking removes full speed even without sufficient power

**static defect · P1.**

**Evidence.** StdShipInertialBattery initializes removedSpeed to ship.speed. Its only reduction branch requires retainingDelta>0, but the earlier guard ensures delta>=pi/2, so retainingDelta<=0. The failure branch changes stored power without limiting removedSpeed.

**Effect.** A qualifying braking activation can stop the entire ship independently of available charge. No dynamic braking fixture was run.

**Proposed repair.** Calculate affordable absorbed energy/speed under a documented arcade model, or model a bounded assist. Keep arc and direction semantics explicit.

**Closure criteria.** Zero, partial and sufficient charge produce correspondingly bounded braking, no negative stores, and consistent feedback to autopilot.

**Proposals:** R06.

**Code/fixtures:** [StdShipInertialBattery.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdShipInertialBattery.java).

### F17 — Intercept and safe-trajectory helpers do not satisfy their contracts

**runtime observation · P1.**

**Evidence.** A runner at (0,0,0), speed 1 along +X, and a chaser at (10,0,0), maximum speed 2, yields no intercept although contact is possible within ten ticks. Static checks also find canMaybeIntercept using runnerO for both start spheres, missing discriminant/horizon checks, and isSafeTrajectory comparing distance from the starting point rather than clearance from obstacles.

**Effect.** Torpedoes and tactical software depend on incorrect interception. The misleading safety helper should not become the basis for a new autopilot. A source search found no production caller of isSafeTrajectory in this baseline.

**Proposed repair.** Define one analytical relative-motion contract with radius, horizon and stationary cases; implement actual swept obstacle clearance or withdraw the unsafe API.

**Closure criteria.** Known reachable/unreachable intercepts, tangencies, equal speeds, stationary bodies, horizon expiry and translation/rotation invariance pass independent mathematical checks.

**Proposals:** R09.

**Code/fixtures:** [CoffeeDark.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CoffeeDark.java) · [GalacticMap.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/interfaces/GalacticMap.java) · [StdSmartTorpedo.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdSmartTorpedo.java) · [ShipTacticalProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/ShipTacticalProgram.java).

### F18 — Galaxy sectors overflow and space time units disagree

**static defect · P1.**

**Evidence.** getSectorName/getInSectorCoords/getSectorMap compute 2L*GalaxyRadius.dm before division. Twice the declared radius exceeds signed long. SpaceObject documents velocity per second, while moveSpaceObject/runSpace apply it per space tick without elapsed-second conversion.

**Effect.** Sector names/maps can be wrong, and a tick-duration change alters physical speed. Approximate masses and gravity are deliberate simplifications, but the unit contract is inconsistent.

**Proposed repair.** Use safe exact sector arithmetic; version sector semantics for existing coordinates. Pick distance/time units explicitly and introduce elapsed-time conversion with a compatibility mode.

**Closure criteria.** Boundary, origin, negative-coordinate and extreme-coordinate sector cases agree; moving for equal simulated seconds under two tick durations gives the same displacement in the new mode.

**Proposals:** R10.

**Code/fixtures:** [CoffeeDark.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CoffeeDark.java) · [SpaceObject.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/interfaces/SpaceObject.java).

### F19 — Ship damage has explicit unfinished subsystem and inertia work

**design limitation · P2.**

**Evidence.** GenSpaceShip still marks inertia/g-force damage during acceleration and non-collision internal-system damage as TODO. StdSpaceShip does implement gravity and some high-thrust crew messaging/damage, so these are incomplete pathways, not a total absence of g-force effects.

**Effect.** Creators cannot reliably author targeted reactor/engine failures, localized breaches or a consistent acceleration injury model using hull damage alone.

**Proposed repair.** Define a damage pipeline with optional compartment/subsystem routing and shared crew acceleration events. Reuse existing combat and hull behavior as the simple mode.

**Closure criteria.** A chosen subsystem hit changes that subsystem, damage-control actions recover it, and hull/crew effects are ordered and saved without duplicate kill/loot credit.

**Proposals:** S04, S05.

**Code/fixtures:** [GenSpaceShip.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/GenSpaceShip.java) · [StdSpaceShip.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Areas/StdSpaceShip.java).

### F20 — Ship identity changes do not consistently rebuild circuits and caches

**static defect · P1.**

**Evidence.** GenSpaceShip.rename calls unregisterElectronics(null, oldKey), but GroundWired unregisters only a non-null component. GenSpaceShip.getShipSensors caches its first list without an invalidation path in that class. StdElecCompItem registers room-owned components before an installation requirement is enforced.

**Effect.** A rename or refit can leave stale topology/contact lists, and merely dropping a component may register it. Actual post-rename failures depend on later re-registration; that full lifecycle was not played.

**Proposed repair.** Use persistent vessel/component IDs, distinct installed membership, and topology generation numbers. Rebuild caches on rename, move, install/remove, copy and restore.

**Closure criteria.** Rename a powered vessel, replace a sensor, move a loose component and restore; registry membership remains unique and software sees the current installed equipment.

**Proposals:** R11, T02.

**Code/fixtures:** [GenSpaceShip.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/GenSpaceShip.java) · [GroundWired.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/GroundWired.java) · [StdElecCompItem.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdElecCompItem.java) · [GenShipProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/GenShipProgram.java).

### F21 — Saved physical motion is ahead of saved software activity state

**runtime observation · P1.**

**Evidence.** A ShipNavProgram with courseSet=true returns courseSet=false after text()/setMiscText round trip. navTrack/course/courseTarget are ordinary runtime fields; no state serialization for them was found in that class or the inspected save adapters. WorldState separately saves NavigableItem travel, which is not this software state.

**Effect.** The engine preserves physical space objects but has no demonstrated resumable ship-autopilot contract. This narrow probe is not a fresh-process campaign restore test and does not prove every program field is lost.

**Proposed repair.** Define persisted navigation/activity state with stable target/component IDs and relative deadlines; either resume validated tracks or restore to a visible safe hold.

**Closure criteria.** Fresh-process saves during launch, approach, orbit and landing preserve or safely suspend intended behavior, with one active job and explicit recovery for missing targets.

**Proposals:** R12, Q03.

**Code/fixtures:** [ShipNavProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/ShipNavProgram.java) · [GenSoftware.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/GenSoftware.java) · [WorldState.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldState.java).

### F22 — Save warnings still say all space objects are lost

**runtime observation · P2.**

**Evidence.** An asteroid is returned by WorldFiles.spaceItems while WorldState.notKept simultaneously warns that things in space are not saved where they are. Help repeats the old limitation despite SPACE capture/restore and space.cmare.

**Effect.** SAVES CHECK undermines a feature already implemented, making genuine omissions harder to identify.

**Proposed repair.** Generate diagnostics from the actual save coverage rules and update help. Keep precise warnings for remaining unsupported state.

**Closure criteria.** A supported unowned asteroid has no blanket loss warning; an intentionally unsupported object/activity reports its exact missing state.

**Proposals:** R12, Q01.

**Code/fixtures:** [WorldFiles.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldFiles.java) · [WorldState.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldState.java) · [help.ini](../../../CoffeeMud/resources/help/help.ini).

### F23 — Several technology categories have no concrete implementation

**integration gap · P2.**

**Evidence.** PERSONAL_TRACTOR, PERSONAL_ATMOSUIT, SHIP_TRACTOR, SHIP_REPLICATOR and COMP_PROBE appear in Technical.TechType. Scoped source searches found enum/panel/filter references but no concrete device returning these categories in the technology item families.

**Effect.** An editor category can suggest a working tractor, suit, replicator or exploration probe when it is only a vocabulary slot. Other existing properties can approximate parts of these concepts.

**Proposed repair.** Mark unsupported categories in creator documentation and validators; implement each only as a selected proposal with its own actual behavior.

**Closure criteria.** The capability reference distinguishes category-only entries, and newly selected devices have use, power, failure, save and script contracts.

**Proposals:** T04, S07, S08, W05, Q01.

**Code/fixtures:** [Technical.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/Technical.java) · [ElecPanel.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/ElecPanel.java) · [GenShipProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/GenShipProgram.java).

### F24 — Manufacturers and technology progress are installation-wide assets

**integration gap · P1.**

**Evidence.** GroundWired reads/writes /resources/tech/manufacturers.xml (falling back to examples) and stores global technology values through Resources property data. No world-owned manufacturer/technology manifest or dedicated campaign-state adapter was found in WorldFiles/WorldState/WorldSettings.

**Effect.** Two worlds sharing one engine installation cannot cleanly ship different manufacturer rules. Runtime research/progress would have no clear world-versus-save ownership boundary.

**Proposed repair.** Add world asset resolution for manufacturer/technology definitions; keep immutable world eras distinct from saved campaign discoveries. Migrate legacy resources with explicit precedence.

**Closure criteria.** Two worlds with the same manufacturer ID load different definitions without cross-contamination; restoring one campaign restores its discoveries without changing another world.

**Proposals:** T01, T03, W01.

**Code/fixtures:** [GroundWired.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/GroundWired.java) · [WorldFiles.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldFiles.java) · [WorldState.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldState.java) · [WorldSettings.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldSettings.java).

### F25 — The inherited telnet program connects to real hosts

**design limitation · P2.**

**Evidence.** ShipTelnetProgram implements ArchonOnly and opens a real java.net.Socket in checkActivate. It is not a simulated world network. The connect occurs before setSoTimeout, which is a read timeout rather than a connect limit.

**Effect.** This leftover does not provide terminals, hacking, radio traffic or a saved local network for offline worlds. Its restricted tool status should not be confused with ordinary player gameplay.

**Proposed repair.** Keep legacy tooling explicitly separate; create an entirely simulated, game-clock network for content. Do not repurpose real sockets as fictional ship communications.

**Closure criteria.** Player terminal/radio interactions run offline, persist in saves, respect discovered nodes and access rights, and never require an external connection.

**Proposals:** T07, T08.

**Code/fixtures:** [ShipTelnetProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/ShipTelnetProgram.java) · [StdProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/StdProgram.java).

### F26 — Existing tests do not establish an integrated sci-fi play loop

**test gap · P1.**

**Evidence.** The S24 station and cutter fixtures both instantiate Asteroid, rename them, and verify position/count restoration. They do not exercise GenSpaceShip interiors, crew, power or software. Spaceangles was rerun and reproduced OF Test #5 failed: 360.0,0.0; Spaceinterception/2 are recorded baseline failures but were not rerun in this audit.

**Effect.** Passing asset/save fixtures does not prove that a powered, crewed ship survives a complete campaign cycle. Existing math failures need repair rather than permanent acceptance for new sci-fi work.

**Proposed repair.** Add focused integration fixtures and a small playable technology museum after repairs; preserve baseline labels until each failure has an independently justified fix.

**Closure criteria.** A clean-start station-to-shuttle scenario covers editing, publishing, actual play, pause/turns, save/restore and a second world; all advertised sci-fi paths have relevant tests.

**Proposals:** Q02, Q03, Q04.

**Code/fixtures:** [s24-build.js](../../../tools/fixtures/fixes/setup/s24-build.js) · [s24-where.js](../../../tools/fixtures/fixes/setup/s24-where.js) · [engine-tests.sh](../../../tools/engine-tests.sh) · [test-all.txt](../../../tools/baseline/test-all.txt) · [Spaceangles.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Tests/Spaceangles.java).

### F27 — Portable electronics can drain while inactive and go negative

**static defect · P2.**

**Evidence.** StdTriCorder.tick decrements its power cycle outside the activated/owned-by-MOB gate. StdElecItem.setPowerRemaining stores any long without clamping. An inactive still-ticking tricorder can therefore be decremented below zero. Tick-registration lifetime was not dynamically traced.

**Effect.** A discarded or switched-off device can accumulate negative charge. Saved invalid values complicate recharging and balance.

**Proposed repair.** Specify idle versus active draw, gate drain accordingly, and centralize store invariants with deliberate migration of old values.

**Closure criteria.** Inactive and active portable devices behave as documented across 100 pulses, pause, time jumps and restore; stores remain within their allowed bounds.

**Proposals:** R06, T04.

**Code/fixtures:** [StdTriCorder.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdTriCorder.java) · [StdElecItem.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdElecItem.java).

### F28 — Thruster wear and failure feedback do not match their apparent intent

**static defect · P2.**

**Evidence.** StdShipThruster multiplies usesRemaining by 100 when forming a wear fraction, so ordinary positive wear values bypass its reduction. Its no-thrust/non-aft power path reports insufficient_fuel even when that condition does not establish fuel exhaustion.

**Effect.** Damaged equipment does not degrade as expected and maintenance/flight software receives misleading failure reasons. Fuel exhaustion also has separate shutdown handling.

**Proposed repair.** Use a documented wear curve and structured reason codes; distinguish stopped, unsupported port, uninstalled, unpowered and out-of-fuel states.

**Closure criteria.** A 25%-condition engine follows its selected curve; a lateral maneuver with fuel does not report fuel exhaustion; diagnostics and scripts receive the actual reason.

**Proposals:** R04, T05, Q06.

**Code/fixtures:** [StdShipThruster.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdShipThruster.java) · [StdCompFuelConsumer.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdCompFuelConsumer.java).
