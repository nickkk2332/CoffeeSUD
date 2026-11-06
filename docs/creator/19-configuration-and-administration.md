# Configuration and administration

[Handbook](README.md)

This chapter explains how to configure a CoffeeSUD installation and how to
administer a running game. It covers `coffeemud.ini` (the engine's main
settings file), the world's own `world.ini`, command-line overrides, the
security model (security flags, groups, Archon, builder mode and the DEV
game), the developer cheats (`GOD`), the administrator commands that still
work, logs and maintenance, the content database as a cache, and the
performance knobs that still apply. It is for creators who run their own
world, and for engine developers who need to know where a setting is read.

CoffeeSUD is a single-player fork of CoffeeMud 5.11. Most of the original
administration manual (`CoffeeMud/guides/ArchonGuide.html`) was about running
a public server: ports, accounts, bans, e-mail, web pages, intermud chat. All
of that is gone or inert. This chapter says what survives and says so
plainly when an inherited setting no longer does anything.

## In this chapter

- [The configuration layers](#the-configuration-layers)
- [Who has power: the four ways to play](#who-has-power-the-four-ways-to-play)
- [coffeemud.ini](#coffeemudini)
  - [File format and when it is read](#file-format-and-when-it-is-read)
  - [Settings the launcher forces](#settings-the-launcher-forces)
  - [Launch and mode keys](#launch-and-mode-keys)
  - [Time and calendar keys](#time-and-calendar-keys)
  - [Saving, party and profile keys](#saving-party-and-profile-keys)
  - [Quest, NPC and dialogue keys](#quest-npc-and-dialogue-keys)
  - [New-character keys](#new-character-keys)
  - [Death, combat and survival keys](#death-combat-and-survival-keys)
  - [Skill and ability keys](#skill-and-ability-keys)
  - [Start, death and morgue rooms](#start-death-and-morgue-rooms)
  - [World, economy and crafting keys](#world-economy-and-crafting-keys)
  - [Display and text keys](#display-and-text-keys)
  - [Formula keys](#formula-keys)
  - [Class path keys](#class-path-keys)
  - [Database keys](#database-keys)
  - [Inert keys](#inert-keys)
- [world.ini, coffeemud.ini and launch overrides](#worldini-coffeemudini-and-launch-overrides)
- [Security](#security)
  - [SYSOPMASK and the Archon](#sysopmask-and-the-archon)
  - [Security flags](#security-flags)
  - [Security groups](#security-groups)
  - [Area staff (subops)](#area-staff-subops)
  - [File access flags](#file-access-flags)
  - [Giving a character flags or groups](#giving-a-character-flags-or-groups)
  - [How a command decides whether you may use it](#how-a-command-decides-whether-you-may-use-it)
  - [Playtest and suspended powers](#playtest-and-suspended-powers)
  - [JavaScript approval](#javascript-approval)
- [GOD and the developer cheats](#god-and-the-developer-cheats)
- [Administrator commands that still work](#administrator-commands-that-still-work)
- [Logs, DEVLOG and diagnostics](#logs-devlog-and-diagnostics)
- [Maintenance tasks](#maintenance-tasks)
- [The content database is a cache](#the-content-database-is-a-cache)
- [Performance and threading](#performance-and-threading)
- [DISABLE, ENABLE, DEBUG, SAVE and NOCACHE flags](#disable-enable-debug-save-and-nocache-flags)
- [CoffeeMud administration that no longer applies](#coffeemud-administration-that-no-longer-applies)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting](#troubleshooting)
- [See also](#see-also)

## The configuration layers

A running game's settings come from several places. Later layers win over
earlier ones.

| Order | Layer | File or source | Holds |
| --- | --- | --- | --- |
| 1 | Engine defaults | `CoffeeMud/coffeemud.ini` | Everything: engine, rules, display, logging, security |
| 2 | Launcher settings | built into `SUD.java` | A few single-player values that replace the ini (see [Settings the launcher forces](#settings-the-launcher-forces)) |
| 3 | World settings | `WORLDDIR/world.ini` (default `world/world.ini`) | Only the [allowlisted](world-settings.md) keys that make the world the game it is |
| 4 | Launch overrides | `NAME=VALUE` words after `sud.sh` | Anything |
| 5 | Saved game | the save's header | The few values a save carries (time mode, save mode, party options) |

The launcher (`SUD.main`) reads `coffeemud.ini` itself, adds the world
settings and the command line, and hands the result to the engine as
command-line overrides. The consequence: **a value from world.ini or the
command line also survives `RESET INIFILE`**, which reloads only the file.

Source: [SUD.java](../../CoffeeMud/com/planet_ink/coffee_mud/application/SUD.java)
(`readSettings`, `main`), [CMProps.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/CMProps.java)
(`getStr`, `resetSystemVars`), [WorldSettings.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldSettings.java).

Rule of thumb for a creator:

- A setting that defines **the game you are making** (start room, calendar,
  death rules, formulas, new-character rules) belongs in `world/world.ini`,
  so it travels with the world.
- A setting that belongs to **this machine or this session** (logs, scripting
  limits, editor, builder autosave, `BUILDER`, `DEV`) belongs in
  `coffeemud.ini` or on the command line.

## Who has power: the four ways to play

CoffeeSUD has no administrator accounts. What a character may do depends on
how the game was started.

| Mode | How to start | What the character is | Admin and builder commands |
| --- | --- | --- | --- |
| Normal game | `./sud.sh` | An ordinary character. Not a SysOp. | Only what the character's class or security flags grant (usually nothing). |
| Builder mode | `BUILDER=1` | An Archon, made so by reading the Archon's manual when the player comes in. | Every command: an Archon matches `SYSOPMASK`. Edits go to the world files; there are no saved games. |
| DEV game | `DEV=1` | An ordinary character who also has the security group `DEV` (or the group `DEVGROUP` names). | The DEV group's flags, plus the commands that test `SaveGame.isDevMode()` (`GOD`, `DEVLOG`, `ASSERT`, `TIMESCALE` and others). Saves are marked `[dev]`. |
| Playtest | `PLAYTEST ON` in builder mode | The Archon with every sysop power suspended. | None, except `BUILD [command]` which runs one command with powers back. |

`DEV=1` in builder mode is ignored with a message: builder mode already has
every power. `BUILDER=1` also writes world files; `DEV=1` never writes them.

Sources: [SUD.java](../../CoffeeMud/com/planet_ink/coffee_mud/application/SUD.java),
[SaveGame.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/save/SaveGame.java)
(`makeArchon`, `devGroup`, `applyDevGroup`, `isDevMode`),
[CMSecurity.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/CMSecurity.java).
Launching and the mode flags are covered in [Installing and running](18-installing-and-running.md).

## coffeemud.ini

### File format and when it is read

`CoffeeMud/coffeemud.ini` is a plain text file of `NAME=VALUE` lines.

- Lines starting with `#` or `!` are comments. Blank lines are ignored.
- Names are upper-cased when the launcher reads them. Spaces around the
  `=` are removed.
- A value may continue on the next line if the line ends in a backslash `\`
  (the `GROUP_`, `BADNAMES`, `CHANNELS`, `STARTINGITEMS` and `TRAINCOSTS`
  entries do this). The launcher's own early read does not join continuation
  lines; it only needs short single-line keys (`BUILDER`, `SAVEDIR`,
  `WORLDDIR`, `GAMENAME`, `DEV`, `WINDOWS`, `CHECK`, `RESTORE`, `FRESH`,
  `MODULES`, `MODULESETTINGS`).
- HTML entities in a value are converted when the engine reads it.
- The file must be on the local file system, in the folder you start the
  game from (`CoffeeMud/`). Its own header says never to put it in the
  database file system.

The file is read:

1. By the launcher, before the engine starts, for the keys above.
2. By the engine at boot, completely.
3. Again on demand by `RESET INIFILE` (see
   [Maintenance tasks](#maintenance-tasks)). That reloads the file, re-reads
   the system variables and the channel definitions, and re-applies
   `DISABLE`, `ENABLE`, `DEBUG` and `SAVE`. It does **not** re-read
   `GROUP_` security groups or `SYSOPMASK`; restart for those. Several keys
   (calendar, `START`, formulas that were already compiled) are safest
   changed with a restart.

To see what the game is actually using, type `WORLD` (alias `LIST
WORLDINI`). It lists every world-settable key with its value and the layer
it came from (`coffeemud.ini`, `world.ini` or the command line).

### Settings the launcher forces

Before the world settings and the command line, the launcher adds these
values. They replace what `coffeemud.ini` says, because they are given to the
engine as overrides. You can still override them with `world.ini` (when the
key is allowlisted) or on the command line.

| Key | Forced value | Why |
| --- | --- | --- |
| `COMMONACCOUNTSYSTEM` | `50` | The profile is an account that holds all the player's characters (up to 50 unless `PROFILEMAX` says less). |
| `EMAILREQ` | `DISABLED` | No e-mail is asked for. |
| `IDLETIMERS` | `0 0` | No idle warning or idle log-off. |
| `SYSMSGS`, `ERRMSGS`, `WRNMSGS`, `DBGMSGS` | `FILE` | Log lines go to `mud.log` and not to the player's terminal. The ini says `BOTH`; that value is overridden. |

This is why a change to one of these four log settings in `coffeemud.ini`
appears to be ignored. They are not world.ini keys, so the only way to
change them is the command line (`./sud.sh DBGMSGS=BOTH`). The same holds
for the other forced keys unless they are allowlisted.

### Launch and mode keys

Read by the launcher (`SUD.java`) unless stated.

| Key | Shipped | Meaning |
| --- | --- | --- |
| `BUILDER` | blank | `1`, `Y...`, `T...` or `ON` plays on the database cache itself, makes the player an Archon with the in-game editors, writes edits back to the world files, and has no saved games. Blank is a normal game. |
| `DEV` | not in ini (commented) | `1`, `Y...`, `T...` or `ON` starts a developer's game (see above). |
| `DEVGROUP` | not set | Name of the security group a DEV game gives each character; `GROUP_` before the name is accepted and removed. Default `DEV`. |
| `DEVSTART` | not set | Room ID where a new character starts in builder mode and DEV games (for example a dev hub). Ignored in a normal game. |
| `DEVLOG` | not set | Initial DEVLOG level and module mask for DEV games and as a fallback in builder mode; see [Logs, DEVLOG and diagnostics](#logs-devlog-and-diagnostics). |
| `NOTESFILE` | not set | File the `NOTE` command writes playtest notes to; default `notes/playtest.txt`. |
| `SCRIPTERRORS` | not set (`LOG`) | What a normal game does with a script error: `LOG`, `NOTICE` (tell each player once) or `SHOW` (show the error to the room). |
| `CHECK` | launch only | `1` loads the world, checks it, and ends the game with a result code. |
| `RESTORE` | blank | The save to restore without asking; `NEW` starts a new game without asking. |
| `FRESH` | launch only | With a restore: `CHANGED`, `ALL` or area names, to take those areas from the world files and not from the save. |
| `WORLDDIR` | `world` | Folder of world files. |
| `SAVEDIR` | `saves` | Folder of saved games. Only one running game may use a save folder at a time (`SaveSlots.acquire` takes a lock; a second launch is refused). |
| `GAMENAME` | `CoffeeSUD` | The game's name for the title screen and `@MudInfo?NAME@`. World allowlisted. |
| `MUD_NAME` | `ROTLOVE` | The engine's internal name, used in log and shutdown text. World allowlisted. Leave it unless you want it in `SHUTDOWN` messages. |
| `WINDOWS` | blank | `1` gives every character other than the one in the terminal a window of its own. Needs a display. |
| `MODULES`, `MODULESETTINGS` | commented | Optional gameplay modules; prefer `world.ini`. See [Optional modules](14-optional-modules.md). |
| `TIMEMODE` | `REALTIME` | `REALTIME` or `TURNS`. World allowlisted. A restored game keeps its saved mode. |
| `TIMESCALE` | `1` | Real-time speed from `0.1` to `20`. `TIMESCALE` the command changes it in builder mode or a DEV game. |
| `RANDOMSEED` | blank | A whole number seeds the dice at world load and again at play start. |
| `RECORDINPUT` | `0` | `1`, `ON` or `TRUE` records typed input to a playtest file; see [Testing and release](13-testing-release.md). |
| `SCRIPTSLOW` | `0` | Milliseconds; a script trigger that runs longer writes a warning to `mud.log`. `0` disables. |
| `EDITOR` | `LINE` | Where builders edit scripts and descriptions: `LINE`, `WINDOW` or `EXTERNAL`. |
| `EDITORCOMMAND` | blank | Program for `EXTERNAL`; falls back to `$VISUAL`, then `$EDITOR`. |
| `BUILDERAUTOSAVE` | `0` | `1` saves the mobs and items of each room a building command changed, after each command. |
| `BACKUPS` | `5` | How many copies of each world file `SAVE` keeps in `world/.backup/`. `0` keeps none. |
| `JSCRIPTS` | `2` | JavaScript approval policy; see [JavaScript approval](#javascript-approval). |
| `JSTIMELIMIT` | `2` | Seconds a JavaScript block may run before it is stopped. |
| `JSTIMELIMIT_JRUN` | `30` | The same for `JRUN` scripts. |
| `WINDOWPANEL` | commented | Panel sections beside each window: `STATUS`, `MAP`, `PARTY`. |

Only `1` turns on `PARTYMEET`, `PARTYFIGHT` and `BUILDERAUTOSAVE` (the code
compares the value to the string `1`). `BUILDER`, `DEV` and `WINDOWS` accept
`1`, any word starting with `Y` or `T`, or `ON`.

### Time and calendar keys

Mostly world-allowlisted. The calendar keys are for the default clock only;
an area may carry its own calendar (see [World simulation](10-world-simulation.md)).

| Key | Shipped | Meaning |
| --- | --- | --- |
| `TICKTIME` | `4000` | Milliseconds per engine tick. Values under 500 become 4000. See [Performance and threading](#performance-and-threading). Engine-only (not allowlisted). |
| `MILLISPERMUDHOUR` | `600000` | Real milliseconds in one game hour. A value smaller than `TICKTIME` becomes 600000. |
| `HOURSINDAY` | `6` | Hours in a game day. |
| `DAYSINMONTH` | `24` | Days in a month. |
| `MONTHSINYEAR` | seven ordinal names | Comma-separated month names. |
| `DAYSINWEEK` | blank | Comma-separated weekday names. |
| `DAWNHR`, `DAYHR`, `DUSKHR`, `NIGHTHR` | `0`, `1`, `4`, `5` | The hour each part of the day starts; `-1` removes that part. |
| `YEARDESC` | `year #` | Year names; `#` is replaced by the number. |
| `STARTDATE` | blank | `YEAR/MONTH/DAY/HOUR` a new game starts at; blank is the engine's own. A save continues from its own date. |
| `LONGWAIT` | `ALL` | Who may `WAIT` longer than 100 ticks: `ALL` or `BUILDERS`. |
| `BREATHTICKS` | `@x1/3` | Ticks a mob can go without air, a formula (`@x1` constitution, `@x2` level). |
| `DIALOGUETIME` | blank | `INSTANT` or `TICKS` for NPC replies; blank is instant in turn-based time, ticks in real time. |
| `CATCHUP` | blank | `OFF` stops an awakened area's NPCs from catching up on what they would have done. |

### Saving, party and profile keys

| Key | Shipped | Meaning |
| --- | --- | --- |
| `SAVEMODE` | `ANYWHERE` | `ANYWHERE`, `NOTINCOMBAT`, `SAVEPOINTS` or `IRONMAN`. Chosen when a new game starts; kept in its saves. |
| `AUTOSAVE` | blank | Game minutes between autosaves (`30`), or `ON-AREA-CHANGE`. Blank or `0` is off. |
| `AUTOSAVEKEEP` | `3` | How many rotating autosave slots. |
| `PARTYMEET` | blank | `1` makes added party characters stay where they are for the player to meet. |
| `PARTYFIGHT` | blank | `1` lets the player's characters fight each other. Overrides `PLAYERKILL`. |
| `PARTYTURNS` | `LEADER` | `LEADER` or `ALL`: how party members' orders run in turn-based time. |
| `PARTYMAX` | `0` | Most characters in the party at once; `0` is no limit. |
| `ACTIVECHARMAX` | `0` | Cap on profile characters active in the world. |
| `PARTYSUMMON` | `ALLOW` | `ALLOW`, `MEET` or `MASK`. |
| `PARTYSUMMONMASK`, `PARTYSUMMONMESSAGE` | blank | Mask and refusal text for `MASK` mode. |
| `PARTYROLES` | blank | `"role:mask; role:mask"` for the roles the party warns about. |
| `PROFILEMAX` | `0` | Most characters in the profile; `0` is no limit. |
| `NEWCHARS` | `ALWAYS` | `ALWAYS`, `FIRSTONLY`, `CHEAT` or `MASK`. With `NEWCHARMASK` and `NEWCHARMESSAGE` (allowlisted, not in the shipped file). |
| `SAVE` | blank | Save flags (the engine's `NOPLAYERS`, `ROOMMOBS` and so on); see [the flag section](#disable-enable-debug-save-and-nocache-flags). |

The save and party systems themselves are described in
[Saves and party](31-saves-and-party.md).

### Quest, NPC and dialogue keys

All world-allowlisted.

| Key | Shipped | Meaning |
| --- | --- | --- |
| `QUESTMARKERS` | blank | `AUTO` marks every running quest's first quest mob and every `Prop_QuestGiver` in room listings. Blank marks only `Prop_QuestMarker`. |
| `QUESTNOTICES` | blank | `OFF` hides the "Quest updated:" lines. |
| `QUESTTRACK` | blank (`PATH`) | `OFF`, `AREA` or `PATH`: how much an objective's place shows. |
| `CHECKROLLS` | blank (`BRIEF`) | `OFF`, `BRIEF` or `FULL`: what a script's `CHECK()` tells the character. |
| `HINTTOPICS` | blank (false) | `true` has every NPC list its conversation topics the first time you talk. |
| `CHATDEFAULT` | blank | Chat file in `resources/` for MudChat mobs with no parameters; `NONE` for none. Default `chat.dat`. |
| `AUTOREACTION` | `PLANAR` | Systems by which mobs react to a character's history: `AREA`, `RACE`, `NAME`, `DEITY`, `PLANAR`. Blank disables. |
| `DUELTICKDOWN` | `5` | Ticks a challenged duellist has to answer. |

### New-character keys

All world-allowlisted unless noted. Several are covered in more depth in
[Characters](03-characters.md).

| Key | Shipped | Meaning |
| --- | --- | --- |
| `STARTINGITEMS` | `1 Ration, 1 Waterskin, 1 Torch, Pixie, 1 Apple, ...` | Items every new character gets. A race name starts a list for that race. A catalog name is allowed. `,` alone gives nothing. |
| `STARTHP`, `STARTMANA`, `STARTMOVE` | `20`, `100`, `100` | Starting points; a comma and a formula adds to every class's formula. |
| `STARTSTAT` | `0` | `0` roll randomly up to `MAXSTATS`; `-1` spend points; `SCRIPT` from `world/scripts/progression.js`; a number sets all stats. |
| `MAXSTATS` | `70` | Total of rolled starting stats when `STARTSTAT` is `0`. |
| `BASEMINSTAT`, `BASEMAXSTAT` | `3`, `18` | Base stat limits before race and class. |
| `STATCOSTS` | `<18 1, <22 2, <25 3, <99 5` | Training cost to raise a stat. |
| `TRAINCOSTS` | see file | What `TRAIN` buys and what it costs. |
| `CLASSSYSTEM` | `SUB` | `SUB`, `NO`, `MULTI`, `APP-...`, `DISABLED`, with `,GAIN=` and `,SWITCH=` costs. |
| `MUDTHEME` | `1` | Races and classes available: 1 fantasy, 2 technological, 4 heroic, summed. |
| `CREATIONTEMPLATES` | `OFFER` | `OFFER`, `ONLY` or `NO` for ready-made character templates. |
| `SHOWLOCKED` | `NO` | `YES` lists locked races and classes that have an unlock hint. |
| `CHARCREATIONSCRIPTS` | blank | Scripts run during character creation or at login. |
| `RACEMIXING` | blank | Override of how mixed-race children are made. |
| `NEWCHARLEVEL`, `NEWCHARXP` | `1`, `KEEP` | The level a later character starts at, and whether its XP is kept or floored. |
| `DISCOVERYXP` | blank | XP formula for each room entered the first time. |
| `FORMULA_PRACTICESPERLEVEL`, `FORMULA_TRAINSPERLEVEL`, `TRAINSATLEVELS` | blank | What a level gives. |
| `AGEDISEASES` | blank | Replaces the ageing diseases. |
| `LASTPLAYERLEVEL` | `91` | Highest level mundane XP gives. |
| `DEITYPOLICY` | blank | Deity choice rules (`REQUIREDMASK`, `NOREBUKE`, `NOCONVERT`, `DEITYMASK`). |
| `MAXITEMSHOWN` | `5` | Items listed by `LOOK` before it summarises. Engine-only. |
| `MAXITEMSWORN` | `0` | Cap on worn items; `0` is no cap. Engine-only. |
| `MAXWEARPERLOC` | `6` | Cap per body-part type; `0` is no cap. Engine-only. |
| `BADNAMES` | a long list | Names refused for characters. Probably still checked when a character is named (unverified). |
| `DEFAULTPLAYERFLAGS` | blank | Player option flags on for new characters (`AUTOLOOT`, `AUTOEXITS`, ...). |
| `RECOVERRATE` | `1` | Five-tick periods between natural recovery. |
| `REMORTMASK`, `REMORTRETAIN` | `-LEVEL +>60`, see file | The REMORT command's rules. |
| `HUNGER`, `THIRST` | `500,100,100`, `1000,100,100` | Capacity and two percentages. |
| `EXPDEFER`, `RPAWARDS` | blank | Hold earned XP until the configured command is used (the release is done by the internal `DeferCmd`, which has no typed name), and XP for role-play actions. CoffeeSUD runs the deferral and the award interval on the game clock, so a paused or turn-based game releases held XP by game time. Syntax is in the ini's own comments. |
| `XPMOD` | blank | Global XP modifiers, with the arguments of `Prop_ModExperience`. |
| `LOGOUTMASK`, `PRIDECATS`, `PRIDECOUNT` | blank, blank, `10` | A mask a character must match to log off, and top-player lists. Multiplayer-era; see the [inert list](#inert-keys). |

### Death, combat and survival keys

Mostly world-allowlisted. Details belong to [Combat and encounters](29-combat-and-encounters.md).

| Key | Shipped | Meaning |
| --- | --- | --- |
| `COMBATSYSTEM` | `DEFAULT` | `DEFAULT`, `QUEUE`, `MANUAL` or `TURNBASED`. |
| `SHOWDAMAGE` | `NO` | `YES`, `NO`, `SOURCE`, `TARGET`. Engine-only. |
| `PROWESSOPTIONS` | blank | What the score sheet shows of combat, defence and damage. Engine-only. |
| `INJURYSYSTEM` | `100,60,25,100,2,10,15,20,100` | Nine values for limb injury and bleeding. |
| `PLAYERKILL` | `NEVER` | A player's characters never fight each other, unless `PARTYFIGHT` is on. Engine-only. |
| `PLAYERDEATH` | `(@x1 * 50) < 2000` | What a death costs: `EXPERIENCE`, `UNLEVEL X`, `OUT X`, `DOWNED X Y`, a number, a formula. |
| `PARTYDEFEAT`, `PARTYDEFEATGOLD` | `DEATH`, `10` | What happens when the whole party in a room is down: `RESTORE`, `CHECKPOINT`, `WAIT`, `DEATH`. Engine-only. |
| `MOBDEATH` | `DEATH` | `DEATH` or `OUT (expression)` for NPCs. |
| `FLEE` | `EXPERIENCE` | What fleeing costs. Engine-only. |
| `EXPRATE` | `6` | How fast XP changes with level difference. `0` removes the bonus and penalty. |
| `EFFECTCXL`, `EFFECTCAP` | `0` | Combat XP level modifiers for OP effects, and a cap on uninvokable effects. |
| `CORPSEGUARD` | `ALL` | Who may loot a corpse. Engine-only. |
| `ITEMLOOTPOLICY` | `20% RUIN` | What happens to a killed mob's items. Engine-only. |
| `ITEMWEAR`, `ITEMBREAK` | `100`, `DESTROY` | Wear and tear percentage; `DESTROY` or `BREAK` for worn-out items. Engine-only. |
| `DIFFICULTY` | `NORMAL` | Game difficulty preset (allowlisted, with `DIFFICULTY_*` presets). |
| `BONUSACT` | `1.0` | Default bonus action multiplier for NPCs and skills. Engine-only. |
| `DEFCMDTIME`, `DEFCOMCMDTIME` | `0.20`, `1.0` | Action cost of a command out of and in combat, with per-command exceptions. |
| `DEFABLETIME`, `DEFCOMABLETIME`, `DEFSOCTIME`, `DEFCOMSOCTIME` | `0.10`, `1.0` | The same for skills and socials. |
| `WALKCOST`, `RUNCOST` | `1`, `2` | Movement points per room. |
| `AWARERANGE` | `3` | Size of the automap. `0` disables. (Allowlisted; the code default is `0`.) |
| `FOLLOWLEVELDIFF` | `0` | Level range limit for followers. |

### Skill and ability keys

| Key | Shipped | Meaning |
| --- | --- | --- |
| `MANACOST`, `MANAMINCOST` | `-1, Skill_Recall 25`, `-1, 10` | Default mana and move costs, with per-skill exceptions. `MANACOST` is world-allowlisted. |
| `MANACOMPOUND` | blank | Extra cost for repeating a skill soon. |
| `MANACONSUMETIME`, `MANACONSUMEAMT` | `0`, `0` | Spells that drain mana over time. |
| `PRACMAXPCT` | `75` | Highest proficiency practice can give. |
| `COMMONCOST`, `SKILLCOST`, `LANGCOST` | `1 TRAIN`, `1 TRAIN, Skill_WildernessLore 1 PRACTICE, PlayInstrument 3 PRACTICE`, `3 PRACTICE` | Cost to gain an ability. |
| `MAXCOMMONSKILLS`, `MAXCRAFTINGSKILLS`, `MAXNONCRAFTINGSKILLS`, `MAXLANGUAGES` | `0`, `2`, `5`, `3` | Limits on skills every class shares. `0` is no limit. |
| `DEFAULTABILITYARGS` | blank | Default parameters by ability ID. World allowlisted. |
| `NEWACODES`, `NEWDOMAINS` | blank | Extra ability types and domains. World allowlisted. |
| `RACEEXPIRATIONDAYS` | `365` | Days before an unused generic race is purged from the database. |

### Start, death and morgue rooms

All three are world-allowlisted, with prefix families `START_*`, `DEATH_*`
and `MORGUE_*` (by race, class, alignment, level, faction or mask).

| Key | Shipped | Meaning |
| --- | --- | --- |
| `START` | `New Area#0` | Room new characters appear in. |
| `DEATH` | `START` | Where a dead character goes: a room ID, `START`, `MORGUE`, `HERE` or `CHECKPOINT`. |
| `MORGUE` | `HERE` | Where a player's body goes: a room ID, `HERE`, `START` or `DEATH`. |

### World, economy and crafting keys

| Key | Shipped | Meaning |
| --- | --- | --- |
| `DIRECTIONS` | `7` | `7` (N, S, E, W, U, D, V) or `11` (adds diagonals). |
| `SKYSIZE` | `3` | Width of the generated sky above outdoor rooms; `0` for none, negative to hide the exit. |
| `AUTOWEATHERPARMS` | blank | `NO`, or parameters of the weather behavior added to areas. |
| `AUTOAREAPROPS` | blank | Properties and behaviors added to every area (`ID(parms);ID2`). |
| `GEN_BACKTICKS` | blank | `false` keeps real apostrophes in generated text. |
| `PROFILEPROPERTY` | blank | `true` makes bought property belong to the whole profile. |
| `HOMEBEACONS`, `HOMERECALL` | blank | Owned rooms act as homes; turns `HOME` takes. |
| `PROFILERECIPES` | blank | `true` shares known recipes across the profile. |
| `CRAFTEXPERIMENTS` | blank (10) | Craft experiments per game day; `0` unlimited. |
| `CRAFTXP`, `GATHERXP` | blank | XP for crafting and gathering. |
| `DISMANTLEPCT` | blank (50) | Percent of materials `DISMANTLE` returns. |
| `DEFAULTCURRENCY` | blank | Currency code for areas with none; `CURRENCY_*` defines currencies. |
| `EXPIRATIONS` | `30,1200,60,30,1330` | Minutes before monster loot, player drops, gathered resources, monster corpses and player corpses vanish. |
| `PREJUDICE`, `PRICEFACTORS`, `IGNOREMASK`, `BUDGET`, `DEVALUERATE`, `INVRESETRATE`, `AUCTIONRATES` | see file | Shopkeeper pricing and stock rules. See [Society](09-society.md). |
| `DEFAULTPARENTAREA` | blank | Parent area for areas that name none. |
| `MAPFOG`, `CLANCLOCK`, `CLANNEWS`, `CLANVOTES`, `JOURNALAUTO`, `JOURNALTALK`, `ROOMHEADER`, `ROOMFOOTER`, `COLORALIASES` | at the end of the file | Late additions. `CLANCLOCK`, `CLANNEWS` and `CLANVOTES` are world-allowlisted. |
| `ADDCHARSTAT_*`, `REPLACECHARSTAT_*`, `STATEFFECT_*`, `STARTAGE_*`, `EXTVAR_*`, `ADDMATERIAL_*`, `REPLACEMATERIAL_*`, `ADDWEARLOC_*`, `REPLACEWEARLOC_*`, `MOBRANK_*`, `DIFFICULTY_*`, `CURRENCY_*` | not shipped | Prefix families for custom stats, materials, wear locations and so on. Of these, `ADDCHARSTAT_`, `REPLACECHARSTAT_`, `STATEFFECT_`, `STARTAGE_`, `MOBRANK_`, `DIFFICULTY_` and `CURRENCY_` are world-allowlisted; `EXTVAR_`, `ADDMATERIAL_`, `REPLACEMATERIAL_`, `ADDWEARLOC_` and `REPLACEWEARLOC_` are engine-only. |

### Display and text keys

| Key | Shipped | Meaning |
| --- | --- | --- |
| `EQVIEW` | `DEFAULT` | Equipment as a list or a paragraph (`DEFAULT`, `MIXED`, `PARAGRAPH`). |
| `EXVIEW` | `DEFAULT` | Exits shown as list, `BRIEF`, `PARAGRAPH` or `MIXED`. |
| `DEFAULTPROMPT` | `^N%E<^h%hhp ^m%mm ^v%vmv^N> ` | The prompt every character starts with. |
| `COLORSCHEME` | see file | Default ANSI colours by code. |
| `PAGEBREAK` | `0` | Lines before `<pause - enter>`; `0` for none. |
| `ESCAPE0` to `ESCAPE9` | blank | Custom escape codes. |
| `LISTFILE` | `/resources/lists.ini` | The file of charts and string lists. |
| `LANGUAGE`, `COUNTRY` | `en`, `TX` | Select `resources/parser_en_TX.properties` and `translation_en_TX.properties`. |
| `CHARSETINPUT`, `CHARSETOUTPUT` | `iso-8859-1` | Text encodings of the console stream. |
| `EDITORTYPE` | `MENU` | `MENU` or `WIZARD` for the mob and item editors. |

### Formula keys

The `FORMULA_` keys set how the engine computes combat numbers, NPC stats,
recovery, carrying and XP. The shipped file lists each with its default and
the meaning of `@x1`, `@x2` and so on; the whole `FORMULA_*` family is
world-allowlisted. Keys in the shipped file:

- NPC stats: `FORMULA_NPCHITPOINTS`, `FORMULA_NPCARMOR`, `FORMULA_NPCATTACK`,
  `FORMULA_NPCDAMAGE`, `FORMULA_NPCSPEED`, `FORMULA_NPCREJUV`,
  `FORMULA_NPCMONEY`.
- Hit and armour: `FORMULA_ATTACKADJUSTMENT`, `FORMULA_ARMORADJUSTMENT`,
  `FORMULA_ATTACKFUDGEBONUS`.
- Damage, ranged and melee: `FORMULA_DAMAGERANGEDTARGETED`,
  `FORMULA_DAMAGERANGEDSTATIC`, `FORMULA_DAMAGERANGEDFUDGE`,
  `FORMULA_DAMAGEMELEETARGETED`, `FORMULA_DAMAGEMELEESTATIC`,
  `FORMULA_DAMAGEMELEEFUDGE`.
- Criticals: `FORMULA_CHANCESPELLCRIT`, `FORMULA_DAMAGESPELLCRIT`,
  `FORMULA_DAMAGESPELLFUDGE`, `FORMULA_CHANCEWEAPONCRIT`,
  `FORMULA_DAMAGEWEAPONCRIT`.
- The same family with a `PVP` form (`FORMULA_PVPATTACKFUDGEBONUS`,
  `FORMULA_PVPCHANCESPELLCRIT` and so on). Each falls back to the non-PvP
  formula when blank. Player-versus-player only applies when `PARTYFIGHT` is on.
- Recovery: `FORMULA_HITPOINTRECOVER`, `FORMULA_MANARECOVER`,
  `FORMULA_MOVESRECOVER`.
- XP: `FORMULA_TOTALCOMBATXP`, `FORMULA_INDCOMBATXP`.
- Carrying and following: `FORMULA_MAXCARRY`, `FORMULA_MAXITEMS`,
  `FORMULA_MAXFOLLOW`.
- Practice gain: `FORMULA_PROFGAIN`.
- Level gains (blank in the file): `FORMULA_PRACTICESPERLEVEL`,
  `FORMULA_TRAINSPERLEVEL`.
- `MOB_HP_BASE` (not in the shipped file, default `11`) is the "ability" hit
  point modifier passed to `FORMULA_NPCHITPOINTS` as `@x2`.

Tuning guidance is in [Combat profiles](16-combat-profiles.md) and
[Combat and encounters](29-combat-and-encounters.md).

### Class path keys

`COMMON`, `LIBRARY`, `ITEMS`, `WEAPONS`, `ARMOR`, `MISCMAGIC`, `CLANITEMS`,
`MOBS`, `BEHAVIORS`, `ABILITIES`, `CHARCLASSES`, `RACES`, `COMMANDS`,
`LOCALES`, `AREAS`, `EXITS`, `CLANTYPES`, `TECH`, `COMPTECH`, `SOFTWARE`,
`MCPPACKAGES` and `CMTESTS` each list where the engine loads classes of that
kind. The value is semicolon-separated; `%DEFAULT%` is the stock package.
For example, `MOBS=%DEFAULT%;resources/examples/Lemming.js`.

In CoffeeSUD the launcher adds a world's own classes without touching these:
if `world/classes/<kind>/` exists, that path is appended to the key
automatically, and `world/js/<kind>/` is appended for JavaScript classes.
Set the keys yourself only for classes outside the world folder. See
[Engine programming](32-engine-programming.md).

### Database keys

| Key | Shipped | Meaning |
| --- | --- | --- |
| `DBCLASS` | `com.planet_ink.fakedb.Driver` | JDBC driver. Leave as is. |
| `DBSERVICE` | `jdbc:fakedb:resources/fakedb` | The content cache's folder. Must start `jdbc:fakedb:` or world files and saves are switched off. |
| `DBUSER`, `DBPASS` | blank | Unused by the fake database. |
| `DBCONNECTIONS` | `2` | Connection pool size. |
| `DBREUSE`, `DBTRANSACT`, `DBPINGINTERVALMINS` | `TRUE`, `FALSE`, `60` | Pool behavior; leave as shipped. |
| `DBPARMS` | not set | Extra JDBC properties. Not used by the fake database. |

Other JDBC databases are described in the ini's comments as inherited
examples. The single-player save and world-file system requires the fake
database (`SaveGame.beforeBoot` prints "The world files and saved games need
the fake database, so they are off" otherwise). Treat the database as fixed.

### Inert keys

These keys are still in `coffeemud.ini` because CoffeeSUD kept the file, but
the code that read them served a network server, accounts or other features
removed from the fork. Setting them has no effect you can use in a CoffeeSUD
game.

| Keys | Why inert |
| --- | --- |
| `PORT`, `PROXY`, `MPCPKEY`, `BIND`, `BACKLOG`, `DOMAIN` | The telnet listener is replaced by one console session (`MUD.runConsole`). Nothing listens. |
| `MAXCONNSPERIP`, `MAXNEWPERIP`, `MAXCONNSPERACCOUNT`, `BLACKLISTFILE`, `WHITELISTIPSCONN`, `WHITELISTLOGINS`, `WHITELISTIPSNEWPLAYERS` | Connection limits and IP lists; there are no remote connections. |
| `MUDSTATE`, `ADMINEMAIL` | Reported to intermud and MSSP, both removed. |
| `EMAILREQ`, `IDLETIMERS`, `COMMONACCOUNTSYSTEM` | Replaced by the launcher (see above); `COMMONACCOUNTSYSTEM` is forced to 50. |
| `ACCOUNTEXPIRATION`, `TRIALDAYS`, `EXPCONTACTLINE`, `ACCOUNTPURGE`, `AUTOPURGE`, `HASHPASSWORDS` | Account and player purging and passwords. Leave `AUTOPURGE` at `>=0 0 0` (off). |
| `COMMANDJOURNALS`, `FORUMJOURNALS`, `JOURNALLIMIT` | Player-to-admin journals and web forums. The shipped lists are empty. `CHANNELBACKLOG` (the stored history of channel messages) is left over too (unverified). |
| `CLANWEBSITES`, `CLANFORUMDATA`, `PUBLICCLANCATS`, `CLANVOTEO`, `CLANVOTED`, `CLANVOTER` | Clan web pages, forums and vote timings. The three vote keys are not read by any Java code at all. |
| `SOUNDPATH`, `MXPIMAGEPATH`, `MSXPVARS` | MSP, MXP and MSXP client protocols. |
| `DISCORD_JAR_PATH`, `DISCORD_BOT_KEY`, `TWITTER_*` | Chat integrations. |
| `PRIVATERESOURCES`, `PRIVATERESOURCEPATH` | Resource sharing for several hosts in one process. |
| `PROMPTBEHAVIOR` | Telnet go-ahead and carriage-return suffix. |
| `KILMSGS`, `ACCMSGS` | `KILMSGS` is misspelt; the log channel is `KILLMSGS` (see [Logs](#logs-devlog-and-diagnostics)). `ACCMSGS` is the web server access log. |
| `CHANNELFILTER`, `EMOTEFILTER`, `SAYFILTER`, `POSEFILTER` | Profanity filters for player-to-player text. Left over; no players share a channel. |
| `PRIDECATS`, `PRIDECOUNT`, `LOGOUTMASK` | Multiplayer features (top-player lists, restricting log-off) (unverified; they load without error). |
| `I3ROUTERS`, `SMTPSERVERNAME`, `GVCLIENTID`, `GVCLIENTSECRET` | Intermud, mail and Grapevine; not in the shipped file. |
| `LANGCHAIN4J_JAR_PATH`, `LANGCHAIN4J_LLM_TYPE`, `LANGCHAIN4J_BASEURL` | Optional LLM bridge for the `LLM` command and `LLMIFY`; unrelated to world authoring. |

Most entries above are inert because the system they configure has no entry
point in a single-player game. A few (the profanity filters, `CHARSETINPUT`,
`CHARSETOUTPUT`) may still be applied to the console stream; this was not
traced to the end (unverified). Where unsure, leave the shipped value.

## world.ini, coffeemud.ini and launch overrides

`world/world.ini` is a small ini file in the world folder for the settings
that make a world the game it is. It is optional; the shipped world has none.
The complete list of keys it accepts is the generated
[world settings reference](world-settings.md); this section does not repeat
it. The rules in short:

- **Allowlist.** `WorldSettings.KEYS` and `WorldSettings.PREFIXES` decide.
  A key that is not on the list is reported when the game starts
  (`world.ini: KEY is not a world setting, so ignored (the engine's own
  settings go in coffeemud.ini).`) and ignored. Engine settings such as
  `DBSERVICE`, `NUMLOGS`, the log channels, `JSCRIPTS`, `JSTIMELIMIT`,
  `TICKTIME`, `BUILDER`, `DEV`, `SYSOPMASK` and `GROUP_*` are never read from
  world.ini.
- **Prefix families.** `START_*`, `DEATH_*`, `MORGUE_*`, `FORMULA_*`,
  `ADDCHARSTAT_*`, `REPLACECHARSTAT_*`, `STATEFFECT_*`, `STARTAGE_*`,
  `DIFFICULTY_*`, `MOBRANK_*` and `CURRENCY_*` are accepted when something
  follows the underscore.
- **Format.** `NAME=VALUE` lines; `#` and `!` start comments; names are
  upper-cased. A world.ini line has no continuation (a value must be on one
  line).
- **Precedence.** `coffeemud.ini` < launcher settings < `world.ini` <
  command line. `LIST WORLDINI` (or `WORLD`) shows each effective value and
  where it came from.
- **When it takes effect.** At the next start. `WORLD SET KEY=VALUE` in
  builder mode writes a line in world.ini (keeping the rest of the file) and
  says it takes effect the next time the game starts. A blank value removes
  the line.
- **A new game or a restored one.** World.ini decides what a **new** game
  starts with. A restored game keeps the few settings its save carries (time
  mode, save mode, party options and the other saved state), and the world's
  checksum is part of what a save compares against; see
  [Saves and party](31-saves-and-party.md).

### Launch overrides

Any word `NAME=VALUE` after `./sud.sh` is a launch override and beats every
file:

```sh
./sud.sh BUILDER=1
./sud.sh DEV=1 TIMESCALE=8
./sud.sh RESTORE=hero FRESH=CHANGED
./sud.sh WORLDDIR=/home/me/worlds/island SAVEDIR=/home/me/saves/island
./sud.sh DBGMSGS=BOTH DEBUG=SCRIPTVARS
```

Details:

- The names are upper-cased; the value keeps its case. Only the first `=`
  splits the word. If a name is given twice, the last one wins.
- An override is applied even to keys that are not allowlisted for world.ini.
- A `NAME:X=VALUE` form with a thread-group digit exists in the engine for
  multi-host setups; in CoffeeSUD there is one host (group `0`), so use plain
  `NAME=VALUE`.
- A WORLDDIR on the command line is also where world.ini is read from.

### Example: a world's world.ini

```ini
# world/world.ini: the settings that make this world the game it is
GAMENAME=Island Chronicle
START=Harbor#1
DEATH=START
TIMEMODE=TURNS
PARTYMAX=4
PROFILEMAX=8
SAVEMODE=SAVEPOINTS
AUTOSAVE=ON-AREA-CHANGE
HOURSINDAY=24
DAWNHR=5
DAYHR=7
DUSKHR=18
NIGHTHR=20
DISABLE=DBERRORQUESTART, COMMAND_POSE, QUITREASON, RETIREREASON, CHANNELAUCTION
FORMULA_NPCHITPOINTS=3 + @x1 + (@x1 * @x2)
```

The room `Harbor#1` is an example; use a real room ID of your world. Check
the result with `WORLD` in builder mode, then with a plain `NEW` game.
Builder privileges can hide a policy mistake, so always test as an ordinary
character.

## Security

### SYSOPMASK and the Archon

An all-powerful administrator is a **SysOp**. A character is a SysOp when it
matches the zapper mask in `SYSOPMASK`. Shipped value:

```ini
SYSOPMASK=-ANYCLASS +Archon
```

That mask means "any character whose class is Archon". The mask language is
the same as everywhere in the engine (see
[Behaviors and masks](24-behaviors-and-masks.md) and `AHELP ZAPPERMASKS`).
A SysOp passes every security check below and may use every administrator
command anywhere. If the mask is blank or missing, the engine falls back to
`-ANYCLASS +Archon` when the Archon class exists (and `-LEVEL +>30`
otherwise).

How a character becomes an Archon in CoffeeSUD:

- **Builder mode** (`BUILDER=1`) gives each new character the Archon's
  manual to read, which changes class to Archon (`SaveGame.makeArchon`,
  `ManualArchon`). Builder mode keeps those characters in the content cache.
- Nothing else makes an Archon. In a normal game or a DEV game no character
  matches `SYSOPMASK`, so a normal game has no SysOp unless you give a
  character the Archon class yourself (for example with the Archon manual
  item, or `MODIFY USER` in builder mode).

`SYSOPMASK` is read once at boot (`MUD.java` calls `CMSecurity.setSysOp`).
`RESET INIFILE` does not recompile it; restart after changing it.

`WIZLISTMASK` (shipped blank) chooses which names appear in the wizard list;
it defaults to `SYSOPMASK` and is a leftover of multiplayer staff lists.

### Security flags

`CMSecurity.SecFlag` is the list of privileges. Every flag also has an
`AREA_` twin (the "subop" form) that only counts inside areas where the
character is listed as staff (see below). The table groups the flags by
purpose. The "Meaning" column is from the shipped help (`AHELP SECURITY`)
and the code that checks the flag.

Flags that open a command of the same name:
`AFTER`, `AHELP`, `AS`, `ASYNC`, `AT`, `BEACON`, `CATALOG`, `CHARGEN`,
`CLOAK`, `DUMPFILE`, `EVERY`, `GMODIFY`, `GOTO`, `MERGE`, `PAUSE`, `POSSESS`,
`PURGE`, `RESET`, `RESTRING`, `SHUTDOWN`, `SNOOP`, `STAT`, `SYSMSGS`,
`TICKTOCK`, `TRAILTO`, `TRANSFER`, `WHERE`, `WIZEMOTE`, `WIZINV`.
(`PAUSE` is open to everyone in single-player, so its flag is moot.)

| Flag | Grants |
| --- | --- |
| `ABOVELAW` | Immunity to the Arrest behavior; also the `LAW` tool. |
| `ALLSKILLS` | All skills, songs, prayers and spells, and no wait between skill uses. |
| `CARRYALL` | Overrides item carrying restrictions. |
| `GOTO` | The `GOTO` and `POOF` commands, and walking through doors and past entry checks (the NOCLIP cheat). |
| `IMMORT` | Never dies (hit points stop at 1). |
| `SUPERSKILL` | Skills and spells never fail. |
| `ORDER` | Order any mob to do anything; `GIVE`, `TAKE` and `DRESS` never fail; with `CMDROOMS`, aggressive creatures leave the character alone. |
| `IDLEOK`, `NOEXPIRE`, `NOPURGE` | Exempt from idle time, account expiry and purging. All three belong to removed multiplayer systems. |
| `PKILL` | Overrides player-kill flag settings. |
| `MXPTAGS` | Insert MXP tags in typed text. Inert without MXP clients. |
| `LISTADMIN` | `LIST` of ticks, threads, reports, the log and most system lists. |
| `LOADUNLOAD` | `LOAD`, `UNLOAD` and `LIST RESOURCES`. |
| `RESETUTILS` | The maintenance-style `RESET` tools such as `RESET PROPERTYGARBAGE` that follow `RESET ROOM`, `AREA`, `REJUV`, `RELEVEL`, `INIFILE` and `PASSWORD` in the code (those need only `RESET`). Several of the later ones also need SysOp. |
| `SESSIONS` | `LIST SESSIONS`. |
| `JSCRIPTS` | Approve JavaScript when `JSCRIPTS=1`; the `JS`, `JRUN`, `JCONSOLE` and `MPCOMMAND` commands. |
| `JOURNALS` | Administer all journals. |
| `NEWS` | The news list and `CREATE NEWS`. |
| `CMDDATABASE` | `LIST SQL`, `LIST DBCONNECTIONS` and database modify tools. |
| `PLANES` | Create, modify and destroy planes of existence. |
| `ACHIEVEMENTS`, `AUTOAWARDS`, `TITLES`, `COMPONENTS`, `EXPERTISE`, `EXPERTISES` | Create, modify, destroy those definitions. |

The `CMD...` family opens `CREATE`, `MODIFY` and `DESTROY` for one kind of
thing:

| Flag | Creates, modifies and destroys |
| --- | --- |
| `CMDAREAS` | Areas; also `GENERATE`, `GENLAYOUT`, `REGENERATE`, `VESSEL`, `VOCAB`. |
| `CMDROOMS` | Rooms; also `WORLD` tools, `UNDO`, `RUNFILE`, `SANDBOX`, `PUZZLE`, `DEVWATCH`, `SCRIPTPROF`, `PREVIEW`/`CHECKTEXT`. |
| `CMDEXITS` | Exits; `LINK` and `UNLINK`. |
| `CMDITEMS` | Items in rooms. |
| `CMDMOBS` | Mobs in rooms; keeps mobile mobs from wandering off; `ENCOUNTER`, `COMBATDEBUG`, `CHATTEST`. |
| `CMDPLAYERS` | Modify or destroy players (the character editor, `MODIFY USER`). |
| `CMDQUESTS` | Quests; `QUESTINFO`, `VARS`, `QUESTCHECK`. |
| `CMDRACES`, `CMDCLASSES`, `CMDABILITIES`, `CMDCOMMANDS`, `CMDRECIPES`, `CMDFACTIONS`, `CMDSOCIALS`, `CMDCLANS` | Custom races, classes, abilities, commands, recipes, factions, socials and clans. |
| `CMDCRON` | Cron jobs. |
| `CMDHELP` | Help entries. |

Import, export and copy:

| Flag | Grants |
| --- | --- |
| `COPYITEMS`, `COPYMOBS`, `COPYROOMS` | The `COPY` command for that kind of thing. (`COPY` is the group of all three plus the `COPY` flag; the group is `SECURITY_COPY_GROUP`.) |
| `EXPORT`, `EXPORTFILE`, `EXPORTPLAYERS` | The `EXPORT` command; to a file; of player data. |
| `IMPORTITEMS`, `IMPORTMOBS`, `IMPORTPLAYERS`, `IMPORTROOMS` | The `IMPORT` command for each kind. |
| `KILLDEAD` | `KILL [name] DEAD` (a mob dies at once). |

Flags with no live check (they only exist because the enum kept them): `ABILITIES`,
`ANNOUNCE`, `CMD`, `COPY`, `GRAPEVINE`, `I3`, `IMC2`, `IMPORT`, `KILL`,
`MISC`, `POLLS`. `BAN` and `BOOT` are still checked, but only inside the
`DESTROY BAN` and `DESTROY SESSION` tools, and `ANNOUNCE`, `BAN` and `BOOT`
have no command of their own any more. The shipped help also names `TASKS`,
`KILLSTUCK`, `KILLBUGS`, `KILLIDEAS` and `KILLTYPOS`; `TASKS` is not a flag,
and the `KILL...` forms are journal names for journals CoffeeSUD does not
define.

Flag names are looked up exactly, with an optional trailing `S`
(`COPYITEM` finds `COPYITEMS`), spaces turn into underscores (`AREA GOTO`
is `AREA_GOTO`), and `POOF` is accepted as `GOTO`.

### Security groups

A group is a named list of flags. Define one with a `GROUP_NAME` key in
`coffeemud.ini` (not in world.ini). The name after `GROUP_` is the group's
name; when you assign it to a character, write the name without `GROUP_`.

The value is a comma-separated list of: security flags (with or without
`AREA`), other group names, `FS: path` and `VFS: path` file access, and
journal names. Groups may include other groups.

Shipped groups:

| Group | Contents |
| --- | --- |
| `QUESTMASTERS` | `POSSESS, AT, AFTER, CMDQUESTS, GOTO, COPYITEM, COPYMOB, IMMORT` |
| `SUBOP` | Area-limited builder powers: `AHELP`, `AREA ANNOUNCE`, `AREA AT`, `AREA COPY...`, `AREA CMDROOMS`, `AREA CMDITEMS`, `AREA CMDEXITS`, `AREA CMDRACES`, `AREA GOTO`, `AREA POSSESS`, `AREA STAT`, `AREA TRANSFER`, `AREA WHERE`, `AREA RESET`, `AREA KILLDEAD`, `AREA ORDER`, `AREA ABOVELAW`, `AREA WIZINV`, `AREA CLOAK`, `AREA CMDMOBS`, `IMMORT`, `TRAILTO`, `SESSIONS`, `AREA POOF`, `AREA PURGE`, `AREA RESTRING`, `AREA LISTADMIN`, `AREA TRAILTO`, `AREA SUPERSKILL`, `AREA LOADUNLOAD`, `AREA MXPTAGS`, `CARRYALL`, `AREA GMODIFY`, `VFS: RESOURCES` |
| `TRAVELER` | `GOTO` |
| `DEV` | `GOTO, AT, TRANSFER, STAT, WHERE, LISTADMIN, CMDQUESTS, CMDMOBS, JSCRIPTS, AHELP, FS: RESOURCES`. No `IMMORT` and no `ORDER`, so a test plays like play. |
| `WIZI` | `WIZINV, ABOVELAW, CLOAK` |
| `MISCHIEF` | `POSSESS, AT, STAT` |
| `SLAYER` | `KILLDEAD` |
| `ASSISTANT`, `ASSISTANT2`, `ASSISTANT3` | Escalating helper sets: copy, sessions, reset, transfer, purge, superskill, restring, where, and for `ASSISTANT3` also `BEACON`, `TICKTOCK`, `SNOOP`, `TRAILTO`. |
| `IMPLEMENTOR` | A builder's set: `AHELP, ANNOUNCE, AT, COPY..., CMDROOMS, CMDITEMS, CMDEXITS, CMDRACES, GOTO, POSSESS, STAT, SYSMSGS, TRANSFER, WHERE, RESET, KILLDEAD, ORDER, ABOVELAW, WIZINV, CMDMOBS, IMMORT, TRAILTO, SESSIONS, SUPERSKILL, MXPTAGS, CLOAK, CARRYALL, GMODIFY, FS: RESOURCES, CATALOG` |
| `SYSOP` | Nearly everything an Archon has, as a group. |

The shipped `QUESTMASTERS` group names `COPYITEM` and `COPYMOB`; both resolve
(the parser adds a trailing `S`).

An unknown name in a group is logged: `Unknown security flag: X in group
NAME`. Groups are parsed once at boot (`MUD.java` calls
`CMSecurity.parseGroups`); `RESET INIFILE` does not re-parse them.

For the DEV game, add or change the DEV group here. To use a different group,
set `DEVGROUP`:

```ini
GROUP_PLAYTESTER=GOTO, AT, TRANSFER, STAT, WHERE, LISTADMIN, CMDQUESTS, JSCRIPTS, AHELP, FS: RESOURCES
```

```sh
./sud.sh DEV=1 DEVGROUP=PLAYTESTER
```

### Area staff (subops)

An area has a staff list (the area editor's "Staff" field). A character
named there is a **subop** of that area. The `AREA_` flags (written `AREA
GOTO` in a group or a character's Security field) work only while the
character is in an area where they are staff. A few checks (`isAllowedAnywhere`)
also accept an `AREA_` flag when the character is staff of any area, so a
subop can use the command from outside; the command then limits itself to
the subop's areas. `SubOp` powers matter little in single-player but remain
available for builders sharing a world across characters of the profile.

### File access flags

Two flags give file access to the `SHELL` command (alias `CMFS`, `.`):

- `FS: path` gives read and write access to the local file system below
  `path` (relative to `CoffeeMud/`, no leading slash).
- `VFS: path` the same for the virtual file system stored in the content
  database. `FS: ` with nothing after it means the whole folder.

A leading `AREA ` limits it to subops (`FS: AREA path`). A character who has
one of these (the `SYSOP` group has `FS: `, the `DEV` group has `FS:
RESOURCES`) can use `SHELL` to list, copy and delete files and `DUMPFILE`
to display them. A DEV game's `JRUN` runs scripts from `resources/` for the
same reason.

### Giving a character flags or groups

In builder mode (or with `CMDPLAYERS`), open the character editor and use the
Security Groups field:

```text
MODIFY USER Bran
```

The editor menu lists "Security Groups" among the fields and asks `Enter a
group to add/remove`. Enter a flag, `AREA flag`, a group name (without
`GROUP_`), a journal name or a path; entering one the character already has
removes it, and a blank line ends. A value that is none of these prints the
valid flags, groups and journal names. You can only grant a flag you have
yourself (a global flag needs you to have it everywhere, an `AREA` flag
anywhere), and a group only if you are a SysOp. The values are stored with
the character as a semicolon-separated list (`DEV;AREA GOTO`).

A character class can also carry flags: `getSecurityFlags` on the class
(the Archon class has them all through `SYSOPMASK`; a custom GenCharClass
has a security field). Class flags and player flags are added together.

### How a command decides whether you may use it

Every command class has a `securityCheck(MOB)` method. The command appears
to exist for you only if it returns true. Typical bodies:

```java
return CMSecurity.isAllowed(mob, mob.location(), CMSecurity.SecFlag.PURGE);
return CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.GOTO);
return SaveGame.isBuilderMode() || SaveGame.isDevMode();
return SaveGame.isBuilderMode() || SaveGame.isDevMode()
    || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDRECIPES);
return CMProps.isSinglePlayer();
```

The checks are layered:

1. **Playtest.** A character in `PLAYTEST` fails every check (not a SysOp,
   not staff, no flag), except inside `BUILD`.
2. **Suspended powers.** A flag listed in the character's `GOD` suspensions
   (or `-NODEATH` for `IMMORT`) fails, even for an Archon.
3. **SysOp.** A SysOp passes everything else.
4. A non-player (no `playerStats`) fails, and a mob that a player possesses
   has the player's powers only while the player has `SYSOPMSGS` on.
5. Otherwise the character must have the flag in its own security flags or
   its class's, counting `AREA_` flags only in its staff areas.

`isAllowed(mob, room, flag)` checks in the given room; `isAllowedAnywhere`
accepts a subop flag from anywhere; `isAllowedEverywhere` accepts only the
global flag.

Many creator tools do not use a flag at all: they test
`SaveGame.isBuilderMode()` or `SaveGame.isDevMode()`. That is how `GOD`,
`DEVLOG`, `ASSERT`, `KIT`, `TESTCHAR`, `TIMESCALE`, `RECORD` and `NOTE` work
in a DEV game without giving the character the Archon class. Others
combine: `LAW`, `PUZZLE`, `QUESTCHECK`, `ORGCHECK`, `VALIDATE`, `VOCAB`,
`VESSEL`, `PROGRESSION`, `ENCOUNTER` and `COMBATDEBUG` accept builder mode,
a DEV game, or the matching `CMD...` or `ABOVELAW` flag. And a few are
single-player controls, available to everyone: `WAIT`, `TIMEMODE`,
`RESTORE`/`SAVES`, `SAVE`, `PAUSE`, `MODULES`, `ITEMMODS`, `MODCRAFT`,
`COMBATRESULT` and `CHEAT`.

For the precise gate on each command see the generated
[command gates reference](tool-permissions.md): it quotes each
`securityCheck` body. It is a source excerpt, not a promise that every
subcommand shares the gate: `execute()` may add builder/DEV, filesystem,
target or operation restrictions. The generated
[tool reference](tool-reference.md) holds each command's syntax.

### Playtest and suspended powers

Two mechanisms take sysop powers away from a character who has them.

- **PLAYTEST** (builder mode only) suspends every power at once:
  aggressive mobs attack, followers follow, spawns spawn as for a player,
  and the prompt says `(playtest)`. `BUILD [command]` runs one command with
  powers back. `PLAYTEST OFF` ends it.
- **GOD** suspends single powers (next section), in builder mode or a DEV
  game.

Both are implemented in `CMSecurity.setPlaytest` and
`CMSecurity.setSuspended` and are checked at the top of every
`isAllowed...` method.

### JavaScript approval

`JSCRIPTS` in `coffeemud.ini` controls whether JavaScript inside scripted
behaviors runs:

| Value | Meaning |
| --- | --- |
| `0` | JavaScript never runs. |
| `1` | A script runs only after approval by a character with the `JSCRIPTS` flag (a SysOp or the `DEV` group). Approvals are made with `MODIFY JSCRIPT` (the command accepts any word starting `JSCRIPT`) and, made in builder mode, are kept in `world/jscripts.ini` so every game has them; an unapproved script is remembered with its text and refused until approved. |
| `2` | (default) Always runs. |

GenCommands, GenAbilities and GenTraps never need approval. `JSTIMELIMIT`
and `JSTIMELIMIT_JRUN` limit a script's running time in seconds (2 and 30).
In CoffeeSUD the player writes the world, so `2` is the setting; `1` is for
a world you hand to other people to play with scripts they did not read.
See [JavaScript](05-javascript.md).

## GOD and the developer cheats

`GOD` is available in builder mode and in a `DEV=1` game (the command's
`securityCheck` is `SaveGame.isDevMode() || SaveGame.isBuilderMode()`; it
cannot be ordered). It sets `Prop_DevCheats`
([source](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_DevCheats.java)),
an effect saved with the character like any other.

```text
GOD
GOD [CHEAT|POWER|ALL] ([CHEAT|POWER]...) (ON|OFF)
GOD RESET
```

- `GOD` alone lists the cheats, whether each is on, and any suspended powers.
- A **cheat name** turns that cheat on or off; with neither `ON` nor `OFF`
  it turns it over.
- A **power name** (`IMMORT`, `GOTO`, `ALLSKILLS`, `ORDER`, `CMDROOMS`, or
  any security flag) with `OFF` suspends that sysop power for this
  character; with `ON` it gives it back.
- `ALL` is every cheat; `GOD ALL OFF` also turns off what the sysop powers
  give, so you play as a mortal would.
- `GOD RESET` forgets everything: the effect is removed.
- Several words may be named before one `ON` or `OFF`. An unknown word
  gives `'x' is neither a cheat (...) nor a power.`

The cheats:

| Cheat | Effect | A sysop power that gives it (or part) when not set |
| --- | --- | --- |
| `NOCLIP` | Walk through closed and locked doors and past `Prop_Req*` entry checks; walking costs no movement. | `GOTO` |
| `NOCOST` | Skills cost no mana, movement or hit points, and need no wait between uses. | `ALLSKILLS` (the wait only) |
| `NODAMAGE` | Take no damage. | none |
| `NODEATH` | Hit points stop at 1. `-NODEATH` (`GOD NODEATH OFF`) also suspends `IMMORT`. | `IMMORT` |
| `SEEALL` | See the dark, the hidden, the invisible and sneakers; never blind. | none |
| `NOAGGRO` | Aggressive creatures leave the character be. | `ORDER` or `CMDROOMS` |

`GOD` stores its state as the text of `Prop_DevCheats`, a space-separated
list: a cheat name turns it on, `-NAME` turns a cheat off even where a
power would give it, and `-FLAG` suspends that security flag. `GOD` alone shows the state. The suspension only counts while
the effect is present: if the effect is removed from the character the
suspension lapses automatically.

Examples:

```text
GOD noclip noaggro on
GOD nodamage
GOD immort off
GOD all off
GOD reset
```

Use `GOD ALL OFF` and a normal `NEW` game before you call a quest or a puzzle
done: builder powers hide the very problems players hit. The shorter route
is `PLAYTEST ON` in builder mode.

`CHEAT ON` and `CHEAT OFF` are a different feature: they unlock the world's
options marked as cheats (`NEWCHARS=CHEAT`) and mark saves `[cheat]`. They
are for players, not developers; `GOD` is for developers.

## Administrator commands that still work

Every command below has a class in
`CoffeeMud/com/planet_ink/coffee_mud/Commands/`, a `securityCheck` that is
reachable in a CoffeeSUD game, and a help entry (`AHELP name`). "Gate" is what
`securityCheck` requires (all of these are open to a SysOp, so an Archon in
builder mode can use them all). The generated
[command gates reference](tool-permissions.md) quotes the code, and the
[tool reference](tool-reference.md) has the syntax.

### Moving, watching and acting

| Command | Gate | What it does here |
| --- | --- | --- |
| `GOTO [room ID/desc/mob/area]`, `GOTO PREVIOUS` | `GOTO` anywhere | Go to a room, a mob or an area. Also lets the character walk through doors. |
| `POOF` | `GOTO` group | Change the messages `GOTO` shows. |
| `AT [room/mob/area] [command]` | `AT` anywhere | Do a command somewhere else. |
| `AS [character] [command]` | `AS` anywhere, or any player in single-player | Do a command as another character or mob. In CoffeeSUD any player may use it on their own party characters (see [Saves and party](31-saves-and-party.md)). The `PORT x` form in the help refers to other hosts and does not apply. |
| `ASYNC [command]` | `ASYNC` anywhere | Run a command on its own thread. |
| `AFTER (EVERY/STOP/LIST) [n] [SECONDS/TICKS/MINUTES/HOURS] [command]` | `AFTER` here | Run a command later, or repeatedly. |
| `EVERY [ROOM/ITEM/MOB/EXIT] ... [command]` | `EVERY` here | Run a command for every matching object. |
| `TRANSFER [mob/ALL] [destination]` | `TRANSFER` here | Move a mob or the party to a place. The `SRVR` form belongs to other hosts. |
| `TRAILTO ...` | `TRAILTO` here | Find a route to an area or room (also `EVERYROOM`/`EVERYAREA`), or report rooms that cannot be reached. |
| `WHERE (AREA) ROOM/MOB/ITEM/... [mask]`, `WHERE SELECT:` | `WHERE` | Find things in the world; `SELECT:` takes an MQL query. |
| `POSSESS [mob]` (`POSS`) | `POSSESS` here | Control a mob; `QUIT` returns. Your powers move to the mob only if `SYSMSGS` is on. |
| `ORDER [mob] [command]` | `ORDER` here | Order anyone, not only followers. |
| `SNOOP [name]` | `SNOOP` here | Watch another live session. In a single-player game there is no one else's session unless party windows are on (unverified for windows). |
| `CLOAK (OFF)`, `WIZINV (OFF/NOCLOAK)` (`WIZINVISIBLE`, `NOWIZINV`) | `CLOAK`, `WIZINV` | Hide from other characters' lists and sight. |
| `WIZEMOTE [target] message` | `WIZEMOTE` | Staff emote. |
| `SYSMSGS` | `SYSMSGS` here, or already set | Toggle debug messages and extended output. Mobile NPCs will not enter or leave a room where a player has it on. |
| `BEACON [name]` | `BEACON` here | Set a start room. |
| `SWITCH` | profile on, or SysOp | Change which profile character you play. Covered in [Saves and party](31-saves-and-party.md). |

### Building and the world

These are used all the time by creators; they are explained in the building
chapters ([World workflow](02-world-workflow.md),
[Areas, rooms and exits](20-areas-rooms-exits.md), [Mobs](21-mobs.md),
[Items](22-items.md)).

| Command | Gate | Purpose |
| --- | --- | --- |
| `CREATE`, `MODIFY` (`MOD`), `DESTROY` (`JUNK`, `TEAR`) | the matching `CMD...` flag | Make, edit and remove objects, including `MODIFY USER` for characters and `DESTROY` of rooms and mobs. |
| `COPY [n] [thing]` | `COPY` group | Duplicate rooms, mobs and items. |
| `PURGE [thing]` | `PURGE` here | Destroy mobs or items without stopping respawn. |
| `RESTRING [item]` | `CMD` group or `RESTRING` | Rename or redescribe an item. |
| `GMODIFY`, `MERGE` | `GMODIFY`, `MERGE` | Bulk find and change. |
| `CATALOG` | `CATALOG` here | Manage the catalog of template mobs and items. |
| `EXPORT`, `IMPORT` | the export/import groups | Write and read `.cmare` data. `IMPORT` also reads ROM-style area files. |
| `LINK`, `UNLINK` | `CMDEXITS` | Join and separate rooms. |
| `GENERATE`, `GENLAYOUT`, `REGENERATE` | `CMDAREAS` | Random areas; see [Generation](12-generation.md). |
| `TEMPLATE`, `CHARGEN` | `CMDITEMS`/`CMDROOMS`; `CHARGEN` | Template tools; show an average character of a class and level. |
| `RESET ROOM/AREA` | `RESET` here | Revive mobs and items to their saved state. |
| `RESET REJUV`, `RESET RELEVEL`, `RESET MOBCOMBATSTATS`, `RESET ITEMSTATS` | `RESET`, partly SysOp | Rejuvenate, shift levels, or normalise mob and item stats (the `NOSAVE` option previews). |
| `SAVE` | single-player: everyone | Saves the game, or in builder mode writes world files; see [World workflow](02-world-workflow.md). |
| `WORLD ...` | `CMDROOMS`, builder mode or DEV | The world tools (`STATUS`, `DIFF`, `RELOAD`, `CHECK`, `BACKUPS`, `SET` and more); see [World workflow](02-world-workflow.md). |

### Diagnostics, scripting and testing

| Command | Gate | Purpose |
| --- | --- | --- |
| `LIST ...` | per list; see below | Lists of nearly everything. |
| `STAT ...` | the command is open to all; targets and forms may need the `STAT` flag | Characteristics of mobs, items and rooms, and the engine's statistics. |
| `TICKTOCK [n] (HOURS/...)` / `TICKTOCK SET [date]` / `TICKTOCK [THREAD]` | `TICKTOCK` here | Advance the game clock, set the date, or run a maintenance thread once. |
| `SHUTDOWN` | `SHUTDOWN` here | End the game. Forms: `SHUTDOWN` (asks), `SHUTDOWN NOPROMPT`, `SHUTDOWN FOR [reason]`, `SHUTDOWN IN [n] [unit]`, `SHUTDOWN AT [time]`, `SHUTDOWN CANCEL`. The code has no `RESTART` option. A builder should `SAVE` first; a normal game's `QUIT` is the normal exit. |
| `LOAD`, `RELOAD`, `UNLOAD` | `LOADUNLOAD` | Load or unload resources, areas, and class files (Java source or JavaScript). `UNLOAD HELP`, `UNLOAD ALL`, `UNLOAD CLASS x` refresh caches. |
| `SHELL`, `CMFS`, `.` | any `FS:`/`VFS:` path | A file manager within the granted paths. |
| `DUMPFILE (RAW) [USER/ALL] [file]` | `DUMPFILE` | Show a text file. |
| `JRUN`, `JS`, `JCONSOLE`, `MPCOMMAND` | `JSCRIPTS` | Run scripts and script commands from the command line; see [JavaScript](05-javascript.md). |
| `VARS`, `QUESTINFO` | `CMDQUESTS` | Script variables and quest state. |
| `TEST`, `TEST LIST` | SysOp | Run the engine's built-in tests (`LIST TESTS`). |
| `GOD` | builder or DEV | See above. |
| `PLAYTEST`, `BUILD` | builder mode | See above. |
| `DEVLOG`, `SCRIPTERRORS`, `SCRIPTPROF`, `SCRIPTTRACE`, `COMBATDEBUG`, `PROPCHECK`, `SCRIPTCHECK`, `QUESTCHECK`, `ORGCHECK`, `VALIDATE`, `PUZZLE`, `LAW`, `ASSERT`, `TESTCHAR`, `KIT`, `TIMESCALE`, `RECORD`, `NOTE`, `DEVWATCH`, `SANDBOX`, `RUNFILE`, `UNDO` | mostly builder mode and DEV | The creator and developer tools of CoffeeSUD; see [Testing and release](13-testing-release.md). |

`NOPURGE [player]`, `EXPIRE [player] [days]`, `CHECKPOINT` and `ROLLBACK`,
`PAUSE`, `TIMEMODE` and `WAIT` also exist, but the first two belong to the
removed player-purging and account-expiry systems, and the last four are
player controls described in [Saves and party](31-saves-and-party.md).

#### LIST

`LIST` is the main inspection command. Each topic needs one of several
flags (for example `LIST MOBS` needs `CMDMOBS`; the system lists need
`LISTADMIN`); a SysOp may list them all, and `LIST LIST` or `LIST HELP`
names the topics. A few that matter for an administrator:

| Topic | Shows |
| --- | --- |
| `LIST REPORTS` | Memory, CPU times, tick group counts and the status of the main service threads. |
| `LIST THREADS (SHORT) (EXTEND)` | Every Java thread and what it is doing. |
| `LIST THREAD [n]` | One thread. |
| `LIST TICKS (n/ACTIVE/PROBLEMS/mask) (column)` | The ticking objects; `PROBLEMS` lists those with the highest average thread time. |
| `LIST LOG (FRONT n/BACK n/SKIP n)` | The server log (`mud.log`). |
| `LIST WORLDINI` | World-settable keys, their values and sources. |
| `LIST WORLD` | The areas with rooms, state, population and level. |
| `LIST UNSAVED` | Rooms with mobs or items not yet saved (builder mode). |
| `LIST DISABLES`, `LIST DISABLEFLAGS`, `LIST ENABLEFLAG`, `LIST DEBUGFLAGS` | The active and available flags of the [flag section](#disable-enable-debug-save-and-nocache-flags). |
| `LIST RESOURCES (mask)` | Files and resources in memory. |
| `LIST CRON`, `LIST HOLIDAYS`, `LIST QUESTS` | Scheduled things. |
| `LIST INSTANCES` | Running instance areas. |
| `LIST OBJCOUNTERS` | Object counts. |
| `LIST FILEUSE [path]` | Where files are used. |
| `LIST TESTS` | The built-in tests (the help text misspells it `TETS`). |
| `LIST DBCONNECTIONS (LONG)`, `LIST SQL [query]` | The database connections; a raw query against the cache. |
| `LIST HELP`, `LIST LIST`, `LIST TAGS` | The list of list topics and tags. |
| `LIST SESSIONS`, `LIST USERS`, `LIST ACCOUNTS`, `LIST BANNED`, `LIST EXPIRED`, `LIST NOPURGE`, `LIST WHO`, `LIST POSTOFFICES`, `LIST NEWS` | Inherited multiplayer lists. They are still registered; with one local player they show at most that player (unverified). |

Content lists (`LIST ITEMS`, `ARMOR`, `WEAPONS`, `MOBS`, `ROOMS`, `AREA`,
`AREAS`, `LOCALES`, `EXITS`, `BEHAVIORS`, `PROPERTIES`, `RACES`, `CLASSES`,
`ABILITIES`, `SPELLS`, `SONGS`, `PRAYERS`, `CHANTS`, `SKILLS`, `COMMON`,
`THIEFSKILLS`, `DISEASES`, `POISONS`, `TRAPS`, `LANGUAGES`, `COMPONENTS`,
`EXPERTISES`, `FACTIONS`, `TITLES`, `ACHIEVEMENTS`, `RECIPES`, `SCRIPTS`,
`MATERIALS`, `ENVRESOURCES`, `QUESTNAMES`, `LINKAGES`, `UNLINKEDEXITS`,
`ORPHANS`, `ONEWAYDOORS`, `CONTENTS`, `PLANES`, `CLANS`, `GOVERNMENTS`,
`SOCIALS`, `COMMANDS`, `VERBS`, `MANUFACTURERS`, `SOFTWARE`, `TECH`,
`MAGIC`, `SPACE`, `PLANETS`, `SHIPS` and so on) are the builder's catalogues;
Their usage is in the generated [tool reference](tool-reference.md) and in
`AHELP LIST_name` (for example `AHELP LIST_TICKS`).

## Logs, DEVLOG and diagnostics

### The log files

The engine writes `mud.log` in the folder the game is started from
(`CoffeeMud/`). At each boot the older logs rotate: `mud.log` becomes
`mud1.log`, `mud1.log` becomes `mud2.log`, up to `NUMLOGS` files (shipped
`10`). The files are ignored by git.

The log has channels, each set to one of `ON` (terminal), `OFF`, `FILE`
(`mud.log`), `BOTH`, or `OWNFILE` (its own file, `mud_help.log` for help):

| Key | Shipped | Carries |
| --- | --- | --- |
| `SYSMSGS` | `BOTH`, forced to `FILE` | Informational lines. |
| `ERRMSGS` | `BOTH`, forced to `FILE` | Errors and abnormal events, including script errors. |
| `WRNMSGS` | `BOTH`, forced to `FILE` | Warnings. |
| `DBGMSGS` | `BOTH`, forced to `FILE` | Debug lines; needs `DEBUG` flags. |
| `HLPMSGS` | `OWNFILE` | Failed `HELP` requests. |
| `KILLMSGS` | `OFF` in the file as `KILMSGS` | Kills. The key the code reads is `KILLMSGS`; the shipped file's `KILMSGS=OFF` has no effect (the channel is off when blank). Set `KILLMSGS` to turn it on. |
| `CBTMSGS` | `OFF` | Detailed combat and XP lines. |
| `ACCMSGS` | `OFF` | Web access log; inert. |

A channel's value may carry a rolling rule: `OWNFILE<9999L` rolls after
9999 lines, `<5M` after 5 megabytes (`B`, `K`, `M`, `G`), and a leading
number such as `55OWNFILE<999L` keeps 55 files.

The terminal belongs to the player, so the launcher sends the four main
channels to `FILE`. To watch the log live without editing the file, use
`DEVLOG` or run `tail -f mud.log` in another terminal.

`LIST LOG` shows the log in the game (`LISTADMIN`).

### DEVLOG

`DEVLOG` shows the builder every line the engine logs as it is logged, in a
compact coloured form, whatever the log channels say. It needs builder mode
or a DEV game.

```text
DEVLOG ON (ERROR|WARN|INFO|DEBUG) (MODULE [MASK])
DEVLOG OFF
DEVLOG LAST ([number]) (ERROR|WARN|INFO|DEBUG) (MODULE [MASK])
DEVLOG CLEAR
DEVLOG WINDOW (ERROR|WARN|INFO|DEBUG) (MODULE [MASK])
DEVLOG WINDOW OFF
```

Examples:

```text
devlog on warn module Scripting,Quest*
devlog last 30 error
```

The level is a floor: `WARN` shows `ERROR` and `WARN`. The module mask is a
comma-separated list of module names with `*` as a wildcard. A repeated line
is shown once with a count `(x3)`. `DEVLOG LAST` shows the recent lines
(history is kept in memory). In builder mode the last setting is kept in
`devlog.txt` beside the database and wins over the ini key; in a DEV game
the ini key `DEVLOG` sets the start value:

```ini
DEVLOG=WARN Scripting,Quest*
```

`DEVLOG WINDOW` opens a display-only log window with the same level and module
options (`DEVLOG WINDOW OFF` closes it). It needs a display; without one it says
so and the terminal carries on.

`MPDEBUG` and JavaScript's `debug()` send lines to the debug log, which
`DEVLOG LAST` keeps and the watcher is always told of, and which do nothing
in a normal game.

### Script errors

Script errors are always logged. `SCRIPTERRORS` (the command) lists the last
200 errors since the game began, each with file and line, the command, what
went wrong and the host and room:

```text
SCRIPTERRORS
SCRIPTERRORS 50 elder.script
SCRIPTERRORS CLEAR
```

The ini key `SCRIPTERRORS` (`LOG`, `NOTICE`, `SHOW`) says what a **normal**
game does with them; builder mode and DEV games tell the builder each one.
`SCRIPTPROF` times scripts; `SCRIPTSLOW=ms` writes a warning for any trigger
that runs longer. `VARS` shows and sets script variables.

### The DEBUG flags

`DEBUG=` takes comma-separated names (see
[the flag section](#disable-enable-debug-save-and-nocache-flags)) and
needs `DBGMSGS` not `OFF` (it is `FILE` in a CoffeeSUD game). Flags
for features that are still in the game, and so may log, include `PROPERTY`, `ARREST`,
`CONQUEST`, `MUDPERCOLATOR`, `GMODIFY`, `MERGE`, `BADSCRIPTS`,
`CLASSLOADER`, `DBROOMPOP`, `DBROOMS`, `CMAREA`, `MISSINGKIDS`,
`FLAGWATCHING`, `UTILITHREAD`, `MAPTHREAD`, `VACUUM`, `EXPORT`, `IMPORT`,
`MESSAGES`, `SHUTDOWN`, `SQLERRORS`, `SCRIPTVARS`, `SCRIPTTRACE`,
`QUESTSCRIPTS`, `RANDOMQUESTS`, `AUTOAWARDS`, `CALENDAR`, `CRONTRACE`,
`ELECTRICTHREAD` and `EVERYTHING`. The network ones (`TELNET`, `HTTP...`,
`I3`, `IMC2`, `SMTP...`, `BININ`, `BINOUT`, `STROUT`, `INPUT`, `OUTPUT`,
`GMCP`) belong to removed systems. `DEBUG` is a developer's tool: it fills
the log.

`SCRIPTTRACE` (the command) and `DEBUG=SCRIPTTRACE` are different: the
command traces one host and shows the builder; the flag logs.

## Maintenance tasks

Routine jobs for a creator or a developer.

| Task | How |
| --- | --- |
| Reload `coffeemud.ini` | `RESET INIFILE` (needs `RESET`). Re-reads the file, system variables, channels and the `DISABLE`, `ENABLE`, `DEBUG` and `SAVE` flags. Not groups or `SYSOPMASK`. World.ini and command-line values stay. |
| Change a world setting | `WORLD SET KEY=VALUE` in builder mode, then restart. |
| Refresh a cache | `UNLOAD HELP`, `UNLOAD ALL`, `UNLOAD INIFILE`, `LOAD RESOURCE name`; then use the thing. |
| Save the world files | `SAVE` in builder mode. A copy of each file it replaces goes to `world/.backup/` (`BACKUPS` many; `WORLD BACKUPS` lists them, `WORLD RESTOREFILE` puts one back). |
| Check the world | `WORLD CHECK`, or `./sud.sh CHECK=1` for a headless check with an exit code. |
| Find the files that did not load | `WORLD PROBLEMS`, `WORLD STATUS`. |
| Pull in files edited outside the game | `WORLD RELOAD` (or `DEVWATCH ON`). |
| Back up a campaign | Copy a folder from `SAVEDIR`; or `SAVE name`, `CHECKPOINT`. See [Saves and party](31-saves-and-party.md). |
| Reset the content cache | Delete the contents of `resources/fakedb/` except `fakedb.schema`; the next start rebuilds it from the world files. |
| Clear the working databases of dead games | Done at start (`SaveSlots.deleteWorkingDatabases`). A crash can leave `saves/.play-*` folders; they are safe to delete when no game is running. The `saves/.owner.lock` file is an operating-system file lock that is released when the game's process ends; do not delete it to start a second game. |
| Free memory | `LIST REPORTS`, `LIST OBJCOUNTERS`; remove `ROOMVISITS` (see below); use `NOCACHE`. |
| Fire a schedule now | `TICKTOCK [thread]`, `TICKTOCK 6` (hours). |
| Test as a mortal | `PLAYTEST ON`, or `GOD ALL OFF`. |

`TICKTOCK` takes a number and `HOURS`, `DAYS`, `MONTHS`, `YEARS` (or a
negative number to go back), `TICKTOCK CLANTICK` for the clan maintenance,
`TICKTOCK SET [Y#/M#/D#/H# | M#/D#/Y# H#h]` for a date, or a thread name as
shown by `LIST REPORTS`.

## The content database is a cache

The world is the files in `world/`: area files, custom objects, the catalog,
quests, templates and so on (see [World workflow](02-world-workflow.md)).
The "database" the engine reads is the **fake database** in
`resources/fakedb/`, which is only a **cache** built from those files, plus
(in builder mode) the profile's characters. It is not source. It is not
shipped (git ignores `resources/fakedb/*` except `fakedb.schema`).

What happens at startup (`SaveGame.beforeBoot`, `WorldFiles`):

1. The launcher computes a checksum of the world files (their paths and
   contents; `dev/` areas and optional module files are left out) and
   compares it to `resources/fakedb/world.sum`.
2. If they match, the content tables are copied. A normal game plays on a
   **copy** in `saves/` (a "working database"); builder mode plays on the
   cache itself.
3. If they differ (you edited a file, pulled from git, or changed branch), the
   game starts from an empty database and **rebuilds the world from the
   files** (`The world files have changed: rebuilding the world from them...`).
   A normal game then copies what it built back to the cache so the next
   start is quicker.
4. A normal game never writes the world files. Builder mode writes them on
   `SAVE` and when it quits.

What this means for an administrator:

- **Back up `world/`, not `resources/fakedb/`.** Losing the cache costs a
  slower first start; losing the files loses the world.
- **A stale cache is harmless.** The checksum catches it. Deleting
  `resources/fakedb/world.sum` forces a rebuild.
- **Editing files outside the game is fine** when the game is not running (or
  with `WORLD STATUS`/`WORLD RELOAD`/`DEVWATCH` while it is); see
  [World workflow](02-world-workflow.md). An `Unknown class` in a file makes
  it "load only in part"; the file is then not written back until fixed
  (`WORLD PROBLEMS`; `SAVE FORCE` overrides).
- **A normal game is disposable.** Its working database is deleted at the
  next start; only a saved game (`saves/NAME/`) keeps the campaign's state.
- **Characters live in the cache only in builder mode.** In a normal game
  they are in the save slot.
- **Only one process may use a save folder.** A second launch is refused
  with a message from `SaveSlots.acquire`.
- **Other JDBC databases are not supported** for the single-player systems:
  `DBSERVICE` must start `jdbc:fakedb:`.
- `LIST SQL [query]` can read the cache; it is for diagnosis, not editing.

## Performance and threading

CoffeeSUD runs the whole world on **one game thread** in single-player
(`ServiceEngine.run` sets `gameThread`; `oneThread` runs each due tick group
in turn). The multi-threaded design of CoffeeMud is mostly dormant.

| Setting | Shipped | What it does now |
| --- | --- | --- |
| `TICKTIME` | `4000` | Milliseconds per tick: the throttle on combat, spell duration, rejuvenation, and nearly every timed rule. Lower is faster and heavier. Values below 500 become 4000. Use `TIMESCALE` instead for a quick test. |
| `MILLISPERMUDHOUR` | `600000` | Calendar speed relative to real time. |
| `TIMESCALE` | `1` | Runs the game clock `0.1` to `20` times real time (affects everything on the game clock: ticks, cooldowns, decay, calendar). The `TIMESCALE` command changes it in builder mode or a DEV game. |
| `TIMEMODE` | `REALTIME` | `TURNS` moves the clock only when the player acts, so the world costs nothing while you think. |
| `OBJSPERTHREAD` | `512` | Ticking objects per tick group. With one game thread the groups run one after another, so this only decides how objects are batched, and how long one group can hold the thread. |
| `MINWORKERTHREADS`, `MAXWORKERTHREADS` | `5`, `60` | Size of the worker pool for work that runs off the game thread. Values of `0` or less for the maximum mean no limit. Raising them does not make a game thread faster. |
| `JSTIMELIMIT`, `JSTIMELIMIT_JRUN` | `2`, `30` | Seconds before a runaway script is stopped. Keeps one bad script from freezing the single game thread. |
| `SCRIPTSLOW` | `0` | Warn about slow script triggers (the game thread waits for them). |
| `NOCACHE` | `MAPFINDERS` | See below. |
| `COMPRESS` | blank | Optimise memory over CPU: `GENMOBS`, `ITEMDESC`, `ROOMDESC`, `RESOURCES`, `MOBDESC`. |
| `CATCHUP` | blank (on) | `OFF` skips the catch-up an awakened area does. Cheap to turn off in large worlds. |
| `AUTOREACTION` | `PLANAR` | Leave low; `AREA,RACE,NAME,DEITY` costs CPU and memory. |
| `DISABLE=ROOMVISITS` | not set | Stops remembering every room visited; saves memory in huge worlds, but disables exploration statistics (`DISCOVERYXP`, exploration achievements). |
| `DISABLE=PASSIVEAREAS` | not set | Turns off the sleep of areas the player has not visited for 30 minutes; costs CPU. Leave as is. |

Use `LIST REPORTS`, `LIST TICKS PROBLEMS` and `SCRIPTPROF` to find a slow
object before changing a setting. The cheapest speed-up is almost always to
fix one script or behavior that ticks too often.

Engine developers: thread and tick design is in
[Engine programming](32-engine-programming.md).

## DISABLE, ENABLE, DEBUG, SAVE and NOCACHE flags

These five keys hold comma-separated flag lists. `DISABLE`, `ENABLE`,
`DEBUG` and `SAVE` are re-applied by `RESET INIFILE`; `DISABLE` and `ENABLE`
are world-allowlisted.

### DISABLE

Shipped value:

```ini
DISABLE=DBERRORQUESTART, COMMAND_POSE, QUITREASON, RETIREREASON, CHANNELAUCTION
```

Unknown or duplicate flags are logged as `Unknown or duplicate disable flag`.
Flags are case-insensitive. Generic forms, each a family:

| Form | Disables |
| --- | --- |
| `COMMAND_X` | The command class `X` (the class name, not the word you type). |
| `ABILITY_X` | The ability class `X`. |
| `RACE_X`, `CHARCLASS_X`, `FACTION_X`, `EXPERTISE_X` | That race, class, faction or expertise by ID. |
| any of the above ending `*` | Every ID that starts with what precedes: `ABILITY_Spell_*` disables every spell; `ABILITY_Chant_*` every chant. |

Named flags that still do something in a single-player game:

| Flag | Disables |
| --- | --- |
| `EXPERIENCE`, `LEVELS`, `UNLEVELXP`, `UNLEVEL` | XP gain and loss; level changes; losing levels. |
| `RACES`, `CLASSES`, `STDRACES`, `STDCLASSES` | Race and class systems (new characters get `PlayerRace`/`PlayerClass`, or the standard ones); the stock player races and classes only. |
| `CHARCRSTAT`, `CHARCRRACE`, `CHARCRCLASS`, `CHARCRGENDER`, `FULLSTATS` | Steps of character creation and the full stat view. |
| `CLASSTRAINING` | Changing class with `TRAIN`. |
| `HUNGER`, `THIRST`, `FATIGUE`, `HYGIENE`, `FOODROT` | Those survival systems. |
| `ALLERGIES`, `AUTODISEASE`, `ALL_AGEING`, `SLOW_AGEING` | Allergies, automatic diseases, ageing, the one-year cap on ageing while away. |
| `DARKNIGHTS`, `DARKWEATHER`, `WEATHER`, `WEATHERCHANGES`, `WEATHERNOTIFIES`, `TODNOTIFIES` | Night and weather darkness; weather; notices. |
| `PASSIVEAREAS` | The sleeping of unvisited areas. |
| `ARREST`, `CONQUEST`, `CLANTICKS` | The legal system, area conquest, clan automation. |
| `EMOTERS`, `MOBILITY`, `MUDCHAT`, `RANDOMMONSTERS`, `RANDOMITEMS` | Behaviors of those kinds. |
| `QUESTS`, `RANDOMQUESTS`, `SCRIPTING`, `SCRIPTABLE`, `SCRIPTABLEDELAY` | Quests; MOBPROG scripting. |
| `EQUIPSIZE` | Armour size fitting. |
| `ROOMVISITS` | Remembering rooms entered. |
| `LANGUAGES`, `AUTOLANGUAGE` | Languages; the auto-switching of the spoken language. |
| `LOGOUTS` | Removal of characters from the world on log-off. |
| `SHOWXP` | Seeing experience numbers. |
| `COMBATSTATS`, `ATTRIBS`, `STATS`, `STATSTHREAD` | Stat counters and the statistics system. |
| `ITEMREJUV`, `MOBTEACHER`, `DEATHCRY`, `DIS955RULE`, `AUTOMOODS`, `TECHLEVEL`, `BADEXITS`, `CRONJOBS`, `SPACETHREAD`, `AUTOAWARDS`, `NPCAUTOAWARDS`, `CHANNELBACKLOGS`, `MQLCACHE`, `ITEMGENCACHE`, `CATALOGCACHE`, `SPECOMBATTHREAD`, `ELECTRICTHREAD` | Smaller engine features and caches; see `DisFlag` in `CMSecurity.java`. |
| `STDACHIEVEMENTS`, `STDTITLES`, `STDSOCIALS` | The stock `achievements.ini`, `titles.ini` and `socials.txt`, leaving the world's own copies. |
| `SAVETHREAD`, `UTILITHREAD`, `THREADTHREAD`, `MAPTHREAD`, `JOURNALTHREAD`, `CATALOGTHREAD`, `PLAYERTHREAD`, `SESSIONTHREAD` | Individual maintenance threads. For testing only. |
| `DBERRORQUE`, `DBERRORQUESTART` | The saving of failed SQL and its retry at boot. Leave `DBERRORQUESTART` as shipped. |
| `FATAREAS`, `THINAREAS`, `FATGRIDS`, `THINGRIDS`, `PROPERTYOWNERCHECKS` | Area and grid caching and property ownership checks. Leave alone. |
| `COMMAND_POSE`, `QUITREASON`, `RETIREREASON`, `CHANNELAUCTION` | The `POSE` command; the shipped default. `QUITREASON` is not read anywhere. |

Flags that belong to removed systems and do nothing useful: `LOGINS`,
`NEWPLAYERS`, `NEWCHARACTERS`, `MAXNEWPERIP`, `MAXCONNSPERIP`,
`MAXCONNSPERACCOUNT`, `CONNSPAMBLOCK`, `AUTOPURGE`, `PURGEACCOUNTS`, `MXP`,
`MSP`, `MCCP`, `MSDP`, `GMCP`, `MSSP`, `MCP`, `I3`, `IMC2`, `GRAPEVINE`,
`SMTPCLIENT`, `ANSIPROMPT`. (`MSSP`, `MCP`, `I3`, `IMC2`, `GRAPEVINE` and
`QUITREASON` are not read by any Java code at all.)

### ENABLE

`ENABLE` turns on races and classes that are off by default. The only forms
that do anything are:

```ini
ENABLE=RACE_OGRE, RACE_GNOLL, CHARCLASS_FIGHTER OGRE
```

`RACE_ID` enables a race for players; `CHARCLASS_ID` (optionally followed by
race IDs) enables a class, restricted to those races. IDs are upper-case.

### DEBUG

Comma-separated names from `CMSecurity.DbgFlag`; see
[the DEBUG flags](#the-debug-flags). `LIST DEBUGFLAGS` shows the available
ones with a description, and `LIST DEBUGS` those on.

### SAVE

| Flag | Effect |
| --- | --- |
| `NOPLAYERS` | Turn off automatic character saving and the shutdown save: manual `SAVE` only. |
| `NOPROPERTYMOBS`, `NOPROPERTYITEMS` | Do not save player or clan property mobs or items. |
| `ROOMMOBS`, `ROOMSHOPS`, `ROOMITEMS` | Re-save the mobs, shopkeepers or items of every room at shutdown. |

These belong to the original persistence model. CoffeeSUD saves the world
through its save slots instead, so leave `SAVE` blank unless you know you
need one.

### NOCACHE

Comma-separated: `GENMOBS`, `ROOMDESC`, `FILERESOURCES`, `CATALOG`,
`MAPFINDERS`, `ACCOUNTS`, `PLAYERS`. Each makes that subsystem fetch from the
database or file each time instead of keeping it in memory; it saves memory
and costs CPU. Shipped `NOCACHE=MAPFINDERS`; the comment suggests removing it
if script writers are slowing the game, since `MAPFINDERS` makes
find-by-name lookups slower.

## CoffeeMud administration that no longer applies

The original Archon guide describes these. They do not exist or do nothing in
CoffeeSUD.

| Original feature | Status |
| --- | --- |
| Telnet, web and SMTP servers, the MUDGrinder control panel, `PORT`, `PROXY`, `BIND` | Removed. One console session; no ports. |
| Player accounts, passwords, e-mail, trial and expiry, idle log-off | Replaced by the local **profile**. `ACCOUNT*`, `EMAILREQ`, `IDLETIMERS`, `HASHPASSWORDS`, `EXPIRE`, `NOPURGE` are inert. |
| `BAN`, `BOOT`, `ANNOUNCE`, `MULTIWATCH` commands | No command class. (The `BAN` and `BOOT` flags still exist for `DESTROY BAN` and `DESTROY SESSION`.) |
| Intermud (I3, IMC2, Grapevine), Discord, Twitter, polls | Flags remain, features removed. |
| Channels between players, forums, command journals (BUG, IDEA, TYPO) | Removed. Only `INFO` and `QUESTCHAT` channels remain, for the world's own news and quest announcements. |
| The `AS ... PORT x`, `TRANSFER ... SRVR` forms | Multi-host; do not apply. |
| `SHUTDOWN RESTART` | Not in the command's code. |
| Multi-host servers (`PRIVATERESOURCES`) | Inert. |
| Player-versus-player law (`PLAYERKILL`, `CORPSEGUARD` and the `PKILL` flag) | A player's characters are one person; `PARTYFIGHT` is the exception. |
| MSP, MXP and MSXP protocols, charset negotiation | Telnet client features; no clients. |
| Clan web sites, forums and public clan categories | Removed. Clans exist in the world as organisations; see [Society](09-society.md). |
| Database back ends (MySQL, Postgres, Derby, HSQLDB) as the live store | Not supported; the fake database is a cache. |
| `UpgradeTool` / `upgrade.sh` for INI changes | Still present in `application/UpgradeTool.java` for merging an old `coffeemud.ini`; unrelated to world files. |

## Worked examples

### Example 1: a test game with developer powers, then a mortal check

You are building a quest and want to jump around without becoming an Archon.

1. Add a group for yourself to `coffeemud.ini` (or use the shipped `DEV`):

   ```ini
   GROUP_TESTER=GOTO, AT, TRANSFER, STAT, WHERE, LISTADMIN, CMDQUESTS, CMDMOBS, JSCRIPTS, AHELP, FS: RESOURCES
   ```

2. Start a DEV game that uses it, record the log, and speed up the clock:

   ```sh
   cd CoffeeMud && ./sud.sh DEV=1 DEVGROUP=TESTER TIMESCALE=8 DEVLOG="WARN Scripting,Quest*"
   ```

3. In the game:

   ```text
   GOD nocost noclip on
   GOTO Harbor#12
   DEVLOG LAST 20 error
   SCRIPTERRORS
   VARS LIST
   ```

4. Before you call it done, play as a mortal:

   ```text
   GOD ALL OFF
   ```

   Then restart without `DEV=1` and start a `NEW` game: a mortal has no `GOTO`
   and the game then tests what players will really see.

### Example 2: a world that ships its own rules

You want a hard, short, turn-based game with a four-person party that can
only be saved at inns.

1. Create `world/world.ini`:

   ```ini
   GAMENAME=Salt Marsh
   START=Ferry#1
   DEATH=CHECKPOINT
   MORGUE=START
   TIMEMODE=TURNS
   SAVEMODE=SAVEPOINTS
   PARTYMAX=4
   PROFILEMAX=6
   PLAYERDEATH=DOWNED 4 10, EXPERIENCE
   COMBATSYSTEM=TURNBASED
   DISABLE=DBERRORQUESTART, COMMAND_POSE, QUITREASON, RETIREREASON, CHANNELAUCTION, HUNGER, THIRST
   ```

2. Check what the engine will use (builder mode):

   ```text
   WORLD
   ```

   Each key shows `world.ini` as its source. Anything shown as ignored at
   startup was not an allowlisted key: see [world-settings.md](world-settings.md).

3. Mark inns as save points with `Prop_SavePoint` (see
   [Properties reference](23-properties-reference.md)) and try a `NEW` game.

4. Override one value for a single run without editing the file:

   ```sh
   ./sud.sh TIMEMODE=REALTIME
   ```

### Example 3: reading a problem out of the logs

A mob stops answering after you edited its script.

1. In builder mode: `SCRIPTERRORS 20` lists the newest script errors with
   file and line (`progs/elder.script:212 MPECHOT ...`).
2. `DEVLOG ON WARN MODULE Scripting` shows new ones as they happen.
3. `LIST LOG BACK 40` or `tail -n 40 mud.log` shows the log tail.
4. Edit the script and `WORLD RELOAD CHANGED`, or `SCRIPT RESET` the host.
5. If it was slow, not wrong: `SCRIPTPROF`, or set `SCRIPTSLOW=50` and read
   the warnings in `mud.log`.

## Persistence notes

| Setting or state | Where it lives | Survives |
| --- | --- | --- |
| `coffeemud.ini` | `CoffeeMud/coffeemud.ini`, tracked in git | Everything; edit by hand. |
| `world.ini` | `WORLDDIR/world.ini`, part of the world | World edits and git. `WORLD SET` writes it. |
| Launch overrides | The command line | One launch only. |
| `GOD` cheats and suspensions | `Prop_DevCheats` on the character | The character's save; `GOD RESET` clears them. DEV groups are removed from characters of a non-DEV game when it starts. |
| Security groups on a character | The character's `Security Groups` field | The character (cache in builder mode, save slot in play). |
| Time mode, save mode, party options | The save header | A restore keeps the saved values and ignores the ini for them. |
| JavaScript approvals | `WORLDDIR/jscripts.ini` | Written only in builder mode. |
| `DEVLOG` setting | `devlog.txt` beside the database in builder mode | Builder sessions; the ini key is the fallback. |
| Logs | `mud.log`, `mud1.log` ... | Rotated each boot up to `NUMLOGS`. |
| Content cache | `resources/fakedb/` | Disposable; rebuilt from the world files when `world.sum` disagrees. |
| Working databases | `saves/.play-*` | Disposable; deleted at the next start. |

A reload (`WORLD RELOAD`) changes the world definitions, not `coffeemud.ini`;
`RESET INIFILE` does the reverse.

## Troubleshooting

- **A ini change did nothing.** Check the key is not [inert](#inert-keys),
  not [forced by the launcher](#settings-the-launcher-forces), and not
  overridden: `WORLD` shows the source. If it is a `GROUP_` or `SYSOPMASK`,
  restart. If it is a `FORMULA_` or calendar key, restart too.
- **`world.ini: X is not a world setting, so ignored`.** `X` is not on the
  [allowlist](world-settings.md). Move it to `coffeemud.ini` or the command
  line.
- **Command not found / "You are not allowed".** The command's `securityCheck`
  failed. In a normal game only DEV or builder mode opens the creator tools.
  Start with `DEV=1` or `BUILDER=1`, or check the flag with the table in
  [Security flags](#security-flags). In `PLAYTEST` every flag is off; use
  `BUILD`.
- **`DEV=1 is for a normal game: builder mode has every power already.`**
  You passed both; remove one.
- **Archon powers missing in a game started with `BUILDER=1`.** Powers come
  from the Archon class (`SYSOPMASK`). Builder mode makes the player an
  Archon as they come in (`SaveGame.makeArchon`); check `STAT` on yourself
  for the class, and that `SYSOPMASK` was not edited.
- **`Unknown security flag: X in group Y` in `mud.log`.** The `GROUP_Y`
  line names a flag that is not in `SecFlag` (a typo, or one of the inherited
  names such as `TASKS`).
- **The log shows nothing on screen.** Intended: the launcher sends the main
  channels to `FILE`. Use `DEVLOG`, `tail -f mud.log`, or
  `./sud.sh DBGMSGS=BOTH`.
- **`KILMSGS=ON` does nothing.** The key is `KILLMSGS`.
- **The game rebuilds the world at every start.** The world files' checksum
  differs from `resources/fakedb/world.sum` each time: a file changes between
  runs (a tool rewriting it) or the cache folder cannot be written. `WORLD
  STATUS` lists files changed outside the game.
- **"The world files and saved games need the fake database, so they are
  off."** `DBSERVICE` does not start `jdbc:fakedb:`.
- **A second launch refuses to start.** Another game holds the save
  folder's lock. Close it, or give the second its own `SAVEDIR`.
- **`JSCRIPTS=1` and a script is silent.** It is waiting for approval:
  `MODIFY JSCRIPT` in builder mode.
- **Builder edits are lost.** A mob or item you `CREATE` is in the room only
  until `SAVE ROOM` or `SAVE CHANGED`; `LIST UNSAVED` shows the rest, and
  `BUILDERAUTOSAVE=1` does it after each command.

## See also

- [world-settings.md](world-settings.md): the generated list of world.ini keys.
- [tool-permissions.md](tool-permissions.md): each command's `securityCheck`.
- [tool-reference.md](tool-reference.md): syntax of every command.
- [Installing and running](18-installing-and-running.md): launching, modes and the folder layout.
- [World workflow](02-world-workflow.md): the world folder, SAVE, WORLD commands, UNDO.
- [Saves and party](31-saves-and-party.md): saves, profile, time and pause.
- [Testing and release](13-testing-release.md): the creator's test and check tools.
- [Optional modules](14-optional-modules.md): `MODULES` and `MODULESETTINGS`.
- [State, persistence and reload matrix](state-and-persistence.md).
- [Engine programming](32-engine-programming.md): threads, ticks and the class path.
