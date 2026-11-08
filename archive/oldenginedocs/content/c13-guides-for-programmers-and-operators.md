# Guides for programmers and operators

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `c13` (content: Guides for programmers and operators) · 100 files · 11,624,947 bytes of data.

Citation convention: `guides/...` paths are relative to the CoffeeMud folder. `guides/*.html:N` is the line number in the HTML source, not in a text rendering. Java paths are relative to `com/planet_ink/coffee_mud/`, except `fakedb/...` (relative to `com/planet_ink/`) and the full `com/planet_ink/coffee_web/...` path. Counts for the reference catalogues come from scripts that parse the generated HTML; where a count is a parse result rather than a direct reading, the text says so. Where a guide and the code disagree, the code is treated as correct and the guide is cited as the claim.

## Summary

This bucket is CoffeeMud 5.11's own documentation for people who program or run a server: a 6,000-line programmer's guide, an installation and settings guide for `coffeemud.ini`, guides for the web, SMTP, CM1, proxy and client protocols and the MUDGrinder admin tool, nine SQL schemas, a flat-file schema, and a JSON changelist that upgrades the database. Most of the bytes (about 10.8 MB of 11.6 MB) are 79 reference pages generated from the running game's classes, listing every player class, race, spell, prayer, chant, song, skill, expertise, behaviour, property and help topic. For a single-player Python game the most useful parts are the catalogues (48 classes with stat formulas, 11 races, about 1,950 ability entries with domains and quality tags), the message-with-preview model, the layered stat recalculation, and the word-length pseudo-language. Several guide passages no longer match the code, so they should not be copied without checking. The operator material is for a multiplayer server and can be dropped.

## Key files and types

Guides and data (sizes in bytes, from the manifest):

| File | Role | Size |
|---|---|---|
| `guides/Programming.html` | Programmer's guide 5.11: messages, state layers, ticks, libraries, input, DB tables, commands, content classes, skills, spells, common skills, poisons, diseases, traps, languages | 348,622 |
| `guides/InstallationGuide.html` | Install, upgrade, `coffeemud.ini` settings, multi-host, proxy, integrations (Discord, LLM, X), database setup, localization | 120,012 |
| `guides/WebServer.html` | Web servers, `web/*.ini` options, CMVP macros, JavaScript pages, servlets, WebSocket packets | 85,173 |
| `guides/Protocols.html` | Client protocols: ASCII, ANSI, TELNET, MCCP, MCP, MSP, MXP, MSDP, GMCP, MSSP, MPCP, Grapevine, Discord, X, LLM | 50,970 |
| `guides/SMTPServer.html` | Mail server and journal mailing lists | 12,225 |
| `guides/MUDGrinder.html` | Web admin tool (control panel, editors, file browser) | 9,489 |
| `guides/CM1Server.html` | CM1 control socket (line protocol) | 6,191 |
| `guides/database/changelist.json` | Schema history: 59 version steps, 299 operations | 32,435 |
| `guides/database/fakedb.schema` | Flat-file database schema, 25 tables | 3,915 |
| `guides/database/fakedb2.schema` | Same 25 tables with sizes and CLOB types; no code path names it | 4,126 |
| `guides/database/coffeemuddb*.sql` (9 files) | Manual DDL per engine (generic, Access, Derby, HSQL, MS SQL, MySQL, Oracle, PostgreSQL, DB2) | 113,598 for the nine files |
| `guides/database/dropalltables.sql`, `README` | 25 DROP statements; a readme for the folder | 476, 821 |
| `guides/refs/` (79 files) | Generated reference pages (see below) | 10,836,894 |
| `guides/images/` (61 files) | Images for the guides; not in this manifest | not counted |

Generated reference pages (`guides/refs/`):

| File(s) | Content | Size |
|---|---|---|
| `help.html` | Player command and topic help (261 command entries, 265 other topics, parsed) | 459,180 |
| `ahelp.html` | Archon command and topic help (201 command entries, 53 other, parsed) | 237,876 |
| `skills.html` | Common and racial skills (about 790 entries, parsed) | 657,862 |
| `skillse.html` | Expertises: 1,369 tiers in 146 families | 793,155 |
| `skillss.html` | Spells: 414 | 341,705 |
| `skillsp.html` | Prayers: 331 | 254,367 |
| `skillsc.html` | Chants: about 310 | 232,314 |
| `skillsd.html` | Songs, dances and plays: 110 | 73,618 |
| `affbehav.html` | Properties, affects, traps and bombs, diseases: 534 entries | 343,260 |
| `behav.html` | Behaviours: 96 | 146,359 |
| `pclass_*.html` (48) | One page per player class | 7,264,082 |
| `prace_*.html` (11) | One page per race | 24,260 |
| `pclasstab.html`, `pracetab.html`, `pclasses.html`, `praces.html`, `nav.html`, `intro.html`, `pnoclass.html`, `pnorace.html`, `pclass.html`, `prace.html` | Frame and navigation pages; `pclass.html` and `prace.html` are 0 bytes | small |

Code that builds, reads or describes these files:

| File | Role | Lines |
|---|---|---|
| `application/MUD.java` | Boot: loads `guides/database/changelist.json` (1485), reads `RUNWEBSERVERS` (1562) and `RUNSMTPSERVER` (1576) | 2,471 |
| `core/database/DDLValidator.java` | Turns the changelist into a schema: constructor (59), type mapping (108), version check (501), create (1014), upgrade (1061) | 1,257 |
| `core/database/DBConnections.java` | Connection pool; PostgreSQL identifier quoting (551, 1108); Derby shutdown stub (766) | 1,168 |
| `fakedb/backend/Backend.java` | Reads and writes `fakedb.schema` (readSchema 149, readRawSchema 692) | 1,115 |
| `fakedb/backend/structure/FakeTable.java` | Column definition parser (about 385 to 445) | 1,219 |
| `fakedb/backend/structure/FakeColumn.java` | Column type enum (INTEGER, STRING, LONG, DATETIME, CLOB, BLOB) | 90 |
| `fakedb/DBCopy.java` | Copies data between two fakedb schema versions; honours `#CONVERTER` (254) | 916 |
| `application/RebuildRefDocs.java` | Boots the MUD and runs the reference rebuild macro (60 to 135) | 135 |
| `WebMacros/RebuildReferenceDocs.java` | Runs every `web/admin/work/*.cmvp` template and writes `guides/refs/*.html` (59 to 98) | 99 |
| `WebMacros/AddFile.java` | `TEMPLATE=` / `NEW=` macro that writes one page per class or race (45 to 95) | 131 |
| `WebMacros/CharClassData.java` | Supplies the class fields used by the class template | 1,442 |
| `WebMacros/RaceNext.java`, `Libraries/CharCreation.java` | Race list filter; `isAvailableRace` at `CharCreation.java:363` | 5,394 (CharCreation) |
| `Libraries/MUDHelp.java` | Help loader: every `resources/help/*.ini` except `ARC_*` (`getHelpFile` 1756) | 2,104 |
| `Libraries/CMProtocols.java` | Protocol library: `!!SOUND` (905), LangChain4J wiring (4756 to 4840), MSSP variables (4496) | 5,149 |
| `application/MUDProxy.java` | MPCP proxy; HMAC-SHA1 keys (76, 84) | 2,521 |
| `WebMacros/WebSockJSON.java` | JSON WebSocket: packet enum (88), config keys (148 to 216) | 393 |
| `Libraries/CoffeeLevels.java` | Level-up gains, including the attack bonus (`getAttackBonusNextLevel` 93) | 1,746 |
| `core/CMLib.java`, `core/CMProps.java`, `core/threads/ServiceEngine.java` | Library accessor (380), tick constants (681, 683), tick scheduler (349) | 1,785; 3,965; 1,871 |
| `Common/interfaces/CMMsg.java` | Message codes and masks (944 to 970), minor types (938, 1283) | 1,684 |
| `Common/interfaces/Session.java` | Player I/O, blocking and callback prompts (422, 433, 490, 519) | 1,557 |
| `Commands/interfaces/Command.java`, `Commands/StdCommand.java` | Command API | 181; 316 |

## How it works

### 1. The engine model in the programmer's guide

The programmer's guide is the best map of the engine's internal design. It is also partly out of date, so each mechanic below is marked where the code differs.

#### Messages

Every event is a `CMMsg` (`Common/interfaces/CMMsg.java`) with a source MOB, an optional target and an optional tool, and three sets of code and text: one for the source, one for the target and one for everyone else in the room (`guides/Programming.html:872` to `:1000`). The sender builds the message, offers it to the room's `okMessage` (preview), and then calls `send` so that every listener executes it. The room methods exist as the guide describes: `StdRoom.okMessage` (`Locales/StdRoom.java:745`), `send` (1734), `sendOthers` (1742), `showHappens` (1749, 1758) and the `show` shortcut (1772). A `false` from any listener cancels the message. The guide adds that a message can carry trailer messages that run only after the host message succeeds (`guides/Programming.html:1304`).

The code field is split into a major part (bit flags) and a minor part (type). The masks are at `Common/interfaces/CMMsg.java:944` to `:970`: `MASK_HANDS=2048`, `MASK_MOVE=4096`, `MASK_EYES=8192`, `MASK_MOUTH=16384`, `MASK_SOUND=32768`, `MASK_ALWAYS=65536`, `MASK_MAGIC=131072`, `MASK_DELICATE=262144`, `MASK_MALICIOUS=524288`, `MASK_CHANNEL=1048576`, `MASK_OPTIMIZE=2097152`, `MASK_CNTRLMSG=4194304`, `MASK_INTERMSG=8388608`. The guide's table stops there; the code also has `MASK_SPAMMY=16777216` (`CMMsg.java:970`). The minor type is limited by `MINOR_MASK=2047` (`CMMsg.java:938`), and types 2000 to 2047 are reserved for channels (`CMMsg.java:931`, `:1283`).

Messages are built with `CMClass.getMsg` overloads (`core/CMClass.java:3707` to `:3791`). The guide's first example calls `CMClass.newMsg` (`guides/Programming.html:281`), which does not exist in `CMClass.java` (no match in the file).

#### Stat layers

Physical objects keep a base state and a current state. The base is changed by the object itself. The current state is rebuilt from the base by a `recover` call that copies the base and then lets each relevant effect modify the copy (`guides/Programming.html:1325` to `:1360`). For items, `basePhyStats` is at `Items/Basic/StdItem.java:212`, `recoverPhyStats` at `:219`, and the effect hook `affectPhyStats` at `:625`. For MOBs, `recoverCharStats` (`MOBS/StdMOB.java:926`), `resetToMaxState` (1039) and `recoverMaxState` (1047) do the same job for the character stats and the hit, mana, movement, hunger, fatigue and thirst values. The racial code in `Races/Elf.java` shows the pattern on both sides: `affectCharStats` adds dexterity +1, constitution -1 and magic save +10 (lines 152 to 154), and `unaffectCharStats` removes exactly the same amounts (lines 161 to 163). The guide says this paired undo is needed because a Polymorph-type effect changes race in the middle of a recalculation (`guides/Programming.html:4425`).

Key fields of `PhyStats` and the `STAT_*` codes of `CharStats` are listed at `guides/Programming.html:1353` and `:1549`.

#### Ticks

The default tick is 4,000 milliseconds: `TIME_TICK = 4000` at `core/CMProps.java:681`. The derived `TICKS_PER_RLMIN` is `round(60000 / TIME_TICK)`, which is 15 ticks per real minute (`core/CMProps.java:683`). Every object that implements `tick(Tickable, int)` receives calls with a tick ID. `Tickable.TICKID_MOB` is `0` (`core/interfaces/Tickable.java:82`). A MOB ticks every 4 seconds, and its behaviours and effects tick with it. Items, exits and rooms tick only while they have behaviours (`guides/Programming.html:1615` to `:1700`). Returning `false` from `tick` stops further calls. Objects can be scheduled with `startTickDown(obj, tickID, numTicks)` (`core/threads/ServiceEngine.java:349` to `:351`). The guide calls this through `CMClass.threads()` and `CMClass.ThreadEngine()` (`guides/Programming.html:1688`, `:1706`, `:1711`); the accessor that exists is `CMLib.threads()` (`core/CMLib.java:380`).

The guide's rule of thumb is one action per tick for a normal player, and two in combat under the default combat system (`guides/Programming.html:663`).

#### Libraries and the CMLib accessor

Engine services are singletons reached through `CMLib` (`core/CMLib.java`). The guide lists 52 library rows, each with a class name, an interface name and an accessor (`guides/Programming.html:1833` to `:2230`). Examples: `combat()` returns `Libraries/MUDFight.java` (3,817 lines), `flags()` returns the sensory and disposition helper, `map()` finds rooms and areas, and zapper masks are in `Libraries/MUDZapper.java` (another bucket). The guide also lists 13 core utility classes (`CMath`, `CMParms`, `CMProps`, `CMStrings` and others, `guides/Programming.html:1729`) and 33 common data classes such as `DefaultMessage`, `DefaultPhyStats` and `DefaultCharStats` (`guides/Programming.html:2230` onward).

#### Input without blocking

`Session.prompt(String, String)`, `confirm` and `choose` block the thread until the player answers (`Common/interfaces/Session.java:422`, `:490`, `:519`). The guide says this wastes a server thread and recommends the callback form `prompt(InputCallback)` (`Session.java:433`), whose callback runs when input arrives (`guides/Programming.html:2416` to `:2525`). A MOB without a session (every NPC) cannot be prompted at all (`guides/Programming.html:2432` to `:2437`).

#### Commands

A command implements `Commands/interfaces/Command.java` (`StdCommand.java` is the usual base). The interface returns access words, action costs (`actionsCost`, `combatActionsCost`), whether an order can be given (`canBeOrdered`), a security check, a pre-execute hook for waiting, and `execute` (`guides/Programming.html:3400` to `:3490`). The guide gives the package as `com.planet_ink.coffee_mud.core.interfaces.Command` (`guides/Programming.html:3416`); the file is in `Commands/interfaces/`.

#### Content classes

The guide walks through how to write each kind of object: MOBs (`guides/Programming.html:3491`), items (3659), behaviours (3860), character classes (3979), races (4280), exits (4469), locales (4504), areas (4559) and properties (4627). The worked examples are `NormalGuy` (a class, around line 3996), `Grumbler` (a race, line 4294), `Ravenous` (a behaviour that eats food in the room, line 3873) and `Prop_AstralSpirit` (a property that makes a MOB incorporeal, line 4666). The pattern is the same for all of them: a class with an `ID()` that matches its file name, a constructor that sets base stats and then calls `recoverPhyStats`, and overrides of `okMessage`, `executeMsg` and `tick` for the behaviour. The guide also notes that a class can extend a standard base (`StdMOB`, `StdItem`, `StdRace`) or a generic one (`GenMob`, `GenItem`); the generic ones are edited at run time, at the cost of memory and load time (`guides/Programming.html:682`, `:727`).

Class formulas are text strings with variables (`@x1` to `@x9`). For the Mage (`CharClasses/Mage.java`), hit points use `((@x6<@x7)/6)+(1*(1?5))` (line 104), mana `((@x4<@x5)/3)+(1*(1?4))` (line 110) and movement `3*((@x2<@x3)/18)` (line 98). The class reference page prints these as "(Con/6)+(1d5) per level", and so on. Level-up attack gain is the attack attribute divided by 18, rounded down, plus the class bonus; an extra 1 is added at 22 and an extra 2 at 25 (`Libraries/CoffeeLevels.java:102` to `:108`). The guide says it is divided by 6 (`guides/Programming.html:4065`), which is wrong.

#### Abilities: quality, flags and invoke

Every spell, skill, prayer, chant, song, poison, disease and trap is an `Ability`. The guide's control points are:

- `abstractQuality()` says whether the ability is always good for the caster, always good for others, malicious, or only sometimes useful. The guide says only the beneficial and malicious values are used in combat or by most behaviours (`guides/Programming.html:4797` to `:4880`).
- `castingQuality()` refines that for one invoker and target at one time (`guides/Programming.html:4836`).
- `flags()` marks the effect as moving, binding, transporting, summoning, charming, tracking, heating, burning, holy or unholy, paralysing and so on (`guides/Programming.html:4885` to `:4940`).
- `invoke(mob, commands, givenTarget, auto, asLevel)` does the work. The usual shape is: pick a target, call `super.invoke` (which charges mana or movement), run `proficiencyCheck`, send a message through the room, and apply `beneficialAffect` or `maliciousAffect` (`guides/Programming.html:5006` to `:5100`).

The guide's Trip example is a good illustration of the first step and of where the guide has fallen behind. Its level adjustment is a simple formula, `(target level - mob level) * 5` plus a dexterity term (`guides/Programming.html:5036`). The current `Abilities/Skills/Skill_Trip.java` also subtracts a per-ability term and an extra-level term (`Skill_Trip.java:243` to `:249`):

```java
int levelDiff=target.phyStats().level()-(mob.phyStats().level()+(2*getXLEVELLevel(mob)));
...
levelDiff-=(abilityCode()*mob.charStats().getStat(CharStats.STAT_DEXTERITY));
final int adjustment=(-levelDiff)+(-(35+((int)Math.round((target.charStats().getStat(CharStats.STAT_DEXTERITY)-9.0)*3.0))));
```

#### Spells, prayers, chants and songs

Spells extend `Abilities/Spells/Spell`, and their `classificationCode()` adds one of eight domains: Divination, Abjuration, Illusion, Evocation, Alteration, Transmutation, Enchantment and Conjuration (`guides/Programming.html:5120` to `:5160`). Prayers must set `FLAG_HOLY` or `FLAG_UNHOLY` (both gives neutral), so alignment is data on the ability (`guides/Programming.html:5183`). Chants fix a mana cost with `overrideMana` (the guide's `Chant_AlterTime` returns 100, `guides/Programming.html:5220`). Songs extend `Song`, `Dance` or `Play`, which handle group targeting and the requirement that the performer stays in the room (`guides/Programming.html:5251`). `Song_Protection` uses `affectCharStats` to raise saving throws by four times the singer's charisma and lowers dexterity by 1 (`guides/Programming.html:5268` to `:5290`).

#### Common skills: gathering and crafting

Common skills run over time. The skill adds itself as an effect on the player, receives `tick` calls, and produces its result in `unInvoke` (`guides/Programming.html:5294`). Gathering skills extend `Abilities/Common/GatheringSkill` and crafting skills extend `CraftingSkill` (`guides/Programming.html:5306`, `:5449`). The current `Abilities/Common/Fishing.java` tells the player "Nothing is biting around here." when the tick counter reaches 6 with nothing caught (`Fishing.java:104`, `:110`). Its duration is computed by `getDuration` (`Fishing.java:262`), not by the `35 - level` expression in the guide (`guides/Programming.html:5436`).

Crafting recipes are rows in a tab-separated file under `resources/skills/`. The guide gives the nine columns as constants `RCP_FINALNAME` to `RCP_SPELL` (`guides/Programming.html:5483`). `resources/skills/glassblowing.txt` has 88 rows of nine tab-separated fields, for example `glass cup`, level 1, 20 ticks, 2 units of the required material, class type `GenEmptyDrink`, a liquid capacity of 5. The third column is named for wood in the guide but holds the amount of any material. The file is read by name through `loadRecipes` (`guides/Programming.html:5528` shows the method; the guide's own example file is `glassblowing.txt`). Recipe catalogues are covered by chapters c04 and c05.

#### Poisons, diseases and traps

Poison and disease templates are base classes with overridable constants: `POISON_TICKS`, `POISON_DELAY`, `POISON_DAMAGE`, and the text strings. `Poison_BeeSting` uses 10 ticks and a 2-tick delay (`Abilities/Poisons/Poison_BeeSting.java:59` to `:68`); its damage is `1 + rank` when there is an invoker, not the constant `2` the guide shows (`Poison_BeeSting.java:101` to `:104`; guide at `guides/Programming.html:5726`). `Disease_Cold` uses 24 ticks and a 5-tick delay (`Abilities/Diseases/Disease_Cold.java:90` to `:99`). When one tick of the disease remains, it may give the player Pneumonia, after saving throws (`Disease_Cold.java:139` to `:146`). The example trap can sit on an item or an exit (`Trap_ElectricShock.java:52` to `:54`). It is armed only with a material requirement (`Trap_ElectricShock.java:70` to `:73` asks for "10 pounds of metal"), and when sprung it deals `roll(trapLevel()+abilityCode(), 8, 1)` damage (`Trap_ElectricShock.java:137`). The guide gives `roll(trapLevel(), 8, 1)` (`guides/Programming.html:5914`).

#### Languages

A language is a `StdLanguage` subclass with two tables. The first is an exact-word map from English to the invented word (`Elvish.java:88` on; about 520 entries). The second is a list of word lists keyed by word length, from one to six letters, with 8 to 25 invented words in each list (`Elvish.java:68` to `:73`). `StdLanguage.translate` (`Abilities/Languages/StdLanguage.java:212`) looks up the exact word first, returns player names unchanged, and otherwise picks a word from the list for the word's length. The pick is `choices[abs(word.toLowerCase().hashCode()) % choices.length]` (`StdLanguage.java:234`), so the same English word always gives the same invented word. The guide says the word is chosen at random (`guides/Programming.html:5976`), which is wrong. There are 59 files under `Abilities/Languages/`, including the animal speech languages and special ones such as `Drunken.java` and `Encrypto.java`.

### 2. Database: schema, changelist and upgrade

The guide explains the design history: first the object data lived in Java classes, then in XML blobs, and then in queryable tables for journals, polls, forums and virtual files (`guides/Programming.html:2525` to `:2585`). It gives the reason for the cryptic names: table and column names are short so that they do not collide with reserved words in any engine. The 25 tables are, in the guide's words:

| Table | Purpose |
|---|---|
| `CMVFS` | Virtual (database) file system, one row per file |
| `CMCHAB` | Character abilities, behaviours and effects (one row each) |
| `CMCHCL`, `CMCLAN`, `CMCLIT` | Clan membership, clans, clan items |
| `CMCHAR`, `CMCHFO`, `CMCHIT` | Characters, followers, character inventory |
| `CMROOM`, `CMROCH`, `CMROEX`, `CMROIT` | Rooms, room mobs, room exits, room items |
| `CMAREA`, `CMADAT` | Areas, and extra area data for behaviours and effects |
| `CMQUESTS` | Quest definitions or references to quest files |
| `CMJRNL` | Journals, forums and email |
| `CMPDAT`, `CMPOLL` | Player and clan data sections; polls |
| `CMSTAT` | Daily statistics |
| `CMGRAC`, `CMCCAC`, `CMGAAC`, `CMCDAC` | Generic race, class, ability and command definitions |
| `CMACCT` | Optional accounts |
| `CMBKLG` | Channel backlog |

The schema is described by `guides/database/changelist.json`, which is a map from a version number to a list of operations. The file has 59 versions (1 to 8 and 10 to 60; there is no version 9) and 299 operations: 262 adds, 30 modifies, 4 deletes and 3 moves. Targets are columns (229), keys (38), tables (25), indexes (6) and rows (1). Two examples show the style. Version 38 creates `CMCHCL` and moves the clan name and clan role columns from `CMCHAR` into it. Version 60 creates `CMADAT` and moves rows for the sections `ARREST`, `CONQITEMS`, `TIMECLOCK`, `ARTIFACTS`, `SCRIPTABLEVARS` and `LIBRARY_RECORDS` out of `CMPDAT` (the `where` clause is in the file).

The boot path is `application/MUD.java:1485` to `:1501`, which parses the file and passes it to `DDLValidator`. The constructor fills each version index, and a missing index becomes an empty list (`core/database/DDLValidator.java:59` to `:83`), which is why version 9 is harmless. `getDatabaseVersionCode` finds the last applied version (`:501`). Version 0 triggers `createDatabase` (`:1014`); otherwise `upgradeDatabaseVersion` (`:1061`) generates the DDL for the pending operations. `getPortableType` (`:108` to `:150`) maps JDBC types to the four portable types INT, LONG, VARCHAR and TEXT, plus TIMESTAMP and DATETIME.

`fakedb.schema` is the default database's schema, and its grammar is simple. A group is a table name line, optionally followed by a version suffix such as `V2`, and then one line per column: `NAME TYPE [NULL|KEY|INDEX] [size]`. Groups are separated by blank lines (`fakedb/backend/Backend.java:692` to `:742` for the reader, `:149` to `:187` for the table loader). A `#VERSION n` line sets the default table version (`Backend.java:228` to `:253`), and a `#CONVERTER=` line names the class that `DBCopy` uses to transform data between schema versions (`fakedb/DBCopy.java:254`). Column parsing is in `fakedb/backend/structure/FakeTable.java` (about lines 385 to 445), and the types are an enum in `FakeColumn.java:23`.

Checks run for this chapter, by script rather than by reading each file:

- The column names in `fakedb.schema` match the cumulative result of applying the changelist for all 25 tables.
- All nine SQL files match the changelist's column names, except that `coffeemuddbmssql.sql` has no `CMCDAC` table and `coffeemuddbpsql.sql` has no `CMPDAT` table.
- `fakedb2.schema` has the same 25 tables and column names as `fakedb.schema`, but it adds explicit sizes (`string KEY 50`) and uses `clob` for long text. Nothing in the code or the guides refers to it by name.

PostgreSQL needs special handling. `DBConnections.setupPostGres` (`core/database/DBConnections.java:551` to `:570`) reads the first word of every non-comment line of `fakedb.schema` as an identifier, and `fixIdentifiers` (`:1108` to `:1165`) double-quotes any matching word in every SQL statement before it runs. This keeps the upper-case names created by `DDLGenerator` (which quotes names) in step with the unquoted names in the code.

### 3. Reference pages: how they are built

The reference pages are generated from the running game, so they describe the code and the data rather than a copy of either. The build works in four steps.

1. `application/RebuildRefDocs.java` boots the server with the admin web server set up as the rebuild target and runs the `RebuildReferenceDocs` macro (`:60` to `:135`).
2. `WebMacros/RebuildReferenceDocs.java` (`:59` to `:98`) runs the CMVP filter over each of the 14 templates in `web/admin/work/` and saves the result as `guides/refs/<name>.html`, rewriting `.cmvp` links to `.html`.
3. The templates that have one page per entity call `@AddFile?TEMPLATE=/web/admin/work/pclass.cmvp&NEW=/guides/refs/pclass_@@CharClassID@@.html@` inside the navigation loop (`web/admin/work/pclasstab.cmvp:24`; the race version is at `web/admin/work/pracetab.cmvp:19`). `AddFile` runs the template for each entity and saves it (`WebMacros/AddFile.java:45` to `:95`).
4. The field macros read the live objects. `CharClassData` prints class fields, `AbilityNext` and `AbilityData` list each ability with its type, domain and qualification, `ExpertiseNext` lists expertises, `LevelNext` gives the level rows, and `ClassRaceNext` lists races that qualify (`web/admin/work/pclass.cmvp`).

The navigation list for races uses `RaceNext`, which keeps a race only if `CharCreation.isAvailableRace` accepts it (`WebMacros/RaceNext.java:59`; `Libraries/CharCreation.java:363` to `:372`). That check rejects skill-only races unless they are enabled in the security settings.

Helpers: the help pages come from `HelpTopics`, which reads `resources/help/*.ini` through `MUDHelp.getHelpFile` (`Libraries/MUDHelp.java:1756` to `:1775`; files starting with `ARC_` go to the Archon file). The behaviour and property pages iterate the `AbilityAffectNext` and `AbilityData` macros (`web/admin/work/affbehav.cmvp`).

### 4. Operator configuration and protocols

The main settings file is `coffeemud.ini`. The guide documents its most important keys (`guides/InstallationGuide.html:323` to `:543`). The values in the shipped file and the code that reads them are:

| Key | Shipped value | Read by |
|---|---|---|
| `PORT` | 5555 (`coffeemud.ini:25`) | server boot |
| `RUNWEBSERVERS` | `pub,admin` (`:66`) | `MUD.java:1562` |
| `RUNSMTPSERVER` | `TRUE` (`:70`) | `MUD.java:1576` |
| `RUNCM1SERVER`, `CM1CONFIG` | `TRUE`, `/web/cm1.ini` (`:195`, `:200`) | `Libraries/IntermudClient.java:293` to `:296` |
| `DBCLASS`, `DBSERVICE` | `fakedb`, `jdbc:fakedb:resources/fakedb` (`:142`, `:143`) | database connector |
| `HASHPASSWORDS` | `YES` (`:225`) | account code |
| `SYSMSGS`, `ERRMSGS`, `WRNMSGS`, `DBGMSGS` | `BOTH` (`:1938` to `:1941`) | logging |
| `HLPMSGS`, `ACCMSGS` | `OWNFILE`, `OFF` (`:1942`, `:1945`) | logging |
| `MPCPKEY` | a placeholder string (`:33`; value not reproduced) | `application/MUDProxy.java` |
| `MSXPVARS` | `GENRE=Fantasy,GAMEPLAY=Adventure,GAMEPLAY=Social` (`:1765`) | `Libraries/CMProtocols.java:4496` |
| `LANGCHAIN4J_*` | blank (`:2215` to `:2231`) | `Libraries/CMProtocols.java:4756` to `:4840` |

The web servers are configured in `web/common.ini`, `web/pub.ini` and `web/admin.ini`. Their ports are 27744 and 80 for the public site (`web/pub.ini:8`) and 27777 for admin (`web/admin.ini:8`). The mail server listens on 25 (`web/email.ini:8`) and the CM1 socket on 27733 (`web/cm1.ini:4`).

Protocol and integration notes that matter for a design study:

- MSP sound output is a line of the form `!!SOUND(name V=.. P=..)` built in `CMProtocols.java:905` to `:906`. The guide says sounds go in `/resources/web/pub/sounds/` (`guides/Protocols.html:388`); the shipped folder is `web/pub/sounds/`.
- MXP is driven by the file `resources/text/mxp.txt`, which the guide says is sent once negotiation succeeds (`guides/Protocols.html:396`).
- MPCP, the proxy control protocol, signs each packet with HMAC-SHA1 and a shared key (`application/MUDProxy.java:76`, `:84`), and the guide says each JSON packet carries a timestamp against replay (`guides/Protocols.html:905`; not checked in code).
- The WebSocket JSON interface has seven packet types (`login`, `connect`, `logout`, `error`, `input`, `text`, `config`) and seven config keys (`asciionly`, `linefeeds`, `msp`, `msdp`, `mxp`, `gmcp`, `telnet`). Both lists match the code (`WebMacros/WebSockJSON.java:88` to `:216`).
- The LLM integration uses LangChain4J. `LANGCHAIN4J_LLM_TYPE` picks a provider, and each feature category (`ARCHON`, `MUDCHAT`, `PERCOLATOR`, `MOBPROG`, `LLMIFY`) can override it. Builder methods are read by reflection: a key such as `LANGCHAIN4J_<CATEGORY>_<METHOD>` maps to a method on the provider's builder (`Libraries/CMProtocols.java:4817` to `:4835`). The guide's provider table is at `guides/InstallationGuide.html:800` onward.
- The web macro language (CMVP) runs macros between `@` signs with `?` parameters. Macros can nest by adding `@` levels. A page can also run Rhino JavaScript between `@jscript@` and `@/jscript@` (`guides/WebServer.html:1969`).

### 5. Admin and web surfaces

MUDGrinder is the administration web app. It has a control panel for engine settings, editors for races, classes, abilities, recipes, holidays, titles, socials, clans, factions, deities and the catalogue, a file browser that works on both the local folder and the database file system, a journal browser, a player manager, quest and poll managers, system reports and statistics (`guides/MUDGrinder.html:25` to `:137`). The guide's section "Not Your Mothers MUDGrinder" says it is "not forgiving" (`guides/MUDGrinder.html:150`). Nothing in it is game logic, but its editors show which fields are considered data (the same fields the reference pages print).

## Content worth mining

### Player classes (48 pages)

Every class page prints the same fields. The table shows each class's prime stat, its qualification (minimum stats to join), and the per-level formulas for hit points, mana and attack. Base values are the same for every class: 20 hit points, 100 mana and 100 movement, plus the per-level term shown. Movement per level is `N * (Str/18)` with N from 3 (casters such as Mage and Enchanter) to 10 (Gypsy). The last column counts the ability rows parsed from each page (per-level rows on the page, not distinct abilities).

| Class | Prime | Qualifies at | Hit points per level | Mana per level | Attack per level | Ability rows on page |
|---|---|---|---|---|---|---|
| Abjurer | Int | Int 9+ | (Con/6)+(1d5) | (Int/3)+(1d4) | +(Int/18) | spells 154, skills 11, common 66 |
| Alterer | Int | Int 9+ | (Con/6)+(1d5) | (Int/3)+(1d4) | +(Int/18) | spells 153, skills 11, common 66 |
| Apprentice | Wis | Wis 5+, Int 5+ | (Con/9)+(1d4) | (Int/10)+(1d2) | +(Wis/18)-1 | skills 3, common 13 |
| Arcanist | Dex | Dex 9+, Int 9+ | (Con/3)+(1d10) | (Int/5)+(1d3) | +(Dex/18) | spells 18, thief 11, skills 15, common 68 |
| Artisan | Wis | Str 9+, Dex 9+ | (Con/6)+(1d5) | (Int/10)+(1d2) | +(Wis/18)-1 | thief 3, skills 40, common 108 |
| Assassin | Dex | Dex 5+, Wis 5+ | (Con/3)+(1d10) | (Int/6)+(1d3) | +(Dex/18) | thief 25, skills 28, common 65 |
| Barbarian | Str | Str 9+, Con 9+ | (Con/2)+(2d7) | (Int/8)+(1d2) | +(Str/18) | thief 1, skills 49, common 67 |
| Bard | Cha | Cha 9+ | (Con/3)+(2d6) | (Int/6)+(1d2) | +(Cha/18) | spells 1, thief 8, skills 20, common 65 |
| Beastmaster | Con | Dex 9+, Con 9+ | (Con/2)+(2d7) | (Int/4)+(1d4) | +(Con/18) | chants 34, thief 3, skills 25, common 68 |
| Burglar | Dex | Dex 9+, Cha 9+ | (Con/3)+(1d10) | (Int/6)+(1d3) | +(Dex/18) | spells 2, thief 36, skills 16, common 66 |
| Cavalier | Str | Str 9+, Dex 9+ | (Con/2)+(2d6) | (Int/8)+(1d2) | +(Str/18) | chants 1, thief 3, skills 71, common 67 |
| Charlatan | Cha | Cha 9+, Wis 9+ | (Con/3)+(2d6) | (Int/3)+(1d6) | +(Cha/18) | spells 3, prayers 1, chants 1, songs 5, thief 6, skills 26, common 65 |
| Cleric | Wis | Wis 9+ | (Con/3)+(1d10) | (Int/4)+(1d4) | +(Wis/18) | prayers 104, skills 10, common 65 |
| Conjurer | Int | Int 9+ | (Con/6)+(1d5) | (Int/3)+(1d4) | +(Int/18) | spells 153, skills 11, common 66 |
| Delver | Con | Str 9+, Con 9+ | (Con/2)+(2d6) | (Int/4)+(1d4) | +(Con/18) | chants 39, skills 10, common 65 |
| Diviner | Int | Int 9+ | (Con/6)+(1d5) | (Int/3)+(1d4) | +(Int/18) | spells 155, skills 11, common 67 |
| Doomsayer | Wis | Str 9+, Wis 9+ | (Con/3)+(1d10) | (Int/4)+(1d4) | +(Wis/18) | prayers 38, skills 10, common 65 |
| Druid | Con | Con 9+ | (Con/2)+(2d7) | (Int/4)+(1d4) | +(Con/18) | chants 96, skills 12, common 69 |
| Enchanter | Int | Int 9+ | (Con/6)+(1d5) | (Int/3)+(1d4) | +(Int/18) | spells 156, skills 11, common 66 |
| Evoker | Int | Int 9+ | (Con/6)+(1d5) | (Int/3)+(1d4) | +(Int/18) | spells 153, skills 11, common 66 |
| Fighter | Str | Str 9+ | (Con/2)+(2d7) | (Int/8)+(1d2) | +(Str/18) | skills 52, common 64 |
| Gaian | Con | Wis 9+, Con 9+ | (Con/2)+(2d7) | (Int/4)+(1d4) | +(Con/18) | chants 43, skills 13, common 65 |
| Gaoler | Str | Str 5+, Dex 5+ | (Con/6)+(1d5) | (Int/10)+(1d2) | +(Str/18)-1 | prayers 1, thief 5, skills 24, common 27 |
| Gypsy | Dex | Dex 9+, Con 9+ | (Con/6)+(1d5) | (Int/10)+(1d2) | +(Dex/18) | chants 12, thief 16, skills 20, common 66 |
| Healer | Wis | Wis 9+, Cha 9+ | (Con/3)+(1d10) | (Int/4)+(1d4) | +(Wis/18) | prayers 39, skills 7, common 65 |
| Illusionist | Int | Int 9+ | (Con/6)+(1d5) | (Int/3)+(1d4) | +(Int/18) | spells 155, skills 11, common 66 |
| Jester | Cha | Cha 9+, Dex 9+ | (Con/3)+(2d6) | (Int/6)+(1d3) | +(Cha/18) | spells 2, songs 13, thief 9, skills 27, common 65 |
| Mage | Int | Int 9+ | (Con/6)+(1d5) | (Int/3)+(1d4) | +(Int/18) | spells 205, skills 11, common 66 |
| Mer | Con | Int 9+, Dex 9+ | (Con/2)+(2d5) | (Int/4)+(1d6) | +(Con/18) | chants 42, thief 1, skills 16, common 66 |
| Minstrel | Cha | Cha 9+, Int 9+ | (Con/3)+(2d6) | (Int/6)+(1d2) | +(Cha/18) | songs 24, thief 1, skills 17, common 66 |
| Missionary | Wis | Wis 9+, Dex 9+ | (Con/3)+(1d10) | (Int/4)+(1d4) | +(Wis/18) | prayers 34, skills 9, common 66 |
| Monk | Str | Str 9+, Dex 9+ | (Con/3)+(2d7) | (Int/8)+(1d2) | +(Str/18)+1 | thief 4, skills 43, common 64 |
| Necromancer | Wis | Wis 9+, Con 9+ | (Con/3)+(1d10) | (Int/4)+(1d4) | +(Wis/18) | prayers 35, skills 9, common 65 |
| Oracle | Wis | Wis 9+, Int 9+ | (Con/3)+(1d10) | (Int/4)+(1d4) | +(Wis/18) | prayers 38, skills 11, common 66 |
| Paladin | Str | Wis 9+, Str 9+ | (Con/2)+(2d6) | (Int/8)+(1d3) | +(Str/18) | prayers 30, skills 52, common 64 |
| Pirate | Dex | Dex 5+, Cha 5+ | (Con/3)+(1d10) | (Int/6)+(1d3) | +(Dex/18) | prayers 1, songs 2, thief 28, skills 26, common 65 |
| Prancer (shown as Dancer) | Cha | Cha 9+, Str 9+ | (Con/3)+(2d6) | (Int/6)+(1d2) | +(Cha/18) | thief 3, skills 22, common 64 |
| Purist | Wis | Wis 9+, Cha 9+ | (Con/3)+(1d10) | (Int/4)+(1d4) | +(Wis/18) | prayers 40, skills 8, common 65 |
| Ranger | Str | Str 9+, Int 9+ | (Con/2)+(2d6) | (Int/7)+(1d3) | +(Str/18) | chants 29, thief 2, skills 49, common 67 |
| Reliquist | Dex | Dex 9+, Wis 9+ | (Con/3)+(1d10) | (Int/5)+(1d3) | +(Dex/18) | prayers 41, thief 18, skills 24, common 66 |
| Sailor | Dex | Con 5+, Dex 5+ | (Con/6)+(1d5) | (Int/9)+(1d2) | +(Dex/18)-1 | thief 1, skills 17, common 25 |
| Scholar | Int | Int 9+, Wis 6+ | (Con/9)+(1d3) | (Int/9)+(1d2) | +(Int/18)-1 | thief 4, skills 34, common 66 |
| Shaman | Wis | Wis 9+, Con 9+ | (Con/3)+(1d10) | (Int/4)+(1d4) | +(Wis/18) | prayers 47, skills 10, common 68 |
| SkyWatcher | Con | Int 9+, Con 9+ | (Con/2)+(2d7) | (Int/4)+(1d4) | +(Con/18) | chants 44, thief 1, skills 16, common 68 |
| Templar | Wis | Wis 9+, Str 9+ | (Con/3)+(1d10) | (Int/4)+(1d4) | +(Wis/18) | prayers 40, thief 1, skills 20, common 65 |
| Thief | Dex | Dex 9+ | (Con/3)+(1d10) | (Int/6)+(1d3) | +(Dex/18) | spells 3, thief 36, skills 23, common 66 |
| Transmuter | Int | Int 9+ | (Con/6)+(1d5) | (Int/3)+(1d4) | +(Int/18) | spells 153, skills 11, common 66 |
| Trapper | Dex | Dex 9+, Con 9+ | (Con/3)+(1d10) | (Int/6)+(1d3) | +(Dex/18) | spells 2, thief 27, skills 21, common 68 |

Patterns worth noting: the caster families (Mage, Conjurer, Diviner, Enchanter, Evoker, Illusionist, Transmuter, Abjurer, Alterer) share the same hit, mana and attack formulas and have similar spell-row counts (153 to 156 each; Mage 205). The Mage page itself calls these sub-classes of Mage (`guides/refs/pclass_Mage.html`, description). Each class is defined by its prime stat, its qualification and its per-level formulas. The class pages say "Dancer" where the class ID is `Prancer` (`CharClasses/Prancer.java:40` and `:43`).

### Races (11 pages)

Stat adjustments are as printed on each race page. "Save" entries are saving-throw bonuses; "max" entries adjust the maximum.

| Race | Stat changes | Racial abilities (proficiency) | Bonus language | Life |
|---|---|---|---|---|
| Aarakocran | dex +2, con -1, cha -1; save vs overlooking +50 | Eagle Eyes 50, Winged Flight 100, Hunting 50 | Bird Speak 100 | 45 years |
| Centaur | str +1, int +1, cha -1, wis -1 | infravision, Buck 50, Hardiness 100, Foraging 50 | Elvish 75 | 78 years |
| Duergar | con +1, cha -4; save vs fire +15, cold -15, water -10, mind +5, poison +10, disease -10 | darkvision, Mining 50, Invisibility 25, Grow 25 | Dwarven 100, Undercommon 25 | 290 years |
| Dwarf | con +1, cha -1; save vs poison +10 | infravision, Stonecunning 100, Mining 50 | Dwarven 100 | 290 years |
| Elf | dex +1, con -1; save vs magic +10 | infravision, Keenvision 100, Foraging 50 | Elvish 100 | 430 years |
| Gnome | int +1, wis -1; save vs mind +10, overlooking +10 | infravision, Burrowspeak 100, Gem Digging 50, Burrow Hide 25 | Gnomish 100 | 260 years |
| Half Elf | save vs justice +5, magic +5 | infravision, Fishing 50, Cultural Adaptation 50 | Elvish 75 | 137 years |
| Half Ogre | str +3, int -1, cha -1, wis -1 | Butchering 35 | Gigantic 50 | 78 years |
| Halfling | str -1, dex +1; save vs paralysis +10, detection +10 | infravision, Cooking 75, Sling Proficiency 25, Fast Slinging 25 | Elvish 25 | 120 years |
| Human | save vs justice +10; trains +2 | Diligent Studying 100, Wood Chopping 50 | none | 78 years |
| Pixie | str -6, dex +7, con -7, cha +2; save vs poison +10, magic +50 | infravision, Winged Flight 100, Foraging 50, Improved Invisibility 100 | Fey 100 | 430 years |

The Elf entry matches the code: `Races/Elf.java:152` to `:154` (dex +1, con -1, magic save +10), with the infravision sense at line 145 and Keenvision as a racial effect (line 166). The pages for Aarakocran, Centaur and Pixie are not in the navigation list (see Problems). Centaur and Pixie are skill-only races (`Races/Centaur.java:186` to `:188`, `Races/Pixie.java:97`), and skill-only races are offered at character creation only when they are enabled (`Libraries/CharCreation.java:363` to `:372`).

### Abilities

The reference pages list every ability with a quality tag, a domain, a mana, movement or other cost, a target type, a range, the commands to use it, an example and a paragraph of description. The counts below are parsed from the pages.

| Family (page) | Entries | Quality tags | Largest domains |
|---|---|---|---|
| Spells (`skillss.html`) | 414 | Circumstantial 140, Malicious 126, Always Beneficial 82, Sometimes Beneficial 66 | Enchantment/charm 61, Divination 56, Invocation/evocation 53, Alteration 50, Illusion 49, Conjuration 48, Abjuration 47, Transmutation 46, Cosmology 4 |
| Prayers (`skillsp.html`) | 331 | Circumstantial 101, Malicious 88, Always Beneficial 74, Sometimes Beneficial 68 | Communing 49, Holy protection 36, Blessing 29, Cursing 26, Death lore 25, Evangelism 23, Neutralization 23, Restoration 22, Healing 21, Creation 20, Corruption 19, Vexing 17 |
| Chants (`skillsc.html`) | about 310 | Circumstantial 102, Malicious 82, Sometimes Beneficial 68, Always Beneficial 57 | Plant growth 33, Weather mastery 31, Animal affinity 27, Shape shifting 27, Plant control 27, Preserving 26, Enduring 26, Deep magic 25, Breeding 16, Moon altering 15 |
| Songs, dances and plays (`skillsd.html`) | 110 | Always Beneficial 56, Malicious 36, Sometimes Beneficial 13, Circumstantial 5 | Playing 41, Singing 39, Dancing 30 |
| Skills (`skills.html`) | about 790 | Circumstantial 323, Malicious 120, Sometimes Beneficial 96, Always Beneficial 26; 222 with no tag (presumably passive or automatic; not checked) | Crafting 47, Weapon use 45, Martial lore 42, Animal affinity 41, Nature lore 37, Stealthy 34, Influential 29, Street smarts 25, Gathering 25, Sea travel 23, Evasive 23 |
| Expertises (`skillse.html`) | 1,369 in 146 families | (not a tagged family) | Most families have ten tiers, I to X |
| Properties, affects, traps, diseases (`affbehav.html`) | 534 | (see below) | About 120 properties, 79 traps (including the bomb traps), 66 diseases, 50 skill-type affects, 34 prayer-type affects |
| Behaviours (`behav.html`) | 96 | (not tagged) | Most target MOBs (63 entries) |

Sample entries, paraphrased from the pages (cost is the figure printed for the example level):

| ID | Domain | Quality | Cost | Notes |
|---|---|---|---|---|
| `Spell_Fireball` | Invocation/evocation | Malicious | Mana 57 | Targets creatures; range 1 to 5; a ball of fire |
| `Spell_AcidArrow` | Conjuration | Malicious | Mana 54 | Range from touch to range 2; extra acid damage each round |
| `Spell_ResistFire` | Abjuration | Always Beneficial | Mana 57 | Resistance, not immunity, to heat attacks |
| `Prayer_Anger` | Evangelism | Malicious | Mana 66 | Starts a fight among the inhabitants of a room of peace, except the caster |
| `Chant_AlterTime` | Moon summoning | Circumstantial | Mana 100 | Moves the area's time forward or back one hour; see `CMLib.map().getFirstArea().tickTock` in the guide |
| `Song_Protection` | Singing | Always Beneficial | Mana 65 | Group protection from certain magic and elements, at a small cost to dexterity |
| `Skill_Trip` | Dirty fighting | Malicious | Movement 51 | Puts the target on the floor for a short time |
| `Fishing` | Gathering | Circumstantial | Movement 26 | Catch fish in water; can bundle resources |
| `GlassBlowing` | Crafting | Circumstantial | Movement 30 | Sand to glass in a room with an open fire |

An expertise is a modifier with its own requirements. The page for `ACROBAT1` ("Acrobatic I") says it requires "a skill of type Acrobatic", a class level of 28 or more in the highest class, and a base dexterity of 13 (`skillse.html`, entry for Acrobatic I). Tier II adds a prerequisite of tier I and raises the thresholds to 33 and 14. The mechanism is a set of "Allows" lines on each ability (for example, Spell_Fireball allows "Ranged In/Evoking I, Reduced In/Evoking I, Ignalist I"), so every expertise is a named modifier applied to a family of abilities.

Behaviours on the page include several NPC roles: Aggressive, Arrest, Bardness, Clericness, CombatAbilities, Concierge, Conquerable, CorpseEater, Wimpy and WimpyAggressive, with `ActiveTicker` as a base class that the page says not to use directly (`behav.html`).

### Pseudo-language

The Elvish language is the easiest mechanic to borrow. The exact-word table gives the important words (about 520 entries in `Elvish.java`). Everything else is translated by length: a three-letter English word becomes a three-letter invented word, chosen deterministically from the list for that length by the word's hash (`Abilities/Languages/StdLanguage.java:212` to `:236`, `Elvish.java:68` to `:73`). A listener who does not speak the language still sees the right number of "words" and can guess the shape of a sentence, which the guide says is intended (`guides/Programming.html:5983`).

### Message vocabulary

The guide lists the message tags used across the engine (`guides/Programming.html:291` to `:340`). The useful families are: subject and object pronouns for the source and target (`<S-HIS-HER>`, `<T-HIM-HER>`), name forms (`<S-NAME>`, `<T-NAMESELF>`, `<S-NAMENOART>`), verb inflection (`attack(s)`, `reach(es)`, `fumble(s)`), and an observer-sensitive "you" (`<S-NAME>` becomes "You" when the viewer is the source). Colour codes are written as `^r` (red), `^!` (bold), `^f` (you-fight), `^S` (spell) and so on (`guides/Programming.html:438` to `:520`). The source also uses named tags such as `^<FIGHT^>...^</FIGHT^>` (`Libraries/MUDFight.java:531`), which the guide does not document.

### Adult and violent content in the reference data

The reference pages and the guide describe some adult and violent systems. They are described here neutrally, and no explicit text is reproduced.

- `Spell_Torture` (`guides/refs/skillss.html:2044`): an illusion-domain malicious spell that makes the target feel prolonged pain while taking damage. If others are present, they may also yell out information about themselves or their clan.
- `Chant_SensePregnancy` (`guides/refs/skillsc.html:1374`): a druid chant that tells whether a target is pregnant and how far along, with more detail at higher expertise. The related `Pregnancy` property is at `guides/refs/affbehav.html:2879`; its implementation is in `Abilities/Misc/Pregnancy.java` (813 lines, another bucket).
- `Disease_Chlamydia` (`guides/refs/affbehav.html:1964`): spreads sexually, halves movement, and causes infertility and reduced interest in sex.
- `Addictions` (`guides/refs/affbehav.html:1649`): governs addiction to food, drink, smoking and snuff.
- `Torturesmithing` is a crafting skill (listed at `guides/refs/skills.html:2215`; `guides/refs/skillse.html` names it as one of the prerequisite skills for an armour-crafting expertise). Its recipes are in `resources/skills/torturesmith.txt`, and drug-making recipes are in `resources/skills/drugs.txt` (both named in `guides/InstallationGuide.html`); neither file was read here.
- `REMORTING` (`guides/refs/help.html`) describes "legacy remorting" through children: two characters create a child with a social (MATE), a pregnancy follows, and birth comes some months later in game time. The entry describes a mechanic and contains no explicit content.

A keyword search of the bucket for sexual, pregnancy, drug, addiction and torture terms found the entries above and a few plain mentions in the same files. No content in the bucket places children in a sexual context. Each of these systems would need deliberate design if reused.

## Notable design ideas

- **Preview, then execute.** A proposed event is offered to every listener, any of which can veto it, and only then is it applied (`guides/Programming.html:1123` to `:1176`; `Locales/StdRoom.java:745`, `:1734`). The same mechanism handles damage, movement, speech and spells, so a single place can enforce rules such as "an incorporeal creature cannot make sounds" (the guide's `Prop_AstralSpirit` example, `guides/Programming.html:4666`).
- **Stats are recomputed, not mutated.** Base values stay fixed and the current values are rebuilt from base plus effects each time (`StdItem.java:219`, `StdMOB.java:926`). Equipment and spells can change stats without bookkeeping, and removing an effect cannot leave a stale value.
- **Abilities are tagged.** The quality tag (malicious, beneficial, circumstantial) lets NPCs decide what to cast without knowing each ability (`guides/Programming.html:4797`). The domain tag groups abilities for the player and for the help system.
- **Expertises as modifiers.** A named modifier with requirements applies to a whole family of abilities (`guides/refs/skillse.html`; the "Allows" lines in each ability entry). It gives advancement without new spells.
- **Recipes as data.** Crafts are rows in a tab-separated file with named columns (`resources/skills/glassblowing.txt`; `guides/Programming.html:5483`). The same file is editable by builders and read by the code with no new Java.
- **Generic versus standard objects.** Every object type has a fast compiled version and a data-driven version, with the trade-off stated (`guides/Programming.html:682`, `:727`).
- **Schema history as data.** The database changelist is a list of operations per version, applied in order at boot, with row moves for reorganisations (`guides/database/changelist.json`; `DDLValidator.java:1061`).
- **Self-documenting reference.** The reference pages are regenerated from the running game (`application/RebuildRefDocs.java:60` to `:135`), so the numbers in them are the numbers the game uses.
- **Zapper masks.** A small eligibility language such as `-LEVEL +>10 -RACE +Elf` decides who may subscribe to or post on a journal (`guides/SMTPServer.html`, JOURNALS parameters). The same language is used elsewhere in the engine (`Libraries/MUDZapper.java`, another bucket).

## Problems and cruft

Guide statements that disagree with the code (the code is the authority):

- The attack-bonus divisor is 18, not 6 (`guides/Programming.html:4065`; `Libraries/CoffeeLevels.java:102`).
- The Trip level formula omits the extra-level and ability terms (`guides/Programming.html:5036`; current code at `Abilities/Skills/Skill_Trip.java:243` to `:249`).
- Fishing's duration is no longer `35 - level` (`guides/Programming.html:5436`; `Abilities/Common/Fishing.java:262`).
- Poison damage is `1 + rank` with an invoker, not 2 (`guides/Programming.html:5726`; `Abilities/Poisons/Poison_BeeSting.java:101` to `:104`).
- Trap damage uses `trapLevel()+abilityCode()` (`Abilities/Traps/Trap_ElectricShock.java:137`), not `trapLevel()` as the guide shows (`guides/Programming.html:5914`).
- Elvish words are chosen by hash, not at random (`guides/Programming.html:5976`; `Abilities/Languages/StdLanguage.java:234`).
- The guide's accessor names `CMClass.newMsg`, `CMClass.threads()` and `CMClass.ThreadEngine()` do not exist; the real ones are `CMClass.getMsg` (`core/CMClass.java:3707`) and `CMLib.threads()` (`core/CMLib.java:380`) (`guides/Programming.html:281`, `:1688`, `:1706`, `:1711`).
- The guide's package for `Command` is wrong (`guides/Programming.html:3416`).
- The guide's table of contents names "Core Topic 2: State Layers" and "Core Topic 3: Threading"; the sections are headed "The State of Things" (`:1325`) and "Tick Tock" (`:1615`).
- The anchor for "Core Topic 6" has the id `DIG5`, duplicating section 5 (`guides/Programming.html:2525`).
- The guide's colour table does not document the `^<TAG^>` form used by the code (`Libraries/MUDFight.java:531`).
- `guides/WebServer.html:1929` says the error page is `errorpage.mwhtml`. The configured and shipped name is `errorpage.cwhtml` (`guides/WebServer.html:298`; `web/pub.templates/errorpage.cwhtml`; `com/planet_ink/coffee_web/util/CWConfig.java:53`).
- `guides/SMTPServer.html:88` says the mail server is enabled by `RUNWEBSERVERS=true`. The code checks `RUNSMTPSERVER` (`application/MUD.java:1576`).
- `guides/CM1Server.html:54` gives the CM1 port as `e.g.25`. The shipped port is 27733 (`web/cm1.ini:4`).
- `guides/Protocols.html:388` says sound files go in `/resources/web/pub/sounds/`. The shipped folder is `web/pub/sounds/`.
- `guides/InstallationGuide.html:828` tells the reader to "Set your javacord lib path" in the LLM section. The key set is `LANGCHAIN4J_JAR_PATH` (`coffeemud.ini:2219`).
- `guides/InstallationGuide.html:366` says `DBGMSGS` is `OFF` by default. The shipped value is `BOTH` (`coffeemud.ini:1941`).
- The Derby workaround in `guides/InstallationGuide.html:2917` (a `shutdown=true` connection) is not in the code. `DBConnections.killConnections` (`core/database/DBConnections.java:766` to `:785`) builds the shutdown properties but the `DriverManager` call is commented out (line 780), and `deregisterDriver` (`:739`) only sets a flag.

Schema and data problems:

- `coffeemuddbmssql.sql` has no `CMCDAC` table and `coffeemuddbpsql.sql` has no `CMPDAT` table. Both files are manual schemas, so this matters only for administrators who build a database by hand (checked by script).
- Version 9 is missing from `changelist.json`. The loader fills the gap with an empty list (`core/database/DDLValidator.java:83`), so it works, but the gap is undocumented.
- `fakedb2.schema` is not used by any code path. Its sizes and CLOB types are a schema form the parser accepts (`fakedb/backend/structure/FakeTable.java`), so it reads like a leftover sample.
- `guides/database/README` lists the SQL files but not the changelist that drives the automatic upgrade.
- The shipped `MPCPKEY` is a placeholder (`coffeemud.ini:33`); the guide says to change it before running a proxy.

Reference pages:

- `web/admin/work/pracetab.cmvp` links 8 race pages, but 11 `prace_*.html` files exist. The three unlinked races are Aarakocran, Centaur and Pixie. The navigation and the page list come from the same loop, so the pages were most likely generated at different times, when the list of playable races was different (inference; not verified).
- `pclass.html` and `prace.html` are empty (0 bytes) and are not linked from any page. The cause was not determined.
- The class `Prancer` is shown as "Dancer" on its own page and in the race qualifying lists (`CharClasses/Prancer.java:40`, `:43`). Anyone looking up a class by its ID will not find the page.

Multiplayer assumptions that a single-player design should not copy:

- Prompts and sessions exist to talk to other connected players. An NPC has no session, so it cannot be prompted (`guides/Programming.html:2432` to `:2437`).
- Journals, clans, channels, public polls, mail forwarding and the proxy (MPCP) exist to move players between servers and to let them talk to each other.
- The trap code checks that the player who set a trap is not breaking PK (player-killing) rules by hurting another player (`guides/Programming.html:5920`). That check only matters when players can attack players.
- Intermud and social integrations (I3, Discord, X, Grapevine) are for a networked game.

Cruft in the documentation itself: the guides repeat the same Archon and builder material in several places, and the MUDGrinder notes are mostly the author's voice rather than instructions.

## Takeaways for a Python SUD

Borrow:

- **An event with a preview.** A small class for a proposed action with a list of listeners, each able to veto it, then an apply step. Keep the source, target, tool and separate texts for the actor, the target and the observers; in a single-player game the observers are the narrator and the NPCs in the room, which is still useful. A sketch:

```python
class Event:
    def __init__(self, source, target, tool, text):
        self.source, self.target, self.tool, self.text = source, target, tool, text
        self.cancelled = False

def fire(room, event):
    if all(l.preview(event) for l in room.listeners):   # any False cancels
        for l in room.listeners:
            l.apply(event)
        return True
    return False
```

- **Layered stats.** A frozen `Base` dataclass and an `effective()` function that folds in the effects. This avoids the mutable-state bugs the guide's `recoverPhyStats` design exists to prevent.
- **Ability taxonomy.** A `Quality` enum (malicious, beneficial, circumstantial) on every ability, plus a domain string for grouping. The NPC logic and the help output can both use it.
- **Expertise-style modifiers.** Named modifiers with prerequisites that add "allows" entries to a family of abilities.
- **Length-keyed pseudo-language.** A dict for exact words and per-length lists of invented words. For a stable choice use a fixed hash such as `zlib.crc32(word.lower().encode())`, not Python's built-in `hash()`, which changes between runs.
- **Recipes as TSV or CSV.** Columns for name, level, time in ticks, materials, value, result class and spell, read into dataclasses at start-up. The format is already known to builders.
- **Migrations as an ordered list.** Use `PRAGMA user_version` in SQLite and a list of migration functions, instead of a JSON changelist. Keep the gap-tolerant behaviour (a version with no change is a no-op).
- **Generated reference.** A script that exports the game's data tables to Markdown, so the docs cannot drift from the data.

Simplify:

- Use turn-based ticks: one tick is one player action, which matches the undecided real-time or turn-based question. The guide's 4-second tick (15 per minute) is a reference point if real-time is chosen.
- Treat "actions" as a cost in ticks, as the guide does (`guides/Programming.html:663`), but drop the combat-versus-out-of-combat split unless combat is central.
- Use blocking input. The callback prompts exist to free server threads.
- Keep the stat-formula strings idea but evaluate them with a small safe parser (for example, `ast`-based), not `eval`.

Drop:

- Gen objects that are edited at run time, the MUDGrinder, the web server and macro language, MPCP, MSSP, MSDP, GMCP, Grapevine, Discord, X, I3, SMTP, CM1, multi-host boot and the protocol zoo. A single-player SUD needs none of them.
- Journals, clans, channels, polls and account systems.
- The "others see it" rules for PK and ordering pets, unless the design gives NPCs their own allegiances.
- The adult-content systems in their current form (torture, pregnancy, addiction, sexual disease). If any of these ideas are wanted, design them from scratch for the game.

## Coverage

Read in full: `guides/Programming.html` (all sections, read from a plain-text rendering; the line numbers cited are HTML lines), `guides/SMTPServer.html`, `guides/CM1Server.html`, `guides/MUDGrinder.html`, `guides/database/fakedb.schema`, `guides/database/README`, `web/admin/work/pclasstab.cmvp`, `web/admin/work/pracetab.cmvp` (only its key lines), `web/admin/work/help.cmvp`, and the small generated pages `guides/refs/nav.html`, `intro.html`, `pclasses.html`, `praces.html`, `pnoclass.html`, `pnorace.html`, `pracetab.html`, `prace_Elf.html`, `pclass.html` and `prace.html` (empty). The 25 lines of `guides/database/dropalltables.sql` were checked, not read as prose.

Read in part: `guides/InstallationGuide.html` (installation, getting started, upgrading, settings, multi-host, proxy, integrations, the database overview, Derby and troubleshooting sections; the Access, MySQL-Linux and HSQL steps and the package listing were skimmed, localization was read, and the Offline/Shutdown section was not read); `guides/WebServer.html` (introduction, configuration, the CMVP syntax, the error page, JavaScript, servlets and WebSockets were read; the macro table was sampled); `guides/Protocols.html` (ASCII, ANSI, TELNET, MSP, MXP, MSDP, GMCP, MSSP, MPCP, Grapevine, Discord, X and LLM were read; MCCP, MCP and ANSI-256 were not).

Structural parse, not read line by line: `guides/refs/` (all 79 files inventoried and sizes totalled; the 48 class pages, 11 race pages, six skill pages, both help pages, both property and behaviour pages were parsed with scripts; Elf and Mage were compared with the code).

Checked by script: `guides/database/changelist.json` (all 299 operations), the nine SQL schema files (table and column names), `fakedb2.schema` (table and column names).

Not read: the images in `guides/images/` (not in the manifest); the large `guides/refs/` pages were not read in full, as instructed.

Code read for cross-checks: the files named in the citations above (about 40 files). Other buckets cover the recipe data (`resources/skills/*.txt`, chapters c04 and c05), the help and socials data (c03 and c08), and the racial and class implementations.
