# Player stats, accounts and pride stats

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b010` (Player stats, accounts and pride stats) · 7 files · 5681 lines of Java.

## Summary

Each player character carries a `PlayerStats` object (implemented by `DefaultPlayerStats`) that holds what the game remembers about that character beyond the core MOB fields: display and channel preferences, friend and ignore lists, a tell history, a per-level progress log (time, room, age, costs paid), experience "banks", per-level combat counters, titles, birthday, and a hygiene value. An optional `PlayerAccount` (`DefaultPlayerAccount`) groups several characters under one login, holds the password and character limits, and shares some fields with its characters. `DefaultPrideStats` is the common base for both and keeps time-bucketed leaderboard counters (kills, rooms explored, experience, minutes played, quest points) per hour, day, week, month, season, year and all-time. Persistence is a flat XML blob per character plus several dedicated database columns, so the data model is clear but the save format is ad hoc. For a single-player SUD the reusable parts are the progress log, the time-bucketed counters and the per-character preference and social records; the accounts, multi-character limits, tell stacks and leaderboards are multiplayer-specific.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Common/DefaultPlayerStats.java` | Character stats: fields, name-based stat get/set, XML save and load, titles, tell stacks, visited rooms, level history, XP banks, combat counters, birthday, hygiene | 2540 |
| `Common/interfaces/PlayerStats.java` | Character contract; enums `PlayerFlag` (NOSTATS, NOTOP) and `PlayerCombatStat` (12 counters); `TellMsg`, `IMudTransferData`; hygiene and birthday constants | 1312 |
| `Common/DefaultPlayerAccount.java` | Account container: character-name list, password, expiry, account-wide friends and ignore, bonus limits, achievements, tattoos, a fake account MOB | 1013 |
| `Common/interfaces/AccountStats.java` | Contract shared by account and character (email, password, expiry, notes, bonus limits, ignore, `Agent` enum) | 372 |
| `Common/interfaces/PlayerAccount.java` | Account contract; `AccountFlag` enum (13 flags) | 207 |
| `Common/DefaultPrideStats.java` | Base class of both: per-period pride counters and their XML | 159 |
| `Common/interfaces/PrideStats.java` | `PrideStat` enum (7 counters) and the bump and get methods | 78 |

Related code read only as far as these classes call it: `Libraries/CMPlayers.java` (pride routing and top charts), `Libraries/CoffeeLevels.java` (level-up and XP), `core/database/MOBloader.java` (save and load), `Common/interfaces/TimeClock.java` (pride periods).

## How it works

### 1. Object model and the account link

- Hierarchy: `DefaultPrideStats` (`DefaultPrideStats.java:40`) is the base of `DefaultPlayerStats` (`:51`) and `DefaultPlayerAccount` (`DefaultPlayerAccount.java:45`). The interfaces run `PrideStats` (`PrideStats.java:43`), then `AccountStats` (`AccountStats.java:41`), then `PlayerStats` (`PlayerStats.java:48`) and `PlayerAccount` (`PlayerAccount.java:19`).
- A character's stats are reached through `MOB.playerStats()` (`PlayerStats.java:46`). Non-player mobs have none, and code checks for null, for example in `CoffeeLevels.java:1387`.
- When a character is linked to an account (`DefaultPlayerStats.account`, `:114`), some getters read through to the account and the setters write to both:

| Read from account when linked | Read from the character's own copy | Setter writes |
|---|---|---|
| email (`:244-252`), password (`:302-306`, `matchesPassword` `:317-322`), friends, ignored, subscriptions (`:628-649`), account expiry (`:1633-1636`) | last IP (`:230-234`), last login time (`:289-292`), last updated (`:274-278`) | both: `setEmail` `:254-260`, `setPassword` `:308-314`, `setLastIP` `:237-242`, `setLastDateTime` `:295-300`, `setLastUpdated` `:281-286` |

- New characters are attached to their account on first save (`MOBloader.java:3186-3188`). On load, the account is fetched only when `CMProps.isUsingAccountSystem()` (`DefaultPlayerStats.java:1496-1500`).

### 2. Character data model (`DefaultPlayerStats`)

| Group | Fields (line) | What they hold |
|---|---|---|
| Display and channels | `prompt` (`:85`), `colorStr` (`:84`), `wrap` (`:96`, default `DEFAULT_WORDWRAP` 78 from `PlayerStats.java:53`), `pageBreak` (`:102`), `channelMask` (`:81`), `theme` (`:69`) | An empty prompt means the CMProps default (`:467-476`). The channel mask has one bit per channel, 1 meaning off (`PlayerStats.java:129-135`). |
| Social | `friends` (`:107`), `ignored` (`:108`), `subscriptions` (`:109`), `introductions` (`:119`), `tellStack`, `gtellStack` (`:110-111`), `replyTo`, `replyType`, `replyTime` (`:104-106`) | Ignore and friend lists, journal subscriptions, introductions, bounded tell history, and the last private-message sender. |
| Custom messages | `poofin`, `poofout`, `tranpoofin`, `tranpoofout`, `deathPoof`, `announceMsg`, `savedPose`, `poseConstant`, `notes` (`:86-94`) | Text for GOTO and TRANSFER arrivals and departures, death, announcements, the saved pose, and admin notes. |
| Progress | `levelInfo` (`:131`), `legacy` (`:128`), `deathCounter` (`:80`), XP fields (`:73-79`), `combatStats` (`:120`) | Per-level history, generational legacy levels, experience banks, per-level counters. |
| Exploration | `visitedRoomSet` (`:117`), `tVisitedRoomSet` (`:118`) | Visited rooms keyed by extended room ID. The second set is for temporary areas. |
| Identity | `titles` (`:112`), `actTitle` (`:95`), `birthday` (`:103`), `hygiene` (`:68`) | Title stack, birthday fields, and body hygiene. |
| Account and security | `account` (`:114`), `accountExpires` (`:116`), `securityFlags` (`:115`), `playFlags` (`:130`), `autoApproves` (`:132`), `autoInvokeSet` (`:113`), `imudXferData` (`:133`) | Account link, security groups, character flags, remembered approvals, auto-invoke suppression, and inter-MUD origin. |
| Generic | `xtraValues` (`:70`), `alias` (`:127`), `classMap` (`:137`) | Extension stat values, command aliases, and per-class scratch maps. |

### 3. Stat codes: name-based access

- Most fields can be reached by name. `getStat(code)` and `setStat(code, value)` are switch statements over the index of the code in the static table `CODES` (`DefaultPlayerStats.java:2098-2111`, 39 standard codes). `getCodeNum` does a linear scan (`:2464-2472`).
- Extension codes come from `CMProps.getStatCodesList` (`:2448-2456`) and are read and written through `xtraValues` (`:2212`, `:2389`).
- The names are documented for MOBPROG scripts (`guides/Scriptable.html:5498-5499`).
- Only extension codes are written by the generic loop. `getSaveStatIndex` (`:2443-2446`) returns the index of the first extension code. Core fields are written by hand in `getXML`.

### 4. Persistence

**Character.** `getXML()` (`DefaultPlayerStats.java:1060-1126`) builds one flat XML string. It writes explicit tags for friends, ignored, introductions, subscriptions, wrap, theme, page break, account name, titles, aliases, legacy levels, class maps, account expiry, birthday, the poof and announce texts, notes, pose, the level history (`DATES`), security groups, auto-invoke set, the `XP` tag (roleplay and deferred values with their maximums), last XP award time, death count, flags, transfer data, approvals, visited areas, and achievements. It appends pride stats from the base class (`DefaultPrideStats.java:126-135`). `MOBloader.getPlayerStatsXML` wraps the result and adds tattoos and expertises (`core/database/MOBloader.java:2260-2275`). The XML goes to `CMCHAR.CMPFIL` (`MOBloader.java:2296`).

Several fields also have their own columns in the character row: `CMDATE` (last login time, `:2348`), `CMLSIP` (last IP, `:2359`), `CMCHAN` (channel mask, `:2349`), `CMCOLR` (colour, `:2358`), `CMPASS` (password, `:2322`). The character row also stores the prompt (`:2369`). Load is at `MOBloader.java:184-197`. Passwords are not in the XML.

**Account.** `DefaultPlayerAccount.getXML()` (`:365-400`) writes its 17 codes (`CODES`, `:821-825`) as tags, then pride stats, achievements, `TATTOOS`, and `ACCSTATS`, which is seven semicolon-separated numbers (`:389-398`). The row in `CMACCT` holds `CMAXML` (the XML), `CMCHRS` (character names separated by semicolons) and `CMPASS` (`MOBloader.java:3192-3236`, specifically the update at `:3199` and the insert at `:3219`). `MakeAccount` (`:3222-3236`) rebuilds the account from the row and calls `setPlayerNames` (`:3234`).

**Not persisted, from reading only (nothing was run):**
- Tell and group-tell stacks. Neither has a tag (`DefaultPlayerStats.java:519-524`, `:600-605`).
- Combat "spam" summaries (`combatSpams`, declared at `:129`, accessor at `:1626-1629`).
- Temporary visited rooms (`tVisitedRoomSet`, added to at `:1655-1656`).
- Last roleplay XP time (`lastRolePlayTm`, `:77`, `:2040-2048`).
- Hygiene. It has no tag, no stat code, and no column, and `MOBloader` never touches it.
- `COMBATSTATS` (`CODES` index 37). The `combatStats` array is read and written only through `getStat`, `setStat` and `bumpLevelCombatStat`, and no database writer was found in the tree.

### 5. Level history (`DATES`)

- `LevelInfo` (`DefaultPlayerStats.java:160-167`) holds: `level`, `time` (ms), `roomID`, `mins` and `costGains`. `costGains` has one slot per `CostDef.CostType`, which is TRAIN, PRACTICE, MANA, MOVEMENT, HITPOINT, XP, GOLD and QP (`core/interfaces/CostDef.java:33-42`).
- Written by `CoffeeLevels.level()` after the level-up effects: `recordLevelData(basePhyStats.level(), mob.getAgeMinutes(), room, costGains)` (`CoffeeLevels.java:1243`). The level has already been raised by then, since `doBaseLevelAdjustment` sets it (`CoffeeLevels.java:305`).
- `recordLevelData` (`DefaultPlayerStats.java:1842-1884`) updates an existing level's record in place. Otherwise it inserts a new record in level order. It drops a record that arrives less than one second (`TimeManager.MILI_SECOND`, `TimeManager.java:438`) after the previous one (checks at `:1859-1860` and `:1875-1876`).
- Serialised as `level,time,room,mins,c0,c1,...;` (`getLevelDateTimesStr`, `:1540-1554`) and parsed at `:1436-1464`. If the list is empty, a blank level-0 record is added (`addBlankLevelInfo`, `:1267-1276`).
- Reads go through `getLevelInfo` (`:1801-1815`). It returns an exact match, otherwise the most recent record with a lower level.
- Consumers: the LEVELTIMES view in `Commands/Stat.java:1045-1063`, the web macro `WebMacros/PlayerData.java:338-339`, and `Abilities/Archon/Archon_CRecord.java:268`.
- Unit mismatch. The parameter is named `ageHours` and the interface doc says hours (`PlayerStats.java:122`). The caller passes minutes (`CoffeeLevels.java:1243`), and `Stat.java:1063` multiplies by 60000, so the stored value is minutes.
- Off-by-one pairing, unverified intent. `leveledMinutesPlayed(L)` returns the record for level L-1 (`DefaultPlayerStats.java:1832`). `Stat.java:1052-1063` prints that value on the row for level L, next to L's date. As coded, the minutes shown beside level L are the age recorded when L-1 was gained.
- `Stat.java:1055` hides any record whose time is not after the fixed Unix time 1529122205 (mid-June 2018). This is a legacy cut-off.

### 6. Experience banks (deferred and roleplay XP)

- Fields: `deferredXP` and `maxDeferredXP`, `rolePlayXP` and `maxRolePlayXP`, `lastXPDateTime` (`DefaultPlayerStats.java:73-79`). They are saved in the `XP` tag and `LASTXPMILLIS` (`:1118-1119`) and loaded at `:1527-1535`.
- Caps are set lazily from the next level's cost. `maxDeferredXP = round(EXPDEFER_PCT/100 x expNeededLevel)` and `maxRolePlayXP = round(RP_AWARD_PCT/100 x expNeededLevel)` (`CoffeeLevels.java:1371-1383`).
- `gainExperience` (`CoffeeLevels.java:937-1000`) runs the deferral when `EXPDEFER_PCT > 0` and the class and race are not "expless" and the level is not capped:
  - If `EXPDEFER_COMMAND` is empty and `now > lastXPAward + EXPDEFER_SECS` (`:951-952`), the banks are released. Their totals are added to the new amount and XP is applied (`:954-957`).
  - Otherwise, if `deferredXP + rolePlayXP < maxDeferredXP`, the amount is added to the bank (`:963-965`). In every case XP is not applied on this path (`:981`).
  - When XP is applied, `lastXPDateTime` is set (`:990`).
  - Net effect as coded: XP earned in continuous play builds up in a capped bank. It is paid out in one lump on the first gain after `EXPDEFER_SECS` have passed since the last payout. Gains that arrive when the bank is full are dropped.
- `gainRPExperience` (`:1003-1074`) adds roleplay XP to `rolePlayXP` up to `maxRolePlayXP` (`:1024-1032`). With deferral on, the same bank check applies (`:1030`).
- Setters: `setRolePlayXP` ignores negatives and clamps to the maximum (`DefaultPlayerStats.java:2029-2037`). `setDeferredXP` clamps to the maximum and does not check for negatives (`:2070-2076`).
- Level-up gains (`CoffeeLevels.java:1214-1238`): practice is `floor(min(WIS, cap) / 6) + class bonus`, with a minimum of 1 (`:1221-1229`). Training is +1 per level (`:1234-1238`).

### 7. Pride stats (time-bucketed leaderboards)

- Data: `prideStats[period][stat]` (int) and `prideExpireTime[period]` (long), `DefaultPrideStats.java:42-43`. Periods are HOUR, DAY, WEEK, MONTH, SEASON, YEAR and ALLTIME (`TimeClock.java:736-744`). Stats are PVPKILLS, AREAS_EXPLORED, ROOMS_EXPLORED, EXPERIENCE_GAINED, MINUTES_ON, QUESTS_COMPLETED and QUESTPOINTS_EARNED (`PrideStats.java:51-60`).
- `bumpPrideStat` (`DefaultPrideStats.java:95-115`). ALLTIME is never reset. For any other period, when `now > prideExpireTime[period]`, all stats for that period are zeroed and the expiry moves to `period.nextPeriod()` (`:106-111`). The reset happens on the next bump, not on a timer.
- `TimeClock.TimePeriod.nextPeriod()` (`TimeClock.java:788`) uses `Calendar`. Hours roll to the next hour. Days roll to midnight. Weeks roll to the next Sunday. Months roll to the 1st. "Season" is a 91-day step (365/4) from the start of the current day-of-year block. Year rolls to 1 January.
- Reads: `getPrideStat` (`DefaultPrideStats.java:118-123`) does not check expiry. Until something bumps an expired period, reads return the previous period's totals.
- Routing: `CMPlayers.bumpPrideStat` (`CMPlayers.java:1075-1136`):
  - bumps the character's counter unless `PlayerFlag.NOSTATS` is set (`:1088`);
  - updates the all-time top chart unless `NOTOP` is set (`:1090`), and per-category charts keyed by `getPrideCatValue` (`:1093-1122`);
  - repeats the counter and chart steps for the account, using `AccountFlag.NOSTATS` and `NOTOP` (`:1124-1131`).
- Top charts: `adjustTopPrideStats` (`CMPlayers.java:1164`) keeps the top `PRIDECOUNT` entries per period and stat. `PRIDECOUNT` defaults to 10 (`core/CMProps.java:2843`). Entries that fall off can be deleted from the database (`:1138-1151`).
- Load: `parsePrideStats` (`CMPlayers.java:2087-2118`). If a stored expiry has passed, that period's counts come back as zero and the expiry moves on (`:2099-2100`). The leaderboard rebuild skips characters with NOSTATS or NOTOP (`core/database/MOBloader.java:2674-2675`, `:2867-2868`).
- Call sites (from search, not exhaustive):
  - ROOMS_EXPLORED on first visit only: `Locales/StdRoom.java:932-937`, plus `CharClasses/Sailor.java:310` and `CharClasses/Bard.java:326, 345`.
  - EXPERIENCE_GAINED: `CoffeeLevels.java:937, 1013`.
  - MINUTES_ON: `Libraries/CommonMsgs.java:962`, in `tickAging`.
  - PVPKILLS: `Libraries/MUDFight.java:2752`.
  - QUESTPOINTS_EARNED: `Items/Basic/QuestPoint.java:83`, `Items/MiscMagic/GenSuperPill.java:219`, `Abilities/Properties/Prop_EnterAdjuster.java:330`, `Common/DefaultScriptingEngine.java:10723, 10729`.
  - QUESTS_COMPLETED: `Common/DefaultScriptingEngine.java:13362`.
  - AREAS_EXPLORED: no caller found.
- `addRoomVisit` (`DefaultPlayerStats.java:1647-1662`) returns true only on a first visit. Rooms in SubArea or AutoGen areas go to the temporary set (`:1655-1656`), which is not saved.

### 8. Accounts

- Membership: `addNewPlayer` (`DefaultPlayerAccount.java:470-490`) ignores duplicates case-insensitively, bumps the CHARACTERS achievement by +1 (`:487`) and clears the thin-player cache. `delPlayer` (`:506-543`) removes by name and bumps the achievement by -1. `getThinPlayers` (`:559-584`) builds lightweight entries and refreshes them when the count changes or a character's level changed (`:578-579`).
- Character creation limit (`Libraries/CharCreation.java:4369-4373`, `:4386-4388`, and `:2452-2456`): `COMMONACCOUNTSYSTEM` plus the account's `bonusCharsLimit`, unless `NUMCHARSOVERRIDE` is set. The message is "You may only have @x1 characters."
- Simultaneous-online limit (`CharCreation.java:2614-2621`): `MAXCONNSPERACCOUNT` plus `bonusCharsOnlineLimit`, unless `MAXCONNSOVERRIDE` is set. The message is "You may only have @x1 of your characters on at one time."
- Account flags (`PlayerAccount.java:169-206`): NUMCHARSOVERRIDE, NOEXPIRE, CANEXPORT, MAXCONNSOVERRIDE, ANSI, ACCOUNTMENUSOFF, NOAUTOFORWARD, NOSTATS, NOTOP, ANSI16ONLY, ANSI256ONLY, NOCHARPURGE, AUTOTELLNOTIFY. They are stored as a set and serialised as a comma list (`DefaultPlayerAccount.java:821-825`, `:847`, `:904-911`). Character flags are NOSTATS and NOTOP (`PlayerStats.java:1162-1177`).
- Shared state: friends, subscriptions, ignore, expiry and password read through to the account (section 1).
- `getAccountMob` (`DefaultPlayerAccount.java:335-352`) builds a fake StdMOB with a linked stats object, "for forum and other access systems" (`PlayerAccount.java:68-72`).
- Tattoos are timed markers. The list drops any tattoo whose expiry has passed (`DefaultPlayerAccount.java:85-94`). `addTattoo` ignores a name that already exists (`:697-702`).
- Bonus allowances: the account holds bonus common, craft, non-craft and language skill slots, a bonus character-stat point count, and the two character limits (`:71-77`). `AccountStats.java:187-190` says character-level bonuses are "independent of the account bonus". How they are summed was not traced. The one example is `CharClasses/Apprentice.java:263`, which adds 1 to the character's common-skill bonus.

### 9. Titles

- A stack. Index 0 is the active title (`DefaultPlayerStats.java:796-821`). Text in braces is taken literally (`:808-809`). If the top title ends in the placeholder `*` and the next starts with `*`, they are joined (`:814-819`). With no titles, the active title is `*` (`:798-799`).
- `StdMOB.java:531` replaces every `*` with the character's name. The doc example is "*, the bunny slayer" (`PlayerStats.java:256-258`).
- `addTitle` moves a title that already exists to the top (`DefaultPlayerStats.java:861-913`). A `RAND` flag marks titles in the random pool (`getTitleRandom`, `:780-793`; XML at `:1236` and `:1254`).
- Sources: achievements (`Libraries/Achievements.java:8650`), `Libraries/AutoAwards.java:223-224` (adds the title twice to force it to the top), clan ranks (`Common/DefaultClan.java:900`), honorary degrees (`Abilities/Skills/Skill_HonoraryDegreeCommoner.java:222`), and editors and the web grinder (`Libraries/CMGenEditor.java:4456`, `WebMacros/grinder/GrinderPlayers.java:60, 385`).

### 10. Hygiene

- Model: one `long` per character, starting at 0 (`DefaultPlayerStats.java:68`). `adjHygiene` clamps at 0 (`:1614-1623`).
- Constants (`PlayerStats.java:1302-1311`): `HYGIENE_DELIMIT` = 5000 (one unit of stink), `HYGIENE_WATERCLEAN` = -1000, `HYGIENE_COMMONDIRTY` = +2, `HYGIENE_FIGHTDIRTY` = +1, `HYGIENE_RAINCLEAN` = -100 (no caller found).
- Gain:
  - Each move adds the room's `pointsPerMove()` (`Libraries/MUDTracker.java:1793-1800`). That value is the climate-adjusted movement cost (`Locales/StdRoom.java:2860-2862`). The add is skipped for soul-linked mobs, riders, and when the HYGIENE security flag is disabled.
  - Each attack processed against a player adds +1 (`Libraries/MUDFight.java:2963-2968`).
  - Each tick of a common-skill ability adds +2 (`Abilities/Common/CommonSkill.java:278-283`).
  - The Archon stinkify ability sets dirt to `HYGIENE_DELIMIT + 1` (`Abilities/Archon/Archon_Stinkify.java:117`).
- Cleaning: `handleHygienicMessage` (`Libraries/CommonMsgs.java:2227-2275`) is called from water rooms with a minimum of 100 (`Locales/ShallowWater.java:97`, `Locales/UnderWater.java:105`) and from a tub with a minimum of 0 (`Items/Basic/StdTub.java:378`).
  - If the current value is above the cleaning amount, the amount is subtracted, with one of three messages: "You feel a little cleaner, but are still very dirty." (above half the delimit), "You feel a little cleaner; almost perfect." (below 1500), or "You feel a little cleaner." (`:2233-2242`).
  - Otherwise dirt goes to 0. If the value was between 4 and the room minimum, the message is "You can't get any cleaner here." and a 20-tick tattoo `SYSTEM_HYGEIENE_MSG` is added (`:2257-2270`).
  - A zero-amount call with no dirt says "You are already perfectly clean." (`:2245-2250`).
- Triggers (`CommonMsgs.java:2215-2222`): moves not made through a common-skill ability, and the socials named BATHE or WASH. Only characters with stats and no soul link qualify.
- Effects:
  - Charisma. At or above 5000, charisma drops by `hygiene / 5000` (integer division), but never below half its value (`MOBS/StdMOB.java:949-955`).
  - Display: STINK percentage in `Commands/Stat.java:1086`. The Affect command shows "You could use a bath." style text by level once hygiene reaches the delimit (`Commands/Affect.java:60-70`). A system operator's hygiene is reset to 0 on that same check (`:61-62`). A sniffing player gets "has a slight aroma about" text (`Libraries/CommonMsgs.java:746-752`).
- Persistence: not saved (section 4).

### 11. Birthday and age

- `birthday` is `int[4]`: day, month, year, and last year celebrated (`PlayerStats.java:1284-1291`).
- `initializeBirthday(clock, ageHours, race)` (`DefaultPlayerStats.java:992-1016`):
  - each 15 hours of age removes one year (`while(ageHours>15)`, `:1001-1005`);
  - if the age is not whole years, the month and day are chosen at random (`:1006-1010`);
  - current age is the race's young-adult aging-chart entry plus years since birth, minus one if the birthday has not yet come (`:1011-1015`).
- Callers: `MOBloader.java:217, 230` (passes age in minutes divided by 60), `Abilities/Misc/Age.java:1018` (passes `ageYears*15`, which matches the 15-hour year), and `CharCreation.java:3941` and `Commands/Remort.java:459` (pass 0).
- The aging tick (`Libraries/CommonMsgs.java:952-974`) adds elapsed minutes to the mob's age and then compares the clock against the birthday.
- `getBirthdayClock` caches a derived clock (`DefaultPlayerStats.java:979-989`). The cache is not cleared when the birthday changes, in `setBirthday` (`:1128-1142`) or `initializeBirthday`. It throws `NullPointerException` if `birthday` is null (`:984-986`).

### 12. Social lists and tells

- `getHashFrom` (`DefaultPlayerStats.java:491-516`) splits semicolon lists and strips leftover tag names.
- Ignore resolution. `isIgnored(name)` (`:680-687`) checks the name and `name*`. `isIgnored(category, mob)` (`:652-677`) checks the account first, then the mob's name, the account wildcard `Account*`, `category.name`, and `category.Account*`. An ignore entry is therefore a name, an account wildcard ending in `*`, or a category-qualified name.
- Introductions (`:478-489`) store uppercase names and gate the INTRODUCTIONSYSTEM (`PlayerStats.java:805-824`).
- Subscriptions are uppercase journal names (`AccountStats.java:296-302`). Keys shaped like `X :` get a leading space on load (`DefaultPlayerStats.java:1299-1313`, `:2352-2364`; the account has the same code at `DefaultPlayerAccount.java:941-954`). The reason is not documented and looks like a legacy workaround.
- Tells: `addTellStack` (`:519-524`) keeps a ring buffer. `queryTellStack` (`:527-538`) filters by sender, recipient and time. Writers are `Libraries/CommonMsgs.java:619, 631`, `Commands/GTell.java:181` (separate group buffer), and `Libraries/IntermudClient.java:605-637`.

### 13. Combat counters and legacy levels

- `bumpLevelCombatStat(stat, level, amt)` (`DefaultPlayerStats.java:2419-2440`) keeps one row per character level, with the level stored in slot 0 (`:2431`). It returns 0 for level <= 0, when the COMBATSTATS security flag is disabled, or when level >= `LASTPLAYERLEVEL + 10` (`:2424-2425`). Increments are guarded against `long` overflow (`:2433-2438`). An amount of 0 is a read.
- Callers: `MOBS/StdMOB.java:353` (EXPERIENCE_TOTAL), `:2208-2210` (ACTIONS_DONE and ACTIONS_TAKEN), `:3999` (COMBATS_TOTAL), `:4216` (ROUNDS_TOTAL). `Libraries/MUDFight.java:1211-1213` (hits), `:2592-2594` (damage), `:2641` (deaths taken), `:2675` (deaths done). Display: `Commands/Stat.java:1148-1171`.
- Legacy levels: `addLegacyLevel(category)` increments a per-category count (`DefaultPlayerStats.java:1896-1903`). In the Age ability, the new character's legacy comes from the highest parent legacy in the parents' top base class (`Abilities/Misc/Age.java:931-933`). It is credited as `highestLegacyLevel + 1` when the highest parent reached `LASTPLAYERLEVEL` (`:1031-1033`).

### 14. Flags, approvals, auto-invoke, transfer

- `PlayerFlag` set: NOSTATS, NOTOP (`PlayerStats.java:1162-1177`; loaded at `DefaultPlayerStats.java:2338-2345`).
- `autoApproves` maps a command or ID to yes, no or unset. It is serialised as `id:T,id:F` (`DefaultPlayerStats.java:1028-1057`, `:1574-1591`).
- `securityFlags` is a `SecGroup` parsed from a semicolon list (`:1557-1571`) and saved as `SECGRPS`.
- `autoInvokeSet` holds ability IDs whose auto-invocation is suppressed. The ID `ANYTHING` means all of them (`:2395-2404`). `CharClasses/StdCharClass.java:1338-1341` adds class defaults only when `ANYTHING` is not set.
- `IMudTransferData` (`PlayerStats.java:1182-1215`) records the origin host, port, timestamp and an `isLocal` flag. A non-local character is not savable (`DefaultPlayerStats.java:2520-2524`).

### 15. Display and pose preferences

- Channel mask: 32 bits, 1 meaning off (`PlayerStats.java:129-135`). Saved to `CMCHAN`.
- Colour overrides: a string of colour letter, ANSI code and `#`, repeated (`PlayerStats.java:146-156`). Saved to `CMCOLR`.
- Wrap: the default is 78. `setWrap` clamps to the length of `CMStrings.SPACES` (`DefaultPlayerStats.java:330-336`).
- Default prompt when none is set: `^N%E<^h%hhp ^m%mm ^v%vmv^N>` (`:467-476`).
- Pose: `setSavedPose` takes a constant flag. The default is constant (`:93`, `:451-458`).

## Content worth mining

- **Pride counters (7) and where they are bumped:** PVPKILLS (`MUDFight.java:2752`), ROOMS_EXPLORED (`StdRoom.java:932-937`), EXPERIENCE_GAINED (`CoffeeLevels.java:937`), MINUTES_ON (`CommonMsgs.java:962`), QUESTS_COMPLETED and QUESTPOINTS_EARNED (see section 7), AREAS_EXPLORED (none found).
- **Pride periods (7):** HOUR, DAY, WEEK (Sunday-start), MONTH, SEASON, YEAR, ALLTIME (`TimeClock.java:736-744`).
- **Account flags (13)** as listed in section 8, with a clear split between limit overrides (NUMCHARSOVERRIDE, MAXCONNSOVERRIDE), client display (ANSI, ANSI16ONLY, ANSI256ONLY), and privacy (NOSTATS, NOTOP, NOAUTOFORWARD).
- **Character flags (2):** NOSTATS and NOTOP.
- **Combat counters (12)** (`PlayerStats.java:1223-1237`): STATS_LEVEL, COMBATS_TOTAL, EXPERIENCE_TOTAL, ROUNDS_TOTAL, DEATHS_DONE, ACTIONS_DONE, DAMAGE_DONE, HITS_DONE, DEATHS_TAKEN, ACTIONS_TAKEN, DAMAGE_TAKEN, HITS_TAKEN.
- **Cost types on each level-up record (8):** TRAIN, PRACTICE, MANA, MOVEMENT, HITPOINT, XP, GOLD, QP (`CostDef.java:33-42`).
- **Hygiene messages** (section 10), which are short and usable as flavour text as they stand.
- **Title example form:** "*, the bunny slayer" (`PlayerStats.java:256-258`).
- **Reply types:** SAY, YELL, TELL (`PlayerStats.java:1296-1300`).
- **Stat codes:** 39 character codes and 17 account codes (section 3). These are a ready list of what a stat or script interface would need to expose.

## Notable design ideas

- **Account as a shared-state container.** A linked character reads email, password, friends, ignore and expiry through to its account and writes to both (section 1). Each character keeps its own copy of the other fields. This is a simple way to share state without a separate table.
- **Period counters that reset lazily.** Each period stores its own expiry, and the reset happens on the next write (`DefaultPrideStats.java:95-115`). No scheduler is needed. Calendar boundaries come from `TimeClock.nextPeriod()`.
- **Top-N charts maintained on write.** Each bump updates a fixed-size top list per period and category (`CMPlayers.java:1164-1200`), so reading a leaderboard is cheap.
- **Experience banked in a capped pool.** XP accumulates up to a ceiling tied to the next level's cost and is paid out in a lump after a quiet gap (section 6). Pacing lives in the data, not in a timer loop.
- **Hygiene as a reversible, environment-coupled stat.** Dirt rises with movement (scaled by the room's cost), fighting and common-skill use. It falls only in water or a tub. Its only mechanical effect is a capped charisma penalty (section 10).
- **Per-level chronicle.** A record per level with time, room, age and costs paid (section 5). It is cheap, and it supports views of the character's history.
- **Legacy levels across generations.** The Age ability hands progress from parents to a child (section 13), so a lineage keeps some of its history.
- **Name-based stat access with extension codes.** One switch-based get and set for every field, with scripts and editors able to add codes (section 3).
- **Titles as a stack with a name placeholder.** The active title is the top entry. `*` stands for the name, and a random flag enables random pick (section 9).
- **Ignore wildcards.** Three forms (name, account with `*`, category-qualified) give fine control with one set (section 12).
- **Timed tattoos.** Markers that expire are filtered from the list on read (`DefaultPlayerAccount.java:85-94`).

## Problems and cruft

- **Doc and code disagree:**
  - `isOnAutoInvokeList` doc says "ANYTHIN" (`PlayerStats.java:914`). The code and its caller use "ANYTHING" (`DefaultPlayerStats.java:2399`, `CharClasses/StdCharClass.java:1338`).
  - `adjHygiene` doc says it returns true when the amount "goes past HYGIENE_DELIMIT" (`PlayerStats.java:611-614`). The code returns true whenever hygiene is 1 or more (`DefaultPlayerStats.java:1617-1622`). No caller uses the return value; every call site in the tree is a statement (for example `Libraries/CommonMsgs.java:2235`, `Libraries/MUDFight.java:2968`).
  - `recordLevelData` doc says hours (`PlayerStats.java:122`). The value is minutes (section 5).
  - `getRolePlayXP` doc says "maximum" (`PlayerStats.java:1005`). Copied text.
  - AccountFlag docs: CANEXPORT, MAXCONNSOVERRIDE, ANSI and ACCOUNTMENUSOFF all say "overrides account expiration" (`PlayerAccount.java:175-182`). Copied text.
  - `HYGIENE_RAINCLEAN` doc repeats the water-clean text (`PlayerStats.java:1310-1311`). The constant has no caller.
  - `getBirthday` doc says "4-dimensional" for a 4-element array (`PlayerStats.java:586`).
- **Stat-code typo that is now public.** The tranpoof-out field's stat name is `TRAINPOOFOUT` (`DefaultPlayerStats.java:2102`). Its XML tag is `TRANPOOFOUT` (`:1114`, `:1395`). `guides/Scriptable.html:5499` documents the typo, so changing it would break scripts.
- **Wrong fallback types.** `DefaultPrideStats.newInstance` returns `new DefaultPoll()` (`DefaultPrideStats.java:66`). `DefaultPlayerAccount.newInstance` returns `new DefaultPlayerStats()` (`DefaultPlayerAccount.java:114`). Both only apply when reflection fails.
- **`copyOf` shares mutable state.** `DefaultPlayerStats.copyOf` (`:195-228`) reassigns only some collections. Shared references include the auto-invoke set, combat spam map, achievement trackers, approvals, introductions, `combatStats`, `birthday`, `ableMap`, `experMap`, and the pride arrays inherited from the shallow `clone()` (`DefaultPrideStats.java:82-92`). `DefaultPlayerAccount.copyOf` shares `players` (`:123-148`). The one live caller, the Remort snapshot (`Commands/Remort.java:555`), only reads `BONUSCHARSTATS`, so it is harmless there.
- **Inconsistent read-through.** Email, password, friends and expiry read the account when linked, but last IP, last login time and last-updated read the character's own copy (section 1).
- **Checked and not a bug:** `DefaultPlayerAccount.delPlayer` (`:508-515`, `:529-536`) calls `remove` inside a loop over the same list. `SVector` is backed by a `CopyOnWriteArrayList` (`core/collections/SVector.java:40-42`), so the loop iterates a snapshot and cannot throw `ConcurrentModificationException`. The empty `catch` blocks around those loops (`:518-520`, `:538-540`) therefore only hide other errors.
- **Tell buffer holds 51, not 50.** `addTellStack` (`DefaultPlayerStats.java:521-522`) removes the oldest entry only when size is greater than 50, then adds. The buffer therefore settles at 51. Same pattern for group tells (`:602-603`).
- **Stale pride reads.** `getPrideStat` ignores expiry (`:118-123`). Until a bump, an expired period reports the old totals.
- **Level-history pairing and cut-off.** Section 5 covers the off-by-one pairing and the hard-coded epoch at `Stat.java:1055`.
- **Asymmetric deferred-XP setter.** `setRolePlayXP` rejects negatives (`:2031-2032`). `setDeferredXP` does not (`:2070-2076`).
- **Birthday cache.** The clock cache is not cleared on change and can throw (section 11).
- **Password handling (`Libraries/CMEncoder.java`).** With `HASHPASSWORDS` on, the stored value is a salt and a Java `String.hashCode()` of the lowercased password plus the salt (`:103-108`). That hash is 32 bits and case-insensitive. With it off, passwords are stored in plain text (`:112-119`). The password is concatenated into SQL unescaped in `MOBloader.java:2322`, `:3182`, `:3199` and `:3219`. In `:3199`, the account name in the WHERE clause is also unescaped. The delete and create paths call `DB.injectionClean` on the name (`:3207`, `:3217`), but the update path at `:3199` does not. Base64 output from the hash contains no quote characters, so the risk is mainly in plain-text mode and in the unescaped account name.
- **Subscription key workaround.** The leading-space rewrite (section 12) is undocumented.
- **Legacy XML fallbacks.** Indexed `ALIAS0`/`ALIASV0` and `TITLE0` loops (`DefaultPlayerStats.java:1196-1204`, `:1241-1257`) appear to exist for older save formats.
- **Hygiene and combat counters are not saved.** Section 4 covers these. Both are lost on reload, unless some path outside this bucket saves them (none found).
- **Unused fields.** `AREAS_EXPLORED` has no bumps. `HYGIENE_RAINCLEAN` has no callers.
- **Multiplayer assumptions.** Accounts and their character limits, cross-player tells and group tells, pride leaderboards and their top-N charts, the ignore and friend lists, and inter-MUD transfer data (`IMudTransferData`) all assume other people are present.

## Takeaways for a Python SUD

- **Keep the data model, drop the XML.** Use a `@dataclass` per character, with explicit typed fields and `to_dict`/`from_dict` for JSON or SQLite. The 39-code string switch is useful only for dynamic access, so an explicit `get`/`set` for the few stats scripts need may be enough. Keep typed enums for flags and counters, as the Java does (`PlayerFlag`, `PrideStat`, `PlayerCombatStat`).
- **Write the period counters without the lazy-reset trap.** Store counts keyed by a period key computed at read time from game time, for example `("day", "2026-10-22")` or `("week", "2026-W43")`, instead of resetting on bump. Reads then never return stale totals (`DefaultPrideStats.java:95-123` shows the trap).
- **Keep the level chronicle as a list of records** (level, game time, location id, minutes played, costs as a small dict). It is a low-cost feature that gives the player history. Store time played in seconds rather than whole minutes if the world runs in real time, and count turns if it is turn-based.
- **Model hygiene or grime as a bounded number with thresholds.** The `DELIMIT`-based charisma penalty is a clean pattern. For a single-player game, consider making grime affect social checks or a few concrete mechanics rather than a single stat, and make sure it is saved.
- **Drop accounts.** In a single-player game an account reduces to save-slot metadata (name, created time, settings). Drop passwords, character limits, online limits and account flags unless a feature needs them.
- **Drop tells, group tells and leaderboards, or make them local.** A bounded `collections.deque(maxlen=50)` is enough for a message log if the SUD has NPC chatter. Top-N charts are not needed.
- **Experience pacing.** The XP bank is a multiplayer-pacing device. If a single-player game wants pacing, a cooldown or a capped "rest" bank is simpler to reason about than the time-gated release in `gainExperience`.
- **Titles.** A list whose first item is active, with a `{name}` placeholder, reproduces the `*` behaviour without the join rule.
- **Ignore and friends.** In one-player form they are not needed. If the SUD has NPC social rules, a plain `set` of names is enough.
- **Birthdays.** Store age in game days. Derive the year and month from the game calendar when displayed, and avoid the cached clock.
- **Combat counters.** Keep them as a `dict[level][stat] -> int` with a `bump()` method and a ceiling. The Java design is reasonable, but it should be saved.
- **Do not copy:** the tell-buffer off-by-one, the unescaped SQL, the typo'd stat name, and the `copyOf` sharing.

## Coverage

- **Read in full (all 7 files, 5681 lines):** `Common/DefaultPlayerStats.java` (2540), `Common/interfaces/PlayerStats.java` (1312), `Common/DefaultPlayerAccount.java` (1013), `Common/interfaces/AccountStats.java` (372), `Common/interfaces/PlayerAccount.java` (207), `Common/DefaultPrideStats.java` (159), `Common/interfaces/PrideStats.java` (78).
- **Read in part, for interactions only:** `Libraries/CMPlayers.java` (1070-1200, 2080-2120); `Libraries/CoffeeLevels.java` (296-310, 925-1074, 1160-1257, 1371-1383); `core/database/MOBloader.java` (2260-2275, 2316-2326, 2660-2690, 3176-3236; the column names at 184-197 and 2340-2375 were seen through grep output); `Libraries/CommonMsgs.java` (746-754, 940-975, 2205-2275); `Libraries/MUDTracker.java` (1780-1800); `Libraries/MUDFight.java` (2956-2970); `Locales/StdRoom.java` (922-937, 2855-2880); `Commands/Remort.java` (84-100, 434-452, 545-575, 625-660); `Commands/Stat.java` (1040-1065); `Commands/Affect.java` (56-70); `Abilities/Misc/Age.java` (1020-1040, 1080-1090; lines 931-933 seen through grep); `Libraries/CharCreation.java` (2444-2460, 2606-2624, 4362-4392); `Libraries/CMEncoder.java` (100-135); `Common/interfaces/TimeClock.java` (736-830); `MOBS/StdMOB.java` (940-956); `core/collections/SVector.java` (40, 42); `core/interfaces/CostDef.java` (33-43, via grep).
- **Searched only (grep hits, not read):** all other callers of the pride, hygiene, level, combat, title, tell, account and stat-code methods across the tree, and the `CMProps` defaults for PRIDECOUNT, PAGEBREAK, TRIALDAYS and the XP deferral keys.
- **Skipped:** the achievement, tattoo and clan internals, the login and menu flow in `CharCreation`, `MOBloader` beyond the lines listed, and the `CMSecurity` flag definitions.
- **Not verified at runtime:** nothing in this chapter was executed. Every behaviour above comes from reading the code. Points marked "unverified", "not verified at runtime", or "no caller found" are the ones to test first.
