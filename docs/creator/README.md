# CoffeeSUD creator handbook

Build a game, test it as a player, and ship the world as files. This handbook is
for world authors, scripters, and engine developers. It describes CoffeeSUD's
current tools, including the additions beyond CoffeeMud 5.11.

**Start here:** [Your first world](01-first-world.md), then work through the
[cookbook](cookbook.md). Commands shown in `text` blocks are entered in the
game; `sh` blocks run in a terminal from the repository root. Paths beginning
with `world/` are relative to `CoffeeMud/` unless stated otherwise. Example
names such as `Harbour#2` are placeholders; create them or substitute your IDs.

## Read by task

| You want to… | Read |
| --- | --- |
| Make rooms, exits, creatures and reusable templates | [First world](01-first-world.md), [world files and editing](02-world-workflow.md) |
| Choose the opening, character creation, classes, skills and party rules | [Characters and progression](03-characters.md) |
| React to actions, change state, communicate between objects | [MOBPROG](04-mobprog.md) |
| Add commands, effects, AI, libraries or engine code | [JavaScript and extensions](05-javascript.md) |
| Build dialogue, monsters, bosses and defeat rules | [NPCs and encounters](06-npcs-combat.md) |
| Make equipment, puzzles, crafting, gathering and homes | [Items and production](07-items-crafting.md) |
| Build quest arcs, objectives, rewards and repeatable jobs | [Quests](08-quests.md) |
| Build factions, guilds, conquest, crime and services | [Society](09-society.md) |
| Set calendars, weather, travel, vehicles and instances | [Simulation and travel](10-simulation-travel.md) |
| Write descriptions, lore, senses, maps, languages and interface text | [Presentation and vocabulary](11-presentation-language.md) |
| Generate dungeons, encounters, loot and names | [Generation](12-generation.md) |
| Diagnose, automate tests, migrate saves and release a world | [Testing and release](13-testing-release.md) |
| Select optional modules and preserve their scoped campaign state | [Optional modules](14-optional-modules.md) |
| Author optional tagged skills, supports, chains and burning | [Ability composition](15-ability-composition.md) |
| Choose optional damage/defense rules and explain actual combat results | [Combat profiles](16-combat-profiles.md) |

## References and examples

- [Cookbook](cookbook.md): complete recipes, prerequisites, checks and persistence notes.
- [Runnable example pack](examples/README.md): a small museum world, dialogue,
  a switch, a conditional clue, an invented language, generation and tests.
- [Tool reference](tool-reference.md): current shipped command help and authoring
  property/behavior help, indexed by topic. It is a snapshot; `AHELP topic`
  also includes any schema supplied by the running build.
- [World settings](world-settings.md): the exact `world.ini` allowlist and
  precedence rules. Engine configuration has a different scope.
- [State and persistence matrix](state-and-persistence.md): what saves, what
  must be re-registered, and how to design resumable interactions.
- [Feature index](feature-index.md): all 409 reviewed additions, current status,
  chapter, implementation and regression references. Use this to find a less
  familiar feature without reading every chapter.
- [Audit and documentation coverage](../../audit/developer-tools-audit.md):
  what was checked, remaining tool boundaries, and verification of this guide.

## What this engine gives you

Rooms are linked places, rather than a continuous tile grid. A movement step
crosses an exit. Areas collect rooms and carry calendars, climate, law,
behaviors and properties. Mobs are creatures, including player characters;
items include equipment, containers, books and vehicles. Races supply bodies
and racial traits; classes supply progression. Abilities can be active skills,
spells, diseases, poisons or passive properties. Behaviors supply ongoing AI.
Scripts attach events and state to these objects. Quests coordinate longer
stories. The catalog stores reusable mob and item definitions.

You can author with data and editors, add MOBPROG, add JavaScript with direct
engine access, or extend Java. These layers work together. Begin with an
existing generic class and properties; add code when its behavior needs it.

The original review inventory records **388 implemented, one partial and 20
deferred**. This documentation audit also identifies integration gaps and a cached-catalog test failure,
described in the [audit report](../../audit/developer-tools-audit.md). “Implemented” identifies available behavior and regression coverage;
it does not promise that every imaginable combination is tested. JS2 remains
partial because arbitrary synchronous native Java cannot be safely interrupted.
The [feature index](feature-index.md#deferred-work) lists deliberate deferrals.
The proposed “executions” cutscene system is a design draft, not an available API.
The newer [optional-module foundation](14-optional-modules.md) is documented
separately from those 409 review entries. Its working example does not implement
the guild or companion features proposed in the research dossiers. The newer
[ability-composition slice](15-ability-composition.md) supplies opt-in direct-hit
skills and supports, separately from the original review inventory.
Stage 3 adds [optional combat profiles](16-combat-profiles.md) and resolved-event
traces for those skills and their burns.

## Finding authoritative detail

Use `HELP` for player commands and `AHELP` for builder commands, properties and
behaviors. `AHELP BUILDING`, `LIST`, `STAT`, `JS HELP` and the editors reveal
classes, fields and methods supported by the running build. The source and
fixtures linked in the feature index resolve unusual edge cases.

The older [Game Builder's Guide](../../CoffeeMud/guides/GameBuildersGuide.html),
[Scriptable manual](../../CoffeeMud/guides/Scriptable.html), and
[Programming Guide](../../CoffeeMud/guides/Programming.html) still explain many
inherited mechanics. CoffeeSUD's [MOBPROG supplement](../../CoffeeMud/guides/ScriptingSUD.html)
and [JavaScript API manual](../../CoffeeMud/guides/JavaScriptSUD.html) remain
detailed references. Server administration, networking, MUDGrinder, SMTP and
multiplayer operations in legacy manuals are not CoffeeSUD workflows.

This guide set was audited on 2026-11-05. Regenerate the reference snapshots with
`python3 tools/creator_docs.py --write`; check links, coverage and freshness with
`python3 tools/creator_docs.py --check`.
