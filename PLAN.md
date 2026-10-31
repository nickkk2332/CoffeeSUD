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
tools/smoke.sh            # ten-minute check of the single-player path
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

Everything a new game starts from is in `CoffeeMud/world/` (see phase 4,
step 4f): the areas, custom races, classes, abilities and commands, the
catalog, and the quests. The database in `CoffeeMud/resources/fakedb` is only
a cache of those files; just its schema is in git, and the first start after
a checkout, or after the files change, rebuilds it from them.

When it starts, it lists the saved games and asks which to restore, or starts
a new game. `RESTORE=NAME` restores that save without asking, `RESTORE=NEW`
starts a new game without asking, and `BUILDER=1` plays on the content itself
to change the world (see Authoring).

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
- A title screen (continue, new character, quit). The launcher already lists
  the saved games (phase 4); the profile (phase 5) adds the characters.
- Builder mode as a launch setting that makes the player an Archon was done
  in phase 4 (step 4f), and the multiplayer texts in phase 5 (step 5a).

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

### 4. Save games: done

Before this phase the database held the built world and the player
characters only. Killed mobs respawned, dropped items vanished, timed effects
were stripped at shutdown and running quests restarted. The target:
- the built world is read-only content; a save is a separate file;
- saving pauses the clock and writes the whole live state: rooms, mobs, items,
  the player, effects with their remaining time, quest progress, script
  variables, clocks, weather, factions;
- loading rebuilds the world from content and applies the save; several slots;
- the fake JDBC database (`com/planet_ink/fakedb`) and most of
  `DatabaseEngine` go once nothing reads them. (After 4f the fake database
  holds nothing that is not rebuilt from the world files or kept in a save,
  so it stays, as the engine's working store, until there is a reason to
  replace it.)

Steps:
- **4a. Saved games: done.** The content database (`DBSERVICE`) is never
  written in play: each game runs on a working copy made fresh when it starts
  (`saves/.play-*`). `SAVE [name]` writes `saves/NAME/`: the working copy's
  tables that hold the game's state (characters with their items, abilities
  and followers, accounts, player data such as the calendar and bank
  accounts, journals, clans) and a header with the game clock, the time mode,
  the pause and the dice. `RESTORE NAME` restarts the engine in the same
  process, boots a fresh copy of the content with the save's tables over it,
  and logs the character in; at startup the launcher lists the saves and
  asks. `SAVES` lists them, and `QUIT` offers to save first. `BUILDER=1`
  plays on the content database itself, as before, with no saves. The
  calendar keeps its hour, and how far into it, across a save.
- **4b. The live world: done.** A save also holds `world.xml`: what is in
  every room (mobs, with where they belong, their hit points and what they
  carry; items on the floor, inside each other, with when they rot), mobs
  that are dead and how many ticks until they come back, items taken from
  where they belong that are due to come back there, which doors are open or
  locked, and each area's weather. Restoring boots the world as built,
  empties its rooms and puts all that back before the character logs in. The
  player and their followers come back from the character's tables. Left
  out: mobs that cannot be saved, such as summoned ones, and for the engine's
  built-in mob classes (Goblin, Wolf and the like) the stats their
  constructors roll; generic mobs, which builders make, keep everything.
- **4c. Effects and the rest: done.** Effects with time left (spells,
  poisons, an Archon's freeze) are saved on the player, mobs, items, rooms
  and exits, with the ticks each has left and who caused it, and put back
  once the player is in the game, since the player is often who caused them.
  The player's hit points, mana and movement come back as they were, and so
  do the variables that scripts share.
- **4d. Quests, what the player carries, followers: done.**
  - The player's inventory and followers are saved whole with the world and
    put back in place of what the character's tables bring back, so timed
    effects on carried items, a follower's hurts and effects, and summoned
    followers, which the tables cannot hold, all come back.
  - The engine's built-in mob classes (Goblin, Wolf and the like) roll their
    stats when made; those stats are now saved, so a restored wolf is the
    same wolf.
  - Quests: each quest's winners, and whether it was running, on which step
    and with how long left. A quest running in the save is started again if
    the boot did not, moved to its step and given its time; the mobs and
    items it spawns afresh are then made to match the save (moved to where
    they were, with their hurts and stats, or done away with if they were
    gone). A quest not running in the save is stopped if the boot started
    it. Quest-owned things are left to the quest, not saved as room content.
  - Not exact: a quest that was waiting to start gets a new wait; items a
    quest had given to mobs other than the player come back where the quest
    put them; spawned copies of spawnable quests start over.
- **4e. The world in files: done.** The world (areas, rooms, exits, and
  the mobs and items placed in them) is in `CoffeeMud/world/`, one `.cmare`
  file per area, in the format EXPORT writes and IMPORT reads, laid out a
  tag to a line so that git shows what changed. The files are the world; the
  database's world tables are a copy, which the rest of the engine goes on
  reading. The database keeps a checksum of the files it was built from
  (`world.sum`); at startup, if the files differ (edited by hand, or pulled),
  its world tables are emptied, but for space objects, and the files
  imported. A normal game does this in its own copy, so the content is still
  never written in play; builder mode does it in the content. Builder mode
  writes the world back to the files on SAVE (plain SAVE just writes them)
  and when it quits, rewriting only the files that changed, and not at all if
  a file failed to load, so that a broken file is never written over. The
  Archon's manual, which IMPORT refuses from files of unknown origin, is let
  in from the world's own files.
- **4f. All the content in files: done.** The rest of what a new game
  starts from moved to `CoffeeMud/world/` too, and the database became a
  cache of the files:
  - `areas/`: one `.cmare` file per area, as in 4e;
  - `custom.cmare`: the custom races, classes, abilities and commands, as the
    database held them;
  - `catalog.cmare`: the catalog of mobs and items, each with its category and
    catalog data;
  - `quests/`: one `.quest` file per quest, holding the quest's script as the
    database held it (most are a `LOAD=` line naming a script in
    `resources/quests`).

  The database in `resources/fakedb` is a cache, out of git but for its
  schema. When it is not current with the files (no `world.sum`, or a
  different one), it is rebuilt from nothing: custom objects first, before
  the engine loads its classes, so that they load like any other; then the
  areas, the catalog and the quests when the map loads. A normal game
  rebuilds in its own working database and then copies the content tables and
  the checksum back into the cache, so the next game starts at once; it still
  never writes the world files. Builder mode empties the cache's content
  tables and rebuilds in place, and writes all of the files back on SAVE and
  when it quits. Builder mode also makes the player an Archon on login, as
  the Archon's manual does, so the editors are at hand without finding it.
  - Gone with the old database: the builder characters and account kept in
    it, and files kept in the database's own file system. Builder mode's
    characters live in the cache, so they last until it is deleted, and are
    not in normal games, which start with no characters.
  - Not in files: space objects (ships, planets and the like, outside any
    room), which a rebuild loses; there are none in the shipped world.

### Authoring

- The in-game editors stay: CREATE, MODIFY, DESTROY, COPY, GMODIFY, MERGE,
  the field editor (`CMGenEditor`), IMPORT and EXPORT of `.cmare` area files,
  GENERATE for random areas, and MOBPROG and JavaScript scripting. They are
  used in builder mode, and from phase 4 on they edit the world's content (what
  a new game starts from), never a save.
- Builder mode is `sud.sh BUILDER=1`: the game runs on the database cache
  itself and makes the player an Archon, so edits and the builder's
  `SAVE ROOM`, `SAVE AREA` and the like go into the world. Rooms, exits,
  areas, races, classes, abilities, catalog entries and quests are written
  to the cache as they are made; mobs and items placed with `CREATE` become
  part of a room when it is saved with `SAVE ROOM` or `SAVE AREA`, and a new
  quest is kept with `SAVE QUESTS`. Everything goes to its files in
  `CoffeeMud/world/` on the builder's SAVE (a plain SAVE just writes them) and
  when builder mode quits, rewriting only the files that changed.
- In a normal game the same commands change only that game's copy of the
  world, and every building command (`CREATE`, `MODIFY`, `DESTROY`, `COPY`,
  `GMODIFY`, `MERGE`, `IMPORT`, `GENERATE`, `LINK`, `CATALOG`, the builder's
  `SAVE`) says so: mobs and items made, changed or destroyed stay that way in
  the game's saves, but changes to rooms, exits, areas and the like last only
  until the game ends.
- The world files can also be edited by hand, or new ones added; the next
  start rebuilds the world from them. Commit them to share a change; the
  cache is never committed.
- The MUDGrinder web editor is gone with the web server. `archive/mudgrinder`
  keeps a browsable snapshot of it (open `snapshot/index.html` from disk), its
  page templates and the Java behind them, as a reference for a future visual
  editor.
- A purpose-built visual editor comes later, once the content format is
  settled.

### 5. Single-player society: in progress

Accounts, channels, clans, polls, journals, mail, auctions, who lists and
player killing assume other people. Each is removed or turned into its
NPC-facing half (hirelings, an NPC auctioneer, notice boards).

Steps:
- **5a. What needs other people: done.**
  - Removed, with their help: `WHOIS`, `WIZLIST`, `CHANWHO`, `FRIENDS`,
    `IGNORE`, `AUTONOTIFY`, `POLL` and the whole poll system (its `CREATE`,
    `MODIFY`, `DESTROY` and `LIST`, library and database calls),
    `PLAYERKILL` and `DUEL` (`PLAYERKILL=NEVER`, since every player is the
    same person), `AUCTION` (auctions held on a channel), `ANNOUNCE`, `BOOT`,
    `BAN`, `PASSWORD`, `AUTOFORWARD`, `SUBSCRIBE`, `PROXYCTL`, `RULES` and
    `MOTD`/`NEWS`.
  - Channels: only `INFO` (world news, now with conquests) and `QUESTCHAT`
    (which quests announce on) are left; the chat, clan, admin and auction
    channels are gone, and so are the report journals (`BUG`, `TYPO`,
    `IDEA`, `TASK`, `STUCK`) and the web forums.
  - `CONFIG` no longer offers `PLAYERKILL`, `AUTONOTIFY`, `AUTOFORWARD`,
    `MOTD` or `PRIVACY`. They stay in the option bits saved with each
    character, which are numbered by position, so old characters still load.
  - The new-character texts lost the rules, the web site and the beta-test
    note, and the intermud help, left from phase 2, is gone.
  - Kept, as the half that faces NPCs or the party: `WHO` (the characters in
    the game: with a party, all of yours), `TELL`, `REPLY` and `WHISPER`
    (NPCs listen), `GTELL`, `SERVE` (deities), mail and post offices
    (characters can send each other things), journals as boards in the
    world, auction houses (an auctioneer that bids is content for later),
    hirelings, `SNOOP` (to watch a party member), `AUTOTELLNOTIFY` (tells to
    your other characters), and `SWITCH` and `ACCOUNT`, which become the
    profile.
  - Clans stay for now: NPCs belong to clans, areas can be conquered by
    them, and a party could form one. The clan commands are a later decision.
- **5b. The profile: done.** The engine's account system is on, and its one
  account (`PlayerAccount`, named `Profile`) holds every character of the
  game. Nobody logs in to it: a new game makes it and asks for the first
  character's name, as before, and a restored game plays its character at
  once. `PROFILE` (or `ACCOUNT`) lists the characters, `SWITCH NAME` plays
  another, and `LOGOFF` goes to the list of characters (the account menu,
  cut down to listing, making, retiring and playing them, and quitting),
  where `NEW NAME` makes one. The first character's answer about colour is
  kept for the ones after it. A save holds the profile with the rest of the
  game's state (it is one of the state tables). Characters made before the
  profile, in older saves and in builder mode's cache, join it when they are
  played. Logging in no longer shows news: there is no message of the day,
  poll or shutdown to announce, and `CALENDAR` shows what is coming.
  - Fixed on the way: a character that had just come into the world (a new
    one, a restored one, or one switched to) could not act until its first
    tick, up to four seconds, so its first command that takes time waited,
    and what was typed after it, such as the answer to `QUIT`'s question,
    was taken for the next command. It now starts with a tick's actions and
    no wait from an earlier command. The list of characters keeps what was
    typed ahead instead of eating it as stray network input.
  - Only one character is in the world at a time for now; the party (5c)
    brings in more.
  - At the list of characters, the world goes on in real time, and quitting
    from there does not save.
- **5c. The party.** Profile characters brought into the world beside the
  one being played, each on a session of its own with no terminal behind
  it; commands to add them to the party, send them back and leave them in
  the world; `CONTROL` and `AS`; saves that hold every character in the
  world and where each one is.
- **5d. Party turns** in turn-based time, as designed below.
- **5e. One window per character**, as designed below.

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
- Controlling the party, two ways that can be used together:
  - One window, switching: commands go to the character being controlled,
    changed with something like `CONTROL <name>`, or one command goes to
    another with `AS <name> <command>`. This comes first.
  - One window per character, so that several can be given commands without
    switching. The game opens the windows itself, in the same process, with
    no network connection: to the engine a session is an input and an output
    stream, as `ConsoleSession` already shows with the terminal, so a
    `WindowSession` does the same with a window. A window needs only
    scrolling text with colour, an input line and command history (MUD
    output is lines, not a screen to redraw), in Swing, which comes with
    Java. Later it can show what a terminal cannot, such as health bars, the
    party and a map. Windows are a setting (`WINDOWS=1`), not a replacement:
    the terminal stays, for those who want it and for the tests. The cost is
    that the player's own MUD client cannot be used.
  - Characters not being given commands act as followers: they follow the
    leader and, with `AUTOASSIST`, join fights.
- Time with a party:
  - In real time each character acts as the player types for them, and
    `PAUSE` stops them all.
  - In turn-based time the leader (the first character, or whichever the
    player makes leader) decides when the world moves, by one of two modes,
    chosen with a setting and a command (`PARTYTURNS LEADER` or
    `PARTYTURNS ALL`), as `TIMEMODE` is:
    - **The leader drives** (`LEADER`): giving a companion orders does not
      move time. When the leader acts, the turn resolves: companions with
      orders carry them out, those without do what followers do, and then the
      leader acts. The player can play the leader alone and step in for a
      companion only when needed.
    - **Everyone acts** (`ALL`): the world waits until every enabled
      character has an action, then they act in order. Characters the player
      disables act as followers.
  - Suggested details, to settle when it is built:
    - A turn in `ALL` is one combat round (one 4-second tick), and each
      character acts once in it. A fully timed system, where each character
      is ready again when their last action ends, is more exact but harder
      to follow.
    - The order is the party's order by default, with a command to change
      it; initiative from dexterity is the alternative.
    - A character still busy with a long action (a spell being cast, say) is
      skipped until it ends.
    - The leader's `WAIT` passes the turn for everyone.
    - If the leader dies or leaves the party, the next character in order
      leads; the player can also hand the lead over.

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
- Phase 4: play runs on a copy of the content, which is never written;
  SAVE, RESTORE and SAVES, with the saves listed at startup; the live world,
  timed effects, script variables, the player's inventory and followers,
  built-in mobs' rolled stats and running quests in each save; building
  commands warn in a normal game. Smoke test covers saving, restoring at
  startup and during play, a changed world, effects, a running quest, a
  follower and a carried item's effect coming back, and the content staying
  untouched.
- The world is in `.cmare` files, one per area, rebuilt into the database
  when they change and written back by builder mode. Smoke test covers a
  hand edit picked up in play without touching the content, and a builder's
  edit written to the file and used by the next game.
- All the content is in files: custom races, classes, abilities and commands,
  the catalog and the quests joined the areas, and the database became a
  cache, rebuilt from the files when they change; only its schema is in git.
  Builder mode makes the player an Archon. Smoke and engine tests start from
  the bare schema, as a fresh checkout does; the smoke test covers the first
  game building and caching the world, the next using the cache, and a quest
  made in builder mode written to its own file. A rebuild from the files into
  an empty database, written back out, gives the same files.
