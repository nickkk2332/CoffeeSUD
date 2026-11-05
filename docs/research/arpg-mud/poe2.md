# Path of Exile 2: a separate design and a moving target

[Dossier](README.md) · [PoE1 systems](poe1-systems.md) · [Proposals](proposals.md)

**Snapshot:** Early Access, through the researched 0.5/0.5.5 content families.
The [publisher's Steam page](https://store.steampowered.com/app/2694490/Path_of_Exile_2/)
gives 6 December 2024 as the Early Access launch and 11 December 2026 as the
planned exit. It describes four current acts and further campaign/class content
as planned. This dossier does not assume the full release has already happened.

## Public development sequence

| Revision | Verified design changes | What CoffeeSUD should learn |
| --- | --- | --- |
| 0.1, December 2024 | Six classes with two ascendancies each; three acts repeated through Cruel; a large shared passive tree and endgame | Keep the initial rules profile available for historical comparison, not as the current preset. [Launch notes](https://poe2db.tw/us/Version_0.1.0) |
| 0.2, Dawn of the Hunt, April 2025 | Huntress, additional ascendancies, spear/buckler play and broader build/endgame revisions | Add weapon-specific action models through capability definitions. [0.2 notes](https://poe2db.tw/us/Version_0.2.0) |
| 0.3, The Third Edict, August 2025 | Nonlinear Act Four, temporary interludes replacing Cruel, Abyss encounters and desecrated modifiers; support tiers/Lineage supports; removal of the one-copy-per-character support restriction | Version support legality and campaign structure independently. [0.3 notes](https://poe2db.tw/us/Version_0.3.0) |
| 0.4, The Last of the Druids, December 2025 | Druid, Oracle/Shaman, shapeshifting talismans, Fate of the Vaal temple construction; Abyss becomes core | Separate a weapon-granted form from race, and treat an evolving dungeon layout as saved gameplay. [0.4 notes](https://poe2db.tw/us/Version_0.4.0) |
| 0.5, Return of the Ancients, May 2026 | Runes of Aldur encounters select recipes and wave difficulty; Runeforging and Runic Ward; ocean expeditions; new endgame quests and progression; Martial Artist/Spirit Walker | Compose crafting, encounter selection and campaign projects. [Official notes](https://www.pathofexile.com/forum/view-thread/3932540) |
| 0.5.5, Forbidden Rites, September 2026 | Campaign Ritual chains, Wildwood access, individual party tribute; Trial of Chaos can be left/resumed and pays room rewards; Runes of Aldur elements become core | Resume support, reward ownership and understandable modifiers are gameplay features. [Official notes](https://www.pathofexile.com/forum/view-thread/4000864) |
| 1.0, announced for December 2026 | Full release and further content remain future commitments in this snapshot | Record announced features separately; revisit after release instead of inventing final rules. [Publisher listing](https://store.steampowered.com/app/2694490/Path_of_Exile_2/) |

Small patches matter too. A mechanic's launch restriction may disappear months
later. For example, a guide repeating the original support-copy restriction
would now give a false account of build freedom. The same applies to old
descriptions of repeating the campaign on Cruel or the original Atlas point
progression.

## Classes are examples of capabilities, not an engine checklist

Current community references list eight playable bases and 22 ascendancies;
the [class reference](https://exiledb.com/classes) labels its data 0.5.5. The
last-minute and unreleased class material should be rechecked against live
official notes before building a faithful replica.

| Base | Referenced ascendancies | Useful authoring pattern |
| --- | --- | --- |
| Warrior | Titan, Warbringer, Smith of Kitava | Heavy control, battlefield objects, equipment/forging identity |
| Ranger | Deadeye, Pathfinder | Projectile delivery and consumable/resource specialization |
| Witch | Infernalist, Blood Mage, Lich | Alternate costs, minion relationships and unusual defensive rules |
| Sorceress | Stormweaver, Chronomancer, Disciple of Varashta | Elemental interactions, timing manipulation and a specialized summoner |
| Mercenary | Witchhunter, Gemling Legionnaire, Tactician | Ammunition, skill/socket rules and allied battlefield roles |
| Monk | Invoker, Acolyte of Chayula, Martial Artist | Combo resources, alternate power systems, illusions and body runes |
| Huntress | Amazon, Ritualist, Spirit Walker | Mixed melee/ranged play, sacrifice and spirit/creature relationships |
| Druid | Oracle, Shaman | Form-linked actions and conditional/elemental spell construction |

The proposal is not to ship these classes or their copyrighted setting. It is
to let a creator define equivalents: an oath-bound rifle mystic, a blood-funded
healer, an animal-shifting scavenger, or a diplomat whose constructs inherit
negotiated faction privileges. The same primitives should work outside fantasy.

## Important differences from PoE1

| Dimension | PoE2 reference | Proposed CoffeeSUD choice |
| --- | --- | --- |
| Skill organization | Skills have their own support arrangement; equipment sockets serve augment items | Model ability composition separately from physical socket inventory; I05 must distinguish the two |
| Gem acquisition | Uncut gems allow players to choose skills/supports as they progress | Add an authored choice reward whose output pool is filtered by level, knowledge and world rules |
| Persistent effects | Spirit pays for persistent buffs, recurring minions and certain trigger setups | A14: optional dedicated reservation resource rather than requiring mana reservation in every preset |
| Weapon sets | Swapping changes equipment and can change passive allocations and available Spirit | A19: atomic loadouts, with an eligibility preview and a clear policy for summons/reservations that become invalid |
| Damage conversion | Converted damage scales using its resulting type rather than remembering its old type | A09: support distinct calculation profiles; never reuse PoE1 conversion implicitly |
| Avoidance and control | Active dodge, blocking/parrying, stun/freeze buildup and vulnerability windows create timing decisions | A10/A12: declared action windows and reactions suited to text; optional real-time presentation |
| Skills used together | Setup, consumption, detonations, ailments and weapon actions encourage sequences | A03/A20: publish machine-readable setup/payoff conditions and explain why a combo is unavailable |
| Recovery equipment | Life/mana flasks and condition-triggered charms split responsibilities | A18: reusable reservoir and condition-policy definitions, not a fixed belt hardcoded to one game's layout |
| Endgame specialization | 0.5's fully allocatable main Atlas tree coexists with selectable master bonuses | G02: distinguish completion-based upgrades from mutually exclusive specialization choices |

Evidence: [skill organization](https://www.poe2wiki.net/wiki/Skill) (indexed
excerpt), [keywords](https://poe2db.tw/us/Keywords),
[Spirit](https://poe2db.tw/us/Spirit),
[weapon sets](https://poe2db.tw/us/Weapon_Sets),
[conversion guide](https://mobalytics.gg/poe-2/guides/damage-conversion),
[combat calculation guide](https://mobalytics.gg/poe-2/guides/damage-defence-calc-order),
[dodge explanation](https://mobalytics.gg/poe-2/guides/dodge-roll-mechanic).
These community mechanic references are not a substitute for versioned balance
data. Proposed CoffeeSUD implementations should have their own explicit rules.

### A turn-based translation

An enemy begins a sweeping attack that resolves after two **world action
steps**. Its warning names the affected lanes or rooms. The controlled player
can dodge to another lane, brace with a shield, interrupt, or accept the hit
while completing a powerful action. Other party characters can follow authored
reaction policies. In a real-time world the same encounter uses ticks; a
paused campaign does not advance deadlines while a user reads a menu.

This preserves a choice about commitment and counterplay. It does not depend
on recreating animation frames, aiming a mouse or testing the player's typing
speed. A dodge should declare exactly which attack categories it avoids;
“invulnerable while dodging” must not be an undocumented universal assumption.

## Defense, ailments and resource interactions

PoE2's keyword reference distinguishes physical and elemental/chaos damage,
several damaging/control ailments, defenses and persistent resources. For
example, cold hits build freeze, lightning can shock, and chaos interacts with
energy shield differently from PoE1. Copying a damage-type name does not copy
the same rules. [Keywords](https://poe2db.tw/us/Keywords).

For A12/A13, each effect definition needs a threshold source, buildup/decay,
duration or magnitude, stack policy, immunity interaction and dispel behavior.
A boss may retain partial buildup through a phase change, clear it, or become
temporarily protected; all three need to be intentional. Distinguish avoiding
a hit, reducing its damage, delaying part of that damage, absorbing it with a
secondary pool and recovering afterward.

Runic Ward, introduced in 0.5, provides survival after life reaches one and
regenerates separately. It is a different rule from casually labeling an
ordinary absorb shield “ward.” [0.5 notes](https://www.pathofexile.com/forum/view-thread/3932540).
A creator could use A13 for a mech's emergency reactor, a lich's remaining
souls or a guardian spirit. Specify ordering with death prevention, damage over
time, culling and simultaneous hits before shipping it.

Resource ownership is particularly important when moving among weapon sets,
forms or alternate costs. An effect that reserves Spirit in one loadout cannot
quietly continue for free in another. A single atomic “recompute active build”
operation should validate the new arrangement and then commit its consequences.

## Crafting and itemization

PoE2 should not be described as PoE1 with fewer crafting options. Its equipment
augments, currency operations, omen interactions, desecration, rune families and
later Runeforging form a separate evolving system. Current community crafting
references also mark old multi-desecration tricks as legacy rather than legal
current recipes. [Crafting codex](https://poe2.page/poe2_crafting_codex.html),
[currency/omen reference](https://www.poe2noob.com/crafting/reference),
[0.3](https://poe2db.tw/us/Version_0.3.0),
[0.5](https://www.pathofexile.com/forum/view-thread/3932540).

I03/I04/I05 should therefore model operations and restrictions rather than
hardcode named currencies. Examples include adding a modifier, replacing an
eligible modifier with a constrained one, revealing a hidden choice, adding an
augment slot, replacing an augment, and upgrading a base while retaining its
authored identity. An omen-like effect modifies the **next compatible committed
transaction**. A failed preview, canceled choice or incompatible target should
not consume it unless the author explicitly designs that behavior.

Crafting encounters are especially adaptable: let the player choose a desired
result, then add runic conditions that make earning it harder. This connects
crafting knowledge with play instead of requiring every good item to come from
blind repeated rerolls. The proposal supports both styles as independent rules.

## Adventures and endgame projects

PoE2 has used a discoverable Atlas with distinct content families, specialized
progression, bosses, waystones and tablets. Early Access added and changed
Breach, Ritual, Delirium, Expedition, Abyss and Vaal activities as well as the
Sekhemas/Chaos trial paths. The **0.5** overhaul adds fixed objectives,
deterministic quest access to pinnacle encounters, a fortress that awards Atlas
points, and selectable master bonuses. [Official 0.5 notes](https://www.pathofexile.com/forum/view-thread/3932540).

For CoffeeSUD, this suggests three deliberately separate layers:

1. **Discovery:** a journal/map that reveals places and connections.
2. **Activity progression:** a saved project such as a temple, expedition,
   ritual sequence or faction investigation.
3. **Access and rewards:** a contract defining costs, attempts, participants,
   boss eligibility and who owns what.

Sekhemas/Sanctum-like trials suggest a separate run-health resource,
boons/afflictions, branch selection, merchants and a relic loadout (G12).
Chaos/Ultimatum-like trials suggest successive objectives and difficulty choices
(G04). Their ability to resume and bank intermediate rewards in later updates
is particularly relevant to a local narrative game: the player should be able
to stop a session without losing an hour of progress solely because an active
interpreter stack cannot be saved.

The 0.4 temple suggests adjacency-aware room construction (G13). The later
ocean exploration suggests rumors, chart discovery and a voyage ledger (G01).
The Forbidden Rites chain suggests carrying a defeated encounter forward into
the next battle, while retaining individual reward claims (G03/I20).
[0.4 notes](https://poe2db.tw/us/Version_0.4.0),
[0.5.5 notes](https://www.pathofexile.com/forum/view-thread/4000864).

## Recommendation

Choose PoE2's **combat readability and commitment** as one optional preset,
PoE1's broad build composition as another, and MUD-style persistent character
relationships as a third. They can share infrastructure without promising that
every mathematical rule or content pack composes automatically. Start with
action windows, support composition, loadout transitions and resumed trials;
require validators and useful player explanations from the first prototype.
