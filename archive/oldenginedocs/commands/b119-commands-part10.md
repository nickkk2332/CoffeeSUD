# Commands, part 10: Remort to Shell

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b119` (Commands [part 10/12: Remort .. Shell]) · 20 files · 7971 lines of Java.

## Summary

This bucket is the tail of the typed command set, from REMORT to SHELL: 20 command classes, almost all of them one verb with a few aliases. Each is a prototype `Command` that is registered under its access words and charged in action units; most extend `StdCommand`, and a few extend `Go`, `Affect` or `Skills` to reuse their logic. Two are large staff tools. RESET (`com/planet_ink/coffee_mud/Commands/Reset.java`, 2669 lines) is a set of about thirty repair and data-migration utilities, and SHELL (`com/planet_ink/coffee_mud/Commands/Shell.java`, 1256 lines) is a file manager over CoffeeMud's virtual file system with copy, move, edit, search and a command runner. The player mechanics worth studying are REMORT, a prestige reset whose kept attributes come from a short text spec; SAY, which resolves a target from the words typed, swaps languages with the listener and emits markup-tagged messages; SHEATH, which treats container capacity as weight; and SERVE, whose liege is shown by SCORE. REPLY, REQUEST, SAVE USERS and RETIRE assume other human players and need rethinking for a single-player game.

## Key files and types

Every path, here and in the text, is relative to the CoffeeMUD folder and written in full.

| File | Role | Lines |
|---|---|---|
| com/planet_ink/coffee_mud/Commands/Reset.java | RESET: builder and admin repair and migration utilities (about 30 subcommands) | 2669 |
| com/planet_ink/coffee_mud/Commands/Shell.java | SHELL, CMFS, `.`: virtual-file-system shell, file ops, command runner | 1256 |
| com/planet_ink/coffee_mud/Commands/Remort.java | REMORT: level-1 re-creation that keeps attributes named in REMORTRETAIN | 789 |
| com/planet_ink/coffee_mud/Commands/Score.java | SCORE, SC: character sheet; extends Affect | 442 |
| com/planet_ink/coffee_mud/Commands/Say.java | SAY, `` ` ``, SA, SAYTO: speech with target and language handling | 434 |
| com/planet_ink/coffee_mud/Commands/Save.java | SAVE: persist rooms, mobs, items, players, races, quests, factions | 428 |
| com/planet_ink/coffee_mud/Commands/Sheath.java | SHEATH: put wielded or held weapons into worn containers | 274 |
| com/planet_ink/coffee_mud/Commands/Report.java | REPORT: say own status, or list skills and stats; extends Skills | 262 |
| com/planet_ink/coffee_mud/Commands/Request.java | REQUEST: post an item request with a broker | 158 |
| com/planet_ink/coffee_mud/Commands/Restring.java | RESTRING: opens the generic editor on a generic item | 157 |
| com/planet_ink/coffee_mud/Commands/Retire.java | RETIRE: permanently delete the character | 148 |
| com/planet_ink/coffee_mud/Commands/Sell.java | SELL: sell items to a shopkeeper | 147 |
| com/planet_ink/coffee_mud/Commands/Reply.java | REPLY, REP, RE: answer the last tell, say or yell | 146 |
| com/planet_ink/coffee_mud/Commands/Remove.java | REMOVE, REM: take off worn items | 130 |
| com/planet_ink/coffee_mud/Commands/Serve.java | SERVE: swear fealty to a player (liege) or a deity | 117 |
| com/planet_ink/coffee_mud/Commands/SetCmd.java | SET: forwards `SET <word>` to a command named `SET<WORD>` | 95 |
| com/planet_ink/coffee_mud/Commands/Right.java | RIGHT: directional walk; extends Go | 92 |
| com/planet_ink/coffee_mud/Commands/Run.java | RUN: walk with AUTORUN set for the move; extends Go | 79 |
| com/planet_ink/coffee_mud/Commands/Replay.java | REPLAY: re-show recent output lines | 75 |
| com/planet_ink/coffee_mud/Commands/Rules.java | RULES: show resources/text/rules.txt | 73 |
| com/planet_ink/coffee_mud/Commands/StdCommand.java | Base class (other bucket, cited for costs and helpers) | 316 |
| com/planet_ink/coffee_mud/Commands/interfaces/Command.java | Command contract (other bucket, cited) | 181 |

## How it works

### The command contract and action costs

- A command is entered as its access words (`getAccessWords()`), and `execute(mob, commands, metaFlags)` receives the parsed words with the access word first. The interface says `execute` performs the command and that its boolean return is "almost meaningless" (`com/planet_ink/coffee_mud/Commands/interfaces/Command.java:116-133`). Most commands here return `false` on every path.
- Cost is two methods: `actionsCost` out of combat and `combatActionsCost` in combat. The base class returns `CMProps.getCommandActionCost(ID(), 0.0)` (`com/planet_ink/coffee_mud/Commands/StdCommand.java:223-233`), so a command with no override is free unless the config sets an exception. `checkedActionsCost` picks the combat cost when the mob, or the vessel it is aboard, is in combat (`com/planet_ink/coffee_mud/Commands/StdCommand.java:241-262`).
- The default costs come from `coffeemud.ini`: `DEFCMDTIME=0.20` and `DEFCOMCMDTIME=1.0` (`coffeemud.ini:545-546`). They are stored as integers times 100 (`com/planet_ink/coffee_mud/core/CMProps.java:3099-3100`) and read back as 0.20 and 1.00 actions (`com/planet_ink/coffee_mud/core/CMProps.java:1183-1196`). Exceptions are keyed by the class ID.
- Commands in this bucket take one of four cost shapes: the default (0.20 / 1.00), a scaled default (Sheath, Run, Right), free (no override, so 0.0, which covers Reset, Shell, Save, Score, Restring, Replay and Rules), or zero by design (SetCmd).

### Command catalogue

All 20 classes. "Cost" is out of combat / in combat in action units. "Staff" marks builder and admin commands.

| Command (access words) | Class | Syntax and effect | Cost | Restrictions | Audience |
|---|---|---|---|---|---|
| RESET | com/planet_ink/coffee_mud/Commands/Reset.java | ROOM, AREA, REJUV, RELEVEL, SORTHELP, PASSWORD, INIFILE, ROOMIDS, AREAROOMIDS, and about 20 repair subcommands (see below) | 0 / 0 | `securityCheck` needs SecFlag.RESET (com/planet_ink/coffee_mud/Commands/Reset.java:56-60); most repair subcommands also need RESETUTILS (com/planet_ink/coffee_mud/Commands/Reset.java:1144-1148); many need sysop | Staff |
| SHELL, CMFS, `.` | com/planet_ink/coffee_mud/Commands/Shell.java | SHELL with one of 13 subcommands (table below); bare SHELL prints the working directory | 0 / 0 | `hasAccessibleDir` (com/planet_ink/coffee_mud/Commands/Shell.java:1251-1255; com/planet_ink/coffee_mud/core/CMSecurity.java:693-716); not orderable | Staff |
| REMORT | com/planet_ink/coffee_mud/Commands/Remort.java | Re-creates the character at level 1, keeping what REMORTRETAIN names; asks for the password | 0.20 / 1.00 | REMORTMASK must pass (com/planet_ink/coffee_mud/Commands/Remort.java:121-126); shipped mask "-LEVEL +>90" (coffeemud.ini:696); not orderable | Player |
| SCORE, SC | com/planet_ink/coffee_mud/Commands/Score.java | SCORE with prefix-matched BASE, INFO, STATS, DETAILS, AFFECTS or EFFECTS; SCORE ALL is the default (com/planet_ink/coffee_mud/Commands/Score.java:384-385) | 0 / 0 | none | Player |
| SAY, `` ` ``, SA, SAYTO | com/planet_ink/coffee_mud/Commands/Say.java | SAY message; SAY target message; SAYTO target message. Code also accepts SAYT (com/planet_ink/coffee_mud/Commands/Say.java:410-412) | 0.20 / 1.00 | SAYTO target must be visible (com/planet_ink/coffee_mud/Commands/Say.java:258-263) | Player |
| SAVE | com/planet_ink/coffee_mud/Commands/Save.java | SAVE [AREA] ROOM / MOBS / ITEMS / REWORLD; SAVE GRACES; SAVE QUESTS; SAVE FACTION id; SAVE USER name; SAVE USERS / PLAYERS / CHARACTERS [ALL]; SAVE name | 0 / 0 | per-target SecFlags CMDROOMS, CMDRACES, CMDQUESTS, CMDFACTIONS, CMDPLAYERS (com/planet_ink/coffee_mud/Commands/Save.java:158-359); `securityCheck` at com/planet_ink/coffee_mud/Commands/Save.java:419-426 | Staff |
| SHEATH | com/planet_ink/coffee_mud/Commands/Sheath.java | SHEATH [item] [IN container] [QUIETLY] [IFPOSSIBLE]; with no argument sheaths every wielded or held weapon it can | 0.10 / 0.50 | needs a worn container with capacity; weapon must be wielded or held | Player |
| REPORT | com/planet_ink/coffee_mud/Commands/Report.java | REPORT (says HP, mana, move and XP to level); REPORT STATS, AFFECTS, EXPERTISES, ALL; or a skill category or domain | 0.20 / 1.00 | none | Player |
| REQUEST | com/planet_ink/coffee_mud/Commands/Request.java | REQUEST count item-mask price [FROM] broker; REQUEST CANCEL | 0.20 / 1.00 | needs a CraftBroker in the room, otherwise the words are said aloud (com/planet_ink/coffee_mud/Commands/Request.java:61-69); not orderable | Player |
| RESTRING | com/planet_ink/coffee_mud/Commands/Restring.java | RESTRING item [@ mob / container / room]; opens the generic editor on a generic item | 0 / 0 | SECURITY_CMD_GROUP or SecFlag.RESTRING (com/planet_ink/coffee_mud/Commands/Restring.java:151-155) | Staff |
| RETIRE | com/planet_ink/coffee_mud/Commands/Retire.java | RETIRE (must be typed in full): password, optional reason, then permanent deletion | 0.20 / 1.00 | password; 120 s prompt (com/planet_ink/coffee_mud/Commands/Retire.java:72); not orderable | Player |
| SELL | com/planet_ink/coffee_mud/Commands/Sell.java | SELL item / count / ALL TO shopkeeper | 0.20 / 1.00 | worn items are skipped (FILTER_UNWORNONLY, com/planet_ink/coffee_mud/Commands/Sell.java:90); coins excluded (com/planet_ink/coffee_mud/Commands/Sell.java:105); mobs only at child-dealing shops (com/planet_ink/coffee_mud/Commands/Sell.java:81-100); not orderable | Player |
| REPLY, REP, RE | com/planet_ink/coffee_mud/Commands/Reply.java | REPLY message; replies to the last tell, say or yell | 0.20 / 1.00 | QUIET must be off (com/planet_ink/coffee_mud/Commands/Reply.java:55-59); target must still be logged in (com/planet_ink/coffee_mud/Commands/Reply.java:69-76) | Player |
| REMOVE, REM | com/planet_ink/coffee_mud/Commands/Remove.java | REMOVE item; takes off worn items | 0.20 / 1.00 | must be worn (FILTER_WORNONLY, com/planet_ink/coffee_mud/Commands/Remove.java:63) | Player |
| SERVE | com/planet_ink/coffee_mud/Commands/Serve.java | SERVE player or deity; swears fealty | 0.20 / 1.00 | target must be visible; a player with NOFOLLOW refuses (com/planet_ink/coffee_mud/Commands/Serve.java:63-69); a non-deity must be at least your level (com/planet_ink/coffee_mud/Commands/Serve.java:87-92); not orderable | Player |
| SET | com/planet_ink/coffee_mud/Commands/SetCmd.java | SET word ...; re-enqueues as command `SET<WORD>` if one exists | 0 / 0 | not listed in the command list (com/planet_ink/coffee_mud/Commands/SetCmd.java:91-94) | Player |
| RIGHT | com/planet_ink/coffee_mud/Commands/Right.java | RIGHT [NORTH / SOUTH]; east, or northeast or southeast with the argument | Go rule: 0.20 / 1.00, quartered with AUTORUN | players only where the direction type is CARAVAN (com/planet_ink/coffee_mud/Commands/Right.java:78-85) | Player |
| RUN | com/planet_ink/coffee_mud/Commands/Run.java | RUN direction; sets AUTORUN for the move and restores it (com/planet_ink/coffee_mud/Commands/Run.java:72-77) | 0.05 / 0.25 | none | Player |
| REPLAY | com/planet_ink/coffee_mud/Commands/Replay.java | REPLAY [n]; prints the last n lines of the session buffer | 0 / 0 | not for monsters (com/planet_ink/coffee_mud/Commands/Replay.java:52) | Player |
| RULES | com/planet_ink/coffee_mud/Commands/Rules.java | RULES; prints the rules file through the web-macro filter, or a fallback line | 0 / 0 | none | Player |

The Shell subcommands, each with a one-character form, are all in `com/planet_ink/coffee_mud/Commands/Shell.java:54-77`:

| Words | Short | Effect | Code |
|---|---|---|---|
| DIRECTORY, LS | `$` | list a directory; -R recurses, -L long form | com/planet_ink/coffee_mud/Commands/Shell.java:371-477 |
| COPY, CP | `>` | copy files; -R, -F overwrite, -P keep paths | com/planet_ink/coffee_mud/Commands/Shell.java:478-614 |
| CHANGEDIRECTORY, CD, GO | `.` | change the working directory, kept per mob | com/planet_ink/coffee_mud/Commands/Shell.java:615-639 |
| DELETE, RM, RD | `-` | delete files; -R recurses | com/planet_ink/coffee_mud/Commands/Shell.java:640-684 |
| TYPE, CAT, TP | backslash | print a file | com/planet_ink/coffee_mud/Commands/Shell.java:728-768 |
| MAKEDIRECTORY, MKDIR, MD | `+` | make a directory | com/planet_ink/coffee_mud/Commands/Shell.java:819-844 |
| FINDFILE, FF | `*` | list files matching a mask | com/planet_ink/coffee_mud/Commands/Shell.java:845-884 |
| SEARCHTEXT, GREP, ST | `&` | search text of files, skipping archive and image extensions | com/planet_ink/coffee_mud/Commands/Shell.java:885-949 |
| EXEC, JRUN | `:` | run a JRUN script on a file | com/planet_ink/coffee_mud/Commands/Shell.java:769-818 |
| EDIT | `/` | edit a file in the journal editor | com/planet_ink/coffee_mud/Commands/Shell.java:950-992 |
| COMMAND, CMD | `_` | run game commands as the mob, with aliases | com/planet_ink/coffee_mud/Commands/Shell.java:685-727 |
| MOVE, MV | `~` | move or rename; -R -F -P | com/planet_ink/coffee_mud/Commands/Shell.java:993-1152 |
| COMPAREFILES, DIFF, CF | `=` | colour diff of two files | com/planet_ink/coffee_mud/Commands/Shell.java:1153-1232 |

Reset's subcommands, grouped by what they touch:

- **Rooms and players (no extra gate beyond the command):** ROOM (com/planet_ink/coffee_mud/Commands/Reset.java:864-889), AREA (1000-1021), REJUV ROOM / AREA / WORLD with MOB or ITEM (553-593), RELEVEL area X-Y, refused in instance child areas (595-666), TICKCOUNTERS (668-672), PASSWORD (891-977, needs CMDPLAYERS everywhere), INIFILE (979-998, reloads the config).
- **Gated by RESETUTILS:** ROOMIDS and AREAROOMIDS renumber rooms (1038-1142), VISITATION (2230-2261), CLANTICK (2221-2228), PROPERTYGARBAGE (1150-1174), SORTHELP (673-862), VISITATION, and the world fixers below.
- **World fixers (sysop):** MOBCOMBATSTATS (1386-1514), MOBWEAPONDAMAGE (1516-1644), ITEMSTATS (2049-2219), ALLMOBARMORFIX (1686-1734), GOLDCEILINGFIXER (1736-1780), GROUNDLYDOORS (1646-1684), WORLDMATCONFIRM (1854-1924), AREAINSTALL (1782-1852), AREARACEMAT (2263-2476, interactive), BANKDATA (1357-1384).
- **Race data (CMDRACES):** RACESTATGAINS (1176-1208), GENRACEAGINGCHARTS (1210-1262), GENMIXEDRACEBUILDS (1264-1355), MIXEDRACES report (1926-2047, sysop).
- **Technology:** MANUFACTURERS (2478-2658).

### REMORT: a prestige reset with a declarative retention spec

**Data model.** The kept attributes are a list of keywords from the `RemortRetain` enum: HITPOINT, MANA, MOVE, LEVEL, ATTACK, DEFENSE, DAMAGE, SKILL, SKILLSAT100, FACTION, EXPERTISE, BONUSSTATPOINT, QUESTPOINT and PERMAFFECTS (`com/planet_ink/coffee_mud/Commands/Remort.java:57-73`). Six more tokens are plain flags: RACE, GENDER, CHARCLASS, VISITATION, WARRANTS and STATS are removed from the list and checked by exact match (com/planet_ink/coffee_mud/Commands/Remort.java:142-159). The spec is the `REMORTRETAIN` config string, split on commas (com/planet_ink/coffee_mud/Commands/Remort.java:141).

**Spec grammar.** A token is a keyword with an optional amount. `CMath.isPct` gives a fraction, a bare integer is a flat count, and a decimal between 0 and 1 is a fraction; any other number is divided by 100 (com/planet_ink/coffee_mud/Commands/Remort.java:197-216). A keyword with no amount means 100% (`pctAmount` starts at 1.0, com/planet_ink/coffee_mud/Commands/Remort.java:198). Bare integers are flat, so `50` means 50 and not 50%, while `50%` and `0.5` both mean half.

**What each keyword keeps** (all in the `switch` at com/planet_ink/coffee_mud/Commands/Remort.java:217-404; `flat` is the flat amount, `pct` the fraction):

| Keyword | Kept value (code) | Lines |
|---|---|---|
| HITPOINT | pct × base hit points − STARTHP, plus flat | 253-261 |
| MANA | pct × base mana − STARTMANA, plus flat | 272-280 |
| MOVE | pct × base movement − STARTMOVE, plus flat | 290-298 |
| LEVEL | round(pct × level) − 1, plus flat; applied only if above 1 | 262-271 |
| ATTACK | pct × attackAdjustment, plus flat | 219-227 |
| DEFENSE | 100, minus pct × (100 − armor), minus flat | 237-252 |
| DAMAGE | pct × damage, plus flat | 281-289 |
| QUESTPOINT | pct × quest points, plus flat | 228-236 |
| SKILL | a random subset of learned skills, re-added at proficiency 0 | 299-313, 471-480 |
| SKILLSAT100 | a random subset of skills at 100, re-added at 100 (see Problems) | 314-329, 481-490 |
| FACTION | a random subset of factions with their values | 330-344, 491-494 |
| EXPERTISE | a random subset of expertises | 345-359, 495-498 |
| BONUSSTATPOINT | pct × max(0, sum of base stats − BASEMINSTAT × stat count − account bonus-point total); when attributes are disabled and STARTSTAT is 0, the minimum is 10 per stat and the account total is not subtracted | 360-382 |
| PERMAFFECTS | a random subset of effects that cannot be removed | 383-403, 401 |

A random subset is made by removing a random element until the list is the right length (`CMLib.dice().roll(1, size, -1)`, com/planet_ink/coffee_mud/Commands/Remort.java:311, 327, 342, 357, 400). `Dice.roll(1, n, -1)` returns 0 to n−1 (`com/planet_ink/coffee_mud/Libraries/Dice.java:398-406`), so the choice is uniform.

**Control flow.**

1. The mask is checked and the retention spec is parsed (com/planet_ink/coffee_mud/Commands/Remort.java:121-216). The prompt is "This will drop your level back to N!" with N the computed level (com/planet_ink/coffee_mud/Commands/Remort.java:408).
2. A password is asked for within 120 s (com/planet_ink/coffee_mud/Commands/Remort.java:409-416). The callback saves the player, sends followers away with the NOFOLLOW command, and bumps the REMORT achievement (com/planet_ink/coffee_mud/Commands/Remort.java:429-445).
3. The character is reset in memory: level 1, armour, damage and attack from the computed values, a new age, saving throws and max codes zeroed, abilities, behaviours, scripts and expertises deleted, and XP set to 0 (com/planet_ink/coffee_mud/Commands/Remort.java:446-470). Retained skills, factions and expertises are re-added (471-498). Tattoos are kept or re-awarded according to whether their achievement has the REMORT flag (499-519). The start room is recomputed (520-521).
4. A failsafe ExtAbility named `RemoteFailSafe` is added with a listener (com/planet_ink/coffee_mud/Commands/Remort.java:522-764). The listener runs the rebuild when a BRINGTOLIFE control message arrives, either at once or 500 ms later (com/planet_ink/coffee_mud/Commands/Remort.java:744-747). The message is sent immediately after the listener is set up (765).
5. The rebuild (`remortRun`, com/planet_ink/coffee_mud/Commands/Remort.java:546-742) sets the class to StdCharClass at level 1 (560-561), strips personal effects not retained (562-577), then runs the normal creation prompts for each thing not retained: race (591-606), gender (607-622), base stats via `promptBaseCharStats` with the bonus points (626-642), class (655-670). Warrants and visitations are cleared if they are not retained (675-688). The class grants its start abilities (690-691), Allergies is added unless disabled (692-698), and achievements are reloaded (625, 645-646, 701-703).
6. The level is restored by granting exactly the XP needed for each level up to `newLevel − 1`, or by calling `level(mob)` when experience is deferred or capped (com/planet_ink/coffee_mud/Commands/Remort.java:705-720). The message is "You have remorted back to level N!" (com/planet_ink/coffee_mud/Commands/Remort.java:724).

**Abort path.** If the session drops during any of the creation prompts (`sess.isStopped()`, com/planet_ink/coffee_mud/Commands/Remort.java:601, 617, 637, 665), `slowStop` runs (com/planet_ink/coffee_mud/Commands/Remort.java:83-108). It copies the saved account state back over the live account, waits 3 s, and calls `CMLib.players().delPlayer(mob)` (com/planet_ink/coffee_mud/Commands/Remort.java:106), then throws to end the rebuild. The character is therefore deleted, not left half-built. Whether that is the intent is unverified, but the code path is clear.

**Shipped configuration.** The code default is `REMORTRETAIN=SKILLSAT100` and `REMORTMASK=-LEVEL +>60` (`com/planet_ink/coffee_mud/core/CMProps.java:2910-2911`). The shipped `coffeemud.ini` uses `REMORTRETAIN=100% FACTIONS, 100% QUESTPOINT, RACE, GENDER` and `REMORTMASK=-LEVEL +>90` (`coffeemud.ini:696`, `coffeemud.ini:719`). So the shipped remort keeps factions, quest points, race and gender at 100% and resets everything else, including skills, class and base stats.

### SAY: target resolution, language swap and markup

Say's `exec` (com/planet_ink/coffee_mud/Commands/Say.java:117-399) is shared by the speech verbs and takes a `multiRoom` flag for yelling. The steps for a plain SAY are:

1. **Target candidate.** With more than two words, the second word is a candidate (com/planet_ink/coffee_mud/Commands/Say.java:145-148). A stop-word list rejects candidates that are a prefix of a word such as THE, A, IS, HAS or BEEN (com/planet_ink/coffee_mud/Commands/Say.java:53-68, 158-166). A held wand whose magic word matches is not a target (com/planet_ink/coffee_mud/Commands/Say.java:150-155).
2. **Special targets.** `self` targets the speaker (com/planet_ink/coffee_mud/Commands/Say.java:168-172). `noone` and `nobody` are allowed for SAYTO (com/planet_ink/coffee_mud/Commands/Say.java:174-178).
3. **Lookup.** `R.fetchFromRoomFavorMOBs` (com/planet_ink/coffee_mud/Commands/Say.java:182) finds the MOB. A numeric suffix such as `name.2` picks the second match (com/planet_ink/coffee_mud/Commands/Say.java:195-209). A name shorter than four characters must match exactly; longer names match as substrings (com/planet_ink/coffee_mud/Commands/Say.java:210-217).
4. **Auto-language.** If the room holds exactly two inhabitants and no target was given, the other one is the target (com/planet_ink/coffee_mud/Commands/Say.java:273-288), unless AUTOLANGUAGE is disabled (com/planet_ink/coffee_mud/Commands/Say.java:268). If the target speaks a language the speaker also has, the two languages are swapped for the message with `setBeingSpoken` (com/planet_ink/coffee_mud/Commands/Say.java:292-315, 346-352, 390-396).
5. **Filters.** The SAYFILTER config filters the words (com/planet_ink/coffee_mud/Commands/Say.java:328), and player input passes the secondary user filter (com/planet_ink/coffee_mud/Commands/Say.java:329-332).
6. **Message.** Targeted speech is a two-sided `MSG_SPEAK` message whose text is wrapped in a `<SAY "name">` markup tag (com/planet_ink/coffee_mud/Commands/Say.java:334-342). `gmcpSaySend` also sends a GMCP `comm.channel` JSON packet to clients that negotiated GMCP (com/planet_ink/coffee_mud/Commands/Say.java:70-115).
7. **Hearing in other rooms.** For yell-type calls, each adjacent room with an open exit gets "You hear someone ... from the <direction>" (com/planet_ink/coffee_mud/Commands/Say.java:359-386).

### SCORE and REPORT: stat words and prowess flags

- Score's sections are BASE, INFO, STATS, DETAILS and AFFECTS or EFFECTS (`com/planet_ink/coffee_mud/Commands/Score.java:45-53`). Prefix matching picks the section (com/planet_ink/coffee_mud/Commands/Score.java:139).
- `CMProps.Int.COMBATPROWESS` is a bit mask that chooses how values show. Bit STAT_PROFICIENCY turns stats into words, POWER_LEVEL shows the fudged power level, FACTION_RANGE shows faction words, and the armour, combat and damage prowess bits add a line each (com/planet_ink/coffee_mud/Commands/Score.java:107, 144, 336, 355-360).
- Stat words come from the CHARSTAT_CHART list, 34 entries from "disabled" to "ungodly" (`resources/lists.ini:536-550`). A value past the chart is turned into Roman numerals and each digit becomes one adverb from EXTREME_ADVS (`resources/lists.ini:91-99`). The digits are I, V, X, L, C, D and M, mapped to the seven adverbs in order (com/planet_ink/coffee_mud/Commands/Score.java:66-97, 118-121). So a value of 34 reads "Very ungodly" (derived from the code, not run), and 35 reads "Very Very ungodly".
- The level line shows class level against character level when the class lags (com/planet_ink/coffee_mud/Commands/Score.java:154-157) and lists other classes (208-229). The liege is read from the MOB (com/planet_ink/coffee_mud/Commands/Score.java:244-249), which SERVE sets (see below).
- The death-lives line appears only if PLAYERDEATH contains a PUR entry (com/planet_ink/coffee_mud/Commands/Score.java:186-204). The shipped value is `(@x1 * 50) < 2000` (`coffeemud.ini:374`), which has none, so the line does not show by default.
- REPORT (`com/planet_ink/coffee_mud/Commands/Report.java:49-242`) builds a string and says it with `postSay` (com/planet_ink/coffee_mud/Commands/Report.java:239), so the room hears the status. The category names are the ability access codes and domain names, with aliases POWER, POWERS and LANGS (com/planet_ink/coffee_mud/Commands/Report.java:141-145).

### SHEATH: capacity by weight

- A container can hold a sheathed weapon if it is a non-drink container with capacity, and its contain types are not CONTAIN_ANYTHING (com/planet_ink/coffee_mud/Commands/Sheath.java:103-108). Capacity is weight: `canSheathIn` checks `capRem[0] >= weight` and then subtracts it (com/planet_ink/coffee_mud/Commands/Sheath.java:48-61).
- Items already inside a worn sheath reduce its remaining capacity (com/planet_ink/coffee_mud/Commands/Sheath.java:112-121).
- The move is a `MSG_PUT` into the container, with "sheath(s) <O-NAME> in <T-NAME>" as the text or no text when quiet (com/planet_ink/coffee_mud/Commands/Sheath.java:246-251). QUIETLY and IFPOSSIBLE are stripped from the end of the words (com/planet_ink/coffee_mud/Commands/Sheath.java:70-80).

### SAVE: the persistence model

- Saving a room first counts what the database holds for the room against what is in memory (`DBCountRoomMobsItems`, com/planet_ink/coffee_mud/Commands/Save.java:88-94). It asks for confirmation with the difference, such as "add 2 mob(s)" or "delete 1 item(s)" (com/planet_ink/coffee_mud/Commands/Save.java:96-118).
- Items are written with `DBUpdateItems`, then an item rejuvenation tick is scheduled (com/planet_ink/coffee_mud/Commands/Save.java:122-127). Mobs are written with `DBUpdateMOBs` (com/planet_ink/coffee_mud/Commands/Save.java:128-129).
- Area saves run the same routine for every room without the per-room prompt, after a single confirmation (com/planet_ink/coffee_mud/Commands/Save.java:223-233, 292-306, 323-335).
- The room's help cache is cleared with `Resources.removeResource("HELP_" + area)` (com/planet_ink/coffee_mud/Commands/Save.java:282, 313, 343).
- SAVE GRACES counts race use across all player records and followers, then calls `updateAllRaceDates` (com/planet_ink/coffee_mud/Commands/Save.java:192-214).

### SERVE and liege loyalty

SERVE sends a `MSG_SERVE` to the recipient (com/planet_ink/coffee_mud/Commands/Serve.java:93). The `executeMsg` method of `StdMOB` handles `TYP_SERVE` by setting the liege to the target's name unless the target is a Deity (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:3602-3606`). It handles `TYP_REBUKE` by clearing the liege unless the MOB is married to it (com/planet_ink/coffee_mud/MOBS/StdMOB.java:3592-3600). SCORE then shows "who serves" or "who is married to" (com/planet_ink/coffee_mud/Commands/Score.java:244-249). Deity conversion sends the same message type from `com/planet_ink/coffee_mud/Abilities/Skills/Skill_Convert.java:357` and from character creation at `com/planet_ink/coffee_mud/Libraries/CharCreation.java:4097`.

### REQUEST: broker orders

REQUEST finds shopkeepers in the room and keeps only CraftBroker ones (com/planet_ink/coffee_mud/Commands/Request.java:54-60). The money string is parsed in the shop's currency (com/planet_ink/coffee_mud/Commands/Request.java:106-108) and the item mask with `parseSpecialItemMask` (com/planet_ink/coffee_mud/Commands/Request.java:121). It then posts a `MSG_BROKERADD` with the text "list(s) a new item request" (com/planet_ink/coffee_mud/Commands/Request.java:130-135). CANCEL uses the same message type with a cancel text (com/planet_ink/coffee_mud/Commands/Request.java:83-94).

### RETIRE, RESTRING, SELL, REPLY and the small verbs

- **RETIRE** asks for the password, then an optional reason unless `DisFlag.RETIREREASON` is disabled (com/planet_ink/coffee_mud/Commands/Retire.java:94-123). It obliterates the player, logs the character out and bumps the RETIRE achievement (com/planet_ink/coffee_mud/Commands/Retire.java:97-99, 118-121).
- **RESTRING** accepts only generic items (`isGeneric`, com/planet_ink/coffee_mud/Commands/Restring.java:104-106). It searches the room, a mob, or a container named after `@`, opens the generic name, display text and description editors (com/planet_ink/coffee_mud/Commands/Restring.java:115-117), and recomputes the stats (com/planet_ink/coffee_mud/Commands/Restring.java:136).
- **SELL** lets a shop that deals in children (`DEAL_CHILDREN`) take group-member mobs, and never players (com/planet_ink/coffee_mud/Commands/Sell.java:81-100). Coins are never sold (com/planet_ink/coffee_mud/Commands/Sell.java:105).
- **REPLY** keeps a reply type per player: SAY, YELL or TELL (`com/planet_ink/coffee_mud/Common/interfaces/PlayerStats.java:1296-1300`). A TELL reply raises the snoop suspension by 1 and then lowers it by 11 (com/planet_ink/coffee_mud/Commands/Reply.java:103-107).
- **RIGHT** is a fixed-direction walk: east by default, and northeast or southeast when north or south is given (com/planet_ink/coffee_mud/Commands/Right.java:52-61). **RUN** sets AUTORUN for one move (com/planet_ink/coffee_mud/Commands/Run.java:72-77), which also quarters the walk cost in `Go` (`com/planet_ink/coffee_mud/Commands/Go.java:261-268`).
- **REPLAY** prints from the session's last-message buffer, which holds 100 lines (`com/planet_ink/coffee_mud/Common/interfaces/Session.java:1493`).
- **SET** is a dispatcher with no cost. It enqueues `SET` plus the next word if a command of that name exists (com/planet_ink/coffee_mud/Commands/SetCmd.java:54-67). No `SET*` command is registered in this package; the target commands live elsewhere (unverified).

### SHELL: a file manager over the virtual file system

- **Path prefixes.** `//` demands a local file and `::` demands a virtual-file-system file (`com/planet_ink/coffee_mud/core/CMFile.java:129-130`). Without a prefix, the file is looked up in the VFS and then on disk.
- **Permissions per file.** `CMFile(path, accessor)` sets read and write bits from `CMSecurity.canAccessFile` for the VFS and local copies, and sets the directory bit only if the user may traverse it (`com/planet_ink/coffee_mud/core/CMFile.java:213-237`). The command checks the bits before every step (for example com/planet_ink/coffee_mud/Commands/Shell.java:537-541, 588-592).
- **Redirection.** `>` and `>>` swap the mob's session for a FakeSession that writes to the target file, feed it 19 blank lines of input so prompts do not block, and run the command (com/planet_ink/coffee_mud/Commands/Shell.java:263-297). Output is captured by changing the session, not by changing the command.
- **Command runner.** COMMAND expands the alias for the first word, queues the commands with `enqueCommand`, and sleeps in one-second steps while the queue drains, up to 99 times (com/planet_ink/coffee_mud/Commands/Shell.java:685-727).
- **Editor.** EDIT loads the file into the journal message editor. On save it writes the file and removes the resource cache entries with the file's name (com/planet_ink/coffee_mud/Commands/Shell.java:964-990).
- **Internal use.** `executeInternal` runs SHELL with a fake session and returns the output text (com/planet_ink/coffee_mud/Commands/Shell.java:238-261). A `.` argument returns the working directory (com/planet_ink/coffee_mud/Commands/Shell.java:241-243).

## Content worth mining

- **Remort retention specs.** The shipped string (`coffeemud.ini:719`) is a compact example of a declarative rebirth rule: "100% FACTIONS, 100% QUESTPOINT, RACE, GENDER". The keyword table above is a ready-made list of what a Python rebirth could keep.
- **Stat words.** The CHARSTAT_CHART list (`resources/lists.ini:536-550`) gives 34 steps of adjectives for an attribute, from "disabled" through "feeble", "average", "excellent" and "fantastical" to "ungodly". EXTREME_ADVS (`resources/lists.ini:91-99`) is Very, Extremely, Incredibly, Astoundingly, Stupendously, Drastically and Amazingly.
- **Rules text.** `resources/text/rules.txt` has four numbered rules in its header (counted by pattern). The first is no harassing new players, and the second is to role-play to keep the fantasy illusion. The fallback line when the file is missing is "This mud has no rules. Welcome to chaos." (com/planet_ink/coffee_mud/Commands/Rules.java:52-63).
- **Manufacturers.** The MANUFACTURERS subcommand (com/planet_ink/coffee_mud/Commands/Reset.java:2478-2658) defines fictional technology firms grouped by ship and personal equipment type, for example SHIP_SOFTWARE with firms such as Nanoapp and Cryptosoft, SHIP_ENGINE with six engine makers, and PERSONAL_SHIELD with six shield makers. Each firm gets a random efficiency and reliability around 0.75 to 1.25 and a tech-level band (com/planet_ink/coffee_mud/Commands/Reset.java:290-329). See Problems for the two lists that never get created.
- **Race fixers.** RACESTATGAINS caps each generic race stat gain at +5 (com/planet_ink/coffee_mud/Commands/Reset.java:1190-1197). GENRACEAGINGCHARTS averages the aging charts of a mixed race's parent races (com/planet_ink/coffee_mud/Commands/Reset.java:1229-1244).
- **Speech stop-words.** The list of words that cannot be a target (com/planet_ink/coffee_mud/Commands/Say.java:53-68) is a usable filter for any parser that takes a name from a sentence.
- **Searchable file types.** SEARCHTEXT skips archive and image extensions: ZIP, JPE, JPG, GIF, CLASS, WAV, BMP, JPEG, GZ, TGZ and JAR (com/planet_ink/coffee_mud/Commands/Shell.java:79-81).

## Notable design ideas

- **REMORT as data.** The whole rebirth rule is one string, parsed into (keyword, fraction, flat) triples. A game can offer different rebirths by changing a string, not code.
- **Two-phase rebirth with a failsafe.** The character is reset in memory, a failsafe effect holds a listener, and the real rebuild runs when a control message arrives. If the process is interrupted, the effect still exists to finish or undo the work (com/planet_ink/coffee_mud/Commands/Remort.java:522-765).
- **Creation reuse.** Remort calls the same race, gender, stat and class prompts as new-character creation, with the theme chosen from the account (com/planet_ink/coffee_mud/Commands/Remort.java:534-544, 595, 611, 631, 659).
- **Per-command costs in config.** Each command's action cost is a config value with a class-ID key and a combat variant (com/planet_ink/coffee_mud/core/CMProps.java:1143-1196). Scaling a cost (RUN at 1/4 of the default, SHEATH at 1/2) is one line in the command.
- **Speech with resolved targets.** SAY resolves "name.2" and short names, uses a stop-word list, and swaps languages with a listener who knows one the speaker does not (com/planet_ink/coffee_mud/Commands/Say.java:141-315).
- **Output capture by session swap.** Redirection and internal calls make a command write to a file or a string by swapping the session (com/planet_ink/coffee_mud/Commands/Shell.java:263-297, 238-261). The command itself does not change.
- **Roman-numeral extremes.** A value past the top of a word table is written as I, V, X and so on, and each digit becomes an adverb, so "very very ungodly" needs no extra list (com/planet_ink/coffee_mud/Commands/Score.java:66-97).
- **Mask bits for display.** One integer chooses numbers, words and power level (com/planet_ink/coffee_mud/Commands/Score.java:107, 144, 336, 355-360).

## Problems and cruft

**Bugs found by reading (not run):**

1. **SKILLSAT100 uses the wrong list size.** com/planet_ink/coffee_mud/Commands/Remort.java:324 computes the kept count from `abilities.size()`, the SKILL list, not `abilities100.size()`. The SKILL list is filled only when SKILL is retained (com/planet_ink/coffee_mud/Commands/Remort.java:304), so with the code default `SKILLSAT100` (`com/planet_ink/coffee_mud/core/CMProps.java:2911`), the kept count is 0 and every skill at 100 is removed (com/planet_ink/coffee_mud/Commands/Remort.java:326-327). The shipped config does not use this keyword, so the bug is latent.
2. **Remort deletes a half-built character.** A dropped session during the creation prompts ends in `delPlayer` (com/planet_ink/coffee_mud/Commands/Remort.java:106, reached from 603, 619, 639, 667). The copy-back and the delete sit side by side and look deliberate, but nothing in the file says why, and a player can lose a character by losing a connection.
3. **Manufacturers: the last set is never created.** The final block defines six personal firms (com/planet_ink/coffee_mud/Commands/Reset.java:2648-2656) and closes without calling `makeManufacturer`. The GRAVGEN set (com/planet_ink/coffee_mud/Commands/Reset.java:2548-2557) reuses the six names from the DAMPENER set (com/planet_ink/coffee_mud/Commands/Reset.java:2538-2547). `makeManufacturer` skips any name that already exists and logs "Dup Reset Manufacturer Name" (com/planet_ink/coffee_mud/Commands/Reset.java:298-303), using `getManufacturer` keyed by upper-cased name (`com/planet_ink/coffee_mud/Libraries/GroundWired.java:997-1004`). So the GRAVGEN set is never created either.
4. **Password reset defaults to a weak password.** When the account has no valid email, the confirmation defaults to Y and sets the password to the lower-cased account name (com/planet_ink/coffee_mud/Commands/Reset.java:960-963).
5. **Shell parses a leading flag as DELETE.** Any word starting with `-` is taken as the DELETE short form (com/planet_ink/coffee_mud/Commands/Shell.java:300-332). For "SHELL -R path", the flag becomes the command and the path is overwritten with "R" (com/planet_ink/coffee_mud/Commands/Shell.java:330-332), so the file list is built from the working directory. Not run, so the scope of the deletion is unverified, but the input is risky.
6. **COPY and MOVE disagree on what a target directory is.** COPY checks `exists() && isDirectory()` for all four candidates (com/planet_ink/coffee_mud/Commands/Shell.java:514-524). MOVE uses `isLocalDirectory()` and `isVFSDirectory()` for two of them (com/planet_ink/coffee_mud/Commands/Shell.java:1035-1039). A target that COPY accepts can be rejected by MOVE, and the other way round.
7. **Shell's working-directory map leaks.** `pwds` is a static list of MOB references (com/planet_ink/coffee_mud/Commands/Shell.java:52). Entries are added on CD and removed only on the next CD (com/planet_ink/coffee_mud/Commands/Shell.java:636-637), and nothing removes them on logout (no other reference in the source).
8. **Generic age chart fix looks wrong.** When a value drops below the previous one, GENRACEAGINGCHARTS adds the previous value to it (com/planet_ink/coffee_mud/Commands/Reset.java:1251-1252), not sets it to the previous value. The intent is unverified.
9. **Shell's COMMAND stalls its thread.** It sleeps in one-second steps for up to about 100 s (com/planet_ink/coffee_mud/Commands/Shell.java:708-724).
10. **Retire logs the reason against the name.** The prompt tells the player the answer is for administrative purposes and kept confidential (com/planet_ink/coffee_mud/Commands/Retire.java:107), but the callback writes the reason with the character name to the system log (com/planet_ink/coffee_mud/Commands/Retire.java:118).
11. **Reply's snoop adjustment is unexplained.** It adds 1 and then subtracts 11 (com/planet_ink/coffee_mud/Commands/Reply.java:103-107).
12. **Reset's propertygarbage passes the admin's name.** It sets the owner to the admin and then clears it (com/planet_ink/coffee_mud/Commands/Reset.java:1167-1168), then passes the admin's name as the player list to `updateLot` (com/planet_ink/coffee_mud/Commands/Reset.java:1169). The intent is unclear, and the prompt says "default room descriptions" while the code does not change descriptions directly.
13. **Save's keyword is literally GRACES.** It is not RACES (com/planet_ink/coffee_mud/Commands/Save.java:192), and the error message at com/planet_ink/coffee_mud/Commands/Save.java:406-407 does not list it.

**Cruft and copy-paste:**

- The ROOM, AREA and WORLD scan scaffolding is repeated in about four blocks: MOBCOMBATSTATS, MOBWEAPONDAMAGE, ITEMSTATS and AREARACEMAT. The `makeManufacturer` check `names.length%3!=0` is meaningless because the loop is fixed at six (com/planet_ink/coffee_mud/Commands/Reset.java:292-296), and its message says "Test:" (com/planet_ink/coffee_mud/Commands/Reset.java:293).
- ALLMOBARMORFIX logs every mob it skips (com/planet_ink/coffee_mud/Commands/Reset.java:1721-1722), so a large area floods the log.
- Several handlers assume a session exists (for example com/planet_ink/coffee_mud/Commands/Reset.java:1178 `mob.session().confirm` and com/planet_ink/coffee_mud/Commands/Reset.java:1152), though others check it first.
- Remort ignores unknown tokens in REMORTRETAIN without a warning (com/planet_ink/coffee_mud/Commands/Remort.java:161-194).

**Multiplayer assumptions:** REPLY and its reply types, REQUEST and the broker, SAVE USERS and the account copies, RETIRE and obliteration, the account-level achievement and tattoo handling in REMORT, and SERVE's loyalty to another player.

## Takeaways for a Python SUD

- **Command table.** Model each command as a small object with access words, `actions_cost(state)`, `combat_cost(state)`, `can_run(player)`, and `run(player, args)`. Keep the costs in a config table keyed by command name, with combat and out-of-combat values, as CMProps does (com/planet_ink/coffee_mud/core/CMProps.java:1143-1196). In a turn-based SUD, the cost becomes the turns a command takes; in a real-time one, it becomes a cooldown.
- **Rebirth as data.** Borrow the REMORT spec in a form Python can read directly:

```python
@dataclass(frozen=True)
class Keep:
    kind: str          # "HITPOINT", "SKILL", "FACTION", ...
    fraction: float = 0.0
    flat: int = 0

KEEP = [Keep("FACTION", 1.0), Keep("RACE"), Keep("SKILL", 0.5)]
```

  Parse the string "100% FACTIONS, RACE" once, and apply the list when the player starts a new life. Keep the rule that bare integers are flat and percentages are fractions (com/planet_ink/coffee_mud/Commands/Remort.java:197-216), and be explicit about it.
- **What to drop for one player.** REPLY, the tell and yell paths, REQUEST, SAVE USERS, RETIRE with obliteration, SERVE to another player, and the account and tattoo layers. Keep SAY's target resolution and language swap for NPCs, and keep REMORT's idea of a new life that keeps some things.
- **Target resolution.** Port the stop-word list and the "name.2" rule to a small function `resolve_target(words, candidates)`. Use the 4-character rule for substring matches, because short names otherwise match too much (com/planet_ink/coffee_mud/Commands/Say.java:210-217).
- **Languages.** A reasonable single-player version is: when the player speaks to an NPC, the NPC answers in a language the player knows, otherwise the reply is garbled. Model the language as a set on the character, not an ability.
- **Stat words.** Keep a word table per attribute (CHARSTAT_CHART) and a bisect into it. For values past the table, reuse the Roman-numeral trick or pick a simpler rule: one adverb per ten points above the top word.
- **Container capacity.** Compute free capacity from contents by weight, as Sheath does (com/planet_ink/coffee_mud/Commands/Sheath.java:48-61, 112-121), and do not store it.
- **Saves.** Keep a dirty flag per room and per character. Before a manual save, show the difference that would be written, as Save does (com/planet_ink/coffee_mud/Commands/Save.java:88-118).
- **Do not port.** SHELL as a general file manager. A single-player game needs at most a debug console with load, save, and a few inspect commands. Also do not port RESET's world fixers; instead, write a migration script when the data format changes.
- **Simplify.** Remort's pass through a failsafe effect and a control message is a workaround for CoffeeMud's threading and session model. A Python SUD can run the rebirth as one function that prompts in order and writes the new character only at the end, which also removes the bug in Problem 2.

## Coverage

- **Read in full:** com/planet_ink/coffee_mud/Commands/Reset.java (all 2669 lines, in nine passes), com/planet_ink/coffee_mud/Commands/Shell.java (all 1256), com/planet_ink/coffee_mud/Commands/Remort.java (all 789), com/planet_ink/coffee_mud/Commands/Say.java (all 434), com/planet_ink/coffee_mud/Commands/Save.java (all 428), com/planet_ink/coffee_mud/Commands/Sheath.java (all 274), com/planet_ink/coffee_mud/Commands/Report.java (lines 36-262, the whole class body), com/planet_ink/coffee_mud/Commands/Request.java (lines 41-158), com/planet_ink/coffee_mud/Commands/Restring.java (lines 41-156), com/planet_ink/coffee_mud/Commands/Retire.java (lines 44-148), com/planet_ink/coffee_mud/Commands/Reply.java (lines 41-145), com/planet_ink/coffee_mud/Commands/Serve.java (lines 41-115), com/planet_ink/coffee_mud/Commands/SetCmd.java, com/planet_ink/coffee_mud/Commands/Right.java, com/planet_ink/coffee_mud/Commands/Run.java, com/planet_ink/coffee_mud/Commands/Replay.java, com/planet_ink/coffee_mud/Commands/Rules.java, com/planet_ink/coffee_mud/Commands/Score.java (lines 35-442).
- **Read in part:** com/planet_ink/coffee_mud/Commands/Sell.java (lines 41-127 for the body, and the cost methods from the grep of the file), com/planet_ink/coffee_mud/Commands/Remove.java (lines 41-120 with the cost methods from grep).
- **Read as cited support:** com/planet_ink/coffee_mud/Commands/StdCommand.java (all 317 lines), com/planet_ink/coffee_mud/Commands/interfaces/Command.java (lines 1-181), com/planet_ink/coffee_mud/Commands/Go.java (cost methods, lines 261-275), com/planet_ink/coffee_mud/core/CMProps.java (cost functions and config keys), com/planet_ink/coffee_mud/core/CMFile.java (constructor flags and prefix checks), com/planet_ink/coffee_mud/core/CMSecurity.java (`hasAccessibleDir`), com/planet_ink/coffee_mud/Libraries/Dice.java (`roll`), com/planet_ink/coffee_mud/MOBS/StdMOB.java (serve and rebuke handlers), com/planet_ink/coffee_mud/Libraries/GroundWired.java (manufacturer lookup), lists.ini (CHARSTAT_CHART and EXTREME_ADVS), coffeemud.ini (cost defaults, remort keys, PLAYERDEATH), com/planet_ink/coffee_mud/Common/interfaces/Session.java (MAX_PREVMSGS), com/planet_ink/coffee_mud/Common/interfaces/PlayerStats.java (reply types).
- **Skimmed:** license headers and imports in every file; the Retire and Request class headers.
- **Skipped:** the body of com/planet_ink/coffee_mud/Commands/Go.java other than the cost methods, the Affect and Skills base classes (other buckets), the CMLib services called from these commands (other buckets), and any subcommand code that did not change the behaviour described above.
- **Not run:** no command was executed, so the Shell DELETE scope, the Remort abort path and the Score numeric examples are read from the code only.
