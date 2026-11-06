# MOBPROG scripting guide

[Handbook](README.md) · [Triggers](26-mobprog-triggers.md) · [Commands](27-mobprog-commands.md)
· [Functions and `$` codes](28-mobprog-functions.md) · [JavaScript](05-javascript.md)
· [CoffeeSUD scripting supplement (HTML)](../../CoffeeMud/guides/ScriptingSUD.html)
· [Inherited CoffeeMud scripting manual (HTML)](../../CoffeeMud/guides/Scriptable.html)

MOBPROG is CoffeeSUD's built-in scripting language: small event programs that
make a mob, item, room, exit or area react to what happens around it. It is the
tool for most "when the player does X, the world does Y" content: a guard who
wants a password, a lever that opens a gate, a shopkeeper who remembers a
favour, a bell that rings at dawn, a quest giver who checks what you carry.
This chapter is the guide: how a script is attached, how it runs, how its
control flow and variables work, how to debug it, and a set of worked examples.
The complete references are separate chapters: every trigger is in
[chapter 26](26-mobprog-triggers.md), every command in
[chapter 27](27-mobprog-commands.md), every condition function, value function
and `$` code in [chapter 28](28-mobprog-functions.md). This chapter links to
their entries instead of repeating them.

It serves world creators who write scripts in the editor or in `.script` files,
and, in the execution-model and debugging sections, scripters and engine
developers who need to know exactly when and on what a script runs. Everything
here was checked against `DefaultScriptingEngine`, `Scriptable`, `ScriptEvents`,
`ScriptCheck`, the `SCRIPT*` commands, the shipped help and the test fixtures
under `tools/fixtures/fixes/` (those fixtures are executed by the test suite, so
the syntax they use is known to work).

## In this chapter

- [What MOBPROG is, and when to use something else](#what-mobprog-is-and-when-to-use-something-else)
- [Vocabulary](#vocabulary)
- [Attaching scripts to things](#attaching-scripts-to-things)
  ([Scriptable](#the-scriptable-behavior) ·
  [inline scripts](#inline-scripts) ·
  [LOAD= files](#load-files) ·
  [LOAD arguments](#load-arguments-and-arg) ·
  [world scripts](#world-scripts-the-world-host) ·
  [editing](#editing-scripts-in-game) ·
  [reloading](#reloading-after-an-edit))
- [Program structure](#program-structure)
- [The execution model](#the-execution-model)
  ([hosts](#hosts-and-the-stand-in-mob) ·
  [events and queues](#from-event-to-running-program) ·
  [who is free to react](#when-a-host-is-too-busy-to-react) ·
  [engine switches](#per-script-engine-switches) ·
  [what runs where](#what-runs-on-which-object))
- [Control flow](#control-flow)
- [Variables and state](#state-and-scope)
  ([the codes](#the-codes-in-text) ·
  [script variables](#script-variables-mpsetvar) ·
  [scopes](#variable-scopes) ·
  [other kinds of state](#tattoos-quest-state-and-the-javascript-store) ·
  [what a save keeps](#what-a-save-keeps))
- [Four grammars, not one](#four-grammars-not-one)
- [Functions, signals, alarms and waiting](#calls-signals-and-pauses)
- [Dialogue input: menus and prompts](#dialogue-input)
- [Party and language awareness](#party-and-language-awareness)
- [What CoffeeSUD adds to the language](#what-coffeesud-adds-to-the-language)
- [Pick the right event](#pick-the-right-event)
- [Debugging](#debugging)
- [Performance and safety practices](#performance-and-safety-practices)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## What MOBPROG is, and when to use something else

A MOBPROG script is a list of *programs*. Each program starts with a *trigger
line* naming an event (`SPEECH_PROG hello`), runs a list of commands when that
event happens to its host, and ends with `~`. The language is deliberately
small: `IF`/`ELSE`, `SWITCH`, `FOR`, `WHILE`, variables kept by name, about 150
`MP...` commands that change the world, and about 300 functions that ask about
it. Anything that is not a script word is run as an ordinary game command by the
script's mob (`say`, `emote`, `give`, `open`).

Pick the tool that fits the job:

| You want | Best tool | Why |
| --- | --- | --- |
| A fixed behaviour with a few options (patrol, guard a door, sleep, remember a crime) | a Behavior or Property from [chapter 24](24-behaviors-and-masks.md) or [23](23-properties-reference.md) | No code to maintain; tested, saved and shown by `STAT`. |
| An NPC conversation with menus, topics and barks | the dialogue tools of [chapter 6](06-npcs-dialogue.md) first, MOBPROG for the glue | TALK, MudChat, Sounder, Scene and Schedule need no script at all. |
| Reacting to one event with a few steps, conditions and saved flags | **MOBPROG** | Compact, readable by builders, checked by `SCRIPTCHECK`, runs inside the game thread, saved with the world. |
| A puzzle across several objects (lever, door, altar) | **MOBPROG** with `MPSIGNAL` and `MPDOOR` | Signals and per-object state are built in. |
| Timed events, countdowns, cut-scenes | **MOBPROG** with `MPWAIT`, `MPALARM`, `TIME_PROG`, `EVERY_PROG` | Their queues are saved. |
| Real data structures, loops over the world, string and JSON handling, calling Java classes, anything that must run on a schedule independent of any object | **JavaScript** ([chapter 5](05-javascript.md)) | A full language; world JS, hooks and `cm.store()`. A MOBPROG can embed a `<SCRIPT>` block when it needs a little. |
| A new kind of object, a new combat rule, a new command with Java-level access | **Java** or a JS-defined class ([chapter 32](32-engine-programming.md)) | Scripts cannot add new `Behavior`, `Ability` or `Command` kinds. |

Two rules of thumb. First, if a MOBPROG grows past a screenful, split it into
`FUNCTION_PROG` pieces in a file, or move the logic to JavaScript and keep the
MOBPROG as the thin trigger layer. Second, scripts that only make a *property*
of a thing (it blocks entry, it is locked by a password, it changes state when
pulled) are usually already a property: check [chapter 23](23-properties-reference.md)
before writing one.

## Vocabulary

| Term | Meaning |
| --- | --- |
| **Host** | The object a script is attached to: a mob, item, room, exit or area. The script runs *for* it and `$q` is its name. |
| **Program** | One trigger line plus its commands, ended by `~`. A script holds many programs. |
| **Trigger** | The first line of a program, always a `..._PROG` word and its parameters. It says which event runs the program. |
| **Event** | Something that happens in the game that a trigger listens for: a message (speech, entry, a gift), a clock tick, a game event (save, new game). |
| **Actor** (`$n`) | The creature that caused the event. Not necessarily a player: use `ISPC($n)` to tell. |
| **Target** (`$t`), **items** (`$o`, `$p`) | The other participants, where the trigger provides them. |
| **`$i`** | The mob the script acts as. For a mob host it is the mob. For any other host it is a *stand-in mob* (see below). |
| **Command** | A line that changes the world: `MPECHO`, `MPSETVAR`, `MPTRANSFER`. |
| **Function** | A query: the condition form `HASTATTOO($n KEY)` inside `IF`, or the value form `$%LEVEL($n)%` inside text. |
| **Variable** | A named value kept by the engine (`MPSETVAR`, `VAR`), not a local in the programming-language sense. |
| **Tick** | The game's basic time step (`TICKTIME=4000` ms in `coffeemud.ini`). In turn-based time a tick passes when the party acts. |

## Attaching scripts to things

### The Scriptable behavior

A script is attached with the **Scriptable** behavior. Its single parameter is
the script text itself, or `LOAD=` references to files, or both.
[Source](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Scriptable.java) ·
help: `AHELP BEHAVIOR_SCRIPTABLE`.

To attach a script to a mob, item or room in the editor:

1. Stand where the host is (or carry it). `MODIFY MOB the guard` (or `MODIFY ITEM`,
   `MODIFY ROOM`).
2. In the property list, choose the *Behaviors* line and answer the prompt with
   `Scriptable`. For this behavior the editor opens the script editor instead
   of a one-line parameter prompt (see [editing scripts](#editing-scripts-in-game)).
3. Enter the script, or just `LOAD=progs/guard.script`, and save.
4. `SCRIPTCHECK guard` to lint it, then test with `SCRIPT FIRE` (see
   [debugging](#debugging)).
5. `SAVE ROOM` (mobs and items) or the save that applies to your world so the
   change reaches the world files; see [chapter 2](02-world-workflow.md).

The help names the behavior's targets as mobs, items and rooms. The script
engine itself also hosts scripts on exits and areas (the `SCRIPT` and
`SCRIPTCHECK` tools take `AREA`, and `MPSIGNAL` can address an exit), and
the world files store scripts that were added to rooms and areas. Test an exit
or area host before relying on it: the editor's behavior list highlights the
behaviors that fit the thing being edited, and Scriptable is highlighted for
mobs, items and rooms.

A thing may carry more than one Scriptable behavior; each is a separate engine
with its own queue and state. Engines are also attached without the behavior
by quests (`MPSCRIPT`, a quest's `SET SCRIPT`), by the world host
([below](#world-scripts-the-world-host)) and by JavaScript. `EDIT SCRIPT`,
`SCRIPT LIST` and `SCRIPTCHECK` see both kinds.

### Inline scripts

The whole script can be the behavior's parameter. Because the parameter is a
single text field, lines are separated by `;` instead of line breaks:

```text
GREET_PROG 100;if ispc($n);say Welcome, $n.;endif;~
```

Every command line, and the trigger line, must end with `;` or a line break, and
every program with `~`. Inline scripts are good for one- or two-line reactions.
Anything longer belongs in a file, where each command has its own line and the
file and line numbers in error messages point at something you can open.

Escapes (all four matter inside text): write `\;`, `\~`, `\<`, `\#` and `\\` to
put a literal `;`, `~`, `<`, `#` or `\` in a command. Apostrophes in inline
scripts are stored as backticks (`` ` ``) and turned back when the script is
parsed; you will see the backticks if you read the raw parameter in a world
file.

### LOAD= files

`LOAD=path` pulls a file into the script. Scripts in files use real line breaks
(a `;` also works) and are much easier to read, review and share:

```text
LOAD=progs/guard.script
```

Rules:

- The path is a *resource* path, relative to the `resources/` folder. The usual
  place for your own scripts is `world/resources/progs/name.script` and the
  parameter is `LOAD=progs/name.script`. The world's `resources/` overlay is
  searched before the stock `resources/` (see [chapter 2](02-world-workflow.md));
  stock scripts live in `CoffeeMud/resources/progs/`.
- `LOAD=` must be at the very start of the parameter or follow a `;`, a line
  break or a `~`. Several files, and inline programs, may be mixed:
  `LOAD=progs/a.script~LOAD=progs/b.script~RAND_PROG 5;...;~`.
- A file may itself contain `LOAD=` lines, up to 10 levels deep.
- `LOAD=world:scripts/name.script` reads a file of the *world folder*
  (`world/scripts/`) instead of `resources/`. Mind the
  [world host](#world-scripts-the-world-host) before using that folder for
  per-NPC scripts.
- Everything loaded is parsed once and cached; two hundred guards that load the
  same file share one parsed copy.
- A missing file is logged as `LOAD ... NOREAD` and the host simply has no
  triggers from it. `SCRIPTCHECK` reports it (but see the
  [checker limitations](#what-scriptcheck-does-not-do)).

For a file-backed host the Scriptable parameter stays tiny (`LOAD=progs/x.script`)
and the world file is easy to diff. A file ends with a program's `~`; a trailing
blank line is harmless. Lines whose first non-blank character is `#` are
comments.

A file can also end with an XML tail holding named mobs and items for
`MPMLOAD`/`MPOLOAD ... FROMFILE`; the tail starts at the XML boundary the parser
looks for (`<?xml version="1.0"?>` followed by `<FILE><NAME>...</NAME><DATA>...`).
Most worlds use the catalog (`MPMLOAD CATALOG name`) instead.

A script text may begin with `STATIC=`. The engine then reads every `LOAD=`
file once, at that moment, and keeps the expanded text as the script itself, so
later changes to the files no longer reach it. `EXTRACT SCRIPT` leaves such a
script alone.

### LOAD arguments and ARG()

*(CoffeeSUD addition SC13.)* One guard script, many guards, each with its own
post: give `LOAD=` arguments after the file name, and read them in the file with
`ARG()`:

```text
LOAD=progs/guard.script POST=castle#12 GREETING="Halt! Who goes there?"
```

```text
ARGDEFAULT POST=gate GREETING="Move along."
GREET_PROG 100
if ispc($n)
  say $%ARG(GREETING)%
  mpsetvar @$i POSTED $%ARG(POST)%
endif
~
```

- `KEY=value` pairs follow the file name; quote values that contain spaces.
- `ARGDEFAULT KEY=value ...` lines at the top of the file give defaults for
  arguments the host did not pass. The engine strips these lines before parsing,
  so they are not commands.
- `$%ARG(NAME)%` reads a value as text; `ARG(NAME == value)` tests it. A missing
  name gives an empty string.
- Each host's arguments are its own. They are part of the host's script text, so
  they are saved and loaded with the host.
- The older pattern still works: put `FUNCTION_PROG POST~return castle#12~`
  before `LOAD=...` in the host's own text and read it with `$%CALLFUNC(POST)%`.
- Generic behaviours made with `CREATE BEHAVIOR` use the same idea with
  `PARM(name)`; see [chapter 24](24-behaviors-and-masks.md).

Fixture: [`sc13-loadargs.js`](../../tools/fixtures/fixes/sc13-loadargs.js).

### World scripts (the world host)

*(CoffeeSUD addition ST6.)* Every `world/scripts/*.script` file is attached,
automatically and without any behavior, to one hidden item called `WORLD`. That
item is held by the player's room, follows the player, and ticks every tick, so
its scripts hear world-wide events (`GAME_PROG`, `QUEST_PROG`, `TURN_PROG`,
`EVERY_PROG`, `DELAY_PROG`, `RAND_PROG`) wherever the player goes. Its variables
live under the host name `WORLD`. This is the right home for story-wide logic.

[Source](../../CoffeeMud/com/planet_ink/coffee_mud/core/WorldHost.java).

Consequence: **do not keep reusable per-NPC scripts in `world/scripts/`**.
A file there is both read by `LOAD=world:scripts/...` for your guard *and*
attached to the `WORLD` item as a second copy, so its `GREET_PROG` and
`RAND_PROG` run once more for the invisible host. Put NPC scripts in
`world/resources/progs/` and use `LOAD=progs/name.script`. The `world:` prefix is
for sharing a file that is *meant* to be both.

### Editing scripts in game

*(CoffeeSUD addition B3.)* In builder mode, or with `CMDMOBS`, `CMDITEMS` or
`CMDROOMS`:

```text
EDIT SCRIPT [TARGET] ([NUMBER])
EXTRACT SCRIPT [TARGET] ([NUMBER]) ([FILE])
```

`TARGET` is a mob or item here or carried, `HERE`, `AREA`, or `name@room`.
`NUMBER` picks which script when a thing has several: first its Scriptable
behaviors', then the scripts attached some other way.

- `EDIT SCRIPT` opens the script in the line editor, one statement to a line
  (a script kept on one line is split at its unescaped `;` and joined again
  when you save). Menu letters: `L` list, `E` edit a line, `I` insert, `D`
  delete, `A` add, `R` replace text, `S` save, `Q` quit.
- On save the script is checked as `SCRIPTCHECK` would; you may edit again,
  keep it anyway, or cancel.
- A script that is only `LOAD=file` opens *the file*. Saving rewrites the file,
  so every host that loads it sees the change, and the cached parse is dropped.
  A `LOAD=` with arguments after the name is not "only a file", so it opens the
  whole parameter text.
- A room or area is saved by the edit; a mob or item is changed in memory and
  needs `SAVE ROOM`.
- `EXTRACT SCRIPT` moves an inline script into `resources/progs/NAME.script`
  (the world's overlay copy in builder mode), one statement to a line, and
  leaves `LOAD=progs/NAME.script` behind. The default file name is made from the
  host's name; give a name ending in `.script` or containing `/` to choose one.
  It refuses a script that already is a `LOAD=`, an empty one, a `STATIC=` one,
  or a name that exists.
- `EDITOR=WINDOW` or `EDITOR=EXTERNAL` (with `EDITORCOMMAND=` or `$VISUAL`/
  `$EDITOR`) in `coffeemud.ini` swap the line editor for a window or your own
  editor on a temporary file. The same editor opens from `MODIFY` script fields
  and when you add a Scriptable behavior.

`MODIFY JSCRIPTS` and `JSCRIPTS=` in `coffeemud.ini` control whether `<SCRIPT>`
JavaScript blocks inside a MOBPROG run; the shipped setting `2` runs them
all (see [chapter 5](05-javascript.md)).

Sources: [EditCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/EditCmd.java),
[TextEditor](../../CoffeeMud/com/planet_ink/coffee_mud/core/TextEditor.java).

### Reloading after an edit

A script file you change outside the game is not noticed until the parsed copy
is dropped:

```text
WORLD RELOAD SCRIPT progs/guard.script
WORLD RELOAD SCRIPT ALL RESET
```

`WORLD RELOAD SCRIPT [FILE]` forgets every parsed program and re-reads the
scripts that name the file (or all with `ALL`); adding `RESET` also lets
`ONCE_PROG`s, `DELAY_PROG` waits and the `TIME_PROG`/`DAY_PROG` marks run
again. `UNLOAD` can drop the resource cache too, and a script can drop one with
`MPUNLOADSCRIPT`. `EDIT SCRIPT` on a `LOAD=` file does it for you.

## Program structure

A script is plain text. The parser (`parseScripts`) reads it left to right and
splits it with five rules:

| Character | Effect |
| --- | --- |
| line break | ends a command line |
| `;` | also ends a command line (this is what inline scripts use) |
| `~` | ends the program |
| `#` as the first non-blank character of a line | the rest of the line is a comment |
| `\` before `;` `~` `<` `#` or `\` | the character is taken literally |

Inside a `<SCRIPT> ... </SCRIPT>` block (embedded JavaScript) none of the
splitting applies, except that a literal `;` still needs `\;` if the JavaScript
is kept in an inline parameter.

A program is the trigger line, then the body, then `~`:

```text
SPEECH_PROG p password
# Only the exact phrase "password" opens the way.
if hastattoo($n KNOWS_TOWER)
  mpechoat $n The guard grunts. 'You already know the way.'
else
  mpechoat $n The guard leans close. 'The tower is north of the square.'
  mptattoo $n KNOWS_TOWER
endif
~
```

- The **trigger line** is the program's first line: a trigger word from the
  [trigger reference](26-mobprog-triggers.md) and its parameters (a chance, a
  phrase, an hour list, a mask). `SPEECH_PROG p password` means "when someone
  says exactly the phrase *password*"; without the `p` the parameters are a list
  of words, any one of which triggers it. Parameter forms differ per trigger, so
  read the entry, for instance [SPEECH_PROG](26-mobprog-triggers.md#speech_prog),
  [GREET_PROG](26-mobprog-triggers.md#greet_prog) and
  [FUNCTION_PROG](26-mobprog-triggers.md#function_prog).
- The **body** is zero or more command lines run top to bottom. Blank lines are
  ignored; command and function names are not case sensitive; arguments keep
  their case.
- **`~`** ends the program. Forgetting it is the classic mistake: the next
  trigger line becomes a body line and is reported as `Unexpected prog start --
  missing '~'?`.
- A script can hold any number of programs and any mix of triggers. One host may
  have several programs with the *same* trigger; the first whose parameters match
  runs (see [first match wins](#from-event-to-running-program)).
- A body line is one of: a control word (`IF`, `ELSE`, `ELSEIF`, `ENDIF`,
  `SWITCH`, `CASE`, `DEFAULT`, `ENDSWITCH`, `FOR`, `NEXT`, `WHILE`,
  `ENDWHILE`, `BREAK`, `CONTINUE`, `RETURN`), an `MP...` command, a
  `<SCRIPT>` JavaScript block, or any other word, which is run as a **game
  command** by the script's mob.
- The first word of an `MP` line must be a real command name. The engine still
  accepts an unambiguous prefix (`MPEXCH` for `MPEXCHANGE`), but an unknown or
  ambiguous `MP` word is an error, never a game command. Always write full names.

Indentation is only for people. The engine trims every line.

### Function programs

`FUNCTION_PROG name` is a program that no event triggers: it is *called* by name
(`MPCALLFUNC`, `CALLFUNC()`, `SCRIPT CALL`, `MPMENU`). It is the closest thing
the language has to a subroutine and it can return a value with `RETURN`. See
[calls, signals and pauses](#calls-signals-and-pauses).

## The execution model

### Hosts and the stand-in mob

Every script runs *for* a host and *as* a mob:

- **Mob host.** The mob is both host and actor. `$i` is the mob; game commands
  such as `say` or `give` are performed by it.
- **Any other host** (item, room, exit, area, the world host). The engine creates
  one stand-in `StdMOB`, named like the host and standing in the host's room.
  `$i` is the stand-in, `$q` is the host itself. `MPECHO`, `MPKILL`, `MPCAST`,
  game commands and the rest are performed by the stand-in. An item that is
  carried reports the room of its carrier.

The command reference lists, command by command, what each does for a non-mob
host ([chapter 27](27-mobprog-commands.md)). Practical consequences:

- Use `$q` when you mean "the thing the script is on" and `$i` when you mean "the
  one who acts". For a mob they are the same.
- Functions about `$i` (`HITPRCNT($i)`, `ISFIGHT($i)`) are meaningless for an
  item host.
- An item host's room is wherever the item is: in a room, or in the pack of a
  mob standing in a room. An item whose carrier is in an empty room is skipped
  on that tick.

### From event to running program

Triggers come in two kinds, and they run differently.

**Message triggers** (`GREET`, `SPEECH`, `GIVE`, `GET`, `OPEN`, `BUY`, `DEATH`,
`CNCLMSG`, `EXECMSG`, ...). Every action in the game is a message that all
interested objects in the room (and the area) see. A scripted host's engine
looks through its programs, in the order they are written, for the first one
whose trigger matches the message. When one matches:

1. The matching program is **queued** with a delay of one tick, together with
   its context: host, actor (`$n`), target (`$t`), items (`$o`, `$p`) and the
   message words (`$g`).
2. The engine **stops looking** at that message for this host. Only the first
   matching program of an engine runs. (`MPSETINTERNAL MULTITRIGGER TRUE` turns
   this off.) Consequence: order programs from most to least specific. A
   `BRIBE_PROG 500` must come before `BRIBE_PROG 100`, or the 500 can never run.
3. On the host's next tick the queue is processed and the program runs from its
   first body line to the end, all in one go.

So a reply to speech normally comes up to one tick (4 seconds by default) after
the player speaks. In turn-based time the replies of `SPEECH_PROG`, `GIVE_PROG`,
`BRIBE_PROG`, `SOCIAL_PROG`, `TALK_PROG` and MudChat are run before the player's
next prompt instead, and the clock does not move for them
(`DIALOGUETIME=INSTANT`; see [chapter 31](31-saves-and-party.md)). Some triggers are
not queued: `TALK_PROG` runs at once, and `CNCLMSG_PROG`, `CMDFAIL_PROG` and
`READ_PROG` run while the game is still deciding whether the action may happen,
because the game is waiting for their answer (`RETURN CANCEL` refuses it). Their
reference entries say which.

**Clock triggers** (`RAND_PROG`, `DELAY_PROG`, `TIME_PROG`, `DAY_PROG`,
`EVERY_PROG`, `ONCE_PROG`, `TURN_PROG`...). The engine's own tick asks each such
program whether it is due and, if it is, runs it immediately in that tick. Percent
chances (`RAND_PROG 20`) are rolled every tick the host ticks.

Other facts about the loop:

- The host must be alive. A dead mob's engine does nothing.
- A restored game's scripts wait until the saved world is back, so a
  `ONCE_PROG` that already ran does not run again before the restore finishes.
- Executing a command may cause new messages, which may trigger scripts, which
  may cause messages. The engine stops handling messages for an engine that is
  already three levels deep in its own message handling, so a script that makes
  itself trigger endlessly is cut off rather than freezing the game.
- A queue longer than 5 is de-duplicated (identical host/actor/target/script
  entries are collapsed). A queue that still grows past 25 entries (150 for an
  area) is a runaway: the engine logs `Attempt to enque more than N events`
  with a queue report and clears it.
- Scripts run on the single game thread. A prompt command (`MPPROMPT`,
  `MPCONFIRM`, `MPCHOOSE`) marks its program to run on its own thread so the game
  does not freeze while the player types; `MPWAIT` and `MPMENU` never block.
- A long-running program holds up the game. `SCRIPTSLOW=ms` in `coffeemud.ini`
  logs every trigger that runs longer, with its host, file and line.

### When a host is too busy to react

Most *message* triggers on a **mob** host only fire when the mob is free to
behave normally. The engine's test (`isFreeToBeTriggered`) is: it can act at all
(not asleep, paralysed, bound, unconscious...), it is **not in combat**, it is
**not following** someone (for an NPC), and its hit points are **at least half**
of its maximum. A wounded or fighting shopkeeper stops answering speech, gifts
and purchases. `SCRIPTTRACE` reports it as `busy (asleep, fighting, bound or the
like), so its triggers wait`.

`MPSETINTERNAL ACTIVETRIGGER TRUE` relaxes the test to "can act at all". Combat
triggers (`FIGHT_PROG`, `HITPRCNT_PROG`, `DEATH_PROG`, `COMBAT_PROG`) are meant
for exactly this state and do not need it. Item, room, exit and area hosts have
no such condition.

### Per-script engine switches

`MPSETINTERNAL name value` changes how *this engine* behaves. Put it in a
`ONCE_PROG` or at the start of the programs that need it. The names the engine
accepts (anything else logs `Unknown stat`):

| Name | Value | Effect |
| --- | --- | --- |
| `SCOPE` | name, `*`, `INDIVIDUAL`, `GLOBAL` | Which variable store this script uses ([scopes](#variable-scopes)). |
| `VARHOST` | `INSTANCE` or `NAME` | `INSTANCE`: a bare `$i`/`$n` host in `MPSETVAR` means that object's own variables (as `@$i`). Saved with the engine. |
| `NODELAY` | true/false | Run queued responses at once instead of one tick later. (The `SCRIPTABLEDELAY` disable flag sets this globally.) |
| `ACTIVETRIGGER` | true/false | Triggers fire even if the mob is fighting, wounded or following. |
| `MULTITRIGGER` | true/false | Let every matching program run, not just the first. |
| `DEFAULTQUEST` | quest name | Make a quest's variable scope the default for reads. |
| `SAVABLE` | true/false | Whether this engine is written to the world files. |
| `PASSIVE` | true/false | Whether the script runs while its area is passive (no player nearby). Default true. |
| `NEEDPC` | true/false | When true, the script's clock triggers are skipped on ticks when no player character is in its room. |
| `CANCELWAITS` | (none) | Drops every pending `MPWAIT` of this engine except the one running. |
| `TRACE` | true/false | Traces this host to the builder, as `SCRIPTTRACE` does (builder mode or a DEV=1 game only). |
| `LLM...` | text | Settings for `MPLLM` (an LLM-backed command that needs an LLM session). |

### What runs on which object

The same trigger word means slightly different things on different hosts. In
general (each trigger's entry in [chapter 26](26-mobprog-triggers.md) gives its
exact host rules):

| Host | Hears |
| --- | --- |
| **Mob** | Events in its own room that involve it or happen near it, its own actions (`..ING_PROG`), clock triggers, and area-wide `@GLOBAL` forms. Needs to be free (above). |
| **Item** | Events about itself (`GET_PROG`, `DROP_PROG`, `WEAR_PROG`, `USE_PROG`, `READ_PROG`) and events in the room it is in. An item carried by a mob treats the carrier as the creature that events such as death and damage are about. Clock triggers tick while it is in the game. |
| **Room** | Everything that happens in the room, and room-level events (`DISCOVER_PROG`, `WEATHER_PROG`, `AMBUSH_PROG`, `WAVE_PROG`). A room never triggers `ARRIVE_PROG`. |
| **Exit** | Events at the exit; useful for `SIGNAL_PROG` (`MPSIGNAL ROOMID:NORTH`), `OPEN_PROG`/`CLOSE_PROG`, `VERB_PROG`. |
| **Area** | Events in any of its rooms, and entering or leaving the area (`AREA_ENTER_PROG`, `AREA_LEAVE_PROG`); the calendar triggers. An event in a room reaches the room's area and that area's parent areas, so a script on a parent area hears its children. |
| **World host** (`WORLD`) | Game-wide events (`GAME_PROG`, `QUEST_PROG`, `TURN_PROG`, `EVERY_PROG`...) wherever the player is. |

Events with no message, such as a skill learned (`SKILL_PROG`), a room first
visited (`DISCOVER_PROG`), weather (`WEATHER_PROG`), the moon, quest progress,
and game events, are delivered by `ScriptEvents` to the same hosts a message in
that room would reach: the room, its items, its mobs and what they carry, and
its area and the area's parents. `ScriptEvents` only looks for a trigger some
loaded script actually has, so an unused trigger costs nothing.

## Control flow

All control words are case-insensitive and each stands on a line of its own.
Complete syntax is in the [commands reference](27-mobprog-commands.md); this
section is how they fit together.

### IF, ELSEIF, ELSE, ENDIF

```text
if ispc($n) and !hastattoo($n BANNED)
  say Welcome, $n.
elseif isnpc($n)
  say Hello, colleague.
else
  say Hm.
endif
```

- A **condition** is one or more *function calls* joined by `AND`, `OR`, `NOT`
  (written `!`), `ANDNOT`, `ORNOT`, grouped with parentheses. A function call is
  `NAME(arguments)`; the comparison operator lives **inside** the parentheses:
  `LEVEL($n >= 10)`, `VAR($n CHAPTER == 2)`, `MATH($1 + 1 > 5)`. A bare
  comparison such as `$n == 5` outside a function is not understood; wrap it in
  `EVAL($n == 5)`. Operators are `==` `!=` `>` `<` `>=` `<=`. Do not rely on how
  `AND` and `OR` bind when mixed; use parentheses.
- Every `IF` needs an `ENDIF`. `ELSEIF cond` (or `ELSE IF cond`) is rewritten at
  load time into `ELSE` + `IF`, with the extra `ENDIF` supplied, so a whole
  chain needs one `ENDIF` (CoffeeSUD addition SC17). `ELSE` takes no text.
- A missing `ENDIF`, or an unparseable condition, is logged and the rest of that
  block of the program is abandoned.
- Because the whole program runs in one tick, a condition is tested against the
  world *at that instant*; nothing can change between lines (unless a line
  starts something delayed).

### SWITCH, CASE, DEFAULT, ENDSWITCH

For many tests of one value:

```text
switch $%VAR($i MOOD)%
case calm
  say All is well.
case angry
  say Leave.
default
  say Hm.
endswitch
```

Case values are compared case-insensitively as text. A `CASE` whose value
contains a `$` is evaluated each time. Cases do not fall through: when one body
ends the switch is done.

### FOR and NEXT

```text
for $1 = 1 to 5          # counts 1,2,3,4,5; counts down if the end is lower
for $1 = 0 to< 5         # 0,1,2,3,4 (TO< excludes the end; TO> the other way)
for $1 in sword,shield   # a comma list; each item is substituted
for $1 in LIST $<WORLD SUSPECTS>   # substitutes first, splits on spaces or commas
for $1 in #sunaltar      # every object tagged sunaltar
for $1 in SELECT: * from mobs      # an MQL query, from the host's point of view
  mpecho $1
next
```

- The loop variable must be one of `$0`..`$9` and must not already hold a whole
  number (so loops nest with different variables). It is cleared afterwards.
- Plain `FOR $1 IN a b c` with spaces does not split on spaces (it splits on
  commas). Use `IN LIST` for space-separated lists, and quote words that
  contain spaces.
- A `FOR` stops after **10 real seconds** (logged as violating the 10 second
  rule) or if the host is destroyed.
- `MPWAIT` cannot pause inside a `FOR` (it logs and does not wait).

### WHILE and MPWHILE

Two different loops with similar names:

| | `WHILE (cond) [max] ... ENDWHILE` | `MPWHILE (cond) command` |
| --- | --- | --- |
| Body | any number of lines | one command line |
| Bound | number of passes, default 1000, or the number after the condition: `WHILE (cond) 50` | 4 real seconds |
| Stops with | `BREAK`, `CONTINUE`, `RETURN` allowed | the command only |
| Added by | CoffeeSUD (SC17) | inherited |
| Cut-off is logged | `stopped after N passes` | `cut off after 4 seconds` |

Prefer `WHILE`: its limit does not depend on how fast the machine is, which
matters when the game can run in turn-based time. Both stop when the host is
destroyed.

### BREAK, CONTINUE, RETURN

- `BREAK` ends the nearest `FOR` or `WHILE` loop. Outside a loop it ends the
  whole program, from however deep inside `IF` and `SWITCH` blocks it appears.
- `CONTINUE` ends the current pass of the nearest loop and starts the next
  (CoffeeSUD addition SC17).
- `RETURN text` ends the whole program and hands `text` to whatever ran it. For
  a `FUNCTION_PROG` that is the function's value (`CALLFUNC()`, `SCRIPT CALL`).
  For the "veto" triggers (`CNCLMSG_PROG`, `CMDFAIL_PROG`, `VERB_PROG`,
  `READ_PROG`, `GAME_PROG SAVE`, ...) the word `CANCEL` (or `CONTINUE` for
  `VERB_PROG`) is the answer the game is waiting for. A plain `RETURN` ends the
  program early with nothing returned. `RETURN` inside a loop ends the program,
  not only the loop.

### Nesting

`IF`, `SWITCH`, `FOR` and `WHILE` nest freely. The ten temporary variables
`$0`..`$9` limit `FOR` nesting to ten, and `$0`..`$9` are also what a function call
passes in, so choose loop variables that do not clash with values you still
need.

