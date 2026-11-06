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
