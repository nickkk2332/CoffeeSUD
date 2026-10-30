# Triggers (rituals), the JavaScript engine and the intermud client

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b038` (Triggers, JavaScript engine and intermud client) · 6 files · 6032 lines of Java.

Citation convention: `path:line` where the path is relative to the CoffeeMUD folder. `Common/`, `Libraries/`, `MOBS/`, `Abilities/`, `Behaviors/` and `core/` are short for `com/planet_ink/coffee_mud/<dir>/`. Nothing was executed. Items marked "by reading" are inferred from the code and were not run.

## Summary

Three unrelated subsystems share this bucket. The largest, `DefaultTriggerer`, implements "rituals": ordered trigger steps such as saying a phrase, performing a social, putting a named item in a container, waiting a number of ticks or standing in a room at a given hour, all written as one short string. Rituals drive deity blessings, curses, services and powers (`MOBS/StdDeity.java`), ability prerequisites that NPCs must perform (`Libraries/CMAbleComps.java`), and TRIGGER-based timed effects (`Abilities/Misc/TriggeredAffects.java`). `JavaScriptingEngine` is a second, Rhino-based script engine with a sandbox and a bridge to the MOBPROG engine, but nothing in the repository selects it, so the live scripting path is `DefaultScriptingEngine` (bucket b037). `IntermudClient` is the facade over five inter-MUD and chat links (CM1, IMC2, I3, Grapevine, Discord) that routes tells, channel traffic, who and finger requests to whichever link is online. For a single-player text game the ritual grammar and its "expect a step, then wait" matcher are worth borrowing; the intermud layer is multiplayer plumbing to drop.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Common/DefaultTriggerer.java` | Ritual parser, step matcher, NPC step generator, per-mob tracker, wait queue | 1944 |
| `Common/JavaScriptingEngine.java` | Rhino `ScriptingEngine` with `$`-variable expansion, class sandbox, MOBPROG bridge. Dormant (see section 10) | 1473 |
| `Libraries/IntermudClient.java` | `IntermudInterface` implementation: starts and stops link clients, routes player commands | 1724 |
| `Common/interfaces/Triggerer.java` | Interface, the 27-value `TriggerCode` enum (lines 57-92), global `TrigSignal.sig` (340-343) | 344 |
| `Libraries/interfaces/IntermudInterface.java` | Interface, `InterProto` (CM1, IMC2, I3, Grapevine, DISCORD; 49-56), `InterQuery`, `RemoteIMud` | 341 |
| `Common/NonTriggerer.java` | Null object. `isDisabled()` is true and every query returns empty | 206 |

Related code outside the bucket that the chapter relies on (read in part only):

| File (lines read) | Why it matters here |
|---|---|
| `Libraries/CMAbleComps.java` (250-275, 530-565, 856-896, 1392-1611) | Ability components with TRIGGER requirements: per-mob triggerer setup, NPC action, tick and message hooks |
| `MOBS/StdDeity.java` (55-180, 701-846, 1242) | Six ritual slots per deity; `HolyEvent` hooks; default ritual strings |
| `Abilities/Misc/TriggeredAffects.java` (418-522, 594-671) | Ability that adds or removes effects when a trigger fires |
| `MOBS/StdMOB.java` (73, 900-906, 3699-3700, 4334-4335) | Holds the triggerer. Ritual checks run inside `executeMsg` (3412+) and `tick` (4007+) |
| `Abilities/StdAbility.java` (2092-2110) | An NPC that lacks the required components starts its ritual |
| `Libraries/CMGenEditor.java` (5898-5909) | Builder-typed ritual text is validated by parsing it |
| `Common/DefaultScriptingEngine.java` (9262-9300), `core/CMSecurity.java` (1008-1085) | The engine actually used for `<SCRIPT>` blocks, and the JavaScript approval gate |
| `Libraries/intermud/` (named, not read): `i3/CoffeeMudI3Bridge.java`, `i3/I3Client.java`, `imc2/IMC2Driver.java`, `GrapevineClient.java`, `DiscordClient.java`, `cm1/CM1Server.java` | The protocol clients behind `IntermudClient` |

## How it works

### 1. The ritual text

The entry point is `addTrigger(key, text, socials, errors)` (`Common/DefaultTriggerer.java:244-700`). Its behaviour:

- The text is upper-cased and trimmed (246). A text of `-` means "no trigger" and stores an empty step array (252-258).
- Connectors: `&` joins steps with AND and `|` joins them with OR. AND steps go into `putHere`. OR alternatives hang off the previous step through `orConnect` (262-268, 653-661). So the text `A|B&C` means (A or B), then C.
- `NAME+` sets `addArgs`, so the words matched by that step are kept in the completion's argument list (299-303, 1342-1351).
- `NAME#n` sets `reqCount` (305-310). Nothing reads `reqCount`; the only references are the declaration (120) and the assignment (308).
- An `ASSISTn` prefix sets `other` (290-298). `-1` means only the host may perform the step (1330-1331). `0` means any assister. `n>0` means the n-th assister (1332; description text 770-777).
- Command names must match an enum name exactly, or else the first enum name that starts with the typed text is used (313-323). So `SA` resolves to SAY and `ST` to STANDING.
- Parse problems are appended to an `errors` list, for example "Illegal trigger", "Unknown material", "Illegal SPELL in" and "Illegal social in" (326-327, 435, 615). The builder editor depends on this list (CMGenEditor.java:5901-5909).
- Starter reordering (673-696): the first step whose message code is positive (a real player action, not TIME, WAIT and similar) is moved to position 0. Text written as `TIME 12&SAY X` is therefore stored as SAY X followed by TIME 12. Stored order is what runs. The code has no comment explaining this; the intent is unverified.
- Parsed arrays are cached in a static map `ritualCache` keyed by the upper-cased text (38, 247-249, 698). Nothing else reads or clears it.

### 2. Step kinds

Each row gives the player-side match (`stepGetCompleted`, 1305-1745) and the NPC-side generator (`genNextAbleTrigger`, 943-1154). "auto" means the triggerer completes the step itself. Msg codes are `CMMsg` constants (`Common/interfaces/CMMsg.java`), read through `getCMMsgCode` (1156-1203).

| Code | Argument | Player-side match | NPC generator |
|---|---|---|---|
| SAY | text | Source message contains the text (upper-case substring; code TYP_SPEAK = 27). 1338-1354 | Says "^T<S-NAME> say(s) '@x1'.^N" (988-989) |
| YOUSAY | text | Completes on the triggering message. Source is made to say the text through `postSay`, with the ignore flag set so the say does not re-trigger (1364-1379) | None (996-997) |
| OTHERSAY | text | Every other occupant of the room says the text (1410-1439) | None (1000-1001) |
| ALLSAY | text | Every occupant, including the source, says the text (1380-1409) | None (998-999) |
| TIME | hour of day | Any message from a source whose area's hour equals the argument (1355-1359) | Auto (990-992) |
| WAIT | ticks | Sets a wait and completes once ticks x tick-ms have passed (1440-1458) | Returns a MSG_OK_ACTION once due (1002-1008) |
| RANDOM | percent | On any matching message, completes when `rollPercentage() <= n` (1360-1363) | Auto (993-995) |
| CHECK | zapper mask | `maskCheck(mask, source, true)` (1459-1468) | Auto (1009-1011) |
| INCLUDE | n, mask | At least n other occupants passing the mask join as assistants (1475-1508) | Auto (1012-1014) |
| TIMEOUT | seconds | Deadline. Only the NPC path sets it; see section 6 (1469-1474, 1015-1018) | Auto |
| INROOM | room id, or holy / unholy / balance | Source is in the room, or the room's cleric infusion matches the triggerer name (1554-1590) | Auto (1095-1097) |
| RIDING | name | Source rides a mount whose name contains the text (1591-1603) | Auto (1098-1100) |
| SITTING, STANDING, SLEEPING | none | Source is in that posture (1693-1701) | Acts if not already in it (1140-1151) |
| PUTTHING | item, container | Target is a Container; tool is an Item whose name matches the first argument; target name matches the second (1509-1521) | Puts a generated GenItem into a GenContainer (1019-1032) |
| PUTVALUE | gold, container | Tool's base gold value at least n; target is a Container matching the name (1635-1649) | Generated item worth n (1110-1121) |
| PUTMATERIAL | material, container | Tool material matches the resource or material mask (1650-1665) | Generated resource item (1122-1132) |
| BURNTHING | item | Target name match (shared branch 1522-1538) | Generated item burnt (1033-1041) |
| BURNMATERIAL | material | Target material matches (1666-1679) | Shares 1122-1132 |
| BURNVALUE | gold | Target base gold value at least n (1680-1692) | Generated item (1133-1139) |
| READING | item | Target name match (shared branch 1522-1538) | Generated item read (1042-1050) |
| EAT, DRINK | item | Target name or material match (shared branch 1522-1538) | Generated item eaten or drunk (1077-1094) |
| CAST | ability | Tool ID or name matches (1604-1617) | Casts the ability (1101-1107) |
| EMOTE | text | Source message contains the text (1618-1634) | Emote message (1108-1109) |
| SOCIAL | social, then target or `*` | Tool is a Social with the same base name; `*` accepts any target (1539-1553) | Social message. With `*`, the target-specific variant is used when the NPC has a victim (1051-1076) |

### 3. Data model

- `TrigTracker`, one per mob (126-170). `states` is a `LimitedTreeMap` of `TrigState` with 120000 ms idle expiry and 100 entries (128). `compl` is a `LimitedTreeSet` of recently completed keys with `TICKTIME*2` ms idle expiry (8000 ms at the default tick) and 10 entries (129). The mob is held by weak reference (134).
- Trackers are looked up by mob name, not identity (706-726; see Problems).
- `TrigState`, one per (mob, ritual key) (172-233): `completed` is the index of the last completed step, starting at -1. `time` is the millisecond stamp of the last completion. Also `waitExpire`, `timeout`, `args` (the captured words), and `assistants`.
- `DefaultTriggerer` fields (38-47): `rituals` maps key to `Trigger[]`. `assisting` maps key to the assisting mob. `waitingFor` is the list of states that are waiting. `ignoreOf` is a set of names (`LimitedTreeSet`, default 60000 ms idle and 100 entries; `core/collections/LimitedTreeSet.java:118`). `holyName` (default "Unknown", 45) is used by the holy, unholy and balance room checks.
- `isDisabled()` is true when there are no rituals (96-98). `CMAbleComps` checks this to decide whether a mob needs a real triggerer.
- `Trigger` (112-124) holds one step: `triggerCode`, `parm1`, `parm2`, `soc`, `socT`, `cmmsgCode`, `reqCount`, `orConnect`, `addArgs`, `other`.

### 4. Control flow A: a player's message completes a step

1. `whichTracking(msg)` (1239-1277) returns nothing if the source is ignored (1242) or the message is ENTER, LEAVE or LOOK (1244-1252). Otherwise it calls `isTracking(key, msg)` (1215-1236) for each ritual key. That method peeks at the next step: a candidate if the step has no message code (below 0), or its code equals `msg.sourceMinor()`, or it is a SOCIAL and the tool is a Social. Assisted keys are added too (1264-1273).
2. `getCompleted(hostM, keys, msg)` (1774-1810) calls `stepGetCompleted` for each key. The step is skipped unless the assister rule holds (1330-1333) and the source is in the same room as the host (1333).
3. A matched step calls `setCompleted` (198-202: `completed++`, `time=now`), and the room receives a progress message (1718-1721). When the last step completes, the key goes into `compl` (1725-1727), the state is cleared (1728), and `Triad(hostM, key, args)` is returned (1723-1730, 1782).
4. If the host does not complete the key but an assister holds it, the assister's triggerer receives the message (1783-1807).
5. A partial state with no progress is cleared (1741-1743).

Callers: `CMAbleComps.handleAbilityComponentTriggers` (CMAbleComps.java:1573-1611), called from `StdMOB.executeMsg` (StdMOB.java:3699-3700), and `TriggeredAffects.isTriggered` (TriggeredAffects.java:608-642). Matching runs after the action is executed, so a ritual cannot veto a message. When an ability's ritual completes, the ability is queued to run as a trailer (1597-1607), and the action cost is deducted (1596).

### 5. Control flow B: an NPC performs its own ritual

`genNextAbleTrigger(hostM, mob, key, force)` (943-1154) returns a `CMMsg` for the next step, or null. It first checks the assister map (947-962), then the step index (963-985). The `force` flag selects whether a new state is created if none exists (970-973).

- Callers: `CMAbleComps.startAbilityComponentTrigger` (1495-1519), which runs when `StdAbility` finds missing components on an NPC (StdAbility.java:2102-2105). `CMAbleComps.tickAbilityComponentTriggers` (1522-1570) runs from `StdMOB.tick` (StdMOB.java:4334-4335) for NPCs only (1547). `StdDeity` runs the SERVICE ritual from the `HolyEvent.SERVICE` hook (StdDeity.java:753-759).
- Each tick, at most one action is sent. The loop returns after the first successful send (1512-1513, 1562-1563).
- Generated actions are synthetic. PUTTHING, PUTVALUE, PUTMATERIAL and the burn and eat steps create a fresh `GenItem` or `GenContainer` (`CMClass.getBasicItem`) with the requested name or value (1019-1032, 1110-1132). The NPC's inventory is not consulted. The ritual therefore needs no physical item to exist.

### 6. Control flow C: waiting, timeouts and expiry

- WAIT on the player path records `waitExpire` and adds the state to `waitingFor` (1444-1454, `setWait` 219-232). `whosDoneWaiting` (1851-1880) returns the mobs whose wait has ended and removes them from the queue.
- Two callers poll that queue. `tickAbilityComponentTriggers` (CMAbleComps.java:1532-1546) sends each waiting mob an empty MSG_OK_VISUAL through `executeMsg`, which re-runs the wait check. `StdDeity` polls the queue at StdDeity.java:1242; what it does with the result was not read.
- WAIT length is `n x TICKTIME` ms (1004, 1444). The default tick is 4000 ms (`core/CMProps.java:681`; any value under 500 falls back to 4000 at 2720-2722). `WAIT 10` is therefore 40 seconds at default ticks. The description shows the same as seconds (852).
- TIMEOUT: the NPC path sets `timeout = n x 1000` ms (1016). `getState` treats a state older than its timeout as absent (151-153). On the player path (1469-1474), the same step writes its value into `state.time`, and `setCompleted` (called at 1722) overwrites that field at once. A TIMEOUT step reached by a player's message therefore sets no deadline. This is by reading.
- Expiry: states idle out after 120 s (128). The recently-completed set keeps a key for 2 ticks, which is 8 s at default ticks (129). `wasCompletedRecently` removes the key when it is read (1837-1848). `Triggerer.java:296-297` documents this window as "typically within 4 seconds".
- `setIgnoreTracking` (925-934) puts a name in `ignoreOf`, which stops that mob's messages from advancing any ritual. `StdDeity` sets and clears it at several points (StdDeity.java:484-606). The purpose at each point was not read in detail.

### 7. Assisters and INCLUDE

- `INCLUDE n mask` (1475-1508) scans the room for occupants other than the source who pass the mask. It requires n of them. Each one joins through `CMLib.ableComponents().addAssistingTriggerer(M, hostM, key)` (1499-1502). That call gives the assister a `DefaultTriggerer` if it has none (CMAbleComps.java:1441-1450), and registers the host's key there with `addTriggerAssist` (DefaultTriggerer.java:1895-1909).
- An assister's later messages then advance the host's ritual through the delegation in section 4 (1783-1807). A step written `ASSISTn` must be done by the n-th assister (1332).
- The host's state holds the assister list (`assistants`, 1496-1503). Only the first assister's name is recorded as the argument (1494-1495).

### 8. Output shown to players

- NPC action messages, listed in section 2, for example "<S-NAME> put(s) <O-NAME> into <T-NAME>." (1031) and "<S-NAME> sleep(s)." (1150).
- Ritual progress. Each completed step sends `R.show(...)` with code `MSG_RITUAL` (1718-1721). The text is built by concatenation (see Problems).
- Output from YOUSAY, OTHERSAY and ALLSAY, which are `postSay` calls (1373, 1399, 1429).
- There is no failure text. Nothing in this file reports a ritual that did not complete.
- `getTriggerDesc(key, player)` (759-922) rebuilds an English description from the parsed steps. Examples: "@x2 needs to say '@x1'" (783), "wait @x1 seconds" (852), "then all will automatically say '@x1'" (861), "the hour of the day is @x1" (803), "requires at least @x1 assistants like @x2" (871). `StdDeity` shows these in its help text (StdDeity.java:233-259, 1474-1584), and `CMAbleComps` in component descriptions (CMAbleComps.java:560).

### 9. The null object and versioning

- `NonTriggerer` (206 lines) is the default for every mob (`MOBS/StdMOB.java:73`, `MOBS/StdMobWrapper.java:52`). All methods are no-ops and `isDisabled()` is true (87-90).
- A real `DefaultTriggerer` is created only when a mob needs a ritual (CMAbleComps.java:1411-1412, 1446-1447, 1473-1482).
- `TrigSignal.sig` is a single global int (Triggerer.java:340-343). `CMAbleComps` increments it after rebuilding the component map (CMAbleComps.java:892). Each triggerer records the value it was built with, and `isObsolete()` compares it with the current value (DefaultTriggerer.java:84-87; NonTriggerer.java:74-78). Obsolete triggerers are replaced lazily the next time they are used (CMAbleComps.java:1425-1438, 1452-1456).

### 10. JavaScriptingEngine (dormant Rhino engine)

- The class is `JavaScriptingEngine extends ScriptableObject implements ScriptingEngine` (64). It keeps an internal `DefaultScriptingEngine` as `mpEngine` (79). That engine runs the MOBPROG commands and functions, and everything the JavaScript scope does not define falls through to it.
- Scope setup (`getScope`, 150-171): it removes `getClass`, `JavaAdapter` and `JavaImporter` (154-156), exposes sixteen Java methods (`functions`, 131-148: quest, setupState, toJavaString, getCMType, mob, getVar, setVar, host, source, target, monster, item, item1, item2, message, objs), and evaluates the script once under a 10-second limit (160).
- Name dispatch (`get`, 271-358):
  - A name in `methH` (MOBPROG commands, from `Common/interfaces/ScriptingEngine.java:620`, such as MPECHO, IF, FOR and RETURN) becomes a function. It rebuilds the call as one MOBPROG line and runs it with `mpEngine.execute` (291-334).
  - A name ending in `$` calls `mpEngine.functify` and returns the value (335-349).
  - Other names in `funcH` (value functions such as RAND, STAT and HAS; `ScriptingEngine.java:496`) go to `mpEngine.eval` and come back as a Boolean (350-358).
- `$`-variables (`getArgumentItem` 173-252, `varify` 610-966). The letter after `$` selects the value. Examples: `$a` area, `$n` source, `$t` target, `$o` primary item, `$p` secondary item, `$i` monster name, `$r` random player, `$c` random inhabitant, `$x` exit, `$d` and `$D` room display text and description, `$e $m $s $y` source pronouns, `$E $M $S $Y` target pronouns, `$h $j $k` monster pronouns, `$f` follower. Angle brackets hold script variables, square brackets quest item names, braces quest mob names, and percent signs an inline function. A `.n` suffix indexes a list value and `.LENGTH#` returns its length (929-957). Escaped dollar signs are handled at 621 and 633-636.
- Sandbox (1307-1371): `CustomClassShutter` blocks `java.io.`, `java.nio.`, `java.lang.Runtime`, `java.lang.ProcessBuilder`, `java.lang.reflect`, `java.security`, `sun.`, `com.sun.`, `org.mozilla.javascript` and `java.rmi.` (1358-1367). `SandboxNativeJavaObject` hides members of `Class` objects except a few name getters (1316-1331).
- Limits (`LimitedContext`, 1287-1305): the context checks an expiry every 100 instructions (1279, 1302). The time limit is 10 s for `execute`, `getScope` and `callFunc` (160, 561, 1047) and 4 s for `eval` and `functify` (1008, 1025).
- Hooks: `tick`, `executeMsg` and `okMessage` (503-523) only refresh the context. `okMessage` always returns true (522), so the engine never vetoes a message.
- Approval: `preApproveScripts` sets `approvedScripts` (1261-1263), and that flag is never read. The class never calls `CMSecurity`, so the JSCRIPTS approval settings do not apply to it.
- Reachability: by grep, no source or data file in the CoffeeMUD folder names the class except its own file and the project's coverage manifest. Because `COMMON=%DEFAULT%` (coffeemud.ini:2251) auto-registers every class in the Common package (CMClass.java:3216), it can be obtained through `CMClass.getCommon("JavaScriptingEngine")` or a stored class ID. Nothing in the repository does either. The game's scripts use `DefaultScriptingEngine` throughout (for example `Behaviors/Scriptable.java:53`, `Commands/MPRun.java:96`).
- The live JavaScript path is elsewhere. `DefaultScriptingEngine` runs `<SCRIPT>` blocks through Rhino with its own `JScriptEvent` scope, guarded by `approvedScripts` or `CMSecurity.isApprovedJScript` (DefaultScriptingEngine.java:9276-9300). That is covered in bucket b037.

### 11. IntermudClient

- Fields (69-73): `i3mud` (`CoffeeMudI3Bridge`), `imc2` (`IMC2Driver`), `grapevine` (`GrapevineClient`), `discord` (`DiscordClient`), and `cm1Servers` (a list of `CM1Server`). `isOnline(proto)` (1350-1366) checks each one.
- Start (`activate`, 384-392) starts CM1, I3, IMC2, Grapevine and Discord. Each start is gated as follows:
  - configuration flags: `RUNCM1SERVER` as a private property equal to TRUE (293-294), `RUNI3SERVER` (186), `RUNGVSERVER` (232), `RUNIMC2CLIENT` (352);
  - the host thread: I3, Grapevine and IMC2 start only on the main host thread (187, 237, 352), CM1 does not check it;
  - the CMSecurity disable flags (188, 238, 352);
  - Discord needs a jar path, a bot key and at least one channel (327-330).
  - `I3PORT` falls back to 27766 when it is zero (193-195).
- Routing tells (`imudTell`, 566-647) tries I3 (588-613), IMC2 (615-623) and Grapevine (625-640). If none accepts, it reports "@x1 is an unknown mud." (644). The sender sees "You tell ..." first (597, 619, 635), and the line is also pushed onto the session's tell stack (605, 621, 637).
- Routing who (`imudWho`, 484-539) tries I3 (494-516), IMC2 (518-522) and Grapevine (524-532). `triedAny` decides whether to report that the mud name is unavailable (534-537).
- Channel messages (`imudChannel`, 792-822), called from `Libraries/CMChannels.java:1143`. The message fans out to IMC2 (808-809), I3 (811-812), Discord (814-818) and Grapevine (820-821), each only if it knows the channel. A message starting with `:` or `,` is treated as a social and rendered through the social system (IMC2 at 662-711; Grapevine at 719-789).
- Remote socials: `socialNormalFixOut` (1165-1178) replaces the source and target tokens with the names, including the he, she and his-or-her tokens, and turns single quotes into backticks (1176). Remote readers therefore never see pronouns.
- Lifecycle: `registerPlayerOnline` and `registerPlayerOffline` (1673-1704) only notify Grapevine. They are called from `Libraries/CharCreation.java:4552` and `Common/DefaultSession.java:2845`.
- Other callers: `Commands/Tell.java:166-167`, `Libraries/CommonMsgs.java:518-521`, `Commands/WhoIs.java:62-90`, `Commands/Who.java:269-272`, `Commands/ChanWho.java:76`, `Common/DefaultSocial.java:620-621`. The admin command sets `Commands/Grapevine.java`, `Commands/IMC2.java` and `Commands/I3Cmd.java` call the channel and start/stop methods. `Libraries/intermud/i3/I3Client.java:370` and 651 restart I3 from inside the I3 client.

## Content worth mining

- **Deity ritual slots.** A deity holds six rituals: WORSHIP_BLESSING, WORSHIP_CURSE, CLERIC_BLESSING, CLERIC_CURSE, SERVICE and POWER (`MOBS/interfaces/Deity.java:147-152`). The `HolyEvent` enum (127 onward) starts with SERVICE_BEGIN, SERVICE, SERVICE_CANCEL, CURSING, BLESSING and REBUKE (only the first six were read).
- **Default deity rituals** (`MOBS/StdDeity.java`): cleric and worship blessing default to "SAY Bless me <deity name>" (161, 194). The service ritual defaults to "SAY Bless us <deity name>", then WAIT 10, WAIT 10, ALLSAY "Amen.", then SAY "Go in peace" (215). Those four steps are the clearest worked example in the bucket.
- **Ability components** (`resources/skills/components.txt`, 38 lines, read in full). The format is `Skill=(disposition:fate:amount:component:mask)` joined with `&&` or `||`, with an optional quoted description. A `(TRIGGER:ritual:mask)` term adds a ritual requirement. Dispositions are inventory, have, held, worn, nearby and onground. Fates are consumed and kept. Shipped entries (lines 34-38), paraphrased: bandaging takes one cloth, or five dust for elves, or a strip of cotton or wool; the thief runecasting skill needs a rune tile, stone or cube; tarot reading needs a deck of tarot cards; tasseography needs coffee (consumed); fortune telling needs a crystal ball. The file contains no active TRIGGER entry.
- **JavaScript variable codes** (section 10). They are a compact template language for messages, and a good model for a text game's message templates.
- **Social token set** (`socialNormalFixOut`, 1167-1176). The nine tokens are source and target names and their pronoun forms (HIM-HER, HIS-HER, HE-SHE), with the first two also written as NAMESELF.
- **Ritual description strings** (`getTriggerDesc`, 783-871). A ready-made set of player-facing phrases for each step kind.

## Notable design ideas

- **One definition, three uses.** The ritual text is parsed once into steps, and the same steps are used to check a player's actions, to make an NPC perform the ritual, and to describe the ritual to players (`getTriggerDesc`). The description cannot drift from the behaviour.
- **Cheap filter, then full match.** `isTracking` (1215-1236) looks only at the next step's message code and does not evaluate it. Only candidates reach `stepGetCompleted`. This keeps many concurrent rituals cheap on every message.
- **Waits without timers.** A waiting state is kept in a queue (`waitingFor`) and polled once per tick (1851-1880). Each state stores only a timestamp and an index.
- **Synthetic actions.** An NPC's ritual makes its own generated items, so the ritual never needs the physical objects to exist (1019-1132).
- **Global version counter.** One integer invalidates every cached triggerer at once, and each object refreshes itself when next used (Triggerer.java:340-343; section 9).
- **Null object.** `NonTriggerer` lets the common path run without null checks, and it is used for every mob that has no rituals.
- **Assisters.** `INCLUDE` and `ASSISTn` let several people take part in one ritual, with the assister list kept in the host's state.
- **Interpreter deadlines.** The JavaScript engine enforces a wall-clock limit inside the interpreter by checking every 100 instructions (1279, 1302). That is a reusable pattern, separate from the flawed sandbox.

## Problems and cruft

### DefaultTriggerer

1. **Progress text is wrong.** `key.toString()+": "+state.completed+1` (1721) concatenates the number and then appends "1". For the first step the text is "key: 01" rather than "key: 1". The debug line at 1716 uses `(state.completed+1)` correctly.
2. **TIMEOUT on the player path has no effect.** The write to `state.time` at 1472 is overwritten by `setCompleted` (198-202, called at 1722). Only the NPC path (1016) sets the deadline. By reading.
3. **An empty or `-` ritual can throw.** Such a ritual stores an empty array (252-258, 697-699). `isTracking` then returns true for every message, because `peekIndex (0) >= length-1 (-1)` (1223-1225). `stepGetCompleted` then reads `triggers[0]` when there is no state (1309-1321), which is an ArrayIndexOutOfBounds. `genNextAbleTrigger` guards the case (964) but the other two paths do not. The `CMAbleComps` path that calls `addTrigger` without a length check (1414) can reach this. By reading.
4. **`isTracking(key, msg)` has no null check.** It dereferences `rituals.get(key)` (1223-1224). `StdDeity` calls it for six slots (StdDeity.java:799-835). Whether any slot can be unset in practice was not checked.
5. **State is keyed by name.** Trackers (706-726) and the ignore set (239, 930) use `mob.Name()`. Two different mobs with the same name share ritual progress while the first is still referenced. This matters for NPC clones.
6. **Silent reordering.** The starter rule (673-696) changes the author's step order. There is no warning.
7. **`reqCount` is dead.** It is parsed (308) and never used.
8. **SAY matches substrings.** `indexOf` on the upper-cased message (1340, and 1619 for EMOTE) matches anywhere in the utterance, so a phrase can match inside another word.
9. **RANDOM re-rolls on every message.** The step has no message code, so every message from the source that is not ENTER, LEAVE or LOOK is a candidate (1244-1252, 1360-1363). A "RANDOM 10" step is effectively a per-action roll. The description (849) reads "x% of the time", which matches only if the roll is per action.
10. **Documentation mismatch.** `Triggerer.java:296-297` says "typically within 4 seconds". The code keeps the window at two ticks, which is 8 s at default ticks (129).
11. **Ignore set may expire.** `ignoreOf` is a `LimitedTreeSet` with 60 s idle expiry (LimitedTreeSet.java:118). A name set during a long `StdDeity` action could drop out before the matching `setIgnoreTracking(false)` call (StdDeity.java:484-606). Not confirmed at runtime.

### JavaScriptingEngine

12. **Wrong variable.** Lines 472 and 494 assign `I2` from `msg.tool()` when the test is on `msg.target()`. If the target is an Item and the tool is not, the cast throws. By reading.
13. **NPC context is null for non-mob hosts.** `getMakeMOB` fills `factoryMOB` and then returns null for any host that is not a MOB (977-991). `monster()`, `mob()` and the `$i` family are then null for item and room scripts. The consequences were not traced.
14. **Inconsistent result handling.** `execute` casts the result straight to String (562-568), while `functify` converts with `Context.toString` (1031-1032). A final expression that yields a number would throw. By reading.
15. **Value functions return booleans.** Names in `funcH` without the `$` suffix go to `mpEngine.eval` and come back as a Boolean (350-358), so a function such as STAT loses its value unless the `$` form is used. By reading.
16. **No approval gate.** `approvedScripts` (76, 1261-1263) is never read, and the class never calls `CMSecurity`.
17. **Blocklist sandbox.** Only the packages listed at 1358-1367 are blocked. `java.lang.System`, `java.lang.Thread`, `java.net` and the game's own packages are not on the list. `SandboxNativeJavaObject` filters only `Class` members (1316-1331). Whether a script can reach a dangerous static method was not tested.
18. **Null dereference.** `message()` reads `mpContext.msg` without a check (1456-1458).
19. **Engine never vetoes.** `okMessage` always returns true (518-523).

### IntermudClient

20. **I3 reference dropped.** The catch block in `startGrapevine` sets `i3mud = null` (281) without shutting the I3 bridge down.
21. **Dead computation.** `playstate` is computed (243-266) and never used. The Grapevine client is created without it (267-268).
22. **Duplicate check.** The Discord start test checks `DISCORD_BOT_KEY` twice (328-329). One of the two was probably meant to be a different key.
23. **Possible ConcurrentModificationException.** `startCM1` removes from `cm1Servers` while iterating over it (297-304). This can fail when two servers share the same INI file. By reading.
24. **Null dereference in Grapevine channel sends.** When a social has no target, the third branch calls `msg.target().name()` (771), which throws. By reading.
25. **Unguarded Grapevine calls.** `queryService` for NAME (1203-1204) and `channelListen` for Grapevine (1469) do not check that the client exists. The other Grapevine calls do (1505, 1543, 1579).
26. **Dead store.** `emote=2` (707) is overwritten by `emote=1` (709).
27. **Misleading helper.** `destroymob` (649-657) destroys the mob's room as well. It is safe only because the caller creates a throwaway room (667).
28. **Hard-coded target.** The IMC2 ping goes to "Server01" (1274).
29. **Typo and untranslated text.** "nto" (1402); "Not found!" is not passed through `L()` (955).

### Outside the bucket

30. **JavaScript approval can be bypassed on second use.** In `CMSecurity.isApprovedJScript` (1070-1085), an unseen script is stored in the approval table and rejected (1081-1082). On the next check the table holds an entry, so the script is accepted (1078-1084). The table is keyed by the 32-bit `String.hashCode` (1077), so hash collisions can approve the wrong script. Pending entries are left out of the file the next time an approval is saved (`approveJScript`, 1018-1034), but the in-memory entry stays until restart. By reading. `DefaultScriptingEngine.java:9276` is the caller.

## Takeaways for a Python SUD

1. **Borrow the ritual grammar as data.** Keep the idea of ordered steps, OR alternatives and a capture flag, but build the steps as Python objects at load time and validate them there, as the builder editor does. A sketch:

```python
from dataclasses import dataclass, field

@dataclass(frozen=True)
class Step:
    kind: str                  # "say", "say_all", "wait", "put", "random", "in_room", ...
    arg: str = ""              # text, ticks, percent, item name ...
    capture: bool = False      # keep the matched words as ritual args ("+" in CoffeeMud)
    alternatives: tuple = ()   # OR-alternatives ("|" in CoffeeMud)

@dataclass(frozen=True)
class Ritual:
    name: str
    steps: tuple[Step, ...]    # AND-ed, in order

@dataclass
class Progress:                # one per (entity id, ritual name)
    completed: int = -1        # index of last completed step
    last_turn: int = 0         # when it completed; WAIT counts from here
    args: list = field(default_factory=list)

SERVICE = Ritual("service", (  # mirrors the default in StdDeity.java:215
    Step("say", "bless us {deity}"),
    Step("wait", "10"),
    Step("wait", "10"),
    Step("say_all", "Amen."),
    Step("say", "go in peace"),
))
```

2. **Keep the three uses.** One definition should drive the player-side check, the NPC action and the help text. Generate the description from the parsed steps, as `getTriggerDesc` does, so help never goes stale.
3. **Use game time for waits and deadlines, in one place.** Store the turn a step completed and count WAIT and TIMEOUT from it. Set deadlines in the single function that completes a step; the TIMEOUT bug above shows what happens when two paths disagree.
4. **Key progress by identity.** Use entity IDs, not names, for `(entity, ritual)` progress. Clones with the same name must not share state.
5. **Drop assisters and the intermud layer.** `INCLUDE` and `ASSISTn` become "a companion is present", and the whole of `IntermudClient` (I3, IMC2, Grapevine, Discord, CM1) has no single-player use.
6. **Replace the JavaScript engine with data.** Conditions can be a small declarative expression language or plain Python predicates on world state. If user scripts are needed, run them out of process with a hard timeout. An in-process `exec` cannot be sandboxed reliably, and the approval scheme in `CMSecurity` should not be copied.
7. **Predicate-based items.** Instead of requiring a generated item, match on the tags of the items present (for example "a container holds an item tagged X"). NPC rituals can then just check the predicate.
8. **Message templates.** Adopt the `$`-letter or `{name}`-style token approach with a restricted mapping (`str.format_map` over a safe object) for messages, including the pronoun forms.
9. **Keep the global version counter** for hot reloads of ritual or content data. It is cheap to check and avoids rebuilding caches one by one.

## Coverage

- **Read in full (6 files):** `Common/interfaces/Triggerer.java` (344 lines), `Common/NonTriggerer.java` (206), `Libraries/interfaces/IntermudInterface.java` (341), `Common/JavaScriptingEngine.java` (1473), `Common/DefaultTriggerer.java` (1944), `Libraries/IntermudClient.java` (1724).
- **Read in part (outside the bucket, to explain interactions):** `Libraries/CMAbleComps.java` (ranges above), `MOBS/StdDeity.java` (55-180, 701-846, 1242 and the call sites in the chapter), `Abilities/Misc/TriggeredAffects.java` (418-522, 594-671), `MOBS/StdMOB.java` (73, 900-906, 3690-3704, 4326-4340, plus a grep for `setObsolete`), `Abilities/StdAbility.java` (2092-2110), `Libraries/CMGenEditor.java` (5890-5911), `Common/DefaultScriptingEngine.java` (9262-9300), `core/CMSecurity.java` (1008-1085), `core/CMClass.java` (2511-2535 and 3216), `core/CMProps.java` (681, 2716-2724), `core/collections/LimitedTreeSet.java` and `LimitedTreeMap.java` (constructors), `Common/interfaces/CMMsg.java` (constants, by grep), `Common/interfaces/ScriptingEngine.java` (the `methods` and `funcs` arrays, by grep and awk), `MOBS/interfaces/Deity.java` (enums, by grep), `resources/skills/components.txt` (in full), `coffeemud.ini` (line 2251).
- **Named only, not read:** `Libraries/intermud/` (all protocol clients), `Libraries/intermud/MiniJSON` and the rest of `StdDeity.java`.
- **Skipped:** the ability-component parser in `CMAbleComps.java` (roughly lines 100-250, 300-530 and 900-1395), the body of `StdDeity` outside the sections above, and the callers of `setObsolete` in `StdMOB`.
- **Grep-only checks:** no source or data file names `JavaScriptingEngine` except its own file and the coverage manifest; `reqCount` appears only at lines 120 and 308; `approvedScripts` is written and never read in the JavaScript engine; `playstate` appears only at lines 243-264; `ritualCache` is used only in `DefaultTriggerer.java`.
