# Random area generation (MUDPercolator) and layouts

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b020` (Random area generation (MUDPercolator) and layouts) · 16 files · 11143 lines of Java.

## Summary

MUDPercolator is CoffeeMud's data-driven generator. It reads XML templates (AREA, ROOM, MOB, ITEM, EXIT, STRING and others), chooses among alternatives using SELECT modes, CONDITION and VALIDATE tests and PICKWEIGHT weights, expands `$variables`, and builds real Area, Room, MOB, Item and Exit objects. For a whole area it first asks a layout manager (maze, tree, cross, branch, mesh, box city, grid city, apartment) for an abstract graph of nodes close to the requested room count, classifies every node (leaf, street, interior and so on), and then fills each node by running room templates whose conditions can test the node's shape. Gate exits tie a generated area to rooms that already exist, which is how random instances and grid locales join the normal world. For a single-player SUD the useful part is the split between a shape generator and a content filler, plus a small template language whose selection rules (any, all, weighted pick, limit, repeat) are easy to reuse.

## Key files and types

All paths are relative to the CoffeeMud folder. The Java package prefix is `com/planet_ink/coffee_mud/`.

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Libraries/MUDPercolator.java` | Template engine and object builder: ID/MERGE handling, CONDITION/SELECT/INSERT/LIKE, variable expansion, deferred post-processing, `buildArea`, `fillInArea`, `layOutRooms`, `processRoom`, `findRoom`, `findExit`, `buildRoom`, `buildMob`, `buildItem`, `relevelRoom` | 8243 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/AreaGenerationLibrary.java` | Public API; nested interfaces `LayoutManager` and `LayoutNode`; enums `LayoutTags`, `LayoutTypes`, `LayoutFlags`, `LayoutRuns` | 568 |
| `com/planet_ink/coffee_mud/Libraries/layouts/AbstractLayout.java` | Base class: size-error helper `diff`, `getDirection` between two nodes, `getRunDirection` | 80 |
| `com/planet_ink/coffee_mud/Libraries/layouts/LayoutSet.java` | Node set on a coordinate grid: `use`, `makeNextCoord`, `drawABox`, DFS `fillMaze`, `clipLongStreets`, `fillInFlags` | 342 |
| `com/planet_ink/coffee_mud/Libraries/layouts/DefaultLayoutNode.java` | Node: coordinates, links keyed by one direction, tag map, flag set, `setExits` | 278 |
| `com/planet_ink/coffee_mud/Libraries/layouts/MazeLayout.java` | MAZE: randomized depth-first maze over a width x height box | 116 |
| `com/planet_ink/coffee_mud/Libraries/layouts/TreeLayout.java` | TREE: stems growing outward from one root, branching as they go | 166 |
| `com/planet_ink/coffee_mud/Libraries/layouts/MeshLayout.java` | MESH: boxed edge with a full 4-neighbour grid inside | 126 |
| `com/planet_ink/coffee_mud/Libraries/layouts/SpottedMeshLayout.java` | SPOTTEDMESH: MESH with about one dead end per seven nodes | 105 |
| `com/planet_ink/coffee_mud/Libraries/layouts/CrossLayout.java` | CROSS: two crossing spines with leaf spurs | 110 |
| `com/planet_ink/coffee_mud/Libraries/layouts/BranchLayout.java` | BRANCH: two crossing spines with branch stubs and leaves | 219 |
| `com/planet_ink/coffee_mud/Libraries/layouts/BoxCityLayout.java` | BOXCITY: box bisected into blocks by streets, mazes inside the blocks | 199 |
| `com/planet_ink/coffee_mud/Libraries/layouts/BoxCitySquareLayout.java` | BOXCITYSQUARE: BOXCITY with a central plaza of eight nodes | 119 |
| `com/planet_ink/coffee_mud/Libraries/layouts/GridCityLayout.java` | GRIDCITY: streets on alternate rows and columns | 152 |
| `com/planet_ink/coffee_mud/Libraries/layouts/ApartmentLayout.java` | APARTMENT: parallel hallways with rooms on both sides | 171 |
| `com/planet_ink/coffee_mud/Libraries/layouts/LayoutTester.java` | Developer harness that prints layouts as ASCII; its `main` is the only user | 149 |
| `guides/RandomAreas.html` | Manual for the XML format (57,420 bytes) | n/a |
| `resources/randareas/random.xsd` | Schema draft (126 lines); no Java file references it | 126 |
| `resources/randareas/*.xml` | 20 XML files, about 615 KB: 13 theme files, `example.xml` (driver), and 6 shared `inc_*.xml` include files | n/a |

## How it works

### 1. Loading the template document

The caller parses an XML document and runs `buildDefinedIDSet` (`com/planet_ink/coffee_mud/Libraries/MUDPercolator.java:266-487`). Every tag with an `ID` is indexed under its upper-cased ID (`MUDPercolator.java:273-276`, `:396`). A duplicate ID is resolved by the MERGE attribute:

- Neither side has MERGE: the first tag wins and an error is logged (`MUDPercolator.java:314-317`).
- `MERGE="over<n>"` on one side lets the higher-numbered copy replace the other (`MUDPercolator.java:308-327`).
- `MERGE=true` on both sides merges parameters and child tags (`MUDPercolator.java:330-391`). REQUIRES is merged as a list; CONDITION and VALIDATE are joined with `and`; INSERT, DEFINE and PREDEFINE are joined with commas (`MUDPercolator.java:349-378`).

`LOAD="file"` splices a whole XML file into the tag once (`MUDPercolator.java:399-440`). `FROM="file"` imports only the tag with a matching ID (`MUDPercolator.java:441-484`). `example.xml` uses LOAD to pull in the theme files (`resources/randareas/example.xml:` the `<STRING load=...>` lines near the end of the file).

### 2. Choosing among alternatives

`getAllChoices` (`MUDPercolator.java:3881-3964`) turns a container tag into the list of child tags that apply:

1. Test the tag's CONDITION (`:3884`), unless the caller skips it.
2. Run PREDEFINE (`:3887`).
3. Resolve INSERT (`:3890-3921`): named tags, or an MQL `SELECT:` query. Inserted tags are parsed as if they were children.
4. Resolve LIKE (`:3925-3928`, `processLikeParm` at `:3844-3865`). LIKE copies the contents and parameters of an earlier tag of the same type; the local parameters win (`:3862`).
5. Collect child tags of the same name, recursively (`:3944-3949`).
6. Apply SELECT (`:3950`, `selectChoices`).
7. Run TAGDEFINE (`:3951-3962`).

`testCondition` (`MUDPercolator.java:3966-4069`) parses the condition text for `$name` and `$[formula]` references, generates missing values for the current prefix on demand (`:3986-3997`), and evaluates the expression with `CMStrings.parseStringExpression` (`:4046`). If the expression contains a random `?` range, the rolled value is written into the definitions so the same roll is reused in later tests (`:4024-4027`). A tag with `LLM` set is dropped when no LLM is installed (`:3968`).

`selectChoices` (`MUDPercolator.java:4205-4380`) implements the SELECT modes. With no SELECT, all choices are kept (`:4208-4209`) and the caller then picks one by roll where a single item is needed (`findArea` at `:801-803`, `findRoom` at `:1466-1468`, `findExit` at `:1577`).

| SELECT value | Behaviour in code | Reference |
|---|---|---|
| `all` | every choice | `:4215-4216` |
| `any`, `any-n` | one, or n distinct, uniformly | `:4331-4347` |
| `pick-n` | n draws without replacement, weighted by PICKWEIGHT; weight 0 is never drawn while others have weight; negative weight excludes; total weight 0 falls back to uniform | `:4267-4329` |
| `limit-n` | up to n random distinct choices | `:4241-4252` |
| `repeat-n` | n uniform draws with replacement | `:4349-4361` |
| `first`, `first-n`, `last`, `last-n`, `none` | positional or empty | `:4212-4213`, `:4228-4265` |
| math expression | used as n for the `any`-style draw | `:4363-4376` |

An empty choice list is an error unless the mode is `any-0`, `first-0`, `last-0`, `pick-0` or `limit-` (`:4218-4226`). `Dice.roll(number, die, modifier)` sums `number` rolls of `1..die` (`com/planet_ink/coffee_mud/Libraries/Dice.java:398-404`). The engine calls `roll(1,n,-1)` as a 0-based index, which gives `0..n-1` (for example `:803`).

### 3. Variables, strings and DEFINE scope

**Defining values.** DEFINE takes a comma-separated list of `id=value`, `id+=n`, `id-=n`, `id*=n` or `id/=n` (`defineReward`, `MUDPercolator.java:3265-3314`). Compound forms are rewritten as `oldValue op value` (`:3287-3292`). Values go through `strFilter`, and results that are math expressions collapse to integers (`:3301-3303`). The definition also propagates to a parent tag with the same tag name (`:3311-3313`).

`addDefinition` (`MUDPercolator.java:3187-3199`) stores `ROOM_TITLE` and also appends the value to a plural list, `ROOM_TITLES`, as a comma-separated string. The header comment at `:616` lists the variables that `buildRoom` creates.

**Scope.** Keys starting with `_` are copied from a room out to its layout group (`findRoom`, `:1501-1509`). Keys starting with `__` are copied to every group in the area (`updateLayoutDefinitions`, `:878-896`). `guides/RandomAreas.html` documents the same rule in the DEFINE entry.

**Variable syntax.** `strFilter` (`MUDPercolator.java:7808-8005`) handles the following:

- `$name` and `${:flags:name}`, where flags are `l u j 1 p c _` (lower, upper, JavaScript-safe, one line, plural, capitalized, one word). See `parseVariables` at `:1639-1742` and the modifier loop at `:7969-7982`.
- `$[expression]`, a math expression (`:1703-1719`, `:7820-7832`).
- `$SELECT:...`, an MQL query (`:7834-7835`).
- `$STAT:<path>:<stat>`, which navigates to ROOM, AREA, a compass direction, ANYROOM, MOB, ITEM or AREAGATE and reads a stat there (`:7837-7931`).
- `(a(n))`, which becomes `a` or `an` based on the next word (`:7989-8003`).
- An unknown variable raises an error (`:7967-7968`).

`findString` (`MUDPercolator.java:3573-3690`) resolves a name in this order: special names (`SYSTEM_RANDOM_NAME:min-max` at `:3581-3587`, `ROOM_AREAGATE` at `:3590-3610`), prefixed definitions, a parameter on the tag, a plain definition, `CONTENT_LOAD` (a file, at `:3630-3639`), and finally child STRING tags combined with ACTION `APPEND`, `REPLACE` or `PREPEND` (`:3649-3685`).

Every filtered string has its apostrophes replaced with backticks (`MUDPercolator.java:8004`).

**REQUIRES.** `checkRequirements` (`MUDPercolator.java:4174-4197`) and `getUnfilledRequirements` (`:4132-4172`) check that each named variable exists and matches one of: `int` (`=int`), `#` or `double`, `$` or `string`, or a semicolon-separated list of allowed values.

### 4. Deferred evaluation

A `PostProcessException` (`MUDPercolator.java:548-555`) means "not yet resolvable". `PostProcessAttempt` (`:557-597`) queues the attempt with a copy of the definitions (`addPostProcessAttempt`, `:566-582`). `postProcess` (`:733-757`) reruns the queue after the main build. An attempt that still fails aborts the generation with "Unsatisfied Post Process Exception" (`:755`). Area author and description (`:844-871`), and stat fields filled through `fillOutRequiredStatCodeSafe` (`:599-614`), use this path.

### 5. Building an area

- **`findArea`** (`MUDPercolator.java:791-824`) picks one AREA choice uniformly from the post-SELECT list. It tests VALIDATE, applies DEFINE on a copy of the definitions, and copies back keys starting with `_`.
- **`buildArea`** (`:826-876`) creates the class from CLASS via `CMClass.getAreaType` (`:831`), requires a unique NAME (`:836-840`, duplicate names are destroyed and refused), applies optional author and description, then calls `fillInArea`.
- **`fillInArea`** (`:1316-1367`) reads LAYOUT and looks up the manager (`:1318-1325`). SIZE may be a math expression but must be a positive integer (`:1326-1331`). It sets area stats, affects and behaviours (`:1333-1348`), registers the area so room IDs can be issued (`:1350`), calls `layOutRooms` (`:1354`), and on failure destroys the area and its rooms (`:1357-1365`).

### 6. Layout managers and the node model

**Registration.** `activate` (`MUDPercolator.java:527-539`) loads every class in the `layouts` package by reflection and keys it by `name()` in upper case. `getLayoutManager` (`:248-263`) creates a new instance on each call, so per-run state (`TreeLayout.originalDirection`, the `Random` fields) is local to one generation.

**Contract.** `LayoutManager.generate(num, dir)` returns a list of nodes. The first node is the entry room, and `dir` is the direction of entry (`com/planet_ink/coffee_mud/Libraries/interfaces/AreaGenerationLibrary.java:275-294`).

**Coordinates.** `LayoutSet.makeNextCoord` (`com/planet_ink/coffee_mud/Libraries/layouts/LayoutSet.java:110-133`) defines NORTH as y minus 1, SOUTH as y plus 1, EAST as x plus 1 and WEST as x minus 1, with diagonals. So a layout's box lies at y <= 0 and "y=0" is its south edge. `dir` is the heading of travel into the layout. A traveller heading NORTH arrives from the south, so the entry node sits on the y=0 edge (`MazeLayout.java:78-80`, `MeshLayout.java:50-52`).

**Links.** `DefaultLayoutNode.crossLink` (`com/planet_ink/coffee_mud/Libraries/layouts/DefaultLayoutNode.java:89-94`) stores the neighbour under the single cardinal direction returned by `AbstractLayout.getDirection` (`AbstractLayout.java:53-64`). Each node therefore has at most one link per cardinal direction. A diagonal neighbour is stored under N or S, which matters for BOXCITYSQUARE (see Problems).

**Tags and flags.** Each node carries four tags (`AreaGenerationLibrary.java:497-503`): NODETYPE (`surround`, `leaf`, `street`, `square`, `interior`), NODERUN (`ns`, `ew`, `ud`, `nesw`, `nwse`), NODEFLAGS (`corner`, `gate`, `intersection`, `tee`, `offleaf`) and NODEEXITS, a string such as `,n,s,` built by `DefaultLayoutNode.setExits` (`DefaultLayoutNode.java:197-211`). `processRoom` exports them to the template engine as `ROOMTAG_NODETYPE`, `ROOMTAG_NODERUN`, `ROOMTAG_NODEFLAGS` and `ROOMTAG_NODEEXITS` (`MUDPercolator.java:1372-1373`). It also exports, for each linked neighbour, `NODETYPE_<DIR>`, `ROOMLINK_<DIR>` and `ROOMTITLE_<DIR>` (`:1375-1388`).

**Flagging.** `LayoutSet.fillInFlags` (`LayoutSet.java:269-341`) sets exits on every node. A node with one exit becomes a leaf unless it is a gate (`:278-279`). A two-link node is a corner unless its links are opposite (`:299-304`). An interior node next to a leaf becomes offleaf (`:290-296`). Three- or four-link nodes whose neighbours are all street or surround become tee or intersection (`:306-338`).

**Sizing.** Box-shaped layouts take `diameter = round(sqrt(num))` and choose the width as `diameter` or `diameter+1`, whichever gives a box area closer to `num` (`AbstractLayout.diff` at `AbstractLayout.java:41-45`; for example `MazeLayout.java:71-72`). The size is therefore approximate.

**Helper methods.** `LayoutSet.drawABox` (`LayoutSet.java:149-207`) draws the perimeter as `surround` nodes. `LayoutSet.clipLongStreets` (`:237-267`) turns the far end of a straight street into a leaf. `LayoutSet.fillMaze` (`:209-235`) is an unbounded recursive DFS that only stops where it meets existing nodes.

### 7. The layouts

| Layout (name) | Class | Shape and algorithm | Size produced | Entry point |
|---|---|---|---|---|
| MAZE | `MazeLayout.java` | Randomized depth-first maze over the whole box; every cell is `street`; the entry cell is flagged `gate` (`:36-65`, `:108-110`) | width x height; SIZE 50 gives 7x7 = 49 | Middle of the edge that faces `dir` (`:78-103`) |
| TREE | `TreeLayout.java` | One root; each round every live stem tries forward and its two side turns, and the new stems are shuffled (`:138-161`). Side turns are limited so the tree keeps moving away from the entry side (`:53-74`) | Up to the budget; overshoot is possible (see Problems) | Root at (0,0), flagged `gate` (`:131-136`) |
| MESH | `MeshLayout.java` | Boxed edge (`:42`), then a full 4-neighbour grid (`:45-104`) | width x height | Middle of the edge (`:105-119`) |
| SPOTTEDMESH | `SpottedMeshLayout.java` | MESH, then about ceil(n/7) interior nodes with four links are cut down to one link, making dead ends; the neighbour kept is flagged `offleaf` (`:42-104`). A cut is skipped if it would isolate a neighbour (`:65-86`) | Same as MESH | Same as MESH |
| CROSS | `CrossLayout.java` | Vertical spine of d+1 and horizontal spine of d+1 with d = num/3/2 + 1 (`:51-85`). They share one node. Leaves are added as spurs: the first d spine nodes get east and west leaves, the rest get north and south leaves (`:86-103`) | About 2d+1 spine nodes plus spurs, capped by the budget | Spine ends (`:67-68`, `:84-85`) |
| BRANCH | `BranchLayout.java` | Two spines of length d = round(sqrt(n)), at least 3 and at most n/3 (`:83-85`). Branch stubs of two streets plus a leaf sit at random positions (`:158-186`). Leaves then fill the remaining budget (`:188-212`) | Within the budget | Spine ends, with a leaf beyond the entry except on the entry side (`:119-156`) |
| BOXCITY | `BoxCityLayout.java` | Box bisected by north-south and east-west streets while a half-width is at least 3 (`:37-87`). A DFS maze fills each block from a midpoint (`:89-98`, `:122-151`) | About the box area | Middle of the edge, searched outward from the middle (`:156-188`) |
| BOXCITYSQUARE | `BoxCitySquareLayout.java` | BOXCITY with a plaza: eight `square` nodes around the centre (`:36-68`). Free sides of plaza nodes get leaves (`:94-117`) | About the box area | As BOXCITY |
| GRIDCITY | `GridCityLayout.java` | Streets on every second column and row (`:46-93`); interior connectors between them, north ones alternating (`:95-118`) and south ones on every column (`:119-135`) | About the box area | Middle of the edge, at an even position (`:51-91`) |
| APARTMENT | `ApartmentLayout.java` | Hallway length is num/3, halved while above 7 with the number of hallways doubled (`:60-66`). Parallel spines join hallways (`:121-144`). Each hallway step adds one street and two leaves, one per side (`:145-165`) | Approximate; not capped | Start of a hallway, chosen by `dir` (`:73-111`) |

### 8. From a layout to rooms: `layOutRooms`

`layOutRooms` (`com/planet_ink/coffee_mud/Libraries/MUDPercolator.java:899-1312`) does the following in order.

1. **Gate planning** (`:912-1035`). The caller may supply gate rooms as `ROOMTAG_GATEEXITROOM<dir>` (see section 11). For each such direction except the one facing back, the layout chooses the node that lacks a link in that direction and is furthest that way (`:969-988`, using `Directions.isMoreDirections` at `com/planet_ink/coffee_mud/core/Directions.java:933-966`). For UP, DOWN and GATE it chooses the non-leaf node nearest the centre of the bounding box (`:989-1034`).
2. **Entry room.** `nodes[0]` is the magic room. It keeps the exit opposite to `direction` free for the parent room. A link on that side is an error (`:1255-1257`). The room is tagged with ambiance `#GATE<opposite>` (`:1209-1210`).
3. **Grouping** (`:1040-1164`):
   - Leaves form groups with adjacent leaves and offleaf interiors. Each new leaf group is inserted at a random position among the leaf groups (`:1043-1081`, random insert at `:1072-1076`).
   - Streets are grouped along their run: `ns` as north-south chains, `ew` as east-west, `nesw` and `nwse` as diagonal chains. Streets without a run take the longest chain (`:1082-1152`).
   - Anything left is grouped by recursive fill of its type (`:1154-1164`).
   The resulting order is leaves, then streets, then the rest. The manual gives the same order (`guides/RandomAreas.html`, AREA glossary).
4. **Group definitions.** The magic room's group is processed first (`:1168-1197`). Each group starts from its own copy of the definitions (`:1185-1191`), so DEFINE values inside one room do not leak to its siblings except through `_` and `__` keys.
5. **Rooms.** Each node is built by `processRoom` (`:1369-1418`), group by group (`:1225-1248`). After each group, `__` keys are propagated (`:1247`).
6. **Doors.** Doors are set from node links (`:1280-1287`). For a gate direction with no door yet, the room gets a door to the outer room and a copy of the outer exit, plus ambiance `#GATE<dir>` (`:1291-1301`).
7. **Magic-room links** (`:1251-1266`). Links from the entry room are set as doors.

Room IDs are issued in build order by `A.getNewRoomID` (`:1400`). The magic room must end up as `#<preNum>` (`:1194-1197`).

### 9. Rooms, exits and nested layouts

**`findRoom`** (`MUDPercolator.java:1458-1518`) takes one ROOM choice at random (`:1466-1468`), tests VALIDATE (`:1470`), applies DEFINE on a copy (`:1472-1474`), and then either:

- builds it with `buildRoom` (`:1496-1500`), or
- if the choice has LAYOUT, lays out a nested graph inside the same area (`:1478-1493`). The nested magic room's gate exit points back to the parent (`:1489-1491`).

**`buildRoom`** (`:618-728`) creates the room class from CLASS via `CMClass.getLocale` (`:622-626`) and fills in TITLE and DESCRIPTION and other stats (`:627-633`). It adds mobs (`bringToLife`, `:634-640`), items (`setExpirationDate(0)`, `:641-648`), affects, behaviours (`:649-662`) and exits (`:663-720`). `R.startItemRejuv()` runs last (`:726`).

**Exits.** An exit direction that the layout linked is required (`ROOMLINK_<DIR>`, set at `:1387`). `buildRoom` then calls `findExit` with `required=true` (`:673-689`). For other directions, `findExit` is optional (`:691-699`). `findExit` (`:1549-1622`) works as follows:

- It keeps the EXIT choices that pass VALIDATE and, for optional exits, have a matching DIRECTION parameter (`:1561-1572`).
- It picks one at random (`:1577`) and builds the exit (`:1581`).
- An optional exit may point at an existing room by ID (ROOMLINK parameter, `:1586-1593`) or spawn a new room (`:1604`).
- A spawned room that is not yet in an area gets a new ID and joins the area (`:709-716`).

### 10. Mobs and items

**`buildMob`** (`MUDPercolator.java:1886-2116`):

- A `catalog` class copies a prototype from the catalog (`:1892-1904`). Otherwise the class comes from `CMClass.getMOB` (`:1907-1910`). Generic mobs take their name from a stat or a copy source (`:1912-1929`).
- LEVEL less than or equal to 0 is clamped to 1 (`:1945-1948`). The stats are then recomputed from the level (`:1954-1955`).
- GENDER defaults to a 50/50 roll (`:1973`).
- RACE is looked up by name. If the name is unknown, one of the matching `findRaces` results is chosen at random (`:1980-1996`).
- FACTION entries add faction values (`:2010-2020`).
- POWERBONUS (`:2056-2081`) is computed as a stat difference: the engine fills out two stat blocks, at the MOB's level and at that level plus the bonus, and records the difference of speed, armour, damage, attack and hp, mana and move as a `Prop_Adjuster` affect (`:2061-2072`).
- Affects, behaviours, abilities and shop stock follow (`:2043-2105`).

**`buildItem`** (`MUDPercolator.java:2475-2865`):

- A class starting with `metacraft` produces an item from a crafting recipe (`:2482-2546`). The text after the colon is parsed by `getMetacraftFilter` (`:2355`, not read; the data uses forms such as `any-<Skill> < $level_range`). A craft skill is chosen at random (`:2534`). All its recipes are crafted (`craftAllOfThisRecipe`, `:2537`) and filtered (`:2543-2545`), and one result is kept at random (`:2547-2549`).
- For the unfiltered `anything` case, skills with `wright` in their ID are removed (ship, boat and wagon makers, `:2524-2530`), and the search has a 1-second wall-clock budget (`:2531-2532`).
- A `catalog` class copies a catalog item (`:2761-2775`). A generic class uses `CMClass.getItem` and gen-item stats (`:2776-2795`).

### 11. Calls into the generator

- **Admin GENERATE command** (`com/planet_ink/coffee_mud/Commands/Generate.java:119-129`, `:262-286`). For AREA it sets the gate exit to the opposite of the heading and the gate room to the admin's own location (`:268-269`), then calls `findArea` with the heading (`:270`).
- **Auto-generated instances** (`com/planet_ink/coffee_mud/Areas/StdAutoGenInstance.java:520`, `:641`, `:676-699`). The instance is generated on entry. For each neighbouring room that links back, the generator records a gate exit (`:676-690`), then calls `fillInArea` (`:699`).
- **AutoGrid locale** (`com/planet_ink/coffee_mud/Locales/AutoGrid.java:148`, `:231`, `:286-300`). It generates a random room set in a temporary `StdThinInstance` area (`:298-300`), with gate exits set in the same way.
- **Quests** load `randareas/example.xml` (for example `com/planet_ink/coffee_mud/Abilities/Prayers/Prayer_DivineQuest.java:190`). This is a documented use in the code, not read in full.

### 12. Levelling

`relevelRoom` (`MUDPercolator.java:8028-8157`) moves the levels of a room's weapons, armour, mobs, carried items and shop stock from one level range to another. `makeNewLevel` (`:8018-8025`) maps linearly: `newMin + round((level-oldMin)/(oldMax-oldMin) * (newMax-newMin))`. Mob levels are clamped to the new minimum (`:8071-8072`). Item levels are clamped to 1 (`:8045-8046`). A room with a land title is left alone (`:8035-8036`).

## Content worth mining

### Theme and data files

Counts are the number of opening tags of each kind, matched case-insensitively with grep. They are rough.

| File | Bytes | AREA | ROOM | MOB | ITEM | STRING | metacraft lines |
|---|---|---|---|---|---|---|---|
| town.xml | 122,848 | 9 | 28 | 51 | 201 | 98 | 43 |
| rats.xml | 67,148 | 8 | 21 | 26 | 130 | 102 | 10 |
| lair.xml | 73,737 | 8 | 18 | 20 | 190 | 120 | 24 |
| prison.xml | 53,785 | 8 | 17 | 18 | 153 | 82 | 18 |
| oceanfloor.xml | 44,121 | 9 | 7 | 21 | 11 | 246 | 0 |
| mountains.xml | 22,030 | 8 | 7 | 26 | 3 | 111 | 0 |
| plains.xml | 20,324 | 8 | 7 | 27 | 3 | 102 | 0 |
| snow.xml | 19,571 | 8 | 7 | 25 | 3 | 101 | 0 |
| swamp.xml | 19,426 | 8 | 7 | 23 | 3 | 103 | 0 |
| desert.xml | 18,389 | 8 | 7 | 20 | 3 | 101 | 0 |
| jungle.xml | 18,831 | 8 | 7 | 22 | 3 | 99 | 0 |
| forest.xml | 18,291 | 8 | 7 | 21 | 7 | 98 | 0 |
| planet.xml | 3,513 | 2 | 4 | 0 | 0 | 35 | 0 |
| example.xml | 5,343 | 10 | 1 | 0 | 0 | 28 | 0 |
| inc_common.xml | 30,451 | 0 | 0 | 0 | 167 | 146 | 83 |
| inc_adjectives.xml | 34,142 | 0 | 0 | 0 | 0 | 606 | 0 |
| inc_roomruns.xml | 26,021 | 0 | 0 | 0 | 0 | 319 | 0 |
| inc_fantasynames.xml | 7,243 | 0 | 0 | 0 | 0 | 56 | 0 |
| inc_streetnames.xml | 5,568 | 0 | 0 | 0 | 0 | 50 | 0 |
| inc_dungeonblurbs.xml | 3,950 | 0 | 0 | 0 | 0 | 53 | 0 |

The eight common layouts (TREE, SPOTTEDMESH, MAZE, GRIDCITY, CROSS, BRANCH, BOXCITYSQUARE, BOXCITY) each appear 13 times in `layout=` attributes across the data. MESH and APARTMENT appear once each.

### Mechanisms that are reusable as content

- **A single "random dungeon" chooser.** `example.xml` (lines 33-65 for the layout templates, line 47 for the chooser) defines eight dungeon templates that differ only in LAYOUT, then uses `select="any"` over them. Its `predefine` draws size from `30?60`, level range from `1?91`, and sets aggression as `10+$level_range`.
- **Room roles chosen by node type.** `town.xml:44` picks one `hallway_rooms` template (`pick-1`) for `street` or `surround` nodes. `town.xml:97` picks `interior_rooms` (`any-1`) for `interior` or `square`. `town.xml:112` picks `leaf_rooms` (`any-1`) for `leaf`. PICKWEIGHT is used heavily: `pickweight="1"` appears 403 times in the data, `"5"` 56 times and `"2"` 51 times.
- **Layout tags turned into English.** `inc_roomruns.xml:23-24` maps NODERUN `ns` to "north-south" and `ew` to "east-west". `inc_roomruns.xml:41-43` maps NODEEXITS `,n,` to "north" and `,n,s,` to "north and south". These drive the room description text.
- **One-per-area items using `__` variables.** A town inn desk template (`town.xml:298`) is allowed only while `__towninndesk` is below 1, it requires `'gate' in $ROOMTAG_NODEFLAGS`, and it increments the counter. This gives exactly one desk per town.
- **Procedural item stock.** `metacraft:any-<Skill> < $level_range` templates (for example for cooking, baking, weaponsmithing, armorsmithing, scribing and herbalism) appear in `lair.xml`, `prison.xml`, `rats.xml`, `town.xml` and `inc_common.xml`. Each one produces a random craftable item at or below the level range.
- **Name and text banks.** `inc_fantasynames.xml`, `inc_adjectives.xml`, `inc_streetnames.xml` and `inc_dungeonblurbs.xml` are STRING banks that are combined by the templates. `SYSTEM_RANDOM_NAME:min-max` builds syllable names (`MUDPercolator.java:3581-3587`).

## Notable design ideas

- **Shape first, content second.** The layout gives each room a type, run, exit set and flags. Templates test these with ordinary conditions, so one set of room templates adapts to corridors, dead ends and crossings (`MUDPercolator.java:1372-1388`).
- **Choice rules as data.** The SELECT modes (`any`, `all`, `pick-n` with weights, `limit`, `repeat`, positional) let a designer say "one of these", "about three of these" or "rarely this" without code (`:4205-4380`).
- **One roll per context.** A random expression in a condition is fixed the first time it is evaluated, so the same name or level is used throughout the room (`:4024-4027`).
- **Three scopes of definition.** Plain names last for one room, `_` for a room group, `__` for the whole area (`:1501-1509`, `:878-896`).
- **Gate exits stitch generated space into the world.** The generated entry room gets a reserved exit back to the parent, and existing neighbours get gate exits into the new area (`:1255-1264`, `:1291-1301`; `StdAutoGenInstance.java:676-690`). The area is grown from the edge the player came in by.
- **Items from crafting recipes.** `metacraft` runs the crafting skills to produce an item, so the game's own recipes supply loot (`MUDPercolator.java:2475-2549`).
- **Power bonus as level offset.** A mob's "power bonus" is the stat difference between its level and a higher level, stored as one affect (`:2056-2072`).
- **Growth and pruning in the layouts.** TREE grows stems that branch as they go. SPOTTEDMESH makes dead ends by cutting links, with a safety check (`SpottedMeshLayout.java:65-86`). CROSS and BRANCH grow from a spine. `clipLongStreets` turns long straight streets into leaves (`LayoutSet.java:237-267`).

## Problems and cruft

Items marked "unverified" were read but not run.

- **Comparator bug.** `objComparator` tests `o1` for `Environmental` on both lines and then casts `o2` (`MUDPercolator.java:235-236`). If `o1` is an Environmental and `o2` is not, the cast throws. It is used in sorting and set code in the MQL path (`:7561`, `:7582`). Reachability is unverified.
- **Possible endless loop in TREE.** `TreeLayout.generate` loops `while(lSet.spaceAvailable())` (`TreeLayout.java:138`). If no stem can grow while space remains, `progress` stays empty and the loop never exits. Reachability is unverified.
- **TREE overshoots its budget.** The space check runs once per stem (`TreeLayout.java:141`), but each stem can add up to three nodes (`:144-152`). The tree can therefore exceed the requested size by up to two. Read from code; not measured.
- **Sizes are not fitted.** MAZE gives 49 rooms for SIZE 50 (`MazeLayout.java:71-72`). The box layouts give about w x h. APARTMENT is approximate and uncapped (`ApartmentLayout.java:60-66`). `layOutRooms` accepts whatever count comes back (`MUDPercolator.java:905-907`).
- **Inconsistent size rules.** `fillInArea` accepts SIZE as a math expression (`MUDPercolator.java:1327-1328`). `findRoom` requires a plain integer for a room's LAYOUT size (`:1486`). `findRoom` also writes `ROOM_SIZE` into the parent's definitions (`:1488`) while reading the size from `rDefined` (`:1485`).
- **BOXCITYSQUARE plaza links are lossy.** `crossLink` keeps one link per cardinal direction (`DefaultLayoutNode.java:89-94`). The ring and the centre links include diagonal neighbours, which map to N or S (`AbstractLayout.java:53-64`). Some ring links are overwritten (`BoxCitySquareLayout.java:44-93`). Exits built from the plaza may therefore be wrong or missing. Unverified in play.
- **Key packing is fragile.** `LayoutSet.getHashCode` packs (x,y) as `x*total + y` (`LayoutSet.java:53-56`). It is unique only while the layout's y-span stays below `total`. That holds for a connected set of exactly `total` nodes, but not for a generator that overshoots.
- **Unbounded fill.** `LayoutSet.fillMaze` has no bounds of its own (`LayoutSet.java:209-235`). BOXCITY relies on the already-placed street nodes to close each block (`BoxCityLayout.java:89-98`). A gap in the street grid would let it grow until it meets existing nodes. Recursive depth also grows with block size (unverified at large SIZE).
- **Quadratic grouping.** The leaf loop restarts from index 0 after every group (`MUDPercolator.java:1043-1081`). The street loop rescans the list (`:1083-1152`). Each removal from a list is linear. Large areas will be slow (not measured).
- **Non-reproducible randomness.** `layOutRooms` seeds its `Random` from the clock (`MUDPercolator.java:903`). `SpottedMeshLayout` seeds from `nanoTime` (`SpottedMeshLayout.java:52`). `BranchLayout` uses `Math.random` (`BranchLayout.java:167`, `:182`). `AbstractLayout` uses an unseeded `Random` (`AbstractLayout.java:39`). The metacraft search is bounded by wall-clock time (`MUDPercolator.java:2531-2532`). The same template can produce different output on each run.
- **Apostrophes become backticks** in every filtered string (`MUDPercolator.java:8004`). The code gives no reason.
- **Wrong log names.** `MeshLayout.java:118` and `GridCityLayout.java:148` both log as "GridCityLayout".
- **Dead code and duplicates.** `BranchLayout` updates `minLeaves` and never reads it (`BranchLayout.java:88-96`). The corridor lists repeat their last node (`:102-118`, `:131-147`). `findRooms` (`MUDPercolator.java:1520-1547`) has no caller in the file. The `buildMob` path calls `fillOutMOB` twice (`:1954-1955`).
- **Stray imports.** `ApartmentLayout.java:3` imports `java.awt.List` (the file uses `java.util.List` by full name). `LayoutTester.java:7` imports `CMProps.Str` but does not use it.
- **Null type.** `DefaultLayoutNode.type()` calls `LayoutTypes.valueOf` on a possibly null tag (`DefaultLayoutNode.java:190-194`). An untyped node throws.
- **Odd code comments.** A comment at `MUDPercolator.java:1983` says the RACE setter does nothing useful. A faction VALUE is parsed with `Integer.parseInt` without a check (`:2018`).
- **Binary gender default.** Gender is a 50/50 roll (`:1973`). This is a simplification rather than an error.
- **Guide and schema mismatches.**
  - The guide lists the layouts as MAZE, BOXCITY, BOXCITYSQUARE, CROSS, GRIDCITY, TREE and APARTMENT (`guides/RandomAreas.html:624-625`). It omits BRANCH, MESH and SPOTTEDMESH, which the data uses and the code registers.
  - The guide says `repeat-X` "always selects every choice" (`guides/RandomAreas.html:289-291`). The code draws X choices with replacement (`MUDPercolator.java:4349-4361`).
  - `random.xsd` is not referenced by any Java file. It declares LAYOUT as an element (`random.xsd:23`, `:102`), but the engine reads it only as an attribute (`MUDPercolator.java:1478`, `:3925`). It also omits attributes the engine reads: LIKE, PICKWEIGHT, ACTION, TAGDEFINE, LLM and CONTENT_LOAD.

## Takeaways for a Python SUD

1. **Keep the two-stage split.** Write a shape generator that returns nodes with a type, a run, a set of exits and flags. Write a content filler that picks templates by those properties. In Python the node can be a small dataclass. Key links by neighbour identity or by a cardinal direction, and never by a diagonal:

   ```python
   @dataclass
   class Node:
       pos: tuple[int, int]
       kind: str = "interior"          # surround, leaf, street, square, interior
       run: str | None = None          # ns, ew, nesw, nwse
       exits: dict[str, "Node"] = field(default_factory=dict)  # cardinal only
       flags: set[str] = field(default_factory=set)            # gate, corner, tee, offleaf
   ```

2. **Borrow the selection rules, not the parser.** Implement `select(mode, choices, rng)` with `any`, `any-n`, `all`, `limit-n`, `repeat-n` and weighted `pick-n`. Store templates as Python data, for example dicts or YAML, with `when=` as a Python predicate or a small safe expression. A string expression language is not worth building.

3. **Use real scopes.** Replace the `_` and `__` prefix convention with a `Scope` chain: room, group, area. Use one `random.Random(seed)` per area, so a saved SUD can regenerate the same world.

4. **Layouts worth porting.** MAZE is about 20 lines of recursive backtracking. GRIDCITY and BOXCITY suit towns. CROSS and BRANCH suit villages. TREE suits caves and mines. Store the grid as `dict[tuple[int,int], Node]` rather than the hashed packing in `LayoutSet`. Keep the dead-end pruning idea from SPOTTEDMESH as a generic post-pass.

5. **Generate lazily.** The MUD builds whole areas at once and needs instance cleanup (`StdAutoGenInstance`, `passivemins`) because of groups. A single-player game can generate a region the first time the player steps into it, with the entry gate as the trigger. This drops most of the instance machinery.

6. **Drop.** The MQL engine (about 3,400 lines), the LLM hooks (`MUDPercolator.java:3403-3528`), the deferred post-process queue (replace it with ordinary dependency order), the catalog and gen-object plumbing, and the admin GENERATE command. Keep the metacraft idea in a bounded form: `random_item(recipes, level_max, rng)` with a fixed attempt count instead of a wall-clock budget.

7. **Rethink the multiplayer parts.** The per-group instance and its timeouts, the "reset instance" option, and the `mob.location()` gate choice (`Generate.java:268-269`) all assume several players. In SUD the gate is the player's own room and there is one persistent world.

8. **Keep the level trick.** Compute stat blocks as a function of level and store only the difference for a power bonus (`MUDPercolator.java:2056-2072`). Use an explicit gender distribution in data instead of the hard-coded 50/50.

## Coverage

No code was run. Every claim comes from reading.

- **Read in full:** `AreaGenerationLibrary.java`, `AbstractLayout.java`, `DefaultLayoutNode.java`, `LayoutSet.java`, `MazeLayout.java`, `TreeLayout.java`, `MeshLayout.java`, `SpottedMeshLayout.java`, `CrossLayout.java`, `BranchLayout.java`, `BoxCityLayout.java`, `BoxCitySquareLayout.java`, `GridCityLayout.java`, `ApartmentLayout.java`, `LayoutTester.java`, `resources/randareas/random.xsd`, `resources/randareas/example.xml`.
- **Read in part, the parts that matter for generation:** `MUDPercolator.java` lines 1-2185 (except 2022-2039), 2475-2549, 2755-2804, 3187-3572, 3573-3690, 3844-4380, 7808-8157. This covers the ID loading, areas, layouts, rooms, exits, mobs, items, conditions, SELECT, DEFINE, strings and relevelling.
- **Skimmed or grepped only:** `MUDPercolator.java` lines 2185-2474 (shop inventory, metacraft filter), 2549-2754, 2805-2865 (item tail), 2865-3186 (abilities, behaviours, races), 3316-3402, 3693-3843 (`buildQuestScript`, `findObject`), 4382-7807 (`strFilterNow` and the MQL engine, about 3,400 lines), 8158-8243.
- **Guide:** `guides/RandomAreas.html` was converted to text and read for the introduction, the tag overview, the meta-attributes, SELECT, DEFINE, value syntax, the AutoGen and AutoGrid types, and the AREA and ROOM glossary entries. The MOB, ITEM, AFFECT, ABILITY, BEHAVIOR, CONTENT, EXIT, RACE, FACTION, QUEST and OBJECT entries were not read.
- **Data:** `town.xml`, `inc_roomruns.xml` and `lair.xml` were sampled with grep. The other theme files and the `inc_*` banks were counted and not read.
- **Callers outside the bucket:** `Commands/Generate.java`, `Areas/StdAutoGenInstance.java`, `Locales/AutoGrid.java` and quest files were checked by grep for the relevant lines only.
