# World simulation, travel, vehicles and instances

[Handbook](README.md) · [Travel options](../travel.md)
· Feature families [W](feature-index.md#w), [T](feature-index.md#t)

## Time is part of the rules

TIMEMODE TURNS advances when actions cost time; REALTIME advances while the
game runs. PAUSE holds the game clock. Configure day length, week/month/year,
start date, custom seasons, seasonal daylight and named moons. Areas can have
their own calendar context. Use game dates for deadlines, dues, regrowth and
routes; use real time only when the world deliberately calls for it.

Long WAIT, WAIT UNTIL, MPPASSTIME and JS time helpers let time pass without a
busy script loop. LONGWAIT controls policy. Movement travel-time properties
can consume minutes; a journey can consume hours. A player can cross a quest
deadline while travelling. Time jumps and catch-up are meaningful simulation
events; test them instead of assuming every missed tick must be replayed.

TIMESCALE is a diagnostic/control tool, not a rewrite of all authored timing
units. `Prop_TimeMode` locks selected zones to turn-based or real time. Party
turn options determine shared action progression; test background characters
as well as the current window.

## Weather, terrain and changing rooms

Weather forecasts, SET/NEXT and MPWEATHER support authored climate behavior.
World climate/weather text data can be selected per area; Prop_SkyText supplies
local sky lines. Holidays support calendar recurrences and tests by name.
RoomStates selects named room versions. Tides and floodable rooms can react
to weather, tide or script variables. ResetWhole supports time/absence rules,
exceptions and messages.

Hazards are typed and inspectable. Darkness can be a visibility rule, a danger,
or supernatural darkness ordinary torches cannot defeat. Configure breathing
and BREATHTICKS before drowning. Material, race and effects determine who can
survive a zone; writing “underwater” in the room description is insufficient.
Use WEATHER, TIME, LOOK AS and HAZARDS with a representative character.

Exposure/body-temperature simulation remains deferred. Existing weather,
hazards, hunger, thirst and breathing can still provide survival mechanics.

## Walking and routes

`Prop_TravelTime TIME=120 MOUNTED=0.5 VEHICLE=0.7 NIGHT=1.5` changes game
minutes for a move. Exit settings take precedence over destination and source
room/area settings. Followers do not multiply the charge. `Prop_Road` modifies
time while keeping terrain; NOTFOR can restrict unsuitable vehicles.

Ports are authored destinations with NAME, KIND and optional hidden/knowledge
mask rules. PORTS lists visible ones. COURSETO/SAILTO/DRIVETO accept ports,
rooms, areas or chart coordinates. These use suitable open-exit routes and
normal movement checks; they are not a fastest-route algorithm weighted by
travel minutes. A route preview does not promise permission through every
future locked or hazardous exit.

## Vessels and caravans

Choose the correct boardable/rideable vehicle type. Set its hull, speed,
interior, exits and navigation rules. TICKSPERMOVE and TICKSPERTURN are at
least one; WEATHERSPEED changes response to conditions. FIXEDINTERIOR controls
whether copies share a fixed interior or get unique copied interiors.

`VESSEL STATUS name` inspects a craft; VESSEL ENTER/RENAME/HOME supports
builder maintenance. `Prop_HelmRestrict` can require a navigation mask and run
an on-failure script. Builders and qualified Sailor crew can bypass restrictions;
test with a normal passenger and navigator. MPHITCH refuses riding loops.

MPBOARD, MPSAIL, MPANCHOR, MPMOVEVESSEL, MPHITCH and MPUNHITCH support scripts.
ABOARD, RIDING, RIDERS, ISANCHORED, ISDOCKED, COURSE, VESSELROOM, SPEED and HULL
expose state. Saves retain vessel identity, interior, riders, course and
related travel state. Moving a vessel directly is different from sailing it
through ordinary movement checks.

## Public transport and journeys

| System | Use |
| --- | --- |
| TransportLine | Scheduled stops, fares, party/pet/cargo rules and boarding calls |
| TicketTaker | Ticket/numeric fares with party and cargo charging policy |
| TaxiBehavior | Authored destination fares and party/pet handling |
| DelayedTransporter | Delayed destination list, fare validation and transactional party move |
| Prop_Journey | Abstract hours-long trip with route points and random events |
| MovingRoom | A moving room/lift with route XML, wait/travel time and timetable |
| Nanny stable network | Saved mount custody and retrieval at sister stables |

TIMETABLE communicates public departures. Explicit party charging avoids
charging the leader repeatedly; validate money and all riders before moving
anyone. A passenger-only line should not smuggle vehicles/cargo through its
rules. Ordinary unoccupied NPC transport does not fast-forward world time.

Prop_Journey uses DEST, optional VIA, DURATION in game hours, FARE, PARTY,
messages and weighted EVENTS. JOURNEY shows status, RESUME and CANCEL.
Combat pauses travel; a function event can return PAUSE and require a visible
resume. Missing/dead/disconnected host policies and cancellation/refund rules
are configurable. Saved participants and host bindings use persistent IDs;
ambiguous legacy names remain unbound with diagnostics. See
[recovery policies](../authoring-reliability.md#journey-recovery).

MovingRoom can load `world/routes/*.xml` with ROUTE and optional LINE. Its WAIT
and TRAVEL are ticks; DEPART is a game-hour timetable. Runtime boarding links
and descriptive overlays are excluded from exported world definitions.

## Waypoints and fast travel

Prop_Waypoint defines a network, destination, discovery rules, costs and scope.
Discovery can require ENTER, TOUCH, RITUAL or a quest. TRAVEL lists only known
destinations and ATTUNE enforces discovery requirements. CHARACTER and PROFILE
scope have different sharing. Give waypoints stable IDs; IDs are scoped to the
network. Duplicate IDs in one network are errors. Renaming a stone should not
make it a second free discovery or invalidate a saved route.

## Resets, generated areas and instances

An ordinary area is one live place. A copied instance gives participants a
private copy; in-place scaling adjusts the real area. InstanceArea can use
`INPLACE=true`, an ID, level adjustment and prefixes. ResetWhole can reset on
return after MINAWAY game hours while skipping selected rooms. A partially
cleared save retains its state instead of automatically respawning everything.

Autogenerated instances can use explicit, GAME or ENTRY seed policies. GAME
keeps one campaign seed; ENTRY creates and saves a seed for each instance.
Generated and planar instances, visit timers, instance limits and space objects
have save support. Use LIST INSTANCES and vessel/instance diagnostics to inspect
what is live. Seeded generation and save persistence are related but separate:
neither alone specifies when a cleared dungeon should reset.

Test entry, leaving all participants behind, a lone companion remaining inside,
reset after a time jump, save inside, fresh-process restore, and returning from
a missing/destroyed entry vehicle. Link a safe recovery destination. The
[travel reference](../travel.md) gives exact option units and formats.
