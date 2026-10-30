# Random quest templates (random and holiday quests)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `c06` (content: Random quest templates (random and holiday quests)) · 52 files · 1,082,927 bytes of data.

## Summary

CoffeeMud invents quests at runtime. The XML under `resources/quests/random/` is a grammar for quests: each template names a quest type (capture, collect, delivery, escort, kill, protect, travel, a "dispel" help quest, or a murder-style mystery), picks real NPCs, rooms, items and areas from the running world with world queries, fills sentences from string tables that are gated by conditions on the chosen NPC, and hands the result to the quest engine as a script. `Behaviors/RandomQuests.java` keeps a small set of generated quests alive on an area or mob, and `Libraries/MUDPercolator.java` interprets the XML. `resources/quests/holidays/holidays.quest` is a simpler use of the same engine: four named world events (drought, famine, harsh winter, New Year) that change room state, NPC chatter and shop price multipliers on a timer. For a single-player SUD the reusable ideas are the reason-and-verb grammar, "pick real world entities with a query" as the way to build tasks, and the holiday event list; the PREV chains, PC-group masks and channel announcements are multiplayer plumbing to drop.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `resources/quests/random/all.xml` | Index. Chooses one template from `TEMPLATE` (`normal`, `auto`, `random` or a name) and `AVOIDSCANS`; loads each template as a `QUEST` entry | 133 |
| `resources/quests/random/inc_questcommon.xml` | Shared grammar: counts, level span, verb families, reason helpers, junk items, announcer and giver queries, multiple-quest rule | 322 |
| `resources/quests/random/normal_*.xml` (26 files) | Templates for quests that have a quest-giver NPC (`ANNOUNCER_*` fields) | 269–714 each |
| `resources/quests/random/auto_*.xml` (23 files) | Templates for "world" quests with no giver; they carry `FREQUENCY` and `DURATION` instead | 199–633 each |
| `resources/quests/holidays/holidays.quest` | Holiday event script, loaded as the quest named `holidays` | 58 |
| `com/planet_ink/coffee_mud/Behaviors/RandomQuests.java` | Behaviour that generates quests from a template file, on area or mob | 324 |
| `com/planet_ink/coffee_mud/Libraries/MUDPercolator.java` | Reads the XML: selection, conditions, variables, world queries, template fill. Used: 266–300, 1639–1700, 3343–3402, 3530–3700, 3881–3965, 4205–4425, 7698–7810 | 8243 |
| `com/planet_ink/coffee_mud/Libraries/Quests.java` | Holiday file loader, holiday entries and listing, CREATE and DELETE HOLIDAY. Used: 169–316, 457–500 | 2680 |
| `com/planet_ink/coffee_mud/Common/DefaultQuest.java` | Quest object and script `SET` handling (`DURATION`, `WAIT`, `INTERVAL`, `EXPIRATION`). Used: 531–560, 5155–5212, 6422–6520 | 7124 |
| `com/planet_ink/coffee_mud/Abilities/Properties/Prop_QuestGiver.java` | `GENERATE:` quest offers on an item, room or mob. Used: 151–166 | 523 |
| `com/planet_ink/coffee_mud/Commands/Generate.java` | Builder `GENERATE` command; the `QUEST` case. Used: 296–300, 332–367 | 447 |
| `com/planet_ink/coffee_mud/Behaviors/MudChat.java` | Chat behaviour; holiday events add lines to it through a `+` parameter. Used: 226, 327–365 | 1755 |
| `com/planet_ink/coffee_mud/Behaviors/Mageness.java` (with `Clericness`, `Druidness`, `Bardness`, `Fighterness`, `Thiefness`) | Class-flavour behaviours attached to generated attacker mobs | 123 |
| `com/planet_ink/coffee_mud/Abilities/Common/GenGatheringSkill.java` | Generic gathering skill that travel templates define inline and grant on arrival | 784 |
| `guides/RandomAreas.html` | Official description of `QUEST` tags, `QUEST_TEMPLATE_ID` and MQL (sections at lines 952–1180) | 1281 |

## How it works

### Two layers: the XML chooses, the `.quest` template scripts

A random quest is built from two files. The XML under `resources/quests/random/` decides which entities and words to use. The matching `.quest` file under `resources/quests/templates/` (bucket c07) holds the quest script itself, with `#$NAME=` markers. The link is the `QUEST_TEMPLATE_ID` attribute on each template's root element, for example `<QUEST ID="normal_capture1" QUEST_TEMPLATE_ID="normal_capture1">` (`resources/quests/random/normal_capture1.xml:40`).

`MUDPercolator.buildQuestTemplate` (`com/planet_ink/coffee_mud/Libraries/MUDPercolator.java:3343-3402`) resolves that name first as a resource path and then as `quests/templates/<name>.quest` (lines 3346-3357). It cuts the text after `#!QUESTMAKER_END_SCRIPT` and, for each `#$VAR=` line whose `$VAR` appears in the script, asks the percolator for a tag or define with the same name and substitutes the value (lines 3373-3389). A missing required value throws `Required variable $X not found`. Two fixed substitutions also happen: `$#AUTHOR` becomes `CoffeeMud` (line 3366) and the template's `set duration` line is rewritten from `DURATION` and `EXPIRATION` (lines 3367-3372; see the Problems section). `buildQuestScript` (lines 3693-3704) turns the result's `%0D` markers into real line breaks.

Embedded `<FILE><NAME>…</NAME><DATA>…</DATA></FILE>` blocks in the generated script become internal files of the quest (`Common/DefaultQuest.java:6150-6175`). Templates use this for reward-item XML, the per-player tracking script (`auto_capture1.quest:139-140` shows the `CAN_ACCEPT` function) and generated skill XML (travel variants).

### Template families and how one is chosen (`all.xml`)

`all.xml` has one chooser `STRING` (lines 21-76) and one `QUEST` with a `load` line for each template (lines 80-133). The chooser is a uniform `select="any-1"` over the entries whose `CONDITION` holds:

- `normal` and `random` allow the `normal_*` entries (lines 25-52), and `auto` and `random` allow the `auto_*` entries (lines 53-75).
- Entries marked `and $avoidscans!='YES'` are skipped when the behaviour sets `AVOIDSCANS=YES` (`all.xml:78`). The in-game help says this avoids "quest templates that scan an entire area" (`resources/help/arc_behaviors.ini:1379`). With `AVOIDSCANS=YES` 13 of the 25 normal entries and 11 of the 23 auto entries remain.
- `normal_travel3` is commented out of the chooser (lines 49-51) but still loaded in the `QUEST` list (line 105), so it is reachable only by naming it.

The `TEMPLATE` default is `normal` (`RandomQuests.java:108-109`), so a behaviour with no template parameter generates only giver-based quests.

### The string and selection language (MUDPercolator)

Every quest field is a `STRING`, `OBJECT`, `MOB`, `ITEM` or `BEHAVIOR` element with optional attributes:

- `SELECT` picks among child elements. `selectChoices` (`MUDPercolator.java:4205-4425`) accepts `ALL`, `NONE`, `ANY` (one uniform), `ANY-n` and `ANY-$var` (n uniform without replacement), `REPEAT-n`, `FIRST`/`LAST` with `-n`, `LIMIT-n`, `PICK-n` (weighted without replacement), and a plain expression.
- `PICKWEIGHT` sets the weight for `PICK-n`. An untagged child has weight 0 and is never chosen while others have weight. Weight may be an expression, for example `PICKWEIGHT="$target_level"` (`normal_capture1.xml:501`).
- `CONDITION` is tested by `testCondition` (line 3966) with `=`, `!=`, `>`, `<`, `in`, `and`, `or`. Example: `CONDITION="$target_int &gt; 5"` (`normal_capture1.xml:123`).
- `DEFINE` publishes a value under a name, `PREDEFINE` publishes before the children are built, `REQUIRES` demands a name exist (`checkRequirements`, line 4174), and `MERGE` and `ACTION="PREPEND"/"APPEND"` combine text.
- `load="…"` includes another file's definitions (`normal_capture1.xml:74`).
- Variables are `$name`. `${x:name}` applies modifiers from `parseVariables` (lines 1639-1700): `l` lower, `u` upper, `c`/`C` capitalise, `p` plural, `j` JavaScript-escape, `1` one line, `_` one word. Unknown modifiers such as `h` are skipped without effect. `$[a?b]` is an inclusive random number and `$[expr]` a calculation (see `Libraries/Quests.java:1055-1056`, which states "1?5 means random number from 1-5").
- Grammar helpers: `${C:…}` capitalises, `${p:target_name}` pluralises a mob or item name, and `${SELECT: … FROM …}` runs an inline query.

### World queries (MQL)

`OBJECT` elements whose value starts with `SELECT:` run an MQL query over the live world (`doMQLSelectObjs`, `MUDPercolator.java:7698`; parser `MQLClause`, line 4427). The guide describes the form as `SELECT: [properties] FROM [object set] WHERE [comparisons]` (`guides/RandomAreas.html`, MQL notes, lines 1054-1180). Narrowing suffixes such as `\npcs`, `\room`, `\exits` and `\mobs` walk from areas to rooms to mobs, and `count\` counts. The shared viability queries in `inc_questcommon.xml` are typical:

```
SELECT: . FROM area\npcs
  WHERE name\count=1 and current_intelligence>5
  and "Aggressive" notin objattrib and "QuestBound" notin affbehav
  and (SELECT: count\. FROM mob\room\exits) > 0
  and 'ROOMUNEXPLORABLE' notin (SELECT: current_sensesstr FROM mob\room)
```
(`resources/quests/random/inc_questcommon.xml:23-31`). A giver must have a unique name, intelligence above 5, must not be aggressive or quest-bound, must stand in a room with exits, and that room must be explorable. The giver-selection variant also excludes shopkeepers (`mql_givable_questgivers`, lines 43-52).

### Generator lifecycle (RandomQuests)

`RandomQuests` is an `ActiveTicker` behaviour attached to an area or mob (`Behaviors/RandomQuests.java:76-80` sets 10–20 ticks and 100% chance). Its parameters are read in `setParms` (lines 89-112):

- `MINQUESTS`/`MAXQUESTS` (both default 1, lines 63-64), `MAXATTEMPTS` (3), `EXPIRE` (default `6 hours`, line 68), `TAGID` (default `all_quests`), `PATH` (default `randareas/example.xml`, line 70), and `INLINE`.
- Every other key becomes a template variable in `varMap`. `EXPIRATION` is always set to the expiry value (line 110).

`tick` (line 270) waits for `canAct`, fixes `numQuests` once per setting (between min and max), drops dead weak references, and starts a `GenerateAQuest` if fewer quests than `numQuests` are alive. The generator runs on a library thread (line 299) unless `INLINE` is set, in which case it runs inside the tick (lines 294-296).

`GenerateAQuest.run` (lines 164-266) does the work:

1. Global cap: at most `availableProcessors()/4` generators run at once across all instances (lines 168-170).
2. For up to three attempts, reads and parses the whole XML file again (line 178), builds the defined-ID table with the behaviour's variables (`buildDefinedIDSet`), checks that the tag `TAGID` is a `QUEST`, runs `checkRequirements`, builds the script, runs `postProcess`, and requires a `QUEST_ID`.
3. Builds a `DefaultQuest`, rejects duplicate names, calls `addQuest` and `startQuest`, sets `setCopy(true)` so it is temporary, and stores a weak reference (lines 218-238).
4. Several failures set `disable` on the behaviour instance, after which its `tick` returns early: missing file (line 182), undefined `TAGID` (194), missing requirements (205), and three failed attempts (251-252). Debug disabling is at line 112.

`endBehavior` (line 116) stops and deletes the quests the behaviour made.

### Quest lifecycle: duration, wait, expiry

`DefaultQuest.setStat` (`Common/DefaultQuest.java:6422-6520`) implements the script's `SET` lines. The codes come from `Quest.QCODES` (`Common/interfaces/Quest.java:826-829`):

- `SET DURATION` (code 2) sets how long the quest runs in ticks, from a time expression.
- `SET WAIT` (code 3) sets the minimum time between runs. `SET INTERVAL` (code 9) sets the maximum. The next start is `minWait + roll(1, maxWait) - elapsed` ticks (`DefaultQuest.java:5209`).
- `SET EXPIRATION` (code 15, lines 6495-6513): `0` makes the quest persistent (`setCopy(false)`, saved to the database). Any other value sets the duration and marks the quest a temporary copy (`setCopy(true)`). `DefaultQuest.getStat("EXPIRATION")` returns the duration only for copies (line 336).

So a generated quest from `RandomQuests` is temporary and lasts the expiry time, and one from the builder's `GENERATE` command without an expiration is saved to the world, as `RandomAreas.html:1016-1023` describes.

The tick is 4000 ms (`core/CMProps.java:681`) and a mud hour is 600000 ms (`CMProps.java:682`), so 150 ticks is one mud hour (the same figure appears in the builder prompt, `Libraries/Quests.java:1055`).

### Quest types

Each `normal_` and `auto_` family has the same skeleton (QUEST_ID, QUEST_CRITERIA, CATEGORY, reward fields, speech strings and a `reason` chooser). Their distinguishing fields are:

| Type | Target | Mechanic (key fields) |
|---|---|---|
| capture | mobs | Bring named or listed mobs back. `CAPTURABLES_MASK` or explicit `CAPTUREABLES_MOBS/ROOMS/AREAS` with `CHANCE`. Captives have `CAPTURABLES_ATTACK` (fight or not) and `CAPTURABLES_SURRENDER`. |
| collect | items held by mobs | `COLLECTABLES_ITEMS`, `NUM_COLLECTABLES`, `COLLECTABLES_CHANCE`, holders by `HOLDERS_MASK` or `HOLDER_AREAS/ROOMS`. Wrong items get `WRONG_COLLECTABLE_SAY` ("This is not what I asked for."). |
| delivery | an item to a mob or container | `DELIVERABLE_ITEM`, `DELIVEREE_MASK` or `DELIVEREE_MOBS` with `DELIVEREE_CHANCE`, `DESTINATION_ROOM/CONTAINER`. |
| dispel (help) | a mob afflicted with an effect | `HELPABLES_*`, `TEMPORARY_EFFECTS`, `HELPABLE_ABILITY`, a cure item (`quest_drop_item`) or a prayer. Effects are `Thief_Bind` (bound), a `Disease_*` class, or `Fighter_Pin` (pinned). |
| escort | a mob, to a room or area | `ESCORTED_MOB`, `DESTINATION_ROOM` or `ESCORT_DESTINATION`, `ATTACKER_MOB` with `ATTACKER_PCT_CHANCE` and `ATTACKER_SCREAM`. |
| killer | mobs | `KILLABLES_MASK` or `KILLABLES_MOBS` with `KILLABLES_CHANCE`, `NUM_KILLABLES`, `KILLABLES_ATTACK`. |
| mystery | a murder or theft | Suspects (`SUSPECTS_MOBS`, 2–4 hidden NPCs), tools (`TOOLS_ITEMS`, 2–4 craftable items), motives (`MOTIVE_LIST`, 2–4 words), `WITNESS_MASK` (intelligent non-player witnesses). |
| protect | a mob | `PROTECTED_MOB`, `PROTECTED_ROOM/AREA`, `ATTACKER_MOB` as a group of `ATTACKER_SIZE` mobs. |
| travel | a room | `DESTINATION_ROOM/AREA`, `DESTINATION_NAME`, `TRAVEL_RESPONSE_TYPE`. The `travel3` variants also define a generated gathering skill and grant it on arrival (below). |

The full reads are listed in Coverage. `normal_capture1.xml` is the reference for the giver family, and `auto_capture1.xml` for the world family.

**Giver quests (`normal_*`).** The announcer NPC is chosen by `OBJECT ID="source"` (`normal_capture1.xml:546-550`), and its room, area and name are published as `ANNOUNCER_ROOM`, `ANNOUNCER_AREA` and `ANNOUNCER_NAME` (lines 44-46). The target area and mobs come from `target_area` and `target` (lines 594-618). The notify and instruction strings are built from `reason_short` and `reason_desc` plus a hurt-phrase when the targets are aggressive (lines 89-113).

**World quests (`auto_*`).** There is no giver. The template sets `FREQUENCY` and `DURATION` (`auto_capture1.xml:42-43`). The `.quest` script sets `set wait $FREQUENCY`, `set interval 1` and `set duration $DURATION` (`resources/quests/templates/auto_capture1.quest:124-133`), then assigns the quest to players in the area whose level fits the mask with `set pcmobgroup reselect any mask=$QUEST_CRITERIA` (line 130). Acceptance is handled by the `CAN_ACCEPT` function, which requires `ISPC` and, unless `MULTIPLE` is `PREV`, checks that the player has not already won (`auto_capture1.quest:139-140`).

**Travel with a generated skill (`auto_travel3`, `normal_travel3`).** The template defines a `GenGatheringSkill` in XML (`auto_travel3.xml:82-100`). Its `TRIGSTR` is the reason verb, its `ROOMMASK` is `-LOCATION +ROOMID(...)` (so it works only in the target room), and its `YIELDFORMULA` is `(1?5)+3`. The `.quest` writes the XML as an internal file and grants it on arrival with `MPCAST` (`auto_travel3.quest:202-203`, bucket c07). Only `auto_travel3.xml` and `normal_travel3.xml` define an `ABILITY` in the file set.

**Mystery.** `normal_mystery1.xml` builds a murder (or robbery, gift, magic, product, or generic) case. The suspects are `GenMob`s with `Prop_WizInvis` and hidden dispositions (`normal_mystery1.xml:407-421`); the tools are craftable items from `metacraft:any-…` filters (lines 424-440). The instruction text says the player must find out WHO did it, WHAT was used and the MOTIVE, that locals give clues when asked, and that the player should report back with an answer to each (`reason_murder`, lines 84-115). The clue mechanism is in the `.quest` script and is not traced here.

### Dialogue: reasons, verbs and speeches

Each template defines a `reason` chooser with one entry per story hook. In `normal_capture1` there are 16 hooks (`normal_capture1.xml:122-139`), and each has a condition:

| Hook (`reason_…`) | Story | Condition (as written) |
|---|---|---|
| `theyoweme` | the target owes the giver | target intelligence &gt; 5 |
| `partytime` | a party or celebration, guests are late | none |
| `iowethem` | the giver owes the target | target intelligence &gt; 5 |
| `ilostthem` | giver lost the target | giver alignment &gt; 5000 |
| `welosteachother` | giver and target separated | giver alignment &gt; 5000 |
| `theylostme` | target lost the giver | none |
| `gencapture` | plain capture | none |
| `giveinstr`, `planning`, `receiveinstr`, `wedotogether`, `theydoforme` | lessons, committee meetings, joint projects | target intelligence &gt; 5 |
| `idoforthem` | giver does something for the target | intelligence &gt; 5 and alignment &gt; 5000 |
| `idotothem`, `wedotoeachother` | cruel or ritual motives | alignment &lt; -5000 |
| `deitydo` | a deity requires it | a deity is set |

The `reason_` entry supplies `reason_verb`, `reason_verbed`, `reason_desc`, the win, lose, fight and surrender speeches, and the quest-name variants (lines 161-495). Verb families (`inc_questcommon.xml:134-300`) keep the phrasing consistent: a present form and a past form are chosen together. For example, the capture family is `capture` / `captured`, `catch` / `caught`, `kidnap` / `kidnapped`, `bring` / `brought`.

The short strings in `reason_short` are sentence-length prompts such as "I need $num_targets ${p:target_name} in $target_area_name $reason_verbed!" (`normal_capture1.xml:90`). The fight speeches are where the aggressive and dark tone appears: targets refuse, threaten or beg, and the instruction text tells the player to "hurt" them if needed (`reason_hurt` and `hurt_them_phrase`, `inc_questcommon.xml:269-310`).

### Numbers and formulas

| Quantity | Formula or value | Source |
|---|---|---|
| Targets per quest | `$[3?8]` (3–8) | `inc_questcommon.xml:85` |
| Targets, motives and tools in mystery | `$[2?4]` (2–4) each | `normal_mystery1.xml:66, 405, 424` |
| Player level window | target level ± 8 (`level_span`) | `inc_questcommon.xml:88-90`; `normal_capture1.xml:42` |
| Gold reward | `2 × target level` | `normal_capture1.xml:519-521` |
| XP reward | `100 + 3 × target level` | `normal_capture1.xml:523-525` |
| Quest points | YES weight 1, NO weight 2 | `normal_capture1.xml:514-517` |
| Aggressive targets | NO weight 50, YES weight = target level | `normal_capture1.xml:499-502` |
| Ambush chance (escort, aggressive) | 10 | `normal_escort1.xml:49-52` |
| Attacker chance (protect) | `5 + target level / 2` | `normal_protect1.xml:54` |
| Density for multi-target lists | `2 × (5 + num_targets / target_area_size × 100)` | `normal_capture2.xml:50`; `normal_killer2.xml:51`; `normal_delivery3.xml:50` |
| Faction change | source `$source_faction_amt / 500`; target `$target_faction_amt / -500` | `normal_killer1.xml:57`; `auto_capture1.xml:55` |
| Several quests from one giver | PREV 98, YES 1, NO 1 | `inc_questcommon.xml:78-82` |
| Announcement channel | LOCAL, SAY or none, uniform | `inc_questcommon.xml:65-69` |
| Expiry of a generated quest | 6 hours | `RandomQuests.java:68` |
| Auto quest frequency in XML | `1` (one tick) | `auto_capture1.xml:42` |
| Auto quest duration in XML | `90 minutes` | `auto_capture1.xml:43` |
| Holiday wait | `800` ticks minimum plus 1 | `holidays.quest` (first holiday) |
| Holiday duration | `900 + (1?400)` ticks | `holidays.quest` |
| Generator gap | every 10–20 ticks | `RandomQuests.java:78`; `ActiveTicker.java:49-50` |

The 4-second tick turns the holiday numbers into real time, roughly 53 minutes between runs (801 ticks) and 60 to 87 minutes of duration (901–1300 ticks). These are derived here, not measured.

### Messages shown to players

Player-facing text comes from three places: the quest name (`QUEST_NAME`, `normal_capture1.xml:59-65`), the notify string said when the player enters the announcer's room (`quest_notifystring`, lines 76-87), and the instruction string in the quest log (`quest_instructionstring`, lines 98-113). The win, lose and surrender speeches come from the shared tables (`quest_generic_winner_speeches`, `quest_generic_loser_speeches`, `quest_generic_surrender_speeches`, `inc_questcommon.xml` and `normal_capture1.xml:141-159`). A sample win line: "They are all $reason_verbed! That's all of them, thanks!" (`normal_capture1.xml:149`).

### Holiday quests

`holidays.quest` is a plain script in the quest language: a header step with `SET NAME`, then one step per holiday separated by `STEP BREAK`. The first step is skipped when reading names (`Libraries/Quests.java:222, 226`).

**Loading.** `Quests.getHolidayFile` (lines 169-205) makes sure a quest named `holidays` exists with the script `LOAD=quests/holidays/holidays.quest`, copies the file to its writable location if needed, and reads it. `getHolidayEntries` (lines 207-316) turns each step into a `JournalEntry`. It reads `NAME`, `AREAGROUP` or `AREA`, `MUDDAY`, `DATE` and `DURATION`, and sets the expiry to the duration in ticks or to one mud hour when there is no date (lines 297-303). These entries power the calendar (`Commands/CalendarCmd.java:110-134`), `LIST HOLIDAYS` (`Libraries/Quests.java:361`), and two prayers that treat the holiday quest as special (`Abilities/Prayers/Prayer_SeekersPrayer.java:117`, `Prayer_Prophecy.java:225`).

**Editing.** `CREATE HOLIDAY` writes a new step from a fixed template (`Libraries/Quests.java:487-500`): `SET WAIT 900+(1?100)`, `SET DURATION 900`, `SET PERSISTANCE TRUE`, a `MUDCHAT` line, and `GIVE STAT PRICEMASKS 0.75 -MATERIAL +CLOTH`. Builders can list holidays (`Libraries/Quests.java:361`) and delete them (`deleteHoliday`, line 504) in the game. `createHoliday` appends the new step to the file on disk when `save` is set (lines 474-479).

**The four events (`holidays.quest`).**

| Event | Timing | Effects |
|---|---|---|
| drought | `WAIT 800`, `DURATION 900+(1?400)` | Rooms picked by a zapper mask over season and weather tags (`set room any mask=-SEASON +SUMMER -WEATHER +DROUGHT`; selection not traced). NPCs picked by `MASK=+INT 3` chat about water and drought (`MUDCHAT` triggers: water, thirst, drought). Drink price multiplier 20.0. |
| famine | same timing | Rooms picked by a mask over season, heat and dust tags. NPCs chat about food and hunger. Food price multiplier 10.0. |
| harsh winter | same timing | Rooms picked by a mask over season, winter and cold tags. NPCs chat about cold and snow. Wooden goods and coal multiplier 10.0; cloth and leather armour 5.0. |
| newyear | `MUDDAY 1-1`, `DURATION 900+(1?400)` | Chat with a "Happy New Year" line, `GIVE AFFECT MOOD happy`, and a 0.9 price multiplier on a broad mask. |

The chat lines use `MudChat`'s bonus syntax, a `+` prefix followed by `;`-separated lines (`Behaviors/MudChat.java:327-365`). The leading digit of each response is its weight (`MudChat.java:226`), so `5yes, …` has weight 5 and `9the famine …` weight 9.

Price effects use `GIVE STAT PRICEMASKS <multiplier> <mask>`, the same form as the generated default (`Libraries/Quests.java:498`). The shop-side code that applies the multiplier was not traced; `GenShopkeeper.java:106` lists `PRICEMASKS` among its stat codes.

### Interactions

- **Prop_QuestGiver.** An item, room or mob with `QUEST="GENERATE:…"` calls `RandomQuests` with `INLINE=true` and runs its `tick` up to ten times (`Abilities/Properties/Prop_QuestGiver.java:151-166`). Generation happens whenever the cached quest is missing or has stopped running (`Prop_QuestGiver.java:145-146`), and a new behaviour instance is created each time (line 153).
- **Generate command.** `GENERATE QUEST` builds a script with `buildQuestScript` and adds it to the quest manager (`Commands/Generate.java:296-300`, `332-367`). The guide's example `generate quest all_quests template=normal "expiration=20 minutes"` uses this path (`RandomAreas.html:1012-1013`).
- **Quest engine.** Generated quests live in the quest manager (`Libraries/Quests.java:122` `addQuest`, `144` `delQuest`) and are run by `DefaultQuest`.
- **Class flavour.** Generated attacker mobs get one class behaviour chosen at random from 36 (see Content). Each is a combat-ability behaviour, for example `Mageness` extends `CombatAbilities` (`Behaviors/Mageness.java:36`).
- **Holiday events.** They reuse `MudChat`, shop price masks, room masks and mob masks, so they change the same objects the rest of the game uses.

## Content worth mining

### Catalogue of the 52 files

Lines and bytes come from the files themselves and the manifest. "[gated]" means the template is excluded when `AVOIDSCANS=YES` (`all.xml`). Features are read from each file's top-level tags and the names of its reasons and targets, so they describe the shape of each template, not every line.

| File | Family, type | Lines | Bytes | Target selection and distinguishing fields |
|---|---|---|---|---|
| all.xml | index | 133 | 12,186 | Chooser and loader |
| inc_questcommon.xml | shared include | 322 | 17,890 | Verb families, reasons, junk items, queries |
| holidays.quest | holiday events | 58 | 2,398 | Four world events |
| normal_capture1 | normal, capture [gated] | 625 | 34,619 | Mask-picked captives (`-HOME area -NAMES name`); 16 reasons; read in full |
| normal_capture2 | normal, capture | 688 | 37,210 | Explicit mob, room and area lists with density `CHANCE` |
| normal_capture3 | normal, capture [gated] | 650 | 35,305 | Mask-picked captives |
| normal_capture4 | normal, capture | 714 | 38,268 | Explicit lists with `CHANCE` |
| normal_collect1 | normal, collect [gated] | 437 | 24,328 | Items held by mask-picked mobs |
| normal_collect2 | normal, collect | 386 | 20,044 | Holders in listed areas and rooms |
| normal_collect3 | normal, collect | 504 | 27,496 | Holders from a mob list plus areas and rooms |
| normal_collect4 | normal, collect | 408 | 27,192 | Items picked by resource and crafting class; wrong-item line |
| normal_collect5 | normal, collect | 283 | 13,518 | Shared target-item selection; wrong-item line; no holder structure |
| normal_delivery1 | normal, delivery [gated] | 482 | 24,955 | Deliver to a mask-picked mob |
| normal_delivery2 | normal, delivery [gated] | 371 | 17,837 | Deliverer NPC to a room or container, with container guess helpers |
| normal_delivery3 | normal, delivery | 499 | 26,721 | Deliver to listed mobs with `CHANCE` |
| normal_delivery4 | normal, delivery | 419 | 21,245 | Deliver to a mob picked from viable targets |
| normal_dispel1 | normal, dispel [gated] | 511 | 30,666 | Help a bound (`Thief_Bind`), diseased or pinned (`Fighter_Pin`) mob |
| normal_escort1 | normal, escort [gated] | 424 | 19,590 | Escort to a room; ambush chance 10 when aggressive |
| normal_escort2 | normal, escort | 400 | 18,056 | Escort to an area; attackers |
| normal_killer1 | normal, killer [gated] | 346 | 17,383 | Kill mask-picked mobs |
| normal_killer2 | normal, killer | 409 | 20,283 | Kill listed mobs, rooms and areas with `CHANCE` |
| normal_mystery1 | normal, mystery | 445 | 26,787 | Six case types; suspects, tools, motives, witnesses |
| normal_mystery2 | normal, mystery | 346 | 20,102 | Four case types; no tools |
| normal_protect1 | normal, protect | 454 | 22,620 | Protect a mob from an attacker group drawn from biome pools |
| normal_protect2 | normal, protect [gated] | 500 | 24,739 | Protect a viable target from an attacker group |
| normal_travel1 | normal, travel [gated] | 313 | 14,198 | Travel to a room with a travel-message type |
| normal_travel2 | normal, travel [gated] | 327 | 14,937 | Travel to a named mob's room |
| normal_travel3 | normal, travel with skill | 269 | 14,116 | Travel with a generated gathering skill; not in chooser |
| normal_travel4 | normal, travel [gated] | 334 | 15,533 | Travel to a named mob's room with messages |
| auto_capture1 | auto, capture [gated] | 524 | 27,929 | Mask-picked captives; level-band or sysop criteria |
| auto_capture2 | auto, capture | 603 | 32,079 | Explicit lists with `CHANCE` |
| auto_capture3 | auto, capture [gated] | 565 | 28,926 | Mask-picked captives |
| auto_capture4 | auto, capture | 633 | 32,997 | Explicit lists with `CHANCE` |
| auto_collect1 | auto, collect [gated] | 358 | 19,675 | Items held by mask-picked mobs |
| auto_collect2 | auto, collect | 307 | 15,795 | Holders in listed areas and rooms |
| auto_collect3 | auto, collect | 427 | 23,174 | Holders from a mob list plus areas and rooms |
| auto_collect4 | auto, collect | 337 | 23,358 | Items by resource and crafting class |
| auto_collect5 | auto, collect | 236 | 11,373 | Shared item selection; wrong-item line |
| auto_delivery1 | auto, delivery [gated] | 415 | 20,566 | Deliver to a mask-picked mob |
| auto_delivery2 | auto, delivery [gated] | 316 | 14,566 | Deliver to a room or container |
| auto_delivery3 | auto, delivery | 437 | 22,535 | Deliver to listed mobs with `CHANCE` |
| auto_delivery4 | auto, delivery | 355 | 17,732 | Deliver to a mask-picked mob |
| auto_dispel1 | auto, dispel [gated] | 439 | 26,670 | Help an afflicted mob; same effect list as normal (contains a typo, see Problems) |
| auto_escort1 | auto, escort [gated] | 364 | 16,220 | Escort to a room with attackers |
| auto_escort2 | auto, escort | 349 | 15,360 | Escort to an area with attackers |
| auto_killer1 | auto, killer [gated] | 272 | 12,964 | Kill mask-picked mobs |
| auto_killer2 | auto, killer | 341 | 16,571 | Kill listed mobs, rooms and areas with `CHANCE` |
| auto_protect1 | auto, protect | 380 | 18,949 | The only protect template in the auto family |
| auto_travel1 | auto, travel [gated] | 252 | 10,910 | Travel to a room with a message |
| auto_travel2 | auto, travel [gated] | 263 | 11,812 | Travel to a named mob's room |
| auto_travel3 | auto, travel with skill [gated] | 199 | 10,244 | Travel with a generated gathering skill granted on arrival |
| auto_travel4 | auto, travel [gated] | 271 | 12,300 | Travel to a named mob's room with messages |

### Verb families (`inc_questcommon.xml`)

| Family | Choices (present / past) |
|---|---|
| capture | capture / captured; catch / caught; kidnap / kidnapped; bring / brought |
| gather | gather / gathered; lead / led; collect / collected |
| find | find / found; rescue / rescued |
| escort | escort / escorted to; bring / brought; take / taken |
| protect | protect / protected; save / saved; guard / guarded; safeguard / safeguarded |
| travel | travel / traveled; journey / journeyed; venture / (typo); go / arrived |
| research | research; study; investigate; read (each with -ing and -ed forms) |
| steal | steal / stolen; pilfer / pilfered; snatch / snatched; grab / grabbed |
| bind | tie / tied up; restrain / restrained; handcuff / handcuffed; bind / bound |
| unbind | free / freed; liberate / (typo); emancipate / emancipated; release / released; unbind / unbound |
| hurt | hurt / hurt; kill / killed; beat / beaten; pound / pounded; murder / murdered; violently convince / violently convinced; end / ended |
| heal | heal / healed; save / saved; help / helped; cure / cured; restore / restored |
| transform | morph / morphed; transform / transformed; alter / altered; mutate / mutated |

### Story hooks and speech in the capture family

The sixteen `reason_` hooks listed in How it works are the richest content in the set. Two samples of tone, as paraphrase: a creditor wants their money back from debtors who are "not sure which one" owes them, and a host has been "throwing a party" and the guests are late.

### Mystery motives and case types

Motives are drawn from 13 words: anger, jealousy, honor, debt, irritation, revenge, retribution, retaliation, payback, affront, abuse, offense and glory (`normal_mystery1.xml`, `quest_murder_motive_list`). The case types are murder, robbery, gift, magic, product and generic (`reason_murder`, `reason_robbed`, `reason_gift`, `reason_magic`, `reason_product`, `reason_genmystery`).

### Junk items (`inc_questcommon.xml:96-132`)

Thirty-five "junk" item definitions serve as clutter for quest rooms and as a name source (`$any_item_junk_name`). They are a string of tiny prayer bones, a small clay jug, a cheap-metal shard, a crate, a bedroll, a locket, a glob of blood, a playing card, a worn key, a scrap of paper with a random numeric code, a stick of chalk, a practice sword, a pebble, a puzzle piece and a holy symbol. Some are rotten food from the cooking or baking crafts, with a `Poison_Rotten` effect (lines 105-106, 112-113). Their names are drawn with the `$item_material` and `$adj_colored_color` helpers.

### Disease list (dispel quests)

The disease choices are `Disease_*` classes from `Abilities/Diseases/`, about 48 entries. Samples: Aids, Anemia, Asthma, Blindness, Cold, Diabetes, Giggles, Gonorrhea, Lyme, Malaria, Plague, Scurvy, Smiles, Tinnitus, Yawning, Zika. One entry does not resolve to a class (see Problems).

### Class-flavour behaviours

Generated attacker groups (`normal_protect1.xml:349-386`, and the same block in 16 other files) pick one of 36 class behaviours: Bard, Dancer, Jester, Minstrel (Bardness); Cleric, Doomsayer, Healer, Missionary, Necromancer, Oracle, Purist, Shaman, Templar (Clericness); Beastmaster, Delver, Druid, Mer, Skywatcher (Druidness); Barbarian, Fighter, Ranger (Fighterness); Abjurer, Alterer, Conjurer, Diviner, Enchanter, Evoker, Illusionist, Mage, Transmuter (Mageness); Arcanist, Assassin, Burglar, Pirate, Thief, Trapper (Thiefness).

### Holiday events

Four events in one file (see the holiday section above). The event list is the reusable part: each is a name, a wait, a duration, a list of rooms to affect, a list of NPCs to talk, and a list of price rules.

## Notable design ideas

- **World queries as quest sources.** A quest names real entities that already exist (a unique-named NPC with intelligence above 5, a room with exits, a mob in another area). The text then refers to them by name and place, so the same template reads differently in each world. The viability query at `inc_questcommon.xml:23-31` is the pattern.
- **Reasons gated by personality.** Each story hook has a condition on the NPC's intelligence and on its alignment (`normal_capture1.xml:122-139`). Smart NPCs get debt and lesson hooks that need dialogue, good NPCs get hooks about helping others, and evil ones get hooks about cruelty. The world's character data shapes its quests without any extra logic.
- **Verb pairs.** Present and past forms are chosen together, so "capture / captured" never mixes with "bring / caught". Capitalisation and plurals come from the variable modifiers.
- **Level-scaled rewards and aggression.** Gold and XP scale with the target's level, and the chance of an aggressive target is `level / (50 + level)` (weights 50 and level). That is a simple, honest difficulty knob.
- **Density formula.** Lists of targets get a per-target chance of `2 × (5 + count / area size × 100)`, so more targets in a small area give a higher chance (`normal_capture2.xml:50`).
- **Mystery as a deduction frame.** The murder case has a culprit chosen from suspects who are hidden, tools that hint at the method, motives drawn from a list, and witnesses who must be intelligent. The player has to gather clues and come back with three answers. This is a good shape for a single-player puzzle.
- **Quest-scoped skills.** `auto_travel3` defines a gathering skill in XML, grants it on arrival and limits it to one room by a mask (`auto_travel3.xml:82-100`). A quest can create a small mechanic of its own.
- **Ephemeral quests.** One `EXPIRATION` setting decides whether a quest is temporary (copy) or saved (`DefaultQuest.java:6495-6513`). Generated quests disappear unless saved, which keeps the world tidy.
- **Holiday events as data.** Four events are a list of wait, duration, room mask, chat and price rules. Adding one is a text edit or a builder command, not code.

## Problems and cruft

Nothing here was executed. Items marked "unverified" are read from the code and need a test run.

1. **Possible unbounded loop in inline generation.** With `INLINE=true`, `tick` loops `while(myQuests.size() < numQuests) generator.run();` (`Behaviors/RandomQuests.java:294-296`). `run()` returns early when the CPU cap is reached (lines 168-170) and does not check `disable`. A generation that fails sets `disable` and adds no quest (lines 182, 194, 205, 251-252), so the loop calls `run()` again. `Prop_QuestGiver` uses `INLINE=true` (`Prop_QuestGiver.java:157`). A `GENERATE:` offer whose template cannot succeed may therefore spin the calling thread (unverified).
2. **Generation never runs on small hosts.** `availableProcessors()/4` is integer division before `ceil` (`RandomQuests.java:168`). With 1 to 3 processors the cap is 0, so no generator ever runs (unverified at runtime; combine with item 1 for the inline case).
3. **Documented defaults differ from the code.** The help says `EXPIRE` defaults to `3 hours` and `MINQUESTS`/`MAXQUESTS` to `1-3` (`resources/help/arc_behaviors.ini:1362, 1370`; `guides/refs/behav.html:686`). The code uses `6 hours` (`RandomQuests.java:68`) and 1 and 1 (lines 63-64).
4. **`expiration=` ignored by the behaviour.** `setParms` copies every unrecognised key into `varMap`, then overwrites `EXPIRATION` with the `expire` value (`RandomQuests.java:101-110`). A key named `expiration` therefore never takes effect through the behaviour. The `GENERATE` command path builds its own defined-ID table from the command's variables (`Commands/Generate.java:296-300`); whether that honours `expiration=` as the guide's example implies was not traced.
5. **Auto quests lose their 90-minute duration.** `MUDPercolator.buildQuestTemplate` first writes `SET DURATION <XML duration>` in place of the template's `set duration` line (`MUDPercolator.java:3367-3369`). It then searches for `set duration` again, which now matches the line it just wrote, and replaces it with `SET EXPIRATION <expiry>` (lines 3370-3372). Because `RandomQuests` always supplies an expiry (`RandomQuests.java:110`), auto quests from the behaviour run for the expiry (6 hours) instead of 90 minutes (unverified at runtime; the code path is clear).
6. **Dead references in `all.xml`.** `auto_mystery1`, `auto_mystery2` and `auto_protect2` are loaded (`all.xml:125-128`) but have no files and are not in the chooser (lines 53-75). They are reachable only by naming them explicitly, and then the loader would point at a missing file (unverified).
7. **Typos that reach player text.**
   - `centured` for "venture" (`inc_questcommon.xml:162`). It is the past form for one of four travel verbs and reaches player text through `$verb_traveled` (`normal_travel1.xml:107`).
   - `libertated` for "liberate" (`inc_questcommon.xml:243`). It reaches the winner speech through `$verb_unbound` (`normal_dispel1.xml:203`).
   - `Disease_Syphilisv` (`normal_dispel1.xml:226`, `auto_dispel1.xml:203`) has no matching class. `Abilities/Diseases/Disease_Syphilis.java` exists.
8. **Copy-paste slips.** The `verb_unbound` body reads `$verb_returned` (`inc_questcommon.xml:240`). The `STTRING` tag for `quest_category` (`inc_questcommon.xml:73, 76`) is harmless only if definitions are keyed on `ID`, which `buildDefinedIDSet` (`MUDPercolator.java:266-300`) appears to do (unverified).
9. **Filter skipped.** `QUEST_NOTIFYSSTRING` (double S) in `normal_escort1.xml:72`, `normal_travel1.xml:61`, `normal_delivery2.xml:63` and the auto counterparts skips the `${j:1:…}` filter. The template still resolves the raw `quest_notifystring` define (`normal_escort1.xml:79`), so the text is not escaped the way other templates' text is (unverified).
10. **Unknown modifier.** `${h:quest_template}` (`normal_capture1.xml:41`) uses `h`, which `parseVariables` does not recognise (`MUDPercolator.java:1639-1700`), so it is silently ignored.
11. **Auto frequency looks unintended.** The XML sets `FREQUENCY` to `1`, which is one tick (the wizard says a bare number is in ticks, `resources/quests/templates/auto_capture1.quest:7-8`). The `.quest` then sets `wait $FREQUENCY` (which is 1) and `interval 1` (`auto_escort1.quest:133-134`), so the minimum gap between starts is about two ticks (`DefaultQuest.java:5209`) unless something else holds the quest. The wizard's own example and default is 90 minutes (`auto_capture1.quest:22-24`) (unverified).
12. **Stray quotes.** Some child `STRING`s in `normal_capture1.xml:187-193` are followed by a literal `"` in the parent element. Probably ignored by the parser, but untidy.
13. **Repeated work.** Every generation attempt re-reads and re-parses the whole XML file (`RandomQuests.java:178-180`), and every `GENERATE:` generation creates a new behaviour instance (`Prop_QuestGiver.java:153`). The help itself calls quest generation "cpu-intensive".
14. **Naming quirk, not a bug.** Some templates use `CAPTUREABLES_*` and others `CAPTURABLES_*` (`normal_capture2.xml:47-49` against `normal_capture1.xml:48`). Both spellings are used consistently with the matching `.quest` files, so nothing breaks.
15. **Violent and dark content.** Murder, kidnapping, ritual violence and grim motives appear in the speech tables (for example `normal_capture1.xml:438-476` and `inc_questcommon.xml:269-273`). They are in-world speech, described here in general terms. No sexual content was noticed in the parts read.

## Takeaways for a Python SUD

**Borrow the grammar, not the XML.** A quest template in Python can be a plain data object with the pieces named in this chapter: a kind, a pick rule for the giver and the target, a list of reason hooks each with a condition and text, and the reward formulas.

```python
from dataclasses import dataclass, field
from typing import Callable

@dataclass
class Reason:
    key: str                              # e.g. "debt", "lost", "deity"
    applies: Callable[[dict], bool]       # e.g. lambda c: c["giver"].intelligence > 5
    desc: str                             # "{giver} owes me money, but I'm not sure which one."

@dataclass
class QuestTemplate:
    kind: str                             # capture, collect, deliver, help, escort, kill, travel, mystery
    giver_query: Callable[[World], list]  # unique name, intelligence > 5, not hostile, has exits
    target_query: Callable[[World], list]
    reasons: list[Reason] = field(default_factory=list)
    reward: Callable[[int], dict]         # level -> {"gold": 2*lvl, "xp": 100 + 3*lvl}
```

**Keep the selection rules, drop the language.** The `SELECT` modes map onto `random.choice`, `random.sample` and `random.choices(weights=...)`. The MQL queries become list comprehensions over a world model with indexes. A parser for a query language is not needed for one player.

**Make personality shape the quest.** The reason conditions on intelligence and alignment are the cheapest way to make a single world feel consistent. Give each NPC a few traits and let the template read them.

**Take the grammar tables as data.** The verb pairs and speech lists fit in a YAML or JSON file that a writer can edit. Keep present and past forms together, and test every choice when loading, which would have caught the typos in this set.

**Take the holiday list as a model for world events.** Each event is data: a name, a wait, a duration, a room selector, an NPC selector with chat lines, and price multipliers by item selector. A Python `dataclass` list plus a scheduler is enough.

**Keep the ephemeral and persistent split.** Generated quests should be temporary unless the player keeps them, which is the `EXPIRATION` rule.

**Drop the multiplayer parts.** The `PREV` chains (`inc_questcommon.xml:78-82`), the player-group masks (`set pcmobgroup`, `ISPC` in `auto_capture1.quest:130, 140`), the announcement channels (`inc_questcommon.xml:65-69`), the shared caps on generator threads (`RandomQuests.java:168-170`) and the `avoidscans` gate exist to share one server among many players or to limit the cost of whole-area scans. A single-player game needs one quest-giver relationship, one player's progress, and no scanning, so most of this goes.

**Rethink the mystery frame for one player.** The clue loop is the best fit for a single-player text game. It needs a culprit, a set of clues attached to NPCs who know them, and a check that the player's three answers match. That is the part worth prototyping first.

## Coverage

- **Read in full:** `resources/quests/random/all.xml` (133 lines), `resources/quests/random/inc_questcommon.xml` (322), `resources/quests/holidays/holidays.quest` (58), `resources/quests/random/normal_capture1.xml` (625), `com/planet_ink/coffee_mud/Behaviors/RandomQuests.java` (324).
- **Read in part:** `auto_capture1.xml` (header and lines 36-130), `auto_travel3.xml` (lines 76-100), `normal_mystery1.xml` (lines 36-120 and 396-445), `normal_dispel1.xml` (lines 1-60 and 190-262), `normal_protect1.xml` (lines 340-372), `auto_collect1.xml` (header); and the holiday loader and `SET` handling in `Libraries/Quests.java`, `Common/DefaultQuest.java`, `Libraries/MUDPercolator.java` and `Prop_QuestGiver.java` (the cited ranges).
- **Skimmed by tag inventory, reason counts and sample quest names:** the other 42 template files (all 49 template files were inventoried).
- **Skipped in detail:** the speech text of most templates, the `.quest` companion files (`resources/quests/templates/`, bucket c07), the biome mob pools in `resources/randareas/` (other buckets), and the shop-side price code.
- **Not run:** no quest was generated and no runtime behaviour was observed. Every item marked "unverified" depends on that.
