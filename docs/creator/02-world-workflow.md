# World files, builder mode and the editing workflow

[Handbook](README.md) · [Tool reference](tool-reference.md) · [State and persistence](state-and-persistence.md)

This chapter explains how a CoffeeSUD world is stored on disk, how you change
it from inside the game (builder mode) or by hand, and how the pieces that sit
between the two work: the content database cache, the `SAVE` variants, the
`WORLD` command family, `UNDO`, reloading, backups and the catalog. It is for
world creators first. Engine developers will find the code paths named in
each section; the internals are covered further in
[engine internals](33-engine-internals.md).

The short version: the **world folder** (`world/` by default) is the source of
truth and the thing you put in git. A **content database** under
`resources/fakedb/` is only a cache built from it. A **campaign save** under
`saves/` records what happened in one playthrough on top of the world.
Builder mode (`BUILDER=1`) is the only mode that writes the world folder.

## In this chapter

- [Source, cache, and campaign](#source-cache-and-campaign): the three layers and the launch modes
- [The world folder](#the-world-folder): every file and folder, with formats
  - [world.ini](#worldini)
  - [areas and dev areas](#areas-and-dev-areas-areascmare-devcmare)
  - [catalog.cmare](#catalogcmare)
  - [custom objects](#custom-objects-custom)
  - [quests](#quests-quests)
  - [scripts, js, routes, macros](#scripts-js-routes-and-macros)
  - [tests](#tests-tests)
  - [the resources overlay](#the-resources-overlay-resources)
  - [asset folders and files](#asset-folders-and-files)
  - [templates, clans, space, journals, vfs, laws, maps](#other-files-the-export-writes)
  - [housekeeping folders](#housekeeping-folders-and-files)
- [Builder mode](#builder-mode): starting it, what it changes, the first trap
- [Editor workflow](#editor-workflow): a working loop, step by step
- [SAVE reference](#save-reference): every form of `SAVE`
- [Unsaved rooms, UNDO and HISTORY](#unsaved-rooms-undo-and-history)
- [The WORLD command](#the-world-command): every subcommand
- [Protect and publish edits](#protect-and-publish-edits): checksums, `.builder` copies, `SAVE FORCE`, backups
- [Editing world files by hand](#editing-world-files-by-hand)
- [Reload deliberately](#reload-deliberately): what each reload does
- [The catalog](#the-catalog): `CATALOG` commands and how entries are stored
- [The content database cache](#the-content-database-cache)
- [World files versus campaign saves](#world-files-versus-campaign-saves)
- [Worked examples](#worked-examples)
- [Shared authoring grammars](#shared-authoring-grammars) and
  [Stable identities and migrations](#stable-identities-and-migrations)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## Source, cache, and campaign

Three things are easy to confuse:

| Layer | Where | Written by | Purpose |
| --- | --- | --- | --- |
| World files | `world/` (`WORLDDIR`) | Builder mode `SAVE`/quit, or your editor | The publishable definition of a new game. Version-control this. |
| Content database | `resources/fakedb/` (`DBSERVICE=jdbc:fakedb:resources/fakedb`) | The engine | A cache built from the world files, plus the builder's own characters. Git-ignored. |
| Campaign save | `saves/<slot>/` (`SAVEDIR`) | `SAVE`, autosave, quit in a normal game | One playthrough: its characters, party, live changes and state. |

A normal game never writes the world folder. It plays on a *working copy* of
the cache (`saves/.play-<time>/`) that is thrown away when the game ends;
saves are how a playthrough persists. Builder mode plays on the cache itself,
has no saves, and writes the world folder.

Editing things as an Archon in a normal game changes only that game's working
copy and its saves. The first time you use a building command in a normal game
the engine warns you ("This game plays on a copy of the world"); the editors
of the game's data files (HELP, AHELP, SOCIAL, ACHIEVEMENT, TITLE, AWARD, FACTION,
HOLIDAY, EXPERTISE, COMPONENT and PLANE) are refused in a normal game,
because they write files that every game reads. Start with `BUILDER=1` to
change the world itself.

Keep your world directory and the engine version together: a world is built
for the classes, properties and help of the engine that wrote it.

### Launch modes

All launches go through `CoffeeMud/sud.sh` (`sud.bat` on Windows). Any
`NAME=VALUE` argument overrides `coffeemud.ini`. See
[installing and running](18-installing-and-running.md) for the full list.

| Launch | Effect on the world |
| --- | --- |
| `sud.sh` | Normal game. Plays on a copy of the cache. World files read-only. |
| `sud.sh BUILDER=1` | Builder mode: plays on the cache, you are made an Archon, no saves, `SAVE` and quit write the world files. |
| `sud.sh DEV=1` | A normal game (with saves) whose characters have the developer security group. Loads `world/dev/` areas. Ignored with `BUILDER=1`. |
| `sud.sh CHECK=1` | Loads the world as a new game would, on a scratch copy, runs `WORLD CHECK` with no player, prints it, exits 1 if there were errors. For scripts and commit hooks. |
| `sud.sh WORLDDIR=/path/world` | Use another world folder. |
| `sud.sh DBSERVICE=jdbc:fakedb:/path/db SAVEDIR=/path/saves` | Keep the cache and saves elsewhere (useful for isolated test worlds; the [museum example](examples/README.md) does this). |

Sources: [SUD.java](../../CoffeeMud/com/planet_ink/coffee_mud/application/SUD.java),
[SaveGame.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/save/SaveGame.java),
[WorldFiles.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldFiles.java).

### How the game decides whether to rebuild from files

At each start the engine computes a checksum of the world files and compares
it with the one stored in the cache (`world.sum`):

- **Same checksum:** the cache is current. A normal game copies its content
  tables into a fresh working database; builder mode plays on the cache.
- **Different checksum** (you edited a file, pulled from git, or the cache is
  missing): the console prints "The world files have changed: rebuilding the
  world from them..." and the world is built from the files: custom objects
  first, then areas, the catalog, and the quests. A normal game copies the
  result back into the cache so the next start is fast.

The details, including which files count towards the checksum, are in
[The content database cache](#the-content-database-cache).

## The world folder

`world/` is the folder named by `WORLDDIR`. Nothing in it is required. The
shipped `CoffeeMud/world/` holds only `areas/new-area.cmare` (the two-room
starter area), `quests/holidays.quest`, and `tests/start.play`.

```text
world/
  world.ini                  settings that make this world the game it is
  areas/NAME.cmare           one file per area (rooms, exits, mobs, items)
  dev/NAME.cmare             dev areas: builder mode and DEV=1 games only
  catalog.cmare              the catalog of reusable mobs and items
  custom/races|classes|abilities|commands|behaviors/ID.cmare   custom generic objects
  quests/NAME.quest          one quest script per file
  scripts/NAME.script        scripts on the hidden WORLD host
  js/init.js, js/lib/, js/<kind>/   world JavaScript
  routes/NAME.xml            MovingRoom route files
  macros/NAME.cmd            RUNFILE command lists
  tests/*.play|*.test|*.js   regression scenarios
  resources/...              overlay of the stock resources/ folder
  vocab/ lore/ text/ kits/ encounters/ classes/ modules/ compositions/ combat/ itemmods/
  score.txt ranks.ini crops.txt resources.txt materials.txt creation.ini origins.ini
  perks.ini combinations.txt itemsets.txt affixes.xml
  templates.cmare clans.cmare space.cmare journals/ vfs/ laws/ maps/
  .backup/ .history/         SAVE backups and UNDO history (git-ignored)
```

A file can be in one of three states with respect to the running game:

- **Written by the export:** the builder-mode `SAVE`/quit rewrites it from the
  live world (areas, dev areas, catalog, custom objects, quests, templates,
  clans, space, journals, vfs). Hand edits to these are protected from being
  overwritten; see [Protect and publish edits](#protect-and-publish-edits).
- **Hand-authored, tracked:** never generated or deleted by `SAVE`, but
  counted in the world's checksum and watched for "changed outside the game"
  (scripts, js, routes, and the asset folders and files).
- **Not tracked:** read when needed and ignored by the checksum
  (`macros/`, `tests/`, `maps/`, `laws/`, `namegen/`, `upgrades.txt`,
  `world.ini`, the housekeeping folders).

| Path | Kind | Format | In checksum | Reloadable live |
| --- | --- | --- | --- | --- |
| `world.ini` | settings | `KEY=VALUE` lines | no (read at every start) | no, next start |
| `areas/*.cmare` | exported | XML | yes | `WORLD RELOAD` |
| `dev/*.cmare` | exported | XML | no (own `dev.sum`) | `WORLD RELOAD` |
| `catalog.cmare` | exported | XML | yes | `WORLD RELOAD CATALOG` |
| `custom/**` (and legacy `custom.cmare`) | exported | XML (+ `.script`) | yes | next start |
| `quests/*.quest` | exported | quest script text | yes | `WORLD RELOAD QUEST` |
| `templates.cmare`, `clans.cmare`, `space.cmare` | exported | XML | yes | next start |
| `journals/*.xml`, `vfs/**` | exported | XML / raw bytes | yes | next start |
| `scripts/**`, `js/**`, `routes/**` | hand-authored | script / JS / XML | yes | see [Reload deliberately](#reload-deliberately) |
| `vocab/ lore/ text/ kits/ encounters/` and `score.txt creation.ini origins.ini perks.ini combinations.txt itemsets.txt` | hand-authored | various | yes | `WORLD RELOAD ASSETS` |
| `resources/**`, `ranks.ini`, `crops.txt`, `resources.txt`, `materials.txt`, `classes/**` | hand-authored | various | yes | subsystem reload or restart |
| `modules/ compositions/ combat/ itemmods/ affixes.xml` | hand-authored | JSON / XML | no (versioned by their own subsystems) | `WORLD MODULES/COMPOSITIONS/COMBAT/ITEMMODS RELOAD` |
| `macros/ tests/ maps/ laws/ namegen/ upgrades.txt` | misc | text | no | read on use |
| `.backup/ .history/ *.builder` | housekeeping | copies | no | n/a |

The rest of this section describes each file with a short example. Every
example below is a real layout the engine reads (adapted from the shipped
world, test fixtures or the [museum example](examples/README.md)).

### world.ini

Plain `NAME=VALUE` lines read at every start, *after* `coffeemud.ini` and
*before* the command line (so the command line still wins). `#` and `!` start
comments. Only keys on the world allowlist are accepted; any other key is
reported ("`X` is not a world setting, so ignored") and skipped, because the
engine's own settings (database, logs, JavaScript limits) belong in
`coffeemud.ini`. The allowlist and value grammar are in
[world settings](world-settings.md); [configuration](19-configuration-and-administration.md)
gives an overview.

```ini
# A small world's rules.
GAMENAME=The Drowned Coast
START=Coast#0
TIMEMODE=TURNS
HOURSINDAY=12
SAVEMODE=SAVEPOINTS
DIFFICULTY=NORMAL
```

Inspect what is in effect, and where each value came from (`coffeemud.ini`,
`world.ini` or the command line), with `WORLD` (alias `LIST WORLDINI`). In
builder mode `WORLD SET KEY=VALUE` rewrites that line (or appends it; a blank
value removes it) while keeping the rest of the file, and takes effect at the
next start. `WORLD SET` needs sysop rights, which builder mode gives you.
Prefixed families (`START_<RACE>`, `DEATH_...`, `FORMULA_...`, `CURRENCY_...`
and others) are accepted when they begin with an allowed prefix.

`world.ini` is not part of the checksum and is not tracked as "changed
outside the game": it is simply re-read whenever the game starts.

### Areas and dev areas (`areas/*.cmare`, `dev/*.cmare`)

One XML file per area, named from the area: lower-case, runs of anything other
than letters and digits become a dash (`New Area` becomes `new-area.cmare`;
duplicates get `-2`, `-3`...). The file holds the area's own fields, then every
room with its exits, and the mobs and items placed in it. Rooms are identified
by `ROOMID` (`Area Name#number`).

This is the shipped starter area, abridged:

```xml
<AREA>
 <ACLAS>StdArea</ACLAS>
 <ANAME>New Area</ANAME>
 <ADESC />
 <ACLIM>0</ACLIM>
 <ATECH>0</ATECH>
 <ADATA>
  <PARENTS />
  <CHILDREN />
  <BEHAVES />
  <AFFECS />
  <AUTHOR />
  <AATMO>-1</AATMO>
 </ADATA>
 <AROOMS>
  <AROOM>
   <ROOMID>New Area#0</ROOMID>
   <RAREA>New Area</RAREA>
   <RCLAS>StdRoom</RCLAS>
   <RDISP>New Room</RDISP>
   <RDESC>Brand new database room! ...</RDESC>
   <RTEXT>&lt;RCLIM&gt;-1&lt;/RCLIM&gt;&lt;RATMO&gt;-1&lt;/RATMO&gt;&lt;IMG /&gt;&lt;BEHAVES /&gt;&lt;AFFECS /&gt;</RTEXT>
   <ROOMEXITS>
    <REXIT>
     <XDIRE>0</XDIRE>
     <XDOOR>New Area#1</XDOOR>
     <XEXIT>
      <EXID>StdOpenDoorway</EXID>
      <EXDAT />
     </XEXIT>
    </REXIT>
   </ROOMEXITS>
   <ROOMCONTENT>
    <ROOMMOBS />
    <ROOMITEMS>
     <RITEM>
      <ICLAS>ManualArchon</ICLAS>
      <ILOCA />
      <IREJV>0</IREJV>
      <IUSES>2147483647</IUSES>
      <ILEVL>0</ILEVL>
      <IABLE>0</IABLE>
      <ITEXT />
     </RITEM>
    </ROOMITEMS>
   </ROOMCONTENT>
  </AROOM>
  ...
 </AROOMS>
</AREA>
```

What the main tags mean:

| Tag | Meaning |
| --- | --- |
| `ACLAS` / `ANAME` / `ADESC` | Area class, name, description |
| `ACLIM`, `ATECH`, `AATMO` | Climate, tech level, atmosphere (see [simulation](10-world-simulation.md)) |
| `ADATA` | Area behaviors (`BEHAVES`), effects (`AFFECS`), author, parents/children (sub-areas) and other area data |
| `AROOM` / `ROOMID` / `RAREA` / `RCLAS` | A room, its ID, owning area and locale class |
| `RDISP` / `RDESC` | Room title and description |
| `RTEXT` | The room's own text: climate, behaviors, effects (escaped XML, or readable, see below) |
| `REXIT` / `XDIRE` / `XDOOR` / `XEXIT` | An exit: direction number, destination room ID (`Area#n`), and the exit object (`EXID` class, `EXDAT` its text) |
| `RMOB` / `MCLAS` / `MTEXT` / `MLEVL` / `MABLE` / `MREJV` | A placed mob: class, own text, level, ability, rejuvenation |
| `RITEM` / `ICLAS` / `ITEXT` / `ILOCA` / `IUSES` / `ILEVL` / `IREJV` | A placed item: class, text, container it sits in, uses, level, rejuvenation |

A room that holds more than 20 items collapses identical ones into a single
`<RITEM COUNT=n>`.

**Readable layout (`xml="1"`).** When the export writes a file, a thing's own
text (`ITEXT`, `MTEXT`, `RTEXT`, `EXDAT`, `ADATA`) is laid out as indented XML
instead of one escaped line, so that git diffs show changes line by line:

```xml
<ITEXT xml="1">
 <NAME>the Sunblade</NAME>
 <DESC>A blade of pale gold, warm to the touch.</DESC>
 <PROP ABILITY="0" ARMOR="0" ATTACK="0" DAMAGE="5" DISPOSITION="0" LEVEL="5" REJUV="0" SPEED="1.0" WEIGHT="2" HEIGHT="0" SENSES="0" />
 <BEHAVES />
 <AFFECS />
</ITEXT>
```

The reader accepts both forms. The layout only changes whitespace *between*
tags: text values (descriptions, `<VARIES>` blocks, script bodies kept as
CDATA) are preserved exactly, so authored line breaks and markup survive a
save. `PROP` writes the base stat values as named attributes and reads them
back in the fixed order ABILITY, ARMOR, ATTACK, DAMAGE, DISPOSITION, LEVEL,
REJUV, SPEED, WEIGHT, HEIGHT, SENSES.

Do not hand-write rooms from scratch if you can avoid it: let the editor make
them, then export. Hand edits are best for bulk text changes (descriptions,
names), copying rooms between areas, and fixing values.

**Dev areas.** An area tagged `DEV` is written to `dev/NAME.cmare` instead of
`areas/`. Builder mode and `DEV=1` games load them; a normal game never does
(a normal game deletes any dev area its cache holds). Use them for test
hubs, scratch maps and QA rooms that must not ship. In builder mode:

```text
WORLD DEV                    list the dev areas
WORLD DEV ON                 make this area a dev area (SAVE writes it to world/dev)
WORLD DEV My Test Area ON    ... or name the area
WORLD DEV OFF                make it a shipped area again
```

`WORLD CHECK` warns about an exit from a shipped area into a dev area, which
leads nowhere in a normal game. Dev areas have their own checksum (`dev.sum`)
so changing them does not make a normal game rebuild its world. `DEVSTART=`
in `coffeemud.ini` can start new characters in a dev room in builder and DEV
games.

### catalog.cmare

The reusable mobs and items of the [catalog](#the-catalog), with their
category and spawn/drop rules. Written only if the catalog is not empty.

```xml
<CATALOG>
 <CATITEM>
  <ITEM>
   <ICLAS>GenWeapon</ICLAS>
   <IUSES>100</IUSES>
   <ILEVL>5</ILEVL>
   <IABLE>0</IABLE>
   <IREJV>0</IREJV>
   <ITEXT xml="1"> ... </ITEXT>
  </ITEM>
  <CATEGORY />
  <CATADATA xml="1">
   <CATALOGDATA NAME="the Sunblade" CATAGORY="">
    <RATE>100%</RATE>
    <LMASK>-NAMES "+an iron chest"</LMASK>
    <SPAWN>CONTAINER</SPAWN>
    <CAP>1</CAP>
    <UNIQUE>ONCE</UNIQUE>
   </CATALOGDATA>
  </CATADATA>
 </CATITEM>
 <CATMOB> ... <MOB><MCLAS>GenMob</MCLAS><MTEXT xml="1"> ... </MTEXT> ... </MOB> ... </CATMOB>
</CATALOG>
```

The file must begin with `<CATALOG>`; anything else loads as "it does not
start with CATALOG". An entry whose `ICLAS`/`MCLAS` is not a known class is a
load error ("a mob of no known class"). `CATALOGDATA` fields are described in
[The catalog](#the-catalog).

### Custom objects (`custom/`)

Custom generic races, character classes, abilities, commands and behaviors
(things made with `CREATE RACE`, `CREATE ABILITY`, `GenCommand`, generic
behaviors and so on) are written one object per file in a folder per kind:

```text
custom/races/ID.cmare       <RACE> ... </RACE>
custom/classes/ID.cmare     <CCLASS> ... </CCLASS>
custom/abilities/ID.cmare   <ABILITY ID="..." TYPE="..."> ... </ABILITY>
custom/commands/ID.cmare    <COMMAND ID="..." BASE="..."> ... </COMMAND>
custom/behaviors/ID.cmare   <BEHAVIOR ID="..."> ... </BEHAVIOR>
```

An ability's or command's `<SCRIPT>` of more than one line is written to a
file beside it, `custom/abilities/ID.script`, and the element refers to it as
`<SCRIPT>FILE:ID.script</SCRIPT>`. Edit the `.script` file with a normal
editor. Older worlds had a single `custom.cmare`; it is still read, and the
next export replaces it with the per-object files (the old file is backed up
and removed).

Custom objects are put into the database *before* the engine loads its
classes, so areas can use them. See [characters](03-characters.md),
[abilities](25-abilities-and-effects.md) and
[engine programming](32-engine-programming.md) for creating them.

### Quests (`quests/`)

One quest script per file, `quests/NAME.quest`, named from the quest name.
The file is the quest's script exactly as the quest list holds it (see
[quests](08-quests.md)). A quest file loaded at boot becomes a row in the
quest list; the quest's real name is inside the script (`set name`), not the
file name.

```text
set name gather_herbs
set duration 100
```

The shipped `quests/holidays.quest` is a one-line file that loads the stock
holiday quests:

```text
LOAD=quests/holidays/holidays.quest
```

`WORLD RELOAD QUEST name` reads one back from its file while the game runs.

### Scripts, js, routes and macros

| Folder | Contents | Notes |
| --- | --- | --- |
| `scripts/*.script` | MOBPROG scripts on one hidden **WORLD** item | Made at the first login after start; ticks wherever the player goes. Triggers that fire anywhere (`GAME_PROG`, `QUEST_PROG`, `TURN_PROG`, `DELAY_PROG`, `RAND_PROG`...) hear the whole game. Also loadable by any script with `LOAD=world:scripts/name.script`. |
| `js/init.js`, `js/lib/*.js`, `js/state.json`, `js/<kind>/` | World JavaScript | `init.js` is re-read at each start and may define `onBoot`, `onBuilderStart`, `onPlayerEnter`. `js/<kind>/` (behaviors, abilities, properties, items, mobs, races, charclasses, commands, common, locales, exits, areas) are JavaScript-defined engine classes added to the class path. See [JavaScript](05-javascript.md). |
| `routes/NAME.xml` | `MovingRoom` route files | Referenced as `ROUTE=NAME` by the behavior. |
| `macros/NAME.cmd` | `RUNFILE` command lists | Not tracked or checksummed. |

`scripts/`, `js/` and `routes/` count towards the world checksum, and
`*.builder` copies are ignored there.

`macros/NAME.cmd` is a plain list of game commands, one per line, run as the
builder typed them:

```text
# world/macros/trail.cmd   run with:  RUNFILE trail north
STOPONERROR
WORLD PATH $1,$1,$1 TYPE=StdRoom NAME="A trail"
LIST UNSAVED
```

`$1`-`$9` are the arguments, `$room` is the builder's room ID and `$area`
its area name. `#` starts a comment; `STOPONERROR` stops at the first line that
fails. A line fails if no command, social or skill you may use starts with its
first word, or if it throws. The summary says how many lines ran and failed.
(A command that opens an editor still reads what you type next.) For richer
automation use `JRUN` and its `builder` helper
([JavaScript](05-javascript.md)).

### Tests (`tests/`)

`.play`, `.test` and `.js` scenarios that `WORLD TEST` and `tools/playtest.sh`
run. The shipped `tests/start.play` is the model:

```text
# The start of the game: the first room, its book, and the way north.
#char Wanderer,Human,Apprentice
look
#expect Obvious exits
#assert HERE New Area#0
get book
#assert HAS book
north
#reject You can't go that way
```

See [testing and release](13-testing-release.md). `tests/` is not tracked.

### The resources overlay (`resources/`)

`world/resources/<path>` shadows `resources/<path>` (the engine's stock data
folder), file for file:

- A file in `world/resources/` is read **instead of** the stock file of the
  same path (help files, socials, titles, achievements, factions, recipes,
  scripts, chat files, text screens, random-area XML...).
- `NAME.add` is read **after** the stock `NAME`, to append a few lines
  (recipes, list entries) without copying the whole file.
- A folder listing shows the world's files together with the stock ones, so a
  new file is found as if it were stock (`world/resources/progs/guard.script`
  is `LOAD=progs/guard.script`).
- The translation and parser rule files are the exception: the world's copy
  is read *before* the stock file and its rules win; a line `#STOCK OFF` in a
  world section leaves out the stock rules of that section.
- `DISABLE=STDACHIEVEMENTS`, `STDTITLES` or `STDSOCIALS` makes the stock
  `achievements.ini`, `titles.ini` or `socials.txt` read as missing, leaving
  only the world's.
- In **builder mode**, an editor that writes a file under `resources/`
  (HELP, SOCIAL, ACHIEVEMENT and the others) writes the *world's* copy, so
  the world carries what you change. In a normal game those editors are
  refused. `EXTRACT SCRIPT` likewise puts a script in
  `world/resources/progs/NAME.script`.
- A path beginning `world/` in `LOAD=` and similar places is the world folder,
  wherever `WORLDDIR` points.

Never edit stock `resources/` to change one world: put the changed file in
`world/resources/` with the same relative path. A recipe's result may be
`CATALOG:name` to produce a copy of a catalog item.

### Asset folders and files

These are hand-authored and tracked (changing one changes the world checksum).
Formats differ by purpose; they are not interchangeable INI files. Each is
documented in the chapter that uses it.

| Path | What it is | Format and where documented |
| --- | --- | --- |
| `vocab/` | Languages, glossary, word lists, translation lexicon (`vocab/languages/NAME.txt` etc.) | UTF-8; `AHELP LANGUAGE_AUTHORING`; [presentation](11-presentation-language.md) |
| `lore/*.ini` | Codex entries | `KEY=title\|category\|text`, optional `FILE=path`; UTF-8; [presentation](11-presentation-language.md) |
| `text/NAME.txt` | Paged narration, `[section]` blocks, `title.txt` shown at launch | Plain text with script substitutions and `<VARIES>`; [presentation](11-presentation-language.md) |
| `kits/NAME.kit` | Character kits for `KIT`/`TESTCHAR` and creation | One setting a line (`race`, `class`, `level`, `gold`, `room`, `item`, `ability`, `faction`, `var`, `starting`, `title`, `wear`...); [characters](03-characters.md) |
| `encounters/NAME.enc` | Encounter tables for `RandomEncounters` / `ENCOUNTER` | One entry per line: `MOBS`, `ITEM`, `EMOTE`, `TRAP`, `FUNC`...; [combat](29-combat-and-encounters.md) |
| `score.txt` | Extra lines shown by SCORE | One line per row, rendered with prompt `%` codes and story markup; lines that render empty are dropped; [presentation](11-presentation-language.md) |
| `creation.ini`, `origins.ini`, `perks.ini`, `ranks.ini` | Character creation steps, origins, perks, rank ladders | Sectioned INI-style files; [characters](03-characters.md) |
| `materials.txt`, `resources.txt`, `crops.txt`, `itemsets.txt`, `combinations.txt` | World materials, resource variants, crops, item sets, `USE X ON Y` combinations | Tab-separated or line-oriented; [crafting](07-items-crafting.md) |
| `affixes.xml` | Random-enchantment affixes | XML `<AFFIX id=...>`; [item modifiers](17-item-modifiers-crafting.md) |
| `classes/<kind>/` | World-owned Java classes (`.class`) or `.js` files per class path key | `classes/commands/`, `classes/abilities/`, `classes/mobs/`, `classes/behaviors/`, ... (COMMON, ITEMS, WEAPONS, ARMOR, MISCMAGIC, CLANITEMS, MOBS, BEHAVIORS, ABILITIES, CHARCLASSES, RACES, COMMANDS, LOCALES, AREAS, EXITS, TECH, COMPTECH, SOFTWARE); added to the class path after the stock ones at start; [engine programming](32-engine-programming.md) |
| `modules/`, `compositions/`, `combat/`, `itemmods/` | Optional-module, ability-composition, combat-profile and item-modifier JSON | [modules](14-optional-modules.md), [composition](15-ability-composition.md), [combat profiles](16-combat-profiles.md), [item modifiers](17-item-modifiers-crafting.md); kept out of the world checksum, each versions itself |
| `namegen/STYLE.ini` | Random name styles (checked before `resources/namegen`) | [generation](12-generation.md) |
| `upgrades.txt` | `ItemUpgrader` upgrade list | `UPGRADES=world/upgrades.txt` parameter |

Only the files listed in the table's first column are tracked; other names
you put at the top of `world/` are ignored by the engine.

### Other files the export writes

These are generated by the builder-mode export from live state. You normally
never edit them by hand, but they are plain text and can be reviewed in git.

| File | Content |
| --- | --- |
| `templates.cmare` | Builder templates (personal and shared, all written as shared): `<TEMPLATES><TEMPLATE WHO="..." KEY="...">...</TEMPLATE></TEMPLATES>`. Imported as shared templates at new-game and builder rebuilds. |
| `clans.cmare` | Clans with no player members (NPC-run organisations such as an orc horde): `<CLANS><CLAN><ID/><PREMISE/><ACCEPT/><STATUS/><DATA/><RECALL/><DONATION/><MORGUE/><TROPHIES/></CLAN></CLANS>`. See [society](09-society.md). |
| `space.cmare` | Things in space and in no room (ships, stations, stars, asteroids) with position, speed and course: `<SPACE>` of item XML. Put in space at each boot (no database table holds them). |
| `journals/NAME.xml` | A world journal with entries (notice boards, books, and `SYSTEM_CRON` for world cron jobs): `<JOURNAL NAME="..."><ENTRY><KEY/><FROM/><TO/><DATE/><UPDATE/><EXPIRES/><PARENT/><ATTRS/><DATA/><ICON/><SUBJ/><MSG/></ENTRY></JOURNAL>`. |
| `vfs/...` | Files of the database's virtual file system (written with `SHELL ::`), one real file each, plus `vfs/.index` (tab-separated: path, type, owner). |
| `laws/AREA.ini` | An area's own law (an Arrest behaviour's custom law) written when the law book is edited in builder mode; the file is named like the area's `.cmare`. See [society](09-society.md). |
| `maps/NAME.html` | Output of `WORLD MAP HTML=NAME` (never read back). |

### Housekeeping folders and files

| Path | Created by | Content |
| --- | --- | --- |
| `.backup/` | The export, before overwriting or deleting a world file | Up to `BACKUPS=` copies (default 5, `0` disables) of each file, named `path~with~tildes.YYYYMMDD-HHMMSS-mmm`. A `.gitignore` containing `*` is written. `WORLD BACKUPS` / `WORLD RESTOREFILE`. |
| `.history/` | Logged building commands | Up to 50 `.undo` files for `UNDO`: text with `WHAT`, `WHEN` and one `ROOM <length> <id>` block per room as it was before the command. A `.gitignore` containing `*` is written. |
| `FILE.builder` | The export, beside a file you changed outside the game | The text the export *would* have written there. Delete it after reconciling; the export removes it when the file is back in step. |

Inside the database folder (`resources/fakedb/` by default), not in `world/`:
`fakedb.data.*` (the tables), `world.sum`, `dev.sum` and `worldproblems.txt`.
See [The content database cache](#the-content-database-cache).

## Builder mode

Start it with `BUILDER=1` (in `coffeemud.ini`, or on the command line):

```sh
CoffeeMud/sud.sh BUILDER=1
CoffeeMud/sud.sh BUILDER=1 WORLDDIR=/path/to/my-world
```

On the first login the engine makes you an Archon (it has you read the
Archon's manual), reports any world files that loaded only in part, and ends
with a short summary: whether the world files loaded whole, how many files
were changed outside the game, how many rooms have unsaved mobs or items, and
a pointer to `AHELP BUILDING`. `AHELP BUILDING` (alias `BUILDERMODE`) is the
in-game one-page workflow.

What is different in builder mode:

- You play on the content database itself. There are no saved games.
- Your characters live in the cache (the cache is not rebuilt out from under
  them when you edit files; only its content tables are).
- Building commands change the live world *and* the database cache at once.
  The world **files** change only when you `SAVE` or quit.
- Quitting exports the world files (`QUIT` first asks about unsaved mobs and
  items).
- Dev areas (`world/dev`) are loaded.
- `UNDO`, `WORLD STATUS/DIFF/BACKUPS/RESTOREFILE/DIG/PATH/DEV/SET`,
  `LIST UNSAVED`, `SAVE CHANGED` and `DEVWATCH` are available.
- Editors that write data files under `resources/` write the world's copy.

If the process dies without a clean quit, the database cache still holds
what you built, and because the files still match the stored checksum the
next builder start plays on that cache. Run `SAVE` to bring the files up
to date. Do not rely on this; save often.

### The first trap: mobs and items need SAVE ROOM

Rooms, exits and areas are written to the database as you edit them
(`MODIFY ROOM`, exit edits). **Mobs and items you `CREATE` are only in the
live room** until you save that room's contents. Without it they vanish when
the room resets and are not in the exported file. Use `SAVE ROOM` (which
saves each mob into its own start room; see [SAVE reference](#save-reference)),
`SAVE CHANGED` for every touched room at once, or set `BUILDERAUTOSAVE=1`.
`LIST UNSAVED` shows what is outstanding; so does the prompt code `%U`, and
the `QUIT` and plain `SAVE` messages.

## Editor workflow

A practical loop for building in the game:

1. Start builder mode and `WORLD STATUS` to confirm nothing was changed
   outside the game. `WORLD CHECK` shows the current state of the world.
2. Make or change things. The editing commands are covered in
   [areas, rooms and exits](20-areas-rooms-exits.md),
   [mobs](21-mobs.md) and [items](22-items.md); in outline:
   - `CREATE` starts a generic definition or places an object. `MODIFY` edits
     one. `DESTROY` removes. `COPY` clones. `TRANSFER` moves.
   - `GMODIFY` and MQL (`MODIFY UPDATE:` ...) apply broad changes, including
     rooms, exits and areas. Preview the selection first, and use version
     control for bulk edits. `GMODIFY CHANGE=` resets each room first, which
     drops unsaved mobs and items (it asks).
   - `LIST` finds definitions; `STAT` inspects live objects.
   - `EDIT SCRIPT [target] [n]` and `EDIT DESC [target]` open the line editor
     (list, edit, insert, delete, add, replace, save, quit); on save a script
     is checked as `SCRIPTCHECK` does. `EXTRACT SCRIPT` moves an inline script
     into `world/resources/progs/NAME.script` and leaves `LOAD=`. MODIFY's
     script fields and multi-line descriptions open the same editor.
     `EDITOR=WINDOW` or `EDITOR=EXTERNAL` (with `EDITORCOMMAND=`, `$VISUAL`
     or `$EDITOR`) in `coffeemud.ini` choose another editor.
   - The effects editor can filter, edit an existing property in place, and
     duplicate instances; distinguish editing one from adding another.
   - `WORLD DIG ON` makes a room wherever you walk where there is no exit;
     `WORLD PATH e,e,n` lays out a chain of rooms; `WORLD MAP` draws the area
     ([The WORLD command](#the-world-command)).
   - `GENERATE` builds whole areas from random-area templates
     ([generation](12-generation.md)); `GENERATE ... SANDBOX` tries one first.
   - `IMPORT` and `EXPORT` move standalone `.cmare` files in and out of the
     running world (see their `AHELP`). An imported area becomes part of the
     world files at the next `SAVE`.
   - `RUNFILE name args` runs a `world/macros/name.cmd`; `JRUN file args`
     runs JavaScript with the `builder` helper object.
3. Try it. `SANDBOX ENTER` takes you to a scratch area (unsavable, never in
   the files or the database); `SANDBOX PREVIEW name` puts a copy of a
   catalog mob or item, or a builder template, there to look at; `SANDBOX
   CLEAR` empties it; `SANDBOX LEAVE` returns you. `PLAYTEST` plays as an
   ordinary character, and `BUILD` runs one builder command meanwhile.
4. Save mobs and items: `LIST UNSAVED`, then `SAVE CHANGED` (or `SAVE ROOM`).
5. Publish the files: `SAVE`. Read the report: files written, files left
   because they were changed outside the game, files held back because they
   loaded only in part, unsaved rooms, and script-check problems.
6. Check: `WORLD CHECK`, `SCRIPTCHECK`, `PROPCHECK`, `QUESTCHECK`,
   `ORGCHECK`, `LIST LINKAGES`. From a shell, `sud.sh CHECK=1`.
7. Commit the `world/` folder with git. `tools/worlddiff.py` compares two
   worlds object by object (see below).

**Templates and the catalog.** `TEMPLATE ADD [id] [thing/here]` stores a
builder template (a mob, item, room or exit) under a short id, which `CREATE`
can then use by name; `TEMPLATE LIST`, `DELETE [id]` and `TOGGLE [id]`
(shares it) manage them. The
[catalog](#the-catalog) holds reusable mob and item designs shared by name
across the whole world. Both are exported (`templates.cmare`,
`catalog.cmare`).

**`JRUN` builder helper.** In a `JRUN` script the `builder` object offers
`here()`, `room(id)`, `dig(dir, type)`, `link(fromId, dir, toId)`,
`create(kind, name, roomId)` (`kind` is `mob` or `item`), `saveRoom(id)`,
`forEachRoom(area, fn)` and `cmd(line)`, each acting as the builder who ran
it. Rooms and things it touches are marked unsaved like any other building
command. Details in [JavaScript](05-javascript.md).

```js
// JRUN file: add a room to the east, give it a chest, and save it
var N = builder.dig('east', 'StdRoom');
builder.create('item', 'StdItem', '' + N.roomID());
builder.saveRoom('' + N.roomID());
builder.cmd('LIST UNSAVED');
```

**Comparing worlds.** `tools/worlddiff.py OLD_WORLD NEW_WORLD` or
`tools/worlddiff.py --git REV [WORLD]` compares two world folders object by
object: rooms by ID (kind, title, description, behaviours, effects, exits and
the mobs and items placed there), and other files as added, removed or changed.
It exits 1 if there are differences. It understands both the readable
(`xml="1"`) and older escaped layouts.

```sh
tools/worlddiff.py --git HEAD~1 CoffeeMud/world
```

## SAVE reference

`SAVE` means different things in builder mode and a normal game. In builder
mode a bare `SAVE`, `SAVE FORCE` and `SAVE CHANGED` write the world files; the
room/area/item variants save database rows first and then export. In a normal
game a bare `SAVE` or `SAVE name` is a *campaign* save
([saves and party](31-saves-and-party.md)).

Source: [Save.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Save.java).

| Command | Mode | What it does |
| --- | --- | --- |
| `SAVE` | builder | Export the world files: every area and dev area, custom objects, catalog, quests, templates, clans, space, journals, vfs. Writes only files whose text changed, deletes files for things that are gone, backs up what it overwrites, and leaves files changed outside the game or loaded only in part. Prints a report. |
| `SAVE FORCE` | builder | As `SAVE`, but also writes over files changed outside the game and files that loaded only in part, then clears the problems list. Review `WORLD DIFF` and `WORLD PROBLEMS` first. |
| `SAVE CHANGED` | builder | Saves the mobs and items of every room `LIST UNSAVED` shows (as `SAVE ROOM HOME`), then exports. Says "No room has unsaved mobs or items." if none. |
| `SAVE ROOM` | builder / normal Archon | Saves this room's exits and its contents. In builder mode the default is `HOME` (below); in a normal game it is `ALL`. |
| `SAVE ROOM HOME` | either | Items as `SAVE ROOM`; each mob into **its own start room**. Mobs of this room that have wandered off are saved here; mobs standing here whose home is elsewhere are left out and listed ("Saved 2 mobs as this room's; skipped: ..."). |
| `SAVE ROOM ALL` | either | The older behaviour: every savable mob and item standing in this room is saved into this room. If the saved counts would change, asks "Saving X will add/delete N mob(s)... Are you sure (Y/n)?". |
| `SAVE AREA ROOM` (`HOME`/`ALL`) | either | `SAVE ROOM` for every room of the area. Asks "Doing this assumes every mob and item in every room in this area is correctly placed. Are you sure (N/y)?" (default N). |
| `SAVE ITEMS` | either | Only this room's items. |
| `SAVE AREA ITEMS` | either | Items of every room of the area (confirmation, default N). |
| `SAVE MOBS` | either | Only this room's mobs (as standing here). |
| `SAVE AREA MOBS` | either | Mobs of every room of the area (confirmation, default N). |
| `SAVE REWORLD ITEMS` | either | Loads and re-saves every item in the world. Rarely needed (asks, default N). |
| `SAVE QUESTS` | either (`CMDQUESTS`) | Saves the quest list to the database ("Quest list saved."). The export then writes `quests/*.quest`. |
| `SAVE FACTION name` | either (`CMDFACTIONS`) | Saves that faction. |
| `SAVE name` | normal | Campaign save into slot `name`. |
| `SAVE name` | builder | Same as plain `SAVE` (there are no campaign saves in builder mode). |
| `SAVE USERS` / `PLAYERS` / `CHARACTERS`, `SAVE USER name`, `SAVE name` of a player, `SAVE GRACES` | builder-style | Inherited CoffeeMud forms that write player records (and race-usage dates). Rarely useful in single-player. |

Notes:

- There is no bare `SAVE AREA`; the forms are `SAVE AREA ROOM`, `SAVE AREA
  ITEMS` and `SAVE AREA MOBS`.
- `HOME`/`ALL` is accepted only directly after `ROOM` (`SAVE ROOM HOME`,
  `SAVE AREA ROOM ALL`).
- Every builder-style `SAVE` in builder mode finishes by exporting the world
  files and printing the report, so `SAVE ROOM` already publishes.
- The report lines: "The world files are written: N changed." or "The world
  files are up to date."; "Not written, as they loaded only in part: ...";
  "Not written, as they were changed outside the game: ...", with the
  `.builder` copies; "Unsaved mobs or items in N rooms, which the world files
  do not have..."; and "SCRIPTCHECK ALL finds N problems in the world's
  scripts." If the export could not run at all it says "The world files were
  not written. If the world did not load whole from them, fix them and start
  again."
- Quitting in builder mode runs the same export without the report.
- Saving needs the matching security flag (`CMDROOMS`, `CMDQUESTS`,
  `CMDFACTIONS`); builder mode's Archon has them all.

## Unsaved rooms, UNDO and HISTORY

### LIST UNSAVED, SAVE CHANGED and BUILDERAUTOSAVE

While a building command runs (CREATE, MODIFY, DESTROY, COPY, TRANSFER,
RESTRING, EVERY, GENERATE, REGENERATE), each room whose mobs or items it
puts in or takes out is marked. `MODIFY`/`RESTRING` of a mob or item marks
your room, MQL updates mark rooms (as "MQL"), `JRUN`'s builder helper marks
rooms (as "JRUN"). A saved room is unmarked.

```text
LIST UNSAVED
```

```text
Rooms with unsaved mobs or items (SAVE CHANGED saves them):
  Emberfall#4: CREATE, MODIFY (+1 mob, +2 items)
  Emberfall#7: DESTROY (-1 mob)
```

The change is the difference between the live room and what the database has.
`SAVE CHANGED` saves exactly these rooms and then exports. `BUILDERAUTOSAVE=1`
in `coffeemud.ini` does that after every building command (default 0).
`QUIT` asks "Quit -- save them first (Y/n), or cancel (c)?". `%U` in a prompt
is the count. `WORLD RELOAD AREA` refuses while rooms of that area are
unsaved (add `FORCE` to drop them).

### UNDO

In builder mode the last 50 building commands (the ones above, except
`REGENERATE`, plus `GMODIFY`) are logged, each with the rooms it changed *as they were before it*: the
room's XML including its mobs and items, taken when the command starts (your
room) and before each room's mobs or items first change. An entry is kept
only if a room really changed. Entries are kept in memory and in
`world/.history/*.undo`, so they survive a restart.

```text
UNDO             put back the last command's rooms
UNDO 3           put back the last 3 commands, newest first
UNDO LIST        list them (20), newest first
UNDO LIST 50
HISTORY          the commands you typed (not the builder log)
```

Output of `UNDO LIST`:

```text
Building commands, newest first (UNDO n puts the last n back):
  1. CREATE ITEM StdItem @Emberfall#4 (11-06 14:02, 1 room)
  2. MODIFY ITEM cup @Emberfall#4 (11-06 13:58, 1 room)
```

Undoing makes each room again from its saved XML, with any characters in it
waiting in a neighbouring room meanwhile, and links exits from other rooms
into it again. The undone entry is forgotten. `UNDO` with nothing logged says
"There is nothing to undo."

Limits: `UNDO` works on rooms (and their contents), up to 50 steps, in builder
mode only. It does not undo file edits, area-level changes, catalog or quest
edits, `WORLD SET`, or arbitrary JavaScript. For files use
`WORLD RESTOREFILE` and git.

Note that `HISTORY` is the ordinary typed-command history of your session;
`UNDO LIST` is the builder's log. (Some older text says HISTORY lists the
undo log. It does not.)

## The WORLD command

`WORLD` (source:
[WorldCmd.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/WorldCmd.java))
is the umbrella for world settings, checks, files and reloads. Subcommands
below; the generated [tool reference](tool-reference.md) has the `AHELP
WORLD` text.

| Syntax | Mode | Purpose |
| --- | --- | --- |
| `WORLD` or `WORLD LIST` (alias `LIST WORLDINI`) | any | Every world-settable key that is set, with its value and where it came from; then the keys not set |
| `WORLD SET KEY=VALUE` | builder, sysop | Change (or add) a line in `world.ini`; blank value removes it; takes effect at next start |
| `WORLD PROBLEMS` | any | Files that loaded only in part and why |
| `WORLD CHECK (AREA [NAME]) (LOG) (ONLY=part,part)` | builder, DEV=1 or `CMDROOMS` | The world lint, errors then warnings |
| `WORLD STATUS` | builder | Files changed outside the game; what `SAVE` would write, delete and leave |
| `WORLD DIFF [FILE]` | builder | A file on disk against what `SAVE` would write |
| `WORLD RELOAD ...` | builder (normal game: see below) | Load files again into the running world; forms in [Reload deliberately](#reload-deliberately) |
| `WORLD BACKUPS ([FILE])` | builder | Files with backups, or one file's backup times |
| `WORLD RESTOREFILE [FILE] ([TIME])` | builder | Put a backup back on disk |
| `WORLD DEV ([AREA] ON/OFF)` | list: any; change: builder | Dev areas |
| `WORLD DIG ON/OFF (TYPE=...) (LINK=...)` | builder | Dig mode |
| `WORLD PATH [DIRECTIONS] (TYPE=...) (NAME="...") (DESC="...") (LINK=...)` | builder | Lay out a chain of rooms |
| `WORLD MAP (RADIUS [N]) (AREA) (IDS) (FLAGS) (HTML=[FILE])` | builder, DEV=1 or `CMDROOMS` | Draw the rooms around you |
| `WORLD TEST ([FILE.play/.test/.js])` | see [testing](13-testing-release.md) | Run playtest scenarios |
| `WORLD MODULES`, `COMPOSITIONS`, `COMBAT`, `ITEMMODS` | builder or DEV=1 | Optional-subsystem tools: [modules](14-optional-modules.md), [composition](15-ability-composition.md), [combat profiles](16-combat-profiles.md), [item modifiers](17-item-modifiers-crafting.md) |

### WORLD STATUS

```text
WORLD STATUS
```

```text
Changed outside the game since it was loaded or last written:
  areas/new-area.cmare (edited)
  quests/hunt.quest (added)
SAVE would write: areas/emberfall.cmare.
SAVE would leave, as they were changed outside the game: areas/new-area.cmare.  What it would write goes beside each, as .builder; WORLD DIFF shows the difference, and SAVE FORCE writes over them.
```

Each changed file is marked `(edited)`, `(added)` or `(deleted)` relative to
what the game last loaded or wrote. The plan is computed by a *dry run* of the
export: nothing is written, deleted or cached. If the world did not load whole
it says "SAVE writes nothing, as the world did not load whole from its files
(WORLD PROBLEMS)." If there is nothing to do: "SAVE would change no world
file." Files that loaded only in part are listed separately.

### WORLD DIFF

`WORLD DIFF castle`, `WORLD DIFF areas/castle.cmare`, `WORLD DIFF quests/hunt.quest`
or `WORLD DIFF world/catalog.cmare`. The argument is a path (with or without
`world/` and extension), a file's base name, or an area's name.

```text
areas/new-area.cmare (changed outside the game, so SAVE leaves it and puts this beside it, as areas/new-area.cmare.builder), - on disk, + as SAVE would write it:
line 38:
- <RDESC>A stone room written by hand.</RDESC>
+ <RDESC>You`re standing in a dull room of stone.</RDESC>
```

`-` lines are on disk; `+` lines are what the next `SAVE` would write. Output
is capped at 200 lines. Other results: "SAVE would leave it as it is; it is as
the game has it", "not on disk; SAVE would write it, N lines", "SAVE would
delete it, all N lines", "SAVE would write it as it is".

### WORLD CHECK

The world lint. Parts (`ONLY=` takes a comma list, case-insensitive):

| Part | Finds |
| --- | --- |
| `EXITS` | Exits to nowhere (error); doors with no way back, exits into dev areas (warnings) |
| `REACH` | Start rooms that do not exist (error); rooms nothing leads to from the start (warning), by area |
| `KEYS` | Locked doors and containers whose key exists nowhere: rooms, carried, shops, catalog (error) |
| `TEXT` | Rooms still with the new room's text, class default titles/descriptions, `TODO` tags from `WORLD DIG`/`PATH`, generic mobs/items with their class's stand-in text |
| `SHOPS` | Shopkeepers with nothing to sell |
| `DATA` | Everything in `WORLD PROBLEMS` |
| `SCRIPTS` | `SCRIPTCHECK` results |
| `PROPS` | `PROPCHECK` results |
| `JS` | World JavaScript classes that do not compile or extend nothing |
| `VOCAB`, `LORE`, `TEMPLATES`, `ROUTES`, `IDS` | Languages/glossary/lists/help, lore entries/files, presentation text/options, travel and route references, stable author identities |
| `MODULES`, `COMPOSITIONS`, `COMBAT`, `ITEMMODS` | The optional subsystems |

`WORLD CHECK AREA` alone checks your area; `AREA name` another; `LOG` also
writes the report to the log. From a shell:

```sh
CoffeeMud/sud.sh CHECK=1        # exit status 1 if any error
```

### WORLD PROBLEMS

An object of an unknown class (a misspelled ability name, say) is skipped
when a file loads, and that file is then recorded as loaded *only in part*.
Builder mode will not write such a file (so the skipped object is not
silently dropped) until you fix it and load the world from it again, or use
`SAVE FORCE`, which writes the world as it is now (without the skipped
objects). Problems are kept in `worldproblems.txt` in the database folder and
shown at builder login. Note that if a file cannot be parsed *at all*, the
world is marked not loaded whole and builder mode writes no world files at
all until it is fixed.

### WORLD DIG, PATH and MAP

```text
WORLD DIG ON TYPE=ForestPath LINK=ONE-WAY
WORLD DIG OFF
WORLD PATH e,e,n,ne TYPE=ForestPath NAME="A forest trail" DESC="Roots crowd the track."
WORLD MAP
WORLD MAP AREA IDS FLAGS
WORLD MAP RADIUS 8
WORLD MAP AREA HTML=emberfall
```

- **DIG ON** makes a room, like `CREATE ROOM`, wherever you walk where there
  is no exit, and moves you into it, linked back unless `LINK=ONE-WAY`. The
  type is the room's own kind, or `TYPE=` a locale class or a builder
  template. **DIG OFF** stops.
- **PATH** lays out one room per direction (comma-separated), following
  exits that already exist (reporting "made N rooms and followed M exits")
  and leaves you where you are. `NAME=` and `DESC=` apply to new rooms.
- Rooms made by DIG/PATH are saved and tagged `TODO`, which `WORLD CHECK`
  (TEXT) lists until `EDIT DESC` gives the room its own text.
- **MAP** draws the rooms around you (5 steps, or `RADIUS n`) or the whole
  area (`AREA`), north up, laid out by walking exits from your room (`*`).
  `IDS` numbers rooms and lists them; `FLAGS` marks `> < v ^` an exit that
  does not lead back, `+` a locked door, `@` an exit to another area, `s` a
  room with scripts, and lists rooms of the area nothing leads to from here.
  `HTML=FILE` writes `world/maps/FILE.html` with each room and its exits as
  links.

### WORLD SET

```text
WORLD SET START=Emberfall#0
WORLD SET HOURSINDAY=12
WORLD SET START=              (blank removes the line)
```

Only keys in the world-settings allowlist are accepted ("`X` is not a world
setting: those are listed by WORLD, and the engine's own settings stay in
coffeemud.ini"). The comment lines and order of an existing `world.ini` are
preserved.

### DEVWATCH

```text
DEVWATCH ON 5       look every 5 seconds (default 3, minimum 1)
DEVWATCH OFF
DEVWATCH            say whether it is on
```

A background thread checks the world files and the scripts under `resources/`
and `world/resources/` and queues, on the game thread, `WORLD RELOAD CHANGED`
when a world file changed outside the game (not for deleted files) and `WORLD
RELOAD SCRIPT <path>` for a changed `.script` or `.js` file. Each reload is
noted in `DEVLOG`. Builder mode or `CMDROOMS` only.

Caveat: a changed file that `WORLD RELOAD CHANGED` cannot load live (for
example anything under `classes/` or `custom/`) stays listed as changed, so
`DEVWATCH` re-queues the reload on every pass and repeats the message. Turn
it off, restart, or restore the file.

## Protect and publish edits

Builder mode never silently overwrites a world file you changed with another
program. The mechanism is the same for every file the export writes and every
tracked asset.

### How the game knows

When the game loads the world, and each time the export writes a file, it
records a checksum of that file (its length and CRC32). A file is "changed
outside the game" if its bytes on disk no longer match: **edited** (different
bytes), **added** (a tracked file it did not know) or **deleted**.
`WORLD STATUS` lists them, and the builder-login summary counts them.

### What SAVE does with such a file

This applies to the files the export writes (areas, dev areas, custom
objects, catalog, quests, templates, clans, space, journals, vfs). The
hand-authored assets (scripts, vocab, lore, text and the rest) are never
written or deleted by `SAVE`; they only show in `WORLD STATUS` and are picked
up by reloads.

| Situation | `SAVE` leaves | And writes | `SAVE FORCE` |
| --- | --- | --- | --- |
| File **edited** outside the game | your version on disk | the game's version beside it, `FILE.builder` | writes the game's version over yours (backed up first) |
| File **added** outside the game, game has no such thing | your file (does not delete it) | nothing | deletes it (backed up) |
| File **deleted** outside the game, game still has the thing | the file missing | the game's version as `FILE.builder` | writes the file again |
| File loaded only in part (`WORLD PROBLEMS`) | the file | nothing | writes the world as it is now (without the skipped objects) |

After a `SAVE` that left files, the report names them, and the cache's
`world.sum` is deleted so that the **next start builds from the files** (your
edits win; the game's copies of those files are in the `.builder` files and
whatever the live game had that the files lack is not in the next game). When
the file is back in step (you reloaded it, or `SAVE FORCE` wrote it) the
`.builder` copy is removed by the next export.

### Reconciling

1. `WORLD STATUS` to see what is different.
2. `WORLD DIFF name` to see the lines: `-` is on disk, `+` is what `SAVE` would
   write.
3. Choose:
   - **Keep your disk edit:** `WORLD RELOAD AREA name` (or `WORLD RELOAD
     CHANGED`). The file is loaded into the game and recorded as known. The
     next `SAVE` finds nothing to protect.
   - **Keep the game:** `SAVE FORCE`.
   - **Merge:** open `FILE.builder` and your file in a diff tool, produce the
     file you want, then `WORLD RELOAD`.
4. Delete leftover `*.builder` files if any remain (they are ignored by the
   engine and by the checksum).

`SAVE FORCE` also accepts a partially loaded world (it writes only what
loaded). Use it only after reading `WORLD PROBLEMS`.

### Backups

Before the export overwrites or deletes any file, it copies the old file to
`world/.backup/`, keeping the last `BACKUPS=` copies of each file (default
5, `0` for none; set in `coffeemud.ini`).

```text
WORLD BACKUPS                    all files that have backups: count and newest time
WORLD BACKUPS castle             one file's backup times, oldest first
WORLD RESTOREFILE castle         put the newest back
WORLD RESTOREFILE castle 20261106-1402   put back the backup whose time starts with that
```

The time is `YYYYMMDD-HHMMSS-mmm`; any prefix that matches works
(`20261106`, `20261106-1402`). Backup file names are the path with `/`
replaced by `~` plus the time, e.g. `areas~castle.cmare.20261106-140210-123`.
A restored file is *changed outside the game* by definition: `WORLD RELOAD`
loads it into the game, and `SAVE` leaves it as it is (`SAVE FORCE` would
write the game's version over it).

Backups cover files the export wrote over or removed. They are not a general
undo: they do not cover your own editor saves, `WORLD SET`, files in
`.backup` itself, or changes that were never exported. Git is the real
history of the world; commit before bulk edits.

## Editing world files by hand

Hand editing is a first-class workflow, especially for descriptions and
scripts, bulk renames, and merging through git. These are the rules.

**Know what happens to an edit.**

| When you edit | What happens |
| --- | --- |
| Game stopped | At the next start the checksum differs, so the world is rebuilt from the files ("The world files have changed: rebuilding the world from them..."). In builder mode the cache's content is cleared and rebuilt (your characters stay). A normal game rebuilds its working database and refreshes the cache. |
| Builder mode running | The game keeps its live copy. `WORLD STATUS` shows the file as changed; `SAVE` will not overwrite it. Load it with `WORLD RELOAD`, or let `DEVWATCH ON` do it. |
| Normal game running | The running game does not notice. A later start rebuilds. Campaign saves made earlier compare their recorded world version on restore (see [saves](31-saves-and-party.md)). |

**Make safe edits.**

- Edit with the game stopped when you can: there is nothing to reconcile.
- Keep files well-formed XML. A file that cannot be parsed makes the world
  "not loaded whole": the console prints "World file NAME could not be read:
  ...", the area is missing from that game, and builder mode will not write
  *any* world files until you fix it and start again. Run `sud.sh CHECK=1`
  after editing to catch this and other errors before playing.
- Either layout is fine. The readable `xml="1"` layout and the older escaped
  one-line layout load identically. When you change text inside a tag, keep
  the tag's contents exactly as the engine needs them (escape `<`, `>` and `&`
  in plain text fields as XML does).
- Do not change `ROOMID`s, `RAREA` or exit destinations casually: exits, saved
  characters, quests and scripts refer to rooms by ID. Renaming an area
  (`ANAME`) means changing every `ROOMID`/`RAREA`/`XDOOR` that mentions it;
  use the in-game editor for that.
- Area file names are derived from the area name. After a `SAVE`, a file
  whose name differs from its area's canonical name (`foo-hand-made.cmare`
  holding area "Foo") is rewritten as `foo.cmare` and the odd file is removed
  (with a backup). Name hand-made files canonically from the start.
- `SAVE` rewrites a hand-formatted file into the engine's canonical layout
  if its text differs (backed up first). That is expected; commit the
  canonical form.
- Text assets under `vocab/`, `lore/`, `text/`, `kits/`, `encounters/` and
  the single files are safe to edit live; follow with `WORLD RELOAD ASSETS`.
- Scripts and JavaScript: edit freely, then `WORLD RELOAD SCRIPT file`,
  `JS RELOAD`, or `DEVWATCH ON`.

**Add an area by hand.** Put `areas/castle.cmare` in place. With the game
stopped, start it (it rebuilds). In builder mode, `WORLD RELOAD CHANGED`
reports "Castle is loaded from areas/castle.cmare." Link it into the existing
map from the game: exits from other areas into it do not exist until you make
them.

**Delete an area.** With the game stopped, delete the file and start. In
builder mode, remove the area with the editor and `SAVE` (the file is backed up
and removed). Deleting a file while the game runs leaves it listed as
"deleted"; `WORLD RELOAD CHANGED` answers "was deleted: start the game again
to leave it out, or SAVE FORCE to write it back."

**Merge with git.** Because the export lays out one tag per line, ordinary
line merges work. After a pull, start the game (or `WORLD RELOAD CHANGED` in
builder mode). `.backup/` and `.history/` carry their own `.gitignore` and
should not be committed; the cache under `resources/fakedb/` is git-ignored
by the repository.

**Compare worlds** with `tools/worlddiff.py` (above) or `WORLD DIFF`.

## Reload deliberately

Reload is not a universal restart. Each tool has a boundary, and some changes
need a restart.

| Tool | Boundary |
| --- | --- |
| `WORLD RELOAD [AREA] name (FORCE)` | Rebuild one area from its file. The name may be the area's name or its file name (`AREA` alone means your area). Players and followers inside wait in a room outside the area and are brought back to the same room IDs; exits from other areas into it are linked again (those to rooms it no longer has are counted as lost). Calendar settings are reapplied. Unsaved mobs and items in its rooms are dropped: refused ("N rooms of X have unsaved mobs or items, which WORLD RELOAD drops: SAVE CHANGED first, or add FORCE.") unless `FORCE`. |
| `WORLD RELOAD SCRIPT [file/ALL] (RESET)` | Forget parsed scripts, then re-read every script on an area, room, mob or item (including carried items) that names that file in its script text (or all). `RESET` also lets one-time triggers run again. The file name is as `LOAD=` names it (`progs/innkeeper.script`). |
| `WORLD RELOAD QUEST name` / `QUESTS` | Stop the quest, re-read its script from `quests/*.quest` and the files it loads, restart it at the step it was on with its variables kept. `QUESTS` does each quest that has a file. |
| `WORLD RELOAD CATALOG` | Re-read `catalog.cmare`: each entry in the file replaces the same-named catalog entry (or is added); entries not in the file are kept. |
| `WORLD RELOAD ASSETS` | Clear vocabulary, dictionary, translation, character-creation steps, perks, combinations, encounter tables, item-set definitions, help and socials caches, and accept the changed reloadable assets. Does not replace Java binaries. |
| `WORLD RELOAD CHANGED` | Take every file `WORLD STATUS` lists as changed and dispatch it to its loader: `resources/**.script` and `.js` via script reload, reloadable assets via `ASSETS`, area files via area reload, quest files via quest reload, `catalog.cmare` via catalog reload. Anything else answers "is not loaded again while the game runs: start the game again for it." and stays listed as changed. |
| `JS RELOAD [file/ALL]` | Replace compatible JavaScript classes and scopes using declared state contracts ([JavaScript](05-javascript.md)). |
| `WORLD MODULES/COMPOSITIONS/COMBAT/ITEMMODS RELOAD` | The optional subsystems, each with its own validation. |
| `DEVWATCH ON [seconds]` | Poll world files and resource scripts and run the matching reload. |
| `JS WATCH ON [seconds]` | Watch JavaScript files in builder mode. |

**In a normal game** (`DEV=1`, or an Archon with `CMDROOMS`), `WORLD RELOAD`
takes no arguments that matter: it saves the game as the slot `world-reload`
and restores it, which rebuilds the world from its files and puts the
campaign back on top. It refuses if the game cannot be saved.

What to do after editing a file:

| You edited | Do |
| --- | --- |
| `areas/*.cmare`, `dev/*.cmare` | `WORLD RELOAD AREA name` (save unsaved first) |
| `catalog.cmare` | `WORLD RELOAD CATALOG` |
| `quests/*.quest` | `WORLD RELOAD QUEST name` |
| a script under `resources/` or `world/resources/` | `WORLD RELOAD SCRIPT file` (or `DEVWATCH`) |
| `vocab/ lore/ text/ kits/ encounters/`, `score.txt`, `creation.ini`, `origins.ini`, `perks.ini`, `combinations.txt`, `itemsets.txt` | `WORLD RELOAD ASSETS` |
| `scripts/*.script` (the WORLD host), `custom/**`, `templates.cmare`, `clans.cmare`, `space.cmare`, `journals/`, `vfs/`, `routes/` | Restart (or the specific reload of that subsystem if it has one) |
| `js/**` | `JS RELOAD` |
| `resources/**` data (recipes, laws, factions...), `ranks.ini`, `crops.txt`, `materials.txt`, `resources.txt`, `classes/**` | The subsystem's own reload where it has one; otherwise restart. They remain listed as changed until accepted |
| `modules/ compositions/ combat/ itemmods/ affixes.xml` | The matching `WORLD ... RELOAD` ([modules](14-optional-modules.md), [composition](15-ability-composition.md), [combat profiles](16-combat-profiles.md), [item modifiers](17-item-modifiers-crafting.md)) |
| `world.ini` | Restart (`WORLD SET` says so) |

`WORLD SET KEY=value` writes a rule for the **next startup** only.

## The catalog

The catalog is a world-wide table of reusable mob and item designs, keyed by
**name**. It serves four purposes:

1. A template library: `CREATE <cataloged name>` makes a copy.
2. Global editing: changing the catalog entry changes every placed copy that
   is still tied to it.
3. Spawn and drop rules: a catalog item can be given to mobs as equipment, drop
   from corpses, appear in rooms or containers, and be unique; a catalog mob
   can spawn into rooms.
4. A name that other systems refer to: `CATALOG:name` in recipes and quest
   `LOAD`s, `catalog:name` in `RandomMonsters` and `MPMLOAD`, kits, spawners,
   encounter tables and `UPGRADE` rules.

Source: [Catalog.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Catalog.java),
[CMCatalog.java](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CMCatalog.java).
Needs the `CATALOG` security flag (builder mode's Archon has it).
In a normal game the command works on that game's working copy and warns that
the world file is not changed.

### CATALOG commands

| Syntax | Effect |
| --- | --- |
| `CATALOG [mob or item here]` | Catalog the mob or item in your room under the current category. If an entry of that name exists and differs, lists the differing properties and asks "Please confirm (y/N)?" before changing the catalog entry. If it already matches but the category differs, asks whether to move it. A Java class mismatch with the existing entry is refused. |
| `CATALOG SELF` / `ME` | Catalog yourself (a mob). |
| `CATALOG LIST (MOBS/ITEMS) (MASK)` | List entries: name, spawn mode, mask. `MOBS` or `ITEMS` limits; the mask matches names; honours your current category. |
| `CATALOG LIST (MOBS/ITEMS) CATEGORIES` (also `CATS`, `CATEGORY`, `CATAGORY`) | List category names. |
| `CATALOG CATEGORY [name]` (also `CATAGORY`) | Set the category new entries go into. Asks to create a new one. `CATALOG CATEGORY NONE` returns to the global (empty) category. `CATALOG CATEGORY LIST` lists them. The reserved names `GLOBAL`, `NONE` and `UNCATEGORIZED` cannot be created. |
| `CATALOG EDIT [item or mob name]` | Prompt for spawn mode and rules (below). |
| `CATALOG DELETE [name]` | Remove an entry. Warns if copies are in use. Placed copies are only detached; none is destroyed. `everydamnmob`, `everydamnitem` and `everydamnthing` delete in bulk after one confirmation for the whole set (it says how many entries and how many are in use). |
| `CATALOG (ROOM/AREA/WORLD) (MOBS/ITEMS)` | Catalog everything in the room, area or world (rooms are reset and saved). One "are you sure" prompt for a room, a second for an area or the world. Test on a small area first. |
| `CATALOG SCAN (ROOM/AREA/WORLD) (MOBS/ITEMS)` | Report which live objects are cataloged, and "falsely cataloged" ones. |
| `CATALOG DBSCAN ...` | The same, against the saved rooms in the database. |
| `CATALOG OVERLOOK ...` / `DBOVERLOOK ...` | List objects that should be tied to the catalog (a same-named entry exists) or are not cataloged. |
| `CATALOG CLEAN (ROOM/AREA/WORLD) (MOBS/ITEMS)` | For objects flagged cataloged whose catalog entry no longer exists, remove the flag and save the room. |

The catalog is keyed by name: once a name is set, do not change it, or the
copies no longer match.

### Spawn modes and rules (CATALOG EDIT)

Each entry carries "catalog data" with these fields:

| Field | Meaning |
| --- | --- |
| spawn (`NONE`) | Nothing. |
| spawn `LIVE` (item) | Given to mobs as equipment when they are brought to life, subject to the mob mask and rate. |
| spawn `DROP` (item; the edit menu's "D") | Appears on a corpse at death, subject to mask and rate. |
| spawn `ROOM` | Appears in a random matching room while below the cap. Mobs may spawn this way too (the catalog thread brings copies to life, unsaved). |
| spawn `CONTAINER` (item) | Appears in a matching container when first opened, on a timer, or both. |
| rate | Chance as a percentage (`10%`). |
| mask | A zapper mask choosing the rooms, mobs or containers ([masks](24-behaviors-and-masks.md)). |
| cap | Maximum number of copies in the world (0 for none). |
| unique (items) | `TIME` (default: one at a time), `ONCE` (once ever), `WHILE` (once for a number of game days after one is lost). |
| days | For `WHILE`, game days before a lost one may return. |
| when | For containers: `OPEN`, `TIMER` or `BOTH`. |
| rules | Extra spawn rules that share the cap and uniqueness: `KIND RATE [WHEN] [MASK]`, such as `CONTAINER 5% OPEN -SUBNAMES +*chest*` or `DROP 100% -NAMES +chieftain`. `DELETE` removes one at the prompt. |

`AHELP UNIQUES` explains uniqueness. Every copy in the game counts, including
copies from a save. Keep rare items unique by catalog rules rather than by
leaving the original somewhere.

### How entries are stored

- **Live:** each entry is a mob or item object in the catalog library, with a
  `CataData` and a count of placed copies. Placed copies carry the
  `IS_CATALOGED` disposition bit so the engine knows they are tied to an
  entry.
- **Database:** entries are rows of the item and mob tables under the special
  room IDs `CATALOG_ITEMS` and `CATALOG_MOBS`, with the catalog data XML beside
  each entry.
- **File:** `world/catalog.cmare`, written on `SAVE`: a `<CATALOG>` of
  `<CATMOB>`/`<CATITEM>` entries, each with the object, its `CATEGORY` and its
  `CATADATA` (the `CATALOGDATA` element above). See the example under
  [catalog.cmare](#catalogcmare).
- **Import:** at a rebuild, the file is imported after the areas and before the
  quests. `WORLD RELOAD CATALOG` re-imports it into a running catalog, keeping
  the database to one row per entry.

Editing `catalog.cmare` by hand is supported (the `CATALOGDATA` block in
particular). Keep each entry's name unique within mobs and within items, and
use real class IDs.

Catalog entries are not a substitute for per-instance state or authored
source IDs: a placed copy can diverge from its catalog entry. See
[Stable identities and migrations](#stable-identities-and-migrations).

## The content database cache

The engine still uses a database layer (CoffeeMud's `DBInterface`) but
CoffeeSUD runs it on a plain-file "fake database" (`DBSERVICE=jdbc:fakedb:folder`,
default `resources/fakedb`). The world files are the content; the database is
a cache of what is built from them, plus the builder's characters.

**What is in the cache folder:**

| File | Content |
| --- | --- |
| `fakedb.schema` | The schema (kept in git; the rest of the folder is git-ignored) |
| `fakedb.data.CMAREA`, `CMROOM`, `CMROEX`, `CMROCH`, `CMROIT`, `CMGRAC`, `CMCCAC`, `CMGAAC`, `CMCDAC`, `CMQUESTS` | The **content tables**: areas, rooms, exits, room mobs (characters), room items (the catalog is in here too, under `CATALOG_ITEMS`/`CATALOG_MOBS`), custom races, classes, abilities, commands/behaviors, quests |
| `fakedb.data.CM...` (others) | Everything else: players/characters (builder mode), journals, clans, VFS, player data... |
| `world.sum` | The checksum of the world files this cache was built from, e.g. `3-17-1a2b3c4d` |
| `dev.sum` | The checksum of the `dev/` files its dev areas match (builder mode) |
| `worldproblems.txt` | Tab-separated `path<TAB>problem` lines for files that loaded only in part |

**The checksum** is a CRC32 over the sorted list of each tracked file's path
and its own length-and-CRC32. It covers `areas/`, `quests/`, `custom/`
(and legacy `custom.cmare`), `js/`, `scripts/`, `routes/`, `catalog.cmare`,
`templates.cmare`, `clans.cmare`, `space.cmare`, `journals/`, `vfs/`, and the
hand-authored asset folders and files. It leaves out `world.ini`, `dev/` (own
checksum), `modules/ compositions/ combat/ itemmods/ affixes.xml` (each
subsystem versions itself), `macros/ tests/ maps/ laws/ namegen/`,
`.backup`, `.history` and `*.builder` copies. The checksum is also the
*world version* that a campaign save records.

**Lifecycle:**

1. **Start.** Compute the checksum; compare with `world.sum`.
2. **Same:** normal game copies the content tables into a fresh working
   database (`saves/.play-<time>/`); builder mode plays on the cache.
3. **Different:** empty content tables, and import in order: custom objects
   (right after the database connects, so the engine loads them with its
   classes), then areas (`areas/*.cmare` in name order), `catalog.cmare`,
   the unique item registry, and `quests/*.quest`. Templates, clans, journals
   and vfs are imported for a new game and for a builder rebuild (builder
   rebuilds replace the database's). Dev areas load after, in builder mode and
   DEV games. If every file loaded, `world.sum` is written; a normal game also
   copies the new tables into the cache so the next start is fast.
4. **Problems.** If a file imports with errors that skip objects, the file is
   recorded in `worldproblems.txt`; if a file cannot be read at all the world
   is not "whole": no `world.sum` is written and builder mode writes nothing.
5. **Builder `SAVE`/quit.** The live world is written to the files; changed
   areas are also cached (`cacheArea`), and `world.sum` is rewritten for the
   new files (unless files were left as changed outside the game, in which case
   `world.sum` is deleted so the next start builds from files).

You can always delete the cache's content tables and `world.sum` (or the whole
folder except `fakedb.schema`); the next start rebuilds from the files. In
builder mode this loses nothing that is in the files, but loses anything the
cache held that was never exported. Never hand-edit `fakedb.data.*`.

## World files versus campaign saves

| | World files | Campaign save |
| --- | --- | --- |
| Folder | `world/` | `saves/<slot>/` |
| Written by | Builder-mode `SAVE`/quit, or you | `SAVE`, autosave, quit (normal game) |
| Holds | The definition of a new game | A playthrough: characters, party, clock, live changes, scripts' saved state, quest state |
| Shared by | Every game started from it | That playthrough |
| Versioned by | git | Keep slots; a save records the world version |

How they interact:

- A save records the world checksum and a checksum of each area file. When a
  save is restored against a *different* world, the game tells you at login
  what changed ("The world has changed since this game was saved", "Changed
  area file: ...", "New area file: ...", "Area file gone: ...").
- Which parts of the world a restore takes from the save and which from the
  files is decided per area. `RESTORE name FRESH=CHANGED`, `FRESH=ALL` or
  `FRESH=area,area` (also accepted as the launch setting `FRESH=` with
  `RESTORE=<slot>`) makes the restore take those areas from the files as they
  are now rather than from the save. See [saves and party](31-saves-and-party.md)
  for the rules and the full list of what a save keeps.
- External assets (scripts, dictionaries, lore, text) stay *live*: saves do not
  contain private copies of them. Version and distribute the matching world
  when a campaign must keep its rules and wording.
- Edits an Archon makes in a normal game stay in that game's working copy and
  its saves. They do not reach the world files; make them in builder mode to
  publish.
- Reloading in a normal game (`WORLD RELOAD`) saves and restores through the
  `world-reload` slot: world definitions are re-read from files, and the
  campaign state is put back on top.

For what each system persists, see [state and persistence](state-and-persistence.md);
for release testing across a process boundary, [testing and release](13-testing-release.md).

## Worked examples

### Example 1: add a trail and a guard, publish, check

Start builder mode, walk to the room where the trail should begin.

```text
WORLD STATUS
WORLD PATH e,e,n TYPE=StdRoom NAME="A forest trail" DESC="Roots crowd the track."
```

`WORLD PATH` makes three rooms (east, east, north from where you stand),
linked both ways, saved, and tagged `TODO`. Walk to the last one and give it
a real description and a guard:

```text
e
e
n
EDIT DESC HERE
CREATE MOB GenMob
LIST UNSAVED
```

`LIST UNSAVED` shows this room has an unsaved mob. Use `MODIFY MOB` to name
and equip it ([mobs](21-mobs.md)), then keep it:

```text
SAVE ROOM
```

The report ends with "The world files are written: 1 changed." (the area
file). Check, then commit from a shell:

```text
WORLD CHECK
```

```sh
CoffeeMud/sud.sh CHECK=1          # optional: from a shell, exit 1 on errors
git add CoffeeMud/world && git commit -m "Add the forest trail"
```

If `WORLD CHECK` still lists the three rooms as "tagged TODO", `EDIT DESC`
has not been run in them.

### Example 2: hand-edit a description while the builder runs

You have the game open in builder mode and fix a typo in the file from an
editor.

```sh
sed -i 's/dull room of stone/dull room of grey stone/' CoffeeMud/world/areas/new-area.cmare
```

In the game:

```text
WORLD STATUS
```

```text
Changed outside the game since it was loaded or last written:
  areas/new-area.cmare (edited)
SAVE would leave, as they were changed outside the game: areas/new-area.cmare.  ...
```

```text
WORLD DIFF new-area
```

shows the `-`/`+` lines for the changed `RDESC`. You want your edit, so load
it:

```text
WORLD RELOAD new-area
```

The area is rebuilt from the file (anyone in it waits outside and returns). If
the area had unsaved mobs or items you would first `SAVE CHANGED`, or add
`FORCE` to drop them. `WORLD STATUS` now reports nothing changed, and the next
`SAVE` is free to rewrite the file in canonical layout.

With `DEVWATCH ON 3` the reload happens by itself a few seconds after you
save the file in your editor.

### Example 3: recover from a mistake

A bulk command (`EVERY`, `GMODIFY`, or a bad `DESTROY`) damaged some rooms. In
decreasing order of preference:

1. **Not saved to files yet:** `UNDO LIST` then `UNDO` (or `UNDO 3`) puts the
   rooms back as they were before those commands.
2. **Already exported:** git has the previous version of the file. If you
   have not committed, the export kept a copy:

   ```text
   WORLD BACKUPS new-area
   WORLD RESTOREFILE new-area 20261106-1402
   WORLD RELOAD new-area
   ```

   `RESTOREFILE` puts the file back on disk (it is now "changed outside the
   game"), `RELOAD` loads it. Without a time it restores the newest backup.
3. **Whole world:** `git checkout -- CoffeeMud/world` with the game stopped,
   then start; the checksum changes and the world is rebuilt.

### Example 4: a catalog mob that spawns, and a pre-commit check

Put a goblin in the catalog, make it appear in forest rooms, and fail commits
that break the world.

Create the mob in a room and shape it with `CREATE MOB GenMob` and `MODIFY
MOB` ([mobs](21-mobs.md)); name it "a trail goblin". Then:

```text
CATALOG CATEGORY Forest
CATALOG a trail goblin
CATALOG EDIT a trail goblin
```

`CATALOG CATEGORY Forest` asks "Create new category 'FOREST' (y/N)?" the first
time (categories are stored in upper case). At the `EDIT` prompts choose
`R)oom`, a cap (for example 4), a rate and a room mask (see
[masks](24-behaviors-and-masks.md)). Then:

```text
CATALOG LIST MOBS goblin
SAVE
```

`SAVE` writes `catalog.cmare` with a `<CATMOB>` for the goblin and its
`<CATALOGDATA>`. Any room can now `CREATE a trail goblin`.

A git hook (`.git/hooks/pre-commit`):

```sh
#!/bin/sh
CoffeeMud/sud.sh CHECK=1 || { echo "world check failed"; exit 1; }
```

## Shared authoring grammars

Many systems reuse the same small languages. Know which one a field wants.

Many systems reuse zapper masks. `-RACE +Human +Elf` requires one of those
races; `-TATTOO +ORIGIN_COAST` requires that tattoo. Quote multiword
arguments, for example `-AREA "+New Area"`. Use `AHELP MASKS` for the running
engine's full key list, and [behaviors and masks](24-behaviors-and-masks.md).
Some positive keys, particularly +IF and +JS, are permissive overrides; use
their negative forms for required script conditions. Test both an allowed and
a refused character instead of judging a mask by its wording. The consumer
determines whether it tests a viewer, owner, arriving player, room or item.
The same mask can mean something different on a travel gate and a shop recipe
if their subjects differ.

Formulas also have context. `@x1` might be caster level in one field and the
crafter's level difference in another. Read that field's help. Engine math can
evaluate left to right; bracket arithmetic deliberately rather than relying on
JavaScript's operator precedence. A formula string, a dice expression, a
percentage and a JavaScript expression are not interchangeable formats.

Timing units are explicit: ticks for a switch reset or effect, game hours for
a journey or crop rule, minutes for TravelTime, and seconds for a JS runtime
limit. A calendar hour need not equal a fixed number of real seconds under all
time modes. Avoid copying a number between fields without checking the unit.

Message templates such as `<S-NAME>` and `<T-NAMESELF>` refer to the event's
source and target; verb suffixes such as `(s)` select the appropriate view.
They need an actual message context. Prompt `%` codes, MOBPROG `$` codes,
VARIES tags, Java properties escapes and XML escapes are separate layers. The
[cookbook](cookbook.md) labels hosts and file formats to keep those layers
clear. In world files the XML layer is the outermost one: write `&lt;` and
`&gt;` for literal angle brackets in plain text fields, and leave the markup
inside readable `xml="1"` elements alone.

## Stable identities and migrations

Use separate concepts for a definition, a physical copy and a story fact:

- A catalog/class ID identifies a reusable definition.
- A mob or item's persistent instance ID identifies that particular copy.
  Moving or renaming it retains identity; cloning creates a new one.
- A source ID identifies a clue, first-entry event, immersion source or
  waypoint across content revisions. Preserve it when changing prose or moving
  the source.
- A tattoo or script/store key identifies a story fact. Pick a documented
  scope: character, profile, instance, quest or world.

Presentation and travel properties supporting source IDs accept `ID=`,
`ALIASES=` and `LEGACYKEYS=`. Independent sources need different IDs. Copies
with the same authored ID deliberately share progress. Without an explicit ID,
a saved `Prop_AuthorIdentity` is assigned on first stateful use; edits before
migration may need a complete legacy key. See
[identity and recovery details](../authoring-reliability.md). Room IDs
(`Area#n`) are identities too: renumbering or renaming rooms in a world file
breaks the references that point at them (exits, start rooms, quests, saved
characters and campaign saves).

Campaign saves record accepted world file versions; external assets stay live.
They do not contain private copies of dictionaries, lore and script files.
Version and distribute the matching world when a campaign must keep its rules
and wording. [Release and save testing](13-testing-release.md) covers recovery.

## Troubleshooting and gotchas

| Symptom | Cause and fix |
| --- | --- |
| My mob or item is gone after a restart | Mobs and items need `SAVE ROOM` (or `SAVE CHANGED`); `LIST UNSAVED` shows what is outstanding. |
| `SAVE ROOM` reported "skipped: a guard (home Town#4)" | Builder-mode `SAVE ROOM` saves each mob into its *own start room*. Go to the mob's home room and save there, or use `SAVE ROOM ALL` to save everything standing here. |
| "Not written, as they were changed outside the game" | A file was edited, added or deleted by another program. `WORLD STATUS`, `WORLD DIFF`, then `WORLD RELOAD` (keep disk) or `SAVE FORCE` (keep game). A `.builder` copy sits beside the file. |
| "The world files were not written. If the world did not load whole from them, fix them and start again." | A world file could not be parsed. Read the console error from the last start ("World file X could not be read"), fix it, start again. |
| "Not written, as they loaded only in part" | An object of an unknown class was skipped on load. `WORLD PROBLEMS` names it. Fix the class name; `SAVE FORCE` would drop the object for good. |
| World rebuilds at every start | Something changes a tracked file between runs, or the cache folder is not writable, or `DBSERVICE` is not a `jdbc:fakedb:` value (then saves and world files are off: "The world files and saved games need the fake database, so they are off."). |
| Lots of messages from `DEVWATCH` | A changed file `WORLD RELOAD CHANGED` cannot load live (for example under `classes/` or `custom/`) stays "changed". Turn `DEVWATCH OFF` and restart. |
| `WORLD RELOAD` refused: "N rooms of X have unsaved mobs or items" | `SAVE CHANGED` first, or add `FORCE` to drop them. |
| `UNDO` says nothing to undo | The command did not change a room (it logs only when a room's XML differs), it was not one of the logged commands, or you are not in builder mode. `HISTORY` is your typed commands, not the undo log; use `UNDO LIST`. |
| `WORLD STATUS`/`DIFF`/`BACKUPS` say "is for builder mode" | They only work with `BUILDER=1`. |
| Backups are missing | `BACKUPS=0`, or the file was never overwritten by a `SAVE` (backups are made only when the export writes over or deletes a file). |
| My hand-made area file changed name after `SAVE` | Area files are named from the area name; the odd name is removed (with a backup). |
| A catalog copy did not follow my catalog edit | The names differ (catalog is keyed by name), or the copy was detached by `CATALOG DELETE`. `CATALOG SCAN AREA`, `OVERLOOK` and `CLEAN` find strays. |
| Changes I made as an Archon in a normal game are gone from new games | By design: a normal game plays on a working copy. Repeat them in builder mode. |
| `WORLD SET` says a key is not a world setting | Only the allowlist ([world settings](world-settings.md)) can live in `world.ini`; engine settings stay in `coffeemud.ini`. |
| I deleted `resources/fakedb` | Keep `fakedb.schema` (it is in git); delete only the `fakedb.data.*` files and `world.sum` to force a rebuild. Builder-mode characters live there too. |

## See also

- [First world](01-first-world.md): the beginner tutorial that uses this workflow.
- [Installing and running](18-installing-and-running.md): launch flags, modes, folder layout.
- [Configuration and administration](19-configuration-and-administration.md): `coffeemud.ini`, `world.ini`, security.
- [World settings reference](world-settings.md) (generated) and [tool reference](tool-reference.md) (generated).
- [Areas, rooms and exits](20-areas-rooms-exits.md), [mobs](21-mobs.md), [items](22-items.md): what to build.
- [Saves and party](31-saves-and-party.md): campaign saves, slots, restore rules.
- [State and persistence](state-and-persistence.md): what each system saves.
- [Testing and release](13-testing-release.md): playtest files, `CHECK=1`, packaging a world.
- [Engine internals](33-engine-internals.md): the world-file, checksum and export code in depth.
- [Cookbook](cookbook.md) and [examples](examples/README.md): runnable worlds.
