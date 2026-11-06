# Optional ARPG and MUD systems: research and proposals

Research snapshot: **5 November 2026 (UTC)**. CoffeeSUD baseline: commit
`df43a4c7`, including the creator handbook and authoring audit.

**Recommendation:** extend CoffeeSUD with independent, opt-in rules modules.
The strongest first investments are composable ability modifiers, inspectable
combat rules, transactional item crafting, itemized expeditions, and progression
tracks that can belong to a character, relationship, organization, or profile.
These foundations support many different games. Individual leagues and guilds
then become content packs built from those foundations.

This is a design dossier, **not an implementation or a promise to implement
every proposal**. Names, example commands, formats, settings, and APIs in the
proposal chapters are illustrative unless explicitly identified as existing.
Use the [creator handbook](../../creator/README.md) for commands that work today.

Implementation update: the [optional-module foundation](../../creator/14-optional-modules.md)
now supplies module manifests, validation, scoped state and basic version
migrations. These establish the first Q01/Q02/Q06 contracts; feature-specific
ARPG validators, migrations and gameplay remain separate work.

Stage 2's [ability-composition slice](../../creator/15-ability-composition.md)
implements the first A01/A02 family: authored direct-hit skills, three example
supports, deterministic modifiers, bounded chaining/burning and version-pinned
character loadouts. It remains default off. This does not mark the broader
trigger, item, passive-tree or other research proposals complete.

Stage 3's [combat-profile slice](../../creator/16-combat-profiles.md) implements
the first A09/A04 contracts for composed direct hits and their burns: final-type
versus type-history conversion, explicit defenses, removable stat sources,
version-pinned profiles and inspection of actual resolved events. Native combat,
further ailments and recovery pools remain separate work. It is also default off.

Stage 4's [Ember Trial](../../creator/examples/ember-trial/README.md) combines
Stages 1–3 into an isolated playable sample with training, support builds,
defense equipment, scripted enemies, a boss and persistent completion. It adds
example content and authoring recipes rather than more engine mechanic families.

## Reading routes

| Start here if you want… | Guide |
| --- | --- |
| PoE's underlying character, combat, loot and progression systems | [Path of Exile 1 systems](poe1-systems.md) |
| The mechanics introduced over the game's lifetime | [PoE1 expansion and league history](poe1-history.md) |
| What PoE2 does differently, including Early Access changes | [Path of Exile 2](poe2.md) |
| Ideas from We MUD Together's 3Kingdoms guides | [3Kingdoms](three-kingdoms.md) |
| 3Scapes' guilds, existing features and advertised plans | [3Scapes](three-scapes.md) |
| Lost Souls' simulation, affiliations, skills and relationships | [Lost Souls](lost-souls.md) |
| Concrete features to select or discuss | [Proposal catalog](proposals.md) |
| Optionality, engine boundaries, persistence and delivery order | [Architecture and roadmap](architecture.md) |
| Examples of how the proposed tools would fit together | [Design cookbook](cookbook.md) |
| Research scope, source quality and unresolved questions | [Coverage and sources](coverage.md), [source register](sources.csv) |

## The first shortlist

These are recommendations for discussion, not a requirement to build everything
in this order. Proposal IDs remain stable so you can say “I want A03 and G07.”

| Proposal | Why it is useful | Fit |
| --- | --- | --- |
| A01–A04: ability tags, support composition, triggers, rule explanations | Make custom builds expressible and debuggable | Extend generic abilities and effect hooks |
| I01–I04: richer affix rules and transactional crafting | Make loot modification reliable enough to support many crafting styles | Extend affixes, recipes and item identity |
| G01–G04: expedition items, campaign graph, encounter director, choices | Support maps, dungeons, trials and league-like activities | Extend generation, instances, quests and waves |
| P01–P03: progression tracks, specialties, affiliation compatibility | Let guilds change how a character advances | Extend classes, ranks, organizations and prerequisites |
| P05: persistent companion bonds | Make companions a developing relationship and build choice | New reusable contract over existing followers |
| Q01–Q04: previews, validators, simulation and readable inspection | Keep combinatorial systems understandable to creators and players | Extend authoring tools; some simulation tools are still deferred |

An attractive small first content pack would contain one skill with three
supports, one nine-node build tree, one craftable equipment family, and one
branching expedition with a boss. That would exercise the main contracts before
adding dozens of mechanics.

## Version distinctions that change the proposal

* **PoE1:** the current researched major version is 3.29, *Curse of the Allflame*.
  Version 3.30, *Covens of Umbra*, is announced for 13 November; its reveal is
  scheduled for 6 November. It is not a released mechanics source in this snapshot.
  [Official announcement](https://www.pathofexile.com/forum/view-thread/4012420).
* PoE1's 3.29 update removes **socket colour matching as a requirement**, rather
  than removing coloured sockets altogether. Matching a coloured socket grants
  a benefit. A classic colour-restriction mode and a current permissive mode
  should therefore be different presets.
  [Official notes](https://www.pathofexile.com/forum/view-thread/3985332).
* **PoE2:** 0.5 introduced *Return of the Ancients* and 0.5.5 introduced the
  *Forbidden Rites* event. The published 1.0 date is **11 December 2026**; its
  unreleased content is not counted as current gameplay.
  [0.5 notes](https://www.pathofexile.com/forum/view-thread/3932540),
  [0.5.5 notes](https://www.pathofexile.com/forum/view-thread/4000864),
  [publisher's Steam listing](https://store.steampowered.com/app/2694490/Path_of_Exile_2/).
* **3Scapes:** its feature page explicitly labels Generations, Positioning,
  Primordials and World Events **upcoming**. They are interesting designs, not
  verified live systems. [Feature page](https://www.3scapes.org/world/features/).
* **Lost Souls:** its skills documentation explicitly warns that some listed
  skills have no implemented effect. A vocabulary entry is not evidence of a
  complete gameplay loop. [Skills](https://wiki.lostsouls.org/articles/skills).

## What “comprehensive” means here

The unit of coverage is a **mechanic or reusable design pattern**: how a system
works, how it changed, what is interesting, and what CoffeeSUD would need.
The dossier covers the named PoE1 league/expansion sequence, PoE2's major public
Early Access revisions, and the major public system directories of the three
MUD resources, with deeper examination of distinctive guilds and mechanics.

It is not a claim to have read every forum reply, all Lost Souls articles,
every room walkthrough, every unique item, or every numerical balance change.
Those are effectively unbounded catalogs. The coverage guide records sampling,
blocked pages, stale descriptions and questions requiring an in-game check.
Publicly documented mechanics are evidence of a design; only playing or
inspecting a game's server could establish that every advertised interaction
currently works.

The proposals use original CoffeeSUD terminology and examples. Source games'
names identify inspiration; their lore, prose, item lists and balance tables
are not proposed as bundled CoffeeSUD content.
