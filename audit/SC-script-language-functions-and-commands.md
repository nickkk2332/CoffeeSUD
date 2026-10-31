# Script language, functions and commands

MOBPROG syntax, variables, string, list, number and time functions, cross-script calls, general script commands, zapper mask keys and the script documentation. Part of the [builder capabilities audit](README.md).

<a id="sc1"></a>

## SC1. Lists in variables: loop, add, remove, shuffle

*Kind:* script function and script command · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** Space-separated lists half work: `.LENGTH#` counts words, `.n` or `.$0` takes one, `STRIN` tests membership, `$<V>.$%RAND0NUM($<V>)%` picks one at random, and `mpsetvar WORLD V $<WORLD V> cook` appends. But `FOR $0 IN $<WORLD SUSPECTS>` loops once, because `FOR ... IN` splits on commas before it substitutes; the guide's space-separated example has the same fault. Removing a word, de-duplicating, shuffling, sorting or joining need index loops. Tags use commas, words use spaces.

**Add.** `FOR $0 IN LIST <text>`, which substitutes first, then splits on spaces (with quotes), or on commas if there are any. `MPLIST <host> <var> ADD|ADDUNIQUE|DEL|CLEAR|SHUFFLE|SORT <words>` and `$%LISTJOIN(text sep)%`. Readable aliases of today's forms: `LISTHAS`, `LISTCOUNT`, `LISTGET`, `LISTFIND`, `LISTPICK`. One list convention, documented in the guide.

**Example.** A manor mystery: `MPLIST WORLD SUSPECTS ADD cook butler gardener`, the culprit is `$%LISTPICK($<WORLD SUSPECTS>)%`, and `FOR $0 IN LIST $<WORLD SUSPECTS>` hands out alibis.

<!-- from: mobprog-language/mpl-lists -->

<a id="sc2"></a>

## SC2. Absolute game time, elapsed time and weekdays

*Kind:* script function · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** `DATETIME` gives the current hour, day, week, month, season or year; `DATETIME(ALLTIME)` returns 0, and only JavaScript can read `toHoursSinceEpoc`. Timed tattoos (`MPTATTOO $n '450 PLANTED'`, tested with `HASTATTOOTIME`) handle delays, but in ticks the builder converts by hand, and only on mobs. No function gives the weekday (`+WEEK` is the week of the month). The `ISRL*` tests read the computer's clock, which means nothing in a turn-based game.

**Add.** `$%DATETIME(STAMP)%` and `$%GAMETIME(HOURS|DAYS|MONTHS|YEARS)%`, counted from the area calendar's epoch. `$%ELAPSED(<stamp> DAYS)%` and the test `ELAPSED(<stamp> DAYS >= 3)`. `MPSETVAR host var NOW`. `DATETIME(DAYOFYEAR|WEEKDAY)`, `WEEKDAY()` and `ISWEEKDAY(name)`. `$%TICKS(3 DAYS)%` for tattoo durations. Document `ISRL*` as real-world, or add a world option that maps them to game time.

**Example.** A smith runs `mpsetvar $n SWORD_ORDERED NOW`; once `elapsed($<$n SWORD_ORDERED> DAYS >= 3)` he hands over the sword. A market opens only when `ISWEEKDAY(Marketday)`.

*See also:* ST11 (Both add weekday tests on the area calendar (ISWEEKDAY, WEEKDAY())); ST6 (Both add a GAMETIME() function; define it once).

<!-- from: mobprog-language/mpl-gametime, world-systems/ws-func-gametime -->

<a id="sc3"></a>

## SC3. Load mobs and items from the catalog

*Kind:* script command and quest feature · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** `MPMLOAD` and `MPOLOAD` copy a template found by name in the world, or load one from a `.cmare` or generator file. None reads `world/catalog.cmare`, nor do quest `LOAD MOB` and `RandomMonsters`. The workarounds are a hidden storage room of prototypes, which bypasses catalog caps, or a generator file with `class="catalog"`, whose picks freeze after the first call. `MPMLOAD` also clears its template's start room, and its `ALL` keyword loads nothing.

**Add.** A `CATALOG` source: `MPMLOAD CATALOG <name> [count]`, `MPOLOAD CATALOG <name> [INTO <container>]`, and the room and shop forms, setting `$b` to the last copy. The same for quest `load mob|item catalog <name>` and `RandomMonsters` (`catalog:goblin,goblin shaman`). Copies go through `changeCatalogUsage`, so caps and DEVPLAN item 1's rules hold; `FORCE` skips them. Tests `CATALOG(name)` and `$%CATALOGCOUNT(name)%`. Fix `ALL` and the start room.

**Example.** A chieftain's `HITPRCNT_PROG 50` runs `mpmload catalog goblin guard 2`, then `mpforce $b kill $n`. A reward `mpoload catalog the Sunblade` does nothing if the unique Sunblade exists.

*See also:* C4, C9 (All three add a catalog source to RandomMonsters and MPMLOAD (catalog:name, CATALOG= weights); settle one syntax); SC14 (Both fix MPMLOAD clearing its template's start room).

<!-- from: mobprog-language/mpl-catalog-load, encounters-combat/enc-catalog-load -->

<a id="sc4"></a>

## SC4. Saved variables per object, not per name

*Kind:* script command and script function · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `MPSETVAR` and `VAR` key a mob's or item's variables by its name (`getVarHost`), so every "a city guard" shares one `BRIBED` and every "a lever" one state. `MPSETINTERNAL SCOPE *` gives each engine its own table, but other scripts cannot read it and a save drops it. `$@i` keys on the Java identity, which changes on every load. Tattoos and `TAGS` are saved per copy, but hold only flags.

**Add.** A per-object host, `@`: `MPSETVAR @$i BRIBED 1`, `VAR(@$t BRIBED == 1)`, `$<@$i BRIBED>`. Values are stored on the object in the `ExtraData` effect, which a save already writes, so they move and die with that copy. `MPSTATE <obj> <name> <value>|CLEAR`, the test `STATE(<obj> <name>)` and JS `state(obj, name[, value])` use the same store. `MPSETINTERNAL VARHOST INSTANCE` makes bare `$i` mean `@$i`. Mob values clear on rejuv unless marked `PERSIST`. The guide explains how this differs from `SCOPE *`.

**Example.** Five gate guards share one script; bribing the east guard opens only his gate. Three goblin archers each count their own arrows.

*See also:* JS7 (Two saved per-object state stores: SC4's @-host variables in ExtraData and JS7's cm.store keyed by host); S7 (S7 saves the INDIVIDUAL-scope variables that SC4 says a save drops; SC4's per-object store is the other answer to the same loss); JS17, SC6, I2 (JS17's state(), signal() and door() helpers wrap SC4's per-object state, SC6's signals and I2's MPDOOR).

<!-- from: mobprog-language/mpl-instance-vars, items-puzzles/ip-object-state, encounters-combat/enc-instance-vars -->

<a id="sc5"></a>

## SC5. Read and change the message a script reacts to

*Kind:* script function and script command · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `CNCLMSG_PROG` and `EXECMSG_PROG` match any message type, and `=S|T|O VAR=VAL` filters on the source, target or tool by exact equality. But a script cannot read the message's value (damage, XP), its texts (a crime name), its tool's ability ID or the type that matched, and can only cancel it. `CNCLMSG_PROG` keeps the `CMMsg` in `$0`, usable from a `<SCRIPT>` block past the approval gate, undocumented. `EXECMSG_PROG` keeps nothing. Only `DAMAGE_PROG` passes a value, in `$g`.

**Add.** Keep the message in both; `EXECMSG` keeps a copy of its fields, as `CMMsg` objects are pooled. `$%MSG(TYPE|VALUE|SOURCE|TARGET|TOOL|TOOLID|STEXT|TTEXT|OTEXT|SMINOR|TMINOR|OMINOR)%`, tests like `MSG(VALUE > 10)`, and `$q` for the tool's name. In `CNCLMSG_PROG`: `MPMSG VALUE <n|*0.5|-5>`, `MPMSG TEXT S|T|O <text>`, `MPMSG TARGET <obj>`. Let `=S|T|O` take `*` wildcards and masks.

**Example.** A frost shrine's `CNCLMSG_PROG DAMAGE` runs `MPMSG VALUE *0.5` when `$%MSG(TOOLID)%` contains Fire. A fence's `EXECMSG_PROG LEGALWARRANT` reads `$%MSG(OTEXT)%` to learn the crime.

*Note.* JavaScript can already read and change the message: in a `CNCLMSG_PROG` `<SCRIPT>` block, `objs()[0]` is the live message (see *Already possible* in the README). What this item adds is the MOBPROG forms, `MSG()` and `MPMSG`.

*See also:* ST12 (SC5's MSG() functions are the message-detail reads that ST12 says it pairs with); JS15, JS17 (Message access and veto from scripts: SC5 in MOBPROG, JS17's msg() and cancel(), JS15's cancel(); JS15 and JS17 both add cancel()).

<!-- from: mobprog-triggers/mt-msg-access, mobprog-language/mpl-msg -->

<a id="sc6"></a>

## SC6. Signals and function calls between scripts

*Kind:* script command and script trigger, with a JavaScript hook · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `MPCALLFUNC` and `CALLFUNC` find functions only in the running script itself, so a lever cannot tell the gatehouse to raise the portcullis with MOBPROG commands. Only an approved inline `<SCRIPT>` JavaScript block can do it, by fetching the target's script engine and calling its Java `callFunc`. Stopgaps: `MPAT <room> <command>`; `MPSCRIPT <target> EXECUTE`, a one-off `ONCE_PROG` on the target that cannot reach its own functions; echoes caught by `ACT_PROG`, which the player sees unless they are `MPECHOAT` aimed at an NPC (only that NPC sees it, yet every `ACT_PROG` in the room matches it) or at a room the player is not in; or variables polled by `RAND_PROG`.

**Add.** `MPCALLFUNC <target> <func> [args]` and `CALLFUNC(<target> func args)`. `MPSIGNAL <target> <name> [args]`, the target being an object, a room ID, an exit `room:north`, `TAG:<tag>[@WORLD]`, `ROOM`, `AREA` or `WORLD` (through `CMMap.scriptHosts`). It fires `SIGNAL_PROG <name>|ALL` on each target's scripts: `$g` the args, `$n` the actor, `$o` the sender. Queued, optionally delayed by ticks, silent. JS gets `signal(target, name, args)`. The puzzle props proposed elsewhere would emit signals.

**Example.** Three braziers tagged `sunaltar` run `MPSIGNAL TAG:sunaltar lit` when lit. The altar's `SIGNAL_PROG lit` counts them and opens the sanctum door at three.

*Re-checked against the code after writing: corrected.*

*See also:* I9, I12 (later), I15, I25 (The puzzle props send and receive SC6's signals (signal gate1 on, ONFULL=signal, SIGNAL inputs)); JS17, SC4, I2 (JS17's state(), signal() and door() helpers wrap SC4's per-object state, SC6's signals and I2's MPDOOR).

<!-- from: mobprog-triggers/mt-signal, mobprog-language/mpl-signal, items-puzzles/ip-signal -->

<a id="sc7"></a>

## SC7. MPWAIT: pause a script and resume it later

*Kind:* script command · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `MPALARM` delays one command; the rest of the body runs at once. A cutscene or ritual must be split into `FUNCTION_PROG` steps chained with `MPALARM n MPCALLFUNC step2`. Those alarms keep `$n` and `$t` but are lost on save. `DELAY_PROG` repeats rather than pauses, and `MPWHILE` is capped at four seconds of real time.

**Add.** `MPWAIT <ticks>`, `MPWAIT <n> HOURS` (game time) and `MPWAIT UNTIL (<condition>) [max ticks]`. They suspend the body and resume at the next line, keeping `$n`, `$t`, `$o`, `$0`-`$9` and the enclosing `IF`, `FOR` and `SWITCH` frames. Pending waits are saved with the game. `MPSETINTERNAL CANCELWAITS` drops them. The resumable frame runs on the single game thread, unlike the prompt commands, which spawn a thread.

**Example.** A shrine's `GREET_PROG`: `mpecho The candles gutter.`, `mpwait 2`, `mpecho A voice whispers your name, $n.`, `mpwait 3`, then `mpcast bless $n` if not already blessed.

*See also:* S7 (SC7's saved pending waits and S7's saved queued alarms belong in one script-state section of the save); N28 (Two answers to scripted cutscenes: MPWAIT in any script, or a Scene behaviour for NPC exchanges).

<!-- from: mobprog-language/mpl-wait -->

<a id="sc8"></a>

## SC8. Party and terminal awareness in scripts

*Kind:* script function · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Scripts treat each party member as a separate player and cannot ask who is being played, who leads, or who is in the party; the engine never refers to `Party`. With party windows off, `MPECHOAT` to a background member goes to its silent `PartySession`, and the prompt commands likely ask it too. `GROUP_GREET_PROG`, `NUMPCSROOM`, `ISFOLLOW` and `$f` give rough group checks. Profile flags already exist as `HASACCTATTOO` and `MPACCTATTOO`, since the account is the profile; the guide should say so.

**Add.** Codes `$u` and `$U` for the character at the terminal (`Party.terminalOf`). Functions `ISPLAYED($n)`, `INPARTY($n)`, `PARTYLEADER($n)` (its text form gives the name), `PARTYSIZE($n)` and `$%PARTY($n)%`, the list of names. With windows off, `MPECHOAT`, `MPECHOAROUND`, `MPPROMPT`, `MPCONFIRM` and `MPCHOOSE` aimed at a background member go to the terminal, prefixed with the member's name.

**Example.** A ferryman's `GROUP_GREET_PROG 100`: `mpechoat $u Five coins a head, and I count $%PARTYSIZE($u)% of you.`

*See also:* ST10 (Both add PARTYSIZE, INPARTY, PARTYLEADER and $%PARTY%; build the functions once).

<!-- from: mobprog-language/mpl-party -->

<a id="sc9"></a>

## SC9. Bring the trigger and message-type help up to date

*Kind:* presentation · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** The in-game `SCRIPTABLE` help omits 12 of the 58 triggers, among them `GROUP_GREET`, `GIVING`, `CAST`, `WEARING` and `CMDFAIL`; `CMDFAIL_PROG`, the custom-verb trigger, is in no guide either. `MESSAGE_TYPES` stops at `LEGALWARRANT`. `MPSETINTERNAL`, `MPLLM`, `MPHIT`, `MPACHIEVE` and `MPACCUSE` are only in the HTML guide, and `MPSTEPQUEST` is nowhere. `LOGIN`, `LOGOFF` and `CHANNEL` read as multiplayer. `LIST MSGTYPES` and `LIST MSGMASKS` already print every type.

**Add.** Regenerate the lists in `resources/help/arc_behaviors.ini` from the engine's `progs`, `funcs` and `methods` arrays, and point `MESSAGE_TYPES` at `LIST MSGTYPES`. Add to `guides/Scriptable.html` a `CMDFAIL_PROG` section and single-player notes: what `LOGIN` and `LOGOFF` mean (new game, restore, switch, party), that only `INFO` and `QUESTCHAT` remain, `ONCE_PROG` and saves, the parent-area listener trick. Optionally, `LIST TRIGGERS` with each trigger's syntax and hosts.

**Example.** `HELP SCRIPTABLE` shows `CMDFAIL_PROG` with an example that adds "pray at altar".

*See also:* SC15, JS20 (SC9 and SC15 both rewrite guides/Scriptable.html with single-player notes and a CMDFAIL_PROG section, which JS20 says may be enough on its own); Q9, ST5 (Q9, SC9 and ST5 each ask to document MPSTEPQUEST or QUEST_TIME_PROG's -1 and -2 codes; write it once).

<!-- from: mobprog-triggers/mt-trigger-docs -->

<a id="sc10"></a>

## SC10. String functions

*Kind:* script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** MOBPROG has no case change, character length, substring, replace, trim, padding, capitalisation, a/an article or plural. The text forms of `STRCONTAINS` and `EVAL` return "[unimplemented function]". What exists: the `STRCONTAINS` and `STRIN` tests, `STRIN`'s word index, the word modifiers `.n`, `.LENGTH#`, `.DECOMMA#` and `.DESEMICOLON#`, and `$g` (lowercased) beside `$G`. A `<SCRIPT>` JavaScript block can do the rest, past the approval gate.

**Add.** `$%UPPER(s)%`, `$%LOWER(s)%`, `$%CAP(s)%`, `$%LEN(s)%`, `$%SUBSTR(s start [len])%`, `$%REPLACE(s 'old' 'new')%`, `$%TRIM(s)%`, `$%PADRIGHT(s n)%`, `$%ANAME(s)%` (a or an, from `CMLib.english()`) and `$%PLURAL(s n)%`, and the tests `STRSTARTS(s x)` and `STRENDS(s x)`. Give `STRCONTAINS` a text form returning the position, and `EVAL` one returning the result.

**Example.** `mpecho $%CAP($%ANAME($o)%)% flares in $%LOWER($n)%'s hand.`, or a shop sign padded into columns.

*Note.* `JSCRIPTS=2` already runs every JavaScript block without approval (see *Already possible* in the README), so the JavaScript route mentioned above needs no approval; what this item adds is the convenience of doing it without JavaScript.

<!-- from: mobprog-language/mpl-strings -->

<a id="sc11"></a>

## SC11. Numbers, dice and weighted picks

*Kind:* script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `MPSETVAR` reads a leading `+`, `-`, `*` or `/` as an operation on 32-bit integers, so `MPSETVAR $i MOD -5` subtracts; storing -5 means setting 0 first. `NUMBER()` rejects signs and decimals. `$%MATH(expr)%` already has decimals, `%`, `^`, `?` (random between, so `1?6+1?6+1?6` is close to 3d6), `<` (min) and `>` (max). Weighted picks exist only in generator XML (`PICKWEIGHT`, through `MPOLOAD FROMGENFILE`). `DiceLibrary`'s `rollNormalDistribution` is not reachable from scripts.

**Add.** `MPSETVAR host var =<value>` for literal assignment. Signed and decimal `NUMBER()`. Long arithmetic, with `%N` and `^N` operations. `$%DICE(3d6+2)%` and the test `DICE(1d20+5 >= 15)`. `$%PICK(a b "c d")%`, `$%WPICK(common:70 rare:25 legendary:5)%` and `$%ROLLNORMAL(3 6 0)%`. `$%CLAMP(v lo hi)%` and `$%ABS(v)%` as readable shorthands.

**Example.** A pit trap: `if dice(1d20+$%STAT($n DEX)% < 15)`, `mpdamage $n 5 15`. Loot: `mpoload $%WPICK("a copper ring":70 "a silver ring":25 "a jade ring":5)%`.

<!-- from: mobprog-language/mpl-numbers-random -->

<a id="sc12"></a>

## SC12. Add, remove and count tags from scripts

*Kind:* script command and script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Scripts test tags with `HASTAG` and loop over tagged objects with `FOR $0 IN #tag`, and `$%HASTAG(target)%` already returns an object's whole tag list. The only way to set one is `MPSET target TAGS`, which replaces the comma list; rebuilding it from `HASTAG`'s space-separated, quoted output is error-prone. Counting means a `FOR` loop with `mpsetvar $i N ++`. `HASTAG` also logs its errors under the `HASTATTOO` label.

**Add.** `MPTAG <target> ADD|DEL|TOGGLE <tag>`, `$%NUMTAGGED(tag)%` and the test `TAGGED(tag)`, for mobs, items, rooms and exits. Document `$%HASTAG(x)%` as the tag list, with `$%TAGS(target)%` as an alias, and fix the error label.

**Example.** Each villager the player helps gets `MPTAG $t ADD grateful`. The mayor checks `math($%NUMTAGGED(grateful)% >= 5)` before offering the key to the town.

<!-- from: mobprog-language/mpl-tags -->

<a id="sc13"></a>

## SC13. Script files with parameters

*Kind:* script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `LOAD=` takes only a file name, so one guard script shared by twenty guards cannot be told each guard's post or greeting. Setting variables in `ONCE_PROG` collides, since variables are keyed by name. A working pattern exists but is undocumented: put `FUNCTION_PROG POST~return castle#12~` before `LOAD=progs/guard.script` in each object's text, and read it in the file with `$%CALLFUNC(POST)%`. It is wordy, has no defaults, and a missing function logs an error.

**Add.** `LOAD=progs/guard.script POST=castle#12 GREETING="Halt, who goes there?"`. In the file, `$%ARG(POST)%` reads a value, `ARG(POST == castle#12)` tests it, and `ARGDEFAULT POST=...` lines set defaults. The arguments join the file name in the parse cache key. The cookbook also shows the `FUNCTION_PROG` pattern.

**Example.** One shopkeeper script serves every merchant in the world, each given its own `OPEN` and `CLOSE` hours and the room to walk home to.

*See also:* N7, P1, JS9 (Four ways to give a shared script or effect per-instance parameters (SC13's ARG(), N7's PARM(), P1's EFFECTPARM(), JS9's parms); decide them together).

<!-- from: mobprog-language/mpl-templates -->

<a id="sc14"></a>

## SC14. Fix the confirmed MOBPROG bugs

*Kind:* script function and command fixes, with a debug tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Confirmed in the code: `LEVEL_PROG` fills `$0`-`$9` with the whole argument array; `NUMMOBS(... MASK=...)` reads the wrong argument and gives 0; named XML for `MPMLOAD` and `MPOLOAD` is sought under trigger 55, not 58; the `MP` prefix fallback takes the last match; `MPDAMAGE ... kill` never kills; `INROOM(... BEACON)` drops the start room; `MPMLOAD` clears its template's start room; `MPACCUSE` checks the wrong object; `MPRLOAD` runs as a game command; one-digit `RAND_PROG` fires only in active areas; an unapproved JavaScript block is refused only once per boot; the text forms of `ISNPC`, `ISPC`, `ISIMMORT`, `EVAL` and `STRCONTAINS` are placeholders. `scriptableTest.script` names a `TEST SCRIPTABLE` command that no longer exists.

**Add.** Fix each, and re-check the `MPLLM` and `NUMMOBSINAREA` faults listed in `DOCS`. Turn `resources/progs/scriptableTest.script` into an automated check run by `tools/engine-tests.sh`, covering the new commands and functions too.

**Example.** A `LEVEL_PROG` congratulation using `$1` prints a Java object name today; a census NPC using `NUMMOBS` with a mask always reports 0.

*See also:* ST17 (Both fix LEVEL_PROG filling every local variable with the trigger array); SC3 (Both fix MPMLOAD clearing its template's start room); CR6 (Both fix INROOM(BEACON) throwing away the start room); DT10 (SC14 fixes the MP prefix fallback taking the last match, and DT10 makes an ambiguous MP word an error); JS1 (Both fix JSCRIPTS=1 letting a refused script run from its second try).

<!-- from: mobprog-language/mpl-bugfixes -->

<a id="sc15"></a>

## SC15. A CoffeeSUD edition of the Scriptable guide

*Kind:* presentation · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `guides/Scriptable.html` (8,733 lines) describes multiplayer CoffeeMud. It lacks `CMDFAIL_PROG`, says `INDIVIDUAL` variables are saved with the target (true only for players), and shows a space-separated `FOR IN` list that the code splits on commas. `IPADDRESS`, `ISPKILL`, `CHANNEL_PROG`, `LOGOFF_PROG`, clans and `MPPOSSESS` have no single-player meaning given. It never says account tattoos are the profile, that `ONCE_PROG` fires again on each restore, or what a save keeps.

**Add.** Single-player notes per entry, multiplayer-only entries marked, a "what survives a save" table, a quick reference grouped by purpose, and a cookbook: once-ever events, character versus profile flags, cutscenes, puzzle rooms, timed returns, catalog spawns. Also document what this audit found: `$@x`, timed tattoos as delays, `SWITCH` in place of `ELSEIF`, `MPAT` and `MPSCRIPT EXECUTE`, `FUNCTION_PROG` with `LOAD=`, `MATH`'s `? < > ^ %`, `$%HASTAG(x)%`, quest `SET DATE MUDDAY`, `CHARCREATIONSCRIPTS` `END`/`CHARLOGIN`/`ALLCHARLOGIN`, and `HASACCTATTOO`/`MPACCTATTOO`.

**Example.** A builder new to MOBPROG writes a lever-and-door puzzle from the cookbook instead of reading 8,700 lines of HTML.

*See also:* SC9, JS20 (SC9 and SC15 both rewrite guides/Scriptable.html with single-player notes and a CMDFAIL_PROG section, which JS20 says may be enough on its own).

<!-- from: mobprog-language/mpl-guide -->

<a id="sc16"></a>

## SC16. +ANYINGROUP and +ALLINGROUP mask keys

*Kind:* zapper mask key, used by behaviours and scripts · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Zapper masks test one creature at a time. `-FOLLOW` and `-RIDE` apply the rest of a mask to the leader or the mount, and `+/-GROUPSIZE` counts the group, so "the leader carries the pass" can be written with `+OR`. Nothing tests whether any or every member does, and `-IF` checks one subject. Behaviours that scan the whole room already cover part of this: `Aggressive -ITEM "+stolen idol"` attacks whoever in the room holds the idol, `Wimpy very=1 -CLASS +Paladin` runs from any paladin present while out of combat, and `GateGuard` counts every player in the room. What cannot be said is a check on one creature that also looks at its companions. `DoorwayGuardian` checks only the creature moving, `CombatAssister` the creature attacked, MudChat the speaker, and an in-combat `Wimpy` only the creature attacking it. So "let the party through if one of them carries the pass" or "flee when the attacker's party includes a paladin" cannot be written.

**Add.** `+ANYINGROUP <rest of mask>` and `+ALLINGROUP <rest of mask>`, applying the rest to the subject's group in the same room (leader, followers, party), built on the same recursion as `FOLLOW` and `RIDE`. Every mask user gets them at once: `Aggressive`, `DoorwayGuardian`, `GateGuard`, `Follower`, `CombatAssister`, `Wimpy`, `NastyAbilities`, `NiceAbilities`, MudChat `/` conditions and scripts.

**Example.** `DoorwayGuardian north +ALLINGROUP +ITEM "-guild pass"` lets the party in when one member carries the pass. DoorwayGuardian blocks movers that match its mask, and `+ITEM "-guild pass"` matches those without the pass, so it blocks only when every member lacks one. `+ANYINGROUP` here would block the party unless everyone had a pass. A ghoul with `Wimpy +ANYINGROUP -CLASS +Paladin` flees from a party that has a paladin.

*Re-checked against the code after writing: corrected.*

<!-- from: behaviors-npc/npc-group-mask -->

<a id="sc17"></a>

## SC17. ELSEIF, WHILE blocks, CONTINUE, named locals and parameters

*Kind:* script command · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** A chain of tests needs one ENDIF per branch, because `ELSE IF` on one line is refused. SWITCH/CASE helps only when every branch tests the same value. MPWHILE runs a single command, often MPCALLFUNC, and stops after 4 seconds of real time, which makes little sense under turn-based time. FOR has BREAK but no CONTINUE. There are only ten locals, $0 to $9, set with MPARGSET, and $9 is also SHOPHAS's price. A FUNCTION_PROG gets one string, $g, which the script splits with $g.0, $g.1 and so on.

**Add.** `ELSEIF <cond>`. A `WHILE <cond>` ... `ENDWHILE` block, capped by a number of passes, not by time. `CONTINUE` in loops. Named locals: `MPLOCAL name value`, read as `$(name)`, which last while the body runs. Named parameters: `FUNCTION_PROG heal WHO AMOUNT` binds $(WHO) and $(AMOUNT) from the words of the call. Optional: plain comparisons in IF, wrapped in EVAL automatically.

**Example.** A gate guard's GREET_PROG checks level, then faction, then whether the player carries a pass, in one IF / ELSEIF / ELSE / ENDIF.

<!-- from: mobprog-language/mpl-grammar -->
