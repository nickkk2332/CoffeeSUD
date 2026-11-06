# Runnable creator example: the museum

[Handbook](../README.md) · [Cookbook](../cookbook.md)

This pack builds three rooms, a menu-driven guide, a two-sided switch gate,
conditional text, a sensory clue, lore, first-entry text, a world language,
random strings and two JavaScript commands with saved state. It uses the
current engine APIs; there are no external services or packages.

The separate [optional-module example](optional-modules/README.md) demonstrates
default-off selection, a character-owned practice counter, dependencies and
fresh-process save compatibility without changing this museum or the default world.

The [ability-composition pack](ability-composition/README.md) adds a separately
enabled Ember bolt skill with chaining, burning and power supports.

The [Ember Trial](ember-trial/README.md) is Stage 4's complete playable sample:
seven rooms, training, support builds, equipment, scripted enemies, a boss and
saved completion. Its creation tool publishes a separate deployment ready for
ordinary play, using the opt-in Stage 1–3 systems.

## Automated tour

From the repository root, with a JDK on PATH:

```sh
python3 docs/creator/examples/check.py
```

The script makes an isolated deployment, starts the builder, runs the authoring
script, publishes with SAVE, runs ordinary-character `.play` and JavaScript
tests, then checks a save in a fresh game process. Failures retain transcripts;
`KEEP=1` also retains passing artifacts. It needs Python 3.8 or newer and the existing
launcher/test tools. It does not modify `CoffeeMud/world` or a user's saves.

## Build and explore by hand

```sh
python3 docs/creator/examples/make_world.py /tmp/sud-museum
CoffeeMud/sud.sh BUILDER=1 WORLDDIR=/tmp/sud-museum/world DBSERVICE=jdbc:fakedb:/tmp/sud-museum/db SAVEDIR=/tmp/sud-museum/saves TIMEMODE=TURNS
```

Choose a new destination; creation refuses to overwrite an existing directory.
On Windows choose an absolute local path, use `CoffeeMud\sud.bat`, and pass
those paths in the same NAME=VALUE options. Quote a complete option if its path
contains spaces. Keep database, world and saves separate from other launchers.

Follow builder character creation, then enter:

```text
JRUN /resources/creator/build.js
LOOK
WORLD MAP AREA IDS
SAVE
QUIT
```

Answer the builder's quit prompt. The build script intentionally refuses a
second build over the same museum. Edit it with normal tools after creation,
or make a fresh deployment. SAVE publishes it and the new START setting.
The setup script is a builder recipe, not a script to attach to the guide.

Start an ordinary new game using the same paths and `RESTORE=NEW`, leaving out
BUILDER=1. Try:

```text
SAY gate
1
PULL lever
EAST
LOOK inscription
LISTEN
READ tablet
LORE COAST
NORTH
MUSEUMBELL
SAVE museum
```

Quit and launch with `RESTORE=museum`. MUSEUMSTATUS should still show one bell,
the tablet's lore stays learned, and the gate stays open. Check a normal new
game too: it begins with the closed gate and no learned lore. The language
definition and dictionary are world content; learned proficiency is campaign
state. `LANGUAGE PREVIEW MuseumSpeech hello` is a builder diagnostic.

## Replay tests

After publishing, from the repository root:

```sh
WORLD=/tmp/sud-museum/world python3 tools/playtest.py /tmp/sud-museum/world/tests
```

In a builder/DEV session, `WORLD TEST museum.play` and `WORLD TEST museum.js`
use the same test files. The in-game commands report asynchronously. Each file
gets a fresh source-world copy, database and saves. The ordinary playtest covers
visible dialogue, gating, clues and first-entry behavior; the JavaScript test
checks both exit objects, conditional rendering, language loading and generation.

## Files to adapt

| File | Adapt for |
| --- | --- |
| [build.js](world/resources/creator/build.js) | Repeatable setup of rooms, exits, items, properties, NPC and generic language |
| [museum-guide.script](world/resources/progs/museum-guide.script) | Asynchronous MPMENU and answer branching |
| [init.js](world/js/init.js) | Registering commands at boot and persisting JSON state |
| [museum.xml](world/resources/randareas/museum.xml) | Random text and JS expressions in XML |
| [lore](world/lore/museum.ini), [narration](world/text/museum.txt) | File-backed text formats |
| [dictionary](world/vocab/languages/museum.txt) | World-owned word translations |
| [museum.play](world/tests/museum.play), [museum.js](world/tests/museum.js) | Player and API-level regression tests |

The narration file is supplied for the cookbook's narration recipe; it does not
automatically block the museum opening. Example room IDs and source IDs are
namespaced so they are easy to replace. Keep or migrate source IDs when adapting
a shipped world; assign new ones for independent clues.

## Known lint-path limitation

The pack uses `world/resources/progs/` and `LOAD=progs/museum-guide.script`,
which work for both execution and lint. Runtime also supports `world:scripts/`,
but SCRIPTCHECK currently reports those references as missing. The audit probe
reproduced this with the same guide script and confirmed that the resource
path passes. See the [audit](../../../audit/developer-tools-audit.md#open-script-lint-path-gap).

## Headless validation warnings

With the resource-overlay script path, CHECK=1 reports zero errors and four
warnings for this world. Two come from the retained starter area's old text
and its disconnection from the museum start. Two are known PROPCHECK false
positives for supported `MSG_up`/`MSG_down` switch options; the played/API tests
verify them. See the [validator audit](../../../audit/developer-tools-audit.md#open-property-key-validation-gap).
