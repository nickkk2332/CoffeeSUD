# Locales (part 1): grid locales, sky and sea, and the terrain rooms

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b142` (Locales [part 1/3: GridLocale .. MountainsGrid]) · 60 files · 6700 lines of Java.

Citations are `path:line`. Paths are relative to `com/planet_ink/coffee_mud/`, and that prefix is dropped (so `Locales/AutoGrid.java:126` means `com/planet_ink/coffee_mud/Locales/AutoGrid.java:126`). Items marked **[unverified]** were reasoned from the code and not run. The room, grid and exit base classes (`StdRoom`, `StdGrid`, `StdThinGrid`, `StdMaze`, `StdExit`), the two-phase message flow and the movement and climate pipeline are covered in chapter `b171`; this chapter refers to it rather than repeating it.

## Summary

This bucket holds the physical world of CoffeeMud's outdoors and much of its indoors. It has 34 single-room classes (jungle, desert, hills, mountains, caves, roads, stone and wooden rooms, ice, water, air), and each sets a movement cost, a climate mask, a domain code, a weighted list of gatherable resources and sometimes a hazard. Grid locales tile a rectangle of identical rooms in two forms, fully built ("fat") and lazily built ("thin"), and the sky grids wrap their edges while the sea grids send edge exits back to their middle, so neither shows a far side. AutoGrid is a grid whose interior is generated from a layout XML file when a player comes near, and the air and water rooms add their own rules for flight, falling, swimming, encumbrance and sinking. For a text game the reusable parts are the terrain-as-data pattern (one record per biome with movement, climate, thirst and a weighted resource table), the wrap-around sky and the generated-interior grid; most of the rest depends on CoffeeMud's room-object model and on other players being present.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Locales/interfaces/GridLocale.java` | Contract for a room that holds a rectangle of child rooms; `CrossExit` record for outer exits | 183 |
| `Locales/AutoGrid.java` | Grid whose interior is generated from a layout XML, on demand, with an expiry timer | 744 |
| `Locales/EndlessSky.java` | Fully built sky grid of `InTheAir` cells, edges wrap | 219 |
| `Locales/EndlessThinSky.java` | Lazily built sky grid with the same wrap rules | 241 |
| `Locales/EndlessOcean.java` | Fully built sea grid; edges reconnect to the middle row or column | 188 |
| `Locales/EndlessThinOcean.java` | Lazily built sea grid with the same edge rule | 170 |
| `Locales/InTheAir.java` | Air room: entry checks, weather veto, falling | 188 |
| `Locales/IndoorInTheAir.java` | Indoor air ("the space") that reuses the `InTheAir` statics | 75 |
| `Locales/IndoorUnderWater.java` | Underwater room; an effectively unlimited fresh-water source | 184 |
| `Locales/CaveRoom.java` | Base cave room, cave resource list, rare disease hook | 122 |
| `Locales/Jungle.java`, `Locales/Desert.java` | Warm terrains with thirst, climate and disease hooks | 112, 99 |
| `Locales/Hills.java`, `Locales/Mountains.java` | Hill and mountain terrains with resource lists | 90, 95 |
| `Locales/ClimbableSurface.java`, `Locales/ClimbableLedge.java` | Rock surface carrying a climbing property; ledge with ladder handling | 97, 64 |
| `Locales/MagicShelter.java`, `Locales/HideoutShelter.java` | Protected rooms built from property effects | 104, 89 |
| `Locales/IcePlains.java`, `Locales/FrozenPlains.java` | Cold plains; both return the warm plains resource list (see problems) | 83, 59 |
| `Locales/interfaces/Room.java` | Domain codes and descriptive arrays (rest of the contract is in b171) | 1003 |
| `Locales/interfaces/LocationRoom.java` | Room at a coordinate on a planet surface; implemented by `SpacePort` | 69 |

The 22 grid and maze classes are catalogued in the content section. Together with `AutoGrid`, `GridLocale`, `Room` and `LocationRoom`, they make up 26 of the 60 files; the other 34 are single-room classes.

## How it works

### 1. Terrain rooms are data on a `StdRoom` subclass

Each terrain room is a `StdRoom` subclass that sets its parameters in the constructor and overrides a few methods. Jungle is the template:

- name: `name="the jungle"` (`Locales/Jungle.java:46`);
- movement cost: `setMovementCost(3)` (`Jungle.java:47`), the weight that `pointsPerMove()` turns into a step cost (b171 section 7);
- climate: `climask=Places.CLIMASK_WET|CLIMASK_HOT` (`Jungle.java:49`). The bits are `CLIMASK_WET=1`, `COLD=2`, `WINDY=4`, `HOT=8`, `DRY=16`, `VOID=32`, with `INHERIT=-1` and `NORMAL=0` (`core/interfaces/Places.java:91-105`);
- domain: `domainType()` returns `Room.DOMAIN_OUTDOORS_JUNGLE` (`Jungle.java:53-56`). The codes are in `Locales/interfaces/Room.java:81-107` (outdoors, 0 to 13) and `:134-152` (indoors, base `INDOORS=128` at `Room.java:78`);
- resources: a static `resourceList` and `roomResources` (`Jungle.java:80-105`) returned by `resourceChoices()` (`Jungle.java:107-111`);
- hazards, when present, in `executeMsg` (section 8).

A class that does not set a climate (Hills, Mountains, Road) inherits one from its area, as b171 section 7 describes. Road sets none, and the `Places` default applies.

### 2. Gathering: a weighted roll with neighbour clustering

Every terrain shares the resource code in `StdRoom.myResource()` (`Locales/StdRoom.java:692-742`):

1. The cached resource is cleared after 30 minutes (`StdRoom.java:696-697`).
2. A weight table is built from `resourceChoices()`. Each entry's weight is `RawMaterial.CODES.FREQUENCY(code)` (`StdRoom.java:716-725`, frequency read at `:722`), which is the third field of each row of the `RawMaterial` table (`Items/interfaces/RawMaterial.java:633` for CACTUS, `:642` for SAND with 50, `:683` for GEM with 1, `:816` for DIRT with 50; accessor at `:1390-1392`).
3. For each neighbour in the four cardinal and diagonal directions that has the same `ID()`, holds a resource, and returns the identical list object (`R.resourceChoices()==resourceChoices()`, `StdRoom.java:711`), a bonus range of `map.getMax() / 4 / n` is added for that neighbour's resource (`StdRoom.java:728`), where `n` is the number of such neighbours.
4. The roll is `CMLib.dice().roll(1, map.getMax(), 0)` (`StdRoom.java:736`), and the room keeps the resource that the roll falls in.

Two consequences follow. The weights matter a lot, so the dominant item of a biome is much more common than the rest (table in section 2a below). And the neighbour bonus makes deposits patchy: a desert or cave that already has sand or stone tends to repeat it. The identity check on the list (`StdRoom.java:711`) means that a subclass returning a copy of the list would silently lose the bonus. Every class in this bucket returns its static list, so this does not happen today.

**2a. Weights from the lists.** Computed from the `RawMaterial` frequency field and the lists in this bucket. The total counts duplicate entries, so GEM in the cave list is counted twice.

| Terrain list | Entries | Total weight | Dominant entries (weight, share) |
|---|---|---|---|
| Desert (`Desert.java:83-91`) | 7 | 102 | sand 50 (49%), scales 20 (20%), water 10, lamp oil 10, cactus 5, peppers 5, dates 2 |
| Cave (`CaveRoom.java:78-114`) | 35 | 275 | stone 80 (29%), dirt 50 (18%), mushrooms 20, scales 20, then ten items at 10 or fewer |
| Jungle (`Jungle.java:80-105`) | 23 | 237 | dirt 50 (21%), scales, fur and feathers 20 each, berries 15 |
| Mountains (`Mountains.java:57-88`) | 30 | 237 | stone 80 (34%), dirt 50 (21%), tin, iron, alabaster, herbs, lead, copper 10 each |
| Hills (`Hills.java:57-82`) | 24 | 235 | dirt 50 (21%), leather, rice, feathers, wool 20 each, beans and berries 15 |
| Road (`Road.java`, `resourceList`) | 5 | 220 | stone 80 (36%), sand 50, clay 50, feathers 20, scales 20 |
| Underwater, salt water, shore (other classes, used by water rooms) | 9, 9, 3 | 309, 316, 170 | fish 100 (32% of underwater) |

### 3. Grid locales: the child contract, fat and thin

`GridLocale` (`Locales/interfaces/GridLocale.java:51-157`) is a `Room` that owns child rooms. `getGridChildLocaleID()` (`GridLocale.java:58`) names the child class by ID, so a grid is always a container for one kind of terrain. `CrossExit` (`GridLocale.java:164-182`) is the record for an outer exit from a cell (coordinates, direction, destination ID, and whether it leaves the grid).

The fat and thin forms differ only in their superclass. `DesertGrid` extends `StdGrid` and `DesertThinGrid` extends `StdThinGrid`, and both name `Desert` as the child and return `Desert.roomResources` (`DesertGrid.java:59-68`, `DesertThinGrid.java:66-75`). When the admin setting `THINGRIDS` is disabled, each thin class's `newInstance()` returns the fat class instead (`DesertThinGrid.java:58-64`; the same pattern at `EndlessThinSky.java:71-77`, `EndlessThinOcean.java:58-64`, `GreatThinLake.java:58-64`, `IndoorWaterThinSurface.java:79-85`).

The three mazes (`CaveMaze.java`, `DesertMaze.java`, `MetalRoomMaze.java`) extend `StdMaze` and use the depth-first carve described in b171 section 6.

### 4. The sky: endless by wrapping

Both sky grids read `SKYSIZE`, take its absolute value, and fall back to 3 if it is 0 (`EndlessSky.java:52-62`, `EndlessThinSky.java:52-62`). The setting is documented in `coffeemud.ini:1061-1069`: 0 means no sky, the default is 3, and a leading minus hides the exit to the sky. The same value sets the size of the underwater grids (`UnderWaterThinGrid.java:51-52`).

**Fat sky** (`EndlessSky.buildGrid`, `EndlessSky.java:165-218`). Cells link north-south and east-west inside the grid. Then the edges wrap:

- west edge to east edge and top to bottom (`EndlessSky.java:202-205`);
- diagonal wraps: NW joins `[0][0]` to `[last][last]`, and NE joins `[last][0]` to `[0][last]` (`EndlessSky.java:208-211`, guarded by `Directions.NORTHEAST<NUM_DIRECTIONS()`).

The UP direction chains the columns. A cell's north neighbour is also its UP neighbour (`EndlessSky.java:180-184`), and the first cell of each column links UP to the last cell of the previous column (`EndlessSky.java:198-207`).

**Thin sky** (`EndlessThinSky.fillExitsOfGridRoom`, `EndlessThinSky.java:104-240`). Each new cell asks `getMakeSingleGridRoom` for its neighbour, which creates it on demand. On an edge, the link goes to the cell on the opposite edge: a north exit from `y==0` goes to `(x, last)` (`EndlessThinSky.java:182-187`), and west from `x==0` goes to `(last, y)` (`:196-200`). Diagonals follow the same pattern (`:210-238`). The sky also keeps its own link to the real room above or below when one exists (`:117-125`, `:149-157`).

**Entry.** `StdGrid.getGridRoomFrom` ends by calling `findCenterRoom(opDirection)` (`StdGrid.java:315`). `EndlessSky` overrides it so that arrival from below (the outer room is in direction DOWN from the sky) lands on the far corner `[last][last]` (`EndlessSky.java:96-103`). Other grids use the centre.

**Attachment.** The sky is created by `StdRoom.giveASky` above an outdoor room that has no UP exit (`StdRoom.java:543-625`; b171 section 7). Water surfaces override it, which is covered in section 6.

### 5. Air: entry rules, weather and falling

`InTheAir` (`InTheAir.java:35-132`) is the room type for sky cells. Its rules live in two statics that `IndoorInTheAir` and the sky grids reuse.

- **Entering** (`InTheAir.isOkAirAffect`, `InTheAir.java:134-187`). A mob that enters must already be flying or falling, or it gets "You can't fly." (`:158`). Sitting and sleeping are refused with "You can't sit here." (`:143`) and "You can't sleep here." (`:148`) unless the action is forced.
- **Weather veto** (`InTheAir.java:161-182`). On a roll above 50 (`rollPercentage()>50`, `:161`), six weather types stop the entry with a visible message: blizzard ("The swirling blizzard inhibits <S-YOUPOSS> progress.", `:166`), hail, rain, sleet, thunderstorm ("The thunderstorm inhibits <S-YOUPOSS> progress.", `:178`) and windy ("The hard winds inhibit <S-YOUPOSS> progress.", `:181`). The other weather types do not block entry. So at those weathers, a half of attempts fail.
- **Falling** (`InTheAir.airAffects`, `InTheAir.java:67-125`). It collects mobs and loose items in the room that have no `Falling` effect (`:75-94`, `:95-113`, loose items only when they are not in a container, `:111`). Mobs whose start room is this room are exempt (`:78-79`). Each one goes to `CMLib.tracking().makeFall(...)` (`:123-124`), which is `Libraries/MUDTracker.java:2439-2469`. That method needs a DOWN room (or an UP room when reversed), skips flying or in-flight subjects, and then invokes the `Falling` ability (`Abilities/Misc/Falling.java`) with misc text `NORMAL` or `REVERSED`.
- **Reversal** (`InTheAir.java:115`). If any existing `Falling` effect is reversed and none is normal, new falls are reversed. The code is written for things that rise; how a reversed effect looks to a player is **[unverified]**.

### 6. Water: surfaces, the sea below, and underwater rooms

**Surfaces.** `EndlessOcean`, `GreatLake`, `IndoorWaterSurface` and `IndoorShallowWater` share a veto from `CMLib.tracking().isOkWaterSurfaceAffect` (`EndlessOcean.java:78-90`; `Libraries/MUDTracker.java:2471-2530`). Entering, leaving or fleeing needs swimming ability or a boat. Otherwise the message is "You need to swim or ride a boat that way." (`MUDTracker.java:2516`). A swimmer whose weight is over half their maximum carry is refused with "You are too encumbered to swim." (`MUDTracker.java:2522-2524`).

**Sinking.** `UnderWater.sinkAffects` (`Locales/UnderWater.java:86-135`) is called from `executeMsg` of each water room (`EndlessOcean.java:74`, `IndoorUnderWater.java:103`). It gives a `Sinking` effect to a mob that is not swimming, with the same `REVERSED` pattern as falling (`UnderWater.java:124-135`, reversal read at `:166`).

**The sea below.** `WaterSurface.giveASky` (`Locales/WaterSurface.java:110-175`) overrides the sky rule for water. For a surface with no DOWN exit, not underwater and not in the air (`:124-127`), it creates an underwater grid below through `UnderWaterLocaleID()` (`:135`). The link is an open doorway when `SKYSIZE>0` and an unseen walkway when negative (`:131-133`). Neighbouring seas are then joined where they exist (`:150-172`). Each concrete surface names its sea class: `IndoorWaterSurface` names `IndoorUnderWaterGrid` (`IndoorWaterSurface.java:59-62`), `IndoorWaterThinSurface` names `IndoorUnderWaterThinGrid` (`IndoorWaterThinSurface.java:60-63`), and `IndoorWaterSurfaceColumn` names `IndoorUnderWaterColumnGrid` (`IndoorWaterSurfaceColumn.java:59-62`).

**Underwater.** `UnderWater.isOkUnderWaterAffect` (`UnderWater.java:172-215`) is the veto for underwater rooms. Fire and gas fail with "That won't work underwater." (`:181`). Slashing and bashing weapon damage is reduced. The code computes `damage = value/3*2` and subtracts it from `value` (`:189-194`), so about a third of the damage remains. Drinking is allowed unless the liquid is salt water, which is refused with "You don't want to be drinking saltwater." (`:205`). The drink messages are "You are no longer thirsty." (`:98`) and "You have drunk all you can." (`:101`).

**Drink source.** `IndoorUnderWater` is an unlimited fresh-water source. It sets `thirstQuenched()` to 500 (`IndoorUnderWater.java:115`), `liquidHeld()` and `liquidRemaining()` to `Integer.MAX_VALUE - 1000` (`:121`, `:127`), `baseThirst()` to 0 (`:62-65`), and `decayTime()` to 0 (`:68-71`).

### 7. Edge rules of the sea and lake grids

Sea grids do not wrap. Each outer edge with no exit of its own links to the middle of the grid instead.

- `EndlessOcean.buildGrid` (`EndlessOcean.java:93-187`). After the normal build, the north edge `(x,0)` links NORTH to `(x, yGridSize()/2)` (`:98-106`). South, east and west follow the same rule (`:107-130`), and the diagonals link edge cells to the centre `[xGridSize()/2][yGridSize()/2]` (`:131-185`).
- `EndlessThinOcean.fillExitsOfGridRoom` (`EndlessThinOcean.java:101-169`) does the same lazily. For example, `y==0` with no north exit links NORTH to `getMakeSingleGridRoom(x, yGridSize()/2)` (`:114-119`).

The effect is a patch of sea whose far edge is never reached: walking off one side brings you to the middle of the sea. That is the opposite of the sky, which wraps to the far edge. The code has no comment on the intent, so the choice is **[unverified]** as a design decision.

### 8. Hazards in terrain rooms

Three terrains add rare, neutral-toned illness effects. They are gated the same way, so only a character who is inside, not a monster, and wounded (current HP below maximum) can get one.

- **Cave** (`CaveRoom.executeMsg`, `CaveRoom.java:61-76`). Two independent 1% rolls (`:66-67`) give a 1-in-10,000 chance per qualifying message. The effect is `Disease_Syphilis`, applied only if the character lacks it and `AUTODISEASE` is enabled (`:71-73`).
- **Jungle** (`Jungle.java:58-78`). The same double roll (`:64-65`), then a 50/50 choice between `Disease_Gonorrhea` (roll above 50) and `Disease_Malaria` (`:70-73`).
- **Desert** (`Desert.java:64-81`). One 1% roll (`:69`), but only if movement is at most half its maximum (`:73`). The effect is `Disease_Eczema` (`:75`).

Thirst is the other terrain number. `baseThirst()` is 4 for Desert (`Desert.java:58-62`) and DesertMaze, 1 for CaveSeaPort (`CaveSeaPort.java:53-56`), and 0 for IndoorShallowWater (`IndoorShallowWater.java:59-62`) and IndoorUnderWater. The climate then adds to it, as b171 section 7 explains (`StdRoom.java:2871-2886`).

### 9. Climbing: rock surfaces and ledges

`ClimbableSurface` (`ClimbableSurface.java:35-97`) carries a `Prop_Climbable` effect in `climbA` and forwards `okMessage`, `executeMsg` and `recoverPhyStats` to it (`:75-96`). The property does the work:

- `Prop_Climbable.okMessage` refuses a move into the room with "You need to climb that way, if you know how." (`Abilities/Properties/Prop_Climbable.java:100`) unless the mover is already climbing, flying or falling. A ladder in the room or the room below mounts the mover automatically (`:92-96`, `postMountLadder`), and the same for the fall check at `:155-166`.
- `Prop_Climbable.executeMsg` makes a non-climbing mob fall when it moves DOWN through an open DOWN exit (`:171`, via `makeFall`).
- Items thrown or dropped over the room fall (`:133`, `:140`).

`ClimbableLedge` (`ClimbableLedge.java:35-64`) adds a rule for entries. An entry from the room below must climb (`:58-61`). An entry from anywhere else returns `true` at `:59` before the parent checks run, so the veto chain for that message is skipped. A ladder in the room mounts the entrant (`:52-57`).

Movement costs: `ClimbableSurface` 4 (`:48`), `CaveSurface` 4 (`CaveSurface.java:47`), `MountainSurface` 6 (`MountainSurface.java:46`).

### 10. Shelters and anti-magic rooms

These rooms are built from property effects rather than code.

- **MagicShelter** (`MagicShelter.java:42-81`). It attaches five property effects, each made non-savable (`:50-74`): `Prop_PeaceMaker`, `Prop_NoRecall`, `Prop_NoSummon`, `Prop_NoTeleport` and `Prop_NoTeleportOut`. Its movement cost is 0 (`:48`). Its `okMessage` refuses RECALL and LEAVE with "You can't leave the shelter that way.  You'll have to revoke it." (`:96-100`, text at `:99`). The description is "You are in a domain of complete void and peace." (`:47`).
- **Prop_PeaceMaker** on a room. A malicious action aimed at someone else is blocked, and the default text is "You feel too peaceful here." (`Abilities/Properties/Prop_PeaceMaker.java:91`). The room can supply its own list of replies, chosen at random, through the property's text (`:79-82` and `:92-95`).
- **Prop_NoRecall** replies "<S-NAME> attempt(s) to recall, but the magic fizzles." (`Abilities/Properties/Prop_NoRecall.java:80`). Prop_NoSummon and Prop_NoTeleport fizzle quietly (`Prop_NoSummon.java:94-99`, `Prop_NoTeleport.java:146`).
- **HideoutShelter** (`HideoutShelter.java:34-89`) has the same five properties, is dark (`:49`), has the display text "Secret Hideout" (`:46`), and sits in the wooden domain (`:87`).
- **MagicFreeRoom** (`MagicFreeRoom.java:35-57`) adds one property, `Prop_MagicFreedom` (`:48`), whose display name is "Anti-Magic Field" (`Abilities/Properties/Prop_MagicFreedom.java`). It vetoes any message that carries `MASK_MAGIC` on the source, target or others, and it prints "Magic energy fizzles and is absorbed into the air." (`Prop_MagicFreedom.java:88`).

### 11. Roads as speed tiers

`Road` (`Road.java:35-71`) is a plains-domain room with movement cost 1 (`:47`). `LongRoad`, `LongerRoad` and `LongestRoad` extend it and differ only in the cost: 5 (`LongRoad.java:46`), 10 (`LongerRoad.java:46`) and 20 (`LongestRoad.java:46`). Travel cost is therefore a property of the room class, not of the route. The road resource list is stone, feathers, scales, sand and clay (table in section 2a).

### 12. AutoGrid: a grid generated from a layout

`AutoGrid` (`AutoGrid.java:44-745`) extends `StdGrid` and implements `AutoGenArea`. It is a grid whose interior is produced by the area generator (`CMLib.percolator().fillInArea`, `AutoGrid.java:298-300`; the generator is described in chapter b020) from an XML file. The data for the XML comes from the room definition: `XSIZE` and `YSIZE` give the total room count, and `OTHERVARS` passes variables. The shipped example is `resources/randareas/lair.xml`, where an AutoGrid is the level below a lair, with `XSIZE=5 YSIZE=5` and `OTHERVARS="AGGROCHANCE=... THEME=... LEVEL_RANGE=... multi_level=true"` (`resources/randareas/lair.xml:690-698`).

**Setup.** The area is chosen from `AREA_ID`/`AREA_IDS` variables, or from the `<area>` tags that are defined in the file (`AutoGrid.java:150-195`). If `LEVEL_RANGE` is missing it defaults to 1, and `AGGROCHANCE` to 50, each with a warning (`:218-227`). A layout piece is chosen at random (`:230-233`), and a `THEME` is chosen from the unfilled requirements when none is given (`:234-240`). Requirements are checked with `checkRequirements` (`:243`).

**Gate tagging.** AutoGrid finds the outside room that links back to it (`:254-267`). It passes that room, its exit and its direction to the generator through keys such as `ROOMTAG_GATEEXITROOM` and `ROOMTAG_NODEGATEEXIT`, with direction-suffixed copies (`:268-296`). The generator therefore knows where the entrance must be.

**Lifecycle.** The timing is the core of AutoGrid, and it is set in four places:

- `canAutoGenerateNow` (`:107-123`) requires the host to be running (`:109-110`). An instance child or parent, or a grid with no room ID, generates only if a player is nearby (`:114-121`). `playerNearby` (`:84-105`) checks the grid's outside doors and the doors of those rooms, so a player within two steps counts.
- While generating, `expires` is set to eight ticks ahead (`:134`), and when generation is not allowed it is set to two ticks ahead and a plain `StdGrid` is built instead (`:128-132`). With the default `TICKTIME=4000` (`coffeemud.ini:2151`, `CMProps.java:681`) these are 32 seconds and 8 seconds.
- `getBuiltGrid` clears the grid once the timer has passed (`:77-82`). Clearing destroys the children, moves mobs and items out, and sets `subMap` to null (`StdGrid.java:973-1061`).
- A successful generation sets `expires = Long.MAX_VALUE` (`:403`), so the grid stays until `resetInstance` (`:657-661`) or an explicit clear.

**Placement.** Layout nodes are shifted so the minimum coordinate is 0 (`:318-336`). The array is sized to the largest index, plus one (`:346-351`). Leftover rooms, which are generated but not placed in a node, go to free cells nearest the centre (`:366-400`, sort at `:377-385`). The entrance is the first node, joined to the outside room for the gate direction (`:401`, `:456-459`).

**Interior links.** `buildFinalLinks` (`:439-596`) joins each outside direction to a cell found by a search from the edge or the centre. The search differs by direction (`:465-584`). Outside rooms are linked through `doors[d]` (`:447`), and `getGridRoomFrom` uses the `roomLinks` map to route an entry (`:430-437`).

**Documentation.** `guides/RandomAreas.html:520-536` says AutoGrid rooms are generated only after the area loads, that an embedded AutoGrid waits for a nearby player, and that generation takes as long as any random area. The code agrees with all three (`AutoGrid.java:114-121`, `:126-134`).

## Content worth mining

**Terrain catalogue (single-room classes).** Movement is the `setMovementCost` value (inherited where the class sets none). Climate is the mask in the constructor. Resources are the list used by `resourceChoices()`.

| Class | Room name | Move | Climate | Thirst | Domain | Resources |
|---|---|---|---|---|---|---|
| `Jungle` | the jungle | 3 | WET, HOT | 1 (default) | outdoors jungle | 23, dirt dominant |
| `Desert` | the desert | 2 | HOT, DRY | 4 | outdoors desert | 7, sand 49% |
| `Hills` | the hills | 3 | inherited | 1 | outdoors hills | 24, dirt 21% |
| `Mountains` | the mountain | 5 | inherited | 1 | outdoors mountains | 30, stone 34% |
| `MountainSurface` | the surface (inherited) | 6 | inherited | 1 | outdoors mountains | Mountains list |
| `ClimbableSurface` | the surface | 4 | inherited | 1 | outdoors rocks | none in this class |
| `CaveRoom` | the cave | 2 | NORMAL, dark | 1 | indoors cave | 35, stone 29% |
| `CaveSurface` | the surface (inherited) | 4 | NORMAL, dark | 1 | indoors cave | cave list |
| `LargeCaveRoom` | the cave | 4, room size 8 | as cave | 1 | as cave | cave list |
| `CaveSeaPort` | the cave sea port | 2 | WET | 1 | indoors cave seaport | shore list (fish 59%) |
| `IcePlains` | the snow | 2 | COLD, WET | 1 | outdoors plains | own list ignored; uses plains list |
| `FrozenPlains` | the grass (inherited) | 2 | COLD | 1 | outdoors plains | own list (fur) ignored; uses plains list |
| `FrozenMountains` | the mountain (inherited) | 5 | COLD | 1 | outdoors mountains | mountains list |
| `Road` | a road | 1 | inherited | 1 | outdoors plains | 5, stone 36% |
| `LongRoad` / `LongerRoad` / `LongestRoad` | a road | 5 / 10 / 20 | inherited | 1 | outdoors plains | road list |
| `CityStreet` | the street | 1 | inherited | 1 | outdoors city | none |
| `MetalRoom` | the room | 1 | NORMAL | 1 | indoors metal | none |
| `MagicFreeRoom` | (inherited default) | 1 | NORMAL | 1 | indoors stone | none; Anti-Magic Field |
| `IceRoom` | (default) | 1 | COLD | 1 | indoors stone | none |
| `HotRoom` | (default) | 1 | HOT | 1 | indoors stone | none |
| `DarkStoneRoom` | (stone, dark) | 1 (stone base) | inherited | 1 | indoors stone | none |
| `LargeStoneRoom` | (stone) | 3, room size 5 | inherited | 1 | indoors stone | none |
| `LargeWoodRoom` | (wood) | 3, room size 5 | inherited | 1 | indoors wood | none |
| `MagicShelter` | the shelter; "Magic Shelter" | 0 | NORMAL | 1 | indoors magic | none; five properties |
| `HideoutShelter` | the hideout; "Secret Hideout" | 0 | NORMAL, dark | 1 | indoors wood | none; five properties |
| `InTheAir` | the sky | 1 | none | 1 | outdoors air | none |
| `IndoorInTheAir` | the space | 1, room size 5 | none | 1 | indoors air | none |
| `IndoorUnderWater` | the water | 3, room size 5 | swimming | 0 | indoors underwater | fresh water, unlimited |
| `IndoorShallowWater` | the water | 2 (shallow base) | WET | 0 | indoors water surface | underwater list |
| `IndoorWaterSurface` | the water | 2 | WET | 1 | indoors water surface | underwater list |
| `IndoorWaterThinSurface` | the water | 2 | WET | 1 | indoors water surface | underwater list |
| `IndoorWaterSurfaceColumn` | the water | inherited | WET | 1 | indoors water surface | underwater list |

**Grid catalogue.** The grid's own movement is set in its constructor. The child class is what a player actually walks on. The thin variant of each grid uses the same child.

| Grid class | Kind | Child class | Grid move | Child move | Notes |
|---|---|---|---|---|---|
| `DesertGrid` / `DesertThinGrid` / `DesertMaze` | fat / thin / maze | `Desert` | 2 | 2 | desert resources |
| `JungleGrid` / `JungleThinGrid` | fat / thin | `Jungle` | 3 | 3 | jungle resources |
| `HillsGrid` / `HillsThinGrid` | fat / thin | `Hills` | 3 | 3 | hills resources |
| `MountainsGrid` | fat | `Mountains` | 5 | 5 | mountain resources |
| `MountainSurfaceGrid` / `MountainSurfaceThinGrid` | fat / thin | `MountainSurface` | 5 | 6 | grid and child costs differ |
| `CaveGrid` / `CaveMaze` | fat / maze | `CaveRoom` | 2 (default) | 2 | dark, normal climate |
| `GreatLake` / `GreatThinLake` | fat / thin | `WaterSurface` | 2 | 2 | underwater list |
| `EndlessSky` / `EndlessThinSky` | fat / thin | `InTheAir` | 1 | 1 | `SKYSIZE` cells per side |
| `EndlessOcean` / `EndlessThinOcean` | fat / thin | `SaltWaterSurface` / `SaltWaterThinSurface` | 2 | as water base | edges go to the middle |
| `IndoorUnderWaterGrid` / `IndoorUnderWaterThinGrid` / `IndoorUnderWaterColumnGrid` | fat / thin / column | `IndoorUnderWater` | 3 (base) | 3 | water column below surfaces |
| `MetalRoomMaze` | maze | `MetalRoom` | 1 | 1 | metal domain |
| `AutoGrid` | generated grid | class from the layout | n/a | from layout | section 12 |

**Messages players see.** Short strings from this bucket, quoted or paraphrased:

| Situation | Message | Where |
|---|---|---|
| Enter air without flight | "You can't fly." | `Locales/InTheAir.java:158` |
| Sit or sleep in air | "You can't sit here." / "You can't sleep here." | `InTheAir.java:143`, `:148` |
| Weather blocks entry | "The swirling blizzard inhibits ... progress." (also hail, rain, sleet, thunder, wind) | `InTheAir.java:166-181` |
| Swim-only crossing | "You need to swim or ride a boat that way." | `Libraries/MUDTracker.java:2516` |
| Too heavy to swim | "You are too encumbered to swim." | `MUDTracker.java:2524` |
| Fire or gas underwater | "That won't work underwater." | `Locales/UnderWater.java:181` |
| Salt water | "You don't want to be drinking saltwater." | `UnderWater.java:205` |
| Shelter exit | "You can't leave the shelter that way.  You'll have to revoke it." | `Locales/MagicShelter.java:99` |
| Peace room | "You feel too peaceful here." | `Abilities/Properties/Prop_PeaceMaker.java:91` |
| Climb needed | "You need to climb that way, if you know how." | `Abilities/Properties/Prop_Climbable.java:100` |
| AutoGrid bad area | "The area id '@x1' has not been defined in the data file for AutoGrid @x2." | `Locales/AutoGrid.java:200` |

**Descriptions.** `MagicShelter` ("You are in a domain of complete void and peace.", `Locales/MagicShelter.java:47`) and `HideoutShelter` ("You are in a small dark room.", `Locales/HideoutShelter.java:47`). Sky grids use "Up in the sky" (`Locales/EndlessSky.java:50`).

**AutoGrid layouts.** `resources/randareas/lair.xml:38-50` defines the layouts that a generator area can use: MAZE, GRIDCITY, BOXCITYSQUARE, BOXCITY, CROSS, TREE, BRANCH and SPOTTEDMESH, each as an `AREA` with `like="maze_dungeon"` and a `requires` list (area name, size, level range, aggro chance, theme). Themes are kobolds, goblins, orcs, troglodytes, gnolls, hobgoblins and trolls (`lair.xml:38`).

## Notable design ideas

- **A biome is one record.** Movement cost, climate, domain, thirst and a weighted resource list are the whole definition of a terrain. The class adds only a hazard or a property when it needs one (sections 1 and 8).
- **Clustered deposits.** The neighbour bonus (`StdRoom.java:711-728`) makes resources form patches without any map data. It is a cheap rule that gives geography.
- **Infinity with a 3-by-3 torus.** A default sky of nine cells, wrapped on every edge, feels endless to the player (`EndlessSky.java:202-211`; `coffeemud.ini:1069`). The thin version creates cells only when visited.
- **A sea that recentres.** Walking off a sea's edge lands you in its middle (section 7). The space stays small, and the far side is never shown.
- **One reversible effect for falling, rising and sinking.** `Falling` and `Sinking` are effects with a `REVERSED` flag and a misc text (sections 5 and 6). The same mechanism covers falls, upward motion and submersion.
- **Protection as composition.** A shelter is a bag of five property effects, not a special class (section 10). Changing the rules of a room means changing its property list.
- **A generated interior with a tagged entrance.** AutoGrid passes the direction and room of its entrance to the generator (`AutoGrid.java:268-296`), so the generated level fits the place it is attached to (section 12).
- **Roads as speed tiers.** Three subclasses of one room differ only in cost (`LongRoad`, `LongerRoad`, `LongestRoad`, section 11).

## Problems and cruft

Found by reading the code; none of these were run.

1. **AutoGrid corner links for NW, SE, SW, UP and DOWN.** After each search, `buildFinalLinks` calls `linkRoom(subMap[len-1][0], dirRoom, d, ...)` unconditionally (`AutoGrid.java:529`, `:542`, `:555`, `:568`, `:582`). For NW, SE and SW that is the north-east corner, not the corner that `StdGrid` uses (`StdGrid.java:771-780`: `[0][0]`, `[last][last]` and `[0][last]`). The call happens whether or not the search found a cell, so the north-east corner gets a door in that direction to the outside room. The found cell gets its own link later (`AutoGrid.java:588-592`). **[unverified at runtime]**
2. **Two failure paths in AutoGrid behave differently.** A `CMException` during generation rebuilds a plain grid and keeps the 32-second timer (`AutoGrid.java:407-412`). Any other exception calls `clearGrid(null)` and returns (`:414-418`), which leaves `subMap` null. The next `getBuiltGrid` then calls `buildGrid` at once (`StdGrid.java:347-352`), so a persistent failure reruns the whole generation on every access. **[unverified cost]**
3. **Wrong value in a log line.** `Log.errOut("Unable to load '"+xml+"' ...")` at `AutoGrid.java:142` prints `xml`, which is null or empty at that point, not the path from `getGeneratorXmlPath()`.
4. **Wrong tag in a log line.** `AutoGrid.java:416` logs under the tag `"StdGrid"`.
5. **Approximate grid sizing.** The loop at `AutoGrid.java:346-350` compares the product of the largest indices with the room count, and it grows both axes by one each time. A full layout can end up one row and one column larger than needed, with empty cells. **[unverified impact]**
6. **EndlessOcean's south-east rule links the wrong direction.** In the SE block, the east-edge loop calls `linkRoom(..., Directions.NORTHEAST, ...)` (`EndlessOcean.java:182`). The east edge therefore never gets its SE link to the centre. When an outer NE exit exists, the NE block is skipped and the east edge gets a NE link to the centre instead.
7. **Unused resource lists.** `IcePlains.resourceList` (`IcePlains.java:58-76`) is never returned: `resourceChoices()` returns `Plains.roomResources` (`:81`). `FrozenPlains.resourceList`, which holds only fur (`FrozenPlains.java:50-52`), is also unused: `resourceChoices()` returns `Plains.roomResources` (`:57`). `CaveSeaPort.roomResources` combines the cave and shore lists (`CaveSeaPort.java:64`), but `resourceChoices()` returns `Shore.roomResources` (`:69`). A search found no other references to these three lists. The likely cause is copy-paste.
8. **Gem counted twice.** `RESOURCE_GEM` appears at `CaveRoom.java:95` and `:102`, so a cave roll gives gems double weight.
9. **Dead field.** `protected boolean crossLinked=false;` is declared and never read (`EndlessSky.java:43`, `EndlessThinSky.java:43`).
10. **State changes during the veto phase.** `airAffects` is called from `okMessage` as well as `executeMsg` (`InTheAir.java:62-63`, `IndoorInTheAir.java:63-64`, `EndlessSky.java:77-78`, `EndlessThinSky.java:85-86`). It can start a `Falling` effect (`MUDTracker.java:2460-2462`) for a message that is then refused by another listener. Entry is also decided by a random roll in the veto phase (`InTheAir.java:161`), so a message offered twice can get two outcomes. Both break the rule in b171 section 3 that the veto should be free of side effects. **[unverified]**
11. **A silent catch.** `EndlessSky.buildGrid` catches every exception and clears the grid without logging (`EndlessSky.java:214-217`).
12. **Unchecked lookup.** `MagicFreeRoom` calls `addEffect(CMClass.getAbility("Prop_MagicFreedom"))` without a null check (`MagicFreeRoom.java:48`). `MagicShelter` does check (`MagicShelter.java:50-53`).
13. **Raw doors versus doors.** `EndlessSky.buildFinalLinks` reads `rawDoors()[d]` (`EndlessSky.java:114`). `StdGrid` and `AutoGrid` read `doors[d]` (`StdGrid.java:751`, `AutoGrid.java:447`). b171 describes `doors[]` as the destination that callers should use. **[unverified impact]**
14. **Fat and thin skies disagree on one cell.** The fat sky links UP from `(0,0)` to `(1,0)` (`EndlessSky.java:198-201`). The thin sky links UP from `(0,0)` to `(last,last)` (`EndlessThinSky.java:127-146`). **[unverified which is intended]**
15. **Mountain grid costs differ from their children.** `MountainsGrid` and `MountainSurfaceGrid` set movement 5 (`MountainsGrid.java:47`, `MountainSurfaceGrid.java:47`), and their child `MountainSurface` sets 6 (`MountainSurface.java:46`). The child is what a player walks on, so the grid's cost is probably unused. **[unverified]**
16. **Duplicate diagonal exits in the thin ocean.** A corner cell gets two diagonal exits to the same centre cell (`EndlessThinOcean.java:140-160`, for example `(0,0)` gets NW and NE). **[by reading]**
17. **A comment that disagrees with the code.** `EndlessThinSky.java:110-112` says the adjacent rooms "do NOT" take edges into account. The code below does handle the edges, so the comment looks stale. **[unverified which is current]**
18. **The resource-bonus identity check.** `StdRoom.java:711` compares lists with `==`. A future subclass that returns a copy would lose the clustering bonus with no error.
19. **Short-circuit in a ledge.** `ClimbableLedge.okMessage` returns `true` at `:59` for entries from elsewhere, which skips the whole parent veto chain for that message, not only the climb check. This may be intended. **[unverified]**
20. **Multiplayer assumptions.** `playerNearby` (`AutoGrid.java:84-105`), the start-room exemption in `InTheAir.airAffects` (`InTheAir.java:78-79`) and the admin test in `distributeStuff` (b171 section 6) all assume other people in the world.

## Takeaways for a Python SUD

- **Terrain as data.** Keep one record per biome, in a data file rather than a class, with movement cost, climate tags, base thirst and a weighted resource table. A class is needed only for a behaviour, such as a hazard or a property. A sketch:

```python
from dataclasses import dataclass
import random

@dataclass(frozen=True)
class Terrain:
    name: str
    move_cost: int                       # time units per step (CoffeeMud setMovementCost)
    climate: frozenset = frozenset()     # e.g. {"wet", "hot"}
    base_thirst: int = 1
    resources: tuple = ()                # ((item, weight), ...)

JUNGLE = Terrain("the jungle", 3, frozenset({"wet", "hot"}), 1,
                 (("dirt", 50), ("fur", 20), ("berries", 15)))

def gather(t: Terrain, rng: random.Random, neighbour_items=()):
    """Weighted roll with CoffeeMud's clustering bonus (StdRoom.myResource)."""
    weights = list(t.resources)
    total = sum(w for _, w in weights)
    if neighbour_items:
        bonus = total // 4 // len(neighbour_items)
        weights += [(item, bonus) for item in neighbour_items]
    items, ws = zip(*weights)
    return rng.choices(items, weights=ws, k=1)[0]
```

- **Borrow the wrap.** A small torus gives an endless sky or sea at almost no cost: `x % n, y % n`. For a single player, create cells lazily in a dict keyed by `(x, y)`, as the thin grids do, and drop cells that have not been visited for a long time.
- **Borrow the recentring sea** if the world needs a sea with no shore on one side. Make it a rule about edge cells, not a size.
- **Use one reversible effect** for falling, rising and sinking. A record with a `reversed` flag and a time remaining is enough.
- **Keep the veto phase pure.** Put the falling and sinking effects in the execute phase only (section 10, problem 10). Let a veto return a reason string, and do the change afterwards.
- **Shelters as a list of rules.** A room holds a list of rule objects (no recall, no teleport, no summon, peace), and the rules are checked on each action. This needs no subclass per shelter.
- **Generate interiors, and tag the entrance.** A grid generated from a template when the player arrives is worth copying. Pass the entrance direction into the generator so the level fits the room it connects to (section 12).
- **Simplify.** Drop the nearby-player and admin distribution rules, the boat and encumbrance code only if the game has no water, and the thin/fat split. A single-player world can build each area when first entered.
- **Roads as speed tiers.** A travel mode can be a multiplier on movement cost, not a class per tier.

## Coverage

- **Read in full** (from the class declaration to the end; package, import and licence header skipped): all 59 files other than `Room.java`. This includes `GridLocale.java`, `LocationRoom.java`, `AutoGrid.java`, `EndlessSky.java`, `EndlessThinSky.java`, `EndlessOcean.java`, `EndlessThinOcean.java`, `InTheAir.java`, `IndoorUnderWater.java`, `CaveRoom.java`, and the remaining grid, maze and single-room classes of the bucket.
- **Skimmed:** `Locales/interfaces/Room.java` (domain codes at lines 76-175; the rest of the 1003 lines is the contract covered by b171).
- **Read for interaction only** (ranges): `Locales/StdGrid.java` (170-190, 262-318, 347-356, 587-625, 744-806, 973-1066); `Locales/StdRoom.java` (543-625, 692-742, 2860-2886); `Locales/WaterSurface.java` (110-175); `Locales/UnderWater.java` (86-135, 172-216); `Libraries/MUDTracker.java` (2439-2530); `Abilities/Properties/Prop_Climbable.java` (60-178); `Abilities/Properties/Prop_PeaceMaker.java` (56-100); `Abilities/Properties/Prop_MagicFreedom.java` (partial); `Locales/Plains.java`, `Road.java`, `Shore.java`, `ShallowWater.java`, `WaterSurface.java` (resource and movement lines); `core/Directions.java` (codes and `getOpDirectionCode`); `core/interfaces/Places.java` (climate masks); `Items/interfaces/RawMaterial.java` (table rows and the frequency accessor); `core/CMProps.java` (681, 3503-3506); `coffeemud.ini` (1059-1069, 2151); `guides/RandomAreas.html` (518-540); `resources/randareas/lair.xml` (38-50, 690-698).
- **Weights in section 2a** were computed from the `RawMaterial` table and the lists in this bucket, with a short script in the scratch directory, not in the repository.
- **Skipped:** the body of `Room.java` beyond the domain constants (left to b171), and the single-file helpers in other buckets that are named here but not read (for example `SaltWaterSurface`, `UnderSaltWater`, `Falling`, `Sinking`).
