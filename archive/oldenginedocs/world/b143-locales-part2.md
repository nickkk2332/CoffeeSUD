# Locales part 2: terrain, water, grid, maze, sky, space and ship rooms

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b143` (Locales [part 2/3: MountainsMaze .. StdRoom]) · 31 files · 7131 lines of Java.

Citations are `path:line`, with paths relative to `com/planet_ink/coffee_mud/` (the prefix is written once here and dropped below, as in the b171 chapter). Short names: `StdRoom` is `Locales/StdRoom.java`, `StdGrid` is `Locales/StdGrid.java`, and so on. The room, grid and exit base mechanics are covered in depth by b171 and are only summarised here. Items marked **[unverified]** were reasoned from the code but not run. Resource weights were extracted from the source with a script, not from a running game.

## Summary

This bucket holds the concrete room types from `MountainsMaze` to `StdRoom` in the Locales package, plus the grid and maze base classes they depend on. Almost every terrain type is a short class that declares a name, a movement cost, a domain code, a climate mask and a static list of resources the room can yield, and leaves the behaviour to `StdRoom`, `StdGrid` and `StdMaze`. A grid is one object that stands for a rectangle of identical child rooms (5 by 5 by default), built all at once (`StdGrid`) or created on demand and expired after about 41 minutes (`StdThinGrid`), and a maze is a grid whose internal passages are carved as a perfect maze. Water rooms supply unlimited drink, a sea surface lazily creates the underwater grid below itself, sky ledges require flight or a fall, and ship decks and holds take their capacity limits from a property string. For a single-player Python game the terrain table, weighted resource pools, grid templates and maze carving are the parts worth borrowing.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Locales/StdRoom.java` | Base room (read in part here; full mechanics in b171) | 3363 |
| `Locales/StdGrid.java` | Fat grid: builds the full child array, edge links, entry mapping, clearing | 1284 |
| `Locales/StdMaze.java` | Grid whose internal passages are a randomised perfect maze | 212 |
| `Locales/ShallowWater.java` | Drinkable, endless fresh water with no sea below | 190 |
| `Locales/SpacePort.java` | Port placed on a planet surface, with coordinates from the space library | 125 |
| `Locales/Plains.java` | Grassland, outdoor, 28 resources | 93 |
| `Locales/ShipDeck.java` | Ship's deck (WoodenDeck base), capacity 20 items / 300 weight / 0 siege | 88 |
| `Locales/ShipLightGunDeck.java` | Gun deck, capacity 20 / 400 / 1 siege | 88 |
| `Locales/ShipMedGunDeck.java` | Gun deck, capacity 20 / 600 / 2 siege | 88 |
| `Locales/ShipHeavyGunDeck.java` | Gun deck, capacity 20 / 1000 / 3 siege | 88 |
| `Locales/ShipHold.java` | Ship hold (WoodRoom base), capacity 100 items / 10000 weight | 88 |
| `Locales/ShipMagazine.java` | Magazine (WoodRoom base), capacity 200 items / 1000 weight | 88 |
| `Locales/ShipQuarter.java` | Crew quarters (WoodRoom base), capacity 100 items / 1000 weight | 88 |
| `Locales/SaltWaterSurface.java` | Sea surface with an underwater salt grid below | 80 |
| `Locales/SaltWaterThinSurface.java` | Thin variant of the sea surface | 85 |
| `Locales/SaltWaterSurfaceColumn.java` | Column variant of the sea surface | 67 |
| `Locales/Shore.java` | Coast, wet, 3 resources | 76 |
| `Locales/SeaPort.java` | Port on the coast (extends Shore) | 54 |
| `Locales/Road.java` | Road on plains domain, 5 resources, movement 1 | 71 |
| `Locales/MountainsMaze.java` | Mountain maze, movement 5, child `Mountains` | 67 |
| `Locales/MountainsThinGrid.java` | Thin mountain grid, movement 5 | 76 |
| `Locales/PlainsGrid.java` | Fat grid of Plains | 67 |
| `Locales/PlainsThinGrid.java` | Thin grid of Plains | 75 |
| `Locales/RoadGrid.java` | Fat grid of Road | 68 |
| `Locales/RoadThinGrid.java` | Thin grid of Road | 76 |
| `Locales/ShoreGrid.java` | Fat grid of Shore | 67 |
| `Locales/OceanGrid.java` | Fat grid of SaltWaterSurface | 67 |
| `Locales/OceanThinGrid.java` | Thin grid of SaltWaterSurface | 67 |
| `Locales/SewerMaze.java` | Dark, wet sewer maze of SewerRoom children | 70 |
| `Locales/SewerRoom.java` | Dark, wet sewer tunnel | 59 |
| `Locales/OverTheLedge.java` | Ledge in the sky with entry rules | 56 |

Classes outside the bucket that these depend on: `Locales/WaterSurface.java` (sea-surface base, creates the sea below), `Locales/InTheAir.java` (air base, flight and falling), `Locales/WoodenDeck.java` and `Locales/WoodRoom.java` (ship bases), `Locales/StdThinGrid.java` (lazy grid base, b171), `Locales/Mountains.java`, `Locales/Woods.java`, `Locales/UnderWater.java` and `Locales/UnderSaltWater.java` (resource lists), `Abilities/Properties/Prop_ReqCapacity.java` (ship capacity), `Libraries/MUDTracker.java` (walking), `Libraries/MUDFight.java` (`expendEnergy`, movement and thirst per call), `Common/DefaultClimate.java` (weather multipliers), `Common/DefaultCharState.java` (`adjThirst`), `Libraries/Sense.java` (`isInDark`), `Libraries/CommonMsgs.java` (hygiene), `Common/DefaultPlayerStats.java` (room visits), `core/Directions.java`, `core/interfaces/Places.java` (climate bits), `Common/interfaces/PhyStats.java` (disposition and sense bits), `core/CMProps.java` and `core/CMSecurity.java` (settings and grid switches), `Libraries/interfaces/WorldMap.java` (expiry constant), `core/collections/IntegerRangeMap.java`, `Items/interfaces/RawMaterial.java` (resource frequencies). The space chapter is in NEWOVERVIEW as b035.

## How it works

### 1. What a locale class declares

Most classes in this bucket follow one shape. `Plains` is typical:

```java
public Plains()
{
    super();
    name="the grass";
    setMovementCost(2);
    recoverPhyStats();
}
public int domainType() { return Room.DOMAIN_OUTDOORS_PLAINS; }
public List<Integer> resourceChoices() { return Plains.roomResources; }
```

(`Locales/Plains.java:43-55`, `:88-92`.) A type fixes five things:

- **Name.** `name` is the default text shown for the room (`Locales/StdRoom.java:51` defaults to `"the room"`).
- **Movement cost.** `setMovementCost` writes the base weight of the room (`StdRoom.java:165-169`). The value is read back by `getMovementCost()` (`:171-174`) and is the number of movement points one step costs (section 3).
- **Domain code.** A `domainType()` constant (`Locales/interfaces/Room.java:78` for `INDOORS=128`; outdoor codes at `:83-107`, for example `PLAINS=3`, `WATERSURFACE=6`, `UNDERWATER=4`, `AIR=5`, `DESERT=9`, `MOUNTAINS=11`, `SPACEPORT=12`, `SEAPORT=13`). The domain drives several rules: digging (`StdRoom.java:815-841`), sky creation (`:559-563`) and room size.
- **Climate mask.** `climask` is one of `CLIMASK_INHERIT=-1`, `CLIMASK_NORMAL=0` or `CLIMASK_WET=1` (`core/interfaces/Places.java:91-95`, other bits defined alongside). `INHERIT` means the room takes the area's climate (`StdRoom.java:359-380`).
- **Resources and base thirst.** `resourceChoices()` returns the list used by `myResource()` (section 4), and `baseThirst()` returns the room's base thirst (`StdRoom.java:2865-2868`, default 1).

The `StdRoom` constructor sets a default movement cost of 2 and a room size of 1 indoors or 10 outdoors (`StdRoom.java:86-94`, `:176-185`). Room size becomes `maxRange()` (`:2895-2898`), which is the ranged-combat limit described in b171.

### 2. Catalogue of the room types

Grouped by kind. "Inherit" means the value comes from the base class shown in the Base column. Movement is the value passed to `setMovementCost`, or the inherited default of 2 where none is set.

| Type (class) | Kind and base | Name | Domain | Move | Climate | Base thirst | Resource list |
|---|---|---|---|---|---|---|---|
| Plains | Outdoor flat | the grass | PLAINS | 2 | inherit | 1 | 28 items (section 4) |
| PlainsGrid | Fat grid of Plains | (grid, children "the grass") | PLAINS | 2 | inherit | 1 | Plains list |
| PlainsThinGrid | Thin grid of Plains | (inherits "the room") | PLAINS | 2 | inherit | 1 | Plains list |
| Road | Outdoor flat | a road | PLAINS | 1 | inherit | 1 | 5 items: STONE 80, SAND 50, CLAY 50, FEATHERS 20, SCALES 20 |
| RoadGrid / RoadThinGrid | Grid of Road | a road | PLAINS | 1 | inherit | 1 | Road list |
| Shore | Coast | the shore | DESERT | 2 | WET | 1 | FISH 100, SAND 50, HIDE 20 |
| ShoreGrid | Fat grid of Shore | the shore | DESERT | 2 | inherit | 1 | Shore list |
| SeaPort | Port on the coast, extends Shore | the sea port | SEAPORT | 2 | WET | 1 | Shore list |
| ShallowWater | Drinkable shallows, extends StdRoom | the water | WATERSURFACE | 2 | WET | 0 | UnderWater list (fresh) |
| SaltWaterSurface | Sea surface, extends WaterSurface | the water | WATERSURFACE | 2 | WET | 1 | UnderSaltWater list plus SALMON and FISH (FISH twice) |
| SaltWaterThinSurface | Thin sea surface | the water | WATERSURFACE | 2 | WET | 1 | as SaltWaterSurface |
| SaltWaterSurfaceColumn | Column sea surface, extends WaterSurfaceColumn | inherit | inherit | inherit | inherit | 1 | SaltWaterSurface list |
| OceanGrid / OceanThinGrid | Grid of SaltWaterSurface | (grid) | WATERSURFACE | 2 | inherit | 1 | Woods list (see Problems) |
| MountainsMaze | Maze of Mountains | the room | MOUNTAINS | 5 | inherit | 1 | Mountains list, 30 items |
| MountainsThinGrid | Thin grid of Mountains | the mountains | MOUNTAINS | 5 | inherit | 1 | Mountains list |
| SewerRoom | Dark sewer tunnel | the sewers | INDOORS_CAVE | 2 | WET | 1 | none |
| SewerMaze | Dark sewer maze of SewerRoom | the room | INDOORS_CAVE | 2 | WET | 1 | empty list |
| OverTheLedge | Ledge in the sky, extends InTheAir | the sky | AIR | 1 | inherit | 1 | none |
| SpacePort | Port on a planet surface | the space port | SPACEPORT | 1 | inherit | 1 | none |
| ShipDeck and three gun decks | Ship deck, extends WoodenDeck | the ship deck | OUTDOORS_CITY | 1 | inherit | 1 | none |
| ShipHold, ShipMagazine, ShipQuarter | Ship interior, extends WoodRoom | the ship hold / magazine / quarter | INDOORS_WOOD | 1 | NORMAL | 1 | none |

Sources for the table: names and domains from the constructors and `domainType()` overrides cited in sections 5 to 12; movement costs from `MountainsMaze.java:46`, `MountainsThinGrid.java:47`, `Road.java:47`, `SpacePort.java:49`, `InTheAir.java:46`, `WoodenDeck.java:47`, `WoodRoom.java:46`; the fat and thin grids follow their child type (`getGridChildLocaleID`).

Four rows need a note. `SaltWaterSurface` does not set its own name, domain or climate, so it inherits `WaterSurface`'s "the water", domain and wet climate; `WaterSurface` sets `setMovementCost(2)` and `climask=WET` in its constructor. `SaltWaterSurface.domainType()` is inherited, and the domain it returns for the sea below is `UNDERWATER` (`SaltWaterSurface.java:54-58`). `ShallowWater` is the only room in this bucket whose base thirst is 0 (`ShallowWater.java:61-64`). The ship decks inherit the `WoodenDeck` climate (no `climask` is set) and the holds get `NORMAL` from `WoodRoom.java:48`.

### 3. Movement, climate and thirst: how the numbers are used

These are the engine calls that consume the numbers above. They are reached on every step.

**Movement points.** `pointsPerMove()` returns the area climate's `adjustMovement(getMovementCost(), this)` (`StdRoom.java:2860-2863`). The climate function multiplies the base cost in storms and does nothing indoors (`Common/DefaultClimate.java:616-629`):

- thunderstorm x2, hail x2, dust storm x3, blizzard x4;
- indoor rooms return the base unchanged (`DefaultClimate.java:618-619`).

Walking goes through `MUDTracker.move` (`Libraries/MUDTracker.java:1665`). Before the leave and enter messages are sent (`MUDTracker.java:1835`, `:1845`), a non-monster mob is charged `WALKCOST` calls to `expendEnergy(mob,true)` (`MUDTracker.java:1783`), or `RUNCOST` calls when running. The defaults are 1 and 2 (`core/CMProps.java:3132-3133`). `expendEnergy` subtracts `-room.pointsPerMove()` from movement, and if the mob carries more than its maximum, the cost is increased in proportion to the excess (`Libraries/MUDFight.java:3426-3442`). The charge is taken before the move, so the cost comes from the room being left **[unverified in edge cases]**. Each walk also adds `pointsPerMove()` to hygiene (`MUDTracker.java:1799`), and a mob with zero or less movement is refused with "You are too tired" (`MUDTracker.java:1785-1790`).

**Thirst per round.** `thirstPerRound()` starts from `baseThirst()` and adds terrain bits: +1 hot, -1 wet, +1 dry, +1 windy (`StdRoom.java:2871-2886`). It then passes the value through the area climate's `adjustWaterConsumption`, which returns the base unchanged indoors and otherwise multiplies it: drought x4, dust storm x3, heat wave x2, rain and thunderstorm halve it (`DefaultClimate.java:588-613`). A room with no climate object returns 0 (`StdRoom.java:2883-2885`).

The thirst part of `expendEnergy` is not gated by `expendMovement`. It subtracts `thirstPerRound() * factor` from the mob's thirst, where `factor` is `baseWeight/500` with a floor of 0.001 (`MUDFight.java:3445-3452`). It runs on every walk step and on every recovery tick of a non-monster mob (`MOBS/StdMOB.java:4203`). A positive adjustment is a gain, so it quenches thirst (`Common/DefaultCharState.java:250-265`).

Net effect per terrain, from the base values alone:

- Plains, Road, SpacePort, ships and the gun decks: +1 per round, the default.
- Shore, SeaPort, SewerRoom, SewerMaze and the sea surfaces: base 1 plus the wet bit of -1 gives 0.
- ShallowWater: base 0 plus the wet bit of -1 gives -1 in clear weather, which is a net gain of thirst relief. This means standing or walking in shallow water slowly quenches thirst, and the effect is scaled by body weight. **[unverified in play]**
- MountainsMaze and MountainsThinGrid: thirst is normal but movement costs 5 points per step.

### 4. Resources

A room's yield is `myResource()` (`StdRoom.java:691-742`):

- The result is cached in the room and is refreshed when the cached value is older than 30 minutes (`StdRoom.java:694-697`, `30*TimeManager.MILI_MINUTE`).
- When a new value is needed, the code builds a weighted map from `resourceChoices()` using each resource's frequency (`StdRoom.java:716-725`). Frequencies come from the `frequency` field of `RawMaterial` entries (`Items/interfaces/RawMaterial.java:823`, read through `FREQUENCY` at `:1390-1392`). The Javadoc says "1-10" (`:831`), but the data goes to 100, so the comment is out of date.
- Neighbouring rooms of the same class that already have a yield get an extra share of the map (`StdRoom.java:705-734`). This clusters a resource across a region.
- A null list means no resource (`StdRoom.java:679-682`, `:701-703`).

The map is keyed by inclusive integer ranges (`core/collections/IntegerRangeMap.java:112-120`), and each entry is built as `[min, min+freq]` with the next minimum at `max+1` (`StdRoom.java:721-724`). Each resource therefore gets `freq+1` slots, a small skew.

Resource frequencies for the lists in this bucket (weights are the `frequency` field):

| List (class) | Entries and weights |
|---|---|
| Plains (`Plains.java:57-86`) | wheat 20, hops 20, barley 20, corn 20, rice 20, smurfberries 1, greens 5, vegetable 5, carrots 5, tomatoes 5, beans 15, onions 1, garlic 1, flint 10, flowers 10, cotton 20, meat 20, herbs 10, eggs 10, beef 20, hide 20, dirt 50, fur 20, honey 50, feathers 20, leather 20, fresh water 10, wool 20 |
| Road (`Road.java:57-63`) | stone 80, sand 50, clay 50, feathers 20, scales 20 |
| Shore (`Shore.java:64-69`) | fish 100, sand 50, hide 20 |
| UnderWater, used by ShallowWater (`UnderWater.java:307-319`) | seaweed 50, fish 100, catfish 10, salmon 18, carp 1, trout 10, sand 50, clay 50, limestone 20 |
| UnderSaltWater, used by SaltWaterSurface (`UnderSaltWater.java:49-61`) | seaweed 50, fish 100, tuna 10, shrimp 15, sand 50, clay 50, pearl 1, limestone 20, salt 20 |
| SaltWaterSurface additions (`SaltWaterSurface.java:68-73`) | salmon 18, fish 100 (fish is already in the UnderSaltWater list) |
| Mountains (`Mountains.java:57-88`) | stone 80, dirt 50, iron 10, alabaster 10, lead 10, copper 10, tin 10, herbs 10, amber 5, spice 5, crystal 5, silver 2, amethyst 2, garnet 2, opal 2, topaz 2, basalt 2, shale 2, pumice 2, sandstone 2, soapstone 2, aquamarine 2, chrysoberyl 2, nickel 2, onyx 1, turquoise 1, diamond 1, quartz 1, palladium 1, platinum 1 |
| Woods, used by OceanGrid (`Woods.java:76-110`) | 33 entries from the forest list, including trees, fruit, honey, fur and leather |

### 5. Water rooms

**ShallowWater** (`ShallowWater.java`) extends `StdRoom` and implements `Drink`. Its water is fresh by default (`:43`) and effectively endless: `liquidHeld` and `liquidRemaining` return `Integer.MAX_VALUE - 1000` (`:119-128`). It never decays (`:67-75`) and each drink quenches 500 (`:113-116`). Drinking salt water is refused when the liquid type is salt (`:80-86`). Each message in the room is passed to the hygiene handler with the value 100 and the clean-water type (`:97`), which is implemented in `Libraries/CommonMsgs.java:2227-2243` and reports "You feel a little cleaner" style messages. `ShallowWater` does not create a sea below.

**Sea surfaces.** `WaterSurface` (`Locales/WaterSurface.java`, outside the bucket) is the base for all three sea surfaces, and its code is what the subclasses tune:

- `giveASky` (`WaterSurface.java:110-172`) creates the sea below as a grid whose class is `UnderWaterLocaleID()` (`:72-74`), and links the neighbouring rooms' sea grids to it sideways. It returns early for children of temporary grids (`:119-122`).
- `fixUnderwater` runs on most messages (`:200-212`, called from `okMessage` at `:214-224` and `executeMsg` at `:231-237`). If the link down is missing, it clears the sea and creates it again (`clearSky` at `:177-196`).
- `WaterSurface` also gives `thirstQuenched` 1000 and `WET` climate (constructor, `setMovementCost(2)`).

The salt variants override three hooks. `SaltWaterSurface` uses `UnderSaltWaterGrid` (`SaltWaterSurface.java:48-52`), reports `UNDERWATER` as the sea's domain (`:54-58`), and recognises the salt underwater grids (`:60-66`). `SaltWaterThinSurface` uses `UnderSaltWaterThinGrid` (`SaltWaterThinSurface.java:58-62`) and falls back to the fat `SaltWaterSurface` when thin grids are disabled (`:78-84`). `SaltWaterSurfaceColumn` uses `UnderSaltWaterColumnGrid` (`SaltWaterSurfaceColumn.java:48-60`) and offers the salt surface list (`:62-66`).

**Ocean grids.** `OceanGrid` (fat) and `OceanThinGrid` (thin) are `WATERSURFACE` grids of `SaltWaterSurface` children, with movement 2 (`OceanGrid.java:46`, `:53`, `:57-60`). Their resource lists are the forest list (see Problems).

### 6. Grids: fat and thin

**Fat grid (`StdGrid`).** The grid holds a 2-D array `subMap` of child rooms, description and display-text pools, a list of cross exits, and `xsize` and `ysize`, which default to 5 (`StdGrid.java:47-52`). `buildGrid` creates the whole array once (`:887-925`). It is called lazily by `getBuiltGrid` when `subMap` is null (`:347-354`).

Each child is made by `getGridRoom` (`:1159-1195`). It takes the child class from `getGridChildLocaleID()`, clears its room ID so it is a child rather than a primary room, sets its parent and area, and copies the grid's text. If the pools are set, one random index is drawn and used for both the display text and the description, so the two stay paired when the pools have the same length (`:1172-1185`). The grid's effects and behaviours are copied into every child (`:1187-1193`).

The pool text is written with `<P>` separators. `setDescription` and `setDisplayText` split on them (`:204-240`), so one field carries several variants.

**Thin grid (`StdThinGrid`).** Children are created when first accessed (`getMakeGridRoom` at `StdThinGrid.java:590`, `getGridChild` at `:316-318`) and stored in a list (`:52-53`). Every access sets the child's expiry to now plus `ROOM_EXPIRATION_MILLIS = 2500000` ms, about 41.7 minutes (`StdThinGrid.java:298-304`, `Libraries/interfaces/WorldMap.java:48`). `buildGrid` only clears the grid (`:884-887`). The b171 chapter covers the expiry path in detail.

**Switching.** Two settings choose the implementation at creation time: `THINGRIDS` ("thin uncached grids") and `FATGRIDS` ("standard cached grids") in `core/CMSecurity.java:3008-3009`. `StdGrid.newInstance` rewrites `XGrid` to `XThinGrid` when `FATGRIDS` is disabled (`StdGrid.java:93-109`). Each thin class does the reverse when `THINGRIDS` is disabled, for example `PlainsThinGrid.newInstance` returns `new PlainsGrid().newInstance()` (`PlainsThinGrid.java:57-62`; the same pattern in `RoadThinGrid.java:58-62` and `MountainsThinGrid.java:58-62`).

**Grid families.** Every grid family in the bucket is the same pattern with a different child:

| Family | Fat | Thin | Child | Notes |
|---|---|---|---|---|
| Plains | PlainsGrid | PlainsThinGrid | Plains | movement 2 |
| Road | RoadGrid | RoadThinGrid | Road | movement 1, name "a road" on the grid |
| Mountains | (MountainsGrid, other bucket) | MountainsThinGrid | Mountains | movement 5 |
| Shore | ShoreGrid | (none in bucket) | Shore | default movement 2 |
| Ocean | OceanGrid | OceanThinGrid | SaltWaterSurface | sea surfaces with sea below |
| Sewer | (maze only) | (none) | SewerRoom | see section 9 |

**Entry from outside.** A grid also acts as its own entrance. When someone enters the grid object itself, `StdGrid.okMessage` finds the direction they came from and redirects the message to the child that matches the entry point (`StdGrid.java:1198-1232`, `getAltRoomFrom` at `:1221`). If no direction matches, the player is told "Some great evil is preventing your movement that way." (`:1215-1218`).

**Placement at build time.** After building, `distributeStuff` moves any mobs and loose floor items on the grid object into random children (`:946-970`). It is skipped for temporary grids (those with no room ID) and when an administrator is present.

**Addressing.** A child's code is `ID#(x,y)` (`getGridChildCode`, `:1068-1085`), and `getGridChild(String)` parses it back (`:1128-1145`). This gives children a stable address for saving.

### 7. Edges, links and cross exits

Inside a fat grid, `buildGrid` links each new child to the neighbours that are already built: north, west, north-west and south-west (`StdGrid.java:899-912`). The diagonal links are only made when the eight-direction configuration is loaded (`:906`, `Directions.NORTHEAST < NUM_DIRECTIONS()`). The build order is column by column, which is why south-west is already available.

The outer edges are linked to the neighbouring rooms by `buildFinalLinks` (`:744-807`). Each direction's neighbour (`doors[d]`) is linked to every child on the matching edge: the top row for north, the bottom row for south, the right column for east, the left column for west, and the single corner child for the diagonals. `fillInTheExtraneousExternals` (`:833-885`) handles cross exits that leave from an edge or corner child, linking them to their destination room when the grid has no link on that side.

When a move enters the grid from outside, `getGridRoomFrom` maps the entry to the opposite edge at the same offset. Moving east into the grid arrives in the west column on the same row (`case WEST`, `StdGrid.java:288-291`), and a diagonal arrives at the corresponding corner (`:293-296`, `:307-310`). If the two grids are different sizes, or there is no edge match, the code falls back to the centre child on that edge (`findCenterRoom`, `:697-742`, using `initCenterRoomXY` at `:620-657`).

Cross exits are kept in `gridexits` (`StdGrid.java:50`). `getAltRoomFrom` checks them first (`:318-345`) and uses the child at the named coordinates if the exit matches the direction and the destination room ID. Linking helpers `linkRoom` and `halfLink` (`:538-618`) set the exit on both sides with `Open` exits by default.

### 8. Mazes

`StdMaze` extends `StdGrid` (`StdMaze.java:35`). Building a maze (`buildGrid`, `:187-211`) creates the children, carves the maze from the centre cell (`buildMaze`, `:179-185`, using `xsize/2` and `ysize/2`), then calls `buildFinalLinks` and `distributeStuff`.

**Carving.** `mazify` (`:130-177`) is a depth-first search over the grid that keeps a visited set:

1. From the current cell, if any neighbour is unvisited, pick a random direction by rolling over `NUM_DIRECTIONS()` and retry until the roll hits an unvisited neighbour (`:156-172`).
2. Link the two cells with an `Open` exit (`:136`, `:173`), then recurse into the neighbour (`:174`).
3. Repeat from the same cell while unvisited neighbours remain.

The result is a spanning tree, so every cell is reachable and there are no loops.

**Directions.** `goodDir` (`:80-97`) refuses UP, DOWN and any code at or above `GATE`. `GATE` is 6 (`core/Directions.java:87`), and the diagonals are 7 to 10, so mazes are cardinal even when the eight-direction configuration is loaded. The outer edges block movement (`x==0` for west, `y==0` for north, and so on).

**Exploration flag.** Each maze cell gets `SENSE_ROOMUNEXPLORABLE` (`StdMaze.java:48-59`, `Common/interfaces/PhyStats.java:495`). `DefaultPlayerStats.addRoomVisit` skips rooms with that bit (`Common/DefaultPlayerStats.java:1651`), so walking a maze does not count as visiting its rooms for room-visit tracking. The exits are `Open`, not doors, so a maze has no locks.

**Failure path.** `buildGrid` catches `Exception`, clears the grid and does nothing else (`StdMaze.java:207-210`). `StdGrid.buildGrid` logs the error instead (`StdGrid.java:920-923`).

Types: `MountainsMaze` (`MountainsMaze.java:46`, movement 5, child `Mountains`, resources at `:63-66`) and `SewerMaze` (`SewerMaze.java:47-50`, child `SewerRoom`).

### 9. Sewers and dark rooms

`SewerRoom` and `SewerMaze` set the `IS_DARK` disposition in their constructors (`SewerRoom.java:48`, `SewerMaze.java:47`). `IS_DARK` is `128` (`Common/interfaces/PhyStats.java:567`), and `Libraries/Sense.java:1047-1049` reports it through `isInDark`. What a dark room hides from a viewer depends on the callers of `isInDark`, which were not traced. Both rooms are `WET` (`SewerRoom.java:51`, `SewerMaze.java:50`), which with base thirst 1 makes thirst neutral, and the indoor rule means the climate does not change it further (`DefaultClimate.java:590-591`). The sewer maze's resource list is an empty `Vector` (`SewerMaze.java:66-69`), and `SewerRoom` returns `null`, so neither yields resources.

### 10. Sky ledges and air

`InTheAir` (`InTheAir.java`, outside the bucket) is the base of `OverTheLedge`. It sets movement 1 (`:46`), the domain `AIR` (`:54`), and the name "the sky". Its `okMessage` (`:58-65`) runs the base checks, then `isOkAirAffect` and `airAffects`.

- **Entry.** A mob that enters the air must be flying or falling, otherwise "You can't fly." (`InTheAir.java:158`). Sitting and sleeping in the air are refused (`isOkAirAffect`, `:134` onward).
- **Weather.** On entry, a roll over 50 (`:161`) checks the weather: a blizzard, hail, rain, sleet, thunderstorm or strong wind refuses the entry with a message such as "The hard winds inhibit <S-YOUPOSS> progress."
- **Falling.** `airAffects` (`:67-124`) looks at each mob and each loose item in the room. Anything without a `Falling` effect, that is not the start room of a mob, is sent to `makeFall` (`:124`).

`OverTheLedge` (`OverTheLedge.java:44-54`) changes two of these rules:

- If the room is flagged as sleeping, `okMessage` returns true before any other check (`:46-47`).
- An entry into the ledge from anywhere other than the room directly below (`getRoomInDir(DOWN)`) returns true without the flight and weather checks (`:49-52`). An entry from below falls through to the `InTheAir` rules.

So a ledge can be reached sideways without flying, while climbing up from below needs flight or a fall. The class name suggests the opposite, and the intent is **[unverified]**.

`StdRoom.giveASky` skips rooms in the air domain (`StdRoom.java:559-563`), so ledges never get a sky above them.

### 11. Space port

`SpacePort` (`SpacePort.java:35`) implements `LocationRoom`, uses the `SPACEPORT` domain and has movement 1 (`:49`). Its coordinates are not stored. `coordinates()` finds the planet with `CMLib.space().getSpaceObject(this,true)` and returns `CMLib.space().getLocation(planet.coordinates(), dirFromCore, planet.radius())` (`:59-66`). `dirFromCore` is a two-element direction that `setDirectionFromCore` accepts only when its length is 2 (`:74-79`). It is exposed as the `COREDIR` stat (`:81`, `:88`, `:100`). The planetary geometry is in the space library and is covered by the b035 chapter.

### 12. Ship decks and holds

The four decks extend `WoodenDeck` (outdoor city domain, movement 1, deck ambiance) and the three interiors extend `WoodRoom` (indoor wood domain, normal climate, movement 1). Ship capacity is a `Prop_ReqCapacity` property (`Abilities/Properties/Prop_ReqCapacity.java`) that each class builds in its constructor from a text string:

```java
capacityA=CMClass.getAbility("Prop_ReqCapacity");
capacityA.setMiscText("items=20 weight=300 siegeweapons=0");
capacityA.setAffectedOne(this);
capacityA.makeNonUninvokable();
```

(`Locales/ShipDeck.java:49-54`.) The property is never added to the room's effect list. Instead, `okMessage` calls it before the normal checks (`ShipDeck.java:82-87`). `copyOf` and `newInstance` rebuild the property each time (`:60-79`).

The parameters parsed in `Prop_ReqCapacity` are `items` (count), `weight` (summed item weight), `siegeweapons` (which defaults to the `items` value, `Prop_ReqCapacity.java:116-117`), `people`, `players`, `mobs`, `rooms`, `indoor`, and `droponly` or `containersok` (`:113-121`). Entering is refused when the people cap is reached: "No more people can fit in there." (`:392-400`).

| Room | Base | Capacity string | Siege slots |
|---|---|---|---|
| ShipDeck | WoodenDeck | `items=20 weight=300 siegeweapons=0` (`ShipDeck.java:52`) | 0 |
| ShipLightGunDeck | WoodenDeck | `items=20 weight=400 siegeweapons=1` (`ShipLightGunDeck.java:52`) | 1 |
| ShipMedGunDeck | WoodenDeck | `items=20 weight=600 siegeweapons=2` (`ShipMedGunDeck.java:52`) | 2 |
| ShipHeavyGunDeck | WoodenDeck | `items=20 weight=1000 siegeweapons=3` (`ShipHeavyGunDeck.java:52`) | 3 |
| ShipHold | WoodRoom | `items=100 weight=10000` (`ShipHold.java:52`) | 100 by default |
| ShipMagazine | WoodRoom | `items=200 weight=1000` (`ShipMagazine.java:52`) | 200 by default |
| ShipQuarter | WoodRoom | `items=100 weight=1000` (`ShipQuarter.java:52`) | 100 by default |

Because the decks are outdoor-classed, the sky rule in `StdRoom.giveASky` (`StdRoom.java:559-563`) would give them a sky above unless `SKYSIZE` is 0. This was not checked in play **[unverified]**.

### 13. Messages players see

Short, quotable lines from these files (paraphrased where they include tokens):

- "You don't want to be drinking saltwater." (`ShallowWater.java:84`)
- "You are no longer thirsty." and "You have drunk all you can." (`ShallowWater.java:105`, `:108`)
- "You feel a little cleaner; almost perfect." and related hygiene lines (`Libraries/CommonMsgs.java:2233-2243`)
- "You can't really dig here." (`StdRoom.java:818`, digging is allowed only in the listed outdoor domains, `:823-835`)
- "You can't shop here." and "You can't do that here." (`StdRoom.java:809`, `:849`)
- "Some great evil is preventing your movement that way." (`StdGrid.java:1217`)
- "You can't fly." (`InTheAir.java:158`), and the weather refusals at `:161-180`
- "No more people can fit in there." (`Prop_ReqCapacity.java:400`)
- "You are too tired." (`MUDTracker.java:1785-1790`)

## Content worth mining

- **Resource catalogues by terrain.** The lists in section 4 give a usable table for a Python game: each terrain is a few `(resource, weight)` pairs. Road has stone, sand, clay and feathers, and the sea gives seaweed, fish, shrimp, tuna, pearl and salt. The mountain list is an ore table (iron, lead, silver, copper, tin, several gems, platinum), with stone at 80 and dirt at 50.
- **Names and short descriptions.** "the grass", "a road", "the shore", "the sea port", "the water", "the sewers", "the sky", "the space port", "the mountains" and the four ship interiors. Each is a one-line name the game can reuse.
- **Ship tiers.** The three gun decks form a progression. The light, medium and heavy gun decks have weight limits 400, 600 and 1000 and 1, 2 and 3 siege slots (section 12). This is a ready-made, data-only upgrade ladder.
- **Weather rules.** The weather multipliers for movement (x2 storms, x3 dust, x4 blizzard) and for water consumption (x2 heat, x3 dust, x4 drought, halving in rain) in `DefaultClimate.java:588-629` are a ready table.
- **Sewer and maze texture.** The sewer rooms and maze give a dark, wet underground region built from two classes. The sewer name, dark flag and wet climate are the ingredients.
- **Air rules.** Flight or falling to enter, and a 50 percent weather check, give a complete rule for climbing or flying in one paragraph of code.

## Notable design ideas

1. **A region is a template, not a list of rooms.** `StdGrid` stores one object with dimensions, child class, and description and display-text pools. Children are copies that pick a variant at random (section 6). This is a compact way to describe a large region of similar rooms.
2. **Entering a region lands you on the matching edge.** `getGridRoomFrom` maps an entry from the east to the west column at the same row, and corners to corners (section 6). The map feels continuous without the builder placing any links by hand.
3. **Two implementations behind one name.** Fat and thin grids are the same class family, and a configuration flag chooses between them at creation time (section 6). The same world can run with a small memory footprint or with everything materialised.
4. **Resources cluster.** A new yield favours the resources of neighbouring rooms of the same class, and yields refresh after 30 minutes (section 4). This gives regional character with almost no data.
5. **The sea below is made on demand.** A surface creates its underwater grid when it is first needed and checks the link on each message (section 5). Depth is free until someone goes there.
6. **Water as a thirst source.** Shallow water has a base thirst of 0 and a wet climate, so standing in it reduces thirst (section 3). The rule comes from the same two numbers that describe every other terrain.
7. **Capacity as a string on the room.** Ship limits are one text line in each constructor (section 12). A builder can read and edit it without code.
8. **A maze cell that does not count as visited.** The `SENSE_ROOMUNEXPLORABLE` bit (section 8) lets the exploration system skip maze squares without changing the maze.

## Problems and cruft

1. **FISH is listed twice in the salt-water surface.** `SaltWaterSurface.java:68-73` starts from `UnderSaltWater.resourceList`, which already contains `RESOURCE_FISH` (`UnderSaltWater.java:49-61`), and then adds `RESOURCE_FISH` again. Fish therefore carry double the weight in the map at `StdRoom.java:721-724` (weight 200 of about 434 in the salt-water list **[unverified in play]**). This is a clear data bug.
2. **Ocean grids use the forest list.** `OceanGrid.java:63-66` and `OceanThinGrid.java:63-66` return `Woods.roomResources`, a list of trees, fruit and fur (`Woods.java:76-110`). This is almost certainly copy-paste. The grid's own list matters only if something asks the grid object for a resource; children use the sea list **[unverified whether any caller does]**.
3. **An empty list where null means none.** `SewerMaze.resourceChoices()` returns an empty `Vector` (`SewerMaze.java:66-69`), but `StdRoom.myResource` treats only `null` as "no resource" (`StdRoom.java:701-703`). With an empty list the code reaches `map.get(...)` on an empty map and calls `.intValue()` on the result, which is null (`StdRoom.java:735-738`). This is a latent null dereference if the maze object is ever asked for a resource **[unverified whether it is]**.
4. **Off-by-one weights.** Each resource gets `freq+1` slots because the ranges are inclusive (section 4). The skew is small but it is in every table.
5. **Mazes fail quietly.** `StdMaze.buildGrid` swallows all exceptions without logging (`StdMaze.java:207-210`), while `StdGrid` logs them (`StdGrid.java:920-923`). The carving is recursive (`StdMaze.java:130-177`), and a deep enough maze would raise a `StackOverflowError`, which this `catch` does not handle. The size at which this happens is **[unverified]**.
6. **Rebuilds delete items.** `StdGrid.buildGrid` clears the grid first (`StdGrid.java:890`), and `clearGrid` destroys loose items in the children unless they are owned property (`:1015-1051`). `WaterSurface.clearSky` can clear the sea below and `fixUnderwater` can call it (`WaterSurface.java:177-196`, `:200-212`). Anything a player dropped on the sea floor or in a grid child is lost when the grid is rebuilt.
7. **`OverTheLedge` skips every other veto when asleep.** `okMessage` returns true before calling the base class (`OverTheLedge.java:46-47`), so exit, effect and behaviour vetoes are bypassed. Its flight rule applies to entry from below, which is the opposite of what the name suggests (`:49-52`).
8. **Side effects during the veto phase.** `InTheAir.okMessage` calls `airAffects` (`InTheAir.java:63`), which can start falls. The veto phase is supposed to be a check, not a change of state.
9. **Hidden primary-room convention.** Grid and sewer classes set `_roomID` to their class name in the constructor (`SewerRoom.java:46`, `SewerMaze.java:46`, `StdGrid.java:57`, and the matching line in `StdThinGrid`). This makes a freshly made instance look like a primary room, and `getGridRoom` clears it for children (`StdGrid.java:1166`). `distributeStuff` depends on it (`StdGrid.java:948`). The convention is undocumented and easy to break.
10. **Copy-pasted ship classes.** The four decks have the same 88-line body and differ in three numbers, and each rebuilds its capacity property in three places (`ShipDeck.java:52`, `:64`, `:75`). All four are named "the ship deck" (`ShipDeck.java:48`, `ShipHeavyGunDeck.java:48`, and the others), so players see the same name for different tiers.
11. **Shared sense bit.** `SENSE_ROOMUNEXPLORABLE` and `SENSE_ITEMUNLEARNABLE` both have the value 512 (`PhyStats.java:495`, `:497`). This is harmless because the two apply to different object types, but it is confusing.
12. **Water resources ignore the liquid.** `ShallowWater.resourceChoices()` always returns the fresh-water underwater list (`ShallowWater.java:186-189`), even though `setLiquidType` can change the liquid (`:143-146`).

Multiplayer assumptions in this bucket: the people, player and mob caps in `Prop_ReqCapacity` (`:392-412`), the faction and achievement updates on entry (`StdRoom.java:781-786`), and the random placement of other mobs into grids (`StdGrid.java:946-970`). A single-player game needs none of the caps and only the NPC-versus-player distinction.

## Takeaways for a Python SUD

1. **Terrain as a small table of records.** Port each locale type as one record, not one class. A minimal sketch:

   ```python
   @dataclass(frozen=True)
   class Terrain:
       name: str                       # "the grass"
       move_cost: int                  # points per step
       indoors: bool = False
       wet: bool = False
       base_thirst: int = 1
       resources: tuple[tuple[str, int], ...] = ()   # (resource, weight)

   PLAINS = Terrain("the grass", 2, resources=(("wheat", 20), ("honey", 50), ("dirt", 50)))
   ```

   Keep the numbers from sections 2 and 4. Drop `domain` unless a rule depends on it.
2. **Weighted resource draws with `random.choices`.** Use weights as given, without the `+1` skew (section 4). Keep the 30-minute refresh, expressed as game turns, and optionally the neighbour bonus if regions should feel regional.
3. **One movement-cost function with weather.** `step_cost(terrain, weather)` can reproduce the storm multipliers and the indoor exception in one place (section 3). Charge the cost at one clear point; CoffeeMud charges the room being left, which is a choice worth making deliberately.
4. **Thirst per step, not per tick.** Tie thirst to the terrain and the step (section 3), so that walking through shallow water quenches thirst and walking through a desert costs more. Drop the body-weight factor unless carrying matters for the game.
5. **Region template with lazy cells.** A `Region(width, height, cell_factory, descriptions)` that creates cells in a dict on first access gives the thin-grid behaviour without two classes (section 6). Pick one mode; the fat/thin switch is a memory optimisation that a single-player game can skip.
6. **Edge-matched entry in a few lines.** When the player moves east from region A into region B, place them in B's west column on the same row (section 6). Store edges as neighbour references, and keep the centre fallback for mismatched sizes.
7. **Mazes by recursive backtracker.** About 20 lines of Python: a visited set, a shuffled list of the four cardinal directions, and recursion (section 8). Do not copy the diagonal or door handling; a single-player maze needs neither. Keep an "explored" flag only if the game tracks exploration.
8. **Water as a flag.** Make drinkable water a terrain flag with `quench=500` and no sea below (section 5). The underwater grid is a large feature; add it only if underwater play is in scope.
9. **Ledges as a requirement.** A room can carry `requires=("flying",)` and a per-entry check with a weather modifier (section 10). Drop falling, the sleeping bypass and the veto-phase side effects; the rule can be one function.
10. **Ship holds as containers.** A room with `max_items` and `max_weight` replaces the property system (section 12). Give each ship interior its own name, and keep siege slots only if combat needs them.
11. **Dark rooms as a flag.** `dark=True` on a room and a visibility check that reads it (section 9). The sewers become a one-line terrain.
12. **Drop the multiplayer machinery.** The people, player and mob caps, the faction and achievement hooks on entry, and the two-phase veto pass are not needed for one player. Use direct method calls (for example `region.enter(from_direction)`) instead of `okMessage` redirects (section 6, `StdGrid.java:1198-1232`).

## Coverage

- **Read in full:** `Plains.java`, `Road.java`, `ShallowWater.java`, `SpacePort.java`, `Shore.java`, `SeaPort.java`, `SaltWaterSurface.java`, `SaltWaterThinSurface.java`, `SaltWaterSurfaceColumn.java`, `OverTheLedge.java`, `SewerRoom.java`, `SewerMaze.java`, `StdMaze.java`, `MountainsMaze.java`, `ShipDeck.java`, `PlainsGrid.java`, `RoadGrid.java`, `OceanGrid.java`, `OceanThinGrid.java`, `PlainsThinGrid.java`, `RoadThinGrid.java`, `MountainsThinGrid.java`, `ShoreGrid.java`, and `StdGrid.java` (all 1284 lines, excluding the licence header). Bodies were read from the first class line on, so the import and licence blocks were skipped.
- **Read by comparison:** `ShipHeavyGunDeck.java`, `ShipLightGunDeck.java`, `ShipMedGunDeck.java`, `ShipHold.java`, `ShipMagazine.java` and `ShipQuarter.java` were diffed against `ShipDeck.java`, and their declaration, name, capacity, `newInstance` and `okMessage` lines were grepped. The bodies are otherwise identical.
- **Skimmed:** `StdRoom.java` (lines 40-209, 350-390, 540-750, 745-906, 1289-1330 and 2858-2898, plus the method list). The rest is covered in b171. `StdThinGrid.java` was read in part (class header, `getGridRoomIfExists`, `getGridChild`, `getMakeGridRoom`, `buildGrid`). `WaterSurface.java` and `InTheAir.java` were read in full as base classes outside the bucket.
- **Outside the bucket, read for interactions only:** `WoodenDeck.java`, `WoodRoom.java`, `Prop_ReqCapacity.java` (parameters and the enter check), `DefaultClimate.java` (movement and water functions), `MUDFight.java` (`expendEnergy`), `MUDTracker.java` (`move`, the walk charge and the leave and enter messages), `DefaultCharState.java` (`adjThirst`), `StdMOB.java` (one recovery-tick line), `CommonMsgs.java` (hygiene handler), `Sense.java` (`isInDark`), `DefaultPlayerStats.java` (`addRoomVisit`), `IntegerRangeMap.java` (range comparison), `core/Directions.java`, `core/interfaces/Places.java`, `PhyStats.java` (bit values), `CMProps.java` (walk and run costs), `CMSecurity.java` (grid flags), `WorldMap.java` (expiry constant), and the resource lists in `Mountains.java`, `Woods.java`, `UnderWater.java`, `UnderSaltWater.java` and `RawMaterial.java`. The weights were extracted with a script that parsed the source.
- **Skipped:** `StdRoom.java` bodies of `executeMsg`, `tick`, `bringMobHere`, `moveItemTo` and the fetch methods, and `StdThinGrid.java` beyond the parts above (b171). `Mountains.java`, `MountainsGrid`, `WaterSurfaceColumn.java` and `UnderWater.java` belong to other buckets and were not read beyond their resource lists.
- **Not run:** no code was executed. Every number above comes from reading the source, and items marked **[unverified]** were not checked in a running game.
