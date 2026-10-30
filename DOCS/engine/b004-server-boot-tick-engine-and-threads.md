# Server boot, tick engine and threads

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b004` (Server boot, tick engine and threads) · 11 files · 5977 lines of Java.

## Summary

Almost everything that changes over time in CoffeeMud (mobs, items, rooms, areas, spell and effect timers, behaviours, scripts, clocks and background services) is a `Tickable` registered with a tick ID and called back on a schedule. The default schedule is one global tick of 4 seconds (`TICKTIME`). Durations are counted in ticks, so the tick is both the game's clock and its throttle. Registrations are batched into tick groups that share an interval, and each group runs as one job on a per-host worker pool, while a single scheduler thread decides which groups are due. The same code also boots and shuts down each host in a fixed sequence, and it runs a watchdog that reports and restarts stuck tick groups. For a single-player SUD the reusable idea is the tick as the unit of time with per-object countdowns. The thread pools, host groups, session dispatch and connection handling are multiplayer machinery.

## Key files and types

Paths are relative to `com/planet_ink/coffee_mud/` unless they start with another folder.

| File or class | Role | Lines |
|---|---|---|
| `application/MUD.java` | Process entry point (`main`), restart loop, host groups, per-port listeners, connection acceptance, staged `globalShutdown` | 2471 |
| `core/threads/ServiceEngine.java` | The `ThreadEngine` implementation: tick-group registry, scheduler thread, worker pools, `checkHealth` watchdog, status reports | 1871 |
| `Libraries/interfaces/ThreadEngine.java` | Public API: `startTickDown`, `deleteTick`, `suspendAll`, `executeRunnable`, `scheduleRunnable`, reports | 485 |
| `core/threads/StdTickGroup.java` | A batch of tickers sharing one interval; run as one pool job | 380 |
| `core/threads/StdTickClient.java` | One registration: object, tick ID, countdown in group passes, timing stats | 223 |
| `core/threads/CMThreadPoolExecutor.java` | Per-host worker pool with an idle-first queue policy and a timeout path | 235 |
| `core/threads/CMThreadFactory.java` | Names worker threads; keeps a list of every thread it has made | 57 |
| `core/threads/CMFactoryThread.java` | Thread subclass that records which Runnable it is running | 52 |
| `core/threads/CMRunnable.java` | Interface for pool jobs (start time, active time, group ID) | 40 |
| `core/threads/TimeMs.java` | Synchronized millisecond timestamp holder; no users inside this bucket | 61 |
| `application/Shutdown.java` | Standalone admin client that sends a `<SHUTDOWN ...>` control message to a running server | 102 |

Related interfaces outside this bucket: `core/interfaces/Tickable.java` (tick IDs and status codes), `core/interfaces/TickableGroup.java:44`, `core/interfaces/TickClient.java:27`.

## How it works

### 1. Time constants

- `TIME_TICK = 4000` (`core/CMProps.java:681`) is the default tick, read from `TICKTIME` (`coffeemud.ini:2151`). Values below 500 ms are reset to 4000 (`core/CMProps.java:2720-2722`).
- Derived values: 15 ticks per real minute, 900 per real hour, 21,600 per real day (`core/CMProps.java:2724-2726`; `getTicksPerMinute()` at `:3548-3551`).
- Game time: `MILLISPERMUDHOUR=600000` (`coffeemud.ini:1099`), so `getTicksPerMudHour()` is 600000 / 4000 = **150 ticks per mud hour** (`core/CMProps.java:3539-3542`).
- Fixed service periods (`core/interfaces/MudHost.java`): `TIME_SAVETHREAD_SLEEP` = 60 min (line 46), `TIME_UTILTHREAD_SLEEP` = 10 min (line 48), `MAIN_HOST` = `'0'` (line 50).

### 2. Vocabulary

- A **Tickable** implements `boolean tick(Tickable ticking, int tickID)`. Returning false unregisters it (`core/threads/StdTickClient.java:153-155`).
- **Tick IDs** are integers (`core/interfaces/Tickable.java:82-136`). Two flag bits change how a tick is scheduled: `TICKID_LONGERMASK` = 256 (watchdog grace period, section 13) and `TICKID_SOLITARYMASK` = 65536 (gets its own group). `TICKID_SHORTERMASK` = 255 is the low-byte mask for the ID itself (`Tickable.java:75-79`).
- A **TickClient** (`StdTickClient`) is one registration: the object, its tick ID and its countdown.
- A **TickableGroup** (`StdTickGroup`) is a batch of TickClients that share one interval. It implements `CMRunnable`, so the pool can run it directly.
- **Status codes** (`Tickable.java:139-179`) are what `getTickStatus()` returns. The watchdog reads them to say what a stuck object was doing.

### 3. Registration: `startTickDown`

`startTickDown(E, tickID, numTicks)` uses the global tick (`core/threads/ServiceEngine.java:349-352`). The group is chosen in `ServiceEngine.java:373-408`:

1. If a group already holds `(E, tickID)`, that registration is un-suspended and nothing is added. The method returns null (`:379-385`). Calls are therefore idempotent, and the return value can be null.
2. Otherwise an existing group is reused only if it has the same interval, is not solitary, has fewer than `getMaxObjectsPerThread()` members, and its thread-group name starts with the same character (`:387-397`).
3. A solitary tick ID (bit 65536) always gets a new group (`:398-403`).
4. The new client is `new StdTickClient(E, numTicks, tickID)` (`:405-407`). It fires after `numTicks` group passes, then every `numTicks` passes.

The effective period is `numTicks x interval`. `startTickDown(this, TICKID_MOB, 1)` fires every 4 seconds.

Group size comes from `OBJSPERTHREAD=512` (`coffeemud.ini:2143`). If the setting is 0 or missing, `getMaxObjectsPerThread()` returns 128 (`ServiceEngine.java:300-309`).

### 4. The scheduler loop (`ServiceEngine.run`, `ServiceEngine.java:1681-1778`)

One daemon thread named `Scheduler<c>` runs the loop (`:1846-1851`). Each pass does this:

1. Polls once per second until the host is `RUNNING` (`:1683-1684`). Nothing ticks before that.
2. While the engine is suspended, it sleeps 2 s, or runs the exempt runnables every 100 ms (`:1690-1709`; section 9).
3. Sets `globalTickID` from elapsed time (`:1711`). Nothing reads it. The protected getter at `:311-314` has no callers.
4. Dispatches due `scheduleRunnable` jobs (`:1713-1736`).
5. For each group that is not running and not queued, and whose `nextTickTime` is due, submits it to the pool for its thread group (`:1737-1748`).
6. Sleeps until the earliest due time, at most one hour away (`:1712`, `:1760-1764`). Changes to the schedule interrupt the sleep: `addTickGroup`, `delTickGroup` and `scheduleRunnable` (`:243-247`, `:319-320`, `:327-328`).

### 5. A group run (`StdTickGroup.run`, `StdTickGroup.java:332-371`)

- Records `lastStart` and sets `nextTickTime = start + tickTime` (`:335-336`). A run that overruns its interval is followed by a new run immediately.
- Skips all ticking if the host is not `RUNNING` or the engine is suspended (`:342-343`).
- Walks its TickClients in set order and calls `tickTicker(false)`. A true return removes that client (`:346-353`).
- Updates counters in a `finally` block (`:359-364`). A group that becomes empty removes itself from the engine (`:366-370`).
- Clients are kept in an `STreeSet`, which copies the whole tree on every add or remove (`core/collections/STreeSet.java:221-233`, `:397-409`). Iterators are snapshots, so an object can remove itself mid-walk. Each change costs O(n).
- Iteration order comes from `StdTickClient.compareTo` (`:111-120`): the object's `hashCode()`, then the tick ID. Only 36 source files override `hashCode()`, so the order is effectively identity order. It is arbitrary but stable.

### 6. Per-object countdown (`StdTickClient.tickTicker`, `StdTickClient.java:140-180`)

```java
if((!suspended) && ((--tickDown)<1))    // line 148: a suspended client does not count down
{
    tickDown=reTickDown;                 // line 150
    if(!clientObject.tick(clientObject,tickID)) return true;   // 153-155: false removes it
}
```

- `tickTicker(true)` forces the next pass to fire (`:146-147`). Rejuvenation uses this for room items and dead mobs (`ServiceEngine.java:858-877`).
- Any Throwable from `tick()` is logged, and the client is removed (`StdTickClient.java:158-161`). One exception unregisters that object for good.
- Time is accumulated in nanoseconds and converted to milliseconds (`:165-178`) for the status reports.

### 7. Worker pools (`ServiceEngine.getPoolExecutor`, `CMThreadPoolExecutor`)

- There is one pool per first character of a thread-group name. The array is `threadPools[256]` (`ServiceEngine.java:58`, `:142-163`). Host `0-MUD` gets pool `'0'`, and its workers are named `Worker0#n` (`:151`; `CMThreadFactory.java:48`).
- Sizes: core = `MINWORKERTHREADS` (default 5, `coffeemud.ini:2159`), max = `MAXWORKERTHREADS` (60, `coffeemud.ini:2166`; the code fallback is 100, `core/CMProps.java:3136`). Non-core threads time out after 2 ticks = 8 s (`ServiceEngine.java:152`). The queue holds 1024 jobs (`:152`).
- Queue policy: `CMArrayBlockingQueue.offer` accepts a job only when `active + queued < poolSize` (`CMThreadPoolExecutor.java:58-63`). The pool therefore adds a thread before it queues. If no thread can be added, the rejection handler blocks in `queue.put` (`:78-92`).
- Duplicate guard: `pendingTasks` makes `execute` a no-op for a job that is already queued (`:136-137`). `isActiveOrQueued` stops the scheduler from submitting a group twice (`:124-127`; `ServiceEngine.java:1741`).
- Each worker records its current Runnable (`CMFactoryThread.java:38-41`; `CMThreadPoolExecutor.java:96-111`). `findRunnableByThread` reads it. This is how `MUD.threadList` names a stuck session or group.

Jobs submitted to pools are tick groups (`ServiceEngine.java:1747`), connection acceptors (`MUD.java:151`), session runs (`Libraries/Sessions.java:315-317`) and scheduled runnables (`ServiceEngine.java:1735`).

### 8. Periodic services at other rates

| Interval | Verified call site | Note |
|---|---|---|
| 100 ms | Session dispatcher `THSessions` (`Libraries/Sessions.java:299`) | Re-dispatches sessions that are not running (`:315-317`) |
| 250 ms | I3 server thread (`Libraries/intermud/i3/server/ServerThread.java:230`) | |
| 500 ms | `Prop_InstantDeath` (`Abilities/Properties/Prop_InstantDeath.java:310`) | |
| 1000 ms | Ballistic launch (`Items/CompTech/StdCompLauncher.java:366`) | |
| 1234 ms | Item generator (`Behaviors/ItemGenerator.java:402`) | |
| 4000 ms (= `TICKTIME`) | Default for mobs, items, rooms, areas, effects | |
| About 5 min | SMTP server (`MUD.java:1579`) | 5 x `getTicksPerMinute()` = 75 ticks at the default |
| 10 min | `THThreads` watchdog (`ServiceEngine.java:1845`); session sweep (`Libraries/Sessions.java:321-323`) | |
| 60 min | Save threads (`Libraries/CMMap.java:2439`, `CMPlayers.java:3478`, `CMCatalog.java:988`, `Libraries/CoffeeTables.java:142`) | |

### 9. Suspension and the Pause command

- `suspendAll(exceptRs)` sets a flag (`ServiceEngine.java:477-481`). The scheduler then stops dispatching groups. It runs the exempt runnables every 100 ms on the scheduler thread instead (`:1690-1706`).
- Two callers. The PAUSE command suspends everything except the admin's own session (`Commands/Pause.java:71`, with the message at `:72`). `MUD.globalShutdown` suspends everything with no exemptions (`MUD.java:702`).
- `suspendResumeRecurse` freezes an object and everything it holds (`ServiceEngine.java:964-1017`). An Area is set to FROZEN or ACTIVE (`:1013`).

### 10. What the 4-second tick drives

**Mobs** (`MOBS/StdMOB.java:4007-4396`; runs when `tickID == TICKID_MOB`, `:4012`):

- *Dead.* The rejuvenation counter goes down one per tick (`:4021-4023`). When it passes below zero, the monster is reborn in its start room with "<S-NAME> appears!" (`:4026-4060`). Respawn is therefore counted in ticks. If no valid start room exists the monster is destroyed (`:4027-4035`).
- *Alive.* NPCs run a validity check every 60 ticks (`:4081-4083`). Turn-based combat (`:4129-4138`) or the default system (`:4140-4145`) accrues `actions` by `speed` per tick, halved while sitting. The default system adds one bonus action while in combat (`:4207-4208`). While in combat, each tick calls `CMLib.combat().tickCombat(this)` (`:4212`).
- *Breath.* Water and gas atmospheres do random damage of up to level + 2 per tick (`:4147-4194`), with messages such as "can't breathe", "drowning in" and "choking on" (`:4155`, `:4171`, `:4184-4186`).
- *Recovery.* Every `RECOVERRATE x (5 + stat adjustment)` ticks (`:4156-4157`, `:4196-4201`). The shipped `RECOVERRATE=1` (`coffeemud.ini:691`) gives a recovery every 5 ticks, about 20 seconds.
- *Peace time* grows by one tick's worth of milliseconds per tick out of combat (`:4222`). Autodraw sheathing happens in a time window (`:4223-4227`).
- *Fatigue.* Non-immortal players gain one tick's worth of fatigue per tick (`:4236-4247`). Sleep removes `REST_PER_SLEEP` (`:4241-4242`). `REST_PER_SLEEP = tick x 200` (`Common/interfaces/CharState.java:71`) and `FATIGUED_MILLIS = tick x 3000` (`:75`). At the default tick, fatigue becomes significant after 3000 ticks, about 3.3 real hours.
- *Effects, behaviours, scripts, factions, class, race.* Effects are ticked, and an effect returning false is unInvoked (`:4318-4330`). Then mana (`:4332`), behaviours (`:4337-4348`), scripts (`:4349-4360`), factions (`:4362-4387`), class and race (`:4389-4395`).

**Abilities and effects** (`Abilities/StdAbility.java`):

- An effect on a mob has no ticker of its own. The mob ticks it (`StdMOB.java:4318-4330`). For a non-mob target (room, item, exit) the effect registers itself as a `TICKID_MOB` ticker with a count of 1 (`StdAbility.java:685-690`).
- `startTickDown` converts the requested duration in ticks. It adds `addedTickTime`, class duration modifiers and `TICK+...` stat adjustments (`:709-713`, `:748-751`), then sets `tickDown` (`:779`).
- Each tick, a decrementable effect counts down. At zero it calls `unInvoke()` and returns false (`:2848-2859`). `TICKS_ALMOST_FOREVER` = `Integer.MAX_VALUE/2` (`Abilities/interfaces/Ability.java:48`).

**Areas** (`Areas/StdArea.java:1277-1326`; the ticker starts when the area becomes ACTIVE, `:558-562`):

- An area goes PASSIVE after 30 minutes with no player (`Areas/interfaces/Area.java:812`; `StdArea.java:1284-1290`). If the PASSIVEAREAS security flag is disabled, the timer is reset instead, and instance children are never made passive (`:1286`).
- Each tick runs the climate and time objects (`:1292-1294`), then behaviours, scripts and effects (`:1296-1322`).

**Rooms** (`Locales/StdRoom.java:1225-1275`): behaviours and scripts run under `TICKID_ROOM_BEHAVIOR` (`:1234-1256`). That ticker is registered only when the room needs it (`:3040`, `:3151`). Other ticks handle room effects (`:1257-1272`). Room respawn uses `TICKID_ROOM_ITEM_REJUV` (`:1170`).

**Items** (`Items/Basic/StdItem.java:748-791`): every `10 + d100` ticks the item runs `validCheck` and is destroyed if invalid (`:752-763`). Behaviours and scripts run under `TICKID_ITEM_BEHAVIOR` (`:767-791`), registered at `:2070` and `:2181`.

**The game clock** (`Common/DefaultTimeClock.java:1084-1151`): the clock is sampled on each tick. When a mud hour has passed since the last advance it calls `tickTock(1)` (`:1087-1095`, `:1150-1151`). It is wall-clock driven. After a stall it advances by one hour, not by the elapsed hours. Missed hours are not replayed.

**Shutdown countdown** (`Commands/Shutdown.java:184`, `:234-251`): one ticker on `TICKID_AREA` compares wall-clock times. It re-announces each time half the remaining time has gone (`:247`), and starts the shutdown at the deadline (`:239-242`).

### 11. Boot sequence

1. `MUD.main` (`MUD.java:2074`) names the thread `MUD`. It parses `BOOT=` ini files, `NAME=` and `NAME:X=` overrides (`:2079-2113`), and starts logging (`:2142-2148`). It prints `CoffeeMud(R) v` plus `HOST_VERSION` = `5.11.0.4` (`:86`, `:2156`).
2. For each ini file it creates a `ThreadGroup "<i>-MUD"` and a daemon `HostGroup` thread (`:2164-2190`). The first character of the group name is the host code (`:1341`). It waits up to 30 minutes for the main host (`:2199-2212`), installs a JVM shutdown hook (`:2221-2234`), opens connections on every host (`:2235-2236`) and joins the main host group (`:2240`).
3. `HostGroup.run` (`MUD.java:1847`) initialises the libraries and classes (`:1849-1850`), loads the ini (`:1865`), applies command-line overrides (`:1873-1886`), sets state BOOTING (`:1888`) and calls `serviceEngine.activate()` (`:1889`). Activation creates pool `'0'` (`ServiceEngine.java:1783`), registers the THThreads watchdog (`:1786-1845`) and starts the scheduler (`:1846-1851`). The scheduler waits for RUNNING, so no tick runs yet.
4. The main host registers the engine as library THREADS (`MUD.java:1929-1930`). Other hosts reuse main's THREADS and INTERMUD libraries (`:1931-1935`) and wait for main to finish (`:1857-1863`).
5. `PORT` and `PROXY` are parsed (`:1945-1981`), and each port gets a `MUD@port` thread (`:1967-1983`). Startup waits up to 60 seconds for one listener to leave STARTING (`:1991-2010`).
6. `initHost` (`MUD.java:1412-1844`) does the following in order:
   - Reads the compression and no-cache flags (`:1426-1441`).
   - Connects to the database. `DBCONNECTIONS=0` exits the process (`:1472-1476`). It validates the schema and upgrades it if needed (`:1512-1530`).
   - Starts web servers and SMTP (`:1562-1585`).
   - Loads classes, factions, expertises, channels, journals, security and socials (`:1594-1668`).
   - Registers the faction tick at `TICKID_MOB` with a 10-tick interval (`:1672`).
   - Loads the map: rooms, space, artifacts, then fills areas and sends `MSG_STARTUP` to every room (`:1685-1717`). If the map is empty it creates "New Area#0" with a manual archon item (`:1723-1742`).
   - Loads clans and quests (`:1755-1780`). Non-main hosts wait for main to be RUNNING (`:1782-1797`).
   - Starts the clocks and calls `activateLibraries` (`:1812-1821`). Sets RUNNING (`:1839`) and "Online" (`:1840`).
7. Listeners enter WAITING and call `accept()` (`:535-540`). Each socket becomes a `ConnectionAcceptor` job on the pool (`:146-153`). Until `acceptConns` is set true, a connection gets the offline text if the database is connected, and is otherwise closed (`:408-435`).
8. A `ConnectionAcceptor` (`:245-460`) creates a `DefaultSession` (`:296`) and checks connection limits (`:316-319`). A blocked address gets `text/connblocked.txt` (with minutes substituted) or `text/blocked.txt` (`:330-337`). Otherwise it gets a random `text/intro*.txt` (`:369-383`) and runs the session inline (`:398`). Rejected sockets are closed one second later by `closeLater` (`:190-240`).

### 12. Shutdown sequence

Entry points: the in-game `shutdown` command (`Commands/Shutdown.java:205-218`, which calls `globalShutdown(session, true, "")` on a new thread), the JVM shutdown hook (`MUD.java:2221-2234`), and `defaultShutdown` (`:593-596`).

`MUD.globalShutdown` (`MUD.java:669-1049`):

1. Sets SHUTTINGDOWN (`:671`). A watchdog prints a stack dump if there is no progress for 5 minutes (`:675-697`, `SHUTDOWN_TIMEOUT` at `:120`).
2. Suspends ticking (`:702`) and closes listeners (`:705-706`).
3. Saves players (`:711-768`). First it unInvokes undoable effects and deletes non-savable ones (`:727-741`), so nothing transient is written.
4. Saves statistics (`:772-783`).
5. Sets every area to STOPPED (`:799-804`) and sends `MSG_SHUTDOWN` to every room (`:806-824`).
6. Shuts down QUEST, TECH and SESSIONS (`:832-845`).
7. If room-save flags are set, it runs `tickAllTickers(null)` (`:855`). That is one normal pass over every ticker, run on the shutdown thread (`ServiceEngine.java:922-961`). Then it sends `MSG_EXPIRE` to each room (`MUD.java:877-878`).
8. Stops sessions, waiting 3 seconds (`:892-919`). Stops SMTP (`:924-933`).
9. Shuts down STATS and THREADS (`:936-938`), then SOCIALS through PLAYERS, then every remaining library (`:943-954`).
10. Releases resources, DB connections, web servers, macros and classes (`:957-1006`).
11. Sets `bringDown = keepItDown`, resumes the engine, kills host threads and clears the host list (`:1013-1044`). Sets STOPPED if not restarting (`:1046-1047`).

`ServiceEngine.shutdown` (`ServiceEngine.java:1234-1291`) stops and removes every group (`:1237-1248`), kills the scheduler (`:1250`), calls `tick` once on each area clock and saves it (`:1252-1266`), then shuts the pools down with 2-second and 3-second timeouts (`:1267-1288`).

### 13. The watchdog: `checkHealth` (`ServiceEngine.java:1562-1678`)

- Runs as the THThreads tick every 10 minutes (`:1845`), unless the UTILITHREAD or THREADTHREAD security flags are disabled (`:1834-1836`).
- A group is suspect if it has been running for more than 5 minutes (`:1564`, `:1576-1578`). A group whose current ticker lacks `TICKID_LONGERMASK` is reported at once. A LONGER ticker is tolerated until the group's previous finish is more than 120 minutes old (`:1565`, `:1585`).
- Each suspect is logged as `LOCKED GROUP ...` with the object name, status code, tick ID and location (`:1583-1613`). The stack of the group's thread is dumped (`:1617`).
- The group is killed (`:1640-1644`) and its clients are re-added with `startTickDown(obj, id, getTotalTickDown())` (`:1655`). That call uses the default interval, not the group's own interval (see section 14).
- Overdue web-server threads are killed (`:1658-1676`).

### 14. Status reports

`getTickInfoReport` and `getSystemReport` expose counters per group and per client (`ServiceEngine.java:537-839`, `:1121-1231`): ticker counts, total and average milliseconds, and the top consumers. The admin LIST command and the web SystemInfo macro read them (`Commands/ListCmd.java:3261-3276`, `WebMacros/SystemInfo.java:59`).

### 15. Messages shown to players

- Respawn: "<S-NAME> appears!" (`StdMOB.java:4060`).
- Breathing: "can't breathe", "drowning in ...", "choking on ..." (`StdMOB.java:4155`, `:4171`, `:4184-4186`).
- Connections: text files `connblocked.txt`, `blocked.txt` and `offline.txt`, plus an "OFFLINE:" status line (`MUD.java:333-337`, `:415-424`).
- Shutdown: "<MUD name> is now shutting down!" and "Starting <MUD name>...shutdown" (`Commands/Shutdown.java:213`, `:215`).
- Pause: "All objects have been suspended. Enter PAUSE again to resume." (`Commands/Pause.java:71-72`).

## Content worth mining

### Tick IDs with example uses

| Value | Constant | Example use (verified call site) |
|---|---|---|
| 0 | `TICKID_MOB` | Mob turns and effects on non-mob targets (`StdMOB.java:4012`, `StdAbility.java:689`) |
| 1 | `TICKID_ITEM_BEHAVIOR` | Item behaviours and scripts (`Items/Basic/StdItem.java:767`, `:2070`) |
| 2 | `TICKID_EXIT_REOPEN` | Doors and containers reopening after a delay (`Exits/StdExit.java:759`, `Items/Basic/StdContainer.java:424`) |
| 4 | `TICKID_LIGHT_FLICKERS` | Lamps and stoves (`Items/Basic/LightSource.java:114`, `GenStove.java:274`) |
| 5, 6 | `TICKID_TRAP_RESET`, `TICKID_TRAP_DESTRUCTION` | Traps (`Abilities/Thief/Thief_DeathTrap.java:140`, `:189`) |
| 7 | `TICKID_ITEM_BOUNCEBACK` | Artifacts and returning items (`Abilities/Properties/Prop_Artifact.java:294`, `Items/Basic/StdInnKey.java:136`) |
| 8 | `TICKID_ROOM_BEHAVIOR` | Room behaviours and scripts (`Locales/StdRoom.java:3040`) |
| 9 | `TICKID_AREA` | Area climate, time and behaviours (`Areas/StdArea.java:1282`); shutdown countdown (`Commands/Shutdown.java:184`) |
| 10 | `TICKID_ROOM_ITEM_REJUV` | Room respawn (`Locales/StdRoom.java:1170`) |
| 11 | `TICKID_EXIT_BEHAVIOR` | Exit behaviours (`Exits/StdExit.java:1153`) |
| 12 | `TICKID_SPELL_AFFECT` | Timed room effects (`Abilities/Prayers/Prayer_Fertilize.java:114`) |
| 13 | `TICKID_QUEST` | Quest timers (`Common/DefaultQuest.java:527`) |
| 14 | `TICKID_CLAN` | Clan upkeep (`Libraries/Clans.java:512`) |
| 16 | `TICKID_EMAIL` | SMTP service (`MUD.java:1579`) |
| 18 | `TICKID_LIVEAUCTION` | Auctions (`Commands/Auction.java:267`) |
| 20 | `TICKID_MISCELLANEOUS` | Instant-death property (`Abilities/Properties/Prop_InstantDeath.java:256`); THThreads watchdog (`ServiceEngine.java:1845`) |
| 22 | `TICKID_ELECTRONICS` | Tricorders and shields (`Items/BasicTech/StdTriCorder.java:195`) |
| 23 | `TICKID_SUPPORT` | Background services: sessions, combat engine, save threads (`Libraries/Sessions.java:299`, `Libraries/MUDFight.java:129`, `Libraries/CMMap.java:2439`) |
| 24, 25 | `TICKID_SPECIALMANEUVER`, `TICKID_SPECIALCOMBAT` | Ship manoeuvres and siege fire (`Items/Weapons/StdSiegeWeapon.java:420`) |
| 26 | `TICKID_BALLISTICK` | Projectile flight (`Items/CompTech/StdCompLauncher.java:366`, `StdShipWeapon.java:353`) |
| 27 | `TICKID_EVENT` | Scheduled journal events (`Libraries/CMJournals.java:1178`) |

### Constants a text game could reuse

| Name | Value | Where |
|---|---|---|
| Global tick | 4000 ms (`TICKTIME`) | `core/CMProps.java:681`, `coffeemud.ini:2151` |
| Ticks per mud hour | 150 (600000 / 4000) | `core/CMProps.java:3539-3542` |
| Objects per group | 512 | `coffeemud.ini:2143` |
| Worker threads | 5 core, 60 max | `coffeemud.ini:2159`, `:2166` |
| Fatigued | 3000 ticks | `Common/interfaces/CharState.java:75` |
| Rest per sleep | 200 ticks | `CharState.java:71` |
| Recovery | every 5 ticks at `RECOVERRATE=1` | `coffeemud.ini:691`, `StdMOB.java:4156-4157` |
| NPC validity check | every 60 ticks | `StdMOB.java:4083` |
| Item validity check | every 10 + d100 ticks | `StdItem.java:755` |
| Area goes passive | 30 min without a player | `Areas/interfaces/Area.java:812` |
| Long-running group report | 5 min; LONGER grace 120 min | `ServiceEngine.java:1564-1565` |
| Shutdown watchdog | 5 min | `MUD.java:120` |

## Notable design ideas

1. **The tick is both clock and throttle.** Durations are integers in ticks, so changing `TICKTIME` rescales the whole world. The shipped ini calls this the "CoffeeMud Throttle" (`coffeemud.ini:2145-2150`).
2. **The group is the unit of scheduling.** Objects are batched by interval and share a thread group, so one pool job runs a batch of objects in sequence. Parallelism comes from the number of groups, not from locking each object.
3. **Effects ride on their owner's ticker.** A mob with ten buffs costs one `tick()` call, and each buff is a `tickDown` counter (`StdMOB.java:4318-4330`, `StdAbility.java:2848-2859`).
4. **Flag bits set the policy.** `SOLITARY` gives a private group (`Sessions` at 100 ms), `LONGER` gives the watchdog grace period (`Tickable.java:75-79`).
5. **Status codes make stalls explainable.** Each object reports what it was doing (`Tickable.java:139-179`), so the watchdog can name the offender.
6. **Save order settles the world first.** Shutdown saves players before sessions, expires rooms and runs one settle pass before writing map data (`MUD.java:711-878`), and drops undoable effects before saving players (`:727-741`).
7. **Pause keeps the admin's session alive.** Suspension exempts one session and runs it every 100 ms (`Commands/Pause.java:71`, `ServiceEngine.java:1690-1706`).
8. **A self-healing watchdog.** `checkHealth` dumps stacks, kills stuck groups and re-adds their members (`ServiceEngine.java:1617`, `:1640-1655`).
9. **Idle-first pool.** The pool adds a thread before it queues a job (`CMThreadPoolExecutor.java:58-63`), which keeps latency low during bursts.
10. **Copy-on-write sets.** `STreeSet` lets an object remove itself while a group iterates, with no locks, at O(n) cost per change (`core/collections/STreeSet.java:397-409`).

## Problems and cruft

Items 1-4 and 7 were confirmed by reading the code. The others are inferences from the code and were not run.

1. **The close loop runs on the caller's thread.** `closeLater` starts its thread with `new Thread(...).run()` (`MUD.java:237`), not `start()`. The loop runs inside `synchronized(closeables)` (`:192`), so the calling acceptor sleeps while it holds the monitor. Each rejected connection holds the lock for about a second (`:195`), and other rejections queue behind it. This is the anti-abuse path, so the cost falls on the wrong code.
2. **Removal uses the wrong key.** `active` is a `Map<Runnable, Thread>` (`CMThreadPoolExecutor.java:40`). `active.remove(t)` at `:226` passes a Thread, so it does nothing. A timed-out task stays in `active`, and therefore in `getActiveCount()` (`:114-116`), until its own `afterExecute` runs (`:110`).
3. **The timeout kill is effectively unreachable.** `getTimeoutOutRuns` is called only from the rejection branch (`:149`). The rejection handler blocks in `queue.put` (`:78-92`) and throws only on interrupt, so the timeout path almost never runs.
4. **Killed groups come back on the wrong interval.** `checkHealth` re-adds clients with the three-argument `startTickDown` (`ServiceEngine.java:1655`), which uses the default tick (`:351`). A 100 ms session dispatcher or 250 ms I3 ticker that is ever killed returns at 4 seconds. Whether the old and new registrations then run together was not checked.
5. **Unit mix in `getTimeMSToNextTick`.** `msToGo += (CMProps.getTickMillis() + tickDown)` (`StdTickClient.java:133-135`) adds a count of ticks to a value in milliseconds. The two callers only display a countdown (`Items/Weapons/StdSiegeWeapon.java:1100`, `Items/Basic/StdSiegableBoardable.java:935`), so the displayed time is wrong.
6. **Misc labels are off by one.** `getTickStatusSummary` finds the misc number by testing `1<<(10+i)` for i = 1..5 (`ServiceEngine.java:1395-1402`). `STATUS_MISC` (4096) is printed as "Misc2". `STATUS_MISC5` (65536) and `STATUS_MISC6` (131072) match no case and print "Misc0" (`Tickable.java:167-177`).
7. **Thread list only grows.** `CMThreadFactory.newThread` adds every thread to `active` (`CMThreadFactory.java:49`), and nothing removes them. `findRunnableByThread` does a linear `contains` on that list (`ServiceEngine.java:188`), so memory and lookup cost grow with thread churn.
8. **One bad tick removes the object.** A Throwable from `tick()` unregisters the client (`StdTickClient.java:158-161`). A mob's effects run inside the mob's own tick (`StdMOB.java:4318-4330`). Unless the inner loops catch errors (not checked), one failing effect could silently stop a mob's ticks.
9. **Pool creation is unsynchronised.** `getPoolExecutor(char)` checks and then assigns `threadPools[...]` with no lock (`ServiceEngine.java:142-155`). Two threads making the first pool for the same character could leak one. Pool `'0'` is created early in `activate()` (`:1783`), which limits exposure. This is a possible race, not a confirmed one.
10. **Thread-group keys use one character.** `new ThreadGroup(i+"-MUD")` (`MUD.java:2181`) and `charAt(0)` everywhere (`ServiceEngine.java:142-163`) mean host 10 shares host 1's pool and tick-group key. This only matters with ten or more ini files.
11. **Ordering contract.** `StdTickClient.compareTo` returns -1 for two different objects with equal hash codes, in both directions (`StdTickClient.java:113-114`). Such a collision breaks set lookups. Collisions are rare because most classes use identity hashes.
12. **Dead code.** `getTicksEllapsedSinceStartup` (`ServiceEngine.java:311-314`) has no callers, so `globalTickID` (`:1711`) is never used. `TimeMs` has no users in this bucket.
13. **Stalled exempt runnables stall the scheduler.** The exempt runnables run `run()` on the scheduler thread (`ServiceEngine.java:1692-1706`). A slow one delays every tick in the host. Only one session is exempt today, so the risk is small.
14. **Unverified: does a session pin a worker?** `ConnectionAcceptor` calls `sess.run()` inline (`MUD.java:398`), and `Sessions.tick` also dispatches sessions that are not running (`Libraries/Sessions.java:315-317`). Whether `Session.run()` blocks for the whole connection, which would pin one pool thread per player, is in `DefaultSession` and was not checked.
15. **Minor mismatch.** The code fallback for `MAXWORKERTHREADS` is 100 (`CMProps.java:3136`), but the shipped ini sets 60 (`coffeemud.ini:2166`).
16. **External restart command not run here.** `execExternalCommand` is stored (`MUD.java:1028`) but nothing in this bucket runs it. The launcher must, and it is outside this bucket.

## Takeaways for a Python SUD

1. **Keep the tick as the unit of game time.** Store durations as integer tick counts on each effect, decrement them in the owner's tick, and unregister by returning False. Make the tick size one named constant. If play is turn-based, the tick can be one player action and no wall clock is needed.
2. **Keep effects on their owner.** An actor ticks its own effects. Rooms and items that need timers get their own ticker. Do not create one ticker per effect.
3. **Schedule by interval buckets, not per object.** A few buckets are enough: one for the world tick, one for housekeeping (about 10 minutes) and one for autosave (about 60 minutes). A bucket is a list of entities.
4. **Use one thread and one loop.** CPython threads do not run Python code in parallel, and CoffeeMud's pools, queues, suspension flags and thread-group keys exist for multiplayer I/O. Pace the loop with `time.monotonic()`. Read input in a separate thread into a `queue.Queue` that the loop drains each tick, or use `select` on stdin. Pause becomes a flag that stops world ticks but not input.
5. **Drop** host groups, `OBJSPERTHREAD`, connection blocking, session dispatch, IP limits and thread-group keys.
6. **Do not drop failing objects silently.** Log the exception and keep the object, or quarantine it after a set number of failures.
7. **Replace the kill-and-re-add watchdog with timing.** Measure each pass and log a warning when the budget is exceeded, naming the slow entities. Python threads cannot be killed safely, so keep each tick short.
8. **Save in CoffeeMud's order.** Settle the world (one pass, respawn the dead, expire transient state), drop undoable effects, save the clock, then write files atomically with a temporary file and `os.replace`.
9. **Decide the clock policy on purpose.** CoffeeMud advances game time from wall time and does not replay missed hours. For a single-player game, store `game_ticks` in the save and advance it per tick, so time spent offline does not pass.
10. **Iterate over copies.** Python has no copy-on-write set. Iterate over `list(self.tickers)` and rebuild the list after the pass.
11. **Name tick kinds with an `Enum`.** Use names from `Tickable.java`. Avoid bit arithmetic for labels, which is where the misnumbered Misc labels came from.
12. **Order by insertion.** Use a list or `dict` so tick order is deterministic and tests are repeatable, instead of hash order.

A minimal sketch of the scheduler this suggests:

```python
import logging, time
from dataclasses import dataclass, field

log = logging.getLogger(__name__)
TICK_SECONDS = 4.0          # CoffeeMud default (TICKTIME=4000)

@dataclass
class Ticker:
    target: object          # anything with tick(kind) -> bool
    kind: str
    every: int = 1          # run once per `every` passes
    alive: bool = True
    _left: int = field(init=False)
    def __post_init__(self):
        self._left = self.every

class Scheduler:
    def __init__(self):
        self.tickers = []
        self.paused = False

    def add(self, target, kind, every=1):
        self.tickers.append(Ticker(target, kind, every))

    def pass_once(self):
        for t in list(self.tickers):
            if not t.alive:
                continue
            t._left -= 1
            if t._left > 0:
                continue
            t._left = t.every
            try:
                if t.target.tick(t.kind) is False:
                    t.alive = False
            except Exception:
                log.exception("tick failed for %r", t.target)   # keep the object
        self.tickers = [t for t in self.tickers if t.alive]

    def run(self, stop):
        while not stop():
            start = time.monotonic()
            if not self.paused:
                self.pass_once()
            time.sleep(max(0.0, TICK_SECONDS - (time.monotonic() - start)))
```

## Coverage

- **Read in full (11 of 11):** `application/MUD.java` (four chunks), `core/threads/ServiceEngine.java` (three chunks), `Libraries/interfaces/ThreadEngine.java`, `core/threads/StdTickGroup.java`, `core/threads/StdTickClient.java`, `core/threads/CMThreadPoolExecutor.java`, `core/threads/CMThreadFactory.java`, `core/threads/CMFactoryThread.java`, `core/threads/CMRunnable.java`, `core/threads/TimeMs.java`, `application/Shutdown.java`.
- **Read in part, to check cross-references:** `core/interfaces/Tickable.java` (constants by grep and ranges), `core/interfaces/MudHost.java` (lines 40-60), `core/CMProps.java` (HostState, timing and worker settings, `getTickMillis` family), `core/CMLib.java` (`killThread`, `s_sleep`, `activateLibraries`), `Common/interfaces/CharState.java` (constants by grep), `coffeemud.ini` (2130-2175, plus grep for other timing keys), `MOBS/StdMOB.java` (`tick`, 4007-4396), `Abilities/StdAbility.java` (`tick`, `addEffect`, `startTickDown`, 676-780 and 2841-2915), `Areas/StdArea.java` (`tick`, `setAreaState`), `Locales/StdRoom.java` (`tick`), `Items/Basic/StdItem.java` (`tick`), `Commands/Shutdown.java` (countdown and startup, 178-251), `Commands/Pause.java` (56-95), `Libraries/Sessions.java` (tick and start, 296-330), `Common/DefaultTimeClock.java` (`tick`, 1084-1162), `core/collections/STreeSet.java` (add, remove, iterator).
- **Searched by grep only:** the 173 `startTickDown(` occurrences (listed by file and sampled, not all read), tick-ID reference counts, `hashCode` overrides, `suspendAll` and `getTimeoutOutRuns` callers.
- **Skipped:** the rest of `StdMOB.tick` (after 4396), `DefaultSession.run` internals, `CombatLibrary.tickCombat`, the `coffee_web` server, `SMTPserver`, the `tick()` bodies of `StdExit` and `StdContainer`, and the `ListCmd` report code beyond its call sites.
- **Not run:** no server was started and no runtime behaviour was observed. Every behaviour above is from reading the source.
