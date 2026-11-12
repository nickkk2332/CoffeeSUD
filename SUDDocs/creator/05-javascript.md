# JavaScript

[Handbook](README.md) · [Full API manual](../../CoffeeMud/guides/JavaScriptSUD.html)
· [JS feature index](feature-index.md#js)

JavaScript is CoffeeSUD's second scripting layer, beside the MOBPROG language
of [chapter 4](04-mobprog.md). It runs in Mozilla Rhino inside the game
process and gets live Java engine objects (mobs, rooms, items, messages,
libraries) through a helper object named `cm`. This chapter is for world
creators who script (a trap, a ferryman, a market day), for builders who use
JavaScript to build or test a world (`JRUN`, `JCONSOLE`), and for engine
developers who want a JavaScript class instead of a Java one. It covers every
place JavaScript can run, the complete `cm` and `sud` API, saved state,
events, timers, new commands, JavaScript classes, reloading, debugging and
limits, and ends with worked examples.

## In this chapter

- [Choose an extension layer](#choose-an-extension-layer)
- [The runtime](#the-runtime)
- [Where JavaScript runs](#where-javascript-runs)
- [Approval, limits and settings](#approval-limits-and-settings)
- [Getting started](#getting-started)
- [The shared API](#the-shared-api)
- [The cm object](#the-cm-object)
- [Objects that cm returns](#objects-that-cm-returns)
- [sud, gen and the other globals](#sud-gen-and-the-other-globals)
- [Files and libraries](#files-and-libraries)
- [Durable state, code and timers](#durable-state-code-and-timers)
- [Events: cm.on](#events-cmon)
- [Timers](#timers)
- [New commands, local verbs and effects](#new-commands-local-verbs-and-effects)
- [World lifecycle hooks: init.js](#world-lifecycle-hooks-initjs)
- [JavaScript inside MOBPROG](#javascript-inside-mobprog)
- [Prop_JScript and JSBehavior](#prop_jscript-and-jsbehavior)
- [JScriptable](#jscriptable)
- [Quests](#quests)
- [Character progression: progression.js](#character-progression-progressionjs)
- [Zapper masks](#zapper-masks)
- [JavaScript classes](#javascript-classes)
- [Other places JavaScript runs](#other-places-javascript-runs)
- [Reload and debugging](#reload-and-debugging)
- [Blocking work and execution limits](#blocking-work-and-execution-limits)
- [Java interop](#java-interop)
- [Native development](#native-development)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting](#troubleshooting)
- [See also](#see-also)

## Choose an extension layer

| Layer | Best fit | Persistence contract |
| --- | --- | --- |
| `<SCRIPT>` inside MOBPROG / `MPJSFILE` / `JS()` | Short calculations and engine calls inside an existing trigger | Explicit `cm.store` or script variables |
| `JScriptable` behavior | One mob, item, room, exit or area scripted wholly in JavaScript | `state` (the host's `cm.store`); everything else is code |
| `Prop_JScript` / `JSBehavior` | A reusable effect or behavior in a file, with per-instance parameters and stat hooks | Changes to `parms` are written back to the host's text |
| `world/js/init.js` | World lifecycle, global events, commands and timers | Re-register code at boot; keep data in stores |
| `world/js/[kind]/` class | A new engine subtype (behavior, property, command, mob...) with overrides | Declare STATE/ACTIVITY where the class has state to keep |
| `world/scripts/progression.js` | How characters are created, gain XP and level | Stateless hooks that return numbers |
| Native Java | New engine interfaces, infrastructure, performance-sensitive logic | Implement the engine's save and state hooks |

Rule of thumb: if a trigger and a few MOBPROG commands do it, use MOBPROG.
Reach for JavaScript when you need real data structures, loops, saved
structured state, world-wide events, new commands or stat hooks.

## The runtime

- **Engine:** Mozilla Rhino 1.7.15.1 (the bundled `CoffeeMud/lib/js.jar`).
  Rhino is a JavaScript interpreter on the JVM, not Node and not a browser.
  There is no `window`, DOM, `fetch`, `setTimeout`, `process`, npm or Node
  filesystem; use `cm.every`/`cm.after` for time and `require()` for
  libraries.
- **Language level:** ES6 (`Context.VERSION_ES6`). Verified by the test
  suite: `let`, `const`, arrow functions, template literals, `Array.includes`,
  `Array.find` and `Object.assign`. Rhino 1.7.x covers only part of ES6; test
  any other modern feature (for example `class`) before relying on it.
- **Interpreted mode:** the game sets Rhino to interpreter mode with an
  instruction observer so that a runaway loop can be stopped (see
  [limits](#blocking-work-and-execution-limits)).
- **Dice:** `Math.random()` is replaced by the game's own random generator.
  A game started with `RANDOMSEED=` rolls the same numbers and a save keeps
  the generator's place. `Date` is still real time; use `cm.now()` for game
  time.
- **Trusted code:** scripts are world code written by the world's author.
  Rhino is not a security sandbox here; scripts can call any Java the game
  process can (see [Java interop](#java-interop)).
- **Strings:** a Java `String` returned by the engine is not a JavaScript
  string. `me.Name().length` is a method call, not a property. Write
  `('' + me.Name()).length`, and compare with `==` (not `===`) or convert
  first.

Source: [JSRuntime](../../CoffeeMud/com/planet_ink/coffee_mud/core/JSRuntime.java),
[JSScope](../../CoffeeMud/com/planet_ink/coffee_mud/core/JSScope.java).

## Where JavaScript runs

Every entry point below gets the same base set of globals (`cm`, `sud`,
`require`, `include`, `gen`, `debug`, `importPackage`, `importClass`, see
[The shared API](#the-shared-api)). The last column lists what each adds.

| Entry point | Use | Time limit | Adds to the scope |
| --- | --- | --- | --- |
| `<SCRIPT>` block in a MOBPROG | A few lines inside a trigger | `JSTIMELIMIT` | `host()`, `source()`, `target()`, `monster()`, `item()`, `item1()`, `item2()`, `message()`, `msg()`, `objs()`, `getVar`, `setVar`, `cancel()`, `room(id)`, `findItem(where,name)`, `door(roomId,dir,op)`, `transform(...)`, `signal(words)`, `state(thing,name[,value])`, `parm(name)`, `toJavaString`, `getCMType`, and every MOBPROG command/function as an uppercase JS function |
| `JS(expr)` / `JS$(expr)` in a condition or value | A one-expression test | `JSTIMELIMIT` | Same as `<SCRIPT>` (one scope kept per scripting engine) |
| `MPJSFILE file args` | Run a file as a `<SCRIPT>` | `JSTIMELIMIT` | Same as `<SCRIPT>` plus `args` (the rest of the line) |
| `JScriptable` behavior | An object scripted wholly in JS | `JSTIMELIMIT` | `me`, `state`, `CMMsg`, handler functions |
| `Prop_JScript`, `JSBehavior` | Stat hooks and message hooks in a file | `JSTIMELIMIT` | `parms`, `CMMsg`, hook functions |
| `world/js/init.js` | Boot code and world lifecycle hooks | `JSTIMELIMIT` | `onBoot()` and the other lifecycle functions |
| `world/js/[kind]/*.js` class | A new engine class | none (compiled to Java) | `this` is the Java object |
| `world/scripts/progression.js` | Character creation and growth hooks | `JSTIMELIMIT` | the hook functions |
| Quest `JSFILE` / `<JSQUEST>` | Quest handlers | `JSTIMELIMIT` | `quest`, `quests`, `state` |
| Quest `<SCRIPT>` (legacy quest-script block) | Quest setup | `JSTIMELIMIT` | `quest()`, `setupState()`, `toJavaString`, `getCMType` |
| Zapper mask `+JS`/`-JS` | A custom test in a mask | `JSTIMELIMIT` | `mob`, `item`, `room`, `target` |
| `cm.on`, `cm.every`, `cm.at`, `cm.after`, `cm.command`, `cm.verb` callbacks | Event and timer code | `JSTIMELIMIT` | the scope where the function was created |
| `JRUN file` | Run a file now (builder scripts, tests) | `JSTIMELIMIT_JRUN` | `mob()`, `numParms()`, `getParm(i)`, `getParms()`, `builder`, `toJavaString`, `getCMType`, `debug` |
| `JCONSOLE` | Interactive prompt | `JSTIMELIMIT` | `mob()`, `here`, `area`, `target`, `me`, MOBPROG bridge |
| Random-area templates | `${js:expr}`, `js:` conditions | see [templates](#other-places-javascript-runs) | `defined`, `obj`, `test(condition)` |
| Language `TRANSLATESCRIPT` | Custom translation | `JSTIMELIMIT` | `translate`, `garble` functions |
| Cron job `<SCRIPT>` | A timed job | `JSTIMELIMIT` | see [other places](#other-places-javascript-runs) |

## Approval, limits and settings

### JSCRIPTS

`JSCRIPTS` in [`coffeemud.ini`](../../CoffeeMud/coffeemud.ini) controls whether
JavaScript embedded in scripts may run. Shipped value: `JSCRIPTS=2`.

| Value | Meaning |
| --- | --- |
| `0` | Embedded JavaScript is never run: `<SCRIPT>` blocks, `JS()`, `MPJSFILE`, quest `<SCRIPT>` blocks, language `TRANSLATESCRIPT` and template `${js:}` are all off. |
| `1` | Each script body must be approved first, template `${js:}` expressions included. An unapproved `<SCRIPT>` is refused (the room is told "A Javascript was not authorized. Contact an Admin to use MODIFY JSCRIPT to authorize this script.") and is recorded as waiting. |
| `2` | (default) Always run. CoffeeSUD ships this because the player writes the world. |

The value applies to the JavaScript embedded in MOBPROG and the other
places listed above. It does **not** gate `JScriptable`, `Prop_JScript`,
`JSBehavior`, `world/js/init.js`, JavaScript classes, `progression.js`, quest
`JSFILE`/`<JSQUEST>` handlers, `JRUN` or `JCONSOLE`: those run whatever the
setting. (Code check: only `DefaultScriptingEngine`, `DefaultQuest`'s legacy
`<SCRIPT>`, `ScriptCheck`, `LanguageLexicon` and `GenJs` consult
`JSCRIPTS`.) The scripts of `GenCommand`, `GenAbility` and `GenTrap` never
need approval.

With `JSCRIPTS=1` approvals work like this:

1. A script body is hashed. An unseen body is stored as pending and refused.
2. In builder mode (`BUILDER=1`), run `MODIFY JSCRIPT` (needs the `JSCRIPTS`
   security flag). It shows each pending body and asks "Approve this script
   (Y/n)?". Answering yes records the approval; anything else drops it.
3. In CoffeeSUD, approvals made in builder mode are written to
   `world/jscripts.ini` (lines `hash=approver`), so every game and a fresh
   checkout shares them. Outside builder mode the file is read but not
   written.
4. Editing the body changes its hash, so it must be approved again.

`MODIFY JSCRIPT` prints "This command is only used when your Scriptable
Javascripts require approval" unless `JSCRIPTS=1`.
`WORLD CHECK` also reports unapproved language translation scripts.

### Time limits and error settings

| Key (coffeemud.ini) | Default | Meaning |
| --- | --- | --- |
| `JSCRIPTS` | `2` | See above. |
| `JSTIMELIMIT` | `2` | Most seconds a script may run at one time (script blocks, hooks, event and timer callbacks, quest and progression handlers). |
| `JSTIMELIMIT_JRUN` | `30` | The same for `JRUN` files. |
| `SCRIPTERRORS` | `LOG` | In a normal game: `LOG` writes errors to the log only, `NOTICE` tells each player once that a script went wrong, `SHOW` shows each error to the room it happened in. Builder mode and `DEV=1` games always tell the builder. |
| `STARTSTAT` | `0` (also a `world.ini` key) | `SCRIPT` makes new characters' stats come from `statRoll` in `progression.js`. |

For `JSTIMELIMIT` and `JSTIMELIMIT_JRUN` a value that is not a whole number falls back to
the default; the minimum is 1. Engine-level JS settings belong in `coffeemud.ini` or a
`NAME=VALUE` launch override, not in `world.ini` (see [world-settings](world-settings.md));
`STARTSTAT` is the exception, a `world.ini` key as well.

### Who may use the commands

`JRUN`, `JCONSOLE`, `JS`, and `MODIFY JSCRIPT` need the `JSCRIPTS` security
flag (`CMSecurity.SecFlag.JSCRIPTS`); the shipped `GROUP_DEV` list in
`coffeemud.ini` includes it. `JS STATE`, `JS RELOAD` and `JS WATCH` additionally require builder
mode or a `DEV=1` game. See [chapter 19](19-configuration-and-administration.md)
for security groups.

## Getting started

### Try an expression: JCONSOLE

```text
JCONSOLE
```

opens a prompt (`>`); `exit` leaves it. A one-line form needs no prompt:

```text
JCONSOLE cm.here().displayText()
```

Type `<` on a line by itself to start a multi-line block and `>` on a line by
itself to run it. The scope persists between lines (variables stay) until
`.reset`. Lines starting with a dot are console commands (see
[JCONSOLE reference](#jconsole-reference)). The scope has `here`, `area` and
`target` (the character you are fighting), refreshed each line, and `me`
(yourself).

### Run a file: JRUN

```text
JRUN resources/myscript.js arg1 arg2
JRUN /resources/creator/build.js
```

The file is read through the virtual file system (a path under `resources/`;
`world/resources/...` overlays it, as the shipped example does). Arguments are
available as `getParm(0)`, `getParm(1)`, `numParms()` and `getParms()`. The
`mob()` function is the person who ran it. JRUN scripts also get the
[`builder` helper](#the-builder-helper). A script that throws is reported
like any other error (shown immediately in builder mode).

### Put a few lines in a trigger: `<SCRIPT>`

```text
CNCLMSG_PROG GET ALL
<SCRIPT>
if (target().Name() == 'a golden idol' && !cm.store('world').trapDisarmed) {
  MPECHO('A dart flies!');
  cancel();
}
</SCRIPT>
~
```

### Give the world a startup file: init.js

Create `world/js/init.js` with an `onBoot` function (see
[World lifecycle hooks](#world-lifecycle-hooks-initjs)). Everything that is
code (listeners, commands, timers, verbs) belongs there, because code is not
saved.

### Where files live

| Path (relative to `CoffeeMud/`) | Contents |
| --- | --- |
| `world/js/init.js` | World boot file |
| `world/js/state.json` | Initial stores for a new game |
| `world/js/lib/*.js` | Shared `require()` libraries |
| `world/js/props/*.js` (any non-class folder) | `Prop_JScript`/`JSBehavior` files, `JScriptable` files, quest handlers, `MPJSFILE` files |
| `world/js/[kind]/*.js` | JavaScript classes; kinds: `behaviors`, `abilities`, `properties`, `items`, `mobs`, `races`, `charclasses`, `commands`, `common`, `locales`, `exits`, `areas` |
| `world/scripts/progression.js` | Progression hooks |
| `world/scripts/*.script` | MOBPROG files (`LOAD=world:scripts/x.script`) |
| `world/jscripts.ini` | Approved script hashes (`JSCRIPTS=1`, builder mode) |
| `resources/js/lib/*.js` | Engine-shipped libraries (none ship today) |
| `resources/js/templates/` | Commented class templates (`Property.js`, `WorldBehavior.js`) |
| `resources/examples/` | `CustomProperty.js`, `Lemming.js`, `QuickWho.js`, `FindRawMaterial.js` |

The `world:` prefix in a path means "under the world folder" (for example
`world:js/props/bloodblade.js`). A path without the prefix is under
`resources/`. A path containing `..` is refused.

All of `world/js` and `world/scripts` is part of the world checksum, so
changes show in `WORLD STATUS` and travel with the world.

## The shared API

Every JavaScript scope (the table in [Where JavaScript runs](#where-javascript-runs))
is built by one routine, `JSScope.prepare`, and so has these globals:

| Global | What it is |
| --- | --- |
| `cm` | The helper object: finding things, speaking, rolling, time, law, shops, banks, stores, events, timers, commands, verbs, background work. [Reference below](#the-cm-object). |
| `sud` | Short names over `cm` for time, party, save and variables. [Reference](#sud-gen-and-the-other-globals). |
| `gen` | Generation from a template file (random-area templates). |
| `require(name)` | Loads a CommonJS library from `world/js/lib/` or `resources/js/lib/`. |
| `include(path)` | Runs a file in the current scope. |
| `debug(x)` | Writes `x` to the developer log (`DEVLOG`) in builder mode and `DEV=1` games; nothing in a normal game. |
| `importPackage(...)`, `importClass(...)` | Rhino's package import, so a Java class can be named without its package. Scopes are Rhino `ImporterTopLevel` objects (the scope of a background worker is not). |
| `Packages.<java.package>` | Direct Java access, for example `Packages.com.planet_ink.coffee_mud.core.CMLib`. |
| `Math.random()` | The game's dice (seedable). |

A background worker (`cm.background`) has none of these; see
[Blocking work](#blocking-work-and-execution-limits).

`cm.host()` is the thing the script runs for: a `<SCRIPT>`'s host mob, item or
room; the `JScriptable`'s host; the character who typed `JRUN` or `JCONSOLE`;
`null` in `init.js`, libraries, quests and `progression.js`. Wherever a
parameter below says "or null for the host's", `null` means that host.
`cm.here()` is the host's room, falling back to the current player's room.

A helper that returns a mob, item or room returns the **live Java object**.
Its methods are the engine's (see [Java interop](#java-interop)); direct
changes may need `recoverPhyStats()`/`recoverCharStats()`, messages or save
calls. Prefer normal commands and library operations when you want the game's
checks to apply. `cm.cmd` is like `MPFORCE`: it takes the immediate
forced-command path.

Use `JS HELP cm` or `JS HELP expression` in game to list the methods of what
an expression gives (see [Reload and debugging](#reload-and-debugging)).

## The cm object

Source: [JSCm](../../CoffeeMud/com/planet_ink/coffee_mud/core/JSCm.java).
Methods that take a `where` accept a Room, an Area or (for items) a MOB; the
exact accepted types are in the Notes column.

### Finding things

| Member | Returns | Notes |
| --- | --- | --- |
| `cm.lib` | `CMLib` (a Java class object) | `cm.lib.map()`, `cm.lib.law()`, `cm.lib.beanCounter()`, `cm.lib.combat()`, ... any library. |
| `cm.host()` | the host or `null` | |
| `cm.here()` | Room | Host's room, else the player's. |
| `cm.room(id)` | Room or `null` | By room ID, `Area#12`. |
| `cm.area([name])` | Area or `null` | Named area, or the host's. |
| `cm.findMob(name[, where])` | MOB or `null` | `where` is a Room or an Area to search alone. With no `where`: the host's room, then its area, then the whole world (first 100 matches). |
| `cm.findItem(name[, where])` | Item or `null` | `where` is a Room, an Area or a MOB (inventory). Same default search order. |
| `cm.player()` | MOB or `null` | The character at the terminal (the one being played; party members are skipped). |
| `cm.party()` | List of MOB | The player's party, leader first; empty if no player. |

Names are matched the way the game matches names, so ambiguous names return
the first match.

### Making things

| Member | Returns | Notes |
| --- | --- | --- |
| `cm.loadMob(catalogName[, room])` | MOB or `null` | A copy of the catalog mob, brought to life in the room (default the host's). Catalog caps (SC3) apply. Start room is cleared. |
| `cm.loadItem(catalogName[, roomOrMob])` | Item or `null` | A copy of the catalog item, in the room or in the mob's inventory (default the host's room; room items get monster-equipment expiry). |

### Saying and doing

| Member | Effect |
| --- | --- |
| `cm.echo([room,] text)` | Shows text to a room (as `MPECHO`). |
| `cm.tell(mob, text)` | Tells one mob (as `MPECHOAT`). |
| `cm.say(mob, text)` | The mob says `text` (forced `SAY`). |
| `cm.emote(mob, text)` | The mob emotes (forced `EMOTE`). |
| `cm.cmd(mob, command)` | The mob does a command line, forced (as `MPFORCE`). Local verbs for queued player input are not exercised by this path. |
| `cm.mp(lines)` | Runs MOBPROG lines for the host with `$`-codes working. Lines are separated by newline or `;`. Returns what the lines `RETURN`, or `""`. So a line containing a semicolon cannot be sent this way. |
| `cm.log(text)` | Writes to the game log (`sysOut`, source "JS"). |

### Dice

| Member | Returns |
| --- | --- |
| `cm.dice('3d6+2')` | A roll (number). |
| `cm.dice(count, sides, plus)` | A roll. |
| `cm.chance(pct)` | `true` that percent of the time (rolls 1-100, true if the roll is at most `pct`). |

### Time

| Member | Returns / effect |
| --- | --- |
| `cm.now()` | Game time in game milliseconds; stands still while paused. |
| `cm.ticks()` | `cm.now()` in ticks. |
| `cm.realNow()` | Real time in milliseconds (also what `Date` gives). |
| `cm.clock([area])` | A [Clock](#clock) for the area (default the host's). |
| `cm.calendar()` | The engine's own `TimeClock` for the host's area. |
| `cm.passTime(hours[, area])` | The area's clock goes forward hour by hour, its events running (as `TIME SET`). |
| `cm.passHours(hours)` | The game's ticks pass for that many game hours as `MPPASSTIME` does; false if time is already passing. |
| `cm.interrupt(who, message)` | Ends a `WAIT`/`MPPASSTIME` or journey under way (as `MPINTERRUPT`); `who` null for any. Returns true if one ended. |
| `cm.advance(ticks)` | In turn-based time, goes on that many ticks (as `WAIT`); `false` and nothing in real time. |
| `cm.isTurnBased()` | True in a turn-based game. |
| `cm.weather([area])` | A [Weather](#weather) for the area, or `null`. |
| `cm.holiday(name)` | True while the named holiday (a quest by that name) is on. |
| `cm.holidays()` | Names of the holidays on now. |

### Mode and save

| Member | Returns |
| --- | --- |
| `cm.isBuilder()` | True in builder mode. |
| `cm.isDev()` | True in a `DEV=1` game. |
| `cm.saveSlot()` | The slot last saved to or restored from, or `""`. |

### Law, shops and banks

| Member | Returns |
| --- | --- |
| `cm.law([areaOrRoom])` | A [LawView](#lawview), or `null` where there is no law. |
| `cm.shop(keeper)` | A [Shop](#shop) for a shopkeeper, or `null`. |
| `cm.bank(banker)` | A [Bank](#bank) for a banker, or `null`. |

### Script variables and stats

| Member | Effect |
| --- | --- |
| `cm.var(holder, name)` / `cm.getVar(holder, name)` | Reads a script variable (as `$<host var>`); `""` if unset. |
| `cm.var(holder, name, value)` / `cm.setVar(...)` | Sets it (as `MPSETVAR`; `++ -- + - * /` forms work). Names are upper-cased on set. |
| `cm.vars(holder)` | The sorted variable names of a holder. |
| `cm.stat(thing, 'LEVEL')` | A stat as `STAT()` reads it (`""` if none). Works on mobs, items, rooms, areas. |

`holder` is a host's name, `'*'` (global variables), or an object (its own
variables, like `@` in MOBPROG). A Room is named by its extended ID.

### Stores

| Member | Effect |
| --- | --- |
| `cm.store([key])` | The saved store (a plain object). See [Durable state](#durable-state-code-and-timers). |
| `cm.storeKey(holder)` | The key a holder's store has. |
| `cm.stores()` | The keys of all stores. |

### Events and timers

| Member | Effect |
| --- | --- |
| `cm.on(event, fn[, key])` | Register a listener; returns its key. |
| `cm.off(key)` / `cm.off(event, fn)` | Remove listeners; returns how many. |
| `cm.listening([event])` | List of `"event key"` strings. |
| `cm.waitUntil(test[, then])` | A tick listener that ends once `test()` returns true; then `then()` runs once (also `sud.time.waitUntil`). |
| `cm.every(spec, fn[, key])` | Repeat on the game clock. |
| `cm.at(spec, fn[, key])` | Run when the calendar comes to a time. |
| `cm.after(ticks, fn[, data])` | Once, saved with the game. |
| `cm.stopTimer(key)` | Cancel an `every`, `at` or `after`. |
| `cm.timers()` | List of `"every key"`, `"at key"`, `"after id"`. |

### Commands and verbs

| Member | Effect |
| --- | --- |
| `cm.command(name[, opts], fn)` | New game command; returns its ID (`JS` + upper-case name). |
| `cm.serviceDone(npc, who, service)` | Keeps `who`'s held payment for a `"pay": "done"` service of `npc`, as `MPSERVICE who DONE`. Throws when there is none. |
| `cm.serviceRefund(npc, who, service)` | Gives back `who`'s latest payment for the service, held or kept, once. Throws when there is none. |
| `cm.poolUnits(org, pool)` | The units in an organization's shared pool. |
| `cm.poolAdd(org, pool, n, what)` | Adds units, paid by no one; refused past the pool's `MAX`. Returns the receipt number. |
| `cm.poolSpend(org, pool, n, what)` | Spends units; refused, spending nothing, when the pool holds too little. Returns the receipt number. |
| `cm.adoptCulture(mob, culture, force)` | Takes up a culture of `world/cultures.ini`, by the same rules as `MPCULTURE`; returns what changed, in words. Throws when refused. |
| `cm.leaveCulture(mob, force)` | Leaves the character's culture: what it gave goes, but its `KEEP`. Throws when refused (`LEAVE=NEVER` without `force`). |
| `cm.record(mob, book, record, value, lower)` | A personal record in a challenge book: the first value and the best (lowest when `lower`), kept; returns `"first"`, `"best"` or `""`. |
| `cm.uncommand(name)` | Remove it; true if there was one. |
| `cm.verb(thing, phrase, fn)` | A phrase that works near one room, item or mob. |
| `cm.unverb(thing, phrase)` | Remove it; returns how many. |

### Background work

| Member | Effect |
| --- | --- |
| `cm.background(worker, data, timeoutMs, callback)` | JSON-only work off the game thread; returns a job. See [Blocking work](#blocking-work-and-execution-limits). |

## Objects that cm returns

### Clock

`cm.clock([area])`. Properties are methods (call them with parentheses).

| Member | Returns |
| --- | --- |
| `hour()` | Hour of the day from 0. |
| `day()` | Day of the month from 1. |
| `month()` | Month from 1. |
| `year()` | Year. |
| `daypart()` | `dawn`, `day`, `dusk` or `night`. |
| `season()` | `spring`, `summer`, `fall` or `winter`. |
| `moon()` | Moon phase, lower case. |
| `absHours()` | Hours since the calendar began. |
| `hoursInDay()`, `daysInMonth()`, `monthsInYear()` | Calendar sizes. |
| `text()` | Short time description (as the `TIME` command). `toString()` gives the same. |
| `pass(hours)` | Advance this clock, running its events, and save it. |
| `raw` | The engine's `TimeClock`. |

### Weather

`cm.weather([area])`.

| Member | Effect |
| --- | --- |
| `get()` | Current weather word, lower case: clear, rain, ... (`toString()` too). |
| `next()` | The weather that comes next. |
| `describe()` | The weather as a player is told it. |
| `forecast()` | What the weather will do, as a player is told it. |
| `set(word)` | Change the weather now (as `WEATHER SET`); false if not a weather word. |
| `setNext(word)` | Set what it turns to next; false if not a weather. |
| `hold(word, hours)` | Change now and hold it for game hours (as `MPWEATHER ... FOR`); false if not a weather. |
| `chances()` | Map of each weather to its percent odds for the step after next. |
| `raw` | The engine's `Climate`. |

### LawView

`cm.law([areaOrRoom])`; `null` when there is no law.

| Member | Effect |
| --- | --- |
| `warrants()` / `warrants(mob)` | Warrants (all, or against a mob). |
| `crimes()` | Sorted crime names the law knows. |
| `accuse(mob, crime)` | Make a warrant as a witness would (`LAW ACCUSE`); false if it is no crime here or the law is off. |
| `pardon(mob[, crime])` | Remove warrants (all if no crime or `ALL`); returns how many. |
| `wanted(mob[, crime])` | True if a warrant is out. |
| `finesOwed(mob)` | Fines owed, base value. |
| `addFine(mob, amount)` | Add to fines (minus takes away); returns what it owes now. |
| `fine(mob, amount)` | Take the amount from the mob's money now, in the area's currency; false (nothing taken) if it cannot pay. |
| `release(mob)` | Let a mob out of jail as if its time were served. |
| `suppress(crime, time)` | No warrants for a crime (or `ALL`) for a time, `"n TICKS|HOURS|DAYS|WEEKS|MONTHS"` or `OFF`; false if the time is invalid. |
| `behavior`, `area`, `raw` | The legal behavior, its area and the engine's `Law`. |

### Shop

`cm.shop(keeper)`.

| Member | Effect |
| --- | --- |
| `stock()` | List of what is for sale. |
| `find(name)` | First thing of that name for sale. |
| `price(thing[, buyer])` | What the keeper asks (base value), or -1. `thing` may be an object or a stock name; buyer defaults to the player. |
| `pays(thing[, seller])` | What it pays for a thing, or -1. |
| `remove(name, n)` | Take `n` (or -1 for all) of a product out of stock; returns how many. |
| `stock(product, n)` | Set how many of a product it has (a new thing is copied onto a shelf); false if nothing to stock. |
| `setPrice(name, price)` | Fixed price (-1 for usual); false if not stocked. |
| `clear()` | Empty every shelf. |
| `reset()` | Put the stock back as made. |
| `budget()` / `setBudget(amount)` | What is left of its buying budget (-1 for no limit). |
| `keeper`, `raw` | The keeper and its `ShopKeeper` interface. |

`stock()` with no arguments lists; `stock(product, n)` sets.

### Bank

`cm.bank(banker)`. `depositor` is a mob, a name, or `null` for the player.

| Member | Effect |
| --- | --- |
| `balance(depositor)` | Money kept, base value. |
| `items(depositor)` | Items kept. |
| `worth(depositor)` | Worth of the kept items. |
| `pay(depositor, delta, reason)` | Pay in (positive) or take out (negative) with a ledger line; false if not enough. |
| `debt(debtor)` | What one owes the bank. |
| `lend(debtor, amount, interestPct, dueHours)` | Add a debt (a minus pays some off); interest per month in percent; `dueHours` game hours or 0. |
| `forgive(debtor)` | Clear the debt. |
| `raw` | The `Banker`. |

## sud, gen and the other globals

### sud

`sud` is a small object of short names over `cm`, defined in `JSScope`.

| Member | Same as |
| --- | --- |
| `sud.time.now()`, `ticks()`, `isTurnBased()` | `cm.now()`, `cm.ticks()`, `cm.isTurnBased()` |
| `sud.time.advance(ticks)` | `cm.advance(ticks)` |
| `sud.time.passTime(hours[, area])` | `cm.passTime(...)` |
| `sud.time.waitUntil(test[, then])` | `cm.waitUntil(...)` |
| `sud.party.leader()` | First of `cm.party()`, or `null` |
| `sud.party.members()` | `cm.party()` |
| `sud.party.isMember(mob)` | Is the mob in `cm.party()` |
| `sud.save.slot()`, `isBuilder()`, `isDev()` | `cm.saveSlot()`, `cm.isBuilder()`, `cm.isDev()` |
| `sud.find.room(id)`, `mob(name[, where])`, `item(name[, where])` | `cm.room`, `cm.findMob`, `cm.findItem` |
| `sud.vars.get(holder, name)`, `set(holder, name, value)`, `list(holder)` | `cm.getVar`, `cm.setVar`, `cm.vars` |
| `sud.on(event, fn[, key])`, `sud.off(key)` | `cm.on`, `cm.off` |

### gen

`gen` runs the random-area template system from JavaScript (see
[chapter 12](12-generation.md)); each call is a fresh roll and results are
live objects that you place yourself. `file` is a template file under
`resources/` or `world/resources/`; `vars` is an object such as
`{level_range: 5}` (variable names are upper-cased).

| Call | Returns |
| --- | --- |
| `gen.item(file, id, vars)` | Array of items, placed nowhere. |
| `gen.mob(file, id, vars)` | Array of mobs, not yet brought to life. |
| `gen.string(file, id, vars)` | Text. |
| `gen.room(file, id, dir)` | A room linked off the host's room in a free direction. |
| `gen.area(file, id, dir, seed, vars)` | An area whose entrance is linked off the host's room; `seed` is a number, `GAME ...` or null for a new one. |
| `gen.layout(name, size, seed)` | Array of nodes `{index, x, y, type, exits}` laid out as `GENERATE` would. |

An unknown direction or layout throws an error.

### Other globals worth knowing

- `Math.random()`: seedable game dice.
- `JSON`: Rhino's built-in; the stores are saved through it.
- `Packages` / `java` / `javax`: Java access (Rhino standard).
- In `<SCRIPT>` and JRUN scopes, `toJavaString(x)` and `getCMType(x)` (returns
  `mob`, `item`, `room`, ... or `unknown`/`null`).

## Files and libraries

```js
// world/js/lib/prices.js
exports.discount = function (price, percent) {
    return Math.max(0, Math.floor(price * (100 - percent) / 100));
};
```

```js
var prices = require('prices');
prices.discount(20, 10);          // 18
```

### require(name)

`require` is Rhino's CommonJS loader, installed in sandboxed mode:

- Search order: `world/js/lib/` first, then `resources/js/lib/` (relative to
  the game's working directory). Name the file without `.js`.
- A module has its own `exports`; do not rely on seeing the caller's variables
  (its scope inherits from the scope `require` was installed in, so `cm` and
  the standard objects are there; whether the caller's top-level variables are
  also visible is unverified).
- Each module is compiled once and re-read when the file's timestamp
  changes. A scope that has already required a module keeps the old copy;
  callers made later get the new one (see [reload](#reload-and-debugging)).
- Modules are ordinary scopes with `cm` available; their module-level state
  lives as long as the process (not a campaign save).
- It is not npm: no `node_modules`, no `package.json`.

### include(path)

`include('world:js/helpers.js')` or `include('helpers.js')` runs the file in
the **calling scope**, so what it defines stays there. `path` is under
`resources/`, or `world:path` under the world folder; `..` is refused; a
missing file throws "include: no such file". Compiled once while unchanged.

Use `require` for libraries with an interface and `include` to share setup
code that must define globals.

## Durable state, code and timers

Three things are different and must not be confused:

| Kind | Examples | Saved in the campaign? |
| --- | --- | --- |
| **Data** | `cm.store` contents, `cm.after` data | Yes, as JSON |
| **Code registration** | `cm.on`, `cm.command`, `cm.verb`, `cm.every`, `cm.at` | No: recreate at every boot |
| **Process memory** | JavaScript globals, library module state | No |

### cm.store

`cm.store(key)` returns a plain JavaScript object that is saved with the
game. A save writes all stores as JSON in the `<JSSTATE>` section of
`world.xml`; a restore puts them back; a new game starts from
`world/js/state.json` if the world has one.

```js
var state = cm.store('world');            // also cm.store()
state.visits = (state.visits || 0) + 1;
```

Keys (`JSStore.keyOf`):

| You pass | The key is | Notes |
| --- | --- | --- |
| nothing, `null`, `''` | `world` | The shared world store. |
| a string such as `'quest:smith'` | that string | Explicit keys deliberately share data. |
| a Room | `room:<extended room ID>` | |
| a MOB or Item | `instance:<persistent instance ID>` | Survives moving and renaming; a clone gets its own. |
| an Area | `area:<name>` | |
| a Quest | `quest:<name>` | |

Old name-based stores (`mob:Room#1/Name`, `item:...`) migrate to the instance
key on first access when exactly one live object matches. If several match,
the old data stays under its legacy string key, each instance starts from a
copy of it, and a warning is logged; give each its own data with `JS STATE`.

A store holds data: numbers, strings, booleans, arrays and plain objects.
Functions and Java objects (mobs, rooms) are lost at the save; keep a name,
a room ID or an instance key and look the object up again.

```js
// a smith's <SCRIPT>: an order, due on day 12, still due after SAVE and RESTORE
var orders = cm.store(cm.host());
orders.list = orders.list || [];
orders.list.push({who: source().Name(), item: 'longsword', readyDay: cm.clock().day() + 3});
```

`source().Name()` is a Java string; JSON saves it as a string, but compare
with `'' + x` when the type matters.

### world/js/state.json

```json
{"world": {"gold": 0}, "quest:smith": {"orders": []}}
```

A new game loads each top-level object as a store. Each value must itself be
an object; other values are skipped. A file that is not a JSON object logs an
error and the game starts with no stores.

### JS STATE (builder or DEV games)

```text
JS STATE
JS STATE world
JS STATE world = {gold: 5}
JS STATE quest:smith DELETE
```

With no key it lists the stores (first 70 characters of each). `JS STATE key`
shows one, indented. `= value` replaces the store; the value may be JSON or a
JavaScript object literal and must be an object. `DELETE` removes it.

### Old trick

Before `cm.store`, state was kept as JSON text in a variable:
`setVar('*', 'STATE', JSON.stringify(o))`. It still works; prefer stores.

## Events: cm.on

`cm.on(event, function[, key])` registers a listener and returns its key.
A second registration with the same key replaces the first. With no key the
function's own text is the key, so a `<SCRIPT>` that registers the same
function each run keeps one listener. `cm.off(key)` removes by key;
`cm.off(event, fn)` removes one added without a key; `cm.listening([event])`
lists. Event names are case-insensitive (`enterRoom` = `enterroom`). Unknown
names throw an error listing the valid ones.

Listeners are code, not state. Register them in `onBoot`. Listener errors are
reported (and logged) and the listener keeps going; the error does not stop
the engine.

### Clock and weather events

Watched on the player's area, once per tick. The function gets
`(area, was, now)`.

| Event | `was`/`now` |
| --- | --- |
| `hour`, `day`, `month`, `year` | numbers |
| `daypart` | `dawn`, `day`, `dusk`, `night` |
| `season` | `spring`, `summer`, `fall`, `winter` |
| `weather` | weather words, lower case |

They only fire while the player stays in one area between ticks.

### Course-of-the-game events

These are the `GAME_PROG` events. The function gets `(character, detail)`.

| Event | Detail |
| --- | --- |
| `newgame`, `restore` | `restore`: the slot |
| `save` | the slot; **return `false` to refuse the save** |
| `newchar`, `switchto`, `switchfrom` | |
| `pause`, `resume`, `timemode` | `timemode`: the mode |

`tick` runs every tick with no arguments.

### World events

Fired wherever they happen in the world.

| Event | Arguments | Notes |
| --- | --- | --- |
| `death` | `(victim, killer)` | `killer` may be null. |
| `beforeDeath` | `(victim, killer)` | Return `false` to keep the victim alive, at 1 hit point. |
| `levelUp` | `(mob, level)` | After the level is gained. |
| `questWin` | `(mob, quest)` | |
| `questEnd` | `(quest, mob, why)` | |
| `enterRoom` | `(mob, room, from)` | |
| `beforeEnterRoom` | `(mob, room, from)` | Return `false` to keep the mover out. |
| `enterArea` | `(mob, area, from)` | When the move crosses an area border; `from` may be null. |
| `itemGained` | `(mob, item)` | A mob in a room gets an item. |
| `itemLost` | `(mob, item)` | |
| `itemCrafted` | `(mob, item, receiptJSON)` | After a controlled item-modifier craft has settled. Item-loss notifications from its consumed costs are deferred until settlement. Returning false cannot roll the transaction back. A repeated settled request emits no second event. Listeners may inspect and save the result but must not start a crafting mutation. See [chapter 17](17-item-modifiers-crafting.md). |
| `factionChange` | `(mob, faction, change)` | `change` is the delta. |
| `turn` | `(leader, count)` | Each step of turn-based time. |

Veto events (`beforeDeath`, `beforeEnterRoom`, `save`) are never deferred. If
several listeners exist, any `false` vetoes. Keep these quick and do not
trigger the same operation recursively from inside them.

```js
// world/js/init.js: at 50 orcs killed, the warlord comes
function onBoot() {
  cm.on('death', function(victim, killer) {
    if (killer == null || !sud.party.isMember(killer) || ('' + victim.Name()).indexOf('orc') < 0) return;
    var w = cm.store('world');
    w.orcKills = (w.orcKills || 0) + 1;
    if (w.orcKills == 50) cm.loadMob('Orc Warlord', cm.room('Fort#1'));
  }, 'orcCount');
}
```

`itemGained`/`itemLost` cost nothing until something listens; registering one
turns the check on for every mob.

## Timers

Timers run on the game's clock: they wait while the game is paused and step
with turns. A ticker starts when the first timer or clock listener is
registered.

| Call | Meaning |
| --- | --- |
| `cm.every(spec, fn[, key])` | Again and again. `spec` is a number of ticks, or `'N ticks'`, `'N hours'`, `'N days'` (also `turns`, `hour`, `day`). A bare `'day'` means 1. Throws if it is no time. A key repeats replace the earlier timer. Returns the key (default: the function's text). |
| `cm.at(spec, fn[, key])` | When the calendar comes to it, once per match. `spec` is pairs `hour N`, `day N` (day of month), `month N`, `season winter`, combined: `'day 7 hour 8'`. Throws if unparseable. Returns the key. |
| `cm.after(ticks, fn[, data])` | Once, `ticks` on (at least 1), as `fn(data)`. **Saved** with the game; returns an id like `t123-4`. |
| `cm.stopTimer(key)` | Takes away an `every`, `at` or `after` by key/id. |
| `cm.timers()` | `["every key", "at key", "after id"]`. |

`every` and `at` are code: register them in `onBoot`. `at` fires when its
condition **becomes** true (a restore into the matching hour does not fire
until the next match). `every` with hours counts game hours on the player's
area clock.

`after` stores the function's source text and its `data` in the hidden store
`js:timers`. After a restore the function is rebuilt from its text in a fresh
scope with `cm`, `require` and `include` but **without** the variables around
it:

```js
// right: everything the callback needs is in data
cm.after(30, function (d) {
  cm.echo(cm.room(d.room), 'The cart arrives.');
}, {room: 'Town#12'});

// wrong: 'where' is a captured local; it vanishes after RESTORE
var where = cm.room('Town#12');
cm.after(30, function () { cm.echo(where, 'The cart arrives.'); });
```

```js
function onBoot() {
  cm.at('day 7 hour 8', function () {
    cm.echo(cm.room('Town#Square'), 'Traders set up their stalls.');
  }, 'marketDay');
}
```

## New commands, local verbs and effects

### cm.command

```js
cm.command('dig', {help: 'DIG [direction]: dig here.', cost: 1,
                   combatCost: 2, words: ['excavate']},
           function (mob, args) {
  cm.tell(mob, 'You dig ' + args);
});
```

Makes a real game command. The name is one word (no spaces). Options:

| Option | Default | Meaning |
| --- | --- | --- |
| `help` | `"<NAME>: a command of this world."` | Shown by `dig ?` or `dig help`. |
| `cost` | `1` | Action cost. |
| `combatCost` | same as `cost` | Action cost in combat. |
| `words` | none | Extra words for the same command. |

`fn(mob, args)`: `args` is the rest of the line as a string. Return `false` if
nothing was done (costs are then not taken); any other return counts as done.
A second `cm.command` with the same name replaces the first;
`cm.uncommand('dig')` removes it. Its class ID is `JSDIG`. Commands are
orderable (`canBeOrdered` true) and available to everyone (no security
check): check permissions inside the function if needed.

### cm.verb

```js
function onBoot() {
  cm.verb(cm.room('Mine#7'), 'pull lever', function (mob, rest) {
    cm.echo(mob.location(), 'The cart rattles away down the slope.');
    cm.store('world').cartAtBottom = true;
  });
}
```

`cm.verb(thing, phrase, fn)` makes a phrase that works near that room, item or
mob only, checked before the game's own commands. The first word must be the
command word; the phrase's other words must all appear among what follows
(`pull the lever` matches `pull lever`). The function gets
`(mob, rest)`; return `false` to let the game go on. If the player types the
phrase where none of its things is within reach and the first word is no game
command or social, they are told "You can't do that here." A destroyed host's
verbs are dropped. `cm.unverb(thing, phrase)` removes. Both are code: register
at boot. The MOBPROG alternatives are `VERB_PROG` and `CMDFAIL_PROG` in
[chapter 26](26-mobprog-triggers.md).

Note that `cm.cmd` (forced) does not run local-verb interception; test verbs
with real typed input.

### Effects

Stat-changing and message-intercepting effects are written as
[Prop_JScript](#prop_jscript-and-jsbehavior) files or as
[JavaScript classes](#javascript-classes).

## World lifecycle hooks: init.js

Source: [WorldJS](../../CoffeeMud/com/planet_ink/coffee_mud/core/WorldJS.java).
`world/js/init.js` is read once when the game starts (and again on a game
restart or `JS RELOAD`). It runs top to bottom in a scope of its own (`cm`
there has no host), so top-level code runs at load; then the engine calls
whichever of these functions it defines. Hooks run on the game thread under
the usual time limit; an error is reported like any script error and the game
goes on.

| Function | Called | Arguments | Return |
| --- | --- | --- | --- |
| `onBoot()` | Once as the game starts, after clocks and world files load, before any character is in the world | none | ignored |
| `onNewGame(player)` | When a new game's first character enters the world; `state.json` stores are already loaded | the character | ignored |
| `onRestore(player, slot)` | When a restored game's character enters the world | the character, the slot name | ignored |
| `onBeforeSave(slot)` | Just before a save | the slot | `false` refuses the save (say why yourself); runs before `cm.on('save')` listeners and `GAME_PROG SAVE` |
| `onPlayerEnter(player)` | Each time a character enters the world, after `onNewGame`/`onRestore` | the character | ignored |
| `onSwitch(fromChar, toChar)` | When the player switches characters | both characters | ignored |
| `onPartyChange(leader)` | When the party changes (join, leave, rest, lead, switch) | the leader (or the member if none) | ignored |
| `onBuilderStart(builder)` | In builder mode only, when the character enters at game start, before the new-game/restore event | the builder | ignored |
| `onReload(old)` | After `JS RELOAD` of init.js (`onBoot` is not run again) | the old file's scope | ignored |

If you need hooks like "before death" or "before entering a room", those are
[cm.on events](#events-cmon), not init.js functions.

An `onBoot` skeleton that registers everything it owns:

```js
// world/js/init.js
function onBoot() {
    cm.on('beforeEnterRoom', function (mob, room, from) {
        // veto: nobody enters the vault before the alarm is off
        return !(String(room.roomID()) === 'Keep#9' && !cm.store('world').alarmOff);
    }, 'vault.guard');

    cm.on('save', function (who, slot) {
        return cm.var('*', 'INBOSSFIGHT') != 'yes';   // no saving in the boss fight
    }, 'noBossSave');

    cm.command('museumstatus', {help: 'MUSEUMSTATUS: show the museum count.', cost: 0},
        function (who, args) {
            cm.tell(who, 'Bells rung: ' + (cm.store('world').museumBells || 0));
        });

    cm.every('6 hours', function () {
        cm.store('world').sixHourTicks = (cm.store('world').sixHourTicks || 0) + 1;
    }, 'sixHour');
}

function onNewGame(player) {
    cm.tell(player, 'The world remembers you.');
}
```

Use stable keys so a second registration (after `JS RELOAD` or a re-run)
replaces rather than duplicates. See also the shipped
[example init.js](examples/world/js/init.js).

## JavaScript inside MOBPROG

This is the cheapest way to add JavaScript to a world: a few lines inside a
trigger. For the MOBPROG language itself see [chapter 4](04-mobprog.md).

### `<SCRIPT>` blocks

```text
GREET_PROG 100
<SCRIPT>
var shop = cm.shop(host());
if (shop != null && source() == sud.party.leader())
  cm.say(host(), 'For you, the cloak is only ' + Math.floor(shop.price('cloak', source()) * 0.9) + '.');
</SCRIPT>
~
```

The block runs when the line is reached. The scope is rebuilt each time, so
variables do not carry between runs; use `cm.store` or script variables. The
block's source is named for errors (host, room, trigger and file line) so a
typo points at the right line of the `.script` file.

| Function | Gives |
| --- | --- |
| `host()` | The script's host (mob, item, room...). |
| `source()` | The mob that caused the trigger. |
| `target()` | The trigger's target (Environmental). |
| `monster()` | The host as a mob, when it is one. |
| `item()`, `item1()` | The primary item of the trigger; `item2()` the secondary. |
| `message()` | The trigger's text as a string (`$?`-style). |
| `msg()` | The `CMMsg` of a message trigger (`CNCLMSG_PROG`, `EXECMSG_PROG`...), or null. |
| `objs()` | The context's temporary object array. |
| `getVar(host, var)` / `setVar(host, var, value)` | Script variables (names upper-cased on set). |
| `room(id)` | A room by ID. |
| `findItem(where, name)` | An item in a room (or its ID string), a mob, or a container. |
| `door(roomId, dir, op)` | Quiet door change from anywhere: `op` is `OPEN`, `CLOSE`, `LOCK`, `UNLOCK` or `TOGGLE`; `dir` may name a container. False if no such door or lock. |
| `transform(item, name, keep)` | An item turned into another in place (as `MPTRANSFORM`; `keep` is its KEEP list or nothing); returns the new item or null. |
| `signal(words)` | Runs `MPSIGNAL words`. |
| `state(thing, name[, value])` | Reads (two arguments) or sets (three, via `MPSTATE`) an object's state. |
| `parm(name)` | A generic behavior's parameter, as `PARM(name)`. |
| `cancel()` | Ends the prog as `RETURN CANCEL`, so a `CNCLMSG_PROG` refuses its message. |
| `toJavaString(x)`, `getCMType(x)` | Helpers. |
| `debug(x)` | Developer-log output (see [debugging](#debugging)). |

**The MOBPROG bridge.** Every MOBPROG command is also an uppercase function:
`MPECHO('hello')`, `MPFORCE('$n', 'smile')`. With one argument it is passed
as written; with several, all but the last are single-quoted. Every
MOBPROG function works too: `RANDNUM$('10')` returns the value as a string,
and the bare name (`ISHOUR('6')`) returns a boolean. The `$`-codes expand as
in MOBPROG. Spell out in [chapter 27](27-mobprog-commands.md) and
[chapter 28](28-mobprog-functions.md).

**Ending with `cancel`.** Besides calling `cancel()`, a block whose last
value is the string `'cancel'` also cancels.

Gotchas:
- A script file's `~` ends a script, so keep `~` out of embedded JavaScript.
- A `<JSQUEST>` block (see [Quests](#quests)) may be split at semicolons;
  end its lines without them.

### JS(expr) and JS$(expr)

```text
IF JS(cm.store('world').trapDisarmed)
  MPECHO The trap is already safe.
ENDIF
MPECHO The answer is $%JS$(6 * 7)%.
```

`IF JS(expr)` is true when the expression is truthy. As a value, the
expression's result becomes text (whole numbers lose their decimal point;
`null`/`undefined` give nothing). One scope is kept per scripting engine, with
`host()`, `source()`, `target()` and the rest set for each call. Subject to
`JSCRIPTS` approval ("not approved: ..." in the error log if refused).

### MPJSFILE

```text
MPJSFILE world:js/trap.js north
```

Runs a file as a `<SCRIPT>`, with `args` set to the rest of the line. The path
is under `resources/` or `world:`. A missing file logs "no such file". Use it
for scripts too long to inline. `cancel()` works the same.

## Prop_JScript and JSBehavior

[Prop_JScript](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_JScript.java)
(a property, for mobs, items, exits, rooms and areas) and
[JSBehavior](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/JSBehavior.java)
(a behavior, same targets) run a JavaScript file:

```text
SCRIPT=world:js/props/bloodblade.js KILLS=0
```

The file is compiled once while it is unchanged and run in a **scope of its
own for each property or behavior**, so its variables belong to that one
instance. The other parameters become `parms` (numbers become numbers;
everything else strings). What a hook leaves in `parms` is written back into
the host's text, so `parms` is the place for small per-instance saved state.
The scope has `CMMsg`, `parms`, and all the shared globals (`cm`'s host is
the thing the effect is on).

| Hook | Prop_JScript | JSBehavior | Arguments / return |
| --- | --- | --- | --- |
| `affectPhyStats` | yes | no | `(host, affected, stats)`: a PhyStats to change |
| `affectCharStats` | yes | no | `(host, mob, stats)`: a CharStats |
| `affectCharState` | yes | no | `(host, mob, state)`: a CharState |
| `okMessage` | yes | yes | `(host, msg)`; return `false` to refuse the message |
| `executeMsg` | yes | yes | `(host, msg)` |
| `tick` | yes | yes | `(host, tickID)`; return `false` (Prop_JScript) to stop ticking |
| `onAdded`, `onRemoved` | yes | yes | `(host)` |
| `catchUp` | yes | yes | `(host, ticks)`: the area woke after being asleep |
| `onReload` | yes | yes | `(old)`: after `JS RELOAD` (the old scope) |

Behaviors are never asked for stat changes, so stat hooks need
`Prop_JScript`. Stat hooks run **every time stats are recomputed**: keep them
quick, derive values from base state, and never roll dice or grant rewards in
them. A hook that throws is reported once and **not called again** until the
text is set again. `SCRIPT` is required: without it the effect reports "no
SCRIPT=".

In-game help: `AHELP PROP_JSCRIPT` and `AHELP BEHAVIOR_JSBEHAVIOR`. See also
[chapter 23](23-properties-reference.md#prop_jscript) and
[chapter 24](24-behaviors-and-masks.md#jsbehavior).

```js
// world/js/props/bloodblade.js: 1 extra damage for each 10 kills
function affectPhyStats(host, affected, stats) {
  if (affected == host) stats.setDamage(stats.damage() + Math.floor(parms.KILLS / 10));
}
function executeMsg(host, msg) {
  // a death message's tool is the killer
  if (msg.sourceMinor() == CMMsg.TYP_DEATH && host.owner() != null && msg.tool() == host.owner()) {
    parms.KILLS++;
    host.recoverPhyStats();
  }
}
```

```js
// world/js/props/cursed.js on a sword: it cannot be dropped, and weakens its wielder
function okMessage(host, msg) {
  return !(msg.targetMinor() == CMMsg.TYP_DROP && msg.target() == host);
}
function affectCharStats(host, mob, stats) {
  var S = Packages.com.planet_ink.coffee_mud.Common.interfaces.CharStats;
  stats.setStat(S.STAT_STRENGTH, stats.getStat(S.STAT_STRENGTH) - 2);
}
```

## JScriptable

[JScriptable](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/JScriptable.java)
scripts a mob, item, room, exit or area wholly in JavaScript. Parameters:

```text
LOAD=world:js/npc/ferryman.js
LOAD=world:js/npc/ferryman.js SCOPE=GLOBAL
```

or the script text itself as the parameter. The script defines the handlers
it wants; only those are called.

### Handlers

| Handler | When |
| --- | --- |
| `onLoad()` | The behavior is added (and the game loads its host). |
| `onGreet(who)` | Someone comes into the host's room (any room of an area, for an area host). |
| `onLeave(who)` | Someone leaves it. |
| `onArrive(room)` | The host itself comes into a room. |
| `onSpeech(who, text)` | Someone else says something (language-aware for mobs). |
| `onGive(who, item)` | Someone gives the host an item. |
| `onGet(who, item)`, `onDrop(who, item)` | Someone else gets or drops an item. |
| `onWear(who, item)`, `onRemove(who, item)` | Someone wears/wields/holds, or removes, an item. |
| `onOpen`, `onClose`, `onLock`, `onUnlock` `(who, thing)` | Someone opens, closes, locks or unlocks a thing. |
| `onDeath(killer)` | The host dies. |
| `onKill(victim)` | The host kills. |
| `onDamage(who, amount)` | The host is hurt. |
| `onCast(who, ability, target)` | Someone else casts a spell. |
| `onLook(who)` | Someone else looks at or examines the host. |
| `onTick()` | Each tick. |
| `onHour(hour)`, `onDay(day)` | As the host's area clock changes. |
| `onCommand(who, verb, rest)` | Someone near the host types a command, before the game's own; return `true` to handle it. |
| `canMessage(msg)` | Before any message; return `false` to refuse it. |
| `afterMessage(msg)` | After any message. |
| `onReload(old)` | After `JS RELOAD`, with the old scope. |
| `onQuestAccept(quest, mob)`, `onQuestStep(quest, step, label)`, `onQuestWin(quest, mob, outcome)`, `onQuestFail(quest, mob, reason)`, `onQuestEnd(quest, reason)` | For the mobs and items of a running [quest](#quests). |

(The first group of 26 handler names is `JScriptable.HANDLERS`; quest
handlers are called by the quest machinery.) A handler that throws is
reported and not called again until the parameters are set again.

### Scope

- `me`: the host. `cm.host()` is the host too.
- `state`: the host's `cm.store`, saved with the game. With
  `SCOPE=INDIVIDUAL` (the default) it is the host's own store (`instance:`
  key for mobs and items); with `SCOPE=GLOBAL` or a name, the hosts that name
  the same scope **share one script scope** and `state` is the store
  `scope:<name>`; `me` and `cm` are set again for each call.
- `CMMsg`: the message constants (`CMMsg.TYP_GET`...).
- Everything shared.

### Functions callable from MOBPROG

Any function the script defines that is not one of the handlers answers
`MPCALLFUNC` and `CALLFUNC`: `MPCALLFUNC fare 5` or `IF CALLFUNC(fare 5)` on
the host's own script, `MPCALLFUNC $i fare 5` for another host's. The function
receives the arguments as **one string** and the calling mob, and what it
returns is `CALLFUNC`'s value.

```js
// world/js/npc/ferryman.js
function onSpeech(who, text) {
  if (('' + text).toLowerCase().indexOf('ferry') < 0) return;
  cm.say(me, 'Five coins, ' + who.name() + ', and we cross.');
  state.waiting = '' + who.Name();
}
function onGive(who, item) {
  if (state.waiting == '' + who.Name() && item.Name().indexOf('coin') >= 0) {
    state.crossing = {who: state.waiting, ticks: 3};
    cm.say(me, 'Hold on.');
  }
}
function onTick() {
  if (state.crossing && --state.crossing.ticks == 0) {
    cm.cmd(cm.findMob(state.crossing.who), 'east');
    state.crossing = null;
  }
}
function fare(args, who) {                    // MPCALLFUNC fare 5
  return 'The fare is ' + args + ' for ' + who.Name();
}
```

## Quests

A quest can have JavaScript handlers, either in a file named by
`SET JSFILE world:js/quests/cat.js` or in a `<JSQUEST>` ... `</JSQUEST>`
block in the quest script. Source:
[JSQuests](../../CoffeeMud/com/planet_ink/coffee_mud/core/JSQuests.java).
The quest chapter ([chapter 8](08-quests.md)) covers the quest script itself.

| Handler | When |
| --- | --- |
| `onStart(state)` | The quest starts. |
| `onStep(n, state)` | A step is reached (n from the step number). |
| `onTimeUp(state)` | The quest fails because time ran out (`EXPIRED`). |
| `onWin(player)` | A winner is declared. |
| `onAccept(player)` | A player accepts the quest. |
| `onStop()` | The quest ends. |

`state` is `cm.store('quest:name')`, saved with the game. The scope also has
`quest` (the Quest object), `quests` and everything shared; `cm.host()` is
null.

`quests` is a helper that works by quest name:

| Member | Effect |
| --- | --- |
| `quests.get(name)` | The Quest or null. |
| `quests.start(name)` | Start; false if no such quest or it would not start. |
| `quests.step(name)` | Advance one step. |
| `quests.win(name, who)` | Declare a winner (a mob or name), as `MPQUESTWIN`. |
| `quests.fail(name)` / `quests.stop(name)` | Stop it (no winner). |
| `quests.running(name)` | True while running. |
| `quests.stepNumber(name)` | Current step from 1, or 0. |
| `quests.status(name)` | `running`, `stopping`, `finished`, `waiting`, `stopped` or `none`. |
| `quests.objective(name)` | The quest's `INSTRUCTIONS`. |
| `quests.journal(name)` | "Display name: instructions", as the player's quest list has it. |
| `quests.getVar(name, var)` / `quests.setVar(name, var, value)` | The quest's own variables. |

The file is re-read when its text changes. A handler that throws is reported
as "quest 'name' fn()".

```js
// world/js/quests/cat.js: "Missing Cat"
function onStart(state) {
  var alleys = ['Town#14', 'Town#15', 'Town#22'];
  state.alley = alleys[Math.floor(Math.random() * alleys.length)];
  state.startHour = cm.clock().absHours();
  cm.loadItem('a frightened cat', cm.room(state.alley));
}
function onWin(player) {
  var hours = cm.clock().absHours() - state.startHour;
  cm.lib.beanCounter().addMoney(player, Math.max(10, 50 - 5 * hours));
}
```

A quest script may also contain the older `<SCRIPT>` ... `</SCRIPT>` block,
which runs at quest-script load time with `quest()` and `setupState()`
available. It is subject to `JSCRIPTS` approval ("`<SCRIPT>` not approved. Use
MODIFY JSCRIPT to approve.").

## Character progression: progression.js

`world/scripts/progression.js`, if present, decides how characters are made
and grow. Source:
[JSProgression](../../CoffeeMud/com/planet_ink/coffee_mud/core/JSProgression.java).
Each function is called only if defined. The file runs in the usual scope
(`cm`, `require`, `include`, `sud`), is re-read when its modification time
or size changes, and is reset at every game start.

| Function | Called | Return |
| --- | --- | --- |
| `xpGain(mob, source, amount)` | For each XP gain (amount above 0). | The XP it gives instead (a number; not under 0). Return nothing for the usual. |
| `practicesForLevel(mob)` | At each level | Practices to grant instead of the usual. |
| `trainsForLevel(mob)` | At each level | Trains to grant instead of the usual. |
| `onLevel(mob, level)` | After a level and its gains | ignored |
| `onLearn(mob, abilityId)` | When a character gets a skill (teacher, level, script) | ignored |
| `onTrain(mob, what, amount)` | After `TRAIN`; `what` is `MANA`, `MOVES`, `GAIN`, `PRACTICES`, `HITPOINTS`, a stat name (`STRENGTH`) or a class ID | ignored |
| `onCreationStep(mob, step, answer)` | After each step of character creation | ignored |
| `onCreated(mob)` | When the new character enters the world, before `GAME_PROG NEWCHAR` | ignored |
| `statRoll(mob, bonus)` | With `STARTSTAT=SCRIPT` in `coffeemud.ini` or `world.ini`: sets the new character's stats | set them on `mob`, or return an object / list |

`xpGain` `source` strings begin with the origin: `COMBAT:<victim level>[flag]`
for a kill, `ABILITY:<id>`, `CLASS:<id>`, `COMMAND:Teach`, `MISC:...`,
`SCRIPT:<quest>` (from `MPEXP` and quest rewards), `DEATH:`, `FACTION:<id>`,
`SIEGE:`, `LIEGE:...`, `COST:`, and a few others (`SERVICE:`, `QUEST:`, `DISCOVERY:`,
`ACHIEVE:`, `REMORT:`, `HOMAGE...` with no colon). Test with `source.indexOf('COMBAT:') == 0`.
The XP from your function is still subject to the difficulty's player-XP
setting.

`onCreationStep` steps the code passes: `race`, `gender`, `stats`, `class`,
`factions`, `deity`, `traits`, `template`, `origin`, `kit`, plus the name
(lower-cased) of other creation questions. For `race`, `gender`, `stats`,
`class`, `factions` and `deity` the `answer` is a text form of the choice;
for the others it is empty.

`statRoll` stats may be returned as `{ strength: 14, dex: 12 }` (names,
abbreviations or their starts) or as a list in the order of the base stats.
Stats left out stay at `BASEMINSTAT`. `bonus` is the bonus points the theme
gives.

```js
// world/scripts/progression.js
function xpGain(mob, source, amount) {
  if (source.indexOf('COMBAT:') == 0 && mob.findTattoo('PACIFIST') != null)
    return 0;                       // the pacifist monk's vow
  if (source.indexOf('SCRIPT:') == 0)
    return amount * 1.5;            // MPEXP's, from the world's scripts
}
function trainsForLevel(mob) {
  return (mob.basePhyStats().level() % 5 == 0) ? 2 : 1;
}
function statRoll(mob, bonus) {
  function d6() { return 1 + Math.floor(Math.random() * 6); }
  function roll() { var r = [d6(), d6(), d6(), d6()].sort(); return r[1] + r[2] + r[3]; }
  return { strength: roll(), intelligence: roll(), dexterity: roll(),
           constitution: roll(), charisma: roll(), wisdom: roll() };
}
```

See also [chapter 3](03-characters.md) and the shipped
[Ember Trial progression.js](examples/ember-trial/world/scripts/progression.js).

## Zapper masks

A zapper mask (as in `Prop_ReqEntry`, a shop's buy mask, a spawn rule's mask;
full syntax in [chapter 24](24-behaviors-and-masks.md)) may have:

- `-JS "expr"`: lets through only what the expression is true for.
- `+JS "expr"`: lets through what the expression is true for, whatever else
  the mask says.

The expression sees `mob` (the mob checked, or an item's owner), `item` (the
item checked), `room` (where), `target` (whatever is checked) and `cm`
(whose host is the target, or the mob). It is compiled once with the mask and
run in one scope shared by all masks. A value counts as true when truthy; an
error counts as false and is reported once per expression. Describing the
mask (`maskDesc`) reads "Requires that the JavaScript "expr" be true." for
`-JS` and "Allows those for whom the JavaScript "expr" is true." for `+JS`.

```text
Prop_ReqEntry: -JS "cm.store('world').bellRung && cm.clock().hour() < 4"
```

Masks are evaluated often (every time something is checked), so keep the
expression cheap and free of side effects.

## JavaScript classes

A JavaScript file can be a full engine class, treated like a compiled Java
class: a behavior, a property or ability, a command, a mob, an item, a race,
a class, a locale, an exit, an area. This is the most powerful layer and the
only one that can override engine methods directly.

### Folders and loading

Put the file in `world/js/[kind]/Name.js`. At game start each of these folders
joins the class path of its kind (after the stock classes):

| Folder | Class path setting |
| --- | --- |
| `behaviors` | `BEHAVIORS` |
| `abilities`, `properties` | `ABILITIES` |
| `items` | `ITEMS` |
| `mobs` | `MOBS` |
| `races` | `RACES` |
| `charclasses` | `CHARCLASSES` |
| `commands` | `COMMANDS` |
| `common` | `COMMON` |
| `locales` | `LOCALES` |
| `exits` | `EXITS` |
| `areas` | `AREAS` |

(`world/classes/[kind]/` is a parallel folder for compiled or script classes
without the JS checks; see [chapter 32](32-engine-programming.md).) The class
name is the file name without `.js`, and `ID()` must return exactly that name.
The class is then used like any other: `Name PARAMS` as a behavior or
property in an area file, `CREATE MOB Name`, and so on.

### Directives

The loader reads special comment lines, which are real code:

| Line | Meaning |
| --- | --- |
| `//extends fully.qualified.Class` | Required. The Java class to extend (the first one counts). A missing or unknown class makes the file fail to load; `WORLD CHECK`'s JS part reports it. |
| `//implements fully.qualified.Interface` | Optional, repeatable. Interfaces to implement. |
| `//package name` | Optional. Package for the generated class. |

### Writing the class

- `this` is the Java object. Call the parent with `this.super$method(args)`.
- Methods you define override the superclass's methods of the **same name and
  number of parameters**. There are no constructors: set initial fields in
  `newInstance()` (mobs and items) or in `startBehavior` and similar hooks.
- Top-level `var`s are shared by all instances of the class; keep
  per-instance state in an engine-supported field, in `this.text()`/parms, or
  in `cm.store(this)`.
- The file is compiled to Java bytecode, so **no instruction time limit
  applies** to it, and a loop that never ends in class code hangs the game.
- Use `Packages.com.planet_ink.coffee_mud...` for engine classes. `cm`,
  `require` and the other globals are available in the class's script scope.
- To migrate saved state across `JS RELOAD`, expose it through the engine's
  `STATE`/`ACTIVITY` stat codes (see [Reload](#reload-and-debugging)).

Templates: [`Property.js`](../../CoffeeMud/resources/js/templates/Property.js)
and [`WorldBehavior.js`](../../CoffeeMud/resources/js/templates/WorldBehavior.js).
Examples: [`CustomProperty.js`](../../CoffeeMud/resources/examples/CustomProperty.js),
[`Lemming.js`](../../CoffeeMud/resources/examples/Lemming.js),
[`QuickWho.js`](../../CoffeeMud/resources/examples/QuickWho.js).

### JSWorldBehavior

[JSWorldBehavior](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/JSWorldBehavior.java)
is a ready base for a behavior on a **room or area**. Extend it and define
any of:

| Method | Called |
| --- | --- |
| `onEnter(mob, room)` | Someone came into the room (any room of the area, for an area host). |
| `onLeave(mob, room)` | Someone left. |
| `onWeather(area, was, now)` | The weather changed; `was`/`now` are the engine's **upper-case** weather names. |
| `onDaypart(area, was, now)` | The time of day changed: `DAWN`, `DAY`, `DUSK`, `NIGHT` (upper case). |

Helpers: `this.echo(text)` (the room, or every occupied room of the area),
`this.roll('3d6+2')`, `this.spawn('catalog mob')` (a catalog mob in the host's
room; catalog caps hold), `this.room()`, `this.area()` and `this.parm('FARE')`
(a `FARE=5` in the behavior's own text). Note the case difference from the
`weather` and `daypart` [cm.on events](#events-cmon), which use lower case.

```js
// world/js/behaviors/BellRinger.js
//extends com.planet_ink.coffee_mud.Behaviors.JSWorldBehavior
function ID() { return "BellRinger"; }
function accountForYourself() { return "a bell that rings for visitors"; }
function onEnter(mob, room) {
    if (mob.isPlayer())
        this.echo("A bell rings as " + mob.name() + " comes in.");
}
function onDaypart(area, was, now) {
    if (now == "NIGHT")
        this.echo("The lamplighter makes his rounds.");
}
```

Give it to a room as `BellRinger` (or `BellRinger FARE=5`).

### A property class

From the shipped template (a property is an ability that is always on):

```js
//extends com.planet_ink.coffee_mud.Abilities.Properties.Property
function ID() { return "MyProperty"; }
function name() { return "my property"; }
function accountForYourself() { return "Hums quietly."; }
function okMessage(host, msg) { return this.super$okMessage(host, msg); }
function executeMsg(host, msg) { this.super$executeMsg(host, msg); }
var CMParms = Packages.com.planet_ink.coffee_mud.core.CMParms;
// the property's own text is this.text(); one setting of it:
// CMParms.getParmStr(this.text(), "WORDS", "")
```

Give it to a thing in its `.cmare` file or with `MODIFY` as any property:
`MyProperty WORDS=hello`. The `ID` must be unique among abilities.

### A command class

```js
// world/js/commands/Chime.js
//extends com.planet_ink.coffee_mud.Commands.StdCommand
var CMParms = Packages.com.planet_ink.coffee_mud.core.CMParms;
function ID() { return "Chime"; }
var words = CMParms.toStringArray(CMParms.parse("CHIME"));
function getAccessWords() { return words; }
function execute(mob, commands, metaFlags) {
    mob.tell("A small chime sounds.");
    return false;        // false: no further action cost
}
```

(Pattern verified by the test fixture `FixWorld.js`.) For a command that
needs no new class, [`cm.command`](#new-commands-local-verbs-and-effects) is
simpler and replaceable at runtime. A class is the choice when you want the
engine's own command features (security checks, `canBeOrdered`, action cost
methods).

### Checking classes

`WORLD CHECK` has a JS part that lists, for every `.js` in the class folders,
a missing `//extends` line, an `//extends` naming no class, and a file that
does not compile (with its line). It skips `lib/` and non-class folders such
as `props/`.

## Other places JavaScript runs

### Random-area templates

In a random-area template ([chapter 12](12-generation.md)): `${js:expr}` puts
in what the expression gives, and `CONDITION="js:expr"` or
`VALIDATE="js:expr"` keep a tag when it gives a true value. The expression
sees `defined` (the generator's variables by any case; numbers as numbers;
setting one defines it), `obj` (what is being built, or null),
`test(condition)` (a condition in the template's own grammar) and the shared
globals. One scope serves a generation run, so a library's state lasts while
an area is built. `JSCRIPTS=0` turns it off.

### Language TRANSLATESCRIPT

A generic language (`GenLanguage`) may carry `TRANSLATESCRIPT`, JavaScript
defining `translate(word, language)` and optionally `garble(text, count)`.
Return null to fall back to dictionaries. It runs in a plain standard scope
(no `cm`) under `JSCRIPTS` approval and the time limit. See
[languages](../languages.md).

### Cron jobs (inherited)

The inherited journal-driven cron system can run a `<SCRIPT>` ... `</SCRIPT>`
in a `SYSTEM_CRON` entry with `mob()` available. It is not part of the
single-player workflow and is not otherwise documented here.

## Reload and debugging

### JS RELOAD

```text
JS RELOAD
JS RELOAD props/ferry.js
JS RELOAD ALL
JS WATCH ON 5
JS WATCH OFF
```

Builder mode (or `DEV=1`) only. `JS RELOAD` with no argument reloads the
`world/js` files changed since the game started or since they were last
reloaded; a name (`props/ferry.js`, `js/props/ferry.js`, `world:js/props/ferry.js`
all work, `.js` optional) reloads one; `ALL` reloads every `.js` under
`world/js`. `JS WATCH ON [seconds]` does it by itself (default 3 seconds);
`JS WATCH OFF` stops it; `JS WATCH` alone reports.

A file that does not compile is reported with its line and **left as it was**;
the running definition keeps working. What a successful reload does, by where
the file is:

| File | Effect |
| --- | --- |
| `init.js` | Read again; its `onReload(old)` is called with the old scope. `onBoot` does **not** run again, and listeners, commands and timers registered earlier stay until you re-register with the same keys. |
| `lib/*.js` | Re-read where it is `require`d from then on; a scope that already required it keeps its copy. |
| Class folders (`behaviors/`, `properties/`, ...) | The class is compiled again. Behaviors and effects in the world are replaced by new instances (text copied). Mobs, items, rooms and so on made from then on use the new class; existing ones keep the old code until re-made. |
| Any other file (`props/...`) | Each `Prop_JScript`/`JSBehavior` running it reads it again, and the file's `onReload(old)` is called with the old scope. (A `JScriptable` rebuilds its scope and calls `onReload(old)` too.) |

Migration for replaced behaviors and effects: declared `STATE`/`ACTIVITY`
stat codes, effect duration (`TICKDOWN`), proficiency, invoker, savable flag,
`CANUNINVOKE` and `NEXTCAST` carry over. If the new class cannot accept
the saved state, or the migration throws, that live instance **keeps its old
class** and is counted in the report ("N incompatible instances kept (see
log)"). Arbitrary custom Java fields are not inferred. Always test a clean
restart as well as a reload before release.

```js
// world/js/props/ferry.js: a crossing half done carries over a reload
var crossing = null;
function onReload(old) { crossing = old.crossing; }
```

### JS ERRORS

```text
JS ERRORS
JS ERRORS 25
```

Lists the most recent errors (default 10, up to the last 50 kept): when,
where the script is (host, room and trigger, or file/quest/cron name), the
line, and the message, with the source line when Rhino has it. A script that
ran out of time is listed as "it ran longer than its time limit of N
seconds". In builder mode and `DEV=1` games each error is also shown to you
as it happens ("Script error: ..."); in a normal game it is logged and
handled by the `SCRIPTERRORS` ini key. The `SCRIPTERRORS` command (builder
mode, `DEV=1` games, or a player with the `CMDMOBS` or `JSCRIPTS` security flag) lists errors of both MOBPROG and JavaScript:
`SCRIPTERRORS [n] [text it must hold]` (default the last 20) and
`SCRIPTERRORS CLEAR`. See [chapter 13](13-testing-release.md).

### JS HELP

```text
JS HELP cm
JS HELP cm.room('Town#1')
JS HELP cm.store('world')
```

Evaluates the expression (in a scope with `cm`, `me`, `here`) and prints
what the value offers: a Java object's methods (each name with the argument
counts it takes, such as `setName(1)`), its stats for things that have them, a
class's static methods, or a JavaScript object's properties. A backtick is
read as an apostrophe so you can type the expression inside a quoted string.

### JCONSOLE reference

```text
JCONSOLE                    interactive prompt
JCONSOLE <line>             run one line
JCONSOLE @blacksmith        bind the console to a mob (or @here, @area, @me, an item name, a room ID)
JCONSOLE @blacksmith cm.store(me)
```

A bound console runs as that thing: MOBPROG calls (`MPECHO('...')`) run as it
with you as the source, and `me`, `here`, `area` and `cm` are its. Each console
keeps its own scope.

| Console line | Effect |
| --- | --- |
| `.inspect expr` | Name, class, room and stats of a thing; a JavaScript object as JSON (`.inspect cm.store(me)`). |
| `.trigger GREET` | Fire the event on the target, as if it happened, for its `JScriptable` and scripts. |
| `.trigger SPEECH <text>` | Same, someone says the text. |
| `.trigger GIVE <item>` | Same, a give. |
| `.trigger LOOK` / `.trigger TICK` | Same. |
| `.load file` | Run a file in the console's scope (under `resources/`, or `world:path`). |
| `.reset` | Drop the console and start a new scope. |
| `.time on|off` | Show how long each line took. |
| `.history [n]` | The lines typed before (default last 20). |
| `!n` | Run history line `n` again. |

In builder mode the history is kept (under `world/.history/`) from one game
to the next. `.trigger` simulates a real event and can change state. `.inspect`
reads a backtick as an apostrophe.

### The builder helper

`JRUN` scripts have an object named `builder` for building worlds. Each call
is made as the builder who ran the script.

| Member | Effect |
| --- | --- |
| `builder.here()` | The builder's room. |
| `builder.room(id)` | A room by ID. |
| `builder.dig(dir, type)` | A room made in a direction as `WORLD DIG` makes one, linked both ways, saved and tagged TODO; `type` is a locale class or template (null for this room's). Null if the direction is taken. |
| `builder.link(fromId, dir, toId)` | A one-way exit between rooms, saved. |
| `builder.create(kind, name, roomId)` | `kind` is `"mob"` or `"item"`; the catalog's of that name, or one of that class, placed in the room (default the builder's); the room is marked unsaved. |
| `builder.saveRoom([id])` | Save a room's mobs and items as `SAVE ROOM` does. |
| `builder.forEachRoom(areaName, fn)` | Calls `fn(room)` for each room of the area; returns the count. |
| `builder.cmd(line)` | Runs a command as the builder typed it. |

Source: [JSBuilder](../../CoffeeMud/com/planet_ink/coffee_mud/core/JSBuilder.java);
a complete use is the shipped
[build.js](examples/world/resources/creator/build.js).

### Debugging

- `debug(x)` writes `x` (objects as JSON, Java objects as their text) with the
  script's file and line to the developer log. It does nothing outside builder
  mode and `DEV=1` games. View with `DEVLOG LAST` (see `DEVLOG` usage:
  `DEVLOG ON`, `OFF`, `LAST [N]`, `CLEAR`, `WINDOW`).
- `MPDEBUG` is the MOBPROG equivalent.
- `JS ERRORS` and the game log carry full errors.
- In JCONSOLE, `.time on` and `.inspect` help find slow or surprising values.
- A `<SCRIPT>` error's line number is the line in the `.script` file, not in
  the block.
- Check the type: `getCMType(x)` tells a mob from an item; `('' + x)` turns a
  Java string into a JavaScript one.

## Blocking work and execution limits

Interpreted scripts have an **instruction budget**: every 10,000 instructions
Rhino checks the clock, and a script that has run past its limit is stopped
with a "ran longer than its time limit of N seconds" error and cannot catch it
(only its `finally` blocks run). Ordinary scripts use `JSTIMELIMIT` (default 2
seconds); `JRUN` uses `JSTIMELIMIT_JRUN` (default 30).

What the limit does **not** cover:

- Blocking native Java calls made from a script (file or network I/O, `sleep`)
  cannot be preempted safely on the game thread.
- Compiled `//extends` classes have no instruction limit at all.

Use `cm.background` for blocking work.

### cm.background

```js
var job = cm.background(
  function (data) {                 // the worker
    // runs on a pool thread in a FRESH scope: no cm, no closure, no game objects
    return {echo: data.text, n: data.n * 2};
  },
  {text: 'hi', n: 21},              // JSON data in
  5000,                             // timeout in milliseconds (1..300000)
  function (error, result) {        // runs on the game thread
    if (error) { debug('background failed: ' + error); return; }
    cm.echo(cm.here(), 'Worker said ' + result.echo + ' ' + result.n);
  });
```

Rules:

- `data` must be JSON-encodable; the worker receives it parsed and returns a
  JSON-encodable value, which the callback gets as `result`. A thrown error
  arrives as the `error` string.
- The worker's source text is re-evaluated in a clean scope containing only
  standard JavaScript. Java classes under `com.planet_ink.` and
  `org.mozilla.` are blocked there; other Java (such as `java.net`) is
  available, so set explicit I/O timeouts.
- The callback runs on the game thread, under the normal time limit; apply
  world changes **there**, never in the worker.
- The pool has two daemon worker threads and a queue of 32; when full the
  callback gets "background queue is full".
- On timeout the late result is discarded and the worker is interrupted;
  native calls that ignore interruption may keep a worker busy.
- Jobs and callbacks are cancelled on new game or restore and are never saved.
- The returned job has `cancel()`, `done()`, `error()` and `resultJSON()`.
  A cancelled job calls back with "cancelled".
- A callback whose host was destroyed in the meantime is skipped.

This supplies bounded background integration; it is not a dedicated service
for long-running or networked work.

## Java interop

Scripts can reach any Java class:

```js
var CMLib = Packages.com.planet_ink.coffee_mud.core.CMLib;
var CMClass = Packages.com.planet_ink.coffee_mud.core.CMClass;
importPackage(Packages.com.planet_ink.coffee_mud.core);   // then name classes plainly

var M = CMClass.getMOB('GenMob');                          // a new mob
var R = cm.lib.map().getRoom('Town#1');                    // same as cm.room
```

Useful entry points:

| Need | Use |
| --- | --- |
| Libraries (map, combat, law, English, XML, ...) | `cm.lib.<name>()` or `Packages...CMLib.<name>()` |
| Make engine objects | `CMClass.getMOB/getItem/getLocale/getBehavior/getAbility/getCommon(...)` |
| Message constants | `CMMsg.TYP_*`, `CMMsg.MSG_*` (`CMMsg` is provided in `Prop_JScript`/`JSBehavior`/`JScriptable` scopes; elsewhere import `Packages.com.planet_ink.coffee_mud.Common.interfaces.CMMsg`) |
| Stat objects | `Packages.com.planet_ink.coffee_mud.Common.interfaces.CharStats.STAT_STRENGTH` |
| Builder-style world edits | `CMLib.database()`, `CMLib.map()`; see the shipped `build.js` |
| Optional modules | `Packages.com.planet_ink.coffee_mud.core.OptionalModules` (see [chapter 14](14-optional-modules.md)) |

Cautions:

- Java methods use Java conventions: `mob.Name()` (capital N), `mob.name()`,
  `room.roomID()`, `msg.source()`. Use `JS HELP expr` to find the real names.
- Java strings are not JavaScript strings (see [the runtime](#the-runtime)).
- Java lists and maps have Java methods (`list.size()`, `list.get(i)`,
  `list.contains(x)`); Rhino also allows `list[i]` on `java.util.List`.
- Calling engine code directly skips the checks that commands and messages
  apply. If you change stats directly, call `recoverPhyStats()`,
  `recoverCharStats()` and `recoverMaxState()` as the engine does.
- Do not hold Java objects in `cm.store`, in `cm.after` data, or across a
  restore.

## Native development

Use the [Programming Guide](../../CoffeeMud/guides/Programming.html) and
[chapter 32](32-engine-programming.md) for engine interfaces. Source is under
`CoffeeMud/com/planet_ink/coffee_mud/`; `core` covers the shared runtime,
`Libraries` the services, and `Commands`/`Abilities`/`Behaviors` and so on the
corresponding types. World-owned native definitions use the supported world
class loader; changing a compiled Java binary requires a restart rather than
a reload. Match the current interfaces and licensing when distributing code.

Build with `CoffeeMud/build.sh` or `CoffeeMud\build.bat`. Do not run
concurrent compilers against the same class tree. Add meaningful regression
coverage for new engine behavior (the JavaScript fixtures `tools/fixtures/fixes/js*.js`
are a model), exercise save/restore for live state, and use `ENGINE=1
tools/check.sh` for core changes. The three space-test baseline failures are
tracked in the [audit](../../audit/developer-tools-audit.md); a new failure
still needs investigation.

## Worked examples

### 1. A custom command, a world hook and saved counters (init.js)

Goal: a `RING` command in the museum that counts rings, a quiet daily event,
and a guard against saving mid-fight. Put this in `world/js/init.js`:

```js
function onBoot() {
    // 1. a command: replaceable, so re-running onBoot is harmless
    cm.command('ring', {help: 'RING: ring the museum bell.', cost: 1, words: ['chime']},
        function (who, args) {
            var room = who.location();
            if (String(room.getArea().Name()) !== 'CreatorMuseum') {
                cm.tell(who, 'There is no bell here.');
                return false;                     // no action cost
            }
            var data = cm.store('world');         // saved with the game
            data.bells = (data.bells || 0) + 1;
            cm.echo(room, who.name() + ' rings the bell (' + data.bells + ').');
        });

    // 2. a timed event: every game day at hour 8, once the clock gets there
    cm.at('hour 8', function () {
        cm.echo(cm.room('CreatorMuseum#0'), 'The curator opens the doors.');
    }, 'museum.open');

    // 3. a world event with a veto
    cm.on('save', function (who, slot) {
        return String(cm.var('*', 'INFIGHT')) !== 'yes';
    }, 'museum.nosave');
}
```

Test it:

```text
JS RELOAD init.js
RING
JS STATE world
```

`JS RELOAD init.js` rereads the file but does not run `onBoot` again; to run
the new registrations in a live game, call the functions from `JCONSOLE`, or
restart. Then `SAVE`, restart and `JS STATE world` to see that `bells`
persisted. The command and timer exist again because `onBoot` re-registered
them.

### 2. A stateful NPC (JScriptable)

A ferryman who asks for a fare, waits for payment and then carries the player
across. File `world/js/npc/ferryman.js` is the one shown under
[JScriptable](#jscriptable). Attach it to the mob:

```text
JScriptable LOAD=world:js/npc/ferryman.js
```

(as a behavior on the mob in its area file, or with `MODIFY MOB`). Notes on how
it works:

- `state` is the ferryman's own store, keyed by his instance ID, so a second
  ferryman has separate data and the data survives save/restore.
- `onSpeech`/`onGive` only fire for **other** characters' actions on him.
- `onTick` counts down three ticks and then forces the player east with
  `cm.cmd`. Because the crossing lives in `state` (JSON), it is still pending
  after a restore.
- Test without playing it out: `JCONSOLE @ferryman` then
  `.trigger SPEECH ferry please`, then `.inspect cm.store(me)`.

### 3. A custom behavior with saved parameters (JSBehavior)

A mob that regains a little health once per game hour and counts how many
times it did, saved in its own behavior text.

File `world/js/props/regen.js`:

```js
// parms.HEALS counts heals; parms.LAST holds the last game hour healed
function tick(host, tickID) {
    if (host.amDead()) return true;
    var hour = cm.clock().absHours();
    if (hour == parms.LAST) return true;          // already healed this hour
    parms.LAST = hour;
    parms.HEALS = (parms.HEALS || 0) + 1;
    host.curState().adjHitPoints(2, host.maxState());
    return true;
}
```

Give the mob the behavior `JSBehavior` with the parameters
`SCRIPT=world:js/props/regen.js HEALS=0 LAST=0`. After a few hours the
behavior text reads, for example, `SCRIPT=world:js/props/regen.js HEALS=3
LAST=12408`: that is how the counters persist through a save. A behavior is
used here because behaviors are ticked with their host. Stat changes need a
`Prop_JScript` instead (`affectPhyStats(host, affected, stats)` and the other
stat hooks), and those hooks must be cheap and free of side effects (see
[the hook table](#prop_jscript-and-jsbehavior)).

### 4. A custom property class (JS class)

A property class whose strength is set from its own text. The skeleton
follows the shipped template:

```js
// world/js/properties/ArmorWard.js
//extends com.planet_ink.coffee_mud.Abilities.Properties.Property
function ID() { return "ArmorWard"; }
function name() { return "an armor ward"; }
function accountForYourself() { return "Turns blows aside."; }

function affectPhyStats(affected, stats) {
    this.super$affectPhyStats(affected, stats);
    var power = Packages.com.planet_ink.coffee_mud.core.CMParms.getParmInt(this.text(), "POWER", 3);
    stats.setArmor(stats.armor() - power);       // lower armor value = harder to hit
}
```

Give it with `MODIFY` as any property: `ArmorWard POWER=5`. Class code has no
instruction time limit, so write it carefully, and keep stat overrides cheap
because they run on every stat recompute. For a skill or spell that is cast,
the data-driven way is `GenAbility` ([chapter 25](25-abilities-and-effects.md)),
whose script can use `<SCRIPT>` JavaScript. A JavaScript class extending
`StdAbility` is possible, but it must override the engine's `invoke`
overloads, and this chapter has no verified example of that.

### 5. A quest with JavaScript state

See the "Missing Cat" handlers in [Quests](#quests): `onStart(state)` picks a
random alley and loads the cat; `onWin(player)` pays by how many game hours
have passed, using `cm.clock().absHours()`.

### 6. A building script (JRUN)

```js
// resources/creator/mini.js: JRUN /resources/creator/mini.js
if (!cm.isBuilder()) throw new Error('Open the world with BUILDER=1.');
var room = builder.dig('north', null);       // a room north of where you stand
if (room != null) {
    builder.create('mob', 'Town Guard', room.roomID());   // a catalog mob you have
    builder.saveRoom(room.roomID());
}
```

Always build in builder mode and `SAVE` afterwards (see
[chapter 2](02-world-workflow.md)).

## Persistence notes

| Thing | Saved in the campaign? | Where it lives / what a reload does |
| --- | --- | --- |
| `cm.store` contents | Yes: `<JSSTATE>` in `world.xml` | `JS STATE` inspects; a restore clears stores not in the save |
| `world/js/state.json` | No (read for new games) | World file, part of the world checksum |
| `cm.after` timers and data | Yes (store `js:timers`) | Function is rebuilt from text after restore |
| `cm.every`, `cm.at`, `cm.on`, `cm.command`, `cm.verb` | No | Re-register in `onBoot`; a restart re-runs it |
| JS globals / module state | No | Lost at restart; `JS RELOAD` replaces scopes |
| `Prop_JScript`/`JSBehavior` `parms` | Yes, in the host's property text | Written back after each hook |
| `JScriptable` `state` | Yes (the host's store) | Survives reload |
| Quest handlers' `state` | Yes (`quest:name`) | |
| Background jobs | No | Cancelled on new game/restore |
| JS classes | No (they are code) | `JS RELOAD` recompiles; see migration rules |
| Approvals (`JSCRIPTS=1`) | `world/jscripts.ini` | Written in builder mode only |

A new game clears stores, timers and approvals' pending state and loads
`state.json`; a restore replaces stores from the save and forgets live timer
functions (rebuilt from text when due).

## Troubleshooting

| Symptom | Likely cause and fix |
| --- | --- |
| "A Javascript was not authorized" | `JSCRIPTS=1` and the body is not approved: `MODIFY JSCRIPT` in builder mode (or set `JSCRIPTS=2`). |
| `<SCRIPT>` silently does nothing | `JSCRIPTS=0`; the room is told nothing in that case. |
| "ReferenceError: x is not defined" with a line number | The line is the line of the `.script` file for a `<SCRIPT>` block. Check `JS ERRORS`. |
| "it ran longer than its time limit" | An infinite loop or a very big loop; raise `JSTIMELIMIT` only if the work is legitimate. Blocking I/O: use `cm.background`. |
| `me.Name().length` is a function / `===` fails | A Java string. Use `('' + x)` and `==`. |
| `x.indexOf is not a function` | The value is a Java object, not a string; call `String(x)` or `'' + x`. |
| Listener runs twice | Registered without a key from a scope that runs repeatedly, or from a loop: give a key. |
| Command/listener gone after restart | Code registrations are not saved: put them in `onBoot`. |
| `cm.after` callback fails after RESTORE | It used a captured variable; pass what it needs in `data`. |
| Stat hook flips values | A `Prop_JScript` stat hook is run each recompute; do not roll dice or count in it. |
| A hook stopped being called | It threw once; fix the error and set the text again (or `JS RELOAD`). |
| `JS RELOAD` reports "N incompatible instances kept (see log)" | The new class cannot accept saved STATE/ACTIVITY; fix the class or re-create the instances. |
| `cm.cmd(mob, 'pull lever')` does nothing | `cm.cmd` is forced and skips local verb interception; type the command. |
| `cm.store(mob)` empty after cloning | Clones get their own instance store by design. |
| JCONSOLE multi-line `<` never runs | End the block with `>` on its own line. |
| `JS STATE` "is for builder mode" | It needs `BUILDER=1` or `DEV=1`. |
| Backtick turned into `'` | In `JS HELP`, `.inspect` and cron scripts a backtick is read as an apostrophe; use another quote or a `+` concatenation there. |

## See also

- [Chapter 4: MOBPROG](04-mobprog.md), with the references
  [triggers](26-mobprog-triggers.md), [commands](27-mobprog-commands.md),
  [functions](28-mobprog-functions.md)
- [Chapter 8: Quests](08-quests.md)
- [Chapter 12: Generation](12-generation.md) (template JavaScript)
- [Chapter 13: Testing and release](13-testing-release.md) (fixtures run JS)
- [Chapter 23: Properties](23-properties-reference.md#prop_jscript) and
  [chapter 24: Behaviors](24-behaviors-and-masks.md#jscriptable)
- [Chapter 32: Engine programming](32-engine-programming.md)
- [State and persistence matrix](state-and-persistence.md)
- The older full manual [JavaScriptSUD.html](../../CoffeeMud/guides/JavaScriptSUD.html)
  and shipped help (`AHELP JS`, `AHELP JRUN`)
