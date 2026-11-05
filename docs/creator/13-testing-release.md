# Testing, diagnostics and release

[Handbook](README.md) · [Tool reference](tool-reference.md)

## Choose an isolation boundary

| Workflow | Use | What survives |
| --- | --- | --- |
| SANDBOX ENTER/PREVIEW | Inspect a catalog/template or generated trial | Scratch area is excluded from exports |
| PLAYTEST ON/OFF | Quick in-place mortal checks | Inventory, XP and world changes remain |
| PLAYTEST GAME | Play current builder snapshot independently | Disposable separate world/database/saves/process |
| DEV=1 normal game | Campaign debugging with real character state | Normal campaign slots; source world unchanged |
| PLAYTEST RUN / WORLD TEST | Repeatable world tests | Scratch campaign per file; results report asynchronously |
| Python playtest runner | Headless regression/CI | Scratch campaign per file and failing exit code |

PLAYTEST ON can select PARTY, KIT and DIFFICULTY. OFF heals and ends mortal
mode. PARTY MORTAL/ARCHON changes one active member in builder mode; ARCHON
grants the class through the existing manual. These powers are not a balanced
character-generation system. TESTCHAR and KIT produce representative builds.
The shipped DEV group includes GOTO, which can bypass movement restrictions.
The CLI world runner uses DEV for ASSERT diagnostics; verify door/permission
gating in a normal game too, or explicitly narrow GROUP_DEV for those tests.
BUILD runs one privileged command during a playtest; an editor requesting input
after that command returns can require ending mortal mode.

Headless PLAYTEST GAME prints the prepared folder and launch command. Remove
the temporary folder after testing. A separate launcher needs a separate save
directory: an ownership lock prevents two processes from writing the same
SAVEDIR. Character windows in one process share that process's campaign.

## Static checks and their limits

| Tool | What it checks / explains |
| --- | --- |
| WORLD PROBLEMS | Content skipped or partially loaded |
| WORLD CHECK | Cross-world errors/warnings, including selected domains |
| SCRIPTCHECK | Known syntax, argument shapes, common arity, blocks, undefined reads |
| PROPCHECK | Property options/types/ranges and references for supported schemas |
| QUESTCHECK | Quest commands, selections, files and embedded scripts |
| GENERATE CHECK | XML templates, classes, IDs, selectors, layouts, variables and JS |
| CHECKTEXT / PREVIEW | Markup, links, details and rendered views |
| CHATLINT / CHATTEST | Dialogue patterns, proven shadowing and simulated responses |
| VALIDATERECIPES | Recipe shapes and content references |
| VOCAB / LANGUAGE PREVIEW / COVERAGE | Replacement/translation output and dictionary coverage |
| ORGCHECK | Organization/faction/government cross-references |
| LAW TEST / PRICECHECK / PROGRESSION | Explain law, price and advancement decisions |
| PUZZLE CHECK | Inspect existing switches/plates/blockages/signals |

`WORLD CHECK ONLY=VOCAB,LORE,TEMPLATES,ROUTES,IDS` targets the newer asset
domains. Use WORLD CHECK with no filter for the complete running-build set.
The WORLD help and runtime usage list the domains; dedicated property checks
can append schemas for their current options. Errors can fail headless
checks. Warnings need author judgment, especially intentionally shared IDs,
optional stock resources and unstocked scaffold shops.

```sh
CoffeeMud/sud.sh CHECK=1
```

This loads a scratch new-game world, checks without a player, and exits with
failure for errors. Add WORLDDIR for a different world. Static checks do not
prove narrative reachability, combat balance, arbitrary JavaScript branches or
the behavior of every randomly generated combination.

Valid `LOAD=world:scripts/...` references currently hit a separate static file
resolution gap: runtime supports them, SCRIPTCHECK does not. Use the documented
[resource-overlay path](04-mobprog.md#current-file-path-lint-limitation) when a
file must also pass that checker. This is a known tool defect, not proof that
the script's runtime path is missing.

PROPCHECK can also flag valid dynamic state keys such as Prop_Switch's MSG_up
and MSG_down because its accepted-key extraction is based partly on literal
help examples. The [audit](../../audit/developer-tools-audit.md#open-property-key-validation-gap)
distinguishes this known warning from an actual unsupported parameter.

## Runtime inspection

Use VARS for MOBPROG state and JS STATE for JSON stores. SCRIPT/SCRIPTFIRE,
SCRIPTCALL and reset controls exercise specific scripts; SCRIPTTRACE follows
execution, SCRIPTPROF finds hot scripts. STAT BEHAVIORS provides bounded live
fields, TRACEB observes behavior transitions, and COMBATDEBUG exposes combat
calculations. VESSEL and LISTINSTANCES inspect transport/instances. WEATHER,
TIME, LOOKAS and HAZARDS preview the player's environment.

DEVLOG has terminal ON/OFF, LAST/history and filters. `DEVLOG WINDOW WARN
MODULE Scripting,Quest*` opens a display-only log view with independent filters;
`DEVLOG WINDOW OFF` closes it. Use terminal output when headless. MPDEBUG and
JavaScript debug write developer diagnostics. JS ERRORS and SCRIPTERRORS
retain source/host/line context. NOTE records an authoring note. GOD/DevCheats
can toggle powers in authorized builder/DEV games; disable them for balance tests.

Commands that simulate an action, invoke a script, set a puzzle state or change
weather can mutate the world. Read-only diagnostic output does not imply that
all commands in the same tool family are read-only.

## World tests

Place tests under `world/tests`. In the game:

```text
PLAYTEST RUN start.play
WORLD TEST
```

The runner only accepts the world's test directory and supported extensions.
It needs the developer checkout's `tools/playtest.py` and Python 3. The
`sud.python` system property can select the Python executable; defaults are
python3 on Unix and python on Windows. Results arrive asynchronously.

From the repository root:

```sh
python3 tools/playtest.py
python3 tools/playtest.py CoffeeMud/world/tests/start.play
```

`WORLD=/absolute/world` selects a different source world for CLI runs. Each
file receives its own world copy, fresh database and saves, seed 42 and turn
mode unless overridden. KEEP=1 retains passing scratch folders; failures keep
their transcript path. Separate files do not share a prior file's saved slot.

`.play` and `.test` use the same grammar:

```text
#char Wanderer,Human,Apprentice
#seed 42
look
#expect Obvious exits
#assert HERE New Area#0
get book
#assert HAS book
```

`#expect` and `#reject` inspect output for the preceding command step.
`#assert` uses the developer ASSERT command: HERE, HAS, VAR or QUEST checks.
`#wait N`, `#save slot`, `#builder`, `#new`, `#restore slot` and `#realtime`
control the scenario. Ordinary `#` lines are comments. Group a menu command and its answer before
the first expectation: assertion markers themselves use the shared input stream
and can otherwise be consumed as menu answers. These directives belong
to the Python runner, not a script attached to an NPC. See
[playtest.py](../../tools/playtest.py) for the exact current grammar.

JavaScript tests run through JRUN in a builder campaign:

```javascript
test('the starting room exists', function () {
    assert(cm.room('New Area#0') != null, 'missing start');
});
test('dice bounds', function () {
    var n = cm.dice(1, 6, 0);
    assert(n >= 1 && n <= 6, 'roll out of range');
});
```

Use `assert.equal(actual,expected)` for strict equality. Convert returned Java
strings with `String(value)` when appropriate. Syntax errors, thrown errors
and failed assertions fail the run. A test proving only that an object exposes
the field your setup just assigned is weak; prefer visible behavior and state
transitions. The [example pack](examples/README.md) supplies a playable test.

RECORDINPUT can capture a session for replay; edit sensitive or incidental input
before sharing. Input recording plus a fixed seed is not exact deterministic
session replay: that dedicated feature remains deferred.

## Save, migration and recovery tests

Test a **fresh process** restore at meaningful points: pending dialogue or
narration; a delayed action; active combat/boss phase; an assisted craft; a
journey/vehicle/instance; a quest deadline; and a changed room. Do not count a
same-process field check as proof that startup rebinding works.

SAVES offers inspection/copy/delete/diff and checkpoints/autosave tools; use
its help for exact forms. SAVEMODE, autosave policy and no-save properties can
restrict where/when players save. Save hooks may refuse. RESTORE with selective
FRESH policies is a migration tool, not a promise of compatibility for arbitrary
breaking changes.

New save generations have completeness hashes and retain a previous complete
generation. Startup can recover interrupted publication and preserve damaged
generations. Legacy version-1 saves remain readable; unsupported versions are
rejected before replacing live state. Directory sync is best effort where the
platform lacks support. See [reliability details](../../CoffeeMud/guides/ReviewReliability.md).

## Release workflow

1. Stop watchers. Review LIST UNSAVED, WORLD STATUS, WORLD DIFF and WORLD
   PROBLEMS. Reconcile external edits before exporting; review any `.builder`
   files rather than blindly forcing a save.
2. Save and commit authored content. Exclude scratch databases, temporary saves,
   dev-only content not intended for users, logs and test artifacts. Keep the
   required public resources, script libraries and referenced text files.
3. Run world/recipe/script/quest/generation checks and the world's tests against
   a clean source copy. Play NEW without builder/DEV privileges.
4. Verify supported older saves with IDs/aliases and explicit migration rules.
   Preserve accepted world versions when campaigns need exact assets. Record
   intended incompatibilities in release notes.
5. Package the world, compatible engine revision, runtime requirements, licenses,
   launch instructions and a recovery location. Test a clean installation.

For engine changes, build and run the repository's required checks.
`tools/check.sh` runs fixes/smoke; ENGINE=1 adds engine tests. Small machines
can use CHECKS_SERIAL=1 and FIXES_JOBS. Do not run multiple fixes harnesses
against the same `resources/_fixes` staging directory or compile concurrently
into one class tree. An isolated full checkout avoids these shared artifacts.
Documentation changes can use focused example and link/coverage validation.

Deliberate deferrals and the native-Java timeout boundary are listed in the
[audit](../../audit/developer-tools-audit.md). Current known engine baseline
failures are Spaceangles, Spaceinterception and Spaceinterception2. Keep a
baseline distinction in test reports; it does not excuse a new failure.
