# Achievements, awards and clan governments

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `c02` (content: Achievements, awards and clan governments) · 3 files · 357,345 bytes of data.

## Summary

Three data files form a long-term progression and governance layer on top of the CoffeeMud engine. `resources/achievements.ini` defines 1,235 live achievements (487 for characters, 581 for accounts, 167 for clans). Each one is a counter over one of 47 event types, and completing it sets a persistent "tattoo" mark and grants rewards such as XP, training points, stats, abilities, titles and expertises. `resources/awards.txt` holds 1,027 rules that attach temporary property effects (saving throws, attack, damage, crit chance, XP rate) to characters by a character filter and a calendar filter (week, day of year, month, year, birth month). `resources/clangovernments.xml` defines 11 clan government types, each a rank table of positions with powers, voting rules, promotion rules, member perks and a clan XP curve.

These matter for a single-player SUD because all three are data, not code. A new achievement, calendar blessing or political system is a file edit that the engine interprets. Much of the machinery serves multiplayer (accounts, clans, dues, intermud, timed clan death), so the transferable parts are the event-counter model, the reward grammar, the calendar-filtered effects, and the rank-and-power government model.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `resources/achievements.ini` | 1,235 achievement definitions in `[PLAYER]` (line 365), `[ACCOUNT]` (879) and `[CLAN]` (1514); documented header at lines 1-363 | 1,712 |
| `resources/awards.txt` | 1,027 `character mask :: date mask :: property(args)` rules; documented header at lines 1-21 | 1,052 |
| `resources/clangovernments.xml` | 11 `CLANTYPE` government definitions | 1,626 |
| `com/planet_ink/coffee_mud/Libraries/Achievements.java` | Achievement engine: file parser, per-event trackers, event dispatch, reward grant, tattoo and announcement | 10,219 |
| `Libraries/interfaces/AchievementLibrary.java` | `Event` enum (47 types, lines 71-120), `AwardType` (369), `AchievementFlag` (218), `Achievement` and `Tracker` interfaces | 897 |
| `Libraries/AutoAwards.java` | Loads `awards.txt`, parses each rule into an `AutoProperties`, attaches the per-character effect | 894 |
| `Abilities/Misc/AutoAwards.java` | Per-character effect that re-evaluates date-masked awards on each in-game hour change | 568 |
| `Libraries/Clans.java` | Parses and writes `clangovernments.xml` (`parseGovernmentXML`, line 1204); government catalogue | 2,443 |
| `Common/DefaultClanGovernment.java` | Government object: XP formula (line 114), voting duration and quorum, titles, conquest flags | 1,547 |
| `Common/DefaultClan.java` | Clan state: XP and level (`setExp`, line 596), votes (2470), auto-promotion (2044), decline and purge (2026-2028), member class grant (839) | 3,038 |
| `Common/interfaces/Clan.java` | `Function` enum (the 29 powers), `Authority` (1190), `AutoPromoteFlag` (1201) | 1,317 |
| `Abilities/Properties/Prop_Adjuster.java`, `Prop_HaveAdjuster.java` | The stat-adjusting property used by almost every awards rule | 98, 1,535 |
| `Abilities/Properties/Prop_ModExperience.java` | Property that rewrites XP-change messages in `okMessage` | 108 |
| `Libraries/MUDZapper.java` | Zapper mask language, including date-mask evaluation (`isDateMatch`, line 3643) | 10,244 |
| `Common/DefaultTimeClock.java` | The calendar: months, days per month, week and year derivation | 1,378 |
| `Common/DefaultTattoo.java` | Tattoo marks, optionally expiring after a number of ticks | 193 |

## How it works

Conventions: Java paths below are relative to `com/planet_ink/coffee_mud/`. Data paths are relative to the CoffeeMUD folder.

### 1. The achievement file format

- Each entry is one line: `TATTOO=EVENT="..." DISPLAY="..." TITLE="..." REWARDS="..." PLAYERMASK="..." <event parameters>`. Sections switch the agent: `[PLAYER]`, `[ACCOUNT]`, `[CLAN]` (the `Agent` enum, `Common/interfaces/AccountStats.java:350`).
- Common parameters, listed in `BASE_ACHIEVEMENT_PARAMETERS` (`AchievementLibrary.java:59-62`): `EVENT`, `DISPLAY`, `TITLE`, `REWARDS`, `VISIBLEMASK`, `DURATION`, `PLAYERMASK`, `FLAGS`. `DISPLAY` is the player-facing name. `VISIBLEMASK` and `PLAYERMASK` are zapper masks (who can see it, who can progress it). `FLAGS=REMORT` makes the achievement reset on remort (153 stock entries use it).
- The tattoo name is the text before the first `=`, uppercased. It may not begin with a digit (`Achievements.java:958-961`). A duplicate is rejected when added (`Achievements.java:8105`). The stock file has 1,235 unique names and none is reused across sections.
- Only 26 of the 47 event types appear in active stock entries (`AchievementLibrary.java:71-120` lists all 47). Two commented-out lines use names that are not in the enum, `PLAYERACHIEVER` (`achievements.ini:1624`) and `CLANKILLER` (`achievements.ini:1695`). If re-enabled they would fail with "Blank or unknown achievement type" (`Achievements.java:964-966`).

### 2. Loading and building trackers

- `reloadAchievements` (`Achievements.java:9851`) clears the three agent lists and the event map, then reads `achievements.ini` and any overflow files `achievements.ini.2` through `.98` (lines 9862-9869). A `[SECTION]` line changes the agent. Any other line goes to `evaluateAchievement(agent, row, true)`. Unknown section names are logged (line 9889). After loading, every online player is evaluated against the new definitions (around lines 9899-9915).
- `evaluateAchievement` (`Achievements.java:947`) runs in order: skip comments and blank lines (949-950); take the tattoo name (952-961); resolve `EVENT` against the enum (964-966); read the common parameters, flags and masks (966-989); build the reward list from `TITLE` and `REWARDS` (991-1030); then switch on the event. The switch has one case per event, from `case KILLS` (1041) to `case CLANLEVELSGAINED` (7917).
- Each case returns an anonymous `Achievement` with three parts. `parseParms` validates the event's own parameters. `getTracker(oldVal)` returns a `Tracker` that holds a count. `testBump` decides whether a bump counts. `KILLS` (lines 1041-1203) is the template. Its `testBump` (line 1152 onward) counts a kill when the dead mob matches the zapper mask and, if one is given, the killer matches the player mask. `isAchieved` is `num >= 0 && count >= num`.
- Tracker counts are saved as strings. `getCountParms` writes the count, and `getTracker(oldVal)` rebuilds the tracker on load (`Common/DefaultPlayerStats.java:1475-1482`). Trackers for each player are held in `achievementers` (`DefaultPlayerStats.java:126`), with equivalents on accounts and clans (`Common/DefaultClan.java:2761`).

### 3. Event bumps, completion and announcements

1. Game code reports an event with `CMLib.achievements().possiblyBumpAchievement(mob, Event, bumpNum, parms...)` (`Achievements.java:8346`). About 116 call sites outside the engine do this. Examples: kills (`Libraries/MUDFight.java:2732`), deaths (`MUDFight.java:1524-1526`), level-ups (`Libraries/CoffeeLevels.java:1363-1365`), remorts (`Commands/Remort.java:445`), retirements (`Commands/Retire.java:97`), social use (`Common/DefaultSocial.java:503`), and crafting. Crafting is reported from 34 skill files, for example `Abilities/Common/Cooking.java:409`.
2. The bump is first broadcast to the room as a `TYP_ACHIEVE` message (`sendAchievementEventMsg`, line 8194). Then the method loops over `eventMap.get(E)` and dispatches by agent to `possiblyBumpPlayerAchievement` (line 8208) or to the account and clan equivalents.
3. For a character achievement (lines 8208-8225): if the character already has the tattoo, stop. Otherwise get the tracker, call `testBump`, and check `isAchieved`. If complete, send the `P` message and grant rewards. If not, send the `p` progress message.
4. Completion (`giveAwards`, `Achievements.java:9124`). A timed achievement (DURATION above zero) resets its tracker and adds a timed tattoo (lines 9129-9133). Otherwise it adds a permanent tattoo. The ACHIEVEMENTS channel receives "`<name>` has completed the '`<display>`' `<agent>` achievement!" (line 9151), and the player is told "You have completed the '`<display>`' `<agent>` achievement!" (line 9178). Then the rewards are applied.
5. Tattoos are the only durable "done" flag. `DefaultTattoo.java:67` and `:80` set expiry as `now + ticks * CMProps.getTickMillis()`. The one stock timed achievement is `ACH_INSTEXPDO` with `DURATION=150` (`achievements.ini:660`), which is 600 seconds at the default `TICKTIME=4000` (`coffeemud.ini:2151`).
6. Some achievements are polled rather than bumped. `evaluatePlayerAchievements` and `evaluateAccountAchievements` run from the player thread for each in-game player (`Libraries/CMPlayers.java:3538-3539`). `evaluateClanAchievements` runs from the clan tick (`Libraries/Clans.java:2388`). The achievements command re-polls before listing (`Commands/Achievements.java:152-154`). The header says some events "are updated every mud-hour" for everyone online (`achievements.ini:355-356`). I did not verify which events are polled, or how each polled `isAchieved` reads live values.

### 4. Rewards

`parseAwards` (`Achievements.java:89`) reads `REWARDS` as a list. Each award is a number followed by words that choose the kind:

- `XP`, `EXP` or `EXPERIENCE`: experience (line 109). `CLANXP`: clan experience (line 145). `QP` or `QUEST...`: quest points (line 181). `NOPURGE`: protection from purge (line 217).
- `TATTOO(ID=... DESC=... ACCOUNT=...)`: a tattoo (lines 253-325). `ITEM(ID="...")` and `MOB(ID="...")`: copies from the global catalogue (line 327).
- An ability ID with optional `(AUTOGAIN=, MASK=, PARMS=, PREREQS=)`, granted at a level (about lines 425-482).
- An expertise ID followed by its level (about lines 482-737).
- Any stat name, with a leading `ACCOUNT` for account stats (lines 737-880). The `STAT` case applies whatever name `CMLib.coffeeMaker().getAnyGenStat` accepts (lines 8568-8600). The header documents `1 PRACTICES 1 TRAINS` (`achievements.ini:52`), and 77 PLAYER entries use `1 TRAINS`.
- A currency name, matched by `CMLib.english().matchAnyCurrencySet` (lines 885-915).

`TITLE` is a separate award. For characters, `*` becomes the character's name and the title is added to the title list (lines 8640-8653). For clan-derived titles, `@1` becomes the government name and `@2` the clan ID (line 8647). Who receives the rewards depends on the agent:

- Character achievements: the character.
- Account achievements: each character created or remorted on that account, once, guarded by the tattoo check. Loading happens in `CharCreation.java:3296` and `:4158`, `Commands/Remort.java:645` and `:702`, and `Abilities/Properties/Prop_ReRollStats.java:114`. This matches the header at `achievements.ini:29-31`.
- Clan achievements: the clan itself gets clan XP, clan currency, tattoos, and a clan announcement (`giveAwards` for clans, about lines 8724-8840; the clan `TITLE` case at 8820 is "awarded elsewhere"). Members get the character-level rewards only if their clan role has `CLAN_BENEFITS` (`loadClanAchievements`, `Achievements.java:10054`). When that authority is lost, `removeClanAchievementAwards` reverses the grants (`Achievements.java:8850`, called from `Common/DefaultClan.java:862`).

### 5. The awards file and AutoAwards

Line format (`awards.txt:1-21`): `[character zapper mask] :: [date zapper mask] :: Property(args) [Property(args) ...]`. The stock file has 1,027 rules from line 23 to 1052.

- Parsing. `getAllAutoAwards` (`Libraries/AutoAwards.java:626-740`) reads every extended copy of the file, skips `#` lines, splits each line on `::` (lines 649-657), and reads property tokens with an argument state machine. `addProp` (lines 607-624) resolves each ID as an ability, then as a behaviour, and logs and drops unknown IDs.
- Attachment. `giveAutoProperties` (`Libraries/AutoAwards.java:745-768`) attaches a hidden `AutoAwards` effect. Players get it in `bringToLife` with reset (`MOBS/StdMOB.java:1373-1374`). NPCs get it when they are attacked or start following (`StdMOB.java:3499-3501`, `:3637`).
- Evaluation. The effect's `tick` (`Abilities/Misc/AutoAwards.java:193`) re-checks only when the in-game hour changes (line 231) or when the awards file hash changes (lines 202-209). `assignMyEntries` (line 380) keeps the rules whose character mask matches, grouped by time period. `gatherTimelyEntries` (line 116) then keeps those whose date mask matches the character's home clock, using `maskCheckDateEntries`.
- Application. `applyAward` (line 155) sets the property text on a new `Prop_*` or behaviour, makes the mob its affected object, and forces proficiency 100. It flips `+` and `-` when the reverse flag is set (the reverse chant, below). Stats are then recovered (`recoverPhyStats` at line 275, and the char-stat and max-state recovers after it).
- Reset. The `RESET` stat (`Abilities/Misc/AutoAwards.java:428-435`) clears the cache and reassigns entries. `giveAutoProperties` sets it when called with reset (`Libraries/AutoAwards.java:766`), and players reach that call through `bringToLife` (`MOBS/StdMOB.java:1374`).

Date masks use the zapper language (`Libraries/MUDZapper.java`). A term with a `-` prefix must match, and one with a `+` prefix must not (`maskCheckDateEntries`, lines 5924-5960). `+N of M` means "value modulo M equals N" (`isDateMatch`, lines 3643-3656; parsed at lines 5315-5327). The date keys are `MONTH`, `WEEK` (week within the month), `WEEKOFYEAR`, `DAYOFYEAR`, `YEAR`, `HOUR`, `DAY` and `SEASON`. `BIRTHMONTH` and similar keys belong in the character column.

The calendar matters. `DefaultTimeClock` derives `weekOfYear` only when day-of-week names exist (`Common/DefaultTimeClock.java:500-503`). The shipped `coffeemud.ini` sets `DAYSINMONTH=24` (line 1109), seven months (line 1115) and an empty `DAYSINWEEK` (line 1121). These keys apply to the default clock only, so that clock's year is 168 days, and its week-of-year is not computed unless day names are set. The default month starts at 1 (`DefaultTimeClock.java:90`), and day-of-year is month times days-per-month plus the day when the month is above zero (lines 496-497).

Two more pieces use the same machinery:

- Druid fortune chants act on a target's `AutoAwards` effect through its stats. Steal Fortune copies the target's award list to the caster and marks the target with a suppressor (`Abilities/Druid/Chant_StealFortune.java:161-170`). Suppress Fortune sets `SUPPRESSOR` on the target (`Chant_SuppressFortune.java:140`). Reverse Fortune sets `REVERSER` on the target, which makes `applyAward` flip the signs of the awards (`Chant_ReverseFortune.java:141`; `Abilities/Misc/AutoAwards.java:155-170`). Extend Fortune sets `HOLDER` on the target (`Chant_ExtendFortune.java:140`); its full effect is unverified.
- The thief skill Runecasting (`Abilities/Thief/Thief_Runecasting.java`) reads the subject's current award set and calls `CMLib.masking().dateMaskToNextTimeClock` (lines 460 and 667) to find when each rule next applies. It is a forecast of the target's coming blessings.

### 6. Clan governments

- Loading. `parseGovernmentXML` (`Libraries/Clans.java:1204`) reads each `CLANTYPE` for `NAME`, `TYPEID`, `CATEGORY`, `VOTING` (`MAXDAYS`, `QUORUMPCT`), `POSITIONS`, `AUTOPOSITION`, `ACCEPTPOSITION`, `REQUIREDMASK`, `ENTRYSCRIPT`, `EXITSCRIPT`, `AUTOPROMOTEBY`, `PUBLIC`, `FAMILYONLY`, `RIVALROUS`, `XPPERLEVELFORMULA`, `OVERRIDEMINMEMBERS`, `CONQUEST`, `TITLES`, `ABILITIES`, `EFFECTS` and `MISCVARS`. A bad `TYPEID` makes the parser return no governments (`Clans.java:1497`), and the catalogue then falls back to a generated sample government (`Clans.java:987-992`).
- The file is not strict XML. It has 834 unquoted attribute values such as `TYPEID=0`, `LEVEL=4` and `ROLEID=6`. Python's `ElementTree` rejects it, but the game's `CMLib.xml().parseAllXML` accepts it. The writer uses the same style (`Clans.java:1029`, `:1070`, `:1135`).
- Positions. `ROLEID` is the role index stored with each member (`DBUpdateClanMembership`). `RANK` is an authority order where the lowest number is the most senior (`Common/DefaultClan.java:1844-1849`). `MAX` is a count, or a percentage of the membership when it ends in `%`. The percentage is parsed at `Clans.java:1300` and applied in `getMostInRole` (`DefaultClan.java:1926-1939`), which has a minimum of 1. `INNERMASK` is a zapper mask a member must pass to hold the post (`DefaultClan.java:1296-1320`).
- Powers. The 29 `Function` values (`Common/interfaces/Clan.java:1241`) are the powers. Each position lists the powers it has. Each `VOTING` block lists powers that need a vote. `VOTE_ASSIGN` makes promotions need a vote, and `VOTE_OTHER` makes other listed powers need one (`Clans.java:1258-1264`). Each power then has an `Authority` of `CAN_NOT_DO`, `CAN_DO` or `MUST_VOTE_ON` (`Clan.java:1190`).
- Votes (`DefaultClan.java:2470-2480`). Duration is `MAXDAYS` in in-game days, or 54 days if zero, converted to milliseconds through ticks per day and tick length. Quorum is `QUORUMPCT` of the voters, with a minimum of 2 (1 when there is only one voter). A vote closes when everyone has voted or time runs out, and fails if the quorum was not reached.
- Auto-promotion. `AUTOPROMOTEBY` takes `NONE`, `LEVEL`, `RANK`, `GOLD`, `XP`, `JOINDATE`, or an `_OVERWRITE` variant that moves existing members as well as filling vacancies (`Clan.java:1201`). `DefaultClan.java:2044` onward ranks members by the chosen flag and only counts members active within the overthrow window.
- Decline. `DAYSCLANDEATH` (2160 in `coffeemud.ini:1573`) and `DAYSCLANOVERTHROW` (952, line 1584) set how long a member can be absent before counting as lost and when leadership counts as vacant. Per the comment at `coffeemud.ini:1571`, 24 in-game days equal one real day, so these are roughly 90 and 40 real days. If the clan has too few active members, or no leaders, it is marked FADING; once it has been fading for more than 3 days it is deleted (`DefaultClan.java:2028`, `:2374-2390`).
- Dues. `clanDues` (`Libraries/Clans.java:1760`) runs once per in-game year and draws dues from member bank accounts. A clan with dues but no preferred bank is warned.
- Perks. `EFFECTS` entries apply a property (`Prop_Adjuster`, `Prop_ModExperience`, `Prop_ModFaction`) at a clan level, and may be restricted to listed roles (`Common/DefaultClan.java:1262-1275`). `ABILITIES` entries are stored as clan stats (`Clans.java:1464-1479`).
- Class clans. `CATEGORY="CLASSCLAN"` (PROFESSION) makes members take the clan's class when their role has `CLAN_BENEFITS` (`Common/DefaultClan.java:839-855`, `updateClanPrivileges`). The clan class is held in the `CLANCHARCLASS` stat (`DefaultClan.java:2849`), which appears in the mask `-CLASS +Apprentice +%[CLANCHARCLASS]%` (`clangovernments.xml:1180`).
- Conquest. `CONQUEST` sets `ENABLED`, `ITEMLOYALTY` and `DEITYBASIS` (`Clans.java:1448`).

### 7. Clan XP curve

The default clan XP formula is `(500 * @x1) + (1000 * @x1 * @x1 * @x1)` (`Common/DefaultClanGovernment.java:114`), where `@x1` is the current clan level. `setExp` (`DefaultClan.java:596`) raises the level while cumulative XP exceeds the threshold for the current level. Each government may override it with `XPPERLEVELFORMULA`. All 11 stock types leave it empty.

| Current level L | Cumulative XP needed to pass L (500L + 1000L^3) |
|---|---|
| 1 | 1,500 |
| 2 | 9,000 |
| 3 | 28,500 |
| 4 | 66,000 |
| 5 | 127,500 |
| 10 | 1,005,000 |

## Content worth mining

### Achievement families (active stock entries)

| Family | Entries | Examples (tattoo: display, reward or title; line) |
|---|---|---|
| Class mastery, character | 142 (the 30-level tier for 48 classes; the 60- and 90-level tiers for 47 each) | `ACH_MASTERAbjurer`: "Gain 30 levels in Abjurer", title "*, Master Abjurer" (366) |
| Class try-out, account | 48 | `ACCT_ACH_TRIED<Class>`: "Try out the <class> for 15 levels" (881) |
| Remort "past life" ladder, account | 470 (47 classes x 10) | Past Life I-VI at 30 class levels; Savant I-II at 60; Prodigal I-II at 90. Rewards: `5 MANA`, an expertise such as `3 REDUCEDABJURATION1`, or an ability such as `5 Spell_RepairingAura` (929-938) |
| Remort, clan | 47 | `CLACH_REM_<Class>1`: 5 remorts by members, "Prodigal <Class> Clan" (1634) |
| Crafting | 44 character, 21 clan | `ACH_FIRSTCRAFTOR`: first craft, 50 XP (542). `ACH_ALLCRAFTOR`: 3,000 items, title "*, Master Craftsman" (543) |
| Skill use | 22 character, 13 clan | `ACH_DIVINATIONCASTER`: 30,000 divination casts, title "*, Master of Divination" (581) |
| Proficiency | 184 | `ACH_MAXSKILL1`: one skill to 100 percent (689). Domain variants reward `1 TRAINS` and carry `FLAGS="REMORT"` (723) |
| Combat kills | 11 character, 3 clan | `ACH_DRAGONSLAYR`: 200 adult dragons, title "*, Dragon Slayer", mask `-RACE +Dragon -LEVEL +>20` (507) |
| Deaths | 12 | `ACH_LOWDEATH1`: 10 deaths below level 31, title "*, Newbie Dwarfin' Award Recipient" (647) |
| Exploration | 5 character | `ACH_EXPLORER1-5`: 10, 25, 50, 75 and 95 percent of the world, with titles up to "*, Master Explorer" (523-527). The clan tiers are commented out (1567-1571) |
| Stat values | 12 | `ACH_SUPERSTRENGTH`: 25 base Strength, title "*, the Powerful" (529). `ACH_NOSTRENGTH`: 3 base Strength, title "*, the Weakling" (530) |
| Quests | 7 character, 6 account, 7 clan | `ACH_ALLQUESTOR1-3`: 1, 10, 25 quests (606-608); clan variants pay clan XP (1614-1615) |
| Triumph instances | 15 | `ACH_INSTEXPDO`: complete a Triumph, reward `1 VICTORY`, timed 150 ticks (660). Count and value-expression variants follow |
| Births and families | 10 character (`RACEBIRTH` 5, `PLAYERBORNPARENT` 4, `PLAYERBORN` 1), 1 account, 3 clan | `ACH_PLAYERBORN` and `ACH_MUDDADDY`/`ACH_MUDMOMMY` (630-632); `ACH_BREEDING1` (676); clan parent and birth counts (1630-1632) |
| Sea combat | 6 | Sinking 1, 10, 100 enemy ships (682-683) |
| Meta achievements | 1 character, 8 account, 1 clan | `ACH_1ACHIEVER` requires named achievements (614). Account and clan variants (1503-1504, 1622) |
| Clan politics and territory | 12 declarations, 2 conquest, 5 conquest points, 1 property, 5 membership | `CLACH_DECLARE_HOSTILE1-2` (1682-1683); `CLACH_CONQUEST1-2` (1697-1698); membership counts (1708-1709) |
| Social use | 1 clan | One social-use achievement (`CLACH_SOCIALWIGGY`, line 1612) |

Only 26 event types are used: `SKILLPROF` (184), `CLASSLEVELSGAINED` (142 character, 48 account, 45 clan), `REMORT` (470 account, 47 clan), `CRAFTING` (44 character, 21 clan), `SKILLUSE`, `QUESTOR`, `KILLS`, `DEATHS`, `EXPLORE`, `STATVALUE`, `FACTIONS`, `INSTANCEEXPIRE`, `SHIPSSUNK`, `RACEBIRTH`, `PLAYERBORN`, `PLAYERBORNPARENT`, `EFFECTSHAD`, `MENDER`, `ACHIEVER`, `RETIRE`, `CLANDECLARE`, `CONQUESTPOINTS`, `CONQUEREDAREAS`, `CLANMEMBERS`, `CLANPROPERTY` and `SOCIALUSE`. Of the 1,235 entries, 720 have rewards, 328 have a title, and 153 carry `FLAGS=REMORT`.

### Calendar and character awards (`awards.txt`)

| Pattern | Lines | Effect | Example (line) |
|---|---|---|---|
| Saving throws by week (and birth month) | 592 rules use `WEEKOFYEAR` | One class, or a birth-month group, gets +10 or -10 on one save type in one week; each save type runs as a rotating block | `-CLASS +Artisan :: -WEEKOFYEAR +1 :: Prop_Adjuster(SAVEFIR+10)` (38) |
| Race-category combat by day | 360 `DAYOFYEAR` rules | ATTACK +110 (with `MULTIPLYPH=TRUE`) or DAMAGE changes for one race category on one day | `-RaceCat +Amphibian :: -DAYOFYEAR +1 :: Prop_Adjuster(ATTACK+110 MULTIPLYPH=TRUE)` (558) |
| Race category damage | DAMAGE rules | Small damage changes on one race category on one day | `-RaceCat +Worm :: -DAYOFYEAR +6 :: Prop_Adjuster(DAMAGE-2)` (917) |
| Birth month | 64 uses of `BIRTHMONTH` | Save bonus or penalty for characters born in a given month, combined with a week | `-BIRTHMONTH +1 :: -WEEKOFYEAR +1 :: Prop_Adjuster(SAVETRA+10)` (918) |
| Base class month | 7 | `Prop_ModExperience(+50% SELF)` in a month by class | `-BASECLASS +Fighter :: -MONTH +1` (23) |
| Whole-population month | 1 | `Prop_ModExperience(+20% SELF)` in a month of a year cycle | `-MOB +PLAYER :: -MONTH +0 -YEAR "+0 of 20"` (30) |
| Alignment cycles | 24 | XP +5 to +10 percent, and weapon or magic crit chance and damage, on week and month cycles | `-ALIGNMENT +Good :: -MONTH "+0 of 6" :: Prop_ModExperience(+10% SELF)` (1029) |

Across the file, `Prop_Adjuster` appears in 1,013 rules with 34 distinct argument strings. Saving-throw keys appear in 638 rules, `ATTACK` in 180, `DAMAGE` in 183, `MULTIPLYPH=TRUE` in 183, and crit keys in 12. The saving-throw keys are three-letter forms of the `STAT_SAVE_*` codes (`Common/interfaces/CharStats.java:62-90`): `SAVEFIR`, `SAVEACI`, `SAVEELE`, `SAVEGAS`, `SAVEUND`, `SAVEDIS`, `SAVEPOI`, `SAVEPAR`, `SAVETRA`, `SAVEWAT`, `SAVECOL`, `SAVEMIN`, `SAVEGEN` and `SAVEJUS`.

### Clan governments (`clangovernments.xml`)

| ID | Name (short description) | Lines | Positions, top to bottom (MAX) | Voting (days, quorum %) | Promotion | Notes |
|---|---|---|---|---|---|---|
| 0 | GANG (ruled by a boss who assigns underlings) | 2-158 | BOSS (1), LIEUTENANT, TREASURER (1), ENCHANTER (3), STAFF, MEMBER, APPLICANT | none | RANK | Boss holds nearly all powers; lieutenants can order underlings |
| 1 | GUILD (several bosses who assign underlings) | 159-326 | GUILDMASTER (5), SECRETARY, TREASURER (1), ENCHANTER (3), CHIEF, MEMBER, APPLICANT | 24, 100 | RANK | Six powers need a vote (ASSIGN, SET_DONATE, DECLARE, SET_HOME, PREMISE, TAX) |
| 2 | UNION (elected leaders and staff) | 327-484 | SENATOR (5), SECRETARY (1), TREASURER (1), ENCHANTER (3), SHERIFF, CITIZEN, APPLICANT | 48, 66 | RANK | Nine voted powers, including ACCEPT, REJECT and EXILE |
| 3 | FELLOWSHIP (all decisions through a vote) | 485-649 | FIRST CITIZEN (1), MANAGER, TREASURER, ENCHANTER, SOLDIER, CITIZEN, APPLICANT | 100, 33 | RANK | Nine voted powers; the most democratic type |
| 4 | THEOCRACY (clerics rule, conquest by conversion) | 650-815 | HIGH PRIEST (1), PRIEST, TREASURER (1), ENCHANTER (3), CRUSADER, BELIEVER, APPLICANT | none | LEVEL_OVERWRITE | Every post needs a member who worships the clan deity (`INNERMASK`, `REQUIREDMASK` at line 762); `DEITYBASIS` true |
| 5 | FAMILY (patron or matron rules; family only) | 816-1031 | PATRON (1), MATRON (1), MISTRESS, MISTER, NEUTER, COUSIN, CHILD, BLACKSHEEP | none | NONE | `FAMILYONLY` true; new members join as CHILD; position names and gender masks encode family roles |
| 6 | ANARCHY (anyone can join and do anything) | 1032-1116 | FREEMAN (unlimited) | none | NONE | One position with 20 powers and no limit; the only type with a single position |
| 7 | PROFESSION (class clan, `CATEGORY="CLASSCLAN"`) | 1117-1199 | OFFICER, MEMBER, APPLICANT | 10, 66 | NONE | Joining needs the Apprentice class; members take the clan's class; conquest disabled; not public; no abilities or effects |
| 8 | CO-OP (no boss can tax or order underlings) | 1200-1325 | HOUSE MOTHER (1, female mask), HOUSE FATHER (1, male mask), MEMBER, APPLICANT | none | RANK | `MISCVARS` sets `TAX=10%` (line 1272); conquest disabled |
| 9 | MERITOCRACY (ruled by the biggest XP donors) | 1326-1480 | HIGH ENTREPRENEUR (2%), ENTREPRENEUR (5%), DILIGENT WORKER (10%), HARD WORKER (50%), WORKER, APPLICANT | none | XP_OVERWRITE | Posts are shares of membership; `TAX=0%`; grants `Spell_ClanExperience` at level 0 |
| 10 | PLUTOCRACY (ruled by the biggest money donors) | 1481-1625 | TYCOON (1%), MOGUL (3%), LANDLORD (5%), TENNANT (50%), SERF, APPLICANT | none | GOLD_OVERWRITE | Posts are shares of membership; entry is as a SERF |

Nine types share the same 17-entry `EFFECTS` list (Prop_Adjuster at clan levels 3-20, Prop_ModExperience at 21-22, Prop_ModFaction at 23-25). ANARCHY has the same list but writes stat names in full (`WISDOM+1`, line 1099), and PROFESSION has no effects. Conquest is enabled everywhere except PROFESSION and CO-OP, and THEOCRACY turns item loyalty off and deity basis on. `ISDEFAULT` is not set anywhere, so the default government is the first one (`Libraries/Clans.java:1002-1010`), which is GANG. The file has 63 positions and 152 `ABILITY` entries.

## Notable design ideas

- Tattoos as the only "done" state. A completed achievement is a set membership, so a data edit can re-run every check without granting twice (`findTattoo` guards each grant), and an expiring achievement is a timed membership.
- One event vocabulary, many thresholds. About 116 call sites report events, and the file chooses the thresholds, masks and rewards. Gameplay code never names an achievement.
- One filter language everywhere. Zapper masks select who, what, where and when for kills, areas, dates and clan declarations. This keeps the content vocabulary small.
- Scope reuse. The same engine runs for characters, accounts and clans. Rewards flow downward: account rewards to each new or remorted character, clan rewards to members whose role grants `CLAN_BENEFITS`.
- Remort as prestige. Past Life, Savant and Prodigal tiers at 30, 60 and 90 class levels turn one class into a ladder that extends play.
- Calendar blessings. Rules rotate weekly, daily or monthly, and a birth-month bonus makes a character's birthday a mechanical fact. Effects are only re-evaluated on an hour change, so the cost is small.
- Divination on a schedule. The fortune chants steal, suppress or reverse a character's calendar effects, and the Runecasting skill forecasts the next ones using `dateMaskToNextTimeClock`.
- Governments as data. A government is a rank table, a power set per rank, vote gating, a promotion rule and perks, so a new political system is a file entry.
- Share-based posts. Using `MAX` as a percentage of membership (2%, 5%, 10%, 50%) makes posts scale with population.
- Economy-driven succession. PLUTOCRACY and MERITOCRACY let gold or XP donated decide rank, so the economy changes who rules.

## Problems and cruft

1. Not strict XML. `clangovernments.xml` has 834 unquoted attributes. The game parses it, but standard tools reject it, and the writer keeps the same style (`Clans.java:1029`, `:1070`, `:1135`). Any Python port must pre-quote or use a lenient parser.
2. Duplicate power. `CO-OP` `MEMBER` lists `CHANNEL` twice (`clangovernments.xml:1255` and `:1257`). The effect is idempotent, so this is harmless but sloppy.
3. Dead achievement data. Commented-out entries use event names that do not exist (`achievements.ini:1624` `PLAYERACHIEVER`, `:1695` `CLANKILLER`). Others are disabled clan explore tiers (`:1567-1571`) and clan faction tiers (`:1626-1627`).
4. Two placeholder syntaxes. Achievement titles use `@1` and `@2` (`Achievements.java:8647`), while government titles use `@x1` and `@x2` through `CMStrings.replaceVariables` (`Common/DefaultClan.java:892-894`). Both work on their own path, but a builder can mix them up.
5. Calendar-dependent rules. The 592 `WEEKOFYEAR` rules need day-of-week names to be configured (`DefaultTimeClock.java:500-503`), and `coffeemud.ini:1121` ships that setting empty. Twelve `DAYOFYEAR` rules name values above 168, the length of the default year, with values up to 180. Their meaning depends on how the clock counts days (`DefaultTimeClock.java:496-497`, with the default month at 1, line 90). Whether area clocks override the shipped calendar is unverified, and so is whether these rules ever fire.
6. Character masks can go stale mid-session. The per-character rule list is cached and is rebuilt only when the file hash changes or on `RESET` (`Abilities/Misc/AutoAwards.java:199-209`, `:428-435`). A mid-session change of class or race may not re-run the character mask until the next reset. Unverified at runtime.
7. Mixed stat spellings. `ANARCHY` writes `WISDOM+1` and similar where other types use `WIS+1`. The loose parser accepts both by prefix match (`core/CMParms.java`, `parseLooseParms`), so this is style only.
8. Multiplayer-only machinery. Clan death and overthrow timers, dues, purge, account propagation, and the external achievement feed (`Libraries/intermud/GrapevineClient.java:991` and `:1027` build a title, points and progress record for each achievement) have no single-player meaning.
9. Unused timing feature. `DURATION` is used by one stock entry (`ACH_INSTEXPDO`), so the expiry path is barely exercised.

## Takeaways for a Python SUD

- Achievements as data. Load a TOML or YAML table with `id`, `event`, `count`, optional `where` predicate, `title` and `rewards`. Validate every line at load time, as `evaluateAchievement` does, and log the bad ones (`Achievements.java:960-966`). Keep the tattoo set in the save.
- Keep a short event list. For one player you need about 15 events: kill, death, level-up, class level, craft, skill use, proficiency, explore percent, quest, stat threshold, item get, room enter, time played, remort or prestige, and a meta "achiever" that waits on other ids. Drop CLAN*, ACCOUNT, BIRTHS, SHIPSSUNK, SOCIALUSE and intermud.
- Predicates at load time. Compile any mask strings to Python callables once, as `getPreCompiledMask` does, rather than parsing per event.
- Typed rewards. Use small classes (`XP(50)`, `Stat("strength", 2)`, `Ability("fireball", level=17)`, `Title("*, Dragon Slayer")`). This replaces the reward grammar with checked objects.
- A bump function. A sketch:

```python
@dataclass(frozen=True)
class Achievement:
    id: str
    event: str
    count: int
    title: str = ""
    rewards: tuple = ()
    where: Callable[[dict], bool] = lambda ctx: True

def bump(state, by_event, event, ctx, amount=1):
    for a in by_event.get(event, ()):
        if a.id in state.tattoos or not a.where(ctx):
            continue
        n = state.counters[a.id] = state.counters.get(a.id, 0) + amount
        if n >= a.count:
            state.tattoos.add(a.id)
            for r in a.rewards:
                apply_reward(state, r)
```

- One calendar object. Derive day of year, week of year, month and year in one class, and check every date mask against its length at load time. This avoids the stock `DAYOFYEAR` and `WEEKOFYEAR` mismatches.
- Calendar effects as data. Represent a rule as `{"who": {"class": "Artisan"}, "when": {"week_of_year": 1}, "effect": {"save_fire": 10}}`. Recompute the active set on hour or day change only, as `AutoAwards.tick` does (`Abilities/Misc/AutoAwards.java:231`), and apply the modifiers to a stat dict.
- XP curve. `def xp_for_next(level): return 500 * level + 1000 * level ** 3` reproduces the stock clan curve. For one player, a simpler table is probably enough.
- Governments as NPC institutions. Keep three or four types: a single ruler, a council that votes with quorum, a meritocracy ranked by XP contributed, and a plutocracy ranked by gold. Model each as `Institution(ranks=[Rank(title, share, powers)], vote=..., promotion=...)`. Use simulated NPC votes only when a council decision needs one.
- Succession by absence. The clan's inactivity overthrow (`DefaultClan.java:2026-2027`) becomes "if the ruler has not acted for N in-game days, the next rank takes over". That keeps a useful single-player mechanic without other humans.
- Drop the rest. Account and clan propagation, dues, purge, intermud and database persistence all go, along with the unquoted-XML problem.

## Coverage

- `resources/achievements.ini`: header (lines 1-363) read in full. The three sections were parsed by script for all 1,235 entries (counts by section and event, tattoo uniqueness, reward keyword frequencies, title and flag counts). About 25 representative lines printed by script (for example 366-368, 507-508, 529-530, 542-543, 614, 616-617, 660-661, 689-690, 881-882, 929-938, 1516-1517, 1562-1563, 1573-1574, 1597-1598, 1612, 1622, 1634, 1682-1683, 1697-1709).
- `resources/clangovernments.xml`: the GANG block (lines 2-158) read in full, and all 11 types parsed by script (positions, powers, voting, promotion, masks, abilities, effects, conquest flags, MISCVARS, the 29-power vocabulary check). Other type blocks not read by eye.
- `resources/awards.txt`: header (lines 1-21) read in full; all 1,027 rules analysed by script (mask keys, date-key combinations, property arguments, value ranges). About 40 representative lines printed with sed or script.
- Code read in part: `Libraries/Achievements.java` (89-175, 947-1060, 8180-8230, 8293-8400, 8497-8530, 8640-8700, 8820-8850, 9124-9180, 9730-9790, 9851-9915, 10016-10100), `Libraries/AutoAwards.java` (607-770), `Abilities/Misc/AutoAwards.java` (116-300, 380-440), `Abilities/Properties/Prop_Adjuster.java` (whole), `Prop_HaveAdjuster.java` (290-420), `Prop_ModExperience.java` (60-108), `Libraries/MUDZapper.java` (3643-3660, 5315-5330, 5771-5960), `Common/DefaultTimeClock.java` (90-106, 500-505), `Libraries/Clans.java` (900-1010, 1204-1300, 1440-1500, 1760-1800), `Common/DefaultClan.java` (596-615, 839-870, 1008-1030, 1262-1320, 1835-1850, 1926-1940, 2026-2050, 2470-2480, 2370-2420), `Common/DefaultClanGovernment.java` (114, 380-402), `Common/interfaces/Clan.java` (1190-1290), `Common/DefaultPlayerStats.java` (1771-1790), `Common/DefaultTattoo.java` (60-90), `coffeemud.ini` (key lines only).
- Grep only: event bump sites (`possiblyBumpAchievement`), the `CMLib.xml` loader, `Abilities/Druid` chant handling, `Abilities/Thief/Thief_Runecasting.java`, `Libraries/intermud/GrapevineClient.java`, `Commands/Achievements.java`, `Libraries/CharCreation.java`, `Commands/Remort.java`.
- Skipped: the bodies of the event cases from `CHARITY` (1204) through `CLANLEVELSGAINED` (7917), `WebMacros` editors, `Trophy` and `Commands/Achievements.java` display code.
