# Travel, vehicles and instances

Ships, mounts, ferries and other transport, routes, fares, travel time, fast travel, and instanced or resetting dungeons. Part of the [builder capabilities audit](README.md).

<a id="t2"></a>

## T2. Dungeons that reset when the party comes back

*Kind:* behaviour · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** `ResetWhole TICKS=<n>` resets an area after n ticks with no player activity. In turn-based time that already means a fresh area after a long absence. `MUDHOUR=<h>` resets at a game hour, and `MPRESET AREA` in a `GREET_PROG` resets on entry. Its last-access time is not saved, so it is -1 after a restore. `InstanceArea` makes a fresh copy per visit, but copies do not save, and its type adjustments (`LEVELADJ`, `ELITE`, `PREFIX` and the rest) apply only to copies.

**Add.** `INPLACE=true` on `InstanceArea`: no copy is made, and the type's adjustments are applied to the real area at each reset, keyed to the entering leader's level. Save `ResetWhole`'s last-access time. As conveniences on `ResetWhole`: `ONREENTRY=true` (reset when a party member enters after all had left), `MINAWAY=<game hours>`, `SKIPROOMS=<room mask>` and `RESETMSG`. The rooms are ordinary, so saves restore them as they are.

**Example.** "The Drowned Barrow" has `ResetWhole ONREENTRY=true MINAWAY=12` and `InstanceArea INPLACE=true LIKE=Triumph`. A return after half a day finds it restocked at the party's level. A save made inside restores the same half-cleared barrow.

*See also:* W9 (Both add ResetWhole parameters (DAYS vs MINAWAY, MSG vs RESETMSG) for how an area resets after the party leaves); G14 (Alternatives for a dungeon that is fresh when the party returns: reset it in place, or re-roll it).

<!-- from: vehicles-travel-instances/vti-reset-on-reentry -->

<a id="t3"></a>

## T3. Script commands to sail, anchor, dock, board and hitch

*Kind:* script command · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** A script can `MPFORCE` a crew mob aboard to type `RAISE ANCHOR`, `COURSE` and `STEER`. It can `MPBEHAVE` `Patroller` on the vessel with room IDs as steps; Patroller routes between them but loops, and `MPUNBEHAVE` stops it. `MPTRANSFER $n <ship room id>` puts one person aboard. JavaScript can call `NavigableItem` methods. There is no one-shot route and no move-and-dock, and no script can set an item's `riding()`, so nothing hitches a cart.

**Add.** `MPSAIL <vessel> TO <room id|area|port name>`, a one-shot route computed as `SeaNavigation` and `AwaitShip` do, or `MPSAIL <vessel> <dir dir ...>`. `MPANCHOR <vessel> UP|DOWN`. `MPMOVEVESSEL <vessel> <room>`, which moves and docks it with no voyage, for scenes and quests. `MPBOARD <who> <vessel> [room id]`, for a mob or a player with the party. `MPHITCH <item> <mount>` and `MPUNHITCH <item>`.

**Example.** When the player pays the smuggler, the quest runs `MPBOARD $n 'the Black Gull'`, then `MPSAIL 'the Black Gull' TO 'Hidden Cove#1'`. When the ship reaches the cove, the next quest step starts.

*See also:* T5, T14 (One route-finder serves T3's MPSAIL TO, T5's stops and T14's COURSE TO a named port).

<!-- from: vehicles-travel-instances/vti-vessel-commands -->

<a id="t4"></a>

## T4. Travel time on rooms, exits and areas

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** A step out of combat costs a fifth of a tick, about 4 game seconds, and a game hour is 150 ticks. The cost is set per command for the whole game (`CMProps.getCommandActionCost`). No room, exit or area changes it, and `LongRoad` changes movement points only. A 30-room road or ocean crossing passes in about a minute of game time, so day and night, hunger and weather cannot carry a journey.

**Add.** A `Prop_TravelTime` property for exits, rooms or whole areas: `TIME=<game minutes per step>`, with multipliers `MOUNTED=0.5`, `VEHICLE=0.7` and `NIGHT=1.5`. Walking, riding or a vessel moving through costs that much game time. In turn-based time the world moves on by it, through the same queue as other commands. In real time the clock is briefly stopped and moved ahead. The party moves together and each member is charged once.

**Example.** The 12 rooms of the "Kingsroad" area have `TIME=120`: two hours a step, half on horseback. Walking from Ashford to the capital takes a day. The party sees dusk, gets hungry, and a nighttime ambush script can fire.

*See also:* T6, T12, T18 (T6, T12 and T18 use T4's travel time).

<!-- from: vehicles-travel-instances/vti-travel-time -->

<a id="t5"></a>

## T5. Timetabled ferry, coach and airship lines

*Kind:* behaviour · *Value:* high · *Effort:* large · *Decision:* yes / no

**Today.** `Patroller` on a vessel or rideable follows room-ID stops and routes between them, with `MIN`/`MAX` for pace, `.` to pause one step and `RESTART` to loop. `Prop_TicketTaker` takes a fare on boarding, and `TIME_PROG` with `MPBEHAVE` can start departures at set hours. Missing: waiting at a stop for game hours, crew-only steering (`rideok=only` skips boardables, so passengers can steer), a timetable sign, and a saved route position (Patroller's step is a plain field).

**Add.** A `TransportLine` behaviour for any navigable vessel, rideable or mob driver: `STOPS=room;room;...` or port names, `DEPART=6,12,18` (game hours) or `WAIT=<ticks at stop>`, `ROUND=loop|back`, `FARE=<n>` or `FARES=stop:price,...`, `BOARDCALL=`, `DEPARTMSG=`, `ARRIVEMSG=` (with `$stop` and `$next`), `PASSENGERONLY=true`, and `SIGN=<item name>` for a board at each stop. Stop index and departure time save in world.xml. It routes as T3's `MPSAIL TO`, so stops need not be adjacent.

**Example.** "The Gull" runs Port Azure, Saltmarsh and Gullrock, leaving at 6, 12 and 18, two silver a stop. The quay's board reads "The Gull: at Saltmarsh, due here at 12". `WAIT UNTIL ARRIVAL` (T1 (now in W4)) carries the party across.

*See also:* T3, T14 (One route-finder serves T3's MPSAIL TO, T5's stops and T14's COURSE TO a named port); T8 (T5's PASSENGERONLY and T8's NAVMASK both keep passengers off the helm); T16 (Two timetabled routes with a saved position (TransportLine vs MovingRoom with DEPART=)); T10, T17 (Three fare rules for the party (T5's FARE, T10's PERHEAD, T17's PARTY=LEADER); settle one).

<!-- from: vehicles-travel-instances/vti-transport-line -->

<a id="t6"></a>

## T6. Journeys: passages that take hours, with events on the way

*Kind:* property, script trigger · *Value:* high · *Effort:* large · *Decision:* yes / no

**Today.** A journey can be faked: a transit room with `DelayedTransporter min=N max=N chance=100;<dest room>` (it carries followers), `RAND_PROG` or `DELAY_PROG` scripts there for events, and a `GIVE_PROG` or `Prop_TicketTaker` with `MPTRANSFER` to get in. But durations are in ticks only, the countdown does not pause for an event, no trigger marks the stages, and progress is not saved.

**Add.** A `Prop_Journey` property for an exit, portal, rideable or NPC: `DEST=<room>`, `DURATION=<game hours>`, optional `VIA=<transit room>`, `FARE=`, `LEAVEMSG`/`ARRIVEMSG`, and `EVENTS="10%:storm_fn;5%:LOAD MOB pirate band;2%:CATALOG encounter-sea"`, rolled each game hour. On a hit the journey pauses for the fight or scene and resumes after. The whole party moves, and time passes as in T4. A new `JOURNEY_PROG` trigger fires at start, each hour (with the hour) and at arrival. A save made mid-journey resumes where it stopped.

**Example.** Passage to Gullrock: the party sits in the ferry cabin for 6 game hours. In hour 3 a kraken event loads a tentacle mob on deck. After the fight the trip goes on, and the party lands on the Gullrock quay at dusk.

*See also:* T4, T12, T18 (T6, T12 and T18 use T4's travel time); T10 (Alternatives for a timed passage with messages and fares (Prop_Journey vs an extended DelayedTransporter)).

<!-- from: vehicles-travel-instances/vti-journey -->

<a id="t7"></a>

## T7. Script functions for riding and vessels

*Kind:* script function, JavaScript hook · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** `INCONTAINER($n)` gives what a mob rides, and `ISCONTENT($i)` lists a rideable's riders. `INROOM($i)` and `INAREA($i)` say where a vessel is. `INAREA($n)` for someone aboard gives the interior's area name, a rough "aboard". `STAT($i ABILITY)` and `STAT($i USES)` give speed and hull. Course, anchor and docking are not stat codes, there is no rider count, and a vessel cannot be found by name anywhere in the world.

**Add.** `ABOARD($n)` (the vessel item's name, or empty), `RIDERS($i)` (a count), `ISANCHORED(<vessel>)`, `ISDOCKED(<vessel>)`, `COURSE(<vessel>)` (remaining directions) and `VESSELROOM(<vessel>)` (its room ID, found by name anywhere). `RIDING($n)`, `SPEED` and `HULL` can be short names for what `INCONTAINER` and `STAT` already give. Each also as a JavaScript call.

**Example.** A smuggler whispers the password only if `aboard($n)` is "the Black Gull" and `isanchored("the Black Gull")` is false. A harbour board shows `vesselroom(the Gull)` to say where the ferry is.

<!-- from: vehicles-travel-instances/vti-vessel-functions -->

<a id="t8"></a>

## T8. Crew-only helm for world-owned vessels

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** When a vessel's `OWNER` is empty, anyone may raise the anchor, set a course or steer, so a passenger can take the world's ferry. Setting `OWNER` to a clan or an NPC captain's name locks the helm (anchor or brake, steer, sail, course, raise and lower; not TENDER), though anyone following the owner still passes. A script can also block it: nav commands reach the ship as HUH messages and the interior area's scripts are asked first, so a `CNCLMSG_PROG` there with `if ispc($n)` and `return cancel` stops passengers. That is one script per ship.

**Add.** A `NAVMASK` stat on `GenNavigableBoardable` (which `GenCaravan`, `GenSailingShip` and `GenAirShip` extend), or a `Prop_HelmRestrict` property: a zapper mask for who may use `RAISE`/`LOWER ANCHOR`, `COURSE`, `STEER`, `SAIL`, `TENDER` and brakes, with `MESSAGE="The captain glares: hands off the wheel."`. Mobs with `Sailor` on the vessel, and Archons, always pass. An optional `ONFAIL=<script function>` lets a crew script react.

**Example.** The ferry has `NAVMASK="-PLAYERS"`. The pirate galleon the player is held on has `NAVMASK="-TATTOOS MUTINEER"`, so the party can take the helm only after the mutiny quest.

*Re-checked against the code after writing: corrected.*

*See also:* T5 (T5's PASSENGERONLY and T8's NAVMASK both keep passengers off the helm).

<!-- from: vehicles-travel-instances/vti-helm-restrict -->

<a id="t9"></a>

## T9. Builder-set vessel pacing

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** How many ticks a vessel waits between moves and between turns are Java fields, `ticksPerMoves` and `ticksPerTurn`, both 1. Only `GenBalloon` changes them, in its constructor. They have no stat codes. A world-run vessel can be paced with `Patroller`'s `MIN` and `MAX`, but a player-steered one can only change `ABILITY`, which is rooms per tick and soon too fast.

**Add.** `TICKSPERMOVE` and `TICKSPERTURN` stat codes on `GenNavigableBoardable`, `GenCaravan`, `GenAirShip` and `GenBalloon`, set with `MODIFY` or `MPSET` and kept in catalog and recipe templates. Optionally a `WEATHERSPEED=true|false` switch for the wind bonus.

**Example.** A river barge with `ABILITY=1 TICKSPERMOVE=6` takes about a game hour to drift 25 rooms of river, so the trip downstream passes a sunset. An ox caravan and a fast courier get their own pace the same way.

<!-- from: vehicles-travel-instances/vti-vessel-pacing -->

<a id="t10"></a>

## T10. DelayedTransporter with messages, fares, conditions and party handling

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** `DelayedTransporter` takes `min`, `max`, `chance` and a list of rooms, and sends each mob to a random one when its own count runs out. There is no message, no look on arrival, no fare and no condition. It carries the mob's followers, but whoever's count ends first goes, so a follower may leave alone. Messages and conditions need a `GREET_PROG` or `DELAY_PROG` with `MPECHO`, or `Prop_ReqTattoo` on the exit.

**Add.** `DEPARTMSG=`, `ARRIVEMSG=`, `LOOK=true`, `FARE=<n>` (taken from the leader, or from each member with `PERHEAD=true`), `MASK=<zapper>` for who is carried, and `DEST=ORDER|RANDOM` to cycle the list in order or pick at random. `PARTY=true` carries the whole party when the first member's time is up, so no one is left behind or sent ahead alone.

**Example.** A druid circle: after 3 ticks in it, "The standing stones hum and the world folds...", and the party arrives at the Moonwell and sees it described. Only those with the GROVEFRIEND tattoo are carried (`MASK="-TATTOOS GROVEFRIEND"`).

*See also:* T5, T17 (Three fare rules for the party (T5's FARE, T10's PERHEAD, T17's PARTY=LEADER); settle one); T6 (Alternatives for a timed passage with messages and fares (Prop_Journey vs an extended DelayedTransporter)).

<!-- from: vehicles-travel-instances/vti-delayedtransporter-opts -->

<a id="t11"></a>

## T11. Unique interiors when vessels are copied, and a VESSEL command

*Kind:* builder tool · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** Every copy of a vessel keeps the same interior area name (the `shipbuilding.cmare` templates are all `UNNAMED_n`), and the Archon guide warns that ships sharing room IDs confuse the engine. The rename in `copyOf` is commented out, and catalog spawn and quest `LOAD` do not rename either. Changing a copy's name in `MODIFY ITEM` calls `Boardable.rename`, which fixes it, but on a cataloged item the change goes to the catalog entry instead. `GOTO <ship room id>` reaches the interior.

**Add.** On `COPY`, catalog spawn, quest `LOAD` or `CREATE` from a template, give the interior area and its room IDs a unique name derived from the item's name. An option on the catalog entry keeps a fixed name. A `VESSEL` builder command: `VESSEL <item> RENAME <name>`, `VESSEL <item> ENTER` (go to its first room to edit) and `VESSEL <item> HOME <room>`.

**Example.** The builder catalogs one "merchant cog" and places five on trade routes. Each gets its own interior (`Cog_Mira#0`, `Cog_Tern#0` and so on), and ship lookups never pick the wrong one.

*See also:* S15 (Both deal with vessels whose interiors share room IDs (copies in T11, double-saved owned ships in S15)); DT28 (Both add a VESSEL builder command (RENAME, ENTER and HOME vs STATUS)).

<!-- from: vehicles-travel-instances/vti-unique-interiors -->

<a id="t12"></a>

## T12. Waypoint network: fast travel between places found

*Kind:* property or behaviour · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** `Prop_Transporter` goes to one fixed room, and `TaxiBehavior` offers every destination to everyone. `Concierge` with `PORTAL=true` on a stone item (`Mirewood#1=20;Highpeak#4=20;PORTAL=true`) lets the player pick a priced destination, but lists them all from the start. Travel "between the stones you have found" takes a script per stone: `MPTATTOO` in a `GREET_PROG`, then a `SPEECH_PROG` that builds a list from `HASTATTOO` and uses `MPCHOOSE`, `MPMONEY` and `MPTRANSFER`.

**Add.** A declarative waypoint, as a `Prop_Waypoint` property or `Waystone` behaviour for items or rooms: `NETWORK=`, `NAME=`, `DISCOVER=ENTER|TOUCH|RITUAL|QUEST`, `COST=` (`moves 20`, `gold 5` or mana), `TIME=<game hours>` (T4), `MASK=` and `MSG=`. Found waypoints are saved per character or profile. `TRAVEL`, `TOUCH` or `USE` at one lists the found ones of that network; `TRAVEL <name>` moves the party there.

**Example.** Six elven waystones across the realm. The party attunes each as it explores, and later goes from the Mirewood stone to Highpeak in an hour of game time for 20 mana, but never to a stone it has not found.

*See also:* T4, T6, T18 (T6, T12 and T18 use T4's travel time).

<!-- from: properties/pr-waypoint, vehicles-travel-instances/vti-waystones -->

<a id="t14"></a>

## T14. Named ports, and COURSE TO a place

*Kind:* property, command · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** `COURSE` takes direction words only, so a player crossing an ocean grid works out the path step by step. `SEACHART ADD` and `SEANAVIGATE <n>` (`Skill_SeaCharting`, `Skill_SeaNavigation`) route to a charted point and set the course, but over water only, and to points the player made. `Concierge`, `TaxiBehavior` and `Patroller` route to rooms the builder lists. A builder cannot name a port or landmark the game understands.

**Add.** A `Prop_Port` property for rooms: `NAME="Port Azure"`, `KIND=SEA|ROAD|AIR|SPACE`, `HIDDEN=true|false` and an optional `KNOWNMASK` for who knows it. `COURSE TO <port|room id>`, `SAIL TO` and `DRIVE TO` compute a route over water, drivable rooms or air with the tracking library, refuse an unknown or unreachable place, and take the voyage's turn-based time. Points from `SEACHART` are accepted too. A `PORTS` command lists the known ports.

**Example.** On a bought sloop the player types `course to port azure`, and the ship sails the 40-room channel by itself. The hidden "Smuggler's Cove" becomes known when a quest gives the tattoo its `KNOWNMASK` asks for.

*See also:* T3, T5 (One route-finder serves T3's MPSAIL TO, T5's stops and T14's COURSE TO a named port).

<!-- from: vehicles-travel-instances/vti-course-to -->

<a id="t16"></a>

## T16. MovingRoom routes in the world files, with saved position

*Kind:* behaviour · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** `MovingRoom` reads its routes from one global file, `resources/movingroom.xml`, outside world/ and not exported. Its position (`currentStop`, `isReversed`, `currentStatus`) is in plain fields, reset on restore. It adds and removes exits and appends text to real room descriptions, and builder export reads the live rooms, so that text and those exits can likely end up in the world files (to verify). The line and display settings it parses (`LINEPRINTNAME`, `ROOMPRINTNAME`, `DISPLOC`) are never used.

**Add.** Let the parameters carry the route inline (`ROUTE=<xml>`) or name a file under `world/routes/`. Allow a timetable by game hour (`DEPART=`) as well as ticks. Save `currentStop`, `isReversed` and `currentStatus` in world.xml. Make the exits and description text temporary overlays that export skips. Show the line and where the car is on a sign item, or with a `TIMETABLE` command.

**Example.** The dwarven mine lift, its route in `world/routes/deepdelve-lift.xml`, visits Surface, Level 2 and the Deeps. A brass plate at each landing reads "Lift: descending to the Deeps". A save made on the lift restores it mid-shaft.

*See also:* S9, S17 (T16 saves MovingRoom's position, which S17 assigns to S9's STATE stat); T5 (Two timetabled routes with a saved position (TransportLine vs MovingRoom with DEPART=)).

<!-- from: vehicles-travel-instances/vti-movingroom-world -->

<a id="t17"></a>

## T17. Fares for the whole party

*Kind:* property and behaviour · *Value:* low · *Effort:* small · *Decision:* yes / no

**Today.** `Prop_TicketTaker` takes one parameter, the fare. It charges every player character who boards (mounts, sits, enters or sleeps aboard) and remembers who has paid. Party characters are all players, so each one must carry coin and pay alone. `TaxiBehavior` keeps one rider, who pays, and lets off only those who mounted. Neither has an option for the leader to pay, or for pets or cargo.

**Add.** A `PARTY=LEADER|EACH` parameter for both, and for the proposed transport line if it is approved. With `LEADER`, the leader pays once, per head, for the members and their mounts, and the others board without being stopped. `EACH` is today's rule. Optional `PETS=<price>` and `CARGO=<price per 100 lbs>`.

**Example.** A ferry gangplank charges 2 silver with `PARTY=LEADER PETS=5`. The leader of four with a horse pays 4 x 2 plus 5. The other three walk aboard.

*See also:* T5, T10 (Three fare rules for the party (T5's FARE, T10's PERHEAD, T17's PARTY=LEADER); settle one).

<!-- from: vehicles-travel-instances/vti-party-fares -->

<a id="t18"></a>

## T18. Builder-marked roads for caravans

*Kind:* property · *Value:* low · *Effort:* small · *Decision:* yes / no

**Today.** Caravans go only where `isDrivableRoom` (in `Sense`) allows, and it looks only at what the room's locale class fixes: its domain and, for caves and indoor stone, its movement cost (base weight) and size (height). Seaport, spaceport, city and plains always pass. Caves pass only with a base weight over 3, and indoor stone only with a set weight and height. Nothing overrides this, and a locale's domain is fixed. A forest road, mountain pass or desert track must become a plains or city locale such as `Road`, and loses its terrain and resources.

**Add.** A `Prop_Road` property for rooms or areas. It makes them drivable for `GenCaravan` vehicles, coach caravans included (Wainwrighting's wagons and coaches are `GenRideable` and already go anywhere but air and water), and is checked before the domain rule. Optional `TIME=<multiplier>`, which combines with the proposed travel-time setting, and `NOTFOR=<vehicle names>`.

**Example.** The Old Forest Road is a strip of `WoodsGrid` rooms with `Prop_Road`. The player's spice caravan follows it through the forest. The rest of the forest stays closed to it.

*Re-checked against the code after writing: corrected.*

*See also:* T4, T6, T12 (T6, T12 and T18 use T4's travel time).

<!-- from: vehicles-travel-instances/vti-drivable-rooms -->

<a id="t19"></a>

## T19. Sister stables for mounts

*Kind:* behaviour · *Value:* low · *Effort:* medium · *Decision:* yes / no

**Today.** Most of this exists. The `Nanny` behaviour, set for example to `RATE=2.5 NAME="The Stables" WATCHES="mounts,wagons,boats"`, guards the mounts, wagons or boats left in its room once the owner leaves. They are given back only when the hourly fee is paid (the first game hour is free). They do not decay and cannot be attacked. The drop-offs are saved in the behaviour's parameters. But a mount can be collected only where it was left.

**Add.** A `NETWORK=<name>` parameter for `Nanny`. A mount left at one stable of the network can be collected at another for `SENDFEE=<n>`, paid on top of the fee. Perhaps also explicit `STABLE`, `RETRIEVE` and `LIST` verbs, and a rate per game day.

**Example.** The party leaves its horses at the Port Azure livery before taking ship. Three game days later it collects them at Gullrock's sister stable and pays the keep and the send fee.

<!-- from: vehicles-travel-instances/vti-stable -->
