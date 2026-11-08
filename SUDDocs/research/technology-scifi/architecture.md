# Architecture, integration and rollout

[Start](README.md) · [Proposals](proposals.md) · [Findings](audit.md)

This chapter is a proposed engineering contract. New manifests, commands,
schemas, events and module names below do not exist merely because they are
described here. Existing foundations are linked to the current code inventory.

## Build on shared engine systems

CoffeeSUD is a local game engine with a profile, party, game clock, source world
files and campaign saves. Technology should obey those same boundaries. Keep
ordinary MOBs, items, rooms, areas, exits, abilities, damage, recipes, factions
and quests as the canonical game entities. An alien medic is still an authored
character; a laser hit still participates in ordinary combat and death credit.

Extend [Technical/Electronics/TechComponent](inventory.md#v02) for devices,
[WorldFiles/WorldState](inventory.md#v19) for persistence,
[Travel](inventory.md#v30) for abstract journeys and
[languages/presentation](inventory.md#v29) for text. Use common IDs, work jobs,
resource transfers and event dispatch rather than building independent copies
inside a drone system, factory system and ship system.

The earlier [ARPG architecture proposals](../arpg-mud/architecture.md) are also
designs. Their possible item modifiers, damage tags, activities and progression
services should be shared with C02/C04/W01 if selected. They are not prerequisites
that can already be called. An implementation spike must choose one owner for
each common primitive and update both proposal sets to avoid competing designs.

The executions draft is separate. A reactor failure, boarding scene or critical
hit can use existing scripts/text initially. If executions are later implemented,
they can subscribe to committed technology/combat events and apply their selected
participant-control policy; no technology proposal requires a global cutscene
lock or assumes both participants must be controlled.

## Optionality and compatibility

Clear correctness defects such as POWERREM writing capacity should be repaired
for existing users. Newly introduced simulation remains opt-in. Semantic
changes with balance/save consequences need a versioned compatibility contract,
especially manufacturer factors, physical time units and sector calculations.
An old world must not silently acquire finite suit oxygen, maintenance bills,
heat, radiation dose, hacking traces or colony upkeep.

Suggested module boundaries:

| Proposed module | Responsibility | Useful without spaceflight? |
| --- | --- | --- |
| Technology definitions | Makers, profiles, compatible parts, feature capabilities | Yes |
| Personal equipment | Cells, suits, guns, shields, tools | Yes |
| Power topology | Buses, links, breakers, priorities | Yes |
| Fictional networks | Nodes, credentials, access, data applications | Yes |
| Agents and bodies | Drones, remote control, implants, treatment | Yes |
| Environments | Compartment air, radiation, contamination | Yes |
| Science and production | Research, samples, assembly, fabrication, queues | Yes |
| Space travel | Charts, route/tactical/continuous movement, FTL | No |
| Ship operations | Crew stations, damage control, boarding, towing | Partly |
| Campaign simulation | Bases, colonies, markets and fleets | Yes |

A preset selects data and defaults; it is not an inheritance hierarchy. A
cyberpunk game can have implants and networks with no galaxy. A scavenger game
can have guns and generators with no cybernetics. A space-opera game can have
abstract jumps and hull-only damage with no numeric pressure or orbital physics.

### Further genre choices within the proposed foundations

These are selectable extensions of the catalog, not extra implemented features
or hidden requirements for its smallest versions:

| Setting or mechanic | Reuse | Additional contract to design when selected |
| --- | --- | --- |
| Electric cars, tanks, VTOL craft and mechs | Existing vehicles/interiors, T04–T06, C02, S02 control principles | Ground/air movement domains, terrain/clearance, traction or lift, crew/power/loadout limits; continuous space physics is unnecessary |
| Radio, distress traffic and delayed interstellar messages | T07 nodes/links, T09 applications, C11 translation, T12 jobs | Frequency/channel, range, relay, interference, encryption, sender identity, latency and saved delivery queues |
| Cameras, turrets and facility alarms | T07 authority, C05/C06 agents, Q06 events | Detection rules, operator/autonomy limits, target legality, ammunition and alarm escalation |
| Digital minds, backups and synthetic bodies | C05–C09 identity/body/control, T12 recovery jobs | Separate person identity from chassis; choose one active body or deliberate forks, copy costs, death/continuity and profile permissions |
| Cloning and biological fabrication | C09/C10 physiology/traits, W01/W04/W05 research/assembly | Sample provenance, growth resources/time, identity/memory policy and whether copies are independent actors |
| Alien ecologies and adaptive organisms | Generic races/behaviors, C10 traits, W02 samples | Bounded authored adaptations, food/atmosphere needs and inspectable consequences; no unrestricted evolutionary simulation is implied |
| Nanotechnology and self-repair | T05 faults, T12 jobs, W05 fabrication | Feedstock, power, spread limits, repair caps and unwanted-contamination behavior |
| Psionics, magitech and magic interference | Generic abilities, C04 tags, T01 profiles | Which effects target electronics, how interference consumes resources, and which mechanisms remain distinct |
| Stasis, cryosleep and generation travel | T12 jobs, S02/S03 travel, physiology | Aging/hunger/disease progression, interrupted recovery and clocks outside the sleeping crew |
| Anomalies, wormholes and exotic travel | Existing exits/instances, S03/S09, quest outcomes | Return paths, coordinate/knowledge changes and compatibility with saved travel authority |

For mind backups and cloning, duplication can be an intentional mechanic. The
engine must distinguish an authored fork from an accidental save/restore exploit
and assign the resulting actors valid independent identities. For time travel,
begin with authored quest/instance state: rolling the entire campaign backward
while preserving selected actors is a separate major persistence design and is
outside these proposals' smallest versions.

Enablement requires a dependency resolver and WORLD CHECK diagnostics. Hard
dependencies in the [catalog](proposals.csv) must be present. Avoid a rule that
silently enables every subsystem in response to one device class. Existing
legacy devices can remain usable through a documented legacy profile.

## Definition ownership and runtime ownership

| Data | Owner | Save behavior |
| --- | --- | --- |
| Device blueprints, makers, recipes, terminal pages | Source world | Load through world asset resolution; record compatibility version |
| Era definitions and enabled feature rules | Source world | Record the selected rule/profile version for compatibility checks |
| Charge, heat, wear, faults, installed parts | Item/component instance | Save instance state by persistent ID |
| Circuit and network definitions | World/boardable template | Save runtime rewiring, damage, switches and access changes |
| Navigation, scan, hack, build and research jobs | Explicit entity or campaign scope | Save progress, reservations, target IDs, deadlines and resume policy |
| Discovery, research unlocks, credentials | Character, profile or organization as authored | Save only at the selected scope; never infer profile-wide sharing |
| Drone control and crew assignments | Controlled agent/vessel | Save actor/controller IDs and intended orders; revalidate authority |
| Contacts and confidence | Observer/sensor/knowledge scope | Save only useful observations; rebind targets or age out safely |
| Colony aggregates and market state | Campaign organization/location | Save progress and last processed game time |

Maker XML currently lives in installation resources (F24). Move new definitions
under world asset resolution with an explicit precedence chain: world override,
selected package, then legacy fallback. A proposed directory such as
`world/technology/` would require loader, export, change detection, rebuild,
validation and packaging support. Creating files in that directory today would
not make them load.

Current campaign restores use live external world assets. This proposal does
not pretend that saves already snapshot all source files. Record compatible
world/profile/schema versions; when definitions change, choose a documented
migration or a visible safe suspension. If exact version-pinned content is
later selected, that requires its own asset lifecycle, not an undocumented copy
inside every device.

## Identity, copies and installation

Names are presentation. A renamed shuttle remains the same vessel; two shuttles
named Courier remain different. Reuse current persistent item identity where
appropriate and define IDs for components, network nodes, jobs and connections.
Imported templates need a namespace and copy/remap policy.

An installation graph owns parent-child bindings independently of the room
containing an item. Loose batteries on the bridge are not automatically part of
the bridge bus. Removing an engine disconnects it before software can issue a
new control command. A topology generation number invalidates cached component
lists after install/remove, move, copy, rename and restore.

Copies of ships normally clone their interior devices with new IDs and remapped
internal references. Deliberately shared fixed interiors need an explicit rule:
they must not accidentally create two physical ships controlling the same engine
or inventory. Reject ownership/container/control cycles at edit/import time.

Docking, towing and remote control are first-class relationships with two valid
endpoints. Destroying one endpoint releases or recovers the relationship. A
temporary exit or display name alone cannot safely represent the connection.

## One simulation clock and bounded work

GameTime and the existing tick scheduler supply time. PAUSE freezes world
progress; turn mode advances through actions. Device effects and jobs must not
quietly use wall-clock time to bypass those rules. Use seeded engine randomness
for gameplay outcomes, while acknowledging that exact deterministic replay is
still deferred in CoffeeSUD.

R10 must specify physical units before new continuous space simulation depends
on them. A practical new contract uses decameters for position, simulated seconds
for elapsed time, speed in decameters/second and acceleration in compatible
units. That is a proposal requiring adapters for legacy per-tick values. Text
display units can differ without changing stored physical units.

Every job chooses a catch-up policy:

- **Bounded discrete steps** for combat, hazardous active maneuvers and other
  ordered interactions where skipping steps changes outcomes.
- **Analytical progress** for ordinary production/research where elapsed work
  can be calculated without replaying every missed pulse.
- **Suspend or safe hold** where missing participants, inaccessible targets or
  dangerous uncertainty makes automatic progress inappropriate.

Do not perform millions of ticks to advance a month of colony growth. Cap graph
size, job queues, active probes, recursive transfers and per-tick event work.
Detect excessive configurations with an actionable diagnostic rather than
silently discarding material or freezing the console.

Stored deadlines should be remaining game durations or compatible absolute game
times, never unrebased wall-clock timestamps. Random results already committed
to a job must remain committed on restore; a new random roll must not be an
accidental side effect of rebuilding a menu.

## Validate, reserve, commit, announce

The existing validation-phase resource mutations (F14) need a shared remedy.
For shots, transfers, assembly, surgery and terminal actions:

1. Resolve identities and collect the intended targets and costs.
2. Validate permission, geometry, prerequisites and available resources without
   changing durable state or announcing success.
3. Reserve resources when an action spans time, with saved reservation ownership.
4. Commit the operation once, atomically with respect to the game action.
5. Emit result events, messages and quest/progression credit after the commit.
6. Release reservations or run the authored failure/refund policy if interrupted.

These are logical transaction rules; they do not require a new external database.
The local engine can coordinate them on its action/simulation lifecycle. Each
operation needs an ID so completion after save/restore is idempotent. Unhandled
errors should leave a diagnosable safe state rather than pretending the action
succeeded.

Attribution must include the actor who ordered an action, its actual executor
(drone, turret, software, crew member) and the owning organization where needed.
System/factory actors used for CMMsg delivery must not erase responsibility for
crime, reputation, combat credit or mission completion.

## Physics modes and information boundaries

Exactly one authority moves each vessel at a time:

| Mode | Canonical progress | Suitable use |
| --- | --- | --- |
| Abstract route | Journey segment/progress and authored destinations | Ordinary shuttle/FTL travel |
| Tactical region | Encounter positions, speed bands or bounded vectors | Chase, ambush, battle, salvage |
| Continuous | SpaceObject position/direction/velocity under versioned units | Worlds where steering physics is a feature |

Transitions bind passengers, cargo, crew, jobs and destination recovery. Moving
between modes must not duplicate a ship, multiply elapsed time or let autopilot
and an abstract journey both update its position. Existing ground route commands
are not a continuous-space trajectory planner.

Physical truth and player knowledge are separate. A sensor contact may know
direction and approximate mass without knowing a secret name or exact interior.
Database services answer only from the installed chart/authorized knowledge.
Builder diagnostics can expose truth; normal status and scripts acting as a
player must respect the selected knowledge scope.

Fictional network actions are capability-checked simulation. They never evaluate
player input as JavaScript/Java and never require a real TCP connection. Trusted
creator scripts retain their existing extension capabilities; fictional hacking
does not expand those privileges.

## Restore sequence and recovery

Extend the current save lifecycle with an explicit order:

1. Load compatible source definitions and rule profiles.
2. Restore physical entities/interiors and persistent IDs, following existing
   ownership routes for unowned, player-owned and organization-owned craft.
3. Rebuild installation, circuit and fictional network graphs.
4. Restore stores, faults, gas/contamination and other durable instance state.
5. Rebind controllers, crew, docking/tow links and knowledge/contact targets.
6. Reconstruct jobs and reservations; validate prerequisites and apply the
   selected resume/suspend/cancel policy.
7. Publish recovery diagnostics and allow normal ticking/input.

Missing parts should produce useful results: a destroyed beacon yields a safe
hold; a missing factory recipe freezes its queue for inspection; a missing drone
controller applies the saved autonomy policy. Never silently bind to the first
object with a matching display name. Never recreate a destroyed object merely
because a job references it.

## Proposed data sketch

The following **proposal-only** YAML illustrates responsibilities; it is not a
loader format or a list of recognized configuration keys:

```yaml
proposal_only: true
schema: technology-profile/1
id: station-survival
modules: [personal-equipment, power-topology, fictional-networks]
rules:
  quality_model: consistent-v1
  space_motion: abstract-route
  atmosphere: discrete
ownership:
  charts: profile
  credentials: character
jobs:
  missing_target: suspend-with-diagnostic
  idle_production: bounded-analytic
```

A real schema must validate dependencies, units and versions, load through world
assets, round-trip through creator tools, and define its save adapter before
this can be advertised as supported.

## Recommended work packages

| Package | Contents | Exit condition |
| --- | --- | --- |
| 0: Repair and establish facts | Q02/Q03 alongside R01–R12 | Narrow reproduced defects fixed; a real powered ship has an honest fresh-process restore contract |
| 1: World technology kit | T01–T04, T07/T09/T12, C01/C04/C05/C11 as selected; Q01/Q04–Q06/Q09/Q10/Q12 | A creator can ship the small station example using documented data and normal player commands |
| 2A: Exploration and ships | S01–S09, then S10–S12 as selected | One flight/travel authority, sound contacts, recoverable travel and meaningful ship operations |
| 2B: Cyberpunk and industry | T05/T06/T08/T10/T11, C02/C03/C06–C09, W01–W07/W10 as selected | Each chosen loop has saved resources/jobs and a playable example |
| 3: Larger campaigns | C10, W08/W09/W11/W12, Q11 | Bounded offscreen simulation and reconcilable world consequences |

The table groups product choices, not a claim that every item in one package is
a hard prerequisite of every other item. The catalog dependency graph is the
authoritative implementation ordering; Q07/Q08 can accompany the relevant
advanced systems. Smaller proposals should still include documentation,
validation and failure paths instead of deferring them until the entire genre
collection is finished.

The first vertical slice should remain small: a station with an emergency bus,
one terminal, one drone and a shuttle. Ship weapons, cybernetic surgery and
factory management can wait until their chosen slices have clear gameplay value.
No schedule is promised by this document, and none of these proposals authorizes
engine implementation without a subsequent selection.
