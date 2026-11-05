# Path of Exile 1: the systems beneath the leagues

[Dossier](README.md) · [History](poe1-history.md) · [Proposal catalog](proposals.md)

The useful target is a game where characters, equipment and adventures can be
constructed from rules. CoffeeSUD already supports authored abilities, classes,
effects, affixes, recipes, encounters and instances. PoE suggests ways to make
their **combinations** first-class, inspectable and persistent.

## Character construction

PoE's [official overview](https://www.pathofexile.com/game) establishes three
important ideas: item-granted active skills, behavior-changing support gems,
and a shared passive graph with class-specific starting positions. Keystones
change rules; jewels modify the graph. The overview contains old statements
about currency, so it is not sufficient evidence for every current economy rule.

| Mechanic family | What is interesting | Proposed CoffeeSUD support |
| --- | --- | --- |
| Class starting positions | Class influences opportunity cost without owning every possible skill | A05: multiple roots, reachability, path refunds and prerequisites; retain ordinary classes as a simpler option |
| Active skill gems | Equipment and learned abilities become separate axes | A02/I05: an ability source can be a learned skill, socketed object, implant, patron or temporary reward |
| Supports | Change delivery, target behavior, cost, duration or conditions | A01/A02: typed transformations with compatibility checks; publish the final skill in plain language |
| Skill variants | A recognizable skill can have mechanically distinct versions | Versioned variant definitions rather than a proliferation of copied scripts |
| Ascendancies | Small specialized trees can transform a broad archetype | A06: optional secondary trees with a shared or separate point budget |
| Masteries | A cluster can offer a selectable specialization rather than another linear stat node | A05: choice groups, duplicate restrictions and preview of respec consequences |
| Ordinary, radius, timeless and cluster jewels | Equipment can add or reinterpret progression opportunities | A07: deterministic graph patches and stable node IDs; never silently rebind allocations after removal |
| Tattoos, omens and item trees | Progression can alter a node, one future event, or the equipment itself | I11/I13: explicit scope and consumption point; show what survives item transfer or character respec |
| Profile and league unlocks | Some progress belongs to an account within a rules environment | P01/P12: distinguish campaign, character, profile and season; do not grant cross-world power by accident |

Sources for later additions: [jewels, 2.0](https://poedb.tw/us/Version_2.0.0),
[timeless jewels, 3.7](https://poedb.tw/us/Version_3.7.0),
[cluster jewels, 3.10](https://poedb.tw/us/Version_3.10.0),
[mastery-era changes, 3.16](https://poedb.tw/us/Version_3.16.0),
[tattoos/omens, 3.22](https://poedb.tw/us/Version_3.22.0),
[transfigured gems, 3.23](https://poedb.tw/us/Version_3.23.0),
[Bloodlines, 3.27](https://poedb.tw/us/Version_3.27.0).

For a text game, show a tree as reachable neighborhoods, prerequisites and
searchable paths. A graphical tree can be an optional client, but every choice
must also work through menus or commands. An author should be able to create
a twelve-node discipline without designing a thousand-node graph.

### Build-defining rules worth making authorable

First, a compact class-pattern index. The
[official named class listing](https://www.pathofexile.com/ascendancy/classes)
and [database index](https://poedb.tw/us/Ascendancy_class) currently show the
following branches. The official page's prose still says 20 while its named
list contains 21; the roster and dated additions are more useful than that
stale headline. The last column contains **CoffeeSUD design prompts**, not a
complete specification of each source class's passives.

| Base | Named branches in the researched listing | CoffeeSUD design prompts |
| --- | --- | --- |
| Marauder | Juggernaut, Berserker, Chieftain | Durability, escalating rage, elemental/chieftain identity |
| Duelist | Slayer, Gladiator, Champion | Recovery through combat, counter-defense, ally protection |
| Ranger | Deadeye, Warden, Pathfinder | Delivery specialization, conditional elements, reusable reservoirs |
| Shadow | Assassin, Saboteur, Trickster | Precision/critical events, proxies/triggers, hybrid resource defenses |
| Witch | Necromancer, Occultist, Elementalist | Minion ownership, curses/debuffs, elemental interactions |
| Templar | Inquisitor, Hierophant, Guardian | Resistance rules, mana/construct play, party defense |
| Scion | Ascendant, Reliquarian, Luminary | Borrowed archetypes, item-rule-derived talents, equipped companion builds |

Historical references need adjustment: Warden replaces Raider in the Settlers
era; Reliquarian arrives in 3.28; Luminary arrives in 3.29. The
[Luminary definition](https://poedb.tw/us/Luminary) is particularly relevant to
CoffeeSUD: permanent, equipment-customizable mercenaries with active/reserve
roles demonstrate a companion build distinct from recruiting another player
character. A17/P05/I20 should keep those ownership categories explicit.
[3.25](https://poedb.tw/us/Version_3.25.0),
[3.28](https://poedb.tw/us/Version_3.28.0),
[3.29](https://www.pathofexile.com/forum/view-thread/3985332).

The separate [Bloodline catalog](https://poedb.tw/us/Bloodline_Ascendancy_class)
lists Chaos, Oshabi, Nameless, Catarina, Aul, Lycia, Olroth, Farrul, Delirious,
Breachlord, Saresh, Velka and Abyssal definitions. This is a definition catalog,
not certification that every historical league's unlock remains obtainable.
The shared point-budget idea belongs in A06; a boss/patron can grant access
without granting a second unrestricted tree's worth of free power.

The [keystone reference](https://www.poewiki.net/wiki/List_of_keystone_passive_skills)
and [conflict discussion](https://www.poewiki.net/wiki/Keystone) illustrate rules
that replace ordinary assumptions: life instead of mana, a one-life defensive
build, damage redirected through mana, or an accuracy/critical tradeoff. These
are indexed references, not a promise that the old numeric values are current.
[Blood Magic](https://www.poewiki.net/wiki/Blood_Magic) is a particularly clear
example of cost and reservation changing together.

| Original CoffeeSUD build template | Required contracts |
| --- | --- |
| Blood-funded healer | Alternate costs, payment safety, reservation and recovery ordering; A13–A15 |
| One-life shield mystic | Explicit vital-pool replacement and immunity rules; A09/A13 |
| Mana-armored scholar | Damage routing, concurrent spending and an emergency fallback; A13/A15 |
| Self-burning guardian | Self-damage distinguished from hits, sustainable recovery and an aura; A09/A13/A14 |
| Critical-trigger duelist | Attack/spell linkage, cooldowns, resource payment and bounded proc chains; A02/A03 |
| Corpse-powered summoner | Corpse custody, minion budgets, source ownership and legal kill credit; A14/A16/A17 |
| Poison specialist or single heavy burn | Explicit ailment stacking and snapshot rules; A12 |
| Rallying support captain | Aura/curse limits, formation, shared resources and contribution rewards; A14/P06/I20 |
| Unarmed artificer or attribute specialist | Alternative base-scaling sources and equipment restrictions; A01/A02/A09 |

The distinction between self-degeneration and incoming hits is demonstrated by
the [damage-taken trigger reference](https://www.poewiki.net/wiki/Cast_when_Damage_Taken_Support).
Triggering a spell from a critical attack is illustrated in the
[PvP mechanics reference](https://www.poewiki.net/wiki/Player_versus_player).
The proposal is reusable contracts for these interactions, not a bundled copy
of particular source skills or a promise that every combination is balanced.

## Combat is a pipeline, not a single damage formula

The following distinction is essential: attack/spell, hit/damage-over-time,
damage type and delivery method describe different dimensions. A fire spell
does not necessarily hit, and a physical hit does not necessarily use a weapon.
[Mechanics](https://www.poewiki.net/wiki/Game_mechanics),
[ailments](https://www.poewiki.net/wiki/Ailment),
[receiving damage](https://www.poewiki.net/wiki/Defense_mechanics)
(indexed excerpts; full wiki pages were blocked).

| Layer | Reference pattern | CoffeeSUD design consequence |
| --- | --- | --- |
| Base and added values | Weapon-derived attacks and independently scaled skills | State the origin of each contribution; calculate local item changes before global character changes |
| Modifier algebra | Increased/reduced values add within their class; more/less multiply | A01/A09: named stacking groups, order, caps, rounding and an explanation trace |
| Conversion and extra damage | Conversion moves a portion; “gain as extra” creates another portion | A09: a finite directed conversion graph, over-conversion policy and no accidental double counting |
| Accuracy, avoidance and criticals | Landing an attack and making it critical are separate decisions | Separate roll stages and reveal why an attack missed; allow deterministic alternatives for turn-based presets |
| Hits and ailments | Ailment damage has its own calculation; mitigation should not be applied twice | A12: store the ailment's source snapshot, stacking rule, duration and cleansing behavior |
| Penetration and exposure | Changing effective resistance differs from changing a resistance stat | Give each effect an explicit stage and floor; explain immunity and penetration interactions |
| Layered defense | Armor, evasion, block, energy shield, suppression, ward and recovery solve different problems | A13: selectable defense components with budgets, rather than enabling every layer in every world |
| Recovery | Instant gain, regeneration, recharge, recoup and leech have different timing | Store outstanding recovery obligations, caps and cancellation policy; prevent infinite self-hit loops |
| Resource reservation | Persistent effects compete with resources available for active use | A14: reserve/release atomically, including after death, form change and equipment swap |
| Charges and combat states | Build, spend, expire or transform temporary resources | A15/A20: named counters, gain/spend events, maximums, decay and out-of-combat rules |

[Conversion](https://www.poewiki.net/wiki/Damage_conversion) describes one-way
type conversion and its distinction from extra damage; PoE1's original damage
type history should not automatically become PoE2's rule. [Leech](https://www.poewiki.net/wiki/Leech)
describes per-instance and aggregate recovery limits. Exact equations and
numeric caps should belong to a versioned rules preset, not scattered scripts.

CoffeeSUD's existing formulas and properties can express many individual
effects. The new requirement is a **common composition contract** so a support,
class passive, item affix and room hazard agree about order and ownership.
The architecture guide gives a proposed calculation pipeline and example.

## Delivery methods and battlefield objects

PoE distinguishes direct casting, attacks, projectiles, chaining, area effects,
channeling, brands, minions, totems, traps and mines. Proxies introduce ownership
questions: a trap, a minion, and a totem borrowing a character's skill should
not inherit every stat identically.
[Gem tags](https://www.poewiki.net/wiki/List_of_skill_gems_by_gem_tag),
[totems](https://www.poewiki.net/wiki/Totem),
[mines](https://www.poewiki.net/wiki/Mine),
[traps](https://www.poewiki.net/wiki/Trap),
[brands](https://www.poewiki.net/wiki/Brand) (indexed excerpts).

For CoffeeSUD, A11/A16/A17 should define:

* A delivery geometry: current room, adjacent rooms, a directed path, a radius
  over a room graph, or optional local positions inside an encounter.
* A collision and line-of-sight policy. “Three projectiles” needs a meaningful
  rule for repeated hits on one target, closed exits and friendly targets.
* An owner, controller, source skill and credit recipient. Damage credit,
  threat, experience, loot eligibility and criminal responsibility are separate.
* A snapshot/live-inheritance policy. Re-equipping a weapon must not duplicate
  a summoned item's value or strand unsupported effects.
* An actor/tick/message budget. A summon build should not flood a terminal with
  fifty repeated attacks every tick.

Warcries, guard skills, stances, banners, curses/marks, auras/heralds, movement
skills, retaliation and Vaal skills can then use the same action, trigger,
resource and reservation primitives. Their individual historical balance
revisions do not justify separate hardcoded subsystems.

## Loot and item construction

[Items](https://poedb.tw/us/Item) illustrates PoE's unusually broad itemization:
equipment, currency, access items and collectible rewards. Some text on this
general page is historical, including prophecies; current access must be checked
against version notes. The important transferable principle is that an item
can be a **reusable rules object**, not only a weapon or consumable.

| Family | Reference idea | Authoring proposal |
| --- | --- | --- |
| Rarity and item level | A base's identity differs from its available modifier pool | I01/I02: explicit base, generation level, requirements, rarity and pool |
| Prefixes/suffixes and groups | Capacity, eligibility, weighting and mutually exclusive modifiers constrain outcomes | Extend existing affixes with groups, tiers, tags and a reasoned eligibility report |
| Implicit, explicit, enchantment and special modifiers | Different layers have different replacement/protection rules | Preserve layer identity through every craft; an enchantment is not an extra ordinary suffix |
| Influenced, fractured and synthesized items | Some origins unlock modifiers; some modifiers are locked; some states exclude others | I01/I04/I08: capability matrix checked before spending materials |
| Corruption and mirrored copies | Powerful irreversible changes and constrained duplicates | Snapshot outcomes, mark non-editable states, preserve identity and prevent copying transaction receipts |
| Quality, catalysts and materials | Quality may target particular properties rather than one global bonus | I09: named quality channels and material properties with clear consumers |
| Sockets and links | Equipment can carry behavior and connectivity | I05: typed receptacles; choose whether topology, color, capacity or compatibility matters |
| Unique items | A distinctive rule can enable an entire build | Build-changing properties with tradeoffs; rarity must not imply universally superior power |
| Set-like collections | Reassembling a collection or collecting variants can be its own objective | I19: extend existing item sets and journals; collection completion need not grant mandatory combat power |

[Modifier](https://www.poewiki.net/wiki/Modifier),
[fractured items](https://www.poewiki.net/wiki/Fractured_item),
[synthesized items](https://www.poewiki.net/wiki/Synthesised_item),
[eldritch implicits](https://www.poewiki.net/wiki/Eldritch_implicit_modifier)
(indexed excerpts) show why a single “random enchantments” list is insufficient
for a deep crafting game. CoffeeSUD's existing affix system is still the right
starting point; these are additions to its contracts.

## Crafting styles, including historical ones

The catalog should support the following **operation families**, with each
world deciding which are available:

| Operation | Useful decision | Proposed representation |
| --- | --- | --- |
| Add/remove/reroll a modifier or its values | Spend to improve an incomplete item | I03/I04, transactional operations with eligibility and cost previews |
| Guaranteed-tag, weighted or blocked-tag crafting | Choose a target property while accepting uncertainty elsewhere | I06, explicit pool transformations |
| Bench and meta-crafting | Add a chosen property or constrain what later operations may change | Protected groups and capacity accounting; no hidden exception chains |
| Fossil/resonator-style recipes | Combine ingredients that change the random distribution | A finite composition of weights, exclusions and guarantees |
| Beastcrafting and unveil choices | Acquire a special operation through an encounter or discovery | G09/I07, knowledge unlock plus a separate cost transaction |
| Recombination and synthesis | Sacrifice multiple items for a new combined result | I08, consume inputs once; record the inherited and generated parts |
| Split, imprint and restore | Trade flexibility against duplication restrictions | I08, explicit lineage and copy restrictions across saves |
| Vendor recipes | Sell a meaningful combination for a deterministic result | Reuse recipe validation and add a merchant transaction adapter |
| Spatial graveyard or adjacency crafting | Placement changes the output distribution | I10, an optional layout interpreter rather than a mandatory universal crafting screen |
| Runes, grafts, tattoos, omens and weapon trees | Move build decisions outside ordinary equipment affixes | Typed progression sources, not ad hoc hidden item flags |

[Crafting](https://www.poewiki.net/wiki/Crafting),
[vendor recipes](https://www.poewiki.net/wiki/Vendor_recipe_system),
[Harvest](https://poedb.tw/us/Version_3.11.0),
[Sentinel](https://poedb.tw/us/Version_3.18.0),
[Necropolis](https://poedb.tw/us/Version_3.24.0),
[Settlers](https://poedb.tw/us/Version_3.25.0).
The design should preserve interesting choices without making large public
trading markets necessary to obtain ordinary crafting inputs.

## Flasks, economy and presentation

Charge-based, modifiable [flasks](https://www.poewiki.net/wiki/Flask) turn recovery
and utility into equipment decisions; charge gain can itself depend on combat.
Later updates added automation options. A18 should provide reusable reservoirs,
refill events and optional activation conditions, with one action budget and
clear priority when several conditions happen together.
[Expedition-era changes](https://poedb.tw/us/Version_3.15.0).

Tinctures add weapon coatings with upkeep and their own activation rules.
Treat them as a distinct equipment/resource policy rather than assuming every
temporary item buff is a flask. A15/A18 can provide the shared machinery while
keeping their costs, duration and mutual restrictions separate.
[Settlers-era changes](https://poedb.tw/us/Version_3.25.0).

Currency items are simultaneously crafting inputs and trade instruments;
specialized fragments, cards and scrip provide targetable rewards. Gold and
the currency exchange arrived later, and asynchronous item trading arrived
after that. A local CoffeeSUD campaign needs useful vendors and deterministic
paths to essential components. I14/I15 must permit local NPC markets without
requiring a remote economy or online services.
[Settlers](https://poedb.tw/us/Version_3.25.0),
[Keepers](https://poedb.tw/us/Version_3.27.0).

Loot filters are a substantial usability feature, with ordered conditions and
display actions. The [official filter reference](https://www.pathofexile.com/item-filter/about)
is a better inspiration than burying unwanted items in an unreadable room
description. I16/Q07 should support plain-text grouping, comparisons, tagged
warnings, a dry-run explanation and an accessible way to inspect hidden loot.
Filtering presentation must not silently destroy items.

## Endgame, modes and social systems

G01/G02 separate a dungeon's **destination**, **difficulty item**, **modifiers**,
**progression**, and **entry contract**. PoE's repeated Atlas redesigns show why
those should not be welded together. Allow the world author to choose a fixed
map, discoverable graph, endless frontier or storyline-driven network. Add
targetable boss fragments, finite entry attempts, invitations, influence layers,
content-specializing passives and optional mastery loadouts only as needed.

P12/P13 cover Standard, Hardcore, solo self-found, private rule variants,
seasonal starts, challenge milestones and leaderboards. The [league help](https://poedb.tw/us/League)
describes parent-league migration and self-found restrictions. For CoffeeSUD,
the defaults should preserve existing campaigns; a season reset must create a
new progression scope, never erase an unrelated save. Single-player runs can
have personal challenge records without competitive ladders.

Co-op also needs individual reward rights, safe entry/exit rules, explicit
scaling, and support builds that do more than multiply party damage. Existing
CoffeeSUD profile characters, parties and windows are not six independent
online players. The same authoring tools should work for one controlled hero,
a local party, or a future multiplayer host without assuming identical input
and timing models.
