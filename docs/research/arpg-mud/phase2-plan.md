# Phase 2: build plan

Status: in progress. Agreed: everything except the three large families (G02,
P05, P02), which are deferred; the three merges; ailments and defense pools for
composed skills only.

This plans the 35 phase 2 entries of [proposals.csv](proposals.csv) that remain
(Q09, Q10 and Q11 were built with [phase 1](phase1-plan.md)). Each is built to
its MVP and acceptance lines, and only where the engine does not already do it.

## How it is built

- **Mechanisms, not content packs.** Each change comes with what makes it
  clear: a handbook section or chapter (every field with its default, the
  commands, what it deliberately does not do), one small example definition, and
  tests whose checks state the rules. The in-game inspection (previews, CHECK,
  LEDGER, explanations) is extended to each new piece. No playable packs, build
  scripts or play-through guides.
- **Only what is missing.** Every entry was audited against the engine
  (upstream CoffeeMud and everything CoffeeSUD added). Where the engine already
  does most of an entry, the work is the gap, often a few parameters on an
  existing property; where it does all of it, the work is a handbook recipe.
- **Off by default.** New families are optional modules on the phase 1
  framework (pinned definitions, saved state, migrations, CHECK, ledgers). An
  extension to an existing property changes nothing for content that does not
  use its new parameters.
- **Confirm before building.** The audit read code and the handbook; it ran
  nothing, and some findings are inferred. Each stage starts by confirming its
  entries' gaps with a failing test (or, for "already there", a passing one).
- **Each stage** is committed with its tests and docs, and a full check
  (`ENGINE=1 tools/check.sh`) passes before it is merged.

## Where each entry starts

Verdicts: **recipe** (works today; the handbook teaches it), **extension** (new
parameters, fields or commands on existing code), **family** (a new optional
module), **merged** (folded into another entry). Sizes are for the missing part.

| ID | Proposal | Already there | Missing | Verdict | Size |
| --- | --- | --- | --- | --- | --- |
| A05 | Build trees and masteries | Skill prerequisites (`SKILLTREE`), perks.ini pools with `COST`, `MASK`, `REQUIRES`; `RESPEC` | Roots and adjacency, any-of requirements, exclusive groups, effects held only while a node is, refunds that handle dependants, removed nodes | extension of perks | M |
| A06 | Secondary trees | Perk pools (separate or shared budgets), tracks, ranks | Picks earned by a boss, track or script; leaving removes only that pool's grants | merged into A05 | S |
| A10 | Tactical reactions | `Prop_Telegraph`, `MPTELEGRAPH`, `WINDUP`: one wind-up; interrupt by stun, damage or moving; avoid by leaving, hiding or one word; paused and turn time already hold the deadline | Chained steps; several responses with their own outcomes; who responded saved; a clock-mode test | extension | M |
| A12 | Ailment buildup | Diseases, poisons, immunities; composed burns (one per target, snapshot, saved) | A buildup meter per target and type (threshold, decay, immunity window), a status consumed for a payoff, cleansing, inspection | family (under combat profiles) | M |
| A13 | Defense pools | Absorb properties, mana shield, stoneskin, weapon leech, pools that refill | A pool spent before hit points with a recharge delay; capped leech over time; a declared order of absorb, pool, hit points, death prevention | family (under combat profiles) | M |
| A14 | Reservations | Reservable pools (one unnamed amount, JavaScript only); auras; summons | A reservation per effect or minion, made and released with it; suspension when capacity drops (today it silently shrinks); shown in `RESOURCES` | family (under named resources) | M |
| A18 | Flasks and charms | `Prop_Invokable`: charges, recharge (dawn, daily, ticks), cooldown, worn/held, mask, shown by `IDENTIFY` | Refuse when there is nothing to do (nothing spent); refills on events; optional automatic use; cooldown and recharge kept across a save | extension | S |
| A19 | Weapon and action loadouts | `Prop_OutfitContainer` (separate steps, no rollback); supports linked freely; cooldowns per base skill | A named loadout (items, links, reservations) swapped all-or-nothing; its cost in turn time | family (after A14) | L |
| A20 | Combos and momentum | Native set-ups and payoffs; composed costs and gains; charge pools that drain; kill triggers | A miss or the end of combat resetting a combo; trigger actions that set or drain; a combo display | extension | M |
| A23 | Knowing and senses | Viewer-specific descriptions (`VARIES` masks), `Prop_ShowWhen`, sensory details, identification, appraisal, `Prop_Familiar` senses | Unknown names for unidentified things, revealed once per character; one rule for identify, appraise, examine and prices; a familiar's sense as a recipe | extension | M |
| A24 | Elite traits and linked packs | Mob ranks (numbers only), telegraphs, boss phases, morale; the encounter director's roles, saved actors and budgets | Trait definitions (effects, a power cost, exclusions, a readable line); applying them; budgets per mob and pack; one pack-level trait | family | M |
| G02 | Expedition atlas | Waypoints (no connections), map fog, expeditions (charts, seeds, runs, completion), tracks | The whole atlas: nodes, links, gates, a boss node; discovered and completed nodes per character; `ATLAS` | family | L |
| G04 | Escalating trials | Escrow (bank, forfeit), director phases, expedition offers drawn from a seed, menus that survive a restore | Offers between rounds, stored and chosen; STOP banks and ends; chosen modifiers applied to later spawns | extension of the director | M |
| G05 | Shrines | `Prop_HaveSpellCast`, `Prop_HereSpellCast`, `ObjectGuardian`, `Prop_LocationBound`, switches; timed effects saved | A recipe | recipe | S |
| G06 | Guarded caches | Loot tables on open, receptacles, USE/COMBINE, staged recipes, guards, wave spawners, the director | Starting a director from a script; a hook when a director completes; a recipe | extension | S |
| G08 | Possession and empowerment | Admin possession only; expedition modifiers | A trait that moves to a host and back; a device that changes rewards | merged into A24 and G04 | S |
| I05 | Sockets | Upstream `Prop_ItemSlot` and `Prop_ItemSlotFiller`: typed, removable, saved slots; effects passed on (not in the handbook) | A type per slot; the installed items' custody when the host is destroyed; item-held supports for composed skills; teaching | extension | M |
| I06 | Targeted crafting | Operation tags and allow-lists; loot policies (tags, tiers, budgets, weights, guarantee) with preview and sampling | Excluded tags, weight multipliers, a guaranteed family, catalysts on recipes, a player preview, refusal of an empty pool before paying | extension | M |
| I07 | Hidden modifiers | Identification, curses; menus and seeded offers that survive a restore; the once-only crafting ledger; an unused `offers` stream | An unresolved modifier; a reveal that draws three legal choices once and settles one | extension | M |
| I09 | Materials and quality | `materials.txt` with effects, alloys, materials passed to products, quality tiers for native crafting | Named numeric traits (conductivity) with declared consumers; quality aimed at one consumer, such as defense modifiers | extension | M |
| I12 | Incubators and mastery | Kill achievements with weapon masks, `Prop_ItemGrowth`, `Prop_Transform`, item tracks | A recipe (optionally a mask on track earning rules) | recipe | S |
| I14 | Offer books | Currencies, shops, money changers, banks, quest boards that refresh and save | An offer book per NPC drawn from a stream and saved; holding one offer; a paid reroll as one transaction | extension | M |
| I16 | Loot organization | `LOOT` and `LOOT FILTER` on containers, inventory masks, grouping | The same views over your own inventory; filters on untracked items; why each item is hidden | extension | S |
| I17 | Offerings | Receptacles, transforms, menus, seeded offers, the crafting ledger, escrow | The same saved-choice primitive as I07 | merged into I07 | S |
| I19 | Collections | Achievements (`GOTITEM`, `ACHIEVER`, hidden ones, hints), discoveries, lore | A recipe | recipe | S |
| I20 | Loot rights and binding | Personal escrow entries per member; `Prop_ItemBinder` (owner, on pickup or equip, saved) | Refusing `GIVE`, wearing by another and other routes; separate fields for allocation, use, trade, source and expiry | extension | M |
| P02 | Specialty access | Skill access from class, race, organizations; per-source limits; count budgets; purchase records and refunds | A point budget tied to base stats; combining sources; what leaving a source does to learned skills | family | M |
| P03 | Affiliations | Organization categories with per-category caps; masks that forbid pairs; grants that follow membership | A two-way incompatible tag re-checked on rejoin and class changes; a declared policy for what leaving removes | extension | S |
| P04 | Guild services | Service NPCs (trainers, menders, healers, identifiers, upgraders), hours and closed states, scaffolded guilds, stations | One service record (provider, eligibility, hours, cost, refusal) used by help, `TALK` and the service itself; station masks and hours | extension | M |
| P05 | Companion bonds | Followers saved; mounts and stables; familiars; bound summons; the relationship state scope | A bond definition with policies for death, separation and restore; tracks owned by a bond | family | L |
| P06 | Party formations | Upstream `FORMATION` rows, range and assisting, target priority, threat | Named roles with targeting and reactions; rows kept across a restore and `PARTY LEAD`; teaching the command | extension | M |
| P08 | Shared pools | Organization banks with ledgers, donations, dues; tracks that accumulate | A spendable organization-owned pool with contributor and consumer records; a service spend with a receipt | extension of named resources | M |
| P11 | Cultures | Origins (one per character, at creation), creation questions, cultural abilities on races, languages learned later | Culture as its own source; adopting and leaving one later under the same rules | extension of origins | M |
| P13 | Challenge books | Achievements with scopes, hidden entries, progress, rewards | Named books with a "hidden entries" count; personal records (first, best) | extension | S |
| P15 | Latent advancement | Lore and discoveries (once, per character or profile), rank ladders with tattoo requirements | Re-checking when lore or a discovery unlocks; a chance roll drawn once, from a stream, at unlock | extension | S |

In all: 3 recipes, 21 extensions, 8 new families, 3 merged into others.

## Defects the audit found

Bugs in what exists, fixed in stage 1, each with a test:

- **Telegraphs:** who braced or dodged, the damage taken and the ticks left during
  a wind-up were kept in memory only; now they are kept in the property's text,
  so a save in mid-wind-up keeps them.
- **`Prop_Invokable`:** the cooldown and a `TICKS` recharge countdown were not
  saved; now they are (`COOLLEFT=`, `RECHARGEIN=`).
- **`Prop_ItemBinder`:** a bound item could be given away, and the receiver could
  wear it; now neither.
- **Ranks:** leaving a rank removed its effects by ability ID, which could remove
  the same kind of effect given by another source; now only its own.
- **Formation:** rows were lost on restore and on `PARTY LEAD`; now kept.
- **`TRACKS CONTRIBUTE`:** recorded no contributor and credited the member's last
  organization; now it credits the one named (or the only one) and keeps who gave.
- **Docs:** chapter 29 pointed to a `FORMATION` section that did not exist.

Found while building (each fixed, with a test):

- **`-WEAPONCLASS` on a character wielding nothing** passed, so a sword mastery
  counted bare-handed kills; a character wielding nothing now counts as `NATURAL`.
- **Causal triggers:** a reaction's composed cast was rolled against
  proficiency despite being cast as automatic, so it fizzled one time in twenty;
  it no longer does.
- **Cookbook:** a kill achievement with `ZAPPERMASK=""` is refused by the engine;
  an egg grows only while held, not carried; a collection completes at the next
  achievement check, not at once.

Found by the audit but belonging to an entry, so handled there: a socketed host's
gems going with it when destroyed (upstream's behaviour; I05 adds a custody
option), a perk removed from perks.ini (A05), the `TALK` services menu ignoring
eligibility (P04's service records), and practices counting a buffed Wisdom at
level-up (upstream's rule; P02, deferred).

## Stages

| Stage | Contents | Kind | Status |
| --- | --- | --- | --- |
| 1 | The defects above | fixes | Done |
| 2 | Recipes: G05 shrines, I12 incubators and mastery, I19 collections; teaching what exists: sockets, `FORMATION`, affiliation slots, achievement books | handbook | Done |
| 3 | Combat on existing properties: A10 telegraph chains and responses, A18 flasks and charms, A20 combo rules | extensions | Done |
| 4 | Build trees: A05 on perks, with A06's secondary pools | extension | Done |
| 5 | Reservations, then loadouts: A14, A19 | families | Done |
| 6 | Ailments and defense pools: A12, A13 | families | Done |
| 7 | Encounters: A24 traits (with G08's possession), G04 trials (with G08's devices), G06 director hooks | family, extensions | Done |
| 8 | Items: I05 sockets, I06 targeted crafting, I07 and I17 saved choices, I09 materials, I14 offer books, I16 inventory views, I20 binding | extensions | Done |
| 9 | Knowledge: A23 unknown names and reveals, P15 latent talents | extensions | Done |
| 10 | Organizations: P03 affiliations, P04 services, P06 formation roles, P08 shared pools, P11 cultures, P13 challenge books | extensions |  |
| 11 | The atlas: G02 | family | Deferred |
| 12 | Companion bonds: P05 | family | Deferred |
| 13 | Specialty access: P02 | family | Deferred |
| 14 | Catalog status, handbook index, a full check | docs |  |

The order puts the cheap and immediately useful work first (fixes, recipes, small
extensions), then the families in dependency order: A14 before A19, the
combat-profile children together, A24 and G04 before G08's parts. The three
large families (the atlas, bonds, specialty access) come last; each stands
alone, so they can be reordered or dropped without affecting the rest.

## Decisions

1. **Ailments and defense pools (A12, A13):** for composed skills only, as the
   combat profiles are.
2. **Merges:** A06 into A05, G08 into A24 and G04, I17 into I07.
3. **The large families:** the atlas (G02), bonds (P05) and specialty access
   (P02) are deferred.
