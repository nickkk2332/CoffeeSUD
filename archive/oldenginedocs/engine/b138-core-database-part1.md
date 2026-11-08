# Database layer, part 1: engine interface, connection pool and record stores

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b138` (core/database [part 1/2: DatabaseEngine .. DDLGenerator]) · 10 files · 11496 lines of Java.

## Summary

Every persistent fact in CoffeeMud goes through one interface, `DatabaseEngine`, which has roughly 240 methods grouped by table: rooms, areas, characters, accounts, clan vaults, journals and mail, polls, daily statistics, channel backlogs, and generated races, classes, abilities and commands. `DBInterface` implements it as a facade that hands each group to a loader class, and the loaders run SQL over a JDBC connection pool. This is plumbing, but it sets the game's save model: data is written on demand, whole sets are often deleted and re-inserted, and nothing is dirty-tracked. The generic owner/section/key record store is what lets features add persistent data without schema changes.

## Key files and types

Text citations give the file name and line. Every file is under `com/planet_ink/coffee_mud/`.

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Libraries/interfaces/DatabaseEngine.java` | Persistence API, grouped by table in javadoc; nested `PAData`, `PollData`, `AckRecord`, `RoomContent` | 3409 |
| `com/planet_ink/coffee_mud/core/database/DDLGenerator.java` | Dialect-specific DDL and column-migration SQL | 1880 |
| `com/planet_ink/coffee_mud/core/database/DBInterface.java` | Engine implementation: facade over loaders, per-table database routing, player-instance rooms | 1822 |
| `com/planet_ink/coffee_mud/core/database/DBConnections.java` | Connection pool: fetch and release, health flags, idle pings, retry queue, identifier quoting | 1168 |
| `com/planet_ink/coffee_mud/core/database/DBConnection.java` | One JDBC connection: statement state, transactions, failure counters | 811 |
| `com/planet_ink/coffee_mud/core/database/ADataLoader.java` | Area records (CMADAT), the generic owner/section/key store | 733 |
| `com/planet_ink/coffee_mud/core/database/BackLogLoader.java` | Channel backlog (CMBKLG) with index counters | 628 |
| `com/planet_ink/coffee_mud/core/database/DBConnector.java` | Public pool facade, `injectionClean`, reconnect, ping settings | 560 |
| `com/planet_ink/coffee_mud/core/database/ClanLoader.java` | Clan records (CMCLAN) and clan vault items (CMCLIT) | 314 |
| `com/planet_ink/coffee_mud/core/database/DBCopyConversions.java` | One-off conversions for old clan and character membership tables | 171 |

## How it works

### Layers and per-table routing

`CMLib.database()` returns the engine. `DBInterface` is a thin facade, and most methods are one-line delegations, for example `DBUpdatePlayerAbilities` calls `mobLoader.DBUpdateAbilities(mob)` (`DBInterface.java:341-345`). Each loader is built with a `DBConnector`. The constructor copies loaders from an existing engine and rebuilds only the ones whose table group is named in the private list `CMProps.getPrivateSubSet("DB.*")` (`DBInterface.java:86-146`), so one table group can live in a second database. The table groups are documented at `coffeemud.ini:2307-2317`.

Player-owned instanced rooms are redirected rather than given their own subsystem. `ifPlayerRoomCreated`, `ifPlayerRoomUpdated` and `ifPlayerRoomDeleted` store such a room as a `PLAYERINSTANCE` player-data record (`DBInterface.java:582-626`), and each room-write method checks this first (for example `DBUpdateExits`, `DBInterface.java:628-634`).

### Connection pool and health

- Shipped settings (`coffeemud.ini:142-149`): fakedb driver, 5 connections, reuse on, transactions off, idle ping every 60 minutes.
- `DBFetchAny` (`DBConnections.java:369-549`) opens connections up to the maximum, otherwise takes an idle one. When the pool is full it sleeps a random 0-500 ms and tries again, with no timeout.
- `DBDone` (`DBConnections.java:602-609`) calls `closeStatements`, which commits any open transaction (`DBConnection.java:411-414`), and drops closed connections from the list. With reuse off, every fetch opens and closes a JDBC connection.
- Health flags: 50 consecutive fetch failures set `lockedUp` (`DBConnections.java:457-463`). More than 25% of pool connections reporting errors sets `disconnected` (`DBConnections.java:1060-1070`). A connection with more than two failures in a row is dead (`DBConnection.java:748-760`). A connection held for two minutes is treated as locked up (`DBConnection.java:768-774`).
- `StdLibrary.checkDatabase` (`StdLibrary.java:162-172`) pings idle connections with `SELECT 1 FROM CMCHAR` and reads `errorStatus()`. It runs from the map and player save ticks (`CMMap.java:2473`, `CMPlayers.java:3522`).

### Write path, escaping and retry

- A failed plain SQL statement is appended to a file, `SQLErrors.que`, as SQL, error and attempt count (`DBConnection.java:605-606`, `DBConnections.java:806-834`). The queue is replayed on reconnect (`DBConnector.java:161-162`, `DBConnections.java:841-973`). Items past 20 attempts are abandoned, and each replayed line costs a one-second sleep (`DBConnections.java:925`, `960`).
- Writes that carry text parameters go through `updateWithClobs` as prepared statements (`DBConnector.java:255-300`). They are never queued, so a failure is logged and the write is lost. Most large payloads (room, player and area XML) take this path.
- `updateWithClobs(List<DBPreparedBatchEntry>)` runs several statements on one connection (`DBConnections.java:178-237`). `ClanLoader` uses it to delete a clan's vault and reinsert it (`ClanLoader.java:254-262`).
- `DBConnector.injectionClean` replaces every `'` with a backtick (`DBConnector.java:429-434`). `ADataLoader` uses it, so area keys with apostrophes are silently changed. `ClanLoader` builds its SQL without any escaping.

### Generic owner/section/key records

`PAData` holds `who` (owner), `section` (category), `key` and an `xml` payload (`DatabaseEngine.java:3143-3218`). Player records live in CMPDAT and area records in CMADAT. The API queries by owner, by section, by section plus key pattern, or by an XML fragment, and deletes by owner or section (`DatabaseEngine.java:1876-2166` for players, `2186-2451` for areas). Artifacts are kept this way too (`DBReadArtifacts`, `DatabaseEngine.java:2174`). Upsert is `DBReCreatePlayerData` and `DBReCreateAreaData` (`DatabaseEngine.java:2150`, `2435`). `ADataLoader.DBReCreate` checks for the record, then updates or inserts under a lock keyed on the record key (`ADataLoader.java:489-527`).

Generated races, classes, abilities and commands are stored as XML with a base-class name (`AckRecord.typeClass`, `DatabaseEngine.java:3335-3360`; `DBCreateAbility`, `DatabaseEngine.java:2634`). This is how builder-made content survives a reboot.

### Persisted entities and save granularity

Table groups (`coffeemud.ini:2307-2317`): characters (CMCHAR, CMCHAB, CMCHFO, CMCHCL, CMCHIT, CMPDAT); world (CMROOM, CMROCH, CMROEX, CMROIT, CMAREA, CMADAT); generated definitions (CMGRAC, CMCCAC, CMGAAC, CMCDAC); CMSTAT; CMPOLL; CMVFS, a small database-backed file system; CMJRNL; CMQUESTS; clans (CMCLAN, CMCLIT, CMCHCL); CMBKLG.

Saves are split by slice. A character can be saved as its base record, player stats, abilities, items or followers, each separately (`DatabaseEngine.java:813-877`). Room contents are rewritten as sets: `DBUpdateTheseMOBs` deletes a room's mobs and reinserts them (`DatabaseEngine.java:538-546`).

Journals serve several purposes. Forum posts count their replies on the parent (`DatabaseEngine.java:1524-1535`). Mail is written into a mailbox journal (`DBWriteJournalEmail`, `DatabaseEngine.java:1476-1489`). Private documents such as bank records are journal entries addressed to a player (`DatabaseEngine.java:1834-1840`). Forum paging returns the timestamp at the top of each page (`DatabaseEngine.java:1574-1584`). Daily statistics are one row per day, keyed by the day's start time (`DatabaseEngine.java:2673-2687`).

### Mechanisms worth knowing

- Channel backlog (`BackLogLoader.java:49-127`, `192-237`). A header row (CMINDX=0) keeps the channel's highest index in its CMDATE column. `getCounter` loads it on first use and rescans for higher indexes (`BackLogLoader.java:64-82`). `delBackLogEntry` renumbers every later row with one UPDATE each (`BackLogLoader.java:262-281`).
- Clan vault (`ClanLoader.java:82-107`, `246`). Each stored item keeps its room as a leading `<ROOM ID=... EXPIRE=... />` tag in its text. At boot the item goes back into that room, is docked if it is boardable, or is placed in space from `SPACE.` coordinates.
- Generated-race expiry (`DatabaseEngine.java:2495-2538`). Each generic race has a creation timestamp. `registerRaceUsed` refreshes it and `pruneOldRaces` deletes races past expiry.
- Character expiry (`DatabaseEngine.java:824-834`) and leaderboard scans throttled by a CPU duty-cycle percentage (`DatabaseEngine.java:776-804`) are contracts in the interface. Their implementations are outside this bucket.

### Schema migration

DDLValidator (part 2 of this bucket) builds change lists. `DDLGenerator.generateSQLForChanges` (`DDLGenerator.java:1478-1879`) turns them into SQL for the connected product, dispatching on product name (`DDLGenerator.java:1676-1694`, `1738-1756`). A column type change uses a temporary column: add it, copy the data (truncating a VARCHAR when it shrinks), fill NULLs if the column becomes NOT NULL, drop the old column, then rename (`DDLGenerator.java:256-417`, PostgreSQL). A MOVE ROWS change emits a SELECT, an INSERT with `?` placeholders and a DELETE. DDLValidator recognises the SELECT and INSERT pair and binds the rows itself (`DDLValidator.java:1105-1107`).

## Content worth mining

Nothing in this bucket is game content. Two reference items are useful.

- The table catalogue: `coffeemud.ini:2307-2317` and `guides/database/coffeemuddb.sql`. The channel-backlog table (`coffeemuddb.sql:390-402`) is a compact example: name, index, sub-name, timestamp, text payload, and a primary key on (name, index).
- Poll records (`DatabaseEngine.java:2768-2835`): one row per poll holds the option list and every vote as XML, with a zapper mask that says who may vote. The same mask language appears elsewhere in the game, so the shape transfers directly to in-world votes.

## Notable design ideas

- One owner/section/key store (`PAData`) gives every feature persistent per-player and per-area data without a schema change. Deleting a player's data is one call by owner.
- Thin records (`RoomContent`, `DatabaseEngine.java:3371-3407`) let area-wide counts and searches run without building game objects.
- Mail, private documents and forum posts share the journal store and differ mainly in their `to` field.
- Player housing is handled by routing writes to player data (`DBInterface.java:582-626`), not by a separate subsystem.
- The backlog counter repairs itself from the table on first use (`BackLogLoader.java:64-82`).

## Problems and cruft

Verified by reading only; nothing was executed.

- **Ping interval overflows.** `dbPingIntMins * (60 * 1000)` is int arithmetic (`DBConnector.java:495`). A setting of zero or less becomes `Integer.MAX_VALUE` (`DBConnector.java:146-147`), which multiplies to -60000 ms. The idle test at `DBConnections.java:313` is then always true, so every idle connection is pinged on each health check. Any setting above 35,791 minutes overflows the same way. The shipped value is 60 (`coffeemud.ini:149`).
- **Lossy or missing escaping.** Apostrophes in area keys become backticks (`ADataLoader.java`). `ClanLoader` writes the clan premise, recall, donation, morgue and item names straight into SQL (`ClanLoader.java:185-193`, `203`, `280-289`). An apostrophe in those fields breaks the statement, and a crafted value could change it. Channel names are concatenated unescaped too (`BackLogLoader.java:64`, `88`, `202`).
- **Lost writes.** Text-carrying writes are never queued for retry (`DBConnection.java:605-606`), so a transient failure silently drops room, player and clan saves.
- **Connection leak.** `searchBackLogEntries` fetches a connection and then overwrites the variable with a second one, so the first is never released (`BackLogLoader.java:365`, `370`, `383`). Leaked connections stay "in use" until `errorStatus()` releases everything.
- **Double release.** `updateBackLogEntry` calls `DBDone` in its catch and again in `finally` (`BackLogLoader.java:179`, `188`). If another thread takes the connection in between, it is marked free while still in use.
- **Status call with side effects.** `errorStatus()` calls `DBDone` on every connection once the pool is flagged (`DBConnections.java:1090-1094`). That closes statements other threads are using (`DBConnection.java:443-450`) and clears their in-use flags. Save ticks call it.
- **Unbounded waits and busy retries.** A full pool makes callers sleep and retry with no timeout (`DBConnections.java:455-501`). When the pool is below its maximum and creation keeps failing, there is no sleep at all, so the loop spins and logs (`DBConnections.java:379-406`). `ADataLoader.DBDelete` polls up to ten times with 250 ms sleeps inside the caller's thread (`ADataLoader.java:546-558`).
- **Backlog defects.** `DBInterface.addBackLogEntry` drops its timestamp argument (`DBInterface.java:1699-1702`), and the loader writes `System.currentTimeMillis()` instead (`BackLogLoader.java:202`). Trimming to zero deletes the header row but leaves the in-memory counter (`BackLogLoader.java:462`; the cache is cleared only at `301`). If the first counter read fails, `getCounter` dereferences null (`BackLogLoader.java:100-126`), and its callers sit outside any try block (`BackLogLoader.java:197`).
- **Renumbering depends on row order.** The renumbering SELECT has no ORDER BY (`BackLogLoader.java:262`). Applied out of order, an intermediate state can collide with the primary key (CMNAME, CMINDX).
- **Latent problems.** After the not-ready branch, `query()` and `update()` fall through to `clearErrors()` (`DBConnection.java:514-523`, `616-621`), so an idle connection can clear the pool's failure flags. `fixIdentifiers` wraps identifiers that the SQL already quotes a second time (`DBConnections.java:1108-1167`). `DBRawQuery` ends its column loop by catching the exception from an out-of-range index and swallows every other exception (`DBInterface.java:1776-1787`).
- **Dead code and duplication.** The space branch in `ClanLoader.getDBItemUpdateStrings` can never run, because only items whose owner is a Room are collected (`ClanLoader.java:221-223`, `243`). `DBCopyConversions` has a commented-out block (`137-169`). `DDLGenerator` has six near-identical MODIFY COLUMN builders, for PostgreSQL, HSQLDB, DB2, Derby, SQL Server and Oracle, about 950 lines in all (`DDLGenerator.java:256-1285`), and two unused locals (`1492`, `1737`). The NOT NULL fill sets `''` for every column type (`DDLGenerator.java:375`, `551`, `726`, `905`, `1243`). On PostgreSQL that literal is not a valid integer, so it would fail on numeric columns (reading only).
- **Naming mismatch.** `coffeemud.ini:2306` documents the command table as DBCOMMAND. The enum constant and the privacy test use DBCOMMANDS (`DatabaseEngine.java:91`, `DBInterface.java:145-146`).
- **Legacy default driver.** `DBConnection` defaults to `sun.jdbc.odbc.JdbcOdbcDriver` (`DBConnection.java:116-117`). That bridge was removed in Java 8. The shipped configuration uses the bundled fakedb driver instead (`coffeemud.ini:142`).

## Takeaways for a Python SUD

- Use the standard `sqlite3` module with one connection, parameterised queries everywhere, and a transaction around each player action or tick batch. A single-player process needs no pool, no pings, no health flags, no file-based error queue and no multi-database routing. `?` placeholders replace `injectionClean` and remove the apostrophe corruption.
- Collapse the 240-method surface into a handful of repositories: characters, world, items, journals, clans and a generic record store. The generic store maps onto one table with JSON payloads:

  ```sql
  CREATE TABLE records (owner TEXT, section TEXT, key TEXT, payload TEXT,
                        PRIMARY KEY (owner, section, key));
  INSERT INTO records VALUES (?, ?, ?, ?)
    ON CONFLICT (owner, section, key) DO UPDATE SET payload = excluded.payload;
  ```

  The upsert replaces `DBReCreate`'s check-then-act.
- Backlogs and journals become rows with an autoincrement id. Read the newest N with `ORDER BY id DESC LIMIT ?` and page by id. That removes the header-row counter and the renumbering entirely.
- Generated content: a `definitions(kind, id, base, data)` table, where `base` names the Python class to build from, as `AckRecord` does. Keep hand-written content in files, not in the database.
- Save granularity: track which entities a tick changed and rewrite only those. Avoid the whole-room delete-and-reinsert pattern.
- Migrations: `PRAGMA user_version` with an ordered list of SQL steps. SQLite cannot retype or drop columns in place, so use the create-copy-rename pattern from `DDLGenerator` only for the few changes that need it. Skip the six-dialect generator.
- Multiplayer assumptions to drop: account and password tables, email lookups, character expiry, cross-player pride scans, clan-channel sub-name hashing, and the ping and error-queue machinery. Keep journals only if the design needs in-game notes or mail.
- Time: CoffeeMud orders backlog and journal entries by wall-clock milliseconds. A SUD with a game clock should store game time, so saved state can be replayed and tested deterministically.

## Coverage

- Read in full (all 10 manifest files): `DatabaseEngine.java` (3409 lines), `DDLGenerator.java` (1880), `DBInterface.java` (1822), `DBConnections.java` (1168), `DBConnection.java` (811), `ADataLoader.java` (733), `BackLogLoader.java` (628), `DBConnector.java` (560), `ClanLoader.java` (314), `DBCopyConversions.java` (171).
- Consulted for cross-references only: `DDLValidator.java` (lines 306-330, 780-800, 1020-1050, 1095-1112, 1183-1200, plus a grep), `SVector.java` (iterator type), `ThreadEngine.java:65-74`, `StdLibrary.java:155-178`, `CMMap.java:2462-2480`, `CMPlayers.java:3512-3530`, `Commands/Modify.java:3068-3080`, `CMProps.java:681` and `2634-2643`, `coffeemud.ini:100-150` and `2280-2317`, `guides/database/coffeemuddb.sql:388-402`.
- Skimmed: none. Skipped: none.
