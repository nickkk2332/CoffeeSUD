# Science-fiction design cookbook

[Start](README.md) · [Capabilities](inventory.md) · [Proposals](proposals.md)

These eight designs are original scenario sketches. They are not runnable world
assets. **Current version** describes existing tools that can approximate the
scenario today, with the audit's limits. **Expanded version** requires the named
proposals and their dependencies. Proposed commands, property names and formats
are not presented as existing authoring APIs.

## 1. The emergency-power research station

The party wakes in a station whose laboratory and shuttle share a damaged power
source. Keeping the laboratory online reveals a rescue signal, but briefly
taking it offline leaves enough reserve to prepare the shuttle. The engineer
can repair the main source and remove that tradeoff.

**Current version.** Author rooms, exits, a generator, battery, panel and console
using the existing generic items. AstroEngineering supplies actual installation
and repair. Scripts/quest objectives represent the larger power-routing choice,
with explicit state rather than relying on unverified panel conservation. World
text, scenery, help and logs communicate the emergency. Test with a normal player;
builder privileges must not be the only route to operating the devices.

**Expanded version.** T02/T03 define main and reserve buses with a breaker and
priority loads. T09 supplies a data-authored terminal; C05 supplies a maintenance
drone. T12 saves the repair task. R06 establishes the power ledger before balance
depends on it. This makes the initial station a suitable Q04 integration museum.

**Creator decisions.** Choose whether life support is another load or an abstract
safe background system. Give the player at least two paths: a physical repair
and a routing decision. Report which loads are shed and why; do not demand that
the player infer an invisible electrical graph.

**Acceptance scene.** Save with the breaker open and the drone repairing, restore
in a new process, and continue. One repair remains active, reserve charge is
unchanged, disconnected loads stay disconnected and completion grants one quest
outcome. Repeat with the terminal unpowered and with the main source destroyed.

## 2. The cracked shuttle over a silent moon

A survey shuttle has enough thrust to leave orbit but a damaged life-support
loop. The crew must decide between landing at an abandoned outpost and attempting
a risky direct trip to a rescue beacon.

**Current version.** GenSpaceShip/StdSpaceShip provide a boardable craft and
interior. Existing air refresh/stale-air behavior, ordinary breathing rules,
hazards and scripts can express the danger. Use an abstract Prop_Journey for a
controlled route, or the inherited navigation software only within tested paths
and with the documented defects in mind. Do not describe the current air system
as numeric pressure simulation or assume a ready-made functional atmosphere suit.

**Expanded version.** T04 supplies actual sealed suits. T10 adds compartments and
an airlock. S02 provides a defined travel mode; S03 gives the direct jump a
charge-up/fuel contract. S09 binds the outpost's physical landing site to its
interior and recovery route. R04/R12 stabilize maneuvers and saved intent.

**Creator decisions.** Decide whether oxygen is per character or a shared cabin
resource; choose a warning cadence that conveys danger without repeating every
tick. The abandoned outpost should remain a meaningful fallback if the rescue
beacon disappears or the shuttle is lost.

**Acceptance scene.** Save during an airlock cycle, with one companion aboard the
shuttle and one in the outpost. Restore, finish the cycle, undock and cancel a
jump charge. Verify pressure/state, actor locations, fuel reservations and a safe
return path. A prose warning cannot substitute for testing the actual hazard.

## 3. The corporate archive heist

The target document is in a sealed research archive. The party can obtain an
employee credential, tap an isolated maintenance terminal, or physically reach
the archive through a service tunnel. Hacking the wrong node alerts security.

**Current version.** Use locks, keys, requirement masks, scripts, existing puzzle
properties, journal objectives and a generic terminal/log prop. Organizations,
reputation and law can express the employer and consequences. These can create
an excellent authored heist now. A scripted access puzzle does not imply a
general network simulation, and ShipTelnetProgram is not an in-world hacking tool.

**Expanded version.** T07 models archive, maintenance, door and camera nodes.
T08 provides a trace with explicit progress and failed-attempt costs. T09 authors
the terminal pages and capability-checked actions. C06 can add a remotely
controlled scout camera or drone, with the operator's body left vulnerable.

**Creator decisions.** Decide whether credentials belong to one character, the
party, or an organization. Define what each node can do; reaching a maintenance
node should not automatically grant control of the entire facility. Offer a
physical or social recovery path after a failed intrusion.

**Acceptance scene.** Begin an intrusion, switch to another party window, cut the
relay, save and restore. The trace does not reset, the operator does not gain
extra actions by switching, and reconnecting restores only legally acquired
access. All interactions run without contacting any real network service.

## 4. The independent salvage drone

A small utility drone enters a radioactive maintenance shaft while the party
stays behind shielding. It can carry one sample, operate a manual switch and
return to charge. Losing the radio link forces a choice made before deployment:
wait, return, or finish the assigned task autonomously.

**Current version.** Author a robot-like generic race/MOB, limit its role with
scripts, and use followers/orders or profile recruitment where appropriate.
Radiation disease/hazards already provide environmental danger. A scripted robot
can behave convincingly, but existing party commands do not by themselves provide
a battery, control link, payload limit or drone ownership lifecycle.

**Expanded version.** C05 defines chassis, battery and payload; C06/T07 provide
telepresence and signal loss; T11/W03 handle dose and contaminated salvage. S08
reuses relevant deployment and data-reporting behavior for space probes rather
than building an unrelated second agent system.

**Creator decisions.** Make the drone a lightweight tool or a full companion,
then enforce the corresponding cap. Decide whether its sensors reveal what the
party knows, what the operator currently sees, or stored data recovered later.
Do not give an unreachable agent direct access to all world information.

**Acceptance scene.** Deploy, pick up the unique sample, lose the link, destroy
the charging station and save. Restore and recover the drone by the authored
route. There is one drone, one sample, the same remaining battery and an explicit
response to the missing charger. Repeated deployment cannot multiply free agents.

## 5. The three-person boarding duel

A courier crew faces a customs cutter. A marine can repel boarders, an engineer
can restore propulsion, and a pilot can maneuver into a docking position or flee.
Disabling the cutter's weapons ends the encounter without requiring every
occupant to die.

**Current version.** Existing ship interiors, profile parties, orders, ship
weapons/shields, quests and scripted encounter phases are useful building
blocks. A carefully authored room-scale boarding scene is possible. Do not
assume Sailor behavior supplies complete spacecraft battle tactics, or rely on
the broken limited shield/interception paths as if they were supported strategy.

**Expanded version.** S04 supplies subsystem damage, S10 assigns crew/control
roles, and S11 manages real docking/boarding relationships. S05 handles zero
gravity and acceleration. C03 can add cover in the boarding corridor. S06 adds
sensor confidence and jamming. All shots pass through R08's committed combat
resolution with valid attribution.

**Creator decisions.** Decide whether crew members are individually simulated or
one abstract rating for each station. Choose whether a disabled ship surrenders,
attempts repairs or calls for rescue. Distinguish a hull destruction outcome from
a boarding victory or a negotiated inspection.

**Acceptance scene.** Save while both ships are docked and one party member is
on the cutter. Restore, damage the docking connector, then separate. Occupants,
cargo and access links recover under the selected policy; weapons fire consumes
once and quest/reputation credit cannot be earned twice for the same surrender.

## 6. The belt survey and recovery contract

A mining cooperative offers payment for finding a missing survey buoy. An
uncertain contact may be the buoy, a decoy or a derelict with valuable cargo.
Recovering its data is enough to finish the contract; towing the whole body earns
an optional reward if the party can afford the fuel.

**Current version.** Author space bodies, a database record and a fixed sequence
of discoveries using scripts, journal objectives and abstract journeys. Existing
physical space export/save support is useful. An Asteroid can stand in for a
derelict marker, but its label does not give it a ship interior, working sensors
or towing mechanics.

**Expanded version.** S01 records chart knowledge, S06 adds uncertain contacts,
S08 supplies the buoy/probe lifecycle, and S07 adds capture/tow links. W02 gives
survey evidence provenance. S12 packages the mission using ordinary quest
objectives, with distinct partial and optional outcomes.

**Creator decisions.** Keep chart uncertainty separate from random rewards.
Define what equipment resolves a contact and which observations persist when the
link is lost. Data-only recovery should remain a legitimate cheaper outcome.

**Acceptance scene.** Identify the buoy, retrieve its data, tow it halfway home,
save and restore, then abandon the tow. Mission evidence stays valid, no extra
buoy or cargo appears, the tow releases symmetrically and the base reward can be
claimed only once. A chart query cannot disclose the decoy's secret identity.

## 7. The clinic and the ancient replacement arm

A scavenger returns with a damaged artificial arm. It functions as a basic
prosthesis, but activating its embedded field projector requires a compatible
cell and a recovered technical pattern. A clinic can install it; a field
mechanic can only stabilize the damage.

**Current version.** FalseLimb/amputation-related behavior and wearable/granted
effects can approximate the prosthesis. Recipe learning, experiments, journal
flags, custom classes and scripts can supply the discovery chain. Existing
health scans should first receive R02. Current tools do not establish a general
implant capacity, surgical job or synthetic physiology model.

**Expanded version.** C07 models implant capacity, C08 installation/removal,
C09 physiology-aware treatment, W01 research, and W12 incompatible lost-tech
standards. T04/T05 handle charge and optional heat. The field projector can use
C04's shared damage/resistance tags rather than a separate cybernetic combat path.

**Creator decisions.** Keep capacity, strain and social consequences world
options. An implant need not reduce humanity or morality. Define what survives
an interrupted installation and whether removing the implant produces a reusable
part, a damaged part, or waste.

**Acceptance scene.** Save with the implant reserved for surgery. Restore, lose
the practitioner, and follow the recovery route. The arm remains exactly once,
no effects are granted before installation commits, removal revokes only its
contribution, and ordinary non-augmented characters retain a viable progression.

## 8. The outpost that becomes a colony

The player restores an abandoned workshop. Initially it can only recycle scrap
into fuses. Later, trade and scientific work support a clinic and a landing pad.
The settlement can grow into a colony if that mode is selected; it can remain a
small personal base in a less managerial campaign.

**Current version.** Housing, organization ownership, shops, crafting recipes,
salvage, transport and scripts can represent a workshop with fixed upgrade
objectives. Use explicit authored state. The existence of these pieces does
not provide general industrial queues, conserved production graphs or aggregate
population simulation.

**Expanded version.** W04 builds machines, W06 runs bounded queues, W07 manages
cargo, and W08 supplies facilities/upkeep. W10 adds selected market behavior.
W09/W11 are deliberately later choices for aggregate population and political
activity. T12 provides one shared job lifecycle, T03 handles brownouts and W03
keeps contaminated input from quietly becoming clean output.

**Creator decisions.** Set limits on production, storage, catch-up and offscreen
activity. Decide whether workers are abstract assignments or named NPCs. Keep
the personal-base progression independent of the full colony simulation.

**Acceptance scene.** Start a batch with reserved scrap, interrupt power, wait
a month of game time and restore. Queues apply the declared bounded catch-up
policy, output and waste are conserved, upkeep is not reset, and the console
explains why work stopped. The game should not replay a month of individual
worker ticks before returning control.

## Proposal-only job sketch

The following YAML summarizes one design responsibility. It is **not** supported
JRUN, MOBPROG, quest or world-file syntax:

```yaml
proposal_only: true
job:
  id: outpost-printer-batch-17
  kind: fabrication
  owner: organization:survey-cooperative
  host: component:printer-2
  reserved_inputs: [cargo:scrap-lot-41]
  progress_work: 30
  required_work: 100
  on_power_loss: suspend
  on_missing_host: recover-inputs-at-owned-storage
  catch_up: bounded-analytic
  completion: emit-once
```

The point is the explicit owner, reservation, recovery and completion contract.
A real implementation needs schema validation, identity binding and tests for
every transition before a creator can use a format like this.
