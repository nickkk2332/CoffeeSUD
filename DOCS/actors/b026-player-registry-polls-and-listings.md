# Player registry, polls and listings

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b026` (Player registry, polls and listings) · 8 files · 7429 lines of Java.

Citations are relative to the CoffeeMud folder. After this note the prefix `com/planet_ink/coffee_mud/` is dropped, so `Libraries/CMPlayers.java:3512` means `com/planet_ink/coffee_mud/Libraries/CMPlayers.java:3512`. Paths outside that package are written in full.

## Summary

This bucket is the bookkeeping layer under CoffeeMud's player characters, accounts and listings. `CMPlayers` caches online characters and accounts, reads lightweight "thin" views of offline characters for lists, saves players from a background tick, runs the INI-driven autopurge with warning emails, and keeps seven period-bucketed pride stats with fixed-size top-N charts. `Polls` and `DefaultPoll` implement a small voting system: options, a qualifier mask, an optional expiry and six bit flags, stored as one row per poll and driven by the `POLL`, `CREATE POLL`, `MODIFY POLL`, `DESTROY POLL` and `LIST POLLS` commands. `CMLister` turns entry lists into width-aware column tables, groups identical items with counts and summarises the overflow beyond `MAXITEMSHOWN`. For a single-player SUD the reusable parts are the thin-view listing pattern, period-bucketed stats with top-N snapshots, the poll-with-predicate model and the list summariser; accounts, IP-based voting, cross-host loading, purge warnings and human leaderboards are multiplayer machinery to drop.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Libraries/CMPlayers.java` | `PlayerLibrary` implementation: character and account caches, load, save, delete, rename, merge, thin views, name lookup, autopurge, pride-stat charts, background tick | 3640 |
| `Libraries/interfaces/PlayerLibrary.java` | Interface, plus nested enums `PlayerSortCode`, `PrideCat`, `PlayerCode` and interfaces `ThinPlayer`, `ThinnerPlayer` | 1244 |
| `Libraries/CMLister.java` | `ListingLibrary` implementation: column tables, `lister()` item lists with counts and overflow summary, wiki list | 683 |
| `Libraries/interfaces/ListingLibrary.java` | Interface and the `ListStringer` callback | 492 |
| `Libraries/Polls.java` | `PollManager` implementation: poll cache, vote UI, edit wizard, tally, persistence calls | 482 |
| `Common/DefaultPoll.java` | `Poll` implementation: fields, eligibility, results visibility, XML serialisation | 331 |
| `Common/interfaces/Poll.java` | `Poll` interface, nested `PollOption` and `PollResult`, flag constants | 321 |
| `Libraries/interfaces/PollManager.java` | `PollManager` interface (cache, vote, edit and persistence methods) | 236 |

Outside this bucket, cited for interactions: `Commands/PollCmd.java` (POLL), `Commands/ListCmd.java` (LIST USERS, ACCOUNTS, POLLS), `Commands/Top.java` (TOP), `Commands/Create.java`, `Commands/Modify.java` and `Commands/Destroy.java` (poll editing), `core/database/PollLoader.java` and `core/database/DBInterface.java` (the CMPOLL table), `Common/interfaces/PrideStats.java`, `Common/interfaces/TimeClock.java`, `core/CMProps.java` and `coffeemud.ini`.

## How it works

### 1. Player registry (CMPlayers)

#### 1.1 Data model

`CMPlayers` holds these fields (`Libraries/CMPlayers.java:57-81`):

- `playersList` (`SVector<MOB>`): characters held in memory. Everything else is read from the database on demand.
- `accountsList` and `allAccountsLoaded`: the account cache. When `accounts()` (2269-2332) reads the full account table with caching on, it sets `allAccountsLoaded` and later calls use memory.
- `playerLocations` (`CrossRefTreeMap<MOB,Room>`): a two-way MOB/room index, so `getPlayersHere(room)` (242-245) does not scan.
- `playerLibList`: the other `PlayerLibrary` instances on the same world map, found by `getOtherPlayerLibAllHosts()` (697-714).
- Pride-stat charts: `topPlayers`, `topAccounts`, `prevTopPlayers`, `prevTopAccounts`, indexed `[TimePeriod][PrideStat]` (66-74), and per-category maps `topPlayerCats` and `prevTopPlayerCats` (78-81).

Thin views (`Libraries/interfaces/PlayerLibrary.java:1068-1154`) expose a character as name, charClass, race, level, age (minutes played), last (login time in millis), email, ip, exp, expLvl, liege, worship, gender and clans. Three sources build one:

- a database row, `CMLib.database().getThinUser()` (`CMPlayers.java:358`) or `getExtendedUserList()` (2055, and `Commands/ListCmd.java:2125`), for offline characters;
- a live MOB, `getThinPlayer(MOB)` (457-557);
- a stat bag, `makeThinModifiablePlayer()` (362-455), which reads stat names such as `"LAST"`, `"IP"` and `"EXPERIENCENEEDED"`. Its `AGE` read carries the comment `// ? doesn't exist?` (401).

`getEmptyThinPlayerObject()` (261-350) returns an all-empty placeholder. The write-side builder `ThinnerPlayer` (`PlayerLibrary.java:1165-1243`) is a fluent setter object for name, password, account name, email, expiration and loaded MOB. Its implementation (`CMPlayers.java:2154-2244`) is a plain object.

Accounts are `PlayerAccount` objects, deduplicated by name in `addAccount()` (649-661). Names are normalised with `CMStrings.capitalizeAndLower` in `getAccount()` (677), `getPlayer()` (748), `getLoadPlayer()` (841) and `isLoadedPlayer()` (929), so lookups are case-insensitive by construction.

#### 1.2 Loading and saving

`getLoadPlayer(name)` (`CMPlayers.java:832-861`):

1. Returns the cached MOB if one exists (836-838).
2. Refuses unless `CMProps.HostState.RUNNING` (834-835).
3. If `playerExists()`, reads the character with `DBReadPlayer` and its followers with `DBReadFollowers` (841-842), sets `lastUpdated` (844), recovers phy and char stats (845-846), updates the race creation date when the race is generic (847-849), calls `autoInvocation(M,false)` on each ability (851-855), then recovers max state and resets to max (857-858).

`savePlayer(mob, lib, noCachePlayers, forceSave)` (1746-1828) has three paths:

- A character from another MUD (`IMudTransferData` with `isLocal` false) is not written to this database. If the session negotiated MPCP, a player update is sent (1756-1763).
- An online, savable character has its factions updated (1767), then its character row, items, abilities, followers, account and session ping are written, each by its own database call (1769-1787).
- An offline, savable character is written only if it has never been saved, was last saved before its last login (`lastUpdated < lastDateTime`), or the save is forced (1792-1797). When `noCachePlayers` is set (the `PLAYERSNOCACHE` setting, which comes from the `NOCACHE` list at `coffeemud.ini:2118-2127`), an offline character that is not in the game is evicted from memory after the save (1812-1819).

`savePlayers()` (1830-1839) saves every entry in `playersList`. The tick calls it (3547-3548) unless the save or player thread is disabled (3525-3527).

#### 1.3 Name lookup

- `getPlayer()` (746-755) matches the cached name exactly. `getPlayerAllHosts()` (758-770) then asks the other hosts.
- `getLoadPlayerAllHosts()` (792-829) checks the local cache, then each other host's cache, then each other host's database. If a host's database has the name, the load runs on that host's thread (813-822).
- `findPlayerOnline(srch, exactOnly)` (`CMPlayers.java:2121-2152`) searches online sessions only. An exact full-name match returns at once. Otherwise three slots are filled and the first non-null is returned, in this order: exact short name `name()`, substring of `Name()`, substring of `name()` (2131-2150). Each slot keeps the last match in iteration order, not the first.
- `getLoadPlayerByEmail()` (560-575) scans cached characters, then thin views, comparing email case-insensitively.
- `getLiegeOfUserAllHosts()` (864-894) and `playerExistsAllHosts()` (953-976) query the other hosts' databases.

The multi-host design is visible here. `getOtherPlayerLibAllHosts()` (697-714) collects `PlayerLibrary` instances whose `MAP` library is the same object, so several server threads, each with its own database engine (`CMLib.get(threadId)._database()`, 807), share one world. `activate()` names the tick thread `THPlayers` plus one character taken from the thread-group name (3477), so each host also writes its own pride-record file.

#### 1.4 Renaming, merging and deletion

`renamePlayer(mob, oldName)` (1251-1330) runs after the editor changes a character's name (`Libraries/CMGenEditor.java:11857`). In order it:

- renames the database row (1254);
- updates other cached characters whose worship or liege points at the old name (1257-1262);
- re-declares quest winners, removing the old name and adding the new (1263-1271);
- replaces the name in the account roster (1274-1282);
- changes the owner of `PrivateProperty` items the character carries (1283-1291);
- walks every room: a `LandTitle` owned by the old name is re-owned, and any room effect whose misc text equals the old name is rewritten (1294-1329).

`mergePlayer(mergeM, keepM)` (182-240) copies the first character's stats, experience, practices, trains, quest points, age, wimp threshold, attribute bits, abilities, expertises, tattoos and items into the second, then destroys the first. A TODO at 184 reads "merge more stuff from mergeM into keepM here". Its callers are the inter-MUD protocol code (`Libraries/CMProtocols.java:165-206`, `458`, `741`, found by grep).

`obliteratePlayer(deadMOB, deleteAssets, quiet)` (1550-1689) runs these steps in order:

1. Drops the cache entry and the journal subscriptions (1552-1559).
2. Removes the character from its account (1565-1570).
3. If `deleteAssets`, sends `MSG_RETIRE` to the character's room and then to every other room. A room whose `okMessage` returns false stops the deletion at this point (1586-1609). The retire message is "A horrible death cry is heard throughout the land." (1572), and `quiet` suppresses it.
4. Logs the session out (1579-1585). This happens before the veto check at step 3, so a vetoed deletion leaves a live character with no session.
5. Removes the name from the no-purge list (1616), deletes the external item store (1617-1619), posts "@x1 has just been deleted." to the `PLAYERPURGES` channels with the account name in parentheses when there is one (1620-1632), and bumps `CoffeeTableRow.STAT_PURGES` (1633).
6. Deletes the MOB row (1635), archives its pride records (1636), moves items out and saves followers, abilities and items (1637-1660).
7. If `deleteAssets`, deletes private journal entries and all player data (1661-1665).
8. Updates the account (1666-1676), removes the character as a quest winner (1677-1685), and destroys the MOB (1688).

#### 1.5 Autopurge and warnings

Configuration:

- `AUTOPURGE` (`coffeemud.ini:1250`) is a comma list of level conditions, each with two numbers: days until purge and days before purge to send a warning. The shipped example is `<2 10 0, <3 15 1, <10 30 2, <90 90 7, >=90 0 0` (line 1249). The shipped value `>=0 0 0` purges nothing (1247-1250).
- `ACCOUNTPURGE` (`coffeemud.ini:1258`) is the number of days an account with no characters is kept. 0 disables it.
- `PURGEACCOUNTS` and `AUTOPURGE` in the disable list turn the feature off (`coffeemud.ini:2022-2024`).

Parsing and timing:

- `getPurgeDays()` (2334-2375) compiles the list into per-level arrays with `CMLib.utensils().compileConditionalRange` (2341). It rebuilds only when the string's hash changes (2337).
- A purge count of zero is skipped and leaves the level at zero, which means never purged (2357-2358, 2395-2397).
- The warning point is the purge point minus the warning days (2360-2361, 2370).
- `getPurgeWarnTimes(lastLogin, level)` (2377-2411) returns the purge and warning times for a level, or null for "never". Levels past the table use the last entry (2380-2391).

`autoPurge()` (2493-2628) runs from the tick:

1. Reads the warned list from `warnedplayers.ini` and drops entries older than 10 days (2499-2531, with the age test at 2517-2518). Each line holds a name, an email address and a timestamp.
2. For each user row from the database (2533): once past the warning point with no live warning, the character is loaded and a warning entry is recorded and sent (2560-2575). Loading the character is how the warning is addressed.
3. Once past the purge point, if the warning is more than a day old, the character is not a sysop, is not on the no-purge list, and its account lacks `NOCHARPURGE`, the character is obliterated with `deleteAssets` and `quiet` (2578-2593).
4. Accounts with no characters are removed once the latest of last login, last update and expiration, plus `ACCOUNTPURGE` days, has passed (2604-2626).

`warnPrePurge()` (2630-2684) sends the warning through `CMLib.smtp().emailOrJournal` (2683) with the subject "<MUD name> Autopurge Warning: <name>" (2666). The body says the character will be purged in N days when more than two days remain, otherwise in N hours, and asks the player to log in to keep it (2670-2682). Nothing is sent when the SMTP client is disabled (2656-2661).

No-purge protection: `noPurge(name)` (2470-2491) takes `+name` to add, `-name` to remove, or a bare name to test. The list lives in `protectedplayers.ini` as a case-insensitive sorted set (2413-2467). Accounts share the same list.

#### 1.6 Pride stats and top charts

- Stats (`Common/interfaces/PrideStats.java:51-60`): `PVPKILLS`, `AREAS_EXPLORED`, `ROOMS_EXPLORED`, `EXPERIENCE_GAINED`, `MINUTES_ON`, `QUESTS_COMPLETED`, `QUESTPOINTS_EARNED`.
- Periods (`Common/interfaces/TimeClock.java:736-744`): `HOUR`, `DAY`, `WEEK`, `MONTH`, `SEASON` (365/4 days, so 91), `YEAR`, `ALLTIME`. `nextPeriod()` (788-827) rounds a `Calendar` forward to the next boundary; `ALLTIME` returns `Long.MAX_VALUE` (824).
- Bump: `bumpPrideStat(mob, stat, amt)` (1074-1136) hands off to the library that owns the session (1079-1084). It skips the character's stats when `NOSTATS` is set, and updates the character chart unless `NOTOP` is set (1085-1091). For each `PRIDECATS` category it computes a bucket with `getPrideCatValue()` (2750-2786) and updates that bucket's chart (1093-1122). Account stats follow the same rule with the account flags (1124-1131).
- Chart update: `adjustTopPrideStats()` (1164-1208). For each period, a zero or negative value removes the name (1176-1177). A positive value replaces the existing entry, or is inserted in descending order and the chart is trimmed to `PRIDECOUNT` (1181-1202). The default is 10 (`coffeemud.ini:832-835`).
- Categories: `PRIDECATS` (`coffeemud.ini:825-830`) accepts `CLASS`, `RACE`, `BASECLASS`, `RACECAT`, `LEVEL`, `LEVEL-X`, `GENDER` and `CLAN`. The `PrideCat` enum (`PlayerLibrary.java:872-883`) also has `ACCOUNT` and `PLAYER`, which are placeholders. `LEVEL-X` buckets a level as `floor(level/X)+1` (`CMPlayers.java:2769-2771`). Parsing is in `propertiesLoaded()` (104-148).
- Rollover: when a period's expiry passes, the tick copies each current chart into its previous-period slot and clears it (3557-3577). It does the same for category charts (3578-3611), then writes `resources/sys_reports/<name>_prev_top_records.xml` (3614-3615, 1468-1497). The next boundary for each period is kept in `topPrideExpiration` (2945-2946, 3563-3566).
- Departed characters: `preservePrideRecords()` (1499-1547) writes each chart entry of a deleted character as an archive row named `<name> <year>/<day-of-year>` under `SYSTEM_PRIDE_PARCHIVE`, or `SYSTEM_PRIDE_AARCHIVE` for accounts (1519-1533, 1525-1528). The embedded space marks these rows, and `deleteFakePrideStatPermanently()` (1138-1151) deletes them when they drop off a chart.
- Boot: `activate()` (3470-3483) starts the tick, then calls `resetAllPrideStats()` (2841-2949), which rebuilds every chart from database scans on a worker thread, and `loadPreviousPrideRecords()` (1412-1466). `parsePrideStats()` (2087-2118) discards stored counts whose next-boundary time has passed (2099-2100).

#### 1.7 Background tick

`tick()` (`CMPlayers.java:3512-3626`) runs unless the player thread is disabled (3515-3516). It is scheduled with `MudHost.TIME_SAVETHREAD_SLEEP`, which is 60 minutes (`core/interfaces/MudHost.java:46`; the scheduler call is at `CMPlayers.java:3478`, and the meaning of its final argument was not traced). Each pass does the following:

1. For each savable, in-game character: `awards().evaluateAutoTitles`, `achievements().evaluatePlayerAchievements`, `evaluateAccountAchievements`, and `selectRandomTitle` (3529-3543). Random titles are picked with `dice().roll(1,n,-1)` (3497).
2. `autoPurge()` (3545) and `savePlayers()` (3547-3548), both inside the save-thread check (3525-3527).
3. The pride-period rollover, then `savePreviousPrideRecords()` if anything reset (3551-3615).

`shutdown()` (3628-3639) clears the lists and removes the tick.

### 2. Polls (Poll, DefaultPoll, Polls, PollCmd)

#### 2.1 Data model

`DefaultPoll` fields (`Common/DefaultPoll.java:88-97`):

- `name`: the key, default `"POLL"`.
- `subject`: the results header, default `"Poll Results Title"`.
- `description`: the introduction, default `"This is a Poll! Choose from the following:"`.
- `author`: default `"no one"`.
- `expiration`: millis since 1970, 0 for none.
- `bitmap`: the flags.
- `qualZapper`: a zapper mask (the same language as the rest of the engine, see `Poll.java:120-134`).
- `options`: a list of `PollOption`, each with `text` (`Poll.java:265-277`).
- `results`: a list of `PollResult`, each with `user` (character name), `ip` (session address, followed by a tab and the account name when one exists) and `answer` (`Poll.java:285-307`). `answer` is a 1-based option index as a string. `"-1"` means abstain, or no valid choice (`Polls.java:209-214`).
- `loaded`: whether options and results are in memory.

Database row `CMPOLL` (`core/database/PollLoader.java:53-61`, insert at 281-299): `CMNAME`, `CMBYNM` (author), `CMSUBJ`, `CMDESC`, `CMOPTN` (options XML), `CMFLAG`, `CMQUAL`, `CMRESL` (results XML) and `CMEXPI`. The XML comes from `getOptionsXML()` (`DefaultPoll.java:220-235`) and `getResultsXML()` (238-255), giving `<OPTIONS><OPTION><TEXT>...</TEXT></OPTION></OPTIONS>` and `<RESULTS><RESULT><USER>...</USER><IP>...</IP><ANS>...</ANS></RESULT></RESULTS>`.

#### 2.2 Flags

| Flag | Value | Effect (code location) |
|---|---|---|
| `FLAG_ACTIVE` | 1 | Poll accepts votes (`Poll.java:309-310`; checked at `DefaultPoll.java:299`) |
| `FLAG_PREVIEWRESULTS` | 2 | Results shown to everyone before voting and before expiry (`DefaultPoll.java:323-324`) |
| `FLAG_ABSTAIN` | 4 | Abstain option offered (`Polls.java:200-201`, `209`); also lets non-voters see results (`DefaultPoll.java:327-328`) |
| `FLAG_VOTEBYIP` | 8 | One vote per session address or account (`DefaultPoll.java:270-280`; `Polls.java:215-220`) |
| `FLAG_HIDERESULTS` | 16 | Results hidden except from staff with `SecFlag.POLLS` (`DefaultPoll.java:321`) |
| `FLAG_NOTATLOGIN` | 32 | Not offered at login, only through the POLL command (`Polls.java:135`; `PollCmd.java:54-87`) |

#### 2.3 Eligibility and visibility

`mayIVote(mob)` (`DefaultPoll.java:295-312`) requires, in order: the poll is ACTIVE (297-298); the qualifier mask passes (`MaskingLibrary.maskCheck`, 301); the poll has not expired, and if it has, the ACTIVE bit is cleared and the poll is saved on the spot (303-307); and the mob has no existing vote (309).

`getMyVote(mob)` (257-284) matches a result when its `user` equals the character's name, or, for IP-recorded results, when its address equals the session address, starts with that address followed by a tab, or ends with the tab and the account name (270-280). With `FLAG_VOTEBYIP` set, a second account on the same address is therefore blocked.

`mayISeeResults(mob)` (314-330) checks in order: the qualifier mask (317-319); `HIDERESULTS` unless the viewer has `SecFlag.POLLS` (321); `PREVIEWRESULTS`, which returns true (323-324); an expiry that has not been reached, which returns false (325-326); and a viewer who has not voted when `ABSTAIN` is clear, which returns false (327-328). Otherwise true.

#### 2.4 Commands and the editor

- `CREATE POLL` (`Commands/Create.java:2300-2312`) requires `SecFlag.POLLS`. It makes a `DefaultPoll` named `POLL`, appending `!` until the name is free (2306-2307), sets ACTIVE, writes the row at once (`createPoll()`, `Polls.java:379-391`), then runs the editor (2310). On success the room shows "The world has grown more uncertain." (2311).
- `MODIFY POLL` (`Commands/Modify.java:2999-3010`) takes a number from `LIST POLLS` or a name, and runs `modifyVote()`.
- `DESTROY POLL` (`Commands/Destroy.java:2231-2252`) asks for confirmation, then `deletePoll()`, which removes the poll from the cache and deletes its row. Messages: "POLL '<name>' not found. Try LIST POLLS." (2244) and "The world has grown a bit more certain." (2252).
- `LIST POLLS` (`Commands/ListCmd.java:5114-5136`) prints numbered names, marked "(inactive)" when ACTIVE is clear, or "(expires: <date>)" when an expiry is set. With no polls it says "No polls available. Fix that by entering CREATE POLL!" (5118).

`modifyVote()` (`Polls.java:233-325`) walks the fields in this order, each through `CMLib.genEd().prompt`. When `EDITORTYPE` is set, every field is shown at once (239-240, 307-311):

1. Name, with duplicates getting `!` appended (245-248).
2. Introduction (249).
3. Results Header, cut to 250 characters (250-252).
4. Qual. Mask (255).
5. Poll Active, Preview Results, Allow Abstention, Use IP Addresses/Accounts, Hide Results, POLL CMD only (256-267).
6. Exp. Date in `MM/DD/YYYY HH:MM AP` form, or `NA` to clear (268-284). An unparseable date is silently ignored (see Problems).
7. Each existing option, where a blank answer deletes it (286-293).
8. New options until a blank line (297-305).

Then the editor loops on "Edit which?" to reopen a numbered field (317-318). The author is set to the editor only when the author is empty (253-254). The poll is saved with `updatePoll(oldName, P)` (324).

#### 2.5 Voting flow and messages

`PollCmd.execute()` (`Commands/PollCmd.java:49-113`) calls `getMyPollTypes(mob, commands==null)` (54). `Polls.getMyPollTypes()` (124-146) sorts polls into three lists:

- `[0]`: votable now;
- `[1]`: votable, but NOTATLOGIN, and only while logging in;
- `[2]`: results visible, but no vote possible.

With `commands == null` (the form used at login; the caller was not traced), PollCmd offers votable polls at once, then reports how many are waiting and how many have results still open (81-87). With a command, it runs each `[0]` poll and shows its results if visible, then shows `[2]` under "Previous polling results" (89-111). The `[1]` loop at 89-97 can never run (see Problems).

`processVote()` (`Polls.java:179-230`):

- shows the introduction and numbered options as "N: text" (188-200);
- adds "Press ENTER to abstain from voting." when ABSTAIN is set (201);
- prompts "Please make your selection (1-N): " until a number in range is typed, or ENTER if ABSTAIN is set (204-213);
- builds the `PollResult` (214), adds the session address and account name when VOTEBYIP is set (215-220), and calls `addVoteResult()` (221).

`addVoteResult()` (`DefaultPoll.java:287-292`) appends the result and then `updatePollResults()` rewrites the whole results XML (`Polls.java:393-397`).

`processResults()` (`Polls.java:328-376`) prints the subject, then each option as "N: text (Votes: v - p%)" with percentages rounded against valid votes (360-367). With ABSTAIN set, an "Abstentions" line follows (368-374). A result counts when its answer is 1..N, or, with ABSTAIN set, when it is 0 or less (349-357).

Other messages: "No polls are available at this time." (`PollCmd.java:60`); "N poll(s) are awaiting your participation." (67); "Results from N poll(s) are still available." (86); "Oops! No options defined!" (`Polls.java:191`); "Press ENTER to continue:" after results (`PollCmd.java:78`).

#### 2.6 Persistence and cache

- `getCache()` (`Polls.java:71-88`) loads every poll on first use, unless the `POLLCACHE` disable flag is set (73; the flag is listed in the INI at `coffeemud.ini:2065`).
- `getPoll(String)` (90-106) matches cached names case-insensitively (97). Without a cache it loads one poll by exact name (102), which is an SQL match and may differ in case handling.
- `getPollList()` (148-176) always runs a full `SELECT * FROM CMPOLL` (`Polls.java:159`, through `DBInterface.java:1615-1617` to `PollLoader.java:143`), then reuses cached objects where they exist.
- `loadPollIfNecessary()` (421-471) reads one row (`PollLoader.java:50`) and parses options and results from XML with `CMLib.xml().parseAllXML` (`Polls.java:436-467`).
- `deletePoll()` removes from the cache and then deletes the row (414-419; `PollLoader.java:262-268`).

### 3. Listings (CMLister, ListingLibrary)

#### 3.1 Column tables

Column widths are authored for a 78-column design and scaled to the viewer's wrap width. `fixColWidth(w, session)` returns `round(w/78 × wrap)`, and uses 78 when there is no session or the wrap is 0 (`Libraries/CMLister.java:620-649`). The widths used are 24 for three-column tables of objects (`build3ColTable`, 432-466), 37 for two columns (`build2ColTable`, 560-594), and 72 divided by N for N-column string tables (`buildNColTable`, 651-682).

The string forms (`build2ColTable`, `build3ColTable`, `build4ColTable`, `buildNColTable`, 597-682) take lists of strings. The object forms (384-514) take a `Map`, `Vector` or `Enumeration` plus a `Filterer[]` and a `ListStringer`. `ListStringer` (`Libraries/interfaces/ListingLibrary.java:481-491`) converts an object to text. The default (`CMLister.java:47-66`) prints a string as is, prints an Ability, CharClass or Race ID with `*` when generic, and otherwise prints the class ID. `LikeRoomFilter` keeps only rooms in the same area (68-86).

#### 3.2 lister(): item lists

`lister(viewer, items, useName, tag, tagParm, longLook, compress)` (`CMLister.java:260-370`) builds the lists used for room contents and inventory (`Libraries/CommonMsgs.java:1677`, `1690`, `2016`, `2024`; `Commands/Inventory.java:174`). It works as follows:

1. Each item is compared with the items after it by `itemSeenString()` text and `CMLib.flags().isSeenTheSameWay()`. Equal items are removed and counted (`getReps`, 172-202; 289). The first is printed with its count as `(n)` (`appendReps`, 204-220).
2. Each line's text comes from `itemSeenString()` (127-170). With `useName`, the name is used. With `longLook` and a container, the name is indented. If the item's short name differs from its class name, the line reads "<name> is here." Otherwise the display text is used, capitalised and sentence-ended when compressed, and the name is the fallback.
3. Overflow: once `MAXITEMSHOWN` distinct items are shown (default 5, `coffeemud.ini:657-663`; check at 274-283), the remainder is replaced by `summarizeTheRest()` (222-258). That prints "There are also <types> items here.", with each item's general type from `CMLib.materials().getGeneralItemType` (233) and "other" for unnamed types. `longLook`, sysop messages and `useName` all bypass the cut-off (277-281).
4. Open top-level containers without a door list their contents, with counts and disposition blurbs (322-364).

#### 3.3 Wiki export and other consumers

`buildWikiList()` (`CMLister.java:516-552`) writes `*[[ID]]` or `*[[ID|name]]` lines for publishing engine data to a wiki (see `ListingLibrary.java:280-306`). Property abilities use the ID alone (519). Other callers: `Commands/Help.java:96` (four-column list of matching topics), `Commands/ClanPVPKills.java:111` (three-column), `Commands/Who.java:60-63` and `359-360`, and many `fixColWidth` calls in `Commands/ListCmd.java`, such as 2095-2101 for LIST USERS.

### Interactions with other systems

- Registry to database: `DBReadPlayer`, `getExtendedUserList`, `getThinUser`, the player update calls, `DBReadFollowers` and the pride-archive calls (`CMPlayers.java:841-842`, 358, 2055, 1545, 1636). The `DatabaseEngine` itself is outside this bucket.
- Registry to other libraries: awards and achievements (3536-3539), factions (1767), coffee tables (1633), quests (1263-1271, 1677-1685), journals (1559, 1733), channels (1620), SMTP (2683), MPCP (1760), sessions (1560, 2129), threads (813, 3478), world map (1294, 1590).
- Polls to other libraries: masking (`maskCheck`, `DefaultPoll.java:301, 319`), the generic editor (`genEd().prompt`, `Polls.java:245-290`), XML parsing (`Polls.java:436, 454`), time formatting and parsing (`Polls.java:270, 279`), and `CMSecurity` (`DefaultPoll.java:321`, `Create.java:2302`).
- Listings to other libraries: `flags().canBeSeenBy` and `getDispositionBlurbs` (`CMLister.java:231, 310`), `protocol().mxpImage` (299), `materials().getGeneralItemType` (233).

## Content worth mining

### Leaderboard stats and periods

| Stat | Label shown by TOP (`Commands/Top.java`) |
|---|---|
| `PVPKILLS` | PVP Kills (89) |
| `EXPERIENCE_GAINED` | XP Gained (89) |
| `QUESTS_COMPLETED` | Quests Completed (89) |
| `MINUTES_ON` | Mins Online (110) |
| `ROOMS_EXPLORED` | Rooms Explored (110) |
| `QUESTPOINTS_EARNED` | QuestPoints Earned (110) |
| `AREAS_EXPLORED` | No label in TOP (where it is shown was not traced) |

Periods: HOUR, DAY, WEEK, MONTH, SEASON (91 days), YEAR, ALLTIME (`TimeClock.java:738-744`). The legacy TOP view shows "All Time" or "This Month" (`Top.java:87`).

### Player sort codes and list columns

| Code | Alias (`PlayerLibrary.java:849-857`) | Meaning in LIST |
|---|---|---|
| NAME | CHARACTER | Character name |
| CLASS | CHARCLASS | Class |
| RACE | RACE | Race |
| LEVEL | LVL | Level |
| AGE | HOURS | Minutes played, shown in hours (`ListCmd.java:2159`) |
| LAST | DATE | Last login |
| EMAIL | EMAILADDRESS | Email (staff lists only) |
| IP | LASTIP | Last address (staff lists only) |
| REMAIN | REMAIN | Days until autopurge |

LIST USERS columns (`ListCmd.java:2095-2124`): race 8, class 10, level 4, hours 5, then email, last login or IP (23, 18, 23), then the character name (15). LIST USERS and LIST ACCOUNTS need `CMDPLAYERS` and `STAT` (`ListCmd.java:5327`, `5354`).

### Autopurge schedule as one line

`<2 10 0, <3 15 1, <10 30 2, <90 90 7, >=90 0 0` (`coffeemud.ini:1249`) reads as: below level 2, purge after 10 days and warn 0 days before; below level 3, 15 and 1; and so on. Level 90 and above is never purged.

### Message strings

These are short, and are shown as quoted in the code:

- "A horrible death cry is heard throughout the land." (`CMPlayers.java:1572`)
- "@x1 has just been deleted." (`CMPlayers.java:1627`)
- "No polls are available at this time." (`PollCmd.java:60`)
- "Please make your selection (1-N): " (`Polls.java:208`)
- "Press ENTER to abstain from voting." (`Polls.java:201`)
- "The world has grown more uncertain." and "The world has grown a bit more certain." (`Create.java:2311`, `Destroy.java:2252`)
- The autopurge warning is paraphrased here: the character will be purged in N days or hours unless the player logs in again (`CMPlayers.java:2681-2682`).

## Notable design ideas

1. **Thin views for listing without loading.** One `ThinPlayer` interface is backed by a database row for offline characters and by a live MOB for online ones (`PlayerLibrary.java:1068-1154`; `CMPlayers.java:352-359`, `457-557`, `2049-2083`).
2. **Save only what changed, and evict idle characters.** An offline character is written only when its last-update time is behind its last-login time (`CMPlayers.java:1792-1797`), and with `PLAYERSNOCACHE` it is dropped from memory after the save (1812-1819).
3. **Calendar-aligned period buckets with a previous-period snapshot.** `nextPeriod()` (`TimeClock.java:788-827`) finds boundaries, and the tick copies each chart to a previous slot before clearing it (`CMPlayers.java:3571-3577`).
4. **Ghost entries keep leaderboards honest after deletion.** A deleted character's chart entries are re-filed under `<name> <year>/<day>` (`CMPlayers.java:1519-1533`) and removed when displaced (1138-1151).
5. **Compile-once, data-driven purge schedule.** The whole schedule is one INI line (`coffeemud.ini:1249`), parsed into arrays and rebuilt only when its hash changes (`CMPlayers.java:2337`).
6. **Two-stage purge with a grace day and expiring warnings.** The warning is sent first, the purge follows more than a day later (`CMPlayers.java:2578-2580`), and warning records expire after 10 days (2517-2518).
7. **One qualifier language for everything.** Polls use the same zapper mask as other systems (`Poll.java:120-134`; `DefaultPoll.java:301`).
8. **Vote integrity by address or account.** `FLAG_VOTEBYIP` records `address<TAB>account` and blocks other accounts on the same address (`DefaultPoll.java:270-280`; `Polls.java:217-219`).
9. **Results policy as flags.** Preview, hide, expiry and abstain combine into a small visibility rule (`DefaultPoll.java:314-330`).
10. **Grouped item lists with an overflow summary.** Identical items print as `(n) name`, and anything beyond the limit is summarised by generic type (`CMLister.java:172-258`, `274-283`).
11. **Layout authored once, scaled to the screen.** Column widths are written on a 78-column design and scaled to each viewer's wrap width (`CMLister.java:620-649`).
12. **Name lookup ladder.** Online lookup tries an exact name, then substring matches on the long and short names (`CMPlayers.java:2131-2150`).

## Problems and cruft

Bugs and risks, verified by reading:

1. **Aborted votes are recorded as "-1" and use up the vote.** When the session drops or the prompt loop ends without a valid choice, `processVote()` still creates and stores a result with answer `"-1"` (`Polls.java:204-221`). Without ABSTAIN such a result is never tallied (349-357). Separately, the VOTEBYIP branch calls `mob.session().getAddress()` with no null check (217).
2. **A stored answer of 0 is tallied into the last option.** Without ABSTAIN, `votes[votes.length-1]` is incremented for a 0 answer (`Polls.java:349-355`). `processVote()` never stores 0, so this needs hand-edited data.
3. **CREATE POLL polls keep the author "no one".** The default author is non-empty (`DefaultPoll.java:92`), and the editor sets the author only when it is empty (`Polls.java:253-254`). `Create.java:2305-2310` does not set one.
4. **Bad expiry dates are ignored silently.** An unparseable expiry leaves the old value, with no message (`Polls.java:277-283`).
5. **Dead branch in PollCmd.** `PollCmd.java:89-97` can never run. List `[1]` is filled only when `login` is true (`Polls.java:135`), and this loop runs only when `commands != null`, which means `login` is false.
6. **Inverted javadoc.** `Poll.java:250-256` says `mayISeeResults` returns true when the mob "may not see" results. The code returns true when the mob may see them (`DefaultPoll.java:314-330`).
7. **A predicate writes to the database.** `mayIVote()` saves the poll when it finds it expired (`DefaultPoll.java:303-307`). `PollCmd.java:54` calls it for every poll at every login.
8. **SQL built by string concatenation.** In `PollLoader.java`, the poll name, author, subject and qualifier mask are concatenated into SQL (lines 50, 236-250, 264, 281-299). Only the description and options are bound as parameters (245-250, 289-299). A quote in any of the concatenated fields breaks the statement. Whether `DB.query` escapes its input was not verified.
9. **Full-table reads and whole-blob writes.** `getPollList()` runs `SELECT *` over CMPOLL, including results XML, on every call (`Polls.java:159`; `PollLoader.java:143`). Each vote rewrites the whole results XML (`Polls.java:291`; `PollLoader.java:254-260`). Both grow with the number of voters.
10. **Unsynchronised cache writes.** `getCache()` is `synchronized`, but `addPoll()` and `removePoll()` are not (`Polls.java:57-69`).
11. **Lexicographic sort on REMAIN.** `getThinSortValue()` returns the REMAIN value as a string (`CMPlayers.java:1980-1988`). The LIST comparator (`CMPlayers.java:2062-2077`, `ListCmd.java:2130-2147`) therefore sorts "10" before "9". Account sorting compares numeric strings numerically (2309-2311), so the two sorts disagree.
12. **Loose sort-code matches depend on hash order.** `getCharThinSortCode()` iterates a `Hashtable` and returns the first key with the prefix (`CMPlayers.java:2020-2026`, table at 84). Prefixes that match several codes, such as "LA" (LAST, LASTIP), resolve in hash order.
13. **The warned-list file is rewritten repeatedly.** The `if(warnChanged)` write sits inside the per-user loop (`CMPlayers.java:2533`, `2599-2600`, loop closes at 2601), and `warnChanged` is never reset inside the loop. Once one warning is recorded, the file is rewritten for every later user.
14. **One malformed warned-list line aborts the whole pass.** `codedWarnStr.substring(0, firstSpace)` with no space gives index -1 (`CMPlayers.java:2514`). `autoPurge()` has no catch, and `tick()` has only a try/finally (3518-3623).
15. **Unchecked database result.** `DBReadFollowers(M,false)` runs on the result of `DBReadPlayer` with no null check (`CMPlayers.java:841-842`).
16. **A room veto leaves a live character with no session.** The session is logged out at 1579-1585, before the veto check at 1586-1609. The veto path re-adds the character and account and returns, so the account and cache are restored but the session is gone. The comment at 1606 says so.
17. **Probable double destroy.** `autoPurge()` calls `M.destroy()` after `obliteratePlayer()` has already destroyed the MOB (`CMPlayers.java:2590-2591`, `1688`). This is harmless only if `destroy()` is idempotent, which was not checked.
18. **The password is readable through the generic accessor.** `getPlayerValue(PASSWORD)` returns the stored password string, or the account's password when `COMMONACCOUNTSYSTEM` is above 1 (`CMPlayers.java:3057-3069`). Writes are ignored (`3421-3423`). The content is not reproduced here.
19. **Rename rewrites by text match.** Any room effect whose misc text equals the old character name is renamed (`CMPlayers.java:1316-1321`).
20. **Uncertain stat name.** `makeThinModifiablePlayer()` reads `"AGE"`, with the comment `// ? doesn't exist?` (401).
21. **Email decision is unused.** `canReceiveRealEmail` (2644-2654) only drives a debug message. The actual choice between email and journal is left to `emailOrJournal` (2683).
22. **Dead block.** `CMPlayers.java:2362-2366` cannot run, because `set[0]` is already known to be positive at 2357-2358.
23. **Unused method.** `bumpTopPrideCat()` (1218-1248) is never called; its only call site is commented out (1119).
24. **Fixed top-10 ignores PRIDECOUNT.** `getRecordXML()` writes 10 entries (`CMPlayers.java:1373`), and TOP hard-codes 10 rows (`Top.java:100`, `120`, `156`), although `PRIDECOUNT` is configurable (`coffeemud.ini:832-835`).
25. **Mislabelled period.** The legacy TOP view labels every period other than ALLTIME "This Month" (`Top.java:87`), though HOUR, DAY and WEEK are valid.
26. **Wrong item in MXP output.** Container contents are given an MXP image of the container, `item`, not `item2` (`CMLister.java:347-348`).
27. **Latent cast failure.** `summarizeTheRest()` casts every element to `Item` (`CMLister.java:230`). `ListingLibrary.java:49-50` says mobs and exits may be passed. The cast fails only when the overflow path is reached with a non-item.

Documentation and configuration disagreements:

- `coffeemud.ini:1243-1245` says a zero warning count means the character is never warned. The code sets the warning point equal to the purge point (`CMPlayers.java:2360-2361`, `2390-2391`), so the warning goes out at purge time and the purge follows at least a day later (`2578-2580`).
- `coffeemud.ini:1257-1258` says the `ACCOUNTPURGE` default is 0. `CMProps.java:2770` falls back to 14 when the value is blank or absent (`setIntVar` default at `CMProps.java:1726-1727`). The shipped file sets 0.
- `Poll.java:313` describes ABSTAIN only as "abstain from a vote". The code also lets non-voters see results (`DefaultPoll.java:327-328`).

Performance and multiplayer assumptions:

- Each autopurge pass reads every user row (`CMPlayers.java:2498`) and loads each character it warns or purges (2563, 2582). It runs once per tick (3545).
- `getLoadPlayerByEmail()` scans every cached and stored player (560-575).
- The architecture assumes several server threads, each with its own database engine, sharing one world (697-714, 807, 813-822). The tick thread name carries a host letter (3477).
- Accounts, IP-based voting, inter-MUD player transfer (`CMProtocols.java`), cross-host loading and leaderboards among players are all multiplayer features.

## Takeaways for a Python SUD

**Registry.** Borrow the thin-summary idea: a small dataclass for each save slot (name, level, location, last played) for menus and sorting, so the full state loads only when chosen. Borrow the dirty check (save when `last_saved < last_played`). Borrow the lookup ladder (exact, then substring) for names the player types. Drop accounts, multi-host, MPCP, email warnings, no-purge and the veto-driven deletion.

The rename code (`CMPlayers.java:1251-1330`) is a checklist of what a name-keyed design must update: liege, quest winners, property owners, room effects. A Python SUD should key everything on a stable ID (`"char:0042"`), so renaming changes only a display field.

**Stats and time.** Keep period buckets, but compute boundaries from in-game time, not the wall clock. `TimeClock.nextPeriod()` uses a `Calendar`, which suits a real-time server but would drift from a turn-based game clock.

```python
from dataclasses import dataclass, field
from enum import Enum

class Period(Enum):
    DAY = "day"; WEEK = "week"; MONTH = "month"; YEAR = "year"; ALL_TIME = "all"

@dataclass
class Leaderboard:
    size: int = 10                                            # PRIDECOUNT
    rows: list[tuple[str, int]] = field(default_factory=list)  # best first

    def set_score(self, name: str, value: int) -> None:
        self.rows = [(n, v) for n, v in self.rows if n != name]
        if value > 0:
            self.rows.append((name, value))
            self.rows.sort(key=lambda row: -row[1])
            del self.rows[self.size:]
```

For each stat, keep `current[period][stat]` and `previous[period][stat]`. At a boundary, copy current to previous and clear current. A departed character's entries can be re-filed under a dead-name key, as the original does, or simply dropped.

**Polls.** A Python poll can be a dataclass with a predicate in place of the zapper mask and a game-time expiry. This is only worth keeping if factions or town councils vote in the world; otherwise drop the module.

```python
from dataclasses import dataclass, field
from enum import IntFlag
from typing import Callable

class PollFlag(IntFlag):
    ACTIVE = 1
    PREVIEW_RESULTS = 2
    ABSTAIN = 4
    HIDE_RESULTS = 16

@dataclass
class Poll:
    key: str                                  # stable id; the title is for display
    title: str
    intro: str
    options: list[str]
    flags: PollFlag = PollFlag.ACTIVE
    qualifies: Callable[[str], bool] = lambda voter: True
    closes_at: float | None = None            # in-game time
    votes: dict[str, int] = field(default_factory=dict)  # voter -> 1-based option, -1 abstain

    def can_vote(self, voter: str, now: float) -> bool:
        if not self.flags & PollFlag.ACTIVE:
            return False
        if self.closes_at is not None and now >= self.closes_at:
            return False
        return voter not in self.votes and self.qualifies(voter)

    def can_see_results(self, voter: str, now: float) -> bool:
        if not self.qualifies(voter):
            return False
        if self.flags & PollFlag.HIDE_RESULTS:
            return False
        if self.flags & PollFlag.PREVIEW_RESULTS:
            return True
        if self.closes_at is not None and now < self.closes_at:
            return False
        return voter in self.votes or bool(self.flags & PollFlag.ABSTAIN)
```

Store abstention separately from "no vote" rather than the `-1` sentinel the original uses (see Problems 1 and 2).

**Listings.** Build tables from plain strings and a width taken from the terminal. Group identical items, and cap the number of distinct lines shown in a normal look, with a full list for EXAMINE.

```python
from collections import Counter

def fit_row(cells: list[str], term_width: int = 78, design_width: int = 78) -> str:
    col = round(24 / design_width * term_width)     # scale the authored width
    return "".join(c[:col - 1].ljust(col) for c in cells)

def describe_contents(items, limit: int = 5) -> list[str]:
    groups: dict[str, list] = {}                    # name -> [count, kind]
    for it in items:
        groups.setdefault(it.name, [0, it.kind])[0] += 1
    lines, overflow = [], set()
    for name, (count, kind) in groups.items():
        if len(lines) < limit:
            lines.append(f"({count}) {name}" if count > 1 else name)
        else:
            overflow.add(kind)
    if overflow:
        lines.append(f"There are also {', '.join(sorted(overflow))} here.")
    return lines
```

**Things to avoid, from the problems above.** Use parameterised queries (`sqlite3` placeholders), not string concatenation (Problem 8). Append one vote row rather than rewriting a whole results blob (Problem 9). Avoid per-call full-table reads (Problem 9). Use deterministic prefix matching with an ordered list (Problem 12). Compare numbers as numbers (Problem 11).

## Coverage

**Read in full (8 files):** `Libraries/interfaces/PollManager.java`, `Common/interfaces/Poll.java`, `Common/DefaultPoll.java`, `Libraries/Polls.java`, `Libraries/interfaces/ListingLibrary.java`, `Libraries/CMLister.java`, `Libraries/interfaces/PlayerLibrary.java`, and `Libraries/CMPlayers.java` (read in six chunks, lines 1-3640).

**Read in part:**
- `Commands/PollCmd.java` (lines 36-121, the whole command body).
- `Commands/ListCmd.java` (2067-2200, 2300-2345, 5114-5136, plus dispatch lines by grep).
- `Commands/Top.java` (50-164).
- `Commands/Create.java` (2290-2315) and `Commands/Destroy.java` (2228-2252).
- `core/database/PollLoader.java` (44-75 and 225-300; the rest by grep).
- `core/database/DBInterface.java` (1588-1624).
- `Common/interfaces/PrideStats.java` (40-120) and `Common/interfaces/TimeClock.java` (730-870).
- `core/CMProps.java` (grep, and 1688-1728).
- `coffeemud.ini` (sections at lines 212-218, 509-515, 652-664, 818-838, 1232-1262, 2018-2030, 2060-2069, 2116-2127).
- `Libraries/CommonMsgs.java` (1996-2025, plus grep for lister calls).
- `core/interfaces/MudHost.java` (grep for the constant).

**Grep only:** `WebMacros/PollData.java`, `WebMacros/PollNext.java`, `Libraries/CMProtocols.java`, `Libraries/CMGenEditor.java`, `Commands/Modify.java` (the poll branch was not read in full), `Commands/Who.java`, `Commands/Help.java`, `Commands/Inventory.java`, `Commands/ClanPVPKills.java`.

**Skipped:** the `DatabaseEngine` internals beyond the poll table, `PlayerStats` and `PlayerAccount` (other buckets), `MaskingLibrary`, the generic editor, `XMLLibrary`, `core/smtp`, and the middle section of `PollLoader.java` (lines 75-224, the list and update variants).

**Not verified:**
- the semantics of `CMUtensils.compileConditionalRange`;
- the final argument of `startTickDown`;
- whether `DB.query` escapes its input;
- whether `DBReadPlayer` can return null when `playerExists` is true;
- the caller that passes `commands == null` at login;
- how `ListCmd` combines the two security flags on POLLS;
- what `emailOrJournal` does;
- the meaning of the `(short)5` argument to the pride scans;
- whether `AREAS_EXPLORED` is shown anywhere;
- whether `destroy()` is idempotent.
