# CoffeeSUD: turning CoffeeMud into a single-player game

CoffeeSUD is a hard fork of CoffeeMud 5.11 that keeps its world simulation and
removes everything that exists because many people share one server. The aim
is a game one person plays on their own machine, with real save games.

This file is the plan and the progress log. `DOCS/` holds the earlier analysis
of the engine; its chapters explain each subsystem.

## Running it

```sh
CoffeeMud/build.sh        # compiles in place (about a minute from clean)
CoffeeMud/sud.sh          # play in this terminal
tools/smoke.sh            # one-minute check of the single-player path
tools/engine-tests.sh     # the engine's TEST ALL suite, about 15 minutes
```

On Windows, `CoffeeMud\build.bat` and `CoffeeMud\sud.bat` do the same as
the first two. `build.bat` always compiles from clean; run it after pulling.

`sud.sh` takes `NAME=VALUE` arguments that override `coffeemud.ini`, for
example `DBSERVICE=jdbc:fakedb:/some/scratch/copy` to play against a copy of
the database.

## Ground rules

- Every step keeps the build green and the smoke test passing, and is
  committed on its own. Larger steps also run `tools/engine-tests.sh`, which
  must match the baseline.
- Removing code beats adding switches. A setting is kept only while a phase
  still needs both paths.
- The engine's behaviour (combat, abilities, effects, behaviours, scripts) is
  the asset. Changes to it need a reason tied to single-player play.

## Phases

### 0. Baseline: done

- Compiled classes and logs are no longer tracked; `build.sh` compiles in place
  (`CMClass` finds classes by scanning package folders, so output sits next to
  the sources).
- `tools/mudserver.sh` and `tools/mudbot.py` boot the stock multiplayer server
  on a scratch database and drive it over telnet. They exist to compare
  against the original behaviour and go away with the network code.
- `tools/baseline/test-all.txt` is the engine's own `TEST all` suite on the
  unmodified server: 47 of 50 pass. `Spaceangles`, `Spaceinterception` and
  `Spaceinterception2` already fail there, so they are not regressions.

### 1. Console launcher: first cut done

- `application/SUD.java` boots the engine in console mode: no listeners, no web,
  mail or intermud services, logs to `mud.log` only.
- `Common/ConsoleSession.java` is a `DefaultSession` over standard input and
  output. It sends no telnet negotiation and filters any telnet bytes the
  session writes later. When input ends, the session logs out and saves.
- `CMProps.isSinglePlayer()` turns off passwords, e-mail and the login input
  pause in `CharCreation`.
- The `INFO` channel no longer repeats the player's own achievements, levels
  and creation.
- `tools/smoke.sh` (about a minute) creates a character, plays, becomes an
  Archon, quits, then loads the character again. `tools/sudplay.py` types a
  script into the game, waiting for each answer as a person would; piping a
  file straight in races the game's asynchronous questions. Stats are rolled
  at random, so the script picks Apprentice, the one class every roll allows.

Still to do:
- A title screen (continue, new character, quit) listing saved characters.
- Rewrite the multiplayer texts: intro, new-character text, rules, help.
- A debug switch that makes the player an Archon, instead of the manual in the
  start room.

### 2. Remove the network and multiplayer services: mostly done

Done, each step verified with the smoke test and the engine suite:
- the embedded web server (`coffee_web`), the browser client (`siplet`), the
  web macro engine and the MUDGrinder pages under `web/`. `core/TextMacros`
  expands the few macros game text still uses (`@MudInfo?NAME@`,
  `@INIValue@`, `@RaceList@`, simple `@if@` blocks).
- intermud (I3, IMC2, Grapevine, Discord, CM1) and the standalone tools built on
  it (`I3Router`, `MUDProxy`, `Shutdown`, `AutoPlayTester`, `RebuildRefDocs`).
- the SMTP server and client. Mail now goes to the in-game mailbox
  (`JournalsLibrary.postToMailbox`).

Still to do:
- client protocols (MXP, MSP, GMCP, MSDP, MCCP, MSSP) and telnet handling in
  `DefaultSession`, leaving a plain text session. The console session already
  never turns them on, so this is cleanup rather than behaviour.
- the telnet listener in `MUD.run`, and with it `tools/mudserver.sh` and
  `tools/mudbot.py`.
- host groups: `CMLib` and `CMClass` keep one instance per thread group so
  several muds can share a process. One game needs one instance.

### 3. One thread and a game clock

- Replace the thread pools in `ServiceEngine` with a single scheduler behind the
  existing `ThreadEngine` interface, so every tick runs in a known order.
- Add a game clock and move game logic off `System.currentTimeMillis()`
  (about 1,500 calls in 400 files; log timestamps and I/O timeouts stay on the
  wall clock). Cooldowns and timers then survive pausing and reloading.
- Pause, and optionally a turn-based mode where time advances only with the
  player's actions.
- Seed the dice so a session can be replayed.

### 4. Save games

Today the database stores the built world and the player characters only.
Killed mobs respawn, dropped items vanish, timed effects are stripped at
shutdown and running quests restart. The target:
- the built world is read-only content; a save is a separate file;
- saving pauses the clock and writes the whole live state: rooms, mobs, items,
  the player, effects with their remaining time, quest progress, script
  variables, clocks, weather, factions;
- loading rebuilds the world from content and applies the save; several slots;
- the fake JDBC database (`com/planet_ink/fakedb`) and most of
  `DatabaseEngine` go once nothing reads them.

### 5. Single-player society

Accounts, channels, clans, polls, journals, mail, auctions, who lists and
player killing assume other people. Each is removed or turned into its
NPC-facing half (hirelings, an NPC auctioneer, notice boards).

### 6. Content

The shipped world is two rooms. Options: the example areas in
`resources/examples`, generated areas from `resources/randareas`, and a
hand-built starting area.

## Open decisions

- **Time model:** real time with pause, turn-based, or a switch between them.
  Phase 3 makes all three possible; the default is a design choice.
- **Interface:** terminal only, or a graphical or web front end later on the
  same session code.

## Progress log

- Phase 0 done: build script, untracked classes, telnet harness.
- Phase 1 first cut: `sud.sh` creates a character, plays, quits, saves and
  reloads it without a password.
- Phase 2: web, intermud and mail removed (about 1,650 files). Engine suite
  unchanged at 47 of 50.
