# MOBPROG scripting

[Handbook](README.md) · [Complete CoffeeSUD supplement](../../CoffeeMud/guides/ScriptingSUD.html)
· [Inherited command grammar](../../CoffeeMud/guides/Scriptable.html)

## Hosts and programs

Attach the `Scriptable` behavior to a mob, item, exit, room or area. Its
parameters can be inline programs or `LOAD=world:scripts/name.script`.
File-backed scripts are easier to review and share. A program starts with a
trigger, ends at `~`, and executes for its host. Multiple programs share a file.
Reusable files can receive parameters; use `ARG()` and the documented LOAD
argument syntax instead of editing a separate copy for every NPC.

```text
SPEECH_PROG p tower
if hastattoo($n KNOWS_TOWER)
  mpechoat $n The guide nods. 'You remember the way.'
else
  mpechoat $n The guide points north. 'Beyond the square.'
  mptattoo $n KNOWS_TOWER
endif
~
```

The host above is the guide. `$i` is the host/acting scripted creature,
`$n` the event's actor, `$t` its target where provided, `$o`/`$p` event items,
`$g` event detail, and `$0` onward the event/function arguments. Availability
depends on the trigger; never assume a target exists in every event. `$u`
identifies the terminal character. These bindings are not arbitrary names you
can reuse as local variables.

Comparisons belong inside function parentheses, for example
`VAR($n CHAPTER == 2)`. Text interpolation uses forms such as
`$<host variable>` and `$%FUNCTION$(args)%`; consult the supplement for the
function's boolean versus string form. A game command typed in the terminal,
an `MP…` script command, a function expression and a zapper mask are four
different grammars.

## Pick the right event

| Event family | Useful triggers |
| --- | --- |
| Conversation and meetings | SPEECH, NOTUNDERSTOOD, TALK, FIRSTGREET, REGREET, SOCIAL |
| Object interaction | GET/GIVE, READ, USE/COMBINE, SWITCH, RIDE/DISMOUNT, REMOVING, VERB |
| Combat and effects | COMBAT, HITPRCNT, KILL/DEATH, AFFECT, SURRENDER/YIELD, PHASE |
| Story and progression | QUEST, FACTION, CLAN, PARTY, LEVEL, SKILL, RECIPE/CRAFT, ACHIEVE |
| Exploration and world | DISCOVER, area borders, WEATHER/MOON, GENERATED, DESTROY/DECAY |
| Time | TURN, EVERY, TIME/DAY and calendar triggers |
| Game lifecycle | GAME NEWGAME/RESTORE/SAVE/NEWCHAR/SWITCHTO/SWITCHFROM/PAUSE/RESUME/TIMEMODE |
| Message interception | EXECMSG and CNCLMSG, including selected `@GLOBAL` forms |

The names above omit `_PROG` for readability. See the [ST index](feature-index.md#st)
for every added trigger and its exact event arguments. Events such as LOGIN,
LEVEL, LOGOFF and CHANNEL register when a script loads; they need not wait for
a local room tick. Global listeners can observe events outside their room;
keep filters narrow so rewards and counters do not run twice.

`VERB_PROG` provides object-local verbs before skills/socials. Use it when a
real action word such as `turn dial` should belong to the object. CMDFAIL/HUH
handlers are useful for unknown commands, but do not make them silently absorb
unrelated actions. `CNCLMSG_PROG` can inspect or rewrite a message and return
cancel to refuse it; EXECMSG observes accepted activity. Use message tools to
alter damage rather than subtracting HP in an unrelated output event.

## State and scope

| Storage | Use |
| --- | --- |
| Named `MPSETVAR` scope | A deliberate shared namespace, including WORLD/story state |
| Per-object `@` / `MPSTATE` | Independent state for a physical host, using saved extra data |
| Character tattoo | A durable flag for that character, directly usable by masks |
| Profile/account tattoo | Unlocks shared across that profile's characters |
| `cm.store(key)` | Structured JSON state shared with JavaScript |
| Local names/function parameters | Temporary calculation during that invocation |

Do not key independent twins by the same display name. Per-object scope and
persistent instance identities prevent accidental shared counters. “Account”
in inherited scripting means the local profile. HASACCTTATTOO/MPACCTATTOO use
that scope; HASTATTOO/MPTATTOO use the character.

Lists, add/remove/shuffle, string operations, dice, numeric expressions,
weighted picks and tags are available. ELSEIF, bounded WHILE blocks, CONTINUE,
named locals and function parameters reduce the need for nested repeated code.
Bound loops and avoid relying on a saved local stack: an arbitrary executing
program and its local variables are not serialized.

## Calls, signals and pauses

`FUNCTION_PROG name` exposes a script function. `MPCALLFUNC` invokes a function;
`MPSIGNAL` broadcasts to a selected scope and `SIGNAL_PROG` receives it. This
is useful for a switch notifying a gate or a boss notifying its arena. Use
documented target/scope forms rather than hoping every distant host is loaded.

`MPWAIT` suspends and resumes a continuation. `MPALARM NAME=name ticks command`
creates a named saved alarm; `MPALARMCANCEL name` removes it and `ALARM()`
reports remaining time. Telegraphs build on this for cancellable windups.
The queues and supported delayed continuations survive saves; ordinary locals
and function parameters do not become general persistent memory. Model long
stories as explicit stages and saved variables.

Use `EVERY_PROG` for a saved game-time repeating schedule with catch-up.
TURN_PROG runs on game turns. `MPPASSTIME` advances game time; `MPWHILE` has a
real-time bound and is not the same as a bounded WHILE block. `ISRL…` functions
read the computer clock; use DATETIME/GAMETIME/ELAPSED and the area's calendar
for in-world deadlines.

## Dialogue input

`MPMENU` displays filtered numbered choices and later calls a FUNCTION_PROG
with the selected key in `$0` and the answering character in `$n`. It does
**not** block the rest of the initiating program. Put dependent work in the
answer function. MPPROMPT, MPCONFIRM and MPCHOOSE also have terminal behavior;
background party characters forward input to the shared terminal.

Pending menus save the displayed options and source binding. Restore reissues
the prompt; a changed or missing source cancels it with an explanation. New
menu/narration input replaces another interaction on that input stream.
Avoid overlapping prompts from several NPCs. See the [dialogue recipe](cookbook.md#2-a-numbered-npc-conversation).

## Party and language awareness

ISPLAYED, INPARTY, PARTYSIZE, PARTYLEADER and PARTY distinguish the current
character, active characters and the group. A follower is not necessarily a
playable profile character. Use `$u` when you mean the terminal, and `$n`
when you mean the actor. Award quest/discovery credit through their sharing
policies instead of an extra ad hoc loop that grants the same reward again.

Unknown speech does not enter ordinary SPEECH or MudChat handling on a
monolingual listener. NOTUNDERSTOOD provides a response; UNDERSTANDS reports
comprehension. `MPSAYIN` selects a language for one line, and `MPLEARNLANG`
teaches or adjusts proficiency. See [language rules](../languages.md).

## Debugging

Run `SCRIPTCHECK` on a file or host before testing it. Use SCRIPT LIST/SCRIPT FIRE
to inspect or invoke a program, SCRIPT CALL to invoke a function, VARS to inspect
scopes, SCRIPTTRACE to follow execution, SCRIPTPROF to find expensive triggers,
and SCRIPTERRORS/DEVLOG for context. Exact usage is in the [tool reference](tool-reference.md).

Static checking validates known command shapes, common function arity, block
balance and variable reads with no known assignment. It cannot prove external
state, dynamic command construction, timing or the correctness of your story.
Test wrong paths, two identical hosts, combat interruption, save/restore and
both game-time modes.

## Current file-path lint limitation

Runtime LOAD supports `world:scripts/name.script`, but the static checker's
file resolver currently assumes a resource path. SCRIPTCHECK/WORLD CHECK SCRIPTS
can therefore report “no such file” for a valid world:scripts source. This is a
confirmed [audit gap](../../audit/developer-tools-audit.md#open-script-lint-path-gap).
For a file-backed script that also works through the checker, place it at
`world/resources/progs/name.script` and use `LOAD=progs/name.script` and
`SCRIPTCHECK progs/name.script`. The resource overlay keeps it world-owned.
Do not ignore other diagnostics just because this path issue is known.
