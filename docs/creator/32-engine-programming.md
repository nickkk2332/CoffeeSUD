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

## The message model

CoffeeSUD is a message-passing engine. Nothing of consequence happens in the
world (a step, a swing, a spell, picking up a coin, a shopkeeper's greeting) by
a method call that changes state directly. The actor builds a **message**
(`Common/interfaces/CMMsg`, normally a `DefaultMessage`), asks the room whether
it is allowed (**preview**), and then sends it (**execute**), and everything
near the event gets to veto, change or react to it. Almost all game rules are
`okMessage` and `executeMsg` methods on effects, items, behaviors, races,
classes and rooms.

### Anatomy of a message

| Part | Accessors | Meaning |
| --- | --- | --- |
| source | `source()`, `setSource(MOB)` | The MOB doing it. Never null. When no mob is involved the engine uses a factory mob (`CMLib.map().getFactoryMOB(room)` or `CMClass.getFactoryMOB()`) |
| target | `target()`, `setTarget(Environmental)` | What it is done to, or null: a mob, item, room, exit, or even an ability |
| tool | `tool()`, `setTool(Environmental)` | What it is done with, or null: the spell being cast (an `Ability`), the weapon (an `Item`) |
| three codes | `sourceCode()`, `targetCode()`, `othersCode()` | What the event is, as seen by the source, the target and everyone else in the room (see below) |
| three texts | `sourceMessage()`, `targetMessage()`, `othersMessage()`, and the `set...Message` methods | What each of those three audiences is told. A null text tells that audience nothing |
| value | `value()`, `setValue(int)` | A number the message carries: the damage in a `TYP_DAMAGE` message, the amount in a healing message, whether a saving throw was made in an attack. Defaults to 0 |
| trailers | `trailerMsgs()`, `addTrailerMsg(CMMsg)`, `trailerRunnables()`, `addTrailerRunnable(Runnable)` | Follow-up messages and jobs, run after this one has been delivered (see below) |

A **code** is an `int` with two parts. The low 11 bits (`MINOR_MASK`, 2047) are
the **minor type**, "what happened": one of the `TYP_` constants (0 to 154, plus
the 2000 to 2047 range for channels). The remaining bits (`MAJOR_MASK`) are the
**major mask**, "how it can be perceived and what it needs": a combination of
`MASK_` flags. A `MSG_` constant is a ready-made code, a minor type with its
usual flags: `MSG_GET = MASK_HANDS | TYP_GET`, `MSG_SPEAK = MASK_SOUND |
MASK_MOUTH | TYP_SPEAK`. Read them back with `sourceMinor()`, `targetMinor()`,
`othersMinor()` (the type) and `sourceMajor()`, `targetMajor()`, `othersMajor()`
(the mask), or test them with `targetMajor(int mask)` (all the bits are set),
`targetMajorAny(int mask)` (any of them) and `isTarget(int)` (and the same for
`source` and `others`).

| Mask | Value | Meaning |
| --- | --- | --- |
| `MASK_HANDS` | 2048 | uses the hands; blocked if the source cannot use its hands |
| `MASK_MOVE` | 4096 | whole-body movement |
| `MASK_EYES` | 8192 | needs sight |
| `MASK_MOUTH` | 16384 | speaking or eating |
| `MASK_SOUND` | 32768 | makes noise |
| `MASK_ALWAYS` | 65536 | must happen whatever the source's state: skips the source's ability checks, and `Room.show` does not preview it |
| `MASK_MAGIC` | 131072 | magical |
| `MASK_DELICATE` | 262144 | fine work (thief skills) |
| `MASK_MALICIOUS` | 524288 | an attack |
| `MASK_CHANNEL` | 1048576 | channel traffic |
| `MASK_OPTIMIZE` | 2097152 | a repeated message: skip expensive recomputation |
| `MASK_CNTRLMSG` | 4194304 | an internal control message, not an in-world event |
| `MASK_INTERMSG` | 8388608 | one step of a larger action |
| `MASK_SPAMMY` | 16777216 | frequent and unimportant (combat spam filtering) |

Helpful combinations: `MSK_CAST_VERBAL`, `MSK_CAST_SOMANTIC`, `MSK_CAST`,
`MSK_CAST_MALICIOUS`, `MSK_HAGGLE`.

A grouped guide to the minor types. The exhaustive list, with the matching
`MSG_` constants, is [CMMsg.java](../../CoffeeMud/com/planet_ink/coffee_mud/Common/interfaces/CMMsg.java).

| Group | Types |
| --- | --- |
| Moving | `LEAVE`, `ENTER`, `FLEE`, `RETREAT`, `RECALL`, `NOISYMOVEMENT`, `QUIETMOVEMENT`, `TRAVEL`, `ADVANCE`, `MOUNT`, `DISMOUNT`, `SIT`, `STAND`, `SLEEP`, `SITMOVE`, `FOLLOW`, `NOFOLLOW`, `GRAVITY` |
| Handling things | `GET`, `PUT`, `DROP`, `GIVE`, `WEAR`, `WIELD`, `HOLD`, `REMOVE`, `OPEN`, `CLOSE`, `LOCK`, `UNLOCK`, `PUSH`, `PULL`, `FILL`, `POUR`, `EAT`, `DRINK`, `THROW`, `ACTIVATE`, `DEACTIVATE`, `REPAIR`, `ENHANCE`, `INSTALL`, `RELOAD`, `UNLOAD`, `WAND_USE`, `DIG` |
| Commerce | `BUY`, `SELL`, `VALUE`, `LIST`, `BID`, `DEPOSIT`, `WITHDRAW`, `BORROW`, `SERVE` |
| Looking and reading | `LOOK`, `EXAMINE`, `GLANCE`, `READ`, `WASREAD`, `WRITE`, `WROTE`, `REWRITE`, `LOOK_EXITS`, `SNIFF`, `AROMA` |
| Speech | `SPEAK`, `EMOTE`, `TELL`, `ORDER`, `THINK`, `VIEW`, `DUELCHALLENGE` |
| Combat and magic | `WEAPONATTACK`, `ATTACKMISS`, `DAMAGE`, `HEALING`, `DEATH`, `CAST_SPELL`, `PREINVOKE`, `PANIC`, `PARALYZE`, `DISEASE`, `EXTINGUISH`, and the saving-throw kinds `FIRE`, `COLD`, `WATER`, `GAS`, `MIND`, `GENERAL`, `JUSTICE`, `ACID`, `ELECTRIC`, `POISON`, `UNDEAD`, `LASER`, `SONIC` |
| Character | `EXPCHANGE`, `RPXPCHANGE`, `LEVEL`, `FACTIONCHANGE`, `PRACTICE`, `TEACH`, `TRAIN`, `RECIPELEARNED`, `LIFE`, `POSSESS`, `DISPOSSESS` |
| System | `OK_ACTION` and `OK_VISUAL` (text only, no game meaning), `AREAAFFECT`, `ROOMRESET`, `EXPIRE`, `LOGIN`, `QUIT`, `SHUTDOWN`, `STARTUP`, `COMMAND`, `COMMANDFAIL`, `COMMANDREJECT`, `HUH`, `WEATHER`, `WEATHERCHANGE`, `TODCHANGE`, `DATECHANGE`, `NEWROOM`, `GENERATED`, `ITEMGENERATED`, `LEGALWARRANT`, `LEGALSTATE`, `CLANEVENT`, `HOLYEVENT`, `RITUAL` |

`TYP_OK_ACTION` and `TYP_OK_VISUAL` are the "just show this text" types. A
sound or sight that nothing needs to veto is `MSG_OK_ACTION` (audible) or
`MSG_OK_VISUAL` (visible).

### Building a message

`CMClass.getMsg` has these forms (all return a `CMMsg`; the first form that is
not "all" gives each audience its own code and text):

```java
CMClass.getMsg(source, allCode, text)
CMClass.getMsg(source, allCode, text, value)
CMClass.getMsg(source, target, allCode, text)
CMClass.getMsg(source, target, tool, allCode, text)
CMClass.getMsg(source, target, tool, sourceCode, targetCode, othersCode, text)
CMClass.getMsg(source, target, tool, allCode, sourceText, targetText, othersText)
CMClass.getMsg(source, target, tool, sourceCode, sourceText, targetCode, targetText, othersCode, othersText)
```

For example, the spell-casting message in `Spell_Shield`:

```java
final CMMsg msg = CMClass.getMsg(mob, target, this,
		verbalCastCode(mob, target, auto),
		auto ? "" : L("^S<S-NAME> invoke(s) a shield upon <T-NAMESELF>.^?"));
```

Texts are templates, not final strings. The output filter
(`Libraries/CoffeeFilter`) rewrites them separately for each reader:

| Tag | Result for reader R |
| --- | --- |
| `<S-NAME>`, `<T-NAME>`, `<O-NAME>` | The name of the source, target or tool: "you" for R itself, "someone" or "something" if R cannot see it, otherwise its `name(R)` |
| `<S-NAMESELF>`, `<T-NAMESELF>` | As NAME, but "yourself" when it is R and the actor is its own target, and "himself"/"herself"/"itself" for reflexive cases |
| `<S-HIS-HER>`, `<S-HIM-HER>`, `<S-HE-SHE>` | "your" / "you" / "you" for R itself; otherwise the pronoun for that mob's gender (`<T-...>` and `<O-...>` likewise) |
| `<S-HIM-HERSELF>`, `<S-HIS-HERSELF>` | "yourself" for R, otherwise himself or herself |
| `<S-YOUPOSS>` | "your" for R, otherwise the name with `'s` |
| `<S-IS-ARE>`, `<S-HAS-HAVE>` | "are"/"have" for R, otherwise "is"/"has" (the verb agrees with whether the subject is "you") |
| `<S-NAMENOART>`, `<S-SIRMADAM>`, `<S-MRMS>`, `<S-MISTERMADAM>`, `<S-WITHNAME>` | Name without article; sir/madam; Mr/Ms; Mister/Madam; " with <name>" |
| verb endings `invoke(s)`, `push(es)`, `cr(ys)` | Dropped for "you" or an article-less subject ("you invoke"), kept as s, es or ies otherwise ("Bob invokes") |
| `@x1`, `@x2`, ... | Variables filled by `L(text, a, b, ...)` before filtering; see [Localisation](#localisation) |
| `^S` ... `^?` | Colour codes (`^S` is the spell colour, `^?` resets); see [Presentation](11-presentation-language.md) |

Because the three texts are filtered per reader, write one template with tags
for the whole room, not a separate string per audience, unless the audiences
really need different wording.

### Sending a message: preview, then execute

The standard idiom (from `Spell_MagicMissile`):

```java
final CMMsg msg = CMClass.getMsg(mob, target, this, somaticCastCode(mob, target, auto), text);
if(mob.location().okMessage(mob, msg))          // 1. preview: may anyone veto?
{
	mob.location().send(mob, msg);              // 2. execute: everyone reacts
	if(msg.value() <= 0)                        // 3. look at what the handlers did to it
		CMLib.combat().postDamage(mob, target, this, damage, CMMsg.MASK_ALWAYS | CMMsg.TYP_CAST_SPELL,
				Weapon.TYPE_BURSTING, L("^SThe missile <DAMAGE> <T-NAME>!^?"));
}
```

`Room.show(source, target, [tool,] code, text)` builds the message, previews it
(unless the code has `MASK_ALWAYS`), sends it, and returns whether it was
allowed: the shortest way to post simple events. The `Room` interface has many
overloads (`show`, `showOthers`, `showSource`, `showHappens`, `send`,
`sendOthers`): `showOthers` leaves the source out, `showSource` tells only the
source, `showHappens` has no actor (it uses a factory mob), and `sendOthers`
skips the source's own `executeMsg`.

Posting a message to a room only reaches that room. To tell several rooms,
post to each. The area hears every message of every one of its rooms first
(`StdRoom.okMessage` begins with `getArea().okMessage(this, msg)`), which is
how area-wide properties and behaviors work.

**Preview (`okMessage`).** `StdRoom.okMessage` asks, in this order, and stops
at the first `false`: the area, the room's own rules for entering and leaving,
the source mob, every other mob in the room, every item lying in the room, the
room's effects, behaviors and scripts, and each of its exits. A mob in turn
asks its deity, its classes, its race, its effects, the items it carries, its
behaviors, its scripts and its factions, and applies its own checks (you
cannot speak if you cannot breathe, you cannot use your hands if they are
bound, a dead mob cannot act). Items ask their own effects and behaviors the same
way. A `false` from anyone means the event does not happen and nothing is
sent. The previewer is expected to tell the source why
(`msg.source().tell(...)`); a silent veto confuses players.

**Execute (`executeMsg`).** `Room.send(source, msg)` runs `executeMsg` on
the source first, then on every other mob in the room, then on the room itself
(which passes it to the area, to the items lying in the room, to its exits,
behaviors, scripts and effects), then offers it to the global monitors
(`CMLib.commands().addGlobalMonitor(MsgMonitor)`). Execute methods change the
world: they move the item, apply the damage, start the effect. They cannot veto.

**Trailers.** A handler that wants something to happen after the current event
finishes, rather than in the middle of it, attaches a trailer:

- `msg.addTrailerMsg(CMMsg)` queues another message. After the first message
  has been executed, the room previews each trailer (a trailer aimed at a dead
  mob is dropped, except a death), sends it, and processes its own trailers, to
  a depth of three. `Behaviors/ItemIdentifier` uses them to make a mob say,
  examine and hand back an item in sequence.
- `msg.addTrailerRunnable(Runnable)` queues a job. After the trailer messages,
  each runnable is given to `CMLib.threads().executeRunnable`, which in
  CoffeeSUD queues it on the game thread to run after the current job, in
  order (`Behaviors/Beggar` uses one to take the coins it was given out of its
  inventory). A runnable does not run in the middle of the event.
- `msg.suspendResumeTrailers(Boolean)` turns a message's trailers off and on.
- Trailers fire only when the message goes through `Room.send`, `sendOthers` or
  a `show` call. Calling a listener's `executeMsg` directly skips them.

**Altering a message.** The message object is shared by every handler, so a
handler can change it for those after it. Common changes: `msg.setValue(...)`
(a damage-reducing effect lowers it in `okMessage`; a save raises it by one),
`msg.setSourceMessage/TargetMessage/OthersMessage(...)` (change or blank the
text), `msg.setTarget(...)` (redirect), `msg.setTool(...)`, `msg.setSourceCode`
/ `setTargetCode` / `setOthersCode` (change what it is), and the `modify(...)`
family, which re-initialises all fields at once. Change only in `okMessage`,
which runs before anyone has acted; by `executeMsg` it is too late for the
change to affect what others decide. Do not keep a reference to a `CMMsg` for
later use and expect its fields to stay as they were: the same object is
reused by later handlers and, once nothing refers to it, `DefaultMessage`'s
finalizer clears it and returns it to a cache that `CMClass.getMsg()` draws
from. Copy out what you need. Do not call `CMClass.returnMsg` yourself unless
you built the message, are finished with it and nothing else has seen it (as
`StdElecPanel` does).

### Writing handlers

Every `MsgListener` has the same shape. The `myHost` argument is the object
the listener is attached to (for a behavior or an effect, the thing it is on);
for an `Ability` it equals `affected`.

```java
@Override
public boolean okMessage(final Environmental myHost, final CMMsg msg)
{
	// veto: only for the thing this effect is on, only for the event we care about
	if(msg.amITarget(myHost) && (msg.targetMinor() == CMMsg.TYP_GET))
	{
		msg.source().tell(L("It is stuck fast."));
		return false;
	}
	return super.okMessage(myHost, msg);          // always give the parents their say
}

@Override
public void executeMsg(final Environmental myHost, final CMMsg msg)
{
	super.executeMsg(myHost, msg);
	if(msg.amISource((MOB)myHost) && (msg.sourceMinor() == CMMsg.TYP_DEATH))
		msg.addTrailerMsg(CMClass.getMsg((MOB)myHost, CMMsg.MSG_OK_ACTION, L("<S-NAME> mutter(s) a last word.")));
}
```

Rules of thumb:

1. Always begin by testing that the message concerns you: `amITarget(myHost)`,
   `amISource(mob)`, `msg.tool() == myHost`, the minor type. Handlers run for every
   message in the room, many times a second; return `true` fast.
2. Return `super.okMessage(...)` at the end, not `true`, so inherited rules
   still apply. Call `super.executeMsg(...)` first (or last, as the class does).
3. `okMessage` must not change the world (no moves, damage or tells other than
   the explanation for a veto): the event may still be vetoed by a later handler.
   Do that work in `executeMsg`.
4. Do not post a new message from inside `okMessage`. Post it from
   `executeMsg`, or attach it as a trailer.
5. Never throw out of a handler; the engine logs the exception and the event
   dies half-processed.
6. The type of `msg.target()` is `Environmental`; cast only after `instanceof`.

To change an existing rule without editing its class, add an effect or a
behavior to the thing and let it veto or alter the message. Many `Prop_`
classes are exactly this: `Prop_ReqNoMOB` is an `okMessage` that refuses a
`TYP_ENTER` or `TYP_SIT` into its room for any mob that is not a player.

### Commands post messages too

A typed command is not special: it builds a message and sends it. `Wimpy`
just sets a value; `Say` previews and sends `MSG_SPEAK`; `Get` ends in
`CMLib.commands().postGet(mob, container, item, quiet)`, which builds
`MSG_GET`, previews and sends it. When you write a command that does one of the
common actions, call the `CMLib.commands().post...` helper rather than
building the message yourself, so every inherited rule still applies.

## Ticks, time and the game thread

### Ticks

Things that act over time implement `Tickable.tick(Tickable ticking, int
tickID)` and are registered with the thread engine:

```java
CMLib.threads().startTickDown(this, Tickable.TICKID_MISCELLANEOUS, 1);   // every tick
CMLib.threads().startTickDown(this, MY_TICK_ID, 5);                      // every 5th tick
CMLib.threads().startTickDown(this, MY_TICK_ID, 3000, 1);                // every 3000 ms
CMLib.threads().deleteTick(this, MY_TICK_ID);                            // stop it
CMLib.threads().isTicking(this, MY_TICK_ID);
```

`startTickDown(E, tickID, numTicks)` ticks `E` every `numTicks` ticks of the
standard length `CMProps.getTickMillis()` (the `TICKTIME` setting, 4000 ms).
The four-argument form takes the tick length in milliseconds. A second request
for the same object and ID is ignored. `tick` returns `true` to keep ticking
and `false` to be removed; `destroy()` on an object also removes it. The
`tickID` says which timer fired, so an object with two timers can tell them
apart; the constants are in `Tickable`:

| Constant | Used for |
| --- | --- |
| `TICKID_MOB` (0) | Every mob; also passed to the effects and behaviors a mob ticks |
| `TICKID_ITEM_BEHAVIOR` (1), `TICKID_ROOM_BEHAVIOR` (8), `TICKID_EXIT_BEHAVIOR` (11) | Hosts that tick only because they have a behavior; stops when the last one is removed |
| `TICKID_AREA` (9) | Every area |
| `TICKID_SPELL_AFFECT` (12) | Defined for effects; only a few properties (`Prop_WeakBridge`) start it. A timed effect on a non-mob host registers itself with `TICKID_MOB` (`StdAbility.addEffect`) |
| `TICKID_EXIT_REOPEN` (2), `TICKID_DEADBODY_DECAY` (3), `TICKID_LIGHT_FLICKERS` (4), `TICKID_TRAP_RESET` (5), `TICKID_TRAP_DESTRUCTION` (6), `TICKID_ITEM_BOUNCEBACK` (7), `TICKID_ROOM_ITEM_REJUV` (10) | Their named timers |
| `TICKID_QUEST` (13), `TICKID_CLAN` (14), `TICKID_CLANITEM` (15), `TICKID_LIVEAUCTION` (18), `TICKID_TIMEAUCTION` (19) | Quests, clans, auctions |
| `TICKID_MISCELLANEOUS` (20), `TICKID_PROPERTY_SPECIAL` (21), `TICKID_ELECTRONICS` (22), `TICKID_SUPPORT` (23), `TICKID_SPECIALMANEUVER` (24), `TICKID_SPECIALCOMBAT` (25), `TICKID_BALLISTICK` (26), `TICKID_EVENT` (27) | General purpose and subsystem timers |
| `TICKID_SOLITARYMASK` (65536) | OR it in to give the object a tick group of its own (the library service tickers use `TICKID_SUPPORT \| TICKID_SOLITARYMASK`); otherwise ticking objects with the same period share a group |
| `TICKID_LONGERMASK` (256), `TICKID_SHORTERMASK` (255) | Masks for sorting ids by group |

Objects that tick by the same period are batched into tick groups
(`core/threads/StdTickGroup`); `OBJSPERTHREAD` (512) caps a group's size. A group
runs all its members in turn when its time comes. `getTickStatus()` is for
diagnosis: set a status (`Tickable.STATUS_*`, or your own) so that `LIST TICKS`
and a hung-thread report can say where a tick was.

The default ticks:

- A **mob** is ticked once per tick for as long as it is in the world
  (`bringToLife` starts it). `StdMOB.tick` runs the mob's own upkeep, takes
  commands off its queue, and then ticks, in this order: its **effects**
  (`A.tick(...)`; one that returns `false` is `unInvoke()`d), its **behaviors**,
  its **scripts**. So an `Ability`'s `tick` is called by its host, not by the
  scheduler, with `tickID == TICKID_MOB`.
- An ordinary **item, room or exit** is ticked only when it has behaviors or
  scripts (adding the first one starts the tick, removing the last stops it);
  items with their own timers (lights, corpses, traps) and the doors that
  reopen start those timers themselves.
- An **area** ticks every tick.
- A timed effect counts its own life down in `StdAbility.tick`: each tick it
  decrements `tickDown`, and at zero calls `unInvoke()` and returns `false`.
  The initial value comes from `startTickDown` (and the class `classDurationModifier`).

### Time: what the CoffeeSUD clock changes

In CoffeeMud, ticks were driven by the system clock and many threads. In
CoffeeSUD there is **one game clock** (`core/GameTime`) and **one game thread**,
because a single player needs to be able to pause the world and to make time
advance only when they act.

`GameTime.now()` returns the game time in milliseconds. While the clock runs it
is the system time (same scale and origin as `System.currentTimeMillis()`); it
differs because it:

- **stands still while the game is paused** (`PAUSE`) **or turn-based**
  (`TIMEMODE TURNS`), and moves only when asked to;
- **runs faster or slower** than real time under `TIMESCALE` (0.1 to 20);
- **can be set** (a restored save sets it back to the saved time).

| Method | Meaning |
| --- | --- |
| `GameTime.now()` | The game time |
| `GameTime.isPaused()`, `isTurnBased()`, `isStopped()` | `isStopped()` is true when either is on: the clock does not move by itself |
| `GameTime.rate()`, `rateText()`, `setRate(double)`, `realMs(long gameMs)` | The `TIMESCALE` factor, and how many real milliseconds a game span takes |
| `GameTime.pause()`, `resume()`, `setTurnBased(boolean)` | What `PAUSE`, `RESUME` and `TIMEMODE` call |
| `GameTime.advance(long ms)` | Move the clock forward at once, running nothing in between |
| `GameTime.requestAdvance(long ms)`, `requestAdvance(ms, BooleanSupplier until)`, `requestAdvance(ms, until, Runnable after)` | While stopped: ask the scheduler to move the clock forward by `ms`, running everything that falls due on the way, in order, optionally stopping early when `until` says so; `Wait` and the turn-based command loop use it |
| `GameTime.isAdvancing()`, `cancelAdvance()`, `takeAdvance(long maxStep)`, `checkStop()`, `set(long)` | The scheduler's side, and loading a save |

**The rule for new code: anything that times something in the world reads
`GameTime.now()`, never `System.currentTimeMillis()`.** That includes
cooldowns, durations, expiry, decay, "when did this last happen", delays, and
anything saved with a time in it. Then pause, turn-based play and TIMESCALE
work on your feature for free. (`DefaultTimeClock`, expirations, scripts and
the ability cooldowns all use it: about 560 uses of `GameTime.now()` in
`Abilities`, `Behaviors` and `MOBS` against 20 of `System.currentTimeMillis()`.)
Keep `System.currentTimeMillis()` for the real world: logs, input timeouts, the
profile's "last played" date, thread health checks, cache expiry.

A tick-count countdown (an effect's `tickDown`, an NPC's "wait 5 ticks") needs
no clock reading at all: ticks stop when the clock stops, so the countdown
pauses with the game. A `startTickDown(..., 3000, 1)` period in milliseconds is
also measured in game time.

### The game thread

`core/threads/ServiceEngine` implements `ThreadEngine` (it is `CMLib.threads()`).
In single-player it has a driving thread, `Scheduler0`, which sets a static
`gameThread` when the engine is `RUNNING`. From then on **that thread runs the
whole game**: every tick group, every delayed job and every job handed to
`executeRunnable`, one at a time. (Before then, during boot and shutdown, work
runs on worker thread pools as in CoffeeMud.) Consequences:

- **Game code runs on one thread; you need no locks for game objects.** A tick, a
  `MsgListener`, a command's `execute`, a script and a JavaScript hook never run
  concurrently with each other. Typed commands run on this thread too: the
  session reads the typed line, puts it in the mob's command queue
  (`enqueCommand`), and `StdMOB.dequeCommand` (called from the session's loop
  and from the mob's tick) runs it when the mob has enough action points and the
  clock allows.
- **Never block it.** A `Thread.sleep`, a blocking read, a network call or a long
  loop freezes the world. Wait by being ticked: record that you are waiting,
  return from `tick`, and continue on a later tick. A command that waits in a
  blocking `session.prompt(...)` (the `MODIFY` editors do) holds the game thread
  until the answer arrives: while it waits only the queued jobs run (the wait
  loop calls `CMLib.s_sleep`, which calls `ServiceEngine.beforeSleep`), and no
  tick group runs. That is acceptable for builder screens and character
  creation, and wrong for anything a player does in play. To ask the player a
  question without stopping the world, use the session's asynchronous prompt,
  `session.prompt(InputCallback)`, which returns at once and calls you back.
  If you must sleep, call `CMLib.s_sleep(ms)` and not `Thread.sleep`: it first
  runs the jobs queued for the game thread, so a hand-off that waits for itself
  still finishes.
- **Work handed to `executeRunnable` runs later on the same thread.**
  `CMLib.threads().executeRunnable(Runnable)` (and the `String` and `char`
  group-name overloads) queue the job for the game thread, to run after the
  current job, in the order queued. It is not parallel. Use it to defer work out
  of the middle of a message or to break a long job into pieces.
- **Delayed work**: `CMLib.threads().scheduleRunnable(Runnable R, long ellapsedMs)`
  runs `R` on the game thread once `ellapsedMs` of *game time* has passed. It
  waits out a pause and stops with turn-based play.
- **Jobs that should wait for a quiet moment**: `ServiceEngine.runWhenStill(Runnable)`
  runs a job once the stopped clock has nothing left to run (the world is
  between player turns). The only caller is the `RANDOMSEED` seeding in `MUD.java`.
- **Detecting the thread**: `ServiceEngine.isGameThread()` and
  `ServiceEngine.hasGameThread()` are static. `core/ItemModifiers.gameThread()`
  refuses item mutations made elsewhere, and a save runs on the game thread.
- **Off-thread work is the exception.** Reading a file or computing something
  slow can be done on a separate `Thread`, but it must be started in the game's
  thread group (see above), may touch no game object, and must hand its result
  back with `CMLib.threads().executeRunnable(...)`. JavaScript has this packaged as
  `cm.background` ([JavaScript](05-javascript.md)).

The scheduler loop (`ServiceEngine.run`), in outline:

1. Run the queued game jobs.
2. If the clock is stopped and time has been asked for (`requestAdvance`), move
   the clock to the next thing that falls due.
3. Run every delayed job whose game time has come.
4. Find every tick group whose next time has come and run it. Groups of
   objects flagged real-time (`runsInRealTime`: the session list, so the
   console keeps reading input while the game is paused, and the engine's own
   health check) are timed by the system clock; every other group is timed by
   the game clock.
5. Run the queued game jobs again; if the clock is stopped, check whether the
   requested advance should end early, and run the "still" jobs once nothing
   is left to do.
6. Sleep until the next group or job is due (a stopped clock sleeps in 250 ms
   slices), or until woken by a queued job or a `TIMESCALE` change.

### Commands and action points

A mob does not run a command as soon as it is typed; the command waits in a
queue until the mob has paid for it. `Command.actionsCost(mob, cmds)` and
`combatActionsCost` say how many "actions" a command costs (1.0 is a whole
tick); defaults come from `DEFCMDTIME` (0.20) and `DEFCOMCMDTIME` (1.0) in
`coffeemud.ini`, with per-command exceptions
(`CMProps.getCommandActionCost(ID(), default)`). A player's free actions refill
as time passes, so with the clock stopped (paused or turn-based) a command that
costs time waits: paused, it waits for the game to resume; turn-based, the
dequeue loop asks the clock to move forward by what the command costs
(`GameTime.requestAdvance`), the world catches up, and then the command
runs. In a party, only the leader's turn moves the world
([Saves and party](31-saves-and-party.md)).

### The calendar

The game calendar is a `TimeClock` (`Common/interfaces/TimeClock`). The world
has a global one (`CMLib.time().globalClock()`) and each area has one
(`area.getTimeObj()`: its own clock, else its parent's, else the global one). One game hour is
`MILLISPERMUDHOUR` (600,000 ms by default, ten real minutes). The clock
advances through `tickTock(int hours)` when `GameTime.now()` has moved a whole
hour past `lastTicked`, so the calendar obeys pause, turn-based time and
TIMESCALE. Read it with `getHourOfDay()`, `getTODCode()` (dawn, day, dusk,
night), `getDayOfMonth()`, `getMonth()`, `getYear()`, `getSeasonCode()`,
`getMoonPhase(room)`. Dates and durations are in the
[World simulation](10-world-simulation.md) chapter. Never convert calendar time
with your own arithmetic; `TimeClock.deriveClock(millis)`, `deriveMillisAfter`
and `bump(TimePeriod, n)` exist for it.

### Catch-up

A passive area (no player has been near it for a while) stops ticking its
contents. When it wakes, CoffeeSUD calls `Behavior.catchUp(host, ticks)` and
`Ability.catchUp(host, ticks)` (default no-ops) so a behavior can do at once
what it would have done meanwhile (grow a crop, finish a journey). Implement
them if your class has a timer. `core/AreaCatchUp` is the caller.

### Game dice

The game's random numbers (`CMath.random()`, `CMLib.dice().roll(...)`,
`rollPercentage()`) are a seeded `GameRandom` whose state is saved with the
campaign. A test that needs exact reproduction starts the game with
`RANDOMSEED=42` (and `TIMEMODE=TURNS`; see [Testing](13-testing-release.md)).
