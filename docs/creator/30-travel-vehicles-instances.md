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
