# 3Kingdoms through We MUD Together

[Dossier](README.md) · [3Scapes](three-scapes.md) · [Proposal catalog](proposals.md)

[We MUD Together](https://wemudtogether.com/) is a player-maintained 3Kingdoms
resource. Its [about page](https://wemudtogether.com/about/) makes that provenance
clear. It mixes current observations, reproduced help and older guides, sometimes
explicitly marked as old. Treat it as evidence of distinctive designs, not a
guarantee that every old formula still matches the server. 3Scapes is a related
but separate source; its rules are not automatically 3Kingdoms rules.

## Progression beyond a character level

| Documented system | Interesting behavior | Proposal and current engine fit |
| --- | --- | --- |
| [Mudwide skills](https://wemudtogether.com/skills/) | A hierarchical path independent of guild powers; points come from several forms of progress; trainers and resets matter | P01/P02: extend skill trees and prerequisites with explicit budgets, training access and a readable path browser |
| [Professions](https://wemudtogether.com/professions/) | Minor, specialization and major choices layer passive, situational and active benefits over a guild | P01: slotted disciplines with unlock stages; ordinary classes remain usable |
| [Milestones](https://wemudtogether.com/milestones/) | Level progression communicates access to more systems | Q07/P15: an authored “what opened up?” view, populated from real eligibility rules |
| [Skillquests](https://wemudtogether.com/skillquests/) | Discovery and tasks can award advancement currency | Use quests and achievements now; a progression ledger should make grants once-only and auditable |
| [Eternals](https://wemudtogether.com/guilds/eternals/) | A later progression layer coexists with the original guild and uses distinct costs for skills and powers | P01/P09: capstone progression with a separate budget and explicit scope; avoid assuming endless mandatory numeric inflation |
| [Ascended](https://wemudtogether.com/guilds/ascended/) | A costly transition to an alternate immortal form can be reversed at a special location | A21/P09: an authored transformation ceremony and state migration. The guide explicitly says this guild is not currently maintained |
| [Alignment](https://wemudtogether.com/alignment/) | Standing affects eligibility and equipment; knowledge can improve the precision of displayed alignment | Existing factions/masks and vocabulary cover much of this; A23 makes uncertainty a declared perception policy |
| [Reputation](https://wemudtogether.com/reputation/) | Standing can be both a relationship and a spendable service resource | Extend organization services with separate lifetime standing and spendable favor, or explicitly document when spending changes the relationship |
| [Medals](https://wemudtogether.com/medals/) | A broad collection of accomplishments records exploration, combat, time and special feats | Reuse achievements and journals; P13 adds collections, optional secrecy and milestone presentation |

The useful feature is the ability to **combine different progression rhythms**.
A scholar could learn through discoveries, a hunter through creature captures,
and a guild engineer through successful research. They can still adventure in
the same world. A progression-track definition should declare its earning
events, costs, maximums, reset rules and sharing scope.

## Guilds that change the game loop

These are not proposals to duplicate named guilds. They are examples of rules
that an author should be able to express.

### Gentech: research, personal resources and a common power core

The [Gentech guide](https://wemudtogether.com/guilds/gentech/) describes
experimental powers, success chances, slots and phases; guild experience can
be split toward research credits. Personal power/storage coexist with a shared
core funding guild facilities. Research failures can have severe consequences,
and members help maintain one another's efficiency. The guide includes older
material and later corrections.

This is a particularly strong P01/P04/P08 example. An authored guild should be
able to define an experiment graph, optional random outcomes, a collective
resource pool, contribution records and services that consume it. A single-player
world can use NPC contributions and authored projects; a multiplayer economy
must not be necessary for a basic class to function. Q03 should show research
eligibility, expected costs and irreversible outcomes before commitment.

### Elementals: staged mastery and resource conversion

[Elementals](https://wemudtogether.com/guilds/elementals/) progress through
elemental forms and later Spirit/Time progression. Changing forms can reset
access to powers while retaining longer-term advancement. Their own attack and
energy systems differ from ordinary weapon combat; the guide identifies several
outdated help files.

A21/P01 should let a form change its actions, resource consumers and eligible
equipment while an independent mastery track persists. A creator can choose a
short campaign-friendly cycle instead of importing the source's very long grind.
Form changes must reconcile equipment, natural attacks, injuries, reservations
and followers in one operation.

### Juggernauts: the character, suit and organization are separate

The [Juggernaut guide](https://wemudtogether.com/guilds/juggernauts/) presents
suit-based combat and clans with different early access to shared powers,
including support and supply abilities. A suit's role is not identical to the
pilot's identity.

Use A19/A21 for equipment-bound action sets, P03 for clan access and P04 for
supplies. CoffeeSUD already has vehicles, rideables and equipment. The missing
general contract is deciding which actor owns damage, resources, experience and
escape when a piloted shell breaks. The same mechanism could support a demon
host, a diving rig or a magical battle avatar.

### Psicorps: trainable disciplines and paid combat performance

[Psicorps](https://wemudtogether.com/guilds/psicorps/) uses tours, scrip and
multiple disciplines. Powers have explicit tiers, costs and maintained effects;
opponent difficulty affects rewards. Training can gate further advancement.

P01/P02 should support both breadth requirements and specialization ceilings.
Reward rules should examine meaningful encounter completion and contribution;
paying endlessly for safely prolonging a fight is an exploitable progression
rule. The source's own guidance is useful evidence that advancement needs an
earning policy, not just another number on the character sheet.

### Bards, Jedi, Changelings and core guilds

| Source | Reference pattern | Proposed use |
| --- | --- | --- |
| [Bards](https://wemudtogether.com/guilds/bards/) | Songs combine with combat, stealth and magic | A14/A20: maintained performances and conditional combinations; musical knowledge can matter outside combat |
| [Jedi](https://wemudtogether.com/guilds/jedi/) | Broad career/skill development and a strong thematic progression identity | P01/P03/P10: careers, practices and temptation/conduct rules; create original setting-specific equivalents |
| [Changelings](https://wemudtogether.com/guilds/changelings/) | Form discovery and mastery are a substantial part of play; the guide intentionally withholds some details | A21/A23: discovered-form journal, partial information and explicit identity/equipment transitions; no need to copy secret form lists |
| [Core guilds](https://wemudtogether.com/guilds/core/) | Simpler fighter, cleric, sorcerer and android paths coexist with more elaborate systems | Supply straightforward templates alongside advanced toolkits; complexity should be an author choice |

## Items whose progress is not the physical item

[Linked items](https://wemudtogether.com/linked/) can preserve mastery through
logins and reboots, sometimes across registered alternate characters. The Rod
of Lordly Might illustrates use-unlocked runes and selectable weapon/utility
functions. The physical copy and the owner's accumulated knowledge are distinct.

I11/I12 should support **artifact mastery keyed by an authored identity**, with
an explicit character/profile scope. That differs from ordinary item-instance
growth already supported by `Prop_ItemGrowth`. If the owner loses a sword and
finds another of the same tradition, the author can choose which knowledge
transfers and which physical improvements do not. Prevent transferring shared
mastery into unlimited saleable copies of a valuable item.

This distinction also helps crafting tools, inherited family weapons, spellbooks,
mount training and musical instruments. Display both states: “this copy's
condition/upgrades” and “your mastery of this artifact family.”

## Crafting as interacting professions

The [crafting guide](https://wemudtogether.com/crafting/) connects three gathering
roles with three production roles. In particular, prepared gems are charged
with captured monster energy after manufacture, and overfilling can damage
them. Farming is a resource-management puzzle; mining includes danger.
Stored raw materials survive reboots while finished products have a different
lifetime. Some embedded notes explicitly date to 2009.

I04/I06/G20 should support production chains whose order matters, including
semi-finished goods. A useful original example is: mine a conductor, shape it
at a forge, attune it to a living storm, then fit it to a vehicle. CoffeeSUD's
recipes, workstations, gathering and crops already supply much of the content
surface. New engine work should focus on reliable item modifications, timed
stages and component provenance.

[Transmuter](https://wemudtogether.com/professions/transmuter/) adds material
conversion. [Golem Master](https://wemudtogether.com/professions/golem-master/)
builds followers from body components, including pack animals and stationary
watchers. I04/G18/P05 could let creature parts determine capacity, senses or
actions, with a visible resulting stat block. A constructed helper should have
a declared task, lifetime and ownership; a stationary watcher does not need to
pretend to be an ordinary party recruit.

## Companions, missions, housing and dungeons

| Source | Interesting detail | CoffeeSUD adaptation |
| --- | --- | --- |
| [Puppies](https://wemudtogether.com/puppies/) | Rescue/adoption standing, growth, equipment, care and combat roles form a whole progression loop | P05: bonds, needs and recovery; reduce repetitive care commands through an authored policy rather than requiring constant typing |
| [Missions](https://wemudtogether.com/missions/) | Activities reward combinations of XP, currency, equipment and temporary benefits | Reuse quests/jobs; distinguish temporary, repeatable and permanent reward grants |
| [Dungeons](https://wemudtogether.com/dungeons/) | Guides distinguish scaling and reset behavior; Aegis illustrates instance-specific access and rewards | G01/G03: explicit scaling and reset contracts; never accept a key from the wrong expedition instance |
| [Houses](https://wemudtogether.com/houses/) | Owners can edit rooms/details/responses, share access and use services/storage | P16/Q10: safe player construction with budgets and permissions, built on existing housing and furnishing |
| [Waypoints](https://wemudtogether.com/waypoints/) | Travel discovery is part of navigating a large multi-realm world | Reuse waypoints, lore and map knowledge; an author can choose whether travel knowledge is character- or profile-wide |

Fantasy, Science and Chaos demonstrate that very different themes can share
one progression framework. Their presence is not a reason to make technology
or magic mandatory. P11/Q11 should let a pack declare its theme and dependencies
while using common ownership, action, crafting and save contracts.

Some dungeon details suggest smaller features worth keeping in the catalog.
The [dungeon guide](https://wemudtogether.com/dungeons/) describes Celestial
Zodiacs tools that reveal conditions or grant access, Section Z capsules with
decay and limited preservation, and Marble Plinths as a ceremonial challenge
where killing the participants defeats the intended approach. These suggest
A23/I12/G23 content: diagnostic instruments, stabilizing perishable rewards, and
encounters whose successful action is not an ordinary attack. The review sampled
these sections rather than copying the guide's numeric scaler tables or routes.

## What to take, and what to leave configurable

The standout additions are guild-specific advancement, shared facilities,
linked mastery, useful noncombat companions and interlocking production chains.
Long timers, gear loss on reboot, secrecy policies, extreme progression costs
and paid convenience/boosts reflect a persistent social server's choices. They
should not become CoffeeSUD defaults. Where a source uses a paid unlock, the
proposal is an ordinary authored unlock or optional rules choice; no payment
integration is required.

The site's public hints, maps and item submissions also suggest better creator
tools: structured difficulty reports, source/version labels, discoverable help,
and machine-checkable links from a recipe or guild power to its dependencies.
These belong in Q01/Q02/Q05/Q10, supporting every world rather than only an
ARPG-themed one.

The site's [3K Coding Primer](https://wemudtogether.com/3KCodePrimer/) is also
useful authoring evidence. It teaches LPC types, scope, object lifecycle,
inheritance and code structure in a staged reference. CoffeeSUD uses different
implementation technologies; this is a model for progressively exposing complex
tools, not a reason to add an LPC runtime. Q10 should carry a creator from a
small working object through a complete system and its persistence contract.
