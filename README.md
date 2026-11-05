# CoffeeSUD

CoffeeSUD is a single-player text adventure engine. It is a hard fork of
[CoffeeMud](https://github.com/bozimmerman/CoffeeMud) 5.11, the Java MUD
server. It keeps CoffeeMud's world simulation: races and classes,
thousands of abilities, combat, crafting, factions, law, quests, scripting
and random areas. It drops everything that exists only because many people
share one server. You play it on your own machine, in a terminal, with real
save games.

**Status.** The engine is playable and under active development. The
world it ships is two rooms: writing the world is the owner's job, using the
builder tools described below. Engine capabilities are being added step by
step from [`DEVPLAN.md`](DEVPLAN.md).

## What it changes from CoffeeMud

- **No server.** There are no network listeners, web server, mail, intermud
  or multiplayer channels. The game runs in the terminal you start it from,
  with logins and passwords removed.
- **A game clock.** Everything that happens over time runs on the game's
  own clock: cooldowns, effects, decay, scripts, quests and the calendar.
  - `PAUSE` stops the world.
  - `TIMEMODE TURNS` makes it turn-based, so the world moves only when you
    act.
  - `RANDOMSEED=` fixes the dice.
- **Save games.**
  - `SAVE name` writes the whole live world, including rooms, mobs, items,
    timed effects, quest progress, script state, weather and factions.
  - `RESTORE name` restores that campaign state. External world assets remain
    live; keep the matching world version for older campaigns. `SAVES` lists slots.
  - The launcher offers the saves when it starts.
- **The world is files.** Areas, the catalog, quests, scripts and custom
  races, classes, abilities and commands live as readable files in
  `CoffeeMud/world/`. You can edit them in the game or by hand. The
  database is only a cache rebuilt from them.
- **A profile and a party.** One profile holds all of your characters.
  - `SWITCH` plays another character.
  - Several characters can be in the world at once as a party, following a
    leader.
  - `AS name command` gives one of them an order.
  - With `WINDOWS ON`, each party member gets a window of its own.
- **Builder and developer tools.** `BUILDER=1` turns the game into the
  world editor, using CoffeeMud's in-game editors. It adds:
  - undo;
  - world checks and script checks;
  - playtest files you can replay;
  - tracing and debugging for scripts.
- **Optional world modules.** Creators can select versioned modules, validate
  dependencies and keep typed campaign state with migration/suspension rules.
  New modules default off; an example counter demonstrates the lifecycle.
  See the [module guide](docs/creator/14-optional-modules.md).
- **Optional composed abilities.** Versioned base skills and supports combine
  tags, damage, costs, timing, bounded chains and burning through an inspectable
  modifier pipeline. Characters retain independent choices and cooldowns across
  saves. See the [guide and example](docs/creator/15-ability-composition.md).
- **More for content authors.** CoffeeSUD adds new MOBPROG triggers and
  functions, and fuller JavaScript (Rhino, ES6). Quests gain:
  - a journal with objectives;
  - per-character status and outcomes;
  - repeats, rewards and deadlines;
  - credit shared by the party or the whole profile;
  - story templates and quest boards.

## Requirements

- A Java JDK, version 9 or newer. It is developed with JDK 21.
- Python 3, only for the test tools in `tools/`.
- Linux, macOS or Windows.

## Quick start

To play in this terminal (it compiles first; the first build takes about a
minute):

```sh
CoffeeMud/sud.sh
```

On Windows:

```bat
CoffeeMud\sud.bat
```

`sud.sh` takes `NAME=VALUE` settings that override `CoffeeMud/coffeemud.ini`
for one run. For example:

| Setting | Effect |
|---|---|
| `RESTORE=hero` | Load the save called `hero` without asking. |
| `RESTORE=NEW` | Start a new game without asking. |
| `TIMEMODE=TURNS` | Start in turn-based time. |
| `RANDOMSEED=42` | Fix the dice. |
| `BUILDER=1` | Edit the world itself instead of playing a copy. |
| `WINDOWS=1` | Give each party member a window. |

In the game, `HELP` lists the commands. These are the ones for a
single-player game:

| Command | What it does |
|---|---|
| `SAVE`, `RESTORE`, `SAVES` | Save games. |
| `PAUSE`, `RESUME` | Stop and start the game clock. |
| `TIMEMODE TURNS`, `TIMEMODE REALTIME` | Switch between turn-based and real time. |
| `PROFILE`, `SWITCH name` | Your characters. `LOGOFF` opens the list of them, where `NEW name` makes another. |
| `PARTY`, `AS name command` | Your party. |
| `QUESTS` | Your quests, objectives and journal. |

In builder mode, `AHELP BUILDING` is the place to start.

## Building a world

Run `CoffeeMud/sud.sh BUILDER=1`. The game then runs on the world's own
content and makes you an Archon. You build with the in-game editors:
`CREATE`, `MODIFY`, `GMODIFY`, `CATALOG`, `GENERATE` and the rest. Builder
`SAVE` writes your changes to `CoffeeMud/world/`; commit those files to
share them.

In a normal game, the same commands change only that game's copy of the
world.

Guides:

- **[CoffeeSUD creator handbook](docs/creator/README.md)**: the current front
  door for world authors and developers, with system chapters, a cookbook,
  runnable examples, tool/settings references and a complete feature index.
- [`docs/languages.md`](docs/languages.md): custom languages, comprehension and
  immersion, local vocabulary, and world-owned socials and help.
- [`CoffeeMud/guides/ScriptingSUD.html`](CoffeeMud/guides/ScriptingSUD.html):
  CoffeeSUD's additions to MOBPROG scripting and quests.
- [`CoffeeMud/guides/JavaScriptSUD.html`](CoffeeMud/guides/JavaScriptSUD.html):
  JavaScript in CoffeeSUD.
- `CoffeeMud/guides/`: CoffeeMud's own manuals (Game Builder's Guide,
  Archon's Guide and others). These still apply to the engine. Ignore their
  parts on servers, web pages and multiplayer administration.
- `CoffeeMud/coffeemud.ini`: engine settings, with comments. The
  [world settings allowlist](docs/creator/world-settings.md) identifies which
  rules a world can override in `CoffeeMud/world/world.ini`.

## Development

```sh
CoffeeMud/build.sh       # compile in place
tools/check.sh           # the checks for a commit: fix tests and the smoke test, about ten minutes
ENGINE=1 tools/check.sh  # plus CoffeeMud's own engine test suite, for the end of a step
tools/fixes.sh NAME...   # one or more of the fix tests in tools/fixtures/fixes
tools/smoke.sh           # the single-player path on its own
```

On Windows, `CoffeeMud\build.bat` compiles.

Ground rules (see [`PLAN.md`](PLAN.md)):

- Every commit keeps the build green and `tools/check.sh` passing.
- Changes to the engine's behaviour need a reason tied to single-player
  play.
- Removing code beats adding switches.

## Repository layout

| Path | What is there |
|---|---|
| `CoffeeMud/` | The engine: Java sources under `com/planet_ink/`, `resources/` (help, recipes, quest templates, random areas), `guides/`, and the launch scripts. |
| `CoffeeMud/world/` | The world a new game starts from: areas, catalog, quests, scripts, custom classes and settings. |
| `tools/` | Build checks, test runners and fixtures, save and world inspectors, playtest runner. |
| [`PLAN.md`](PLAN.md) | How CoffeeMud became a single-player game, phase by phase, with a progress log. |
| [`DEVPLAN.md`](DEVPLAN.md) | The engine capabilities and builder tools being added now, item by item. |
| [`LATER.md`](LATER.md) | Things wanted but out of the current scope. |
| [`audit/`](audit/README.md) | The audit of what a builder can and cannot do without Java, which `DEVPLAN.md` is built from. |
| [`DOCS/`](DOCS/README.md) | A chapter-by-chapter analysis of CoffeeMud's engine, written before the fork. |
| `archive/mudgrinder/` | CoffeeMud's MUDGrinder web editor, kept for reference (it went with the web server). |

## Licence and credits

CoffeeMud is © 2000–2025 Bo Zimmerman and its contributors. It is licensed
under the Apache License 2.0; see [`CoffeeMud/LICENSE`](CoffeeMud/LICENSE)
and [`CoffeeMud/NOTICE`](CoffeeMud/NOTICE).

CoffeeSUD's changes and additions are also under the Apache License 2.0.

CoffeeSUD is an independent fork and is not affiliated with the CoffeeMud
project. For CoffeeMud itself, see <https://github.com/bozimmerman/CoffeeMud>.
