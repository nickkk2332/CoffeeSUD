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

## The object model

Everything in the world is an object that implements a stack of small
interfaces, and almost every interface has a standard class (`Std...`) and, for
things a builder can define in data, a generic class (`Gen...`).

### The interfaces

```text
CMObject                  ID(), name(), newInstance(), copyOf(), initializeClass()
 +- Tickable              tick(), getTickStatus()
 |   `- Environmental     + StatsAffecting, MsgListener, Contingent, Modifiable, Expireable
 |       +- Physical      + Affectable: has PhyStats and a list of effects
 |       |   `- PhysicalAgent   + Behavable, Taggable: also has behaviors and scripts
 |       |       +- MOB          (also Rider, ItemPossessor, AbilityContainer, Combatant, ...)
 |       |       +- Item         (through Rider; also Wearable, Readable)
 |       |       +- Room         (through Places; also ItemPossessor, MOBPossessor)
 |       |       +- Area         (through Places)
 |       |       `- Exit         (also Readable, CloseableLockable)
 |       `- Ability      an effect, skill, spell, prayer, song, language, trap or property
 +- Behavior              Tickable + MsgListener + Contingent + Modifiable
 +- Command               typed commands
 +- Race, CharClass       Tickable + StatsAffecting + MsgListener + Modifiable
 +- CMLibrary             a service library
 `- CMCommon              data objects: CMMsg, PhyStats, CharStats, CharState, TimeClock, Session, ...
```

What each interface contributes (the methods you will actually call and
override; all are in `core/interfaces/` unless noted):

| Interface | Key methods |
| --- | --- |
| `CMObject` | `ID()` (the class name; the lookup key), `name()`, `newInstance()` (a fresh object of the same class), `copyOf()` (a clone with its state), `initializeClass()` (called once on the prototype at boot) |
| `Environmental` | `Name()` and `setName`, `name()` (what viewers see; may be changed by `PhyStats.newName()`), `displayText()` / `setDisplayText`, `description()` / `setDescription`, `image()`, `isGeneric()`, `text()` / `setMiscText(String)` (the saved parameter string), `miscTextFormat()`, `sameAs(Environmental)`, `L(String, String...)` |
| `Tickable` | `tick(Tickable ticking, int tickID)` (return `false` to stop being ticked), `getTickStatus()` |
| `MsgListener` | `okMessage(Environmental myHost, CMMsg msg)` (may veto or alter), `executeMsg(Environmental myHost, CMMsg msg)` (react) |
| `StatsAffecting` | `affectPhyStats(Physical affected, PhyStats stats)`, `affectCharStats(MOB, CharStats)`, `affectCharState(MOB, CharState)` |
| `Contingent` | `destroy()`, `amDestroyed()`, `isSavable()`, `setSavable(boolean)` |
| `Modifiable` | `getStatCodes()`, `getStat(String)`, `setStat(String, String)`, `isStat(String)`, `getSaveStatIndex()` |
| `Expireable` | `expirationDate()`, `setExpirationDate(long)` (a game time; see below) |
| `Affectable` (`Physical`) | `basePhyStats()`, `phyStats()`, `recoverPhyStats()`, `addEffect(Ability)`, `addNonUninvokableEffect(Ability)`, `delEffect(Ability)`, `fetchEffect(String ID)`, `effects()`, `numEffects()`, `eachEffect(EachApplicable<Ability>)`, `delAllEffects(boolean)` |
| `Behavable` (`PhysicalAgent`) | `addBehavior(Behavior)`, `delBehavior`, `fetchBehavior(String ID)`, `behaviors()`, `addScript(ScriptingEngine)`, `scripts()`, `delAllBehaviors()` |
| `MOB` (`MOBS/interfaces`) | `baseCharStats()` / `charStats()` / `recoverCharStats()`, `baseState()` / `curState()` / `maxState()` / `recoverMaxState()` / `resetToMaxState()`, `location()` / `setLocation(Room)`, `getStartRoom()` / `setStartRoom(Room)`, `bringToLife(Room, boolean resetStats)`, `removeFromGame(...)`, `getVictim()`, `isPlayer()` / `isMonster()`, `session()`, `tell(String)`, `playerStats()`, `getMoney()`; from `ItemPossessor`: `items()`, `addItem`, `delItem`, `findItem`, `moveItemTo`; from `AbilityContainer`: `addAbility`, `fetchAbility`, `abilities()`; from `MUDCmdProcessor`: `enqueCommand`, `doCommand`, `dequeCommand`, `actions()` |
| `Item` (`Items/interfaces`) | `owner()` / `setOwner`, `container()` / `setContainer`, `material()`, `value()` / `baseGoldValue()` / `setBaseValue`, `usesRemaining()`, `amWearingAt(long)` (from `Wearable`), `secretIdentity()`, `stopTicking()` |
| `Room` (`Locales/interfaces`) | `roomID()`, `getArea()`, `domainType()`, `getRoomInDir(int)`, `getRawExit(int)`, `getExitInDir(int)`, `show(...)` / `send(MOB, CMMsg)` / `showHappens(...)` (message posting), `addItem`, `fetchInhabitant`, `inhabitants()`, `recoverRoomStats()`, `numPCInhabitants()` |
| `Area` (`Areas/interfaces`) | `getProperMap()` (its rooms), `getRoom(String id)`, `getClimateObj()`, `getTimeObj()` (its `TimeClock`), `getTheme()`, `getAreaState()`, children and parents |
| `Exit` (`Exits/interfaces`) | `doorName()`, `openWord()`, `closeWord()`, `viewableText(MOB, Room)`, plus lock and open state from `CloseableLockable` |
| `Ability` (`Abilities/interfaces`) | `invoke(MOB, List<String>, Physical, boolean auto, int asLevel)`, `unInvoke()`, `triggerStrings()`, `classificationCode()`, `abstractQuality()`, `canAffect(Physical)`, `affecting()`, `invoker()`, `proficiency()`, `proficiencyCheck(...)`, `startTickDown(...)`, `accountForYourself()`, `canBeUninvoked()` |
| `Behavior` (`Behaviors/interfaces`) | `getParms()` / `setParms(String)`, `startBehavior(PhysicalAgent)`, `endBehavior(PhysicalAgent)`, `canImprove(PhysicalAgent)`, `flags()`, `accountForYourself()`; CoffeeSUD adds default `catchUp(PhysicalAgent host, long ticks)` and `repliesNow(PhysicalAgent host)` |
| `Command` (`Commands/interfaces`) | `getAccessWords()`, `execute(MOB, List<String>, int metaFlags)`, `preExecute(...)`, `securityCheck(MOB)`, `actionsCost(...)`, `combatActionsCost(...)`, `canBeOrdered()`, `canBeCancelled()`, `executeInternal(MOB, int, Object...)` |

`Environmental.Name()` is the object's own name; `name()` and `name(MOB viewer)`
are the name as seen (the second is on `Physical`). Always show `name(viewer)`
or a `<S-NAME>`/`<T-NAME>` tag to players, never `Name()`.

### Standard classes and generic classes

| Kind | Interface | Standard base class | Notable subclasses | Generic (data-defined) classes |
| --- | --- | --- | --- | --- |
| Item | `Item` | `Items/Basic/StdItem` | `StdContainer`, `StdFood`, `LightSource`, `StdKey`, `StdBook`; `Items/Weapons/StdWeapon`; `Items/Armor/StdArmor` (extends `StdContainer`); `StdDrink` (extends `StdContainer`); `Items/MiscMagic/StdWand`, `StdPotion`, `StdScroll`... | `GenItem`, `GenContainer`, `GenFood`, `GenDrink`, `GenWeapon`, `GenArmor`, `GenWand`, `GenBoat`... (about 180 `Gen*` item classes) |
| MOB | `MOB` | `MOBS/StdMOB` | `StdRideable`, `StdShopKeeper`, `StdBanker`, `StdDeity`, `StdPostman`, `StdAuctioneer`, creature classes such as `Chicken` | `GenMob`, `GenRideable`, `GenShopkeeper`, `GenBanker`, `GenUndead`, `GenCow`... |
| Room | `Room` | `Locales/StdRoom` | `StoneRoom`, `Woods`, `Plains`, grids (`StdGrid`), mazes, water locales | none: every room stores its own display text, description, exits and effects |
| Exit | `Exit` | `Exits/StdExit` | `StdOpenDoorway`, `StdClosedDoorway`, `StdLockedDoorway`, `Door`, `Gate` | `GenExit`, `GenDoor`, `GenCurtain`, `GenAirLock` |
| Area | `Area` | `Areas/StdArea` | `StdGridArea`, `StdThinArea`, `StdThinInstance`, `StdBoardableShip` | none |
| Ability | `Ability` | `Abilities/StdAbility`; `Properties/Property` (extends the small `ThinAbility`) | `Spells/Spell`, `Prayers/Prayer`, `Druid/Chant`, `Songs/Song`, `Skills/StdSkill`, `Common/CommonSkill`, `Languages/StdLanguage`, `Diseases/Disease`, `Poisons/Poison`, `Traps/StdTrap` | `GenAbility`, `GenCraftSkill`, `GenGatheringSkill`, `GenWrightSkill`, `GenDisease`, `GenPoison`, `GenTrap`, `GenLanguage` |
| Behavior | `Behavior` | `Behaviors/StdBehavior` | all behaviors | `GenBehavior` |
| Command | `Command` | `Commands/StdCommand` | all commands | `GenCommand` |
| Race | `Race` | `Races/StdRace` | all races | `GenRace` |
| Character class | `CharClass` | `CharClasses/StdCharClass` | all classes | `GenCharClass` |

(`Skills/StdSkill` and the other intermediate classes named above are in the
folders shown. Check the file before relying on a name: for example `Prayer` is
in `Abilities/Prayers/` and `Chant` in `Abilities/Druid/`.)

A **standard** class is complete Java: its behavior, name and defaults are
written in code, and an instance stores only what changes (for an item, the
use count, level, ability and a text string; for a mob, its stats, inventory
and effects). A **generic** class is a thin Java class whose entire definition
is data: its fields are read and written through `getStat`/`setStat`
(`Modifiable`), serialised to XML by `Libraries/CoffeeMaker`
(`GenericBuilder`), edited by the builder's `MODIFY` screens
(`Libraries/CMGenEditor`), and stored in world files and the catalog. Builders
create generic objects with no programmer involved; programmers write standard
classes. See [Mobs](21-mobs.md), [Items](22-items.md) and
[Characters](03-characters.md) for the data side.

### Prototypes and instances

The sets built at boot hold **prototypes**. Never put a prototype in the world.
Use the getters, which return a new instance (`newInstance()`):

| You want | Call |
| --- | --- |
| An ability (spell, property...) | `CMClass.getAbility("Spell_Fortify")`, or `CMClass.getAbility(id, miscText)` to set its parameters |
| A behavior | `CMClass.getBehavior("Sneezer")` |
| An item of any item type | `CMClass.getItem("LuckyCoin")`; also `getWeapon`, `getArmor`, `getMiscMagic`, `getBasicItem` |
| A mob | `CMClass.getMOB("Sparrow")` |
| A room, exit, area | `CMClass.getLocale("StoneRoom")`, `getExit(...)`, `getAreaType(...)` |
| A race, class | `CMClass.getRace("Human")`, `CMClass.getCharClass("Mage")` (these return the shared prototype; races and classes are stateless) |
| A command | `CMClass.getCommand("Say")` (the shared prototype; commands keep no per-use state) |
| A common object | `CMClass.getCommon("DefaultScriptingEngine")`, `CMClass.getCommon("DefaultPhyStats")` |
| Anything by ID | `CMClass.getUnknown(id)` (items first, then mobs and abilities) |
| The prototype itself, for reading only | `CMClass.getAbilityPrototype(id)`, `getItemPrototype`, `getMOBPrototype`, `getCommonPrototype`, `getPrototypeByID(type, id)` |

Each getter returns `null` for an unknown ID; check before using it.
`CMClass.findAbility`, `findRace`, `findCharClass` and `findCommand` also match by
display name.

A newly created object is not yet *in* the world:

- A mob is placed with `mob.bringToLife(room, true)` (and `setStartRoom(room)`
  first if it should rejuvenate there). `bringToLife` starts its tick.
- An item is placed with `room.addItem(item)` or `mob.addItem(item)`, then the
  holder's `recoverRoomStats()` or `recoverPhyStats()`.
- Remove either for good with `destroy()`; `destroy()` stops ticking, removes it
  from its holder and marks it `amDestroyed()`. Test `amDestroyed()` before using
  a reference you kept across ticks.
- A permanent effect (a property) is added with
  `host.addNonUninvokableEffect(ability)`; a timed one is added by invoking the
  spell (`beneficialAffect` and `maliciousAffect` copy the ability, call
  `startTickDown` and recompute the stats). Plain `host.addEffect(ability)` adds
  an effect that is removed only when its `unInvoke()` runs.

### Where the engine keeps instances

- `CMLib.map()` (`WorldMap`) holds the areas and rooms: `getArea(name)`,
  `getRoom(roomID)`, `areas()`, `rooms()`, `roomLocation(Environmental)`.
- A room holds its inhabitants and items; a mob holds its inventory; an item may
  sit in a container. `owner()` and `container()` say where an item is.
- Prototypes live in `CMClass`. The catalog (`CMLib.catalog()`) holds named
  template mobs and items.

## Physical state: PhyStats, CharStats and CharState

Three small data objects (in `Common/interfaces/`) hold the numbers that
describe a thing right now. Each exists in two copies on the object: a **base**
copy that is the real, saved value, and a **current** copy that is recomputed
from the base plus everything that affects the object.

| Object | Base | Current | Recompute | What it holds |
| --- | --- | --- | --- | --- |
| `PhyStats` | `basePhyStats()` | `phyStats()` | `recoverPhyStats()` | On every `Physical`: `level`, `ability`, `rejuv`, `weight`, `height`, `armor`, `damage`, `speed`, `attackAdjustment`, `disposition` (a bit mask of `IS_*` flags such as `IS_HIDDEN`, `IS_INVISIBLE`, `IS_SLEEPING`, `IS_FLYING`, `IS_SWIMMING`, `IS_LIGHTSOURCE`, `IS_DARK`), `sensesMask` (`CAN_NOT_SEE`, `CAN_SEE_HIDDEN`, `CAN_NOT_HEAR`, `CAN_NOT_MOVE`, ...), `newName` and ambiances |
| `CharStats` | `baseCharStats()` | `charStats()` | `recoverCharStats()` | On MOBs: the six stats (`STAT_STRENGTH`, `STAT_INTELLIGENCE`, `STAT_DEXTERITY`, `STAT_CONSTITUTION`, `STAT_CHARISMA`, `STAT_WISDOM`), gender, the saving throws (`STAT_SAVE_FIRE`, `STAT_SAVE_MAGIC`, `STAT_SAVE_SPELLS`, ...), maximum-stat adjustments, age, weight adjustment, the race (`getMyRace()`), the classes and levels (`getCurrentClass()`), the deity |
| `CharState` | `baseState()` | `maxState()` (the caps); `curState()` is the live value | `recoverMaxState()` | On MOBs: hit points, mana, movement, hunger, thirst, fatigue |

The rules:

1. **Change the base to make a lasting change, then recompute.** For example
   `mob.basePhyStats().setLevel(10); mob.recoverPhyStats();`. Writing to
   `phyStats()` directly works only until the next recompute, which can be a few
   milliseconds away.
2. **To make a temporary change, be an effect.** Anything that is on the
   object and implements `StatsAffecting` (an `Ability`, an item the mob holds,
   the room it stands in, its race and class, a faction) gets a turn to alter
   the *current* copy in `affectPhyStats`, `affectCharStats` or
   `affectCharState`. That is how a spell like `Spell_Shield` lowers armor for
   as long as it lasts: it overrides `affectPhyStats` and does
   `affectableStats.setArmor(affectableStats.armor() - 10 - ...)`. Lower armor
   means harder to hit.
3. **Call the recompute after you add, remove or change anything that
   affects**: after changing a base stat, after `addEffect` or `delEffect`,
   after moving an item onto a mob. `addEffect`, `addNonUninvokableEffect` and
   `delEffect` only edit the list of effects (and, on adding, call
   `setAffectedOne`); they do *not* recompute. The ability helpers do:
   `startTickDown` (so `beneficialAffect` and `maliciousAffect`) and
   `unInvoke` recompute the host's stats. Code that adds or removes an effect
   itself must call `recoverPhyStats()` (and for a mob `recoverCharStats()` and
   `recoverMaxState()` if the effect changes those) afterwards. Note also that
   `addEffect` silently ignores an effect whose ID the host already has.
4. **`CharStats` and `CharState` have more than one recompute.** The usual
   order after a big change to a mob is `recoverCharStats()`,
   `recoverPhyStats()`, `recoverMaxState()` (the maximums depend on the stats,
   and the stats depend on the class and race). `StdMOB.recoverPhyStats()` starts
   from a copy of the base, then applies (in this order) the room, the mount, the
   deity, each class, the race, each item, each effect, each faction, and
   CoffeeSUD's `core/StatEffects` (the `STATEFFECT_` properties); see
   `MOBS/StdMOB.java` near line 796.
5. **`affect*` methods are called very often.** Keep them free of side effects,
   allocation and anything slow. Never call `recoverPhyStats()` from inside an
   `affect*` method.
6. `curState()` is the one live value of the three: damage and healing change
   it directly (`curState().adjHitPoints(...)`, normally through
   `CMLib.combat().postDamage`), and `resetToMaxState()` refills it.

A related CoffeeSUD helper, `core/StatEffects`, lets world content (item and
mob property strings named `STATEFFECT_...`) add to the stats without a Java
class; it is documented in
[Characters](03-characters.md).

## Libraries and global utilities

### CMLib

`core/CMLib` is the single access point for services. Every library is an
object created at boot from a class in `Libraries/` (or `core/database`,
`core/threads`) and reached through a static accessor. Always call the accessor
every time (`CMLib.flags().isSleeping(mob)`); do not cache the library in a
static field, because the accessor depends on the current thread group (see
below). The `CMLib.Library` enum lists them; each accessor returns the
interface in the third column.

| Accessor | Interface | Implementation | What it is for |
| --- | --- | --- | --- |
| `CMLib.map()` | `WorldMap` | `CMMap` | Areas and rooms: `getRoom(id)`, `getArea(name)`, `roomLocation(E)`, `areas()`, `rooms()`, factory MOBs |
| `CMLib.hunt()` | `WorldHuntLibrary` | `WorldHuntUtils` | Searches across the whole map for mobs, items, rooms, exits |
| `CMLib.flags()` | `CMFlagLibrary` | `Sense` | State queries: `canBeSeenBy`, `isSleeping`, `isInDark`, `isAnimalIntelligence`, `canMove`, `isGettable`, `isAliveAwakeMobile`... Use these instead of testing `PhyStats` bits by hand |
| `CMLib.combat()` | `CombatLibrary` | `MUDFight` | Attacks, damage, healing, death, saves: `postAttack`, `postDamage`, `postHealing`, `postDeath`, `checkSavingThrows`, `checkDamageSaves`, `standardMobCondition` |
| `CMLib.commands()` | `CommonCommands` | `CommonMsgs` | The shared bodies of common commands, as `post*` methods: `postGet`, `postDrop`, `postGive`, `postOpen`, `postWear`, `postSay`, `postLook`, `postFlee`, `postCommandFail`, `postCommandRejection`, `handleUnknownCommand` |
| `CMLib.english()` | `EnglishParsing` | `EnglishParser` | Command and target parsing: `findCommand(mob, words)`, `fetchItemList`, `startsWithAnIndefiniteArticle`, skill invocation |
| `CMLib.masking()` | `MaskingLibrary` | `MUDZapper` | The zapper mask language: `getPreCompiledMask(String)`, `maskCheck(...)`. See [Behaviors and masks](24-behaviors-and-masks.md) |
| `CMLib.tracking()` | `TrackingLibrary` | `MUDTracker` | Movement and path finding: `walk`, `move`, tracking helpers |
| `CMLib.coffeeMaker()` | `GenericBuilder` | `CoffeeMaker` | Objects to and from XML: `getItemXML`, `getMobXML`, `getRoomXML`, `getAnyGenStat`, `setAnyGenStat`, `unpackItemFromXML` |
| `CMLib.genEd()` | `GenericEditor` | `CMGenEditor` | The in-game editor screens (`modifyStdItem`, `modifyStdMob`, `modifyGenRace`, `genMiscText`...) |
| `CMLib.catalog()` | `CatalogLibrary` | `CMCatalog` | Catalogued template mobs and items |
| `CMLib.beanCounter()` | `MoneyLibrary` | `BeanCounter` | Currencies and coins: `getTotalAbsoluteNativeValue(mob)`, `nameCurrencyShort(...)`, `addMoney(holder, amount)`, `subtractMoneyGiveChange(...)` |
| `CMLib.coffeeShops()` | `ShoppingLibrary` | `CoffeeShops` | Shopkeeper stock and pricing |
| `CMLib.materials()` | `MaterialLibrary` | `RawCMaterial` | Raw materials and resources |
| `CMLib.itemBuilder()` | `ItemBalanceLibrary` | `TimsLibrary` | Item power calculation and balancing |
| `CMLib.law()` | `LegalLibrary` | `MUDLaw` | Laws, crimes and warrants |
| `CMLib.factions()` | `FactionManager` | `Factions` | Factions and alignment (`setAlignment`) |
| `CMLib.clans()` | `ClanManager` | `Clans` | Clans |
| `CMLib.quests()` | `QuestManager` | `Quests` | Quest scripts and state |
| `CMLib.leveler()` | `ExpLevelLibrary` | `CoffeeLevels` | Experience and leveling |
| `CMLib.expertises()` | `ExpertiseLibrary` | `ColumbiaUniv` | Expertises |
| `CMLib.ableMapper()` | `AbilityMapper` | `CMAbleMap` | Which class or race gets which ability at which level: `addCharAbilityMapping`, `qualifyingLevel`, `qualifiesByLevel` |
| `CMLib.ableComponents()` | `AbilityComponents` | `CMAbleComps` | Spell components |
| `CMLib.ableParms()` | `AbilityParameters` | `CMAbleParms` | Recipe parameter tables for craft skills |
| `CMLib.percolator()` | `AreaGenerationLibrary` | `MUDPercolator` | Random area and object generation. See [Generation](12-generation.md) |
| `CMLib.time()` | `TimeManager` | `CoffeeTime` | The global calendar (`globalClock()`) and time formatting |
| `CMLib.dice()` | `DiceLibrary` | `Dice` | Dice: `roll(n, d, bonus)`, `rollPercentage()` |
| `CMLib.lang()` | `LanguageLibrary` | `DirtyLanguage` | Translation (`L`, `fullSessionTranslation`) and in-world speech garbling |
| `CMLib.coffeeFilter()` | `TelnetFilter` | `CoffeeFilter` | The output filter: colour codes, `<S-NAME>` pronoun tags, wrapping |
| `CMLib.color()` | `ColorLibrary` | `Brown` | Colour code tables |
| `CMLib.help()` | `HelpLibrary` | `MUDHelp` | Help lookup and the generated ability help |
| `CMLib.lister()` | `ListingLibrary` | `CMLister` | The tables the `LIST` command prints |
| `CMLib.journals()` | `JournalsLibrary` | `CMJournals` | Journals and the in-game mailbox (`postToMailbox`) |
| `CMLib.socials()` | `SocialsList` | `Socials` | Socials |
| `CMLib.sessions()` | `SessionsList` | `Sessions` | The list of sessions (the player's console and windows) |
| `CMLib.login()` | `CharCreationLibrary` | `CharCreation` | Character creation and the profile flow |
| `CMLib.players()` | `PlayerLibrary` | `CMPlayers` | Player character cache |
| `CMLib.achievements()` | `AchievementLibrary` | `Achievements` | Achievements |
| `CMLib.awards()` | `AutoAwardsLibrary` | `AutoAwards` | Automatic titles and properties |
| `CMLib.xml()` | `XMLLibrary` | `XMLManager` | XML parsing and writing |
| `CMLib.encoder()` | `TextEncoders` | `CMEncoder` | Compression and encoding |
| `CMLib.coffeeTables()` | `StatisticsLibrary` | `CoffeeTables` | Statistics |
| `CMLib.utensils()` | `CMMiscUtils` | `CoffeeUtensils` | Miscellaneous helpers that fit nowhere else |
| `CMLib.channels()` | `ChannelsLibrary` | `CMChannels` | Channels (largely vestigial in a single-player game) |
| `CMLib.slavery()` | `SlaveryLibrary` | `SlaveryParser` | Geas and orders given to followers |
| `CMLib.tech()` | `TechLibrary` | `GroundWired` | Electronics circuits |
| `CMLib.space()` | `GalacticMap` | `CoffeeDark` | Space objects |
| `CMLib.city()` | `CityMap` | `CityCafe` | A cache of bank, post office, auction and library objects |
| `CMLib.protocol()` | `ProtocolLibrary` | `CMProtocols` | What is left of the client protocol code (sound tags are discarded) |
| `CMLib.database()` | `DatabaseEngine` | `core/database/DBInterface` | The SQL cache (`jdbc:fakedb:`), the loaders for rooms, mobs, players, journals |
| `CMLib.threads()` | `ThreadEngine` | `core/threads/ServiceEngine` | Ticks, delayed and queued jobs, the scheduler. See [Ticks, time and the game thread](#ticks-time-and-the-game-thread) |

`CMLib` also hands out the static utility classes through `CMLib.math()`,
`parms()`, `strings()`, `classes()`, `security()`, `directions()`, `log()`,
`resources()` and `props()`, but code calls those classes directly:

| Class | Use |
| --- | --- |
| `CMath` | Numbers: `s_int`, `s_double`, `isInteger`, `isNumber`, `bset(mask, bit)`, `div`, `mul`, `random()` (the seeded game generator; never use `Math.random()` or a new `java.util.Random`, see below) |
| `CMStrings` | Strings: `capitalizeAndLower`, `padRight`, `removeColors`, `replaceVariables`, `startsWithIgnoreCase` |
| `CMParms` | Parameter strings and lists: `parse`, `combine(list, from)`, `parseCommas`, `getParmInt/Str/Bool/Double(text, key, default)` (read `KEY=value` from a parameter string, key match is case-insensitive and by start of word), `parseEQParms`, `toListString` |
| `Resources` | The text and object cache: `getFileResource(name, reload)`, `submitResource`, `removeResource` |
| `CMFile` | The sandboxed virtual file system (`CMFile.FLAG_LOGERRORS`...). Paths are relative to `CoffeeMud/`, forward slashes, and cannot leave it |
| `Log` | Logging, see [Logging](#logging) |
| `GameTime`, `GameRandom` | The game clock and the seeded generator, see below |
| `CMClass` | The class registry, see above |

### CMProps

`core/CMProps` holds `coffeemud.ini` after parsing (and the world's
`world.ini`, and any command-line override). It has typed enums:

```java
CMProps.getVar(CMProps.Str.MUDSTATUS)          // a string setting
CMProps.getIntVar(CMProps.Int.TICKSPERMUDDAY)   // an integer
CMProps.getBoolVar(CMProps.Bool.MOBCOMPRESS)    // a flag
CMProps.getTickMillis()                         // TICKTIME: 4000 ms by default
CMProps.getMillisPerMudHour()                   // MILLISPERMUDHOUR: 600000 by default
CMProps.isSinglePlayer()                        // true in CoffeeSUD
CMProps.getProp("SOMEKEY")                      // any raw key, as a string
CMProps.getCommandActionCost(ID(), default)     // see Command, below
CMProps.isState(CMProps.HostState.RUNNING)      // the engine's boot state
```

To add a setting for your own feature, read it with `CMProps.getProp("MYKEY")`
or `CMProps.instance().getStr("MYKEY", "default")` where you use it; you do not
need a new enum entry. Settings that scripts and builders change at run time
should go through [World settings](world-settings.md) instead.

`CMProps` also carries the `EXTVAR_<ClassID>` mechanism that adds stat codes to
a generic class, covered in
[Saving and configuring your classes](#saving-and-configuring-your-classes).

### CMSecurity

`core/CMSecurity` answers "may this player do this?" and "is this feature
turned off?":

```java
CMSecurity.isAllowed(mob, room, CMSecurity.SecFlag.CMDMOBS) // does the mob's security group allow it here?
CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.GOTO)
CMSecurity.isASysOp(mob)                                    // an Archon
CMSecurity.isDisabled(CMSecurity.DisFlag.AUTODISEASE)       // the DISABLE= list
CMSecurity.isDebugging(CMSecurity.DbgFlag.MESSAGES)         // the DEBUG= list
CMSecurity.isCommandDisabled("TALLY")                       // DISABLE=COMMAND_X
CMSecurity.isAbilityDisabled("SPELL_FORTIFY")
```

In CoffeeSUD the player is an ordinary mob with no special rights. Builder mode
(`BUILDER=1`) gives the character the Archon group; `DEV=1` gives the `DEV`
group (`GROUP_DEV` in `coffeemud.ini`). `SaveGame.isBuilderMode()` and
`SaveGame.isDevMode()` (in `core/save/`) tell you which game this is. Use them,
not `SecFlag`s, for developer-only commands. See
[Configuration and administration](19-configuration-and-administration.md).

### Thread groups: why the statics work

`CMClass`, `CMLib`, `CMProps`, `Log` and `Resources` keep *one instance per
thread group*, indexed by the first character of the current thread's thread
group name. The game runs in the group named `0-MUD`. This is a leftover of
CoffeeMud's ability to host several muds in one JVM. The consequence for you:
**any thread you start yourself must be created in the game's thread group**
(`new Thread(Thread.currentThread().getThreadGroup(), runnable)` from game
code), or every call to `CMLib.x()` on it fails with a `NullPointerException`.
Threads created from game code inherit the group by default. The usual answer
is not to start threads at all (see the next section).

### The seeded dice

`CMath.random()`, `CMLib.dice()` and `CMath.s_int`-style helpers draw from one
`GameRandom` that behaves like `java.util.Random` but whose state is saved and
restored with a campaign save, and which `RANDOMSEED=n` seeds for reproducible
runs. Code that must replay identically (including tests) uses only these.
`Math.random()` and `new Random()` break that guarantee.
