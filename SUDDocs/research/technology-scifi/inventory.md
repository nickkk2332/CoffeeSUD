# Current technology and science-fiction capabilities

[Start](README.md) · [Findings](audit.md) · [Proposals](proposals.md)

This inventory groups 34 systems. It distinguishes a working primitive from a
finished gameplay loop. The scoped item/ability families contain 106 Java source
files: 42 BasicTech, 49 CompTech, 13 Software and two Tech abilities. Discovery
covered these families plus their interfaces, space/power libraries, ship/planet
areas, save adapters and relevant creator documentation. Focused functions were
traced and selected runtime paths probed; this is not a claim of exhaustive
line-by-line review of every source file.

- **Existing foundation:** concrete behavior suitable for reuse, with normal testing.
- **Partial:** concrete behavior with an identified defect or missing integration.
- **Approximation:** generic systems can support a recipe, without a dedicated lifecycle.
- **Category only:** a name/interface slot without a located concrete device.
- **Not a dedicated system:** reusable adjacent pieces, but no located subsystem.

Creators can already write a crashed starship, radiation ruin, laser-equipped
raider, scripted robot or abstract shuttle journey. They should not assume that
a complete suit/pressure model, drone network or factory exists merely because
the fiction can be represented. Ships and space objects are supported assets,
but powering, controlling, damaging and restoring a whole ship need the repairs
and integration checks below.

## Capability map

| ID | System | Current status | Proposed next work |
| --- | --- | --- | --- |
| V01 | [Genres, eras and mixed settings](#v01) | existing foundation | T01, C12 |
| V02 | [Technical identity and generic device fields](#v02) | partial | R01, T01 |
| V03 | [Manufacturers and technology levels](#v03) | partial | R07, T01, W01, W12 |
| V04 | [Firearms and energy weapons](#v04) | partial | R08, C01, C02, C03 |
| V05 | [Personal shields and fields](#v05) | partial | R07, R08, C04 |
| V06 | [Tricorders and medical/life/emission scans](#v06) | partial | R02, R06, C09, W02 |
| V07 | [Electrical circuits and panels](#v07) | partial | R06, R11, T02, T03 |
| V08 | [Generation, batteries and fuel](#v08) | existing foundation | R01, R07, T05, T06 |
| V09 | [Installation, repair and enhancement](#v09) | existing foundation | R11, T05, W04 |
| V10 | [Space bodies, planets and the galaxy](#v10) | partial | R10, S01, S09 |
| V11 | [Movement, gravity, collision and interception](#v11) | partial | R09, R10, S02 |
| V12 | [Engines, navigation and warp](#v12) | partial | R04, R12, S02, S03 |
| V13 | [Sensors, windows, viewscreens and contacts](#v13) | partial | R05, R11, S06 |
| V14 | [Consoles and software services](#v14) | partial | R03, T07, T08, T09 |
| V15 | [Ship database and diagnostics](#v15) | partial | R02, R03, Q06 |
| V16 | [Ship weapons, shields, launchers and torpedoes](#v16) | partial | R05, R06, R09, S04 |
| V17 | [Ship interiors, air and gravity](#v17) | partial | R01, T04, T10, T11, S05 |
| V18 | [Crew, companions and opposing craft](#v18) | approximation | S10, C06, W11 |
| V19 | [Space assets and campaign saves](#v19) | partial | R12, Q03 |
| V20 | [Technology authoring and validation](#v20) | partial | Q01, Q04, Q05, Q06, Q07 |
| V21 | [Genre-neutral combat and effects](#v21) | existing foundation | C03, C04, C10, C12 |
| V22 | [Robots, drones and artificial minds](#v22) | approximation | C05, C06, C07, C08 |
| V23 | [Prosthetics and cybernetics](#v23) | approximation | C07, C08 |
| V24 | [Radiation, biology and dangerous environments](#v24) | existing foundation | T10, T11, C09, W03 |
| V25 | [Crafting, salvage and gathering](#v25) | existing foundation | W04, W05, W06, W07 |
| V26 | [Factories, bases and colonies](#v26) | not a dedicated system | W06, W07, W08, W09, W10 |
| V27 | [Fictional networks and hacking](#v27) | not a dedicated system | T07, T08, T09, S11 |
| V28 | [Corporations, governments and quests](#v28) | existing foundation | W10, W11, S12 |
| V29 | [Presentation, alien languages and story](#v29) | existing foundation | C11, Q04, Q10 |
| V30 | [Travel, transports and instances](#v30) | existing foundation | S01, S02, S09, W11 |
| V31 | [Research, expertise and scientific discovery](#v31) | approximation | W01, W02, W12 |
| V32 | [Named-only device categories](#v32) | category only | T04, S07, S08, W05, Q01 |
| V33 | [Clock, random and simulation lifecycle](#v33) | partial | R10, R12, T12, Q02 |
| V34 | [Tests and a shipped sci-fi example](#v34) | partial | Q02, Q03, Q04, Q12 |

<a id="v01"></a>

## V01: Genres, eras and mixed settings

**Current.** Areas have technology/theme settings; generic races, classes, abilities and items can serve modern, science-fiction or mixed worlds.

**Boundary.** A genre flag does not configure a coherent technology ruleset or supply a shipped world.

**Findings:** no specific defect claimed.

**Sources:** [StdArea.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Areas/StdArea.java) · [Technical.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/Technical.java) · [03-characters.md](../../../SUDDocs/creator/03-characters.md).

<a id="v02"></a>

## V02: Technical identity and generic device fields

**Current.** Technical, Electronics and TechComponent expose technology level, manufacturer, activation, charge and installation; many Gen variants expose data editing.

**Boundary.** Setter bugs and overloaded legacy fields make data editing and round trips inconsistent.

**Findings:** F01, F02, F03.

**Sources:** [Technical.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/Technical.java) · [Electronics.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/Electronics.java) · [TechComponent.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/TechComponent.java).

<a id="v03"></a>

## V03: Manufacturers and technology levels

**Current.** Manufacturer XML, stock example makers, quality factors, random technology levels and creator commands already exist.

**Boundary.** Assets are installation-wide; quality semantics disagree; no complete saved world research/era progression.

**Findings:** F13, F24.

**Sources:** [DefaultManufacturer.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultManufacturer.java) · [GroundWired.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/GroundWired.java) · [manufacturers.xml](../../../CoffeeMud/resources/examples/manufacturers.xml).

<a id="v04"></a>

## V04: Firearms and energy weapons

**Current.** StdGun/GenGun model ammunition and ranged attacks; energy families include lasers, blasters, phasers, disruptors and sonic guns with selectable modes.

**Boundary.** No dedicated general magazine/fire-mode/heat/attachment suite; charge and validation paths have defects.

**Findings:** F14, F15.

**Sources:** [StdGun.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdGun.java) · [StdElecWeapon.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdElecWeapon.java) · [GenPhaser.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/GenPhaser.java).

<a id="v05"></a>

## V05: Personal shields and fields

**Current.** Multiple shield variants offer energy, kinetic, absorption, reflection, stealth and other field effects.

**Boundary.** Quality direction and validation-phase resource consumption need repair before promising balanced layering.

**Findings:** F13, F14.

**Sources:** [StdPersonalShield.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdPersonalShield.java) · [GenReflectionShield.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/GenReflectionShield.java) · [GenStealthShield.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/GenStealthShield.java).

<a id="v06"></a>

## V06: Tricorders and medical/life/emission scans

**Current.** Portable computers run scan disks and report health, life signs and emissions; program settings can be edited.

**Boundary.** Health percentages are wrong, biological assumptions exclude some constructs, and passive/active power needs a reliable contract.

**Findings:** F04, F27.

**Sources:** [StdTriCorder.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdTriCorder.java) · [HealthScanProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/HealthScanProgram.java) · [LifeScanProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/LifeScanProgram.java) · [EmissionScanProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/EmissionScanProgram.java).

<a id="v07"></a>

## V07: Electrical circuits and panels

**Current.** GroundWired registers components into location-derived circuits and distributes generator/battery output through panels.

**Boundary.** This is not an authored graph of cables, buses and breakers; forwarding, installation and identity need repair.

**Findings:** F12, F20.

**Sources:** [GroundWired.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/GroundWired.java) · [StdCompPanel.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdCompPanel.java) · [StdElecCompContainer.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdElecCompContainer.java).

<a id="v08"></a>

## V08: Generation, batteries and fuel

**Current.** Fueled/fuelless generators, batteries and solar, wind, water and Bussard variants provide actual generation conditions.

**Boundary.** Their formulas are simplified and quality/rate behavior is inconsistent; heat, fuel grades and reactor hazards are not a general simulation.

**Findings:** F01, F02, F13.

**Sources:** [StdCompGenerator.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdCompGenerator.java) · [GenSolarGenerator.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenSolarGenerator.java) · [GenWindGenerator.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenWindGenerator.java) · [GenWaterPowerGenerator.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenWaterPowerGenerator.java) · [GenBussardCollector.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenBussardCollector.java).

<a id="v09"></a>

## V09: Installation, repair and enhancement

**Current.** AstroEngineering supports INSTALL, REPAIR and ENHANCE, with time/proficiency and manufacturer/type expertise.

**Boundary.** It is not a shipyard/refit plan editor, a bill-of-materials machine factory or a bounded per-subsystem maintenance model.

**Findings:** F20, F28.

**Sources:** [AstroEngineering.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Tech/AstroEngineering.java) · [TechSkill.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Tech/TechSkill.java).

<a id="v10"></a>

## V10: Space bodies, planets and the galaxy

**Current.** SpaceObject, planetary areas, stars, asteroids, a spatial index and named sectors represent space coordinates and large bodies.

**Boundary.** Sector overflow and unit ambiguity remain; procedural planet surfaces and a knowledge-sensitive galaxy are not complete game loops.

**Findings:** F18.

**Sources:** [SpaceObject.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/interfaces/SpaceObject.java) · [CoffeeDark.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CoffeeDark.java) · [StdPlanet.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Areas/StdPlanet.java) · [StdThinPlanet.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Areas/StdThinPlanet.java) · [Star.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Basic/Star.java) · [Asteroid.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Basic/Asteroid.java).

<a id="v11"></a>

## V11: Movement, gravity, collision and interception

**Current.** CoffeeDark moves indexed bodies, applies approximate gravity, handles swept collision messages and provides trajectory/intercept helpers.

**Boundary.** Incorrect math and simplified sequential updates limit reliability; this is not a physically exact orbital simulator.

**Findings:** F17, F18.

**Sources:** [CoffeeDark.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CoffeeDark.java) · [GalacticMap.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/interfaces/GalacticMap.java).

<a id="v12"></a>

## V12: Engines, navigation and warp

**Current.** Reaction/fuelless thrusters, impulse/warp families and ship navigation software support launch, approach, stop, orbit, course and landing paths.

**Boundary.** Existing warp classes adjust thrust/speed ceilings; they do not define authored jump lanes, travel permissions or a safe resumable autopilot.

**Findings:** F07, F08, F21, F28.

**Sources:** [GenWarpDrive.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenWarpDrive.java) · [GenWarpEngine.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenWarpEngine.java) · [ShipNavProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/ShipNavProgram.java) · [RocketShipProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/RocketShipProgram.java).

<a id="v13"></a>

## V13: Sensors, windows, viewscreens and contacts

**Current.** Optical/gravitic and generic sensors, directional viewscreens and sensor software discover space objects and relay observations.

**Boundary.** Identity, occlusion and direction defects remain; a persistent fog-of-war/contact-confidence model is absent.

**Findings:** F09, F10, F20.

**Sources:** [StdElecCompSensor.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdElecCompSensor.java) · [GenShipOpticalSensor.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenShipOpticalSensor.java) · [GenGraviticSensor.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenGraviticSensor.java) · [GenShipViewScreen.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenShipViewScreen.java).

<a id="v14"></a>

## V14: Consoles and software services

**Current.** Computer consoles host disks, menus, typed commands, circuit peers and software service requests/replies.

**Boundary.** No general data-authored application framework, fictional network topology or access-control model for terminals.

**Findings:** F05, F25.

**Sources:** [StdComputerConsole.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdComputerConsole.java) · [StdProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/StdProgram.java) · [Software.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/Software.java).

<a id="v15"></a>

## V15: Ship database and diagnostics

**Current.** DatabaseProgram stores JSON records; ShipDiagProgram reports component condition; tactical and navigation programs exchange services.

**Boundary.** Custom fields and missing service advertisements break useful paths. Diagnostics contain hard-coded hull-power/damage-header behavior.

**Findings:** F05, F06.

**Sources:** [DatabaseProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/DatabaseProgram.java) · [ShipDiagProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/ShipDiagProgram.java).

<a id="v16"></a>

## V16: Ship weapons, shields, launchers and torpedoes

**Current.** Directional weapons/shields, component launchers, smart torpedoes and tactical software have real combat paths.

**Boundary.** Limited arcs can throw; guidance, power conservation and subsystem damage need completion. No full crewed battle was tested here.

**Findings:** F11, F16, F17, F19.

**Sources:** [StdShipWeapon.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdShipWeapon.java) · [StdShipShieldGenerator.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdShipShieldGenerator.java) · [StdCompLauncher.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdCompLauncher.java) · [StdSmartTorpedo.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/BasicTech/StdSmartTorpedo.java) · [ShipTacticalProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/ShipTacticalProgram.java).

<a id="v17"></a>

## V17: Ship interiors, air and gravity

**Current.** StdSpaceShip provides stale-air changes, atmosphere propagation through open exits, artificial/no-gravity flags and high-thrust effects. GenCompEnviroSystem sends AIRREFRESH.

**Boundary.** Room air resources are discrete; no continuous pressure/gas/temperature model or general sealed-suit battery model. Some inertia damage remains TODO.

**Findings:** F03, F19, F23.

**Sources:** [StdSpaceShip.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Areas/StdSpaceShip.java) · [GenCompEnviroSystem.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/GenCompEnviroSystem.java) · [StdShipGravityGen.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/CompTech/StdShipGravityGen.java) · [Breath.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/Breath.java).

<a id="v18"></a>

## V18: Crew, companions and opposing craft

**Current.** Sailor behavior, NPC combat behavior, profile parties, orders and scriptable actors supply useful crew building blocks.

**Boundary.** There is no demonstrated general space combat captain/crew-station/task-allocation system. Sailor is not evidence of complete spacecraft AI.

**Findings:** no specific defect claimed.

**Sources:** [Sailor.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Sailor.java) · [Party.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/Party.java) · [06-npcs-combat.md](../../../SUDDocs/creator/06-npcs-combat.md).

<a id="v19"></a>

## V19: Space assets and campaign saves

**Current.** WorldFiles exports space.cmare; WorldState captures/restores physical SPACE objects. Planets are areas; owned craft use ownership routes.

**Boundary.** Software activity persistence, warning accuracy and realistic crewed-ship restore coverage lag physical persistence.

**Findings:** F21, F22, F26.

**Sources:** [WorldFiles.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldFiles.java) · [WorldState.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldState.java) · [SaveGame.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/save/SaveGame.java).

<a id="v20"></a>

## V20: Technology authoring and validation

**Current.** CREATE/MODIFY, stat fields, manufacturers, LIST SPACE/SHIPS, LIST ELECTRONICS, WORLD CHECK and JS extensions are available.

**Boundary.** No integrated tech manifest validator, circuit explorer, flight simulator or concise current technology cookbook.

**Findings:** F23, F26.

**Sources:** [ListCmd.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Commands/ListCmd.java) · [CMGenEditor.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CMGenEditor.java) · [WorldCheck.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/WorldCheck.java) · [tool-reference.md](../../../SUDDocs/creator/tool-reference.md).

<a id="v21"></a>

## V21: Genre-neutral combat and effects

**Current.** Generic abilities, custom classes, races, damage/resistance properties, hazards, encounter phases and telegraphs support science-fiction content.

**Boundary.** Renaming a spell gives a useful recipe but does not add ammunition, line-of-fire, a battery or network ownership rules.

**Findings:** no specific defect claimed.

**Sources:** [GenAbility.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/GenAbility.java) · [GenCharClass.java](../../../CoffeeMud/com/planet_ink/coffee_mud/CharClasses/GenCharClass.java) · [Prop_Resistance.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Resistance.java) · [Telegraph.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/Telegraph.java) · [06-npcs-combat.md](../../../SUDDocs/creator/06-npcs-combat.md).

<a id="v22"></a>

## V22: Robots, drones and artificial minds

**Current.** Generic races/MOBs, golems, followers, orders, scripts and party limits can represent a robot companion or turret.

**Boundary.** No reusable chassis/battery/control-link/autonomy/firmware lifecycle was located; constructs are not automatically a drone platform.

**Findings:** no specific defect claimed.

**Sources:** [GenRace.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Races/GenRace.java) · [MetalGolem.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Races/MetalGolem.java) · [Party.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/Party.java) · [03-characters.md](../../../SUDDocs/creator/03-characters.md).

<a id="v23"></a>

## V23: Prosthetics and cybernetics

**Current.** FalseLimb and amputation-related systems, wearable properties and granted abilities can approximate an artificial limb or implant.

**Boundary.** No dedicated implant slots, power/heat budgets, incompatibilities, surgery/recovery or extraction lifecycle was located.

**Findings:** no specific defect claimed.

**Sources:** [FalseLimb.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/FalseLimb.java) · [Amputation.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Misc/Amputation.java) · [07-items-crafting.md](../../../SUDDocs/creator/07-items-crafting.md).

<a id="v24"></a>

## V24: Radiation, biology and dangerous environments

**Current.** Radiation sickness, diseases, poisons, material effects, hazards, breathing and custom races supply real survival mechanics.

**Boundary.** No unified radiation dose/shielding/contamination or continuous pressure/temperature exposure model; broader exposure remains deferred.

**Findings:** no specific defect claimed.

**Sources:** [Disease_RadiationSickness.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Diseases/Disease_RadiationSickness.java) · [Prop_Hazard.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Hazard.java) · [RawMaterial.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/RawMaterial.java) · [Breath.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/Breath.java).

<a id="v25"></a>

## V25: Crafting, salvage and gathering

**Current.** Recipe-driven crafting, custom materials, salvage yield, quality, learning and gathering exist.

**Boundary.** No general industrial production queue, part-quality machine blueprint or cargo-volume logistics model.

**Findings:** no specific defect claimed.

**Sources:** [SalvageYield.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/SalvageYield.java) · [CraftQuality.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/CraftQuality.java) · [WorldMaterials.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/WorldMaterials.java) · [07-items-crafting.md](../../../SUDDocs/creator/07-items-crafting.md).

<a id="v26"></a>

## V26: Factories, bases and colonies

**Current.** Housing, containers, ownership, organizations, shops, crafting and scripts provide pieces of a workshop or settlement.

**Boundary.** No complete production graph, upkeep, worker assignment or aggregate colony tick.

**Findings:** no specific defect claimed.

**Sources:** [Organizations.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/Organizations.java) · [09-society.md](../../../SUDDocs/creator/09-society.md) · [07-items-crafting.md](../../../SUDDocs/creator/07-items-crafting.md).

<a id="v27"></a>

## V27: Fictional networks and hacking

**Current.** Locks, puzzles, scripts and console disks can present a local access puzzle. ShipTelnetProgram is a restricted real-network tool.

**Boundary.** No saved topology, device credentials, intrusion trace or simulated electronic warfare system.

**Findings:** F25.

**Sources:** [ShipTelnetProgram.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/Software/ShipTelnetProgram.java) · [07-items-crafting.md](../../../SUDDocs/creator/07-items-crafting.md).

<a id="v28"></a>

## V28: Corporations, governments and quests

**Current.** Factions, organizations, laws, reputation, journals, quest boards and rewards can represent corporations, fleets and mission control.

**Boundary.** Not a complete corporate economy, sector diplomacy or autonomous faction fleet simulator.

**Findings:** no specific defect claimed.

**Sources:** [Organizations.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/Organizations.java) · [08-quests.md](../../../SUDDocs/creator/08-quests.md) · [09-society.md](../../../SUDDocs/creator/09-society.md).

<a id="v29"></a>

## V29: Presentation, alien languages and story

**Current.** Custom vocabulary/languages, world help, contextual descriptions, UI presentation and scripting support alien and technological settings.

**Boundary.** Executions are a separate unimplemented design. Native mission voiceovers or terminal schemas are not implied by generic text hooks.

**Findings:** no specific defect claimed.

**Sources:** [Presentation.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/Presentation.java) · [Languages.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/Languages.java) · [LanguageLexicon.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/LanguageLexicon.java) · [11-presentation-language.md](../../../SUDDocs/creator/11-presentation-language.md) · [feature-index.md](../../../SUDDocs/creator/feature-index.md).

<a id="v30"></a>

## V30: Travel, transports and instances

**Current.** Journeys, transport lines, vehicles, ports, waypoints and generated instances support trains, shuttles and abstract interstellar trips.

**Boundary.** Ground route graphs and abstract journeys do not solve continuous 3D spaceflight. Avoid two simultaneous movement authorities.

**Findings:** no specific defect claimed.

**Sources:** [Travel.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/Travel.java) · [10-simulation-travel.md](../../../SUDDocs/creator/10-simulation-travel.md) · [travel.md](../../../SUDDocs/travel.md).

<a id="v31"></a>

## V31: Research, expertise and scientific discovery

**Current.** Experiments, learned recipes, journal flags, skill/expertise systems and discovery rewards offer science progression pieces.

**Boundary.** No complete technology graph with per-project work, reproducible findings and component unlock contracts.

**Findings:** no specific defect claimed.

**Sources:** [03-characters.md](../../../SUDDocs/creator/03-characters.md) · [07-items-crafting.md](../../../SUDDocs/creator/07-items-crafting.md) · [AstroEngineering.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Tech/AstroEngineering.java).

<a id="v32"></a>

## V32: Named-only device categories

**Current.** Technical.TechType reserves personal tractor/atmosuit, ship tractor/replicator and launchable probe categories.

**Boundary.** The presence of the category is not a working item or player feature. Dedicated implementations were not found in the scoped families.

**Findings:** F23.

**Sources:** [Technical.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/Technical.java) · [ElecPanel.java](../../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/ElecPanel.java).

<a id="v33"></a>

## V33: Clock, random and simulation lifecycle

**Current.** GameTime, the single-player tick model, seeded dice, pause, turns and existing save adapters are available.

**Boundary.** Exact deterministic replay remains deferred; sector/velocity units and new activity catch-up policies still need explicit contracts.

**Findings:** F18, F21.

**Sources:** [GameTime.java](../../../CoffeeMud/com/planet_ink/coffee_mud/core/GameTime.java) · [10-simulation-travel.md](../../../SUDDocs/creator/10-simulation-travel.md) · [13-testing-release.md](../../../SUDDocs/creator/13-testing-release.md).

<a id="v34"></a>

## V34: Tests and a shipped sci-fi example

**Current.** Space math tests and physical space export/restore fixtures exist; broad authoring museum examples cover adjacent generic systems.

**Boundary.** The S24 craft are Asteroid objects. No integrated powered/crewed ship museum is established by those tests.

**Findings:** F26.

**Sources:** [s24-build.js](../../../tools/fixtures/fixes/setup/s24-build.js) · [s24-where.js](../../../tools/fixtures/fixes/setup/s24-where.js) · [README.md](../../../SUDDocs/creator/examples/README.md).
