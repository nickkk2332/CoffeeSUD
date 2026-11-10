# CoffeeSUD engine internals

This chapter is for engine developers who change the parts of CoffeeSUD that
CoffeeMud did not have: the save system and world files, the optional-module
framework, instance and author identities, script events, the JavaScript
integration, the party and session layer, the game clock, travel helpers, unique
items, organisations and the authoring checks. For each subsystem it gives the
purpose, the key classes and entry points, the data formats, the invariants the
code relies on, threading, persistence, extension points, the tests that cover
it and the traps. General engine programming (the CMClass/CMLib object model,
message flow, writing new Java classes, build and class loading) is in
[chapter 32](32-engine-programming.md); this chapter assumes you have read it.
Creators who only want the commands should read
[chapter 2](02-world-workflow.md) and [chapter 31](31-saves-and-party.md).

All class names below were checked against the source. Almost every class lives
in `CoffeeMud/com/planet_ink/coffee_mud/core/`; the save classes are in
`core/save/`. Paths in this chapter that start `world/` are relative to
`CoffeeMud/`.

## In this chapter

- [Orientation](#orientation)
- [The save system](#the-save-system)
- [World files](#world-files)
- [The content database as a cache](#the-content-database-as-a-cache)
- [Optional modules](#optional-modules)
- [Instance IDs](#instance-ids)
- [Author identities, story records and story text](#author-identities-story-records-and-story-text)
- [Script events and the trigger plumbing](#script-events-and-the-trigger-plumbing)
- [JavaScript integration](#javascript-integration)
- [The party and session layer](#the-party-and-session-layer)
- [Game time, time modes and pause](#game-time-time-modes-and-pause)
- [Travel helpers](#travel-helpers)
- [Unique items](#unique-items)
- [Organisations and guilds](#organisations-and-guilds)
- [The authoring checks](#the-authoring-checks)
- [The isolated playtest](#the-isolated-playtest)
- [Tests](#tests)
- [Recipes for common changes](#recipes-for-common-changes)
- [Pitfalls](#pitfalls)
- [See also](#see-also)

## Orientation

### What CoffeeSUD added

CoffeeSUD is CoffeeMud 5.11 with the multi-user services removed and a
single-player layer added. The additions are mostly static utility classes in
`core/`, not libraries registered with `CMLib`. They are reached directly
(`GameTime.now()`, `SaveGame.save(...)`, `ScriptEvents.game(...)`), they keep
their state in static fields, and most have a `reset()`, `clear()` or `forget()`
that `SaveGame.beforeBoot` or the relevant boot step calls so a restore inside
the same process starts clean. When you add one, follow that pattern and make sure
the reset is called from `beforeBoot` (see [Boot and restore
flow](#boot-and-restore-flow)).

Comments in the source carry short feature tags: `B2`, `B6` (builder tools),
`ST6` (script triggers), `JS7` (JavaScript), `S9` (saves), `W28` (world
systems), `DT13` (developer tools), `Q4` (quests) and so on. They match the
feature ids in `audit/project-feature-status.csv` and the per-theme audits
(`audit/S-saves-and-world-files.md` describes the save and world-file features
as proposed; the code below is what was built).

### Package map

| Package or file | Holds |
| --- | --- |
| `application/SUD.java` | The single-player entry point: reads `coffeemud.ini` and the command line, picks a save, sets the launch state, calls `MUD.main`. |
| `core/save/SaveSlots` | Saves on disk: names, locks, generations, manifests, recovery, working databases. Knows only files. |
| `core/save/SaveGame` | Save and restore lifecycle: `beforeBoot`, `afterBoot`, `playerLoggedIn`, `save`, `restore`, save rules, header. |
| `core/save/WorldState` | The live world as XML (`world.xml`): `capture` and `apply`. |
| `core/save/WorldFiles` | The world folder: export and import, checksums, the content cache, backups, per-file tracking. |
| `core/save/WorldSettings` | `world/world.ini` whitelist. |
| `core/save/SaveTools` | `SAVES INFO/COPY/DIFF`, rotating saves, the `AutoSaver` ticker. |
| `core/WorldAssets`, `core/UndoLog` | Tracked asset paths; builder UNDO history. |
| `core/ModuleDefinitions`, `core/OptionalModules` | Optional module manifests and campaign state. |
| `core/InstanceIds`, `core/AuthorIds`, `core/StoryRecords`, `core/StoryText` | Identities and conditional text. |
| `core/ScriptEvents`, `core/ScriptHooks`, `core/HostScripts`, `core/WorldHost` | Script triggers fired from engine code. |
| `core/JSScope`, `JSStore`, `JSReload`, `JSRuntime`, `JSJobs`, `JSEvents`, `WorldJS` | JavaScript integration. |
| `core/Party`, `Common/PartySession`, `Common/WindowSession`, `core/CharacterWindow`, `core/ConsoleInput` | Party, sessions and console. |
| `core/GameTime`, `core/TimeLock` | Game clock, pause, turn-based time, time-mode zones. |
| `core/Travel` | Shared travel rules and interfaces. |
| `core/Uniques`, `core/Organizations`, `core/Guilds` | Unique catalog items; clan rules; guild scaffolding. |
| `core/WorldCheck`, `ScriptCheck`, `PropCheck`, `AuthoringCheck`, `AuthoringSchema` | Static checks. |
| `core/IsolatedPlaytest`, `core/PlaytestRunner` | Disposable playtest campaigns. |

### The game thread

In single player the scheduler thread of `core/threads/ServiceEngine` runs
everything once the world is up: every tick, every delayed job, every session and
everything handed to `CMLib.threads().executeRunnable(Runnable)`, one at a time.
The relevant statics are `ServiceEngine.hasGameThread()`,
`ServiceEngine.isGameThread()`, `ServiceEngine.beforeSleep()` (runs queued game
jobs before the game thread sleeps, so code that hands work off and waits for it
still finishes) and `ServiceEngine.runWhenStill(Runnable)` (runs a job once the
stopped clock has nothing left to run; used to seed the dice).

Consequences you must design for:

- Anything that reads or changes the world (rooms, mobs, items, effects) runs on
  the game thread. Boot still loads on worker threads, so code in the load path
  must not assume the game thread exists yet. `SaveGame.afterBoot` therefore
  uses a private `runOnGameThread` that waits (up to 200 x 50 ms) for
  `hasGameThread()` and then blocks until its job finishes (120 second limit).
- `SaveGame.save` runs on the game thread, so the world stands still while it
  writes. That is what makes `WorldState.capture` consistent without locks.
- Never block the game thread on real time. Blocking Java calls cannot be
  preempted; JavaScript that must wait uses `cm.background` (see
  [JSJobs](#background-jobs-jsjobs)).
- Real-time concerns (logs, file times, input timeouts, `SaveSlots` times) use
  the system clock. Game logic uses `GameTime.now()`. Never mix them in one
  comparison.

### Boot and restore flow

```text
SUD.main
  CMProps.setSinglePlayer(true); MUD.consoleMode = true
  readSettings(args)                  coffeemud.ini, then NAME=VALUE arguments
  SaveGame.configure(saveDir, builder)
  SaveSlots.acquire + recover          (normal games only) own the save folder
  WorldFiles.configure(WORLDDIR)
  DEV / WINDOWS / CHECK flags
  choose a save: RESTORE=NAME|NEW, else chooseSave(); SaveSlots.validateFormat
  OptionalModules.launchedWith(MODULES, MODULESETTINGS); SaveGame.moduleStartupProblem
  MUD.main(fullArgs)                  engine boots
    SaveGame.beforeBoot(DBSERVICE)    reset families, working db, header -> clock/modes/party
    WorldFiles.afterDatabaseConnect   custom objects into the db (rebuild only)
    WorldFiles.loadWorld              rebuild from files or read the cache
    console session starts
    SaveGame.afterBoot(session)       clocks, WorldJS.onBoot, WorldState.apply, dice, login name
    SaveGame.playerLoggedIn(mob)      WorldState.finishRestore, reports, WorldHost.start, GAME_PROG
  play ...
  SaveGame.restore(slot)              sets nextBoot, shuts the host down; MUD restarts the engine
                                      and the flow above repeats from beforeBoot
```

`SUD.main` ends with `System.exit(checkOnly ? WorldCheck.exitCode() : 0)` and
releases the save folder in a `finally` block for normal games. Settings
precedence, lowest first: `coffeemud.ini`, the fixed `SINGLE_PLAYER_SETTINGS` in
`SUD`, `world/world.ini` (whitelisted keys only, see `WorldSettings`), the
world's class paths (`worldClassPaths`), then the command line.

Source: [SUD.java](../../CoffeeMud/com/planet_ink/coffee_mud/application/SUD.java),
[SaveGame.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/save/SaveGame.java).

## The save system

### Concepts

A running normal game has two databases and a folder of files:

- The **content database** (`DBSERVICE`, a fake JDBC database under
  `resources/fakedb`) is a cache of the world files. It is never written in
  normal play.
- The **working database** (`SAVEDIR/.play-<millis>/`) is made fresh at every
  start: the schema, the content tables copied from the cache, and, when
  restoring, the saved state tables over them. The engine's `DBSERVICE` setting
  is overridden to point at it (`SaveGame.bootOverrides`).
- A **save** (`SAVEDIR/<name>/`) is a snapshot of the working database's state
  tables plus a header and `world.xml`.

Builder mode (`BUILDER=1`) plays directly on the content cache, has no saves and
writes world files on SAVE and quit. `SaveGame.isBuilderMode()` selects between
the two worlds in nearly every method of the save package.

### On-disk layout (SaveSlots)

```text
SAVEDIR/                       "saves" by default (SAVEDIR=)
  .owner.lock                  advisory lock, held for the process lifetime; never deleted
  NAME/                        one save; NAME: letters, digits, - and _, max 32, starts alnum
    header.properties          who, where, when, clock, mode, party, dice, world sums
    db/fakedb.data.CMCHAR ...  the STATE_TABLES, copied from the working database
    world.xml                  WorldState.capture output
    world-manifest.properties  the world files this game loaded (path -> size-crc)
    modules.json               present only when optional modules are in the campaign
    .complete                  SHA-256 of every other file (the manifest)
  .new-NAME/                   a save being written
  .old-NAME/                   the previous complete generation, retained until the next write
  .damaged-NAME-<millis>/      a public generation recovery set aside
  .play-<millis>/              a running game's working database
```

`SaveSlots.STATE_TABLES` is `CMCHAR, CMCHAB, CMCHIT, CMCHFO, CMCHCL, CMACCT,
CMPDAT, CMJRNL, CMCLAN, CMCLIT`. Those are the tables a save carries; every other
table is content and comes from the cache. `WorldFiles.CONTENT_TABLES` is
`CMAREA, CMROOM, CMROEX, CMROCH, CMROIT, CMGRAC, CMCCAC, CMGAAC, CMCDAC,
CMQUESTS`. If you add a table that holds game state, add it to `STATE_TABLES`;
if it holds content built from files, add it to `CONTENT_TABLES` and teach
`WorldFiles` to build it. The two lists must never overlap.

### Locks, generations, manifests and recovery

`SaveSlots` is the only class that touches slot directories. The methods that
change or scan the folder (`acquire`, `release`, `list`, `prepareWorkingDatabase`,
`write`, `delete`, `recover`) are `static synchronized`; `isValidName`, `find`,
`validateFormat` and the header readers are not.

**Ownership lock.** `acquire(saveDir)` opens `.owner.lock` and takes a
`FileChannel.tryLock()`. If another process holds it the call throws
`IOException("Another game is using the save folder ...")`. The lock is
per-canonical-path, kept in a static map, and released by `release(saveDir)`
(which also deletes this process's working databases) or by a JVM shutdown
hook (`closeAll`). The lock file is never unlinked: a stale file from a crash is
harmless because the OS drops the lock when the process dies. `SUD.main`
acquires before showing the save menu, so a second process fails before it can
delete anything. `list`, `find`, `prepareWorkingDatabase`, `write`, `delete` and
`recover` all call `acquire` themselves; `list` swallows the failure and gives a
read-only listing.

**Working databases.** `prepareWorkingDatabase(contentDb, saveDir, slot, tables)`
calls `acquire` and `recover`, validates the slot's format if one is given
(`validateFormat`), deletes abandoned `.play-*` directories
(`deleteWorkingDatabases`, which skips any path in the `activeWorking` set, so an
aliased path to a live game is safe), creates a new `.play-<millis>` (suffix
`-n` on collision), copies `fakedb.schema`, the requested content tables, then the
save's `db/` files over them. `releaseWorkingDatabase(work)` must be called only
after the database has closed; `SaveGame.beforeBoot` does so for the previous
game's directory.

**Writing a save.** `write(saveDir, slot, workingDb, header, files)`:

1. `acquire`, `recover`.
2. Build `.new-NAME/`: `db/` with each `STATE_TABLES` file copied (each copy
   `fsync`ed), every entry of `files` (UTF-8, `fsync`ed), then
   `header.properties`.
3. Write `.complete`: SHA-256 per file (relative path with `/`) of everything in
   the new directory except `.complete` itself, then `fsync` the directories.
4. Delete `.old-NAME`; rename `NAME` to `.old-NAME`; rename `.new-NAME` to `NAME`;
   `fsync` the save folder. If the second rename fails, `.old-NAME` is renamed
   back and the write throws.
5. `.old-NAME` is kept: the previous complete generation survives until the next
   successful write of the same name.

**Completeness.** `complete(dir)` is true when `header.properties` is non-empty,
`db/` is a directory, `world.xml` is a file, and either `.complete` is missing
(legacy version-1 saves) or every file it lists exists with a matching digest.
A file the manifest does not list (a `.DS_Store`, a sync marker) does not make a
save look damaged; this is deliberate and covered by a test.

**Recovery.** `recover(saveDir)` runs under the ownership lock before listing,
restoring, writing or deleting. For each name that has a `.old-` or `.new-`
sibling and whose public directory is not complete:

- if the public directory is missing and `.new-` is complete, promote `.new-`
  (a crash between the two renames keeps the newer save);
- else promote `.old-` if complete, else `.new-` if complete;
- an existing damaged public directory is first renamed to
  `.damaged-NAME-<millis>` so it can be inspected;
- an unfinished `.new-` (no `.complete`) is never promoted.

**Format checks.** `validateFormat(saveDir, slot)` is called before any live state
is replaced (launch, `RESTORE`, and `SaveGame.restore`). It requires `complete`,
header `version` 1 or 2 (absent means 1), for version 2 a header
`moduleformat=1` and a `modules.json` file, a `world.xml` that looks like a
`<WORLD>` element, and a `<WORLD VERSION=...>` attribute of `1` if present.
`SaveGame.VERSION` is 1; `SaveSlots.MODULE_FORMAT_VERSION` is 2 and
`SaveGame.writeSnapshot` raises the header version to 2 only when the campaign
contains optional modules (`OptionalModules.hasModules()`), so ordinary saves
stay readable by older engines.

**Delete.** `delete` removes the slot and its `.old-` and `.new-` siblings.

### The header

`SaveGame.writeSnapshot` writes these `header.properties` keys:

| Key | Meaning |
| --- | --- |
| `version` | 1, or 2 with modules |
| `dev`, `cheat` | `true` if the game was DEV=1 or had cheats on |
| `character`, `level`, `class`, `race` | The played character |
| `party` | Names of other characters in the world |
| `place`, `area` | Room display text (colours removed) and area name |
| `saved` | System milliseconds when written |
| `gametime` | `GameTime.now()` |
| `timemode`, `timemodebefore` | `TURNS` or `REALTIME`; the mode a `Prop_TimeMode` zone took over from |
| `paused` | Whether the clock was paused |
| `turns` | `ScriptEvents.turns()` |
| `difficulty`, `exchange`, `genseed` | `GameDifficulty.current()`, `Exchange.saved()`, `GenSeed.saved()` |
| `savemode` | `SaveGame.SaveMode` name |
| `gamedate` | Short in-game date |
| `worldsum`, `areasums` | `WorldFiles.loadedChecksum()` and `name=crc;...` of the area files this game loaded |
| `partymeet`, `partyfight`, `partyturns`, `partyorder`, `partyalone` | Party settings |
| `dice` | `CMath.getRandState()` |
| `moduleformat` | `1` when `modules.json` is present |

When adding a persistent global setting, the header is the right place for a
single scalar (read it in `beforeBoot` if it must be set before the boot, or in
`afterBoot` if it needs the world). Anything structured belongs in `world.xml`.

### Lifecycle (SaveGame)

**`beforeBoot(contentDbService)`** runs before every boot, including an
in-process restore. In order it: resets `OptionalModules`, `AbilityComposition`,
`CombatProfiles`, `ItemModifiers` and `JSJobs`; clears `ScriptMenus` and
`Narration`; resets the save mode to `ANYWHERE`; stops the previous autosaver;
releases the previous working database; takes `nextBoot` and `nextFresh`; clears
the world-change report; calls `OptionalModules.boot(...)` (module preflight and
the player-facing module report); then, if `DBSERVICE` is a fake database, either
points at the cache (builder mode, clearing content tables when the files changed)
or prepares the working database (normal mode) and calls
`WorldFiles.beginBoot(rebuild, cache)`. When restoring it finally reads the header: it runs `compareWorlds`, then sets the
time mode, `TimeLock.restore`, the save mode, the pause, `GameTime.set`, and the
`Party` settings.

**`afterBoot(session)`** runs on the game thread for the first job
(`loadClocks`, `WorldFiles.loadSpace`, `WorldState.noteContentQuests`,
`noteContentShapes` in normal mode, `WorldJS.reset`, `JSProgression.reset`,
`QuestTimers.startTicking`, `WorldJS.call("onBoot")`). Then it sets
`WorldHost.forget`, the `bootEvent` (`NEWGAME` or `RESTORE`), turns, difficulty,
exchange and generator seed; for a restore it applies `WorldState.apply(world.xml)`
on the game thread, clears `worldPending`, restores the dice last (so play rolls on
exactly where the save left off) and gives the session the saved character name
as `LOGIN_ACCOUNT`.

**`playerLoggedIn(mob)`** applies the dev group, tops up the character's actions,
calls `WorldState.finishRestore(mob)`, prints the world-change report and the
module report, starts `DevLog`/`InputRecord`, runs `PuzzleCheck` once (and
`QuestCheck` in builder mode), makes the player an Archon in builder mode, starts
the autosaver (normal mode, not for a background party member), starts the world
host (`WorldHost.start`), fires `ScriptEvents.moved` into the area, fires GAME_PROG
`NEWGAME` or `RESTORE`, calls `WorldJS.call("onPlayerEnter")` and resumes saved
menus and narration.

**`save(mob, slot, quitting)`** checks `whyNotSave` (save rules) and then
`writeSnapshot`: `ItemCrafting.inCommit()` refuses mid-transaction;
`ScriptEvents.gameCheck(mob, slot)` runs `onBeforeSave` and GAME_PROG `SAVE`,
either of which may refuse; `InstanceIds.prepareSave()`; `CMLib.players().savePlayer`
for the player and each party member; `saveClocks()`;
`ServiceEngine.beforeSleep()`; header; `OptionalModules.snapshot()` into
`modules.json`; `WorldFiles.loadedManifest()` into `world-manifest.properties`;
`WorldState.capture(mob)` into `world.xml`; `SaveSlots.write`. On success
`currentSlot` becomes the slot (unless writing to a destination other than the
save folder, as the isolated playtest does).

**`restore(slot)`** refuses in builder mode and in an IRONMAN game, finds the slot,
validates the format, runs `OptionalModules.prepare(...)` as a preflight, sets
`nextBoot` and starts a thread named `Restore` that calls
`host.shutdown(null, false, null)`. The launcher re-enters `beforeBoot`.

### Save rules

`SaveGame.SaveMode` is `ANYWHERE`, `NOTINCOMBAT`, `SAVEPOINTS`, `IRONMAN`; it is
set by `SAVEMODE` at new-game time and stored in the header. `whyNotSave` returns a
refusal reason or null:

- `IRONMAN`: a named slot other than `ironmanSlot(mob)` is refused; a quit save is
  always allowed.
- `Prop_NoSave` on the room or its area refuses (`MSG=` text).
- Any mode other than `ANYWHERE` refuses while `partyFighting(mob)`.
- `SAVEPOINTS` requires `Prop_SavePoint` on the room or area.

`autoSave(mob, slot)` (used by `Prop_AutoSave`, `Prop_Checkpoint SAVE=AUTO` and
the `AutoSaver`) skips the place rules (the builder put the autosave there) but
still refuses mid-fight outside `ANYWHERE`. `placeEffect(R, id)` looks on the room
and then its area.

### Autosave and checkpoints

`SaveTools.AutoSaver` is a `Tickable` started by `SaveGame.startAutoSaver` with
`CMLib.threads().startTickDown(autoSaver, Tickable.TICKID_MISCELLANEOUS, 1)`. It is
configured by `AUTOSAVE` (minutes of game time, or `ON-AREA-CHANGE`; the code tests
`startsWith("ON-AREA")`) and `AUTOSAVEKEEP` (default 3). The interval converts
minutes through `CMProps.getMillisPerMudHour()` and is measured with `GameTime.now()`,
so a paused or turn-based game does not autosave until time advances.
Autosaves take turns through `SaveTools.nextInTurn("autosave", keep)`, which picks
`autosave1..n`, the one never written or oldest. `CHECKPOINT` and `ROLLBACK`
(`Commands/Checkpoint`) use three slots named `checkpoint1..3` through the same
helpers: `nextInTurn(CHECKPOINT_SLOT, CHECKPOINTS)` to write and
`newestInTurn` to roll back. `ROLLBACK` is only `SaveGame.restore(newest)`.

`SAVES` (and `RESTORE` with no argument) is `Commands/Restore`; its subcommands are
`SAVES INFO name`, `DELETE name`, `COPY from to`, `DIFF a b` and `CHECK`. `SAVES
CHECK` calls `WorldState.notKept(mob)`, which reports what a save would not keep;
extend it when you add a thing a save cannot hold. `SaveTools.copy` copies the
save folder tree; `SaveTools.info` and `SaveTools.diff` produce the `INFO` and `DIFF` reports.

### What world.xml holds (WorldState)

`WorldState.capture(player)` returns one `<WORLD VERSION="1">` document. It must run
on the game thread; it throws `IllegalStateException` during an item-crafting
commit. It first calls `InstanceIds.prepareSave()` so every mob and item has an ID
before any record refers to another, and uses an identity map (`written`) that gives
each mob, player or item a numeric `SID` as it is written, so `<RIDES>` can refer to
things written elsewhere in the file.

Sections in write order (`captureWorld`):

| Tag | Content |
| --- | --- |
| `MENUS`, `NARRATION` | `ScriptMenus.toXML()` and `Narration.toXML()` pending choices and unread pages |
| `PLAYER`, `PARTY` | The played character and the other characters in the world (`playerXML`) |
| `ROOMSHAPES` | Rooms made or changed in play (`NEWROOM`, `CHANGEDROOM`), destroyed rooms (`GONEROOM`), changed areas (`CHANGEDAREA`) |
| `ROOMS` | Per room: `ID`, room effects and STATES, doors (`DOOR DIR IID OPEN LOCKED`), mobs, items |
| `SPACE` | Space objects (`WorldFiles.spaceItems()`) |
| `DEAD` | Dead mobs and ticks until they return (`DEADMOB LEFT`) |
| `TAKEN` | Items taken from their rejuvenation room and due back |
| `WEATHER` | Per area: current, next, held weather and time left |
| `AREAS` | Per-area STATES |
| `VESSELS` | Boardable travel state |
| `SCRIPTVARS` | Every VARSCOPE table (scripts' shared variables) |
| `JSSTATE` | `JSStore.toXML()` |
| `QUESTS` | `questsXML(player)`; made-in-play quests are saved whole |
| `QUESTRECORDS` | `QuestRecords.toXML()` |
| `YIELDS` | `Yields.toXML()` gathered-out rooms |
| `UNIQUES` | `Uniques.toXML()` |
| `RIDES` | `<RIDE R=sid ON=sid/>` pairs |
| `COMBAT` | `<FOLLOW ACTOR LEADER/>` and `<FIGHT ACTOR TARGET RANGE ACTIONS/>`, by instance reference |

Per-object state is carried by three hooks in `statesXML(P)`, written inside a
`<STATES>` tag on every saved mob, item, room and area:

- `<BSTATE ID=behaviorID N=index>` from `Behavior.getStat("STATE")`; restored with
  `setStat("STATE", xml)` by ID and position among behaviors of that ID.
- `<ASTATE ID N>` from `Travel.Stateful.travelState()` on effects.
- `<SSTATE KEY=hex>` from `ScriptingEngine.getStateXML()`, keyed by the hex hash of
  the script text so editing a script drops its stale state.

Timed effects are written by `effectsXML(P)` with ticks left and the caster; they are
put back late (see below) because the caster is often the player.

### Restore order

`apply(worldXML)` runs on the game thread before the player logs in (it is called
from `afterBoot`); `finishRestore(mob)` runs from `playerLoggedIn`. The order is
load-bearing:

```text
afterBoot (game thread)
  WorldState.apply(world.xml)
    clear transient maps; note newRooms          booted rooms the save does not have (D-SP-2)
    startRunningQuests(QUESTS)                   quests running at the save take over the world's mobs again (D-SP-11)
    clearWorld()                                 empty every other room of boot-time mobs/items
    ROOMSHAPES  -> putShapes                     recreate made/changed/destroyed rooms and areas
    SPACE       -> putSpace                      before rooms: a ship's rooms must exist
    ROOMS       -> fillRoom per room             skips rooms of FRESH= areas; notes missing rooms
                -> putBackLaterRooms(false)      vessels carried inside another
    VESSELS     -> remembered in laterVessels
    DEAD        -> bringBackDead
    TAKEN       -> armRejuv
    SCRIPTVARS  -> setScriptVars                 (again after QUESTS, which may reset its own)
    QUESTRECORDS, YIELDS
    QUESTS      -> setQuests                     restart, set step/time, match spawned mobs/items
    RIDES       -> laterRides; putBackRides      those whose mount and rider are both back
    PLAYER/PARTY tags stashed for finishRestore
    AREAS       -> putStates
    WEATHER     -> climate current/next/held
    JSSTATE     -> JSStore.fromXML               before onRestore
    MENUS, NARRATION
    UNIQUES     -> Uniques.fromXML               last, over what coming back counted
  dice restored
  worldPending = false
playerLoggedIn
  WorldState.finishRestore(mob)
    putBackPlayer(mob), bring in each party member, set leaders
    putBackLaterRooms(true); putBackVessels      characters' own vessels are back
    putBackRides                                 the players and followers are back
    laterEffects -> putEffects                   timed effects, with causes resolvable
    MovingRoom.restoreAll()
    putBackCombat()                              FOLLOW and FIGHT, by instance id
    CommonSkill.resolveActivityLinks             then for every Physical via InstanceIds.visit
    takeBackRemadeAreas
  ... reports, WorldHost.start, GAME_PROG RESTORE, WorldJS onPlayerEnter, ScriptMenus.resumeSaved
```

Invariants:

- Nothing in `apply` may send messages that scripts hear. `SaveGame.isWorldPending()`
  is true from `beforeBoot` until the world is back; scripts check it so they wait
  rather than re-run triggers against an unrestored world.
- `restored` (SID to object) is cleared in `finishRestoring` after effects, because a
  skill's ACTIVITY may name something that was restored.
- A save made against one version of the world and restored against another uses
  `compareWorlds`: it compares `worldsum` and `areasums`, collects changed, new and
  gone area files in a report told at login, and `FRESH=` (`WorldState.setFresh`)
  makes `fillRoom`, `bringBackDead` and `TAKEN` skip rooms of the named areas
  (`CHANGED`, `ALL`, or a comma list) so they keep the world's current contents.
  Sums of different kinds (`sumKind` is the part before the first dash) are not
  compared as whole worlds.
- Room shapes: `noteContentShapes()` hashes the shape of every non-vessel room and
  area at boot (`getRoomShapeXML`, `getAreaObjectXML`), so a later save writes only
  what play changed; this is why author edits to an unchanged room reach a restored
  game.

### Extension points: persisting new state

Pick the lightest mechanism that fits:

1. **A property or behaviour's own state.** Implement `getStat("STATE")` and
   `setStat("STATE", xml)` (behaviours) or `Travel.Stateful` (effects). Times must be
   saved as game time left, not absolute milliseconds. The save calls them through
   `statesXML`/`putStates`. For JS-defined classes declare `STATE`/`ACTIVITY`
   (reload also uses it, see [JSReload](#jsreload)).
2. **A script variable.** `MPSETVAR`/`cm.setVar`; all VARSCOPE tables are saved
   (`SCRIPTVARS`).
3. **A JS store.** `cm.store(key)` JSON, `JSSTATE`.
4. **A module record.** `OptionalModules.put` for world, character, profile, item,
   relationship or organization scope (see [Optional modules](#optional-modules)).
5. **A tattoo or `Prop_StoryRecords` on a character.** Travels with the character's
   tables (`CMCHAR`).
6. **A new `world.xml` section.** Add `fooXML()` to `captureWorld` and a `fromXML` in
   `apply` at the right point in the restore order, with a version-tolerant reader
   (missing tag means "no data"). Add a reset to `SaveGame.beforeBoot` if it keeps
   static state. If it cannot be saved, add a line to `WorldState.notKept`.
7. **A header scalar.** For single global settings.

### Persistence of the player

`CMLib.players().savePlayer(mob)` writes the character tables; `playerXML` adds
what the tables lose (hit points, mana, movement, inventory and followers whole,
entry limits via `limitsXML`). The restored character is rebuilt from the tables
and then `putBackPlayer` overwrites inventory and followers from `world.xml`.
`SUD` sets the login account from the header `character` field.

### Tests

- `tools/SaveStorageTest.java` run by `tools/storage-tests.sh`: real second-process
  exclusion (exit code 23), active and abandoned working copies, canonical folder
  aliases, interrupted replacement recovery, retention of the last complete
  generation, stray files not rolling a save back, a crash between the renames,
  damaged public generation falling back and being preserved as `.damaged-`,
  recovery of a complete first save from `.new-`, refusal to promote an unfinished
  snapshot, unsupported save and world formats, the module-aware format, deletion
  of recovery generations, and a crashed owner's stale lock file.
- Played groups of `tools/fixes.sh`: `saves`, `saves2` to `saves5`, `review`,
  `remaining`, `modules`; JavaScript fixtures `review-core.js`, `review-workflows.js`,
  `review-background.js`, `s9-states.js`, `s5-law.js`, `item1-uniques.js`,
  `js7-store.js`, `t-instances.js`.

### Pitfalls

- `SaveSlots.list` has side effects (acquire and recover). Do not call it from a
  place that must not own the folder.
- Never write a file inside a slot after `write` returns; the manifest would no
  longer match. Add new per-save files to the `files` map in `writeSnapshot` so
  they are digested.
- `apply` depends on the order of sections; adding a section that needs another to be
  back first means inserting it after that section, not at the end. `UNIQUES` is last
  on purpose.
- `capture` stores references by `InstanceIds.reference`; if an object is not in
  `written` its SID is unusable (`sidOf` returns 0 for things not in the save).
- Anything that keeps a pointer to a live object across a restore is wrong: the
  restore destroys and recreates rooms' contents. Use IDs and resolve late.
- Do not call `GameTime.set` outside a restore; cooldowns hold absolute game times.

## World files

### Purpose

The world folder (`WORLDDIR`, default `world`) is the content a new game starts
from. The files are the source of truth; the fake database is a cache built from
them. `WorldFiles` reads them at boot (rebuilding the cache when they changed),
writes them back in builder mode, and tracks which files were changed outside the
game. Creator-facing workflow is in [chapter 2](02-world-workflow.md).

Source: [WorldFiles.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldFiles.java),
[WorldAssets.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/WorldAssets.java),
[WorldSettings.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldSettings.java),
[UndoLog.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/UndoLog.java).

### Folder layout as the code knows it

| Path under `world/` | Written and read by | Notes |
| --- | --- | --- |
| `areas/NAME.cmare` | `exportAllReadable`, `importArea` | One area each: rooms, exits, placed mobs and items. File name from `fileNameFor(area name, ".cmare", taken)`. |
| `dev/NAME.cmare` | same | Areas tagged `DEV` (`WorldFiles.DEV_TAG`); loaded only in builder mode and DEV=1 (`devAreasWanted`); excluded from `checksum()`; own `dev.sum`. |
| `custom/{races,classes,abilities,commands,behaviors}/ID.cmare` and `ID.script` | `customFiles`, `afterDatabaseConnect` | One file per generic race, class, ability, command or generic behaviour; a long SCRIPT goes to an `ID.script` sidecar. The old single `custom.cmare` is still read and is deleted by the next export. |
| `catalog.cmare` | `catalogText`, `importCatalog` | Catalog mobs and items with category and catalog data. |
| `quests/NAME.quest` | `questRows`, `importQuests` | One quest's script (often a `LOAD=` line). |
| `templates.cmare`, `clans.cmare` | `templatesText`, `clansText` | Shared builder templates (player data `SYSTEM_BTEMPLATE_SHARED`), clans with no player members. |
| `space.cmare` | `spaceText`, `loadSpace` | Items in space; put in space at every boot (no database table holds them). |
| `journals/NAME.xml` | `journalFiles` | Journals of world `StdJournal` items plus `SYSTEM_CRON`. |
| `vfs/...` and `vfs/.index` | `writeVFS`, `importExtras` | One file per entry of the database file system; `.index` has tab-separated `path, integer mask, author`. |
| `laws/AREA.ini` | `lawFile`, `writeLaw` | An area's own law (builder mode). |
| `js/`, `scripts/`, `routes/` | tracked only | JavaScript, world scripts (`WorldHost`) and route files; hand-edited. |
| `world.ini` | `WorldSettings` | Whitelisted settings; tracked for display but excluded from the checksum and from `managedPaths()`. |
| `modules/`, `compositions/`, `combat/`, `itemmods/`, `affixes.xml` | read by their families | Hand-authored; tracked, but "versioned apart" (below). |
| `vocab/`, `lore/`, `text/`, `resources/`, `kits/`, `encounters/`, `classes/` and a list of single files | `WorldAssets` | Live external assets; see below. |
| `tests/` | `PlaytestRunner` | `.play`, `.test`, `.js` world tests. |
| `.backup/`, `.history/` | `backup`, `UndoLog` | Not part of the world. `.backup` gets a `.gitignore` of `*` when first made. |
| `FILE.builder` | `mayChange` | The game's copy of a file that was changed outside the game. |

Area, catalog and similar files are written "readable": `layOut(xml)` puts one tag
per line and a thing's own `text` as an element marked `xml="1"` so git diffs show
what changed. `collapseText` is its exact inverse (escaping the readable form back
to the one-line form the engine reads). Both are package-private statics in
`WorldFiles`; round-trip safety is the invariant, and `WorldFiles` compares files
through `collapseText` so a file written the old (escaped) way is not taken for a
changed area.

### Checksums and world sums

Every tracked file has a per-file sum from `sumOf(File)`: `length + "-" + hex(CRC32
of bytes)`. `fileSums()` is a path-to-sum map over `trackedPaths()` =
`managedPaths()` plus `WorldAssets.paths()`; `managedPaths()` is `contentPaths()`
minus `world.ini` plus `dev/*.cmare`. `contentPaths()` collects: `areas/*.cmare`,
`quests/*.quest`, `custom.cmare`, `custom/<kind>/*.cmare` and `*.script`,
everything under `js/`, `scripts/` and `routes/`, `catalog.cmare`, `world.ini`,
`templates.cmare`, `clans.cmare`, `space.cmare`, `journals/*.xml` and everything under
`vfs/` (never `*.builder`).

The **world sum** is `manifestChecksum(manifest)`: `"3-" + count + "-" + hex(CRC32)`
over the sorted `path\0sum\0` pairs, skipping `dev/` and every path
`WorldAssets.versionedApart` (`modules/`, `compositions/`, `combat/`, `itemmods/`,
`affixes.xml`). The leading `3` is the sum kind; a save with another kind cannot be
compared whole (`SaveGame.sumKind`), so only its area files are compared. Three
different sums matter:

- `checksum()`: the sum of the files on disk now. The cache stores it in
  `fakedb/world.sum` (`SUM_FILE`); `isCacheCurrent(cache)` is
  `checksum().equals(readSum(cache))`.
- `bootChecksum()`: the sum when the game booted (set by `beginBoot`).
- `loadedChecksum()`: the sum of `knownSums`, the files **as this process loaded or
  last wrote or reloaded them** (`markLoaded`, the `accept*Changes` methods and the
  export update it). This is what a save records in `worldsum`, so a save made after
  a live `WORLD RELOAD` records the world the game really has.

`areaSums()` is a separate `file name -> hex CRC32` map of just `areas/`; saves
store `loadedAreaSums()` (the same, with the length prefix stripped) as `areasums`.

### Why modules are "versioned apart"

`WorldAssets.VERSIONED_APART` (`modules/`, `compositions/`, `combat/`, `itemmods/`,
`affixes.xml`) are tracked so WORLD STATUS and WORLD DIFF show edits, but they are
not part of `worldsum`, because each family pins what a campaign uses itself
(manifest fingerprints, composition versions and so on). A world that never uses
them has the same sum it had before they existed, and editing them never reports a
save as made with another world version or forces a cache rebuild. Preserve this
when adding another family: put its folder in `VERSIONED_APART` and in
`WorldAssets.DIRECTORIES`.

### Tracked assets (WorldAssets)

`WorldAssets.DIRECTORIES` = `vocab, lore, text, resources, kits, encounters,
classes, modules, compositions, combat, itemmods`. `WorldAssets.FILES` = `score.txt,
ranks.ini, crops.txt, resources.txt, materials.txt, creation.ini, origins.ini,
perks.ini, combinations.txt, itemsets.txt, affixes.xml`. `paths()` walks the
directories (regular files, links not followed) and adds present files, sorted.
`reloadable(path)` is true for `vocab/`, `lore/`, `text/`, `kits/`, `encounters/`
and `score.txt, creation.ini, origins.ini, perks.ini, combinations.txt,
itemsets.txt`: those have readers that use live files or caches cleared by
`reload()`, which clears `Vocabulary`, `LanguageLexicon`, `CreationSteps`, `Perks`,
`Combinations`, `Encounters`, `Prop_ItemSet` definitions, help, socials and
`PropCheck` help keys, and then calls `WorldFiles.acceptAssetChanges()`.
`scriptsLoaded(files)` acknowledges only the script dependencies an explicit script
reload actually read.

Assets are **live external files**. A save records the accepted version in its
manifest; it never embeds a private copy. If you add a data file that a feature
reads live, add it to `FILES` or `DIRECTORIES` so WORLD STATUS sees it and a reload
can acknowledge it, and decide whether it is `reloadable`.

The `accept*Changes` methods (`acceptAssetChanges`, `acceptModuleChanges`,
`acceptCompositionChanges`, `acceptCombatChanges`, `acceptItemModifierChanges`) each
rebuild `knownSums` for their own paths only, so an explicit family reload
acknowledges exactly the files it validated and no others.

### Boot: rebuild or reuse

`SaveGame.beforeBoot` decides: `isCacheCurrent(cache)`; builder mode clears content
tables when the cache is stale (`clearContentTables`). `beginBoot(build,
cacheToRefresh)` stores `rebuild`, `bootSum`, `bootAreaSums`, `knownSums =
fileSums()`, clears the kept-outside list and `loadedWhole`.

`afterDatabaseConnect()` (right after the database connects, before the engine loads
classes) inserts custom races, classes, abilities, commands and behaviours into the
database when rebuilding, so they load with ordinary classes. `loadWorld()` then
runs `loadWorldFiles()`:

- not rebuilding: for a normal new game `importExtras(false)` (templates, clans,
  journals, VFS), `preloadClans()`, `DBReadAllRooms`, `MovingRoom.restoreAll`,
  `loadedWhole = true`, `loadProblems()`. A restored game does not import extras: its
  save already holds them.
- rebuilding: `importing = true` (so IMPORT's refusal of unknown-origin manuals is
  waived), extras, `preloadClans`, every `areas/*.cmare` through `importArea`, the
  catalog, `Uniques.registerWorld()`, quests, each wrapped by `imported(key, ...)`
  which captures what the engine logs as going wrong as that file's *problems*.
  `loadedWhole` becomes true only if no file failed. Then `writeSum(dbDir, bootSum)`
  and, for a normal game, the content tables and sum are copied back to the cache.

`importArea` reads the file through `readWorldXML` and calls
`fillAreaAndCustomVectorFromXML` then `unpackAreaFromXML(..., true, true)`.

### Problems, held-back files and the "never write over a broken file" rule

A world file that loads only in part (unknown class skipped and so on) is recorded in
`problems` (persisted to `worldproblems.txt`, `PROBLEMS_FILE`, in the database
folder because the cache was built from those files). `exportAll(force)` returns -1
(nothing written) unless in builder mode and `loadedWhole`. For files with problems,
`holdBack(key, force)` keeps the existing text and records the file in `heldBack`
unless `SAVE FORCE` is used. This is the invariant that makes the exporter safe:
**never rewrite a world file from a partially loaded world**.

### Export

`exportAll` sets `CMLib.coffeeMaker().setReadableText(true)` and calls
`exportAllReadable(force)`:

1. Collects savable, non-instance-child areas, sorted by name; dev areas go to
   `devFiles`.
2. For each area, `layOut(getAreaXML(...))`; compares with the existing file through
   `collapseText`; changed areas also go to `cacheArea(A)` (`DBUpdateArea`, rooms,
   exits, mobs, items) with readable text off, so the cache and files agree without a
   rebuild.
3. `writeFolder` for `areas/` and `dev/` (dev only if `devLoadedWhole`), the
   `custom/` tree, the catalog, quests, `templates.cmare`, `clans.cmare`,
   `space.cmare`, journals and `vfs`.
4. After a real run, `knownSums` gets the new sum for each path in `settled`.
5. If files were kept outside, delete `world.sum` and `dev.sum` so the next boot
   rebuilds; otherwise write the new sum.

`writeOrDelete` and `writeFolder` write only when the bytes differ and delete files
the world no longer produces. Both go through `mayChange(F, data)` (below) and
`backup(F)` (keeps the last `BACKUPS=` copies, default 5, in `.backup/` named
`path~with~tildes.yyyyMMdd-HHmmss-SSS`; `backupsOf`, `backedUp`, `restoreBackup`).

### Edits outside the game (B2) and ExportPlan

`mayChange(F, data)`: a file is *kept* when `!forcing && changedOutside(path)`, that
is `knownSums.get(path)` differs from `sumOf(file)` now (or the file appeared or
vanished). A kept file is not written or deleted; what the export would have written
goes beside it as `NAME.builder` (`BUILDER_EXT`) and the path is added to
`keptOutside`. A file the export leaves as the game has it is `settle`d: noted in
`settled` and any `.builder` copy removed. `changedOutside()` returns each path with
`edited`, `added` or `deleted` for WORLD STATUS.

`plan()` runs the same export in dry-run mode (`dryRun != null`): `mayChange` fills an
`ExportPlan` (`writes`, `deletes`, `kept`, `heldBack`, and `texts`: what each written
file would hold) and returns false so nothing is written or cached. WORLD STATUS and
WORLD DIFF use the plan, and so does `IsolatedPlaytest.prepare`, which applies
`plan.texts` onto a copy of the world folder so unsaved builder definitions reach
the playtest. `findPath(what, plan)` resolves a builder's name to a tracked path
(path with or without `.cmare`, file stem, or area name).

`loadAreaFile(path)` and `reloadCatalog()` implement `WORLD RELOAD AREA` and
`RELOAD CATALOG`: they import into the running world (the caller takes the old area
out first), drop the file's problem record, and `markLoaded(path)`.

### UndoLog (`.history`)

`UndoLog` records, in builder mode only, the last `KEEP = 50` building commands
(`CREATE, MODIFY, DESTROY, COPY, TRANSFER, RESTRING, EVERY, GENERATE, GMODIFY`),
each with the XML of every room it changed *as it was before*. `begin(mob, command,
words)` and `end(command)` are called around those commands (depth counted per
thread so nested commands are one entry); `snapshot(R)` captures a room's XML the
first time it changes inside an entry. `end` drops entries where no room's XML
ended up different. Entries are kept in memory and written to
`world/.history/<15-digit time>-<n>.undo` files (read at first use; a `.gitignore` of `*` is written with the folder): a text file of
`WHAT ...`, `WHEN millis`, then `ROOM <length> <roomID>` followed by that many
characters of XML. `undo(n)` recreates each room from its XML (`restoreRoom`) with
its contents waiting outside, and links the exits into it again. `HISTORY` lists
entries. The XML is taken by reflection from the coffee-maker's private
`getRoomXML` method so it reflects the live room rather than the database.

### Catalog import

Catalog entries are the `CATMOB` and `CATITEM` elements of `catalog.cmare`. An
entry carries `MOB`/`ITEM` data (class, text, level, ability, rejuvenation, uses)
plus `CATEGORY` and `CATADATA` (the catalog data: spawn kind, rate, cap, mask, and
uniqueness fields, see [Unique items](#unique-items)). `importCatalog(F, replace)`
creates the object, then `CMLib.catalog().addCatalog(category, P)`; with `replace`
(`WORLD RELOAD CATALOG`) an entry of an existing name updates the catalog's own copy
in place and `updateCatalog`s it. After adding, `data.build(CATADATA)` and
`updateCatalogCategory(own, data.category())` write the data onto the catalog's own
object (not onto the file's copy, which has no database row; doing so once left two
rows per entry). See [the cache](#the-content-database-as-a-cache) for the loader
side.

### World settings

`WorldSettings` holds a whitelist (`KEYS` plus key prefixes `PREFIXES`) of
`coffeemud.ini` keys a world may set in `world/world.ini`. `read(worldDir, ignored)`
returns the accepted settings in file order and lists ignored keys. `SUD` places them
between the fixed single-player settings and the command line; `sourceOf(key)`
reports whether a key's effective value came from `coffeemud.ini`, `world.ini` or
the command line (`FROM_INI`, `FROM_WORLD`, `FROM_COMMAND`). To let a world set a new
key, add it to `KEYS` (or a prefix); `SUDDocs/creator/world-settings.md` is generated
from that list, so run the generator rather than editing it by hand.

### Tests

`b2-handedits.js` (kept files, `.builder`, SAVE FORCE), `b5-reload.js`, `b6-undo.js`
(UNDO, `.history`, backups), `catalog-import.js` (one row per imported entry; the
loader prefers the row with data), `sc3-catalog.js` (catalog loading from scripts),
`s6-overlay.js` (resources overlay on a separate `WORLDDIR`), `ar-assets.js`,
`review-core.js` (every newly tracked asset, accepted-version provenance),
`w21-lawfile.js`, `dt27-dev.js` (dev areas).

### Pitfalls

- Add any new file kind to `contentPaths()` (and to `exportAllReadable` if the
  game generates it) or it will be neither checksummed nor protected from being
  overwritten.
- `world.ini` is tracked for display but is deliberately outside the checksum;
  editing it does not rebuild the cache. Its settings apply at the next process
  start.
- The exporter writes from the live world, not from the database. Anything a
  builder changes without a builder command (a script, `JRUN`) reaches the files
  only through `exportAll`, and the `cacheArea` step keeps the cache in step.
- `fileNameFor` lower-cases the name, keeps ASCII letters and digits, turns runs of anything else into one `-`, and adds `-2`, `-3` ... on a clash. Two area names that differ only in what it strips get numbered files, and the numbering follows the sorted export order, so renaming one area can rename another's file.
- Never read `knownSums` as "what is on disk"; it is "what this process last loaded
  or wrote".

## The content database as a cache

`DBSERVICE` names a fake JDBC database (`jdbc:fakedb:<folder>`, implemented under
`com/planet_ink/fakedb`). Saves and world files require it; for any other
`DBSERVICE` `SaveGame.beforeBoot` prints that they are off and calls
`WorldFiles.beginBoot(false, null)`. Its folder holds `fakedb.schema` and one
`fakedb.data.TABLE` file per table, plus `world.sum`, `dev.sum` and
`worldproblems.txt`.

Rules to keep in mind when touching code that uses it:

- Content tables (`WorldFiles.CONTENT_TABLES`) are built from the files. In a normal
  game they are written only in the working copy; the copy back to the cache happens
  once, after a rebuild, in `loadWorldFiles`.
- State tables (`SaveSlots.STATE_TABLES`) are what a save carries. Characters,
  accounts (`CMACCT`, the single local profile), player data (`CMPDAT`: bank
  accounts, the calendar, builder templates), journals, clans.
- A catalog entry is a row in `CMROIT` (items) or `CMROCH` (mobs) under the pseudo
  room id `CATALOG_ITEMS` / `CATALOG_MOBS` (`RoomLoader` tests
  `roomID.startsWith("CATALOG_")`). The catalog data (spawn rule, rate, cap, mask,
  uniqueness) is stored with the row. On load, `RoomLoader.DBReadContent`
  sorts rows with `RoomLoader.catalogOrder(itemNums, cataData)`: by name, and for a name
  with several rows the row **with** catalog data first; the first row of a name
  becomes the catalog's entry, later duplicates are deleted
  (`DBDeleteRoomItem`/mob). This guards caches built before the import fix that held
  two rows per entry, one with empty data.
- `RoomLoader` also supports `MOBNOCACHE` (mobs are not cached except catalog rows).
- Because a normal game writes only its working copy, do not call database writers
  from commands that should affect "the world" in a normal game; those changes are
  deliberately lost at game end (the building warning says so). In builder mode the
  same calls change the cache and `exportAll` carries them to the files.

Tests: `catalog-import.js`, `sc3-catalog.js`, `fixes.sh` smoke checks for
normal-play world-file preservation.

## Optional modules

### Purpose

Optional modules let a world declare gameplay features that are off by default, pin
their rule versions into each campaign, and keep their per-owner state in the save
with a defined lifecycle (migrate, suspend, refuse). Creator-facing rules are in
[chapter 14](14-optional-modules.md); this section is about how the framework is
built and how a feature family plugs in.

Source: [ModuleDefinitions.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/ModuleDefinitions.java)
(manifests), [OptionalModules.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/OptionalModules.java)
(campaign state, plans, capsules), `WORLD MODULES` in `Commands/WorldCmd.java`.

### Two classes, three concepts

- A **definition** (`ModuleDefinitions.Definition`) is a validated manifest read from
  `world/modules/<id>.json`: id, title, version, handler, `requires`, `conflicts`,
  `clock_modes`, `scopes`, typed `settings` and `state` fields, `on_disable`,
  `provides`, `references`, `migrations`.
- A **plan** (`ModuleDefinitions.Plan`) is the result of `ModuleDefinitions.load(world,
  selection, rawSettings)`: all definitions found, the selection (`roots`), the
  transitive `enabled` set, `reasons` (which root selected each module), per-module
  effective `settings`, and two lists of problems: `errors` (everything) and
  `blocking` (those that touch the selection, `MODULES` itself, `MODULESETTINGS`
  while anything is selected, or a selected dependency). `requireValid()` throws the
  blocking list. Anything non-blocking is a warning (`warnings()`).
- A **campaign** (`OptionalModules.Campaign`) is the live state of one game: a map
  of module id to `Capsule` (id, `requires_handler`, version, fingerprint, removal
  policy, settings, records) plus the `plan` it was prepared against. `OptionalModules`
  holds the `current` campaign in a static field; its static methods are
  `synchronized`.

### Definitions and fingerprints

A `Definition` constructor rejects unknown keys (`keys(obj, source, ...)`), duplicate
JSON keys and trailing data (via the strict parser `ModuleDefinitions.StrictJson`),
bad ids (lower-case letters, digits, `.`, `_`, `-`, starting with a letter, at most
96 characters, through `id(...)`), self-dependency and self-conflict, bad clock modes
or scopes, and field types other than `integer`, `boolean`, `string`. Limits:
`MAX_FILES = 256` manifests, `MAX_FILE_BYTES = 262144`, `MAX_RECORDS = 10000`, 128
settings and 128 state fields per module, strings at most 4096 characters.

`handler` is one of `data`, `example.counter`, `abilities.composition`,
`combat.profiles`, `items.modifiers`. `ModuleDefinitions.nativeHandler(h)` is true
for the last three; a native module's id must equal its handler. Each native handler
imposes extra shape rules in the `Definition` constructor (required settings and state
fields with fixed bounds, and required dependencies: `combat.profiles` requires
`abilities.composition` and `world` scope; `items.modifiers` requires
`combat.profiles` and `character` scope).

The **fingerprint** is SHA-256 (hex) of the canonical JSON of `contract()`: schema,
id, version, handler, `on_disable`, `requires`, sorted `conflicts`, `clock_modes`,
`scopes`, `provides`, each field's `contract()` (type, default, bounds, and for
strings `max_length` and sorted `choices`), and sorted `references`. Title and
`migrations` are excluded. Whitespace, key order, set-like array order and written-out
defaults therefore do not change it. When you add a manifest field that changes
behaviour, add it to `contract()`; when you add one that does not, leave it out.

A `Migration` has `from`, `to` (`to > from`), `state_renames` and `setting_renames`
(old name to new name; destinations must be unique). `Migration.apply` renames
simultaneously, so swaps work, and refuses to overwrite an existing destination.
`Definition.route(from)` returns the chain of steps to the current version or throws
`no migration from version n to v`; the constructor checks every start version has a
route.

### Campaign preparation: `prepare`

`OptionalModules.prepare(plan, saved, restoring)` builds a staged `Campaign`
without touching the running one. It is called from several places with the same logic:
the launcher's startup check (`SaveGame.moduleStartupProblem`), the preflight in
`SaveGame.restore`, `OptionalModules.boot` (from `beforeBoot`), and `reload(apply)` (WORLD MODULES PLAN/RELOAD, which prepares
against `snapshot()` of the running campaign).

- New game (`restoring == false`): one fresh `Capsule` per module in `plan.enabled`,
  with the plan's settings.
- Restore: the save's `modules.json` is parsed (`schema` must be 1, size at most twice
  `MAX_STATE_BYTES` = 8 MiB). For each saved module the removal policy and handler
  are read first, since without them there is no policy to apply. If parsing the
  capsule fails: policy `refuse` throws; policy `suspend` keeps the object as saved
  (`Capsule(saved, id, problem)`) and writes it back unchanged in later saves. If the
  module is not enabled or its definition is gone, or its version is equal but the
  fingerprint differs, or no migration route exists, or an owner's scope is no longer
  supported: the same refuse/suspend split applies. Otherwise every record and the
  settings are migrated into staging variables and only committed after all validated.
- A module with a dependency that is not active becomes suspended (or refuses),
  repeated until stable (transitive).
- Modules the world enables that the save lacks are **not** added; a creator note
  suggests `WORLD MODULES ADOPT`.
- `account()` enforces 8 MiB of serialized records and 10,000 records.

`Campaign.problem(id)` is the activation test: not adopted, disabled by the world, a
saved problem, a missing definition, or a clock mode (`TURNS` or `REALTIME`)
that the module or any module in its dependency closure does not support. Empty means
active. `OptionalModules.enabled(id)` is `current != null && current.problem(id).isEmpty()`.
Every consumer must test `enabled` at the point of use, not once at startup, because
the clock mode can change in play and a dependency can become suspended.

### Reading and writing state

Owners are created with `OptionalModules.world()`, `character(mob)`, `item(item)`,
`profile(mob)` (`@profile:<account name>`), `organization(clan)`
(`@organization:<clanID>`), `relationship(first, second)` (ordered pair), and
`owner(scope, persistentKey)` for adapters that already hold a stable key. Character
and item keys come from `InstanceIds.reference(...)` (see [Instance IDs](#instance-ids)),
never from a name. Keys are non-empty, at most 512 characters, with no control
characters.

`get(id, owner)` returns a copy of the stored values or the defaults (reads do not
allocate a record); `put(id, owner, values)` validates the complete record against the
module's `state` schema, then replaces it atomically, adjusting `stateBytes` and
`recordCount` and refusing when a budget would be exceeded. `setting(id, key)` returns
the effective typed setting. `configure(id, key, text)` is the typed creator-only
change to a campaign setting. Calls are `synchronized` individually; a read-modify-write
spanning calls belongs on the game thread or in the caller's own coordination.

### Persistence

`SaveGame.writeSnapshot` calls `OptionalModules.hasModules()` and, if true, writes
`OptionalModules.snapshot()` as `modules.json`, sets header `moduleformat=1` and
raises the header `version` to `SaveSlots.MODULE_FORMAT_VERSION` (2). The snapshot is
`{"schema":1,"modules":[ capsule... ]}`, each capsule
`{id, version, fingerprint, on_disable, settings, records:[{scope, owner, values}],
requires_handler?}`. `SaveSlots.validateFormat` and `OptionalModules.readSave`
refuse a version-2 save without `moduleformat=1` and `modules.json`. Because the file
is in the manifest of the atomic write, a damaged module file makes the whole save
incomplete rather than silently legacy.

`WorldAssets.VERSIONED_APART` keeps `modules/` out of the world sum.
`WorldFiles.acceptModuleChanges()` acknowledges only the flat `modules/*.json`
manifests after an explicit `WORLD MODULES RELOAD`.

### How a feature family plugs in

The three shipped families (composition, combat profiles, item modifiers;
[chapters 15, 16, 17](15-ability-composition.md)) follow one pattern. To add a fourth:

1. Add a native handler id to `ModuleDefinitions.nativeHandler` and the allowed
   handler list in the `Definition` constructor, with the shape checks that fix the
   settings and state fields your code reads (so a hand-written manifest cannot break
   your invariants) and any `requires`.
2. Create a library class (static, like `AbilityComposition`) with `MODULE = "<id>"`,
   `reset()`, `check()` (a validation of your world files returning a report of
   errors), `reload()` (which on success updates your cache and calls a new
   `WorldFiles.acceptXChanges()` that acknowledges just your folder), and a
   `require()` that throws when `!OptionalModules.enabled(MODULE)`.
3. Call `reset()` from `SaveGame.beforeBoot`.
4. Keep its hand-authored source folder in `WorldAssets.DIRECTORIES` and
   `VERSIONED_APART`.
5. Store campaign state through `OptionalModules.get/put` with the right owner, and
   keep any reference to saved things as `InstanceIds` references.
6. Add a `WorldCheck` part in `WorldCheck.PARTS` and `run` (the existing parts
   `MODULES`, `COMPOSITIONS`, `COMBAT`, `ITEMMODS` are the model), and a `WORLD`
   subcommand in `WorldCmd` if creators need to reload.
7. Guard every runtime entry point with `enabled(MODULE)` and behave harmlessly when
   suspended (see `CompositionBurn` and `CombatProfiles`, which stop a timed effect
   if the module goes inactive).

### Tests

`optional-modules.js` (schemas, dependencies, conflicts, references, state isolation,
Unicode, pinned settings, migrations, suspension and refusal, clocks, warnings,
byte-order marks, fingerprints, damaged capsules, the world sum without module
files), `ability-composition.js`, `combat-profiles.js`, `item-modifiers.js`; the
played driver `tools/modules-integration.py` (group `modules` of `tools/fixes.sh`),
and `SaveStorageTest` for the module-aware format.

### Pitfalls

- A saved capsule with an unknown native handler refuses the restore whatever its
  policy, so an old engine never drops native state silently.
- `MODULES` and `MODULESETTINGS` are read once per process through
  `OptionalModules.launchedWith` (the launcher's raw values), not from the engine's
  copy, which has Java properties escapes and HTML entities applied. Settings
  changes need a restart.
- Do not change a contract field without raising `version` (and adding a migration);
  the fingerprint check suspends or refuses saved campaigns.

## Instance IDs

### Purpose

`InstanceIds` gives each physical mob or item a durable identity that survives
moving, renaming and save/restore, and is distinct for copies. Anything that must
refer to "that sword" across a save (module records, JS stores, combat links, item
modifier ledgers, journey bindings, queued script actors) stores an instance reference,
not a name.

Source: [InstanceIds.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/InstanceIds.java).

### Design

An ID is a UUID stored as the `INSTANCEID` stat (`InstanceIds.STAT`) of an `ExtraData`
effect on the host, created on demand. A reference string is:

| Form | Meaning |
| --- | --- |
| `@instance:<uuid>` (`InstanceIds.PREFIX`) | a mob or item (any `Physical` other than room/area) |
| `@room:<extended room id>` | a room, by its extended ID |
| `@area:<name>` | an area, by name |
| anything else | `reference` returns the host's `Name()` (only for non-physical hosts) |

`reference(Environmental)` produces these; `isReference(value)` recognises them;
`find(reference)` resolves them: rooms and areas through `CMLib.map()`, instances
through a `ConcurrentHashMap<String, WeakReference<Physical>>` index with a fall-back
to a full world walk (`visit`) when the cached object is gone or no longer carries that
ID. `id(host)` is `synchronized(host)`: it creates the `ExtraData` if needed and, if
the existing ID is empty **or another live, non-destroyed object in the index already
carries the same ID** (which is what a copy of the object looks like, since copying
duplicates its effects), assigns a fresh UUID. That is how copies get independent
identities without copy hooks. Consequence: a copy shares the ID string until
somebody calls `id(copy)`; code that must tell the original from a copy (as
`ItemModifiers` does) checks `InstanceIds.find(ref) == item` as well as the string.

`restore(host, id)` re-attaches a saved ID after a restore (`WorldState` calls it for
items, mobs, doors via `IID`, and the player via `INSTANCE`). `existing(host)` returns
the ID without creating one. `legacy(host, name, type)` is the migration helper for
old name-only references: it rebinds only when exactly one candidate (in the host's
room, the host's inventory, the named player, or the host itself) matches and logs
`Ambiguous legacy ... was not rebound` otherwise. `visit(Consumer<Physical>)` walks
every room, exit, floor item, inhabitant and inventory item, and each player, once.
`prepareSave()` assigns IDs to every mob and item before a save writes anything, so
forward and backward references in one document both resolve.

### Invariants and threading

- IDs are assigned and read on the game thread; `id()` locks the host only.
- Never store an `@instance:` reference in content (world files): IDs are
  per-campaign. Authored identity is `AuthorIds`.
- Resolution can be O(world) when the index is stale; cache the `Physical` for the
  duration of one operation, not across ticks.
- Resolve late, after `WorldState.finishRestore`, not in `apply`: people and followers
  are not all back until then.

### Tests

`review-core.js` (copies get distinct IDs; legacy store migration), `js7-store.js`,
`item-modifiers.js`, `review-background.js`.

## Author identities, story records and story text

### AuthorIds: stable source identity

Some state is about an authored thing (a clue, a first entry, a waypoint) rather than
a physical instance: a copy of the room or a re-saved item must keep the discovered
flag. `AuthorIds` provides that identity.

Source: [AuthorIds.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/AuthorIds.java),
[StoryRecords.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/StoryRecords.java),
[StoryText.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/StoryText.java).

The private `identity(host, scope, params, create)` (reached through `key`, `keys`
and `recorded`) resolves in order:

1. `ID=` in the feature's parameter text (`Scenery.option(params, "ID", "")`).
2. A saved identity in the host's `Prop_AuthorIdentity` effect, whose text holds
   `SCOPE=AUTO:<uuid>` per scope.
3. For a room or area with none saved, a stable one: `AUTO:ROOM:<extended room id>` or
   `AUTO:AREA:<name>`. Nothing is written onto the room, so the room does not look
   "changed by play" and author fixes reach restored games.
4. If `create` is true (items and mobs), a new `AUTO:<uuid>` is appended to
   `Prop_AuthorIdentity`.

`key(prefix, host, scope, params, part, create)` returns
`StoryRecords.token(prefix, scope + ":" + id + ":" + part)`, which is
`prefix + upper-case SHA-256 hex` of the upper-cased key. `keys(...)` returns the
canonical key first, then a `legacy` key if given, then keys built from the parameter
`ALIASES` (for scope `FIRSTENTRY` also the bare alias, for `WAYPOINT` the older
`part\nalias` form) and any literal `LEGACYKEYS`. `recorded(record, ...)` returns true
if the record `Tattooable` carries any of those and **adds the canonical tattoo** if it
only had a legacy one, so keys migrate forward by use.

Use `ALIASES` for old authored ids a content revision replaced, and `LEGACYKEYS` for
complete old saved tokens when the key shape or location changed. Ambiguous legacy
physical-name references stay unbound by design. Authored identities are used by
`Prop_FirstEntry`, `Waypoints`, `Languages`, `Scenery` and `ViewRules` (see the
callers of `AuthorIds.` in the source).

### StoryRecords: per-character JSON

`StoryRecords.read(mob)` parses the character's `Prop_StoryRecords` effect text as a
`MiniJSON.JSONObject` (empty if absent or unparsable, with the error logged);
`write(mob, data)` creates the effect if needed and stores `data.toString()`. Helpers
`object(data, key)`, `string`, `number` and `token` keep callers null-safe. Because it
is an effect with text on the character, it saves and restores with the character
tables. `LoreBook`, `Chronicle`, `Ambience`,
`Languages`, `ColorAliases`, `PlayerMaps`, `ViewRules`, `Prop_FirstEntry` and the
`AUTOMAP` and `COLORSET` commands keep their per-character
"seen once" and knowledge records here.

### StoryText: conditional text

`StoryText.render(viewer, host, text)` expands the markup in room, area, item, mob, exit
and book text. Text with no `<` is returned as is (the fast path for most text). A
`<VARIES>` document is parsed into tags and conditional blocks; other text only has
`<CALL function args>` expanded (up to 32 calls per look, results cached per
viewer/host/command in a thread-local `Frame`, with a time budget derived from
`JSRuntime.limitSeconds()`; `StoryText.look(Supplier)` shares one frame across title,
description and details of one look). Recursion is limited to depth 32.
`test(viewer, host, area, room, name, args)` decides a condition tag: `MASK`/`MASKn`,
`FIRSTVISIT`, `INCOMBAT`, `DARK`/`LIT`, `INDOORS`/`OUTDOORS`, `HOLIDAY`,
`WANTED`, `FULLMOON`/`NEWMOON`/`WAXING`/`WANING`, `HOUR`, `MONTH`, `VAR`/`IFVAR`,
`PARTYTATTOO`, `QUESTWON`/`QUESTRUNNING`/`QUESTSTEP`, and the `Room.VariationCode`
names (weather, time of day, season, tides, disposition, visited, from-direction).
`known(name)` lists the same names for the validators. `ELSE` is true when the
previous sibling was false. `TextPreview` can override weather, time of day, season,
visited and direction so authors can preview text; `noteFirstVisit`/`firstVisit`/
`arriving`/`first` carry the "first visit" decision across the move message so
descriptions read consistently.

Invariants: rendering must not advance state (a `CALL` is computed once per frame and
should be a pure function); `StoryText` is read-only apart from `setVariable`.

Tests: `ar-shared.js`, `pr1-review.js`, `pr-presentation.js`, `pr-text.js`,
`pr-render.js`, `l-language.js`.

## Script events and the trigger plumbing

### Purpose

CoffeeMud scripts (MOBPROG) fire on messages. CoffeeSUD adds many triggers for things
that are not messages (a skill learned, a room discovered, a quest step, a calendar
change, a save, a party change). `ScriptEvents` is the bridge: engine code calls a
static method such as `ScriptEvents.game(...)`, which finds the hosts that could hear
the event and hands each script engine the event through
`ScriptingEngine.eventTrigger(...)`. The trigger and command reference for scripters
is in [chapter 26](26-mobprog-triggers.md); this section is about the plumbing.

Source: [ScriptEvents.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/ScriptEvents.java),
`Common/interfaces/ScriptingEngine.java` (`progs`, `eventTrigger`, `eventCheck`),
`Common/DefaultScriptingEngine.java` (`eventTrigger`, `registerGlobalTriggers`,
`getStateXML`, `setStateXML`), `Behaviors/Scriptable.java`, `core/WorldHost.java`,
`core/HostScripts.java`.

### Trigger codes and registration

A trigger's code is its position in `ScriptingEngine.progs` plus one
(`ScriptEvents.code(name)` returns 0 for an unknown name). The array is positional and
saved scripts do not store codes, but internal tables do, so **append new triggers at
the end** and never reorder. The same array is what `ScriptCheck` uses to recognise
trigger names (`PROGS = set(ScriptingEngine.progs)`), so adding a name there also
teaches the checker.

Two tables keep an event from costing anything when nobody listens:

- `used`: a `BitSet` of trigger codes that some parsed script has. A script engine
  that parses a script calls `ScriptEvents.uses(code)` for each of its triggers
  (`registerGlobalTriggers` in `DefaultScriptingEngine`). Every `ScriptEvents`
  entry point starts with `if(!isUsed(X_PROG)) return;`.
- `registry`: trigger code to a weak set of hosts that have a script with that
  trigger (`register(host, code)`, `registered(code)` which also skips destroyed
  hosts). This serves events that are heard anywhere in the world (GAME_PROG,
  QUEST_PROG, TURN_PROG and the like) without walking every room.

Events at a place (a room, an area) reach the same hosts a message there would:
`hosts(Room)` is the room, its items, its inhabitants and what they carry, then the
area and its parents (`getParentsRecurse`); `hosts(Area)` is every room's hosts plus
the area and its parents. `deliver(hosts, msg)` hands a `CMMsg` to each host's
scripts and to behaviours that are script engines, for events with no single room.

### Firing

`fire(hosts, triggerCode, source, target, msg, args, bits, test)` is the common
tail. For each host and each of its engines (`engines(host)`: the host's scripts, and
behaviours that are scripting engines) it calls
`S.eventTrigger(host, code, source, target, null, msg, args, bits, test)`. `bits` is
the format string `parseBits` uses to split the trigger line (for example `"CCr"`),
`args` become the script's `$0`, `$1` and so on, `msg` becomes `$g` or the event's own
text, and `test` is a predicate over the parsed trigger line that decides whether this
script's line fits (each trigger has a `...Matches(String[] t, ...)` method, such as
`gameMatches`, `turnMatches`, `areaMatches`).

In `DefaultScriptingEngine.eventTrigger`: it returns at once if the host or source is
null, if the recursion counter is above 2, or if the trigger is switched off
(`canTrigger`: an `MPNOTRIGGER` cool-down held in `noTrigger` as a game-time
deadline); it resolves the host's monster stand-in (`getMakeMOB`) and last known
location (an area hearing an event from no room uses one of its rooms); mobs that are
not free to be triggered (`isFreeToBeTriggered`) are skipped; then for each script of
that code whose line fits it queues a response (`enqueResponse` or a
`ScriptableResponse` with `ticks`) for the engine's normal dequeue, unless the
script's engine has `noDelay`. `multiTriggers` decides whether the first matching
script stops the search. Lines marked `@GLOBAL` (ST12) are the only ones heard by
hosts away from the event.

`eventCheck` is the synchronous variant for events that can be refused:
`ScriptEvents.gameCheck` (GAME_PROG SAVE) uses it so that a script's `RETURN CANCEL`
refuses a save.

Stand-ins: events that do not come from a character use `source(name, area)` or
`standIn(name)`, which return a stand-in mob ("the weather", "the sky", "the game")
placed in a room so `$n` has a referent.

### Where the events come from

| Method | Trigger or hook | Called from |
| --- | --- | --- |
| `game(kind, mob, detail)` | GAME_PROG (`NEWGAME, RESTORE, SAVE, NEWCHAR, SWITCHTO, SWITCHFROM, PAUSE, RESUME, TIMEMODE, ANY`), `JSEvents`, `WorldJS` on-functions | `SaveGame`, the `SWITCH`, `PAUSE` and `TIMEMODE` commands, `TimeLock` |
| `gameCheck(mob, slot)` | `onBeforeSave`, `cm.on('save')`, GAME_PROG SAVE (may refuse) | `SaveGame.writeSnapshot` |
| `turn(leader)` | TURN_PROG, turn count (saved in the header) | `Party.passTurn` and friends, and `StdMOB` for a command that took time in real time |
| `moved(mob, from, to)` | AREA_ENTER_PROG, AREA_LEAVE_PROG, area text | `MUDTracker` (every move), `Travel.transfer`, `SaveGame.playerLoggedIn` |
| `skill`, `levelGrant` | SKILL_PROG | ability learning code |
| `quest`, `questFail`, `questTimeout` | QUEST_PROG, QUESTFAIL_PROG, QUESTTIMEOUT_PROG | quest code |
| `weatherChanged`, `timeOfDayChanged`, `dateChanged`, `calendarChanged`, `onClock` | WEATHER_PROG, MOON_PROG, SEASON_PROG, MONTH_PROG, YEAR_PROG, WEEKDAY_PROG | the climate and clocks |
| `party`, `clan`, `faction` | PARTY_PROG, CLAN_PROG, FACTION_PROG | party/clan/faction code |
| `boarded`, `sail`, `vesselArrived`, `vesselDocked` | BOARD_PROG, DISEMBARK_PROG, SAIL_PROG, VESSEL_ARRIVE_PROG, VESSEL_GREET_PROG, DOCK_PROG | vessels |
| `verb`, `propVerb` | VERB_PROG | the command parser |
| `use`, `switched`, `itemSet`, `destroyed`, `affected` | USE_PROG, COMBINE_PROG, SWITCH_PROG, SET_PROG, DESTROY_PROG, DECAY_PROG, AFFECT_PROG | item and effect code |
| `wave`, `arenaWin`, `arenaLose`, `partyDefeat`, `ambush`, `phase`, `yielded`, `defeated`, `surrender`, `encounterReset` | WAVE_PROG, ARENAWIN_PROG, ARENALOSE_PROG, PARTYDEFEAT_PROG, AMBUSH_PROG, PHASE_PROG, YIELD_PROG, DEFEAT_PROG, SURRENDER_PROG, RESET_PROG | encounter code |
| `talk`, `talked`, `met` | TALK_PROG, FIRSTGREET_PROG, REGREET_PROG | NPC dialogue |
| `generated` | GENERATED_PROG | generators |
| `varChanged` | VAR_PROG | script variable code |
| `signal` | SIGNAL_PROG | `MPSIGNAL` |
| `achieved` | ACHIEVE_PROG | achievements |

(The list is representative; scan `ScriptEvents` for the rest. Each method's javadoc
names its trigger line format.)

### Saved script state

`ScriptingEngine.getStateXML()` and `setStateXML(xml)` carry the state of the engine's
bookkeeping across a save. The XML starts with `<HASH>` (a hash of the script list) and
may contain: `<ONCE>` (indices of ONCE_PROGs and QUEST_TIME_PROGs already run, except
`ONCE_PROG BOOT`, which is once per load), `TIME` and `DAY` (the hour or day each
TIME_PROG or DAY_PROG last ran), `DELAY` (ticks left), `<EVERY>` (EVERY_PROG next
times), `<NOTRIG>` (`MPNOTRIGGER` cool-downs as game time left), `<QUE>` (queued
MPALARMs and delayed responses: trigger code, tick delay, script index, source,
monster, target, items and message, plus `$0`-`$9` words for a paused `MPWAIT` body),
`<SCOPE>`, `<VARHOST>INSTANCE` and, for an `INDIVIDUAL` scope, `<VARS>`. `WorldState.statesXML`
wraps it as `<SSTATE KEY=...>` keyed by the hex hash code of the script text, so
changing a script drops its stale state. A freshly restored engine holds the XML in
`pendingState` and returns it unchanged from `getStateXML` until it has read it, so a
save made before the first tick does not lose it.

### Adding a trigger

1. Append the name to `ScriptingEngine.progs` with a numbered comment, and document it
   in the help (`resources/help/arc_behaviors.ini` lists the existing ones).
2. Add `public static final int X_PROG = code("X_PROG");` and a `fooMatches` predicate
   and a firing method to `ScriptEvents` following an existing one. Guard with
   `isUsed`. Build the host list with `hosts(Room)`, `hosts(Area)`,
   `registered(code)` or a combination.
3. Call it from the engine code where the event happens, on the game thread.
4. Make sure `ScriptCheck` accepts the trigger line (it reads `progs`); add arguments
   validation in `ScriptCheck` if the line has a fixed form.
5. If the trigger can refuse something, use `eventCheck`.
6. Test in a `tools/fixtures/fixes` file (see `st6-game.js`, `st5-quest.js`,
   `st2-skill.js`, `st10-party.js` for the model). The generated
   [trigger reference](26-mobprog-triggers.md) comes from the help files.

### World scripts and hosts

`WorldHost` makes one hidden item named `WORLD` that carries every
`world/scripts/*.script`, held by the player's room so it ticks wherever the player
goes; its variables are `WORLD`'s. It is created at the first login of each game
(`WorldHost.start(mob)`), before GAME_PROG `NEWGAME` or `RESTORE` fires, and
`WorldHost.forget()` drops the previous game's host in `SaveGame.afterBoot`.
`HostScripts` (P21) runs scripts that come with a thing rather than sit on it: a race's or class's `SCRIPT` runs as a hidden effect on each mob of it (an engine per mob, made when first needed, never saved), and a skill's or poison's `SCRIPT` is one engine per ID whose `ONFOUND`, `ONCRAFTED`, `ONAFFECT` and similar functions are called.
`ScriptHooks` holds P3's hook names: a generic ability's or trap's `SCRIPT` gets a
`FUNCTION_PROG` call of that name (`CANCAST` may `RETURN false`), and
`ScriptHooks.maskCheck(mask, mob, target)` lets a cast mask's `+IF`/`-IF` see `$t`.

### Threading and invariants

- Fire on the game thread. `ScriptEvents` keeps static tables (`used`, `registry`,
  `sources`) guarded by `synchronized` blocks, but the scripts themselves are not
  thread-safe.
- Never fire triggers while a restore is applying world state: `isWorldPending()`
  suppresses `AFFECT_PROG` and others for that reason. A trigger you add that is
  caused by objects being created or modified should check it.
- A recursion guard (`recurseCounter` above 2) silently drops nested events; do not
  fire a trigger from inside the handler of the same trigger and expect delivery.

### Tests

The `st*` fixtures (`st2-skill.js`, `st5-quest.js`, `st6-game.js`, `st7-verb.js`,
`st8-vessel.js`, `st9-clan.js`, `st10-party.js`, `st11-calendar.js`, `st14-faction.js`,
`st16-border.js`, `st20-generated.js`, `st22-social.js`, `st29-achieve.js`),
`dt7-script.js`, `p21-scripts.js`, `js6-cm.js`, `js8-world.js`, and the played
`st1`/`st6` groups.

## JavaScript integration

### Purpose and layers

CoffeeSUD embeds Rhino. The language reference is [chapter 5](05-javascript.md); the
Java side is a stack of small classes:

| Class | Role |
| --- | --- |
| `JSRuntime` | Installs a global Rhino `ContextFactory` once per process (it survives engine restarts). Every `Context.enter()` gets a time limit: `JSTIMELIMIT` seconds (default 2), `JSTIMELIMIT_JRUN` for `JRUN` (default 30). Errors are recorded with where they happened (`report`, `recentErrors`, `JS ERRORS`). |
| `JSScope` | Gives every JavaScript scope the same equipment: `require()` (sandboxed, over `world/js/lib` then `resources/js/lib`), the `cm` object (`JSCm.forScope`), `gen` (`JSGen`), `include(path)`, `debug`, a dice-backed `Math.random` (`DiceRandom`, so `RANDOMSEED` and saves cover it), and `sud`, the short names defined in the `SUD_JS` string. |
| `JSCm` | The `cm` API object. Its members return game objects so scripts need no engine package names. The host is the thing the script runs for; the player is the character at the terminal. |
| `JSStore` | `cm.store(key)`: plain JSON objects kept with the game. |
| `JSEvents` | `cm.on(event, fn, key)` listeners and `waitUntil`; `fire(event, args...)` called by the engine; listeners are code, not state. |
| `JSTimers`, `JSJobs` | `cm.every/at/after` timers on the game clock; bounded background jobs. |
| `WorldJS` | `world/js/init.js` and its `onBoot`, `onNewGame`, `onRestore`, `onBeforeSave`, `onPlayerEnter`, `onSwitch`, `onPartyChange`, `onBuilderStart` hooks. |
| `JSReload` | `JS RELOAD` and `JS WATCH`. |
| `JSHooks`, `JSVerbs`, `JSCommand`, `JSMask`, `JSQuests`, `JSProgression`, `JSConsole`, `JSBuilder`, `GenJs` | Smaller feature surfaces (hooks, verbs, commands, masks, quests, progression, console, builder, generator-defined JS). |

### JSScope

`JSScope.prepare(cx, scope, host)` is the single place a new scope is equipped. If you
add a global to every script, add it here, not in each runner. `compileFile(cx, path)`
and `fileText(path)` read `resources/` paths or `world:path`, compile once while the
file is unchanged, and refuse `..`. `libraryDirs()` is the `require()` search path.
`worldFiles()` and `checkWorldClasses()` (used by WORLD CHECK `JS`) list and compile
the world's JavaScript class folders (`CLASS_FOLDERS`: behaviors, abilities,
properties, items, and so on) and validate their `//extends` lines.
`JSScope.reload()` drops the compiled `require()` libraries and rediscovers the library folders.

### JSStore

A store is keyed by a string or by an object: `keyOf(holder)` gives `room:<id>` for a
room, `instance:<uuid>` for a mob or item (via `InstanceIds.id`), `area:<name>`,
`quest:<name>`, or the string itself (`world` if empty). Two maps hold them: `live`
(stores as JavaScript objects) and `saved` (the not yet touched JSON of the last
restore). Persistence:

- `toXML()` writes `<JSSTATE><STORE><KEY>..</KEY><JSON>..</JSON></STORE>...</JSSTATE>`
  into `world.xml`; `fromXML(tag)` clears everything (and `JSTimers.forget()`) and
  fills `saved`.
- `newGame()` (called from `ScriptEvents.game("NEWGAME", ...)`) starts a new game from
  `world/js/state.json`, a JSON object of `key: store`, if present.
- Only JSON-compatible data survives: a function or a Java object is lost at save.
- **Legacy migration.** Before stores were per instance, mobs and items were keyed by
  `mob:<room>/<name>` and `item:<where>/<name>`. `migrateLegacy(holder, key)` moves a
  legacy store to the instance key only if exactly one world object has that legacy
  key; if several do, it logs a warning once, keeps the legacy data under its old
  key and gives each instance a copy.

Restore order matters: `JSStore.fromXML` runs near the end of `WorldState.apply` and
before the `onRestore` hook, so `onRestore` sees its stores.

### JSReload

`JSReload.reload(which)` implements `JS RELOAD [file|ALL]`. A file counts as changed
when its modification time is later than game start or its last reload (`changed()`).
A file that does not compile is reported with its line and left as it was. By kind:

- `world/js/init.js`: `WorldJS.reload()` reads it again and calls its `onReload(old)`
  with the old scope if it defines one (`onBoot` is not run again).
- `world/js/lib`: scopes made from now on require the new library; existing scopes keep
  what they have required.
- A JavaScript class folder (behaviors, abilities, properties, ...): the class is
  compiled again. For behaviours and effects of that class in the world, a new
  instance is made and `migrate`d, then replaces the old one: text, invoker,
  proficiency, savable flag, and the **declared** state codes `STATE` and `ACTIVITY`,
  `TICKDOWN`, `CANUNINVOKE`, `NEXTCAST` (see `migrateAbility`); a behaviour copies
  `STATE`. If the new class cannot accept the saved state (its `getStat` returns empty
  after `setStat`), the replacement is **refused** and the live instance is kept, with
  a warning in the log. Mobs, items and rooms made from now on use the new class;
  those already in the world keep the old code.
- Other files (`Prop_JScript`/`JSBehavior` script files): `reloadHolders(script)` has
  each running holder read the file again and calls its `onReload(old)`.

Arbitrary private fields and closures cannot be inferred. A JS-defined class that wants
to be reloadable or savable must declare `STATE` (behaviours, effects) or `ACTIVITY`.
`JSReload.watch(mob, seconds)` starts `JS WATCH ON`, which polls every few seconds.

### Background jobs (JSJobs)

`cm.background(worker, data, timeoutMs, callback)` calls `JSJobs.start`. The worker
function is converted to source text and evaluated in a **fresh scope** on one of two
daemon threads (queue of 32); it receives JSON data and returns JSON; a class shutter
blocks `com.planet_ink.*` and `org.mozilla.*`. Timeout is 1 to 300000 ms; the deadline
timer finishes the job with an error and cancels the work. The callback runs on the game
thread through `executeRunnable` with `(error, result)` and only if the job's generation
(`JSJobs.reset()` bumps it, called from `beforeBoot`) and host are still current, so a
late result can never touch a restored world. Jobs are not saved; Java calls that ignore
interruption can hold a worker.

### Threading and invariants

- All `Context` use goes through `JSRuntime.enter`, so the time limit always applies.
  JavaScript classes (`//extends`) compile to bytecode, which instruction counting
  cannot see; they have no instruction limit.
- `cm.on` listeners and `every/at` registrations are recreated at boot: register them in
  `world/js/init.js` `onBoot` with a stable key. A listener with the same key replaces
  the earlier one; with no key, the function's own text is the key.
- Do not capture engine objects in stored state; store ids and re-resolve.

### Tests

`js2-runtime.js`, `js3-require.js`, `js5-world.js`, `js6-cm.js`, `js7-store.js`,
`js8-world.js`, `js9-props.js`, `js10-reload.js`, `js11-jscriptable.js`,
`js19-timers.js`, `review-core.js` (store identity), `review-reload.js` (compiled
replacement keeps declared state and refuses incompatible state),
`review-workflows.js` (isolated jobs, native call timeout completion).

## The party and session layer

### Purpose

CoffeeSUD has one human player and one process console. The player can have several
characters in the world at once (a party, or characters left on their own). The
engine still expects every player character to have a `Session`, so each character
that is not at the terminal gets a session with nothing behind it.

Source: [Party.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Party.java),
`Common/ConsoleSession.java`, `Common/PartySession.java`, `Common/WindowSession.java`,
[CharacterWindow.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/CharacterWindow.java),
[WindowPanel.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/WindowPanel.java),
[ConsoleInput.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/ConsoleInput.java),
[ProfileOwner.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/ProfileOwner.java).
Creator-facing commands (PARTY, AS, SWITCH, CONTROL) are in
[chapter 31](31-saves-and-party.md).

### Sessions

- `ConsoleSession` (`Common`) is the terminal: a `DefaultSession` whose connection is
  the process's standard input and output. No telnet negotiation is sent and telnet
  commands the session writes are filtered. When standard input ends, the connection
  reports itself closed, so the session logs out and saves the player.
- `ConsoleInput` is a singleton `InputStream` with one daemon pump thread reading
  `System.in` into a `LinkedBlockingDeque<Integer>`. It lives in `core`, which the
  system class loader loads once, while session classes are reloaded at each boot;
  that is how an in-process `RESTORE` keeps what the player typed ahead. `isFinished()`
  is true when standard input ended, the queue is empty and 1.5 seconds passed.
- `PartySession` extends `DefaultSession` with a `PartySocket` that reads nothing and
  discards output. Its `autoLogin(name, password)` loads the character with
  `CMLib.players().getLoadPlayer` and runs `CMLib.login().completePlayerLogin`; there
  is no password because these sessions exist only for the player's own characters.
- `WindowSession` extends `PartySession`: with `WINDOWS=1` and a display
  (`CharacterWindow.canOpen()`), its socket is a `CharacterWindow` (Swing) showing what
  the character sees, with a line to type commands into and an optional panel
  (`WindowPanel`: character state, map, party; `WindowPanel.setSections`). Closing the
  window calls `Party.rest(mob)` on the game thread. With no display it behaves as a
  `PartySession`.

`Party.isPartySession(S)` is true for both. Code that wants "the player's terminal"
must exclude party sessions: `Party.terminalOf(mob)` returns the non-party session of
the same profile. Several engine places already do (`DefaultScriptingEngine`
`MPECHOAT` routing, `JSCm.player`, `Narration`).

### Profile and ownership

All the player's characters share one `PlayerAccount` (the profile, `CMACCT`); the
login replaces CoffeeMud accounts. `Party.profileOf(mob)` returns
`mob.playerStats().getAccount()`. `othersInWorld(mob)` lists the profile's other
characters currently in the game. `ProfileOwner` implements the reserved owner
`@PROFILE`: any character of the profile owns what the profile owns (land, rooms,
private items, vessels), and a world file can ship a home owned by `@PROFILE` that is
never wiped as a nonexistent owner. `OptionalModules.profile(mob)` keys on the same
account.

### Party model and turns

A party is the group following one leader (`leaderOf(mob)` is
`mob.getGroupLeader()` or the mob). `leadsParty(leader)` is true if another of the
profile's characters in the world follows them. State is static in `Party`:
`turns` (`Turns.LEADER` or `Turns.ALL`), `order` (names), `onTheirOwn` (names),
`meetWhereLeft`, `fightEachOther`, `windows`, plus per-turn sets `waited` and `told`
and the member currently `acting`. All of the persistent ones are written to the save
header (`partymeet`, `partyfight`, `partyturns`, `partyorder`, `partyalone`) and
restored in `beforeBoot`; `fightEachOther` is applied in `afterBoot` after the boot
read `PLAYERKILL`. `setFightEachOther` rewrites `CMProps.Str.PKILL` to
`ALWAYS-100000` or `NEVER` and sets each player's `PLAYERKILL` attribute, because the
engine allows player versus player fights only when both have it.

Turn-based time with a party (`turnOf(mob)` answers what a command that takes time may
do):

| Result | Meaning |
| --- | --- |
| `Turn.LEAD` | Go ahead and move the world: the leader, or anyone not in a party. In `Turns.ALL` the leader is held (`WAIT`) until every member who is not `onTheirOwn` has a queued command or has `WAIT`ed; `toldWaiting` limits the message. |
| `Turn.ORDER` | The member currently `acting`: run without moving the world. |
| `Turn.WAIT` | A non-leader's command waits for the leader's turn. |

`beforeLeaderActs(leader)` runs members' queued orders before the leader acts (all of
them in `LEADER`; those ahead of the leader in the party's order in `ALL`);
`afterLeaderActs(leader)` runs those after the leader in `ALL`, ends the turn
(`endTurn` clears `waited`, `told`, `toldWaiting`) and fires `ScriptEvents.turn(leader)`
(TURN_PROG and the turn count). `passTurn(leader)` is `WAIT`. `carryOut(M)` sets
`acting = M` and runs `M.dequeCommand()` through `actWithTerminal`. `turnMillis(leader,
ms)` returns how far the world advances after the leader's action: at least one tick
in `ALL`.

`actWithTerminal(M, r)` is the trick that lets a background character's action show
on the terminal: it swaps sessions between `M` and the character being played
(`M.setSession(T); T.setMob(M); played.setSession(mine); mine.setMob(played)`) inside
`synchronized(T)`, runs `r`, then swaps back in a `finally`. A windowed character just
runs `r` (its own window shows it).

Membership operations: `bringIn(mob, name, room[, restoring])` makes a `PartySession`
or `WindowSession`, sets its account and `autoLogin`s the resting character (checks
`WorldRules.activeRefusal(acct)` for the active-character limit unless `restoring`);
`rest(M)` hands the lead on if needed, fires `ScriptEvents.party("REST", ...)`, saves
the character and stops the session; `release(M)` detaches a background character's
session so the terminal can play it (SWITCH); `handOver(leader, to)` makes `to` the
leader and fires `party("LEAD", ...)`. `setWindows(on)` moves every existing
background character to a new session type with `moveToNewSession`.

### Persistence and restore

`playerXML` writes each character with location, leader, hit points, effects, items and
followers; `WorldState.finishRestore` brings back the played character, then calls
`Party.bringIn(..., restoring = true)` for each member in `PARTY`, restores their
state and sets leaders. Header fields restore the party settings. A character in the
world but not played is saved with the others; characters resting in the profile are in
the character tables only.

### Invariants and pitfalls

- A background character's session never reads input; commands reach it only through
  `AS`, orders carried out by `carryOut`, or scripts.
- Session classes are reloaded each boot but `Party`'s statics are not (it is in
  `core`); `SaveGame.beforeBoot` resets what must be reset through the header. Do not
  hold a `Session` in a static.
- When touching code that sends text to "the player", send to the terminal's mob, not
  to a party member, or the text is discarded by `PartySocket`.
- Swapping sessions in `actWithTerminal` is not re-entrant for the same pair; do not
  call it from inside itself for another member.

### Tests

`party-controls.js`, `sc8-party.js`, `st10-party.js`, `f-party-clan.js`, `p9-recruit.js`,
`dt16-testchar.js`, `review-background.js` (background-party prompts go to the shared
terminal; restored choices queue; narration without a visible window releases its
hold).

## Game time, time modes and pause

### GameTime

`GameTime` is the world's clock. Game logic reads `GameTime.now()` instead of
`System.currentTimeMillis()`; logs, input timeouts and real dates stay on the system
clock. It holds one immutable `State(gameAt, realAt, rate)` in a `volatile` field,
replaced whole so a reader never sees half a change. While running,
`now = gameAt + (sysNow - realAt) * rate`; while stopped (`realAt == 0`) it is
`gameAt`.

| Method | Effect |
| --- | --- |
| `now()`, `rate()`, `rateText()` | Read. |
| `setRate(r)` | `TIMESCALE`: game milliseconds per real one, clamped to `MIN_RATE` 0.1 .. `MAX_RATE` 20; the scheduler is told through `setRateListener` because its sleeps were computed at the old rate. |
| `isPaused()`, `isTurnBased()`, `isStopped()` | Pause, turn-based flag, or either (clock stopped). |
| `pause()`, `resume()` | `PAUSE`: `pause` does nothing when turn-based (already stopped). |
| `setTurnBased(on)` | Switch mode; clears `paused`; stops or starts the clock. |
| `advance(ms)` | Move the clock at once; runs nothing on the way. |
| `requestAdvance(ms[, until[, after]])` | Ask the scheduler to move a **stopped** clock forward step by step (no effect while it runs); `until` is a stop test checked after each step; `after` runs when the time has been taken (W4). |
| `takeAdvance(maxStep)` | Scheduler only: take up to `maxStep` of the pending time, advance, and run `after` when it is all taken. |
| `isAdvancing()`, `cancelAdvance()`, `checkStop()` | Query, cancel, and apply the stop test. |
| `set(ms)` | Used only by a restore. |

Why `requestAdvance` instead of `advance`: if you jump the clock, nothing due on the
way (weather, hunger, scripts, cooldowns) happens. The scheduler
(`ServiceEngine`) takes the pending time a step at a time, up to the next thing due,
so everything happens in order. This is also why `Travel.elapse` uses it.

### TimeMode and PAUSE

`TIMEMODE TURNS|REALTIME` (`Commands/TimeMode.java`) calls `GameTime.setTurnBased` and
fires GAME_PROG `TIMEMODE`. `PAUSE`/`RESUME` (`Commands/Pause.java`) call
`pause()`/`resume()` and fire GAME_PROG `PAUSE`/`RESUME`. `WAIT [n]` (`Commands/Wait`)
asks for `n` ticks with an early stop if the player is hurt, attacked or moved. In
turn-based play a command that takes time runs and then the world moves on by as long
as it took (`StdMOB` consults `Party.turnOf` and requests the advance); a command that
must wait for its actions advances the world to the moment it can go. The prompt shows
`(paused)`.

The mode and pause are saved in the header (`timemode`, `paused`, `gametime`) and set
in `beforeBoot` before the world loads. Anything that stores an **absolute game time**
(cooldowns, contract end times, the unique-item `lost` list, `DUESNEXT`-style
deadlines) is consistent because `GameTime.set` restores the same epoch; anything that
stores real time would not be.

`Narration` and `Waits` pause the clock for paged text and long waits
(`GameTime.pause()` when single-player and not turn-based) and release it afterwards;
[the persistence matrix](state-and-persistence.md) lists unread pages and temporary pause
ownership as saved, so a restore does not leave the game stuck paused.

### TimeLock (zones)

`Prop_TimeMode` on a room or area holds the time mode while the played character is in
it (`MODE=TURNS|REAL...`, `ONLYCOMBAT`, `RESTORE`, `MSG`). `TimeLock.check(mob)` runs
after moves and fight changes for a played character: entering a zone saves
`previous` (the mode before) and `hold`s the zone's mode (firing GAME_PROG `TIMEMODE`);
leaving, or ending combat when `ONLYCOMBAT`, `release`s and restores `previous` unless
`RESTORE=false`. `TimeLock.heldBy()` lets `TIMEMODE` refuse while held. The previous
mode is saved as header `timemodebefore` and restored with `TimeLock.restore`.

### Pitfalls

- Never use `System.currentTimeMillis()` for a duration that should freeze when the
  game is paused or turn-based.
- `requestAdvance` is ignored while the clock runs; check `isStopped()` first or use
  `Travel.elapse`, which pauses, requests the advance and resumes in the `after`
  callback.
- `GameTime` statics survive an in-process restore (the class is in `core`); the
  restore sets the clock, mode and pause from the header. The rate is not saved: `MUD.java`
  applies the `TIMESCALE` setting (if numeric and above 0) at each boot, and the
  `TIMESCALE` command (`Commands/TimeScaleCmd`, builders and DEV=1) changes it for the
  running process only; a boot with no `TIMESCALE` setting does not reset a rate set earlier
  in the same process.

### Tests

`dt23-timescale.js`, `w28-timemode.js`, `w4-wait.js`, `sc2-time.js`, `st11-calendar.js`,
`q11-deadline.js`, `p28-xptime.js`.

## Travel helpers

### Purpose

`Travel` is the shared rule set for authored transport: journeys, transport lines,
moving rooms, vessels, stables, tickets and travel time. Behaviours and properties
implement small interfaces declared in `Travel`; the class itself holds routing,
fares, temporary links and the elapsed-time bridge to `GameTime`. Creator-facing
behaviour is in [chapter 30](30-travel-vehicles-instances.md).

Source: [Travel.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Travel.java),
[RouteStops.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/RouteStops.java),
[Waypoints.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Waypoints.java),
`Abilities/Properties/Prop_JourneyState.java`, `Prop_Journey.java`,
`Behaviors/TransportLine.java`, `MovingRoom.java`, `Abilities/Misc/InstanceArea.java`.

### Interfaces and their implementers

| Interface | Methods | Implemented by |
| --- | --- | --- |
| `Travel.Stateful` | `travelState()`, `restoreTravelState(xml)` | `InstanceArea`; any effect that implements it is saved as `<ASTATE>` by `WorldState.statesXML` |
| `Travel.InPlaceScaling` (extends `Stateful`) | `isInPlace()`, `rebuildInPlace(room, leader)` | `InstanceArea` |
| `Travel.Helm` | `allowed(mob, ship)` | `Prop_HelmRestrict` |
| `Travel.CommittedEntry` | `entering(host, who, from, to)` | `ResetWhole`, `InstanceArea` |
| `Travel.BoardingReceipt` | `disembarked(who)` | `Prop_TicketTaker` |
| `Travel.PassageSource` | `begin(who)` | `Prop_Journey` |
| `Travel.Passage` | `depart`, `status`, `resumeJourney`, `cancelJourney` | `Prop_JourneyState` (and `Commands/Journey`) |
| `Travel.StableService` | `stable(attendant, customer, request)` | `Nanny` |
| `Travel.PacedVehicle` | `travelReady()` | `StdNavigableBoardable` |
| `Travel.Timetable`, `Line` | `timetable()`; `arrivals()`, `isMoving()` | `TransportLine` (`Waits` only reads `Line`) |
| `Travel.MovingRoute` | `refreshOverlay`, `clearOverlay`, `isAttached` | `MovingRoom` |

`Travel.entering(who, from, to)` is the "entry work runs after veto checks and payment,
before rooms receive ENTER" hook: it calls `CommittedEntry.entering` on the behaviours
and effects of the destination room and its area. `Travel.disembarked(who, vessel)`
calls `BoardingReceipt` on hosts aboard.

### Key operations

- `transfer(leader, passengers, to, look[, commit[, force]])`: validates **every**
  passenger first (access, `okMessage` of the mob, the destination and the room being
  left), returns false without moving anyone if one refuses, calls the `commit`
  supplier (a fare), then moves them together, sending leave and enter messages and
  `ScriptEvents.moved`. `force = true` skips the checks (a teleport).
- `fare(...)` / `fares(...)`: one fare convention: price per head or flat, per-player
  or leader pays, pet and cargo supplements; `fare` checks affordability of everyone
  before charging anyone (`collect` false only checks).
- `elapse(ms)`: advance the clock through the scheduler without skipping weather,
  hunger or scripts. Single-player only; pauses a running clock, requests the advance
  and resumes afterwards. `moved(traveler, from, to, exit)` calls it with
  `stepMillis(...)` when a player is involved (`hasPlayer`), called from `MUDTracker`
  and `StdNavigableBoardable`.
- `stepMillis`: travel time from `Prop_TravelTime` on the exit, destination room,
  destination area, source room or source area (first found), scaled by `VEHICLE`
  (default 0.7), `MOUNTED` (0.5), `NIGHT` (1.5) options and a road's `TIME`; minutes
  converted with `CMProps.getMillisPerMudHour()`.
- `route(from, to, vehicle)`: a breadth-first path of directions (fewest steps) over open exits that suit the vehicle's terrain, searching at most 10,000 rooms; null when unreachable; `sail`, `hitch`
  (refuses a riding loop), `destination(name, who)`, `knownPort`, `vessel(name)`,
  `label`, `value(function, object)` (script functions).
- `temporaryLink(owner, from, dir, to, exit)`, `temporaryDoor`, `temporaryExit`,
  `unlink(owner)`, `clearLinks()`: owner-keyed overlay links that do not exist in the
  world files (a moving room's current door). The overlay is in weak maps and is
  cleared by `clearRoutes()` (`MovingRoom.clearAll()` at load) and re-established by
  `restoreRoutes()` (`MovingRoom.restoreAll()`), never exported.
- `newInteriorName(base)` makes a unique instance area name `base_vN`.
- `beginJourney(who, source, definition)` gets a `Prop_JourneyState` ability and calls
  `depart`.

### Persistence

State that must survive a save is carried by `Travel.Stateful` effects (`<ASTATE>`),
vessels' `getTravelStateXML()` (`<VESSELS>`), and the host behaviours' `STATE`
(`MovingRoom`'s position). The restore order places vessels before characters
(`putBackVessels` runs in `finishRestore` after the characters' own vessels are back)
and calls `MovingRoom.restoreAll()` after effects.

### Pitfalls

- Never move several passengers one by one with `bringMobHere`; use `transfer` so that
  a refusal moves none.
- Temporary links are keyed by owner identity; unlink in the owner's `finalize`/stop
  path or they leak until `clearRoutes`.
- `elapse` does nothing when the game is not single-player or `ms <= 0`.

### Tests

`t-travel-core.js`, `t-journeys.js`, `t-transport.js`, `t-waypoints-stables.js`,
`t-instances.js` (return resets, in-place scaling state, MovingRoom overlays never
export), `ar-journeys.js`, and the played `travel` group.

## Unique items

### Purpose

Unique and rare catalog items (DEVPLAN item 1): a catalog item has a **cap** (most copies
in the world) and one of three kinds of uniqueness: `TIME` (at most the cap at once),
`ONCE` (once the cap's number have appeared, no more) and `WHILE` (as once, but a lost
copy may appear again after the item's number of game days). It may have several spawn
rules, each `KIND RATE [WHEN] [MASK]`, sharing the cap. Kinds are `LIVE`, `DROP`,
`ROOM`, `CONTAINER`; a container rule rolls on first `OPEN`, on the catalog's `TIMER`
into a random matching container, or `BOTH`.

Source: [Uniques.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Uniques.java).

### Design

- `Rule` (spawn kind, rate, `when`, mask text and compiled mask) is parsed by
  `Uniques.parse(line, why)`; `rules(CataData)` returns the catalog's own rule plus the
  extra ones, caching compiled rules by text. `Rule.text()` re-writes a rule as stored.
- `Track` per item (upper-case name): `ever` (copies that have appeared), `lastLive`,
  and `lost` (game times, oldest first). `HOWS = {TIME, ONCE, WHILE}`;
  `WHENS = {OPEN, TIMER, BOTH}`.
- `live(data, exclude)` counts copies in the game (in a room, or carried by a mob that
  is) plus copies made but not yet placed (`pending`, a weak map filled by `appeared`
  for copies not yet in the game).
- `mayAppear(name, data, exclude)`: false if no cap or `live >= cap`; `TIME` true; else
  `ever < cap` true; `ONCE` false; `WHILE` true if the oldest `lost` entry is at least
  `uniqueDays * dayMillis()` old. `appeared(name, data, P)` increments `ever` or
  consumes the oldest `lost`.
- Hook points in the engine: `CMCatalog` (LIVE/DROP rolls via `dropRoll`; `ROOM` and
  `CONTAINER` timers via `timer` and `observeAll` in the catalog's maintenance pass), `StdContainer.opened`
  (`Uniques.opened`), `ItemRejuv` (`mayReturn`, `returned`), `StdMOB.admitCarried`
  (a carried copy counts), `MUDPercolator` and `ScriptLib` (generators and
  `MPOLOAD CATALOG` respect the cap), `WorldFiles.loadWorldFiles` (`registerWorld`
  counts the copies placed in areas, **before** the catalog is read).

### Persistence

`toXML()` calls `observeAll()` then writes
`<UNIQUES><U NAME EVER LIVE LOST="t1,t2"/>...</UNIQUES>` into `world.xml`;
`fromXML(tag)` clears `tracks` and refills them. `UNIQUES` is **last** in the restore
because items coming back from the save have already been counted by `admit`; a missing
tag (older save) starts again from the copies in the game. Absolute game times in `lost`
rely on `GameTime.set`.

### Tests

`item1-uniques.js`, `g6-loot.js`, `catalog-import.js`.

## Organisations and guilds

### Purpose

`Organizations` and `Guilds` hold shared rules for clans, governments and NPC-run
organisations so that commands, scripts and world content use one implementation.
The creator view is in [chapter 9](09-society.md).

Source: [Organizations.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Organizations.java),
[Guilds.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Guilds.java),
[OrgCheck.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/OrgCheck.java),
`Common/DefaultClan.java` (calls `Organizations.tick`).

### Organizations

- Clock: `now()` is `GameTime.now()` unless `CLANCLOCK` is not `GAME` (then the system
  clock). `tick(C)` is called from `DefaultClan`'s tick; if `CLANCLOCK` is not `REAL` it
  runs `dues(C)`, and once per game day it runs `changed(M)` for each member
  and each NPC member, then `C.update()`.
- Rules from data: `setting(C, key, fallback)` reads the clan's own stat first, then its
  government's (`NPCFLAGS`, `DUESPERIOD`, `DUESGRACE`, `DUESLATE`, `DUESAUTO`,
  `DUESNEXT`); `npcRun(C)` is the `NPCRUN` clan flag or `NPCFLAGS` containing it;
  `npcMembers(C)` scans world mobs with a role in the clan.
- Ranks: `rankTest`, `rankMask`, `memberValue(mob, clan, field)` (`POSITION`,
  `RANKNAME`, `JOINDATE`, `DUESOWED`, `DONATEDGOLD`, `DONATEDXP`), `roleID`,
  `rankDetails`, `merit(M, C)` (merit promotion/demotion: picks the best-ranked position
  whose inner mask the member meets, whose seats are not full (NPC seats counted by
  identity, not name), and, for a demotion, whose dues are not late), `changed(M)`
  (re-evaluates a member's clans, forces `AutoAwards`, recovers stats; guarded by a
  thread-local set against re-entry).
- Dues: `dues(C)` advances the saved `DUESNEXT` deadline **before** collecting (so a
  restore cannot charge twice), adds `periods * dues` to each member's owed amount,
  optionally auto-pays (`payDues`, `payFromBanks`), and applies the late step
  (`WARN`, `DEMOTE`, `EXILE` from `DUESLATE`) while firing CLAN_PROG `DUESLATE`.
  `periodHours(C)` converts `DUESPERIOD` (`DAY, WEEK, MONTH, YEAR`) to hours of the
  global clock.
- Factions: every NPC-run clan gets a reaction faction `CLAN_<CODE>` from
  `clanreaction_<code>.ini` or `examples/clanreaction.ini` (`clanFaction`, persisted or
  `IFLAG_NEVERSAVE`); `refreshRelations` maps clan relations ally/war to faction
  relations (+0.5 / -0.5).
- Single-player voting: `soleProfileVoter` (a profile that holds all votes); party
  helpers `partyClans`, `partyFactions`, `voterKey`, `memberActive`.

### Guilds

`Guilds.create(name, template, hallID)` is the validated scaffold used by `CREATE
GUILD`: checks name, free exit in the hall, template government (`government(name)`
searches stock governments), then creates an NPC-run `DefaultClan` (`C.create()`),
its reaction faction, a guildmaster (`GuildMaster` behaviour with `CLAN=` `RANKS=MERIT`
`DUES=TRUE`), a trainer (`MOBTeacher`), a quartermaster shopkeeper with a clan
price rule, and a members' room gated by `Prop_ReqEntry` with `-CLANRANK`, linked to
the hall and written to the cache (`DBCreateRoom`, `DBUpdateExits`, `DBUpdateMOBs`). It
returns a reminder to save the world. `foundParty(founder, name, template)` founds a
player clan from a party (single player only): every member must share the profile
and be eligible; the cost (`CLANCOST`) is paid once by the founder.

### Persistence

Clans are in the state tables `CMCLAN` and `CMCLIT` and carried by saves; clans without
player members are also world content (`clans.cmare`). The faction definitions for clans
are files under the factions folder. Law and conquest state is saved in the area
STATES (`s5-law.js`).

### Tests

`f-organizations.js`, `f-guilds.js`, `f-party-clan.js`, `ar-organizations.js`
(rank cap counts same-name NPCs), `dt37-orgcheck.js`, `s5-law.js`.

## The authoring checks

### Purpose

Static checks read the world and report problems by file and line without running
game logic. Creators run them through commands; CI runs them through `sud.sh CHECK=1`
(world loaded as a new game on a scratch copy, `WorldCheck.run(null, null)` printed,
exit code 1 if any error). The checks are read-only: they never assign IDs, run
scripts or change the world.

| Class | Command | Reads |
| --- | --- | --- |
| `WorldCheck` | `WORLD CHECK [ONLY=parts]`, `CHECK=1` | The whole world, by part |
| `ScriptCheck` | `SCRIPTCHECK` | MOBPROG scripts with `LOAD=` followed, never run |
| `PropCheck` | `PROPCHECK` | Each effect's text on rooms, exits, mobs and items, and the catalog |
| `AuthoringCheck` + `AuthoringSchema` | via `WorldCheck` parts `VOCAB, LORE, TEMPLATES, ROUTES, IDS` | Text lists, dictionaries, lore, templates, routes, authored IDs |
| `QuestCheck` | `QUESTCHECK` | Quest scripts |
| `PuzzleCheck` | `PUZZLE CHECK` | Rooms, doors and objects scripts name |
| `OrgCheck` | `ORGCHECK` | Faction files, clan references, government entry scripts |
| `TextCheck` | `CHECKTEXT` (`PREVIEW`) | Room and area VARIES text |
| `GenerateCheck` | `GENERATE CHECK` | Generator XML files |

### WorldCheck

`WorldCheck.PARTS` = `EXITS, REACH, KEYS, TEXT, SHOPS, DATA, SCRIPTS, PROPS, JS,
VOCAB, LORE, TEMPLATES, ROUTES, IDS, MODULES, COMPOSITIONS, COMBAT, ITEMMODS`.
`run(Area only, Set<String> parts)` returns `Finding`s (`Severity` ERROR or WARNING;
`part`; `what`) sorted errors first. A `null` or empty part set runs everything.
`DATA` (what the world files could not load) and `JS` (`JSScope.checkWorldClasses`)
run only for the whole world (`only == null`). `SCRIPTS` maps
`ScriptCheck.isWarning(p)` lines to warnings and the rest to errors; `PROPS` findings
are always warnings. The four module-framework parts (`MODULES`, `COMPOSITIONS`,
`COMBAT`, `ITEMMODS`) are errors, except `ITEMMODS`, which is a warning unless
`ItemModifiers.inUse()` (a world that never opted in keeps its legacy `affixes.xml`).
`MODULES` reports `plan.blocking` as errors and `plan.warnings()` as warnings and
checks each enabled module's `clock_modes` against the current clock.
`report(found)` formats `WORLD CHECK: n errors, m warnings.` and the lines;
`errors(found)` counts; `setHeadless`/`runHeadless`/`exitCode` implement `CHECK=1`.

Add a part by adding its name to `PARTS`, a block in `run`, and a test in
`dt13-worldcheck.js` or a family fixture. Parts are cheap to run alone with `ONLY=`.

### ScriptCheck

`checkText(name, text, files)`: expands `LOAD=` includes (recording the file and line
of every statement in `Src` records), splits into units the way the engine's parser
does (`;` between commands, `~` between triggers, `#` comments), collects assignments,
then `checkUnit` per unit, then verifies every `MPCALLFUNC` target has a
`FUNCTION_PROG`. It checks triggers against `ScriptingEngine.progs`, commands against
`ScriptingEngine.methods` (exact, no prefix matching), functions against `funcs`,
IF/ELSE/ENDIF, SWITCH/CASE and FOR/NEXT balance, `MPWHILE` parentheses, unterminated
`<SCRIPT>`, JavaScript that does not compile, unapproved blocks where approval is
needed, missing `LOAD=` files and room IDs in `MPGOTO`, `MPTRANSFER` and `MPOLOADROOM`.
Findings read `file:line what`; things the checker cannot be sure of (counts and
variables a script may get another way) are `file:line warning: what`. `checkHost`,
`checkArea` (area, rooms, exits, mobs, what mobs carry, room items) and `checkAll`
accumulate with a shared file cache so a file many hosts load is reported once.
`ScriptSignatures` holds argument counts for commands (`add("MPECHO", 1, true)`); add
a new command's signature there.

### PropCheck and AuthoringSchema

`PropCheck.checkEffect(host, A)` reports: `AuthoringSchema.check(ID, text)` problems
(typed options), what the class logs when it reads its own text (captured with
`Log.startCapture`/`stopCapture` by instantiating a fresh effect and calling
`setMiscText`), `KEY=` words that the effect's `AHELP` entry does not name
(`keysOf(ID)` parses `[A-Za-z][A-Za-z0-9_]*\s*=` out of the help text, cached per ID and
cleared by `WorldAssets.reload()`), room IDs of the form `Area#12` that do not resolve,
unknown abilities in a `Prop_` spell list, and effects that `canAffect` says cannot
affect their host. Only `isSavable()` and not-`canBeUninvoked()` effects are checked
(what was set on a thing, not what was cast on it).

`AuthoringSchema` registers typed option metadata for the newer authored properties
(`schema("Prop_Waypoint", "NETWORK:text", "DISCOVER:enum:ENTER,TOUCH,RITUAL,QUEST",
...)`) with types `text`, `int:min:max`, `number:min:max`, `boolean`, `enum:...`,
`room`, `file:prefix`, `list`, `language`, `languages`, `lore`; `identities(...)` adds
`ID`, `ALIASES`, `LEGACYKEYS`. `keys(id)` feeds both `PropCheck` (so a new property's
keys need not be in help to be accepted) and `AuthoringCheck`. **When you add a
property with options, add a schema.** `AuthoringCheck.checkList(key, value)` validates
`HEALTH_CHART`/`HEALTHCHART` (11 entries), `WEAPON_HIT_DESCS`/`WEAPON_MISS_DESCS` (6) and
`DAMAGE_WORDS` rows. `AuthoringCheck.run(part, only)` also detects duplicate `ID`s
(`IDS`: error for a duplicate `Prop_Waypoint` ID within a network, warning otherwise),
ambiguous waypoint names and `TransportLine`/`MovingRoute` stops that do not resolve.

### Findings format and exit codes

Plain strings: `where / host / ID: problem`, or `file:line problem`. Findings from
commands are told to the player; from `CHECK=1` they print on standard output. Exit
code 1 only for errors.

### Tests

`dt11-scriptcheck.js`, `dt12-propcheck.js`, `dt13-worldcheck.js`, `dt32-questcheck.js`,
`dt37-orgcheck.js`, `dt14-generatecheck.js`, `pr-authoring.js`,
`remaining-authoring.js`, `ar-validation.js`, `ar-assets.js`, `review-core.js`
(parser/validator parity for route stops).

### Pitfalls

- Keep the checkers and the engine's parser in step. `ScriptCheck` reads
  `ScriptingEngine.progs`, `methods` and `funcs`, so a new command or function is
  accepted automatically, but its argument rules need a `ScriptSignatures` entry.
- Validators that "pass" a reproduced bad case are regressions: the project review
  added fixtures for false-green cases (`review-core.js`); add one when you fix a miss.

## The isolated playtest

### Purpose

Builder mode shares the cache and writes the world, so a risky test (dying, balance,
long play) is awkward. `PLAYTEST GAME` (`Commands/PlaytestCmd`) launches a **separate
process** on a disposable copy of the builder's world with a new save, so the builder
session and its files are untouched. `PLAYTEST ON/OFF` is different: it only suspends
the builder's sysop powers in place (`Prop_Playtest`) and keeps its changes.
`PLAYTEST RUN [FILE]` and `WORLD TEST [FILE]` run `.play`, `.test` or `.js` files under
`world/tests` through `tools/playtest.py` (`PlaytestRunner`).

Source: [IsolatedPlaytest.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/IsolatedPlaytest.java),
[PlaytestRunner.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/PlaytestRunner.java),
`Commands/PlaytestCmd.java`.

### How IsolatedPlaytest.prepare works

1. `Files.createTempDirectory("sud-playtest-")` with `world/`, `db/`, `saves/`, `logs/`.
2. Copy the builder's world folder, then apply `WorldFiles.plan()`: write every file the
   next SAVE would write (`plan.texts`) and delete what it would delete, so unsaved
   builder edits are in the test world. If `plan()` is null (the world did not load
   whole) it fails.
3. Copy `fakedb.schema` from the builder's database folder.
4. `SaveGame.exportPlaytest(builder, saves)` writes a save named `playtest` into the
   temp `saves/` through the normal `writeSnapshot` path with the builder's cache as the
   source database (`SaveGame.databaseDir()`); it requires builder mode.
5. Release the temp save folder's lock (`SaveSlots.release`) so the child can own it.
6. Build the command: the JVM's own `java`, `-Dsud.isolated.playtest=true`,
   `-Dsud.log.prefix=<temp>/logs/mud`, the current class path (absolute paths), `SUD`
   with `BUILDER=0 DEV=0 RESTORE=playtest WINDOWS=0`, the charset settings, and
   `WORLDDIR`, `SAVEDIR`, `DBSERVICE` pointing into the temp folder.

`launch` runs that process and, when a display exists, opens a `CharacterWindow` wired
to the process's standard input and output ("close to discard"); with no display it
returns the prepared command line to run in another terminal. When the process ends the
temp directory is deleted. In the child, `SaveGame.playerLoggedIn` sees
`sud.isolated.playtest` and gives the character `Prop_Playtest` at the starting room
so a death returns to the start.

### Invariants

- The child is an ordinary normal-mode `RESTORE` of a snapshot, so it exercises the real
  restore path; anything that cannot restore also fails the playtest.
- The playtest never writes the builder's world: its world folder and database are
  copies.
- Closing the window destroys the process; deleting the temp folder is best effort
  (`deleteTree`).

### Tests

`dt8-playtest.js`, `review-workflows.js` (independent playtest preparation),
`review-isolation.js` and `review-play.js` in `tools/fixtures/fixes/setup/`, and the
played `review` group.

## Tests

### How the suites work

- `tools/fixes.sh` runs every `tools/fixtures/fixes/*.js` (except `prelude.js`) with
  `JRUN` in a builder-mode game on a scratch copy of the world, after `prelude.js`.
  A fixture reports `FIXTEST ok FILE/NAME` or `FIXTEST FAIL FILE/NAME: why` and ends
  with `FIXTEST done FILE`. `prelude.js` provides `M` (the builder's mob), `CM`
  (`Packages.com.planet_ink.coffee_mud`), `check(name, cond, why)`, `ok`, `fail`, `done`,
  `scratchRoom()`, `newMob(cls, R)`, `scriptOn`, `scriptedMob(name, script)`, `gvar`,
  `clearVar`, `field(obj, name)`, `call(obj, name, args)` and `callTyped(obj, cls, name,
  typeNames, args)` (the last three reach private members by reflection, which is how
  fixtures exercise private statics such as `WorldFiles.importCatalog`). Engine classes
  are reachable as `CM.core.X`, `CM.core.save.X`.
- Played groups run real processes with their own world, database and saves: `st1`,
  `st6`, `pr5`, `saves` (alias `saves1`), `saves2` to `saves5`, `travel`, `presentation`,
  `language`, `review`, `remaining`, `modules`, `composition`, `combat`, `trial`,
  `itemcraft`, plus `jrun` for the JavaScript tests. Run one with `tools/fixes.sh GROUP`;
  run chosen fixtures with `tools/fixes.sh NAME ...`; `KEEP=1` keeps the scratch folder.
- `tools/storage-tests.sh` compiles and runs `tools/SaveStorageTest.java` (file-level
  save safety, with a real second process).
- `tools/smoke.sh` is the single-player path; `tools/check.sh` is the commit check
  (fixes and smoke side by side); `ENGINE=1 tools/check.sh` adds the engine suite
  (`tools/engine-tests.sh`, `TEST ALL`, about 15 minutes). Package-specific drivers:
  `tools/modules-integration.py`, `combat-integration.py`, `composition-integration.py`,
  `item-modifier-integration.py`, `review-integration.py`, `remaining-integration.py`
  and their `*-tests.sh` wrappers.

```sh
tools/fixes.sh optional-modules      # one fixture
tools/fixes.sh saves3                # one played group
tools/storage-tests.sh               # save folder safety
```

### Coverage by subsystem

| Subsystem | Where it is tested |
| --- | --- |
| Save folder safety | `SaveStorageTest`; `saves*` groups |
| WorldState capture/apply | `s5-law.js`, `s9-states.js`, `item1-uniques.js`, `js7-store.js`, `t-instances.js`, `review-*`, played `saves*` |
| World files and cache | `b2-handedits`, `b5-reload`, `b6-undo`, `catalog-import`, `s6-overlay`, `dt27-dev`, `ar-assets`, `w21-lawfile`, smoke |
| Modules | `optional-modules`, `ability-composition`, `combat-profiles`, `item-modifiers`, `modules-integration.py` |
| Identities and story text | `review-core`, `ar-shared`, `pr-*`, `l-language` |
| Script events | `st*.js`, `dt7-script.js`, `p21-scripts.js` |
| JavaScript | `js*.js`, `review-reload`, `review-workflows` |
| Party and time | `party-controls`, `sc8-party`, `st10-party`, `w28-timemode`, `dt23-timescale`, `sc2-time` |
| Travel | `t-*.js`, `ar-journeys` |
| Organisations | `f-*.js`, `ar-organizations`, `dt37-orgcheck` |
| Checks | `dt11` to `dt14`, `dt32`, `dt37`, `pr-authoring`, `remaining-authoring`, `ar-validation` |

## Recipes for common changes

### Add a field that must survive a save

1. Decide who owns it (see [Extension points](#extension-points-persisting-new-state)).
2. For behaviours/effects: implement `getStat("STATE")`/`setStat("STATE", ...)`. Write
   times as game time left. Return an empty string when there is nothing to keep.
3. Add a fixture in the style of `s9-states.js`: set state, read it with `getStat`, put
   it back on a fresh instance, assert equality.
4. If it names other objects, use `InstanceIds.reference` and resolve in `setStat` late
   or in `restoreTravelState`-like callbacks.
5. If a restore can't hold it, add a `WorldState.notKept` line so `SAVES CHECK` says so.

### Add a world file kind

1. Constants and a loader in `WorldFiles` (`importExtras` or `loadWorldFiles`), in the
   right place in the boot (after what it needs).
2. A writer in `exportAllReadable` using `writeOrDelete`/`writeFolder` so B2 and backups
   apply.
3. List its files in `contentPaths()` (and `managedPaths()` if the export writes it).
4. Decide whether it belongs in the world sum; if hand-authored and versioned by its
   own system, put it in `WorldAssets.VERSIONED_APART`.
5. Add a fixture using a separate `WORLDDIR` (see `s6-overlay.js`).

### Add a header setting

Write it in `writeSnapshot`, read it in `beforeBoot` (before the world loads) or
`afterBoot`, and set a default when absent so old saves load. Remember
`SaveSlots.validateFormat` only checks `version`.

### Add a restore-time reset

If a class keeps static state that must not leak between games, add a `reset()` and
call it from `SaveGame.beforeBoot` next to `JSJobs.reset()`.

### Add a check

See [Add a part to WorldCheck](#worldcheck) and, for properties, an
`AuthoringSchema.schema(...)` line.

## Pitfalls

- Static state is the norm in this layer. A restore runs in the same process, so every
  new static needs a reset path; tests that pass in a fresh process and fail after
  `RESTORE` are almost always a missing reset.
- Game time versus real time: store game time left or absolute game time, never system
  time, in saved state.
- Do not hold live objects across restores or reloads; hold references and resolve them.
- Builder mode and normal mode diverge in the same methods (`SaveGame.isBuilderMode()`):
  test both when you change anything near saves, the cache or `WorldFiles`.
- Order matters at boot (custom objects before classes load; extras before rooms;
  catalog after areas) and at restore (see the diagram). Moving a call is a behaviour
  change.
- Anything the exporter or checks parse is a file format. Changing a format means
  keeping old files readable (the exporter rewrites on the next SAVE, but a game may
  load an old cache or save first).
- Class loading: `core/` is loaded once by the system class loader, while the session
  classes (`Common/*Session`) are loaded again at each boot (the note in `ConsoleInput`
  says so). Static state in `core/` therefore survives an in-process restore unless a
  reset clears it; do not assume a class outside `core/` keeps its statics, and check
  [chapter 32](32-engine-programming.md) for how the engine loads each kind.

## See also

- [Chapter 32: engine programming](32-engine-programming.md): object model, message flow,
  writing new classes, build and class loading.
- [Chapter 2: world workflow](02-world-workflow.md): the creator view of world files,
  SAVE, WORLD STATUS/DIFF/RELOAD, UNDO.
- [Chapter 31: saves and party](31-saves-and-party.md): the single-player model.
- [Chapter 14: optional modules](14-optional-modules.md), and the families it supports:
  [15](15-ability-composition.md), [16](16-combat-profiles.md),
  [17](17-item-modifiers-crafting.md).
- [Chapter 5: JavaScript](05-javascript.md), [chapter 4: MOBPROG](04-mobprog.md) and
  [chapter 26: triggers](26-mobprog-triggers.md).
- [Chapter 30: travel, vehicles and instances](30-travel-vehicles-instances.md).
- [Chapter 13: testing and release](13-testing-release.md) for the creator-side test tools.
- [State, persistence and reload matrix](state-and-persistence.md).
- Plans: `PLAN.md` (phases 3 to 5: clock, saves, world files), `DEVPLAN.md`
  (engine capabilities), `audit/project-review-fixes.md` (save safety and identity
  follow-ups), `audit/S-saves-and-world-files.md` (the save and world-file feature
  proposals, many of them built).
