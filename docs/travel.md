# Travel, vehicles and instances

These features use the game clock in real-time and turn-based games. Authored
travel advances the scheduler, so weather, hunger, NPCs and scripts run during
the time spent travelling. Saves keep transport progress separately from the
world's authored definitions.

## Roads and time per step

Put `Prop_TravelTime` on an exit, room or area:

```
TIME=120 MOUNTED=0.5 VEHICLE=0.7 NIGHT=1.5
```

`TIME` is game minutes per step. The exit takes priority, followed by the
destination room/area and then the source room/area. The mounted, vehicle and
night values multiply it. Followers moving with the leader do not add another
party-sized time charge. Unoccupied NPC transport does not fast-forward the
whole world. Vessels retain the remaining step time in saves, and a scheduled
line announces arrival after that time has passed.

`Prop_Road` on a room or area allows caravans through its existing terrain:

```
TIME=0.75 NOTFOR="heavy caravan,GenCaravan"
```

Its optional `TIME` multiplies the travel-time property. `NOTFOR` excludes
matching vehicle names or class IDs. Woods and mountain roads keep their
terrain, resources and descriptions.

## Named ports and vessel control

Put `Prop_Port` on a destination room:

```
NAME="Port Azure" KIND=SEA HIDDEN=false
NAME="Smuggler Cove" KIND=SEA HIDDEN=true KNOWNMASK="-TATTOO +COVE_CHART"
```

`KIND` describes a SEA, ROAD, AIR or SPACE destination. `PORTS` lists known
ports. A `KNOWNMASK` reveals a hidden port to qualifying characters. From a
navigable ship or caravan, `COURSE TO Port Azure`, `SAIL TO Port Azure`, or
`DRIVE TO Port Azure` plots a one-shot route. Room IDs, area names and the
character's numbered Sea Charting points (`CHART 2`, or `2`) are accepted.
Routing uses open exits and suitable terrain; the vessel's normal movement
checks still apply as it moves.

Generic navigable vessels expose these stats in MODIFY and MPSET:

| Stat | Meaning |
| --- | --- |
| `TICKSPERMOVE` | Minimum ticks between moves, at least 1 |
| `TICKSPERTURN` | Minimum ticks between turns, at least 1 |
| `WEATHERSPEED` | Allow the existing wind bonus; default TRUE |
| `FIXEDINTERIOR` | Keep an interior's name on copies; default FALSE |

Copied vessels otherwise receive distinct interior area names and room IDs.
Use a fixed interior for a single, deliberately named craft. Builder commands:

```
VESSEL STATUS the Black Gull
VESSEL the Black Gull ENTER
VESSEL the Black Gull RENAME BlackGull
VESSEL the Black Gull HOME Harbour#2
```

Save the vessel's room after changing its authored data. `ENTER` visits its
first interior room. Existing LIST SHIPS and LIST INSTANCES remain available.

`Prop_HelmRestrict` on a vessel restricts helm commands, including anchor,
brake, course, steering and tendering:

```
NAVMASK="-PLAYERS" MESSAGE="The captain says: hands off the wheel."
NAVMASK="-TATTOO +MUTINEER" ONFAIL=warn_passenger
```

NPCs with Sailor aboard that vessel and builders with item permissions pass.
`ONFAIL` names a function on the vessel's scripts.

## Scheduled transport and fares

`TransportLine` goes on a navigable vessel, rideable, or mounted NPC driver:

```
STOPS="Port Azure;Saltmarsh;Gullrock" DEPART=6,12,18 ROUND=BACK
FARE=2 PARTY=LEADER PETS=5 CARGO=1 PASSENGERONLY=true
BOARDCALL="Boarding for $next." DEPARTMSG="The Gull leaves $stop."
ARRIVEMSG="The Gull arrives at $stop." SIGN="a timetable board"
```

Write the parameters on one behaviour line. Stops may be room IDs or port
names and need not be adjacent. `ROUND=LOOP` cycles; `BACK` reverses at each
endpoint. `DEPART` lists game hours; without it, `WAIT` sets ticks at each stop.
`FARES="Port Azure:2,Saltmarsh:3"` overrides fares at particular stops.
`PASSENGERONLY` installs crew-only helm access unless the craft already has an
explicit helm restriction. `TIMETABLE [line]` and the optional sign report
current progress. Stop, direction, next departure and arrivals survive saves.

`Prop_TicketTaker` also accepts:

```
FARE=2 PARTY=LEADER PETS=5 CARGO=1
```

With `PARTY=LEADER`, the leader pays each character's fare together, with an
optional price per pet and per 100 pounds of cargo. Companions are admitted on
that payment. `PARTY=EACH` retains individual payment. Numeric fares and named
ticket items retain their existing syntax. TaxiBehavior also supports
`PARTY=LEADER`, `PETS` and `CARGO`, with one party quote and boarding together. Its existing destination syntax is
semicolon-separated, for example `PARTY=LEADER;PETS=5;Gullrock#0=7`.

`DelayedTransporter` retains its semicolon-separated destinations, and adds:

```
min=3 max=3 chance=100 PARTY=LEADER PERHEAD=true FARE=2 DEST=ORDER LOOK=true
MASK="-TATTOO +GROVEFRIEND" DEPARTMSG="The stones hum."
ARRIVEMSG="You emerge beside the moonwell.";Moonwell#0;Grove#2
```

Use one parameter line. `DEST=RANDOM` is the default; ORDER cycles the list.
`PARTY=true` or `PARTY=LEADER|EACH` makes the party's countdown shared. `PERHEAD`
counts a fare for each eligible character; otherwise FARE is a flat charge.
The mask selects who is carried. Entry refusals and insufficient funds are
checked before moving anyone or collecting money. Countdown and destination
order are saved.

## Journeys and encounters

`Prop_Journey` goes on a passage, portal, rideable or NPC:

```
DEST=Gullrock#0 VIA=Ferry#Cabin DURATION=6 FARE=2 PARTY=LEADER
LEAVEMSG="The mainland falls astern." ARRIVEMSG="Gullrock rises ahead."
EVENTS="10%:storm_scene;5%:LOAD MOB pirate band;2%:CATALOG encounter-sea"
```

`DURATION` is game hours, including fractions. `VIA` is an optional transit
room; without it the party waits where it departed. Entering the host starts
the passage; `JOURNEY [host]` can start it explicitly. `JOURNEY` shows progress,
`JOURNEY RESUME` continues a paused scene, and `JOURNEY CANCEL` ends the trip at
the current location.

Events roll each travelled hour. Function names call the host's MOBPROG or
JavaScript functions; `LOAD MOB` uses catalog names or mob classes, and CATALOG
uses a world encounter table. Combat pauses travel, which resumes after the
fight. A scene function returning `PAUSE` waits for an explicit JOURNEY RESUME.
Other noncombat events resume after a tick. A final-hour scene finishes before
arrival. Saves keep remaining time, completed hours, event pause and each
passenger's identity, including identically named mounts.

```
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

ALL and CANCEL are also supported. `$g` is the stage; `$0` is completed hours
and `$1` the destination room ID.

## Waypoint networks

Put `Prop_Waypoint` on a room or item:

```
NETWORK=ELVEN NAME="Mirewood Stone" DISCOVER=TOUCH
SCOPE=PROFILE COST="mana 20" TIME=1 MASK="-TATTOO +ELVEN_ATTUNEMENT"
```

`TRAVEL` lists discovered destinations available from here. `TRAVEL Highpeak
Stone` takes the party there, charging once and advancing TIME game hours.
`COST` accepts `mana N`, `moves N` or `gold N`. `SCOPE=CHARACTER` is the default;
PROFILE shares discovery across that profile. `ID` optionally provides a stable
author ID, so moving or renaming the stone does not change its discovery key.

`DISCOVER=ENTER` attunes on entry, TOUCH on touching/using it, RITUAL through
`TRAVEL ATTUNE` (optionally calling `RITUAL=<function>`), and QUEST after winning
`QUEST=<quest name>`. `TRAVEL ATTUNE` checks the mask and discovery requirements.
Scripts may award discovery through `Waypoints.discover(character, property,
true)`. Unknown destinations are hidden from the travel list.

## Return resets, instances and lifts

`ResetWhole` adds:

```
ONREENTRY=true MINAWAY=12 SKIPROOMS="-LOCATION '+ROOMSTR(Sanctum)'"
RESETMSG="Fresh tracks lead into the barrow."
```

It resets on return after everyone left and at least MINAWAY game hours passed.
Skipped rooms keep their current contents. Existing TICKS, DAYS, EXCEPT and MSG
rules remain; RESETMSG is an alias for MSG. Last access, absence and a pending
message are saved.

`InstanceArea` can scale the real area instead of making a copy:

```
ID=DROWNED_BARROW INPLACE=true LEVELADJ=@x1 PREFIX=weathered
```

`LIKE=<existing type>` can supply the normal instance adjustments. Initial
entry scales the area; a ResetWhole return reset applies those adjustments to
the restored rooms using the entering leader's level. A save inside preserves
the half-cleared area and its scaling state.

MovingRoom accepts its existing XML format inline or from a world route file:

```
ROUTE=deepdelve-lift.xml WAIT=5 TRAVEL=10 DEPART=6,12,18 SIGN="a brass plate"
```

The file lives under `world/routes/`; `LINE=<XML section>` selects a section,
otherwise the root tag is used. Inline XML is a quoted ROUTE value. Legacy
`min=3 max=3 chance=100;SUBWAY;3;10` still reads resources/movingroom.xml.
WAIT and TRAVEL are ticks. DEPART optionally replaces the wait with a game-hour
timetable. Saved state includes position, direction and remaining leg time.
Boarding links and descriptive text are runtime overlays, excluded from world
exports. `TIMETABLE` or SIGN publishes the line's position.

## Sister stables

Put Nanny on each stable keeper:

```
RATE=2 NAME="Port Azure Livery" WATCHES="mounts,wagons"
NETWORK=ROYAL_STABLES SENDFEE=5
```

`STABLE [mount]`, `STABLE LIST` and `RETRIEVE [mount]` are also understood when
spoken to the keeper. A mount in the same network can be collected elsewhere,
paying accrued keep plus SENDFEE. Custody, owner, drop-off time and prior
payments use the existing saved stable records. The ranger skill remains available
as `STABLEMOUNT`.

## Transport scripting

```
mpboard $n 'the Black Gull'
mpsail 'the Black Gull' TO 'Hidden Cove#1'
mpanchor 'the Black Gull' DOWN
mpmovevessel 'the Black Gull' 'Harbour#2'
mphitch 'a merchant wagon' 'an ox'
mpunhitch 'a merchant wagon'
```

MPSAIL also accepts an explicit list of directions. MPBOARD carries the nearby
party and optionally names an interior room. MPMOVEVESSEL moves and docks the
craft without a voyage. MPHITCH refuses riding loops.

MOBPROG and JavaScript functions: `ABOARD(who)`, `RIDING(who)`, `RIDERS(rideable)`,
`ISANCHORED(vessel)`, `ISDOCKED(vessel)`, `COURSE(vessel)`, `VESSELROOM(vessel)`,
`SPEED(vessel)` and `HULL(vessel)`. Vessel names resolve across the world;
VESSELROOM returns a room ID and HULL the current condition percentage.

In JavaScript, use the standard uppercase MOBPROG bridge: `VESSELROOM$("the Gull")`
returns its string value, while `ISANCHORED("the Gull")` returns a boolean.


See [authoring identities, checks and recovery](authoring-reliability.md) for
saved source IDs, migration aliases, validation and world asset tracking.
Journey recovery, return and refund policies are documented there too.
