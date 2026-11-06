# Engine programming

This chapter is for Java developers who change or extend the CoffeeSUD engine:
people who add a command, a spell, a property, a behavior, an item, a mob, a
locale, a race or a class, or who fix something in the core. It explains how the
source tree is laid out and built, how classes are found and registered, what the
object model and the message system look like, how ticks and the single game
thread work, and how to write, save, test and debug a new class of each kind.
World builders and scripters do not need it; they should start with the
[first world tutorial](01-first-world.md) and the [MOBPROG guide](04-mobprog.md).

CoffeeSUD is a single-player fork of the CoffeeMud 5.11 engine. Most of the
object model is unchanged from CoffeeMud, so the old
[Programming guide](../../CoffeeMud/guides/Programming.html) still helps as a
second opinion, but parts of it are out of date (it names `CMClass.newMsg`,
`CMClass.threads()` and `Tickable.MOB_TICK`, none of which exist; they are
`CMClass.getMsg`, `CMLib.threads()` and `Tickable.TICKID_MOB`), and it knows
nothing about the game clock, the single game thread or the world files. Where
CoffeeSUD differs from CoffeeMud this chapter says so.

## In this chapter

- [Source layout](#source-layout)
- [Building the engine](#building-the-engine)
- [Class loading and registration](#class-loading-and-registration)
- [The object model](#the-object-model)
- [Physical state: PhyStats, CharStats and CharState](#physical-state-phystats-charstats-and-charstate)
- [Libraries and global utilities](#libraries-and-global-utilities)
- [The message model](#the-message-model)
- [Ticks, time and the game thread](#ticks-time-and-the-game-thread)
- [Writing new classes](#writing-new-classes)
  - [Rules every class follows](#rules-every-class-follows)
  - [Command](#command)
  - [Ability (a spell)](#ability-a-spell)
  - [Property](#property)
  - [Behavior](#behavior)
  - [Item](#item)
  - [MOB](#mob)
  - [Locale](#locale)
  - [Race](#race)
  - [Character class](#character-class)
  - [Library](#library)
  - [Test](#test)
- [Saving and configuring your classes](#saving-and-configuring-your-classes)
- [Getting your classes into a game](#getting-your-classes-into-a-game)
- [Worked examples](#worked-examples)
- [Coding conventions](#coding-conventions)
- [Logging](#logging)
- [Localisation](#localisation)
- [Testing a Java change](#testing-a-java-change)
- [Persistence notes](#persistence-notes)
- [Troubleshooting](#troubleshooting)
- [See also](#see-also)

Paths in this chapter that start `com/planet_ink/` are under `CoffeeMud/`.
Shell commands run from the repository root. Package names are relative to
`com.planet_ink.coffee_mud` (so `Commands` means
`com.planet_ink.coffee_mud.Commands`) unless written in full.

## Source layout

The repository root holds the game (`CoffeeMud/`), the test and tooling scripts
(`tools/`), the handbook (`docs/`), the project plans (`PLAN.md`, `DEVPLAN.md`,
`LATER.md`), per-feature audits (`audit/`) and the earlier subsystem analysis
(`DOCS/`). Everything the engine runs is Java under
`CoffeeMud/com/planet_ink/coffee_mud/` (5,168 source files and about 1.4 million
lines in this snapshot). Class files are compiled next to the sources; they are
not tracked in git.

| Package | What it holds | Base classes and interfaces |
| --- | --- | --- |
| `Abilities` | Every skill, spell, prayer, chant, song, language, disease, poison, trap, craft skill and property. Subfolders `Archon`, `Common`, `Diseases`, `Druid` (chants), `Fighter`, `Languages`, `Misc`, `Paladin`, `Poisons`, `Prayers`, `Properties`, `Ranger`, `Skills`, `Songs`, `Specializations`, `SuperPowers`, `Spells`, `Tech`, `Thief`, `Traps`. | `StdAbility`, `ThinAbility`, `GenAbility`; `Properties/Property`; `Spells/Spell`; `Abilities/interfaces/Ability` |
| `Areas` | Area classes: `StdArea`, grid, thin (loaded on demand) and instance areas, boardable ships, planets. | `Areas/interfaces/Area` |
| `Behaviors` | Behaviors, including `Scriptable`, `JSBehavior` and the generic behavior. | `StdBehavior`; `Behavior` |
| `CharClasses` | Character classes and `GenCharClass`. | `StdCharClass`; `CharClass` |
| `Commands` | Every typed command (362 files), including the CoffeeSUD building and testing commands. | `StdCommand`; `Command` |
| `Common` | Default implementations of the "common" data objects: messages, stats, clocks, sessions, shops, quests, scripting engines, clans, factions. | `Common/interfaces/*` (`CMMsg`, `PhyStats`, `CharStats`, `CharState`, `TimeClock`, `Session`, `Quest`, `ScriptingEngine`, ...); all extend `CMCommon` |
| `Exits` | Exit classes: doors, gates, generic exits. | `StdExit`; `Exit` |
| `Items` | Items by kind: `Basic`, `Weapons`, `Armor`, `MiscMagic`, `ClanItems`, `BasicTech`, `CompTech`, `Software`. | `StdItem` and its subclasses; `Items/interfaces/*` |
| `Libraries` | The engine's service libraries (one class per interface), plus `editors/` (the in-game generic editor screens), `layouts/` (area layout generators) and `interfaces/`. | `StdLibrary`; `CMLibrary` and one interface each |
| `Locales` | Room types: `StdRoom`, the indoor and outdoor locales, grids, mazes, water, ship decks. | `StdRoom`; `Room`, `GridLocale` |
| `MOBS` | Mob classes: `StdMOB`, creatures, shopkeepers, bankers, deities, generic mobs. | `StdMOB`; `MOB` |
| `Races` | Races and `GenRace`. | `StdRace`; `Race` |
| `Tests` | Engine self-tests run by the `TEST` command. | `StdTest`; `CMTest` |
| `application` | Entry points: `SUD` (the single-player launcher), `MUD` (the engine boot code `SUD` calls), `UpgradeTool`, `VFShell`, `LocalizationHelper`. | |
| `core` | Static utilities and engine services: `CMClass`, `CMLib`, `CMProps`, `CMSecurity`, `CMath`, `CMParms`, `CMStrings`, `Log`, `Resources`, `GameTime`, `GameRandom`, and about 200 more, most of them CoffeeSUD's own single-player subsystems (`Party`, `Waits`, `StatEffects`, `JS*`, `Quest*`, `Combat*`, ...). Subpackages: `interfaces` (the object-model interfaces), `collections` (specialised lists, maps and trees), `database` (the SQL cache loaders), `exceptions`, `save` (campaign saves and world files), `threads` (the scheduler). | |

Four things that are different from stock CoffeeMud:

- The network and multi-player code is gone (telnet, web server, mail,
  intermud, MUDGrinder, protocols). A few leftovers still compile because other
  code calls them: `Libraries/CMProtocols` (its `msp()` sound tags are
  filtered out of all output), `Libraries/CMChannels`, `Common/DefaultSession`
  (the base of the console and window sessions), `Common/DefaultPlayerAccount`.
  Before using an inherited multi-player class, check that something still
  creates it.
- `core/` has grown from the original utilities into most of what CoffeeSUD
  adds. New subsystems are usually a static helper class in `core/` (for
  example `core/Party.java` or `core/Waits.java`) that the stock classes call at
  a few hook points, not a new library. The inventory of these subsystems is in
  [Engine internals](33-engine-internals.md).
- Content that used to live in the SQL database now lives in world files under
  `CoffeeMud/world/`; the database (`jdbc:fakedb:`) is only a cache rebuilt from
  them. See [World workflow](02-world-workflow.md) and
  [State and persistence](state-and-persistence.md).
- Besides Java, a class can be written in JavaScript. See
  [JavaScript](05-javascript.md) and
  `CoffeeMud/resources/examples/Lemming.js`.

The other top-level folders of `CoffeeMud/` are `resources/` (help files, recipe
tables, quest templates, random-area definitions, `translation_en_TX.properties`),
`world/` (the shipped campaign content), `saves/` (campaign saves, not in git),
`guides/` (the inherited HTML manuals), `lib/` (`js.jar`, the Rhino JavaScript
engine, the only library) and the launch and build scripts.

## Building the engine

The engine compiles in place with `CoffeeMud/build.sh`:

```sh
CoffeeMud/build.sh          # compile if any source changed since the last good build
CoffeeMud/build.sh clean    # delete every .class file first, then compile
```

What the script does (see [build.sh](../../CoffeeMud/build.sh)):

1. It lists every `*.java` file under `com/` and takes a checksum of the list.
2. If you asked for `clean`, there is no `.build-stamp`, or the checksum differs
   from the stamp (a source file was added or deleted), it deletes every `.class`
   file first. This matters because the class loader finds classes by scanning
   folders: a `.class` left behind by a deleted source would still be loaded.
3. Otherwise, if no `.java` file is newer than `.build-stamp`, it prints
   `Up to date.` and stops.
4. Otherwise it runs one `javac` over the whole source list: `-nowarn
   -Xlint:none -g -encoding UTF-8 --release 8 -Xmaxerrs 1000 -J-Xmx2g
   -classpath ".:./lib/js.jar" -d .`. The output goes to `.build.log`; the script
   prints it minus the noise and finishes with `Build OK.` or `Build FAILED.`
   (exit status 0 or the `javac` status). The stamp is written only on success.

Consequences for developers:

- **Class files sit beside the sources** (`-d .`). There is no jar and no
  separate output folder. `CMClass` finds classes by scanning
  `com/planet_ink/coffee_mud/<Package>/` for `.class` files.
- **Every build recompiles everything**, even for a one-line change. It takes
  about a minute from clean; `sud.sh` runs `build.sh` every time it starts.
- **The language level is Java 8** (`--release 8`). Do not use `var`, `List.of`,
  records, text blocks or other newer features; the code base uses none. The JDK
  must be version 9 or newer (for `--release`); the project is developed on JDK
  21. `build.bat` is the Windows equivalent (it always compiles from clean and
  locates Java through `findjava.bat`).
- **The classpath is `.` plus `lib/js.jar`.** There is no other dependency.
- **`CoffeeMud/build.xml`, `Makefile` and `.github/workflows/build-coffeemud.yaml`
  are inherited from CoffeeMud** and still mention `lib/jzlib.jar`, which no
  longer exists. Use `build.sh`.
- Sources must be UTF-8.

Run the game from `CoffeeMud/` with `./sud.sh` (it runs `java -classpath
".:./lib/js.jar" -Xmx1g com.planet_ink.coffee_mud.application.SUD`). Any
`NAME=VALUE` argument overrides `coffeemud.ini`. The launch modes (builder,
`DEV=1`, `CHECK=1`) are in [Installing and running](18-installing-and-running.md).
A developer's game is normally `./sud.sh DEV=1` (a normal game with the `DEV`
security group) or `./sud.sh BUILDER=1` (an Archon editing the world files).

## Class loading and registration

Almost every game object type is a Java class chosen by name at run time:
rooms by locale class, mobs by mob class, effects by ability ID. `core/CMClass`
is the registry. At boot it scans folders, loads each class it finds, makes one
instance of it (the *prototype*), and files the prototype in a list or map by
type. You never register a class in code; you put a `.class` file in a scanned
folder.

### What gets scanned

`CMClass.loadAllCoffeeMudClasses` loads these sets in this order, each from the
default folder plus any extra paths in the matching `coffeemud.ini` key:

| ini key | Default folder (`com/planet_ink/coffee_mud/`) | Must implement | `CMObjectType` |
| --- | --- | --- | --- |
| `LIBRARY` | `Libraries/` | `CMLibrary` | `LIBRARY` |
| `COMMON` | `Common/` | `CMCommon` | `COMMON` |
| `RACES` | `Races/` | `Race` | `RACE` |
| `CHARCLASSES` | `CharClasses/` | `CharClass` | `CHARCLASS` |
| `MOBS` | `MOBS/` | `MOB` | `MOB` |
| `EXITS` | `Exits/` | `Exit` | `EXIT` |
| `AREAS` | `Areas/` | `Area` | `AREA` |
| `LOCALES` | `Locales/` | `Room` | `LOCALE` |
| `ABILITIES` | `Abilities/`, and with `%DEFAULT%` also all its subfolders | `Ability` | `ABILITY` |
| `ITEMS` | `Items/Basic/` | `Item` | `ITEM` |
| `WEAPONS` | `Items/Weapons/` | `Weapon` | `WEAPON` |
| `ARMOR` | `Items/Armor/` | `Armor` | `ARMOR` |
| `MISCMAGIC` | `Items/MiscMagic/` | `MiscMagic` | `MISCMAGIC` |
| `CLANITEMS` | `Items/ClanItems/` | `ClanItem` | `CLANITEM` |
| `TECH`, `COMPTECH`, `SOFTWARE` | `Items/BasicTech/`, `Items/CompTech/`, `Items/Software/` | `Technical`, `TechComponent`, `Software` | `TECH` (all three) |
| `BEHAVIORS` | `Behaviors/` | `Behavior` | `BEHAVIOR` |
| `COMMANDS` | `Commands/` | `Command` | `COMMAND` |
| `CMTESTS` | `Tests/` | `CMTest` | `TEST` |

All of them ship as `%DEFAULT%`. The value is a semicolon-separated list, and
`%DEFAULT%` stands for the default folder. A list entry is a folder, a single
`.class` file or a `.js` file, with forward slashes, relative to `CoffeeMud/`:

```text
MOBS=%DEFAULT%;resources/mymobs
COMMANDS=%DEFAULT%;resources/mycommands/Tally.class
```

When a boot-time scan finishes, it also adds the generic objects defined in the
world files (generic races, classes, abilities, commands, behaviors) to the same
sets, then `CMClass.intializeClasses()` calls `initializeClass()` once on every
prototype, in `CMObjectType` order (races, character classes, mobs, abilities,
locales, ...). That is why a `CharClass.initializeClass()` can map abilities (the
ability classes are already loaded) and why anything that reads the *other* sets
must not run before this point.

The rules the scanner applies (from `CMClass.loadObjectsIntoList`):

- A folder is scanned for `*.class` files whose names contain no `$` (inner and
  anonymous classes are skipped) and for `*.js` files. `.java` files are not
  compiled by the scan.
- A class is kept only if it is `public`, not `abstract`, not an interface, and
  assignable to the set's interface. Others are skipped (with a warning when
  the log level allows).
- It is instantiated with its public no-argument constructor. An exception in
  the constructor is logged and the class is skipped.
- For environmental types the class's `ID()` must equal its simple class name,
  or the log says `Possible class ID misnaming`. Lookups use the ID.
- If two entries in the list have the same simple name, the later one replaces
  the earlier one in the set. This is how a custom class can override a stock
  one: put a class of the same name in a folder listed after `%DEFAULT%`.
- The prototype lists are sorted case-insensitively by ID.

### Classes in a world folder

CoffeeSUD adds a convenient way to ship Java or JavaScript classes with a world.
At start (`SUD.worldClassPaths`), for each of the keys `COMMON`, `ITEMS`,
`WEAPONS`, `ARMOR`, `MISCMAGIC`, `CLANITEMS`, `MOBS`, `BEHAVIORS`, `ABILITIES`,
`CHARCLASSES`, `RACES`, `COMMANDS`, `LOCALES`, `AREAS`, `EXITS`, `TECH`,
`COMPTECH` and `SOFTWARE`, a folder `WORLDDIR/classes/<key in lower case>/` (for
example `world/classes/commands/`) is appended to that key's path list. In the
same way `WORLDDIR/js/<kind>/` folders of JavaScript classes are appended, for
the kinds `behaviors`, `abilities`, `properties` (joins `ABILITIES`), `items`,
`mobs`, `races`, `charclasses`, `commands`, `common`, `locales`, `exits` and
`areas`. The folders are only used if they exist when the game starts. There is
no folder for `LIBRARY` or `CMTESTS`; those two can only be extended through
`coffeemud.ini` or a command-line `LIBRARY=...` argument.

Notes:

- A `.class` file loaded this way is defined from its bytes, not from the Java
  class path. The loader's fallback for a "wrong name" error retries with the
  last path segment as the class name, so the safe way to ship one is to compile
  it in the default package (no `package` line). Whether a class compiled in a
  package loads from an arbitrary folder is not exercised by anything in the
  repository (unverified); classes compiled inside the source tree, in their
  real package and folder, always work.
- `SUD.worldClassPaths` checks `WORLDDIR/classes/...` but adds the literal path
  `world/classes/...`, so with `WORLDDIR` set to a folder other than `world` the
  appended path points at the wrong place (see Troubleshooting).
- Changes to files under `classes/` are not picked up by `WORLD RELOAD`; they
  stay listed as changed until the game restarts
  ([World workflow](02-world-workflow.md)).

### Two class loaders

`CMClass` extends `ClassLoader`. Its `loadClass` hands a request to the normal
(system) loader when the class is outside `com.planet_ink.coffee_mud.`, or is
under `core.` or `application.`, or has `.interfaces.` in its name; everything
else (every Std class, every locale, mob, command, library) is read as bytes
from the folders and defined by `CMClass` itself. In practice:

- The interfaces and `core/` utilities are single, shared classes. Game classes
  are defined by `CMClass`, once per name (the static `classes` map caches them).
- Code in `core/` and `application/` should talk to game classes only through
  interfaces and `CMClass.getX(...)`. If a `core/` class names a concrete game
  class (some CoffeeSUD code does: `core/WeatherControl` and `core/JSCm`
  import `Commands.EnvToolsCmd`, `core/JSCommand` imports
  `Commands.StdCommand`), the system loader loads its own separate copy of that
  class from the class path, with separate static fields. Do not add static
  state to a class that `core/` also references by name (unverified in play).
- Because the class files must also be on the class path (`.`), editing a class
  file under the source tree and starting the game picks the change up with no
  further step.

### LOAD and UNLOAD at run time

In builder mode (Archon) `LOAD <type> <path>` loads a class into a running game,
and `UNLOAD CLASS <name>` removes one:

```text
LOAD COMMAND com/planet_ink/coffee_mud/Commands/Tally.java
LOAD ABILITY resources/myabilities/Spell_Fortify.class
UNLOAD CLASS Tally
```

If the path ends in `.java`, `LOAD` first compiles the file with the JDK's
`javax.tools` compiler (the game must be running on a JDK, not a JRE), using the
JVM's class path and the JVM's default language level, then loads the resulting
`.class`. `LOAD` replaces the prototype in the set, so new objects use the new
class; objects already in the world keep the old one. Treat it as a quick way to
try a class. The full help is `HELP LOAD` and `HELP UNLOAD`.

### Boot order

`SUD` reads `coffeemud.ini` and `world.ini`, then calls `MUD.main`. In outline
the engine then: connects to the database cache, loads all classes as above,
loads factions, initialises the classes, loads expertises and socials, loads the
catalog, loads rooms and builds the map, loads quests, and goes online
(`CMProps.HostState` moves `BOOTING`, `LOADINGMAP`, `STARTING`, `RUNNING`; shutdown
is `SHUTTINGDOWN`, `STOPPED`). The scheduler thread waits until the state is
`RUNNING` before it starts ticking anything.
