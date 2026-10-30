# Character creation, experience and levels

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b021` (Character creation, experience and levels) · 4 files · 8108 lines of Java.

## Summary

Two subsystems share this bucket. `CharCreation` is a resumable login and account state machine: name entry, an optional account menu, then a fixed sequence of creation steps (password, e-mail, ANSI, theme, race, gender, six base stats, class, faction "alignment" choices, optional deity, finish). `CoffeeLevels` owns experience: the per-kill split, level-difference scaling, the level chart, the per-level gains of hit points, mana, movement, attack, practices and trains, and the reverse "de-level" path. Nearly every number is data: the level chart, the stat budget, the stat cost table and the class gain formulas come from `coffeemud.ini`, `resources/lists.ini` and class code strings evaluated by the CMath formula language. For a single-player SUD, the creation wizard, the stat budget and the data-driven progression are worth borrowing. Accounts, group XP, clan taxes, liege homage and channel broadcasts are multiplayer machinery to drop.

## Key files and types

Paths are relative to the CoffeeMUD folder. Prose cites `File.java:line` with the directory given here.

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Libraries/CharCreation.java` | Login and account state machine (`LoginState` enum, `LoginSessionImpl`), base-stat rolling and point buy, race, class, gender, faction and deity prompts, character finish, name rules, creation limits, random name generator | 5394 |
| `com/planet_ink/coffee_mud/Libraries/CoffeeLevels.java` | `ExpLevelLibrary` implementation: XP gain and loss, `level()` and `unLevel()`, per-level HP, mana, move and attack, XP modifiers, level cap | 1746 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/CharCreationLibrary.java` | API, plus nested enums `LoginResult`, `NewCharNameCheckResult` and interface `LoginSession` | 565 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/ExpLevelLibrary.java` | API for XP and levels, nested `ModXP` class | 403 |

Supporting code outside the bucket, cited where used: `com/planet_ink/coffee_mud/Libraries/MUDFight.java` (kill-XP split), `CharClasses/StdCharClass.java` (default class formulas and first-level grants), `MOBS/StdMOB.java` (XP helpers), `Common/interfaces/CharStats.java` (stat codes), `Common/DefaultPlayerStats.java` (per-level records), `Libraries/CoffeeUtensils.java` (cost tables), `core/CMath.java` (formula language), `core/CMProps.java` (defaults), `coffeemud.ini`, `resources/lists.ini`, `resources/factions/*.ini`.

## How it works

### 1. Login and character creation state machine

- `LoginState` (`CharCreation.java:93-155`) enumerates every login, account-menu and creation step. `LoginSessionImpl` (`:162-225`) holds the in-flight `login`, `acct`, `mob`, `theme`, `statPoints`, `baseStats` and an `index` used to walk the faction list.
- `loginSubsystem` (`:1007-1133`) is the dispatcher. Each handler reads the last line of input, sets `state` and returns `NO_LOGIN`, `NORMAL_LOGIN` or `INPUT_REQUIRED`. The blocking wrappers loop on `session.blockingIn(...)`: `createCharacter` (`:5205-5241`) and the `promptRace`, `promptGender` and `promptCharClass` helpers (`:5130-5202`) wait 90 s per prompt (`:5219`, `:5143`, `:5168`, `:5193`). `promptBaseCharStats` takes its timeout from the caller (`:5119`).
- Name entry (`loginName`, `:1188-1289`). An empty line prints help. `*` substitutes a random name from `generateRandomName(3,6)`, retried with `(3,8)` until `isOkName` passes and the name is free (`:1227-1234`). A trailing `!` sets a wizard-invisible login (`:1235-1240`). The name is then capitalised (`:1241`).
- Account system off (the default, `COMMONACCOUNTSYSTEM=1`, `coffeemud.ini:1321`). `CMProps.isUsingAccountSystem()` is true only when the value is above 1 (`core/CMProps.java:1131-1134`). An existing name asks for its password (`:1274-1287`, then `loginPassStart` `:1525-1583`). An unknown name gets "Is this a new character you would like to create (y/N)?" (`:1539-1543`). `Y` enters `CHARCR_START` (`loginNewcharConfirm`, `:1585-1601`).
- Account system on. The account is looked up first (`:1242-1244`). An unknown name gets "Is this a new account you would like to create (y/N)?" (`:1531-1535`). `Y` enters `ACCTCREATE_START` (`loginNewaccountConfirm`, `:1603-1611`).
- Account creation. ANSI prompt, default Yes (`acctcreateStart` `:1350-1392`, `acctcreateANSIConfirm` `:1394-1436`). Password, checked only for emptiness (`acctcreatePassworded` `:1509-1523`). Optional e-mail (`:1438-1507`). Then `finishCreateAccount` (`:721-775`). If no password was given and `EMAILREQ` is not `DISABLE`, a random password is generated and e-mailed (`:730-750`). If a legacy character with the account's name already exists, the session is sent to `IMPORT <name>` (`:768-772`).
- Account menu (`acctmenuCommand`, `:1943-2550`, keywords only). HELP or `?`, LIST, QUIT, NEW, MENU, ANSI/COLOR variants (16, 256, TRUE, OFF), PASSWORD, EMAIL, RETIRE/DELETE, EXPORT (only with the `CANEXPORT` flag), IMPORT. Any other word logs in as that character. Otherwise "'X' is an unknown character or command" (`:2512-2523`).

Creation steps run in this order, each a `charcr*` method:

1. Password (`charcrStart` `:2639`, `charcrPasswordDone` `:2671-2727`). Not asked when the account already has one or e-mail-password mode is on. Length must be 1 to 40 (`:2679`).
2. E-mail (`:2729-2851`). `EMAILREQ=OPTIONAL` (`coffeemud.ini:1275`) makes it skippable. Validated with `CMLib.smtp().isValidEmailAddress` and typed twice.
3. ANSI (`:2815` prompt, `:2853-2914`). Asked here only for characters without an account.
4. Theme (`:2916-2979`). `MUDTHEME=1` (`coffeemud.ini:1053`, fantasy only) skips the prompt (`:2900-2912`). Theme bits: FANTASY 1, TECHNOLOGY 2, HEROIC 4 (`Areas/interfaces/Area.java:709-713`).
5. Race (`charcrRaceStart` `:3018-3081`, re-entry `:3083-3137`, confirm `:3139-3153`, done `:3155-3165`). Lists the qualifying races, accepts a name, a prefix or `?` for help, then asks "Is X correct (Y/n)?". `charcrRaceDone` sets HP, move and mana to `STARTHP`, `STARTMOVE` and `STARTMANA` (`:3158-3160`).
6. Gender (`charcrGenderStart` `:3167-3208`, done `:3210-3244`). Choices come from the `GENDERS` list (`resources/lists.ini:896-899`). Entries whose code ends in `-` are skipped (`:3172-3176`), so the neuter entry is never offered.
7. Base stats (`:3246-3608`, section 2).
8. Class (`:3617-3826`, section 3).
9. Factions, including alignment (`:3828-3978`, section 3).
10. Deity (`:3980-4114`). Reached only when `DEITYPOLICY` sets a `REQUIREDMASK` and the character qualifies for at least one deity (`:3969-3977`). Cleric-like characters (FAITH at least 100) use `getClericRequirements()`, others `getWorshipRequirements()` (`:3995-3997`). Choosing sends a `MSG_SERVE` message (`:4097-4105`).
11. Finish (`charcrStartFinish` `:4116-4129`, `charcrFinish` `:4131-4210`). Start-room `bringToLife` (`:4155`), `DBCreateCharacter` (`:4160`), `addPlayer` (`:4161`), the `END` creation script (`:4163`), new-player channel and coffee-table announcements (`:4177-4180`), then `completePlayerLogin` if the character belongs to an account (`:4194`).

### 2. Base stats

- Six base stats: STRENGTH 0, INTELLIGENCE 1, DEXTERITY 2, CONSTITUTION 3, CHARISMA 4, WISDOM 5 (`Common/interfaces/CharStats.java:48-58`, definitions `:853-858`). Each has a MAX-adjust code (22 to 27, `:92-102`). `CODES.toMAXBASE` maps base to max-adjust, and the effective maximum is `BASEMAXSTAT` plus that adjust.
- Budget (`getTotalBonusStatPoints`, `:236-257`). `MAXSTATS` (`coffeemud.ini:575`, 70) is clamped to between `(BASEMINSTAT+1)*6` and `(BASEMAXSTAT-1)*6`, then player and account bonus points are added, and `BASEMINSTAT*6` is subtracted. Defaults `BASEMINSTAT=3` (`coffeemud.ini:581`) and `BASEMAXSTAT=18` (`:586`) give 70 − 18 = 52 points to spread over six stats that start at 3 and cap at 18.
- The mode is chosen by `STARTSTAT` (`coffeemud.ini:610`, default 0, read at `:3249`):
  - Above 0: every stat is set to that value, then the bonus points are dealt at random (`:3252-3263`). No prompt.
  - 0, the default: random roll. `reRollStats` (`:260-286`) deals the points one at a time to a uniformly random stat (`roll(1,6,-1)`, `:276`), skipping stats at the cap. The totals are shown with "This would qualify you for ..." and the player is asked "Would you like to re-roll (y/N)?" (`:3367-3372`). Before showing, the code re-rolls up to 50 times until at least one class qualifies (`:3317-3324`). If none does, the last roll is kept and the class fallback applies (`:3645-3670`).
  - Below 0: interactive point buy in `charcrStatPick` (`:3435-3608`). Commands: `Str 3` adds 3, `-Str 2` removes 2, `R` re-rolls, `?` shows help. With 0 points left, Enter finishes (`:3443-3454`). A bare add prompts "How many points to add or remove (ex: +4, -1)" (`:3530`).
  - Overrides. With `CHARCRSTAT` disabled, `STARTSTAT<0` sets every stat to the minimum (`:3267-3268`), and `STARTSTAT=0` rolls each stat separately in 3 to 17 (`:3270-3276`). With `ATTRIBS` disabled and `STARTSTAT<=0`, the local `startStat` is set to 10 (`:3250-3251`) and the fixed-value branch runs. That branch reads the config value again (`:3254`), so every stat gets the config value, 0 by default (see Problems).
- Costs. `getStatCosts` (`:3420-3433`) compiles `STATCOSTS` (`coffeemud.ini:598`, default `<18 1, <22 2, <25 3, <99 5`, `core/CMProps.java:2909`) with `CoffeeUtensils.compileConditionalCosts` (`Libraries/CoffeeUtensils.java:1834`). The first matching condition wins, so raising a stat from value v costs 1 for v below 18, 2 for 18 to 21, 3 for 22 to 24, and 5 for 25 to 98. Refunds use the same tier (`:3544-3553`).
- Caps. A stat cannot be raised above `BASEMAXSTAT` (`:3585-3598`) and cannot be lowered below its rolled base (`:3566-3581`).
- Training uses the same table. `getTrainingCost` (`:4937-4964`) prices a stat increase in trains at `STATCOSTS`, capped at `BASEMAXSTAT` plus the max-adjust (`:4940-4941`).

### 3. Class, factions and deity

- Class qualification. `classQualifies` (`:343-360`) runs `canChangeToThisClass` (`:289-305`) over every class. That checks the theme bit, `qualifiesForThisClass` (`CharClasses/StdCharClass.java:360-420`, including the `MULTICLASS` rule string) and the class-level limit. Paladin adds an alignment check (`CharClasses/Paladin.java:309-315`).
- Selection (`charcrClassInit`, `:3617-3711`).
  - No qualifying class: the class whose `SubClassRule` is ANY (the last one found, with no `break`, `:3648-3653`), else `Apprentice`, else `StdCharClass` (`:3645-3670`).
  - Exactly one qualifying class, or no session: chosen automatically (`:3672-3696`).
  - `CHARCRCLASS` disabled: random pick (`:3698-3705`).
  - Otherwise the class list is shown and typed (`:3713-3793`).
- `charcrClassDone` (`:3795-3826`): class level 1 (`:3798-3799`), universal starting items (`:3804`, section 4), wimpy 5 (`:3805`), race outfit (`:3807`), an "Allergies" effect if not disabled (`:3809-3814`), full state (`:3819`). Then the faction step.
- `charcrFactionDone` (`:3927-3978`) calls `startCharacter` (`:3932`). That grants the first-level practices (`getPracsFirstLevel()` = 5, `StdCharClass.java:92-95`), the first-level trains (`getTrainsFirstLevel()` = 3, `:98-101`) and the class abilities (`:1330-1331`). It also applies the class outfit, sets the start room, initialises age and grants starting money.
- Factions (`charcrFactionNext` `:3828-3871`, `charcrFactionPick` `:3873-3925`). Every faction is walked, skipping disabled ones (`:3832-3841`). Auto-defaults (`findAutoDefault`) apply silently (`:3843-3845`). If the mob has exactly one allowed value, it is set (`:3846-3850`). Otherwise the faction's intro text is shown and the player types a choice (`:3856-3869`). The typed text is matched by prefix, then substring, against the range names (`:3886-3910`, names from `getNamedFactionChoices` `:4212-4224`).
- Alignment is just a faction, configured in `resources/factions/alignment.ini`:
  - `AUTODEFAULTS=-10000 -RACE +Demon +Devil;0` (line 69). Demons and devils start at -10000 and everyone else at 0.
  - `AUTOCHOICES=-7499; 0; 7500` (line 77), the selectable values.
  - `CHOICEINTRO=text/alignment.txt` (line 84).
  - Seven named bands from "pure evil" to "pure goodness" over -10000 to 10000 (lines 100 to 106).
- `resources/factions/inclination.ini` uses the same mechanism with class-keyed defaults (line 69: Fighter or Paladin 10000, Barbarian -5000, Thief -10000, Devil -10000, otherwise 0). Its choices are -10000, 0 and 10000 (line 78).
- `resources/factions/triumph.ini` has no choices (line 82) and one name-keyed auto-default (line 73), not reproduced here.

### 4. Names, limits and the random name generator

- `isBadName` (`:399-426`) checks a built-in `DEFAULT_BADNAMES` list (`:83-89`, a short list of reserved and profane words, not reproduced) and the configurable `BADNAMES` list (`coffeemud.ini:1302-1324`). Entries may carry `*` wildcards at either end (`:417-422`).
- `isOkName` (`:429-481`). 3 to 20 characters (`:431-433`). Letters only, with spaces allowed when the caller permits (`:436-455`). Not a deity, mob-type or clan name (`:457-478`). Not banned (`:480`). The article-stripping loop at `:439-446` builds a list that is never used.
- Limits. `MAXNEWPERIP=3` (`coffeemud.ini:57`) counts new characters per IP address over a rolling 24 hours (`core/CMProps.java:2151-2183`, `finishNameCheck` `CharCreation.java:4226-4242`). `MUDTHEME=0` closes new logins (`:4228`). With the account system, `COMMONACCOUNTSYSTEM` plus bonus slots is the characters-per-account cap (`:4369-4377`, `numAccountsAllowed` `:4383-4391`).
- Random names (`generateRandomName`, `:5278-5329`). Syllables come from two weighted tables (`:5257-5275`). Each token is letters followed by one digit, read as a bitmask: 2 means the syllable may open a name, 4 means it may sit in the middle, and 1 means it may close one (`:5299-5320`). The syllable count is uniform between the bounds (`:5282`).

### 5. Experience: pool, share and scaling

The kill path runs in four stages. The numbers are in section 7.

1. `MUDFight.dispenseExperience` (`Libraries/MUDFight.java:3300-3346`) builds a group pool from the number of participants, then gives each killer a share.
2. Each share is `postExperience` (`CoffeeLevels.java:1077-1107`), a `TYP_EXPCHANGE` message that the room can veto or modify before it reaches `handleExperienceChange` (`:1507-1530`).
3. `gainExperience` (`:906-1001`) applies the XP percentage stat, global `XPMOD` rules, the level-difference scaling, clan and liege shares, the pride counter, deferral, the level cap and the message.
4. `checkedLevelGains` (`:635-658`) levels up as many times as the XP allows.

### 6. Level chart and thresholds

- `EXP_CHART` (`resources/lists.ini:560-659`) holds 100 values, indexed by level. Examples: level 1 needs 500 XP, level 2 needs 1100, level 5 needs 3500, level 10 needs 9500, level 50 needs 146,500, level 91 needs 351,500, and index 99 is 391,500. Past the end, `getLevelExperience` extrapolates with the last difference, 5000 here (`CoffeeLevels.java:1155-1165`), so level 100 needs 396,500.
- Level L to L+1 needs `chart[L]`. `getExpNextLevel` is `getLevelExperience(level)` (`MOBS/StdMOB.java:275-278`), and `checkedLevelGains` loops while the XP is enough (`CoffeeLevels.java:635-658`).
- Cap. `LASTPLAYERLEVEL=91` (`coffeemud.ini:515`). At 91, `getExpNeededLevel` returns `Integer.MAX_VALUE` (`StdMOB.java:303-307`), so no further levels are gained. XP keeps accruing until `getGainedExperienceCap`, which is `round(1.02 * chart[91])` = 358,530 (`CoffeeLevels.java:1137-1153`, checked at `:985-988`).
- Losing levels. `checkedLevelLosses` (`:514-531`) de-levels only while XP is below `getLevelExperience(level-2)`, which is the start of the previous level. A character therefore keeps a full level of buffer before losing a level.
- Caps for NPCs use the same `LASTPLAYERLEVEL` value (armor formula, section 10).

### 7. Kill experience, in full

1. The pool, `FORMULA_TOTALCOMBATXP` = `100 + ((25*@x1) - (@x1*((25*@x1)^.5)))` (`coffeemud.ini:2489`, default at `core/CMProps.java:3230`). With n participants this is 100 + 25n − 5n^1.5. Hand-computed values: 120 for one participant, about 136 for two, 160 for four, a peak of about 193 near n = 11, and below zero from about 32 participants (900 − 905 at n = 32). The variables are `dividers.size()`, the sum of squared levels, the sum of levels, `killers.size()`, the highest level and the victim level (`MUDFight.java:3314-3326`).
2. Each killer's share, `FORMULA_INDCOMBATXP` = `(@x1 * (@x2 / @x3)) < 100` (`coffeemud.ini:2498`, default `core/CMProps.java:3231`). `@x1` is the pool, `@x2` the killer's level squared and `@x3` the sum of squared participant levels (`MUDFight.java:3328-3343`). The `<` is CMath's minimum operator (`core/CMath.java:1444`), so the share is capped at 100. A solo kill therefore yields at most 100 before scaling. Equal-level groups get pool/n each, so a pair of equal killers shares about 136 in total.
3. `postExperience` (`CoffeeLevels.java:1077-1107`) builds the message with the amount in `msg.value()`. The room may veto it (`:1091`) or modify it (`:1094`). Without a room, `gainExperience` runs directly (`:1101-1104`).
4. `gainExperience` (`:906-1001`), in order:
   - XP percentage stat `STAT_XP_ADJ_PCT` (`:921-926`).
   - Global `XPMOD` rules (`modGlobalExperience` `:577-591`, parser `:719-872`). Empty by default (`coffeemud.ini:1225`).
   - `adjustedExperience` (`:1439-1505`). Class and race `adjustExperienceGain` hooks run for each group member (`:1448-1456`). Then scaling against `EXPRATE` = 6 (`coffeemud.ini:406`), called L:
     - If the victim's level minus the killer's is below -L, the award is 0 (`:1481-1482`).
     - If the strongest player in the room is more than L levels above the victim, the award is multiplied by (L / that gap) squared (`:1486-1489`). This applies only when `CMLib.flags().getDirType(victim)` is COMPASS (`:1458-1461`); the meaning of that flag is not verified.
     - If the killer is more than L levels below the strongest player, the award is multiplied by (L / gap) squared (`:1490-1491`).
     - Otherwise the award becomes amount + round(min(d/L, L) × amount), where d is the victim level minus the killer level (`:1493-1499`). A victim three levels above gives +50%, one three levels below gives -50%, and one six or more below gives 0.
   - Clan share through `applyExpMods`, once level is at least `MINCLANLEVEL` (`:604-615`, `coffeemud.ini:1551`).
   - Liege share: 10% to the homage owner, at least 1 (`:617-633`).
   - Pride counter `EXPERIENCE_GAINED` (`:937`).
   - Deferred XP (`:941-983`). Off by default (`EXPDEFER` empty, `coffeemud.ini:774`). When on, XP is banked and paid out after `EXPDEFER_SECS` or on a command. `deferCommandCheck` (`:1532-1644`) intercepts commands.
   - Level cap check (`:985-988`). The message is "You gain ^H<amount>^N^! experience points." (`:991-997`). The amount term from `getXPAmountTerm` (`:874-904`) becomes words when the `EXP_WORDS` prowess is set (`core/CMProps.java:476`): "barely any", "a trifling amount of", ..., "an extraordinary amount of" (`resources/lists.ini:1988-2001`).
- RP XP is a parallel path, `gainRPExperience` (`:1003-1075`), gated by `RP_AWARD_PCT`.
- `getEffectFudgedLevel` (`:1404-1437`) shifts a player's power level by `EFFECTCXL` for each effect from a higher-level non-deity caster (`coffeemud.ini:459`, default 0, so off). It feeds the scaling (`:1441`, `:1477`) and the score display (`Commands/Score.java:143`).

### 8. Level-up

`level(mob)` (`CoffeeLevels.java:1182-1369`):

- Guards (`:1185-1189`). The `LEVELS` security flag, `leveless` class or race, and `isLevelCapped`. A global `MSG_LEVEL` message can be vetoed (`:1191-1192`), then the room (`:1196-1197`).
- Group members get a chat line, "I have gained a level." (`:1201-1212`).
- `doBaseLevelAdjustment` (`:301-402`) applies the gains:
  - HP: `getPlayerHPBonusNextLevel` (`:188-212`), floored at `STARTHP` (`:364-365`).
  - Move: `getMoveBonusNextLevel` (`:162-177`).
  - Attack: `getAttackBonusNextLevel` (`:93-109`).
  - Mana: `getManaBonusNextLevel` (`:55-70`).
  - Damage +1 every `LevelsPerBonusDamage` levels (`:392-399`, default 1 per `StdCharClass.java:104-107`).
  - The gains are stored per level (`recordLevelData` at `:1243`, `Common/DefaultPlayerStats.java:1842`).
- Practices (`:1221-1231`) are `floor(min(WIS, cap)/6)` plus the class bonus, minimum 1. Trains (`:1234-1238`) are always 1.
- Messages (`:1217-1239`). "^xYou have L E V E L E D ! ! ! ! ! ^.^N", then "You are now a ...", then "You have gained N hit points, M move points, K attack points, J points of mana, P practice points, and 1 training session."
- Abilities and expertises. Class and race grants (`:1280-1283`), autoinvoke of qualifying abilities (`:1286-1296`), "You have learned the ..." lines for new abilities (`:1298-1317`) and expertises (`:1319-1338`), then `curClass.level(...)` and the race `level(...)` hooks (`:1347-1348`). Achievements are bumped at `:1362-1365`.

Per-level gain formulas, defaults from `CharClasses/StdCharClass.java` and overridable per class:

| Gain | Default formula (as written) | Meaning | Source |
|---|---|---|---|
| HP | `((@x6<@x7)/3)+(1*(1?6))` | min(CON, maxCON)/3 plus a uniform real in [1, 6.5) | `StdCharClass.java:118-120` |
| Mana | `((@x4<@x5)/3)+(1*(1?6))` | min(INT, maxINT)/3 plus the same roll | `StdCharClass.java:126-128` |
| Move | `5*((@x2<@x3)/18)` | 5 times min(STR, maxSTR)/18 | `StdCharClass.java:110-112` |
| Attack | floor(attack stat / 18) + class bonus, +1 at 22, +2 at 25 | attack stat is the class attribute, default STR | `CoffeeLevels.java:93-109`; `StdCharClass.java:86-89` |
| Damage | +1 per 1 class level | `LevelsPerBonusDamage` default 1 | `StdCharClass.java:104-107` |
| Practices | floor(WIS/6) + class bonus, minimum 1 | first class level grants 5 more | `CoffeeLevels.java:1221-1231`; `StdCharClass.java:92-95` |
| Trains | +1 per level | first class level grants 3 more | `CoffeeLevels.java:1234-1238`; `StdCharClass.java:98-101` |

Variable order for the HP and move formulas (`CoffeeLevels.java:165-175`, `:191-201`): x1 level, x2 STR, x3 maxSTR, x4 DEX, x5 maxDEX, x6 CON, x7 maxCON, x8 WIS, x9 INT. Mana (`:58-68`) uses x2 WIS, x3 maxWIS, x4 INT, x5 maxINT, x6 attack attribute, x7 its max, x8 CHA and x9 CON.

Worked values from the defaults. A character at CON 18 gains about 6 + 3.75 = 9.75 HP per level on average, and one at CON 3 about 4.75. Starting values are HP 20, mana 100, move 100 (`coffeemud.ini:520-530`). Current HP at level L is `STARTHP + (L-1) * bonus` (`CoffeeLevels.java:215-219`). A zero or negative HP gain returns 1 if CON is at least 1, else 0 (`:205-210`).

### 9. De-level

`unLevel` (`CoffeeLevels.java:404-495`):

- Guards (`:407-412`). Message "^ZYou have ****LOST A LEVEL****^.^N" (`:420`), with optional channel posts.
- XP is cut back (`:430-431`).
- The gains of the lost level are read from the stored record (`:434`, `DefaultPlayerStats.java:1836-1841`) and reversed exactly.
- Practices and trains are removed (`:445-456`).
- Abilities whose qualifying level equals the lost class level, which are default-gain and class-only, are forgotten ("You have forgotten X.", `:465-484`).
- NPCs go through `fixMobStatsIfNecessary` (`:1385-1402`).

### 10. NPC stats

`fillOutMOB` (`:221-245`, `:267-293`) and `getLevelMOB*` (`:111-140`):

- Speed `1 + floor(level/30)` (`:136-140`).
- Armor `102 - round((lvl + levelMid) * (lvl/levelLow))`, where levelMid = 0.44 × `LASTPLAYERLEVEL` and levelLow = levelMid / 2 (`:118-126`).
- Damage equals the level (`:128-133`). Attack is `(level-1)*attackBonus + level` (`:111-116`).
- HP comes from `CMLib.dice().rollHP` (`:79-91`). XP is `getLevelExperience(level)` (`:291`).
- `getPowerLevel` (`:142-160`) combines damage, speed, armor and attack into one score.

## Content worth mining

**Stat budget and costs (for a single-player SUD).** Six stats from 3 to 18, a 52-point budget, cost tiers 1/2/3/5 from `STATCOSTS` (`coffeemud.ini:598`). The same table prices training (`CharCreation.java:4937-4964`).

**Starting items per race** (`coffeemud.ini:620-622`). The shipped value is "1 Ration, 1 Waterskin, 1 Torch, Pixie, 1 Apple, 1 Waterskin, 1 Torch, Mindflayer, 1 Waterskin, 1 Torch". A race name opens its own section, and a race with a section receives only that section (`CharCreation.java:829-885`, `:874`). Pixies receive an apple, a waterskin and a torch. The ini comment (`:618-619`) and the code default (`core/CMProps.java:2848`) name the same three items in a different order. The shipped value adds the Pixie and Mindflayer sections.

**Experience chart** (`resources/lists.ini:560-659`), one number per level, extrapolated past 99. Sample: 0, 500, 1100, 1800, 2600, 3500, 4500, 5600, 6800, 8100, 9500 for levels 0 to 10. Level 91 is 351,500.

**Experience words** (`resources/lists.ini:1988-2001`). Thresholds 9, 25, 35, 50, 70, 90, 110, 135, 175, 250, 400, 1000, then "an extraordinary amount of". Good flavour text for a single-player game.

**Alignment bands** (`resources/factions/alignment.ini:100-106`): pure evil, evil, somewhat evil, pure neutral, somewhat good, good, pure goodness. Each band is named, with a flag (EVIL, NEUTRAL, GOOD).

**Genders** (`resources/lists.ini:896-899`). Three entries, M, F and a neuter marked `N-` that is never offered. Each gives pronoun and title forms (for example him/his/he and sir/mister for M, her/her/she and madam/ms. for F, and it/its/it for the neuter).

**Name generator** (`CharCreation.java:5257-5275`). Weighted syllable tables with start, middle and end bitmasks. The data format is reusable for NPC or town names once the vowel bug is fixed (section Problems).

**Limits and formulas** (`coffeemud.ini:2504-2518`).
- Max carry: `@x1 + ((@x2 + 10.0) * @x2 * @x1 / 150.0) + (@x2 * 5.0)`, where x1 is base weight and x2 is current STR.
- Max items: `(2 * @x1) + (2 * @x3) + (2 * @x2)`, where x1 is the wear-slot count, x3 DEX and x2 level.
- Max followers: `1 + ((@x2 - 6.0) / 3.0)`, where x2 is CHA. Used by `getMaxCarry`, `getMaxItems` and `getMaxFollowers` (`CharCreation.java:5015-5068`).

**Creation messages worth reusing**: "Please choose from the following races (?):" (`:3076`), "Would you like to re-roll (y/N)?" (`:3369`), "This would qualify you for X." (`:3366`), "You have no more points remaining." (`:3378`), "You do not qualify for any classes. Please modify your stats until you do." (`:3447`).

## Notable design ideas

- Creation is one explicit state machine. Each step is a method that returns the next state, and steps can loop back (`RACECONFIRMED` returns to `RACESTART`, `:3131-3143`). Prompts are re-entrant, and the same states serve login, account menu and creation.
- The stat budget and cost table are data (`STATCOSTS` as conditional tiers, `CoffeeUtensils.java:1834-1900`). One table prices point buy, refunds and training.
- Retry until viable. The random roll is repeated up to 50 times until some class qualifies (`CharCreation.java:3317-3324`), so the character is never stranded.
- Factions as the generic mechanism. Alignment and "inclination" use the same engine: zapper-keyed auto-defaults, optional choice prompts, named ranges.
- Level-difference scaling. XP depends on the victim's level relative to both the killer and the strongest player nearby (`CoffeeLevels.java:1439-1505`). This discourages farming weak monsters and rewards fighting up.
- Squared-level shares (`MUDFight.java:3300-3346`). The pool grows with the group, peaks near a dozen members, then falls. Higher-level killers take a larger share, and each share is capped.
- Exact reversal. Per-level gains are recorded so that de-levelling undoes exactly what was given (`CoffeeLevels.java:434`).
- Hysteresis on de-level. A character loses a level only after dropping below the start of the previous level (`:514-531`).

## Problems and cruft

Verified by reading:

1. A bare `-STAT` changes nothing. `CharCreation.java:3514-3515` sets `statPointsChange = -1`, and then `:3534` (`if(statPointsChange <= 0)`) returns to `CHARCR_STATSTART` before any change. Only `-STAT N` works.
2. The XPMOD parser (`CoffeeLevels.java:719-872`) has a parenthesis-depth branch (`:811-826`) that never runs, because `paren` only changes inside `if(paren>0)`. The branch also indexes `s.charAt('i')` (`:813`, `:824`), a character literal where `i` was meant. Parentheses are not tracked, so operators inside them are treated as top-level operators. Nested XPMOD expressions are therefore unreliable (not run).
3. `getLevelExperienceJustThisLevel` is inconsistent beyond the chart. For `level >= len` it returns `(1+(level-len))*lastDiff` (`CoffeeLevels.java:1177-1179`), while `getLevelExperience` implies a constant step of `lastDiff`. Callers are `Abilities/Spells/Spell_Wish.java:2004-2042` and `Abilities/Common/SelfInvesting.java:186`. Only levels past 99 are affected. The interface doc (`Libraries/interfaces/ExpLevelLibrary.java:58-66`) says "gain at this level to advance to the next", but the in-chart code returns `chart[level] - chart[level-1]`, the cost to reach this level. Also, the doc block at `ExpLevelLibrary.java:226-236` for `getLevelHitPoints` says it returns mana.
4. The scaling cap at `CoffeeLevels.java:1496-1498` clamps `levelDiff/EXPRATE` to `EXPRATE` (6), which allows a multiplier of up to 7. The clamp looks like a typo for 1.0. The `||(levelDiff<0)` test at `:1493-1494` is always true inside that branch.
5. `CharCreation.java:5283` sets `isVowel = CMLib.dice().rollPercentage()>0`. `rollPercentage` returns 1 to 100 (`Libraries/Dice.java:409-411`), so the test is always true and every generated name starts with a vowel-class syllable, subject to the start mask. The intent was probably a coin flip.
6. Dead code. The `isOkName` article list (`CharCreation.java:439-446`) is never used. `translateAmount` (`CoffeeLevels.java:660-667`) and `isFilledOutMOB` (`:247-265`) have no callers.
7. The `CHARCRSTAT`-disabled random path (`CharCreation.java:3270-3276`) uses `roll(1, BASEMAX-BASEMIN, -1)`, which yields 3 to 17, so 18 is unreachable there. The normal path can reach 18.
8. With the default `BASEMAXSTAT=18`, the cost tiers for 18 and above (`STATCOSTS`) can never be used, because raising stops at the cap (`:3585-3598`).
9. Password rules differ. Characters must be 1 to 40 characters (`:2679`), while account passwords are only checked for emptiness (`:1512`).
10. Smaller items. `trainGain` is fixed at 1 and its `<=0` check is unreachable (`CoffeeLevels.java:1234-1235`). `recoverCharStats()` is called twice (`CharCreation.java:3331-3332`). `loginNewaccountConfirm` declares `result` that is always `NO_LOGIN` (`:1605`, `:1619-1625`).
11. Blocking sleeps on the session thread: `CMLib.s_sleep(1000)` (`CharCreation.java:4147`) and `2000` (`:743`).
12. Any input starting with "r" triggers a re-roll (`:3469`).
13. The race section in `STARTINGITEMS` replaces the default list rather than adding to it (`CharCreation.java:874`). This is probably intended, but it is easy to misread.
14. Multiplayer-only assumptions: liege homage (`CoffeeLevels.java:617-633`), clan taxes (`:533-554`, `:604-615`), group chat (`:1201-1212`), channel broadcasts (`:1244-1252`, `CharCreation.java:4177-4179`), login redirection and pending-login checks (`CharCreation.java:1551-1561`).
15. Asymmetric liege handling. Gains give the homage owner at least 1 point (`CoffeeLevels.java:625-627`). Losses take nothing when the amount is 2 or less (`:535`, `:558`).
16. The kill pool goes negative for about 32 or more participants (section 7). Large groups would lose XP. Whether that is reachable depends on group sizes the game allows, which is not verified.
17. The `ATTRIBS` override (`CharCreation.java:3250-3254`) is broken. The local `startStat` becomes 10, but line 3254 calls `setAllBaseValues(CMProps.getIntVar(STARTSTAT))` again. With the default `STARTSTAT=0`, every base stat is set to 0, and a negative `STARTSTAT` gives negative stats. Not run; read from the code.

## Takeaways for a Python SUD

- Creation as data-driven steps. Write each step as a function `step(state, io) -> next_step`, with the order held in a list. This is testable without a socket. Keep the steps that matter for a single player: name, background (race), gender, stats, class, and an optional patron or alignment choice. Drop password, e-mail, ANSI, theme and account steps.
- Stats. Six stats, 3 to 18, with a 52-point budget. Default to the roll-and-reroll option, which needs only a retry loop until the character qualifies for a class. Offer point buy as an alternative. Store the cost tiers as `[(18, 1), (22, 2), (25, 3), (99, 5)]` and reuse them for training.
- Level chart. A list of thresholds in data, with `level_for_xp` by `bisect`, a last-difference extrapolation and a hard level cap (91 here).
- Per-class gain formulas. Keep them as data expressions, evaluated by a small safe evaluator or by precompiled functions. Do not copy the CMath `<` operator (it means minimum); write `min()` explicitly.
- De-levelling. Only build it if the design has a death penalty. If it does, store a per-level record of what was gained and reverse it exactly, as `CoffeeLevels` does.
- XP. Keep three ideas: scaling by the victim's level against the player's level (one function, with a per-kill cap), the words-for-amount flavour text, and the hysteresis on level loss. Drop groups, clan taxes, liege homage, deferred XP, RP XP and XP modifier strings (or reduce modifiers to a simple list).
- Alignment. A single score from -10000 to 10000 with the seven named bands as data, rather than a generic faction engine. The "inclination" axis can be a second score with class-keyed starting values.
- Names. Port the bitmask syllable generator with the vowel flag fixed. Use it for NPC and town names.
- Starting kit. A dictionary per race with an explicit rule. Decide whether a race entry replaces the default kit or adds to it, and document the choice.
- Drop: accounts, IMPORT and EXPORT, IP limits, e-mail passwords, ANSI, MSSP, login redirection, channel broadcasts and group announcements.

## Coverage

- Read in full: `CoffeeLevels.java` (all 1746 lines); `CharCreationLibrary.java` (all 565); `ExpLevelLibrary.java` (all 403).
- `CharCreation.java` read in full or in long ranges: fields and state types (`:40-300`), class and race qualification and name checks (`:289-484`), account finish and class list (`:721-889`), dispatcher and login name (`:1007-1290`), account and new-character confirmations (`:1350-1630`), every `charcr*` creation step (`:2639-4212`), creation limits (`:4226-4430`), training cost and carry or item formulas (`:4937-5071`), prompts, `createCharacter` and the name generator (`:5105-5330`).
- Skimmed by grep: the account menu keyword list (`:1943-2550`) and formula compilation in `activate()` (`:5384-5394`).
- Not read: `:1630-1950` (redirect, password receive, e-mail password, account menu start and show), `:2550-2638` (`completePlayerLogin`), `:4245-4312` (spam check), `:4431-4936` (`finishLogin`, start, death and body room selection), `:4966-5104` (room configuration), `:5331-5383` (login driver).
- Outside the bucket, consulted for mechanics and numbers: `MUDFight.java:3300-3346`, `StdCharClass.java` (`:74-130`, `:194`, `:1330-1331`), `StdMOB.java:275-325`, `CharStats.java:48-58, 92-102, 853-858`, `DefaultPlayerStats.java:1836-1851`, `CoffeeUtensils.java:1834-1900`, `CMath.java:1372-1480, 1800-1852`, `Dice.java:398-412`, `CMProps.java` defaults, `coffeemud.ini` keys, `resources/lists.ini` (EXP_CHART, GENDERS, EXPERIENCE_AMOUNTS), `resources/factions/*.ini`, `Paladin.java:309-315`, `Prop_ReRollStats` use at `Abilities/Misc/Age.java:1037`.
- Not verified at runtime. Everything was derived by reading. Hand-computed values (the pool, the chart extrapolation, the HP averages) are marked as such in the text.
