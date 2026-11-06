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

The CoffeeSUD travel properties and behaviors (`Prop_TravelTime`, `Prop_Road`, `Prop_Port`, `Prop_Journey`, `Prop_Waypoint`, `Prop_HelmRestrict`, `TransportLine`, `Nanny`, `MovingRoom`, `DelayedTransporter`, `TaxiBehavior`, the new `Prop_TicketTaker` options) read `KEY=value` options. The properties use one shared tokenizer (`AuthoringOptions`); the behaviors use the equivalent `CMParms` readers. The rules that matter in practice are the same:

- Options are separated by white space or a semicolon. Keys are case-insensitive. Values are case-sensitive unless a table says otherwise.
- A value containing spaces, semicolons or equals signs goes in double quotes: `NAME="Port Azure"`. A literal quote inside a quoted value is `\"`, a literal backslash `\\`, a literal semicolon `\;`.
- Booleans are `true` or `false`. Numbers may be fractional where the tables say so.
- A bare word with no `=` is a flag.
- Because `;` separates options, list values that themselves contain semicolons are quoted: `STOPS="Pier#0;Quay#3"`, `EVENTS="10%:storm;5%:LOAD MOB pirate"`.

Two behaviors keep an older syntax in front of the options. `DelayedTransporter` is `min=.. max=.. chance=.. [options] ; destination ; destination ...` (the options come before the first semicolon, so do not put a semicolon inside its messages). `Concierge` and `TaxiBehavior` are a semicolon list of `price`, `place=price` and `FLAG=value` entries. Both are described in their own sections.

`WORLD CHECK ONLY=routes` and `WORLD CHECK ONLY=ids` (see [02-world-workflow.md](02-world-workflow.md)) validate these options: unknown keys, bad enums, missing `DEST`/`DURATION`, missing stop rooms, missing route files, ambiguous waypoint names and duplicate waypoint IDs.

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

Hosts: rooms, exits, items, mobs. `DEST` and `DURATION` are required; `WORLD CHECK ONLY=routes` and the property editor refuse a definition without them.

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
| `STOPS` | required | At least two stops, separated by semicolons inside quotes: room IDs, port names, or area names. A stop the engine cannot resolve is a `WORLD CHECK ONLY=routes` error. |
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
- `PLACE=price` gives a price for a room ID (or room name) or an area name. Listed places are matched first; any other place is still found by area name or by searching within `MAXRANGE` rooms, at the base price plus `PERROOM` for each room of the trail.
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
| `FARE` | 0 | Price. The leader pays for the group (a flat price, or per head with `PERHEAD=true` and `PARTY=LEADER`). With `PERHEAD=true` and no `PARTY` setting, or `PARTY=EACH`, each player character pays their own. |
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
- Route files live under `world/routes/` and are part of the world (`SAVE` publishes, `WORLD CHECK ONLY=routes` validates).

### Overlay: what never reaches the world file

The links between the car and the stops, and the changing description text, are runtime overlays (`Travel.temporaryLink` and the `Prop_TransitView` effect). They are not saved with rooms and not written by room export, so a builder saving a stop room does not bake a door into it. Removing the behavior, or reloading the world, removes them. The route position (current stop, direction, status, remaining wait) is saved with the game.

`TIMETABLE` and `SIGN` show where the car is.

## Riding, mounts and carts

Riding is the base of both mounts and vehicles. Hosts: any `Rideable` (a creature such as `GenHorse`, `GenRideable`; an item such as a boat or `HorseCart`). The commands are `MOUNT`, `BOARD`, `RIDE` and `M` (all the same command), `DISMOUNT`, and `LEAVE`:

| Command | Effect |
| --- | --- |
| `MOUNT horse` | Climb onto a visible rideable in the room (creature or item). |
| `BOARD ship` | Same words; on a boardable vessel `BOARD` runs `ENTER` instead (you go inside, you do not ride on it). |
| `MOUNT cart horse` | Hitch: put an item (or willing/bound creature) onto another rideable. A cart pulled by a horse moves when the horse moves. |
| `DISMOUNT`, `LEAVE` | Get off. |

The ride basis (`RIDEBASIS`, one of `LAND_BASED`, `WATER_BASED`, `AIR_FLYING`, `FURNITURE_SIT`, `FURNITURE_SLEEP`, `FURNITURE_TABLE`, `ENTER_IN`, `LADDER`, `WAGON`, `FURNITURE_HOOK`) says what kind of thing it is and what terrain it can cross; `MOBSHELD` (rider capacity) says how many it carries. The `MOUNTSTR`, `DISMOUNTSTR` and related strings give the verbs. See [21-mobs.md](21-mobs.md#rideable-mobs-and-mounts) and [22-items.md](22-items.md#furniture-rideables-and-boardables).

What riding changes in travel:

- A mounted rider pays no movement points; the mount is carried and moves with them.
- A mounted step uses the `MOUNTED` multiplier of `Prop_TravelTime`; a rider on an item (cart, boat) or a moving vehicle uses `VEHICLE`.
- The party fares count a mount as a party member that is not a player character, so `PETS` applies to it under `PARTY=LEADER`.
- Scripts can hitch and unhitch with `MPHITCH` and `MPUNHITCH`; the engine refuses to create a riding loop (a cart cannot ride what rides it).

Practical setups:

- A horse and cart for hire: a `GenHorse` with `Prop_TicketTaker FARE=5 PARTY=LEADER`, or a `GenRideable` coach with `TransportLine` for a scheduled route.
- A taxi: `TaxiBehavior` on a rideable item or mob, see above.
- A wagon train: `GenCaravan` plus `Prop_Road` on the road rooms.

## Stables

Source: [Nanny](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Nanny.java), [Stable command](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Stable.java). Reference entry: [24-behaviors-and-masks.md](24-behaviors-and-masks.md#nanny).

A stable keeper is a mob with the `Nanny` behavior. It keeps mounts, wagons, boats and children (whatever `WATCHES` lists) safe while the owner is away. CoffeeSUD adds explicit commands and sister stables that share custody records.

### Nanny options

| Option | Default | Meaning |
| --- | --- | --- |
| `RATE` | 2.0 | Fee per game hour per creature. |
| `NAME` | `nursery` | Name of the place, used in the keeper's speech and in `STABLE LIST`. |
| `WATCHES` | `Babies,Children` | Comma list of what the keeper takes. Words (abbreviated to three letters): `Babies`, `Children`, `Mounts`, `Wagons`, `Cars`, `Boats`, `AirCars`, `Followers`. |
| `NETWORK` | none | Name of a stable network. Keepers with the same network name are sister stables. |
| `SENDFEE` | 0 | Extra fee for retrieving a mount at a stable other than the one that holds it. |

```text
RATE=2 NAME="Port Azure Livery" WATCHES="mounts,wagons" NETWORK=ROYAL_STABLES SENDFEE=5
```

### Using a stable

Two ways work together.

- **Passive** (the original behavior): when a party that owns watched creatures enters the keeper's room, the keeper offers to hold them. Once the owner leaves, the creatures stay in the keeper's care and cannot be attacked or decay. On return the keeper quotes the bill; the player pays by giving coins to the keeper and the creatures follow again.
- **Explicit** (CoffeeSUD): at the keeper, type

```text
STABLE horse
STABLE LIST
RETRIEVE horse
```

`STABLE <name>` leaves a mount or vehicle that is yours in the keeper's care. It must be of a kind in `WATCHES`, you must be its owner, and nobody else may be riding it. `STABLE` with no name, or `STABLE LIST`, lists your animals in this network, each with the stable it is at and its current bill. `RETRIEVE <name>` collects one, paying keep (hours kept, rounded up, times `RATE`, less any earlier payment) plus `SENDFEE` if it is held at a different sister stable. It refuses for another owner ("No mount of yours by that name is in this stable network."), when you cannot afford it, or when the mount cannot enter the room. The keeper also understands `stable`, `retrieve` and `list` when spoken in the room. The ranger skill that stables a mount in the wild is `STABLEMOUNT`.

Records (owner, drop-off time, payments) are saved with the keeper (see [Persistence](#persistence)). Give each keeper in a network the same `NETWORK` text and a different `NAME`.

## Waypoints and fast travel

Source: [Prop_Waypoint](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Waypoint.java), [Waypoints](../../CoffeeMud/com/planet_ink/coffee_mud/core/Waypoints.java), [TravelCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/TravelCmd.java), [AuthorIds](../../CoffeeMud/com/planet_ink/coffee_mud/core/AuthorIds.java). Reference entry: [23-properties-reference.md](23-properties-reference.md#prop_waypoint).

A waypoint network is a set of `Prop_Waypoint` stones (or rooms) with the same `NETWORK` name. A character can travel from a waypoint they are at to any other waypoint of the network that they have discovered. Discovery is remembered per character or shared per profile.

### Prop_Waypoint options

Hosts: rooms and items. An item must lie in a room (or be carried, to act as the departure point); destinations are looked up among rooms and the items lying in rooms.

| Option | Default | Meaning |
| --- | --- | --- |
| `NETWORK` | `DEFAULT` | Network name (case-insensitive). |
| `NAME` | the host's name | Destination name shown in `TRAVEL` and typed after it. Names within a network should be unique; duplicate names make `WORLD CHECK ONLY=routes` warn. |
| `ID` | derived | A stable author ID. Discovery is stored under it, so moving or renaming the stone never makes it a new place or loses discovery. IDs are scoped to the network and duplicates in one network are an error. Without `ID` a room waypoint is keyed by its room ID, and an item waypoint by a hidden saved identity the engine attaches to it. |
| `ALIASES` | none | Comma list of old IDs, still recognized as the same waypoint after you change `ID`. |
| `LEGACYKEYS` | none | Comma list of old saved discovery tokens to honor. |
| `DISCOVER` | `ENTER` | How a character finds it: `ENTER`, `TOUCH`, `RITUAL`, `QUEST`. See below. |
| `SCOPE` | `CHARACTER` | `CHARACTER`: each character discovers separately. `PROFILE`: discovery is shared by every character of the player's profile. |
| `COST` | `moves 0` | `mana N`, `moves N` (or `movement`), or `gold N` (or `money`) charged to the leader once per trip. |
| `TIME` | 0 | Game hours the trip takes (the clock advances through the scheduler). |
| `MASK` | none | Zapper mask. Characters who do not match cannot discover, see or use it. |
| `MSG` | none | Text shown to the travellers on arrival. |
| `RITUAL` | none | With `DISCOVER=RITUAL`, the name of a script function (`FUNCTION_PROG` or JavaScript) that `TRAVEL ATTUNE` calls. It passes unless the function returns `false`, `0` or `CANCEL`, or does not exist. |
| `QUEST` | none | With `DISCOVER=QUEST`, the quest that must have been won by the character. |

```text
NETWORK=ELVEN NAME="Mirewood Stone" ID=mirewood-stone DISCOVER=TOUCH SCOPE=PROFILE COST="mana 20" TIME=1 MASK="-TATTOO +ELVEN_ATTUNEMENT"
```

### Discovery

| `DISCOVER` | How a character discovers it |
| --- | --- |
| `ENTER` | Walking into the waypoint's room. Also `TOUCH <stone>`, `USE <stone>` and `TRAVEL ATTUNE`. |
| `TOUCH` | `TOUCH <name>` or `USE <name>` at the stone (the stone can be named, or the waypoint name used), or `TRAVEL ATTUNE`. |
| `RITUAL` | `TRAVEL ATTUNE`, which first runs the `RITUAL` function if named. An undefined function leaves the ritual "incomplete". |
| `QUEST` | `TOUCH`, `USE` or `TRAVEL ATTUNE`, once the character has won `QUEST`. |
| (script) | A script or Java code may award discovery directly with `Waypoints.discover(character, property, true)`, bypassing the mode but not the `MASK`. |

Only player characters can discover. Discovery writes a tattoo on the character (or on the profile for `SCOPE=PROFILE`), shown to nobody. On a first discovery the character sees "You attune to Name (NETWORK)."

### Travel

At a waypoint you are attuned to:

```text
TRAVEL
TRAVEL Highpeak Stone
TRAVEL ATTUNE
TOUCH Mirewood Stone
```

| Command | Effect |
| --- | --- |
| `TRAVEL` | List the discovered destinations of the network of every waypoint here. Undiscovered ones are not listed, and neither are ones the character's `MASK` excludes. |
| `TRAVEL <name>` | Take the party to that destination. |
| `TRAVEL ATTUNE` | Try to discover the waypoints here (checks mask, ritual, quest). |
| `TOUCH [name]`, `USE [name]` | Touch or use a waypoint here; this discovers it for `TOUCH`/`ENTER`/`QUEST` modes and then lists destinations. |

`TRAVEL <name>` checks, in order: no journey is in progress; this waypoint is discovered and passes its mask ("You must attune to this waypoint first."); the destination is discovered ("You have not discovered that destination in this network."); it is not here ("You are already there."); nobody in the party is fighting; the `COST` text is valid; the leader can afford it. The party is then moved together after every member has passed leave and enter checks; only then is the cost paid, `MSG` shown and `TIME` game hours advanced. A failed check changes nothing.

The "here" waypoints are the room's own, visible items in the room, and items the character carries. Destinations are rooms with a waypoint and items with one that lie in rooms (not inside containers or inventories).

To make a fast-travel network:

1. In each location add `Prop_Waypoint` to a stone item (or the room): same `NETWORK`, a distinct `NAME`, a distinct `ID`.
2. Choose `DISCOVER` and `SCOPE`, and a `COST` and `TIME`.
3. `WORLD CHECK ONLY=routes,ids` to catch duplicate IDs and names.
4. Test: attune, `TRAVEL`, travel with a companion, travel with too little mana, rename a stone and keep its ID.

## Instances, resets and planes

Many places should be fresh each visit, private to the party, scaled to its level, or reset after the party has been away. Four mechanisms:

| Need | Mechanism |
| --- | --- |
| A private copy of an area per party | `InstanceArea` on the area (default: copy mode) |
| The real area, rescaled to the party | `InstanceArea` with `INPLACE=true` |
| A fresh area when the party returns | `ResetWhole` (optionally with `ONREENTRY`/`MINAWAY`) |
| A different dungeon each time (or the same, seeded) | `StdAutoGenInstance` areas, see [12-generation.md](12-generation.md) |
| An alternative reality of an area | Planes (`StdPlanarAbility`) |

### InstanceArea

Source: [InstanceArea](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Misc/InstanceArea.java), types in `resources/skills/areainstancetypes.txt`, reference entry [23-properties-reference.md](23-properties-reference.md#instancearea).

`InstanceArea` is an Ability (property) that goes on an area. When a player character crosses into the area from outside, the party is moved into a private copy of it (a `SubThinInstance` area named `<n>_<Area Name>`), whose mobs and items are adjusted by the options. The same group entering again goes to the same copy while it exists. It can also be given to a mob, an item or a room (with `AREAMATCH` naming the areas it applies to).

The parameter text is either the name of an instance type from `areainstancetypes.txt` (one type per line, written as a quoted name followed by `KEY=value` options, for example `"Triumph" LIKE="Triumph" DISPLAY="..."`; the shipped file defines `Triumph` and its variants, which depend on player factions; an unknown type name is an error), or a list of `KEY=value` options. `ID=` names the type and is required in the inline form.

Options (`InstVar`):

| Option | Meaning |
| --- | --- |
| `ID` | Type name. Required unless the text is just a type name. |
| `LIKE` | Name of another type to inherit every option from. Options given here override. |
| `INPLACE` | `true`: do not copy; scale the real area instead (below). |
| `AREAMATCH` | Comma list of area names the property applies to when it is on a mob, item or room. |
| `DISPLAY`, `DESCRIPTION`, `CATEGORY` | Informational: display name, text, categories. |
| `LEVELADJ` | A formula for each mob's level. `@x1` and `@x3` both evaluate to the instance level (the party's top level; the type-file header calls `@x1` the area's median level, but the code passes the instance level for both), `@x2` the mob's own level, `@x4`/`@x5` area min/max level, `@x6` experience rate plus 1, `@x7` best player faction value. A bare `@x1` or `@x3` puts each mob at the party's level. |
| `ILEVELADJ` | The same for item levels. |
| `ELITE` | Elite level, a number or formula; raises hit points, attack, damage and saves. |
| `PREFIX` | Comma list of strings to prefix mob names with (one chosen at random per instance). |
| `ALIGNMENT` | Numeric alignment for mobs. |
| `MOBCOPY` | How many times each mob is copied. |
| `BEHAVE` | Space list of behaviors (with `(parms)`) added to mobs. |
| `BEHAVAFFID` | `oldID=newID` replacements for behaviors. |
| `ENABLE` | Abilities granted to mobs: `AbilityID(arg)`, a domain, a flag, a type, or `number(x/y)` skills per levels. |
| `MOBRESIST`, `ABSORB` | Resistance and damage absorption arguments (`Prop_Resistance`, `Prop_AbsorbDamage`). |
| `SETSTAT`, `ADJSTAT`, `ADJSIZE`, `ADJUST` | Set or adjust mob stats and size (`Prop_StatTrainer`, `Prop_StatAdjuster`, `height=x weight=y`, `Prop_Adjuster`). |
| `MIXRACE` | A race ID every mob becomes a mix of. |
| `PROMOTIONS` | Names with chance in parentheses for promoted mobs. |
| `ROOMCOLOR`, `ROOMADJS` | Colour code for room titles; adjectives sprinkled into room descriptions. |
| `REFFECT`, `AEFFECT`, `IEFFECT` | Behaviors or properties added to rooms, the instance area, or armor and weapons. |
| `IADJUST` | `Prop_WearAdjuster` arguments for armor and weapons. |
| `WEAPONMAXRANGE`, `BONUSDAMAGESTAT`, `REQWEAPONS` | Combat rules inside the instance. |
| `ATMOSPHERE`, `AREABLURBS`, `HOURS`, `RECOVERRATE`, `FATIGUERATE`, `SPECFLAGS` | Environment rules (`SPECFLAGS`: `NOINFRAVISION`, `BADMUNDANEARMOR`, `ALLBREATHE`). |
| `FACTIONS`, `PLAYFACTIONS` | Factions given to mobs or entering players. |
| `DURATION` | Ticks before the instance times out (`LIST INSTANCES` shows the remaining ticks). |
| `LIMIT` | Entry limits for the group leader: `N/period`, comma separated, for example `1/DAY`. |
| `LEADERREQ` | A mask the leader must match, to choose between types with the same name. |
| `OPPOSED`, `TRANSITIONAL` | Listed in the type file's header but not recognised by `InstanceArea` (they belong to planes): using them logs "Unknown instance var". |
| `ARMORADJ`, `ATTACKADJ`, `SAVEADJ`, `HPADJ`, `DAMAGEADJ` | Accepted names; no effect found in the code (see audit notes). |

`LIMIT` periods are `ticks`, `seconds`, `minutes`, `hours`, `days` or `weeks` (the first letters are enough), measured in fixed milliseconds of the game clock, not calendar days of the game world. A leader who has entered `N` times within the period is refused ("You are not allowed to re-enter this special place right now."), unless a builder (`CMDAREAS`). The counters are stored in the leader's player record.

Unknown options are logged as "Unknown instance var". A missing `ID` is logged too.

```text
ID=CRYPT LEVELADJ=@x3 PREFIX="restless,bone-chilled" DURATION=900 LIMIT=1/DAY ELITE=1
```

Give a mob the tattoo `NOAINST <type name>` in the normal area to keep it from spawning in that instance copy (the type file's header describes this).

Tools: `LIST INSTANCES` shows every instance (parent, class, who it is for, remaining ticks, room count and a room to `GOTO`). The zapper masks `+AREAINSTANCE` and `-AREAINSTANCE` test it.

### In-place scaling

`INPLACE=true` avoids copies. The real area is scaled to the entering leader's level the first time a player enters it (and again whenever `ResetWhole` resets it):

```text
ID=DROWNED_BARROW INPLACE=true LEVELADJ=@x1 PREFIX=weathered
```

- On the first committed entry (`Travel.entering`, after fares and entry vetoes) the whole area's rooms are rebuilt with the instance's adjustments, using the leader's level as the instance level.
- `ResetWhole` resets rooms and then calls the same rebuild for each reset room (using the entering leader, or the last known level), so the returning party meets freshly stocked rooms at its own level.
- Docked vessels, a titled room's contents and dropped gear are kept (`fixingInPlace`).
- The state (`READY`, `LEVEL`) is saved in the campaign, so a save inside restores the half-cleared barrow and the same scaling.
- There is no timer, `LIMIT`, `LIST INSTANCES` entry or private copy: it is the one real area.

### ResetWhole

Source: [ResetWhole](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/ResetWhole.java). Reference entry: [24-behaviors-and-masks.md](24-behaviors-and-masks.md#resetwhole).

`ResetWhole` resets a room or area (every mob and item back to its definition). Hosts: rooms, areas. An area ignores its rooms that carry their own `ResetWhole`.

| Option | Default | Meaning |
| --- | --- | --- |
| a number, or `TICKS=n` | 30000 ms | Reset when no player has been there for that many ticks. |
| `DAYS=n` | none | Reset `n` game days (on the area's calendar) after the party was last there. Used when no `TICKS`. |
| `RLHOUR=h` | none | Reset at a real-clock hour (0 to 23). Means little with pause and turn-based time. |
| `MUDHOUR=h` | none | Reset at a game hour. |
| `EXCEPT="mask"` | none | Mobs and items matching never return once gone (a slain boss or taken loot). Names with spaces go in single quotes: `-NAME +'the Goblin King'`. |
| `MSG="text"` / `RESETMSG="text"` | none | Shown to the first who comes in after a reset. |
| `ONREENTRY` | false | Reset when a party member enters after everyone has left. |
| `MINAWAY=h` | 0 | With `ONREENTRY`, minimum game hours the area must have been empty. |
| `SKIPROOMS="mask"` | none | Rooms matching the mask are left as they are, for example `SKIPROOMS="-LOCATION '+ROOMSTR(Sanctum)'"`. |

Last access, the "away" flag and a pending message are saved. Whether a cleared dungeon should reset on return is a design decision: neither seeding nor saving decides it. `EncounterReset` is a different tool, which resets a boss that the party has walked away from (see [29-combat-and-encounters.md](29-combat-and-encounters.md)).

### Generated instances

An area of class `StdAutoGenInstance` builds a new random layout when entered. Its `SEED` variable controls repeatability: a number (the same dungeon every time), `GAME` (the save's own seed: the same dungeon on every visit of this campaign) or `ENTRY` (a new layout each entry). The seed in use is stored in the area as `GENSEED`. Created layouts and copies made in play are saved with the campaign, so a save inside a generated dungeon restores it. Authoring templates are in [12-generation.md](12-generation.md).

### Planes

A plane is a variant of an area that follows different rules, made by planeshift magic (`Spell_Planeshift`, `Prayer_PlanarTravel` and relatives). Source: [StdPlanarAbility](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/StdPlanarAbility.java), `resources/skills/planesofexistence.txt`.

- The file defines planes, one per line as a quoted name followed by `KEY=value` options (Earth, Air, Fire, Water, Astral, Ethereal, Limbo, Nirvana, Abyss, Nine Hells and others, about 37 entries) with the same style of options as instances (`PREFIX`, `LEVELADJ`, `MOBRESIST`, `BEHAVE`, `REQWEAPONS`, `ATMOSPHERE`, `SPECFLAGS`, `HOURS`, `RECOVERRATE`, `OPPOSED`, `TRANSITIONAL`, `DESCRIPTION` and more; the file header lists each).
- `CAST "PLANESHIFT" nirvana` moves the caster's group to a copy of the current area under that plane's rules, for a time. "To return home, travel to the Prime Material plane."
- The ability `PlanarAbility` (ID `StdPlanarAbility`) on a normal area means "this is a plane": players cannot travel to that same plane from there, and only a few of the plane's attributes (`SPECFLAGS`, `BONUSDAMAGESTAT`, `RECOVERRATE`, `WEAPONMAXRANGE`, `REQWEAPONS`) apply.
- Builders with the `PLANES` security flag edit the list in game: `CREATE PLANE <name>`, `MODIFY PLANE <name>`, `DESTROY PLANE <name>`; `LIST PLANES` shows them. Instances of planes made in play are saved with the campaign.

## Scripting travel

### MOBPROG commands

These commands (all in [27-mobprog-commands.md](27-mobprog-commands.md)) drive vessels from scripts. Multiword arguments use single quotes. Names resolve across the whole world (`Travel.vessel`).

| Command | Syntax | Effect |
| --- | --- | --- |
| `MPSAIL` | `MPSAIL <vessel> TO <room id\|area\|port>` or `MPSAIL <vessel> <dir> <dir> ...` | One-shot course; clears the anchor. Hidden ports are allowed (scripts have no character). |
| `MPANCHOR` | `MPANCHOR <vessel> UP\|DOWN` | Set the anchor (brake, tie-down). |
| `MPMOVEVESSEL` | `MPMOVEVESSEL <vessel> <room id\|port\|area>` | Move and dock it there with no voyage; clears its course and drops the anchor; fires `VESSEL_ARRIVE_PROG`. |
| `MPBOARD` | `MPBOARD <who> <vessel> [interior room id]` | Move `who` and their nearby party aboard (the first interior room, or the named one). |
| `MPHITCH` | `MPHITCH <item> <mount>` | Hitch a rider to a rideable. Both must be in the same room; loops refused. |
| `MPUNHITCH` | `MPUNHITCH <item>` | Unhitch. |

```text
mpboard $n 'the Black Gull'
mpsail 'the Black Gull' TO 'Hidden Cove#1'
mpanchor 'the Black Gull' DOWN
mpmovevessel 'the Black Gull' 'Harbour#2'
mphitch 'a merchant wagon' 'an ox'
mpunhitch 'a merchant wagon'
```

Errors (unknown vessel, no route, loops) are logged to the script error log with "RunTime".

### Functions and values

Documented in [28-mobprog-functions.md](28-mobprog-functions.md). All take a vessel name (or, for `RIDING`/`ABOARD`, a character; for `RIDERS`, a rideable):

| Function | Returns |
| --- | --- |
| `ABOARD(who)` | Name of the vessel the character is inside, or empty. |
| `RIDING(who)` | Name of what the character rides, or empty. |
| `RIDERS(rideable)` | Number of riders. |
| `ISANCHORED(vessel)` | `true` or `false`. |
| `ISDOCKED(vessel)` | `true` if docked. |
| `COURSE(vessel)` | The remaining directions, space separated. |
| `VESSELROOM(vessel)` | Room ID where the vessel is. |
| `SPEED(vessel)` | Top speed (`getMaxSpeed`). |
| `HULL(vessel)` | Hull condition percentage (uses remaining). |

As values: `$%COURSE('the Fix Travel Gull')%`. In JavaScript use the uppercase bridge: `VESSELROOM$("the Gull")` returns a string, `ISANCHORED("the Gull")` a boolean, `HULL$("the Gull")` a string. See [05-javascript.md](05-javascript.md).

### Triggers

| Trigger | When |
| --- | --- |
| `JOURNEY_PROG START\|HOUR\|ARRIVE\|CANCEL\|ALL` | A journey changes stage. |
| `SAIL_PROG` | A helm order (anchor, steer, course, other) is given aboard by someone allowed. |
| `DOCK_PROG` | A vessel's anchor goes down. |
| `VESSEL_ARRIVE_PROG [room id\|area\|ALL]` | A vessel moves into a room (heard aboard). |
| `VESSEL_GREET_PROG` | A vessel arrives where the script's host is. |

Reference: [26-mobprog-triggers.md](26-mobprog-triggers.md). Argument details are in `ScriptEvents`: for `SAIL_PROG` the order kind is `ANCHOR`, `STEER`, `COURSE` or the command word; the room ID or area is matched on the arrival triggers.

## Command reference

| Command | Who | Syntax and effect |
| --- | --- | --- |
| `JOURNEY` | any | `JOURNEY [host\|RESUME\|CANCEL]`, see [Journey commands](#journey-commands). |
| `PORTS` | any | List known ports. |
| `COURSE TO`, `SAIL TO`, `DRIVE TO` | helm | Plot to a port, room ID, area or chart point. |
| `COURSE`, `STEER`, `SAIL`/`DRIVE`/`FLOAT`, `RISE`, `DIVE`, `RAISE ANCHOR`, `LOWER ANCHOR`, `TENDER`, `RAISE`, `LOWER`, `JUMP OVERBOARD`, `FOLLOW` | helm | Vessel control, see [Vessels](#vessels-rideables-boardables-ships-and-caravans). |
| `TIMETABLE` | any | `TIMETABLE [line]`. |
| `WAIT UNTIL ARRIVAL\|STOP\|DOCK` | passenger | Pass time until the vessel arrives, stops, or docks (`HELP WAIT`). |
| `MOUNT`, `BOARD`, `RIDE`, `DISMOUNT`, `LEAVE` | any | See [Riding](#riding-mounts-and-carts). |
| `STABLE`, `RETRIEVE` | any | At a stable keeper. |
| `TRAVEL` | any | `TRAVEL [name\|ATTUNE]` at a waypoint. |
| `TOUCH`, `USE` | any | Activate a waypoint (and other things). |
| `VESSEL` | builder | `VESSEL STATUS <name>`, `VESSEL <name> ENTER\|RENAME <text>\|HOME <room>`. |
| `LIST SHIPS`, `LIST INSTANCES`, `LIST PLANES` | builder | Inventory of vessels, instances and planes. |
| `CREATE\|MODIFY\|DESTROY PLANE` | `PLANES` flag | Edit planes. |
| `WORLD CHECK ONLY=routes`, `WORLD CHECK ONLY=ids` | builder | Validate travel definitions. |

## Worked examples

### Example 1: a ferry with a timetable

Goal: "The Gull" sails Port Azure, Saltmarsh and Gullrock at 6, 12 and 18 o'clock, charging two coins per head, with a board at each quay. Checked by the `t-transport.js` fixture (line, fares, saved progress).

1. **Rooms.** In the area `Azure Coast`, build a strip joined by open east/west exits:

   | Room | Locale | Role |
   | --- | --- | --- |
   | `Azure Coast#1` | `SeaPort` | Port Azure quay |
   | `Azure Coast#2`, `#3` | `WaterSurface` | open water |
   | `Azure Coast#4` | `SeaPort` | Saltmarsh quay |
   | `Azure Coast#5`, `#6` | `WaterSurface` | open water |
   | `Azure Coast#7` | `SeaPort` | Gullrock quay |

   A ship routes only through water and seaport rooms, so the strip has to be continuous.

2. **Ports.** `GOTO Azure Coast#1`, `MODIFY ROOM`, add the effect `Prop_Port` with `NAME="Port Azure"`. Do the same at `#4` (`NAME="Saltmarsh"`) and `#7` (`NAME="Gullrock"`).

3. **Signs.** At each quay `CREATE ITEM GenReadable` and name it `a timetable board` (give it a description; its readable text is filled in by the line).

4. **The ship.** At `Azure Coast#1`:

   ```text
   CREATE ITEM GenSailingShip
   ```

   Then `MODIFY ITEM` on it: set the name to `the Gull`, `Moves per Tick` to 1, `TICKSPERMOVE` to 3, and leave `OWNER` empty. `VESSEL the Gull ENTER` takes you inside; build a `WoodenDeck` and a cabin with `CREATE ROOM` in ship directions. `SAVE` the room.

5. **The line.** `MODIFY ITEM the Gull`, in Behaviors add `TransportLine` with:

   ```text
   STOPS="Port Azure;Saltmarsh;Gullrock" DEPART=6,12,18 ROUND=BACK FARE=2 PARTY=LEADER PETS=5 BOARDCALL="The Gull is boarding for $next." DEPARTMSG="The Gull casts off from $stop, bound for $next." ARRIVEMSG="The Gull ties up at $stop." SIGN="a timetable board"
   ```

6. **Slow the sea (optional).** On the area, add `Prop_TravelTime TIME=20`. A vehicle uses the `VEHICLE` multiplier (0.7 by default), so each sea room then takes 14 game minutes, and the line announces arrival only after that time has passed.

7. **Check it.**

   ```text
   WORLD CHECK ONLY=routes
   TIMETABLE
   VESSEL STATUS the Gull
   ```

   `WORLD CHECK ONLY=routes` proves each stop resolves; `TIMETABLE` should say "the Gull: at Port Azure; next Saltmarsh, departure hour 6" (or the next listed hour).

8. **Ride it.** As a normal character with coins, standing at Port Azure:

   ```text
   BOARD gull
   WAIT UNTIL ARRIVAL
   LOOK
   ```

   The leader pays 2 for each player character plus 5 for each non-player companion, the party goes aboard together, and the line announces departure at the hour. Because `PASSENGERONLY` defaults to true, `SAIL EAST` aboard gives "The crew will not let you use the helm."

Variants: use `WAIT=30` instead of `DEPART` for a ferry that leaves every 30 ticks; `FARES="Gullrock:3"` to charge three when boarding at Gullrock; `ROUND=LOOP` for a circular route.

### Example 2: a mountain journey with an ambush

Goal: an eight-hour coach ride from Highmark to Stonereach. Each hour there is a chance of an ambush scene or wolves. The party can cancel and get a partial refund.

World:

- `Highmark#1` the coach yard, with an exit east into `Highmark#2` ("Inside the mountain coach"), a closed-in room with no other exits.
- `Stonereach#1` the destination.

On the east exit of `Highmark#1` (`MODIFY EXIT east`), add `Prop_Journey`:

```text
DEST=Stonereach#1 VIA=Highmark#2 DURATION=8 FARE=6 PARTY=LEADER PERHEAD=true LEAVEMSG="The driver cracks the whip and the coach lurches uphill." ARRIVEMSG="Stonereach's lamps appear through the snow." EVENTS="12%:ambush;10%:LOAD MOB mountain wolf*1d3" MISSING=WAIT DEAD=CONTINUE DISCONNECTED=TRAVEL CANCEL=RETURN REFUND=UNUSED
```

Attach this script to the same exit (as a script on the exit, see [04-mobprog.md](04-mobprog.md)):

```text
FUNCTION_PROG AMBUSH
mpechoat $n Rocks crash across the road. Figures with crossbows rise from the snow.
mpechoat $n Type JOURNEY RESUME when you are ready to go on.
return PAUSE
~
JOURNEY_PROG HOUR
mpechoat $n The road climbs. Hour $0 of the journey.
~
JOURNEY_PROG ARRIVE
mpechoat $n The coach shudders to a stop at Stonereach.
~
```

How it plays:

1. The party walks east. Fare is checked for all, the party is moved into the coach, and `JOURNEY_PROG START` and `LEAVEMSG` fire. The clock advances one hour; the weather and hunger run.
2. Each hour: a 12% roll for `AMBUSH`, then a 10% roll for wolves (the first success ends the roll). `AMBUSH` returns `PAUSE`, so the party must type `JOURNEY RESUME`, after talking, searching or fighting in the coach. Wolves (a catalog mob called `mountain wolf`, which you must have created, or a mob class ID) are loaded into the coach and fighting pauses travel by itself; when the wolves are dead the next tick resumes.
3. `JOURNEY` at any time reports "5.0 game hours remain to Stonereach."
4. `JOURNEY CANCEL` ends the trip. `CANCEL=RETURN` sends the present party back to `Highmark#1`; `REFUND=UNUSED` repays the fraction of the 6 coins for the hours not travelled.
5. After the eighth hour and any final scene the party arrives; `ARRIVEMSG` and the `ARRIVE` script run.

Tests to run: two party members with one short of coins (nobody departs); a companion who wanders out of the coach (`MISSING=WAIT` pauses with an explanation; `JOURNEY RESUME` re-checks); a death; a save in the middle of the ambush pause, reloaded in a fresh game; a refused return (lock the origin exit) which stays paused. The fixtures `t-journeys.js` and `ar-journeys.js` do exactly these.

### Example 3: a fast-travel waypoint network

Goal: three elven stones. The first is known from the start, the second must be touched, the third needs a ritual. Travel costs mana and an hour.

Properties (one per stone, on an item named `an elven standing stone` lying in the room; the `ID` keeps discovery stable):

```text
NETWORK=ELVEN NAME="Mirewood Stone" ID=mirewood DISCOVER=ENTER SCOPE=PROFILE COST="mana 20" TIME=1 MSG="Moss and moonlight swirl around you."
NETWORK=ELVEN NAME="Highpeak Stone" ID=highpeak DISCOVER=TOUCH SCOPE=PROFILE COST="mana 20" TIME=1
NETWORK=ELVEN NAME="Sunken Stone" ID=sunken DISCOVER=RITUAL RITUAL=SINGTOSTONE SCOPE=PROFILE COST="gold 5" TIME=2 MASK="-TATTOO +ELVEN_ATTUNEMENT"
```

The Sunken Stone's room carries a `FUNCTION_PROG SINGTOSTONE` that checks, for example, that the character has the song and returns something other than a refusal.

Play-through:

```text
(walk into Mirewood)         You attune to Mirewood Stone (ELVEN).
TRAVEL                       ELVEN: Mirewood Stone
(go to Highpeak)
TOUCH highpeak stone         You attune to Highpeak Stone (ELVEN).
                             ELVEN: Highpeak Stone, Mirewood Stone
TRAVEL Mirewood Stone        You travel to Mirewood Stone.
```

The party arrives together, 20 mana is charged once to the leader, and an hour of game time passes. At the Sunken Stone before the ritual, `TRAVEL` lists only the stones already discovered and `TRAVEL <name>` says "You must attune to this waypoint first."; `TRAVEL ATTUNE` runs `SINGTOSTONE` and answers "Waypoint attuned." or "The ritual is incomplete.", and the character without `ELVEN_ATTUNEMENT` gets "You cannot attune to this waypoint.".

Run `WORLD CHECK ONLY=routes,ids` for duplicate names and IDs, and test: a second character in the same profile sees the same discoveries (`SCOPE=PROFILE`); one with 5 mana is refused ("You cannot pay the waypoint's cost.") and nobody moves; a renamed stone with the same `ID` is still discovered.

### Example 4: a scaled dungeon instance

Goal: "The Drowned Barrow" restocks and rescales at the party's level when the party returns after 12 or more game hours, but a half-cleared barrow survives a save.

On the area (`MODIFY AREA`):

- Effect `InstanceArea`: `ID=DROWNED_BARROW INPLACE=true LEVELADJ=@x1 PREFIX=weathered`
- Behavior `ResetWhole`: `ONREENTRY=true MINAWAY=12 SKIPROOMS="-LOCATION '+ROOMSTR(Sanctum)'" RESETMSG="Fresh tracks lead into the barrow."`

What happens: the first time a player enters, the whole area is rescaled to the leader's level (`@x1`) and its mobs are prefixed "weathered". The party kills some mobs and leaves. If it comes back before 12 game hours, nothing is reset: the barrow is as they left it. After 12 hours, entry resets every room except the Sanctum, which keeps its contents, shows the message to the first one in, and scales the new mobs to the entering leader's level. A save inside restores the same rooms, the same scaling state and last-access time.

For a copy-per-party dungeon with entry limits use the other mode instead:

```text
ID=CRYPT LEVELADJ=@x3 DURATION=900 LIMIT=1/DAY PREFIX="restless,chilled"
```

on the area. Each party that enters gets its own `1_The Crypt` copy; `LIST INSTANCES` shows it with its remaining ticks; after one entry, the leader is refused for a day. The in-place pair is exercised by `t-instances.js`.

Test list: enter at two different levels; leave and return early and late; leave one companion inside; save inside and restore in a fresh process; return from a destroyed entry vehicle; make sure a safe recovery exit exists.

### Example 5: a player ship

Goal: the party buys a sloop, names it, and sails it to a port by name with one crew NPC.

1. **Build the ship.** At the shipwright's quay: `CREATE ITEM GenSailingShip`, name it `a sloop [NEWNAME]`, `PRICE` 4000, `OWNER` empty, `Moves per Tick` 2, `TICKSPERMOVE` 2. `VESSEL a sloop ENTER` and build `WoodenDeck` (fore), cabin and hold, with `Prop_ReqCapacity` on the deck.
2. **Sell it.** Add the finished item to the inventory of a shopkeeper that sells ships (the shopkeeper type that sells vessels, per the Archon guide), or give it in a script that has the vessel as `$o`: `mpgrant $o $n PROFILE NAME='The Wavewalker'`. `PROFILE` makes the whole profile the owner, so every party character may steer; `NAME=` fills the `[NEWNAME]` and renames the interior.
3. **Crew.** Put a `GenMob` on the deck with the `Sailor` behavior. A `Sailor` aboard is always allowed to steer and will man siege weapons in combat.
4. **Name the ports.** Put `Prop_Port` on the quays: `NAME="Port Azure"`, `NAME="Gullrock"` (see Example 1).
5. **Sail.** On deck:

   ```text
   PORTS
   COURSE TO Gullrock
   ```

   The ship plots the route (and raises its anchor), sails it under its pace and the `Prop_TravelTime` of the sea rooms, and stops at the end of its course. `LOWER ANCHOR` at a quay to hold it; `TENDER`, `JUMP OVERBOARD`, `STEER` and `COURSE north north east` work as in the sailing help (`HELP SAILING`).
6. **Inspect.** `VESSEL STATUS wavewalker` as a builder shows the position, course, hull, owner and crew; `LIST SHIPS` lists it with anchor, course and owner.
7. **Persist.** Save. The vessel is kept with the owning character; its anchor, course, facing, remaining step time and the vessel it follows are in the campaign's vessel records.

## Persistence

What is saved where. "World" means the world files (`SAVE` in builder mode publishes them); "campaign" means the save slot.

| Feature | Authored definition (world) | Runtime state (campaign) |
| --- | --- | --- |
| `Prop_TravelTime`, `Prop_Road`, `Prop_Port`, `Prop_HelmRestrict`, `Prop_TicketTaker` | Property text on the host | Nothing; ticket receipts and `groupPaid` are in memory only (a reload may charge a boarding again). |
| `Prop_Journey` | Property text | The running trip is `Prop_JourneyState` on the leader, saved with the character: definition, origin, destination, transit room, hours left and elapsed, remaining leg time, waiting and manual-pause flags, seats, player names, dead seats, fare receipts, host reference. Each member also carries a `Prop_JourneyPassenger` marker effect with its seat ID, which the journey uses to find its passengers again. After a restore the timers are rebuilt from the remaining time. |
| `TransportLine` | Behavior parameters | `<LINE>` state: stop, next stop, direction, moving flag, initialized, remaining travel wait, remaining departure wait, departure hour, arrival count. |
| Vessels | The item and its interior area | `<VESSELS>`: anchor, direction, facing, remaining step wait, ticks since move and turn, the course, and the vessel followed. Riders and positions are in the world state. |
| `MovingRoom` | Behavior parameters, route file | Current stop, next stop, status, direction, initialized flag, remaining wait, departure hour. Links and overlay text are rebuilt after load. |
| `DelayedTransporter` | Behavior parameters | The destination index and each passenger's ticks present. |
| `Concierge`, `TaxiBehavior` | Parameters | Quoted trips (who, destination, price, flags) and the saved home room. |
| `Nanny` | Parameters (rate, name, network, fees, watches) | Custody records after `\|~\|` in the saved parameters: animal, owner, drop-off time and payments. Remote retrieval retains the original owner's bill. |
| `Prop_Waypoint` | Property text | Discovery tattoos on the character (or the profile for `SCOPE=PROFILE`), keyed by the waypoint's ID. |
| `InstanceArea` copy mode | Property text | Instance areas made in play are saved as `NEWAREA` records and rebuilt on restore; entry-limit counters live in the leader's player record. |
| `InstanceArea INPLACE` | Property text | `<INPLACE READY LEVEL>` state. |
| `ResetWhole` | Behavior parameters | `<RESETWHOLE AGO AWAY MSG DONE>`: time since last access, away flag, pending message. |
| Planes and generated areas | `planesofexistence.txt`, templates | Remade areas with their seeds (`GENSEED`). |

A running journey remembers its host by a persistent ID; if the host was deleted, it falls back to looking the host up by room, type and name, which is ambiguous when names repeat. Give hosts unique names, and give waypoints explicit `ID=` values. See [state-and-persistence.md](state-and-persistence.md) for the general rules and [02-world-workflow.md](02-world-workflow.md) for `SAVE` and `WORLD`.

## Checking your work

| Tool | What it proves |
| --- | --- |
| `WORLD CHECK ONLY=routes` | Stops resolve, route XML is well formed, route files exist, waypoint names are not ambiguous, `Prop_Journey` options are valid. |
| `WORLD CHECK ONLY=ids` | No two waypoints in a network share an `ID`. |
| `VESSEL STATUS <name>` | A vessel's real state: position, course, anchor, hull, behaviors, crew. |
| `TIMETABLE` | Every line and moving room. |
| `PORTS` | The ports a character knows (test with a normal and a builder character). |
| `LIST SHIPS`, `LIST INSTANCES`, `LIST PLANES` | What exists. |
| `JOURNEY` | State of a running trip. |
| Fixtures | `t-travel-core.js`, `t-transport.js`, `t-journeys.js`, `t-waypoints-stables.js`, `t-instances.js`, `dt28-vessel.js` in `tools/fixtures/fixes/` show working syntax; `setup/travel-*.js` builds and saves a small travel world. |

Test with an ordinary character, not a builder: builders bypass helm restrictions, ports, masks and limits.

## Troubleshooting and gotchas

- **Travel time does nothing.** `Prop_TravelTime` is only charged when a player character moves, in single-player. Check `TIME` is above 0, and that an exit or nearer property is not overriding it (exit first, then destination, then source).
- **A caravan will not leave the road.** The rooms need `Prop_Road` (or a drivable locale). `NOTFOR` may be excluding it by name.
- **"There is no navigable route."** The router only uses open, traversable exits through terrain the vessel accepts. A closed door, a land room in a water route, or the 10,000 room limit stops it. A ship needs water or seaport rooms all the way between the stops.
- **A passenger took the ferry's helm.** An unowned vessel with no `Prop_HelmRestrict` allows everyone. Add `NAVMASK="-PLAYERS"` or use a `TransportLine`. `FLOAT`, `RISE`, `DIVE` and `TIEDOWN` are refused without the custom `MESSAGE`.
- **The line never leaves.** It needs two or more stops that resolve, a route between them, and, with `DEPART`, hours inside the day's range. A closed exit along the route or a stop in non-water rooms for a ship will make it wait and retry.
- **Fare is charged twice.** Two ticket takers (a mob and a vehicle) or `PARTY=EACH` with an unremembered receipt after reload. Use one and prefer `PARTY=LEADER`.
- **A ticket-item taker does not block.** With a named ticket (`a red ticket`) the property says the player needs one but does not stop boarding in the current code; use a numeric fare.
- **Journey ends at once.** The leader left the transit room (it has exits the party used, or something moved them) or died. Keep the transit room closed in.
- **Journey will not start.** "The party cannot depart": a member is in combat, cannot afford the fare, or is refused by an entry property of the transit room. "The journey has no valid destination": a hidden port, a missing room.
- **`DelayedTransporter` ignores my message.** A semicolon inside an option message ends the option list. Remove it.
- **`DelayedTransporter` teleports through a locked gate.** `ENTRY=FORCE` is the default. Use `ENTRY=CHECK`.
- **The moving room has no door.** The car or the stop already has an exit in the door direction; temporary links never replace authored ones. Remove that exit.
- **Hidden port still listed.** `KNOWNMASK` replaces `HIDDEN`; a mask that everyone matches makes it public. Builders see all ports.
- **Waypoint discovery lost or shared.** Without `ID`, a room waypoint is keyed to its room ID (renumbering the room loses discovery) and an item waypoint to a hidden saved identity. Give every waypoint an explicit `ID` before release and use `ALIASES` when changing it. A copy of a stone with an explicit `ID` shares its discovery: give the copy its own `ID` (`WORLD CHECK ONLY=ids` reports duplicates).
- **`STABLE` does nothing.** There is no keeper with `Nanny` in the room, the animal is not owned by you, or `WATCHES` omits its kind. The ranger skill word is `STABLEMOUNT`.
- **Two copies of one ship have the same interior.** `FIXEDINTERIOR=true` on the original. Set it back to `false`.
- **An instance never resets.** `InstanceArea` copy mode builds a fresh copy per party; it does not reset the real area. Use `ResetWhole` for that.
- **The barrow resets at every return.** `ONREENTRY=true` with no `MINAWAY` resets whenever the party returns after leaving. Set `MINAWAY` to the game hours you want.
- **A name that works for builders but not players.** If a port with that name exists but the character does not know it, `COURSE TO`, `DEST` and the like stop with "unknown" instead of falling through to a room or area of the same name.

## See also

- [20-areas-rooms-exits.md](20-areas-rooms-exits.md) for areas, locales and exits.
- [21-mobs.md](21-mobs.md) for rideable creatures, shopkeepers and Sailor/Nanny hosts.
- [22-items.md](22-items.md) for boardable and rideable item fields.
- [23-properties-reference.md](23-properties-reference.md) and [24-behaviors-and-masks.md](24-behaviors-and-masks.md) for every property and behavior named here, and the zapper masks.
- [26-mobprog-triggers.md](26-mobprog-triggers.md), [27-mobprog-commands.md](27-mobprog-commands.md), [28-mobprog-functions.md](28-mobprog-functions.md) and [05-javascript.md](05-javascript.md) for scripting.
- [29-combat-and-encounters.md](29-combat-and-encounters.md) for ship combat and `EncounterReset`.
- [10-world-simulation.md](10-world-simulation.md) for time, weather and calendars.
- [12-generation.md](12-generation.md) for generated dungeons and seeds.
- [31-saves-and-party.md](31-saves-and-party.md) and [33-engine-internals.md](33-engine-internals.md) for the single-player party model and engine internals.
- [state-and-persistence.md](state-and-persistence.md), [cookbook.md](cookbook.md) (recipes 22 and 23) and the older [travel reference](../travel.md).
