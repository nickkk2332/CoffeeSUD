# JavaScript and engine extensions

[Handbook](README.md) · [Full API](../../CoffeeMud/guides/JavaScriptSUD.html)
· [JS feature index](feature-index.md#js)

## Choose an extension layer

| Layer | Best fit | Persistence contract |
| --- | --- | --- |
| `<SCRIPT>` inside MOBPROG / `MPJSFILE` | Short calculations and engine calls in an existing event | Explicit store/host state |
| `JScriptable` | An object scripted wholly in JavaScript | Handler state as documented; explicit JSON for durable data |
| `Prop_JScript` / `JSBehavior` | Reusable property/behavior file and per-instance parameters | Changes to `parms` written back to host text |
| `world/js/init.js` | World lifecycle, global events, commands and timers | Re-register code; save data in stores |
| `world/js/[kind]/` class | New engine subtype with overrides | Declare STATE/ACTIVITY where required |
| Native Java | New engine interfaces, infrastructure or performance-sensitive logic | Implement the engine's save/state hooks |

Rhino runs these scripts, not Node or a browser. ES6 features supported by the
bundled Rhino are available, but npm modules, DOM APIs and Node filesystem
globals are not implied. Java interop is available. These scripts are trusted
world code, not a security sandbox for hostile content.

## The shared API

Every normal game JavaScript scope gets `cm`, `sud`, `require`, `include`,
`gen`, `debug`, and Java package access. A background worker has a deliberately
different scope. Important helpers:

| API | Use |
| --- | --- |
| `cm.host()`, `cm.here()`, `cm.room(id)`, `cm.area([name])` | Locate the host and places |
| `cm.findMob(name[,where])`, `cm.findItem(name[,where])` | Find a live object; names can be ambiguous |
| `cm.loadMob(catalogName[,room])`, `cm.loadItem(catalogName[,where])` | Place a catalog copy under catalog rules |
| `cm.tell(who,text)`, `cm.echo([room,]text)`, `cm.say(who,text)` | Output or actual speech |
| `cm.cmd(who,line)`, `cm.mp(lines)` | Forced game commands or the MOBPROG bridge |
| `cm.dice('3d6+2')`, `cm.chance(25)` | Campaign randomness |
| `cm.now()`, `cm.ticks()`, `cm.clock()`, `cm.weather()` | Game time and world wrappers |
| `cm.var(holder,name[,value])`, `cm.stat(object,code)` | Script variables and engine stats |
| `cm.store(key)` | Saved JSON-compatible structured data |
| `cm.law()`, `cm.shop(keeper)`, `cm.bank(banker)`, `cm.lib` | Law, economy and direct library access |

Call `JS HELP cm`, or `JS HELP expression`, to inspect methods in the running
build. The [full manual](../../CoffeeMud/guides/JavaScriptSUD.html) documents
the clock, weather, law, shops, banks, `sud` aliases and uppercase MOBPROG
bridge functions. A helper returning a mob/room returns a live Java object;
direct changes may require stat recovery, messages or database/world-save calls.
Prefer normal commands and library operations when their checks are desired.
`cm.cmd` is analogous to MPFORCE: it calls the immediate forced-command path.
Object-local verbs intercept queued player input, so they are not exercised by
that helper. Use played input to test local verbs; the example pack demonstrates
the explicit queue API for an API-level test.

`<SCRIPT>` has event helpers such as `host()`, `source()`, `target()`, `msg()`,
`cancel()` and uppercase MOBPROG functions. JRUN/JCONSOLE instead provide
`mob()`, `here`, `area`, `target` and `me`; JRUN also provides `builder`.
Do not copy a recipe between these scopes without adapting the bindings.

## Files and libraries

```javascript
// world/js/lib/prices.js
exports.discount = function (price, percent) {
    return Math.max(0, Math.floor(price * (100 - percent) / 100));
};
```

Use `require('prices').discount(20, 10)`. Resolution checks `world/js/lib/`
before `resources/js/lib/`. Libraries export through `exports`; require is not
an npm dependency loader. `include('world:js/file.js')` executes a file in the
current scope. Library scopes can retain ordinary runtime state, but that state
is not a campaign save. Existing callers can retain old imports after reload.

JS execution follows `JSCRIPTS`: 0 disables it, 1 requires approval of bodies,
and 2 allows trusted world scripts. When approval is enabled, use `MODIFY
JSCRIPT` for the actual script body. Translation hooks and generator JavaScript
use this policy too. Engine-level JS runtime settings belong in
`coffeemud.ini` or launch overrides, not an arbitrary `world.ini` key.

## Durable state, code and timers

```javascript
var state = cm.store('world');
state.visits = (state.visits || 0) + 1;
```

Store numbers, strings, booleans, arrays and plain objects. Functions and Java
game objects are not durable JSON data. Use a room ID, an authored key or a
persistent instance ID and resolve the object again. `cm.store(mobOrItem)`
uses its persistent instance identity; moving/renaming keeps the store and
cloning separates it. Explicit string keys deliberately share data. Old
unambiguous name stores migrate; ambiguous legacy data stays under its old key.
`world/js/state.json` supplies initial stores for a new game. `JS STATE`
inspects them; builder forms can replace/delete a store.

Register events in `init.js`'s `onBoot`:

```javascript
function onBoot() {
    cm.on('enterRoom', function (who, room, from) {
        if (who.playerStats() != null)
            debug(who.Name() + ' entered ' + room.roomID());
    }, 'creator.visit-log');
}
```

Explicit listener keys replace earlier registrations. Events cover time,
weather, save/restore/newgame/switch, deaths, levels, quests, entry, items,
factions and turns. `beforeDeath`, `beforeEnterRoom` and save listeners can
veto their operation by returning false. Keep them quick and avoid triggering
the same operation recursively. Listeners, commands and verbs are code and
are not saved; re-register them at boot. Lifecycle hooks include onBoot,
onNewGame, onRestore, onBeforeSave, onPlayerEnter, onSwitch and onReload.

`cm.every(10, fn, key)` repeats by ticks; strings such as `'6 hours'` use
game time. `cm.at('day 7 hour 8', fn, key)` uses calendar rules. Register
both at boot. `cm.after(3, fn, data)` is a saved one-shot continuation;
after restore its function is reconstructed from text. It must use `data`
and `cm`, not captured local variables. `cm.stopTimer(key)` cancels a timer.

## New commands, local verbs and effects

`cm.command(name, options, function(who,args){…})` registers a command with
help, aliases (`words`), action cost and combat cost. `cm.verb(thing, phrase,
function(who,rest){…})` registers a local phrase; return false to permit normal
handling. Both should be installed from onBoot. MOBPROG VERB_PROG is an
alternative for local object actions.

Attach `Prop_JScript` with `SCRIPT=world:js/props/example.js POWER=2`, or
`JSBehavior` with the analogous script parameter. Hooks include okMessage,
executeMsg, tick, onAdded and onRemoved. Prop_JScript also has affectPhyStats,
affectCharStats and affectCharState. Stat hooks can run frequently; calculate
from base state, keep them fast, and avoid granting rewards or consuming dice
every time stats recover. `parms` contains the per-instance settings.

For JS classes, directories include abilities, properties, behaviors, items,
mobs, races, charclasses, commands, common, locales, exits and areas. Start
from the commented [bundled templates](../../CoffeeMud/resources/js/templates).
Declare state the loader can serialize and migrate. A custom race/class may
combine a generic data definition with a small scripted hook rather than
reimplementing all of the underlying Java interface.

## Reload and debugging

`JS RELOAD` reloads changed files; `JS RELOAD file` chooses one and `ALL`
chooses all. Syntax errors report source/line and leave the prior definition
running. Compatible replacements migrate declared STATE/ACTIVITY, effect
duration, proficiency, invoker and saved flags. Incompatible live instances
keep their old class and appear in the report. Arbitrary custom Java fields
are not inferred. Test both reload and a clean restart before release.

`JCONSOLE @here`, `@area`, `@me` or an object name binds an interactive
console to that host. `.inspect`, `.trigger`, `.load`, `.reset`, `.time`,
`.history` and `!n` support exploration. `.trigger` simulates an event and can
change state. `JS ERRORS`, `DEVLOG` and `debug(value)` provide diagnostics;
debug is silent in an ordinary non-DEV game.

## Blocking work and execution limits

Interpreted scripts have instruction budgets: ordinary scripts use
JSTIMELIMIT, JRUN uses JSTIMELIMIT_JRUN (shipped help describes defaults of
2 and 30 seconds). This is not a safe forced timeout for arbitrary native
Java calls or compiled `//extends` classes while they touch live state.

Use `cm.background(worker, JSONdata, timeoutMs, callback)` for blocking work.
The worker runs in a fresh scope with no `cm`, game objects or captured closure.
The callback receives `(error,result)` on the game thread; apply world changes
there. The job exposes cancel/done/error/resultJSON. The pool has two daemon
workers and a queue of 32. Timeouts discard late results and request interruption;
native operations ignoring interruption may retain a worker. Set explicit I/O
timeouts. Jobs/callbacks are cancelled on new game/restore and are not saved.
This supplies bounded background integration, not a dedicated LLM service.

## Native development

Use the [Programming Guide](../../CoffeeMud/guides/Programming.html) for engine
interfaces. Source is under `CoffeeMud/com/planet_ink/coffee_mud/`; core covers
shared runtime, Libraries services, and Commands/Abilities/Behaviors/etc. contain
the corresponding types. World-owned native definitions use the supported
world class loader; changing a Java binary requires restart rather than ASSETS
reload. Match the current interfaces and licensing when distributing code.

Build with `CoffeeMud/build.sh` or `CoffeeMud\build.bat`. Do not run concurrent
compilers against the same class tree. Add meaningful regression coverage for
new engine behavior, exercise save/restore for live state, and use
`ENGINE=1 tools/check.sh` for core changes. Three space-test baseline failures
are tracked in the [audit](../../audit/developer-tools-audit.md); a new failure
still needs investigation.
