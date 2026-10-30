# Configuration properties and security

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b002` (Configuration properties and security) · 2 files · 7033 lines of Java.

## Summary

`CMProps` is the typed settings layer for `coffeemud.ini`. It defines about 154 string keys, 109 integer keys and 18 boolean keys, plus several list families read from `lists.ini`. It reads them once into arrays indexed by enum ordinal and rebuilds them with `resetSystemVars()`. The same file holds the game's tuning text: cost mini-languages, combat and stat formulas written as strings, hunger, thirst, injury and expiry tables, and word filters. `CMSecurity` is the permission layer. It has about 185 security flags (89 of them with an area-scoped twin), nesting groups, file-path grants, journal admin rights, soul-mate delegation, sysop masks, DISABLE/ENABLE, DEBUG and SAVE switches, JavaScript approval, and per-address ban and connection checks. For a single-player Python game the reusable parts are the typed settings, the feature toggles and the formulas-as-data approach. Most of the security machinery exists for multiple players. Reading the code turned up several real bugs. The most serious is a JavaScript approval bypass that is live with the shipped `JSCRIPTS=1`.

## Key files and types

Paths are relative to the CoffeeMUD folder.

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/core/CMProps.java` | Ini loader and typed accessors; key enums; cost and mana parsers; list-file caches; `resetSystemVars()` and `resetSecurityVars()`; prompt bytes; word filters; RP awards and deferred XP | 1–3965 (whole file) |
| `com/planet_ink/coffee_mud/core/CMSecurity.java` | Security flags, groups and file paths; enable and disable sets; debug and save switches; JavaScript approval; ban and IP-block lists; connection limiter | 1–3068 (whole file) |
| `CMProps.Str` | String keys (154 constants) | 150–306 |
| `CMProps.Int` | Integer keys (109), plus the `Prowesses` bit set | 316–431, 456–491 |
| `CMProps.Bool` | Boolean keys (18) | 499–519 |
| `CMProps.ListFile` | Keys read from `lists.ini` (73) | 546–638 |
| `CMProps.StrList`, `CMProps.WhiteList` | One string list (`SUBSCRIPTION_STRS`); three regex white lists (`IPSCONN`, `LOGINS`, `IPSNEWPLAYERS`) | 525–540, 644–649 |
| `CMSecurity.SecFlag` | Permission flags: 185 constants, 96 plain and 89 `AREA_` variants | 2341–2423 |
| `CMSecurity.SecGroup`, `CMSecurity.SecPath` | Nested flag groups with paths and journal names; file-path grants | 2432–2465, 2472–2763 |
| `CMSecurity.DisFlag`, `DbgFlag`, `SaveFlag` | Feature switches (111), debug categories (67), save-behaviour switches (6) | 2942–3067, 2853–2934, 2838–2846 |
| `CMSecurity.ConnectState` | NORMAL, BANNED, BLOCKED | 2185–2190 |

Callers outside this bucket that matter for the analysis:

- `application/MUD.java:319` (connection check), `:1045` (`unloadAll` at shutdown), `:1887` (boot reset).
- `Commands/Reset.java:986-987` and `Commands/Unload.java:262-263, 293-294` (reload paths).
- `WebMacros/INIModify.java:357` (web INI editor).
- `Commands/Modify.java:2950-2972` (JavaScript approval screen).
- `Common/DefaultScriptingEngine.java:9276` and `Common/DefaultQuest.java:1082` (script execution gates).
- `core/CMFile.java:217-219` (VFS and local access checks).

## How it works

Citations in this section are `core/…` paths under `com/planet_ink/coffee_mud/`.

### 1. Loading, scoping and accessors

- **Thread-group scoping.** `CMProps` extends `java.util.Properties` (`core/CMProps.java:48`). Instances sit in a 256-slot array keyed by the first character of the calling thread's group name (`:51`, `:53-55`, `:70-73`). `instance(Session)` picks by session group id (`:102-105`). A missing key falls back to the MAIN_HOST instance (`:997-998`, `:1018-1019`). Base group is `'0'` (`:89`). The design lets one JVM host several configured muds.
- **Loading.** `load(String)` reads the file through `CMFile` (`:920-938`). `loadPropPage` and `loadSystemPropPage` are the entry points (`:849-869`).
- **Command-line overrides.** `getCommandLineOverrides()` (`:123-126`) is consulted only by `getStr` (`:993`, `:1014`). `getPrivateStr` and `getRawPrivateStr` read the raw properties (`:965-982`).
- **Typed state.** `resetSystemVars()` (`:2715-3263`) is the single place where raw strings become typed fields: `sysVars`, `sysInts`, `sysBools`, `sysLists` and `sysLstFileLists` (`:662-669`), indexed by enum ordinal. It is called at boot (`application/MUD.java:1887`), by the reset command (`Commands/Reset.java:987`), by unload (`Commands/Unload.java:294`) and by the web INI editor (`WebMacros/INIModify.java:357`). It ends by calling `resetSecurityVars()` (`:3250`) and `CMLib.propertiesLoaded()` (`:3261`).
- **Name-based access.** `isPropName`, `getProp` and `setProp` (`:1341-1445`) map a key name to its enum, so the same keys can be read and written by name at runtime.
- **Case.** `setVar` upper-cases most `Str` values (`:1787-1792`). `setUpLowVar` keeps case (`:1839-1842`).

| Accessor | Behaviour | Reference |
|---|---|---|
| `getStr(key)` | `""` if missing | `:990-1002` |
| `getStr(key, default)` | Default if missing or empty | `:1011-1023` |
| `getBoolean(key)` | True if the value starts with `T` (any case) | `:1063-1069` (test at `:1066`) |
| Bool enum keys | Three keys are true only for `YES` (`ACCOUNTEXPIRATION`, `INTRODUCTIONSYSTEM`, `HASHPASSWORDS`) | `:2974-2976` |
| `getInt(key)` | 0 on parse failure | `:1095-1105` |
| `getIntVar(Int)` | -1 if unset or failed | `:1471-1481` |

Two boolean conventions coexist (see Problems).

### 2. Action costs: a default-plus-exceptions mini-language

The six action-cost keys (`DEFCMDTIME`, `DEFCOMCMDTIME`, `DEFABLETIME`, `DEFCOMABLETIME`, `DEFSOCTIME`, `DEFCOMSOCTIME`) take a comma list. A bare number is the default for all remaining items, and `ID value` pairs are exceptions. The ini comment gives the example `DEFCMDTIME=Score 1.0, East 0.5, 0.15` (`coffeemud.ini:536-544`).

- `setExceptionCosts` (`core/CMProps.java:2268-2299`) fills a map keyed by upper-cased ID.
- `resetSystemVars` stores the default as integer hundredths (`:3099-3104`). The shipped `DEFCMDTIME=0.20` becomes 20 (`coffeemud.ini:545`).
- `getCommandActionCost(ID)` returns `DEFCMDTIME/100.0` when the ID has no exception (`:1183-1186`). Skills and socials follow the same pattern (`:1206-1305`).

Mana costs use `setManaCosts` (`:2240-2266`): a list of numbers or `ID N` pairs. A value may be a compiled math expression (`getIndividualManaCostObject`, `:2230-2238`). `MANAMINCOST` uses the exception parser without the hundredths scaling (`:3107`).

A negative-to-positive switch in the parser stores a `_DEFAULT` key in the same map (`:2280-2286`). Only the mana-minimum readers consult it (`Abilities/StdAbility.java:1757`, `Abilities/Common/CommonSkill.java:613`).

### 3. Formulas as data

Combat, damage, recovery, XP and capacity formulas are strings under `FORMULA_*` keys (`core/CMProps.java:3202-3237`). They use placeholders `@x1` to `@x10` and `@xx`. This bucket does not define what each placeholder means. The callers and the math compiler are outside it, so that meaning is unverified here.

PvP variants default to the PvE string (`:3205`, `:3207`, `:3209`, `:3211`, `:3213`, `:3215`, `:3217`, `:3220`, `:3223`, `:3225`). A server can therefore override one set and inherit the rest.

Start-state formulas use a "base, formula" pair (`setStartStateStuff`, `:3265-3277`), for example `STARTHP=<n>, <formula>`. The base number goes to `STARTHP`, `STARTMANA` or `STARTMOVE`, and the remainder goes to the matching `FORMULA_CLASS*ADD` key.

Quoted defaults, verbatim because they are data:

- `FORMULA_NPCHITPOINTS` = `3 + @x1 + (@x1 * @x2)` (`:3226`)
- `FORMULA_ATTACKFUDGEBONUS` = `@x3 * (@x1 - @x2)* (@x1 - @x2)` (`:3204`)
- `FORMULA_MAXCARRY` = `@x1 + ((@x2 + 10.0) * @x2 * @x1 / 150.0) + (@x2 * 5.0)` (`:3233`)
- `FORMULA_MAXITEMS` = `(2 * @x1) + (2 * @x3) + (2 * @x2)` (`:3234`)
- `FORMULA_MAXFOLLOW` = `1 + ( ( @x2 - 6.0 ) / 3.0)` (`:3235`)
- `FORMULA_TOTALCOMBATXP` = `100 + ((25*@x1) - (@x1*((25*@x1)^.5)))` (`:3230`)
- `FORMULA_INDCOMBATXP` = `(@x1 * (@x2 / @x3)) < 100` (`:3231`)
- `FORMULA_PROFGAIN` = `(100 - (@x3 * 50)) * ( (@x1 + 1 - @x2) / ( (@x1 * 2) + (10 * @x2) ) )` (`:3236`)

### 4. Time and pacing

- `TIME_TICK` defaults to 4000 ms (`:681`). A `TICKTIME` under 500 falls back to 4000 (`:2720-2722`). The shipped value is `TICKTIME=4000` (`coffeemud.ini:2151`).
- `MILLISPERMUDHOUR` defaults to 600000 (`:682`, `:2727-2729`; shipped `coffeemud.ini:1099`).
- Derived values: 15 ticks per real minute, `round(60000/TIME_TICK)` (`:683`, `:2724`). 150 ticks per mud hour, `MILLIS_PER_MUDHOUR / TIME_TICK` (`getTicksPerMudHour`, `:3539-3542`). 21600 ticks per real day (`:685`, `:2726`).
- Action costs are measured in ticks. The ini comment says "1.0 means a full 4 second tick" (`coffeemud.ini:540`).
- Named walking and running costs: `WALKCOST` defaults to 1 and `RUNCOST` to 2 (`:3132-3133`). Their consumers are not read in this bucket, so the units are unverified.

### 5. Survival and body tuning

- **Hunger and thirst.** Each is a three-number list: full capacity, gain percent and loss percent (`:2988-2995`). The ini describes the second number as the percent applied to amounts consumed and the third as the percent applied to amounts lost through exercise (`coffeemud.ini:735-752`). Shipped values are `HUNGER=500,100,100` and `THIRST=1000,100,100`. Partial lists are broken (see Problems).
- **Injuries.** `INJURYSYSTEM` is a list of nine positional values, read by size (`:3160-3169`). The fields are `INJPCTCHANCE`, `INJPCTHP`, `INJPCTHPAMP`, `INJPCTCHANCEAMP`, `INJMULTIPLIER`, `INJMINLEVEL`, `INJBLEEDMINLEVEL`, `INJBLEEDPCTHP` and `INJBLEEDPCTCHANCE`. Code defaults are 100, 40, 10, 100, 4, 10, 15, 20, 100. The shipped line is `100,60,25,100,2,10,15,20,100` (`coffeemud.ini:340`). Consumers are outside this bucket.
- **Expiry timers.** `EXPIRATIONS` holds five values (`:3053-3085`): monster equipment 30, player drop 1200, resource 60, monster body 30, player body 1330. The shipped line matches (`coffeemud.ini:1173`). Units are unverified here.
- **Mana consumption.** `MANACONSUMEAMT` maps `LEVEL` to -100 and `SPELLLEVEL` to -200, and otherwise takes a number (`:3013-3019`). The negative values are sentinels.
- **Combat system.** `COMBATSYSTEM` maps `queue`, `manual` and `turnbased` to `CombatLibrary.CombatSystem` ordinals, and anything else to DEFAULT (`:3022-3032`). The shipped value is DEFAULT (`coffeemud.ini:291`).
- **Stats.** `STATCOSTS` defaults to `<18 1, <22 2, <25 3, <99 5` (`:2909`; shipped `coffeemud.ini:598`), which reads as a threshold-to-cost table (consumer not read). `BASEMAXSTAT` defaults to 18 and `BASEMINSTAT` to 3 (`:3094-3095`).
- **Class training.** Tokens `GAIN=n` and `SWITCH=n` in `CLASSSYSTEM` set `CLASSTRAINCOST` and `CLASSSWITCHCOST`. Any other token sets the `MULTICLASS` mode (`:2739-2748`). The shipped value is `CLASSSYSTEM=SUB` (`coffeemud.ini:495`). `TRAINCOSTS` defaults to five training costs (`:2935`).

### 6. Experience deferral and role-play awards

- **`EXPDEFER`** = `hours pct [command] [(argument)] [mask]` (`parseXPDeferDetails`, `:3793-3872`). Hours are converted to milliseconds with `MILLISPERMUDHOUR` (`:3816`) and stored as seconds (`:3817`). The percentage is stored as an integer (`:3836`). A named command without `$` clears the pass-through flag (`:3847-3850`). The shipped value is empty (`coffeemud.ini:774`).
- **`RPAWARDS`** = `pct [seconds], TYPE amount, ...` (`parseRPAwards`, `:3713-3791`). A bare number of 1 or more is read as a percentage, so `8` is 8 percent. A bare number under 1 is a fraction, so `0.5` is 50 percent (`:3760-3762`). The seconds default to 8 (`:3738`) and are stored times 1000 (`:3765`). Event types map to `RP_*` integers (`:3784`), and `CHANNEL-NAMED(...)` lists are supported (`:3776-3781`). The shipped value is empty (`coffeemud.ini:802`).

### 7. Word filters

Four filters, `EMOTEFILTER`, `POSEFILTER`, `SAYFILTER` and `CHANNELFILTER`, hold upper-cased word lists (`:2905-2950`).

- `isINIFiltered` matches whole words, case-insensitively. A match must start the text, follow whitespace or follow a caret, and must not be followed by a letter (`:3338-3394`, boundary test at `:3373-3377`).
- `applyINIFilter` replaces each non-space letter of a matched word with a character from `FILTER_PATTERN` = `%#@*!$&?` (`:58-59`). The character is chosen by position (`:3451`). Censored text keeps its length and shape.

### 8. Lists, gender table, locale and charset

- **List files.** `lists.ini` supplies 73 keys. They are parsed once and cached in `Resources` under a key built from the hash of the `LISTFILE` value (`:2301-2358`, cache key at `:2309`). Accessors return string lists, pair lists, int lists, two-level choice arrays and three-level grids (`:2423-2562`). Random pickers are `getListFileChoiceFromIndexedList` (`:2571-2577`), `...ByHash` (`:2586-2593`) and `getAnyListFileValue` (`:2611-2614`).
- **Genders.** `GENDERS` rows are pipe-separated columns: a gender code, a short label and pronouns (`resources/lists.ini:896`). `getGenderDef` indexes them by the first letter of each row (`:2368-2402`).
- **Locale.** `LANGUAGE` and `COUNTRY` set `CMLib.lang().setLocale` (`:2717-2718`; shipped `coffeemud.ini:157-158`).
- **Charset.** `CHARSETINPUT` and `CHARSETOUTPUT` default to iso-8859-1 (`:2979-2980`; shipped `coffeemud.ini:96`, `:100`).

### 9. Feature switches

- **DISABLE** (`core/CMSecurity.java:1611-1628`). Tokens are `DisFlag` names (111) or prefixed IDs: `ABILITY_`, `EXPERTISE_`, `COMMAND_`, `RACE_`, `CHARCLASS_` and `FACTION_` (`getSpecialDisableSet`, `:1663-1696`). `resetSecurityVars` adds `CLASSES` when `MULTICLASS` is `DISABLED` (`core/CMProps.java:3305-3307`). The shipped line is `DISABLE=DBERRORQUESTART, COMMAND_POSE, QUITREASON, RETIREREASON` (`coffeemud.ini:2079`).
- **ENABLE** (`setAnyEnableVars`, `:1444-1454`). Only `RACE_` and `CHARCLASS_` work. The ABILITY, EXPERTISE, COMMAND and FACTION branches in `getSpecialEnableMap` (`:1234-1244`, `:1256-1258`) are empty, so those tokens are rejected and logged as unknown (`setAnyEnableVar`, `:1349-1382`). This matches the ini comment (`coffeemud.ini:2082-2099`). `CHARCLASS` entries can carry race qualifiers (`:1365-1373`). A duplicate enable is accepted silently while the host is RUNNING (`:1377-1378`).
- **Queries.** `isAnyFlagDisabled`, `isCommandDisabled`, `isRaceEnabled` and similar (`:1498-1600`).
- **DEBUG** (`setDebugVars`, `:1186-1200`). A comma list of `DbgFlag` names. `EVERYTHING` sets a global switch (`:1199`).
- **SAVE** (`setSaveFlags`, `:1906-1919`). Six `SaveFlag` values, `NOPLAYERS`, `NOPROPERTYMOBS`, `NOPROPERTYITEMS`, `ROOMMOBS`, `ROOMSHOPS` and `ROOMITEMS`, invert default persistence (`isSaveFlag`, `:1005-1009`).

### 10. Security model (CMSecurity)

- **Flags and scope.** `SecFlag` constants come in plain and `AREA_` pairs. `getRegularAlias` and `getAreaAlias` link them (`:2377-2422`). A plain flag applies everywhere. An `AREA_` flag applies only where the mob is sub-operator (subop) of the room's area, through `room.getArea().amISubOp(name)` (`:626`, `:702`, `:814`, `:892`).
- **Groups.** `GROUP_<NAME>=` lines in the ini define `SecGroup`s (`parseGroups`, `:267-287`; shipped examples `coffeemud.ini:245-253`). Each token goes through `parseSecurityFlag` (`:296-357`). It can be a group name, an `FS:` or `VFS:` path grant (optionally `AREA`), a journal name (with `KILL` prefix and `S` suffix forms), or a flag. A flag that is not found retries with an `S` suffix (`:346-347`), so `COPYITEM` resolves to `COPYITEMS`. `POOF` maps to `GOTO` (`:348-349`). The shipped `GROUP_QUESTMASTERS` (`coffeemud.ini:245`) grants `POSSESS, AT, AFTER, CMDQUESTS, GOTO, COPYITEM, COPYMOB, IMMORT`.
- **Checks.** `isAllowed(mob, room, flag)` (`:883-898`) tests the player's own flags, then the class flags (`mob.baseCharStats().getCurrentClass().getSecurityFlags(level)`). It includes area flags only for a subop in that room's area. `containsAny` and the `isAllowedAnywhere*` family handle groups and cross-area checks (`:856-871`, `:908-934`, `:969-996`). `SecGroup.contains` recurses through nested groups (`:2572-2584`).
- **Sysop.** `isASysOp` tests a compiled zapper mask from `SYSOPMASK` (`:500-540`). With no mask, `setSysOp` uses `-ANYCLASS +Archon` if an Archon class exists and `-LEVEL +>30` otherwise (`:197-207`). The mask compiler is in `MaskingLibrary`, outside this bucket.
- **Soul-mate delegation.** A mob with a linked soul mate loses its staff checks unless that soul mate carries the `SYSOPMSGS` attribute (`:889-891`). The same test appears in `:862-864`, `:914-916`, `:950-952` and `:975-977`. Sysop status is inherited the same way (`:534-539`).
- **Files.** A `SecPath` grants a path prefix, flagged VFS or local and optionally area-only (`:2432-2465`). `canAccessFile` (`:766-797`) compares the upper-cased path with each grant's `slashPath`. `canTraverseDir` (`:725-754`) lets a player list parent directories. `getAccessibleDirs` (`:613-684`) builds the listing used by a web page (`WebMacros/CheckAuthCode.java:77`). `CMFile` calls `canAccessFile` separately for VFS and local reads and writes (`core/CMFile.java:217-219`).
- **Journals.** `registerJournal` adds a journal flag (`:213-217`). `containsJournal` treats `*` as "any journal in this group" (`:2551-2554`), and checks subgroups only for named journals (`:2557-2562`). `isJournalAccessAllowed` is at `:831-846`.
- **Scripts.** `JSCRIPTS` takes 0 (never run), 1 (needs approval) or 2 (always run). The constants are `JSCRIPT__NO_APPROVAL`, `JSCRIPT_REQ_APPROVAL` and `JSCRIPT_ALL_APPROVAL` (`:68-70`). The shipped value is 1 (`coffeemud.ini:233`). `isApprovedJScript` (`:1070-1085`) keys approval on the 32-bit `String.hashCode()` of the script text (`:1077`). `approveJScript` writes approvals to `::jscripts.ini` (`:1018-1034`). The quest and scripting gates (`Common/DefaultQuest.java:1082`, `Common/DefaultScriptingEngine.java:9276`) call the check. The MODIFY JSCRIPTS screen (`Commands/Modify.java:2950-2972`) lists unapproved entries and offers approval.
- **Connections and bans.** `getConnectState` (`:2272-2330`) runs once per incoming connection (`application/MUD.java:319`). It returns BANNED if `isBanned` matches the name or address against `banned.ini` (`:2070-2098`). It also returns BANNED if the address is in the IP block list. That list is loaded by `isIPBlocked` (`:1997-2018`) from the file named by `BLACKLISTFILE`, default `/resources/ipblock.ini` (`:2999`; loader `:2030-2058`). Each address also has a record and a counter. Once the counter reaches `CONN_MAX_PER_ADDR` = 6 (`:73`, test at `:2319`), further attempts are BLOCKED and the address joins an auto-block set (`:2308-2323`). The record expires after `CONN_LAST_DELAY_MS` = 5 minutes without an attempt (`:72`, `:2293`). In the tree, `clearConnectState` is called only from the SMTP request handler (`core/smtp/ProcessSMTPrequest.java:292`) and from tests.
- **Ban list.** `ban` appends to `banned.ini` after a duplicate check (`:2158-2175`). `unban` rewrites the file (`:2105-2150`). The in-game `Ban` command upper-cases its input first (`Commands/Ban.java:59-60`).

## Content worth mining

### Tuning values a text game could reuse

| Setting | Shipped value | Code default or note | Meaning |
|---|---|---|---|
| `HUNGER` | `500,100,100` (ini 743) | — | Stomach capacity, percent gained, percent lost |
| `THIRST` | `1000,100,100` (ini 752) | — | Same shape for drink |
| `INJURYSYSTEM` | `100,60,25,100,2,10,15,20,100` (ini 340) | `100,40,10,100,4,10,15,20,100` (`:3161-3169`) | Nine injury parameters, named in section 5 |
| `EXPIRATIONS` | `30,1200,60,30,1330` (ini 1173) | Same (`:3053-3085`) | Five expiry timers |
| `DEFCMDTIME` / `DEFABLETIME` | `0.20` / `0.10` (ini 545, 951) | — | Default action cost in ticks |
| `WALKCOST` / `RUNCOST` | — | `1` / `2` (`:3132-3133`) | Action cost of walking and running (name only; consumer not read) |
| `STATCOSTS` | `<18 1, <22 2, <25 3, <99 5` (ini 598) | Same (`:2909`) | Stat point costs by threshold |
| `REMORTMASK` | `-LEVEL +>90` (ini 696) | `-LEVEL +>60` (`:2910`) | Zapper mask for rebirth (consumer not read) |
| `AUCTIONRATES` | `0,2,0.1%,10%,5%,4,600` (ini 1863) | `0,10,0.1%,10%,5%,1,168` (`:2855`) | Auction parameters (meaning not verified here) |
| `PLAYERKILL` | `OPTIONAL-4` (ini 356) | Level gap 26 if no dash (`:676`) | The part after the dash is the level gap (`:1825-1830`) |
| `BASEMAXSTAT` | `18` (ini 586) | `18` (`:3094`) | Maximum base stat |
| `STARTINGITEMS` | begins `1 Ration, 1 Waterskin, 1 Torch, Pixie, 1 Apple, …` (ini 620, continued on following lines) | `1 Waterskin, 1 Ration, 1 Torch` (`:2848`) | Starting inventory list |

Code defaults and shipped values have drifted apart in several places (see Problems, item 14).

### Formula catalogue

The formulas are quoted in How it works, section 3. Their shapes are visible even though the placeholder meanings are not verified here. `FORMULA_MAXITEMS` and `FORMULA_MAXFOLLOW` are linear in their placeholders. `FORMULA_TOTALCOMBATXP` uses a power of 0.5 (`^.5`). `FORMULA_INDCOMBATXP` is a comparison against 100, not a value.

### Feature-switch themes

`DisFlag` has 111 names. Examples by theme:

| Theme | Examples |
|---|---|
| Progression and creation | LEVELS, EXPERIENCE, UNLEVEL, UNLEVELXP, CLASSTRAINING, RACES, CLASSES, CHARCRSTAT, CHARCRRACE, CHARCRCLASS, CHARCRGENDER, FULLSTATS |
| Body and survival | HUNGER, THIRST, FOODROT, AUTODISEASE, ALLERGIES, HYGIENE, FATIGUE, SLOW_AGEING, ALL_AGEING |
| Weather, time and places | WEATHER, WEATHERCHANGES, TODNOTIFIES, DARKWEATHER, DARKNIGHTS, PASSIVEAREAS, FATAREAS, THINAREAS, ROOMVISITS |
| Creatures, items, scripting | MOBILITY, EMOTERS, MUDCHAT, RANDOMMONSTERS, RANDOMITEMS, ITEMREJUV, SCRIPTING, SCRIPTABLE, SCRIPTABLEDELAY, MOBTEACHER, AUTOMOODS |
| Quests and rewards | QUESTS, RANDOMQUESTS, AUTOAWARDS, NPCAUTOAWARDS, CHANNELAUCTION, CATALOGTHREAD |
| Communication and protocols | LANGUAGES, AUTOLANGUAGE, MXP, MSP, MCP, MCCP, MSSP, MSDP, GMCP, I3, IMC2, GRAPEVINE |
| Politics and property | CONQUEST, ARREST, CLANTICKS, PROPERTYOWNERCHECKS |
| Accounts and connections | LOGINS, LOGOUTS, NEWPLAYERS, MAXCONNSPERIP, MAXCONNSPERACCOUNT, CONNSPAMBLOCK, AUTOPURGE, PURGEACCOUNTS |
| Persistence and threads | DBERRORQUE, SAVETHREAD, STATSTHREAD, JOURNALTHREAD, MAPTHREAD, PLAYERTHREAD, CRONJOBS, SPACETHREAD |

Debug categories (`DbgFlag`, 67) include `MUDPERCOLATOR` ("random area generation"), `BADSCRIPTS` ("bad mobprog practices"), `OUTPUT` ("all raw session output") and `EVERYTHING`, with descriptions in `CMSecurity.java:2853-2934`. The security flags (`SecFlag`, 185) cover movement (`GOTO`, `AT`), administration (`BOOT`, `PURGE`, `SNOOP`, `POSSESS`, `IMMORT`), and building (`COPY*`, `IMPORT*`, `EXPORT*`, `CMD*`).

### Language and text tables in `lists.ini`

These are the most directly reusable data in this bucket. Samples are paraphrased or cited by key.

| Key (`resources/lists.ini`) | What it is | Sample |
|---|---|---|
| `HEALTH_CHART` (497) | Graded health-status lines with a `<MOB>` placeholder | Lines range from near death ("is hovering on deaths door!") to lightly hurt |
| `DAMAGE_WORDS_THRESHOLDS` (441) | Breakpoints for picking damage wording | `0,3,6,10,15,25,35,50,70,100,130,165,215,295,495,795,2147483647` |
| `EXP_CHART` (560) | XP thresholds per level | Starts `0, 500, 1100, …` |
| `GENDERS` (896) | Pronoun rows: code, short label, subject, object, possessive, title | Rows start with `M` and `F` |
| `PERSONALITY_TRAITS` (2003) | Trait words in groups | First group opens with `LAWFUL`, then adjectives such as Disciplined and Honorable |
| `WEATHER_CLEAR` (1033) | Several description variants for one weather state | "The weather is clear." and "The weather is clear and humid." |
| `TECH_LEVEL_NAMES` (1767) | Tech-level names | `Mk.I` to `Mk.XX`, then `Ver2.0` to `Ver9.9` |
| `GAIT_LIST` (1046) | Four-part movement verbs: key, present participle, "enters" phrase, plain verb | `AMBLE` / ambling / ambles in / ambles, `TIPTOE`, `SKIP` |

The `Prowesses` bit set (`core/CMProps.java:456-491`) has 19 names, for example `ARMOR_ADV`, `COMBAT_NOUN`, `DAMAGE_ADJ`, `SKILL_PROFICIENCY`, `POWER_LEVEL` and `EXP_WORDS`. They appear to select word families for descriptive text. Their use is outside this bucket. The default selection is four bits (`:3171-3175`).

### Message texts

- An unapproved quest script reports that the `<SCRIPT>` is not approved and points to MODIFY JSCRIPT (`Common/DefaultQuest.java:1082`).
- The approval screen says the command applies only when scripts need approval, lists "Unapproved script" entries, and asks "Approve this script (Y/n)?" (`Commands/Modify.java:2950-2972`).
- The fallback mud name is `Unnamed_CM_` followed by hex (`core/CMProps.java:3879-3922`).

## Notable design ideas

- **The key enums are the schema.** `Str`, `Int`, `Bool`, `ListFile`, `StrList` and `WhiteList` define every setting, and `getProp`/`setProp` read and write them by name (`:1341-1445`). The same keys can be reloaded live (`Commands/Reset.java:986-987`, `WebMacros/INIModify.java:357`).
- **Default-plus-exceptions in one line.** The cost strings give one default and any number of per-command overrides (`:2268-2299`, ini 536-544). One line carries a whole cost family.
- **Formulas as data with inheritance.** PvP variants reuse the PvE string unless overridden (`:3205-3225`). Designers can rebalance by editing text.
- **A small qualification language.** Zapper masks gate sysop status (`SYSOPMASK`), rebirth (`REMORTMASK`, for example `-LEVEL +>90`) and deferred-XP eligibility (`EXPDEFER_MASK`).
- **Namespaced feature switches.** DISABLE takes both plain names and prefixed IDs (`:1663-1696`), so a whole subsystem or one ability can be turned off with the same syntax.
- **Plain and area twins.** Each area-scoped flag is generated from its plain partner by name (`:2377-2422`). Subops get the same checks, limited to their own area.
- **An approval gate for user code.** `JSCRIPTS=1` is a good idea for user-authored scripts: run nothing until a trusted user approves it. The implementation is broken (Problems, item 1).
- **Masking that keeps shape.** Censored words keep their length and shape by cycling punctuation (`:3451`).
- **Connection rate control with auto-block and expiry** (`:2308-2323`).
- **Deferred experience with a command hook.** Experience can be held back for a set time and paid later, optionally triggering a command (`:3793-3872`).
- **Role-play rewards.** `RPAWARDS` pays for in-character actions by event type: introductions, speech, socials, emotes, going away from keyboard and channel use (`:3713-3791`; `RP_*` keys at `CMProps.java:407-421`).

## Problems and cruft

Ordered roughly by severity. Each item states how it was checked. "Replicated" means the Java semantics were reproduced in a standalone program. The repository code was not run.

1. **JavaScript approval is bypassed on the second run (high; live with the shipped `JSCRIPTS=1`).** `isApprovedJScript` stores every unknown script in the approval table, with the script's own text as the value (`core/CMSecurity.java:1081`), and returns false. Any later call finds a non-null value and returns true (`:1078-1084`). The approval screen treats those same entries as pending (`Commands/Modify.java:2956-2968`), so the table mixes pending and approved records, and the check does not tell them apart. The identity is `String.hashCode()` (`:1077`), a 32-bit value that collides easily ("Aa" and "BB" both hash to 2112). Replicated: the first call returns false and the second returns true for the same script.
2. **Ability disables survive a reload (medium).** `setAnyDisableVars` clears six sets but not `ablDisVars` (`core/CMSecurity.java:1614-1620`). The reset command's reload (`Commands/Reset.java:984-987`) does not call `unloadAll`, and neither does the soft unload (`Commands/Unload.java:293-294`). An ability removed from DISABLE stays disabled until restart. Only `unload()` (`:233-239`) clears the set, and only `unloadAll` at shutdown calls it (`application/MUD.java:1045`). By reading.
3. **PROMPTBEHAVIOR's CR and LF are swapped (medium).** `CR` appends `\n` (`core/CMProps.java:2867`) and `LF` appends `\r` (`:2873`). The shipped comment says CRLF "adds extra carriage returns" (`coffeemud.ini:1157-1164`). The CRLF setting therefore emits LF then CR, the reverse of its name. By reading.
4. **Partial HUNGER and THIRST lists give zero percentages (medium).** `toArray(new String[3])` (`core/CMProps.java:2988`, `:2992`) pads a short list with nulls, so the length tests at `:2989-2995` always pass and the defaults never apply. A missing percentage becomes `CMath.s_int(null)`, which is 0 (`core/CMath.java:2104-2114`; `CMStrings.deleteAllofChar(null, …)` returns null, `core/CMStrings.java:901-905`). So `HUNGER=500,50` sets the loss percent to 0. Replicated for the array shape. `INJURYSYSTEM` avoids this by testing `V.size()` (`:3161-3169`).
5. **`addListVar` loses its value (low; no callers in the tree).** It copies the array to a longer one (`core/CMProps.java:1763`) and then writes into the old array (`:1764`). The new value is lost, the last existing entry is overwritten, and an empty list throws ArrayIndexOutOfBounds. Replicated.
6. **The gender fallback would throw (low; unreachable with shipped data).** `getGenderDef` calls `Arrays.fill(p.genderDefs, "UNK")` (`:2394`) on a `String[][]`, so the first store throws ArrayStoreException. Replicated. It is reached only if `GENDERS` yields no usable row, and `lists.ini:896` defines one. The apparent intent was to fill the 20-slot array created at `:2393`.
7. **A negative index from a hash (low).** `getListFileChoiceFromIndexedListByHash` takes `hash % length` (`:2589`). Callers negate negative hashes (`Items/MiscMagic/StdWand.java:138-141`, `Abilities/Properties/Prop_ClanEquipment.java:248-253`). `Integer.MIN_VALUE` stays negative after negation. The string "polygenelubricants" hashes to that value, and the modulo gives -3. Replicated.
8. **Section headers lose a character (latent).** `splitSections` uses `ts.substring(1,ts.length()-2)` (`core/CMProps.java:751`), which drops the last letter of each section name. Replicated with `[ABC]` giving `AB`. The shipped `coffeemud.ini` has no section lines, and only `application/MUDProxy.java:599-608` calls the method.
9. **Charset is read before it is set (latent).** The prompt suffix reads `getVar(CHARSETINPUT)` (`core/CMProps.java:2888`) before the value is assigned (`:2979`). On the first reset with an undocumented PROMPTBEHAVIOR token, `getBytes(null)` throws NullPointerException. The catch at `:2892` handles only `UnsupportedEncodingException`. Documented tokens (GA, CR, LF) never reach this path. Replicated for the null call.
10. **A blank `CLASSSYSTEM` leaves `MULTICLASS` unset (latent).** `MULTICLASS` is set only from a non-GAIN or SWITCH token (`core/CMProps.java:2739-2748`). A blank value gives an empty list (`core/CMParms.java:913-918`). `resetSecurityVars` then calls `equalsIgnoreCase` on null (`:3306`). Other readers include `CharClasses/StdCharClass.java:365`, `Commands/Train.java:421` and `Libraries/CharCreation.java:3365`. The shipped value is SUB, so this is not hit by default.
11. **Asymmetric sign handling in the cost parser (unverified intent).** When a negative default is followed by a positive one, `setExceptionCosts` stores `_DEFAULT` but does not assign `endVal` (`core/CMProps.java:2280-2281`). The mirror branch does assign it (`:2285-2286`). The returned default can therefore stay negative.
12. **Two boolean conventions.** `getBoolean` is true for any value starting with T (`core/CMProps.java:1066`). The Bool enum keys need `YES` (`:2974-2976`). The shipped `HASHPASSWORDS=YES` and `ACCOUNTEXPIRATION=NO` (`coffeemud.ini:225`, `:1334`) follow the second convention.
13. **The JSCRIPTS comment and the shipped value disagree.** The comment calls 2 the default (`coffeemud.ini:227-233`). The shipped line is `JSCRIPTS=1`, which is the value with the bypass in item 1.
14. **Shipped defaults drift from code defaults.** `INJURYSYSTEM` (ini 340 against `:3161-3169`), `AUCTIONRATES` (ini 1863 against `:2855`), `REMORTMASK` (ini 696 against `:2910`) and `STARTINGITEMS` (ini 620 against `:2848`). Code defaults apply only when a key is absent, so a reader of one file can miss the other.
15. **Soul-mate recursion can be unbounded (by reading, not run).** The thread-scoped `isASysOp` (`core/CMSecurity.java:487-491`) has no self-guard. The plain overload guards only the self-loop (`:538`). Two mobs that name each other as soul mate, and both carry `SYSOPMSGS` while neither matches the mask, recurse without end in both overloads. `StdMOB.setSoulMate` (`MOBS/StdMOB.java:4971-4974`) sets one side only.
16. **The ThinPlayer overload mixes array slots (by reading).** `isASysOp(PlayerLibrary.ThinPlayer)` tests `secs[0]` (`core/CMSecurity.java:568-570`) but assigns from `secs['0']` (`:573`). Those are index 0 and index 48, so the assignment can hit null and throw NullPointerException.
17. **A VFS grant is not trimmed (by reading).** `FS:` trims the text after the colon (`core/CMSecurity.java:304`), but `VFS:` does not (`:319`). So `VFS: AREA x` is not recognised as area-only.
18. **FS grants reach VFS files (confirm intent).** `canAccessFile` returns true for an FS grant whatever the VFS flag is (`:789-790`, `(!p.isVfs || isVFS)`). `CMFile` uses this check for VFS writes (`core/CMFile.java:217`). The class header describes FS and VFS as separate (`core/CMSecurity.java:56-57`).
19. **The directory listing drops missing parts (listing only).** `getAccessibleDirs` skips path components that do not exist instead of rejecting the grant (`core/CMSecurity.java:649-680`). So "//NOPE/BAR" is reported as "//BAR" if BAR exists at the root. Enforcement uses the raw grant (`canAccessFile`, `:792`).
20. **Group cycles are not guarded (by reading).** `addGroup` (`core/CMSecurity.java:434-442`) and `parseGroups` (`:267-287`) allow GROUP_A to include GROUP_B and the reverse. `contains` (`:2572-2584`) and `containsAny` (`:2593-2611`) recurse without a visited set.
21. **Ban-list case handling (medium for web-added entries).** `isBanned` upper-cases the login (`core/CMSecurity.java:2074`) and compares it with the raw list entries (`:2083-2092`). The in-game `Ban` command upper-cases before writing (`Commands/Ban.java:59`). The web `BanListMgr` does not (`WebMacros/BanListMgr.java:99`), so mixed-case web entries never match. The doc comment (`core/CMSecurity.java:2066`) says the search is case-insensitive.
22. **IPv6 addresses are truncated (by reading).** `makeIPNumFromInetAddress` reads only the first four address bytes (`core/CMSecurity.java:1969-1972`). IPv6 clients are therefore compared with IPv4 blocks by their first 32 bits.
23. **The effective connection limit is five (naming).** The first attempt in a record is reported as 0 (`core/CMSecurity.java:2300-2305`). The sixth attempt is therefore the first one refused, although the constant is called `CONN_MAX_PER_ADDR` = 6.
24. **Positional lists and sentinels (design).** `INJURYSYSTEM`, `EXPIRATIONS` and `HUNGER` are positional, so the meaning of each slot lives only in code. `MANACONSUMEAMT` uses -100 and -200 as sentinels (`core/CMProps.java:3014-3017`).
25. **Minor.** `applyINIFilter` indexes the original string with positions taken from the upper-cased copy. Those can differ in length, so the censor can land on the wrong letter (for example "straße" is 6 characters and 7 in upper case, replicated). `SecGroup.size()` is computed once at reset (`core/CMSecurity.java:2535-2541`) and goes stale when nested groups change. `removeAnyEnableVar` always returns true (`:1319-1341`). `getFakeMudName` hashes host and hardware identifiers, including MAC addresses (`core/CMProps.java:3899-3907`), and sign-extends bytes when packing (`:3912`). `DAYSCLANOVERTHROW` defaults to 952 (`:3114`; shipped `coffeemud.ini:1584`), an unexplained value.

## Takeaways for a Python SUD

1. **Use typed, validated settings, not strings.** Define one frozen dataclass per concern, load it from TOML (`tomllib`, Python 3.11+), and validate at load time. Reject unknown keys and out-of-range values. `CMProps` silently returns 0, -1 or `""` (`getInt` at `:1103`, `getIntVar` at `:1479`, `getStr` at `:1000`), which hides mistakes.

   ```python
   @dataclass(frozen=True)
   class Survival:
       hunger_capacity: int = 500    # CoffeeMud HUNGER=500,100,100
       hunger_gain_pct: int = 100
       hunger_loss_pct: int = 100
   ```

2. **Keep the time model, drop the threads.** A 4-second tick with 150 ticks per in-game hour (`core/CMProps.java:681-686`) is a useful reference for a real-time mode. A turn-based design can express the same thing as turns per in-game hour. Drop the per-thread-group registry (`:51-55`); a single process needs one settings object.
3. **Keep cost tables as default-plus-exceptions.** `action_cost = {"default": 0.2, "score": 1.0, "east": 0.5}` mirrors `DEFCMDTIME=Score 1.0, East 0.5, 0.15`. For a turn-based game, integer action points are cleaner than the hundredths scaling at `:3099`.
4. **Keep formulas as data, but parse them safely.** Store formulas in a data file with named variables instead of `@x1`. Evaluate them with a whitelisted `ast` walker that allows numbers, names, `+ - * /`, `**` and comparisons. Never `eval` text from a data file. Borrow the variant-inheritance idea: a `pvp_damage` formula defaults to `damage` (`:3205`).
5. **Keep feature switches as a set.** Use `disabled: frozenset[str]` for named subsystems such as hunger, thirst, weather, weather changes and random monsters, drawn from the DisFlag themes. Gate optional content with an enable set, as `RACE_` and `CHARCLASS_` do, but use one namespace.
6. **Keep debug categories as a set.** Log per category, as `DbgFlag` does, and keep an `EVERYTHING` switch.
7. **Name the fields of positional lists.** Replace `INJURYSYSTEM=100,60,25,…` with named keys. Replace the -100 and -200 sentinels with an enum.
8. **Gate user-authored code by content hash, with explicit records.** If the game loads player scripts, key approval on a SHA-256 of the content. Store approvals as `{hash: approver}`, and never put unapproved entries in the approved table (Problems, item 1).
9. **Drop most of the security system.** Groups, `SecPath`, VFS paths, journal admin, soul-mate delegation, sysop masks, IP blocking, the connection limiter, the new-user-per-IP caps (`core/CMProps.java:2151-2183`), SMTP and mailbox keys, the I3 router list (`I3ROUTERS`, `core/CMProps.java:207`), and the Discord, Twitter, LangChain4j and MPCP keys (`Str` at `:298-306`) all serve multiplayer or networking. Keep one developer flag and the debug categories. The telnet prompt bytes (`:2857-2897`) and charset keys are not needed in Python, where text is already Unicode.
10. **Rethink the multiplayer assumptions.** RP awards pay for other players' actions, so keep only self-directed rewards if the design wants them. The subop per-area rights would become "owner of a place" only if the design has a home-base mechanic. Soul mates, which are linked alternate characters, have no single-player equivalent.
11. **Borrow the masking idea only for generated text,** and keep the length of censored words (`:3451`).
12. **Borrow the tables, not the code.** Gender rows with pronouns, the gait verb quadruples, several variants per weather state, the health thresholds with damage-wording breakpoints, personality trait groups, and tech-level names (if the setting is sci-fi) can all become Python dicts or TOML tables. They are small and self-contained.

## Coverage

- **Read in full:** `com/planet_ink/coffee_mud/core/CMProps.java` (all 3965 lines, in 10 chunks) and `com/planet_ink/coffee_mud/core/CMSecurity.java` (all 3068 lines, in 8 chunks).
- **Read in targeted ranges to check callers and data:** `application/MUD.java` (319, 1045, 1887), `Commands/Reset.java` (980-990), `Commands/Unload.java` (258-296), `WebMacros/INIModify.java` (352-360), `Commands/Modify.java` (2950-2972), `Common/DefaultScriptingEngine.java` (9268-9290), `Common/DefaultQuest.java` (1076-1092), `core/CMFile.java` (205-225), `Commands/Ban.java` (45-62), `WebMacros/BanListMgr.java` (85-100), `Items/MiscMagic/StdWand.java` (136-143), `Abilities/Properties/Prop_ClanEquipment.java` (245-253), `core/smtp/SMTPserver.java` (200-207), `core/smtp/ProcessSMTPrequest.java` (292), `MOBS/StdMOB.java` (4971-4974), `core/CMStrings.java` (901-905), `core/CMath.java` (2104-2114), `core/CMParms.java` (913-918).
- **Data checked:** `coffeemud.ini` (the keys cited, by line) and `resources/lists.ini` (the keys sampled in the content table).
- **Replicated in a standalone Java program (not run inside CoffeeMud):** Problems items 1, 4, 5, 6, 7, 8, 9 and 25, and the hash-collision and length claims.
- **Not read:** the zapper mask compiler (`MaskingLibrary`), the CMath expression compiler, the `Resources` cache, `Directions`, `MudHost`, the help files (`resources/help/*.ini`), `guides/`, and the rest of `lists.ini` and `coffeemud.ini`.
