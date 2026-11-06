# Quests

[Handbook](README.md) · [Quest feature index](feature-index.md#q)

This chapter is the complete guide to quests in CoffeeSUD: the quest script
format and every command in it, where quest files live and how a quest is
started, stepped, scheduled and stopped, the builder and player commands, and
the CoffeeSUD additions (features Q1 to Q16) that give each character their own
quest status, journal, objectives, deadlines, rewards and markers. It is
written for world creators (quest files and the Quest Maker), scripters (the
MOBPROG commands, functions and triggers a quest uses) and engine developers
(the Java classes behind it).

CoffeeSUD is single-player. Wherever the inherited CoffeeMud text speaks of
"players", "online players" or channels, read "the player's characters" (the
active character and the party). The quest engine itself is the inherited
`DefaultQuest`; CoffeeSUD kept it and added per-character state around it.

## In this chapter

- [Concepts](#concepts)
  - [Separate the controller from a character's progress](#separate-the-controller-from-a-characters-progress)
  - [Quest life cycle](#quest-life-cycle)
  - [What does not apply in CoffeeSUD](#what-does-not-apply-in-coffeesud)
- [Where quests live and how they load](#where-quests-live-and-how-they-load)
- [The quest script format](#the-quest-script-format)
  - [File structure and parsing rules](#file-structure-and-parsing-rules)
  - [Steps](#steps)
  - [QUIET and OPTIONAL](#quiet-and-optional)
  - [SET: quest settings](#set-quest-settings)
  - [SET: choosing the quest's objects](#set-choosing-the-quests-objects)
  - [IMPORT and LOAD](#import-and-load)
  - [GIVE and TAKE](#give-and-take)
  - [RESET and embedded scripts](#reset-and-embedded-scripts)
  - [Time expressions](#time-expressions)
  - [Controller structure](#controller-structure)
- [How a character takes a quest: the accept protocol](#how-a-character-takes-a-quest-the-accept-protocol)
- [Starting, stopping, stepping and scheduling](#starting-stopping-stepping-and-scheduling)
- [Quest scripting from MOBPROG](#quest-scripting-from-mobprog)
- [Builder commands](#builder-commands)
- [Player commands](#player-commands)
- [CoffeeSUD quest features Q1 to Q16](#coffeesud-quest-features-q1-to-q16)
- [Quest properties, items and behaviors](#quest-properties-items-and-behaviors)
- [Story templates and the Quest Maker](#story-templates-and-the-quest-maker)
- [Quest boards and generated quests](#quest-boards-and-generated-quests)
- [JavaScript quest handlers](#javascript-quest-handlers)
- [Quest state in saves](#quest-state-in-saves)
- [Worked examples](#worked-examples)
- [Quest verification checklist](#quest-verification-checklist)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## Concepts

A quest is a small program, the *quest script*, that the quest engine runs. The
script picks objects in the world (areas, rooms, mobs, items), loads new ones,
gives them behaviors, effects and MOBPROG scripts, and then waits. The
MOBPROG scripts it hands out do the actual story: they talk to the player, give
and take items, count kills and decide when the player has won. Quest script
commands are *not* MOBPROG commands; they are a separate, line-oriented
language described in [The quest script format](#the-quest-script-format).

Vocabulary used throughout:

| Term | Meaning |
| --- | --- |
| Quest (controller) | The loaded `DefaultQuest` object: one per quest name. It has a state (idle, waiting, running, finished), a current step, winners, and variables. |
| Quest script | The text that defines the quest (settings, selections, GIVEs, STEPs). |
| Step | The part of the script between two `STEP` lines. The quest runs one step at a time. |
| Quest mob / item / room | An object the script designated with `SET` or `LOAD`. They are numbered in the order designated (1, 2, ...). `ISQUESTMOBALIVE(1 quest)` and `QUESTS ... QUESTOBJ:1` use these numbers. |
| Giver | The quest mob that carries the *accept script* (the one with `FUNCTION_PROG CAN_ACCEPT` and `DO_ACCEPT`). Usually quest mob 1. |
| Tracking script | A MOBPROG script placed on a *character* when they accept the quest. Its presence is what "on the quest" means (`QUESTSCRIPTED`). |
| Winner | A character name recorded on the quest as having won it. |
| Copy | A temporary clone of a quest made by `SET SPAWNABLE` or by the quest generators. Copies delete themselves when they stop. |

### Separate the controller from a character's progress

A loaded quest controls world setup, selected objects, timing and steps.
Individual characters have acceptance, objective, outcome, win and cooldown
state. Stopping a global controller and abandoning one character's quest are
different operations. Decide whether the quest is personal, party-shared or
profile-shared (`SET SHARED`) before writing reward code.

| State | Lives on | Saved in |
| --- | --- | --- |
| Quest running/waiting/finished, step, time left, quest variables, winners with the time of their last win | The quest controller | Campaign save (`QUESTS`) |
| Where the quest's mobs and items are, and whether they are gone | The quest controller | Campaign save (`QMOB`, `QITEM`) |
| "On the quest" (tracking script) | The character | The character, if the script was given `SAVABLE` |
| Status (ACTIVE/WON/FAILED/ABANDONED/EXPIRED), outcome word, win count, time of last win | `QuestRecords` | Campaign save (`QUESTRECORDS`) |
| Objectives, journal entries, personal deadlines | Hidden effect `Prop_QuestLog` on the character | The character |
| Bound quest items | Items the character carries, with `Prop_QuestItem` | The character's inventory |
| Script variables (`MPSETVAR`) | The variable scope the script was given (`*` = per character) | Campaign save |

### Quest life cycle

```text
 loaded (idle) --- SET WAIT/INTERVAL, DATE, AUTOSTART, HOLIDAY, SCHEDULE ---> waiting
     |                                                                          |
     | MODIFY QUEST x START / MPSTARTQUEST                                      | wait ends
     v                                                                          v
  running (step 1) --- MPSTEPQUEST / STEP AUTO + duration ---> running (step n)
     |
     | duration ends (EXPIRED) / MPENDQUEST quest / MODIFY QUEST x STOP / ENDONWIN win / last step passed
     v
  stopped: cleanup of everything the quest loaded or gave
     |-- scheduled quest ----------> waiting again
     |-- RESTART NEVER or a time ---> finished (kept, with its winners)
     |-- no schedule --------------> finished (kept, with its winners)   [Q1]
     `-- copy ---------------------> deleted
```

- A quest added to the list (loaded from `world/quests`, created, restored)
  does nothing until something starts it. It shows as `loaded` in `LIST QUESTS`.
- A scheduled quest shows as `waiting (...)` and starts by itself.
- When a quest stops, everything it designated is put back: mobs and items it
  loaded are removed, scripts, behaviors, effects and stats it gave are taken
  away, borrowed world mobs are restored. See [Steps](#steps) for how a step
  change does the same for the step before.
- A stopped quest with no schedule is *kept*, FINISHED, with its winners. This is
  CoffeeSUD Q1; inherited CoffeeMud deleted such a quest from the list.
  Only `DESTROY QUEST` removes one now.

### What does not apply in CoffeeSUD

- **MUDGrinder and the web "Quest Manager"** are gone. The inherited
  GameBuildersGuide text that mentions them is out of date. Use CREATE QUEST,
  MODIFY QUEST and the files in `world/quests`.
- **Channels.** Only INFO and QUESTCHAT remain, and the shipped
  `normal_*` and `competitive_*` templates announce on `$QUEST_ANNOUNCECHANNEL`.
  For a single-player game keep that blank or use `SAY`.
  The story templates (`story_*`) do not use channels.
- **Competitive quests** (`competitive_*` templates race several players). They
  load by name but are hidden from the Quest Maker list in a single-player
  game ([story templates](#story-templates-and-the-quest-maker)).
- **`SET MINPLAYERS` and `SET PLAYERMASK`** still work. They count the local
  online sessions that have a character (and pass the mask). A MINPLAYERS of 1
  is met whenever the game is running with its player; how background party
  members count was not verified, so leave MINPLAYERS out unless you need it.
- **Per-player accounts.** "Profile" replaces account in `SET SHARED PROFILE`.
  See [Saves and party](31-saves-and-party.md).

## Where quests live and how they load

| Place | What it is |
| --- | --- |
| `world/quests/NAME.quest` | One quest per file. The file holds the quest's script exactly as the quest list holds it: either the whole script, or a single `LOAD=path` line that points at the real script. The file name comes from the quest NAME. This is the world's content. |
| `resources/quests/` | The shipped quest library: `templates/` (Quest Maker templates, `*.quest`), `random/` (the generator documents for random quests, `*.xml`), `holidays/holidays.quest` (the stock holidays and weather events). `LOAD=` paths in a quest resolve here. |
| `world/quests/holidays.quest` | The shipped example: its whole content is `LOAD=quests/holidays/holidays.quest`. |
| Campaign save (`QUESTS`, `QUESTRECORDS`) | Running state; see [Quest state in saves](#quest-state-in-saves). |

How a quest gets in the list:

1. **At boot** the game imports every `world/quests/*.quest` into the quest
   table (`WorldFiles.importQuests`), reads each script, and refuses a file
   whose script names no quest. A normal game then loads the table after the
   map. Each quest is added to the quest list; a scheduled one starts waiting.
2. **CREATE QUEST** adds one in play (see [Builder commands](#builder-commands)).
3. **The Quest Maker** (`CREATE QUEST` with no script) writes a script to
   `resources/quests/NAME.quest` and adds a quest whose script is
   `LOAD=quests/NAME.quest`.
4. **Generators** (random quests, quest boards) add copies in play.

In builder mode SAVE writes the quest list back to `world/quests`, one file per
quest, rewriting only files that changed (see [World workflow](02-world-workflow.md)
for SAVE, backups and the `.builder` side files). A file you edit outside the
game while the game is running is not read until you run `WORLD RELOAD QUEST
NAME` (one quest, stopped, read from its file and started again at the step it
was at) or `WORLD RELOAD QUESTS` (each quest that has a file), or restart.
`MODIFY QUEST NAME RELOAD` does the same for the quest's script and the files
it loads. `DEVWATCH ON` does it for you when files change.

Details that matter:

- A quest *file* is only the quest's text. If it is a bare `LOAD=` line, the
  script lives in `resources/` and is read each time the quest starts. The
  `LOAD=` path of the *quest itself* is resolved under `resources/` only; the
  `world:` prefix (a file of the world folder) is accepted in
  `GIVE SCRIPT LOAD=world:scripts/x.script` and other MOBPROG loads but was not
  found in the quest engine's own file lookup, so keep quest scripts either
  inline in the `.quest` file or under `resources/`.
- `DISABLE=QUESTS` in `coffeemud.ini` turns the quest ticker off entirely: no
  quest starts, waits or expires while it is set.
- A rename of a quest NAME is a *different* quest. Characters carrying the
  old name lose it silently: when `QUESTS` is listed, a tracking script whose
  quest no longer exists is stepped, ended and removed from the character.
  Keep quest NAMEs stable; change `SET DISPLAY` for the title players see.

## The quest script format

### File structure and parsing rules

A quest script is plain text. The engine splits it into commands like this:

- One command per line. A semicolon (`;`) ends a command too, which is how a
  whole script is typed on the one line after `CREATE QUEST`. To put a literal
  semicolon in a command (a MOBPROG line given by `GIVE SCRIPT`, a MudChat
  reply), write `\;`. In a file use separate lines instead.
- Blank lines are ignored. A line whose first character is `#` is a comment
  (Quest Maker templates use `#` lines for their prompts, see
  [templates](#story-templates-and-the-quest-maker)).
- Case does not matter for command words. Arguments with spaces go in double
  quotes (`set mob reselect "Old Ned"`).
- If the *first* command is `LOAD=path [ARG ...]`, the script is that file.
  The arguments become `ARG1`, `ARG2`, ... (see [object specifiers](#object-specifiers)).
- `<OPTION>` ... `</OPTION>` blocks: of all the option blocks in a script,
  exactly one, chosen at random, runs. Use them for random variants.
- The line `<?xml version="1.0"?>` ends the script. After it come embedded
  files, each
  `<FILE><NAME>name</NAME><DATA>...</DATA></FILE>`. A `LOAD=name` (in the
  quest, in a `GIVE SCRIPT`, in `IMPORT`) that matches a FILE name is served from
  the quest itself, before the resources are searched. This is how the
  shipped templates carry their MOBPROG scripts inside the one quest file.
- `<SCRIPT>` ... `</SCRIPT>` on their own lines hold embedded JavaScript run
  by the quest (needs an approved script: `MODIFY JSCRIPT`). `<JSQUEST>` ...
  `</JSQUEST>` holds JavaScript quest handlers ([below](#javascript-quest-handlers)).
- The settings before the first `STEP` are read when the script is set; the
  settings in a later step are read when that step starts.

A complete minimal quest file, adapted from `tools/fixtures/fixes/setup/dt2.quest`:

```text
set name dt2quest
set duration 1000
set room any
import mobs _fixes/s1mobs.cmare
load mob any
give script LOAD=_fixes/dt2herald.script
step break
```

It names the quest, gives it a 1000-tick life, picks any room, loads a mob
there from an imported file, and gives the mob a script. `step break` ends the
script. Without a `STEP BREAK` (or a plain `STEP`) at the end the engine
reports "Nothing parsed ... quest not started".

### Steps

`STEP` divides the script. The quest runs one step at a time.

| Line | Effect |
| --- | --- |
| `STEP` | Ends the step. The lines after it are the next step, run when the quest steps on (`MPSTEPQUEST`, `MODIFY QUEST x STEP n`, or `STEP AUTO`). |
| `STEP NAME` | Same, and *names* the step that follows (Q6). The name is for `MPSTEPQUEST quest =NAME`, `MODIFY QUEST x STEP NAME`, `QVAR(quest STEPNAME)` and the save. |
| `STEP AUTO` | The step that follows runs by itself when the current step's duration runs out (instead of the quest ending as EXPIRED). The duration can still be cut short by `MPSTEPQUEST`. |
| `STEP AUTO NAME` | AUTO, and names the step that follows. |
| `STEP BREAK` | Ends the script: nothing after it is read. The quest stays in its last step until its duration ends or it is stopped. Stepping past a `BREAK` step ends the quest. |
| `STEP BACK` | Ends the step, and the next step the quest takes is *this same step again*. For endless "set up, then apply per player" loops. |
| `STEP GOTO NAME` | Not a step end. Anywhere in a step: when this step steps on, the step named `NAME` runs instead of the next one (Q6). Lets a branch rejoin the story. An unknown name is a script error. |

Step rules:

- **Each step must set its own `SET DURATION`.** Stepping resets the duration
  to "none", so a step that sets none stops the quest ("No duration" in the
  log, "a step that sets no duration stops the quest" in `QUESTINFO`).
  `SET DURATION 0` is allowed: that step lasts until it is stepped or the quest
  is ended. (The inherited GameBuildersGuide says earlier durations are re-used;
  the code does not.)
- Step 1 is everything before the first `STEP`. Step numbers count from 1.
  A step's *name* is given on the `STEP` line *before* it, so
  `step confront` makes the following lines step 2, called "confront".
- At the end of a step the engine cleans up what the step designated: borrowed
  mobs and items are restored, loaded ones removed, scripts, behaviors and
  effects taken away, and the scripts given are told the step ended (they
  hear `QUEST_TIME_PROG * -1`). `SET PRESERVE n` before a `GIVE` or `LOAD`
  keeps that object for `n` further steps.
- Settings that live in a step (`SET OBJECTIVE`, `SET JOURNAL`, `SET LORE`,
  `SET DURATION`) are per step. Settings of the quest as a whole (`SET NAME`,
  `SET WAIT`, `SET REPEATABLE`, ...) are read from the first step's lines when
  the script is read and are kept for the quest's life.

### QUIET and OPTIONAL

`QUIET` and `OPTIONAL` are *prefix* words on a command, or switches on their
own line. They are not `SET` variables. (The audit note Q9 describes
`SET QUIET`; the code has the forms below.)

| Form | Effect |
| --- | --- |
| `QUIET` alone | From here on, errors are not logged. Errors still stop the script. |
| `QUIET ON`, `QUIET OFF` | Switch the quiet flag. |
| `QUIET command ...` | Quiet for this one command. |
| `OPTIONAL` alone / `OPTIONAL ON` / `OPTIONAL OFF` | From here on, a command that fails (nothing to find, no such name) is skipped instead of aborting the script. |
| `OPTIONAL command ...` | Optional for this one command. |

The stock holidays use `quiet` after the settings so that a weather quest that
finds no suitable room just does not start, quietly.

### SET: quest settings

`SET` takes a variable and its value. These are the quest's own settings.
`QUESTCHECK` and `QUESTINFO` report problems by these names.

| Setting | Value and meaning |
| --- | --- |
| `NAME` | **Required.** The unique quest name (the key everything else uses). Quest Maker names allow letters, digits and `_` only; a hand-written name may contain spaces, but then it must be quoted in MOBPROG arguments. Avoid them. |
| `DISPLAY` | Friendly name shown to players (`QUESTS`, offers, boards). Default: the NAME. |
| `INSTRUCTIONS` | Text players see in `QUESTS NAME`. A quest board only lists quests that have INSTRUCTIONS. |
| `CATEGORY` | Free word. `QUESTS` groups the character's quests by it, `main` first (Q3). |
| `QUESTTYPE` | Free word; boards can filter by it (`TYPFILTER`). Templates set it to their own name. |
| `AUTHOR` | Who made it. The Quest Maker fills in the creator. |
| `DURATION` | **Required.** How long the quest (or this step) runs: a number of ticks (4 seconds by default), an arithmetic expression (`900+(1?400)`, where `1?400` rolls 1 to 400), or a time (`3 mudhours`; note `DAYS` and `HOURS` here mean *game* days and hours). `0` is no limit. `UNTIL DUSK`, `UNTIL HOUR h` and `UNTIL END` (for `SET SCHEDULE` quests) end the quest at dusk, at that hour, or as the schedule window closes. See [Time expressions](#time-expressions). |
| `WAIT` | Minimum time between automatic starts. **A WAIT without `INTERVAL` never schedules the quest** (see Gotchas). |
| `INTERVAL` | Random extra wait, 1 to the value, after WAIT. The full wait is WAIT plus a random 1..INTERVAL. Use `set interval 1` to mean "exactly WAIT". |
| `DATE m-d` | Start date, month then day, on the game's calendar (inherited help calls it day-month and real time; the code reads the part before the `-` as the month and uses the game calendar). Accepted instead of WAIT as the schedule. A `/` also works as the separator. |
| `MUDDAY m-d` | The same, with the game calendar made explicit (`SET MUDDAY 1-1` is the first day of the first month). The stock holidays use it. |
| `WEEKDAY`, `EVERY`, `SEASONSTART`, `MOON` | Game-dated starts (W6): `SET WEEKDAY monday`, `SET EVERY 3 DAYS FROM 1/1/1`, `SET SEASONSTART WINTER`, `SET MOON FULL`. Details in [World simulation](10-world-simulation.md). |
| `HOLIDAY NAME` | The quest runs exactly while that holiday is on (W6). A quest with `SET HOLIDAY` and no `DURATION` of its own lasts as long as the holiday. |
| `SCHEDULE "rules"` | Runs in a game-time window: `HOUR a-b`, `WEEKDAY`, `EVERY n DAYS`, `SEASON`, `MONTH`, `MOON`, `HOLIDAY`, `WEATHER` (W23). Pair with `SET DURATION UNTIL ...`. |
| `MINPLAYERS n` | Players that must be online for a *timed* start. |
| `PLAYERMASK mask` | A zapper mask that narrows who counts for MINPLAYERS. It is also checked on a character who tries to accept: a character who fails the mask cannot take the quest (`canAcceptQuest`). See [masks](24-behaviors-and-masks.md). |
| `RUNLEVEL n` | If 0 or more, a timed start is skipped while another running quest with a run level of 0 or more and at or below this one is active. Default -1 (no limit). |
| `SPAWNABLE` | `TRUE`/`FIRST`, `ONCE`, `ALL`, `ALLONCE`, `ANY`, `ANYONCE` (values of `Quest.Spawn`; `NO` is the default). When the quest starts it spawns *copies* instead of running itself: `TRUE`/`FIRST` spawns the first step as a copy, `ALL` spawns every step as its own copy, `ANY` a random step. The `...ONCE` forms allow only one copy of a name at a time. Copies share the winners list. |
| `PERSISTANCE true/false` | (The spelling is the engine's.) If the quest hits an error *while starting*, retry: first after 10 ticks, then the pause doubles each time. Default false (give up, then wait as usual). The GameBuildersGuide describes this differently; this is what the code does. |
| `EXPIRATION time` | Makes the quest a temporary copy that lives this long and is then removed from the list for good (RandomQuests sets it). `0` (default) is permanent. |
| `PRESERVE n` | The next `LOAD` / `GIVE` / `SET` objects survive `n` steps instead of being cleaned up at the next step. |
| `OBJECTIVE id count text` | In a step: an objective for the quest journal of each character on the quest as the step starts and of whoever accepts it during the step (Q3). |
| `JOURNAL text` | In a step: a journal entry for the same characters (Q3). |
| `LORE key` | In a step: unlocks a lore entry for the same characters (a `LORE_KEY` tattoo; see [Presentation and language](11-presentation-language.md)). |
| `JSFILE path` | A JavaScript file of quest handlers (JS18), `world:js/quests/x.js` form. See [JavaScript quest handlers](#javascript-quest-handlers). |
| `RESTART ALWAYS/NEVER/time` | Q1 (below). |
| `ENDONWIN true` | Q1. A win (`MPQUESTWIN`) stops the quest. |
| `AUTOSTART BOOT` | Q1. Starts once, a tick after the game begins, then finishes. |
| `REPEATABLE NO/YES/time` | Q5. |
| `MAXWINS n` | Q5. |
| `REWARDS "awards"` | Q10. |
| `DROPPABLE FALSE` | Q15. |
| `SHARED NONE/PARTY/PROFILE` | Q12. |

Settings that only name a type of object (`CLASS` is accepted and ignored) are
not needed. `SET <name>` with a name the engine does not know stops the script
with "unknown variable" (unless `OPTIONAL`).

### SET: choosing the quest's objects

Selection commands pick *existing* world objects and make them the quest's, or
clear the pick. The engine keeps a current area, room, mob, item, and groups.
Later `LOAD` and `GIVE` commands act on the current choice.

| Command | Picks |
| --- | --- |
| `SET AREA [name ...]` | The current area. `ANY` picks a random area; `ANY name1 name2` picks one of those. No name clears it. |
| `SET AREAGROUP [name ...]` | A group of areas (`ALL` for every area). Becomes the room group. |
| `SET ROOM [name]` | The current room: `ANY`, a room ID (`Brenn#12`), or words from a room title or description, searched in the current area, room group or the world. No name clears the room (not the area). Add `MASK=zapper mask` to filter rooms (`set room any mask=-SEASON +SUMMER`). |
| `SET ROOMGROUP [name]` | A set of rooms. `ALL` for all rooms; `ANY a b c` for several. |
| `SET ROOMGROUPAROUND radius locale` | Rooms within `radius` of the current room that match the locale. |
| `SET LOCALE`, `SET LOCALEGROUP`, `SET LOCALEGROUPAROUND` | Same, by locale class or type name (`StdRoom`, `woods`, `underwater` ...). |
| `SET MOB [RESELECT] [name]` | The current mob, an *existing* mob matching the name (or `MASK=...`), brought into the designated room if there is one. A mob already used by the quest is not picked again unless `RESELECT` is used by both picks. Mobs with the `QuestBound` property set to `*` are never picked. |
| `SET MOBGROUP [RESELECT] [name]` | A group of existing mobs (`ALL` for all). Scripts see them via `QUESTMOB`. |
| `SET MOBTYPE [RESELECT] [class]` | An existing mob by Java class name. |
| `SET PCMOB`, `SET PCMOBGROUP` | Like `SET MOB` with an object specifier, but allowed to select player characters. |
| `SET ITEM`, `SET ITEMGROUP`, `SET ITEMTYPE` | Existing items, the same way (an item must be on the map). |
| `SET ABILITY`, `SET ABILITYGROUP` | Abilities previously imported (see `IMPORT ABILITIES`). |
| `SET <KIND> [OBJECT]` | Any of `AREA`, `ROOM`, `MOB`, `ITEM`, ... followed by an object specifier copies or re-designates that object (see below). |
| `SET PRESERVE n` | See the settings table. |

"Mystery" objects exist for logic puzzles (`SET AGENT`, `AGENTGROUP`, `ACTION`,
`ACTIONGROUP`, `FACTION`, `FACTIONGROUP`, `MOTIVE`, `MOTIVEGROUP`, `TARGET`,
`TARGETGROUP`, `TOOL`, `TOOLGROUP`, `WHEREHAPPENED`, `WHEREHAPPENEDGROUP`,
`WHEREAT`, `WHEREATGROUP`, `WHENHAPPENED`, `WHENHAPPENEDGROUP`, `WHENAT`,
`WHENATGROUP`). They are used by the `*_mystery*` generator templates and take
a name, a count, an object specifier, or (for the WHEN forms) hours of
difference. They are inherited unchanged; the GameBuildersGuide describes
each. Single-player story quests rarely need them.

#### Object specifiers

An `[OBJECT]` is one of the quest's current values: `LOADEDMOBS`,
`LOADEDITEMS`, `LOADEDABILITIES`, `AREA`, `ROOM`, `MOBGROUP`, `ITEMGROUP`,
`ROOMGROUP`, `ABILITYGROUP`, `ITEM`, `ENVOBJ`, `STUFF`, `MOB`, `ABILITY`,
`STEPNUM`, or a mystery object. Specifiers combine with `+` and `-`:
`MOBGROUP-MOB` is the group without the current mob; `MOB+ITEM` a group of both.
`ARG1`, `ARG2`, ... are the arguments of the `LOAD=` that brought the script
in. Where an object specifier gives a group, a random member is chosen.

In `GIVE BEHAVIOR`, `GIVE ABILITY`, `GIVE AFFECT` and `GIVE SCRIPT` parameters
(and the lines of an embedded script), `$NAME` is replaced by the name of that
quest object: `give script GREET_PROG 100\; say Welcome to $AREA!\;~\;`.
`$_NAME` makes it capitalized, `$&NAME` drops an article ("the"), `$|NAME`
replaces spaces with `|` (for MudChat/QuestChat patterns).

### IMPORT and LOAD

`IMPORT` reads a file of ready-made objects into the quest; `LOAD` creates
them in the world.

| Command | Effect |
| --- | --- |
| `IMPORT MOBS file` | Reads mobs from a `.cmare` or XML file written by `EXPORT ROOM MOBS` (or the `<ITEMS>`/`<MOBS>` data of an embedded FILE). |
| `IMPORT ITEMS file` | Same for items (`EXPORT ROOM ITEMS`). |
| `IMPORT ABILITIES file` | Same for abilities. |
| `LOAD MOB name` | Creates the named imported mob in the current room (or a room of the current group, area, or any room if none chosen) and makes it the current mob. `MASK=...` after the name filters. |
| `LOAD MOBGROUP [n] name` | Creates all (or `n`) matching mobs; they become the mob group. |
| `LOAD ITEM name`, `LOAD ITEMGROUP [n] name` | Same for items. |
| `LOAD ABILITY name`, `LOAD ABILITYGROUP [n] name` | Same for abilities. |
| `LOAD MOB/MOBGROUP/ITEM/ITEMGROUP [n] CATALOG [FORCE] name` | From the world catalog instead of an import: copies of the catalog entry. They are counted against the entry's cap; none are made past it unless `FORCE`. Words after `CATALOG` are the catalog name (`load mob catalog a fixq2 giver`). |
| `LOAD ENCOUNTER table` | CoffeeSUD (SC3-era): rolls an encounter table in the quest's room (or a room of its area); the mobs it brings belong to the quest and go when it ends. See [Combat and encounters](29-combat-and-encounters.md). |
| `LOAD=path [ARG ...]` | As a command inside a script: includes another quest file as if its lines were here. Arguments are accessible as ARG1... If an argument is a group (`MOBGROUP`), the include runs once per member. |

Imported/embedded file paths: a name that matches an embedded FILE of the quest
is read from the quest; otherwise it is a path under `resources/`.
An import that cannot be read, a `LOAD` with nothing matching, and a `LOAD`
before any `IMPORT` all abort the quest ("no mobs imported", "no item found to
load"). Loaded mobs and items belong to the quest and are removed when the quest
steps or stops.

### GIVE and TAKE

`GIVE` attaches something to the *current* mob, item, room or area (or the
group); `TAKE` removes it. Everything given is removed again when the step or
quest ends.

| Command | Effect |
| --- | --- |
| `GIVE ITEM` | Gives the current item to the current mob (or one of the group). |
| `GIVE ITEMS` | Gives all items of the group/loaded items. |
| `GIVE FOLLOWER name` | Makes a previously designated mob (matching `name`) a follower of the current mob. |
| `GIVE BEHAVIOR id [parms]` | Adds a behavior to the current mob, item, room, area or group. |
| `GIVE ABILITY id [parms]` | Teaches an ability to the current mob or mob group. |
| `GIVE AFFECT id [parms]` | Adds an effect or property (`Prop_QuestGiver`, `Prop_QuestMarker`, `Prop_QuestItem`, ...) to the current mob, item, room, area or group. |
| `GIVE STAT name value` | Sets a stat on the current object for the quest's life: any stat the object has (`PRICEMASKS`, level, ...), `SUPPRESSLAW` on an area, and **`KEYPLAYER`**: `TRUE` means its death resets the quest; `FAIL` means its death fails the quest for every character on it and the quest goes on (Q4). |
| `GIVE SCRIPT [flags] script` | Adds a MOBPROG script. The script is a `LOAD=file` or inline lines (`\;` between lines). Flags before it: `SAVABLE` (saved with the object), `GLOBAL` (variable scope shared by everything), `INDIVIDUAL` (or `*`, scope per character), `PLAYEROK` (allowed onto a player). Without a scope flag, the variable scope is the quest's name. |
| `TAKE BEHAVIOR id` | Removes a behavior given by the quest from the current object. |
| `TAKE ABILITY id` | Removes an ability. |
| `TAKE AFFECT id` | Removes an effect. |

`GIVE SCRIPT` on a *character* is special: a script that has both
`FUNCTION_PROG CAN_ACCEPT` and `DO_ACCEPT` (and is not given with `PLAYEROK`)
is run through those functions instead of being attached, so a quest can offer
itself to a character from the script.

The scripts a quest gives are ordinary MOBPROG. A script given by a quest is
registered with the quest: `QUESTSCRIPTED`, `QVAR`, `MPQSET`, `$%QVAR()%`
and `*` as a quest name inside it all refer to that quest.
Link the script's pieces to the MOBPROG chapters:
[04-mobprog.md](04-mobprog.md), [triggers](26-mobprog-triggers.md),
[commands](27-mobprog-commands.md), [functions](28-mobprog-functions.md).

### RESET and embedded scripts

`RESET` reloads the current room (or the room group, or the area) from its
saved state, clearing what is on the floor and the mobs in it. It takes a
moment and can disturb players there; use it sparingly. With nothing designated
it is a script error.

`<SCRIPT>` ... `</SCRIPT>` blocks run JavaScript in the quest's scope with a
`JScriptQuest` object (the script can call quest functions). The block needs an
approved script. `<JSQUEST>` blocks are read by the handler system and skipped
by the engine here.

### Time expressions

Wherever a duration or wait is given (`DURATION`, `WAIT`, `INTERVAL`, `EXPIRATION`,
`RESTART`, `REPEATABLE`, `MPQTIMER`, `REOFFER`, `REFRESH`):

- a plain number is **ticks** (one tick is 4 seconds of real time by default);
- a number followed by `seconds`, `minutes`, `hours`, `days` is real time;
- a number followed by `mudhours`, `muddays`, `mudweeks`, `mudmonths`,
  `mudyears` is game time (one MUDDAY is the game's day length,
  `world.ini`'s calendar);
- an arithmetic expression using numbers, `+ - * / ( )` and `a?b` (roll);
- in `SET DURATION` only, `DAYS` and `HOURS` are read as game days and hours.
  Everywhere else `days` and `hours` are real time. Prefer the explicit `mud`
  forms.

Game time follows pause and turn-based time ([Saves and party](31-saves-and-party.md),
[World simulation](10-world-simulation.md)): a quest or deadline counted in
`mud` units holds when the game is paused or in turns mode.

### Controller structure

A controller is the first step of a script: the settings, the selections, the
`GIVE`s, then `STEP` or `STEP BREAK`. A minimal controller file can be:

```text
set name museum_demo
set duration 1000
set room any
step break
```

This is a controller scaffold, not a complete player quest: it has no
acceptance NPC, objective or win handler. Add those deliberately. Use
[QUESTCHECK](#builder-commands) to catch unknown commands, missing files and a
`WAIT` without `INTERVAL` before starting. Keep quest NAMEs stable when
changing titles.

## How a character takes a quest: the accept protocol

The engine does not know what "accepting" means. It calls functions in a
MOBPROG script that the quest gave to one of its objects, the *accept script*.
The engine finds it as the first script among the quest's gifts that defines
both `FUNCTION_PROG CAN_ACCEPT` and `FUNCTION_PROG DO_ACCEPT`. A quest with no
such script cannot be offered by `Prop_QuestGiver`, listed on a quest board,
or taken with `QUESTS ACCEPT` / `ACCEPT n`. (It can still be started and run by
scripts of its own.)

| Function (in the accept script) | Called when | `$n` | Result |
| --- | --- | --- | --- |
| `CAN_ACCEPT` | The engine asks whether the character may take it: offers, boards, `QUESTS OFFERED`, `ACCEPT`. | The character | Non-empty and not `CANCEL` means yes. `RETURN CANCEL` or `RETURN` (empty) means no. |
| `DO_ACCEPT` | The character is accepted. | The character | Gives the character the *tracking script* (`mpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=file`), items, objectives, journal entries. |
| `CAN_DROP` (Q15) | `QUESTS DROP`, on the character's tracking script and then on the accept script. | The character | Non-empty return refuses the drop and is shown to the player. |
| `DO_DROP` (Q15) | The drop is confirmed. | The character | Cleanup, such as taking back the parcel. Runs before the scripts go. |

What the engine itself checks, in order, before it calls `CAN_ACCEPT`
(`DefaultQuest.canAcceptQuest`): the quest is running and not stopping; the
character passes `SET PLAYERMASK`; the character does not already carry a
script for this quest; the character has not hit `SET REPEATABLE` /
`SET MAXWINS` (Q5). Only then is `CAN_ACCEPT` asked. A refusal by the engine
is final even if `CAN_ACCEPT` says yes.

What `acceptQuest` does after `DO_ACCEPT`: records the character as ACTIVE
(`QuestRecords`), applies the current step's `SET OBJECTIVE`, `SET JOURNAL`
and `SET LORE`, fires `QUEST_PROG ACCEPT`, and for `SET SHARED PARTY`
accepts for the party members in the same room.

The usual skeleton, as the shipped templates write it:

```text
FUNCTION_PROG CAN_ACCEPT
	if ISPC($n) and !QUESTSCRIPTED($n *)
		RETURN TRUE
	endif
	RETURN CANCEL
~

FUNCTION_PROG DO_ACCEPT
	mpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=mine_track.script
	mpqobjective $n * ADD fetch 1 Bring the bell to Old Ned
	mpqjournal $n * Old Ned asked me to find the chapel bell.
	mpqset * STATISTICS ACCEPTED
~

SPEECH_PROG p i accept
	if CALLFUNC(CAN_ACCEPT)
		sayto "$n" Thank you.  The bell lies in the marsh.
		MPCALLFUNC DO_ACCEPT
	endif
~
```

In the accept script `*` as a quest name means "this script's quest", and
`QUESTSCRIPTED($n *)` is true when the character carries *any* tracking
script of that quest. The `mpscript ... INDIVIDUAL SAVABLE` line gives
the character a copy of `mine_track.script`. `INDIVIDUAL` makes the
script's `MPSETVAR` variables per character; `SAVABLE` makes the script (and
so the fact that the character is on the quest) part of the character's save.
`STATIC=` parses the script once and shares the parse.

Ways an offer reaches the player:

1. **Speech.** A `SPEECH_PROG` on the giver reacts to "accept" or "quest". Typed
   with `SAY` or the TALK command ([NPCs and dialogue](06-npcs-dialogue.md)).
2. **`Prop_QuestGiver`.** Offers by a trigger (entering a room, taking an item,
   speaking to a mob) with a yes/no prompt ([properties](#quest-properties-items-and-behaviors)).
3. **A quest board** ([boards](#quest-boards-and-generated-quests)).
4. **`QUESTS OFFERED` then `QUESTS ACCEPT n`**, or `ACCEPT n`, for any quest the
   player was offered or has read.

## Starting, stopping, stepping and scheduling

| To ... | Do |
| --- | --- |
| Start a quest now | `MODIFY QUEST NAME START` (builder), or `MPSTARTQUEST NAME` in a script. Starting a running quest restarts it. |
| Stop a quest | `MODIFY QUEST NAME STOP`, `MPENDQUEST NAME`, `DESTROY QUEST NAME` (also removes it). A stop cleans up everything the quest loaded or gave and records an END event. |
| Step it | `MPSTEPQUEST NAME` (next step), `MPSTEPQUEST NAME +N` (skip N steps), `MPSTEPQUEST NAME =N` (go to step N), `MPSTEPQUEST NAME =LABEL` (a named step, Q6). In tests: `MODIFY QUEST NAME STEP n-or-label`. `STEP AUTO` steps when the duration runs out. |
| Pause a quest from starting | `MODIFY QUEST NAME DISABLE`; `ENABLE` undoes it. A disabled quest is not ticked. The flag is kept in the quest table. |
| Run on a schedule | `SET WAIT` plus `SET INTERVAL` (a random wait), or `SET DATE` / `SET MUDDAY`, `SET WEEKDAY`, `SET EVERY`, `SET SEASONSTART`, `SET MOON`, `SET HOLIDAY`, `SET SCHEDULE`. |
| Start once at boot | `SET AUTOSTART BOOT` (Q1). |
| Never start by itself | No schedule settings. It stays `loaded` and is started by something else. |
| Run for ever | `SET DURATION 0`. |

How a timed start is decided each tick: when the wait has run out, the engine
checks `RUNLEVEL` (skip if a quest with a run level at or below this one is
running) and `MINPLAYERS` (with `PLAYERMASK`); if allowed, it starts, otherwise
it goes back to waiting. A quest that raised an error while starting and has
`PERSISTANCE true` retries sooner (10 ticks, then 20, 40, ...).

What ends a running quest:

- its duration runs out (the end reason is EXPIRED), unless the step is
  `STEP AUTO`, in which case the next step starts;
- `MPENDQUEST NAME`, `MODIFY QUEST NAME STOP`, or `DESTROY QUEST`;
- the last step is stepped past;
- `SET ENDONWIN TRUE` and a script calls `MPQUESTWIN` (Q1);
- an object of the quest is destroyed in a way that resets the quest: a
  quest mob or item flagged with `QuestBound` is expired, its room is reset or
  the game shuts down, or `GIVE STAT KEYPLAYER TRUE` mob dies. The quest is
  restarted after a short pause. (`QuestBound` is the engine's own effect on every
  quest object; see its [help](../../CoffeeMud/resources/help/arc_properties.ini).)

Events other scripts can hear are in [Quest scripting from MOBPROG](#quest-scripting-from-mobprog).

## Quest scripting from MOBPROG

Quests give MOBPROG scripts to their objects and to characters. These are the
quest-specific parts of the MOBPROG language; everything else is in
[04-mobprog.md](04-mobprog.md).

### Commands

| Command | Syntax | Notes |
| --- | --- | --- |
| [MPSTARTQUEST](27-mobprog-commands.md#mpstartquest) | `MPSTARTQUEST quest` | Starts the quest. |
| [MPENDQUEST](27-mobprog-commands.md#mpendquest) | `MPENDQUEST quest` or `MPENDQUEST who` | With a quest name: stops that quest. With a character: removes that character's tracking scripts for the script's own quest (the turn-in cleanup after a win). |
| [MPSTEPQUEST](27-mobprog-commands.md#mpstepquest) | `MPSTEPQUEST quest (+N / =N / =LABEL)` | Steps on, skips N, jumps to step N, or to a named step. A jump is only legal to a number greater than 0. |
| [MPQUESTWIN](27-mobprog-commands.md#mpquestwin) | `MPQUESTWIN who quest (OUTCOME word) (REWARD)` | The character wins the quest: recorded as a winner, the achievement `QUESTOR` is bumped, a win message is broadcast, Q4/Q5 record the win and outcome, `REWARD` gives the quest's `SET REWARDS`. A leading `-` on `who` (`MPQUESTWIN -Name quest`) takes a win away. `*` is this script's quest. For `SET SHARED PARTY`, the party members on the quest win too. |
| [MPQUESTFAIL](27-mobprog-commands.md#mpquestfail) | `MPQUESTFAIL who quest (reason)` | Q4. Fails it for the character and ends their tracking scripts. |
| [MPQSET](27-mobprog-commands.md#mpqset) | `MPQSET quest VAR value` | Sets a quest variable. Special forms: `MPQSET * QUESTOBJ $o` registers an item/mob as the quest's so it is cleaned up; `MPQSET * STATISTICS ACCEPTED/SUCCESS/WON/FAILED` bumps the statistics tables. `++` and `--` as the value add or subtract 1. `INSTRUCTIONS` is the usual use: `MPQSET * INSTRUCTIONS text`. |
| [MPLOADQUESTOBJ](27-mobprog-commands.md#mploadquestobj) | `MPLOADQUESTOBJ quest VAR $obj` | Loads a quest object into a script's `$i $n $t $o $a ... $9` slot. |
| [MPSCRIPT](27-mobprog-commands.md#mpscript) | `MPSCRIPT who (flags) script` | Gives a script to a mob or item; flags `GLOBAL`, `INDIVIDUAL`, `SAVABLE`, `EXECUTE`, `STATIC`, `DELETE`. A script given from a quest script is registered with that quest. |
| [MPQOBJECTIVE](27-mobprog-commands.md#mpqobjective) | see [Q3](#q3-quest-journal-objectives-and-history) | Objectives. |
| [MPQJOURNAL](27-mobprog-commands.md#mpqjournal) | `MPQJOURNAL who quest text` | A dated journal entry. |
| [MPQTIMER](27-mobprog-commands.md#mpqtimer) | `MPQTIMER who quest time` or `OFF` | A personal deadline (Q11). |
| [MPREWARD](27-mobprog-commands.md#mpreward) | `MPREWARD who "awards"` | An ad-hoc reward in `SET REWARDS` words (Q10). |
| [MPQUESTPOINTS](27-mobprog-commands.md#mpquestpoints) | `MPQUESTPOINTS who n` / `++n` / `--n` | Sets or adjusts quest points. |
| [MPCHOOSE](27-mobprog-commands.md#mpchoose) | `MPCHOOSE who VARIABLE CHOICES DEFAULT prompt` | A hotkey choice stored in a variable; used by the branching story template. |

`who` is a character name or one of `$n`, `$i`. The quest argument `*` means
the quest the script came from. A script that is not a quest's must name the
quest.

### Functions

| Function | Meaning |
| --- | --- |
| [QUESTSCRIPTED](28-mobprog-functions.md#questscripted)`($n quest)` | The character carries a tracking script of the quest (`*` for any quest). |
| [QUESTWINNER](28-mobprog-functions.md#questwinner)`($n quest)` | The character has won the quest (`*` this quest; `previous` is used by the `normal_*` chain option). |
| [QUESTSTATUS](28-mobprog-functions.md#queststatus)`($n quest == STATUS)` | ACTIVE, WON, FAILED, ABANDONED, EXPIRED, or nothing (Q4). |
| [QUESTOUTCOME](28-mobprog-functions.md#questoutcome)`($n quest == word)` | The outcome word from `MPQUESTWIN ... OUTCOME`, or the failure reason (Q4). |
| [QUESTWINS](28-mobprog-functions.md#questwins)`($n quest >= n)` | How many times the character won (Q5). |
| [QUESTWINAGE](28-mobprog-functions.md#questwinage)`($n quest > hours)` | Game hours since their last win, `-1` if never (Q5). |
| [QOBJECTIVE](28-mobprog-functions.md#qobjective)`($n quest id == value)` | The objective's count, `DONE` or `FAILED` (Q3). |
| [QVAR](28-mobprog-functions.md#qvar)`(quest NAME op value)` | A quest variable or setting: `STEPNAME`, `REMAINING`, `DURATION`, `FINISHED`, `INSTRUCTIONS`, `PROGRESS`, and any `MPQSET` variable. `$%QVAR(quest NAME)%` returns the value. |
| [QUESTMOB](28-mobprog-functions.md#questmob)`(name quest)` | The mob is one the quest designated. |
| [QUESTROOM](28-mobprog-functions.md#questroom)`(roomid quest)` | The room is one the quest designated. |
| [ISQUESTMOBALIVE](28-mobprog-functions.md#isquestmobalive)`(n quest)` | Quest mob number `n` is alive. Tracking scripts use it to end themselves when the giver dies. |

The `+QUESTWIN` family of zapper masks (`-QUESTWIN "+quest"`, `-QUESTFAIL`,
`-QUESTOUTCOME "+quest:word"`, `-QUESTWINS "+quest:>=3"`) work anywhere a mask
does (`SET PLAYERMASK`, behaviors, shops, [masks](24-behaviors-and-masks.md)).
Read them as the standard `-`/`+` pairs: `-QUESTWIN +ned` lets through only
those who have won `ned`; `+QUESTWIN -ned` those who have not.

### Triggers

| Trigger | Fires | Details |
| --- | --- | --- |
| [QUEST_PROG](26-mobprog-triggers.md#quest_prog) | `QUEST_PROG START/ACCEPT/STEP/WIN/FAIL/END/ANY quest/* (detail)` | Heard by every object with the trigger, wherever it is. `$n` the accepter, winner or dropper (or "the quest"), `$g` the quest's name, `$0` the detail: the step number for STEP; for FAIL and END the outcome WON, EXPIRED, STOPPED or DROPPED. FAIL fires when a quest ends with no winner. |
| [QUEST_TIME_PROG](26-mobprog-triggers.md#quest_time_prog) | `QUEST_TIME_PROG quest minutes-left` | For a script a quest gave, when the running quest reaches that many minutes left. `-1` fires at each step, `-2` when it ends. |
| [QUESTFAIL_PROG](26-mobprog-triggers.md#questfail_prog) | `QUESTFAIL_PROG quest/*` | Q4. A quest failed for a character. `$n` the character, `$g` the quest, `$0` why. Heard by everything, and by the character's own scripts. |
| [QUESTTIMEOUT_PROG](26-mobprog-triggers.md#questtimeout_prog) | `QUESTTIMEOUT_PROG (quest)` | Q11. The character's own deadline came. With no such trigger on their scripts, the quest fails for them as EXPIRED. |
| [FUNCTION_PROG](26-mobprog-triggers.md#function_prog) | `FUNCTION_PROG NAME` | Defines `CAN_ACCEPT`, `DO_ACCEPT`, `CAN_DROP`, `DO_DROP`, and your own names (`CAN_COMPLETE` for markers). Called with `MPCALLFUNC NAME` / `CALLFUNC(NAME)`. |

## Builder commands

All quest builder commands need the `CMDQUESTS` security flag, which the
single-player builder has (see [Configuration and administration](19-configuration-and-administration.md)
and [tool permissions](tool-permissions.md)). Full syntax is generated into
[tool-reference.md](tool-reference.md#questinfo).

| Command | Does |
| --- | --- |
| `CREATE QUEST script` | Adds a quest. The script is a whole quest script (commands separated by `;`) or `LOAD=path`. `CREATE QUEST LOAD=quests/robbed/robbed.quest` loads a shipped one. It is refused if the script names no quest or has no `SET DURATION`; an existing name asks before loading a duplicate. After `CREATE QUEST`, run `SAVE QUESTS` (or SAVE) to keep it. |
| `CREATE QUEST` (no script) | Offers the Quest Maker wizard ([below](#story-templates-and-the-quest-maker)). |
| `MODIFY QUEST name-or-number` | Opens the quest's menu: Started, Enabled, and the script text in the editor. Add `START`, `STOP`, `ENABLE` or `DISABLE` as the last word to do it without the menu. A changed script is rejected if it has no name, no duration, or the name clashes with another quest. |
| `MODIFY QUEST name STEP n-or-label` | Test control. Moves a running quest to a step. Reports if it went with errors. |
| `MODIFY QUEST name WIN character` / `UNWIN character` | Makes a character a winner, or not (and takes back the win count). |
| `MODIFY QUEST name RESETWINNERS` | Forgets all winners. |
| `MODIFY QUEST name REOPEN` | Makes a FINISHED quest one that has not run (Q1), so it may start again. |
| `MODIFY QUEST name VAR NAME value` | Sets a quest variable. |
| `MODIFY QUEST name RELOAD` | Reads the quest's script and the scripts it loads again from their files, and starts it again at the step it was at, with its variables. |
| `DESTROY QUEST name-or-number` | Stops the quest and removes it. This is the only thing that removes a finished quest. |
| `LIST QUESTS (words)` | The quest list: number, name, and status: `running (time)`, `copy running`, `disabled`, `finished`, `finished (starts again in n ticks)`, `waiting (time)` or `loaded`. |
| `LIST QUESTNAMES`, `LIST QUESTWINNERS (words)` | Names with their display names; winners with times. |
| `SAVE QUESTS` | Saves the quest list to the quest table (builder SAVE also writes `world/quests`). |
| [QUESTINFO](tool-reference.md#questinfo)` name-or-number` | One screen: state (running, waiting, stopped, finished, disabled), settings, step and its name, time left or until start, variables, the MOB, ITEM, ROOM and AREA it designated and its groups with where each is, what it holds, the scripts, behaviors and effects it added and to what, winners with times, and its last errors. |
| [QUESTCHECK](tool-reference.md#questcheck)` (name/file/ALL)` | Reads a quest's script without starting it and reports each problem with its line. Takes a file in `world/quests`, a loaded quest by name, a file in the resources, or ALL (the default, every file of `world/quests`). Available in builder mode, DEV=1 games, or with `CMDQUESTS`. Builder mode runs it on `world/quests` at the start and prints one line. |
| `QUESTS name`, `QUESTS player` | As an admin, `QUESTS PLAYER` shows the player's won and active quests, `QUESTS NAME` (for a quest the admin is not on) shows its winners. |
| `WORLD RELOAD QUEST name` / `QUESTS` | Reads the quest(s) from `world/quests` again (see above). |
| `VARS LIST QUEST name` | Lists the script variables of a quest's scope. |
| `ASSERT QUEST name STEP n` | Test assertion: the quest is running at that step. See [Testing](13-testing-release.md). |
| `AHELP QUESTS` | The inherited reference for the script commands. |

What QUESTCHECK checks (`core/QuestCheck.java`):

- every line's first word is a quest command (`SET LOAD GIVE TAKE STEP RESET
  IMPORT OPTIONAL QUIET <SCRIPT> </SCRIPT>`);
- `SET` variables, `GIVE`/`TAKE`/`LOAD`/`IMPORT` kinds are known;
- `SET NAME` and `SET DURATION` exist;
- a `SET WAIT` has a `SET INTERVAL`;
- a `STEP GOTO name` has a step of that name;
- `LOAD=` and `IMPORT` files exist, in the quest's own `<FILE>` sections or in
  the resources (a name containing `$` is skipped: it is a template variable);
- each script given, inline or embedded, is run through `SCRIPTCHECK`.

Known gaps (verified in the source): it does not list the W6/W23 settings
(`SET WEEKDAY`, `EVERY`, `SEASONSTART`, `MOON`, `HOLIDAY`, `SCHEDULE`), `SET LORE`,
`SET PRESERVE`'s siblings it was not told, `LOAD ENCOUNTER`, or `<JSQUEST>`
blocks, and reports them as unknown. Treat those reports as noise and test
the quest by playing it. It does not check that a named mob, area or
room exists, or that the quest can find objects to select.

## Player commands

| Command | Does |
| --- | --- |
| `QUESTS` | The quests you have accepted, grouped by category with the `main` group first. Under each: its objectives (`[x]` done, `[ ]` not yet, `[-]` failed, with counts and the place if the quest says) and the time left on the game clock, the quest's or your own deadline, whichever is sooner. |
| `QUESTS name` or `QUESTS n` | One quest: the instructions, `Time Remaining`, `Progress` (the script's `PROGRESS` variable), the quest's reward, objectives, history (journal entries) and quest items you carry for it. |
| `QUESTS LOG` | The latest 20 journal entries of all your quests. |
| `QUESTS WON` | Quests you won, with the date, the outcome word and the number of wins. (`QUESTWINS` is the same command with WON implied.) |
| `QUESTS FAILED` | Quests you failed, dropped or ran out of time for, with the reason. |
| `QUESTS OFFERED` | Quests you were offered (by a giver or a board) that you may still take. |
| `QUESTS ACCEPT n-or-name` | Takes one of those on. |
| `ACCEPT n-or-name` | Takes the job numbered `n` on the quest board you last looked at; with no board it behaves as `QUESTS ACCEPT`. |
| `QUESTS WHERE (n)` | Which way to the first open objective that has a place, from here. |
| `QUESTS DROP name` | Drops an accepted quest (after a y/N). Refused for `SET DROPPABLE FALSE` quests, or if a `CAN_DROP` function says no; the reason is shown. |

Players see the quests of the character they are playing; party members and the
`CONTROL` / `AS` commands act per character ([Saves and party](31-saves-and-party.md)).

