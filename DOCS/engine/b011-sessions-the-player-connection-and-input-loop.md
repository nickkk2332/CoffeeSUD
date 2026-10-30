# Sessions: the player connection and input loop

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b011` (Sessions: the player connection and input loop) · 5 files · 7682 lines of Java.

## Summary

A `Session` joins one network connection to one player `MOB`. `DefaultSession` negotiates telnet options during a short handshake, then runs as a polling state machine: `Libraries/Sessions.java` re-queues every session that is not currently running onto a worker pool every 100 ms, and each `run()` reads at most one complete line, filters it, and either hands it to a pending input callback or queues it as commands on the player's `MOB`. Typed commands carry action costs that the MOB's per-tick action pool and speed throttle, so pacing comes from data rather than from per-command timers. The session also shapes output (word wrap, paging, duplicate collapse, the prompt), drives login and logout, and runs a watchdog that kills stuck sessions. `FakeSession` is a socket-less version for scripted and test use.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Common/DefaultSession.java` | The live connection: socket setup, telnet/MCCP/MXP/MSDP/GMCP/MPCP negotiation, byte-to-line input, prompts and output, login and logout sequencing, `run()` dispatch, 34 named stat codes | 4629 |
| `Common/interfaces/Session.java` | The `Session` contract; telnet, MTTS and `SessionStatus` constants; `InputCallback` and `TickingCallback` | 1557 |
| `Common/FakeSession.java` | Session without a socket: output goes to memory or to a file named by the group argument, input comes from a queue | 856 |
| `Libraries/Sessions.java` | Per-thread-group registry of sessions, the 100 ms scheduling tick, and the watchdog (`sessionCheck`) | 414 |
| `Libraries/interfaces/SessionsList.java` | Registry API: add, remove, iterators, lookup by character name | 226 |

Collaborators outside this bucket that the input loop depends on: `MOBS/StdMOB.java` (command queue, action pool), `Libraries/EnglishParser.java` (`findCommand`), `Libraries/CoffeeFilter.java` (`simpleInFilter`, `fullOutFilter`), `Libraries/CoffeeUtensils.java` (`deAlias`, `buildPrompt`), `Libraries/DirtyLanguage.java` with `resources/parser_en_TX.properties` (command pre-processor), `Libraries/CharCreation.java` (login menu), `application/MUD.java` (acceptor), `core/threads/ServiceEngine.java` (worker pools), and `core/CMSecurity.java` (connection throttle).

## How it works

### 1. From socket to worker thread

- `MUD.java:291-296` (`ConnectionAcceptor.run`) creates the session with `CMClass.getCommon("DefaultSession")`, so the session class can be replaced through the class registry. It calls `initialize(sock, groupName)` (`MUD.java:310`) and then `CMSecurity.getConnectState` (`MUD.java:319`), which applies bans, an IP autoblock, and a per-address count (`CMSecurity.java:72-73` for `CONN_MAX_PER_ADDR = 6` and `CONN_LAST_DELAY_MS = 5 min`; logic at `2272-2330`).
- An accepted connection gets `handshake(intro)` (`MUD.java:393`), is added to the registry (`MUD.java:394`), and gets one `run()` on the acceptor thread (`MUD.java:398`). Later calls come from the ticker.
- `Sessions.tick()` (`Sessions.java:305-320`) runs every 100 ms (registered with a 100 ms period at `Sessions.java:299`). For each session that is not `isRunning()`, it calls `CMLib.threads().executeRunnable(S.getGroupName(), S)`. `ServiceEngine.getPoolExecutor(char)` (`ServiceEngine.java:142-155`) keeps one pool per thread-group letter, named `Worker<c>`, bounded by `MINWORKERTHREADS` (5) and `MAXWORKERTHREADS` (60) (`coffeemud.ini:2159`, `2166`).
- `DefaultSession.run()` (`3167-3293`) returns at once if `runThread` is already set. Otherwise it dispatches:
  1. If `inputCallback` is set, the callback path runs (`3186-3239`) and `mainLoop` is skipped.
  2. Otherwise by `status`: `MAINLOOP` calls `mainLoop()`; `LOGIN`, `ACCOUNT_MENU`, `CHARCREATE` and `LOGIN2` call `loginSystem()`; `LOGOUT` to `LOGOUTFINAL` call `preLogout()` then `logoutFinal()`. Handshake states do nothing here, because the handshake is a callback.
- `isRunning()` is true only after `run()` has set `runThread` (`3103-3105`). A session can therefore be queued again on a later tick before it starts. The duplicate `run()` returns at once on the `runThread` check (`3169-3175`). This is harmless, but the queue can carry duplicates.

The input loop is a poll, not a blocking read. `readlineContinue()` (`2708-2753`) returns `null` as soon as the socket buffer is empty, and returns one completed line per call. Pasted text therefore takes one ticker period per line. Its timeout is only checked while bytes keep arriving (`2729`).

### 2. Bytes to a line

The path is `rawin` (a `BufferedInputStream`) through `readByte()` (`2394-2412`), which throws `InterruptedIOException` when no bytes are available. `nonBlockingIn()` (`2491-2599`) catches that and returns -1. The rest of the pipeline:

- `readChar()` (`2414-2460`) first drains `fakeInput`, which holds text injected by `setFakeInput` and typed-ahead input replayed after the handshake (`DefaultSession.java:537-538`). Multi-byte charsets go through `SesInputStream` and an `InputStreamReader` (`initialize`, `609-612`). The default charset is ISO-8859-1 (`coffeemud.ini:96`, `100`).
- Each character is then classified in `nonBlockingIn`:
  - IAC (255) starts telnet negotiation in `handleIAC()` (`2167-2392`).
  - ESC (27) goes to `handleEscape()` (`2038-2165`). Only MXP secure lines (`ESC[<n>z` with n of 3 or less, `2069`) are parsed; other escape sequences are read and discarded (`2046-2063`).
  - NUL is dropped (`2510-2513`).
  - CR and LF end a line. CR/LF pairs are collapsed through `lastWasCR` and `lastWasLF` (`2517-2546`), and CRLF is echoed when the client has ECHO on.
  - Backspace (8) erases one character when LINEMODE is off, and echoes a backspace (`2555-2566`).
  - Anything else is appended to `input` and echoed when ECHO is on (`2577-2586`).
  - Return codes are `0` for a finished line, `1` for a consumed or ignored byte, and `-1` for no data. `hotkey()` calls with `appendInputFlag=false` to read single keys (`2462-2489`).
- `readlineContinue()` strips MCP framing (`sessionMCPCheck`, `2687-2705`), then runs `CMLib.coffeeFilter().simpleInFilter(inStr, doMxp, isArchon)` (`2741-2743`). `doMxp` is true only for characters with the MXPTAGS security flag, and `isArchon` for sysops.

`CoffeeFilter.simpleInFilter` (`CoffeeFilter.java:1975-2030`) normalises every typed line:

- `'` becomes `` ` `` (`1986-1987`), so typed apostrophes never reach the parser. `Emote.java:101` and `GTell.java:112` accept either character at the start of text.
- `%00`, `%FF`, `%1B` and `%07` get a backslash in front, except for archons (`1989-2000`).
- `^<`, `^>` and `^&` lose their caret unless MXP tags are permitted (`2006-2014`).
- A backspace removes the character before it (`2019-2029`).

### 3. A line to commands: `mainLoop()`

`mainLoop()` (`3610-3781`) runs once per `run()` while the status is `MAINLOOP` and no input callback is pending. In order:

1. `setInputLoopTime()` (`3615`). This is the watchdog's clock (see section 7).
2. If `suspendCommandLine` is set (during `hotkey` or `blockingIn`), return. Otherwise `readlineContinue(PINGTIMEOUT)` (`3618-3624`).
3. With no input, send a NUL keep-alive if nothing has been written for 30 s (`3627-3628`).
4. With input: update `lastKeystroke`, add the line to `prevMsgs` (`3633-3634`, not capped here, see Problems), and clear AFK unless an explicit AFK message is set (`3635-3636`).
5. Parse with `CMParms.parse` (`3637`; `CMParms.java:788`): space-separated, quoted strings kept whole, backslash escapes honoured.
6. If the first word is an alias (`pStats.getAlias`, `3644`), `CMLib.utensils().deAlias` expands it (`CoffeeUtensils.java:3844-3876`). Alias bodies are split on `{…}`; `@X` substitutes the typed arguments by position; a leading `noecho` suppresses the echo; without `@X` the typed arguments are appended to each alias command.
7. The mob's action pool is saved and zeroed (`3656-3657`). `metaFlags()` sets `SNOOPED` if the session is watched and `POSSESSED` if a soul mate is attached (`1131-1135`). `INORDER` is set when an alias produced several commands (`3659-3660`).
8. For each command: `addPreviousCmd` records it in history (`1137-1152`), the echo is printed if requested, `CMLib.lang().preCommandParser(parsedInput)` rewrites it (`DirtyLanguage.java:616`), and each result goes to `mob.enqueCommand(cmd, metaFlags, 0)` (`3670-3672`).
9. The saved pool is restored (`3675`). The typed line itself does not spend the pool; queued commands spend it later.
10. `while (mob.dequeCommand())` (`3686-3688`) executes whatever the budget and spacing allow.
11. Once a minute, idle policy runs (section 7).
12. A prompt is shown when `needPrompt` is set and no command was parsed on this pass (`3745-3761`). During combat the prompt is limited to one per tick.

### 4. Command pre-processing, lookup and queueing

**Pre-processor.** `preCommandParser()` (`DirtyLanguage.java:616`) joins the words with tabs and runs the `[command-pre-processor]` section of `resources/parser_en_TX.properties` (lines 100-179). The rule kinds are `replace` (regular expression), `replacewhole`, `replaceexact`, `replaceall` and `ignorewhole`. A `\n` in a replacement splits one line into two commands. Representative rules (paraphrased from the file):

```
define "{COMMANDS}" as "get|put|wear|wield|hold|open|close|eat|lock|unlock|pull|push|sniff|buy|sell|look"
replace  "^(?>ge|g)\t(.+)"                       with "get\t\1"      # g sword -> get sword
replace  "^(?>loo|lo|l)\t(.+)"                   with "look\t\1"     # l -> look
replace  "^place(\t.+)"                          with "put\1"
replace  "^put\t(.+)\tin\t(.+)"                  with "put\t\1\tfrom\t\2"
replace  "(^(?>{COMMANDS})\t)(.+)\tand\t(.+)"    with "\1\2\n\1\3"   # get sword and shield -> two commands
replace  "(^(?>{COMMANDS})\t)the\t(.+)"          with "\1\2"         # drop articles
```

**Lookup.** `EnglishParser.findCommand()` (`976-1110`) resolves the first word in this order:

1. A leading non-alphanumeric character is split off as its own word (`986-991`).
2. Exact command trigger, if `securityCheck` passes and the command is not disabled (`994-998`).
3. Exact skill (ability) trigger via `getSkillToInvoke` (`1000-1003`).
4. Exact social, exact channel name, exact journal name (`1008-1039`).
5. Inexact pass: ability triggers by prefix (`1041-1058`), then command triggers by prefix (`findCommandByTrigger(word, false)`, about `1060-1066`), then social, channel and journal prefixes.
6. Anything left falls through to `CMLib.leveler().deferCommandCheck` (not read). A mob with no location gets no lookup at all (`976-982`).

**Queue.** `StdMOB.enqueCommand()` (`2413-2448`):

- resolves the object with `findCommand`, and sends unknown words to `handleUnknownCommand`;
- computes the cost with `calculateActionCost()` (`2291-2312`): `Command.checkedActionsCost` (`Commands/StdCommand.java:242-262`), `Ability.checkedCastingCost`, `Social.checkedActionsCost`, or `1.0` for anything else;
- runs zero-cost commands at once unless `INORDER` is set. Other commands are added to `commandQue`, a `LinkedList` of `QMCommand` records (`StdMOB.java:176-184`);
- `checkCommandCancel()` (`2314-2331`) first removes the head of the queue if that command returns `canBeCancelled()` (`StdCommand.java:236`, default `false`). A new command therefore cancels a cancellable command still waiting.

**Costs.** Defaults come from `coffeemud.ini`: `DEFCMDTIME=0.20` out of combat and `DEFCOMCMDTIME=1.0` in combat (`coffeemud.ini:545-546`), parsed at `CMProps.java:3099-3100`. Per-command overrides are read by `CMProps.getCommandActionCost` (`CMProps.java:1143-1150`; combat version `1168-1175`).

**Execution.** `dequeCommand()` (`StdMOB.java:2142-2225`) runs the head command when both hold:

- `actions() >= cost`;
- at least `cost / phyStats().speed() × tick` has passed since the last command (`reqEllapse`), where the tick is `CMProps.getTickMillisD()`, 4000 ms by default (`CMProps.java:681`, `coffeemud.ini:2151`).

When a command runs, `setActions(diff)` deducts its cost, and the next queued command's cost is recalculated (`2180-2184`). `doCommand()` (`2250-2280`) calls `Command.execute`, `Social.invoke` or `CMLib.english().invokeSkill`. Exceptions are caught and logged, and the player sees "Oops-- you ran into a game bug! Please inform the administrators."

**Regeneration.** `StdMOB.tick()` (`4007`) adds `phyStats().speed()` to the pool each tick, half that while sitting (`4143-4144`). When the queue is empty the fractional remainder is discarded first (`4143`). In combat under the default combat system the mob gets one extra action per tick (`4208`).

Worked numbers, assuming speed 1.0 (the speed of a real mob was not checked): each tick grants 1 action. An out-of-combat command costs 0.20 action and needs 800 ms (0.20 × 4000 / 1.0) after the previous command, so a mob can issue about five ordinary commands per tick. In combat each command costs 1.0, so one command per tick plus the bonus action.

### 5. Output

- `print(msg)` (`1545-1549`) runs `applyFilters` (`1675-1698`). That applies the session's `SessionFilter` list, which game effects can register (`Abilities/Diseases/Disease_ColorBlindness.java:33` is one example), and then `CMLib.coffeeFilter().fullOutFilter` (`CoffeeFilter.java:1144`). `fullOutFilter` word-wraps to `S.getWrap()`, which is 78 when there is no session, and inserts anchor tags around URLs.
- `onlyPrint()` (`1418-1519`) is the common funnel:
  - it mirrors the text to snoops (`1425`, `snoopSupportPrint` at `1382-1410`);
  - it translates through `CMLib.lang().finalTranslation` (`1426`);
  - it collapses an exact repeat of the previous single line, showing the count as "(N)" (`1430-1451`);
  - on the session's own thread only, it pages every `pageBreak` lines with "<pause - enter>" and stops if the player types `q`, `qu`, `ex` or `x` (`1452-1488`);
  - it keeps the last `MAX_PREVMSGS` (100) output lines for `getLastMsgs()` (`1490-1513`);
  - it writes the text (`1514`).
- `rawCharsOut(PrintWriter, char[])` (`1308-1368`) writes under a fair `ReentrantLock` with a 10 s timeout (`writeLock`, `133`). A write error stops the session (`1339-1341`). A lock timeout kicks the session and interrupts the thread that holds the lock (`1351-1358`).
- `rawBytesOut()` (`1231-1300`) is the byte-level equivalent, used for telnet, MCCP, GMCP, MSDP and MPCP.
- `promptPrint()` (`1552-1582`) sets `lastWasPrompt`, appends `promptSuffix` (`CMProps.getPromptSuffix`, `CMProps.java:3284`), and sends IAC GA only if the mob has the `TELNET_GA` attribute or there is no mob. `mightSupportTelnetMode(TELNET_GA)` is always false (see Problems), so the attribute is the only path. `flagTextDisplayed()` (`1667-1673`) sets `needPrompt` unless the mob has `NOREPROMPT` and is not in combat.
- `showPrompt()` (`2894-2917`) builds the prompt from `playerStats().getPrompt()` with `CoffeeUtensils.buildPrompt` (`CoffeeUtensils.java:1941`). The default is `^N%E<^h%hhp ^m%mm ^v%vmv^N> ` (`coffeemud.ini:1155`). MXP clients also receive ENTITY definitions for hit points, mana, moves and experience (`2903-2914`).

The prompt language handled by `buildPrompt`, from its `switch`: `%h`/`%H` hit points and maximum, `%m`/`%M` mana, `%v`/`%V` movement, `%e`/`%E` current victim and that victim's health text, `%d` range to target, `%a`/`%A` alignment, `%c`/`%C` items carried and capacity, `%j`/`%J` followers, `%I` visibility state, `%B` newline, `%K`/`%k` the tank's stats, `%-NAME` a worn item's name or a stat by code name, `%< ATTR … %<` shown only if the mob has the attribute (`!` inverts it), `%/ … %/` hidden in combat, and `%\ … %\` shown only in combat. A `%` directly after `h`, `m` or `v` gives a percentage instead of a number.

### 6. Prompts and callbacks

Two styles coexist.

**Blocking prompts.** `prompt()`, `choose()`, `confirm()` and `blockingIn()` (`2612-2680`) run on the session thread, inside a command if need be. They poll `nonBlockingIn(true)` and sleep 100 ms when there is no data (`2646`). The configuration notes warn that such prompts hold a worker thread (`coffeemud.ini:2163-2165`). `choose()` (`2790-2828`) re-prompts until one of the choices is entered, and returns any trailing text through `paramsOut`.

**Callbacks.** `prompt(InputCallback)` (`1747-1759`) installs a `Session.InputCallback` (`Session.java:1202-1426`). Each line is passed to `setInput()`:

- `PROMPT`: a trailing `\` is stripped (`1331-1333`); an empty line becomes the default unless `noTrim` is set (`1335-1337`).
- `CONFIRM`: an answer starting with `T` counts as `Y` (`1344-1346`).
- `CHOOSE` and `CONFIRM`: the first character is upper-cased and must be one of the choices, otherwise `showPrompt()` runs and the callback keeps waiting (`1347-1368`).
- `WAIT`: stays waiting.

When the callback is no longer waiting, `callBack()` runs on a worker thread (`3207-3225`). `timedOut()` runs once `timeoutMs` has passed (`1307-1313`). `TickingCallback` (`Session.java:1435-1490`) is a prompt with no text that ticks every `tickerMs`.

**Handshake.** `HandshakeCallback` (`DefaultSession.java:421-554`) ticks every 250 ms through:

1. `HANDSHAKE_OPEN` to `HANDSHAKE_MCCP`.
2. `HANDSHAKE_MCCP`: waits until IAC traffic has been quiet for 500 ms, or 5 s have passed, or 10 s since the last state change (`456-459`). Then it may start MCCP2.
3. `HANDSHAKE_MXP`: sends image and support tags (`477-479`).
4. `HANDSHAKE_MXPPAUSE`: waits up to 3 s for MXP replies (`487`).
5. `HANDSHAKE_DONE`: sends the intro text, and for MXP clients with image support an intro picture (`494-531`).
6. `LOGIN`. Input typed during the handshake was collected by `TickingCallback.setInput` (`Session.java:1466`) and is replayed through `fakeInput` (`537-538`).

**Hotkeys.** `hotkey(maxWait)` (`2462-2489`) sets `suspendCommandLine`, reads single keys without buffering, echoes them, and ignores NUL, 1 and -1.

### 7. Login, logout and the watchdog

**Status machine.** `SessionStatus` (`Session.java:1550-1556`) runs `HANDSHAKE_OPEN`, `HANDSHAKE_MCCP`, `HANDSHAKE_MXP`, `HANDSHAKE_MXPPAUSE`, `HANDSHAKE_DONE`, `LOGIN`, then `ACCOUNT_MENU` / `CHARCREATE` / `LOGIN2`, then `MAINLOOP`, then `LOGOUT` through `LOGOUT11` and `LOGOUTFINAL`. `IDLE` is declared and has a `run()` case, but nothing in the code sets it.

**Login.** `loginSystem()` (`3393-3477`) creates a `CharCreationLibrary.LoginSession` through `CharCreation.createLoginSession` (`CharCreation.java:228-233`) and passes each line to it. The login session returns `INPUT_REQUIRED`, `NO_LOGIN` or `NORMAL_LOGIN`. A login session that has seen no input for 3 minutes (`HSIDLETIMEOUT`, `3403`) is disconnected (`3418-3422`). `setLoggedInState()` (`3337-3391`) moves to `LOGIN2`, writes a connection log line (address, terminal type, MXP, MSDP, GMCP, MCCP, ANSI level, character name), sends a `TYP_LOGIN` global message that can veto the login (`3375-3377`), and moves to `MAINLOOP`. `CMMap.sendGlobalMessage` (`CMMap.java:562`) runs an `okMessage` pass over the global handlers, any of which can refuse, and then an `executeMsg` pass. `autoLogin()` (`3295-3334`) handles MUD-to-MUD swaps. Setting the `MOBNAME` stat also jumps straight to `MAINLOOP` (`4507-4528`).

**Stopping.** `stopSession(disconnect, removeMOB, dropSession, killThread)` (`2831-2892`):

- `disconnect` sends an MPCP disconnect or a player-transfer ping and registers the player offline with intermud (`2834-2846`);
- `removeMOB` removes the mob from the game, unless the `LOGOUTMASK` check fails and the mob is not a sysop, in which case the character stays in the world, sleeping (`2854-2870`);
- `dropSession` runs `preLogout` and `logoutFinal`;
- `killThread` interrupts the session's run thread (`2877-2890`). The source's own comment calls this "a really really bad idea", because other sessions on the same thread die too (`2875-2876`).

`LOGOUTMASK` is a zapper mask (`MUDZapper.java:5678-5695`) with an optional leading tick count (`CMProps.java:2918-2933`). An empty mask passes everyone (`CMProps.java:2918-2933` gives an empty string; the mask check returns true for empty input), so the default removes the character at once. The mask decides whether a dropped link leaves the character in the world.

`preLogout()` (`2983-3066`):

- sends `MSG_QUIT` to the room of the mob and of each group member with `R.send` (`3015-3026`). `StdRoom.send` runs `executeMsg` directly and does not run the `okMessage` phase (`Locales/StdRoom.java:1734-1739`), so this notice cannot be vetoed;
- waits in one-second steps for up to 2 minutes if the player was in a PK fight recently (`3029-3032`);
- posts LOGOFFS channel notices and friend notices (unless the player has PRIVACY), and runs `LoginLogoutThread` to tell the other rooms (`3041-3051`).

`logoutFinal()` (`3479-3603`) posts ACCOUNTLOGOFFS notices when the whole account is offline (`3520-3526`). If the logout mask fails it sets the mob sleeping and keeps it in the world (`3538-3570`), optionally removing it after `LOGOUTMASKTICKS` ticks. It clears the history (`3493-3495`), runs `closeSocks()` (`2919-2969`: shut down input, flush a NUL, close), and removes the session from the registry in `finally` (`3600`).

**Idle policy** (`mainLoop`, `3691-3743`), checked once a minute:

- `IDLETIMERS` (`coffeemud.ini:1265`, default `90 85`): at 85 minutes idle the player gets a warning with the minutes left; at 90 minutes, "You are being logged out!" and the session stops (`3703-3711`).
- After 10 minutes idle the AFK flag is set (`3714-3720`), and role-play experience is awarded if `RP_GOAFK` is configured.
- After 3 hours of AFK, a sleeping character gets `Disease_Narcolepsy` and an awake one gets `Disease_Blahs` (`3724-3741`).
- Returning from AFK reports missed tells and gtells (`1174-1188`).

**Watchdog.** `Sessions.sessionCheck()` (`Sessions.java:180-288`) runs every 10 minutes (`Sessions.java:321-323`, `core/interfaces/MudHost.java:48`). It compares each session's `getInputLoopTime()` with a base threshold of 60 000 ms, multiplied by:

- 600 for sessions whose last command was IMPORT, EXPORT, CHARGEN or MERGE (`194-200`);
- 15 for staff in command rooms (`202-203`);
- 5 for sessions in LOGIN (`205-206`).

When the elapsed time exceeds the threshold times 10, the session is stopped and force-killed (`208-230`). Above the threshold alone, a suspect session is logged (`232-257`). Sessions with no mob, outside ACCOUNT_MENU and CHARCREATE, are stopped after 5 minutes (`260-285`). `stopSessionAtAllCosts` (`159-178`) escalates in steps: stop, sleep, run the session once more, stop with `killThread`, then remove it.

`setInputLoopTime()` is called at the top of `mainLoop`, in the callback path and during login (`3203`, `3416`). Nothing refreshes it while a blocking prompt inside a command waits, so the 10-minute default applies there. This follows from reading the code and was not run.

### 8. Telnet and protocol layer

- **Negotiation at connect.** `initialize()` (`583-664`) offers TERMTYPE (with NEWENVIRON), COMPRESS2 unless disabled, MXP, GMCP, MSP, MSDP, NAWS and LINEMODE, and turns server echo off (`643`). MCCP, MXP, MSP, GMCP and MSDP are each skipped when a `DisFlag` disables them (`631-640`). The `#$#mcp version: 2.1` line is sent unless MCP is disabled (`618-619`).
- **Option state.** `serverTelnetCodes` and `clientTelnetCodes` are 256-entry boolean arrays (`123-124`). ANSI, ANSI16 and ANSI256 share these arrays under option numbers 253, 252 and 254 (`Session.java:1099-1105`), which are also real telnet commands. See Problems.
- **Terminal.** The TERMTYPE response is matched against known client names (`1834-1926`). Each maps to an MTTS capability bitmask (`Session.java:1156-1178`: ANSI 1, VT100 2, UTF8 4, 256COLORS 8, MOUSE 16, OSCCOLOR 32, SCREENREAD 64, PROXY 128, TRUECOLOR 256, MNES 512, MSLP 1024, SSL 2048). NAWS sets width (minus 2) and height (`1934-1944`).
- **Compression.** MCCP2 wraps `rawout` in a JZlib `ZOutputStream` with `Z_SYNC_FLUSH` (`556-574`). `compress2Off()` undoes it (`666-677`).
- **MSDP, GMCP, MCP.** Subnegotiations go to `CMLib.protocol()` (`processMsdp`, `processGmcp`, `pingGmcp`; the bodies were not read). Lines beginning `#$#` go to `CMProtocols.mcp` (`CMProtocols.java:1300`) via `sessionMCPCheck`. MCP packages can push text into a session's input (`CMProtocols.java:3119`; `Libraries/mcppkgs/MCPMooSimpleEditPackage.java:59`).
- **MXP.** Client support is tracked in `mxpSupportSet` and version data in `mxpVersionInfo` (`handleEscape`, `2091-2140`).
- **MPCP.** A mud-to-mud session transfer protocol. Packets are a command word and a JSON body, with an HMAC-SHA1 digest keyed by `MPCPKEY` and a one-second timestamp window (`1966-2031`). `clientinfo` sets the client address and reconnect flag. Other commands go to `CMProtocols.processMPCPTransfer` (`CMProtocols.java:277`), and `sessioninfo` restores session stats (`354-412`). Outgoing digests escape 0xFF bytes (`388-395`). `coffeemud.ini:33` carries a placeholder key that should be replaced.

### 9. What a session stores

Field names are from `DefaultSession.java` unless noted.

- **Ownership:** `mob` (the player), `acct` (the account), `loginSession`, `groupName` and `threadGroupChar`, `ipAddress`, `isReconnect`, `foreignDeparted`, `snoops` (sessions watching this one), `snoopSuspensionStack`, `textFilters` (`SessionFilter` list).
- **Socket and streams:** `sock`, `rawin`, `rawout`, `in` (BufferedReader), `out` (PrintWriter), `charWriter`, `inMaxBytesPerChar`, `writeLock`, `writeStartTime`, `writeThread`, `lastWriteTime`, `lastReadTime`, `lastIACIn`.
- **Input:** `input` (the line being assembled), `fakeInput`, `inputCallback`, `history` (a list of parsed commands, capped at 100 and skipping lines that start with `!`, `1137-1152`), `lastWasCR`, `lastWasLF`, `suspendCommandLine`, `waiting`, `lastKeystroke`.
- **Output:** `needPrompt`, `lastWasPrompt`, `promptSuffix`, `promptLastShown`, `lastStr` with `spamStack` (duplicate collapsing), `curPrevMsg` and `prevMsgs` (the last 100 output lines, which also receive typed input, `3634`), `currentColor`, `lastColor`, `markedColors` (a stack), `clookup` (colour table), `terminalWidth`, `terminalHeight`.
- **Protocol state:** `serverTelnetCodes`, `clientTelnetCodes`, `telnetSupportSet`, `terminalType`, `mttsBitmap`, `lastTTypeR`, `mxpSupportSet`, `mxpVersionInfo`, `msdpReportables`, `gmcpSupports`, `gmcpPings`, `mcpKey`, `mcpKeyPairs`, `mcpSupported`, `strCache` (a free-form key/value store reachable through `getStat`/`setStat`).
- **Clocks and lifecycle:** `status` with `lastStateChangeMs`, `lastLoopTop` (the watchdog clock), `activeMillis`, `onlineTime`, `userLoginTime`, `lastStart`, `lastStop`, `milliTotal` (command-parsing time per session), `lastPKFight`, `lastNPCFight`, `afkTime`, `afkMessage`, `lastBlahCheck`, `killFlag`, `runThread`, `loggingOutObj`, `connectionComplete`.

Persistence goes through the generic stat interface. `DefaultSession` exposes 34 stat codes (`SESS_STAT_CODES`, `DefaultSession.java:4120-4129`), among them TELNETSCODES, TERMTYPE, PREVCMD, LASTMSG, MSDPREPORTS, MCPKEYPAIRS, LOGINTIME and ACTIVETIME. The MPCP session swap writes them out as JSON and restores them with `setStat` (`CMProtocols.java:499-515`). The MOB's command queue is not part of this state.

`FakeSession` holds much less: an output file named by the `groupName` argument (`FakeSession.java:126-129`), a memory buffer used when the group is `MEMORY` (`126-127`), a `mob`, an input queue `inputV`, a set of client telnet modes, and two strip flags for snoop text (`800-813`). `getStatus()` always returns `MAINLOOP` and `isRunning()` always returns false (`657-661`, `182-186`).

### 10. The registry (`Libraries/Sessions.java`)

- Each thread-group letter has its own `Sessions` library. `CMLib.library(char, SESSIONS)` gives the one that owns the sessions in that group (`DefaultSession.java:4499-4503`). `moveSessionToCorrectThreadGroup` (`Sessions.java:390-412`) moves a session to a private group named `<THEME>PLAYERS` for its area theme.
- `localOnline()` (`Sessions.java:43-55, 70-79`) selects sessions in MAINLOOP that are not stopped, with an active mob that is in the game.
- `findCharacterSessionOnline` (`361-387`) looks players up by exact name first, then by partial name.
- `add`, `remove` and `isSession` synchronise on the library (`139-156`).

## Content worth mining

| Item | Where | Notes for a Python SUD |
|---|---|---|
| Shortcut and rewrite rules | `resources/parser_en_TX.properties:100-179` | A short table of regex and whole-word rewrites applied in order. Python's `re` can run them directly. |
| Multi-command splitting | same file ("get X and Y", "put X in Y") | One typed line becomes several commands without a grammar. |
| Prompt codes | `Libraries/CoffeeUtensils.java:1941` | Single-letter codes for hit points, mana and movement, attribute conditionals, combat-only blocks. |
| Default prompt | `coffeemud.ini:1155` | A `hp mana moves` status line with colour codes; a good default for a status bar. |
| AFK and missed-message lines | `DefaultSession.java:1168`, `1182-1187` | "You are now listed as AFK." and "You missed: N tell(s)…" are short and reusable. |
| Idle consequences | `DefaultSession.java:3724-3741` | After hours of AFK a sleeping character gets a narcolepsy effect, an awake one a "blahs" effect. Time-based, not punitive. |
| Command failure line | `StdMOB.java` `doCommand` (`2250-2280`) | "Oops-- you ran into a game bug!" is a clean catch-all for command exceptions. |
| Pager | `DefaultSession.java:1470` | "<pause - enter>" with the quit words `q`, `qu`, `ex`, `x`. |
| Config knobs | `coffeemud.ini` 545-546, 812, 1265, 2151, 2159-2166 | Action-cost defaults, logout mask, idle timers, tick length, thread counts. |

## Notable design ideas

1. **Pacing by action economy.** Every command has a cost. The mob earns actions each tick at a rate set by its speed, and combat uses a separate, higher cost table plus a bonus action. Commands queue behind that budget, so no command needs a timer of its own. The same model can run in real time or in turns (see Takeaways).
2. **Poll, do not block.** A 100 ms ticker drives each session rather than a blocking read, so one pool can serve every connection. Blocking prompts remain available, with a documented thread cost.
3. **Pending input as an object.** `InputCallback` records the prompt type, default, choices, timeout and continuation. Validation and re-prompting live in `setInput`, not in each command.
4. **Two-phase global messages with veto.** Login asks the global handlers whether it may proceed (`CMMap.java:562`). Quit notices skip the veto phase by design.
5. **Text filters as game effects.** A `SessionFilter` can rewrite everything a player sees (`Disease_ColorBlindness.java:33`). A filter that returns null removes itself (`DefaultSession.java:1685-1690`).
6. **Snoop as a mirror.** Watchers receive each output line with a name prefix (`1382-1410`), and a suspension counter stops snooping while it is above zero (`1386`).
7. **Linkless logout.** A dropped connection can leave the character in the world, sleeping, while the mask check fails (`2854-2870`, `3538-3570`). The state lives in the world, not in the session.
8. **Sessions as data.** The 34 stat codes make a session serialisable, and MPCP uses them to hand a player to another host (`CMProtocols.java:499-515`).
9. **Aliases with variables.** `@X` placeholders and a `noecho` flag (`CoffeeUtensils.java:3844-3876`) give players scripting without any new command.

## Problems and cruft

All found by reading. Nothing was executed.

1. **Probable write-lock leak.** In `rawCharsOut(PrintWriter, char[])`, the socket check returns (`1317-1318`) inside the `tryLock` branch, before the `try/finally` that unlocks (`1319-1349`). A closed socket therefore leaves `writeLock` held. Later writes wait 10 s and then kick the session (`1351-1358`). `rawBytesOut()` (`1235-1245`) keeps the same check inside its `try`, so it is safe.
2. **MXP secure lines assume the whole line is buffered.** `handleEscape()` reads the line with `readByte()` (`2073-2078`). `readByte()` throws as soon as the buffer is empty (`2411`), which unwinds the parse. The bytes read so far are lost, and the rest of the line arrives as ordinary input. A line split across TCP packets is mangled. This is inferred from the code.
3. **Java deserialisation of a session stat.** `setStat(MSDPREPORTS)` base64-decodes its value and passes it to `ObjectInputStream.readObject()` (`DefaultSession.java:4362-4383`). `restoreMPCPSessionStats` feeds every stat code from an MPCP `sessioninfo` packet into `setStat` (`CMProtocols.java:499-515`). Packets are authenticated only by the shared `MPCPKEY` HMAC (`DefaultSession.java:1966-2031`). The exposure therefore depends on that key staying secret, and the shipped value is a placeholder (`coffeemud.ini:33`). Reachability beyond this path was not traced.
4. **In-band shutdown with a password.** An MXP `SHUTDOWN` tag on a secure line names a sysop and a password and runs the Shutdown command if both match (`DefaultSession.java:2137-2162`). The credentials travel in the telnet stream, and no separate flag gates the path.
5. **`readlineContinue` does not block.** It returns null when no bytes are buffered (`2725-2729`), but its Javadoc says it "blocks for a short amount of time" (`Session.java:674-676`). Its timeout is treated as an absolute time whenever the value exceeds the current clock (`2713`). `initialize()` calls `readlineContinue(10000)` with the comment "must handle big mpcp and similar requests" (`615`), which does not wait.
6. **Documentation contradicts the code on paging.** `Session.java:114-121` says the one-argument `onlyPrint` has "No page breaking". The code pages on the session thread (`DefaultSession.java:1452-1488`).
7. **Documentation contradicts the code on `getLastMsgs()`.** `Session.java:897-903` describes the messages "received by this user". The list also holds typed input (`3634`). It is capped only when output is added (`1502-1506`), not when input is added.
8. **Race on the callback.** `run()` hands `callBack.callBack()` to a worker (`3210-3225`), then on the session thread sets `inputCallback = null` if the old callback has finished (`3237-3238`). If the worker installs a new callback first, the session thread overwrites it. The window is small and no lock covers it.
9. **Odd locking and visibility.** `setStatus` synchronises on `status` itself (`233-243`), which is an enum constant shared by every session, so the lock protects nothing per session. `status`, `killFlag`, `waiting` and `needPrompt` are not `volatile` (`92`, `105`, `106`, `112`), although the watchdog reads `status` from another thread (`Sessions.java:185-230`).
10. **Apostrophes are rewritten for everyone.** All typed apostrophes become backticks (`CoffeeFilter.java:1986-1987`), including in names and free text. Emote and GTell accept both characters, so the commands still work (`Emote.java:101`, `GTell.java:112`).
11. **Telnet codes are overloaded.** ANSI modes are stored under option numbers 252-254 (`Session.java:1099-1105`), which are also WONT, DO and DONT. Option state and display state share the same arrays (`DefaultSession.java:123-124`).
12. **Dead or unused members.** `HNDSHAKETIMEOUT` (`DefaultSession.java:69`) is declared and never used. `tickTotal` is never incremented, so `getTotalTicks()` returns 0 unless it is set through `setStat` (`153`, `983`, `4319`). The `IDLE` status is never set (`Session.java:1553`).
13. **A typo in a persisted key.** `ACCTOUNTNAME` (`DefaultSession.java:4121`, `4251`, `4537`) is used as a saved stat code. Correcting the spelling would break saved and transferred data.
14. **Unbounded input history.** `prevMsgs.add(input)` (`3634`) is neither synchronised nor capped. The cap is applied only when output is added (`1502-1506`).
15. **DEL is not erase.** Only byte 8 is treated as erase (`2555`). Byte 127 falls through to the default branch and is kept as a character (`2567-2586`). Not checked against real clients.
16. **Logout can stall a worker.** `preLogout()` sleeps in one-second steps for up to 2 minutes after a PK fight, on the worker thread (`3029-3032`).
17. **`killThread` is unsafe.** `stopSession(…, killThread=true)` interrupts a pool thread that may be running other sessions (`2877-2890`). The code's own comment says so (`2875-2876`).
18. **A latent false "online" entry.** `localOnlineFilter` counts any session whose status is MAINLOOP and whose mob is in the game (`Sessions.java:48-51`). `FakeSession.getStatus()` always returns MAINLOOP. Nothing registers a FakeSession today, but one would appear as an online player.

## Takeaways for a Python SUD

**Borrow**

1. **Action budget as the time model.** Put each command's cost in a data file. Each tick adds `speed` to the actions pool. A command runs when the pool covers its cost and enough time has passed since the last one. This paces real-time play with no per-command timers. Turn-based play is the same model with one tick per player turn, so the real-time versus turn-based choice can wait. A minimal sketch (spacing omitted):

   ```python
   from collections import deque
   from dataclasses import dataclass, field
   from typing import Callable

   @dataclass
   class Command:
       name: str
       cost: float                 # from a data file, e.g. {"look": 0.0, "get": 0.2}
       run: Callable[[], None]

   @dataclass
   class Player:
       speed: float = 1.0
       actions: float = 0.0
       queue: deque = field(default_factory=deque)

   def tick(p: Player) -> None:
       p.actions += p.speed

   def drain(p: Player) -> None:
       while p.queue and p.actions >= p.queue[0].cost:
           cmd = p.queue.popleft()
           p.actions -= cmd.cost
           cmd.run()
   ```

2. **Pending input as an object.** A small record holding the prompt kind (`prompt`, `choose`, `confirm`), the text, the default, the valid choices and a continuation. Validation and re-prompting then live in one function, not in every command.

3. **Rewrite rules as data.** A text file of `replace` and `replacewhole` lines, applied in order to the words of a typed line, with a separator that splits one line into several commands. Port the get/put, article and shortcut rules from `parser_en_TX.properties` directly.

4. **One output funnel.** Wrap, collapse repeated lines to "(N)", page long output with a pause, re-show the prompt when idle, and keep the last 100 lines for scrollback. A prompt template such as `"{hp}/{max_hp} hp  {mana}/{max_mana} mana"` with `str.format_map` covers most of the prompt language.

5. **Observer hook.** A list of listeners that receive every output line. This serves debugging and session recording at once.

**Simplify**

- Replace the 100 ms poll and thread pool with one loop. A terminal game can use `input()` directly; a GUI or async design can use an event loop, with `await` for prompts. This removes the cost that CoffeeMud documents for blocking prompts.
- Drop the telnet, MXP, MSDP, GMCP, MCCP, MCP and MPCP layers. A single-player terminal needs none of them.
- Drop the watchdog, the per-group registry, the thread groups and the linkless logout. Keep "save on quit" instead.
- Keep one input normaliser (strip control characters, collapse whitespace, optionally normalise quotes) and one place that rejects bad input.
- Keep the pre-processor only if the verb set grows. A dict of aliases may be enough at first.

**Drop**

- Connection throttling, IP bans and autoblocks.
- Session transfer and stat-based serialisation. Save the player object instead of the session.
- Reflective class lookup for sessions, which Python does not need.

**Multiplayer assumptions to rethink**

- The logout mask and sleeping characters exist because a dropped link leaves a character in the world. A single-player game has no link to drop, so remove them.
- Missed-tell reports and channel and friend notices have no single-player equivalent. A "while you were away" summary could replace the AFK report.
- The idle timers exist to free seats on a shared server. In a single-player game, idle time can be a game clock in which the world keeps moving. The narcolepsy and blahs effects show the sort of consequence that could attach to idle time.
- The global login and quit messages (`CMMap.sendGlobalMessage`) become one event bus. The veto pattern is worth keeping only if mods are expected.

## Coverage

- **Read in full:** `Common/DefaultSession.java` (all 4629 lines, in 800-line chunks), `Common/interfaces/Session.java` (all 1557), `Common/FakeSession.java` (all 856), `Libraries/Sessions.java` (all 414), `Libraries/interfaces/SessionsList.java` (all 226).
- **Read in part, to follow the interactions:** `MOBS/StdMOB.java` (176-184, 1131-1152, 2103-2460, 4007-4010, 4120-4150, 4200-4212, 4270-4292), `Commands/StdCommand.java` (224-262), `Libraries/EnglishParser.java` (976-1110), `Libraries/CoffeeFilter.java` (1144-1160, 1969-2030), `Libraries/CoffeeUtensils.java` (1941-1990, 3844-3876), `Libraries/DirtyLanguage.java` (616-660), `resources/parser_en_TX.properties` (1-30, 86-180), `Libraries/CMProtocols.java` (277-412, 480-515, 1300-1302, 3119), `Libraries/CharCreation.java` (228-233), `Libraries/CMMap.java` (562-600), `Locales/StdRoom.java` (1688-1740), `core/threads/ServiceEngine.java` (142-160, 259-290), `application/MUD.java` (245-400), `core/CMSecurity.java` (72-73, 2268-2330), `core/CMProps.java` (681, 1143-1186, 2918-2933, 3099-3100, 3284-3287), `core/CMParms.java` (788-830), `Libraries/MUDZapper.java` (5678-5695), `Abilities/Archon/Archon_LLMIFY.java` (340-375), `Commands/Emote.java` (101), `Commands/GTell.java` (112), `Commands/Shell.java` (288), `Abilities/Diseases/Disease_ColorBlindness.java` (33), `Libraries/mcppkgs/MCPMooSimpleEditPackage.java` (59), `core/interfaces/MudHost.java` (48), `coffeemud.ini` (lines 33, 96, 100, 545-546, 812, 1155, 1265, 2151, 2159-2166).
- **Skimmed or not read:** the bodies of `CMProtocols.processMsdp`, `processGmcp`, `pingGmcp` and `mcp`; the login menus in `CharCreation`; `ThreadEngine` internals (`CMThreadPoolExecutor`); `SLinkedList`; `deferCommandCheck`.
- **Checked by grep:** constant use (`HNDSHAKETIMEOUT`, `tickTotal`, `SessionStatus.IDLE`), registration sites for sessions (`CMLib.sessions().add` at `MUD.java:394` and in `Archon_LLMIFY`), callers of `setFakeInput`, and the `TELNET_GA` check.
- **Not run:** no claim here comes from running the server. The MXP line-loss, the write-lock leak and the callback race are inferred from reading and should be confirmed by a test before anyone relies on them.
