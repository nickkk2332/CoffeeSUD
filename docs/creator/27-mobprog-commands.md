# MOBPROG commands reference

[Handbook](README.md) · [MOBPROG guide](04-mobprog.md) · [Triggers](26-mobprog-triggers.md) · [Functions and `$` codes](28-mobprog-functions.md)

This is the reference for everything a MOBPROG script can *do*: the `MP*` commands, the control-flow statements (`IF`, `SWITCH`, `FOR`, `WHILE`, `MPWHILE`, `BREAK`, `RETURN` and friends), the embedded-JavaScript block, and the rule that any other line runs as an ordinary game command. It lists every command the engine knows, with syntax, arguments, behaviour, an example and notes. It is for scripters and world creators. Triggers (what starts a script) are in [26](26-mobprog-triggers.md); the `IF` conditions and `$%FUNC()%` value functions and the `$` codes are in [28](28-mobprog-functions.md); how to structure and debug a whole script is in [04](04-mobprog.md).

Source of truth: the command table `methods` in [ScriptingEngine](../../CoffeeMud/com/planet_ink/coffee_mud/Common/interfaces/ScriptingEngine.java), the execute switch and `extraCommand` in [DefaultScriptingEngine](../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultScriptingEngine.java), the argument-count table in [ScriptSignatures](../../CoffeeMud/com/planet_ink/coffee_mud/core/ScriptSignatures.java) and the `SCRIPTCHECK` linter [ScriptCheck](../../CoffeeMud/com/planet_ink/coffee_mud/core/ScriptCheck.java). Every entry below was read against that code.

## In this chapter

- [How commands work](#how-commands-work): program layout, lookup, arguments and quoting, `$` substitution, targets, hosts, errors, flow, timing
- [Commands by purpose](#commands-by-purpose): which command to reach for
- [Control flow](#control-flow): `IF`, `ELSE`, `ELSEIF`, `ENDIF`, `SWITCH`, `FOR`, `MPWHILE`, `WHILE`, `BREAK`, `RETURN`, `<SCRIPT>`, ...
- [Messages and speech](#messages-and-speech)
- [Movement and placement](#movement-and-placement)
- [Loading and removing things](#loading-and-removing-things)
- [Doors, exits and visibility](#doors-exits-and-visibility)
- [Variables, lists, tags and state](#variables-lists-tags-and-state)
- [Calling, timing and script management](#calling-timing-and-script-management)
- [Asking the player](#asking-the-player)
- [Stats, skills and progression](#stats-skills-and-progression)
- [Combat and effects](#combat-and-effects)
- [Quests](#quests)
- [Journal, lore and narration](#journal-lore-and-narration)
- [Money, shops, banks and property](#money-shops-banks-and-property)
- [Law, factions, clans and relationships](#law-factions-clans-and-relationships)
- [World, weather, time and generation](#world-weather-time-and-generation)
- [Travel and vessels](#travel-and-vessels)
- [Logging, debugging and miscellany](#logging-debugging-and-miscellany)
- [Game commands in scripts](#game-commands-in-scripts)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## How commands work

### Program layout

A script is a list of *programs*. Each program is a trigger line followed by command lines, and ends at `~`:

```text
GREET_PROG 100
if ispc($n)
  mpecho $i nods to $n.
endif
~
```

The parser (in `parseScripts`) splits the text with these rules:

| Character | Effect |
| --- | --- |
| newline | ends a command line |
| `;` | also ends a command line, so several commands fit on one line |
| `~` | ends the program |
| `#` as the first non-blank character of a line | the line is a comment |
| `\` before `;`, `~`, `<`, `#` or `\` | takes the character literally (the backslash is removed) |

The first line of a program is the trigger (`GREET_PROG 100`); every later line is a command. Blank lines are ignored. Command names are not case sensitive (`MPECHO`, `mpecho` and `MpEcho` are the same). Arguments keep their case unless the command says otherwise.

Because `;` ends a line, a command that must carry whole script text inside itself (`MPSCRIPT`, `MPALARM` with several steps) needs the inner `;` and `~` written as `\;` and `\~`.

`ELSEIF c` (or `ELSE IF c`) is rewritten when the script is loaded into `ELSE` / `IF c` with one more `ENDIF` at the end of the chain, so a chain of `ELSEIF`s needs only one `ENDIF`.

### Command lookup

For each command line the engine takes the first word, upper-cases it and looks for it in the command table (the `methods` array, 150-odd names). The result decides what happens:

1. **An exact name** runs that command.
2. **A word starting with `MP` that is not a name**: the engine looks for command names that *start with* that word. If exactly one does, it runs (so `MPECHOA`... is ambiguous, but `MPEXCH` runs `MPEXCHANGE`). If none do, it logs `unknown command` and goes on to the next line. If several do, it logs `ambiguous command` listing them and goes on. An `MP` word is *never* run as a game command.
3. **A trigger name** (`GREET_PROG` ...) in the middle of a program logs `Unexpected prog start -- missing '~'?`.
4. **Anything else** is a game command: see [Game commands in scripts](#game-commands-in-scripts).

Write the full command name. Prefix matching is a leftover convenience; `SCRIPTCHECK` (see below) rejects a prefix with `MPX is not a command; write MPXYZ in full`, and a later release that adds a longer command starting with the same letters would turn your prefix into an ambiguity. Names that are exact never change meaning.

`ELSE` is not in the table: it is only understood inside an `IF` block. A stray `ELSE` outside an `IF` is passed on as a game command and fails. `ENDIF`, `ENDSWITCH`, `NEXT`, `CASE`, `DEFAULT`, `ENDWHILE` and `ELSEIF` outside their block log a syntax error such as `ENDIF Syntax: Without IF`.

The names `MEA` (for `MPECHOAT`) and `MER` (for `MPECHOAROUND`) are real table entries, not prefixes. `MPRLOAD` is in the table but only logs `MPRLOAD is not a command` (an old name that never had an implementation).

### Arguments and quoting

Most commands split their line into *bits*. A bit is a run of non-blank characters, or a run enclosed in single quotes `'...'` or backticks `` `...` ``. The closing quote must be followed by a space or the end of the line. Double quotes do **not** group at the bit level (a few commands, such as `MPMENU`, `MPDOOR`, `MPTELEGRAPH` and `MPSHOP`, parse double-quoted text themselves, and say so below).

Each command takes some bits as separate arguments and the *rest of the line* as one last argument. The rest of the line comes in two flavours that matter for apostrophes:

- Rest taken **raw** (`MPECHO`, `MPECHOAT`, `MPECHOAROUND`, `MPFORCE`, `MPAT`, `MPAFFECT`, `MPBEHAVE`, `MPASOUND`, `MPPOSSESS` ...): the text is used exactly as written, so `mpecho Bob's sword gleams.` prints the apostrophe.
- Rest taken **cleaned** (most others): surrounding single quotes are stripped, and every remaining `'` becomes a backtick `` ` ``. A name such as `Bob's sword` will not match an item called `Bob's sword`; write the search name without the apostrophe, or use a `$` code.

An argument that names a person or thing and has several words must be quoted: `mpat 'The Gate Room' say hello`, `mptransfer 'a guard' Midgaard#3001`. Inside the *first* argument of `MPAT`, `MPFORCE`, `MPCAST`, `MPECHOAT` and similar, a multi-word name needs `'...'`.

Numbers are plain text; most numeric arguments accept a `$` code or a `$%FUNC()%` that expands to a number. Where a command accepts `++`/`--`, say so below.

Argument counts: `ScriptSignatures` records the minimum number of words for each command; `SCRIPTCHECK` reports `MPXYZ needs at least N arguments; got M` as a warning. Counts are checked after the line is split into bits, and are skipped when the line contains `$g` or `$%`, which may expand to several words. The minimums are listed in each entry as "Minimum".

### `$` substitution

Before a command uses an argument, the engine usually runs *variable substitution* on it:

| Form | Meaning |
| --- | --- |
| `$n` `$t` `$i` `$o` `$p` `$b` ... | name or pronoun of the event's actor, target, host, item, loaded thing, etc. |
| `$0` .. `$9` | the trigger's local slots (arguments of a function call, a `FOR` counter, `MPARGSET` values) |
| `$<HOST VAR>` | a script variable's value |
| `$%FUNC(args)%` | a value function's result |
| `$(name)` | a named local (`MPLOCAL`, `FUNCTION_PROG` parameters) |
| `$$` or `\$` | a literal dollar sign |

The complete list of `$` codes and every value function is in [28](28-mobprog-functions.md). Two points matter for commands:

- Arguments that name a thing (a *target*) also accept the codes as **objects**: `mpkill $n` hits the actual actor, not whoever happens to have the same name.
- `MPECHO` text and the rest of a game-command line are substituted as text. Substitution happens at the moment the command runs, so values set by earlier lines are visible.

### Targets

Most commands take a *target*: the mob, item, exit or room to act on. A target argument is resolved in this order (the engine's `getArgumentItem`):

1. A `$` code: `$n` (actor), `$t` (target), `$i` or `$q` (the host), `$o`/`$p` (the event's items), `$b` (last thing loaded), `$a` (the area), `$d` (the room), `$f` (who the host follows), `$r` (a random player in the room), `$c` (a random mob or player in the room), `$w`/`$W` (the holder of `$o`/`$p`), `$u` (the character at the terminal), `$x` plus a direction letter, as in `$xn` (the exit that way; plain `$x` is a random exit), `$0`..`$9` (a stored object), `$[N quest name]` (the quest's Nth item) and `${N quest name}` (its Nth mob).
2. Otherwise the text is substituted, then searched: a room ID (anything containing `#` after the first character) gives that room; then a name is looked up among the things in the host's room (mobs first, then items), then the host's inventory, then the player (by name, anywhere in the game), then the actor's inventory.
3. If nothing matches the command does nothing (usually silently).

Commands that must act on a creature use a variant (`getArgumentMOB`) that resolves from the point of view of the *mob* the script runs as, which differs from the host only when the host is not a mob.

Room arguments (`MPGOTO`, `MPAT`, `MPTRANSFER`, ...) accept a `$` code (the room that thing is in), a full room ID such as `Midgaard#3001`, a bare number or `#N` (a room of the host's own area), or a room, area, mob or player name searched liberally in the host's area then in the world. A name that matches several rooms picks one at random, so use IDs.

### Hosts and the mob a script runs as

A script lives on a *host*: a mob, an item, a room, an exit or an area (see [04](04-mobprog.md)). The engine needs a mob to *act as* for echoes, fights, casting, forcing commands. For a mob host that is the mob. For any other host the engine makes one stand-in mob (`StdMOB`), named like the host and standing in the host's room; this is `$i` in text and the actor of `MPECHO`, `MPKILL`, `MPCAST`, `MPFORCE`, game commands and so on.

| Command or group | Mob host | Item / room / exit / area host |
| --- | --- | --- |
| `MPECHO`, `MPECHOAT`, `MPASOUND`, `MPECHOAROUND` | echo shows as from the mob | shows as from the stand-in (works, the room sees the host's name) |
| `MPGOTO`, `MPTRANSFER $i` | moves the mob | an item host moves itself; a room, exit or area host cannot move |
| `MPOLOAD` | into the mob's inventory | item: into its holder (or the room); room: into the room; otherwise where the host stands |
| `MPJUNK` | the mob's items | the stand-in's items, or a room host's floor |
| `MPKILL`, `MPHIT`, `MPCAST`, `MPCASTEXT`, `MPTRACKTO`, `MPWALKTO`, `MPSTOP` | the mob | the stand-in (it fights and casts for the host) |
| `MPTELEGRAPH` | the mob | refused: "only a mob in a room can wind up an attack" |
| `MPWAVE` | the room the mob is in | the host room itself (`WaveSpawner` there) |
| `MPOLOADSHOP`, `MPMLOADSHOP` | the host if a shopkeeper, else the shopkeeper in its room | same |
| `MPPURGE self` | a mob with a start room is killed (and may rejuvenate); one without is destroyed | destroys the host object |

Item, room, exit and area hosts also read the *location* the host was last in, so a room-hosted `MPECHO` goes to that room and an area-hosted one to the room the script last knew.

### What happens on errors

A bad command never stops the program. The engine writes one line to the error log and to the script error list, then goes on with the next line:

```text
file:line MPGOTO Syntax: ... (a guard @ Midgaard#3001, world:scripts/guard.script)
```

The `file:line` prefix names the script file and line; the part in parentheses is the host and where it is. Read them with `SCRIPTERRORS` in the game, or watch the log (see [04](04-mobprog.md) and [13](13-testing-release.md)). A normal game does not show script errors to the player; the builder sees them in builder mode. Typical outcomes:

- Unknown target, unknown stat, bad direction, bad room: logged, nothing happens.
- A syntax problem in an `IF` (no `ENDIF`, a condition that does not parse): logged, and the **rest of that program level is abandoned**. The same for `SWITCH` without `ENDSWITCH` and `FOR` without `NEXT`, or with a bad loop variable.
- A command that fails inside `FOR` or `MPWHILE` fails once per pass.
- `MPDEBUG` and the trace tools log extra lines (see [MPDEBUG](#mpdebug)).

`SCRIPTCHECK` (and the `CHECK` modes) read every script *without running it* and report by file and line: unknown commands (no prefix matching), unknown functions, unbalanced `IF`/`SWITCH`/`FOR`/`WHILE` blocks, missing `ENDIF`, `<SCRIPT>` without `</SCRIPT>`, JavaScript that does not compile, `LOAD=` files that are missing, `MPCALLFUNC` to a function that does not exist, room IDs that do not exist in `MPGOTO`, `MPTRANSFER` and `MPOLOADROOM`, and argument-count warnings. It also flags a game-command line whose first word is not a known command, social or ability.

### Flow, return values and what ends a program

Every program is executed top to bottom. Blocks (`IF`, `SWITCH`, `FOR`, `WHILE`, `MPWHILE`'s body) run as nested sub-programs. A nested block that returns a value ends the whole enclosing program with that value, unless the construct consuming it is a loop (see [BREAK](#break)).

- `RETURN text` ends the program and hands `text` back to whatever ran it. For a `FUNCTION_PROG` that is the function's result; for the "cancel" family of triggers a result of `CANCEL` vetoes the event (see [26](26-mobprog-triggers.md)).
- `BREAK` ends the program (or the nearest enclosing `FOR` / `WHILE` loop).
- `CONTINUE` ends the current pass of the nearest `FOR` / `WHILE` loop.
- A command that pauses the program (`MPWAIT`) ends the current run and queues the rest.
- A prompt command (`MPPROMPT`, `MPCONFIRM`, `MPCHOOSE`) marks the program as one that may block, and runs it on its own thread so the game does not freeze while the player answers.

### Timing

Commands run at the moment the trigger runs, on the game's script thread, and take no game time. Nothing waits unless you use `MPALARM` (a delayed command), `MPWAIT` (pause the rest of the program), `MPTELEGRAPH` (a delayed attack) or `MPPASSTIME` (make game time pass). A loop is bounded: `FOR` stops after 10 real seconds, `MPWHILE` after 4 real seconds, `WHILE` after a number of passes (default 1000).

### CoffeeSUD single-player notes

- There is one player (a *profile* with a party of characters). `$u` is the character at the terminal. With party windows off, `MPECHOAT`, `MPPROMPT`, `MPCONFIRM`, `MPCHOOSE` and `MPMENU` aimed at a party member in the background are routed to the terminal, with the member's name in front.
- The account is the player's profile. `MPACCTATTOO` therefore sets a flag shared by all of the profile's characters; `MPTATTOO` sets one on a single character.
- Channels: only `INFO` and `QUESTCHAT` exist, so `MPCHANNEL` has little to say to. `MPPOSSESS` has no sensible single-player use. `MPSAVEVAR`/`MPLOADVAR` use the game's local data store, not a multi-user database.
- A save keeps script variables, what is queued (`MPALARM`, `MPWAIT`, delayed signals) and the tattoos and quest state commands change. See [Persistence notes](#persistence-notes).

Entries added by CoffeeSUD (not in the original CoffeeMud) are marked **CoffeeSUD**.

## Commands by purpose

| Purpose | Commands |
| --- | --- |
| Control flow | [IF](#if), [ELSE](#else), [ELSEIF](#elseif), [ENDIF](#endif), [SWITCH](#switch), [CASE](#case), [DEFAULT](#default), [ENDSWITCH](#endswitch), [FOR](#for), [NEXT](#next), [MPWHILE](#mpwhile), [WHILE](#while), [ENDWHILE](#endwhile), [BREAK](#break), [CONTINUE](#continue), [RETURN](#return), [\<SCRIPT\>](#script) |
| Messages and speech | [MPECHO](#mpecho), [MPECHOAT](#mpechoat), [MEA](#mea), [MPECHOAROUND](#mpechoaround), [MER](#mer), [MPASOUND](#mpasound), [MPSPEAK](#mpspeak), [MPSAYIN](#mpsayin), [MPCHANNEL](#mpchannel), [MPMSG](#mpmsg) |
| Movement and placement | [MPGOTO](#mpgoto), [MPAT](#mpat), [MPTRANSFER](#mptransfer), [MPBEACON](#mpbeacon), [MPPUT](#mpput), [MPTRACKTO](#mptrackto), [MPWALKTO](#mpwalkto), [MPLINK](#mplink), [MPUNLINK](#mpunlink), [MPSETRESPAWN](#mpsetrespawn) |
| Loading and removing | [MPMLOAD](#mpmload), [MPOLOAD](#mpoload), [MPOLOADROOM](#mpoloadroom), [MPMLOADSHOP](#mpmloadshop), [MPOLOADSHOP](#mpoloadshop), [MPGENERATE](#mpgenerate), [MPTRANSFORM](#mptransform), [MPM2I2M](#mpm2i2m), [MPJUNK](#mpjunk), [MPPURGE](#mppurge), [MPSLAY](#mpslay), [MPRLOAD](#mprload) |
| Doors, exits, visibility | [MPOPEN](#mpopen), [MPCLOSE](#mpclose), [MPLOCK](#mplock), [MPUNLOCK](#mpunlock), [MPDOOR](#mpdoor), [MPHIDE](#mphide), [MPUNHIDE](#mpunhide) |
| Variables and state | [MPSETVAR](#mpsetvar), [MPSAVEVAR](#mpsavevar), [MPLOADVAR](#mploadvar), [MPLIST](#mplist), [MPLOCAL](#mplocal), [MPARGSET](#mpargset), [MPSTATE](#mpstate), [MPTAG](#mptag), [MPSET](#mpset), [MPGSET](#mpgset), [MPSETINTERNAL](#mpsetinternal) |
| Calling and timing | [MPCALLFUNC](#mpcallfunc), [MPALARM](#mpalarm), [MPALARMCANCEL](#mpalarmcancel), [MPWAIT](#mpwait), [MPSIGNAL](#mpsignal), [MPNOTRIGGER](#mpnotrigger), [MPINTERRUPT](#mpinterrupt), [MPPASSTIME](#mppasstime), [MPSCRIPT](#mpscript), [MPUNLOADSCRIPT](#mpunloadscript), [MPJSFILE](#mpjsfile), [MPBEHAVE](#mpbehave), [MPUNBEHAVE](#mpunbehave) |
| Asking the player | [MPPROMPT](#mpprompt), [MPCONFIRM](#mpconfirm), [MPCHOOSE](#mpchoose), [MPMENU](#mpmenu), [MPLLM](#mpllm) |
| Stats and progression | [MPEXP](#mpexp), [MPRPEXP](#mprpexp), [MPLEVELUP](#mplevelup), [MPLEVELDOWN](#mpleveldown), [MPQUESTPOINTS](#mpquestpoints), [MPTRAINS](#mptrains), [MPPRACS](#mppracs), [MPENABLE](#mpenable), [MPDISABLE](#mpdisable), [MPLEARNRECIPE](#mplearnrecipe), [MPLEARNLANG](#mplearnlang), [MPPERK](#mpperk), [MPRANK](#mprank), [MPRESPEC](#mprespec), [MPPLAYERCLASS](#mpplayerclass), [MPTITLE](#mptitle), [MPTATTOO](#mptattoo), [MPACCTATTOO](#mpacctattoo), [MPACHIEVE](#mpachieve), [MPRECRUIT](#mprecruit) |
| Combat and effects | [MPKILL](#mpkill), [MPHIT](#mphit), [MPDAMAGE](#mpdamage), [MPHEAL](#mpheal), [MPCAST](#mpcast), [MPCASTEXT](#mpcastext), [MPAFFECT](#mpaffect), [MPUNAFFECT](#mpunaffect), [MPSTOP](#mpstop), [MPTHREAT](#mpthreat), [MPTELEGRAPH](#mptelegraph), [MPWAVE](#mpwave), [MPREJUV](#mprejuv), [MPRESET](#mpreset) |
| Quests | [MPSTARTQUEST](#mpstartquest), [MPSTEPQUEST](#mpstepquest), [MPENDQUEST](#mpendquest), [MPQUESTWIN](#mpquestwin), [MPQUESTFAIL](#mpquestfail), [MPREWARD](#mpreward), [MPQSET](#mpqset), [MPLOADQUESTOBJ](#mploadquestobj), [MPQOBJECTIVE](#mpqobjective), [MPQJOURNAL](#mpqjournal), [MPQTIMER](#mpqtimer) |
| Journal, lore, narration | [MPJOURNAL](#mpjournal), [MPLORE](#mplore), [MPNARRATE](#mpnarrate) |
| Money, shops, property | [MPMONEY](#mpmoney), [MPSHOP](#mpshop), [MPBANK](#mpbank), [MPDEBT](#mpdebt), [MPEXCHANGE](#mpexchange), [MPDEED](#mpdeed), [MPUNDEED](#mpundeed), [MPGRANT](#mpgrant) |
| Law and society | [MPACCUSE](#mpaccuse), [MPPARDON](#mppardon), [MPFINE](#mpfine), [MPRELEASE](#mprelease), [MPSUPPRESSLAW](#mpsuppresslaw), [MPFACTION](#mpfaction), [MPAFFINITY](#mpaffinity), [MPSETCLAN](#mpsetclan), [MPSETCLANDATA](#mpsetclandata), [MPCLANRELATION](#mpclanrelation), [MPCONQUER](#mpconquer) |
| World, weather, time | [MPWEATHER](#mpweather), [MPROOMSTATE](#mproomstate), [MPREGENERATE](#mpregenerate), [MPPASSTIME](#mppasstime) |
| Travel and vessels | [MPSAIL](#mpsail), [MPANCHOR](#mpanchor), [MPMOVEVESSEL](#mpmovevessel), [MPBOARD](#mpboard), [MPHITCH](#mphitch), [MPUNHITCH](#mpunhitch) |
| Logging and debugging | [MPLOG](#mplog), [MPDEBUG](#mpdebug) |
| Legacy and rarely useful | [MPPOSSESS](#mppossess), [MPSCRIPT](#mpscript), [MPRLOAD](#mprload) |

CoffeeSUD additions (everything not in the original CoffeeMud 5.11 command list): `MPALARM NAME=`, `MPALARMCANCEL`, `MPTELEGRAPH`, `MPWAVE`, `MPSETRESPAWN`, `MPTHREAT`, `MPAFFINITY`, `MPMENU`, `MPMSG`, `MPSIGNAL`, `MPWAIT`, `MPLIST`, `MPTAG`, `MPSTATE`, `MPLOCAL`, `WHILE`/`ENDWHILE`/`CONTINUE`/`ELSEIF`, `MPJSFILE`, `MPDOOR`, `MPTRANSFORM`, `MPQOBJECTIVE`, `MPQJOURNAL`, `MPQUESTFAIL`, `MPREWARD`, `MPQTIMER`, `MPGENERATE`, `MPREGENERATE`, `MPGRANT`, `MPDEED`, `MPUNDEED`, `MPLEARNRECIPE`, `MPLEARNLANG`, `MPSAYIN`, `MPPERK`, `MPRECRUIT`, `MPLEVELUP`, `MPLEVELDOWN`, `MPRESPEC`, `MPRANK`, `MPPARDON`, `MPFINE`, `MPRELEASE`, `MPSUPPRESSLAW`, `MPSHOP`, `MPBANK`, `MPDEBT`, `MPEXCHANGE`, `MPWEATHER`, `MPROOMSTATE`, `MPPASSTIME`, `MPINTERRUPT`, `MPCLANRELATION`, `MPCONQUER`, `MPSAIL`, `MPANCHOR`, `MPMOVEVESSEL`, `MPBOARD`, `MPHITCH`, `MPUNHITCH`, `MPLORE`, `MPJOURNAL`, `MPNARRATE`, plus the `CATALOG`, `ENCOUNTER` and `FROMFILE ... ALL` forms of `MPMLOAD`/`MPOLOAD` and the `NOW`, `@host`, `=`, `%` and `^` forms of `MPSETVAR`.

## Control flow

Control-flow statements are lines like any other, but they structure the lines around them. They are looked up in the same table.

### IF

**Syntax**

```text
IF <condition>
  ...commands...
[ELSEIF <condition>
  ...commands...]
[ELSE
  ...commands...]
ENDIF
```

**Arguments:** the rest of the line is one condition, built from condition functions such as `ISPC($n)` or `VAR($n QUESTSTEP == 2)`, combined with `AND`, `OR`, `NOT` and parentheses. Every condition function and the comparison forms are in [28](28-mobprog-functions.md). Minimum: 1 word.

**Behaviour:** the condition is evaluated; if true the lines up to `ELSEIF`/`ELSE`/`ENDIF` run, otherwise the `ELSE` lines run. `IF` blocks nest; the engine pairs each `ELSE` and `ENDIF` with the nearest open `IF` (text inside a `<SCRIPT>` block is skipped when it counts them). A condition that cannot be parsed is logged (`IF Syntax: ...`) and ends the current program level. An `IF` with no `ENDIF` logs `IF Syntax: Without ENDIF!` and ends the program.

**Example**

```text
GREET_PROG 100
if ispc($n) and not hastattoo($n MET_GUARD)
  mptattoo $n MET_GUARD
  say Welcome, $n. First visit?
else
  say Back again, $n.
endif
~
```

**Notes**

- `ELSE` takes no text on its line (`ELSE something` logs `Decorated ELSE is now illegal!!`). Use `ELSEIF` or `ELSE IF` for another test.
- A `RETURN` or `BREAK` inside the block ends the whole program, not just the block.
- Write the comparison *inside* the function's parentheses: `VAR($n CHAPTER == 2)`, not `VAR($n CHAPTER) == 2`.

### ELSE

**Syntax:** `ELSE` on a line by itself, between `IF` and `ENDIF`.

**Behaviour:** starts the lines run when the `IF` condition was false. Only one `ELSE` per `IF` is meaningful. `ELSE` is *not* in the command table: outside an `IF` block it is passed to the game as a command and fails (SCRIPTCHECK reports `ELSE without IF`).

**Example:** see [IF](#if).

### ELSEIF

**Syntax:** `ELSEIF <condition>` (or `ELSE IF <condition>`). **CoffeeSUD.** Minimum: 1 word.

**Behaviour:** at load time `ELSEIF c` is rewritten to `ELSE` followed by `IF c`, and one extra `ENDIF` is added where the chain's own `ENDIF` is. So a whole chain needs a single `ENDIF`, and `ELSEIF` costs one nesting level.

**Example**

```text
SPEECH_PROG p price
if var($n BARGAIN == yes)
  say Ten coins, friend.
elseif var($n BARGAIN == no)
  say Twenty, and not a coin less.
else
  say Twenty-five.
endif
~
```

**Notes:** an `ELSEIF` with no open `IF` logs `ELSEIF Syntax: ELSEIF without IF`.

### ENDIF

**Syntax:** `ENDIF` on a line by itself. Closes the nearest open `IF`. A lone `ENDIF` logs `ENDIF Syntax: Without IF`.

### SWITCH

**Syntax**

```text
SWITCH <value>
CASE <value>
  ...commands...
CASE <value>
  ...commands...
DEFAULT
  ...commands...
ENDSWITCH
```

Minimum for `SWITCH`: 1 word.

**Arguments:** `SWITCH` takes one value (after `$` substitution, the whole rest of the line); each `CASE` takes one value (the rest of its line, after substitution).

**Behaviour:** the `SWITCH` value is compared, case-insensitively and as text, with each `CASE`. The lines under a matching `CASE` run, up to the next `CASE`, `DEFAULT` or `ENDSWITCH`; there is **no fall-through** into the next case. `DEFAULT` runs if no `CASE` *above it* matched. The engine caches the line numbers of constant cases, so a switch on a value is fast; a `CASE` that contains a `$` code is evaluated each time. `SWITCH` blocks nest. A missing `ENDSWITCH` logs `SWITCH Syntax: Without ENDSWITCH!` and ends the program.

**Example**

```text
SPEECH_PROG p color
switch $g
case red
  say Red is the colour of the guard.
case blue
  say Blue is the colour of the mage.
default
  say I do not know that colour.
endswitch
~
```

**Notes**

- Put `DEFAULT` last. A `DEFAULT` placed above a later matching `CASE` runs and then the `CASE` runs too.
- A `CASE` that appears twice with the same value runs both bodies.
- `SWITCH` is the tidy replacement for a long `ELSEIF` chain when all tests are equalities on one value.

### CASE

One branch of a [SWITCH](#switch): `CASE <value>`. Outside a `SWITCH` it logs `CASE Syntax: Without SWITCH`. Minimum: 1 word.

### DEFAULT

The fall-back branch of a [SWITCH](#switch): `DEFAULT` alone on a line. Outside a `SWITCH` it logs `DEFAULT Syntax: Without SWITCH`.

### ENDSWITCH

Closes a [SWITCH](#switch): `ENDSWITCH` alone on a line. Outside a `SWITCH` it logs `ENDSWITCH Syntax: Without SWITCH`.

### FOR

**Syntax**

```text
FOR $<n> = <from> TO <to>
FOR $<n> = <from> TO< <to>
FOR $<n> = <from> TO> <to>
FOR $<n> IN <list>
FOR $<n> IN LIST <text>
FOR $<n> IN #<tag>
FOR $<n> IN select: <MQL query>
  ...commands...
NEXT
```

Minimum: 4 words.

**Arguments**

| Part | Meaning |
| --- | --- |
| `$<n>` | the loop variable: `$0` to `$9` only. It must not already hold a number. |
| `= <from> TO <to>` | counts from `<from>` to `<to>` inclusive. Both must be whole numbers after substitution. Counts down if `<to>` is lower. |
| `TO<` / `TO>` | like `TO`, but the end value is moved by one: `TO<` stops one short of it, `TO>` goes one past it (the adjustment is added to the end value, so it flips for a descending loop). |
| `IN a,b,c` | loops over a comma-separated list; each item is `$`-substituted first. Quote an item with commas in it. |
| `IN LIST <text>` | the text is substituted *first*, then split on spaces (quoted words stay together) or on commas if there are any. The right form for a list kept in a variable: `FOR $0 IN LIST $<WORLD SUSPECTS>`. |
| `IN #<tag>` | loops over every object in the world tagged `<tag>` (see [MPTAG](#mptag)); `$<n>` is the object itself and works as a target. |
| `IN select: ...` | loops over the objects an MQL `select:` query returns (the first column of each row). |

**Behaviour:** the body (everything up to the matching `NEXT`) runs once per value with `$<n>` set to it. Loops nest; give each loop its own variable. When the loop ends `$<n>` is cleared. `BREAK` leaves the loop, `CONTINUE` goes to the next pass, `RETURN` ends the whole program. A loop that runs for more than 10 real seconds is cut off with a `For loop ... violates 10 second rule` error.

**Example**

```text
GREET_PROG 100
for $1 = 1 to 3
  mpecho Echo number $1 rings out.
next
~
```

```text
RAND_PROG 5
for $0 in #sunaltar
  mpecho $0 flickers.
next
~
```

**Notes**

- Errors: `'$x' is not a tmp var $1, $2..` (the variable is not `$` plus one digit), `'N' is already in use!` (a nested loop reused a live counter), `illegal range`, `5 parms required!`, `Without NEXT!`.
- `MPWAIT` cannot pause inside a loop body.
- The comma form of `IN` splits on commas *before* it substitutes, so a space-separated list in a variable loops once; use `IN LIST` for that.

### NEXT

Closes a [FOR](#for): `NEXT` alone on a line. A lone `NEXT` logs `NEXT Syntax: Without FOR`.

### MPWHILE

**Syntax:** `MPWHILE ( <condition> ) <command>`. Minimum: 1 word.

**Arguments:** a condition in parentheses (the same grammar as `IF`; the closing parenthesis must match the opening one), then exactly one command line.

**Behaviour:** while the condition is true the command is run again and again. The loop stops when the host is destroyed or when 4 real seconds have passed (it then logs `MPWHILE RunTime: cut off after 4 seconds, its condition still true`). To run several commands, make the command `MPCALLFUNC` of a `FUNCTION_PROG`, or use [WHILE](#while).

**Example**

```text
GREET_PROG 100
mpsetvar $i COUNT 0
mpwhile (var($i COUNT < 3)) mpsetvar $i COUNT ++
~
```

**Notes**

- The body runs as an anonymous function; `$n`, `$t` and the locals carry over.
- A missing `(` or `)` logs `MPWHILE Syntax: ...` and skips the line.
- `MPWAIT` cannot pause inside it.

### WHILE

**Syntax**

```text
WHILE ( <condition> ) [max passes]
  ...commands...
ENDWHILE
```

**CoffeeSUD.** Minimum: 1 word.

**Behaviour:** a block loop. While the condition is true the lines up to the matching `ENDWHILE` run. It stops after `max passes` passes (default 1000), logging `WHILE RunTime: stopped after N passes`. It is bounded by passes, not by real time (the real-time cap belongs to `MPWHILE`). `BREAK` leaves it, `CONTINUE` goes to the next pass, `RETURN` ends the program. `WHILE` blocks nest. A `WHILE` with no `ENDWHILE` logs `WHILE without ENDWHILE` and ends the program.

**Example**

```text
EVERY_PROG 1
mpsetvar $i WAVES 0
while (var($i WAVES < 3)) 10
  mpmload a skeleton
  mpsetvar $i WAVES ++
endwhile
~
```

**Notes:** `MPWAIT` cannot pause inside a loop. See also [FOR](#for).

### ENDWHILE

Closes a [WHILE](#while): `ENDWHILE` alone on a line. A lone `ENDWHILE` logs `ENDWHILE Syntax: ENDWHILE without WHILE`.

### BREAK

**Syntax:** `BREAK`. Takes no arguments (minimum 0; it ignores any).

**Behaviour:** ends the nearest enclosing `FOR` or `WHILE` loop. With no loop around it, ends the whole program (every nested `IF`/`SWITCH` level is left). The program ends as if it had returned nothing.

**Example**

```text
GREET_PROG 100
for $1 = 1 to 10
  if var($i FOUND == yes)
    break
  endif
next
~
```

**Notes:** `BREAK` is listed twice in the engine's table; both entries do the same thing.

### CONTINUE

**Syntax:** `CONTINUE`. **CoffeeSUD.**

**Behaviour:** ends the current pass of the nearest `FOR` or `WHILE` loop and starts the next. Inside an `IF` in the loop body it still reaches the loop. Outside any loop it ends the program quietly (it returns a marker that nothing consumes).

### RETURN

**Syntax:** `RETURN [text]`. Minimum: 0 words.

**Arguments:** optional text; `$` codes in it are substituted.

**Behaviour:** ends the whole program and gives `text` to whatever ran it. A `FUNCTION_PROG` hands the text to the caller (`$%CALLFUNC(name args)%`, `MPCALLFUNC` ignores it). For triggers that can veto an event (the *cancel* family such as `CNCLMSG_PROG`, `CMDFAIL_PROG`, `GAME_PROG SAVE`) the text `CANCEL` stops the event. For all other triggers the value is ignored. Using `RETURN` with no text just ends the program.

**Example**

```text
FUNCTION_PROG HEIGHT
return $<$i HEIGHT>
~

CNCLMSG_PROG DAMAGE
if hastag($t PROTECTED)
  return cancel
endif
~
```

**Notes:** a `RETURN` inside an `IF`, `SWITCH` or loop leaves the entire program, not only the block.

### \<SCRIPT\>

**Syntax**

```text
<SCRIPT>
  ...JavaScript...
</SCRIPT>
```

**Behaviour:** everything between the tags is run as JavaScript, with the script's context available (see [05](05-javascript.md) for the API). The functions defined in the block's scope are `host()`, `source()`, `target()`, `monster()`, `item()`, `item1()`, `item2()`, `message()`, `objs()`, `getVar()`, `setVar()`, `toJavaString()`, `getCMType()`, `debug()`, `cancel()`, `msg()`, `room()`, `findItem()`, `door()`, `transform()`, `signal()`, `state()` and `parm()`, plus `require()`, `include()` and `cm`. Whether the block runs depends on `JSCRIPTS` in `coffeemud.ini`: `2` (the CoffeeSUD default) runs every block, `1` runs only blocks approved in builder mode (`MODIFY JSCRIPTS`; an unapproved block shows the room `A Javascript was not authorized.  Contact an Admin to use MODIFY JSCRIPT to authorize this script.` and does not run), `0` never runs them. A JavaScript error is reported with the script file and line. A last value of `cancel`, or a call to `cancel()`, ends the program as `RETURN CANCEL` does. Inside the tags the parser does not split on `;`, `~` or newlines, so JavaScript is written normally; no backslash escapes are needed in a script file.

**Example**

```text
GREET_PROG 100
<SCRIPT>
  if (source().isPlayer()) { setVar(host(), 'LAST', source().name()); }
</SCRIPT>
~
```

**Notes**

- The tag must be on its own line. Text after `</SCRIPT>` on that line is ignored.
- For a longer JavaScript file, use [MPJSFILE](#mpjsfile).
- `IF`/`FOR` scanning skips over `<SCRIPT>` blocks, so JavaScript text that looks like `IF` or `NEXT` does not confuse the block counts.

## Messages and speech

Echo commands print text as a game message: the text is `$`-substituted, then shown with a normal "action" message type, so it respects blindness, sleeping and so on for each viewer (a blind character does not read a visual echo). Start the text with `NOSOUND ` to make it a *visual-only* message (a sight with no sound, so a deaf viewer still gets it); `NOSOUND` is removed from the text. Echoes appear for the player as plain lines; use `^` colour codes in the text as elsewhere in the game.

### MPECHO

**Syntax:** `MPECHO <text>`. Minimum: 1 word.

**Arguments:** `<text>`, the rest of the line (taken raw, so apostrophes stay).

**Behaviour:** shows the text to everyone in the host's room, as an action by the mob the script runs as. It is the usual way to narrate. For an item, exit, room or area host the room is the location the host was last in.

**Example**

```text
GREET_PROG 100
mpecho The old bell tolls once, as it has not in a hundred years.
~
```

**Notes**

- To address one person, use [MPECHOAT](#mpechoat); to leave one person out, [MPECHOAROUND](#mpechoaround).
- `MPECHO NOSOUND The lamp flickers.` is seen but not heard.
- Text that begins with a game verb is still just text: `MPECHO` never runs commands.

### MPECHOAT

**Syntax:** `MPECHOAT <target> <text>`. Minimum: 2 words.

**Arguments**

| Argument | Meaning |
| --- | --- |
| `<target>` | a mob or player (name or `$` code; quote multi-word names), or one of: a room ID, an area name, `AREA`, `WORLD` |
| `<text>` | the rest of the line (raw) |

**Behaviour:** shows the text to the target only, as an action by the script's mob. If the target is a room ID the whole room sees it; an area name echoes to every room of that area (and its sub-areas) that has anyone in it; `AREA` means the host's area; `WORLD` every room that has anyone in it. If the host's own room is empty, an area or world echo is also delivered to that room so the host does not go silent. With party windows off, an echo to a party member in the background is also shown at the terminal, with the member's name in front.

**Example**

```text
GIVE_PROG all
mpechoat $n The smith weighs the coin in his hand, then looks away.
~

RAND_PROG 1
mpechoat AREA A distant horn sounds.
~
```

**Notes**

- Others in the room do **not** see the text. Triggers like `ACT_PROG` in the target's room still match the message (the echo is a real message).
- A target that does not resolve is treated as a room or area name; if that fails nothing is shown.
- `MEA` is a built-in short name for the same command.

### MEA

`MEA <target> <text>` is `MPECHOAT` under a shorter name (a real table entry, not a prefix). Same arguments and behaviour. Minimum: 2 words.

### MPECHOAROUND

**Syntax:** `MPECHOAROUND <mob> <text>`. Minimum: 2 words.

**Arguments:** `<mob>` a mob or player (name or `$` code); `<text>` the rest of the line.

**Behaviour:** shows the text to everyone in the mob's room *except* that mob. Pair it with an `MPECHOAT` to give the target a different line:

```text
mpechoat $n You feel a chill as the ghost touches you.
mpechoaround $n $n shivers suddenly.
```

**Notes:** `NOSOUND` works as in `MPECHO`. The mob does not need to be in the host's room: the echo goes to the room the mob is in. `MER` is the short name.

### MER

`MER <mob> <text>` is `MPECHOAROUND` under a shorter name. Minimum: 2 words.

### MPASOUND

**Syntax:** `MPASOUND <text>`. Minimum: 1 word.

**Behaviour:** shows the text in every room next to the host's room whose connecting exit is **open** (a closed door blocks it). The host's own room does not see it. Use it for shouts, gongs and roars that carry.

**Example**

```text
DEATH_PROG
mpasound A bloodcurdling howl echoes from somewhere nearby.
~
```

### MPSPEAK

**Syntax:** `MPSPEAK <mob> <language>`. Minimum: 2 words.

**Arguments:** `<mob>`, the creature that starts speaking; `<language>`, a language ID or name (for example `Dwarven`).

**Behaviour:** the mob begins speaking that language (as if it had used the language's skill) at full proficiency, which makes its later `SAY`s come out in that language and be garbled for listeners who do not know it. An NPC is given proficiency 100 in it.

**Example:** `mpspeak $i Elvish` in an `ONCE_PROG`, so the host's `say` lines are in Elvish.

**Notes:** the language must be a language ability; otherwise nothing happens. To speak one line in a language and then switch back, use [MPSAYIN](#mpsayin). To teach a player a language, use [MPLEARNLANG](#mplearnlang).

### MPSAYIN

**Syntax:** `MPSAYIN <language> <text>`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** the script's mob says one line in the language, then goes back to the language it was speaking. `Common` is allowed. An unknown language logs `MPSAYIN RunTime: Unknown language X` and says nothing. Listeners hear garbled text unless they know the language (or have a translator); see `NOTUNDERSTOOD_PROG` in [26](26-mobprog-triggers.md) and `UNDERSTANDS()` in [28](28-mobprog-functions.md).

**Example:** `mpsayin Elvish Mae govannen, mellon.`

### MPCHANNEL

**Syntax:** `MPCHANNEL [!]<channel> <text>`. Minimum: 2 words.

**Behaviour:** posts the text on the named channel as if the script's mob had spoken it. A leading `!` sends it as a *system* message (no speaker). An unknown channel logs `MPCHANNEL Syntax: Unknown channel`.

**Notes:** CoffeeSUD keeps only the channels the world speaks on, `INFO` and `QUESTCHAT` (see the `CHANNELS` setting in `coffeemud.ini`), so this is mostly useful for announcing news on `INFO`: `mpchannel !INFO The bridge has fallen.` There is no one else to hear a chat channel.

### MPMSG

**Syntax**

```text
MPMSG VALUE <n | +n | -n | *x | /x>
MPMSG TEXT <S|T|O> <text | NULL>
MPMSG TARGET <thing>
```

**CoffeeSUD.** Minimum: 2 words.

**Behaviour:** changes the *message under judgement*. It works only in `CNCLMSG_PROG`, which holds the live message (`EXECMSG_PROG` holds only a copy, so changes there do nothing useful); elsewhere it logs `MPMSG RunTime: there is no message here: MPMSG works in CNCLMSG_PROG`.

| Form | Effect |
| --- | --- |
| `VALUE n` or `VALUE =n` | sets the message's value (damage, experience ...) to n |
| `VALUE +n` / `-n` | adds / subtracts |
| `VALUE *x` | multiplies (rounded) |
| `VALUE /x` | divides (rounded; division by 0 does nothing) |
| `TEXT S text` | replaces the source's own message text (`S`, `T`, `O`: source, target, others) |
| `TEXT T text`, `TEXT O text` | replaces the target's / the others' text; `NULL` removes it |
| `TARGET thing` | redirects the message to another target |

**Example**

```text
CNCLMSG_PROG DAMAGE
if hastag($t FROSTSHRINE)
  mpmsg value *0.5
endif
~
```

**Notes:** read the message with the `MSG()` functions in [28](28-mobprog-functions.md). Changes made here apply to the message before it takes effect.

## Movement and placement

### MPGOTO

**Syntax:** `MPGOTO <room>`. Minimum: 1 word.

**Arguments:** `<room>` a room ID (`Midgaard#3001`), a number or `#N` (a room of the host's area), a `$` code (the room that thing is in), or a name searched in the area and then the world.

**Behaviour:** moves the host to the room at once, with no leave or arrive message. A mob host is moved with its followers; an item host is moved (with what it holds). A room, exit or area host cannot move; only its stand-in mob goes and is removed again, so the command does nothing visible. After a successful move the host's "where am I" is the new room, so later lines in the same program (`MPECHO` and the rest) act there.

**Example**

```text
FUNCTION_PROG HOME
mpgoto Midgaard#3001
~
```

**Notes**

- To go and come back in one command line use [MPAT](#mpat). To move *another* creature use [MPTRANSFER](#mptransfer).
- A name that matches several rooms picks one at random: prefer IDs. `SCRIPTCHECK` warns when a literal ID does not exist.

### MPAT

**Syntax:** `MPAT <room> <command>`. Minimum: 2 words.

**Arguments:** `<room>` as for `MPGOTO` (quote it if it has spaces); `<command>` the rest of the line, one script line.

**Behaviour:** the mob the script runs as steps into the room, the command line runs there, and the mob steps back. The command may be any script line, including an `MP*` command or a game command; `MPAT Midgaard#3001 mpecho The ground shakes.` shows the echo in that room. If the room is the current room the command just runs.

**Example**

```text
SPEECH_PROG p alarm
mpat Keep#12 mpecho The alarm bell rings out across the courtyard.
~
```

**Notes**

- Only one command per `MPAT`. For several, make a `FUNCTION_PROG` and write `MPAT <room> MPCALLFUNC name`.
- For a non-mob host the stand-in is moved and removed afterwards.
- For a notification that must wake scripts elsewhere, [MPSIGNAL](#mpsignal) is cleaner.

### MPTRANSFER

**Syntax:** `MPTRANSFER <who> <room>`. Minimum: 2 words. (Left empty by a `$` code that expands to nothing, `<room>` means the host's current room.)

**Arguments**

| Argument | Meaning |
| --- | --- |
| `<who>` | a `$` code (mob or item), `ALL` (everyone in the host's room except the host), or a name: tried in the host's room, then among players, then through the rest of the host's area |
| `<room>` | as for `MPGOTO`; empty means the host's current room |

**Behaviour:** moves the creature to the room with a full enter/leave message sequence (blank text, so the room sees nothing unless you echo), looks at the new room for it, takes it out of combat (a fighting mob flees to nowhere and combat ends), and wakes it from sitting and sleeping for the move. Members of the creature's group who are in the same room come along. An item is moved to the room (with a player who holds it only if the item itself is the target). If the creature cannot leave (a spell or an effect forbids it), the move is skipped for it.

**Example**

```text
GREET_PROG 100
if ispc($n)
  mpechoat $n The floor drops away beneath you!
  mptransfer $n Dungeon#1
endif
~
```

**Notes**

- Quote `<who>` when it has spaces: `mptransfer 'a guard' Keep#12`.
- `MPTRANSFER $i <room>` moves the host itself (equivalent to `MPGOTO` but with messages).

### MPBEACON

**Syntax:** `MPBEACON <room> <ALL|name>`. Minimum: 1 word.

**Behaviour:** sets the *start room* of the player(s) in the host's room: `ALL` for every player there except the host, or a name for one. A creature that dies or is recalled returns to its start room. Monsters are unaffected. An unknown room logs `MPBEACON RunTime: X is not a room`.

**Example:** `mpbeacon Inn#1 $n` (the inn becomes the resting place of the player that triggered the script).

**Notes:** see `MPSETRESPAWN` for the finer respawn point used by `Prop_Checkpoint`.

### MPPUT

**Syntax:** `MPPUT <thing> <destination>`. Minimum: 2 words.

**Arguments:** `<thing>`, an item or mob; `<destination>` a container, a room, a mob, a rideable thing, or `NULL`/`NONE`.

**Behaviour:** silently places the thing:

- item into a container: the item is moved to the container's holder first, then into the container;
- item to a room or a mob: placed in the room or the mob's inventory;
- a mob (or item) onto a rideable destination: starts riding it;
- `NULL` or `NONE`: takes an item out of its container and off whatever it rides.

No message is shown.

**Example:** `mpput $o NULL` takes the item out of its container; `mpput $o chest` puts it in a chest in the room.

### MPTRACKTO

**Syntax:** `MPTRACKTO <target>`. Minimum: 1 word.

**Behaviour:** orders the script's mob to track *target* using the `Skill_Track` ability (so it finds its own way, opening doors as the skill does). The text is passed to the skill as a person would type it, so a name, an area name or room ID works. Does nothing if the mob is already tracking the same thing.

**Example:** `mptrackto $n` in a `COMBAT_PROG` to chase the player.

### MPWALKTO

**Syntax:** `MPWALKTO <target>`. Minimum: 1 word.

**Behaviour:** like `MPTRACKTO` but restricted to land (`LANDONLY`), so the mob will not swim, fly or sail.

### MPLINK

**Syntax:** `MPLINK <direction> <room> <exitclass> [name]`. Minimum: 3 words.

**Arguments:** direction (`north`, `up`, ...), the target room (same area as the host), an exit class ID (for example `Open`; any exit class from the exit list), and an optional name for the exit.

**Behaviour:** makes an unsaved one-way exit from the host's room in that direction to the room, if the host's room has no exit that way. The exit is not saved with the world, so a reload removes it. It works only while the host is in its home area (`MPLINK from X is an illegal source room` otherwise) and the target room must be in that same area. A missing direction, class or room is logged.

**Example:** `mplink east Cave#12 Open a crack in the wall` (an exit named "a crack in the wall").

**Notes:** the exit class and the name are the rest of the line after the room. For a lasting exit change the world data; see [20](20-areas-rooms-exits.md).

### MPUNLINK

**Syntax:** `MPUNLINK <direction>`. Minimum: 1 word.

**Behaviour:** removes an exit that `MPLINK` made in the host's room. It refuses (and logs an error) for a saved exit, for a direction that leads to another area, and for an unknown direction.

### MPSETRESPAWN

**Syntax:** `MPSETRESPAWN <who> <room | HERE | CLEAR>`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** sets (or clears) a character's respawn point for `Prop_Checkpoint` and the `DEATH=CHECKPOINT` death rule. `HERE` is the host's room; a room ID must exist and have an ID. See [29](29-combat-and-encounters.md) and [23](23-properties-reference.md#prop_checkpoint).

**Example:** `mpsetrespawn $n HERE`

## Loading and removing things

### How `MPMLOAD`, `MPOLOAD` and the shop loaders find what to make

`MPMLOAD`, `MPOLOAD`, `MPOLOADROOM`, `MPMLOADSHOP` and `MPOLOADSHOP` all resolve their first argument the same way (the text after the command name, `$`-substituted):

| Form | Source |
| --- | --- |
| `100 gold`, `5` , a number with an optional currency name | (items only) a pile of coins of that amount |
| a Java class ID such as `GenItem`, `StdMOB` | a fresh blank instance of that class |
| `CATALOG [FORCE] <name> [count]` or `CATALOG:<name>` | **CoffeeSUD**: copies of the catalog's mob or item (`CATALOG` command, `world/catalog.cmare`). `count` (1-100) makes several. Copies are counted by the catalog: none is made past the entry's cap unless `FORCE`. |
| `FROMFILE <file> <name \| ALL \| ANY>` | a mob or item from an XML file of exported mobs or items; `ALL` loads every one in the file (**CoffeeSUD** fixed `ALL`), `ANY` a random one |
| `FROMGENFILE <file> <name \| ALL \| ANY> <tag> [VAR=value ...] [FRESH]` | generated from a random-generation (MUDPercolator) XML file's `<tag>`; the result is kept after the first call unless `FRESH` asks for a new roll each time (see [12](12-generation.md)) |
| `ENCOUNTER <table>` (`MPMLOAD` only) | **CoffeeSUD**: rolls an encounter table here (see [29](29-combat-and-encounters.md)); `$b` is the leader |
| anything else | a name: searched among the things in the room, the host and the actor, then the area, then the whole world; one match is picked at random, and the thing found is **copied** |

The thing loaded becomes `$b` (and `$B` for its display text) for the rest of the program. If several copies are made (`CATALOG ... 3`, `FROMFILE ... ALL`) `$b` is the last.

Items that are `ArchonOnly` are never loaded by a script. A catalog copy made past a cap is skipped silently.

### MPMLOAD

**Syntax:** `MPMLOAD <mob>`. Minimum: 1 word.

**Behaviour:** makes a copy of the mob and places it in the host's room, alive. The copy's start room is cleared when the template lives in a room (so the copy is not a resident there: when it dies it does not come back by rejuvenation). `$b` is the new mob.

**Example**

```text
HITPRCNT_PROG 50
mpecho The chieftain bellows for his guard!
mpmload catalog goblin guard 2
mpforce $b kill $n
~
```

**Notes**

- A name that is found in the world is copied from wherever it is. For predictable results use a catalog name, a `FROMFILE` or an exact class.
- `MPFORCE $b ...` commands the new mob; `MPKILL`/`MPFORCE` can send it after the actor.
- The prototype search can pick a mob of the same name in another area; that copy's equipment and effects come with it.

### MPOLOAD

**Syntax:** `MPOLOAD <item> [INTO <container>]`. Minimum: 1 word.

**Behaviour:** makes a copy of the item and gives it to the host: into a mob host's inventory; into the holder's inventory for an item host (or the floor of its room); onto the floor of a room host's room; onto the floor of the room for exit and area hosts. With `INTO <container>` the copy is put in that container: either a `$` code (`INTO $o`), or the name of a container in the host's room or inventory that has room (capacity > 0). Gold is loaded as coins. The copy becomes `$b`.

**Example**

```text
GIVE_PROG all
mpoload a rusty key
mpoload 25 gold
mpoload a scroll of fire INTO chest
~
```

**Notes**

- A mob host that loads a weapon or armour holds it in its pack; use `MPFORCE $i wear <item>` or `wield <item>` to equip it.
- `MPOLOAD CATALOG the Sunblade` does nothing while the one Sunblade the catalog allows exists. `CATALOG FORCE` ignores the cap.
- `MPOLOAD` fills the *host's* own hands. To give the copy to a player follow it with `MPFORCE $i give $b $n`.

### MPOLOADROOM

**Syntax:** `MPOLOADROOM <item> [INTO <container>]`. Minimum: 1 word.

**Behaviour:** like `MPOLOAD` but always puts the copy on the floor of the host's room (or in a container there). Coins are dropped as a pile and a raw-material pile is rebundled. `$b` is the copy.

**Example:** `mpoloadroom a glowing orb` in a room host's `ONCE_PROG`.

**Notes:** `SCRIPTCHECK` checks that a literal room ID in `MPOLOADROOM`'s second word exists.

### MPOLOADSHOP

**Syntax:** `MPOLOADSHOP <item> [PRICE=<n>]`. Minimum: 1 word.

**Behaviour:** finds the shopkeeper (the host if it is one, else the shopkeeper that holds the host, else a shopkeeper in the room), makes a copy of the item (catalog caps apply), and adds one to its store. `PRICE=n` fixes the price; without it the shop's usual price rules apply. If there is no shopkeeper the command does nothing.

**Example:** `mpoloadshop a healing potion PRICE=40`

**Notes:** see [MPSHOP](#mpshop) for stocking counts, removing and re-pricing later.

### MPMLOADSHOP

**Syntax:** `MPMLOADSHOP <mob> [PRICE=<n>]`. Minimum: 1 word.

**Behaviour:** like `MPOLOADSHOP`, but stocks a *mob* (a pet or a mount for sale, or a hireling's listing). The mob is added to the store's inventory as an item for sale; the copy itself is then discarded.

### MPGENERATE

**Syntax**

```text
MPGENERATE ITEM  <file> <id> [VAR=value ...] [INTO <container> | TO <mob>] [SEED=n]
MPGENERATE MOB   <file> <id> [VAR=value ...] [AT <room>] [SEED=n]
MPGENERATE ROOM  <file> <id> <direction> [VAR=value ...]
MPGENERATE AREA  <file> <id> <direction> [VAR=value ...] [SEED=n]
MPGENERATE QUEST <file> <id> [VAR=value ...]
```

**CoffeeSUD.** Minimum: 3 words.

**Arguments:** the kind; the random-generation file; the template ID inside it; any number of `NAME=value` variables (they define variables of the template; quote values with spaces); where to put the result. `PLAYER_LEVEL`, `PARTY_SIZE`, `AREA_LEVEL`, `AREA_THEME`, `HOUR`, `SEASON`, `WEATHER` and `GAME_DAY` are defined for the template unless you give them.

**Behaviour:** generates a fresh roll each time from a MUDPercolator template (see [12](12-generation.md)):

- `ITEM`: the items go into the container given by `INTO`, into the mob given by `TO`, or on the floor of the host's room.
- `MOB`: the mobs are brought to life in the host's room, or at the room given by `AT`.
- `ROOM`, `AREA`: a new room or area is generated and linked to the host's room in a free direction (`<direction>` is required).
- `QUEST`: generates and starts the quest.

`$b` is the first thing made. Errors in the file or ids are logged as `MPGENERATE RunTime: ...`.

**Example**

```text
SPEECH_PROG p search
mpgenerate ITEM loot.xml treasure RARITY=rare TO $n
~
```

**Notes:** `SEED=n` repeats a result. To re-roll an existing area, use [MPREGENERATE](#mpregenerate).

### MPTRANSFORM

**Syntax:** `MPTRANSFORM <item> <name> [KEEP=<list>]`. **CoffeeSUD.** Minimum: 2 words.

**Arguments:** the item to replace (target); the new item's name, class ID or catalog name; `KEEP=` a comma list of `STATE`, `TAGS`, `USES`, `CONTAINER`, `WORN`, or `ALL` / `NONE` (default: `CONTAINER,WORN`).

**Behaviour:** replaces the item with another in the same place (held by the same creature or on the same floor). `KEEP` chooses what carries over: script variables and states (`STATE`), tags, remaining uses, the container it was in, where it was worn. Whatever was inside it stays inside. The new item is `$p` and `$b` for the rest of the program.

**Example:** `mptransform $o a broken lever KEEP=TAGS` when the lever is thrown.

**Notes:** If no item by that name can be built or found, it logs `MPTRANSFORM RunTime: no item called ...`. JavaScript has `transform(item, name, keep)`.

### MPM2I2M

**Syntax:** `MPM2I2M <mob | caged item> [<item name> [<item display text>]]`. Minimum: 3 words in `SCRIPTCHECK`'s table, though one is enough at run time (repeat the target, `mpm2i2m $t $t $t`, to silence the warning; a name equal to the target is ignored).

**Behaviour:** turns a mob into a caged-animal item on the floor (the mob dies as the item appears), or turns a caged item back into the mob in the room (and destroys the item). Optional names for the new item. For a target that is neither, it logs `MPM2I2M RunTime: X is not a mob or a caged item.`

**Example:** `mpm2i2m $t` to bottle a creature; `mpm2i2m $o` to release it.

**Notes:** the item class is `GenCaged`; it is not limited to animals.

### MPJUNK

**Syntax:** `MPJUNK <item | ALL>`. Minimum: 1 word.

**Behaviour:** destroys the item the host holds (by name or `$` code), or one on the floor if the host is a room. `ALL` destroys everything in the mob's inventory. Nothing is shown. The destruction runs `DESTROY_PROG` triggers with `$g` set to `JUNKED` (see [26](26-mobprog-triggers.md)).

**Example:** `mpjunk $o` in a `GIVE_PROG` where the mob eats the gift.

### MPPURGE

**Syntax**

```text
MPPURGE <target>
MPPURGE ROOM <name>
MPPURGE MY <name>
MPPURGE SELF | ME
```

Minimum: 0 words (with none, nothing happens).

**Behaviour:** destroys the target. `ROOM <name>` searches the room's contents, items first; `MY <name>` searches only the host's inventory; `SELF`/`ME` destroys the host itself. An item is destroyed outright. A mob with a start room is killed (it can rejuvenate); one without is destroyed. A player target is sent back to its start room (and its session is stopped): do not purge the player.

**Example:** `mppurge $o` (the item just given), `mppurge self` at the end of a one-shot visitor's script.

**Notes:** after `MPPURGE SELF` the program continues until it ends; avoid commands that need the host. To remove a loaded copy later use `$b`.

### MPSLAY

**Syntax:** `MPSLAY <mob>`. Minimum: 1 word.

**Behaviour:** kills the target at once with the host as the killer, with the usual death handling (corpse, experience, `DEATH_PROG` and the death rules). Only mobs and players; items are ignored.

**Example:** `mpslay $n` after a trap closes. Use [MPDAMAGE](#mpdamage) with `KILL` for a hit that the target's saves and armor can still affect.

### MPRLOAD

`MPRLOAD` is in the command table but has no implementation: running it logs `MPRLOAD Syntax: MPRLOAD is not a command.` and does nothing. It is listed so that a script that uses it is told so instead of running a game command named `MPRLOAD`. Use [MPMLOAD](#mpmload) or [MPOLOADROOM](#mpoloadroom).

## Doors, exits and visibility

The four `MPOPEN`/`MPCLOSE`/`MPLOCK`/`MPUNLOCK` commands change **one side** of a door, silently, and do not start the door's relock timer. A door is a real two-sided object (a room's exit one way and the neighbour's the other), so after an `MPOPEN` the other side may still look shut; `MPDOOR` changes both sides and says what happened.

Name the exit with an exit code: `$xn` is the north exit of the host's room (`$xs`, `$xe`, `$xw`, `$xu`, `$xd` and so on), or use the name of a container.

### MPOPEN

**Syntax:** `MPOPEN <exit | container>`. Minimum: 1 word.

**Behaviour:** opens a door or container that has a door, and clears any lock on it (the engine sets it open and unlocked). Does nothing for a target without a door.

**Example:** `mpopen $xn` in a `FUNCTION_PROG` that a lever calls.

### MPCLOSE

**Syntax:** `MPCLOSE <exit | container>`. Minimum: 1 word.

**Behaviour:** closes an *open* door or container; the result is closed and unlocked. Does nothing if it is not open or has no door.

### MPLOCK

**Syntax:** `MPLOCK <exit | container>`. Minimum: 1 word.

**Behaviour:** closes **and** locks a door or container that has a lock. Does nothing if it has no lock.

### MPUNLOCK

**Syntax:** `MPUNLOCK <exit | container>`. Minimum: 1 word.

**Behaviour:** unlocks a *locked* door or container and leaves it closed. Follow with `MPOPEN` to open it.

**Example**

```text
SPEECH_PROG p open sesame
mpopen $xn
mpecho The stone door grinds open.
~
```

### MPDOOR

**Syntax:** `MPDOOR <OPEN|CLOSE|LOCK|UNLOCK|TOGGLE> <room|HERE> <direction|container> [QUIET | "message"]`. **CoffeeSUD.** Minimum: 2 words.

**Arguments**

| Argument | Meaning |
| --- | --- |
| verb | `OPEN`, `CLOSE`, `LOCK`, `UNLOCK` or `TOGGLE` (open if shut, close if open) |
| room | a room ID, `HERE` (the host's room), or a `$` code (that thing's room) |
| what | a direction word, or a container's name (double quotes allowed for a name with spaces) |
| message | `QUIET` for none, or text in double quotes; `<T-NAME>` stands for the door. By default the door's own words (`The door opens.`) |

**Behaviour:** changes the door on **both sides** from anywhere, as a character would, showing the message in both rooms. Starts the door's usual relock timer. A door already in the wanted state is left alone silently. A container in the room works too. Errors: `no such room`, `no door or container called X`, `it has no door`, `it has no lock`.

**Example**

```text
SIGNAL_PROG lit
mpdoor OPEN Keep#14 north "The sanctum door grinds open."
~
```

**Notes:** the single-side commands above are useful when you want only one side changed.

### MPHIDE

**Syntax:** `MPHIDE <mob | item | exit>`. Minimum: 0 words.

**Behaviour:** marks the target "not seen": it is undetectable by any means (not the same as the Hide skill, which can be spotted). It does not appear in room listings. Use it to hide a prop until a script reveals it.

**Example:** `mphide $o` in `ONCE_PROG`, `mpunhide $o` in the trigger that reveals it.

### MPUNHIDE

**Syntax:** `MPUNHIDE <mob | item | exit>`. Minimum: 0 words.

**Behaviour:** reverses `MPHIDE`. It does not affect the Hide skill or other hiding effects.

## Variables, lists, tags and state

Script variables are text values kept under a **host** (an owner name) and a **variable name**, in a *scope* (a store). Names are not case sensitive (stored upper-case). Read them with `$<HOST VAR>` in text and `VAR(HOST VAR == x)` in conditions ([28](28-mobprog-functions.md)). An empty value *deletes* the variable.

The host word can be:

| Host | Meaning |
| --- | --- |
| a `$` code (`$n`, `$i`, `$t`, `$0` ...) | the **name** of that object. All mobs with the same name share their variables ("a city guard" is one host). A room code gives the room's ID. |
| a literal word (`WORLD`, `FIXTEST`, `Gatehouse`) | a free-form owner name, shared by whoever uses the same word |
| `@<code>` (for example `@$i`) | **CoffeeSUD**: the *object itself*: values live on that one object (in its extra data), move and die with it, and are saved with it. A mob's are cleared when it comes back to life unless the variable `PERSIST` is set. |
| `*` | every host (for `MPSAVEVAR`, `MPLOADVAR`, and reads) |

The scope is chosen by `MPSETINTERNAL SCOPE`: the default shared store, a named store shared by the scripts that name it, or a store of the script's own (`INDIVIDUAL` or `*`). Saving rules are in [Persistence notes](#persistence-notes). `MPSETINTERNAL VARHOST INSTANCE` makes every bare `$` code mean `@code` in that script.

### MPSETVAR

**Syntax:** `MPSETVAR <host> <variable> <value>`. Minimum: 2 words (a missing value clears the variable).

**Arguments:** the host; the variable name (one word, `$`-substituted); the value (the rest of the line, `$`-substituted, so `$<...>` and `$%...%` work in it).

**Behaviour:** sets the variable, with an operator form chosen by the **first character** of the value:

| Value | Effect |
| --- | --- |
| `++` / `--` | add / subtract one (an unset variable counts as 0) |
| `+n` / `-n` | add / subtract the whole number n |
| `*n` / `/n` | multiply / integer-divide (division by 0 is logged and leaves the value) |
| `%n` | remainder after dividing by n (**CoffeeSUD**) |
| `^n` | raise to the power n (**CoffeeSUD**) |
| `=text` | the text exactly as written; the way to store `-5` or `*x` literally (**CoffeeSUD**) |
| `NOW` | the area clock's current stamp, for `ELAPSED()` (**CoffeeSUD**) |
| empty | removes the variable |
| anything else | the text is stored as is |

Arithmetic is on whole numbers (64-bit). An operator whose operand is not a number leaves a numeric variable unchanged.

**Example**

```text
SPEECH_PROG p bribe
mpsetvar $i BRIBED 1
mpsetvar $n GOLD_GIVEN +10
mpsetvar WORLD SUSPECTS cook butler gardener
mpsetvar @$i VISITS ++
~
```

**Notes**

- `MPSETVAR $i X 1` is shared by every mob of the same name; use `@$i` for a variable per object.
- Changing a variable runs `VAR_PROG` triggers and, if a trace is on, shows in the trace (see [26](26-mobprog-triggers.md) and [13](13-testing-release.md)).
- For lists use [MPLIST](#mplist); for per-object flags without a value use [MPTAG](#mptag).

### MPSAVEVAR

**Syntax:** `MPSAVEVAR <host> <variable | *>`. Minimum: 2 words.

**Behaviour:** writes the variable's current value to the game's local data store, so it survives where the in-memory store would not. `*` writes every variable of the host. A variable with no value deletes its record. For a player the record goes with that character's store.

**Example:** `mpsavevar $n KILLS`

**Notes:** `MPLOADVAR` reads it back. In CoffeeSUD the saved game already keeps script variables of the supported scopes (see [Persistence notes](#persistence-notes)); `MPSAVEVAR` is rarely needed.

### MPLOADVAR

**Syntax:** `MPLOADVAR <host> <variable | *>`. Minimum: 2 words.

**Behaviour:** reads the saved value(s) of the host's variable (or all, with `*`) from the local data store into the script variables, replacing the current value.

### MPLIST

**Syntax:** `MPLIST <host> <variable> <ADD|ADDUNIQUE|DEL|CLEAR|SHUFFLE|SORT> [words]`. **CoffeeSUD.** Minimum: 3 words.

**Behaviour:** changes a list kept in a variable. A list is text of words separated by spaces, or by commas if any comma is present; a word with spaces is quoted. The operations: `ADD` appends the words; `ADDUNIQUE` appends those not present (case-insensitive); `DEL` removes matching words; `CLEAR` empties it; `SHUFFLE` randomises the order; `SORT` sorts it (case-insensitive). An unknown operation logs `MPLIST Syntax: unknown change X`.

**Example**

```text
ONCE_PROG
mplist WORLD SUSPECTS CLEAR
mplist WORLD SUSPECTS ADD cook butler gardener
mplist WORLD SUSPECTS SHUFFLE
~

GREET_PROG 100
for $0 in list $<WORLD SUSPECTS>
  mpecho Suspect: $0
next
~
```

**Notes:** `FOR $0 IN LIST <text>` loops over a list, and `LISTJOIN`, `LISTHAS`, `LISTCOUNT`, `LISTGET`, `LISTFIND` and `LISTPICK` read it ([28](28-mobprog-functions.md)).

### MPLOCAL

**Syntax:** `MPLOCAL <name> <value>`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** sets a named local, read as `$(name)`, that lasts while the program (and the blocks and functions it calls) runs. It is not saved. `FUNCTION_PROG name PARAM1 PARAM2` binds the words of a call to `$(PARAM1)` and `$(PARAM2)` the same way (the last takes the rest of the words).

**Example**

```text
FUNCTION_PROG HEAL WHO AMOUNT
mpheal $(WHO) $(AMOUNT) $(AMOUNT)
~
```

### MPARGSET

**Syntax:** `MPARGSET <$x> <value>`. Minimum: 2 words.

**Arguments:** `<$x>` is one of `$0`..`$9`, `$n`, `$t`, `$i`, `$o`, `$p`, `$b`, `$d`, `$g` (a dollar sign and one character). The value is a `$` code or a name (it is resolved to a mob, then an item), a double-quoted string `"text"`, or any text; the word `null` clears a numbered slot.

**Behaviour:** rebinds one of the script's event slots for the rest of the program: `MPARGSET $n $r` makes the "actor" a random player, `MPARGSET $0 "red"` stores a string in `$0`. It writes no *variables*: a slot lasts only while the program runs. `$i` rebinds the host (and the mob it acts as); `$g` takes text (a room becomes its ID).

**Example**

```text
GREET_PROG 100
mpargset $0 $r
mpechoat $0 Someone here has been chosen.
~
```

**Notes:** `$q` is accepted and ignored. Any other code logs `Invalid argument var`.

### MPSTATE

**Syntax:** `MPSTATE <thing> <name> <value | CLEAR>`. **CoffeeSUD.** Minimum: 3 words.

**Behaviour:** keeps a value on that one mob or item, the same store as `MPSETVAR @<thing> <name> <value>`; `CLEAR` removes it. Read it with `STATE(<thing> <name>)`. It refuses rooms.

**Example:** `mpstate $o CHARGES 3`

### MPTAG

**Syntax:** `MPTAG <target> <ADD|DEL|TOGGLE> <tag>`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** gives the target a tag, takes it off, or toggles it. Tags are cheap flags kept on the object (mob, item, room, exit) and saved with it. Test with `HASTAG(<target> <tag>)`, count with `NUMTAGGED`, loop with `FOR $0 IN #<tag>`, signal with `MPSIGNAL TAG:<tag>`.

**Example**

```text
GIVE_PROG all
mptag $i GRATEFUL ADD
~
```

### MPSET

**Syntax:** `MPSET <target> <stat> <value>`. Minimum: 3 words.

**Arguments:** a mob, item, exit, room or area; a stat name (upper-cased by the engine; see below); the value (rest of line, `++` and `--` mean add/subtract one).

**Behaviour:** sets a stat on the object, then recomputes its derived stats. A mob's stat is looked for in order: the object's own stat codes (the same list `STAT` shows), the character stats (`STRENGTH`, `RACE`, `GENDER` ...), the current state (`HITPOINTS`, `MANA`, `MOVEMENT`), the base physical stats (`LEVEL`, `WEIGHT`, `ARMOR`, `DAMAGE`, `SPEED`, `ATTACK` ...), player stats, `BASE<state>` and `MAX<state>` forms for the base and maximum state, and `STINK`; then the generic mob codes. For an item: its own stat codes and the generic item codes. Setting `LEVEL` re-fills the mob's level-based stats; setting `RACE` runs the race's start-up. Players changed this way are written to the log. An unknown stat logs `MPSET Syntax: Unknown stat: X for Y`.

**Example**

```text
DEATH_PROG
mpset $i NAME a restless spirit
mpset $i LEVEL ++
~
```

**Notes**

- Use with care; it can break an object. `MPSET` is the standard-stat command; [MPGSET](#mpgset) reaches the fields of generic mobs and items (`MODIFY` fields).
- To add a behaviour or effect, use `MPBEHAVE` and `MPAFFECT`. `MPSET $i AFFBEHAV` style lists exist for generic objects.

### MPGSET

**Syntax:** `MPGSET <target> <stat> <value>`. Minimum: 3 words.

**Behaviour:** like `MPSET`, but a mob's or item's *generic field codes* are tried first (the same names `MODIFY` and `GSTAT` use, so a stat that only generic objects have, or player data, is reachable), then the standard sources, then any generic stat of any physical object. `++` and `--` work. Unknown stat: `MPGSET Syntax: Unknown stat`.

**Example:** `mpgset $n HEIGHT 72`

### MPSETINTERNAL

**Syntax:** `MPSETINTERNAL <setting> <value>`. Minimum: 2 words.

**Behaviour:** changes how *this host's scripts* behave, for as long as the engine lives. Settings (case-insensitive):

| Setting | Value | Effect |
| --- | --- | --- |
| `SCOPE` | `GLOBAL`, a name, or `INDIVIDUAL`/`*` | where variables are kept: the shared store, a named shared store, or this engine's own |
| `VARHOST` | `INSTANCE` or `NAME` | `INSTANCE`: a bare `$` code as host means `@code` (per object) |
| `NODELAY` | `TRUE`/`FALSE` | triggers run at once instead of on the next tick |
| `ACTIVETRIGGER` / `ACTIVETRIGGERS` | `TRUE`/`FALSE` | triggers run even while the mob fights, follows someone, or is under half hit points (not when asleep or bound) |
| `MULTITRIGGER` / `MULTITRIGGERS` | `TRUE`/`FALSE` | one event may set off several of the host's programs, not just the first |
| `DEFAULTQUEST` | quest name | the quest `*` names in quest commands |
| `SAVABLE` | `TRUE`/`FALSE` | whether this script is saved with the host |
| `PASSIVE` | `TRUE`/`FALSE` | `TRUE` lets the script run while its area is passive (nobody there) |
| `NEEDPC` | `TRUE`/`FALSE` | `TRUE`: with no player in the room, only what is queued runs |
| `TRACE` | `TRUE`/`FALSE` | turn script tracing for this host on or off (as `SCRIPTTRACE`) |
| `CANCELWAITS` | any | drops the bodies `MPWAIT` left pending |
| `LLMPROMPT`, `LLMMAXMSGS` | text / number | the LLM chat's instructions and message memory for [MPLLM](#mpllm) |

An unknown setting logs `MPSETINTERNAL Syntax: Unknown stat`.

**Example**

```text
ONCE_PROG
mpsetinternal VARHOST INSTANCE
mpsetinternal MULTITRIGGER TRUE
~
```

## Calling, timing and script management

### MPCALLFUNC

**Syntax**

```text
MPCALLFUNC <function> [arguments]
MPCALLFUNC <target> <function> [arguments]
```

Minimum: 1 word.

**Arguments:** the name of a `FUNCTION_PROG` (or of any program, by its trigger word); optionally a *target* whose scripts hold it; the rest of the line is passed to the function as its message (`$g`, and the `$(PARAM)` names of the `FUNCTION_PROG`).

**Behaviour:** runs a function. With one word, the engine looks for the function in the **host's own scripts** (the name is matched as `FUNCTION_PROG name`, then as a program called `name` or `name_PROG`). If none is found, the first word is taken as a target and the second as the function name (**CoffeeSUD**: `MPCALLFUNC Gatehouse raise`): the function runs on that other thing, with the sender as the actor. If neither works, `MPCALLFUNC Unknown: Function: name` is logged. The function runs at once, to the end, in the same step. The commands in it see `$n`/`$t` from the caller, and `$g`/`$0` hold the argument words. Its `RETURN` value is dropped (use `$%CALLFUNC(name args)%` or `CALLFUNC()` when you need the value).

**Example**

```text
FUNCTION_PROG GREETING WHO
say Welcome, $(WHO)!
~

GREET_PROG 100
mpcallfunc GREETING $n
~
```

**Notes**

- The function's `FUNCTION_PROG` name is matched upper-case; the argument list is split into the function's declared parameter names, the last taking the rest of the words.
- `SCRIPTCHECK` reports an `MPCALLFUNC` to a function that no script on that host defines.
- When no script function and no target matches, the name is tried as a function of the host's own JavaScript (`JScriptable`); see [05](05-javascript.md).

### MPALARM

**Syntax:** `MPALARM [NAME=<name>] <ticks> <command>`. Minimum: 2 words.

**Arguments:** `NAME=` (optional, **CoffeeSUD**) gives the alarm a name; `<ticks>` a positive whole number of game ticks (not game hours); `<command>` the rest of the line, one script line.

**Behaviour:** queues the command to run after that many ticks, with the same actor, target, items and locals the program had when the alarm was set. The rest of the program continues at once. A named alarm replaces any alarm of the same name, can be cancelled with [MPALARMCANCEL](#mpalarmcancel), is kept in a saved game, and is dropped if the mob it belongs to dies, sleeps or is held or bound. `ALARM($i name)` tests for it. Errors: `Bad time`, `No command!`, `No name after NAME=`.

**Example**

```text
GREET_PROG 100
mpecho The fuse hisses.
mpalarm 3 mpecho The barrel explodes!
mpalarm 3 mpdamage $n 20 40
~
```

**Notes**

- It queues one line. For several steps, set the alarm to `MPCALLFUNC step2` and write `FUNCTION_PROG step2`. Often [MPWAIT](#mpwait) is simpler.
- In turn-based time a tick passes when the party acts; see [31](31-saves-and-party.md).

### MPALARMCANCEL

**Syntax:** `MPALARMCANCEL <name>`. **CoffeeSUD.** Minimum: 1 word.

**Behaviour:** cancels a named alarm (`MPALARM NAME=`) on all of the host's scripts, and a telegraph of that name ([MPTELEGRAPH](#mptelegraph)); a telegraphing mob is told its attack is done.

### MPWAIT

**Syntax**

```text
MPWAIT <ticks>
MPWAIT <n> <HOURS|DAYS|WEEKS|MONTHS>
MPWAIT UNTIL (<condition>) [max ticks]
```

**CoffeeSUD.** Minimum: 1 word.

**Behaviour:** pauses the program here and resumes at the next line later: after that many ticks, after that much *game* time on the area's calendar, or once the condition is true (tested each tick, giving up after `max ticks`, default 1000, and then going on). While paused, `$n`, `$t`, `$o`, `$p`, `$g`, `$0`-`$9`, the locals, and the `IF` and `SWITCH` blocks it is inside are kept. The wait is saved with the game. `MPSETINTERNAL CANCELWAITS` drops pending waits.

**Example**

```text
GREET_PROG 100
if ispc($n)
  mpecho The candles gutter.
  mpwait 2
  mpecho A voice whispers your name, $n.
  mpwait 3
  if not isaffected($n Prayer_Bless)
    mpcast Prayer_Bless $n
  endif
endif
~
```

**Notes**

- It cannot pause inside a `FOR`, `WHILE` or `MPWHILE` loop, or inside a function that another line called: it logs a `RunTime` error and does **not** wait. Use it at the top level of a program or inside `IF`/`SWITCH`.
- A bare number is ticks; with a unit it is game time (so it follows the area's calendar and pauses with the game clock).

### MPSIGNAL

**Syntax:** `MPSIGNAL <target> <name> [arguments] [DELAY <ticks>]`. **CoffeeSUD.** Minimum: 2 words.

**Arguments**

| Target | Hears it |
| --- | --- |
| a `$` code or a name | that one thing |
| a room ID | that room |
| `<room>:<direction>` or `HERE:<direction>` | that room's exit in the direction |
| `ROOM` | everything scripted in the sender's room |
| `AREA` | everything scripted in the sender's area |
| `WORLD` | every scripted thing |
| `TAG:<tag>` | things tagged `<tag>` in the sender's area; `TAG:<tag>@WORLD` anywhere |

The name is the signal's name; the rest are arguments (they become `$g`); an optional trailing `DELAY <ticks>` queues it.

**Behaviour:** fires `SIGNAL_PROG <name>` (or `SIGNAL_PROG ALL`) on every script of every target. The signal is queued for the next tick (or after the delay) and is silent; the receiver sees the actor as `$n` and the sender as `$o`. Errors: `no signal name`, `no one to hear it`.

**Example**

```text
# on each brazier
SPEECH_PROG p light
mpsignal TAG:sunaltar lit
~

# on the altar
SIGNAL_PROG lit
mpsetvar @$i LIT ++
if var(@$i LIT >= 3)
  mpdoor OPEN HERE north
endif
~
```

**Notes:** receive with `SIGNAL_PROG` ([26](26-mobprog-triggers.md)). A delayed signal is saved with the game.

### MPNOTRIGGER

**Syntax:** `MPNOTRIGGER <trigger name> <milliseconds>`. Minimum: 0 words per the table; both arguments are needed.

**Behaviour:** switches a trigger off for that many milliseconds on this host (for example `MPNOTRIGGER SPEECH_PROG 5000`), and throws away any of that trigger's responses already queued. An unknown trigger or a non-numeric time is logged. Use it to stop a script from re-firing on its own echoes.

**Example:** `mpnotrigger GREET_PROG 10000`

### MPINTERRUPT

**Syntax:** `MPINTERRUPT <who> [message]`. **CoffeeSUD.** Minimum: 1 word.

**Behaviour:** ends the wait (`WAIT`, `MPPASSTIME`) or journey of that character or their party, optionally telling them the message.

### MPPASSTIME

**Syntax:** `MPPASSTIME <n> <HOURS|DAYS|WEEKS> [INTERRUPTIBLE] [QUIET]` or `MPPASSTIME UNTIL <DAWN|DAY|DUSK|NIGHT|HOUR n|DAY n|ARRIVAL|DOCK> [INTERRUPTIBLE] [QUIET]`. **CoffeeSUD.** Minimum: 1 word.

**Behaviour:** makes game time pass for the character who triggered the script (`$n` if a player, else the character at the terminal): the world goes on as it would, the clock is paused around it if it runs, up to three game days. The player is told each hour (`QUIET`: not). `INTERRUPTIBLE` ends it if they are hurt, attacked or moved. `UNTIL DAWN` waits for the next dawn (or the one after, if it is dawn now). Errors: bad words, or `time is already passing`.

**Example:** `mppasstime 8 HOURS INTERRUPTIBLE` in a bed's `SLEEP_PROG`.

**Notes:** see [10](10-world-simulation.md) for time and the wait commands.

### MPSCRIPT

**Syntax:** `MPSCRIPT <target> [flags] <script text>`. Minimum: 2 words.

**Arguments:** the target (mob, item, room, exit, area); flags in any order before the text: `SAVABLE` (kept with the target), `STATIC` (load `LOAD=` files now), `DELETE` (remove a script instead), `EXECUTE` (run its `ONCE_PROG` now and remove it), `GLOBAL` (shared variable store), `INDIVIDUAL` or `*` (its own store); then the script text, or `*` for a copy of the script that is running.

**Behaviour:** appends a **new, separate** script to the target. Unlike `MPBEHAVE Scriptable` it can add more than one unrelated script, and it is the way a quest hands a script to a player. `DELETE` removes a script whose text matches (`DELETE *` removes the host's own script from the target). `EXECUTE` adds the script, runs its triggers once, then removes it: a one-off program run on the target. A script added for a quest is registered with that quest (so `MPENDQUEST` can remove it).

**Example**

```text
SPEECH_PROG p quest
mpscript $n SAVABLE GREET_PROG 100\;mpecho The wolves howl, $n.\;~\;
~
```

**Notes**

- Because `;` and `~` end lines and programs, the inner ones are written `\;` and `\~` (or put the program in a file and add `LOAD=...`).
- `MPSCRIPT $n DELETE ...` has to match the text exactly.
- The target's scripts run as that target (`$i` is the target).

### MPUNLOADSCRIPT

**Syntax:** `MPUNLOADSCRIPT <script file>`. Minimum: 1 word (extra words are not allowed).

**Behaviour:** forgets the parsed copy of a `LOAD=` script file from the cache, so the next use reads the file again. Use it after changing a script file while the game runs. Logs `File does not exist` if the file is missing.

**Example:** `mpunloadscript world:scripts/guard.script`

### MPJSFILE

**Syntax:** `MPJSFILE <file> [args]`. **CoffeeSUD.** Minimum: 1 word.

**Behaviour:** runs a JavaScript file as a `<SCRIPT>` block would, with the rest of the line as its arguments. `<file>` is under `resources/`, or `world:path`. Subject to the same `JSCRIPTS` approval as `<SCRIPT>`. A last value or a call of `cancel` ends the program as `RETURN CANCEL`.

**Example:** `MPJSFILE world:js/fixjs15.js one two`

### MPBEHAVE

**Syntax:** `MPBEHAVE <behavior> <target> [parameters]`. Minimum: 2 words.

**Arguments:** a behaviour ID or name (`Aggressive`, `Mobile` ...); the target; the rest of the line is the behaviour's parameters.

**Behaviour:** gives the target the behaviour if it does not already have it. If the script belongs to a quest, the behaviour is registered with the quest so it goes when the quest ends. Existing behaviours are not changed.

**Example:** `mpbehave Aggressive $i`; `mpbehave Mobile $i min=3 max=6`.

**Notes:** behaviours are in [24](24-behaviors-and-masks.md).

### MPUNBEHAVE

**Syntax:** `MPUNBEHAVE <behavior> <target>`. Minimum: 2 words.

**Behaviour:** removes the behaviour from the target, if present.

## Asking the player

`MPPROMPT`, `MPCONFIRM` and `MPCHOOSE` **block**: the program waits for the player's answer, so the engine runs such a program on a separate thread (it marks the program `SPAWN`). They need a character with a session, that is a player or party member; aimed at an NPC they do nothing. With party windows off, a prompt to a party member in the background appears at the terminal with the member's name in front. If the player does not answer in time, or leaves, the program ends (the command returns an empty result). `MPMENU` does **not** block.

The answer is stored as a script variable on the *target's name* (`MPSETVAR <name> <var> <answer>`), so read it back with `$<$n VAR>` or `VAR($n VAR == ...)`.

### MPPROMPT

**Syntax:** `MPPROMPT <player> <variable> <prompt text>`. Minimum: 3 words.

**Behaviour:** shows the prompt and waits (up to six minutes) for a line of text; stores the line in the variable.

**Example**

```text
SPEECH_PROG p name
mpprompt $n PETNAME What will you name the pup?
mpecho $n calls the pup $<$n PETNAME>.
~
```

### MPCONFIRM

**Syntax:** `MPCONFIRM <player> <variable> <default Y|N> <prompt text>`. Minimum: 4 words.

**Behaviour:** asks a yes/no question (waits up to one minute); stores `Y` or `N`. The default is taken on an empty answer.

**Example:** `mpconfirm $n SURE N Are you certain you want to open the vault?`

### MPCHOOSE

**Syntax:** `MPCHOOSE <player> <variable> <choices> <default> <prompt text>`. Minimum: 5 words.

**Arguments:** `<choices>` is a string of upper-case hotkey characters (for example `ABC`, or `YNM`); `<default>` the key taken on an empty answer.

**Behaviour:** shows the prompt and waits (up to one minute) for one of the keys, asking again after a key that is not a choice; stores the key typed in the variable.

**Example**

```text
SPEECH_PROG p choice
mpchoose $n PATH SKN S What do you do? (S)pare, (K)ill, (N)either?
if var($n PATH == K)
  mpkill $n
endif
~
```

### MPMENU

**Syntax:** `MPMENU <player> <FUNCTION> "<prompt>" <key>:"<label>"[IF <condition>] ... [default key]`. **CoffeeSUD.** Minimum: 1 word.

**Arguments:** the character; the name of a `FUNCTION_PROG` in the same script; a double-quoted prompt; then the choices, each `key:"label"`, optionally followed by `[IF condition]` (the choice is shown only if the condition is true); a final bare word is the default key.

**Behaviour:** shows the prompt and a numbered list of the choices. The script **goes on at once**: the character's next line of input answers it, by number or by key. An empty line, or an answer that matches nothing, takes the default (with no default it asks again). The answer is not a command, so it takes no game time in turn-based play. Then `FUNCTION_PROG <FUNCTION>` runs with the key as `$0` and `$g`, the character as `$n` and the host as `$i`. One menu per character: a new one replaces the old. An unknown function logs `no FUNCTION_PROG X`.

**Example**

```text
GREET_PROG 100
if ispc($n)
  mpmenu $n SAGE "The old sage regards you." tower:"Ask about the tower" ring:"Show him the ring"[IF HAS($n ring)] bye:"Leave" bye
endif
~

FUNCTION_PROG SAGE
switch $0
case tower
  say The tower fell a century ago.
case ring
  say Where did you find that?
endswitch
~
```

**Notes:** a menu of items for a conversation is the usual job; see [06](06-npcs-dialogue.md) for dialogue design.

### MPLLM

**Syntax:** `MPLLM <host> <variable> <message>`. Minimum: 3 words.

**Behaviour:** sends the message to this host's LLM chat and stores the reply in the script variable, as `MPSETVAR` would (but not for `@` hosts). The first call creates the chat: with `MPSETINTERNAL LLMPROMPT <text>` the instructions are that text, otherwise the first message *becomes* the instructions (and the variable is set empty). `MPSETINTERNAL LLMMAXMSGS <n>` sets how many messages it remembers (default 5). The program is marked as one that may take a long time. If no LLM is configured (the `LANGCHAIN4J_` settings in `coffeemud.ini`), it logs `MPLLM RunTime: Could not create LLM Session.`

**Example**

```text
SPEECH_PROG all
mpllm $n REPLY $g
say $<$n REPLY>
~
```

**Notes:** optional; a world should work without it. Setup is in [19](19-configuration-and-administration.md).

## Stats, skills and progression

The target of these commands is a character: a `$` code or a name; many accept NPCs too. Where a number is taken, `++n` and `--n` add and subtract from the current value, and a plain number sets it.

### MPEXP

**Syntax:** `MPEXP <who> <amount | NEXTLEVEL | n%>`. Minimum: 2 words.

**Behaviour:** gives experience through the normal experience routine (so level-ups, caps and the party's share apply). `NEXTLEVEL` gives exactly what the next level needs (**CoffeeSUD**). `n%` gives that percentage of the experience needed for the character's current level (for example `25%`).

**Example:** `mpexp $n 150`; `mpexp $n 50%`.

### MPRPEXP

**Syntax:** `MPRPEXP <who> <amount | n%>`. Minimum: 2 words.

**Behaviour:** like `MPEXP`, but role-playing experience, the kind the `RPAWARDS` setting in `coffeemud.ini` sets up. While `RPAWARDS` is empty (the default) it has no effect.

### MPLEVELUP

**Syntax:** `MPLEVELUP <who> [n]`. **CoffeeSUD.** Minimum: 1 word.

**Behaviour:** the character gains exactly *n* levels (default 1), each by the experience it needs, wherever the character is within its level. `n` must be at least 1.

### MPLEVELDOWN

**Syntax:** `MPLEVELDOWN <who> [n]`. **CoffeeSUD.** Minimum: 1 word.

**Behaviour:** the character loses *n* levels (default 1), its experience set back with them.

### MPQUESTPOINTS

**Syntax:** `MPQUESTPOINTS <who> <n | ++n | --n>`. Minimum: 2 words.

**Behaviour:** sets the character's quest points, or adds (`++5`) or subtracts (`--5`). Gains count toward the "quest points earned" statistic. Anything else logs `Bad syntax`.

**Example:** `mpquestpoints $n ++3`

### MPTRAINS

**Syntax:** `MPTRAINS <who> <n | ++n | --n>`. Minimum: 2 words.

**Behaviour:** sets or adjusts the character's training sessions. Like `MPQUESTPOINTS` in form.

### MPPRACS

**Syntax:** `MPPRACS <who> <n | ++n | --n>`. Minimum: 2 words.

**Behaviour:** sets or adjusts the character's practice sessions.

### MPENABLE

**Syntax:** `MPENABLE <who> <ability | expertise> <proficiency> [parameters]`. Minimum: 2 words.

**Arguments:** the character; a skill, spell or other ability (name or ID), or an expertise ID; proficiency as a number 0-100, or `++n` / `--n` relative to the current; optional text the ability receives as its parameters.

**Behaviour:** teaches the character the ability at that proficiency (or changes it if known); an expertise is added. A language is checked against the language cap (`MAXLANGUAGES`) first: if the character cannot learn another, the error is logged and nothing happens. Adding an ability also starts its auto-invocation (so a passive skill takes effect). An unknown name logs `Unknown skill/expertise`.

**Example:** `mpenable $n Skill_Haggle 75`; `mpenable $n Dwarven ++20`.

### MPDISABLE

**Syntax:** `MPDISABLE <who> <ability | expertise>`. Minimum: 2 words.

**Behaviour:** removes the ability, and the expertise of that name, from the character.

### MPLEARNRECIPE

**Syntax:** `MPLEARNRECIPE <who> <skill> <recipe name | recipe line>`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** the character knows that recipe for good: one of the crafting skill's own recipes by name (including one marked `HIDDEN=1`), or a whole recipe line whose fields are joined with `\t` (written as the two characters backslash and `t`). The character is told `You know how to make X now.` See [07](07-items-crafting.md) for recipes.

**Example:** `mplearnrecipe $n Blacksmithing 'steel dagger'`

### MPLEARNLANG

**Syntax:** `MPLEARNLANG <who> <language> <n | +n | -n> [FORCE]`. **CoffeeSUD.** Minimum: 2 words (the engine needs 3).

**Behaviour:** sets (`40`) or adjusts (`+10`) the character's knowledge of a language, between 0 and 100, and brings any live effect up to date. A language the character does not yet know is refused when it would pass the language cap, unless `FORCE`. The character is told `Your grasp of X improves.` when it rises.

**Example:** `mplearnlang $n MuseumSpeech 40`

### MPPERK

**Syntax:** `MPPERK <who> <perk id>` or `MPPERK <who> -<perk id>`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** gives the character a perk of `world/perks.ini` with its rewards, or (with `-`) takes it off; the rewards stay. An unknown perk logs `MPPERK $n [-]perk (a perk of world/perks.ini)`. See [03](03-characters.md).

### MPRANK

**Syntax:** `MPRANK <who> <ladder> [+n | -n | =n | CHECK]`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** moves the character up (`+1` if not given) or down a rank ladder of `world/ranks.ini`, or to rank *n* (`=0` takes them off it), with the rank's effects, title and (once) rewards. `CHECK` raises them as far as the next ranks' `REQUIRES` allow. An unknown ladder logs `no ladder X in world/ranks.ini`. Read the rank with `RANK(who ladder)`.

**Example:** `mprank $n GUILD +1`

### MPRESPEC

**Syntax:** `MPRESPEC <who> [SKILLS|EXPERTISES|STATS|CLASS|ALL] [name|ALL]`. **CoffeeSUD.** Minimum: 1 word.

**Behaviour:** undoes what the character bought with trains and practices, of those kinds (`ALL` if not given), the one named or all, and gives back what each cost, free. Skills that come by themselves stay. The character is told `You unlearn X.` for each.

### MPPLAYERCLASS

**Syntax:** `MPPLAYERCLASS <who> <class | level> ...`. Minimum: 2 words.

**Behaviour:** the rest of the line is read word by word: a number sets the level of the character's current class; a class name or ID changes the current class (only to classes that are available to players). So `mpplayerclass $n Mage 5` makes them a level-5 Mage.

### MPTITLE

**Syntax:** `MPTITLE <who> [-]<title>`. Minimum: 2 words.

**Behaviour:** adds a title to a player character, or (with a leading `-`) removes it. Adding a title they already have moves it to the top. Has no effect on mobs without player data.

**Example:** `mptitle $n the Slayer of Rats`

### MPTATTOO

**Syntax:** `MPTATTOO <who> [-]<tattoo>`. Minimum: 2 words.

**Behaviour:** adds the tattoo to the character if it is not there; with a leading `-` removes it. A tattoo is a one-word flag kept with the character and saved; test with `HASTATTOO($n NAME)`. A tattoo name starting with a number of ticks and a word (`'450 PLANTED'`, quoted) is a *timed* tattoo that wears off. Adding a tattoo that is already present does nothing.

**Example:** `mptattoo $n BELLHEARD`; `mptattoo $n -BELLHEARD`.

### MPACCTATTOO

**Syntax:** `MPACCTATTOO <who> [-]<tattoo>`. Minimum: 2 words.

**Behaviour:** like `MPTATTOO`, but on the player's **account**, which in CoffeeSUD is the *profile* shared by all of the player's characters. Test with `HASACCTATTOO`. Does nothing for a character without a profile.

### MPACHIEVE

**Syntax:** `MPACHIEVE <player> <achievement id> [amount]`. Minimum: 3 words.

**Behaviour:** adds *amount* to the player's progress in the achievement. With no amount it *grants* the achievement outright, but only from a mob that has the `CMDPLAYERS` security flag. A non-player target, an unknown achievement or a bad amount is logged.

**Example:** `mpachieve $n SLAYER 1`

### MPRECRUIT

**Syntax:** `MPRECRUIT <npc> <player> [PARTY | REST] [NAME=<name>]`. **CoffeeSUD.** Minimum: 1 word (needs 2).

**Behaviour:** the NPC becomes a character of the player's profile, keeping its race, class, level, stats, abilities, effects, inventory, factions, tattoos and description, and leaves the world as an NPC. It joins the party (`PARTY`, the default) or rests (`REST`). `NAME=` names it (else the first word of the NPC's name). It refuses, telling the player, if the profile or party is full or the name is taken. See [03](03-characters.md) and [31](31-saves-and-party.md).

## Combat and effects

### MPKILL

**Syntax:** `MPKILL <mob>`. Minimum: 1 word.

**Behaviour:** the script's mob makes the target its victim, so it starts attacking it on its next turn. Nothing happens for a target that is not a mob or player.

**Example:** `mpkill $n` in a `SPEECH_PROG` for the insult that starts a fight. To make another mob fight use `MPFORCE <mob> kill <target>`.

### MPHIT

**Syntax:** `MPHIT <mob>`. Minimum: 1 word.

**Behaviour:** the script's mob makes a single attack on the target with the weapon it wields, as a combat round would, and no one's fight target is changed by it (everyone in the room keeps the victim they had).

**Example:** `mphit $n` in a trap item's `GET_PROG`.

### MPDAMAGE

**Syntax:** `MPDAMAGE <target> <min> <max> [ME] [KILL] [MEKILL] [type]`. Minimum: 3 words.

**Arguments:** a mob, player or item; the damage range (a random value between, or exactly `min` when `max` is not larger); then option words in any order: `ME` (the script's mob is credited as the attacker; otherwise the target "hurts itself"), `KILL` (allow it to kill), `MEKILL` (both), and a damage type word such as `FIRE`, `COLD`, `WATER`, `GAS`, `MIND`, `ACID`, `ELECTRIC`, `POISON`, `DISEASE`, `PARALYZE`, `UNDEAD` or `JUSTICE` (the default is a spell-like hit).

**Behaviour:** on a mob or player: does that damage directly (there is no to-hit roll, so it cannot miss), but **never kills** unless `KILL` or `MEKILL` is given: the hit is cut so that one hit point is left. Damage below 1 does nothing. On an item that wears out: reduces its remaining uses by the amount (leaving 1 unless `KILL`), and destroys it when none are left (`<item> is destroyed!`); an item that does not wear out is destroyed only by `KILL`.

**Example**

```text
GET_PROG
mpecho A needle springs from the chest!
mpdamage $n 5 15 POISON
~
```

**Notes:** the damage is a script effect; for attacks that should be dodgeable, use `MPCAST` or `MPHIT`, or telegraph them with [MPTELEGRAPH](#mptelegraph) (see [29](29-combat-and-encounters.md)).

### MPHEAL

**Syntax:** `MPHEAL <target> <min> <max>`. Minimum: 3 words.

**Behaviour:** heals a mob or player by a random amount from `min` to `max` hit points (exactly `min` if `max` is not larger), with the normal healing message path. On an item that wears out, mends it by that much (up to 100 uses). An amount below 1 does nothing.

**Example:** `mpheal $n 10 20`

### MPCAST

**Syntax:** `MPCAST <spell> <target>`. Minimum: 1 word.

**Arguments:** an ability name or ID (a spell, prayer, skill ...); quote a name with spaces. The target may be left out for abilities that take none.

**Behaviour:** the script's mob uses the ability at 100 proficiency. A mob host pays the usual costs (mana and so on) and may fail for lack of them; a stand-in for an item, room, exit or area host is refilled to full first, so those hosts always can. An unknown ability logs `MPCAST RunTime: X is not a valid ability name.`

**Example:** `mpcast fireball $n`; `mpcast 'Prayer_Bless' $n`

**Notes:** for abilities that need extra words (a direction, a target item) use [MPCASTEXT](#mpcastext).

### MPCASTEXT

**Syntax:** `MPCASTEXT <spell> <target> <arguments>`. Minimum: 3 words.

**Behaviour:** like `MPCAST`, but the rest of the line is given to the ability as the extra words a player would type after its name.

**Example:** `mpcastext Skill_Track $n north`

### MPAFFECT

**Syntax:** `MPAFFECT <ability> <target> [parameters]`. Minimum: 2 words.

**Arguments:** an ability name or ID; the target; the rest of the line is passed to the ability as its parameter text. A parameter text beginning with `<n> TICKS ` limits how long it lasts. Prefix the ability with `property:` (for example `property:Spell_Fly`) to apply it as a permanent effect whatever its type.

**Behaviour:** puts the ability on the target. For a *property*-type ability (or with `property:`), it is added as a permanent effect (or, with `TICKS`, as a temporary one through `TemporaryAffects`). For other abilities, it is cast on the target with auto-invocation (no cost, no checks), and with `TICKS` its expiry is set. An unknown ability logs `MPAFFECT RunTime: X is not a valid ability name.`

**Example**

```text
ONCE_PROG
mpaffect Prop_ReqAlignment $i +GOOD
mpaffect Spell_Haste $n 20 TICKS
~
```

**Notes:** properties are in [23](23-properties-reference.md); abilities in [25](25-abilities-and-effects.md).

### MPUNAFFECT

**Syntax:** `MPUNAFFECT <target> [ALL | <ability>]`. Minimum: 2 words.

**Behaviour:** ends the named effect on the target (it is un-invoked, then removed if it stays), or every effect with `ALL` or no word. Properties go too.

**Example:** `mpunaffect $n Poison_Venom`

### MPSTOP

**Syntax:** `MPSTOP <mob | ALL>`. Minimum: 0 words.

**Behaviour:** makes the mob stop fighting (peace) and stops any crafting or other common skill it is performing. `ALL` does so for every creature in the host's room.

**Example:** `mpstop all` when a duel ends or an oath is sworn.

### MPTHREAT

**Syntax:** `MPTHREAT <npc> <who> <+n | -n | =n | *x>`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** changes how threatening `<who>` is to the NPC (its target choice in combat): add, subtract, set (`=`) or multiply (`*`). See [29](29-combat-and-encounters.md) and `TOPTHREAT`.

**Example:** `mpthreat $t $n *3` (a taunt).

### MPTELEGRAPH

**Syntax**

```text
MPTELEGRAPH [NAME=<name>] <ticks> "<warning>" <command> [INTERRUPT=<list>] [AVOID=<list>] [INTERRUPTMSG="<text>"]
```

**CoffeeSUD.** Minimum: 2 words. **Host:** a mob in a room.

**Behaviour:** the mob *winds up* an attack. The warning shows now and each tick, `LOOK` at the mob shows it is gathering power, and after `<ticks>` the command runs once for each foe in the room (`$n` each one) who did not avoid it: those who left, hid (`AVOID=HIDE`) or typed the avoid word. Options:

| Option | Meaning |
| --- | --- |
| `INTERRUPT=STUN,DAMAGE>20%,MOVE` | what stops the wind-up: a stun, a sleep or a hold always does; `DAMAGE>n%` when it loses that share of its hit points meanwhile; `MOVE` when it leaves the room |
| `AVOID=LEAVE,HIDE,COMMAND:brace` | how a foe avoids it: leaving, hiding, typing the word `brace` |
| `INTERRUPTMSG="text"` | shown when it is interrupted |

It is a named alarm (`TELEGRAPH` unless `NAME=` is given), so `ALARM($i TELEGRAPH)` and [MPALARMCANCEL](#mpalarmcancel) work on it. A second telegraph of the same name replaces the first.

**Example**

```text
FUNCTION_PROG BREATH
mptelegraph 2 "<S-NAME> rears back, flames gathering in its throat!" mpdamage $n 30 60 FIRE INTERRUPT=STUN,DAMAGE>20% AVOID=HIDE,COMMAND:brace
~
```

**Notes:** for an item or room host it logs `only a mob in a room can wind up an attack`. The `Prop_Telegraph` property holds the state; see [29](29-combat-and-encounters.md).

### MPWAVE

**Syntax:** `MPWAVE <START | NEXT | STOP | RESET>`. **CoffeeSUD.** Minimum: 2 words per the table; one is required.

**Behaviour:** drives the `WaveSpawner` behaviour of the room: start it (`START=CALL` waits for this), bring the next wave, end it as a loss, or clear it to start again. Run it from the room itself or from a mob in the room. Without `WaveSpawner` there it logs `MPWAVE RunTime: ..., in a room with WaveSpawner`.

**Example:** `mpwave NEXT`

### MPREJUV

**Syntax:** `MPREJUV <AREA | ROOM | room id | area name> [items | mobs]`. Minimum: 1 word.

**Behaviour:** triggers the rejuvenation tick (as if the room's reset time had come) for the host's area or room, or a named room or area: dead mobs come back, lost items return. `items` or `mobs` (write them in lower case) limits it to those. An unknown place logs `MPREJUV Syntax: Unknown location`.

**Example:** `mprejuv room mobs`

### MPRESET

**Syntax:** `MPRESET <AREA | ROOM | room id | area name | item | mob>`. Minimum: 0 words.

**Behaviour:** `AREA` resets the host's area as an area reset does; `ROOM` or a room ID resets that room; an area name resets that area; an item is taken out of its container and mended to 100 uses; a mob is restored to full hit points, mana and movement. An unknown target logs `Unknown location or item`.

**Example:** `mpreset room` at the end of a puzzle's reset button.

## Quests

A quest is defined in a quest script (see [08](08-quests.md)). These commands drive a quest from a MOBPROG. In quest arguments, `*` stands for the quest the script belongs to (the script's *default quest*), when the script was given to the host by a quest.

### MPSTARTQUEST

**Syntax:** `MPSTARTQUEST <quest name>`. Minimum: 1 word.

**Behaviour:** starts the quest with that name, as `QUEST START` would. An unknown name logs `MPSTARTQUEST Unknown: Quest`.

**Example:** `mpstartquest bell` in the giver's `FUNCTION_PROG DO_ACCEPT`.

### MPSTEPQUEST

**Syntax:** `MPSTEPQUEST <quest name> [+n | =n | =label]`. Minimum: 1 word.

**Behaviour:** moves the quest on to its next step. `+n` skips *n* steps; `=n` jumps to step number *n*; `=label` (**CoffeeSUD**) jumps to the step the quest script named with `STEP NAME label`. An unknown quest or label is logged.

**Example:** `mpstepquest bandits =spared`

### MPENDQUEST

**Syntax:** `MPENDQUEST <quest name | mob>`. Minimum: 1 word.

**Behaviour:** with a quest's name: stops that quest (it announces the end, clears its objects and counts the stop). With a mob (when the script itself belongs to a quest): removes that quest's scripts from the mob and runs their end handling. With a name matching one of the host's own quest scripts: ends that script. An unknown name logs `MPENDQUEST Unknown: Quest`.

**Example:** `mpendquest bell`

### MPQUESTWIN

**Syntax:** `MPQUESTWIN <who> <quest name> [OUTCOME <word>] [REWARD]`. Minimum: 2 words.

**Behaviour:** declares the character a winner of the quest, bumps the "quests completed" statistic and the QUESTOR achievement, and sends the win message to scripts. `OUTCOME word` (**CoffeeSUD**) keeps a one-word record of how it ended, read with `QUESTOUTCOME()` and shown in `QUESTS WON`. `REWARD` gives the quest's `SET REWARDS`. With a shared quest (`SET SHARED PARTY`) the party members present win with them. An unknown quest logs `MPQUESTWIN Unknown: Quest`.

**Example:** `mpquestwin $n bandits OUTCOME spared REWARD`

### MPQUESTFAIL

**Syntax:** `MPQUESTFAIL <who> <quest name> [reason]`. **CoffeeSUD.** Minimum: 1 word.

**Behaviour:** the quest fails for that character: it is recorded (`QUESTSTATUS`, `QUESTS FAILED`), `QUESTFAIL_PROG` and `QUEST_PROG FAIL` fire, and their quest scripts end and go, as `MPENDQUEST <who>` does. The rest of the line is the reason.

**Example:** `mpquestfail $n oskar the escort died`

### MPREWARD

**Syntax:** `MPREWARD <who> "<awards>"`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** gives the character a reward, written in the words a quest's `SET REWARDS` uses (a number of coins, `QP`, `XP`, `ITEM:name`, `FACTION:name:+n` and so on), with one message. See [08](08-quests.md) for the reward words.

**Example:** `mpreward $n "5 QP"`

### MPQSET

**Syntax:** `MPQSET <quest> <variable> <value>`. Minimum: 3 words.

**Behaviour:** sets a quest variable to a value (`++` and `--` add and subtract one). Special variables: `QUESTOBJ` with an object registers the object with the quest (so it is removed when the quest ends and not saved); `STATISTICS` with `ACCEPTED`, `SUCCESS` (or `WON`) or `FAILED` bumps that quest statistic. An unknown quest logs `MPQSET Syntax: Unknown quest`.

**Example:** `mpqset bell STATISTICS ACCEPTED`

### MPLOADQUESTOBJ

**Syntax:** `MPLOADQUESTOBJ <quest> <quest variable> <$x>`. Minimum: 3 words.

**Behaviour:** takes the object the quest stored under the variable name (a mob, item or room the quest script designated) and puts it into one of the program's slots: `$0`-`$9`, `$n`, `$t`, `$i`, `$o`, `$p`, `$b` or `$d`. Then the rest of the program can use it as a target. Unknown quest, unknown variable and a bad slot are logged.

**Example:** `mploadquestobj bell BELLITEM $0`

### MPQOBJECTIVE

**Syntax:** `MPQOBJECTIVE <who> <quest> <ADD|SET|BUMP|DONE|FAIL|HIDE|SHOW|AT|CLEAR> <id> [...]`. **CoffeeSUD.** Minimum: 3 words.

| Form | Effect |
| --- | --- |
| `ADD <id> <count> <text>` | gives the character an objective in the quest journal; `AT=<place>` after the id says where it is (`AT=Brenn#14`, `AT=QUESTOBJ:2`) |
| `SET <id> <n>` | sets the progress |
| `BUMP <id> [n]` | adds *n* (1) to the progress; reaching the count makes it done |
| `DONE <id>` / `FAIL <id>` | marks it finished / failed |
| `HIDE <id>` / `SHOW <id>` | hides it from the journal / shows it again |
| `AT <id> [where]` | sets where the objective is; no place forgets it |
| `CLEAR [id]` | removes one objective, or all of the quest's |

The character is told of each change. `QOBJECTIVE(who quest id)` reads the state. `*` as the quest is this script's quest.

**Example**

```text
MPQOBJECTIVE $n bell ADD drowned 5 Lay 5 drowned men to rest
MPQOBJECTIVE $n bell BUMP drowned
```

### MPQJOURNAL

**Syntax:** `MPQJOURNAL <who> <quest> <text>`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** adds an entry to the character's quest journal, dated on the game calendar (`QUESTS <quest>`, `QUESTS LOG`).

**Example:** `mpqjournal $n bell The bell rang under the water.`

### MPQTIMER

**Syntax:** `MPQTIMER <who> <quest> <game time | OFF>`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** the character's own deadline for the quest, from now, as `6 MUDHOURS`, `2 MUDDAYS` or a number of ticks; `OFF` takes it away (and so does the quest's end for them). It is kept with the character (in saves too) and counted on the game clock. When it comes, a `QUESTTIMEOUT_PROG` in the script the quest gave them runs, or, with none, the quest fails for them (status `EXPIRED`).

**Example:** `mpqtimer $n bell 2 muddays`

## Journal, lore and narration

### MPJOURNAL

**Syntax:** `MPJOURNAL <who> [category] <text>`. **CoffeeSUD.** Minimum: 2 words.

**Behaviour:** adds a dated personal journal entry for a player character: the date on the game calendar, the script host as source and the character's location. With two words after the command the second is the text and the category is `Notes`; with more, the second word is the category (single-quote it if it has spaces) and the rest is the text. At most 300 entries are kept per character; the oldest goes first.

**Example:** `MPJOURNAL $n Rumors Beware the beast of the marsh`

**Notes:** see [11](11-presentation-language.md) for the `JOURNAL` command and categories.

### MPLORE

**Syntax:** `MPLORE <who> ADD <key> [PROFILE]`. **CoffeeSUD.** Minimum: 2 words (the engine needs 3).

**Behaviour:** unlocks a lore (codex) entry: it is saved as the tattoo `LORE_<KEY>` on the character, or on the profile with `PROFILE`. The first time, a journal line `Learned <title>.` is added. Any word but `ADD` logs `Use MPLORE target ADD key [PROFILE]`.

**Example:** `mplore $n ADD FIXBEAST`

### MPNARRATE

**Syntax:** `MPNARRATE <target> <file[#section]> [PAUSE | NOPAUSE] [STYLE=<name>]`. **CoffeeSUD.** Minimum: 1 word (needs 2).

**Arguments:** `<target>` a character, `ROOM` or `AREA`; `<file>` a text file under `world/text/` (the `#section` picks the lines under a `[section]` header; without one the whole file is used, ignoring header lines); `PAUSE` pages the text a paragraph at a time with Enter between (the clock is held in real-time play and nothing is spent in turn-based play); `NOPAUSE` (the default) shows it all; `STYLE=` a `COLORALIASES` name for the colour.

**Behaviour:** reads the text (script codes such as `$n` and `VARIES` text are expanded) and shows it to the player characters in the target. Non-player creatures are ignored. Blank lines separate paragraphs.

**Example:** `MPNARRATE $n museum.txt#arrival PAUSE`

**Notes:** see [11](11-presentation-language.md) for narration files.

