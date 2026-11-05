# CoffeeSUD: builder capabilities audit

This is a list of engine additions that would let the owner build the world
without editing the engine's Java: new script triggers, functions and
commands, JavaScript hooks, behaviours, properties, quest features, builder
and debugging tools, world options and data files. Each is built once, and
the owner then uses it as content. It is item 2 of `DEVPLAN.md`.

For implementation status at `d1787779`, see the
[project status review](project-status-review.md) and the
[complete feature inventory](project-feature-status.csv). The decisions below
record the original scope; the review checks current code and remaining gaps.
The [first implementation follow-up](project-review-fixes.md) records the fixes
and the inventory's separate follow-up status columns.

## Decisions

The owner went through every item on 2026-10-31: 388 yes, 20 later and 1
undecided. The *Decision* column of the summary and the line under each item
say which, with the owner's note where there was one.

- *Yes* items are planned in `DEVPLAN.md` item 2, in the order set there.
  Each is checked against the code again before it is built.
- *Later* items are wanted but out of the current scope. They were moved,
  in full, to [`LATER.md`](../LATER.md), so they are not forgotten; here
  only their titles remain.
- *Undecided* items stay here until the owner decides.

*Value* is how much an item helps building a world (high, medium, low);
*effort* is a rough, relative size of the engine work (small, medium,
large), as the auditors judged it. *See also* lines link items that overlap
or depend on each other, and mark those that are *later*. IDs are fixed; a
few numbers are missing because those items were merged into others (listed
under *Merged items* at the end). The list can also be read in a browser,
with filters and a search:
[`tools/audit-review.html`](../tools/audit-review.html).

## How it was made

21 areas of the engine were audited, each by its own reader: script
triggers, the script language, JavaScript, quests, NPC behaviours, world
behaviours, properties, builder commands, generators, data files, world
systems, items and puzzles, NPC dialogue, single-player tooling,
presentation and combat, and then the five areas a completeness check found
missing (character progression, travel and vehicles, factions, crafting and
housing, languages). They made 510 proposals. A second reader checked each
against the code: 8 turned out to be possible already (listed at the end,
with how), and the others are either new or partly there, in which case the
item says what exists and what it adds. Duplicates across areas were merged
into 420 items, and a last pass merged 11 more, leaving 409 items in 18
themes.

A sample of 42 items, one in ten, was checked against the code again after
writing: 7 were accurate, 30 had small slips (a name, a syntax, an
overstatement) and 5 were misleading, mostly by calling something impossible
that a builder can already do in part. All of these are corrected, and those
items say *Re-checked*. Expect about the same in the others: most are right
in substance, and about one in eight overstates the gap. So each item is
checked against the code again when it is built, and the owner is told if it
turns out smaller than it looks here.

## Themes

| Prefix | Theme | Items |
|---|---|---|
| ST | [Script triggers](ST-script-triggers.md) | 27 |
| SC | [Script language, functions and commands](SC-script-language-functions-and-commands.md) | 17 |
| JS | [JavaScript](JS-javascript.md) | 21 |
| Q | [Quests](Q-quests.md) | 16 |
| N | [NPC behaviour and dialogue](N-npc-behaviour-and-dialogue.md) | 29 |
| C | [Combat and encounters](C-combat-and-encounters.md) | 25 |
| I | [Items, properties and puzzles](I-items-properties-and-puzzles.md) | 27 |
| W | [World systems (time, weather, environment, economy, law)](W-world-systems.md) | 29 |
| G | [Generators and random content](G-generators-and-random-content.md) | 17 |
| CR | [Crafting, gathering and housing](CR-crafting-gathering-and-housing.md) | 22 |
| F | [Factions and organisations](F-factions-and-organisations.md) | 22 |
| T | [Travel, vehicles and instances](T-travel-vehicles-and-instances.md) | 16 |
| L | [Languages and vocabulary](L-languages-and-vocabulary.md) | 14 |
| P | [Characters, progression and custom abilities](P-characters-progression-and-custom-abilities.md) | 30 |
| PR | [Presentation](PR-presentation.md) | 20 |
| S | [Saves and world files](S-saves-and-world-files.md) | 24 |
| B | [Builder tools](B-builder-tools.md) | 14 |
| DT | [Testing and debugging tools](DT-testing-and-debugging-tools.md) | 39 |

## What builders can do today

Java paths are relative to `CoffeeMud/com/planet_ink/coffee_mud/`. Data and guide paths are relative to `CoffeeMud/`.

- **Scripts (MOBPROG).** `Scriptable` on mobs, items, rooms and areas, `ScriptableEverymob`, and `MPSCRIPT` to attach a script during play. About 58 triggers (`Common/interfaces/ScriptingEngine.java:421-480`), in three kinds. Most react to things in the same room: greet, speech, give, get, drop, wear, open, lock, consume, buy, cast, death, damage. Some run on ticks: `RAND_PROG`, `DELAY_PROG`, `FIGHT_PROG`, `HITPRCNT_PROG`, `ONCE_PROG`, `TIME_PROG`, `DAY_PROG`. A few hear the whole world: `LOGIN_PROG`, `LEVEL_PROG`. `EXECMSG_PROG` and `CNCLMSG_PROG` catch any of about 150 message types, and `CNCLMSG_PROG` can block one with `RETURN CANCEL`. `CMDFAIL_PROG` adds new verbs. An area script hears all its rooms, and messages pass up to parent areas, so a parent "World" area can listen to the whole world. There are 118 functions, usable as `IF` tests or as `$%FUNC()%` text, and about 100 `MP` commands. Any other line runs as a game command. Flow control: `IF`, `SWITCH`, `FOR` (number ranges, lists, MQL `SELECT`, `#tag` groups), `MPWHILE`, `BREAK`, `RETURN`. `FUNCTION_PROG` libraries load with `LOAD=`. Variables have global, named, quest and individual scopes and take `++`, `--`, `+N` and `*N`. The global, named and quest tables are saved. Zapper masks can include script conditions with `+IF`, and `ScriptLater` schedules delayed scripts that are saved. Reference: `guides/Scriptable.html`.
- **JavaScript.** Rhino `<SCRIPT>` blocks can go in any trigger and in `GenAbility`, `GenTrap`, `GenCommand`, quest scripts and cron jobs. `JRUN` runs a file and `JCONSOLE` gives a prompt. Whole classes (behaviours, abilities, properties, commands, mobs) can be written in JS as `//extends` files, loaded with `LOAD` or through the `coffeemud.ini` class paths. A block gets `host`, `source`, `target`, `monster`, `item1`/`item2`, `message`, `objs` and `getVar`/`setVar`. It can call every MP command and function by its uppercase name, and reach the whole engine through `Packages.com.planet_ink.coffee_mud...`. In a `CNCLMSG_PROG`, `objs()[0]` is the live message, so JS can read and change its value. With `JSCRIPTS=2` every block runs without approval. JS keeps state only in MOBPROG variables, which saves hold. JSON is available. Arrow functions, template strings and `let`/`const` are not.
- **Quests.** Quest scripts: `SET`, `LOAD`, `GIVE`, `TAKE`, `STEP`, `RESET`, `IMPORT`, `<OPTION>`, `LOAD=` with arguments, embedded `<FILE>` and `<SCRIPT>` blocks. Scheduling: `WAIT`, `INTERVAL`, `DURATION`, `MUDDAY`, `SPAWNABLE`, `RUNLEVEL`. An undo journal takes back what a quest handed out when it steps or ends. The player side is MOBPROG: the `CAN_ACCEPT`/`DO_ACCEPT` convention, a tracking script put on the player with `MPSCRIPT`, `MPQSET`/`QVAR`, `MPSTEPQUEST`, `MPQUESTWIN`, `MPENDQUEST`, `QUESTWINNER`, `QUESTSCRIPTED`, `QUESTMOB` and `QUEST_TIME_PROG`. Around these: `Prop_QuestGiver`, quest boards, `QuestChat`, `RandomQuests`, 74 Quest Maker templates, holidays, `+QUESTWIN` masks and quest points. A save keeps winners, the current step, the time left, where quest mobs are, and the player's `SAVABLE` scripts with their variables.
- **NPC behaviours.** About 95, each set by a parameter string. Movement: `Mobile` (leash, `OPENDOORS`, terrain), `Patroller`, `Follower`. Fighting: the `Aggressive` family (masks, delays, bribes), helpers that join fights (`BrotherHelper`, `MOBHelper`, `CombatAssister`, `Guard`), door and gate guards (`GateGuard`, `DoorwayGuardian`), `CombatAbilities` and per-class presets (modes, +/- skill lists), `FightFlee`, `Wimpy`, `ProtectedCitizen`. Services: `Hireling`, `Healer`, `MOBTeacher`, menders, `MoneyChanger`, `Concierge`, `TaxiBehavior`. Ambience: `Emoter`, `Sounder`, `Mime`. Plus spawners. `Prop_ClosedDayNight` opens by day and closes at night. Scripts add more with `MPBEHAVE`, `MPWALKTO`, `MPTRACKTO` and `TIME_PROG`. Masks know hour, season, weather and group size.
- **Dialogue.** `MudChat` files in `chat.dat` style: keywords, patterns, weighted replies, emotes, commands, mask conditions, `$%func%` expansion and `$@group` switches. `QuestChat` adds lines said only once. In scripts: `SPEECH_PROG`, `GREET_PROG`, `GIVE_PROG`, `BRIBE_PROG` and `SOCIAL_PROG`, the conditions `VAR`, `QVAR`, `QUESTSCRIPTED`, `FACTION`, `TATTOO` and `MOOD`, and `MPCHOOSE`/`MPPROMPT` menus. An LLM can answer through MudChat backtick replies and `MPLLM`. Languages, the `Mood` effect, faction `MUDCHAT`/`REACTION` rules and shop prejudice also shape what NPCs say.
- **Rooms, areas, weather, hazards.** `<VARIES>` changes room text by season, time, 14 weather states, the viewer's posture, `VISITED`, the direction arrived from, `ELSE`, and a zapper `MASK`, so `+TATTOO` and `+IF` work. Ambience: `Emoter`, `Sounder`, `Prop_Sounder`, `Prop_Smell`. Darkness: night, caves, `Prop_RoomDark HOURS=`. Weather: `WeatherAffects`, `Prop_Weather`, `LinkedWeather`, `PuddleMaker`. Hazards: room atmosphere (drowning, choking), `FieryRoom`, `NarrowLedge`, `WeakBridge`, `InstantDeath`, traps. Spawning: `RandomMonsters`, `RandomItems`, `ItemGenerator`, `RandomTraps`. Also `ResetWhole`, `MovingRoom`, `WaterCurrents`, `TemporaryAffects`, `TriggeredAffects`, area `TIME_PROG`/`DAY_PROG`, and area classes for calendars, grids and generated instances.
- **Properties.** About 144 `Prop_*` classes plus misc effects, attached with `MODIFY` and set by a text string. They include:
  - stat adjusters for items owned, worn or ridden and for rooms, with an `ALLSET` set bonus;
  - ability grants, spell keepers and casters, resistances;
  - entry and wear gates (`Prop_ReqEntry` and others);
  - binding, limits, sockets (`Prop_ItemSlot`) and tethers;
  - room redirects by mask, windows and remote views (`Prop_RoomView`, `Prop_RoomWatch`);
  - hiding things by season, time or weather (`Prop_ClosedSeason`);
  - zone rules: no recall, teleport, summon, magic or combat, unmappable, safe death;
  - terrain traps, local verbs and socials (`Prop_Socials`), `Prop_PropSetter`, `Prop_Doppleganger`;
  - the wrappers `MultiEffects`, `TemporaryAffects` and `TriggeredAffects`.

  Tattoos serve as flags. New properties can be made without Java as a `GenAbility` with a script, or as JS classes.
- **Data-defined content.** `GenRace` and `GenCharClass`. `GenAbility`: targeting, costs, masks, effects in the adjuster syntax, damage and heal formulas, help, and a script that sees every message and tick while the effect lasts. Also `GenTweakAbility`, `GenLanguage`, `GenCraftSkill`/`GenWrightSkill`/`GenGatheringSkill`, `GenTrap`, `GenPoison`, and `GenCommand` (a `FUNCTION_PROG EXECUTE` script). Data files: `achievements.ini` (47 events, 13 rewards, `[ACCOUNT]` achievements, unlock tattoos), awards, `titles.ini`, `factions/`, expertises, components and rituals, socials, help, holidays, planes, instance types, extra stats. Most take `.2` to `.9` extension files. Gen types made in builder mode go to `world/custom.cmare`.
- **Builder commands.** `BUILDER=1` makes the player an Archon and reads and writes the world files. Editing: `CREATE`, `MODIFY`, `DESTROY`, `COPY`, the numbered field editor, `GMODIFY`, MQL (`LIST`/`WHERE SELECT:`, `MODIFY UPDATE:`, `DESTROY DELETE:`), `EVERY`, `CATALOG` prefabs, `TEMPLATE`, `IMPORT` (also `.are` files), `EXPORT`, `MERGE`, `GENERATE`. Inspecting and testing: `GOTO`, `AT`, `WHERE`, `TRAILTO`, `STAT` (with `STAT <mob> SCRIPTS`), `SYSMSGS`, `POSSESS`, `CHARGEN COMBAT`, `DEVIATIONS`, `MPRUN`, `JRUN`, `JCONSOLE`, and `LIST UNLINKEDEXITS`/`ONEWAYDOORS`/`ORPHANS`/`LINKAGES`/`SCRIPTS ERRORS`/`LOG`/`TICKS`. Any `MP` command can be typed as a command, and `MPUNLOADSCRIPT <file>` or `UNLOAD RESOURCE PARSEDPRG` makes edited scripts reload.
- **Dev and test.** `sud.sh` takes any ini key on the command line: `BUILDER`, `RESTORE=NAME|NEW`, a scratch `DBSERVICE`/`WORLDDIR`/`SAVEDIR`, `TIMEMODE=TURNS`, `RANDOMSEED`, `TICKTIME`, `MILLISPERMUDHOUR`, `START`, `DISABLE`, `DEBUG`, `JSCRIPTS`. Time controls: `PAUSE`, `TIMEMODE`, `WAIT n` (at most 100 ticks), `TICKTOCK`, `MODIFY TIME/DAY`. The debug flags `SCRIPTTRACE` and `SCRIPTVARS` write to `mud.log`. `SHELL` has `EDIT` and `COMPAREFILES`. `tools/sudplay.py` and `smoke.sh` run scripted sessions.
- **Generators.** `GENERATE` runs MUDPercolator XML from `resources/randareas/`, which can make areas, rooms, mobs, items, strings, quests and scripts. The template language has conditions, weighted picks, three variable scopes, MQL, `metacraft:` loot, catalog copies and `POWERBONUS`. There are ten 2D layouts, twelve themes and several text banks. In builder mode a generated area is written to `world/areas/*.cmare`. During play: `StdAutoGenInstance`, `AutoGrid`, `RandomQuests`, `Prop_QuestGiver GENERATE:`, and `FROMGENFILE` to load generated mobs and items from scripts. Every roll uses the one shared seeded generator. Reference: `guides/RandomAreas.html`.
- **World systems.** Shop economics are set per shopkeeper and area, fall back to the parent area and then the ini, and are stored in the `.cmare`: `PREJUDICE` by faction band, tattoo, race or class; `PRICEMASKS` (with `+SEASON`); budgets, devaluation, restock, ignore masks and currencies. Laws come from `laws.ini`: crimes, punishments, fines, banishment, taxes. `AUTOREACTION` factions give town reputation. Holiday quests push price masks onto shopkeepers. `ResourceOverride` and `ADDMATERIAL_` shape what can be gathered.
- **Items and puzzles.** About 60 Gen item classes: containers, keys, books, recipe books, portals, a scale, furniture, invisible detail items. Exits have key codes and auto-reclose. There are about 60 traps, 14 bombs, and `GenTrap`. `Prop_OpenPassword` and `Prop_OpenCommand` open things by a password or a typed phrase. The get, put, open, lock and look triggers, plus `EXECMSG_PROG`, `CNCLMSG_PROG` and `CMDFAIL_PROG`, cover item actions, and `MPPROMPT` asks for typed answers. Repair works through `MEND`, mending skills and `ItemMender`. Saves keep item text, effects, door states and script variables.
- **Encounters and combat.** Per-mob tuning: `Prop_CombatAdjuster`, `Prop_StatAdjuster`, `Prop_Adjuster`, `Prop_Resistance`, `Prop_AbsorbDamage`, `Prop_WeaponImmunity`, `Prop_FightSpellCast`. AI: the behaviours above plus `MOBHunter` and `TargetPlayer`. Scaling with `Prop_Doppleganger`. Respawn through `REJUV`, `Prop_Uncampable`, `ResetWhole`, `RandomMonsters`, catalog `ROOM` spawns and `MPREJUV`/`MPRESET`. Bosses can be built by hand from `FIGHT_PROG`, `HITPRCNT_PROG`, `DAMAGE_PROG`, `DEATH_PROG`, `KILL_PROG`, `GROUP_GREET_PROG` and `CNCLMSG_PROG` on `DEATH`/`DAMAGE`, with the commands `MPKILL`, `MPHIT`, `MPDAMAGE`, `MPHEAL`, `MPSTOP ALL`, `MPAFFECT`, `MPALARM` and `MPMLOAD`. Game-wide settings: `FORMULA_*`, `PLAYERDEATH`, `MOBDEATH`, `FLEE`, and `DEATH`/`MORGUE` rooms chosen by mask. `Prop_SparringRoom` gives defeat without death.
- **Character progression.** `coffeemud.ini` keys: `STARTSTAT`, `MAXSTATS`, `BASEMIN`/`MAXSTAT`, `STATCOSTS`, `STARTINGITEMS` by race, start, death and morgue rooms by mask, `DEITYPOLICY`, `DISABLE CHARCR*`, `EXP_CHART`, `LASTPLAYERLEVEL`, `XPMOD`, `EXPDEFER`, `TRAINCOSTS`, `CLASSSYSTEM`. A faction with `AUTOCHOICES` becomes a creation question. `RACE_x` and `CHARCLASS_x` profile tattoos unlock races and classes. `CHARCREATIONSCRIPTS` runs scripts during creation. Skill access is in `allqualifylist.txt` and `expertises.txt`, training in `MOBTeacher` and `Prop_Trainer`. `Prop_ModExperience` filters XP by source. Scripts: `LEVEL_PROG`, `MPEXP`, `MPENABLE`/`MPDISABLE`, `MPPLAYERCLASS`, `MPTRAINS`, `MPPRACS`, `MPTATTOO`, `MPACCTATTOO`, `MPSCRIPT SAVABLE`. `ADDCHARSTAT` and `EXTVAR` add stats and fields.
- **Vehicles, travel, instances.** `GenSailingShip`, `GenCaravan`, `GenAirShip`, `GenBalloon` and `GenCastle` have interiors that the builder builds after `ENTER`, kept in the item's XML. Players use `RAISE ANCHOR`, `SAIL`/`DRIVE`, `STEER` and `COURSE`. `Sailor` gives a crew, `Patroller` a route, `Prop_TicketTaker` a fare. `TaxiBehavior` is a paid ride, and `MOUNT` hitches a cart to an animal. Small rideables and mounts come from recipe files (`boatwright.txt`, `wainwright.txt`, `racialmounts.txt`). Also `MovingRoom` (`resources/movingroom.xml`), `DelayedTransporter`, `GenPortal`, `GenMobilePortal`, `InstanceArea` (`areainstancetypes.txt`), `StdThinInstance`, `StdAutoGenInstance` and `planesofexistence.txt`. All of it runs on game ticks, so it works in turn-based time, and followers come along.
- **Organizations and factions.** Governments are data in `resources/clangovernments.xml`. NPCs join clans through `<CLAN ROLE=>` or `ClanHelper`. `-CLAN` and `-CLANLEVEL` masks work wherever masks do. Scripts have `MPSETCLAN`, `CLANDATA` and `MPSETCLANDATA`, and `Conquerable` makes areas that can be taken. Factions are data: bands, change rules, relations, ability gates, `AFFBEHAV` and per-band `REACTION`s. They reach play through `-FACTION` masks, shop `PREJUDICE` and the `FACTION`/`MPFACTION` script calls.
- **Crafting, gathering, housing.** `CREATE CRAFTSKILL`, `GATHERSKILL` and `WRIGHTSKILL` save to `world/custom.cmare`. Recipes are tab-separated rows in `resources/skills/` with `.1` to `.9` extension files, edited with `MODIFY RECIPE` and listed with `LIST RECIPES`. `KEY_VALUE_PARMS` sets stats on crafted items, and `components.txt` lists components. Gathering is shaped by `ResourceOverride`, `Prop_CommonTwister`, `Prop_ImproveGather`, `hunting.txt`, `fishing.txt`, race butchering yields and `ADDMATERIAL_`. Building skills are data files with a `BUILDER_MASK` column. Land is sold through `Prop_RoomForSale`, `Prop_RoomsForSale`, `Prop_LotsForSale` and `Prop_AreaForSale`, with title items.
- **Presentation.** `<VARIES>` room text (see above), and items with no display text are highlighted in the room description. A minimap comes from `AWARERANGE`/`AUTOMAP`, plus area map items. Colour codes cover 256 colours and true colour, with `COLORSCHEME` and `COLORSET`. The prompt has about 60 tokens. Any `resources/help/*.ini` loads as help. Screens are in `resources/text`, and any engine string can be replaced through `translation_en_TX.properties`. There are `lists.ini` tables and calendar settings, and the `QUESTS` log is fed by script variables.
- **Vocabulary and languages.** Rule files `translation_` and `parser_<LANG>_<COUNTRY>.properties` rename command words, rewrite typed input and run item-name passes. `LANGUAGE` and `COUNTRY` choose which files load. About 70 phrase tables can be layered through `LISTFILE`, and `REPLACECHARSTAT_` renames stats. Currencies are set per area and inherited, with `MoneyChanger` fees. Socials come from the socials file and `Prop_Socials`, and `SOCIAL_PROG` reacts to them. `CREATE LANGUAGE` makes a `GenLanguage`, which can be given to races, mobs (`Prop_LanguageSpeaker`, `CommonSpeaker`) and written items, and taught by `MOBTeacher` or `MPENABLE`. Scripts can use `ISSPEAKING`, `MPSPEAK` and `ISABLE`.

## Summary

| ID | Item | Kind | Value | Effort | Decision |
|---|---|---|---|---|---|
| [ST1](ST-script-triggers.md#st1) | Register LOGIN, LEVEL, LOGOFF and CHANNEL triggers when the script loads | script trigger | high | small | yes |
| [ST2](ST-script-triggers.md#st2) | SKILL_PROG: skill learned, improved, mastered or failed | script trigger | high | small | yes |
| [ST3](ST-script-triggers.md#st3) | DISCOVER_PROG: first visit to a room or area | script trigger | high | small | yes |
| [ST4](ST-script-triggers.md#st4) | Weather and moon changes as messages and triggers | script trigger, JavaScript hook | high | medium | yes |
| [ST5](ST-script-triggers.md#st5) | QUEST_PROG: quest start, accept, step, win, fail and end, anywhere | script trigger, quest feature | high | medium | yes |
| [ST6](ST-script-triggers.md#st6) | GAME_PROG and TURN_PROG: new game, save, restore, switch, pause and turns | script trigger, script function, JavaScript hook | high | medium | yes |
| [ST7](ST-script-triggers.md#st7) | VERB_PROG: verbs on objects that win over skills and socials | script trigger, property, builder tool | high | medium | yes |
| [ST8](ST-script-triggers.md#st8) | Vessel triggers: board, disembark, sail, arrive and dock | script trigger, JavaScript hook | high | medium | yes |
| [ST9](ST-script-triggers.md#st9) | CLAN_PROG, and entry and exit scripts on every membership change | script trigger, JavaScript hook | high | medium | yes |
| [ST10](ST-script-triggers.md#st10) | PARTY_PROG and party functions | script trigger, script function | medium | small | yes |
| [ST11](ST-script-triggers.md#st11) | Calendar triggers: named times of day, ranges, season, month, year, weekday | script trigger, script function | medium | small | yes |
| [ST12](ST-script-triggers.md#st12) | @GLOBAL modifier for EXECMSG_PROG, DEATH_PROG and KILL_PROG | script trigger | medium | small | yes |
| [ST13](ST-script-triggers.md#st13) | CRAFT_PROG and RECIPE_PROG | script trigger | medium | small | yes |
| [ST14](ST-script-triggers.md#st14) | FACTION_PROG: faction change and range crossing | script trigger | medium | small | yes |
| [ST15](ST-script-triggers.md#st15) | COMBAT_PROG: combat start, end and flight | script trigger | medium | small | yes |
| [ST16](ST-script-triggers.md#st16) | Area border triggers and an area entry banner | script trigger, property | high | small | yes |
| [ST17](ST-script-triggers.md#st17) | LEVEL_PROG for level loss, with the new level in $g | script trigger | medium | small | yes |
| [ST18](ST-script-triggers.md#st18) | Fix RIDE_PROG; add DISMOUNT_PROG and REMOVING_PROG | script trigger | medium | small | yes |
| [ST19](ST-script-triggers.md#st19) | READ_PROG for books, signs and inscriptions | script trigger | medium | small | yes |
| [ST20](ST-script-triggers.md#st20) | GENERATED_PROG after a generator runs | script trigger, script function and generator | medium | small | yes |
| [ST21](ST-script-triggers.md#st21) | First and repeat meetings: FIRSTGREET_PROG, REGREET_PROG, MEMORY() | script trigger and script function | medium | small | yes |
| [ST22](ST-script-triggers.md#st22) | SOCIAL_PROG: exact names and socials aimed at the mob | script trigger | medium | small | yes |
| [ST24](ST-script-triggers.md#st24) | EVERY_PROG: saved game-time timer that catches up | script trigger | medium | medium | yes |
| [ST25](ST-script-triggers.md#st25) | VAR_PROG: react when a variable meets a condition | script trigger | medium | medium | yes |
| [ST26](ST-script-triggers.md#st26) | AFFECT_PROG: effect added or worn off | script trigger | medium | medium | yes |
| [ST27](ST-script-triggers.md#st27) | DESTROY_PROG and DECAY_PROG for items | script trigger | medium | medium | yes |
| [ST29](ST-script-triggers.md#st29) | ACHIEVE_PROG: achievement earned | script trigger | low | small | yes |
| [SC1](SC-script-language-functions-and-commands.md#sc1) | Lists in variables: loop, add, remove, shuffle | script function and script command | high | small | yes |
| [SC2](SC-script-language-functions-and-commands.md#sc2) | Absolute game time, elapsed time and weekdays | script function | high | small | yes |
| [SC3](SC-script-language-functions-and-commands.md#sc3) | Load mobs and items from the catalog | script command and quest feature | high | small | yes |
| [SC4](SC-script-language-functions-and-commands.md#sc4) | Saved variables per object, not per name | script command and script function | high | medium | yes |
| [SC5](SC-script-language-functions-and-commands.md#sc5) | Read and change the message a script reacts to | script function and script command | high | medium | yes |
| [SC6](SC-script-language-functions-and-commands.md#sc6) | Signals and function calls between scripts | script command and script trigger, with a JavaScript hook | high | medium | yes |
| [SC7](SC-script-language-functions-and-commands.md#sc7) | MPWAIT: pause a script and resume it later | script command | high | medium | yes |
| [SC8](SC-script-language-functions-and-commands.md#sc8) | Party and terminal awareness in scripts | script function | high | medium | yes |
| [SC9](SC-script-language-functions-and-commands.md#sc9) | Bring the trigger and message-type help up to date | presentation | medium | small | yes |
| [SC10](SC-script-language-functions-and-commands.md#sc10) | String functions | script function | medium | small | yes |
| [SC11](SC-script-language-functions-and-commands.md#sc11) | Numbers, dice and weighted picks | script function | medium | small | yes |
| [SC12](SC-script-language-functions-and-commands.md#sc12) | Add, remove and count tags from scripts | script command and script function | medium | small | yes |
| [SC13](SC-script-language-functions-and-commands.md#sc13) | Script files with parameters | script function | medium | small | yes |
| [SC14](SC-script-language-functions-and-commands.md#sc14) | Fix the confirmed MOBPROG bugs | script function and command fixes, with a debug tool | medium | small | yes |
| [SC15](SC-script-language-functions-and-commands.md#sc15) | A CoffeeSUD edition of the Scriptable guide | presentation | medium | small | yes |
| [SC16](SC-script-language-functions-and-commands.md#sc16) | +ANYINGROUP and +ALLINGROUP mask keys | zapper mask key, used by behaviours and scripts | medium | small | yes |
| [SC17](SC-script-language-functions-and-commands.md#sc17) | ELSEIF, WHILE blocks, CONTINUE, named locals and parameters | script command | medium | medium | yes |
| [JS1](JS-javascript.md#js1) | Run world JavaScript without per-script approval | world option | high | small | yes |
| [JS2](JS-javascript.md#js2) | A time limit and readable errors for all JavaScript | debug tool and world option | high | small | yes |
| [JS3](JS-javascript.md#js3) | Shared JavaScript libraries with require() | JavaScript hook | high | small | yes |
| [JS5](JS-javascript.md#js5) | JavaScript classes and script files kept in world/ | data file and JavaScript hook | high | medium | yes |
| [JS6](JS-javascript.md#js6) | One `cm` helper object in every JavaScript scope | JavaScript hook and script function | high | medium | yes |
| [JS7](JS-javascript.md#js7) | Structured JavaScript state kept in saves | JavaScript hook and builder tool | high | medium | yes |
| [JS8](JS-javascript.md#js8) | World-wide JavaScript events with cm.on | JavaScript hook | high | medium | yes |
| [JS9](JS-javascript.md#js9) | Effects whose stat changes are written in JavaScript | property and behaviour | high | medium | yes |
| [JS10](JS-javascript.md#js10) | JS RELOAD: update changed JavaScript in the running world | builder tool | high | medium | yes |
| [JS11](JS-javascript.md#js11) | JScriptable: a Scriptable written wholly in JavaScript | behaviour | high | large | yes |
| [JS12](JS-javascript.md#js12) | JCONSOLE as a given mob, item or room | debug tool | medium | small | yes |
| [JS13](JS-javascript.md#js13) | Seeded randomness and game time in JavaScript | JavaScript hook | medium | small | yes |
| [JS14](JS-javascript.md#js14) | +JS and -JS entries in zapper masks | script function | medium | small | yes |
| [JS15](JS-javascript.md#js15) | JS() in conditions, MPJSFILE, and cancel from \<SCRIPT> | script function and script command | medium | small | yes |
| [JS16](JS-javascript.md#js16) | A single-player JavaScript guide and JS HELP | presentation and debug tool | medium | small | yes |
| [JS17](JS-javascript.md#js17) | Message, cancel and lookup helpers for JavaScript in scripts | JavaScript hook | medium | small | yes |
| [JS18](JS-javascript.md#js18) | JavaScript quest handlers and a quests helper | JavaScript hook, quest feature | medium | medium | yes |
| [JS19](JS-javascript.md#js19) | Game-clock scheduling from JavaScript | JavaScript hook | medium | medium | yes |
| [JS20](JS-javascript.md#js20) | Commands and local verbs from JavaScript | JavaScript hook | medium | medium | yes |
| [JS21](JS-javascript.md#js21) | Upgrade Rhino and fix the quote swap | JavaScript runtime upgrade | medium | medium | yes |
| [JS23](JS-javascript.md#js23) | A world JavaScript file for progression | JavaScript hook | medium | medium | yes |
| [Q1](Q-quests.md#q1) | Finished quests stay known | quest feature | high | small | yes |
| [Q2](Q-quests.md#q2) | Quest-giver and turn-in markers in room listings | property, world option | high | small | yes |
| [Q3](Q-quests.md#q3) | Quest journal: objectives, history and game time in QUESTS | script command, script function, presentation | high | medium | yes |
| [Q4](Q-quests.md#q4) | Per-character quest status and outcome | script function, script command | high | medium | yes |
| [Q5](Q-quests.md#q5) | Repeatable quests with win counts and game-time cooldowns | quest feature, script function | medium | small | yes |
| [Q6](Q-quests.md#q6) | Named steps and jumps for branching | quest feature | medium | small | yes |
| [Q7](Q-quests.md#q7) | Control re-offers and accept a declined quest later | property, player command | medium | small | yes |
| [Q8](Q-quests.md#q8) | Prop_QuestItem: bound quest items | property | medium | small | yes |
| [Q9](Q-quests.md#q9) | Quest QUIET fix and a quest-script help page | quest fix and documentation | medium | small | yes |
| [Q10](Q-quests.md#q10) | Declarative quest rewards, shown when offered | quest feature, script command | medium | medium | yes |
| [Q11](Q-quests.md#q11) | Per-character quest deadlines | script command, script trigger | medium | medium | yes |
| [Q12](Q-quests.md#q12) | Quest credit for the party or the whole profile | quest feature | medium | medium | yes |
| [Q13](Q-quests.md#q13) | Single-player story templates for the Quest Maker | data file | medium | medium | yes |
| [Q14](Q-quests.md#q14) | Quest board with generated jobs, refreshed daily | behaviour, generator | medium | medium | yes |
| [Q15](Q-quests.md#q15) | Undroppable main quests and a working drop hook | quest feature, script function | low | small | yes |
| [Q16](Q-quests.md#q16) | Objective locations and QUESTS WHERE | presentation, world option | low | small | yes |
| [N1](N-npc-behaviour-and-dialogue.md#n1) | WHEN= condition on every behaviour | behaviour | high | small | yes |
| [N2](N-npc-behaviour-and-dialogue.md#n2) | MPMENU: numbered dialogue choices that do not block | script command | high | medium | yes |
| [N3](N-npc-behaviour-and-dialogue.md#n3) | Schedule behaviour: daily routines over several places | behaviour | high | medium | yes |
| [N4](N-npc-behaviour-and-dialogue.md#n4) | Sleeper behaviour: sleeping NPCs woken by noise | behaviour | high | medium | yes |
| [N5](N-npc-behaviour-and-dialogue.md#n5) | Sentry behaviour: lookouts who spot sneakers and raise the alarm | behaviour | high | medium | yes |
| [N6](N-npc-behaviour-and-dialogue.md#n6) | Memory behaviour: NPCs remember what each character did | behaviour | high | medium | yes |
| [N7](N-npc-behaviour-and-dialogue.md#n7) | GenBehavior: named behaviours with parameters, defined as data | data file, behaviour, builder tool | high | medium | yes |
| [N8](N-npc-behaviour-and-dialogue.md#n8) | Catch-up hook when an area wakes | JavaScript hook, world option | high | medium | yes |
| [N9](N-npc-behaviour-and-dialogue.md#n9) | TALK command and TALK_PROG trigger | script trigger and command | high | medium | yes |
| [N10](N-npc-behaviour-and-dialogue.md#n10) | Replies arrive at once in turn-based time | world option | high | medium | yes |
| [N11](N-npc-behaviour-and-dialogue.md#n11) | MudChat: readable conditions, script effects and per-player saved state | behaviour, data file | high | medium | yes |
| [N12](N-npc-behaviour-and-dialogue.md#n12) | Prop_NPCLines: per-NPC wording for stock service lines | property | high | medium | yes |
| [N13](N-npc-behaviour-and-dialogue.md#n13) | Companion behaviour: NPC party members who take orders and comment | behaviour | high | large | [later](../LATER.md#n13) |
| [N14](N-npc-behaviour-and-dialogue.md#n14) | Dialogue trees: a Dialogue behaviour and .dlg files | behaviour, data file | high | large | [later](../LATER.md#n14) |
| [N15](N-npc-behaviour-and-dialogue.md#n15) | Prop_Affinity: one NPC's opinion of each character | property, script function, script command | high | large | yes |
| [N16](N-npc-behaviour-and-dialogue.md#n16) | Patroller waypoints that wait, act and change route by time | behaviour | medium | small | yes |
| [N17](N-npc-behaviour-and-dialogue.md#n17) | Warning before aggression | behaviour | medium | small | yes |
| [N18](N-npc-behaviour-and-dialogue.md#n18) | Fix the NPC behaviour bugs a world will hit | behaviour | medium | small | yes |
| [N19](N-npc-behaviour-and-dialogue.md#n19) | Fix the MudChat defects | behaviour | medium | small | yes |
| [N20](N-npc-behaviour-and-dialogue.md#n20) | Escortee: an NPC that travels with the party to a destination | behaviour | medium | medium | yes |
| [N21](N-npc-behaviour-and-dialogue.md#n21) | A configurable Hireling | behaviour | medium | medium | yes |
| [N22](N-npc-behaviour-and-dialogue.md#n22) | Event barks with conditions and cooldowns, in Sounder | behaviour | medium | medium | yes |
| [N23](N-npc-behaviour-and-dialogue.md#n23) | Rumours: a world list, gossiping NPCs, rumours from events | data file, behaviour and script command | medium | medium | [later](../LATER.md#n23) |
| [N24](N-npc-behaviour-and-dialogue.md#n24) | Topic keywords the player can learn and list | presentation | medium | medium | yes |
| [N25](N-npc-behaviour-and-dialogue.md#n25) | LLM calls off the game thread | behaviour, script command and world option | medium | medium | [later](../LATER.md#n25) |
| [N26](N-npc-behaviour-and-dialogue.md#n26) | LLM NPCs with lore files and saved memory per player | behaviour | medium | medium | [later](../LATER.md#n26) |
| [N27](N-npc-behaviour-and-dialogue.md#n27) | LLM NPCs that act from a declared whitelist | behaviour | medium | medium | [later](../LATER.md#n27) |
| [N28](N-npc-behaviour-and-dialogue.md#n28) | Scene: NPC exchanges the player overhears | behaviour | medium | medium | yes |
| [N29](N-npc-behaviour-and-dialogue.md#n29) | CHECK(): a skill check for dialogue | script function | low | small | yes |
| [C1](C-combat-and-encounters.md#c1) | Healing and support aimed at the ally who needs it | behaviour | high | small | yes |
| [C2](C-combat-and-encounters.md#c2) | Morale: mobs and groups that flee, rally or surrender | behaviour, script trigger | high | medium | yes |
| [C3](C-combat-and-encounters.md#c3) | BossPhases: boss phases that fire once per fight | behaviour, script trigger, script function | high | medium | yes |
| [C4](C-combat-and-encounters.md#c4) | Spawners with catalog weights, conditions and despawn | behaviour | high | medium | yes |
| [C5](C-combat-and-encounters.md#c5) | Telegraphed attacks: named, cancellable, saved alarms | script command, script function | high | medium | yes |
| [C6](C-combat-and-encounters.md#c6) | Prop_PartyScaler: scale fights to the party's size | property | high | medium | yes |
| [C7](C-combat-and-encounters.md#c7) | Checkpoints: per-character respawn points | property, script command, script function, world option | high | medium | yes |
| [C8](C-combat-and-encounters.md#c8) | A downed state and rules for when the whole party falls | world option, script trigger | high | medium | yes |
| [C9](C-combat-and-encounters.md#c9) | Random encounters on entering a room, from encounter tables | behaviour, data file, script command, quest feature | high | large | yes |
| [C10](C-combat-and-encounters.md#c10) | TargetPriority: which party member a monster attacks | behaviour | medium | small | yes |
| [C11](C-combat-and-encounters.md#c11) | Pursue: chase a fleeing target, then go home | behaviour | medium | small | yes |
| [C12](C-combat-and-encounters.md#c12) | Target masks and life steal on damage properties | property | medium | small | yes |
| [C13](C-combat-and-encounters.md#c13) | A difficulty setting and a player-side flag in combat formulas | world option | medium | small | yes |
| [C14](C-combat-and-encounters.md#c14) | Ini formulas for NPC armor, attack, damage, speed, rejuv and money | world option | medium | small | yes |
| [C15](C-combat-and-encounters.md#c15) | Prop_MobRank: minion, normal, elite and boss presets | property, script function | medium | small | yes |
| [C16](C-combat-and-encounters.md#c16) | Prop_DeathRules: death and defeat rules per area or room | property | medium | small | yes |
| [C17](C-combat-and-encounters.md#c17) | Essential and yielding mobs | property and script trigger | medium | small | yes |
| [C18](C-combat-and-encounters.md#c18) | Threat and target picking in scripts | script function and script command | medium | small | yes |
| [C19](C-combat-and-encounters.md#c19) | Respawns that follow the story | property | medium | medium | yes |
| [C20](C-combat-and-encounters.md#c20) | Wave and arena rooms | behaviour and script trigger | medium | medium | yes |
| [C21](C-combat-and-encounters.md#c21) | Leash and reset for bosses | behaviour and script trigger | medium | medium | yes |
| [C22](C-combat-and-encounters.md#c22) | Ability rotations for CombatAbilities | behaviour | medium | medium | yes |
| [C23](C-combat-and-encounters.md#c23) | Ambushes with a perception check and surprise | property and script trigger | medium | medium | yes |
| [C24](C-combat-and-encounters.md#c24) | Invasions and raids on an area | behaviour and script command | medium | large | [later](../LATER.md#c24) |
| [C25](C-combat-and-encounters.md#c25) | CONSIDER ALL for the whole fight | presentation | low | small | yes |
| [I1](I-items-properties-and-puzzles.md#i1) | Fix property bugs that make documented options do nothing | property fixes | high | small | yes |
| [I2](I-items-properties-and-puzzles.md#i2) | MPDOOR: change a door on both sides from anywhere, with a message | script command, script function | high | small | yes |
| [I3](I-items-properties-and-puzzles.md#i3) | Books with pages kept in the item and the world files | presentation, data file, script trigger | high | medium | yes |
| [I4](I-items-properties-and-puzzles.md#i4) | Prop_When: props and behaviours active only while a condition holds | property | high | medium | yes |
| [I5](I-items-properties-and-puzzles.md#i5) | Prop_Transform: things that change form over time or by condition | property | high | medium | yes |
| [I6](I-items-properties-and-puzzles.md#i6) | Item sets with tiers, any bonus, and progress shown | property, script function, script trigger | high | medium | yes |
| [I7](I-items-properties-and-puzzles.md#i7) | Prop_Cursed: hidden curses that Remove Curse ends cleanly | property | high | medium | yes |
| [I8](I-items-properties-and-puzzles.md#i8) | Prop_Invokable: item powers on demand, with recharging charges | property | high | medium | yes |
| [I9](I-items-properties-and-puzzles.md#i9) | Prop_Switch: levers, buttons and dials that keep their state | property, script trigger, script function | high | medium | yes |
| [I10](I-items-properties-and-puzzles.md#i10) | USE X ON Y and COMBINE A WITH B, with triggers and a table | player commands, script triggers, data file | high | medium | yes |
| [I11](I-items-properties-and-puzzles.md#i11) | Prop_Discoverable: hidden things anyone can find | property | high | medium | yes |
| [I12](I-items-properties-and-puzzles.md#i12) | PuzzleController: puzzle state, sequences and solve actions | behaviour, builder tool | high | large | [later](../LATER.md#i12) |
| [I13](I-items-properties-and-puzzles.md#i13) | Prop_BlockedExit: blockages the player must clear | property | medium | small | yes |
| [I14](I-items-properties-and-puzzles.md#i14) | Prop_Detector: an item that reacts to what is near | property | medium | small | yes |
| [I15](I-items-properties-and-puzzles.md#i15) | Receptacles: containers that react to the right objects | property | medium | small | yes |
| [I16](I-items-properties-and-puzzles.md#i16) | Prop_ComboLock: dials, codes and keypads | property | medium | small | yes |
| [I17](I-items-properties-and-puzzles.md#i17) | Riddler behaviour: riddles with several answers, tries and consequences | behaviour | medium | small | yes |
| [I18](I-items-properties-and-puzzles.md#i18) | Fix READ \<direction> so it reads writing on an exit | bug fix (player command) | medium | small | yes |
| [I19](I-items-properties-and-puzzles.md#i19) | Fix Prop_CloseCommand and let command phrases start with a real verb | bug fix (property) | medium | small | yes |
| [I20](I-items-properties-and-puzzles.md#i20) | GenTrap: choose what springs it, and run its script with no ability | property (and a bug fix) | medium | small | yes |
| [I21](I-items-properties-and-puzzles.md#i21) | Richer keys and locks: master keys, keys used up, unpickable locks | property (and a change to key matching) | medium | small | yes |
| [I22](I-items-properties-and-puzzles.md#i22) | MPTRANSFORM: turn an item into another in place | script command and JavaScript hook | medium | small | yes |
| [I23](I-items-properties-and-puzzles.md#i23) | Prop_Attunement: powers that wake only once the item is attuned | property and command | medium | medium | yes |
| [I24](I-items-properties-and-puzzles.md#i24) | Prop_ItemGrowth: items that grow with use | property | medium | medium | yes |
| [I25](I-items-properties-and-puzzles.md#i25) | Prop_PressurePlate: triggers on weight and presence | property and script functions | medium | medium | yes |
| [I26](I-items-properties-and-puzzles.md#i26) | Durability options: wear rate, broken instead of destroyed, unbreakable items | world option and property | medium | medium | yes |
| [I27](I-items-properties-and-puzzles.md#i27) | ItemUpgrader behaviour: upgrade paths at a smith or workbench | behaviour and property | medium | medium | yes |
| [W1](W-world-systems.md#w1) | Warrants and fines in scripts and masks | script function, script command | high | small | yes |
| [W2](W-world-systems.md#w2) | Currency file and a world default currency | data file, world option | high | small | yes |
| [W3](W-world-systems.md#w3) | Typed hazard zones: Prop_Hazard | property | high | medium | yes |
| [W4](W-world-systems.md#w4) | Letting time pass: long WAIT, WAIT UNTIL, MPPASSTIME | script command, builder tool | high | medium | yes |
| [W5](W-world-systems.md#w5) | Area calendars and the start date as content | world option, data file | high | medium | yes |
| [W6](W-world-systems.md#w6) | Holidays: more recurrences and tests by name | quest feature, script function | high | medium | yes |
| [W7](W-world-systems.md#w7) | RoomStates: named versions of a room | behaviour, script command | high | large | yes |
| [W8](W-world-systems.md#w8) | Tides for builders | script function, property | medium | small | yes |
| [W9](W-world-systems.md#w9) | ResetWhole: DAYS, EXCEPT and MSG | behaviour | medium | small | yes |
| [W10](W-world-systems.md#w10) | Darkness tests, and dangers in the dark | script function, property | medium | small | yes |
| [W11](W-world-systems.md#w11) | Darkness that torches cannot beat | property | medium | small | yes |
| [W12](W-world-systems.md#w12) | MPWEATHER, and WEATHER SET, FORECAST and NEXT | script command, builder tool | medium | small | yes |
| [W13](W-world-systems.md#w13) | Sky and weather lines per area: Prop_SkyText | property, presentation | medium | small | yes |
| [W14](W-world-systems.md#w14) | MPSHOP: remove, restock and reprice shop stock | script command, script function | medium | small | yes |
| [W15](W-world-systems.md#w15) | Bank accounts and debts in scripts | script function, script command | medium | small | yes |
| [W16](W-world-systems.md#w16) | Coin descriptions, materials and weights | data file | medium | small | yes |
| [W17](W-world-systems.md#w17) | Money formatting for scripts | script function | medium | small | yes |
| [W18](W-world-systems.md#w18) | A breath count before drowning | world option, property, script function | medium | medium | yes |
| [W19](W-world-systems.md#w19) | Floodable rooms driven by weather, tide or a variable | behaviour | medium | medium | yes |
| [W20](W-world-systems.md#w20) | Climates and weather texts as data, chosen per area | data file, world option, script command | medium | medium | yes |
| [W21](W-world-systems.md#w21) | Law book edits in builder mode saved to the world files | builder tool, data file | medium | medium | yes |
| [W22](W-world-systems.md#w22) | Custom seasons, seasonal day length and named moons | world option | medium | medium | yes |
| [W23](W-world-systems.md#w23) | Game-time schedules for quests | quest feature | medium | medium | yes |
| [W24](W-world-systems.md#w24) | Gradual and conditional restocking | property | medium | medium | [later](../LATER.md#w24) |
| [W25](W-world-systems.md#w25) | Exposure: body temperature from climate, shelter and clothing | behaviour, property, script function | medium | large | [later](../LATER.md#w25) |
| [W26](W-world-systems.md#w26) | Bounty hunters and wanted posters | behaviour, property | medium | large | [later](../LATER.md#w26) |
| [W27](W-world-systems.md#w27) | Market: supply and demand that remembers and recovers | behaviour, script command, script function | medium | large | [later](../LATER.md#w27) |
| [W28](W-world-systems.md#w28) | Prop_TimeMode: zones locked to turn-based or real time | property | low | small | yes |
| [W29](W-world-systems.md#w29) | Exchange rates that can change | behaviour, script command | low | small | yes |
| [G1](G-generators-and-random-content.md#g1) | Seeds for repeatable generation | world option | high | small | yes |
| [G2](G-generators-and-random-content.md#g2) | Fix RandomQuests hanging or never generating | behaviour | high | small | yes |
| [G3](G-generators-and-random-content.md#g3) | Depth and position variables for generated rooms | generator | high | small | yes |
| [G4](G-generators-and-random-content.md#g4) | GENERATE PREVIEW and GENLAYOUT: see before you build | builder tool | high | small | yes |
| [G5](G-generators-and-random-content.md#g5) | MPGENERATE command and GENSTRING function for MOBPROG | script command and script function | high | medium | yes |
| [G6](G-generators-and-random-content.md#g6) | Loot tables: Prop_LootTable on mobs and containers | property | high | medium | yes |
| [G7](G-generators-and-random-content.md#g7) | Guaranteed placements and reachable keys (ENSURE) | generator | high | medium | yes |
| [G8](G-generators-and-random-content.md#g8) | Hand-drawn MAP layout: drawn shape, generated content | generator | high | medium | yes |
| [G9](G-generators-and-random-content.md#g9) | GENERATE FILL: theme templates on hand-built rooms | builder tool | high | medium | yes |
| [G10](G-generators-and-random-content.md#g10) | Data-driven affixes for random enchantment | data file | high | medium | yes |
| [G11](G-generators-and-random-content.md#g11) | JavaScript generator helpers | JavaScript hook | medium | small | yes |
| [G12](G-generators-and-random-content.md#g12) | Name styles by culture and race | data file and script function | medium | small | yes |
| [G13](G-generators-and-random-content.md#g13) | Cleaner baking: unlinked areas, apostrophes, GENSOURCE, REGENERATE | builder tool | medium | medium | yes |
| [G14](G-generators-and-random-content.md#g14) | Regenerate behaviour and MPREGENERATE: areas that re-roll | behaviour and script command | medium | medium | yes |
| [G15](G-generators-and-random-content.md#g15) | New theme packs, set-piece library and theme skeleton | data file | medium | medium | yes |
| [G16](G-generators-and-random-content.md#g16) | Multi-level areas and new layouts (CAVE, CLUMP, LINEAR, RING, ISLANDS) | generator | medium | large | yes |
| [G17](G-generators-and-random-content.md#g17) | JavaScript in random-area templates | generator, JavaScript hook | low | medium | yes |
| [CR1](CR-crafting-gathering-and-housing.md#cr1) | Homes owned by the profile, and pre-owned homes | world option | high | small | yes |
| [CR2](CR-crafting-gathering-and-housing.md#cr2) | A world materials file with stable codes | data file | high | medium | yes |
| [CR3](CR-crafting-gathering-and-housing.md#cr3) | Workstations and tool wear | property and data file | high | medium | yes |
| [CR4](CR-crafting-gathering-and-housing.md#cr4) | Recipes a character knows | property, script command and script function | high | medium | yes |
| [CR5](CR-crafting-gathering-and-housing.md#cr5) | Recipe requirement keys | data file | medium | small | yes |
| [CR6](CR-crafting-gathering-and-housing.md#cr6) | Script functions and commands for property | script function and script command | medium | small | yes |
| [CR7](CR-crafting-gathering-and-housing.md#cr7) | Building without a deed where the world allows | property | medium | small | yes |
| [CR8](CR-crafting-gathering-and-housing.md#cr8) | Building skills as data | builder tool and data file | medium | small | yes |
| [CR9](CR-crafting-gathering-and-housing.md#cr9) | Weighted and masked finds for data gathering skills | data file | medium | small | yes |
| [CR10](CR-crafting-gathering-and-housing.md#cr10) | A mask column for hunting and fishing tables | data file | medium | small | yes |
| [CR11](CR-crafting-gathering-and-housing.md#cr11) | Seasonal room resources | behaviour | medium | small | yes |
| [CR12](CR-crafting-gathering-and-housing.md#cr12) | Crafting and gathering XP for every class | world option and property | medium | small | yes |
| [CR13](CR-crafting-gathering-and-housing.md#cr13) | A way home for the party | property, command and world option | medium | small | yes |
| [CR14](CR-crafting-gathering-and-housing.md#cr14) | Resource depletion and regrowth on game time, saved | property | medium | medium | yes |
| [CR15](CR-crafting-gathering-and-housing.md#cr15) | Named resource variants as data | data file | medium | medium | yes |
| [CR16](CR-crafting-gathering-and-housing.md#cr16) | Crop rules as data | property and data file | medium | medium | yes |
| [CR17](CR-crafting-gathering-and-housing.md#cr17) | Rolled quality tiers for crafted items | world option | medium | medium | yes |
| [CR18](CR-crafting-gathering-and-housing.md#cr18) | EXPERIMENT: discover recipes by trying materials | skill subcommand, recipe keys and world option | medium | medium | yes |
| [CR19](CR-crafting-gathering-and-housing.md#cr19) | Salvage and dismantle yields as data | property, skill subcommand and world option | medium | medium | yes |
| [CR20](CR-crafting-gathering-and-housing.md#cr20) | Furniture that changes its room: Prop_Furnishing | property | medium | medium | yes |
| [CR21](CR-crafting-gathering-and-housing.md#cr21) | LEARN for data crafting skills (GenCraftSkill) | data file | low | small | yes |
| [CR22](CR-crafting-gathering-and-housing.md#cr22) | Linked storage chests for the profile: Prop_SharedStash | property | low | medium | [later](../LATER.md#cr22) |
| [F1](F-factions-and-organisations.md#f1) | Clan rank and member data for a named clan | script function | high | small | yes |
| [F2](F-factions-and-organisations.md#f2) | MPCLANRELATION and clanrelation() for scripted diplomacy | script command, script function, builder tool | high | small | yes |
| [F3](F-factions-and-organisations.md#f3) | MPCONQUER and area ruler functions | script command, script function | high | small | yes |
| [F4](F-factions-and-organisations.md#f4) | Mask keys -CLANRANK and -RULER | property, behaviour (mask keys) | high | small | yes |
| [F5](F-factions-and-organisations.md#f5) | Fix MPFACTION band names and ability faction rules; add FACTIONVALUE | script command, script function | high | small | yes |
| [F6](F-factions-and-organisations.md#f6) | Faction band effects on the player, applied live | data file | high | medium | yes |
| [F7](F-factions-and-organisations.md#f7) | Clans as world content: world/clans.cmare | data file, builder tool | high | medium | yes |
| [F8](F-factions-and-organisations.md#f8) | Clan timers on game time, NPC members counted | world option | high | medium | yes |
| [F9](F-factions-and-organisations.md#f9) | Ranks earned by deeds: AUTOPROMOTEBY MERIT | data file | high | medium | yes |
| [F10](F-factions-and-organisations.md#f10) | GuildMaster behaviour for NPC-run guilds | behaviour | high | medium | yes |
| [F11](F-factions-and-organisations.md#f11) | Reputation per town, and law that heeds it | world option, data file | medium | small | yes |
| [F12](F-factions-and-organisations.md#f12) | ClanEnemyAggressive behaviour | behaviour | medium | small | yes |
| [F13](F-factions-and-organisations.md#f13) | Clan prices and rank-gated shop stock | property | medium | small | yes |
| [F14](F-factions-and-organisations.md#f14) | Hidden factions, band ladder and party standings table | presentation | medium | small | yes |
| [F15](F-factions-and-organisations.md#f15) | Clan news reaches the player | world option | medium | small | yes |
| [F16](F-factions-and-organisations.md#f16) | Dues on the game calendar, with consequences | data file | medium | medium | yes |
| [F17](F-factions-and-organisations.md#f17) | Clan standing generated from each NPC organization | world option and data file | medium | medium | yes |
| [F18](F-factions-and-organisations.md#f18) | CREATE GUILD generator and single-player governments | generator and data file | medium | medium | yes |
| [F19](F-factions-and-organisations.md#f19) | ClanWarfare behaviour: NPC clans raid and conquer | behaviour and world option | medium | large | [later](../LATER.md#f19) |
| [F20](F-factions-and-organisations.md#f20) | Party-founded clans: the profile votes once | world option and command | low | small | yes |
| [F21](F-factions-and-organisations.md#f21) | Deity holy days, favor gates and portents | property | low | medium | [later](../LATER.md#f21) |
| [F22](F-factions-and-organisations.md#f22) | Prop_ClanDisguise: pass as a member in uniform | property and script trigger | low | medium | [later](../LATER.md#f22) |
| [T2](T-travel-vehicles-and-instances.md#t2) | Dungeons that reset when the party comes back | behaviour | high | medium | yes |
| [T3](T-travel-vehicles-and-instances.md#t3) | Script commands to sail, anchor, dock, board and hitch | script command | high | medium | yes |
| [T4](T-travel-vehicles-and-instances.md#t4) | Travel time on rooms, exits and areas | property | high | medium | yes |
| [T5](T-travel-vehicles-and-instances.md#t5) | Timetabled ferry, coach and airship lines | behaviour | high | large | yes |
| [T6](T-travel-vehicles-and-instances.md#t6) | Journeys: passages that take hours, with events on the way | property, script trigger | high | large | yes |
| [T7](T-travel-vehicles-and-instances.md#t7) | Script functions for riding and vessels | script function, JavaScript hook | medium | small | yes |
| [T8](T-travel-vehicles-and-instances.md#t8) | Crew-only helm for world-owned vessels | property | medium | small | yes |
| [T9](T-travel-vehicles-and-instances.md#t9) | Builder-set vessel pacing | property | medium | small | yes |
| [T10](T-travel-vehicles-and-instances.md#t10) | DelayedTransporter with messages, fares, conditions and party handling | behaviour | medium | small | yes |
| [T11](T-travel-vehicles-and-instances.md#t11) | Unique interiors when vessels are copied, and a VESSEL command | builder tool | medium | small | yes |
| [T12](T-travel-vehicles-and-instances.md#t12) | Waypoint network: fast travel between places found | property or behaviour | medium | medium | yes |
| [T14](T-travel-vehicles-and-instances.md#t14) | Named ports, and COURSE TO a place | property, command | medium | medium | yes |
| [T16](T-travel-vehicles-and-instances.md#t16) | MovingRoom routes in the world files, with saved position | behaviour | medium | medium | yes |
| [T17](T-travel-vehicles-and-instances.md#t17) | Fares for the whole party | property and behaviour | low | small | yes |
| [T18](T-travel-vehicles-and-instances.md#t18) | Builder-marked roads for caravans | property | low | small | yes |
| [T19](T-travel-vehicles-and-instances.md#t19) | Sister stables for mounts | behaviour | low | medium | yes |
| [L1](L-languages-and-vocabulary.md#l1) | NPCs who understand only their own language | property | high | small | yes |
| [L2](L-languages-and-vocabulary.md#l2) | A glossary for renaming terms without regex | data file | high | medium | yes |
| [L3](L-languages-and-vocabulary.md#l3) | List overrides per area, room and mob (Prop_LocalLists) | property | high | medium | yes |
| [L4](L-languages-and-vocabulary.md#l4) | Script trigger and functions for understanding a language | script trigger and script function | high | medium | yes |
| [L5](L-languages-and-vocabulary.md#l5) | GenLanguage: dictionary files, suffixes, kept names, written name | data file | high | medium | yes |
| [L6](L-languages-and-vocabulary.md#l6) | Foreign text inside descriptions, exits, echoes and help | presentation | high | medium | yes |
| [L7](L-languages-and-vocabulary.md#l7) | GenRace HEALTHCHART and CORPSEBLURBS fields | data file | medium | small | yes |
| [L8](L-languages-and-vocabulary.md#l8) | Hit and miss messages per weapon | property | medium | small | yes |
| [L9](L-languages-and-vocabulary.md#l9) | Socials as world content, with pruning of stock ones | data file and builder tool | medium | small | yes |
| [L10](L-languages-and-vocabulary.md#l10) | MPLEARNLANG and MPSAYIN, and an MPENABLE fix | script command | medium | small | yes |
| [L11](L-languages-and-vocabulary.md#l11) | INTERPRETS for hearing and reading, with partial understanding | data file | medium | small | yes |
| [L12](L-languages-and-vocabulary.md#l12) | JavaScript hook for languages built by rule | JavaScript hook | medium | small | yes |
| [L13](L-languages-and-vocabulary.md#l13) | Learning a language by immersion | property | medium | small | yes |
| [L14](L-languages-and-vocabulary.md#l14) | World help with fixed priority and hidden stock entries | data file and builder tool | medium | small | yes |
| [P1](P-characters-progression-and-custom-abilities.md#p1) | GenAbility as a complete custom property | property | high | small | yes |
| [P2](P-characters-progression-and-custom-abilities.md#p2) | Exact level-ups from scripts: MPLEVELUP, MPLEVELDOWN, EXPTONEXT | script command and script function | high | small | yes |
| [P3](P-characters-progression-and-custom-abilities.md#p3) | Named lifecycle hooks and a cast-failure message for GenAbility scripts | script trigger | high | medium | yes |
| [P4](P-characters-progression-and-custom-abilities.md#p4) | GenAbility TARGETMODE: area, group and all-enemies abilities | data file (GenAbility field) | high | medium | yes |
| [P5](P-characters-progression-and-custom-abilities.md#p5) | Achievement rewards: SCRIPT, EFFECT, FACTION, VAR and RECIPE | data file (achievement rewards) | high | medium | yes |
| [P6](P-characters-progression-and-custom-abilities.md#p6) | Character origins (backgrounds) as data | data file | high | medium | yes |
| [P7](P-characters-progression-and-custom-abilities.md#p7) | Starting kits by class, origin or mask, with a choice of kit | data file | high | medium | yes |
| [P8](P-characters-progression-and-custom-abilities.md#p8) | Ready-made characters (templates) offered by NEW | data file and world option | high | medium | yes |
| [P9](P-characters-progression-and-custom-abilities.md#p9) | MPRECRUIT: an NPC met in the world joins the profile | script command and property | high | medium | yes |
| [P10](P-characters-progression-and-custom-abilities.md#p10) | World rules for the profile and party: size, new characters, roles, cheats | world option | high | medium | yes |
| [P11](P-characters-progression-and-custom-abilities.md#p11) | Prop_Discovery: XP and marks for discoveries, once per character, party or profile | property and world option | high | medium | yes |
| [P12](P-characters-progression-and-custom-abilities.md#p12) | Creation steps and questions as data | data file | high | large | yes |
| [P13](P-characters-progression-and-custom-abilities.md#p13) | Picks at level-up and at creation: perks, traits and flaws | data file | high | large | yes |
| [P14](P-characters-progression-and-custom-abilities.md#p14) | GenAbility action costs and components in the ability's record | data file (GenAbility fields) | medium | small | yes |
| [P15](P-characters-progression-and-custom-abilities.md#p15) | Starting level for characters made later in the game | world option | medium | small | yes |
| [P16](P-characters-progression-and-custom-abilities.md#p16) | Teachers gated by mask, rank and price, and a TEACHLIST view | behaviour | medium | small | yes |
| [P17](P-characters-progression-and-custom-abilities.md#p17) | Trains and practices per level as formulas | world option, data file | medium | small | yes |
| [P18](P-characters-progression-and-custom-abilities.md#p18) | Creation screens that follow the content, with locked choices shown | presentation | medium | small | yes |
| [P19](P-characters-progression-and-custom-abilities.md#p19) | Summons as a GenAbility field | data file | medium | medium | yes |
| [P20](P-characters-progression-and-custom-abilities.md#p20) | GenDisease: diseases as data | data file, builder tool | medium | medium | yes |
| [P21](P-characters-progression-and-custom-abilities.md#p21) | Scripts on poisons, tweaks and trade skills | data file, script trigger | medium | medium | yes |
| [P22](P-characters-progression-and-custom-abilities.md#p22) | Masks, effects and scripts on TWEAK abilities | data file | medium | medium | yes |
| [P23](P-characters-progression-and-custom-abilities.md#p23) | Retraining with refunds | property, script command | medium | medium | yes |
| [P24](P-characters-progression-and-custom-abilities.md#p24) | SKILLTREE view with prerequisites and hints | presentation | medium | medium | yes |
| [P25](P-characters-progression-and-custom-abilities.md#p25) | Rank ladders | data file, script command, script function | medium | medium | yes |
| [P26](P-characters-progression-and-custom-abilities.md#p26) | Effects for added stats | world option | medium | medium | yes |
| [P27](P-characters-progression-and-custom-abilities.md#p27) | Creation review with BACK, and a QUICK NEW | presentation | medium | medium | yes |
| [P28](P-characters-progression-and-custom-abilities.md#p28) | EXPDEFER and RPAWARDS timers on game time | world option | low | small | yes |
| [P29](P-characters-progression-and-custom-abilities.md#p29) | Ageing as data | data file, script trigger, world option | low | medium | yes |
| [P30](P-characters-progression-and-custom-abilities.md#p30) | Training that takes game time | world option | low | medium | [later](../LATER.md#p30) |
| [PR1](PR-presentation.md#pr1) | VARIES text on items, mobs, exits and areas | presentation | high | small | yes |
| [PR2](PR-presentation.md#pr2) | VARIES tags for world variables, quests and the party | presentation | high | small | yes |
| [PR3](PR-presentation.md#pr3) | First-visit text: FIRSTVISIT tag and Prop_FirstEntry | presentation and property | high | small | yes |
| [PR5](PR-presentation.md#pr5) | Title screen, game name and prologue from world data | presentation | high | small | yes |
| [PR6](PR-presentation.md#pr6) | A lore codex: LORE command, Prop_Lore and MPLORE | property, script command and presentation | high | medium | yes |
| [PR7](PR-presentation.md#pr7) | JOURNAL: saved notes, clues and an automatic chronicle | script command and presentation | high | medium | yes |
| [PR8](PR-presentation.md#pr8) | Prop_Details: extra descriptions by keyword | property | high | medium | yes |
| [PR9](PR-presentation.md#pr9) | A map for every player, with fog of war and room glyphs | presentation, property and world option | high | medium | yes |
| [PR10](PR-presentation.md#pr10) | Emoter: conditional, weighted, non-repeating ambient lines | behaviour | medium | small | yes |
| [PR11](PR-presentation.md#pr11) | Descriptions for dark rooms | property | medium | small | yes |
| [PR12](PR-presentation.md#pr12) | MPNARRATE: story text from files, with pauses | script command | medium | small | yes |
| [PR13](PR-presentation.md#pr13) | CALL tag: text computed by a script function | JavaScript hook and presentation | medium | small | yes |
| [PR14](PR-presentation.md#pr14) | Script variables in the prompt, and world-defined score lines | presentation | medium | small | yes |
| [PR15](PR-presentation.md#pr15) | LISTEN and TOUCH commands, with Prop_Senses | property and presentation | medium | small | yes |
| [PR16](PR-presentation.md#pr16) | In-game help for VARIES and the description tags | data file | medium | small | yes |
| [PR17](PR-presentation.md#pr17) | Named colour aliases | world option | medium | small | yes |
| [PR18](PR-presentation.md#pr18) | Prop_ShowWhen: things only some viewers see | property | medium | medium | yes |
| [PR19](PR-presentation.md#pr19) | Map items that reveal and mark rooms | property | medium | medium | yes |
| [PR20](PR-presentation.md#pr20) | Room header and footer templates | world option | low | small | yes |
| [PR21](PR-presentation.md#pr21) | Side panel in the character windows | presentation | low | medium | ? |
| [S1](S-saves-and-world-files.md#s1) | Quest state that survives SAVE and RESTORE | quest feature | high | small | yes |
| [S2](S-saves-and-world-files.md#s2) | Save rules: save points, no-save zones, no saving in combat, autosave | world option, property | high | small | yes |
| [S3](S-saves-and-world-files.md#s3) | world/world.ini for the settings that define the game | world option, builder tool | high | small | yes |
| [S4](S-saves-and-world-files.md#s4) | Riding kept in saves and world files | save format | high | small | yes |
| [S5](S-saves-and-world-files.md#s5) | SAVE keeps conquests | save format | high | small | yes |
| [S6](S-saves-and-world-files.md#s6) | A world/resources overlay for every data file | world option, data file | high | medium | yes |
| [S7](S-saves-and-world-files.md#s7) | Script engine state in the save: ONCE, TIME, DAY, DELAY, alarms, local variables | script trigger, save format, debug tool | high | medium | yes |
| [S8](S-saves-and-world-files.md#s8) | Generated quests survive save and restore | quest feature, generator | high | medium | yes |
| [S9](S-saves-and-world-files.md#s9) | A save hook for behaviour state: contracts, bribes, routes, dialogue | behaviour, save format | high | medium | yes |
| [S10](S-saves-and-world-files.md#s10) | Templates, NPC clans, cron jobs, board posts and VFS files in world/ | world option, builder tool | high | medium | yes |
| [S11](S-saves-and-world-files.md#s11) | One file per custom object, with scripts beside it | data file, builder tool | high | medium | yes |
| [S12](S-saves-and-world-files.md#s12) | Warrants, fines and criminal records in the save | save format | high | medium | yes |
| [S13](S-saves-and-world-files.md#s13) | World version in saves, a compatibility report and RESTORE ... FRESH= | builder tool, debug tool | high | medium | yes |
| [S15](S-saves-and-world-files.md#s15) | Vessels that save and restore exactly | save format | high | medium | yes |
| [S16](S-saves-and-world-files.md#s16) | Crops and crafts in progress survive a save | save rule | high | medium | yes |
| [S17](S-saves-and-world-files.md#s17) | Rooms and areas changed in play survive saves | save format | high | large | yes |
| [S18](S-saves-and-world-files.md#s18) | Generated dungeons survive a save | generator and save rule | high | large | yes |
| [S19](S-saves-and-world-files.md#s19) | Instances and planes survive a save | save rule | high | large | yes |
| [S20](S-saves-and-world-files.md#s20) | Opt out of stock achievements, titles and socials; wildcard disables | world option | medium | small | yes |
| [S21](S-saves-and-world-files.md#s21) | World cron jobs in files, on the game clock | world option and data file | medium | small | yes |
| [S22](S-saves-and-world-files.md#s22) | Save instance limits and planar visit timers | save rule | medium | small | yes |
| [S23](S-saves-and-world-files.md#s23) | Save tools: info, delete, copy, diff, checkpoints, autosave | debug tool and world option | medium | medium | yes |
| [S24](S-saves-and-world-files.md#s24) | Space objects in world files and saves | data file and save rule | medium | medium | yes |
| [S25](S-saves-and-world-files.md#s25) | Readable world files and a world diff | data file and builder tool | medium | large | yes |
| [B1](B-builder-tools.md#b1) | SAVE ROOM HOME: save each mob into its own start room | builder tool | high | small | yes |
| [B2](B-builder-tools.md#b2) | Protect hand edits: per-file checks, WORLD STATUS and WORLD DIFF | builder tool | high | small | yes |
| [B3](B-builder-tools.md#b3) | Multi-line editing of scripts and descriptions | builder tool | high | medium | yes |
| [B4](B-builder-tools.md#b4) | Track unsaved changes: LIST UNSAVED, SAVE CHANGED, warning on quit | builder tool | high | medium | yes |
| [B5](B-builder-tools.md#b5) | WORLD RELOAD of areas, scripts, quests and the catalog, and DEVWATCH | builder tool | high | medium | yes |
| [B6](B-builder-tools.md#b6) | UNDO, HISTORY and backups of world files | builder tool | high | medium | yes |
| [B7](B-builder-tools.md#b7) | Effects editor: edit in place, filtered list, duplicates | builder tool | medium | small | yes |
| [B8](B-builder-tools.md#b8) | GMODIFY and MQL UPDATE for exits and areas | builder tool | medium | small | yes |
| [B9](B-builder-tools.md#b9) | Dig mode and BUILD PATH for fast room creation | builder tool | medium | small | yes |
| [B10](B-builder-tools.md#b10) | MAP of an area, in the terminal and as HTML | presentation | medium | medium | yes |
| [B11](B-builder-tools.md#b11) | Macro files (RUNFILE) and a builder helper for JRUN | script command and JavaScript hook | medium | medium | yes |
| [B12](B-builder-tools.md#b12) | SANDBOX scratch area and PREVIEW of catalog objects | builder tool | low | small | yes |
| [B13](B-builder-tools.md#b13) | AHELP BUILDING and a builder summary at login | presentation | low | small | yes |
| [B14](B-builder-tools.md#b14) | CHATDRAFT: LLM drafts of fixed NPC dialogue | builder tool | low | small | [later](../LATER.md#b14) |
| [DT1](DT-testing-and-debugging-tools.md#dt1) | VARS: list, set, clear and watch script variables | debug tool and script function | high | small | yes |
| [DT2](DT-testing-and-debugging-tools.md#dt2) | QUESTINFO and quest test controls | builder tool and quest feature | high | small | yes |
| [DT3](DT-testing-and-debugging-tools.md#dt3) | World load report, and no overwrite of partly loaded files | debug tool | high | small | yes |
| [DT4](DT-testing-and-debugging-tools.md#dt4) | CHATTEST, CHATLINT and CHAT RELOAD | debug tool | high | small | yes |
| [DT5](DT-testing-and-debugging-tools.md#dt5) | DEV=1: a normal game with saves and developer powers | world option | high | small | yes |
| [DT6](DT-testing-and-debugging-tools.md#dt6) | Prop_DevCheats and GOD toggles | property and debug tool | high | small | yes |
| [DT7](DT-testing-and-debugging-tools.md#dt7) | SCRIPT FIRE, CALL, RESET: run a host's triggers on demand | debug tool | high | medium | yes |
| [DT8](DT-testing-and-debugging-tools.md#dt8) | PLAYTEST: meet the world as a mortal from builder mode | builder tool | high | medium | yes |
| [DT9](DT-testing-and-debugging-tools.md#dt9) | DEVLOG: live errors and debug lines for the builder | debug tool | high | medium | yes |
| [DT10](DT-testing-and-debugging-tools.md#dt10) | Script errors with file and line, kept from the player | debug tool | high | medium | yes |
| [DT11](DT-testing-and-debugging-tools.md#dt11) | SCRIPTCHECK: a static checker for MOBPROG and JavaScript | builder tool and debug tool | high | medium | yes |
| [DT12](DT-testing-and-debugging-tools.md#dt12) | PROPCHECK: check every property string in the world | builder tool | high | medium | yes |
| [DT13](DT-testing-and-debugging-tools.md#dt13) | WORLD CHECK: one lint of the whole world, also headless | debug tool | high | medium | yes |
| [DT14](DT-testing-and-debugging-tools.md#dt14) | GENERATE CHECK: lint for generator XML | debug tool and generator | high | medium | yes |
| [DT15](DT-testing-and-debugging-tools.md#dt15) | Playtest files with assertions, and a runner that fails | builder tool and data file | high | medium | yes |
| [DT16](DT-testing-and-debugging-tools.md#dt16) | TESTCHAR and KIT: test characters and loadouts as data | builder tool and data file | high | medium | yes |
| [DT17](DT-testing-and-debugging-tools.md#dt17) | PREVIEW and CHECKTEXT for conditional text | builder tool, presentation | high | medium | yes |
| [DT18](DT-testing-and-debugging-tools.md#dt18) | Script trace for one host, shown to the builder | debug tool, script command, script function | medium | small | yes |
| [DT19](DT-testing-and-debugging-tools.md#dt19) | STAT BEHAVIORS: an NPC's live behaviour state | debug tool | medium | small | yes |
| [DT20](DT-testing-and-debugging-tools.md#dt20) | WEATHER SET, TIME SET, LOOK AS and HAZARDS | builder tool, debug tool | medium | small | yes |
| [DT21](DT-testing-and-debugging-tools.md#dt21) | LAW: inspect and test an area's legal system | builder tool | medium | small | yes |
| [DT22](DT-testing-and-debugging-tools.md#dt22) | PRICECHECK: how a price was reached | debug tool | medium | small | yes |
| [DT23](DT-testing-and-debugging-tools.md#dt23) | TIMESCALE: run real time faster or slower | world option, builder tool | medium | small | yes |
| [DT24](DT-testing-and-debugging-tools.md#dt24) | MPDEBUG and a JavaScript debug() for builder-only output | script command, JavaScript hook | medium | small | yes |
| [DT25](DT-testing-and-debugging-tools.md#dt25) | RECORD INPUT: a replayable playtest file | debug tool | medium | small | yes |
| [DT26](DT-testing-and-debugging-tools.md#dt26) | NOTE: playtest notes that reach builder mode | builder tool | medium | small | yes |
| [DT27](DT-testing-and-debugging-tools.md#dt27) | world/dev/: areas loaded only for building and testing | world option | medium | small | yes |
| [DT28](DT-testing-and-debugging-tools.md#dt28) | VESSEL STATUS and LIST INSTANCES | debug tool | medium | small | yes |
| [DT29](DT-testing-and-debugging-tools.md#dt29) | VALIDATE RECIPES | debug tool | medium | small | yes |
| [DT30](DT-testing-and-debugging-tools.md#dt30) | VOCAB: test translation and parser rules | debug tool, builder tool | medium | small | yes |
| [DT31](DT-testing-and-debugging-tools.md#dt31) | LANGUAGE PREVIEW and COVERAGE for invented languages | debug tool | medium | small | yes |
| [DT32](DT-testing-and-debugging-tools.md#dt32) | QUESTCHECK: check a quest script without starting it | debug tool, quest feature | medium | medium | yes |
| [DT33](DT-testing-and-debugging-tools.md#dt33) | PUZZLE command: see, test, reset and check puzzle wiring | builder tool | medium | medium | yes |
| [DT34](DT-testing-and-debugging-tools.md#dt34) | SCRIPTPROF: timing per script and trigger, slow-script warnings | debug tool | medium | medium | yes |
| [DT35](DT-testing-and-debugging-tools.md#dt35) | COMBATDEBUG: explain each roll while tuning | debug tool | medium | medium | yes |
| [DT36](DT-testing-and-debugging-tools.md#dt36) | PROGRESSION report: the level curve and the XP the content gives | debug tool | medium | medium | yes |
| [DT37](DT-testing-and-debugging-tools.md#dt37) | ORGCHECK: check faction files, governments and clan references | builder tool | medium | medium | yes |
| [DT39](DT-testing-and-debugging-tools.md#dt39) | DETERMINISTIC=1: exact replay for playtests | world option | medium | large | [later](../LATER.md#dt39) |
| [DT40](DT-testing-and-debugging-tools.md#dt40) | SIMFIGHT: simulate the real party against an encounter | debug tool | medium | large | [later](../LATER.md#dt40) |

## Merged items

These IDs were merged into another item, which now covers them.

| Merged | Into |
|---|---|
| T15 | [CR1](CR-crafting-gathering-and-housing.md#cr1) Homes owned by the profile, and pre-owned homes |
| DT38 | [DT15](DT-testing-and-debugging-tools.md#dt15) Playtest files with assertions, and a runner that fails |
| T13 | [G1](G-generators-and-random-content.md#g1) Seeds for repeatable generation |
| JS22 | [JS6](JS-javascript.md#js6) One `cm` helper object in every JavaScript scope |
| S14 | [S6](S-saves-and-world-files.md#s6) A world/resources overlay for every data file |
| CR23 | [S8](S-saves-and-world-files.md#s8) Generated quests survive save and restore |
| PR4 | [ST16](ST-script-triggers.md#st16) Area border triggers and an area entry banner |
| ST23 | [ST6](ST-script-triggers.md#st6) GAME_PROG and TURN_PROG: new game, save, restore, switch, pause and turns |
| ST28 | [ST6](ST-script-triggers.md#st6) GAME_PROG and TURN_PROG: new game, save, restore, switch, pause and turns |
| JS4 | [ST6](ST-script-triggers.md#st6) GAME_PROG and TURN_PROG: new game, save, restore, switch, pause and turns |
| T1 | [W4](W-world-systems.md#w4) Letting time pass: long WAIT, WAIT UNTIL, MPPASSTIME |

## Already possible

What the audit first proposed and then found a builder can already do, with how.

**JavaScript runs without approval** (javascript, sp-dev-tooling). Set `JSCRIPTS=2` in `CoffeeMud/coffeemud.ini` (line 239, whose comment already calls 2 the default), or start with `sud.sh JSCRIPTS=2`. `CMSecurity.isApprovedJScript` then returns true for every `Scriptable`, `GenAbility`, `GenTrap` and quest `<SCRIPT>`, in builder mode and in normal games, however often the text changes. `JSCRIPTS=0` turns JS off. `JRUN`, `JCONSOLE` and cron JS are never checked. A clumsier route is `hash=anything` lines in `resources/jscripts.ini`. Two corrections for the plan:
- Mode 1 has a hole. An unapproved script's hash is stored with its text (`CMSecurity.java:1080-1083`), so each block is refused only the first time it runs after a boot, with the room message once, and runs from then on. Quest `<SCRIPT>`s fail on that first setup.
- `approveJScript` keeps only approvers who exist as players, so approvals made in builder mode can vanish.

What is left is small: make `JSCRIPTS=2` the fork's default, or add a builder-only mode, and fix or remove mode 1.

**Changing a message's amount** (character-progression). JavaScript can do it now. In a `CNCLMSG_PROG`, `objs()[0]` is the live message (documented in `guides/Scriptable.html`). For example, `cnclmsg_prog EXPCHANGE all` with a `<SCRIPT>` block that takes `var m=objs()[0]` and calls `m.setValue(m.value()*2)` when `HASTATTOO("$n WELLRESTED")` is true. `Train.java` and `postExperience` read `msg.value()` after the check, so the new amount takes effect. The same works for `TRAIN` and `DAMAGE`. Put the script on a room, an area, a parent area (with `DEFAULTPARENTAREA` that covers the whole world) or a carried item. Only a MOBPROG-native `MPMSGVALUE` and `$v` would be new, as a convenience.

**Commands: wrapping and synonyms** (data-content, vocabulary-languages).
- Wrap a stock command: `CREATE COMMAND Sleep` makes a `GenCommand` that overrides it. Its `FUNCTION_PROG EXECUTE` script runs the stock class from a `<SCRIPT>` block, keeps the result in `objs()[0]`, and ends with `RETURN $0`. MOBPROG lines placed before the block act as a "before" hook (they can `RETURN false`, with the arguments in `$1..$n`). Lines between `</SCRIPT>` and `RETURN $0` act as an "after" hook and can test `$0`. `GenCommand` scripts skip the approval check, though `JSCRIPTS=0` still turns them off. To wrap a command in one place only, use `CNCLMSG_PROG` (before) and `EXECMSG_PROG` (after).
- Synonyms: `CREATE COMMAND Inventory`, then set `ACCESS` to `INVENTORY,INV,I,SATCHEL`. The command is saved in `world/custom.cmare`, and `CMClass.reloadCommandWords()` registers the words. A new word, such as `CREATE COMMAND STOW`, gets a script whose lines run as the player's commands (`$g.N` holds each argument). For input rewriting, the `[command-pre-processor]` section of the parser file can map `^larboard$` to `west` without losing `WEST`. `DISABLE=COMMAND_<ID>` hides stock words.

A `WRAPS` field or an `X = Y` line format would only make this tidier.

**Place rules and senses** (properties).
- Forbidding an action somewhere: a `CNCLMSG_PROG` on the room's or area's `Scriptable`, for example `cnclmsg_prog SLEEP all` / `if !ISLIKE($n -EFFECTS +Spell_Warmth)` / `mpechoat $n It is far too cold to sleep here.` / `return cancel` / `endif` / `~`. An area script covers all its rooms. The message codes in `CMMsg.TYPE_DESCS` include `SLEEP`, `SIT`, `EAT`, `DRINK`, `SPEAK`, `CAST_SPELL`, `READ`, `RECALL` and `WRITE`, and exceptions go in `IF` lines (`ISLIKE`, `QUESTWINNER`, `HASTATTOO`). To reuse a rule, use `Scriptable LOAD=file`. Ready-made cases: `Prop_RestrictSpells`, `Prop_NoRecall`, `Prop_PeaceMaker`, `Prop_NoTelling`. Lighting has no message type of its own. At most, document the pattern.
- LISTEN and TOUCH: `Prop_Socials` on a room, area or exit with `NAME=LISTEN SRCCODE=S TGTCODE=S YOUSEE='Water drips somewhere below.'` and an optional `MASK=`. `TOUCH` is already a social for items and exits, so a `SOCIAL_PROG` can answer it and test `ISNIGHT` and the like. `Prop_Smell` covers smell. Caveats: `Thief_Listen` takes the word LISTEN for characters who have that skill, and social text has no chance or `<VARIES>`. Adding `<VARIES>` parsing to social text is the only change worth making.

**Reloading edited scripts** (builder-commands). An Archon can type any `MP` command directly. `MPUNLOADSCRIPT progs/sage.script` drops every parsed copy of that file. `UNLOAD RESOURCE PARSEDPRG` drops all parsed scripts, and `UNLOAD RESOURCE sage.script` works too, because it matches by substring. Script files are read again on each parse, and the reparse also resets the once, delay, time and day state. Two small gaps remain:
- `MPUNLOADSCRIPT` misses scripts whose parameters exceed 100 characters, and it does not follow nested `LOAD=` files. `UNLOAD RESOURCE PARSEDPRG` covers both.
- There is no automatic reload when a file changes, which an optional `SCRIPTWATCH` could add.

Otherwise document this in `AHELP` rather than build a new command.
