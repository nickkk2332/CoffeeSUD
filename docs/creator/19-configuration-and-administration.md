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

This is why a log setting you change in `coffeemud.ini` appears to be
ignored: for the four channels above, put the new value on the command line
(`./sud.sh DBGMSGS=BOTH`) or in nothing else.

### Launch and mode keys

Read by the launcher (`SUD.java`) unless stated.

| Key | Shipped | Meaning |
| --- | --- | --- |
| `BUILDER` | blank | `1`, `Y...`, `T...` or `ON` plays on the database cache itself, makes the player an Archon with the in-game editors, writes edits back to the world files, and has no saved games. Blank is a normal game. |
| `DEV` | not in ini (commented) | `1`, `Y...`, `T...` or `ON` starts a developer's game (see above). |
| `DEVGROUP` | not set | Name of the security group a DEV game gives each character; `GROUP_` before the name is accepted and removed. Default `DEV`. |
| `DEVSTART` | not set | Room ID where a new character starts in builder mode and DEV games (for example a dev hub). Ignored in a normal game. |
| `DEVLOG` | not set | Initial DEVLOG level and module mask for DEV games and as a fallback in builder mode; see [Logs, DEVLOG and diagnostics](#logs-devlog-and-diagnostics). |
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
| `DEFAULTPLAYERFLAGS` | blank | Player option flags on for new characters (`AUTOLOOT`, `AUTOEXITS`, ...). |
| `RECOVERRATE` | `1` | Five-tick periods between natural recovery. |
| `REMORTMASK`, `REMORTRETAIN` | `-LEVEL +>60`, see file | The REMORT command's rules. |
| `HUNGER`, `THIRST` | `500,100,100`, `1000,100,100` | Capacity and two percentages. |
| `EXPDEFER`, `RPAWARDS`, `LOGOUTMASK`, `PRIDECATS`, `PRIDECOUNT`, `XPMOD` | blank | Experience deferral, role-play awards, logout rule, top-player lists, and global XP modifiers. These are multiplayer-era; see the [inert list](#inert-keys) for which still do anything. |

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
removed from the fork. Setting them has no effect in a CoffeeSUD game.

| Keys | Why inert |
| --- | --- |
| `PORT`, `PROXY`, `MPCPKEY`, `BIND`, `BACKLOG`, `DOMAIN` | The telnet listener is replaced by one console session (`MUD.runConsole`). Nothing listens. |
| `MAXCONNSPERIP`, `MAXNEWPERIP`, `MAXCONNSPERACCOUNT`, `BLACKLISTFILE`, `WHITELISTIPSCONN`, `WHITELISTLOGINS`, `WHITELISTIPSNEWPLAYERS` | Connection limits and IP lists; there are no remote connections. |
| `MUDSTATE`, `ADMINEMAIL` | Reported to intermud and MSSP, both removed. |
| `EMAILREQ`, `IDLETIMERS`, `COMMONACCOUNTSYSTEM` | Replaced by the launcher (see above); `COMMONACCOUNTSYSTEM` is forced to 50. |
| `ACCOUNTEXPIRATION`, `TRIALDAYS`, `EXPCONTACTLINE`, `ACCOUNTPURGE`, `AUTOPURGE`, `HASHPASSWORDS` | Account and player purging and passwords. Leave `AUTOPURGE` at `>=0 0 0` (off). |
| `COMMANDJOURNALS`, `FORUMJOURNALS`, `JOURNALLIMIT`, `CHANNELBACKLOG` | Player-to-admin journals and web forums. The shipped lists are empty. |
| `CLANWEBSITES`, `CLANFORUMDATA`, `PUBLICCLANCATS`, `CLANVOTEO`, `CLANVOTED`, `CLANVOTER` | Clan web pages, forums and vote timings. The three vote keys are not read by any Java code at all. |
| `SOUNDPATH`, `MXPIMAGEPATH`, `MSXPVARS` | MSP, MXP and MSXP client protocols. |
| `DISCORD_JAR_PATH`, `DISCORD_BOT_KEY`, `TWITTER_*` | Chat integrations. |
| `PRIVATERESOURCES`, `PRIVATERESOURCEPATH` | Resource sharing for several hosts in one process. |
| `PROMPTBEHAVIOR` | Telnet go-ahead and carriage-return suffix. |
| `KILMSGS`, `ACCMSGS` | `KILMSGS` is misspelt; the log channel is `KILLMSGS` (see [Logs](#logs-devlog-and-diagnostics)). `ACCMSGS` is the web server access log. |
| `CHANNELFILTER`, `EMOTEFILTER`, `SAYFILTER`, `POSEFILTER` | Profanity filters for player-to-player text. Left over; no players share a channel. |
| `PRIDECATS`, `PRIDECOUNT`, `RPAWARDS`, `EXPDEFER`, `LOGOUTMASK` | Multiplayer features (top-player lists, role-play XP, deferred XP, restricting log-off). They load without error but give nothing a single player can use. |
| `I3ROUTERS`, `SMTPSERVERNAME`, `GVCLIENTID`, `GVCLIENTSECRET` | Intermud, mail and Grapevine; not in the shipped file. |
| `LANGCHAIN4J_JAR_PATH`, `LANGCHAIN4J_LLM_TYPE`, `LANGCHAIN4J_BASEURL` | Optional LLM bridge for the `LLM` command and `LLMIFY`; unrelated to world authoring. |

Keys in this table other than the forced and the verified-unread ones are
marked inert because the system they configure has no entry point in a
single-player game; a few (the profanity filters, `CHARSETINPUT`) may still
be applied to the console stream. Where unsure, leave the shipped value.

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
`TICKTOCK`, `TRAILTO`, `TRANSFER`, `WHERE`, `WIZEMOTE`, `WIZINV`, `ORDER`.

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
power would give it, and `-FLAG` suspends that security flag. You can read
the list with `STAT` on yourself or `LIST`. The suspension only counts while
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

