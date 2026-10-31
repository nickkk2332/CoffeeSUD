# JavaScript

The JavaScript runtime and its limits, helper objects, hooks, scheduling, JS classes kept in the world folder, and JS approval. Part of the [builder capabilities audit](README.md).

<a id="js1"></a>

## JS1. Run world JavaScript without per-script approval

*Kind:* world option · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** The shipped coffeemud.ini has JSCRIPTS=1. A new or edited `<SCRIPT>` block is refused ("A Javascript was not authorized") until it is approved with MODIFY JSCRIPT. Approvals go to `::jscripts.ini` in the database VFS. Normal games do not copy it, so a script approved in builder mode does nothing in a real game or a fresh checkout. Only GenCommand pre-approves its scripts. GenAbility and GenTrap do not. JSCRIPTS=2 (always run) already exists, in the ini or as `sud.sh JSCRIPTS=2`. JSCRIPTS=1 also has a bug: a refused script is added to the table, so it passes from its second run each boot.

**Add.** Ship JSCRIPTS=2, in the ini or in SUD's SINGLE_PLAYER_SETTINGS, since the player is the author. If JSCRIPTS=1 stays as an option: save approvals to world/jscripts.ini, pre-approve in GenAbility and GenTrap, list unapproved JS in a world check, and fix the bug.

**Example.** A GenAbility "Chain Lightning" with a ten-line JS block works the same in builder mode, in a new game and on a fresh checkout. There is no hash to approve.

*See also:* SC14 (Both fix JSCRIPTS=1 letting a refused script run from its second try).

<!-- from: mobprog-language/mpl-js-approval, builder-commands/bc-jscript-approval, data-content/dc-js-trust-world -->

<a id="js2"></a>

## JS2. A time limit and readable errors for all JavaScript

*Kind:* debug tool and world option · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** `<SCRIPT>`, JRUN, JCONSOLE, quest JS and cron JS all call Context.enter() with no time limit. The world runs on one game thread, so `while(true){}` in a RAND_PROG freezes the game. Every entry point names its source `<cmd>`. JS errors go only to mud.log, which can be read in game with LIST LOG. MOBPROG errors also show in the room. JRUN exits its context outside a finally block. JavaScriptingEngine.LimitedContextFactory exists, but nothing uses it, and it is incomplete: it never sets setOptimizationLevel(-1) or setGenerateObserverCount(true).

**Add.** One factory, installed at boot with ContextFactory.initGlobal, so every entry point gets the limit: `JSTIMELIMIT=2` (seconds) in the ini, with a longer limit for JRUN. JRUN exits in a finally block. Source names say where the code is, such as "mob 'Brenna' @ Town#4 SPEECH_PROG" or "world/js/lib/dialog.js". In builder mode, errors and timeouts are shown to the builder with the line and a snippet. `JS ERRORS [n]` lists recent errors with host, trigger, line and time.

**Example.** A typo shows "JS error in 'Brenna' (Town#4) SPEECH_PROG line 3: ReferenceError: "soruce" is not defined". A runaway loop stops after 2 seconds.

*See also:* DT9, DT10 (All three route script and JavaScript errors, with file and line, to the builder; build one error channel).

<!-- from: javascript/js-runtime-guard -->

<a id="js3"></a>

## JS3. Shared JavaScript libraries with require()

*Kind:* JavaScript hook · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** JS has no module system. The Rhino jar contains CommonJS Require, but nothing installs it. Code can be shared in other ways. Many hosts can use LOAD=progs/dialog.script and share one parsed copy, `<SCRIPT>` blocks included. FUNCTION_PROG with MPCALLFUNC gives named subroutines. A JS file can be pulled in with eval(String(...Resources.getFileResource(...))). That last way has no module scope, no exports and no cache invalidation.

**Add.** Install Rhino's `require` in every JS scope. Modules are looked up in world/js/lib, then resources/js/lib. Each compiles once and is cached. The cache is dropped when the file changes or on JS RELOAD. Modules see `cm` and export with `exports.fn = ...`. `<SCRIPT SRC="lib/x.js">` runs a file as a block.

**Example.** world/js/lib/dialog.js exports `menu(npc, player, options)`. Every quest-giver's SPEECH_PROG runs `<SCRIPT> require('dialog').menu(monster(), source(), TOPICS) </SCRIPT>`.

*See also:* JS6 (JS22 (now in JS6)'s include() and JS3's require() are two module mechanisms for shared JavaScript; pick one); G17 (G17's template JavaScript loads libraries through JS3's require() from world/js/lib).

<!-- from: javascript/js-require-libs -->

<a id="js5"></a>

## JS5. JavaScript classes and script files kept in world/

*Kind:* data file and JavaScript hook · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** JS classes load from any folder named in the ini's class-path keys. `BEHAVIORS=%DEFAULT%;world/js/behaviors/` works now, in the ini or on the sud.sh command line, and LOAD BEHAVIOR file.js reloads a class at run time. But nothing in world/ loads by default. LOAD= includes are forced under resources/, so a .script file cannot sit next to its area. world.sum covers only areas, custom.cmare, catalog.cmare and quests.

**Add.** At boot, before areas import, load world/js/\<type>/*.js for every class type (behaviors, abilities, properties, items, mobs, races, charclasses, commands). MOBPROG files go in world/scripts/, reached with a `world:` prefix (LOAD=world:scripts/guard.script). Cover these files in world.sum and list them in LIST CUSTOM. Show compile errors and broken //extends lines to the builder and in a world check. Ship commented templates for a behaviour and a property. Add a base class, JSWorldBehavior, with onEnter, onLeave, onWeather and onDaypart hooks and helpers (area echo, roll, catalog spawn).

**Example.** world/js/behaviors/Ferryman.js waits for a fare and carries the party across the river. River.cmare attaches it as `Ferryman FARE=5 FAR=Riverbank#2`. Both files are committed together.

*See also:* S6 (Two ways to keep scripts and JS classes in world/: JS5's world: prefix and world/js class folders, or S6's overlay and WORLDDIR/classes); CR8 (CR8 names world-loaded JS classes (JS5) as the alternative to GenBuildingSkill).

<!-- from: javascript/js-world-folder, behaviors-npc/npc-js-world, behaviors-world/bw-jsworld, properties/pr-js-world-classes, data-content/dc-world-js-classes -->

<a id="js6"></a>

## JS6. One `cm` helper object in every JavaScript scope

*Kind:* JavaScript hook and script function · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** `<SCRIPT>` and JCONSOLE can call every MOBPROG command and function as an upper-case JS function (MPECHO, MPMLOAD, RANDNUM$, STAT$, with $-codes expanded), but only in string form. Other entry points get far less: JRUN 6 helpers, quest JS 4, cron JS 3 and JS classes 2. importPackage does not work because there is no ImporterTopLevel. Object-level work needs long Packages.com.planet_ink... paths and internal calls such as CMLib.law().getLegalBehavior(R).getWarrantsOf(...), which may change.

**Add.** One scope builder used by every entry point. It provides the MOBPROG bridge, ImporterTopLevel, and an object `cm` that returns Java objects. Members: lib, room('Area#12'), area, findMob, findItem, loadMob(catalogName, room), loadItem, echo, tell, say, emote, cmd, dice, chance, now, calendar, player(), party(), var, stat, mp('MPECHO hello') and log. World members: clock(area) (hour, season, moon, absHours and so on), passTime, weather(area).get/set/forecast, law(area).warrants/pardon/fine/accuse, shop(mob).stock/price, market, bank and holiday. Time and weather events reach JS through cm.on. All of it is documented in guides/.

**Example.** `var g = cm.loadMob('Town Guard', cm.room('Town#1')); cm.say(g, 'Halt, ' + cm.player().name() + '!');`

**Also merged here, from JS22 (Shared JavaScript code, a sud helper object, and debug()).** In all three scopes:
- `include('progs/lib/combat.js')`, cached and path-checked like `LOAD=`;
- `debug(x)`, which writes to DEVLOG;
- a `sud` object: `sud.time` (now, isTurnBased, advance(ticks), waitUntil(fn)), `sud.party` (leader, members, isMember), `sud.save` (slot, isBuilder, isDev), `sud.find` (room(id), mob(name, area), item(name, room)) and `sud.vars` (get, set, list).

JCONSOLE also gets `here`, `area` and `target`. `sud.on('save', fn)` waits for the lifecycle-trigger item.

*See also:* JS3 (JS22 (now in JS6)'s include() and JS3's require() are two module mechanisms for shared JavaScript; pick one); W1, W4, W6, W12, W14, W15, W27 (JS6's cm world members (law, passTime, holiday, weather, shop, bank, market) are JavaScript forms of these W items and depend on them); B11 (B11's JRUN builder object and JS6's cm object both add helpers to the JRUN scope); DT9, DT24 (JS22 (now in JS6)'s and DT24's debug() both write to DT9's developer log; one function).

<!-- from: javascript/js-helper-api, world-systems/ws-js-worldapi, sp-dev-tooling/spdev-jslib -->

<a id="js7"></a>

## JS7. Structured JavaScript state kept in saves

*Kind:* JavaScript hook and builder tool · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** JS values are lost between runs. setVar stores only strings and upper-cases names. A workaround exists: setVar('WORLD','STATE',JSON.stringify(obj)), read back with JSON.parse(getVar(...)). This Rhino has JSON, and global vars are written to the save's \<SCRIPTVARS>. But every value must be packed by hand. There is no stable key per host and no starting state for a new game.

**Add.** `cm.store(key)` returns a plain JS object that is saved with the game. A key can be 'world', a host (keyed by room ID, or by a mob's start room plus its name), 'quest:\<name>' or any string. On SAVE, stores are written as JSON, in a \<JSSTATE> section of world.xml or over the global vars. On restore they come back after WorldState.apply and before onRestore. A new game starts from world/js/state.json if it exists. `JS STATE [key]` in builder mode shows and edits a store.

**Example.** A blacksmith keeps `cm.store(me)` = {orders:[{who:'Ann', item:'longsword', readyDay:12}], served:7}. After SAVE and RESTORE, the sword is still due on day 12.

*See also:* SC4 (Two saved per-object state stores: SC4's @-host variables in ExtraData and JS7's cm.store keyed by host); JS18, JS19 (JS18's quest state and JS19's pending timers are saved through JS7's cm.store and JSSTATE section).

<!-- from: javascript/js-persistent-state -->

<a id="js8"></a>

## JS8. World-wide JavaScript events with cm.on

*Kind:* JavaScript hook · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** A script hears only the messages that reach its host. CMMap broadcasts DEATH, LEVEL, LOGIN, QUIT, WINQUEST, ENDQUEST, LEGALWARRANT, LEGALSTATE and CHANNEL world-wide, but Scriptable listens only through LOGIN_PROG, LEVEL_PROG and CHANNEL_PROG. Workarounds: ScriptableEverymob on an area gives every native mob a script, an area hears its rooms' messages, and achievements.ini counts kills and exploration. A JavaAdapter MsgListener can be added with addGlobalHandler, but the handler is held weakly and is gone after a reboot.

**Add.** `cm.on(name, fn)` and `cm.off(name, fn)`, registered from init.js or libraries and set up again at each boot. Events: death(victim, killer), levelup, questwin, questend, enterRoom(mob, room, from), enterArea, itemGained, itemLost, factionChange(mob, faction, delta), turn() (each step of turn-based time), hour, day, weather(area, kind), save and restore. 'Before' variants such as beforeDeath and beforeEnterRoom can return false to veto. enterRoom, itemGained, factionChange and turn are not broadcast today. They need new sendGlobalMessage calls or a hook in the turn scheduler.

**Example.** A death handler counts orcs the party kills in cm.store('world').orcKills. At 50, it loads the Orc Warlord at Fort#1.

*See also:* ST12 (Both expose the engine's world-wide broadcasts (DEATH, LEVEL, WINQUEST, LEGALWARRANT and so on) to scripts, ST12 in MOBPROG and JS8 in JavaScript); ST4, ST11 (ST4's dawn, dusk and date-change messages and ST11's calendar triggers hook the same clock transitions, which JS8's hour, day and weather events also need); ST14, F6 (All three need DefaultFaction to report a change and a band crossing beyond the source's room (FACTION_PROG, live band effects, a JS factionChange event)); ST6 (JS8's save, restore and turn() events repeat ST6's lifecycle and turn events (with JS4 (now in ST6) and ST23 (now in ST6) merged into ST6)).

<!-- from: javascript/js-event-bus -->

<a id="js9"></a>

## JS9. Effects whose stat changes are written in JavaScript

*Kind:* property and behaviour · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** MOBPROG cannot change computed stats: there is no affectPhyStats or affectCharStats trigger. GenAbility has a SCRIPT field (okMessage, executeMsg, tick), and HERESTATS and MOCKABILITY give stat changes that stay fixed while the effect lasts (HERESTATS may use caster and target level variables, worked out once when cast). Prop_*Adjuster gives fixed changes gated by a mask. MPSET changes base stats permanently, so a kill-counting sword can be built today by MPSETting its DAMAGE. A JS class extending Ability can compute stats, but it needs an ini path or LOAD, one class per effect, and parameters parsed by hand.

**Add.** A property `Prop_JScript` (for mobs, items, rooms, exits and areas) and a behaviour `JSBehavior`. The behaviour gets only okMessage, executeMsg and tick: behaviours are never asked for stat changes, so the stat hooks need the property. Alternatively, the same as a JS-file field on GenAbility. Parameters: `SCRIPT=world:js/props/bloodblade.js KILLS=0`. The file may define affectPhyStats(host, affected, stats), affectCharStats, affectCharState, okMessage, executeMsg, tick, onAdded and onRemoved. Parameters arrive as `parms`, and changes to them are written back to the property's text, so they are saved with the host. Each file is compiled once and shared.

**Example.** "Bloodblade" counts its wielder's kills in parms.KILLS and adds 1 damage per 10 kills. The count is kept in the sword's own text, so it survives saves.

*Re-checked against the code after writing: corrected.*

*See also:* SC13, N7, P1 (Four ways to give a shared script or effect per-instance parameters (SC13's ARG(), N7's PARM(), P1's EFFECTPARM(), JS9's parms); decide them together); I24 (Both make items that grow with kills: JS9's bloodblade example in JavaScript, I24 as a property).

<!-- from: javascript/js-prop-jscript -->

<a id="js10"></a>

## JS10. JS RELOAD: update changed JavaScript in the running world

*Kind:* builder tool · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** LOAD \<TYPE> file.js (sysop) recompiles a JS class and replaces its template, so new instances use the new code. Mobs, items and behaviours already in the world keep the old class. Load unloads the old class first only for .class names. Scripts can mostly be reloaded already: UNLOAD RESOURCE PARSEDPRG, UNLOAD FILE and MPUNLOADSCRIPT drop cached MOBPROG files, so edited LOAD= files and their `<SCRIPT>` blocks are read again on the next trigger.

**Add.** `JS RELOAD [file|ALL]` in builder mode. It finds changed world/js files by modification time and recompiles them. Each live instance is replaced with a new one: its text() is copied, it is attached again to its host, and onReload(oldState) is called if defined. It also clears the require cache, rebuilds JScriptable scopes, and reports compile errors as file:line. `JS WATCH ON|OFF` checks file times every few seconds and reloads automatically.

**Example.** The builder edits the ferryman's dialog in a text editor, types `js reload` and talks to him again. onReload carries over his half-finished crossing.

*See also:* B5 (Both reload changed files in the running world and add an automatic file watch (JS WATCH, DEVWATCH)).

<!-- from: javascript/js-hot-reload -->

<a id="js11"></a>

## JS11. JScriptable: a Scriptable written wholly in JavaScript

*Kind:* behaviour · *Value:* high · *Effort:* large · *Decision:* yes / no

**Today.** JS can live only inside MOBPROG triggers. Each `<SCRIPT>` block starts with a fresh scope and its result is discarded. CALLFUNC finds only FUNCTION_PROG. A JS class extending StdBehavior can veto in okMessage, but must decode CMMsg codes by hand. The unused JavaScriptingEngine (one scope per script, callFunc) is broken: okMessage always returns true, I2 is taken from msg.tool() instead of msg.target(), and getMakeMOB returns null for hosts that are not mobs. Scriptable.engine() and CoffeeMaker.unpackGenScriptsXML hard-code DefaultScriptingEngine.

**Add.** Finish JavaScriptingEngine and expose it as a behaviour `JScriptable` for mobs, items, rooms, exits and areas. It takes inline JS or LOAD=world:js/npc/ferryman.js, plus a scope (GLOBAL, INDIVIDUAL or a name). It calls only the handlers the script defines: onLoad, onGreet, onArrive, onLeave, onSpeech(who, text), onGive, onGet, onDrop, onWear, onRemove, onOpen, onClose, onLock, onUnlock, onDeath, onKill, onDamage, onCast, onLook, onTick, onHour and onDay. onCommand(who, verb, rest) returns true to handle an unknown command. canMessage(msg) returns false to veto, and afterMessage(msg) runs after the message. MPCALLFUNC and CALLFUNC can call its functions. Scripts use `cm`, the time limit and cm.store. The engine choice is saved in the \<SCRPT> XML.

**Example.** When a player says "ferry", the ferryman's onSpeech asks for five coins and sets state.crossing. Then onTick moves the boat.

*See also:* JS18 (JS11 finishes JavaScriptingEngine as JScriptable, while JS18 proposes dropping it or making it selectable; decide its fate once).

<!-- from: javascript/js-jscriptable -->

<a id="js12"></a>

## JS12. JCONSOLE as a given mob, item or room

*Kind:* debug tool · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** JCONSOLE keeps a scope per builder, which is lost on reboot. It takes multi-line input between '<' and '>' and can call every MOBPROG command and function. Every call runs as the builder: host, monster and source are all the builder, with no target or message. Objects print as raw toString text. To test a mob's script, the builder must act it out: say the phrase or give the item.

**Add.** `JCONSOLE @<mob|item|here|area>` binds host() and monster() to the target and source() to the builder, and sets `me`, `here` and `cm`. Dot commands: `.trigger GREET|SPEECH text|GIVE item` fires that handler on the target's JScriptable or Scriptable. `.load file.js` loads a file and `.reset` clears the scope. `.inspect expr` prints name, class ID, location and getStatCodes values. `.time on` shows run time. History is kept across sessions in builder mode.

**Example.** After `jconsole @blacksmith`, `.trigger SPEECH repair my sword` shows exactly what the smith answers, and `.inspect cm.store(me)` shows his orders.

*See also:* DT7 (Both fire a chosen trigger on a chosen host on demand (JCONSOLE .trigger, SCRIPT FIRE)).

<!-- from: javascript/js-console-attach -->

<a id="js13"></a>

## JS13. Seeded randomness and game time in JavaScript

*Kind:* JavaScript hook · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** The fork rolls all the game's dice from one seeded generator and keeps time with GameTime, so RANDOMSEED and turn-based time are repeatable. From `<SCRIPT>`, RANDNUM$, RAND0NUM$ and RAND use that generator. ISHOUR, ISDAY, ISSEASON and DATETIME$ read the game clock. But plain JS Math.random and Date use the JDK, and cron JS counts real milliseconds. Ordinary JS therefore breaks seeded replays and ignores pauses and turns.

**Add.** In every JS scope, back Math.random with the shared dice generator, so the save's dice state covers it. Add cm.now(), cm.calendar() (hour, day, month, season) and cm.ticks() on the game clock. Optionally, make Date.now() and new Date() read GameTime, with cm.realNow() for real time. That part is riskier, because some code may expect real dates.

**Example.** A card-playing NPC written in JS deals the same hands under RANDOMSEED=42. A "come back in 3 hours" check counts game hours, which do not pass while the game is paused.

*See also:* DT39 (Exact replay needs JS13's Math.random on the shared dice as well as DT39's deterministic scheduler).

<!-- from: javascript/js-seeded-random -->

<a id="js14"></a>

## JS14. +JS and -JS entries in zapper masks

*Kind:* script function · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** Zapper masks gate catalog spawn rules, Prop_Req* checks, ability components and shop masks. Their scripted test is +IF/-IF (resources/help/zappermasks.txt). It takes one quoted MOBPROG condition, compiled once per mask, with the tested object as host. Global vars are visible, so `+IF "var(WORLD BELLRUNG == 1)"` can already gate on world state set by setVar from JS. +IF lacks structured data and the JS language.

**Add.** Mask entries `+JS "expr"` and `-JS "expr"`. The expression sees `mob`, `target`, `item`, `room` and `cm`, cm.store included. It is compiled once per mask and runs under the time limit. An error counts as no match. It can reuse the compiled-entry slot that +IF uses. It is mainly a convenience over +IF.

**Example.** Prop_ReqEntry on a crypt door has `+JS "cm.store('world').bellRung && cm.calendar().hour < 4"`. Alongside DEVPLAN item 1, a catalog spawn rule with `+JS "cm.store('world').dragonDead"` makes the dragonscale chest loot appear only after the dragon dies.

<!-- from: javascript/js-zapper-mask -->

<a id="js15"></a>

## JS15. JS() in conditions, MPJSFILE, and cancel from \<SCRIPT>

*Kind:* script function and script command · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** To use one JS test in an IF, a builder needs a whole `<SCRIPT>` block that sets a var, then `if var(...)`, because the block's value is discarded. A one-line `<SCRIPT>...</SCRIPT>` already works anywhere in a line, so an MPJS command is not needed. JS cannot veto a message. In CNCLMSG_PROG it can change the message in objs()[0], but a bridged RETURN('cancel') ends only its own sub-execute. Running a JS file needs the eval-of-CMFile workaround.

**Add.** A function `JS("expr")`, true when the result is truthy, for use in IF. A string form `JS$`, for `$%JS$(...)%` substitution in text. A command `MPJSFILE world:js/x.js args`. Inside `<SCRIPT>`, `cancel()`, or a final value of "cancel", makes the enclosing prog return cancel, so CNCLMSG_PROG can veto from JS.

**Example.** `cnclmsg_prog GET p idol` with `<SCRIPT> if (!cm.store('world').trapDisarmed) { MPECHO('A dart flies!'); cancel() } </SCRIPT>`. The player cannot take the idol until the trap is disarmed.

*See also:* SC5, JS17 (Message access and veto from scripts: SC5 in MOBPROG, JS17's msg() and cancel(), JS15's cancel(); JS15 and JS17 both add cancel()).

<!-- from: javascript/mobprog-js-inline -->

<a id="js16"></a>

## JS16. A single-player JavaScript guide and JS HELP

*Kind:* presentation and debug tool · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** The JS documentation is scattered and partly out of date. Scriptable.html (around line 4610) covers `<SCRIPT>`, the MOBPROG bridge and getVar/setVar. It still says semicolons must be escaped, which is no longer true inside `<SCRIPT>`. Programming.html covers JS classes next to material for the removed web server. arc_help.ini covers JCONSOLE, JRUN and MPRUN. Nothing lists what each context provides, and CMDFAIL_PROG and +IF masks are barely documented. In JCONSOLE, `for (k in obj)` lists a Java object's members, but only roughly.

**Add.** A guides/JavaScript.html covering: each context and what it exposes (a table), the `cm` API, event and handler names, state and save rules, require and the world/js layout, error and time-limit behaviour, and today's tricks (the MOBPROG bridge, JSON in global vars, one-line `<SCRIPT>`). It ends with worked examples: a shopkeeper, a ferryman, a cursed sword and a quest. Also add `JS HELP <expr>`, which lists the methods of the object an expression returns, with its STAT codes and values. The guide is worth writing whichever other items are approved.

**Example.** `js help cm.room('Town#1')` lists getArea(), numInhabitants() and fetchInhabitant(i), and stat codes such as DISPLAY, DESCRIPTION and CLIMATE.

<!-- from: javascript/js-guide -->

<a id="js17"></a>

## JS17. Message, cancel and lookup helpers for JavaScript in scripts

*Kind:* JavaScript hook · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** JavaScript in a trigger's `<SCRIPT>` block gets host, source, target, item, item2, the message text, objs and getVar/setVar. Every MOBPROG command and function can be called as an uppercase function (`MPOPEN('$xN')`, `INAREA$()`), and `Packages.com.planet_ink.coffee_mud.core.CMLib` reaches the engine. In `CNCLMSG_PROG` the CMMsg is `objs()[0]`. `EXECMSG_PROG` and the other queued triggers get no message. JS cannot cancel: it sets a variable, then MOBPROG runs `RETURN CANCEL` after the block. Common lookups need long calls, and none of the helpers are documented.

**Add.** In JScriptEvent: `msg()` (the CMMsg in every message trigger, null elsewhere), `cancel()`, `room(id)`, `findItem(where, name)`, `door(roomId, dir, op)` over MPDOOR, `signal(...)` and `state(...)`. Document them in `guides/Scriptable.html`.

**Example.** A boulder's `CNCLMSG_PROG` reads `msg().value()` to get the push direction and calls `cancel()` unless the way is downhill.

*See also:* SC5, JS15 (Message access and veto from scripts: SC5 in MOBPROG, JS17's msg() and cancel(), JS15's cancel(); JS15 and JS17 both add cancel()); SC4, SC6, I2 (JS17's state(), signal() and door() helpers wrap SC4's per-object state, SC6's signals and I2's MPDOOR).

<!-- from: items-puzzles/ip-js-msg -->

<a id="js18"></a>

## JS18. JavaScript quest handlers and a quests helper

*Kind:* JavaScript hook, quest feature · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** A quest's `<SCRIPT>` runs once, at setup, with `quest()` and `setupState()` and no MOBPROG bridge. Steps and wins are scripted through what the quest gives (`GIVE SCRIPT`): `QUEST_TIME_PROG`, `MPSTEPQUEST`, `MPQUESTWIN`, `QVAR`, and the `FUNCTION_PROG` hooks `CAN_ACCEPT`, `DO_ACCEPT` and `DO_DROP`. JS reaches quests only through `Packages...CMLib.quests()`. `WINQUEST` is sent only by `MPQUESTWIN`. JavaScriptingEngine is dormant: nothing creates it, its quest calls do nothing, and its `isFunc` ignores the name it is given.

**Add.** A quest file names `JSFILE=world:js/quests/<name>.js` or holds a `<JSQUEST>` block. `onStart(state)`, `onStep(n, state)`, `onTimeUp(state)`, `onWin(player)` and `onStop()` are called if defined. Scripts on quest objects may define `onQuestAccept(quest, mob)`, `onQuestStep(quest, step, label)`, `onQuestWin(quest, mob, outcome)`, `onQuestFail(quest, mob, reason)` and `onQuestEnd(quest, reason)`. Quest JS gets the bridge. Its state lives in `cm.store('quest:'+name)` and is saved with the game. A `quests` helper gives start, step, win, fail, stop, running, stepNumber, status, objective, journal and getVar/setVar. JavaScriptingEngine is either dropped, or made selectable (`MPSCRIPT ENGINE=JS`) with `isFunc` fixed.

**Example.** In "Missing Cat", `onStart` picks a random alley from `setupState().roomGroup` and stores it. `onWin` pays 50 gold, less 5 for each game hour taken, counted from the stored start time.

*See also:* ST5, Q4, Q11 (Quest outcome hooks overlap: ST5's QUEST_PROG FAIL and END EXPIRED, Q4's QUESTFAIL_PROG, Q11's QUESTTIMEOUT_PROG, and JS18's onQuestFail and onQuestEnd in JavaScript); JS7, JS19 (JS18's quest state and JS19's pending timers are saved through JS7's cm.store and JSSTATE section); JS11 (JS11 finishes JavaScriptingEngine as JScriptable, while JS18 proposes dropping it or making it selectable; decide its fate once).

<!-- from: javascript/js-quest-api, quests/qst-js -->

<a id="js19"></a>

## JS19. Game-clock scheduling from JavaScript

*Kind:* JavaScript hook · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** MOBPROG schedules on the game clock with `TIME_PROG`, `DAY_PROG`, `DELAY_PROG`, `RAND_PROG`, `MPALARM <ticks> <command>` and ScriptLater. Quests start on `SET MUDDAY` and take `WAIT`, `INTERVAL` and `DURATION` in mudhours or muddays. They live in `world/quests`, so a market day can be a quest today. CRON runs on real time and is kept in the journal table, which is save state: a job made in builder mode never reaches a new game. JS has no scheduler of its own. A `<SCRIPT>` can call `MPALARM('10 MPCALLFUNC market')` through the MOBPROG bridge. That runs MOBPROG text, not a JS function, and pending alarms are not saved. A CRON job can hold a `<SCRIPT>` block, but it runs on real time.

**Add.** `cm.every('1 day'|'6 hours'|ticks, fn)`, `cm.at('hour 6'|'day 1'|'season winter', fn)` and `cm.after(ticks, fn, data)`. All run on GameTime, built on the tick `TIME_PROG` uses, so they wait while the game is paused and step with turns. Recurring ones are registered from `init.js` (JS4 (now in ST6)) on each boot. Pending one-shot timers and their data go into the save's JSSTATE (JS7) and come back on restore. `cm` comes from JS6.

**Example.** `cm.at('day 7 hour 8', function(){ fillMarket(); cm.echo(cm.room('Town#Square'), 'Traders set up their stalls.'); })` makes a market day on the 7th of each month. As in DAY_PROG, 'day' is the day of the month, and the shipped calendar has no weeks (DAYSINWEEK= is empty).

*Re-checked against the code after writing: corrected.*

*See also:* ST24, S21 (Three ways to run something on a game-clock interval that survives saves; ST24 also suggests moving cron to GameTime, which is S21); JS7, JS18 (JS18's quest state and JS19's pending timers are saved through JS7's cm.store and JSSTATE section).

<!-- from: javascript/js-gametime-schedule -->

<a id="js20"></a>

## JS20. Commands and local verbs from JavaScript

*Kind:* JavaScript hook · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** `CREATE COMMAND` makes a GenCommand with HELP, ACCESS (its words), SCRIPT, ORDEROK, SECMASK, ACTCOST and CBTCOST. Its `FUNCTION_PROG EXECUTE` may be a `<SCRIPT>`, is pre-approved, gets the arguments in `objs()`, and is saved in `world/custom.cmare`. A verb for one room uses `CNCLMSG_PROG HUH p <phrase>` (documented) or `CMDFAIL_PROG <verb> [P phrase|words]` (undocumented), where `return cancel` swallows the failure. There is no short way to do either from JS, and no ready fallback message.

**Add.** `cm.command('dig', {help:'DIG [direction]', cost:1, combatCost:2}, function(mob, args){...})`, in `init.js` or a library, makes a GenCommand behind the scenes (or a JS command class), kept with the world. `cm.verb(roomOrItemOrMob, 'pull lever', function(mob, rest){...})` makes a phrase work only near that object. It handles the matching and gives a "You can't do that here" fallback. Document `CMDFAIL_PROG` in `guides/Scriptable.html` first. That alone may be enough.

**Example.** `cm.verb(cm.room('Mine#7'), 'pull lever', function(m){ cm.echo(m.location(), 'The cart rattles away.'); cm.store('world').cartAtBottom=true; })`

*See also:* ST7 (JS20's cm.verb is a JavaScript form of ST7's VERB_PROG local verbs); SC9, SC15 (SC9 and SC15 both rewrite guides/Scriptable.html with single-player notes and a CMDFAIL_PROG section, which JS20 says may be enough on its own).

<!-- from: javascript/js-commands-verbs -->

<a id="js21"></a>

## JS21. Upgrade Rhino and fix the quote swap

*Kind:* JavaScript runtime upgrade · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** By its manifest, `lib/js.jar` is Mozilla Rhino 1.7R2, built 2011-05-09. It has commonjs and NativeJSON, so it is a late 1.7R3-era build. Scripts get no arrow functions, no template strings, no `let`/`const` by default, and no `Array.find`/`includes` or `Object.assign`. The game runs on Java 21, which current Rhino supports (1.7.14 and later need Java 11). Separately, `setScript` turns every `'` into a backtick and `getScripts` turns it back (DefaultScriptingEngine, also for `STATIC=`). JConsole input and cron do the same. A literal backtick in an inline script comes back as a quote.

**Add.** Replace `lib/js.jar` with Rhino 1.7.15 or later, set language version ES6 in the shared JS runtime, and keep `RHINO.LICENSE`. Recheck the `//extends` class compiler (ClassCompiler, in CMClass) and the JConsole and JRUN scopes. Keep `<SCRIPT>` bodies out of the quote swap, or swap through an escape that cannot clash. This matters only once template literals work.

**Example.** ``cm.party().filter(m => m.curState().getHitPoints() < 10).forEach(m => cm.tell(m, `You feel weak, ${m.name()}.`))``

<!-- from: javascript/rhino-upgrade -->

<a id="js23"></a>

## JS23. A world JavaScript file for progression

*Kind:* JavaScript hook · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** No single world file sees progression events and returns a result, though most hooks exist as scripts. `LEVEL_PROG` is global. `CHARCREATIONSCRIPTS` (RACE, GENDER, STATS, CLASS, FACTIONS, END) may hold `<SCRIPT>` JS. A script on a parent area (`DEFAULTPARENTAREA`) hears every EXPCHANGE, TEACH, PRACTICE and TRAIN message, and can change XP with `objs()[0].setValue()`. XPMOD and Prop_ModExperience scale XP by type. Practice and train counts can only be adjusted after the fact (`MPPRACS`, `MPTRAINS`), and `STARTSTAT` has no script mode.

**Add.** `world/scripts/progression.js`, with each function called if defined: `onCreated(mob)`, `onCreationStep(mob, step, answer)`, `xpGain(mob, source, amount)` (returns the amount), `onLevel(mob, level)`, `practicesForLevel(mob)` and `trainsForLevel(mob)` (each returns a number and overrides the progression-formulas item), `onLearn(mob, abilityId)`, `onTrain(mob, what, amount)`, and `statRoll(mob)` for a new `STARTSTAT=SCRIPT`. It runs in the usual JS scope. It complements the proposed SCRIPT field on GenRace and GenCharClass.

**Example.** `xpGain` returns 0 for `COMBAT:*` when the character bears a pacifist monk's tattoo, and `amount*1.5` for `DISCOVERY:*`. `trainsForLevel` gives 2 at every fifth level.

*See also:* P17 (JS23's practicesForLevel and trainsForLevel override P17's per-level formulas; they are alternatives); P21 (JS23 says it complements P21's SCRIPT field on GenRace and GenCharClass).

<!-- from: character-progression/cp-js-progression-hooks -->
