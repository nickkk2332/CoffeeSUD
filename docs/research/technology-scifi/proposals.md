# Technology and science-fiction proposal catalog

[Start](README.md) · [Current capabilities](inventory.md) · [Findings](audit.md)
· [Architecture](architecture.md) · [Design cookbook](cookbook.md)

All 72 entries are proposals, not supported APIs. The CSV contains the same
content for filtering. R = repairs, T = shared technology infrastructure,
S = ships/space, C = characters/combat, W = science/industry/world, Q = creator
tools/verification. Each family has twelve entries.

**Kinds:** repair fixes existing behavior; extend builds on a current primitive;
new adds a dedicated subsystem; recipe packages existing authoring facilities
(and explicitly identified dependencies) into examples. None has been implemented
by this dossier. Dependencies are hard prerequisites within this catalog; a world
can skip any leaf feature. Shared-primitive overlaps with the earlier ARPG/MUD
proposals are integration guidance, not claims those proposals already exist.

**Phases:** 0 repair/verify existing foundations; 1 world assets and a small usable
tech kit; 2 selected advanced mechanics; 3 optional large campaign simulation.
This is an ordering, not a release promise. S/M/L/XL indicate relative engineering
scope: S is localized, M spans several classes/tools, L spans a subsystem and
its persistence/authoring, XL adds major simulation/integration. No calendar
estimate is justified before implementation spikes. Scope includes tests/docs.

**Acceptance criteria are proposed tests.** They have not passed unless the
audit explicitly records a current observation. The listed authoring controls
are design requirements, not recognized INI keys, commands or schema fields.

## Selection guide

| Goal | Starting selection | Expand when useful |
| --- | --- | --- |
| Stabilize inherited technology | R01–R12, Q02–Q03 | Q01, Q06, Q09 |
| Post-apocalyptic/scavenger world | T01, T04, C01, C04, Q04 | T05–T06, W03–W04 |
| Cyberpunk infiltration | T01, T04, T07, T09 | T08, C05–C09 |
| Space exploration | S01–S03, Q04 | S06–S09, W01–W02 |
| Crew and ship combat | S02, S04–S06 | S10–S12, C06 |
| Industry/base campaign | T02–T03, T12, W04, W07 | W05–W10 |
| Large political/colony simulation | W08–W10 | W09, W11, Q11 |

Every selection includes the transitive dependencies of its entries. These
capabilities are engine options rather than a requirement for ordinary worlds.

## Catalog

## Repair existing systems

### R01 — Repair generic component editing and type identity

**repair · S · phase 0 · depends on Q02.**

**Current foundation.** V02: generic component stat codes and manufacturer/panel categories.

**Change.** Correct POWERREM setters, fractional RECHRATE parsing, gravity generator classification and equality. Audit corresponding getters and sibling stat-code branches.

**Creator controls.** Keep current stat names as compatibility aliases; show exact units, ranges and unsupported enum choices in editors.

**Smallest useful version.** Repair affected classes and publish a compatibility note for gravity panels authored against the previous wrong type.

**Acceptance.** Independent edits and XML round trips retain charge/capacity/rate; identical gravity generators compare equal and install into a gravity panel.

**Tradeoff.** Automatic clamping can discard legacy values; report and migrate malformed objects deliberately.

**Audit findings:** F01, F02, F03.

### R02 — Repair medical scans and database rendering

**repair · S · phase 0 · depends on Q02.**

**Current foundation.** V06/V15: HealthScanProgram and DatabaseProgram.

**Change.** Use guarded fractional HP calculations and render each custom database field from its own value. Define missing/null metadata output.

**Creator controls.** Preserve creator field labels and world text hooks; avoid forced lowercasing of proper names when revising the display contract.

**Smallest useful version.** A health readout and a database entry with two custom fields and no NOTE both work.

**Acceptance.** Health boundaries and zero maximum do not throw; custom fields render once, survive serialization and do not duplicate NOTE.

**Tradeoff.** Display corrections can change transcripts; test semantic content rather than full terminal formatting.

**Audit findings:** F04, F06.

### R03 — Complete software service discovery

**repair · M · phase 0 · depends on Q02.**

**Current foundation.** V14/V15: existing service discovery and COORDQUERY handlers.

**Change.** Advertise supported coordinate services, define request/reply schemas, and reject stale or wrong-circuit service responses.

**Creator controls.** Declare offered, required and optional services in program metadata with versioned payloads.

**Smallest useful version.** Navigation resolves a known chart beacon through an installed database disk, with clear failure when the service is absent.

**Acceptance.** Two ships with different beacons do not share results; unknown or malformed queries fail without changing an existing course.

**Tradeoff.** Do not silently give a scanner omniscient access to every physical space object.

**Audit findings:** F05.

### R04 — Finish navigation validation and engine feedback

**repair · L · phase 0 · depends on R03, R06, R09.**

**Current foundation.** V09/V12: AstroEngineering, engine profiles and ShipNavProgram.

**Change.** Separate engine profile arrays, report turning capability correctly, validate landing destinations before constructing tracks, and distinguish engine failure reasons.

**Creator controls.** Expose legal maneuvers, minimum condition, fuel policy and safe-hold behavior; reserve detailed flight tuning for advanced worlds.

**Smallest useful version.** Launch, approach, stop and land a two-engine shuttle, with a safe refusal at an unlandable body.

**Acceptance.** Profiling leaves fuel/thrust unchanged; unavailable feedback is failure; low-condition and no-fuel states produce distinct messages; the no-pad path never throws.

**Tradeoff.** Existing autonomous maneuvers need scenario tests; mathematical unit tests alone cannot certify a landing.

**Audit findings:** F07, F08, F28.

### R05 — Repair contacts, optical geometry and directional coverage

**repair · M · phase 0 · depends on Q02.**

**Current foundation.** V13/V16: sensor converters, directional sets and shield/weapon arcs.

**Change.** Capture actual sensed contacts, use floating angular sizes, send every requested scan direction and centralize bounds-safe directional coverage.

**Creator controls.** Validate permitted directions/counts and invalidate cached facing/coverage immediately when a builder edits them.

**Smallest useful version.** A viewscreen distinguishes two bodies and a three-direction shield/scan works through every central arc.

**Acceptance.** Coincident objects do not divide by zero; small occluders do not hide larger apparent targets; all legal center/count pairs avoid exceptions and duplicates.

**Tradeoff.** Detection uncertainty is a later feature; repairs must not introduce accidental knowledge leaks.

**Audit findings:** F09, F10, F11.

### R06 — Repair power conservation, inertial assistance and portable draw

**repair · L · phase 0 · depends on Q02.**

**Current foundation.** V07/V08/V17: GroundWired, panel forwarding, inertial batteries and tricorders.

**Change.** Establish conserved allocation through panels, bounded stores, affordable inertial assistance and explicit inactive-device draw.

**Creator controls.** Keep simple location-based circuits as the legacy mode; declare storage and loss separately from forwarding.

**Smallest useful version.** A generator, battery, nested panels and two loads obey an inspectable energy ledger; a powerless inertial unit cannot stop a ship.

**Acceptance.** Supply equals storage change plus useful draw plus declared loss; insufficient power is applied once; inactive devices follow their documented policy and never accumulate negative charge.

**Tradeoff.** Changing accounting may alter old balance; capture representative legacy circuits before migration.

**Audit findings:** F12, F16, F27.

### R07 — Version manufacturer quality and efficiency semantics

**repair · M · phase 0 · depends on Q02.**

**Current foundation.** V03/V05/V08: manufacturer factors and their consumers.

**Change.** Give reliability, output quality, energy cost and installation quality separate documented meanings, replacing contradictory multiplier conventions.

**Creator controls.** Select legacy versus consistent quality formulas per imported technology profile; display factors with their direction and units.

**Smallest useful version.** A small manufacturer matrix covers one generator, gun, personal shield, engine and battery.

**Acceptance.** Improving a factor cannot worsen the associated result in the new mode; probability and efficiency boundaries are finite and bounded; legacy mode preserves documented old calculations.

**Tradeoff.** This is a balance migration, not a universal multiplier flip. Do not rewrite creator data without a preview.

**Audit findings:** F13.

### R08 — Make technological combat resolution transactional

**repair · L · phase 0 · depends on R07.**

**Current foundation.** V04/V05/V16: gun, energy-weapon and shield message handling.

**Change.** Move ammunition/power consumption to committed attack resolution, repair item-target mode casts and define insufficient-charge behavior.

**Creator controls.** Choose refuse, proportional shot or explicit emergency-shot behavior per weapon; define shield order and blocked-hit cost.

**Smallest useful version.** One gun, one energy weapon and one shield resolve accepted and late-vetoed attacks correctly.

**Acceptance.** A veto consumes nothing; an accepted attack consumes once; partial charge never bypasses modifiers; item targets and simultaneous shield layers avoid duplicate effects.

**Tradeoff.** Other combat listeners rely on CMMsg order; central reservation/commit hooks are safer than copying fixes into every subclass.

**Audit findings:** F14, F15.

### R09 — Replace broken interception and trajectory contracts

**repair · L · phase 0 · depends on Q02.**

**Current foundation.** V11/V16: GalacticMap, tactical software and smart torpedoes.

**Change.** Solve relative-motion interception with explicit radii/time horizon and make trajectory safety actually inspect swept clearance, or withdraw the unsupported helper.

**Creator controls.** Document arcade motion assumptions and numeric tolerance; return structured no-solution reasons for diagnostics.

**Smallest useful version.** Torpedo/tactical targeting uses the corrected shared solver, with a bounded obstacle query.

**Acceptance.** Independent analytic cases cover reachable, unreachable, tangent, stationary and equal-speed cases; translation/rotation and time-horizon checks agree.

**Tradeoff.** A constant-velocity solver does not predict arbitrary future maneuvers; communicate prediction confidence rather than promising a hit.

**Audit findings:** F17.

### R10 — Repair sectors and establish versioned space units

**repair · L · phase 0 · depends on Q02.**

**Current foundation.** V10/V11/V33: SpaceObject units, sector helpers and space ticks.

**Change.** Remove long overflow from sector arithmetic and define distance, speed, acceleration and elapsed-time conversion coherently.

**Creator controls.** Provide a legacy-tick versus simulated-seconds space profile with explicit import/migration behavior; preserve authored coordinates.

**Smallest useful version.** Sector lookup/maps agree at signed boundaries; a controlled body moves the expected distance under two tick durations.

**Acceptance.** Negative/extreme coordinates are valid; equivalent elapsed seconds produce equivalent motion in the new profile; existing worlds are not silently accelerated.

**Tradeoff.** Do not replace simplified gravity/mass with real physics as part of a correctness patch.

**Audit findings:** F18.

### R11 — Stabilize vessel, component and circuit membership

**repair · M · phase 0 · depends on Q02.**

**Current foundation.** V07/V09/V13/V19: electronics registration, ship renaming and cached component lists.

**Change.** Separate persistent identity from display names and installation topology; rebuild membership/caches on rename, refit, move, copy and restore.

**Creator controls.** Assign persistent vessel/component IDs through existing identity mechanisms; expose loose versus installed state and diagnostics for duplicate IDs.

**Smallest useful version.** Rename and refit one powered ship without orphaning its sensor or console.

**Acceptance.** No duplicate/stale registry entries remain after install/remove/rename/save; two copied craft get independent IDs and contents unless explicitly shared.

**Tradeoff.** Reusing a name as a circuit key will remain a migration input only, not the new identity contract.

**Audit findings:** F20.

### R12 — Complete saved technology activity state and truthful diagnostics

**repair · L · phase 0 · depends on R11, Q03.**

**Current foundation.** V19/V33: existing physical SPACE assets, save adapters and software settings.

**Change.** Persist or safely suspend navigation/technology jobs, rebind targets by stable ID, and remove blanket space-object loss warnings already contradicted by implemented saves.

**Creator controls.** Declare resume, safe hold or cancel/refund policy per activity. Missing targets must yield a visible recoverable state.

**Smallest useful version.** Save a shuttle during approach, restore in a new process and resume or enter a documented safe hold.

**Acceptance.** Exactly one activity returns, no elapsed deadline is charged twice, missing targets do not move the ship blindly, and SAVES CHECK reports actual unsupported state.

**Tradeoff.** Physical velocity and software intent are separate; restoring a physical ship must not imply a valid flight plan.

**Audit findings:** F21, F22.

## Shared technology infrastructure

### T01 — World-owned technology profiles and capability manifests

**extend · M · phase 1 · depends on R01, R07.**

**Current foundation.** V01/V02/V03: generic tech classes, world asset resolution and manufacturer XML.

**Change.** Let a world ship device definitions, makers, era rules, enabled subsystems and technology labels independently of the engine installation.

**Creator controls.** Select modern, post-apocalyptic, cyberpunk, space-opera or custom presets; every feature remains individually selectable and existing content has a legacy profile.

**Smallest useful version.** Two scratch worlds share the engine but define the same maker ID differently, with correct resource fallback.

**Acceptance.** World precedence is inspectable, unknown schema versions fail clearly, no runtime campaign edit writes installation-wide maker files, and existing worlds load without adopting new rules.

**Tradeoff.** A theme preset is data, not a compulsory collection of classes or balance assumptions.

**Audit findings:** F23, F24.

### T02 — Authored power buses, wiring and breaker topology

**extend · L · phase 1 · depends on R06, R11, T01.**

**Current foundation.** V07: circuit registry and installed components.

**Change.** Add explicit buses, links, switches, breakers and isolated subcircuits instead of inferring every electrical relationship from room location.

**Creator controls.** Define node IDs, capacity, loss, link direction, breaker thresholds and whether cables cross room exits; provide a simple bus mode.

**Smallest useful version.** Station main bus, emergency bus and a breaker with two independently connected loads.

**Acceptance.** A cut cable isolates only downstream loads; breaker trips and rewiring survive restore; accidental cycles are either supported by a solver or rejected before play.

**Tradeoff.** Detailed circuit graphs must be bounded; worlds may choose buses without simulating individual wires.

**Audit findings:** F12, F20.

### T03 — Power priorities, brownouts and reserve automation

**extend · M · phase 1 · depends on R06, T02.**

**Current foundation.** V07/V08: allocation and battery supply.

**Change.** Make life support, propulsion, weapons and optional loads compete under authored priorities and stable brownout rules.

**Creator controls.** Set priority classes, minimum operating power, reserve floors, automatic battery/backup behavior and hysteresis to prevent rapid toggling.

**Smallest useful version.** A station sheds decorative lights before life support, then restores them once stable power returns.

**Acceptance.** Total consumption stays conserved; equal-priority fairness is deterministic within the network; a short dip does not spam transitions or bypass reserve floors.

**Tradeoff.** Priority is world policy; do not make life support universally unbeatable in deliberately harsh settings.

**Audit findings:** F12, F24.

### T04 — Personal power, sealed suits and powered armor

**extend · L · phase 1 · depends on R06, T01.**

**Current foundation.** V06/V17/V24/V32: electronics, wearable properties and breathing rules.

**Change.** Introduce rechargeable personal power ports and a concrete atmosphere-suit lifecycle, including seal, oxygen, filters, battery and powered-assist effects.

**Creator controls.** Choose which resources matter, refilling sources, incompatible gear, seal checks, warnings and fail-safe behavior; allow a simple single-charge suit.

**Smallest useful version.** A sealed suit works in vacuum and reports remaining oxygen/charge, with a safe opening/refill interaction.

**Acceptance.** Swapped cells cannot duplicate charge; unsealing matters immediately; empty oxygen differs from empty battery; party companions and saves retain their own stores.

**Tradeoff.** A suit should not require a continuous gas simulator; that is an optional T10 layer.

**Audit findings:** F23, F27.

### T05 — Heat, wear, maintenance and component faults

**extend · L · phase 2 · depends on R04, R07, T01.**

**Current foundation.** V08/V09: condition, engineering and component failures.

**Change.** Add optional heat budgets, cooling, wear curves and typed repairable faults shared by weapons, reactors, engines and implants.

**Creator controls.** Set heat capacity, dissipation, thresholds, duty cycles, maintenance intervals, fault tables and inspection/repair requirements.

**Smallest useful version.** A laser overheats and recovers; an engine with a clogged coolant loop can be diagnosed and repaired.

**Acceptance.** Heat and faults persist; offline/time-jump cooling follows declared rules; hidden faults are discoverable without exposing every internal number to the player.

**Tradeoff.** Use one thermal service for devices and environments where enabled, rather than several incompatible heat meters.

**Audit findings:** F28.

### T06 — Fuel, coolant and consumable supply systems

**extend · M · phase 2 · depends on R06, T01.**

**Current foundation.** V08/V25: fuel consumers, materials and containers.

**Change.** Model compatible fuel/coolant types, finite tanks, contamination and transfer adapters with optional simplified fuel units.

**Creator controls.** Specify accepted resource tags, grade, transfer losses, tank capacity, mixing rules and safe drain/refill interactions.

**Smallest useful version.** A generator and shuttle accept compatible fuel; an incompatible canister is rejected before transfer.

**Acceptance.** Transfers conserve quantity and metadata, interrupted actions cannot duplicate material, and saved partly filled tanks restore exactly.

**Tradeoff.** Existing mass/resource systems should supply the canonical quantity; do not introduce a second hidden inventory of the same fuel.

**Audit findings:** F13.

### T07 — Simulated local networks and device access

**new · L · phase 1 · depends on T01, R11.**

**Current foundation.** V14/V27: consoles, locks, scripting and software services.

**Change.** Create fictional nodes, links and authenticated capabilities for terminals, doors, cameras and machines; the simulation never opens real network sockets.

**Creator controls.** Declare wired/wireless links, node visibility, credential scopes, permitted operations, segmentation and ownership.

**Smallest useful version.** An offline facility terminal can unlock its authorized door and inspect a connected generator.

**Acceptance.** Disconnected nodes are unreachable; credentials do not grant arbitrary Java/JS execution; node identities and authorization survive copy/restore without leaking between worlds.

**Tradeoff.** This is game content permission, not an operating-system security product; keep trusted creator scripts separate from fictional hacking.

**Audit findings:** F25.

### T08 — Hacking, intrusion traces and countermeasures

**new · L · phase 2 · depends on T07, T12.**

**Current foundation.** V27: current lock/puzzle/skill primitives.

**Change.** Add an authored intrusion activity with discovery, access levels, traces, countermeasures and multiple resolutions including social credentials or physical bypass.

**Creator controls.** Set skill gates, action costs, alarms, partial successes, exploits, trace decay and lockout/recovery; allow puzzle or skill-check modes.

**Smallest useful version.** Hack a security node for temporary door access while a game-time trace advances.

**Acceptance.** Cancel, disconnect, combat, party switching and restore keep one intrusion state; failures trigger authored consequences and leave a recoverable route.

**Tradeoff.** Repeated attempts must not become a free reroll loop; use visible resource/time or lockout rules.

**Audit findings:** F25.

### T09 — Data-authored terminal applications and data objects

**extend · L · phase 1 · depends on R02, R03, T07.**

**Current foundation.** V14/V15/V29: software menus, JSON data, world text and presentation.

**Change.** Provide schemas for menus, logs, maps, inquiries and allowed device actions, reducing the need for a custom Java disk per application.

**Creator controls.** Declare pages, records, local variables, conditions, command aliases, discoverable data and capability-checked actions.

**Smallest useful version.** A creator authors a research terminal with three logs, one door command and a locked diagnostics page.

**Acceptance.** Bad links/actions are found at validation, pages preserve proper names and markup, and user-entered text cannot become an unrestricted runtime command.

**Tradeoff.** Data applications should use bounded expressions/actions; full JavaScript remains an explicit trusted extension path.

### T10 — Compartment atmosphere, pressure and airlocks

**extend · XL · phase 2 · depends on R12, T04, T01.**

**Current foundation.** V17/V24: discrete room atmosphere, AIRREFRESH and breathing.

**Change.** Add an optional compartment model for pressure, oxygen, contaminants and pump/vent flows, connected through doors and leaks.

**Creator controls.** Choose abstract safe/unsafe atmosphere or numeric pressure/gases; set room volume, seal behavior, vent rates, interlocks and warning text.

**Smallest useful version.** A two-door airlock cycles pressure while a breach depressurizes only connected compartments.

**Acceptance.** Gas accounting is bounded and conserved within declared loss; simultaneous doors behave consistently; time jumps do not skip fatal exposure without policy; restore preserves airlock phase.

**Tradeoff.** Continuous gas mixtures are expensive; default to a small compartment-level model, not fluid dynamics per tile.

### T11 — Radiation, contamination and environmental exposure

**extend · L · phase 2 · depends on T01, T04.**

**Current foundation.** V24: hazards, diseases, material effects and breathing.

**Change.** Offer cumulative radiation dose, shielding, contamination transfer and configurable exposure effects, alongside optional heat/cold/toxic-zone tracking.

**Creator controls.** Define dose units, source strength, attenuation, species susceptibility, treatment, decontamination and whether temperature simulation is enabled.

**Smallest useful version.** A radiation ruin rewards shielding and a dosimeter; contaminated salvage needs cleaning before storage.

**Acceptance.** Exposure and contamination are saved, source removal stops new dose, protection factors compose predictably and sealed containers block only the modes they declare.

**Tradeoff.** Broader body-temperature work overlaps deferred exposure; share that foundation instead of creating a second survival system.

### T12 — Shared technology jobs and game-clock scheduling

**extend · L · phase 1 · depends on R12.**

**Current foundation.** V33: GameTime, long waits and saved activities.

**Change.** Provide one lifecycle for refits, scans, hacks, production and research: start, progress, interrupt, suspend, resume and complete once.

**Creator controls.** Set owner scope, work units, resource reservations, interruption/refund rules and catch-up mode: exact bounded steps, analytic progress or suspended.

**Smallest useful version.** One repair and one scan use the same saved job machinery and distinct per-character ownership.

**Acceptance.** PAUSE freezes work, turns advance correctly, long WAIT follows policy, fresh restore does not duplicate completion/rewards, and abandoned jobs release reservations.

**Tradeoff.** Shared lifecycle does not mean every activity gets the same timing or interruption behavior.

## Ships, space and expeditions

### S01 — Knowledge-aware star charts and sector destinations

**extend · M · phase 1 · depends on R05, R10, T01.**

**Current foundation.** V10/V13/V30: sector math, sensors, discovery rewards and authored ports.

**Change.** Separate the physical galaxy from what a character/profile knows, with chart entries, uncertainty, navigation beacons and authored destinations.

**Creator controls.** Set discovery scope, coordinates, public/secret lanes, chart sale/sharing, sensor identification thresholds and map labels.

**Smallest useful version.** A chart shows two known stations and one uncertain contact; buying a survey adds a destination without revealing unrelated secrets.

**Acceptance.** Unknown objects stay absent from normal maps, discovery is saved at the selected scope and sector/chart coordinates agree with actual space positions.

**Tradeoff.** Maps may be text tables or small diagrams; no graphical client is required.

### S02 — Choose abstract, tactical or continuous space travel

**extend · L · phase 1 · depends on R04, R10, R12, S01.**

**Current foundation.** V11/V12/V30: journeys, ship physics and navigation.

**Change.** Expose travel modes suited to a text engine: authored route journeys, encounter-scale tactical motion, or continuous spaceflight.

**Creator controls.** Choose mode per region/journey, transition gates, encounter cadence, player steering granularity and maximum simulation work per tick.

**Smallest useful version.** A shuttle uses a route journey between stations and switches to a bounded tactical encounter at an ambush.

**Acceptance.** Exactly one authority moves the vessel; transitions preserve passengers/cargo and relative progress; pause/restore work in each mode.

**Tradeoff.** Continuous physics should be selected for gameplay value; do not require it for ordinary space-opera travel.

### S03 — Jump drives, warp routes and transit constraints

**extend · L · phase 2 · depends on S02, T06, T12.**

**Current foundation.** V12/V30: warp engines, coordinate destinations and journeys.

**Change.** Add explicit FTL transit contracts: charge-up, fuel, destination, gravity exclusion, cooldown, disruption and emergency exit.

**Creator controls.** Choose jump gates, free jumps, hyperspace lanes or continuous warp; set range, permits, mass limits, hazards and arrival dispersion.

**Smallest useful version.** A charge-up jump to a known beacon can be interrupted before departure and restores safely if saved during transit.

**Acceptance.** Fuel is reserved/consumed once, invalid destinations fail before departure, cargo/passengers stay bound to the ship and missing arrival assets invoke recovery.

**Tradeoff.** Relativity and hard-science requirements are world choices; do not quietly rewrite clocks across the campaign.

### S04 — Ship subsystems, targeted damage and damage control

**extend · XL · phase 2 · depends on R08, R12, T02, T05.**

**Current foundation.** V16/V17: hull damage, component condition, interiors and engineering.

**Change.** Route damage through configurable shields, armor, hull, compartments and subsystems; support disabled engines, reactor faults, boarding breaches and repair tasks.

**Creator controls.** Declare hit locations, penetration, linked components, critical tables, fire/leak effects, kill attribution and repair access.

**Smallest useful version.** An engine hit disables propulsion while leaving crew alive; an engineer isolates and repairs the damaged bus.

**Acceptance.** Damage is applied once, destroyed parts stop operating, targeting cannot reveal undiscovered internals, repairs persist and hull death awards loot/quest credit once.

**Tradeoff.** Keep a hull-only mode for small craft and worlds that do not want damage-control micromanagement.

**Audit findings:** F19.

### S05 — Zero gravity, acceleration and crew safety

**extend · L · phase 2 · depends on R01, R06, R12, T04.**

**Current foundation.** V17: gravity flags, GravityFloat and existing high-thrust effects.

**Change.** Unify artificial gravity, acceleration injury, inertial protection and zero-gravity movement under one selectable crew model.

**Creator controls.** Set gravity regimes, restraints, magnetic boots, maneuver costs, recoil/knockback, g-force tolerance and cinematic versus harsh injuries.

**Smallest useful version.** Loss of gravity changes movement; strapped-in crew with a powered dampener survive a burn better than unprotected crew.

**Acceptance.** Each maneuver generates one crew-acceleration event, species/gear protection follows declared order and restore during a gravity transition does not reapply damage.

**Tradeoff.** Zero gravity should change decisions without requiring vector input for every room movement.

**Audit findings:** F19.

### S06 — Contacts, stealth, signatures and electronic warfare

**extend · L · phase 2 · depends on R05, S01, T07.**

**Current foundation.** V13/V16: sensors, emissions, stealth fields and targeting.

**Change.** Introduce contact confidence, active/passive sensors, signature sources, jamming, decoys and target locks with saved expiry.

**Creator controls.** Set sensor bands, ranges, occlusion rules, emissions, lock thresholds, scan costs, spoofing and what observations reveal.

**Smallest useful version.** A silent-running ship is an uncertain passive contact; an active scan identifies it while exposing the scanning ship.

**Acceptance.** Targeting requires sufficient knowledge, jamming affects declared bands, decoys have bounded lifetimes and lost contacts expire consistently through save/time jumps.

**Tradeoff.** Avoid one universal stealth score; keep a few readable bands and explain uncertainty in text.

### S07 — Tractor beams, towing and salvage capture

**new · L · phase 2 · depends on R06, R09, R11, S02.**

**Current foundation.** V16/V32: directional components, space bodies and category slots.

**Change.** Implement real tractor/tow links with range, force, mass ratio, line-of-sight, resistance and power consumption.

**Creator controls.** Set target classes, towing speed, mass limits, break conditions, capture restrictions and delivery/ownership rules.

**Smallest useful version.** A tug captures a disabled cargo pod and tows it to a station without teleporting its inventory.

**Acceptance.** Cycles and self-towing are rejected, both bodies follow one movement authority, disconnected links stop exerting force and captured ownership persists without duplicating salvage.

**Tradeoff.** Start with coupled route/tactical movement before attempting a detailed continuous-force tow solver.

**Audit findings:** F23.

### S08 — Exploration probes, beacons, satellites and buoys

**new · M · phase 2 · depends on R11, R12, S01, T12.**

**Current foundation.** V13/V16/V32: sensors, launchers, space bodies and probe category.

**Change.** Add non-weapon deployables that survey, relay, mark, watch or recover data, with finite fuel/lifetime and optional retrieval.

**Creator controls.** Declare launch compatibility, sensor payloads, communication links, autonomy, detection, recall and who owns discoveries.

**Smallest useful version.** Launch a survey probe that reports a distant anomaly after game-time travel and stores data if its link drops.

**Acceptance.** Deployment removes one payload, reports cannot duplicate rewards, lost links preserve local data and retrieval/destruction/restore yield one consistent object.

**Tradeoff.** Share drone control/lifecycle primitives with C05; a probe need not be a full combat party character.

**Audit findings:** F23.

### S09 — Stations, planets and generated expedition sites

**extend · L · phase 2 · depends on S01, S02, Q04.**

**Current foundation.** V10/V19/V30: boardable interiors, procedural areas and instances.

**Change.** Provide authored station/planet templates that bind physical destinations to stable interiors, surface landing sites and optional seeded expeditions.

**Creator controls.** Set climate, biome, atmosphere, resources, settlement seeds, landing pads, instance scope and return/recovery destinations.

**Smallest useful version.** A survey finds a moon with one permanent outpost and one seeded ruin expedition reached through a landing site.

**Acceptance.** Travel/save links remain stable, generated content uses recorded seeds, companions left behind retain access policy and destroyed landing craft cannot strand a campaign without the chosen recovery path.

**Tradeoff.** A planet does not need millions of rooms; abstract uninteresting transit and generate bounded points of interest.

### S10 — Crew stations, autopilots and opposing ship AI

**extend · XL · phase 2 · depends on R04, S02, S04, C05.**

**Current foundation.** V18: Sailor, combat AI, parties and scriptable NPCs.

**Change.** Add roles for pilot, engineer, sensors, weapons and damage control, with task priorities and reusable opponent flight tactics.

**Creator controls.** Choose manual or automatic roles, crew qualifications, ship standing orders, retreat thresholds, targeting rules and command authority.

**Smallest useful version.** A three-person crew handles steering and repairs while a pirate craft approaches, threatens, attacks and retreats at a threshold.

**Acceptance.** One actor owns each control action, absent/dead crew are handled, orders survive switching, AI work stays bounded and all ships obey the same resource constraints.

**Tradeoff.** Small ships can use one abstract crew rating; rich individual crew should be an optional layer.

### S11 — Boarding, docking and physical/digital infiltration

**extend · L · phase 2 · depends on R11, S04, T07, T10.**

**Current foundation.** V17/V27/V30: exits, interiors, locks, travel and simulated networks.

**Change.** Model docking connectors, boarding links, airlock compatibility and infiltration that can disable a target through physical or network access.

**Creator controls.** Define consensual/hostile docking rules, velocity/mass limits, seals, breach tools, access credentials and separation recovery.

**Smallest useful version.** Dock two ships, cross an airlock, disable a security node and return before undocking.

**Acceptance.** Connector state is symmetric, unsafe undocking handles occupants/items explicitly, access grants do not bypass atmosphere and restore rebuilds one valid connection.

**Tradeoff.** Do not model boarding as an untracked temporary exit that can survive one endpoint being destroyed.

### S12 — Trade, rescue, patrol and exploration mission templates

**extend · M · phase 2 · depends on S02, S08, W10.**

**Current foundation.** V28/V30: quests, journals, organizations and journeys.

**Change.** Offer reusable mission patterns for cargo delivery, escorts, distress rescue, recovery, bounty interception and scientific survey.

**Creator controls.** Set employer, legal authority, route, cargo identity, deadlines, evidence, optional objectives and partial/failure outcomes.

**Smallest useful version.** A rescue contract accepts living survivors or recovered flight data with different outcomes, without requiring every encounter to be lethal.

**Acceptance.** Mission cargo and survivors have stable identity, alternative outcomes are exclusive where intended, deadlines use game time and save/replay cannot duplicate rewards.

**Tradeoff.** Mission generators should compose tested objectives; a new quest engine is unnecessary.

## Characters, combat and bodies

### C01 — Magazines, reloads and authored firing modes

**extend · L · phase 1 · depends on R08, T01.**

**Current foundation.** V04: StdGun, ammunition and ranged combat.

**Change.** Add explicit magazine/round state, reload actions and semi/automatic/burst modes with bounded ammunition consumption.

**Creator controls.** Set caliber/resource tags, magazine capacity, reload time, chamber policy, mode costs, reliability and compatible weapons.

**Smallest useful version.** A pistol and burst rifle use removable partially filled magazines and interruptible reloads.

**Acceptance.** Rounds are conserved across split/load/unload/save; canceled reloads leave a valid state; a three-round burst consumes exactly the rounds actually fired.

**Tradeoff.** Do not encode new magazine state in overloaded armor/readableText bits; add explicit serialized fields with legacy adapters.

### C02 — Weapon attachments, gadgets and mod compatibility

**extend · M · phase 2 · depends on C01, T01, T05.**

**Current foundation.** V04/V25: item properties, recipes and quality.

**Change.** Support scopes, suppressors, barrels, battery packs and tool modules through declared sockets and compatibility rules.

**Creator controls.** Define attachment slots/tags, mutually exclusive parts, stat effects, power/heat tradeoffs and install/remove requirements.

**Smallest useful version.** A scope improves selected aiming behavior while a suppressor changes signature and heat rather than simply adding damage.

**Acceptance.** Invalid combinations fail before consuming parts, detached mods retain identity/condition, saves preserve the installed graph and no item can contain itself through nested slots.

**Tradeoff.** Share item-mod primitives with the ARPG proposal set rather than introduce a second socket framework.

### C03 — Ranged tactics, cover and readable line of fire

**extend · L · phase 2 · depends on C01, R05.**

**Current foundation.** V21: room/exits, ranged abilities, telegraphs and encounter logic.

**Change.** Offer optional room-scale cover, suppression, aimed shots and line-of-fire rules appropriate to text combat.

**Creator controls.** Set cover positions, occupancy, destructibility, penetration, exposure, aiming costs and whether adjacent rooms can exchange fire.

**Smallest useful version.** Two cover positions in one room allow the party to flank a suppressing turret.

**Acceptance.** Shots obey the selected visibility/cover path, destroyed cover stops protecting, AI uses the same rules and effects never silently fire through closed exits.

**Tradeoff.** Start with named room positions; a spatial tile grid is a separate major engine choice, not an implied requirement.

### C04 — Damage tags, armor and shield interaction profiles

**extend · M · phase 1 · depends on R07, R08, T01.**

**Current foundation.** V05/V21: existing weapon types, resistances and personal fields.

**Change.** Provide data-authored kinetic, energy, EMP, chemical, radiation and exotic interactions using existing damage where possible and explicit additional tags where necessary.

**Creator controls.** Define mitigation order, resist/penetration curves, shield recharge/delay, bypass rules and immunities with documented caps.

**Smallest useful version.** One armor and shield profile differentiates ballistic, laser and EMP attacks while ordinary fantasy damage remains compatible.

**Acceptance.** Tags survive all relevant combat paths; caps and rounding are consistent; adding a tag does not bypass death/quest hooks or make every non-tech target immune.

**Tradeoff.** Avoid a parallel combat engine. New tags should extend shared damage events also needed by the ARPG designs.

### C05 — Robotic chassis, drones and saved autonomy

**new · L · phase 1 · depends on R11, R12, T04.**

**Current foundation.** V22: generic MOB/race definitions, followers and party controls.

**Change.** Create a robot/drone lifecycle with chassis slots, battery, payload, ownership, local orders and bounded autonomous tasks.

**Creator controls.** Choose full character versus lightweight agent, slot limits, maintenance, allowed tasks, recovery behavior and death/destruction rules.

**Smallest useful version.** A maintenance drone follows, repairs a designated device, returns to charge and persists when the player switches characters.

**Acceptance.** Party/drone caps are enforced, an agent has one owner/controller, batteries and inventory remain conserved and restore creates one instance with the correct order.

**Tradeoff.** Lightweight drones should not require a character sheet or consume unlimited profile members.

### C06 — Remote control, telepresence and signal loss

**new · L · phase 2 · depends on C05, T07.**

**Current foundation.** V18/V22: party switching, orders and simulated nodes.

**Change.** Add explicit remote-control sessions for drones, cameras, turrets or bodies while the operator remains physically present and vulnerable.

**Creator controls.** Set control range/link quality, latency in game actions, autonomy on disconnect, operator action locks and takeover permissions.

**Smallest useful version.** Control a scout drone through a camera network; a cut relay returns it to an authored safe behavior.

**Acceptance.** Exactly one controller can command an actuator; switching windows cannot bypass action budgets; operator attacks, disconnect, destruction and restore release or recover control deterministically.

**Tradeoff.** Remote control must have its own contract; it is not the same as switching between independently active party members.

### C07 — Cybernetic implants and body capacity

**new · L · phase 2 · depends on T04, C04.**

**Current foundation.** V23: FalseLimb, generic races, wear locations and effects.

**Change.** Introduce implant slots, capacity/compatibility, power and condition while supporting replacement limbs, sensory implants and embedded tools.

**Creator controls.** Set biological/synthetic eligibility, slot occupancy, prerequisites, conflicting implants, energy/heat costs and optional strain/cost models.

**Smallest useful version.** A replacement arm and ocular implant grant declared functions and consume separate capacity/power budgets.

**Acceptance.** Implants cannot be duplicated through equipment moves; removal revokes granted abilities; damage/power loss follows declared fail-safe behavior and identity persists across restore.

**Tradeoff.** Do not force an essence, humanity or morality penalty; that belongs to a world-specific progression policy.

### C08 — Surgery, installation, extraction and recovery

**extend · L · phase 2 · depends on C07, T12.**

**Current foundation.** V09/V23/V24: engineering, injuries, medicine and timed jobs.

**Change.** Define clinical installation/removal/repair jobs with facilities, tools, practitioner skill, anesthesia and recovery outcomes.

**Creator controls.** Author surgery requirements, consumables, consent/control policy, failure complications, recoverable parts and recovery duration.

**Smallest useful version.** A clinic installs or removes one implant; interrupted surgery leaves a documented stable patient state.

**Acceptance.** Materials/implants are reserved once, no duplicate organ/limb is produced, cancellation and practitioner loss are handled, and save/restore cannot reroll a committed complication.

**Tradeoff.** Use clinical outcomes rather than requiring detailed anatomy simulation for every implant.

### C09 — Medicine, synthetic repair and configurable life detection

**extend · M · phase 2 · depends on R02, C05, T11.**

**Current foundation.** V06/V22/V24: scanners, diseases, health conditions and constructs.

**Change.** Let diagnosis and treatment target biological, synthetic and hybrid entities through declared physiology and sensor signatures.

**Creator controls.** Define scan channels, known/unknown diagnoses, treatment tools, repair versus healing rules, triage and knowledge prerequisites.

**Smallest useful version.** A medscanner reports an injured human and a damaged drone differently; the correct tool treats each.

**Acceptance.** Detection is independent of fertility, omitted data does not leak hidden conditions, treatment cannot duplicate rewards and hybrid targets obey a clear combined profile.

**Tradeoff.** Do not globally classify every non-breeding race as non-life; retain older scanner behavior only as a chosen legacy profile.

### C10 — Genetic adaptation, mutations and biotech traits

**extend · L · phase 3 · depends on T01, T12, C09.**

**Current foundation.** V21/V24: generic races, diseases, effects and advancement.

**Change.** Offer optional acquired traits, unstable mutations, engineered adaptations and species-specific interactions without replacing the race/class engine.

**Creator controls.** Define trait slots, causes, inheritance if desired, compatibility, reversibility, costs and discovery/diagnosis requirements.

**Smallest useful version.** A reversible radiation adaptation changes one resistance and one drawback under an authored treatment contract.

**Acceptance.** Trait grants/removals are idempotent, conflicting traits validate before application, appearance/vocabulary update correctly and saved progression does not apply effects twice.

**Tradeoff.** Avoid an unrestricted procedural race editor at runtime; bound the selectable authored trait set.

### C11 — Alien communication, machine languages and translation tools

**extend · M · phase 1 · depends on T01, T04.**

**Current foundation.** V29: current languages, comprehension, lexicons and local vocabulary.

**Change.** Add translator devices, machine protocols and scanning-based linguistic knowledge as recipes/extensions of the language engine.

**Creator controls.** Set supported languages/protocols, power, confidence, partial translations, encrypted messages and knowledge sharing scope.

**Smallest useful version.** A translator exposes partial alien speech, then improves after studying a saved sample.

**Acceptance.** Translation uses current comprehension rules, unknown secrets stay unknown, vocabulary respects context and a dead battery revokes only the device contribution.

**Tradeoff.** Use the existing language system; do not create separate incompatible speech handling for science fiction.

### C12 — Science-fiction professions and progression packages

**recipe · M · phase 1 · depends on T01, C04.**

**Current foundation.** V01/V21/V31: custom classes, abilities, expertise and world character setup.

**Change.** Publish optional creator recipes for engineer, medic, pilot, hacker, marine, scavenger and scientist roles using the existing class/ability system.

**Creator controls.** Choose class-based, skill-based or hybrid advancement, starting equipment, licenses and profession discovery hints.

**Smallest useful version.** Three example professions demonstrate ship repair, field medicine and ranged combat with different resource tradeoffs.

**Acceptance.** Each example is playable with a normal non-builder character, advancement prerequisites are visible and worlds can select a role without installing the entire genre package.

**Tradeoff.** Only require newly proposed mechanics when an example explicitly uses them; ordinary classes need no engine rewrite.

## Science, industry and campaign systems

### W01 — Technology research and saved project unlocks

**extend · L · phase 2 · depends on T01, T12.**

**Current foundation.** V03/V31: experiments, discovery, expertise, recipes and journals.

**Change.** Add project prerequisites, work, evidence, breakthroughs and unlocks for devices/recipes while keeping world eras distinct from campaign knowledge.

**Creator controls.** Set character/party/profile/organization scope, required samples, research methods, project costs and alternative paths.

**Smallest useful version.** Analyze a salvaged sensor to unlock one improved recipe, with progress shared at the chosen scope.

**Acceptance.** Prerequisites validate without cycles unless explicitly supported, project resources are reserved once, restore does not reroll completed work and unlocks do not rewrite global technology level.

**Tradeoff.** Share progression/unlock primitives with ARPG proposal P-family systems rather than maintaining a parallel advancement service.

**Audit findings:** F24.

### W02 — Scientific surveys, samples and evidence provenance

**extend · M · phase 2 · depends on R02, S08, T12.**

**Current foundation.** V06/V31: scan tools, material identity, discoveries and journals.

**Change.** Represent sampled sites/specimens, instrument calibration, analysis and provenance so exploration rewards more than killing or collecting arbitrary flags.

**Creator controls.** Define sample types, storage needs, evidence quality, contamination, location binding and repeatability.

**Smallest useful version.** A survey probe returns one sample whose analysis identifies a hazard and advances a research objective.

**Acceptance.** Splitting/merging samples follows provenance rules, duplicate evidence cannot claim a first-discovery reward twice and unseen site data is not exposed through metadata.

**Tradeoff.** Keep qualitative evidence tiers available; creators should not need a full scientific measurement simulator.

### W03 — Hazardous salvage, decontamination and waste

**extend · M · phase 2 · depends on T11, T12.**

**Current foundation.** V24/V25: radiation disease, hazards, salvage and material resources.

**Change.** Track hazardous cargo, contamination, quarantine and waste disposal as explicit properties/actions shared by salvage and industry.

**Creator controls.** Set sealed-container rules, contamination transfer, cleanup reagents, legal disposal sites and exposure incidents.

**Smallest useful version.** Retrieve a contaminated reactor core, transport it in a shielded case and decontaminate it before use.

**Acceptance.** Containers and handoffs preserve contamination, disposal destroys only the selected material, and save/interruption cannot clean an object for free.

**Tradeoff.** Contamination is optional metadata; clean salvage must remain simple.

### W04 — Blueprints, machine assembly and shipyard refits

**extend · L · phase 2 · depends on T01, T12, W01.**

**Current foundation.** V09/V25: engineering, crafting, recipes, materials and item quality.

**Change.** Author machines/vehicles as compatible parts with assembly/refit plans, tooling and repair requirements.

**Creator controls.** Set bill of materials, component tags, sockets, facilities, work/skills, minimum quality and recovered-part reuse.

**Smallest useful version.** Build one generator from a casing, coil and control unit; refit a shuttle engine without recreating its crew or interior.

**Acceptance.** Assembly/refit reserves resources, installed parts retain identity, invalid plans fail before consumption and cancellation/restore yields the declared recoverable parts.

**Tradeoff.** Share attachment/socket and job primitives with C02 and existing crafting; do not create a second item fabrication engine.

### W05 — Replicators, fabricators and matter/energy budgets

**new · L · phase 2 · depends on W04, T03, T06.**

**Current foundation.** V25/V32: crafting resources and reserved replicator category.

**Change.** Implement concrete fabrication devices with recipes, feedstock, power, time and optional pattern/license restrictions.

**Creator controls.** Choose food-only replicator, additive printer or exotic matter conversion; define conservation ratios, byproducts and prohibited outputs.

**Smallest useful version.** A station fabricator prints a replacement fuse from feedstock and a validated pattern.

**Acceptance.** No arbitrary item cloning occurs, input/output/energy balances are inspectable, jam/interruption policies conserve committed materials and queues restore without duplicate products.

**Tradeoff.** Exotic creation is a world rule with explicit cost, not an accidental bypass of crafting and economy.

**Audit findings:** F23.

### W06 — Production machines and bounded factory queues

**new · L · phase 2 · depends on T03, T12, W04.**

**Current foundation.** V25/V26: crafting, containers, ownership and scripts.

**Change.** Provide production jobs with inputs, outputs, machine capacity, workers, energy and stopped/starved states.

**Creator controls.** Set recipes, batch size, queue limits, throughput, operating hours, work requirements, waste and time-jump catch-up limits.

**Smallest useful version.** A recycler feeds a component printer; a brownout pauses both without losing their current work.

**Acceptance.** Inputs/outputs are conserved, analytic catch-up equals bounded normal progress where declared, disconnected storage halts cleanly and restore completes each batch once.

**Tradeoff.** Bound queue and graph work; do not tick every hypothetical factory worker in an unloaded planet.

### W07 — Cargo logistics, manifests and storage networks

**extend · L · phase 2 · depends on R11, T06, T12.**

**Current foundation.** V25/V26/V30: containers, material stacks, shops and transport cargo.

**Change.** Add cargo reservations/manifests, capacity in selected units, loading jobs and explicit transfer endpoints for ships, warehouses and factories.

**Creator controls.** Choose mass/volume/slot constraints, legal ownership, loaders, transfer speed, hazardous cargo rules and local versus linked storage.

**Smallest useful version.** Load a named shipment into a shuttle with a manifest and unload exactly that shipment at its destination.

**Acceptance.** Capacity is checked before partial commits, ownership/quest identity survives transfers, loading interruption is recoverable and linked storage does not duplicate items.

**Tradeoff.** Reuse existing container quantities and ownership; remote shared storage remains an explicit world capability, not an accidental default.

### W08 — Base facilities, utilities and upkeep

**extend · L · phase 3 · depends on T02, W06, W07.**

**Current foundation.** V26: housing, organizations, construction and power.

**Change.** Compose bases from functional facilities such as clinic, workshop, storage, comms, defense and living space, with optional upkeep.

**Creator controls.** Define ownership scope, capacities, resource needs, upgrade paths, utility links and offline/unoccupied simulation policy.

**Smallest useful version.** An owned outpost powers a workshop and clinic, requiring parts and periodic maintenance rather than real-time attendance.

**Acceptance.** Ownership changes preserve contents, upkeep uses game time, neglected facilities degrade according to policy and restore cannot reset unpaid obligations.

**Tradeoff.** A base can be a handful of authored rooms; no mandatory colony-management metagame.

### W09 — Aggregate colonies and settlement development

**new · XL · phase 3 · depends on W08, W10.**

**Current foundation.** V26/V28: organizations, economics and world simulation.

**Change.** Add optional aggregate population, workforce, supply, development and unrest with visible projects instead of thousands of offscreen NPC ticks.

**Creator controls.** Choose tick cadence, resource categories, worker abstractions, migration, disasters, growth limits and which individuals become simulated actors.

**Smallest useful version.** One small colony converts supplies into an infirmary upgrade and reacts to interrupted trade.

**Acceptance.** Catch-up has bounded cost, conservation/limits hold under long waits, unrest changes have explainable causes and loaded rooms agree with aggregate state.

**Tradeoff.** Large-scale simulation is deliberately late and optional; it should consume shared economy/job state rather than invent independent quantities.

### W10 — Trade goods, permits and market recovery

**extend · L · phase 2 · depends on T01, W07.**

**Current foundation.** V28: shops, law, organizations, restocking and existing economy.

**Change.** Offer local trade profiles with stock-sensitive prices, permits, contraband, tariffs and bounded recovery, suitable for cargo campaigns.

**Creator controls.** Set goods categories, stock targets, production/consumption rates, spreads, legal jurisdiction, permits and anti-loop caps.

**Smallest useful version.** Two stations buy/sell distinct goods and a permit changes legal access without altering physical cargo identity.

**Acceptance.** Prices/stock persist, long waits recover according to policy, invalid purchases are atomic and trivial buy/sell/restore loops do not create free cargo or money.

**Tradeoff.** This overlaps deferred market/restock work; implement one reusable market primitive shared by fantasy and science-fiction worlds.

### W11 — Corporate, fleet and political activity packages

**extend · XL · phase 3 · depends on S10, W10.**

**Current foundation.** V18/V28/V30: organizations, reputation, law, quests and transport.

**Change.** Build optional patrol, convoy, blockade, piracy and corporate competition activities on shared actors and missions.

**Creator controls.** Declare territory, legal authority, standing relations, budgets, routes, spawn caps, offscreen resolutions and intervention opportunities.

**Smallest useful version.** A corporation funds one convoy and one patrol; raiding cargo affects reputation and future availability.

**Acceptance.** Activities spend real budgets, do not spawn unlimited fleets, offscreen and observed results reconcile, and restore preserves outcomes without duplicate actors.

**Tradeoff.** Autonomous warfare overlaps deferred organization raids; use one general activity model rather than separate genre-specific war engines.

### W12 — Lost technology, reverse engineering and era transitions

**extend · M · phase 3 · depends on W01, W04.**

**Current foundation.** V03/V31: technology levels, maker data, experiments and recipes.

**Change.** Support ancient relics, incompatible standards, reverse engineering and deliberate campaign era changes without automatically invalidating all gear.

**Creator controls.** Set eras, standards/adapters, repairability, knowledge gates, obsolescence effects, salvage yield and transition triggers.

**Smallest useful version.** An ancient tool works immediately but needs research and an adapter before a settlement can reproduce its power cell.

**Acceptance.** Old saves retain the chosen era, new unlocks are scoped correctly, maker bounds remain valid and a transition produces a reviewable migration report.

**Tradeoff.** Technology advancement must be an authored campaign event, not a wall-clock treadmill inherited from a multiplayer server.

## Creator tools, verification and release

### Q01 — Technology capability reference and schema validator

**extend · M · phase 1 · depends on T01.**

**Current foundation.** V20/V32: WORLD CHECK, creator handbook and component editors.

**Change.** Add current capability labels and tech validation for maker IDs, component types, stat ranges, service requirements and missing assets.

**Creator controls.** Validate only enabled profiles, with errors for impossible references and warnings for intentional simplifications or optional devices.

**Smallest useful version.** WORLD CHECK gains a technology report identifying an unknown maker and a category-only device claim.

**Acceptance.** Reports cite object/file locations and remediation, do not claim unsupported enum slots are working devices and stay quiet for a valid legacy world.

**Tradeoff.** Do not burden fantasy worlds with disabled sci-fi warnings.

**Audit findings:** F22, F23.

### Q02 — Repair-oriented math and device regression harness

**extend · M · phase 0 · depends on none.**

**Current foundation.** V34: engine tests, fixes harness and the recorded audit probes.

**Change.** Turn each selected repair into independent assertions with analytic/reference cases and scoped seed coverage, replacing reproduced observations after the fix.

**Creator controls.** Keep focused groups for editing, geometry, combat, power and software; record known baseline failures until their precise contracts are repaired.

**Smallest useful version.** Add direct checks for wrong setters, health percentages, service registration, contact identity, limited shield arcs and reachable interception.

**Acceptance.** Checks fail on the audited baseline for the intended reason and pass after repair; they avoid mirroring broken formulas and expose numeric tolerance.

**Tradeoff.** A successful observation runner is not a passing feature test; do not relabel existing failures as success.

**Audit findings:** F26.

### Q03 — Fresh-process technology save and isolation matrix

**extend · L · phase 0 · depends on none.**

**Current foundation.** V19/V34: physical SPACE fixtures and campaign storage harness.

**Change.** Exercise actual GenSpaceShip interiors, power, programs, crew, cargo, ownership and activity state across publish, new play and fresh-process restore.

**Creator controls.** Cover unowned, player-owned and organization-owned craft, duplicate display names, destroyed targets and two separate worlds.

**Smallest useful version.** A powered crewed shuttle saved mid-approach restores with the same physical state and a valid declared activity outcome.

**Acceptance.** Counts/IDs are unique, software binds to the correct components, no source world or other save changes and missing assets lead to useful recovery diagnostics.

**Tradeoff.** Existing Asteroid-based S24 tests remain valuable but cannot stand in for this matrix.

**Audit findings:** F21, F26.

### Q04 — A playable technology museum and creator cookbook

**recipe · M · phase 1 · depends on R01, R02, R03, R06, R11, R12, T01.**

**Current foundation.** V20/V29/V34: creator museum examples, world text and authoring tools.

**Change.** Ship an opt-in example world showing portable tools, one power circuit, a terminal, an engineering task and a minimal shuttle with clear current-tool instructions.

**Creator controls.** Separate runnable present-day examples from proposal-only future examples and document exact enablement, expected output and recovery.

**Smallest useful version.** A research station scenario goes from clean import through play and a mid-scenario save/restore.

**Acceptance.** Normal players can finish it without builder privileges; docs, examples and advertised feature status agree; selecting it does not modify the default world.

**Tradeoff.** An example world is both a teaching artifact and integration fixture; keep it small enough to maintain.

**Audit findings:** F26.

### Q05 — Circuit, network and installation inspectors

**extend · M · phase 1 · depends on T02, T07.**

**Current foundation.** V20: LIST ELECTRONICS/SHIPS, object stats and developer diagnostics.

**Change.** Provide readable graph/ledger views of topology, installed status, power allocation, authority and fictional network reachability.

**Creator controls.** Filter by vessel, room, bus or node; show stable IDs and human names; offer compact text and machine-readable export.

**Smallest useful version.** Inspect why a terminal has power but cannot reach a locked door, and why a downstream light is shed.

**Acceptance.** Inspecting never changes the network, totals match the simulator and hidden nodes/details remain creator-only outside normal player diagnostics.

**Tradeoff.** A terminal table is sufficient; an interactive graphical editor is optional later.

### Q06 — Structured technology events and failure diagnostics

**extend · M · phase 1 · depends on R03, R04, R11.**

**Current foundation.** V15/V20: COMPONENTFAILURE messages, diagnostics and script hooks.

**Change.** Publish typed lifecycle events with initiating actor, device, owner, targets, resource deltas and stable reason codes instead of scraping display strings.

**Creator controls.** Offer script filters for power gained/lost, fault, access, link, scan, install, transfer and navigation state; debounce repeated states.

**Smallest useful version.** A script responds once to an actual fuel exhaustion and differently to an unsupported thrust port.

**Acceptance.** Events have consistent validate/commit ordering, saved jobs do not replay completion twice and factory/system actors retain the originating player attribution where relevant.

**Tradeoff.** Reuse existing event/script dispatch; unbounded per-tick broadcasts would make large worlds unusable.

**Audit findings:** F28.

### Q07 — Technology editors, templates and stat discoverability

**extend · L · phase 2 · depends on Q01, Q05, T09.**

**Current foundation.** V20: CMGenEditor, generic definitions and creator commands.

**Change.** Add technology-focused creation templates, grouped fields with units, compatible part pickers, service summaries and previews.

**Creator controls.** Keep terminal-based editing first; provide text/JSON import/export with canonical IDs and schema versions.

**Smallest useful version.** A creator builds a battery-powered access terminal without manually knowing every inherited generic stat code.

**Acceptance.** Editor and hand-edited files produce equivalent objects, invalid graphs/services are caught before publish and undo/save preserves original source assets.

**Tradeoff.** Do not revive the removed multiplayer web administration stack just to gain a form editor.

### Q08 — Balance profiles and bounded simulation benchmarks

**extend · M · phase 2 · depends on Q02, T05, S02.**

**Current foundation.** V20/V33/V34: engine testing, playtests and diagnostics.

**Change.** Benchmark common device, circuit and encounter sizes; provide selectable arcade/hard-survival balance profiles with explicit limits.

**Creator controls.** Report simulation work, active objects, power/job events, memory and maximum catch-up cost alongside gameplay balance measures.

**Smallest useful version.** Measure ten versus one hundred circuits and a small tactical ship encounter under turns and real time.

**Acceptance.** Profiles preserve their declared units; performance budgets have representative hardware/context and excessive graphs are diagnosed instead of silently hanging.

**Tradeoff.** Benchmarks guide caps, not universal speed promises. Exact deterministic replay remains separately deferred.

### Q09 — Migration, compatibility reports and save recovery

**extend · M · phase 1 · depends on R10, R11, R12, T01.**

**Current foundation.** V19/V20: world/save versions, asset validation and release workflow.

**Change.** Define explicit migrations for legacy stat fields, maker formulas, circuit keys, sector units and new saved activity schemas.

**Creator controls.** Offer inspect/dry-run/apply stages, preserve original assets and emit machine-readable changes plus unsupported-state warnings.

**Smallest useful version.** Import a legacy technology world into a consistent profile without silently changing coordinates or losing charge.

**Acceptance.** Repeated migration is idempotent, interrupted writes recover atomically, incompatible saves remain available and recovery never modifies an unrelated campaign.

**Tradeoff.** Do not promise arbitrary future backward compatibility; record required engine/world versions and migration boundaries.

### Q10 — Genre-aware presentation, vocabulary and accessibility

**recipe · S · phase 1 · depends on T01, C11.**

**Current foundation.** V29: current presentation, world help, language and text facilities.

**Change.** Provide optional terminology/text packs for ships, stations, cybernetics and modern equipment, including readable sensor and status output.

**Creator controls.** Select labels, concise/verbose diagnostics, color-independent warnings, abbreviations and local unit display.

**Smallest useful version.** A science-fiction world labels power, hull and comms naturally and offers help for supported commands only.

**Acceptance.** Text-only/no-color output retains all information, existing language comprehension still applies and default fantasy vocabulary stays intact.

**Tradeoff.** Presentation must not imply a missing mechanic works; category-only and proposed systems remain clearly labeled.

### Q11 — Composable scenario and mission generation adapters

**extend · M · phase 3 · depends on S09, S12, Q01.**

**Current foundation.** V28/V30: generation, encounters, quest templates and instances.

**Change.** Add generator pieces for derelicts, labs, habitats, convoy missions and crash sites with validated dependency bundles.

**Creator controls.** Declare required enabled systems, seed policy, solvability constraints, resource limits, exit/recovery and optional encounter substitutions.

**Smallest useful version.** Generate a bounded derelict with a reachable power repair, valid airlock and return route.

**Acceptance.** A recorded seed reproduces the authored structure, missing required systems fail generation clearly and every generated objective has a reachable completion/recovery path.

**Tradeoff.** Generation should select proven templates; random unsupported combinations are not additional content quality.

### Q12 — Feature completion checklist and release gates

**extend · S · phase 1 · depends on Q01, Q02, Q03.**

**Current foundation.** V20/V34: handbook status coverage and repository checks.

**Change.** Track every selected technology feature from data schema to runtime, creator help, scripts, saves, migration and playable example coverage.

**Creator controls.** Use statuses proposed, prototype, partial and supported; keep intentionally simplified behavior and deferred requirements explicit.

**Smallest useful version.** A technology coverage table links each advertised device/system to the relevant tests and example.

**Acceptance.** A category, interface or editor field alone cannot mark a feature complete; supported rows include normal-player use, save behavior and one failure/recovery path.

**Tradeoff.** This is a development artifact, not a new player-facing approval or setup flow.
