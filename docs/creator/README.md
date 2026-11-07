# CoffeeSUD creator handbook

Build a game, test it as a player, and ship the world as files. This handbook covers
everything CoffeeSUD offers to world authors, scripters and engine developers: how to
install and run it, how to build places, creatures and items, how to script them, how
every game system works and is configured, and how the engine itself is put together.
It describes the code as it is now, including everything CoffeeSUD adds to CoffeeMud 5.11.

**New here?** Read [Installing and running](18-installing-and-running.md), then work
through [Your first world](01-first-world.md). After that, use the chapter for whatever
you are building, and the references when you need an exact name or parameter.

Conventions: commands in `text` blocks are typed in the game; `sh` blocks run in a
terminal from the repository root. Paths beginning with `world/` are relative to
`CoffeeMud/` unless stated otherwise. Example names such as `Harbour#2` are placeholders.

## Contents

### Getting started

| Chapter | What it covers |
| --- | --- |
| [Installing and running](18-installing-and-running.md) | Building, launching, every launch argument, normal/builder/DEV/CHECK/playtest modes, folders, console and windows, logs, troubleshooting, what CoffeeSUD removed from CoffeeMud |
| [Your first world](01-first-world.md) | A complete beginner tutorial: from an empty world to a small, tested, saved area |
| [Configuration and administration](19-configuration-and-administration.md) | `coffeemud.ini` and `world.ini`, security flags and permissions, GOD and developer cheats, admin commands, logs, maintenance |
| [World files and the builder workflow](02-world-workflow.md) | The world folder and every file format, builder mode, SAVE and the WORLD commands, UNDO, the catalog, editing files by hand |

### Building the world

| Chapter | What it covers |
| --- | --- |
| [Areas, rooms and exits](20-areas-rooms-exits.md) | Areas, every locale class, creating and linking rooms, exits, doors, locks and keys, field references, bulk tools |
| [Mobs](21-mobs.md) | Every mob class, the mob editor field by field, equipment, shopkeepers and other services, mounts, rejuvenation, the mob catalog |
| [Items](22-items.md) | Every item class and field, weapons, armor and wear locations, containers, magic items, materials, the item catalog, unique and rare items |
| [Crafting, gathering, housing and puzzles](07-items-crafting.md) | Recipes and crafting skills, gathering and farming, materials, homes and furniture, switches, locks, combinations and other interactive items |
| [Generation](12-generation.md) | The random-generation XML language tag by tag, GENERATE commands, generators, loot tables, affixes, names |

### Characters and abilities

| Chapter | What it covers |
| --- | --- |
| [Characters and progression](03-characters.md) | Races, classes, character creation, stats, levels and experience, training, skill trees, ranks, ageing, death and rebirth |
| [Abilities and effects](25-abilities-and-effects.md) | Skills, spells and every ability family, flags and costs, timed effects, custom abilities (GenAbility), poisons, diseases and traps |
| [Saves, party and the single-player model](31-saves-and-party.md) | The profile, the party, AS and SWITCH, windows, pause and turn-based time, SAVE and RESTORE, save rules, the save folder, restoring after the world changed |

### Scripting

| Chapter | What it covers |
| --- | --- |
| [MOBPROG scripting](04-mobprog.md) | The guide: attaching scripts, program structure, the execution model, control flow, variables and state, debugging, worked examples |
| [MOBPROG triggers](26-mobprog-triggers.md) | Reference: every trigger, when it fires, its arguments and bindings |
| [MOBPROG commands](27-mobprog-commands.md) | Reference: every script command and control statement |
| [MOBPROG functions and codes](28-mobprog-functions.md) | Reference: every condition and value function, expression rules and the `$` codes |
| [JavaScript](05-javascript.md) | The JavaScript runtime, where scripts run, the `cm` API, events, timers, stores, world hooks, JS-defined classes, Java interop |

### Game systems

| Chapter | What it covers |
| --- | --- |
| [NPCs and dialogue](06-npcs-dialogue.md) | Schedules, sentries, memory, affinity, escorts, hirelings, MudChat, TALK and topics, menus, barks, scenes |
| [Combat and encounters](29-combat-and-encounters.md) | How native combat works, the encounter toolkit (morale, boss phases, telegraphs, waves, downed and essential characters, threat, difficulty), encounter design, combat debugging |
| [Quests](08-quests.md) | The quest script format, quest commands, quest state, journal, markers, rewards, templates, boards, generated quests |
| [Society](09-society.md) | Clans and governments, factions, NPC organisations, law and crime, conquest, currencies, shops and banks |
| [World simulation](10-world-simulation.md) | Time and time modes, calendars, holidays, weather and climate, seasons, tides, darkness, breath, hazards, room states, resets |
| [Travel, vehicles and instances](30-travel-vehicles-instances.md) | Travel time, journeys, transport lines, taxis, fares, ships and mounts, stables, waypoints, instances |
| [Presentation and language](11-presentation-language.md) | Conditional descriptions, lore, journal, narration, maps, prompts and panels, colours, vocabulary, languages |

### Testing and release

| Chapter | What it covers |
| --- | --- |
| [Testing, debugging and release](13-testing-release.md) | Every developer tool, playtest files and assertions, checks in a pipeline, the repository test suites, releasing a world and keeping saves compatible |

### Optional modules

These systems are off unless a world selects them.

| Chapter | What it covers |
| --- | --- |
| [Optional modules](14-optional-modules.md) | Module manifests, selection, campaign state, versions, migrations, disabling |
| [Ability composition](15-ability-composition.md) | Tagged skills, supports, chains and burning |
| [Combat profiles](16-combat-profiles.md) | Versioned damage and defense rules and explained combat results |
| [Item modifiers and crafting](17-item-modifiers-crafting.md) | Tracked equipment modifiers and selective, protected crafting |
| [Named resources](34-named-resources.md) | Heat, focus and charges, with costs, gains and changes over time |
| [Causal triggers](35-causal-triggers.md) | Reactions to blocks and kills, with budgets, depth limits and saved cooldowns |
| [Reward escrow](36-reward-escrow.md) | Provisional, banked, forfeited and claimed rewards, with activity policies |
| [Encounter director](37-encounter-director.md) | Phased bosses and defense waves as one saved state machine, with budgets |
| [Expeditions](38-expeditions.md) | Charts, entry contracts, seeded instances with modifiers, attempts and recovery |
| [Progression tracks](39-progression-tracks.md) | Character disciplines, organization research and item mastery, with milestones and migration |
| [Loot policies](40-loot-policies.md) | Rarities, tier limits, budgets and a guarantee counter for tracked loot, with preview, sampling and LOOT |
| [The Ember Trial](examples/ember-trial/README.md) | A complete playable sample adventure using all of the above |

### Engine development

| Chapter | What it covers |
| --- | --- |
| [Engine programming](32-engine-programming.md) | Source layout and build, the object model, libraries, the message model, ticks and the game thread, writing new Java classes of each kind, testing |
| [Engine internals](33-engine-internals.md) | CoffeeSUD's own subsystems: saves, world files, the content cache, optional modules, instance IDs, script events, the party layer, the authoring checks |

## References

| Reference | What it holds |
| --- | --- |
| [Properties](23-properties-reference.md) | Every property, its hosts and full parameter syntax |
| [Behaviors and masks](24-behaviors-and-masks.md) | Every behavior and its parameters; the zapper mask language |
| [MOBPROG triggers](26-mobprog-triggers.md), [commands](27-mobprog-commands.md), [functions](28-mobprog-functions.md) | The complete scripting language |
| [World settings](world-settings.md) | The exact `world.ini` allowlist and precedence rules |
| [State and persistence](state-and-persistence.md) | What saves, what must be re-registered, how to design resumable interactions |
| [Cookbook](cookbook.md) | Complete recipes with prerequisites, checks and persistence notes |
| [Runnable examples](examples/README.md) | A small museum world, dialogue, a switch, a clue, an invented language, generation and tests |
| [Tool reference](tool-reference.md) | Generated snapshot of the shipped command, property and behavior help |
| [Tool permissions](tool-permissions.md) | Generated table of command permission gates |
| [Feature index](feature-index.md) | All 409 reviewed CoffeeSUD additions, their status, chapter, implementation and tests |

## Read by task

| You want to... | Read |
| --- | --- |
| Get the game built and running | [Installing and running](18-installing-and-running.md) |
| Make rooms, exits and areas | [Areas, rooms and exits](20-areas-rooms-exits.md), [First world](01-first-world.md) |
| Make creatures, shopkeepers, guards and bosses | [Mobs](21-mobs.md), [NPCs and dialogue](06-npcs-dialogue.md), [Combat and encounters](29-combat-and-encounters.md) |
| Make equipment, containers, keys and magic items | [Items](22-items.md), [Properties](23-properties-reference.md) |
| Make puzzles, crafting, gathering and homes | [Crafting, gathering, housing and puzzles](07-items-crafting.md) |
| Choose the opening, races, classes and advancement | [Characters and progression](03-characters.md) |
| Make custom spells, skills, poisons and traps | [Abilities and effects](25-abilities-and-effects.md) |
| React to events and keep state | [MOBPROG scripting](04-mobprog.md) and its references, [JavaScript](05-javascript.md) |
| Build quest arcs, jobs and rewards | [Quests](08-quests.md) |
| Build guilds, factions, laws and economies | [Society](09-society.md) |
| Set time, weather, seasons and hazards | [World simulation](10-world-simulation.md) |
| Add travel, ships, fast travel and instances | [Travel, vehicles and instances](30-travel-vehicles-instances.md) |
| Write descriptions, lore, maps and languages | [Presentation and language](11-presentation-language.md) |
| Generate dungeons, loot and names | [Generation](12-generation.md) |
| Control saving, the party and turn-based play | [Saves, party and the single-player model](31-saves-and-party.md) |
| Configure the engine and permissions | [Configuration and administration](19-configuration-and-administration.md) |
| Diagnose problems, automate tests and release | [Testing, debugging and release](13-testing-release.md) |
| Change or extend the Java engine | [Engine programming](32-engine-programming.md), [Engine internals](33-engine-internals.md) |

## What this engine gives you

Rooms are linked places, not a continuous tile grid; a movement step crosses an exit.
Areas collect rooms and carry calendars, climate, law, behaviors and properties. Mobs
are creatures, including player characters; items include equipment, containers, books
and vehicles. Races supply bodies and racial traits; classes supply progression.
Abilities can be active skills, spells, diseases, poisons or passive properties.
Behaviors supply ongoing AI. Scripts attach events and state to all of these. Quests
coordinate longer stories. The catalog stores reusable mob and item definitions.

You can author with data and editors, add MOBPROG, add JavaScript with direct engine
access, or extend Java. These layers work together: begin with an existing generic
class and properties, and add code only when the behavior needs it.

CoffeeSUD is single-player. One profile holds your characters, several of whom can be
in the world as a party; the game can pause or run turn by turn; saves are folders you
can copy; and the authored world lives in readable files rather than a database.

## Finding authoritative detail

Use `HELP` for player commands and `AHELP` for builder commands, properties and
behaviors. `AHELP BUILDING`, `LIST`, `STAT`, `JS HELP` and the editors show the classes,
fields and methods the running build supports. The feature index links the source and
the tests behind every CoffeeSUD addition.

The inherited CoffeeMud manuals in `CoffeeMud/guides/` ([Archon's Guide](../../CoffeeMud/guides/ArchonGuide.html),
[Game Builder's Guide](../../CoffeeMud/guides/GameBuildersGuide.html),
[Scriptable manual](../../CoffeeMud/guides/Scriptable.html),
[Programming Guide](../../CoffeeMud/guides/Programming.html) and
[Random areas](../../CoffeeMud/guides/RandomAreas.html)) still explain inherited
mechanics, but parts describe networking, MUDGrinder, SMTP and multiplayer administration
that CoffeeSUD removed. Where they and this handbook disagree, this handbook follows the
current code. The CoffeeSUD supplements [ScriptingSUD](../../CoffeeMud/guides/ScriptingSUD.html)
and [JavaScriptSUD](../../CoffeeMud/guides/JavaScriptSUD.html) are older companions to the
scripting chapters.

This handbook was rewritten and checked against the code in November 2026. The audit
findings from that work (mismatches between code and help text, and engine defects found
while documenting) are in [the documentation audit](../../audit/docs-audit-2026-11.md).
Regenerate the reference snapshots with `python3 tools/creator_docs.py --write`; check
links, coverage and freshness with `python3 tools/creator_docs.py --check`.
