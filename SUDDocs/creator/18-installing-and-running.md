# Installing, building and running CoffeeSUD

This chapter takes you from a fresh checkout to a running game, and explains
what happens when a session starts. It covers the prerequisites, the build
scripts, the two launchers, every launch argument, the title screen and the
new-game and restore flow, the different ways to run the game (normal play,
builder mode, developer games, the headless world check and the isolated
playtest), the folders the game reads and writes, the console and the
per-character windows, scripted and headless runs, the logs, common startup
failures, and a list of what CoffeeSUD removed from CoffeeMud. It is for
everyone: world creators need the launch modes and the folder layout, scripters
and testers need the headless tools, and engine developers need the build
rules and the logs.

## In this chapter

- [Concepts: engine, world and saves](#concepts-engine-world-and-saves)
- [Prerequisites](#prerequisites)
- [Building](#building)
- [Launching the game](#launching-the-game)
- [Launch arguments](#launch-arguments)
- [What happens at startup](#what-happens-at-startup)
- [The title screen and the saved-game menu](#the-title-screen-and-the-saved-game-menu)
- [Starting a new game](#starting-a-new-game)
- [Ways to run the game](#ways-to-run-the-game)
- [The folder layout](#the-folder-layout)
- [The console and the character windows](#the-console-and-the-character-windows)
- [Quitting and shutting down](#quitting-and-shutting-down)
- [Logs](#logs)
- [Headless and scripted runs](#headless-and-scripted-runs)
- [Running more than one game](#running-more-than-one-game)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting](#troubleshooting)
- [What CoffeeSUD removed from CoffeeMud](#what-coffeesud-removed-from-coffeemud)
- [See also](#see-also)

## Concepts: engine, world and saves

CoffeeSUD is three separate things on disk. Keeping them apart answers most
questions about where a change ends up.

| Thing | Where | What it is | Who changes it |
| --- | --- | --- | --- |
| The engine | `CoffeeMud/com/planet_ink/...` (Java sources and the `.class` files built next to them), `CoffeeMud/lib/js.jar`, `CoffeeMud/resources/` | The rules of the game: classes, races, abilities, commands, help files, recipes, the fake-database driver. | Engine developers, with `build.sh`. |
| The world | `CoffeeMud/world/` (or any folder named by `WORLDDIR`) | Everything a new game starts from: areas, rooms, mobs, items, the catalog, quests, scripts, custom classes and abilities, `world.ini`. Plain text files. | World creators, in builder mode or with a text editor. |
| A campaign | `CoffeeMud/saves/<name>/` (or `SAVEDIR`) | One playthrough: the characters, the live state of the world, the game clock, the dice. | The player, with `SAVE`. |

Between them sits a cache, the **content database** (`CoffeeMud/resources/fakedb/`).
It is a copy of the world built into the engine's database tables. It is never
the source of truth: if the world files change, the cache is rebuilt from them
at the next start. Only its schema (`fakedb.schema`) is kept in git.

A normal game never writes to the world files. It plays on a **working
database**, a fresh copy of the cache made at every start (`saves/.play-<time>/`),
and a save keeps the parts of that copy that hold the game's state. Builder
mode is the opposite: it plays on the cache itself and writes the world files
back. This one distinction (normal game versus builder mode) is behind most of
the rules in this chapter.

```text
world/ files --(rebuilt when they change)--> content database (cache)
                                                 |            ^
                          normal game: copy      |            | builder mode: plays on it,
                                                 v            | SAVE writes world/ files
                                     working database  ---SAVE---> saves/<name>/
```

The launcher is `CoffeeMud/sud.sh` (`sud.bat` on Windows). It runs
[`SUD.java`](../../CoffeeMud/com/planet_ink/coffee_mud/application/SUD.java),
which reads settings, shows the title screen, asks which game to play, and then
hands over to the inherited CoffeeMud boot code in
[`MUD.java`](../../CoffeeMud/com/planet_ink/coffee_mud/application/MUD.java).
There is no server: the game runs in the process you start, with your terminal
as its one connection, and the process exits when you quit.

## Prerequisites

| Need | Detail |
| --- | --- |
| Java | A JDK (not only a JRE), version 9 or newer. The project is developed with JDK 21. `javac` and `java` must be on `PATH` for `sud.sh` and `build.sh`. |
| Rhino | `CoffeeMud/lib/js.jar` is shipped in the repository. It runs the world's JavaScript. Nothing else is needed. |
| Python 3 | Only for the tools in `tools/` (test runners, playtests, inspectors). The game itself does not need it. |
| A display | Optional. Only the per-character windows, the DEVLOG window and the PLAYTEST GAME window need one (a desktop session, or `DISPLAY` on Linux). Without one they say so and the game carries on in the terminal. |
| Memory | The launcher gives Java `-Xmx1g`. The build gives javac `-J-Xmx2g`. |
| Disk | The compiled classes are written next to the sources, so the checkout grows by the size of the `.class` files. |
| OS | Linux, macOS (POSIX shell launchers) or Windows (batch launchers). |

Why JDK 9 and not 8: `build.sh` compiles with `--release 8`, which the Java 8
compiler does not understand. The code is compiled to the Java 8 level (language and library), so the
classes are not tied to a newer runtime than the compiler's. `build.bat` does not pass `--release`, and
`findjava.bat` says "version 8 or later", but the supported statement is the
one in the repository README: a JDK of version 9 or newer.

To check your Java:

```sh
java -version
javac -version
```

On Windows, `findjava.bat` (used by `build.bat` and `sud.bat`) looks for a
working JDK in this order: the folder `JAVA_HOME` names, then each `javac` on
`PATH`, skipping any that do not run (a stale link left by an uninstalled
Java is the usual reason). If none works it prints how to set `JAVA_HOME`,
for example `setx JAVA_HOME "C:\Program Files\Java\jdk-21"`, and you must
open a new command window afterwards.

## Building

The engine is compiled in place. The class loader (`CMClass`) discovers game
classes by scanning the package folders for `.class` files, so the output has
to sit next to the sources. That is why the build is a script and not a jar.

### build.sh (Linux, macOS)

```sh
CoffeeMud/build.sh          # rebuild if any source is newer than the last good build
CoffeeMud/build.sh clean    # delete every .class file first, then rebuild
```

What the script does, in order:

1. Changes into its own folder (`CoffeeMud/`), so it can be run from anywhere.
2. Lists every `.java` file under `com/` into a temporary list
   (`.sources.<pid>.list`; one per run so games started side by side do not read
   each other's).
3. Decides whether to build:
   - If you passed `clean`, or there is no stamp file (`.build-stamp`), or the
     list of source files differs from the one in the stamp (a source file was
     added or deleted), it deletes every `.class` file and the stamp and builds
     everything. A class file left behind by a deleted source would still be
     loaded by `CMClass`, which is why a changed set of files forces a clean
     build.
   - Otherwise, if no `.java` file is newer than `.build-stamp`, it prints
     `Up to date.` and exits 0 without compiling.
   - Otherwise it rebuilds. The rebuild compiles every source file, not just
     the changed ones; the check only decides whether to build at all.
4. Runs `javac -nowarn -Xlint:none -g -encoding UTF-8 --release 8 -Xmaxerrs 1000`
   with classpath `.:./lib/js.jar`, output directory `.`, and all sources.
   javac's full output goes to `.build.log`; the script prints it with the
   noise lines ("Picked up JAVA_TOOL_OPTIONS", `[options]` warnings, "Note:",
   warning counts) removed.
5. On success it writes a checksum of the source list into `.build-stamp` and
   prints `Build OK.` On failure it prints `Build FAILED.`, leaves the stamp
   alone (so the next run builds again) and exits with javac's status.

What the messages mean:

| Message | Meaning |
| --- | --- |
| `Up to date.` | No source changed since the last good build. Nothing was compiled. |
| `Build OK.` | The compile succeeded and the stamp was written. |
| `Build FAILED.` | javac reported errors (shown above the message; the whole log is in `CoffeeMud/.build.log`). The stamp is not updated. After a *clean* attempt that failed, the class files are already gone, so the game cannot start until a build succeeds. |

A first build from clean takes about a minute. An incremental rebuild after
editing one file also recompiles everything, so it takes about as long. Build
files are listed in `.gitignore`: `*.class`, `.build-stamp`, `.build.log`,
`.sources*.list`, and the runtime logs.

`sud.sh` runs `./build.sh` (output discarded) on every launch. When nothing
changed this costs a directory scan and prints nothing. If the build fails,
`sud.sh` prints `Build failed; run ./build.sh to see why.` and exits 1 without
starting the game.

### build.bat (Windows)

```bat
CoffeeMud\build.bat
```

`build.bat` always starts clean: it deletes every `.class` file, writes a list
of all sources (doubling backslashes and quoting each path, because javac
treats a backslash in a list file as an escape), and runs javac with
`-nowarn -Xlint:none -g -encoding UTF-8 -J-Xmx1g -classpath ".;lib\js.jar"`.
It prints `Build OK.` or `Build FAILED.` and returns javac's status. There is no
`Up to date.` case and no stamp: every run is a full build of about a minute.

`sud.bat` builds only if `com\planet_ink\coffee_mud\application\SUD.class` is
missing. After pulling changes or editing Java, run `build.bat` yourself.

### Other build files

`CoffeeMud/` also contains `Makefile`, `build.xml` (Ant), `make.bat`,
`upgrade.sh` and `upgrade.bat`. These are inherited from CoffeeMud. They still
mention `lib/jzlib.jar`, which CoffeeSUD removed with the network code, and the
upgrade scripts belong to CoffeeMud's multi-file server upgrade process. Use
`build.sh` / `build.bat`; they are the supported, tested path.

### What to rebuild when

| You changed | Do |
| --- | --- |
| A `.java` file under `CoffeeMud/com/` | `build.sh` (or just start `sud.sh`, which builds). |
| A world file, `world.ini`, a script, a JavaScript file | Nothing to compile. The next start rebuilds the cache if needed. |
| A file under `resources/` (help, recipes, quest templates...) | Nothing to compile; it is read at start. |
| `coffeemud.ini` | Nothing to compile; it is read at start. |
| Added or removed a `.java` file | The build notices and cleans by itself. On Windows, `build.bat` always cleans. |
| Pulled changes from git | `build.sh`; on Windows `build.bat`. |

## Launching the game

From the repository root:

```sh
CoffeeMud/sud.sh
```

On Windows:

```bat
CoffeeMud\sud.bat
```

### What sud.sh does

```sh
cd "$(dirname "$0")"
./build.sh > /dev/null || { echo "Build failed; run ./build.sh to see why." >&2; exit 1; }
exec java -classpath ".:./lib/js.jar" -Xmx1g \
	com.planet_ink.coffee_mud.application.SUD "$@"
```

It changes into `CoffeeMud/` (so `coffeemud.ini`, `world/`, `saves/` and
`resources/` are found by relative path), builds if needed, then replaces
itself with the JVM. Every argument you give it is passed on to `SUD`. The heap
limit is fixed at `-Xmx1g`; to change it, edit the script or run the `java`
line yourself from `CoffeeMud/` with another `-Xmx`.

`sud.sh` does not run `findjava`; it needs `java` and `javac` on `PATH`. To
pass JVM options such as a system property, use the standard environment
variable:

```sh
JAVA_TOOL_OPTIONS="-Djava.awt.headless=true" CoffeeMud/sud.sh
```

(Java prints a `Picked up JAVA_TOOL_OPTIONS: ...` line to stderr when that
variable is set; that is Java, not the game.)

### What sud.bat does

`sud.bat` changes into its folder, calls `findjava.bat` (which sets `JAVA` and
`JAVAC`), builds with `build.bat` only if `SUD.class` is missing, then runs
`"%JAVA%" -classpath ".;lib\js.jar" -Xmx1g com.planet_ink.coffee_mud.application.SUD %*`.

### Running from another folder

Everything is relative to the current directory at the moment Java starts.
`coffeemud.ini` is read from the current folder, and `world`, `saves` and
`resources/fakedb` are resolved against it. Always start through the launcher
(which changes into `CoffeeMud/`), or `cd CoffeeMud` first if you run `java`
yourself. A game started from another folder fails with
`MUD/ERROR: Unable to read ini file: 'coffeemud.ini'.`

### Running java by hand

The launcher is a thin wrapper. The equivalent, from `CoffeeMud/`:

```sh
java -classpath ".:./lib/js.jar" -Xmx1g com.planet_ink.coffee_mud.application.SUD RESTORE=hero
```

The tools in `tools/` use exactly this when they need a custom JVM line
(`sudplay.py` can be told to with the `SUDPLAY_COMMAND` environment variable;
see [Headless and scripted runs](#headless-and-scripted-runs)).

## Launch arguments

Every argument to `sud.sh` is a `NAME=VALUE` setting. A setting given on the
command line overrides the same setting anywhere else, for that run only.
There are no `-flags` and no positional arguments. `NAME` is not case
sensitive (it is upper-cased); `VALUE` is taken as typed, so paths and save
names keep their case.

```sh
CoffeeMud/sud.sh RESTORE=hero TIMEMODE=TURNS RANDOMSEED=42
```

Write each setting as one word, `NAME=VALUE` with no spaces around `=`. A value
with spaces or quotes must be quoted for your shell so that it arrives as one
argument:

```sh
CoffeeMud/sud.sh 'MODULESETTINGS={"example.counter":{"step":2}}'
```

On Windows, `sud.bat` passes `%*` to the game, so quote values with double
quotes as `cmd` requires.

### Where settings come from, and which wins

The game's settings are assembled from four places. Later ones override
earlier ones:

1. `coffeemud.ini` in the `CoffeeMud/` folder (the engine's own settings).
2. A short fixed list the launcher applies for single-player play (below).
3. `WORLDDIR/world.ini`, but only the keys on the
   [world settings allowlist](world-settings.md). Any other key in it is
   reported at startup (`world.ini: KEY is not a world setting, so ignored`)
   and ignored.
4. The command line.

The fixed single-player list is: `COMMONACCOUNTSYSTEM=50` (the profile is an
account that holds all your characters), `EMAILREQ=DISABLED`,
`IDLETIMERS=0 0` (no idle logout), and `SYSMSGS=FILE`, `ERRMSGS=FILE`,
`WRNMSGS=FILE`, `DBGMSGS=FILE` (the four main log channels go to `mud.log`
only, not the screen, whatever `coffeemud.ini` says). Because the command line
comes last, you can still override any of them, for example `SYSMSGS=BOTH` to
see the info log in the terminal.

`coffeemud.ini` is read by the launcher as plain `NAME=VALUE` lines: blank
lines and lines starting `#` or `!` are skipped, the key is upper-cased and
both sides are trimmed. (It is not read as a Java properties file, so a Windows
path with backslashes stays as typed.)

`WORLDDIR` is resolved before `world.ini` is read, so it is the one setting that
cannot come from `world.ini`; take it from the command line or `coffeemud.ini`.

### Launcher arguments

These are read by `SUD` itself, before the engine boots. Boolean values are
true for `1`, `ON`, or anything starting with `Y` or `T` (`YES`, `TRUE`, `Y`,
`T`; any letter case). `0`, `OFF`, blank and anything else are false.

| Setting | Default | What it does |
| --- | --- | --- |
| `RESTORE=name` | blank | Restore the saved game called `name` without asking. A name that is not a save prints `There is no saved game called 'name'.` and exits 1. The name may also be the save's number in the list the menu would show (newest first). |
| `RESTORE=NEW` | blank | Start a new game without showing the saved-game menu. (A save literally named `new` cannot be restored this way.) |
| `FRESH=...` | blank | With `RESTORE=name` only: take some areas from the world files as they are now instead of from the save. `CHANGED` (the areas whose files changed since the save was made), `ALL`, or area names separated by commas. See [FRESH](#fresh-restoring-into-a-changed-world). |
| `SAVEDIR=path` | `saves` | The folder that holds saved games and working databases. Relative to `CoffeeMud/` unless absolute. Created if missing. |
| `WORLDDIR=path` | `world` | The folder of world files. Relative to `CoffeeMud/` unless absolute. A path starting `world/` in scripts and `LOAD=` lines always means this folder. |
| `BUILDER=1` | blank (off) | [Builder mode](#builder-mode): play on the content database as an Archon, write the world files on `SAVE` and on exit, no saved games. |
| `DEV=1` | blank (off) | [Developer's game](#developers-game-dev1): a normal game with saves, plus the developer security group and the dev areas. Ignored, with a message, in builder mode. |
| `CHECK=1` | blank (off) | [Headless world check](#headless-world-check-check1): load the world as a new game would, run `WORLD CHECK` with no player, print it, exit. Exit status 1 if there are errors, else 0. |
| `WINDOWS=1` | blank (off) | Give each of your other characters in the world a window of its own. Needs a display; otherwise the launcher prints `There is no display here, so the characters' windows (WINDOWS=1) are off.` |
| `GAMENAME=text` | `CoffeeSUD` | The game's name, shown on the title screen (`@MudInfo?NAME@` in `world/text/title.txt`) and passed to the engine as the host's name. A world may set it in `world.ini`. `coffeemud.ini` also has `MUD_NAME` (shipped as `ROTLOVE`, a leftover), which the engine's own texts use as the mud's name when it is non-empty; set both if you rename a game. |
| `MODULES=ids` | blank | Optional world modules to select, by id. See [Optional modules](14-optional-modules.md). |
| `MODULESETTINGS={...}` | blank | A JSON object of per-module setting overrides. |

`MODULES` and `MODULESETTINGS` are checked before anything else is touched. If
the selection is invalid, or a restored save is incompatible with it, the
launcher prints `Optional modules: <reason>`, releases the save folder and exits
1. Builder mode and `CHECK=1` are exempt so that you can inspect and fix a
broken module definition.

### Engine settings worth knowing at launch

Any key of `coffeemud.ini` can be given on the command line. These are the
ones used most when launching; each is explained in its own chapter. "New game
only" means a restored game keeps the value saved with it.

| Setting | Default in `coffeemud.ini` | Effect | Chapter |
| --- | --- | --- | --- |
| `TIMEMODE=REALTIME` / `TURNS` | `REALTIME` | How time passes at start. Anything starting `TURN` means turn-based. A restored game keeps the mode it was saved in. | [10](10-world-simulation.md) |
| `TIMESCALE=n` | `1` | Run real time faster or slower, from `0.1` to `20` (values outside are clamped). | [10](10-world-simulation.md) |
| `RANDOMSEED=n` | blank | A whole number that seeds the dice when the world loads and again when play starts. Not applied to a restored game (it restores the saved dice). A non-number is logged as an error and ignored. With `TIMEMODE=TURNS` the same seed and the same commands play the same game: things tick in the order they first came into the game, so a change elsewhere in the engine does not reorder them. | [13](13-testing-release.md) |
| `SAVEMODE=` | `ANYWHERE` | `ANYWHERE`, `NOTINCOMBAT`, `SAVEPOINTS` or `IRONMAN`. New game only. | [31](31-saves-and-party.md) |
| `AUTOSAVE=`, `AUTOSAVEKEEP=` | blank, `3` | Autosave every N game minutes, or `ON-AREA-CHANGE`. Not in builder mode. | [31](31-saves-and-party.md) |
| `PARTYMEET=1`, `PARTYFIGHT=1`, `PARTYTURNS=LEADER`/`ALL` | blank, blank, `LEADER` | Party behaviour at start. New game only; `PARTYMEET` and `PARTYFIGHT` are on for `1`, `ON`, or any word starting `Y` or `T`. | [03](03-characters.md) |
| `DEVGROUP=`, `DEVSTART=`, `DEVLOG=` | unset | Developer's-game and builder-mode helpers. | [19](19-configuration-and-administration.md) |
| `RECORDINPUT=1` | `0` | Write every typed line to a replayable `.play` file in `SAVEDIR/recordings/`. | [13](13-testing-release.md) |
| `EDITOR=LINE`/`WINDOW`/`EXTERNAL`, `EDITORCOMMAND=` | `LINE` | Where builders edit scripts and descriptions. | [19](19-configuration-and-administration.md) |
| `BUILDERAUTOSAVE=1`, `BACKUPS=n` | `0`, `5` | Builder-mode saving behaviour. | [02](02-world-workflow.md) |
| `WINDOWPANEL=STATUS,MAP,PARTY` | unset | Panel beside each character window. | [31](31-saves-and-party.md) |
| `SCRIPTERRORS=LOG`/`NOTICE`/`SHOW`, `SCRIPTSLOW=ms` | `LOG`, `0` | What happens on a script error; slow-script warnings. | [04](04-mobprog.md) |
| `JSCRIPTS=0`/`1`/`2` | `2` | JavaScript policy. `2` always runs scripts. | [05](05-javascript.md) |
| `DBSERVICE=jdbc:fakedb:path` | `jdbc:fakedb:resources/fakedb` | The content database (the cache) to use. See below. | this chapter |
| `NUMLOGS=n`, `SYSMSGS=`, `ERRMSGS=`, `WRNMSGS=`, `DBGMSGS=` | `10`, then `FILE` (see above) | Log rotation and where each channel goes. | [Logs](#logs) |
| `CHARSETINPUT=`, `CHARSETOUTPUT=` | `iso-8859-1` | Character sets for what you type and what is printed. Set both to `UTF-8` if your terminal is UTF-8 and the text has non-ASCII characters. | this chapter |

For the full ini reference see [Configuration and administration](19-configuration-and-administration.md)
and the [world settings](world-settings.md) allowlist.

### DBSERVICE: the content database

`DBSERVICE` names the cache of the world. It must start with `jdbc:fakedb:`
followed by a folder. The folder must contain `fakedb.schema`; a new one is
made by copying `CoffeeMud/resources/fakedb/fakedb.schema` into an empty
folder, which is what every test tool does:

```sh
mkdir -p /tmp/db && cp CoffeeMud/resources/fakedb/fakedb.schema /tmp/db/
CoffeeMud/sud.sh DBSERVICE=jdbc:fakedb:/tmp/db
```

If `DBSERVICE` names anything else (a real JDBC database), the launcher
continues but prints `The world files and saved games need the fake database,
so they are off.` and logs an error. In practice, never change the driver.
The cache is also what builder mode keeps its characters in, so a separate
world should have a separate `DBSERVICE` (see
[Running more than one game](#running-more-than-one-game)).

### FRESH: restoring into a changed world

A save records a checksum of the world files and of each area file at the time
it was made. When you restore it against a world that has changed since, the
game tells you at login (`This save was made with another version of the
world:` and a list of new, changed or removed area files). By default the save
wins: rooms are put back as they were saved, so your edits to area files do not
show. `FRESH=` changes that for the run:

| Value | Takes from the current world files |
| --- | --- |
| `CHANGED` | The areas whose files differ from when the save was made. |
| `ALL` | Every area. |
| `Area One,Area Two` | The named areas (case-insensitive, comma-separated). |

The in-game form is `RESTORE name FRESH=CHANGED`. See
[Saves and party](31-saves-and-party.md).

### Inherited launch arguments

The engine's `MUD.main` still understands a few arguments from CoffeeMud,
which `SUD` passes through. Only these matter here:

| Argument | Status in CoffeeSUD |
| --- | --- |
| `NAME:0=VALUE` | The `:0` suffix addresses host group 0, which is the only group. `NAME:1=VALUE` and higher are ignored. Just write `NAME=VALUE`. |
| `BOOT=other.ini` | Inherited: boots from another ini file. `SUD` itself always reads `coffeemud.ini` for the settings above, so `BOOT=` splits the two and is not supported. Copy and edit `coffeemud.ini`, or override on the command line. |
| A word with no `=` | Not a setting. It is folded into the host's name (unverified in play). Avoid it. |

### Java system properties

These are `-D` options, passed with `JAVA_TOOL_OPTIONS` or a hand-written
`java` line, not `NAME=VALUE`:

| Property | Effect |
| --- | --- |
| `-Dsud.log.prefix=/path/prefix` | Where the log files go and what they are called: the log is `/path/prefix.log` (and `prefix1.log`, ...). Default `mud` in the current folder. The isolated playtest uses it. |
| `-Dsud.isolated.playtest=true` | Set by `PLAYTEST GAME` in its child process: gives the character the mortal-playtest property. Not for general use. |
| `-Dsud.python=python3` | The Python the in-game `PLAYTEST RUN` / `WORLD TEST` uses to start `tools/playtest.py`. Default `python3` (`python` on Windows). |
| `-Djava.awt.headless=true` | Forces "no display", so windows stay off even on a desktop. Test scripts set this. |

### Common command lines

```sh
CoffeeMud/sud.sh                                  # menu of saves, or a new game
CoffeeMud/sud.sh RESTORE=hero                     # continue a save, no menu
CoffeeMud/sud.sh RESTORE=NEW                      # new game, no menu
CoffeeMud/sud.sh RESTORE=hero FRESH=CHANGED       # continue, taking changed areas from the files
CoffeeMud/sud.sh TIMEMODE=TURNS RANDOMSEED=42     # turn-based, repeatable dice (new game)
CoffeeMud/sud.sh WINDOWS=1                        # a window for each other character
CoffeeMud/sud.sh BUILDER=1                        # edit the world
CoffeeMud/sud.sh DEV=1                            # play with developer powers
CoffeeMud/sud.sh CHECK=1                          # lint the world, exit 1 on errors
CoffeeMud/sud.sh SYSMSGS=BOTH ERRMSGS=BOTH        # also show the log on screen
CoffeeMud/sud.sh WORLDDIR=/w/world DBSERVICE=jdbc:fakedb:/w/db SAVEDIR=/w/saves   # another world
```

## What happens at startup

This is the order of events from `sud.sh` to the first prompt, which is also
the order in which things can go wrong.

1. **Build check.** `sud.sh` runs `build.sh`. (Windows: only if `SUD.class` is
   missing.)
2. **Settings.** `SUD` reads `coffeemud.ini`, then `WORLDDIR/world.ini`, then the
   command line (see [Launch arguments](#launch-arguments)). Keys in `world.ini`
   that are not on the allowlist are reported here.
3. **Save-folder lock** (not in builder mode). `SUD` locks `SAVEDIR/.owner.lock`
   for the life of the process, so two games cannot share a save folder. It then
   repairs any save that an earlier crash left half-replaced (`.old-NAME` and
   `.new-NAME` folders; see [Folder layout](#the-folder-layout)). If another
   process holds the lock: `Another game is using the save folder <path>. Close
   it or choose a different SAVEDIR.` and exit 1.
4. **Modes.** `DEV=1`, `WINDOWS=1` and `CHECK=1` take effect (messages are printed
   for each). In builder mode a stray `DEV=1` prints `DEV=1 is for a normal
   game: builder mode has every power already.` and is ignored.
5. **Title screen** (not for `CHECK=1`): `world/text/title.txt`, if the world has
   one.
6. **Which game.** Builder mode prints `Builder mode: changes go straight into
   the world, and there are no saved games.` Otherwise `RESTORE=` decides, or the
   saved-game menu asks. For a new game, `world/text/prologue.txt` is shown.
7. **Module check.** `MODULES` / `MODULESETTINGS` are validated against the save
   being restored (see above). A problem exits 1.
8. **Boot.** `Loading the world...` is printed and the inherited `MUD.main` takes
   over: it installs the JavaScript runtime, opens the log (rolling the previous
   logs), and then, before each boot, `SaveGame.beforeBoot`:
   - decides if the cache is current by comparing the checksum of the world files
     with `world.sum` in the cache folder;
   - in a normal game makes the working database, `SAVEDIR/.play-<time>/`, from the
     cache's content tables (or from nothing, if the cache is out of date) plus,
     when restoring, the save's state tables;
   - sets the game clock, time mode and pause from the save, when restoring.
   If the world files changed, you see `The world files have changed: rebuilding
   the world from them...` and the areas, catalog, quests and custom objects are
   imported from the files (this is slower than a normal start). A normal game then
   copies the built tables back to the cache so the next start is quick.
9. **Engine load.** Classes are scanned and registered (the counts are in
   `mud.log`: races, classes, abilities, commands and so on), expertises, socials
   and help are read, the catalog and map are loaded, the quests start.
   `Initialization complete.` is logged.
10. **The console session.** One `ConsoleSession` is attached to your terminal.
    `SaveGame.afterBoot` runs: clocks are read, `world/js/init.js` is read and its
    `onBoot()` called, and, when restoring, the saved world state is put back
    over the freshly built one, then the saved dice. Then the intro text is shown:
    `Restored 'name'.` for a restore, nothing extra if the world has a
    `title.txt` (the launcher showed it), otherwise `resources/text/intro.txt`.
11. **Login.** A restored game logs the saved character straight in. A new game
    asks for the first character's name and creates it (see
    [Starting a new game](#starting-a-new-game)). Your first prompt is the game.
12. **After login** (`SaveGame.playerLoggedIn`): reports of what changed in the world
    since a save was made; the optional-module notes; in builder mode and DEV games
    the DEVLOG filter, and in builder mode a `QUESTCHECK` summary; the AUTOSAVE
    ticker (not in builder mode);
    the world's scripts start (`GAME_PROG NEWGAME` for a new game, `RESTORE` for a
    restore, and `onPlayerEnter` in `world/js/init.js`).

If the process is killed during step 8 or 9, nothing in `world/` or in any save
has been written (a normal game never writes the world files), and the next
start clears the leftover `.play-*` folder.

## The title screen and the saved-game menu

### world/text/title.txt

If `world/text/title.txt` exists, `SUD` prints it first. Inside it,
`@MudInfo?NAME@` becomes the game's name (`GAMENAME`) and these colour codes work:

| Code | Colour | Code | Colour |
| --- | --- | --- | --- |
| `^w` | white | `^W` | bright white |
| `^r` | red | `^R` | bright red |
| `^g` | green | `^G` | bright green |
| `^y` | yellow | `^Y` | bright yellow |
| `^b` | blue | `^B` | bright blue |
| `^p` | purple | `^P` | bright purple |
| `^c` | cyan | `^C` | bright cyan |
| `^k` | black | `^K` | bright black (dark grey) |

`^N` or `^?` returns to the normal colour, and `^^` prints a literal `^`. The
screen always ends in the normal colour. Without a `title.txt`, the game shows
the inherited coffee-cup picture from `resources/text/intro.txt` when a new
character is about to be made.

### The menu

When there is at least one saved game and `RESTORE=` is not set, the launcher
lists them, newest first, and asks:

```text
  C) Continue - hero, level 1 Apprentice, in Rocky Room, saved Nov 6 10:08
  N) New game
  Q) Quit
Saved games:
   1) hero         hero, level 1 Apprentice, in Rocky Room, saved Nov 6 10:08
   2) ironbark     Ironbark, level 7 Fighter, with Mira, in Harbour Gate, saved Nov 5 21:40 [dev]
Choose C, N or Q, or a saved game's number or name (Enter continues):
```

| You type | Result |
| --- | --- |
| Enter, `C`, `CONTINUE` | Restore the newest save (line 1 of the list). |
| `N`, `NEW` | Start a new game (and show the prologue). |
| `Q`, `QUIT` | Exit with status 0. |
| A number | Restore that save from the list. |
| A name | Restore that save (not case sensitive). |
| Anything else | `There is no saved game called 'x'.` and ask again. |

If the input ends (end of file) the menu returns "new game". With no saves at
all the menu is skipped and a new game starts.

Each line of the list is made from the save's header: the character's name,
level and class, the party members, the room's title, when it was saved, and
the tags `[dev]` (made in a `DEV=1` game) and `[cheat]` (cheats were on when it
was saved). Before restoring, the launcher checks the save is whole: a save
that is incomplete, damaged, or from a newer format prints a message such as
`The save 'hero' is incomplete or damaged.` or `Unsupported save format 3 in
'hero'. This engine supports versions 1 and 2.` and exits 1. Nothing is changed.

### world/text/prologue.txt

For a new game only, `world/text/prologue.txt` is shown after the title and
before the engine boots. Put a line of three or more dashes between pages;
the launcher prints one page, then `[Enter] `, then the next. The same colour
codes work. For a prologue that is a script, or that runs after the character
exists, see `CHARCREATIONSCRIPTS` and `GAME_PROG NEWCHAR` in the
[MOBPROG chapter](04-mobprog.md).

## Starting a new game

A normal game starts with an empty profile, so the first thing it asks for is
the first character's name. The exact questions depend on the world's settings
(creation templates, origins, kits, traits and any creation scripts: see
[Characters](03-characters.md)). With the shipped world the sequence is, in
outline (the menus and wording are abridged here):

```text
name: Hero
Create a new character called 'Hero' (y/N)? y
Do you want ANSI colors (Y/n)? y
(race list)  : Human
Is Human correct (Y/n)? y
What is your gender (M/F/N)? m
(stat rolls)  Would you like to re-roll (y/N)? n
(class list)  : Apprentice
Is Apprentice correct (Y/n)? y
(alignment)   good
...
Character creation complete!
```

The colour answer is asked once, for the first character, and is kept for the
characters made later in the same profile. The character then enters the world
at the room named by `START` in `world/world.ini` (or by `START_<race>` and the
mask forms; see [world settings](world-settings.md)).

Things to know:

- The dice for the stat rolls come from the game's random generator. With
  `RANDOMSEED=n` and `TIMEMODE=TURNS` the same answers give the same character.
  The shipped smoke test fixes the seed because, for some rolls, the Apprentice
  class is the only one the stats allow.
- A new game has no save yet. `SAVE` with no name saves under the character's
  name in lower case, keeping only letters, digits, `-` and `_` (`Hero` is saved
  as `hero`); `QUIT` offers the same name. After the first save, the game
  remembers its slot and a bare `SAVE` writes to it again.
- Save names are 1 to 32 characters of letters, digits, `-` and `_`, and must
  start with a letter or digit.
- The profile is the single account that holds all your characters. `PROFILE`
  lists them, `SWITCH name` plays another, and `LOGOFF` goes to the characters
  menu (`L` list, `N` new character, `D` delete or retire, `H` help, `Q` quit, or
  type a name to play it). See [Saves and party](31-saves-and-party.md).
- In builder mode the characters live in the cache (`resources/fakedb`), so they
  persist between launches; the second builder launch goes straight to the
  characters menu, and you type the builder character's name to play it.

### Restoring a game

`RESTORE=name`, the menu, or the in-game `RESTORE name` all do the same thing:
boot a fresh copy of the world's content, lay the save's state tables over it,
restore the live world from `world.xml`, put the dice back, and log the saved
character in. In-game `RESTORE` restarts the engine inside the same process;
the launcher's menu is not shown again. A restored game keeps its own time mode,
pause, save mode, party settings and difficulty, whatever the launch arguments
say. `TIMEMODE`, `PARTYTURNS`, `RANDOMSEED` and similar launch settings apply to
new games only.

Things a restore reports at login:

- `This save was made with another version of the world:` followed by new,
  changed or removed area files, and the hint
  `RESTORE name FRESH=CHANGED takes the changed areas' rooms as they are now.`
- `The save's optional modules:` notes (see [Optional modules](14-optional-modules.md)).

## Ways to run the game

There are five ways to run CoffeeSUD. They share the engine and differ in what
the game plays on, what it writes, and what powers you have.

| | Normal game | Builder mode | Developer's game | World check | Isolated playtest |
| --- | --- | --- | --- | --- | --- |
| Launch | `sud.sh` | `sud.sh BUILDER=1` | `sud.sh DEV=1` | `sud.sh CHECK=1` | `PLAYTEST GAME` in builder mode |
| Plays on | A working copy of the cache, new each start | The cache itself | A working copy | A scratch copy, no player | A copy of the world, database and saves in a temp folder, in a second process |
| World files written | Never | On `SAVE` and on shutdown | Never | Never | Never; the copy is deleted when the window closes |
| Saved games | Yes | None (the world files are the "save") | Yes, tagged `[dev]` | None | One, `playtest`, in the temp folder |
| Your powers | An ordinary character | An Archon, via the Archon's manual | The `DEV` security group | None | An ordinary mortal |
| `world/dev/` areas | Not loaded | Loaded | Loaded | Not loaded | Not loaded |
| Autosave | Yes, if `AUTOSAVE` is set | No | Yes | No | Yes, if set |
| Characters | In the saves | In the cache (persist) | In the saves | None | In the temp saves |
| Exit status | 0 | 0 | 0 | 1 if the check found errors, else 0 | n/a |

### Normal game

This is the default: you play a character, with saves. Nothing you or the
world do changes `world/`. Building commands that a character with the right
security can use (`CREATE`, `MODIFY`, `DESTROY`, `GMODIFY`, `IMPORT`...) change
only that game's copy, and say so (`This game plays on a copy of the world.`).
The editors of the game's data files (help, socials, achievements, titles,
awards, factions, holidays, expertises, components, planes) are refused in a
normal game, because their output lives in `resources/` and a save does not
keep it; use builder mode for those.

### Builder mode

```sh
CoffeeMud/sud.sh BUILDER=1
```

Builder mode is how you write a world. The game plays on the content database
itself and makes you an Archon at login by having your character read the
Archon's manual (`Builder mode makes you an Archon, with the in-game editors.`).
At login you also see a line about the quest check
(`QUESTCHECK: n problems in world/quests.`), whether the world files loaded
whole, how many were changed outside the game, how many rooms have unsaved mobs
or items, and a pointer to `AHELP BUILDING`.

- **What is written, and when.** The in-game editors change the cache.
  `SAVE` (and `SAVE ROOM`, `SAVE AREA`, `SAVE CHANGED`, `SAVE FORCE`) write the
  world files, and so does a normal exit. Only files that changed are rewritten.
  Mobs and items you `CREATE` belong to a room only after `SAVE ROOM`; `LIST
  UNSAVED` and the `QUIT` prompt tell you about unsaved ones. `BUILDERAUTOSAVE=1`
  does it after each building command. See [World workflow](02-world-workflow.md).
- **Protection.** A world file that failed to load whole is never written over. A
  file that was edited outside the game after the game loaded it is not
  overwritten either (it is kept; the game's copy is written beside it with a
  `.builder` extension); `WORLD STATUS` lists them. `BACKUPS=5` keeps copies of
  each file `SAVE` replaces, in `world/.backup/`.
- **No saved games.** `SAVE` means "write the world". There is no `RESTORE`.
- **Undo.** `UNDO` reverses the last building commands.
- **Tools.** Builder mode has `WORLD` (STATUS, DIFF, RELOAD, CHECK,
  BACKUPS...), `PLAYTEST` (builder mode only), `DEVWATCH`, `SCRIPTCHECK`,
  `PROPCHECK`, and the tools it shares with DEV games: `DEVLOG`, `QUESTCHECK`,
  `RECORD INPUT`, `TIMESCALE`, `SCRIPTERRORS`, `ASSERT`, `GOD`.
- **Characters.** Builder characters are stored in the cache, not in any save,
  so they last until the cache folder is deleted, and they are not in normal
  games.
- **One at a time per cache.** Builder mode has no save-folder lock. Two builder
  games on the same `DBSERVICE` and `WORLDDIR` would write the same files and
  tables. Use a different `DBSERVICE` (and `WORLDDIR`) for each.
- **JavaScript approvals.** Under `JSCRIPTS=1`, scripts are approved in builder
  mode and the approvals are written to `jscripts.ini` in the world folder.

### Developer's game (DEV=1)

```sh
CoffeeMud/sud.sh DEV=1
```

A developer's game is a normal game, with saves and a normal character, plus the
tools to test the world as it plays. It is not builder mode: the world is not
changed, and the class does not change.

- Each character gets the security group named by `DEVGROUP` (the group `DEV` by
  default, defined as `GROUP_DEV` in `coffeemud.ini`:
  `GOTO, AT, TRANSFER, STAT, WHERE, LISTADMIN, CMDQUESTS, CMDMOBS, JSCRIPTS, AHELP,
  FS: RESOURCES`). The group deliberately has no `IMMORT` or `ORDER`, which would
  make testing unlike play. You are told at login
  (`Developer's game (DEV=1): you have the DEV security group, to test with, and
  saves are marked [dev].`).
- Saves made in it are tagged `[dev]` in the list. Restoring such a save in a game
  without `DEV=1` takes the group away again, so a dev save does not carry powers
  into normal play.
- `world/dev/` areas are loaded (they never are in a normal game and are left out
  of the world checksum, so they do not make a normal game rebuild its cache).
- `DEVSTART=<room id>` makes new characters start in a room such as a dev hub.
- `DEVLOG`, `SCRIPTERRORS`, `TIMESCALE`, `GOD` (cheat toggles), `RECORD INPUT`,
  `ASSERT`, `QUESTCHECK` and `WORLD CHECK` / `WORLD TEST` work, as in builder
  mode (`PLAYTEST`, including `PLAYTEST RUN`, is builder mode only). Scripts can
  ask `ISDEV()`. `SCRIPTTRACE` (needs `CMDMOBS` or `CMDROOMS`) and `VARS` (needs
  `CMDQUESTS`) depend on security flags, not on the mode; the DEV group has both
  `CMDMOBS` and `CMDQUESTS`.
- Use it to play-test with extra eyes without risking the world, and for the test
  runners (`tools/playtest.py` plays every `.play` file as a `DEV=1` game).

### Headless world check (CHECK=1)

```sh
CoffeeMud/sud.sh CHECK=1 SAVEDIR=/tmp/check-saves
echo $?
```

`CHECK=1` loads the world exactly as a new game would, on a scratch copy, runs
`WORLD CHECK` with no player, prints the report and exits. No title screen, menu
or login appears. The report looks like:

```text
WORLD CHECK: 1 errors, 2 warnings.
Errors:
  [exits] New Area#3 north: an exit to nowhere
Warnings:
  [text] New Area#1: StdRoom's default description
  [text] New Area#2: tagged TODO, as WORLD DIG or PATH made it; EDIT DESC gives it its own text
```

The exit status is 1 when there is at least one error and 0 otherwise, so it fits
in a script or a CI job. The parts of the check are `EXITS`, `REACH`, `KEYS`,
`TEXT`, `SHOPS`, `DATA`, `SCRIPTS`, `PROPS`, `JS`, `VOCAB`, `LORE`, `TEMPLATES`,
`ROUTES`, `IDS`, `MODULES`, `COMPOSITIONS`, `COMBAT` and `ITEMMODS`. The same check
is available in the game as `WORLD CHECK` (builder mode or DEV), where `ONLY=`
picks parts. `CHECK=1` still takes the save-folder lock, so give it its own
`SAVEDIR` if a game is running on the default one.

### Isolated playtest (PLAYTEST GAME)

In builder mode, `PLAYTEST GAME` starts a disposable campaign of your unsaved
world in a second Java process. It:

1. copies the world folder to a temp folder (`sud-playtest-*`);
2. applies your current unsaved builder state on top of the copy;
3. exports your character and party as a save called `playtest` in that folder;
4. starts a second `SUD` there with `BUILDER=0 DEV=0 RESTORE=playtest WINDOWS=0`
   and its own `WORLDDIR`, `SAVEDIR` and `DBSERVICE`, `-Dsud.isolated.playtest=true`
   and its log in `<temp>/logs/mud.log`;
5. shows it in a window titled "Isolated playtest - close to discard".

You meet the world as a mortal, with no powers; a death returns you to where the
playtest began. Closing the window kills the process and deletes the temp folder,
and your builder world is untouched. With no display it does not open a window;
it prints the exact command to run in another terminal and the temp folder to
delete afterwards.

This is different from the in-builder `PLAYTEST ON`/`OFF`, which suspends your
powers in place but keeps the changes you make, and from `PLAYTEST RUN [FILE]` /
`WORLD TEST`, which run the files in `world/tests/` in disposable campaigns and
report PASS or FAIL. See [Testing and release](13-testing-release.md).

## The folder layout

### The repository

| Path | What is there |
| --- | --- |
| `CoffeeMud/` | The engine and the launchers. The game's working directory. |
| `CoffeeMud/world/` | The world a new game starts from (below). |
| `tools/` | Test runners, fixtures and inspectors (Python and shell). |
| `SUDDocs/creator/` | This handbook. |
| `archive/oldenginedocs/` | An analysis of CoffeeMud's engine, written before the fork. |
| `audit/` | The feature audit and per-theme reports. |
| `archive/mudgrinder/` | CoffeeMud's web editor, kept for reference only. |
| `PLAN.md`, `DEVPLAN.md`, `LATER.md` | How CoffeeMud became CoffeeSUD, the capabilities added since, and deferred work. |

### Inside CoffeeMud/

| Path | What is there | In git? |
| --- | --- | --- |
| `sud.sh`, `sud.bat` | The launchers. | yes |
| `build.sh`, `build.bat`, `findjava.bat` | The build scripts and the Windows Java finder. | yes |
| `coffeemud.ini` | Engine settings, with comments. Read from the current folder. | yes |
| `com/planet_ink/coffee_mud/...` | Java sources, with the compiled `.class` files beside them. | sources only |
| `lib/js.jar` | Rhino, the JavaScript engine. | yes |
| `resources/` | The engine's data files: `help/`, `text/`, `quests/`, `randareas/`, `skills/`, `factions/`, `progs/`, `js/`, `namegen/`, `examples/`, and ini/text files such as `socials.txt`, `achievements.ini`, `currencies.ini`, `laws.ini`, `titles.ini`. | yes |
| `resources/fakedb/` | The content database (the cache). Rebuilt from `world/`. | schema only (`fakedb.schema`) |
| `world/` | The world files. | yes |
| `saves/` | Saved games and working databases (`SAVEDIR`). | no |
| `guides/` | The HTML manuals (inherited CoffeeMud guides and the `*SUD.html` supplements). | yes |
| `mud.log`, `mud1.log` ... | The game logs. | no |
| `.build-stamp`, `.build.log`, `.sources*.list` | Build bookkeeping. | no |
| `SQLErrors.que` | An inherited database-error queue; it may appear and is ignored by git. | no |
| `Makefile`, `build.xml`, `make.bat`, `upgrade.sh`, `upgrade.bat` | Inherited CoffeeMud build and upgrade files; not used. | yes |

### Inside a world folder

`WORLDDIR` (default `world/`) holds what a new game starts from. The files
and their formats are the subject of [World workflow](02-world-workflow.md);
this is the map.

| Path | Contents |
| --- | --- |
| `world.ini` | The settings that make this world the game it is (start room, calendar, rules). Only allowlisted keys: see [world settings](world-settings.md). |
| `areas/NAME.cmare` | One file per area: its rooms, exits, and the mobs and items placed in them. |
| `dev/NAME.cmare` | Areas for building and testing, loaded only in builder mode and `DEV=1` games. |
| `custom/races/`, `classes/`, `abilities/`, `commands/`, `behaviors/` | Custom (generic) races, classes, abilities, commands and behaviors, one file each, with long scripts beside them as `.script` files. (An older single `custom.cmare` is still read.) |
| `catalog.cmare` | The catalog of reusable mobs and items. |
| `space.cmare` | Things in space that are in no room. |
| `templates.cmare`, `clans.cmare` | Object templates and NPC clans. |
| `quests/NAME.quest` | One quest script each. |
| `journals/`, `vfs/` | Board posts and files in the engine's virtual file system. |
| `js/` | World JavaScript: `init.js`, and `behaviors/`, `abilities/`, `commands/`... folders of JS-defined classes. |
| `scripts/`, `routes/` | Script and route files that belong to the world (tracked in its version like the JavaScript folders; see [World workflow](02-world-workflow.md)). |
| `classes/<kind>/` | Compiled `.class` (or `.js`) engine classes for this world, added after the stock ones. |
| `resources/` | The world's own copy of any file in the engine's `resources/` (help, socials, titles, recipes...). A file here is read instead of the stock one; `NAME.add` adds lines to it. |
| `text/` | Text screens: `title.txt`, `prologue.txt`, `panel.txt` and others. |
| `vocab/`, `lore/`, `kits/`, `encounters/`, `modules/`, `compositions/`, `combat/`, `itemmods/` | Language vocabulary, lore, character kits, encounters and the optional-module sources. |
| `score.txt`, `ranks.ini`, `crops.txt`, `resources.txt`, `materials.txt`, `creation.ini`, `origins.ini`, `perks.ini`, `combinations.txt`, `itemsets.txt`, `affixes.xml` | Single-file world assets. |
| `tests/` | `.play`, `.test` and `.js` playtest files (`tools/playtest.sh` runs them all). |
| `jscripts.ini` | JavaScript approvals (used with `JSCRIPTS=1`), written in builder mode. |
| `.backup/` | Copies of world files that builder `SAVE` replaced (`BACKUPS=n` kept). |
| `.history/` | The `UNDO` log. |

The shipped `world/` is deliberately small: two rooms in one area
(`areas/new-area.cmare`), one quest, and a start playtest in `tests/`. Writing
the world is the creator's job.

Not every one of these folders must exist; the engine reads what is there. A
change to any file that is part of the world version makes the cache rebuild at
the next start and makes saves report a changed world (see
[Saves and party](31-saves-and-party.md)).

### The cache: resources/fakedb

The folder holds one `fakedb.data.<TABLE>` file per table, `fakedb.schema`, and
`world.sum`, the checksum of the world files the content tables were built
from (`3-<file count>-<crc>`). If the checksum differs from the current world
files, or `world.sum` is missing, the next start rebuilds. `dev.sum` does the
same for the dev areas in builder mode. Deleting everything but `fakedb.schema`
is always safe; the next start rebuilds the content (and, in builder mode,
forgets the builder characters). `worldproblems.txt` in the cache folder lists
files that did not load whole.

### The saves folder

```text
saves/
  .owner.lock            held by the running game; never delete while a game runs
  hero/                  one saved game (the slot name is the folder name)
    header.properties    who, where, when, the game clock, time mode, dice, party
    db/                  the saved state tables (characters, items, accounts, journals, clans)
    world.xml            the live world: rooms' contents, dead mobs, doors, weather...
    modules.json         optional-module state (format 2 saves)
    world-manifest.properties   the world files' checksums the save was made with
    .complete            a SHA-256 manifest proving the save is whole
  autosave1/ autosave2/  rotating autosaves (AUTOSAVEKEEP of them)
  recordings/            RECORDINPUT / RECORD INPUT files (.play)
  .play-1730000000000/   the running game's working database (deleted at exit)
  .old-hero/ .new-hero/  a save being replaced; repaired at the next start
  .damaged-hero-<time>/  a save found damaged and set aside by that repair
```

A save is written to `.new-NAME` first, flushed, then swapped in, with the old
one kept as `.old-NAME` until the next write. A crash between the two renames is
repaired at the next start (preferring the last whole save). A save is a folder,
so copying a save is copying the folder; copy it while no game is using that
`SAVEDIR`. Save and restore details are in
[Saves and party](31-saves-and-party.md) and
[State and persistence](state-and-persistence.md).

## The console and the character windows

### The console

Your terminal is the game's one connection. `ConsoleSession` reads standard
input and writes standard output; there is no telnet negotiation, and ANSI
colour is always on. Things to know:

- Colour is ANSI. If your terminal does not show it, you see escape
  sequences. The first character's creation asks `Do you want ANSI colors
  (Y/n)?`.
- Text is encoded with `CHARSETINPUT` and `CHARSETOUTPUT` (default `iso-8859-1`).
  If you see garbage for accented or non-Latin characters in a UTF-8 terminal,
  launch with `CHARSETINPUT=UTF-8 CHARSETOUTPUT=UTF-8`.
- Some questions are asked asynchronously (confirmations, menus). When input is
  piped in faster than a person types, an answer can arrive before its question
  and be read as a command. This is why `tools/sudplay.py` waits for the game to
  go quiet before each line; do not feed a script with a plain `< file`.
- When standard input ends (end of file), the session ends: the player is logged
  out and the process exits. This does not write a named save. Only `SAVE`,
  `QUIT` (when you answer yes), `CHECKPOINT` and autosave do.
- The prompt shows `(paused)` after `PAUSE`, and `(playtest)` in a builder
  playtest, `(xN)` when `TIMESCALE` is not 1.

### Per-character windows

Several of your characters can be in the world at once (a party). The one you
type to in the terminal is the *played* character. With windows on, each other
character in the world gets its own Swing window: what that character sees
scrolls in it, in colour, and a line at the bottom takes that character's
commands, with the lines typed earlier a key press away.

| Way to turn on | Detail |
| --- | --- |
| `WINDOWS=1` at launch (or in `coffeemud.ini`) | If there is no display: `There is no display here, so the characters' windows (WINDOWS=1) are off.` |
| `WINDOWS ON` in game | `There is no display here, so the windows cannot open.` when headless. |
| `WINDOWS OFF` | Closes them all; the characters stay in the world. |
| `WINDOWS PANEL ON` / `OFF` / `STATUS,MAP,PARTY` | A panel beside each window's text. `WINDOWPANEL=` in `coffeemud.ini` or on the command line starts a game with one; a world's `world.ini` cannot set it. `STATUS` reads `world/text/panel.txt` (prompt codes such as `%h`, `%H`), `MAP` draws the rooms about the character and `PARTY` shows a hit-point bar for each member. |

Closing a window sends that character to rest; `SWITCH name` swaps the terminal
with a window. In turn-based time, what a window's character types still waits
for the party leader's turn (`PARTYTURNS`). A display is needed only for these
windows, the `DEVLOG WINDOW`, and the `PLAYTEST GAME` window. Java decides by
itself whether a display exists; to force it off, set
`JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` (the test scripts do this).

For turn order, the party and window behaviour in play, see
[Saves and party](31-saves-and-party.md).

## Quitting and shutting down

| You do | What happens |
| --- | --- |
| `QUIT` in a normal game | `Quit -- save the game as 'hero' first (Y/n), or cancel (c)?` Enter or `Y` saves to the current slot (or the character's name), `N` quits without saving, `C` cancels. If saving is refused (a no-save room, a fight, `SAVEPOINTS`) the reason is shown and the default becomes cancel; `N` quits without saving. In `IRONMAN` mode the prompt is `Quit -- the game is saved as 'x' (Y), or cancel (c)?`. |
| `QUIT` in builder mode with unsaved mobs or items | `Unsaved mobs or items in n rooms: ...  Quit -- save them first (Y/n), or cancel (c)?` |
| `QUIT` in builder mode, nothing unsaved | `Quit -- are you sure (y/N)?` The world files are then written as the game shuts down. |
| `QUIT` at the characters menu | Quits without saving. |
| `SHUTDOWN` (needs the `SHUTDOWN` security flag) | Brings the game down. `SHUTDOWN IN 10 minutes` schedules it; `SHUTDOWN CANCEL` stops it. Prefer `QUIT`. |
| Closing the terminal, Ctrl+C | The JVM's shutdown hook runs the engine's shutdown (which, in builder mode, exports the world files first). An unsaved normal game is lost, mobs and items not yet saved with `SAVE ROOM` in builder mode are lost, and a kill during the shutdown can leave things half written. Always `QUIT`. |
| Standard input ends | The player is logged out; the game exits; no named save is written. |

Shutdown (`MUD.globalShutdown`) in builder mode first exports the world to its
files, then logs everyone out, stops the services, closes the database and
releases the save-folder lock. The normal exit status is 0. After the engine is
down, the `.play-*` working database of a normal game is deleted. The log
(`mud.log`) records each step: `New Connections are now closed`, `All users
saved`, `Database connections closed`, `CoffeeMud shutdown complete`.

## Logs

The game writes its log to `mud.log` in `CoffeeMud/` (the current folder).
At each start the previous logs shift: `mud.log` becomes `mud1.log`, that
becomes `mud2.log`, and so on, and the oldest beyond `NUMLOGS` (10 by default)
is deleted. So the newest log is always `mud.log`, a new one is made at every
boot, and an in-game `RESTORE` (which reboots inside the same process) keeps
logging to the same file.

`-Dsud.log.prefix=PATH/name` moves the log to `PATH/name.log`; the isolated
playtest uses this to log under its temp folder.

### Format

```text
20261106.1008.33 info  MUD            CoffeeMud v5.11.0.1
20261106.1008.39 info  HOST0          Mapped rooms      : 9 in 2 areas
20261106.1008.46 info  Scheduler0     127.0.0.1 UNKNOWN, character login: Trialbuilder
20261106.1008.48 info  WorldFiles     Wrote the world files: 2 changed.
```

Each line is `yyyyMMdd.HHmm.ss`, the level cut to five letters (`info`, `error`,
`warni` for a warning, `debug`, `help`, `kills`, `comba` for combat, `acces` for access), the
source (a thread or component name), and the message. Java stack traces (stderr) go to the same file.

### What goes where

The log channels and their `coffeemud.ini` keys:

| Key | Level | Default here |
| --- | --- | --- |
| `SYSMSGS` | `info`: normal events | `FILE` (forced by the launcher) |
| `ERRMSGS` | `error`: errors | `FILE` (forced by the launcher) |
| `WRNMSGS` | `warn`: warnings | `FILE` (forced by the launcher) |
| `DBGMSGS` | `debug`: debug output (needs `DEBUG=...` too) | `FILE` (forced by the launcher) |
| `HLPMSGS` | failed `HELP` lookups | `OWNFILE`: `mud_help.log` |
| `KILLMSGS`, `CBTMSGS` | kills; detailed combat and XP | `OFF` |
| `ACCMSGS` | web access log | `OFF` (there is no web server) |

Values: `OFF`; `ON` (screen only); `FILE` (`mud.log` only); `BOTH` (screen and
`mud.log`); `OWNFILE` (its own file, with optional roll rules such as
`OWNFILE<9999L`, see the ini). The kills key is `KILLMSGS`; the old spelling
`KILMSGS` is still read when `KILLMSGS` is blank. The
launcher forces the four main channels to
`FILE` so the log does not scroll over the game; put `SYSMSGS=BOTH` or
`ERRMSGS=BOTH` on the command line if you want to watch them. The builder help
suggests `ERRMSGS=BOTH` while building.

### Reading the log

What to look for, in order of usefulness:

| Search for | Shows |
| --- | --- |
| `error` | Java exceptions and engine errors. The first one after a symptom is usually the cause. |
| `warni` | Things that did not stop the game: scripts that ran slow (`SCRIPTSLOW`), a module not selected, a world file that loaded in part. |
| `WorldFiles` | The world import and export: `Building the world from the files in world`, `Dev areas loaded from dev/`, `Wrote the world files: n changed`. Any `Unable to write ...` is a failed export. |
| `PuzzleCheck` | Problems found in the world's puzzle wiring at the first login. |
| `Initialization complete.` | The boot finished. Anything before it is a startup problem. |
| `character login:` | The player came in. |

The startup lines list how many races, classes, abilities, commands and so on
were loaded, which doubles as a sanity check that a build is whole: if a count is
zero, the class files for that kind are missing; run `build.sh clean`.

In a builder or DEV game, `DEVLOG` tells you the engine's log lines as they happen
(`DEVLOG ON WARN MODULE Scripting,Quest*`), with a level (ERROR, WARN, INFO, DEBUG) and
a module mask, whatever `mud.log` is set to keep. `DEVLOG LAST` replays the last
500 lines, and `DEVLOG WINDOW` opens a display-only log window (it works; the
older audit note that said otherwise is out of date). `SCRIPTERRORS` lists the
last 200 script errors with file and line. See
[Testing and release](13-testing-release.md).

## Headless and scripted runs

The game is a text program on standard input and output, so it can be driven
by a script. Two tools in `tools/` do it correctly; both need Python 3.

### tools/sudplay.py

```sh
python3 tools/sudplay.py SCRIPTFILE [NAME=VALUE ...]
```

`sudplay.py` starts `CoffeeMud/sud.sh` with the given `NAME=VALUE` settings and
types the script into it one line at a time. It sends a line only after the
game has answered the previous one and gone quiet (0.8 seconds of silence), the
way a person waits to read before typing; sending lines faster races the game's
asynchronous questions and an answer can be read as a command. The transcript
(everything the game printed) goes to standard output, and `sudplay.py` exits
with the game's exit code, or 124 if the run did not finish in time.

A script file is one input line per line. Two directives are understood by the
tool and are not sent to the game:

| Line | Meaning |
| --- | --- |
| `#expect TEXT` | Wait (up to `SUDPLAY_EXPECT_LIMIT` seconds) until `TEXT` has appeared in the output since the last line was sent. Use it for commands that take longer than a pause, such as a long `WAIT`. |
| `#sleep N` | Wait `N` seconds, to let game time pass. |

Every other line is typed into the game, including lines that start with `#`.
(There are no comment lines in a `sudplay` script; `playtest.py` files have
their own, see below.) A blank line is an empty input, which some prompts need.

Environment variables:

| Variable | Default | Effect |
| --- | --- | --- |
| `SUD_GAME_DIR` | the repository's `CoffeeMud/` | A different checkout to run. |
| `SUDPLAY_COMMAND` | `[".../sud.sh"]` | A JSON list that replaces the launcher command, for a custom JVM line. |
| `SUDPLAY_LIMIT` | `300` | Seconds the whole run may take before it is killed (exit 124). |
| `SUDPLAY_EXPECT_LIMIT` | `1800` | Seconds one `#expect` may wait. |

Fixed inside the tool: up to 120 seconds for the first prompt, 30 seconds for the
answer to one line, and 60 seconds after the script for the game to end by
itself (then standard input is closed, which logs the player out).

A complete script that makes a character, looks around and quits without
saving. The answers follow the creation prompts in order: name, confirm the
name, ANSI colours (`N` here), race, confirm, gender, re-roll (`N`), class,
confirm, alignment, and a blank line to finish:

```text
Hero
Y
N
Human
Y
M
N
Apprentice
Y
good

look
inventory
quit
n
```

```sh
python3 tools/sudplay.py hero.txt RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS \
    SAVEDIR=/tmp/sud-saves DBSERVICE=jdbc:fakedb:/tmp/sud-db
```

(Make `/tmp/sud-db` first with a copy of `fakedb.schema`; see
[DBSERVICE](#dbservice-the-content-database). If the world has a
`text/prologue.txt` with several pages, add one blank line to the script per
page break, to answer each `[Enter]`.) `RESTORE=NEW` skips the menu;
`RANDOMSEED` with `TIMEMODE=TURNS` makes the stat rolls repeatable; the scratch
`SAVEDIR` and `DBSERVICE` keep the run from touching your real saves and cache.
The last two lines answer `QUIT`'s save question with `n`.

### tools/playtest.sh and playtest.py

```sh
tools/playtest.sh                       # every file in CoffeeMud/world/tests
tools/playtest.sh world/tests/start.play
KEEP=1 tools/playtest.sh FILE.play      # keep the scratch folder and transcript
WORLD=/path/to/world tools/playtest.sh  # test another world folder
```

A playtest is a regression test kept as content, in `world/tests/`. Each file
is played by `sudplay.py` in a fresh game on a scratch copy of the world,
database and saves, with a fixed seed (42), in turn-based time, as a `DEV=1`
game so that `ASSERT` answers. The runner prints `PASS` or `FAIL` for each
file, with every failing step and its line, and exits 1 if any failed. A file
holds one game command per line, and these directives (everything else that
starts with `#` is a comment):

| Directive | Meaning |
| --- | --- |
| `#seed N` | The dice's seed (default 42). |
| `#realtime` | Real time instead of turns. |
| `#builder` | Builder mode (an Archon) instead of a normal new game. |
| `#new` | A new game (the default). |
| `#restore SLOT` | Restore this save (saves are the run's own; one made by an earlier file is not kept). |
| `#char NAME,RACE,CLASS[,GENDER[,ALIGNMENT]]` | The character a new game makes. Default `Wanderer,Human,Apprentice,M,good`. |
| `#expect TEXT` | `TEXT` is in what the step's commands printed. |
| `#reject TEXT` | `TEXT` is not. |
| `#assert WHAT` | `ASSERT WHAT` is ok: `HERE room`, `HAS item`, `VAR host name op value`, `QUEST name STEP n`. |
| `#wait N` | `N` ticks pass (`WAIT N`). |
| `#save SLOT` | `SAVE SLOT`. |

A step is a run of commands up to the next `#expect`, `#reject` or `#assert`.
The shipped example is `CoffeeMud/world/tests/start.play`:

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
south
#assert HERE New Area#0
```

A `.js` file in `world/tests/` is a JavaScript test: it runs in builder mode
through `JRUN` with `test(name, fn)` and `assert(...)` provided, and passes when
it reports `WORLDTEST SCRIPT PASS`. Inside a builder game, `PLAYTEST RUN
[FILE]` (and, in a builder or DEV game, `WORLD TEST`) run the same files in disposable campaigns and report
here. Playtest files, assertions and recording one by hand (`RECORD INPUT`,
`RECORDINPUT=1`) are covered in [Testing and release](13-testing-release.md).

### The other tools

| Tool | What it does |
| --- | --- |
| `tools/check.sh` | The checks for a commit: the creator-docs check, the composition, combat and item-modifier suites, the fixes suite and the smoke test (10 to 30 minutes depending on the machine; the fixes suite is the slowest part). `ENGINE=1 tools/check.sh` adds the engine's own `TEST ALL` suite, which runs alongside the others. |
| `tools/fixes.sh [NAME ...]` | One or more of the JavaScript fix tests in `tools/fixtures/fixes/`, each run with `JRUN` in a builder-mode game on a scratch world. |
| `tools/smoke.sh` | The single-player path on its own: character creation, save, restore, pause, turn-based time, party, the world files, builder edits. `KEEP=1` keeps the scratch folder. |
| `tools/engine-tests.sh` | The engine's `TEST ALL` suite (about 15 minutes). |
| `tools/saveinspect.py SAVE` | Prints a saved game's contents, and with `--var`, `--hp` or `--room` edits a few things in it (the game must not be running on that save; the old `world.xml` is kept as `world.xml.bak`, and the checksum in `.complete` is updated). |
| `tools/worlddiff.py OLD NEW` or `--git REV` | Compares two world folders object by object. Exit 1 if they differ. |
| `python3 SUDDocs/creator/examples/check.py` | The museum example's automated tour (builds a world, publishes it, plays it, restores it in a fresh process). |

All of them use their own scratch worlds, databases and save folders, so they
do not touch your `saves/` or your cache. They run `sud.sh` underneath, which
builds first; run `CoffeeMud/build.sh` once yourself before starting several in
parallel, so that they do not build at the same time.

## Running more than one game

Games can run side by side if they do not share state. Three settings define the
state of a run:

| Setting | What it isolates |
| --- | --- |
| `SAVEDIR` | The saves and the working database. A normal game locks it, so two normal games on one folder are refused. |
| `DBSERVICE` | The content cache. Builder mode plays on it directly, with no lock, so two builder games on one cache would corrupt each other. Normal games read it and refresh it when it is out of date. |
| `WORLDDIR` | The world files. A cache is built from one world; pointing two worlds at one cache makes each start rebuild it. |

Rule: **a world, its cache and its saves go together.** For a second world:

```sh
mkdir -p /tmp/w2/db && cp CoffeeMud/resources/fakedb/fakedb.schema /tmp/w2/db/
cp -r CoffeeMud/world /tmp/w2/world        # or your own world folder
CoffeeMud/sud.sh WORLDDIR=/tmp/w2/world DBSERVICE=jdbc:fakedb:/tmp/w2/db SAVEDIR=/tmp/w2/saves
```

Absolute paths are fine for all three. This is also how a world can live in its
own repository: the repository holds the world folder, and a launcher script
passes these three settings. The museum example does exactly this (see below).

## Worked examples

### Example 1: first run, play, save and restore

```sh
CoffeeMud/build.sh      # optional: sud.sh builds anyway
CoffeeMud/sud.sh
```

1. There are no saves, so the menu is skipped and the game starts a new one. The
   first time, the world files are imported into the cache
   (`The world files have changed: rebuilding the world from them...`).
2. Answer the creation prompts; the game prints `Character creation complete!`
   and puts you in the start room of the shipped world.
3. Play. Type `SAVE` to save as `hero` (your character's name in lower case).
4. Type `QUIT`. You are asked `Quit -- save the game as 'hero' first (Y/n), or
   cancel (c)?`. Answer `Y`.
5. Start again: `CoffeeMud/sud.sh`. The menu shows `C) Continue - hero, ...`. Press
   Enter. The game prints `Restored 'hero'.` and you are back where you saved.
6. To skip the menu: `CoffeeMud/sud.sh RESTORE=hero`. To start over:
   `CoffeeMud/sud.sh RESTORE=NEW`.

### Example 2: build in a separate world folder, then play it

```sh
mkdir -p /tmp/tavern/db
cp CoffeeMud/resources/fakedb/fakedb.schema /tmp/tavern/db/
cp -r CoffeeMud/world /tmp/tavern/world

# build: you are an Archon, SAVE writes /tmp/tavern/world
CoffeeMud/sud.sh BUILDER=1 WORLDDIR=/tmp/tavern/world DBSERVICE=jdbc:fakedb:/tmp/tavern/db
```

In the game, make the characters (first launch), then build with the in-game
editors (see [Your first world](01-first-world.md)) and `SAVE` when done; `QUIT`
exits and writes the world once more. Now check and play it as a player would:

```sh
CoffeeMud/sud.sh CHECK=1 WORLDDIR=/tmp/tavern/world DBSERVICE=jdbc:fakedb:/tmp/tavern/db \
    SAVEDIR=/tmp/tavern/check-saves
CoffeeMud/sud.sh WORLDDIR=/tmp/tavern/world DBSERVICE=jdbc:fakedb:/tmp/tavern/db \
    SAVEDIR=/tmp/tavern/saves RESTORE=NEW
```

Builder mode records the checksum of the files it wrote in the cache, so the
normal game starts at once from the cache with no rebuild. (If a world file was
changed outside the game and builder `SAVE` kept it, the checksum is dropped and
the next game rebuilds.) Your builder character is not in the normal game; make a
new one.

### Example 3: a continuous-integration job

```sh
set -e
CoffeeMud/build.sh
CoffeeMud/sud.sh CHECK=1 SAVEDIR=/tmp/ci-saves DBSERVICE=jdbc:fakedb:/tmp/ci-db
tools/playtest.sh
```

`CHECK=1` fails the job (exit 1) if `WORLD CHECK` finds any error; `playtest.sh`
fails it if any `world/tests` file fails. Prepare `/tmp/ci-db` first with a copy
of `fakedb.schema`, as above. `CHECK=1` already imports the world on a first
run, so the job does not need a separate rebuild step.

### Example 4: test a saved game's compatibility with a changed world

```sh
CoffeeMud/sud.sh RESTORE=hero
```

If you have edited area files since `hero` was saved, the first screen after
login says `This save was made with another version of the world:` and lists
`Changed area file: areas/harbour.cmare`. Rooms are as the save had them. To take
a changed area from the files instead (keeping your characters and everything
else the save holds):

```sh
CoffeeMud/sud.sh RESTORE=hero FRESH=CHANGED
```

## Persistence notes

What each run writes, and where:

| Run | Writes | Does not write |
| --- | --- | --- |
| Normal game | `saves/NAME/` on `SAVE`, `QUIT`, autosave and `CHECKPOINT`; `saves/.play-*` (deleted at exit); `mud.log`; the cache's content tables and `world.sum` when it had to be rebuilt; `saves/recordings/` if recording | `world/` (never) |
| Builder mode | `world/` on `SAVE` and shutdown; `world/.backup/`, `world/.history/`, `jscripts.ini`; the cache (also holds your characters); `mud.log` | `saves/` slots (builder mode has none) |
| `DEV=1` | Like a normal game, with `[dev]` saves | `world/` |
| `CHECK=1` | `mud.log`; a scratch working database in `SAVEDIR`, deleted at exit; the cache, if it had to be rebuilt | `world/` and saves |
| `PLAYTEST GAME` | A temp folder, deleted when the window closes | Anything in your world, cache or saves |

Consequences worth remembering:

- **A normal game's changes to the world are saved in the save, not in the
  world.** Mobs and items you make or move persist in the save; rooms, exits and
  areas you edit last until the game ends (the building commands say so).
- **A restore plays the save's version of the world**, laid over a freshly built
  copy of the world's content. Changes to world files since the save are
  reported at login (rooms new since the save are kept as the world has them); scripts, text and other external assets are read live from
  the world folder, so they do take effect on a restore. See
  [State and persistence](state-and-persistence.md).
- **Deleting the cache is safe.** Everything in `resources/fakedb/` but the schema
  is rebuilt from `world/`. In builder mode this also forgets the builder
  characters.
- **A crash is survivable.** Saves are written whole or not at all, and the
  save-folder repair on the next start puts back the last whole save. A normal
  game that dies before you save loses the session only.
- **Git.** Commit `world/` (and `docs`, `tools`, sources). Do not commit
  `saves/`, `mud*.log`, `*.class`, `.build-*` or the cache; they are ignored.

## Troubleshooting

### The build

| Symptom | Cause and fix |
| --- | --- |
| `javac: command not found` / `java: command not found` | No JDK on `PATH`. Install a JDK and open a new shell. `java -version` and `javac -version` should both work. |
| `error: invalid flag: --release` or `release version 8 not supported` | The compiler is JDK 8 (no `--release`) or too old. Use JDK 9 or newer. |
| `Build FAILED.` with errors in files you did not touch | A half-finished earlier build or a stale class. Run `CoffeeMud/build.sh clean`. If a merge brought a conflict marker into a `.java` file, fix that first. |
| `Build failed; run ./build.sh to see why.` from `sud.sh` | The launcher's build failed and its output was hidden. Run `CoffeeMud/build.sh` to see the errors (full log in `CoffeeMud/.build.log`). |
| Out of memory while compiling | The build uses `-J-Xmx2g`. Close other programs or run on a machine with more memory. |
| Windows: `Could not find javac` or `Found javac at ..., but it does not run` | See `findjava.bat`'s message: install a JDK and set `JAVA_HOME`, then open a new command window. |
| Windows: `sud.bat` runs old code | It builds only if `SUD.class` is missing. Run `build.bat`. |
| The game starts but a whole kind of thing is missing (`Races loaded : 0`) | The class files for that kind are missing. Run `build.sh clean`. |

### Startup

| Symptom | Cause and fix |
| --- | --- |
| `MUD/ERROR: Unable to read ini file: 'coffeemud.ini'.` | The working folder is not `CoffeeMud/`. Start with `CoffeeMud/sud.sh` or `cd CoffeeMud`. |
| `Another game is using the save folder ... Close it or choose a different SAVEDIR.` | Another CoffeeSUD process (maybe a `CHECK=1` run, a test tool, or a hung game) holds `SAVEDIR/.owner.lock`. Close it, or pass another `SAVEDIR`. A crashed process releases the lock when it exits. |
| `There is no saved game called 'x'.` | `RESTORE=` named no save. The list is `ls CoffeeMud/saves` (folders not starting with `.`), or the menu. Names are not case sensitive. |
| `The save 'x' is incomplete or damaged.` / `Unsupported save format` / `The world snapshot in 'x' is incomplete or damaged.` | A save whose `.complete` manifest does not match, or from an engine of another format. Copy a good folder over it, or restore another save. `.old-x` and `.damaged-x-*` folders in `saves/` hold earlier generations. |
| `Optional modules: ...` | The `MODULES` selection is invalid, or the save was made with modules the world no longer provides. See [Optional modules](14-optional-modules.md). |
| `world.ini: KEY is not a world setting, so ignored (the engine's own settings go in coffeemud.ini).` | A key in `world.ini` is not on the [allowlist](world-settings.md). Move it to `coffeemud.ini` or the command line. |
| `Cannot start the game safely: ...` | The working database could not be made: the save folder is not writable, the disk is full, or `DBSERVICE`'s folder has no `fakedb.schema`. |
| `HOST#0 could not start any listeners.` in the log; `CoffeeMud failed to start.` | `PORT=` in `coffeemud.ini` is empty. It is not used to listen (nothing listens), but the host group needs a value. Leave it as shipped (`5555`). |
| `The world files and saved games need the fake database, so they are off.` | `DBSERVICE` does not start with `jdbc:fakedb:`. Restore the shipped value. |
| The first start is slow, and says `rebuilding the world from them...` | Normal when the world files changed. It is quick the next time. |
| A rebuild happens at every start | The cache folder is shared with another world or is not writable, so its `world.sum` never matches. Give each world its own `DBSERVICE`. |
| `The world files loaded whole.` is missing in builder mode, or `WORLD PROBLEMS` lists files | A world file did not load completely (an unknown class, a bad tag). It is held back from being written over. Read `worldproblems.txt` in the cache folder, `mud.log`, and `WORLD PROBLEMS`; fix the file. |
| `No display ...` / windows do not open | You have no display (SSH, a container, `java.awt.headless`). Use the terminal; `AS name command` shows what other characters do. |
| Strange characters instead of accents | Set `CHARSETINPUT=UTF-8 CHARSETOUTPUT=UTF-8` for a UTF-8 terminal. |
| A script file or `world/` change does not appear | The world is read at start. In builder mode `WORLD RELOAD` loads a changed file; otherwise restart. Remember a restored game reads scripts live but rooms from the save (use `FRESH=`). |
| Output is flooded with log lines | You set a log channel to `ON` or `BOTH`. The default sends them to `mud.log` only. |
| The game seems to hang at start | Check `mud.log`: a long line count after `Loading map...` means a large world is loading. A stack trace means a failure; the first `error` line is the cause. |

### In play

| Symptom | Cause and fix |
| --- | --- |
| `QUIT` says it will not save (`Quit without saving (n), or cancel (C)?`) with a reason | The save rules (`SAVEMODE`, `Prop_NoSave`, a fight) refuse a save here. Move, finish the fight, or answer `n`. |
| In a normal game `CREATE` or `MODIFY` says it will not last | Expected: the game plays on a copy of the world. Use `BUILDER=1` to change the world. |
| An editor is refused with `The HELP editor is refused in a normal game` (or SOCIAL, TITLE...) | The editors of the data files are builder-mode only. |
| Everything I built is gone | In a normal game, rooms and areas are not saved to `world/`. In builder mode, make sure you used `SAVE` (and `SAVE ROOM` for mobs and items), or quit with `QUIT` and answer the prompts. Recover earlier versions of a file from `world/.backup/` (`WORLD BACKUPS`, `WORLD RESTOREFILE`) or from git. |
| I edited a world file and builder `SAVE` did not overwrite it | A file changed outside the game is kept; the game's version is written beside it as `.builder`. Use `WORLD DIFF`, `WORLD RELOAD`, or `SAVE FORCE`. |

## What CoffeeSUD removed from CoffeeMud

CoffeeSUD is a single-player fork of CoffeeMud 5.11. It removed everything that
exists because many people share one server, and replaced some of it. The plan
and progress log are in `PLAN.md`. This section is the summary for readers who
know CoffeeMud.

### Servers and services

| Removed | Where it was | Now |
| --- | --- | --- |
| The telnet listener, ports, proxies | `MUD.run`, `PORT`, `PROXY`, `BIND`, `BACKLOG`, `MAXCONNSPERIP`, `MAXNEWPERIP`, `MPCPKEY`, `DOMAIN` | Your terminal is the one session. These keys are still in `coffeemud.ini` but do nothing, except that `PORT` must be non-empty. |
| The embedded web server and its pages | `coffee_web`, `web/`, the web macros, the web admin and the in-browser client (Siplet) | Gone. `core/TextMacros` expands the few macros game text still uses (`@MudInfo?NAME@`, `@INIValue@`, `@RaceList@`, simple `@if@`). |
| MUDGrinder | The web editor | Gone with the web server. `archive/mudgrinder/` holds a browsable snapshot, its templates and the Java behind them, for reference. Build with the in-game editors in builder mode. |
| The SMTP server and e-mail | `coffee_smtp`, mail to real addresses | Mail goes to the in-game mailbox, and post offices in the world still work. `EMAILREQ` is forced to `DISABLED`. |
| CM1 (the JSON admin/API server) | `CM1Server.html` | Gone. |
| Intermud and chat integrations | I3, IMC2, Grapevine, Discord, I3Router, MUDProxy | Gone, with their channel flags. |
| The off-line server, the stand-alone tools | `application/OffLine`, `Shutdown`, `AutoPlayTester`, `RebuildRefDocs` | Gone. |
| The multiplayer launch scripts | `mud.sh`, `mud.bat`, `config.sh`, `tools/mudserver.sh`, `tools/mudbot.py` | `sud.sh` / `sud.bat`. |
| Real databases | MySQL, PostgreSQL, HSQLDB, Derby, Access by JDBC | The content database is a file-based cache of the world files (`resources/fakedb`). The `DBCLASS`/`DBSERVICE` examples in `coffeemud.ini` for other drivers do not apply; do not change the driver. |
| Client protocols | GMCP, MSDP, MSSP, MCP, MPCP, MCCP compression, the `MXP`/`NOMXP` commands | Gone. MXP and MSP markup left in some texts is dropped by the output filter. |
| Host groups and multi-MUD hosting | More than one mud in one process | One game per process. (`NAME:0=VALUE` is the only host-group form that still works.) |
| Packaging and service installs | Chocolatey, RPM, DEB, service units, the upgrade tool | Build from the repository with `build.sh`. |

### Players, accounts and chat

| Removed | Now |
| --- | --- |
| Logins, passwords, account names, e-mail, idle logouts, bans, `BOOT` | You are the only player. The game makes one account, the **profile**, which holds all of your characters and is never logged in to. `PASSWORD`, `BAN`, `BOOT`, `AUTOFORWARD`, `SUBSCRIBE`, `PROXYCTL` are gone. |
| Several people in the world | Several of **your** characters in the world at once: the **party** (`PARTY`, `SWITCH`, `AS`, `WINDOWS`). See [Saves and party](31-saves-and-party.md). |
| Public channels and chat between players | Only two channels are left, the ones the world itself speaks on: `INFO` (news, calendar, executions, conquests) and `QUESTCHAT` (quest announcements). `CHANNELS=` in `coffeemud.ini` lists them. |
| Player-facing commands | `WHOIS`, `WIZLIST`, `CHANWHO`, `FRIENDS`, `IGNORE`, `AUTONOTIFY`, `POLL` (and the poll system), `DUEL`, player `AUCTION`, `ANNOUNCE`, `RULES`, `MOTD`/`NEWS`, `SESSIONS`, and the report journals `BUG`, `TYPO`, `IDEA`, `TASK`, `STUCK`. |
| Player killing | `PLAYERKILL=NEVER`: every character is the same person. `PARTY FIGHT ON` lets your own characters fight each other. |
| Kept, for NPCs and the party | `WHO` (your party), `TELL`, `REPLY`, `WHISPER` (NPCs listen), `GTELL`, mail and post offices, journals as boards in the world, auction houses with an NPC auctioneer, hirelings, `SNOOP` (to watch a party member), `SHUTDOWN`, clans (NPC clans and conquest). |
| Real-time, shared server clock | A **game clock**: `PAUSE`, `RESUME`, `TIMEMODE TURNS`, `TIMESCALE`, `WAIT`. See [Simulation and travel](10-world-simulation.md). |
| The "world is the database" model | The world is **files**; the database is a cache. Saves are real save games: `SAVE`, `RESTORE`, `SAVES`, autosave. |

### What stayed the same

The simulation is CoffeeMud's: races, classes, thousands of abilities, combat,
crafting, factions, law, quests, MOBPROG, JavaScript (now Rhino with ES6),
random areas, the in-game editors (`CREATE`, `MODIFY`, `DESTROY`, `COPY`,
`GMODIFY`, `MERGE`, `IMPORT`, `EXPORT`, `GENERATE`, `CATALOG`), `ZAPPERMASK`
syntax, the `DISABLE` and `ENABLE` flags, and the ini file's engine and combat
settings.

### For readers coming from CoffeeMud

| In CoffeeMud you would... | In CoffeeSUD |
| --- | --- |
| Install a package or unzip and run `mud.sh` | Check out the repository, install a JDK, run `CoffeeMud/sud.sh`. |
| Telnet to port 5555 and log in as the first character to make an Archon | Run `sud.sh BUILDER=1`; you are made an Archon at login. |
| Edit the world in MUDGrinder or by IMPORT and EXPORT | Use the in-game editors in builder mode, or edit `world/*.cmare` files by hand; `IMPORT`/`EXPORT` still work for area files. |
| Back up the database | Commit `world/` to git. Back up `saves/` by copying folders. |
| Run a shutdown with warnings to players | `QUIT`. |
| Set up `SYSOPMASK` and give other builders security flags | Builder mode is the sysop. `DEV=1` gives the developer group. See [Configuration and administration](19-configuration-and-administration.md). |
| Read the Installation Guide for databases, proxies, multiple hosts, packages, MUDGrinder, SMTP | Skip it. This chapter replaces it. |
| Use `AHELP` and the Archon's Guide | Still valid for commands, with `AHELP BUILDING` for the builder-mode workflow. |

The inherited manuals in `CoffeeMud/guides/` still describe the engine. Ignore
their parts on servers, the web, intermud, e-mail, databases and multi-player
administration: `InstallationGuide.html`, `WebServer.html`, `SMTPServer.html`,
`CM1Server.html`, `MUDGrinder.html`, `Protocols.html`. `ScriptingSUD.html` and
`JavaScriptSUD.html` are the CoffeeSUD supplements to the scripting guides.

## See also

- [Your first world](01-first-world.md): the beginner tutorial that follows this
  chapter.
- [World workflow](02-world-workflow.md): the world folder, file formats, the
  `SAVE` variants and the `WORLD` commands in detail.
- [Configuration and administration](19-configuration-and-administration.md):
  the ini keys, security flags and developer powers.
- [Testing and release](13-testing-release.md): playtest files, assertions,
  `RECORD INPUT` and release packaging.
- [Saves and party](31-saves-and-party.md): the profile, the party, `SAVE` and
  `RESTORE`, and what a save keeps.
- [Optional modules](14-optional-modules.md): `MODULES` and `MODULESETTINGS`.
- [World settings](world-settings.md) and
  [State and persistence](state-and-persistence.md): references.
- [Engine programming](32-engine-programming.md): the Java build and class loading
  for engine developers.
