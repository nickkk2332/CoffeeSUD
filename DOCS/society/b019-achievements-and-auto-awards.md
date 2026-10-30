# Achievements and auto-awards

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b019` (Achievements and auto-awards) · 4 files · 12306 lines of Java.

## Summary

The achievement system is a data-driven engine. Each row of `resources/achievements.ini` names one of 47 event types (`Libraries/interfaces/AchievementLibrary.java:71-120`), that event's parameters, a display name, an optional title and a list of typed rewards; the row's tattoo key is both the unique id and the completion marker. Game code reports events with `possiblyBumpAchievement(...)`, which routes each event to the matching tracker for a player, account or clan, while many "state" achievements (stats, factions, exploration, class levels) are polled instead. Completion stamps the tattoo and applies rewards (titles, XP, stats, abilities, expertises, items) under rules that differ for remort, character creation, accounts and clans. A separate library, `AutoAwards`, gives calendar-driven bonuses from `resources/awards.txt` and capped, mask-based titles from `resources/titles.ini`, so the world keeps producing goals and rewards on its own clock.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Libraries/Achievements.java` | Achievement engine: row parser and 47-way event switch (946-8136), event dispatch (8138-8480), award granting and removal (8483-9191), polling and load hooks (9730-10106) | 10219 |
| `Libraries/interfaces/AchievementLibrary.java` | `Event` enum with per-event parameter lists (71-211), `AwardType` (369-385), `AchievementLoadFlag` (394-401), `Achievement`, `Award` and `Tracker` interfaces (231-643) | 897 |
| `Libraries/AutoAwards.java` | Auto-title loading, capped-title bookkeeping, auto-property file parser (`getAllAutoAwards`, 626-742), `giveAutoProperties` (744-767) | 894 |
| `Libraries/interfaces/AutoAwardsLibrary.java` | `AutoTitle` and `AutoProperties` interfaces (204-295) | 296 |
| `Abilities/Misc/AutoAwards.java` (outside bucket, read 116-296 and 380-414) | Hidden effect attached to a character that applies matching auto-properties when the in-game calendar changes | 568 |
| `Common/DefaultPlayerStats.java` (outside bucket) | Stores per-character trackers in `achievementers`; save and load of `<ACHIEVEMENTS>` (1075-1083, 1474-1490, 1758-1790) | n/a |
| `resources/achievements.ini` (data) | 1712 lines, 1235 achievement rows in sections `[PLAYER]`, `[ACCOUNT]`, `[CLAN]` | n/a |
| `resources/awards.txt` (data) | 1027 auto-property rows (`character mask :: date mask :: effects`) | n/a |
| `resources/titles.ini` (data) | Auto-title definitions; the shipped file has comments only, no active rows | n/a |

## How it works

### 1. Definition rows

- The file is `achievements.ini`, or `achievements.txt` if only that exists (`Achievements.java:9838-9848`). Extra parts `achievements.ini.2` through `.98` are concatenated (9862-9869).
- Section headers `[PLAYER]`, `[ACCOUNT]` and `[CLAN]` set the agent for the rows that follow; rows before any header are `PLAYER` (9871-9891).
- A row is `TATTOO=KEY="value" KEY=value ...`. The tattoo is the text before the first `=`, upper-cased (956). Keys are read with `CMParms.getParmStr`, and `DURATION` with `getParmInt` (969).
- Base keys (`AchievementLibrary.java:59-60`): `EVENT`, `DISPLAY`, `TITLE`, `REWARDS`, `VISIBLEMASK`, `DURATION`, `PLAYERMASK`, `FLAGS`. The only flag is `REMORT`, documented as "is reset on remort and can be re-attained" (`AchievementLibrary.java:218-221`).
- Parse order in `evaluateAchievement` (946-8136): a `TITLE` award is added first (993-1027), then `REWARDS` through `parseAwards` (1030), then one anonymous `Achievement` class is built from the `EVENT` switch (1039-8080), then `canApplyTo(agent)` (8087), then the class's own `parseParms` (8092), then registration (8098-8134).
- Validation: a tattoo may not start with a digit (959-960); a duplicate tattoo is rejected (8101-8106); each event's `parseParms` validates its own keys and returns `"Error: ..."` strings. Bad rows are logged and skipped, not fatal (9892-9896).
- The display name is indexed in `achievementFinder` (8126-8133) so that chat and commands can find a tattoo by its name.
- Example row from the shipped data: `ACH_MAXSKILL1=EVENT="SKILLPROF" ABILITYID="SKILL" NUM=1 PROF=100 DISPLAY="Get a skill to 100% proficiency"` (`resources/achievements.ini`).

### 2. The 47 event types

The `Event` enum (`AchievementLibrary.java:71-120`) declares each event's extra parameters. `BASE_ACHIEVEMENT_PARAMETERS` are added to all of them. "Fed by" gives the bump site (`possiblyBumpAchievement` unless noted). "Polled" means no game code bumps it; `evaluate*Achievements` checks it (section 5). Line numbers are in `Libraries/Achievements.java` unless a file is named.

| Event | Extra parameters | Fed by | Tracker behaviour |
|---|---|---|---|
| KILLS | NUM, ZAPPERMASK | `MUDFight.java:2732` (arg: victim) | Victim must match ZAPPERMASK, killer must match PLAYERMASK; `count += bump`. Savable. |
| STATVALUE | VALUE, ABOVEBELOW, STAT | Polled | Reads a generic stat from the mob (`getAnyGenStat`, 1803). Done when above or below VALUE. Not savable. Player and account only (1726-1728). |
| FACTION | VALUE, ABOVEBELOW, ID | Polled | `fetchFaction(ID)` against VALUE (1970); an unset faction fails (1958). |
| FACTIONS | VALUE, ABOVEBELOW, IDMASK, NUM | Polled | Counts factions matching IDMASK that are above or below VALUE (2141-2143); done when the count reaches NUM (2129). |
| EXPLORE | PERCENT, AREA | Polled | `pstats.percentVisited(mob, area)` or the whole world (2318-2331); `DefaultPlayerStats.java:1750-1756`. |
| CHARITY | AMOUNT, ZAPPERMASK | `Behaviors/Beggar.java:76` (bump = amount given, arg = beggar) | Beggar must match ZAPPERMASK; `count += amount`. |
| ROOMENTER | ROOMID (list) | Polled | Counts listed rooms the player has visited (`hasVisited`, 4144); done when all are visited. |
| CRAFTING | NUM, ABILITYID, ITEMMASK | 34 craft-skill classes under `Abilities/Common/` (e.g. `Blacksmithing.java:232`) | Per completed item. ABILITYID accepts `*`, a skill ID, a category or a domain (2519-2523); only common-skill crafting, building, epicurean and crafting domains are allowed (2587-2606). |
| MENDER | NUM, ABILITYID, ITEMMASK | 19 repair skills (e.g. `Carpentry.java:161`) | Same tracker as CRAFTING. |
| DECONSTRUCTING | NUM, ABILITYID, ITEMMASK | `Abilities/Common/Costuming.java:221`, `CraftingSkill.java:1951` | Same tracker; the item is `parms[1]` (2525-2529). |
| SKILLUSE | NUM, ABILITYID | `Abilities/StdAbility.java:1821` | Counts uses of matching skills. |
| SKILLPROF | NUM, ABILITYID, PROFF (default 100) | `StdAbility.java:1866`, `Abilities/Common/Lecturing.java:232` | Bump when the player's proficiency is at least PROFF (2962-2963). See problems. |
| EFFECTSHAD | NUM, ABILITYID | `StdAbility.java:754` | Counts distinct effect IDs seen, using a bitset over the sorted ID list (3178-3184). |
| SOCIALUSE | NUM, SOCIALID | `Common/DefaultSocial.java:503,520,536` | Matches the social's name, base name or tail name (3399-3403). |
| QUESTOR | NUM, QUESTMASK (regex) | `Common/DefaultScriptingEngine.java:13357` | Regex on quest name or display name (3798-3806). |
| ACHIEVER | ACHIEVEMENTLIST | Polled | Counts listed tattoos held by the player or account (3976-3982); done at the list size (3962). See problems. |
| LEVELSGAINED | NUM | `Libraries/CoffeeLevels.java:488` (-1 on loss), `:1362` (+1 on gain) | Net levels. Shares a tracker class with RETIRE, REMORT, LEVELUP and LEVELDOWN (4390-4394). |
| CLASSLEVELSGAINED | NUM, CLASS | `CoffeeLevels.java:489`, `:1365` | For a player, derived from the current class level (`getClassLevel + 1`, 4321-4324) and not stored. For a clan, a stored count. |
| TIMEPLAYED | SECONDS | `MOBS/StdMOB.java:4297`, once per 120000 ms (`MOB.java:47`) | Bumped with the seconds accrued; done at SECONDS (4875). |
| JUSTBE | TIMEOUT (not declared) | Polled | A state: PLAYERMASK matches, and if TIMEOUT is set the character's age in minutes is below it (1630-1636). No count. Not savable (1680-1683). |
| DEATHS | NUM, ZAPPERMASK | `MUDFight.java:1524-1526` | Mask applies to the killer, or to the dying player if there is no killer. |
| REMORT | NUM | `Commands/Remort.java:445` | One bump per remort. |
| RETIRE | NUM | `Commands/Retire.java:97`, `:119` | One bump per retirement. |
| GOTITEM | NUM, ITEMMASK (repeatable), ALL | `Libraries/CommonMsgs.java:2673`, `:2707` (+1), `:2738` (-1) | Inventory scan. The cached count is set to -1 on a matching bump (4700-4722), which forces a recount (4649-4651). ALL requires every mask to be present (4683-4686). |
| BIRTHS | NUM, ZAPPERMASK | `Abilities/Misc/Pregnancy.java:644-646` | Fired for each player parent; arg is the child. |
| RACEBIRTH | NUM, ZAPPERMASK | `Pregnancy.java:649-651` | Fired only when `newRaceGenerated` is set. |
| PLAYERBORNPARENT | NUM, ZAPPERMASK | `Abilities/Misc/Age.java:1084` | Fired for each player parent of a new player-born character. |
| PLAYERBORN | (none) | `Age.java:1079` | Target count 1 (5461-5464). |
| CHARACTERS | NUM | `Common/DefaultPlayerAccount.java:487`, `:516` (null mob, no-op), `:541` | Account only. Live count of the account's characters matching PLAYERMASK (5830-5853). Not savable (5886-5890). |
| CLANMEMBERS | NUM, PLAYERMASK | `Commands/ClanAccept.java:142`, `ClanApply.java:126`, `ClanExile.java:128`, `ClanResign.java:109` | Clan only. Live count of members matching the mask (6176-6197). |
| CLANKILLS | NUM, ZAPPERMASK | `MUDFight.java:2771` | A killer in a clan kills a member of a rival clan (`findRivalrousClans`). |
| CLANLEVELSGAINED | NUM | `Common/DefaultClan.java:613`, `:625` | Clan level. |
| CLANDECLARE | NUM, RELATION | `Commands/ClanDeclare.java:164-165` | Counts declared relations in RELATION (7336-7337). |
| CLANPROPERTY | NUM, AREAMASK | `Items/Basic/StdBoardable.java:1029,1040`, `StdTitle.java:512,641,721` | Clan only. Area must match AREAMASK (6391-6393). |
| CONQUEREDAREAS | NUM, AREAMASK | `Behaviors/Conquerable.java:370,1122` | Area must match AREAMASK. |
| CONQUESTPOINTS | NUM, AREAMASK | `Behaviors/Conquerable.java:472` | Area must match AREAMASK. |
| AREAVISIT | AREAMASK, NUM, TIME_MINS, ROOMMASK, INTRUDE | `Behaviors/MOBEater.java:346`, `Locales/StdRoom.java:785`, `MOBS/Dragon.java:567` | Cooldown of TIME_MINS per achievement (`recentVisit`, 6917, 6949). With INTRUDE, counts only when the room or area is owned or ruled and the visitor lacks privilege (6919-6949). |
| INSTANCEEXPIRE | AREAMASK, NUM, TIME_MINS, VALUEEXP | `MOBS/StdMOB.java:3683` (on `TYP_EXPIRE`) | Expression on the instance value (`VALUEEXP`, 7144-7146), with a TIME_MINS cooldown. |
| SHIPSSUNK | NUM, SHIPMASK | `Items/Basic/GenAirShip.java:376`, `GenSailingShip.java:398` | Ship must match SHIPMASK (6553). |
| CMDUSE | NUM, COMMANDID | `MOBS/StdMOB.java:2258` | Command ID match (3609-3611). |
| GROUPKILLS | NUM, SIZE, ZAPPERMASK | `MUDFight.java:2737` (once per player group member) | Victim matches the mask and the group size is at least SIZE (1488). |
| ENTITLED | NUM, TITLEMASK (regex) | Polled | Counts titles that match the regex; the count is cached by list size (7684-7694). |
| SCRIPTED | NUM | `DefaultScriptingEngine.java:13575` (`MPACHIEVE` bump), `:13573` (grant) | Scripts add arbitrary amounts; a zero bump is special-cased (7866). |
| LEVELUP | NUM | `CoffeeLevels.java:1363` | +1 per level gained (shares LEVELSGAINED's class). |
| CLASSLEVELUP | NUM, CLASS | `CoffeeLevels.java:1364` | Same class as CLASSLEVELSGAINED. |
| LEVELDOWN | NUM | `CoffeeLevels.java:490` | +1 per level lost. |
| CLASSLEVELDOWN | NUM, CLASS | `CoffeeLevels.java:491` | Same class as CLASSLEVELSGAINED. |

Agent restrictions seen in `canApplyTo`: STATVALUE and JUSTBE are player or account only (1726-1728, 1562-1565); CHARACTERS is account only (5746-5749); CLANMEMBERS and CLANPROPERTY are clan only (6104, 6286). KILLS and most others accept any agent (1060-1063).

### 3. Trackers and storage

- `Tracker` (`AchievementLibrary.java:589-643`) has `isAchieved(tracked)`, `testBump(mob, tracked, bumpNum, parms...)`, `getCount(tracked)`, `getCountParms(tracked)` and `copyOf()`. The `tracked` object is a `MOB`, `PlayerAccount` or `Clan`, which is why the interface is `Tattooable`.
- Trackers are created per achievement by `Achievement.getTracker(oldValue)`. The count is a field of an anonymous class, for example `private volatile int count = CMath.s_int(oldVal);` (1125).
- `PlayerStats.getAchievementTracker` (`Common/DefaultPlayerStats.java:1771-1785`) caches a tracker per tattoo in `achievementers`. `killAchievementTracker` (1758-1768) drops it and removes the tattoo, which is used for repeatable achievements.
- Saving: `DefaultPlayerStats.java:1075-1083` writes `<ACHIEVEMENTS TATTOO=count .../>`, but only for trackers whose `isSavableTracker()` is true and whose count is non-zero. Loading (1474-1490) rebuilds every player achievement with `getTracker(savedValue)`.
- Non-savable trackers are recomputed from the mob on each check. STATVALUE, JUSTBE and CHARACTERS are examples, and CLASSLEVELSGAINED is savable only for clans (4368-4371).
- Accounts and clans have the same methods (`Common/DefaultPlayerAccount.java:629-660`, `Common/DefaultClan.java:2752-2770`). These were not read in full.
- `copyOf()` is used for previews, so a preview can test a copy without changing the saved count (8386-8387, 8406-8407).

### 4. Event dispatch

`possiblyBumpAchievement(mob, event, bumpNum, parms...)` (`Achievements.java:8346-8379`) runs in four steps:

1. Guard: `mob != null`, `mob.isPlayer()` and the host is running (8348-8351). A null mob is therefore a silent no-op.
2. Send a `CMMsg` of type `TYP_ACHIEVE` with text `E:<EVENT>:<+/-n>` to the mob's room (8194-8206).
3. For each achievement in `eventMap[event]`, dispatch by agent: `possiblyBumpPlayerAchievement` (8208-8224), `possiblyBumpAccountAchievement` (8226-8245) or `possiblyBumpClanAchievement` (8247-8290). A clan dispatch uses `parms[0]` when it is a clan, otherwise every clan the mob belongs to.
4. Each dispatch skips achievements the target already holds (`findTattoo`), then calls `testBump`. If it returns true and `isAchieved` is also true, it sends a message with code `P`, `A` or `C` (completion) and calls `giveAwards`. Otherwise it sends `p`, `a` or `c` (progress) (8217-8241, 8262-8266).

The progress messages are on the same `TYP_ACHIEVE` message type as the event message. `Behaviors/StockMarket.java:2070` is one consumer of that type.

`bumpAchievement` (8293-8312) bumps one specific achievement, and `grantAchievement` (8315-8343) forces it. Scripts use both (`DefaultScriptingEngine.java:13571-13575`).

### 5. Polled achievements

Achievements with no bump site are checked by `evaluatePlayerAchievements`, `evaluateAccountAchievements` and `evaluateClanAchievements` (`Achievements.java:9780-9836`). A check grants the achievement when `isAchieved` is true (9735, 9751, 9768). The callers are:

- The player thread, for every in-game player each cycle (`Libraries/CMPlayers.java:3536-3539`).
- The player's `achievements` command (`Commands/Achievements.java:152-154`).
- The clan thread (`Libraries/Clans.java:2388`).
- `reloadAchievements` (9898-9915), which re-checks all players after the definition file is loaded, so newly added rows apply retroactively.

So polled achievements are only as current as these calls.

### 6. Completion

`giveAwards(A, holder, mob, flag)` (`Achievements.java:9124-9191`) does the following:

1. If the holder does not already have the tattoo, it adds it. If `DURATION > 0`, it first calls `killAchievementTracker` and then `addTattoo(tattoo, duration)` (9129-9133). `AchievementLibrary.java:345-352` documents the duration as "in ticks"; how expiry is applied was not traced.
2. If the mob is not cloaked and the achievement is visible to it (`VISIBLEMASK`), it announces on the achievements channel to subscribers (9136-9152): "@x1 has completed the '@x2' @x3 achievement!".
3. For a clan, it announces to the clan (9158-9159), applies the clan rewards (9160), and calls `loadClanAchievements` for each online member with a role (9161-9174). For a player or account it tells the mob "You have completed the '...' achievement!" (9178-9180).
4. Only `PLAYER` achievements apply rewards to the mob at this point (9182-9187). Account achievements do not (see section 9).

### 7. Award types

`parseAwards` (`Achievements.java:89-944`) reads `REWARDS` as tokens: a number followed by one or more words (97-107). The result is an `Award` object with `getType()`, `isPreAwarded()` and `isNotAwardedOnRemort()`. The table lists what each type does in the code.

| Award | Written as | Effect on a player | Effect on a clan | Re-given on remort | Code |
|---|---|---|---|---|---|
| TITLE | `TITLE="..."` (base key) | Adds the title; `*` becomes the name (8651) | `@1` and `@2` become government name and clan ID (8647-8648) | Yes | 993-1027, 8640-8655 |
| XP | `N XP`, `N EXP`, `N EXPERIENCE` | `leveler().postExperience(mob, "ACHIEVE:...")` (8677) | Not applied (8757) | Yes | 109-114, 8673-8679 |
| QP | `N QP`, `N QUEST...` | Adds quest points (8620); removal takes them back if the player has enough (9016-9019) | n/a | Yes | 181-182, 8616-8622 |
| CURRENCY | `N <currency name>` (e.g. gold) | `beanCounter().giveSomeoneMoney` (8562) | n/a | Yes | 885-896, 8552-8567 |
| CLANCURRENCY | `N clan <currency>` | n/a | Deposit to the clan bank, or announce that there is none (8766-8807) | Never to players | 8766-8807 |
| CLANXP | `N CLANXP` | n/a | `clan.adjExp(null, N)` (8828) | Never to players | 8823-8830 |
| STAT | `N <GENSTAT>`, or `N ACCOUNT <stat>` for account stats | Adds to the stat with `setAnyGenStat` and recalculates (8588-8598) | n/a | Pre-awarded for `MAX*` stats and `BONUSCHARSTATS` (746-750); not re-given for stats kept on `PlayerStats` (751) | 746-760, 8568-8602 |
| ABILITY | `N <AbilityID>(PARMS= MASK= PREREQS= AUTOGAIN=)` | Stored in `pStats.getExtraQualifiedSkills()` (8546); `grantAbilitiesAndExpertises` adds the skill when the level and MASK are met (9926-9937) | n/a | Yes | 428-480, 8538-8551 |
| EXPERTISE | `N <ExpertiseID>` | Stored in `getExtraQualifiedExpertises()`; added with `addExpertise` if it has no cost (9941-9949) | n/a | Yes | 483-736, 8603-8615 |
| TATTOO | `TATTOO(ID=... DESC=... ACCOUNT=...)` | Adds a tattoo to the character, or to the account when ACCOUNT is true (8662-8668) | `clan.addTattoo` (8835) | Yes | 253-325, 8656-8672 |
| ITEM | `ITEM(ID=catalog name)` | A copy of the catalog item goes to inventory (8691) | n/a | Never | 327-418, 8680-8693 |
| MOB | `MOB(ID=catalog name)` | Catalog mob is brought to life and set to follow the player (8709-8710) | n/a | Never | 8694-8714 |
| NOPURGE | `N NOPURGE` | Runs the `NoPurge` command, which gives protection from auto-purge (8625-8630) | n/a | Yes | 217-251, 8623-8639 |

Catalog lookups run at parse time when the game is running (349-361), and `getItem()` copies the catalog entry each time it is called (385-390).

Award removal (`removeAwards`, 8893-9122) reverses the reversible awards (ABILITY, STAT, EXPERTISE, QP, NOPURGE, TITLE, TATTOO). It skips an award if another achievement the mob holds grants the same type and description (8903-8923). XP, CURRENCY, ITEM and MOB awards are never reversed (9086-9115).

### 8. Load flags: creation, remort and inheritance

`AchievementLoadFlag` (`AchievementLibrary.java:394-401`) filters `giveAwards` (8505-8529):

| Flag | Gives | Used by |
|---|---|---|
| `CHARCR_PRELOAD` | Pre-awarded rewards only, before stat selection (8519-8522) | `Libraries/CharCreation.java:3296` |
| `CHARCR_POSTLOAD` | Non-pre-awarded rewards (8523-8526) | `CharCreation.java:4158` |
| `REMORT_PRELOAD` | Pre-awarded rewards that are re-given on remort (8507-8512) | `Commands/Remort.java:625`, `:645` |
| `REMORT_POSTLOAD` | Non-pre-awarded rewards that are re-given on remort (8513-8518) | `Remort.java:701-703`, `Abilities/Properties/Prop_ReRollStats.java:114` |
| `NORMAL` | Everything (8527-8528) | `Commands/Import.java:5792` and completion paths |

The `*_PRELOAD` flags do not add the tattoo. They apply only the awards, so that stat maximums can be set before a character's stats are chosen (10031-10032).

Remort (`Commands/Remort.java:509`) deletes player achievements that carry the `REMORT` flag and keeps the others for re-award. `reloadPlayerAwards` (9990-10012) re-applies the player's achievement rewards. When a character is loaded, `loadPlayerSkillAwards` rebuilds skill and expertise qualifications from its tattoos (9955-9987; `core/database/MOBloader.java:266`).

### 9. Account and clan achievements

- **Account.** `giveAwards` applies rewards only for `PLAYER` achievements (9182-9187), so an account achievement is recorded on the account but gives nothing at completion. The rewards reach each character that lacks the tattoo when `loadAccountAchievements` runs (10016-10052). That function copies the account tattoos onto the character (10029-10038) and applies the rewards. The help text confirms the design: "New Characters, Children, and Remorted" (`Achievements.java:10136-10137`). No login-time call was found, so a character already in play gets account rewards at its next creation, remort or reroll.
- **Clan.** Rewards apply at completion (8724-8847). Members who join or load get the tattoo and the clan's rewards through `loadClanAchievements` (10054-10106), which is gated by the clan's `CLAN_BENEFITS` authority (10059). The help text says "Existing Members" (10140).
- `removeClanAchievementAwards` (8850-8864) removes the clan achievements a member inherited. Its callers were not traced.

### 10. Auto-titles (`resources/titles.ini`)

- Row format from the file header: `Title=[max:]zappermask`. The `*` in a title becomes the player's name. An escaped `\=` is allowed (`Libraries/AutoAwards.java:80-82`), and a duplicate title is rejected case-insensitively (104-111). `getAutoTitleFilename` prefers `titles.ini` and falls back to `titles.txt` (56-67).
- `evaluateAutoTitles(mob)` (198-243) walks all titles. If the mask matches and the player does not hold the title, it adds the title twice (the second add is commented "put it on TOP!", 223-224) and bumps the holder count. If the count is over `max`, it calls `dispossessOldTimers` (226-227). If the mask no longer matches and the player holds the title, it removes it and lowers the count (231-239).
- `dispossessOldTimers` (296-350) finds the holders with a database search for `<TITLE>...</TITLE>`, sorts them by last login (307-314), and removes the title from the oldest holders until the count fits. It skips the player who just qualified (319-320).
- Counts are kept in memory. `updateTitleMax` (269-294) counts existing holders when a capped title is added.
- Refresh happens in `reloadAutoTitles` (450-479), which re-checks every online player, in the player thread (`CMPlayers.java:3536`), and when the player uses the title command (`Commands/Title.java:64`).
- The file header says braces mark a mandatory title that the player cannot change. No handling of braces was found in `AutoAwards.java`, so this is unverified.

### 11. Auto-properties (`resources/awards.txt`) and the AutoAwards effect

- Row format from the file header: `[character zappermask] :: [date zappermask] :: Id(args) Id(args) ...`, where an `Id` is an ability or behaviour (`resources/awards.txt`, header lines 3-4).
- `getAllAutoAwards` (`Libraries/AutoAwards.java:626-742`) splits each row on `::` (649-657). A small state machine then reads `Id(args)` pairs, with backslash escapes inside the parentheses (705-707). Unknown IDs are logged and dropped (`addProp`, 607-624).
- `AutoPropertiesImpl` (500-581) takes its period from the first time-period name found in the date mask (520-530), defaulting to YEAR. It keeps a hash so that changes can be detected (531-537).
- `giveAutoProperties` (744-767) gives a player a hidden `AutoAwards` effect (`Abilities/Misc/AutoAwards.java`). NPCs are skipped unless a security flag is set (747-750). Callers include character setup (`MOBS/StdMOB.java:1374`, with reset) and several other paths (`StdMOB.java:3499, 3501, 3637`; `Behaviors/StockMarket.java:1592, 1632, 1965`).
- The effect (`Abilities/Misc/AutoAwards.java`) works in three steps:
  - `assignMyEntries` (380-414) keeps the global entries whose character mask matches this character. It recomputes only when the global list's hash changes (`savedHash`, 202-208).
  - `tick` (193-296) rechecks the date masks only when the character's home-clock hour changes, or when forced. `gatherTimelyEntries` (116-153) tests each entry's date mask with `maskCheckDateEntries`.
  - If the set of matching entries changed, the effect clears its affects (261), reapplies every matching property with `applyAward` (155-191), and recalculates the stats (275-279).
- `applyAward` creates an ability with `setMiscText(parms)` and proficiency 100, or a behaviour with `setParms` (155-191). With `reverse` set, it swaps every `+` and `-` in the parameters (162-171).
- Druid fortune chants manipulate this effect through stats: `Abilities/Druid/Chant_StealFortune.java` (SUPPRESSOR and HOLDER, with the award string copied, 100-110 and 155-174); `Chant_ReverseFortune.java` (REVERSER, 96-97 and 141-142); `Chant_SuppressFortune.java` (SUPPRESSOR, 96-97 and 140-141); and `Chant_ExtendFortune.java` (HOLDER, 96-97 and 140). All of them set `FORCETICK`.

### 12. Interactions with other systems

- Achievements write to the player's stats, skills, expertises, titles and inventory, and to the clan's bank and experience (sections 6-9).
- Achievements feed the channel system (`ChannelFlag.ACHIEVEMENTS`), the clan announcement system, and the message stream (`TYP_ACHIEVE`).
- Scripts can grant or bump achievements (`DefaultScriptingEngine.java:13357-13575`).
- The web admin editor reads and writes definitions through `WebMacros/AchievementData.java` (166, 192). In-game editing goes through `addModifyAchievement` (9458-9590).
- Remort and retire previews run `fakeBumpAchievement` (`Commands/Achievements.java:212-214`), which tests copies of the trackers and lists what would be earned.
- Character creation and remort use the load flags (section 8) through `CharCreation.java` and `Remort.java`.

## Content worth mining

**achievements.ini (1235 rows):** `[PLAYER]` 487 rows, `[ACCOUNT]` 581 rows, `[CLAN]` 167 rows (section markers at lines 365, 879, 1514). Rows by event, in descending order: REMORT 517, CLASSLEVELSGAINED 235, SKILLPROF 184, CRAFTING 65, RETIRE 48, SKILLUSE 35, QUESTOR 20, INSTANCEEXPIRE 15, KILLS 14, STATVALUE 12, FACTIONS 12, DEATHS 12, CLANDECLARE 12, ACHIEVER 10, RACEBIRTH 7, SHIPSSUNK 6, EXPLORE 5, PLAYERBORNPARENT 5, CONQUESTPOINTS 5, CLANMEMBERS 5, EFFECTSHAD 3, MENDER 2, PLAYERBORN 2, CONQUEREDAREAS 2, SOCIALUSE 1, CLANPROPERTY 1. Twenty-six of the 47 events are used.

Patterns worth reusing:

| Pattern | Example from the data | What it shows |
|---|---|---|
| One mastery per class | `ACH_MASTER<class>`: CLASSLEVELSGAINED, NUM=30, TITLE `*, Master <Class>` | A templated family (one row per class) |
| Account "trying out" and "past life" ladders | `ACCT_ACH_TRIED<class>` (15 levels); `ACCT_REM_<class>1` (REMORT, reward `5 MANA`, mask `-ANYCLASSLEVEL +<class> +>29`, display "<Class> Past Life I") | Account-wide progress across characters |
| Kill goal with a mask | `ACH_DRAGONSLAYR`: KILLS, NUM=200, ZAPPERMASK `-RACE +Dragon -LEVEL +>20`, TITLE `*, Dragon Slayer` | Goal with a target type and a level filter |
| Stat threshold | `ACH_SUPERSTRENGTH`: STATVALUE, ABOVEBELOW ABOVE, STAT STRENGTH, VALUE 24, TITLE `*, the Powerful` | A state check, not an event |
| Composite | `ACH_1ACHIEVER`: ACHIEVER, ACHIEVEMENTLIST of two tattoos, TITLE `*, Master Quester` | Achievements built from other achievements |
| Expiring achievement | `ACH_INSTEXPDO`: INSTANCEEXPIRE, DURATION=150, VALUEEXP `$VALUE>0`, reward ` 1 VICTORY`, "Complete a Triumph" | Repeatable or time-limited tattoo |
| Lineage | `ACH_MUDDADDY`: PLAYERBORNPARENT, TITLE `Father *`, PLAYERMASK `-GENDER +MALE` | Titles from world events, not combat |
| Clan ladder | `CLACH_ALLCRAFTOR` (craft 90000 items, 10000 CLANXP); `CLACH_CROSSBREEDING` (100 new races, 50000 CLANXP); `CLACH_CRAFTACHIEVER` (all clan crafting achievements, 100000 CLANXP) | Group goals with large shared rewards |

Reward strings are short and repetitive. Among the 724 lines with a non-empty `REWARDS`, the most common shapes are `N TRAINS` (85), `N XP` (76), `N CLANXP` (49), `N MANA` (26), `-N ARMOR` (26), `N ATTACK` (23), `N DAMAGE` (20) and `N HITS` (18). `TITLE=` appears on 985 lines. The catalog award types are nearly unused in the shipped data (one `TATTOO(`, one `ITEM(`, one `MOB(`, one `NOPURGE`), and the one `TATTOO` row is `TATTOO(ID=RACE_ELF ...)`.

**awards.txt (1027 rows):** 592 rows use weekly date masks (`-WEEKOFYEAR +n`), 76 use monthly masks (`-MONTH +n`), and none use hourly masks. Examples:

- `-BASECLASS +Fighter :: -MONTH +1 :: Prop_ModExperience(+50% SELF)`, which gives a monthly experience bonus to one base class per month.
- `-Baseclass +Bard :: -MONTH +1 :: Prop_Adjuster(SAVEGEN-10)`, which applies a monthly adjustment to a stat.

**titles.ini:** no active rows. The header describes the format and says titles are refreshed every mud-hour (10 minutes), which was not verified against the player thread's cadence.

## Notable design ideas

- **The tattoo is the key and the gate.** One string names the achievement, marks completion, and can gate play. The `CHARCLASS_` and `RACE_` tattoos unlock classes and races in character creation (`CharClasses/StdCharClass.java:470-484`).
- **One engine, three scopes.** The same tracker and dispatch code serves players, accounts and clans. Accounts pass achievements to new characters, and clans pay their rewards into the clan bank.
- **Progress is derived where possible.** Class-level progress is read from the character's current class level (4318-4327) and needs no saved counter. Stat, faction, exploration and room-visit progress are likewise read from the world state.
- **Each event declares its parameters.** The `Event` enum (`AchievementLibrary.java:71-120`) lists the keys each event accepts, and the editor and row writer use that list (`buildRow` 9318-9335, `fillAchievementParmTree` 9421-9456).
- **Previews without side effects.** `copyOf()` and `fakeBumpAchievement` let the game show what a remort or retire would earn (`Commands/Achievements.java:212`).
- **Reversible bonuses.** `applyAward(..., reverse)` swaps `+` and `-` (`Abilities/Misc/AutoAwards.java:162-171`), so one chant can invert a bonus. Four fortune chants steal, suppress, reverse or extend another player's bonuses.
- **Calendar-driven bonuses with change detection.** The auto-award effect checks only when the hour changes, and rebuilds its list only when the matching set changes (`Abilities/Misc/AutoAwards.java:193-296`). This is a good fit for a living world.
- **Masks everywhere.** Titles, achievements and auto-awards all use the same zapper-mask language (`CMLib.masking()`), so one filter syntax covers "who qualifies".

## Problems and cruft

1. **Clear bug: wrong cast in TATTOO awards.** `Achievements.java:8662-8663` reads `if(pStats instanceof PlayerAccount) ((PlayerAccount)taward).addTattoo(tattooStr);`. The cast should be on `pStats`. As written, `taward` is an anonymous `TattooAward`, so the cast throws `ClassCastException` whenever that branch runs. The matching removal code at 9065-9075 casts `pStats` correctly. Whether `mob.playerStats()` can ever be a `PlayerAccount` was not established, so the impact is latent.
2. **Wrong format string.** `Achievements.java:727` reads `L("@x1 qualification at level @x1", ""+defE.getMinimumLevel())`. It uses one placeholder twice and passes one argument, so the expertise name is missing and the level is shown in its place.
3. **Parameter name mismatch.** SKILLPROF's enum entry declares `PROF` (`AchievementLibrary.java:84`), but the code reads `PROFF` with default 100 (`Achievements.java:3002-3005`). The shipped data uses `PROF=100` (184 rows), so custom thresholds are silently ignored, and the error message also names `PROF`.
4. **Undeclared key dropped on re-save.** JUSTBE reads `TIMEOUT` (1689) but the enum does not declare it (`AchievementLibrary.java:92`). `buildRow` and `fillAchievementParmTree` only write declared keys (9318-9335, 9421-9456), so an edit in the admin editor would drop TIMEOUT. This follows from reading the code and was not tested.
5. **Always-true guards.** Several zapper-mask checks test `length() >= 0`, which is always true: `Achievements.java:5087` (BIRTHS), `5248` (RACEBIRTH), `5409` (PLAYERBORNPARENT), `5716` (DEATHS) and `6592` (SHIPSSUNK). By contrast, KILLS (1197) and CLANKILLS (6071) reject an empty mask. Whether an empty compiled mask matches everything was not checked.
6. **Count fallback can pass a goal.** CHARACTERS returns 1 when the account has no matching characters or no account (`Achievements.java:5853-5856`). An achievement with NUM=1 can then be satisfied by a mask that matches nothing.
7. **Double counting in ACHIEVER.** For each listed tattoo, `getCount` adds 1 if the character holds it and another 1 if the account holds it (3976-3982). `loadAccountAchievements` copies account tattoos onto new characters (10029-10038), so an account-level list can reach its size after half of its entries. This is reasoned from the code and was not run.
8. **Null mob makes a no-op.** `DefaultPlayerAccount.java:516` calls `possiblyBumpAchievement(null, CHARACTERS, -1)`, and the guard at `Achievements.java:8348` returns at once for a null mob.
9. **Possible null dereference.** In `removeAwards`, `mob.fetchEffect(myA.ID())` is followed by `effectA.isNowAnAutoEffect()` with no null check (`Achievements.java:8943-8944`). Whether `fetchEffect` can return null here was not established.
10. **Stale ENTITLED cache.** The cached title count is refreshed only when the list size changes (7684-7694), so a rename that keeps the size gives a stale count.
11. **Non-atomic counters.** Counters are `volatile int` updated with `count += bumpNum` (1161 and similar). Two threads bumping one tracker could lose an update. This was not reproduced.
12. **Costly boot and expiry.** `updateTitleMax` and `dispossessOldTimers` run database searches and load offline players from the database (`AutoAwards.java:269-350`).
13. **Account rewards arrive late.** Account achievements give nothing when earned, and the rewards arrive only at creation, remort or reroll (section 9). This is documented in the help text, but it is surprising.
14. **Minor.** `GROUPKILLS` checks `numStr` where it means `szStr` (`Achievements.java:1527`), which is harmless because `numStr` was validated first. `ARMOR` award text uses `"ARMOR".startsWith(stat)` (8592), which tests the wrong way round.

## Takeaways for a Python SUD

1. **Make achievements data, not code.** Store each one as a record: `id` (the tattoo key), `event`, `display`, `title`, `rewards`, `player_filter`, and optional `duration_turns` and `flags`. Keep the event list in one registry that also declares each event's accepted parameters, as CoffeeMud's `Event` enum does. Validate rows at load and log errors rather than failing.
2. **Use one dispatch path.** Map each event name to a list of achievements, then do `bump(player, event, amount, **context)`: for each achievement in the list, skip it if held, call `tracker.test(...)`, and if it is complete, grant it. Send a progress message as well, for the UI.
3. **Keep trackers small.** A tracker is a plain object with `test(ctx)` and `is_achieved(player)`. Save only a `dict` of `tattoo -> count` in the save file. Derive state-based progress (stat thresholds, class level, visited rooms, faction standing) from the world, and check it on a schedule or after relevant changes. No counter needs saving for those.
4. **Awards as small objects with `apply(player)`.** Keep these types: XP, currency, stat delta, ability or skill unlock, title, item, and flag (tattoo). Drop the remort, clan, account and catalog-mob variants. The reversible `revoke` is worth keeping only if the game has penalties or character resets.
5. **Calendar bonuses map well to a living world.** Copy the idea from `awards.txt`: a player predicate plus a calendar predicate. Only re-evaluate when the calendar key changes, using `(hour, day, season, month)`, and only re-apply when the set of active bonuses changes (hash it). Season and weekday bonuses could drive world events, not only player stats.
6. **Titles as a list of predicates.** Titles are a simple `title -> predicate` mapping with an optional cap. The cap and the "oldest holder loses it" logic are for multiplayer, so drop them for one player.
7. **Masks as Python predicates.** Rather than a mask language, write filters as small lambdas or a parsed dict such as `{"race": "Dragon", "min_level": 21}`. Reuse the same filter for titles, achievements and bonuses.
8. **Keep the fake-bump preview.** A `preview(player, event, amount)` that runs tests on copies is cheap and lets the player see what an action would earn.
9. **Drop the multiplayer parts.** Remove accounts, clans, clan banks, PvP kill events (`CLANKILLS`, `GROUPKILLS`, `DEATHS` against players), channel announcements, and inheritance by new characters. Keep the player-level and world-level events: kills, skill use and proficiency, crafting, exploration, quest completion, level and class progress, and item possession.
10. **Fix the problems before porting.** Avoid the wrong-cast and format-string bugs, use one parameter name per key, check that a mask filters as intended, and never let a count fallback satisfy a goal.

## Coverage

- **Read in full:** `Libraries/interfaces/AchievementLibrary.java` (897 lines), `Libraries/interfaces/AutoAwardsLibrary.java` (296), `Libraries/AutoAwards.java` (894), and `Libraries/Achievements.java` lines 1-1859 (the award parser and the first five event classes, KILLS to STATVALUE), 8077-9122 apart from 8737-8749 (dispatch, awards and removal), 9124-9233 (completion), and 9730-10219 (polling, load hooks and help).
- **Read in part:** `Libraries/Achievements.java` lines 1860-8076 (the remaining event classes), read through a filtered extract that kept each class's `testBump`, `isAchieved`, `getCount` and `parseParms` bodies and dropped shared boilerplate, plus a grep of each event's parameter keys. Also `Libraries/Achievements.java` 9233-9729 (help map, `addModifyAchievement`, `resaveAchievements`), which was not read, and 9318-9335 and 9421-9456, which were read.
- **Outside the bucket, read in part:** `Abilities/Misc/AutoAwards.java` lines 116-423 (tick, apply and assignment), with 1-115 and 424-568 not read; the bump call sites in `Abilities/Misc/Pregnancy.java`, `Age.java`, `Libraries/MUDFight.java`, `Libraries/CoffeeLevels.java`, `Libraries/CommonMsgs.java`, `Common/DefaultPlayerStats.java` (1075-1083, 1474-1490, 1750-1790), `Common/DefaultPlayerAccount.java` and `Common/DefaultClan.java` (tracker methods only), `Commands/Remort.java`, `Commands/Retire.java`, `Commands/Achievements.java`, `Common/DefaultScriptingEngine.java`, `CharClasses/StdCharClass.java`, `Commands/Title.java`, and `Libraries/CMPlayers.java`. Each was read only around the cited lines.
- **Data, aggregated with scripts:** `resources/achievements.ini`, `resources/awards.txt` and `resources/titles.ini`, using counts, headers and sampled rows, not every row.
- **Skipped:** the web and command editors (`WebMacros/AchievementData.java`, `Commands/Achievements.java` beyond the cited lines), `getAwardString` (9336-9420), and the save and edit paths (9457-9729).
