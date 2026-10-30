# Quests: the script format and engine

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b031` (Quests) · 4 files · 11190 lines of Java.

All paths are relative to the CoffeeMUD folder. Line numbers were read from the source. Nothing was run, so every behaviour claim below is "by reading" unless marked otherwise.

## Summary

A CoffeeMud quest is a small interpreted script. It is written as plain-text commands grouped into `STEP` blocks, and it selects or creates rooms, mobs, items and abilities, gives them behaviours, effects and scripts, and then reverses all of those changes when the quest stops. Quests run on their own tick, wait on a schedule (an interval, a real calendar date, or a mud calendar date), and can spawn copies of themselves for each step. The same engine runs holidays (four seasonal events kept in one file and editable in-game), QuestMaker (74 fill-in-the-blank templates that become quest scripts) and a "mystery" generator that picks one true culprit, motive, place, time and tool from candidate pools. For a single-player living-world game this is a complete, data-driven event system with a scheduler and an undo journal for world changes.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Common/DefaultQuest.java` | The quest object. Holds the schedule and state, parses the script (`parseQuestScript`, 1036), runs the lifecycle (`startQuestInternal` 4416, `stepQuest` 4858, `stopQuestInternal` 5093, `enterDormantState` 5136), handles timing (`tick` 5407, `startQuestOnTime` 5451, `resetWaitRemaining` 5150), and keeps the undo journal (`cleanQuestStep` 4565, `runtimeRegister*` 5524–5751). Inner classes: `PreservedQuestObject` 6657, `QuestState` 6731, `MysteryData` 7000, `JScriptQuest` 7079. | 7124 |
| `Libraries/Quests.java` | The manager, `CMLib.quests()` (ID "Quests", line 40). Keeps the quest cache, edits the holiday file (169–986), provides the step helpers (`getNextQuestScriptCommands` 1414, `parseQuestSteps` 1453), loads QuestMaker templates (`getQuestTemplate` 1504), runs the QuestMaker wizard (`questMakerCommandLine` 1836), and holds the per-variable validators (`getQuestCommandEval` 2193). | 2680 |
| `Common/interfaces/Quest.java` | The public contract and its vocabulary: `Spawn` enum (802), `QCODES` (826), `QOBJS` (835), `MYSTERY_QCODES` (842), `QuestScript` (865). | 870 |
| `Libraries/interfaces/QuestManager.java` | Manager interface, `HolidayData` (403), `QMCommand` template variable types (491), `QM_COMMAND_OPTIONAL` = 128 (482). | 516 |

Related files outside the bucket that the engine depends on: `core/database/QuestLoader.java` (persistence, 240 lines), `Abilities/Misc/QuestBound.java` (the back-pointer effect on quest objects), `Common/DefaultScriptingEngine.java` (per-quest scripts, and the `QVAR`/`MPENDQUEST`/`QUESTWINNER` functions).

## How it works

### Data model

`DefaultQuest` fields (`Common/DefaultQuest.java:53–87`):

- Identity: `name`, `author`, `displayName`, `questType`, `category`, `instructions`.
- Schedule: `duration` in ticks (default 450, with the comment "about 30 minutes", line 60), `minWait` (WAIT), `maxWait` (INTERVAL), `startDate` (DATE or MUDDAY), `ticksRemaining`, `waitRemaining`.
- Eligibility: `minPlayers`, `playerMask` (a Zapper mask), `runLevel`.
- Flags: `spawn` (default `NO`, line 73), `durable` (PERSISTANCE), `copy` (a spawned child).
- `questState` (line 74) is the parse state. It is replaced on every start (line 4433). It holds the current `area`, `room`, `mob`, `item`, `ability`, `envObject` (the "current object" that GIVE/TAKE act on), the groups (`mobGroup`, `itemGroup`, `roomGroup`, `abilityGroup`), imported `loadedMobs`/`loadedItems`/`loadedAbilities`, `mysteryData`, `addons` (the undo journal), `worldObjects` (objects in use), `vars` (free-form `SET` variables), `reselectable`, `errors`, `done`, `error`, `beQuiet`, `isOptional`, `autoStepAfterDuration`, `preserveState`, `lastLine`, `stepNumber` (`QuestState`, 6731–6769).

Winners live on the quest, not in the state: `winners` is a `CaselessTreeMap<Long>` (line 84) mapping name to time of last win.

The `SET` vocabulary is the `QCODES` enum (`Common/interfaces/Quest.java:826–832`): `CLASS, NAME, DURATION, WAIT, MINPLAYERS, PLAYERMASK, RUNLEVEL, DATE, MUDDAY, INTERVAL, SPAWNABLE, DISPLAY, INSTRUCTIONS, PERSISTANCE, AUTHOR, EXPIRATION, QUESTTYPE, CATEGORY`. `setStat` and `getStat` switch on these by index (`DefaultQuest.java:6422–6643`). Any other `SET name value` goes into `questState.vars` (line 6526).

### Script format

Source of truth: `parseLoadScripts` (`DefaultQuest.java:6088–6235`) and `parseQuestScript` (1036).

- Commands are separated by newlines or `;`. `\;` is an escaped semicolon (6189, 6231). A line starting with `#` is a comment (6220).
- `STEP` ends a step (1152–1179). `STEP BREAK` ends the script (sets `done`). `STEP BACK` loops to the start of the step. `STEP AUTO` advances automatically when the duration runs out (1167–1172, and 5428–5431).
- `<OPTION>` ... `</OPTION>` makes an alternative block. One alternative is picked at random per run (6222–6230, and `parseFinalQuestScript` 865–891).
- `LOAD=file arg...` includes another script file, with `$ARG1` style arguments (6094–6125, `modifyStringFromArgs` 6239–6288).
- An embedded XML section after the file boundary holds `<FILE><NAME>..</NAME><DATA>..</DATA></FILE>` entries. These become `internalFiles` (6129–6181). Templates use this to ship companion scripts and item XML inside the quest.
- `<SCRIPT>` ... `</SCRIPT>` is JavaScript, run in Rhino with a `JScriptQuest` scope (1071–1103, 7079–7123). It must be approved first ("<SCRIPT> not approved. Use MODIFY JSCRIPT to approve.", 1084, checked by `CMSecurity.isApprovedJScript`, `core/CMSecurity.java:1070`).

Top-level verbs in `parseQuestScript`: `OPTIONAL`, `QUIET`, `STEP`, `RESET`, `SET`, `IMPORT`, `LOAD=`, `LOAD`, `GIVE`, `TAKE`, `<SCRIPT>`. A bare stat name is a no-op here and is applied earlier by `setVars` (531–566). An unknown verb is an error (4353–4356).

`SET <object-kind>` is the selection verb. It chooses an object and makes it current. The sub-verbs are:

- `AREA`, `AREAGROUP` (1212–1272)
- `ROOM`, `ROOMGROUP`, `ROOMGROUPAROUND`, `LOCALE`, `LOCALEGROUP`, `LOCALEGROUPAROUND` (1855–2170)
- `MOB`, `MOBGROUP`, `MOBTYPE`, `PCMOB`, `PCMOBGROUP` (1274–1579, 2172–2370)
- `ITEM`, `ITEMGROUP`, `ITEMTYPE` (1582–1850, 2372–2465)
- `ABILITY`, `ABILITYGROUP` (1672–1737, 2467–2540)
- `AGENT`, `AGENTGROUP`, `FACTION`, `FACTIONGROUP` (2542–2747)
- `WHEREHAPPENED[GROUP]`, `WHEREAT[GROUP]`, `WHENHAPPENED[GROUP]`, `WHENAT[GROUP]` (2749–2962, 3073–3125)
- `MOTIVE[GROUP]`, `ACTION[GROUP]` (2964–3071)
- `TARGET[GROUP]`, `TOOL[GROUP]` (3127–3340)
- `PRESERVE n` (1852–1853), which keeps the object for `n` more steps.

`GIVE` (3791–4217) acts on the current object: `FOLLOWER`, `ITEM[S]`, `ABILITY`, `BEHAVIOR`, `STAT`, `SCRIPT`, `AFFECT`. `TAKE` (4219–4351) is the reverse for `ABILITY`, `BEHAVIOR` and `AFFECT`. `LOAD` (3518–3789) instantiates previously imported `MOB`, `ITEM` or `ABILITY` copies. `IMPORT` (3351–3507) reads them from embedded or resource XML.

Example from the holiday file (`resources/quests/holidays/holidays.quest:8–19`, paraphrased):

```
SET NAME drought
SET WAIT 800
SET INTERVAL 1
SET DURATION 900+(1?400)
QUIET
SET ROOM ANY MASK=-SEASON +SUMMER -WEATHER +DROUGHT
...
GIVE BEHAVIOR MUDCHAT +(...)...
GIVE STAT PRICEMASKS 20.0 -JAVACLASS +StdDrink ...
STEP BREAK
```

### Object selection

Selection is the core of the language. `SET <kind> <name>` resolves a name against the world, in this order:

1. `ANY`, `ALL`, a tagged object `#tag` (`CMLib.map().getTaggedObjects`; `parseSetAreaLine` 966–974 for areas and 1444–1459 for mobs), or a name.
2. For mobs, matching is a substring test on the class ID, race category, race name and class name (1317–1322). Matching is tiered by `sortSelect` (620–664): exact name, then name contains, then display text contains, then description contains. The best non-empty tier supplies the candidates.
3. `MASK=<zapper>` restricts candidates with a Zapper mask (`breakOutMaskString` 801–825; `CMLib.masking().maskCompile`, see `Libraries/MUDZapper.java`). Holiday masks look like `-SEASON +SUMMER -WEATHER +DROUGHT` (holidays.quest:13) and `+INT 3` (line 15).
4. `SET AREA select: <MQL>` runs a percolator query (`CMLib.percolator().doMQLSelectObjects`, 928–950). This is the only place the quest language uses the MQL query system.
5. The pick is random, `CMLib.dice().roll(1,n,-1)` (for example 1383, 2248, 2441).
6. `RESELECT` allows objects already used by another running quest. Without it, `filterOutThoseInUse` (827–862) removes them, using `Quests.objectInUse` (`Libraries/Quests.java:50–64`). Ability selection always reselects (1723, 2528).

Object expressions (`getObjectIfSpecified`, `DefaultQuest.java:6290–6374`) are the second selection language, used for `ARGn` and for stat references. Tokens are stat names (`QOBJS`, `Quest.java:835–839`: `LOADEDMOBS, LOADEDITEMS, LOADEDABILITIES, AREA, ROOM, MOBGROUP, ITEMGROUP, ROOMGROUP, ABILITYGROUP, ITEM, ENVOBJ, STUFF, MOB, ABILITY, STEPNUM`, and the mystery codes). They are combined with `+` (union), `-` (remove) and `#` (choose index). A list result is picked at random when a single object is needed. Stat references may end in `_ROOMID` or `_CLASS`, or take `#` indices, `#?` for random, and `#$` for a quoted list (`QuestState.getStat` 6784–6904, `DefaultQuest.getStat` 6546–6643).

Text substitution: `$NAME` becomes the object's `Name()`. `$_NAME` capitalizes. `$&NAME` removes an article. `$|NAME` joins with `|` (`modifyStringFromArgs`, 6239–6288).

### Undo journal and steps

Every change a step makes is recorded so it can be reversed when the step or quest ends.

- Objects in use go into `questState.worldObjects` as `PreservedQuestObject` (`runtimeRegisterObject`, 5567–5595). Each gets an `Abilities/Misc/QuestBound` effect whose text is the quest, so the engine can find it later (5588–5593).
- Changes to existing state are saved as an addon: `runtimeRegisterAbility` (5524), `runtimeRegisterEffect` (5598), `runtimeRegisterBehavior` (5650), `runtimeRegisterStat` (5697). For a stat the entry is `[entity, stat, oldValue, stat, oldValue]` (5718–5723). Removing a change restores the old value.
- `cleanQuestStep(preserveSkip)` (4565–4850) runs at each step boundary and at stop. It first ends per-mob scripts (4581–4593). It then ages preserved objects by `PRESERVE` (4614–4629). Unpreserved items marked `IS_UNSAVABLE` are destroyed (4637–4643); other items get their owner back (`restoreOwner`, 4645). Non-player mobs are destroyed or dismissed depending on whether they have a start room (4648–4676). Finally the addons are reverted (4681–4847).

`stepQuest` (4858) calls `cleanQuestStep(0)`, clears the duration to -1 (4877), re-applies `SET` variables from the new step (`setVars`, 4886), and parses from `lastLine`. A quest whose step ends with `STEP BREAK` and has a valid duration enters the running state (4911–4915). Otherwise it stops.

### Lifecycle and timing

Constants:

- The default tick is 4000 ms (`TIME_TICK = 4000`, `core/CMProps.java:681`, settable as `TICKTIME`, 2720).
- A mud hour is 600000 ms (`MILLIS_PER_MUDHOUR`, `CMProps.java:682`). At the default tick that is 150 ticks per mud hour, as the comment at `Libraries/Quests.java:1055` says. The comment is only correct at default settings.
- Default quest duration is 450 ticks, about 30 real minutes (`DefaultQuest.java:60`).
- Holiday defaults: `SET WAIT 900+(1?100)`, `SET DURATION 900`, `SET PERSISTANCE TRUE` (`Libraries/Quests.java:489–492`). That is about 60 real minutes of duration.

Time expressions go through `CMLib.time().parseTickExpression` (for example `DefaultQuest.java:6432–6439`). They accept numbers, operators, `?` for random ranges, and the unit words `seconds, minutes, hours, days, mudhours, muddays, mudweeks, mudmonths, mudyears` (`resources/quests/templates/auto_capture1.quest:7–10`, and the validator message at `Libraries/Quests.java:2673`).

The cycle, as implemented:

1. `setScript` parses the whole script's `SET` lines, including duration and wait (`DefaultQuest.java:487–512`, `setVars` 531–566).
2. `autostartup` (521–528) calls `resetWaitRemaining(0)`. If that returns true and the quest is not running, it starts the quest tick, `TICKID_QUEST` = 13 (`core/interfaces/Tickable.java:108`).
3. `resetWaitRemaining` (5150–5211) computes the countdown:
   - With `DATE`, ticks to the next real-life date at midnight (5191–5205).
   - With `MUDDAY`, ticks to the next mud date (5166–5188).
   - Otherwise `WAIT + d(INTERVAL)` ticks, minus time already elapsed (5209: `waitRemaining=(minWait+(CMLib.dice().roll(1,maxWait,0)))-...`).
   - If neither a date nor both WAIT and INTERVAL are set, the quest is not rescheduled (5159–5161). A quest with only `SET WAIT` and no `SET INTERVAL` is deleted after its first stop (5139–5143). All templates and holidays set `interval 1` to avoid this.
4. `tick` (5407–5449) does one of two things. If waiting, it calls `startQuestOnTime`. If running, it decrements `ticksRemaining` only when duration is above 0 (5424–5425), so duration 0 means run until stopped. At zero it either steps (`STEP AUTO`) or calls `stopQuest` (5426–5437).
5. `startQuestOnTime` (5451–5488) is blocked if another running quest has a run level between 0 and this one's (5456–5470). Otherwise it needs `minPlayers` matching local online sessions, unless duration is 0 (5474–5484).
6. `startQuestInternal` (4416–4544) reparses from the top. On error, a durable quest retries after 10 ticks (4493–4497). On success it sends `CMMsg.TYP_QUESTSTART` globally (4526) and calls `enterRunningState` (4546–4563). That sets `ticksRemaining` to the duration, or 1 for duration 0 (4551–4552).
7. Failed starts back off. `resetData` doubles the pause on each failure (5152–5157). This matches the interface comment at `Common/interfaces/Quest.java:268–272`.
8. `stopQuestInternal` (5093–5133) marks everything for cleanup, runs `cleanQuestStep(0)`, reloads the script for non-copies (5122–5123), sends `TYP_QUESTSTOP` (5124) and enters the dormant state (5136–5147). A copy is removed from the manager there (5139–5143).

Run level semantics match the interface text (`Quest.java:615–631`). `-1` never defers, and a higher number is weaker.

### Spawning

`SPAWNABLE` (`DefaultQuest.java:6476–6482`) selects a `Quest.Spawn` (`Quest.java:802–820`): `NO`, `TRUE`, `FIRST` (only the first step), `ALL` and `ALLONCE` (every step as its own copy), `ANY` and `ANYONCE` (one random step as a copy), and `ONCE` (first step, once). `startQuestInternal` (4435–4467) spawns children and leaves the parent dormant. `spawnQuest` (4362–4404) makes a copy with `setCopy(true)`, renames it `name#n` if needed (4368–4382), and reschedules it. The time already spent on a step is kept per script text in `stepEllapsedTimes` (4386–4392, map at line 83). Copies are deleted when they stop (`enterDormantState`, 5139).

### Player acceptance

A quest can be offered to a player through two functions in an attached script, `CAN_ACCEPT` and `DO_ACCEPT` (`ScriptingEngine`, called from `DefaultQuest.java:167–252`).

- `canAcceptQuest` refuses if the quest is not running or is stopping (198–199), if the mob fails the `playerMask` (202–204), or if the mob already has a script with this quest's name as its default quest (206–212). Otherwise it calls `CAN_ACCEPT` (214–231), and "cancel" or an empty answer means no.
- `acceptQuest` calls `DO_ACCEPT` (235–252).
- `GIVE SCRIPT` to a player runs the same two functions when the script defines them. Without `PLAYEROK` the give is silently skipped if `CAN_ACCEPT` refuses (4141–4156).

Callers: `Abilities/Properties/Prop_QuestGiver.java:188, 246, 265`, `Items/Basic/StdQuestBoard.java:105, 117, 325`, and `Abilities/Prayers/Prayer_PlanarPilgrimage.java:557`.

A template shows the pattern. `templates/auto_capture1.quest` defines `FUNCTION_PROG CAN_ACCEPT` at line 139, with a condition on `$MULTIPLE` and `QUESTWINNER`. It defines `DO_ACCEPT` at line 150, which calls `mpscript $n INDIVIDUAL SAVABLE ...` to attach the player's copy of the tracking script (line 152). Quest state is therefore per-player and held in the player's scripts (`getPlayerPersistentQuests`, `Libraries/Quests.java:1808–1832`).

### Winners and persistence

- `declareWinner(name)` (`DefaultQuest.java:5264–5296`) records a name and time in the main quest's `winners` map (`getMainQuestObject` 5243–5260). A leading "-" removes the winner. `wasWinner` and `whenLastWon` read it (5348–5365).
- Serialization is `name@time;` (`getWinnerStr` 5299–5310, `setWinners` 5313–5334).
- Persistence is in `core/database/QuestLoader.java`. The table `CMQUESTS` stores name, class, flags (the suspended bit), raw script text (`CMQSCRPT`) and winners (`CMQWINNS`). `DBRead` rebuilds each quest from its script (lines 44–100). `DBUpdateQuest` deletes and reinserts a row (109–124).
- Runtime state (step number, counters, timers) is not stored. On boot `MUD.java:1775–1779` adds the loaded quests and `autostartup` starts their waits from zero (inference from `resetWaitRemaining(0)` at 5209).

### Holidays

- The file `resources/quests/holidays/holidays.quest` (path from `Libraries/Quests.java:45`) holds a header step named "holidays" (lines 1–6, with `set spawnable allonce`, `set wait 130`, `set duration 1`) and one step per holiday.
- `getHolidayFile` (169–203) copies the bundled file into the writable area if needed (176–182) and recreates the "holidays" quest if it is missing (183–195).
- `getHolidayEntries` (207–318) turns each step into a `JournalEntry` for the calendar. The date comes from `DATE` or `MUDDAY` and the expiry from the duration (279–306).
- The editor (`modifyHoliday` 692–771, `alterHoliday` 774–986) reads the step into `HolidayData` (`getEncodedHolidayData` 591–690), prompts through `promptDuration` (988–1058), `genPricing` (1210–1276), `genMudChat` (1309–1411), `genBehaviors` (1060–1124) and `genProperties` (1126–1189), then rewrites the file. Messages include "Holiday modified." (767) and "Holiday deleted." (534).
- `createHoliday` and `getDefaultHoliData` (457–501) write a default step with a wait of 900 plus 1-to-100 ticks, a 900-tick duration, a persistent flag, a mob group, a MUDCHAT behaviour and a price factor.

Effects a holiday can apply: a `MUDCHAT` behaviour on matching mobs, a `MOOD` affect (`GIVE AFFECT MOOD`), and `PRICEMASKS` on shopkeepers. Price factors are displayed as percentages (`Libraries/Quests.java:1251`).

### QuestMaker

- Templates are `resources/quests/templates/*.quest`. Each file has the markers `#!QUESTMAKER_START_SCRIPT <title>`, `#!QUESTMAKER_PAGE <page title>` and `#$VAR=$TYPE=default` lines (example: `auto_capture1.quest:1, 5, 24, 115`). Comment lines around the variables are the help shown to the builder. Text after `QUESTMAKER_END_SCRIPT` is the quest script with `$VAR` placeholders.
- `getQuestTemplate` (1504–1660) parses these. Each variable row is stored as (type, name, default, value) (1584–1601). A type with bit 128 is optional (`QM_COMMAND_OPTIONAL`, `QuestManager.java:482`).
- `questMakerCommandLine` (1836–2190) shows the template list, walks each page, and prompts for each variable using the validator for its type (`getQuestCommandEval`, 2193–2676). The types are listed at `QuestManager.java:491–515`: `$TITLE, $LABEL, $EXPRESSION, $UNIQUE_QUEST_NAME, $CHOOSE, $ITEMXML, $STRING, $ROOMID, $AREA, $MOBXML, $NAME, $LONG_STRING, $MOBXML_ONEORMORE, $ITEMXML_ONEORMORE, $ITEMXML_ZEROORMORE, $ZAPPERMASK, $ABILITY, $MEFFECT, $EXISTING_QUEST_NAME, $HIDDEN, $FACTION, $TIMEEXPRESSION`.
- `$MOBXML` and `$ITEMXML` offer mobs and items in the room, or a new generic one, and open the Modify editor on it (`addXMLQuestMob` 1662–1732, `addXMLQuestItem` 1734–1805).
- Substitution is plain string replacement (2158–2164). `$#AUTHOR` becomes the creating player's name (2167).
- The result is confirmed ("Create the new quest: @x1 (y/N)?", 2169), saved to `quests/<name>.quest` (2172–2174), checked for a duration (2175), and added to the manager (2180–2181).

### Mystery quests

`Quest.java:842–854` defines the mystery codes: `FACTION, AGENT, ACTION, TARGET, MOTIVE, WHEREHAPPENED, WHEREAT, WHENHAPPENED, WHENAT, TOOL`, each with a `GROUP` form. `MysteryData` (`DefaultQuest.java:7000–7077`) holds one chosen value per category, plus the candidate lists.

A `GROUP` command draws a set of candidates and picks one as the true value. For example, `WHEREHAPPENEDGROUP` takes room groups from the selector, picks `n` distinct rooms and sets one as the true location (2749–2839). `MOTIVEGROUP` and `ACTIONGROUP` do the same over string lists, using `sizeDownTo` to cut the list (790–799, 2964–3035). The template `templates/normal_mystery1.quest` uses `set motivegroup $NUM_MOTIVES $MOTIVE_LIST` (line 176), `set toolgroup $NUM_TOOLS` (173) and `import items $QUEST_ID_tools.cmare` (171). The announcer mob is marked as the key player with `give stat keyplayer true` (167). `KEYPLAYER` sets the `KEY` on the `QuestBound` effect (4060–4065).

### Interactions with other systems

- Scripting: `GIVE SCRIPT` attaches a `DefaultScriptingEngine` (4134–4167). The template scripts use `QVAR` (`DefaultScriptingEngine.java:6271–6277`), `MPENDQUEST` (13206) and `QUESTWINNER` (`ScriptingEngine.java:525`).
- Abilities and behaviours: `GIVE AFFECT` and `GIVE BEHAVIOR` use `runtimeRegisterEffect` and `runtimeRegisterBehavior`. Holidays use `MudChat` and `Aggressive` behaviours (`genMudChat` 1309–1411, `genBehaviors` 1060–1124).
- Time: `CMLib.time().parseTickExpression` (6434 and elsewhere), `TimeClock.tickTock(hours)` (`Common/interfaces/TimeClock.java:214`, used at `DefaultQuest.java:2902, 2957`).
- Threads: `CMLib.threads().startTickDown(this,Tickable.TICKID_QUEST,1)` (4558).
- Statistics: `CMLib.coffeeTables().bump` on start, stop, success and time-out (`Libraries/Quests.java:148`; `DefaultQuest.java:4410, 4498, 5086, 5434`).
- Law: `CMLib.law().getLegalBehavior` for `SUPPRESSLAW` (4052).
- Messages: `CMLib.map().sendGlobalMessage` with `CMMsg.TYP_QUESTSTART=152` and `TYP_QUESTSTOP=153` (`Common/interfaces/CMMsg.java:1276, 1278`). `Behaviors/StockMarket.java:2041` handles starts, and its comment there says holiday-driven influences arrive as quest starts.
- XML: `IMPORT` uses `CMLib.coffeeMaker().addMOBsFromXML` and `addItemsFromXML` (3386, 3434) and `unpackAbilitiesFromXml` (3484).
- Commands: `CREATE QUEST` (`Commands/Create.java:2315–2345`), `QUESTWINS` (`Commands/Questwins.java`), and `MOTD` (277) use the library.

### Messages shown to players

Quest start and stop are not announced as text. Their visible effects are the spawned mobs and items, a `showHappens(CMMsg.MSG_OK_ACTION)` on the affected room (for example `DefaultQuest.java:1408, 2270`), and the MUDCHAT lines of holiday mobs. Builder-facing messages include "No duration, quest '...' not started." (4506), "No valid quest templates found in resources/quests/templates!" (`Libraries/Quests.java:1845`), "A quest of that name already exists.  Enter another." (2255), and "Quest names may only contain letters, digits, or _ -- no spaces or special characters." (2251).

## Content worth mining

### QuestMaker templates (74 files)

Every file is `resources/quests/templates/<family>_<theme><n>.quest`. The header title is the family and theme in words (for example "Auto Capture #1").

| Theme | auto | normal | competitive |
|---|---|---|---|
| capture | 4 | 4 | 3 |
| collect | 5 | 5 | 5 |
| delivery | 4 | 4 | 4 |
| dispel | 1 | 1 | 1 |
| escort | 2 | 2 | 2 |
| invasion | – | – | 2 |
| killer | 2 | 2 | 2 |
| mystery | – | 2 | 2 |
| protect | 1 | 2 | 2 |
| travel | 4 | 4 | 2 |
| **total** | **23** | **26** | **25** |

The family names are as the file names give them. The `auto_capture1` script checks `QUESTWINNER` and a `$MULTIPLE` variable (`PREV`, `YES`, else) before accepting a player (line 139 onward). So "competitive" templates control whether a player can repeat or must be a fresh winner. The exact meaning of "auto" beyond its naming was not traced.

`auto_capture1.quest` has five pages: Quest Name/Duration (line 5), Quest Criteria and Instructions with a `$ZAPPERMASK` (31), Captureable Mob or Mobs (47), Capturables Behavior (68) and Quest Completion (89). Durations are time expressions such as `$TIMEEXPRESSION=90 minutes` (lines 24 and 29). The other themes were not checked page by page.

### Holidays (4 plus a header)

`resources/quests/holidays/holidays.quest` defines `drought` (line 8), `famine` (21), `harsh winter` (34) and `newyear` (47). The first three set a wait of 800 ticks plus one interval roll, a duration of 900 plus a random 1-to-400 range, and a room mask by season or weather. `newyear` uses a mud date and applies to all rooms. The mob chatter is weighted keyword replies. Each entry also sets price factors by item class. In the drought, drinks get a price factor of 20.0, shown as 2000% (`holidays.quest:18`, `Libraries/Quests.java:1251`). `newyear` uses a mud date, `SET MUDDAY 1-1` (line 48).

### Mystery pools

`normal_mystery1.quest` supplies a motive list, `$MOTIVE_LIST`, with short examples such as "drunken anger", "jealous love" and "insulted honor" (line 112). `MOTIVEGROUP` keeps the list as candidates and picks one as the motive (`DefaultQuest.java:3029–3032`).

### Command and time vocabulary

- Time expressions: `900+(1?400)` means 900 plus a random 1 to 400 ticks. `1?5` is a random roll.
- Zapper masks, `MASK=-SEASON +SUMMER`, select by flags.
- Holiday step lines: `SET NAME`, `SET WAIT`, `SET INTERVAL`, `SET DURATION`, `SET DATE`, `SET MUDDAY`, `SET ROOM`, `SET MOBGROUP RESELECT MASK=...`, `GIVE BEHAVIOR MUDCHAT +(...)`, `GIVE AFFECT MOOD`, `GIVE STAT PRICEMASKS`.

## Notable design ideas

1. **An undo journal for world changes.** Every change is recorded as an addon with its old value and its preserve count, and `cleanQuestStep` reverses them in reverse order (`DefaultQuest.java:4565–4850`, `5524–5751`). A quest leaves the world as it found it, without the author having to write teardown code.
2. **Steps with object lifetimes.** `PRESERVE n` keeps an object for `n` steps (1852–1853, 4614–4629). Quests can hand a prop from one scene to the next.
3. **Set algebra for selection.** `+`, `-` and `#` (`getObjectIfSpecified`, 6290–6374) let a script pick "one of the guards, but not the captain" in a single expression.
4. **Reservation pools.** `RESELECT` and `filterOutThoseInUse` (827–862) let several quests share a pool of mobs without grabbing the same one, which keeps concurrent quests from breaking each other.
5. **Schedules in game time.** Quests can start on a real date, a mud date, or an interval that is randomized (`resetWaitRemaining` 5150–5211). Holidays are calendar entries that the journal system can display (`getHolidayEntries`).
6. **Exponential backoff.** A failed start doubles the pause (5152–5157). A durable quest retries after 10 ticks (4483–4497).
7. **Run levels.** A lower run level blocks others from starting while it runs (5456–5470). This is a simple priority system for a busy world.
8. **Back-pointers from world objects.** Each quest object carries a `QuestBound` effect that names its quest (5588–5593). Lookups of "which quest is using this" are then cheap (`isObjectInUse`, 6075–6086).
9. **Random alternatives in scripts.** `<OPTION>` blocks (6222–6230, 865–891) give script-level variety with no extra mechanism.
10. **Templates that are also documentation.** A template's `#` comments are both the wizard's help text and the file's documentation (`getQuestTemplate` 1504–1660). The same file works as a recipe and as a reference.
11. **Mystery generator.** Pick one true value per category, plus distractors, and the same mechanism yields clue sets (`MysteryData`, 7000–7077).

## Problems and cruft

Every item here was found by reading and has not been run.

1. **`QUIET ON` and `QUIET OFF` do not set the quiet flag.** The standalone form assigns `q.beQuiet=optional;` (`DefaultQuest.java:1145`). It should assign the requested state. The bare `QUIET` (1131–1134) and the inline `QUIET ON <cmd>` form (1136) do work. The `OPTIONAL` branch right above (1106–1113) is correct, so this looks like a copy-paste error. The help files document only the player's `QUIET` command, not the script form.
2. **`ABILITYGROUP` and `ABILITY` set the wrong current object.** `ABILITYGROUP` sets `q.envObject=q.itemGroup;` (`DefaultQuest.java:1736`), and `ABILITY` sets `q.envObject=q.item;` (2539). Both should use their own variables. A `GIVE BEHAVIOR` or `GIVE AFFECT` that follows them acts on the wrong object or on null.
3. **The QuestMaker ABILITY validator can dereference null.** `Libraries/Quests.java:2553–2556` calls `CMClass.getAbility`, falls back to `findAbility` (which returns null when nothing matches, `core/CMClass.java:2104–2130`), and then reads `A.classificationCode()` without a null check. An unknown ability name typed at the prompt throws. The `$MEFFECT` validator (2594–2606) guards the same case.
4. **Template substitution also deletes page titles.** Each page's title row stores the page name in the variable column (`Libraries/Quests.java:1562`). The substitution loop does not skip that row (2158–2164), and `CMStrings.replaceAll` (`core/CMStrings.java:927–945`) replaces every occurrence. If a page title occurs in the generated script, that text is removed.
5. **Concurrent modification risk.** `runtimeRegisterObject` removes an entry from `questState.worldObjects` inside a for-each loop (`DefaultQuest.java:5573–5583`). That field is an `SVector` over a synchronized list (`core/collections/SVector.java:13, 437–439`), so its iterator is fail-fast. A `ConcurrentModificationException` is likely when a destroyed object is not the last one in the list.
6. **The holiday ANY-area branch is dead code.** `Libraries/Quests.java:247–251` tests `line.get(0)` for "ANY". The lines were filtered by the command "SET" (230, 1414–1447), so `line.get(0)` is always "SET". The intended `AREAGROUP ANY` case never runs. Instead, the loop at 254–258 iterates over every token of the line.
7. **Duplicated date arithmetic.** `parseRLDate` (`Libraries/Quests.java:320–336`), `parseHolidayMudDay` (338–358), and the DATE and MUDDAY branches of `resetWaitRemaining` (`DefaultQuest.java:5166–5206`) repeat the same logic. A fix must be made in three places.
8. **The interface and the code disagree on `stepQuest`.** `Common/interfaces/Quest.java:292–293` says that `stepQuest` calls `stopQuest()` when no steps remain. The code calls `stopQuestInternal()` (`DefaultQuest.java:4916`). `stopQuest` is the one that bumps the success counter (5083–5087), so that counter is not bumped on this path.
9. **Duplicate doc text.** `Quest.java:816–819` describes both `ANY` and `ANYONCE` as "one random step, once". `TRUE` and `FIRST` at 806–809 are also described identically.
10. **Unused field.** `expires` (`DefaultQuest.java:61`) is never read. `EXPIRATION` is implemented as "copy with a duration" (6497–6512), which makes the copy delete itself at stop (5139–5143).
11. **Unguarded argument access.** `PRESERVE` reads `p.get(2)` with no length check (`DefaultQuest.java:1853`). `GIVE ABILITY` reads `p.get(2)` (3956) when the line is short but `q.ability` is already set, which skips the length check at 3949. Both throw `IndexOutOfBoundsException`. The try/catch in `startQuestInternal` (4469–4477) and `stepQuest` (4889–4893) catches them and turns them into quest errors, so this is not a crash.
12. **Names go into SQL unescaped.** `QuestLoader.DBUpdateQuest` puts `Q.name()` straight into the SQL text (`core/database/QuestLoader.java:109, 118`). The QuestMaker path checks names (`Libraries/Quests.java:2248–2251`). The `CREATE QUEST` path does not validate the name (`Commands/Create.java:2315–2345`). Creating quests requires `CMDQUESTS` (2317), which limits exposure.
13. **Winners are keyed by name string.** `declareWinner`, `wasWinner` and `whenLastWon` (`DefaultQuest.java:5264–5365`) use the player name. A renamed character loses its win record. This is inference, not tested.
14. **`parseQuestScript` is one method of about 3,300 lines** (1036–4360). The MOB, PCMOB, ITEM, ABILITY and their GROUP variants repeat the same choose-filter-reserve pattern. The duplication makes changes risky.
15. **Timers reset on restart** (see Persistence). This is probably acceptable, but it is a design choice nobody appears to have written down.

## Takeaways for a Python SUD

1. **Keep the step model and the undo journal; drop the interpreter.** A quest is a list of steps, and each command returns an undo closure registered on a journal with a keep-count. Replace the addons, `worldObjects` and `QuestBound` machinery (`DefaultQuest.java:4565–4850`, `5524–5751`) with something like:

   ```python
   @dataclass
   class Journal:
       entries: list[tuple[int, Callable[[], None]]] = field(default_factory=list)

       def record(self, undo, keep_steps=0):
           self.entries.append((keep_steps, undo))

       def end_step(self):
           kept = []
           for keep, undo in reversed(self.entries):
               if keep > 0:
                   kept.append((keep - 1, undo))
               else:
                   undo()
           self.entries = list(reversed(kept))
   ```

2. **Define quests as data, not as a language.** Store each quest as YAML, TOML or JSON, with steps as lists of small typed commands. The `SET`/`GIVE`/`TAKE` vocabulary (`DefaultQuest.java:1036–4360`) is useful as a list of verbs, but Python can express it in data directly. Mask filters become plain predicates: `{"race": "elf", "min_level": 10}`.
3. **Keep the expression idea, not the parser.** A dice-plus-random tick expression such as `900 + (1d400)` is enough for the holiday style (`holidays.quest:11`). A small `ast`-based evaluator with a random roll is safer than reproducing `CMath`.
4. **Use in-game time for durations.** Express quest durations in game time (game hours or turns), not in wall-clock ticks. That works for both real-time and turn-based SUD, because each one can define its own clock. It also gives you the "mud date" feature (`MUDDAY`) for free as a calendar event.
5. **Schedule with a priority queue, not one thread per quest.** Keep the `WAIT + d(INTERVAL)` rule and the doubling backoff after a failed start (`DefaultQuest.java:5150–5211`, 5152–5157). Run levels (5456–5470) become a single "one main quest at a time" rule, plus a priority for background events.
6. **Turn holidays into calendar events.** The holiday file is a list of dated events with weighted NPC lines and price multipliers by category. In SUD this is one `events.py` (or one data file) that the editor can change. The four holidays are a useful starting set.
7. **Borrow the mystery generator.** For a text game this is one of the most reusable ideas here. Pick one true culprit, motive, place, time and tool from pools, and add distractors. Clues are then easy to generate and check, and nothing else needs to be stored (`MysteryData`, `DefaultQuest.java:7000–7077`, `templates/normal_mystery1.quest`).
8. **Build quests from templates.** Ten themes (capture, collect, delivery, dispel, escort, invasion, killer, mystery, protect, travel) cover most quest shapes. A SUD version can fill slots from pools and validate each answer, as the QuestMaker wizard does (`Libraries/Quests.java:2193–2676`). The "competitive" family depends on multiple winners and can be dropped.
9. **Single-player changes:**
   - Drop `minPlayers`, `PCMOB`/`PCMOBGROUP`, the session-based `playerMask` and the cross-player winner list (`DefaultQuest.java:5474–5484, 1539–1549`). The player is the only player.
   - Replace `CAN_ACCEPT` and `DO_ACCEPT` with `can_accept(player)` and `on_accept(player)` methods.
   - Replace the Rhino JavaScript and the MOBPROG layer with ordinary Python hooks.
   - Keep one "completed" flag per quest instead of a `name@time` list.
10. **Persistence.** Store definitions and completion flags, as `CMQUESTS` does (`core/database/QuestLoader.java:44–124`). Decide explicitly whether countdowns survive a restart. CoffeeMud resets them (inference), which is probably acceptable.
11. **Things to leave behind.** The embedded XML file mechanism (`DefaultQuest.java:6129–6181`), the escaped-semicolon format (6189, 6231), the `$`-substitution language (6239–6288), and the string-keyed `vars` map (`questState.vars`). Use Python modules, `str.format_map` and typed attributes.
12. **Bugs not to copy.** The `QUIET` assignment (item 1 of Problems), the wrong current-object assignments (item 2), the title substitution (item 4), and the modify-while-iterating list (item 5).

## Coverage

- **Read in full:** `Common/DefaultQuest.java` (7124 lines, in nine chunks), `Libraries/Quests.java` (2680 lines), `Common/interfaces/Quest.java` (870 lines), `Libraries/interfaces/QuestManager.java` (516 lines). Total 11190 lines, matching the manifest.
- **Read in part, for interactions:** `core/database/QuestLoader.java` (lines 44–124 of 240), `Commands/Create.java` (the QUEST branch, 2315–2345), `core/collections/SVector.java` (the list field and iterator), `core/CMStrings.java` (`replaceAll`, 927–945), `core/CMClass.java` (`findAbility`, 2090–2130), `core/CMProps.java` (tick and mud-hour constants, 681–682, 3530), `Libraries/Dice.java` (`roll`, 398–405), `Common/interfaces/CMMsg.java` (152–153 entries).
- **Searched only (grep):** `Abilities/Properties/Prop_QuestGiver.java`, `Items/Basic/StdQuestBoard.java`, `Common/DefaultScriptingEngine.java` (`QVAR`, `MPENDQUEST`, `QUESTWINNER`), `Behaviors/StockMarket.java`, `Commands/Questwins.java`, `Commands/MOTD.java`, `Abilities/Prayers/Prayer_PlanarPilgrimage.java`, `Abilities/Misc/QuestBound.java`.
- **Content:** all 74 template names and headers. `templates/auto_capture1.quest` and `templates/normal_mystery1.quest` were read in part. `holidays/holidays.quest` was read in its first 60 lines.
- **Not read:** `resources/quests/random/` (51 XML files), `Behaviors/RandomQuests.java`, `Behaviors/QuestChat.java`, `Tests/HolidaysQuests.java`, `core/database/QuestLoader.java` lines 126–240 (the bulk update), and the rest of `application/MUD.java`. The guides (`guides/*.html`) and quest entries in `resources/help/*.ini` were not consulted, since they belong to other buckets.
