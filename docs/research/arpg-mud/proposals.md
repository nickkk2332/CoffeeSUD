# Proposal catalog: 96 optional additions and extensions

[Dossier](README.md) · [Architecture and roadmap](architecture.md) · [Design cookbook](cookbook.md) · [Filterable CSV](proposals.csv)

Every entry is a **proposal**, including entries based on existing tools. None
of the new commands, schemas or mechanics in this catalog has been implemented
by this research task. Source-guide links lead to the observed mechanics and
external references; the proposed changes and acceptance criteria are original
design recommendations.

**Kind:** `recipe` means a useful content prototype can use existing tools;
`extend` means reuse an existing engine subsystem but add a reusable contract;
`new` means a new first-class primitive; `defer` means retain the idea for a
later decision. A recipe still needs authored content, and a script prototype
does not establish that a reusable engine API exists.

**Effort:** S is a small content/presentation task; M crosses several existing
interfaces; L adds substantial state, tooling or execution contracts; XL changes
core combat, identity, persistence or a broad runtime. These are relative scopes,
not calendar estimates. Each entry's MVP is deliberately smaller than its full
reference system.

**Phase:** 0 establishes schemas/validation; 1 establishes shared foundations;
2 builds a playable vertical slice; 3 adds selected activity and progression
packs; 4 considers the largest optional systems. Dependencies can cross phases:
implement their smallest required contract first, not their entire wishlist.
Several foundations can be developed iteratively. See the roadmap for a small
coherent first slice rather than treating all 96 entries as one project.

**Acceptance** is the minimum meaningful demonstration for that proposal, in
addition to the shared fresh-process save/restore, default-world compatibility,
ownership and clock checks in the architecture guide.

## Index

- [Abilities, builds and combat — A01–A24](#abilities-builds-and-combat)
- [Items, crafting and economy — I01–I20](#items-crafting-and-economy)
- [Activities and world progression — G01–G24](#activities-and-world-progression)
- [Characters, organizations and relationships — P01–P16](#characters-organizations-and-relationships)
- [Creator tools and delivery contracts — Q01–Q12](#creator-tools-and-delivery-contracts)

## Abilities, builds and combat

### A01 — Typed tags and modifier algebra

**extend · L · phase 1 · depends on Q01, Q02.**

Declare typed tags, modifier stages, stacking groups, caps and source ownership. Existing foundation: Generic abilities, properties, masks and world stats.

- **Smallest useful version:** One numeric modifier pipeline plus compatibility tags for a small skill family.
- **Acceptance:** Removing any source recomputes the result correctly; explain additive versus multiplicative effects and rounding.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [lost-souls](lost-souls.md).

### A02 — Composable skills and supports

**new · L · phase 1 · depends on A01.**

Compose a base skill with validated behavior, cost, targeting and timing transformations. Existing foundation: Generic abilities and item-granted abilities.

- **Smallest useful version:** One attack, three supports and a readable compiled skill sheet.
- **Acceptance:** Reject an incompatible support before spending; equip, unequip, respec and restore produce the same legal skill.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md).

### A03 — Causal triggers and bounded reactions

**extend · L · phase 1 · depends on A01.**

Give triggered actions causal IDs, priorities, costs, cooldowns and recursion limits. Existing foundation: Script events, generic hooks and combat AI.

- **Smallest useful version:** On-block and on-kill triggers with one declared action budget.
- **Acceptance:** Two mutually triggering effects terminate; summons retain correct ownership; restore cannot replay a committed trigger.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [three-kingdoms](three-kingdoms.md).

### A04 — Explain a combat result

**extend · M · phase 1 · depends on A01, A09.**

Show the applied and rejected rules behind damage, defenses, costs and immunity. Existing foundation: COMBATDEBUG and stat inspection.

- **Smallest useful version:** A plain-text calculation trace for one hit and one ailment.
- **Acceptance:** The explanation agrees with the resolved event and does not consume dice or alter the encounter.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md).

### A05 — Graph progression and masteries

**extend · L · phase 2 · depends on A01, P01.**

Add graph roots, choice groups, path legality, point currencies and readable neighborhoods. Existing foundation: Skill trees, prerequisites and respec tools.

- **Smallest useful version:** A nine-node tree with two roots and one mastery choice.
- **Acceptance:** Refunding a bridge handles dependent allocations explicitly; old stable node IDs restore after a compatible content update.
- **Reference analysis:** [poe1-systems](poe1-systems.md).

### A06 — Secondary archetype trees

**extend · M · phase 2 · depends on A05, P03.**

Allow specialized secondary trees with shared or independent point budgets. Existing foundation: Classes, ranks and achievement grants.

- **Smallest useful version:** A boss-earned secondary discipline available to several classes.
- **Acceptance:** A character cannot double-spend one budget between the original and secondary tree; leaving removes only grants from that source.
- **Reference analysis:** [poe1-history](poe1-history.md) · [poe2](poe2.md).

### A07 — Jewels that alter progression graphs

**new · XL · phase 4 · depends on A05, I05, Q06.**

Apply deterministic bounded graph patches, expansions or node substitutions. Existing foundation: Item effects and progression prerequisites.

- **Smallest useful version:** One socket adds a small cluster; another changes a declared neighborhood.
- **Acceptance:** Removing or replacing a jewel produces a reviewable allocation migration; graph cycles and overlapping patches are validated.
- **Reference analysis:** [poe1-systems](poe1-systems.md).

### A08 — Body sockets, grafts and charged glyphs

**new · L · phase 3 · depends on A01, I05, A15.**

Provide optional body-bound modules with installation, capacity, charge and removal rules. Existing foundation: Body parts, wear slots, effects and charges.

- **Smallest useful version:** Two implant slots and one charged glyph on a declared body profile.
- **Acceptance:** Form changes, death and restore preserve legal installations and reconcile granted powers and reservations.
- **Reference analysis:** [poe1-history](poe1-history.md) · [lost-souls](lost-souls.md) · [three-scapes](three-scapes.md).

### A09 — Versioned damage and defense pipeline

**extend · XL · phase 1 · depends on A01.**

Separate base values, conversion, scaling, hit checks, mitigation, ailments and recovery; allow different rule profiles. Existing foundation: Combat formulas, resistances and generic damage/healing.

- **Smallest useful version:** One documented direct-hit pipeline and two conversion presets.
- **Acceptance:** A golden scenario distinguishes PoE1-like type history from PoE2-like final-type scaling and prevents double mitigation.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md) · [lost-souls](lost-souls.md).

### A10 — Action windows and tactical reactions

**extend · L · phase 2 · depends on A03, G03.**

Declare commitments, reaction windows, interrupt rules and optional encounter lanes. Existing foundation: Windups, telegraphs, turns and boss phases.

- **Smallest useful version:** One two-step sweep with dodge, brace and interrupt responses.
- **Acceptance:** Each clock mode resolves the same declared opportunity; reading a paused menu does not spend its deadline.
- **Reference analysis:** [poe2](poe2.md) · [three-scapes](three-scapes.md).

### A11 — Delivery geometry and trajectories

**new · L · phase 3 · depends on A02, A09.**

Define line, radius, chain, repeat-hit and collision policies over room graphs or optional local positions. Existing foundation: Rooms, exits, ranges and multi-target abilities.

- **Smallest useful version:** A directed bolt and a chain that cannot cross a closed exit.
- **Acceptance:** Target order is stable; friendly-fire and repeated-hit limits hold after displacement or a target disappearing.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md).

### A12 — Ailment buildup and interaction rules

**extend · L · phase 2 · depends on A09.**

Add threshold buildup, decay, consumption, stacking, immunity and source snapshots. Existing foundation: Diseases, poisons, effects and saving throws.

- **Smallest useful version:** Freeze buildup plus a strike that consumes a status for a payoff.
- **Acceptance:** Cleanse, phase transition, immunity and save/restore never leave hidden buildup or apply damage reduction twice.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md).

### A13 — Defense pools and recovery obligations

**extend · L · phase 2 · depends on A09, A15.**

Configure absorption, delayed damage, recharge, leech and death-prevention ordering. Existing foundation: HP, shields, resistances and healing effects.

- **Smallest useful version:** One rechargeable defense and capped over-time leech.
- **Acceptance:** Simultaneous hits, damage over time and restoring pending recovery obey documented caps and cannot create infinite healing.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md).

### A14 — Reservation and maintained-effect budgets

**new · L · phase 2 · depends on A01, A15.**

Reserve named capacity for persistent skills, allies or equipment effects. Existing foundation: Costs, auras, summons and active effects.

- **Smallest useful version:** One dedicated command resource funds an aura and a persistent minion.
- **Acceptance:** Changing gear, form or resource maximum reconciles active effects atomically; suspended effects cannot continue for free.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md) · [three-kingdoms](three-kingdoms.md).

### A15 — Named resources and charge state machines

**extend · M · phase 1 · depends on A01.**

Unify pools, counters, gain/spend events, decay, reservation eligibility and UI presentation. Existing foundation: World stats, effect state and item charges.

- **Smallest useful version:** Heat, focus and a three-charge combat resource.
- **Acceptance:** Spend and gain ordering is defined; changing maximums clamps predictably; saved decay uses the selected clock.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md) · [three-kingdoms](three-kingdoms.md).

### A16 — Owned proxy actions

**extend · L · phase 3 · depends on A02, A03.**

Distinguish minion, totem, trap, mine, persistent field and attached-brand ownership. Existing foundation: Summons, traps and combat behaviors.

- **Smallest useful version:** A placed turret and a triggered trap using a shared skill definition.
- **Acceptance:** Inheritance, threat, kill credit, legality and loot eligibility resolve from explicit fields rather than the nearest player.
- **Reference analysis:** [poe1-systems](poe1-systems.md).

### A17 — Persistent minion loadouts

**extend · L · phase 3 · depends on A16, I20.**

Save a roster and equipment policy for persistent summoned actors without confusing them with profile recruits. Existing foundation: Followers, equipment, summoning and recruitment.

- **Smallest useful version:** One recurring guardian with a fixed equipment budget and recovery rule.
- **Acceptance:** Death, dismissal, travel and restore do not duplicate equipment or exceed world follower limits.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md).

### A18 — Flasks, charms and reusable reservoirs

**extend · M · phase 2 · depends on A03, A15.**

Define reusable consumable-like equipment with refill events and optional condition-based use. Existing foundation: Invokable items, charges, recharge and cooldowns.

- **Smallest useful version:** A healing flask and a cleanse charm with visible charges.
- **Acceptance:** Two activation conditions cannot double-spend; full-health behavior, cooldown and refill rules are inspectable.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md).

### A19 — Atomic weapon and action loadouts

**new · L · phase 2 · depends on A01, A14.**

Switch equipment, conditional passives and active capabilities as one legal transition. Existing foundation: Equipment, ability grants and custom commands.

- **Smallest useful version:** Two weapon sets with different skill and reservation arrangements.
- **Acceptance:** Refused swaps leave the old state intact; successful swaps reconcile minions, cooldowns and costs without free lingering benefits.
- **Reference analysis:** [poe2](poe2.md) · [three-kingdoms](three-kingdoms.md).

### A20 — Momentum, combos and combat styles

**extend · M · phase 2 · depends on A03, A15.**

Expose setup/payoff chains and momentum without requiring a sub-second input sequence. Existing foundation: Combat actions, statuses and martial abilities.

- **Smallest useful version:** A three-step optional martial combo and an encounter-based streak counter.
- **Acceptance:** Interrupted, missed or delayed steps follow declared reset rules; slow text input does not accidentally invalidate a turn-mode combo.
- **Reference analysis:** [poe1-history](poe1-history.md) · [poe2](poe2.md) · [three-scapes](three-scapes.md).

### A21 — Forms, hosts and body capability profiles

**extend · XL · phase 3 · depends on A01, A19, Q06.**

Separate persistent identity, controller, current body and form-granted capabilities. Existing foundation: Generic races, polymorph abilities, natural weapons and wear slots.

- **Smallest useful version:** One humanoid-to-beast transition with explicit equipment and movement policies.
- **Acceptance:** Transform while injured, carrying equipment and in transit; restore either form with stable identity and no stranded gear.
- **Reference analysis:** [poe2](poe2.md) · [three-kingdoms](three-kingdoms.md) · [lost-souls](lost-souls.md).

### A22 — Optional anatomy and local injuries

**new · XL · phase 4 · depends on A21, A09.**

Add a persistent part graph with functions, coverage, health, disabling, severing and restoration. Existing foundation: Race body parts and existing injury-related abilities.

- **Smallest useful version:** One multi-headed creature and one injured limb with a documented recovery route.
- **Acceptance:** Vital-part death, area damage, severed equipment, regrowth and form transitions behave consistently; ordinary HP worlds remain unchanged.
- **Reference analysis:** [lost-souls](lost-souls.md).

### A23 — Knowledge-aware senses and inspection

**extend · M · phase 2 · depends on A01.**

Render names, appraisal detail and available investigation through knowledge and sensory rules. Existing foundation: Senses, lore, journals, presentation and languages.

- **Smallest useful version:** Unknown alloy identification and a familiar granting a new environmental sense.
- **Acceptance:** Inventory, trade, help and combat messages respect the same knowledge rule; repeated inspection does not reroll or mutate state.
- **Reference analysis:** [lost-souls](lost-souls.md) · [three-kingdoms](three-kingdoms.md).

### A24 — Elite traits and linked packs

**extend · M · phase 2 · depends on A01, G03.**

Budget individual and pack-level traits with exclusions, telegraphs and readable counters. Existing foundation: Mob ranks, behaviors, scaling and encounter tables.

- **Smallest useful version:** Three elite traits and one linked pack with a shared objective.
- **Acceptance:** Reject impossible combinations; display the actual abilities; pack membership and shared state survive a partial defeat and restore.
- **Reference analysis:** [poe1-history](poe1-history.md).

## Items, crafting and economy

Implementation update: Stage 5 delivers a bounded numeric-modifier and controlled
crafting family for I01/I03/I04. See the
[working creator guide](../../creator/17-item-modifiers-crafting.md) for supported
schemas and commands. Broader goals below, including arbitrary effect layers,
rarity policies, staged production and additional currencies, remain proposals.

### I01 — Structured item modifier records

**extend · L · phase 1 · depends on A01, Q01.**

Keep affix identity, tier, tags, group, layer, rolled values, locks and provenance separately from display text. Existing foundation: Data-driven affixes and item properties.

- **Smallest useful version:** One equipment family with prefix/suffix capacity and mutually exclusive groups.
- **Acceptance:** Rebuilding, renaming or exporting an item preserves its modifiers and source IDs; illegal mixed states are rejected.
- **Reference analysis:** [poe1-systems](poe1-systems.md).

### I02 — Inspectable loot generation policies

**extend · M · phase 1 · depends on I01, Q08.**

Declare rarity, eligible pools, budgets, targetable rewards and optional bad-luck protection. Existing foundation: Loot tables, affix weights, catalog rules and generation seeds.

- **Smallest useful version:** One seeded table with tier constraints and a finite guarantee counter.
- **Acceptance:** Preview and sampled runs agree about eligibility; unique caps and once-only rules hold across a real restore.
- **Reference analysis:** [poe1-systems](poe1-systems.md).

### I03 — Atomic item and reward transactions

**new · L · phase 1 · depends on I01, Q06.**

Prepare costs/outcomes before committing input consumption, output changes and a receipt in one game-thread transition. Existing foundation: Item identity, saves, recipes and currency operations.

- **Smallest useful version:** One deterministic craft with a canceled-choice and failed-precondition path.
- **Acceptance:** Save only at transaction boundaries; repeated completion after restore cannot duplicate a committed output or consume its inputs twice.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [architecture](architecture.md).

### I04 — Declarative crafting operations

**extend · L · phase 1 · depends on I03.**

Describe add, remove, replace, protect, reroll and base-upgrade operations with eligibility and costs. Existing foundation: Recipes, workstations, salvage and known recipes.

- **Smallest useful version:** A three-operation workbench and one staged production recipe.
- **Acceptance:** Preview explains invalid targets and protected groups; failed validation consumes nothing; post-commit hooks cannot retry the craft.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [three-kingdoms](three-kingdoms.md).

### I05 — Typed sockets and augments

**extend · L · phase 2 · depends on I01, A02.**

Define socket capacity, compatibility, connectivity, occupant identity, installation and removal. Existing foundation: Containers, receptacles and item-granted abilities.

- **Smallest useful version:** Separate one physical augment socket from one skill support slot.
- **Acceptance:** Unequip, extraction, item destruction and restore preserve custody and legal grants; classic and permissive color policies are different presets.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md).

### I06 — Targeted crafting distributions

**extend · M · phase 2 · depends on I04.**

Allow guaranteed tags, excluded tags and composable weight adjustments without bespoke currency scripts. Existing foundation: Recipes, materials and weighted affixes.

- **Smallest useful version:** Two crafting ingredients bias a pool and one guarantees a modifier family.
- **Acceptance:** Explain the eligible distribution, detect empty pools and enforce guarantees without bypassing item restrictions.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [three-kingdoms](three-kingdoms.md).

### I07 — Hidden modifiers and reveal choices

**new · M · phase 2 · depends on I04, A23.**

Keep an unresolved modifier and a stable reveal menu with discovery rewards. Existing foundation: Identification, known recipes and menus.

- **Smallest useful version:** An unidentified affix reveals three legal alternatives at a specialist.
- **Acceptance:** Reopening or restoring the menu preserves its options; choosing twice cannot grant two affixes or repeat knowledge rewards.
- **Reference analysis:** [poe1-history](poe1-history.md) · [poe2](poe2.md).

### I08 — Destructive transformations and item lineage

**new · L · phase 3 · depends on I04, Q06.**

Model corruption, recombination, split, imprint and restore as constrained lineage operations. Existing foundation: Item copying, transformation and stable physical identities.

- **Smallest useful version:** One two-item merge and one irreversible corruption table.
- **Acceptance:** Consume both source identities once; copied receipts cannot be reused; protected, bound and incompatible states remain enforced.
- **Reference analysis:** [poe1-systems](poe1-systems.md).

### I09 — Material and quality channels

**extend · M · phase 2 · depends on I01, A09.**

Add optional material properties, mixtures and quality channels with declared combat/crafting consumers. Existing foundation: Custom materials, named resources, quality and durability.

- **Smallest useful version:** One alloy with conductivity and one quality channel affecting only defense mods.
- **Acceptance:** Preview consumers and units; no unused trait is presented as a working benefit; resizing and repair check the same material rules.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [lost-souls](lost-souls.md).

### I10 — Layout-based crafting projects

**new · L · phase 4 · depends on I04, G13.**

Let adjacency or placement modify a crafting project's rules. Existing foundation: Recipes, puzzle primitives and room generation.

- **Smallest useful version:** A four-slot arrangement with two adjacency bonuses and a visible output preview.
- **Acceptance:** Rotations and placement order have documented semantics; restore preserves the project; invalid combinations cannot consume components.
- **Reference analysis:** [poe1-history](poe1-history.md).

### I11 — Progression attached to equipment

**new · L · phase 3 · depends on I01, A05, P01.**

Give item instances or artifact families their own trees, investments and transfer rules. Existing foundation: Item growth, attunement and ability grants.

- **Smallest useful version:** A three-node weapon tree with distinct physical and mastery progress.
- **Acceptance:** Selling, copying, losing and reacquiring the artifact follows its declared scope; a merge cannot inherit the same investment twice.
- **Reference analysis:** [poe1-history](poe1-history.md) · [three-kingdoms](three-kingdoms.md).

### I12 — Incubators and persistent mastery

**extend · M · phase 2 · depends on P01, I18.**

Count eligible use toward a future unlock or reward, optionally on a character/profile instead of the current copy. Existing foundation: Item growth, discovery and achievements.

- **Smallest useful version:** An incubator and a weapon-family mastery with visible progress.
- **Acceptance:** Qualifying events count once; moving the item cannot move character-bound progress; restoring near completion gives one reward.
- **Reference analysis:** [poe1-history](poe1-history.md) · [three-kingdoms](three-kingdoms.md).

### I13 — Tattoos and next-event omens

**new · M · phase 3 · depends on A05, I03, A03.**

Replace a declared progression node or alter the next compatible committed event. Existing foundation: Traits, effects and conditional scripts.

- **Smallest useful version:** One reversible node tattoo and one crafting omen.
- **Acceptance:** Check eligibility before consumption; canceled previews preserve the omen; respec and migration remove only the tattoo's own source.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md).

### I14 — Special currencies and offer books

**extend · M · phase 2 · depends on I03.**

Support expedition scrip, tribute, bargaining, deferred offers and stepwise merchant services. Existing foundation: Currencies, banks, shops and pricing.

- **Smallest useful version:** A local NPC with three saved offers and one defer option.
- **Acceptance:** Offer prices/options survive restore; character and party balances stay distinct; a reroll is a committed paid action.
- **Reference analysis:** [poe1-history](poe1-history.md) · [three-kingdoms](three-kingdoms.md).

### I15 — Optional asynchronous trade book

**defer · XL · phase 4 · depends on I03, I20, Q12.**

Add a local order book first; a network economy would require a separate host/service and authority model. Existing foundation: Existing shops, banks and exchange-rate support.

- **Smallest useful version:** NPC or campaign-local listings with custody, expiry and completed-sale receipts.
- **Acceptance:** Concurrent-looking attempts serialize; restore preserves escrow; no remote trade is claimed safe without authoritative settlement.
- **Reference analysis:** [poe1-history](poe1-history.md).

### I16 — Loot organization, filters and shared storage

**extend · L · phase 2 · depends on I01, Q07.**

Add filter/explanation rules and typed storage views; profile-linked inventory is a separate new persistence feature. Existing foundation: Inventory, containers, journals and profile-owned housing.

- **Smallest useful version:** Text grouping and a recoverable hidden-loot view over existing inventory.
- **Acceptance:** Filters change presentation only; moving items between views never duplicates them; a future shared stash must have one physical owner.
- **Reference analysis:** [poe1-systems](poe1-systems.md).

### I17 — Offerings with selectable outcomes

**extend · M · phase 2 · depends on I03, G04.**

Consume an authored offering for a stable set of alternative outcomes, with explicit stakes. Existing foundation: Receptacles, menus and transformations.

- **Smallest useful version:** Offer one item and choose one of three generated replacements.
- **Acceptance:** Options persist after restore; abandoning follows a declared refund rule; the sacrificed item cannot also remain in inventory.
- **Reference analysis:** [poe1-history](poe1-history.md) · [lost-souls](lost-souls.md).

### I18 — Escrow and deferred encounter rewards

**new · L · phase 1 · depends on I03.**

Represent provisional, banked, forfeited and claimed rewards separately. Existing foundation: Quest rewards, wave completion and saved script state.

- **Smallest useful version:** A three-room trial that can bank rewards and resume.
- **Acceptance:** Leaving, failing, dying and restoring have explicit outcomes; replaying a completion event cannot claim the same reward again.
- **Reference analysis:** [poe1-history](poe1-history.md) · [poe2](poe2.md).

### I19 — Collections and targetable item discovery

**recipe · S · phase 2 · depends on P13.**

Supply content templates for collection books, targetable components and distinct build-enabling uniques. Existing foundation: Achievements, item sets, journals and loot tables.

- **Smallest useful version:** One six-entry collection with source hints and a nonmandatory completion reward.
- **Acceptance:** Only eligible acquisitions count; an ordinary copy and a cosmetic variant are distinguished deliberately; missing entries remain discoverable.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [three-kingdoms](three-kingdoms.md).

### I20 — Loot rights and binding contracts

**extend · L · phase 2 · depends on I03.**

Separate pickup allocation, use binding, trade permission, source instance and expiry. Existing foundation: Ownership, party membership and item use masks.

- **Smallest useful version:** Personal reward claims plus one explicit equip-bound item.
- **Acceptance:** Two party characters cannot claim one shared entitlement twice; binding checks all transfer routes and survives restore.
- **Reference analysis:** [poe2](poe2.md) · [three-scapes](three-scapes.md).

## Activities and world progression

### G01 — Itemized expeditions and entry contracts

**extend · L · phase 1 · depends on I03, I18.**

Let an item select destination, seed, difficulty, modifiers, costs, attempts and participants. Existing foundation: Travel, generation, instances and quests.

- **Smallest useful version:** One chart opens a seeded dungeon with two modifier choices.
- **Acceptance:** Entry consumes once; save/restore preserves remaining attempts and instance identity; missing members have a safe recovery path.
- **Reference analysis:** [poe1-history](poe1-history.md) · [poe2](poe2.md) · [three-kingdoms](three-kingdoms.md).

### G02 — Discoverable endgame campaign graphs

**new · L · phase 2 · depends on G01, P01.**

Maintain discovered nodes, connections, story gates, influence and chosen content progression separately from physical rooms. Existing foundation: Maps, lore, waypoints and generated layouts.

- **Smallest useful version:** A twelve-node expedition atlas with one deterministic boss quest.
- **Acceptance:** Discovery and boss eligibility persist; shuffled physical instances do not erase progress; changing a preset invokes migration rather than silently reinterpreting nodes.
- **Reference analysis:** [poe1-history](poe1-history.md) · [poe2](poe2.md).

### G03 — Reusable encounter director

**extend · L · phase 1 · depends on A01, I18.**

Coordinate actors, phases, objectives, budgets, interruption and completion through one saved state machine. Existing foundation: Waves, arenas, boss phases, telegraphs, threat and resets.

- **Smallest useful version:** One three-phase boss and one defense wave using shared definitions.
- **Acceptance:** Target death, actor removal, retreat, party wipe and restore cannot duplicate waves or rewards; scaling is chosen once by a declared rule.
- **Reference analysis:** [poe1-history](poe1-history.md) · [three-scapes](three-scapes.md).

### G04 — Escalating choices and cash-out trials

**extend · M · phase 2 · depends on G03, I18.**

Offer increasing risk, objectives and modifiers while separating provisional and banked rewards. Existing foundation: Menus, wave encounters and quest rewards.

- **Smallest useful version:** Three rounds with a stop option and visible cumulative conditions.
- **Acceptance:** A canceled menu does not change stakes; failing forfeits only declared escrow; resumed trials preserve offers and modifier order.
- **Reference analysis:** [poe1-history](poe1-history.md) · [poe2](poe2.md).

### G05 — Shrines and contestable auras

**recipe · S · phase 2 · depends on G03.**

Provide templates for an object that benefits its current holder or nearby side. Existing foundation: Room effects, switches, use scripts and timed abilities.

- **Smallest useful version:** A guarded shrine transfers a timed benefit when claimed.
- **Acceptance:** Ownership changes remove the prior effect; leaving the area and restoring near expiry do not leave permanent benefits.
- **Reference analysis:** [poe1-history](poe1-history.md).

### G06 — Guarded caches and chosen reward encounters

**recipe · S · phase 2 · depends on G03, I02.**

Template an inspected reward object whose activation releases a budgeted encounter. Existing foundation: Containers, traps, loot tables and encounter scripts.

- **Smallest useful version:** A craftable cache and an imprisoned elite with a known ingredient.
- **Acceptance:** The object cannot pay before success; repeated activation, target disappearance and restore cannot release duplicate populations.
- **Reference analysis:** [poe1-history](poe1-history.md).

### G07 — Encounter ecology and death-driven escalation

**extend · M · phase 3 · depends on G03, A24.**

Accumulate local actions into a budgeted consequence such as a portal, infestation or escalating patrol. Existing foundation: Spawn tables, room state and script events.

- **Smallest useful version:** A death cluster opens one demon encounter with an explicit cap.
- **Acceptance:** Off-screen events, chained explosions and restored corpses count once; escalation cannot recursively feed itself without a limit.
- **Reference analysis:** [poe1-history](poe1-history.md).

### G08 — Possession and player-chosen empowerment

**extend · M · phase 2 · depends on G03, A24.**

Let an entity or device modify a selected encounter with explicit reward changes. Existing foundation: Effects, roaming mobs and conditional behaviors.

- **Smallest useful version:** One roaming spirit and one opt-in enemy empowerment device.
- **Acceptance:** Host death/removal restores ownership and reward provenance; the same empowerment cannot be stacked beyond its budget.
- **Reference analysis:** [poe1-history](poe1-history.md).

### G09 — Capture, menageries and encounter ingredients

**new · L · phase 3 · depends on G03, I03, P15.**

Store captured creature identities and classifications separately from live followers; consume captures in authored encounters. Existing foundation: Recruitment, summons, journals and crafting components.

- **Smallest useful version:** Capture three creature families and use one capture recipe.
- **Acceptance:** Capture cannot also produce a normal kill reward unless authored; trading, freeing, consuming and restoring each capture conserve ownership.
- **Reference analysis:** [poe1-history](poe1-history.md).

### G10 — Prophecies and conditional future events

**extend · L · phase 3 · depends on G03, I18.**

Register durable future promises with eligibility, priority, expiry, scope and conflict rules. Existing foundation: Quest conditions, saved variables and delayed events.

- **Smallest useful version:** A prophecy adds an encounter when entering a qualifying region.
- **Acceptance:** Repeated room descriptions do not trigger it; concurrent candidates use documented priority; expired or canceled promises clean up state.
- **Reference analysis:** [poe1-history](poe1-history.md) · [lost-souls](lost-souls.md).

### G11 — Layered areas and temporary realities

**extend · L · phase 3 · depends on G01, G03.**

Create linked alternate areas with a duration/resource budget and declared consequences on the original. Existing foundation: Instances, planes, room states and transport.

- **Smallest useful version:** A room-copy expedition and an expanding rift over a bounded graph.
- **Acceptance:** Copies do not duplicate unique rewards or source knowledge unintentionally; forced return has a safe location if the original room vanished.
- **Reference analysis:** [poe1-history](poe1-history.md) · [poe2](poe2.md).

### G12 — Roguelike routes, relics and run health

**new · L · phase 3 · depends on G01, G04, I18.**

Add run-scoped resolve, boons, afflictions, relic loadouts and branch selection. Existing foundation: Generated rooms, quests, effects and save state.

- **Smallest useful version:** A four-room trial with two branches and one merchant.
- **Acceptance:** Bodily defeat and run failure remain distinct; quit/resume restores choices, costs and unclaimed rewards without rerolling.
- **Reference analysis:** [poe1-history](poe1-history.md) · [poe2](poe2.md).

### G13 — Player-shaped dungeon topology

**new · L · phase 3 · depends on G01, Q09.**

Keep a construction project whose choices place, upgrade, connect or consume dungeon tiles. Existing foundation: MAP layouts, generation guarantees, locks and quest state.

- **Smallest useful version:** A six-room temple project with one adjacency rule and a reachability preview.
- **Acceptance:** Each legal build has an entry and reachable exit; generation preserves required keys; restore retains both project and instantiated version.
- **Reference analysis:** [poe1-history](poe1-history.md) · [poe2](poe2.md).

### G14 — Light-limited depth expeditions

**extend · L · phase 3 · depends on G01, G03, A15.**

Combine a route network, moving safe zone, fuel, depth, upgrades and optional hidden side paths. Existing foundation: Light, darkness, hazards, journeys and generation.

- **Smallest useful version:** A three-depth mine with flares and a return route.
- **Acceptance:** Fuel and darkness use the selected clock; the safe zone survives restore; depleted supplies cannot strand a mandatory campaign path.
- **Reference analysis:** [poe1-history](poe1-history.md).

### G15 — Faction operation and relationship boards

**new · L · phase 3 · depends on P03, G03, I18.**

Store members, jobs, ranks, relationships and investigation outcomes as an operation graph. Existing foundation: Organizations, diplomacy, quests and NPC memory.

- **Smallest useful version:** Four NPCs in two cells with capture, recruit and interrogate outcomes.
- **Acceptance:** Moving an NPC changes future encounters coherently; mutual relationships and once-only safehouse rewards persist across restore.
- **Reference analysis:** [poe1-history](poe1-history.md).

### G16 — Objective arenas and team tournaments

**extend · L · phase 3 · depends on G03, P06.**

Support objectives beyond kills, including totems, escort, territory and role assignments. Existing foundation: Arenas, waves, parties and threat.

- **Smallest useful version:** A two-lane totem match and a small saved tournament bracket.
- **Acceptance:** Friendly AI can pursue objectives; timeout, disconnect and full-party defeat resolve clearly; rewards do not require farming a stalled match.
- **Reference analysis:** [poe1-history](poe1-history.md) · [three-scapes](three-scapes.md).

### G17 — Room-graph tower defense

**new · L · phase 4 · depends on G03, A11.**

Define lanes, travel cost, build sites, tower upgrades and a defendable core. Existing foundation: Room links, summons, waves and resource counters.

- **Smallest useful version:** Two lanes, three tower types and a finite wave schedule.
- **Acceptance:** Monsters progress by declared path rules; blocked lanes recalculate or fail safely; a text summary exposes imminent threats.
- **Reference analysis:** [poe1-history](poe1-history.md).

### G18 — Build-a-boss and component-derived creatures

**extend · L · phase 3 · depends on G03, I03, A24.**

Derive a creature's actions and reward budget from chosen parts. Existing foundation: Mob templates, ranks, abilities and item components.

- **Smallest useful version:** Three component slots assemble a boss or a utility construct.
- **Acceptance:** Preview matches the resulting actor; incompatible parts fail before consumption; repeated creation cannot duplicate the same component.
- **Reference analysis:** [poe1-history](poe1-history.md) · [three-kingdoms](three-kingdoms.md).

### G19 — Fog, pursuit and escalating area overlays

**extend · M · phase 3 · depends on G03, G11.**

Apply an optional progress-driven risk overlay with visible intensity and an exit condition. Existing foundation: Room states, darkness, map fog and hazards.

- **Smallest useful version:** A five-room mist expedition where collected wisps empower a later encounter.
- **Acceptance:** Overlay state cleans up on exit; completion records do not stack indefinitely; map knowledge is separate from current visibility.
- **Reference analysis:** [poe1-history](poe1-history.md) · [poe2](poe2.md).

### G20 — Settlements and multi-stage production

**extend · L · phase 3 · depends on P04, P08, I04.**

Represent projects, worker slots, inputs, outputs, upkeep and bounded catch-up. Existing foundation: Housing, crops, workstations, organizations and calendar.

- **Smallest useful version:** A quarry feeds a forge and funds one local shipment.
- **Acceptance:** Elapsed-time processing has a work cap; interrupted production conserves inputs; events are summarized instead of replaying thousands of ticks.
- **Reference analysis:** [poe1-history](poe1-history.md) · [three-kingdoms](three-kingdoms.md).

### G21 — Prepared heists and extraction

**extend · L · phase 3 · depends on G01, G03, P05, I18.**

Author blueprints, crew capabilities, alert, contraband custody and escape transitions. Existing foundation: Quests, locks, traps, stealth, followers and travel.

- **Smallest useful version:** A two-room heist with one specialist requirement and two escape routes.
- **Acceptance:** Abort, alarm, death and successful extraction settle exactly the declared loot; crew loss cannot trap the player inside an unsolvable state.
- **Reference analysis:** [poe1-history](poe1-history.md).

### G22 — Optional invasions and scheduled events

**new · L · phase 4 · depends on G01, G03, Q08.**

Add bounded world-event controllers with region eligibility, announcement and cleanup policies. Existing foundation: Schedules, encounters, calendars and instances.

- **Smallest useful version:** A player-started event plus one game-calendar trigger.
- **Acceptance:** Quiet/off-screen regions do not accumulate unlimited events; restore does not restart an already completed invasion; participation and scaling are explicit.
- **Reference analysis:** [poe1-history](poe1-history.md) · [three-scapes](three-scapes.md).

### G23 — Constraint-based challenge generation

**new · XL · phase 4 · depends on P01, G03, Q02.**

Generate a goal with optional restriction, taboo, forfeit and reward, and validate a feasible solution. Existing foundation: Quests, masks, achievements and generated jobs.

- **Smallest useful version:** Three authored challenge templates including a noncombat delivery task.
- **Acceptance:** The generator excludes impossible builds/locations; restriction violations and completion race deterministically; rewards and forfeits apply once.
- **Reference analysis:** [lost-souls](lost-souls.md).

### G24 — Composable activity presets

**extend · L · phase 3 · depends on Q01, Q02, Q11.**

Combine selected activities through a compatibility manifest and a shared encounter budget. Existing foundation: World settings, themes and generation templates.

- **Smallest useful version:** Two compatible activities plus one conflict that receives a useful diagnostic.
- **Acceptance:** Composition cannot spawn two exclusive controllers or duplicate rewards; disabling a module leaves an explicit policy for existing saves.
- **Reference analysis:** [poe1-history](poe1-history.md).

## Characters, organizations and relationships

### P01 — General progression tracks

**extend · L · phase 1 · depends on Q01, Q06.**

Declare independent currencies, earning rules, curves, milestones, caps and reset behavior for several owner types. Existing foundation: Classes, ranks, achievements, XP and saved scopes.

- **Smallest useful version:** A character discipline, an organization research project and an item mastery track.
- **Acceptance:** Each owner scope earns and spends independently; repeated source events cannot grant twice; old track IDs migrate predictably.
- **Reference analysis:** [three-kingdoms](three-kingdoms.md) · [lost-souls](lost-souls.md) · [poe1-systems](poe1-systems.md).

### P02 — Specialty access and training budgets

**extend · L · phase 2 · depends on P01, A01.**

Separate available access, minimum investment, point budget and effective proficiency; support incompatibility and antagonism. Existing foundation: Ability mappings, prerequisites, trainers and respec.

- **Smallest useful version:** Two affiliations contribute access to one skill with an attribute-bound budget.
- **Acceptance:** Leaving a source previews over-investment and resolves it by policy; temporary stat buffs do not create permanent free specialty points.
- **Reference analysis:** [lost-souls](lost-souls.md) · [three-kingdoms](three-kingdoms.md).

### P03 — Affiliation compatibility and obligations

**extend · M · phase 2 · depends on P01.**

Add character-level exclusive slots, incompatible tags, membership grants and conduct rules. Existing foundation: Organizations, memberships, masks and diplomacy.

- **Smallest useful version:** One primary guild plus two compatible associations and a forbidden combination.
- **Acceptance:** Join/leave/rejoin validates the whole build and removes only source-owned benefits; standing, membership and disguise remain distinct.
- **Reference analysis:** [lost-souls](lost-souls.md) · [three-scapes](three-scapes.md).

### P04 — Guild homes and declarative services

**extend · M · phase 2 · depends on P03, I03.**

Describe service eligibility, location/time, costs, shared infrastructure and refusals. Existing foundation: Guild scaffolds, NPC schedules, shops, banks and trainers.

- **Smallest useful version:** A guild laboratory offers research and repair when the relevant NPC is present.
- **Acceptance:** The same eligibility explains help, menus and execution; closed services consume nothing; traveling headquarters preserve ownership.
- **Reference analysis:** [three-kingdoms](three-kingdoms.md) · [three-scapes](three-scapes.md) · [lost-souls](lost-souls.md).

### P05 — Persistent companion and familiar bonds

**new · L · phase 2 · depends on P01, A03, I20.**

Make reciprocal relationships own progression, capabilities, needs, commands and death/separation rules. Existing foundation: Followers, recruits, affinity, summons and saved identity.

- **Smallest useful version:** One mount bond and one noncombat familiar with growth milestones.
- **Acceptance:** Neither participant can be bound twice illegally; missing actors, form changes, death and restore resolve from declared policies.
- **Reference analysis:** [lost-souls](lost-souls.md) · [three-kingdoms](three-kingdoms.md).

### P06 — Optional party formations and policies

**extend · M · phase 2 · depends on A10, P05.**

Add front/flank/rear/guard roles with explicit targeting and reaction policies. Existing foundation: Parties, ranks, target priority and threat.

- **Smallest useful version:** Three formation roles in one encounter without a graphical client.
- **Acceptance:** Roles survive switching and travel; area attacks and bypass traits follow documented rules; world party limits remain authoritative.
- **Reference analysis:** [three-scapes](three-scapes.md) · [poe1-history](poe1-history.md).

### P07 — Delegated missions and off-screen expeditions

**new · L · phase 3 · depends on G01, P05, I18.**

Assign a bounded crew to a saved game-time mission with costs, outcomes and a return policy. Existing foundation: Journeys, generated jobs and background work queues.

- **Smallest useful version:** One shipment and one scouting mission with a deterministic seed.
- **Acceptance:** Restoring cannot reroll the mission; catch-up is bounded; members cannot simultaneously be in the party and on the mission.
- **Reference analysis:** [poe1-history](poe1-history.md) · [three-kingdoms](three-kingdoms.md).

### P08 — Shared resource pools and contribution records

**extend · M · phase 2 · depends on P04, I03.**

Generalize guild power, research materials and maintenance pools with contributor/consumer records. Existing foundation: Clan funds, banks, dues and crafting assistance.

- **Smallest useful version:** One shared power core funds a service and receives NPC/player contributions.
- **Acceptance:** A service cannot spend a pool twice; all actor identities remain valid across saves; single-player campaigns have a viable supply policy.
- **Reference analysis:** [three-kingdoms](three-kingdoms.md) · [three-scapes](three-scapes.md).

### P09 — Voluntary rebirth and retirement

**new · XL · phase 4 · depends on P01, Q06, I18.**

Apply a reviewable reset manifest and grant bounded legacy progress in a new generation scope. Existing foundation: Character creation, achievements, profiles and respec.

- **Smallest useful version:** Retire one consenting character to a new start while retaining an authored legacy choice.
- **Acceptance:** Show every lost/retained field; companion, quest, item and profile references migrate; cancellation leaves the original unchanged.
- **Reference analysis:** [three-scapes](three-scapes.md) · [three-kingdoms](three-kingdoms.md).

### P10 — Patrons, favor and conduct

**extend · L · phase 3 · depends on P03, P01.**

Add favor events, portfolios, obligations, holy-day gates and optional portents to existing religious systems. Existing foundation: Deities, factions, calendars and achievements.

- **Smallest useful version:** A patron rewards one conduct rule and unlocks a calendar-bound service.
- **Acceptance:** Witness/scope rules are explicit; favor cannot be farmed by replaying one event; leaving reconciles benefits without erasing unrelated standings.
- **Reference analysis:** [lost-souls](lost-souls.md) · [three-scapes](three-scapes.md).

### P11 — Culture and identity lifecycle

**extend · M · phase 2 · depends on P01, A23.**

Keep culture, upbringing, learned language, current body and adopted identity as separate sources. Existing foundation: Origins, creation steps, languages, races and vocabulary.

- **Smallest useful version:** Two cultures share a race; one character can adopt a language/community later.
- **Acceptance:** Changing culture does not erase unrelated skills or rewrite race anatomy; creation and later adoption use the same grant rules.
- **Reference analysis:** [lost-souls](lost-souls.md) · [three-kingdoms](three-kingdoms.md).

### P12 — Season, hardcore and self-found profiles

**extend · L · phase 3 · depends on Q01, Q06, I20.**

Declare rule scopes, entry flags, trade/party restrictions and parent-scope migration. Existing foundation: World configuration, profiles and separate campaigns.

- **Smallest useful version:** An isolated local season and an optional hardcore-to-parent transfer.
- **Acceptance:** A transfer conserves items and progress by policy; self-found restrictions cover every transfer route; starting a season never erases another campaign.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [poe2](poe2.md).

### P13 — Challenges, collections and personal records

**extend · M · phase 2 · depends on P01.**

Group accomplishments into optional collections, challenge sets and personal-run records. Existing foundation: Achievements, journals, discovery and profile rewards.

- **Smallest useful version:** A ten-entry challenge book with visible and hidden milestones.
- **Acceptance:** Rewards apply once in the declared scope; old seasons remain readable; no online leaderboard is required for local play.
- **Reference analysis:** [poe1-systems](poe1-systems.md) · [three-kingdoms](three-kingdoms.md) · [three-scapes](three-scapes.md).

### P14 — Configurable death, ghost and life contracts

**extend · L · phase 3 · depends on A21, I18.**

Compose defeat, injuries, ghost control, resurrection, life counters and recovery services. Existing foundation: Downed/revive, checkpoints, death rules and corpses.

- **Smallest useful version:** One ghost-recovery episode and one limited-life preset with explicit beginner protection.
- **Acceptance:** Save during downing, ghost state and resurrection; each restores safely and cannot consume another life or duplicate corpse equipment.
- **Reference analysis:** [lost-souls](lost-souls.md) · [three-scapes](three-scapes.md).

### P15 — Discovery and latent advancement

**extend · M · phase 2 · depends on P01, A23.**

Let meaningful discoveries unlock disciplines, senses or latent powers under authored conditions. Existing foundation: Discovery properties, achievements, lore and known recipes.

- **Smallest useful version:** Three lore discoveries open one noncombat talent; optional chance uses a stable event seed.
- **Acceptance:** Repeated reading gives no repeated grant; knowledge sharing obeys scope; a menu refresh cannot reroll awakening.
- **Reference analysis:** [lost-souls](lost-souls.md) · [three-kingdoms](three-kingdoms.md).

### P16 — Households, estates and campaign legacies

**extend · M · phase 3 · depends on P03, P13.**

Connect home permissions, household roles, collections, memorials and persistent chronicles. Existing foundation: Housing, furnishing, ownership, journals and organizations.

- **Smallest useful version:** A shared estate with guest/owner permissions and one adventuring trophy display.
- **Acceptance:** Guests cannot edit protected structures; transfers settle ownership and storage; a retired character's record remains readable.
- **Reference analysis:** [three-kingdoms](three-kingdoms.md) · [three-scapes](three-scapes.md) · [lost-souls](lost-souls.md).

## Creator tools and delivery contracts

### Q01 — Capability manifests and versioned schemas

**new · M · phase 0 · depends on none.**

Declare module IDs, rule versions, dependencies, conflicts, save sections and supported clock modes. Existing foundation: World manifests, configuration and validation commands.

- **Smallest useful version:** A manifest for one optional pack that can be inspected without activating it.
- **Acceptance:** An incompatible pack or unsupported save version fails before changing campaign state; diagnostics name the exact dependency.
- **Reference analysis:** [architecture](architecture.md).

### Q02 — Cross-system compilation and validation

**extend · L · phase 0 · depends on Q01.**

Validate schemas, references, type/unit compatibility, dependency cycles, controller conflicts and bounded guarantees. Existing foundation: Existing validators, masks and reference checks.

- **Smallest useful version:** One command checks a support, craft and expedition together.
- **Acceptance:** Errors identify authored source locations; dynamic scripts are marked unknown rather than falsely proved safe or impossible.
- **Reference analysis:** [architecture](architecture.md).

### Q03 — Build, craft and encounter previews

**extend · M · phase 1 · depends on Q01, A04.**

Provide non-mutating previews of legal choices, costs, outcomes and known interactions. Existing foundation: PROGRESSION, CONSIDER, PRICECHECK and inspection.

- **Smallest useful version:** One build comparison, one craft preview and one expedition preflight.
- **Acceptance:** Preview consumes no currency, dice or once-only state; hidden information follows the selected creator/player view.
- **Reference analysis:** [architecture](architecture.md) · [poe1-systems](poe1-systems.md).

### Q04 — Bounded combat and economy simulation

**new · L · phase 3 · depends on Q02, Q09.**

Add a deterministic headless harness with declared agents, budgets, metrics and stopping rules. Existing foundation: PLAYTEST, TESTCHAR and fixture infrastructure.

- **Smallest useful version:** Compare two small builds against a fixed boss across a bounded seed set.
- **Acceptance:** Runs stop on stalled encounters or budget limits; reports separate observed distributions from exact analytical probabilities.
- **Reference analysis:** [architecture](architecture.md).

### Q05 — State inspection and event provenance

**extend · M · phase 1 · depends on Q01.**

Show controllers, active grants, causal events, reservations and pending transactions with source locations. Existing foundation: STAT, TRACEB, script traces and saved state.

- **Smallest useful version:** Inspect one character, item, relationship and expedition ledger.
- **Acceptance:** Inspection does not mutate state; a failed transition reports the owner, rule source and recovery path.
- **Reference analysis:** [architecture](architecture.md).

### Q06 — Migration and compatibility tooling

**extend · L · phase 1 · depends on Q01.**

Version new ledger schemas and supply dry-run migrations, missing-source policies and rollback summaries. Existing foundation: Stable source IDs, instance IDs, aliases and manifests.

- **Smallest useful version:** Rename a tree node and retire one module against a pre-change save.
- **Acceptance:** The old save is tested in a fresh process; refused migrations leave it untouched; uncertain name-based references never bind randomly.
- **Reference analysis:** [architecture](architecture.md).

### Q07 — Accessible text presentation contracts

**extend · M · phase 1 · depends on Q01.**

Require plain-text alternatives, stable choice IDs, summaries and adjustable combat detail. Existing foundation: Menus, windows, narration, colors, lore and help.

- **Smallest useful version:** Render one skill tree, boss warning and loot filter without color or a graphical map.
- **Acceptance:** Headless and screen-reader-oriented views retain all actionable information; scrolling does not advance game time.
- **Reference analysis:** [architecture](architecture.md) · [poe2](poe2.md).

### Q08 — Content and interaction budgets

**new · M · phase 1 · depends on Q01.**

Declare limits for stacked modifiers, triggered actions, proxies, repeated rewards and catch-up work. Existing foundation: Difficulty formulas, rank presets and encounter budgets.

- **Smallest useful version:** A pack publishes a finite actor/trigger/loot budget per encounter.
- **Acceptance:** Reject or cap excessive compositions deterministically; explain which layer exceeded the budget; default worlds inherit no new scaling.
- **Reference analysis:** [architecture](architecture.md).

### Q09 — Reproducible random streams and scenario records

**extend · L · phase 2 · depends on Q01.**

Partition seeds for loot, offers, encounters and projects; record enough context to reproduce supported scenarios. Existing foundation: Saved campaign dice and generation seeds.

- **Smallest useful version:** Reproduce one generated item and one expedition from a recorded rules version and seed.
- **Acceptance:** Inspection does not consume random state; canceled menus keep outcomes; reports state that arbitrary native/JS execution is not exact replay.
- **Reference analysis:** [architecture](architecture.md).

### Q10 — Creator templates and worked packs

**recipe · M · phase 2 · depends on Q02, Q07.**

Supply small original content packs with declared contracts and complete create/play/save/restore examples. Existing foundation: Creator cookbook, generic editors and exported worlds.

- **Smallest useful version:** A support build, a guild laboratory, a bonded familiar and a branching expedition.
- **Acceptance:** A creator can build and complete each example from the guide using the matching engine version; no placeholder command is presented as live syntax.
- **Reference analysis:** [cookbook](cookbook.md).

### Q11 — Optional pack composition and retirement

**new · L · phase 2 · depends on Q01, Q06.**

Resolve namespaced definitions, overrides, dependencies, conflicts and saved-instance behavior when a pack is removed. Existing foundation: World resource overlays and configuration.

- **Smallest useful version:** Enable two packs with one deliberate conflict and retire one while preserving a safe campaign path.
- **Acceptance:** Activation cannot silently override an unrelated ID; disabling describes what happens to extant items, quests, summons and stored progress.
- **Reference analysis:** [architecture](architecture.md).

### Q12 — Performance and transaction observability

**extend · M · phase 1 · depends on Q01.**

Measure bounded work, delayed transitions, rollbacks, queue sizes and duplicate-event suppression. Existing foundation: Event traces, tick timing and authoring diagnostics.

- **Smallest useful version:** A report for the prototype pack's actors, triggers, transactions and catch-up.
- **Acceptance:** A stress scenario remains within declared work limits; diagnostics identify an offending source without dumping unrelated campaign/private data.
- **Reference analysis:** [architecture](architecture.md).
