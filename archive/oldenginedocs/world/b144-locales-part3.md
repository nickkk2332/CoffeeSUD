# Terrain locales: water, swamp, woods and their grids

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b144` (Locales [part 3/3: StdThinGrid .. WoodsThinGrid]) · 40 files · 6301 lines of Java.

Citations are `path:line`. Paths are relative to the CoffeeMud folder, and the common prefix `com/planet_ink/coffee_mud/` is written once here and dropped below (so `Locales/Swamp.java:97` means `com/planet_ink/coffee_mud/Locales/Swamp.java:97`). Items marked **[unverified]** were reasoned from the code but not run. The room, grid and exit base classes (`StdRoom`, `StdGrid`, `StdMaze`, `StdThinGrid`) are covered in depth in `b171-rooms-grids-and-exits-models-and-base-classes.md`; this chapter cites them only where a terrain depends on them.

## Summary

Each terrain in this bucket is a room class, and the class chosen for a room decides what that place is: how much movement it costs, its climate, what can be breathed or drunk, which resources can be gathered, which hazards can strike and which messages it prints. The 40 files are less varied than they look. They are about ten terrains (water surface, underwater, swamp, woods, stone, wooden room, cave, void, deck, tree) each offered in up to four layouts (single room, fat grid, thin grid, maze), and the layout classes differ mostly by one line, the child class they create. The water terrains are the richest part. A lake surface builds the sea below it on demand, objects and creatures sink through the water on a timer, and a whirlpool is a spiral of one-way currents that drains into the sea. For a Python game the terrain table and the layout generators are worth reusing; the 40 class files are not.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Locales/ThinRoom.java` | Stand-in for a room that is not loaded; it loads the real room the first time a neighbour walks into it | 1237 |
| `Locales/StdThinGrid.java` | Base of the five thin-grid terrains in this bucket (depth in b171) | 1139 |
| `Locales/UnderWater.java` | Base of every underwater room: breathing, drinking, damage underwater, and the sinking rule for objects | 326 |
| `Locales/WaterSurface.java` | Lake or sea surface: creates the sea below it and stitches neighbouring seas together | 317 |
| `Locales/UnderWaterGrid.java` | Fat underwater grid, square with side `SKYSIZE`, with wrap-around edges | 248 |
| `Locales/UnderWaterColumnGrid.java` | Underwater shaft: one chain of cells from top to bottom | 241 |
| `Locales/UnderWaterThinGrid.java` | Lazy underwater grid, built a cell at a time | 212 |
| `Locales/Whirlpool.java` | Surface grid laid out as a spiral of currents that drains to the sea below | 245 |
| `Locales/Swamp.java` | Swamp room: disease rolls on every relevant action, drinkable water | 221 |
| `Locales/Woods.java` | Woods room: poison ivy roll, 33 gatherable resources | 117 |
| `Locales/Void.java` | Flight-only room with no atmosphere | 68 |
| `Locales/WaterSurfaceColumn.java`, `Locales/WaterThinSurface.java` | Surface variants that build column or thin seas | 53, 71 |
| `Locales/WoodenDeck.java`, `Locales/WoodSeaPort.java`, `Locales/TreeSurface.java` | Ship deck, dock room, climbable trunk | 57, 71, 56 |
| `Behaviors/WaterCurrents.java` | (outside this bucket) Moves creatures and items along a current; used by `Whirlpool` | 921 |
| `Abilities/Misc/Sinking.java` | (outside) Timed effect that walks an actor or item down (or up) a water column | 396 |
| `Libraries/MUDTracker.java` | (outside) Water-surface entry rules (`isOkWaterSurfaceAffect`) and `makeSink` | 2471-2586 |
| `Locales/InTheAir.java` | (outside) Flight and weather rules that `Void` uses | 134-186 |

## How it works

### 1. A terrain is a room class with a few numbers

Every terrain class sets the same small set of fields in its constructor. The numbers that matter:

- **Movement cost.** `setMovementCost` sets the weight that the room charges per step (`Locales/StdRoom.java:165`). Water costs 2 or 3 (`UnderWater.java:49`, `WaterSurface.java:50`), a swamp or wood 3 (`Swamp.java:49`, `Woods.java:47`), stone and plain indoor rooms 1 (`StoneRoom.java:47`, `WoodRoom.java:46`), a tree 4 (`TreeSurface.java:47`), and a whirlpool 30 (`Whirlpool.java:46`). A class that does not call it inherits the base value of 2 (`StdRoom.java:91`).
- **Room size.** `setRoomSize` sets the room's height (`StdRoom.java:176-178`). The default is 1 indoors and 10 outdoors (`StdRoom.java:92`). Caves and the underwater city street set 5 (`WetCaveRoom.java:46`, `UnderwaterCityStreet.java:48`). The Locales interface says the height is the ranged-weapon limit (see b171).
- **Climate.** `climask` is set to `CLIMASK_WET` (1) for water, swamp, cave-wet and salt rooms (`core/interfaces/Places.java:95`), `CLIMASK_NORMAL` (0) for stone and wood rooms, and `CLIMASK_VOID` (32) for the void (`core/interfaces/Places.java:105`, `Void.java:48`). Classes that do not set it take the inherit sentinel.
- **Domain.** `domainType()` returns one of the `DOMAIN_*` codes: outdoors `WOODS` (1), `UNDERWATER` (4), `AIR` (5), `WATERSURFACE` (6), `SWAMP` (8) (`Locales/interfaces/Room.java:83-97`), and indoors `STONE`, `WOOD`, `CAVE`, `UNDERWATER`, `SEAPORT` as `INDOORS+0`, `+1`, `+2`, `+4`, `+9` (`Room.java:134-152`). The domain is not just a label. The sea code checks it (`WaterSurface.java:124-127`), the makeSink code checks the room below (`Libraries/MUDTracker.java:2560-2566`), and the sinking and current behaviours check whether a neighbour is watery (`Abilities/Misc/Sinking.java:95-96`).
- **Resources.** `resourceChoices()` returns the list of resources the room can produce. `StdRoom` returns `null` (`StdRoom.java:679-682`), and a `null` list gives the room no resource at all (`StdRoom.java:701-702`). So `StoneRoom`, `WoodRoom`, `WoodenDeck`, `Whirlpool` and the grid and maze classes that do not override it have none.

### 2. Water: breathing, drinking and damage

`UnderWater` (`UnderWater.java:34`) is the base of every underwater room, and `WaterSurface` (`WaterSurface.java:35`) is the base of every lake surface. Both implement `Drink`, so a builder can make water drinkable with a liquid type, a thirst-quench value and no depletion (`UnderWater.java:233-305`, `WaterSurface.java:240-309`).

**Rules that apply inside the water** (`UnderWater.isOkUnderWaterAffect`, `UnderWater.java:172-211`, called from `okMessage` at `:213-224`):

- Fire and gas are refused with "That won't work underwater." (`UnderWater.java:177-183`).
- Slashing and bashing weapons lose most of their force. The code computes `damage = value/3*2` and subtracts it, which leaves about one third of the damage (`UnderWater.java:185-197`).
- Drinking saltwater is refused with "You don't want to be drinking saltwater." (`UnderWater.java:203-207`). Drinking anything else returns `1` at once, which skips the rest of the room's veto chain (`UnderWater.java:208`, see Problems).
- Each underwater room sets thirst to zero per round (`baseThirst` returns `0`, `UnderWater.java:61-65`) and quenches 500 per drink (`UnderWater.java:233-237`). The surface quenches 1000 (`WaterSurface.java:240-243`). The swamp quenches 500 (`Swamp.java:125-128`).
- Water is effectively unlimited: `liquidHeld` and `liquidRemaining` return `Integer.MAX_VALUE - 1000` (`UnderWater.java:239-249`) and filling a container costs nothing (`UnderWater.java:302-305`).
- The water washes the actor. A message whose source is in the room calls `handleHygienicMessage` with a value of 100 and `HYGIENE_WATERCLEAN` (`UnderWater.java:104-105`).

**Crossing a surface** is governed by `MUDTracker.isOkWaterSurfaceAffect` (`Libraries/MUDTracker.java:2471-2551`), which `WaterSurface.okMessage` calls (`WaterSurface.java:218`):

- Entering, leaving or fleeing across the surface needs a creature that is water-worthy, flying, falling or riding a water-based boat. Otherwise: "You need to swim or ride a boat that way." (`MUDTracker.java:2476-2517`). A boat is recognised by the destination area being a `Boardable` (`:2490-2500`) or by a water-based rideable item in the inventory (`:2503-2510`).
- A swimmer who weighs more than half their carrying capacity is refused: "You are too encumbered to swim." (`MUDTracker.java:2520-2525`, the test is `mob.maxCarry()*0.50`).
- Sitting or sleeping on the surface is refused unless the creature can breathe water: "You cannot rest here." (`MUDTracker.java:2530-2537`).
- A drink on a water room returns `FORCEDOK` after the saltwater check (`MUDTracker.java:2540-2549`).

**Objects and creatures sink.** `UnderWater.sinkAffects` (`UnderWater.java:86-170`) is called from `executeMsg` in every underwater room (`UnderWater.java:226-231`, the grid versions `UnderWaterGrid.java:98-101`, `UnderWaterThinGrid.java:119-122`) and on the surface (`WaterSurface.java:231-236`). It scans the room's inhabitants and items on every message:

- A creature in a room that is not underwater sinks unless it is already swimming, is an amphibian or fish by racial category, is riding, is standing in its own start room, or already has a `Sinking` effect (`UnderWater.java:117-138`, start-room and riding tests at `:120-122`). The categories are tested by string (`:136-137`).
- Items sink unless they are carried (`item.container()==null`, `:145`). An item that already has a `Sinking` effect is marked reversed if its proficiency is 100 (`:150-154`).
- `makeSink` (`Libraries/MUDTracker.java:2555-2586`) does the work. The target sinks into the room below, or the room above if the sink is reversed (`:2560-2562`). The room below must be underwater (`:2563-2566`). A creature sinks only if it is not water-worthy or flying and weighs at least 1 (`:2568-2571`), and an item only if its ultimate container is not water-worthy or flying (`:2572-2575`). The `Sinking` effect is created with the text `REVERSED` or `NORMAL` (`:2581`).

**The `Sinking` effect** (`Abilities/Misc/Sinking.java`) does the moving. It is a `TICKID_MOB` tick, so it runs on the creature's tick, which is 4 seconds by default (see b171). Each time it fires:

- It stops if the creature is no longer on a water surface, is water-worthy, is flying, or weighs less than 1 (`Sinking.java:195-200`).
- A descending creature with the `Skill_Swim` skill (`Sinking.java:202`) makes a `proficiencyCheck(mob,25,…)` (`:203-205`). On success it treads water, costs energy (`:207-212`), and does not move.
- Otherwise it prints "YOU ARE SINKING DOWN!!" (or "UPWARDS") and walks one room (`:218-221`). It stops if the next room is not watery (`:223-226`).
- Items fall one room per tick, but a room with more than 100 items slows the fall. The delay is `roll(1, numItems/50)` ticks (`:256-260`).
- The effect refuses to carry the creature into an air room unless it flies: "You can't seem to get there from here." (`:131-138`).

### 3. Lakes build the sea below them

`WaterSurface.giveASky` (`WaterSurface.java:110-175`) is called when a surface room is first used (`fixUnderwater`, `:200-210`, runs on every non-expire message at `:216-217` and `:232-234`). It creates a sea under the room, provided four conditions hold: the room has no `DOWN` door, its domain is neither underwater nor air, and the `SKYSIZE` setting is non-zero (`:124-127`).

- The sea is a new instance of the class named by `UnderWaterLocaleID()`. That is `UnderWaterGrid` for `WaterSurface` (`WaterSurface.java:72-75`), `UnderWaterThinGrid` for `WaterThinSurface` (`WaterThinSurface.java:59-62`) and `UnderWaterColumnGrid` for `WaterSurfaceColumn` (`WaterSurfaceColumn.java:49-52`).
- A negative `SKYSIZE` hides the way down. The exit is then an `UnseenWalkway` and not a `StdOpenDoorway` (`WaterSurface.java:129-134`, the setting is described in `coffeemud.ini:1061-1069`).
- The surface's `DOWN` door points to the sea, and the sea's `UP` door points back (`WaterSurface.java:138-141`).
- **Neighbours are stitched.** For each direction the surface has an exit to, `giveASky` is called on that neighbour, and if the neighbour's `DOWN` room is also a sea grid, the two seas are joined with a matching exit in that direction (`WaterSurface.java:142-168`). A lake therefore gets one connected sea beneath it. Recursion stops at depth 1000 (`:114-115`).
- Sky for the room is handled by the base class (`WaterSurface.java:116`, `StdRoom.giveASky`, see b171).

`getSky` and `clearSky` (`WaterSurface.java:90-107`, `:177-198`) undo this, and `IsUnderWaterFatClass` (`:82-87`) names the three sea classes that a surface will accept as its sea. The mazes are not in that list, so an underwater maze can never be the sea under a lake.

### 4. Underwater grid shapes

Every underwater grid takes its side length from `SKYSIZE` (`UnderWaterGrid.java:51-52`, `UnderWaterThinGrid.java:51-52`), with a fallback of 3 if the setting is zero (`UnderWaterGrid.java:57-60`). The shipped default is 3 (`coffeemud.ini:1069`). The three sea layouts are different ways of joining the same cells.

**Fat grid, `UnderWaterGrid`** (`UnderWaterGrid.java:35`, a `StdGrid`). The cells are linked north, west and, when the 11-direction set is enabled, the two northern diagonals (`:211-222`). A cell's `UP` exit is linked to the cell to its north (`:212`), so `UP` and `NORTH` lead to the same room. The edges wrap around. Row ends join across (`:231-232`), column tops join to column bottoms (`:233-234`), and the top of each column joins to the bottom of the previous column by an `UP` link (`:235-236`). The sea therefore has no edge (a torus) **[as written]**. The outside rooms attach at the edges: `NORTH` to the top row (`:170-175`), `UP` to the top-left cell (`:170-171`), `DOWN` to the bottom-right cell (`:173-174`), and the diagonals to the matching corners (`:176-186`). An entry from the surface lands at the top-left cell (`findCenterRoom(UP)` returns `subMap[0][0]`, `:123-124`).

**Column, `UnderWaterColumnGrid`** (`UnderWaterColumnGrid.java:37`, extends `UnderWaterGrid`). Each cell's `UP` exit leads to the previous cell in one sequence, with the sequence wrapping from one column to the next (`:218-228`). The result is a single chain from top `[0][0]` to bottom `[last][last]`. Every cell is also linked to the outside room in all eight horizontal directions (`:51-132`), so a creature in any cell of the shaft can leave sideways from any neighbour. The guide describes it as "straight up and down" (`guides/ArchonGuide.html:923`).

**Thin grid, `UnderWaterThinGrid`** (`UnderWaterThinGrid.java:35`, extends `StdThinGrid`). The override `fillExitsOfGridRoom` (`:126-210`) creates neighbours as they are first needed. It uses `UP` and `DOWN` for north and south in the interior (`:143-147`, `:169-173`), wraps the edges (`:149-160`, `:175-209`), and on the top row connects `UP` to the surface (`:138-141`). The comment at `:132-134` says the neighbour logic "does NOT" handle the edge, which is the same gap b171 notes for `StdThinGrid` (b171 problem 8).

**Maze, `UnderWaterMaze`** (`UnderWaterMaze.java:35`, extends `StdMaze`). It is not sized from `SKYSIZE` (it keeps the `StdGrid` default of 5, `StdGrid.java:51-52`), and it is not a sea that a surface can build (see section 3). The guide warns "a huge multi-room maze of underwater locales -- be careful!" (`guides/ArchonGuide.html:933`).

**Salt variants.** The `UnderSalt*` classes change three things: the liquid is saltwater, the atmosphere is saltwater, and the resource list is the salt list. `UnderSaltWater` (`UnderSaltWater.java:33-46`) and the grid, thin, column and maze variants (`UnderSaltWaterGrid.java:43-76`, `UnderSaltWaterThinGrid.java:43-84`, `UnderSaltWaterColumnGrid.java:37-59`, `UnderSaltWaterMaze.java:43-74`) are otherwise copies of their fresh-water parents.

**Switch to fat classes.** Each thin class has a `newInstance` that returns the fat class when the `THINGRIDS` security flag is disabled (`UnderWaterThinGrid.java:79-84`, `WaterThinSurface.java:65-70`, `UnderSaltWaterThinGrid.java:68-74`, and the land grids). The flag is described as "thin uncached grids" (`core/CMSecurity.java:3008`). A single-player port does not need this switch.

### 5. Swamp and woods: hazards on the action

Both terrains roll for a disease when a creature acts in the room. The probability is written into the code as a dice test, `CMLib.dice().rollPercentage()` which returns 1 to 100 (`Libraries/Dice.java:409-412`), so `==1` is a 1 percent chance and `<10` is a 9 percent chance.

**Swamp** (`Swamp.java:61-105`):

- The first gate (`:63-67`) requires that the message targets the room, or is an advance or retreat, that the actor is not a monster, that a 1 percent roll succeeds, that the actor is in the room, and that the `AUTODISEASE` flag is not disabled.
- Inside the gate, if the actor is hurt, another 1 percent roll (`:69-70`) gives `Disease_Chlamydia` when a 1 to 100 roll is over 50 and `Disease_Malaria` otherwise (`:73-76`). Chlamydia is a sexually transmitted infection in this game's disease list; it is named only as an ability ID here.
- Inside the same gate, if the actor's movement is under half of maximum, `Disease_FootFungus` is applied with no further roll (`:80-85`).
- A disease is not applied twice, because each invocation checks `fetchEffect(A.ID())==null` (`:77`, `:83`).
- Drinking from the swamp has its own check (`:87-103`). A roll under 10 (9 percent) applies `Disease_Malaria` (`:97-102`). This check sits outside the `AUTODISEASE` gate (see Problems).
- The swamp's drink messages are "You are no longer thirsty." and "You have drunk all you can." (`Swamp.java:93`, `:96`).

**Woods** (`Woods.java:58-74`) has the same structure with one change. The condition has two `rollPercentage()==1` tests in a row (`:62-63`), so a qualifying message has a 1 in 10,000 chance of applying `Disease_PoisonIvy` (`:67-71`). The double test is probably a duplicate line (see Problems).

Each disease is a separate ability class in `Abilities/Diseases/` (for example `Disease_PoisonIvy.java`, `Disease_Malaria.java`, `Disease_Chlamydia.java`, `Disease_FootFungus.java`). Their effects are in other buckets **[unverified in this chapter]**.

### 6. Void and air

`Void` (`Void.java:35`) has movement cost 1 (`:46`), the void climate (`:48`), no atmosphere (`atmosphere=RESOURCE_NOTHING`, `:49`) and the air domain (`:54-57`). Its `okMessage` calls the base room first, then `InTheAir.isOkAirAffect` (`Void.java:60-66`).

`InTheAir.isOkAirAffect` (`Locales/InTheAir.java:134-186`) does the following:

- Sitting and sleeping are refused except on exits or dead bodies. "You can't sit here." and "You can't sleep here." (`InTheAir.java:138-150`).
- Entering the room requires flight or falling. Otherwise: "You can't fly." (`InTheAir.java:152-159`).
- Then, on a roll over 50 (`:161`), the area's weather can block the entry with a message for blizzard, hail, rain, sleet, thunderstorm or wind (`:163-182`). The weather is read from `room.getArea().getClimateObj().weatherType(room)`, so a void inside an area with bad weather can still refuse an entry **[as written]**.

Nothing in the code creates a `Void` by ID (a search for `getLocale("Void")` finds nothing). It is picked by a builder or by an import table.

### 7. Plain rooms and special surfaces

- **WoodenDeck** (`WoodenDeck.java:35`): city domain, movement 1 (`:47`), and the `IS_DECK_ROOM` ambiance (`:48`). Ships and the sailing skills use it: `Abilities/Skills/Skill_CrowsNest.java:394` and `Items/Basic/StdNavigableBoardable.java:92` create one, and `Items/Basic/GenCastle.java:87` tests the ambiance flag.
- **WoodSeaPort** (`WoodSeaPort.java:35`, extends `WoodRoom`): an indoor wood room where ships dock. Movement 2 (`:47`), wet climate (`:49`), baseline thirst 1 (`:52-56`), and the resource list taken from `Shore` (`:64`, `Locales/Shore.java`).
- **TreeSurface** (`TreeSurface.java:35`, extends `ClimbableSurface`): a climbable trunk with movement 4 (`:47`) and domain woods (`:52-55`). The base class attaches a `Prop_Climbable` effect (`Locales/ClimbableSurface.java:44-54`).
- **StoneRoom** and **WoodRoom** (`StoneRoom.java:35`, `WoodRoom.java:35`): plain indoor rooms with movement 1, a normal climate and no resources.
- **WetCaveRoom** (`WetCaveRoom.java:35`, extends `CaveRoom`): room size 5 (`:46`), wet (`:48`), and the cave resource list (`:51-55`).
- **UnderwaterCityStreet** (`UnderwaterCityStreet.java:35`, extends `UnderWater`): movement 3 (`:47`), room size 5 (`:48`). **UndergroundCityStreet** (`UndergroundCityStreet.java:35`, extends `LargeStoneRoom`): movement 2 (`:47`).

### 8. Lazy stand-ins: `ThinRoom`

`ThinRoom` (`ThinRoom.java:38`) implements `Room` directly. It is a placeholder that holds only a room ID and an area, and it lets a large world be kept in the database until a player walks near it.

- **Shared arrays.** Every instance starts with the static arrays `sexits` and `srooms` (`ThinRoom.java:116-117`, with the instance fields `exits` and `rooms` pointing at them at `:113-114`). A write copies them first (`setRawExit` at `:215-225`, `setRawDoor` at `:388-392`). This is copy-on-write, so one placeholder costs almost no memory.
- **Empty behaviour.** The room has no name beyond "A Thin Room" (`:47-49`), no description, no items, no inhabitants, no effects and no scripts (all empty enumerations or no-op methods). It is not savable (`:95-98`), it has no tick (`:1221-1224`, status `STATUS_NOT` at `:108`), and `getRoomInDir` returns `null` (`:455-457`).
- **Loading.** The only real method is `prepareRoomInDir` (`:235-288`). A `recurse` flag stops re-entry (`:239-243`, `:286`). It takes the room from the map (`CMLib.map().getRoom`, `:247`), or else reads it from the database with `DBReadRoom`, `DBReadRoomExits` and `DBReadContent` (`:248-254`). It fills the area (`:255`) and sets an expiry for rooms in thin areas (`:256-257`). It then replaces the caller's door pointer with the real room (`:261-266`), sends a `MSG_NEWROOM` from "the wind" to the caller's room (`:267-274`), and calls `prepareRoomInDir` on the loaded room unless that room is itself a `ThinRoom` (`:277-281`).
- **Where placeholders come from.** `RoomLoader` creates one for a neighbour that is unloaded or lies in a thin area (`core/database/RoomLoader.java:812-822`). `CoffeeMaker` does the same for link targets (`Libraries/CoffeeMaker.java:1219`, `:1244`). When a room is destroyed, `StdRoom.prepareRoomInDir` gives its neighbours a placeholder with the same ID (`Locales/StdRoom.java:1641-1653`).

### 9. Interactions with other systems

- **Currents** (`Behaviors/WaterCurrents.java`). `applyCurrents` (`:118-236`) picks out the creatures and items in a room that are not monsters, not riding, not in flight, not in combat and have no mobility behaviour (`:127-135`, the mobility test is `CMLib.flags().isMobile`, which checks for a movement behaviour in `Libraries/Sense.java:1811-1822`). The current moves them to the first open exit in its direction whose destination has the same domain or is watery (`:171-185`). Its messages are "...swept in from @x1 by the current." (`:53-56`). The default timing is 3 to 5 room-behaviour ticks with a 75 percent chance (`:65-70`). `ActiveTicker` supplies the countdown and the chance (`Behaviors/ActiveTicker.java:128-165`).
- **Whirlpool spells and skills.** `Abilities/Druid/Chant_Whirlpool.java:285-322` builds a `Whirlpool` grid over the target room (see section 10). `Abilities/Druid/Chant_PlantMaze.java:183-185` makes a `WoodsMaze` whose side is `10 + level terms`. `Abilities/Druid/Chant_IllusionaryForest.java:141` creates `Woods`, `Abilities/Druid/Chant_Treehouse.java:158` and `Abilities/Druid/Druid_Krakenform.java:163` create `WoodRoom`, and `Abilities/Thief/Thief_SmugglersHold.java:163` and `Abilities/Skills/Skill_Stowaway.java:325` create `WoodRoom` boxes.
- **Building.** `Abilities/Common/BuildingSkill.java:484` builds a `WaterSurface` and `:634` builds an `UnderWater` room.
- **Import.** `Commands/Import.java:6277-6440` maps legacy sector numbers to these classes (see Content).
- **Settings.** `SKYSIZE` (`coffeemud.ini:1061-1069`, read at `core/CMProps.java:3092`), `THINGRIDS` and `FATGRIDS` (`core/CMSecurity.java:3008-3009`).
- **Movement.** The room's `okMessage` runs the surface and sea rules (`WaterSurface.java:214-226`), so a player walking into water passes through `MUDTracker` before the move is made (the walking pipeline is in b171, section 3).

### 10. Whirlpool: a spiral that drains

`Chant_Whirlpool` (`Abilities/Druid/Chant_Whirlpool.java:35`) is a druid chant:

- It refuses if the target already has the whirlpool effect: "There is already a whirlpool here! Geesh, how did you miss it?!" (`:285-289`).
- On success it prints "A MASSIVE WHIRLPOOL FORMS HERE!" (`:302`) and creates a `Whirlpool` locale (`:306`). It copies the target's exits in every direction except `DOWN` (`:314-321`), so the whirlpool sits between the same neighbours as the room it replaces. The grid's area is named "A Whirlpool" and is not savable (`:309-312`). The player is told "You are caught in a massive whirlpool" and "The only way out appear to be to fight against the currents." (`:307-308`).
- When the effect ends, the grid is cleared and its area destroyed (`:98-102`).

`Whirlpool` (`Locales/Whirlpool.java:35`, extends `StdGrid`) has these parts:

- **Entry.** `buildFinalLinks` joins every outside neighbour to one cell, the top-right corner `subMap[xsize-1][0]` (`:181`, `:208`). Any way in lands at the outer end of the spiral.
- **Spiral.** `buildWhirl` (`:130-174`) starts at `(xsize-1, 0)` and runs west, south, east and north. Each step calls `linkRoomOpen` (`:63-125`), which links to the next cell only if that cell has no exits yet (`:106-109`). The run length `num` starts at `xsize-1` (`:134`) and drops by one each time a run runs into an existing link (`:142-145`). Each link adds a `WaterCurrents` behaviour to the cell it leaves, with the parameters `minticks=1 maxticks=2 chance=75` and the direction (`:111-118`). The cell's description gains "The swirling waters are pulling you @x1." (`:117-118`).
- **Hand trace.** For the default 5 by 5 grid (`StdGrid.java:51-52`), the spiral visits all 25 cells and ends at the centre `(2,2)` **[derived by hand from the code; not run]**. A 3 by 3 grid behaves the same way.
- **Drain.** From the last cell of the spiral, the code calls `giveASky(0)` and then walks `DOWN` while a room exists (`:154-171`). Each cell on that path gets a `WaterCurrents` behaviour with `BOATS` set, so boats are carried down too (`:161-168`).

The spiral's exits are two-way, because `linkRoom` writes both sides. Only the current is one-way, so a player can always climb back out and has to fight the current to do so.

## Content worth mining

### A. Terrain catalogue (all 40 files)

Columns: class, base class, domain, movement cost, climate, what it creates or does, resources. Lines are the class's own file. "inh." means the value is inherited from the base class.

| Class (file) | Base | Domain | Move | Climate | Children / notes | Resources | Lines |
|---|---|---|---|---|---|---|---|
| `ThinRoom` | `Room` | outdoors city (`ThinRoom.java:169`) | 0 (`:467`) | inh. | placeholder, see §8 | none | 1237 |
| `StdThinGrid` | `StdRoom` | (base) | inh. | inh. | base of thin grids, default child `StdRoom` (`StdThinGrid.java:98-101`) | none | 1139 |
| `UnderWater` | `StdRoom` | underwater (`UnderWater.java:58`) | 3 (`:49`) | wet (`:51`) | fresh water, drinkable | 9 (`:307-318`) | 326 |
| `WaterSurface` | `StdRoom` | water surface (`WaterSurface.java:58`) | 2 (`:50`) | wet (`:52`) | builds a grid sea below (§3) | 9 (inh., `:315`) | 317 |
| `UnderWaterGrid` | `StdGrid` | underwater (`UnderWaterGrid.java:69`) | 3 (`:47`) | wet (`:62`) | child `UnderWater` (`:75`), side `SKYSIZE` | inh. | 248 |
| `UnderWaterColumnGrid` | `UnderWaterGrid` | inh. | inh. | inh. | vertical chain (§4) | inh. | 241 |
| `UnderWaterThinGrid` | `StdThinGrid` | underwater (`:67`) | 3 (`:47`) | wet (`:62`) | child `UnderWater` (`:89`) | inh. | 212 |
| `UnderWaterMaze` | `StdMaze` | underwater (`:54-57`) | 3 (`:47`) | wet (`:49`) | child `UnderWater` (`:68`) | inh. | 103 |
| `Whirlpool` | `StdGrid` | water surface (`Whirlpool.java:54`) | 30 (`:46`) | normal (`:48`) | child `WaterSurface` (`:60`), spiral (§10) | none | 245 |
| `WaterSurfaceColumn` | `WaterSurface` | inh. | 2 | inh. | sea `UnderWaterColumnGrid` (`:51`) | inh. | 53 |
| `WaterThinSurface` | `WaterSurface` | water surface (`:55`) | 2 (`:47`) | wet (`:49`) | sea `UnderWaterThinGrid` (`:61`) | inh. | 71 |
| `UnderSaltWater` | `UnderWater` | underwater | inh. (3) | wet (`UnderSaltWater.java:44`) | saltwater liquid and atmosphere (`:45-46`) | salt list (`:49-61`) | 68 |
| `UnderSaltWaterGrid` | `UnderWaterGrid` | underwater | 3 (`:47`) | wet (`:51`) | child `UnderSaltWater` (`:70`) | salt list (`:74-77`) | 78 |
| `UnderSaltWaterColumnGrid` | `UnderWaterColumnGrid` | inh. | inh. | inh. | child `UnderSaltWater` (`:53`) | salt list (`:57-60`) | 61 |
| `UnderSaltWaterThinGrid` | `UnderWaterThinGrid` | underwater (`:56-59`) | 3 (`:47`) | wet (`:51`) | child `UnderSaltWater` (`:78`) | salt list (`:82-85`) | 86 |
| `UnderSaltWaterMaze` | `UnderWaterMaze` | underwater | 3 | wet (`:49`) | child `UnderSaltWater` (`:68`) | salt list (`:72-75`) | 76 |
| `UnderwaterCityStreet` | `UnderWater` | inh. | 3 (`:47`) | inh. | room size 5 (`:48`) | inh. (`:309-318`) | 51 |
| `Swamp` | `StdRoom` | swamp (`Swamp.java:57`) | 3 (`:49`) | wet (`:51`) | disease rolls (§5), drinkable | 13 (`:197-212`) | 221 |
| `SwampGrid` | `StdGrid` | swamp (`:55`) | 3 (`:47`) | wet (`:49`) | child `Swamp` (`:61`) | `Swamp` list (`:67`) | 69 |
| `SwampThinGrid` | `StdThinGrid` | swamp (`:55`) | 3 (`:47`) | wet (`:49`) | child `Swamp` (`:69`), fallback to `SwampGrid` (`:63`) | `Swamp` list (`:73-75`) | 77 |
| `Woods` | `StdRoom` | woods (`Woods.java:54`) | 3 (`:47`) | inh. | poison ivy roll (§5) | 33 (`:76-109`) | 117 |
| `WoodsGrid` | `StdGrid` | woods (`WoodsGrid.java:53`) | 3 (`:46`) | inh. | child `Woods` (`:59`) | `Woods` list (`:63-65`) | 67 |
| `WoodsMaze` | `StdMaze` | woods (`WoodsMaze.java:53`) | 3 (`:46`) | inh. | child `Woods` (`:59`) | `Woods` list (`:63-65`) | 67 |
| `WoodsThinGrid` | `StdThinGrid` | woods (`WoodsThinGrid.java:53`) | 3 (`:46`) | inh. | child `Woods` (`:59`), fallback to `WoodsGrid` (`:73`) | `Woods` list (`:63-65`) | 75 |
| `TreeSurface` | `ClimbableSurface` | woods (`TreeSurface.java:54`) | 4 (`:47`) | inh. | climbable trunk | none | 56 |
| `Void` | `StdRoom` | air (`Void.java:56`) | 1 (`:46`) | void (`:48`) | flight required (§6) | none | 68 |
| `WoodenDeck` | `StdRoom` | city (`WoodenDeck.java:55`) | 1 (`:47`) | inh. | deck ambiance (`:48`) | none | 57 |
| `WoodSeaPort` | `WoodRoom` | indoor seaport (`WoodSeaPort.java:61`) | 2 (`:47`) | wet (`:49`) | dock room, baseline thirst 1 (`:52-56`) | `Shore` list (`:64`) | 71 |
| `WoodRoom` | `StdRoom` | indoor wood (`WoodRoom.java:54`) | 1 (`:46`) | normal (`:48`) | plain | none | 56 |
| `WoodRoomGrid` | `StdGrid` | indoor wood (`:54`) | 1 (`:46`) | normal (`:48`) | child `WoodRoom` (`:60`) | none | 62 |
| `WoodRoomMaze` | `StdMaze` | indoor wood (`:54`) | 1 (`:46`) | normal (`:48`) | child `WoodRoom` (`:60`) | none | 62 |
| `WoodRoomThinGrid` | `StdThinGrid` | indoor wood (`:54`) | 1 (`:46`) | normal (`:48`) | child `WoodRoom` (`:68`), fallback to `WoodRoomGrid` (`:62`) | none | 70 |
| `StoneRoom` | `StdRoom` | indoor stone (`StoneRoom.java:54`) | 1 (`:47`) | normal (`:49`) | plain | none | 57 |
| `StoneGrid` | `StdGrid` | indoor stone (`StoneGrid.java:54`) | 1 (`:46`) | normal (`:48`) | child `StoneRoom` (`:60`) | none | 62 |
| `StoneMaze` | `StdMaze` | indoor stone (`StoneMaze.java:54`) | 1 (`:46`) | normal (`:48`) | child `StoneRoom` (`:60`) | none | 62 |
| `StoneThinGrid` | `StdThinGrid` | indoor stone (`:54`) | 1 (`:46`) | normal (`:48`) | child `StoneRoom` (`:68`), fallback to `StoneGrid` (`:62`) | none | 70 |
| `WetCaveRoom` | `CaveRoom` | inh. (cave) | inh. | wet (`:48`) | room size 5 (`:46`) | cave list (`CaveRoom.java:115-120`) | 56 |
| `WetCaveGrid` | `StdMaze` | indoor cave (`WetCaveGrid.java:55`) | inh. (2) | wet (`:49`) | child `WetCaveRoom` (`:61`), size 5 (`:47`) | cave list (`:67`) | 69 |
| `WetCaveMaze` | `StdMaze` | indoor cave (`:55`) | inh. (2) | wet (`:49`) | same as `WetCaveGrid` (identical code) | same | 69 |
| `UndergroundCityStreet` | `LargeStoneRoom` | inh. | 2 (`:47`) | inh. | street | inh. | 49 |

Names used in the game text: "the water" (`UnderWater.java:47`, `WaterSurface.java:48`), "Under the water" (`UnderWaterGrid.java:49`), "the swamp" (`Swamp.java:48`), "the woods" (`Woods.java:46`), "the void" (`Void.java:47`), "a wooden deck" (`WoodenDeck.java:46`), "the wooden sea port" (`WoodSeaPort.java:46`), "the tree" (`TreeSurface.java:46`), "the street" (`UnderwaterCityStreet.java:46`).

Short descriptions from the Archon guide list (quoted briefly, `guides/ArchonGuide.html`): Swamp "damp outdoor swampy region" (`:861`); Void "no air, no climate, floating in the void" (`:938`); Whirlpool "a water surface that pulls you down" (`:971`); UnderWater "as wet as it gets... unless you are a fish you won't live long" (`:913`); WoodenDeck "a resourceless outdoor room that inherets weather" (`:975`); TreeSurface "a wooden ClimbableSurface, such as the trunk of a tree" (`:880`); Woods "thick with trees and life, the bright forest" (`:1000`).

### B. Resource lists

The lists are IDs from `Items/interfaces/RawMaterial.java`, so the names below are the constant suffixes. They can be reused as a per-terrain gathering table.

- **Woods** (33, `Woods.java:76-109`): wood species (wood, pine, oak, maple, redwood, yew, hickory, teak, cedar, elm, cherrywood, beechwood, willow, sycamore, spruce), sap, flowers, fruit (fruit, apples, berries, peaches, cherries, oranges, lemons), nuts, herbs, dirt, honey, vine, fur, hide, feathers, leather.
- **Swamp** (13, `Swamp.java:197-212`): jade, scales, cocoa, coal, pipeweed, bamboo, flowers, reed, sugar, lamp oil, dirt, tar, clay.
- **Underwater, fresh** (9, `UnderWater.java:307-318`): seaweed, fish, catfish, salmon, carp, trout, sand, clay, limestone.
- **Underwater, salt** (9, `UnderSaltWater.java:49-60`): seaweed, fish, tuna, shrimp, sand, clay, pearl, limestone, salt.
- **Shore** (used by `WoodSeaPort`, `Locales/Shore.java`) and **cave** (`CaveRoom.java:115`) are in other buckets.

### C. Legacy sector mapping (import)

`Commands/Import.java:6277-6440` maps the sector numbers of an imported legacy area to these classes. There are three tables, chosen by the file format. Only the rows for this bucket's terrains are shown.

| Sector | Table 1 (`circleFormat`, `Import.java:6279-6329`) | Table 2 (`codeBits&8 == 0`, `:6334-6384`) | Table 3 (else, `:6388-6438`) |
|---|---|---|---|
| 0 | `StoneRoom` (`:6282`) | `CityStreet` (`:6337`) | `StoneRoom` (`:6391`) |
| 3 | `Woods` (`:6291`) | `Woods` (`:6346`) | `WoodRoom` (`:6400`) |
| 6 | `ShallowWater` (`:6300`) | `ShallowWater` (`:6355`) | `IndoorShallowWater` (`:6409`) |
| 7 | `WaterSurface` (`:6303`) | `WaterSurface` (`:6358`) | `IndoorWaterSurface` (`:6412`) |
| 8 | `UnderWater` (`:6306`) | `FrozenPlains` (`:6361`) | `IceRoom` (`:6415`) |
| 15 | `Swamp` (`:6327`) | `Swamp` (`:6382`) | `Swamp` (`:6436`) |

The tables have `// Lava Room?` comments on the cave entries (`:6324`, `:6379`, `:6433`), which shows the mapping was approximate.

### D. Short messages (verbatim, short)

| Message | Source |
|---|---|
| "That won't work underwater." | `UnderWater.java:181` |
| "You don't want to be drinking saltwater." | `UnderWater.java:205` |
| "You need to swim or ride a boat that way." | `Libraries/MUDTracker.java:2516` |
| "You are too encumbered to swim." | `Libraries/MUDTracker.java:2524` |
| "You cannot rest here." | `Libraries/MUDTracker.java:2536` |
| "YOU ARE SINKING DOWN!!" / "UPWARDS" | `Abilities/Misc/Sinking.java:218` |
| "<S-NAME> tread(s) water." | `Abilities/Misc/Sinking.java:207` |
| "You can't seem to get there from here." | `Abilities/Misc/Sinking.java:137` |
| "A MASSIVE WHIRLPOOL FORMS HERE!" | `Abilities/Druid/Chant_Whirlpool.java:302` |
| "The only way out appear to be to fight against the currents." | `Abilities/Druid/Chant_Whirlpool.java:308` |
| "The swirling waters are pulling you @x1." | `Locales/Whirlpool.java:117-118` |
| "<S-NAME> <S-IS-ARE> swept in from @x1 by the current." | `Behaviors/WaterCurrents.java:53` |
| "You can't fly." | `Locales/InTheAir.java:158` |
| "The hail storm inhibits <S-YOUPOSS> progress." | `Locales/InTheAir.java:169` |
| "You are no longer thirsty." / "You have drunk all you can." | `Locales/Swamp.java:93`, `:96` |

## Notable design ideas

- **Terrain as data plus a layout.** About 40 classes are ten terrains times four layouts. The Woods grid and the stone grid differ only in the child class (`WoodsGrid.java:59` returns `"Woods"`, `StoneGrid.java:60` returns `"StoneRoom"`). A builder can pick any terrain for any layout, and the layout code does not change.
- **The lake makes its own sea.** `WaterSurface.giveASky` (`WaterSurface.java:110-175`) creates the sea under a surface room the first time anyone looks, and stitches it to the seas under neighbouring surfaces. Nobody has to author the underwater half of a lake.
- **Sinking is a status effect, not physics.** `Sinking` is a timed effect that moves its owner one room per tick (`Sinking.java:164-227`). It removes itself when the conditions are no longer met (`:195-200`, `:223-226`). A swimmer can tread water on a skill check (`:202-215`). Only the rules change, not the engine.
- **A whirlpool is a one-way current on two-way exits.** The exits are bidirectional, so the map stays simple, and the currents are a behaviour that pushes creatures along the spiral (`Whirlpool.java:111-118`). The player's escape is the choice to fight the current.
- **Vetoes carry their reason.** Each rule returns `false` and prints the reason in the same branch (`UnderWater.java:177-183`, `MUDTracker.java:2512-2517`, `InTheAir.java:152-159`). The rule and its explanation sit together.
- **Placeholders that load on demand.** `ThinRoom` is a flyweight with copy-on-write arrays (`ThinRoom.java:111-114`, `:215-225`) that becomes the real room the first time someone walks in (`:235-288`).

## Problems and cruft

1. **Column entry inverts up and down** **[read from code; not run]**. `UnderWaterColumnGrid.getAltRoomFrom` returns the bottom cell for `UP` (`UnderWaterColumnGrid.java:179-180`) and the top cell for `DOWN` (`:181-182`). The rest of the design puts the top at `[0][0]`: the chain links the top cell's `UP` to the outside and the bottom cell's `DOWN` to the outside (`:218-228`, `:98-103`), and `findCenterRoom(UP)` returns `subMap[0][0]` (`UnderWaterGrid.java:123-124`). The inverted branch runs only when the surface room is itself a grid child (`UnderWaterColumnGrid.java:166-168`), where the origin's grid parent is used. A creature diving from such a surface would arrive at the bottom.
2. **The flat sea reuses `UP` and `DOWN` as north and south.** In `UnderWaterGrid`, the `UP` exit of each interior cell goes to the cell to its north (`UnderWaterGrid.java:211-212`), and the top row wraps `UP` to another column (`:235-236`). Only `[0][0]` has `UP` to the surface (`:170-171`). `UnderWaterThinGrid` does the same (`UnderWaterThinGrid.java:143-147`, `:169-173`), but on its top row `UP` is the surface (`:138-141`). A player sees "up" and "down" exits in the sea that do not change depth.
3. **The swamp drinking check ignores two gates.** The disease check after drinking (`Swamp.java:97-102`) does not test the `AUTODISEASE` flag or `isAbilityDisabled`, which the other disease branches test (`:63-67`, `:77`, `:83`). It also does not test `isMonster()`, so an NPC that drinks can catch malaria.
4. **Woods has the same test twice.** `Woods.java:62-63` repeats `rollPercentage()==1`, which makes the poison ivy chance 1 in 10,000 instead of 1 in 100 **[intent unverified; probably a copy error]**.
5. **WetCaveGrid is a maze and duplicates WetCaveMaze.** `WetCaveGrid.java:35` extends `StdMaze`, and the file is identical to `WetCaveMaze.java` apart from the class name and the copyright year (checked with `diff`). The name is misleading and one of the two files is redundant.
6. **The drink shortcut skips the room's other vetoes.** `UnderWater.isOkUnderWaterAffect` returns `1` for a drink, and `okMessage` then returns `true` without calling `super.okMessage` (`UnderWater.java:199-209`, `:216-222`). A script or item that refuses a drink in water never sees the message.
7. **Every message scans the room.** `sinkAffects` walks every inhabitant and item in the room on every message (`UnderWater.java:117-160`), and `fixUnderwater` runs on every surface message (`WaterSurface.java:216-217`, `:233-234`). On a busy lake this is linear work per message **[not measured]**.
8. **The Void's weather check contradicts its climate.** The guide says "no air, no climate" (`guides/ArchonGuide.html:938`), but `InTheAir.java:163` reads the weather of the room's area. A void inside a stormy area can refuse entry **[as written]**.
9. **The spiral assumes a square grid.** `buildWhirl` takes the run length from `xsize` only (`Whirlpool.java:134`). For a non-square grid the spiral may not cover every cell **[unverified]**.
10. **Copy-paste classes.** Each terrain has a fat, a thin, a maze and sometimes a column class that differ by a base class and an ID (for example `WoodsGrid`, `WoodsMaze`, `WoodsThinGrid`). The thin-grid `newInstance` fallbacks repeat the same four lines (`UnderWaterThinGrid.java:79-84`, `WaterThinSurface.java:65-70`, `SwampThinGrid.java:58-63`, and others).
11. **The thin-grid edge gap reappears in the sea.** `UnderWaterThinGrid.java:132-134` carries the "it does NOT" comment from `StdThinGrid` (b171 problem 8), so the thin sea has the same edge gap.

## Takeaways for a Python SUD

- **One terrain table.** Replace the class-per-terrain pattern with a table of data. Values from CoffeeMud, for example:

```python
@dataclass(frozen=True)
class Terrain:
    name: str                  # "the swamp"
    domain: str                # "swamp", "woods", "water_surface", "underwater", "void"
    move_cost: int             # CoffeeMud value: swamp 3, woods 3, stone 1, lake 2, underwater 3, whirlpool 30
    wet: bool = False
    breathes: str = "air"      # "air", "fresh_water", "salt_water", "none"
    quench: int = 0           # one drink, CoffeeMud: sea 500, lake 1000
    resources: tuple[str, ...] = ()
    hazards: tuple[tuple[float, str], ...] = ()   # (chance per turn, effect id)
```

  Put the 13 swamp and 33 woods resources in this table, and make each hazard chance an explicit number. Do not hide a double roll (see problem 4).
- **Generate layouts, do not author them.** Write four generators: a single room, a lazy grid (`dict[(x, y)] -> Room`, created on first access), a maze (iterative depth-first carve from the centre, with an explicit stack as b171 suggests), and a column (a chain of cells). Terrain then supplies only its data. The Whirlpool spiral (`Whirlpool.java:130-152`) is a short function that returns a list of cells in order.
- **Build the sea under a lake on demand.** A lake cell creates its underwater neighbour when first entered and links it to the neighbours' seas (§3). In a single-player game this is a lazy dictionary entry, not a database load.
- **Sinking as a status.** `sinking = True` on the player. Each turn, if the player is not swimming and not holding a boat, move one level down; a swim check lets them tread water instead, and it costs stamina (`Sinking.java:202-215`). Remove the status when the player reaches a dry cell (`:195-200`).
- **Currents as a turn rule.** A whirlpool cell has a current that moves the player to the next cell in the spiral with a chance per turn (`WaterCurrents.java:171-185`). The exits stay two-way, so the escape is a choice to spend the turn fighting the current. Keep the drain: the final cell leads to the sea below (`Whirlpool.java:154-171`).
- **Veto with the reason.** Write each rule as `check(player, action) -> str | None`, where the string is the reason shown to the player, for example "You need to swim or ride a boat that way." (`MUDTracker.java:2516`). Keep rules for fire underwater, saltwater drinking and the encumbrance limit of half carrying capacity (`MUDTracker.java:2522`).
- **Drop.** `THINGRIDS`/`FATGRIDS` switches, database stand-ins (keep only the lazy-realise idea), `isMonster` and `isMobile` checks (in one player, the only actor is the player; NPC rules become a separate question), `handleHygienicMessage`, `CMSecurity` flags, the `Drink` interface (a terrain field does the same job), and the grid-wide message bus.
- **Rethink for turn-based play.** CoffeeMud's rates are per 4-second tick: sinking one room per tick, currents every 1 to 2 ticks, and a door reopening after 45 ticks (b171). A turn-based port should set each rate per turn in the table and not try to match real seconds.

## Coverage

- **Read in full (every non-blank line, compact view with braces removed):** `Locales/UnderWater.java`, `WaterSurface.java`, `WaterThinSurface.java`, `WaterSurfaceColumn.java`, `UnderSaltWater.java`, `UnderWaterGrid.java`, `UnderWaterColumnGrid.java`, `UnderWaterThinGrid.java`, `UnderWaterMaze.java`, `UnderSaltWaterMaze.java`, `UnderSaltWaterGrid.java`, `UnderSaltWaterColumnGrid.java`, `UnderSaltWaterThinGrid.java`, `Whirlpool.java`, `Swamp.java`, `Woods.java`, `Void.java`, `WoodenDeck.java`, `TreeSurface.java`, `WoodRoom.java`, `WoodSeaPort.java`, `WetCaveRoom.java`, `StoneRoom.java`, `UnderwaterCityStreet.java`, `UndergroundCityStreet.java`, `StoneGrid.java`, `StoneMaze.java`, `StoneThinGrid.java`, `WoodsGrid.java`, `WoodsMaze.java`, `WoodsThinGrid.java` (30 files).
- **Key lines read by grep (constructor, domain, climate, cost, child class, resource list, `newInstance`):** `SwampGrid.java`, `SwampThinGrid.java`, `WetCaveGrid.java`, `WetCaveMaze.java` (also diffed against `WetCaveGrid.java`), `WoodRoomGrid.java`, `WoodRoomMaze.java`, `WoodRoomThinGrid.java` (7 files).
- **Skimmed:** `ThinRoom.java` (lines 1-300, 455-470, 675-680, 1177-1240, and a method index for the Room-interface stubs in between); `StdThinGrid.java` (method index, lines 40-62, 98-103, 884-887, 1010-1047). The base-class mechanics are in b171.
- **Outside this bucket, read for the interactions:** `Behaviors/WaterCurrents.java` (39-275), `Behaviors/ActiveTicker.java` (49-165, partly), `Abilities/Misc/Sinking.java` (59-227, 340-396), `Libraries/MUDTracker.java` (2471-2600), `Locales/InTheAir.java` (134-186), `Locales/ClimbableSurface.java` (35-60), `Locales/StdRoom.java` (92, 165-178, 679-702, 1641-1676), `Locales/StdGrid.java` (51-52, 558-618, 697-705), `Abilities/Druid/Chant_Whirlpool.java` (280-330), `Abilities/Druid/Chant_PlantMaze.java` (176-190), `Commands/Import.java` (6270-6440), `Libraries/Dice.java` (409-412), `Libraries/Sense.java` (1803-1822), `coffeemud.ini` (1058-1072), `guides/ArchonGuide.html` (names and descriptions, by grep).
- **Checked by grep only:** the `Disease_*` ability classes (existence only), `Skill_Swim`, the call sites listed in §9, `Locales/CaveRoom.java`, `Locales/LargeStoneRoom.java`, `Locales/Shore.java` (resource lists only).
- **Skipped:** `Abilities/Diseases/*` effects, `Skill_Swim` internals, the `CMMsg` and `CMLib.flags()` internals, the walking pipeline in `MUDTracker.move` (covered in b171, section 3).
