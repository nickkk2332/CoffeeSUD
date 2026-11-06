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
| Vessels, instances | `VESSEL STATUS`, `LIST SHIPS/INSTANCES/UNSAVED` | Builder/DEV or CMDAREAS | [VESSEL STATUS and LIST INSTANCES](#vessel-status-and-list-instances) |
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
area (with `AREA` and no name, your own area); `LOG` also writes the report to
the log; `ONLY=` takes one or more part names separated by commas.

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

An unknown name in `ONLY=` is refused and the valid names are listed.
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
or carried, `ROOM`, `name@room`, `AREA` (your area) or `ALL` (everything in the
world, including quests' scripts). The result is `SCRIPTCHECK x: no problems
found.` or `SCRIPTCHECK x: N problems:` followed by one `file:line message`
line each.

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

### LAW TEST and the other LAW tools

See [LAW](#law) under runtime inspection; `LAW TEST` is the static explainer.

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
script fire innkeeper SPEECH room AS me
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
`progs/elder.script:212 unknown command MPECHOT`. A number limits it (20 by
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
Without `SELL` it is what the shop asks; with `SELL` what it pays. The item is
looked for in the shop's stock, then the character's and your own inventory,
then the room. With no `AT`, the first shopkeeper here that is not a player;
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
sharing. A recording plus a fixed seed is not an exact deterministic session
replay; that dedicated feature (`DETERMINISTIC=1`) remains deferred.

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

Access: builder mode, DEV=1 or CMDAREAS. `VESSEL STATUS` shows a vessel as it
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

### BUILDER and DEV characters in tests

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

In the game (builder mode or DEV=1):

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
