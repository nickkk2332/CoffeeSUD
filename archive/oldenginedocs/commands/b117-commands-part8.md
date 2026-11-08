# Commands, part 8: Load through Modify

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b117` (Commands [part 8/12: Load .. Modify]) · 12 files · 6582 lines of Java.

## Summary

This bucket holds twelve verbs from the middle of the alphabetical command set. Most of it is builder and admin tooling: MODIFY (3,427 lines) edits live rooms, areas, mobs, items, abilities, quests, socials and game data files, and MERGE (1,068 lines) patches world objects from an exported file or against a database. Both use one generic, name-keyed stat interface, so any field can be read, compared and written as a string. The player-facing verbs are small and follow the check, message, send pattern described in b170: LOAD (ammunition reloads), LOCK, LOGOFF, MEND, LOOK, MOTD/NEWS (the "what changed since you last logged in" digest that the login sequence runs) and MXP. Three verbs (MPRUN, MPCOMMAND, METAMSGCOMMAND) expose the scripting engine and the message system to builders. Several defects in MODIFY and MERGE are listed under "Problems and cruft", and a Python port should not copy them.

## Key files and types

Java paths below omit the `com/planet_ink/coffee_mud/` prefix, as in b170. Paths under `resources/` and `coffeemud.ini` are relative to the CoffeeMUD folder.

| File or class | Role | Lines |
|---|---|---|
| `Commands/Modify.java` | MODIFY/MOD: builder and admin editor for live objects and game data; about 45 subcommands plus a `MODIFY <name>` shorthand | 3427 |
| `Commands/Merge.java` | MERGE: diff-and-merge of MOBS or ITEMS export files into the world, or interactive compare against a database | 1068 |
| `Commands/MOTD.java` | MOTD/NEWS: login digest of news, clan notices, parcels, mail, quests and journal replies; ON/OFF; SET edits the MOTD text | 535 |
| `Commands/Load.java` | LOAD/RELOAD: ammunition reload for players; class, area, resource and faction loads and Java compile for archons | 353 |
| `Commands/Look.java` | LOOK: room, exit, item, mob and container descriptions; FOR searches | 351 |
| `Commands/Lock.java` | LOCK: exits and lockable containers, with the paired exit updated | 151 |
| `Commands/Logoff.java` | LOGOFF/LOGOUT: confirmation prompt, PK lockout, quit broadcast | 140 |
| `Commands/Mend.java` | MEND: random choice among the mending skills the mob knows | 126 |
| `Commands/MPRun.java` | MPRUN: runs a MOBprogram script with optional user:password, with a tattoo throttle | 123 |
| `Commands/MXP.java` | MXP: switches on MXP telnet mode and sends the MXP stylesheet | 121 |
| `Commands/MPCommand.java` | Each MP verb as a typed command; runs one typed line as a script | 99 |
| `Commands/MetaMsgCommand.java` | METAMSGCOMMAND: a verb that exists only as a meta-message | 88 |

Related code followed from this bucket (not in it):

| File or class | Why it matters | Lines |
|---|---|---|
| `Commands/StdCommand.java` | Default action costs (224-233), orderability (265-268), security (271-274) | 316 |
| `MOBS/StdMOB.java` | `doCommand` dispatch; catches command exceptions and prints the "game bug" message (2241-2288) | 6287 |
| `Libraries/CoffeeMaker.java` | `isAnyGenStat` and `setAnyGenStat`: name-based stat access (3297, 3797-3803) | 6719 |
| `Libraries/CMGenEditor.java` | `CMLib.genEd()`: the interactive Gen editor that most MODIFY subcommands call | 12704 |
| `core/CMSecurity.java` | `isAllowed(mob, room, flag)` with area sub-operator scope (883-899) and `isAllowedEverywhere` (944-956) | 3068 |
| `Abilities/Common/CraftingSkill.java` | `canMend` (2204-2227): which items a craft skill may repair | 2459 |
| `Abilities/GenAbility.java` | Generic ability class; `isGeneric()` is true (347-350) | 1516 |
| `Common/interfaces/ScriptingEngine.java` | `methods` array of MP verbs (620 onward) | 808 |
| `Libraries/CharCreation.java` | Login sequence; runs `MOTD NEW PAUSE` (552, 558) | 5394 |
| `core/interfaces/MUDCmdProcessor.java` | Meta-flag constants `METAFLAG_ASMESSAGE=64` (50) and `METAFLAG_QUIETLY=512` (56) | 202 |
| `core/CMProps.java` | Command action-cost accessors (1143-1197) and config reads (3099-3100) | 3965 |
| `coffeemud.ini` | `DEFCMDTIME=0.20` and `DEFCOMCMDTIME=1.0` (545-546) | 2526 |

## How it works

### 1. Command catalogue

All twelve classes extend `StdCommand` and return their access words from `getAccessWords()`. Action cost works in two ways. Five commands override `actionsCost` and `combatActionsCost` to read the configured default: LOAD, LOCK, LOGOFF, MEND and METAMSGCOMMAND (for example `Load.java:342-352`). Those commands pay the configured 0.20 actions out of combat and 1.0 in combat (`coffeemud.ini:545-546`, `CMProps.java:1183-1196`). The other seven inherit the `StdCommand` default of 0.0 (`StdCommand.java:224-233`), so LOOK, MOTD, MERGE, MODIFY, MPRUN, MPCOMMAND and MXP are free. The "Orderable" column says whether a commanded or charmed mob may be given the verb (`canBeOrdered`).

Audience key: P = player; B = builder (a per-subcommand flag); A = admin, archon or sysop.

| Verb (access words) | Syntax | What it does | Audience | Cost and gates |
|---|---|---|---|---|
| LOAD, RELOAD | `LOAD <ammo> <weapon>`. Archon forms: `LOAD <TYPE> <name>`, `LOAD AREA <name>`, `LOAD RESOURCE <file>`, `LOAD FACTION <file>`, `LOAD <file>.java` | Ammunition into a ranged weapon (`Load.java:77-188`). Archon: load a game class of a given type (289-313), an area with door relinking (217-254), a text resource (202-215) or a faction (191-200). A `.java` file is compiled with the system compiler (260-281) | P for ammo; A for loads | Actions 0.20 / 1.0. Orderable. The archon path needs LOADUNLOAD (`Load.java:82`); compile and load needs sysop (`256`) |
| LOCK, LOC | `LOCK <direction or thing>` | Locks an exit or a lockable container. An open door is closed first (`Lock.java:71-80`). The paired exit is locked too (`107-111`) | P | Actions 0.20 / 1.0 (`Lock.java:133-143`). Orderable |
| LOGOFF, LOGOUT | `LOGOFF`; a trailing `!` is passed on when the mob has a soulmate, that is, a possession (`Logoff.java:55-56`) | Asks "Logout -- are you sure (y/N)?" with a 30 s timeout that defaults to N (`80-111`). On yes, sends a quit message and logs out, or stops the session if logouts are disabled (`98-107`) | P | Actions 0.20 / 1.0. Not orderable (`134-137`). Refused for five minutes after a PK fight unless sysop (`61-67`). `LOGOUTMASK` applies (`69-75`) |
| MEND | `MEND <item>` | Picks one of the mending skills the mob knows that supports the item, at random (`Mend.java:72-94`). A properly worn item must be removed first (`67-71`) | P | Actions 0.20 / 1.0. Orderable |
| MOTD, NEWS | `MOTD [AGAIN, NEW, PREVIOUS or OLD] [count] [PAUSE]`; `MOTD ON`; `MOTD OFF`; `MOTD SET` | Digest of items since last login (AGAIN/NEW) or older items (PREVIOUS/OLD) (`MOTD.java:95-407`). ON/OFF controls the login digest (`408-432`). SET opens the journal editor to replace the MOTD file (`434-472`) | P. SET has no role check of its own (see Problems) | Cost 0. Orderable |
| MPRUN | `MPRUN [user:password] <script>` | Runs a MOBprogram script in a non-savable scripting engine, then prints "Completed." (`MPRun.java:52-104`) | A (JSCRIPTS at the room, `79-80`) | Cost 0. Not orderable. Failed logins throttle it (section 9) |
| MP verbs, via MPCOMMAND | `MP<VERB> <arguments>`, for each of the 84 MP verbs in `ScriptingEngine.methods` (for example `MPECHO`, `MPGOTO`, `MPSET`) | Runs one MOBprog line at once, as the typing mob, by wrapping it in a temporary program named MPRUN (`MPCommand.java:72-79`) | B or A (JSCRIPTS) | Cost 0. Not orderable |
| METAMSGCOMMAND | Internal. Not typed by players | Only when invoked with the ASMESSAGE meta flag (`MetaMsgCommand.java:53`). Sends the rest of the line as a room `MSG_COMMAND` message (`58-65`). Typed input is treated as unknown (`54`) | Internal | Actions 0.20 / 1.0. Not orderable |
| MXP | `MXP`; `MXP QUIET`; `MXP OFF` | Switches on MXP telnet mode and sends the text of `text/mxp.txt` unless quiet (`MXP.java:73-99`). OFF hands over to NOMXP (`57-64`) | P | Cost 0. Not orderable. Disabled by `DisFlag.MXP` (`118`) |
| LOOK, LOO, LO, L | `LOOK [UNOBTRUSIVELY]`; `LOOK [AT] <thing>`; `LOOK AROUND`; `LOOK FOR [ALL] <thing>`; `LOOK <direction>`; `LOOK <thing> IN, FROM or ON <container>`; `LOOK EXIT` | Describes the room, a thing, an exit or a container. FOR lists matches as "You spot ..." (`Look.java:205-273`). A trailing UNOBTRUSIVELY is stripped (`61-67`), and for a plain LOOK it removes the room-look text (`315`) | P | Cost 0. Orderable. The game also forces LOOK through `CommonMsgs.postLook` (`CommonMsgs.java:420-426`) |
| MERGE | `MERGE [ROOM, AREA or WORLD] <file> [ON=..] [CHANGE=..] [IGNORE=..]`; `MERGE [scope] DATABASE <MOBS, ITEMS, ROOMS, WEAPON or ARMOR> DBCLASS=.. DBSERVICE=.. DBUSER=.. DBPASS=.. [IGNORE=MISSING,EXTRA] [MASK=..]`; `MERGE ?` | File merge (section 2) or database compare (section 3). `MERGE ?` lists the valid field names (`Merge.java:236-250`) | B (MERGE flag; WORLD needs it everywhere) | Cost 0. Orderable. `securityCheck` uses `isAllowedAnywhere(MERGE)` (`Merge.java:1065`) |
| MODIFY, MOD | `MODIFY <noun> [args]`, or `MODIFY <name>` (shorthand) | Edits one live object or game data entry (section 4) | B and A; each noun has its own flag | Cost 0. Not orderable. `securityCheck` passes if any command group applies (`Modify.java:3421-3425`) |

### 2. Merge: field diff over string-keyed stats

Every game object exposes its editable fields by name. `Merge.getStat` and `setStat` wrap `getStat` and `setStat` on the object (`Merge.java:52-75`). REJUV is a special case: it reads the base rejuvenation value and reports `NO_REJUV` as "0" (`52-64`). The candidate fields come from `getStatCodes()` on the local object, plus REJUV (`Merge.java:96`, `102`).

`tryMerge` (`Merge.java:84-196`) does the matching for each object in the file:

- Only file objects with the same class ID are candidates (`123`).
- The match fields are the ON list if one was given. Otherwise they are all stat codes, minus IGNORE and CHANGE fields (`104-113`, `126-136`).
- Every match field must compare equal as strings (`138-152`).
- If the match holds, the fields to write are the CHANGE list intersected with the stat codes, or all stat codes if there is no CHANGE list (`153-166`).
- Each field that differs is copied and logged to the system log (`169-180`, `177`).
- After any change, the object's physical and character stats are recomputed and `text()` is called (`184-194`).

Parsing (`Merge.java:385-440`): the words `CHANGE=`, `ON=` and `IGNORE=` select a target list. Bare words that follow are added to the most recent list. Each name must be a valid field name for some class (`241-247`, `369-380`). At least one of the three lists must be non-empty (`441-445`). The input file must begin, within its first 20 characters, with `<MOBS>` or `<ITEMS>` (`321`, `341`). Other files are refused (`361`).

Writing back (`Merge.java:495-564`): each target room is locked by its own `SYNC` + room-ID monitor (`502`). Its area is set to FROZEN while the write runs (`506`) and restored afterwards (`562`). The code visits floor items, the items carried by savable mobs, and shop stock (`521-552`). Changed sets are saved with `DBUpdateItems` and `DBUpdateMOBs` (`556-559`). Scope defaults to every area the mob may merge (`446-453`). ROOM and AREA restrict the scope (`251-275`).

### 3. Merge DATABASE mode: keyed three-way compare

`MERGE ... DATABASE` opens a second database connection from DBCLASS, DBSERVICE, DBUSER and DBPASS (`Merge.java:706-711`, `742`). The defaults are 3 connections, a 30-minute ping interval, connection reuse on and no transactions (`713-716`). These values are typed in the game, so they appear in the typed command line. The scope is the current area for AREA, the current room for ROOM, and otherwise every area (`756-767`).

Objects are matched by name, with an occurrence index for duplicates. The key is `name.N` (`830`, `886`), and the search loops are at `833-841` and `889-897`. These rules apply:

- A database object with no local match is offered for adding, unless `IGNORE=MISSING` is given (`844-854`, `900-912`).
- A local object with no database match is offered for deletion, unless `IGNORE=EXTRA` is given (`935-944`, `951-958`, `1025-1032`).
- Rooms that exist in the database but not in the local world are skipped with a log line (`798-805`).

Field differences go through `dbMerge` (`Merge.java:618-694`). For each stat that differs, it shows the local and database values and asks D (take the database value), E (edit with the Gen prompt), N (keep) or Q (quit) (`645-672`). By default the differences are listed with numbers and the user picks one with "Edit which?". With the EDITORTYPE property above zero, each difference is prompted in turn (`636-638`, `656-691`). Q throws `CMException("Cancelled by user.")` (`665`), which the caller reports as "Cancelled." (`1048-1051`).

### 4. Modify: the common shape

#### 4.1 Routing and permission

`execute` reads the second word and dispatches to one method per noun (`Modify.java:2624-3396`). Each branch checks one `CMSecurity.SecFlag` before it runs. Examples: CMDITEMS for ITEM (`2637`), CMDROOMS for ROOM (`2692`), CMDAREAS for AREA (`2790`), CMDEXITS for EXIT (`2806`), TICKTOCK for the calendar words (`2884`). A denied user gets "You are not allowed to do that here." (`errorOut`, `2609-2613`).

`CMSecurity.isAllowed(mob, room, flag)` (`CMSecurity.java:883-899`) accepts sysops. For others it checks the player's flags and class flags, with `subop` set when the mob is a sub-operator of the room's area (`Area.amISubOp`). This is how area staff get grants for their own areas only. `isAllowedEverywhere` passes `subop=false` (`944-956`). Actions that are global use the everywhere check: `MODIFY UPDATE:` (`3182`), `MODIFY AREA UPDATE:` (`2794`) and `MERGE WORLD` (`279`).

#### 4.2 Name-keyed generic stats

Most subcommands end with `CMLib.coffeeMaker().isAnyGenStat(obj, WORD)` followed by `setAnyGenStat` (`CoffeeMaker.java:3297-3310`, `3797-3803`). `isAnyGenStat` searches the base physical stats, base character stats, state, player stats and account. A single name space therefore covers every kind of object. When a name is not known, the reply lists the valid names (`Modify.java:262-266`, `719-722`, `1117-1119`, `2376-2380`).

#### 4.3 Persistence and change logging

Edits to a live room, item or mob are saved with the matching database call (for example `Modify.java:437`, `608-610`, `2383`). Generic prototypes are stored as XML. Races, classes, commands and abilities are deleted from the database and created again from their `ALLXML` string (`Modify.java:1600-1601`, `1705-1706`, `1632-1634`, `1786-1787`, `1826-1827`, `1872-1873`, `1911-1913`, `1951-1953`, `1991-1993`, `2032-2033`, `2066-2068`).

Most editors copy the object first (`copyOf`), compare with `sameAs` at the end, and log only when something changed (`Modify.java:177`, `269-276`, `2604-2605`, `3273-3274`). The log category names the editor, such as "CreateEdit" or "Mobs".

#### 4.4 Shorthand: `MODIFY <name>`

With no noun, `execute` tries each kind of object in order (`Modify.java:3209-3394`):

1. An item in the room or the inventory (`3251-3276`). Generic items open the Gen misc editor; others open the standard item editor.
2. A mob (`3278-3317`). A non-player monster opens the monster editor. A player routes to `players()` with a rebuilt command list (`3300`). A generic mob opens the misc editor.
3. An exit, by direction name (`3319-3341`).
4. A social (`3343-3347`).
5. A space object, exact match (`3349-3360`).
6. An area, by name (`3362-3369`).
7. A space object, inexact match (`3371-3379`).
8. A player, by name (`3381-3385`).
9. Any name with an `@` target (`3387-3391`).

Each match rewrites the command list and calls `execute` again. The item target syntax is `MODIFY ITEM <name>@<room, mob or container>` (`Modify.java:80-111`).

### 5. Modify: the subcommand table

| Subcommand | Gate | What it edits | Notes |
|---|---|---|---|
| ITEM | CMDITEMS | LEVEL, ABILITY, HEIGHT, REJUV, USES, SAVEABLE, MISC, any gen stat, or the generic editor | Negative LEVEL and USES are silently ignored (`Modify.java:181`, `226`). A REJUV of 0 or less means never rejuvenate (`208-220`). A copy of a space ship has its area renamed (`277-283`, unverified; see Problems) |
| MANUFACTURER | CMDITEMS | Manufacturer record | `2475-2498` |
| ROOM | CMDROOMS | Whole room editor; NAME, DESCRIPTION, CLASS, XGRID, YGRID, ID, AREA, AFFECTS, BEHAVIORS, any gen stat | AREA can create a new area with a confirm and an area-type prompt (`481-510`), then renumber the room (`534-543`). ID must start with the area name or be `#n` (`560-574`). Invalid from grid children (`424-429`) |
| AREA | CMDAREAS | Whole area editor, or NAME, DESC, FILE, CLIMATE, PASSIVE, ACTIVE, FROZEN, STOPPED, ADDSUB, DELSUB, XGRID, YGRID, AFFECTS, BEHAVIORS | A name with a second word that is an area name retargets the command (`955-965`). Renaming can re-key the rooms (`1125-1152`). UPDATE: runs MQL across the area (`2792-2799`) |
| EXIT | CMDEXITS | Exit text, or the generic exit editor | `updateChangedExit` scans every room to find the exit's owner (`1459-1495`) |
| MOB | CMDMOBS | LEVEL, ABILITY, REJUV, MISC, gen stats | Non-player monsters in the room only (`2525-2529`). A level change refills the monster (`2593-2603`, see Problems) |
| USER, PLAYER | CMDPLAYERS | Full player editor; PROFICIENCIES; PROFICIENCY(ability); gen stats | Works for offline players through `getLoadPlayerAllHosts` (`2291`) |
| ACCOUNT | CMDPLAYERS, account system only | Account editor; renaming re-keys the account and its players | `745-797` |
| RACE | CMDRACES | Generic race | Must be generic (`1592-1597`) |
| CLASS | CMDCLASSES | Generic character class | `1697-1709` |
| ABILITY, TRAP, POISON, TWEAK, LANGUAGE, CRAFTSKILL, WRIGHTSKILL, GATHERSKILL | CMDABILITIES | Generic ability of that family | Routed through `wrongGenAbilityMessage` (`1711-1756`), which is broken for generic abilities (see Problems) |
| COMMAND | CMDCOMMANDS | Generic command | `1606-1637` |
| ALLQUALIFY | CMDABILITIES | Allqualify rule for EACH or ALL | `1639-1677`; saves the mapping file (`1675`) |
| RECIPE | CMDRECIPES | Recipe list of a recipe-driven common skill | `2656-2688` |
| COMPONENT | COMPONENTS | Component list of a skill or social | `2073-2145` |
| PLANE | PLANES | Planar rule text | `2147-2181` |
| EXPERTISE | EXPERTISES | Intended: `ID=PARAMETERS` definition | Never reached (see Problems) |
| HELP, AHELP | CMDHELP | Help entry by key; AHELP prefers archon help | `2426-2473`, dispatch at `2835-2841` |
| AWARD | AUTOAWARDS | Auto-award property, by list number | `319-377` |
| ACHIEVEMENT | ACHIEVEMENTS | Achievement by tattoo, for player, account or all | `2392-2424` |
| SOCIAL | CMDSOCIALS | Social definition; `T-` means target | `2183-2278` |
| POLL | POLLS | Vote editing on a poll | `2992-3013` |
| HOLIDAY | CMDQUESTS | Holiday entry | `3015-3035` |
| QUEST | CMDQUESTS | Start, stop, enable, disable, script | `1294-1457` |
| CRON | CMDCRON | SUBJECT, INTERVAL or SCRIPT of a scheduled job | `1192-1292` |
| NEWS | JOURNALS or NEWS | Posts a news entry | `3037-3057` |
| GOVERNMENT, FACTION, CLAN | CMDCLANS or CMDFACTIONS | Generic editor for the record | `3120-3208`; the government prefix match keeps the last match (`3137-3141`) |
| DAY, MONTH, YEAR, TIME or HOUR | TICKTOCK | Area calendar or time of day | `2882-2944`; no range checks |
| JSCRIPT | JSCRIPTS | Approves pending scripts when approval mode is on | `2946-2975` |
| SQL | CMDDATABASE | Runs raw SQL; backticks stand for quotes | `3066-3118` |
| UPDATE: | Sysop, or CMDAREAS everywhere | MQL update across all objects | `3180-3186` |
| TITLE | TITLES | Refused: the message says titles can only be listed, created or destroyed | `2843-2849` |
| EXPERTISE (second branch) | EXPERTISES | Dead code | `2984-2990` |

### 6. Notable editors inside Modify

- **MQL preview.** `MODIFY UPDATE: <mql>` asks the percolator for a list of changes (`Modify.java:811`). It shows each object with its room, the old and new value of each stat (`816-825`), and then asks "Save the above changes (y/N)?" (`872-897`). On yes, it sets each stat and saves the owning room, item or mob (`826-867`). Confirmation input is upper-cased, so "y" works (`Session.java:1343-1359`).
- **Cron.** Jobs are rows in the `SYSTEM_CRON` journal (`Modify.java:1205`). A job is found by number, exact subject, or substring (`1207-1228`). INTERVAL is parsed by `CMLib.time().parseTickExpression` against the home clock (`1256`), multiplied by the tick length in milliseconds (`1266`), and stored as the next run time (`1268`). SCRIPT opens the line editor on the job body and saves on SAVEFILE (`1277-1290`).
- **Quests.** A menu of toggles: started (start or stop) and enabled (enable or disable) (`1337-1340`). The script editor validates the edit. A quest needs a name and a duration, and names must be unique. An invalid edit is reverted (`1343-1374`). A start that does not run is reported (`1384-1390`).
- **Players.** `MODIFY USER` snapshots the player (`2306-2311`) and logs which stat codes changed (`2315-2325`). PROFICIENCIES sets every skill's proficiency (`2331-2344`). PROFICIENCY(name) sets one (`2346-2363`).
- **Areas.** State words set the area state (`981-998`). CLIMATE takes letter codes that OR bit masks (`1041-1078`). ADDSUB and DELSUB manage the area's sub-operator list (`1080-1098`). FILE sets the archive path (`1011-1019`).

### 7. Mend: which skill answers

`Mend.execute` (`Mend.java:50-94`) first checks that the target is not worn properly (`67-71`). It then collects each known ability that is a `MendingSkill` and whose `supportsMending(target)` is true (`72-82`). It chooses one at random with `CMLib.dice().roll(1, n, -1)` (`83`), which gives an index from 0 to n-1. A craft skill gets the arguments `MEND <item name>` (`90-91`). Any other skill gets only the name (`93`). The skill is then invoked (`94`).

The eligibility rules come from each skill. For craft skills, `CraftingSkill.canMend` accepts only items that are `subjectToWearAndTear()` and whose `usesRemaining()` is below 100 (`CraftingSkill.java:2215-2226`). Each prayer has its own rule. Prayer_Heal, for example, requires a MOB whose `curState().getHitPoints()` is below `maxState().getHitPoints()` (`Prayer_Heal.java:76-80`). Prayer_Bless accepts a MOB with a result from `getSomething(mob, true)` or a curse-domain effect (`Prayer_Bless.java:185-189`).

### 8. MOTD: the since-last-login digest

`MOTD.execute` (`MOTD.java:59-481`) builds one text block from many sources. The filter is the timestamp of the last login. An item is kept if its update date is not before `pStats.getLastDateTime()` (`114-123`). The sources are:

- The `SYSTEM_NEWS` journal, with a deprecated `CoffeeMud News` fallback (`110-112`).
- Clan notices from `CLAN_MOTD_<clan id>` for clans whose members have the CLAN_BENEFITS function (`154-167`).
- Post-office parcels waiting for the player, and for clans with the WITHDRAW function (`172-214`).
- Command journals flagged ADMINECHO, for staff (`216-243`).
- The mailbox, reduced to a single line: "You have mail waiting. Enter 'EMAIL BOX' to read." (`245-261`).
- Expired accounts or characters, for staff (`263-275`).
- Active quests, with the count (`277-294`).
- Journal messages waiting for a reply, which can be read at once with a 5 s confirm defaulting to N (`299-332`).
- Journal subscriptions and channel messages missed (`333-400`).

The MOTD body is the file `text/motd.txt` (`MOTD.java:94`). A body that starts with `<cmvp>` is run as a virtual page (`104-105`). The file is not shipped: there is no `motd*` file under the repository (checked by search). A new game shows no body until a builder writes one.

The login sequence calls `MOTD NEW PAUSE` after the poll step and before the calendar and shutdown checks (`CharCreation.java:552-558`). It does nothing for monsters or sessions without a connection (`549-553`). `DAILYMESSAGE` is a reversed attribute (`MOB.java:1075`). Setting it turns the digest off (`MOTD.java:421-431`).

### 9. MPRUN, MPCOMMAND and METAMSGCOMMAND

**MPRUN throttle.** When the first argument has the form `user:password`, the command looks up the player (`MPRun.java:63-64`). An unknown user gets the tattoo `SYSTEM_MPRUNDOWN` for one minute of ticks (`67`, `CMProps.getTicksPerMinute()`). A wrong password gets two minutes (`70-73`). A tattoo timer is ticks, via `addTattoo(name, tickDown)` (`StdMOB.java:5706-5712`). While the tattoo is present, every MPRUN is treated as an unknown command (`55-56`). This is an anti-guessing throttle that needs no account lock. The credentials are removed before the script runs (`76`). JSCRIPTS must be allowed for the checked mob (`79-80`).

**Script execution.** MPRUN and MPCOMMAND each run a script in a `DefaultScriptingEngine` set to not savable and scope `*`. They then call `executeMsg` and `tick` (`MPRun.java:94-104`, `MPCommand.java:60-70`). The two methods are identical copies.

**MPCOMMAND.** The access words are built lazily from `ScriptingEngine.methods`, keeping the names that start with `MP` (`MPCommand.java:43-58`, `ScriptingEngine.java:620`). The command wraps the typed text as a program: `IMASK_PROG P MPRUN` followed by the line and a `~` (`76`). So a builder can type `MPECHO hello` and see its effect at once.

**METAMSGCOMMAND.** The verb has no meaning for typed input. Without ASMESSAGE (value 64, `MUDCmdProcessor.java:50`), it calls `handleUnknownCommand` (`MetaMsgCommand.java:53-54`). With the flag, it builds a `MSG_COMMAND` message from the rest of the line (`58-59`) and sends it to the room if `okMessage` allows (`62-64`). Other parts of the game use the flag, for example `Commands/ProxyCtl.java` and `Items/Basic/StdNavigableBoardable.java`.

### 10. Lock, Look and the exit model

Lock finds the target by direction or by name (`Lock.java:60-64`). For an exit, it sends the lock message to the exit, but only when the exit is not already locked (`83-89`). The message is "<S-NAME> lock(s) <T-NAMESELF><O-WITHNAME>." with a door sound (`81`). The paired exit on the other side gets the same message (`107-111`). If the far side is also locked, the room is told "... is locked from the other side." (`113-121`). A container lock goes through `okMessage` and reports rejection (`126-129`).

Look's search logic (`Look.java:129-185`) first looks for a container named after IN, FROM or ON (`129-152`), then the room (`154-157`), then open containers in the room (`172-185`). A direction is used only when nothing else matches (`186-203`).

### 11. Load: the ammunition loop

For a weapon that takes ammunition, Load finds the next matching ammunition item (`Load.java:167`). It sends a `MSG_RELOAD` message (`175-179`) and continues while the weapon is not full (`172-173`). It stops at the first rejection (`182-183`). `doneOne` is shared by all weapons (`158`), so the loop tries at least once even when the weapon is already full. The veto of `okMessage` decides whether that attempt happens (unverified).

## Content worth mining

- **Mending skills.** Craft skills with repair support: Armorsmithing, Bandaging, Boatwright, Cobbling, Costuming, Fletching, JewelMaking, LeatherWorking, MasterLeatherWorking, Sculpting, Shipwright, Smelting, Tailoring, Textiling, Weaponsmithing, Weaving, ScrimShaw, the generic GenWrightSkill. Prayers that also mend: Bless, CureDisease, CureExhaustion, CureLight, CureSerious, HealMount, HealUndead, Heal, MassCureDisease, MinorInfusion, RemoveCurse, RestoreSmell, Restoration, RockFlesh, TrueResurrection, MassFreedom, CureCannibalism. Plus Fighter_TendMount (`Abilities/Common/*.java` and `Abilities/Prayers/*.java`, each with a `supportsMending` method).
- **MOBprog verbs.** 84 MP-prefixed verbs in `ScriptingEngine.methods` (`Common/interfaces/ScriptingEngine.java:620` onward). Examples by group: messages (MPECHO, MPECHOAT, MPECHOAROUND, MPASOUND), combat and state (MPKILL, MPDAMAGE, MPHEAL, MPAFFECT), movement (MPGOTO, MPTRANSFER, MPWALKTO, MPFORCE), world objects (MPOPEN, MPLOCK, MPHIDE, MPRESET), quests (MPSTARTQUEST, MPENDQUEST, MPQSET, MPSTEPQUEST), dialogue (MPPROMPT, MPCONFIRM, MPCHOOSE, MPSPEAK).
- **MODIFY flavour lines.** These are shown to the room after an edit, and a SUD could reuse the pattern for narrated world changes: "The very nature of reality changes." (`Modify.java:664`), "There is something different about this place..." (`609`), "The power of time just went sideways!" (`372`), "The complication of skill usage just increased!" (`2116`), "The happiness of all mankind has just fluxuated!" (`2277`), "The world is now more informed!" (`3055`). The verb-to-line table pattern is reusable.
- **Login digest lines.** "You are on N quest(s). Enter QUESTS to see them!" (`MOTD.java:284`); "There are currently N expired accounts. Enter LIST EXPIRED to view them." (`270-273`); "The journal X has N new entries and M replies for you." (`372`).
- **Look and Lock text.** "You spot '...'" and "Nothing like that catches your eye." (`Look.java:265-269`); "Logout -- are you sure (y/N)?" (`Logoff.java:85`); "<S-NAME> reload(s) <T-NAME> with <O-NAME>." (`Load.java:175`).

## Notable design ideas

- **One name space for stats.** MODIFY and MERGE both address fields by string. The reply to a bad name lists the valid names. This gives builders a discoverable editor without a hand-written form for every class.
- **Three-way compare with occurrence keys.** Matching by `name.N` (`Merge.java:830`, `886`) handles duplicates without any IDs. The per-field D/E/N/Q prompt (`645-672`) lets a builder take either side field by field.
- **Preview, then confirm.** MQL shows old and new values before any write (`Modify.java:816-897`). This is the safest pattern for bulk edits.
- **Area-scoped permission.** A sub-operator list on each area (`Area.amISubOp`) grants staff rights over their own area only (`CMSecurity.java:883-899`). `ADDSUB` and `DELSUB` manage the list from inside the game.
- **Timestamp digest.** MOTD builds the since-last-login digest from timestamps across many record types (`MOTD.java:114-123`). Nothing has to be stored per item.
- **Tattoo as a timer.** MPRUN's penalty uses the mob's existing timed-tattoo storage (`MPRun.java:67-73`), not a new mechanism.
- **Commands that exist only as messages.** METAMSGCOMMAND (`MetaMsgCommand.java:50-68`) lets game logic send a verb through the normal message flow without a player typing it.
- **Polymorphic repair.** MEND asks each mending skill whether it applies (`Mend.java:79`). A new repair method only needs a `supportsMending` method.
- **Copy, compare, log.** Most editors copy the object first and log only when `sameAs` reports a change (`Modify.java:177`, `269-276`). This keeps the audit log quiet unless something changed.

## Problems and cruft

Each item below was found by reading the code. None was run.

**Defects**

1. **MODIFY ABILITY and its family throw on generic abilities.** `wrongGenAbilityMessage` casts `(ItemCraftor)A` without a test at `Modify.java:1731`. Any generic ability that is not a tweak, language, craft skill, trap or poison reaches that line. `GenAbility` is not an `ItemCraftor` (`Abilities/GenAbility.java:38`, `Abilities/interfaces/ItemCraftor.java:44`), so a `ClassCastException` results. `abilities()` calls the check at `1782-1783`. `StdMOB.doCommand` catches the exception and tells the player "Oops-- you ran into a game bug!" (`MOBS/StdMOB.java:2281-2287`). The same unguarded call is reached by CRAFTSKILL (`1984`), GATHERSKILL (`2024`) and WRIGHTSKILL (`2059`), and by the trap, poison, tweak and language editors, which each call `wrongGenAbilityMessage` for generic abilities of the wrong kind (`Modify.java:1818`, `1858`, `1904`, `1944`). The LargeConstructions and ItemCollection messages (`1731-1742`) can never be shown for the same reason.
2. **MODIFY EXPERTISE never reaches its editor.** The EXPERTISE branch at `Modify.java:2827-2832` returns after printing "You can't modify components...". The second EXPERTISE branch at `2984-2990` is dead code.
3. **MODIFY CRON ... SUBJECT sets the subject to the word SUBJECT.** `Modify.java:1244` calls `modJob.subj(field)`, and `field` is the word "SUBJECT". The validated new value is `value` (`1238`).
4. **Climate code D sets the windy bit.** `Modify.java:1065-1067` uses `CLIMASK_WINDY` for 'D'. `core/interfaces/Places.java:103` defines `CLIMASK_DRY=16`, and the error text at `1072` lists D as DRY.
5. **ADDSUB and DELSUB do not stop on error.** The error message at `Modify.java:1082-1085` and `1092-1095` has no `return`, so an unknown user or a name that is not a sub-operator is still added or removed (`1087`, `1097`).
6. **MODIFY PLANE message is inverted.** `StdPlanarAbility.getPlanarVars` returns null when no plane matches (`Abilities/StdPlanarAbility.java:1637-1657`). `Modify.java:2159-2163` reports "already exists" when the result is null.
7. **MOTD channel count is a string concatenation.** `MOTD.java:391` writes `""+(lastIndex-lowestIndex)+1`, which gives "51" instead of 6 when the difference is 5.
8. **MERGE IGNORE does not protect fields when CHANGE is omitted.** `allMyFields` is copied before IGNORE fields are removed (`Merge.java:103-108`). With no CHANGE list, that copy is the write list (`156-157`). IGNORE only narrows the match test.
9. **MERGE IGNORE is case-sensitive in the database path.** `ignores` is built from the upper-cased text (`717-719`), but the checks at `844`, `900`, `935`, `990` and `1025` test the raw string with `ignore.contains("MISSING")` or `"EXTRA"`. `CMParms.getParmStr` returns the text as typed (`core/CMParms.java:1509-1530`). So `IGNORE=missing` does not suppress the prompt.
10. **MERGE cancel can leave an area FROZEN.** A Q throws from inside the synchronized block (`Merge.java:806-1043`). The restore at `1042` is skipped and the catch at `1048-1051` does not restore it. Read from code; not tested.
11. **MERGE forces areas ACTIVE.** After the run, every affected area is set to ACTIVE (`566-574`). The state saved at `505` and restored at `562` is ignored.
12. **MERGE database mode never writes room fields.** `updateRoom` is the constant false (`Merge.java:813`), so the `DBUpdateRoom` call at `1035-1036` never runs.
13. **MOTD SET has no role check.** `MOTD.java:434-436` relies only on `motdFile.canWrite()`. `core/CMFile.java:721-733` looks at VFS bits and does not take the mob into account. Unless another layer blocks players, any player can replace the MOTD text when the file is writable. This needs confirming.
14. **MODIFY MOB level refill tests the wrong mob.** `Modify.java:2593` tests `!mob.isGeneric()`, where `mob` is the typing player, not the edited `modMOB`. The intent is probably `modMOB` (unverified).
15. **LOAD with no arguments fails.** For a non-archon, `Load.java:89` reads `commands.get(1)` on a list of one word. The result is the generic game-bug message.
16. **Silent failures.** Negative LEVEL and USES on items do nothing and say nothing (`Modify.java:181`, `226`). Locking an exit that is already locked produces no message (`Lock.java:88-89`).
17. **Space ship copy rename.** `Modify.java:277-283` renames the copy's area to a "DO NOT DE_REGISTER" name. Whether that area object is shared with the live ship depends on the clone code (`Areas/StdSpaceShip.java:399-412`). Not checked; treat as a hazard.
18. **Dead message branch in LOOK.** `Look.java:306-312` ("Examine what?") needs a first word beginning with E. None of Look's access words does.
19. **Dead branch in MOTD.** `NEW` is handled first (`MOTD.java:96`), so the `NEW` test in the SET branch (`434`) is reachable only when the stats object is missing.
20. **Database mode indexes by the wrong array.** `Merge.java:645-647` loops over the database object's stat codes and reads `M.getStatCodes()[i]`, which assumes equal lengths.

**Cruft and design issues**

- **Inconsistent costs.** Five commands charge the default 0.20 action; seven charge nothing (section 1). The configuration allows per-command exceptions (`coffeemud.ini:544`), but none is set for these verbs.
- **Duplicate script runner.** MPRUN and MPCOMMAND carry the same `executeScript` body (`MPRun.java:94-104`, `MPCommand.java:60-70`).
- **Credentials typed in the game.** MPRUN takes `user:password` (`MPRun.java:70`). MERGE takes DBPASS (`Merge.java:711`). Both appear in the typed line.
- **One large dispatcher.** `Modify.java` is 3,427 lines. The `execute` chain is about 2,000 lines of `if commandType.equals` (`2635-3394`). The shorthand re-enters `execute` through rewritten command lists (`3338-3346`, `3351-3359`, `3364-3368`, `3374-3378`, `3383-3390`). The last of these looks safe for the shipped space-object classes, which extend or implement Area (`Areas/StdBoardableShip.java:39`, `Areas/StdPlanet.java:35`) or Item (`Items/Basic/StdSpaceBody.java:40`). That is not checked for every class, so a rewrite is not guaranteed.
- **Full-world scan per exit edit.** `updateChangedExit` loops over every room to find the exit's owner (`Modify.java:1465-1485`).
- **News item leak.** `MODIFY NEWS` creates a journal item and does not destroy it (`3044-3054`).
- **Mislabelled log categories.** Quest edits are logged under "Rooms" (`1454`). Rooms missing locally are logged as "not in database" (`Merge.java:802`), which means "not in the local world".
- **Repeated generic-object save pattern.** Eleven editors repeat the same delete-and-recreate sequence (section 4.3). A helper would remove the copy-paste.

## Takeaways for a Python SUD

1. **Name-keyed fields for editing.** Give each entity a declared field table (a dataclass with a list of names and types). Build the "try one of" reply from the table. Report a bad value; do not coerce it to zero the way `CMath.s_int` does in these commands.
2. **A content sync tool with explicit rules.** Model MERGE as `sync(source_items, world_items, match_on, write_fields, ignore_fields)`. Decide the meaning of IGNORE first (match only, or write as well). Key objects by (type, name, occurrence) when names repeat. A designer could edit content in YAML or JSON and re-apply it to a save file.
3. **Preview and confirm for any bulk edit.** Compute the old and new values first, print them, and write only after yes. MQL's flow (`Modify.java:811-897`) is the model.
4. **A "while you were away" digest.** Build it from an event log and the last-seen time, the way MOTD filters by `getLastDateTime()` (`MOTD.java:114-123`). For a living world this is central, so it deserves its own module.
5. **Repair as a list of candidate skills.** Give each repair skill a `can_repair(item)` predicate and a durability rule such as "usesRemaining below 100" (`CraftingSkill.java:2215-2226`). Choose among the valid candidates at random, as MEND does.
6. **One door object, not two paired exits.** LOCK has to message both sides and special-case the far side (`Lock.java:107-121`). A single `Door` shared by two rooms has one state, so it needs none of that code.
7. **Command costs in one place.** Use a single default and an explicit per-command override table. Do not mix free and default-cost declarations, as this bucket does.
8. **Drop the multiplayer parts.** Leave out clan notices, post-office parcels, channel replay, journal replies, soulmate dispossession, PK lockouts, global quit broadcasts and the per-room SYNC monitors. The MPRUN tattoo throttle guards against remote password guessing, which a local single-player game does not face.
9. **Keep the builder console as a dev tool.** A single-player debug command with field discovery, preview-confirm and a one-line script runner (the MPCOMMAND pattern) would help a designer test a world without a separate editor. Build it as a table of handlers, each with its own declared permission, not one long if-chain.

## Coverage

- **Read in full (all code lines):** `Commands/Modify.java` (3427), `Commands/Merge.java` (1068), `Commands/MOTD.java` (535), `Commands/Load.java` (353), `Commands/Look.java` (351), `Commands/Lock.java` (151), `Commands/Logoff.java` (140), `Commands/Mend.java` (126), `Commands/MPRun.java` (123), `Commands/MXP.java` (121), `Commands/MPCommand.java` (99), `Commands/MetaMsgCommand.java` (88). The import blocks were checked for names only.
- **Skimmed:** none.
- **Skipped:** none within the bucket.
- **Related code followed (targeted reads, not full):** `Commands/StdCommand.java`, `MOBS/StdMOB.java` (dispatch and cost fields), `Libraries/CoffeeMaker.java` (stat accessors), `core/CMSecurity.java` (flag checks), `core/CMParms.java` (`getParmStr`), `core/CMFile.java` (`canWrite`), `core/CMProps.java` (cost accessors), `Libraries/CMMap.java` (`getArea` lookup), `Libraries/CharCreation.java` (login hook), `Libraries/CommonMsgs.java` (`postLook`), `Abilities/GenAbility.java`, `Abilities/interfaces/ItemCraftor.java`, `Abilities/Common/CraftingSkill.java` (`canMend`), `Abilities/Prayers/Prayer_Heal.java` and `Prayer_Bless.java` (`supportsMending`), `Abilities/StdPlanarAbility.java` (`getPlanarVars`), `Common/interfaces/ScriptingEngine.java` (MP verb list), `core/interfaces/MUDCmdProcessor.java` (meta flags), `Areas/StdSpaceShip.java` (`copyOf`), `Items/Basic/StdSpaceBody.java`, `Areas/StdBoardableShip.java`, `coffeemud.ini` (cost keys), and `core/interfaces/Places.java` (climate masks).
- **Not followed:** the internals of `CMGenEditor`, the database layer, `Quit.dispossess` (from LOGOFF), `Session.prompt`, and the scripting engine's execution.
- **Not executed:** every defect in Problems and cruft was found by reading. Items 1, 2, 3, 4, 5 and 9 are the most certain. Items 10, 13, 14 and 17 need a run or a closer read to confirm.
