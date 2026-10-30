# Commands, part 7: Kill to List

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b116` (Commands [part 7/12: Kill .. ListCmd]) · 10 files · 8805 lines of Java.

## Summary

This bucket holds nine small verbs (KILL, LINK, LEARN, LEAVE, KNOCK, LINEWRAP, LLM, LEFT, LANGUAGES) and LIST, the largest file in it at 7,491 lines. LIST is the staff window onto the game: its 122 permission-gated entries print areas, rooms, mobs, abilities, classes, quests, journals, tick timers, threads, logs and database connections, and the same data can be exported as wiki templates or JSON. The small verbs show the command model at its simplest, with a per-verb action cost (0.2 actions out of combat and 1.0 in combat by default), an orderable flag, and verbs that hand the work to an NPC, as LEARN does when it makes the character speak to a teacher and then runs TRAIN. LIST's most distinctive parts are a zapper-mask search over world contents, an in-game query language (`LIST SELECT:`), configurable command journals, and an ability taxonomy of 14 classification codes, 82 domains and 44 flags. Several subcommands contradict their help text or print wrong data; these were found by reading the code, not by running it, and are listed under Problems and cruft.

## Key files and types

Java paths are shortened. `Commands/Kill.java` means `com/planet_ink/coffee_mud/Commands/Kill.java`, relative to the CoffeeMud folder. Other paths are given in full. Line counts are for the Java file.

| File or class | Role | Lines |
|---|---|---|
| `Commands/ListCmd.java` | LIST dispatcher, the 122-entry permission enum `ListCmdEntry`, about 100 helper methods (report builders, wiki and JSON exporters), shopkeeper review (`LIST` alone or `LIST <shopkeeper>`) | 7491 |
| `Commands/Kill.java` | KILL, K, ATTACK, MURDER, FIGHT. Target choice, retargeting, auto re-wield, admin KILL DEAD | 240 |
| `Commands/Link.java` | LINK. Builder verb that makes a two-way exit between two existing rooms (extends `At`) | 253 |
| `Commands/Learn.java` | LEARN. Routes a request to a teacher NPC and then to TRAIN | 173 |
| `Commands/Leave.java` | LEAVE. Dismounts, or walks out of a room through its only open exit | 143 |
| `Commands/Knock.java` | KNOCK on an exit door or an object. Sound carries to nearby indoor rooms | 142 |
| `Commands/LineWrap.java` | LINEWRAP. Per-player word-wrap width | 107 |
| `Commands/LLM.java` | LLM. Admin chat with an optional LangChain4j model (archon category) | 97 |
| `Commands/Left.java` | LEFT. Relative movement for caravan-type vehicles (extends `Go`) | 92 |
| `Commands/Languages.java` | LANGUAGES, LANGS. Lists known language abilities (extends `Skills`) | 67 |
| `Commands/StdCommand.java` | Base class. Default costs (224-227), `canBeOrdered` (265-268), `securityCheck` (271-274), `I()` word translation (78-83) | 316 |
| `Commands/Go.java` | Movement base. Its cost rule (261-276) is inherited by LEFT | 283 |
| `Commands/Skills.java` | `parsedOutIndividualSkill` (65-157), used by LANGUAGES | - |
| `Behaviors/MOBTeacher.java` | Teacher NPC. Parses "TEACH" and "GAIN" in speech (337-558) | 562 |
| `Libraries/CMProtocols.java` | LLM session factory, model setup, rolling chat memory (4739-5125) | - |
| `core/CMSecurity.java` | `SecFlag` enum (2341-2377) and the permission helpers (908, 969) | - |
| `core/CMProps.java` | Command cost lookup (1143-1196) | - |
| `core/CMParms.java` | `combine` (364-373), end-exclusive | - |
| `core/Directions.java` | `DirType` (40-44) and the caravan and ship direction names (122-125) | - |
| `Abilities/interfaces/Ability.java` | Classification codes (671-701), description arrays (708-716), domains (722-884), flags (946-1036) | - |
| `Libraries/EnglishParser.java` | `parseSpaceDistance` (585-610). Returns decametres. A bare number is taken as decametres | - |
| `resources/help/arc_help.ini` | `LIST_*` help keys. ListCmd checks them at run time (6880-6888) | - |
| `coffeemud.ini` | Command cost defaults (545-546), command journals (1466-1500), LLM settings (2219-2232) | - |

## How it works

### 1. The command contract

- A verb is one prototype object. Its access words come from `getAccessWords()` (for example KILL, K, ATTACK, MURDER, FIGHT in `Kill.java:41-47`), and they go through `I()` so that they can be translated (`StdCommand.java:78-83`).
- `execute(mob, commands, metaFlags)` receives the split words, with the command word at index 0. LIST shows this by removing index 0 first (`ListCmd.java:7393`).
- **Action cost.** The base `actionsCost` is the configured per-verb value, or 0.0 if none is set (`StdCommand.java:224-227`). Verbs that override it use `DEFCMDTIME`, which is 0.20 by default outside combat. `DEFCOMCMDTIME`, which is 1.0 by default, applies in combat (`coffeemud.ini:545-546`; `CMProps.java:1183-1196`). The config comment says 1.0 is one full 4-second tick. Per-verb exceptions are written as a list such as `Score 1.0, East 0.5, 0.15` (same comment). `Go` divides the cost by 4 when AUTORUN is set (`Go.java:261-267`).
  - Kill, Learn, Leave, Knock and LIST override both costs with the defaults.
  - Link, LineWrap, LLM and Languages override neither, so they cost 0.0 unless configured. Builders' LINK and players' LINEWRAP are free in the default setup.
  - Left inherits `Go`. Its outside-combat cost is 0.20, or 0.05 with AUTORUN.
- **Ordering.** `canBeOrdered()` defaults to true (`StdCommand.java:265-268`). Learn and LineWrap opt out (`Learn.java:167-171`, `LineWrap.java:101-104`).
- **Listing.** `putInCommandlist()` defaults to true. Left opts out (`Left.java:88-91`).
- **Security.** `securityCheck()` defaults to true (`StdCommand.java:271-274`). Link, LLM and Left override it.

### 2. KILL (`Kill.java`)

- **Bare KILL.** When not fighting, it prints "Kill whom?" (`Kill.java:67-69`). When fighting, it attacks the current victim. Under the DEFAULT combat system a bare KILL does nothing (`Kill.java:72-74`). A null command list (`Kill.java:53-60`) is the auto-repeat path that the combat loop uses. The caller was not checked.
- **Target lookup.** A visible room target is found first. Otherwise an item gives "'X' is not alive.", and anything else gives "I don't see 'X' here." (`Kill.java:116-124`).
- **Follower preference.** When either party is following someone, the code looks for a non-follower match. It tries `name.2`, `name.3` and so on, up to 1000 (`Kill.java:127-143`).
- **Admin instant kill.** `KILL <x> DEAD` needs the KILLDEAD security flag and a non-monster caller (`Kill.java:81-88`). It sets hit points to 0 and calls `postDeath` with no combat (`Kill.java:146-156`). `KILL ALL DEAD` does this to every NPC in the room that is not following anyone. Each one is addressed with its exact context name in the form `$name$` (`Kill.java:85-107`).
- **Retargeting.** While already in combat, choosing a new target costs an action and prints "You are now targeting X." (`Kill.java:159-186`). If the old and new victims share a victim, the range to the target is carried over (`Kill.java:172-182`).
- **Attack.** If the attacker may not attack the target, the message is "You are not allowed to attack X." (`Kill.java:189-196`). When the attacker has no wielded weapon but holds one, the held weapon is removed and wielded by the Wield command before the attack (`Kill.java:199-217`). The attack itself is handed to `CMLib.combat().postAttack` (`Kill.java:218`).
- **Cost and ordering.** Combat cost is `getCommandCombatActionCost(ID())`, which defaults to 1.0 (`Kill.java:224-227`). Outside combat it is 0.2. Kill can be ordered (`Kill.java:236-238`).

### 3. LINK (`Link.java`)

- Syntax is `LINK [ROOM ID] [DIRECTION]` (`Link.java:55-69`). The target room is looked up in this area, then by global ID, then by a world search. `findWorldRoomLiberally(mob, roomID, "R", 100, 120000)` searches rooms with a time limit of 120 seconds (`Link.java:71-88`; `WorldHuntLibrary.java:71`). In a boat area, the word "outside" means the room outside the vessel (`Link.java:76-79`).
- `exitifyNewPortal` (`Link.java:101-239`) writes the exit in both directions. If the room on the other side already has an exit back, the link is made one-way and the player is told so (`Link.java:123-125`). When either room sits in a grid locale, the code writes grid cross-exits (`GridLocale.CrossExit`) instead (`Link.java:130-188`). The new exit is a `StdOpenDoorway` (`Link.java:156-161`). Both areas are saved with `DBUpdateExits` (`Link.java:230-237`).
- The room is told "Suddenly a portal opens up in the landscape." The security gate is CMDEXITS (`Link.java:96`, `Link.java:248-251`). Builder-level.

### 4. LEARN (`Learn.java`) and the teacher side (`MOBTeacher.java`)

- LEARN does not teach anything. It works in four steps.
  1. If the caller is alone in the room, it says "You will need to find someone to teach you first." (`Learn.java:55-59`).
  2. An optional last word names the teacher in the room. "FROM" is also accepted, and the form becomes SAYTO (`Learn.java:66-80`).
  3. It looks the request up in this order: the TRAIN list (`Learn.java:84-93`), an ability by name (`Learn.java:94-102`), an expertise (`Learn.java:103-136`), and a prefix of the TRAIN list (`Learn.java:138-149`). If nothing matches, it sends a generic request (`Learn.java:150-152`).
  4. For a match it has the character say a phrase such as "I would like you to teach me X" or "I would like to be trained in X". The phrase goes through `mob.doCommand`, so NPC reactions apply. Then it runs TRAIN locally (`Learn.java:87-101`).
- `MOBTeacher.executeMsg` (`MOBTeacher.java:348-559`) does the teaching. It scans speech for the words TEACH or "GAIN " (`MOBTeacher.java:379-383`). It strips quotation marks and the politeness words PLEASE and ME (`MOBTeacher.java:387-419`). "PLEASE" sets a bonus that raises the teacher's INT and WIS to 25 for the lesson (`MOBTeacher.java:543-548`). After every lesson both are set to 19 (`MOBTeacher.java:555-556`).
- "LIST" gets "Try the QUALIFY command." and "ALL" gets a refusal (`MOBTeacher.java:424-433`). Each side must be able to see the other (`MOBTeacher.java:434-443`).
- A taught ability starts at `(maxProficiency * PRACMAXPCT/100) / 2` (`MOBTeacher.java:550-553`). Expertises are matched by exact name and then by substring (`MOBTeacher.java:504-516`). Failures get "I'm sorry, but I've never heard of X" or "I do not know X" (`MOBTeacher.java:523-539`).

### 5. LEAVE (`Leave.java`)

- Riding: LEAVE dismounts through `riding.dismountString(mob)` and fires MSG_DISMOUNT (`Leave.java:55-56`, `113-121`).
- Not riding, with no argument: if exactly one visible, open exit leads to a real room, the character walks through it (`Leave.java:73-101`). Otherwise "Which way? Try EXITS." (`Leave.java:104`).
- Not riding, with a direction: the direction is resolved with `findCommand` and the direction verb runs (`Leave.java:60-66`).

### 6. KNOCK (`Knock.java`)

- `KNOCK <x>`. If the target names a direction, the verb knocks on that exit. It refuses if the exit has no door: "You can't knock on X!" (`Knock.java:59-85`). Otherwise it looks for an object in the room or the inventory (`Knock.java:62-71`).
- The message carries an MSP sound tag, `knock.wav`, at priority 50 (`Knock.java:68`, `86`, `93`, `114`).
- Sound propagation. The room on the far side of a door hears "You hear a knock on X" (`Knock.java:91-93`). For indoor rooms, the code walks outward through open paths up to depth 5 (`Knock.java:96-101`). Each room it reaches within that depth hears "You hear a knock <direction>." (`Knock.java:103-115`).

### 7. LINEWRAP (`LineWrap.java`)

- The width is stored per player (`LineWrap.java:52-58`). The default is 78 (`Common/interfaces/PlayerStats.java:53`).
- A number greater than 10 sets the width. `DISABLED` sets 0 and `DEFAULT` copies the default (`LineWrap.java:76-93`).
- When the default is in use, the output can show NAWS and the width the terminal negotiated (`LineWrap.java:57-68`). A cosmetic fault prints the number twice, as in "78Default (78)" (see Problems).

### 8. LLM (`LLM.java`, `CMProtocols.java`)

- **Gate.** Admin only. The caller needs a player record, the archon category model installed, and LISTADMIN (`LLM.java:89-95`).
- **Sessions.** `LLM <text>` makes a session the first time, or after `LLM reset`. The session holds up to 100 messages (`LLM.java:68-69`). Sessions are stored in memory by name and never removed in this file (`LLM.java:52`, `66-79`).
- **First message.** When a session is made, the typed text is replaced by "Greetings! My name is <name>!" (`LLM.java:70`). The first message the admin types is therefore not sent as typed.
- **Model.** The model is loaded by reflection from a LangChain4j jar at `LANGCHAIN4J_JAR_PATH`. The type comes from `LANGCHAIN4J_<CAT>LLM_TYPE`. The categories are ARCHON, MUDCHAT, PERCOLATOR, MOBPROG and LLMIFY (`coffeemud.ini:2219-2232`; `CMProtocols.java:4756-4789`). A blank path disables the feature. A failed setup is retried after 6 minutes (`CMProtocols.java:4750`). The model builder is set, through reflection and with failures ignored, to temperature 0.7 (`CMProtocols.java:4800-4809`) and a request timeout of 20 seconds (`CMProtocols.java:4811-4814`).
- **Rolling memory.** Before each call, if the window is nearly full, the code keeps the system message at index 0 and drops the first exchange. It copies everything from index 3 onward (`CMProtocols.java:5076-5084`). Replies lose a pair of surrounding quotation marks (`CMProtocols.java:5100-5106`).
- **Blocking.** `chat()` runs in the command thread (`LLM.java:79`). The 20-second timeout is set on the model builder. The chat wrapper adds no timeout of its own (`CMProtocols.java:5070-5096`).

### 9. LEFT (`Left.java`)

- `LEFT` moves west by default. `LEFT NORTH` and `LEFT SOUTH` move northwest and southwest (`Left.java:52-61`). Walking or running depends on the AUTORUN attribute (`Left.java:64-67`).
- Gate: monsters always, and players only when their current direction type is CARAVAN (`Left.java:78-85`).
- The caravan vocabulary is "Foreward, Backward, Right, Left, Up, or Down". The ship vocabulary is "Foreward, Aft, Starboard, Port, Above, or Below" (`core/Directions.java:122-125`). `Directions.DirType` has three values, COMPASS, SHIP and CARAVAN (`core/Directions.java:40-44`). `Right.java` is the mirror of this file and is outside the bucket.

### 10. LANGUAGES (`Languages.java`)

- With no argument, LANGUAGES lists the language abilities the character knows, with an optional level filter (`Languages.java:53-57`). The detail view for a named language does not work (see Problems).

### 11. LIST: dispatch and permissions (`ListCmd.java`)

- **Shopkeeper path.** `execute` (`ListCmd.java:7389-7471`) handles `LIST` and `LIST <name>` for shopkeepers. It sends a MSG_LIST message whose wording depends on the shop type. Bankers review an account, post offices check a postal box, and librarians review a catalogue (`ListCmd.java:7453-7461`). `LIST <name> FOR <text>` filters the review (`ListCmd.java:7409-7416`, `7462-7463`). If the name is not a shopkeeper and the caller has some LIST permission, the request goes to the admin dispatcher. Otherwise the reply is "You don't see anyone here buying or selling." (`ListCmd.java:7437-7447`).
- **Admin dispatcher.** `archonlist` (`ListCmd.java:6855-6992`) resolves the word through `getMyCmd` (`ListCmd.java:4468-4497`). An exact enum name is tried first. Then comes a prefix match in declaration order, and last come command journal names. A word is accepted only if the caller has one of the entry's security flags or LISTADMIN (`ListCmd.java:4472-4474`, `4482-4484`).
- **Permission table.** `ListCmdEntry` (`ListCmd.java:5280-5404`) has 122 entries. Each carries a `SecGroup` of flags. 62 entries include LISTADMIN. The other 60 are gated by content flags such as CMDMOBS, CMDROOMS, CMDABILITIES and JOURNALS. Some entries have aliases: SPACE also answers to BODIES, MOONS, STARS and SPACESHIPS, and SUPERPOWERS also answers to POWERS. The same enum decides which words the player sees in "You can list: ..." (`ListCmd.java:4448-4466`, `6902-6908`).
- **Help check.** The first time the dispatcher prints its list of usable words (after a bare LIST, LIST HELP, or an unknown word), it logs each usable word that has no `LIST_<WORD>` key in `arc_help.ini` (`ListCmd.java:6880-6888`). This is a built-in self-check.
- **Dispatch.** `archonlist` is one switch with about 120 cases (`ListCmd.java:6912-7385`). Most cases call a helper that returns text, and the result is printed through the session.
- **Formats.** The words `wiki`, `wikihelp`, `json`, `jsonhelp`, `jsonhelpl` and `jsonhelpl4000` are removed from the arguments (`ListCmd.java:99-137`). They select an exporter. Wiki output fills MediaWiki templates such as `{{RaceTemplate|...}}` (`ListCmd.java:699-732`). JSON lines are truncated so that no field exceeds 4000 bytes (`ListCmd.java:755-791`).
- **Column layout.** Widths come from `CMLib.lister().fixColWidth(width, session)`, so tables fit the player's terminal. Tables are built with `build3ColTable` or `build4ColTable`.

### 12. LIST: the mechanics that matter most

**(a) Zapper-mask world search (`getAreaStuff`, `ListCmd.java:886-1205`).** `LIST AREA <word> [text]` searches the rooms of the caller's area. The word can be ROOM, EXIT, ITEM, MOB, SHOP, or RESOURCE or TYPE (which prints the room resource table). It can also be `ITEMMASK`, `MOBMASK`, `ITEMMASK2` or `MOBMASK2`.
- `MOBMASK` and `ITEMMASK` compile the zapper mask once (`ListCmd.java:961`, `973`; `maskCheck(compiled, E, true)` at `1054`, `1080`).
- `MOBMASK2` and `ITEMMASK2` pass the raw mask text to `maskCheck(text, E, true)` for each object (`ListCmd.java:983-994`, `1060`, `1086`).
- Plain text is matched against the name, display text and description (`ListCmd.java:1013-1015`).
- Visibility is checked. The caller needs the WHERE flag for the room and access to the area (`ListCmd.java:1007-1009`). Cloaked mobs show only to callers with CLOAK or WIZINV and enough level (`ListCmd.java:488-498`, `1074`).
- Each line gives the category and name, the room, and for shop stock the price from `getShopPrice`. When no mob owns the shop, it creates a temporary seller and destroys it afterwards. It asks `CMLib.coffeeShops().sellingPrice` for the price and abbreviates the result. XP and QP prices are shown as "xp" and "qp" (`ListCmd.java:500-538`).

**(b) In-game query language (`LIST SELECT:`, `ListCmd.java:4345-4398`).** The rest of the line is passed to `CMLib.percolator().doMQLSelectObjects` with the area, or with no area when none is given. Each result row prints the object's ID, name and room (`ListCmd.java:4356-4370`). The help example is `list select: name from mobs where 'here' in name` (`arc_help.ini`, LIST_SELECT). With `LIST AREA SELECT:` the query is limited to the caller's area (`ListCmd.java:6976-6980`).

**(c) Command journals.** `LIST <JOURNAL>S` prints a journal's entries (`ListCmd.java:1928-1993`). The journal names come from the `COMMANDJOURNALS` setting (`coffeemud.ini:1496-1500`). The default set is:
- BUG, with CONFIRM and a script hook `progs/bugreport.script`.
- TYPO, which records the room.
- IDEA.
- TASK.
- STUCK, with channel echo to WIZINFO, expiry after 2 days and admin echo.

Other options are described in the comments above the setting (`coffeemud.ini:1466-1495`). They include ASSIGN categories, MOTD and REPLYALL. Callers without journal access see only entries addressed to their own name (`ListCmd.java:1966-1967`). Staff can filter by player name or by an ASSIGN category (`ListCmd.java:1950-1964`).

**(d) Tick timers (`listTicks`, `appendTick`, `ListCmd.java:3258-3440`).** The report walks each tick group's tickers. It prints group, ID (the low 8 bits of the ID, `ListCmd.java:3273`), client name, and a status column that defaults to the last code word. It supports `ACTIVE` (a ticker is active when its last stop is before its last start, `ListCmd.java:3263`) and `PROBLEMS [n]`. The problems view prints two tables, by total time and by average time, for the tickers that the system report marks as problems (`ListCmd.java:3356-3389`). Large numbers are shortened with k, m, b or g suffixes (`ListCmd.java:3285-3288`).

**(e) Threads and sessions (`ListCmd.java:1325-1511`).** `dumpThreadGroup` walks the thread-group tree. With the SHORT or ACTIVE word (`ListCmd.java:7171-7173`), it inspects only the top stack frame of each thread and treats the thread as idle if that frame is `sleep`, `park` or `wait` (`ListCmd.java:1340-1367`). Session threads show the last command typed (`ListCmd.java:1385-1388`). `LIST THREAD <name>` prints the stack of one thread (`ListCmd.java:1483-1511`).

**(f) Script inventory (`listScripts`, `ListCmd.java:1513-1745`).** The code walks every script host in every area and collects the external script files on each host and its shop items. Inline scripts are reported as "*Custom*". `COUNT`, `DETAILS` and `CUSTOM` produce different views. `ERRORS` scans the log for lines containing "error Scripting" (`ListCmd.java:1579-1593`).

**(g) Link analysis (`listLinkages`, `ListCmd.java:1747-1926`).** For each area, the code merges rooms into connected groups through their exits, then does the same for areas through cross-area exits. Groups of fewer than four areas are listed as "small groups" (`ListCmd.java:1911-1921`).

**(h) Ability taxonomy (`listAbilities`, `ListCmd.java:5941-6249`).** One argument can name an ability code (SPELLS, SKILLS and so on), a domain (with or without DOMAIN_), or a flag (with or without FLAG_). Words that name a code or domain set the filter. Words that name a flag add a bit test to the filter. The wiki export gives each ability's ID, domain, availability per class and level, expertises that it allows, cost, quality, targets, range, trigger words, usage and examples (`ListCmd.java:6010-6232`).

**(i) Race and class exports (`listRaces`, `listCharClasses`, `ListCmd.java:2510-2925`).** A race is described with its aging chart (`ListCmd.java:2669`), wear locations, racial and cultural abilities, and the classes it qualifies for. A class is described with a level-by-level table of abilities that it grants. Automatic grants are marked with `*` (`ListCmd.java:2856-2894`).

**(j) Space searches (`listSpace`, `ListCmd.java:3957-4267`).** `LIST SPACE` takes filters for all objects, bodies, moons, stars and ships. It also takes WITHIN, AROUND and ORDERBY clauses. Each object's type is named Star, Ship, Planet, Moon or Obj., using radius thresholds (`ListCmd.java:3908-3925`). The default search radius is the solar system radius plus 1,000,000 dm (`ListCmd.java:4240`). See Problems for the parsing faults.

**(k) Real estate and stocks.** `LIST REALESTATE AREA|WORLD|METRO [owner] [TAXES]` lists land titles with owners, lot IDs, prices and back taxes (`ListCmd.java:340-477`). `LIST STOCKS` reads the stock table, stored as the area section data `CMKTSTOCKS`, and hides bankrupt entries (`ListCmd.java:6445-6492`).

**(l) Help-request analytics (`listHelpFileRequests`, `ListCmd.java:3675-3724`).** The help log is scanned for "wanted help on" lines and the requests are counted per topic. The result is sorted by count.

**(m) Log viewer (`listLog`, `ListCmd.java:5167-5278`).** `FRONT n`, `BACK n` and `SKIP n` select a window of the log. A word filters the lines. Output pauses after the player's page length. The pause waits up to 10 minutes for a key (`ListCmd.java:5420-5440`).

### 13. LIST: the family table

Entries are grouped by what they report. The gates are the security groups in `ListCmdEntry`. Which staff roles hold each flag is set in the security configuration, which was not checked.

| Family | Entries (LIST word) | Gate | Who |
|---|---|---|---|
| Items and equipment | ITEMS, ARMOR, WEAPONS, MAGIC, TECH, CLANITEMS, SOFTWARE, MANUFACTURERS | CMDITEMS (CLANITEMS adds CMDCLANS; MANUFACTURERS adds LISTADMIN) | Builder |
| Mobs, rooms and exits | MOBS, ROOMS, AREA, LOCALES, EXITS, UNLINKEDEXITS, ONEWAYDOORS, ORPHANS, EXPIRES | CMDMOBS, CMDROOMS, CMDEXITS | Builder |
| Areas and world | AREAS, WORLD, PLANETS, SPACE (and aliases), SPACESHIPAREAS, SHIPS, CURRENTS, TIMEZONES, LINKAGES, CONQUERED, FILEUSE | CMDAREAS, CMDROOMS (+LISTADMIN) | Builder or admin |
| Resources and economy | MATERIALS, RESOURCES, ENVRESOURCES, AREARESOURCES, REALESTATE, STOCKS, RECIPES, COMPONENTS | CMDITEMS, CMDROOMS, CMDAREAS, LOADUNLOAD, CMDRECIPES | Builder or admin |
| Abilities and classes | ABILITIES, SPELLS, SONGS, PRAYERS, CHANTS, TRAPS, PROPERTIES, THIEFSKILLS, COMMON, SKILLS, SUPERPOWERS, DISEASES, POISONS, LANGUAGES, TECHSKILLS, ALLQUALIFYS, RACES, CLASSES, RACECATS, BEHAVIORS | CMDABILITIES, CMDRACES, CMDCLASSES, CMDMOBS and others | Builder |
| Taxonomy (codes, domains, flags) | ABILITYDOMAINS, ABILITYCODES, ABILITYFLAGS | LISTADMIN and CMDABILITIES | Admin |
| Scripts and content text | SCRIPTS, SOCIALS, EXPERTISES, TITLES, AWARDS, ACHIEVEMENTS, HOLIDAYS, QUESTS, QUESTNAMES, QUESTWINNERS, MISCHELP, MISCAHELP, TAGS | CMDMOBS and similar, or LISTADMIN | Builder or admin |
| People and organisations | USERS, ACCOUNTS, WHO, SESSIONS, BANKS, LIBRARIES, POSTOFFICES, CLANS, GOVERNMENTS, BANNED, NOPURGE, EXPIRED | CMDPLAYERS, STAT, CMDMOBS, CMDCLANS, BAN, NOPURGE | Admin |
| Journals and news | JOURNALS, COMMANDJOURNALS (and each journal name such as BUGS), NEWS, POLLS | JOURNALS, NEWS, POLLS | Staff |
| System and debug | LOG, THREAD, THREADS, TICKS, REPORTS, TRACKINGFLAGS, DBCONNECTIONS, SQL, SELECT:, HOSTS, CRON, DEBUGS, DEBUGFLAG(S), DISABLE*, ENABLEFLAG, MSGTYPES, MSGMASKS, PLANES, HELP, LIST, HELPFILEREQUESTS, STATS, COMMANDS, OBJCOUNTERS, WHO | LISTADMIN, plus CMDDATABASE for SQL and DBCONNECTIONS | Admin |

Notes on the table. `LIST WHO` runs the Who command (`ListCmd.java:7066-7077`). `OBJCOUNTERS` prints "NO LONGER AVAILABLE" (`ListCmd.java:7205-7207`). `LIST SQL` runs a raw SQL query. Backticks are turned into quotes first (`ListCmd.java:5456-5473`).

## Content worth mining

- **Ability classification codes** (`Abilities/interfaces/Ability.java:671-697`). The 14 codes are SKILL 0, SPELL 1, PRAYER 2, SONG 3, TRAP 4, PROPERTY 5, THIEF SKILL 6, LANGUAGE 7, CHANT 8, COMMON SKILL 9, DISEASE 10, POISON 11, SUPERPOWER 12 and TECH 13. The mask for the code field is 31 (`ALL_ACODES`). The domain is stored above those five bits, as `n << 5` (`Ability.java:722-726`).
- **Domains** (`Ability.java:722-884`, 82 constants). Examples by group:
  - Schools of magic: DIVINATION, ABJURATION, ILLUSION, EVOCATION, ALTERATION, TRANSMUTATION, ENCHANTMENT, CONJURATION.
  - Performance: SINGING, DANCING, PLAYING.
  - Thief: DECEPTIVE, DETRAP, STEALING, STEALTHY, TRAPPING, STREETSMARTS.
  - Priest: HOLYPROTECTION, HEALING, BLESSING, CURSING, EVANGELISM, COMMUNING.
  - Combat: WEAPON_USE, KICKING, PUNCHING, GRAPPLING, DIRTYFIGHTING, COMBATLORE, COMBATFLUIDITY, EVASIVE, MARTIALLORE.
  - Craft and nature: GATHERINGSKILL, CRAFTINGSKILL, PLANTGROWTH, PLANTCONTROL, ANIMALAFFINITY, BREEDING, ROCKCONTROL, NATURELORE, CALLIGRAPHY, POISONING, ARCANELORE, ACROBATIC, INFLUENTIAL, ARTISTIC, RACIALABILITY, ECONOMIC, TRAVEL, ROPEUSE.
- **Flags** (`Ability.java:946-1036`, 44 constants). Examples: BINDING, MOVING, TRANSPORTING, WEATHERAFFECTING, SUMMONING, CHARMING, TRACKING, HEATING, FIREBASED, HOLY and UNHOLY (NEUTRAL is both together), PARALYZING, NOORDERING, CLANMAGIC, HEALINGMAGIC, WATERBASED, AIRBASED, EARTHBASED, INTOXICATING, ADJUSTER, RESISTER, IMMUNER, ZAPPER, CASTER, ENABLER, UNCRAFTABLE, POTENTIALLY_DEADLY, AGGROFYING, TIDEALTERING, FEARING, DIVINING, POLYMORPHING.
- **Vehicle vocabularies** (`core/Directions.java:122-125`). The same words map to different directions depending on whether the vehicle is a caravan or a ship, and LEFT and RIGHT exist only in caravans.
- **Command journals as a feedback channel** (`coffeemud.ini:1466-1500`). Players submit bugs, typos, ideas and tasks. Each journal has its own rules: confirmation, a script hook, expiry, channel echo and category assignment. This is a complete feedback pipeline in configuration.
- **Teacher phrasing** (`MOBTeacher.java:379-419`). The teacher finds the keyword TEACH or GAIN anywhere in a sentence, then drops the words ME and PLEASE. "Please teach me X" and "teach me X" both work. The scan is crude, but it covers the common forms.
- **Social keys** (`ListCmd.java:4746`, `4761`). A social is stored as a family of entries: `HUG`, `HUG <T-NAME>` (for a target) and `HUG SELF`. Each entry holds the messages for the actor, the room, the target and the failed case.
- **Expertise ranks** (`ListCmd.java:4577-4578`, `4617-4623`). Ranks are stored as numbered definitions such as "Name I", "Name II", and the list groups them by base name. Requirements and costs are rendered from masks (`ListCmd.java:4550-4557`, `4629-4638`).
- **Achievement reward types** (`ListCmd.java:4906-4959`). ABILITY, CURRENCY, CLANCURRENCY, STAT, EXPERTISE, QP, NOPURGE, TITLE, TATTOO, XP, CLANXP, ITEM and MOB.
- **Recipes** (`ListCmd.java:3855-3899`). Common skills with recipe files are found by checking for a recipe format and a recipe file name.
- **Space type thresholds** (`ListCmd.java:3917-3920`). A body is a Planet when its radius exceeds the moon radius plus 10,000 dm, and a Moon otherwise.

## Notable design ideas

- **One enum drives everything about LIST.** `ListCmdEntry` gives each word its aliases, its permission groups, and its place in the "You can list" message. It also sets the prefix matching and the help-key check. A new report needs one line in the enum and one case in the switch.
- **The game documents itself.** The wiki and JSON exporters read live data and fill templates. Races, classes, abilities, expertises, socials, behaviors and areas all come out of the running game, so the wiki cannot drift from the code.
- **Masks as a query language.** Zapper masks give staff a filter language over mobs and items in the world. Compiling the mask once (`MOBMASK`) is the intended fast path. The per-object form (`MOBMASK2`) takes the raw text and evaluates it for each object, so it may re-parse the text each time (not checked in `MUDZapper.java`).
- **Command as NPC conversation.** LEARN makes the character say a line through `doCommand`, so the teacher's behaviour decides the outcome. The verb is only a front end, and content authors can change how teaching works without touching code.
- **Relative directions per vehicle.** The same command words mean different directions in caravans and ships. LEFT is available only where that vocabulary applies.
- **Sound propagation.** KNOCK tells the rooms around a door, walking outward through open exits to a fixed depth. The rule is simple and local, so a room can react to a sound from next door without any global event system.
- **Automatic re-wield.** KILL wields a held weapon before attacking. The player does not need a separate step.
- **Disambiguation by ordinal.** `KILL goblin.2` picks the second goblin. Followers are skipped when there is a choice. `KILL ALL DEAD` addresses each NPC by its exact context name.
- **Costs as fractions of a tick.** Each verb costs part of a tick, and combat costs a full one. This is an easy, tunable model of pacing.
- **Journals as a configuration language.** A short string such as `BUG CONFIRM SCRIPT=... CHANNEL=... EXPIRE=2` defines a whole feedback channel with its own rules.
- **Tick instrumentation.** Each ticker tracks its last start and stop times and total milliseconds, and `LIST TICKS PROBLEMS` ranks the slowest. The engine can therefore report its own hot spots.

## Problems and cruft

Everything below was found by reading the code. Nothing was executed, so each item should be checked before it is relied on.

**A. Wrong results, errors or crashes**

1. **Space coordinates are parsed into the wrong slot.** `ListCmd.java:4061-4063` writes `valL[i]`, where `i` is the command index, not the coordinate index `x`. The `if(i==2)` test then keeps only the last value. So `LIST SPACE AROUND 1,2,3` centres on (0,0,3). In `LIST SPACE ALL AROUND 1,2,3 WITHIN 5 DM`, `AROUND` is at index 2, so `i` becomes 3 and `valL[3]` throws `ArrayIndexOutOfBoundsException`.
2. **The last word of a space clause is dropped.** The loops test `end<commands.size()-1` (`ListCmd.java:4040`, `4095`, `4126`), and `CMParms.combine` is end-exclusive (`CMParms.java:364-373`). Consequences:
   - `WITHIN 100 LY` reads only `100`. That is a bare number, and `parseSpaceDistance` returns it as decametres (`EnglishParser.java:597-600`; `EnglishParsing.java:1115-1120`). The unit is silently dropped.
   - `WITHIN 5` with no unit fails with "no valid distance specified" (`ListCmd.java:4097-4100`).
   - `AROUND <name>` as the last word fails with "no coordinates or object specified" (`ListCmd.java:4036-4043`).
   - `ORDERBY <column>` as the last word fails (`ListCmd.java:4126-4130`).
3. **ORDERBY versus SORTBY.** The parser for LIST SPACE accepts `ORDERBY` only (`ListCmd.java:4119`). The help text and the example "list planets all sortby name" use SORTBY (`arc_help.ini`, LIST_SPACE). SORTBY is parsed only by LIST AREAS (`ListCmd.java:6510`).
4. **LIST SPACESHIPS prints the usage text.** The alias routes to `listSpace` with the word SPACESHIPS. None of the filter codes starts with that word, so no filter is set (`ListCmd.java:3965-3968`, `3906`, `5373`). LIST SHIPS works, because it is its own entry (`ListCmd.java:5381`).
5. **ORDERBY COORDSY sorts by X.** The test is `if (x == 7)` (`ListCmd.java:4191-4193`). It compares the command index, not the column. COORDSX falls into the three-key coordinate sort, so it sorts by X, then Y, then Z.
6. **LIST STARS selects the wrong objects.** The filter uses `isOnFire` (`ListCmd.java:3985-3987`; `Libraries/Sense.java:1417`). The type column uses `isLightSource` (`ListCmd.java:3911`; `Sense.java:1053`). The filter selects burning objects, while the type column calls light sources Stars. The two sets differ.
7. **LIST CONTENTS with a room ID lists the current room.** The lookup finds `R` (`ListCmd.java:5039`) and then uses `mob.location()` instead (`ListCmd.java:5041`).
8. **LIST COMMANDS with a player name ignores the player.** `whoM` is assigned (`ListCmd.java:5587-5588`) and never read again. The help says the player's list is shown (`arc_help.ini`, LIST_COMMANDS).
9. **A malformed behaviour help block stops the whole behaviour list.** The `break` at `ListCmd.java:6310-6311` is inside the loop over behaviours.
10. **Alignment for unholy abilities.** `ListCmd.java:6200-6202` tests `FLAG_HOLY` twice, so "Evil" is never printed. An ability with only FLAG_UNHOLY shows no alignment. FLAG_NEUTRAL is HOLY and UNHOLY together (`Ability.java:964-968`), and `CMath.bset` requires every bit of the mask (`core/CMath.java:722-723`).
11. **Ability code labels are off by one.** `Ability.java:709-710` lists "ARCHON SKILL" at index 13, but `ACODE_TECH` is 13 (`Ability.java:697`). "TECH SKILL" sits at index 14, which has no constant. Following the code: `LIST ABILITIES TECH` finds index 14 (`ListCmd.java:5956-5958`, `CMParms.java:4336-4345`) and lists no abilities. `LIST ABILITIES ARCHON` lists tech skills. `LIST ABILITYCODES` labels code 13 as ARCHON_SKILL.
12. **LANGUAGES with a name never shows the detail.** The call passes `ACODE_SPELL` (`Languages.java:53`). `parsedOutIndividualSkill` shows detail only when the ability's code is in the list (`Skills.java:111-112`), and otherwise returns false (`Skills.java:156`). A language is never shown in detail, and the full list is printed.
13. **LIST AREA EXIT (singular) never matches.** The check compares the word to `"EXIT "`, with a trailing space (`ListCmd.java:921`). Only EXITS works.
14. **The mask echo shows the keyword.** `LIST AREA MOBMASK ...` prints "Mask used:" followed by the word MOBMASK, not the mask (`ListCmd.java:960`, `972`).
15. **LIST ENVRESOURCES filters case-sensitively for resource names.** The text is not lower-cased before it is compared with the capitalised resource name (`ListCmd.java:4316-4318`).
16. **LIST USERS LOADED.** With no sort key, the E-Mail column shows the character name, because `getSortValue` returns the name when the code is null (`ListCmd.java:2280`; `Libraries/CMPlayers.java:1842`). Sorting by IP does nothing, because the sort loop is skipped for IP (`ListCmd.java:2233`).
17. **LIST ACCOUNTS can throw.** When the joined character-name string is 21 to 24 characters long and has no comma in its first 21 characters, `substring(0,24)` runs past the end (`ListCmd.java:2467-2474`). This needs a name of that length, and name-length limits were not checked.
18. **LIST CLASSES SHORT prints nothing.** `SHORT` stays in the arguments and becomes a name filter (`ListCmd.java:2734-2739`). LIST RACES removes it first (`ListCmd.java:2514-2515`).
19. **LIST LINKAGES output.** The small-group line uses `V.get(0)` inside the loop over `v` (`ListCmd.java:1917`), so the first area's name is repeated. The group count is printed before the empty groups are removed (`ListCmd.java:1904-1909`).
20. **LIST SCRIPTS ERRORS uses fixed offsets.** `substring(16)` and `substring(38)` assume a log layout (`ListCmd.java:1588-1589`). A short error line throws.
21. **LIST SESSIONS numbering.** The `#` column counts only rows shown, and rows are skipped under the NAME sort (`ListCmd.java:6786-6800`). The `LIST SESSIONS <n>` lookup counts every session (`ListCmd.java:6747-6766`). The two numberings disagree when unnamed sessions exist.
22. **LIST A resolves to ARMOR.** Prefix matching uses declaration order, and ARMOR is declared before ABILITYDOMAINS (`ListCmd.java:5284-5285`, `4476-4489`). Short words are therefore silently resolved.
23. **A bare LIST gives admins "List what?".** Callers with any LIST permission go straight to the admin dispatcher, which rejects the empty argument (`ListCmd.java:7395-7401`, `6857-6861`). A named shopkeeper still works.
24. **LIST SQL is open to CMDDATABASE alone.** The entry's only flag is CMDDATABASE (`ListCmd.java:5380`). The query is passed to `DBRawQuery` unchanged (`ListCmd.java:5461`).
25. **LLM drops the first message and never clears sessions.** See section 8 above (`LLM.java:52`, `66-79`).
26. **Go's combat cost reads the wrong setting.** `Go.combatActionsCost` reads `DEFCMDTIME`, not `DEFCOMCMDTIME` (`Go.java:270-276`). LEFT inherits this. This is outside the bucket and is noted here only because LEFT depends on it.
27. **The help-request count is encoded confusingly.** A topic that has help text gets the count -(n+1) for n requests (`ListCmd.java:3692-3703`). The list is then sorted ascending (`ListCmd.java:3714`; `core/collections/DVector.java:149-185`). Topics with help come first, and the most-requested topic that lacks help comes last. The output is not a clean ranking.

**B. Cosmetic faults and dead code**

- LINEWRAP prints the number twice, as in "78Default (78)" (`LineWrap.java:66`, `68`).
- LIST REPORTS calls `System.gc()` and sleeps for 1.5 seconds inside a player command (`ListCmd.java:2000-2001`).
- OBJCOUNTERS is a dead entry (`ListCmd.java:7205-7207`).
- `shortenNumber` truncates, because integer division comes before `Math.round` (`ListCmd.java:3933`).
- `truncateLongestField` removes the last over-limit field, not the longest (`ListCmd.java:764-765`).
- The expertise match in LEARN tests the same condition twice (`Learn.java:107-108`).
- `LIST SPACE AROUND <name>` calls `findSpaceObject` twice with identical arguments (`ListCmd.java:4069-4071`).

**C. Privacy and multiplayer assumptions**

- Staff listings show account e-mail addresses and last IP addresses (`ListCmd.java:2381-2389`, `6762`). They are staff-only, and only the kind of data is described here.
- Most of LIST assumes other humans: USERS, ACCOUNTS, SESSIONS, WHO, BANKS, post offices, clan governments, journals addressed to players, and KILL's follower rules.

## Takeaways for a Python SUD

- **Verb registry.** Keep one table per verb with its name, aliases, a cost in turns, a combat cost, and a role. Drop `canBeOrdered` unless the game has companions.

  ```python
  @dataclass(frozen=True)
  class Verb:
      name: str
      aliases: tuple[str, ...] = ()
      cost: float = 0.2      # fraction of a turn, outside combat
      combat_cost: float = 1.0
      role: Role = Role.PLAYER
  ```

- **Kill.** Borrow the target syntax (`goblin.2`), auto-wield before attacking, and the rule that a retarget costs an action. Drop the follower preference and the instant-kill admin paths, except as a developer tool.
- **Learn.** Make teaching an intent. The verb produces "ask trainer about X", and the trainer's rules decide. A small intent parser is enough. The keyword scan in MOBTeacher is simple, but a parser that handles "teach me", "learn" and "train in" will be more reliable.
- **Knock and noise.** Implement sound as a breadth-first walk through open exits with a depth limit of 5. Use it for alarms, footsteps and knocks.
- **LIST as a report registry.** Keep one function per report, keyed by name, with a role check. A small developer set is enough: rooms, mobs, items, abilities, races, classes, scripts, unlinked exits and orphans. Generate Markdown tables from the same registry, not MediaWiki.
- **Filters.** Parse a mask such as `level>10 race=elf` into a predicate once, at the start of the command, and reuse it for each object. Do not evaluate the raw string per object.
- **Taxonomy as enums.** Use `Enum` for the ability kinds and domains, and `IntFlag` for the flags. Keep the names and values in one place. Do not pack the kind and domain into one integer with shifts. The label mismatch in Problems item 11 comes from keeping the names in a separate array from the constants.
- **Vehicle vocabularies.** Store word sets as data: compass, ship, caravan. Only the vehicle the player is in changes the direction names.
- **Feedback journals.** A `feedback` command that appends a line to a JSONL file, tagged by category, replaces the journal system. A `list feedback` view reads it back. A config string like `BUG CONFIRM EXPIRE=2` is a readable way to set these rules.
- **Query.** A read-only `find` command over the content, or a SQL view over a SQLite copy of it, covers the MQL use. Do not expose raw writes.
- **Timing reports.** Use `time.perf_counter()` around each system and print the slowest on demand. This replaces the ticker and thread reports.
- **Drop.** USERS, ACCOUNTS, SESSIONS, WHO, LINKAGES across areas and the threads and DB reports. Keep STOCKS if a market simulation exists, because it is the only market-style simulation in this bucket.
- **Keep.** Per-player wrap width, as a setting read by `textwrap`. The NAWS idea does not apply to a local console.

## Coverage

- **Read in full.** All ten files in the bucket. `ListCmd.java` (7,491 lines) was read end to end in chunks.
- **Read in part, for the lines cited.** `StdCommand.java`, `Go.java`, `Skills.java`, `MOBTeacher.java` (337-562), `CMProtocols.java` (4739-5125 and the model setup at 4750-4814), `CMSecurity.java` (SecFlag, 908 and 969), `CMProps.java` (1143-1196), `CMParms.java` (combine, 235-400), `core/Directions.java`, the `Ability` constants, `EnglishParser.java` (585-610), `DVector.java` (149-185), `CMPlayers.java` (`getSortValue`), `Sense.java` (`isOnFire`, `isLightSource`), `coffeemud.ini` (the command, journal and LLM sections), and the `LIST_*` entries in `arc_help.ini`.
- **Skipped.** The bodies of the library calls that these commands delegate to: combat (`CMLib.combat`), tracking and the world hunt, masking, the percolator and MQL, the database, journals, achievements and the wiki template files. Help text beyond the `LIST_*` keys cited above.
- **Executed.** Nothing. All bug claims come from reading the code and should be checked before they are relied on.
