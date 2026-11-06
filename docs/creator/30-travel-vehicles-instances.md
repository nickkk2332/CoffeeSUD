# Travel, vehicles and instances

This chapter covers everything that moves a party through the world: the cost of a step, travel time, roads, abstract journeys that take game hours, scheduled ferries and coaches, taxis and ticket takers, delayed portals, moving rooms such as lifts, rideable mounts and carts, stables, boardable vessels (boats, sailing ships, caravans, air ships), fast-travel waypoints, and instanced or resetting places (dungeons that reset, scaled areas, planes). It is written for world creators (the in-game editors and world files), scripters (the `MP*` vessel commands, functions and `JOURNEY_PROG`) and engine developers (the `Travel`, `Waypoints` and `ScriptEvents` helpers). It takes over the travel half of the old `10-simulation-travel.md`; time, weather and calendars are now in [10-world-simulation.md](10-world-simulation.md).

CoffeeSUD is single-player. "The party" means the player's controlled characters and their followers, and "the leader" is the party leader (`getGroupLeader()`). Several features below (travel time passing through the scheduler, journeys, fares for the whole party) depend on that model; see [31-saves-and-party.md](31-saves-and-party.md).

## In this chapter

- [Concepts and vocabulary](#concepts-and-vocabulary)
- [Option syntax used by the travel properties](#option-syntax-used-by-the-travel-properties)
- [Travel basics: movement cost, travel time and roads](#travel-basics-movement-cost-travel-time-and-roads)
- [Journeys](#journeys)
- [Named ports and routes](#named-ports-and-routes)
- [Vessels: rideables, boardables, ships and caravans](#vessels-rideables-boardables-ships-and-caravans)
- [Transport lines and timetables](#transport-lines-and-timetables)
- [Fares and ticket takers](#fares-and-ticket-takers)
- [Taxis and the Concierge](#taxis-and-the-concierge)
- [DelayedTransporter](#delayedtransporter)
- [Moving rooms](#moving-rooms)
- [Riding, mounts and carts](#riding-mounts-and-carts)
- [Stables](#stables)
- [Waypoints and fast travel](#waypoints-and-fast-travel)
- [Instances, resets and planes](#instances-resets-and-planes)
- [Scripting travel](#scripting-travel)
- [Command reference](#command-reference)
- [Worked examples](#worked-examples)
- [Persistence](#persistence)
- [Checking your work](#checking-your-work)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## Concepts and vocabulary

| Term | Meaning |
| --- | --- |
| Step | One move through an exit. Costs movement points, an action delay and (optionally) game time. |
| Travel time | Game minutes a step takes, set with `Prop_TravelTime`. The world clock really advances by that much through the scheduler, so hunger, weather, NPCs and scripts all run meanwhile. |
| Journey | An abstract trip (`Prop_Journey`): the party waits in a transit room while `DURATION` game hours pass, with random or scripted events on the way. |
| Transport line | A vehicle or driver on a route of stops (`TransportLine`), leaving by timetable and charging fares. |
| Timetable | The published state of a line or moving room: `TIMETABLE`, or text on a sign item. |
| Port | A room made a named destination with `Prop_Port`. `COURSE TO`, `SAIL TO`, `DRIVE TO`, `MPSAIL` and `TransportLine` stops accept port names. |
| Rideable | Something you mount: a horse, a cart, a canoe. The rider is carried but is not "inside" it. |
| Boardable | A vessel with an interior area of its own (a ship, a caravan, an air ship). You enter it; the interior is a separate area whose deck rooms link to wherever the vessel is. |
| Navigable boardable | A boardable that moves (`GenSailingShip`, `GenCaravan`, `GenAirShip`, `GenBalloon`). It understands `COURSE`, `STEER`, `RAISE ANCHOR` and so on. |
| Helm | The right to give those orders. Anyone may if the vessel has no owner; `Prop_HelmRestrict` and `Sailor` crew change that. |
| Moving room | A room (a lift, a train car) that docks at stop rooms on a route (`MovingRoom`). |
| Waypoint | A discoverable place in a named network (`Prop_Waypoint`); `TRAVEL` jumps between discovered ones. |
| Instance | A private copy of an area made when a party enters (`InstanceArea`), or an area scaled in place (`INPLACE=true`). |
| Plane | A planar variant of an area made by planeshift magic (`StdPlanarAbility`, `planesofexistence.txt`). |
| Room ID | `Area Name#N`. Travel properties take room IDs wherever a room is needed. |

What is CoffeeSUD-specific: `Prop_TravelTime`, `Prop_Road`, `Prop_Port`, `Prop_Journey`, `Prop_Waypoint`, `Prop_HelmRestrict`, `TransportLine`, the party fare rules, the `MP*` vessel commands, `VESSEL`, `PORTS`, `TIMETABLE`, `JOURNEY`, `TRAVEL`, `STABLE`/`RETRIEVE`, `MovingRoom` route files and saved positions, and `InstanceArea INPLACE`. `TaxiBehavior`, `Concierge`, `DelayedTransporter`, `Nanny`, `MovingRoom`, `ResetWhole`, `Prop_TicketTaker` and `InstanceArea` are inherited from CoffeeMud 5.11 with CoffeeSUD extensions noted below.

CoffeeSUD removed networking and multiplayer. Nothing here needs an account or another player; where the inherited text talks about players or clans, read "the party".

## Option syntax used by the travel properties

Every CoffeeSUD travel property and behavior (`Prop_TravelTime`, `Prop_Road`, `Prop_Port`, `Prop_Journey`, `Prop_Waypoint`, `Prop_HelmRestrict`, `TransportLine`, `Nanny`, `MovingRoom`, `DelayedTransporter`, `TaxiBehavior`, the new `Prop_TicketTaker` options) reads `KEY=value` options with one shared tokenizer (`AuthoringOptions`):

- Options are separated by white space or a semicolon. Keys are case-insensitive. Values are case-sensitive unless a table says otherwise.
- A value containing spaces, semicolons or equals signs goes in double quotes: `NAME="Port Azure"`. A literal quote inside a quoted value is `\"`, a literal backslash `\\`, a literal semicolon `\;`.
- Booleans are `true` or `false`. Numbers may be fractional where the tables say so.
- A bare word with no `=` is a flag.
- Because `;` separates options, list values that themselves contain semicolons are quoted: `STOPS="Pier#0;Quay#3"`, `EVENTS="10%:storm;5%:LOAD MOB pirate"`.

Two behaviors keep an older syntax in front of the options. `DelayedTransporter` is `min=.. max=.. chance=.. [options] ; destination ; destination ...` (the options come before the first semicolon, so do not put a semicolon inside its messages). `Concierge` and `TaxiBehavior` are a semicolon list of `price`, `place=price` and `FLAG=value` entries. Both are described in their own sections.

`WORLD CHECK ROUTES` and `WORLD CHECK IDS` (see [02-world-workflow.md](02-world-workflow.md)) validate these options: unknown keys, bad enums, missing `DEST`/`DURATION`, missing stop rooms, missing route files, ambiguous waypoint names and duplicate waypoint IDs.

How to attach a property or behavior is the same for everything in this chapter. In the editors (`MODIFY ROOM`, `MODIFY EXIT <dir>`, `MODIFY AREA`, `MODIFY MOB <name>`, `MODIFY ITEM <name>`), use the Effects field to add a property by class ID (`Prop_Journey`) and the Behaviors field to add a behavior (`TransportLine`); you are then asked for the parameter text. In world files they are `AFF` entries (`ACLASS`, `ATEXT`) and `BHAVE` entries (`BCLASS`, `BPARMS`); see [23-properties-reference.md](23-properties-reference.md#attaching-properties) and [24-behaviors-and-masks.md](24-behaviors-and-masks.md).

## Travel basics: movement cost, travel time and roads

### What a step costs

Three independent costs apply to ordinary movement. All three are separate from a journey.

| Cost | Where it is set | Details |
| --- | --- | --- |
| Movement points | `WALKCOST` and `RUNCOST` in `coffeemud.ini` (defaults 1 and 2), the room's own movement cost, the climate, encumbrance | Walking charges `WALKCOST` units of effort, running (`RUN`, auto-run) charges `RUNCOST`. Each unit takes the room's cost (below) off movement points. A rider whose mount carries them pays nothing; the mount moves. When movement is 0 you are "too tired". |
| Action time | the per-command action cost used by the turn system | Normal; not changed by anything in this chapter. |
| Game time | `Prop_TravelTime` | Optional extra game minutes, passed through the scheduler. |

Details of the movement-point cost, from `MUDTracker.move` and `MUDFight.expendEnergy`:

- Each unit costs the movement cost of the room the character is leaving. Most locales fix their cost: `CityStreet`, `Road`, `StoneRoom`, `WoodRoom` 1; `Plains`, `Desert`, `Shore`, `WaterSurface`, `StdRoom` 2; `Hills`, `Swamp`, `Jungle`, `UnderWater` 3; `CaveSurface`, `TreeSurface`, `ClimbableSurface`, `LargeCaveRoom` 4; `Mountains`, `LongRoad` 5; `MountainSurface` 6; `LongerRoad` 10; `LongestRoad` 20; `Whirlpool` 30; `HideoutShelter` and `MagicShelter` 0. (Source: each locale's `setMovementCost`.)
- Outdoor rooms multiply it with the weather: thunderstorm and hail x2, duststorm x3, blizzard x4. Indoor rooms do not (`DefaultClimate.adjustMovement`).
- Carrying more than your `maxCarry` multiplies the cost by `1 + 10 x (weight - maxCarry) / maxCarry`.
- A mounted rider does not pay (`MUDTracker`: the cost is charged only when `riding() == null`).
- Builders with the `NOCLIP` or `NOCOST` dev cheat pay nothing.

To make a region more tiring, use the locale with the cost you want (`LongRoad`, `LongerRoad` and `LongestRoad` exist only for this). To change the cost for the whole game, change `WALKCOST` and `RUNCOST`:

```ini
# CoffeeMud/coffeemud.ini
WALKCOST=1
RUNCOST=2
```

### Prop_TravelTime

Source: [Prop_TravelTime](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_TravelTime.java), evaluated by [Travel.stepMillis](../../CoffeeMud/com/planet_ink/coffee_mud/core/Travel.java).

Hosts: exits, rooms, areas. Parameters:

| Option | Default | Meaning |
| --- | --- | --- |
| `TIME` | 0 | Game minutes per step. 0 means no extra time. |
| `MOUNTED` | 0.5 | Multiplier when the mover is riding a creature, or is the mount carrying riders. |
| `VEHICLE` | 0.7 | Multiplier when the mover is a vehicle (a rideable or boardable item), or rides an item such as a cart or boat. |
| `NIGHT` | 1.5 | Multiplier when the destination's local time of day is NIGHT. |

```text
TIME=120 MOUNTED=0.5 VEHICLE=0.7 NIGHT=1.5
```

Rules (all from `Travel.stepMillis` and `Travel.moved`):

- Only one `Prop_TravelTime` applies per step. Priority is: the exit used, then the destination room, the destination area, the source room, the source area. The first found wins; they are not added.
- The multipliers combine: a night ride on a mount takes `TIME x MOUNTED x NIGHT`.
- A `Prop_Road` on the destination room (or its area) multiplies the result by its own `TIME` (default 1).
- The time is charged once per step, not once per party member. Followers moving with the leader do not add a charge.
- Time is only charged when a player character is moving, riding or aboard (`Travel.hasPlayer`), and only in single-player mode. A lone NPC wandering does not fast-forward the world.
- The wait is carried out by the scheduler (`GameTime.requestAdvance`), not by jumping the clock, so weather, hunger, NPC ticks and scripts run during it. Consecutive steps accumulate.
- A vessel that is travelling remembers how much step time remains (`WAIT` in its saved `<TRAVEL>` state) and cannot take another step until it has passed.
- One game hour is `MILLISPERMUDHOUR` milliseconds of game clock; the property converts as `minutes x millisPerHour / 60`.

To make the Kingsroad a two-hour walk per room, half that on a horse, with a night penalty:

1. `GOTO` the first room of the road, or `MODIFY AREA` if the whole area is the road.
2. Add `Prop_TravelTime` with `TIME=120 MOUNTED=0.5 NIGHT=1.5` to the area (Effects field of `MODIFY AREA`).
3. For one slow exit (a mountain pass), `MODIFY EXIT east` and add `Prop_TravelTime TIME=240`; the exit wins over the area.
4. Test it: `WAIT` is not needed; just walk and check `TIME` (the game clock) before and after, and note hunger and weather changes.

Prop_TravelTime does not charge movement points and is unrelated to `WAIT`, which is the player's own time passing.

### Prop_Road

Source: [Prop_Road](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Road.java), used by `Travel.drivable` and `Travel.stepMillis`.

Hosts: rooms, areas.

| Option | Default | Meaning |
| --- | --- | --- |
| `TIME` | 1 | Multiplier on the `Prop_TravelTime` step for steps that end in this room. |
| `NOTFOR` | none | Comma-separated vehicle names or class IDs that may not use the road. A vehicle is excluded if its class ID equals an entry, or its name contains the entry text. |

```text
TIME=0.75 NOTFOR="heavy caravan,GenCaravan"
```

Why it exists: land vehicles (`GenCaravan`, and any rideable whose basis is `LAND_BASED` or `WAGON`) may only enter "drivable" rooms. Without `Prop_Road` the test is the room's locale (`CMFlagLibrary.isDrivableRoom`): cities, plains, seaports and spaceports pass; caves and indoor stone only with certain weights and heights; forests, mountains and swamps do not. Putting `Prop_Road` on a room or area (checked first, room before area) makes it drivable without changing its locale, resources or description. A Woods room stays Woods but a caravan may roll through it.

`Prop_Road` applies to routing as well as to moving: `Travel.route` uses the same `drivable` test, so `COURSE TO` and `TransportLine` stops across a road work, and fail without it.

To make a forest road for caravans:

1. Build the road as a strip of ordinary `Woods` rooms joined by open exits.
2. Add `Prop_Road` (empty parameters, or `TIME=0.75`) to each road room, or to a small area that contains only the road.
3. Optionally add `Prop_TravelTime` to the area so a day's travel takes game time.
4. Test with `GenCaravan`: `DRIVE north` should move along the road and refuse into the unmarked forest.

## Journeys

A journey is an abstract trip: the party is moved to a transit room, a number of game hours pass in hourly legs, random or scripted events can interrupt, and the party arrives somewhere else. Use it for passages you do not want to build room by room (a coach ride, a sea crossing, a trek through the mountains). Use [TransportLine](#transport-lines-and-timetables) instead when the vehicle should physically exist and move through rooms.

Source: [Prop_Journey](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Journey.java) (the definition), [Prop_JourneyState](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_JourneyState.java) (the running trip), [Prop_JourneyPassenger](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_JourneyPassenger.java) (per-passenger marker), [Journey command](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Journey.java).

### How a journey runs

1. **Definition.** A `Prop_Journey` sits on a room, exit, item or mob (the host). It carries the options in the table below.
2. **Start.** A player character entering the host (an `ENTER` message whose target is the host, or whose tool is the host exit) starts the trip, or any character types `JOURNEY <host>`. For a room host, entering the room starts it; for an exit host, walking through the exit starts it; for an item host (a boardable, portal or rideable), entering it starts it.
3. **Checks.** Nothing happens unless all of these hold: the party is not already travelling; the clock is not in the middle of another time advance; `DEST` resolves to a room the traveller may see (a hidden port the traveller does not know fails); `DURATION` is positive; `VIA`, if given, resolves; and nobody in the party is in combat. Each refusal is a message to the traveller ("Your party is already travelling.", "The journey has no valid destination.", "Finish the fight before departing." and so on).
4. **Depart.** The party is everyone in the leader's group in the same room, plus the creatures they ride. They are moved together to the transit room (`VIA`, or the room they stand in if `VIA` is empty), using the normal leave and enter checks. If anyone is refused, nobody moves and no fare is charged ("The party cannot depart."). Otherwise the fare is charged, each member gets a hidden `Prop_JourneyPassenger` marker with a unique seat ID, the leader gets `Prop_JourneyState` (shown as "(Travelling)"), `JOURNEY_PROG START` fires and `LEAVEMSG` is shown.
5. **Legs.** The journey runs in legs of at most one game hour. For each leg the game clock is advanced through the scheduler, so the world lives on. At the end of a leg `JOURNEY_PROG HOUR` fires and the `EVENTS` are rolled.
6. **Events.** The entries of `EVENTS` are tried in the order written. Each has a percentage chance. The first that succeeds fires; at most one event happens per leg. The journey then pauses (see below).
7. **Arrive.** After the last leg, every surviving passenger is moved to `DEST` with ordinary entry checks, `ARRIVEMSG` is shown, `JOURNEY_PROG ARRIVE` fires, and all markers and the state are removed. If an entry check refuses, the journey stays paused and says "The party cannot arrive yet. JOURNEY RESUME retries arrival."
8. **Abort.** The state checks every tick. If the leader dies, or the leader is no longer in the transit room (walked out, was moved), the journey ends where the leader is, `JOURNEY_PROG CANCEL` fires and refunds are applied per `REFUND`. The transit room must therefore be a place the party stays in.

A leg is `min(1, hours left)`; a `DURATION` of 2.5 gives legs of 1, 1 and 0.5 hours. The first leg's length is fixed when the journey starts; remaining time is kept exactly across pauses and saves.

### Prop_Journey options

Hosts: rooms, exits, items, mobs. `DEST` and `DURATION` are required; `WORLD CHECK ROUTES` and the property editor refuse a definition without them.

| Option | Default | Meaning |
| --- | --- | --- |
| `DEST` | required | Destination: a room ID, a port name (`Prop_Port`), an area name (first accessible room), or `CHART n` / a number for the traveller's numbered Sea Charting point. |
| `DURATION` | required | Game hours, fractional allowed, greater than 0. |
| `VIA` | where the party stands | Transit room ID or port name. The party waits here. Give a dedicated room (a cabin, a coach interior) if the players should see something while they wait. |
| `FARE` | 0 | Price in the base currency of the traveller. |
| `PARTY` | `LEADER` | `LEADER`: the leader pays for everyone. `EACH`: every player character pays their own share. |
| `PERHEAD` | `true` | With `true` each player character in the party costs `FARE`; with `false` the fare is a flat price for the whole party. Creatures that are not player characters (mounts, followers) are never charged on a journey. |
| `LEAVEMSG` | none | Text shown to every passenger on departure. |
| `ARRIVEMSG` | none | Text shown to every passenger on arrival. |
| `EVENTS` | none | Quoted list `chance%:action;chance%:action...`. See below. |
| `MISSING` | `WAIT` | Policy for a passenger who is alive but not with the leader. `WAIT`, `CONTINUE`, `RESCUE`, `CANCEL`, `RETURN`. |
| `DEAD` | `CONTINUE` | Policy for a dead passenger. `CONTINUE`, `WAIT`, `CANCEL`, `RETURN`. |
| `DISCONNECTED` | `TRAVEL` | Policy for a player character that has no session (a background party member not in a window). `TRAVEL`, `WAIT`, `CONTINUE`, `CANCEL`, `RETURN`. |
| `CANCEL` | `HERE` | Where `JOURNEY CANCEL` ends the trip: `HERE` (where the party is now, normally the transit room) or `RETURN` (back to the room the journey started from). |
| `REFUND` | `NONE` | What a cancelled trip refunds: `NONE`, `FULL` (everything the payer paid), `UNUSED` (the part of the fare for the hours not yet travelled). |

```text
DEST=Gullrock#0 VIA=Ferry#Cabin DURATION=6 FARE=2 PARTY=LEADER LEAVEMSG="The mainland falls astern." ARRIVEMSG="Gullrock rises ahead." EVENTS="10%:storm_scene;5%:LOAD MOB pirate band;2%:CATALOG encounter-sea"
```

### Events

An `EVENTS` entry is `chance%:action`. The chance is 0 to 100 (the `%` is optional). The action is one of:

| Action | Effect |
| --- | --- |
| a function name | Calls `FUNCTION_PROG <name>` (or the JavaScript equivalent) on the journey host's scripts, passing the hours already travelled and the leader. If the function returns `PAUSE` the journey waits until someone types `JOURNEY RESUME`; any other result lets it resume by itself. |
| `LOAD MOB <spec>` | Spawns mobs into the transit room, hostile to the party: `<spec>` is one or more `name*dice` parts joined by `+` (a catalog mob name or mob class ID; `2d3`, `1d4+1`, `3` or `2-5` as the dice), or `encounter:<table>`, or `catalog:<name>`. Mobs spawned are not rejuvenated. |
| `CATALOG <table>` | Rolls an encounter table (`world/encounters/<table>.enc`) in the transit room against the leader. See [29-combat-and-encounters.md](29-combat-and-encounters.md). |

After any event the journey is paused. A pause caused by an event that is not a `PAUSE` function, or by combat, lifts by itself at the next tick once nobody in the party is fighting. A pause caused by `PAUSE`, or by a passenger policy, needs `JOURNEY RESUME`. This lets a scene run to its end (a conversation, a puzzle) before travel continues.

Because `EVENTS` is a list of statements separated by `;`, it must be quoted. Function names are looked up on the host: an exit host needs its `FUNCTION_PROG` scripts on that exit, a room host on the room, and so on. The saved journey remembers the host by its persistent ID; see [Persistence](#persistence).

### Passenger policies

Before every leg and before arrival the journey checks each seat:

- **Dead**: the passenger is dead or destroyed.
- **Missing**: the passenger is alive but is not in the leader's room, or has lost the marker.
- **Disconnected**: a player character whose session is gone.

| Policy | Meaning |
| --- | --- |
| `TRAVEL` | (Disconnected only) keep the character travelling with the group. |
| `CONTINUE` | Release the seat and carry on without that passenger. A dead or missing passenger never blocks arrival. |
| `WAIT` | Pause without spending time and tell the leader why. `JOURNEY RESUME` re-checks. Unknown policy words behave like `WAIT`. |
| `RESCUE` | (Missing only) try to move the living, non-fighting passenger back to the leader through normal leave/enter checks; if that fails, wait. |
| `CANCEL` | End the trip now, where the party is (refund per `REFUND`). |
| `RETURN` | End the trip now and send the present passengers back to the origin. |

When a refused `RETURN` leaves the party stuck, the journey stays paused with "The party cannot return to the origin yet. JOURNEY CANCEL retries the return."; provide a recovery route in the transit room.

### Refunds

At the start every payer's actual payment is saved as a receipt (seat, player, currency, amount). A cancelled journey (by `JOURNEY CANCEL`, a policy, or an abort) pays `REFUND` back: nothing, the full amount, or the unused fraction `hoursLeft / (hoursLeft + hoursElapsed)`. Each receipt pays once: a tattoo `FARE_REFUND_...` marks it, so repeated cancel attempts or a reload cannot pay twice. A successful arrival never refunds. A trip saved before receipts existed cannot reconstruct an old fare.

### JOURNEY_PROG

The `JOURNEY_PROG` trigger lets scripts follow a trip. Syntax: `JOURNEY_PROG START`, `HOUR`, `ARRIVE`, `CANCEL` or `ALL` (no argument also means all). In the script `$g` is the stage name in lowercase, `$0` the game hours completed and `$1` the destination room ID. It runs on the scripts in the transit room and its occupants, the journey host, and any script that registered for the trigger anywhere (those see it as if from a distance). Reference: [26-mobprog-triggers.md](26-mobprog-triggers.md#journey_prog).

```text
JOURNEY_PROG START
  mpecho The ship leaves harbour.
~
JOURNEY_PROG HOUR
  mpecho Another hour passes at sea.
~
JOURNEY_PROG ARRIVE
  mpecho Land at last.
~
```

### Journey commands

| Command | Effect |
| --- | --- |
| `JOURNEY` | While travelling: show progress ("2.0 game hours remain to Gullrock (paused). JOURNEY RESUME or CANCEL."). Otherwise start the journey of the room you stand in. |
| `JOURNEY <host>` | Start the journey on a named host (an item, mob or exit in the room). "You are on no journey. JOURNEY [passage] begins one here." if it has none. |
| `JOURNEY RESUME` | Continue after a scene, or retry recovery or arrival. Refused while anyone fights. |
| `JOURNEY CANCEL` | End the trip according to `CANCEL=` and `REFUND=`. |

Further reading: [Journey recovery](../authoring-reliability.md#journey-recovery) in the reliability guide, and the cookbook recipe "A paid abstract journey with recovery" in [cookbook.md](cookbook.md).

To make a journey, step by step:

1. Build the destination room and the transit room (say `Ferry#Cabin`). Keep the transit room self-contained: exits out of it let players walk away and abort the trip.
2. Choose the host. A ferry gangplank exit is natural: `MODIFY EXIT` on the quay's exit into the ferry, and add `Prop_Journey`.
3. Write the definition on one line (see the example above). Quote `EVENTS`, messages and any value with spaces.
4. Put event functions in `FUNCTION_PROG` scripts on the same host.
5. Test as a party of two: pay, depart, `JOURNEY`, force an event, fight, `JOURNEY RESUME`, `JOURNEY CANCEL`, save in the middle and restore.

## Named ports and routes

Source: [Prop_Port](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Port.java), [Ports command](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Ports.java), [Travel.destination / route / sail](../../CoffeeMud/com/planet_ink/coffee_mud/core/Travel.java).

A port is a room with `Prop_Port`:

| Option | Default | Meaning |
| --- | --- | --- |
| `NAME` | the room's title | The name players type: `COURSE TO Port Azure`. |
| `KIND` | `SEA` | `SEA`, `ROAD`, `AIR` or `SPACE`. This is only a label shown by `PORTS`; routing is decided by the vessel's terrain rules, not by `KIND`. |
| `HIDDEN` | `false` | A hidden port is not listed or accepted until the character qualifies. |
| `KNOWNMASK` | none | A zapper mask. If given, only characters matching it know the port (this replaces the `HIDDEN` test). Builders with `CMDROOMS` always know all ports. |

```text
NAME="Port Azure" KIND=SEA HIDDEN=false
NAME="Smuggler Cove" KIND=SEA HIDDEN=true KNOWNMASK="-TATTOO +COVE_CHART"
```

`PORTS` lists the ports the character knows, sorted, as `Name (KIND)`. A mask-hidden port stays out of the list, out of `COURSE TO`, and out of `Prop_Journey` `DEST` for that character, until they have the tattoo (or whatever the mask checks).

### Where a destination name can come from

`Travel.destination(name, who)` resolves, in order:

1. An exact room ID (`Harbour#2`). The character must be able to access the room, and if it is a port they must know it.
2. For a character, a number or `CHART n`: the nth point in the character's `Skill_SeaCharting` list (`SEACHART` ... `SEANAVIGATE n`).
3. A port `NAME` (case-insensitive). If a port by that name exists but the character does not know it, resolution stops with "unknown" rather than falling through.
4. An area name: the first accessible room of the area.

Scripts and `TransportLine` stops pass no character, so they see hidden ports too.

### COURSE TO, SAIL TO, DRIVE TO

From inside a navigable vessel (on any of its decks), the helm holder types one of:

```text
COURSE TO Port Azure
SAIL TO Port Azure
DRIVE TO Hidden Cove#1
```

The three words are interchangeable on any vessel (the engine matches `COURSE|SAIL|DRIVE TO ...`). The command refuses without the helm ("You are not permitted to do that."), and answers "Unknown or inaccessible destination." or "There is no navigable route to that destination." or "The vessel is already there." Success says "Course set.", clears the anchor and sets the course; the vessel then moves under the normal pacing rules and checks.

The route is a breadth-first search over open exits from the vessel's room, limited to 10,000 rooms, over rooms its terrain accepts:

| Vessel kind | Rooms it may route through |
| --- | --- |
| Water based (ship, boat) | watery rooms and seaports |
| Land based or wagon (caravan, cart) | drivable rooms: `Prop_Road` rooms, plains, city, seaport/spaceport, some caves |
| Air flying | outdoor rooms (not indoors) |

It finds the fewest steps, not the quickest by travel time, and does not predict locks or hazards that appear later. The same router serves `MPSAIL ... TO`, `TransportLine` stops and the taxi.

## Vessels: rideables, boardables, ships and caravans

Item field-by-field editing is in [22-items.md](22-items.md#furniture-rideables-and-boardables); mounts that are creatures are in [21-mobs.md](21-mobs.md#rideable-mobs-and-mounts). This section is about how vessels behave and how to build and control them.

### Kinds of vehicle

| Class | Kind | Notes |
| --- | --- | --- |
| `Boat`, `GenBoat`, `GenRideable` (default basis water) | Rideable item | You `MOUNT`/`BOARD` it and ride on it; no interior. Movement commands move the boat and its riders. `GenBoat` adds siege combat. |
| `HorseCart`, `StdRideable` with basis `WAGON` | Rideable item | A cart with rider capacity (HorseCart: 10). It must be pulled: `MOUNT cart horse` hitches it to a creature (see [Riding](#riding-mounts-and-carts)). |
| `GenCageRideable` | Rideable item | A wagon-basis cage for captives. |
| `GenBoardable` (and `StdBoardable`) | Boardable, not navigable | A thing with an interior area you can `ENTER` and leave: a hut on wheels, a barge fixed to a quay, a tower. It does not move on its own. Owned, priced, lockable. |
| `GenNavigableBoardable` | Boardable base class | Adds navigation. Create one of the concrete classes below instead. |
| `GenSailingShip` | Navigable | Water. `SAIL <dir>`, anchor, tender, jump overboard. |
| `GenCaravan` | Navigable | Land. `DRIVE <dir>`, brake. Runs only on drivable rooms (see [Prop_Road](#prop_road)). Ride basis `WAGON`; riders capacity 0 (people enter, not ride). |
| `GenAirShip` | Navigable | Air. `SAIL <dir>`, `RISE`, `DIVE`, tie-downs instead of an anchor. Routes only through outdoor rooms. |
| `GenBalloon` | Navigable (an air ship) | `FLOAT <dir>`, `RISE`, `DIVE`; ticks per move 3 by default (slow). |
| `GenSiegableBoardable` | Boardable with ship-to-ship combat | See [29-combat-and-encounters.md](29-combat-and-encounters.md). |

Helm command names depend on the class (from each class's `findNavCommand`):

| Action | Sailing ship | Caravan | Air ship | Balloon |
| --- | --- | --- | --- | --- |
| Move one step | `SAIL <dir>` | `DRIVE <dir>` | `SAIL <dir>` | `FLOAT <dir>` |
| Keep going in a direction | `STEER <dir>` | `STEER <dir>` | `STEER <dir>` | `STEER <dir>` |
| Series of moves | `COURSE <dir> <dir> ...` (or `SET COURSE ...`) | same | same | same |
| Plot to a place | `COURSE TO <place>` or `SAIL TO`, `DRIVE TO` | same | same | same |
| Stop moving | `LOWER ANCHOR` | `SET BRAKE` | `TIEDOWN` or `TIE DOWN` | same as air ship |
| Start moving | `RAISE ANCHOR` | `RELEASE BRAKE` or `UNSET BRAKE` | `RELEASE TIEDOWNS` (also `ROPE`, `ROPES`, `TIEDOWN`, `TIES`) | same as air ship |
| Climb / descend | not available | not available | `RISE`, `DIVE` | `RISE`, `DIVE` |
| Meet another vessel | `TENDER <other>`, `RAISE <boat>`, `LOWER <boat>` | same | same | same |
| Leave the vessel in water | `JUMP OVERBOARD` | | | |
| Follow another vessel | `FOLLOW <vessel>` | same | same | same |

Steering and tendering must be done from a deck (an outdoor room of the interior); indoors you get "You must be on deck to steer your ship." Direction words aboard a ship are `Foreward` (`FORE`), `Aft`, `Starboard` (`STB`), `Portside` (`PORT`), `Above` and `Below`; caravans say forward, back, right and left. Help topics: `HELP SAILING`, `HELP SHIP_COMBAT`.

### The interior

A boardable is an item plus an interior area (class `StdBoardableShip`). The area belongs to the item: it travels with it, appears in `LIST SHIPS` and is saved with it. At least one interior room should be outdoors (a `WoodenDeck`, for example): that is where players see out, steer and mount siege weapons, and the deck rooms are the ones linked to the outside through the `EXITNAME` exit (default "gangplank"). A docked vessel's outer deck exits lead to the room the vessel sits in; as it moves, the links move too.

To build a ship (the method of the original guides, plus the CoffeeSUD `VESSEL` command):

1. Stand in a water or port room. `CREATE ITEM GenSailingShip` (or `GenCaravan`, `GenAirShip`, `GenBalloon`, `GenBoardable`).
2. `MODIFY ITEM <ship>`: set the name (a name with `[NEWNAME]` lets a buyer rename it), `Moves per Tick` (the top speed), the `OWNER` (empty means nobody owns it), `PRICE`, and the pacing fields below. Add `Prop_HelmRestrict` or behaviors in the Effects and Behaviors fields.
3. Go inside: `VESSEL <ship> ENTER` (builders), or `ENTER <ship>`. You are on its first interior room.
4. Build the rooms with `CREATE ROOM <direction> [locale]`, using ship directions instead of compass ones (the Archon guide names port, starboard, fore, aft, above and below; the engine's names are `Foreward`, `Aft`, `Starboard`, `Portside`, `Above`, `Below`). Build the ship north to south, which is fore to aft. Make at least one room outdoors, use `Prop_ReqCapacity` on deck rooms to limit siege weapons, and add exits from the deck back to the dock room if the vessel is to be sold there.
5. Mob crew belong on the deck; `Sailor` makes them crew (see below).
6. `SAVE`. The interior, the vessel's position and its owner go into the world file.

An NPC vessel shared by several copies must have unique names. CoffeeSUD does this for you (see "Copies" below).

### Vessel stats

Beyond the standard item fields, boardables expose these stats to `MODIFY`, `MPSET` and the catalog (`GenBoardable.getStatCodes`, `GenNavigableBoardable`):

| Stat | Applies to | Meaning |
| --- | --- | --- |
| `HASLOCK`, `HASLID`, `DEFCLOSED`, `DEFLOCKED` | all boardables | Whether the gangplank/door can be closed or locked and its default state. |
| `CAPACITY`, `CONTAINTYPES`, `RESETTIME` | all | Container capacity, accepted contents, door reset ticks. |
| `RIDEBASIS`, `MOBSHELD` | all | Ride basis and rider capacity. Read-only on boardables: the class decides. |
| `AREA` | all | The interior area as XML. Normally edited by building the rooms, not by hand. |
| `OWNER`, `PRICE` | all | Owner name and base price. With an empty `OWNER` anyone may give helm orders. |
| `EXITNAME` | all | Name of the gangplank exit. |
| `PUTSTR`, `MOUNTSTR`, `DISMOUNTSTR`, `STATESTR`, `STATESUBJSTR`, `RIDERSTR` | all | Text used when boarding, leaving, and describing riders. |
| `TICKSPERMOVE` | navigable | Minimum ticks between moves, at least 1. A river barge with `ABILITY=1 TICKSPERMOVE=6` drifts about one room per six ticks. |
| `TICKSPERTURN` | navigable | Minimum ticks between direction changes, at least 1. |
| `WEATHERSPEED` | navigable | `true` (default) lets wind add to the top speed on water and air; `false` ignores weather. |
| `FIXEDINTERIOR` | all | `false` (default): every copy gets its own interior name and room IDs. `true`: a copy keeps the original interior name. Use `true` only for one deliberately named craft. |

The editor shows `ABILITY` of a navigable item as "Moves per Tick". Top speed is that value, plus 1 in windy weather and another 1 where the climate is windy (or a 50% chance of +1 in windy climate on a calm day), unless `WEATHERSPEED` is false or the vessel is a caravan; then scaled by hull condition (`usesRemaining/100`, and 0 below 10%); minimum 1. A vessel with no hull left "won't seem to move".

### Docking and the anchor

A vessel is either docked (sitting in a room with its deck exits linked to it) or not. `LOWER ANCHOR`/`SET BRAKE`/`TIEDOWN` holds it in place and fires `DOCK_PROG` on the vessel, its occupants and the occupants of the room it is in. `RAISE ANCHOR` lets it move. `COURSE`, `STEER` and `SAIL` need the anchor up. The vessel's home port (`homePortID`) is the room it was first docked in; `VESSEL <ship> HOME <room>` changes it.

### Who has the helm

Every helm command asks the vessel's `securityCheck`:

1. A non-player mob with the `Sailor` behavior inside the vessel always passes.
2. If the vessel has `Prop_HelmRestrict` and its mask refuses the character, the character fails.
3. Otherwise standard ownership: anyone with the `CMDITEMS` security flag (builders), anyone if `OWNER` is empty, the owner (or someone with legal privileges on the property), or a wearer of a sailor's cap of the owning clan.

So an unowned world vessel can be taken by any passenger. Close that with `Prop_HelmRestrict` (or a transport line's `PASSENGERONLY`).

### Prop_HelmRestrict

Source: [Prop_HelmRestrict](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_HelmRestrict.java). Host: items (vessels).

| Option | Default | Meaning |
| --- | --- | --- |
| `NAVMASK` | none | Zapper mask a character must match to give helm orders. Empty allows everyone. (`MASK` is accepted as a synonym.) `-PLAYERS` keeps player characters off the helm. |
| `MESSAGE` | "The crew will not let you use the helm." | Shown to a refused character. |
| `ONFAIL` | none | A function name called on the vessel's scripts when someone is refused, with the character and the order text. |

```text
NAVMASK="-PLAYERS" MESSAGE="The captain says: hands off the wheel."
NAVMASK="-TATTOO +MUTINEER" ONFAIL=warn_passenger
```

Pass rules: builders with `CMDITEMS`, then non-player mobs with `Sailor` aboard, then the mask. The orders it screens (by first word): `COURSE`, `STEER`, `SAIL`, `DRIVE`, `TENDER`, `RAISE`, `LOWER`, `SET`, `RELEASE`, `BRAKE`, `ANCHOR`. The words `FLOAT`, `RISE`, `DIVE`, `TIEDOWN` and `FOLLOW` are still refused by the underlying security check, but with the generic "You are not permitted to do that." and without `MESSAGE` or `ONFAIL`. `TransportLine` adds `NAVMASK="-PLAYERS"` automatically unless the craft already has `Prop_HelmRestrict`.

### Crew

The `Sailor` behavior (mobs) makes a mob a crew member: it takes part in ship battles, and a `Sailor` aboard is always allowed at the helm even when `Prop_HelmRestrict` refuses passengers. See [24-behaviors-and-masks.md](24-behaviors-and-masks.md#sailor). A transport line steers its own vessel without a crew mob.

### Copies and unique interiors

When a vessel is copied (`COPY`, the item's `copyOf`, a catalog spawn or a catalog template), the copy's interior area and room IDs are renamed to a fresh unique name derived from the vessel's name (`Travel.newInteriorName`, such as `the_Gull_v3`) unless `FIXEDINTERIOR` is true. The old engine had every copy share room IDs, which confused room lookups. A hand-built NPC ship that you copy five times therefore gets five separate decks. `VESSEL <ship> RENAME <new interior name>` renames an interior deliberately; it must be unused.

### Builder tools for vessels

| Command | Effect |
| --- | --- |
| `VESSEL STATUS <name>` | Print a vessel: where it is and where it is docked, facing, anchor, course, top speed and pace, hull points and percentage (and "sunk"), owner and home port, the behaviors on the vessel, its decks and every mob aboard (with their state, as `STAT BEHAVIORS` shows it), crew (monsters not following anyone) and passengers. |
| `VESSEL <name> ENTER` | Move yourself to the vessel's first interior room and look. |
| `VESSEL <name> RENAME <interior name>` | Rename the interior. Refused if empty or already an area name. Save the room to keep it. |
| `VESSEL <name> HOME <room>` | Set the home port to a room. Save the room to keep it. |
| `LIST SHIPS` | Every active ship, with its location, registration, anchor (`up`/`down`), course and owner. |
| `LIST INSTANCES` | Every instance area (see [Instances](#instances-resets-and-planes)). |

`VESSEL` is available in builder mode, in a DEV game, or with the `CMDAREAS` security flag. A name matches exactly first, then as a substring of a vessel's name. The syntax is `VESSEL STATUS <name>` or `VESSEL <name> ENTER|RENAME <text>|HOME <room>`; the action word comes after the name. With too few words it prints the usage line.

### Tender, follow and siege

`TENDER <other ship>` offers a gangplank to another vessel in the same room; when the other captain tenders back, crews walk between them. Both must be docked, neither in combat; sailing away or fighting disconnects them. A small boat that tenders your ship can be hoisted aboard with `RAISE <boat>` and put back with `LOWER <boat>`. `FOLLOW <ship>` makes the vessel trail another. Ship-to-ship combat, `AIM` and siege weapons are covered in [29-combat-and-encounters.md](29-combat-and-encounters.md).

## Transport lines and timetables

Source: [TransportLine](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/TransportLine.java), [Timetable command](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Timetable.java). Reference entry: [24-behaviors-and-masks.md](24-behaviors-and-masks.md#transportline).

`TransportLine` makes a real vehicle run a scheduled route through real rooms, stopping to take on passengers. It goes on:

- a navigable vessel (`GenSailingShip`, `GenCaravan`, `GenAirShip`, `GenBalloon`): the behavior plots a course with the same router as `COURSE TO` and the vessel sails it;
- a rideable item (a `GenRideable` coach with land basis): the behavior walks it one room at a time, paying `Prop_TravelTime` step time between steps;
- a mob driver: if the mob rides something, the thing it rides is the vehicle; otherwise the mob itself walks the route.

Because it routes through ordinary rooms, the stops need not be adjacent, and all the normal terrain, door and `Prop_Road` rules apply. If no route exists, the line waits and tries again after the next stop delay.

### TransportLine options

Write all options on one behavior line.

| Option | Default | Meaning |
| --- | --- | --- |
| `STOPS` | required | At least two stops, separated by semicolons inside quotes: room IDs, port names, or area names. A stop the engine cannot resolve is a `WORLD CHECK ROUTES` error. |
| `DEPART` | none | Comma list of game hours of the day (`0` to hours-in-day minus 1) at which the vessel leaves a stop, for example `6,12,18`. The next listed hour after arrival is used. Replaces `WAIT`. |
| `WAIT` | 10 | Ticks to wait at each stop when `DEPART` is not given. (A tick is about 4 seconds of game clock.) |
| `ROUND` | `LOOP` | `LOOP` cycles through the stops and returns from the last to the first; `BACK` reverses at each end. |
| `FARE` | 0 | Boarding price. |
| `FARES` | none | Comma list `stop:price,...` giving a different price for boarding at a particular stop. The stop text must match the entry in `STOPS` (case ignored). |
| `PARTY` | `LEADER` | `LEADER` (the leader pays for the nearby party) or `EACH`. See [Fares](#fares-and-ticket-takers). |
| `PETS` | 0 | Price for each creature that is not a player character, when the leader pays. |
| `CARGO` | 0 | Price per 100 pounds of load carried, when the leader pays. |
| `PASSENGERONLY` | `true` | Adds `Prop_HelmRestrict NAVMASK="-PLAYERS"` to a navigable vehicle that has no helm restriction of its own, so passengers cannot steer it. |
| `SIGN` | none | Name of a readable item in each stop room; its text is set to the current timetable every tick. |
| `BOARDCALL` | none | Shown outside the vehicle, and on every interior room, when it is ready to board at a stop. |
| `DEPARTMSG` | none | Shown when it sets out. |
| `ARRIVEMSG` | none | Shown when it reaches a stop. |

In the three messages `$stop` is the stop the vehicle is at (or leaving) and `$next` is the stop it is heading for. Port names are used where a stop has a `Prop_Port`; otherwise the room title.

```text
STOPS="Port Azure;Saltmarsh;Gullrock" DEPART=6,12,18 ROUND=BACK FARE=2 PARTY=LEADER PETS=5 CARGO=1 PASSENGERONLY=true BOARDCALL="Boarding for $next." DEPARTMSG="The Gull leaves $stop." ARRIVEMSG="The Gull arrives at $stop." SIGN="a timetable board"
```

What the behavior installs while it is on the host (both are temporary and not saved):

- a `Prop_TicketTaker` with the current stop's fare, `PARTY`, `PETS` and `CARGO`, unless the vehicle already has one of its own (an authored property always wins);
- a `Prop_HelmRestrict` as described for `PASSENGERONLY`.

They are removed when the behavior is removed or the host changes vehicle.

### How a line behaves

- On its first tick the line finds which stop it is standing at (otherwise it counts as being at stop 1) and starts its wait. Starting at an end stop with `ROUND=BACK` sets the right direction.
- At each stop it waits for `WAIT` ticks or until the next `DEPART` hour on the stop room's local clock; then it plots a course to the next stop, announces `DEPARTMSG` and goes.
- Vessels travelling through rooms with `Prop_TravelTime` spend that time between steps; a line announces arrival only after the last step's time has passed.
- On arrival a navigable vessel's anchor is set and its course cleared; the line announces `ARRIVEMSG`, updates the fare for the new stop, resets the wait and announces `BOARDCALL`.
- A line does not move a player who has not boarded; a party that wants to travel boards at a stop, pays, and uses `WAIT UNTIL ARRIVAL` or `TIMETABLE` while it rides.

### TIMETABLE

`TIMETABLE [line name]` prints every line and moving route (matched by the host's name or by the text) in the form:

```text
the Gull: at Port Azure; next Saltmarsh, departure hour 12
the Gull: travelling to Saltmarsh; next Saltmarsh
the Gull: at Saltmarsh; next Gullrock, departs in 7 ticks
```

A `SIGN` item at each stop shows the same text when read. A line with fewer than two stops reports "Transport line needs at least two stops." On a ferry or coach, `WAIT UNTIL ARRIVAL` (also `STOP` or `DOCK`) waits for the next stop (`HELP WAIT`).

### Making a ferry with a timetable (summary)

1. Make the vessel (see [Vessels](#vessels-rideables-boardables-ships-and-caravans)) and place it at its first quay. Leave `OWNER` empty or give it a world owner name.
2. Give each quay room a port: `Prop_Port NAME="Port Azure"`. Make a readable board item at each quay named "a timetable board".
3. Add the `TransportLine` behavior to the ship (MODIFY ITEM, Behaviors).
4. `TIMETABLE` to check; `WAIT` until the departure hour; `LIST SHIPS` and `VESSEL STATUS` to inspect.
5. A worked version is [Example 1](#example-1-a-ferry-with-a-timetable).

## Fares and ticket takers

Source: [Prop_TicketTaker](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_TicketTaker.java), [Travel.fare](../../CoffeeMud/com/planet_ink/coffee_mud/core/Travel.java). Reference entry: [23-properties-reference.md](23-properties-reference.md#prop_tickettaker).

CoffeeSUD has one fare model, shared by `Prop_TicketTaker`, `TransportLine`, `TaxiBehavior`, `DelayedTransporter` and journeys: each member of the party is either a player character (a human-controlled or party character) or something else (a mount, a pet, a hireling).

| Setting | Meaning |
| --- | --- |
| `PARTY=LEADER` | The leader pays one total for everyone, and the others board without being stopped. |
| `PARTY=EACH` | Each player character pays their own fare. |
| `PETS=<price>` | Under LEADER, the price of each member who is not a player character. |
| `CARGO=<price>` | Under LEADER, a price per 100 pounds of load each member carries (weight above their own body weight, rounded up per member). |
| `PERHEAD` | (journeys, delayed transporters) whether `FARE` is charged for each player character or once for the party. |

Charging is all-or-nothing. The engine first works out every payer's charge. If anyone cannot afford it, the leader is told "X cannot afford the fare." and nobody moves and nothing is collected. Money is only taken after every passenger has passed every entry check.

### Prop_TicketTaker

Hosts: mobs, items. The property goes on a vehicle (a ship, coach, cart), on a mount, or on a mob standing at a gangway.

Parameters (the first three forms are the original ones):

| Parameter text | Meaning |
| --- | --- |
| `2` | A plain number: the boarding fare. |
| `a red ticket` | Any text without `=`: the name of a ticket item each boarder must hold. The ticket is taken. |
| `FARE=2 PARTY=LEADER PETS=5 CARGO=1` | The CoffeeSUD form. `FARE` default 0; `PARTY` default `EACH`; `PETS` and `CARGO` default 0. |

It fires on `MOUNT`, `SIT`, `ENTER` (boarding) and `SLEEP` messages by player characters (monsters board free) when the host is the vehicle being boarded, or the mount a cart is hitched to, or when the host is a mob standing on or beside the vessel's gangway. Walking between a vessel's own rooms never charges again. The receipt is forgotten when the character steps off a boardable vessel into another area (and, with `PARTY=LEADER`, when they dismount), so a return trip pays again. For `PARTY=EACH` on a plain rideable that is not a boardable (a cart), the character stays remembered by name in a bounded list, so a second ride may be free; prefer `PARTY=LEADER` for carts.

- With `PARTY=EACH`, a character pays once and is remembered by name. Money is judged in the host's currency (the mob's, if the host has none). A character who cannot pay is refused with "You'll need X to board."
- With `PARTY=LEADER`, the first party member to board triggers the charge on the leader, which must be in the same room ("Your party leader must be here to pay the fare."). Everyone in the party in that room is then marked as paid for that vehicle.

To put a ticket taker on a gangplank NPC: add `Prop_TicketTaker` with `FARE=3 PARTY=LEADER` to a mob who stands inside the ship's entry room. The same on a ship item charges anyone boarding the ship item.

## Taxis and the Concierge

Source: [Concierge](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Concierge.java), [TaxiBehavior](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/TaxiBehavior.java). Reference entries: [Concierge](24-behaviors-and-masks.md#concierge), [TaxiBehavior](24-behaviors-and-masks.md#taxibehavior).

`Concierge` is a guide for hire. A player says a destination to the concierge (a mob, or an item or room it is attached to); the concierge quotes a price; the player pays by giving, dropping or putting the coins; and the concierge tells the way, leads the player (`MOBILE=true`), or opens a temporary portal (`PORTAL=true`). `TaxiBehavior` is the same service where the vehicle itself drives the party there and then returns.

### Parameter list

Both read a semicolon-separated list. The grammar is:

- A bare number sets the base price (the last price in the list is the default for any place not listed).
- `PLACE=price` gives a price for a room ID (or room name) or area name. When any places are listed, only listed places (and areas by name) are served.
- `FLAG=value` entries below. A flag is true if its value starts with `t`.

| Entry | Meaning |
| --- | --- |
| `PERROOM=n` | Extra price per room along the trail to an unlisted place. Concierge default 0; TaxiBehavior default 1. |
| `MAXRANGE=n` | How far (in rooms) to search for the destination. Default 100. |
| `GREETING="text"` | Said to arrivals. Default "Need directions?..." (the taxi: "Need a lift? If so, come aboard."). |
| `ENTERMSG="text"` | Said when someone mounts or enters the host. |
| `TALKERNAME="name"` | Name the host speaks under when it is an item or room; for a vehicle, the name of a rider to speak as. |
| `CLAN="name"` | Serve only places controlled by that clan; the word `home` means the clan's recall room. |
| `MOBILE=true` | Lead the customer to the destination on foot (Concierge on a mob). |
| `PORTAL=true` | Open a short-lived portal item to the destination instead of giving directions. |
| `AREAONLY=true` | Stay inside the host's own area. |
| `NOCLIMB`, `NOCRAWL`, `NOWATER`, `NOAIR`, `NOLOCKS`, `NOHOMES`, `NOINDOOR` | `=true` removes those kinds of rooms from the search. |
| `PARTY`, `PETS`, `CARGO` | Read by `TaxiBehavior` only (see below). They are accepted and ignored by `Concierge`. |

Players can narrow a request by ending it with `noclimb`, `nodoors`, `noswim`, `noprivate`, `nofly`, `nolocks` or `nocrawl`. A request for a place that is not served gets "I'm sorry, I don't know where 'X' is."

```text
Gullrock#0=7;Saltmarsh=5;GREETING="Where to, friend?"
```

### Taxis

`TaxiBehavior` goes on a rideable item or a mob that can carry riders. The flow:

1. The customer boards (mounts) the vehicle. It greets them and asks where.
2. The customer says a destination; the vehicle quotes the price. Paying (give, drop or put coins) starts the trip.
3. The vehicle drives itself along an outdoor, open path (indoor rooms and closed doors end the trip: "This is as far as I can go."). While it drives it cannot be tracked.
4. At the destination: "We're here. Best of luck!" The new riders are put off and the vehicle drives back to where it started.

The party options of the taxi, in the same list:

```text
PARTY=LEADER;PETS=5;Gullrock#0=7
```

With `PARTY=LEADER` the quote is the total for the party in the room (7 per player character, `PETS` per other creature, plus `CARGO` per 100 lb), the whole party boards together and all are put off together. If the vehicle cannot seat them all, the fare is returned ("The whole party cannot board."). `PARTY=EACH` (default) keeps the single-rider rule of the original taxi.

The Concierge keeps quoted trips in its saved state, so a quote made before a save is honored when the character comes back.

## DelayedTransporter

Source: [DelayedTransporter](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/DelayedTransporter.java). Reference entry: [24-behaviors-and-masks.md](24-behaviors-and-masks.md#delayedtransporter).

`DelayedTransporter` whisks anyone who stays in a room (or with a mob or item) to another room after a delay. It makes standing stones, druid circles and transit rooms. It is a ticking behavior, so the delay is in ticks, not game hours.

```text
min=3 max=3 chance=100 PARTY=LEADER PERHEAD=true FARE=2 DEST=ORDER LOOK=true MASK="-TATTOO +GROVEFRIEND" DEPARTMSG="The stones hum." ARRIVEMSG="You emerge beside the moonwell.";Moonwell#0;Grove#2
```

The text before the first semicolon is options; every later semicolon-separated entry is a destination room ID. Do not use semicolons inside the messages (use the unquoted option text only up to the first `;`).

| Option | Default | Meaning |
| --- | --- | --- |
| `min`, `max` | 5, 5 | Ticks a character must have been present before it can go (`min`) and the tick after which it always goes (`max`). |
| `chance` | 100 | Percent chance per tick, between `min` and `max`. |
| `DEST` | `RANDOM` | `RANDOM` picks a destination from the list; `ORDER` cycles through it in order (the position is saved). |
| `PARTY` | false | `true` or `LEADER`, or `EACH`: the group is carried together when the first member's time is up, so nobody is left behind or sent ahead alone. Without `PARTY`, the character and those following it go. |
| `PERHEAD` | false | Charge `FARE` for each eligible character; otherwise `FARE` is a flat charge. |
| `FARE` | 0 | Price. Charged to the leader, or each player with `PERHEAD` and `PARTY=EACH`. |
| `MASK` | none | Zapper mask of who is carried. A leader that does not match carries no one. |
| `LOOK` | false | Show the arrival room to everyone after the move. |
| `DEPARTMSG`, `ARRIVEMSG` | none | Shown in the old and the new room. |
| `ENTRY` | `FORCE` | `FORCE` teleports regardless of exits and entry properties (the traditional behavior). `CHECK` runs the leave and enter checks of every traveller first (`Prop_ReqEntry`, closed doors, no-teleport properties) and moves nobody if one refuses. |

Fares and entry refusals are settled before anyone moves. If a destination is missing, travellers are told they are "whisked nowhere at all". Each character's ticks present are saved with the destination position.

## Moving rooms

Source: [MovingRoom](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/MovingRoom.java), [RouteStops](../../CoffeeMud/com/planet_ink/coffee_mud/core/RouteStops.java), [movingroom.xml](../../CoffeeMud/resources/movingroom.xml). Reference entry: [24-behaviors-and-masks.md](24-behaviors-and-masks.md#movingroom).

A moving room is one real room (a lift, a subway car, a bus) that visits stop rooms. While it is at a stop, a temporary exit links the car and the stop room; while it is travelling the link is gone and the descriptions say so. Passengers simply walk in and out.

Parameters:

| Option | Default | Meaning |
| --- | --- | --- |
| `ROUTE` | none | Either route XML in quotes, or a file name (relative to `world/routes/`, `routes/` prefix allowed). A value that starts with `<` is XML. |
| `LINE` | the file's root tag | The XML section to use when the file holds several routes. |
| `WAIT` | 10 | Ticks the car waits at a stop. |
| `TRAVEL` | 10 | Ticks the car takes between stops. |
| `DEPART` | none | Game hours of day. When set, the car leaves a stop at the next listed hour instead of after `WAIT`. |
| `SIGN` | none | Name of a readable item in each stop room that shows the line's position. |
| `DISPLOC` | the XML's `DISPLOC` | `true` appends the current timetable text to each stop's overlay. |

```text
ROUTE=deepdelve-lift.xml WAIT=5 TRAVEL=10 DEPART=6,12,18 SIGN="a brass plate"
```

The legacy form still reads the shared file `resources/movingroom.xml`: `min=3 max=3 chance=100;SUBWAY;3;10` (section name, wait ticks, travel ticks).

### Route XML

```xml
<LIFT>
  <STOPS><STOP1>Mine#0</STOP1><STOP2>Mine#4</STOP2><STOP3>Mine#9</STOP3></STOPS>
  <NORMALDIRECTION>
    <TRAVELDIRECTION>down</TRAVELDIRECTION>
    <DOORSDIRECTION>up</DOORSDIRECTION>
    <INSIDE>
      <ARRIVALINFO>The lift stops at $disproom and the gate opens to the $inopendir.</ARRIVALINFO>
      <DEPARTINFO>The gate closes and the lift descends.</DEPARTINFO>
    </INSIDE>
    <OUTSIDE>
      <ARRIVALINFO>The lift arrives.</ARRIVALINFO>
      <DEPARTINFO>The lift drops out of sight.</DEPARTINFO>
    </OUTSIDE>
  </NORMALDIRECTION>
  <REVERSEDIRECTION> ...same fields, for the return trip... </REVERSEDIRECTION>
  <ROOMDESCRIPTIONS>
    <NORMALDIRECTION>
      <INSIDE><DOOROPENED>A gate leads to $disproom.</DOOROPENED><DOORCLOSED>The lift is moving $traveldir.</DOORCLOSED></INSIDE>
      <OUTSIDE><DOOROPENED>A lift is waiting here.</DOOROPENED><DOORCLOSED>The shaft is empty.</DOORCLOSED></OUTSIDE>
    </NORMALDIRECTION>
    <REVERSEDIRECTION> ...same... </REVERSEDIRECTION>
  </ROOMDESCRIPTIONS>
  <LINEPRINTNAME>Mine Line</LINEPRINTNAME>
  <ROOMPRINTNAME>Mine car</ROOMPRINTNAME>
  <DISPLOC>true</DISPLOC>
</LIFT>
```

Rules:

- `STOP1`, `STOP2`, ... must be consecutive from 1 with no gaps or duplicates, and there must be at least two. Any problem is logged and the route is empty. The stop rooms must exist when the behavior starts.
- `DOORSDIRECTION` is the direction the car's door opens, seen from inside. The stop gets the opposite direction. Neither the car nor the stop room may already have an exit in that direction: the engine will not overwrite an authored exit, and the car then simply has no door.
- `$disproom` is the stop's room title (the current stop when open, the next stop when moving); `$inopendir` the door direction from inside; `$outopendir` the opposite; `$traveldir` the current travel direction.
- The route goes forward through the stops and then back; ends use the `REVERSEDIRECTION` block.
- Route files live under `world/routes/` and are part of the world (`SAVE` publishes, `WORLD CHECK ROUTES` validates).

### Overlay: what never reaches the world file

The links between the car and the stops, and the changing description text, are runtime overlays (`Travel.temporaryLink` and the `Prop_TransitView` effect). They are not saved with rooms and not written by room export, so a builder saving a stop room does not bake a door into it. Removing the behavior, or reloading the world, removes them. The route position (current stop, direction, status, remaining wait) is saved with the game.

`TIMETABLE` and `SIGN` show where the car is.
