# Testing, diagnostics and release

[Handbook](README.md) · [Tool reference](tool-reference.md)

This chapter is the testing and debugging manual for CoffeeSUD. It covers
the developer and builder tools that look inside a running world (VARS,
SCRIPT, SCRIPTTRACE, DEVLOG and the rest), the static checkers that lint a
world without playing it (WORLD CHECK, SCRIPTCHECK, PROPCHECK and the rest),
the ways to meet the world as a mortal (PLAYTEST, TESTCHAR and KIT), the
playtest files and runner that turn a play session into a regression test,
the repository's own test suites for engine developers, and the steps for
releasing a world: packaging the folder, versioning it and keeping old saves
working. World creators and scripters will mostly use the first four parts;
engine developers also need the part on the repository suites.

## In this chapter

- [Choose an isolation boundary](#choose-an-isolation-boundary): which mode to test in
- [Modes, access and the tool index](#modes-access-and-the-tool-index): who may run what
- [Static checks and their limits](#static-checks-and-their-limits): reference, one entry per checker
- [Runtime inspection](#runtime-inspection): reference, one entry per inspector
- [Playing the world as a mortal](#playing-the-world-as-a-mortal): PLAYTEST, TESTCHAR, KIT, NOTE
- [World tests](#world-tests): playtest files, ASSERT, JavaScript tests and the runner
- [Checks in a pipeline](#checks-in-a-pipeline): `CHECK=1`, exit codes, commit hooks
- [Save, migration and recovery tests](#save-migration-and-recovery-tests)
- [Release workflow](#release-workflow): packaging, versioning, save compatibility
- [Repository test suites](#repository-test-suites): for engine developers
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## Choose an isolation boundary

Pick the smallest boundary that answers your question. A test that changes
the live builder world is fast but leaves marks; a test in a throwaway
campaign is slower to start but leaves nothing behind.

| Workflow | Use | What survives |
| --- | --- | --- |
| SANDBOX ENTER/PREVIEW | Inspect a catalog entry, template or generated trial | The scratch area is never saved or exported |
| PLAYTEST ON/OFF | Quick in-place mortal checks inside builder mode | Inventory, XP and world changes remain in the builder world |
| PLAYTEST GAME | Play the current builder snapshot independently | A disposable separate world, database, saves and process; closing it discards everything |
| DEV=1 normal game | Campaign debugging with real character state | Normal campaign slots (marked `[dev]`); the source world is unchanged |
| PLAYTEST RUN / WORLD TEST | Repeatable world tests from inside the game | A scratch campaign per file; results arrive asynchronously |
| `tools/playtest.py` | Headless regression and CI | A scratch campaign per file; failing exit code |
| `CHECK=1` | Static lint of the whole world, no player | Nothing is played or saved |
| Repository suites (`tools/check.sh`) | Engine changes | Scratch worlds, databases and saves per group |

Some facts about these boundaries are easy to miss:

- PLAYTEST ON can select PARTY, KIT and DIFFICULTY. OFF heals and ends mortal
  mode. PARTY MORTAL/ARCHON changes one active member in builder mode. These
  powers are not a balanced character-generation system; TESTCHAR and KIT
  produce representative builds.
- The shipped DEV group includes GOTO, which can bypass movement
  restrictions. The CLI world runner uses DEV for its ASSERT diagnostics, so
  verify door and permission gating in a normal game too, or explicitly narrow
  `GROUP_DEV` for those tests.
- BUILD runs one privileged command during a playtest; an editor that asks
  for input after that command returns can require ending mortal mode.
- Headless PLAYTEST GAME prints the prepared folder and the launch command.
  Remove the temporary folder after testing. A separate launcher needs a
  separate save directory: an ownership lock prevents two processes from
  writing the same `SAVEDIR`. Character windows in one process share that
  process's campaign.

## Modes, access and the tool index

CoffeeSUD has no logins or networked players, so tool access depends on how
the game was started and on security flags.

| Mode | How to start it | What it gives |
| --- | --- | --- |
| Builder mode | `CoffeeMud/sud.sh BUILDER=1` | Every character is an Archon. No saves. SAVE and quit write the world folder. Every developer tool is available. |
| DEV=1 game | `CoffeeMud/sud.sh DEV=1` (or `DEV=1` in `coffeemud.ini`) | A normal game with saves. Each character gets the security group named by `DEVGROUP` (default `DEV`) without a change of class. Saves made are marked `[dev]`. |
| Normal game | `CoffeeMud/sud.sh` | No developer tools, except those that check a security flag the character has (for example CMDMOBS) and NOTE, LANGUAGE and COMBATRESULT, which are open to everyone. |
| `CHECK=1` | `CoffeeMud/sud.sh CHECK=1` | Loads the world as a new game, runs WORLD CHECK with no player, exits. |

The DEV group is defined in `coffeemud.ini`:

```ini
GROUP_DEV=GOTO, AT, TRANSFER, STAT, WHERE, LISTADMIN, CMDQUESTS, CMDMOBS, JSCRIPTS, AHELP, FS: RESOURCES
```

It deliberately omits IMMORT and ORDER, which would make the test unlike
play. Because it also omits CMDROOMS, CMDITEMS and CMDAREAS, a few tools whose
only gate is one of those flags (PREVIEW and CHECKTEXT, SANDBOX, DEVWATCH) do
not open in a plain DEV=1 game. Add the flags to `GROUP_DEV`, or set `DEVGROUP`
to a group of your own, if you need them there. In a DEV=1 game a script can
ask `ISDEV()`; in builder mode `ISBUILDER()` (see
[SCRIPTTRACE and ISBUILDER()](#scripttrace)). The DEVSTART key names a room
where new characters start in builder mode and DEV=1 games (for example a dev
hub). See [configuration and administration](19-configuration-and-administration.md)
for the other ini keys.

### GOD and the developer cheats

`GOD` (in a DEV=1 game or builder mode) toggles single powers, kept with the
character as the `Prop_DevCheats` effect. It is the way to play as a mortal
without PLAYTEST, or to walk a dungeon ignoring doors and damage.

| Command | Effect |
| --- | --- |
| `GOD` | Lists the cheats and whether each is on |
| `GOD <cheat> [ON/OFF]` | Turns a cheat on or off; with neither word it turns the cheat over. Several cheats may be named in one command, then ON or OFF |
| `GOD ALL OFF` | Plays as a mortal would |
| `GOD <power> OFF/ON` | Suspends or restores a sysop power by its security flag name (IMMORT, GOTO, ALLSKILLS, ...) |
| `GOD RESET` | Forgets every setting |

| Cheat | Meaning |
| --- | --- |
| NOCLIP | Walk through closed and locked doors and past `Prop_Req*` entry checks; walking costs no movement |
| NOCOST | Skills cost no mana, movement or hit points and need no wait |
| NODAMAGE | Take no damage |
| NODEATH | Hit points stop at 1 |
| SEEALL | See in the dark, the hidden, the invisible and those who sneak; never blind |
| NOAGGRO | Aggressive creatures leave you be |

Some sysop powers already give part of a cheat (GOTO walks through doors,
ALLSKILLS skips the wait, IMMORT keeps you alive, ORDER or CMDROOMS keep the
aggressive away). Turning the cheat OFF takes that away too. Source:
[GodCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/GodCmd.java) and
[Prop_DevCheats](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_DevCheats.java).
Disable the cheats for balance tests; `GOD ALL OFF` is the quickest way. The
administration side of these powers is in
[chapter 19](19-configuration-and-administration.md).

`CHEAT ON/OFF` is a different feature: it opens the world's options marked as
cheats (for example `NEWCHARS=CHEAT`), and a save made while on is marked
`[cheat]` in SAVES.

### Tool index

Every tool in this chapter, with the command's access words, who may run it
(from each command's `securityCheck`) and where it is described.
"Builder/DEV" means builder mode or a DEV=1 game.

| Tool | Command words | Access | Section |
| --- | --- | --- | --- |
| Script variables | `VARS` | CMDQUESTS | [VARS](#vars) |
| Quest inspector | `QUESTINFO`, `MODIFY QUEST ...` | CMDQUESTS (MODIFY: as MODIFY) | [QUESTINFO](#questinfo) |
| World load report | `WORLD PROBLEMS` | as WORLD | [WORLD PROBLEMS](#world-problems) |
| Whole-world lint | `WORLD CHECK`, `sud.sh CHECK=1` | Builder/DEV or CMDROOMS | [WORLD CHECK](#world-check) |
| Script lint | `SCRIPTCHECK` | CMDMOBS, CMDITEMS or CMDROOMS | [SCRIPTCHECK](#scriptcheck) |
| Property lint | `PROPCHECK` | CMDMOBS, CMDITEMS or CMDROOMS | [PROPCHECK](#propcheck) |
| Generator lint | `GENERATE CHECK` | as GENERATE | [GENERATE CHECK](#generate-check) |
| Quest lint | `QUESTCHECK` | Builder/DEV or CMDQUESTS | [QUESTCHECK](#questcheck) |
| Chat lint and test | `CHATTEST`, `CHATLINT`, `CHAT RELOAD` | CMDMOBS | [CHATTEST](#chattest) |
| Text lint and preview | `PREVIEW`, `CHECKTEXT`, `LOOK AS` | CMDROOMS (LOOK AS: Builder/DEV, CMDAREAS or CMDROOMS) | [PREVIEW and CHECKTEXT](#preview-and-checktext) |
| Recipe lint | `VALIDATE RECIPES` | Builder/DEV or CMDRECIPES | [VALIDATE RECIPES](#validate-recipes) |
| Vocabulary tests | `VOCAB` | Builder/DEV or CMDAREAS | [VOCAB](#vocab) |
| Language tests | `LANGUAGE PREVIEW/COVERAGE` | Builder/DEV or CMDABILITIES | [LANGUAGE](#language-preview-and-coverage) |
| Organization lint | `ORGCHECK` | Builder/DEV or CMDFACTIONS | [ORGCHECK](#orgcheck) |
| Puzzle tools | `PUZZLE` | Builder/DEV or CMDROOMS | [PUZZLE](#puzzle) |
| Script runner | `SCRIPT` | CMDMOBS, CMDITEMS or CMDROOMS | [SCRIPT](#script) |
| Developer log | `DEVLOG`, `DEVWATCH` | Builder/DEV (DEVWATCH: builder mode or CMDROOMS) | [DEVLOG](#devlog) |
| Script errors | `SCRIPTERRORS` | Builder/DEV, CMDMOBS or JSCRIPTS | [SCRIPTERRORS](#scripterrors) |
| Script trace | `SCRIPTTRACE` | CMDMOBS or CMDROOMS | [SCRIPTTRACE](#scripttrace) |
| Script profiler | `SCRIPTPROF` | Builder/DEV or CMDROOMS | [SCRIPTPROF](#scriptprof) |
| Behaviour state | `STAT BEHAVIORS`, `TRACEB` | CMDMOBS or CMDROOMS | [STAT BEHAVIORS and TRACEB](#stat-behaviors-and-traceb) |
| Environment | `WEATHER SET/FORECAST/NEXT`, `TIME SET/SEASON`, `LOOK AS`, `HAZARDS` | Builder/DEV or CMDAREAS (HAZARDS also CMDROOMS) | [Weather, time and hazards](#weather-time-look-as-and-hazards) |
| Law | `LAW` | Builder/DEV or ABOVELAW | [LAW](#law) |
| Pricing | `PRICECHECK` | Builder/DEV, CMDAREAS or CMDMOBS | [PRICECHECK](#pricecheck) |
| Clock speed | `TIMESCALE` | Builder/DEV | [TIMESCALE](#timescale) |
| Debug output | `MPDEBUG`, `debug()` | Builder/DEV (silent otherwise) | [MPDEBUG and debug()](#mpdebug-and-debug) |
| Input recorder | `RECORD INPUT`, `RECORD MARK` | Builder/DEV | [RECORD INPUT](#record-input) |
| Notes | `NOTE`, `NOTES` | Everyone (NOTES GOTO: Builder/DEV) | [NOTE and NOTES](#note-and-notes) |
| Dev areas | `WORLD DEV` | as WORLD | [World dev areas](#world-dev-areas) |
| Vessels, instances | `VESSEL STATUS`, `LIST SHIPS/INSTANCES/UNSAVED` | Builder/DEV or CMDAREAS (LIST: LISTADMIN or CMDAREAS) | [VESSEL STATUS and LIST INSTANCES](#vessel-status-and-list-instances) |
| Combat explainer | `COMBATDEBUG` | Builder/DEV or CMDMOBS | [COMBATDEBUG](#combatdebug) |
| Combat results | `COMBATRESULT` | Anyone (needs the combat.profiles module) | [COMBATRESULT](#combatresult) |
| Progression | `PROGRESSION` | Builder/DEV or CMDCLASSES | [PROGRESSION](#progression) |
| Mortal mode | `PLAYTEST`, `BUILD`, `PARTY MORTAL/ARCHON` | Builder mode | [PLAYTEST](#playtest) |
| Test characters | `TESTCHAR`, `KIT` | Builder/DEV | [TESTCHAR and KIT](#testchar-and-kit) |
| Assertions | `ASSERT` | Builder/DEV | [ASSERT](#assert) |
| Scratch area | `SANDBOX` | Builder mode or CMDROOMS | [SANDBOX](#sandbox) |

The same commands appear, with their shipped help text, in the generated
[tool reference](tool-reference.md); this chapter adds the output meaning and
the workflow.

## Static checks and their limits

A static check reads the world (or a file) and reports mistakes without
playing it. They are cheap, so run them often, and run them all before a
release. They are also limited: a clean report does not prove that a story is
reachable, a fight is balanced, a JavaScript branch is correct or every
random combination works.

| Tool | What it checks or explains |
| --- | --- |
| WORLD PROBLEMS | Content skipped or partially loaded from the world files |
| WORLD CHECK | Cross-world errors and warnings, by selectable part |
| SCRIPTCHECK | Known syntax, argument shapes, common arity, blocks, undefined reads |
| PROPCHECK | Property options, types, ranges and references for supported schemas |
| QUESTCHECK | Quest commands, selections, files and embedded scripts |
| GENERATE CHECK | XML templates, classes, IDs, selectors, layouts, variables and JS |
| CHECKTEXT / PREVIEW | Markup, links, details and rendered views |
| CHATLINT / CHATTEST | Dialogue patterns, proven shadowing and simulated responses |
| VALIDATE RECIPES | Recipe shapes and content references |
| VOCAB / LANGUAGE PREVIEW / COVERAGE | Replacement and translation output, dictionary coverage |
| ORGCHECK | Organization, faction and government cross-references |
| LAW TEST / PRICECHECK / PROGRESSION | Explain law, price and advancement decisions |
| PUZZLE CHECK | Existing switches, plates, blockages and signals |
| WORLD COMPOSITIONS CHECK / WORLD COMBAT CHECK / WORLD ITEMMODS CHECK / WORLD MODULES CHECK | Optional skill definitions, pinned combat rules, item modifier libraries and module plans; see chapters [14](14-optional-modules.md), [15](15-ability-composition.md), [16](16-combat-profiles.md) and [17](17-item-modifiers-crafting.md) |

### Errors and warnings

Only WORLD CHECK and `CHECK=1` distinguish severity.

- An **error** is something the engine will get wrong or cannot honor: an exit
  to nowhere, a start room that does not exist, a locked door whose key exists
  nowhere, a script with an unknown trigger or unbalanced IF, a JavaScript
  class that does not compile, a rejected module plan. Errors make
  `CHECK=1` exit with status 1. Fix them.
- A **warning** is something that is probably unintended but can be correct:
  a one-way door, a room nothing leads to, a room that still has the new
  room's default description, a shopkeeper with nothing to sell, a property
  option the checker does not recognize. Warnings never fail a pipeline.
  Judge them: intentionally shared IDs, optional stock resources and
  unstocked scaffold shops are common legitimate warnings.

Individual tools such as SCRIPTCHECK and PROPCHECK print a flat list of
`file:line` or `Area#id / object / property: message` lines with no severity.
Inside WORLD CHECK the same findings are classified: SCRIPTCHECK findings whose
text contains `warning: ` (for example a variable read but never set) are
warnings and all other SCRIPTS findings are errors; every PROPS finding is a
warning.

### WORLD PROBLEMS

```text
WORLD PROBLEMS
```

Lists what did not load whole from the world files. When a hand-edited file
names an unknown item class, ability or behavior, the loader logs it and
skips that object. The file is then marked as loaded only in part, and builder
mode refuses to write it back (a typo must not become lost data) until it is
fixed and the world loaded again, or `SAVE FORCE` writes the file as the world
now has it. With nothing wrong it says `The world files loaded whole.` The same
list is the DATA part of WORLD CHECK (each entry is an error) and is kept in
`worldproblems.txt` beside the content database, so a later start still knows
the file was partial. See the SAVE and
`.builder` rules in [chapter 2](02-world-workflow.md).

### WORLD CHECK

```text
WORLD CHECK (AREA [NAME]) (LOG) (ONLY=[PART],[PART]...)
```

Access: builder mode, a DEV=1 game or CMDROOMS. With no filter it runs every
part over the whole world. `AREA [NAME]` limits the room-based parts to one
area (with `AREA` and no name, your own area; `AREA` takes the next word as
the area's name, so write `LOG` and `ONLY=` before it); `LOG` also writes the
report to the log; `ONLY=` takes one or more part names separated by commas.

Output is a count line, then an `Errors:` block, then a `Warnings:` block,
each finding as `[part] where: what`:

```text
WORLD CHECK: 1 errors, 2 warnings.
Errors:
  [exits] New Area#3 north: an exit to nowhere
Warnings:
  [text] New Area#0: the new room's description
  [reach] New Area: 1 rooms nothing leads to from the start: New Area#9
```

The parts, as `ONLY=` names them:

| Part | Checks | Severity |
| --- | --- | --- |
| `EXITS` | Exits to nowhere; exits that lead into a dev area; doors with no way back | nowhere: error; others: warning |
| `REACH` | Start rooms that do not exist; rooms nothing leads to from the start, by area | missing start: error; unreachable: warning |
| `KEYS` | Locked doors and containers whose key is nowhere (rooms, carried, shops, catalog) | error |
| `TEXT` | Default or unedited room descriptions and titles, rooms tagged TODO (made by `WORLD DIG`/`PATH`), generic mobs and items with their class's default text | warning |
| `SHOPS` | Shopkeepers with nothing to sell | warning |
| `DATA` | What WORLD PROBLEMS lists | error |
| `SCRIPTS` | SCRIPTCHECK over the world or the area | error, or warning when the finding says `warning:` |
| `PROPS` | PROPCHECK over the world or the area | warning |
| `JS` | The world's JavaScript classes: `//extends` lines and whether they compile (world-wide runs only) | error |
| `VOCAB` | Languages, glossary, lists and help files (dictionary scripts that are not approved are warnings) | error / warning |
| `LORE` | Lore entries and files | error / warning |
| `TEMPLATES` | Presentation text and options | error / warning |
| `ROUTES` | Travel and route references | error / warning |
| `IDS` | Stable author identities (duplicate IDs; a duplicate waypoint ID is an error, other duplicates warnings) | error / warning |
| `MODULES` | The configured optional-module plan: blocking problems, clock-mode mismatch | error; stale or unselected manifests: warning |
| `COMPOSITIONS` | Composed ability definitions | error |
| `COMBAT` | Combat profile libraries, the selection, and loaded stat properties | error |
| `ITEMMODS` | Item modifier libraries and tracked items | error when the world opts in to item modifiers, otherwise warning |

`DATA` and `JS` run only on a whole-world check, not with `AREA`. An unknown
name in `ONLY=` is refused and the valid names are listed.
`WORLD CHECK ONLY=VOCAB,LORE,TEMPLATES,ROUTES,IDS` targets the newer asset
domains. Dedicated property checks can append schemas for their current
options. Source:
[WorldCheck](../../CoffeeMud/com/planet_ink/coffee_mud/core/WorldCheck.java).

To act on a result: fix errors first, then read each warning once and either
fix it or decide it is intended. Re-run with `ONLY=` and `AREA` while you
work, then once with no filter before you save.

### SCRIPTCHECK

```text
SCRIPTCHECK [FILE/TARGET/AREA/ALL]
```

Parses MOBPROG scripts with `LOAD=` followed, runs nothing, and reports by
file and line. The argument is a script file in the resources (for example
`scripts/guard.script`, as it would be named in `LOAD=`), a mob or item here
or carried, `ROOM`, `name@room`, `AREA` (your area) or `ALL` (every area of the
world). The result is `SCRIPTCHECK x: no problems
found.` or `SCRIPTCHECK x: N problems:` followed by one `file:line message`
line each, for example:

```text
SCRIPTCHECK scripts/miller.script: 4 problems:
scripts/miller.script:2 unknown trigger GREETPROG
scripts/miller.script:7 unknown command MPECHOT
scripts/miller.script:8 MPSE is not a command; write MPSET or MPSETVAR
scripts/miller.script:15 MPCALLFUNC NOSUCHFUNCTION: no such FUNCTION_PROG
```

It reports unknown triggers (a misspelled `GREETPROG` never fires), unknown
commands (strict: no prefix matching, so `mpechot` is an error and not the
`MPECHO` you meant), unknown functions, wrong argument counts, unbalanced
`IF/ELSE/ENDIF`, `SWITCH/CASE/ENDSWITCH` and `FOR/NEXT`, `MPWHILE` parentheses,
`<SCRIPT>` without `</SCRIPT>`, JavaScript that does not compile and
unapproved blocks, missing `LOAD=` files, `MPCALLFUNC` to a function no
`FUNCTION_PROG` defines, room IDs in `MPGOTO`, `MPTRANSFER` and `MPOLOADROOM`
that do not exist, and (as warnings) variables read but never set. It splits
scripts as the engine's parser does (`;` between commands, `~` between
triggers, `#` comments). Argument signatures and data flow are incomplete, so
some wrong-argument cases still pass.

Known limit: valid `LOAD=world:scripts/...` references currently hit a
separate static file-resolution gap: runtime supports them, SCRIPTCHECK does
not. Use the documented resource-overlay path (a script under
`world/resources/`) as described in the [MOBPROG guide](04-mobprog.md) when a
file must also pass this checker. This is a known tool defect, not proof that
the script's runtime path is missing.

Source:
[ScriptCheck](../../CoffeeMud/com/planet_ink/coffee_mud/core/ScriptCheck.java).

### PROPCHECK

```text
PROPCHECK [ROOM/AREA (NAME)/ALL/CATALOG/MOB OR ITEM]
```

With no argument it checks the room you are in. `AREA` and a name check an
area (your own with no name); `ALL` the world; `CATALOG` the catalog; any
other word is looked up as a mob or item here, carried, or `name@room`. For
every effect and behavior it re-reads the parameter text the way the class
would and reports what would do nothing: the class's own complaints, keys the
property's help (`AHELP PROP_...`) does not name, room IDs that do not
resolve, unknown abilities in a spell list, zapper masks that do not compile,
and effects placed on something they cannot affect. A line reads
`Area#12 / iron chest / Prop_LimitedContents: unknown key MAXX`. PROPCHECK is
the PROPS part of WORLD CHECK. Source:
[PropCheck](../../CoffeeMud/com/planet_ink/coffee_mud/core/PropCheck.java).

PROPCHECK can flag valid dynamic state keys such as Prop_Switch's `MSG_up` and
`MSG_down` because its accepted-key extraction is based partly on literal help
examples. The [audit](../../audit/developer-tools-audit.md#open-property-key-validation-gap)
distinguishes this known warning from a genuinely unsupported parameter. It
also does not detect duplicate IDs in later detail records.

### GENERATE CHECK

```text
GENERATE CHECK [DATA_FILE_PATH]
```

Loads a generator XML file exactly as GENERATE does, with its `LOAD` and
`FROM` includes, but generates nothing. It reports by file, line and tag ID:
whatever loading logs (duplicate IDs, missing includes, `FROM` without an ID),
`INSERT` IDs that are not defined, `LIKE` IDs that are not defined or are of
another tag kind, `CLASS` values that are not a class of their kind (catalog
and metacraft entries excepted), unknown layouts and unknown `SELECT` modes. The
output is `GENERATE CHECK x: no problems found.` or `... N problems:` and a list.
Variable flow, unsatisfiable requirements and unknown attributes are not yet
checked. The generator language is in [chapter 12](12-generation.md). Source:
[GenerateCheck](../../CoffeeMud/com/planet_ink/coffee_mud/core/GenerateCheck.java).

### QUESTCHECK

```text
QUESTCHECK ([QUEST]/[FILE]/ALL)
```

Reads a quest script without starting it or making anything and reports each
problem with its line: commands, `SET` variables and `GIVE`, `TAKE`, `LOAD`
and `IMPORT` kinds the quest engine does not know; no `SET NAME` or `SET
DURATION`; a `SET WAIT` with no `SET INTERVAL` (which deletes the quest after
its first stop); `LOAD=` and `IMPORT` files that are neither the quest's own
`FILE` sections nor in the resources; named-step cross references; and,
through SCRIPTCHECK, each script the quest gives. It takes a file in
`world/quests`, a loaded quest by name, a file in the resources, or `ALL` (the
default), which checks every file of `world/quests`. Builder mode also runs
the `ALL` form at start, in one line. Access: builder mode, DEV=1 or CMDQUESTS.
See [chapter 8](08-quests.md).

### CHATTEST

```text
CHATTEST [NPC] [WHAT YOU WOULD SAY]
CHATLINT [CHAT FILE]
CHAT RELOAD ([CHAT FILE])
```

For NPCs that talk through MudChat. CHATTEST shows what the NPC would make of
a sentence, without anyone speaking: its base and active chat groups,
anything that would stop it answering (it cannot hear or see you, it answered
you last, another NPC spoke, three or more players are here), each entry the
words lead to and whether it matches, and the replies of the first that does,
with weights and `$n` variables filled in. CHATLINT checks a chat file as the
parser reads it: patterns that do not parse, malformed `${}` lines, `@` links
to groups not defined above them, replies with no pattern, patterns with no
replies, unreadable files and lines the parser skips, each with its line
number. CHAT RELOAD re-reads one chat file or all of them; builder mode
re-reads an edited chat file by itself. Access: CMDMOBS. Hidden-pattern
reachability and tick-odds analysis remain limited. Dialogue is covered in
[chapter 6](06-npcs-dialogue.md).

### PREVIEW and CHECKTEXT

```text
PREVIEW (ROOM) ([CONDITION]...)
CHECKTEXT (ROOM/AREA/WORLD)
LOOK AS [CONDITION]...
```

Only room titles and descriptions vary (`<VARIES>` text), so these tools
work on the room you stand in. PREVIEW shows the title and description as
they read under the conditions you give, and changes nothing. Conditions
combine freely:

| Condition | Words |
| --- | --- |
| Time of day | `DAY`, `NIGHT`, `DAWN`, `DUSK` |
| Season | `SPRING`, `SUMMER`, `FALL`, `WINTER` |
| Weather | `RAIN`, `SNOW`, `CLEAR`, `FOG`, `THUNDERSTORM`, `BLIZZARD`, `WINDY`, `CLOUDY` and the other weather names |
| Arrival | `FROM [DIRECTION]`, `FROM UNKNOWN` |
| Visit history | `VISITED`, `FIRSTVISIT` |
| Reader | `AS [CHARACTER]` (for MASK parts and what they are doing) |
| Script variable | `VAR NAME=VALUE` (a variable of the reader, put back afterwards) |

The result is `PREVIEW <conditions said back>:` then the title and
description. `LOOK AS` is the same preview, available to builders through the
LOOK command. CHECKTEXT lists what in rooms' titles and descriptions would
print as literal text instead of varying: variation tags in a text that does
not start with `<VARIES>`, tags that are none (`<nigth>`), tags in small
letters (their end is found only in capitals), tags opened and not closed or
closed and not opened, a MASK containing `>` (write `&gt;`), and an unclosed
colour code. CHECKTEXT defaults to the room, `AREA` and `WORLD` widen it.
Output is `CHECKTEXT: no problems found.` or a count and list. Access:
CMDROOMS. Presentation text is covered in
[chapter 11](11-presentation-language.md).

### VALIDATE RECIPES

```text
VALIDATE RECIPES ([SKILL]/ALL)
```

Checks every row of a crafting skill's recipe file and its extension files
(`.txt.1` and on): each value as `MODIFY RECIPE` would take it (materials,
resources, item classes, spells and so on) and each row's column count, and
reports every problem with its file and row, not only the first. `ALL` (the
default) checks every skill, then `hunting.txt`, `fishing.txt` and
`components.txt`. Access: builder mode, DEV=1 or CMDRECIPES. Crafting is in
[chapter 7](07-items-crafting.md).

### VOCAB

```text
VOCAB TEST [TEXT]
VOCAB INPUT [COMMAND LINE]
VOCAB LIST [KEY]
VOCAB RELOAD
VOCAB ADD [SECTION] [RULE]
```

Tests the translation and parser rule files. `TEST` shows a text after the
session, final and filter translations with each rule that matched. `INPUT`
shows what the raw-input parser and the command pre-processor make of a typed
line: the command, or the commands it splits into, and each rule that matched
(try `VOCAB INPUT get sword and shield from chest`). `LIST` shows a
`lists.ini` key's value in effect and the file it came from (the world's
`resources/` over the game's). `RELOAD` drops the cached rules, lists,
socials and help so the files are read again. `ADD`, in builder mode,
appends a rule under its section to the world's own rule file
(`world/resources/translation_...` or `parser_...` for the
`COMMAND-PRE-PROCESSOR`, `ITEM-PRE-PROCESSOR`, `ITEM-FAIL-PROCESSOR` and
`RAW-INPUT-PROCESSOR` sections) and takes effect at once; for example
`vocab add final-translation replaceall "colour" with "color"`. Access:
builder mode, DEV=1 or CMDAREAS. See
[chapter 11](11-presentation-language.md).

### LANGUAGE PREVIEW and COVERAGE

```text
LANGUAGE PREVIEW [ID] ([PROFICIENCY]) [TEXT]
LANGUAGE COVERAGE [ID] [FILE/ROOM/AREA/HERE]
```

For invented languages. PREVIEW shows what a language does to a text, at a
proficiency (0 to 100, 50 if none is given): what a listener who does not
know it hears, what a listener at that proficiency understands, how a speaker
at it is heard, what it says written and what a reader at it reads, and each
word's translation with its source (the dictionary, or a word list by the
word's length). COVERAGE counts how many words of a text the dictionary
has, over a file, a room (a room ID or `HERE`) or an area's rooms and their
mobs and items, and names the most common missing words
(`Dunetongue's dictionary has 41 of 60 words (68%).`). `LANGUAGE` with other
words is the ordinary LANGUAGES list. The preview and coverage forms need
builder mode, DEV=1 or CMDABILITIES.

### ORGCHECK

```text
ORGCHECK ([FACTION FILE]/CLANS/GOVERNMENTS/ALL)
```

Re-reads faction files line by line so that what the game's reading hides
shows: a key given twice (only the later counts), bands that overlap or leave
a gap, `CHANGE` triggers that match nothing, `REACTION` and `AFFBEHAV` IDs
that are no ability, behavior, command or social, a `REACTION` range that is
no band's code, and a `RELATION` faction that does not exist. `CLANS` lists the
world files' mobs whose `CLAN` is no clan (they lose it as the world loads);
`GOVERNMENTS` lists governments whose `ENTRYSCRIPT` fails SCRIPTCHECK; `ALL`
(the default) does all of it. Access: builder mode, DEV=1 or CMDFACTIONS. See
[chapter 9](09-society.md).

### PUZZLE

```text
PUZZLE LIST ([AREA])
PUZZLE CHECK (AREA [NAME])
PUZZLE SET [TARGET] [STATE]
PUZZLE RESET [TARGET]
PUZZLE SIGNAL [ROOM/AREA/WORLD/TARGET] [NAME] ([ARGS])
```

Inspects and drives the existing puzzle primitives. `LIST` shows an area's
switches (`Prop_Switch`), pressure plates, blockages and the script lines that
use `SIGNAL_PROG`, `SWITCH_PROG` and `PRESSURE_PROG`. `SET` moves a switch to
one of its declared states and runs its actions, `RESET` returns it to its
start state and rearms `ONCE`, and `SIGNAL` sends a signal through the normal
`SIGNAL_PROG` delivery path and says how many hosts received it. `CHECK` (the
world, or `AREA name`) lists the places, doors and things the world's scripts
name that are not there: a room ID for `MPGOTO`, `MPAT` or `MPTRANSFER` that
is no room, a direction for `MPOPEN`, `MPCLOSE`, `MPLOCK` or `MPUNLOCK` with no
door in the host's room, and an `MPOLOAD` or `MPMLOAD` name that is neither
class nor catalog entry. Words containing `$` codes are left alone because
they are only known when the script runs. The output is `PUZZLE CHECK the
world: N problems.` and a list. SET, RESET and SIGNAL change the world.
Access: builder mode, DEV=1 or CMDROOMS. A generic puzzle controller remains
deferred. Puzzle objects are in [chapter 7](07-items-crafting.md).

## Runtime inspection

These tools look at a running world. Several of them change it (they run a
script, set a variable, move a puzzle, change the weather or the clock);
read-only diagnostic output does not imply that every command in the same
tool family is read-only. Each entry below says which verbs change state.

### VARS

```text
VARS LIST ([SCOPE]) ([HOST]) ([MASK])
VARS SCOPES
VARS SET [HOST] [NAME] [VALUE] (SCOPE [SCOPE])
VARS CLEAR [HOST] ([NAME]/*) (SCOPE [SCOPE])
VARS WATCH ([HOST] [NAME]) / VARS UNWATCH
VARS DUMP [FILE]
```

Shows and changes the variables scripts keep (`MPSETVAR`, `$%VAR()%`). Access:
CMDQUESTS. Without a verb it is `LIST`.

- **LIST** prints one line per variable: `scope  host  name = value`. The
  scope argument is `*` (all, the default), `GLOBAL`, `QUEST name` (or just a
  quest's name), a scope name set by `MPSETINTERNAL SCOPE`, or `INDIVIDUAL`
  for engines that keep their own variables (listed as
  `INDIVIDUAL:<owner>`). The host and mask arguments accept `*` as a wild
  card anywhere in the word and are matched without regard to case.
  `vars list * Hero QUEST_*` shows the hero's quest flags.
- **SCOPES** counts hosts and variables per scope (`FIXSCOPE: 1 hosts, 1
  variables`).
- **SET** sets one as `MPSETVAR` would, so `++`, `+5` and `-2` do arithmetic.
  The scope is global unless `SCOPE x` closes the command. The value is every
  word after the name. `vars set Hero ACT 3` starts a test at act 3. Names are
  stored in capitals.
- **CLEAR** clears one variable, or all a host has when the name is `*` or
  omitted.
- **WATCH host name** (either may be `*`) tells you of each change, with the
  script file that made it and the room it ran in: `GLOBAL FIXHOST FIXA =
  '12' (an inline script in ...)`. `VARS WATCH` alone lists yours; `UNWATCH`
  stops them. Use it to find the script that keeps resetting a variable.
- **DUMP file** writes the whole list to a file (a path the CMFile system
  allows you to write, for example `/resources/dump1.txt`) so you can compare
  two runs.

A script can list names too: `$%VARNAMES(host prefix)%`, also usable as a
condition (see [chapter 28](28-mobprog-functions.md)). Source:
[Vars](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Vars.java).

### QUESTINFO

```text
QUESTINFO [QUEST NAME/NUMBER]
MODIFY QUEST [NAME] STEP [NUMBER/STEP NAME]
MODIFY QUEST [NAME] WIN [CHARACTER]
MODIFY QUEST [NAME] UNWIN [CHARACTER]
MODIFY QUEST [NAME] RESETWINNERS
MODIFY QUEST [NAME] REOPEN
MODIFY QUEST [NAME] VAR [NAME] [VALUE]
MODIFY QUEST [NAME] RELOAD
```

`QUESTINFO` shows a quest on one screen. The number is the one `LIST QUESTS`
gives (counting from 1). The screen holds: whether it runs, waits, is stopped
or finished; its step number and name; the time left (or until it starts); its
variables; the MOB, ITEM, ROOM and AREA it designated and its groups, with
where each is; the mobs and items it holds; the scripts, behaviors and
effects it added and to what; its winners; and its last errors. A step that
sets no duration stops the quest and says so here. Access: CMDQUESTS.

The `MODIFY QUEST` verbs are the quest test controls:

| Verb | Effect |
| --- | --- |
| `STEP n` / `STEP name` | Jump a running quest to a step by number or by step name. Tells you if the step failed or had errors |
| `WIN name` | Declare a character a winner |
| `UNWIN name` | Remove a winner |
| `RESETWINNERS` | Forget every winner |
| `REOPEN` | Make a finished quest one that has not run, so it can start again |
| `VAR name value` | Set a quest variable (name in capitals) |
| `RELOAD` | Drop the parsed-script cache, re-read the quest script and the scripts it loads, and if it was running restart it at the step it was at with its variables |

Example: after editing a quest file, `MODIFY QUEST bell RELOAD`, then `MODIFY
QUEST bell STEP 4`, then `QUESTINFO bell` shows the ghost in its room, the
wall count and the last error ("no mob found named drowned priest"). The quest
language is in [chapter 8](08-quests.md). Source:
[QuestInfo](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/QuestInfo.java)
and `questTestVerb` in
[Modify](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Modify.java).

### SCRIPT

```text
SCRIPT LIST [TARGET]
SCRIPT FIRE [TARGET] [TRIGGER] ([WORDS]) (AS [WHO]) (WITH [ITEM])
SCRIPT CALL [TARGET] [FUNCTION] ([ARGUMENTS])
SCRIPT RESET [TARGET] (ONCE/DELAY/TIME/ALL)
SCRIPT DISABLE/ENABLE [TARGET] ([TRIGGER])
SCRIPT ALARMS [TARGET] (LIST/CLEAR)
```

Runs a host's script triggers on demand, to test them without waiting for the
moment they are for. The target is a mob or item here (or carried), `ROOM`
(or `HERE`), `AREA`, or `name@room`. A host with no scripts is refused. Access:
CMDMOBS, CMDITEMS or CMDROOMS.

| Verb | What it does |
| --- | --- |
| `LIST` | The host's triggers, numbered, with `(file:line)` where each is written; `[off]` marks a disabled trigger and `[done]` a `ONCE_PROG` that has run |
| `FIRE trigger [words] [AS who] [WITH item]` | Runs the triggers of that kind now, as if `who` (you by default; a mob in the room or a player) had caused them, whatever their own conditions and odds. `trigger` may be `SPEECH` or `SPEECH_PROG`, `GREET`, `GIVE`, `DEATH`, `TIME_PROG`, and so on. Given words, only the triggers that answer to them run (a `SPEECH_PROG` with the word; the `TIME_PROG` of the hour given), and the words are the message (`$g`); `WITH` supplies the item (`$o`). Says how many ran, or `has no SPEECH_PROG answering 'x'` |
| `CALL function [args]` | Runs a `FUNCTION_PROG` and prints `NAME returned 'value'` |
| `RESET [ONCE/DELAY/TIME/ALL]` | Lets `ONCE_PROG`s, `DELAY_PROG` waits, or the `TIME_PROG` and `DAY_PROG` of this hour and day run again; `ALL` (the default) is all three |
| `DISABLE` / `ENABLE [trigger]` | Turns one trigger kind, or all, off or on, as `MPNOTRIGGER` does from inside a script |
| `ALARMS [LIST/CLEAR]` | Lists what is queued to run (`MPALARM`s and triggers waiting their tick); `CLEAR` forgets them |

Examples:

```text
script list baker
script fire baker TIME_PROG 5 AS Bob
script fire innkeeper SPEECH room
script fire guard GIVE WITH pass
script call innkeeper PayRoom 5
script reset innkeeper ONCE
script alarms innkeeper
```

`SCRIPT FIRE` and `CALL` really run the script: its commands change the
world. The trigger and function names are the ones in
[chapter 26](26-mobprog-triggers.md) and the commands in
[chapter 27](27-mobprog-commands.md). Source:
[ScriptCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/ScriptCmd.java).

### DEVLOG

```text
DEVLOG ON (ERROR/WARN/INFO/DEBUG) (MODULE [MASK])
DEVLOG OFF
DEVLOG LAST ([NUMBER]) (ERROR/WARN/INFO/DEBUG) (MODULE [MASK])
DEVLOG CLEAR
DEVLOG WINDOW (ERROR/WARN/INFO/DEBUG) (MODULE [MASK])
DEVLOG WINDOW OFF
DEVWATCH ON ([SECONDS]) / DEVWATCH OFF
```

Access: builder mode or DEV=1. Single-player writes every log stream to
`mud.log`; DEVLOG shows each line the engine logs to you as it is logged,
whatever `mud.log` keeps: script errors, JavaScript errors, `MPLOG`, `MPDEBUG`
and `debug()` lines (shown whatever you watch), quest failures and what the
world files' loader skipped.

- **ON** takes the minimum level (`ERROR`, `WARN` which is the default,
  `INFO`, `DEBUG`; each includes those above it) and `MODULE mask`, module
  names separated by commas with `*` for any letters (`Scripting,Quest*`).
  Each line comes as one coloured line with its level and module. A line
  repeated is shown once, then the count when something else comes.
- **OFF** stops it.
- **LAST** replays the last lines kept (up to 500), repeats counted. The
  number defaults to 20 and the level to DEBUG (everything).
- **CLEAR** forgets the kept lines.
- **WINDOW** opens a separate display-only log window with its own filters
  and recent history; `WINDOW OFF` closes it. With no display (headless),
  `ON` and `LAST` show the log in the terminal and WINDOW says so.

In builder mode the setting is kept beside the database for next time; the
`DEVLOG=` key in `coffeemud.ini` (`DEVLOG=WARN Scripting,Quest*`) gives a
default otherwise. `DEVWATCH ON [seconds]` looks at the world files and the
resources' scripts every few seconds and runs the reload that matches what
changed, each noted in DEVLOG; `OFF` stops it (builder mode or CMDROOMS).
Source: [DevLog](../../CoffeeMud/com/planet_ink/coffee_mud/core/DevLog.java).

### SCRIPTERRORS

```text
SCRIPTERRORS ([NUMBER]) ([TEXT])
SCRIPTERRORS CLEAR
```

Lists the script errors kept (the last 200, MOBPROG and JavaScript both),
oldest first, each with its file and line, for example
`progs/elder.script:212 MPECHOT Syntax: unknown command MPECHOT (a quest
giver @ Dunmoor#4)` or `... MPSE Syntax: ambiguous command MPSE: MPSET,
MPSETVAR`. A number limits it (20 by
default) and text keeps only errors holding that text. Access: builder mode,
DEV=1, CMDMOBS or JSCRIPTS.

What a normal game does with script errors is the `SCRIPTERRORS` ini key:
`LOG` (the default) only logs them, `NOTICE` tells each player once that a
script went wrong, `SHOW` shows each to the room it happened in. Builder mode
and DEV=1 always tell the builder each one, so a playtester never reads a raw
engine error. An unknown command is an error, as is an ambiguous MP word.
Source: [ScriptErrors](../../CoffeeMud/com/planet_ink/coffee_mud/core/ScriptErrors.java).

### SCRIPTTRACE

```text
SCRIPTTRACE [TARGET/ROOM/AREA] ([TRIGGER])
SCRIPTTRACE OFF
```

Traces the scripts of a mob or item (here, carried, or `name@room`), of
everything in this room (`ROOM`), or in this area (`AREA`) to your session
only, not the log, until `SCRIPTTRACE OFF`. A trigger name after the target
traces that trigger only. It reports each trigger's chance as it is rolled
and whether it ran (`GREET_PROG 50: rolled 73 of 50, skipped`), a trigger
turned off (`MPNOTRIGGER` or `SCRIPT DISABLE`), a host too busy to be
triggered (asleep, fighting), each line run, and each variable changed. One
trace at a time; it is not saved. Access: CMDMOBS or CMDROOMS.

A script can trace itself with `MPSETINTERNAL TRACE TRUE` (honored only in
builder mode or DEV=1), and can ask whether the game is in builder mode with
`ISBUILDER()`, as a condition or as `$%ISBUILDER()%`; `ISDEV()` asks the same of
a DEV=1 game. Use these to guard debug-only script lines, but prefer
`MPDEBUG`, which is silent outside developer games on its own.

### SCRIPTPROF

```text
SCRIPTPROF ON/OFF/RESET
SCRIPTPROF REPORT ([NUMBER]) (AREA)
```

Times each script trigger. `ON` counts and times every trigger run (the whole
run, including the functions and loops it calls) by file and line. `REPORT`
lists the ones that took longest in all (10, or the number given), each with
its share, runs, longest run and the host of that run; `AREA` limits it to your
area. `OFF` stops counting but keeps the numbers until `RESET`. The ini key
`SCRIPTSLOW=` (milliseconds, 0 for none) logs a warning to `mud.log` for each
trigger run longer than that, with host, file and line, because the one game
thread waits for it; an `MPWHILE` cut off at its 4-second cap now says so.
Access: builder mode, DEV=1 or CMDROOMS.

### STAT BEHAVIORS and TRACEB

```text
STAT BEHAVIORS ([MOB/ITEM/HERE])
TRACEB [MOB/ITEM/HERE] ([TICKS])
TRACEB OFF
```

`STAT BEHAVIORS` (also spelled `BEHAVIOURS`) lists each behavior of a mob, item
or this room (`HERE`, also `name@room`) numbered, with its parameters and what
it has built up while running; for a behavior on a timer it adds how many
ticks until it acts, how often, its chance and when it last acted. Keys in the
parameters that the behavior's help (`AHELP BEHAVIOR_...`) does not name are
reported as `unknown key X (AHELP BEHAVIOR_NAME)`; the generic `WHEN`, `TOD` and
`HOURS` keys are exempt. Useful live internal state is exposed only by some
behaviors.

`TRACEB` tells you, for the number of ticks given (20 by default), each
decision of the host's timed behaviors: when one's time comes, whether it
acts, could not act (asleep, bound) or lost its chance roll. `TRACEB OFF`
stops. Access: CMDMOBS or CMDROOMS. Behaviors are in
[chapter 24](24-behaviors-and-masks.md).

### MPDEBUG and debug()

`MPDEBUG message` is a MOBPROG command; `debug(x)` is its JavaScript
counterpart in `<SCRIPT>` blocks and world JavaScript. In builder mode or
DEV=1 they write the message to the developer log as
`[debug host@room file:line] message`, and DEVLOG shows these lines whatever
level and module filters you set. In a normal game they do nothing, so you
can leave them in a shipped script. `MPLOG` is the older variant that writes
to the log file. Compare `SCRIPTTRACE`, which shows every line run.

### WEATHER, TIME, LOOK AS and HAZARDS

```text
HAZARDS ([AREA NAME])
WEATHER SET [WEATHER] (FOR [N] HOURS/DAYS) (AREA [NAME]) (NEXT)
WEATHER FORECAST
WEATHER NEXT
TIME SET [HOUR/DAWN/DAY/DUSK/NIGHT]
TIME SEASON [SPRING/SUMMER/FALL/WINTER]
LOOK AS [CONDITION]...
```

Available in builder mode, DEV=1 or with CMDAREAS (HAZARDS also CMDROOMS). The
plain WEATHER, TIME and LOOK commands are the player's; these words are added
for builders.

- **HAZARDS** lists what an area holds that acts on those in it: the area's own
  behaviors and effects (spawners such as `RandomMonsters`, weather) and, in
  its rooms, exits and items, their behaviors, traps, `Prop_InstantDeath`,
  `Prop_NarrowLedge` and `Prop_Weather`, each with its state (as STAT BEHAVIORS
  gives it) or its parameters. It ends `none.` if the area has none.
- **WEATHER SET** changes the area's weather now, as a change of weather comes,
  with its messages and the scripts that wait on it. Names: CLEAR, CLOUDY,
  WINDY, RAIN, THUNDERSTORM, SNOW, HAIL, HEAT, SLEET, BLIZZARD, DUST, DROUGHT,
  COLD, FOG (the variation names HEATWAVE and DUSTSTORM also work). `NEXT`
  changes only the weather that comes next. `FOR 3 HOURS` holds the weather
  there, each change coming back to it, for that much game time (units HOURS,
  DAYS, WEEKS, MONTHS or TICKS). `AREA name` targets another area.
- **WEATHER FORECAST** shows the weather now, what comes next, a hold, and the
  odds of each weather the step after, from the area's season and climate.
  **WEATHER NEXT** steps to the next weather.
- **TIME SET** moves the area's clock forward to the hour (a number, or
  DAWN/DAY/DUSK/NIGHT) hour by hour as `TICKTOCK` does, so time-based
  triggers run; it always moves forward, wrapping past the end of the day, and
  never back. **TIME SEASON** advances a month at a time, events running, until the season
  comes (at most 24 months).
- **LOOK AS** is PREVIEW (see [above](#preview-and-checktext)).

TIME SET, TIME SEASON and WEATHER SET change the live world and its clock.
Environment systems are in
[chapter 10](10-world-simulation.md).

### LAW

```text
LAW
LAW CRIMES ([TEXT])
LAW WARRANTS ([NAME])
LAW PARDON [NAME] ([CRIME]/ALL)
LAW ACCUSE [NAME] [CRIME]
LAW TEST [CRIME] (AS [CHARACTER]) (AGAINST [VICTIM])
LAW SET KEY=VALUE
```

Inspects the law of the area you are in, as it found its people and places by
name. Access: builder mode, DEV=1 or ABOVELAW. Bare `LAW` shows the legal
behavior and its law file, the officers and judges it found among the area's
mobs (or that it found none, so a misnamed guard shows at once, and a mob named
as one that cannot be, with why: an officer must wander, a judge must stay
put), the jail and release rooms each name found (or `FOUND NOWHERE`), the
taxes, and the counts of crimes and warrants.

- **CRIMES** lists crimes with names, sentences, places and circumstances, or
  those holding the text.
- **WARRANTS** lists the warrants out, or a person's.
- **PARDON** removes a person's warrants, or those for one crime.
- **ACCUSE** makes a warrant, as a witness would.
- **TEST** applies the real warrant-eligibility rules and explains the refusal
  (or the warrant that would result) without changing warrants or campaign
  dice; it uses a fixed roll for random ignore flags. `AS` names the actor and
  `AGAINST` the victim.
- **SET** copies a shared law into the area's custom law before editing one key
  (`KEY=VALUE`, key letters/digits/underscore); in builder mode the custom
  definition is exported with the world.

PARDON, ACCUSE and SET change the world. Law is described in
[chapter 9](09-society.md).

### PRICECHECK

```text
PRICECHECK [ITEM] (AT [SHOPKEEPER]) (FOR [CHARACTER]) (SELL)
```

Explains how a shop's price was reached. It lists the settings the shop prices
by (`PREJUDICE`, `PRICEMASKS`, `BUDGET`, `DEVALRATE`, `INVRESETRATE` and
`IGNOREMASK`) with where each came from (the shopkeeper, its area, a parent
area, the ini, or a running quest), then each step of the sum: base price, the
prejudice rule that matched, each price mask that matched, charisma against the
keeper's wisdom, sales tax, the fall for what is in stock, and the rounding to
coins; last both prices, the budget left and when the stock is next put back.
Without `SELL` it is what the shop asks; with `SELL` what it pays. Without `SELL` the
item is looked for in the shop's stock, then the character's and your own
inventory, then the room; with `SELL` (which must be the last word) the
character's inventory comes first and the shop's stock after it. With no `AT`, the first shopkeeper here that is not a player;
with no `FOR`, yourself. Access: builder mode, DEV=1, CMDAREAS or CMDMOBS.

### TIMESCALE

```text
TIMESCALE ([RATE])
```

Shows or sets how fast the game clock runs against real time, from 0.1 to 20.
At 8, eight game seconds pass in each real one, so ticks, cooldowns, decay,
effects and the calendar all come eight times as soon; a caravan route or a
weather change is watched in minutes. PAUSE and turn-based time work as
before, and the prompt shows `(x8)` while the rate is not 1. Outside the range
it refuses (`TIMESCALE is from 0.1 to 20.`). The `TIMESCALE=` ini key sets it
at start. Access: builder mode or DEV=1.

### RECORD INPUT

```text
RECORD INPUT [FILE]
RECORD INPUT
RECORD INPUT OFF
RECORD MARK [TEXT]
```

Access: builder mode or DEV=1 (`RECORD [player]` and `RECORD SET` remain the
Archon recording skill). `RECORD INPUT file` writes each line typed, in every
window, to a playtest file: `FILE.play` in the saves folder's `recordings`
folder unless FILE contains a folder. The file starts with a header of what the
game started from (the dice seed, `#realtime` if not turn-based, `#builder` or
`#restore slot` or `#new`, the character as a `#char` line, and the world's
checksum) and a comment before each typed line gives the character and the
game time. `RECORD MARK text` puts a `# MARK` comment where you saw the bug.
`RECORD INPUT` alone shows what is being recorded, `OFF` stops it. The ini key
`RECORDINPUT=1` records each game from when the player comes into the world,
into `input-DATE-TIME.play`.

The file is a starting point for a `.play` test: delete the lines you do not
want, then add `#expect` and `#assert` lines (see
[World tests](#world-tests)). Edit sensitive or incidental input before
sharing. In turn-based time (`TIMEMODE=TURNS`) a recording plus a fixed seed
plays the same game again, in this build or a later one that changed nothing
the recording touches: things tick in the order they came into the game, not
in an order the build happens to give them. In real time the moment each line
arrives still matters, so it is not an exact replay; that dedicated feature
(`DETERMINISTIC=1`) remains deferred.

### NOTE and NOTES

```text
NOTE (HERE) [TEXT]
NOTES (ALL)
NOTES DONE [NUMBER]
NOTES UNDONE [NUMBER]
NOTES GOTO [NUMBER]
```

Works in any game. `NOTE text` appends a playtest note to `notes/playtest.txt`
(the `NOTESFILE=` ini key changes the path), outside the world and the saves,
so a note made while playing reaches builder mode. Each note records the date,
the save, the character, the room's ID and name, the game time and the text.
`NOTE HERE text` also records the room as it is: its exits, mobs, items,
effects and behaviors. `NOTES` lists notes still to do, numbered (`ALL` adds
those done); `DONE` and `UNDONE` mark one; `GOTO` takes you to the note's room
(builder mode or DEV=1 only). Give testers `NOTE` as the way to report a
problem while playing in a normal game.

### World dev areas

```text
WORLD DEV
WORLD DEV [AREA] ON
WORLD DEV [AREA] OFF
```

Areas in `world/dev` are for building and testing only: builder mode and DEV=1
games load them, a normal game never does, and they do not count toward the
world checksum that makes a game rebuild its world (they have their own
`dev.sum`). `WORLD DEV` lists them. In builder mode `WORLD DEV area ON` makes
an area a dev area (SAVE writes it to `world/dev`) and `OFF` makes a shipped
one again. `DEVSTART=` in `coffeemud.ini` can start new characters in one, such
as a hub with portals and test gear. WORLD CHECK warns of an exit from a shipped
area into a dev area, which leads nowhere in a normal game. A dev area is the
place for test fixtures that must never ship: debug NPCs, teleporters, gear
stashes. See [chapter 2](02-world-workflow.md).

### SANDBOX

```text
SANDBOX ENTER / LEAVE / CLEAR
SANDBOX PREVIEW [CATALOG NAME/TEMPLATE]
GENERATE ... SANDBOX
```

The Sandbox is a scratch area of four bare rooms in a row, marked unsavable, so
neither the world files nor the database ever hold it. `ENTER` takes you there
(creating it the first time) and `LEAVE` takes you back where you were; `CLEAR`
removes its mobs and items and the areas GENERATE made for it. `PREVIEW`
places a copy of a catalog mob or item, or a builder template, in it and shows
it, its gear and what it sells, until you leave. `GENERATE ... SANDBOX`
generates there with `NOSAVE`, and an area it makes is unsavable too. Access:
builder mode or CMDROOMS. Use it to try a generator or a catalog entry
before it touches the real map.

### VESSEL STATUS and LIST INSTANCES

```text
VESSEL STATUS [NAME]
LIST SHIPS
LIST INSTANCES
LIST UNSAVED
```

Access: builder mode, DEV=1 or CMDAREAS for `VESSEL`; the `LIST` forms
need LISTADMIN or CMDAREAS (`LIST UNSAVED`: LISTADMIN or CMDROOMS), and
LISTADMIN is in the DEV group. `VESSEL STATUS` shows a vessel as it
is: where it is and where it is docked, the way it faces, its anchor, course,
top speed and pace, hull, owner and home port, the behaviors on it, its decks
and those aboard with their state, and its crew and passengers. `LIST SHIPS`
adds each vessel's anchor, course and owner. `LIST INSTANCES` lists the
instance areas made from others, each with its parent, its class, the
characters it was made for, the ticks until it times out, its rooms and a room
to `GOTO`. In builder mode `LIST UNSAVED` lists each room whose mobs or items a
building command (CREATE, MODIFY, DESTROY, COPY, TRANSFER, RESTRING, EVERY,
GENERATE) changed since it was saved, with the commands and the change in its
counts; `SAVE CHANGED` saves them. Also: `VESSEL <name> ENTER` visits its first
interior room, `VESSEL <name> RENAME <new name>` renames the interior and room
IDs, `VESSEL <name> HOME <room ID>` changes its home port. See
[chapter 30](30-travel-vehicles-instances.md).

### COMBATDEBUG

```text
COMBATDEBUG ON/OFF ([TARGET])
COMBATDEBUG LOG [FILE]/OFF
```

Explains each attack on one line. With a target (a character in the room) it
covers attacks by or on that character; with none, every attack. A line shows
the attack against the armor, the fudge, the hit chance and the roll, the
damage of each formula (`TARGETED`, `CRIT`, `FUDGE`), each effect or item that
changed the damage and what it made of it (an absorb or a resistance) or that
stopped it, and what was dealt. Damage outside an attack, such as a spell's,
gets a line of its own. `LOG file` writes the lines to a file as well; `LOG
OFF` stops. `COMBATDEBUG` alone reports what it is watching. Access: builder
mode, DEV=1 or CMDMOBS. Use it while tuning armor, formulas
(`FORMULA_NPCATTACK` and friends) and difficulty. See
[chapter 29](29-combat-and-encounters.md).

### COMBATRESULT

```text
COMBATRESULT ([1-8])
COMBATRESULT STATS
```

A player-side command that is also useful when testing the optional
`combat.profiles` module (see [chapter 16](16-combat-profiles.md)). It needs
the module and its `player_trace` setting. It reads your recent resolved
combat-profile events, newest first (history holds at most eight events per
participant and starts anew after boot or restore) or your current profile
stats. Chains and burn ticks each create results, so the newest may be a burn.
Reading repeats no action and consumes no dice or scheduler time. Only
composed direct hits and their profiled burns use these rules; ordinary
native combat is unchanged. `WORLD COMBAT` is the creator-side inspector.

### PROGRESSION

```text
PROGRESSION [CLASS] ([RACE]) ([TO] [LEVEL])
PROGRESSION XP ([AREA])
```

The first form prints the level curve of a class (a Human to level 10 unless
a race and level are given; the level is clamped to 1-100): for each level the
experience it takes, the hit points, mana and moves, practices and trains, and
the skills gained, those given at once marked `*`. `PROGRESSION XP` adds up
the experience the content gives: the achievements', then each area's (the
literal `MPEXP` amounts in its scripts and its `Prop_EnterAdjuster`
experience), with the level a character reaches having done it all, area by
area in order, for example `Dunmoor: 1200 (3400 in all, level 7)`. Use it to
check that the world's rewards match the curve. Access: builder mode, DEV=1
or CMDCLASSES. See [chapter 3](03-characters.md).

## Playing the world as a mortal

Builder mode makes every character an Archon, which hides most problems: the
Archon is not attacked by `Aggressive` mobs, is not followed by `Follower`,
is not scaled to by `Prop_Doppleganger`, gets no rolled variable equipment and
can walk through anything. These tools let you meet the world as a player
would.

### PLAYTEST

```text
PLAYTEST ON (PARTY=name,name) (DIFFICULTY=preset) (KIT=name)
PLAYTEST OFF
PLAYTEST GAME
PLAYTEST RUN [FILE]
PARTY MORTAL [NAME]
PARTY ARCHON [NAME]
BUILD [COMMAND]
```

Access: builder mode. `PLAYTEST` with no word toggles mortal mode.

**ON.** While on, every sysop power you have is suspended: aggressive mobs
attack you, followers follow, dopplegangers scale to you, spawns spawn as for
a player, and the prompt says `(playtest)`. A death takes you back, whole, to
the room you began the playtest in (a `Prop_Playtest` effect remembers it).
The options are `key=value` words: `PARTY=a,b` brings those characters of your
profile into the party (and tests them as mortals too), `DIFFICULTY=preset`
sets a difficulty preset (an unknown one is refused), `KIT=name` gives you a
kit from `world/kits` (a missing or invalid kit refuses the whole command).
Any other option is refused. ON keeps inventory, XP and world changes: it is
the same world you are building.

**OFF** ends it, restores your powers, makes you whole and makes peace, for
the party too.

**BUILD command** runs one command with your powers back, so you can fix
something without leaving mortal mode (`build modify mob sentry`). `BUILD` is a
command only during a playtest; otherwise `BUILD` is the Building skill. An
editor that asks for input after the command returns may refuse; end the
playtest instead.

**GAME** snapshots the current world, including unsaved builder definitions,
and the party into a disposable campaign in another process and window, as a
normal (non-builder, non-DEV) game with a restored character who has the
playtest effect. Close the window to discard every change; the builder world
stays open and unchanged. With no display (headless) it prints the temporary
folder and the exact command to run in another terminal, and the folder must
be removed by hand. It refuses if the world did not load whole (see WORLD
PROBLEMS and WORLD STATUS). Source:
[IsolatedPlaytest](../../CoffeeMud/com/planet_ink/coffee_mud/core/IsolatedPlaytest.java).

**RUN file** runs playtest files in disposable campaigns; it is the same as
`WORLD TEST` (see [World tests](#world-tests)).

**PARTY MORTAL name** and **PARTY ARCHON name** (builder mode only; `ME` names
yourself) change one member of your party: MORTAL puts the playtest effect on
them, ARCHON removes it and makes them an Archon again. This lets you `SWITCH`
to a mortal tester while you stay an Archon.

Tip: PLAYTEST ON for quick checks of one room's ambush, `PLAYTEST GAME` for
anything that changes lots of state (a boss fight, a long quest), and a normal
DEV=1 or plain game for the final pass. `GOD ALL OFF` in a DEV=1 game is the
lightest version of the same idea.

### TESTCHAR and KIT

```text
TESTCHAR [NAME] (KIT=[KIT]) (PARTY)
KIT [KIT] ([CHARACTER OR MOB])
```

Access: builder mode or DEV=1. `TESTCHAR` makes a character of your profile
with no questions: an average Human Apprentice of level 1 (every base stat 10),
or what the kit says, levelled by the CHARGEN levelling rules, starting where
you are or in the kit's room. With `PARTY` the new character is brought into
the world and the party (as `PARTY ADD`); without it they rest in the profile
and `PARTY ADD name` brings them in. A name already in use is refused. Any
problem inside the kit is listed after "But:" and the character is still
made.

`KIT name [target]` gives a character, follower or mob here (you by default,
or a profile member found anywhere) the kit: race, class, gender and
alignment first, then level, items, gold, abilities, factions and variables,
and finally the room.

Kits live with the world, one per file: `world/kits/NAME.kit`. Each line is a
setting word and its value; `#` starts a comment. Unknown words and empty
values are reported (`NAME.kit:7: 'xyz' is not a kit setting`).

| Setting | Value | Notes |
| --- | --- | --- |
| `race` | Race ID or name | |
| `class` | Class ID or name | |
| `gender` | M, F or N (first letter used) | |
| `alignment` | good, neutral, evil... | A faction alignment name |
| `level` | Whole number | Levelled up from 1 by granting experience |
| `gold` | Number | Sets the character's money (replaces it; a starting kit adds) |
| `room` | Room ID | Start room and location for a new character; with KIT on a live character, moves them |
| `item` | Item class, or `catalog:NAME`, or a plain catalog name | Worn if it can be. A count may come first: `item 2 catalog:a ration` (1 to 100) |
| `ability` | `ID` and optional proficiency | Default proficiency 100 |
| `faction` | `ID VALUE` | Sets standing |
| `var` | `NAME VALUE` | A global script variable of the character, so a test can start at act 3 |
| `wear` | `no` | Keep items in the pack instead of wearing them |
| `starting` | `all`, or a zapper mask such as `-BASECLASS +Fighter` or `-TATTOO +ORIGIN_NOBLE` | Makes the kit a starting kit: given to every new character it lets in |
| `choose` | A group name | Kits sharing a group form a menu in the KIT step of character creation, after the origin; one is taken (a lone one with no question) |
| `title` | Text | The kit's name in that menu |

A starting kit gives its items, gold (added), abilities, factions and vars
but not its race, class, level or room. Starting kits are part of character
creation ([chapter 3](03-characters.md)); this chapter uses kits as test
fixtures. Example kit for a mid-game Ranger:

```text
# world/kits/act2-ranger.kit
race Elf
class Ranger
gender F
alignment good
level 5
gold 120
room Moor#12
item catalog:Hunter's Longbow
item 2 catalog:a ration
ability Skill_Dodge 75
faction Merchants 2500
var ACT 2
```

```text
testchar Tess KIT=act2-ranger PARTY
kit act2-ranger Heals
```

Source:
[TestCharCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/TestCharCmd.java)
and [KitCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/KitCmd.java).

### Testing as another character

When you only need to test as a different character, use `PARTY MORTAL`,
`SWITCH` and TESTCHAR instead of editing your own Archon. The playtest runner
uses a DEV=1 mortal by default and an Archon only for `#builder` files.

## World tests

A world test is a text file kept in the world's `tests` folder
(`world/tests/`). The runner plays it in a fresh disposable campaign and
reports PASS or FAIL. Because the world's tests live with the world, they
travel with a release and anyone can run them.

There are three kinds of file, all in `world/tests`:

| Extension | Runs as | Use for |
| --- | --- | --- |
| `.play` | A new game (default) as a mortal DEV=1 character, typing the commands | Player-visible behavior: text, movement, quests, shops |
| `.test` | Identical to `.play` | A naming convention only |
| `.js` | A JavaScript test in a builder campaign, via JRUN | Direct checks of world data and engine state |

### Running tests

In the game (`WORLD TEST` in builder mode or a DEV=1 game; `PLAYTEST RUN`,
being part of PLAYTEST, in builder mode only):

```text
PLAYTEST RUN start.play
PLAYTEST RUN
WORLD TEST start.play
WORLD TEST
```

With no file name it runs everything under `world/tests`. The runner accepts
only paths inside the world's `tests` folder, and `.play`, `.test` or `.js`
files. It launches Python, so it needs the developer checkout's
`tools/playtest.py` (found as `../tools/playtest.py` from the `CoffeeMud`
folder you launched in) and Python 3; the `sud.python` system property selects
the executable (`python3` on Unix, `python` on Windows). One run at a time;
a run is stopped after 10 minutes; the results arrive asynchronously as a
`WORLD TEST PASS` or `WORLD TEST FAIL` message with up to about 24000
characters of output and the log file's path. The runner copies the world
folder as it is on disk, so save your builder changes first.

From the repository root:

```sh
python3 tools/playtest.py                                  # every file in CoffeeMud/world/tests
python3 tools/playtest.py CoffeeMud/world/tests/start.play # one file
python3 tools/playtest.py path/to/tests                    # every .play/.test/.js in a folder
WORLD=/abs/path/world python3 tools/playtest.py /abs/path/world/tests
tools/playtest.sh ...                                      # the same, as a shell wrapper
```

`WORLD=/absolute/world` selects the source world copied for each run; the
default is `CoffeeMud/world`. Note that with no file arguments the runner
always looks in `CoffeeMud/world/tests`, even when `WORLD` is set, so pass the
test files or folder explicitly for another world. Each file gets its own
fresh world copy, fresh database and saves, `RANDOMSEED` 42 and turn-based
time unless overridden. Output per file:

```text
PASS  CoffeeMud/world/tests/start.play (3 checks)
FAIL  CoffeeMud/world/tests/shop.play
        line 14: expected 'You buy'
        line 20: ASSERT FAIL: HAS sword: the character has no such thing
        (transcript: /tmp/sudplaytest.ab12cd/transcript.txt)
```

The exit status is 1 if any file failed, 0 otherwise. A passing run's scratch
folder is removed unless `KEEP=1`; a failing run's folder always stays (its
path is on the last line) so you can read the full transcript. Separate files
do not share a prior file's saved slot. The underlying driver,
[sudplay.py](../../tools/sudplay.py), feeds the input one line at a time and waits
for the game to go quiet between lines; its timeouts can be raised with
environment variables for slow tests: `SUDPLAY_LIMIT` (whole run, default 300
seconds) and `SUDPLAY_EXPECT_LIMIT` (longest wait for an expected text,
default 1800).

### The .play file format

One command per line. Blank lines are ignored. Lines beginning with `#` are
directives or comments (an unknown directive is a comment).

| Line | Meaning |
| --- | --- |
| `# anything` | A comment |
| `#char NAME,RACE,CLASS[,GENDER[,ALIGNMENT]]` | The character a new game creates. Default `Wanderer,Human,Apprentice,M,good` |
| `#seed N` | The dice seed. Default 42 |
| `#realtime` | Real time instead of turn-based time (`TIMEMODE=REALTIME`) |
| `#builder` | Run in builder mode as an Archon instead of a mortal DEV=1 game |
| `#new` | A new game (the default) |
| `#restore SLOT` | Restore this save instead of starting a new game (see the note below) |
| `#expect TEXT` | `TEXT` appears in what the preceding commands printed |
| `#reject TEXT` | `TEXT` does not appear in it |
| `#assert WHAT` | The developer `ASSERT WHAT` answers `ASSERT ok` |
| `#wait N` | `WAIT N`: N ticks pass |
| `#save SLOT` | `SAVE SLOT` |
| any other line | A command typed into the game |

A **step** is a run of commands up to the next `#expect`, `#reject` or
`#assert`. The runner puts an invisible mark (`ASSERT MARK n`, which prints
`PLAYSTEP n`) after the last command of each step, and `#expect` and `#reject`
look at everything the game printed between the previous mark and this one,
including asynchronous messages from NPCs. Several checks in a row all look at
the same step. Text matching is a case-sensitive substring test. A check with
no command before it in a fresh step (for example two `#assert` lines in a row)
looks at the state at that point.

If the game ends early, later checks fail with `the play did not reach this
step`. The run also fails when the game process exits with a non-zero status
or the transcript shows a JavaScript error.

The runner answers the stock character-creation prompts with a fixed
sequence built from `#char`: name, `Y`, `N`, race, `Y`, gender, `N`, class,
`Y`, alignment, then an empty line. A world that adds creation steps
(origins, kit menus, a prologue) changes that sequence, and the runner has no
directive for the extra answers (unverified for such worlds); for those tests
start from a save instead or use a `.js` test.

`#restore SLOT` makes the run start with `RESTORE=SLOT`, but each file has a
fresh, empty save folder, so there is no slot to restore and the run fails
with "There is no saved game". The directive exists so recordings that started
from a save parse; it is not yet a way to chain tests.

Group a menu command and its answer before the first expectation: the
assertion markers use the shared input stream and can otherwise be consumed as
menu answers. For example:

```text
say gate
1
#expect How do I open the gate?
```

These directives belong to the Python runner. They do nothing in a script
attached to an NPC.

### ASSERT

```text
ASSERT HERE [ROOM ID]
ASSERT HAS [ITEM]
ASSERT VAR [HOST] [NAME] [=/!=/</>/<=/>=] [VALUE]
ASSERT QUEST [NAME] STEP [NUMBER]
ASSERT MARK [N]
```

Access: builder mode or DEV=1. `ASSERT` answers with a line a test (or you)
can read: `ASSERT ok: ...` or `ASSERT FAIL: ... and why`.

| Form | Passes when | Failure message |
| --- | --- | --- |
| `HERE roomID` | The character is in that room (compared with the extended room ID, e.g. `New Area#0`, ignoring case) | `HERE x: the character is in y` |
| `HAS item` | The character carries or wears something matching the name | `HAS x: the character has no such thing` |
| `VAR host name op value` | The global script variable compares so. Numbers compare as numbers if both sides are numeric, otherwise case-insensitively as text. Operators `=` (`==`), `!=` (`<>`), `<`, `>`, `<=`, `>=` | `VAR host NAME op v: it is 'x'`, or `'op' is no comparison` |
| `QUEST name STEP n` | The quest (name or `LIST QUESTS` number) is running at step n | `it is at step m` or `not running`, or `there is no such quest` |
| `MARK n` | Always; prints `PLAYSTEP n` (the runner's own step marker) | |

The command `ASSERT` is also useful by hand when you want an exact yes or no
rather than reading output.

### JavaScript tests

A `.js` file in `world/tests` is wrapped by the runner with a tiny test
library and run through `JRUN` in a builder campaign. The wrapper defines:

- `test(name, fn)`: runs `fn`, records a failure instead of stopping.
- `assert(condition, message)`: throws if the condition is false.
- `assert.equal(actual, expected)`: strict equality (`===`).

and ends by printing `WORLDTEST SCRIPT PASS (n tests)` or `WORLDTEST SCRIPT
FAIL: name: error | ...`. The runner needs the PASS line and rejects the FAIL
line. The whole [JavaScript API](05-javascript.md) (`cm`, `mob()`, `gen`,
`Packages...`) is available.

```javascript
test('the starting room exists', function () {
    assert(cm.room('New Area#0') != null, 'missing start');
});
test('dice bounds', function () {
    var n = cm.dice(1, 6, 0);
    assert(n >= 1 && n <= 6, 'roll out of range');
});
test('the guide passes the script checker', function () {
    var guide = cm.findMob('museum guide', cm.room('CreatorMuseum#0'));
    var problems = Packages.com.planet_ink.coffee_mud.core.ScriptCheck.checkHost(guide, null);
    assert(problems.isEmpty(), String(problems));
});
```

Convert returned Java strings with `String(value)` when comparing. Syntax
errors, thrown errors and failed assertions fail the run. A test that only
proves an object exposes the field your setup just assigned is weak; prefer
visible behavior and state transitions. To test checks that only exist in
Java (such as `ScriptCheck.checkHost`), call them through `Packages...` as
above. Because the file runs in a builder campaign, it sees the world as
saved and acts as an Archon: use it for data and engine state, and a `.play`
file for what a player experiences. The [example pack](examples/README.md)
supplies a playable test pair (`museum.play` and `museum.js`).

### Writing good world tests

- Test visible outcomes: text the player reads, where they end up, what they
  hold, what a quest says. Avoid asserting internal fields.
- Keep each file focused (one scene or one system) and short enough to
  diagnose from its transcript.
- Seed matters. The runner fixes the seed at 42 and uses turn-based time so
  combat and random strings repeat; do not depend on a particular roll, test
  that a roll is possible.
- Start with RECORD INPUT while you play the scene by hand, then trim it and
  add the expectations.
- Verify door, key and permission gating in a normal game as well: the runner
  gives its character the DEV group, whose GOTO bypasses movement
  restrictions.
- Run the tests on a clean checkout of the released world, not only on your
  working folder.

## Checks in a pipeline

Three things make a world fit for an automated pipeline: a static check that
exits non-zero on errors, a runner that exits non-zero on failed tests, and
isolation so neither touches your working folders.

### CHECK=1

```sh
CoffeeMud/sud.sh CHECK=1
```

`CHECK=1` loads the world as a new game would, runs WORLD CHECK over all
parts with no player, prints the report, and exits. The prints start with
`World check (CHECK=1): the world is loaded, checked, and the game ends.` and
then the same report as WORLD CHECK:

```text
WORLD CHECK: 0 errors, 3 warnings.
Warnings:
  [text] Emberfall#4: the new room's description
  ...
```

The exit status is 1 if there is at least one error, 0 for warnings only.
No saves are made, and the dev
areas (`world/dev`) are not loaded, so they are not checked. A world whose
files fail to load at all stops before the check; run WORLD PROBLEMS in
builder mode for that. Add `WORLDDIR=/abs/path/world` for a world other than
`CoffeeMud/world`. To keep a pipeline hermetic give it its own cache and save
folders, and note that the launcher builds the content cache from the world
files on its first run:

```sh
W="$(mktemp -d)"
mkdir -p "$W/db" && cp CoffeeMud/resources/fakedb/fakedb.schema "$W/db/"
CoffeeMud/sud.sh CHECK=1 WORLDDIR="$PWD/mygame/world" \
    DBSERVICE="jdbc:fakedb:$W/db" SAVEDIR="$W/saves"
```

Static checks do not prove narrative reachability, combat balance, arbitrary
JavaScript branches or the behavior of every randomly generated combination.
Pair them with playtest files.

### A world's release gate

A reasonable gate for a world kept in git (adapt the paths):

```sh
#!/bin/sh
# gate.sh: run before tagging a release
set -e
W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT
WORLD="$PWD/mygame/world"

# 1. static lint of the whole world (exit 1 on errors)
mkdir -p "$W/db" && cp CoffeeMud/resources/fakedb/fakedb.schema "$W/db/"
CoffeeMud/sud.sh CHECK=1 WORLDDIR="$WORLD" DBSERVICE="jdbc:fakedb:$W/db" SAVEDIR="$W/saves"

# 2. the world's own play tests (exit 1 on failure)
WORLD="$WORLD" python3 tools/playtest.py "$WORLD/tests"
```

Add your own checks to the same script. `WORLD CHECK ONLY=...` is not available
from the command line, so extra lints (PROPCHECK ALL, SCRIPTCHECK ALL,
QUESTCHECK ALL) belong in a `.js` test that calls `ScriptCheck.checkAll()` and
`PropCheck.checkAll()` (the museum example does this for one host), or in a
`.play` file with `#builder` that types the command and `#reject`s a failure
text, such as `#reject problems:` after `scriptcheck all`. Note that WORLD
CHECK already runs SCRIPTCHECK and PROPCHECK as its SCRIPTS and PROPS parts.

A commit hook can run just step 1; it takes the time of a game start.

## Save, migration and recovery tests

A world changes after players have saves. Test that the saves still work
before you ship the change.

Test a **fresh process** restore at meaningful points: pending dialogue or
narration; a delayed action; active combat or boss phase; an assisted craft;
a journey, vehicle or instance; a quest deadline; and a changed room. Do not
count a same-process field check as proof that startup rebinding works. The
repository's own save tests do exactly this: a game is saved, the process
ends, and a new process restores.

### Inspect and compare saves

| Tool | Use |
| --- | --- |
| `SAVES` | Lists your saves, newest first; marks a save `(this game)`, `[dev]`, `[cheat]` and `(older world)` (made with a different version of the world) |
| `SAVES CHECK` | Lists what a save made now would not keep (for example summoned mobs with no one to follow, things in space) |
| `SAVES INFO name` | What a save holds: who, where, when, the party, quests running, variables and the world it was made with |
| `SAVES DIFF a b` | What differs between two saves: where the character is, hit points, what they carry, variables, quests and what is in each room |
| `SAVES COPY a b`, `SAVES DELETE name` | Copy or delete (delete asks first) |
| `CHECKPOINT`, `ROLLBACK` | Three rotating checkpoint saves; ROLLBACK returns to the newest after asking |
| `python3 tools/saveinspect.py SAVEDIR` | Prints a save outside the game; with `--var HOST NAME VALUE`, `--hp N` or `--room "Area#12"` it edits the save (keeping `world.xml.bak` and updating the checksum in `.complete`); the game must not be running on that save |
| `python3 tools/worlddiff.py OLD NEW` | Compares two world folders object by object (also `--git REV [WORLD]` against a git revision): rooms by ID, mobs and items by name, other files as added, removed or changed |
| `WORLD DIFF file` | A world file against what builder `SAVE` would write |

`SAVEMODE`, the autosave policy and no-save properties can restrict where and
when players may save, and save hooks may refuse; test with the mode the
release ships with. See [chapter 31](31-saves-and-party.md).

### World sums and what a restore does when the world changed

Every save's `header.properties` records `worldsum`, a checksum of the world
files the game had loaded when it saved, and `areasums`, one checksum per area
file. The sum looks like `3-120-ab12cd34` (sum kind, number of files, CRC of
the file names and per-file sums; dev areas and separately versioned module
files are left out). Starting from a save, the game compares its `worldsum`
with the world it booted. If they differ the player is told, when they come
into the world, what changed:

```text
This save was made with another version of the world:
  The world has changed since this game was saved.
  Changed area file: castle.cmare
  New area file: crypt.cmare
  Area file gone: oldtown.cmare
RESTORE name FRESH=CHANGED takes the changed areas' rooms as they are now.
```

Sums of an older kind (a save from an earlier version of the game) cannot be
compared whole; only their area files are, and the world is reported changed
only if one of those differs.

A restored game boots the world as it now stands, then applies the saved live
state over it. So room definitions that the world files own (titles,
descriptions, exits, doors as built) follow the new world, while what the
save recorded about each room's contents does not: its mobs and where they are
hurt or dead, the items on the floor, effects with time left, open and locked
doors, weather, quest state, script variables and the character. Therefore a
mob you add to a room does not appear in an old save, and a mob you remove
comes back with the save. A room you delete is reported (`Room X is gone, with
N mobs and M items saved there`) and its saved contents are dropped.

### FRESH=: take changed areas as they are now

`FRESH` tells a restore to take some areas' rooms (their mobs and items) from
the world as it is now rather than from the save. Everything about the
character, party, quests and variables still comes from the save.

```text
RESTORE before-the-dragon FRESH=CHANGED
RESTORE before-the-dragon FRESH=ALL
RESTORE before-the-dragon FRESH=Castle,Crypt
```

```sh
CoffeeMud/sud.sh RESTORE=before-the-dragon FRESH=CHANGED
```

`CHANGED` is the areas whose files changed (or are new) since the save;
`ALL` is every area; a comma list names areas by area name or area file. Use
it when you changed an area's population, and accept that anything the player
did in that area (a looted chest, a dead boss) is lost there. It is a
migration tool, not a promise of compatibility for arbitrary breaking
changes: quest steps that no longer exist, removed item classes or renamed
IDs are not repaired.

### Migrating and keeping IDs stable

- Keep room IDs, quest names, named quest steps, authored clue and waypoint
  IDs and catalog names stable across releases; renaming a quest name or a
  step strands saves in it. Use the supported alias/legacy-key mechanisms for
  authored IDs described in [state and persistence](state-and-persistence.md).
- Prefer adding to a world over reshaping it. A new area file is reported as
  `New area file` on restore; rooms the save knows nothing about keep the
  contents the world gives them.
- When a change must be a breaking one, list it in the release notes together
  with the save advice: "restore with `FRESH=Castle`" or "start a new game".
- To migrate a save by hand, `tools/saveinspect.py` can set a variable, hit
  points or room outside the game. Take a copy first.
- Keep the engine revision the world was tested with next to the world. A
  world is built for the classes, properties and help of that engine.

### Save format and recovery

New save generations carry completeness hashes and a previous complete
generation is retained. Startup can recover an interrupted publication and
preserve damaged generations. Saves have a format `version` (1, or 2 when a
save also holds optional-module state); legacy version-1 saves remain
readable and an unsupported version or world-snapshot version is rejected
before it replaces live state, with a message such as `Unsupported save
format 3 in 'x'. This engine supports versions 1 and 2.` Directory sync is best
effort where the platform lacks support. See the
[reliability notes](../../CoffeeMud/guides/ReviewReliability.md).

## Release workflow

A release is a world folder plus an engine revision that plays it, a way to
launch it, and the notes that let a player or another author use it.

### What to package

A runnable CoffeeSUD deployment is the engine folder (`CoffeeMud/`: classes,
`lib/js.jar`, `resources`, `coffeemud.ini`, the launch scripts) and a world
folder. You can ship the world inside `CoffeeMud/world` or elsewhere and point
`WORLDDIR` at it. Include:

| Include | Why |
| --- | --- |
| The world folder: `areas/*.cmare`, `quests/`, `world.ini`, `catalog.cmare`, `custom.cmare`, `scripts/`, `js/`, `resources/` overlay (script libraries, text files, recipes, chat files, dictionaries), `kits/`, `text/`, and any other asset folders the world uses | The definition of a new game |
| `tests/` (optional) | Lets others run the world's tests; harmless in play, but running them needs the developer tools |
| A `coffeemud.ini` (or `world.ini` keys) with the settings the world was tested with: `TIMEMODE`, `SAVEMODE`, `DIFFICULTY`, `MODULES` and so on | The world is tested with these |
| The engine revision: build output, or source plus `build.sh`/`build.bat` | A world is built for one engine |
| Launch instructions, a recovery location and licenses (the repository's `LICENSE` and `NOTICE`, and your world's own) | So a clean machine can run it |

Exclude:

| Exclude | Why |
| --- | --- |
| `saves/` and any `SAVEDIR` | Players' campaigns; also your own test saves |
| `resources/fakedb/*` except `fakedb.schema` | The content cache is rebuilt from the world on first start; it also holds the builder's own characters |
| `mud*.log`, `SQLErrors.que`, `logs/` | Logs |
| `*.builder` files anywhere in the world folder, and `world/.backup/` | Unreconciled builder copies and backups. Review each `.builder` file rather than blindly forcing a save, then delete it |
| `world/dev/` | Areas for building and testing only; a normal game never loads them, but there is no reason to distribute them |
| `notes/playtest.txt` and any `recordings/` | Your playtest notes and typed input |
| `resources/_fixes`, `resources/quests/_smoke`, scratch `_worldtest` | Test scaffolding |
| Temporary `PLAYTEST GAME` folders (`sud-playtest-*`) | Disposable campaigns |

A `.gitignore` for a world repository can reuse the repository's own
(`*.class`, saves, logs, the fakedb cache except its schema, test scaffolding).

### Steps

1. **Settle the world.** Stop watchers (`DEVWATCH OFF`). Review `LIST UNSAVED`,
   `WORLD STATUS`, `WORLD DIFF` and `WORLD PROBLEMS`. Reconcile external edits
   before exporting; review any `.builder` files rather than blindly forcing a
   save. See [chapter 2](02-world-workflow.md).
2. **Save and commit authored content.** `SAVE` in builder mode writes the
   world files; commit them. Exclude scratch databases, temporary saves,
   dev-only content, logs and test artifacts. Keep the required public
   resources, script libraries and referenced text files.
3. **Run every check against a clean copy** of what you will ship: `CHECK=1`,
   the world's playtests, plus recipe, quest, generator and language checks
   (`VALIDATE RECIPES`, `QUESTCHECK ALL`, `GENERATE CHECK` on each file,
   `ORGCHECK`, `LANGUAGE COVERAGE`), `PUZZLE CHECK`, `CHECKTEXT WORLD` and
   `PROGRESSION XP` (does the content reach the levels you intend?). Play NEW
   without builder or DEV privileges, with `GOD` off and `PLAYTEST` off.
4. **Verify supported older saves** with IDs, aliases and explicit migration
   rules (see above). Preserve accepted world versions when campaigns need
   exact assets. Record intended incompatibilities in the release notes.
5. **Version it.** The world has no version field of its own; its identity to
   saves is its world sum. Tag the commit (`git tag v1.2`), put the version in
   `GAMENAME`, your release notes and, if you like, a `text/` file, and record
   which engine revision it needs. `python3 tools/worlddiff.py --git v1.1
   CoffeeMud/world` gives the change list between releases.
6. **Package** the world, the compatible engine revision, runtime
   requirements (a JDK; Python 3 only for tests), licenses, launch
   instructions and a recovery location. Archive from a clean export of the
   tag (`git archive`), not from your working folder.
7. **Test a clean installation:** unpack on a machine (or folder) that has
   never run the game, start a new game as an ordinary player, play the main
   path, save, quit, start again, restore. Send the package only after this.

### Launching a packaged world

```sh
CoffeeMud/sud.sh WORLDDIR=/path/to/release/world SAVEDIR=/path/to/saves
```

On Windows use `CoffeeMud\sud.bat` with the same options. `WORLDDIR` is
resolved before `world.ini` is read. Keep `DBSERVICE` and `SAVEDIR` separate
from other launchers: an ownership lock prevents two processes writing the
same `SAVEDIR`. The [installing and running](18-installing-and-running.md)
chapter lists launch flags.

### Updating a world for existing players

Players keep their `saves/`; give them the new world folder and the release
notes. The next restore reports `This save was made with another version of the
world` and offers `FRESH=`. If you only changed text, scripts, JavaScript and
resources, no `FRESH` is needed. If you moved, removed or retuned mobs and
items, say which areas to take fresh. If an update is not compatible with old
saves, say so plainly and say how to start over.

## Repository test suites

This part is for engine developers working on the repository. World authors do
not need it.

All suites are shell or Python wrappers under `tools/`, run from the repository
root, and each builds first with `CoffeeMud/build.sh` and plays in scratch
worlds, databases and saves. They need a JDK and Python 3. The suites are heavy
(a game start per group); do not run several at once on a small machine, do not
run two fixes harnesses against the same checkout (they share
`resources/_fixes`), and do not compile concurrently into one class tree. An
isolated full checkout avoids these shared artifacts.

### tools/check.sh

The check for a commit. It builds once, then runs, in order:

1. `tools/creator_docs.py --check`: the handbook's generated references and
   every relative Markdown link, against the sources;
2. `tools/composition-tests.sh`, `tools/combat-rules-tests.sh` and
   `tools/item-modifier-tests.sh`: the Java rule suites (below);
3. `tools/fixes.sh` and `tools/smoke.sh` side by side;
4. with `ENGINE=1`, `tools/engine-tests.sh` as well.

```sh
tools/check.sh                       # docs, rule suites, fixes and smoke (10 to 30 minutes)
ENGINE=1 tools/check.sh              # also the engine's own TEST ALL suite (a quarter hour more)
CHECKS_SERIAL=1 FIXES_JOBS=2 ENGINE=1 tools/check.sh   # small CI machines
```

It prints each suite's result (`fixes: PASS (n checks)`), the time taken and the
folder holding each suite's full output (`sudcheck.XXXXXX` under `TMPDIR`),
and exits 1 if anything failed. `CHECKS_SERIAL=1` runs the suites one after
another instead of side by side; `FIXES_JOBS=n` limits how many fixes groups
run at once (default 9, the parallel run). Use `ENGINE=1` at the end of a step
and for changes to the engine's core. Documentation-only changes can run
`python3 tools/creator_docs.py --check` alone.

### tools/fixes.sh and fixtures

`tools/fixes.sh` is the main behavior suite. Each file `tools/fixtures/fixes/NAME.js`
is a JavaScript test. The harness puts `prelude.js` in front of it, and all
the files run with `JRUN` in one builder-mode game on a scratch copy of the
world (fixed seed 42, turn-based time). Then "played" groups run alongside, each
with its own world, database and saves, for what needs real play (save and
restore, travel, presentation, language, review, modules, composition, combat,
the Ember Trial sample, item crafting).

```sh
tools/fixes.sh                  # every JavaScript test and every played group
tools/fixes.sh dt1-vars dt15-assert   # only these test files (without .js)
tools/fixes.sh saves            # only the played save tests (saves1 to saves5 in parallel)
tools/fixes.sh saves3           # one group: st1 st6 pr5 s10 travel presentation language
                                # review remaining modules composition combat trial
                                # itemcraft saves saves1..saves5
KEEP=1 tools/fixes.sh           # keep the scratch folder and print its path
```

It prints `  ok    FILE/NAME` per check, `  FAIL ...` for failures, and `PASS`
or `FAILED.  Output kept in ...`; the exit status is 1 on any failure. It also
fails if a test file does not reach its `done()` line (a thrown error ends a
file early), or if the transcript shows `JavaScript error` or `JS error in
JRUN`.

#### Writing a fixture

Copy the shape of an existing `dtN-*.js` file. Every file:

- starts with `FIXFILE = "name";`, the file name without `.js` (the harness
  looks for the line `FIXTEST done name`, so the name must match);
- uses the prelude's helpers (below) and reports with `check(name, cond, why)`;
- ends with `done();`.

The prelude (`tools/fixtures/fixes/prelude.js`) defines these globals:

| Helper | Meaning |
| --- | --- |
| `M` | The builder's mob (`mob()`) |
| `CM`, `CMClass`, `CMLib`, `CMath`, `CMProps`, `CMSecurity`, `WorldFiles`, `CMMsg`, `Tickable` | Shortcuts to the engine packages and classes |
| `ok(name)`, `fail(name, why)`, `check(name, cond, why)`, `done()` | Report: `FIXTEST ok FILE/NAME` or `FIXTEST FAIL FILE/NAME: why`, and `FIXTEST done FILE` |
| `scratchRoom()` | A fresh room in the player's area, linked to nothing |
| `newMob(cls, room)` | A level-5 mob of that class brought to life in a room |
| `scriptOn(host, text)` | Adds a `DefaultScriptingEngine` with that script to a host |
| `scriptedMob(name, script)` | A GenMob in the player's room with a script that runs on its next tick (use `RAND_PROG 100`) |
| `tickMob(m)` | Ticks a mob once, as the game would |
| `runQueued(engine)` | Runs an engine's queued responses, as ticks would |
| `VARS`, `gvar(name)`, `clearVar(name)` | A scripting engine and readers for the global script variables of the host `FIXTEST` |
| `otherRoom()` | Another room of the map |
| `field(obj, name)` | Reads a private or inherited field |
| `call(obj, name, args)` / `callTyped(obj, cls, name, typeNames, args)` | Calls a private method by name (and parameter types) |

Typical pattern for testing a command's output, from `dt15-assert.js`:

```javascript
FIXFILE = "dt15-assert";
var R = M.location();
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixasserter");
var BS = CMClass.getCommon("FakeSession");     // a session that keeps what it is told
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
function out() { return "" + field(BS, "bout").toString(); }
function run(cmdName, words, who) {
    var L = new java.util.ArrayList();
    var w = words.split(" ");
    for (var i = 0; i < w.length; i++) L.add(w[i]);
    CMClass.getCommand(cmdName).execute(who || M, L, 0);
}
run("AssertCmd", "ASSERT HERE Nowhere#99", B);
check("here-fail", has(out(), "ASSERT FAIL: HERE Nowhere#99"), "HERE did not fail");
B.setSession(null);
B.destroy();
done();
```

Rules of thumb: clean up what you create (destroy mobs, clear variables,
delete quests) because all JavaScript tests share one game; test behavior
rather than the field you just set; give each check a distinct name; write a
file under `/resources/_fixes/` with `new CM.core.CMFile(path, M).saveText(...)`
when you need a script or quest file.

For tests that need real play (several processes, restore in a fresh process,
a different world), add a `group_*` function to `fixes.sh` using its `play`
helper (it types a script into `tools/sudplay.py` against its own world copy)
and its `expect OUTFILE TEXT` and `count OUTFILE TEXT N` assertions, and put
the group's name in the `case` list and the parallel list at the end of the
script. Data a played group needs goes in `tools/fixtures/fixes/setup/`
(`.js` files there get the prelude put in front of them and are copied to
`resources/_fixes/`).

### tools/smoke.sh

The single-player smoke test: scripted sessions through `sudplay.py` against
a scratch database and world. Sessions: create a character and play; restore
from the startup list; pause and resume; turn-based time and a fight one WAIT
at a time; the same `RANDOMSEED` rolling the same character; save, move and
restore during play; change the world and restore it; a second profile
character and a party (and its turns); a unique item across a save; a quest, a
follower, a lit torch and a wolf's rolled stats across a restore; editing a
world file by hand; and a builder rename on quit. It fails on missing text or
if play wrote to the world files. `KEEP=1` keeps the scratch folder.

### tools/storage-tests.sh

`tools/storage-tests.sh` compiles and runs `tools/SaveStorageTest.java`, an
isolated filesystem and process test of the save store (generations,
completeness hashes, recovery, locks) that never reads a real campaign save
folder and boots no engine. `tools/check.sh` does not run it; run it yourself
after changing the save store.

### The Java rule suites

Standalone Java programs, compiled by their wrapper with `javac` into a
temporary folder against the built classes, with no game boot:

| Wrapper | Class | Covers |
| --- | --- | --- |
| `tools/composition-tests.sh` | `CompositionTest.java` | Ability composition definitions and schema properties ([chapter 15](15-ability-composition.md)); reads `SUDDocs/creator/examples/ability-composition` |
| `tools/combat-rules-tests.sh` | `CombatRulesTest.java` | Golden combat results and conversion equivalence ([chapter 16](16-combat-profiles.md)); reads `SUDDocs/creator/examples/combat-profiles` |
| `tools/item-modifier-tests.sh` | `ItemModifierRulesTest.java` | Selective edits, protection, source identity and versioned contracts ([chapter 17](17-item-modifiers-crafting.md)) |
| `tools/storage-tests.sh` | `SaveStorageTest.java` | Save storage |

Each prints `ok <check>` per check and fails with an `AssertionError`. The
integration scripts `tools/combat-integration.py`, `composition-integration.py`,
`item-modifier-integration.py`, `modules-integration.py`, `remaining-integration.py`
and `review-integration.py` are the played counterparts, run as groups of
`fixes.sh`.

### tools/engine-tests.sh

Runs the engine's own `TEST ALL` suite through the console in a copy of the
game and compares each test's `PASS` or `FAIL` with `tools/baseline/test-all.txt`,
recorded on the unmodified server. It passes when the results match the
baseline, which includes tests that failed on the original: the recorded
failures are Spaceangles, Spaceinterception and Spaceinterception2. Keep that
baseline distinction in reports; it does not excuse a new failure.

### creator_docs.py --check

```sh
python3 tools/creator_docs.py --check
```

Verifies that the generated handbook files match the sources (tool reference
from the help files, feature index, tool permissions, world settings), that
every feature in `audit/project-feature-status.csv` is mapped to a chapter,
and that every local Markdown link and anchor in `SUDDocs/creator` resolves.
`tools/check.sh` runs it first. Edit the sources and run
`creator_docs.py --write` to regenerate; never hand-edit a generated file.

### Playtest and example tooling

`tools/playtest.py` (and `tools/playtest.sh`) is described under
[World tests](#world-tests). The creator examples have their own driver:
`python3 SUDDocs/creator/examples/check.py` builds the museum on isolated
copies, publishes it with builder `SAVE`, runs its `.play` and `.js` tests,
then checks a save in a fresh process (`KEEP=1` retains passing artifacts);
`SUDDocs/creator/examples/ember-trial/check.py` does the same for the Ember
Trial. `tools/sudplay.py SCRIPTFILE NAME=VALUE...` is the generic scripted-input
driver all of these use. A script line `#expect TEXT` waits until TEXT appears,
and `#sleep N` waits N seconds.

## Worked examples

The room, mob and script names below are illustrative; the commands and file
formats are real.

### Example 1: find out why a script does not work

The miller in `Dunmoor#4` should hand out a sack of flour once when a player
says "flour", and then say a different line. Players report that nothing
happens.

1. Lint the script and everything near it.

   ```text
   scriptcheck miller
   scriptcheck area
   ```

   The first reports `scripts/miller.script:14 unknown trigger SPEECHPROG`
   (the underscore is missing): a typo that makes the trigger never fire and
   that nothing else reports. Fix the file.
2. Reload the script into the running world and look again.

   ```text
   world reload script scripts/miller.script reset
   script list miller
   ```

   `SCRIPT LIST` now shows `1. SPEECH_PROG flour  (scripts/miller.script:14)`,
   so the engine sees the trigger.
3. Run the trigger without speaking, as a character who is not you, and watch
   what the script does.

   ```text
   devlog on warn module Scripting
   scripttrace miller
   script fire miller SPEECH flour AS Bob
   ```

   The trace prints each line run and each variable changed. If a line is
   wrong, SCRIPTERRORS and DEVLOG show it with its file and line.
4. A one-shot line never repeats. Check what state the script left and put it
   back so you can test again.

   ```text
   vars list * Bob MILLER_*
   vars clear Bob MILLER_GAVEFLOUR
   script reset miller ONCE
   ```
5. Turn the trace off and write the regression test (Example 2).

   ```text
   scripttrace off
   devlog off
   ```

### Example 2: a regression playtest for the start of the shipped world

The repository's own test, `CoffeeMud/world/tests/start.play`:

```text
# The start of the game: the first room, its book, and the way north.
#char Wanderer,Human,Apprentice
look
#expect Obvious exits
#assert HERE New Area#0
get book
#assert HAS book
north
#reject You can't go that way
south
#assert HERE New Area#0
```

Run it:

```sh
python3 tools/playtest.py CoffeeMud/world/tests/start.play
```

```text
PASS  CoffeeMud/world/tests/start.play (5 checks)
```

Extend it for your own quest. Suppose `#assert VAR` is used to check a quest
flag that the miller's script sets with `MPSETVAR $n MILLER_GAVEFLOUR 1`:

```text
# world/tests/miller.play: the miller gives flour once
#char Tester,Human,Apprentice
goto Dunmoor#4
say flour
#expect hands you a sack of flour
#assert HAS sack of flour
#assert VAR Tester MILLER_GAVEFLOUR = 1
say flour
#reject hands you a sack of flour
#expect already
```

Notes on this file: `goto` works because the runner's character holds the DEV
group; `#assert VAR` names the variable's host (the character `Tester`)
and the name in capitals; the second `say flour` shows the once-only
behavior by rejecting the first reply. To force the failure path while you
write it, change an `#expect` to text that is not there and confirm the run
prints `FAIL` with `expected '...'` and a transcript path; the repository's
`tools/fixtures/fixes/setup/dt15-fail.play` is a deliberate failing file for
that purpose. Run it before every release.

To produce the first draft by playing, start a DEV=1 game with
`RECORDINPUT=1`, or type `RECORD INPUT miller` in a DEV=1 or builder game,
play the scene, then `RECORD INPUT OFF` and copy
`saves/recordings/miller.play` into `world/tests/`.

### Example 3: set up a mid-game tester and a headless check

To test act 3 without replaying acts 1 and 2:

```text
# world/kits/act3-warrior.kit
race Dwarf
class Fighter
level 12
gold 500
room Crypt#1
item Longsword
item catalog:Mithril Axe
ability Skill_Dodge 75
faction Merchants 2500
var ACT 3
```

```text
testchar Tank KIT=act3-warrior PARTY
switch Tank
quests
```

`TESTCHAR` made the character with no creation questions and brought them into
the party; `SWITCH` plays them. Turn off the cheats you do not want
(`god all off`), fight the lich, and use `COMBATDEBUG ON` to see each roll.
When the area is right, add `#assert QUEST` or `#assert VAR` lines to a
`.play` and let CI repeat it. For the headless side, the release gate from
[A world's release gate](#a-worlds-release-gate) runs `CHECK=1` and the
playtests on a clean checkout and fails the build when either does.

### Example 4: ship a world update without losing saves

You tuned the sunken crypt (removed a trap, moved the lich) and added a new
area, and a player has a save from before.

1. `python3 tools/worlddiff.py --git v1.0 CoffeeMud/world` lists what
   changed: `Crypt#3: mob 'a lich' ...`, `Harbor#0: room added`.
2. In a copy of the old release, `RESTORE` the player's save with the new
   world, without FRESH, and read the message: it names `Changed area file:
   crypt.cmare` and `New area file: harbor.cmare`. Walk the crypt: the lich is
   where the save put him, because mobs follow the save.
3. Restore again with `RESTORE slot FRESH=Crypt` (or `FRESH=CHANGED`): the
   crypt's mobs and items are the world's now. The character, quests and
   variables are unchanged.
4. Write that into the release notes: "Existing saves: restore with
   `FRESH=Crypt` to get the new lich; otherwise the old crypt contents remain."
5. Tag, package and test a clean installation as in the
   [release workflow](#release-workflow).

## Persistence notes

| What | Where it lives | Reload or restart effect |
| --- | --- | --- |
| Playtest files, kits, notes' target | `world/tests`, `world/kits` (in the world folder); notes in `notes/playtest.txt` outside the world and saves | Tests and kits are world files, read when used. Notes survive a world reload |
| `PLAYTEST ON`'s mortal mode | A `Prop_Playtest` effect on the character, with the room to return to | An effect on the character (and on party members you test); `PLAYTEST OFF` removes it. `PLAYTEST GAME` puts it on the restored character in its disposable campaign |
| GOD cheats | `Prop_DevCheats` effect kept with the character | Stay until `GOD RESET` or `GOD ALL OFF`. A game started without DEV=1 takes the DEV security group away again |
| DEVLOG settings | Builder mode: beside the database. Otherwise the `DEVLOG=` ini key | Per game |
| VARS, quest variables | The save's script-variable stores | Saved with the campaign; `VARS SET` writes live state |
| SCRIPT DISABLE, SCRIPTTRACE, SCRIPTPROF, COMBATDEBUG, TRACEB | Memory only | Gone at restart; not saved |
| SCRIPTERRORS ring (last 200), DEVLOG ring (last 500) | Memory | Gone at restart |
| TIMESCALE | Memory; `TIMESCALE=` ini key at start | The rate is not part of a save |
| Sandbox | An unsavable area | Never in world files, database or saves |
| World problems | `worldproblems.txt` beside the content database | Persists across restarts until the file is fixed and loaded whole |
| Recorded input | `saves/recordings/*.play` (or the path given) | Files you keep |
| Saves' world sums | `header.properties` (`worldsum`, `areasums`) | Compared at every restore |
| Test scratch campaigns | Temporary folders (`sudplaytest.*`, `sud-playtest-*`) | Removed after a pass (unless `KEEP=1`); kept after a fail |

TIME SET, WEATHER SET, VARS SET, PUZZLE SET and SCRIPT FIRE change live state,
which a campaign's next save records (the clock, weather, script variables
and the rest are in the save). In builder mode there are no campaign saves, so
review the result of any state-changing tool before you `SAVE`; `WORLD DIFF`
shows what `SAVE` would write. See
[state and persistence](state-and-persistence.md) and
[chapter 31](31-saves-and-party.md).

## Troubleshooting and gotchas

- **A command is "not recognized".** Most tools are gated by mode or security
  flag (see the [tool index](#tool-index)). In a plain DEV=1 game
  `PREVIEW`, `CHECKTEXT`, `SANDBOX` and `DEVWATCH` need flags the DEV group
  lacks; use builder mode or add the flags to `GROUP_DEV`.
- **`CHECK=1` finds no problem a builder session shows.** `CHECK=1` plays a
  new game and does not load `world/dev`; WORLD PROBLEMS is builder-mode
  only. Run both.
- **A `.play` test passes in the editor but fails in CI.** The runner's
  character has the DEV group and the default seed; door and permission tests
  need a normal game too. A world that adds creation steps changes the
  answers the runner types.
- **`#expect` fails but the text is on screen.** Matching is a case-sensitive
  substring over the step's output only (everything between the previous mark
  and this check). Text printed by a later step, or colour codes between the
  words, does not match. Put the `#expect` after the command that prints it.
- **A menu answer is eaten.** Group a menu command and its answer before the
  first expectation.
- **PLAYTEST RUN / WORLD TEST say the runner is missing.** They need
  `../tools/playtest.py` relative to the folder the game was launched in
  (`CoffeeMud/`), so they only work in a developer checkout, and Python 3
  (`-Dsud.python=...` selects the executable).
- **PLAYTEST GAME will not start.** It refuses when the world did not load
  whole (see WORLD PROBLEMS and WORLD STATUS) or no builder database is
  available; it tells you why rather than showing a game error.
- **`SCRIPT FIRE` runs nothing.** Name the trigger exactly (`SPEECH_PROG` or
  `SPEECH`) and, for word triggers, give a word the trigger answers to. `SCRIPT
  LIST` shows which are `[off]` or `[done]`; `SCRIPT RESET target ONCE`
  re-arms `ONCE_PROG`s.
- **SCRIPTCHECK flags a valid `LOAD=world:...` path** or **PROPCHECK flags a
  valid dynamic key**: known tool gaps (see above), not runtime failures.
- **A restored save shows the old mobs.** Mobs follow the save; use `FRESH=`.
- **`#restore` fails in the runner.** Each run has an empty save folder;
  see the note in [The .play file format](#the-play-file-format).
- **Concurrent fixes runs corrupt each other.** `resources/_fixes` is shared;
  run one harness per checkout.
- **DEVLOG WINDOW opens nothing.** There is no display; DEVLOG ON and LAST use
  the terminal.
- **A tool changed the world.** SCRIPT FIRE, SCRIPT CALL, PUZZLE SET/SIGNAL,
  LAW PARDON/ACCUSE/SET, WEATHER SET, TIME SET, VARS SET/CLEAR and MODIFY QUEST
  change live state. Use `PLAYTEST GAME` or a throwaway builder copy when that
  matters; `UNDO` and `WORLD RELOAD` are covered in
  [chapter 2](02-world-workflow.md).
- **Known engine baseline failures.** `ENGINE=1 tools/check.sh` is expected to
  match `tools/baseline/test-all.txt`; Spaceangles, Spaceinterception and
  Spaceinterception2 fail there as on the original server.

Deliberate deferrals (exact deterministic replay with `DETERMINISTIC=1`, a
`SIMFIGHT` party-versus-encounter simulator, a generic puzzle controller, and
the native-Java timeout boundary) are listed in the
[developer tools audit](../../audit/developer-tools-audit.md).

## See also

- [World files, builder mode and the editing workflow](02-world-workflow.md):
  SAVE, WORLD STATUS/DIFF/RELOAD/BACKUPS, UNDO, the world folder.
- [Installing and running](18-installing-and-running.md): launch flags, modes,
  folders, logs.
- [Configuration and administration](19-configuration-and-administration.md):
  `coffeemud.ini` keys, security groups, GOD and developer powers.
- [MOBPROG scripting](04-mobprog.md), [triggers](26-mobprog-triggers.md),
  [commands](27-mobprog-commands.md) and [functions](28-mobprog-functions.md):
  what the script tools exercise.
- [JavaScript](05-javascript.md): `JRUN`, `cm`, and world JS tests.
- [Quests](08-quests.md), [combat](29-combat-and-encounters.md),
  [society and law](09-society.md), [world simulation](10-world-simulation.md).
- [Saves and party](31-saves-and-party.md) and
  [state and persistence](state-and-persistence.md): what a save keeps.
- [Engine programming](32-engine-programming.md) and
  [engine internals](33-engine-internals.md): for the suites' subjects.
- [Examples](examples/README.md): a playable world with `.play` and `.js` tests.
- [Tool reference](tool-reference.md): the shipped help for every command.
