# Quests

[Handbook](README.md) · [Quest feature index](feature-index.md#q-quests)

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
- A rename of a quest NAME is a *different* quest. Characters carrying the old
  name keep its tracking script, but `QUESTS` no longer lists it, and the log
  says once "...has the tracking script of quest '...', which is not loaded; it is
  kept". If the quest comes back (a failed load fixed, the name restored), their
  progress is there again; `QUESTS DROP` discards a leftover script. Only a quest
  deleted in the running game takes its tracking scripts with it. Keep quest
  NAMEs stable; change `SET DISPLAY` for the title players see.

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
script. The final `step break` is optional: a script simply ends after its
last line (the shipped templates end at `set duration 0`), but a script with no
commands at all reports "Nothing parsed ... quest not started".

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
| `MINPLAYERS n` | Players that must be online for a *timed* start. Ignored when the quest's `DURATION` is 0 (such a quest starts on schedule regardless). |
| `PLAYERMASK mask` | A zapper mask that narrows who counts for MINPLAYERS. It is also checked on a character who tries to accept: a character who fails the mask cannot take the quest (`canAcceptQuest`). See [masks](24-behaviors-and-masks.md). |
| `RUNLEVEL n` | If 0 or more, a timed start is skipped while another running quest with a run level of 0 or more and at or below this one is active (a running quest with `DURATION 0` does not count). Default -1 (no limit). |
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
| `SET ROOMGROUPAROUND radius [name]` | Rooms within `radius` rooms of the current room (a room must already be set), filtered by room name as `SET ROOMGROUP` is. |
| `SET LOCALE`, `SET LOCALEGROUP`, `SET LOCALEGROUPAROUND radius locale` | Same, by locale class or type name (`StdRoom`, `woods`, `underwater` ...); the AROUND form takes rooms within `radius` of the current room. |
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
| Start a quest now | `MODIFY QUEST NAME START` (builder), or `MPSTARTQUEST NAME` in a script. `MPSTARTQUEST` on a running quest stops it and starts it again; `MODIFY QUEST ... START` refuses ("already running"). |
| Stop a quest | `MODIFY QUEST NAME STOP`, `MPENDQUEST NAME`, `DESTROY QUEST NAME` (also removes it). A stop cleans up everything the quest loaded or gave and records an END event. |
| Step it | `MPSTEPQUEST NAME` (next step), `MPSTEPQUEST NAME +N` (skip N steps), `MPSTEPQUEST NAME =N` (go to step N), `MPSTEPQUEST NAME =LABEL` (a named step, Q6). In tests: `MODIFY QUEST NAME STEP n-or-label`. `STEP AUTO` steps when the duration runs out. |
| Pause a quest from starting | `MODIFY QUEST NAME DISABLE`; `ENABLE` undoes it. A disabled quest is not ticked. The flag is kept in the quest table. |
| Run on a schedule | `SET WAIT` plus `SET INTERVAL` (a random wait), or `SET DATE` / `SET MUDDAY`, `SET WEEKDAY`, `SET EVERY`, `SET SEASONSTART`, `SET MOON`, `SET HOLIDAY`, `SET SCHEDULE`. |
| Start once at boot | `SET AUTOSTART BOOT` (Q1). |
| Never start by itself | No schedule settings. It stays `loaded` and is started by something else. |
| Run for ever | `SET DURATION 0`. |

How a timed start is decided each tick: when the wait has run out, the engine
checks `RUNLEVEL` (skip if a quest with a run level at or below this one is
running and has a duration) and `MINPLAYERS` (with `PLAYERMASK`; not checked when
the quest's `DURATION` is 0); if allowed, it starts, otherwise
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
  the game shuts down, or a `GIVE STAT KEYPLAYER TRUE` mob dies. The quest is
  reset: stopped and started again later. (`QuestBound` is the engine's own
  effect on every quest object; see `AHELP QUESTBOUND`. The pause before the
  restart was not verified.)

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
| [QUESTSCRIPTED](28-mobprog-functions.md#questscripted)`($n quest)` | The character carries a tracking script of the quest (`*` is this script's quest). `QUESTSCRIPTED($n)` alone, as a value, lists the quests they are scripted for. |
| [QUESTWINNER](28-mobprog-functions.md#questwinner)`($n quest)` | The character has won the quest (`*` is this script's quest). `QUESTWINNER($n)` alone, as a value, lists the quests they have won. |
| [QUESTSTATUS](28-mobprog-functions.md#queststatus)`($n quest == STATUS)` | ACTIVE, WON, FAILED, ABANDONED, EXPIRED, or nothing (Q4). |
| [QUESTOUTCOME](28-mobprog-functions.md#questoutcome)`($n quest == word)` | The outcome word from `MPQUESTWIN ... OUTCOME`, or the failure reason (Q4). |
| [QUESTWINS](28-mobprog-functions.md#questwins)`($n quest >= n)` | How many times the character won (Q5). |
| [QUESTWINAGE](28-mobprog-functions.md#questwinage)`($n quest > hours)` | Game hours since their last win, `-1` if never (Q5). |
| [QOBJECTIVE](28-mobprog-functions.md#qobjective)`($n quest id == value)` | The objective's count, `DONE` or `FAILED` (Q3). |
| [QVAR](28-mobprog-functions.md#qvar)`(quest NAME op value)` | A quest variable or setting: `STEPNAME`, `REMAINING`, `DURATION`, `FINISHED`, `INSTRUCTIONS`, and any `MPQSET` variable. `$%QVAR(quest NAME)%` returns the value. |
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
| `CREATE QUEST script` | Adds a quest. The script is a whole quest script (commands separated by `;`) or `LOAD=path`. `CREATE QUEST LOAD=quests/mine/bell.quest` loads a script saved under `resources/quests/` (the shipped `resources/quests/` folders hold only templates, generator documents and the holidays). It is refused if the script names no quest or has no `SET DURATION`; an existing name asks before loading a duplicate. After `CREATE QUEST`, run `SAVE QUESTS` (or SAVE) to keep it. |
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

Known gaps (verified in the source): its list of known words lacks the
W6/W23 settings (`SET WEEKDAY`, `EVERY`, `SEASONSTART`, `MOON`, `HOLIDAY`,
`SCHEDULE`), `SET LORE`, `LOAD ENCOUNTER` and `<JSQUEST>` blocks, so it reports
them as unknown. Treat those reports as noise and test
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

## CoffeeSUD quest features Q1 to Q16

These are the additions CoffeeSUD made around the inherited quest engine. The
feature ids are those of [the feature index](feature-index.md#q-quests). Each is covered
by a fixture in `tools/fixtures/fixes/` named after it (`q1-finished.js` ...
`q16-where.js`), which is a good place to see exact syntax. Source:
[DefaultQuest](../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultQuest.java),
[Questwins](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Questwins.java) and the
`Quest*` classes in [core](../../CoffeeMud/com/planet_ink/coffee_mud/core/).

### Q1: Finished quests stay known

A quest that has run and then stops, with no wait/interval/date schedule (a
story quest started by `MPSTARTQUEST`, say), is no longer deleted. It stays in
the list as FINISHED, keeps its winners and variables, and is saved that way.
`QUESTWINNER`, `-QUESTWIN` and `QUESTS WON` keep knowing it. Only `DESTROY
QUEST` removes it.

| Setting | Meaning |
| --- | --- |
| `SET RESTART ALWAYS` | The default (blank is the same). A finished quest may be started again by hand or script. |
| `SET RESTART NEVER` | Once it has run it stays finished. `MODIFY QUEST x START` and `MPSTARTQUEST` do nothing until `MODIFY QUEST x REOPEN`. |
| `SET RESTART 1 MUDDAY` | A game time. After it finishes it waits that long, then starts again by itself; a manual start before then is refused. `LIST QUESTS` says `finished (starts again in n ticks)`. |
| `SET ENDONWIN TRUE` | `MPQUESTWIN` stops the quest, which cleans up the mobs and items it loaded. Without it a win leaves the quest running for other characters. |
| `SET AUTOSTART BOOT` | Starts the quest once, a tick after the game begins, then it is finished. (Short for a one-tick `WAIT`.) |

`MODIFY QUEST x REOPEN` makes a finished quest as if it never ran, for testing
or for a scripted reset. A scheduled quest that has finished its run is
`waiting`, not `finished`. In JavaScript, `quests.status(name)` says `finished`.

### Q2: Quest-giver and turn-in markers

`Prop_QuestMarker` shows a mark after a mob or item in the room listing, by how
the quest stands for the *character looking* (each character sees their own):

| Mark | Shown | Default |
| --- | --- | --- |
| `AVAILABLE` | While the character may accept the quest (`CAN_ACCEPT` says yes, running, repeat rules pass) | `^Y(!)^?` (bright yellow) |
| `ACTIVE` | Once they have accepted it | `^w(?)^?` (grey) |
| `READY` | When `READYFUNC` returns something (not `CANCEL`) | `^Y(?)^?` |

Parameters: `QUEST=name` or `*` (default: every running quest the object has a
script from, is the first quest mob or item of, or gives by `Prop_QuestGiver`),
`AVAILABLE=""`, `ACTIVE=""`, `READY=""` (an empty string suppresses that mark),
and `READYFUNC=NAME`, a `FUNCTION_PROG` of this mob's or item's own script,
run with the viewer as `$n`. Example on the turn-in NPC:

```text
give affect Prop_QuestMarker QUEST=bell READYFUNC=CAN_COMPLETE AVAILABLE=
```

with, in its script:

```text
FUNCTION_PROG CAN_COMPLETE
	if HAS($n 'the silver bell')
		RETURN TRUE
	endif
	RETURN
~
```

Only the player's characters see marks (a monster viewer sees none). The world
option `QUESTMARKERS=AUTO` (in `coffeemud.ini` or `world.ini`) marks every
running quest's first quest mob and every `Prop_QuestGiver` host without the
property.

### Q3: Quest journal, objectives and history

Each character has a journal: objectives per quest and dated entries (the
last 200 are kept), held in the hidden effect `Prop_QuestLog` so it saves with
the character. `QUESTS` shows it.

`MPQOBJECTIVE who quest OP ...`:

| Operation | Effect |
| --- | --- |
| `ADD id count text` | Adds an objective that is done at `count` (an `id` already there has its target and text updated). Optionally `AT=where` after the id ([Q16](#q16-objective-locations-and-quests-where)). Announced as `Quest updated: ...`. |
| `SET id n` | Sets the count. Reaching the target makes it DONE; dropping below un-does that. |
| `BUMP id (n)` | Adds `n` (default 1). |
| `DONE id` | Marks it done and raises the count to the target. |
| `FAIL id` | Marks it failed (`[-]`). |
| `HIDE id` / `SHOW id` | Hides it from `QUESTS` and from notices, or shows it. |
| `CLEAR (id)` | Removes one objective, or all of the quest's. |
| `AT id where` | Sets, or with no place clears, where it is. |

`MPQJOURNAL who quest text` adds an entry dated on the game calendar.
`QOBJECTIVE(who quest id == value)` and `$%QOBJECTIVE(who quest id)%` read an
objective: the count, `DONE` or `FAILED` (or nothing if there is none).

A step can give objectives and entries itself, so they appear for whoever is on
the quest when the step starts and whoever accepts during the step:

```text
set objective drowned 5 Lay 5 drowned men to rest
set journal The bell rang under the water.
```

An objective the character already has is left as it is; an entry is not
written twice running.

In `QUESTS` an objective reads `[x] Lay 5 drowned men to rest 5/5 (done)` or
`[ ] Bring the bell to the chapel`; time left is on the game clock
(`about 3 game hours`, `less than a game hour`). `QUESTS NAME` adds the history;
`QUESTS LOG` the latest entries of all quests. The older hand-set script
variables still work and are shown the same way: `OBJECTIVE1` ... `OBJECTIVE20`
(with `OBJECTIVEnDONE`), `PROGRESS`, `TIME_REMAINING`, `TIME_REMAINING_TYPE`
and `INSTRUCTIONS`, set with `MPSETVAR $i NAME value` in the tracking script.

The "Quest updated:" lines are told to the character whenever an objective or
entry changes. `QUESTNOTICES=OFF` in `coffeemud.ini` or `world.ini` keeps them
back (the journal still fills).

### Q4: Per-character status and outcome

Each quest has, for each character, a status (`QuestRecords`):

| Status | Set by |
| --- | --- |
| `ACTIVE` | Accepting the quest. A character who carries the quest's script with no record is also ACTIVE. |
| `WON` | `MPQUESTWIN`. |
| `FAILED` | `MPQUESTFAIL`; a `KEYPLAYER FAIL` mob dying. |
| `ABANDONED` | `QUESTS DROP` (outcome `DROPPED`). |
| `EXPIRED` | A personal deadline ran out with no `QUESTTIMEOUT_PROG` ([Q11](#q11-per-character-quest-deadlines)). |

Plus an outcome word and the game time. Scripting:

```text
MPQUESTWIN $n bandits OUTCOME spared
MPQUESTFAIL $n oskar the escort died
if QUESTSTATUS($n oskar == FAILED)
	say You let him die.
endif
if QUESTOUTCOME($n bandits == spared)
	say The chief is a guard in town now.
endif
```

`MPQUESTFAIL who quest (reason)` records FAILED with the reason (default
`FAILED`), fires `QUESTFAIL_PROG` and `QUEST_PROG FAIL`, and ends and removes
the character's tracking scripts as `MPENDQUEST who` would. It does not stop
the quest for others. `GIVE STAT KEYPLAYER FAIL` on a mob does it for everyone
on the quest, with the reason `KEYPLAYER`, when that mob dies; plain
`KEYPLAYER TRUE` instead resets the whole quest (a server-event habit that
does not suit a personal story).

Masks: `-QUESTFAIL +quest`, `-QUESTOUTCOME +quest:word` (and the
`+`/`-` inverses). `QUESTS WON` shows the outcome word and win count;
`QUESTS FAILED` lists the rest.

### Q5: Repeatable quests with win counts and cooldowns

Winners now carry a count and a game-clock time of the last win.

| Setting | Meaning |
| --- | --- |
| `SET REPEATABLE NO` | Once won, never offered again. |
| `SET REPEATABLE YES` | May be taken again (also the default when unset: the engine only blocks, it does not require, so a giver's `CAN_ACCEPT` that tests `QUESTWINNER` can still block). |
| `SET REPEATABLE 1 MUDDAY` | May be taken again once that game time has passed since the last win. |
| `SET MAXWINS n` | At most `n` wins per character. |

The engine checks these in `canAcceptQuest`, *before* the giver's
`CAN_ACCEPT`, so markers, offers and boards all honour them. Functions:
`QUESTWINS(who quest >= n)` (a winner from before counts were kept counts as
1), `QUESTWINAGE(who quest > hours)` (-1 if never). A quest that opens after
ten wins of another: `SET PLAYERMASK -QUESTWINS +wolf_bounty:>=10`.

### Q6: Named steps and jumps

`STEP label` names the step that follows. Jump with `MPSTEPQUEST quest =label`
(or `=3`, `+2`), end a branch with `STEP GOTO label`, test with
`QVAR(quest STEPNAME == label)` or `$%QVAR(quest STEPNAME)%`. `QUESTINFO`
shows `Step: 3 (spared)`. The save stores the label as well as the number, so
an edited quest file restores to the right step. A jump to a name that does
not exist is logged (`MPSTEPQUEST ... Unknown Step`) and nothing happens. See
[Steps](#steps) and the branching [worked example](#example-2-a-branching-story-quest).

### Q7: Offers, re-offers and a declined quest accepted later

`Prop_QuestGiver ... REOFFER=ALWAYS|ONCE|<game time>`:

| Value | Offers |
| --- | --- |
| (none) | An area offers once per character per run of the quest; a room, exit, mob or item whenever its trigger comes. |
| `ALWAYS` | Whenever the trigger comes. |
| `ONCE` | Once per run of the quest. |
| `1 MUDDAY` (a game time) | Again after that long since the last offer. |

Offers are recorded in the quest's variables (`OFFERED:CHARNAME`, with the game
time; `SHOWN:CHARNAME` for a quest merely shown by a default giver or read on
a board), so a saved game keeps them. `QUESTS OFFERED` lists the running
quests offered or shown to the character that they can still take now;
`QUESTS ACCEPT n` takes one. A "no" at the prompt is therefore not for ever.

### Q8: Prop_QuestItem

`Prop_QuestItem QUEST=name (NODROP) (NOSELL) (NOGIVE) (KEEPONDEATH) (REMOVEON=...)`
binds an item to a quest:

| Flag | Effect |
| --- | --- |
| `NODROP`, `NOSELL`, `NOGIVE` | Refuse those actions with a message ("You cannot drop X: it is for a quest."). |
| `KEEPONDEATH` | The item stays with the character when they die. |
| `REMOVEON=WON,FAILED,ABANDONED,EXPIRED,ENDED` | The item vanishes (and the character is told) when the quest leaves ACTIVE for its holder that way. `ENDED` means the quest itself stopped. `ALL` (the default) is any of them; `NONE` never. |

`QUESTS name` lists what the character carries for it ("Quest items:").
Default removal is broad; set `REMOVEON` explicitly when an artifact should
remain after completion. Items given to a character by quest scripts are best
registered with `MPQSET * QUESTOBJ $o` as well, so the quest cleans them up if
it stops.

### Q9: QUIET and the quest-script help

`QUIET` and `OPTIONAL` work as described in [QUIET and OPTIONAL](#quiet-and-optional).
The quest-script reference is `AHELP QUESTS` (and `AHELP QUESTINFO`,
`AHELP QUESTCHECK`); this chapter is the long form. `MPSTEPQUEST`'s `+N`, `=N`
and `=LABEL` forms, `MPQUESTWIN -name`, `TIME_REMAINING_TYPE`, and
`QUEST_TIME_PROG` with `-1` and `-2` are documented above.

### Q10: Declarative rewards

`SET REWARDS "awards"` states what winning gives, in the words of achievements:

| Word | Gives |
| --- | --- |
| `100 CURRENCY` | Money |
| `50 XP` | Experience |
| `10% XP` | CoffeeSUD: 10 percent of a level's experience span |
| `2 QP` | Quest points |
| `TITLE=...`, `ABILITY=...`, `EXPERTISE=...`, `STAT=...`, `TATTOO=...`, `ITEM=...`, `MOB=...` | The achievement award of that name; the exact forms are those of `AHELP ACHIEVEMENTS` |
| `FACTION:id:+200` | CoffeeSUD: faction standing (`+n`/`-n`) |
| `ITEM:catalog_name` | CoffeeSUD: a copy of a catalog item (underscores are spaces) |
| `SCRIPT(...)`, `EFFECT(...)`, `FACTION(ID=...)`, `VAR(...)`, `RECIPE(...)` | The extra achievement awards (`AHELP ACHIEVEMENTREWARDS`) |

```text
set rewards "100 CURRENCY 15% XP ITEM:silver_bell_charm FACTION:Brenn:+200"
```

`MPQUESTWIN $n quest REWARD` records the win and gives the awards with one
message (`Your reward: ...`). `MPREWARD $n "awards"` gives an ad-hoc set the
same way. The reward is shown as `Reward:` in `Prop_QuestGiver`'s details, on
boards, and in `QUESTS name`. A catalog item that does not exist, or a faction
that does not, is logged and skipped. **Do not both `MPQUESTWIN ... REWARD`
and also pay by hand (`mpexp`, `give`): the player is paid twice.** Pick one
path. With `SET SHARED PARTY` each party member on the quest is paid.

### Q11: Per-character quest deadlines

`SET DURATION` is the quest's own clock, counted from the quest's start. A
*personal* deadline counts from when you set it:

```text
MPQTIMER $n medicine 6 mudhours
MPQTIMER $n medicine OFF
```

The deadline is kept with the character (saved), counts on the game clock
(a pause or turn-based time holds it), and shows in `QUESTS` (the sooner of
the quest's and the personal one). When it comes: if a `QUESTTIMEOUT_PROG`
exists on the character's scripts (bare, or naming the quest) it runs and
decides; if none exists, the quest fails for them with the status `EXPIRED`
(see [Q4](#q4-per-character-status-and-outcome)). A win or any end of the
quest for that character clears the deadline.

### Q12: Credit for the party or the profile

`SET SHARED` says whose a quest is:

| Value | Meaning |
| --- | --- |
| `NONE` | Default. Each character's own. |
| `PARTY` | When one character accepts, the party members in the same room (the player's characters in the group) take it on too. `MPQUESTWIN` and `MPQUESTFAIL` go to every party member *on* the quest (with `OUTCOME` and `REWARD`). A member in another room when it was accepted is not on it. |
| `PROFILE` | A win, the status and the win count of any character of the profile (the player's whole roster) answers for all of them: `QUESTWINNER`, `QUESTSTATUS`, `QUESTOUTCOME`, `QUESTWINS`, the `QUESTWIN` masks, `REPEATABLE` and `MAXWINS`. The journal and `QUESTS name` are readable by all of them ("carried by Name"). |

A late recruit or a companion resting elsewhere does not get the quest
retroactively. Test both, with two characters.

### Q13: Story templates

Six single-player templates are in the Quest Maker: see
[Story templates and the Quest Maker](#story-templates-and-the-quest-maker).

### Q14: Boards with generated jobs

`Prop_QuestBoard`: see [Quest boards and generated quests](#quest-boards-and-generated-quests).

### Q15: Undroppable main quests and a drop hook

- `SET DROPPABLE FALSE` makes `QUESTS DROP` refuse ("'X' is not a quest you
  can drop.").
- `FUNCTION_PROG CAN_DROP` on the character's tracking script (tried first) or
  on the accept script: a non-empty return refuses and is shown to the player.
- `FUNCTION_PROG DO_DROP` on both runs when the drop is confirmed, before the
  quest's scripts go. The character is then recorded ABANDONED (outcome
  `DROPPED`), `QUEST_PROG END` fires with `DROPPED`, their tracking scripts are
  removed, and `Prop_QuestItem` items marked `ABANDONED`/`ALL` vanish.
- The older `DO_DROP` that only ran when the quest no longer existed still
  runs in that case.

### Q16: Objective locations and QUESTS WHERE

An objective can say where it is:

```text
mpqobjective $n * ADD bell 1 Return the bell AT="Brenn#42"
mpqobjective $n * ADD ned 1 Speak to Old Ned AT=QUESTOBJ:1
mpqobjective $n * AT bell Brenn
```

`AT=` takes a room id (`Brenn#42`), an area name, or `QUESTOBJ:n` (also
`QUESTMOB:` / `QUESTITEM:`, by number or name): wherever the quest's *n*th mob
or item is now. `QUESTS` shows an open objective as `(Area, Room title)` (only
the area for an area); a done objective shows no place. `QUESTS WHERE n`
answers for the first open objective with a place: `... lies to the north.`,
`... you are there.`, or `in Area; you do not know the way from here` when no
path is known.

`QUESTTRACK=OFF|AREA|PATH` in `coffeemud.ini` or `world.ini` sets how much is
revealed: nothing (`OFF`); the area and room in `QUESTS`, and only the area in
`QUESTS WHERE` (`AREA`); or (default `PATH`) that and the first direction.

## Quest properties, items and behaviors

| Class | Kind | Use |
| --- | --- | --- |
| [Prop_QuestGiver](23-properties-reference.md#prop_questgiver) | Property | Offers a quest to a character when a trigger fires on its host. |
| [Prop_QuestMarker](23-properties-reference.md#prop_questmarker) | Property | `(!)` / `(?)` marks after a mob or item in room listings ([Q2](#q2-quest-giver-and-turn-in-markers)). |
| [Prop_QuestItem](23-properties-reference.md#prop_questitem) | Property | Binds an item to a quest ([Q8](#q8-prop_questitem)). |
| [Prop_QuestBoard](23-properties-reference.md#prop_questboard) | Property | A board of generated jobs on an item or room. |
| [Prop_QuestLog](23-properties-reference.md#prop_questlog) | Property | The character's hidden journal. Kept by the game; never give it by hand. |
| [QuestBound](23-properties-reference.md#questbound) | Effect | Put by the engine on every quest mob, item and room; with parameter `*` it makes a mob or item ineligible for quest selection. Resets the quest when a quest object is destroyed or its room resets. |
| [StdQuestBoard](../../CoffeeMud/com/planet_ink/coffee_mud/Items/Basic/StdQuestBoard.java), `GenQuestBoard` | Item | A readable board that lists *running* quests ([22-items.md](22-items.md)). |
| [RandomQuests](24-behaviors-and-masks.md#randomquests) | Behavior | Generates quests from the random-quest templates ([12-generation.md](12-generation.md)). |

### Prop_QuestGiver

Parameters: `QUEST="name"` or `QUEST="GENERATE:(args)"`, `MESSAGE="text"`,
`DETAILS=TRUE|FALSE` (default TRUE), `OPTIONAL=TRUE|FALSE` (default TRUE),
`REOFFER=ALWAYS|ONCE|time`, `TRIGGER=...`.

Works on areas, rooms, exits, mobs and items. When the trigger fires for a
player's character, the quest is offered if it is running, the repeat rules and
`REOFFER` allow it, and its accept script's `CAN_ACCEPT` says yes. The
character sees the `MESSAGE`, then the details (quest name, the giver and
where, in which area, the instructions, the reward) and a `Accept this quest
(y/N)?` prompt. `OPTIONAL=FALSE` skips the prompt and accepts at once.
`DETAILS=FALSE` leaves the details out.

| Host | Default `TRIGGER` | What it means |
| --- | --- | --- |
| Area | `ENTER` | A character entering the area from outside. |
| Room, exit | `ENTER` | A character entering the room, or passing through the exit. |
| Mob | `DEATH` | The mob dying. For a living giver set `TRIGGER=ENTER` (a character enters the mob's room) or `SPEAK` (a character speaks to the mob). |
| Item | `GET` | A character taking the item. |

Other values: `LEAVE`, `PUTIN`, `WEAR`, `WIELD`, `READ`, `LOOK` (the code accepts
`LOOK`; the shipped help lists the others only). `QUEST="GENERATE:..."`
generates a quest through `RandomQuests` with the rest as its parameters. An
unknown trigger or quest name is logged once.

### Quest boards, in short

`StdQuestBoard` and `GenQuestBoard` list every *running* quest that has
`DISPLAY` and `INSTRUCTIONS`, an accept script, and that the reader may take.
`READ board` lists them by display name; `READ board 2` shows the quest, giver,
instructions and reward and asks `Accept this quest (y/N)?`. Reading adds the
quest to `QUESTS OFFERED`. The board's readable text can filter:
`SAREAMASK` (giver's area), `TAREAMASK` (target areas), `AAREAMASK` (either),
`GIVERMASK` (the giver mob), `TYPFILTER` (quest type), `CATFILTER`
(category). The list is recomputed every few seconds when quests change.

### RandomQuests

Parameters: `MIN`, `MAX`, `CHANCE` (how often to try; defaults 10 to 20 ticks,
chance 100), `MINQUESTS`, `MAXQUESTS` (how many at once), `PATH` (the generator
document), `TAGID` (default `all_quests`), `EXPIRE` (how long a generated quest
lives; the code defaults to `6 hours`, the shipped help says 3), `MAXATTEMPTS`
(default 3), `INLINE`, and every other `NAME=value` is passed to the
generator as a variable (`TEMPLATE=normal|auto|random|<template>`,
`AREA_NAME`, `SOURCE_NAME`, `TARGETAREA_NAME`, `TARGETNAME`, `NUM_TARGETS`,
`XP_AMOUNT_AWARDED`, `GOLD_AMOUNT_AWARDED`, `QUEST_POINT_AWARDED`,
`QUEST_MULTIPLE`, `TARGET_MIN_AREALEVEL`, `TARGET_MAX_AREALEVEL`,
`ANNOUNCECHANNEL`, ...: see `AHELP RandomQuests`).

**`PATH` is optional.** With no `PATH`, a RandomQuests (and a `GENERATE:`
giver, and a board without `FILE=`) uses the generator that ships,
`quests/random/all.xml`, which defines `all_quests`. Set `PATH` only to use your
own generator document.

## Story templates and the Quest Maker

`CREATE QUEST` with no script offers the Quest Maker wizard: you pick a template,
fill in pages of prompts, review each page, and confirm. The wizard replaces the
template's `$VARIABLES` in the quest text, saves it as
`resources/quests/NAME.quest`, creates a quest whose script is
`LOAD=quests/NAME.quest`, and saves the quest list. In builder mode, SAVE then
writes the quest to `world/quests` as well.

At the `Select a quest template (?):` prompt, `?` lists templates with their
descriptions, `CANCEL` or Enter leaves.

### Templates shipped

Templates are `resources/quests/templates/*.quest`.

| Templates | Use |
| --- | --- |
| `story_talk1` | Speak with a mob, optionally handing over an item (Story Talk #1). |
| `story_chain1` | Three stages, each with its own mob, speech, objective and journal text (Story Chain #1). |
| `story_choice1` | Two outcomes chosen with `MPCHOOSE`, each with its own text, reward, journal entry and outcome word; the second can slay the mob (Story Choice #1). |
| `story_useitem1` | The giver hands over an item; the player leaves it in a room (Story Use-Item #1). |
| `story_explore1` | Go to a place (Story Explore #1). |
| `story_bounty1` | A kill count, paid at the giver, offered again after a cooldown (`SET REPEATABLE`) (Story Bounty #1). |
| `normal_capture1-4`, `normal_collect1-5`, `normal_delivery1-4`, `normal_dispel1`, `normal_escort1-2`, `normal_killer1-2`, `normal_mystery1-2`, `normal_protect1-2`, `normal_travel1-4` | The inherited one-player-at-a-time quest shapes, also the shapes that `RandomQuests` generates. They use `set wait 1 / set interval 1` so they start by themselves, and announce on `$QUEST_ANNOUNCECHANNEL` if set. |
| `auto_*` | The inherited "auto" variants: on its schedule the quest picks the player characters that match the criteria (`SET PCMOBGROUP RESELECT ANY MASK=...`) and `GIVE SCRIPT`s them its accept script, so they are put on it with no giver. |
| `competitive_*` | Race between several players. Marked `#!QUESTMAKER_MODE MULTIPLAYER`: hidden from the wizard's list in a single-player game, but loadable by name. |

The story templates share one shape: they pick an *existing* giver by area,
room and name, give it `Prop_QuestGiver ... TRIGGER=ENTER REOFFER=ALWAYS`
and `Prop_QuestMarker`, make `KEYPLAYER TRUE` of it, set `SET DURATION 0`,
use `SET REWARDS`, `MPQOBJECTIVE`/`MPQJOURNAL`, `MPQUESTWIN ... REWARD`, and end
the character's script with `MPENDQUEST $n`. The tracking script fails the quest
for the character (`MPQUESTFAIL`) if the person they must find is gone.

### Writing your own template

A template is a quest script with a header. Everything starts with `#`:

```text
#!QUESTMAKER_START_SCRIPT My Template #1
#!QUESTMAKER_MODE SINGLE
#A free-form description, shown when the player types ? at the template prompt.
#
#!QUESTMAKER_PAGE Quest Name/Criteria
#Instructions for the page.
#
#Quest ID:
#Label shown before the next variable.
#$QUEST_ID=$UNIQUE_QUEST_NAME=my_quest_1
#
#Category:
#$CATEGORY=($STRING)=side
#
#!QUESTMAKER_END_SCRIPT My Template #1

set name $QUEST_ID
...
```

- `#!QUESTMAKER_START_SCRIPT name` begins and names the template (the name
  must match the `#!QUESTMAKER_END_SCRIPT`). `#!QUESTMAKER_MODE MULTIPLAYER`
  hides it from a single-player list; `SINGLE` or no line shows it.
- `#!QUESTMAKER_PAGE title` starts a page. Plain `#text` lines are the page's
  instructions and the labels of the variables after them.
- `#$NAME=$TYPE=default` defines a variable. Put the type in brackets,
  `($STRING)`, for an optional entry. For `$CHOOSE` the default is the
  comma-separated list of choices.
- After the end line comes the quest script, written with the `$NAME`s. The
  wizard replaces each `$NAME` with the answer, as plain text, **in page order**,
  so no variable name may begin another's (`$GIVER_NAME` and `$GIVER_NAME2`
  would clash; the fixture checks the shipped ones). `$#AUTHOR` is the creator's
  name. Embedded `<?xml ... <FILE>` sections can use the variables too, which is
  how a template carries its MOBPROG scripts. `${2 *}` inside such scripts is a
  MOBPROG form, not a template variable.
- Quest names the wizard accepts (`$UNIQUE_QUEST_NAME`) are letters, digits and
  `_` only, and must not exist.

Variable types: `$UNIQUE_QUEST_NAME`, `$STRING`, `$LONG_STRING`, `$NAME`,
`$EXPRESSION` (arithmetic), `$TIMEEXPRESSION`, `$ZAPPERMASK`, `$ROOMID`,
`$AREA`, `$CHOOSE`, `$FACTION`, `$ABILITY`, `$MEFFECT` (an ability or
behavior), `$EXISTING_QUEST_NAME`, `$ITEMXML`, `$ITEMXML_ONEORMORE`,
`$ITEMXML_ZEROORMORE`, `$MOBXML`, `$MOBXML_ONEORMORE`, `$HIDDEN` (not asked; the
default is used), and the display-only `$TITLE`/`$LABEL`.

## Quest boards and generated quests

### The board property (Q14)

`Prop_QuestBoard` makes an item or a room a board of generated jobs:

```text
Prop_QuestBoard COUNT=3 REFRESH="1 mudday" TEMPLATE=normal LEVELRANGE=3 FILE=quests/random/all.xml
```

| Parameter | Meaning |
| --- | --- |
| `COUNT` | Jobs kept on the board (default 3). |
| `REFRESH` | How often it renews, on the game clock (`1 mudday`, or `MUDDAY:1`; default 1 mudday). |
| `TEMPLATE` | The generator template (default `normal`). |
| `FILE` | The generator document, passed to `RandomQuests` as `PATH`. **Set it** (see above). |
| `TAGID` | The quest tag in that document (default `all_quests`). |
| `VARS="NAME=value ..."` | More generator variables. |
| `LEVELRANGE=n` | Sets `TARGET_MIN_AREALEVEL` and `TARGET_MAX_AREALEVEL` to the reader's level minus and plus `n`. |
| anything else | Passed to `RandomQuests` (`SOURCENAME`-style variables: `SOURCE_NAME`, `AREA_NAME`, ...). `EXPIRE` is empty unless you set it: a job waits on the board until a refresh. |

LOOK at the board (or READ it, for an item; LOOK in the room, for a room)
lists the jobs the reader may take, with reward, and `ACCEPT n` takes one. At
each refresh, jobs done or given up go and new ones come up to `COUNT`; with
none taken and the board full, the oldest is replaced; a job in progress stays.
The generator is told `PLAYER_LEVEL`, `PARTY_SIZE` (the player's characters in
the reader's group), `PLAYER_FACTION_ID` per faction and `QUESTS_DONE`, so a
template (or `$player_level` in a generator document) can fit the job to the
reader. Jobs are saved with the board.

Reading a board adds each job it shows to `QUESTS OFFERED`.

### Generated quests in general

- **Documents.** `resources/quests/random/all.xml` picks a template by the
  `TEMPLATE` variable (`normal`, `auto`, `random` or a template name) and loads
  `normal_*.xml` / `auto_*.xml`, which define a `<QUEST>` with a quest id,
  criteria, announcer, target, rewards and so on, built by the MUDPercolator
  ([12-generation.md](12-generation.md) has the tag reference). Your own
  document can define `all_quests` or any `TAGID`, like
  `tools/fixtures/fixes/setup/q14jobs.xml` does with `QUEST_TEMPLATE_ID="story_talk1"`.
- **Bounded attempts.** Generation is tried `MAXATTEMPTS` times and the
  behavior disables itself on a permanent failure (missing document, unknown
  tag id, requirements missing). Design a board to tell the player when it
  has nothing, rather than depending on a job.
- **They are copies.** A generated quest is a copy: it deletes itself when it
  stops. A saved game keeps copies whole (their script) with progress, and a
  restore makes them again.
- **Requirements.** Generation needs areas with suitable givers
  (`mql_viable_questgivers`: named mobs with an intelligence over 5, not
  aggressive, with exits) and, for a collect or capture, suitable targets. In a
  tiny world generation may often fail.
- **Level criteria.** Generated quests set `QUEST_CRITERIA` to a level window
  (`+SYSOP +LEVELS -<n ->n` in the shipped `normal_travel2.xml`: archons always
  pass). If a character cannot take a generated quest, read its script with
  `QUESTINFO` and check the criteria.

## JavaScript quest handlers

A quest can have JavaScript handlers, either in a file named by `SET JSFILE`
(`SET JSFILE world:js/quests/bell.js`) or in a `<JSQUEST>` ... `</JSQUEST>`
block of the quest script. Functions (all optional):

| Function | Called |
| --- | --- |
| `onStart(state)` | The quest starts. |
| `onStep(n, state)` | A step starts. |
| `onTimeUp(state)` | The duration ran out. |
| `onWin(player)` | `MPQUESTWIN` named a winner. |
| `onAccept(player)` | A character accepted. |
| `onStop()` | The quest stopped. |

`state` is `cm.store('quest:name')`, saved with the game. The scope also has
`quest` (the `Quest` object) and `quests`, a helper: `quests.get(name)`,
`start(name)`, `step(name)`, `win(name, who)`, `fail(name)`, `stop(name)`,
`running(name)`, `stepNumber(name)`, `status(name)` (`running`, `stopping`,
`finished`, `waiting`, `stopped`, `none`), `objective(name)`, `journal(name)`,
`getVar(name, var)`, `setVar(name, var, value)`. JScriptable objects of the
quest's mobs and items may also define `onQuestAccept(quest, mob)`,
`onQuestStep(quest, step, label)`, `onQuestWin(quest, mob, outcome)`,
`onQuestFail(quest, mob, reason)` and `onQuestEnd(quest, reason)`. `fail(name)`
stops the quest; it does not fail it for one character. Register one
authoritative reward path: quest events and handler callbacks can both observe
the same completion. See [JavaScript](05-javascript.md).

## Quest state in saves

What a campaign save holds for quests (`WorldState` in
[core/save](../../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldState.java)):

- **`QUESTS`**, one `QUEST` per quest in the list: the name, `WINNERS`
  (`name@time;`), `RUNNING`, `FINISHED` (Q1), the quest variables (`VARS`:
  `MPQSET` variables, `OFFERED:`, `SHOWN:`, `POSTED`, `QUESTGIVER`, ...), the
  wait left if not running, and when running: `STEP`, `STEPNAME` (Q6),
  `REMAINING`, and for each quest mob (`QMOB`) and item (`QITEM`) where it is,
  or that it is gone (mobs also their base stats and hit points). A quest that
  is not in the world's files (generated, a copy) is saved whole with its
  `SCRIPT`, so a restore can make it again.
- **`QUESTRECORDS`**, one record per quest and character: status, outcome
  word, game time, win count, last win (Q4, Q5).
- **The character** (their save): the tracking scripts given `SAVABLE`, the
  hidden `Prop_QuestLog` (objectives, entries, deadlines), carried
  `Prop_QuestItem` items.
- **Script variables** in the variable scopes (the quest's name for `GIVE
  SCRIPT` scripts, `*` per character for `INDIVIDUAL`): see
  [state and persistence](state-and-persistence.md).
- **Not saved:** `DELAY_PROG` timers in the quest's scripts; objects the quest
  loaded are re-made when the quest restarts and then matched to the save.

A restore that cannot find a saved quest in the world's files reports "Quest X is
gone from the world." A quest that was running starts again, goes to its saved
step (by name when it has one, so an edited file still restores), gets its time
left and has its mobs and items put back or removed. It starts again before the
rest of the world is put back, so a mob or item of the world it had taken over
(`SET MOB`, `RESELECT`) is taken over again rather than cleared away; one that
cannot start that early (it needs something only the save brings back) starts
once the rooms are back. The world files do not hold
winners or running state; those are only in the campaign save. Editing a quest
file between a save and a restore therefore keeps the progress but may
drop objects that no longer exist. See [Saves and party](31-saves-and-party.md).

## Worked examples

The examples below use area and mob names (`Brenn`, `Old Ned`) that your world
must have; change the names to ones that exist. They use only commands shown
in the shipped templates and fixtures. For each, run `QUESTCHECK name` before
starting, play it through normally once, then use `QUESTINFO name`.

All four are single files you can put in `world/quests/NAME.quest`. The quest
script comes first; after the `<?xml version="1.0"?>` line come the MOBPROG
files it hands out, each between `<FILE><NAME>` and `</DATA></FILE>`. A
`~` on its own line ends each MOBPROG trigger or function. (With CREATE QUEST
you would type one `;`-separated line; a file is much easier to edit.)

### Example 1: a fetch quest

Old Ned lost the chapel bell in the moor. The player takes the quest by
walking into Ned's room and answering `y`, finds the bell, brings it back, and
is paid. Needs: an existing mob "Old Ned" in the area "Brenn"; an area "Moor";
a catalog item named "the sunken bell" (`CATALOG` is described in [Mobs](21-mobs.md)
and [Items](22-items.md): build the item, then add it to the catalog).

`world/quests/bell.quest`:

```text
set name bell
set display The Sunken Bell
set category side
set instructions Old Ned lost the chapel bell in the moor.  Find it and give it to him.
set rewards "50 XP 2 QP"
set repeatable no

# the giver: an existing mob
set area "Brenn"
set room
set mobgroup
set mob reselect "Old Ned"
give stat keyplayer true
give script LOAD=bell_ned.script
give affect Prop_QuestGiver QUEST="bell" TRIGGER=ENTER REOFFER=ALWAYS
give affect Prop_QuestMarker QUEST=bell READYFUNC=CAN_COMPLETE

# the bell: a catalog item, hidden in a random room of the moor
set mob
set area "Moor"
set room any
load item catalog the sunken bell
give affect Prop_QuestItem QUEST=bell NODROP NOSELL REMOVEON=WON

set duration 0

<?xml version="1.0"?>
<FILE><NAME>bell_ned.script</NAME>
<DATA>
FUNCTION_PROG CAN_ACCEPT
	if ISPC($n) and !QUESTSCRIPTED($n *)
		RETURN TRUE
	endif
	RETURN CANCEL
~

FUNCTION_PROG DO_ACCEPT
	mpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=bell_track.script
	mpqobjective $n * ADD bell 1 Find the chapel bell AT=Moor
	mpqjournal $n * Old Ned lost the chapel bell in the moor.
	mpqset * STATISTICS ACCEPTED
~

SPEECH_PROG p i accept
	if CALLFUNC(CAN_ACCEPT)
		sayto "$n" Bring it back and I will pay you.
		MPCALLFUNC DO_ACCEPT
	endif
~

FUNCTION_PROG CAN_COMPLETE
	if QUESTSCRIPTED($n *)
		if HAS($n 'the sunken bell')
			RETURN TRUE
		endif
	endif
	RETURN
~

GIVE_PROG all
	if ISPC($n) and QUESTSCRIPTED($n *)
		if NAME($o == 'the sunken bell')
			mpjunk $o
			mpqobjective $n * DONE bell
			mpqjournal $n * I gave the bell back to Old Ned.
			sayto "$n" The chapel will ring again.
			mpquestwin $n * REWARD
			mpendquest $n
			mpqset * STATISTICS SUCCESS
		endif
	endif
~
</DATA></FILE>
<FILE><NAME>bell_track.script</NAME>
<DATA>
ONCE_PROG 100
	mpsetvar $i INSTRUCTIONS Find the chapel bell in the moor and give it to Old Ned.
~

RAND_PROG 25
	IF !isquestmobalive(1 'bell')
		mpqjournal $i * Old Ned is gone.
		mpquestfail $i * the giver is gone
	ENDIF
~
</DATA></FILE>
```

How it works:

- `set mob reselect "Old Ned"` borrows the world's Old Ned (the first quest mob).
  `give script` adds the accept script; the `Prop_QuestGiver` offers (ENTER on a mob
  is a character entering its room); `Prop_QuestMarker` shows `(!)`, `(?)`.
- `load item catalog ...` puts a copy of the catalog item in a random moor room.
  `Prop_QuestItem` stops the player selling or dropping it.
- Taking the quest gives the character `bell_track.script` (that is what
  `QUESTSCRIPTED` tests) and an objective at the moor. `QUESTS` shows
  `[ ] Find the chapel bell (Moor)` and `QUESTS WHERE 1` the way.
- Giving the bell to Ned triggers `GIVE_PROG`; `MPQUESTWIN ... REWARD` records
  the win and pays `50 XP 2 QP` with one message. `MPENDQUEST $n` removes the
  tracking script. The only payment path is `REWARD`: do not also `mpexp`.
- `set repeatable no`: the engine refuses a second offer after a win. Everything
  else about the quest staying `finished` is Q1: `set duration 0` plus no
  schedule means it runs for ever once started.

Start and test:

```text
questcheck bell
modify quest bell start
questinfo bell
quests
```

If the quest is only `loaded`, `MODIFY QUEST bell START` begins it. To make it
begin by itself when the game starts, add `set autostart boot` (it then runs once
and finishes) or use a schedule such as `set wait 1` and `set interval 1`.

### Example 2: a branching story quest

"Bandits of the Fen": Captain Hale sends the player to the bandit chief, who
offers a choice. Each choice is its own step, both rejoin at an epilogue, and
the captain pays differently. Needs existing mobs "Captain Hale" (area "Brenn")
and "the bandit chief" (area "Fen"). Adapted from fixture `q6-steps.js` and
the `story_choice1` template.

`world/quests/bandits.quest`:

```text
set name bandits
set display Bandits of the Fen
set category main
set droppable false
set repeatable no
set instructions Captain Hale wants the bandit chief in the Fen dealt with.
set duration 0

# step 1: the captain offers it
set area "Brenn"
set room
set mobgroup
set mob reselect "Captain Hale"
give stat keyplayer true
give script LOAD=bandits_captain.script
give affect Prop_QuestGiver QUEST="bandits" TRIGGER=ENTER REOFFER=ALWAYS
give affect Prop_QuestMarker QUEST=bandits

# step 2, "confront": the chief decides
step confront
set duration 0
set mobgroup
set mob
set area "Fen"
set room
set mob reselect "the bandit chief"
give script LOAD=bandits_chief.script
give affect Prop_QuestMarker QUEST=bandits READYFUNC=CAN_DECIDE AVAILABLE= ACTIVE=

# step 3, "spared", and step 4, "slain": each lasts a few ticks, then
# moves itself on to the epilogue
step spared
set duration 5
set journal The chief took his men and left the Fen.
step goto epilogue
step auto slain
set duration 5
set journal The chief is dead, and the Fen is quiet.
step goto epilogue
step auto epilogue

# step 5, "epilogue": back to the captain
set duration 0
set mobgroup
set mob
set area "Brenn"
set room
set mob reselect "Captain Hale"
give script LOAD=bandits_epilogue.script

<?xml version="1.0"?>
<FILE><NAME>bandits_captain.script</NAME>
<DATA>
FUNCTION_PROG CAN_ACCEPT
	if ISPC($n) and !QUESTSCRIPTED($n *)
		RETURN TRUE
	endif
	RETURN CANCEL
~

FUNCTION_PROG DO_ACCEPT
	mpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=bandits_track.script
	mpqobjective $n * ADD chief 1 Decide the fate of the bandit chief AT=Fen
	mpqjournal $n * Captain Hale sent me to deal with the bandit chief.
	mpqset * STATISTICS ACCEPTED
	MPSTEPQUEST bandits =confront
~

SPEECH_PROG p i accept
	if CALLFUNC(CAN_ACCEPT)
		sayto "$n" Find him in the Fen.  Then do what you think right.
		MPCALLFUNC DO_ACCEPT
	endif
~
</DATA></FILE>
<FILE><NAME>bandits_chief.script</NAME>
<DATA>
FUNCTION_PROG CAN_DECIDE
	if QUESTSCRIPTED($n *)
		RETURN TRUE
	endif
	RETURN
~

GREET_PROG 100
	if ISPC($n) and QUESTSCRIPTED($n *)
		sayto "$n" So Hale sent you.  What will it be?
		mpchoose $n CHOICE AB A Spare him (A) or kill him (B)?
		MPCALLFUNC DECIDE
	endif
~

FUNCTION_PROG DECIDE
	if !QUESTSCRIPTED($n *)
		RETURN
	endif
	mpqobjective $n * DONE chief
	if VAR($n CHOICE == B)
		sayto "$n" Then be quick about it.
		mpqset * FATE slain
		mpslay $i
		MPSTEPQUEST bandits =slain
	else
		sayto "$n" You will not regret this.  I leave the Fen today.
		mpqset * FATE spared
		MPSTEPQUEST bandits =spared
	endif
~
</DATA></FILE>
<FILE><NAME>bandits_epilogue.script</NAME>
<DATA>
GREET_PROG 100
	if ISPC($n) and QUESTSCRIPTED($n *)
		mpqjournal $n * Captain Hale heard what became of the chief.
		if QVAR(* FATE == spared)
			sayto "$n" You let him go?  Well, the Fen is quiet.
			mpreward $n "10% XP"
			mpquestwin $n * OUTCOME spared
		else
			sayto "$n" He is dead.  The pass is open again.
			mpreward $n "100 CURRENCY"
			mpquestwin $n * OUTCOME slain
		endif
		mpendquest $n
		mpqset * STATISTICS SUCCESS
	endif
~
</DATA></FILE>
<FILE><NAME>bandits_track.script</NAME>
<DATA>
ONCE_PROG 100
	mpsetvar $i INSTRUCTIONS Find the bandit chief in the Fen and decide his fate.
~
</DATA></FILE>
```

Points to copy:

- Step 1 is everything before `step confront`. `step confront` ends it and
  *names* the next step. The names are `confront`, `spared`, `slain`, `epilogue`
  (steps 2 to 5), each given by the `step` line before it. Every step sets
  `duration`.
- `MPSTEPQUEST bandits =spared` jumps to a name. `step goto epilogue` inside
  steps 3 and 4 makes the epilogue the step after either. They end with
  `step auto ...`, so each moves on by itself when its 5 ticks run out (a plain
  `step` would wait for an `MPSTEPQUEST`). The epilogue step is named by the
  last `step auto epilogue`.
- A step change cleans up the step before: the captain's accept script goes when
  the quest steps to "confront", and the chief is released at "spared"/"slain".
  Because the script that calls `MPSTEPQUEST` is itself given by a step
  that ends, it is the **last line** of its block; nothing after it is relied on.
  The character's `bandits_track.script` is on the player, not on a quest
  object, so it survives the steps.
- The branch is remembered in a quest variable (`mpqset * FATE ...`), saved with
  the quest, and read back with `QVAR(* FATE == spared)` and
  `$%QVAR(* FATE)%`. The `OUTCOME` of `MPQUESTWIN` is a single word, so the
  epilogue writes it out in each branch rather than passing a `$%...%` value.
  `QUESTOUTCOME($n bandits == spared)` then answers from `OUTCOME`; `QUESTS WON`
  shows `bandits (spared)`.
- `set droppable false`: `QUESTS DROP bandits` refuses (the main story).
- The saved game stores the step number *and* its name, so editing the file and
  adding a step above does not send a restored game to the wrong step.
- This quest steps for the whole world. In a single-player game that is
  the story's chapter; do not use this shape for a quest that several
  characters take independently ([Q12](#q12-credit-for-the-party-or-the-profile)).

Test branches without playing them: with the quest running,
`MODIFY QUEST bandits STEP spared` jumps to a step by name; `ASSERT QUEST bandits STEP 3`
(from a test file) checks it; `MODIFY QUEST bandits WIN Name` / `UNWIN Name` set
and clear a win.

### Example 3: a repeatable job, and a job board

A wolf bounty the ranger posts every game day. Up to ten wins. Adapted from
the `story_bounty1` template (the Quest Maker would write the same quest).

`world/quests/wolf_bounty.quest`:

```text
set name wolf_bounty
set display Wolf Bounty
set category side
set instructions Kill five wolves in the woods, then come back to be paid.
set rewards "30 CURRENCY"
set repeatable 1 mudday
set maxwins 10

set area "Brenn"
set room
set mobgroup
set mob reselect "Ranger Tam"
give stat keyplayer true
give script LOAD=wolf_giver.script
give affect Prop_QuestGiver QUEST="wolf_bounty" TRIGGER=ENTER REOFFER=ALWAYS
give affect Prop_QuestMarker QUEST=wolf_bounty READYFUNC=CAN_COMPLETE

set duration 0

<?xml version="1.0"?>
<FILE><NAME>wolf_giver.script</NAME>
<DATA>
FUNCTION_PROG CAN_ACCEPT
	if ISPC($n) and !QUESTSCRIPTED($n *)
		RETURN TRUE
	endif
	RETURN CANCEL
~

FUNCTION_PROG DO_ACCEPT
	mpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=wolf_track.script
	mpqobjective $n * ADD kills 5 Kill wolves
	mpqjournal $n * Tam posted a wolf bounty.
	mpqset * STATISTICS ACCEPTED
~

SPEECH_PROG p i accept
	if CALLFUNC(CAN_ACCEPT)
		sayto "$n" Five pelts, and I pay.
		MPCALLFUNC DO_ACCEPT
	endif
~

FUNCTION_PROG CAN_COMPLETE
	if QUESTSCRIPTED($n *) and QOBJECTIVE($n * kills == DONE)
		RETURN TRUE
	endif
	RETURN
~

GREET_PROG 100
	if ISPC($n) and QUESTSCRIPTED($n *) and QOBJECTIVE($n * kills == DONE)
		sayto "$n" Good work.  Here is what I owe you.
		mpqjournal $n * Tam paid the bounty.
		mpquestwin $n * REWARD
		mpendquest $n
		mpqset * STATISTICS SUCCESS
	endif
~
</DATA></FILE>
<FILE><NAME>wolf_track.script</NAME>
<DATA>
ONCE_PROG 100
	mpsetvar $i INSTRUCTIONS Kill five wolves, then return to Ranger Tam.
~

EXECMSG_PROG STO=DEATH ALL
	if ISNPC($n) and ISLIKE($n '+NAMES "-a wolf"') and !QOBJECTIVE($i * kills == DONE)
		mpqobjective $i * BUMP kills
	endif
~
</DATA></FILE>
```

The repeating comes from three lines: `set repeatable 1 mudday` (a game day
after the last win, counted on the game clock, so a pause holds it),
`set maxwins 10`, and the missing `QUESTWINNER` test in `CAN_ACCEPT`. Checks:

- Right after a win the marker `(!)` and the offer disappear for that
  character; a game day later they come back.
- `QUESTS WON` shows `Wolf Bounty (3 wins)`; `QUESTWINS($n wolf_bounty >= 10)`
  is the test for the follow-up.
- A follow-up quest that only opens after ten bounties:
  `set playermask -QUESTWINS +wolf_bounty:>=10` in *its* script.

The same shape works as a generated job: put a board in a room.

```text
Prop_QuestBoard COUNT=3 REFRESH="1 mudday" TEMPLATE=normal LEVELRANGE=3 FILE=quests/random/all.xml
```

as an effect of an item (add it in the item's MODIFY menu, see
[Items](22-items.md)) or of a room used as a notice-board room. When the player
looks at it, three generated jobs appear numbered; `ACCEPT 2` takes the second;
after a game day finished jobs go and new ones come.
If you want a board to offer *your* hand-written jobs, put a `StdQuestBoard` or
`GenQuestBoard` item in the room instead: it lists every running quest the
reader can accept (the quest needs a `DISPLAY`, `INSTRUCTIONS` and an accept
script). Start `wolf_bounty` (`MODIFY QUEST wolf_bounty START`) and it is listed.

### Example 4: a timed quest

Healer Wren gives the player a vial for Farmer Pell's feverish boy, to be
carried within six game hours. Needs mobs "Healer Wren" (area "Brenn") and
"Farmer Pell" (area "Fenwick"), and a catalog item "a vial of medicine".

`world/quests/medicine.quest`:

```text
set name medicine
set display Medicine for Pell
set category side
set instructions Carry Wren's vial to Farmer Pell in Fenwick before the fever peaks.
set rewards "75 CURRENCY 5% XP"
set repeatable no

set area "Brenn"
set room
set mobgroup
set mob reselect "Healer Wren"
give stat keyplayer true
load item catalog a vial of medicine
give item
give script LOAD=medicine_wren.script
give affect Prop_QuestGiver QUEST="medicine" TRIGGER=ENTER REOFFER=ALWAYS
give affect Prop_QuestMarker QUEST=medicine

set item
set mob
set area "Fenwick"
set room
set mob reselect "Farmer Pell"
give script LOAD=medicine_pell.script
give affect Prop_QuestMarker QUEST=medicine READYFUNC=CAN_COMPLETE AVAILABLE= ACTIVE=

set duration 0

<?xml version="1.0"?>
<FILE><NAME>medicine_wren.script</NAME>
<DATA>
FUNCTION_PROG CAN_ACCEPT
	if ISPC($n) and !QUESTSCRIPTED($n *)
		RETURN TRUE
	endif
	RETURN CANCEL
~

FUNCTION_PROG DO_ACCEPT
	mpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=medicine_track.script
	give "a vial of medicine" "$n"
	mpqobjective $n * ADD deliver 1 Take the vial to Farmer Pell AT=Fenwick
	mpqjournal $n * Wren gave me medicine for Pell's boy.  The fever will not wait.
	MPQTIMER $n * 6 mudhours
	mpqset * STATISTICS ACCEPTED
~

SPEECH_PROG p i accept
	if CALLFUNC(CAN_ACCEPT)
		sayto "$n" Run!  You have six hours at the most.
		MPCALLFUNC DO_ACCEPT
	endif
~
</DATA></FILE>
<FILE><NAME>medicine_pell.script</NAME>
<DATA>
FUNCTION_PROG CAN_COMPLETE
	if QUESTSCRIPTED($n *)
		if HAS($n 'a vial of medicine')
			RETURN TRUE
		endif
	endif
	RETURN
~

GIVE_PROG all
	if ISPC($n) and QUESTSCRIPTED($n *)
		if NAME($o == 'a vial of medicine')
			mpjunk $o
			mpqobjective $n * DONE deliver
			mpqjournal $n * Pell's boy drank the medicine in time.
			sayto "$n" Bless you.  He is already cooler.
			mpquestwin $n * REWARD
			mpendquest $n
		endif
	endif
~
</DATA></FILE>
<FILE><NAME>medicine_track.script</NAME>
<DATA>
ONCE_PROG 100
	mpsetvar $i INSTRUCTIONS Take the vial to Farmer Pell before the fever peaks.
~

QUESTTIMEOUT_PROG
	mpechoat $n The boy is past helping.
	mpjunk a vial of medicine
	mpqjournal $n * I was too late with the medicine.
	mpquestfail $n * the fever won
~
</DATA></FILE>
```

What it shows:

- `MPQTIMER $n * 6 mudhours` is the character's own deadline, from the moment
  they accept (not the quest's start). `QUESTS` shows
  `Medicine for Pell (time left: about 5 game hours)` counted on the game clock,
  so pausing the game or playing in turn mode holds it.
- When it comes, the bare `QUESTTIMEOUT_PROG` on the tracking script runs. It
  records the failure with `MPQUESTFAIL` (status FAILED, reason `the fever
  won`, shown by `QUESTS FAILED`). Without that trigger the quest would fail
  by itself as EXPIRED; keep the trigger when you want a custom ending.
- `mpquestwin` clears the deadline.
- Make the whole quest time-limited for everyone instead with `set duration 6
  mudhours` in the quest: when it ends, everything the quest loaded or gave is
  removed (the vial among it), and `QUEST_PROG END quest EXPIRED` fires. Use
  `STEP AUTO` to move on at the deadline instead of ending.
- Use the personal timer (Q11) for "from when the player took it", and `DURATION`
  for "while this event lasts".

## Quest verification checklist

Run QUESTCHECK, inspect QUESTINFO, and use its test controls for start, step,
win and reset (`MODIFY QUEST ... STEP`, `WIN`, `UNWIN`, `RESETWINNERS`, `REOPEN`,
`VAR`, `RELOAD`). Play the whole quest normally at least once. Verify:

- First offer, refusal, acceptance, duplicate offer and returning later
  (`QUESTS OFFERED`).
- Wrong order, missing/destroyed item, dead giver and alternate branch.
- Win, failure, expiry and abandonment, including cleanup and repeat cooldown.
- Two-character sharing and exactly-once rewards.
- Save in each important step, quit, restore in a fresh process and finish.
- That `QUESTS`, `QUESTS WHERE` and the markers say the same thing as the
  accept script.

The fixtures `tools/fixtures/fixes/q1-finished.js` through `q16-where.js`,
`dt2-questinfo.js` and `dt32-questcheck.js` are the examples of the
objective, reward, deadline, sharing and test-control APIs. Static lint cannot
prove a complete story or all possible player choices. See
[Testing and release](13-testing-release.md).

## Troubleshooting and gotchas

| Symptom | Cause and fix |
| --- | --- |
| Quest listed `loaded` and never starts | No schedule. Start it (`MODIFY QUEST x START`, `MPSTARTQUEST`) or add `SET AUTOSTART BOOT` or a WAIT and INTERVAL. |
| `SET WAIT` quest never starts | A WAIT needs an INTERVAL (`set interval 1`). QUESTCHECK warns. The quest is then FINISHED after its first stop rather than deleted. |
| "Quest NOT started -- check your mud.log" | The script raised an error: a `SET MOB` that found no such mob, a `LOAD` with nothing imported, an unknown word. Read `mud.log`, then `QUESTINFO` ("Last errors"). Use `OPTIONAL` before a command that may fail, and `QUIET` to keep the log clean. |
| "No duration, quest not started" | A step with no `SET DURATION`. Add one (0 is fine). |
| Giver never offers | The quest has no accept script; the quest is not running; the mob is not the first thing selected (a different mob got the `Prop_QuestGiver`); the player already carries or has won it (`REPEATABLE`); `SET PLAYERMASK` fails them; the trigger does not suit the host (a mob's default is DEATH). |
| `QUESTS` shows nothing after a rename | The tracking script's quest name no longer exists: it is removed from the character the next time `QUESTS` is listed. Keep quest NAMEs. |
| Reward paid twice | `MPQUESTWIN ... REWARD` plus hand-written `mpexp`/`give`, or two win paths (script and JavaScript). Use one. |
| Quest ends when a mob dies | `GIVE STAT KEYPLAYER TRUE` resets the quest on death. Use `FAIL` to fail it for the characters instead, or no keyplayer. |
| A step's objects vanished | They are cleaned up at the next step. `SET PRESERVE n` keeps them `n` steps, or select them again in the new step. |
| Edited a quest file, nothing changed | `WORLD RELOAD QUEST name`, `MODIFY QUEST name RELOAD`, or `DEVWATCH ON`. In builder mode a changed file is not overwritten by SAVE if it changed outside the game; see [World workflow](02-world-workflow.md). |
| Random quests / board report "ALL_QUESTS has not been defined" | `PATH`/`FILE` points at a document with no `all_quests`. Leave it unset for the shipped `quests/random/all.xml`, or point it at a document that defines your `TAGID`. |
| Board shows no jobs | Generation failed (see the log), or the reader cannot take them (`PLAYERMASK`, level criteria). Quests listed by a `StdQuestBoard` need `DISPLAY`, `INSTRUCTIONS` and an accept script. |
| Marker shows `(!)` for a quest the player cannot take | `CAN_ACCEPT` and the marker agree by construction (the marker asks the same engine). Check the player's `QUESTS OFFERED`; remember the party and per-character windows: the marker is per viewer. |
| QUESTCHECK reports `SET WEEKDAY unknown` etc. | Known limits of its word list (see [Builder commands](#builder-commands)). |

Other points:

- Chat commands in a MudChat script or semicolons: inline scripts need `\;`;
  files do not.
- Tick timers inside quest scripts (`DELAY_PROG`) are not saved; use
  `MPQTIMER` (character) or a quest `DURATION` (quest) for anything that must
  survive a save.
- Decide whether pausing and travel should preserve or advance urgency, and say
  the deadline in dates the player can see (`QUESTS` shows game time left).
- Unlocking a profile achievement does not mean every character accepted a
  personal quest: `SET SHARED PROFILE` shares the *win* and status, not
  acceptance.
- Generated quests and the stock `normal_*` templates still announce on
  `$QUEST_ANNOUNCECHANNEL`; set it blank in single-player games.

## See also

- [04-mobprog.md](04-mobprog.md), [26-mobprog-triggers.md](26-mobprog-triggers.md),
  [27-mobprog-commands.md](27-mobprog-commands.md),
  [28-mobprog-functions.md](28-mobprog-functions.md): the scripts quests hand out.
- [05-javascript.md](05-javascript.md): JavaScript handlers and stores.
- [06-npcs-dialogue.md](06-npcs-dialogue.md): talking to quest givers.
- [21-mobs.md](21-mobs.md), [22-items.md](22-items.md): the mobs, items and
  catalog quests use.
- [23-properties-reference.md](23-properties-reference.md),
  [24-behaviors-and-masks.md](24-behaviors-and-masks.md): Prop_Quest*, QuestBound,
  RandomQuests, zapper masks.
- [12-generation.md](12-generation.md): the random-quest generator documents.
- [10-world-simulation.md](10-world-simulation.md): holidays, schedules, game time.
- [02-world-workflow.md](02-world-workflow.md), [13-testing-release.md](13-testing-release.md),
  [31-saves-and-party.md](31-saves-and-party.md),
  [state-and-persistence.md](state-and-persistence.md): files, tests and saves.
- [tool-reference.md](tool-reference.md#questinfo): generated command help.
- The CoffeeSUD [scripting supplement](../../CoffeeMud/guides/ScriptingSUD.html)
  (the Quests section) and the inherited
  [GameBuildersGuide](../../CoffeeMud/guides/GameBuildersGuide.html) (Quests;
  its MUDGrinder and online-player text does not apply).
