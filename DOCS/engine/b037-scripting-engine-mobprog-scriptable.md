# MOBPROG scripting engine (Scriptable)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b037` (Scripting engine (MOBPROG / Scriptable)) · 2 files · 17243 lines of Java.

Citation convention: `DefaultScriptingEngine.java:N` means `com/planet_ink/coffee_mud/Common/DefaultScriptingEngine.java:N`, and `ScriptingEngine.java:N` means `com/planet_ink/coffee_mud/Common/interfaces/ScriptingEngine.java:N`. Other paths are relative to the CoffeeMUD folder. The manual is `guides/Scriptable.html` (CoffeeMud 5.11). Line numbers were read from the files, not from memory.

## Summary

The scripting engine (MOBPROG, called `Scriptable` or `ScriptingEngine` in code) is a small, line-oriented language that builders attach to mobs, items, rooms and areas as text. A script is a set of event triggers (61 keywords such as `GREET_PROG`, `SPEECH_PROG`, `GIVE_PROG`, `RAND_PROG`, `TIME_PROG`), each followed by a body of commands. The engine receives game messages and ticks, decides whether a trigger applies (keyword list, zapper mask or percentage), and runs the body at once or after a short queued delay. Bodies use `IF`/`ELSE`/`ENDIF`, `SWITCH`, `FOR`, `MPWHILE`, about 100 `MP*` commands, and 119 condition functions, which can be used in tests or substituted into text. Variables are scoped (global, per-object, or per-quest), and an optional Rhino JavaScript block sits behind an approval gate. Much of CoffeeMud's hand-written NPC, room and quest behaviour is written with this engine (the content itself is outside this bucket and was not surveyed), so it is the main place where game logic is authored as data. For a Python SUD it is a useful model for event-driven content, but its stringly typed syntax, global mutable state, and variables keyed by name are things not to copy.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `ScriptingEngine.java` (interface) | Public contract. Name tables: `progs` (430-493, 61 entries incl. the XML boundary), `funcs` (496-617, 119), `methods` (620-723, 101). `SPECIAL_*` slot constants (420-427). `MPContext` (732-807). `SubScript` (362-418) and `ScriptLn` (344-353). Extends `Tickable` and `MsgListener` (48). | 808 |
| `DefaultScriptingEngine.java` | The whole interpreter. Fields 84-118. Parsing 1042-1275. Argument parser `parseBits` 643-791. Variables `getVar`/`setVar` 896-928, 2943-3085. Substitution `varify` 2191-2563. Expression parser `parseEval` 3087-3367. Evaluator `eval` 3666-7051. Value functions `functify` 7069-9083. Helpers 9085-9197. Command executor `execute` 9200-13721. Message dispatch `okMessage` 13877-14154, `executeMsg` 14265-15667. Ticks `tick` 15728-16010. Queue 16023-16174. JavaScript bridge 16181-16434. | 16435 |
| `DefaultScriptingEngine.Signs` / `Connector` | Comparison signs (131-165) and logical connectors with a 5x5 merge table (170-201). | (within 16435) |
| `DefaultScriptingEngine.ScriptableResponse` | A queued body with a tick countdown and a hash used for de-duplication (208-280). | (within) |
| `DefaultScriptingEngine.SubScriptImpl` | A trigger plus its lines; resolves its trigger code through `progH` (282-391). | (within) |
| `DefaultScriptingEngine.JScriptEvent` | The `host`/`monster`/`source` bridge exposed to `<SCRIPT>` JavaScript (16181-16434). | (within) |
| `Behaviors/Scriptable.java` (outside bucket) | The behaviour that holds the script text and creates the engine (`CMClass.getCommon("DefaultScriptingEngine")` at line 53). Not read here. | n/a |
| `core/CMSecurity.java:1070` (outside) | `isApprovedJScript`, the gate for `<SCRIPT>` blocks. Checked by grep only. | n/a |

## How it works

### 1. Script text, storage and parsing

- A script is one string. `setScript` (1042-1057) replaces single quotes with backticks (1044) so quotes survive storage, and it builds a cache key from the first 100 characters, the length and the hash (1052-1055). A leading `+` appends to the existing script (1045-1049).
- `getScripts` (13824-13844) restores the backticks (13837), parses the text on first use and stores the result in the shared `Resources` table under that key (13841). Anything that changes the text must reset this cache (`reset`, 1023-1039, and `MPUNLOADSCRIPT`, 10815-10840 in `execute`).
- `LOAD=path` pulls in other files (`parseLoads` 1073-1128; nesting capped at depth 10, 1078). Files are read through `getResourceFileData` (986-1015). Several `LOAD=` lines in one string are joined with `~`.
- Line grammar (`parseScripts` 1160-1275), a small state machine:
  - a newline ends a command in file form (1197-1205);
  - a `;` ends a command in inline form (1215-1222);
  - a `~` ends a trigger block (1223-1232);
  - a `#` at the start of a line starts a comment (1206-1212);
  - `<SCRIPT>` ... `</SCRIPT>` holds raw JavaScript (1233-1260);
  - a trailing `<?xml version="1.0"?>` section holds embedded MOB or ITEM XML (1181-1186, 1267-1273).
- Characters `; ~ # < \` must be escaped (`deEscapeParseChars`, 1151; the same list is in the guide).
- A block that fails to parse at run time is flagged `SYNTAX_ERROR` (1370-1371) and is skipped on later runs (9210-9211).

### 2. Data model

- `SubScript` (ScriptingEngine.java:362-418) is a list of `ScriptLn` entries (344-353). Entry 0 is the trigger line. Each entry has `first` (trimmed text), `second` (the word array, parsed lazily), and `third` (a per-line cache).
- The `third` slot holds compiled control flow, so the interpreter rewrites its own parse results as it runs:
  - `IF` keeps a triad of then-block, else-block and the `ENDIF` line (9319-9494);
  - `SWITCH` keeps a map from case value to line, with the special keys `$FIRSTVAR`, `$DEFAULT`, `$ENDSWITCH` (9510-9694);
  - `FOR` keeps a pair of body and end line (9695-9931);
  - `MPCALLFUNC` keeps the resolved function (13369-13395);
  - the trigger line keeps a boolean array marking which arguments contain `$` (14156-14231);
  - `MPWHILE` writes its parsed condition back into the line (13396-13520).
- `parseBits(line, codes)` (643-791) is the argument parser. The codes are: `c` a clean word (`C` upper-cased); `r` the rest of the line, cleaned; `p` the rest raw; `s` and `t` split into several words. Each command states its argument shape as a short string, for example `"Cp"` for `MPECHO` (9992) or `"CCcr"` for `MPSET`-style commands.
- `MPContext` (ScriptingEngine.java:732-807) is the execution frame. Fields: `scripted` (the host object), `monster` (the mob that acts; always a `MOB`), `source`, `target`, `primaryItem`, `secondaryItem`, `msg` (the `$g` text), `tmp[12]` (locals `$0`-`$9`; slot 9 is also the shop-price slot written by `SHOPHAS` (`SPECIAL_9SHOPHASPRICE`, 427), so `$9` can collide with it; slots 10 and 11 hold the cached random PC and random anyone, 423-425), `script`, `line` and `parent`. `push()` (787-799) reuses the frame for the first script and clones it for nested blocks.

### 3. Triggers and dispatch

There are three entry points.

- **`okMessage`** (13877-14154) can veto. Only two triggers run here: `CMDFAIL_PROG` (51; 13907-13968) and `CNCLMSG_PROG` (42; 13969-14109). If the script returns `CANCEL`, the message is refused (13962-13963, 14144-14145).
- **`executeMsg`** (14265-15667) handles all other message triggers, about 50 cases. In `executeMsg` a matching trigger enqueues its body and then returns, so by default only the first matching trigger fires for a message (in `okMessage` every matching `CMDFAIL_PROG` and `CNCLMSG_PROG` is tried). `MULTITRIGGERS` (set through `MPSETINTERNAL`, 10063-10065) lets several fire.
- **`tick`** (15728-16010) handles the time and state triggers: `RAND` (5), `ONCE` (6), `FIGHT` (7), `HITPRCNT` (11), `TIME` (14), `DAY` (15), `DELAY` (16), `QUEST_TIME` (13).

Guards that apply to all of these:

- `isFreeToBeTriggered` (1059-1071). A script on a mob runs only when `CMLib.flags().canFreelyBehaveNormal` is true, unless `ACTIVETRIGGER` is set (1061-1069).
- `canTrigger` (15717-15726). `MPNOTRIGGER` (12105-12142) suppresses a trigger code for a number of milliseconds.
- `recurseCounter` (13880, 14267). A message handled inside a handler is ignored beyond depth 2.
- Per-message reuse: `lastMsg` (14552-14553, 14574-14575 and others) stops one message from triggering the same give, get or cast script twice.

**Subject matching.** Most keyword triggers share one argument grammar, `standardTriggerCheck` (14156-14231):

- `ALL` or no argument matches anything (14180-14181).
- `P <name>` matches the exact name or ID of the subject (14196-14204).
- `Z <zapper mask>` runs a mask check through `CMLib.masking()` (14207).
- A word list matches when any word appears as a whole word in the subject's name. The code pads both strings with spaces (14177-14178, 14226).

**Percent and mask gates** (`getTriggerPercent`, 14233-14247): a leading digit is a percent (`rollPercentage() < n` in the caller); `ALL` or `P ALL` returns 101 (always); `P <mask>` and a bare non-digit are mask checks against the subject; a mask match returns 101, a miss returns 0.

**Speech.** `SPEAK_PROG` (14412-14473) and `SPEECH_PROG` (14474-14537) take the spoken sentence (`CMStrings.getSayFromMessage`), upper-case it, strip colour and match words as whole words with `" "+word+" "` (14463, 14525). `P phrase` needs the whole sentence to match (14453, 14517).

**Message-driven triggers that are not keyword matches.** `GREET` (14309) fires when someone enters the host's room or area. `ARRIVE` (14331) fires when the host itself is the source of an enter message. `ENTRY` (15244) fires when an enter message involves the host (as source, target or tool, or when the host is an item). `EXIT` (15281) fires on a leave message for the host's room. `DEATH` (15316) and `KILL` (15333) use the death message (`TYP_DEATH`), with the killer in `msg.tool()`. `DAMAGE` (15350) puts the damage value into `$g`. `LEVEL` (15388) fires when the new level in `msg.value()` is above the source's current level (15396). `CAST` (14583) and `CASTING` (14606) check that `msg.tool()` is an `Ability`.

**`MASK`, `ACT` and `IMASK`.** These three share one body (15435-15502), which filters the text of the message the host sees through `fullOutFilter` and strips punctuation (15463-15464). `ACT_PROG` (18) and `MASK_PROG` (12) fire on messages the host did not cause. `IMASK_PROG` (43) fires only on messages the host caused (`msg.amISource(monster)`, 15447). The guide describes `act_prog` and `imask_prog` this way; `mask_prog` appears only in its index.

### 4. Queueing and timing

- Most triggers call `enqueResponse` (16081-16106) with a 1-tick delay (for example 14324, 14405). The body is wrapped in a `ScriptableResponse` (208-280). Each tick its countdown drops by one (`checkTimeToExecute`, 243-246), and it runs when the countdown reaches zero in `dequeResponses` (16123-16174). With `SCRIPTABLEDELAY` disabled, `noDelay` is true (84) and the body runs at once (16098-16099).
- Queue protection (`dupCheckClear`, 16023-16079):
  - an identical pending response is dropped (hash built at 270-279 from trigger, host, source, target, items, script and message);
  - the limit is 25, or 150 for an area host (16027-16028);
  - each pre-queued alarm (trigger code below zero) adds 0.75 to the limit (16042-16043);
  - above the limit the newest entries are removed and written to a "Queue Log" (16051-16077).
- Special rules:
  - `ENTRY` and `EXIT` run the previous pending body for the same script and source immediately, then queue the new one (15259-15273, 15294-15308);
  - `ARRIVE` first removes this script's own queued responses when the host is an item carried by the message's source (14350-14361), then queues the new one, so an item that keeps moving with its owner does not pile up work.
- `SPAWN` scripts (set by `MPPROMPT`, `MPCONFIRM`, `MPCHOOSE` and `MPLLM`, lines 10089, 10113, 10138, 12745) run on a separate thread when released from the tick path (16144-16156), so that they can wait for a player or a model reply.
- Constants:
  - `TIME_TICK` is 4000 ms (`core/CMProps.java:681`), so the default delay is about 4 seconds;
  - prompts wait 360000 ms (10097) for `MPPROMPT` and 60000 ms (10122, 10148) for `MPCONFIRM` and `MPCHOOSE`;
  - `MPWHILE` stops after 4000 ms (13485, 13514);
  - a `FOR` loop that runs longer than 10 seconds is cut off (9893, 9906).

### 5. Execution (`execute`, 9200-13721)

- Each line's first word is looked up in `methH` (9225), which is built from the `methods` table with 1-based indexes (1140-1141). A `MP`-prefixed word that is not found is matched by prefix against the table (9226-9233). The loop has no `break`, so the last match wins; see Problems.
- Unknown words are not errors. The default branch (13706-13712) substitutes the line and passes it to the monster as a game command, flagged `METAFLAG_MPFORCED`. This is how plain lines such as `say`, `yell` or `emote` work in the sample scripts.
- Block commands recurse with `execute(ctx.push(sub))`. `IF` does this at 9486, `SWITCH` at 9686, and `FOR` at 9901. A non-null return value from a block ends the parent (9487-9491).
- `BREAK` returns the string `"BREAK"` (9932-9936), which `FOR` consumes (9919-9921). `RETURN` returns its substituted value (11513-11517). `CALLFUNC` and `MPCALLFUNC` use the return value; `CANCEL` vetoes a message, and a `callfunc` result of `cancel` is false (4291-4292).
- Errors go to the server log and to the room. `logError` (1367-1391) writes `Scripting Error: <name>/<room>/<command>/<type>/<message>/<script>` to the room (1383) with `MSG_OK_VISUAL`. Builders see errors in play, and players in the room see them too.
- `tickStatus` is set to the current command or function code while the script runs (9238, 3725, 15784 and the `functify` equivalent at 7087). It lets the server show what a stuck script is doing.

### 6. Conditions (`parseEval` and `eval`)

- `parseEval` (3087-3367) turns a condition into a flat token list. Function names are followed by `(`, args and `)`. Comparison signs are read by `Signs` (131-165): `=` or `==`; `>=` or `=>`; `>`; `<`; `<=` or `=<`; `!=` or `<>`; and `.in.` for whole-word containment. Connectors are `AND`, `OR`, `NOT`, `ANDNOT` and `ORNOT`. A `!` becomes `NOT` (3142-3147). An unknown word throws `ScriptParseException` (3127).
- `eval` (3666-7051) walks the tokens with a stack of booleans and connectors. Adjacent connectors are merged with the 5x5 table `Connector.Map` (193-200). Each function is one `case` keyed by its position in `funcs`. For example, `rand` (3729-3740) is `rollPercentage() < n`, and `has` (3741-3790) checks for an item in a mob, room or container.
- Comparisons:
  - numbers use `simpleEval` (1429-1465) with `CMath.s_long`, which returns 0 for text (core/CMath.java:1978-1987);
  - words use `simpleEvalStr` (1393-1427) with case-insensitive comparison;
  - `.in.` tests whole-word containment (1416-1423);
  - maths uses `simpleExpressionEval` (1467-1496) with `CMath.s_parseMathExpression`.
- `functify` (7069-9083) is the text-valued half. The same function names return text, used as `$%NAME(args)%` inside strings (2496-2506). Examples: `$%ISHERE()%` gives the area name (`functify` case 36, 8516-8521; the guide documents the same), and `$%HAS(...)%` gives the name of a random matching item (7096-7133).

### 7. Variables and substitution

- `varify` (2191-2563) expands `$` codes in almost every argument. The table of codes is in "Content worth mining".
- After a code you can add modifiers (2508-2556): `.LENGTH#` (word count), `.DECOMMA#`, `.DESEMICOLON#`, `.n` (the nth word) and `.n..` (word n to the end). A value that itself contains `$` is rewritten to `_` so that substitution cannot recurse (2557-2558).
- Storage. Values are kept in `Resources` under `SCRIPTVAR-<host>` (`getVar` 909-928, `setVar` 2943-3085). The scope set by `SCOPE` (setVarScope, 429-452) decides which table is used:
  - `""` is global (`VARSCOPE-*GLOBAL*`, 901-906);
  - `*` is individual, a new table for each engine (439-440);
  - any other name is a shared named scope;
  - a quest-bound script uses its quest's scope (411-427).
- Host key. `getVarHost` (851-864) uses the extended room ID for rooms and the display name, `E.Name()`, for everything else (858-861). Two mobs with the same name therefore share all their variables. See Problems.
- Operators in `setVar` (2985-3077): the value `++` or `--` adds or subtracts one from the stored integer; `+N`, `-N`, `*N` and `/N` apply arithmetic (division by zero is logged, 3065). Any other value is stored as text. An empty value deletes the key (3080-3083).
- Persistence. `MPSAVEVAR` (12785-12823) writes per-host rows, keyed `<HOST>_SCRIPTABLEVARS_<name>`, into the area data table under `SCRIPTABLEVARS`. `MPLOADVAR` (12824-12859) reads them back. For a player host it uses the player's own database thread (12795-12799). Local XML export and import (467-516) handle the local scope.
- `getStatValue` and `getGStatValue` (2608-2852) let `STAT` and `GSTAT` read any `CharStats`, `PhyStats`, `CharState` or `PlayerStats` field by code, plus generated "fake" stats for the builder's generic object system (2679-2689, 2813-2817).

### 8. Non-mob hosts (proxy actor)

Rooms, items and areas cannot act, so `getMakeMOB` (15669-15715) supplies a shared `StdMOB` named after the host, with display text `"<name> is here."` (15694), placed in the host's room. Commands run as this proxy. The result is that a room or an item can use the full mob command set, and that messages are attributed to an invisible NPC.

### 9. JavaScript blocks (`<SCRIPT>`)

- The block is run in Mozilla Rhino (imported at 36). It runs only if the script was pre-approved (`preApproveScripts`, 460-463) or if `CMSecurity.isApprovedJScript` accepts the source (9276). Otherwise, when the server is set to require approval, the room is shown a message that the script was not authorised and that an admin must authorise it with `MODIFY JSCRIPT` (9312-9316).
- Names exposed to the script (9283-9297): `host`, `source`, `target`, `monster`, `item`, `item1`, `item2`, `message`, `objs`, `getVar`, `setVar`, `toJavaString`, `getCMType`.
- `JScriptEvent.get` (16265-16420) makes MP commands callable by name. The call is turned into a one-line script and run (16270-16295). A name ending in `$` calls `functify` (16296-16311). Any other function name calls `eval` and returns a boolean (16312-16319).

### 10. Player prompts and the LLM hook

- `MPPROMPT` (10085-10108), `MPCONFIRM` (10109-10133) and `MPCHOOSE` (10134-10159) ask a logged-in player for input through `session().prompt`, `confirm` and `choose`, and store the reply in a variable.
- `MPLLM` (12741-12784) sends a prompt to a model through `ProtocolLibrary.createLLMSession` with the `MOBPROG` category (12772-12773) and stores the reply in a variable with `llmSession.chat`. The session settings come from `MPSETINTERNAL LLM...` keys (10079-10080) and the names `LLMPROMPT` and `LLMMAXMSGS` (12766-12769). The guide warns that the call can be slow.

### 11. Quests and time

- Quest hooks. `stepEndQuest` (793-829) runs every `QUEST_TIME_PROG` whose quest name matches and whose minutes argument equals the requested code (-1 for a step, -2 for an end, 806-823). `endQuest` and `stepQuest` (832-841) wrap it. In `tick`, `QUEST_TIME` fires when the quest's `minsRemaining()` is at or below the given minutes (15979-16001).
- Clock triggers. `TIME_PROG` (hours) and `DAY_PROG` (days of the month) read the area's `TimeClock` (15920-15978). They remember the last value that fired for each script (`lastTimeProgsDone`, `lastDayProgsDone`), so that each matching hour or day fires once.
- `DELAY_PROG` picks a random tick count between the two arguments (`roll(1, high-low+1, low-1)`, 15829), counts down on each tick, fires, and removes its entry (15830-15841). The next tick therefore starts a new random countdown, so the trigger repeats. `ONCE_PROG` is the variant that fires once (`oncesDone`, 15910-15919).
- `MPALARM` queues a body to run after a number of ticks (13521-13542). The guide says the unit is a 4-second interval, which agrees with `TIME_TICK`.

### 12. Who owns the engine

- `ScriptingEngine` is both `Tickable` and `MsgListener` (ScriptingEngine.java:48). Any host that carries an engine delivers messages and ticks to it. The holder is `Behaviors/Scriptable.java` (outside this bucket).
- Other callers that create or run engines:
  - `Commands/MPCommand.java:62-70` runs a script typed by a builder, with the `MPRUN` message;
  - `Behaviors/MudChat.java:1024-1030` uses `varify` on its own, for substitution only;
  - `Commands/ClanAccept.java:133-140` runs a clan entry script with the `CLANENTRY` message, and several other clan commands do the same.
- Lifecycle. `copyOf` (617-631) and `reset` (1023-1039) make a new instance for each host and clear the queue, the once-only list and the cached blocks.

## Content worth mining

### Trigger catalogue (61 keywords)

Argument shapes used in the table: **KW** is a keyword list, `P` phrase, `ALL` or `Z` mask (see section 3). **% or Z** is a percent or zapper mask. "Queued" means the body runs one tick later unless `SCRIPTABLEDELAY` is off.

| Code | Trigger | Argument | Fires when | Handled at |
|---|---|---|---|---|
| 1 | GREET_PROG | % or Z | someone enters the host's room | 14309 |
| 2 | ALL_GREET_PROG | % or Z | someone enters, and the monster can act | 14369 |
| 3 | SPEECH_PROG | KW, P, ALL | another entity speaks | 14474 |
| 4 | GIVE_PROG | KW, Z, ALL | an item is given to the host | 14538 |
| 5 | RAND_PROG | % (integer only) | each tick | 15795 (tick) |
| 6 | ONCE_PROG | none | once per script | 15910 (tick) |
| 7 | FIGHT_PROG | % | each tick while in combat | 15844 (tick) |
| 8 | ENTRY_PROG | % or Z | an enter message involves the host (as actor, target, tool, or host is an item) | 15244 |
| 9 | EXIT_PROG | % or Z | someone leaves the host's room | 15281 |
| 10 | DEATH_PROG | none | the host dies (runs at once) | 15316 |
| 11 | HITPRCNT_PROG | % | each tick in combat, when HP is at or below % of max | 15876 (tick) |
| 12 | MASK_PROG | KW | a message the host did not cause | 15435 |
| 13 | QUEST_TIME_PROG | quest name, minutes | the quest's minutes remaining are at or below the value | 15979 (tick), 806 |
| 14 | TIME_PROG | list of hours | the area's hour matches | 15920 (tick) |
| 15 | DAY_PROG | list of days | the area's day matches | 15950 (tick) |
| 16 | DELAY_PROG | min, max ticks | after a random countdown; the next tick starts a new countdown, so it repeats | 15810 (tick) |
| 17 | FUNCTION_PROG | function name | never fires by itself; callable | no case |
| 18 | ACT_PROG | KW, `p` exact | a message the host sees (not its own) | 15441 |
| 19 | BRIBE_PROG | value, ANY, ALL | coins are given | 15212 |
| 20 | GET_PROG | KW | an item is taken from the host or its contents | 14867 |
| 21 | PUT_PROG | KW | an item is put into the host (container) | 15100 |
| 22 | DROP_PROG | KW | an item is dropped in the host's room | 14980 |
| 23 | WEAR_PROG | KW | an item is worn, held or wielded by the host | 15194 |
| 24 | REMOVE_PROG | KW | an item is removed | 15026 |
| 25 | CONSUME_PROG | KW | the host eats or drinks an item | 15080 |
| 26 | DAMAGE_PROG | none | the host takes damage (value in `$g`) | 15350 |
| 27 | BUY_PROG | KW | a shop sale to a customer | 15151 |
| 28 | SELL_PROG | KW | a customer sells to the shop | 15173 |
| 29 | LOGIN_PROG | % or Z | a player logs in | 15364 |
| 30 | LOGOFF_PROG | % or Z | a player quits | 15416 |
| 31 | REGMASK_PROG | Java regex, `p` exact | a message matches the pattern | 15609 |
| 32 | LEVEL_PROG | % or Z | a player's new level is above the old one | 15388 |
| 33 | CHANNEL_PROG | channel, words | a channel message | 15526 |
| 34-37 | OPEN_PROG, CLOSE_PROG, LOCK_PROG, UNLOCK_PROG | KW | the door or container changes state | 15045-15079 |
| 38 | SOCIAL_PROG | social name | a social performed by another entity whose name starts with the argument | 15503 |
| 39 | LOOK_PROG | KW | someone looks at the host | 14853 |
| 40 | LLOOK_PROG | KW | someone examines the host | 14680 |
| 41 | EXECMSG_PROG | message code, words | a message of the given code (queued) | 14694 |
| 42 | CNCLMSG_PROG | message code | can veto a message by returning `CANCEL` | 13969 (okMessage) |
| 43 | IMASK_PROG | KW | a message the host caused | 15446 |
| 44 | KILL_PROG | none | the host kills someone | 15333 |
| 45 | ARRIVE_PROG | % or Z | the host arrives in a room | 14331 |
| 46 | PUTTING_PROG | KW | a put where the host is the item, the room or the actor | 15126 |
| 47 | SPEAK_PROG | KW, P | the host speaks | 14412 |
| 48 | GIVING_PROG | KW | the host gives an item | 14561 |
| 49 | CAST_PROG | KW | an ability is cast on the host | 14583 |
| 50 | CASTING_PROG | KW | the host casts an ability | 14606 |
| 51 | CMDFAIL_PROG | command, words | a player's command fails (can veto) | 13907 (okMessage) |
| 52 | GROUP_GREET_PROG | % or Z | a group enters | 14390 |
| 53 | DROPPING_PROG | KW | the host drops an item | 15005 |
| 54 | GETTING_PROG | KW | the host takes an item | 14893 |
| 55 | RIDE_PROG | KW | someone mounts the host | 14960 |
| 56 | RIDING_PROG | KW | the host mounts | 14940 |
| 57 | WEARING_PROG | KW | the host wears an item | 14915 |
| 58 | `<?XML` | (XML section) | not a trigger; embedded XML data | 1000 (lookup bug) |
| 59 | FOLLOW_PROG | KW | a follow event where the target is a mob | 14628 |
| 60 | UNFOLLOW_PROG | KW | an unfollow event | 14646 |
| 61 | AGE_PROG | KW | an age message (calls `getAgeYears`) | 14663 |

Notes: codes are the 1-based positions in `progs` (ScriptingEngine.java:430-493). The guide's index does not list `CMDFAIL_PROG` (code 51). `DROPPING`, `GETTING` and `WEARING` also check the base trigger's suppression (14894, 14915-14919, 15006), so `MPNOTRIGGER DROP_PROG` also silences `DROPPING_PROG`.

### Condition functions (119)

Every name in `funcs` (ScriptingEngine.java:496-617) has a boolean form in `eval` (3666-7051) and also a case in `functify` (7069-9083), although several of the `functify` cases are placeholders (below). Grouped:

- **Random and maths:** RAND (1), RAND0NUM (71), RANDNUM (42), NUMBER (40), ISODD (92), MATH (84), EVAL (41).
- **Identity and state:** ISNPC (4), ISPC (5), ISNAME (7), NAME (56), ISIMMORT (11), ISALIVE (54), ISFIGHT (10), ISCHARMED (12), ISFOLLOW (15), ISGROUP (119), ISSERVANT (73), ISBEHAVE (69), AFFECTED (14), ISABLE (58), ISSPEAKING (95), ISPKILL (55), IPADDRESS (70), STRCONTAINS (86), STRIN (61), VAR (27), QVAR (83).
- **Character stats:** STAT (13), GSTAT (52), HITPRCNT (16), LEVEL (20), SEX (18), POSITION (19), CLASS (21), BASECLASS (22), RACE (23), RACECAT (24), DEITY (64), MOOD (88), EXP (78), EXPERTISE (115), PRACS (82), TRAINS (81), QUESTPOINTS (80), GOLDAMT (25), VALUE (76), CURRENCY (75).
- **Clans, factions and titles:** CLAN (65), CLANRANK (66), CLANDATA (68), CLANQUALIFIES (98), FACTION (72), HASTITLE (67).
- **Items and containers:** HAS (2), HASNUM (74), WORN (3), WORNON (97), ISCONTENT (96), INCONTAINER (53), ITEMCOUNT (111), OBJTYPE (26), ISOPEN (59), ISLOCKED (60), MOBITEM (47), ROOMITEM (44), NUMITEMSMOB (48), NUMITEMSROOM (46).
- **Tattoos and tags:** HASTATTOO (49), HASTATTOOTIME (109), HASACCTATTOO (99), HASTAG (118).
- **Location:** INROOM (17), INAREA (90), INLOCALE (37), ISRECALL (89), ISHERE (36), ROOMMOB (43), ROOMPC (116), AREAPC (117), CANSEE (112), CANHEAR (113), ISMOON (57), ISWEATHER (51), ISSEASON (50), ISTIME (38), ISDAY (39), ISHOUR (110), ISMONTH (103), ISYEAR (104), ISBIRTHDAY (87), DATETIME (91), ISRLHOUR (105), ISRLDAY (106), ISRLMONTH (107), ISRLYEAR (108).
- **Population:** NUMMOBS (33), NUMMOBSINAREA (32), NUMMOBSROOM (45), NUMPCSROOM (63), NUMPCSAREA (79), NUMRACES (35), NUMRACESINAREA (34), HASNUM (74), EXPLORED (77).
- **Shops:** SHOPITEM (100), NUMITEMSSHOP (101), SHOPHAS (102).
- **Quests:** QUESTWINNER (28), QUESTMOB (29), QUESTOBJ (30), ISQUESTMOBALIVE (31), QUESTROOM (94), QUESTAREA (114), QUESTSCRIPTED (93).
- **Scripts and masks:** CALLFUNC (62). ISLIKE (85) tests a zapper mask in `eval` (4882-4894) and, in `functify`, returns the mask as a readable description (8684-8689).

Placeholders in the text form: `ISNPC` (4), `ISPC` (5), `ISIMMORT` (11), `EVAL` (41) and `STRCONTAINS` (86) return `[unimplemented function]` in `functify` (7215-7216, 7291, 9002, 8692). `CANSEE` (112) and `CANHEAR` (113) produce no text (7878-7884). The guide says `ISIMMORT` is not implemented, which matches.

### Commands (about 100)

Names as they appear in `methods` (ScriptingEngine.java:620-723). Grouped by effect.

- **Output:** MPECHO (2), MPECHOAT (7; alias MEA 87), MPECHOAROUND (8; alias MER 88), MPASOUND (1), MPLOG (66), MPCHANNEL (67). `NOSOUND ` before the text sends visual output only (for example 9998-10002).
- **Combat and health:** MPKILL (10), MPHIT (98), MPDAMAGE (28), MPHEAL (78), MPSLAY (3), MPSTOP (56), MPEXP (11), MPRPEXP (86).
- **Magic and affects:** MPCAST (9), MPCASTEXT (89), MPAFFECT (30), MPUNAFFECT (13), MPENABLE (37), MPDISABLE (38), MPSPEAK (80).
- **Movement and rooms:** MPGOTO (14), MPAT (15), MPTRANSFER (17), MPFORCE (18), MPTRACKTO (29), MPWALKTO (53), MPBEACON (25), MPLINK (90), MPUNLINK (91), MPHIDE (42), MPUNHIDE (43), MPOPEN (44), MPCLOSE (45), MPLOCK (46), MPUNLOCK (47), MPPURGE (12), MPJUNK (4), MPPUT (92).
- **Loading and resetting:** MPMLOAD (5), MPOLOAD (6), MPOLOADROOM (41), MPOLOADSHOP (84), MPMLOADSHOP (85), MPRESET (58), MPREJUV (71), MPM2I2M (40; cage or uncage a mob).
- **Character data:** MPSET (16), MPGSET (35), MPSETVAR (20), MPARGSET (63), MPSETINTERNAL (73), MPMONEY (77), MPQUESTPOINTS (59), MPTRAINS (60), MPPRACS (61), MPPLAYERCLASS (52), MPTITLE (49), MPFACTION (54), MPTATTOO (33), MPACCTATTOO (83), MPSETCLAN (81), MPSETCLANDATA (51), MPACHIEVE (99), MPACCUSE (100), MPPOSSESS (79).
- **Variables and persistence:** MPSAVEVAR (36), MPLOADVAR (39).
- **Scripts and behaviours:** MPSCRIPT (72), MPUNLOADSCRIPT (68), MPBEHAVE (31), MPUNBEHAVE (32), MPNOTRIGGER (55).
- **Quests:** MPSTARTQUEST (23), MPENDQUEST (21), MPSTEPQUEST (69), MPQUESTWIN (22), MPQSET (65), MPLOADQUESTOBJ (64).
- **Prompts and models:** MPPROMPT (74), MPCONFIRM (75), MPCHOOSE (76), MPLLM (101).
- **Control:** IF (19), ELSE, ENDIF (93), SWITCH (70), CASE (96), DEFAULT (97), ENDSWITCH (94), FOR (62), NEXT (95), MPWHILE (27), MPCALLFUNC (24), MPALARM (26), RETURN (48), BREAK (34 and 50; the table has it twice), `<SCRIPT>` (57).
- **Unused:** MPRLOAD (82) is in the table but has no case in `execute`.

### Substitution codes (`varify`, 2191-2563)

| Code | Meaning |
|---|---|
| `$0`-`$9` | local slots `tmp[0]`-`tmp[9]` (2212-2213) |
| `$$` | a literal `$` (2215-2219) |
| `$n` `$N` | source name (2348-2353) |
| `$i` `$I` | monster name, display text (2288-2295) |
| `$t` `$T` | target name (2385-2389) |
| `$o` `$O`, `$p` `$P` | primary, secondary item name (2353-2362) |
| `$q` `$Q` | host name, display text (2363-2370) |
| `$d` `$D` | room display text, description (2251-2256) |
| `$a` | area name (2230-2232) |
| `$b` `$B` | last loaded object name, display text (2239-2244) |
| `$c` `$C`, `$r` `$R` | random anyone, random PC name (2245-2250, 2371-2376) |
| `$e` `$E`, `$s` `$S`, `$m` `$M`, `$y` `$Y` | pronouns of source and target (2257-2264, 2377-2384, 2340-2347, 2427-2434) |
| `$h` `$H`, `$j` `$J`, `$k` `$K` | pronouns of monster and a random PC (2279-2313) |
| `$f` `$F` | the monster's leader name and pronoun (2265-2272) |
| `$g` `$G` | message text, lower-case and as sent (2273-2278) |
| `$l` `$L` | quoted names of visible mobs and loose items in the room (2314-2339) |
| `$w` `$W` | owner name of the primary or secondary item (2390-2395) |
| `$x` `$X` | a random exit, or the exit in a direction (2396-2426) |
| `$<scope name>` | a stored variable (2435-2455) |
| `$[n quest]`, `${n quest}` | quest item or quest mob name (2456-2495) |
| `$%NAME(args)%` | a `functify` value (2496-2506) |
| `$@x` | the object named by `$x`, as text (2221-2228) |

Modifiers after a code: `.LENGTH#`, `.DECOMMA#`, `.DESEMICOLON#`, `.n` and `.n..` (2508-2556).

### Sample script (illustrative, written for this note)

```
GREET_PROG 50
IF RAND(30)
say Welcome to my shop, $n.
ELSE
MPECHOAT $n The shopkeeper nods at you.
ENDIF
~
```

The greet check is a 50% roll, and the `RAND(30)` inside is a separate 30% roll. The body runs one tick after the greet. `say` is not an `MP` command, so it passes through the default branch as a game command (13706-13712). Real examples are in `resources/progs/`: 85 files under `resources/` mention `GREET_PROG`, `RAND_PROG` or `SPEECH_PROG`. `mrinfo.script` is a speech demo (its header comment says so), and `blackjack.script` appears to be a card game from its name. Only the first 45 lines of `mrinfo.script` were read.

## Notable design ideas

- **Two-phase vetoes.** Triggers that can cancel an action run in `okMessage` and can return `CANCEL` (13962-13963). Other triggers run after the event in `executeMsg`. This mirrors the engine's message model, where every listener first sees an offer and then an execution.
- **One argument grammar for subjects.** `ALL`, `P name`, `Z mask`, a keyword list and a percent all work the same way on every event (14156-14247). A builder learns it once, and it fits both players and objects.
- **Scripts are data that compile themselves.** The text is parsed lazily, the parse is cached in the shared resource table (13824-13844), and the interpreter caches block structure in the line's `third` slot. The first run of an `IF` or `FOR` costs more, and later runs are cheap.
- **Deferred execution with protection.** Queued bodies have a tick delay, identical pending bodies are dropped, and the queue has a cap (16023-16079). This limits the damage from a runaway chain of triggers.
- **Scopes.** Variables can be global, per-object, or per-quest (section 7). Quest-bound scripts use their quest's variable table (411-427), which is a way for several scripts on one quest to share state.
- **Proxy actor for rooms and items.** `getMakeMOB` (15669-15715) gives every non-mob host a mob, so one command set serves all of them.
- **Unknown lines become commands.** Anything the table does not know is sent to the monster as a game command (13706-13712). Scripts can therefore call any game command, and a builder does not have to learn a separate API for plain actions.
- **Time and state triggers.** `TIME`, `DAY`, `QUEST_TIME`, `HITPRCNT` and `FIGHT` make the world react to its own state, not just to player actions (15728-16010).
- **Player input as a trigger value.** `MPPROMPT`, `MPCONFIRM` and `MPCHOOSE` store what a player types in a variable (10085-10159), and `MPLLM` does the same for a model's reply (12741-12784). A script can therefore ask a question and act on the answer.
- **Error feedback in the world.** Script errors are shown in the room (1383), which makes problems visible to builders at once. It is also a leak to players, which is discussed under Problems.

## Problems and cruft

### Bugs found by reading (verify before relying on them)

1. **Embedded XML is looked up under the wrong trigger code.** `getResourceFileData` checks `getTriggerCode()==55` (`DefaultScriptingEngine.java:1000`). Code 55 is `RIDE_PROG` (ScriptingEngine.java:486). The embedded XML section is trigger 58 (`<?XML`, ScriptingEngine.java:489; `Libraries/interfaces/XMLLibrary.java:34`). So a named XML block at the end of a script is never found, although the guide says it exists for `MPMLOAD` and `MPOLOAD`. Line 1003 also reads the matching line's `third` slot as a `List<XMLTag>`. For a `RIDE_PROG` line that slot is null, or holds the `boolean[]` written by `standardTriggerCheck` (14173), so the loop at 1004 would throw a `NullPointerException` or `ClassCastException` if it were ever reached. Runtime behaviour is unverified.
2. **`LEVEL_PROG` fills the locals with the wrong value.** The loop at `DefaultScriptingEngine.java:15406-15408` assigns `ctx.tmp[i]=t` (the whole argument array) instead of `t[i]`. `makeNamedString` (2143-2157) then turns the array into its Java identity string, so `$0` and the other locals are not the trigger arguments.
3. **`MPDAMAGE` ignores ME and KILL when there are several option words.** The loop variable is `s4` (12917), but the tests compare `arg4` (12927, 12930). With two or more words, for example `kill fire`, the kill flag is never set. The guide's `MPDAMAGE` entry says the target survives unless `KILL` is given, which the single-word form honours.
4. **`MPACCUSE` checks the wrong object.** The second check at 13592-13595 repeats the test on `PA` (already known to be a `MOB`) instead of on `WA`. The cast at 13599 `(MOB)WA` then throws `ClassCastException` when the second name is an item.
5. **`INROOM ... BEACON` never matches.** `ctx.monster.getStartRoom();` (5829-5830) discards its result, so `R` stays null and the test fails (5861-5868).
6. **`NUMMOBS` with a zapper mask compares against zero.** The mask branch rewrites `arg3` (4757-4760), and `arg3` is then used as the comparison value at 4787. `simpleEval` parses the mask text with `CMath.s_long`, which returns 0 for text (core/CMath.java:1978-1987). The `NUMMOBSINAREA` version (4716-4719, 4745) keeps `arg3` and is correct.
7. **The first `MPLLM` call drops its text.** When no `LLMPROMPT` is set, the session-creation block blanks `arg3` (12768-12771). That call stores an empty value (12777-12781), and only later calls reach the model.
8. **Prefix matching of unknown `MP` words picks the last match.** The fallback at 9228-9232 has no `break`. `MPSE` therefore resolves to `MPSETCLAN` (the last of `MPSET`, `MPSETVAR`, `MPSETCLANDATA`, `MPSETINTERNAL`, `MPSETCLAN`). A typo can run a different command without an error.
9. **`MPRLOAD` has no implementation, and `BREAK` appears twice.** `MPRLOAD` (ScriptingEngine.java:703) has no case in `execute`, so it is forwarded as a game command (13706-13712). `BREAK` is listed at indexes 34 and 50 (ScriptingEngine.java:655 and 671); the later one wins in `methH`.
10. **`RAND_PROG` gating looks accidental.** The test at 15800-15802 fires when the percent text has more than one character or when the area is `ACTIVE`. A single-digit `RAND_PROG` therefore fires only in active areas, and it does not accept a zapper mask (15804). The intent is unverified.
11. **Null-unsafe area calls.** `NUMMOBSINAREA` and `NUMRACESINAREA` call `lastKnownLocation.getArea()` without a check (eval 4722 and 4800; functify 7770 and 7840). `NUMPCSAREA` and `AREAPC` assume `S.mob()` is not null (5199, 8246). Reachability is unverified.

### Design hazards and risks

12. **Variables are keyed by name, not identity.** `getVarHost` (858-861) and `setVar` calls such as 10100 (`MPPROMPT`) and 12731-12737 (`MPSETVAR`) use `Name()`. Two same-named mobs share all of their script variables.
13. **Shared mutable state during execution.** The engine keeps `lastKnownLocation`, `lastLoaded`, `lastToHurtMe`, `altStatusTickable`, `llmSession` and `miscArgVars` as fields (93-118). These are written during a run, and `SPAWN` scripts run on other threads (16147-16156). Concurrent runs on the same engine are possible; the effect is unverified.
14. **Scripts run inside veto checks.** `CNCLMSG_PROG` and `CMDFAIL_PROG` run synchronously in `okMessage` (13956-13961 and 14127-14142). Those scripts can run commands while a message is being checked. The recursion guard (13880) limits this but does not prevent it.
15. **Player-visible error text.** `logError` writes to the room (1383). Builders see errors, and so do players in the room. Scripts with `SYNTAX_ERROR` are skipped silently after the first failure.
16. **Global-level feature flags.** `MPSETINTERNAL` changes engine-wide flags from inside any script (10051-10082): `SCOPE`, `NODELAY`, `ACTIVETRIGGER`, `MULTITRIGGER`, `NEEDPC`, `PASSIVE` and others. Each host has its own engine, so a script can change how the other scripts on that same host behave.
17. **Exposed session address.** `IPADDRESS` (eval 4353-4366; functify 7516-7523) gives a script a player's network address. This is a privacy concern in any game that shares scripts.
18. **Whitespace- and quote-sensitive syntax.** Whether a line is a command or a continuation depends on `;` versus newline, on `~`, on `<` and on backticks (1044, 13837). Escaping rules are documented but easy to get wrong.

### Cruft

19. **Repeated code.** The "clan of this mob, or its first clan" lookup appears seven times (`DefaultScriptingEngine.java:6362, 6419, 6505, 8733, 8792, 8831, 12285`). Stat setters repeat the same search chain in `MPSET` (10160-10330), `MPGSET` (10432-10620), `getStatValue` (2608-2710) and `getGStatValue` (2712-2852). The `MASK=` prefix parsing, with `maskCompile` or `getPreCompiledMask`, appears in six places.
20. **Unused members.** `gstatH` (79) and `getBottomScriptOfType` (14249-14262) are never called.
21. **Dead branches.** `if(H==null) continue;` after an assignment that cannot be null (2978-2982). `if(str == null) break;` where `str` is never null (13920-13922). An empty `synchronized(putRoom){}` with a comment asking whether it does anything (11108-11110).
22. **Reflection in the engine.** `MPSCRIPT` finds `getScripts` through `getDeclaredMethod` (11954-11963) to read SPAWN flags. The method is `protected` and in the same class, so a direct call would do.
23. **Fall-through as control flow.** `OPEN`, `CLOSE`, `LOCK` and `UNLOCK` share a body through a chain of falls through and an `if(targetMinorTrigger<0)` test (15045-15079). It works but is hard to follow.
24. **Labels and typos.** `HASTATTOO` is used as the label for `HASTAG` (5512) and `HASTATTOOTIME` (5533). `"fuction"` at 3713 and `"CONJUCTOR"` at 7040 are misspelled.
25. **Placeholder functions.** See the note under the condition catalogue (7215-7216, 7291, 9002, 8692, 7878-7884).
26. **`eval` returns mid-stack.** Many syntax errors do `return returnable;` (for example 3751, 4658), which returns the local default (usually false) and abandons the rest of the condition. The result is a silent false.
27. **Self-rewriting code.** `MPWHILE` (13396-13520) edits its own argument array and the next line's parse. It is clever, but it makes scripts hard to reason about and to save.

## Takeaways for a Python SUD

**Borrow:**

- **The event model.** A script is a list of triggers, each with an event name, a subject filter and a body. Model "before" hooks that can refuse an action separately from "after" hooks that react (the two-phase pattern, section 3).
- **The subject grammar.** `ALL`, a name, a mask or a keyword list, with an optional percentage, can be a small Python data type with one `matches(entity)` method.
- **A tick-delayed queue with de-duplication and a cap.** Good for a turn-based or real-time loop: it stops one event from producing many identical responses.
- **Scoped variables with arithmetic.** Global, per-object and per-quest scopes, with a single `set_var(name, op, value)` for `++`, `+N` and so on. Key the store by object identity, not by display name.
- **Proxy actors for rooms and items.** Give every scriptable thing an `Actor` facade so that one command set serves all of them.
- **Unknown lines fall through to commands.** In Python this becomes a dispatch table with a fallback to "let the player's command parser handle it".
- **Time and state triggers.** Hour, day, quest-timer, HP threshold and "in combat" make the world move without player input.

**Simplify (single player):**

- Cut 61 triggers to about 20: greet and leave a room, speech and keyword, give, bribe, buy and sell, wear, get and drop, damage, death, kill, fight, hour, day, delay, random, once, and quest timer. Drop login, logoff, channel, social, clan and possession.
- Cut about 100 commands to about 30. Merge `MPSET` and `MPGSET` into one stat setter. Merge `MPECHO`, `MPECHOAT` and `MPECHOAROUND` into one "say to the room" call with a target.
- Replace prompts (`MPPROMPT`, `MPCONFIRM`, `MPCHOOSE`) with plain input, and the LLM call with an ordinary generator.
- The "needs a PC in the area" gate (`NEEDPC`, `PASSIVE`) becomes "simulate only within reach of the player", which is a real decision for a living-world design (see below).

**Drop:**

- JavaScript blocks. They add a second language and a security approval step.
- The dual `;`/newline syntax, `~` separators, backtick quoting and the `$`-code modifiers. These are the main sources of fragility.
- Name-keyed variables, self-rewriting parse caches and fall-through trigger chains.

**Python shape.** Scripts as Python functions, with a generator for delays, would remove most of the parser. A designer-facing text form can compile to the same structure:

```python
from dataclasses import dataclass, field
from typing import Callable, Optional

@dataclass
class Subject:             # "ALL", "P name", "Z mask", or keyword list
    mode: str              # "all" | "name" | "mask" | "words"
    args: tuple = ()

    def matches(self, entity) -> bool:
        if self.mode == "all":
            return True
        if self.mode == "name":
            return entity.name.upper() == self.args[0].upper()
        if self.mode == "words":
            text = f" {entity.name.upper()} "
            return any(f" {w.upper()} " in text for w in self.args)
        return self.args[0](entity)          # mask predicate

@dataclass
class Trigger:
    event: str                               # "greet", "give", "time", ...
    subject: Subject = field(default_factory=lambda: Subject("all"))
    chance: int = 100                        # percent gate, as getTriggerPercent
    once: bool = False
    body: Optional[Callable] = None          # generator: yields to wait N ticks
```

Keep the precedence rule from the engine: one trigger per event by default, with an explicit opt-in for several.

**Content and documentation.** The guide (`guides/Scriptable.html`) is long and does not list `CMDFAIL_PROG`, so the code is the only reference for that trigger. The trigger and command tables above are a usable checklist for a designer's reference.

## Coverage

- **Read in full:** `ScriptingEngine.java` (all 808 lines); `DefaultScriptingEngine.java` (all 16,435 lines, in 21 chunks of about 800 lines, from 1 to 16435).
- **Skimmed:** `guides/Scriptable.html` (text extracted to a scratch file). Read the introduction, script structure, the trigger list, the sections on `MPALARM`, `MPDAMAGE`, `ISHERE`, `MPWHILE`, `MPLLM`, and the `act`, `imask` and `mask` trigger descriptions. The rest of the guide was not read.
- **Checked by grep only (outside the bucket):** `core/CMath.java:1978` (`s_long`), `core/CMProps.java:681` (`TIME_TICK`), `core/CMSecurity.java:1070` (`isApprovedJScript`), `Libraries/interfaces/XMLLibrary.java:34` (`FILE_XML_BOUNDARY`), `Common/DefaultCoffeeShop.java:325-366` (store adds copy the item), `Common/DefaultPlayerStats.java` (`percentVisited` with a null area means the whole world), `Common/DefaultCharState.java:370-383` (stat codes are one static array), `Behaviors/Scriptable.java:53`, `Behaviors/MudChat.java:1024`, `Commands/MPCommand.java:62`, `Commands/ClanAccept.java:133`.
- **Sample resources:** `resources/progs/mrinfo.script` (first 45 lines read); `scriptableTest.script`, `blackjack.script` and others not read.
- **Not read:** the bodies of the CMLib services that the engine calls (combat, quests, shops, factions, masks, channels, LLM protocol). Only their call sites are cited.
- **Skipped:** nothing in the bucket.
