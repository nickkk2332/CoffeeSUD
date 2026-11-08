# Design cookbook: original packs built from the proposals

[Dossier](README.md) · [Catalog](proposals.md) · [Architecture](architecture.md)

These are **implementation design examples**, not runnable CoffeeSUD recipes.
The YAML is intentionally labeled `proposal_only`; no parser or command for
these declarations exists yet. For content that works today, use the
[current creator cookbook](../../creator/cookbook.md). Each example identifies
existing foundations and the proposed additions separately.

## 1. Drowned Observatory: a small first ARPG pack

**Experience:** a player equips an echoing spear, chooses how it behaves, and
spends a chart to enter a short underwater ruin. A pressure budget makes side
rooms a choice. The boss exposes a readable opening. A rescued light-creature
becomes a developing companion.

**Proposals:** A02/A10/A12/A18, I01/I03/I04/I18, G01/G03, P01/P05, Q02/Q07.
Inspiration: [PoE1 expeditions](poe1-history.md), [PoE2 tactics](poe2.md),
[Lost Souls bonds](lost-souls.md).

```yaml
proposal_only: true
ability:
  id: example.echo-spear
  tags: [attack, projectile, physical]
  supports:
    slots: 2
    allowed: [example.fork, example.recall, example.anchor]
expedition:
  id: example.drowned-observatory
  access_item: example.tidal-chart
  clock: action_steps
  run_resource: {name: pressure, maximum: 12}
  rooms: [entry, archive, flooded-vault, observatory, escape]
  entry_cost: consume_one_chart_on_committed_entry
  reward_policy: bank_at_escape
  missing_member_policy: return_to_entry_when_safe
```

**Existing prototype route:** create a small area, a generic weapon/ability,
breathing/hazard rules, an instance, a boss with phases and telegraphs, and a
quest reward. Use real room IDs and the current handbook's syntax.

**New reusable work:** support compilation, an entry receipt, pressure as
run-scoped state, escrow and a reciprocal bond. A script can approximate part
of this today, but it does not establish that those APIs already exist.

**Review:** can the player understand each support without a build website?
Can they leave after two rooms, restore tomorrow and finish? Does the same
chart create only one entitled run? Can the companion remain interesting when
it contributes light and discovery rather than extra attacks?

## 2. Three supports that genuinely change a skill

Start with one strike, then offer:

* **Fork:** choose two nearby targets at reduced damage; prohibit repeatedly
  hitting the same target through a loop.
* **Anchor:** exchange mobility for an interrupting heavy hit.
* **Recall:** return the projectile after a delay, with an explicit second-hit
  rule and a higher cost.

**Proposals:** A01/A02/A03/A04/A11. Inspiration: [PoE skill construction](poe1-systems.md).
Generic abilities and separate scripted variants can prototype these effects
now. The proposed addition is their safe composition into one inspectable
ability without duplicating the base definition.

Declare which supports conflict, how costs stack and whether a repeated action
can trigger on-hit effects again. Show a before/after sheet. A minion using the
same skill must receive the intended inheritance and credit policy, not every
bonus on its owner. Save with the returning projectile pending and verify its
target-loss policy in a new process.

## 3. A reliable smith's choice

**Experience:** trade one alloy and coins to add a cold-related property to an
eligible weapon. A more expensive service reveals three options. A talisman can
modify the next compatible completed craft.

**Proposals:** I01/I03/I04/I06/I07/I13. Inspiration: [PoE crafting](poe1-systems.md)
and [3Kingdoms production](three-kingdoms.md).

```yaml
proposal_only: true
craft:
  id: example.frost-temper
  target: {kind: weapon, forbids: [mirrored, sealed]}
  cost: {example.winter-alloy: 1, local_coins: 20}
  operation:
    add_modifier: {tag: cold, layer: explicit, respect_capacity: true}
  preview: explain_eligibility_and_cost
  randomness: stable_pending_transaction
  incompatible_target: refuse_without_spending
  outcome_hook: after_commit_only
```

**Existing foundation:** recipes, workstations, custom materials, item effects,
known recipes and menus. **New work:** modifier records and the transaction.

The important scenarios are not just successful crafting: the player cancels,
the target is moved by another command, the last suffix is filled before the
choice is answered, the source definition changes, or the save restores with a
choice pending. Bind a transaction to the target's physical identity and rules
version. Choose an explicit invalidation/refund policy. Never reroll an offer
because the player inspected the item again.

## 4. A guild laboratory that changes how its members advance

**Experience:** members divide earned study credit between rank and research.
They choose experiments, contribute power to the laboratory, and receive better
service when a trained NPC is present. One experiment grants a new action;
another improves a shared facility.

**Proposals:** P01/P02/P03/P04/P08, A15 and I03.
Inspiration: [Gentech](three-kingdoms.md) and [Lost Souls specialties](lost-souls.md).

Use the existing guild scaffold, standing faction, trainer and scheduled NPC
to build a prototype. Add ordinary quests for the first two research projects.
The reusable version needs separate study/rank balances, a contribution ledger,
source-owned grants and a service transaction. An experiment can be completely
deterministic in a beginner world, with optional random failure in another.

Make the laboratory viable with NPC or adventure-earned contributions in a
single-player campaign. Define the earning rule so an invulnerable target cannot
produce infinite study by being attacked forever. Check that leaving the guild
removes its access while preserving unrelated training and the historical record
of a contribution. Membership, reputation and spendable favor are separate.

## 5. A light-creature with a reciprocal bond

**Experience:** a small amphibious companion shares light and reveals submerged
writing. Exploring suitable locations grows the bond. At a later milestone it
can carry a light field ahead of the party, but it cannot survive every climate.

**Proposals:** P05/P15, A21/A23 and P01.
Inspiration: [Lost Souls familiar bonds](lost-souls.md) and
[3Kingdoms companion care](three-kingdoms.md).

**Existing foundation:** create a suitable race/mob, senses and breathing,
follower behavior, affinity, lore and discovery events. **New work:** a
relationship object owning reciprocal benefits, durable progress and separation
policy. Do not turn a follower into a profile recruit merely to obtain a place
to store a bond counter.

```yaml
proposal_only: true
bond:
  id: example.lantern-friend
  participants: [consort, familiar]
  maximum_active_of_type: 1
  growth_events: [first_submerged_discovery, rescued_lantern_creature]
  benefits:
    - {at: 1, receiver: party, grant: example.soft-light}
    - {at: 3, receiver: consort, grant: example.read-tide-script}
  separation: suspend_shared_sense
  familiar_death: rescue_quest
```

The familiar's own identity and the relationship's identity must both survive
travel and restore. A duplicate summon should not create a second copy of all
bond benefits. Put repetitive care on an optional policy with costs and limits;
do not require typing the same pet command every combat round.

## 6. A temple whose future the player constructs

**Experience:** three short missions each let the player replace a room,
upgrade a service or open a connection. The completed expedition reflects those
choices. An optional observatory room can upgrade an adjacent archive.

**Proposals:** G01/G13/I18 and Q02/Q09.
Inspiration: [Incursion, Synthesis and Kalandra](poe1-history.md),
[PoE2's Vaal temple](poe2.md).

Prototype a few authored layouts with existing room states, quests and
generation. The new project ledger should store tiles and connections before
creating the physical dungeon. Required keys and the exit need reachability
checks after each legal placement. An upgrade is a semantic change to a room
definition, not a blind replacement that deletes a quest object.

Once instantiated, the run pins the chosen project/rules version. Editing the
unfinished design should not teleport occupants or reshape a live room under
them. Define whether the project is consumed, reusable or gains new wear after
each completed run. Validate a deliberately bad arrangement as well as a good
one.

## 7. A deliberate encounter instead of a typing test

**Experience:** a warden raises a weapon for a sweeping attack. The front rank
can brace, the flank can interrupt, and the rear can complete a channel. A
companion follows a simple declared reaction when not directly controlled.

**Proposals:** A10/A20/P06/G03.
Inspiration: [PoE2 action commitment](poe2.md) and
[3Scapes' planned positioning](three-scapes.md).

Use existing windups, telegraphs, boss phases and target priority for a
prototype. New formation and reaction contracts provide repeatability across
encounters. Define whether an area attack reaches every rank and what a
skirmisher can bypass. Show the active threat and deadline in plain text.

Test the player switching windows, controlling another profile character,
being stunned or feigning death, and entering a menu while the warning is active.
All windows observe the same world. A turn-mode encounter must not resolve early
because another view redraws the room.

A future cinematic clash can listen to the committed event. The proposed
execution system would need its own runner; current `Scene` is not equivalent.
Allow a victim-only control mask when the initiator should remain free, while
the agreed default controls both participants and lets the world continue.

## 8. A noncombat challenge with an actual solution

**Experience:** carry a sealed message across a region without teleporting or
attacking. The player may sneak, negotiate or hire an escort. Breaking the seal
or violating the taboo ends the challenge under a disclosed forfeit policy.

**Proposals:** G23/P01/P15/I18.
Inspiration: [Travelers](lost-souls.md).

An authored quest can implement this now with explicit conditions. General
procedural generation is a separate new feature: the generator must account for
the character's capabilities, available routes, blocked exits and essential
NPCs. Do not offer a no-flight challenge whose sole reachable route requires
flight, or a pacifist task with an unavoidable compulsory kill.

Grant one challenge ID and one completion receipt. Explicitly decide whether
followers' attacks count, whether defensive counters violate the rule, and
whether an ally's teleport carries the challenger illegally. These are rules
to explain in the offer, not surprises to discover after an hour of travel.

## 9. A small outpost without an online economy

**Experience:** a mine feeds a forge; the forge supplies a local caravan. A
retired companion can work a station. The player chooses between an immediate
component reward and upgrading production capacity.

**Proposals:** G20/P07/P08/I04.
Inspiration: [Settlers](poe1-history.md), [3Kingdoms crafting](three-kingdoms.md)
and [guild households](three-scapes.md).

Existing housing, crops, workstations, organization funds and journeys are the
base. The project ledger adds worker assignments, input/output custody and a
bounded elapsed-time calculation. Use the game calendar by default; a world
can choose real-time progression explicitly.

The caravan outcome belongs to a saved mission with a stable seed. Restoring
does not reroll its success, and a worker cannot also be an active party member.
If a route disappears, the mission suspends or returns through an authored
fallback. Avoid producing thousands of items while the player is away by
accident; capacity and storage limits are part of the production rules.

## 10. Voluntary rebirth with a review screen

**Experience:** a character retires after a completed storyline. A successor
starts with a cultural legacy, a journal chapter and one bounded inherited
mastery choice. Equipment, unfinished quests and active relationships have
explicit destinations.

**Proposals:** P09/P11/P13/P16/Q06.
Inspiration: [3Scapes' planned Generations](three-scapes.md) and
[3Kingdoms later progression](three-kingdoms.md).

This requires new migration work; it is not safely implemented by issuing a
level-down command repeatedly. Preview a reset manifest: retained knowledge,
new identity, balances, equipment, affiliations, obligations, companions and
unclaimed rewards. Give every field a preserve, transfer, reset, suspend or
refuse policy. Commit only after the player reviews the concrete result.

The reward can be narrative access rather than permanently increasing damage.
A world can cap generations or allow retirement without starting again. Test
cancellation and a pre-rebirth save in a new process before offering the feature
to a long-lived campaign.

## How to evaluate any recipe

Ask four questions: what decision becomes interesting, what existing tools
already express it, which durable state is genuinely new, and what happens when
the player stops at the worst possible moment? A good first implementation
should make one complete authored adventure possible, with clear inspection and
recovery. Adding more numeric modifiers is not a substitute for completing that
loop.
