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
tools/smoke.sh            # four-minute check of the single-player path
tools/engine-tests.sh     # the engine's TEST ALL suite, about 15 minutes
```

On Windows, `CoffeeMud\build.bat` and `CoffeeMud\sud.bat` do the same as
the first two. `build.bat` always compiles from clean; run it after pulling.
Both find Java through `findjava.bat`: the JDK in `JAVA_HOME` first, then each
`javac` on PATH, skipping any that do not run.

`sud.sh` takes `NAME=VALUE` arguments that override `coffeemud.ini`, for
example `DBSERVICE=jdbc:fakedb:/some/scratch/copy` to play against a copy of
the database, `TIMEMODE=TURNS` to start turn-based, or `RANDOMSEED=42` to
fix the dice.

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
- Builder mode: a launch setting that makes the player an Archon with the
  in-game editors, instead of the manual in the start room. Normal play does
  not have them.

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
  `tools/mudbot.py`, unless several windows (phase 5) need a local-only
  listener.
- host groups: `CMLib` and `CMClass` keep one set of libraries and classes
  per thread group, so that several separate muds, each with its own world and
  ini file, can run in one Java process. One game needs one set.

### 3. One thread and a game clock: done

- **Game clock.** `core/GameTime` is the world's clock. Game logic (cooldowns,
  durations, decay, command pacing, scripts, quests, laws, calendars) reads it
  instead of `System.currentTimeMillis()`; logs, sessions, the database,
  caches and dates shown as real dates stay on the system clock. Tick groups
  run by the game clock, except the session ticker and the thread health
  check.
- **Pause.** `PAUSE` stops the clock and `RESUME` (or `PAUSE` again) starts
  it. While paused nothing in the world ticks, the prompt says `(paused)`, and
  commands that take time wait in the queue; free ones (`LOOK`, `SCORE`) work.
- **One thread.** Once the world is running, the scheduler thread runs every
  tick, every delayed job and every session, so player commands too, one after
  another. Code that sleeps on that thread runs the queued jobs first. Boot
  still loads on worker threads.
- **Turn-based time.** `TIMEMODE TURNS` (or `TIMEMODE=TURNS` in the ini or on
  the command line) stops the clock; `TIMEMODE REALTIME` runs it again, and
  real time with pause stays the default. When turn-based, a command that
  takes time runs, then the world moves on by as long as it took (a step out
  of combat is a fifth of a tick, a combat action a whole tick), and a command
  that must wait for its actions moves the world to the moment it can go.
  `WAIT [n]` lets n ticks pass (also while paused) and stops early if the
  player is hurt, attacked or moved, so a fight goes a round at a time. The
  scheduler moves the clock one step at a time, up to the next thing due, so
  everything on the way happens in order.
- **Seeded dice.** The dice library and `CMath.random()` share one generator,
  and the game's `Math.random()` calls (128 of them) now use it; objects that
  keep their own generator (most animal mobs, planets, layouts) seed it from
  the shared one rather than the clock, so two wolves made in the same
  millisecond no longer roll the same. `RANDOMSEED=n` seeds it when the world
  loads and again when play starts; when turn-based, the second seeding waits
  until the stopped world has nothing left to run, so the same seed and the
  same commands make the same character. Database keys and generated IDs still
  use `Math.random()`.
- **Not yet exact replay.** A whole session does not yet replay exactly from a
  seed: objects in a tick group tick in an order set by identity hash codes,
  boot is threaded, and real time depends on timing. Phase 4 saves the
  generator's state with the game, which is what keeps a reload from rerolling.

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

### Authoring

- The in-game editors stay: CREATE, MODIFY, DESTROY, COPY, GMODIFY, MERGE,
  the field editor (`CMGenEditor`), IMPORT and EXPORT of `.cmare` area files,
  GENERATE for random areas, and MOBPROG and JavaScript scripting. They are
  used in builder mode, and from phase 4 on they edit the world's content (what
  a new game starts from), never a save.
- Content can also be written by hand as `.cmare` XML.
- The MUDGrinder web editor is gone with the web server. `archive/mudgrinder`
  keeps a browsable snapshot of it (open `snapshot/index.html` from disk), its
  page templates and the Java behind them, as a reference for a future visual
  editor.
- A purpose-built visual editor comes later, once the content format is
  settled.

### 5. Single-player society

Accounts, channels, clans, polls, journals, mail, auctions, who lists and
player killing assume other people. Each is removed or turned into its
NPC-facing half (hirelings, an NPC auctioneer, notice boards).

Several characters at once fits here, as a profile and a party:
- The profile is the player's stored record of all their characters.
  CoffeeMud's account (`PlayerAccount`) already holds several characters and
  is switched off in single-player only because it asks for logins; it can
  become the profile.
- From the profile the player can create a new character (later possibly
  behind a cheat setting), bring one into the world, add them to the party,
  send them back to the profile, or leave them in the world as an ordinary
  character.
- Party members use the normal follower system (FOLLOW, ORDER).
- The engine already runs many player characters in one world, so the work
  is the commands for the above and saving: a save holds the profile, every
  character in it and where each one is.
- Optionally later: one window per character, all in the same world, through
  a connection that only accepts this machine.

### 6. Content

The shipped world is two rooms. Options: the example areas in
`resources/examples`, generated areas from `resources/randareas`, and a
hand-built starting area.

## Open decisions

- **Time model (decided):** both, by a setting; real time with pause is the
  default.
- **Authoring (decided):** in-game editors in builder mode and hand-edited
  files now; the MUDGrinder archived for reference; a new visual editor later.
- **Several characters (decided):** a profile holding all the player's
  characters, with a party built on the follower system (phase 5).
- **Interface:** terminal only, or a graphical or web front end later on the
  same session code.

## Progress log

- Phase 0 done: build script, untracked classes, telnet harness.
- Phase 1 first cut: `sud.sh` creates a character, plays, quits, saves and
  reloads it without a password.
- Phase 2: web, intermud and mail removed (about 1,650 files). Engine suite
  unchanged at 47 of 50.
- Phase 3: game clock, pause, one game thread, turn-based time with `WAIT`
  and `TIMEMODE`, and `RANDOMSEED`. Smoke test covers pause, turn-based time,
  a fight a round at a time and seeded character creation. Engine suite
  unchanged at 47 of 50 after each step. Fixed on the way: the world clock
  skipped every other hour when ticks fell exactly on the hour, and launching
  two games at once could start two clean builds over each other.
